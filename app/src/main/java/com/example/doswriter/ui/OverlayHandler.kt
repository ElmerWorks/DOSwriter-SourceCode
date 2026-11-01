package com.example.doswriter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.example.doswriter.MainActivity
import com.example.doswriter.logic.*
import com.example.doswriter.editor.*
import com.example.doswriter.model.EditorBuffer
import kotlin.math.max
import kotlin.math.min

internal fun MainActivity.showSplitOrientationMenu() {
    val options = listOf("Vertical (Side-by-Side)", "Horizontal (Top/Bottom)")
    selectionOverlay.show("Split Orientation", options, 0, { index ->
        splitOrientation = if (index == 0) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        
        val currentBuffer = bufferManager.getCurrentBuffer()
        if (currentBuffer.fileName?.lowercase()?.endsWith(".md") == true) {
            showStatusToast("Markdown Mode")
            enterSplitMode(bufferManager.currentBufferIndex, bufferManager.currentBufferIndex)
            rightEditorView.forceMarkdownRender = true
            rightEditorView.scrollYOffset = leftEditorView.scrollYOffset
            rightEditorView.invalidate()
        } else {
            val bufferOptions = (0 until 8).map { "B${it+1}: ${bufferManager.getBufferAt(it).fileName ?: "Empty"}" }
            selectionOverlay.show("Compare with (Second Panel):", bufferOptions, 0, { bufferIdx ->
                enterSplitMode(bufferManager.currentBufferIndex, bufferIdx)
            }, { showSplitOrientationMenu() })
        }
    }, {})
}

internal fun MainActivity.enterSplitMode(left: Int, right: Int) {
    bufferManager.saveBuffers()
    isSplitActive = true
    activePanel = 0
    leftBufferIdx = left
    rightBufferIdx = right
    
    editorContainer.orientation = splitOrientation
    
    if (splitOrientation == LinearLayout.HORIZONTAL) {
        leftEditorView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        rightEditorView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
    } else {
        leftEditorView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        rightEditorView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
    }
    
    leftEditorView.setBuffer(bufferManager.getBufferAt(left))
    rightEditorView.setBuffer(bufferManager.getBufferAt(right))
    rightEditorView.visibility = View.VISIBLE
    
    editorView = leftEditorView
    bufferManager.switchBuffer(left)
    
    applyTheme()
    updateStatus()
    showStatusToast("Split Mode: B${left+1} | B${right+1}")
}

internal fun MainActivity.swapSplitFocus() {
    if (!isSplitActive) return
    bufferManager.saveBuffers()
    activePanel = 1 - activePanel
    val currentIdx = if (activePanel == 0) leftBufferIdx else rightBufferIdx
    bufferManager.switchBuffer(currentIdx)
    editorView = if (activePanel == 0) leftEditorView else rightEditorView
    updateStatus()
    applyTheme()
    
    val focusMsg = if (splitOrientation == LinearLayout.VERTICAL) {
        if (activePanel == 0) "Focus: Top" else "Focus: Bottom"
    } else {
        if (activePanel == 0) "Focus: Left" else "Focus: Right"
    }
    showStatusToast(focusMsg)
}

internal fun MainActivity.exitSplitMode() {
    if (!isSplitActive) return
    bufferManager.saveBuffers()
    isSplitActive = false
    rightEditorView.visibility = View.GONE
    rightEditorView.forceMarkdownRender = false
    
    editorContainer.orientation = LinearLayout.HORIZONTAL
    leftEditorView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)

    editorView = leftEditorView
    bufferManager.switchBuffer(leftBufferIdx)
    applyTheme()
    updateStatus()
    showStatusToast("Exit Split")
}

internal fun MainActivity.autoSwitchMacroContext() {
    val buffer = bufferManager.getCurrentBuffer()
    val name = buffer.fileName?.lowercase() ?: ""
    if (name.endsWith(".md") || name.endsWith(".markdown") || buffer.isMarkdownMode) {
        macroManager.activeContext = "MARKDOWN"
    } else if (name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".js") || name.endsWith(".css") || name.endsWith(".html")) {
        macroManager.activeContext = "CODE"
    } else {
        macroManager.activeContext = "TEXT"
    }
}

internal fun MainActivity.showMacroContextPicker() {
    val wasConsole = macroOverlay.isConsoleMode()
    macroOverlay.showContextPicker(macroManager.getContextNames(), macroManager.activeContext) { selected ->
        macroManager.activeContext = selected
        val onSubmit: (String) -> Unit = object : (String) -> Unit {
            override fun invoke(text: String) {
                if (wasConsole) {
                    macroManager.addMacro(text)
                    bufferManager.getCurrentBuffer().insert(text)
                    editorView.invalidate()
                    editorView.scrollToCursorTop()
                    updateStatus()
                    showStatusToast("Macro Set: $text")
                } else {
                    macroManager.moveToTop(text)
                    bufferManager.getCurrentBuffer().insert(text)
                    editorView.invalidate()
                    editorView.scrollToCursorTop()
                    updateStatus()
                    showStatusToast("Pasted: $text")
                }
                macroOverlay.show(macroManager.getMacros(), macroManager.activeContext, wasConsole, this, { })
            }
        }
        macroOverlay.show(macroManager.getMacros(), macroManager.activeContext, wasConsole, onSubmit, { })
    }
}

internal fun MainActivity.showMacroAddContextInput() {
    inputOverlay.show("New Context Name:", "", { name ->
        if (name.isNotEmpty()) {
            macroManager.addContext(name)
            macroManager.activeContext = name
            macroOverlay.show(macroManager.getMacros(), name, true, { text ->
                macroManager.addMacro(text)
                bufferManager.getCurrentBuffer().insert(text)
                updateStatus()
            }, { })
        }
    }, { showMacroContextPicker() })
}

internal fun MainActivity.performFind(query: String, all: Boolean, forward: Boolean) {
    if (query.isEmpty()) return
    val buffer = bufferManager.getCurrentBuffer()
    
    val currentSelection = buffer.getSelectedText()
    val start = if (forward) {
        if (buffer.hasSelection() && currentSelection.equals(query, ignoreCase = true)) {
            max(buffer.cursorPosition, buffer.selectionAnchor)
        } else {
            min(buffer.cursorPosition, buffer.selectionAnchor)
        }
    } else {
        if (buffer.hasSelection() && currentSelection.equals(query, ignoreCase = true)) {
            min(buffer.cursorPosition, buffer.selectionAnchor) - 1
        } else {
            min(buffer.cursorPosition, buffer.selectionAnchor) - 1
        }
    }
    
    val pos = if (forward) buffer.find(query, start) else buffer.findBackward(query, start)
    
    if (pos != -1) {
        buffer.setSelection(pos, pos + query.length)
        editorView.scrollToCursor()
        updateStatus()
    } else {
        showStatusToast("Not found")
    }
}

internal fun MainActivity.performReplace(query: String, replacement: String, all: Boolean, forward: Boolean) {
    if (query.isEmpty()) return
    val buffer = bufferManager.getCurrentBuffer()
    
    if (all) {
        var count = 0
        var text = buffer.getText()
        var pos = text.indexOf(query, buffer.prefix.length, true)
        while (pos != -1) {
            buffer.replace(pos until pos + query.length, replacement)
            text = buffer.getText()
            pos = text.indexOf(query, pos + replacement.length, true)
            count++
        }
        Toast.makeText(this, "Replaced $count occurrences", Toast.LENGTH_SHORT).show()
    } else {
        if (buffer.getSelectedText().equals(query, ignoreCase = true)) {
            buffer.replace(buffer.getSelectionRange(), replacement)
        }
        performFind(query, false, forward)
    }
    editorView.invalidate()
    updateStatus()
}

internal fun MainActivity.performThumbnailLink(uri: Uri, index: Int) {
    if (index == 10) {
        bufferManager.globalThumbnailUri = uri.toString()
        bufferManager.saveBuffers()
        showStatusToast("Global Thumbnail set")
        showBufferThumbnailsSettingsMenu()
    } else if (index in 0..7) {
        val buffer = bufferManager.getBufferAt(index)
        buffer.thumbnailUri = uri.toString()
        buffer.useThumbnail = true
        bufferManager.saveBuffers()
        showStatusToast("Thumbnail set for B${index + 1}")
        showSetBufferThumbnailsMenu()
    }
    pendingThumbnailBufferIdx = -1
}

internal fun MainActivity.showFileBrowser(mode: FileBrowserOverlay.Mode, initialDirUri: String?, defaultName: String?, onThumbnailSelect: ((Uri) -> Unit)? = null, onSuccess: (() -> Unit)? = null) {
    handleOverlayCommand(Command.OverlayCancel)
    val rootUri = workingFolderManager.currentFolder?.path
    fileBrowserOverlay.show(
        mode, 
        initialDirUri,
        rootUri,
        defaultName,
        bufferManager.fileBrowserSortMode,
        onAction = { file, actionMode, name ->
            val buffer = bufferManager.getCurrentBuffer()
            
            val parentUri = if (actionMode == FileBrowserOverlay.Mode.SAVE_AS) file.uri.toString() else file.parentFile?.uri?.toString()
            if (parentUri != null) {
                workingFolderManager.lastUsedFolderUri = parentUri
                workingFolderManager.saveFolders()
            }

            if (actionMode == FileBrowserOverlay.Mode.SAVE_AS && name != null) {
                val existing = file.findFile(name)
                if (existing != null) {
                    selectionOverlay.bringToFront()
                    selectionOverlay.show("Overwrite $name?", listOf("No", "Yes"), 0, { if (it == 1) {
                        buffer.fileUri = existing.uri.toString()
                        buffer.fileName = name
                        performUriSave(existing.uri)
                        onSuccess?.invoke()
                    }}, {})
                } else {
                    val newFile = file.createFile("text/plain", name)
                    if (newFile != null) {
                        buffer.fileUri = newFile.uri.toString()
                        buffer.fileName = name
                        performUriSave(newFile.uri)
                        onSuccess?.invoke()
                    }
                }
            } else if (actionMode == FileBrowserOverlay.Mode.OPEN || actionMode == FileBrowserOverlay.Mode.BROWSE) {
                if (onThumbnailSelect != null) {
                    onThumbnailSelect(file.uri)
                } else {
                    handleDocumentOpened(file.uri)
                    onSuccess?.invoke()
                }
            }
        },
        onDelete = { file ->
            val msg = if (file.isDirectory) "Delete Folder and ALL contents: ${file.name}?" else "Delete file: ${file.name}?"
            selectionOverlay.bringToFront()
            selectionOverlay.show(msg, listOf("No", "Yes"), 0, { if (it == 1) {
                if (file.delete()) {
                    showStatusToast("Deleted")
                    fileBrowserOverlay.refresh()
                } else {
                    showStatusToast("Delete failed")
                }
            }}, {})
        },
        onNewFolder = { parent ->
            inputOverlay.bringToFront()
            inputOverlay.show("New Folder Name:", "", { name ->
                if (name.isNotEmpty()) {
                    val newDir = parent.createDirectory(name)
                    if (newDir != null) {
                        workingFolderManager.lastUsedFolderUri = newDir.uri.toString()
                        workingFolderManager.saveFolders()
                        showStatusToast("Created: $name")
                        fileBrowserOverlay.refresh()
                    } else {
                        showStatusToast("Failed to create folder")
                    }
                }
            }, {})
        }
    )
}

internal fun MainActivity.performCloseFile() {
    bufferManager.getCurrentBuffer().clear()
    bufferManager.saveBuffers()
    suppressWatermark = false
    editorView.suppressWatermark = false
    editorView.invalidate()
    updateStatus()
    showStatusToast("Buffer cleared")
}

internal fun MainActivity.handleOverlayCommand(cmd: Command) {
    val currentBuffer = bufferManager.getCurrentBuffer()
    
    if (currentBuffer.pdfUri != null) {
        val scrollStep = 120f
        when (cmd) {
            is Command.MoveUp -> { editorView.scrollManually(-scrollStep); return }
            is Command.MoveDown -> { editorView.scrollManually(scrollStep); return }
            is Command.MoveWordLeft -> { editorView.panPdf(400f); return }
            is Command.MoveWordRight -> { editorView.panPdf(-400f); return }
            is Command.MoveParagraphUp -> { editorView.scrollManually(-400f); return }
            is Command.MoveParagraphDown -> { editorView.scrollManually(400f); return }
            is Command.MoveSentenceUp -> { editorView.scrollManually(-800f); return }
            is Command.MoveSentenceDown -> { editorView.scrollManually(800f); return }
            is Command.MoveToLineStart -> { editorView.panPdfToEdge(false); return }
            is Command.MoveToLineEnd -> { editorView.panPdfToEdge(true); return }
            is Command.MoveToStart -> { editorView.scrollToTop(); return }
            is Command.MoveToEnd -> { editorView.scrollToBottom(); return }
            is Command.PageUp -> { editorView.scrollManually(-editorView.height * 0.8f); return }
            is Command.PageDown -> { editorView.scrollManually(editorView.height * 0.8f); return }
            else -> {}
        }
    }

    if (currentBuffer.isMarkdownMode) {
        val scrollStep = 100f
        when (cmd) {
            is Command.MoveUp -> { editorView.scrollManually(-scrollStep); return }
            is Command.MoveDown -> { editorView.scrollManually(scrollStep); return }
            is Command.MoveParagraphUp -> { editorView.scrollManually(-scrollStep * 3); return }
            is Command.MoveParagraphDown -> { editorView.scrollManually(scrollStep * 3); return }
            is Command.PageUp -> { editorView.scrollManually(-editorView.height * 0.8f); return }
            is Command.PageDown -> { editorView.scrollManually(editorView.height * 0.8f); return }
            is Command.MoveToStart -> { editorView.scrollToTop(); return }
            is Command.MoveToEnd -> { editorView.scrollToBottom(); return }
            else -> {}
        }
    }

    when (cmd) {
        is Command.MoveUp -> editorView.moveVisualLine(-1, cmd.selecting)
        is Command.MoveDown -> editorView.moveVisualLine(1, cmd.selecting)
        is Command.MoveToVisualLineStart -> editorView.moveVisualLineBoundary(false, cmd.selecting)
        is Command.MoveToVisualLineEnd -> editorView.moveVisualLineBoundary(true, cmd.selecting)
        is Command.PageUp -> editorView.moveVisualLine(-bufferManager.getCurrentBuffer().pageLength, cmd.selecting)
        is Command.PageDown -> editorView.moveVisualLine(bufferManager.getCurrentBuffer().pageLength, cmd.selecting)
        Command.RequestClearBuffer -> {
            selectionOverlay.show("Clear current buffer?", listOf("No", "Yes"), 1, { if (it == 1) commandDispatcher.dispatch(Command.ClearBuffer) }, {})
        }
        Command.RequestExit -> {
            selectionOverlay.show("Exit DOSwriter?", listOf("No", "Yes"), 1, { if (it == 1) finish() }, {})
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
        Command.VolumeUp -> {
            val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
        }
        Command.VolumeDown -> {
            val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
        }
        Command.Screenshot -> {
            takeScreenshot()
        }
        Command.ShareBuffer -> {
            val text = bufferManager.getCurrentBuffer().getCleanText()
            if (text.isNotEmpty()) {
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                startActivity(android.content.Intent.createChooser(sendIntent, "Share Buffer Content"))
            } else {
                showStatusToast("Buffer is empty")
            }
        }
        Command.CopyBufferToSystem -> {
            val text = bufferManager.getCurrentBuffer().getCleanText()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("DOSwriter", text))
            showStatusToast("Buffer copied to system")
        }
        Command.FullRefresh -> {
            hardwareProvider.performFullRefresh(this)
        }
        Command.TypewriterDemo -> {
            runTypewriterDemo()
        }
        Command.AddBookmark -> {
            bufferManager.getCurrentBuffer().addBookmark()
            editorView.invalidate()
            showStatusToast("Bookmark added")
            bufferManager.saveBuffers()
        }
        Command.OpenBookmarkMenu -> {
            val buffer = bufferManager.getCurrentBuffer()
            val options = buffer.bookmarks.map { "[BK] ${buffer.getLineText(it)}" }
            selectionOverlay.show("Bookmarks", options, buffer.bookmarks.size - 1, { index ->
                if (index in buffer.bookmarks.indices) {
                    buffer.setCursor(buffer.bookmarks[index], false)
                    editorView.scrollToCursorTop()
                    updateStatus()
                }
            }, {})
        }
        Command.JumpLastBookmark -> {
            val buffer = bufferManager.getCurrentBuffer()
            if (buffer.bookmarks.isNotEmpty()) {
                val last = buffer.bookmarks.removeAt(buffer.bookmarks.size - 1)
                buffer.setCursor(last, false); editorView.scrollToCursorTop(); updateStatus(); bufferManager.saveBuffers()
            }
        }
        Command.CollapseSelection -> {
            bufferManager.getCurrentBuffer().collapseSelection()
            editorView.invalidate()
            bufferManager.saveBuffers()
        }
        Command.RecollapseLastExpansion -> {
            if (bufferManager.getCurrentBuffer().recollapseLastExpansion()) {
                editorView.invalidate()
                bufferManager.saveBuffers()
                showStatusToast("Expansion recollapsed")
            } else {
                showStatusToast("No expansions to recollapse")
            }
        }
        Command.OpenFoldMenu -> {
            val buffer = bufferManager.getCurrentBuffer()
            val options = buffer.folds.map { "[+] ${buffer.getText().substring(it.start, min(it.start + 20, it.end + 1)).replace("\n", " ")}..." }
            selectionOverlay.show("Collapsed Text", options, 0, { buffer.removeFold(it); editorView.invalidate(); bufferManager.saveBuffers() }, {})
        }
        Command.OpenFileBrowser -> {
            val folder = workingFolderManager.lastUsedFolderUri ?: workingFolderManager.currentFolder?.path
            if (folder == null) {
                isRedirectedFromFileMenu = true
                showStatusToast("Link Working Folder to use DOSwriter File Browser or to Select System Browser", length = android.widget.Toast.LENGTH_LONG)
                showFileManagerSettingsMenu()
            } else {
                showFileBrowser(FileBrowserOverlay.Mode.BROWSE, folder, null)
            }
        }
        Command.OpenOutlineSelection -> {
            val buffer = bufferManager.getCurrentBuffer()
            if (buffer.isDirty) {
                selectionOverlay.show("Save changes before opening Outline?", listOf("Save", "Discard", "Cancel"), 0, {
                    when(it) {
                        0 -> { 
                            if (buffer.fileUri != null) performUriSave(Uri.parse(buffer.fileUri)) 
                            else createDocumentLauncher.launch(buffer.fileName ?: "buffer_${bufferManager.currentBufferIndex + 1}.txt")
                            outlineOverlay.show(OutlineOverlay.Mode.SELECT, outlineManager, { path, name, isRemote, shift -> handleOutlineFileSelected(path, name, isRemote, shift) }, { _, _ -> })
                        }
                        1 -> outlineOverlay.show(OutlineOverlay.Mode.SELECT, outlineManager, { path, name, isRemote, shift -> handleOutlineFileSelected(path, name, isRemote, shift) }, { _, _ -> })
                    }
                }, {})
            } else {
                outlineOverlay.show(OutlineOverlay.Mode.SELECT, outlineManager, { path, name, isRemote, shift -> handleOutlineFileSelected(path, name, isRemote, shift) }, { _, _ -> })
            }
        }
        Command.OpenOutlineSetup -> {
            outlineOverlay.show(OutlineOverlay.Mode.SETUP, outlineManager, { _, _, _, _ -> }, { item, action -> handleOutlineSetupAction(item, action) })
        }
        Command.OutlineUp -> outlineOverlay.moveUp()
        Command.OutlineDown -> outlineOverlay.moveDown()
        is Command.OutlineSelect -> outlineOverlay.confirm(cmd.shiftPressed)
        Command.OutlineAddFolder -> outlineOverlay.triggerAction(OutlineOverlay.Action.ADD_FOLDER)
        Command.OutlineAddContainer -> outlineOverlay.triggerAction(OutlineOverlay.Action.ADD_CONTAINER)
        Command.OutlineDelete -> outlineOverlay.triggerAction(OutlineOverlay.Action.DELETE)
        Command.OutlineLinkFile -> outlineOverlay.triggerAction(OutlineOverlay.Action.LINK)
        Command.OutlineLinkRemote -> outlineOverlay.triggerAction(OutlineOverlay.Action.LINK_REMOTE)
        Command.OutlineUnlink -> outlineOverlay.triggerAction(OutlineOverlay.Action.UNLINK)
        Command.OutlineRename -> outlineOverlay.triggerAction(OutlineOverlay.Action.RENAME)
        Command.SaveOutlineToFile -> {
            val uri = outlineManager.currentFileUri
            if (uri != null) performOutlineSave(Uri.parse(uri)) else createOutlineLauncher.launch("myproj.epol")
        }
        Command.SaveOutlineAs -> {
            handleOverlayCommand(Command.OverlayCancel)
            createOutlineLauncher.launch("myproj.epol")
        }
        Command.SaveToFile -> {
            val buffer = bufferManager.getCurrentBuffer()
            if (buffer.fileUri != null) {
                performUriSave(Uri.parse(buffer.fileUri))
            } else {
                handleOverlayCommand(Command.SaveAs)
            }
        }
        Command.SaveAs -> {
            val buffer = bufferManager.getCurrentBuffer()
            if (bufferManager.useWorkstationFileBrowser) {
                var initialDir = workingFolderManager.lastUsedFolderUri ?: workingFolderManager.currentFolder?.path
                if (buffer.fileUri != null) {
                    try {
                        val uri = Uri.parse(buffer.fileUri)
                        val docFile = DocumentFile.fromSingleUri(this, uri)
                        val parent = docFile?.parentFile
                        if (parent != null) initialDir = parent.uri.toString()
                    } catch (e: Exception) {}
                }

                if (initialDir == null) {
                    showStatusToast("Please Link Working Folder in Settings first")
                    showFileManagerSettingsMenu()
                } else {
                    showFileBrowser(FileBrowserOverlay.Mode.SAVE_AS, initialDir, buffer.fileName ?: "new_file.txt")
                }
            } else {
                handleOverlayCommand(Command.OverlayCancel)
                createDocumentLauncher.launch(buffer.fileName ?: "new_file.txt")
            }
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
            showStatusToast("Outline cleared")
        }
        
        Command.OpenSplitMenu -> {
            if (isSplitActive) {
                showStatusToast("Split already active")
            } else {
                showSplitOrientationMenu()
            }
        }
        Command.SwapSplitFocus -> swapSplitFocus()
        is Command.FocusPanel -> {
            if (activePanel != cmd.index) swapSplitFocus()
        }
        is Command.SplitScreen -> {
            splitOrientation = cmd.orientation
            enterSplitMode(bufferManager.currentBufferIndex, cmd.otherBufferIndex)
        }
        Command.ExitSplit -> {
            if (isSplitActive) exitSplitMode()
            else if (bufferManager.getCurrentBuffer().isMarkdownMode) {
                bufferManager.getCurrentBuffer().isMarkdownMode = false
                editorView.invalidate()
            } else if (selectionOverlay.visibility == View.VISIBLE || 
                       inputOverlay.visibility == View.VISIBLE || 
                       searchOverlay.visibility == View.VISIBLE ||
                       macroOverlay.visibility == View.VISIBLE ||
                       helpOverlay.visibility == View.VISIBLE ||
                       outlineOverlay.visibility == View.VISIBLE ||
                       multiSelectionOverlay.visibility == View.VISIBLE) {
                handleOverlayCommand(Command.OverlayCancel)
            } else {
                handleOverlayCommand(Command.OpenWorkspaceLayout)
            }
        }
        Command.ToggleMarkdownRender -> {
            val b = bufferManager.getCurrentBuffer()
            if (b.fileName?.lowercase()?.endsWith(".md") == true) {
                if (isSplitActive && leftBufferIdx == rightBufferIdx) {
                    rightEditorView.invalidate()
                    showStatusToast("Markdown Mode")
                } else {
                    b.isMarkdownMode = !b.isMarkdownMode
                    editorView.invalidate()
                    if (b.isMarkdownMode) showStatusToast("Markdown Mode")
                }
            } else {
                showStatusToast("Not a Markdown file")
            }
        }
        is Command.ForceMarkdownRender -> {
            val b = bufferManager.getCurrentBuffer()
            b.isMarkdownMode = cmd.enabled
            editorView.invalidate()
        }
        Command.ToggleTypewriterMode -> {
            editorView.toggleTypewriterMode()
            val status = if (editorView.isTypewriterActive()) "ON" else "OFF"
            showStatusToast("Typewriter Mode: $status")
        }
        Command.OpenTypewriterSettings -> showTypewriterModeMenu()
        Command.RecenterTypewriter -> {
            editorView.recenterTypewriter()
            showStatusToast("Typewriter Focused")
        }
        Command.OpenFind -> {
            searchOverlay.findText.setLength(0)
            searchOverlay.replaceText.setLength(0)
            searchOverlay.show(SearchOverlay.Mode.FIND, { q, a, f -> performFind(q, a, f) }, { _, _, _, _ -> })
        }
        Command.OpenReplace -> {
            searchOverlay.findText.setLength(0)
            searchOverlay.replaceText.setLength(0)
            searchOverlay.show(SearchOverlay.Mode.REPLACE, { _, _, _ -> }, { q, r, a, f -> performReplace(q, r, a, f) })
        }
        Command.SearchToggleScope -> searchOverlay.toggleScope()
        Command.SearchSwitchField -> searchOverlay.switchField()
        Command.SearchReplaceAll -> {
            if (searchOverlay.mode == SearchOverlay.Mode.REPLACE) {
                searchOverlay.confirm(true, true)
            }
        }
        Command.OpenFile -> {
            if (bufferManager.currentBufferIndex == 8) {
                showStatusToast("Cannot open files in Scratchpad")
            } else {
                if (bufferManager.useWorkstationFileBrowser) {
                    var initialDir = workingFolderManager.lastUsedFolderUri ?: workingFolderManager.currentFolder?.path
                    if (bufferManager.getCurrentBuffer().fileUri != null) {
                        try {
                            val uri = Uri.parse(bufferManager.getCurrentBuffer().fileUri)
                            val docFile = DocumentFile.fromSingleUri(this, uri)
                            val parent = docFile?.parentFile
                            if (parent != null) initialDir = parent.uri.toString()
                        } catch (e: Exception) {}
                    }

                    if (initialDir == null) {
                        showStatusToast("Please Link Working Folder in Settings first")
                        showFileManagerSettingsMenu()
                    } else {
                        showFileBrowser(FileBrowserOverlay.Mode.OPEN, initialDir, null)
                    }
                } else {
                    handleOverlayCommand(Command.OverlayCancel)
                    openDocumentLauncher.launch(arrayOf("text/*", "image/jpeg", "image/png", "image/gif", "application/pdf", "application/octet-stream", "*/*"))
                }
            }
        }
        Command.OpenRecentFiles -> {
            if (bufferManager.useWorkstationFileBrowser) {
                val recents = recentFileManager.getRecentFiles()
                if (recents.isEmpty()) { showStatusToast("No recent files"); return }
                val options = recents.map { it.name }
                selectionOverlay.show("Recent Files", options, 0, { index ->
                    val entry = recents[index]
                    if (entry.isRemote) syncManager.openRemoteFile(entry.path)
                    else handleDocumentOpened(Uri.parse(entry.path))
                }, {})
            } else {
                showRecentFilesMenu()
            }
        }
        Command.CloseFile -> {
            val buffer = bufferManager.getCurrentBuffer()
            if (buffer.isDirty && buffer.getCleanText().isNotEmpty()) {
                selectionOverlay.show("Unsaved changes. Close buffer?", listOf("No", "Save & Close", "Close Anyway"), 0, {
                    when(it) {
                        1 -> {
                            if (buffer.fileUri != null) {
                                performUriSave(Uri.parse(buffer.fileUri))
                                performCloseFile()
                            } else {
                                if (bufferManager.useWorkstationFileBrowser) {
                                    val initialDir = workingFolderManager.lastUsedFolderUri ?: workingFolderManager.currentFolder?.path
                                    if (initialDir == null) {
                                        showStatusToast("Please Link Working Folder in Settings first")
                                        showFileManagerSettingsMenu()
                                    } else {
                                        showFileBrowser(FileBrowserOverlay.Mode.SAVE_AS, initialDir, buffer.fileName ?: "new_file.txt", onSuccess = {
                                            performCloseFile()
                                        })
                                    }
                                } else {
                                    createDocumentLauncher.launch(buffer.fileName ?: "new_file.txt")
                                }
                            }
                        }
                        2 -> performCloseFile()
                    }
                }, {})
            } else {
                performCloseFile()
            }
        }
        Command.CloseAll -> handleCloseAll()
        Command.OpenPublishMenu -> {
            val exportOptions = listOf(
                "Standard PDF Compile (A4)", 
                "Manuscript Format PDF (WYSWYG)",
                "8-in-1 Mini-Book (Landscape)"
            )
            selectionOverlay.show("PUBLISH", exportOptions, currentPdfFormat, { formatIdx ->
                currentPdfFormat = formatIdx
                commandDispatcher.dispatch(Command.InstantPdf)
            }, {})
        }
        Command.InstantPdf -> {
            isMiniBookMode = false
            isManuscriptPdfMode = false
            
            when (currentPdfFormat) {
                0 -> {
                    val options = (0 until 8).map { bufferManager.getBufferAt(it).fileName ?: "Buffer ${it + 1}" }
                    multiSelectionOverlay.show("Standard PDF Export", options) { 
                        if (it.isNotEmpty()) { 
                            selectedBuffersForPdf = it; 
                            createPdfLauncher.launch("compiled_buffers.pdf") 
                        } 
                    }
                }
                1 -> {
                    isManuscriptPdfMode = true
                    createPdfLauncher.launch("manuscript_export.pdf")
                }
                2 -> {
                    isMiniBookMode = true
                    selectedBuffersForPdf = (0 until 8).toList()
                    createPdfLauncher.launch("mini_book_todo.pdf")
                }
            }
        }
        Command.OpenBufferMenu -> showBufferSelectionMenu()
        Command.OpenThemeMenu -> showThemeSettingsMenu()
        Command.OpenMarginMenu -> showMarginSettingsMenu()
        is Command.OpenAppMenu -> {
            if (cmd.index == 0 && workingFolderManager.currentFolder == null) {
                isRedirectedFromFileMenu = true
                showStatusToast("Link Working Folder to use DOSwriter File Browser or to Select System Browser", length = android.widget.Toast.LENGTH_LONG)
                showFileManagerSettingsMenu()
            } else {
                showAppMenu(cmd.index)
            }
        }
        Command.ToggleShowMargins -> {
            val b = bufferManager.getCurrentBuffer()
            b.showMargins = !b.showMargins
            if (isSettingsGlobal) bufferManager.applyMarginsToAll()
            bufferManager.saveBuffers(); editorView.invalidate(); applyTheme()
        }
        Command.OpenFontMenu -> showFontSelectionMenu()
        Command.OpenFontPresetMenu -> showFontPresetMenu()
        Command.OpenLineSpacingMenu -> showLineHeightMenu()
        Command.CycleLineHeight -> performCycleLineHeight()
        is Command.AdjustVerticalOffset -> {
            val next = (editorView.getVerticalOffset() + cmd.delta).coerceIn(0f, 60f)
            editorView.setVerticalOffset(next); bufferManager.saveBuffers()
        }
        Command.MoveToViewportCenter -> { 
            bufferManager.getCurrentBuffer().setCursor(editorView.getIndexAtViewportCenter(), false)
            editorView.scrollToCursor()
            editorView.startLocatorBlink()
            updateStatus() 
        }
        Command.StartFindCursor -> editorView.startFindCursor()
        Command.StopFindCursor -> editorView.stopFindCursor()
        Command.OpenHelpMenu -> showHelpMenu()
        Command.ShowHelp -> showHelpMenu()
        Command.ShowHelpPopup -> showHelpPopup()
        Command.ShowKeymap -> { onReferenceDismiss = null; referenceOverlay.show("DOSwriter_Keymap.jpg") }
        Command.ShowAbout -> { onReferenceDismiss = null; aboutOverlay.show() }
        Command.OpenSettingsMenu -> {
            if (selectionOverlay.visibility == View.VISIBLE && selectionOverlay.getTitle() == "SETTINGS") {
                handleOverlayCommand(Command.OverlayCancel)
            } else {
                showAppMenu(6)
            }
        }
        Command.OpenSyncMenu -> showSyncMenu()
        Command.DisplayTheme -> {
            val b = bufferManager.getCurrentBuffer()
            val theme = themes[if (isDaytime()) b.dayThemeIndex else b.nightThemeIndex]
            val modeLabel = if (isDaytime()) "Day" else "Night"
            showStatusToast("Theme ($modeLabel): ${theme.name}", force = true)
        }
        Command.DisplayFont -> {
            val b = bufferManager.getCurrentBuffer()
            showStatusToast("Font: ${b.fontType}", force = true)
        }
        Command.OpenScriptConsole -> scriptConsoleOverlay.show(bufferManager.scriptHistory) { line ->
            commandDispatcher.dispatch(Command.ExecuteScriptLine(line))
        }
        is Command.ExecuteScriptLine -> {
            val line = cmd.line
            bufferManager.scriptHistory.remove(line)
            bufferManager.scriptHistory.add(line)
            if (bufferManager.scriptHistory.size > 12) bufferManager.scriptHistory.removeAt(0)
            bufferManager.saveBuffers()
            commandScriptManager.executeSingleLine(line)
        }
        Command.OpenWallpaperMenu -> showWallpaperMenu()
        Command.OpenCursorMenu -> showCursorMenu()
        Command.OverlayUp -> {
            if (selectionOverlay.visibility == View.VISIBLE) selectionOverlay.moveUp()
            else if (multiSelectionOverlay.visibility == View.VISIBLE) multiSelectionOverlay.moveUp()
            else if (helpOverlay.visibility == View.VISIBLE) helpOverlay.scrollUp()
            else if (searchOverlay.visibility == View.VISIBLE) searchOverlay.moveHistory(true)
            else if (macroOverlay.visibility == View.VISIBLE) macroOverlay.moveSelection(true)
            else if (scriptConsoleOverlay.visibility == View.VISIBLE) scriptConsoleOverlay.moveHistory(true)
            else if (outlineOverlay.visibility == View.VISIBLE) outlineOverlay.moveUp()
        }
        Command.OverlayDown -> {
            if (selectionOverlay.visibility == View.VISIBLE) selectionOverlay.moveDown()
            else if (multiSelectionOverlay.visibility == View.VISIBLE) multiSelectionOverlay.moveDown()
            else if (helpOverlay.visibility == View.VISIBLE) helpOverlay.scrollDown()
            else if (searchOverlay.visibility == View.VISIBLE) searchOverlay.moveHistory(false)
            else if (macroOverlay.visibility == View.VISIBLE) macroOverlay.moveSelection(false)
            else if (scriptConsoleOverlay.visibility == View.VISIBLE) scriptConsoleOverlay.moveHistory(false)
            else if (outlineOverlay.visibility == View.VISIBLE) outlineOverlay.moveDown()
        }
        Command.OverlayLeft -> {
            if (currentAppMenuIndex != -1) {
                val next = (currentAppMenuIndex + 8) % 9
                showAppMenu(next)
            } else if (helpOverlay.visibility == View.VISIBLE) helpOverlay.prevPage()
        }
        Command.OverlayRight -> {
            if (currentAppMenuIndex != -1) {
                val next = (currentAppMenuIndex + 1) % 9
                showAppMenu(next)
            } else if (helpOverlay.visibility == View.VISIBLE) {
                if (!helpOverlay.nextPage()) {
                    handleOverlayCommand(Command.OverlayCancel)
                }
            }
        }
        is Command.OverlayJump -> { if (multiSelectionOverlay.visibility == View.VISIBLE) multiSelectionOverlay.selectIndex(cmd.index) else selectionOverlay.selectIndex(cmd.index) }
        Command.OverlaySelect -> {
            if (selectionOverlay.visibility == View.VISIBLE) {
                val title = selectionOverlay.getTitle().uppercase()
                if (title.startsWith("MANAGE FOLDERS")) {
                    val folders = imageFolderManager.getFolders()
                    val idx = selectionOverlay.getSelectedIndex()
                    if (idx in folders.indices) {
                        val f = folders[idx]
                        imageFolderManager.currentFolder = f
                        showStatusToast("Current Image Folder: ${f.name}")
                        handleOverlayCommand(Command.OverlayCancel)
                    }
                } else {
                    selectionOverlay.confirm()
                }
            }
            else if (inputOverlay.visibility == View.VISIBLE) inputOverlay.confirm()
            else if (searchOverlay.visibility == View.VISIBLE) searchOverlay.confirm(true)
            else if (macroOverlay.visibility == View.VISIBLE) macroOverlay.confirm()
            else if (scriptConsoleOverlay.visibility == View.VISIBLE) scriptConsoleOverlay.confirm()
            else if (multiSelectionOverlay.visibility == View.VISIBLE) multiSelectionOverlay.confirm()
            else if (outlineOverlay.visibility == View.VISIBLE) outlineOverlay.confirm()
        }
        Command.OverlaySelectBackward -> if (searchOverlay.visibility == View.VISIBLE) searchOverlay.confirm(false)
        Command.OverlayToggle -> if (multiSelectionOverlay.visibility == View.VISIBLE) multiSelectionOverlay.toggleSelection()
        Command.OverlayDelete -> {
            val title = selectionOverlay.getTitle().uppercase()
            val selectedIdx = selectionOverlay.getSelectedIndex() - 1
            if (selectionOverlay.visibility == View.VISIBLE && title == "BOOKMARKS") {
                if (selectedIdx >= 0) {
                    bufferManager.getCurrentBuffer().removeBookmark(selectedIdx)
                    bufferManager.saveBuffers(); handleOverlayCommand(Command.OpenBookmarkMenu)
                }
            } else if (selectionOverlay.visibility == View.VISIBLE && title.startsWith("MANAGE FOLDERS")) {
                val folders = imageFolderManager.getFolders()
                if (selectedIdx in folders.indices) {
                    val removed = folders[selectedIdx]
                    if (imageFolderManager.currentFolder?.path == removed.path) {
                        imageFolderManager.currentFolder = null
                    }
                    imageFolderManager.removeFolder(selectedIdx)
                    if (imageFolderManager.getFolders().isEmpty()) {
                        showImageSettingsMenu()
                    } else {
                        showManageFoldersMenu()
                    }
                }
            } else if (selectionOverlay.visibility == View.VISIBLE && title.startsWith("WORKING FOLDERS")) {
                val idx = selectionOverlay.getSelectedIndex()
                val folders = workingFolderManager.getFolders()
                if (idx in folders.indices) {
                    workingFolderManager.removeFolder(idx)
                    showManageWorkingFoldersMenu()
                }
            }
        }
        Command.OverlayDeleteAll -> {
            if (selectionOverlay.visibility == View.VISIBLE && selectionOverlay.getTitle() == "BOOKMARKS") {
                bufferManager.getCurrentBuffer().clearBookmarks(); bufferManager.saveBuffers(); selectionOverlay.hide()
            }
        }
        Command.OpenImageViewer -> showImageFolderPicker()
        Command.OpenImageSettings -> showImageSettingsMenu()
        is Command.LoadNamedImage -> performLoadNamedImage(cmd.name)
        is Command.LoadNamedImageToBuffer -> performLoadNamedImageToBuffer(cmd.name, cmd.bufferIndex)
        is Command.ClearBufferAtIndex -> {
            if (cmd.index in 0 until bufferManager.getBufferCount()) {
                bufferManager.getBufferAt(cmd.index).clear()
                showStatusToast("Buffer ${cmd.index + 1} cleared")
                if (bufferManager.currentBufferIndex == cmd.index) {
                    suppressWatermark = true
                    editorView.suppressWatermark = true
                    editorView.invalidate()
                    updateStatus()
                }
            }
        }
        Command.ImageNext -> moveViewer(1)
        Command.ImagePrev -> moveViewer(-1)
        Command.ToggleSlideshow -> {
            if (viewerFiles.isEmpty() || !slideshowActive) {
                val f = imageFolderManager.currentFolder
                if (f != null) {
                    if (!f.isRemote) {
                        try {
                            val folder = DocumentFile.fromTreeUri(this, Uri.parse(f.path))
                            if (folder != null && folder.exists()) {
                                val files = folder.listFiles()
                                    .filter { it.isFile && (it.name?.lowercase()?.let { n -> n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".gif") } ?: false) }
                                    .map { it.uri.toString() }
                                
                                if (files.isNotEmpty()) {
                                    viewerFiles = files.toMutableList()
                                    viewerIndex = 0
                                    isViewerRemote = false
                                    showStatusToast("Loaded ${files.size} images from ${f.name}")
                                } else {
                                    showStatusToast("No images found in ${f.name}")
                                }
                            } else {
                                showStatusToast("Folder ${f.name} not found or inaccessible")
                            }
                        } catch (e: Exception) {
                            Log.e("Slideshow", "Error loading from current folder", e)
                            showStatusToast("Error accessing folder: ${e.message}")
                        }
                    } else {
                        isViewerRemote = true
                        showStatusToast("Connecting to Remote Folder: ${f.name}...")
                        syncManager.fetchRemoteFileList(f.path) { items ->
                            val images = items.filter { !it.isDir && (it.name.lowercase().endsWith(".jpg") || it.name.lowercase().endsWith(".jpeg") || it.name.lowercase().endsWith(".png") || it.name.lowercase().endsWith(".gif")) }
                            if (images.isNotEmpty()) {
                                viewerFiles = images.map { it.path }.toMutableList()
                                viewerIndex = 0
                                loadViewerImage()
                                toggleSlideshow()
                            } else {
                                showStatusToast("No images in remote folder ${f.name}")
                            }
                        }
                        return 
                    }
                } else {
                    showStatusToast("No Current Image Folder set. Use Ctrl-Alt-I.")
                }
            }
            
            if (viewerFiles.isNotEmpty()) {
                if (editorView.viewerImageUri == null) loadViewerImage()
                toggleSlideshow()
            }
        }
        
        Command.PdfNextPage -> {
            val current = editorView.getCurrentPdfPage()
            editorView.jumpToPdfPage(current + 1)
            updateStatus()
        }
        Command.PdfPrevPage -> {
            val current = editorView.getCurrentPdfPage()
            editorView.jumpToPdfPage(current - 1)
            updateStatus()
        }
        Command.PdfFirstPage -> {
            editorView.jumpToPdfPage(0)
            updateStatus()
        }
        Command.PdfLastPage -> {
            editorView.jumpToPdfPage(editorView.getPdfPageCount() - 1)
            updateStatus()
        }
        is Command.PdfGotoPage -> {
            editorView.jumpToPdfPage(cmd.pageIndex)
            updateStatus()
        }
        Command.OpenPdfGotoMenu -> {
            val maxPages = editorView.getPdfPageCount()
            inputOverlay.show("Go to Page (1-$maxPages):", "", { input ->
                val page = input.toIntOrNull()
                if (page != null && page in 1..maxPages) {
                    commandDispatcher.dispatch(Command.PdfGotoPage(page - 1))
                } else {
                    showStatusToast("Invalid page number")
                }
            }, { })
        }
        Command.PdfReset -> {
            editorView.pdfReset()
            updateStatus()
            showStatusToast("PDF View Reset")
        }
        Command.AddPdfFavorite -> showPdfFavoriteAddInput()
        is Command.AddPdfFavoriteNamed -> performPdfFavoriteAdd(cmd.identifier)
        Command.OpenPdfFavoritePicker -> showPdfFavoritePicker()
        is Command.SelectPdfFavorite -> performPdfFavoriteSelect(cmd.index)
        Command.ExportPdfFavorites -> createPdfFavoritesLauncher.launch("pdf_favorites.txt")
        Command.OpenPatternCollapseInput -> showPatternCollapseInput()
        is Command.AddCollapsePattern -> performAddCollapsePattern(cmd.pattern)
        Command.OpenPatternCollapsePicker -> showPatternCollapsePicker()
        is Command.ToggleCollapsePattern -> { }
        is Command.DeleteCollapsePattern -> performDeleteCollapsePattern(cmd.index)
        is Command.RenameCollapsePattern -> performRenameCollapsePattern(cmd.index, cmd.newPattern)
        Command.UncollapseAll -> performUncollapseAll()
        Command.RecollapseActivePatterns -> performRecollapseAll()
        Command.CollapseAllParagraphs -> performCollapseAllParagraphs()
        Command.UnwrapParagraphs -> performUnwrapParagraphs()
        Command.OpenManuscriptMenu -> {
            val currentBuf = bufferManager.getCurrentBuffer()
            val workingFolderUri = workingFolderManager.currentFolder?.path ?: workingFolderManager.lastUsedFolderUri
            val hasContent = if (currentBuf.id == 17) {
                ManuscriptEngine(this).getScratchLogEntries(currentBuf, workingFolderUri).isNotEmpty()
            } else {
                !currentBuf.getCleanText().isEmpty()
            }
            if (!hasContent) {
                Toast.makeText(this, "No content to paginate", Toast.LENGTH_SHORT).show()
            } else {
                val options = listOf("1. Layout View (4 or 8-page grid)", "2. Filmstrip View (Single page)", "Cancel")
                selectionOverlay.show("Manuscript Viewer", options, 0, {
                    when(it) {
                        0 -> manuscriptLayoutOverlay.show(bufferManager.getCurrentBuffer(), workingFolderUri)
                        1 -> manuscriptFilmstripOverlay.show(bufferManager.getCurrentBuffer(), workingFolderUri)
                    }
                }, {})
            }
        }
        Command.OpenManuscriptFilmstrip -> {
            val currentBuf = bufferManager.getCurrentBuffer()
            val workingFolderUri = workingFolderManager.currentFolder?.path ?: workingFolderManager.lastUsedFolderUri
            val hasContent = if (currentBuf.id == 17) {
                ManuscriptEngine(this).getScratchLogEntries(currentBuf, workingFolderUri).isNotEmpty()
            } else {
                !currentBuf.getCleanText().isEmpty()
            }
            if (!hasContent) {
                showStatusToast("No content to paginate")
            } else {
                manuscriptFilmstripOverlay.show(bufferManager.getCurrentBuffer(), workingFolderUri)
            }
        }
        Command.OpenManuscriptLayout -> {
            val currentBuf = bufferManager.getCurrentBuffer()
            val workingFolderUri = workingFolderManager.currentFolder?.path ?: workingFolderManager.lastUsedFolderUri
            val hasContent = if (currentBuf.id == 17) {
                ManuscriptEngine(this).getScratchLogEntries(currentBuf, workingFolderUri).isNotEmpty()
            } else {
                !currentBuf.getCleanText().isEmpty()
            }
            if (!hasContent) {
                showStatusToast("No content to paginate")
            } else {
                manuscriptLayoutOverlay.show(bufferManager.getCurrentBuffer(), workingFolderUri)
            }
        }
        Command.OpenManuscriptSettings -> showManuscriptSettingsMenu()
        Command.OpenWorkspaceLayout -> workspaceLayoutOverlay.show(bufferManager)
        Command.OpenExamplesMenu -> showExamplesMenu()
        Command.OpenPdfSearch -> {
            showStatusToast("Find does not work on scanned PDFs")
        }
        Command.OpenPdfFavorites -> {
            showStatusToast("Favorites: Coming soon")
        }

        Command.OverlayCancel -> {
            slideshowActive = false
            slideshowHandler.removeCallbacks(slideshowRunnable)
            selectionOverlay.hide(); multiSelectionOverlay.hide(); inputOverlay.hide(); helpOverlay.hide(); searchOverlay.hide(); macroOverlay.hide(); outlineOverlay.hide(); referenceOverlay.hide(); aboutOverlay.hide(); workspaceLayoutOverlay.hide(); scriptConsoleOverlay.hide(); editorView.clearViewerImage(); fileBrowserOverlay.hide()
            
            val dismissCallback = onReferenceDismiss
            onReferenceDismiss = null
            dismissCallback?.invoke()
        }
        Command.OverlayToggleMarginPreview -> {
            if (selectionOverlay.visibility == View.VISIBLE) selectionOverlay.togglePreviewMargins()
        }
        Command.OpenMacroMenu -> {
            if (macroManager.activeContext == "TEXT") autoSwitchMacroContext()
            
            val onSubmit: (String) -> Unit = object : (String) -> Unit {
                override fun invoke(text: String) {
                    if (text.isNotEmpty()) {
                        macroManager.addMacro(text)
                        bufferManager.getCurrentBuffer().insert(text)
                        editorView.invalidate()
                        editorView.scrollToCursorTop()
                        updateStatus()
                        showStatusToast("Macro Added & Pasted")
                    }
                    macroOverlay.show(macroManager.getMacros(), macroManager.activeContext, true, this, { })
                }
            }
            macroOverlay.show(macroManager.getMacros(), macroManager.activeContext, true, onSubmit, { })
        }
        Command.FastInsertMacro -> {
            if (macroManager.activeContext == "TEXT") autoSwitchMacroContext()
            
            val onSubmit: (String) -> Unit = object : (String) -> Unit {
                override fun invoke(text: String) {
                    if (text.isNotEmpty()) {
                        macroManager.moveToTop(text)
                        bufferManager.getCurrentBuffer().insert(text)
                        editorView.invalidate()
                        editorView.scrollToCursorTop()
                        updateStatus()
                        showStatusToast("Pasted: $text")
                    }
                    macroOverlay.show(macroManager.getMacros(), macroManager.activeContext, false, this, { })
                }
            }
            macroOverlay.show(macroManager.getMacros(), macroManager.activeContext, false, onSubmit, { })
        }
        Command.PasteLastMacro -> {
            val last = macroManager.lastMacroUsed
            if (last != null) {
                bufferManager.getCurrentBuffer().insert(last)
                editorView.invalidate()
                editorView.scrollToCursor()
                updateStatus()
                Toast.makeText(this, "Pasted Last Macro", Toast.LENGTH_SHORT).show()
            } else {
                showStatusToast("No macro used yet")
            }
        }
        Command.MacroEdit -> {
            inputOverlay.show("Define Quick Macro:", macroText, { newText ->
                macroText = newText.take(80)
                showStatusToast("Quick Macro updated")
            }, { })
        }
        Command.MacroInsert -> {
            if (macroText.isNotEmpty()) {
                bufferManager.getCurrentBuffer().insert(macroText)
                editorView.invalidate()
                updateStatus()
            } else {
                Toast.makeText(this, "Quick Macro empty. Ctrl + >> to edit.", Toast.LENGTH_SHORT).show()
            }
        }
        Command.OpenMacroContextPicker -> showMacroContextPicker()
        Command.AddMacroContext -> showMacroAddContextInput()
        Command.RunScript -> {
            val buffer = bufferManager.getCurrentBuffer()
            val text = buffer.getCleanText()
            if (text.isNotEmpty()) {
                val uri = buffer.fileUri
                commandScriptManager.executeScript(text, uri)
            } else {
                showStatusToast("Buffer is empty")
            }
        }
        Command.StopScript -> {
            commandScriptManager.stopScript()
            showStatusToast("Script Stopped")
        }
        Command.ScriptStep -> {
            commandScriptManager.step()
        }
        Command.Beep -> {
            try {
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200)
            } catch (e: Exception) {}
        }
        Command.ToggleHelpTaskSwitch -> {
            if (isHelpTaskSwitchEnabled) {
                if (helpOverlay.visibility == View.VISIBLE) {
                    helpOverlay.hide()
                } else {
                    if (helpOverlay.pagesCount() == 0) {
                        showHelpPopup()
                    } else {
                        helpOverlay.visibility = View.VISIBLE
                    }
                }
            } else {
                showHelpPopup()
            }
        }
        Command.ToggleStatus -> {
            isStatusVisible = !isStatusVisible
            statusText.visibility = if (isStatusVisible) View.VISIBLE else View.GONE
            saveStatusVisibility()
            val state = if (isStatusVisible) "ON" else "OFF"
            showStatusToast("Status Bar: $state")
        }
        Command.ToggleTodoWorkspace -> {
            bufferManager.toggleTodoWorkspace()
            val mode = if (bufferManager.isTodoWorkspace) "TO-DO MODE" else "MAIN MODE"
            showStatusToast(mode)
            editorView.setBuffer(bufferManager.getCurrentBuffer())
            applyTheme()
            updateStatus()
        }
        is Command.InputChar -> { 
            if (inputOverlay.visibility == View.VISIBLE) inputOverlay.handleKey(cmd.char) 
            else if (searchOverlay.visibility == View.VISIBLE) searchOverlay.handleKey(cmd.char)
            else if (macroOverlay.visibility == View.VISIBLE) macroOverlay.handleKey(cmd.char)
            else if (scriptConsoleOverlay.visibility == View.VISIBLE) scriptConsoleOverlay.handleKey(cmd.char)
        }
        Command.InputDelete -> { 
            if (inputOverlay.visibility == View.VISIBLE) inputOverlay.deleteChar() 
            else if (searchOverlay.visibility == View.VISIBLE) searchOverlay.deleteChar()
            else if (macroOverlay.visibility == View.VISIBLE) macroOverlay.deleteChar()
            else if (scriptConsoleOverlay.visibility == View.VISIBLE) scriptConsoleOverlay.deleteChar()
        }
        
        Command.OpenScratchpad -> {
            bufferManager.saveBuffers()
            if (bufferManager.switchBuffer(8)) {
                val buffer = bufferManager.getCurrentBuffer()
                editorView.setBuffer(buffer)
                applyTheme()
                updateStatus()
                showStatusToast("Scratchpad Active")
            }
        }
        Command.FlushScratchpadSave -> {
            val workingFolder = workingFolderManager.currentFolder
            if (workingFolder == null && workingFolderManager.getFolders().isEmpty()) {
                selectionOverlay.show("No Working Folder set for Scratchpad Log", listOf("Link Working Folder Now", "Cancel"), 0, { sel ->
                    if (sel == 0) {
                        linkWorkingFolderLauncher.launch(null)
                    }
                }, {})
            } else {
                val folderUri = workingFolder?.path
                if (bufferManager.flushScratchpad(true, folderUri)) {
                    val folderName = workingFolder?.name ?: "Working Folder"
                    showStatusToast("Scratchpad saved to $folderName/${bufferManager.scratchLogFilename}")
                    editorView.invalidate()
                    editorView.scrollToCursor()
                    updateStatus()
                } else {
                    showStatusToast("Error saving Scratchpad log")
                }
            }
        }
        Command.FlushScratchpadNoSave -> {
            selectionOverlay.show("Discard Scratchpad?", listOf("No", "Yes"), 1, {
                if (it == 1) {
                    bufferManager.flushScratchpad(false)
                    editorView.invalidate()
                    editorView.scrollToCursor()
                    updateStatus()
                }
            }, {})
        }
        Command.OpenScratchpadSettings -> showScratchpadSettingsMenu()
        
        is Command.SwitchBuffer -> { }
        Command.ClearBufferNoWatermark -> {
            suppressWatermark = true
            editorView.suppressWatermark = true
            editorView.invalidate()
            updateStatus()
            showStatusToast("Buffer cleared (No watermark)")
        }
        Command.HideCursor -> {
            editorView.isCursorVisible = false
            leftEditorView.isCursorVisible = false
            rightEditorView.isCursorVisible = false
        }
        Command.ShowCursor -> {
            editorView.isCursorVisible = true
            leftEditorView.isCursorVisible = true
            rightEditorView.isCursorVisible = true
        }
        Command.HideEmptyBufferMsg -> {
            editorView.isWatermarkGlobalHidden = true
            leftEditorView.isWatermarkGlobalHidden = true
            rightEditorView.isWatermarkGlobalHidden = true
        }
        Command.ShowEmptyBufferMsg -> {
            editorView.isWatermarkGlobalHidden = false
            leftEditorView.isWatermarkGlobalHidden = false
            rightEditorView.isWatermarkGlobalHidden = false
        }
        else -> {}
    }
}

internal fun MainActivity.performPdfFavoriteAdd(identifier: String) {
    val buffer = bufferManager.getCurrentBuffer()
    val page = editorView.getCurrentPdfPage()
    buffer.pdfFavorites.add(com.example.doswriter.model.EditorBuffer.PdfFavorite(page, identifier))
    bufferManager.saveBuffers()
    showStatusToast("Favorite added: $identifier")
}

internal fun MainActivity.performPdfFavoriteSelect(index: Int) {
    val buffer = bufferManager.getCurrentBuffer()
    if (index in buffer.pdfFavorites.indices) {
        val favorite = buffer.pdfFavorites[index]
        editorView.jumpToPdfPage(favorite.pageIndex)
        updateStatus()
        showStatusToast("Jumped to ${favorite.identifier}")
    }
}

internal fun MainActivity.performAddCollapsePattern(pattern: String) {
    val buffer = bufferManager.getCurrentBuffer()
    if (!buffer.collapsePatterns.contains(pattern)) {
        buffer.collapsePatterns.add(pattern)
        bufferManager.saveBuffers()
        if (buffer.getLength() > bufferManager.patternCollapseLimit) {
            Toast.makeText(this, "Buffer too large for auto-collapse", Toast.LENGTH_SHORT).show()
        } else {
            applyCollapseToBuffer(pattern)
        }
        showStatusToast("Pattern added")
    } else {
        showStatusToast("Pattern already exists")
    }
}

internal fun MainActivity.performDeleteCollapsePattern(index: Int) {
    val buffer = bufferManager.getCurrentBuffer()
    if (index in buffer.collapsePatterns.indices) {
        buffer.collapsePatterns.removeAt(index)
        bufferManager.saveBuffers()
        showStatusToast("Pattern deleted")
        showPatternCollapsePicker()
    }
}

internal fun MainActivity.performRenameCollapsePattern(index: Int, newPattern: String) {
    val buffer = bufferManager.getCurrentBuffer()
    if (index in buffer.collapsePatterns.indices) {
        buffer.collapsePatterns[index] = newPattern
        bufferManager.saveBuffers()
        showStatusToast("Pattern renamed")
        showPatternCollapsePicker()
    }
}

internal fun MainActivity.performCycleLineHeight() {
    val buffer = bufferManager.getCurrentBuffer()
    val values = listOf(1.0f, 1.2f, 1.5f, 2.0f)
    val nextIdx = (values.indexOf(buffer.lineSpacing) + 1) % values.size
    editorView.setLineSpacing(values[nextIdx])
    if (isSettingsGlobal) bufferManager.applyFontToAll()
    bufferManager.saveBuffers()
    showStatusToast("Line Spacing: ${values[nextIdx]}")
}

internal fun MainActivity.performLoadNamedImage(name: String) {
    // 1. Check if name is already an absolute path or URI and try it first
    if (name.startsWith("/") || name.startsWith("content://") || name.startsWith("file://")) {
        val uriStr = if (name.startsWith("/")) Uri.fromFile(java.io.File(name)).toString() else name
        val file = if (name.startsWith("/")) java.io.File(name) else null
        
        if (file == null || (file.exists() && file.canRead())) {
            viewerFiles = mutableListOf(uriStr)
            viewerIndex = 0
            isViewerRemote = false
            loadViewerImage()
            return
        }
    }

    val fileNameOnly = name.substringAfterLast("/")

    // 2. Try Scripting Image Folder first if set
    bufferManager.scriptImageFolderUri?.let { folderUri ->
        try {
            val root = DocumentFile.fromTreeUri(this, Uri.parse(folderUri))
            val match = root?.listFiles()?.find { 
                it.name?.equals(fileNameOnly, ignoreCase = true) == true || 
                it.name?.equals(name, ignoreCase = true) == true 
            }
            if (match != null) {
                viewerFiles = mutableListOf(match.uri.toString())
                viewerIndex = 0
                isViewerRemote = false
                loadViewerImage()
                return
            }
        } catch (e: Exception) {}
    }

    // 3. Fallback to Current Image Folder
    val folder = imageFolderManager.currentFolder
    if (folder == null) {
        if (!commandScriptManager.isScriptRunning()) {
            showStatusToast("No Image Folder set for '$name'")
        }
        return
    }

    if (folder.isRemote) {
        syncManager.fetchRemoteFileList(folder.path) { items ->
            val match = items.find { 
                it.name.equals(fileNameOnly, ignoreCase = true) || 
                it.name.equals(name, ignoreCase = true) 
            }
            if (match != null) {
                viewerFiles = mutableListOf(match.path)
                viewerIndex = 0
                isViewerRemote = true
                loadViewerImage()
            } else {
                if (!commandScriptManager.isScriptRunning()) {
                    showStatusToast("Image '$name' not found in remote folder")
                }
            }
        }
    } else {
        val root = DocumentFile.fromTreeUri(this, Uri.parse(folder.path))
        val match = root?.listFiles()?.find { 
            it.name?.equals(fileNameOnly, ignoreCase = true) == true || 
            it.name?.equals(name, ignoreCase = true) == true 
        }
        if (match != null) {
            viewerFiles = mutableListOf(match.uri.toString())
            viewerIndex = 0
            isViewerRemote = false
            loadViewerImage()
        } else {
            if (!commandScriptManager.isScriptRunning()) {
                showStatusToast("Image '$name' not found")
            }
        }
    }
}

internal fun MainActivity.performLoadNamedImageToBuffer(name: String, bufferIndex: Int) {
    val buffer = bufferManager.getBufferAt(bufferIndex)

    // 1. Check if name is already an absolute path or URI and try it first
    if (name.startsWith("/") || name.startsWith("content://") || name.startsWith("file://")) {
        val uriStr = if (name.startsWith("/")) Uri.fromFile(java.io.File(name)).toString() else name
        val file = if (name.startsWith("/")) java.io.File(name) else null
        
        if (file == null || (file.exists() && file.canRead())) {
            buffer.imageUri = uriStr
            buffer.fileName = name.substringAfterLast("/")
            bufferManager.saveBuffers()
            if (bufferManager.currentBufferIndex == bufferIndex) {
                editorView.setBuffer(buffer)
            }
            if (!commandScriptManager.isScriptRunning()) {
                showStatusToast("Set B${bufferIndex + 1} background to image")
            }
            return
        }
    }

    val fileNameOnly = name.substringAfterLast("/")

    // 2. Try Scripting Image Folder first if set
    bufferManager.scriptImageFolderUri?.let { folderUri ->
        try {
            val root = DocumentFile.fromTreeUri(this, Uri.parse(folderUri))
            val match = root?.listFiles()?.find { 
                it.name?.equals(fileNameOnly, ignoreCase = true) == true || 
                it.name?.equals(name, ignoreCase = true) == true 
            }
            if (match != null) {
                buffer.imageUri = match.uri.toString()
                buffer.fileName = match.name
                bufferManager.saveBuffers()
                if (bufferManager.currentBufferIndex == bufferIndex) {
                    editorView.setBuffer(buffer)
                }
                if (!commandScriptManager.isScriptRunning()) {
                    showStatusToast("Set B${bufferIndex + 1} background to ${match.name}")
                }
                return
            }
        } catch (e: Exception) {}
    }

    val folder = imageFolderManager.currentFolder
    if (folder == null) {
        if (!commandScriptManager.isScriptRunning()) {
            showStatusToast("No Image Folder set for '$name'")
        }
        return
    }
    
    if (folder.isRemote) {
        showStatusToast("Remote images cannot be set as buffer background yet")
    } else {
        val root = DocumentFile.fromTreeUri(this, Uri.parse(folder.path))
        val match = root?.listFiles()?.find { 
            it.name?.equals(fileNameOnly, ignoreCase = true) == true || 
            it.name?.equals(name, ignoreCase = true) == true 
        }
        if (match != null) {
            buffer.imageUri = match.uri.toString()
            buffer.fileName = match.name
            bufferManager.saveBuffers()
            if (bufferManager.currentBufferIndex == bufferIndex) {
                editorView.setBuffer(buffer)
            }
            if (!commandScriptManager.isScriptRunning()) {
                showStatusToast("Set B${bufferIndex + 1} background to ${match.name}")
            }
        } else {
            if (!commandScriptManager.isScriptRunning()) {
                showStatusToast("Image '$name' not found")
            }
        }
    }
}

internal fun MainActivity.handleCloseAll() {
    selectionOverlay.show("Clear ALL buffers?", listOf("No", "Yes"), 0, {
        if (it == 1) {
            for (b in bufferManager.getAllBuffers()) {
                b.clear()
            }
            bufferManager.saveBuffers()
            suppressWatermark = false
            editorView.suppressWatermark = false
            editorView.invalidate()
            updateStatus()
            showStatusToast("All buffers cleared")
        }
    }, {})
}
