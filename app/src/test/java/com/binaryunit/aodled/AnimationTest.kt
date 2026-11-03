package com.binaryunit.aodled

import com.binaryunit.aodled.config.Animation
import com.binaryunit.aodled.config.Style
import com.binaryunit.aodled.render.PULSE_DARK_MS
import com.binaryunit.aodled.render.PULSE_PERIOD_MS
import com.binaryunit.aodled.render.ROTATE_PERIOD_MS
import com.binaryunit.aodled.render.FRAME_MS
import com.binaryunit.aodled.render.animFrame
import com.binaryunit.aodled.render.animPhase
import com.binaryunit.aodled.render.effectiveAnimation
import com.binaryunit.aodled.render.framesPer
import com.binaryunit.aodled.render.glowAlpha
import com.binaryunit.aodled.render.glowRadius
import com.binaryunit.aodled.render.pulseAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationTest {
    @Test fun `dot motion becomes pulse`() {
        assertEquals(Animation.PULSE, effectiveAnimation(Style.DOT, Animation.MOTION))
        assertEquals(Animation.MOTION, effectiveAnimation(Style.RING, Animation.MOTION))
        assertEquals(Animation.MOTION, effectiveAnimation(Style.EDGES, Animation.MOTION))
        assertEquals(Animation.OFF, effectiveAnimation(Style.DOT, Animation.OFF))
    }

    @Test fun `pulse fades fully out, holds black, fades back`() {
        val fade = (PULSE_PERIOD_MS - PULSE_DARK_MS) / 2
        assertEquals(1f, pulseAlpha(0), 0.001f)
        assertEquals(0f, pulseAlpha(fade), 0.001f)
        assertEquals(0f, pulseAlpha(fade + PULSE_DARK_MS / 2), 0.001f)
        assertEquals(0f, pulseAlpha(fade + PULSE_DARK_MS - 1), 0.01f)
        assertEquals(1f, pulseAlpha(PULSE_PERIOD_MS), 0.001f)
    }

    @Test fun `frame counts whole frames and wraps after a lap`() {
        val frames = framesPer(ROTATE_PERIOD_MS)
        assertEquals(60, frames)                                               // the comet's 12 segments are a fifth
        assertEquals(0, animFrame(0, ROTATE_PERIOD_MS))
        assertEquals(0, animFrame(FRAME_MS - 1, ROTATE_PERIOD_MS))
        assertEquals(1, animFrame(FRAME_MS, ROTATE_PERIOD_MS))
        assertEquals(frames - 1, animFrame(frames * FRAME_MS - 1, ROTATE_PERIOD_MS))
        assertEquals(0, animFrame(frames * FRAME_MS, ROTATE_PERIOD_MS))
    }

    @Test fun `glow scales with thickness within bounds`() {
        assertEquals(4f, glowRadius(1f), 0.001f)
        assertEquals(6f, glowRadius(2f), 0.001f)
        assertEquals(24f, glowRadius(20f), 0.001f)
    }

    @Test fun `aod glow is tighter and full strength`() {
        assertEquals(5f, glowRadius(5f, aod = true), 0.001f)
        assertEquals(4f, glowRadius(1f, aod = true), 0.001f)
        assertEquals(1f, glowAlpha(aod = true), 0.001f)
        assertEquals(0.7f, glowAlpha(aod = false), 0.001f)
    }

    @Test fun `rotation is quick`() = assertEquals(true, com.binaryunit.aodled.render.ROTATE_PERIOD_MS <= 2000L)

    @Test fun `phase repeats exactly every cycle`() {
        for (t in 0L until 3000L step 7) assertEquals(animPhase(t, 1500), animPhase(t + 1500 * 41, 1500))
    }

    @Test fun `phase steps by whole frames within the cycle`() {
        val phases = (0L until 1500L).map { animPhase(it, 1500) }.toSet()
        assertTrue(phases.all { it % FRAME_MS == 0L && it < 1500 })
        assertEquals(1500 / FRAME_MS + 1, phases.size.toLong())               // 0, 33, …, 1485: one shape per frame
    }

    @Test fun `phase never runs ahead of the clock`() {
        for (t in 0L until 1500L) assertTrue(animPhase(t, 1500) in t - FRAME_MS + 1..t)
    }
}
