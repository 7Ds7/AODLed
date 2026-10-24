package com.binaryunit.aodled.render

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import com.binaryunit.aodled.config.Animation
import com.binaryunit.aodled.config.CutoutPlacement
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
    val aod: Boolean = false,
    val cutout: CutoutPlacement? = null,
    val cutoutPath: Path? = null,
)

private const val EDGE_COMET_SHARE = 0.2f  // comet length as a share of the screen border
private const val EDGE_COMET_STEPS = 12
private const val CUTOUT_SAMPLES = 64       // points per contour used to find its leftmost point and winding

/** Where the snake starts on a contour (fraction of its length) and which way is clockwise on screen. */
private class Contour(val startFraction: Float, val clockwise: Boolean)

/**
 * Shared by the doze overlay and the editor, so what you place is what you get. Every shape is drawn twice:
 * a blurred halo (glow) and a crisp core on top.
 */
class LightRenderer {
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pixel = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private var glowBlur = -1f
    private var aod = false
    private val rect = RectF()
    private val path = Path()
    private val segment = Path()
    private val measure = PathMeasure()
    private val matrix = Matrix()
    private val scaledPath = Path()
    private val pos = FloatArray(2)
    private var contoursFor: Path? = null
    private var contours: List<Contour> = emptyList()

    fun draw(canvas: Canvas, s: LightState, tMs: Long) {
        canvas.drawColor(Color.BLACK)
        val colour = s.colour ?: return
        aod = s.aod
        val anim = effectiveAnimation(s.cfg.style, s.cfg.animation)
        val alpha = if (anim == Animation.PULSE) pulseAlpha(tMs) else 1f
        when (s.cfg.style) {
            Style.RING -> drawRing(canvas, s, colour, alpha, anim, tMs)
            Style.DOT -> drawDot(canvas, s, withAlpha(colour, alpha))
            Style.EDGES -> drawEdges(canvas, s, colour, alpha, anim, tMs)
            Style.CUTOUT -> {
                val placement = s.cutout
                val source = s.cutoutPath
                if (placement == null || source == null) drawRing(canvas, s, colour, alpha, anim, tMs)
                else drawCutout(canvas, s, placement, source, colour, alpha, anim, tMs)
            }
        }
    }

    private fun drawRing(canvas: Canvas, s: LightState, colour: Int, alpha: Float, anim: Animation, tMs: Long) {
        val cx = s.ring.cx + s.offset.dx
        val cy = s.ring.cy + s.offset.dy
        val r = s.ring.radius
        val t = ringThickness(r)
        if (anim == Animation.MOTION) {
            drawSwirl(canvas, cx, cy, r, colour, tMs)
        } else {
            val c = withAlpha(colour, alpha)
            canvas.drawCircle(cx, cy, r, stroke(glowPaint(t), t, withAlpha(c, glowAlpha(aod))))
            canvas.drawCircle(cx, cy, r, stroke(core, t, c))
        }
    }

    /** Faint ring that swells and thins, at full brightness only inside a sweeping 45° band. */
    private fun drawSwirl(canvas: Canvas, cx: Float, cy: Float, r: Float, colour: Int, tMs: Long) {
        val w = swirlStroke(r, tMs)
        if (w < 0.5f) return
        val faint = withAlpha(colour, SWIRL_FAINT_ALPHA)
        canvas.drawCircle(cx, cy, r, stroke(glowPaint(w), w, withAlpha(faint, glowAlpha(aod))))
        canvas.drawCircle(cx, cy, r, stroke(core, w, faint))
        val bx = cx + swirlBandOffset(tMs) * r
        val half = SWIRL_BAND_WIDTH * r / 2
        canvas.save()
        canvas.translate(bx, cy)
        canvas.rotate(SWIRL_BAND_ANGLE)
        canvas.clipRect(-half, -4 * r, half, 4 * r)
        canvas.rotate(-SWIRL_BAND_ANGLE)
        canvas.translate(-bx, -cy)
        canvas.drawCircle(cx, cy, r, stroke(glowPaint(w), w, withAlpha(colour, glowAlpha(aod))))
        canvas.drawCircle(cx, cy, r, stroke(core, w, colour))
        canvas.restore()
    }

    private fun drawDot(canvas: Canvas, s: LightState, colour: Int) {
        val x = s.dot.x + s.offset.dx
        val y = s.dot.y + s.offset.dy
        val size = s.dot.size
        canvas.drawCircle(x, y, maxOf(size / 2, 1f), fill(glowPaint(size), withAlpha(colour, glowAlpha(aod))))
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
        if (anim == Animation.MOTION) {
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
                canvas.drawPath(segment, stroke(glowPaint(t), t, withAlpha(colour, a * glowAlpha(aod))))
                canvas.drawPath(segment, stroke(core, t, withAlpha(colour, a)).apply { strokeCap = Paint.Cap.ROUND })
            }
            core.strokeCap = Paint.Cap.BUTT
        } else {
            val c = withAlpha(colour, alpha)
            canvas.drawRoundRect(rect, corner, corner, stroke(glowPaint(t), t, withAlpha(c, glowAlpha(aod))))
            canvas.drawRoundRect(rect, corner, corner, stroke(core, t, c))
        }
    }

    /** The cutout's own outline, scaled and moved; with Motion a snake runs along each contour. */
    private fun drawCutout(
        canvas: Canvas, s: LightState, p: CutoutPlacement, source: Path, colour: Int, alpha: Float, anim: Animation, tMs: Long,
    ) {
        source.computeBounds(rect, true)
        val bx = rect.centerX()
        val by = rect.centerY()
        val t = ringThickness(maxOf(rect.width(), rect.height()) / 2 * p.scale)
        matrix.setScale(p.scale, p.scale, bx, by)
        matrix.postTranslate(p.cx - bx + s.offset.dx, p.cy - by + s.offset.dy)
        source.transform(matrix, scaledPath)
        if (anim != Animation.MOTION) {
            val c = withAlpha(colour, alpha)
            canvas.drawPath(scaledPath, stroke(glowPaint(t), t, withAlpha(c, glowAlpha(aod))))
            canvas.drawPath(scaledPath, stroke(core, t, c))
            return
        }
        val span = snakeSpan(tMs)
        if (span.head - span.tail < 1e-3f) return
        // Scaling and moving don't change where a contour starts or its winding, so measure the source once
        if (contoursFor !== source) {
            contours = contoursOf(source)
            contoursFor = source
        }
        measure.setPath(scaledPath, true)
        var i = 0
        do {
            val contour = contours.getOrNull(i++) ?: break
            val length = measure.length
            val (from, to) = snakeRange(contour.startFraction * length, length, span, contour.clockwise)
            segment.reset()
            addSegment(from, to, length)
            canvas.drawPath(segment, stroke(glowPaint(t), t, withAlpha(colour, glowAlpha(aod))))
            canvas.drawPath(segment, stroke(core, t, colour))
        } while (measure.nextContour())
    }

    private fun contoursOf(source: Path): List<Contour> {
        val m = PathMeasure(source, true)
        val xs = FloatArray(CUTOUT_SAMPLES)
        val ys = FloatArray(CUTOUT_SAMPLES)
        val out = mutableListOf<Contour>()
        do {
            for (k in 0 until CUTOUT_SAMPLES) {
                m.getPosTan(m.length * k / CUTOUT_SAMPLES, pos, null)
                xs[k] = pos[0]
                ys[k] = pos[1]
            }
            out += Contour(snakeStart(xs, ys).toFloat() / CUTOUT_SAMPLES, isClockwise(xs, ys))
        } while (m.nextContour())
        return out
    }

    /** Appends [from, to) of the measured closed path to [segment], wrapping around its start. */
    private fun addSegment(from: Float, to: Float, length: Float) {
        val a = ((from % length) + length) % length
        val b = a + (to - from)
        measure.getSegment(a, minOf(b, length), segment, true)
        if (b > length) measure.getSegment(0f, b - length, segment, true)
    }

    private fun glowPaint(thickness: Float): Paint {
        val blur = glowRadius(thickness, aod)
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
