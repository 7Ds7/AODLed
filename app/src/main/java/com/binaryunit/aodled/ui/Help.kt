package com.binaryunit.aodled.ui

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import com.binaryunit.aodled.R

/**
 * Small card next to a "?" icon, below it or [above] it; if it doesn't fit there it goes on the roomier side,
 * and text too tall for either scrolls. Tapping anywhere else closes it.
 */
fun Activity.showHelp(anchor: View, text: Int, above: Boolean = false) {
    val view = layoutInflater.inflate(R.layout.help_popup, null)
    view.findViewById<TextView>(R.id.text).text = getText(text)
    // Measured at the width it will get, so the height is right on narrow screens and with large text
    val screen = Rect().also { anchor.getWindowVisibleDisplayFrame(it) }
    view.measure(View.MeasureSpec.makeMeasureSpec(screen.width(), View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED)
    val top = IntArray(2).also { anchor.getLocationOnScreen(it) }[1]
    val roomAbove = top - screen.top
    val roomBelow = screen.bottom - top - anchor.height
    val up = if (view.measuredHeight <= (if (above) roomAbove else roomBelow)) above else roomAbove > roomBelow
    val height = minOf(view.measuredHeight, if (up) roomAbove else roomBelow)
    PopupWindow(view, ViewGroup.LayoutParams.WRAP_CONTENT, height, true)
        .showAsDropDown(anchor, 0, if (up) -(anchor.height + height) else 0)
}
