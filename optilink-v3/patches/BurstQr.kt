package de.oai.optilink.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

/**
 * Device-tuned, deliberately conservative animated QR profiles.
 *
 * The older Galaxy Tab S5e receives a less dense symbol from the smaller Pixel
 * display. A Pixel receives a denser symbol from the larger Tab display.
 */
enum class BurstProfile(
    val wireId: Int,
    val symbolSize: Int,
    val framesPerSecond: Int,
    val label: String,
) {
    UNIVERSAL(0, 520, 7, "Stabil"),
    TAB_S5E_RECEIVER(1, 680, 8, "Galaxy Tab S5e"),
    PIXEL_RECEIVER(2, 880, 9, "Pixel 8a / 9a");

    val packetCapacity: Int get() = FramePacket.OVERHEAD_BYTES + symbolSize

    companion object {
        fun fromWireId(id: Int): BurstProfile? = entries.firstOrNull { it.wireId == id }
    }
}

/** RFC 9285 Base45, which keeps QR data in the efficient alphanumeric mode. */
object Base45 {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"
    private val reverse = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, char -> table[char.code] = index }
    }

    fun encode(input: ByteArray): String {
        val out = StringBuilder((input.size * 3 + 1) / 2)
        var index = 0
        while (index + 1 < input.size) {
            val value = (input[index].toInt() and 0xff) * 256 + (input[index + 1].toInt() and 0xff)
            out.append(ALPHABET[value % 45])
            out.append(ALPHABET[(value / 45) % 45])
            out.append(ALPHABET[value / (45 * 45)])
            index += 2
        }
        if (index < input.size) {
            val value = input[index].toInt() and 0xff
            out.append(ALPHABET[value % 45])
            out.append(ALPHABET[value / 45])
        }
        return out.toString()
    }

    fun decode(input: String): ByteArray? = runCatching {
        if (input.length % 3 == 1) return null
        val output = ByteArray((input.length / 3) * 2 + if (input.length % 3 == 2) 1 else 0)
        var source = 0
        var target = 0
        while (source + 2 < input.length) {
            val a = value(input[source])
            val b = value(input[source + 1])
            val c = value(input[source + 2])
            val combined = a + b * 45 + c * 45 * 45
            if (combined > 0xffff) return null
            output[target++] = (combined / 256).toByte()
            output[target++] = combined.toByte()
            source += 3
        }
        if (source < input.length) {
            val combined = value(input[source]) + value(input[source + 1]) * 45
            if (combined > 0xff) return null
            output[target] = combined.toByte()
        }
        output
    }.getOrNull()

    private fun value(char: Char): Int {
        if (char.code !in reverse.indices) error("Invalid Base45 character")
        return reverse[char.code].also { if (it < 0) error("Invalid Base45 character") }
    }
}

object BurstQrTransport {
    private const val PREFIX = "OL3:"
    private val writer = QRCodeWriter()

    fun encodeText(packet: FramePacket, profile: BurstProfile): String {
        val raw = packet.encode(profile.packetCapacity)
        return PREFIX + Base45.encode(raw)
    }

    fun decodeText(text: String): FramePacket? {
        if (!text.startsWith(PREFIX)) return null
        val raw = Base45.decode(text.substring(PREFIX.length)) ?: return null
        return FramePacket.decode(raw)
    }

    @Synchronized
    fun render(packet: FramePacket, profile: BurstProfile): BitMatrix {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 3)
        }
        // Width/height 1 asks ZXing for the native module matrix. The Android
        // view performs exact integer scaling, avoiding interpolation blur.
        return writer.encode(encodeText(packet, profile), BarcodeFormat.QR_CODE, 1, 1, hints)
    }
}
