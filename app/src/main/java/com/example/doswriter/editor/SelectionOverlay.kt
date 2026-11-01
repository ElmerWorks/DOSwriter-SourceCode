package com.example.doswriter.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class SelectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class ThemeColors(val text: Int, val background: Int)
    private var themePreviews: List<ThemeColors>? = null
    private var fontPreviews: List<android.graphics.Typeface?>? = null
    private var cursorStyles: List<com.example.doswriter.model.EditorBuffer.CursorStyle?>? = null
    private var previewFontSize = 48f
    private var previewLineSpacing = 1.2f
    private var showPreviewMargins = true
    private var isSolidPreview = false

    private var items: List<String> = emptyList()
    private var selectedIndex = 0
    private var scrollOffset = 0
    private var onItemSelected: ((Int) -> Unit)? = null
    private var onCancelled: (() -> Unit)? = null
    private var menuTextColor = Color.WHITE

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
        color = Color.rgb(0, 255, 0) // Brighter green for e-ink contrast
        style = Paint.Style.FILL
    }

    private val previewBgPaint = Paint()
    private val previewMarginPaint = Paint()
    private val previewTextPaint = Paint().apply {
        textSize = 24f
        isAntiAlias = true
    }
    private val fontPreviewPaint = Paint().apply {
        isAntiAlias = true
    }

    private var itemHeight = 70f
    private var lastShowTime = 0L
    private var isEinkMode = false

    fun setMenuAppearance(textSize: Float, backgroundColor: Int, textColor: Int) {
        textPaint.textSize = textSize
        this.menuTextColor = textColor
        textPaint.color = textColor
        bgPaint.color = backgroundColor
        // Detect if background is light enough to be considered e-ink light
        val r = Color.red(backgroundColor)
        val g = Color.green(backgroundColor)
        val b = Color.blue(backgroundColor)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
        isEinkMode = luminance > 128

        // Adjust item height based on text size
        itemHeight = textSize * 2.0f
        invalidate()
    }

    fun show(title: String, options: List<String>, initialIndex: Int = 0, onSelect: (Int) -> Unit, onCancel: () -> Unit, previews: List<ThemeColors>? = null, isSolid: Boolean = false, fonts: List<android.graphics.Typeface?>? = null, fontSize: Float = 48f, lineSpacing: Float = 1.2f, cursorStyles: List<com.example.doswriter.model.EditorBuffer.CursorStyle?>? = null) {
        items = listOf(title.uppercase()) + options
        themePreviews = previews
        fontPreviews = fonts
        this.cursorStyles = cursorStyles
        previewFontSize = fontSize
        previewLineSpacing = lineSpacing
        isSolidPreview = isSolid
        selectedIndex = initialIndex + 1
        scrollOffset = 0
        updateScroll()
        onItemSelected = onSelect
        onCancelled = onCancel
        visibility = VISIBLE
        lastShowTime = System.currentTimeMillis()
        invalidate()
    }

    private fun updateScroll() {
        val maxVisible = calculateMaxVisible()
        if (maxVisible <= 0) return
        
        if (fontPreviews != null) {
            // Stationary selection bar logic: always keep selected item at Row 1
            // items[0] is the Title (Row 0)
            // items[selectedIndex] is the selected font (Row 1)
            scrollOffset = selectedIndex - 1
            return
        }

        if (selectedIndex < scrollOffset + 1 && selectedIndex > 0) {
            scrollOffset = selectedIndex - 1
        } else if (selectedIndex >= scrollOffset + maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1
        }
        scrollOffset = scrollOffset.coerceIn(0, max(0, items.size - maxVisible))
    }

    private fun calculateMaxVisible(): Int {
        if (fontPreviews != null) return 2 // Title + Selected
        val availableHeight = height * 0.7f
        return (availableHeight / itemHeight).toInt()
    }

    fun selectIndex(index: Int) {
        if (index + 1 < items.size) {
            selectedIndex = index + 1
            confirm()
        }
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

    fun confirm() {
        val callback = onItemSelected
        hide()
        if (selectedIndex > 0) {
            callback?.invoke(selectedIndex - 1)
        }
    }

    fun hide() {
        visibility = GONE
    }

    fun togglePreviewMargins() {
        showPreviewMargins = !showPreviewMargins
        invalidate()
    }

    fun getTitle(): String = if (items.isNotEmpty()) items[0] else ""
    fun getSelectedIndex(): Int = selectedIndex

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        if (event.action == MotionEvent.ACTION_DOWN) {
            // Debounce touches for 500ms after showing to prevent accidental e-ink "refresh" touches
            if (System.currentTimeMillis() - lastShowTime < 500) return true

            val w = width.toFloat()
            val h = height.toFloat()
            if (event.x < w * 0.1f || event.x > w * 0.9f || event.y < h * 0.15f || event.y > h * 0.85f) {
                hide()
                onCancelled?.invoke()
                return true
            }

            val startY = h * 0.22f
            val topOffset = startY - itemHeight * 0.65f
            val clickedRow = if (event.y < topOffset) -1 else ((event.y - topOffset) / itemHeight).toInt()
            
            val clickedIndex = if (fontPreviews != null) {
                if (clickedRow == 0) 0 
                else selectedIndex + (clickedRow - 1)
            } else {
                clickedRow + scrollOffset
            }

            if (clickedIndex > 0 && clickedIndex < items.size) {
                selectedIndex = clickedIndex
                invalidate()
                performClick()
                confirm()
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
        val maxVisible = calculateMaxVisible()
        
        if (fontPreviews != null) {
            // Stationary Selection Bar logic (Specific for Font Selection)
            // 1. Draw Title (Always items[0] at Row 0)
            textPaint.color = if (isEinkMode) Color.GRAY else Color.LTGRAY
            canvas.drawText(items[0], x, startY, textPaint)
            
            // 2. Draw items starting from selectedIndex at Row 1 onwards
            for (i in 1 until maxVisible) {
                val itemIdx = selectedIndex + (i - 1)
                if (itemIdx >= items.size) break
                
                val y = startY + (i * itemHeight)
                if (i == 1) { // Selection Bar is always Row 1
                    canvas.drawRect(w * 0.1f, y - (itemHeight * 0.65f), w * 0.9f, y + (itemHeight * 0.3f), selectionPaint)
                    textPaint.color = Color.BLACK
                } else {
                    textPaint.color = menuTextColor
                }
                canvas.drawText(items[itemIdx], x, y, textPaint)
            }
        } else {
            // Standard Scrolling logic
            for (i in 0 until maxVisible) {
                val itemIdx = scrollOffset + i
                if (itemIdx >= items.size) break
                val y = startY + (i * itemHeight)
                if (itemIdx == selectedIndex) {
                    canvas.drawRect(w * 0.1f, y - (itemHeight * 0.65f), w * 0.9f, y + (itemHeight * 0.3f), selectionPaint)
                    textPaint.color = Color.BLACK
                } else {
                    textPaint.color = if (itemIdx == 0) (if (isEinkMode) Color.GRAY else Color.LTGRAY) else menuTextColor
                }
                canvas.drawText(items[itemIdx], x, y, textPaint)

                // Draw Cursor Preview inline
                cursorStyles?.let { styles ->
                    val cursorIdx = itemIdx - 1
                    if (cursorIdx >= 0 && cursorIdx < styles.size) {
                        val cStyle = styles[cursorIdx]
                        if (cStyle != null) {
                            val previewX = x + textPaint.measureText(items[itemIdx]) + 60f
                            
                            val cPaint = Paint().apply {
                                color = Color.BLACK
                                style = Paint.Style.FILL
                            }
                            
                            val cursorWidth = 30f
                            val cursorHeight = itemHeight * 0.8f
                            val top = y - cursorHeight * 0.7f
                            val bottom = y + cursorHeight * 0.3f
                            
                            when (cStyle) {
                                com.example.doswriter.model.EditorBuffer.CursorStyle.THIN_CARET -> {
                                    canvas.drawRect(previewX, top, previewX + 4f, bottom, cPaint)
                                }
                                com.example.doswriter.model.EditorBuffer.CursorStyle.THICK_CARET -> {
                                    canvas.drawRect(previewX, top, previewX + 12f, bottom, cPaint)
                                }
                                com.example.doswriter.model.EditorBuffer.CursorStyle.BLOCK -> {
                                    canvas.drawRect(previewX, top, previewX + cursorWidth, bottom, cPaint)
                                }
                                com.example.doswriter.model.EditorBuffer.CursorStyle.UNDERLINE -> {
                                    canvas.drawRect(previewX, bottom - 8f, previewX + cursorWidth, bottom, cPaint)
                                }
                                com.example.doswriter.model.EditorBuffer.CursorStyle.INVERTED -> {
                                    if (itemIdx != selectedIndex) cPaint.color = if (isEinkMode) Color.BLACK else Color.WHITE
                                    canvas.drawRect(previewX, top, previewX + cursorWidth, bottom, cPaint)
                                }
                                com.example.doswriter.model.EditorBuffer.CursorStyle.RETRO_BLOCK -> {
                                    if (itemIdx != selectedIndex) cPaint.color = Color.argb(180, 0, 0, 0)
                                    canvas.drawRect(previewX, top, previewX + cursorWidth, bottom, cPaint)
                                }
                                com.example.doswriter.model.EditorBuffer.CursorStyle.DIM_CARET -> {
                                    cPaint.color = Color.argb(100, 0, 0, 0)
                                    canvas.drawRect(previewX, top, previewX + cursorWidth, bottom, cPaint)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Draw Font Preview Box
        fontPreviews?.let { fonts ->
            val previewIdx = selectedIndex - 1
            if (previewIdx >= 0 && previewIdx < fonts.size) {
                val tf = fonts[previewIdx]
                if (tf != null) {
                    val previewX = w * 0.12f
                    // Fix the preview box position to be independent of Menu Font Size
                    // This provides a much larger box (starting at 38% height)
                    val previewY = h * 0.38f
                    
                    val previewW = w * 0.76f
                    // End at 0.80h to create a gap between preview box and outer container (0.85h)
                    val previewH = (h * 0.80f) - previewY
                    
                    if (previewH > 50f) {
                        // Draw box background
                        previewBgPaint.color = if (isEinkMode) Color.rgb(230, 230, 230) else Color.rgb(40, 40, 40)
                        canvas.drawRect(previewX, previewY, previewX + previewW, previewY + previewH, previewBgPaint)
                        
                        fontPreviewPaint.typeface = tf
                        fontPreviewPaint.color = if (isEinkMode) Color.BLACK else Color.WHITE
                        
                        // Calculate optimal font size to fill the preview box
                        val numLines = 4
                        val padding = 40f // More padding for professional look
                        val availableH = previewH - (2 * padding)
                        
                        // Height formula: H = Size + (N-1) * (Size * Spacing)
                        val fillSize = availableH / (1f + (numLines - 1) * previewLineSpacing)
                        
                        var targetSize = fillSize
                        
                        // Check horizontal constraints
                        fontPreviewPaint.textSize = targetSize
                        val maxLineText = "AaBb DdGg IiLl1| Oo0"
                        val maxLineW = fontPreviewPaint.measureText(maxLineText)
                        val availableWForText = previewW - 60f
                        if (maxLineW > availableWForText) {
                            targetSize *= (availableWForText / maxLineW)
                        }
                        
                        fontPreviewPaint.textSize = targetSize
                        
                        val lines = listOf(
                            "AaBb DdGg IiLl1| Oo0",
                            "1234567890 @#$%^&*",
                            ".,;:!? ()[]{}<>/\\|\"'`~",
                            "The quick brown fox"
                        )
                        
                        val px = previewX + 30f
                        val stepY = targetSize * previewLineSpacing
                        var py = previewY + padding + (targetSize * 0.85f) // Adjust for baseline
                        
                        lines.forEach { line ->
                            if (py < previewY + previewH) {
                                canvas.drawText(line, px, py, fontPreviewPaint)
                                py += stepY
                            }
                        }
                    }
                }
            }
        }

        // Draw Theme Preview if available
        themePreviews?.let { previews ->
            val previewIdx = selectedIndex - 1
            if (previewIdx >= 0 && previewIdx < previews.size) {
                val theme = previews[previewIdx]
                if (Color.alpha(theme.background) == 0 && Color.alpha(theme.text) == 0) return@let

                val previewX = w * 0.55f
                val previewY = h * 0.35f
                val previewW = w * 0.3f
                val previewH = h * 0.3f

                // Outer Margin
                val bgColor = theme.background
                val r = Color.red(bgColor)
                val g = Color.green(bgColor)
                val b = Color.blue(bgColor)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
                val marginColor = if (luminance > 128) Color.argb(40, 0, 0, 0) else Color.argb(40, 255, 255, 255)
                
                // Draw actual background first
                previewBgPaint.color = theme.background
                canvas.drawRect(previewX, previewY, previewX + previewW, previewY + previewH, previewBgPaint)
                
                if (isSolidPreview) return@let

                // Draw margin overlay (non-overlapping rectangles)
                if (showPreviewMargins) {
                    previewMarginPaint.color = marginColor
                    val marginSize = previewW * 0.15f
                    
                    // Top and Bottom (Full width)
                    canvas.drawRect(previewX, previewY, previewX + previewW, previewY + marginSize, previewMarginPaint)
                    canvas.drawRect(previewX, previewY + previewH - marginSize, previewX + previewW, previewY + previewH, previewMarginPaint)
                    
                    // Sides (Only between Top and Bottom to avoid double-overlap corners)
                    canvas.drawRect(previewX, previewY + marginSize, previewX + marginSize, previewY + previewH - marginSize, previewMarginPaint)
                    canvas.drawRect(previewX + previewW - marginSize, previewY + marginSize, previewX + previewW, previewY + previewH - marginSize, previewMarginPaint)
                }

                // Text
                previewTextPaint.color = theme.text
                val text = if (showPreviewMargins) {
                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
                } else {
                    "Vox audita perit, littera scripta manet. Silentium mentem ad verba clara ducit."
                }
                
                val marginOffset = if (showPreviewMargins) previewW * 0.15f else 0f
                
                // Simple word wrap for preview
                var ty = previewY + marginOffset + 40f
                val words = text.split(" ")
                var line = ""
                for (word in words) {
                    val testLine = if (line.isEmpty()) word else "$line $word"
                    if (previewTextPaint.measureText(testLine) < previewW - 2 * marginOffset) {
                        line = testLine
                    } else {
                        canvas.drawText(line, previewX + marginOffset + 10f, ty, previewTextPaint)
                        ty += 35f
                        line = word
                    }
                }
                canvas.drawText(line, previewX + marginOffset + 10f, ty, previewTextPaint)
            }
        }

        // Help text at bottom
        val title = getTitle().uppercase()
        val help = when {
            title == "BOOKMARKS" -> "[Enter] Go to | [D/Del] Delete | [Esc] Cancel"
            title.startsWith("MANAGE FOLDERS") || title.startsWith("WORKING FOLDERS") -> "[D/Del] Unlink | [Esc] Back"
            else -> "[Enter] Select | [Esc] Cancel"
        }
        
        textPaint.color = if (isEinkMode) Color.GRAY else Color.DKGRAY
        val oldSize = textPaint.textSize
        textPaint.textSize = oldSize * 0.7f
        canvas.drawText(help, x, h * 0.83f, textPaint)
        textPaint.textSize = oldSize
    }
}
