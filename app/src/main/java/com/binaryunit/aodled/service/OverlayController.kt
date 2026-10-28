package com.binaryunit.aodled.service

import com.binaryunit.aodled.config.BurnIn
import com.binaryunit.aodled.config.Offset
import com.binaryunit.aodled.render.ANIM_FPS
import kotlin.random.Random

const val LINGER_MS = 100L

interface Scheduler {
    fun post(task: Runnable, delayMs: Long)
    fun cancel(task: Runnable)
}

/** What a draw put on screen, which decides the loops that keep running. */
enum class Light { OFF, STILL, ANIMATED }

/**
 * When the cover goes up and down, when the light shifts and when animation frames run; no Android in here.
 * Doze spike (2026-10-23): a once-drawn frame held for 10 min unplugged, so nothing redraws a still light.
 */
class OverlayController(
    private val host: Host,
    private val scheduler: Scheduler,
    private val nextOffset: () -> Offset = { BurnIn.next(Random) },
) {
    interface Host {
        fun coverUp()
        fun coverDown()
        fun draw(offset: Offset): Light
        fun frame()
    }

    private var covered = false
    private var offset = Offset(0, 0)
    private var shiftArmed = false

    private val hide = Runnable {
        stopLoops()
        if (covered) host.coverDown()
        covered = false
    }

    private val burnIn = object : Runnable {
        override fun run() {
            shiftArmed = false
            offset = nextOffset()
            refresh()
        }
    }

    private val frame = object : Runnable {
        override fun run() {
            host.frame()
            scheduler.post(this, 1000L / ANIM_FPS)
        }
    }

    /** The cover is up exactly while the device is not interactive, lingering [LINGER_MS] after it wakes. */
    fun screen(interactive: Boolean) {
        scheduler.cancel(hide)
        if (interactive) {
            scheduler.post(hide, LINGER_MS)
        } else {
            if (!covered) {
                host.coverUp()
                covered = true
                offset = nextOffset()
            }
            refresh()
        }
    }

    /** Redraws and (re)schedules the loops. No-op while the cover is down. */
    fun refresh() {
        if (!covered) return
        val light = host.draw(offset)
        scheduler.cancel(frame)
        if (light == Light.OFF) {
            stopLoops()
            return
        }
        // A running shift timer is left alone: refreshing must not postpone the next shift
        if (!shiftArmed) {
            scheduler.post(burnIn, BurnIn.INTERVAL_MS)
            shiftArmed = true
        }
        if (light == Light.ANIMATED) scheduler.post(frame, 0)
    }

    fun stop() {
        scheduler.cancel(hide)
        hide.run()
    }

    private fun stopLoops() {
        scheduler.cancel(burnIn)
        shiftArmed = false
        scheduler.cancel(frame)
    }
}
