package com.binaryunit.aodled.spike

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent

// Throwaway: does a once-drawn overlay survive doze on battery? Optional keep-alive redraw.
class SpikeService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var dm: DisplayManager
    private lateinit var cpuLock: PowerManager.WakeLock
    private lateinit var drawLock: PowerManager.WakeLock
    private lateinit var pm: PowerManager
    private var view: View? = null
    private var lastState = -1

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            val state = dm.getDisplay(displayId)?.state ?: return
            if (state == lastState) return
            lastState = state
            evaluate("display")
        }
    }

    // Broadcasts can arrive late in doze; never act on them blindly, re-check interactivity instead
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = evaluate(intent.action ?: "?")
    }

    private val hide = Runnable {
        handler.removeCallbacks(keepAlive)
        Log.d(TAG, "hide shown=${view != null}")
        view?.let { wm.removeView(it) }
        view = null
    }

    private val keepAlive = object : Runnable {
        override fun run() {
            val v = view ?: return
            redraw(v)
            handler.postDelayed(this, keepAliveMs())
        }
    }

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        dm = getSystemService(DisplayManager::class.java)
        pm = getSystemService(PowerManager::class.java)
        cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aodled.spike:cpu")
        drawLock = pm.newWakeLock(0x00000080 or 0x40000000, "aodled.spike:draw") // DRAW_WAKE_LOCK | UNIMPORTANT_FOR_LOGGING
        dm.registerDisplayListener(displayListener, handler)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter, RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        dm.unregisterDisplayListener(displayListener)
        unregisterReceiver(screenReceiver)
        hide.run()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun evaluate(source: String) {
        val interactive = pm.isInteractive
        Log.d(TAG, "$source display=${dm.getDisplay(Display.DEFAULT_DISPLAY)?.state} interactive=$interactive shown=${view != null}")
        if (interactive) hideSoon() else show()
    }

    private fun show() {
        handler.removeCallbacks(hide)
        val v = view ?: addOverlay()
        redraw(v)
    }

    private fun addOverlay(): View {
        val metrics = wm.maximumWindowMetrics
        val cutout = metrics.windowInsets.displayCutout?.boundingRects?.firstOrNull()
        val centre = cutout?.let { PointF(it.exactCenterX(), it.exactCenterY()) } ?: PointF(540f, 60f)
        val v = RingView(this, centre)
        val lp = WindowManager.LayoutParams(
            metrics.bounds.width(),
            metrics.bounds.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
            title = "AODLed spike"
        }
        wm.addView(v, lp)
        view = v
        if (keepAliveMs() > 0) handler.postDelayed(keepAlive, keepAliveMs())
        return v
    }

    private fun hideSoon() {
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, 100)
    }

    private fun redraw(v: View) {
        cpuLock.acquire(250)
        drawLock.acquire(250)
        v.invalidate()
    }

    private fun keepAliveMs() = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_KEEP_ALIVE, 0)

    private class RingView(context: Context, private val centre: PointF) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.WHITE
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            canvas.drawCircle(centre.x, centre.y, 60f, paint)
        }
    }

    companion object {
        const val PREFS = "spike"
        const val KEY_KEEP_ALIVE = "keepAliveMs"
        private const val TAG = "AODLedSpike"
    }
}
