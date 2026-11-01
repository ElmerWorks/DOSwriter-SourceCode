package com.example.doswriter.ui

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.doswriter.MainActivity
import com.example.doswriter.logic.TelexProcessor
import com.example.doswriter.editor.EditorView
import java.util.*
import kotlin.math.max

internal fun MainActivity.refreshActiveViews() {
    if (isSplitActive) {
        leftEditorView.refreshMetrics()
        rightEditorView.refreshMetrics()
        leftEditorView.invalidate()
        rightEditorView.invalidate()
    } else {
        editorView.refreshMetrics()
        editorView.invalidate()
    }
    applyTheme()
}

internal fun MainActivity.applyTheme() {
    val currentBuffer = bufferManager.getCurrentBuffer()
    val theme = themes[if (isDaytime()) currentBuffer.dayThemeIndex else currentBuffer.nightThemeIndex]
    
    val kbBg = if (currentBuffer.keyboardThemeBg) theme.backgroundColor else theme.textColor
    val kbFg = if (currentBuffer.keyboardThemeBg) theme.textColor else theme.backgroundColor
    internalKeyboardView.setThemeColors(kbFg, kbBg, theme.cursorColor)
    internalKeyboardView.showKeyBorder = currentBuffer.showKeyBorder

    if (isSplitActive) {
        applyThemeToView(leftEditorView, bufferManager.getBufferAt(leftBufferIdx))
        applyThemeToView(rightEditorView, bufferManager.getBufferAt(rightBufferIdx))
    } else {
        applyThemeToView(editorView, currentBuffer)
    }
}

internal fun MainActivity.applyThemeToView(view: EditorView, buffer: com.example.doswriter.model.EditorBuffer) {
    val isPdf = buffer.pdfUri != null
    val theme = themes[if (isDaytime()) buffer.dayThemeIndex else buffer.nightThemeIndex]
    
    if (isPdf) {
        view.setTextColor(android.graphics.Color.BLACK)
        view.setCustomBackgroundColor(android.graphics.Color.WHITE)
        view.setCursorColor(android.graphics.Color.DKGRAY)
    } else {
        view.setTextColor(theme.textColor)
        view.setCustomBackgroundColor(theme.backgroundColor)
        view.setCursorColor(buffer.cursorColor ?: theme.cursorColor)
    }
}

internal fun MainActivity.isDaytime(): Boolean {
    if (themeMode == 1) return true; if (themeMode == 2) return false
    val now = Calendar.getInstance(); val currentTime = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val dayStart = dayStartHour * 60 + dayStartMin; val nightStart = nightStartHour * 60 + nightStartMin
    return if (dayStart < nightStart) currentTime in dayStart until nightStart else currentTime >= dayStart || currentTime < nightStart
}

internal fun MainActivity.updateStatus() {
    statusUpdateHandler.removeCallbacks(statusUpdateRunnable)
    statusUpdateHandler.postDelayed(statusUpdateRunnable, 500)
}

internal fun MainActivity.showStatusToast(message: String, length: Int = Toast.LENGTH_SHORT, force: Boolean = false) {
    if (force || (!isStatusMessagesSuppressed && !commandScriptManager.isScriptRunning())) {
        Toast.makeText(this, message, length).show()
    }
}

internal fun MainActivity.updateStatusImmediate() {
    val buffer = bufferManager.getCurrentBuffer()
    val isDay = isDaytime()
    val theme = themes[if (isDay) buffer.dayThemeIndex else buffer.nightThemeIndex]
    
    val r = Color.red(theme.backgroundColor)
    val g = Color.green(theme.backgroundColor)
    val b = Color.blue(theme.backgroundColor)
    val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
    statusText.setTextColor(if (luminance > 128) Color.BLACK else Color.LTGRAY)

    val info = if (isSplitActive) {
        val left = bufferManager.getBufferAt(leftBufferIdx)
        val right = bufferManager.getBufferAt(rightBufferIdx)
        
        val lLabel = if (leftBufferIdx == 8) "SP" else "B${leftBufferIdx + 1}"
        val rLabel = if (rightBufferIdx == 8) "SP" else "B${rightBufferIdx + 1}"
        
        val lDirty = if (left.isDirty) "*" else ""
        val rDirty = if (right.isDirty) "*" else ""
        
        val leftName = (left.fileName ?: "Empty") + lDirty
        val rightName = (right.fileName ?: "Empty") + rDirty
        
        var pdfInfo = ""
        if (left.pdfUri != null) {
            val current = leftEditorView.getCurrentPdfPage() + 1
            val total = leftEditorView.getPdfPageCount()
            pdfInfo = " | Page $current of $total"
        } else if (right.pdfUri != null) {
            val current = rightEditorView.getCurrentPdfPage() + 1
            val total = rightEditorView.getPdfPageCount()
            pdfInfo = " | Page $current of $total"
        }
        
        "$lLabel: $leftName | $rLabel: $rightName$pdfInfo"
    } else {
        val bufferIdx = bufferManager.currentBufferIndex
        if (bufferIdx == 8) {
            "ScratchPad : ${bufferManager.scratchLogFilename}"
        } else {
            val bufferLabel = if (bufferManager.isTodoWorkspace) "To-Do Task List ${bufferIdx + 1}" 
                             else "Buffer ${bufferIdx + 1}"
            
            val syncInd = if (buffer.syncFilePath != null) " [SYNCED]" else ""
            val dirtyInd = if (buffer.isDirty) "*" else ""
            val name = (buffer.fileName ?: "Unsaved") + syncInd + dirtyInd
            val typeInd = if (editorView.isTypewriterActive()) " [T]" else ""
            
            if (buffer.pdfUri != null) {
                val current = editorView.getCurrentPdfPage() + 1
                val total = editorView.getPdfPageCount()
                "$bufferLabel: $name | Page $current of $total"
            } else {
                "$bufferLabel: $name$typeInd | Pos: ${buffer.getWordPosition()} | Words: ${buffer.getWordCount()}"
            }
        }
    }

    statusText.text = info
    statusText.gravity = Gravity.END
}

internal fun MainActivity.saveStatusVisibility() = getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("status_visible", isStatusVisible).apply()

internal fun MainActivity.loadStatusVisibility() { 
    isStatusVisible = getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("status_visible", true)
    statusText.visibility = if (isStatusVisible) View.VISIBLE else View.GONE 
}

internal fun MainActivity.saveGlobalSettings() {
    with(getSharedPreferences("settings", Context.MODE_PRIVATE).edit()) {
        putInt("theme_mode", themeMode); putInt("day_start_h", dayStartHour); putInt("day_start_m", dayStartMin); putInt("night_start_h", nightStartHour); putInt("night_start_m", nightStartMin)
        putInt("device_type", deviceType)
        putInt("hw_manufacturer", hardwareManufacturer)
        putInt("menu_font_mode", menuFontSizeMode)
        putInt("menu_color_mode", menuColorMode)
        putInt("status_font_mode", statusFontSizeMode)
        putBoolean("startup_help_enabled", isStartupHelpEnabled)
        putBoolean("startup_overview_enabled", isStartupOverviewEnabled)
        putBoolean("status_help_enabled", isStatusHelpEnabled)
        putBoolean("font_scan_enabled", isFontScanEnabled)
        putBoolean("help_task_switch", isHelpTaskSwitchEnabled)
        putBoolean("status_messages_suppressed", isStatusMessagesSuppressed)
        putBoolean("persistent_screenshot_enabled", isPersistentScreenshotEnabled)
        putBoolean("system_clipboard_suppressed", isSystemClipboardSuppressed)
        putBoolean("image_fade_enabled", isImageFadeEnabled)
        putBoolean("settings_global", isSettingsGlobal)
        putInt("keyboard_mode", keyboardMode.ordinal)
        
        val custom = themes.subList(baseThemes.size, themes.size)
        val encoded = custom.joinToString(";") { "${it.name}|${it.textColor}|${it.backgroundColor}" }
        putString("custom_themes", encoded)
        putString("macro_text", macroText)
        
        apply()
    }
}

internal fun MainActivity.loadGlobalSettings() {
    val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
    themeMode = prefs.getInt("theme_mode", 0); dayStartHour = prefs.getInt("day_start_h", 7); dayStartMin = prefs.getInt("day_start_m", 0); nightStartHour = prefs.getInt("night_start_h", 19); nightStartMin = prefs.getInt("night_start_m", 0)
    deviceType = prefs.getInt("device_type", -1)
    hardwareManufacturer = prefs.getInt("hw_manufacturer", 0)
    menuFontSizeMode = prefs.getInt("menu_font_mode", 1)
    menuColorMode = prefs.getInt("menu_color_mode", 1) 
    statusFontSizeMode = prefs.getInt("status_font_mode", 1)
    isStartupHelpEnabled = prefs.getBoolean("startup_help_enabled", true)
    isStartupOverviewEnabled = prefs.getBoolean("startup_overview_enabled", true)
    isStatusHelpEnabled = prefs.getBoolean("status_help_enabled", true)
    isFontScanEnabled = prefs.getBoolean("font_scan_enabled", false)
    isHelpTaskSwitchEnabled = prefs.getBoolean("help_task_switch", true)
    isStatusMessagesSuppressed = prefs.getBoolean("status_messages_suppressed", false)
    isPersistentScreenshotEnabled = prefs.getBoolean("persistent_screenshot_enabled", false)
    isSystemClipboardSuppressed = prefs.getBoolean("system_clipboard_suppressed", true)
    isImageFadeEnabled = prefs.getBoolean("image_fade_enabled", true)
    isSettingsGlobal = prefs.getBoolean("settings_global", true)
    keyboardMode = TelexProcessor.KeyboardMode.values()[prefs.getInt("keyboard_mode", 0)]
    
    macroText = prefs.getString("macro_text", "") ?: ""

    val customEncoded = prefs.getString("custom_themes", "") ?: ""
    themes.clear()
    themes.addAll(baseThemes)
    if (customEncoded.isNotEmpty()) {
        customEncoded.split(";").forEach {
            val pts = it.split("|")
            if (pts.size == 3) themes.add(MainActivity.EditorTheme(pts[0], pts[1].toInt(), pts[2].toInt(), pts[1].toInt()))
        }
    }
}

internal fun MainActivity.enableImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}

internal fun MainActivity.takeScreenshot() {
    val rootView = window.decorView.rootView
    val bitmap = android.graphics.Bitmap.createBitmap(rootView.width, rootView.height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    rootView.draw(canvas)

    val filename = "DOSwriter_${System.currentTimeMillis()}.png"
    
    // Check if there is a local linked image folder to save to
    val currentImageFolder = imageFolderManager.currentFolder
    if (currentImageFolder != null && !currentImageFolder.isRemote) {
        try {
            if (currentImageFolder.path.startsWith("content://")) {
                val uri = Uri.parse(currentImageFolder.path)
                val dir = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)
                val file = dir?.createFile("image/png", filename)
                if (file != null) {
                    contentResolver.openOutputStream(file.uri)?.use { 
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                        showStatusToast("Screenshot saved to ${currentImageFolder.name}")
                    }
                    return
                }
            } else {
                val dir = java.io.File(currentImageFolder.path)
                if (dir.exists() || dir.mkdirs()) {
                    val file = java.io.File(dir, filename)
                    file.outputStream().use { 
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                        showStatusToast("Screenshot saved to ${currentImageFolder.name}")
                    }
                    return
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fallback to standard Pictures folder if no folder is linked or if link fails
    val contentValues = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
        }
    }

    val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
        contentResolver.openOutputStream(uri)?.use { 
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            showStatusToast("Screenshot saved to Pictures")
        }
    }
}

internal fun MainActivity.adjustBrightness(delta: Float) {
    hardwareProvider.adjustBrightness(delta, this)
    
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        if (android.provider.Settings.System.canWrite(this)) {
            try {
                val lp = window.attributes
                val newBrightness = (lp.screenBrightness * 255).toInt().coerceIn(1, 255)
                android.provider.Settings.System.putInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, newBrightness)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
