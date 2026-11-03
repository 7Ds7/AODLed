package com.binaryunit.aodled.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import com.binaryunit.aodled.R

/** Small card next to a "?" icon, below it or [above] it; tapping anywhere else closes it. */
fun Activity.showHelp(anchor: View, text: Int, above: Boolean = false) {
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
