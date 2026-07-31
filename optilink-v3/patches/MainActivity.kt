package de.oai.optilink.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import de.oai.optilink.core.BurstProfile
import de.oai.optilink.core.TransferMetadata

class MainActivity : Activity() {
    private var sender: SenderEngine? = null
    private var camera: CameraReceiver? = null
    private var receiver: ReceiverEngine? = null
    private var destinationRequestedSession: Long? = null
    private var previousBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.statusBarColor = Palette.BACKGROUND
        window.navigationBarColor = Palette.BACKGROUND
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        showHome()
    }

    private fun showHome() {
        stopTransfer()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        showSystemBars()
        val content = vertical(14).apply {
            pad(24)
            addView(text("OptiLink", 34f, bold = true))
            addView(text("Optische Dateiübertragung 3.0", 18f, bold = true, color = Palette.MUTED))
            addView(text(
                "Animierte QR-Symbole mit blockweiser MDS-Reparatur. Ohne Netzwerk, Cloud, Konto oder Telemetrie.",
                16f,
                color = Palette.MUTED,
            ).apply { setLineSpacing(0f, 1.16f) })
            addView(infoCard())
            addView(actionButton("Datei senden").apply { setOnClickListener { pickFile() } })
            addView(actionButton("Datei empfangen", primary = false).apply { setOnClickListener { requestReceive() } })
            addView(text("Hochformat · Android 15 oder neuer · Kamera nur beim Empfangen", 13f, color = Palette.MUTED).apply {
                gravity = Gravity.CENTER
            })
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Palette.BACKGROUND)
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        setContentView(scroll)
        applySafeInsets(content)
    }

    private fun infoCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        pad(18)
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(Palette.SURFACE)
            setStroke(dp(1), Palette.LINE)
        }
        addView(text("Burst-QR mit Reparatursymbolen", 18f, bold = true))
        addView(text(
            "Jedes Kamerabild ist ein standardkonformer QR-Code. Verlorene Bilder werden durch systematische MDS-Symbole ersetzt; SHA-256 bestätigt die fertige Datei.",
            14f,
            color = Palette.MUTED,
        ).apply { setPadding(0, dp(8), 0, 0); setLineSpacing(0f, 1.14f) })
    }

    private fun pickFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }, REQUEST_OPEN_FILE)
    }

    private fun chooseTarget(uri: Uri) {
        val labels = arrayOf(
            "Pixel 9a / Pixel 8a\n880 Byte · 9 Bilder/s",
            "Galaxy Tab S5e\n680 Byte · 8 Bilder/s",
            "Stabil / schwierige Bedingungen\n520 Byte · 7 Bilder/s",
        )
        val profiles = arrayOf(BurstProfile.PIXEL_RECEIVER, BurstProfile.TAB_S5E_RECEIVER, BurstProfile.UNIVERSAL)
        AlertDialog.Builder(this)
            .setTitle("Empfangsgerät")
            .setItems(labels) { _, index -> startSender(uri, profiles[index]) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun startSender(uri: Uri, profile: BurstProfile) {
        stopTransfer()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        hideSystemBars()
        previousBrightness = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = 1f }

        val frameView = QrFrameView(this)
        val status = text("Datei wird vorbereitet …", 15f, bold = true, color = Color.WHITE)
        val detail = text("", 12f, color = 0xffcbd2da.toInt()).apply { maxLines = 2 }
        val progress = transferProgressBar(indeterminate = true)
        val stop = actionButton("Beenden", primary = false).apply {
            minHeight = dp(46)
            setOnClickListener { showHome() }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply { setColor(0xff101318.toInt()) }
            addView(status)
            addView(detail, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            })
            addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)).apply { topMargin = dp(12) })
            addView(stop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(frameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)

        sender = SenderEngine(
            context = this,
            uri = uri,
            profile = profile,
            onFrame = { frame -> runOnUiThread {
                frameView.show(frame.matrix)
                status.text = frame.status
                detail.text = frame.detail
                progress.isIndeterminate = frame.progressIndeterminate
                if (!frame.progressIndeterminate) progress.progress = frame.progress
            } },
            onStatus = { message, value, indeterminate -> runOnUiThread {
                status.text = message
                progress.isIndeterminate = indeterminate
                if (!indeterminate) progress.progress = value
            } },
            onError = { message -> runOnUiThread { showFatal(message) } },
        ).also { it.start() }
    }

    private fun requestReceive() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startReceiver()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
    }

    private fun startReceiver() {
        stopTransfer()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        hideSystemBars()

        val texture = TextureView(this)
        val guide = ScanGuideView(this)
        val preview = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(texture, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(guide, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val status = text("Kamera wird vorbereitet …", 15f, bold = true, color = Color.WHITE)
        val detail = text("Den vollständigen QR-Code frontal in den Rahmen halten", 12f, color = 0xffcbd2da.toInt()).apply { maxLines = 2 }
        val progress = transferProgressBar(indeterminate = true)
        val stop = actionButton("Beenden", primary = false).apply {
            minHeight = dp(46)
            setOnClickListener { showHome() }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply { setColor(0xff101318.toInt()) }
            addView(status)
            addView(detail, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            })
            addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)).apply { topMargin = dp(12) })
            addView(stop, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)

        receiver = ReceiverEngine(
            context = this,
            onMetadata = { sessionId, metadata, resumed -> runOnUiThread {
                status.setTextColor(Color.WHITE)
                if (resumed) {
                    destinationRequestedSession = sessionId
                    status.text = "Fortsetzen: ${metadata.fileName}"
                    progress.isIndeterminate = false
                } else if (destinationRequestedSession != sessionId) {
                    destinationRequestedSession = sessionId
                    status.text = "Datei erkannt: ${metadata.fileName}"
                    detail.text = "${formatBytes(metadata.fileSize)} · Speicherort auswählen"
                    createDestination(metadata)
                }
            } },
            onStatus = { message -> runOnUiThread { status.setTextColor(Color.WHITE); status.text = message } },
            onProgress = { state -> runOnUiThread {
                progress.isIndeterminate = false
                progress.progress = state.percent
                detail.text = "${formatBytes(state.estimatedBytes)} / ${formatBytes(state.totalBytes)} · ${state.completedBlocks}/${state.totalBlocks} Blöcke"
            } },
            onComplete = { uri -> runOnUiThread {
                progress.isIndeterminate = false
                progress.progress = 100
                AlertDialog.Builder(this)
                    .setTitle("Übertragung abgeschlossen")
                    .setMessage("Die Datei wurde vollständig empfangen und per SHA-256 geprüft.\n\n$uri")
                    .setPositiveButton("Fertig") { _, _ -> showHome() }
                    .setCancelable(false)
                    .show()
            } },
            onError = { message -> runOnUiThread {
                status.setTextColor(Palette.ERROR)
                status.text = message
                progress.isIndeterminate = false
            } },
        )

        camera = CameraReceiver(
            context = this,
            textureView = texture,
            onFrame = { packet, stats ->
                receiver?.accept(packet)
                runOnUiThread {
                    guide.signalSeen()
                    if (progress.isIndeterminate) {
                        detail.text = "QR erkannt · ${stats.uniqueFrames} eindeutige Bilder · %.1f/s".format(stats.decodedFramesPerSecond)
                    }
                }
            },
            onProfile = { profile -> runOnUiThread {
                val fps = profile.fpsRange?.let { "${it.lower}–${it.upper} fps" } ?: "variable Bildrate"
                status.text = "Bereit · ${profile.yuvSize.width}×${profile.yuvSize.height} · $fps"
            } },
            onError = { message -> runOnUiThread { showFatal(message) } },
        ).also { it.start() }
    }

    private fun transferProgressBar(indeterminate: Boolean): ProgressBar = ProgressBar(
        this,
        null,
        android.R.attr.progressBarStyleHorizontal,
    ).apply {
        max = 100
        progress = 0
        isIndeterminate = indeterminate
        progressTintList = ColorStateList.valueOf(0xff46d99b.toInt())
        progressBackgroundTintList = ColorStateList.valueOf(0xff343a42.toInt())
        indeterminateTintList = ColorStateList.valueOf(0xff46d99b.toInt())
    }

    private fun createDestination(metadata: TransferMetadata) {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = metadata.mimeType
            putExtra(Intent.EXTRA_TITLE, metadata.fileName)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }, REQUEST_CREATE_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            if (requestCode == REQUEST_CREATE_FILE) showHome()
            return
        }
        val uri = data?.data ?: return
        val persistableFlags = data.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (persistableFlags != 0) runCatching { contentResolver.takePersistableUriPermission(uri, persistableFlags) }
        when (requestCode) {
            REQUEST_OPEN_FILE -> chooseTarget(uri)
            REQUEST_CREATE_FILE -> receiver?.attachDestination(uri)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startReceiver()
            else AlertDialog.Builder(this)
                .setTitle("Kamerazugriff erforderlich")
                .setMessage("Optischer Empfang benötigt die Rückkamera. Senden funktioniert ohne Kameraberechtigung.")
                .setPositiveButton("Einstellungen") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }
    }

    private fun showFatal(message: String) {
        AlertDialog.Builder(this)
            .setTitle("OptiLink")
            .setMessage(message)
            .setPositiveButton("Zurück") { _, _ -> showHome() }
            .setCancelable(false)
            .show()
    }

    private fun stopTransfer() {
        sender?.close(); sender = null
        camera?.close(); camera = null
        receiver?.close(); receiver = null
        if (previousBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            window.attributes = window.attributes.apply { screenBrightness = previousBrightness }
            previousBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        destinationRequestedSession = null
    }

    private fun hideSystemBars() {
        window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        window.insetsController?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun showSystemBars() {
        window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
    }

    private fun applySafeInsets(view: View) {
        view.setOnApplyWindowInsetsListener { target, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            target.setPadding(dp(24) + bars.left, dp(24) + bars.top, dp(24) + bars.right, dp(24) + bars.bottom)
            insets
        }
    }

    private fun formatBytes(value: Long): String = when {
        value >= 1L shl 30 -> "%.1f GiB".format(value / (1L shl 30).toDouble())
        value >= 1L shl 20 -> "%.1f MiB".format(value / (1L shl 20).toDouble())
        value >= 1L shl 10 -> "%.1f KiB".format(value / (1L shl 10).toDouble())
        else -> "$value B"
    }

    override fun onBackPressed() {
        if (sender != null || camera != null) showHome() else super.onBackPressed()
    }

    override fun onDestroy() {
        stopTransfer()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_OPEN_FILE = 301
        private const val REQUEST_CREATE_FILE = 302
        private const val REQUEST_CAMERA = 303
    }
}
