package com.binaryunit.aodled.config

import android.content.Context

/**
 * Packages whose notifications would light the LED, so the app filter can list apps it can't query: other
 * profiles (Private Space) and apps without a launcher icon. Package names only. Its own file, so recording
 * one doesn't wake the [Prefs] listeners.
 */
class SeenApps(context: Context) {
    private val prefs = context.getSharedPreferences("seen_apps", Context.MODE_PRIVATE)

    /** From this profile (true) or another one (false). */
    fun get(own: Boolean): Set<String> = prefs.getStringSet(key(own), null)?.toSet() ?: emptySet()

    fun record(pkg: String, own: Boolean) {
        val seen = prefs.getStringSet(key(own), null) ?: emptySet()
        if (pkg !in seen) prefs.edit().putStringSet(key(own), seen + pkg).apply()
    }

    fun forget(own: Boolean) = prefs.edit().remove(key(own)).apply()

    private fun key(own: Boolean) = if (own) "own" else "other"
}
