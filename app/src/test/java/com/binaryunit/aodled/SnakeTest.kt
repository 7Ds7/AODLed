package com.binaryunit.aodled

import com.binaryunit.aodled.render.SNAKE_DARK_MS
import com.binaryunit.aodled.render.SNAKE_ERASE_MS
import com.binaryunit.aodled.render.SNAKE_GROW_MS
import com.binaryunit.aodled.render.SNAKE_PERIOD_MS
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
    @Test fun `grows from nothing to full with easing`() {
        assertEquals(SnakeSpan(0f, 0f), snakeSpan(0))
        assertEquals(0.5f, snakeSpan(SNAKE_GROW_MS / 2).head, 0.01f)        // ease-in-out is symmetric
        assertTrue(snakeSpan(SNAKE_GROW_MS / 4).head < 0.25f)                // slow start
        assertEquals(SnakeSpan(0f, 1f), snakeSpan(SNAKE_GROW_MS))
    }

    @Test fun `erases from the start towards the end`() {
        val mid = snakeSpan(SNAKE_GROW_MS + SNAKE_ERASE_MS / 2)
        assertEquals(1f, mid.head, 0f)
        assertEquals(0.5f, mid.tail, 0.01f)
    }

    @Test fun `dark pause draws nothing, then wraps`() {
        val dark = snakeSpan(SNAKE_GROW_MS + SNAKE_ERASE_MS + SNAKE_DARK_MS / 2)
        assertEquals(dark.head, dark.tail, 0f)
        assertEquals(SnakeSpan(0f, 0f), snakeSpan(SNAKE_PERIOD_MS))
    }

    @Test fun `cycle is about the swirl's`() = assertEquals(1500L, SNAKE_PERIOD_MS)

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
