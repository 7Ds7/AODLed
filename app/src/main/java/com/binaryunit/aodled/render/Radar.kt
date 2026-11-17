package com.binaryunit.aodled.render

import kotlin.math.pow

// Radar: bursts of arcs on both sides of the hole, growing from the placed radius (the first, smallest arc, fitted
// around the hole like Ring) out to [RADAR_SPREAD] times it, each born at full brightness and fading as it grows.
// Its own copy of Ripple's timing so the two are tuned apart. Values picked by the owner in a mockup (2026-11-17).

private const val RADAR_EVERY_MS = 235L
private const val RADAR_LIFE_MS = 775L
private const val RADAR_ARCS = 3
private const val RADAR_PAUSE_MS = 750L
const val RADAR_PERIOD_MS = (RADAR_ARCS - 1) * RADAR_EVERY_MS + RADAR_LIFE_MS + RADAR_PAUSE_MS
const val RADAR_SPREAD = 2.75f // where the arcs end, as a multiple of the first arc
const val RADAR_SWEEP = 40f // degrees of each arc, centred on the left and the right
private const val RADAR_EASE = 2f // growth slows down as the arc gets bigger
private const val RADAR_FADE = 1.6f // above 1 fades early, so the outer part of the growth is faint

/** The arcs alive at [tMs] into the cycle, oldest first; none during the pause. */
fun radarWaves(tMs: Long): List<Wave> {
    val t = tMs % RADAR_PERIOD_MS
    return (0 until RADAR_ARCS).mapNotNull { i ->
        val age = t - i * RADAR_EVERY_MS
        if (age < 0 || age >= RADAR_LIFE_MS) return@mapNotNull null
        val q = age.toFloat() / RADAR_LIFE_MS
        Wave(1f - (1f - q).pow(RADAR_EASE), (1f - q).pow(RADAR_FADE))
    }
}

/** Radius of an arc [growth] of the way out from the first arc ([start]) to the last. */
fun radarRadius(start: Float, growth: Float): Float = start * (1f + (RADAR_SPREAD - 1f) * growth)
