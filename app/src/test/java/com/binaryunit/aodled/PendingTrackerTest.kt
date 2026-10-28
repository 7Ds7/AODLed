package com.binaryunit.aodled

import com.binaryunit.aodled.config.LightOff.ON_DISMISS
import com.binaryunit.aodled.config.LightOff.ON_UNLOCK
import com.binaryunit.aodled.notify.NotifInfo
import com.binaryunit.aodled.notify.PendingTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingTrackerTest {
    private val t = PendingTracker()
    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private fun n(key: String, post: Long, whenMillis: Long, colour: Int) = NotifInfo(key, post, whenMillis, colour)

    @Test fun `newest wins, falls back when removed, empty is null`() {
        t.posted(n("a", 1, 1, red)); t.posted(n("b", 2, 2, green))
        assertEquals("b", t.current(ON_DISMISS)?.key)
        t.removed("b")
        assertEquals("a", t.current(ON_DISMISS)?.key)
        t.removed("a")
        assertNull(t.current(ON_DISMISS))
    }

    @Test fun `update with changed when is new`() {
        t.posted(n("a", 1, 1, red)); t.posted(n("b", 2, 2, green)); t.posted(n("a", 3, 3, red))
        assertEquals("a", t.current(ON_DISMISS)?.key)
    }

    @Test fun `silent repost keeps old alertAt`() {
        t.posted(n("a", 1, 1, red)); t.posted(n("b", 2, 2, green)); t.posted(n("a", 3, 1, red))
        assertEquals("b", t.current(ON_DISMISS)?.key)
    }

    @Test fun `repost updates the stored colour`() {
        t.posted(n("a", 1, 1, red)); t.posted(n("a", 2, 1, green))
        assertEquals(green, t.current(ON_DISMISS)?.colour)
    }

    @Test fun `unlock hides pending until something new`() {
        t.posted(n("a", 1, 1, red)); t.markSeen(10)
        assertNull(t.current(ON_UNLOCK))
        assertEquals("a", t.current(ON_DISMISS)?.key)
        t.posted(n("a", 11, 11, red))
        assertEquals("a", t.current(ON_UNLOCK)?.key)
    }

    @Test fun `reset keeps alertAt for known keys`() {
        t.posted(n("a", 1, 1, red)); t.markSeen(10)
        t.reset(listOf(n("a", 12, 1, red)))                // repost, same when
        assertNull(t.current(ON_UNLOCK))
    }

    @Test fun `reset drops missing keys`() {
        t.posted(n("a", 1, 1, red)); t.reset(emptyList())
        assertNull(t.current(ON_DISMISS))
    }

    @Test fun `reset adds unknown keys as new`() {
        t.markSeen(10); t.reset(listOf(n("a", 12, 12, red)))
        assertEquals("a", t.current(ON_UNLOCK)?.key)
    }

    // The return value says whether the light changed, so unrelated notification traffic doesn't refresh it

    @Test fun `first notification changes the light`() {
        assertTrue(t.posted(n("a", 1, 1, red)))
    }

    @Test fun `removing a key that never qualified changes nothing`() {
        t.posted(n("a", 1, 1, red))
        assertFalse(t.removed("music"))
    }

    @Test fun `silent repost changes nothing`() {
        t.posted(n("a", 1, 1, red))
        assertFalse(t.posted(n("a", 2, 1, red)))
    }

    @Test fun `newer notification in the same colour changes nothing`() {
        t.posted(n("a", 1, 1, red))
        assertFalse(t.posted(n("b", 2, 2, red)))
    }

    @Test fun `newer notification in another colour changes the light`() {
        t.posted(n("a", 1, 1, red))
        assertTrue(t.posted(n("b", 2, 2, green)))
    }

    @Test fun `same colour after unlock changes the light`() {
        t.posted(n("a", 1, 1, red)); t.markSeen(10)
        assertTrue(t.posted(n("b", 11, 11, red)))           // ON_UNLOCK goes from off to red
    }

    @Test fun `removing the shown notification changes the light`() {
        t.posted(n("a", 1, 1, red)); t.posted(n("b", 2, 2, green))
        assertTrue(t.removed("b"))
    }

    @Test fun `reset with the same notifications changes nothing`() {
        t.posted(n("a", 1, 1, red))
        assertFalse(t.reset(listOf(n("a", 5, 1, red))))
    }

    @Test fun `reset that drops the shown notification changes the light`() {
        t.posted(n("a", 1, 1, red))
        assertTrue(t.reset(emptyList()))
    }
}
