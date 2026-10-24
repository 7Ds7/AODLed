package com.binaryunit.aodled.ui

import android.annotation.SuppressLint
import android.graphics.Path
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.binaryunit.aodled.R
import com.binaryunit.aodled.colour.Palette
import com.binaryunit.aodled.config.Animation
import com.binaryunit.aodled.config.Box
import com.binaryunit.aodled.config.ColourMode
import com.binaryunit.aodled.config.LightConfig
import com.binaryunit.aodled.config.Offset
import com.binaryunit.aodled.config.Prefs
import com.binaryunit.aodled.config.Style
import com.binaryunit.aodled.config.defaultCutout
import com.binaryunit.aodled.config.defaultDot
import com.binaryunit.aodled.config.defaultRing
import com.binaryunit.aodled.config.moved
import com.binaryunit.aodled.config.pinched
import com.binaryunit.aodled.config.pinchedEdge
import com.binaryunit.aodled.config.resetPlacement
import com.binaryunit.aodled.render.LightState
import com.binaryunit.aodled.render.LightView
import com.binaryunit.aodled.render.box
import com.binaryunit.aodled.render.contourHalfSize
import com.binaryunit.aodled.render.cornerRadius
import com.binaryunit.aodled.render.cutoutPath
import com.binaryunit.aodled.render.effectiveAnimation
import com.binaryunit.aodled.render.screenGeometry
import com.google.android.material.button.MaterialButtonToggleGroup

/** Full-screen black placement editor. Done saves, Back discards. */
class EditorActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var light: LightView
    private lateinit var hint: TextView
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

    private val styleButtons = mapOf(
        Style.RING to R.id.style_ring,
        Style.DOT to R.id.style_dot,
        Style.EDGES to R.id.style_edges,
        Style.CUTOUT to R.id.style_cutout,
    )

    private val tick = object : Runnable {
        override fun run() {
            if (!animating) return
            light.invalidate()
            light.postOnAnimation(this)
        }
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

        val group = findViewById<MaterialButtonToggleGroup>(R.id.styles)
        findViewById<android.view.View>(R.id.style_cutout).isEnabled = cutoutPath != null
        group.check(styleButtons.getValue(cfg.style))
        group.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            cfg = cfg.copy(style = styleButtons.entries.first { it.value == id }.key)
            render()
        }
        findViewById<android.view.View>(R.id.reset).setOnClickListener {
            cfg = withDefaults(cfg.resetPlacement())
            render()
        }
        findViewById<android.view.View>(R.id.done).setOnClickListener {
            prefs.save(cfg)
            finish()
        }

        val scale = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val f = detector.scaleFactor
                cfg = when (cfg.style) {
                    Style.RING -> cfg.copy(ring = cfg.ring!!.pinched(f))
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
        cfg = when (cfg.style) {
            Style.RING -> cfg.copy(ring = cfg.ring!!.moved(dx, dy, width, height))
            Style.DOT -> cfg.copy(dot = cfg.dot!!.moved(dx, dy, width, height))
            Style.EDGES -> cfg
            Style.CUTOUT -> cfg.cutout?.let { cfg.copy(cutout = it.moved(dx, dy, width, height)) } ?: cfg
        }
        render()
    }

    private fun render() {
        hint.setText(if (cfg.style == Style.EDGES) R.string.editor_hint_edges else R.string.editor_hint)
        val colour = if (cfg.colourMode == ColourMode.FIXED) cfg.fixedColour else Palette.WHITE
        light.state = LightState(
            cfg, cfg.ring!!, cfg.dot!!, colour, Offset(0, 0), cornerRadius, cutout = cfg.cutout, cutoutPath = cutoutPath,
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
