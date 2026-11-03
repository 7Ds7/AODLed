package com.binaryunit.aodled

import android.app.Notification.FLAG_FOREGROUND_SERVICE
import android.app.Notification.FLAG_GROUP_SUMMARY
import android.app.Notification.FLAG_NO_CLEAR
import android.app.Notification.FLAG_ONGOING_EVENT
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.NotificationManager.IMPORTANCE_LOW
import com.binaryunit.aodled.notify.TEST_CHANNEL_ID
import com.binaryunit.aodled.notify.qualifies
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualifyTest {
    private val own = "com.binaryunit.aodled"

    private fun q(
        flags: Int = 0, importance: Int = IMPORTANCE_DEFAULT, suspended: Boolean = false, dndBlocked: Boolean = false,
        respectDnd: Boolean = true, pkg: String = "com.whatsapp", channelId: String = "msg",
    ) = qualifies(flags, importance, suspended, dndBlocked, respectDnd, pkg, channelId, own)

    @Test fun `normal default-importance notification qualifies`() = assertTrue(q())

    @Test fun `ongoing, no-clear, fgs, summary excluded`() =
        listOf(FLAG_ONGOING_EVENT, FLAG_NO_CLEAR, FLAG_FOREGROUND_SERVICE, FLAG_GROUP_SUMMARY)
            .forEach { assertFalse(q(flags = it, importance = IMPORTANCE_HIGH)) }

    @Test fun `silent excluded`() = assertFalse(q(importance = IMPORTANCE_LOW))

    @Test fun `dnd blocked excluded when respecting dnd`() = assertFalse(q(importance = IMPORTANCE_HIGH, dndBlocked = true))

    @Test fun `dnd blocked lights when not respecting dnd`() = assertTrue(q(dndBlocked = true, respectDnd = false))

    @Test fun `silent still excluded when not respecting dnd`() =
        assertFalse(q(importance = IMPORTANCE_LOW, dndBlocked = true, respectDnd = false))

    @Test fun `suspended excluded either way`() {
        assertFalse(q(suspended = true))
        assertFalse(q(suspended = true, respectDnd = false))
    }

    @Test fun `test notification ignores dnd`() =
        assertTrue(q(dndBlocked = true, pkg = own, channelId = TEST_CHANNEL_ID))

    @Test fun `own notifications only on test channel`() {
        assertTrue(q(pkg = own, channelId = TEST_CHANNEL_ID))
        assertFalse(q(pkg = own, channelId = "other"))
        assertFalse(q(pkg = own, channelId = "other", dndBlocked = true, respectDnd = false))
    }
}
