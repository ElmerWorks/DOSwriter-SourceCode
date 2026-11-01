package com.example.doswriter.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class MacroOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var macroList: List<String> = emptyList()
    private var selectedIndex: Int = -1
    private val currentText = StringBuilder()
    
    private var isContextMode = false
    private var isPickerOnly = false
    fun isConsoleMode(): Boolean = !isPickerOnly && !isContextMode
    private var contextList: List<String> = emptyList()
    private var activeContextName = "Text"

    var onSubmitted: ((String) -> Unit)? = null
    var onContextRequested: (() -> Unit)? = null
    var onContextSelected: ((String) -> Unit)? = null
    var onCancelled: (() -> Unit)? = null

    private var isEinkMode = false
    private var menuTextColor = Color.WHITE

    private val bgPaint = Paint().apply {
        color = Color.rgb(20, 20, 20)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
    }

    private val activePaint = Paint().apply {
        color = Color.rgb(50, 50, 50)
        style = Paint.Style.FILL
    }

    private val selectionPaint = Paint().apply {
        color = Color.rgb(100, 100, 255)
        style = Paint.Style.FILL
    }

    fun setMenuAppearance(textSize: Float, backgroundColor: Int, textColor: Int) {
        bgPaint.color = backgroundColor
        textPaint.textSize = textSize
        menuTextColor = textColor
        isEinkMode = (backgroundColor == Color.WHITE)
        activePaint.color = if (isEinkMode) Color.rgb(240, 240, 240) else Color.rgb(50, 50, 50)
        selectionPaint.color = if (isEinkMode) Color.rgb(200, 200, 200) else Color.rgb(60, 60, 180)
        invalidate()
    }

    fun show(macros: List<String>, contextName: String, isNew: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
        isContextMode = false
        isPickerOnly = !isNew
        activeContextName = contextName
        macroList = macros
        onSubmitted = onSubmit
        onCancelled = onCancel
        scrollOffset = 0
        if (isNew) {
            selectedIndex = 0 // [CHANGE CONTEXT]
            currentText.setLength(0)
        } else {
            // Picker mode: Start on the first macro if possible
            selectedIndex = if (macros.isNotEmpty()) 1 else 0
            currentText.setLength(0)
            if (macros.isNotEmpty()) {
                currentText.append(macroList[0])
            }
        }
        visibility = VISIBLE
        invalidate()
    }

    fun showContextPicker(contexts: List<String>, current: String, onSelected: (String) -> Unit) {
        isContextMode = true
        activeContextName = current
        contextList = contexts
        onContextSelected = onSelected
        selectedIndex = contexts.indexOf(current).coerceAtLeast(0)
        invalidate()
    }

    fun handleKey(char: Char) {
        if (isContextMode) return
        currentText.append(char)
        invalidate()
    }

    fun paste(text: String) {
        if (isContextMode) return
        currentText.append(text)
        invalidate()
    }

    fun deleteChar() {
        if (isContextMode) return
        if (currentText.isNotEmpty()) {
            currentText.setLength(currentText.length - 1)
            invalidate()
        }
    }

    fun selectContextOption() {
        if (isContextMode) return
        selectedIndex = 0
        scrollOffset = 0
        invalidate()
    }

    fun moveSelection(up: Boolean) {
        val listSize = if (isContextMode) contextList.size else (macroList.size + 1)
        if (listSize == 0) return
        
        if (up) {
            selectedIndex = if (selectedIndex <= 0) listSize - 1 else selectedIndex - 1
        } else {
            selectedIndex = (selectedIndex + 1) % listSize
        }
        
        // Auto-scroll logic
        maxVisibleItems = if (!isContextMode && !isPickerOnly) 4 else 6
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex
        } else if (selectedIndex >= scrollOffset + maxVisibleItems) {
            scrollOffset = selectedIndex - maxVisibleItems + 1
        }

        updateFieldFromSelection()
        invalidate()
    }

    private fun updateFieldFromSelection() {
        if (isContextMode) return
        // Only update field if we are selecting a macro, not [CHANGE CONTEXT]
        if (selectedIndex > 0 && selectedIndex <= macroList.size) {
            currentText.setLength(0)
            currentText.append(macroList[selectedIndex - 1])
        }
    }

    fun confirm() {
        if (isContextMode) {
            if (selectedIndex >= 0 && selectedIndex < contextList.size) {
                onContextSelected?.invoke(contextList[selectedIndex])
            }
        } else {
            // TEXT MACRO CONSOLE / PASTE TEXT MACRO logic
            
            // If user has typed something in the console, that ALWAYS takes priority for submission
            if (!isPickerOnly && currentText.isNotEmpty()) {
                onSubmitted?.invoke(currentText.toString())
                return
            }

            if (selectedIndex == 0) {
                onContextRequested?.invoke()
                return
            }

            // If picker mode or selection made, use the selection
            val textToSubmit = if (selectedIndex > 0 && selectedIndex <= macroList.size) {
                macroList[selectedIndex - 1]
            } else {
                currentText.toString()
            }

            if (textToSubmit.isNotEmpty()) {
                onSubmitted?.invoke(textToSubmit)
            }
        }
    }

    fun hide() {
        visibility = GONE
    }

    private var scrollOffset = 0
    private var maxVisibleItems = 5

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        
        // Define overlay box (reduced height and positioned lower)
        val boxLeft = w * 0.1f
        val boxRight = w * 0.9f
        val boxTop = h * 0.55f
        val boxBottom = h * 0.95f
        
        // Background box
        canvas.drawRect(boxLeft, boxTop, boxRight, boxBottom, bgPaint)

        val startX = w * 0.15f
        var currentY = boxTop + (h * 0.05f)
        val itemHeight = textPaint.textSize * 1.8f

        // Title
        textPaint.color = if (isEinkMode) Color.BLACK else Color.LTGRAY
        textPaint.isFakeBoldText = true
        val title = if (isContextMode) "SELECT MACRO CONTEXT" else if (isPickerOnly) "PASTE TEXT MACRO [$activeContextName]" else "TEXT MACRO CONSOLE [$activeContextName]"
        canvas.drawText(title, startX, currentY, textPaint)
        textPaint.isFakeBoldText = false
        currentY += itemHeight * 0.8f

        if (!isContextMode && !isPickerOnly) {
            // Active Input Field
            canvas.drawRect(w * 0.12f, currentY - (textPaint.textSize * 1.1f), w * 0.88f, currentY + (textPaint.textSize * 0.4f), activePaint)
            textPaint.color = menuTextColor
            canvas.drawText(currentText.toString() + "_", startX, currentY, textPaint)
            currentY += itemHeight * 1.2f
        }

        // Separator
        val sepColor = if (isEinkMode) Color.LTGRAY else Color.DKGRAY
        val oldStroke = textPaint.strokeWidth
        textPaint.color = sepColor
        canvas.drawLine(w * 0.12f, currentY - itemHeight * 0.5f, w * 0.88f, currentY - itemHeight * 0.5f, textPaint.apply { strokeWidth = 2f })
        textPaint.strokeWidth = oldStroke

        // List
        val activeList = if (isContextMode) contextList else (listOf("[CHANGE CONTEXT]") + macroList)
        
        // Handle Scrolling
        maxVisibleItems = if (!isContextMode && !isPickerOnly) 4 else 6
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex
        } else if (selectedIndex >= scrollOffset + maxVisibleItems) {
            scrollOffset = selectedIndex - maxVisibleItems + 1
        }
        
        val visibleRange = activeList.indices.filter { it >= scrollOffset && it < scrollOffset + maxVisibleItems }
        
        for ((displayIdx, i) in visibleRange.withIndex()) {
            val y = currentY + (displayIdx * itemHeight)

            if (i == selectedIndex) {
                canvas.drawRect(w * 0.12f, y - (itemHeight * 0.65f), w * 0.88f, y + (itemHeight * 0.3f), selectionPaint)
                textPaint.color = Color.BLACK
            } else {
                textPaint.color = menuTextColor
            }
            
            val label = if (!isContextMode && i == 0) activeList[i] else "${i + (if (isContextMode) 1 else 0)}. ${activeList[i]}"
            canvas.drawText(label, startX, y, textPaint)
        }

        // Help
        textPaint.color = if (isEinkMode) Color.GRAY else Color.DKGRAY
        val oldTextSize = textPaint.textSize
        textPaint.textSize = oldTextSize * 0.65f
        val help = if (isContextMode) "[Enter] Select Context | [A] Add Context | [Arrows] Navigate | [Esc] Back"
                   else "[Enter] OK | [Arrows] Select | [Esc] Cancel"
        canvas.drawText(help, startX, boxBottom - (h * 0.03f), textPaint)
        textPaint.textSize = oldTextSize
    }
}
