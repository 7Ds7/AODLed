package com.binaryunit.aodled.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
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

private const val EDGE_HIGHLIGHT_SHARE = 0.15f

/** Shared by the doze overlay and the editor, so what you place is what you get. */
class LightRenderer {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pixel = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val rect = RectF()
    private val path = Path()
    private val segment = Path()
    private val measure = PathMeasure()

    fun draw(canvas: Canvas, s: LightState, tMs: Long) {
        canvas.drawColor(Color.BLACK)
        val colour = s.colour ?: return
        val anim = effectiveAnimation(s.cfg.style, s.cfg.animation)
        val base = withAlpha(colour, when (anim) {
            Animation.OFF -> 1f
            Animation.PULSE -> pulseAlpha(tMs)
            Animation.ROTATE -> ROTATE_BASE_ALPHA
        })
        when (s.cfg.style) {
            Style.RING -> drawRing(canvas, s, base, colour, anim, tMs)
            Style.DOT -> drawDot(canvas, s, base)
            Style.EDGES -> drawEdges(canvas, s, base, colour, anim, tMs)
        }
    }

    private fun drawRing(canvas: Canvas, s: LightState, base: Int, colour: Int, anim: Animation, tMs: Long) {
        val cx = s.ring.cx + s.offset.dx
        val cy = s.ring.cy + s.offset.dy
        val r = s.ring.radius
        stroke.strokeWidth = ringThickness(r)
        stroke.color = base
        canvas.drawCircle(cx, cy, r, stroke)
        if (anim == Animation.ROTATE) {
            stroke.color = colour
            rect.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, rotationAngle(tMs), ROTATE_ARC_DEG, false, stroke)
        }
    }

    private fun drawDot(canvas: Canvas, s: LightState, base: Int) {
        val x = s.dot.x + s.offset.dx
        val y = s.dot.y + s.offset.dy
        if (s.dot.size < 2.5f) {
            // 1x1 or 2x2 physical pixels: no anti-aliasing, snapped to the pixel grid
            pixel.color = base
            val n = s.dot.size.roundToInt()
            val px = x.roundToInt().toFloat()
            val py = y.roundToInt().toFloat()
            canvas.drawRect(px, py, px + n, py + n, pixel)
        } else {
            fill.color = base
            canvas.drawCircle(x, y, s.dot.size / 2, fill)
        }
    }

    private fun drawEdges(canvas: Canvas, s: LightState, base: Int, colour: Int, anim: Animation, tMs: Long) {
        val t = s.cfg.edgeThickness
        val inset = t / 2 + abs(s.offset.dx)
        val corner = maxOf(0f, s.cornerRadius - inset)
        rect.set(inset, inset, canvas.width - inset, canvas.height - inset)
        stroke.strokeWidth = t
        stroke.color = base
        canvas.drawRoundRect(rect, corner, corner, stroke)
        if (anim == Animation.ROTATE) {
            path.reset()
            path.addRoundRect(rect, corner, corner, Path.Direction.CW)
            measure.setPath(path, true)
            val length = measure.length
            val start = rotationAngle(tMs) / 360f * length
            val end = start + length * EDGE_HIGHLIGHT_SHARE
            segment.reset()
            measure.getSegment(start, minOf(end, length), segment, true)
            if (end > length) measure.getSegment(0f, end - length, segment, true)
            stroke.color = colour
            canvas.drawPath(segment, stroke)
        }
    }

    private fun withAlpha(colour: Int, a: Float): Int =
        (((colour ushr 24) * a).roundToInt() shl 24) or (colour and 0xFFFFFF)
}
