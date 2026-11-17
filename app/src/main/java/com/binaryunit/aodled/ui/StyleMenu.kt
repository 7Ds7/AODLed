package com.binaryunit.aodled.ui

import android.graphics.Path
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.binaryunit.aodled.R
import com.binaryunit.aodled.config.Style
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout

private val styleNames = mapOf(
    Style.RING to R.string.style_ring, Style.RIPPLE to R.string.style_ripple, Style.RADAR to R.string.style_radar,
    Style.DOT to R.string.style_dot, Style.EDGES to R.string.style_edges, Style.CUTOUT to R.string.style_cutout,
)

/**
 * Style dropdown, used by the settings and the editor: each entry shows the style's shape; Cutout is greyed out
 * without a camera cutout ([cutout] is its outline, also used for its icon).
 */
class StyleMenu(
    private val field: TextInputLayout,
    private val menu: MaterialAutoCompleteTextView,
    private val cutout: Path?,
    onPick: (Style) -> Unit,
) {
    init {
        menu.setAdapter(object : ArrayAdapter<Style>(menu.context, R.layout.item_style, R.id.name, Style.entries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).apply {
                    val style = Style.entries[position]
                    findViewById<TextView>(R.id.name).setText(styleNames.getValue(style))
                    findViewById<ImageView>(R.id.icon).setImageDrawable(icon(style))
                    alpha = if (isEnabled(position)) 1f else DISABLED_ALPHA
                }

            override fun areAllItemsEnabled() = cutout != null
            override fun isEnabled(position: Int) = Style.entries[position] != Style.CUTOUT || cutout != null
        })
        // The adapter would leave the enum's own name in the field, so show the picked style properly first
        menu.setOnItemClickListener { _, _, position, _ ->
            show(Style.entries[position])
            onPick(Style.entries[position])
        }
    }

    /** Shows [style] as the chosen one, without calling back. */
    fun show(style: Style) {
        menu.setText(menu.context.getString(styleNames.getValue(style)), false)
        field.startIconDrawable = icon(style)
    }

    private fun icon(style: Style) = StyleIcon(
        style, cutout,
        MaterialColors.getColor(menu, com.google.android.material.R.attr.colorOnSurfaceVariant),
        (24 * menu.resources.displayMetrics.density).toInt(),
    )
}
