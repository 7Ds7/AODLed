package com.binaryunit.aodled

import com.binaryunit.aodled.config.BurnIn
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class BurnInTest {
    @Test fun `offsets stay within MAX_PX`() {
        val rnd = Random(42)
        repeat(1000) {
            val o = BurnIn.next(rnd)
            assertTrue(abs(o.dx) <= BurnIn.MAX_PX && abs(o.dy) <= BurnIn.MAX_PX)
        }
    }

    @Test fun `offsets actually vary`() {
        val rnd = Random(7)
        assertTrue((1..50).map { BurnIn.next(rnd) }.toSet().size > 1)
    }
}
