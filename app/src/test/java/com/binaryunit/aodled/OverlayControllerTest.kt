package com.binaryunit.aodled

import com.binaryunit.aodled.config.Offset
import com.binaryunit.aodled.service.LINGER_MS
import com.binaryunit.aodled.service.Light
import com.binaryunit.aodled.service.OverlayController
import com.binaryunit.aodled.service.Scheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs posted tasks in simulated time: [advance] fires everything due up to and including the new time. */
private class FakeScheduler : Scheduler {
    private class Pending(val due: Long, val seq: Long, val task: Runnable)

    private val pending = mutableListOf<Pending>()
    private var seq = 0L
    private var now = 0L

    override fun post(task: Runnable, delayMs: Long) {
        pending += Pending(now + delayMs, seq++, task)
    }

    override fun cancel(task: Runnable) {
        pending.removeAll { it.task === task }
    }

    fun advance(ms: Long) {
        val end = now + ms
        while (true) {
            val next = pending.filter { it.due <= end }.minWithOrNull(compareBy({ it.due }, { it.seq })) ?: break
            pending -= next
            now = next.due
            next.task.run()
        }
        now = end
    }
}

private class FakeHost : OverlayController.Host {
    val events = mutableListOf<String>()
    var light = Light.STILL

    override fun coverUp() { events += "up" }
    override fun coverDown() { events += "down" }
    override fun draw(offset: Offset): Light { events += "draw ${offset.dx}"; return light }
    override fun frame() { events += "frame" }
}

class OverlayControllerTest {
    private val host = FakeHost()
    private val clock = FakeScheduler()
    private var offsets = 0
    private val c = OverlayController(host, clock) { Offset(++offsets, 0) }   // offsets 1, 2, 3, …

    private fun draws() = host.events.filter { it.startsWith("draw") }
    private fun frames() = host.events.count { it == "frame" }

    @Test fun `screen off puts the cover up and draws`() {
        c.screen(false)
        assertEquals(listOf("up", "draw 1"), host.events)
    }

    @Test fun `screen on hides after the linger`() {
        c.screen(false); c.screen(true); clock.advance(99)
        assertFalse("down" in host.events)
        clock.advance(1)
        assertEquals("down", host.events.last())
    }

    @Test fun `repeated screen off adds the cover once`() {
        c.screen(false); c.screen(false)
        assertEquals(1, host.events.count { it == "up" })
        assertTrue(draws().all { it == "draw 1" })
    }

    @Test fun `screen off during the linger keeps the cover`() {
        c.screen(false); c.screen(true); clock.advance(50); c.screen(false); clock.advance(1000)
        assertFalse("down" in host.events)
        assertEquals(1, host.events.count { it == "up" })
    }

    @Test fun `refresh while the cover is down draws nothing`() {
        c.refresh()
        assertTrue(host.events.isEmpty())
    }

    @Test fun `still light runs no frames`() {
        host.light = Light.STILL; c.screen(false); clock.advance(1000)
        assertEquals(0, frames())
    }

    @Test fun `animated light runs frames until the cover goes`() {
        host.light = Light.ANIMATED; c.screen(false); clock.advance(1000)
        assertTrue(frames() > 0)
        c.screen(true); clock.advance(LINGER_MS)
        val before = frames()
        clock.advance(1000)
        assertEquals(before, frames())
    }

    @Test fun `untouched light shifts after 3 minutes`() {
        host.light = Light.STILL; c.screen(false); clock.advance(179_999)
        assertEquals("draw 1", draws().last())
        clock.advance(1)
        assertEquals("draw 2", draws().last())
    }

    @Test fun `no shift while nothing is lit`() {
        host.light = Light.OFF; c.screen(false); clock.advance(600_000)
        assertEquals(listOf("draw 1"), draws())
    }

    @Test fun `light turning off stops frames and shifts`() {
        host.light = Light.ANIMATED; c.screen(false); clock.advance(1000)
        host.light = Light.OFF; c.refresh()
        val before = host.events.toList()
        clock.advance(600_000)
        assertEquals(before, host.events)
    }

    @Test fun `stop takes the cover down and cancels everything`() {
        host.light = Light.ANIMATED; c.screen(false); clock.advance(100)
        c.stop()
        assertEquals("down", host.events.last())
        val before = host.events.toList()
        clock.advance(600_000)
        assertEquals(before, host.events)
    }
}
