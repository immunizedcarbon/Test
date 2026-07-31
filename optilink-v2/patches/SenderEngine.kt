package de.oai.optilink.android

import android.content.Context
import android.net.Uri
import de.oai.optilink.core.FramePacket
import de.oai.optilink.core.FrameType
import de.oai.optilink.core.GridCodec
import de.oai.optilink.core.LinkProfile
import de.oai.optilink.core.MdsErasure
import de.oai.optilink.core.TransferMetadata
import de.oai.optilink.core.WireHeader
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

internal data class PreparedVisualFrame(
    val header: WireHeader,
    val grid: ByteArray,
    val status: String,
)

internal class SenderEngine(
    private val context: Context,
    private val uri: Uri,
    private val profile: LinkProfile,
    private val onFrame: (PreparedVisualFrame) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit,
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var source: SeekableInput? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread({ runSender() }, "OptiLink-Sender").apply { start() }
    }

    private fun runSender() {
        try {
            onStatus("Datei wird vorbereitet …")
            val input = SeekableInput.open(context, uri) { onStatus("Nicht direkt lesbarer Anbieter: ${formatBytes(it)} zwischengespeichert") }
            source = input
            val hash = input.sha256 { done -> onStatus("Integrität wird berechnet: ${percent(done, input.size)}") }
            val sessionId = SecureRandom().nextLong()
            val symbolSize = profile.packetPayloadCapacity
            val sourceSymbolsPerBlock = 32
            val metadata = TransferMetadata(input.displayName, input.mimeType, input.size, hash, symbolSize, sourceSymbolsPerBlock)
            val metaPayload = metadata.encode()
            val blockBytes = symbolSize.toLong() * sourceSymbolsPerBlock
            val totalBlocks = if (input.size == 0L) 0 else ((input.size + blockBytes - 1) / blockBytes).toInt()
            var sequence = 0
            var displayed = 0L
            var blockIndex = 0
            var equationCursor = 0
            var epoch = 0
            var cachedBlock = -1
            var sourceSymbols = emptyList<ByteArray>()
            val periodNanos = 1_000_000_000L / profile.framesPerSecond
            var deadline = System.nanoTime()

            while (running.get()) {
                val metaFrame = displayed % META_INTERVAL == 0L || totalBlocks == 0
                val (packet, status) = if (metaFrame) {
                    FramePacket(FrameType.META, 0, sessionId, sequence, -1, epoch, metaPayload) to
                        if (totalBlocks == 0) "Leere Datei · Metadaten werden gesendet" else
                            "${input.displayName} · Block ${blockIndex + 1}/$totalBlocks · Runde ${epoch + 1}"
                } else {
                    if (cachedBlock != blockIndex) {
                        sourceSymbols = loadBlock(input, blockIndex, symbolSize, sourceSymbolsPerBlock)
                        cachedBlock = blockIndex
                        equationCursor = 0
                    }
                    val sourceCount = sourceSymbols.size
                    val repairCount = maxOf(12, sourceCount / 2)
                    val equationId = if (equationCursor < sourceCount) {
                        equationCursor
                    } else {
                        val repairIndex = equationCursor - sourceCount
                        sourceCount + ((epoch * repairCount + repairIndex) % (MdsErasure.MAX_EQUATIONS - sourceCount))
                    }
                    val value = MdsErasure.encode(sourceSymbols, equationId)
                    val dataPacket = FramePacket(FrameType.DATA, 0, sessionId, sequence, blockIndex, equationId, value)
                    val dataStatus = "${input.displayName} · Block ${blockIndex + 1}/$totalBlocks · Symbol ${equationCursor + 1}/${sourceCount + repairCount}"
                    equationCursor++
                    if (equationCursor >= sourceCount + repairCount) {
                        blockIndex++
                        cachedBlock = -1
                        if (blockIndex >= totalBlocks) { blockIndex = 0; epoch++ }
                    }
                    dataPacket to dataStatus
                }
                val raw = packet.encode(profile.dataByteCapacity)
                val grid = GridCodec.encode(raw, profile, sequence)
                val header = WireHeader(profile, packet.type, sequence and 0xfffff, packet.payload.size)
                onFrame(PreparedVisualFrame(header, grid, status))
                displayed++
                sequence = (sequence + 1) and 0x7fffffff
                deadline += periodNanos
                val sleepNanos = deadline - System.nanoTime()
                if (sleepNanos > 0) {
                    val millis = sleepNanos / 1_000_000
                    val nanos = (sleepNanos % 1_000_000).toInt()
                    Thread.sleep(millis, nanos)
                } else if (sleepNanos < -periodNanos * 3) {
                    deadline = System.nanoTime()
                }
            }
        } catch (t: Throwable) {
            if (running.get()) onError(t.message ?: "Senden fehlgeschlagen")
        } finally {
            running.set(false)
            source?.close(); source = null
        }
    }

    private fun loadBlock(input: SeekableInput, blockIndex: Int, symbolSize: Int, maxSymbols: Int): List<ByteArray> {
        val blockStart = blockIndex.toLong() * symbolSize * maxSymbols
        val remaining = input.size - blockStart
        val count = ceil(remaining.coerceAtMost(symbolSize.toLong() * maxSymbols) / symbolSize.toDouble()).toInt().coerceAtLeast(1)
        return List(count) { symbol ->
            ByteArray(symbolSize).also { target ->
                val offset = blockStart + symbol.toLong() * symbolSize
                val expected = minOf(symbolSize.toLong(), input.size - offset).coerceAtLeast(0).toInt()
                if (expected > 0) {
                    val read = input.readAt(offset, target, expected)
                    if (read != expected) error("Datei konnte bei Offset $offset nicht vollständig gelesen werden")
                }
            }
        }
    }

    override fun close() {
        running.set(false)
        thread?.interrupt()
        thread = null
    }

    companion object {
        private const val META_INTERVAL = 20L
        private fun percent(done: Long, total: Long): String = if (total <= 0) "100 %" else "${done * 100 / total} %"
        private fun formatBytes(value: Long): String = when {
            value >= 1L shl 30 -> "%.1f GiB".format(value / (1L shl 30).toDouble())
            value >= 1L shl 20 -> "%.1f MiB".format(value / (1L shl 20).toDouble())
            value >= 1L shl 10 -> "%.1f KiB".format(value / (1L shl 10).toDouble())
            else -> "$value B"
        }
    }
}
