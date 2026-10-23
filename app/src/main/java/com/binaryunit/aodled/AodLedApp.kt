package com.binaryunit.aodled

import android.app.Application
import com.google.android.material.color.DynamicColors

class AodLedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
