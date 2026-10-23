package com.binaryunit.aodled.service

import android.os.Handler
import android.os.Looper
import com.binaryunit.aodled.notify.PendingTracker

/** Process-wide state shared by the listener and overlay services. Main thread only. */
object AppState {
    val tracker = PendingTracker()

    /** Set by the listener while connected: rebuilds [tracker] from the active notifications. */
    var rebuild: (() -> Unit)? = null

    private val listeners = mutableListOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(l: () -> Unit) {
        listeners += l
    }

    fun removeListener(l: () -> Unit) {
        listeners -= l
    }

    fun changed() {
        main.post { listeners.toList().forEach { it() } }
    }
}
