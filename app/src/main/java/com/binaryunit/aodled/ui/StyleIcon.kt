package com.binaryunit.aodled.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.binaryunit.aodled.config.Style

/** Small outline of a style's shape for the settings menu. Cutout traces the phone's own hole, if it has one. */
class StyleIcon(private val style: Style, private val cutout: Path?, colour: Int, private val size: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour
        strokeWidth = size / 12f
    }
    private val rect = RectF()
    private val shape = Path()
    private val matrix = Matrix()

    override fun draw(canvas: Canvas) {
        val inset = paint.strokeWidth / 2
        rect.set(bounds)
        rect.inset(inset, inset)
        val r = minOf(rect.width(), rect.height()) / 2
        paint.style = Paint.Style.STROKE
        when (style) {
            Style.RING -> canvas.drawCircle(rect.centerX(), rect.centerY(), r * 0.8f, paint)
            Style.DOT -> {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(rect.centerX(), rect.centerY(), r * 0.35f, paint)
            }
            Style.EDGES -> {
                val half = rect.height() * 0.3f // a portrait screen
                rect.set(rect.centerX() - half, rect.top, rect.centerX() + half, rect.bottom)
                canvas.drawRoundRect(rect, half * 0.4f, half * 0.4f, paint)
            }
            Style.CUTOUT -> {
                if (cutout == null) return
                val src = RectF().also { cutout.computeBounds(it, true) }
                matrix.setRectToRect(src, rect, Matrix.ScaleToFit.CENTER)
                cutout.transform(matrix, shape)
                canvas.drawPath(shape, paint)
            }
        }
    }

    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
