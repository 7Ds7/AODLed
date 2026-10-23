package com.binaryunit.aodled.service

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Process
import android.service.notification.StatusBarNotification
import com.binaryunit.aodled.colour.appColour
import com.binaryunit.aodled.colour.ensureVisible

private const val ICON_PX = 48

/**
 * App colour for a notification: its accent colour, else its app icon's dominant colour, else white.
 * Lookups use the notification's own user (Private Space), never ours. Failures are never cached or persisted.
 */
class ColourResolver(private val context: Context) {
    private val iconPixels = HashMap<String, IntArray>()

    fun colourFor(sbn: StatusBarNotification): Int {
        val accent = sbn.notification.color
        return appColour(accent, if (ensureVisible(accent) == null) pixelsFor(sbn) else null)
    }

    private fun pixelsFor(sbn: StatusBarNotification): IntArray? {
        val key = "${sbn.user.hashCode()}/${sbn.packageName}"
        iconPixels[key]?.let { return it }
        // Hidden profiles (Private Space) may refuse non-launcher apps; that just means white
        val pixels = try { loadIcon(sbn)?.let(::toPixels) } catch (e: Exception) { null }
        if (pixels != null) iconPixels[key] = pixels
        return pixels
    }

    private fun loadIcon(sbn: StatusBarNotification): Drawable? {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        launcherApps.getActivityList(sbn.packageName, sbn.user).firstOrNull()?.getIcon(0)?.let { return it }
        if (sbn.user == Process.myUserHandle()) return context.packageManager.getApplicationIcon(sbn.packageName)
        return null
    }

    private fun toPixels(icon: Drawable): IntArray {
        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, ICON_PX, ICON_PX)
        icon.draw(Canvas(bitmap))
        return IntArray(ICON_PX * ICON_PX).also { bitmap.getPixels(it, 0, ICON_PX, 0, 0, ICON_PX, ICON_PX) }
    }
}
