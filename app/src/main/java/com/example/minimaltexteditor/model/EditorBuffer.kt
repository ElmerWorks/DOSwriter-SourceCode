package com.example.minimaltexteditor.model

import java.util.Stack
import kotlin.math.max
import kotlin.math.min

class EditorBuffer(val id: Int) {
    enum class CursorStyle {
        THIN_CARET, THICK_CARET, BLOCK, UNDERLINE, INVERTED, RETRO_BLOCK, DIM_CARET
    }

    data class CursorSettings(
        var style: CursorStyle = CursorStyle.THIN_CARET,
        var blinkEnabled: Boolean = true,
        var blinkIntervalMs: Long = 500L
    )

    val cursorSettings = CursorSettings()

    private val content = StringBuilder()
    var cursorPosition: Int = 0
        private set
    var selectionAnchor: Int = 0
        private set

    var fileName: String? = null
    var fileUri: String? = null
    var imageUri: String? = null
    var imageScale: Float = 1f
    var imagePanX: Float = 0f
    var imagePanY: Float = 0f
    var pdfUri: String? = null
    var sideMargin: Float = 60f
    var topBottomMargin: Float = 60f
    var verticalOffset: Float = 0f
    var fontSize: Float = 48f
    var lineSpacing: Float = 1.2f
    var fontType: String = "MONOSPACE"
    var dayThemeIndex: Int = 3
    var nightThemeIndex: Int = 3 
    var charLimit: Int = 0 
    var isDirty: Boolean = false
    var showMargins: Boolean = false
    var isMarkdownMode: Boolean = false
    var useVirtualInput: Boolean = false
    var syncFilePath: String? = null
    val bookmarks = mutableListOf<Int>()
    
    val prefix = "\n\n"

    data class Fold(var start: Int, var end: Int)
    val folds = mutableListOf<Fold>()

    private data class HistoryState(val text: String, val cursor: Int, val anchor: Int)
    private val undoStack = Stack<HistoryState>()
    private val redoStack = Stack<HistoryState>()
    private val MAX_HISTORY = 100

    init {
        cursorPosition = prefix.length
        selectionAnchor = prefix.length
    }

    private fun pushUndo() {
        isDirty = true
        if (undoStack.size >= MAX_HISTORY) undoStack.removeAt(0)
        undoStack.push(HistoryState(content.toString(), cursorPosition, selectionAnchor))
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.push(HistoryState(content.toString(), cursorPosition, selectionAnchor))
        val state = undoStack.pop()
        applyState(state)
        folds.clear()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.push(HistoryState(content.toString(), cursorPosition, selectionAnchor))
        val state = redoStack.pop()
        applyState(state)
        folds.clear()
    }

    private fun applyState(state: HistoryState) {
        content.setLength(0)
        content.append(state.text)
        cursorPosition = state.cursor
        selectionAnchor = state.anchor
    }

    fun getText(): String = prefix + content.toString()
    fun getCleanText(): String = content.toString()
    fun hasSelection(): Boolean = selectionAnchor != cursorPosition

    fun getSelectionRange(): IntRange {
        val start = min(selectionAnchor, cursorPosition)
        val end = max(selectionAnchor, cursorPosition)
        return start until end
    }

    fun getSelectedText(): String {
        val range = getSelectionRange()
        if (range.isEmpty()) return ""
        return getText().substring(range.first, range.last + 1)
    }

    fun deleteSelection() {
        if (!hasSelection()) return
        pushUndo()
        val range = getSelectionRange()
        val safeStart = max(prefix.length, range.first)
        val safeEnd = max(prefix.length, range.last + 1)
        if (safeStart < safeEnd) {
            val contentStart = safeStart - prefix.length
            val contentEnd = safeEnd - prefix.length
            updateMarkersOnEdit(safeStart, -(contentEnd - contentStart))
            content.delete(contentStart, contentEnd)
        }
        cursorPosition = safeStart
        selectionAnchor = cursorPosition
    }

    fun insert(text: String) {
        if (charLimit > 0 && content.length + text.length > charLimit) return
        pushUndo()
        if (hasSelection()) deleteSelection()
        val insertPos = max(0, cursorPosition - prefix.length)
        content.insert(insertPos, text)
        updateMarkersOnEdit(cursorPosition, text.length)
        cursorPosition += text.length
        selectionAnchor = cursorPosition
    }

    private fun updateMarkersOnEdit(at: Int, delta: Int) {
        val toRemove = mutableListOf<Int>()
        for (i in bookmarks.indices) {
            if (bookmarks[i] >= at) {
                bookmarks[i] += delta
                if (bookmarks[i] < prefix.length) toRemove.add(bookmarks[i])
            }
        }
        bookmarks.removeAll(toRemove)

        val foldsToRemove = mutableListOf<Fold>()
        folds.forEach { fold ->
            if (fold.start >= at) { fold.start += delta; fold.end += delta }
            else if (fold.end >= at) { fold.end += delta }
            if (fold.start >= fold.end || fold.start < prefix.length) foldsToRemove.add(fold)
        }
        folds.removeAll(foldsToRemove)
    }

    fun deleteBackward() {
        if (hasSelection()) deleteSelection()
        else if (cursorPosition > prefix.length) {
            pushUndo()
            updateMarkersOnEdit(cursorPosition - 1, -1)
            content.deleteCharAt(cursorPosition - 1 - prefix.length)
            cursorPosition--
            selectionAnchor = cursorPosition
        }
    }

    fun deleteForward() {
        if (hasSelection()) deleteSelection()
        else if (cursorPosition < prefix.length + content.length) {
            pushUndo()
            updateMarkersOnEdit(cursorPosition, -1)
            content.deleteCharAt(cursorPosition - prefix.length)
        }
    }

    fun deleteWord() {
        if (hasSelection()) { deleteSelection(); return }
        if (content.isEmpty()) return
        pushUndo()
        var start = cursorPosition; var end = cursorPosition; val text = getText()
        while (start > prefix.length && !text[start - 1].isWhitespace()) start--
        while (end < text.length && !text[end].isWhitespace()) end++
        if (start == end) {
            if (end < text.length && text[end].isWhitespace()) while (end < text.length && text[end].isWhitespace()) end++
            else if (start > prefix.length && text[start - 1].isWhitespace()) while (start > prefix.length && text[start - 1].isWhitespace()) start--
        }
        if (start != end) {
            val cs = start - prefix.length; val ce = end - prefix.length
            updateMarkersOnEdit(start, -(ce - cs)); content.delete(cs, ce)
            cursorPosition = start; selectionAnchor = cursorPosition
        }
    }

    fun deleteLine() {
        if (content.isEmpty()) return
        pushUndo()
        val start = findLineStart(cursorPosition); val text = getText(); var end = text.indexOf('\n', start)
        if (end == -1) end = text.length else end += 1
        val cs = max(0, start - prefix.length); val ce = max(0, end - prefix.length)
        if (cs != ce) { updateMarkersOnEdit(start, -(ce - cs)); content.delete(cs, ce) }
        cursorPosition = start.coerceAtMost(prefix.length + content.length); selectionAnchor = cursorPosition
    }

    fun setCursor(pos: Int, selecting: Boolean = false) {
        cursorPosition = pos.coerceIn(prefix.length, prefix.length + content.length)
        if (!selecting) selectionAnchor = cursorPosition
        checkFoldEntry()
    }

    fun setSelection(start: Int, end: Int) {
        selectionAnchor = start.coerceIn(prefix.length, prefix.length + content.length)
        cursorPosition = end.coerceIn(prefix.length, prefix.length + content.length)
    }

    private fun checkFoldEntry() {
        folds.find { cursorPosition > it.start && cursorPosition <= it.end }?.let {
            cursorPosition = it.end + 1
            if (cursorPosition > prefix.length + content.length) cursorPosition = it.start
        }
    }

    fun moveCursorLeft(selecting: Boolean = false) { setCursor(cursorPosition - 1, selecting) }
    fun moveCursorRight(selecting: Boolean = false) { setCursor(cursorPosition + 1, selecting) }
    fun moveCursorToStart(selecting: Boolean = false) { setCursor(prefix.length, selecting) }
    fun moveCursorToEnd(selecting: Boolean = false) { setCursor(prefix.length + content.length, selecting) }
    fun moveCursorToLineStart(selecting: Boolean = false) { setCursor(findLineStart(cursorPosition), selecting) }
    fun moveCursorToLineEnd(selecting: Boolean = false) {
        val text = getText(); var end = text.indexOf('\n', cursorPosition)
        if (end == -1) end = text.length; setCursor(end, selecting)
    }

    fun moveWordLeft(selecting: Boolean = false) {
        val text = getText(); var pos = cursorPosition
        while (pos > prefix.length && text[pos - 1].isWhitespace()) pos--
        while (pos > prefix.length && !text[pos - 1].isWhitespace()) pos--
        setCursor(pos, selecting)
    }

    fun moveWordRight(selecting: Boolean = false) {
        val text = getText(); var pos = cursorPosition
        while (pos < text.length && !text[pos].isWhitespace()) pos++
        while (pos < text.length && text[pos].isWhitespace()) pos++
        setCursor(pos, selecting)
    }

    fun moveParagraphUp(selecting: Boolean = false) {
        val text = getText()
        var pos = cursorPosition
        if (pos <= prefix.length) return
        
        // Skip current paragraph break if we're on it
        if (pos > prefix.length + 1 && text[pos - 1] == '\n') pos--
        
        while (pos > prefix.length) {
            if (text[pos - 1] == '\n' && (pos == prefix.length + 1 || text[pos - 2] == '\n')) {
                break
            }
            pos--
        }
        setCursor(pos, selecting)
    }

    fun moveParagraphDown(selecting: Boolean = false) {
        val text = getText()
        var pos = cursorPosition
        if (pos >= text.length) return
        
        while (pos < text.length) {
            if (text[pos] == '\n' && (pos == text.length - 1 || text[pos + 1] == '\n')) {
                // Found a break, move past it
                pos++
                while (pos < text.length && text[pos] == '\n') pos++
                break
            }
            pos++
        }
        setCursor(pos, selecting)
    }

    fun moveSentenceUp(selecting: Boolean = false) {
        val text = getText()
        var pos = cursorPosition - 1
        
        // Skip trailing whitespace
        while (pos > prefix.length && text[pos].isWhitespace()) pos--
        // Skip current sentence terminator if we're on it
        if (pos > prefix.length && text[pos] in listOf('.', '!', '?')) pos--

        while (pos > prefix.length) {
            if (text[pos] in listOf('.', '!', '?') && (pos + 1 >= text.length || text[pos + 1].isWhitespace())) {
                var target = pos + 1
                while (target < text.length && text[target].isWhitespace()) target++
                if (target < cursorPosition) {
                    setCursor(target, selecting)
                    return
                }
            }
            pos--
        }
        setCursor(prefix.length, selecting)
    }

    fun moveSentenceDown(selecting: Boolean = false) {
        val text = getText()
        var pos = cursorPosition
        
        while (pos < text.length) {
            if (text[pos] in listOf('.', '!', '?') && (pos + 1 >= text.length || text[pos + 1].isWhitespace())) {
                var target = pos + 1
                while (target < text.length && text[target].isWhitespace()) target++
                if (target > cursorPosition) {
                    setCursor(target, selecting)
                    return
                }
            }
            pos++
        }
        setCursor(text.length, selecting)
    }

    fun moveCursorUp(selecting: Boolean = false) { /* Handled by EditorView moveVisualLine */ }
    fun moveCursorDown(selecting: Boolean = false) { /* Handled by EditorView moveVisualLine */ }

    fun findLineStart(pos: Int): Int {
        val text = getText(); var i = (pos - 1).coerceAtMost(text.length - 1)
        while (i >= prefix.length && text[i] != '\n') i--
        return i + 1
    }

    fun clear() {
        pushUndo()
        content.setLength(0)
        setCursor(prefix.length, false)
        bookmarks.clear()
        folds.clear()
        imageUri = null
        pdfUri = null
        fileName = null
        fileUri = null
        isMarkdownMode = false
    }
    fun setText(text: String) {
        content.setLength(0)
        if (text.startsWith(prefix)) content.append(text.substring(prefix.length)) else content.append(text)
        setCursor(prefix.length + content.length, false); undoStack.clear(); redoStack.clear(); bookmarks.clear(); folds.clear()
        isDirty = false
    }
    fun getLength(): Int = content.length
    
    fun getWordCount(): Int {
        if (imageUri != null) return 0
        var count = 0
        var inWord = false
        for (i in 0 until content.length) {
            if (!content[i].isWhitespace()) {
                if (!inWord) {
                    count++
                    inWord = true
                }
            } else {
                inWord = false
            }
        }
        return count
    }

    fun getWordPosition(): Int {
        if (imageUri != null) return 0
        val pos = max(0, cursorPosition - prefix.length)
        var count = 0
        var inWord = false
        for (i in 0 until min(pos, content.length)) {
            if (!content[i].isWhitespace()) {
                if (!inWord) {
                    count++
                    inWord = true
                }
            } else {
                inWord = false
            }
        }
        // If cursor is directly after a word or inside a word, 'count' is the correct 1-based index.
        return count
    }

    fun markClean() { isDirty = false }
    fun selectAll() { selectionAnchor = prefix.length; cursorPosition = prefix.length + content.length }
    fun find(query: String, startPos: Int, ignoreCase: Boolean = true): Int = getText().indexOf(query, startPos, ignoreCase)
    fun findBackward(query: String, startPos: Int, ignoreCase: Boolean = true): Int = getText().lastIndexOf(query, startPos, ignoreCase)
    fun replace(range: IntRange, replacement: String) {
        pushUndo(); val cs = max(0, range.first - prefix.length); val ce = max(0, range.last + 1 - prefix.length)
        updateMarkersOnEdit(range.first, -(ce - cs)); content.replace(cs, ce, replacement); updateMarkersOnEdit(range.first, replacement.length)
        cursorPosition = range.first + replacement.length; selectionAnchor = range.first
    }
    fun addBookmark() {
        val ls = findLineStart(cursorPosition)
        if (!bookmarks.contains(ls)) { if (bookmarks.size >= 8) bookmarks.removeAt(0); bookmarks.add(ls) }
    }
    fun removeBookmark(index: Int) { if (index in bookmarks.indices) bookmarks.removeAt(index) }
    fun clearBookmarks() { bookmarks.clear() }
    fun getLineText(offset: Int): String {
        val text = getText(); val s = findLineStart(offset); var e = text.indexOf('\n', s)
        if (e == -1) e = text.length; val line = text.substring(s, e).trim()
        return if (line.length > 30) line.take(27) + "..." else if (line.isEmpty()) "(empty line)" else line
    }
    fun collapseSelection() {
        if (!hasSelection()) return
        val r = getSelectionRange()
        if (folds.any { it.start in r || it.end in r }) return
        folds.add(Fold(r.first, r.last)); setCursor(r.first, false)
    }
    fun removeFold(index: Int) { if (index in folds.indices) folds.removeAt(index) }
}
