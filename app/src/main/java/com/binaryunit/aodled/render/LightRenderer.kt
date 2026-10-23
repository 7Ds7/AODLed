package com.binaryunit.aodled.render

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.SweepGradient
import com.binaryunit.aodled.config.Animation
import com.binaryunit.aodled.config.DotPlacement
import com.binaryunit.aodled.config.LightConfig
import com.binaryunit.aodled.config.Offset
import com.binaryunit.aodled.config.RingPlacement
import com.binaryunit.aodled.config.Style
import com.binaryunit.aodled.config.ringThickness
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything needed to draw one frame. [colour] null means black only (nothing pending). */
data class LightState(
    val cfg: LightConfig,
    val ring: RingPlacement,
    val dot: DotPlacement,
    val colour: Int?,
    val offset: Offset,
    val cornerRadius: Float,
)

private const val GLOW_ALPHA = 0.7f
private const val COMET_TAIL_START = 0.35f // share of the ring that stays dark ahead of the tail
private const val EDGE_COMET_SHARE = 0.2f  // comet length as a share of the screen border
private const val EDGE_COMET_STEPS = 12

/**
 * Shared by the doze overlay and the editor, so what you place is what you get. Every shape is drawn twice:
 * a blurred halo (glow) and a crisp core on top.
 */
class LightRenderer {
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pixel = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private var glowBlur = -1f
    private val rect = RectF()
    private val path = Path()
    private val segment = Path()
    private val measure = PathMeasure()
    private var comet: SweepGradient? = null
    private var cometKey = Triple(0f, 0f, 0)

    fun draw(canvas: Canvas, s: LightState, tMs: Long) {
        canvas.drawColor(Color.BLACK)
        val colour = s.colour ?: return
        val anim = effectiveAnimation(s.cfg.style, s.cfg.animation)
        val alpha = if (anim == Animation.PULSE) pulseAlpha(tMs) else 1f
        when (s.cfg.style) {
            Style.RING -> drawRing(canvas, s, colour, alpha, anim, tMs)
            Style.DOT -> drawDot(canvas, s, withAlpha(colour, alpha))
            Style.EDGES -> drawEdges(canvas, s, colour, alpha, anim, tMs)
        }
    }

    private fun drawRing(canvas: Canvas, s: LightState, colour: Int, alpha: Float, anim: Animation, tMs: Long) {
        val cx = s.ring.cx + s.offset.dx
        val cy = s.ring.cy + s.offset.dy
        val r = s.ring.radius
        val t = ringThickness(r)
        if (anim == Animation.ROTATE) {
            // Comet: bright head with a fading tail, spinning around the camera
            val shader = cometShader(cx, cy, colour)
            canvas.save()
            canvas.rotate(rotationAngle(tMs), cx, cy)
            canvas.drawCircle(cx, cy, r, stroke(glowPaint(t), t, Color.WHITE).apply { this.shader = shader; this.alpha = (255 * GLOW_ALPHA).roundToInt() })
            canvas.drawCircle(cx, cy, r, stroke(core, t, Color.WHITE).apply { this.shader = shader })
            canvas.restore()
            glow.shader = null
            core.shader = null
        } else {
            val c = withAlpha(colour, alpha)
            canvas.drawCircle(cx, cy, r, stroke(glowPaint(t), t, withAlpha(c, GLOW_ALPHA)))
            canvas.drawCircle(cx, cy, r, stroke(core, t, c))
        }
    }

    private fun drawDot(canvas: Canvas, s: LightState, colour: Int) {
        val x = s.dot.x + s.offset.dx
        val y = s.dot.y + s.offset.dy
        val size = s.dot.size
        canvas.drawCircle(x, y, maxOf(size / 2, 1f), fill(glowPaint(size), withAlpha(colour, GLOW_ALPHA)))
        if (size < 2.5f) {
            // 1x1 or 2x2 physical pixels: no anti-aliasing, snapped to the pixel grid
            pixel.color = colour
            val n = size.roundToInt()
            val px = x.roundToInt().toFloat()
            val py = y.roundToInt().toFloat()
            canvas.drawRect(px, py, px + n, py + n, pixel)
        } else {
            canvas.drawCircle(x, y, size / 2, fill(core, colour))
        }
    }

    private fun drawEdges(canvas: Canvas, s: LightState, colour: Int, alpha: Float, anim: Animation, tMs: Long) {
        val t = s.cfg.edgeThickness
        val inset = t / 2 + abs(s.offset.dx)
        val corner = maxOf(0f, s.cornerRadius - inset)
        rect.set(inset, inset, canvas.width - inset, canvas.height - inset)
        if (anim == Animation.ROTATE) {
            // Comet along the border: short segments brightening from tail to head
            path.reset()
            path.addRoundRect(rect, corner, corner, Path.Direction.CW)
            measure.setPath(path, true)
            val length = measure.length
            val head = rotationAngle(tMs) / 360f * length
            val step = length * EDGE_COMET_SHARE / EDGE_COMET_STEPS
            for (i in 0 until EDGE_COMET_STEPS) {
                val a = (i + 1f) / EDGE_COMET_STEPS
                val start = head - step * (EDGE_COMET_STEPS - i)
                segment.reset()
                addSegment(start, start + step, length)
                canvas.drawPath(segment, stroke(glowPaint(t), t, withAlpha(colour, a * GLOW_ALPHA)))
                canvas.drawPath(segment, stroke(core, t, withAlpha(colour, a)).apply { strokeCap = Paint.Cap.ROUND })
            }
            core.strokeCap = Paint.Cap.BUTT
        } else {
            val c = withAlpha(colour, alpha)
            canvas.drawRoundRect(rect, corner, corner, stroke(glowPaint(t), t, withAlpha(c, GLOW_ALPHA)))
            canvas.drawRoundRect(rect, corner, corner, stroke(core, t, c))
        }
    }

    /** Appends [from, to) of the measured closed path to [segment], wrapping around its start. */
    private fun addSegment(from: Float, to: Float, length: Float) {
        val a = ((from % length) + length) % length
        val b = a + (to - from)
        measure.getSegment(a, minOf(b, length), segment, true)
        if (b > length) measure.getSegment(0f, b - length, segment, true)
    }

    private fun cometShader(cx: Float, cy: Float, colour: Int): SweepGradient {
        val key = Triple(cx, cy, colour)
        comet?.takeIf { key == cometKey }?.let { return it }
        val clear = colour and 0x00FFFFFF
        return SweepGradient(cx, cy, intArrayOf(clear, clear, colour), floatArrayOf(0f, COMET_TAIL_START, 1f))
            .also { comet = it; cometKey = key }
    }

    private fun glowPaint(thickness: Float): Paint {
        val blur = glowRadius(thickness)
        if (blur != glowBlur) {
            glow.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            glowBlur = blur
        }
        return glow
    }

    private fun stroke(p: Paint, width: Float, colour: Int) = p.apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        color = colour
    }

    private fun fill(p: Paint, colour: Int) = p.apply {
        style = Paint.Style.FILL
        color = colour
    }

    private fun withAlpha(colour: Int, a: Float): Int =
        (((colour ushr 24) * a).roundToInt() shl 24) or (colour and 0xFFFFFF)
}
