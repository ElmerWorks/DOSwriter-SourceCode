package com.example.minimaltexteditor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import android.view.Gravity
import android.widget.PopupMenu
import android.view.KeyEvent
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.minimaltexteditor.editor.EditorView
import com.example.minimaltexteditor.editor.HelpOverlay
import com.example.minimaltexteditor.editor.InputOverlay
import com.example.minimaltexteditor.editor.MultiSelectionOverlay
import com.example.minimaltexteditor.editor.SearchOverlay
import com.example.minimaltexteditor.editor.SelectionOverlay
import com.example.minimaltexteditor.logic.BufferManager
import com.example.minimaltexteditor.logic.Command
import com.example.minimaltexteditor.logic.CommandDispatcher
import com.example.minimaltexteditor.logic.KeybindingManager
import com.example.minimaltexteditor.editor.OutlineOverlay
import com.example.minimaltexteditor.editor.ReferenceOverlay
import com.example.minimaltexteditor.editor.AboutOverlay
import com.example.minimaltexteditor.logic.ImageFolderManager
import com.example.minimaltexteditor.logic.OutlineManager
import com.example.minimaltexteditor.logic.SyncManager
import java.util.Calendar
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private lateinit var editorView: EditorView // Current active view
    private lateinit var leftEditorView: EditorView
    private lateinit var rightEditorView: EditorView
    private lateinit var editorContainer: android.widget.LinearLayout

    private lateinit var statusText: TextView
    private lateinit var selectionOverlay: SelectionOverlay
    private lateinit var inputOverlay: InputOverlay
    private lateinit var helpOverlay: HelpOverlay
    private lateinit var searchOverlay: SearchOverlay
    private lateinit var multiSelectionOverlay: MultiSelectionOverlay
    private lateinit var outlineOverlay: OutlineOverlay
    private lateinit var referenceOverlay: ReferenceOverlay
    private lateinit var aboutOverlay: AboutOverlay
    private lateinit var anchorView: View
    
    private lateinit var bufferManager: BufferManager
    private lateinit var outlineManager: OutlineManager
    private lateinit var imageFolderManager: ImageFolderManager
    private lateinit var syncManager: SyncManager
    private lateinit var commandDispatcher: CommandDispatcher
    private val keybindingManager = KeybindingManager()
    
    private var isStatusVisible = true
    private var themeMode = 0 
    private var dayStartHour = 7
    private var dayStartMin = 0
    private var nightStartHour = 19
    private var nightStartMin = 0
    
    private var isVKeyDown = false
    private var isSplitActive = false
    private var currentBrightness = -1f // -1 means system default
    private var activePanel = 0 // 0: Left, 1: Right
    private var leftBufferIdx = 0
    private var rightBufferIdx = 0
    private var selectedBuffersForPdf: List<Int> = emptyList()
    private var pendingLinkContainerId: String? = null
    private var pendingLinkRemoteContainerId: String? = null
    
    private var viewerFiles = mutableListOf<String>()
    private var viewerIndex = -1
    private var isViewerRemote = false
    
    private var slideshowActive = false
    private val slideshowHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val slideshowRunnable = object : Runnable {
        override fun run() {
            if (slideshowActive) {
                moveViewer(1)
                slideshowHandler.postDelayed(this, imageFolderManager.slideshowIntervalSec * 1000L)
            }
        }
    }
    
    private val autoSyncHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoSyncRunnable = Runnable { triggerAutoSyncPush() }

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handleDocumentOpened(uri)
        else {
            handleOverlayCommand(Command.OverlayCancel)
            enableImmersiveMode()
            Toast.makeText(this, "Open canceled", Toast.LENGTH_SHORT).show()
        }
    }

    private val openFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            inputOverlay.show("Folder Name:", "", { name ->
                imageFolderManager.addFolder(name, uri.toString(), false)
                showImageSettingsMenu()
            }, { showImageSettingsMenu() })
        }
    }

    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) handleDocumentCreated(uri)
        else {
            handleOverlayCommand(Command.OverlayCancel)
            enableImmersiveMode()
        }
    }
    
    private val createPdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) handlePdfCreated(uri)
        else {
            handleOverlayCommand(Command.OverlayCancel)
            enableImmersiveMode()
        }
    }

    private val createOutlineLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) handleOutlineCreated(uri)
        else {
            handleOverlayCommand(Command.OverlayCancel)
            enableImmersiveMode()
        }
    }

    private val openOutlineLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handleOutlineOpened(uri)
        else {
            handleOverlayCommand(Command.OverlayCancel)
            enableImmersiveMode()
        }
    }

    private val linkDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handleDocumentLinked(uri)
        else {
            handleOverlayCommand(Command.OverlayCancel)
            enableImmersiveMode()
        }
    }

    private data class EditorTheme(val name: String, val textColor: Int, val backgroundColor: Int, val cursorColor: Int)

    private val baseThemes = listOf(
        EditorTheme("Classic", android.graphics.Color.WHITE, android.graphics.Color.BLACK, android.graphics.Color.WHITE),
        EditorTheme("Retro Terminal", android.graphics.Color.GREEN, android.graphics.Color.BLACK, android.graphics.Color.GREEN),
        EditorTheme("High Contrast", android.graphics.Color.BLACK, android.graphics.Color.WHITE, android.graphics.Color.BLACK),
        EditorTheme("Wheat", android.graphics.Color.rgb(0, 42, 54), android.graphics.Color.rgb(239, 232, 213), android.graphics.Color.rgb(0, 42, 54)),
        EditorTheme("Amber Console", android.graphics.Color.rgb(185, 128, 0), android.graphics.Color.rgb(0, 0, 0), android.graphics.Color.rgb(185, 128, 0)),
        EditorTheme("Yellow Moon", android.graphics.Color.rgb(242, 226, 5), android.graphics.Color.rgb(13, 13, 13), android.graphics.Color.rgb(242, 226, 5)),
        EditorTheme("Wyse Terminal Green", android.graphics.Color.rgb(51, 255, 0), android.graphics.Color.rgb(0, 0, 0), android.graphics.Color.rgb(51, 255, 0)),
        EditorTheme("Wyse Terminal Amber", android.graphics.Color.rgb(255, 176, 0), android.graphics.Color.rgb(0, 0, 0), android.graphics.Color.rgb(255, 176, 0)),
        EditorTheme("Wyse Soft Blue-White", android.graphics.Color.rgb(234, 223, 200), android.graphics.Color.rgb(51, 74, 124), android.graphics.Color.rgb(234, 223, 200)),
        EditorTheme("Retro Gray", android.graphics.Color.rgb(234, 223, 200), android.graphics.Color.rgb(104, 104, 104), android.graphics.Color.rgb(234, 223, 200)),
        EditorTheme("1950s CRT", android.graphics.Color.rgb(77, 77, 77), android.graphics.Color.rgb(236, 234, 211), android.graphics.Color.rgb(77, 77, 77)),
        EditorTheme("VS Code dark", android.graphics.Color.rgb(212, 212, 212), android.graphics.Color.rgb(30, 30, 30), android.graphics.Color.rgb(212, 212, 212)),
        EditorTheme("Gray 1", android.graphics.Color.rgb(214, 226, 222), android.graphics.Color.rgb(142, 141, 147), android.graphics.Color.rgb(214, 226, 222)),
        EditorTheme("Gray 2", android.graphics.Color.rgb(214, 226, 222), android.graphics.Color.rgb(24, 24, 24), android.graphics.Color.rgb(214, 226, 222)),
        EditorTheme("Army", android.graphics.Color.rgb(0, 0, 0), android.graphics.Color.rgb(75, 83, 32), android.graphics.Color.rgb(0, 0, 0)),
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
    private val themes = mutableListOf<EditorTheme>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupManagers()
        setupUI()
        enableImmersiveMode()
    }

    private fun setupManagers() {
        bufferManager = BufferManager(this)
        outlineManager = OutlineManager(this)
        imageFolderManager = ImageFolderManager(this)
        syncManager = SyncManager(bufferManager) { event -> handleSyncEvent(event) }
        loadGlobalSettings()
        commandDispatcher = CommandDispatcher(
            bufferManager,
            onBufferSwitched = { bufferNum ->
                val buffer = bufferManager.getCurrentBuffer()
                val name = buffer.fileName ?: "Empty"
                Toast.makeText(this, "Buffer $bufferNum: $name", Toast.LENGTH_SHORT).show()
                editorView.setBuffer(buffer)
                applyTheme()
                updateStatus()
            },
            onContentChanged = {
                editorView.scrollToCursor()
                updateStatus()
                resetAutoSyncTimer()
            },
            onFontSizeChanged = { increase ->
                val buffer = bufferManager.getCurrentBuffer()
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
                    bufferManager.saveBuffers()
                }
            },
            onThemeChanged = { forward ->
                val buffer = bufferManager.getCurrentBuffer()
                val isDay = isDaytime()
                val oldIdx = if (isDay) buffer.dayThemeIndex else buffer.nightThemeIndex
                val newIdx = if (forward) (oldIdx + 1) % themes.size else (oldIdx - 1 + themes.size) % themes.size
                if (isDay) buffer.dayThemeIndex = newIdx else buffer.nightThemeIndex = newIdx
                val theme = themes[newIdx]
                val modeLabel = if (isDay) "Day" else "Night"
                Toast.makeText(this, "Theme ($modeLabel): ${theme.name}", Toast.LENGTH_SHORT).show()
                applyTheme()
                bufferManager.saveBuffers()
            },
            onOverlayAction = { cmd -> handleOverlayCommand(cmd) },
            onClipboardAction = { cmd -> handleClipboardCommand(cmd) }
        )
    }

    private fun handleClipboardCommand(cmd: Command) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val buffer = bufferManager.getCurrentBuffer()
        when (cmd) {
            Command.Copy -> {
                if (buffer.hasSelection()) {
                    val text = buffer.getSelectedText()
                    clipboard.setPrimaryClip(ClipData.newPlainText("ElmerPad", text))
                    Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
                }
            }
            Command.Cut -> {
                if (buffer.hasSelection()) {
                    val text = buffer.getSelectedText()
                    clipboard.setPrimaryClip(ClipData.newPlainText("ElmerPad", text))
                    buffer.deleteSelection()
                    editorView.invalidate()
                    updateStatus()
                    Toast.makeText(this, "Cut", Toast.LENGTH_SHORT).show()
                }
            }
            Command.Paste -> {
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text.toString()
                    buffer.insert(text)
                    editorView.invalidate()
                    updateStatus()
                }
            }
            else -> {}
        }
    }

    private fun showContextMenu(x: Float, y: Float) {
        val buffer = bufferManager.getCurrentBuffer()
        anchorView.x = x
        anchorView.y = y
        val popup = PopupMenu(this, anchorView)
        popup.menu.add("Copy").apply {
            isEnabled = buffer.hasSelection()
            setOnMenuItemClickListener { commandDispatcher.dispatch(Command.Copy); true }
        }
        popup.menu.add("Cut").apply {
            isEnabled = buffer.hasSelection()
            setOnMenuItemClickListener { commandDispatcher.dispatch(Command.Cut); true }
        }
        popup.menu.add("Paste").apply {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            isEnabled = clipboard.hasPrimaryClip()
            setOnMenuItemClickListener { commandDispatcher.dispatch(Command.Paste); true }
        }
        popup.show()
    }

    private fun handleOverlayCommand(cmd: Command) {
        when (cmd) {
            is Command.MoveUp -> editorView.moveVisualLine(-1, cmd.selecting)
            is Command.MoveDown -> editorView.moveVisualLine(1, cmd.selecting)
            is Command.MoveToVisualLineStart -> editorView.moveVisualLineBoundary(false, cmd.selecting)
            is Command.MoveToVisualLineEnd -> editorView.moveVisualLineBoundary(true, cmd.selecting)
            Command.RequestClearBuffer -> {
                selectionOverlay.show("Clear current buffer?", listOf("No", "Yes"), 1, { if (it == 1) commandDispatcher.dispatch(Command.ClearBuffer) }, {})
            }
            Command.RequestExit -> {
                selectionOverlay.show("Exit ElmerPad?", listOf("No", "Yes"), 1, { if (it == 1) finish() }, {})
            }
            Command.MinimizeApp -> {
                moveTaskToBack(true)
            }
            Command.BrightnessUp -> {
                adjustBrightness(0.1f)
            }
            Command.BrightnessDown -> {
                adjustBrightness(-0.1f)
            }
            Command.AddBookmark -> {
                bufferManager.getCurrentBuffer().addBookmark()
                editorView.invalidate()
                Toast.makeText(this, "Bookmark added", Toast.LENGTH_SHORT).show()
                bufferManager.saveBuffers()
            }
            Command.OpenBookmarkMenu -> {
                val buffer = bufferManager.getCurrentBuffer()
                val options = buffer.bookmarks.map { buffer.getLineText(it) }
                selectionOverlay.show("Bookmarks", options, buffer.bookmarks.size - 1, { buffer.setCursor(buffer.bookmarks[it], false); editorView.scrollToCursor(); updateStatus() }, {})
            }
            Command.JumpLastBookmark -> {
                val buffer = bufferManager.getCurrentBuffer()
                if (buffer.bookmarks.isNotEmpty()) {
                    val last = buffer.bookmarks.removeAt(buffer.bookmarks.size - 1)
                    buffer.setCursor(last, false); editorView.scrollToCursor(); updateStatus(); bufferManager.saveBuffers()
                }
            }
            Command.CollapseSelection -> {
                bufferManager.getCurrentBuffer().collapseSelection()
                editorView.invalidate()
                bufferManager.saveBuffers()
            }
            Command.OpenFoldMenu -> {
                val buffer = bufferManager.getCurrentBuffer()
                val options = buffer.folds.map { "[+] ${buffer.getText().substring(it.start, min(it.start + 20, it.end + 1)).replace("\n", " ")}..." }
                selectionOverlay.show("Collapsed Text", options, 0, { buffer.removeFold(it); editorView.invalidate(); bufferManager.saveBuffers() }, {})
            }
            Command.OpenOutlineSelection -> {
                val buffer = bufferManager.getCurrentBuffer()
                if (buffer.isDirty) {
                    selectionOverlay.show("Save changes before opening Outline?", listOf("Save", "Discard", "Cancel"), 0, {
                        when(it) {
                            0 -> { 
                                if (buffer.fileUri != null) performUriSave(Uri.parse(buffer.fileUri)) 
                                else createDocumentLauncher.launch(buffer.fileName ?: "buffer_${bufferManager.currentBufferIndex + 1}.txt")
                                outlineOverlay.show(OutlineOverlay.Mode.SELECT, outlineManager, { path, name, isRemote -> handleOutlineFileSelected(path, name, isRemote) }, { _, _ -> })
                            }
                            1 -> outlineOverlay.show(OutlineOverlay.Mode.SELECT, outlineManager, { path, name, isRemote -> handleOutlineFileSelected(path, name, isRemote) }, { _, _ -> })
                        }
                    }, {})
                } else {
                    outlineOverlay.show(OutlineOverlay.Mode.SELECT, outlineManager, { path, name, isRemote -> handleOutlineFileSelected(path, name, isRemote) }, { _, _ -> })
                }
            }
            Command.OpenOutlineSetup -> {
                outlineOverlay.show(OutlineOverlay.Mode.SETUP, outlineManager, { _, _, _ -> }, { item, action -> handleOutlineSetupAction(item, action) })
            }
            Command.OutlineUp -> outlineOverlay.moveUp()
            Command.OutlineDown -> outlineOverlay.moveDown()
            Command.OutlineSelect -> outlineOverlay.confirm()
            Command.OutlineAddFolder -> outlineOverlay.triggerAction(OutlineOverlay.Action.ADD_FOLDER)
            Command.OutlineAddContainer -> outlineOverlay.triggerAction(OutlineOverlay.Action.ADD_CONTAINER)
            Command.OutlineDelete -> outlineOverlay.triggerAction(OutlineOverlay.Action.DELETE)
            Command.OutlineLinkFile -> outlineOverlay.triggerAction(OutlineOverlay.Action.LINK)
            Command.OutlineLinkRemote -> outlineOverlay.triggerAction(OutlineOverlay.Action.LINK_REMOTE)
            Command.OutlineUnlink -> outlineOverlay.triggerAction(OutlineOverlay.Action.UNLINK)
            Command.OutlineRename -> outlineOverlay.triggerAction(OutlineOverlay.Action.RENAME)
            Command.SaveOutlineToFile -> {
                val uri = outlineManager.currentFileUri
                if (uri != null) performOutlineSave(Uri.parse(uri)) else createOutlineLauncher.launch("my_project.monk")
            }
            Command.SaveOutlineAs -> {
                handleOverlayCommand(Command.OverlayCancel)
                createOutlineLauncher.launch("my_project.monk")
            }
            Command.OpenOutlineFile -> {
                handleOverlayCommand(Command.OverlayCancel)
                openOutlineLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            }
            Command.RequestClearOutline -> {
                if (outlineManager.isDirty) {
                    selectionOverlay.show("Clear Outline? Unsaved changes will be lost.", listOf("No", "Yes"), 0, { if (it == 1) commandDispatcher.dispatch(Command.ClearOutline) }, {})
                } else {
                    commandDispatcher.dispatch(Command.ClearOutline)
                }
            }
            Command.ClearOutline -> {
                outlineManager.clear()
                outlineOverlay.refresh()
                Toast.makeText(this, "Outline cleared", Toast.LENGTH_SHORT).show()
            }
            
            Command.OpenSplitMenu -> {
                if (isSplitActive) {
                    Toast.makeText(this, "Split already active", Toast.LENGTH_SHORT).show()
                } else {
                    val options = (0 until 8).map { "B${it+1}: ${bufferManager.getBufferAt(it).fileName ?: "Empty"}" }
                    selectionOverlay.show("Compare with (Right Panel):", options, 0, { index ->
                        enterSplitMode(bufferManager.currentBufferIndex, index)
                    }, {})
                }
            }
            Command.SwapSplitFocus -> swapSplitFocus()
            Command.ExitSplit -> {
                if (isSplitActive) exitSplitMode()
                else if (bufferManager.getCurrentBuffer().isMarkdownMode) {
                    bufferManager.getCurrentBuffer().isMarkdownMode = false
                    editorView.invalidate()
                } else if (editorView.isTypewriterActive()) {
                    editorView.toggleTypewriterMode()
                    Toast.makeText(this, "Typewriter Mode: OFF", Toast.LENGTH_SHORT).show()
                } else {
                    handleOverlayCommand(Command.RequestExit)
                }
            }
            Command.ToggleMarkdownRender -> {
                val b = bufferManager.getCurrentBuffer()
                if (b.fileName?.lowercase()?.endsWith(".md") == true) {
                    b.isMarkdownMode = !b.isMarkdownMode
                    editorView.invalidate()
                } else {
                    Toast.makeText(this, "Not a Markdown file", Toast.LENGTH_SHORT).show()
                }
            }
            Command.ToggleTypewriterMode -> {
                editorView.toggleTypewriterMode()
                val status = if (editorView.isTypewriterActive()) "ON" else "OFF"
                Toast.makeText(this, "Typewriter Mode: $status", Toast.LENGTH_SHORT).show()
            }
            Command.RecenterTypewriter -> {
                editorView.recenterTypewriter()
                Toast.makeText(this, "Typewriter Focused", Toast.LENGTH_SHORT).show()
            }
            Command.OpenScratchpad -> commandDispatcher.dispatch(Command.SwitchBuffer(8))
            Command.FlushScratchpadSave -> {
                if (bufferManager.flushScratchpad(true)) {
                    Toast.makeText(this, "Scratchpad saved", Toast.LENGTH_SHORT).show()
                    editorView.invalidate(); updateStatus()
                } else Toast.makeText(this, "Flush failed", Toast.LENGTH_SHORT).show()
            }
            Command.FlushScratchpadNoSave -> {
                bufferManager.flushScratchpad(false)
                Toast.makeText(this, "Scratchpad cleared", Toast.LENGTH_SHORT).show()
                editorView.invalidate(); updateStatus()
            }
            Command.OpenScratchpadSettings -> showScratchpadSettingsMenu()
            Command.OpenFind -> searchOverlay.show(SearchOverlay.Mode.FIND, { q, a, f -> performFind(q, a, f) }, { _, _, _, _ -> })
            Command.OpenReplace -> searchOverlay.show(SearchOverlay.Mode.REPLACE, { _, _, _ -> }, { q, r, a, f -> performReplace(q, r, a, f) })
            Command.SearchToggleScope -> searchOverlay.toggleScope()
            Command.SearchSwitchField -> searchOverlay.switchField()
            Command.OpenFile -> {
                if (bufferManager.currentBufferIndex == 8) {
                    Toast.makeText(this, "Cannot open files in Scratchpad", Toast.LENGTH_SHORT).show()
                } else {
                    handleOverlayCommand(Command.OverlayCancel)
                    openDocumentLauncher.launch(arrayOf("text/*", "image/jpeg", "application/pdf", "application/octet-stream", "*/*"))
                }
            }
            Command.OpenPdfExport -> {
                val options = (0 until 8).map { bufferManager.getBufferAt(it).fileName ?: "Buffer ${it + 1}" }
                multiSelectionOverlay.show("Compile PDF", options) { if (it.isNotEmpty()) { selectedBuffersForPdf = it; createPdfLauncher.launch("compiled_buffers.pdf") } }
            }
            Command.SaveToFile -> {
                val curr = bufferManager.getCurrentBuffer()
                if (curr.fileUri != null) performUriSave(Uri.parse(curr.fileUri)) else createDocumentLauncher.launch("buffer_${bufferManager.currentBufferIndex + 1}.txt")
            }
            Command.SaveAs -> createDocumentLauncher.launch(bufferManager.getCurrentBuffer().fileName ?: "buffer_${bufferManager.currentBufferIndex + 1}.txt")
            Command.CloseFile -> {
                val buffer = bufferManager.getCurrentBuffer()
                if (!buffer.isDirty) {
                    buffer.clear()
                    editorView.invalidate(); updateStatus()
                    Toast.makeText(this, "Buffer closed", Toast.LENGTH_SHORT).show()
                } else {
                    selectionOverlay.show("Save changes?", listOf("Save", "Don't Save", "Cancel"), 0, {
                        when(it) {
                            0 -> {
                                if (buffer.fileUri != null) {
                                    performUriSave(Uri.parse(buffer.fileUri))
                                    buffer.clear()
                                    editorView.invalidate(); updateStatus()
                                } else {
                                    createDocumentLauncher.launch(buffer.fileName ?: "buffer_${bufferManager.currentBufferIndex + 1}.txt")
                                }
                            }
                            1 -> { buffer.clear(); editorView.invalidate(); updateStatus() }
                        }
                    }, {})
                }
            }
            Command.OpenBufferMenu -> {
                val options = (0 until 8).map { "B${it+1}: ${bufferManager.getBufferAt(it).fileName ?: "Empty"}" }
                selectionOverlay.show("Select Buffer", options, bufferManager.currentBufferIndex, { commandDispatcher.dispatch(Command.SwitchBuffer(it)) }, {})
            }
            Command.OpenThemeMenu -> showThemeSettingsMenu()
            Command.OpenMarginMenu -> showMarginSettingsMenu()
            Command.ToggleShowMargins -> {
                val buffer = bufferManager.getCurrentBuffer()
                buffer.showMargins = !buffer.showMargins
                bufferManager.saveBuffers()
                editorView.invalidate()
            }
            Command.GlobalApplySettings -> { bufferManager.applyCurrentSettingsToAll(); Toast.makeText(this, "Applied to all", Toast.LENGTH_SHORT).show() }
            Command.OpenFontMenu -> showFontSelectionMenu()
            Command.OpenLineSpacingMenu -> {
                val vals = listOf(1.0f, 1.15f, 1.2f, 1.5f, 1.8f, 2.0f, 2.5f)
                selectionOverlay.show("Select Spacing", vals.map { it.toString() }, vals.indexOf(bufferManager.getCurrentBuffer().lineSpacing).coerceAtLeast(0), { editorView.setLineSpacing(vals[it]); bufferManager.saveBuffers() }, {})
            }
            Command.ToggleStatus -> { isStatusVisible = !isStatusVisible; statusText.visibility = if (isStatusVisible) View.VISIBLE else View.GONE; saveStatusVisibility() }
            is Command.AdjustVerticalOffset -> {
                val next = (editorView.getVerticalOffset() + cmd.delta).coerceIn(0f, 60f)
                editorView.setVerticalOffset(next); bufferManager.saveBuffers()
            }
            Command.MoveToViewportCenter -> { bufferManager.getCurrentBuffer().setCursor(editorView.getIndexAtViewportCenter(), false); editorView.scrollToCursor(); updateStatus() }
            Command.StartFindCursor -> editorView.startFindCursor()
            Command.StopFindCursor -> editorView.stopFindCursor()
            Command.OpenHelpMenu -> {
                selectionOverlay.show("Help Menu", listOf("Key Assignments", "Instructions", "About"), 0, {
                    when(it) {
                        0 -> commandDispatcher.dispatch(Command.ShowKeymap)
                        1 -> commandDispatcher.dispatch(Command.ShowHelp)
                        2 -> commandDispatcher.dispatch(Command.ShowAbout)
                    }
                }, {})
            }
            Command.ShowHelp -> try { helpOverlay.show(assets.open("INSTRUCTIONS.md").bufferedReader().use { it.readText() }) } catch (e: Exception) {}
            Command.ShowKeymap -> referenceOverlay.show("ElmerPad_Keymap.jpg")
            Command.ShowAbout -> aboutOverlay.show()
            Command.OpenSettingsMenu -> {
                if (selectionOverlay.visibility == View.VISIBLE && selectionOverlay.getTitle() == "SETTINGS") {
                    // Already open, just close it (toggle behavior)
                    handleOverlayCommand(Command.OverlayCancel)
                } else {
                    showSettingsMasterMenu()
                }
            }
            Command.OpenWallpaperMenu -> showWallpaperMenu()
            Command.OpenCursorMenu -> showCursorMenu()
            Command.OverlayUp -> {
                if (helpOverlay.visibility == View.VISIBLE) helpOverlay.scrollUp()
                else if (searchOverlay.visibility == View.VISIBLE) searchOverlay.moveHistory(true)
                else if (selectionOverlay.visibility == View.VISIBLE) selectionOverlay.moveUp()
                else if (multiSelectionOverlay.visibility == View.VISIBLE) multiSelectionOverlay.moveUp()
                else if (outlineOverlay.visibility == View.VISIBLE) outlineOverlay.moveUp()
            }
            Command.OverlayDown -> {
                if (helpOverlay.visibility == View.VISIBLE) helpOverlay.scrollDown()
                else if (searchOverlay.visibility == View.VISIBLE) searchOverlay.moveHistory(false)
                else if (selectionOverlay.visibility == View.VISIBLE) selectionOverlay.moveDown()
                else if (multiSelectionOverlay.visibility == View.VISIBLE) multiSelectionOverlay.moveDown()
                else if (outlineOverlay.visibility == View.VISIBLE) outlineOverlay.moveDown()
            }
            is Command.OverlayJump -> { if (multiSelectionOverlay.visibility == View.VISIBLE) multiSelectionOverlay.selectIndex(cmd.index) else selectionOverlay.selectIndex(cmd.index) }
            Command.OverlaySelect -> {
                if (selectionOverlay.visibility == View.VISIBLE) selectionOverlay.confirm()
                else if (inputOverlay.visibility == View.VISIBLE) inputOverlay.confirm()
                else if (searchOverlay.visibility == View.VISIBLE) searchOverlay.confirm(true)
                else if (multiSelectionOverlay.visibility == View.VISIBLE) multiSelectionOverlay.confirm()
                else if (outlineOverlay.visibility == View.VISIBLE) outlineOverlay.confirm()
            }
            Command.OverlaySelectBackward -> if (searchOverlay.visibility == View.VISIBLE) searchOverlay.confirm(false)
            Command.OverlayToggle -> if (multiSelectionOverlay.visibility == View.VISIBLE) multiSelectionOverlay.toggleSelection()
            Command.OverlayDelete -> {
                if (selectionOverlay.visibility == View.VISIBLE && selectionOverlay.getTitle() == "BOOKMARKS") {
                    bufferManager.getCurrentBuffer().removeBookmark(selectionOverlay.getSelectedIndex() - 1)
                    bufferManager.saveBuffers(); handleOverlayCommand(Command.OpenBookmarkMenu)
                }
            }
            Command.OverlayDeleteAll -> {
                if (selectionOverlay.visibility == View.VISIBLE && selectionOverlay.getTitle() == "BOOKMARKS") {
                    bufferManager.getCurrentBuffer().clearBookmarks(); bufferManager.saveBuffers(); selectionOverlay.hide()
                }
            }
            Command.OpenImageViewer -> showImageFolderPicker()
            Command.OpenImageSettings -> showImageSettingsMenu()
            Command.ImageNext -> moveViewer(1)
            Command.ImagePrev -> moveViewer(-1)
            Command.ToggleSlideshow -> toggleSlideshow()
            Command.OverlayCancel -> {
                slideshowActive = false
                slideshowHandler.removeCallbacks(slideshowRunnable)
                selectionOverlay.hide(); multiSelectionOverlay.hide(); inputOverlay.hide(); helpOverlay.hide(); searchOverlay.hide(); outlineOverlay.hide(); referenceOverlay.hide(); aboutOverlay.hide(); editorView.clearViewerImage()
            }
            Command.OverlayToggleMarginPreview -> {
                if (selectionOverlay.visibility == View.VISIBLE) selectionOverlay.togglePreviewMargins()
            }
            is Command.InputChar -> { if (inputOverlay.visibility == View.VISIBLE) inputOverlay.handleKey(cmd.char) else if (searchOverlay.visibility == View.VISIBLE) searchOverlay.handleKey(cmd.char) }
            Command.InputDelete -> { if (inputOverlay.visibility == View.VISIBLE) inputOverlay.deleteChar() else if (searchOverlay.visibility == View.VISIBLE) searchOverlay.deleteChar() }
            else -> {}
        }
    }

    private fun showWallpaperMenu() {
        val colors = listOf(
            android.graphics.Color.rgb(38, 139, 210),   // Blue
            android.graphics.Color.rgb(133, 153, 0),   // Green
            android.graphics.Color.rgb(253, 246, 227), // Wheat
            android.graphics.Color.rgb(42, 161, 152),  // Cyan
            android.graphics.Color.rgb(220, 50, 47),   // Red
            android.graphics.Color.rgb(211, 54, 130)   // Magenta
        )
        val options = listOf("Solarized Blue", "Solarized Green", "Solarized Wheat", "Solarized Cyan", "Solarized Red", "Solarized Magenta", "Custom...")
        
        selectionOverlay.show("Set Device Wallpaper", options, 0, { index ->
            if (index < colors.size) {
                setWallpaperColor(colors[index])
            } else {
                startCustomWallpaperCreation()
            }
        }, {})
    }

    private fun startCustomWallpaperCreation() {
        inputOverlay.show("Wallpaper RGB (R,G,B):", "0,0,0", { rgbStr ->
            val pts = rgbStr.split(",").mapNotNull { it.trim().toIntOrNull()?.coerceIn(0, 255) }
            if (pts.size == 3) {
                setWallpaperColor(android.graphics.Color.rgb(pts[0], pts[1], pts[2]))
            } else {
                Toast.makeText(this, "Invalid RGB format", Toast.LENGTH_SHORT).show()
            }
        }, {})
    }

    private fun setWallpaperColor(color: Int) {
        try {
            val wm = android.app.WallpaperManager.getInstance(this)
            val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(color)
            wm.setBitmap(bitmap)
            Toast.makeText(this, "Wallpaper set", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to set wallpaper", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSettingsMasterMenu() {
        val options = listOf(
            "Cursor",
            "Font",
            "Margins",
            "Theme",
            "Scratchpad Buffer",
            "Desktop Sync"
        )
        selectionOverlay.show("Settings", options, 0, {
            when(it) {
                0 -> showCursorMenu()
                1 -> showFontSelectionMenu()
                2 -> showMarginSettingsMenu()
                3 -> showThemeSettingsMenu()
                4 -> showScratchpadSettingsMenu()
                5 -> showSyncMenu()
            }
        }, {})
    }

    private fun showFontSelectionMenu() {
        val buffer = bufferManager.getCurrentBuffer()
        val fontOptions = listOf(
            "Monospace", "Courier New", "Serif", "Sans-serif", "Consolas", 
            "Lucida Console", "Roboto Mono", "Times New Roman", "Georgia", 
            "Arial", "Helvetica", "Verdana", "Trebuchet", "Noto Sans"
        )
        val fontTypes = listOf(
            "MONOSPACE", "COURIER NEW", "SERIF", "SANS_SERIF", "CONSOLAS",
            "LUCIDA CONSOLE", "ROBOTO MONO", "TIMES NEW ROMAN", "GEORGIA",
            "ARIAL", "HELVETICA", "VERDANA", "TREBUCHET", "NOTO SANS"
        )
        val currentIndex = fontTypes.indexOf(buffer.fontType.uppercase()).coerceAtLeast(0)
        selectionOverlay.show("Select Font", fontOptions, currentIndex, { index ->
            editorView.setFontType(fontTypes[index])
            bufferManager.saveBuffers()
            showSettingsMasterMenu()
        }, { showSettingsMasterMenu() })
    }

    private fun showMarginSettingsMenu() {
        val buffer = bufferManager.getCurrentBuffer()
        val showLabel = if (buffer.showMargins) "Hide Margin" else "Show Margin"
        selectionOverlay.show("Adjust Margins", listOf("Side Margins...", "Top/Bottom Margins...", showLabel, "Global Apply Settings"), 0, {
            when(it) { 
                0 -> showSideMarginMenu()
                1 -> showTopBottomMarginMenu()
                2 -> {
                    buffer.showMargins = !buffer.showMargins
                    bufferManager.saveBuffers()
                    editorView.invalidate()
                    showMarginSettingsMenu()
                }
                3 -> {
                    bufferManager.applyCurrentSettingsToAll()
                    Toast.makeText(this, "Applied to all", Toast.LENGTH_SHORT).show()
                    showMarginSettingsMenu()
                }
            }
        }, { showSettingsMasterMenu() })
    }

    private fun showCursorMenu() {
        val buffer = bufferManager.getCurrentBuffer()
        val options = listOf(
            "Style: ${buffer.cursorSettings.style.name.replace("_", " ")}",
            "Blink: ${if (buffer.cursorSettings.blinkEnabled) "ON" else "OFF"}",
            "Interval: ${buffer.cursorSettings.blinkIntervalMs}ms",
            "Virtual Input (Supernote Fix): ${if (buffer.useVirtualInput) "ON" else "OFF"}"
        )
        selectionOverlay.show("Cursor Settings", options, 0, { index ->
            when (index) {
                0 -> showCursorStyleMenu()
                1 -> {
                    buffer.cursorSettings.blinkEnabled = !buffer.cursorSettings.blinkEnabled
                    bufferManager.saveBuffers()
                    showCursorMenu()
                }
                2 -> showCursorIntervalMenu()
                3 -> {
                    buffer.useVirtualInput = !buffer.useVirtualInput
                    bufferManager.saveBuffers()
                    // Re-request layout to trigger InputConnection change
                    editorView.invalidate()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.restartInput(editorView)
                    showCursorMenu()
                }
            }
        }, { showSettingsMasterMenu() })
    }

    private fun showCursorStyleMenu() {
        val buffer = bufferManager.getCurrentBuffer()
        val styles = com.example.minimaltexteditor.model.EditorBuffer.CursorStyle.values()
        val options = styles.map { it.name.replace("_", " ") }
        val currentIdx = styles.indexOf(buffer.cursorSettings.style)
        selectionOverlay.show("Select Cursor Style", options, currentIdx, { index ->
            buffer.cursorSettings.style = styles[index]
            bufferManager.saveBuffers()
            editorView.invalidate()
            showCursorMenu()
        }, { showCursorMenu() })
    }

    private fun showCursorIntervalMenu() {
        val buffer = bufferManager.getCurrentBuffer()
        val intervals = listOf(250L, 500L, 750L, 1000L)
        val options = intervals.map { "${it}ms" }
        val currentIdx = intervals.indexOf(buffer.cursorSettings.blinkIntervalMs).coerceAtLeast(0)
        selectionOverlay.show("Blink Interval", options, currentIdx, { index ->
            buffer.cursorSettings.blinkIntervalMs = intervals[index]
            bufferManager.saveBuffers()
            showCursorMenu()
        }, { showCursorMenu() })
    }

    private fun showImageFolderPicker() {
        val folders = imageFolderManager.getFolders()
        if (folders.isEmpty()) {
            Toast.makeText(this, "No linked folders. Press Alt+F8 to setup.", Toast.LENGTH_SHORT).show()
            return
        }
        val options = folders.map { (if (it.isRemote) "[REMOTE] " else "[LOCAL] ") + it.name }
        selectionOverlay.show("Select Image Folder", options, 0, { index ->
            startViewer(folders[index])
        }, {})
    }

    private fun startViewer(folder: ImageFolderManager.LinkedFolder) {
        if (folder.isRemote) {
            if (!syncManager.isConnected()) {
                Toast.makeText(this, "Connect to Desktop first", Toast.LENGTH_SHORT).show()
                return
            }
            syncManager.fetchRemoteFileList(folder.path) { items ->
                val images = items.filter { !it.isDir && (it.name.lowercase().endsWith(".jpg") || it.name.lowercase().endsWith(".jpeg")) }
                if (images.isEmpty()) {
                    Toast.makeText(this, "No JPEGs in remote folder", Toast.LENGTH_SHORT).show()
                } else {
                    viewerFiles = images.map { it.path }.toMutableList()
                    viewerIndex = 0
                    isViewerRemote = true
                    loadViewerImage()
                }
            }
        } else {
            // Local folder
            try {
                val uri = Uri.parse(folder.path)
                val docUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(uri, android.provider.DocumentsContract.getTreeDocumentId(uri))
                val cursor = contentResolver.query(docUri, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE, android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)
                val images = mutableListOf<Pair<String, Long>>()
                cursor?.use {
                    while (it.moveToNext()) {
                        val mime = it.getString(2)
                        if (mime == "image/jpeg") {
                            val id = it.getString(0)
                            val name = it.getString(1)
                            val mtime = it.getLong(3)
                            val fullUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, id).toString()
                            images.add(fullUri to mtime)
                        }
                    }
                }
                if (images.isEmpty()) {
                    Toast.makeText(this, "No JPEGs in local folder", Toast.LENGTH_SHORT).show()
                } else {
                    val sorted = if (imageFolderManager.sortMode == ImageFolderManager.SortMode.NAME) images.sortedBy { it.first } else images.sortedByDescending { it.second }
                    viewerFiles = sorted.map { it.first }.toMutableList()
                    viewerIndex = 0
                    isViewerRemote = false
                    loadViewerImage()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to read local folder", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadViewerImage() {
        if (viewerIndex !in viewerFiles.indices) return
        val path = viewerFiles[viewerIndex]
        if (isViewerRemote) {
            syncManager.fetchRemoteImage(path) { bmp ->
                if (bmp != null) {
                    editorView.setViewerBitmap(bmp, path)
                } else {
                    Toast.makeText(this, "Failed to load remote image", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            editorView.setViewerImage(path, false)
        }
    }

    private fun moveViewer(delta: Int) {
        val next = viewerIndex + delta
        if (next in viewerFiles.indices) {
            viewerIndex = next
            loadViewerImage()
        } else if (slideshowActive) {
            // Loop in slideshow
            viewerIndex = 0
            loadViewerImage()
        }
    }

    private fun toggleSlideshow() {
        slideshowActive = !slideshowActive
        if (slideshowActive) {
            Toast.makeText(this, "Slideshow: ON", Toast.LENGTH_SHORT).show()
            slideshowHandler.postDelayed(slideshowRunnable, imageFolderManager.slideshowIntervalSec * 1000L)
        } else {
            Toast.makeText(this, "Slideshow: OFF", Toast.LENGTH_SHORT).show()
            slideshowHandler.removeCallbacks(slideshowRunnable)
        }
    }

    private fun showImageSettingsMenu() {
        val options = mutableListOf(
            "Sort Mode: ${imageFolderManager.sortMode.name}",
            "Slideshow Interval: ${imageFolderManager.slideshowIntervalSec}s",
            "Link Local Folder...",
            "Link Remote Folder..."
        )
        val folders = imageFolderManager.getFolders()
        folders.forEach { options.add("Remove: ${it.name}") }

        selectionOverlay.show("Image Viewer Settings", options, 0, { index ->
            when (index) {
                0 -> { imageFolderManager.sortMode = if (imageFolderManager.sortMode == ImageFolderManager.SortMode.NAME) ImageFolderManager.SortMode.DATE else ImageFolderManager.SortMode.NAME; showImageSettingsMenu() }
                1 -> {
                    val intervals = listOf(3, 5, 10, 15, 20, 30)
                    val currentIdx = intervals.indexOf(imageFolderManager.slideshowIntervalSec).coerceAtLeast(0)
                    selectionOverlay.show("Slideshow Interval", intervals.map { "${it}s" }, currentIdx, { idx ->
                        imageFolderManager.slideshowIntervalSec = intervals[idx]
                        showImageSettingsMenu()
                    }, { showImageSettingsMenu() })
                }
                2 -> openFolderLauncher.launch(null)
                3 -> {
                    if (syncManager.isConnected()) {
                        showRemoteDirectoryPicker(".") { path ->
                            inputOverlay.show("Folder Name:", path.substringAfterLast("/"), { name ->
                                imageFolderManager.addFolder(name, path, true)
                                showImageSettingsMenu()
                            }, { showImageSettingsMenu() })
                        }
                    } else {
                        Toast.makeText(this, "Connect to Desktop first", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> { imageFolderManager.removeFolder(index - 4); showImageSettingsMenu() }
            }
        }, {})
    }

    private fun showRemoteDirectoryPicker(currentPath: String, onDirSelected: (String) -> Unit) {
        syncManager.fetchRemoteFileList(currentPath) { items ->
            val dirs = items.filter { it.isDir }
            
            // Explicit requirement: if subfolder of root has no subfolders, select it immediately
            if (currentPath != "." && dirs.isEmpty()) {
                onDirSelected(currentPath)
                return@fetchRemoteFileList
            }

            val displayList = mutableListOf<String>()
            if (currentPath != ".") {
                displayList.add(".. [BACK]")
                displayList.add(". [SELECT CURRENT FOLDER]")
            }
            
            dirs.forEach { 
                displayList.add(it.name)
            }

            if (displayList.isEmpty() && currentPath == ".") {
                Toast.makeText(this, "No folders found on desktop", Toast.LENGTH_SHORT).show()
                return@fetchRemoteFileList
            }

            // Simplified title to avoid path confusion
            selectionOverlay.show("Select Remote Directory", displayList, 0, { idx ->
                if (currentPath != ".") {
                    if (idx == 0) {
                        val parent = if (currentPath.contains("/")) currentPath.substringBeforeLast("/") else "."
                        showRemoteDirectoryPicker(parent, onDirSelected)
                        return@show
                    }
                    if (idx == 1) {
                        onDirSelected(currentPath)
                        return@show
                    }
                    // User selected a directory entry (starting at index 2)
                    showRemoteDirectoryPicker(dirs[idx - 2].path, onDirSelected)
                } else {
                    // Root directory list (starts at index 0, no back/select current here for immediate feel)
                    showRemoteDirectoryPicker(dirs[idx].path, onDirSelected)
                }
            }, { showImageSettingsMenu() })
        }
    }

    private fun showThemeSettingsMenu() {
        val buffer = bufferManager.getCurrentBuffer()
        val options = listOf("Day Theme Selection", "Night Theme Selection", "Mode: ${getModeName()}", "Day Start Time: ${getTimeStr(dayStartHour, dayStartMin)}", "Night Start Time: ${getTimeStr(nightStartHour, nightStartMin)}")
        
        val previews = listOf(
            SelectionOverlay.ThemeColors(themes[buffer.dayThemeIndex].textColor, themes[buffer.dayThemeIndex].backgroundColor),
            SelectionOverlay.ThemeColors(themes[buffer.nightThemeIndex].textColor, themes[buffer.nightThemeIndex].backgroundColor),
            SelectionOverlay.ThemeColors(0, 0),
            SelectionOverlay.ThemeColors(0, 0),
            SelectionOverlay.ThemeColors(0, 0)
        )

        selectionOverlay.show("Theme Settings", options, 0, { when(it) { 0 -> showThemeChoiceMenu(true); 1 -> showThemeChoiceMenu(false); 2 -> cycleThemeMode(); 3 -> showTimePicker(true); 4 -> showTimePicker(false) } }, { showSettingsMasterMenu() }, previews)
    }

    private fun showScratchpadSettingsMenu() {
        val options = listOf("Log Filename: ${bufferManager.scratchLogFilename}", "Character Limit: ${bufferManager.scratchCharLimit}")
        selectionOverlay.show("Scratchpad Settings", options, 0, {
            if (it == 0) inputOverlay.show("Enter Log Filename:", bufferManager.scratchLogFilename, { v -> bufferManager.scratchLogFilename = v; bufferManager.saveBuffers(); showScratchpadSettingsMenu() }, { showScratchpadSettingsMenu() })
            else inputOverlay.show("Enter Char Limit:", bufferManager.scratchCharLimit.toString(), { v -> val l = v.toIntOrNull() ?: 280; bufferManager.scratchCharLimit = l; bufferManager.getBufferAt(8).charLimit = l; bufferManager.saveBuffers(); showScratchpadSettingsMenu() }, { showScratchpadSettingsMenu() })
        }, { showSettingsMasterMenu() })
    }

    private fun showSyncMenu() {
        val connected = syncManager.isConnected()
        val options = mutableListOf(
            if (connected) "Disconnect" else "Connect",
            "Host IP: ${bufferManager.syncHost}",
            "Port: ${bufferManager.syncPort}",
            "Token: ${if (bufferManager.syncToken.isEmpty()) "(none)" else "****"}",
            "Auto-Push to Remote: ${if (bufferManager.isAutoSyncEnabled) "ON" else "OFF"}"
        )
        
        if (connected) {
            options.add("Sync with Remote File...")
            val currentSyncPath = bufferManager.getCurrentBuffer().syncFilePath
            if (currentSyncPath != null) {
                options.add("Manual Push Update")
            }
        }

        selectionOverlay.show("Desktop Sync", options, 0, { index ->
            when (index) {
                0 -> { if (connected) syncManager.disconnect() else syncManager.connect(); showSyncMenu() }
                1 -> inputOverlay.show("Enter Host IP:", bufferManager.syncHost, { v -> bufferManager.syncHost = v; bufferManager.saveBuffers(); showSyncMenu() }, { showSyncMenu() })
                2 -> inputOverlay.show("Enter Port:", bufferManager.syncPort.toString(), { v -> bufferManager.syncPort = v.toIntOrNull() ?: 12345; bufferManager.saveBuffers(); showSyncMenu() }, { showSyncMenu() })
                3 -> inputOverlay.show("Enter Token:", "", { v -> bufferManager.syncToken = v; bufferManager.saveBuffers(); showSyncMenu() }, { showSyncMenu() })
                4 -> { bufferManager.isAutoSyncEnabled = !bufferManager.isAutoSyncEnabled; bufferManager.saveBuffers(); showSyncMenu() }
                5 -> {
                    syncManager.isConnected().let { connected ->
                        if (connected) {
                            showRemoteFilePicker(".", { showSyncMenu() }) { path ->
                                syncManager.openRemoteFile(path)
                            }
                        }
                    }
                }
                6 -> {
                    val buffer = bufferManager.getCurrentBuffer()
                    val path = buffer.syncFilePath
                    if (path != null) {
                        syncManager.saveRemoteFile(path, buffer.getCleanText())
                        buffer.markClean()
                        updateStatus()
                        Toast.makeText(this, "Pushing update...", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }, { showSettingsMasterMenu() })
    }

    private fun showRemoteFilePicker(currentPath: String, onCancel: () -> Unit, onFileSelected: (String) -> Unit) {
        syncManager.fetchRemoteFileList(currentPath) { items ->
            if (items.isEmpty() && currentPath == ".") {
                Toast.makeText(this, "No files found on server", Toast.LENGTH_SHORT).show()
                onCancel()
                return@fetchRemoteFileList
            }

            val displayList = mutableListOf<String>()
            if (currentPath != ".") displayList.add(".. [BACK]")
            
            items.forEach { 
                val prefix = if (it.isDir) "[DIR] " else ""
                displayList.add("$prefix${it.name}")
            }

            selectionOverlay.show("Select Remote File ($currentPath)", displayList, 0, { idx ->
                var actualIdx = idx
                if (currentPath != ".") {
                    if (idx == 0) {
                        val parent = if (currentPath.contains("/")) currentPath.substringBeforeLast("/") else "."
                        showRemoteFilePicker(parent, onCancel, onFileSelected)
                        return@show
                    }
                    actualIdx--
                }
                
                val selected = items[actualIdx]
                if (selected.isDir) {
                    showRemoteFilePicker(selected.path, onCancel, onFileSelected)
                } else {
                    onFileSelected(selected.path)
                }
            }, onCancel)
        }
    }

    private fun handleSyncEvent(event: SyncManager.Event) {
        when (event) {
            is SyncManager.Event.Connected -> {
                Toast.makeText(this, "Connected to Host", Toast.LENGTH_SHORT).show()
                if (selectionOverlay.visibility == View.VISIBLE && selectionOverlay.getTitle() == "DESKTOP SYNC") showSyncMenu()
            }
            is SyncManager.Event.Disconnected -> {
                Toast.makeText(this, "Disconnected from Host", Toast.LENGTH_SHORT).show()
                if (selectionOverlay.visibility == View.VISIBLE && selectionOverlay.getTitle() == "DESKTOP SYNC") showSyncMenu()
            }
            is SyncManager.Event.Error -> {
                Toast.makeText(this, "Sync Error: ${event.message}", Toast.LENGTH_LONG).show()
            }
            is SyncManager.Event.FileReceived -> {
                val buffer = bufferManager.getCurrentBuffer()
                buffer.setText(event.content)
                buffer.syncFilePath = event.path
                buffer.fileName = "[REMOTE] ${event.path.substringAfterLast("/")}"
                bufferManager.saveBuffers()
                editorView.invalidate()
                updateStatus()
                Toast.makeText(this, "Loaded from Host: ${event.path}", Toast.LENGTH_SHORT).show()
            }
            is SyncManager.Event.SaveOk -> {
                Toast.makeText(this, "Saved to Host: ${event.path}", Toast.LENGTH_SHORT).show()
            }
            is SyncManager.Event.RemoteFileModified -> {
                val current = bufferManager.getCurrentBuffer()
                if (current.syncFilePath == event.path) {
                    if (!current.isDirty) {
                        syncManager.openRemoteFile(event.path)
                        Toast.makeText(this, "Auto-reloaded: ${event.path}", Toast.LENGTH_SHORT).show()
                    } else {
                        selectionOverlay.show("Remote file changed. Reload?", listOf("No", "Yes (Discard Local Changes)"), 0, {
                            if (it == 1) syncManager.openRemoteFile(event.path)
                        }, {})
                    }
                } else {
                    // Check background buffers
                    for (i in 0 until 8) {
                        val b = bufferManager.getBufferAt(i)
                        if (b.syncFilePath == event.path && !b.isDirty) {
                            // Silently reload in background? 
                            // This is tricky because openRemoteFile updates the CURRENT buffer in handleSyncEvent.
                            // I should probably make openRemoteFile target a specific buffer or just handle it if it's the current one.
                            // For now, let's stick to current buffer only to avoid complexity.
                        }
                    }
                }
            }
        }
    }

    private fun getModeName() = when(themeMode) { 0 -> "Auto"; 1 -> "Always Day"; else -> "Always Night" }
    private fun cycleThemeMode() { themeMode = (themeMode + 1) % 3; saveGlobalSettings(); applyTheme(); showThemeSettingsMenu() }
    private fun showThemeChoiceMenu(day: Boolean) {
        val buffer = bufferManager.getCurrentBuffer()
        val options = themes.map { it.name } + "Custom..."
        val currentIdx = if (day) buffer.dayThemeIndex else buffer.nightThemeIndex
        
        // Prepare previews
        val previews = themes.map { SelectionOverlay.ThemeColors(it.textColor, it.backgroundColor) }
        
        selectionOverlay.show(if (day) "Choose Day Theme" else "Choose Night Theme", options, currentIdx, { index ->
            if (index < themes.size) {
                if (day) buffer.dayThemeIndex = index else buffer.nightThemeIndex = index
                applyTheme()
                bufferManager.saveBuffers()
                showThemeSettingsMenu()
            } else {
                startCustomThemeCreation(day)
            }
        }, { showThemeSettingsMenu() }, previews)
    }

    private fun startCustomThemeCreation(isDay: Boolean) {
        inputOverlay.show("Theme Name:", "", { name ->
            if (name.isBlank()) return@show
            inputOverlay.show("Background RGB (R,G,B):", "0,0,0", { bgStr ->
                val bg = bgStr.split(",").mapNotNull { it.trim().toIntOrNull()?.coerceIn(0, 255) }
                if (bg.size != 3) return@show
                inputOverlay.show("Font RGB (R,G,B):", "255,255,255", { fgStr ->
                    val fg = fgStr.split(",").mapNotNull { it.trim().toIntOrNull()?.coerceIn(0, 255) }
                    if (fg.size != 3) return@show
                    
                    val bgColor = android.graphics.Color.rgb(bg[0], bg[1], bg[2])
                    val fgColor = android.graphics.Color.rgb(fg[0], fg[1], fg[2])
                    val newTheme = EditorTheme(name, fgColor, bgColor, fgColor)
                    
                    themes.add(newTheme)
                    val newIdx = themes.size - 1
                    val buffer = bufferManager.getCurrentBuffer()
                    if (isDay) buffer.dayThemeIndex = newIdx else buffer.nightThemeIndex = newIdx
                    
                    saveGlobalSettings()
                    applyTheme()
                    bufferManager.saveBuffers()
                    Toast.makeText(this, "Theme Created: $name", Toast.LENGTH_SHORT).show()
                }, { showThemeChoiceMenu(isDay) })
            }, { showThemeChoiceMenu(isDay) })
        }, { showThemeChoiceMenu(isDay) })
    }

    private fun showTimePicker(day: Boolean) {
        val options = mutableListOf<String>(); for (h in 0..23) for (m in listOf(0, 30)) options.add(getTimeStr(h, m))
        val currentH = if (day) dayStartHour else nightStartHour; val currentM = if (day) dayStartMin else nightStartMin
        selectionOverlay.show(if (day) "Select Day Start" else "Select Night Start", options, currentH * 2 + (if (currentM == 30) 1 else 0), { 
            val h = it / 2; val m = if (it % 2 == 1) 30 else 0
            if (day) { dayStartHour = h; dayStartMin = m } else { nightStartHour = h; nightStartMin = m }
            saveGlobalSettings(); applyTheme(); showThemeSettingsMenu()
        }, { showThemeSettingsMenu() })
    }

    private fun getTimeStr(h: Int, m: Int): String {
        val suffix = if (h < 12) "AM" else "PM"; val h12 = if (h == 0) 12 else if (h > 12) h - 12 else h
        return String.format("%d:%02d %s", h12, m, suffix)
    }

    private fun showSideMarginMenu() {
        val vals = listOf(0f, 20f, 60f, 120f, 200f)
        selectionOverlay.show("Side Margins", listOf("0px", "20px", "60px", "120px", "200px", "Custom..."), vals.indexOf(bufferManager.getCurrentBuffer().sideMargin).let { if (it == -1) 5 else it }, {
            if (it < 5) { editorView.setSideMargin(vals[it]); bufferManager.saveBuffers() }
            else inputOverlay.show("Enter Side Margin (px):", bufferManager.getCurrentBuffer().sideMargin.toInt().toString(), { v -> editorView.setSideMargin(v.toFloatOrNull() ?: 20f); bufferManager.saveBuffers() }, { showSideMarginMenu() })
        }, { handleOverlayCommand(Command.OpenMarginMenu) })
    }

    private fun showTopBottomMarginMenu() {
        val vals = listOf(0f, 20f, 60f, 120f, 200f)
        selectionOverlay.show("Top/Bottom Margins", listOf("0px", "20px", "60px", "120px", "200px", "Custom..."), vals.indexOf(bufferManager.getCurrentBuffer().topBottomMargin).let { if (it == -1) 5 else it }, {
            if (it < 5) { editorView.setTopBottomMargin(vals[it]); bufferManager.saveBuffers() }
            else inputOverlay.show("Enter Top Margin (px):", bufferManager.getCurrentBuffer().topBottomMargin.toInt().toString(), { v -> editorView.setTopBottomMargin(v.toFloatOrNull() ?: 60f); bufferManager.saveBuffers() }, { showTopBottomMarginMenu() })
        }, { handleOverlayCommand(Command.OpenMarginMenu) })
    }

    private fun performFind(query: String, all: Boolean, forward: Boolean) {
        if (query.isEmpty()) return
        var idx = bufferManager.currentBufferIndex; var start = if (forward) bufferManager.getCurrentBuffer().cursorPosition + 1 else bufferManager.getCurrentBuffer().getSelectionRange().first - 1
        var searched = 0
        while (searched < bufferManager.getBufferCount()) {
            val buffer = bufferManager.getBufferAt(idx); val index = if (forward) buffer.find(query, start) else buffer.findBackward(query, if (start < 0) buffer.getLength() else start)
            if (index != -1) { if (idx != bufferManager.currentBufferIndex) commandDispatcher.dispatch(Command.SwitchBuffer(idx)); buffer.setSelection(index, index + query.length); editorView.scrollToCursor(); updateStatus(); return }
            if (!all) break; searched++; idx = if (forward) (idx + 1) % bufferManager.getBufferCount() else (idx - 1 + bufferManager.getBufferCount()) % bufferManager.getBufferCount(); start = if (forward) 0 else -1
        }
        Toast.makeText(this, "Not found", Toast.LENGTH_SHORT).show()
    }

    private fun performReplace(query: String, replace: String, all: Boolean, forward: Boolean) {
        if (query.isEmpty()) return
        val buffer = bufferManager.getCurrentBuffer()
        if (buffer.getSelectedText().equals(query, ignoreCase = true)) { buffer.replace(buffer.getSelectionRange(), replace); editorView.invalidate(); updateStatus() }
        performFind(query, all, forward)
    }

    private fun handleDocumentLinked(uri: Uri) {
        val containerId = pendingLinkContainerId ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val name = getFileName(uri)
            outlineManager.linkFile(containerId, uri.toString(), name)
            outlineOverlay.refresh()
            Toast.makeText(this, "Linked: $name", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Link failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleOutlineFileSelected(path: String, name: String, isRemote: Boolean) {
        if (bufferManager.currentBufferIndex == 8) {
            Toast.makeText(this, "Cannot load files into Scratchpad", Toast.LENGTH_SHORT).show()
            return
        }

        if (isRemote) {
            if (syncManager.isConnected()) {
                syncManager.openRemoteFile(path)
            } else {
                Toast.makeText(this, "Connect to Desktop first", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val uri = Uri.parse(path)
        val buffer = bufferManager.getCurrentBuffer()
        
        if (name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".jpeg")) {
            buffer.imageUri = path
            buffer.pdfUri = null
            buffer.fileName = name
            buffer.imageScale = 1f
            buffer.imagePanX = 0f
            buffer.imagePanY = 0f
            bufferManager.saveBuffers()
            editorView.invalidate()
            updateStatus()
        } else if (name.lowercase().endsWith(".pdf")) {
            buffer.pdfUri = path
            buffer.imageUri = null
            buffer.fileName = name
            bufferManager.saveBuffers()
            editorView.invalidate()
            updateStatus()
        } else {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val text = stream.bufferedReader().readText()
                    buffer.imageUri = null
                    buffer.pdfUri = null
                    buffer.isMarkdownMode = name.lowercase().endsWith(".md")
                    buffer.setText(text)
                    buffer.fileName = name
                    buffer.fileUri = path
                    bufferManager.saveBuffers()
                    editorView.invalidate()
                    updateStatus()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Load failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleOutlineSetupAction(item: OutlineManager.OutlineItem?, action: OutlineOverlay.Action) {
        when (action) {
            OutlineOverlay.Action.ADD_FOLDER -> {
                inputOverlay.show("Folder Name:", "", { name ->
                    if (name.isNotBlank()) {
                        outlineManager.addFolder(name)
                        outlineOverlay.refresh()
                    }
                }, { outlineOverlay.refresh() })
            }
            OutlineOverlay.Action.ADD_CONTAINER -> {
                if (item is OutlineManager.OutlineItem.Folder) {
                    inputOverlay.show("Container Name:", "", { name ->
                        if (name.isNotBlank()) {
                            outlineManager.addContainer(item.id, name)
                            outlineOverlay.refresh()
                        }
                    }, { outlineOverlay.refresh() })
                } else {
                    Toast.makeText(this, "Select a Folder first", Toast.LENGTH_SHORT).show()
                }
            }
            OutlineOverlay.Action.DELETE -> {
                if (item != null) {
                    val type = if (item is OutlineManager.OutlineItem.Folder) "folder" else "container"
                    selectionOverlay.show("Remove $type from Outline? File not changed.", listOf("No", "Yes"), 0, {
                        if (it == 1) {
                            outlineManager.deleteItem(item.id)
                            outlineOverlay.refresh()
                        }
                    }, { outlineOverlay.refresh() })
                }
            }
            OutlineOverlay.Action.RENAME -> {
                if (item != null) {
                    inputOverlay.show("Rename ${item.name}:", item.name, { name ->
                        if (name.isNotBlank()) {
                            outlineManager.renameItem(item.id, name)
                            outlineOverlay.refresh()
                        }
                    }, { outlineOverlay.refresh() })
                }
            }
            OutlineOverlay.Action.LINK -> {
                if (item is OutlineManager.OutlineItem.FileContainer) {
                    pendingLinkContainerId = item.id
                    linkDocumentLauncher.launch(arrayOf("text/*", "image/jpeg", "application/pdf", "application/octet-stream", "*/*"))
                }
            }
            OutlineOverlay.Action.LINK_REMOTE -> {
                if (item is OutlineManager.OutlineItem.FileContainer) {
                    if (syncManager.isConnected()) {
                        showRemoteFilePicker(".", { outlineOverlay.refresh() }) { path ->
                            outlineManager.linkRemoteFile(item.id, path)
                            outlineOverlay.refresh()
                        }
                    } else {
                        Toast.makeText(this, "Connect to Desktop first", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            OutlineOverlay.Action.UNLINK -> {
                if (item is OutlineManager.OutlineItem.FileContainer) {
                    outlineManager.unlinkItem(item.id)
                    outlineOverlay.refresh()
                    Toast.makeText(this, "Unlinked: ${item.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleOutlineCreated(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            outlineManager.currentFileUri = uri.toString()
            performOutlineSave(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleOutlineOpened(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.openInputStream(uri)?.use { stream ->
                val data = stream.bufferedReader().readText()
                outlineManager.deserialize(data)
                outlineManager.currentFileUri = uri.toString()
                outlineOverlay.refresh()
                Toast.makeText(this, "Outline Loaded", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performOutlineSave(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(outlineManager.serialize().toByteArray())
                outlineManager.markClean()
                Toast.makeText(this, "Outline Saved", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun adjustBrightness(delta: Float) {
        if (currentBrightness == -1f) {
            // Initialize from system if currently at default
            currentBrightness = android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
        }
        currentBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
        val lp = window.attributes
        lp.screenBrightness = currentBrightness
        window.attributes = lp
        
        val percent = (currentBrightness * 100).toInt()
        Toast.makeText(this, "Brightness: $percent%", Toast.LENGTH_SHORT).show()
    }

    private fun resetAutoSyncTimer() {
        autoSyncHandler.removeCallbacks(autoSyncRunnable)
        val buffer = bufferManager.getCurrentBuffer()
        if (buffer.syncFilePath != null && bufferManager.isAutoSyncEnabled && syncManager.isConnected()) {
            autoSyncHandler.postDelayed(autoSyncRunnable, 3000) // 3 second debounce
        }
    }

    private fun triggerAutoSyncPush() {
        val buffer = bufferManager.getCurrentBuffer()
        val path = buffer.syncFilePath
        if (path != null && syncManager.isConnected() && buffer.isDirty) {
            syncManager.saveRemoteFile(path, buffer.getCleanText())
            buffer.markClean() // Mark clean locally once pushed
            updateStatus()
        }
    }

    private fun handleDocumentOpened(uri: Uri) {
        if (bufferManager.currentBufferIndex == 8) {
            Toast.makeText(this, "Cannot load files into Scratchpad", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val name = getFileName(uri)
            val buffer = bufferManager.getCurrentBuffer()
            
            if (name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".jpeg")) {
                buffer.imageUri = uri.toString()
                buffer.pdfUri = null
                buffer.fileName = name
                buffer.imageScale = 1f
                buffer.imagePanX = 0f
                buffer.imagePanY = 0f
                bufferManager.saveBuffers()
                handleOverlayCommand(Command.OverlayCancel)
                editorView.invalidate()
                updateStatus()
                Toast.makeText(this, "Loaded Image $name", Toast.LENGTH_SHORT).show()
            } else if (name.lowercase().endsWith(".pdf")) {
                buffer.pdfUri = uri.toString()
                buffer.imageUri = null
                buffer.fileName = name
                bufferManager.saveBuffers()
                handleOverlayCommand(Command.OverlayCancel)
                editorView.invalidate()
                updateStatus()
                Toast.makeText(this, "Loaded PDF $name", Toast.LENGTH_SHORT).show()
            } else {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val text = stream.bufferedReader().readText()
                    buffer.imageUri = null
                    buffer.pdfUri = null
                    buffer.isMarkdownMode = false // Reset mode on load
                    buffer.setText(text); buffer.fileName = name; buffer.fileUri = uri.toString()
                    bufferManager.saveBuffers()
                    handleOverlayCommand(Command.OverlayCancel)
                    editorView.invalidate()
                    updateStatus()
                    Toast.makeText(this, "Loaded ${buffer.fileName}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) { Toast.makeText(this, "Open failed", Toast.LENGTH_LONG).show() }
    }

    private fun handleDocumentCreated(uri: Uri) {
        contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        bufferManager.setBufferFile(uri.toString(), getFileName(uri)); performUriSave(uri)
    }

    private fun handlePdfCreated(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                val document = PdfDocument(); val paint = Paint().apply { textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE }
                val pageWidth = 595; val pageHeight = 842; val margin = 50; val contentWidth = pageWidth - 2 * margin
                var pageNum = 1
                for (bufferIdx in selectedBuffersForPdf) {
                    val buffer = bufferManager.getBufferAt(bufferIdx); val lines = wrapTextForPdf(buffer.getCleanText(), paint, contentWidth)
                    var y = margin; var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create(); var page = document.startPage(pageInfo); var canvas = page.canvas
                    paint.isFakeBoldText = true; canvas.drawText("BUFFER ${bufferIdx + 1}: ${buffer.fileName ?: "Unnamed"}", margin.toFloat(), y.toFloat(), paint)
                    paint.isFakeBoldText = false; y += 25
                    for (line in lines) {
                        if (y > pageHeight - margin) { document.finishPage(page); pageNum++; pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create(); page = document.startPage(pageInfo); canvas = page.canvas; y = margin }
                        canvas.drawText(line, margin.toFloat(), y.toFloat(), paint); y += 15
                    }
                    document.finishPage(page); pageNum++
                }
                document.writeTo(stream); document.close(); Toast.makeText(this, "PDF created", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { Toast.makeText(this, "PDF failed", Toast.LENGTH_LONG).show() }
    }

    private fun wrapTextForPdf(text: String, paint: Paint, maxWidth: Int): List<String> {
        val result = mutableListOf<String>()
        for (line in text.split("\n")) {
            var currentLine = ""
            for (word in line.split(" ")) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(testLine) <= maxWidth) currentLine = testLine else { if (currentLine.isNotEmpty()) result.add(currentLine); currentLine = word }
            }
            result.add(currentLine)
        }
        return result
    }

    private fun getFileName(uri: Uri): String {
        var name = "file.txt"
        contentResolver.query(uri, null, null, null, null)?.use { cursor -> if (cursor.moveToFirst()) name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) }
        return name
    }

    private fun performUriSave(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(bufferManager.getCurrentBuffer().getCleanText().toByteArray()) }
            bufferManager.getCurrentBuffer().markClean()
            Toast.makeText(this, "Saved successfully", Toast.LENGTH_SHORT).show(); updateStatus()
        } catch (e: Exception) { Toast.makeText(this, "Save failed", Toast.LENGTH_LONG).show() }
    }

    private fun applyTheme() {
        if (isSplitActive) {
            applyThemeToView(leftEditorView, bufferManager.getBufferAt(leftBufferIdx))
            applyThemeToView(rightEditorView, bufferManager.getBufferAt(rightBufferIdx))
        } else {
            applyThemeToView(editorView, bufferManager.getCurrentBuffer())
        }
    }

    private fun applyThemeToView(view: EditorView, buffer: com.example.minimaltexteditor.model.EditorBuffer) {
        val theme = themes[if (isDaytime()) buffer.dayThemeIndex else buffer.nightThemeIndex]
        view.setTextColor(theme.textColor)
        view.setBackgroundColor(theme.backgroundColor)
        view.setCursorColor(theme.cursorColor)
    }

    private fun enterSplitMode(left: Int, right: Int) {
        bufferManager.saveBuffers()
        isSplitActive = true
        activePanel = 0
        leftBufferIdx = left
        rightBufferIdx = right
        
        leftEditorView.setBuffer(bufferManager.getBufferAt(left))
        rightEditorView.setBuffer(bufferManager.getBufferAt(right))
        rightEditorView.visibility = View.VISIBLE
        
        editorView = leftEditorView
        bufferManager.switchBuffer(left)
        
        applyTheme()
        updateStatus()
        Toast.makeText(this, "Split Mode: B${left+1} | B${right+1}", Toast.LENGTH_SHORT).show()
    }

    private fun swapSplitFocus() {
        if (!isSplitActive) return
        bufferManager.saveBuffers()
        activePanel = 1 - activePanel
        val currentIdx = if (activePanel == 0) leftBufferIdx else rightBufferIdx
        bufferManager.switchBuffer(currentIdx)
        editorView = if (activePanel == 0) leftEditorView else rightEditorView
        updateStatus()
        applyTheme()
        Toast.makeText(this, "Focus: ${if (activePanel==0) "Left" else "Right"}", Toast.LENGTH_SHORT).show()
    }

    private fun exitSplitMode() {
        if (!isSplitActive) return
        bufferManager.saveBuffers()
        isSplitActive = false
        rightEditorView.visibility = View.GONE
        editorView = leftEditorView
        bufferManager.switchBuffer(leftBufferIdx)
        applyTheme()
        updateStatus()
        Toast.makeText(this, "Exit Split", Toast.LENGTH_SHORT).show()
    }

    private fun isDaytime(): Boolean {
        if (themeMode == 1) return true; if (themeMode == 2) return false
        val now = Calendar.getInstance(); val currentTime = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val dayStart = dayStartHour * 60 + dayStartMin; val nightStart = nightStartHour * 60 + nightStartMin
        return if (dayStart < nightStart) currentTime in dayStart until nightStart else currentTime >= dayStart || currentTime < nightStart
    }

    private fun setupUI() {
        val root = FrameLayout(this)
        
        editorContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        
        leftEditorView = EditorView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setBuffer(bufferManager.getCurrentBuffer())
            onCursorMoved = { updateStatus() }
            onRightClick = { x, y -> showContextMenu(x, y) }
            onNextImage = { moveViewer(1) }
            onPrevImage = { moveViewer(-1) }
        }
        
        rightEditorView = EditorView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            visibility = View.GONE
            onCursorMoved = { updateStatus() }
            onRightClick = { x, y -> showContextMenu(x, y) }
            onNextImage = { moveViewer(1) }
            onPrevImage = { moveViewer(-1) }
        }
        
        editorView = leftEditorView
        
        editorContainer.addView(leftEditorView)
        editorContainer.addView(rightEditorView)
        root.addView(editorContainer)
        
        statusText = TextView(this).apply { setTextColor(android.graphics.Color.GRAY); textSize = 14f; setPadding(20, 0, 20, 20) }
        root.addView(statusText, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END))
        
        outlineOverlay = OutlineOverlay(this).apply { visibility = View.GONE }; root.addView(outlineOverlay)
        referenceOverlay = ReferenceOverlay(this).apply { visibility = View.GONE }; root.addView(referenceOverlay)
        aboutOverlay = AboutOverlay(this).apply { visibility = View.GONE }; root.addView(aboutOverlay)
        selectionOverlay = SelectionOverlay(this).apply { visibility = View.GONE }; root.addView(selectionOverlay)
        inputOverlay = InputOverlay(this).apply { visibility = View.GONE }; root.addView(inputOverlay)
        anchorView = View(this).apply { layoutParams = FrameLayout.LayoutParams(1, 1); visibility = View.INVISIBLE }; root.addView(anchorView)
        helpOverlay = HelpOverlay(this).apply { visibility = View.GONE }; root.addView(helpOverlay)
        searchOverlay = SearchOverlay(this).apply { visibility = View.GONE }; root.addView(searchOverlay)
        multiSelectionOverlay = MultiSelectionOverlay(this).apply { visibility = View.GONE }; root.addView(multiSelectionOverlay)

        setContentView(root); loadStatusVisibility(); updateStatus(); applyTheme()
    }

    private fun updateStatus() {
        if (isSplitActive) {
            val left = bufferManager.getBufferAt(leftBufferIdx)
            val right = bufferManager.getBufferAt(rightBufferIdx)
            val lSync = if (left.syncFilePath != null) "(S) " else ""
            val rSync = if (right.syncFilePath != null) "(S) " else ""
            val leftName = lSync + (left.fileName ?: "Empty")
            val rightName = rSync + (right.fileName ?: "Empty")
            
            val activeLabel = if (activePanel == 0) "«" else "»"
            statusText.text = "B${leftBufferIdx+1}: $leftName $activeLabel B${rightBufferIdx+1}: $rightName"
        } else {
            val buffer = bufferManager.getCurrentBuffer()
            val syncInd = if (buffer.syncFilePath != null) " [SYNCED]" else ""
            val name = (buffer.fileName ?: "Unsaved") + syncInd
            val typeInd = if (editorView.isTypewriterActive()) " [T]" else ""
            statusText.text = "$name$typeInd | Pos: ${buffer.getWordPosition()} | Words: ${buffer.getWordCount()}"
        }
    }

    private fun saveStatusVisibility() = getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("status_visible", isStatusVisible).apply()
    private fun loadStatusVisibility() { isStatusVisible = getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("status_visible", true); statusText.visibility = if (isStatusVisible) View.VISIBLE else View.GONE }
    private fun saveGlobalSettings() {
        with(getSharedPreferences("settings", Context.MODE_PRIVATE).edit()) {
            putInt("theme_mode", themeMode); putInt("day_start_h", dayStartHour); putInt("day_start_m", dayStartMin); putInt("night_start_h", nightStartHour); putInt("night_start_m", nightStartMin)
            
            // Save custom themes
            val custom = themes.subList(baseThemes.size, themes.size)
            val encoded = custom.joinToString(";") { "${it.name}|${it.textColor}|${it.backgroundColor}" }
            putString("custom_themes", encoded)
            
            apply()
        }
    }
    private fun loadGlobalSettings() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        themeMode = prefs.getInt("theme_mode", 0); dayStartHour = prefs.getInt("day_start_h", 7); dayStartMin = prefs.getInt("day_start_m", 0); nightStartHour = prefs.getInt("night_start_h", 19); nightStartMin = prefs.getInt("night_start_m", 0)
        
        themes.clear()
        themes.addAll(baseThemes)
        val encoded = prefs.getString("custom_themes", "") ?: ""
        if (encoded.isNotEmpty()) {
            encoded.split(";").forEach {
                val pts = it.split("|")
                if (pts.size == 3) {
                    themes.add(EditorTheme(pts[0], pts[1].toInt(), pts[2].toInt(), pts[1].toInt()))
                }
            }
        }
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_V) isVKeyDown = true
        if (keyCode == KeyEvent.KEYCODE_F12 && event.isShiftPressed) { commandDispatcher.dispatch(Command.StartFindCursor); return true }
        
        // Block system Alt/Ctrl intercepts unless they are standard system chords (like Task Switcher)
        val isAlt = event.isAltPressed
        val isCtrl = event.isCtrlPressed
        
        val command = keybindingManager.getCommand(
            event, 
            selectionOverlay.visibility == View.VISIBLE, 
            inputOverlay.visibility == View.VISIBLE, 
            helpOverlay.visibility == View.VISIBLE, 
            searchOverlay.visibility == View.VISIBLE, 
            multiSelectionOverlay.visibility == View.VISIBLE,
            outlineOverlay.visibility == View.VISIBLE || referenceOverlay.visibility == View.VISIBLE || aboutOverlay.visibility == View.VISIBLE,
            editorView.isViewerActive(),
            isVKeyDown
        )
        if (command != null) { commandDispatcher.dispatch(command); return true }
        
        // If we are in the editor and an Alt key was pressed but not handled by us, 
        // we still consume it to prevent system "Symbols" menus on Lenovo/Samsung tablets.
        if (isAlt || isCtrl) {
            // Standard system exclusions (let the OS handle these)
            if (keyCode == KeyEvent.KEYCODE_TAB || keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER) {
                return super.onKeyDown(keyCode, event)
            }
            return true 
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_V) isVKeyDown = false
        if (keyCode == KeyEvent.KEYCODE_F12) commandDispatcher.dispatch(Command.StopFindCursor)
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
        // Reset manual key states to prevent them from getting stuck
        // if the app lost focus while the key was held down.
        isVKeyDown = false
        editorView.requestFocus()
        // Request soft input state to signal system about text input
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(editorView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onPause() { super.onPause(); bufferManager.saveBuffers() }
}
