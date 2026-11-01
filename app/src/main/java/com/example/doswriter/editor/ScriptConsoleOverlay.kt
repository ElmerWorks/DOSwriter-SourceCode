package com.example.doswriter.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class ScriptConsoleOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var inputText = StringBuilder()
    private val history = mutableListOf<String>()
    private var historyIndex = -1
    private val MAX_HISTORY = 12

    private var onExecute: ((String) -> Unit)? = null
    
    private val bgPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 32f
        isAntiAlias = true
    }

    private val borderPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var isEinkMode = false
    private var menuTextColor = Color.GREEN

    fun setMenuAppearance(textSize: Float, backgroundColor: Int, textColor: Int) {
        textPaint.textSize = textSize * 0.8f
        this.menuTextColor = textColor
        textPaint.color = textColor
        bgPaint.color = backgroundColor
        borderPaint.color = textColor
        
        val r = Color.red(backgroundColor)
        val g = Color.green(backgroundColor)
        val b = Color.blue(backgroundColor)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
        isEinkMode = luminance > 128
        invalidate()
    }

    fun show(existingHistory: List<String>, onExec: (String) -> Unit) {
        this.onExecute = onExec
        history.clear()
        history.addAll(existingHistory)
        inputText.setLength(0)
        historyIndex = -1
        visibility = VISIBLE
        invalidate()
    }

    fun handleKey(char: Char) {
        inputText.append(char)
        historyIndex = -1
        invalidate()
    }

    fun deleteChar() {
        if (inputText.isNotEmpty()) {
            inputText.deleteCharAt(inputText.length - 1)
            historyIndex = -1
            invalidate()
        }
    }

    fun moveHistory(up: Boolean) {
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
                inputText.setLength(0)
                invalidate()
            }
        }
    }

    private fun updateFromHistory() {
        if (historyIndex in history.indices) {
            inputText.setLength(0)
            inputText.append(history[history.size - 1 - historyIndex])
            invalidate()
        }
    }

    fun confirm() {
        val cmd = inputText.toString()
        if (cmd.isNotBlank()) {
            history.remove(cmd)
            history.add(cmd)
            if (history.size > MAX_HISTORY) history.removeAt(0)
            onExecute?.invoke(cmd)
        }
        hide()
    }

    fun hide() {
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        
        val rectTop = h * 0.7f
        val rectBottom = h * 0.95f
        
        // Background
        canvas.drawRect(w * 0.05f, rectTop, w * 0.95f, rectBottom, bgPaint)
        canvas.drawRect(w * 0.05f, rectTop, w * 0.95f, rectBottom, borderPaint)

        val startX = w * 0.08f
        var currentY = rectTop + textPaint.textSize * 1.5f

        // Title
        textPaint.color = if (isEinkMode) Color.DKGRAY else Color.GRAY
        canvas.drawText("SCRIPT CONSOLE (Single Line)", startX, currentY, textPaint)
        currentY += textPaint.textSize * 1.8f

        // Prompt
        textPaint.color = menuTextColor
        canvas.drawText("> ${inputText}_", startX, currentY, textPaint)
        
        currentY += textPaint.textSize * 1.8f
        
        // Help
        textPaint.color = if (isEinkMode) Color.DKGRAY else Color.GRAY
        textPaint.textSize = textPaint.textSize * 0.7f
        canvas.drawText("[Enter] Execute | [Up/Down] History | [Esc] Close", startX, currentY, textPaint)
        textPaint.textSize = textPaint.textSize / 0.7f
    }
}
