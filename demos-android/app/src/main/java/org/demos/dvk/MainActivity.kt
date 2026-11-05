package org.demos.dvk

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Native Android 17 frontend to the authentic SIMH-DVK hardware emulator. */
class MainActivity : Activity() {
    companion object {
        private val GUEST_CHARSET: Charset = Charset.forName("KOI8-R")
        private const val IMAGE_BYTES = 20_003_840L
        init { System.loadLibrary("ptybridge") }
    }

    private external fun nativeSpawn(executable: String, dir: String): Long
    private external fun nativeRead(handle: Long, buffer: ByteArray): Int
    private external fun nativeWrite(handle: Long, bytes: ByteArray): Int
    private external fun nativeRequestStop(handle: Long)
    private external fun nativeClose(handle: Long)

    private val reader = Executors.newSingleThreadExecutor()
    private val guest = TerminalBuffer()
    @Volatile private var connection = 0L
    @Volatile private var starting = false
    private var bootReturnSent = false
    private var bootDateSent = false
    private var fileCheckAccepted = false

    private lateinit var status: TextView
    private lateinit var terminal: TextView
    private lateinit var terminalScroll: ScrollView
    private lateinit var input: EditText
    private lateinit var start: Button
    private lateinit var stop: Button

    private val ink = Color.rgb(225, 238, 231)
    private val muted = Color.rgb(146, 168, 159)
    private val accent = Color.rgb(112, 212, 164)
    private val background = Color.rgb(8, 16, 13)
    private val panel = Color.rgb(17, 29, 24)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

    private fun label(value: String, sp: Float, color: Int = ink): TextView =
        TextView(this).apply {
            text = value
            textSize = sp
            setTextColor(color)
        }

    private fun button(value: String, act: () -> Unit): Button =
        Button(this).apply {
            text = value
            isAllCaps = false
            setOnClickListener { act() }
        }

    private fun buildUi() {
        window.navigationBarColor = background
        window.statusBarColor = background
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@MainActivity.background)
            setPadding(dp(14), dp(12), dp(14), dp(10))
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(dp(14), bars.top + dp(12), dp(14), bars.bottom + dp(10))
                insets
            }
        }
        setContentView(root)

        root.addView(label("DEMOS 3.0", 26f, ink).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        root.addView(label("DVK / PDP-11 · ARM64 · offline", 12f, muted))
        status = label("Bereit · Gast ist ausgeschaltet", 13f, accent)
        status.setPadding(0, dp(8), 0, dp(10))
        root.addView(status)

        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        start = button("DEMOS starten") { startSystem() }
        stop = button("Herunterfahren") { requestShutdown() }
        stop.isEnabled = false
        toolbar.addView(start, LinearLayout.LayoutParams(0, dp(49), 1f))
        toolbar.addView(stop, LinearLayout.LayoutParams(0, dp(49), 1f))
        root.addView(toolbar)

        terminal = label("Bereit. Tippe auf „DEMOS starten“.\n", 12f, ink).apply {
            typeface = Typeface.MONOSPACE
            setPadding(dp(10), dp(12), dp(10), dp(12))
            setBackgroundColor(panel)
            setTextIsSelectable(true)
            setHorizontallyScrolling(true)
            gravity = Gravity.TOP or Gravity.START
        }
        terminalScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(terminal)
        }
        val horizontal = HorizontalScrollView(this).apply {
            isFillViewport = true
            addView(terminalScroll)
        }
        root.addView(horizontal, LinearLayout.LayoutParams(-1, 0, 1f))

        val cmdRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            setSingleLine(true)
            hint = "Eingabe an DEMOS"
            textSize = 15f
            setTextColor(ink)
            setHintTextColor(muted)
            setOnEditorActionListener { _, _, event ->
                if (event == null || event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    sendLine()
                    true
                } else false
            }
        }
        cmdRow.addView(input, LinearLayout.LayoutParams(0, dp(54), 1f))
        cmdRow.addView(button("↵") { sendLine() }, LinearLayout.LayoutParams(dp(60), dp(54)))
        root.addView(cmdRow)

        val modifiers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modifiers.addView(button("CTRL+C") { send(byteArrayOf(3)) }, LinearLayout.LayoutParams(0, dp(46), 1f))
        modifiers.addView(button("ESC") { send(byteArrayOf(27)) }, LinearLayout.LayoutParams(0, dp(46), 1f))
        modifiers.addView(button("ENTER") { send(byteArrayOf(13)) }, LinearLayout.LayoutParams(0, dp(46), 1f))
        root.addView(modifiers)
    }

    private fun displayStatus(message: String) {
        runOnUiThread {
            if (!isDestroyed) status.text = message
        }
    }

    private fun ensureGuestMedia(): File {
        val dir = File(filesDir, "machine")
        check(dir.isDirectory || dir.mkdirs()) { "App-Datenspeicher kann nicht erstellt werden" }
        val disk = File(dir, "harddisk.img")
        if (disk.exists()) {
            check(disk.length() == IMAGE_BYTES) { "Festplattenkopie hat eine ungültige Größe" }
        } else {
            val tmp = File(dir, "harddisk.img.tmp")
            try {
                assets.open("harddisk.img").use { source ->
                    FileOutputStream(tmp).use { destination ->
                        source.copyTo(destination)
                        destination.fd.sync()
                    }
                }
                check(tmp.length() == IMAGE_BYTES) { "Ungültiges DEMOS-Festplattenabbild" }
                check(tmp.renameTo(disk)) { "Datenträger konnte nicht atomar gespeichert werden" }
            } finally {
                tmp.delete()
            }
        }

        val floppy = File(dir, "floppy.img")
        if (!floppy.exists()) {
            RandomAccessFile(floppy, "rw").use { it.setLength(819_200L) }
        }
        File(dir, "boot.ini").writeText(
            "set LPT disable\n" +
            "set DZ disable\n" +
            "set cpu 11/23 256k cis idle\n" +
            "attach kmd0 floppy.img\n" +
            "attach kgd0 harddisk.img\n" +
            "boot kgd0\n",
            Charsets.US_ASCII
        )
        return dir
    }

    private fun startSystem() {
        if (starting || connection != 0L) return
        starting = true
        bootReturnSent = false
        bootDateSent = false
        fileCheckAccepted = false
        start.isEnabled = false
        displayStatus("Bereite DEMOS-Datenträger vor …")
        terminal.text = ""
        reader.execute {
            var handle = 0L
            try {
                val directory = ensureGuestMedia()
                val binary = File(applicationInfo.nativeLibraryDir, "libpdp11.so")
                check(binary.isFile && binary.canExecute()) { "DVK-Emulator ist nicht ausführbar" }
                handle = nativeSpawn(binary.absolutePath, directory.absolutePath)
                check(handle != 0L) { "Konsolenprozess konnte nicht gestartet werden" }
                connection = handle
                starting = false
                runOnUiThread {
                    if (!isDestroyed) {
                        start.isEnabled = false
                        stop.isEnabled = true
                    }
                }
                displayStatus("DVK-Emulator läuft · Gast startet")
                val buffer = ByteArray(2048)
                var idleReads = 0
                while (!Thread.currentThread().isInterrupted) {
                    val len = nativeRead(handle, buffer)
                    if (len < 0) break
                    if (len == 0) {
                        idleReads++
                        continue
                    }
                    idleReads = 0
                    val chunk = String(buffer, 0, len, GUEST_CHARSET)
                    Log.i("DEMOS-guest", chunk.take(1024))
                    guest.accept(chunk)
                    if (!bootReturnSent && guest.contains("BOOT (* VERSION")) {
                        bootReturnSent = true
                        nativeWrite(handle, byteArrayOf(13))
                    }
                    if (!bootDateSent && guest.contains("ILI (")) {
                        bootDateSent = true
                        val localDate = SimpleDateFormat("MMddHHmm", Locale.US).format(Date())
                        nativeWrite(handle, (localDate + "\r").toByteArray(Charsets.US_ASCII))
                        displayStatus("DEMOS konfiguriert Systemzeit")
                    }
                    if (!fileCheckAccepted && guest.contains("[DA]")) {
                        fileCheckAccepted = true
                        nativeWrite(handle, byteArrayOf(13))
                        displayStatus("DEMOS prüft und mountet Datenträger")
                    }
                    if (guest.contains("Имя:") || guest.contains("login:")) {
                        displayStatus("DEMOS gestartet · Anmeldung bereit")
                    }
                    val snapshot = guest.snapshot()
                    runOnUiThread {
                        if (!isDestroyed) {
                            terminal.text = snapshot
                            terminalScroll.post { terminalScroll.fullScroll(View.FOCUS_DOWN) }
                        }
                    }
                }
                displayStatus("Emulator wurde beendet")
            } catch (error: Exception) {
                val message = error.message ?: error.javaClass.simpleName
                displayStatus("Startfehler · " + message)
                runOnUiThread { if (!isDestroyed) terminal.text = "FEHLER: " + message }
                Log.e("DEMOS-guest", "Start fehlgeschlagen", error)
            } finally {
                if (handle != 0L) nativeClose(handle)
                connection = 0L
                starting = false
                runOnUiThread {
                    if (!isDestroyed) {
                        start.isEnabled = true
                        stop.isEnabled = false
                    }
                }
            }
        }
    }

    private fun send(bytes: ByteArray) {
        val handle = connection
        if (handle != 0L) nativeWrite(handle, bytes)
    }

    private fun sendLine() {
        val text = input.text.toString()
        input.text.clear()
        send((text + "\r").toByteArray(GUEST_CHARSET))
    }

    private fun requestShutdown() {
        if (connection == 0L) return
        displayStatus("Herunterfahren angefordert · auf SYNCING DISKS ... DONE achten")
        send("/etc/reboot -h\r".toByteArray(GUEST_CHARSET))
    }

    override fun onDestroy() {
        if (connection != 0L) nativeRequestStop(connection)
        reader.shutdownNow()
        super.onDestroy()
    }
}
