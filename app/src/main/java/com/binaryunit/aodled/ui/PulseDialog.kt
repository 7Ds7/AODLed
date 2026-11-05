package com.binaryunit.aodled.ui

import android.app.Activity
import android.content.DialogInterface
import android.view.View
import android.widget.TextView
import com.binaryunit.aodled.R
import com.binaryunit.aodled.config.PULSE_PAUSES
import com.binaryunit.aodled.config.PULSE_PERIODS
import com.binaryunit.aodled.config.Pulse
import com.binaryunit.aodled.config.flashesFast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import kotlin.math.abs

/**
 * Blink length, blinks per burst and pause, starting from [current]; Reset puts the sliders back to the original pulse.
 * Done hands them to [onDone], Cancel changes nothing.
 */
fun Activity.showPulse(current: Pulse, onDone: (Pulse) -> Unit) {
    val view = layoutInflater.inflate(R.layout.dialog_pulse, null)
    val speed = view.findViewById<Slider>(R.id.speed)
    val blinks = view.findViewById<Slider>(R.id.blinks)
    val pause = view.findViewById<Slider>(R.id.pause)
    val speedLabel = view.findViewById<TextView>(R.id.speed_label)
    val blinksLabel = view.findViewById<TextView>(R.id.blinks_label)
    val pauseLabel = view.findViewById<TextView>(R.id.pause_label)
    val flashWarning = view.findViewById<View>(R.id.flash_warning)
    // A saved value that's no longer a step falls back to the nearest one
    fun nearest(steps: List<Long>, ms: Long) = steps.indices.minBy { abs(steps[it] - ms) }.toFloat()
    fun set(p: Pulse) {
        speed.value = nearest(PULSE_PERIODS, p.periodMs)
        blinks.value = p.blinks.coerceIn(1, blinks.valueTo.toInt()).toFloat()
        pause.value = nearest(PULSE_PAUSES, p.pauseMs)
    }
    set(current)
    fun picked() = Pulse(PULSE_PERIODS[speed.value.toInt()], blinks.value.toInt(), PULSE_PAUSES[pause.value.toInt()])

    fun show() {
        val p = picked()
        speedLabel.text = getString(R.string.pulse_speed, duration(p.periodMs))
        blinksLabel.text = getString(R.string.pulse_blinks, p.blinks)
        pauseLabel.text = getString(R.string.pulse_pause, if (p.pauseMs == 0L) getString(R.string.pulse_no_pause) else duration(p.pauseMs))
        // Without a pause there are no bursts to count
        blinks.isEnabled = p.pauseMs > 0
        blinksLabel.alpha = if (blinks.isEnabled) 1f else DISABLED_ALPHA
        flashWarning.visibility = if (p.flashesFast()) View.VISIBLE else View.INVISIBLE
    }
    show()
    listOf(speed, blinks, pause).forEach { it.addOnChangeListener { _, _, _ -> show() } }

    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.anim_pulse)
        .setView(view)
        .setPositiveButton(R.string.filter_done) { _, _ -> onDone(picked()) }
        .setNegativeButton(android.R.string.cancel, null)
        .setNeutralButton(R.string.pulse_reset, null)
        .show()
        // Set after show(), or the button would close the dialog
        .getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener { set(Pulse()) }
}

private fun Activity.duration(ms: Long): String = when {
    ms >= 60_000 -> getString(R.string.pulse_minutes, ms / 60_000)
    ms % 1000 == 0L -> getString(R.string.pulse_seconds, (ms / 1000).toString())
    else -> getString(R.string.pulse_seconds, (ms / 1000.0).toString())
}
