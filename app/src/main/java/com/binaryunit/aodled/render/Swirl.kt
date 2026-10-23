package com.binaryunit.aodled.render

import kotlin.math.abs

// Ring "swirl", modelled on the Samsung punch-hole animation HoleyLight used: a 1 s cycle in which a faint ring
// swells and thins while a bright 45° band sweeps across it. Distances are in units of the ring radius.

const val SWIRL_PERIOD_MS = 1500L // Samsung loop is 1017 ms; owner wanted a longer dark pause
private const val SWELL_END_MS = 150L
private const val THIN_END_MS = 883L
private const val SWEEP_END_MS = 1000L
const val SWIRL_MAX_WIDTH = 14f / 52f
const val SWIRL_FAINT_ALPHA = 0.3f
const val SWIRL_BAND_WIDTH = 48f / 52f
const val SWIRL_BAND_ANGLE = 45f
const val SWIRL_BAND_START = -107.5f / 52f
const val SWIRL_BAND_END = 108f / 52f

/** Ring stroke width as a share of [SWIRL_MAX_WIDTH]: quick swell, slow thinning, then a short gap. */
fun swirlWidth(tMs: Long): Float {
    val t = tMs % SWIRL_PERIOD_MS
    return when {
        t < SWELL_END_MS -> cubicBezier(0.33f, 0f, 0.5f, 1f, t.toFloat() / SWELL_END_MS)
        t < THIN_END_MS -> 1f - cubicBezier(0.1f, 0f, 0.833f, 0.833f, (t - SWELL_END_MS).toFloat() / (THIN_END_MS - SWELL_END_MS))
        else -> 0f
    }
}

/** Horizontal offset of the bright band's centre from the ring centre. */
fun swirlBandOffset(tMs: Long): Float {
    val x = ((tMs % SWIRL_PERIOD_MS).toFloat() / SWEEP_END_MS).coerceAtMost(1f)
    return SWIRL_BAND_START + (SWIRL_BAND_END - SWIRL_BAND_START) * cubicBezier(0.33f, 0f, 0.1f, 1f, x)
}

/** CSS-style cubic-bezier easing: y for a given x, with control points (x1,y1) and (x2,y2). */
fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float, x: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    fun bezier(t: Float, p1: Float, p2: Float) = 3 * (1 - t) * (1 - t) * t * p1 + 3 * (1 - t) * t * t * p2 + t * t * t
    // Bisection on t so that bezier_x(t) == x; x(t) is monotonic for control x in [0, 1]
    var lo = 0f
    var hi = 1f
    var t = x
    repeat(30) {
        val bx = bezier(t, x1, x2)
        if (abs(bx - x) < 1e-5f) return bezier(t, y1, y2)
        if (bx < x) lo = t else hi = t
        t = (lo + hi) / 2
    }
    return bezier(t, y1, y2)
}
