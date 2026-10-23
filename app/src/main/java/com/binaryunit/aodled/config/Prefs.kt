package com.binaryunit.aodled.config

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("aodled", Context.MODE_PRIVATE)
    // SharedPreferences holds listeners weakly, so keep the wrappers alive here
    private val listeners = mutableMapOf<() -> Unit, SharedPreferences.OnSharedPreferenceChangeListener>()

    fun load(): LightConfig {
        val d = LightConfig()
        return LightConfig(
            style = enumOr(KEY_STYLE, d.style),
            animation = enumOr(KEY_ANIMATION, d.animation),
            colourMode = enumOr(KEY_COLOUR_MODE, d.colourMode),
            fixedColour = prefs.getInt(KEY_FIXED_COLOUR, d.fixedColour),
            lightOff = enumOr(KEY_LIGHT_OFF, d.lightOff),
            ring = if (prefs.contains(KEY_RING_CX)) RingPlacement(
                prefs.getFloat(KEY_RING_CX, 0f), prefs.getFloat(KEY_RING_CY, 0f), prefs.getFloat(KEY_RING_R, 0f)
            ) else null,
            dot = if (prefs.contains(KEY_DOT_X)) DotPlacement(
                prefs.getFloat(KEY_DOT_X, 0f), prefs.getFloat(KEY_DOT_Y, 0f), prefs.getFloat(KEY_DOT_SIZE, DOT_DEFAULT)
            ) else null,
            edgeThickness = prefs.getFloat(KEY_EDGE, d.edgeThickness),
        )
    }

    fun save(c: LightConfig) {
        prefs.edit().apply {
            putString(KEY_STYLE, c.style.name)
            putString(KEY_ANIMATION, c.animation.name)
            putString(KEY_COLOUR_MODE, c.colourMode.name)
            putInt(KEY_FIXED_COLOUR, c.fixedColour)
            putString(KEY_LIGHT_OFF, c.lightOff.name)
            if (c.ring != null) {
                putFloat(KEY_RING_CX, c.ring.cx); putFloat(KEY_RING_CY, c.ring.cy); putFloat(KEY_RING_R, c.ring.radius)
            } else {
                remove(KEY_RING_CX); remove(KEY_RING_CY); remove(KEY_RING_R)
            }
            if (c.dot != null) {
                putFloat(KEY_DOT_X, c.dot.x); putFloat(KEY_DOT_Y, c.dot.y); putFloat(KEY_DOT_SIZE, c.dot.size)
            } else {
                remove(KEY_DOT_X); remove(KEY_DOT_Y); remove(KEY_DOT_SIZE)
            }
            putFloat(KEY_EDGE, c.edgeThickness)
        }.apply()
    }

    fun onChange(cb: () -> Unit) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> cb() }
        listeners[cb] = l
        prefs.registerOnSharedPreferenceChangeListener(l)
    }

    fun removeOnChange(cb: () -> Unit) {
        listeners.remove(cb)?.let { prefs.unregisterOnSharedPreferenceChangeListener(it) }
    }

    private inline fun <reified T : Enum<T>> enumOr(key: String, default: T): T =
        prefs.getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    private companion object {
        const val KEY_STYLE = "style"
        const val KEY_ANIMATION = "animation"
        const val KEY_COLOUR_MODE = "colour_mode"
        const val KEY_FIXED_COLOUR = "fixed_colour"
        const val KEY_LIGHT_OFF = "light_off"
        const val KEY_RING_CX = "ring_cx"
        const val KEY_RING_CY = "ring_cy"
        const val KEY_RING_R = "ring_r"
        const val KEY_DOT_X = "dot_x"
        const val KEY_DOT_Y = "dot_y"
        const val KEY_DOT_SIZE = "dot_size"
        const val KEY_EDGE = "edge_thickness"
    }
}
