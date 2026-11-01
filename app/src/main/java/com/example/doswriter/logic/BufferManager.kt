package com.example.doswriter.logic

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.doswriter.model.EditorBuffer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BufferManager(private val context: Context, private val bufferCount: Int = 17) { // 8 Main + 8 To-Do + 1 Scratchpad
    private val buffers = Array(bufferCount) { i -> EditorBuffer(i + 1) }
    var currentBufferIndex = 0
        private set
    
    var isTodoWorkspace: Boolean = false
        private set

    var scratchLogFilename: String = "scratchlog.txt"
    var scratchCharLimit: Int = 280
    
    var syncHost: String = ""
    var syncPort: Int = 12345
    var syncToken: String = ""
    var isAutoSyncEnabled: Boolean = true
    var autoSyncIntervalMins: Int = 1 // 1-30 minutes
    var keyboardHeight: Int = 600
    var pdfFavoritesLimit: Int = 8
    var patternCollapseLimit: Int = 30000 // Default 30KB
    var largeFileWarningLimit: Int = 100000 // Default 100KB (0 = OFF)
    var scrollWheelMode: Int = 0 // 0: Lines, 1: Page Jump
    var scrollWheelLines: Int = 3 // 1, 2, 3, 5, 10 lines per notch
    var isLeftDragSelectMode: Boolean = true // true: Select Text, false: Pan Viewport
    var internalClipboard: String? = null
    
    var useThemeColorsForThumbnail: Boolean = false
    var overlayTextOnImage: Boolean = false
    var thumbnailTextColorMode: Int = 0 // 0: Auto, 1: Dark, 2: Light
    var useFilenameForThumbnailText: Boolean = false
    var globalThumbnailUri: String? = null
    
    // Scripting settings
    var scriptImageFolderUri: String? = null
    var isScriptEchoEnabled: Boolean = true
    var useWorkstationFileBrowser: Boolean = true
    var fileBrowserSortMode: Int = 0 // 0: NAME, 1: MOST RECENT
    val scriptHistory = mutableListOf<String>()

    init {
        val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isFirstRun = settings.getBoolean("first_run_complete", false)
        
        if (!isFirstRun) {
            wipeAllData()
            settings.edit().putBoolean("first_run_complete", true).apply()
        }

        loadBuffers()
        
        // Populate To-Do buffers from asset if it's the first run
        val isReallyFirstRun = !settings.getBoolean("todo_population_done", false)
        if (isReallyFirstRun) {
            populateTodoFromAssets()
            settings.edit().putBoolean("todo_population_done", true).apply()
        }
        
        val isThumbnailPopulated = settings.getBoolean("thumbnail_population_done", false)
        if (!isThumbnailPopulated) {
            populateThumbnailsFromAssets()
            settings.edit().putBoolean("thumbnail_population_done", true).apply()
        }
    }

    private fun populateThumbnailsFromAssets() {
        val haekel = listOf(
            "haekel01.png", "haekel02.png", "haekel03.png", "haekel04.png",
            "haekel05.png", "haekel06.png", "haekel07.png", "haekel08.png"
        )
        for (i in 0 until 8) {
            if (i < buffers.size) {
                buffers[i].thumbnailUri = "asset:///examples/thumbnails/haekel/${haekel[i]}"
                buffers[i].useThumbnail = true
                buffers[i].thumbnailPreference = 1 // 1: Image Priority
            }
        }
        saveBuffers()
    }

    private fun populateTodoFromAssets() {
        try {
            for (i in 0 until 8) {
                val bufferIdx = 8 + i // To-Do buffers start at 8
                val fileName = "PDA/todo${i + 1}.txt"
                try {
                    val content = context.assets.open("examples/$fileName").bufferedReader().use { it.readText() }
                    if (bufferIdx < buffers.size) {
                        buffers[bufferIdx].setText(content)
                        // Pre-populate filename as just the base name
                        buffers[bufferIdx].fileName = "todo${i + 1}.txt"
                    }
                } catch (e: Exception) {
                    // Fallback: use a generic name if specific file missing
                    if (bufferIdx < buffers.size) {
                        buffers[bufferIdx].fileName = "Buffer ${i + 1}"
                    }
                }
            }
            saveBuffers()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun wipeAllData() {
        val prefs = context.getSharedPreferences("buffers", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        settings.edit().clear().apply()
    }

    fun getCurrentBuffer(): EditorBuffer = buffers[getAbsoluteIndex(currentBufferIndex)]

    private fun getAbsoluteIndex(relativeIndex: Int): Int {
        if (relativeIndex == 8) return 16 // Scratchpad is always index 16
        return if (isTodoWorkspace) relativeIndex + 8 else relativeIndex
    }

    fun switchBuffer(index: Int): Boolean {
        if (index in 0 until 9) {
            currentBufferIndex = index
            return true
        }
        return false
    }
    
    fun toggleTodoWorkspace() {
        saveBuffers()
        isTodoWorkspace = !isTodoWorkspace
        val prefs = context.getSharedPreferences("buffers", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_todo_workspace", isTodoWorkspace).apply()
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
                putFloat("buffer_pdf_scroll_y_$i", buffers[i].pdfScrollY)
                putFloat("buffer_pdf_scale_$i", buffers[i].pdfScale)
                putFloat("buffer_pdf_pan_x_$i", buffers[i].pdfPanX)
                putFloat("buffer_margin_$i", buffers[i].sideMargin)
                putFloat("buffer_margin_tb_$i", buffers[i].topBottomMargin)
                putFloat("buffer_vertical_offset_$i", buffers[i].verticalOffset)
                putFloat("buffer_font_size_$i", buffers[i].fontSize)
                putFloat("buffer_line_spacing_$i", buffers[i].lineSpacing)
                putString("buffer_font_type_$i", buffers[i].fontType)
                putString("buffer_font_preset_$i", buffers[i].activePreset)
                putInt("buffer_device_profile_$i", buffers[i].deviceProfile)
                putString("buffer_custom_fonts_path_$i", buffers[i].customFontsPath)
                putBoolean("buffer_font_bold_$i", buffers[i].isBold)
                putInt("buffer_day_theme_$i", buffers[i].dayThemeIndex)
                putInt("buffer_night_theme_$i", buffers[i].nightThemeIndex)
                putString("buffer_bookmarks_$i", buffers[i].bookmarks.joinToString(","))
                putString("buffer_pdf_favorites_$i", buffers[i].pdfFavorites.joinToString(";") { "${it.pageIndex}|${it.identifier}" })
                putString("buffer_collapse_patterns_$i", buffers[i].collapsePatterns.joinToString(";"))
                putString("buffer_folds_$i", buffers[i].folds.joinToString(",") { "${it.start}:${it.end}" })
                putString("buffer_expanded_stack_$i", buffers[i].expandedFoldStack.joinToString(",") { "${it.start}:${it.end}" })
                putInt("buffer_char_limit_$i", buffers[i].charLimit)
                putBoolean("buffer_show_margins_$i", buffers[i].showMargins)
                putBoolean("buffer_markdown_mode_$i", buffers[i].isMarkdownMode)
                putBoolean("buffer_virtual_input_$i", buffers[i].useVirtualInput)
                putBoolean("buffer_show_numeric_row_$i", buffers[i].showNumericRow)
                putBoolean("buffer_keyboard_theme_bg_$i", buffers[i].keyboardThemeBg)
                putBoolean("buffer_show_key_border_$i", buffers[i].showKeyBorder)
                putInt("buffer_line_ending_symbol_$i", buffers[i].lineEndingSymbol)
                putInt("buffer_tab_size_$i", buffers[i].tabSize)
                putInt("buffer_page_length_$i", buffers[i].pageLength)
                putInt("buffer_keyboard_height_$i", buffers[i].keyboardHeight)
                putBoolean("buffer_auto_typewriter_$i", buffers[i].autoTypewriterOnLargeFiles)
                if (buffers[i].cursorColor != null) {
                    putInt("buffer_cursor_color_$i", buffers[i].cursorColor!!)
                } else {
                    remove("buffer_cursor_color_$i")
                }
                putString("buffer_sync_path_$i", buffers[i].syncFilePath)
                putString("buffer_thumbnail_uri_$i", buffers[i].thumbnailUri)
                putString("buffer_thumbnail_text_$i", buffers[i].thumbnailText)
                putBoolean("buffer_use_thumbnail_$i", buffers[i].useThumbnail)
                putInt("buffer_thumbnail_pref_$i", buffers[i].thumbnailPreference)
                putBoolean("buffer_is_dirty_$i", buffers[i].isDirty)
                putInt("buffer_cursor_$i", buffers[i].cursorPosition)
                putInt("buffer_anchor_$i", buffers[i].selectionAnchor)
                putString("buffer_cursor_style_$i", buffers[i].cursorSettings.style.name)
                putBoolean("buffer_cursor_blink_shortcut_$i", buffers[i].cursorSettings.blinkOnShortcut)
                putBoolean("buffer_cursor_blink_always_$i", buffers[i].cursorSettings.blinkAlways)
                putLong("buffer_cursor_interval_$i", buffers[i].cursorSettings.blinkIntervalMs)
                putLong("buffer_cursor_locator_duration_$i", buffers[i].cursorSettings.locatorBlinkDurationMs)
                
                // Typewriter Settings
                putBoolean("buffer_tw_enabled_$i", buffers[i].cursorSettings.typewriter.isEnabled)
                putBoolean("buffer_tw_fixed_$i", buffers[i].cursorSettings.typewriter.isFixed)
                putString("buffer_tw_anchor_$i", buffers[i].cursorSettings.typewriter.anchor.name)
                putBoolean("buffer_tw_guide_$i", buffers[i].cursorSettings.typewriter.showGuide)
                putLong("buffer_tw_delay_$i", buffers[i].cursorSettings.typewriter.charDelayMs)
                putLong("buffer_tw_crlf_delay_$i", buffers[i].cursorSettings.typewriter.crlfDelayMs)
                putBoolean("buffer_tw_terminal_$i", buffers[i].cursorSettings.typewriter.isTerminalMode)
                
                // Manuscript Settings
                putString("buffer_ms_size_$i", buffers[i].manuscriptSettings.pageSize)
                putBoolean("buffer_ms_portrait_$i", buffers[i].manuscriptSettings.isPortrait)
                putFloat("buffer_ms_font_$i", buffers[i].manuscriptSettings.fontSize)
                putFloat("buffer_ms_margin_$i", buffers[i].manuscriptSettings.margins)
                putBoolean("buffer_ms_para_collapse_$i", buffers[i].manuscriptSettings.isParagraphCollapseEnabled)
                putBoolean("buffer_ms_para_sentence_$i", buffers[i].manuscriptSettings.isFirstSentenceOnly)
                putInt("buffer_ms_layout_tiles_$i", buffers[i].manuscriptSettings.layoutTileCount)
            }
            putInt("current_index", currentBufferIndex)
            putBoolean("is_todo_workspace", isTodoWorkspace)
            putInt("scratch_limit", scratchCharLimit)
            putString("sync_host", syncHost)
            putInt("sync_port", syncPort)
            putString("sync_token", syncToken)
            putBoolean("sync_auto_enabled", isAutoSyncEnabled)
            putInt("sync_auto_interval", autoSyncIntervalMins)
            putInt("keyboard_height", keyboardHeight)
            putInt("pdf_favorites_limit", pdfFavoritesLimit)
            putInt("pattern_collapse_limit", patternCollapseLimit)
            putInt("large_file_warning_limit", largeFileWarningLimit)
            putInt("scroll_wheel_mode", scrollWheelMode)
            putInt("scroll_wheel_lines", scrollWheelLines)
            putBoolean("is_left_drag_select_mode", isLeftDragSelectMode)
            putBoolean("global_use_theme_colors_thumbnail", useThemeColorsForThumbnail)
            putBoolean("global_overlay_text_on_image", overlayTextOnImage)
            putInt("global_thumbnail_text_color_mode", thumbnailTextColorMode)
            putBoolean("global_use_filename_for_thumbnail_text", useFilenameForThumbnailText)
            putString("global_thumbnail_uri", globalThumbnailUri)
            putString("script_image_folder", scriptImageFolderUri)
            putBoolean("script_echo_enabled", isScriptEchoEnabled)
            putBoolean("use_workstation_browser", useWorkstationFileBrowser)
            putInt("file_browser_sort_mode", fileBrowserSortMode)
            putString("script_history", scriptHistory.joinToString("|||"))
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
        autoSyncIntervalMins = prefs.getInt("sync_auto_interval", 1).coerceIn(1, 30)
        keyboardHeight = prefs.getInt("keyboard_height", 600).coerceAtLeast(300)
        pdfFavoritesLimit = prefs.getInt("pdf_favorites_limit", 8)
        patternCollapseLimit = prefs.getInt("pattern_collapse_limit", 30000)
        largeFileWarningLimit = prefs.getInt("large_file_warning_limit", 100000)
        scrollWheelMode = prefs.getInt("scroll_wheel_mode", 0)
        scrollWheelLines = prefs.getInt("scroll_wheel_lines", 3)
        isLeftDragSelectMode = prefs.getBoolean("is_left_drag_select_mode", true)
        useThemeColorsForThumbnail = prefs.getBoolean("global_use_theme_colors_thumbnail", false)
        overlayTextOnImage = prefs.getBoolean("global_overlay_text_on_image", false)
        thumbnailTextColorMode = prefs.getInt("global_thumbnail_text_color_mode", 0)
        useFilenameForThumbnailText = prefs.getBoolean("global_use_filename_for_thumbnail_text", false)
        globalThumbnailUri = prefs.getString("global_thumbnail_uri", null)
        scriptImageFolderUri = prefs.getString("script_image_folder", null)
        isScriptEchoEnabled = prefs.getBoolean("script_echo_enabled", true)
        useWorkstationFileBrowser = prefs.getBoolean("use_workstation_browser", true)
        fileBrowserSortMode = prefs.getInt("file_browser_sort_mode", 0)
        
        val savedHistory = prefs.getString("script_history", "") ?: ""
        if (savedHistory.isNotEmpty()) {
            scriptHistory.clear()
            scriptHistory.addAll(savedHistory.split("|||"))
        }

        isTodoWorkspace = prefs.getBoolean("is_todo_workspace", false)

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
            buffers[i].pdfScrollY = prefs.getFloat("buffer_pdf_scroll_y_$i", 0f)
            buffers[i].pdfScale = prefs.getFloat("buffer_pdf_scale_$i", 1f)
            buffers[i].pdfPanX = prefs.getFloat("buffer_pdf_pan_x_$i", 0f)
            
            buffers[i].sideMargin = prefs.getFloat("buffer_margin_$i", 60f)
            buffers[i].topBottomMargin = prefs.getFloat("buffer_margin_tb_$i", 60f)
            buffers[i].verticalOffset = prefs.getFloat("buffer_vertical_offset_$i", 10f)
            buffers[i].fontSize = prefs.getFloat("buffer_font_size_$i", 48f)
            buffers[i].lineSpacing = prefs.getFloat("buffer_line_spacing_$i", 1.2f)
            buffers[i].fontType = prefs.getString("buffer_font_type_$i", "NotoSans-Bold") ?: "NotoSans-Bold"
            buffers[i].activePreset = prefs.getString("buffer_font_preset_$i", "Standard") ?: "Standard"
            buffers[i].deviceProfile = prefs.getInt("buffer_device_profile_$i", 0)
            buffers[i].customFontsPath = prefs.getString("buffer_custom_fonts_path_$i", null)
            buffers[i].isBold = prefs.getBoolean("buffer_font_bold_$i", false)
            buffers[i].dayThemeIndex = prefs.getInt("buffer_day_theme_$i", 3)
            buffers[i].nightThemeIndex = prefs.getInt("buffer_night_theme_$i", 3)
            buffers[i].charLimit = prefs.getInt("buffer_char_limit_$i", if (i == 16) scratchCharLimit else 0)
            buffers[i].showMargins = prefs.getBoolean("buffer_show_margins_$i", false)
            buffers[i].isMarkdownMode = prefs.getBoolean("buffer_markdown_mode_$i", false)
            buffers[i].useVirtualInput = prefs.getBoolean("buffer_virtual_input_$i", false)
            buffers[i].showNumericRow = prefs.getBoolean("buffer_show_numeric_row_$i", true)
            buffers[i].keyboardThemeBg = prefs.getBoolean("buffer_keyboard_theme_bg_$i", true)
            buffers[i].showKeyBorder = prefs.getBoolean("buffer_show_key_border_$i", true)
            buffers[i].lineEndingSymbol = prefs.getInt("buffer_line_ending_symbol_$i", 0)
            buffers[i].tabSize = prefs.getInt("buffer_tab_size_$i", 4)
            buffers[i].pageLength = prefs.getInt("buffer_page_length_$i", 20)
            buffers[i].keyboardHeight = prefs.getInt("buffer_keyboard_height_$i", 600)
            buffers[i].autoTypewriterOnLargeFiles = prefs.getBoolean("buffer_auto_typewriter_$i", false)
            if (prefs.contains("buffer_cursor_color_$i")) {
                buffers[i].cursorColor = prefs.getInt("buffer_cursor_color_$i", 0)
            } else {
                buffers[i].cursorColor = null
            }
            buffers[i].syncFilePath = prefs.getString("buffer_sync_path_$i", null)
            buffers[i].thumbnailUri = prefs.getString("buffer_thumbnail_uri_$i", null)
            buffers[i].thumbnailText = prefs.getString("buffer_thumbnail_text_$i", null)
            buffers[i].useThumbnail = prefs.getBoolean("buffer_use_thumbnail_$i", false)
            buffers[i].thumbnailPreference = prefs.getInt("buffer_thumbnail_pref_$i", 1)
            buffers[i].isDirty = prefs.getBoolean("buffer_is_dirty_$i", false)
            
            val bMarks = prefs.getString("buffer_bookmarks_$i", "") ?: ""
            if (bMarks.isNotEmpty()) {
                buffers[i].bookmarks.clear()
                buffers[i].bookmarks.addAll(bMarks.split(",").mapNotNull { it.toIntOrNull() })
            }

            val bFavs = prefs.getString("buffer_pdf_favorites_$i", "") ?: ""
            if (bFavs.isNotEmpty()) {
                buffers[i].pdfFavorites.clear()
                bFavs.split(";").forEach { pair ->
                    val pts = pair.split("|")
                    if (pts.size == 2) {
                        buffers[i].pdfFavorites.add(EditorBuffer.PdfFavorite(pts[0].toInt(), pts[1]))
                    }
                }
            }

            val bPatterns = prefs.getString("buffer_collapse_patterns_$i", "") ?: ""
            if (bPatterns.isNotEmpty()) {
                buffers[i].collapsePatterns.clear()
                buffers[i].collapsePatterns.addAll(bPatterns.split(";"))
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

            val bStack = prefs.getString("buffer_expanded_stack_$i", "") ?: ""
            if (bStack.isNotEmpty()) {
                buffers[i].expandedFoldStack.clear()
                bStack.split(",").forEach { pair ->
                    val pts = pair.split(":")
                    if (pts.size == 2) {
                        buffers[i].expandedFoldStack.add(EditorBuffer.Fold(pts[0].toInt(), pts[1].toInt()))
                    }
                }
            }
            
            val savedCursor = prefs.getInt("buffer_cursor_$i", buffers[i].prefix.length)
            val savedAnchor = prefs.getInt("buffer_anchor_$i", buffers[i].prefix.length)
            buffers[i].setSelection(savedAnchor, savedCursor)

            val styleName = prefs.getString("buffer_cursor_style_$i", "BLOCK") ?: "BLOCK"
            buffers[i].cursorSettings.style = try { EditorBuffer.CursorStyle.valueOf(styleName) } catch (e: Exception) { EditorBuffer.CursorStyle.BLOCK }
            buffers[i].cursorSettings.blinkOnShortcut = prefs.getBoolean("buffer_cursor_blink_shortcut_$i", true)
            buffers[i].cursorSettings.blinkAlways = prefs.getBoolean("buffer_cursor_blink_always_$i", false)
            buffers[i].cursorSettings.blinkIntervalMs = prefs.getLong("buffer_cursor_interval_$i", 500L)
            buffers[i].cursorSettings.locatorBlinkDurationMs = prefs.getLong("buffer_cursor_locator_duration_$i", 3000L)

            // Typewriter Settings
            buffers[i].cursorSettings.typewriter.isEnabled = prefs.getBoolean("buffer_tw_enabled_$i", false)
            buffers[i].cursorSettings.typewriter.isFixed = prefs.getBoolean("buffer_tw_fixed_$i", false)
            val twAnchorName = prefs.getString("buffer_tw_anchor_$i", "CENTER") ?: "CENTER"
            buffers[i].cursorSettings.typewriter.anchor = try { EditorBuffer.TypewriterAnchor.valueOf(twAnchorName) } catch(e: Exception) { EditorBuffer.TypewriterAnchor.CENTER }
            buffers[i].cursorSettings.typewriter.showGuide = prefs.getBoolean("buffer_tw_guide_$i", false)
            buffers[i].cursorSettings.typewriter.charDelayMs = prefs.getLong("buffer_tw_delay_$i", 100L)
            buffers[i].cursorSettings.typewriter.crlfDelayMs = prefs.getLong("buffer_tw_crlf_delay_$i", 100L)
            buffers[i].cursorSettings.typewriter.isTerminalMode = prefs.getBoolean("buffer_tw_terminal_$i", false)

            // Manuscript Settings
            buffers[i].manuscriptSettings.pageSize = prefs.getString("buffer_ms_size_$i", "4x6") ?: "4x6"
            buffers[i].manuscriptSettings.isPortrait = prefs.getBoolean("buffer_ms_portrait_$i", true)
            buffers[i].manuscriptSettings.fontSize = prefs.getFloat("buffer_ms_font_$i", 14f)
            buffers[i].manuscriptSettings.margins = prefs.getFloat("buffer_ms_margin_$i", 0.25f)
            buffers[i].manuscriptSettings.isParagraphCollapseEnabled = prefs.getBoolean("buffer_ms_para_collapse_$i", true)
            buffers[i].manuscriptSettings.isFirstSentenceOnly = prefs.getBoolean("buffer_ms_para_sentence_$i", true)
            buffers[i].manuscriptSettings.layoutTileCount = prefs.getInt("buffer_ms_layout_tiles_$i", 8)
        }
        currentBufferIndex = prefs.getInt("current_index", 0)
    }

    fun flushScratchpad(save: Boolean, folderUriStr: String? = null): Boolean {
        val scratch = buffers[16]
        val text = scratch.getCleanText()
        if (text.isEmpty()) return true

        if (save) {
            try {
                val timeStamp = SimpleDateFormat("ddMMMyyyy hh:mma", Locale.US).format(Date()).uppercase(Locale.US)
                val logAppend = "--" + timeStamp + "\n" + text + "\n"

                if (!folderUriStr.isNullOrEmpty()) {
                    val root = DocumentFile.fromTreeUri(context, Uri.parse(folderUriStr))
                    if (root != null && root.exists()) {
                        var file = root.findFile(scratchLogFilename)
                        if (file == null) {
                            file = root.createFile("text/plain", scratchLogFilename)
                        }
                        if (file != null) {
                            val os = context.contentResolver.openOutputStream(file.uri, "wa")
                            if (os != null) {
                                os.use { it.write(logAppend.toByteArray()) }
                            } else {
                                return false
                            }
                        } else {
                            return false
                        }
                    } else {
                        return false
                    }
                } else {
                    val file = File(context.getExternalFilesDir(null), scratchLogFilename)
                    file.appendText(logAppend)
                }
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
    
    fun getAllBuffers(): List<EditorBuffer> = buffers.toList()

    fun getBufferAbsolute(index: Int): EditorBuffer {
        val safeIndex = index.coerceIn(buffers.indices)
        return buffers[safeIndex]
    }

    fun getBufferAt(index: Int): EditorBuffer {
        if (index == 8) return buffers[16] // Explicit scratchpad access
        if (index in 0 until 8) {
            val abs = if (isTodoWorkspace) index + 8 else index
            return buffers[abs]
        }
        return getBufferAbsolute(index)
    }

    fun applyCurrentSettingsToAll() {
        val current = getCurrentBuffer()
        val currentKbHeight = keyboardHeight
        for (i in buffers.indices) {
            val b = buffers[i]
            b.sideMargin = current.sideMargin
            b.topBottomMargin = current.topBottomMargin
            b.verticalOffset = current.verticalOffset
            b.fontSize = current.fontSize
            b.lineSpacing = current.lineSpacing
            b.fontType = current.fontType
            b.activePreset = current.activePreset
            b.deviceProfile = current.deviceProfile
            b.isBold = current.isBold
            b.dayThemeIndex = current.dayThemeIndex
            b.nightThemeIndex = current.nightThemeIndex
            b.showMargins = current.showMargins
            b.keyboardThemeBg = current.keyboardThemeBg
            b.showKeyBorder = current.showKeyBorder
            b.cursorSettings.style = current.cursorSettings.style
            b.cursorSettings.blinkOnShortcut = current.cursorSettings.blinkOnShortcut
            b.cursorSettings.blinkAlways = current.cursorSettings.blinkAlways
            b.cursorSettings.blinkIntervalMs = current.cursorSettings.blinkIntervalMs
            b.keyboardHeight = currentKbHeight
            b.useVirtualInput = current.useVirtualInput
        }
        saveBuffers()
    }

    fun applyFontToAll() {
        val current = getCurrentBuffer()
        for (i in buffers.indices) {
            val b = buffers[i]
            b.fontSize = current.fontSize
            b.lineSpacing = current.lineSpacing
            b.fontType = current.fontType
            b.activePreset = current.activePreset
            b.isBold = current.isBold
        }
        saveBuffers()
    }

    fun applyMarginsToAll() {
        val current = getCurrentBuffer()
        for (i in buffers.indices) {
            val b = buffers[i]
            b.sideMargin = current.sideMargin
            b.topBottomMargin = current.topBottomMargin
            b.verticalOffset = current.verticalOffset
            b.showMargins = current.showMargins
        }
        saveBuffers()
    }

    fun applyThemeToAll() {
        val current = getCurrentBuffer()
        for (i in buffers.indices) {
            val b = buffers[i]
            b.dayThemeIndex = current.dayThemeIndex
            b.nightThemeIndex = current.nightThemeIndex
            b.keyboardThemeBg = current.keyboardThemeBg
            b.showKeyBorder = current.showKeyBorder
        }
        saveBuffers()
    }

    fun applyCursorToAll() {
        val current = getCurrentBuffer()
        for (i in buffers.indices) {
            val b = buffers[i]
            b.cursorSettings.style = current.cursorSettings.style
            b.cursorSettings.blinkOnShortcut = current.cursorSettings.blinkOnShortcut
            b.cursorSettings.blinkAlways = current.cursorSettings.blinkAlways
            b.cursorSettings.blinkIntervalMs = current.cursorSettings.blinkIntervalMs
            b.cursorSettings.locatorBlinkDurationMs = current.cursorSettings.locatorBlinkDurationMs
            
            b.cursorSettings.typewriter.isEnabled = current.cursorSettings.typewriter.isEnabled
            b.cursorSettings.typewriter.isFixed = current.cursorSettings.typewriter.isFixed
            b.cursorSettings.typewriter.anchor = current.cursorSettings.typewriter.anchor
            b.cursorSettings.typewriter.showGuide = current.cursorSettings.typewriter.showGuide

            b.cursorColor = current.cursorColor
            b.useVirtualInput = current.useVirtualInput
            b.autoTypewriterOnLargeFiles = current.autoTypewriterOnLargeFiles
        }
        saveBuffers()
    }

    fun applyPatternsToAll() {
        val current = getCurrentBuffer()
        val patterns = current.collapsePatterns.toList()
        for (i in buffers.indices) {
            val b = buffers[i]
            b.collapsePatterns.clear()
            b.collapsePatterns.addAll(patterns)
        }
        saveBuffers()
    }
}
