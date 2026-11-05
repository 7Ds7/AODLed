package com.binaryunit.aodled.render

import com.binaryunit.aodled.config.Animation
import com.binaryunit.aodled.config.LightConfig
import com.binaryunit.aodled.config.Pulse
import com.binaryunit.aodled.config.Style
import kotlin.math.PI
import kotlin.math.cos

const val ANIM_FPS = 30
const val FRAME_MS = 1000L / ANIM_FPS
const val PULSE_PERIOD_MS = 4000L
const val PULSE_DARK_MS = 600L
const val ROTATE_PERIOD_MS = 2000L
const val GLOW_FACTOR = 3f
const val GLOW_MIN = 4f
const val GLOW_MAX = 24f
const val GLOW_ALPHA = 0.7f
// AOD runs the panel so dim that the faint outer halo shows as black: draw a tighter, full-strength glow there
const val AOD_GLOW_FACTOR = 1f
const val AOD_GLOW_ALPHA = 1f

/** A dot can't move along a path, so Motion falls back to pulse. */
fun effectiveAnimation(style: Style, anim: Animation): Animation =
    if (style == Style.DOT && anim == Animation.MOTION) Animation.PULSE else anim

/** The popup's settings for Pulse; a dot on Motion falls back to the original pulse. */
fun pulseFor(cfg: LightConfig): Pulse = if (cfg.animation == Animation.PULSE) cfg.pulse else Pulse()

private fun Pulse.darkMs() = periodMs * PULSE_DARK_MS / PULSE_PERIOD_MS
private fun Pulse.fadeMs() = (periodMs - darkMs()) / 2

/** Lit time of a burst: every blink but the last in full, then the last one's fade out. */
private fun Pulse.burstMs() = (blinks - 1) * periodMs + fadeMs()

/**
 * Fades fully out, stays black for a share of the period like [PULSE_DARK_MS] of [PULSE_PERIOD_MS], fades back in;
 * 1.0 at the start of each period. With a pause, [tMs] counts from the start of a burst, and after the last blink
 * fades out the light stays black until the next one.
 */
fun pulseAlpha(tMs: Long, p: Pulse = Pulse()): Float {
    var t = tMs
    if (p.pauseMs > 0) {
        t %= p.burstMs() + p.pauseMs
        if (t >= p.burstMs()) return 0f
    }
    t %= p.periodMs
    val fade = p.fadeMs()
    val dark = p.darkMs()
    return when {
        t < fade -> 0.5f + 0.5f * cos(PI * t / fade).toFloat()
        t < fade + dark -> 0f
        else -> 0.5f - 0.5f * cos(PI * (t - fade - dark) / fade).toFloat()
    }
}

/** Wait before the next frame at [tMs] into the bursts: one frame while lit, the rest of the pause while dark. */
fun pulseFrameDelay(tMs: Long, p: Pulse): Long {
    if (p.pauseMs == 0L) return FRAME_MS
    val cycle = p.burstMs() + p.pauseMs
    val t = tMs % cycle
    return if (t < p.burstMs()) FRAME_MS else cycle - t
}

/**
 * [tMs]'s place in a [period]-long cycle, rounded down to a whole frame. Every cycle then draws the same few shapes,
 * so the GPU's cache of blurred glows reuses them instead of gaining one per frame (measured on a Pixel: 3000+
 * cached surfaces, about 130 MB, with Ring Motion on the AOD).
 */
fun animPhase(tMs: Long, period: Long): Long {
    val p = tMs % period
    return p - p % FRAME_MS
}

/** Whole frames that fit in a [period]-long cycle. */
fun framesPer(period: Long): Int = (period / FRAME_MS).toInt()

/** Which of [framesPer] frames [tMs] falls in; every frame lasts [FRAME_MS], the last leads straight back to the first. */
fun animFrame(tMs: Long, period: Long): Int = ((tMs / FRAME_MS) % framesPer(period)).toInt()

/** Blur radius of the soft halo drawn around a line or dot of the given thickness. */
fun glowRadius(thickness: Float, aod: Boolean = false): Float =
    (thickness * if (aod) AOD_GLOW_FACTOR else GLOW_FACTOR).coerceIn(GLOW_MIN, GLOW_MAX)

fun glowAlpha(aod: Boolean): Float = if (aod) AOD_GLOW_ALPHA else GLOW_ALPHA
