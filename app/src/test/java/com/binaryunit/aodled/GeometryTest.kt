package com.binaryunit.aodled

import com.binaryunit.aodled.config.Box
import com.binaryunit.aodled.config.CutoutPlacement
import com.binaryunit.aodled.config.DOT_MAX
import com.binaryunit.aodled.config.DOT_MIN
import com.binaryunit.aodled.config.DotPlacement
import com.binaryunit.aodled.config.EDGE_MAX
import com.binaryunit.aodled.config.EDGE_MIN
import com.binaryunit.aodled.config.halfSize
import com.binaryunit.aodled.config.RING_MAX_RADIUS
import com.binaryunit.aodled.config.RING_MIN_RADIUS
import com.binaryunit.aodled.config.RingPlacement
import com.binaryunit.aodled.config.defaultCutout
import com.binaryunit.aodled.config.defaultDot
import com.binaryunit.aodled.config.defaultRing
import com.binaryunit.aodled.config.defaultBurst
import com.binaryunit.aodled.config.defaultCircle
import com.binaryunit.aodled.config.circle
import com.binaryunit.aodled.config.circleLineScale
import com.binaryunit.aodled.config.withCircle
import com.binaryunit.aodled.config.LightConfig
import com.binaryunit.aodled.config.Style
import com.binaryunit.aodled.config.grownBy
import com.binaryunit.aodled.config.moved
import com.binaryunit.aodled.config.pinched
import com.binaryunit.aodled.config.pinchedEdge
import com.binaryunit.aodled.config.placementDiffers
import com.binaryunit.aodled.config.resetPlacement
import com.binaryunit.aodled.config.resized
import com.binaryunit.aodled.config.resizedEdge
import com.binaryunit.aodled.config.lineThickness
import com.binaryunit.aodled.config.ringThickness
import org.junit.Assert.assertEquals
import org.junit.Test

class GeometryTest {
    private val cutout = Box(500f, 20f, 580f, 100f)

    @Test fun `default ring centres on cutout with gap`() =
        assertEquals(RingPlacement(540f, 60f, 45f), defaultRing(cutout, 1080))   // 80/2 + RING_GAP

    @Test fun `default ring without cutout is top centre`() =
        assertEquals(540f, defaultRing(null, 1080).cx)

    @Test fun `default burst centres on cutout with its line starting on the hole's edge`() {
        val b = defaultBurst(cutout, 1080)
        assertEquals(540f, b.cx); assertEquals(60f, b.cy)
        assertEquals(40f, b.radius - ringThickness(b.radius) / 2, 0.001f)
    }

    @Test fun `every circle style starts around the real hole, never Android's cutout rectangle`() {
        // Android's cutout rectangle runs up to the screen's top edge; the hole's outline is the real circle
        val hole = Box(506f, 35f, 574f, 103f)
        assertEquals(defaultRing(hole, 1080), defaultCircle(Style.RING, hole, 1080))
        assertEquals(defaultRing(null, 1080), defaultCircle(Style.RING, null, 1080))
        assertEquals(defaultBurst(hole, 1080), defaultCircle(Style.RIPPLE, hole, 1080))
        assertEquals(defaultBurst(hole, 1080), defaultCircle(Style.RADAR, hole, 1080))
        // Never the rectangle: without an outline they start top centre
        assertEquals(defaultBurst(null, 1080), defaultCircle(Style.RADAR, null, 1080))
    }

    @Test fun `default dot sits below cutout at 10px`() =
        assertEquals(DotPlacement(540f, 124f, 10f), defaultDot(cutout, 1080))

    @Test fun `default edges are thin`() = assertEquals(2f, com.binaryunit.aodled.config.LightConfig().edgeThickness)

    @Test fun `default dot can still pinch down to 1px`() =
        assertEquals(DOT_MIN, defaultDot(cutout, 1080).pinched(0.1f).size)

    @Test fun `ring pinch clamps`() {
        assertEquals(RING_MAX_RADIUS, RingPlacement(0f, 0f, 200f).pinched(10f).radius)
        assertEquals(RING_MIN_RADIUS, RingPlacement(0f, 0f, 20f).pinched(0.01f).radius)
    }

    @Test fun `ring thickness grows with radius, min 1px`() {
        assertEquals(4f, ringThickness(50f), 0.001f)
        assertEquals(1f, ringThickness(5f), 0.001f)
    }

    @Test fun `line scale multiplies ring thickness, still min 1px`() {
        assertEquals(12f, ringThickness(50f, 3f), 0.001f)
        assertEquals(2f, ringThickness(50f, 0.5f), 0.001f)
        assertEquals(1f, ringThickness(10f, 0.5f), 0.001f)
    }

    @Test fun `dot pinch grows from 1px and clamps`() {
        assertEquals(1.5f, DotPlacement(0f, 0f, 1f).pinched(1.5f).size, 0.001f)
        assertEquals(DOT_MIN, DotPlacement(0f, 0f, 1f).pinched(0.5f).size)
        assertEquals(240f, DotPlacement(0f, 0f, 24f).pinched(10f).size, 0.001f)
        assertEquals(600f, DotPlacement(0f, 0f, 500f).pinched(3f).size)
    }

    @Test fun `edge pinch clamps`() {
        assertEquals(EDGE_MAX, pinchedEdge(20f, 5f))
        assertEquals(EDGE_MIN, pinchedEdge(2f, 0.1f))
    }

    @Test fun `move keeps centre on screen`() =
        assertEquals(RingPlacement(1080f, 0f, 48f), RingPlacement(1000f, 50f, 48f).moved(500f, -500f, 1080, 2424))

    @Test fun `dot move keeps position on screen`() =
        assertEquals(DotPlacement(0f, 2424f, 1f), DotPlacement(10f, 2400f, 1f).moved(-50f, 50f, 1080, 2424))

    private val hole = Box(40f, 39f, 128f, 127f)   // emulator "Punch hole": r44 at (84,83)

    @Test fun `default cutout centres on the hole with the ring gap`() {
        val c = defaultCutout(hole)
        assertEquals(84f, c.cx, 0.001f)
        assertEquals(83f, c.cy, 0.001f)
        assertEquals(52f / 44f, c.scale, 0.001f)                       // 44 + CUTOUT_GAP
    }

    @Test fun `default cutout with two holes sizes by one hole and centres on both`() {
        val c = defaultCutout(Box(400f, 0f, 680f, 2424f), halfSize = 44f)
        assertEquals(540f, c.cx, 0.001f)
        assertEquals(1212f, c.cy, 0.001f)
        assertEquals(52f / 44f, c.scale, 0.001f)
    }

    @Test fun `half size uses the longer side`() = assertEquals(60f, Box(0f, 0f, 120f, 40f).halfSize, 0.001f)

    @Test fun `cutout pinch clamps on the scaled half size`() {
        assertEquals(RING_MAX_RADIUS, 44f * CutoutPlacement(0f, 0f, 1f).pinched(100f, 44f).scale, 0.01f)
        assertEquals(RING_MIN_RADIUS, 44f * CutoutPlacement(0f, 0f, 1f).pinched(0.01f, 44f).scale, 0.01f)
    }

    @Test fun `cutout grown moves its edge by the given px`() {
        val p = CutoutPlacement(0f, 0f, 1.25f)                                   // half size 40 → 50
        assertEquals(55f, 40f * p.grownBy(5f, 40f), 0.001f)
        assertEquals(45f, 40f * p.grownBy(-5f, 40f), 0.001f)
    }

    @Test fun `cutout move keeps centre on screen`() =
        assertEquals(CutoutPlacement(0f, 2424f, 1.2f), CutoutPlacement(10f, 2400f, 1.2f).moved(-50f, 50f, 1080, 2424))

    @Test fun `ring resize steps the radius and clamps`() {
        assertEquals(46f, RingPlacement(0f, 0f, 45f).resized(1f).radius)
        assertEquals(RING_MAX_RADIUS, RingPlacement(0f, 0f, RING_MAX_RADIUS).resized(1f).radius)
        assertEquals(RING_MIN_RADIUS, RingPlacement(0f, 0f, RING_MIN_RADIUS).resized(-1f).radius)
    }

    @Test fun `dot resize steps the size and clamps`() {
        assertEquals(5f, DotPlacement(0f, 0f, 4f).resized(1f).size)
        assertEquals(DOT_MAX, DotPlacement(0f, 0f, DOT_MAX).resized(1f).size)
        assertEquals(DOT_MIN, DotPlacement(0f, 0f, DOT_MIN).resized(-1f).size)
    }

    @Test fun `cutout resize moves its edge and clamps on the scaled half size`() {
        assertEquals(51f, 40f * CutoutPlacement(0f, 0f, 1.25f).resized(1f, 40f).scale, 0.001f)
        assertEquals(RING_MAX_RADIUS, 40f * CutoutPlacement(0f, 0f, RING_MAX_RADIUS / 40f).resized(1f, 40f).scale, 0.001f)
        assertEquals(RING_MIN_RADIUS, 40f * CutoutPlacement(0f, 0f, RING_MIN_RADIUS / 40f).resized(-1f, 40f).scale, 0.001f)
    }

    @Test fun `edge resize steps the thickness and clamps`() {
        assertEquals(3f, resizedEdge(2f, 1f))
        assertEquals(EDGE_MAX, resizedEdge(EDGE_MAX, 1f))
        assertEquals(EDGE_MIN, resizedEdge(EDGE_MIN, -1f))
    }
}

class ResetTest {
    private val placed = com.binaryunit.aodled.config.LightConfig(
        ring = RingPlacement(1f, 2f, 30f), dot = DotPlacement(3f, 4f, 9f), edgeThickness = 12f,
        cutout = CutoutPlacement(5f, 6f, 1.5f), ringLineScale = 2f, cutoutLineScale = 2.5f,
    )

    @Test fun `reset ring clears only the ring`() {
        val r = placed.copy(style = com.binaryunit.aodled.config.Style.RING).resetPlacement()
        assertEquals(null, r.ring); assertEquals(placed.dot, r.dot); assertEquals(12f, r.edgeThickness)
        assertEquals(1f, r.ringLineScale); assertEquals(2.5f, r.cutoutLineScale)
    }

    @Test fun `reset dot clears only the dot`() {
        val r = placed.copy(style = com.binaryunit.aodled.config.Style.DOT).resetPlacement()
        assertEquals(null, r.dot); assertEquals(placed.ring, r.ring)
    }

    @Test fun `reset edges restores default thickness`() {
        val r = placed.copy(style = com.binaryunit.aodled.config.Style.EDGES).resetPlacement()
        assertEquals(com.binaryunit.aodled.config.EDGE_DEFAULT, r.edgeThickness); assertEquals(placed.ring, r.ring)
    }

    @Test fun `reset ripple or radar clears only its own circle`() {
        val both = placed.copy(ripple = RingPlacement(7f, 8f, 20f), rippleLineScale = 1.5f,
            radar = RingPlacement(9f, 10f, 15f), radarLineScale = 0.5f)
        val ripple = both.copy(style = Style.RIPPLE).resetPlacement()
        assertEquals(null, ripple.ripple); assertEquals(1f, ripple.rippleLineScale)
        assertEquals(placed.ring, ripple.ring); assertEquals(2f, ripple.ringLineScale); assertEquals(both.radar, ripple.radar)
        val radar = both.copy(style = Style.RADAR).resetPlacement()
        assertEquals(null, radar.radar); assertEquals(1f, radar.radarLineScale)
        assertEquals(placed.ring, radar.ring); assertEquals(both.ripple, radar.ripple); assertEquals(1.5f, radar.rippleLineScale)
    }

    @Test fun `reset cutout clears only the cutout`() {
        val r = placed.copy(style = com.binaryunit.aodled.config.Style.CUTOUT).resetPlacement()
        assertEquals(null, r.cutout); assertEquals(placed.ring, r.ring); assertEquals(placed.dot, r.dot)
        assertEquals(1f, r.cutoutLineScale); assertEquals(2f, r.ringLineScale)
    }
}

class LineThicknessTest {
    private val ring = RingPlacement(0f, 0f, 50f)
    private val dot = DotPlacement(0f, 0f, 9f)
    private val cutout = CutoutPlacement(0f, 0f, 1.25f)
    private val cfg = com.binaryunit.aodled.config.LightConfig(edgeThickness = 12f, ringLineScale = 2f, cutoutLineScale = 3f)

    private fun of(style: com.binaryunit.aodled.config.Style, c: CutoutPlacement? = cutout) =
        cfg.copy(style = style).lineThickness(ring, dot, c, cutoutHalf = 40f)

    @Test fun `ring uses its radius and scale`() = assertEquals(8f, of(com.binaryunit.aodled.config.Style.RING), 0.001f)
    @Test fun `dot uses its size`() = assertEquals(9f, of(com.binaryunit.aodled.config.Style.DOT), 0.001f)
    @Test fun `edges use their thickness`() = assertEquals(12f, of(com.binaryunit.aodled.config.Style.EDGES), 0.001f)
    @Test fun `cutout uses its scaled half size and scale`() =
        assertEquals(12f, of(com.binaryunit.aodled.config.Style.CUTOUT), 0.001f)    // 40 * 1.25 * 0.08 * 3
    @Test fun `cutout without a hole falls back to the ring`() =
        assertEquals(8f, of(com.binaryunit.aodled.config.Style.CUTOUT, c = null), 0.001f)
    @Test fun `ripple and radar use their own scale`() {
        val c = cfg.copy(rippleLineScale = 3f, radarLineScale = 0.5f)
        assertEquals(12f, c.copy(style = Style.RIPPLE).lineThickness(ring, dot, cutout, 40f), 0.001f)
        assertEquals(2f, c.copy(style = Style.RADAR).lineThickness(ring, dot, cutout, 40f), 0.001f)
    }
}

class CircleTest {
    private val ring = RingPlacement(1f, 2f, 30f)
    private val ripple = RingPlacement(3f, 4f, 20f)
    private val radar = RingPlacement(5f, 6f, 15f)
    private val cfg = LightConfig(ring = ring, ripple = ripple, radar = radar,
        ringLineScale = 2f, rippleLineScale = 1.5f, radarLineScale = 0.5f)

    @Test fun `each circle style reads its own placement and line`() {
        assertEquals(ring, cfg.copy(style = Style.RING).circle()); assertEquals(2f, cfg.copy(style = Style.RING).circleLineScale())
        assertEquals(ripple, cfg.copy(style = Style.RIPPLE).circle()); assertEquals(1.5f, cfg.copy(style = Style.RIPPLE).circleLineScale())
        assertEquals(radar, cfg.copy(style = Style.RADAR).circle()); assertEquals(0.5f, cfg.copy(style = Style.RADAR).circleLineScale())
        // Cutout without a hole is drawn as the ring
        assertEquals(ring, cfg.copy(style = Style.CUTOUT).circle())
    }

    @Test fun `changing one circle style leaves the others alone`() {
        val moved = RingPlacement(9f, 9f, 9f)
        val r = cfg.copy(style = Style.RIPPLE).withCircle(moved, 3f)
        assertEquals(moved, r.ripple); assertEquals(3f, r.rippleLineScale)
        assertEquals(ring, r.ring); assertEquals(2f, r.ringLineScale)
        assertEquals(radar, r.radar); assertEquals(0.5f, r.radarLineScale)
    }
}

class PlacementDiffersTest {
    private val base = com.binaryunit.aodled.config.LightConfig(ring = RingPlacement(1f, 2f, 30f))

    @Test fun `moving, resizing, thickness or style is a new placement`() {
        assertEquals(true, base.copy(ring = RingPlacement(1f, 2f, 31f)).placementDiffers(base))
        assertEquals(true, base.copy(ringLineScale = 2f).placementDiffers(base))
        assertEquals(true, base.copy(ripple = RingPlacement(1f, 2f, 30f)).placementDiffers(base))
        assertEquals(true, base.copy(radarLineScale = 2f).placementDiffers(base))
        assertEquals(true, base.copy(style = com.binaryunit.aodled.config.Style.DOT).placementDiffers(base))
    }

    @Test fun `colour or animation is not`() {
        assertEquals(false, base.copy(fixedColour = 0).placementDiffers(base))
        assertEquals(false, base.copy(animation = com.binaryunit.aodled.config.Animation.PULSE).placementDiffers(base))
    }
}
