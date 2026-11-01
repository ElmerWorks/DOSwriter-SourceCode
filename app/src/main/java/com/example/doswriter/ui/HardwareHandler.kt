package com.example.doswriter.ui

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.example.doswriter.MainActivity
import com.example.doswriter.logic.Command
import com.example.doswriter.editor.KeyDef
import com.example.doswriter.editor.LayoutDef
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

internal fun MainActivity.checkFirstRunDeviceType() {
    wizardStep1DisplayType()
}

internal fun MainActivity.saveKeyboardConfig(name: String) {
    val config = mapOf(
        "height" to bufferManager.keyboardHeight,
        "tag" to internalKeyboardView.keyboardTag
    )
    val json = Gson().toJson(config)
    val file = java.io.File(getExternalFilesDir(null), "kb_config_$name.json")
    file.writeText(json)
    showStatusToast("Keyboard config '$name' saved")
}

internal fun MainActivity.loadKeyboardConfig(name: String) {
    try {
        val file = java.io.File(getExternalFilesDir(null), "kb_config_$name.json")
        if (!file.exists()) {
            showStatusToast("Config '$name' not found")
            return
        }
        val json = file.readText()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val config: Map<String, Any> = Gson().fromJson(json, type)

        bufferManager.keyboardHeight = (config["height"] as Double).toInt()
        val tag = config["tag"] as? String ?: "alpha"
        loadKeyboardLayout("en_$tag.json")

        updateKeyboardHeight()
        showStatusToast("Keyboard config '$name' loaded")
    } catch (e: Exception) {
        showStatusToast("Error loading config: ${e.message}")
    }
}

internal fun MainActivity.showLoadKeyboardConfigMenu() {
    val dir = getExternalFilesDir(null)
    val files = dir?.listFiles { _, name -> name.startsWith("kb_config_") && name.endsWith(".json") }
    if (files.isNullOrEmpty()) {
        showStatusToast("No saved keyboard configs found")
        showVirtualKeyboardSettingsMenu()
        return
    }
    val options = files.map { it.name.removePrefix("kb_config_").removeSuffix(".json") }
    selectionOverlay.show("Load Keyboard Config", options, 0, { index ->
        loadKeyboardConfig(options[index])
        showVirtualKeyboardSettingsMenu()
    }, { showVirtualKeyboardSettingsMenu() })
}

internal fun MainActivity.applyKeyboardSettingsGlobally() {
    val currentBuffer = bufferManager.getCurrentBuffer()
    for (b in bufferManager.getAllBuffers()) {
        b.showNumericRow = currentBuffer.showNumericRow
        b.keyboardThemeBg = currentBuffer.keyboardThemeBg
        b.showKeyBorder = currentBuffer.showKeyBorder
        b.keyboardHeight = bufferManager.keyboardHeight
    }
    bufferManager.saveBuffers()
    showStatusToast("Keyboard settings applied to all buffers")
}

internal fun MainActivity.runTypewriterDemo() {
    val buffer = bufferManager.getCurrentBuffer()
    val text = buffer.getCleanText()
    if (text.isEmpty()) {
        Toast.makeText(this, "Buffer is empty", Toast.LENGTH_SHORT).show()
        return
    }

    isDemoRunning = true
    isDemoCrlfActive = false
    demoCrlfStep = 0
    demoTargetView = editorView
    editorView.isDemoRunning = true
    demoOriginalText = text
    wasTypewriterOnBeforeDemo = buffer.cursorSettings.typewriter.isEnabled

    // 1. Clear buffer
    demoText = text
    demoIndex = 0
    buffer.clear()
    
    // 2. Setup Typewriter mode at current buffer settings
    if (buffer.cursorSettings.typewriter.isTerminalMode) {
        // Terminal mode: Start at top, scroll to 0
        editorView.scrollYOffset = 0f
        editorView.scrollToTop()
    } else {
        // Force typewriter on for standard demo
        buffer.cursorSettings.typewriter.isEnabled = true
        editorView.recenterTypewriter()
    }
    
    // 3. Start typing simulation
    demoHandler.removeCallbacks(demoRunnable)
    demoHandler.postDelayed(demoRunnable, 500)
    
    showStatusToast("Typewriter Mode Demo. Press Esc to quit.", Toast.LENGTH_LONG)
}

internal fun MainActivity.stopTypewriterDemo() {
    if (!isDemoRunning) return
    
    val targetView = demoTargetView ?: editorView
    val buffer = targetView.activeBuffer ?: bufferManager.getCurrentBuffer()
    
    isDemoRunning = false
    isDemoCrlfActive = false
    demoCrlfStep = 0
    targetView.isDemoRunning = false
    demoTargetView = null
    demoHandler.removeCallbacks(demoRunnable)
    
    // Restore original state
    buffer.clear()
    buffer.insert(demoOriginalText)
    
    // Restore enabled state
    buffer.cursorSettings.typewriter.isEnabled = wasTypewriterOnBeforeDemo
    
    targetView.invalidate()
    updateStatus()
    
    val status = if (targetView.isTypewriterActive()) "ON" else "OFF"
    showStatusToast("Demo Stopped. Typewriter Mode: $status")
}

internal fun MainActivity.updateVirtualKeyboardDetection() {
    val hasHardKeyboard = resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS
    Log.d("KeyboardView", "Hard keyboard detected: $hasHardKeyboard")
    
    if (hasHardKeyboard) {
        // Force OFF for ALL buffers if a hardware keyboard is detected
        if (isVirtualKeyboardEnabled) {
            isVirtualKeyboardEnabled = false
            for (b in bufferManager.getAllBuffers()) {
                b.useVirtualInput = false
            }
            bufferManager.saveBuffers()
        }
    } else {
        // No hardware keyboard: use current buffer's setting, but default to ON
        // if we're on a touch device and it was somehow disabled.
        val currentBuffer = bufferManager.getCurrentBuffer()
        if (!currentBuffer.useVirtualInput) {
            currentBuffer.useVirtualInput = true
            bufferManager.saveBuffers()
        }
        isVirtualKeyboardEnabled = true
    }

    Log.d("KeyboardView", "Setting internal keyboard visibility: ${if (isVirtualKeyboardEnabled) "VISIBLE" else "GONE"}")
    internalKeyboardView.visibility = if (isVirtualKeyboardEnabled) View.VISIBLE else View.GONE
    if (isVirtualKeyboardEnabled) {
        updateKeyboardHeight()
        internalKeyboardView.invalidate()
    }
}

internal fun MainActivity.toggleVirtualKeyboard() {
    val newState = !isVirtualKeyboardEnabled
    
    // Manual toggle applies to ALL buffers to maintain consistency
    for (b in bufferManager.getAllBuffers()) {
        b.useVirtualInput = newState
    }
    
    isVirtualKeyboardEnabled = newState
    internalKeyboardView.visibility = if (isVirtualKeyboardEnabled) View.VISIBLE else View.GONE
    
    if (isVirtualKeyboardEnabled) {
        updateKeyboardHeight()
    } else {
        hideSystemKeyboard()
    }

    val status = if (isVirtualKeyboardEnabled) "ON" else "OFF"
    Toast.makeText(this, "DOSwriter Keyboard: $status", Toast.LENGTH_SHORT).show()
    bufferManager.saveBuffers()
}

internal fun MainActivity.togglePredictiveText() {
    isPredictiveModeEnabled = !isPredictiveModeEnabled
    predictiveConsoleView.visibility = if (isPredictiveModeEnabled) View.VISIBLE else View.GONE
    if (!isPredictiveModeEnabled) predictiveConsoleView.updateComposing("")
    val status = if (isPredictiveModeEnabled) "ON" else "OFF"
    Toast.makeText(this, "Predictive Text: $status", Toast.LENGTH_SHORT).show()
}

internal fun MainActivity.commitPredictiveText() {
    val text = predictiveConsoleView.composingText.toString()
    if (text.isNotEmpty()) {
        bufferManager.getCurrentBuffer().insert(text)
        predictiveConsoleView.updateComposing("")
        editorView.invalidate()
        updateStatus()
    } else {
        commandDispatcher.dispatch(Command.InsertText("\n"))
    }
}

internal fun MainActivity.updateKeyboardHeight() {
    val height = bufferManager.keyboardHeight.coerceAtLeast(300)
    Log.d("KeyboardView", "Updating keyboard height to: $height")
    val params = internalKeyboardView.layoutParams
    params.height = height
    internalKeyboardView.layoutParams = params
}

internal fun MainActivity.adjustKeyboardSize(delta: Int) {
    bufferManager.keyboardHeight = (bufferManager.keyboardHeight + delta).coerceIn(300, 1200)
    updateKeyboardHeight()
    bufferManager.saveBuffers()
}

internal fun MainActivity.hideSystemKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(editorView.windowToken, 0)
}

internal fun MainActivity.resetKeyboardModifiers() {
    isVKeyDown = false
    internalKeyboardView.shiftActive = false
    internalKeyboardView.ctrlActive = isCtrlLocked
    internalKeyboardView.altActive = false
    internalKeyboardView.invalidate()
}

internal fun MainActivity.loadKeyboardLayout(filename: String) {
    try {
        Log.d("KeyboardView", "Loading keyboard layout: $filename")
        val json = assets.open("layouts/$filename").bufferedReader().use { it.readText() }
        val type = object : TypeToken<LayoutDef>() {}.type
        var layoutDef: LayoutDef = Gson().fromJson(json, type)
        
        Log.d("KeyboardView", "Layout loaded: ${layoutDef.layout_name}, Rows: ${layoutDef.rows.size}")

        // Filter Numeric Row if disabled (Only on Layer 1 Alpha)
        if (filename == "en_alpha.json" && !bufferManager.getCurrentBuffer().showNumericRow) {
            val filteredRows = layoutDef.rows.drop(1)
            layoutDef = layoutDef.copy(rows = filteredRows)
            Log.d("KeyboardView", "Filtered numeric row. New rows: ${layoutDef.rows.size}")
        }
        
        internalKeyboardView.loadLayoutDef(layoutDef)
        internalKeyboardView.keyboardTag = filename.substringAfter("_").substringBefore(".")
    } catch (e: Exception) { 
        Log.e("KeyboardView", "Error loading keyboard: ${e.message}", e)
        Toast.makeText(this, "Keyboard Load Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

internal fun MainActivity.isDirectional(cmd: String): Boolean = cmd in listOf("UP", "DOWN", "LEFT", "RIGHT", "DELETE", "DELETE_FORWARD")

internal fun MainActivity.handleDirectionalKey(keyCode: Int, isShift: Boolean, isCtrl: Boolean, isAlt: Boolean) {
    val meta = (if (isShift) KeyEvent.META_SHIFT_ON else 0) or (if (isCtrl) KeyEvent.META_CTRL_ON else 0) or (if (isAlt) KeyEvent.META_ALT_ON else 0)
    val event = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, keyCode, 0, meta)
    val cmd = keybindingManager.getCommand(
        event, 
        selectionOverlay.visibility == View.VISIBLE, 
        inputOverlay.visibility == View.VISIBLE, 
        helpOverlay.visibility == View.VISIBLE, 
        searchOverlay.visibility == View.VISIBLE, 
        multiSelectionOverlay.visibility == View.VISIBLE, 
        outlineOverlay.visibility == View.VISIBLE,
        macroOverlay.visibility == View.VISIBLE,
        false, 
        false,
        false,
        isPersistentScreenshotEnabled,
        commandScriptManager.isScriptRunning(),
        fileBrowserOverlay.visibility == View.VISIBLE
    )
    cmd?.let { commandDispatcher.dispatch(it) }
}

internal fun MainActivity.handleInternalKey(key: KeyDef) {
    if (key.type == "MODIFIER") return

    val buffer = bufferManager.getCurrentBuffer()
    val isShift = internalKeyboardView.shiftActive
    val isCtrl = internalKeyboardView.ctrlActive || isCtrlLocked
    val isAlt = internalKeyboardView.altActive
    val isCaps = internalKeyboardView.capsLockActive

    val isOverlayActive = selectionOverlay.visibility == View.VISIBLE ||
            inputOverlay.visibility == View.VISIBLE ||
            helpOverlay.visibility == View.VISIBLE ||
            searchOverlay.visibility == View.VISIBLE ||
            macroOverlay.visibility == View.VISIBLE ||
            multiSelectionOverlay.visibility == View.VISIBLE ||
            outlineOverlay.visibility == View.VISIBLE ||
            referenceOverlay.visibility == View.VISIBLE ||
            aboutOverlay.visibility == View.VISIBLE ||
            manuscriptFilmstripOverlay.visibility == View.VISIBLE ||
            manuscriptLayoutOverlay.visibility == View.VISIBLE ||
            workspaceLayoutOverlay.visibility == View.VISIBLE ||
            fileBrowserOverlay.visibility == View.VISIBLE

    // 1. Resolve Active Component (Cmd or Value)
    var activeCmd: String? = null
    var activeValue: String? = null

    when {
        isCtrl -> { activeCmd = key.ctrl_cmd; activeValue = key.ctrl_val }
        isAlt -> { activeCmd = key.alt_cmd; activeValue = key.alt_val }
        isShift -> { activeCmd = key.shift_cmd; activeValue = key.shift_val }
    }
    
    val isModifierHeld = isCtrl || isAlt
    
    // Fallback to primary if modifier didn't have a specific mapping
    if (activeCmd == null && activeValue == null) {
        activeCmd = key.cmd
        activeValue = key.value
    }

    // Handle Caps Lock and Shift for characters
    if (activeValue != null && activeValue.length == 1) {
        val shouldUpper = isShift xor isCaps
        if (shouldUpper) activeValue = activeValue.uppercase(Locale.US)
    }

    // 2. Event Emulation for Overlays and Chords
    val isControlChar = activeValue in listOf("\n", "\t")
    
    if (isModifierHeld || isControlChar || activeCmd != null || isOverlayActive) {
        if (manuscriptFilmstripOverlay.visibility == View.VISIBLE) {
            // Key handled by overlay if needed
        }
        val keyCode = when {
            activeValue == "\n" -> KeyEvent.KEYCODE_ENTER
            activeValue == "\t" -> KeyEvent.KEYCODE_TAB
            activeCmd == "DELETE" -> KeyEvent.KEYCODE_DEL
            activeCmd == "DELETE_FORWARD" -> KeyEvent.KEYCODE_FORWARD_DEL
            activeCmd == "EXIT_SPLIT" -> KeyEvent.KEYCODE_ESCAPE
            activeCmd == "UP" -> KeyEvent.KEYCODE_DPAD_UP
            activeCmd == "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
            activeCmd == "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
            activeCmd == "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
            
            // Explicit Alphanumeric Command Mappings for KeybindingManager
            activeCmd == "SAVE" || activeCmd == "SPLIT" -> KeyEvent.KEYCODE_S
            activeCmd == "OPEN" || activeCmd == "RECENT" -> KeyEvent.KEYCODE_O
            activeCmd == "BUFFERS" -> KeyEvent.KEYCODE_B
            activeCmd == "SYNC" -> KeyEvent.KEYCODE_D
            activeCmd == "CLOSE" -> KeyEvent.KEYCODE_W
            activeCmd == "REPLACE" || activeCmd == "RENDER" -> KeyEvent.KEYCODE_R
            activeCmd == "FIND" -> KeyEvent.KEYCODE_F
            activeCmd == "TYPEWRITER" || activeCmd == "THEME" -> KeyEvent.KEYCODE_T
            activeCmd == "HELP" || activeCmd == "STATUS" -> KeyEvent.KEYCODE_H
            activeCmd == "SETTINGS" -> KeyEvent.KEYCODE_L
            activeCmd == "PageUp" -> KeyEvent.KEYCODE_F
            activeCmd == "PageDown" -> KeyEvent.KEYCODE_G
            activeCmd == "SCREENSHOT" -> KeyEvent.KEYCODE_I
            activeCmd == "PDF_EXPORT" -> KeyEvent.KEYCODE_P
            activeCmd == "NEW" || activeCmd == "FOCUS" -> KeyEvent.KEYCODE_N
            activeCmd == "CUT" || activeCmd == "EXIT" -> KeyEvent.KEYCODE_X
            activeCmd == "COPY" || activeCmd == "CENTER" -> KeyEvent.KEYCODE_C
            activeCmd == "PASTE" -> KeyEvent.KEYCODE_V
            activeCmd == "UNDO" || activeCmd == "REDO" -> KeyEvent.KEYCODE_Z
            activeCmd == "SELECT_ALL" -> KeyEvent.KEYCODE_A
            activeCmd == "MARGINS" -> KeyEvent.KEYCODE_M
            activeCmd == "BOOKMARK" || activeCmd == "V_KBD" -> KeyEvent.KEYCODE_K
            activeCmd == "OUTLINE" || activeCmd == "OUTLINE_SETUP" -> KeyEvent.KEYCODE_F12
            activeCmd == "SWITCH_B1" -> KeyEvent.KEYCODE_F1
            activeCmd == "SWITCH_B2" -> KeyEvent.KEYCODE_F2
            activeCmd == "SWITCH_B3" -> KeyEvent.KEYCODE_F3
            activeCmd == "SWITCH_B4" -> KeyEvent.KEYCODE_F4
            activeCmd == "SWITCH_B5" -> KeyEvent.KEYCODE_F5
            activeCmd == "SWITCH_B6" -> KeyEvent.KEYCODE_F6
            activeCmd == "SWITCH_B7" -> KeyEvent.KEYCODE_F7
            activeCmd == "SWITCH_B8" -> KeyEvent.KEYCODE_F8
            activeCmd == "SWITCH_B9" -> KeyEvent.KEYCODE_F9

            activeValue != null && activeValue.length == 1 -> {
                val char = activeValue[0].lowercaseChar()
                when (char) {
                    in 'a'..'z' -> KeyEvent.KEYCODE_A + (char - 'a')
                    in '0'..'9' -> KeyEvent.KEYCODE_0 + (char - '0')
                    else -> 0
                }
            }
            else -> 0
        }

        if (keyCode != 0) {
            val meta = (if (isShift) KeyEvent.META_SHIFT_ON else 0) or (if (isCtrl) KeyEvent.META_CTRL_ON else 0) or (if (isAlt) KeyEvent.META_ALT_ON else 0)
            val event = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, keyCode, 0, meta)
            
            // --- WORKSTATION INTERCEPTORS (Virtual Keyboard) ---
            if (manuscriptFilmstripOverlay.visibility == View.VISIBLE) {
                if (manuscriptFilmstripOverlay.handleKey(keyCode, event)) { resetKeyboardModifiers(); return }
            }
            if (manuscriptLayoutOverlay.visibility == View.VISIBLE) {
                if (manuscriptLayoutOverlay.handleKey(keyCode, event)) { resetKeyboardModifiers(); return }
            }
            if (workspaceLayoutOverlay.visibility == View.VISIBLE) {
                if (workspaceLayoutOverlay.handleKey(keyCode, event)) { resetKeyboardModifiers(); return }
            }
            if (fileBrowserOverlay.visibility == View.VISIBLE) {
                if (fileBrowserOverlay.handleKey(keyCode, event)) { resetKeyboardModifiers(); return }
            }

            val cmd = keybindingManager.getCommand(
                event, 
                selectionOverlay.visibility == View.VISIBLE, 
                inputOverlay.visibility == View.VISIBLE, 
                helpOverlay.visibility == View.VISIBLE, 
                searchOverlay.visibility == View.VISIBLE, 
                multiSelectionOverlay.visibility == View.VISIBLE, 
                outlineOverlay.visibility == View.VISIBLE || referenceOverlay.visibility == View.VISIBLE || aboutOverlay.visibility == View.VISIBLE || manuscriptFilmstripOverlay.visibility == View.VISIBLE || manuscriptLayoutOverlay.visibility == View.VISIBLE || workspaceLayoutOverlay.visibility == View.VISIBLE,
                macroOverlay.visibility == View.VISIBLE,
                editorView.isViewerActive(), 
                editorView.activeBuffer?.pdfUri != null,
                isVKeyDown,
                isPersistentScreenshotEnabled,
                commandScriptManager.isScriptRunning(),
                fileBrowserOverlay.visibility == View.VISIBLE
            )
            
            if (cmd != null) {
                if (isPredictiveModeEnabled && !isModifierHeld && !isOverlayActive && (cmd == Command.DeleteBackward || cmd == Command.DeleteForward)) {
                    predictiveConsoleView.deleteLastChar()
                } else {
                    commandDispatcher.dispatch(cmd)
                }
                resetKeyboardModifiers(); return
            } else if (activeCmd == "SCREENSHOT") {
                commandDispatcher.dispatch(Command.Screenshot)
                resetKeyboardModifiers(); return
            } else if (isControlChar) {
                when (keyCode) {
                    KeyEvent.KEYCODE_ENTER -> if (isPredictiveModeEnabled && !isOverlayActive) commitPredictiveText() else dispatchInsertText("\n")
                    KeyEvent.KEYCODE_TAB -> if (isPredictiveModeEnabled && !isOverlayActive) predictiveConsoleView.updateComposing(predictiveConsoleView.composingText.toString() + "\t") else dispatchInsertText(" ".repeat(buffer.tabSize))
                }
                resetKeyboardModifiers(); return
            }
        }
    }

    // 3. Process Specific Virtual-Only Commands (Non-KeyEvent)
    if (activeCmd != null) {
        when (activeCmd) {
            "SWITCH_B1" -> commandDispatcher.dispatch(Command.SwitchBuffer(0))
            "SWITCH_B2" -> commandDispatcher.dispatch(Command.SwitchBuffer(1))
            "SWITCH_B3" -> commandDispatcher.dispatch(Command.SwitchBuffer(2))
            "SWITCH_B4" -> commandDispatcher.dispatch(Command.SwitchBuffer(3))
            "SWITCH_B5" -> commandDispatcher.dispatch(Command.SwitchBuffer(4))
            "SWITCH_B6" -> commandDispatcher.dispatch(Command.SwitchBuffer(5))
            "SWITCH_B7" -> commandDispatcher.dispatch(Command.SwitchBuffer(6))
            "SWITCH_B8" -> commandDispatcher.dispatch(Command.SwitchBuffer(7))
            "SWITCH_B9" -> commandDispatcher.dispatch(Command.OpenScratchpad)
            "FLUSH_LOG" -> commandDispatcher.dispatch(Command.FlushScratchpadSave)
            "BRIGHT_UP" -> adjustBrightness(0.1f)
            "BRIGHT_DOWN" -> adjustBrightness(-0.1f)
            "KB_UP" -> adjustKeyboardSize(50)
            "KB_DOWN" -> adjustKeyboardSize(-50)
            "FONT_UP" -> commandDispatcher.dispatch(Command.IncreaseFontSize)
            "FONT_DOWN" -> commandDispatcher.dispatch(Command.DecreaseFontSize)
            "VOL_UP" -> (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
            "VOL_DOWN" -> (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
            "UNDO" -> commandDispatcher.dispatch(Command.Undo)
            "HELP" -> commandDispatcher.dispatch(Command.OpenHelpMenu)
            "SETTINGS" -> showAppMenu(6)
            "V_KBD" -> toggleVirtualKeyboard()
            "TOGGLE_PREDICTIVE" -> togglePredictiveText()
            "KBD_NUM" -> loadKeyboardLayout("en_num.json")
            "KBD_PUNC" -> loadKeyboardLayout("en_punc.json")
            "KBD_ALPHA" -> loadKeyboardLayout("en_alpha.json")
            "OUTLINE" -> commandDispatcher.dispatch(Command.OpenOutlineSelection)
            "OUTLINE_SETUP" -> commandDispatcher.dispatch(Command.OpenOutlineSetup)
            "COPY_BUFFER" -> commandDispatcher.dispatch(Command.CopyBufferToSystem)
            "MINIMIZE" -> moveTaskToBack(true)
            "SHARE" -> commandDispatcher.dispatch(Command.ShareBuffer)
            "WORKSPACE" -> commandDispatcher.dispatch(Command.OpenWorkspaceLayout)
            "ToggleTodoWorkspace" -> commandDispatcher.dispatch(Command.ToggleTodoWorkspace)
            "MacroEdit" -> commandDispatcher.dispatch(Command.MacroEdit)
            "MacroInsert" -> commandDispatcher.dispatch(Command.MacroInsert)
            "CTRL_LOCK" -> {
                isCtrlLocked = !isCtrlLocked
                internalKeyboardView.ctrlActive = isCtrlLocked
                internalKeyboardView.invalidate()
                val status = if (isCtrlLocked) "LOCKED" else "OFF"
                showStatusToast("CTRL Lock: $status")
            }
            "EXIT_SPLIT" -> {
                if (isOverlayActive) {
                    commandDispatcher.dispatch(Command.OverlayCancel)
                } else if (internalKeyboardView.keyboardTag != "alpha") {
                    loadKeyboardLayout("en_alpha.json")
                } else {
                    commandDispatcher.dispatch(Command.ExitSplit)
                }
            }
        }

        val noRevertCmds = listOf("KB_UP", "KB_DOWN", "FONT_UP", "FONT_DOWN", "BRIGHT_UP", "BRIGHT_DOWN", "VOL_UP", "VOL_DOWN", "UP", "DOWN", "LEFT", "RIGHT", "KBD_NUM", "KBD_PUNC", "KBD_ALPHA")
        if (internalKeyboardView.keyboardTag == "punc" && !isPuncSticky && activeCmd !in noRevertCmds) loadKeyboardLayout("en_alpha.json")
        resetKeyboardModifiers()
        return
    }

    // 4. Process Alphanumeric Character
    if (activeValue != null) {
        val charToInsert = activeValue
        if (isPredictiveModeEnabled && !isModifierHeld && !isOverlayActive) {
            predictiveConsoleView.updateComposing(predictiveConsoleView.composingText.toString() + charToInsert)
        } else if (isOverlayActive && charToInsert.length == 1) {
            commandDispatcher.dispatch(Command.InputChar(charToInsert[0]))
        } else {
            dispatchInsertText(charToInsert)
        }
        if (internalKeyboardView.keyboardTag == "punc" && !isPuncSticky) {
            // Return to alpha if it's one of the one-off punctuation keys
            val puncSet = setOf('!', '@', '#', '$', '%', '^', '&', '?', '"', '\'', '~', '`', '[', ']', '{', '}', '\\', ';', '_', '-', '.', '(', ')', '<', '>', '|', ':')
            if (charToInsert.length == 1 && charToInsert[0] in puncSet) {
                loadKeyboardLayout("en_alpha.json")
            }
        }
        resetKeyboardModifiers()
    }
}
