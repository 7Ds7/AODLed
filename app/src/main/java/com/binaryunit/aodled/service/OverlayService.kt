package com.binaryunit.aodled.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
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
import com.binaryunit.aodled.config.defaultCutout
import com.binaryunit.aodled.config.defaultDot
import com.binaryunit.aodled.config.defaultRing
import com.binaryunit.aodled.config.placementDiffers
import com.binaryunit.aodled.render.FRAME_MS
import com.binaryunit.aodled.render.LightState
import com.binaryunit.aodled.render.LightView
import com.binaryunit.aodled.render.box
import com.binaryunit.aodled.render.contourHalfSize
import com.binaryunit.aodled.render.cornerRadius
import com.binaryunit.aodled.render.cutoutPath
import com.binaryunit.aodled.render.effectiveAnimation
import com.binaryunit.aodled.render.lineThickness
import com.binaryunit.aodled.render.pulseFor
import com.binaryunit.aodled.render.pulseFrameDelay
import com.binaryunit.aodled.render.screenGeometry
import kotlin.random.Random

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
    // Pulse bursts count from here (uptime): the cover going up, the display coming back from OFF, a new alert
    private var pulseStart = 0L
    private var lastAlert: Long? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            val state = dm.getDisplay(displayId)?.state ?: return
            if (state == lastDisplayState) return // ignore brightness / refresh-rate changes
            if (lastDisplayState == Display.STATE_OFF) pulseStart = SystemClock.uptimeMillis()
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
        val old = cfg
        cfg = prefs.load()
        if (cfg.placementDiffers(old)) controller.placementChanged()
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
            pulseStart = SystemClock.uptimeMillis()
            view = LightView(this@OverlayService).also { wm.addView(it, layoutParams()) }
        }

        override fun coverDown() {
            wake.releaseCpu() // a pulse pause may be holding it
            view?.let { wm.removeView(it) }
            view = null
        }

        override fun draw(offset: Offset): Light = this@OverlayService.draw(offset)

        override fun frame(): Long {
            wake.poke(100)
            view?.invalidate()
            if (effectiveAnimation(cfg.style, cfg.animation) != Animation.PULSE) return FRAME_MS
            // In a pause nothing is drawn, but the CPU is held awake for it: unplugged, the Pixel 10a sleeps and this
            // uptime timer with it, so the next burst never came (2026-11-04). The saving is the drawing, not the CPU.
            // To let the CPU sleep too, wake for the next burst with an exact alarm instead (USE_EXACT_ALARM is granted
            // at install, but doze may limit how often alarms fire, which short pauses would hit).
            val delay = pulseFrameDelay(SystemClock.uptimeMillis() - pulseStart, pulseFor(cfg))
            if (delay > FRAME_MS) wake.holdCpu(delay + 1000)
            return delay
        }
    }

    override fun onServiceConnected() {
        wm = getSystemService(WindowManager::class.java)
        dm = getSystemService(DisplayManager::class.java)
        pm = getSystemService(PowerManager::class.java)
        prefs = Prefs(this)
        wake = DozeWake(this)
        controller = OverlayController(host, scheduler) {
            BurnIn.next(Random, BurnIn.shift(state(Offset(0, 0), null).lineThickness()))
        }
        cfg = prefs.load()
        dm.registerDisplayListener(displayListener, handler)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // Exported: USER_PRESENT comes from SystemUI, an app uid, so a not-exported receiver never gets it.
        // Safe, all three actions are protected and only the system can send them. Android 12 has no flag.
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_EXPORTED)
        else registerReceiver(screenReceiver, filter)
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
        // Display fully off (no AOD): stop the frame and shift loops; the display listener redraws on DOZE / ON
        if (lastDisplayState == Display.STATE_OFF) {
            wake.releaseCpu() // a pulse pause may be holding it
            return Light.OFF
        }
        val colour = AppState.tracker.current(cfg.lightOff)?.let { displayColour(it.colour, cfg) }
        val alert = AppState.tracker.latestAlert(cfg.lightOff)
        if (alert != lastAlert) {
            lastAlert = alert
            pulseStart = SystemClock.uptimeMillis()
        }
        val state = state(offset, colour)
        wake.poke()
        v.state = state
        return when {
            colour == null -> Light.OFF
            effectiveAnimation(cfg.style, cfg.animation) != Animation.OFF -> Light.ANIMATED
            else -> Light.STILL
        }
    }

    /** The saved config on this screen, with placements left at default derived from the cutout. */
    private fun state(offset: Offset, colour: Int?): LightState {
        val (width, _, cutout) = screenGeometry()
        val path = cutoutPath()
        return LightState(
            cfg = cfg,
            ring = cfg.ring ?: defaultRing(cutout, width),
            dot = cfg.dot ?: defaultDot(cutout, width),
            colour = colour,
            offset = offset,
            cornerRadius = cornerRadius(),
            aod = true,
            cutout = cfg.cutout ?: path?.let { defaultCutout(it.box(), it.contourHalfSize()) },
            cutoutPath = path,
            pulse = pulseFor(cfg),
            pulseStart = pulseStart,
        )
    }
}
