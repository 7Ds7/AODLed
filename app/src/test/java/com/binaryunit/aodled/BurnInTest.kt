package com.binaryunit.aodled

import com.binaryunit.aodled.config.BurnIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class BurnInTest {
    @Test fun `offsets stay within the given shift`() {
        val rnd = Random(42)
        repeat(1000) {
            val o = BurnIn.next(rnd, 8)
            assertTrue(abs(o.dx) <= 8 && abs(o.dy) <= 8)
        }
    }

    @Test fun `offsets reach the full shift`() {
        val rnd = Random(3)
        assertTrue((1..1000).map { BurnIn.next(rnd, 8) }.any { abs(it.dx) == 8 })
    }

    @Test fun `offsets actually vary`() {
        val rnd = Random(7)
        assertTrue((1..50).map { BurnIn.next(rnd, BurnIn.BASE_PX) }.toSet().size > 1)
    }

    @Test fun `thin lines keep the base shift`() {
        assertEquals(BurnIn.BASE_PX, BurnIn.shift(1f))
        assertEquals(BurnIn.BASE_PX, BurnIn.shift(3f))
    }

    @Test fun `thick lines shift by at least their thickness`() {
        assertEquals(8, BurnIn.shift(7.2f))
        assertEquals(24, BurnIn.shift(24f))
    }

    /** The shift is a whole number of px, so the centre of a line t wide is lit for |d| < t/2 of 2n+1 offsets. */
    @Test fun `a line's centre stays lit at most about half the time`() {
        for (t in listOf(2f, 4f, 7f, 12f, 24f)) {
            val n = BurnIn.shift(t)
            val lit = (-n..n).count { abs(it) < t / 2 }
            assertTrue("t=$t lit $lit of ${2 * n + 1}", lit.toFloat() / (2 * n + 1) <= 0.6f)
        }
    }
}
