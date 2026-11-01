package com.example.doswriter.model

import com.example.doswriter.logic.BufferManager
import java.util.Stack
import kotlin.math.max
import kotlin.math.min

class EditorBuffer(val id: Int) {
    val prefix = "\n\n"

    enum class CursorStyle {
        THIN_CARET, THICK_CARET, BLOCK, UNDERLINE, INVERTED, RETRO_BLOCK, DIM_CARET
    }

    enum class TypewriterAnchor {
        CENTER, BOTTOM
    }

    data class TypewriterSettings(
        var isEnabled: Boolean = false,
        var isFixed: Boolean = false,
        var anchor: TypewriterAnchor = TypewriterAnchor.CENTER,
        var showGuide: Boolean = false,
        var charDelayMs: Long = 100L,
        var crlfDelayMs: Long = 100L,
        var isTerminalMode: Boolean = false
    )

    data class CursorSettings(
        var style: CursorStyle = CursorStyle.BLOCK,
        var blinkOnShortcut: Boolean = true,
        var blinkAlways: Boolean = false,
        var blinkIntervalMs: Long = 500L,
        var locatorBlinkDurationMs: Long = 3000L,
        val typewriter: TypewriterSettings = TypewriterSettings()
    )

    val cursorSettings = CursorSettings()

    private val content = StringBuilder()
    
    // Performance optimization: track versions
    var version: Int = 0
        private set

    // Cached metrics
    private var cachedWordCount: Int = -1
    private var cachedWordPos: Int = -1
    private var lastWordPosCursor: Int = -1

    var cursorPosition: Int = prefix.length
        private set
    var selectionAnchor: Int = prefix.length
        private set

    var fileName: String? = null
    var fileUri: String? = null
    var imageUri: String? = null
    var imageScale: Float = 1f
    var imagePanX: Float = 0f
    var imagePanY: Float = 0f
    var pdfUri: String? = null
    var pdfScrollY: Float = 0f
    var pdfScale: Float = 1f
    var pdfPanX: Float = 0f
    var sideMargin: Float = 60f
    var topBottomMargin: Float = 60f
    var verticalOffset: Float = 10f
    var fontSize: Float = 48f
    var lineSpacing: Float = 1.2f
    var fontType: String = "NotoSans-Bold"
    var customFontsPath: String? = null
    var isBold: Boolean = false
    var activePreset: String = "Standard" // Pocket, Compact, Standard, Comfortable, Manuscript, Custom
    var deviceProfile: Int = 0 // 0: Phone, 1: Tablet, 2: Custom (E-Ink)
    var dayThemeIndex: Int = 3
    var nightThemeIndex: Int = 3 
    var charLimit: Int = 0 
    var isDirty: Boolean = false
    var showMargins: Boolean = false
    var isMarkdownMode: Boolean = false
    var useVirtualInput: Boolean = false
    var showNumericRow: Boolean = true
    var keyboardThemeBg: Boolean = true // true: Background color, false: Font color
    var showKeyBorder: Boolean = true
    var lineEndingSymbol: Int = 0 // 0: None, 1: ↵, 2: ¶, 3: ⏎
    var tabSize: Int = 4
    var pageLength: Int = 20
    var syncFilePath: String? = null
    var keyboardHeight: Int = 600
    var autoTypewriterOnLargeFiles: Boolean = false
    var cursorColor: Int? = null // null means use theme font color
    var thumbnailUri: String? = null
    var thumbnailText: String? = null
    var useThumbnail: Boolean = false
    var thumbnailPreference: Int = 1 // 1: Image, 2: Text

    fun getEffectiveThumbnailText(bufferManager: BufferManager): String? {
        if (bufferManager.useFilenameForThumbnailText) {
            val name = fileName
            val dirtySuffix = if (isDirty) "*" else ""
            return if (name.isNullOrEmpty()) {
                "EMPTY$dirtySuffix"
            } else {
                "$name$dirtySuffix"
            }
        }
        if (thumbnailText != null) {
            return thumbnailText
        }
        if (isDirty && fileName == null) {
            return "EMPTY*"
        }
        return null
    }
    val bookmarks = mutableListOf<Int>()

    data class PdfFavorite(val pageIndex: Int, val identifier: String)
    val pdfFavorites = mutableListOf<PdfFavorite>()
    
    val collapsePatterns = mutableListOf<String>()

    data class ManuscriptSettings(
        var pageSize: String = "4x6", // "8.5x11", "6x9", "5x8", "4x6", "3x4"
        var isPortrait: Boolean = true,
        var fontSize: Float = 14f,
        var margins: Float = 0.25f, // Inches
        var isParagraphCollapseEnabled: Boolean = true,
        var isFirstSentenceOnly: Boolean = true,
        var layoutTileCount: Int = 8 // 4 or 8
    )
    val manuscriptSettings = ManuscriptSettings()
    
    data class Fold(var start: Int, var end: Int) {
        fun copy() = Fold(start, end)
    }
    val folds = mutableListOf<Fold>()
    val expandedFoldStack = mutableListOf<Fold>()

    private data class HistoryState(val text: String, val cursor: Int, val anchor: Int)
    private val undoStack = Stack<HistoryState>()
    private val redoStack = Stack<HistoryState>()
    private val MAX_HISTORY = 100

    private fun pushUndo() {
        isDirty = true
        invalidateCache()
        if (undoStack.size >= MAX_HISTORY) undoStack.removeAt(0)
        undoStack.push(HistoryState(content.toString(), cursorPosition, selectionAnchor))
        redoStack.clear()
    }

    private fun invalidateCache() {
        version++
        cachedWordCount = -1
        cachedWordPos = -1
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
        invalidateCache()
    }

    /**
     * Returns the full content as a CharSequence to avoid heavy string copies.
     * Note: This involves a small allocation for the combined sequence.
     */
    fun getContent(): CharSequence {
        // We can use a simple wrapper to combine prefix and content without copying
        return object : CharSequence {
            override val length: Int = prefix.length + content.length
            override fun get(index: Int): Char = if (index < prefix.length) prefix[index] else content[index - prefix.length]
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = toString().subSequence(startIndex, endIndex) // Fallback
            override fun toString(): String = prefix + content.toString()
        }
    }
    
    fun getCharAt(index: Int): Char {
        return if (index < prefix.length) prefix[index] else content[index - prefix.length]
    }
    
    fun getFullLength(): Int = prefix.length + content.length

    fun getText(): String = prefix + content.toString()

    fun getCleanText(): String = content.toString()
    
    fun isLargeFile(): Boolean = content.length > 5000

    fun hasSelection(): Boolean = selectionAnchor != cursorPosition

    fun getSelectionRange(): IntRange {
        val start = min(selectionAnchor, cursorPosition)
        val end = max(selectionAnchor, cursorPosition)
        return start until end
    }

    fun getSelectedText(): String {
        val range = getSelectionRange()
        if (range.isEmpty()) return ""
        val text = getText() // One copy for selection is fine
        return text.substring(range.first, min(range.last + 1, text.length))
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
        invalidateCache()
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
        invalidateCache()
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

        val stackIter = expandedFoldStack.iterator()
        while(stackIter.hasNext()) {
            val fold = stackIter.next()
            if (fold.start >= at) { fold.start += delta; fold.end += delta }
            else if (fold.end >= at) { fold.end += delta }
            if (fold.start >= fold.end || fold.start < prefix.length) stackIter.remove()
        }
    }

    fun deleteBackward() {
        if (hasSelection()) deleteSelection()
        else if (cursorPosition > prefix.length) {
            pushUndo()
            updateMarkersOnEdit(cursorPosition - 1, -1)
            content.deleteCharAt(cursorPosition - 1 - prefix.length)
            cursorPosition--
            selectionAnchor = cursorPosition
            invalidateCache()
        }
    }

    fun deleteForward() {
        if (hasSelection()) deleteSelection()
        else if (cursorPosition < prefix.length + content.length) {
            pushUndo()
            updateMarkersOnEdit(cursorPosition, -1)
            content.deleteCharAt(cursorPosition - prefix.length)
            invalidateCache()
        }
    }

    fun deleteWord() {
        if (hasSelection()) { deleteSelection(); return }
        if (content.isEmpty()) return
        pushUndo()
        var start = cursorPosition; var end = cursorPosition
        val len = getFullLength()
        while (start > prefix.length && !getCharAt(start - 1).isWhitespace()) start--
        while (end < len && !getCharAt(end).isWhitespace()) end++
        if (start == end) {
            if (end < len && getCharAt(end).isWhitespace()) while (end < len && getCharAt(end).isWhitespace()) end++
            else if (start > prefix.length && getCharAt(start - 1).isWhitespace()) while (start > prefix.length && getCharAt(start - 1).isWhitespace()) start--
        }
        if (start != end) {
            val cs = start - prefix.length; val ce = end - prefix.length
            updateMarkersOnEdit(start, -(ce - cs)); content.delete(cs, ce)
            cursorPosition = start; selectionAnchor = cursorPosition
            invalidateCache()
        }
    }

    fun deleteLine() {
        if (content.isEmpty()) return
        pushUndo()
        val start = findLineStart(cursorPosition)
        val len = getFullLength()
        var end = start
        while (end < len && getCharAt(end) != '\n') end++
        if (end < len) end += 1
        
        val cs = max(0, start - prefix.length); val ce = max(0, end - prefix.length)
        if (cs != ce) { updateMarkersOnEdit(start, -(ce - cs)); content.delete(cs, ce) }
        cursorPosition = start.coerceAtMost(prefix.length + content.length); selectionAnchor = cursorPosition
        invalidateCache()
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
        val len = getFullLength()
        var end = cursorPosition
        while (end < len && getCharAt(end) != '\n') end++
        setCursor(end, selecting)
    }

    fun moveWordLeft(selecting: Boolean = false) {
        var pos = cursorPosition
        while (pos > prefix.length && getCharAt(pos - 1).isWhitespace()) pos--
        while (pos > prefix.length && !getCharAt(pos - 1).isWhitespace()) pos--
        setCursor(pos, selecting)
    }

    fun moveWordRight(selecting: Boolean = false) {
        val len = getFullLength()
        var pos = cursorPosition
        while (pos < len && !getCharAt(pos).isWhitespace()) pos++
        while (pos < len && getCharAt(pos).isWhitespace()) pos++
        setCursor(pos, selecting)
    }

    fun moveParagraphUp(selecting: Boolean = false) {
        var pos = cursorPosition
        if (pos <= prefix.length) return
        
        // 1. Skip current paragraph start / preceding newlines
        while (pos > prefix.length && getCharAt(pos - 1).let { it == '\n' || it == '\r' }) pos--
        
        // 2. Scan back for paragraph break
        var foundBreak = false
        while (pos > prefix.length) {
            if (getCharAt(pos - 1) == '\n') {
                var prev = pos - 2
                while (prev >= prefix.length && getCharAt(prev) == '\r') prev--
                if (prev >= prefix.length && getCharAt(prev) == '\n') {
                    // Found double newline. pos is start of current paragraph.
                    foundBreak = true
                    break
                }
            }
            pos--
        }
        setCursor(if (foundBreak) pos else prefix.length, selecting)
    }

    fun moveParagraphDown(selecting: Boolean = false) {
        val len = getFullLength()
        var pos = cursorPosition
        if (pos >= len) return
        
        // 1. Skip current paragraph content
        while (pos < len && getCharAt(pos).let { it != '\n' && it != '\r' }) pos++
        
        // 2. Scan for next paragraph start (after a break)
        var foundBreak = false
        while (pos < len) {
            if (getCharAt(pos) == '\n') {
                var next = pos + 1
                while (next < len && getCharAt(next) == '\r') next++
                if (next < len && getCharAt(next) == '\n') {
                    // Found break, skip all trailing newlines/returns
                    pos = next
                    while (pos < len && getCharAt(pos).let { it == '\n' || it == '\r' }) pos++
                    foundBreak = true
                    break
                }
            }
            pos++
        }
        setCursor(if (foundBreak) pos else len, selecting)
    }

    fun moveToParagraphStart(selecting: Boolean = false) {
        var pos = cursorPosition
        if (pos <= prefix.length) return
        
        // Move back until we find a double newline or hit prefix
        while (pos > prefix.length) {
            if (getCharAt(pos - 1) == '\n') {
                var prev = pos - 2
                while (prev >= prefix.length && getCharAt(prev) == '\r') prev--
                if (prev >= prefix.length && getCharAt(prev) == '\n') {
                    break // Found paragraph start
                }
            }
            pos--
        }
        setCursor(pos, selecting)
    }

    fun moveToParagraphEnd(selecting: Boolean = false) {
        val len = getFullLength()
        var pos = cursorPosition
        if (pos >= len) return
        
        // Move forward until we find a double newline or hit end
        while (pos < len) {
            if (getCharAt(pos) == '\n') {
                var next = pos + 1
                while (next < len && getCharAt(next) == '\r') next++
                if (next < len && getCharAt(next) == '\n') {
                    break // Found paragraph end
                }
            }
            pos++
        }
        setCursor(pos, selecting)
    }

    fun moveSentenceUp(selecting: Boolean = false) {
        var pos = cursorPosition - 1
        while (pos > prefix.length && getCharAt(pos).isWhitespace()) pos--
        if (pos > prefix.length && getCharAt(pos) in listOf('.', '!', '?')) pos--

        while (pos > prefix.length) {
            val c = getCharAt(pos)
            if (c in listOf('.', '!', '?') && (pos + 1 >= getFullLength() || getCharAt(pos + 1).isWhitespace())) {
                var target = pos + 1
                while (target < getFullLength() && getCharAt(target).isWhitespace()) target++
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
        val len = getFullLength()
        var pos = cursorPosition
        
        while (pos < len) {
            val c = getCharAt(pos)
            if (c in listOf('.', '!', '?') && (pos + 1 >= len || getCharAt(pos + 1).isWhitespace())) {
                var target = pos + 1
                while (target < len && getCharAt(target).isWhitespace()) target++
                if (target > cursorPosition) {
                    setCursor(target, selecting)
                    return
                }
            }
            pos++
        }
        setCursor(len, selecting)
    }

    fun moveCursorUp(selecting: Boolean = false) { /* Handled by EditorView moveVisualLine */ }
    fun moveCursorDown(selecting: Boolean = false) { /* Handled by EditorView moveVisualLine */ }

    fun findLineStart(pos: Int): Int {
        var i = (pos - 1).coerceAtMost(getFullLength() - 1)
        while (i >= prefix.length && getCharAt(i) != '\n') i--
        return max(prefix.length, i + 1)
    }

    fun clear() {
        pushUndo()
        content.setLength(0)
        setCursor(prefix.length, false)
        bookmarks.clear()
        pdfFavorites.clear()
        folds.clear()
        expandedFoldStack.clear()
        imageUri = null
        pdfUri = null
        fileName = null
        fileUri = null
        isMarkdownMode = false
        invalidateCache()
    }

    /**
     * Hard reset of the buffer state. Clears all content and metadata without pushing to undo stack.
     * Used when opening new files or loading examples.
     */
    fun reset() {
        content.setLength(0)
        setCursor(prefix.length, false)
        undoStack.clear()
        redoStack.clear()
        bookmarks.clear()
        pdfFavorites.clear()
        folds.clear()
        expandedFoldStack.clear()
        imageUri = null
        pdfUri = null
        fileName = null
        fileUri = null
        isMarkdownMode = false
        isDirty = false
        invalidateCache()
    }
    fun setText(text: String) {
        content.setLength(0)
        if (text.startsWith(prefix)) content.append(text.substring(prefix.length)) else content.append(text)
        setCursor(prefix.length, false); undoStack.clear(); redoStack.clear(); bookmarks.clear(); folds.clear()
        imageUri = null
        pdfUri = null
        isDirty = false
        invalidateCache()
    }
    fun getLength(): Int = content.length
    
    fun getWordCount(): Int {
        if (imageUri != null) return 0
        if (cachedWordCount != -1) return cachedWordCount

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
        cachedWordCount = count
        return count
    }

    fun getWordPosition(): Int {
        if (imageUri != null) return 0
        if (lastWordPosCursor == cursorPosition && cachedWordPos != -1) return cachedWordPos

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
        lastWordPosCursor = cursorPosition
        cachedWordPos = count
        return count
    }

    fun markClean() { isDirty = false }
    fun selectAll() { selectionAnchor = prefix.length; cursorPosition = prefix.length + content.length }
    
    fun find(query: String, startPos: Int, ignoreCase: Boolean = true): Int {
        return getText().indexOf(query, startPos, ignoreCase)
    }
    
    fun findBackward(query: String, startPos: Int, ignoreCase: Boolean = true): Int {
        return getText().lastIndexOf(query, startPos, ignoreCase)
    }
    
    fun replace(range: IntRange, replacement: String) {
        pushUndo(); val cs = max(0, range.first - prefix.length); val ce = max(0, range.last + 1 - prefix.length)
        updateMarkersOnEdit(range.first, -(ce - cs)); content.replace(cs, ce, replacement); updateMarkersOnEdit(range.first, replacement.length)
        cursorPosition = range.first + replacement.length; selectionAnchor = range.first
        invalidateCache()
    }
    fun addBookmark() {
        val ls = findLineStart(cursorPosition)
        if (!bookmarks.contains(ls)) { if (bookmarks.size >= 8) bookmarks.removeAt(0); bookmarks.add(ls) }
    }
    fun removeBookmark(index: Int) { if (index in bookmarks.indices) bookmarks.removeAt(index) }
    fun clearBookmarks() { bookmarks.clear() }
    
    fun getLineText(offset: Int): String {
        val s = findLineStart(offset)
        val len = getFullLength()
        var e = s
        while (e < len && getCharAt(e) != '\n') e++
        
        val line = StringBuilder()
        for (i in s until e) line.append(getCharAt(i))
        val trimmed = line.toString().trim()
        
        return if (trimmed.length > 30) trimmed.take(27) + "..." else if (trimmed.isEmpty()) "(empty line)" else trimmed
    }

    fun collapseSelection() {
        if (!hasSelection()) return
        val r = getSelectionRange()
        addFold(r.first, r.last)
        setCursor(r.first, false)
    }
    fun addFold(start: Int, end: Int) {
        val len = getFullLength()
        val s = start.coerceIn(prefix.length, len)
        val e = end.coerceIn(s, len)
        
        if (s >= e) return
        if (folds.any { it.start == s && it.end == e }) return
        
        folds.add(Fold(s, e))
        folds.sortBy { it.start } // Keep sorted for optimized layout
        invalidateCache()
    }
    fun addFolds(newFolds: List<Fold>) {
        if (newFolds.isEmpty()) return
        var added = false
        newFolds.forEach { fold ->
            if (fold.start < fold.end && !folds.any { it.start == fold.start && it.end == fold.end }) {
                folds.add(fold)
                added = true
            }
        }
        if (added) {
            folds.sortBy { it.start }
            invalidateCache()
        }
    }
    fun removeFold(index: Int) { 
        if (index in folds.indices) {
            folds.removeAt(index)
            invalidateCache()
        }
    }
    fun clearFolds() {
        if (folds.isNotEmpty() || expandedFoldStack.isNotEmpty()) {
            folds.clear()
            expandedFoldStack.clear()
            invalidateCache()
        }
    }

    fun isAtFold(): Boolean = folds.any { it.start == cursorPosition }
    fun uncollapseAtCursor() { 
        val fold = folds.find { it.start == cursorPosition }
        if (fold != null) {
            if (expandedFoldStack.size >= 8) {
                expandedFoldStack.removeAt(0)
            }
            expandedFoldStack.add(fold.copy())
            folds.remove(fold)
            invalidateCache()
        }
    }
    
    fun recollapseLastExpansion(): Boolean {
        if (expandedFoldStack.isNotEmpty()) {
            val fold = expandedFoldStack.removeAt(expandedFoldStack.size - 1)
            if (!folds.any { it.start == fold.start && it.end == fold.end }) {
                folds.add(fold)
                folds.sortBy { it.start }
                invalidateCache()
                return true
            }
        }
        return false
    }

    fun clearSelection() {
        selectionAnchor = cursorPosition
        invalidateCache()
    }
}
