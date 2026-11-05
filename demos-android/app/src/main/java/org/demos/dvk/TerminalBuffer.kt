package org.demos.dvk

import java.util.ArrayDeque

/** Small bounded 80-column terminal model. All calls are on the emulator reader thread. */
internal class TerminalBuffer {
    private val lines = ArrayDeque<String>()
    private val row = CharArray(80) { ' ' }
    private var column = 0
    private var esc = 0
    private val csi = StringBuilder()

    private fun lineText(): String = String(row).trimEnd()

    private fun newline() {
        lines.addLast(lineText())
        if (lines.size > 250) lines.removeFirst()
        row.fill(' ')
        column = 0
    }

    fun accept(chunk: String) {
        for (ch in chunk) {
            if (esc == 1) {
                if (ch == '[') {
                    csi.setLength(0)
                    esc = 2
                } else {
                    if (ch == 'c') { lines.clear(); row.fill(' '); column = 0 }
                    esc = 0
                }
                continue
            }
            if (esc == 2) {
                if (ch.isDigit() || ch == ';' || ch == '?') {
                    if (csi.length < 40) csi.append(ch)
                    continue
                }
                if (ch == 'J' && (csi.toString().contains("2") || csi.isEmpty())) {
                    lines.clear()
                    row.fill(' ')
                    column = 0
                } else if (ch == 'K') {
                    for (i in column until 80) row[i] = ' '
                } else if (ch == 'G') {
                    column = ((csi.toString().toIntOrNull() ?: 1) - 1).coerceIn(0, 79)
                }
                esc = 0
                continue
            }
            when (ch) {
                '\u001b' -> esc = 1
                '\r' -> column = 0
                '\n' -> newline()
                '\b', '\u007f' -> { if (column > 0) column-- }
                '\t' -> repeat(8 - column % 8) { write(' ') }
                else -> if (ch >= ' ') write(ch)
            }
        }
    }

    private fun write(ch: Char) {
        if (column >= 80) newline()
        row[column++] = ch
    }

    fun snapshot(): String = buildString {
        for (line in lines) append(line).append('\n')
        append(lineText())
    }

    fun contains(token: String): Boolean =
        lineText().contains(token, ignoreCase = true) ||
        lines.any { it.contains(token, ignoreCase = true) }
}
