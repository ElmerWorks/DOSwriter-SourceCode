package com.example.doswriter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle
import android.view.View
import android.widget.Toast
import com.example.doswriter.MainActivity
import com.example.doswriter.logic.*
import com.example.doswriter.editor.*
import com.example.doswriter.model.EditorBuffer
import java.io.File
import kotlin.math.max
import kotlin.math.min

internal fun MainActivity.preprocessQuery(q: String): String {
    return q.replace("\r\n", "\n").replace("\r", "\n")
}

internal fun MainActivity.handleClipboardCommand(cmd: Command) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val buffer = bufferManager.getCurrentBuffer()
    when (cmd) {
        Command.Copy, Command.Cut -> {
            if (searchOverlay.visibility != View.VISIBLE && buffer.hasSelection()) {
                var text = buffer.getSelectedText()
                if (buffer.lineEndingSymbol != 0) {
                    val symbol = when (buffer.lineEndingSymbol) {
                        1 -> "\u21B5"
                        2 -> "\u00B6"
                        3 -> "\u23CE"
                        else -> ""
                    }
                    if (symbol.isNotEmpty()) text = text.replace("\r\n", symbol)
                }
                bufferManager.internalClipboard = text
                isInternalCopyLast = true
                
                if (!isSystemClipboardSuppressed) {
                    val clip = ClipData.newPlainText("DOSwriter", text)
                    if (android.os.Build.VERSION.SDK_INT >= 24) {
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                        }
                    }
                    clipboard.setPrimaryClip(clip)
                } else {
                    showStatusToast("Copied to internal buffer")
                }

                if (cmd == Command.Cut) {
                    buffer.deleteSelection()
                    editorView.invalidate()
                    updateStatus()
                }
            }
        }
        Command.Paste -> {
            val clip = try { clipboard.primaryClip } catch (e: Exception) { null }
            val systemText = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
            
            val text = if (isInternalCopyLast && bufferManager.internalClipboard != null) {
                bufferManager.internalClipboard
            } else {
                systemText ?: bufferManager.internalClipboard
            }

            if (text != null) {
                if (searchOverlay.visibility == View.VISIBLE) {
                    searchOverlay.paste(text)
                } else if (inputOverlay.visibility == View.VISIBLE) {
                    inputOverlay.paste(text)
                } else if (macroOverlay.visibility == View.VISIBLE) {
                    macroOverlay.paste(text)
                } else {
                    val sanitized = preprocessQuery(text)
                    buffer.insert(sanitized)
                    editorView.invalidate()
                    updateStatus()
                }
            }
        }
        else -> {}
    }
}

internal fun MainActivity.performUncollapseAll() {
    val buffer = bufferManager.getCurrentBuffer()
    buffer.clearFolds()
    editorView.invalidate()
    showStatusToast("All text uncollapsed")
}

internal fun MainActivity.performRecollapseAll() {
    val buffer = bufferManager.getCurrentBuffer()
    buffer.clearFolds()
    buffer.collapsePatterns.forEach { applyCollapseToBuffer(it) }
    showStatusToast("Recollapsed on all patterns")
}

internal fun MainActivity.performCollapseAllParagraphs() {
    val buffer = bufferManager.getCurrentBuffer()
    val text = buffer.getCleanText()
    if (text.isEmpty()) return

    if (buffer.getLength() > bufferManager.patternCollapseLimit) {
        Toast.makeText(this, "Buffer too large for bulk collapse (Max ${bufferManager.patternCollapseLimit / 1000}KB).", Toast.LENGTH_LONG).show()
        return
    }

    buffer.clearFolds()
    val rawText = buffer.getText()
    val newFolds = mutableListOf<EditorBuffer.Fold>()
    
    var pos = buffer.prefix.length
    while (pos < rawText.length) {
        var lineEnd = rawText.indexOf('\n', pos)
        if (lineEnd == -1) lineEnd = rawText.length
        
        val headerLimit = pos + 40
        val foldStart = if (lineEnd <= headerLimit) lineEnd + 1 else headerLimit
        
        var nextPara = rawText.indexOf("\n\n", foldStart)
        if (nextPara == -1) nextPara = rawText.length
        
        val foldEnd = nextPara - 1
        if (foldEnd > foldStart) {
            newFolds.add(EditorBuffer.Fold(foldStart, foldEnd))
        }
        
        pos = nextPara + 2
    }

    if (newFolds.isNotEmpty()) {
        buffer.addFolds(newFolds)
    }
    editorView.invalidate()
    showStatusToast("All paragraphs collapsed")
}

internal fun MainActivity.performUnwrapParagraphs() {
    val buffer = bufferManager.getCurrentBuffer()
    val input = buffer.getCleanText()
    if (input.isEmpty()) return

    val lines = input.lines()
    val result = StringBuilder()
    var lastLineWasBlank = true

    for (line in lines) {
        val trimmed = line.trimEnd()
        if (trimmed.isEmpty()) {
            if (!lastLineWasBlank) {
                result.append("\n\n") 
            }
            lastLineWasBlank = true
        } else {
            if (!lastLineWasBlank) {
                result.append(" ") 
            }
            result.append(trimmed)
            lastLineWasBlank = false
        }
    }

    buffer.setText(result.toString().trim())
    editorView.setBuffer(buffer)
    updateStatus()
    showStatusToast("Paragraphs unwrapped")
}

internal fun MainActivity.applyCollapseToBuffer(pattern: String) {
    val buffer = bufferManager.getCurrentBuffer()
    val text = buffer.getText()
    val len = text.length
    
    var searchIndex = buffer.prefix.length
    val newFolds = mutableListOf<EditorBuffer.Fold>()

    while (true) {
        val hit = text.indexOf(pattern, searchIndex, ignoreCase = true)
        if (hit == -1) break
        
        val lineEnd = text.indexOf('\n', hit)
        val headerLimit = hit + pattern.length + 40
        
        val foldStart = if (lineEnd != -1 && lineEnd <= headerLimit) {
            lineEnd + 1
        } else {
            min(headerLimit, len)
        }
        
        val nextHit = text.indexOf(pattern, hit + 1, ignoreCase = true)
        val nextPatternPos = if (nextHit != -1) nextHit else len
        
        var foldEnd = nextPatternPos
        if (nextHit != -1) {
            var p = nextHit
            while (p > foldStart && text[p - 1] != '\n') p--
            foldEnd = max(foldStart, p - 1)
        }
        
        if (foldEnd > foldStart) {
            newFolds.add(EditorBuffer.Fold(foldStart, foldEnd))
        }
        
        searchIndex = if (nextHit != -1) nextHit else len
        if (searchIndex <= hit) searchIndex = hit + 1
        if (searchIndex >= len) break
    }
    
    if (newFolds.isNotEmpty()) {
        buffer.addFolds(newFolds)
    }
    editorView.invalidate()
}

internal fun MainActivity.loadExampleFile(fileName: String) {
    try {
        val path = "examples/$fileName"
        val buffer = bufferManager.getCurrentBuffer()
        val lower = fileName.lowercase()
        
        buffer.reset()
        
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif")) {
            val simpleName = fileName.replace("/", "_") // Avoid cache collisions
            val file = File(cacheDir, simpleName)
            assets.open(path).use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            
            if (fileName.contains("thumbnails/")) {
                buffer.thumbnailUri = Uri.fromFile(file).toString()
                buffer.useThumbnail = true
                buffer.thumbnailPreference = 1
                showStatusToast("Thumbnail: $simpleName")
            } else {
                buffer.imageUri = Uri.fromFile(file).toString()
                buffer.fileName = simpleName
            }
        } else if (lower.endsWith(".pdf")) {
            val simpleName = fileName.replace("/", "_")
            val file = File(cacheDir, simpleName)
            assets.open(path).use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            buffer.pdfUri = Uri.fromFile(file).toString()
            buffer.fileName = simpleName
        } else {
            val text = assets.open(path).bufferedReader().use { it.readText() }
            buffer.setText(text)
            buffer.fileName = fileName.substringAfterLast("/")
        }
        
        buffer.markClean()
        bufferManager.saveBuffers()
        editorView.setBuffer(buffer)
        applyTheme()
        updateStatus()
        showStatusToast("Loaded: ${fileName.substringAfterLast("/")}")
        
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(this, "Failed to load example: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

internal fun MainActivity.getCurrentSyllable(): String {
    val buffer = bufferManager.getCurrentBuffer()
    val text = buffer.getText()
    val pos = buffer.cursorPosition
    if (pos <= buffer.prefix.length) return ""
    
    var start = pos - 1
    while (start >= buffer.prefix.length) {
        val c = text[start]
        if (c.isWhitespace() || ".,!?;:()[]{}<>\"'/\\".contains(c)) {
            break
        }
        start--
    }
    return text.substring(start + 1, pos)
}

internal fun MainActivity.processTelexText(text: String): String {
    if (keyboardMode != TelexProcessor.KeyboardMode.VIETNAMESE_TELEX) return text
    
    val result = StringBuilder()
    var currentSyllable = ""
    
    for (char in text) {
        val telexResult = telexProcessor.process(char, currentSyllable)
        if (telexResult.handled) {
            if (telexResult.backspaceCount > 0) {
                val newLength = result.length - telexResult.backspaceCount
                result.setLength(max(0, newLength))
            }
            result.append(telexResult.output)
            if (char.isWhitespace()) {
                currentSyllable = ""
            } else {
                currentSyllable = telexResult.output 
            }
        } else {
            result.append(char)
            if (char.isWhitespace()) currentSyllable = "" else currentSyllable += char
        }
    }
    return result.toString()
}

internal fun MainActivity.dispatchInsertText(text: String) {
    val isOverlayActive = selectionOverlay.visibility == View.VISIBLE ||
                inputOverlay.visibility == View.VISIBLE ||
                searchOverlay.visibility == View.VISIBLE ||
                macroOverlay.visibility == View.VISIBLE ||
                multiSelectionOverlay.visibility == View.VISIBLE ||
                outlineOverlay.visibility == View.VISIBLE ||
                referenceOverlay.visibility == View.VISIBLE ||
                aboutOverlay.visibility == View.VISIBLE ||
                manuscriptFilmstripOverlay.visibility == View.VISIBLE ||
                manuscriptLayoutOverlay.visibility == View.VISIBLE ||
                workspaceLayoutOverlay.visibility == View.VISIBLE

    if (keyboardMode == TelexProcessor.KeyboardMode.VIETNAMESE_TELEX && text.length == 1 && !isOverlayActive) {
        val char = text[0]
        val syllable = getCurrentSyllable()
        val result = telexProcessor.process(char, syllable)
        if (result.handled) {
            repeat(result.backspaceCount) {
                commandDispatcher.dispatch(Command.DeleteBackward)
            }
            commandDispatcher.dispatch(Command.InsertText(result.output))
            return
        }
    }
    
    val buffer = bufferManager.getCurrentBuffer()
    if (text == "\n" && buffer.isAtFold()) {
        buffer.uncollapseAtCursor()
        updateStatus()
        editorView.invalidate()
    } else {
        commandDispatcher.dispatch(Command.InsertText(text))
    }
}
