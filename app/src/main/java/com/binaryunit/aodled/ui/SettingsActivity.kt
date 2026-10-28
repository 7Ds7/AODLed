package com.binaryunit.aodled.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.util.Linkify
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import com.binaryunit.aodled.R
import com.binaryunit.aodled.colour.Palette
import com.binaryunit.aodled.colour.hexOf
import com.binaryunit.aodled.colour.hueSaturationBrightness
import com.binaryunit.aodled.colour.parseHex
import com.binaryunit.aodled.colour.pickedColour
import com.binaryunit.aodled.colour.visibleOnBlack
import com.binaryunit.aodled.config.Animation
import com.binaryunit.aodled.config.ColourMode
import com.binaryunit.aodled.config.LightConfig
import com.binaryunit.aodled.config.LightOff
import com.binaryunit.aodled.config.Prefs
import com.binaryunit.aodled.config.Style
import com.binaryunit.aodled.render.cutoutPath
import com.binaryunit.aodled.service.LightListenerService
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout

private const val DISABLED_ALPHA = 0.38f // Material's opacity for disabled content

class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var cfg: LightConfig
    private lateinit var statusRows: LinearLayout
    private lateinit var palette: ChipGroup
    private lateinit var customChip: Chip
    private lateinit var styleField: TextInputLayout
    private lateinit var styleMenu: MaterialAutoCompleteTextView
    private var cutout: Path? = null
    private var binding = false // true while syncing controls from cfg, so listeners don't save

    private val styleNames = mapOf(
        Style.RING to R.string.style_ring, Style.DOT to R.string.style_dot,
        Style.EDGES to R.string.style_edges, Style.CUTOUT to R.string.style_cutout,
    )
    private val animations = mapOf(Animation.OFF to R.id.anim_off, Animation.PULSE to R.id.anim_pulse, Animation.MOTION to R.id.anim_motion)
    private val colourModes = mapOf(ColourMode.APP to R.id.colour_app, ColourMode.FIXED to R.id.colour_fixed)
    private val lightOffs = mapOf(LightOff.ON_DISMISS to R.id.off_dismiss, LightOff.ON_UNLOCK to R.id.off_unlock)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) postTest()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        // Edge to edge (targetSdk 35): keep the toolbar below the status bar and the last button above the nav bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.updatePadding(top = bars.top)
            findViewById<View>(R.id.content).updatePadding(bottom = bars.bottom)
            insets
        }
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            inflateMenu(R.menu.settings)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.about -> showAbout()
                    R.id.close -> finish() // only closes this screen; the light keeps running in its services
                }
                true
            }
        }
        prefs = Prefs(this)
        statusRows = findViewById(R.id.status_rows)
        palette = findViewById(R.id.palette)

        findViewById<View>(R.id.help_status).setOnClickListener { showHelp(it, R.string.help_status) }
        findViewById<View>(R.id.help_light_off).setOnClickListener { showHelp(it, R.string.help_light_off, above = true) }
        cutout = cutoutPath()
        buildStyleMenu()
        bindToggle(R.id.animation_group, animations) { cfg.copy(animation = it) }
        bindToggle(R.id.colour_group, colourModes) { cfg.copy(colourMode = it) }
        bindToggle(R.id.light_off_group, lightOffs) { cfg.copy(lightOff = it) }
        buildPalette()

        findViewById<View>(R.id.adjust).setOnClickListener { startActivity(Intent(this, EditorActivity::class.java)) }
        findViewById<View>(R.id.test).setOnClickListener {
            // Android 12 has no notification permission: notifications are allowed by default
            if (Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                postTest()
            } else {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cfg = prefs.load() // the editor may have changed the style
        syncControls()
        buildStatusRows()
    }

    private fun <T> bindToggle(groupId: Int, ids: Map<T, Int>, update: (T) -> LightConfig) {
        findViewById<MaterialButtonToggleGroup>(groupId).addOnButtonCheckedListener { _, id, checked ->
            if (!checked || binding) return@addOnButtonCheckedListener
            save(update(ids.entries.first { it.value == id }.key))
        }
    }

    private fun postTest() {
        TestNotifier.post(this)
        Snackbar.make(findViewById(R.id.root), R.string.test_sent, Snackbar.LENGTH_LONG).show()
    }

    private fun showAbout() {
        val version = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0).versionName
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.app_name)
            .setMessage(getString(R.string.about_version, version) + "\n\n" + getString(R.string.about_links))
            .setPositiveButton(android.R.string.ok, null)
            .show()
        // Tapping a link opens the browser; the app itself has no internet permission
        dialog.findViewById<TextView>(android.R.id.message)?.let { Linkify.addLinks(it, Linkify.WEB_URLS) }
    }

    /** Small card next to a "?" icon, below it or [above] it; tapping anywhere else closes it. */
    private fun showHelp(anchor: View, text: Int, above: Boolean = false) {
        val view = layoutInflater.inflate(R.layout.help_popup, null)
        view.findViewById<TextView>(R.id.text).text = getText(text)
        val popup = PopupWindow(view, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        if (above) {
            view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            popup.showAsDropDown(anchor, 0, -(anchor.height + view.measuredHeight))
        } else {
            popup.showAsDropDown(anchor)
        }
    }

    /** Style dropdown: each entry shows the style's shape; Cutout is greyed out without a camera cutout. */
    private fun buildStyleMenu() {
        styleField = findViewById(R.id.style_field)
        styleMenu = findViewById(R.id.style_menu)
        styleMenu.setAdapter(object : ArrayAdapter<Style>(this, R.layout.item_style, R.id.name, Style.entries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).apply {
                    val style = Style.entries[position]
                    findViewById<TextView>(R.id.name).setText(styleNames.getValue(style))
                    findViewById<ImageView>(R.id.icon).setImageDrawable(styleIcon(style))
                    alpha = if (isEnabled(position)) 1f else DISABLED_ALPHA
                }

            override fun areAllItemsEnabled() = cutout != null
            override fun isEnabled(position: Int) = Style.entries[position] != Style.CUTOUT || cutout != null
        })
        styleMenu.setOnItemClickListener { _, _, position, _ ->
            if (!binding) save(cfg.copy(style = Style.entries[position]))
        }
    }

    private fun styleIcon(style: Style) = StyleIcon(
        style, cutout,
        MaterialColors.getColor(styleMenu, com.google.android.material.R.attr.colorOnSurfaceVariant),
        (24 * resources.displayMetrics.density).toInt(),
    )

    private fun buildPalette() {
        val names = resources.getStringArray(R.array.palette_names)
        Palette.ALL.forEachIndexed { i, colour ->
            palette.addView(Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                id = View.generateViewId()
                tag = colour
                text = names[i]
                isCheckable = true
                chipIcon = getDrawable(R.drawable.ic_ring)
                chipIconTint = ColorStateList.valueOf(colour)
                isChipIconVisible = true
            })
        }
        // Custom opens the picker on every tap; it's checked by syncControls once a custom colour is saved
        customChip = Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
            id = View.generateViewId()
            text = getString(R.string.colour_custom)
            isCheckable = true
            chipIcon = getDrawable(R.drawable.ic_ring)
            isChipIconVisible = true
            setOnClickListener { showColourPicker() }
        }
        palette.addView(customChip)
        palette.setOnCheckedStateChangeListener { group, checkedIds ->
            if (binding || checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val colour = group.findViewById<Chip>(checkedIds[0]).tag as? Int ?: return@setOnCheckedStateChangeListener
            save(cfg.copy(fixedColour = colour))
        }
    }

    private fun showColourPicker() {
        val view = layoutInflater.inflate(R.layout.dialog_colour, null)
        val preview = view.findViewById<ImageView>(R.id.preview)
        val warning = view.findViewById<View>(R.id.contrast_warning)
        val hue = view.findViewById<Slider>(R.id.hue)
        val saturation = view.findViewById<Slider>(R.id.saturation)
        val brightness = view.findViewById<Slider>(R.id.brightness)
        val hex = view.findViewById<EditText>(R.id.hex)
        // The colour that OK saves: from the sliders, or exactly as typed in the hex field
        var colour = cfg.fixedColour
        // Sliders and hex field follow each other; syncing stops each from echoing back into the other
        var syncing = false
        fun show() {
            preview.imageTintList = ColorStateList.valueOf(colour)
            warning.visibility = if (visibleOnBlack(colour)) View.INVISIBLE else View.VISIBLE
        }
        fun setSliders() {
            val (h, s, v) = hueSaturationBrightness(colour)
            syncing = true
            hue.value = h
            saturation.value = s
            brightness.value = v
            syncing = false
        }
        fun setHex() {
            syncing = true
            hex.setText(hexOf(colour))
            syncing = false
        }
        setSliders()
        setHex()
        show()
        val fromSliders = Slider.OnChangeListener { _, _, _ ->
            if (syncing) return@OnChangeListener
            colour = pickedColour(hue.value, saturation.value, brightness.value)
            setHex()
            show()
        }
        listOf(hue, saturation, brightness).forEach { it.addOnChangeListener(fromSliders) }
        hex.doAfterTextChanged { text ->
            if (syncing) return@doAfterTextChanged
            colour = parseHex(text.toString()) ?: return@doAfterTextChanged
            setSliders()
            show()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.colour_custom_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ -> save(cfg.copy(fixedColour = colour)) }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { syncControls() } // a cancelled pick leaves the previous chip checked
            .show()
    }

    private fun save(c: LightConfig) {
        cfg = c
        prefs.save(c)
        syncControls()
    }

    private fun syncControls() {
        binding = true
        styleMenu.setText(getString(styleNames.getValue(cfg.style)), false)
        styleField.startIconDrawable = styleIcon(cfg.style)
        findViewById<MaterialButtonToggleGroup>(R.id.animation_group).check(animations.getValue(cfg.animation))
        findViewById<MaterialButtonToggleGroup>(R.id.colour_group).check(colourModes.getValue(cfg.colourMode))
        findViewById<MaterialButtonToggleGroup>(R.id.light_off_group).check(lightOffs.getValue(cfg.lightOff))
        palette.visibility = if (cfg.colourMode == ColourMode.FIXED) View.VISIBLE else View.GONE
        val preset = (0 until palette.childCount).map { palette.getChildAt(it) as Chip }
            .firstOrNull { it.tag == cfg.fixedColour }
        palette.check((preset ?: customChip).id)
        customChip.chipIconTint = ColorStateList.valueOf(
            if (preset == null) cfg.fixedColour
            else MaterialColors.getColor(customChip, com.google.android.material.R.attr.colorOnSurfaceVariant),
        )
        binding = false
    }

    private fun buildStatusRows() {
        statusRows.removeAllViews()
        addStatusRow(R.string.status_listener, Status.listenerGranted(this)) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(this, LightListenerService::class.java).flattenToString(),
            )
        }
        addStatusRow(R.string.status_accessibility, Status.accessibilityEnabled(this)) {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        addStatusRow(R.string.status_battery, Status.batteryUnrestricted(this)) {
            // The request dialog closes at once when we're already unrestricted, so show App info instead
            val action = if (Status.batteryUnrestricted(this)) Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                else Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            Intent(action, Uri.parse("package:$packageName"))
        }
        // The toggle's page moves between versions (Lock screen on 12, Display itself on 16+); Display covers both
        addStatusRow(R.string.status_aod, Status.aodEnabled(this)) { Intent(Settings.ACTION_DISPLAY_SETTINGS) }
    }

    private fun addStatusRow(title: Int, ok: Boolean?, intent: () -> Intent) {
        val row = layoutInflater.inflate(R.layout.item_status, statusRows, false)
        row.findViewById<TextView>(R.id.title).setText(title)
        row.findViewById<TextView>(R.id.summary).setText(
            when (ok) {
                true -> R.string.status_ok
                false -> R.string.status_missing
                null -> R.string.status_unknown
            }
        )
        row.findViewById<ImageView>(R.id.icon).apply {
            setImageResource(if (ok == false) R.drawable.ic_status_missing else R.drawable.ic_status_ok)
            imageTintList = ColorStateList.valueOf(
                MaterialColors.getColor(
                    this,
                    when (ok) {
                        true -> androidx.appcompat.R.attr.colorPrimary
                        false -> androidx.appcompat.R.attr.colorError
                        null -> com.google.android.material.R.attr.colorOnSurfaceVariant
                    },
                )
            )
        }
        row.setOnClickListener { open(intent()) }
        statusRows.addView(row)
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Fallbacks for settings pages that don't exist on this build
            val fallback = when (intent.action) {
                Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            startActivity(Intent(fallback))
        }
    }
}
