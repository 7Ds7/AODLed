package com.binaryunit.aodled.notify

import android.app.Notification
import android.app.NotificationManager

const val TEST_CHANNEL_ID = "test"

private const val EXCLUDED_FLAGS = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR or
    Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY

/** Whether a notification should light the LED: dismissable, audible-importance, not muted by DND, not ours. */
fun qualifies(flags: Int, importance: Int, intercepted: Boolean, pkg: String, channelId: String, ownPkg: String): Boolean {
    if (flags and EXCLUDED_FLAGS != 0) return false
    if (importance < NotificationManager.IMPORTANCE_DEFAULT) return false
    if (intercepted) return false
    if (pkg == ownPkg && channelId != TEST_CHANNEL_ID) return false
    return true
}
