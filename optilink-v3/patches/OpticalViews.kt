package de.oai.optilink.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.google.zxing.common.BitMatrix
import kotlin.math.min

internal class QrFrameView(context: Context) : View(context) {
    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    @Volatile private var matrix: BitMatrix? = null

    init {
        setBackgroundColor(Color.BLACK)
        keepScreenOn = true
    }

    fun show(matrix: BitMatrix) {
        this.matrix = matrix
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val code = matrix ?: return
        val scale = min(width / code.width, height / code.height).coerceAtLeast(1)
        val drawWidth = code.width * scale
        val drawHeight = code.height * scale
        val left = (width - drawWidth) / 2
        val top = (height - drawHeight) / 2
        canvas.drawRect(left.toFloat(), top.toFloat(), (left + drawWidth).toFloat(), (top + drawHeight).toFloat(), white)
        for (y in 0 until code.height) {
            var x = 0
            while (x < code.width) {
                if (!code[x, y]) { x++; continue }
                val start = x
                while (x < code.width && code[x, y]) x++
                canvas.drawRect(
                    (left + start * scale).toFloat(),
                    (top + y * scale).toFloat(),
                    (left + x * scale).toFloat(),
                    (top + (y + 1) * scale).toFloat(),
                    black,
                )
            }
        }
    }
}

internal class ScanGuideView(context: Context) : View(context) {
    private val shade = Paint().apply { color = 0x55000000; style = Paint.Style.FILL }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = context.dp(3).toFloat()
        strokeCap = Paint.Cap.SQUARE
        style = Paint.Style.STROKE
    }
    private val active = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xff46d99b.toInt()
        strokeWidth = context.dp(3).toFloat()
        strokeCap = Paint.Cap.SQUARE
        style = Paint.Style.STROKE
    }
    @Volatile private var lastSeenNanos = 0L

    fun signalSeen() {
        lastSeenNanos = System.nanoTime()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = (min(width, height) * 0.78f).coerceAtLeast(context.dp(180).toFloat())
        val rect = RectF((width - side) / 2f, (height - side) / 2f, (width + side) / 2f, (height + side) / 2f)
        canvas.drawRect(0f, 0f, width.toFloat(), rect.top, shade)
        canvas.drawRect(0f, rect.bottom, width.toFloat(), height.toFloat(), shade)
        canvas.drawRect(0f, rect.top, rect.left, rect.bottom, shade)
        canvas.drawRect(rect.right, rect.top, width.toFloat(), rect.bottom, shade)

        val paint = if (System.nanoTime() - lastSeenNanos < 700_000_000L) active else frame
        val arm = side * 0.14f
        canvas.drawLine(rect.left, rect.top, rect.left + arm, rect.top, paint)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + arm, paint)
        canvas.drawLine(rect.right, rect.top, rect.right - arm, rect.top, paint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + arm, paint)
        canvas.drawLine(rect.left, rect.bottom, rect.left + arm, rect.bottom, paint)
        canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - arm, paint)
        canvas.drawLine(rect.right, rect.bottom, rect.right - arm, rect.bottom, paint)
        canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - arm, paint)

        if (paint === active) postInvalidateDelayed(720)
    }
}
