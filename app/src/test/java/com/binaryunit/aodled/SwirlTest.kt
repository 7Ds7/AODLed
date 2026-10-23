package com.binaryunit.aodled

import com.binaryunit.aodled.render.SWIRL_BAND_END
import com.binaryunit.aodled.render.SWIRL_BAND_START
import com.binaryunit.aodled.render.SWIRL_PERIOD_MS
import com.binaryunit.aodled.render.cubicBezier
import com.binaryunit.aodled.render.swirlBandOffset
import com.binaryunit.aodled.render.swirlWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwirlTest {
    @Test fun `bezier endpoints and linear case`() {
        assertEquals(0f, cubicBezier(0.33f, 0f, 0.5f, 1f, 0f), 0.001f)
        assertEquals(1f, cubicBezier(0.33f, 0f, 0.5f, 1f, 1f), 0.001f)
        assertEquals(0.3f, cubicBezier(0.3f, 0.3f, 0.7f, 0.7f, 0.3f), 0.01f)
    }

    @Test fun `ease out is ahead at the midpoint`() = assertTrue(cubicBezier(0.33f, 0f, 0.5f, 1f, 0.5f) > 0.6f)

    @Test fun `ring swells fast then thins to nothing`() {
        assertEquals(0f, swirlWidth(0), 0.001f)
        assertEquals(1f, swirlWidth(150), 0.001f)
        assertTrue(swirlWidth(500) in 0.01f..0.99f)
        assertEquals(0f, swirlWidth(900), 0.001f)
        assertEquals(0f, swirlWidth(SWIRL_PERIOD_MS - 1), 0.001f)
    }

    @Test fun `cycle repeats`() = assertEquals(swirlWidth(100), swirlWidth(SWIRL_PERIOD_MS + 100), 0.001f)

    @Test fun `band sweeps left to right`() {
        assertEquals(SWIRL_BAND_START, swirlBandOffset(0), 0.001f)
        assertEquals(SWIRL_BAND_END, swirlBandOffset(SWIRL_PERIOD_MS - 1), 0.05f)
        assertTrue(swirlBandOffset(300) < swirlBandOffset(600))
    }

    @Test fun `swirl pauses dark for at least half a second`() {
        assertTrue(SWIRL_PERIOD_MS - 883 >= 500)
        for (t in 883L until SWIRL_PERIOD_MS step 10) assertEquals(0f, swirlWidth(t), 0.001f)
    }
}
