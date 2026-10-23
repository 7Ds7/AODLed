package com.binaryunit.aodled

import com.binaryunit.aodled.config.Box
import com.binaryunit.aodled.config.DOT_MAX
import com.binaryunit.aodled.config.DOT_MIN
import com.binaryunit.aodled.config.DotPlacement
import com.binaryunit.aodled.config.EDGE_MAX
import com.binaryunit.aodled.config.EDGE_MIN
import com.binaryunit.aodled.config.RING_MAX_RADIUS
import com.binaryunit.aodled.config.RING_MIN_RADIUS
import com.binaryunit.aodled.config.RingPlacement
import com.binaryunit.aodled.config.defaultDot
import com.binaryunit.aodled.config.defaultRing
import com.binaryunit.aodled.config.moved
import com.binaryunit.aodled.config.pinched
import com.binaryunit.aodled.config.pinchedEdge
import com.binaryunit.aodled.config.resetPlacement
import com.binaryunit.aodled.config.ringThickness
import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryTest {
    private val cutout = Box(500f, 20f, 580f, 100f)

    @Test fun `default ring centres on cutout with gap`() =
        assertEquals(RingPlacement(540f, 60f, 48f), defaultRing(cutout, 1080))   // 80/2 + RING_GAP

    @Test fun `default ring without cutout is top centre`() =
        assertEquals(540f, defaultRing(null, 1080).cx)

    @Test fun `default dot sits below cutout at 2px`() =
        assertEquals(DotPlacement(540f, 124f, 2f), defaultDot(cutout, 1080))

    @Test fun `default dot can still pinch down to 1px`() =
        assertEquals(DOT_MIN, defaultDot(cutout, 1080).pinched(0.5f).size)

    @Test fun `ring pinch clamps`() {
        assertEquals(RING_MAX_RADIUS, RingPlacement(0f, 0f, 200f).pinched(10f).radius)
        assertEquals(RING_MIN_RADIUS, RingPlacement(0f, 0f, 20f).pinched(0.01f).radius)
    }

    @Test fun `ring thickness grows with radius, min 1px`() {
        assertEquals(4f, ringThickness(50f), 0.001f)
        assertEquals(1f, ringThickness(5f), 0.001f)
    }

    @Test fun `dot pinch grows from 1px and clamps`() {
        assertEquals(1.5f, DotPlacement(0f, 0f, 1f).pinched(1.5f).size, 0.001f)
        assertEquals(DOT_MIN, DotPlacement(0f, 0f, 1f).pinched(0.5f).size)
        assertEquals(DOT_MAX, DotPlacement(0f, 0f, 20f).pinched(3f).size)
    }

    @Test fun `edge pinch clamps`() {
        assertEquals(EDGE_MAX, pinchedEdge(20f, 5f))
        assertEquals(EDGE_MIN, pinchedEdge(2f, 0.1f))
    }

    @Test fun `move keeps centre on screen`() =
        assertEquals(RingPlacement(1080f, 0f, 48f), RingPlacement(1000f, 50f, 48f).moved(500f, -500f, 1080, 2424))

    @Test fun `dot move keeps position on screen`() =
        assertEquals(DotPlacement(0f, 2424f, 1f), DotPlacement(10f, 2400f, 1f).moved(-50f, 50f, 1080, 2424))
}

class ResetTest {
    private val placed = com.binaryunit.aodled.config.LightConfig(
        ring = RingPlacement(1f, 2f, 30f), dot = DotPlacement(3f, 4f, 9f), edgeThickness = 12f,
    )

    @Test fun `reset ring clears only the ring`() {
        val r = placed.copy(style = com.binaryunit.aodled.config.Style.RING).resetPlacement()
        assertEquals(null, r.ring); assertEquals(placed.dot, r.dot); assertEquals(12f, r.edgeThickness)
    }

    @Test fun `reset dot clears only the dot`() {
        val r = placed.copy(style = com.binaryunit.aodled.config.Style.DOT).resetPlacement()
        assertEquals(null, r.dot); assertEquals(placed.ring, r.ring)
    }

    @Test fun `reset edges restores default thickness`() {
        val r = placed.copy(style = com.binaryunit.aodled.config.Style.EDGES).resetPlacement()
        assertEquals(com.binaryunit.aodled.config.EDGE_DEFAULT, r.edgeThickness); assertEquals(placed.ring, r.ring)
    }
}
