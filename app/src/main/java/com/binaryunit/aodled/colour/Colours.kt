package com.binaryunit.aodled.colour

import com.binaryunit.aodled.config.ColourMode
import com.binaryunit.aodled.config.LightConfig
import kotlin.math.roundToInt

// Plain bit arithmetic on ARGB ints: android.graphics.Color is stubbed in JVM unit tests.

object Palette {
    const val WHITE = 0xFFFFFFFF.toInt()
    val ALL: List<Int> = listOf(
        WHITE,
        0xFFFF3B30.toInt(), // red
        0xFFFF9500.toInt(), // orange
        0xFFFFCC00.toInt(), // yellow
        0xFF34C759.toInt(), // green
        0xFF32D7FF.toInt(), // cyan
        0xFF3478FF.toInt(), // blue
        0xFFAF52DE.toInt(), // purple
        0xFFFF2D92.toInt(), // pink
    )
}

private const val MIN_ALPHA = 128
private const val NEAR_BLACK = 16
private const val MIN_SPREAD = 40
private const val HUE_BUCKETS = 12
private const val MIN_SHARE = 0.05

private fun alpha(c: Int) = c ushr 24
private fun red(c: Int) = (c shr 16) and 0xFF
private fun green(c: Int) = (c shr 8) and 0xFF
private fun blue(c: Int) = c and 0xFF
private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

/** Brightest version of [argb] with the same hue; null when unset/transparent; near-black becomes white. */
fun ensureVisible(argb: Int): Int? {
    if (alpha(argb) < MIN_ALPHA) return null
    val max = maxOf(red(argb), green(argb), blue(argb))
    if (max < NEAR_BLACK) return Palette.WHITE
    val scale = 255f / max
    return rgb((red(argb) * scale).roundToInt(), (green(argb) * scale).roundToInt(), (blue(argb) * scale).roundToInt())
}

/** Average colour of the most common hue among saturated opaque pixels, or null if no hue stands out. */
fun dominantColour(pixels: IntArray): Int? {
    val sums = Array(HUE_BUCKETS) { LongArray(4) } // r, g, b, count
    var opaque = 0
    for (p in pixels) {
        if (alpha(p) < MIN_ALPHA) continue
        opaque++
        val r = red(p); val g = green(p); val b = blue(p)
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        if (max - min < MIN_SPREAD) continue
        val bucket = sums[(hue(r, g, b, max, min) / (360f / HUE_BUCKETS)).toInt() % HUE_BUCKETS]
        bucket[0] += r.toLong(); bucket[1] += g.toLong(); bucket[2] += b.toLong(); bucket[3]++
    }
    val best = sums.maxBy { it[3] }
    if (best[3] == 0L || best[3] < opaque * MIN_SHARE) return null
    return rgb((best[0] / best[3]).toInt(), (best[1] / best[3]).toInt(), (best[2] / best[3]).toInt())
}

private fun hue(r: Int, g: Int, b: Int, max: Int, min: Int): Float {
    val d = (max - min).toFloat()
    val h = when (max) {
        r -> ((g - b) / d) % 6
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    } * 60
    return if (h < 0) h + 360 else h
}

/** Notification accent, else app icon's dominant colour, else white. Never black. */
fun appColour(notificationColour: Int, iconPixels: IntArray?): Int =
    ensureVisible(notificationColour)
        ?: iconPixels?.let(::dominantColour)?.let(::ensureVisible)
        ?: Palette.WHITE

fun displayColour(appColour: Int, cfg: LightConfig): Int =
    if (cfg.colourMode == ColourMode.FIXED) cfg.fixedColour else appColour
