package com.binaryunit.aodled.render

// Cutout "snake": a line grows clockwise from the outline's leftmost point until it closes the shape, fast then
// slowing down. For the last 15% of the drawing time the erase already runs behind it, from the start towards
// the end, slow then speeding up; then a dark pause. Lengths are fractions of one contour's length.

const val SNAKE_GROW_MS = 1000L
const val SNAKE_ERASE_MS = 1000L
const val SNAKE_DARK_MS = 300L
const val SNAKE_TRAIL_START_MS = SNAKE_GROW_MS * 85 / 100 // the erase starts at 85% of the drawing time
const val SNAKE_PERIOD_MS = SNAKE_TRAIL_START_MS + SNAKE_ERASE_MS + SNAKE_DARK_MS

/** The lit part of a contour, from [tail] to [head], as fractions of its length. */
data class SnakeSpan(val tail: Float, val head: Float)

fun snakeSpan(tMs: Long): SnakeSpan {
    val t = tMs % SNAKE_PERIOD_MS
    if (t >= SNAKE_TRAIL_START_MS + SNAKE_ERASE_MS) return SnakeSpan(1f, 1f)
    val head = decelerate(minOf(t, SNAKE_GROW_MS).toFloat() / SNAKE_GROW_MS)
    val tail = if (t < SNAKE_TRAIL_START_MS) 0f else accelerate((t - SNAKE_TRAIL_START_MS).toFloat() / SNAKE_ERASE_MS)
    return SnakeSpan(tail, head)
}

// Material's standard decelerate and accelerate curves
private fun decelerate(x: Float) = cubicBezier(0f, 0f, 0.2f, 1f, x)
private fun accelerate(x: Float) = cubicBezier(0.4f, 0f, 1f, 1f, x)

/** Index of the leftmost sample; on a flat left side (within half a pixel) the topmost of those. */
fun snakeStart(xs: FloatArray, ys: FloatArray): Int {
    val minX = xs.min()
    return xs.indices.filter { xs[it] - minX < 0.5f }.minBy { ys[it] }
}

/** Winding as seen on screen (y grows downwards), from the shoelace sum. */
fun isClockwise(xs: FloatArray, ys: FloatArray): Boolean {
    var sum = 0f
    for (i in xs.indices) {
        val j = (i + 1) % xs.size
        sum += xs[i] * ys[j] - xs[j] * ys[i]
    }
    return sum > 0
}

/**
 * Distances (from, to) along a contour of [length] to light for [span], starting at [start] and running clockwise
 * on screen. Not wrapped: either end may fall outside 0..length.
 */
fun snakeRange(start: Float, length: Float, span: SnakeSpan, clockwise: Boolean): Pair<Float, Float> =
    if (clockwise) start + span.tail * length to start + span.head * length
    else start - span.head * length to start - span.tail * length
