package com.example.doswriter.ui

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.example.doswriter.MainActivity
import com.example.doswriter.logic.*
import com.example.doswriter.editor.*
import com.example.doswriter.model.*
import java.util.*

internal fun MainActivity.showFileManagerSettingsMenu() {
    val options = listOf(
        "Link Working Folder",
        "Manage Working Folders",
        "Sort: ${if (bufferManager.fileBrowserSortMode == 0) "NAME" else "MOST RECENT"}",
        "File Browser: ${if (bufferManager.useWorkstationFileBrowser) "DOSwriter" else "System"}"
    )
    selectionOverlay.show("File Manager", options, 0, {
        when(it) {
            0 -> linkWorkingFolderLauncher.launch(null)
            1 -> showManageWorkingFoldersMenu()
            2 -> {
                bufferManager.fileBrowserSortMode = 1 - bufferManager.fileBrowserSortMode
                bufferManager.saveBuffers()
                showFileManagerSettingsMenu()
            }
            3 -> {
                bufferManager.useWorkstationFileBrowser = !bufferManager.useWorkstationFileBrowser
                bufferManager.saveBuffers()
                if (isRedirectedFromFileMenu && !bufferManager.useWorkstationFileBrowser) {
                    isRedirectedFromFileMenu = false
                    selectionOverlay.hide()
                    showAppMenu(0)
                } else {
                    showFileManagerSettingsMenu()
                }
            }
        }
    }, { showAppMenu(6) })
}

internal fun MainActivity.showManageWorkingFoldersMenu() {
    val folders = workingFolderManager.getFolders()
    if (folders.isEmpty()) { showFileManagerSettingsMenu(); return }
    val options = folders.map {
        val suffix = if (it.path == workingFolderManager.currentFolder?.path) " (Current)" else ""
        it.name + suffix
    } + "Back"
    selectionOverlay.show("WORKING FOLDERS (DEL TO UNLINK)", options, 0, { index ->
        if (index in folders.indices) {
            workingFolderManager.currentFolder = folders[index]
            workingFolderManager.saveFolders()
            showStatusToast("Working Folder: ${folders[index].name}")
            showManageWorkingFoldersMenu()
        } else {
            showFileManagerSettingsMenu()
        }
    }, { showFileManagerSettingsMenu() })
}

internal fun MainActivity.showTrackballSettingsMenu() {
    val modeLabel = if (bufferManager.scrollWheelMode == 0) "Lines (${bufferManager.scrollWheelLines} lines)" else "Page Jump (Full Screen)"
    val dragLabel = if (bufferManager.isLeftDragSelectMode) "Select Text (Win/Linux)" else "Pan Viewport"
    val options = listOf(
        "Scroll Mode: $modeLabel",
        "Lines Per Scroll Notch: ${bufferManager.scrollWheelLines} lines",
        "Left Drag Action: $dragLabel",
        "Back"
    )
    selectionOverlay.show("Trackball & Mouse Options", options, 0, {
        when(it) {
            0 -> {
                bufferManager.scrollWheelMode = 1 - bufferManager.scrollWheelMode
                bufferManager.saveBuffers()
                showTrackballSettingsMenu()
            }
            1 -> showScrollWheelLinesMenu()
            2 -> {
                bufferManager.isLeftDragSelectMode = !bufferManager.isLeftDragSelectMode
                bufferManager.saveBuffers()
                showTrackballSettingsMenu()
            }
            3 -> showAdvancedFeaturesMenu()
        }
    }, { showAdvancedFeaturesMenu() })
}

internal fun MainActivity.showScrollWheelLinesMenu() {
    val options = listOf("1 Line", "2 Lines", "3 Lines", "5 Lines", "10 Lines", "Back")
    val values = listOf(1, 2, 3, 5, 10)
    val currentIdx = values.indexOf(bufferManager.scrollWheelLines).coerceAtLeast(0)

    selectionOverlay.show("Lines Per Scroll Notch", options, currentIdx, { index ->
        if (index < values.size) {
            bufferManager.scrollWheelLines = values[index]
            bufferManager.saveBuffers()
            showTrackballSettingsMenu()
        } else {
            showTrackballSettingsMenu()
        }
    }, { showTrackballSettingsMenu() })
}

internal fun MainActivity.showAdvancedFeaturesMenu() {
    val options = listOf(
        "Manuscript View",
        "Text Display",
        "Virtual Keyboard",
        "Keyboard Mode",
        "Scratchpad Buffer",
        "Trackball & Mouse",
        "Hardware",
        "Dev",
        "Request System Brightness Permission",
        "Back"
    )
    selectionOverlay.show("Advanced Features", options, 0, {
        when(it) {
            0 -> showManuscriptSettingsMenu()
            1 -> showTextDisplayMenu()
            2 -> showVirtualKeyboardSettingsMenu()
            3 -> showKeyboardModeMenu()
            4 -> showScratchpadSettingsMenu()
            5 -> showTrackballSettingsMenu()
            6 -> showHardwareMenu()
            7 -> showDevMenu()
            8 -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            }
            9 -> showAppMenu(7)
        }
    }, { showAppMenu(7) })
}

internal fun MainActivity.showDevMenu() {
    val warningLabel = if (bufferManager.largeFileWarningLimit == 0) "OFF" else "${bufferManager.largeFileWarningLimit / 1000} KB"
    val options = listOf(
        "Keyboard Overlay: ${if (internalKeyboardView.showDevOverlay) "ON" else "OFF"}",
        "Overlay Target: ${internalKeyboardView.keyboardTag.uppercase()}",
        "Keyboard Debug Mode: ${if (isVirtualKeyboardEnabled) "DISABLED (KBD ON)" else if (isKeyboardDebugEnabled) "ON" else "OFF"}",
        "Suppress Status Messages: ${if (isStatusMessagesSuppressed) "ON" else "OFF"}",
        "Persistent Screenshot: ${if (isPersistentScreenshotEnabled) "ON" else "OFF"}",
        "Image Fade Transitions: ${if (isImageFadeEnabled) "ON" else "OFF"}",
        "External Font Scan: ${if (isFontScanEnabled) "ON" else "OFF"}",
        "Pattern Collapse Limit: ${bufferManager.patternCollapseLimit / 1000} KB",
        "Large File Warning: $warningLabel",
        "Scripting",
        "Scan Device Fonts",
        "Device Info",
        "Back"
    )
    selectionOverlay.show("Dev Settings", options, 0, {
        when(it) {
            0 -> {
                internalKeyboardView.showDevOverlay = !internalKeyboardView.showDevOverlay
                internalKeyboardView.invalidate()
                showDevMenu()
            }
            1 -> {
                val types = listOf("alpha", "punc", "num")
                val currentIdx = types.indexOf(internalKeyboardView.keyboardTag)
                val nextIdx = (currentIdx + 1) % types.size
                loadKeyboardLayout("en_${types[nextIdx]}.json")
                showDevMenu()
            }
            2 -> {
                if (!isVirtualKeyboardEnabled) {
                    isKeyboardDebugEnabled = !isKeyboardDebugEnabled
                } else {
                    showStatusToast("Debug Mode requires hardware keyboard")
                }
                showDevMenu()
            }
            3 -> {
                isStatusMessagesSuppressed = !isStatusMessagesSuppressed
                saveGlobalSettings()
                showDevMenu()
            }
            4 -> {
                isPersistentScreenshotEnabled = !isPersistentScreenshotEnabled
                saveGlobalSettings()
                showDevMenu()
            }
            5 -> {
                isImageFadeEnabled = !isImageFadeEnabled
                leftEditorView.isImageFadeEnabled = isImageFadeEnabled
                rightEditorView.isImageFadeEnabled = isImageFadeEnabled
                saveGlobalSettings()
                showDevMenu()
            }
            6 -> {
                isFontScanEnabled = !isFontScanEnabled
                saveGlobalSettings()
                showDevMenu()
            }
            7 -> showPatternCollapseLimitMenu()
            8 -> showLargeFileWarningMenu()
            9 -> showScriptingEngineSettingsMenu()
            10 -> showFontScanMenu()
            11 -> {
                val profiler = com.example.doswriter.logic.DeviceProfiler(this)
                val info = profiler.getDeviceInfo()
                val details = listOf(
                    "Manufacturer: ${info.manufacturer}",
                    "Brand: ${info.brand}",
                    "Model: ${info.model}",
                    "Viewport: ${info.width} x ${info.height}",
                    "API Level: ${info.apiLevel}",
                    "Back"
                )
                selectionOverlay.show("Device Profile", details, 5, { showDevMenu() }, { showDevMenu() })
            }
            12 -> showAdvancedFeaturesMenu()
        }
    }, { showAdvancedFeaturesMenu() })
}

internal fun MainActivity.showScriptingEngineSettingsMenu() {
    val options = listOf(
        "Link Scripting Image Folder",
        "Script Echo: ${if (bufferManager.isScriptEchoEnabled) "ON" else "OFF"}",
        "Back"
    )
    selectionOverlay.show("Scripting", options, 0, {
        when(it) {
            0 -> linkScriptImageFolderLauncher.launch(null)
            1 -> {
                bufferManager.isScriptEchoEnabled = !bufferManager.isScriptEchoEnabled
                bufferManager.saveBuffers()
                showScriptingEngineSettingsMenu()
            }
            2 -> showDevMenu()
        }
    }, { showDevMenu() })
}

internal fun MainActivity.showFontScanMenu() {
    val fontDir = java.io.File("/system/fonts")
    val fonts = fontDir.listFiles()?.map { it.name }?.sorted() ?: emptyList()

    if (fonts.isEmpty()) {
        Toast.makeText(this, "Could not access /system/fonts", Toast.LENGTH_SHORT).show()
        showDevMenu()
        return
    }

    selectionOverlay.show("System Fonts (${fonts.size})", fonts + "Back", 0, { index ->
        if (index < fonts.size) {
            val fontName = fonts[index]
            inputOverlay.show("Apply this font?", fontName, { name ->
                editorView.setFontType(name)
                bufferManager.saveBuffers()
                showFontScanMenu()
            }, { showFontScanMenu() })
        } else {
            showDevMenu()
        }
    }, { showDevMenu() })
}

internal fun MainActivity.showLargeFileWarningMenu() {
    val options = listOf("50 KB", "100 KB", "250 KB", "OFF", "Back")
    val values = listOf(50000, 100000, 250000, 0)
    val currentIdx = values.indexOf(bufferManager.largeFileWarningLimit).coerceAtLeast(0)

    selectionOverlay.show("Large File Warning Threshold", options, currentIdx, { index ->
        if (index < values.size) {
            bufferManager.largeFileWarningLimit = values[index]
            bufferManager.saveBuffers()
            showDevMenu()
        } else {
            showDevMenu()
        }
    }, { showDevMenu() })
}

internal fun MainActivity.showPatternCollapseLimitMenu() {
    val options = listOf("10 KB", "20 KB", "30 KB", "40 KB", "50 KB", "64 KB", "Back")
    val values = listOf(10000, 20000, 30000, 40000, 50000, 64000)
    val currentIdx = values.indexOf(bufferManager.patternCollapseLimit).coerceAtLeast(0)

    selectionOverlay.show("Pattern Collapse Limit", options, currentIdx, { index ->
        if (index < values.size) {
            bufferManager.patternCollapseLimit = values[index]
            bufferManager.saveBuffers()
            showDevMenu()
        } else {
            showDevMenu()
        }
    }, { showDevMenu() })
}

internal fun MainActivity.showFontSettingsMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val boldLabel = if (buffer.isBold) "Font Bold: ON" else "Font Bold: OFF"
    val options = listOf(
        "Font Selection",
        "Font Presets (CPL)",
        "Line Height",
        boldLabel,
        "Link Custom Font Folder",
        "Apply Font Settings Globally",
        "Back"
    )
    selectionOverlay.show("Font Settings", options, 0, {
        when(it) {
            0 -> showFontSelectionMenu()
            1 -> showFontPresetMenu()
            2 -> showLineHeightMenu()
            3 -> {
                editorView.setFontBold(!buffer.isBold)
                if (isSettingsGlobal) bufferManager.applyFontToAll()
                bufferManager.saveBuffers()
                showFontSettingsMenu()
            }
            4 -> {
                openFontFolderLauncher.launch(null)
            }
            5 -> {
                bufferManager.applyFontToAll()
                showStatusToast("Font settings applied to all buffers")
                showFontSettingsMenu()
            }
            6 -> showAppMenu(6)
        }
    }, { showAppMenu(6) })
}

internal fun MainActivity.showFontPresetMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val presets = listOf("Pocket", "Compact", "Standard", "Comfortable", "Manuscript")
    val cpls = listOf(45, 60, 75, 90, 100)
    val options = presets.mapIndexed { idx, name -> "$name (${cpls[idx]} CPL)" } + "Back"
    
    val currentIdx = presets.indexOf(buffer.activePreset).coerceAtLeast(0)

    selectionOverlay.show("Font Presets (Target CPL)", options, currentIdx, { index ->
        if (index < presets.size) {
            editorView.applyFontPreset(presets[index])
            if (isSettingsGlobal) bufferManager.applyFontToAll()
            bufferManager.saveBuffers()
            showFontPresetMenu()
        } else {
            showFontSettingsMenu()
        }
    }, { showFontSettingsMenu() })
}

internal fun MainActivity.showLineHeightMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val options = listOf("Tight (1.0)", "Standard (1.2)", "Comfortable (1.5)", "Double (2.0)", "Back")
    val values = listOf(1.0f, 1.2f, 1.5f, 2.0f)

    val currentIndex = values.indexOf(buffer.lineSpacing).coerceAtLeast(0)

    selectionOverlay.show("Line Height", options, currentIndex, { index ->
        if (index < values.size) {
            editorView.setLineSpacing(values[index])
            if (isSettingsGlobal) bufferManager.applyFontToAll()
            bufferManager.saveBuffers()
            showLineHeightMenu()
        } else {
            showFontSettingsMenu()
        }
    }, { showFontSettingsMenu() })
}

internal fun MainActivity.getAvailableFonts(): Pair<List<String>, List<String>> {
    val fontOptions = mutableListOf(
        "Monospace", "Courier New", "Serif", "Sans-serif", "Consolas",
        "Lucida Console", "Roboto Mono", "Times New Roman", "Georgia",
        "Arial", "Helvetica", "Verdana", "Trebuchet", "Noto Sans",
        "NotoSansMono-Light", "NotoSansMono-Regular", "NotoSansMono-Medium", "NotoSansMono-Bold", "NotoSansMono-Black",
        "NotoSans-Light", "NotoSans-Regular", "NotoSans-Medium", "NotoSans-Bold", "NotoSans-Black",
        "NotoSerif-Light", "NotoSerif-Regular", "NotoSerif-Medium", "NotoSerif-Bold", "NotoSerif-Black"
    )
    val fontTypes = mutableListOf(
        "MONOSPACE", "COURIER NEW", "SERIF", "SANS_SERIF", "CONSOLAS",
        "LUCIDA CONSOLE", "ROBOTO MONO", "TIMES NEW ROMAN", "GEORGIA",
        "ARIAL", "HELVETICA", "VERDANA", "TREBUCHET", "NOTO SANS",
        "NotoSansMono-Light", "NotoSansMono-Regular", "NotoSansMono-Medium", "NotoSansMono-Bold", "NotoSansMono-Black",
        "NotoSans-Light", "NotoSans-Regular", "NotoSans-Medium", "NotoSans-Bold", "NotoSans-Black",
        "NotoSerif-Light", "NotoSerif-Regular", "NotoSerif-Medium", "NotoSerif-Bold", "NotoSerif-Black"
    )

    if (isFontScanEnabled) {
        val buffer = bufferManager.getCurrentBuffer()
        buffer.customFontsPath?.let { path ->
            try {
                val root = DocumentFile.fromTreeUri(this, Uri.parse(path))
                val files = root?.listFiles()?.filter {
                    it.name?.lowercase()?.let { n -> n.endsWith(".ttf") || n.endsWith(".otf") } == true
                }?.take(24) ?: emptyList()

                if (files.isNotEmpty()) {
                    files.forEach { file ->
                        file.name?.let { fileName ->
                            val cleanName = fileName.removeSuffix(".ttf").removeSuffix(".otf")
                            if (!fontTypes.contains(cleanName) && !fontTypes.contains(fileName)) {
                                fontOptions.add(cleanName)
                                fontTypes.add(fileName)
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }
    }
    return Pair(fontOptions, fontTypes)
}

internal fun MainActivity.cycleFonts(forward: Boolean) {
    val (_, fontTypes) = getAvailableFonts()
    val buffer = bufferManager.getCurrentBuffer()
    val currentIdx = fontTypes.indexOf(buffer.fontType)
    
    val nextIdx = if (forward) {
        (currentIdx + 1) % fontTypes.size
    } else {
        (currentIdx - 1 + fontTypes.size) % fontTypes.size
    }
    
    val selectedType = fontTypes[nextIdx]
    editorView.setFontType(selectedType)
    if (isSettingsGlobal) {
        bufferManager.applyFontToAll()
        refreshActiveViews()
    }
    bufferManager.saveBuffers()
    showStatusToast("Font: $selectedType")
}

internal fun MainActivity.showFontSelectionMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val (fontOptionsBase, fontTypesBase) = getAvailableFonts()
    
    val fontOptions = fontOptionsBase.toMutableList()
    val fontTypes = fontTypesBase.toMutableList()

    // Add visual separators for the selection menu specifically
    if (isFontScanEnabled && buffer.customFontsPath != null) {
        val root = DocumentFile.fromTreeUri(this, Uri.parse(buffer.customFontsPath!!))
        val hasExternal = root?.listFiles()?.any { it.name?.lowercase()?.let { n -> n.endsWith(".ttf") || n.endsWith(".otf") } == true } ?: false
        if (hasExternal) {
            // Re-find the first external font to insert separator
            val firstInternalCount = 27 // Number of hardcoded fonts
            if (fontOptions.size > firstInternalCount) {
                fontOptions.add(firstInternalCount, "--- EXTERNAL FONTS ---")
                fontTypes.add(firstInternalCount, "---")
            }
        }
    }

    val currentIndex = fontTypes.indexOf(buffer.fontType).coerceAtLeast(0)

    val typefaces = fontTypes.map { type ->
        if (type == "---") return@map null

        var tf: android.graphics.Typeface? = null
        val assetName = type.replace(" ", "")
        val extensions = listOf(".ttf", ".otf")
        for (ext in extensions) {
            try {
                tf = android.graphics.Typeface.createFromAsset(this@showFontSelectionMenu.resources.assets, "fonts/$assetName$ext")
                if (tf != null) break
            } catch (e: Exception) {}
        }

        if (tf != null) return@map tf

        buffer.customFontsPath?.let { path ->
            try {
                if (path.startsWith("content://")) {
                    val root = DocumentFile.fromTreeUri(this@showFontSelectionMenu, Uri.parse(path))
                    val fontFile = root?.findFile(type) ?: root?.findFile("$type.ttf") ?: root?.findFile("$type.otf")
                    fontFile?.let {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            contentResolver.openFileDescriptor(it.uri, "r")?.use { pfd ->
                                tf = android.graphics.Typeface.Builder(pfd.fileDescriptor).build()
                            }
                        } else {
                            contentResolver.openInputStream(it.uri)?.use { input ->
                                val temp = java.io.File(cacheDir, "font_${type.hashCode()}.ttf")
                                if (!temp.exists()) {
                                    temp.outputStream().use { output -> input.copyTo(output) }
                                }
                                tf = android.graphics.Typeface.createFromFile(temp)
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        if (tf != null) return@map tf

        when (type) {
            "MONOSPACE" -> android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
            "COURIER NEW" -> android.graphics.Typeface.create("serif-monospace", android.graphics.Typeface.NORMAL)
            "SERIF" -> android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
            "SANS_SERIF" -> android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
            "ROBOTO MONO" -> android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL)
            "TIMES NEW ROMAN" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
            "GEORGIA" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
            "ARIAL" -> android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            "HELVETICA" -> android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            "VERDANA" -> android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            "TREBUCHET" -> android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
            "CONSOLAS", "LUCIDA CONSOLE" -> android.graphics.Typeface.create("monospace", android.graphics.Typeface.NORMAL)
            "NOTO SANS" -> android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            else -> android.graphics.Typeface.create(type, android.graphics.Typeface.NORMAL)
        }
    }

    selectionOverlay.show("Select Font", fontOptions, currentIndex, { index ->
        val selectedType = fontTypes[index]
        if (selectedType != "---") {
            editorView.setFontType(selectedType)
            if (isSettingsGlobal) {
                bufferManager.applyFontToAll()
                refreshActiveViews()
            }
            bufferManager.saveBuffers()
            showFontSettingsMenu()
        }
    }, { showFontSettingsMenu() }, fonts = typefaces, fontSize = buffer.fontSize, lineSpacing = buffer.lineSpacing)
}

internal fun MainActivity.showMarginSettingsMenu() {
    val options = listOf("Side Margin", "Top/Bottom Margin", "Apply Margins Globally", "Back")
    selectionOverlay.show("Margin Settings", options, 0, {
        when(it) {
            0 -> showSideMarginMenu()
            1 -> showTopMarginMenu()
            2 -> {
                bufferManager.applyMarginsToAll()
                showStatusToast("Margin settings applied to all buffers")
                showMarginSettingsMenu()
            }
            3 -> showAppMenu(6)
        }
    }, { showAppMenu(6) })
}

internal fun MainActivity.showSideMarginMenu() {
    val vals = listOf(0f, 40f, 60f, 100f, 150f)
    val options = vals.map { "${it.toInt()}px" } + "Custom..."
    selectionOverlay.show("Side Margin", options, 0, {
        if (it < 5) {
            editorView.setSideMargin(vals[it])
            if (isSettingsGlobal) bufferManager.applyMarginsToAll()
            bufferManager.saveBuffers()
        }
        else inputOverlay.show("Enter Side Margin (px):", bufferManager.getCurrentBuffer().sideMargin.toString(), { v ->
            editorView.setSideMargin(v.toFloatOrNull() ?: 60f)
            if (isSettingsGlobal) bufferManager.applyMarginsToAll()
            bufferManager.saveBuffers(); showSideMarginMenu()
        }, { showSideMarginMenu() })
    }, { showMarginSettingsMenu() })
}

internal fun MainActivity.showTopMarginMenu() {
    val vals = listOf(0f, 40f, 60f, 100f, 150f)
    val options = vals.map { "${it.toInt()}px" } + "Custom..."
    selectionOverlay.show("Top/Bottom Margin", options, 0, {
        if (it < 5) {
            editorView.setTopBottomMargin(vals[it])
            if (isSettingsGlobal) bufferManager.applyMarginsToAll()
            bufferManager.saveBuffers()
        }
        else inputOverlay.show("Enter Top Margin (px):", bufferManager.getCurrentBuffer().topBottomMargin.toString(), { v ->
            editorView.setTopBottomMargin(v.toFloatOrNull() ?: 60f)
            if (isSettingsGlobal) bufferManager.applyMarginsToAll()
            bufferManager.saveBuffers(); showTopMarginMenu()
        }, { showTopMarginMenu() })
    }, { showMarginSettingsMenu() })
}

internal fun MainActivity.showThemeSettingsMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val options = listOf(
        "Theme Selection",
        "Keyboard Background Theme",
        "Day/Night Mode Settings",
        "App Menu Theme: ${if (menuColorMode == 1) "Light" else "Dark"}",
        "Apply Theme Globally"
    )
    selectionOverlay.show("Theme Settings", options, 0, {
        when(it) {
            0 -> showThemeSelectionMenu()
            1 -> {
                buffer.keyboardThemeBg = !buffer.keyboardThemeBg
                if (isSettingsGlobal) bufferManager.applyThemeToAll()
                bufferManager.saveBuffers(); applyTheme(); showThemeSettingsMenu()
            }
            2 -> showDayNightSettingsMenu()
            3 -> {
                menuColorMode = 1 - menuColorMode
                saveGlobalSettings()
                updateMenuFontSizes()
                showThemeSettingsMenu()
            }
            4 -> {
                bufferManager.applyThemeToAll()
                refreshActiveViews()
                showStatusToast("Theme settings applied to all buffers")
                showThemeSettingsMenu()
            }
        }
    }, { showAppMenu(6) })
}

internal fun MainActivity.showThemeSelectionMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val isDay = isDaytime()
    val currentIdx = if (isDay) buffer.dayThemeIndex else buffer.nightThemeIndex

    val filteredThemes = if (deviceType == 2) {
        val monoNames = listOf("Classic", "E-Ink", "1950s CRT", "Soft Paper Gray", "Cool Light Gray", "Mid Gray")
        themes.filter { it.name in monoNames }
    } else {
        themes
    }

    val options = filteredThemes.map { it.name } + (if (deviceType != 2) listOf("Custom...") else emptyList())
    val previews = filteredThemes.map { SelectionOverlay.ThemeColors(it.textColor, it.backgroundColor) }

    val selectedName = themes.getOrNull(currentIdx)?.name
    val initialIdx = filteredThemes.indexOfFirst { it.name == selectedName }.coerceAtLeast(0)

    selectionOverlay.show("Select Theme (${if (isDay) "Day" else "Night"})", options, initialIdx, { index ->
        if (index < filteredThemes.size) {
            val themeName = filteredThemes[index].name
            val realIdx = themes.indexOfFirst { it.name == themeName }
            if (realIdx != -1) {
                if (isDay) buffer.dayThemeIndex = realIdx else buffer.nightThemeIndex = realIdx
                bufferManager.saveBuffers(); applyTheme(); showThemeSelectionMenu()
            }
        } else if (deviceType != 2) {
            showCustomThemeMenu()
        }
    }, { showThemeSettingsMenu() }, previews)
}

internal fun MainActivity.showCustomThemeMenu() {
    inputOverlay.show("Enter Name|TextHex|BgHex:", "MyTheme|#FFFFFF|#000000", { input ->
        val pts = input.split("|")
        if (pts.size == 3) {
            try {
                val name = pts[0]
                val tc = android.graphics.Color.parseColor(pts[1])
                val bc = android.graphics.Color.parseColor(pts[2])
                themes.add(MainActivity.EditorTheme(name, tc, bc, tc))
                saveGlobalSettings()
                showThemeSelectionMenu()
            } catch (e: Exception) { Toast.makeText(this, "Invalid format", Toast.LENGTH_SHORT).show() }
        }
    }, { showThemeSelectionMenu() })
}

internal fun MainActivity.showDayNightSettingsMenu() {
    val options = listOf(
        "Mode: ${when(themeMode) { 0 -> "Auto"; 1 -> "Always Day"; else -> "Always Night" }}",
        "Day Start: $dayStartHour:${dayStartMin.toString().padStart(2, '0')}",
        "Night Start: $nightStartHour:${nightStartMin.toString().padStart(2, '0')}",
        "Back"
    )
    selectionOverlay.show("Day/Night Mode", options, 0, {
        when(it) {
            0 -> { themeMode = (themeMode + 1) % 3; saveGlobalSettings(); applyTheme(); showDayNightSettingsMenu() }
            1 -> inputOverlay.show("Day Start (HH:MM):", "$dayStartHour:$dayStartMin", { v ->
                val pts = v.split(":")
                if (pts.size == 2) { dayStartHour = pts[0].toIntOrNull() ?: 7; dayStartMin = pts[1].toIntOrNull() ?: 0 }
                saveGlobalSettings(); applyTheme(); showDayNightSettingsMenu()
            }, { showDayNightSettingsMenu() })
            2 -> inputOverlay.show("Night Start (HH:MM):", "$nightStartHour:$nightStartMin", { v ->
                val pts = v.split(":")
                if (pts.size == 2) { nightStartHour = pts[0].toIntOrNull() ?: 19; nightStartMin = pts[1].toIntOrNull() ?: 0 }
                saveGlobalSettings(); applyTheme(); showDayNightSettingsMenu()
            }, { showDayNightSettingsMenu() })
            3 -> showThemeSettingsMenu()
        }
    }, { showThemeSettingsMenu() })
}

internal fun MainActivity.showTextDisplayMenu() {
    val options = listOf(
        "CRLF Display",
        "Unwrap Paragraphs",
        "Tab Size",
        "Page Jump Length",
        "PDF Favorites Size: ${bufferManager.pdfFavoritesLimit}",
        "Menus and Help...",
        "Back"
    )
    selectionOverlay.show("Text Display", options, 0, {
        when(it) {
            0 -> showCrlfDisplayMenu()
            1 -> commandDispatcher.dispatch(Command.UnwrapParagraphs)
            2 -> showTabSizeMenu()
            3 -> showPageLengthMenu()
            4 -> showPdfFavoritesLimitMenu()
            5 -> showMenusAndHelpMenu()
            6 -> showAdvancedFeaturesMenu()
        }
    }, { showAdvancedFeaturesMenu() })
}

internal fun MainActivity.showMenusAndHelpMenu() {
    val options = listOf(
        "Menu Font Size",
        "Status Bar Font Size",
        "Startup Help Message: ${if (isStartupHelpEnabled) "ON" else "OFF"}",
        "Startup Overview: ${if (isStartupOverviewEnabled) "ON" else "OFF"}",
        "Help Task-Switching: ${if (isHelpTaskSwitchEnabled) "ON" else "OFF"}",
        "Back"
    )
    selectionOverlay.show("Menus and Help", options, 0, {
        when(it) {
            0 -> showMenuFontSizeMenu()
            1 -> showStatusFontSizeMenu()
            2 -> {
                isStartupHelpEnabled = !isStartupHelpEnabled
                saveGlobalSettings()
                showMenusAndHelpMenu()
            }
            3 -> {
                isStartupOverviewEnabled = !isStartupOverviewEnabled
                if (isStartupOverviewEnabled) isOverviewPending = true
                saveGlobalSettings()
                showMenusAndHelpMenu()
            }
            4 -> {
                isHelpTaskSwitchEnabled = !isHelpTaskSwitchEnabled
                saveGlobalSettings()
                showMenusAndHelpMenu()
            }
            5 -> showTextDisplayMenu()
        }
    }, { showTextDisplayMenu() })
}

internal fun MainActivity.showPdfFavoritesLimitMenu() {
    val options = listOf("8", "16", "32", "64", "Back")
    val currentIdx = when(bufferManager.pdfFavoritesLimit) {
        16 -> 1
        32 -> 2
        64 -> 3
        else -> 0
    }
    selectionOverlay.show("PDF Favorites Size", options, currentIdx, { index ->
        val newLimit = when(index) {
            0 -> 8
            1 -> 16
            2 -> 32
            3 -> 64
            else -> { showTextDisplayMenu(); return@show }
        }
        bufferManager.pdfFavoritesLimit = newLimit
        for (b in bufferManager.getAllBuffers()) {
            while (b.pdfFavorites.size > newLimit) {
                b.pdfFavorites.removeAt(0)
            }
        }
        saveGlobalSettings()
        bufferManager.saveBuffers()
        showTextDisplayMenu()
    }, { showTextDisplayMenu() })
}

internal fun MainActivity.showStatusFontSizeMenu() {
    val options = listOf("Small", "Medium", "Large", "Back")
    selectionOverlay.show("Status Bar Font Size", options, statusFontSizeMode, { index ->
        if (index < 3) {
            statusFontSizeMode = index
            saveGlobalSettings()
            updateStatusFontSize()
            showStatusFontSizeMenu()
        } else {
            showMenusAndHelpMenu()
        }
    }, { showMenusAndHelpMenu() })
}

internal fun MainActivity.showMenuFontSizeMenu() {
    val options = listOf("Small", "Medium", "Large", "X-Large", "Back")
    selectionOverlay.show("Menu Font Size", options, menuFontSizeMode, { index ->
        if (index < 4) {
            menuFontSizeMode = index
            saveGlobalSettings()
            updateMenuFontSizes()
            showMenuFontSizeMenu()
        } else {
            showMenusAndHelpMenu()
        }
    }, { showMenusAndHelpMenu() })
}

internal fun MainActivity.showPageLengthMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val vals = listOf(10, 20, 30, 40, 50)
    val options = vals.map { "$it Lines" } + "Custom..."
    val currentIdx = vals.indexOf(buffer.pageLength).let { if (it == -1) vals.size else it }

    selectionOverlay.show("Page Jump Length", options, currentIdx, {
        if (it < vals.size) {
            buffer.pageLength = vals[it]
            bufferManager.saveBuffers()
            showTextDisplayMenu()
        } else {
            inputOverlay.show("Enter Lines per Page Jump:", buffer.pageLength.toString(), { v ->
                buffer.pageLength = v.toIntOrNull()?.coerceIn(1, 200) ?: 20
                bufferManager.saveBuffers()
                showTextDisplayMenu()
            }, { showPageLengthMenu() })
        }
    }, { showTextDisplayMenu() })
}

internal fun MainActivity.showTabSizeMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val vals = listOf(2, 4, 8)
    selectionOverlay.show("Tab Size (Spaces)", listOf("2", "4", "8", "Custom..."), vals.indexOf(buffer.tabSize).let { if (it == -1) 3 else it }, {
        if (it < 3) {
            buffer.tabSize = vals[it]
            bufferManager.saveBuffers()
            showTextDisplayMenu()
        } else {
            inputOverlay.show("Enter Tab Size:", buffer.tabSize.toString(), { v ->
                buffer.tabSize = v.toIntOrNull()?.coerceIn(1, 16) ?: 4
                bufferManager.saveBuffers()
                showTextDisplayMenu()
            }, { showTabSizeMenu() })
        }
    }, { showTextDisplayMenu() })
}

internal fun MainActivity.showCrlfDisplayMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val options = listOf("None", "End of line (\u21B5)", "Paragraph mark (\u00B6)", "Return symbol (\u23CE)")
    selectionOverlay.show("CRLF Display", options, buffer.lineEndingSymbol, { index ->
        buffer.lineEndingSymbol = index
        bufferManager.saveBuffers()
        editorView.invalidate()
        showTextDisplayMenu()
    }, { showTextDisplayMenu() })
}

internal fun MainActivity.showKeyboardModeMenu() {
    val options = listOf("Standard", "Vietnamese TELEX")
    selectionOverlay.show("Keyboard Mode", options, keyboardMode.ordinal, {
        commandDispatcher.dispatch(Command.SetKeyboardMode(it))
    }, { showAppMenu(4) })
}

internal fun MainActivity.showVirtualKeyboardSettingsMenu() {
    val options = listOf(
        "Virtual keyboard ${if (isVirtualKeyboardEnabled) "OFF" else "ON"}",
        "Predictive Text: ${if (isPredictiveModeEnabled) "ON" else "OFF"}",
        "Show Numeric Row: ${if (bufferManager.getCurrentBuffer().showNumericRow) "ON" else "OFF"}",
        "KB Background: ${if (bufferManager.getCurrentBuffer().keyboardThemeBg) "Theme BG" else "Theme Font"}",
        "Keyboard Outline: ${if (bufferManager.getCurrentBuffer().showKeyBorder) "ON" else "OFF"}",
        "Save Keyboard Size",
        "Save Keyboard Config",
        "Load Saved Keyboard",
        "Apply Keyboard Setting Globally",
        "Back"
    )
    selectionOverlay.show("Virtual Keyboard", options, 0, { index ->
        when (index) {
            0 -> toggleVirtualKeyboard()
            1 -> {
                togglePredictiveText()
                showVirtualKeyboardSettingsMenu()
            }
            2 -> {
                val buffer = bufferManager.getCurrentBuffer()
                buffer.showNumericRow = !buffer.showNumericRow
                bufferManager.saveBuffers()
                loadKeyboardLayout("en_alpha.json")
                showVirtualKeyboardSettingsMenu()
            }
            3 -> {
                val currentBuffer = bufferManager.getCurrentBuffer()
                currentBuffer.keyboardThemeBg = !currentBuffer.keyboardThemeBg
                if (isSettingsGlobal) bufferManager.applyThemeToAll()
                bufferManager.saveBuffers()
                applyTheme()
                showVirtualKeyboardSettingsMenu()
            }
            4 -> {
                val currentBuffer = bufferManager.getCurrentBuffer()
                currentBuffer.showKeyBorder = !currentBuffer.showKeyBorder
                if (isSettingsGlobal) bufferManager.applyThemeToAll()
                bufferManager.saveBuffers()
                applyTheme()
                showVirtualKeyboardSettingsMenu()
            }
            5 -> {
                val buffer = bufferManager.getCurrentBuffer()
                buffer.keyboardHeight = bufferManager.keyboardHeight
                bufferManager.saveBuffers()
                showStatusToast("Keyboard size saved to buffer")
                showVirtualKeyboardSettingsMenu()
            }
            6 -> {
                inputOverlay.show("Enter Config Name:", "MyDevice", { name ->
                    saveKeyboardConfig(name)
                    showVirtualKeyboardSettingsMenu()
                }, { showVirtualKeyboardSettingsMenu() })
            }
            7 -> showLoadKeyboardConfigMenu()
            8 -> {
                applyKeyboardSettingsGlobally()
                showVirtualKeyboardSettingsMenu()
            }
            9 -> showAdvancedFeaturesMenu()
        }
    }, { showAdvancedFeaturesMenu() })
}

internal fun MainActivity.showScratchpadSettingsMenu() {
    val currentFolder = workingFolderManager.currentFolder
    val folderLabel = if (currentFolder != null) " [Folder: ${currentFolder.name}]" else " [No Working Folder Set]"
    val options = listOf(
        "Char Limit: ${bufferManager.scratchCharLimit}",
        "Log Filename: ${bufferManager.scratchLogFilename}$folderLabel",
        "Working Folder: ${currentFolder?.name ?: "Set Working Folder"}",
        "Back"
    )
    selectionOverlay.show("Scratchpad Settings", options, 0, {
        when(it) {
            0 -> inputOverlay.show("Enter Char Limit:", bufferManager.scratchCharLimit.toString(), { v ->
                bufferManager.scratchCharLimit = v.toIntOrNull() ?: 280
                bufferManager.saveBuffers()
                showScratchpadSettingsMenu()
            }, { showScratchpadSettingsMenu() })
            1 -> {
                if (workingFolderManager.currentFolder == null && workingFolderManager.getFolders().isEmpty()) {
                    selectionOverlay.show("No Working Folder Set", listOf("Link Working Folder Now", "Cancel"), 0, { sel ->
                        if (sel == 0) {
                            linkWorkingFolderLauncher.launch(null)
                        } else {
                            showScratchpadSettingsMenu()
                        }
                    }, { showScratchpadSettingsMenu() })
                } else {
                    inputOverlay.show("Enter Log Filename:", bufferManager.scratchLogFilename, { v ->
                        if (v.isNotBlank()) {
                            bufferManager.scratchLogFilename = v.trim()
                            bufferManager.saveBuffers()
                        }
                        showScratchpadSettingsMenu()
                    }, { showScratchpadSettingsMenu() })
                }
            }
            2 -> {
                if (workingFolderManager.getFolders().isEmpty()) {
                    linkWorkingFolderLauncher.launch(null)
                } else {
                    showManageWorkingFoldersMenu()
                }
            }
            3 -> showAdvancedFeaturesMenu()
        }
    }, { showAdvancedFeaturesMenu() })
}

internal fun MainActivity.showCursorMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val options = listOf(
        "Style: ${buffer.cursorSettings.style.name.replace("_", " ")}",
        "Cursor Color",
        "Blink on Shortcut: ${if (buffer.cursorSettings.blinkOnShortcut) "ON" else "OFF"}",
        "Cursor Blink Always: ${if (buffer.cursorSettings.blinkAlways) "ON" else "OFF"}",
        "Blink Interval: ${buffer.cursorSettings.blinkIntervalMs}ms",
        "Locator Blink Duration: ${buffer.cursorSettings.locatorBlinkDurationMs / 1000}s",
        "Virtual Input (Supernote Fix): ${if (buffer.useVirtualInput) "ON" else "OFF"}",
        "Auto Typewriter (Large Files): ${if (buffer.autoTypewriterOnLargeFiles) "ON" else "OFF"}",
        "Apply Cursor Settings Globally",
        "Back"
    )
    val styles = listOf(buffer.cursorSettings.style, null, null, null, null, null, null, null, null, null)
    selectionOverlay.show("Cursor Settings", options, 0, { index ->
        when (index) {
            0 -> showCursorStyleMenu()
            1 -> showCursorColorMenu()
            2 -> {
                buffer.cursorSettings.blinkOnShortcut = !buffer.cursorSettings.blinkOnShortcut
                bufferManager.saveBuffers()
                editorView.refreshCursorSettings()
                showCursorMenu()
            }
            3 -> {
                buffer.cursorSettings.blinkAlways = !buffer.cursorSettings.blinkAlways
                bufferManager.saveBuffers()
                editorView.refreshCursorSettings()
                showCursorMenu()
            }
            4 -> showCursorIntervalMenu()
            5 -> showCursorLocatorDurationMenu()
            6 -> {
                buffer.useVirtualInput = !buffer.useVirtualInput
                bufferManager.saveBuffers()
                editorView.invalidate()
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.restartInput(editorView)
                showCursorMenu()
            }
            7 -> {
                buffer.autoTypewriterOnLargeFiles = !buffer.autoTypewriterOnLargeFiles
                bufferManager.saveBuffers()
                editorView.invalidate()
                showCursorMenu()
            }
            8 -> {
                bufferManager.applyCursorToAll()
                showStatusToast("Cursor settings applied to all buffers")
                showCursorMenu()
            }
            9 -> showAppMenu(7)
        }
    }, { showAppMenu(7) }, cursorStyles = styles)
}

internal fun MainActivity.showCursorColorMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val options = listOf(
        "Use Theme Font Color",
        "Solarized Yellow",
        "Solarized Orange",
        "Solarized Red",
        "Solarized Magenta",
        "Solarized Blue",
        "Solarized Green",
        "Custom RGB...",
        "Back"
    )

    val solarized = listOf(
        Color.parseColor("#b58900"), // Yellow
        Color.parseColor("#cb4b16"), // Orange
        Color.parseColor("#dc322f"), // Red
        Color.parseColor("#d33682"), // Magenta
        Color.parseColor("#268bd2"), // Blue
        Color.parseColor("#859900")  // Green
    )

    val previews = mutableListOf<SelectionOverlay.ThemeColors>()
    previews.add(SelectionOverlay.ThemeColors(themes[if (isDaytime()) buffer.dayThemeIndex else buffer.nightThemeIndex].textColor, Color.TRANSPARENT))
    solarized.forEach { previews.add(SelectionOverlay.ThemeColors(it, Color.TRANSPARENT)) }
    previews.add(SelectionOverlay.ThemeColors(Color.WHITE, Color.TRANSPARENT)) // Custom placeholder

    selectionOverlay.show("Cursor Color", options, 0, { index ->
        when (index) {
            0 -> {
                buffer.cursorColor = null
                bufferManager.saveBuffers()
                applyTheme()
                showCursorColorMenu()
            }
            in 1..6 -> {
                buffer.cursorColor = solarized[index - 1]
                bufferManager.saveBuffers()
                applyTheme()
                showCursorColorMenu()
            }
            7 -> {
                inputOverlay.show("Enter RGB (e.g. 0,255,0):", "0,255,0", { rgb ->
                    try {
                        val pts = rgb.split(",").map { it.trim().toInt().coerceIn(0, 255) }
                        if (pts.size == 3) {
                            buffer.cursorColor = Color.rgb(pts[0], pts[1], pts[2])
                            bufferManager.saveBuffers()
                            applyTheme()
                        }
                        showCursorColorMenu()
                    } catch (e: Exception) {
                        showStatusToast("Invalid RGB format")
                        showCursorColorMenu()
                    }
                }, { showCursorColorMenu() })
            }
            8 -> showCursorMenu()
        }
    }, { showCursorMenu() }, previews = previews, isSolid = true)
}

internal fun MainActivity.showCursorLocatorDurationMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val options = listOf("1s", "2s", "3s", "5s", "10s", "Custom...")
    val vals = listOf(1000L, 2000L, 3000L, 5000L, 10000L)
    selectionOverlay.show("Locator Duration", options, vals.indexOf(buffer.cursorSettings.locatorBlinkDurationMs).coerceAtLeast(0), {
        if (it < 5) { buffer.cursorSettings.locatorBlinkDurationMs = vals[it]; bufferManager.saveBuffers(); showCursorMenu() }
        else inputOverlay.show("Enter Duration (ms):", buffer.cursorSettings.locatorBlinkDurationMs.toString(), { v ->
            buffer.cursorSettings.locatorBlinkDurationMs = v.toLongOrNull() ?: 3000L
            bufferManager.saveBuffers(); showCursorMenu()
        }, { showCursorMenu() })
    }, { showCursorMenu() })
}

internal fun MainActivity.showTypewriterModeMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val settings = buffer.cursorSettings.typewriter
    val options = listOf(
        "Typewriter Active: ${if (settings.isEnabled) "ON" else "OFF"}",
        "Stationary Cursor: ${if (settings.isFixed) "ON" else "OFF"}",
        "Position: ${settings.anchor.name}",
        "Type Guide Graphic: ${if (settings.showGuide) "ON" else "OFF"}",
        "Terminal Mode: ${if (settings.isTerminalMode) "ON" else "OFF"}",
        "Char Delay: ${settings.charDelayMs}ms",
        "CRLF Delay: ${settings.crlfDelayMs}ms",
        "Back"
    )
    selectionOverlay.show("Typewriter Mode", options, 0, {
        when(it) {
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
            7 -> showAppMenu(5)
        }
    }, { showAppMenu(5) })
}

internal fun MainActivity.showTypewriterCrlfDelayMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val settings = buffer.cursorSettings.typewriter
    val options = listOf("0ms", "25ms", "50ms", "75ms", "100ms", "125ms", "150ms", "175ms", "200ms", "Back")
    val values = listOf(0L, 25L, 50L, 75L, 100L, 125L, 150L, 175L, 200L)
    val currentIdx = values.indexOf(settings.crlfDelayMs).coerceAtLeast(0)

    selectionOverlay.show("Typewriter CRLF Delay", options, currentIdx, { index ->
        if (index < values.size) {
            val newDelay = values[index]
            for (b in bufferManager.getAllBuffers()) {
                b.cursorSettings.typewriter.crlfDelayMs = newDelay
            }
            bufferManager.saveBuffers()
            showAppMenu(5)
        } else {
            showAppMenu(5)
        }
    }, { showAppMenu(5) })
}

internal fun MainActivity.showTypewriterDelayMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val settings = buffer.cursorSettings.typewriter
    val options = listOf("0ms", "25ms", "50ms", "75ms", "100ms", "125ms", "150ms", "175ms", "200ms", "Back")
    val values = listOf(0L, 25L, 50L, 75L, 100L, 125L, 150L, 175L, 200L)
    val currentIdx = values.indexOf(settings.charDelayMs).coerceAtLeast(0)

    selectionOverlay.show("Typewriter Char Delay", options, currentIdx, { index ->
        if (index < values.size) {
            val newDelay = values[index]
            for (b in bufferManager.getAllBuffers()) {
                b.cursorSettings.typewriter.charDelayMs = newDelay
            }
            bufferManager.saveBuffers()
            showAppMenu(5)
        } else {
            showAppMenu(5)
        }
    }, { showAppMenu(5) })
}

internal fun MainActivity.showTypewriterPositionMenu() {
    val settings = bufferManager.getCurrentBuffer().cursorSettings.typewriter
    val options = listOf("Center", "Bottom", "Back")
    selectionOverlay.show("Typewriter Position", options, settings.anchor.ordinal, {
        when(it) {
            0 -> {
                for (b in bufferManager.getAllBuffers()) {
                    b.cursorSettings.typewriter.anchor = EditorBuffer.TypewriterAnchor.CENTER
                }
                bufferManager.saveBuffers()
                if (settings.isEnabled) editorView.recenterTypewriter()
                editorView.invalidate()
                showAppMenu(5)
            }
            1 -> {
                for (b in bufferManager.getAllBuffers()) {
                    b.cursorSettings.typewriter.anchor = EditorBuffer.TypewriterAnchor.BOTTOM
                }
                bufferManager.saveBuffers()
                if (settings.isEnabled) editorView.recenterTypewriter()
                editorView.invalidate()
                showAppMenu(5)
            }
            2 -> showAppMenu(5)
        }
    }, { showTypewriterModeMenu() })
}

internal fun MainActivity.showCursorStyleMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val styles = com.example.doswriter.model.EditorBuffer.CursorStyle.values().toList()
    val options = styles.map { it.name.replace("_", " ") }
    selectionOverlay.show("Cursor Style", options, buffer.cursorSettings.style.ordinal, {
        buffer.cursorSettings.style = styles[it]
        bufferManager.saveBuffers(); editorView.invalidate(); showCursorMenu()
    }, { showCursorMenu() }, cursorStyles = styles)
}

internal fun MainActivity.showCursorIntervalMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val options = listOf("200ms", "300ms", "500ms", "800ms", "1000ms", "Custom...")
    val vals = listOf(200L, 300L, 500L, 800L, 1000L)
    selectionOverlay.show("Blink Interval", options, vals.indexOf(buffer.cursorSettings.blinkIntervalMs).coerceAtLeast(0), {
        if (it < 5) { buffer.cursorSettings.blinkIntervalMs = vals[it]; bufferManager.saveBuffers(); showCursorMenu() }
        else inputOverlay.show("Enter Interval (ms):", buffer.cursorSettings.blinkIntervalMs.toString(), { v ->
            buffer.cursorSettings.blinkIntervalMs = v.toLongOrNull() ?: 500L
            bufferManager.saveBuffers(); showCursorMenu()
        }, { showCursorMenu() })
    }, { showCursorMenu() })
}

internal fun MainActivity.showSyncMenu() {
    val connected = syncManager.isConnected()
    val currentSyncPath = bufferManager.getCurrentBuffer().syncFilePath
    val options = mutableListOf<String>()
    options.add("1. ${if (connected) "Disconnect" else "Connect"}")
    options.add("2. Host IP: ${bufferManager.syncHost}")
    options.add("3. Port: ${bufferManager.syncPort}")
    options.add("4. Token: ${if (bufferManager.syncToken.isEmpty()) "(none)" else "****"}")
    options.add("5. Auto-Push: ${if (bufferManager.isAutoSyncEnabled) "ON" else "OFF"}")
    options.add("6. Timeout: ${bufferManager.autoSyncIntervalMins} min")

    if (connected) {
        options.add("7. Sync with Remote File...")
        options.add("8. Save Buffer to Remote Host...")
        if (currentSyncPath != null) options.add("9. Manual Push Update")
        options.add("10. Transfer Local File to Remote Host")
    }

    selectionOverlay.show("Desktop Sync", options, 0, { index ->
        val selectedOption = options[index]
        when {
            selectedOption.startsWith("1.") -> { if (connected) syncManager.disconnect() else syncManager.connect(); showSyncMenu() }
            selectedOption.startsWith("2.") -> inputOverlay.show("Enter Host IP:", bufferManager.syncHost, { v -> bufferManager.syncHost = v; bufferManager.saveBuffers(); showSyncMenu() }, { showSyncMenu() })
            selectedOption.startsWith("3.") -> inputOverlay.show("Enter Port:", bufferManager.syncPort.toString(), { v -> bufferManager.syncPort = v.toIntOrNull() ?: 12345; bufferManager.saveBuffers(); showSyncMenu() }, { showSyncMenu() })
            selectedOption.startsWith("4.") -> inputOverlay.show("Enter Token:", "", { v -> bufferManager.syncToken = v; bufferManager.saveBuffers(); showSyncMenu() }, { showSyncMenu() })
            selectedOption.startsWith("5.") -> { bufferManager.isAutoSyncEnabled = !bufferManager.isAutoSyncEnabled; bufferManager.saveBuffers(); showSyncMenu() }
            selectedOption.startsWith("6.") -> inputOverlay.show("Auto-Push Timeout (1-30 min):", bufferManager.autoSyncIntervalMins.toString(), { v ->
                bufferManager.autoSyncIntervalMins = v.toIntOrNull()?.coerceIn(1, 30) ?: 1
                bufferManager.saveBuffers(); showSyncMenu()
            }, { showSyncMenu() })
            selectedOption.startsWith("7.") -> {
                if (connected) {
                    showRemoteFilePicker(".", { showSyncMenu() }) { path ->
                        val ext = path.substringAfterLast(".", "").lowercase()
                        if (ext in listOf("txt", "md", "html", "css", "json", "xml", "epol", "csv", "log") || ext.isEmpty() || !path.contains(".")) {
                            syncManager.openRemoteFile(path)
                        } else {
                            Toast.makeText(this, "Remote Sync supports text files only", Toast.LENGTH_LONG).show()
                            showSyncMenu()
                        }
                    }
                }
            }
            selectedOption.startsWith("8.") -> {
                if (connected) {
                    val buffer = bufferManager.getCurrentBuffer()
                    val defaultName = buffer.syncFilePath ?: (buffer.fileName ?: "new_file.txt")
                    inputOverlay.show("Remote Filename:", defaultName, { name ->
                        val ext = name.substringAfterLast(".", "").lowercase()
                        if (ext in listOf("txt", "md", "html", "css", "json", "xml", "epol", "csv", "log") || ext.isEmpty() || !name.contains(".")) {
                            syncManager.saveRemoteFile(name, buffer.getCleanText())
                            buffer.syncFilePath = name
                            buffer.fileName = "[REMOTE] ${name.substringAfterLast("/")}"
                            recentFileManager.addFile(name, name.substringAfterLast("/"), true)
                            buffer.markClean(); bufferManager.saveBuffers(); updateStatus(); showSyncMenu()
                        } else {
                            Toast.makeText(this, "Remote Sync supports text files only", Toast.LENGTH_LONG).show()
                            showSyncMenu()
                        }
                    }, { showSyncMenu() })
                }
            }
            selectedOption.startsWith("9.") -> {
                if (connected) {
                    val buffer = bufferManager.getCurrentBuffer()
                    val path = buffer.syncFilePath
                    if (path != null) {
                        syncManager.saveRemoteFile(path, buffer.getCleanText())
                        buffer.markClean(); updateStatus(); showStatusToast("Pushing update...")
                    }
                }
            }
            selectedOption.startsWith("10.") -> { if (connected) { handleOverlayCommand(Command.OverlayCancel); transferLocalFileLauncher.launch(arrayOf("*/*")) } }
        }
    }, {})
}

internal fun MainActivity.showHardwareMenu() {
    val buffer = bufferManager.getCurrentBuffer()
    val displayTypeLabel = when(deviceType) { 0 -> "Backlit LCD/OLED"; 1 -> "Color E-Ink"; 2 -> "Monochrome E-Ink"; else -> "Not Set" }
    val manufacturerLabel = when(hardwareManufacturer) { 0 -> "Auto Detect"; 1 -> "BOOX (Onyx)"; 2 -> "Bigme"; 3 -> "Supernote"; else -> "Other" }
    val profileLabel = when(buffer.deviceProfile) { 0 -> "Phone"; 1 -> "Tablet"; 2 -> "Custom (E-ink)"; else -> "Phone" }
    
    val options = listOf(
        "Display Type: $displayTypeLabel",
        "Manufacturer: $manufacturerLabel",
        "Device Screen Profile: $profileLabel",
        "Active Provider: ${hardwareProvider.getProviderName()}",
        "Suppress System Clipboard: ${if (isSystemClipboardSuppressed) "ON" else "OFF"}",
        "Set Device Wallpaper",
        "Test Hardware",
        "Re-run Auto-Detection",
        "Back"
    )
    selectionOverlay.show("Hardware Settings", options, 0, {
        when(it) {
            0 -> showDisplayTypeMenu()
            1 -> showManufacturerMenu()
            2 -> showDeviceProfileMenu()
            3 -> if (hardwareProvider.getCapabilities().hasRefreshModes) showRefreshModeMenu()
            4 -> { isSystemClipboardSuppressed = !isSystemClipboardSuppressed; saveGlobalSettings(); showHardwareMenu(); showStatusToast("System Clipboard Suppression: ${if (isSystemClipboardSuppressed) "ON" else "OFF"}") }
            5 -> showWallpaperMenu()
            6 -> showHardwareTest()
            7 -> wizardStep1DisplayType()
            8 -> showAdvancedFeaturesMenu()
        }
    }, { showAdvancedFeaturesMenu() })
}

internal fun MainActivity.showDeviceProfileMenu() {
    val options = listOf("Phone", "Tablet", "Custom (E-ink)", "Back")
    val currentIdx = bufferManager.getCurrentBuffer().deviceProfile
    selectionOverlay.show("Select Device Screen Profile", options, currentIdx, { index ->
        if (index < 3) {
            val buffer = bufferManager.getCurrentBuffer()
            buffer.deviceProfile = index
            if (isSettingsGlobal) {
                for (b in bufferManager.getAllBuffers()) {
                    b.deviceProfile = index
                }
            }
            editorView.recalculateCurrentPreset()
            bufferManager.saveBuffers()
            showHardwareMenu()
        } else {
            showHardwareMenu()
        }
    }, { showHardwareMenu() })
}

internal fun MainActivity.showRefreshModeMenu() {
    val options = listOf("Normal", "Regal (HD)", "A2 (Fast)", "X-Mode (Video)", "Back")
    selectionOverlay.show("Refresh Mode", options, 0, {
        if (it < 4) {
            val modes = listOf(com.example.doswriter.logic.hal.RefreshMode.NORMAL, com.example.doswriter.logic.hal.RefreshMode.REGAL, com.example.doswriter.logic.hal.RefreshMode.A2, com.example.doswriter.logic.hal.RefreshMode.XMODE)
            hardwareProvider.setRefreshMode(modes[it], this); showHardwareMenu()
        } else showHardwareMenu()
    }, { showHardwareMenu() })
}

internal fun MainActivity.showDisplayTypeMenu() {
    val options = listOf("Backlit LCD/OLED", "Color E-Ink", "Monochrome E-Ink", "Back")
    selectionOverlay.show("Select Display Type", options, deviceType.coerceAtLeast(0), {
        if (it < 3) { deviceType = it; saveGlobalSettings(); showHardwareMenu() }
        else showHardwareMenu()
    }, { showHardwareMenu() })
}

internal fun MainActivity.showManufacturerMenu() {
    val options = listOf("Auto Detect", "BOOX (Onyx)", "Bigme", "Supernote", "Other", "Back")
    selectionOverlay.show("Select Manufacturer", options, hardwareManufacturer, {
        if (it < 5) { hardwareManufacturer = it; saveGlobalSettings(); showHardwareMenu() }
        else showHardwareMenu()
    }, { showHardwareMenu() })
}

internal fun MainActivity.showHardwareTest() {
    val info = com.example.doswriter.logic.DeviceProfiler(this).getDeviceInfo()
    val details = listOf(
        "Manufacturer: ${info.manufacturer}",
        "Model: ${info.model}",
        "Android: ${info.apiLevel}",
        "--- Provider ---",
        hardwareProvider.getDiagnosticInfo(),
        "--- Capabilities ---",
        "Frontlight: ${if (hardwareProvider.getCapabilities().hasFrontlight) "YES" else "NO"}",
        "Warm Light: ${if (hardwareProvider.getCapabilities().hasWarmLight) "YES" else "NO"}",
        "Refresh Modes: ${if (hardwareProvider.getCapabilities().hasRefreshModes) "YES" else "NO"}",
        "Full Refresh: ${if (hardwareProvider.getCapabilities().hasFullRefresh) "YES" else "NO"}",
        "Back"
    )
    selectionOverlay.show("Hardware Test", details, details.size - 1, { showHardwareMenu() }, { showHardwareMenu() })
}

internal fun MainActivity.showWallpaperMenu() {
    val options = listOf("Solid Black", "Solid White", "Warm Wheat", "Muted Gray", "Back")
    val colors = listOf(android.graphics.Color.BLACK, android.graphics.Color.WHITE, android.graphics.Color.rgb(245, 222, 179), android.graphics.Color.rgb(60, 60, 60))
    selectionOverlay.show("Set Device Wallpaper", options, 0, {
        if (it < 4) {
            try {
                val wpManager = android.app.WallpaperManager.getInstance(this)
                val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.setPixel(0, 0, colors[it])
                wpManager.setBitmap(bitmap); showStatusToast("Wallpaper updated")
            } catch (e: Exception) { Toast.makeText(this, "Permission required", Toast.LENGTH_SHORT).show() }
        }
        showAppMenu(5)
    }, { showAppMenu(6) })
}

internal fun MainActivity.showImageSettingsMenu() {
    val options = listOf("Link Local Folder", "Slideshow Interval", "Manage Linked Folders", "Buffer Thumbnails", "Back")
    selectionOverlay.show("Image Settings", options, 0, {
        when(it) {
            0 -> openFolderLauncher.launch(null)
            1 -> inputOverlay.show("Slideshow Interval (sec):", imageFolderManager.slideshowIntervalSec.toString(), { v ->
                imageFolderManager.slideshowIntervalSec = v.toIntOrNull()?.coerceIn(1, 3600) ?: 5
                imageFolderManager.saveFolders(); showImageSettingsMenu()
            }, { showImageSettingsMenu() })
            2 -> showManageFoldersMenu()
            3 -> showBufferThumbnailsSettingsMenu()
            4 -> showAppMenu(7)
        }
    }, { showAppMenu(7) })
}

internal fun MainActivity.showBufferThumbnailsSettingsMenu() {
    val options = listOf(
        "Set Thumbnail Images",
        "Set Global Image",
        "Thumbnail Text",
        "Thumbnail Text Color: ${when(bufferManager.thumbnailTextColorMode) { 1 -> "Dark"; 2 -> "Light"; else -> "Auto" }}",
        "Use Theme Colors: ${if (bufferManager.useThemeColorsForThumbnail) "ON" else "OFF"}",
        "Overlay Text on Image: ${if (bufferManager.overlayTextOnImage) "ON" else "OFF"}",
        "Back"
    )
    selectionOverlay.show("Buffer Thumbnails", options, 0, {
        when(it) {
            0 -> showSetBufferThumbnailsMenu()
            1 -> showSetGlobalThumbnailMenu()
            2 -> showThumbnailTextMenu()
            3 -> {
                bufferManager.thumbnailTextColorMode = (bufferManager.thumbnailTextColorMode + 1) % 3
                bufferManager.saveBuffers()
                showBufferThumbnailsSettingsMenu()
            }
            4 -> {
                bufferManager.useThemeColorsForThumbnail = !bufferManager.useThemeColorsForThumbnail
                bufferManager.saveBuffers()
                showBufferThumbnailsSettingsMenu()
            }
            5 -> {
                bufferManager.overlayTextOnImage = !bufferManager.overlayTextOnImage
                bufferManager.saveBuffers()
                showBufferThumbnailsSettingsMenu()
            }
            6 -> showImageSettingsMenu()
        }
    }, { showImageSettingsMenu() })
}

internal fun MainActivity.showThumbnailTextMenu() {
    val options = listOf(
        "Use Filename: ${if (bufferManager.useFilenameForThumbnailText) "ON" else "OFF"}",
        "Define Thumbnail Text",
        "Back"
    )
    selectionOverlay.show("Thumbnail Text", options, 0, {
        when(it) {
            0 -> {
                bufferManager.useFilenameForThumbnailText = !bufferManager.useFilenameForThumbnailText
                bufferManager.saveBuffers()
                showThumbnailTextMenu()
            }
            1 -> showDefineThumbnailTextMenu()
            2 -> showBufferThumbnailsSettingsMenu()
        }
    }, { showBufferThumbnailsSettingsMenu() })
}

internal fun MainActivity.showSetGlobalThumbnailMenu() {
    val isSet = bufferManager.globalThumbnailUri != null
    val options = if (isSet) {
        listOf("Replace Global Image", "Remove Global Image", "Back")
    } else {
        listOf("Link Global Image", "Back")
    }
    
    val title = if (isSet) {
        "Global: [${getFileName(Uri.parse(bufferManager.globalThumbnailUri!!))}]"
    } else {
        "Set Global Image"
    }

    selectionOverlay.show(title, options, 0, { index ->
        if (isSet) {
            when(index) {
                0 -> {
                    pendingThumbnailBufferIdx = 10
                    if (bufferManager.useWorkstationFileBrowser) {
                        val folder = workingFolderManager.lastUsedFolderUri ?: workingFolderManager.currentFolder?.path
                        showFileBrowser(FileBrowserOverlay.Mode.BROWSE, folder, null, onThumbnailSelect = { uri ->
                            performThumbnailLink(uri, 10)
                        })
                    } else {
                        pickThumbnailLauncher.launch(arrayOf("image/*"))
                    }
                }
                1 -> { bufferManager.globalThumbnailUri = null; bufferManager.saveBuffers(); showSetGlobalThumbnailMenu() }
                else -> showBufferThumbnailsSettingsMenu()
            }
        } else {
            when(index) {
                0 -> {
                    pendingThumbnailBufferIdx = 10
                    if (bufferManager.useWorkstationFileBrowser) {
                        val folder = workingFolderManager.lastUsedFolderUri ?: workingFolderManager.currentFolder?.path
                        showFileBrowser(FileBrowserOverlay.Mode.BROWSE, folder, null, onThumbnailSelect = { uri ->
                            performThumbnailLink(uri, 10)
                        })
                    } else {
                        pickThumbnailLauncher.launch(arrayOf("image/*"))
                    }
                }
                else -> showBufferThumbnailsSettingsMenu()
            }
        }
    }, { showBufferThumbnailsSettingsMenu() })
}

internal fun MainActivity.showDefineThumbnailTextMenu() {
    val options = (0 until 8).map { i ->
        val b = bufferManager.getBufferAt(i)
        val text = b.thumbnailText ?: "[None]"
        "B${i + 1}: $text"
    } + "Back"

    selectionOverlay.show("Define Thumbnail Text", options, 0, { index ->
        if (index < 8) {
            val b = bufferManager.getBufferAt(index)
            inputOverlay.show("Enter Thumbnail Text (Max 48):", b.thumbnailText ?: "", { input ->
                val cleanInput = if (input.length > 48) input.take(48) else input
                b.thumbnailText = if (cleanInput.isEmpty()) null else cleanInput
                if (b.thumbnailText != null) b.useThumbnail = true
                bufferManager.saveBuffers()
                showDefineThumbnailTextMenu()
            }, { showDefineThumbnailTextMenu() })
        } else {
            showThumbnailTextMenu()
        }
    }, { showThumbnailTextMenu() })
}

internal fun MainActivity.showSetBufferThumbnailsMenu() {
    val options = (0 until 8).map { i ->
        val b = bufferManager.getBufferAt(i)
        val status = if (b.thumbnailUri != null) {
            if (b.useThumbnail) "[${getFileName(Uri.parse(b.thumbnailUri!!))}]" else "[OFF]"
        } else "[None]"
        "B${i + 1}: ${b.fileName ?: "Empty"} $status"
    } + "Back"

    selectionOverlay.show("Set Thumbnail Images", options, 0, { bufferIndex ->
        if (bufferIndex < 8) {
            val targetBufferIdx = bufferIndex
            val b = bufferManager.getBufferAt(targetBufferIdx)
            val actions = if (b.thumbnailUri == null) {
                listOf("Link Thumbnail Image", "Back")
            } else {
                listOf("Thumbnail Enabled: ${if (b.useThumbnail) "ON" else "OFF"}", "Replace Thumbnail Image", "Remove Thumbnail", "Back")
            }
            
            selectionOverlay.show("B${targetBufferIdx + 1} Thumbnail", actions, 0, { actionIdx ->
                if (b.thumbnailUri == null) {
                    when(actionIdx) {
                        0 -> { 
                            pendingThumbnailBufferIdx = targetBufferIdx
                            if (bufferManager.useWorkstationFileBrowser) {
                                val folder = workingFolderManager.lastUsedFolderUri ?: workingFolderManager.currentFolder?.path
                                showFileBrowser(FileBrowserOverlay.Mode.BROWSE, folder, null, onThumbnailSelect = { uri ->
                                    performThumbnailLink(uri, targetBufferIdx)
                                })
                            } else {
                                pickThumbnailLauncher.launch(arrayOf("image/*"))
                            }
                        }
                        else -> showSetBufferThumbnailsMenu()
                    }
                } else {
                    when(actionIdx) {
                        0 -> { 
                            b.useThumbnail = !b.useThumbnail
                            bufferManager.saveBuffers()
                            showSetBufferThumbnailsMenu() 
                        }
                        1 -> { 
                            pendingThumbnailBufferIdx = targetBufferIdx
                            if (bufferManager.useWorkstationFileBrowser) {
                                val folder = workingFolderManager.lastUsedFolderUri ?: workingFolderManager.currentFolder?.path
                                showFileBrowser(FileBrowserOverlay.Mode.BROWSE, folder, null, onThumbnailSelect = { uri ->
                                    performThumbnailLink(uri, targetBufferIdx)
                                })
                            } else {
                                pickThumbnailLauncher.launch(arrayOf("image/*"))
                            }
                        }
                        2 -> { b.thumbnailUri = null; b.useThumbnail = false; bufferManager.saveBuffers(); showSetBufferThumbnailsMenu() }
                        else -> showSetBufferThumbnailsMenu()
                    }
                }
            }, { showSetBufferThumbnailsMenu() })
        } else {
            showBufferThumbnailsSettingsMenu()
        }
    }, { showBufferThumbnailsSettingsMenu() })
}

internal fun MainActivity.showManageFoldersMenu() {
    val folders = imageFolderManager.getFolders()
    if (folders.isEmpty()) { showImageSettingsMenu(); return }
    val options = folders.map {
        val prefix = if (it.isRemote) "[R] " else "[L] "
        val suffix = if (it.path == imageFolderManager.currentFolder?.path) " (Current)" else ""
        prefix + it.name + suffix
    }
    selectionOverlay.show("Manage Folders (Del to remove)", options, 0, { index ->
        if (index in folders.indices) {
            val f = folders[index]
            imageFolderManager.currentFolder = f
            showStatusToast("Current Image Folder: ${f.name}"); handleOverlayCommand(Command.OverlayCancel)
        }
    }, { showImageSettingsMenu() })
}

internal fun MainActivity.showManuscriptSettingsMenu() {
    val settings = bufferManager.getCurrentBuffer().manuscriptSettings
    val options = listOf(
        "Page Size: ${settings.pageSize}",
        "Orientation: ${if (settings.isPortrait) "Portrait" else "Landscape"}",
        "Reference Font Size: ${settings.fontSize.toInt()}pt",
        "Page Margins: ${settings.margins} in",
        "Paragraph Collapse: ${if (settings.isParagraphCollapseEnabled) "ON" else "OFF"}",
        "Collapsed: ${if (settings.isFirstSentenceOnly) "First Sentence Only" else "Entire Paragraph"}",
        "Layout Grid Tiles: ${settings.layoutTileCount}",
        "Back"
    )
    selectionOverlay.show("Manuscript Settings", options, 0, {
        when(it) {
            0 -> showMsPageSizeMenu()
            1 -> { settings.isPortrait = !settings.isPortrait; bufferManager.saveBuffers(); showManuscriptSettingsMenu() }
            2 -> showMsFontSizeMenu()
            3 -> showMsMarginMenu()
            4 -> { settings.isParagraphCollapseEnabled = !settings.isParagraphCollapseEnabled; bufferManager.saveBuffers(); showManuscriptSettingsMenu() }
            5 -> { settings.isFirstSentenceOnly = !settings.isFirstSentenceOnly; bufferManager.saveBuffers(); showManuscriptSettingsMenu() }
            6 -> { settings.layoutTileCount = if (settings.layoutTileCount == 8) 4 else 8; bufferManager.saveBuffers(); showManuscriptSettingsMenu() }
            7 -> showAdvancedFeaturesMenu()
        }
    }, { showAdvancedFeaturesMenu() })
}

internal fun MainActivity.showMsPageSizeMenu() {
    val sizes = listOf("8.5x11", "6x9", "5x8", "4x6", "3x4")
    selectionOverlay.show("Page Size", sizes + "Back", sizes.indexOf(bufferManager.getCurrentBuffer().manuscriptSettings.pageSize).coerceAtLeast(0), {
        if (it < sizes.size) { bufferManager.getCurrentBuffer().manuscriptSettings.pageSize = sizes[it]; bufferManager.saveBuffers(); showManuscriptSettingsMenu() }
        else showManuscriptSettingsMenu()
    }, { showManuscriptSettingsMenu() })
}

internal fun MainActivity.showMsFontSizeMenu() {
    val sizes = listOf("10", "11", "12", "14", "16", "18")
    val current = bufferManager.getCurrentBuffer().manuscriptSettings.fontSize.toInt().toString()
    selectionOverlay.show("Ref Font Size", sizes + "Back", sizes.indexOf(current).coerceAtLeast(0), {
        if (it < sizes.size) { bufferManager.getCurrentBuffer().manuscriptSettings.fontSize = sizes[it].toFloat(); bufferManager.saveBuffers(); showManuscriptSettingsMenu() }
        else showManuscriptSettingsMenu()
    }, { showManuscriptSettingsMenu() })
}

internal fun MainActivity.showMsMarginMenu() {
    val margins = listOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5")
    val current = bufferManager.getCurrentBuffer().manuscriptSettings.margins.toString()
    selectionOverlay.show("Page Margins (Inches)", margins + "Back", margins.indexOf(current).coerceAtLeast(0), {
        if (it < margins.size) { bufferManager.getCurrentBuffer().manuscriptSettings.margins = margins[it].toFloat(); bufferManager.saveBuffers(); showManuscriptSettingsMenu() }
        else showManuscriptSettingsMenu()
    }, { showManuscriptSettingsMenu() })
}
