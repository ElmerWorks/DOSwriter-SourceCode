package com.example.minimaltexteditor.editor

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

    fun show(mode: Mode, onFind: (String, Boolean, Boolean) -> Unit, onReplace: (String, String, Boolean, Boolean) -> Unit) {
        this.mode = mode
        this.onFindNext = onFind
        this.onReplaceNext = onReplace
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

    fun deleteChar() {
        val target = if (activeField == 0) findText else replaceText
        if (target.isNotEmpty()) {
            target.deleteCharAt(target.length - 1)
            historyIndex = -1
            invalidate()
        }
    }

    fun moveHistory(up: Boolean) {
        if (activeField != 0 || findHistory.isEmpty()) return

        if (up) {
            if (historyIndex < findHistory.size - 1) {
                historyIndex++
                updateFromHistory()
            }
        } else {
            if (historyIndex > 0) {
                historyIndex--
                updateFromHistory()
            } else if (historyIndex == 0) {
                historyIndex = -1
                findText.setLength(0)
                invalidate()
            }
        }
    }

    private fun updateFromHistory() {
        if (historyIndex in findHistory.indices) {
            findText.setLength(0)
            findText.append(findHistory[findHistory.size - 1 - historyIndex])
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
            invalidate()
        }
    }

    fun confirm(forward: Boolean = true) {
        val query = findText.toString()
        if (query.isNotEmpty()) {
            // Add to history if not same as last
            if (findHistory.isEmpty() || findHistory.last() != query) {
                findHistory.add(query)
                if (findHistory.size > MAX_HISTORY) findHistory.removeAt(0)
            }
        }

        if (mode == Mode.FIND) {
            onFindNext?.invoke(query, scope == Scope.ALL, forward)
        } else {
            onReplaceNext?.invoke(query, replaceText.toString(), scope == Scope.ALL, forward)
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
        textPaint.color = Color.GRAY
        canvas.drawText("${mode.name} - Scope: ${scope.name} (Alt+S to toggle)", startX, currentY, textPaint)
        currentY += 60f

        // Find Field
        textPaint.color = if (activeField == 0) Color.WHITE else Color.GRAY
        canvas.drawText("Find: ${findText}_", startX, currentY, textPaint)
        if (activeField == 0) canvas.drawRect(startX - 10f, currentY - 40f, w * 0.9f, currentY + 15f, activePaint)
        currentY += 80f

        // Replace Field (only if in replace mode)
        if (mode == Mode.REPLACE) {
            textPaint.color = if (activeField == 1) Color.WHITE else Color.GRAY
            canvas.drawText("Replace with: ${replaceText}_", startX, currentY, textPaint)
            if (activeField == 1) canvas.drawRect(startX - 10f, currentY - 40f, w * 0.9f, currentY + 15f, activePaint)
            currentY += 60f
        }

        textPaint.color = Color.DKGRAY
        textPaint.textSize = 28f
        canvas.drawText("[Enter] Find Next | [Ctrl+Enter] Find Prev | [Tab] Switch Field", startX, currentY, textPaint)
        currentY += 35f
        canvas.drawText("[Up/Down] History | [Esc] Close", startX, currentY, textPaint)
        textPaint.textSize = 36f
    }
}
