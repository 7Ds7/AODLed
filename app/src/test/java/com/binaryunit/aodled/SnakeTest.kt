package com.binaryunit.aodled

import com.binaryunit.aodled.render.SNAKE_DARK_MS
import com.binaryunit.aodled.render.SNAKE_ERASE_MS
import com.binaryunit.aodled.render.SNAKE_GROW_MS
import com.binaryunit.aodled.render.SNAKE_PERIOD_MS
import com.binaryunit.aodled.render.SNAKE_TRAIL_START_MS
import com.binaryunit.aodled.render.SnakeSpan
import com.binaryunit.aodled.render.isClockwise
import com.binaryunit.aodled.render.snakeRange
import com.binaryunit.aodled.render.snakeSpan
import com.binaryunit.aodled.render.snakeStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakeTest {
    @Test fun `grows fast then slows down`() {
        assertEquals(SnakeSpan(0f, 0f), snakeSpan(0))
        assertTrue(snakeSpan(SNAKE_GROW_MS / 4).head > 0.4f)                 // fast start
        assertTrue(snakeSpan(SNAKE_GROW_MS * 3 / 4).head > 0.9f)             // slow finish
        assertEquals(1f, snakeSpan(SNAKE_GROW_MS).head, 0f)
    }

    @Test fun `trailing starts once two thirds are drawn`() {
        assertEquals(2f / 3, snakeSpan(SNAKE_TRAIL_START_MS).head, 0.01f)
        assertEquals(0f, snakeSpan(SNAKE_TRAIL_START_MS - 1).tail, 0f)
        assertTrue(snakeSpan(SNAKE_TRAIL_START_MS + 100).tail > 0f)
        assertTrue(snakeSpan(SNAKE_TRAIL_START_MS + 100).head < 1f)       // head still growing
    }

    @Test fun `erases slowly then speeds up`() {
        assertTrue(snakeSpan(SNAKE_TRAIL_START_MS + SNAKE_ERASE_MS / 4).tail < 0.15f)   // slow start
        assertTrue(snakeSpan(SNAKE_TRAIL_START_MS + SNAKE_ERASE_MS / 2).tail < 0.5f)
        assertEquals(1f, snakeSpan(SNAKE_TRAIL_START_MS + SNAKE_ERASE_MS - 1).tail, 0.01f)
    }

    @Test fun `tail never passes the head`() {
        for (t in 0 until SNAKE_PERIOD_MS step 5) {
            val s = snakeSpan(t)
            assertTrue("t=$t $s", s.tail <= s.head)
        }
    }

    @Test fun `dark pause draws nothing, then wraps`() {
        val dark = snakeSpan(SNAKE_TRAIL_START_MS + SNAKE_ERASE_MS + SNAKE_DARK_MS / 2)
        assertEquals(dark.head, dark.tail, 0f)
        assertEquals(SnakeSpan(0f, 0f), snakeSpan(SNAKE_PERIOD_MS))
    }

    @Test fun `cycle is trail start plus erase plus dark`() {
        assertEquals(1000L, SNAKE_GROW_MS)
        assertEquals(1000L, SNAKE_ERASE_MS)
        assertTrue(SNAKE_TRAIL_START_MS in 300L..350L)                 // early: the grow is fast at first
        assertEquals(SNAKE_TRAIL_START_MS + SNAKE_ERASE_MS + SNAKE_DARK_MS, SNAKE_PERIOD_MS)
    }

    @Test fun `leftmost sample is the start`() =
        assertEquals(2, snakeStart(floatArrayOf(10f, 5f, 1f, 5f), floatArrayOf(0f, 5f, 10f, 15f)))

    @Test fun `flat left side starts at its top`() =
        assertEquals(3, snakeStart(floatArrayOf(10f, 0f, 0.3f, 0f, 10f), floatArrayOf(0f, 50f, 30f, 20f, 40f)))

    @Test fun `clockwise square is detected`() {
        // right, down, left, up on a y-down screen = clockwise
        assertTrue(isClockwise(floatArrayOf(0f, 1f, 1f, 0f), floatArrayOf(0f, 0f, 1f, 1f)))
        assertFalse(isClockwise(floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(0f, 1f, 1f, 0f)))
    }

    @Test fun `clockwise range runs forward`() =
        assertEquals(25f to 75f, snakeRange(0f, 100f, SnakeSpan(0.25f, 0.75f), clockwise = true))

    @Test fun `counter-clockwise range runs backwards`() =
        assertEquals(-75f to -25f, snakeRange(0f, 100f, SnakeSpan(0.25f, 0.75f), clockwise = false))

    @Test fun `range may cross zero unwrapped`() =
        assertEquals(90f to 140f, snakeRange(90f, 100f, SnakeSpan(0f, 0.5f), clockwise = true))
}
