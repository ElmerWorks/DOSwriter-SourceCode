package com.example.doswriter.ui

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.example.doswriter.MainActivity
import com.example.doswriter.logic.*
import com.example.doswriter.editor.*
import java.util.*

internal fun MainActivity.showEditorContextMenu(x: Float, y: Float) {
    val buffer = bufferManager.getCurrentBuffer()
    val hasSel = buffer.hasSelection()
    val options = listOf(
        if (hasSel) "Copy (Ctrl-C)" else "Copy [No Selection]",
        if (hasSel) "Cut (Ctrl-X)" else "Cut [No Selection]",
        "Paste (Ctrl-V)",
        "Select All (Ctrl-A)",
        "Trackball & Mouse Options",
        "Cancel"
    )
    selectionOverlay.show("Text & Trackball Actions", options, 0, { index ->
        when(index) {
            0 -> if (hasSel) commandDispatcher.dispatch(Command.Copy)
            1 -> if (hasSel) commandDispatcher.dispatch(Command.Cut)
            2 -> commandDispatcher.dispatch(Command.Paste)
            3 -> commandDispatcher.dispatch(Command.SelectAll)
            4 -> showTrackballSettingsMenu()
        }
    }, {})
}

internal fun MainActivity.showStartupHelpMessage() {
    if (!isStartupHelpEnabled) return
    if (isRunningWizard || deviceType == -1) return

    val splashView = TextView(this).apply {
        text = "DOSwriter\nMenus : ALT-F\nHelp  : ALT-H"
        setTextColor(android.graphics.Color.YELLOW)
        setBackgroundColor(android.graphics.Color.argb(220, 0, 0, 0))
        textSize = 28f
        setPadding(60, 60, 60, 60)
        gravity = Gravity.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
    }
    editorStack.addView(splashView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    Handler(Looper.getMainLooper()).postDelayed({
        if (splashView.parent != null) editorStack.removeView(splashView)
        if (deviceType != -1 && isStartupOverviewEnabled && isOverviewPending) {
            isOverviewPending = false
            selectionOverlay.show("VIEW QUICK TUTORIAL?", listOf("Start", "Skip", "Don't show this again"), 0, { index ->
                if (index == 0) showQuickOverview()
                else if (index == 2) { isStartupHelpEnabled = false; saveGlobalSettings(); Toast.makeText(this, "Startup messages disabled", Toast.LENGTH_SHORT).show() }
            }, {})
        }
    }, 3000)
}

internal fun MainActivity.showBufferSelectionMenu() {
    val options = (0 until 8).map { i ->
        val b = bufferManager.getBufferAt(i)
        val indicator = if (b.isDirty) "*" else ""
        "B${i + 1}: ${b.fileName ?: "Empty"}$indicator"
    }
    selectionOverlay.show("Select Buffer", options, bufferManager.currentBufferIndex, { index ->
        commandDispatcher.dispatch(Command.SwitchBuffer(index))
    }, {})
}

internal fun MainActivity.showRecentFilesMenu() {
    val recents = recentFileManager.getRecentFiles()
    if (recents.isEmpty()) { showStatusToast("No recent files"); return }
    selectionOverlay.show("Recent Files", recents.map { it.name }, 0, { index ->
        val entry = recents[index]
        if (entry.isRemote) syncManager.openRemoteFile(entry.path)
        else handleDocumentOpened(Uri.parse(entry.path))
    }, {})
}

internal fun MainActivity.showImageFolderPicker() {
    val folders = imageFolderManager.getFolders()
    if (folders.isEmpty()) { Toast.makeText(this, "No image folders linked", Toast.LENGTH_SHORT).show(); showImageSettingsMenu(); return }
    selectionOverlay.show("Select Image Folder", folders.map { it.name }, 0, { index ->
        imageFolderManager.currentFolder = folders[index]
        showStatusToast("Current Image Folder: ${folders[index].name}")
        viewerFiles.clear(); viewerIndex = -1; handleOverlayCommand(Command.OverlayCancel)
    }, {})
}

internal fun MainActivity.showPdfFavoritePicker() {
    val buffer = bufferManager.getCurrentBuffer()
    if (buffer.pdfFavorites.isEmpty()) { Toast.makeText(this, "No favorites defined", Toast.LENGTH_SHORT).show(); return }
    val options = buffer.pdfFavorites.map { "Page ${it.pageIndex + 1}: ${it.identifier}" }
    selectionOverlay.show("PDF Favorites", options, 0, { index -> commandDispatcher.dispatch(Command.SelectPdfFavorite(index)) }, {})
}

internal fun MainActivity.showPatternCollapsePicker() {
    val buffer = bufferManager.getCurrentBuffer()
    val text = buffer.getText()
    val options = buffer.collapsePatterns.map { pattern ->
        var count = 0; var pos = text.indexOf(pattern, buffer.prefix.length, ignoreCase = true)
        while (pos != -1) { count++; pos = text.indexOf(pattern, pos + 1, ignoreCase = true) }
        val display = if (pattern.length > 25) pattern.take(25) + "..." else pattern
        "[$count] $display"
    } + listOf("---", "Fold List", "Collapse All Paragraphs", "Expand All Paragraphs", "Back")

    selectionOverlay.show("Pattern Collapse Manager", options, 0, { index ->
        if (index < buffer.collapsePatterns.size) {
            val pattern = buffer.collapsePatterns[index]
            val actions = listOf("Collapse: $pattern", "Expand this pattern", "[C] Copy Pattern", "[R] Rename Pattern", "[D] Delete Pattern", "Back")
            selectionOverlay.show("Pattern Details", actions, 0, { actionIdx ->
                when (actionIdx) {
                    0 -> { if (buffer.getLength() > bufferManager.patternCollapseLimit) Toast.makeText(this, "Buffer too large", Toast.LENGTH_SHORT).show() else applyCollapseToBuffer(pattern); showPatternCollapsePicker() }
                    1 -> { val other = buffer.collapsePatterns.filter { it != pattern }; buffer.clearFolds(); editorView.invalidate(); other.forEach { applyCollapseToBuffer(it) }; showPatternCollapsePicker() }
                    2 -> { (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Pattern", pattern)); showPatternCollapsePicker() }
                    3 -> { inputOverlay.show("Rename Pattern:", pattern, { if (it.isNotEmpty()) commandDispatcher.dispatch(Command.RenameCollapsePattern(index, it)) }, { showPatternCollapsePicker() }) }
                    4 -> commandDispatcher.dispatch(Command.DeleteCollapsePattern(index))
                    else -> showPatternCollapsePicker()
                }
            }, { showPatternCollapsePicker() })
        } else if (options[index] == "Fold List") commandDispatcher.dispatch(Command.OpenFoldMenu)
        else if (options[index] == "Collapse All Paragraphs") commandDispatcher.dispatch(Command.CollapseAllParagraphs)
        else if (options[index] == "Expand All Paragraphs") commandDispatcher.dispatch(Command.UncollapseAll)
    }, {})
}

internal fun MainActivity.showExamplesMenu() {
    try {
        val raw = assets.open("INSTRUCTIONS.md").bufferedReader().use { it.readText() }
        val profiler = com.example.doswriter.logic.DeviceProfiler(this)
        val parser = com.example.doswriter.logic.HelpParser(deviceType, profiler.getDeviceInfo(), resources.configuration.smallestScreenWidthDp >= 600)
        
        val categorized = parser.getCategorizedExamples(raw)
        
        if (categorized.isEmpty()) {
            Toast.makeText(this, "No examples found in Instructions", Toast.LENGTH_LONG).show()
            return
        }

        val categories = categorized.keys.filter { it != "THUMBNAIL" }.toList()
        val options = categories.map { it.lowercase().replaceFirstChar { c -> c.uppercase() } + " Examples" } + "Back"
        
        selectionOverlay.show("Example Categories", options, 0, { index ->
            if (index < categories.size) {
                val cat = categories[index]
                val files = categorized[cat] ?: emptyList()
                // Use the full path for the label if it's nested, or just the name?
                // The user complained about missing files. Let's show the full relative path to be sure they are distinct.
                val fileOptions = files.map { it } + "Back"
                
                selectionOverlay.show("${options[index]}", fileOptions, 0, { fileIdx ->
                    if (fileIdx < files.size) {
                        loadExampleFile(files[fileIdx])
                    } else {
                        showExamplesMenu()
                    }
                }, { showExamplesMenu() })
            }
        }, {})
    } catch (e: Exception) { 
        e.printStackTrace()
        Toast.makeText(this, "Failed to load examples from Instructions", Toast.LENGTH_LONG).show()
    }
}

internal fun MainActivity.showAppMenu(index: Int) {
    isRedirectedFromFileMenu = false
    currentAppMenuIndex = index
    val titles = listOf("FILE", "EDIT", "VIEW", "WORKSPACE", "TOOLS", "TYPEWRITER", "PUBLISH", "SETTINGS", "HELP")
    val options = when(index) {
        0 -> listOf("File Browser (CTRL-ALT-F)", "Save (Ctrl-S)", "Save As (Ctrl-Shift-S)", "Open (Ctrl-O)", "Recent (Ctrl-Shift-O)", "Close (Ctrl-W)", "Close All (Ctrl-Alt-W)", "Exit (Ctrl-Alt-X)")
        1 -> listOf("Undo (Ctrl-Z)", "Redo (Ctrl-Alt-Z)", "Cut (Ctrl-X)", "Copy (Ctrl-C)", "Paste (Ctrl-V)", "Select All (Ctrl-A)", "Find (Ctrl-F)", "Replace (Ctrl-R)", "Bookmarks (Alt-K)", "Macros (Ctrl-Alt-M)", "Unwrap Paragraphs")
        2 -> listOf("Buffers (Alt-B)", "Workspace Layout (Ctrl-Alt-B)", "Manuscript Viewer (Ctrl-Q)", "Scratchpad (Alt-9)", "Image Viewer (Alt-Shift-I)", "Margins (Alt-M)", "Markdown Render (Alt-R)", "Status Bar (Ctrl-H)", "Split Screen (Ctrl-Arrow)")
        3 -> listOf(if (bufferManager.isTodoWorkspace) "Main Mode" else "ToDo Mode")
        4 -> listOf("Simple Collapse (Ctrl-J)", "Pattern Manager (Alt-J)", "Recollapse (Ctrl-Shift-J)", "Outline Manager (Alt-O)", "Desktop Sync (Ctrl-D)")
        5 -> listOf(
            "Typewriter Active: ${if (bufferManager.getCurrentBuffer().cursorSettings.typewriter.isEnabled) "ON" else "OFF"}",
            "Stationary Cursor: ${if (bufferManager.getCurrentBuffer().cursorSettings.typewriter.isFixed) "ON" else "OFF"}",
            "Position: ${bufferManager.getCurrentBuffer().cursorSettings.typewriter.anchor.name}",
            "Type Guide Graphic: ${if (bufferManager.getCurrentBuffer().cursorSettings.typewriter.showGuide) "ON" else "OFF"}",
            "Terminal Mode: ${if (bufferManager.getCurrentBuffer().cursorSettings.typewriter.isTerminalMode) "ON" else "OFF"}",
            "Char Delay: ${bufferManager.getCurrentBuffer().cursorSettings.typewriter.charDelayMs}ms",
            "CRLF Delay: ${bufferManager.getCurrentBuffer().cursorSettings.typewriter.crlfDelayMs}ms"
        )
        6 -> listOf("Standard PDF Compile", "Manuscript Format PDF (WYSWYG)", "8-in-1 Minibook (Landscape)", "Manuscript View Settings")
        7 -> listOf("Cursor", "Font", "Margins", "Theme", "File Manager", "Images", "Advanced Features", "Global Settings: ${if (isSettingsGlobal) "ON" else "OFF"}")
        8 -> listOf("Quick Keyboard Shortcuts Reference", "Quick Tutorial", "Menus and Keyboard Shortcuts", "Virtual Keyboard Map", "Typewriter Demo", "How to Use To-Do List (PDA)", "About")
        else -> emptyList()
    }
    selectionOverlay.show(titles[index], options, 0, { handleAppMenuItemClick(index, it) }, { currentAppMenuIndex = -1 })
}

internal fun MainActivity.handleAppMenuItemClick(menuIdx: Int, itemIdx: Int) {
    currentAppMenuIndex = -1
    when(menuIdx) {
        0 -> when(itemIdx) {
            0 -> commandDispatcher.dispatch(Command.OpenFileBrowser); 1 -> commandDispatcher.dispatch(Command.SaveToFile); 2 -> commandDispatcher.dispatch(Command.SaveAs); 3 -> commandDispatcher.dispatch(Command.OpenFile); 4 -> commandDispatcher.dispatch(Command.OpenRecentFiles); 5 -> commandDispatcher.dispatch(Command.CloseFile); 6 -> commandDispatcher.dispatch(Command.CloseAll); 7 -> commandDispatcher.dispatch(Command.RequestExit)
        }
        1 -> when(itemIdx) {
            0 -> commandDispatcher.dispatch(Command.Undo); 1 -> commandDispatcher.dispatch(Command.Redo); 2 -> commandDispatcher.dispatch(Command.Cut); 3 -> commandDispatcher.dispatch(Command.Copy); 4 -> commandDispatcher.dispatch(Command.Paste); 5 -> commandDispatcher.dispatch(Command.SelectAll); 6 -> commandDispatcher.dispatch(Command.OpenFind); 7 -> commandDispatcher.dispatch(Command.OpenReplace); 8 -> commandDispatcher.dispatch(Command.OpenBookmarkMenu); 9 -> commandDispatcher.dispatch(Command.OpenMacroMenu); 10 -> commandDispatcher.dispatch(Command.UnwrapParagraphs)
        }
        2 -> when(itemIdx) {
            0 -> showBufferSelectionMenu(); 1 -> commandDispatcher.dispatch(Command.OpenWorkspaceLayout); 2 -> commandDispatcher.dispatch(Command.OpenManuscriptMenu); 3 -> commandDispatcher.dispatch(Command.OpenScratchpad); 4 -> showImageFolderPicker(); 5 -> commandDispatcher.dispatch(Command.ToggleShowMargins); 6 -> commandDispatcher.dispatch(Command.ToggleMarkdownRender); 7 -> commandDispatcher.dispatch(Command.ToggleStatus); 8 -> commandDispatcher.dispatch(Command.OpenSplitMenu)
        }
        3 -> when(itemIdx) { 0 -> commandDispatcher.dispatch(Command.ToggleTodoWorkspace) }
        4 -> when(itemIdx) {
            0 -> commandDispatcher.dispatch(Command.CollapseSelection); 1 -> commandDispatcher.dispatch(Command.OpenPatternCollapsePicker); 2 -> commandDispatcher.dispatch(Command.RecollapseLastExpansion); 3 -> commandDispatcher.dispatch(Command.OpenOutlineSelection); 4 -> showSyncMenu()
        }
        5 -> {
            val settings = bufferManager.getCurrentBuffer().cursorSettings.typewriter
            when(itemIdx) {
                0 -> {
                    settings.isEnabled = !settings.isEnabled
                    bufferManager.saveBuffers()
                    if (settings.isEnabled) editorView.recenterTypewriter()
                    editorView.invalidate()
                    showAppMenu(5)
                }
                1 -> {
                    val newState = !settings.isFixed
                    for (b in bufferManager.getAllBuffers()) {
                        b.cursorSettings.typewriter.isFixed = newState
                    }
                    bufferManager.saveBuffers()
                    if (settings.isEnabled) editorView.recenterTypewriter()
                    editorView.invalidate()
                    showAppMenu(5)
                }
                2 -> showTypewriterPositionMenu()
                3 -> {
                    val newState = !settings.showGuide
                    for (b in bufferManager.getAllBuffers()) {
                        b.cursorSettings.typewriter.showGuide = newState
                    }
                    bufferManager.saveBuffers()
                    editorView.invalidate()
                    showAppMenu(5)
                }
                4 -> {
                    val newState = !settings.isTerminalMode
                    for (b in bufferManager.getAllBuffers()) {
                        b.cursorSettings.typewriter.isTerminalMode = newState
                    }
                    bufferManager.saveBuffers()
                    if (settings.isEnabled) editorView.recenterTypewriter()
                    showAppMenu(5)
                }
                5 -> showTypewriterDelayMenu()
                6 -> showTypewriterCrlfDelayMenu()
            }
        }
        6 -> when(itemIdx) {
            0 -> { currentPdfFormat = 0; commandDispatcher.dispatch(Command.InstantPdf) }; 1 -> { currentPdfFormat = 1; commandDispatcher.dispatch(Command.InstantPdf) }; 2 -> { currentPdfFormat = 2; commandDispatcher.dispatch(Command.InstantPdf) }; 3 -> showManuscriptSettingsMenu()
        }
        7 -> when(itemIdx) {
            0 -> showCursorMenu(); 1 -> showFontSettingsMenu(); 2 -> showMarginSettingsMenu(); 3 -> showThemeSettingsMenu(); 4 -> showFileManagerSettingsMenu(); 5 -> showImageSettingsMenu(); 6 -> showAdvancedFeaturesMenu(); 7 -> { isSettingsGlobal = !isSettingsGlobal; if (isSettingsGlobal) { bufferManager.applyCurrentSettingsToAll(); refreshActiveViews() }; saveGlobalSettings(); showAppMenu(7) }
        }
        8 -> when(itemIdx) { 0 -> showHelpPopup(); 1 -> showQuickOverview(); 2 -> showTopicNavigationMenu(); 3 -> showVirtualKeyboardAssignmentsMenu(); 4 -> runTypewriterDemo(); 5 -> showHelpFile("PDA/DOSwriter-PDA.md"); 6 -> commandDispatcher.dispatch(Command.ShowAbout) }
    }
}

internal fun MainActivity.showScriptingMenu() {
    selectionOverlay.show("SCRIPTING", listOf("Run Script (Ctrl-F8)", "Stop Script (Ctrl-Alt-F8)", "Script Syntax", "Scripting Settings"), 0, {
        when(it) {
            0 -> commandDispatcher.dispatch(Command.RunScript); 1 -> commandDispatcher.dispatch(Command.StopScript); 2 -> showHelpFile("new-features/DOSwriter-Command-Syntax.md"); 3 -> showScriptingEngineSettingsMenu()
        }
    }, { showAppMenu(4) })
}
