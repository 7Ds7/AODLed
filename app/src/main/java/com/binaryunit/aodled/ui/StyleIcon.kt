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
    private val shapeRect = RectF()
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
            Style.RIPPLE -> {
                // Three rings, fainter as they grow, like one frame of a burst
                val alpha = paint.alpha
                for ((f, a) in listOf(0.25f to 1f, 0.55f to 0.6f, 0.85f to 0.3f)) {
                    paint.alpha = (alpha * a).toInt()
                    canvas.drawCircle(rect.centerX(), rect.centerY(), r * f, paint)
                }
                paint.alpha = alpha
            }
            Style.RADAR -> {
                // ((·)): a dot with three arcs each side, fainter as they grow
                val alpha = paint.alpha
                paint.style = Paint.Style.FILL
                canvas.drawCircle(rect.centerX(), rect.centerY(), r * 0.12f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                for ((f, a) in listOf(0.35f to 1f, 0.6f to 0.6f, 0.85f to 0.3f)) {
                    paint.alpha = (alpha * a).toInt()
                    shapeRect.set(rect.centerX() - r * f, rect.centerY() - r * f, rect.centerX() + r * f, rect.centerY() + r * f)
                    for (middle in listOf(0f, 180f)) canvas.drawArc(shapeRect, middle - 30f, 60f, false, paint)
                }
                paint.strokeCap = Paint.Cap.BUTT
                paint.alpha = alpha
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
