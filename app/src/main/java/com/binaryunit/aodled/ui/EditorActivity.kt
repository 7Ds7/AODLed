package com.binaryunit.aodled.ui

import android.annotation.SuppressLint
import android.graphics.Path
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.Gravity
import android.view.ScaleGestureDetector
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.binaryunit.aodled.R
import com.binaryunit.aodled.colour.Palette
import com.binaryunit.aodled.config.Animation
import com.binaryunit.aodled.config.Box
import com.binaryunit.aodled.config.ColourMode
import com.binaryunit.aodled.config.LINE_SCALE_MAX
import com.binaryunit.aodled.config.LINE_SCALE_MIN
import com.binaryunit.aodled.config.LightConfig
import com.binaryunit.aodled.config.Offset
import com.binaryunit.aodled.config.Prefs
import com.binaryunit.aodled.config.Style
import com.binaryunit.aodled.config.defaultCutout
import com.binaryunit.aodled.config.defaultDot
import com.binaryunit.aodled.config.circle
import com.binaryunit.aodled.config.circleLineScale
import com.binaryunit.aodled.config.defaultBurst
import com.binaryunit.aodled.config.defaultRing
import com.binaryunit.aodled.config.withCircle
import com.binaryunit.aodled.config.moved
import com.binaryunit.aodled.config.pinched
import com.binaryunit.aodled.config.pinchedEdge
import com.binaryunit.aodled.config.resetPlacement
import com.binaryunit.aodled.config.resized
import com.binaryunit.aodled.config.resizedEdge
import com.binaryunit.aodled.render.LightState
import com.binaryunit.aodled.render.LightView
import com.binaryunit.aodled.render.box
import com.binaryunit.aodled.render.contourHalfSize
import com.binaryunit.aodled.render.cornerRadius
import com.binaryunit.aodled.render.cutoutPath
import com.binaryunit.aodled.render.effectiveAnimation
import com.binaryunit.aodled.render.screenGeometry
import com.google.android.material.slider.Slider

/** Full-screen black placement editor. Done saves, Cancel and Back discard. */
class EditorActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var light: LightView
    private lateinit var hint: TextView
    private lateinit var thicknessRow: View
    private lateinit var thickness: Slider
    private lateinit var arrows: List<View>
    private lateinit var cfg: LightConfig
    private var width = 0
    private var height = 0
    private var cornerRadius = 0f
    private var cutout: Box? = null
    private var cutoutPath: Path? = null
    private var cutoutBox: Box? = null
    private var cutoutHalf = 0f
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var animating = false

    private val tick = object : Runnable {
        override fun run() {
            if (!animating) return
            light.invalidate()
            light.postOnAnimation(this)
        }
    }

    private companion object {
        const val REPEAT_START_MS = 100L
        const val REPEAT_SPEEDUP_MS = 8L
        const val REPEAT_MIN_MS = 20L
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)
        // After setContentView: the insets controller needs the decor view to exist
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        light = findViewById(R.id.light)
        hint = findViewById(R.id.hint)
        thicknessRow = findViewById(R.id.thickness_row)
        thickness = findViewById(R.id.thickness)

        prefs = Prefs(this)
        val (w, h, c) = screenGeometry()
        width = w
        height = h
        cutout = c
        cutoutPath = cutoutPath()
        cutoutBox = cutoutPath?.box()
        cutoutHalf = cutoutPath?.contourHalfSize() ?: 0f
        cornerRadius = cornerRadius()
        cfg = withDefaults(prefs.load())

        // A dropdown, not a row of buttons: six style names don't fit across a phone at larger text sizes
        val styles = StyleMenu(findViewById(R.id.style_field), findViewById(R.id.style_menu), cutoutPath) {
            cfg = cfg.copy(style = it)
            render()
        }
        styles.show(cfg.style)
        thickness.valueFrom = LINE_SCALE_MIN
        thickness.valueTo = LINE_SCALE_MAX
        thickness.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            cfg = when (cfg.style) {
                Style.RING, Style.RIPPLE, Style.RADAR -> cfg.withCircle(cfg.circle(), value)
                Style.CUTOUT -> cfg.copy(cutoutLineScale = value)
                Style.DOT, Style.EDGES -> cfg
            }
            render()
        }
        findViewById<View>(R.id.help_burn_in).setOnClickListener {
            showHelp(it, R.string.help_burn_in, above = !prefs.editorPanelTop)
        }
        findViewById<View>(R.id.flip).setOnClickListener {
            prefs.editorPanelTop = !prefs.editorPanelTop
            placePanel()
        }
        placePanel()
        repeating(R.id.nudge_left) { move(-1f, 0f) }
        repeating(R.id.nudge_right) { move(1f, 0f) }
        repeating(R.id.nudge_up) { move(0f, -1f) }
        repeating(R.id.nudge_down) { move(0f, 1f) }
        repeating(R.id.nudge_smaller) { resize(-1f) }
        repeating(R.id.nudge_bigger) { resize(1f) }
        arrows = listOf(R.id.nudge_left, R.id.nudge_right, R.id.nudge_up, R.id.nudge_down).map { findViewById(it) }
        findViewById<android.view.View>(R.id.reset).setOnClickListener {
            cfg = withDefaults(cfg.resetPlacement())
            render()
        }
        findViewById<View>(R.id.cancel).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.done).setOnClickListener {
            prefs.save(cfg)
            finish()
        }

        val scale = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val f = detector.scaleFactor
                cfg = when (cfg.style) {
                    Style.RING, Style.RIPPLE, Style.RADAR -> cfg.withCircle(cfg.circle()!!.pinched(f))
                    Style.DOT -> cfg.copy(dot = cfg.dot!!.pinched(f))
                    Style.EDGES -> cfg.copy(edgeThickness = pinchedEdge(cfg.edgeThickness, f))
                    Style.CUTOUT -> cfg.cutout?.let { cfg.copy(cutout = it.pinched(f, cutoutHalf)) } ?: cfg
                }
                render()
                return true
            }
        })
        light.setOnTouchListener { _, e ->
            scale.onTouchEvent(e)
            drag(e)
            true
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onPause() {
        animating = false
        super.onPause()
    }

    private fun withDefaults(c: LightConfig) =
        c.copy(
            ring = c.ring ?: defaultRing(cutout, width),
            ripple = c.ripple ?: defaultBurst(cutoutBox, width),
            radar = c.radar ?: defaultBurst(cutoutBox, width),
            dot = c.dot ?: defaultDot(cutout, width),
            cutout = c.cutout ?: cutoutBox?.let { defaultCutout(it, cutoutHalf) },
        )

    private fun drag(e: MotionEvent) {
        // Only single-finger moves drag; reset on any pointer change so a pinch never makes the light jump
        if (e.actionMasked != MotionEvent.ACTION_MOVE || e.pointerCount != 1) {
            lastX = if (e.actionMasked == MotionEvent.ACTION_DOWN) e.x else Float.NaN
            lastY = if (e.actionMasked == MotionEvent.ACTION_DOWN) e.y else Float.NaN
            return
        }
        if (lastX.isNaN()) {
            lastX = e.x; lastY = e.y
            return
        }
        val dx = e.x - lastX
        val dy = e.y - lastY
        lastX = e.x; lastY = e.y
        move(dx, dy)
    }

    private fun move(dx: Float, dy: Float) {
        cfg = when (cfg.style) {
            Style.RING, Style.RIPPLE, Style.RADAR -> cfg.withCircle(cfg.circle()!!.moved(dx, dy, width, height))
            Style.DOT -> cfg.copy(dot = cfg.dot!!.moved(dx, dy, width, height))
            Style.EDGES -> cfg
            Style.CUTOUT -> cfg.cutout?.let { cfg.copy(cutout = it.moved(dx, dy, width, height)) } ?: cfg
        }
        render()
    }

    private fun resize(d: Float) {
        cfg = when (cfg.style) {
            Style.RING, Style.RIPPLE, Style.RADAR -> cfg.withCircle(cfg.circle()!!.resized(d))
            Style.DOT -> cfg.copy(dot = cfg.dot!!.resized(d))
            Style.EDGES -> cfg.copy(edgeThickness = resizedEdge(cfg.edgeThickness, d))
            Style.CUTOUT -> cfg.cutout?.let { cfg.copy(cutout = it.resized(d, cutoutHalf)) } ?: cfg
        }
        render()
    }

    /** A tap steps once; holding repeats after the long-press delay, speeding up the longer it's held. */
    @SuppressLint("ClickableViewAccessibility")
    private fun repeating(id: Int, step: () -> Unit) {
        val button = findViewById<View>(id)
        val repeat = object : Runnable {
            var count = 0
            override fun run() {
                step()
                count++
                button.postDelayed(this, maxOf(REPEAT_MIN_MS, REPEAT_START_MS - REPEAT_SPEEDUP_MS * count))
            }
        }
        button.setOnClickListener { step() }
        button.setOnLongClickListener {
            repeat.count = 0
            repeat.run()
            true
        }
        button.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                button.removeCallbacks(repeat)
            }
            false
        }
    }

    private fun placePanel() {
        val top = prefs.editorPanelTop
        val bar = findViewById<View>(R.id.bar)
        val margin = (32 * resources.displayMetrics.density).toInt()
        bar.layoutParams = (bar.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = if (top) Gravity.TOP else Gravity.BOTTOM
            topMargin = if (top) margin else 0
            bottomMargin = if (top) 0 else margin
        }
        // The icon shows where the panel will go
        findViewById<ImageButton>(R.id.flip).apply {
            setImageResource(if (top) R.drawable.ic_panel_bottom else R.drawable.ic_panel_top)
            contentDescription = getString(if (top) R.string.panel_to_bottom else R.string.panel_to_top)
        }
    }

    private fun render() {
        hint.setText(if (cfg.style == Style.EDGES) R.string.editor_hint_edges else R.string.editor_hint)
        // Dot is filled and Edges pinch their own thickness, so the slider is only for the outlines
        val lineScale = when (cfg.style) {
            Style.RING, Style.RIPPLE, Style.RADAR -> cfg.circleLineScale()
            Style.CUTOUT -> cfg.cutoutLineScale
            Style.DOT, Style.EDGES -> null
        }
        thicknessRow.visibility = if (lineScale != null) View.VISIBLE else View.GONE
        // Edges hug the screen edge, so there's nothing to move; − + still change their thickness
        arrows.forEach { it.isEnabled = cfg.style != Style.EDGES }
        if (lineScale != null) thickness.value = lineScale
        val colour = if (cfg.colourMode == ColourMode.FIXED) cfg.fixedColour else Palette.WHITE
        light.state = LightState(
            cfg, cfg.circle()!!, cfg.dot!!, colour, Offset(0, 0), cornerRadius, cutout = cfg.cutout, cutoutPath = cutoutPath,
        )
        val wantAnim = effectiveAnimation(cfg.style, cfg.animation) != Animation.OFF
        if (wantAnim && !animating) {
            animating = true
            light.postOnAnimation(tick)
        } else if (!wantAnim) {
            animating = false
        }
    }
}
