package com.binaryunit.aodled.render

import kotlin.math.pow

// Ripple: bursts of rings growing from the placed radius (the first, smallest ring, fitted around the hole like Ring)
// out to [RIPPLE_SPREAD] times it, each born at full brightness and fading as it grows. Everything scales with the
// first ring, so a small and a big ripple look the same. Timings picked by the owner in a mockup (2026-11-12).

private const val RIPPLE_EVERY_MS = 350L
private const val RIPPLE_LIFE_MS = 800L
private const val RIPPLE_RINGS = 3
private const val RIPPLE_PAUSE_MS = 800L
const val RIPPLE_PERIOD_MS = (RIPPLE_RINGS - 1) * RIPPLE_EVERY_MS + RIPPLE_LIFE_MS + RIPPLE_PAUSE_MS
const val RIPPLE_SPREAD = 2.5f // where the rings end, as a multiple of the first ring
private const val RIPPLE_EASE = 2.2f // growth slows down as the ring gets bigger
private const val RIPPLE_FADE = 2.8f // above 1 fades early, so the outer part of the growth is faint

/** One ring of a burst: its radius as a share of the way from the first ring to the last, and its alpha. */
data class Wave(val growth: Float, val alpha: Float)

/** The rings alive at [tMs] into the cycle, oldest first; none during the pause. */
fun rippleWaves(tMs: Long): List<Wave> {
    val t = tMs % RIPPLE_PERIOD_MS
    return (0 until RIPPLE_RINGS).mapNotNull { i ->
        val age = t - i * RIPPLE_EVERY_MS
        if (age < 0 || age >= RIPPLE_LIFE_MS) return@mapNotNull null
        val q = age.toFloat() / RIPPLE_LIFE_MS
        Wave(1f - (1f - q).pow(RIPPLE_EASE), (1f - q).pow(RIPPLE_FADE))
    }
}

/** Radius of a ring [growth] of the way out from the first ring ([start]) to the last. */
fun rippleRadius(start: Float, growth: Float): Float = start * (1f + (RIPPLE_SPREAD - 1f) * growth)
