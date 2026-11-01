package com.example.doswriter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PersistableBundle
import android.util.Log
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.doswriter.editor.AboutOverlay
import com.example.doswriter.editor.EditorView
import com.example.doswriter.editor.HelpOverlay
import com.example.doswriter.editor.InputOverlay
import com.example.doswriter.editor.KeyboardView
import com.example.doswriter.editor.MacroOverlay
import com.example.doswriter.editor.ManuscriptFilmstripOverlay
import com.example.doswriter.editor.ManuscriptLayoutOverlay
import com.example.doswriter.editor.MultiSelectionOverlay
import com.example.doswriter.editor.OutlineOverlay
import com.example.doswriter.editor.PredictiveConsoleView
import com.example.doswriter.editor.ReferenceOverlay
import com.example.doswriter.editor.SearchOverlay
import com.example.doswriter.editor.SelectionOverlay
import com.example.doswriter.editor.CommandScriptOverlay
import com.example.doswriter.editor.FileBrowserOverlay
import com.example.doswriter.editor.WorkspaceLayoutOverlay
import com.example.doswriter.logic.*
import com.example.doswriter.model.EditorBuffer
import com.example.doswriter.ui.*
import java.io.File
import java.util.*
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    internal lateinit var editorView: EditorView
    internal lateinit var leftEditorView: EditorView
    internal lateinit var rightEditorView: EditorView
    internal lateinit var internalKeyboardView: KeyboardView
    internal lateinit var predictiveConsoleView: PredictiveConsoleView
    internal lateinit var bufferManager: BufferManager
    internal lateinit var outlineManager: OutlineManager
    internal lateinit var imageFolderManager: ImageFolderManager
    internal lateinit var workingFolderManager: WorkingFolderManager
    internal lateinit var recentFileManager: RecentFileManager
    internal lateinit var syncManager: SyncManager
    internal lateinit var macroManager: MacroManager
    internal lateinit var manuscriptEngine: com.example.doswriter.logic.ManuscriptEngine
    internal lateinit var commandDispatcher: CommandDispatcher
    internal lateinit var commandScriptManager: CommandScriptManager
    internal lateinit var hardwareProvider: com.example.doswriter.logic.hal.HardwareProvider
    internal val keybindingManager = KeybindingManager()
    internal val telexProcessor = TelexProcessor()
    internal var keyboardMode = TelexProcessor.KeyboardMode.STANDARD
    
    internal lateinit var statusText: TextView
    internal lateinit var editorContainer: LinearLayout
    internal lateinit var editorStack: FrameLayout
    internal lateinit var selectionOverlay: SelectionOverlay
    internal lateinit var inputOverlay: InputOverlay
    internal lateinit var helpOverlay: HelpOverlay
    internal lateinit var searchOverlay: SearchOverlay
    internal lateinit var macroOverlay: MacroOverlay
    internal lateinit var manuscriptFilmstripOverlay: ManuscriptFilmstripOverlay
    internal lateinit var manuscriptLayoutOverlay: ManuscriptLayoutOverlay
    internal lateinit var workspaceLayoutOverlay: WorkspaceLayoutOverlay
    internal lateinit var commandScriptOverlay: CommandScriptOverlay
    internal lateinit var scriptConsoleOverlay: com.example.doswriter.editor.ScriptConsoleOverlay
    internal lateinit var multiSelectionOverlay: MultiSelectionOverlay
    internal lateinit var outlineOverlay: OutlineOverlay
    internal lateinit var referenceOverlay: ReferenceOverlay
    internal lateinit var fileBrowserOverlay: FileBrowserOverlay
    internal lateinit var aboutOverlay: AboutOverlay
    internal lateinit var anchorView: View

    internal var isStatusVisible = true
    internal var themeMode = 0 
    internal var dayStartHour = 7
    internal var dayStartMin = 0
    internal var nightStartHour = 19
    internal var nightStartMin = 0
    
    internal var deviceType = -1 // -1: Not set, 0: Backlit, 1: Color E-ink, 2: Mono E-ink
    internal var hardwareManufacturer = 0 // 0: Auto, 1: BOOX, 2: Bigme, 3: Supernote, 4: Other
    internal var menuFontSizeMode = 1 // 0: Small, 1: Medium, 2: Large, 3: X-Large
    internal var menuColorMode = 0 // 0: Dark (LCD), 1: Light (E-ink)
    internal var statusFontSizeMode = 1 // 0: Small, 1: Medium, 2: Large
    internal var isStartupHelpEnabled = true
    internal var isStartupOverviewEnabled = true
    internal var isOverviewPending = true // Session flag: shows prompt once per cold boot
    internal var isStatusHelpEnabled = true
    internal var isFontScanEnabled = false
    internal var isHelpTaskSwitchEnabled = true
    internal var isStatusMessagesSuppressed = false
    internal var isPersistentScreenshotEnabled = false
    internal var isSystemClipboardSuppressed = true
    internal var suppressWatermark = false
    internal var isImageFadeEnabled = true
    
    internal var isSettingsGlobal = true
    
    internal var isRedirectedFromFileMenu = false
    internal var isRunningWizard = false

    internal var isCtrlLocked = false
    internal var currentAppMenuIndex = -1
    internal var isVKeyDown = false
    internal var isSplitActive = false
    internal var isPredictiveModeEnabled = false
    internal var isVirtualKeyboardEnabled = false
        set(value) {
            field = value
            if (::leftEditorView.isInitialized) leftEditorView.isVirtualKeyboardEnabled = value
            if (::rightEditorView.isInitialized) rightEditorView.isVirtualKeyboardEnabled = value
            if (!value) hideSystemKeyboard()
            
            // Hide hardware debug console if virtual keyboard is active
            if (::debugInfoTextView.isInitialized) {
                if (value) {
                    debugInfoTextView.visibility = View.GONE
                } else if (isKeyboardDebugEnabled) {
                    debugInfoTextView.visibility = View.VISIBLE
                }
            }
        }
    internal var isKeyboardDebugEnabled = false
        set(value) {
            field = value
            if (::debugInfoTextView.isInitialized) {
                // Only show if virtual keyboard is NOT active
                debugInfoTextView.visibility = if (value && !isVirtualKeyboardEnabled) View.VISIBLE else View.GONE
                if (value) debugInfoTextView.text = "Keyboard Debug Active"
            }
        }
    internal var isDemoModeEnabled = false
    internal var isDemoRunning = false
    internal var demoOriginalText = ""
    internal var wasTypewriterOnBeforeDemo = false

    internal var activePanel = 0 // 0: Left, 1: Right
    internal var isInternalCopyLast = false
    internal var isPuncSticky = false
    internal var leftBufferIdx = 0
    internal var rightBufferIdx = 0
    internal var splitOrientation = LinearLayout.HORIZONTAL
    internal var isMiniBookMode = false
    internal var isManuscriptPdfMode = false
    internal var currentPdfFormat = 0
    internal var lastPortraitKbHeight: Int = -1
    internal var lastPortraitKbTag: String? = null
    internal var selectedBuffersForPdf: List<Int> = emptyList()
    internal var pendingLinkContainerId: String? = null
    internal var pendingLinkRemoteContainerId: String? = null
    
    internal var viewerFiles = mutableListOf<String>()
    internal var viewerIndex = -1
    internal var isViewerRemote = false
    internal var onReferenceDismiss: (() -> Unit)? = null
    internal lateinit var debugInfoTextView: TextView

    internal val statusUpdateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    internal val statusUpdateRunnable = Runnable { updateStatusImmediate() }
    
    internal val saveHandler = android.os.Handler(android.os.Looper.getMainLooper())
    internal val saveRunnable = Runnable { bufferManager.saveBuffers() }

    internal var slideshowActive = false
    internal val slideshowHandler = android.os.Handler(android.os.Looper.getMainLooper())
    internal val slideshowRunnable = object : Runnable {
        override fun run() {
            if (slideshowActive) {
                moveViewer(1)
                slideshowHandler.postDelayed(this, imageFolderManager.slideshowIntervalSec * 1000L)
            }
        }
    }
    
    internal val autoSyncHandler = android.os.Handler(android.os.Looper.getMainLooper())
    internal val autoSyncRunnable = Runnable { triggerAutoSyncPush() }

    internal var demoText: String = ""
    internal var demoIndex: Int = 0
    internal var demoTargetView: EditorView? = null
    internal var isDemoCrlfActive = false
    internal var demoCrlfStep = 0 // 0: None, 1: CR, 2: LF
    internal val demoHandler = android.os.Handler(android.os.Looper.getMainLooper())
    internal val demoRunnable = object : Runnable {
        override fun run() {
            val targetView = demoTargetView ?: editorView
            val buffer = targetView.activeBuffer ?: bufferManager.getCurrentBuffer()
            val settings = buffer.cursorSettings.typewriter
            
            if (isDemoCrlfActive) {
                if (demoCrlfStep == 1) {
                    targetView.invalidate()
                    demoCrlfStep = 2
                    demoHandler.postDelayed(this, settings.crlfDelayMs)
                } else if (demoCrlfStep == 2) {
                    targetView.invalidate()
                    isDemoCrlfActive = false
                    demoCrlfStep = 0
                    demoHandler.postDelayed(this, settings.crlfDelayMs)
                }
                return
            }

            if (demoIndex < demoText.length) {
                val char = demoText[demoIndex++]
                
                if (keyboardMode == TelexProcessor.KeyboardMode.VIETNAMESE_TELEX) {
                    val syllable = getCurrentSyllable()
                    val result = telexProcessor.process(char, syllable)
                    if (result.handled) {
                        repeat(result.backspaceCount) {
                            buffer.deleteBackward()
                        }
                        buffer.insert(result.output)
                    } else {
                        buffer.insert(char.toString())
                    }
                } else {
                    buffer.insert(char.toString())
                }

                targetView.invalidate()
                targetView.scrollToCursor()
                updateStatus()
                
                if (char == '\n') {
                    isDemoCrlfActive = true
                    demoCrlfStep = 1
                    demoHandler.postDelayed(this, settings.crlfDelayMs)
                } else {
                    demoHandler.postDelayed(this, settings.charDelayMs)
                }
            } else {
                stopTypewriterDemo()
            }
        }
    }

    internal val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handleDocumentOpened(uri)
        else {
            handleOverlayCommand(Command.OverlayCancel)
            enableImmersiveMode()
            showStatusToast("Open canceled")
        }
    }

    internal val openFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val doc = DocumentFile.fromTreeUri(this, uri)
            val name = doc?.name ?: "Folder"
            imageFolderManager.addFolder(name, uri.toString(), false)
            showStatusToast("Linked Image Folder: $name")
            showImageSettingsMenu()
        }
    }

    internal var pendingThumbnailBufferIdx: Int = -1 // -1: None, 0-7: Buffer, 10: Global
    internal val pickThumbnailLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && pendingThumbnailBufferIdx != -1) {
            try {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.e("Thumbnails", "Could not take persistable permission: ${e.message}")
            }
            performThumbnailLink(uri, pendingThumbnailBufferIdx)
        }
    }

    internal val openFontFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val buffer = bufferManager.getCurrentBuffer()
            buffer.customFontsPath = uri.toString()
            bufferManager.saveBuffers()
            showStatusToast("Custom font folder linked")
            showFontSettingsMenu()
        }
    }

    internal val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) handleDocumentCreated(uri)
        else {
            handleOverlayCommand(Command.OverlayCancel)
            enableImmersiveMode()
        }
    }
    
    internal val createPdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) handlePdfCreated(uri)
        else {
            handleOverlayCommand(Command.OverlayCancel)
            enableImmersiveMode()
        }
    }

    internal val createPdfFavoritesLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) handlePdfFavoritesExported(uri)
        else {
            handleOverlayCommand(Command.OverlayCancel)
            enableImmersiveMode()
        }
    }

    internal val linkWorkingFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val doc = DocumentFile.fromTreeUri(this, uri)
            val name = doc?.name ?: "Folder"
            workingFolderManager.addFolder(name, uri.toString())
            showStatusToast("Linked Working Folder: $name")
            if (isRunningWizard) {
                wizardStep3PerformanceNote()
            } else if (isRedirectedFromFileMenu) {
                isRedirectedFromFileMenu = false
                selectionOverlay.hide()
                showAppMenu(0)
            } else {
                showFileManagerSettingsMenu()
            }
        }
    }

    internal val linkDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && pendingLinkContainerId != null) {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            outlineManager.linkLocalFile(pendingLinkContainerId!!, uri.toString(), getFileName(uri))
            pendingLinkContainerId = null
            outlineOverlay.refresh()
        }
        enableImmersiveMode()
    }

    internal val createOutlineLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) handleOutlineCreated(uri)
        else {
            handleOverlayCommand(Command.OverlayCancel)
            enableImmersiveMode()
        }
    }

    internal val openOutlineLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handleOutlineOpened(uri)
        else {
            handleOverlayCommand(Command.OverlayCancel)
            enableImmersiveMode()
        }
    }

    internal val transferLocalFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handleTransferToRemote(uri)
        else {
            handleOverlayCommand(Command.OverlayCancel)
            enableImmersiveMode()
        }
    }

    internal val linkScriptImageFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            bufferManager.scriptImageFolderUri = uri.toString()
            bufferManager.saveBuffers()
            showStatusToast("Script Image Folder Linked")
        }
        enableImmersiveMode()
    }

    internal var macroText: String = ""

    data class EditorTheme(val name: String, val textColor: Int, val backgroundColor: Int, val cursorColor: Int)

    internal val baseThemes = listOf(
        EditorTheme("Classic", android.graphics.Color.WHITE, android.graphics.Color.BLACK, android.graphics.Color.GREEN),
        EditorTheme("Monokai", android.graphics.Color.rgb(248, 248, 242), android.graphics.Color.rgb(39, 40, 34), android.graphics.Color.rgb(249, 38, 114)),
        EditorTheme("Solarized Light", android.graphics.Color.rgb(101, 123, 131), android.graphics.Color.rgb(253, 246, 227), android.graphics.Color.rgb(181, 137, 0)),
        EditorTheme("Wheat", android.graphics.Color.rgb(70, 60, 50), android.graphics.Color.rgb(245, 222, 179), android.graphics.Color.rgb(139, 69, 19)),
        EditorTheme("E-Ink", android.graphics.Color.BLACK, android.graphics.Color.WHITE, android.graphics.Color.rgb(0, 100, 0)),
        EditorTheme("Matrix", android.graphics.Color.GREEN, android.graphics.Color.BLACK, android.graphics.Color.rgb(0, 255, 0)),
        EditorTheme("Retro Terminal", android.graphics.Color.rgb(200, 200, 200), android.graphics.Color.rgb(10, 15, 10), android.graphics.Color.rgb(50, 200, 50)),
        EditorTheme("1950s CRT", android.graphics.Color.rgb(77, 77, 77), android.graphics.Color.rgb(236, 234, 211), android.graphics.Color.rgb(77, 77, 77)),
        EditorTheme("VS Code dark", android.graphics.Color.rgb(212, 212, 212), android.graphics.Color.rgb(30, 30, 30), android.graphics.Color.rgb(212, 212, 212)),
        EditorTheme("Gray 1", android.graphics.Color.rgb(214, 226, 222), android.graphics.Color.rgb(142, 141, 147), android.graphics.Color.rgb(214, 226, 222)),
        EditorTheme("Gray 2", android.graphics.Color.rgb(214, 226, 222), android.graphics.Color.rgb(24, 24, 24), android.graphics.Color.rgb(214, 226, 222)),
        EditorTheme("Engineer Notebook", android.graphics.Color.rgb(12, 32, 32), android.graphics.Color.rgb(207, 227, 209), android.graphics.Color.rgb(12, 32, 32)),
        EditorTheme("Soft Paper Gray", android.graphics.Color.rgb(60, 60, 60), android.graphics.Color.rgb(235, 235, 235), android.graphics.Color.rgb(60, 60, 60)),
        EditorTheme("Cool Light Gray", android.graphics.Color.rgb(50, 55, 60), android.graphics.Color.rgb(225, 228, 230), android.graphics.Color.rgb(50, 55, 60)),
        EditorTheme("Mid Gray", android.graphics.Color.rgb(40, 40, 40), android.graphics.Color.rgb(210, 212, 214), android.graphics.Color.rgb(40, 40, 40)),
        EditorTheme("Warm Wheat Paper", android.graphics.Color.rgb(70, 65, 50), android.graphics.Color.rgb(245, 240, 220), android.graphics.Color.rgb(70, 65, 50)),
        EditorTheme("Muted Olive Paper", android.graphics.Color.rgb(55, 65, 45), android.graphics.Color.rgb(230, 235, 215), android.graphics.Color.rgb(55, 65, 45)),
        EditorTheme("Green-Tinted E-Ink", android.graphics.Color.rgb(45, 60, 50), android.graphics.Color.rgb(220, 230, 220), android.graphics.Color.rgb(45, 60, 50)),
        EditorTheme("Cool White", android.graphics.Color.rgb(50, 60, 70), android.graphics.Color.rgb(240, 245, 250), android.graphics.Color.rgb(50, 60, 70)),
        EditorTheme("Soft Blue Paper", android.graphics.Color.rgb(45, 55, 65), android.graphics.Color.rgb(225, 235, 245), android.graphics.Color.rgb(45, 55, 65)),
        EditorTheme("Pale Ice Blue", android.graphics.Color.rgb(35, 45, 55), android.graphics.Color.rgb(210, 225, 240), android.graphics.Color.rgb(35, 45, 55))
    )
    internal val themes = mutableListOf<EditorTheme>()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_overview_pending", isOverviewPending)
        outState.putInt("pending_thumbnail_idx", pendingThumbnailBufferIdx)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isOverviewPending = savedInstanceState?.getBoolean("is_overview_pending") ?: true
        pendingThumbnailBufferIdx = savedInstanceState?.getInt("pending_thumbnail_idx") ?: -1
        checkPermissions()
        setupManagers()
        setupUI()
        updateMenuFontSizes()
        updateStatusFontSize()
        enableImmersiveMode()
    }

    private fun checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            
            val needed = perms.filter { ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (needed.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, needed.toTypedArray(), 101)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isDemoRunning) {
            if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
                stopTypewriterDemo()
            }
            return true
        }

        val isDialogActive = selectionOverlay.visibility == View.VISIBLE ||
                inputOverlay.visibility == View.VISIBLE ||
                multiSelectionOverlay.visibility == View.VISIBLE ||
                macroOverlay.visibility == View.VISIBLE ||
                aboutOverlay.visibility == View.VISIBLE ||
                referenceOverlay.visibility == View.VISIBLE

        if (!isDialogActive) {
            if (manuscriptFilmstripOverlay.visibility == View.VISIBLE) {
                if (manuscriptFilmstripOverlay.handleKey(keyCode, event)) return true
            }
            if (manuscriptLayoutOverlay.visibility == View.VISIBLE) {
                if (manuscriptLayoutOverlay.handleKey(keyCode, event)) return true
            }
            if (workspaceLayoutOverlay.visibility == View.VISIBLE) {
                if (workspaceLayoutOverlay.handleKey(keyCode, event)) return true
            }
            if (fileBrowserOverlay.visibility == View.VISIBLE) {
                if (fileBrowserOverlay.handleKey(keyCode, event)) return true
            }
        }

        if (isKeyboardDebugEnabled) {
            val info = "DOWN: code=$keyCode scan=${event.scanCode} meta=${event.metaState} device=${event.deviceId} repeat=${event.repeatCount}"
            debugInfoTextView.text = info
        }

        if (keyCode == KeyEvent.KEYCODE_V) isVKeyDown = true
        
        val isAlt = event.isAltPressed
        val isCtrl = event.isCtrlPressed
        
        val isOverlayActive = isDialogActive ||
                helpOverlay.visibility == View.VISIBLE ||
                searchOverlay.visibility == View.VISIBLE ||
                outlineOverlay.visibility == View.VISIBLE ||
                manuscriptFilmstripOverlay.visibility == View.VISIBLE ||
                manuscriptLayoutOverlay.visibility == View.VISIBLE ||
                workspaceLayoutOverlay.visibility == View.VISIBLE ||
                scriptConsoleOverlay.visibility == View.VISIBLE ||
                fileBrowserOverlay.visibility == View.VISIBLE

        val command = keybindingManager.getCommand(
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
            fileBrowserOverlay.visibility == View.VISIBLE,
            scriptConsoleOverlay.visibility == View.VISIBLE
        )
        
        if (command != null) {
            if (isPredictiveModeEnabled && !isCtrl && !isAlt && !isOverlayActive && (command == Command.DeleteBackward || command == Command.DeleteForward)) {
                if (predictiveConsoleView.composingText.isNotEmpty()) {
                    predictiveConsoleView.deleteLastChar()
                } else {
                    commandDispatcher.dispatch(command)
                }
            } else if (isPredictiveModeEnabled && !isCtrl && !isAlt && !isOverlayActive && command is Command.InsertText && command.text == "\n") {
                commitPredictiveText()
            } else {
                commandDispatcher.dispatch(command)
            }
            return true
        }

        if (isDialogActive) return true

        if (keyCode == KeyEvent.KEYCODE_TAB && !isCtrl && !isAlt) {
            if (macroOverlay.visibility == View.VISIBLE) {
                macroOverlay.selectContextOption()
                return true
            }
            if (isOverlayActive) return true 

            if (isPredictiveModeEnabled) {
                predictiveConsoleView.updateComposing(predictiveConsoleView.composingText.toString() + "\t")
            } else {
                val spaces = " ".repeat(bufferManager.getCurrentBuffer().tabSize)
                commandDispatcher.dispatch(Command.InsertText(spaces))
            }
            return true
        }

        val unicode = event.unicodeChar
        if (unicode != 0 && !isCtrl && !isAlt) {
            if (isOverlayActive) return true 
            if (isPredictiveModeEnabled) {
                predictiveConsoleView.updateComposing(predictiveConsoleView.composingText.toString() + unicode.toChar())
            } else {
                dispatchInsertText(unicode.toChar().toString())
            }
            return true
        }
        
        if (isAlt || isCtrl) {
            if (keyCode == KeyEvent.KEYCODE_TAB || keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER) {
                return super.onKeyDown(keyCode, event)
            }
            return true 
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isKeyboardDebugEnabled) {
            val info = "UP: code=$keyCode scan=${event.scanCode} meta=${event.metaState} device=${event.deviceId} repeat=${event.repeatCount}"
            debugInfoTextView.text = info
        }
        if (keyCode == KeyEvent.KEYCODE_V) isVKeyDown = false
        if (keyCode == KeyEvent.KEYCODE_F12 || keyCode == KeyEvent.KEYCODE_F) commandDispatcher.dispatch(Command.StopFindCursor)
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN && event?.characters != null) {
            dispatchInsertText(event.characters)
            return true
        }
        return super.onKeyMultiple(keyCode, repeatCount, event)
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
        try {
            val systemBrightness = android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
            val lp = window.attributes
            lp.screenBrightness = systemBrightness
            window.attributes = lp
        } catch (e: Exception) { e.printStackTrace() }
        isVKeyDown = false
        updateVirtualKeyboardDetection()
        editorView.requestFocus()
        showStartupHelpMessage()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            lastPortraitKbHeight = bufferManager.keyboardHeight
            lastPortraitKbTag = internalKeyboardView.keyboardTag
            val screenHeight = resources.displayMetrics.heightPixels
            bufferManager.keyboardHeight = screenHeight / 2
            updateKeyboardHeight()
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (lastPortraitKbHeight != -1) {
                bufferManager.keyboardHeight = lastPortraitKbHeight
                updateKeyboardHeight()
                lastPortraitKbHeight = -1
            }
            lastPortraitKbTag?.let { loadKeyboardLayout("en_$it.json") ; lastPortraitKbTag = null }
        }
        updateVirtualKeyboardDetection()
    }

    override fun onPause() { 
        super.onPause()
        saveHandler.removeCallbacks(saveRunnable)
        bufferManager.saveBuffers() 
    }

    internal fun updateMenuFontSizes() {
        val size = when (menuFontSizeMode) {
            0 -> 28f
            1 -> 36f
            2 -> 44f
            3 -> 52f
            else -> 36f
        }
        
        val bgColor = if (menuColorMode == 1) android.graphics.Color.WHITE else android.graphics.Color.rgb(20, 20, 20)
        val textColor = if (menuColorMode == 1) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        
        if (::selectionOverlay.isInitialized) selectionOverlay.setMenuAppearance(size, bgColor, textColor)
        if (::multiSelectionOverlay.isInitialized) multiSelectionOverlay.setMenuAppearance(size, bgColor, textColor)
        if (::helpOverlay.isInitialized) helpOverlay.setMenuAppearance(size, bgColor, textColor)
        if (::searchOverlay.isInitialized) searchOverlay.setMenuAppearance(size, bgColor, textColor)
        if (::inputOverlay.isInitialized) inputOverlay.setMenuAppearance(size, bgColor, textColor)
        if (::macroOverlay.isInitialized) macroOverlay.setMenuAppearance(size, bgColor, textColor)
        if (::outlineOverlay.isInitialized) outlineOverlay.setMenuAppearance(size, bgColor, textColor)
        if (::fileBrowserOverlay.isInitialized) fileBrowserOverlay.setMenuAppearance(size, bgColor, textColor)
        if (::workspaceLayoutOverlay.isInitialized) workspaceLayoutOverlay.setMenuAppearance(size, bgColor, textColor)
    }

    internal fun updateStatusFontSize() {
        val size = when (statusFontSizeMode) {
            0 -> 12f
            1 -> 14f
            2 -> 18f
            else -> 14f
        }
        if (::statusText.isInitialized) statusText.textSize = size
    }

    internal fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result!!.lastIndexOf('/')
            if (cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result ?: "file"
    }
}
