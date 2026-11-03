package com.binaryunit.aodled.config

import kotlin.math.ceil
import kotlin.random.Random

data class Offset(val dx: Int, val dy: Int)

/**
 * Small random shift of the light so the same pixels aren't lit for hours. Dot moves by the offset; Ring and Cutout
 * grow or shrink by dx around their centre so they stay on the camera; Edges move inward by |dx|.
 */
object BurnIn {
    const val BASE_PX = 3
    const val INTERVAL_MS = 180_000L

    /** At least the line's own [thickness], so a thick line's centre still goes dark about half the time. */
    fun shift(thickness: Float): Int = maxOf(BASE_PX, ceil(thickness).toInt())

    fun next(rnd: Random, maxPx: Int): Offset = Offset(rnd.nextInt(-maxPx, maxPx + 1), rnd.nextInt(-maxPx, maxPx + 1))
}
