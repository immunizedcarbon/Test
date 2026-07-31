package de.oai.optilink.android

import android.content.Context
import android.net.Uri
import de.oai.optilink.core.FramePacket
import de.oai.optilink.core.FrameType
import de.oai.optilink.core.MdsErasure
import de.oai.optilink.core.TransferMetadata
import java.util.BitSet
import java.util.LinkedHashMap

internal data class ReceiveProgress(
    val percent: Int,
    val estimatedBytes: Long,
    val totalBytes: Long,
    val completedBlocks: Int,
    val totalBlocks: Int,
)

internal class ReceiverEngine(
    private val context: Context,
    private val onMetadata: (Long, TransferMetadata, Boolean) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onProgress: (ReceiveProgress) -> Unit,
    private val onComplete: (Uri) -> Unit,
    private val onError: (String) -> Unit,
) : AutoCloseable {
    private val resumeStore = ResumeStore(context)
    private var pendingSessionId: Long? = null
    private var pendingMetadata: TransferMetadata? = null
    private var active: ActiveReceive? = null
    private var verifying = false

    @Synchronized
    fun accept(packet: FramePacket) {
        when (packet.type) {
            FrameType.META -> acceptMetadata(packet.sessionId, packet.payload)
            FrameType.DATA -> active
                ?.takeIf { it.sessionId == packet.sessionId }
                ?.accept(packet.blockIndex, packet.equationId, packet.payload)
            FrameType.END -> Unit
        }
    }

    @Synchronized
    fun attachDestination(uri: Uri) {
        val sessionId = pendingSessionId ?: return
        val metadata = pendingMetadata ?: return
        try {
            active?.close()
            active = ActiveReceive(sessionId, metadata, uri, BitSet())
            resumeStore.save(sessionId, metadata, uri, BitSet())
            onStatus("Zieldatei bereit · QR-Daten werden empfangen")
            active?.emitProgress()
            if (metadata.fileSize == 0L) finish(active!!)
        } catch (t: Throwable) {
            onError(t.message ?: "Zieldatei konnte nicht vorbereitet werden")
        }
    }

    @Synchronized
    private fun acceptMetadata(sessionId: Long, bytes: ByteArray) {
        val metadata = TransferMetadata.decode(bytes) ?: return
        if (active?.sessionId == sessionId || pendingSessionId == sessionId) return
        pendingSessionId = sessionId
        pendingMetadata = metadata
        val resume = resumeStore.load(sessionId, metadata)
        if (resume != null) {
            try {
                active?.close()
                active = ActiveReceive(sessionId, metadata, resume.uri, resume.completedBlocks)
                onMetadata(sessionId, metadata, true)
                onStatus("Unterbrochene Übertragung wird fortgesetzt")
                active?.emitProgress()
                if (active!!.completed.cardinality() == active!!.totalBlocks) finish(active!!)
                return
            } catch (_: Throwable) {
                resumeStore.clear()
                active = null
            }
        }
        onMetadata(sessionId, metadata, false)
    }

    @Synchronized
    private fun finish(receive: ActiveReceive) {
        if (verifying) return
        verifying = true
        onStatus("100 % empfangen · SHA-256 wird geprüft …")
        onProgress(ReceiveProgress(100, receive.metadata.fileSize, receive.metadata.fileSize, receive.totalBlocks, receive.totalBlocks))
        try {
            val hash = receive.output.sha256()
            if (!hash.contentEquals(receive.metadata.fileSha256)) {
                onError("Integritätsprüfung fehlgeschlagen; die Zieldatei wurde nicht bestätigt")
                verifying = false
                return
            }
            receive.output.sync()
            resumeStore.clear()
            val uri = receive.output.uri
            receive.close()
            active = null
            verifying = false
            onComplete(uri)
        } catch (t: Throwable) {
            verifying = false
            onError(t.message ?: "Integritätsprüfung fehlgeschlagen")
        }
    }

    override fun close() {
        synchronized(this) {
            active?.close()
            active = null
        }
    }

    private inner class ActiveReceive(
        val sessionId: Long,
        val metadata: TransferMetadata,
        uri: Uri,
        val completed: BitSet,
    ) : AutoCloseable {
        val output = RandomAccessOutput.open(context, uri, metadata.fileSize)
        private val blockBytes = metadata.symbolSize.toLong() * metadata.sourceSymbolsPerBlock
        val totalBlocks = if (metadata.fileSize == 0L) 0 else ((metadata.fileSize + blockBytes - 1) / blockBytes).toInt()
        private val decoders = object : LinkedHashMap<Int, MdsErasure.Decoder>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, MdsErasure.Decoder>?): Boolean = size > 6
        }

        init {
            if (completed.length() > totalBlocks) completed.clear(totalBlocks, completed.length())
        }

        fun accept(blockIndex: Int, equationId: Int, value: ByteArray) {
            if (blockIndex !in 0 until totalBlocks || completed[blockIndex] || value.size != metadata.symbolSize) return
            val sourceCount = sourceCount(blockIndex)
            val decoder = decoders.getOrPut(blockIndex) { MdsErasure.Decoder(sourceCount, metadata.symbolSize) }
            val changed = decoder.offer(equationId, value)
            if (!changed) return
            if (!decoder.isComplete) {
                onStatus("Empfangen · Block ${blockIndex + 1}/$totalBlocks · ${decoder.rank}/$sourceCount Symbole")
                emitProgress()
                return
            }

            val symbols = decoder.result() ?: return
            val offset = blockIndex.toLong() * blockBytes
            val writeLength = actualBlockBytes(blockIndex).toInt()
            val joined = ByteArray(sourceCount * metadata.symbolSize)
            for (i in symbols.indices) symbols[i].copyInto(joined, i * metadata.symbolSize)
            output.writeAt(offset, joined, writeLength)
            completed.set(blockIndex)
            decoders.remove(blockIndex)
            resumeStore.save(sessionId, metadata, output.uri, completed)
            emitProgress()
            val percent = progressSnapshot().percent
            onStatus("Empfangen: $percent % · ${completed.cardinality()}/$totalBlocks Blöcke")
            if (completed.cardinality() == totalBlocks) finish(this)
        }

        fun emitProgress() = onProgress(progressSnapshot())

        private fun progressSnapshot(): ReceiveProgress {
            if (metadata.fileSize == 0L) return ReceiveProgress(100, 0, 0, 0, 0)
            var estimated = 0L
            var block = completed.nextSetBit(0)
            while (block >= 0) {
                estimated += actualBlockBytes(block)
                block = completed.nextSetBit(block + 1)
            }
            for ((index, decoder) in decoders) {
                if (completed[index]) continue
                val sourceCount = sourceCount(index)
                estimated += actualBlockBytes(index) * decoder.rank / sourceCount
            }
            estimated = estimated.coerceAtMost(metadata.fileSize)
            return ReceiveProgress(
                percent = (estimated * 100 / metadata.fileSize).toInt().coerceIn(0, 100),
                estimatedBytes = estimated,
                totalBytes = metadata.fileSize,
                completedBlocks = completed.cardinality(),
                totalBlocks = totalBlocks,
            )
        }

        private fun actualBlockBytes(blockIndex: Int): Long {
            val start = blockIndex.toLong() * blockBytes
            return minOf(blockBytes, metadata.fileSize - start).coerceAtLeast(0)
        }

        private fun sourceCount(blockIndex: Int): Int {
            val remaining = metadata.fileSize - blockIndex.toLong() * blockBytes
            return ((minOf(remaining, blockBytes) + metadata.symbolSize - 1) / metadata.symbolSize).toInt()
        }

        override fun close() = output.close()
    }
}
