package com.example.doswriter.logic.hal

import android.app.Activity

class GenericAndroidProvider : HardwareProvider {
    private var currentBrightness = -1f // -1 means system default

    override fun getProviderName(): String = "Generic Android"

    override fun adjustBrightness(delta: Float, activity: Activity) {
        val lp = activity.window.attributes
        if (currentBrightness == -1f) currentBrightness = lp.screenBrightness
        if (currentBrightness < 0) currentBrightness = 0.5f // Default start
        
        currentBrightness = (currentBrightness + delta).coerceIn(0.01f, 1.0f)
        lp.screenBrightness = currentBrightness
        activity.window.attributes = lp
    }

    override fun setBrightness(level: Float, activity: Activity) {
        val lp = activity.window.attributes
        currentBrightness = level.coerceIn(0.01f, 1.0f)
        lp.screenBrightness = currentBrightness
        activity.window.attributes = lp
    }

    override fun getBrightness(activity: Activity): Float {
        if (currentBrightness == -1f) {
            val lp = activity.window.attributes
            return if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
        }
        return currentBrightness
    }

    override fun getCapabilities(): HardwareCapabilities {
        return HardwareCapabilities(
            hasFrontlight = true,
            hasWarmLight = false,
            hasRefreshModes = false,
            hasFullRefresh = false,
            hasStylus = false
        )
    }

    override fun getDiagnosticInfo(): String {
        return "SDK: Android Native\nRefresh: Not Supported\nStatus: Active"
    }
}
