package com.binaryunit.aodled.config

const val RING_GAP = 5f
const val CUTOUT_GAP = 8f
const val RING_MIN_RADIUS = 10f
const val RING_MAX_RADIUS = 300f
const val RING_THICKNESS_RATIO = 0.08f
const val DOT_MIN = 1f
const val DOT_MAX = 24f
const val DOT_DEFAULT = 4f
const val DOT_GAP = 24f
const val EDGE_MIN = 1f
const val EDGE_MAX = 24f
const val EDGE_DEFAULT = 2f
const val LINE_SCALE_MIN = 0.5f
const val LINE_SCALE_MAX = 3f

/** Plain rectangle so geometry stays testable on the JVM (android.graphics.RectF is stubbed there). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centreX get() = (left + right) / 2
    val centreY get() = (top + bottom) / 2
}

val Box.halfSize: Float get() = maxOf(right - left, bottom - top) / 2

fun defaultRing(cutout: Box?, screenWidth: Int): RingPlacement {
    if (cutout == null) return RingPlacement(screenWidth / 2f, 60f, 48f)
    val size = maxOf(cutout.right - cutout.left, cutout.bottom - cutout.top)
    return RingPlacement(cutout.centreX, cutout.centreY, size / 2 + RING_GAP)
}

fun defaultDot(cutout: Box?, screenWidth: Int): DotPlacement {
    if (cutout == null) return DotPlacement(screenWidth / 2f, 124f, DOT_DEFAULT)
    return DotPlacement(cutout.centreX, cutout.bottom + DOT_GAP, DOT_DEFAULT)
}

/**
 * Centred on the whole cutout, scaled so the line sits [CUTOUT_GAP] outside it (exactly so on a round hole).
 * [halfSize] is that of the largest single hole, so two holes far apart still get a snug outline.
 */
fun defaultCutout(bounds: Box, halfSize: Float = bounds.halfSize): CutoutPlacement =
    CutoutPlacement(bounds.centreX, bounds.centreY, (halfSize + CUTOUT_GAP) / halfSize)

/** [scale] is the editor's thickness slider, so resizing still scales the line. */
fun ringThickness(radius: Float, scale: Float = 1f): Float = maxOf(1f, radius * RING_THICKNESS_RATIO * scale)

/** Width of the lit line (a dot's size); without a hole, Cutout is drawn as the ring. */
fun LightConfig.lineThickness(ring: RingPlacement, dot: DotPlacement, cutout: CutoutPlacement?, cutoutHalf: Float): Float =
    when (style) {
        Style.RING -> ringThickness(ring.radius, ringLineScale)
        Style.DOT -> dot.size
        Style.EDGES -> edgeThickness
        Style.CUTOUT -> cutout?.let { ringThickness(cutoutHalf * it.scale, cutoutLineScale) }
            ?: ringThickness(ring.radius, ringLineScale)
    }

fun RingPlacement.moved(dx: Float, dy: Float, w: Int, h: Int) =
    copy(cx = (cx + dx).coerceIn(0f, w.toFloat()), cy = (cy + dy).coerceIn(0f, h.toFloat()))

fun RingPlacement.pinched(f: Float) = copy(radius = (radius * f).coerceIn(RING_MIN_RADIUS, RING_MAX_RADIUS))

fun DotPlacement.moved(dx: Float, dy: Float, w: Int, h: Int) =
    copy(x = (x + dx).coerceIn(0f, w.toFloat()), y = (y + dy).coerceIn(0f, h.toFloat()))

fun DotPlacement.pinched(f: Float) = copy(size = (size * f).coerceIn(DOT_MIN, DOT_MAX))

fun CutoutPlacement.moved(dx: Float, dy: Float, w: Int, h: Int) =
    copy(cx = (cx + dx).coerceIn(0f, w.toFloat()), cy = (cy + dy).coerceIn(0f, h.toFloat()))

/** Keeps the scaled outline's half size within the ring's radius limits. */
fun CutoutPlacement.pinched(f: Float, halfSize: Float) =
    copy(scale = (scale * f).coerceIn(RING_MIN_RADIUS / halfSize, RING_MAX_RADIUS / halfSize))

/** Scale that moves the outline's edge out by [d] px (in when negative), for the burn-in shift. */
fun CutoutPlacement.grownBy(d: Float, halfSize: Float): Float = scale + d / halfSize

fun pinchedEdge(thickness: Float, f: Float): Float = (thickness * f).coerceIn(EDGE_MIN, EDGE_MAX)

/** True when what the editor sets (style, position, size, thickness) differs from [other]. */
fun LightConfig.placementDiffers(other: LightConfig): Boolean = placement() != other.placement()

private fun LightConfig.placement() = listOf(style, ring, dot, cutout, edgeThickness, ringLineScale, cutoutLineScale)

/** Back to the default position/size for the current style only. */
fun LightConfig.resetPlacement(): LightConfig = when (style) {
    Style.RING -> copy(ring = null, ringLineScale = 1f)
    Style.DOT -> copy(dot = null)
    Style.EDGES -> copy(edgeThickness = EDGE_DEFAULT)
    Style.CUTOUT -> copy(cutout = null, cutoutLineScale = 1f)
}
