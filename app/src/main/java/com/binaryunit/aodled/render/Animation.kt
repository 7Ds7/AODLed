package com.binaryunit.aodled.render

import com.binaryunit.aodled.config.Animation
import com.binaryunit.aodled.config.Style
import kotlin.math.PI
import kotlin.math.cos

const val ANIM_FPS = 15
const val PULSE_PERIOD_MS = 4000L
const val PULSE_MIN_ALPHA = 0.3f
const val ROTATE_PERIOD_MS = 6000L
const val ROTATE_BASE_ALPHA = 0.35f
const val ROTATE_ARC_DEG = 90f

/** A dot can't rotate, so rotation falls back to pulse. */
fun effectiveAnimation(style: Style, anim: Animation): Animation =
    if (style == Style.DOT && anim == Animation.ROTATE) Animation.PULSE else anim

/** 1.0 at t=0, [PULSE_MIN_ALPHA] at half a period, cosine in between. */
fun pulseAlpha(tMs: Long): Float {
    val phase = (tMs % PULSE_PERIOD_MS).toDouble() / PULSE_PERIOD_MS
    return PULSE_MIN_ALPHA + (1 - PULSE_MIN_ALPHA) * (0.5f + 0.5f * cos(2 * PI * phase).toFloat())
}

fun rotationAngle(tMs: Long): Float = (tMs % ROTATE_PERIOD_MS) * 360f / ROTATE_PERIOD_MS
