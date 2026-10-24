package com.binaryunit.aodled

import com.binaryunit.aodled.config.Animation
import com.binaryunit.aodled.config.Style
import com.binaryunit.aodled.render.PULSE_DARK_MS
import com.binaryunit.aodled.render.PULSE_PERIOD_MS
import com.binaryunit.aodled.render.ROTATE_PERIOD_MS
import com.binaryunit.aodled.render.effectiveAnimation
import com.binaryunit.aodled.render.glowAlpha
import com.binaryunit.aodled.render.glowRadius
import com.binaryunit.aodled.render.pulseAlpha
import com.binaryunit.aodled.render.rotationAngle
import org.junit.Assert.assertEquals
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

    @Test fun `rotation wraps`() {
        assertEquals(0f, rotationAngle(0), 0.001f)
        assertEquals(180f, rotationAngle(ROTATE_PERIOD_MS / 2), 0.001f)
        assertEquals(0f, rotationAngle(ROTATE_PERIOD_MS), 0.001f)
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
}
