package com.example.doswriter.logic.hal

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import java.lang.reflect.Method

class BooxHardwareProvider : HardwareProvider {
    private var sdkDetected = false
    private var diagnosticMessage = "Not initialized"
    
    // Cached Reflection Methods
    private var setFrontlightValueMethod: Method? = null
    private var getFrontlightValueMethod: Method? = null
    private var applyRefreshModeMethod: Method? = null
    private var repaintScreenMethod: Method? = null
    
    // Frontlight Type Constants
    private var CTM_TYPE = 0 // Cold
    private var FL_TYPE = 1  // Warm
    
    // EPD Mode Constants
    private var MODE_FULL = 1
    private var MODE_A2 = 11

    override fun getProviderName(): String = "BOOX (Onyx)"

    override fun initialize(activity: Activity) {
        val loader = activity.classLoader
        try {
            // Priority list for newer firmware (Android 11, 12, 13)
            val lightClasses = listOf(
                "com.onyx.android.sdk.api.device.frontlight.FrontlightManager",
                "com.onyx.android.sdk.device.FrontlightManager",
                "com.onyx.android.sdk.api.device.frontlight.FrontlightController",
                "com.onyx.hardware.FrontlightManager",
                "com.onyx.android.sdk.api.device.frontlight.FrontlightManager"
            )
            val epdClasses = listOf(
                "com.onyx.android.sdk.api.device.epd.EpdController",
                "com.onyx.android.sdk.device.EpdController",
                "com.onyx.android.sdk.api.device.epd.EpdManager",
                "com.onyx.android.sdk.api.device.EPDDeviceManager",
                "com.onyx.hardware.EpdController",
                "android.view.View" // Placeholder to see if we find NOTHING
            )

            var lightClass: Class<*>? = null
            var lightClassName = "None"
            for (name in lightClasses) {
                try { 
                    lightClass = loader.loadClass(name)
                    lightClassName = name.substringAfterLast(".")
                    break 
                } catch (e: Exception) {}
            }

            var epdClass: Class<*>? = null
            var epdClassName = "None"
            for (name in epdClasses) {
                if (name == "android.view.View") continue
                try { 
                    epdClass = loader.loadClass(name)
                    epdClassName = name.substringAfterLast(".")
                    break 
                } catch (e: Exception) {}
            }

            // Root Probes - Try to find ANY Onyx-related entry points
            val sdkRoot = try { loader.loadClass("com.onyx.android.sdk.api.device.Device") } catch (e: Exception) { null }
            val hardwareRoot = try { loader.loadClass("com.onyx.hardware.Device") } catch (e: Exception) { null }
            val rootProbe = if (sdkRoot != null) "SDK Found" else if (hardwareRoot != null) "OnyxHW Found" else "Onyx Class-Missing"

            if (lightClass != null) {
                setFrontlightValueMethod = findMethod(lightClass, "setFrontlightValue", Context::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                getFrontlightValueMethod = findMethod(lightClass, "getFrontlightValue", Context::class.java, Int::class.javaPrimitiveType)
                
                val coldFields = listOf("FRONT_LIGHT_COLD", "CTM_TYPE", "LIGHT_TYPE_COLD")
                val warmFields = listOf("FRONT_LIGHT_WARM", "FL_TYPE", "LIGHT_TYPE_WARM")
                
                for (f in coldFields) {
                    try { CTM_TYPE = lightClass.getField(f).getInt(null); break } catch (e: Exception) {}
                }
                for (f in warmFields) {
                    try { FL_TYPE = lightClass.getField(f).getInt(null); break } catch (e: Exception) {}
                }
            }
            
            if (epdClass != null) {
                applyRefreshModeMethod = findMethod(epdClass, "applyRefreshMode", Context::class.java, Int::class.javaPrimitiveType)
                repaintScreenMethod = findMethod(epdClass, "repaintScreen", View::class.java, Int::class.javaPrimitiveType)
                    ?: findMethod(epdClass, "repaintScreen", View::class.java)
            }
            
            sdkDetected = setFrontlightValueMethod != null || applyRefreshModeMethod != null
            diagnosticMessage = if (sdkDetected) {
                "Hooks: L=${setFrontlightValueMethod!=null} E=${applyRefreshModeMethod!=null}"
            } else {
                "$rootProbe. Found: $lightClassName, $epdClassName."
            }
            
        } catch (e: Exception) {
            sdkDetected = false
            diagnosticMessage = "Init Err: ${e.message}"
        }
    }

    private fun findMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>?): Method? {
        return try { clazz.getMethod(name, *parameterTypes) } catch (e: Exception) { null }
    }

    override fun adjustBrightness(delta: Float, activity: Activity) {
        val current = getBrightness(activity)
        setBrightness((current + delta).coerceIn(0f, 1f), activity)
    }

    override fun setBrightness(level: Float, activity: Activity) {
        if (setFrontlightValueMethod != null) {
            try {
                val onyxValue = (level * 255).toInt()
                setFrontlightValueMethod?.invoke(null, activity, CTM_TYPE, onyxValue)
                return
            } catch (e: Exception) {
                Log.e("BooxHAL", "Set brightness failed: ${e.message}")
            }
        }
        
        val lp = activity.window.attributes
        lp.screenBrightness = level.coerceIn(0.01f, 1.0f)
        activity.window.attributes = lp
    }

    override fun getBrightness(activity: Activity): Float {
        if (getFrontlightValueMethod != null) {
            try {
                val value = getFrontlightValueMethod?.invoke(null, activity, CTM_TYPE) as Int
                return value / 255f
            } catch (e: Exception) { }
        }
        val lp = activity.window.attributes
        return if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
    }

    override fun setWarmLight(level: Float, activity: Activity) {
        if (setFrontlightValueMethod != null) {
            try {
                val onyxValue = (level * 255).toInt()
                setFrontlightValueMethod?.invoke(null, activity, FL_TYPE, onyxValue)
            } catch (e: Exception) { }
        }
    }

    override fun getCapabilities(): HardwareCapabilities {
        return HardwareCapabilities(
            hasFrontlight = true,
            hasWarmLight = setFrontlightValueMethod != null,
            hasRefreshModes = applyRefreshModeMethod != null,
            hasFullRefresh = repaintScreenMethod != null,
            hasStylus = false
        )
    }

    override fun getDiagnosticInfo(): String {
        return if (sdkDetected) {
            "SDK Status: Active\n$diagnosticMessage"
        } else {
            "SDK Status: Not Found\nMode: Standard Android Fallback"
        }
    }

    override fun performFullRefresh(activity: Activity) {
        if (repaintScreenMethod == null) return
        try {
            if (repaintScreenMethod!!.parameterTypes.size == 2) {
                repaintScreenMethod?.invoke(null, activity.window.decorView, MODE_FULL)
            } else {
                repaintScreenMethod?.invoke(null, activity.window.decorView)
            }
        } catch (e: Exception) {
            Log.e("BooxHAL", "Full refresh failed: ${e.message}")
        }
    }

    override fun setRefreshMode(mode: RefreshMode, activity: Activity) {
        if (applyRefreshModeMethod == null) return
        try {
            val onyxMode = when(mode) {
                RefreshMode.NORMAL -> 1
                RefreshMode.REGAL -> 10
                RefreshMode.A2 -> 11
                RefreshMode.XMODE -> 12
            }
            applyRefreshModeMethod?.invoke(null, activity, onyxMode)
        } catch (e: Exception) {
            Log.e("BooxHAL", "Set refresh mode failed")
        }
    }
}
