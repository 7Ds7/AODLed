package com.binaryunit.aodled

import com.binaryunit.aodled.colour.Palette
import com.binaryunit.aodled.colour.appColour
import com.binaryunit.aodled.colour.displayColour
import com.binaryunit.aodled.colour.dominantColour
import com.binaryunit.aodled.colour.ensureVisible
import com.binaryunit.aodled.config.ColourMode
import com.binaryunit.aodled.config.LightConfig
import org.junit.Assert.assertEquals
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
}
