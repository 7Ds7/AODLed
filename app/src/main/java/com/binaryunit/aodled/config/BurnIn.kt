package com.binaryunit.aodled.config

import kotlin.random.Random

data class Offset(val dx: Int, val dy: Int)

/** Small random shift of the light so the same pixels aren't lit for hours. */
object BurnIn {
    const val MAX_PX = 3
    const val INTERVAL_MS = 180_000L

    fun next(rnd: Random): Offset = Offset(rnd.nextInt(-MAX_PX, MAX_PX + 1), rnd.nextInt(-MAX_PX, MAX_PX + 1))
}
