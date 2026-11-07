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
            enabled = prefs.getBoolean(KEY_ENABLED, d.enabled),
            style = enumOr(KEY_STYLE, d.style),
            animation = enumOr(KEY_ANIMATION, d.animation),
            // A restored or edited file can hold values the controls never write; these would crash the pulse or editor
            pulse = Pulse(
                prefs.getLong(KEY_PULSE_PERIOD, d.pulse.periodMs).coerceAtLeast(PULSE_PERIODS.first()),
                prefs.getInt(KEY_PULSE_BLINKS, d.pulse.blinks).coerceIn(1, 10),
                prefs.getLong(KEY_PULSE_PAUSE, d.pulse.pauseMs).coerceAtLeast(0),
            ),
            colourMode = enumOr(KEY_COLOUR_MODE, d.colourMode),
            fixedColour = prefs.getInt(KEY_FIXED_COLOUR, d.fixedColour),
            lightOff = enumOr(KEY_LIGHT_OFF, d.lightOff),
            ring = if (prefs.contains(KEY_RING_CX)) RingPlacement(
                prefs.getFloat(KEY_RING_CX, 0f), prefs.getFloat(KEY_RING_CY, 0f), prefs.getFloat(KEY_RING_R, 0f)
            ) else null,
            dot = if (prefs.contains(KEY_DOT_X)) DotPlacement(
                prefs.getFloat(KEY_DOT_X, 0f), prefs.getFloat(KEY_DOT_Y, 0f), prefs.getFloat(KEY_DOT_SIZE, DOT_DEFAULT)
            ) else null,
            cutout = if (prefs.contains(KEY_CUTOUT_CX)) CutoutPlacement(
                prefs.getFloat(KEY_CUTOUT_CX, 0f), prefs.getFloat(KEY_CUTOUT_CY, 0f), prefs.getFloat(KEY_CUTOUT_SCALE, 1f)
            ) else null,
            edgeThickness = prefs.getFloat(KEY_EDGE, d.edgeThickness),
            ringLineScale = lineScale(KEY_RING_LINE, d.ringLineScale),
            cutoutLineScale = lineScale(KEY_CUTOUT_LINE, d.cutoutLineScale),
            respectDnd = prefs.getBoolean(KEY_RESPECT_DND, d.respectDnd),
            includeOtherProfiles = prefs.getBoolean(KEY_OTHER_PROFILES, d.includeOtherProfiles),
            filterMode = enumOr(KEY_FILTER_MODE, d.filterMode),
            // getStringSet returns the stored instance, which must not be kept or modified
            allowList = prefs.getStringSet(KEY_ALLOW_LIST, null)?.toSet() ?: d.allowList,
            ignoreList = prefs.getStringSet(KEY_IGNORE_LIST, null)?.toSet() ?: d.ignoreList,
        )
    }

    fun save(c: LightConfig) {
        prefs.edit().apply {
            putBoolean(KEY_ENABLED, c.enabled)
            putString(KEY_STYLE, c.style.name)
            putString(KEY_ANIMATION, c.animation.name)
            putLong(KEY_PULSE_PERIOD, c.pulse.periodMs)
            putInt(KEY_PULSE_BLINKS, c.pulse.blinks)
            putLong(KEY_PULSE_PAUSE, c.pulse.pauseMs)
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
            if (c.cutout != null) {
                putFloat(KEY_CUTOUT_CX, c.cutout.cx); putFloat(KEY_CUTOUT_CY, c.cutout.cy); putFloat(KEY_CUTOUT_SCALE, c.cutout.scale)
            } else {
                remove(KEY_CUTOUT_CX); remove(KEY_CUTOUT_CY); remove(KEY_CUTOUT_SCALE)
            }
            putFloat(KEY_EDGE, c.edgeThickness)
            putFloat(KEY_RING_LINE, c.ringLineScale)
            putFloat(KEY_CUTOUT_LINE, c.cutoutLineScale)
            putBoolean(KEY_RESPECT_DND, c.respectDnd)
            putBoolean(KEY_OTHER_PROFILES, c.includeOtherProfiles)
            putString(KEY_FILTER_MODE, c.filterMode.name)
            if (c.allowList.isNotEmpty()) putStringSet(KEY_ALLOW_LIST, c.allowList) else remove(KEY_ALLOW_LIST)
            if (c.ignoreList.isNotEmpty()) putStringSet(KEY_IGNORE_LIST, c.ignoreList) else remove(KEY_IGNORE_LIST)
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

    // coerceIn keeps NaN, which the editor's slider also rejects
    private fun lineScale(key: String, default: Float): Float =
        prefs.getFloat(key, default).takeUnless { it.isNaN() }?.coerceIn(LINE_SCALE_MIN, LINE_SCALE_MAX) ?: default

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_STYLE = "style"
        const val KEY_ANIMATION = "animation"
        const val KEY_PULSE_PERIOD = "pulse_period"
        const val KEY_PULSE_BLINKS = "pulse_blinks"
        const val KEY_PULSE_PAUSE = "pulse_pause"
        const val KEY_COLOUR_MODE = "colour_mode"
        const val KEY_FIXED_COLOUR = "fixed_colour"
        const val KEY_LIGHT_OFF = "light_off"
        const val KEY_RING_CX = "ring_cx"
        const val KEY_RING_CY = "ring_cy"
        const val KEY_RING_R = "ring_r"
        const val KEY_DOT_X = "dot_x"
        const val KEY_DOT_Y = "dot_y"
        const val KEY_DOT_SIZE = "dot_size"
        const val KEY_CUTOUT_CX = "cutout_cx"
        const val KEY_CUTOUT_CY = "cutout_cy"
        const val KEY_CUTOUT_SCALE = "cutout_scale"
        const val KEY_EDGE = "edge_thickness"
        const val KEY_RING_LINE = "ring_line_scale"
        const val KEY_CUTOUT_LINE = "cutout_line_scale"
        const val KEY_RESPECT_DND = "respect_dnd"
        const val KEY_OTHER_PROFILES = "include_other_profiles"
        const val KEY_FILTER_MODE = "filter_mode"
        const val KEY_ALLOW_LIST = "allow_list"
        const val KEY_IGNORE_LIST = "ignore_list"
    }
}
