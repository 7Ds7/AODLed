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
import com.binaryunit.aodled.config.grownBy
import com.binaryunit.aodled.config.lineThickness
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

/** Width of the lit line, which sets the burn-in shift. Without a cutout path, Cutout is drawn as the ring. */
fun LightState.lineThickness(): Float =
    cfg.lineThickness(ring, dot, cutout.takeIf { cutoutPath != null }, cutoutPath?.contourHalfSize() ?: 0f)

private const val EDGE_COMET_STEPS = 12    // segments of one frame's travel each: 12 of a 60-frame lap is a fifth of the border
private const val CUTOUT_SAMPLES = 64       // points per contour used to find its leftmost point and winding

/**
 * One hole's outline, scaled around its own centre ([cx], [cy]); where the snake starts on it (fraction of its
 * length) and which way is clockwise on screen.
 */
private class Contour(val path: Path, val cx: Float, val cy: Float, val startFraction: Float, val clockwise: Boolean)

/**
 * Moving segments kept per frame step and reused every cycle. The GPU caches a blurred path by the path object, so a
 * fresh path each frame would add a cache entry every frame; [owner] is what the segments were built for.
 */
private class SegmentCache {
    private var owner: Any? = null
    private val paths = HashMap<Long, Path>()

    fun get(owner: Any, key: Long, build: (Path) -> Unit): Path {
        if (owner != this.owner) {
            paths.clear()
            this.owner = owner
        }
        return paths.getOrPut(key) { Path().also(build) }
    }
}

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
    private val measure = PathMeasure()
    private val matrix = Matrix()
    private val scaledPath = Path()
    private val pos = FloatArray(2)
    private var contoursFor: Path? = null
    private var contours: List<Contour> = emptyList()
    private var cutoutHalf = 0f
    private val cometSegments = SegmentCache()
    private val snakeSegments = SegmentCache()

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
        val cx = s.ring.cx
        val cy = s.ring.cy
        // Burn-in grows or shrinks the ring around the camera; the line keeps the saved size's thickness
        val r = s.ring.radius + s.offset.dx
        val t = ringThickness(s.ring.radius, s.cfg.ringLineScale)
        if (anim == Animation.MOTION) {
            val phase = animPhase(tMs, SWIRL_PERIOD_MS)
            drawSwirl(canvas, cx, cy, r, swirlStroke(s.ring.radius, phase, s.cfg.ringLineScale), colour, phase)
        } else {
            val c = withAlpha(colour, alpha)
            canvas.drawCircle(cx, cy, r, stroke(glowPaint(t), t, withAlpha(c, glowAlpha(aod))))
            canvas.drawCircle(cx, cy, r, stroke(core, t, c))
        }
    }

    /** Faint ring that swells and thins, at full brightness only inside a sweeping 45° band. */
    private fun drawSwirl(canvas: Canvas, cx: Float, cy: Float, r: Float, w: Float, colour: Int, tMs: Long) {
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
            // Comet along the border: short segments brightening from tail to head. Each segment is one frame's travel
            // long and sits on a fixed slot, so the border has only one segment path per frame of the lap
            path.reset()
            path.addRoundRect(rect, corner, corner, Path.Direction.CW)
            measure.setPath(path, true)
            val length = measure.length
            val frames = framesPer(ROTATE_PERIOD_MS)
            val head = animFrame(tMs, ROTATE_PERIOD_MS)
            val step = length / frames
            val owner = listOf(rect.left, rect.top, rect.right, rect.bottom, corner)
            for (i in 0 until EDGE_COMET_STEPS) {
                val a = (i + 1f) / EDGE_COMET_STEPS
                val slot = Math.floorMod(head - (EDGE_COMET_STEPS - i), frames)
                val segment = cometSegments.get(owner, slot.toLong()) { addSegment(it, slot * step, (slot + 1) * step, length) }
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

    /** The cutout's own outline, scaled and moved; with Motion a snake runs along each hole's outline. */
    private fun drawCutout(
        canvas: Canvas, s: LightState, p: CutoutPlacement, source: Path, colour: Int, alpha: Float, anim: Animation, tMs: Long,
    ) {
        // Splitting and measuring are done once per source path: scaling and moving don't change them
        if (contoursFor !== source) {
            contours = source.contours().map(::contourOf)
            cutoutHalf = source.contourHalfSize()
            contoursFor = source
        }
        source.computeBounds(rect, true)
        val dx = p.cx - rect.centerX()
        val dy = p.cy - rect.centerY()
        val t = ringThickness(cutoutHalf * p.scale, s.cfg.cutoutLineScale)
        // Burn-in grows or shrinks the outline around the camera, like the ring
        val scale = p.grownBy(s.offset.dx.toFloat(), cutoutHalf)
        val phase = animPhase(tMs, SNAKE_PERIOD_MS)
        val span = snakeSpan(phase)
        val owner = listOf(source, scale, dx, dy)
        if (anim == Animation.MOTION && span.head - span.tail < 1e-3f) return
        val c = withAlpha(colour, alpha)
        for ((n, contour) in contours.withIndex()) {
            matrix.setScale(scale, scale, contour.cx, contour.cy)
            matrix.postTranslate(dx, dy)
            contour.path.transform(matrix, scaledPath)
            if (anim != Animation.MOTION) {
                canvas.drawPath(scaledPath, stroke(glowPaint(t), t, withAlpha(c, glowAlpha(aod))))
                canvas.drawPath(scaledPath, stroke(core, t, c))
                continue
            }
            measure.setPath(scaledPath, true)
            val length = measure.length
            val (from, to) = snakeRange(contour.startFraction * length, length, span, contour.clockwise)
            val segment = snakeSegments.get(owner, phase / FRAME_MS * contours.size + n) { addSegment(it, from, to, length) }
            canvas.drawPath(segment, stroke(glowPaint(t), t, withAlpha(colour, glowAlpha(aod))))
            canvas.drawPath(segment, stroke(core, t, colour))
        }
    }

    private fun contourOf(path: Path): Contour {
        val m = PathMeasure(path, true)
        val xs = FloatArray(CUTOUT_SAMPLES)
        val ys = FloatArray(CUTOUT_SAMPLES)
        for (k in 0 until CUTOUT_SAMPLES) {
            m.getPosTan(m.length * k / CUTOUT_SAMPLES, pos, null)
            xs[k] = pos[0]
            ys[k] = pos[1]
        }
        val box = path.box()
        return Contour(path, box.centreX, box.centreY, snakeStart(xs, ys).toFloat() / CUTOUT_SAMPLES, isClockwise(xs, ys))
    }

    /** Appends [from, to) of the measured closed path to [dst], wrapping around its start. */
    private fun addSegment(dst: Path, from: Float, to: Float, length: Float) {
        val a = ((from % length) + length) % length
        val b = a + (to - from)
        measure.getSegment(a, minOf(b, length), dst, true)
        if (b > length) measure.getSegment(0f, b - length, dst, true)
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
