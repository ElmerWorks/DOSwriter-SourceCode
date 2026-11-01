package com.example.doswriter.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

class HelpOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class RenderedBlock(
        val layout: StaticLayout?, 
        val indent: Float, 
        val image: Bitmap? = null, 
        val error: String? = null,
        val blockHeight: Float = 0f,
        val tableRows: List<List<StaticLayout>>? = null,
        val tableColWidths: List<Float>? = null
    )

    private var renderedBlocks: List<RenderedBlock> = emptyList()
    private val imageCache = mutableMapOf<String, Bitmap>()
    private val imageRect = RectF()
    
    private var scrollOffset = 0f
    private var horizontalOffset = 0f
    private var totalHeight = 0f
    private var scaleFactor = 1f
    
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 3.0f)
            invalidate()
            return true
        }
    })
    
    private val bgPaint = Paint().apply {
        color = Color.rgb(5, 5, 5)
        style = Paint.Style.FILL
    }

    private val baseTextPaint = TextPaint().apply {
        color = Color.LTGRAY
        textSize = 32f
        isAntiAlias = true
        typeface = Typeface.SANS_SERIF
    }

    private val h1TextPaint = TextPaint(baseTextPaint).apply {
        color = Color.WHITE
        textSize = 48f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val h2TextPaint = TextPaint(baseTextPaint).apply {
        color = Color.CYAN
        textSize = 40f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val h3TextPaint = TextPaint(baseTextPaint).apply {
        color = Color.LTGRAY
        textSize = 36f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private var rawText: String = ""

    private fun getHelpBitmap(path: String): Bitmap? {
        // Sanitize path: replace backslashes with forward slashes for Android compatibility
        val cleanPath = path.replace("\\", "/")
        
        if (imageCache.containsKey(cleanPath)) return imageCache[cleanPath]
        try {
            // 1. Try Assets
            try {
                val inputStream: InputStream = context.assets.open(cleanPath)
                val bmp = BitmapFactory.decodeStream(inputStream)
                if (bmp != null) {
                    imageCache[cleanPath] = bmp
                    return bmp
                }
            } catch (e: Exception) {}

            // 2. Try absolute path or URI
            var uriPath = cleanPath
            if (uriPath.startsWith("//")) uriPath = uriPath.substring(1)
            
            var uri = Uri.parse(uriPath)
            if (uri.scheme == null) uri = Uri.parse("file://$uriPath")
            
            val stream = context.contentResolver.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(stream)
            if (bmp != null) {
                imageCache[cleanPath] = bmp
                return bmp
            }
        } catch (e: Exception) {
            // 3. Raw file fallback
            try {
                val rawPath = if (cleanPath.startsWith("file://")) cleanPath.substring(7) else cleanPath
                val bmp = BitmapFactory.decodeFile(rawPath)
                if (bmp != null) {
                    imageCache[cleanPath] = bmp
                    return bmp
                }
            } catch (e2: Exception) {}
        }
        return null
    }

    fun setMenuAppearance(textSize: Float, backgroundColor: Int, textColor: Int) {
        bgPaint.color = backgroundColor
        baseTextPaint.color = if (textColor == Color.BLACK) Color.rgb(40, 40, 40) else Color.LTGRAY
        baseTextPaint.textSize = textSize * 0.9f
        
        h1TextPaint.color = textColor
        h1TextPaint.textSize = textSize * 1.3f
        
        h2TextPaint.color = if (textColor == Color.BLACK) Color.rgb(0, 80, 150) else Color.CYAN
        h2TextPaint.textSize = textSize * 1.1f
        
        h3TextPaint.color = if (textColor == Color.BLACK) Color.rgb(60, 60, 60) else Color.LTGRAY
        h3TextPaint.textSize = textSize * 1.0f
        
        if (rawText.isNotEmpty()) {
            prepareRendering(rawText)
        }
        invalidate()
    }

    private var pages: List<String> = emptyList()
    private var currentPage = 0
    var onPageChanged: ((Int, Int) -> Unit)? = null
    var onExitRequested: (() -> Unit)? = null

    fun pagesCount(): Int = pages.size

    fun show(text: String) {
        showPages(listOf(text))
    }

    fun showPages(textPages: List<String>, startIndex: Int = 0) {
        pages = textPages
        currentPage = startIndex.coerceIn(0, max(0, pages.size - 1))
        visibility = VISIBLE
        renderCurrentPage()
    }

    private fun renderCurrentPage() {
        if (pages.isEmpty()) return
        rawText = pages[currentPage]
        post {
            prepareRendering(rawText)
            scrollOffset = 0f
            horizontalOffset = 0f
            scaleFactor = 1f
            invalidate()
            onPageChanged?.invoke(currentPage, pages.size)
        }
    }

    fun nextPage(): Boolean {
        if (currentPage < pages.size - 1) {
            currentPage++
            renderCurrentPage()
            return true
        }
        return false
    }

    fun prevPage(): Boolean {
        if (currentPage > 0) {
            currentPage--
            renderCurrentPage()
            return true
        }
        return false
    }

    private fun prepareRendering(text: String) {
        val result = mutableListOf<RenderedBlock>()
        val maxWidth = width - 80f // Margin
        if (maxWidth <= 0) return

        val tableLines = mutableListOf<String>()

        fun processRegularLine(line: String, currentMaxWidth: Float, targetList: MutableList<RenderedBlock>) {
            var textPaint = baseTextPaint
            var indent = 0f
            var processedLine = line.trimEnd()

            // 1. Image Check with optional |Width parameter
            val imgMatch = Regex("!\\[(.*?)\\]\\((.*?)\\)").find(line)
            if (imgMatch != null) {
                val fullPath = imgMatch.groupValues[2]
                val path = fullPath.substringBefore("|")
                val widthPercent = fullPath.substringAfter("|", "100").toFloatOrNull() ?: 100f
                
                val bmp = getHelpBitmap(path)
                if (bmp != null) {
                    val targetWidth = currentMaxWidth * (widthPercent / 100f)
                    val scale = targetWidth / bmp.width
                    val h = bmp.height * scale
                    val imgIndent = (currentMaxWidth - targetWidth) / 2f
                    targetList.add(RenderedBlock(null, imgIndent, bmp, null, h))
                } else {
                    targetList.add(RenderedBlock(null, 0f, null, "Image not found: $path", 40f))
                }
                return
            }

            // 2. Horizontal Rule Check
            if (line.trim() == "---" || line.trim() == "***" || line.trim() == "___") {
                targetList.add(RenderedBlock(null, 0f, null, "---", 30f))
                return
            }

            // 3. Header and list checks
            if (processedLine.startsWith("### ")) {
                textPaint = h3TextPaint
                processedLine = processedLine.substring(4)
            } else if (processedLine.startsWith("## ")) {
                textPaint = h2TextPaint
                processedLine = processedLine.substring(3)
            } else if (processedLine.startsWith("# ")) {
                textPaint = h1TextPaint
                processedLine = processedLine.substring(2)
            } else if (processedLine.startsWith("- ")) {
                indent = 30f
                processedLine = "• " + processedLine.substring(2)
            } else if (processedLine.startsWith("> ")) {
                indent = 40f
                processedLine = "<i>" + processedLine.substring(2) + "</i>"
            } else if (processedLine.startsWith(": ")) {
                indent = 40f
                processedLine = processedLine.substring(2)
            }

            if (processedLine.isEmpty()) {
                val layout = StaticLayout("", textPaint, currentMaxWidth.toInt(), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
                targetList.add(RenderedBlock(layout, 0f, null, null, layout.height.toFloat()))
            } else {
                // Convert Markdown ** to <b>
                val htmlLine = processedLine
                    .replace(Regex("\\*\\*\\*(.*?)\\*\\*\\*"), "<b><i>$1</i></b>")
                    .replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
                    .replace(Regex("\\*(.*?)\\*"), "<i>$1</i>")
                    .replace(Regex("___(.*?)___"), "<b><i>$1</i></b>")
                    .replace(Regex("__(.*?)__"), "<b>$1</b>")
                    .replace(Regex("_(.*?)_"), "<i>$1</i>")
                    .replace(Regex("~~(.*?)~~"), "<strike>$1</strike>")
                    .replace(Regex("`(.*?)`"), "<tt>$1</tt>")
                    .replace(Regex("__(.*?)__"), "<b>$1</b>")
                    .replace(Regex("_(.*?)_"), "<i>$1</i>")
                
                val spanned = if (android.os.Build.VERSION.SDK_INT >= 24) {
                    Html.fromHtml(htmlLine, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(htmlLine)
                }
                
                val blockWidth = (currentMaxWidth - indent).toInt().coerceAtLeast(1)
                val layout = StaticLayout(spanned, textPaint, blockWidth, Layout.Alignment.ALIGN_NORMAL, 1.1f, 8f, false)
                targetList.add(RenderedBlock(layout, indent, null, null, layout.height.toFloat()))
            }
        }

        fun flushTable() {
            if (tableLines.isEmpty()) return
            val tableBlock = parseTable(tableLines, maxWidth)
            if (tableBlock != null) {
                result.add(tableBlock)
            } else {
                tableLines.forEach { processRegularLine(it, maxWidth, result) }
            }
            tableLines.clear()
        }

        text.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("|") && trimmed.count { it == '|' } >= 2) {
                tableLines.add(line)
            } else {
                flushTable()
                processRegularLine(line, maxWidth, result)
            }
        }
        flushTable()
        
        renderedBlocks = result
        totalHeight = renderedBlocks.sumOf { it.blockHeight.toDouble() }.toFloat()
    }

    private fun parseTable(tableLines: List<String>, maxWidth: Float): RenderedBlock? {
        if (tableLines.size < 2) return null
        
        val separatorIdx = tableLines.indexOfFirst { it.contains("---") && it.contains("|") }
        if (separatorIdx == -1) return null

        val rowsData = mutableListOf<List<String>>()
        tableLines.forEachIndexed { index, line ->
            if (index == separatorIdx) return@forEachIndexed
            
            val cells = line.split("|")
                .toMutableList()
            
            if (cells.first().isEmpty()) cells.removeAt(0)
            if (cells.isNotEmpty() && cells.last().isEmpty()) cells.removeAt(cells.size - 1)
            
            val trimmedCells = cells.map { it.trim() }
            if (trimmedCells.isNotEmpty()) {
                rowsData.add(trimmedCells)
            }
        }

        if (rowsData.isEmpty()) return null
        val numCols = rowsData.maxOf { it.size }
        if (numCols == 0) return null

        val colWidth = maxWidth / numCols
        val padding = 12f
        val cellInnerWidth = (colWidth - 2 * padding).toInt().coerceAtLeast(1)

        val renderedRows = mutableListOf<List<StaticLayout>>()
        var tableHeight = 0f

        rowsData.forEachIndexed { rowIndex, row ->
            val renderedRow = mutableListOf<StaticLayout>()
            var maxRowHeight = 0f
            
            val isHeader = rowIndex == 0 && separatorIdx > 0
            val paint = if (isHeader) {
                TextPaint(baseTextPaint).apply { isFakeBoldText = true; color = h1TextPaint.color }
            } else {
                baseTextPaint
            }

            for (i in 0 until numCols) {
                val text = if (i < row.size) row[i] else ""
                val htmlText = text
                    .replace(Regex("\\*\\*\\*(.*?)\\*\\*\\*"), "<b><i>$1</i></b>")
                    .replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
                    .replace(Regex("\\*(.*?)\\*"), "<i>$1</i>")
                    .replace(Regex("___(.*?)___"), "<b><i>$1</i></b>")
                    .replace(Regex("__(.*?)__"), "<b>$1</b>")
                    .replace(Regex("_(.*?)_"), "<i>$1</i>")
                    .replace(Regex("~~(.*?)~~"), "<strike>$1</strike>")
                    .replace(Regex("`(.*?)`"), "<tt>$1</tt>")
                
                val spanned = if (android.os.Build.VERSION.SDK_INT >= 24) {
                    Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(htmlText)
                }
                
                val layout = StaticLayout(spanned, paint, cellInnerWidth, Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, false)
                renderedRow.add(layout)
                maxRowHeight = max(maxRowHeight, layout.height.toFloat() + 2 * padding)
            }
            renderedRows.add(renderedRow)
            tableHeight += maxRowHeight
        }

        return RenderedBlock(null, 0f, null, null, tableHeight + 20f, renderedRows, List(numCols) { colWidth })
    }

    fun hide() {
        visibility = GONE
        val callback = onExitRequested
        onExitRequested = null // Clear to avoid loops
        callback?.invoke()
    }

    fun scrollUp() {
        scrollOffset = max(0f, scrollOffset - 100f / scaleFactor)
        invalidate()
    }

    fun scrollDown() {
        scrollOffset = min(max(0f, totalHeight - height / scaleFactor), scrollOffset + 100f / scaleFactor)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        
        scaleGestureDetector.onTouchEvent(event)
        if (scaleGestureDetector.isInProgress) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                if (Math.abs(dx) > 10 || Math.abs(dy) > 10 || isDragging) {
                    isDragging = true
                    
                    scrollOffset -= dy / scaleFactor
                    scrollOffset = scrollOffset.coerceIn(0f, max(0f, totalHeight - height / scaleFactor))
                    
                    horizontalOffset -= dx / scaleFactor
                    horizontalOffset = horizontalOffset.coerceIn(0f, max(0f, width - width / scaleFactor))
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDragging = false
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && 
            (event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER) || event.isFromSource(InputDevice.SOURCE_MOUSE))) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (vScroll != 0f) {
                scrollOffset -= vScroll * 150f / scaleFactor
                scrollOffset = scrollOffset.coerceIn(0f, max(0f, totalHeight - height / scaleFactor))
                invalidate()
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        if (renderedBlocks.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        canvas.save()
        canvas.scale(scaleFactor, scaleFactor)

        val startX = 40f - horizontalOffset
        var currentY = 60f - scrollOffset

        renderedBlocks.forEach { block ->
            if (currentY + block.blockHeight > 0 && currentY < (h / scaleFactor)) {
                if (block.image != null) {
                    val imgW = (w / scaleFactor - 80f) - (block.indent * 2)
                    val imgX = startX + block.indent
                    imageRect.set(imgX, currentY, imgX + imgW, currentY + block.blockHeight)
                    canvas.drawBitmap(block.image, null, imageRect, null)
                } else if (block.error == "---") {
                    val lineW = w / scaleFactor - 80f
                    canvas.drawLine(startX, currentY + 15f, startX + lineW, currentY + 15f, baseTextPaint)
                } else if (block.error != null) {
                    val oldColor = baseTextPaint.color
                    baseTextPaint.color = Color.RED
                    canvas.drawText(block.error, startX, currentY + 30f, baseTextPaint)
                    baseTextPaint.color = oldColor
                } else if (block.tableRows != null && block.tableColWidths != null) {
                    var tableY = currentY
                    val borderPaint = Paint().apply { color = Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 1f }
                    val padding = 12f
                    
                    block.tableRows.forEach { row ->
                        var maxRowH = 0f
                        row.forEach { if (it.height + 2 * padding > maxRowH) maxRowH = it.height + 2 * padding }
                        
                        var currentX = startX
                        row.forEachIndexed { colIdx, cellLayout ->
                            val cw = block.tableColWidths[colIdx]
                            canvas.drawRect(currentX, tableY, currentX + cw, tableY + maxRowH, borderPaint)
                            
                            canvas.save()
                            canvas.translate(currentX + padding, tableY + padding)
                            cellLayout.draw(canvas)
                            canvas.restore()
                            
                            currentX += cw
                        }
                        tableY += maxRowH
                    }
                } else if (block.layout != null) {
                    canvas.save()
                    canvas.translate(startX + block.indent, currentY)
                    block.layout.draw(canvas)
                    canvas.restore()
                }
            }
            currentY += block.blockHeight
        }
        canvas.restore()
    }
}
