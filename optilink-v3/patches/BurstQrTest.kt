package de.oai.optilink.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.EnumMap
import java.util.Random

class BurstQrTest {
    @Test
    fun base45RoundTripsBinaryData() {
        val random = Random(0x4f4c33)
        for (size in 0..2048) {
            val input = ByteArray(size).also(random::nextBytes)
            assertArrayEquals(input, Base45.decode(Base45.encode(input)))
        }
    }

    @Test
    fun packetRoundTripsThroughQrPixels() {
        val profile = BurstProfile.TAB_S5E_RECEIVER
        val payload = ByteArray(profile.symbolSize) { index -> (index * 37 + 11).toByte() }
        val packet = FramePacket(FrameType.DATA, profile.wireId, 0x1020304050607080L, 91, 3, 7, payload)
        val text = BurstQrTransport.encodeText(packet, profile)
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 4)
        }
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 900, 900, hints)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix[x, y]) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val result = QRCodeReader().decode(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels))),
        )
        val decoded = BurstQrTransport.decodeText(result.text)
        assertNotNull(decoded)
        assertEquals(packet.type, decoded!!.type)
        assertEquals(packet.sessionId, decoded.sessionId)
        assertEquals(packet.sequence, decoded.sequence)
        assertEquals(packet.blockIndex, decoded.blockIndex)
        assertEquals(packet.equationId, decoded.equationId)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun anySourceCountDistinctMdsRowsRecover() {
        val random = Random(1337)
        for (sourceCount in listOf(1, 5, 17, 32)) {
            val symbols = List(sourceCount) { ByteArray(880).also(random::nextBytes) }
            val decoder = MdsErasure.Decoder(sourceCount, 880)
            val equations = (0 until 255).shuffled(kotlin.random.Random(sourceCount)).take(sourceCount)
            equations.forEach { equation -> decoder.offer(equation, MdsErasure.encode(symbols, equation)) }
            val recovered = decoder.result()
            assertNotNull(recovered)
            symbols.indices.forEach { assertArrayEquals(symbols[it], recovered!![it]) }
        }
    }
}
