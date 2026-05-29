package com.example.minimaltexteditor.logic

import android.content.Context
import com.example.minimaltexteditor.model.EditorBuffer
import java.io.File

class BufferManager(private val context: Context, private val bufferCount: Int = 9) { // 8 standard + 1 scratchpad
    private val buffers = Array(bufferCount) { i -> EditorBuffer(i + 1) }
    var currentBufferIndex = 0
        private set
        
    var scratchLogFilename: String = "scratchlog.txt"
    var scratchCharLimit: Int = 280
    
    var syncHost: String = ""
    var syncPort: Int = 12345
    var syncToken: String = ""
    var isAutoSyncEnabled: Boolean = true

    init {
        val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isFirstRun = settings.getBoolean("first_run_complete", false)
        
        if (!isFirstRun) {
            wipeAllData()
            settings.edit().putBoolean("first_run_complete", true).apply()
        }

        loadBuffers()
    }

    fun wipeAllData() {
        val prefs = context.getSharedPreferences("buffers", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        settings.edit().clear().apply()
    }

    fun getCurrentBuffer(): EditorBuffer = buffers[currentBufferIndex]

    fun switchBuffer(index: Int): Boolean {
        if (index in 0 until bufferCount) {
            currentBufferIndex = index
            return true
        }
        return false
    }

    fun saveBuffers() {
        val prefs = context.getSharedPreferences("buffers", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            for (i in buffers.indices) {
                putString("buffer_$i", buffers[i].getCleanText())
                putString("buffer_name_$i", buffers[i].fileName)
                putString("buffer_uri_$i", buffers[i].fileUri)
                putString("buffer_image_uri_$i", buffers[i].imageUri)
                putFloat("buffer_image_scale_$i", buffers[i].imageScale)
                putFloat("buffer_image_pan_x_$i", buffers[i].imagePanX)
                putFloat("buffer_image_pan_y_$i", buffers[i].imagePanY)
                putString("buffer_pdf_uri_$i", buffers[i].pdfUri)
                putFloat("buffer_margin_$i", buffers[i].sideMargin)
                putFloat("buffer_margin_tb_$i", buffers[i].topBottomMargin)
                putFloat("buffer_vertical_offset_$i", buffers[i].verticalOffset)
                putFloat("buffer_font_size_$i", buffers[i].fontSize)
                putFloat("buffer_line_spacing_$i", buffers[i].lineSpacing)
                putString("buffer_font_type_$i", buffers[i].fontType)
                putInt("buffer_day_theme_$i", buffers[i].dayThemeIndex)
                putInt("buffer_night_theme_$i", buffers[i].nightThemeIndex)
                putString("buffer_bookmarks_$i", buffers[i].bookmarks.joinToString(","))
                putString("buffer_folds_$i", buffers[i].folds.joinToString(",") { "${it.start}:${it.end}" })
                putInt("buffer_char_limit_$i", buffers[i].charLimit)
                putBoolean("buffer_show_margins_$i", buffers[i].showMargins)
                putBoolean("buffer_markdown_mode_$i", buffers[i].isMarkdownMode)
                putBoolean("buffer_virtual_input_$i", buffers[i].useVirtualInput)
                putString("buffer_sync_path_$i", buffers[i].syncFilePath)
                putInt("buffer_cursor_$i", buffers[i].cursorPosition)
                putInt("buffer_anchor_$i", buffers[i].selectionAnchor)
                putString("buffer_cursor_style_$i", buffers[i].cursorSettings.style.name)
                putBoolean("buffer_cursor_blink_$i", buffers[i].cursorSettings.blinkEnabled)
                putLong("buffer_cursor_interval_$i", buffers[i].cursorSettings.blinkIntervalMs)
            }
            putInt("current_index", currentBufferIndex)
            putInt("scratch_limit", scratchCharLimit)
            putString("sync_host", syncHost)
            putInt("sync_port", syncPort)
            putString("sync_token", syncToken)
            putBoolean("sync_auto_enabled", isAutoSyncEnabled)
            apply()
        }
    }

    private fun loadBuffers() {
        val prefs = context.getSharedPreferences("buffers", Context.MODE_PRIVATE)
        scratchLogFilename = prefs.getString("scratch_log_name", "scratchlog.txt") ?: "scratchlog.txt"
        scratchCharLimit = prefs.getInt("scratch_limit", 280)
        syncHost = prefs.getString("sync_host", "") ?: ""
        syncPort = prefs.getInt("sync_port", 12345)
        syncToken = prefs.getString("sync_token", "") ?: ""
        isAutoSyncEnabled = prefs.getBoolean("sync_auto_enabled", true)

        for (i in buffers.indices) {
            val text = prefs.getString("buffer_$i", "") ?: ""
            buffers[i].setText(text)
            buffers[i].fileName = prefs.getString("buffer_name_$i", null)
            buffers[i].fileUri = prefs.getString("buffer_uri_$i", null)
            buffers[i].imageUri = prefs.getString("buffer_image_uri_$i", null)
            buffers[i].imageScale = prefs.getFloat("buffer_image_scale_$i", 1f)
            buffers[i].imagePanX = prefs.getFloat("buffer_image_pan_x_$i", 0f)
            buffers[i].imagePanY = prefs.getFloat("buffer_image_pan_y_$i", 0f)
            buffers[i].pdfUri = prefs.getString("buffer_pdf_uri_$i", null)
            
            // Apply requested new user defaults: 60px margins and Wheat theme (index 3)
            buffers[i].sideMargin = prefs.getFloat("buffer_margin_$i", 60f)
            buffers[i].topBottomMargin = prefs.getFloat("buffer_margin_tb_$i", 60f)
            buffers[i].verticalOffset = prefs.getFloat("buffer_vertical_offset_$i", 0f)
            buffers[i].fontSize = prefs.getFloat("buffer_font_size_$i", 48f)
            buffers[i].lineSpacing = prefs.getFloat("buffer_line_spacing_$i", 1.2f)
            buffers[i].fontType = prefs.getString("buffer_font_type_$i", "MONOSPACE") ?: "MONOSPACE"
            buffers[i].dayThemeIndex = prefs.getInt("buffer_day_theme_$i", 3)
            buffers[i].nightThemeIndex = prefs.getInt("buffer_night_theme_$i", 3)
            buffers[i].charLimit = prefs.getInt("buffer_char_limit_$i", if (i == 8) scratchCharLimit else 0)
            buffers[i].showMargins = prefs.getBoolean("buffer_show_margins_$i", false)
            buffers[i].isMarkdownMode = prefs.getBoolean("buffer_markdown_mode_$i", false)
            buffers[i].useVirtualInput = prefs.getBoolean("buffer_virtual_input_$i", false)
            buffers[i].syncFilePath = prefs.getString("buffer_sync_path_$i", null)
            
            val bMarks = prefs.getString("buffer_bookmarks_$i", "") ?: ""
            if (bMarks.isNotEmpty()) {
                buffers[i].bookmarks.clear()
                buffers[i].bookmarks.addAll(bMarks.split(",").mapNotNull { it.toIntOrNull() })
            }

            val bFolds = prefs.getString("buffer_folds_$i", "") ?: ""
            if (bFolds.isNotEmpty()) {
                buffers[i].folds.clear()
                bFolds.split(",").forEach { pair ->
                    val pts = pair.split(":")
                    if (pts.size == 2) {
                        buffers[i].folds.add(EditorBuffer.Fold(pts[0].toInt(), pts[1].toInt()))
                    }
                }
            }
            
            val savedCursor = prefs.getInt("buffer_cursor_$i", buffers[i].prefix.length)
            val savedAnchor = prefs.getInt("buffer_anchor_$i", buffers[i].prefix.length)
            buffers[i].setSelection(savedAnchor, savedCursor)

            val styleName = prefs.getString("buffer_cursor_style_$i", "THIN_CARET") ?: "THIN_CARET"
            buffers[i].cursorSettings.style = try { EditorBuffer.CursorStyle.valueOf(styleName) } catch (e: Exception) { EditorBuffer.CursorStyle.THIN_CARET }
            buffers[i].cursorSettings.blinkEnabled = prefs.getBoolean("buffer_cursor_blink_$i", true)
            buffers[i].cursorSettings.blinkIntervalMs = prefs.getLong("buffer_cursor_interval_$i", 500L)
        }
        currentBufferIndex = prefs.getInt("current_index", 0)
    }

    fun flushScratchpad(save: Boolean): Boolean {
        val scratch = buffers[8]
        val text = scratch.getCleanText()
        if (text.isEmpty()) return true

        if (save) {
            try {
                val file = File(context.getExternalFilesDir(null), scratchLogFilename)
                file.appendText(text + "\n---\n")
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
        scratch.clear()
        saveBuffers()
        return true
    }

    fun setBufferFile(uri: String, name: String) {
        val buffer = getCurrentBuffer()
        buffer.fileUri = uri
        buffer.fileName = name
        saveBuffers()
    }

    fun getBufferCount(): Int = bufferCount
    fun getStandardBufferCount(): Int = 8
    
    fun getBufferAt(index: Int): EditorBuffer = buffers[index]

    fun applyCurrentSettingsToAll() {
        val current = getCurrentBuffer()
        for (i in buffers.indices) { // Apply to ALL including scratchpad (index 8)
            if (i == currentBufferIndex) continue
            val b = buffers[i]
            b.sideMargin = current.sideMargin
            b.topBottomMargin = current.topBottomMargin
            b.verticalOffset = current.verticalOffset
            b.fontSize = current.fontSize
            b.lineSpacing = current.lineSpacing
            b.fontType = current.fontType
            b.dayThemeIndex = current.dayThemeIndex
            b.nightThemeIndex = current.nightThemeIndex
            b.showMargins = current.showMargins
            b.cursorSettings.style = current.cursorSettings.style
            b.cursorSettings.blinkEnabled = current.cursorSettings.blinkEnabled
            b.cursorSettings.blinkIntervalMs = current.cursorSettings.blinkIntervalMs
        }
        saveBuffers()
    }
}
