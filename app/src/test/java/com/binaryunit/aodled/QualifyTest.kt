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

    @Test fun `normal default-importance notification qualifies`() =
        assertTrue(qualifies(0, IMPORTANCE_DEFAULT, false, "com.whatsapp", "msg", own))

    @Test fun `ongoing, no-clear, fgs, summary excluded`() =
        listOf(FLAG_ONGOING_EVENT, FLAG_NO_CLEAR, FLAG_FOREGROUND_SERVICE, FLAG_GROUP_SUMMARY)
            .forEach { assertFalse(qualifies(it, IMPORTANCE_HIGH, false, "p", "c", own)) }

    @Test fun `silent excluded`() = assertFalse(qualifies(0, IMPORTANCE_LOW, false, "p", "c", own))

    @Test fun `dnd intercepted excluded`() = assertFalse(qualifies(0, IMPORTANCE_HIGH, true, "p", "c", own))

    @Test fun `own notifications only on test channel`() {
        assertTrue(qualifies(0, IMPORTANCE_DEFAULT, false, own, TEST_CHANNEL_ID, own))
        assertFalse(qualifies(0, IMPORTANCE_DEFAULT, false, own, "other", own))
    }
}
