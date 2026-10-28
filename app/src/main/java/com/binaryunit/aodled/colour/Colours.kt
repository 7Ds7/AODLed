package com.binaryunit.aodled.colour

import com.binaryunit.aodled.config.ColourMode
import com.binaryunit.aodled.config.LightConfig
import kotlin.math.abs
import kotlin.math.pow
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
private const val MIN_CONTRAST_ON_BLACK = 3.0 // WCAG minimum for UI parts; a custom colour below it gets a warning

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

/** Custom fixed colour from [hue] (degrees), [saturation] and [brightness] (both 0 … 1). */
fun pickedColour(hue: Float, saturation: Float, brightness: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f / 60f
    val c = brightness * saturation
    val x = c * (1 - abs(h % 2 - 1))
    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = brightness - c
    return rgb(((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
}

/** Where the picker's sliders start for a saved colour. */
fun hueSaturationBrightness(argb: Int): Triple<Float, Float, Float> {
    val r = red(argb); val g = green(argb); val b = blue(argb)
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    if (max == min) return Triple(0f, 0f, max / 255f)
    return Triple(hue(r, g, b, max, min), (max - min).toFloat() / max, max / 255f)
}

/** WCAG contrast against black at or above [MIN_CONTRAST_ON_BLACK]; below it the picker warns. */
fun visibleOnBlack(argb: Int): Boolean {
    fun linear(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    val luminance = 0.2126 * linear(red(argb)) + 0.7152 * linear(green(argb)) + 0.0722 * linear(blue(argb))
    return (luminance + 0.05) / 0.05 >= MIN_CONTRAST_ON_BLACK
}

/** Opaque colour from "#RRGGBB" or "RRGGBB" (any case), or null until the text is a complete code. */
fun parseHex(text: String): Int? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6 || !digits.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
    return (0xFF shl 24) or digits.toInt(16)
}

fun hexOf(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

fun displayColour(appColour: Int, cfg: LightConfig): Int =
    if (cfg.colourMode == ColourMode.FIXED) cfg.fixedColour else appColour
