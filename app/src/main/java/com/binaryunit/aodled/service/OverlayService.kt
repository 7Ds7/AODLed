package com.binaryunit.aodled.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.binaryunit.aodled.colour.displayColour
import com.binaryunit.aodled.config.Animation
import com.binaryunit.aodled.config.BurnIn
import com.binaryunit.aodled.config.LightConfig
import com.binaryunit.aodled.config.Offset
import com.binaryunit.aodled.config.Prefs
import com.binaryunit.aodled.config.defaultDot
import com.binaryunit.aodled.config.defaultRing
import com.binaryunit.aodled.render.ANIM_FPS
import com.binaryunit.aodled.render.LightState
import com.binaryunit.aodled.render.LightView
import com.binaryunit.aodled.render.cornerRadius
import com.binaryunit.aodled.render.effectiveAnimation
import com.binaryunit.aodled.render.screenGeometry
import kotlin.random.Random

/** Doze spike (2026-10-23): a once-drawn frame held for 10 min unplugged, so no keep-alive redraw. */
const val KEEP_ALIVE_MS = 0L
const val LINGER_MS = 100L

/**
 * Owns the full-screen overlay that covers the AOD with black and draws the light. The cover is up exactly while
 * the device is not interactive. Screen broadcasts can arrive late in doze, so they only trigger a re-check.
 */
class OverlayService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var dm: DisplayManager
    private lateinit var pm: PowerManager
    private lateinit var prefs: Prefs
    private lateinit var wake: DozeWake
    private var view: LightView? = null
    private var cfg = LightConfig()
    private var offset = Offset(0, 0)
    private var lastDisplayState = -1

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            val state = dm.getDisplay(displayId)?.state ?: return
            if (state == lastDisplayState) return // ignore brightness / refresh-rate changes
            lastDisplayState = state
            evaluate()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                AppState.tracker.markSeen(System.currentTimeMillis())
                AppState.changed()
            } else {
                evaluate()
            }
        }
    }

    private val onStateChanged: () -> Unit = { refresh() }
    private val onPrefsChanged: () -> Unit = {
        cfg = prefs.load()
        refresh()
    }

    private val hide = Runnable {
        stopLoops()
        view?.let { wm.removeView(it) }
        view = null
    }

    private val keepAlive = object : Runnable {
        override fun run() {
            redraw()
            handler.postDelayed(this, KEEP_ALIVE_MS)
        }
    }

    private val burnIn = object : Runnable {
        override fun run() {
            offset = BurnIn.next(Random)
            refresh()
        }
    }

    private val frame = object : Runnable {
        override fun run() {
            wake.poke(100)
            view?.invalidate()
            handler.postDelayed(this, 1000L / ANIM_FPS)
        }
    }

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        dm = getSystemService(DisplayManager::class.java)
        pm = getSystemService(PowerManager::class.java)
        prefs = Prefs(this)
        wake = DozeWake(this)
        cfg = prefs.load()
        dm.registerDisplayListener(displayListener, handler)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter, RECEIVER_EXPORTED)
        AppState.addListener(onStateChanged)
        prefs.onChange(onPrefsChanged)
        evaluate()
    }

    override fun onDestroy() {
        if (::prefs.isInitialized) {
            dm.unregisterDisplayListener(displayListener)
            unregisterReceiver(screenReceiver)
            AppState.removeListener(onStateChanged)
            prefs.removeOnChange(onPrefsChanged)
            handler.removeCallbacks(hide)
            hide.run()
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun evaluate() {
        if (pm.isInteractive) {
            handler.removeCallbacks(hide)
            handler.postDelayed(hide, LINGER_MS)
        } else {
            show()
        }
    }

    private fun show() {
        handler.removeCallbacks(hide)
        if (view == null) {
            AppState.rebuild?.invoke()
            offset = BurnIn.next(Random)
            view = LightView(this).also { wm.addView(it, layoutParams()) }
        }
        refresh()
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val bounds = wm.maximumWindowMetrics.bounds
        @Suppress("DEPRECATION") // FLAG_FULLSCREEN: proven in the doze spike; keep the same window setup
        return WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
            title = "AODLed"
        }
    }

    /** Recomputes what to draw and (re)schedules the redraw loops. No-op while the cover is down. */
    private fun refresh() {
        val v = view ?: return
        val (width, _, cutout) = screenGeometry()
        val colour = AppState.tracker.current(cfg.lightOff)?.let { displayColour(it.colour, cfg) }
        val state = LightState(
            cfg = cfg,
            ring = cfg.ring ?: defaultRing(cutout, width),
            dot = cfg.dot ?: defaultDot(cutout, width),
            colour = colour,
            offset = offset,
            cornerRadius = cornerRadius(),
        )
        wake.poke()
        v.state = state
        stopLoops()
        if (KEEP_ALIVE_MS > 0) handler.postDelayed(keepAlive, KEEP_ALIVE_MS)
        if (colour != null) {
            handler.postDelayed(burnIn, BurnIn.INTERVAL_MS)
            if (effectiveAnimation(cfg.style, cfg.animation) != Animation.OFF) handler.post(frame)
        }
    }

    private fun redraw() {
        wake.poke()
        view?.invalidate()
    }

    private fun stopLoops() {
        handler.removeCallbacks(keepAlive)
        handler.removeCallbacks(burnIn)
        handler.removeCallbacks(frame)
    }
}
