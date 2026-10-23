package com.binaryunit.aodled.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Display
import android.view.RoundedCorner
import android.view.View
import android.view.WindowManager
import com.binaryunit.aodled.config.Box

class LightView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val renderer = LightRenderer()

    var state: LightState? = null
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val s = state
        if (s == null) canvas.drawColor(Color.BLACK) else renderer.draw(canvas, s, SystemClock.uptimeMillis())
    }
}

/** Full screen size in physical pixels and the camera cutout, if any. */
fun Context.screenGeometry(): Triple<Int, Int, Box?> {
    val metrics = getSystemService(WindowManager::class.java).maximumWindowMetrics
    val cutout = metrics.windowInsets.displayCutout?.boundingRects?.firstOrNull()
    return Triple(
        metrics.bounds.width(),
        metrics.bounds.height(),
        cutout?.let { Box(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) },
    )
}

fun Context.cornerRadius(): Float {
    // Via DisplayManager: Context.getDisplay() throws for non-visual contexts such as services
    val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    return (display?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0).toFloat()
}
