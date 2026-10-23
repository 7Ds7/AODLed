package com.binaryunit.aodled.notify

import com.binaryunit.aodled.config.LightOff

/** [colour] is the resolved app colour, never black. */
data class NotifInfo(val key: String, val postTime: Long, val whenMillis: Long, val colour: Int)

/**
 * Pending qualifying notifications. Each key carries an alertAt: when it was first posted, or reposted
 * with a changed `when` (a new message in an existing conversation). Silent reposts keep the old alertAt.
 */
class PendingTracker {
    private class Entry(val info: NotifInfo, val alertAt: Long)

    private val entries = HashMap<String, Entry>()
    private var seenAt = 0L

    fun reset(all: List<NotifInfo>) {
        val old = HashMap(entries)
        entries.clear()
        all.forEach { entries[it.key] = entryFor(it, old[it.key]) }
    }

    fun posted(n: NotifInfo) {
        entries[n.key] = entryFor(n, entries[n.key])
    }

    fun removed(key: String) {
        entries.remove(key)
    }

    fun markSeen(nowMillis: Long) {
        seenAt = nowMillis
    }

    fun current(mode: LightOff): NotifInfo? = entries.values
        .filter { mode == LightOff.ON_DISMISS || it.alertAt > seenAt }
        .maxByOrNull { it.alertAt }
        ?.info

    private fun entryFor(n: NotifInfo, previous: Entry?): Entry =
        if (previous == null || previous.info.whenMillis != n.whenMillis) Entry(n, n.postTime)
        else Entry(n, previous.alertAt)
}
