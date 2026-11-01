package com.example.doswriter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.PersistableBundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.doswriter.editor.*
import com.example.doswriter.logic.*
import com.example.doswriter.model.*
import com.example.doswriter.MainActivity

internal fun MainActivity.setupManagers() {
    val settings = getSharedPreferences("settings", Context.MODE_PRIVATE)
    if (!settings.contains("menu_color_mode")) {
        settings.edit().putInt("menu_color_mode", 1).apply()
    }

    loadGlobalSettings()
    bufferManager = BufferManager(this)
    manuscriptEngine = com.example.doswriter.logic.ManuscriptEngine(this)
    
    for (b in bufferManager.getAllBuffers()) {
        b.clearFolds()
        b.collapsePatterns.clear()
    }
    bufferManager.saveBuffers()

    outlineManager = OutlineManager(this)
    imageFolderManager = ImageFolderManager(this)
    workingFolderManager = WorkingFolderManager(this)
    recentFileManager = RecentFileManager(this)
    macroManager = MacroManager(this)
    syncManager = SyncManager(bufferManager) { event -> handleSyncEvent(event) }
    hardwareProvider = com.example.doswriter.logic.hal.HardwareFactory.getProvider(this, hardwareManufacturer)
    hardwareProvider.initialize(this)
    bufferManager.keyboardHeight = bufferManager.getCurrentBuffer().keyboardHeight
    commandDispatcher = CommandDispatcher(
        bufferManager,
        onBufferSwitched = { bufferNum ->
            val buffer = bufferManager.getCurrentBuffer()
            val name = buffer.fileName ?: "Empty"
            showStatusToast("Buffer $bufferNum: $name")
            
            suppressWatermark = false
            editorView.suppressWatermark = false
            editorView.setBuffer(buffer)
            
            updateVirtualKeyboardDetection()

            bufferManager.keyboardHeight = buffer.keyboardHeight
            if (isVirtualKeyboardEnabled) updateKeyboardHeight()

            applyTheme()
            updateStatus()
        },
        onContentChanged = {
            if (bufferManager.getCurrentBuffer().getCleanText().isNotEmpty()) {
                suppressWatermark = false
                editorView.suppressWatermark = false
            }
            editorView.invalidate()
            editorView.scrollToCursor()
            updateStatus()
            resetAutoSyncTimer()
            
            saveHandler.removeCallbacks(saveRunnable)
            saveHandler.postDelayed(saveRunnable, 5000) 
        },
        onFontSizeChanged = { increase ->
            if (helpOverlay.visibility == View.VISIBLE) {
                if (increase) {
                    menuFontSizeMode = (menuFontSizeMode + 1).coerceAtMost(3)
                } else {
                    menuFontSizeMode = (menuFontSizeMode - 1).coerceAtLeast(0)
                }
                saveGlobalSettings()
                updateMenuFontSizes()
            } else {
                val buffer = bufferManager.getCurrentBuffer()
                if (buffer.imageUri != null) {
                    val factor = if (increase) 0.1f else -0.1f
                    editorView.adjustImageZoom(factor)
                    if (isSplitActive && leftBufferIdx == rightBufferIdx) {
                        if (editorView == leftEditorView) rightEditorView.adjustImageZoom(factor, false)
                        else leftEditorView.adjustImageZoom(factor, false)
                    }
                    bufferManager.saveBuffers()
                } else if (buffer.pdfUri != null) {
                    val factor = if (increase) 0.1f else -0.1f
                    editorView.adjustPdfZoom(factor)
                    if (isSplitActive && leftBufferIdx == rightBufferIdx) {
                        if (editorView == leftEditorView) rightEditorView.adjustPdfZoom(factor, false)
                        else leftEditorView.adjustPdfZoom(factor, false)
                    }
                    bufferManager.saveBuffers()
                } else {
                    val currentSize = editorView.getFontSize()
                    val nextSize = if (increase) currentSize + 4f else (currentSize - 4f).coerceAtLeast(12f)
                    var allow = true
                    if (increase && buffer.id == 9) {
                        val metrics = editorView.estimateMetrics(nextSize)
                        val charsPerLine = ((editorView.width - 2 * buffer.sideMargin) / metrics.first).toInt().coerceAtLeast(1)
                        val totalLines = (buffer.charLimit / charsPerLine) + 5
                        val totalHeight = totalLines * metrics.second
                        val availableHeight = (editorView.height - 2 * buffer.topBottomMargin)
                        if (totalHeight > availableHeight) {
                            allow = false
                            Toast.makeText(this, "Font too large for Scratchpad limit", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (allow) {
                        editorView.setFontSize(nextSize)
                        if (isSettingsGlobal) {
                            bufferManager.applyFontToAll()
                            refreshActiveViews()
                        } else if (isSplitActive && leftBufferIdx == rightBufferIdx) {
                            if (editorView == leftEditorView) {
                                rightEditorView.setFontSize(nextSize)
                            } else {
                                leftEditorView.setFontSize(nextSize)
                            }
                        }
                        bufferManager.saveBuffers()
                    }
                }
            }
        },
        onThemeChanged = { forward ->
            val buffer = bufferManager.getCurrentBuffer()
            val isDay = isDaytime()
            val oldIdx = if (isDay) buffer.dayThemeIndex else buffer.nightThemeIndex
            
            val monoNames = listOf("Classic", "E-Ink", "1950s CRT", "Soft Paper Gray", "Cool Light Gray", "Mid Gray")
            val isMono = deviceType == 2

            var newIdx = oldIdx
            var found = false
            
            var safety = 0
            while (!found && safety < themes.size) {
                newIdx = if (forward) (newIdx + 1) % themes.size else (newIdx - 1 + themes.size) % themes.size
                val theme = themes[newIdx]
                if (!isMono || theme.name in monoNames) {
                    found = true
                }
                safety++
            }

            val theme = themes[newIdx]
            if (isDay) buffer.dayThemeIndex = newIdx else buffer.nightThemeIndex = newIdx
            
            if (isSettingsGlobal) {
                bufferManager.applyThemeToAll()
                val theme = themes[newIdx]
                val kbBg = if (buffer.keyboardThemeBg) theme.backgroundColor else theme.textColor
                val kbFg = if (buffer.keyboardThemeBg) theme.textColor else theme.backgroundColor
                internalKeyboardView.setThemeColors(kbFg, kbBg, theme.cursorColor)
                internalKeyboardView.showKeyBorder = buffer.showKeyBorder

                refreshActiveViews()
            } else {
                applyTheme()
            }
            
            val modeLabel = if (isDay) "Day" else "Night"
            showStatusToast("Theme ($modeLabel): ${theme.name}")
            bufferManager.saveBuffers()
        },
        onBoldChanged = { enabled ->
            val buffer = bufferManager.getCurrentBuffer()
            editorView.setFontBold(enabled)
            if (isSettingsGlobal) {
                bufferManager.applyFontToAll()
                refreshActiveViews()
            }
            bufferManager.saveBuffers()
            val status = if (enabled) "ON" else "OFF"
            showStatusToast("Bold: $status")
        },
        onOverlayAction = { cmd -> handleOverlayCommand(cmd) },
        onClipboardAction = { cmd -> handleClipboardCommand(cmd) },
        onFontCycle = { forward -> cycleFonts(forward) },
        onKeyboardModeChanged = { mode ->
            keyboardMode = com.example.doswriter.logic.TelexProcessor.KeyboardMode.values()[mode]
            saveGlobalSettings()
            showStatusToast("Keyboard: ${keyboardMode.name.replace("_", " ")}")
        }
    )
    commandScriptManager = CommandScriptManager(this, commandDispatcher, bufferManager).apply {
        textPreprocessor = { text -> processTelexText(text) }
        rootProvider = { workingFolderManager.getFolders().map { it.path } }
        listener = object : CommandScriptManager.Listener {
            override fun onScriptStatusChanged(instruction: String, progress: String, isPaused: Boolean) {
                if (instruction.startsWith("ERROR:")) {
                    Toast.makeText(this@setupManagers, instruction, Toast.LENGTH_LONG).show()
                }
                if (bufferManager.isScriptEchoEnabled) {
                    commandScriptOverlay.update(instruction, progress, isPaused)
                } else if (commandScriptOverlay.visibility == View.VISIBLE) {
                    commandScriptOverlay.hide()
                }
            }
            override fun onScriptFinished() {
                commandScriptOverlay.hide()
                showStatusToast("Script Finished")
            }
        }
    }
    
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.addPrimaryClipChangedListener {
        isInternalCopyLast = false
    }
}

internal fun MainActivity.setupUI() {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    }

    editorStack = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, 1f)
    }
    
    editorContainer = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            editorView.scrollToCursor()
        }
    }
    
    leftEditorView = EditorView(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        setBuffer(bufferManager.getCurrentBuffer())
        onCursorMoved = { updateStatus() }
        onRequestedShowKeyboard = { }
        onRightClick = { x, y -> showEditorContextMenu(x, y) }
        onNextImage = { moveViewer(1) }
        onPrevImage = { moveViewer(-1) }
        onScrollChanged = { 
            if (isSplitActive && leftBufferIdx == rightBufferIdx) {
                val buffer = bufferManager.getCurrentBuffer()
                val totalLines = visualLines.size
                val cursorLine = visualLines.indexOfFirst { buffer.cursorPosition >= it.start && buffer.cursorPosition <= it.end }.coerceAtLeast(0)
                
                if (cursorLine == totalLines - 1 && totalLines > 1) {
                    rightEditorView.scrollToBottom()
                } else {
                    rightEditorView.scrollYOffset = it
                }
            }
        }
    }
    
    rightEditorView = EditorView(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        visibility = View.GONE
        onCursorMoved = { updateStatus() }
        onRequestedShowKeyboard = { }
        onRightClick = { x, y -> showEditorContextMenu(x, y) }
        onNextImage = { moveViewer(1) }
        onPrevImage = { moveViewer(-1) }
    }
    
    editorView = leftEditorView
    
    editorContainer.addView(leftEditorView)
    editorContainer.addView(rightEditorView)
    editorStack.addView(editorContainer)
    
    statusText = TextView(this).apply { setTextColor(Color.GRAY); textSize = 14f; setPadding(20, 0, 20, 20) }
    editorStack.addView(statusText, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    
    fileBrowserOverlay = FileBrowserOverlay(this).apply { visibility = View.GONE }; editorStack.addView(fileBrowserOverlay)
    outlineOverlay = OutlineOverlay(this).apply { visibility = View.GONE }; editorStack.addView(outlineOverlay)
    manuscriptFilmstripOverlay = ManuscriptFilmstripOverlay(this).apply { 
        visibility = View.GONE
        onPageSelected = { charIndex ->
            val buffer = bufferManager.getCurrentBuffer()
            buffer.setCursor(buffer.prefix.length + charIndex, false)
            editorView.scrollToCursorTop()
            updateStatus()
        }
        onPdfRequested = {
            isManuscriptPdfMode = true
            isMiniBookMode = false
            createPdfLauncher.launch("manuscript_export.pdf")
        }
    }; editorStack.addView(manuscriptFilmstripOverlay)
    manuscriptLayoutOverlay = ManuscriptLayoutOverlay(this).apply {
        visibility = View.GONE
        onPageSelected = { charIndex ->
            val buffer = bufferManager.getCurrentBuffer()
            buffer.setCursor(buffer.prefix.length + charIndex, false)
            editorView.scrollToCursorTop()
            updateStatus()
        }
        onPdfRequested = {
            isManuscriptPdfMode = true
            isMiniBookMode = false
            createPdfLauncher.launch("manuscript_export.pdf")
        }
    }; editorStack.addView(manuscriptLayoutOverlay)
    
    workspaceLayoutOverlay = WorkspaceLayoutOverlay(this).apply {
        visibility = View.GONE
        onBufferSelected = { index ->
            commandDispatcher.dispatch(Command.SwitchBuffer(index))
        }
    }; editorStack.addView(workspaceLayoutOverlay)
    
    commandScriptOverlay = CommandScriptOverlay(this).apply {
        visibility = View.GONE
    }; editorStack.addView(commandScriptOverlay)

    scriptConsoleOverlay = ScriptConsoleOverlay(this).apply {
        visibility = View.GONE
    }; editorStack.addView(scriptConsoleOverlay)

    referenceOverlay = ReferenceOverlay(this).apply { visibility = View.GONE }; editorStack.addView(referenceOverlay)
    aboutOverlay = AboutOverlay(this).apply { visibility = View.GONE }; editorStack.addView(aboutOverlay)
    helpOverlay = HelpOverlay(this).apply { visibility = View.GONE }; editorStack.addView(helpOverlay)
    searchOverlay = SearchOverlay(this).apply { visibility = View.GONE }; editorStack.addView(searchOverlay)
    macroOverlay = MacroOverlay(this).apply { 
        visibility = View.GONE 
        onContextRequested = {
            showMacroContextPicker()
        }
    }; editorStack.addView(macroOverlay)
    multiSelectionOverlay = MultiSelectionOverlay(this).apply { visibility = View.GONE }; editorStack.addView(multiSelectionOverlay)
    selectionOverlay = SelectionOverlay(this).apply { visibility = View.GONE }; editorStack.addView(selectionOverlay)
    inputOverlay = InputOverlay(this).apply { visibility = View.GONE }; editorStack.addView(inputOverlay)
    anchorView = View(this).apply { layoutParams = FrameLayout.LayoutParams(1, 1); visibility = View.INVISIBLE }; editorStack.addView(anchorView)

    debugInfoTextView = TextView(this).apply {
        setTextColor(Color.YELLOW)
        setBackgroundColor(Color.argb(220, 0, 0, 0))
        textSize = 36f 
        setPadding(50, 50, 50, 50) 
        visibility = View.GONE
        typeface = Typeface.MONOSPACE
    }
    editorStack.addView(debugInfoTextView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))

    root.addView(editorStack)

    predictiveConsoleView = PredictiveConsoleView(this).apply {
        layoutParams = LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 240)
        visibility = View.GONE
        try {
            val words = assets.open("dict_en.txt").bufferedReader().use { it.readText() }.split(",")
            initDictionary(words)
        } catch (e: Exception) { e.printStackTrace() }
        onCopyClicked = { text ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("DOSwriter", text)
            if (Build.VERSION.SDK_INT >= 24) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this@setupUI, "Copied to system clipboard", Toast.LENGTH_SHORT).show()
        }
        onClearClicked = {
            updateComposing("")
        }
        onSuggestionSelected = { text ->
        }
    }
    root.addView(predictiveConsoleView)

    internalKeyboardView = KeyboardView(this).apply {
        layoutParams = LinearLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, bufferManager.keyboardHeight)
        visibility = View.GONE
        onKeyAction = { key -> handleInternalKey(key) }
    }
    loadKeyboardLayout("en_alpha.json")
    root.addView(internalKeyboardView)

    leftEditorView.isImageFadeEnabled = isImageFadeEnabled
    rightEditorView.isImageFadeEnabled = isImageFadeEnabled

    setContentView(root); loadStatusVisibility(); updateStatus(); applyTheme()
    
    if (deviceType == -1) {
        checkFirstRunDeviceType()
    }
}
