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
import com.binaryunit.aodled.config.LightConfig
import com.binaryunit.aodled.config.Offset
import com.binaryunit.aodled.config.Prefs
import com.binaryunit.aodled.config.defaultCutout
import com.binaryunit.aodled.config.defaultDot
import com.binaryunit.aodled.config.defaultRing
import com.binaryunit.aodled.render.LightState
import com.binaryunit.aodled.render.LightView
import com.binaryunit.aodled.render.box
import com.binaryunit.aodled.render.contourHalfSize
import com.binaryunit.aodled.render.cornerRadius
import com.binaryunit.aodled.render.cutoutPath
import com.binaryunit.aodled.render.effectiveAnimation
import com.binaryunit.aodled.render.screenGeometry

/**
 * Owns the full-screen overlay that covers the AOD with black and draws the light; [OverlayController] decides
 * when. Screen broadcasts can arrive late in doze, so they only trigger a re-check.
 */
class OverlayService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var dm: DisplayManager
    private lateinit var pm: PowerManager
    private lateinit var prefs: Prefs
    private lateinit var wake: DozeWake
    private lateinit var controller: OverlayController
    private var view: LightView? = null
    private var cfg = LightConfig()
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

    private val onStateChanged: () -> Unit = { controller.refresh() }
    private val onPrefsChanged: () -> Unit = {
        cfg = prefs.load()
        controller.refresh()
    }

    private val scheduler = object : Scheduler {
        override fun post(task: Runnable, delayMs: Long) {
            handler.postDelayed(task, delayMs)
        }

        override fun cancel(task: Runnable) {
            handler.removeCallbacks(task)
        }
    }

    private val host = object : OverlayController.Host {
        override fun coverUp() {
            AppState.rebuild?.invoke()
            view = LightView(this@OverlayService).also { wm.addView(it, layoutParams()) }
        }

        override fun coverDown() {
            view?.let { wm.removeView(it) }
            view = null
        }

        override fun draw(offset: Offset): Light = this@OverlayService.draw(offset)

        override fun frame() {
            wake.poke(100)
            view?.invalidate()
        }
    }

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        dm = getSystemService(DisplayManager::class.java)
        pm = getSystemService(PowerManager::class.java)
        prefs = Prefs(this)
        wake = DozeWake(this)
        controller = OverlayController(host, scheduler)
        cfg = prefs.load()
        dm.registerDisplayListener(displayListener, handler)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED) // system broadcasts still arrive
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
            controller.stop()
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun evaluate() = controller.screen(pm.isInteractive)

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

    /** Recomputes what to draw at [offset] and hands it to the view. */
    private fun draw(offset: Offset): Light {
        val v = view ?: return Light.OFF
        val (width, _, cutout) = screenGeometry()
        val path = cutoutPath()
        val colour = AppState.tracker.current(cfg.lightOff)?.let { displayColour(it.colour, cfg) }
        val state = LightState(
            cfg = cfg,
            ring = cfg.ring ?: defaultRing(cutout, width),
            dot = cfg.dot ?: defaultDot(cutout, width),
            colour = colour,
            offset = offset,
            cornerRadius = cornerRadius(),
            aod = true,
            cutout = cfg.cutout ?: path?.let { defaultCutout(it.box(), it.contourHalfSize()) },
            cutoutPath = path,
        )
        wake.poke()
        v.state = state
        return when {
            colour == null -> Light.OFF
            effectiveAnimation(cfg.style, cfg.animation) != Animation.OFF -> Light.ANIMATED
            else -> Light.STILL
        }
    }
}
