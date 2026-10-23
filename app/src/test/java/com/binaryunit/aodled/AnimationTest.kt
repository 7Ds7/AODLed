package com.binaryunit.aodled

import com.binaryunit.aodled.config.Animation
import com.binaryunit.aodled.config.Style
import com.binaryunit.aodled.render.PULSE_MIN_ALPHA
import com.binaryunit.aodled.render.PULSE_PERIOD_MS
import com.binaryunit.aodled.render.ROTATE_PERIOD_MS
import com.binaryunit.aodled.render.effectiveAnimation
import com.binaryunit.aodled.render.glowRadius
import com.binaryunit.aodled.render.pulseAlpha
import com.binaryunit.aodled.render.rotationAngle
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationTest {
    @Test fun `dot rotation becomes pulse`() {
        assertEquals(Animation.PULSE, effectiveAnimation(Style.DOT, Animation.ROTATE))
        assertEquals(Animation.ROTATE, effectiveAnimation(Style.RING, Animation.ROTATE))
        assertEquals(Animation.ROTATE, effectiveAnimation(Style.EDGES, Animation.ROTATE))
        assertEquals(Animation.OFF, effectiveAnimation(Style.DOT, Animation.OFF))
    }

    @Test fun `pulse range`() {
        assertEquals(1f, pulseAlpha(0), 0.001f)
        assertEquals(PULSE_MIN_ALPHA, pulseAlpha(PULSE_PERIOD_MS / 2), 0.001f)
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

    @Test fun `rotation is quick`() = assertEquals(true, com.binaryunit.aodled.render.ROTATE_PERIOD_MS <= 2000L)
}
