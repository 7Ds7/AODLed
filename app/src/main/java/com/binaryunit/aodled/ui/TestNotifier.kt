package com.binaryunit.aodled.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.binaryunit.aodled.R
import com.binaryunit.aodled.notify.TEST_CHANNEL_ID

/** Posts one dismissable notification that lights the LED, for checking the setup. */
object TestNotifier {
    private const val ID = 1

    fun post(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(TEST_CHANNEL_ID, c.getString(R.string.test_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.notify(
            ID,
            Notification.Builder(c, TEST_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_ring)
                .setContentTitle(c.getString(R.string.test_title))
                .setContentText(c.getString(R.string.test_text))
                .setAutoCancel(true)
                .build()
        )
    }
}
