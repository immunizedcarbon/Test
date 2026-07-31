package de.oai.optilink.android

import android.content.Context
import android.net.Uri
import com.google.zxing.common.BitMatrix
import de.oai.optilink.core.BurstProfile
import de.oai.optilink.core.BurstQrTransport
import de.oai.optilink.core.FramePacket
import de.oai.optilink.core.FrameType
import de.oai.optilink.core.MdsErasure
import de.oai.optilink.core.TransferMetadata
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

internal data class PreparedQrFrame(
    val matrix: BitMatrix,
    val status: String,
    val detail: String,
    val progress: Int,
    val progressIndeterminate: Boolean,
)

internal class SenderEngine(
    private val context: Context,
    private val uri: Uri,
    private val profile: BurstProfile,
    private val onFrame: (PreparedQrFrame) -> Unit,
    private val onStatus: (String, Int, Boolean) -> Unit,
    private val onError: (String) -> Unit,
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var source: SeekableInput? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread(::runSender, "OptiLink-Sender").apply { start() }
    }

    private fun runSender() {
        try {
            onStatus("Datei wird vorbereitet …", 0, true)
            val input = SeekableInput.open(context, uri) {
                onStatus("Datei wird lokal vorbereitet: ${formatBytes(it)}", 0, true)
            }
            source = input
            val hash = input.sha256 { done ->
                onStatus("Integrität wird berechnet: ${percent(done, input.size)}", percentInt(done, input.size), false)
            }

            val sessionId = SecureRandom().nextLong()
            val sourceSymbolsPerBlock = MdsErasure.MAX_SOURCE_SYMBOLS
            val metadata = TransferMetadata(
                fileName = input.displayName,
                mimeType = input.mimeType,
                fileSize = input.size,
                fileSha256 = hash,
                symbolSize = profile.symbolSize,
                sourceSymbolsPerBlock = sourceSymbolsPerBlock,
            )
            val metadataPayload = metadata.encode()
            val blockBytes = profile.symbolSize.toLong() * sourceSymbolsPerBlock
            val totalBlocks = if (input.size == 0L) 0 else ((input.size + blockBytes - 1) / blockBytes).toInt()

            var sequence = 0
            var displayedFrames = 0L
            var equationRound = 0
            var windowStart = 0
            var blockOffset = 0
            var epoch = 0
            val periodNanos = 1_000_000_000L / profile.framesPerSecond
            var deadline = System.nanoTime()

            while (running.get()) {
                val currentWindowEnd = if (totalBlocks > 0) minOf(totalBlocks, windowStart + WINDOW_BLOCKS) else 0
                val maxSourcesInWindow = if (totalBlocks > 0) {
                    (windowStart until currentWindowEnd).maxOf { index ->
                        sourceCountForBlock(input.size, index, profile.symbolSize, sourceSymbolsPerBlock)
                    }
                } else 0
                val sendMetadata = displayedFrames < INITIAL_METADATA_FRAMES ||
                    totalBlocks == 0 ||
                    (displayedFrames - INITIAL_METADATA_FRAMES) % METADATA_INTERVAL == 0L

                val packet: FramePacket
                val status: String
                val detail: String
                val progress: Int
                val indeterminate: Boolean

                if (sendMetadata) {
                    packet = FramePacket(FrameType.META, profile.wireId, sessionId, sequence, -1, equationRound, metadataPayload)
                    status = if (totalBlocks == 0) "Leere Datei wird angekündigt" else "Empfänger wird synchronisiert …"
                    detail = "${input.displayName} · ${formatBytes(input.size)} · ${profile.label}"
                    progress = systematicProgress(windowStart, equationRound, blockOffset, totalBlocks, sourceSymbolsPerBlock)
                    indeterminate = epoch > 0 || equationRound >= maxSourcesInWindow
                } else {
                    val windowEnd = currentWindowEnd
                    val blockIndex = windowStart + blockOffset
                    val symbols = loadBlock(input, blockIndex, profile.symbolSize, sourceSymbolsPerBlock)
                    val sourceCount = symbols.size
                    val equationId = if (equationRound < sourceCount) {
                        equationRound
                    } else {
                        sourceCount + ((epoch * (sourceSymbolsPerBlock + REPAIR_ROUNDS) + equationRound - sourceCount) % (MdsErasure.MAX_EQUATIONS - sourceCount))
                    }
                    val value = MdsErasure.encode(symbols, equationId)
                    packet = FramePacket(FrameType.DATA, profile.wireId, sessionId, sequence, blockIndex, equationId, value)
                    progress = systematicProgress(windowStart, equationRound, blockOffset, totalBlocks, sourceSymbolsPerBlock)
                    indeterminate = epoch > 0 || equationRound >= maxSourcesInWindow
                    status = if (indeterminate) {
                        "Reparatursymbole · Fenster ${windowStart / WINDOW_BLOCKS + 1}"
                    } else {
                        "Senden: $progress %"
                    }
                    detail = "Block ${blockIndex + 1}/$totalBlocks · Symbol ${equationRound + 1} · ${profile.framesPerSecond} Bilder/s"

                    blockOffset++
                    if (windowStart + blockOffset >= windowEnd) {
                        blockOffset = 0
                        equationRound++
                        if (equationRound >= maxSourcesInWindow + REPAIR_ROUNDS) {
                            equationRound = 0
                            windowStart = windowEnd
                            if (windowStart >= totalBlocks) {
                                windowStart = 0
                                epoch++
                            }
                        }
                    }
                }

                val matrix = BurstQrTransport.render(packet, profile)
                onFrame(PreparedQrFrame(matrix, status, detail, progress, indeterminate))
                displayedFrames++
                sequence = (sequence + 1) and 0x7fffffff

                deadline += periodNanos
                val sleepNanos = deadline - System.nanoTime()
                if (sleepNanos > 0) {
                    Thread.sleep(sleepNanos / 1_000_000, (sleepNanos % 1_000_000).toInt())
                } else if (sleepNanos < -periodNanos * 3) {
                    deadline = System.nanoTime()
                }
            }
        } catch (_: InterruptedException) {
            // Normal shutdown.
        } catch (t: Throwable) {
            if (running.get()) onError(t.message ?: "Senden fehlgeschlagen")
        } finally {
            running.set(false)
            source?.close()
            source = null
        }
    }

    private fun loadBlock(input: SeekableInput, blockIndex: Int, symbolSize: Int, maxSymbols: Int): List<ByteArray> {
        val blockStart = blockIndex.toLong() * symbolSize * maxSymbols
        val remaining = input.size - blockStart
        val count = ceil(
            remaining.coerceAtMost(symbolSize.toLong() * maxSymbols) / symbolSize.toDouble(),
        ).toInt().coerceAtLeast(1)
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
        private const val INITIAL_METADATA_FRAMES = 72L
        private const val METADATA_INTERVAL = 45L

        private const val WINDOW_BLOCKS = 6
        private const val REPAIR_ROUNDS = 14

        private fun systematicProgress(windowStart: Int, round: Int, blockOffset: Int, blocks: Int, rounds: Int): Int {
            if (blocks <= 0) return 100
            val completedSourceFrames = windowStart.toLong() * rounds
            val currentWindowWidth = minOf(WINDOW_BLOCKS, blocks - windowStart).coerceAtLeast(1)
            val currentFrames = minOf(round, rounds).toLong() * currentWindowWidth + blockOffset
            val total = rounds.toLong() * blocks
            return ((completedSourceFrames + currentFrames) * 100 / total).coerceIn(0, 100).toInt()
        }

        private fun sourceCountForBlock(fileSize: Long, blockIndex: Int, symbolSize: Int, maxSymbols: Int): Int {
            val blockBytes = symbolSize.toLong() * maxSymbols
            val remaining = fileSize - blockIndex.toLong() * blockBytes
            return ((minOf(remaining, blockBytes) + symbolSize - 1) / symbolSize).toInt().coerceAtLeast(1)
        }

        private fun percent(done: Long, total: Long): String = "${percentInt(done, total)} %"
        private fun percentInt(done: Long, total: Long): Int = if (total <= 0) 100 else (done * 100 / total).coerceIn(0, 100).toInt()
        private fun formatBytes(value: Long): String = when {
            value >= 1L shl 30 -> "%.1f GiB".format(value / (1L shl 30).toDouble())
            value >= 1L shl 20 -> "%.1f MiB".format(value / (1L shl 20).toDouble())
            value >= 1L shl 10 -> "%.1f KiB".format(value / (1L shl 10).toDouble())
            else -> "$value B"
        }
    }
}
