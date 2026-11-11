package com.binaryunit.aodled.service

import com.binaryunit.aodled.config.BurnIn
import com.binaryunit.aodled.config.Offset

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
    /** Real time in ms, deep sleep included (elapsedRealtime); the scheduler's delays count uptime, which stops then. */
    private val now: () -> Long,
    private val nextOffset: () -> Offset,
) {
    interface Host {
        fun coverUp()
        fun coverDown()
        fun draw(offset: Offset): Light
        /** Draws the next animation frame; returns how long to wait before the one after. */
        fun frame(): Long
    }

    private var covered = false
    private var offset = Offset(0, 0)
    private var shiftArmed = false
    private var armedAt = 0L
    private var exactNext = false

    private val hide = Runnable {
        stopLoops()
        if (covered) host.coverDown()
        covered = false
    }

    private val burnIn = Runnable { shift() }

    private val frame = object : Runnable {
        override fun run() {
            scheduler.post(this, host.frame())
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
                offset = if (exactNext) Offset(0, 0) else nextOffset()
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
        exactNext = false
        // A running shift timer is left alone: refreshing must not postpone the next shift
        if (!shiftArmed) {
            scheduler.post(burnIn, BurnIn.INTERVAL_MS)
            shiftArmed = true
            armedAt = now()
        }
        if (light == Light.ANIMATED) scheduler.post(frame, 0)
    }

    /**
     * Once a minute (TIME_TICK). A still light lets the CPU deep sleep, where the shift timer stands still, but the
     * AOD's own clock updates still deliver the tick (Pixel, 2026-11-11), so shift here once its time has passed.
     */
    fun tick() {
        if (shiftArmed && now() - armedAt >= BurnIn.INTERVAL_MS) shift()
    }

    /** The light's next showing is exactly where it was placed, so a new placement can be checked; shifts resume after. */
    fun placementChanged() {
        exactNext = true
    }

    fun stop() {
        scheduler.cancel(hide)
        hide.run()
    }

    private fun shift() {
        scheduler.cancel(burnIn)
        shiftArmed = false
        offset = nextOffset()
        refresh()
    }

    private fun stopLoops() {
        scheduler.cancel(burnIn)
        shiftArmed = false
        scheduler.cancel(frame)
    }
}
