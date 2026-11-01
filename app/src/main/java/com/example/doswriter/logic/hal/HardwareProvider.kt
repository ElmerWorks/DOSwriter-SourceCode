package com.example.doswriter.logic.hal

import android.app.Activity

data class HardwareCapabilities(
    val hasFrontlight: Boolean = false,
    val hasWarmLight: Boolean = false,
    val hasRefreshModes: Boolean = false,
    val hasFullRefresh: Boolean = false,
    val hasStylus: Boolean = false
)

enum class RefreshMode {
    NORMAL, REGAL, A2, XMODE
}

interface HardwareProvider {
    /**
     * Reports the hardware manufacturer/name.
     */
    fun getProviderName(): String

    /**
     * Called when the provider is activated.
     */
    fun initialize(activity: Activity) {}

    /**
     * Called when the provider is deactivated.
     */
    fun shutdown(activity: Activity) {}

    /**
     * Adjusts the screen brightness.
     * @param delta Change in brightness (e.g., -0.1f to +0.1f)
     */
    fun adjustBrightness(delta: Float, activity: Activity)

    /**
     * Set absolute brightness level (0.0f to 1.0f)
     */
    fun setBrightness(level: Float, activity: Activity)

    /**
     * Current brightness level (0.0f to 1.0f)
     */
    fun getBrightness(activity: Activity): Float

    /**
     * Reports supported hardware features.
     */
    fun getCapabilities(): HardwareCapabilities

    /**
     * Diagnostic info for the Hardware Test page.
     */
    fun getDiagnosticInfo(): String = "Provider: ${getProviderName()}"

    /**
     * Vendor-specific extensions
     */
    fun setWarmLight(level: Float, activity: Activity) {}
    fun setRefreshMode(mode: RefreshMode, activity: Activity) {}
    fun performFullRefresh(activity: Activity) {}
}
