package com.example.doswriter.logic.hal

import android.content.Context
import android.os.Build

object HardwareFactory {
    private var activeProvider: HardwareProvider? = null

    /**
     * Initializes the hardware provider based on user selection or auto-detection.
     */
    fun getProvider(context: Context, manufacturer: Int = 0): HardwareProvider {
        val current = activeProvider
        if (current != null) return current

        // 0: Auto, 1: BOOX, 2: Bigme, 3: Supernote, 4: Other
        activeProvider = when (manufacturer) {
            1 -> BooxHardwareProvider()
            else -> GenericAndroidProvider()
        }
        
        return activeProvider!!
    }

    /**
     * Resets the active provider (used when user changes Manufacturer in settings)
     */
    fun refreshProvider(context: Context, manufacturer: Int) {
        activeProvider = null
        getProvider(context, manufacturer)
    }
}
