package com.example.minimaltexteditor.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

class MultiSelectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var items: List<String> = emptyList()
    private val selectedItems = mutableSetOf<Int>()
    private var selectedIndex = 0
    private var scrollOffset = 0
    private var onConfirmed: ((List<Int>) -> Unit)? = null

    private val bgPaint = Paint().apply {
        color = Color.rgb(20, 20, 20) // Fully opaque
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
    }

    private val selectionPaint = Paint().apply {
        color = Color.argb(100, 0, 200, 0)
        style = Paint.Style.FILL
    }

    private val itemHeight = 70f

    fun show(title: String, options: List<String>, onConfirm: (List<Int>) -> Unit) {
        items = listOf(title.uppercase()) + options
        selectedItems.clear()
        selectedIndex = 1
        scrollOffset = 0
        onConfirmed = onConfirm
        visibility = VISIBLE
        invalidate()
    }

    fun moveUp() {
        if (selectedIndex > 1) {
            selectedIndex--
            updateScroll()
            invalidate()
        }
    }

    fun moveDown() {
        if (selectedIndex < items.size - 1) {
            selectedIndex++
            updateScroll()
            invalidate()
        }
    }

    fun toggleSelection() {
        if (selectedIndex > 0) {
            val idx = selectedIndex - 1
            if (selectedItems.contains(idx)) {
                selectedItems.remove(idx)
            } else {
                selectedItems.add(idx)
            }
            invalidate()
        }
    }

    fun selectIndex(index: Int) {
        if (index + 1 < items.size) {
            selectedIndex = index + 1
            toggleSelection()
        }
    }

    fun confirm() {
        val result = selectedItems.toList().sorted()
        val callback = onConfirmed
        hide()
        callback?.invoke(result)
    }

    fun hide() {
        visibility = GONE
    }

    private fun updateScroll() {
        val availableHeight = height * 0.7f
        val maxVisible = (availableHeight / itemHeight).toInt()
        if (maxVisible <= 0) return

        if (selectedIndex < scrollOffset + 1 && selectedIndex > 0) {
            scrollOffset = selectedIndex - 1
        } else if (selectedIndex >= scrollOffset + maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1
        }
        scrollOffset = scrollOffset.coerceIn(0, max(0, items.size - maxVisible))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        if (event.action == MotionEvent.ACTION_DOWN) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (event.x < w * 0.1f || event.x > w * 0.9f || event.y < h * 0.15f || event.y > h * 0.85f) {
                hide()
                return true
            }

            val startY = h * 0.22f
            val clickedIndex = ((event.y - startY) / itemHeight).toInt() + scrollOffset
            if (clickedIndex > 0 && clickedIndex < items.size) {
                selectedIndex = clickedIndex
                performClick()
                toggleSelection()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (items.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(w * 0.1f, h * 0.15f, w * 0.9f, h * 0.85f, bgPaint)
        val x = w * 0.15f
        val startY = h * 0.22f
        val availableHeight = h * 0.7f
        val maxVisible = (availableHeight / itemHeight).toInt()
        for (i in 0 until maxVisible) {
            val itemIdx = scrollOffset + i
            if (itemIdx >= items.size) break
            val y = startY + (i * itemHeight)
            if (itemIdx == selectedIndex) {
                canvas.drawRect(w * 0.1f, y - 45f, w * 0.9f, y + 20f, selectionPaint)
            }
            val isChecked = selectedItems.contains(itemIdx - 1)
            val prefix = if (itemIdx == 0) "" else if (isChecked) "[X] " else "[ ] "
            textPaint.color = if (itemIdx == 0) Color.GRAY else Color.WHITE
            canvas.drawText(prefix + items[itemIdx], x, y, textPaint)
        }
        textPaint.color = Color.DKGRAY; textPaint.textSize = 28f
        canvas.drawText("[Space] Toggle | [Enter] Compile PDF | [Esc] Cancel", x, h * 0.82f, textPaint)
        textPaint.textSize = 36f
    }
}
