package com.binaryunit.aodled

import com.binaryunit.aodled.colour.Palette
import com.binaryunit.aodled.colour.appColour
import com.binaryunit.aodled.colour.displayColour
import com.binaryunit.aodled.colour.dominantColour
import com.binaryunit.aodled.colour.ensureVisible
import com.binaryunit.aodled.colour.hexOf
import com.binaryunit.aodled.colour.hueSaturationBrightness
import com.binaryunit.aodled.colour.parseHex
import com.binaryunit.aodled.colour.pickedColour
import com.binaryunit.aodled.colour.visibleOnBlack
import com.binaryunit.aodled.config.ColourMode
import com.binaryunit.aodled.config.LightConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColoursTest {
    @Test fun `brightens app accents`() {
        assertEquals(0xFF2DFF7B.toInt(), ensureVisible(0xFF25D366.toInt()))   // WhatsApp
        assertEquals(0xFFFF493A.toInt(), ensureVisible(0xFFEA4335.toInt()))   // Gmail
    }

    @Test fun `unset or transparent is null`() {
        assertNull(ensureVisible(0))
        assertNull(ensureVisible(0x40FF0000))
    }

    @Test fun `near black becomes white, never black`() {
        assertEquals(Palette.WHITE, ensureVisible(0xFF000000.toInt()))
        assertEquals(Palette.WHITE, ensureVisible(0xFF0A0A0A.toInt()))
    }

    @Test fun `dominant picks majority hue, ignores transparent and grey`() {
        val px = IntArray(100) { 0xFFFF0000.toInt() } + IntArray(50) { 0 } +
            IntArray(80) { 0xFF808080.toInt() } + IntArray(30) { 0xFF0000FF.toInt() }
        assertEquals(0xFFFF0000.toInt(), dominantColour(px))
    }

    @Test fun `all grey icon has no dominant`() = assertNull(dominantColour(IntArray(100) { 0xFFEEEEEE.toInt() }))

    @Test fun `tiny coloured fleck in a grey icon has no dominant`() {
        val px = IntArray(1000) { 0xFF808080.toInt() } + IntArray(10) { 0xFFFF0000.toInt() }
        assertNull(dominantColour(px))
    }

    @Test fun `appColour falls back notification - icon - white`() {
        assertEquals(0xFFFF493A.toInt(), appColour(0xFFEA4335.toInt(), null))
        assertEquals(0xFFFF0000.toInt(), appColour(0, IntArray(10) { 0xFFFF0000.toInt() }))
        assertEquals(Palette.WHITE, appColour(0, null))
    }

    @Test fun `fixed mode overrides`() = assertEquals(
        Palette.ALL[4],
        displayColour(0xFFFF0000.toInt(), LightConfig(colourMode = ColourMode.FIXED, fixedColour = Palette.ALL[4]))
    )

    @Test fun `app mode keeps app colour`() =
        assertEquals(0xFFFF0000.toInt(), displayColour(0xFFFF0000.toInt(), LightConfig()))

    @Test fun `palette has nine colours and no black`() {
        assertEquals(9, Palette.ALL.size)
        assertTrue(Palette.ALL.none { (it and 0xFFFFFF) == 0 })
    }

    // Custom colour: any hue, saturation and brightness, with a warning when it's hard to see on black

    @Test fun `picked colours cover every hue`() {
        assertEquals(0xFFFF0000.toInt(), pickedColour(0f, 1f, 1f))
        assertEquals(0xFFFF8000.toInt(), pickedColour(30f, 1f, 1f))
        assertEquals(0xFFFFFF00.toInt(), pickedColour(60f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), pickedColour(120f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), pickedColour(240f, 1f, 1f))
        assertEquals(0xFFFF0000.toInt(), pickedColour(360f, 1f, 1f))       // wraps round
    }

    @Test fun `less saturation fades to white`() {
        assertEquals(0xFFFF8080.toInt(), pickedColour(0f, 0.5f, 1f))
        assertEquals(0xFFFFFFFF.toInt(), pickedColour(200f, 0f, 1f))
    }

    @Test fun `less brightness darkens down to black`() {
        assertEquals(0xFF008000.toInt(), pickedColour(120f, 1f, 0.5f))
        assertEquals(0xFF808080.toInt(), pickedColour(0f, 0f, 0.5f))
        assertEquals(0xFF000000.toInt(), pickedColour(0f, 1f, 0f))
    }

    @Test fun `bright colours pass the contrast check on black`() {
        assertTrue(visibleOnBlack(0xFFFFFFFF.toInt()))
        assertTrue(visibleOnBlack(0xFFFF0000.toInt()))                     // 5.3:1
        assertTrue(visibleOnBlack(0xFF808080.toInt()))                     // 5.3:1
        assertTrue(Palette.ALL.all { visibleOnBlack(it) })
    }

    @Test fun `dark colours fail the contrast check on black`() {
        assertFalse(visibleOnBlack(0xFF0000FF.toInt()))                    // 2.4:1
        assertFalse(visibleOnBlack(0xFF404040.toInt()))                    // 2.0:1
        assertFalse(visibleOnBlack(0xFF000000.toInt()))
    }

    @Test fun `hex codes parse with or without the hash`() {
        assertEquals(0xFFFF8800.toInt(), parseHex("#FF8800"))
        assertEquals(0xFFFF8800.toInt(), parseHex("ff8800"))
        assertEquals(0xFFFF8800.toInt(), parseHex(" #ff8800 "))
    }

    @Test fun `incomplete or invalid hex is rejected`() {
        assertNull(parseHex("#FF880"))
        assertNull(parseHex("#GG8800"))
        assertNull(parseHex("#FF880011"))
        assertNull(parseHex(""))
        assertNull(parseHex("-FF880"))                                     // a sign isn't a hex digit
    }

    @Test fun `colours show as hex`() {
        assertEquals("#FF8800", hexOf(0xFFFF8800.toInt()))
        assertEquals("#00FF0A", hexOf(0xFF00FF0A.toInt()))
    }

    @Test fun `a saved colour opens the picker where it was`() {
        assertEquals(Triple(240f, 1f, 1f), hueSaturationBrightness(0xFF0000FF.toInt()))
        assertEquals(Triple(0f, 0f, 1f), hueSaturationBrightness(0xFFFFFFFF.toInt()))
        assertEquals(Triple(0f, 0f, 0f), hueSaturationBrightness(0xFF000000.toInt()))
        val (h, s, v) = hueSaturationBrightness(0xFF008000.toInt())
        assertEquals(120f, h, 0.01f)
        assertEquals(1f, s, 0.01f)
        assertEquals(0.5f, v, 0.01f)
    }
}
