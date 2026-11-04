package com.binaryunit.aodled.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.binaryunit.aodled.R
import com.binaryunit.aodled.config.FilterMode
import com.binaryunit.aodled.config.SeenApps
import com.google.android.material.dialog.MaterialAlertDialogBuilder

private class App(val pkg: String, val label: String)

/**
 * [mode]'s list with [checked] ticked: this profile's apps, then other profiles' apps that have notified.
 * Done hands the ticked packages to [onDone]; Cancel changes nothing.
 */
fun Activity.showAppFilter(mode: FilterMode, checked: Set<String>, onDone: (Set<String>) -> Unit) {
    val view = layoutInflater.inflate(R.layout.dialog_app_filter, null)
    view.findViewById<TextView>(R.id.hint).setText(
        if (mode == FilterMode.ALLOW) R.string.filter_allow_hint else R.string.filter_ignore_hint
    )
    val rows = view.findViewById<LinearLayout>(R.id.rows)
    val (own, other) = filterApps(checked)
    fun boxes(apps: List<App>) = apps.map { app ->
        (layoutInflater.inflate(R.layout.item_filter_app, rows, false) as CheckBox).apply {
            text = app.label
            tag = app.pkg
            isChecked = app.pkg in checked
        }
    }
    val ownBoxes = boxes(own)
    val otherBoxes = boxes(other)

    fun section(header: View, title: Int, boxes: List<CheckBox>) {
        header.findViewById<TextView>(R.id.title).setText(title)
        header.findViewById<View>(R.id.select_all).setOnClickListener { boxes.forEach { it.isChecked = true } }
        header.findViewById<View>(R.id.select_none).setOnClickListener { boxes.forEach { it.isChecked = false } }
        if (boxes.isEmpty()) {
            header.findViewById<View>(R.id.select_all).visibility = View.INVISIBLE // keeps the row's height
            header.findViewById<View>(R.id.select_none).visibility = View.INVISIBLE
        }
    }

    /** A section whose heading folds [content] away, with its Select all / None; open each time the list opens. */
    fun foldable(title: Int, boxes: List<CheckBox>, content: List<View>) {
        val header = layoutInflater.inflate(R.layout.item_filter_section, rows, false)
        section(header, title, boxes)
        val arrow = header.findViewById<View>(R.id.arrow).apply { visibility = View.VISIBLE }
        val buttons = listOf(header.findViewById<View>(R.id.select_all), header.findViewById(R.id.select_none))
        val shown = buttons.first().visibility
        var open = true
        header.setOnClickListener {
            open = !open
            arrow.rotation = if (open) 0f else -90f
            buttons.forEach { it.visibility = if (open) shown else View.GONE }
            content.forEach { it.visibility = if (open) View.VISIBLE else View.GONE }
        }
        rows.addView(header)
        content.forEach(rows::addView)
    }
    section(view.findViewById(R.id.all), R.string.filter_all_apps, ownBoxes + otherBoxes)
    foldable(R.string.filter_this_profile, ownBoxes, ownBoxes)
    val otherHint = layoutInflater.inflate(R.layout.item_filter_hint, rows, false)
    foldable(R.string.filter_other_profiles, otherBoxes, listOf(otherHint) + otherBoxes)

    MaterialAlertDialogBuilder(this)
        .setTitle(if (mode == FilterMode.ALLOW) R.string.filter_allow else R.string.filter_ignore)
        .setView(view)
        .setPositiveButton(R.string.filter_done) { _, _ ->
            onDone((ownBoxes + otherBoxes).filter { it.isChecked }.map { it.tag as String }.toSet())
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

/**
 * Apps for the list, each sorted by name. This profile: launcher apps plus those that have notified, named
 * where the package manager can see them. Other profiles can't be queried, so only those that have notified,
 * plus any already in [listed], by package name; an app in both profiles shows once, under this profile.
 */
private fun Activity.filterApps(listed: Set<String>): Pair<List<App>, List<App>> {
    val seen = SeenApps(this)
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION") packageManager.queryIntentActivities(launcher, 0)
    }
    val launcherLabels = resolved.associate {
        it.activityInfo.packageName to it.activityInfo.applicationInfo.loadLabel(packageManager).toString()
    }
    val ownPkgs = launcherLabels.keys + seen.get(own = true) - packageName
    val otherPkgs = seen.get(own = false) + listed - ownPkgs - packageName
    fun label(pkg: String) = launcherLabels[pkg] ?: try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
    return ownPkgs.map { App(it, label(it)) }.sortedBy { it.label.lowercase() } to
        otherPkgs.sorted().map { App(it, it) }
}
