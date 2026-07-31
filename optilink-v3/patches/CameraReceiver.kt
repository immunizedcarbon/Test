package de.oai.optilink.android

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.ReaderException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import de.oai.optilink.core.BurstQrTransport
import de.oai.optilink.core.FramePacket
import java.util.EnumMap
import java.util.LinkedHashSet
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

internal data class CameraRuntimeProfile(
    val cameraId: String,
    val yuvSize: Size,
    val fpsRange: Range<Int>?,
    val hardwareLevel: Int,
)

internal data class CameraScanStats(
    val uniqueFrames: Int,
    val decodedFramesPerSecond: Double,
)

internal class CameraReceiver(
    private val context: Context,
    private val textureView: TextureView,
    private val onFrame: (FramePacket, CameraScanStats) -> Unit,
    private val onProfile: (CameraRuntimeProfile) -> Unit,
    private val onError: (String) -> Unit,
) : AutoCloseable {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val cameraThread = HandlerThread("OptiLink-Camera").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val decoderExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "OptiLink-QR") }
    private val decoding = AtomicBoolean(false)
    private val reader = MultiFormatReader().apply {
        setHints(EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            put(DecodeHintType.TRY_HARDER, true)
            put(DecodeHintType.CHARACTER_SET, "ISO-8859-1")
        })
    }
    private val recentSequences = object : LinkedHashSet<Int>() {
        override fun add(element: Int): Boolean {
            val added = super.add(element)
            if (size > 96) remove(first())
            return added
        }
    }

    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var closed = false
    private var previewSize: Size? = null
    private var sensorOrientation = 90
    private var uniqueFrames = 0
    private var rateWindowStart = System.nanoTime()
    private var rateWindowFrames = 0
    private var lastRate = 0.0

    fun start() {
        if (textureView.isAvailable) openCamera()
        else textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = openCamera()
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                previewSize?.let(::configurePreviewTransform)
            }
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { stopCamera(); return true }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        if (closed || camera != null) return
        try {
            val cameraId = selectBackCamera() ?: error("Keine rückseitige Kamera verfügbar")
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: error("Kamera meldet keine Stream-Konfiguration")
            val size = chooseYuvSize(map.getOutputSizes(ImageFormat.YUV_420_888)?.toList().orEmpty())
                ?: error("Kamera unterstützt keinen YUV_420_888-Stream")
            val ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList().orEmpty()
            val fps = chooseFpsRange(ranges)
            val level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            previewSize = size
            onProfile(CameraRuntimeProfile(cameraId, size, fps, level))

            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 3).apply {
                setOnImageAvailableListener({ source ->
                    val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                    if (!decoding.compareAndSet(false, true)) { image.close(); return@setOnImageAvailableListener }
                    decoderExecutor.execute {
                        image.use { frame ->
                            try {
                                decodeQr(frame)?.let { packet ->
                                    if (recentSequences.add(packet.sequence)) {
                                        uniqueFrames++
                                        rateWindowFrames++
                                        val now = System.nanoTime()
                                        val elapsed = now - rateWindowStart
                                        if (elapsed >= 1_000_000_000L) {
                                            lastRate = rateWindowFrames * 1_000_000_000.0 / elapsed
                                            rateWindowFrames = 0
                                            rateWindowStart = now
                                        }
                                        onFrame(packet, CameraScanStats(uniqueFrames, lastRate))
                                    }
                                }
                            } catch (_: Throwable) {
                                // Malformed or partially visible QR frames are expected and dropped.
                            } finally {
                                decoding.set(false)
                            }
                        }
                    }
                }, cameraHandler)
            }

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) { camera = device; createSession(device, size, fps) }
                override fun onDisconnected(device: CameraDevice) { device.close(); camera = null; onError("Kamera wurde getrennt") }
                override fun onError(device: CameraDevice, error: Int) { device.close(); camera = null; onError("Kamerafehler $error") }
            }, cameraHandler)
        } catch (t: Throwable) {
            onError(t.message ?: "Kamera konnte nicht geöffnet werden")
        }
    }

    private fun decodeQr(image: Image): FramePacket? {
        val luminance = copyLuminance(image)
        var source: LuminanceSource = PlanarYUVLuminanceSource(
            luminance,
            image.width,
            image.height,
            0,
            0,
            image.width,
            image.height,
            false,
        )
        repeat(4) {
            try {
                val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
                return BurstQrTransport.decodeText(result.text)
            } catch (_: ReaderException) {
                // Try the next physical orientation below.
            } finally {
                reader.reset()
            }
            if (!source.isRotateSupported) return null
            source = source.rotateCounterClockwise()
        }
        return null
    }

    private fun copyLuminance(image: Image): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val width = image.width
        val height = image.height
        val output = ByteArray(width * height)
        if (plane.pixelStride == 1 && plane.rowStride == width) {
            buffer.rewind()
            buffer.get(output, 0, minOf(output.size, buffer.remaining()))
            return output
        }
        for (y in 0 until height) {
            val row = y * plane.rowStride
            val target = y * width
            for (x in 0 until width) output[target + x] = buffer.get(row + x * plane.pixelStride)
        }
        return output
    }

    private fun createSession(device: CameraDevice, size: Size, fps: Range<Int>?) {
        val surfaceTexture = textureView.surfaceTexture ?: return
        surfaceTexture.setDefaultBufferSize(size.width, size.height)
        previewSurface = Surface(surfaceTexture)
        val readerSurface = imageReader?.surface ?: return
        try {
            device.createCaptureSession(listOf(previewSurface!!, readerSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    if (closed) { configured.close(); return }
                    session = configured
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(previewSurface!!)
                        addTarget(readerSurface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                        fps?.let { set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
                    }.build()
                    configured.setRepeatingRequest(request, null, cameraHandler)
                    configurePreviewTransform(size)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) = onError("Kamera-Stream konnte nicht konfiguriert werden")
            }, cameraHandler)
        } catch (e: CameraAccessException) {
            onError(e.message ?: "Camera2-Fehler")
        }
    }

    /** Center-crop transform for an unmirrored back-camera preview in portrait. */
    private fun configurePreviewTransform(size: Size) {
        textureView.post {
            if (textureView.width == 0 || textureView.height == 0) return@post
            val rotationDegrees = when (textureView.display?.rotation ?: Surface.ROTATION_0) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            val relative = (sensorOrientation - rotationDegrees + 360) % 360
            val viewRect = RectF(0f, 0f, textureView.width.toFloat(), textureView.height.toFloat())
            val rotated = relative == 90 || relative == 270
            val bufferRect = RectF(
                0f,
                0f,
                if (rotated) size.height.toFloat() else size.width.toFloat(),
                if (rotated) size.width.toFloat() else size.height.toFloat(),
            )
            val centerX = viewRect.centerX()
            val centerY = viewRect.centerY()
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val matrix = Matrix()
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = maxOf(
                textureView.width.toFloat() / bufferRect.width(),
                textureView.height.toFloat() / bufferRect.height(),
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(
                when (relative) {
                    90 -> 90f
                    180 -> 180f
                    270 -> -90f
                    else -> 0f
                },
                centerX,
                centerY,
            )
            textureView.setTransform(matrix)
        }
    }

    private fun selectBackCamera(): String? = manager.cameraIdList.firstOrNull {
        manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }

    private fun chooseYuvSize(sizes: List<Size>): Size? {
        val candidates = sizes.filter { it.width <= 1920 && it.height <= 1080 && it.width >= 960 && it.height >= 540 }
        return (candidates.ifEmpty { sizes }).minByOrNull {
            val aspectPenalty = abs(it.width.toDouble() / it.height - 16.0 / 9.0) * 10_000_000
            val areaPenalty = abs(it.width.toLong() * it.height - 1280L * 720)
            (aspectPenalty + areaPenalty).toLong()
        }
    }

    private fun chooseFpsRange(ranges: List<Range<Int>>): Range<Int>? = ranges
        .filter { it.contains(30) }
        .minWithOrNull(compareBy<Range<Int>> { it.upper - it.lower }.thenByDescending { it.lower })
        ?: ranges.maxByOrNull { it.upper }

    private fun stopCamera() {
        try { session?.stopRepeating() } catch (_: Throwable) {}
        session?.close(); session = null
        camera?.close(); camera = null
        imageReader?.close(); imageReader = null
        previewSurface?.release(); previewSurface = null
    }

    override fun close() {
        if (closed) return
        closed = true
        stopCamera()
        decoderExecutor.shutdownNow()
        cameraThread.quitSafely()
    }
}
