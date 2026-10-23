package com.binaryunit.aodled.service

import android.content.Context
import android.os.PowerManager

private const val DRAW_WAKE_LOCK = 0x00000080 // hidden PowerManager level; lets a frame reach the panel in doze
private const val UNIMPORTANT_FOR_LOGGING = 0x40000000

/** Short wake locks around each redraw while the display dozes. */
class DozeWake(context: Context) {
    private val pm = context.getSystemService(PowerManager::class.java)
    private val cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aodled:cpu").apply { setReferenceCounted(false) }
    private val draw = pm.newWakeLock(DRAW_WAKE_LOCK or UNIMPORTANT_FOR_LOGGING, "aodled:draw").apply { setReferenceCounted(false) }

    fun poke(ms: Long = 250) {
        cpu.acquire(ms)
        draw.acquire(ms)
    }
}
