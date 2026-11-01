package com.example.doswriter.ui

import android.view.View
import android.widget.Toast
import com.example.doswriter.MainActivity
import com.example.doswriter.logic.*
import com.example.doswriter.editor.*
import com.example.doswriter.model.*
import java.util.*

internal fun MainActivity.showHelpMenu() {
    val helpOptions = listOf("Quick Keyboard Shortcuts Reference", "Quick Tutorial", "Menus and Keyboard Shortcuts", "Virtual Keyboard Map", "Typewriter Demo", "How to Use ToDo List (PDA)", "About")
    selectionOverlay.show("Help Menu", helpOptions, 0, {
        when(it) {
            0 -> showHelpPopup()
            1 -> showQuickOverview()
            2 -> showTopicNavigationMenu()
            3 -> showVirtualKeyboardAssignmentsMenu()
            4 -> {
                val buffer = bufferManager.getCurrentBuffer()
                if (buffer.getCleanText().isEmpty()) {
                    Toast.makeText(this, "Text Required for Typewriter Demo", Toast.LENGTH_SHORT).show()
                } else {
                    runTypewriterDemo()
                }
            }
            5 -> showHelpFile("PDA/DOSwriter-PDA.md")
            6 -> commandDispatcher.dispatch(Command.ShowAbout)
        }
    }, {})
}

internal fun MainActivity.showTopicNavigationMenu() {
    try {
        val raw = assets.open("INSTRUCTIONS.md").bufferedReader().use { it.readText() }
        val profiler = com.example.doswriter.logic.DeviceProfiler(this)
        val parser = com.example.doswriter.logic.HelpParser(deviceType, profiler.getDeviceInfo(), resources.configuration.smallestScreenWidthDp >= 600)
        val allTopics = parser.getTopicList(raw)
        val topics = allTopics.filter { it != "Key Features" }
        val menuOptions = topics + listOf("Back")
        selectionOverlay.show("Help Topics", menuOptions, 0, { index ->
            if (index < topics.size) {
                val screens = topics.map { topic -> "# $topic\n\n${parser.getSection(raw, topic)}" }
                helpOverlay.onExitRequested = { showTopicNavigationMenu() }
                helpOverlay.showPages(screens, index)
            } else showHelpMenu()
        }, { showHelpMenu() })
    } catch (e: Exception) { Toast.makeText(this, "Help file not found", Toast.LENGTH_SHORT).show() }
}

internal fun MainActivity.showQuickOverview() {
    try {
        val raw = assets.open("INSTRUCTIONS.md").bufferedReader().use { it.readText() }
        val profiler = com.example.doswriter.logic.DeviceProfiler(this)
        val parser = com.example.doswriter.logic.HelpParser(deviceType, profiler.getDeviceInfo(), resources.configuration.smallestScreenWidthDp >= 600)
        val screens = parser.getOverviewScreens(raw)
        if (screens.isNotEmpty()) {
            helpOverlay.onExitRequested = null
            helpOverlay.showPages(screens)
        } else Toast.makeText(this, "Overview not found", Toast.LENGTH_LONG).show()
    } catch (e: Exception) { Toast.makeText(this, "Failed to load overview", Toast.LENGTH_LONG).show() }
}

internal fun MainActivity.showHelpFile(assetPath: String) {
    try {
        val content = assets.open(assetPath).bufferedReader().use { it.readText() }
        val title = assetPath.substringAfterLast("/").substringBeforeLast(".").replace("-", " ").uppercase()
        helpOverlay.show("# $title\n\n$content")
    } catch (e: Exception) { showStatusToast("Help file not found") }
}

internal fun MainActivity.showHelpPopup() {
    try {
        val raw = assets.open("INSTRUCTIONS.md").bufferedReader().use { it.readText() }
        val profiler = com.example.doswriter.logic.DeviceProfiler(this)
        val parser = com.example.doswriter.logic.HelpParser(deviceType, profiler.getDeviceInfo(), resources.configuration.smallestScreenWidthDp >= 600)
        val screens = parser.getHelpPopupScreens(raw)
        if (screens.isNotEmpty()) {
            helpOverlay.onExitRequested = null
            helpOverlay.showPages(screens)
        } else Toast.makeText(this, "Help popup content not found", Toast.LENGTH_LONG).show()
    } catch (e: Exception) { Toast.makeText(this, "Failed to load help popup", Toast.LENGTH_LONG).show() }
}

internal fun MainActivity.showVirtualKeyboardAssignmentsMenu() {
    val options = listOf("Virtual Keyboard Settings", "Alpha Keyboard", "Punctuation Keyboard", "Numeric Keyboard", "Back")
    selectionOverlay.show("Virtual Keyboard Map", options, 0, { index ->
        val images = listOf("virtual-keyboard-settings.png", "alpha-keyboard.png", "punctuation-keyboard.png", "numeric-keyboard.png")
        if (index < 4) {
            onReferenceDismiss = { showVirtualKeyboardAssignmentsMenu() }
            referenceOverlay.show(images[index])
        } else commandDispatcher.dispatch(Command.OpenHelpMenu)
    }, { commandDispatcher.dispatch(Command.OpenHelpMenu) })
}

internal fun MainActivity.wizardStep1DisplayType() {
    isRunningWizard = true
    val options = listOf("Backlit LCD/OLED", "Color E-Ink", "Monochrome E-Ink")
    selectionOverlay.show("Step 1: Display Type", options, 0, { index ->
        deviceType = index
        if (deviceType == 1 || deviceType == 2) wizardStep2Manufacturer()
        else { 
            hardwareManufacturer = 0
            wizardStep3LinkFolder() 
        }
    }, { wizardStep1DisplayType() })
}

internal fun MainActivity.wizardStep2Manufacturer() {
    val options = listOf("Auto Detect", "BOOX (Onyx)", "Bigme", "Supernote", "Other", "Back")
    selectionOverlay.show("Step 2: Manufacturer", options, 0, { index ->
        if (index == 5) wizardStep1DisplayType()
        else { 
            hardwareManufacturer = index
            wizardStep3LinkFolder() 
        }
    }, { wizardStep2Manufacturer() })
}

internal fun MainActivity.wizardStep3LinkFolder() {
    val options = listOf("Link Working Folder (Recommended)", "I'll Do This Later", "Back")
    selectionOverlay.show("Step 3: Storage Setup", options, 0, { index ->
        when (index) {
            0 -> {
                isRedirectedFromFileMenu = false // Ensure we don't jump to File Menu if we came from Wizard
                linkWorkingFolderLauncher.launch(null)
            }
            1 -> wizardStep3PerformanceNote()
            2 -> wizardStep2Manufacturer()
        }
    }, { wizardStep3LinkFolder() })
}

internal fun MainActivity.wizardStep3PerformanceNote() {
    val options = listOf(
        "Recommended buffer file size limit:",
        "50 KB - 100 KB (~10,000 - 20,000 words)",
        "for peak text editing performance.",
        "OK (Press Any Key or Esc)"
    )
    selectionOverlay.show("PERFORMANCE NOTE", options, 3, { _ ->
        wizardStep4Initialization()
    }, {
        wizardStep4Initialization()
    })
}

internal fun MainActivity.wizardStep4Initialization() {
    isRunningWizard = false
    if (deviceType == 1 || deviceType == 2) { menuColorMode = 1; statusFontSizeMode = 2 }
    if (deviceType == 2) {
        menuFontSizeMode = 2
        val buffer = bufferManager.getBufferAt(0)
        val einkIdx = themes.indexOfFirst { it.name == "E-Ink" }
        if (einkIdx != -1) { buffer.dayThemeIndex = einkIdx; buffer.nightThemeIndex = einkIdx; bufferManager.saveBuffers(); applyTheme() }
    } else if (deviceType == 0) menuFontSizeMode = 1

    com.example.doswriter.logic.hal.HardwareFactory.refreshProvider(this, hardwareManufacturer)
    hardwareProvider = com.example.doswriter.logic.hal.HardwareFactory.getProvider(this, hardwareManufacturer)
    hardwareProvider.initialize(this)
    saveGlobalSettings(); updateMenuFontSizes(); updateStatusFontSize()
    showStatusToast("Active Provider: ${hardwareProvider.getProviderName()}", Toast.LENGTH_LONG)

    if (isStartupOverviewEnabled && isOverviewPending) {
        isOverviewPending = false
        selectionOverlay.show("VIEW QUICK TUTORIAL?", listOf("Start", "Skip", "Don't show this again"), 0, { i ->
            if (i == 0) showQuickOverview()
            else if (i == 2) { isStartupHelpEnabled = false; saveGlobalSettings(); Toast.makeText(this, "Startup messages disabled", Toast.LENGTH_SHORT).show() }
        }, {})
    }
}
