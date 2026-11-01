package com.example.doswriter.logic

import android.content.Context
import android.os.Build
import android.view.WindowManager

class DeviceProfiler(private val context: Context) {

    data class DeviceInfo(
        val manufacturer: String,
        val brand: String,
        val model: String,
        val width: Int,
        val height: Int,
        val apiLevel: Int
    )

    fun getDeviceInfo(): DeviceInfo {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val width: Int
        val height: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            width = bounds.width()
            height = bounds.height()
        } else {
            val display = windowManager.defaultDisplay
            val size = android.graphics.Point()
            display.getRealSize(size)
            width = size.x
            height = size.y
        }

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            width = width,
            height = height,
            apiLevel = Build.VERSION.SDK_INT
        )
    }
    
    fun getFormattedInfo(): String {
        val info = getDeviceInfo()
        return """
            MANUFACTURER: ${info.manufacturer}
            BRAND: ${info.brand}
            MODEL: ${info.model}
            VIEWPORT: ${info.width} x ${info.height}
            API LEVEL: ${info.apiLevel}
        """.trimIndent()
    }
}
