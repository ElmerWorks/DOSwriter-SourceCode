package com.example.minimaltexteditor.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.minimaltexteditor.logic.OutlineManager
import kotlin.math.max

class OutlineOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { SELECT, SETUP }
    private var mode = Mode.SELECT
    private var manager: OutlineManager? = null
    
    private val items = mutableListOf<DisplayItem>()
    private var selectedIndex = 0
    private var scrollOffset = 0
    
    private var onFileSelected: ((String, String, Boolean) -> Unit)? = null
    private var onSetupAction: ((OutlineManager.OutlineItem?, Action) -> Unit)? = null

    enum class Action { ADD_FOLDER, ADD_CONTAINER, DELETE, LINK, LINK_REMOTE, RENAME, UNLINK }

    private val bgPaint = Paint().apply {
        color = Color.rgb(10, 10, 10) 
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 38f 
        isAntiAlias = true
    }

    private val selectionPaint = Paint().apply {
        color = Color.rgb(0, 150, 0) 
        style = Paint.Style.FILL
    }

    private val itemHeight = 70f

    private data class DisplayItem(
        val item: OutlineManager.OutlineItem,
        val isFolder: Boolean,
        val depth: Int
    )

    fun show(mode: Mode, manager: OutlineManager, onSelect: (String, String, Boolean) -> Unit, onSetup: (OutlineManager.OutlineItem?, Action) -> Unit) {
        this.mode = mode
        this.manager = manager
        this.onFileSelected = onSelect
        this.onSetupAction = onSetup
        rebuildItems()
        selectedIndex = if (items.isNotEmpty()) 0 else -1
        scrollOffset = 0
        visibility = VISIBLE
        invalidate()
    }

    private fun rebuildItems() {
        items.clear()
        manager?.getFolders()?.forEach { folder ->
            items.add(DisplayItem(folder, true, 0))
            folder.containers.forEach { container ->
                items.add(DisplayItem(container, false, 1))
            }
        }
    }

    fun moveUp() {
        if (selectedIndex > 0) {
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

    fun confirm() {
        if (selectedIndex < 0 || selectedIndex >= items.size) return
        val displayItem = items[selectedIndex]
        val item = displayItem.item

        if (mode == Mode.SELECT) {
            if (item is OutlineManager.OutlineItem.FileContainer) {
                if (item.remotePath != null) {
                    onFileSelected?.invoke(item.remotePath!!, item.fileName ?: "remote.txt", true)
                    hide()
                } else if (item.linkedUri != null) {
                    onFileSelected?.invoke(item.linkedUri!!, item.fileName ?: "file.txt", false)
                    hide()
                }
            }
        } else {
            if (item is OutlineManager.OutlineItem.FileContainer) {
                onSetupAction?.invoke(item, Action.LINK)
            } else {
                onSetupAction?.invoke(item, Action.RENAME)
            }
        }
    }

    fun triggerAction(action: Action) {
        val selectedItem = if (selectedIndex >= 0 && selectedIndex < items.size) items[selectedIndex].item else null
        onSetupAction?.invoke(selectedItem, action)
    }

    fun refresh() {
        rebuildItems()
        invalidate()
    }

    fun hide() {
        visibility = GONE
    }

    private fun updateScroll() {
        val maxVisible = calculateMaxVisible()
        if (maxVisible <= 0) return

        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex
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
            // Narrower margins for touch detection too
            if (event.x < w * 0.05f || event.x > w * 0.95f || event.y < h * 0.05f || event.y > h * 0.95f) {
                hide()
                return true
            }
            performClick()
            val startY = h * 0.16f
            val clickedIndex = ((event.y - startY) / itemHeight).toInt() + scrollOffset
            if (clickedIndex >= 0 && clickedIndex < items.size) {
                selectedIndex = clickedIndex
                invalidate()
                if (mode == Mode.SELECT) confirm()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun calculateMaxVisible(): Int {
        val availableHeight = height * 0.76f 
        return (availableHeight / itemHeight).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        
        // Background box with small margin at screen edges
        canvas.drawRect(w * 0.05f, h * 0.05f, w * 0.95f, h * 0.95f, bgPaint)

        if (items.isEmpty() && mode == Mode.SELECT) {
            textPaint.color = Color.WHITE
            canvas.drawText("No items. Press Ctrl+F12 to setup.", w * 0.3f, h * 0.5f, textPaint)
            return
        }

        val startX = w * 0.10f
        val startY = h * 0.16f 
        val maxVisible = calculateMaxVisible()

        val title = if (mode == Mode.SELECT) "OUTLINE SELECTION" else "OUTLINE SETUP"
        textPaint.color = Color.GRAY
        // Title at the top of the taller box
        canvas.drawText(title, startX, h * 0.10f, textPaint)

        for (i in 0 until maxVisible) {
            val itemIdx = scrollOffset + i
            if (itemIdx >= items.size) break
            
            val displayItem = items[itemIdx]
            val y = startY + (i * itemHeight)

            if (itemIdx == selectedIndex) {
                canvas.drawRect(w * 0.05f, y - 45f, w * 0.95f, y + 20f, selectionPaint)
            }
            
            val depthPadding = displayItem.depth * 40f
            textPaint.color = if (displayItem.isFolder) Color.CYAN else Color.WHITE
            
            val icon = if (displayItem.isFolder) "📁 " else "📄 "
            var label = icon + displayItem.item.name
            
            if (!displayItem.isFolder) {
                val container = displayItem.item as OutlineManager.OutlineItem.FileContainer
                if (container.linkedUri == null && container.remotePath == null) {
                    label += " (Empty)"
                    textPaint.color = Color.DKGRAY
                } else {
                    label += " [${container.fileName}]"
                }
            }
            
            canvas.drawText(label, startX + depthPadding, y, textPaint)
        }

        // Legend at the bottom of the taller box
        textPaint.color = Color.LTGRAY // Brighter color for better contrast
        textPaint.textSize = 24f
        val legend = if (mode == Mode.SELECT) "[Enter] Load | [Esc] Exit" 
                    else "[F] Folder | [C] Container | [D] Delete | [L] Link | [K] Link Remote | [U] Unlink | [R] Rename | [Esc] Exit"
        canvas.drawText(legend, startX, h * 0.92f, textPaint)
        textPaint.textSize = 38f
    }
}
