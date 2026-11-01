package com.example.doswriter.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class SearchOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { FIND, REPLACE }
    enum class Scope { CURRENT, ALL }

    var mode = Mode.FIND
    var scope = Scope.CURRENT
    
    var findText = StringBuilder()
    var replaceText = StringBuilder()
    private var activeField = 0 // 0 for find, 1 for replace

    private val findHistory = mutableListOf<String>()
    private val replaceHistory = mutableListOf<String>()
    private var historyIndex = -1
    private val MAX_HISTORY = 8

    private var onFindNext: ((String, Boolean, Boolean) -> Unit)? = null
    private var onReplaceNext: ((String, String, Boolean, Boolean) -> Unit)? = null

    private val bgPaint = Paint().apply {
        color = Color.rgb(20, 20, 20) // Fully opaque
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
    }

    private val activePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var isEinkMode = false
    private var menuTextColor = Color.WHITE

    fun setMenuAppearance(textSize: Float, backgroundColor: Int, textColor: Int) {
        textPaint.textSize = textSize
        this.menuTextColor = textColor
        textPaint.color = textColor
        bgPaint.color = backgroundColor
        val r = Color.red(backgroundColor)
        val g = Color.green(backgroundColor)
        val b = Color.blue(backgroundColor)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
        isEinkMode = luminance > 128
        invalidate()
    }

    fun show(mode: Mode, onFind: (String, Boolean, Boolean) -> Unit, onReplace: (String, String, Boolean, Boolean) -> Unit) {
        this.mode = mode
        this.onFindNext = onFind
        this.onReplaceNext = onReplace
        findText.setLength(0)
        replaceText.setLength(0)
        activeField = 0
        historyIndex = -1
        visibility = VISIBLE
        invalidate()
    }

    fun handleKey(char: Char) {
        if (activeField == 0) findText.append(char)
        else replaceText.append(char)
        historyIndex = -1
        invalidate()
    }

    fun paste(text: String) {
        if (activeField == 0) findText.append(text)
        else replaceText.append(text)
        historyIndex = -1
        invalidate()
    }

    fun deleteChar() {
        val target = if (activeField == 0) findText else replaceText
        if (target.isNotEmpty()) {
            target.deleteCharAt(target.length - 1)
            historyIndex = -1
            invalidate()
        }
    }

    fun moveHistory(up: Boolean) {
        val history = if (activeField == 0) findHistory else replaceHistory
        if (history.isEmpty()) return

        if (up) {
            if (historyIndex < history.size - 1) {
                historyIndex++
                updateFromHistory()
            }
        } else {
            if (historyIndex > 0) {
                historyIndex--
                updateFromHistory()
            } else if (historyIndex == 0) {
                historyIndex = -1
                if (activeField == 0) findText.setLength(0)
                else replaceText.setLength(0)
                invalidate()
            }
        }
    }

    private fun updateFromHistory() {
        val history = if (activeField == 0) findHistory else replaceHistory
        val target = if (activeField == 0) findText else replaceText
        if (historyIndex in history.indices) {
            target.setLength(0)
            target.append(history[history.size - 1 - historyIndex])
            invalidate()
        }
    }

    fun toggleScope() {
        scope = if (scope == Scope.CURRENT) Scope.ALL else Scope.CURRENT
        invalidate()
    }

    fun switchField() {
        if (mode == Mode.REPLACE) {
            activeField = 1 - activeField
            historyIndex = -1
            invalidate()
        }
    }

    fun confirm(forward: Boolean = true, forceAll: Boolean = false) {
        val query = findText.toString()
        if (query.isNotEmpty()) {
            findHistory.remove(query)
            findHistory.add(query)
            if (findHistory.size > MAX_HISTORY) findHistory.removeAt(0)
        }

        val replacement = replaceText.toString()
        if (mode == Mode.REPLACE && replacement.isNotEmpty()) {
            replaceHistory.remove(replacement)
            replaceHistory.add(replacement)
            if (replaceHistory.size > MAX_HISTORY) replaceHistory.removeAt(0)
        }

        if (mode == Mode.FIND) {
            onFindNext?.invoke(query, (scope == Scope.ALL) || forceAll, forward)
        } else {
            onReplaceNext?.invoke(query, replacement, (scope == Scope.ALL) || forceAll, forward)
        }
    }

    fun hide() {
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        
        canvas.drawRect(w * 0.05f, h * 0.1f, w * 0.95f, h * 0.5f, bgPaint)

        val startX = w * 0.1f
        var currentY = h * 0.18f

        // Mode Title
        val titleColor = if (isEinkMode) Color.GRAY else Color.LTGRAY
        textPaint.color = titleColor
        canvas.drawText("${mode.name} - Scope: ${scope.name} (Alt+S to toggle)", startX, currentY, textPaint)
        currentY += textPaint.textSize * 1.6f

        // Find Field
        textPaint.color = if (activeField == 0) menuTextColor else titleColor
        canvas.drawText("Find: ${findText}_", startX, currentY, textPaint)
        if (activeField == 0) canvas.drawRect(startX - 10f, currentY - (textPaint.textSize * 1.1f), w * 0.9f, currentY + (textPaint.textSize * 0.4f), activePaint)
        currentY += textPaint.textSize * 2.2f

        // Replace Field (only if in replace mode)
        if (mode == Mode.REPLACE) {
            textPaint.color = if (activeField == 1) menuTextColor else titleColor
            canvas.drawText("Replace with: ${replaceText}_", startX, currentY, textPaint)
            if (activeField == 1) canvas.drawRect(startX - 10f, currentY - (textPaint.textSize * 1.1f), w * 0.9f, currentY + (textPaint.textSize * 0.4f), activePaint)
            currentY += textPaint.textSize * 1.6f
        }

        textPaint.color = Color.DKGRAY
        textPaint.textSize = textPaint.textSize * 0.75f
        val help1 = if (mode == Mode.FIND) "[Enter] Find Next | [Ctrl+Enter] Find Prev | [Tab] Switch Field"
                    else "[Enter] Replace | [Alt+A] Replace All | [Tab] Switch Field"
        canvas.drawText(help1, startX, currentY, textPaint)
        currentY += textPaint.textSize * 1.25f
        canvas.drawText("[Up/Down] History | [Alt+S] Scope | [Esc] Close", startX, currentY, textPaint)
        // Restore size
        textPaint.textSize = textPaint.textSize / 0.75f
    }
}
