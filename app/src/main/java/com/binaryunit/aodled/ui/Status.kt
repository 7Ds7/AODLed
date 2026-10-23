package com.binaryunit.aodled.ui

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import com.binaryunit.aodled.service.LightListenerService
import com.binaryunit.aodled.service.OverlayService

/** What AODLed needs from the system, for the status rows on the settings page. */
object Status {
    fun listenerGranted(c: Context): Boolean = c.getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(ComponentName(c, LightListenerService::class.java))

    fun accessibilityEnabled(c: Context): Boolean {
        val enabled = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val ours = ComponentName(c, OverlayService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    fun batteryUnrestricted(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    /** System always-on display; null when the setting can't be read. */
    fun aodEnabled(c: Context): Boolean? = try {
        when (Settings.Secure.getInt(c.contentResolver, "doze_always_on", -1)) {
            1 -> true
            0 -> false
            else -> null
        }
    } catch (e: SecurityException) {
        null
    }
}
