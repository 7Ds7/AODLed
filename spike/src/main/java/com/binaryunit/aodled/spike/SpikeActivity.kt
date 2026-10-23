package com.binaryunit.aodled.spike

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

class SpikeActivity : Activity() {
    private val choices = listOf("Keep-alive off" to 0L, "Keep-alive 30 s" to 30_000L, "Keep-alive 5 s" to 5_000L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(SpikeService.PREFS, MODE_PRIVATE)
        val current = prefs.getLong(SpikeService.KEY_KEEP_ALIVE, 0)
        val group = RadioGroup(this)
        choices.forEachIndexed { i, (label, ms) ->
            group.addView(RadioButton(this).apply {
                id = i + 1
                text = label
                isChecked = ms == current
            })
        }
        group.setOnCheckedChangeListener { _, id ->
            prefs.edit().putLong(SpikeService.KEY_KEEP_ALIVE, choices[id - 1].second).apply()
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 160, 48, 48)
            addView(TextView(this@SpikeActivity).apply {
                text = "Enable 'AODLed spike' in Settings → Accessibility, then lock the phone. " +
                    "A changed keep-alive applies from the next screen-off."
            })
            addView(group)
        })
    }
}
