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
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

internal data class CameraRuntimeProfile(
    val cameraId: String,
    val yuvSize: Size,
    val fpsRange: Range<Int>?,
    val hardwareLevel: Int,
)

internal class CameraReceiver(
    private val context: Context,
    private val textureView: TextureView,
    private val decoder: ColorFrameDecoder,
    private val onFrame: (DecodedOpticalFrame) -> Unit,
    private val onProfile: (CameraRuntimeProfile) -> Unit,
    private val onError: (String) -> Unit,
) : AutoCloseable {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val cameraThread = HandlerThread("OptiLink-Camera").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val decoderExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "OptiLink-Decoder") }
    private val decoding = AtomicBoolean(false)

    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var previewSize: Size? = null
    private var closed = false

    fun start() {
        textureView.scaleX = 1f
        textureView.scaleY = 1f
        textureView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            previewSize?.let(::configurePreviewTransform)
        }
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
            previewSize = size
            val ranges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList().orEmpty()
            val fps = chooseFpsRange(ranges)
            val level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: -1
            onProfile(CameraRuntimeProfile(cameraId, size, fps, level))
            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 3).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    if (!decoding.compareAndSet(false, true)) { image.close(); return@setOnImageAvailableListener }
                    decoderExecutor.execute {
                        image.use {
                            val wrapped = YuvFrame.wrap(it)
                            try { decoder.decode(wrapped)?.let { decoded -> onFrame(decoded) } }
                            catch (_: Throwable) { /* A malformed optical frame is simply dropped. */ }
                            finally { decoding.set(false) }
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
        } catch (e: CameraAccessException) { onError(e.message ?: "Camera2-Fehler") }
    }

    private fun configurePreviewTransform(size: Size) {
        textureView.post {
            val viewWidth = textureView.width.toFloat()
            val viewHeight = textureView.height.toFloat()
            if (viewWidth <= 0f || viewHeight <= 0f) return@post

            val rotation = textureView.display?.rotation ?: Surface.ROTATION_0
            val viewRect = RectF(0f, 0f, viewWidth, viewHeight)
            val centerX = viewRect.centerX()
            val centerY = viewRect.centerY()
            val matrix = Matrix()

            when (rotation) {
                Surface.ROTATION_90, Surface.ROTATION_270 -> {
                    val bufferRect = RectF(0f, 0f, size.height.toFloat(), size.width.toFloat())
                    bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
                    matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                    val scale = maxOf(viewHeight / size.height, viewWidth / size.width)
                    matrix.postScale(scale, scale, centerX, centerY)
                    val degrees = if (rotation == Surface.ROTATION_90) -90f else 90f
                    matrix.postRotate(degrees, centerX, centerY)
                }
                Surface.ROTATION_180 -> matrix.postRotate(180f, centerX, centerY)
                else -> {
                    val scale = maxOf(viewWidth / size.width, viewHeight / size.height)
                    matrix.postScale(scale, scale, centerX, centerY)
                }
            }

            textureView.scaleX = 1f
            textureView.scaleY = 1f
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
        previewSize = null
    }

    override fun close() {
        if (closed) return
        closed = true
        stopCamera()
        decoderExecutor.shutdownNow()
        cameraThread.quitSafely()
    }
}
