package com.binaryunit.aodled.notify

import android.app.Notification
import android.app.NotificationManager

const val TEST_CHANNEL_ID = "test"

private const val EXCLUDED_FLAGS = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR or
    Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY

/**
 * Whether a notification should light the LED: dismissable, audible-importance, app not suspended, not ours,
 * and not muted by DND when [respectDnd]. The test always lights, so it proves the setup even under DND.
 */
fun qualifies(
    flags: Int, importance: Int, suspended: Boolean, dndBlocked: Boolean, respectDnd: Boolean,
    pkg: String, channelId: String, ownPkg: String,
): Boolean {
    if (flags and EXCLUDED_FLAGS != 0) return false
    if (importance < NotificationManager.IMPORTANCE_DEFAULT) return false
    if (suspended) return false
    if (pkg == ownPkg) return channelId == TEST_CHANNEL_ID
    if (dndBlocked && respectDnd) return false
    return true
}
