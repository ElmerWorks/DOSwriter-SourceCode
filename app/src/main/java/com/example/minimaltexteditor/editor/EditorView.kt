package com.example.minimaltexteditor.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.minimaltexteditor.model.EditorBuffer
import java.util.regex.Pattern
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class EditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var buffer: EditorBuffer? = null
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }

    private val cursorPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }

    private val selectionPaint = Paint().apply {
        color = Color.argb(100, 0, 255, 0)
        style = Paint.Style.FILL
    }

    private val backgroundPaint = Paint().apply {
        color = Color.BLACK
    }

    private val bookmarkPaint = Paint().apply {
        color = Color.CYAN
        textSize = 30f
        isAntiAlias = true
    }

    private val foldPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 40f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    private val marginPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    // Syntax Highlighting Paints (VS Code standard colors)
    private val syntaxTagPaint = Paint(textPaint).apply { color = Color.rgb(86, 156, 214) } // Blue
    private val syntaxAttrPaint = Paint(textPaint).apply { color = Color.rgb(156, 220, 254) } // Light Blue
    private val syntaxValuePaint = Paint(textPaint).apply { color = Color.rgb(206, 145, 120) } // Orange/Brown
    private val syntaxCommentPaint = Paint(textPaint).apply { color = Color.rgb(106, 153, 85) } // Green
    private val syntaxKeywordPaint = Paint(textPaint).apply { color = Color.rgb(197, 134, 192) } // Purple
    private val syntaxSelectorPaint = Paint(textPaint).apply { color = Color.rgb(215, 186, 125) } // Yellow/Tan

    private val imageMatrix = Matrix()

    private var charWidth = 0f
    private var lineHeight = 0f
    private var scrollYOffset = 0f
    private var shouldScrollToCursor = false
    
    private var imageBitmap: Bitmap? = null
    private var lastImageUri: String? = null
    private var pdfRenderer: PdfRenderer? = null
    private var pdfFileDescriptor: ParcelFileDescriptor? = null
    private var lastPdfUri: String? = null
    private var pdfPageBitmaps = mutableMapOf<Int, Bitmap>()
    private var totalPdfHeight = 0f

    private var isTypewriterActive = false
    private var typewriterTargetY = 0f
    
    private var viewerImageUri: String? = null
    private var isViewerRemote = false
    private var viewerBitmap: Bitmap? = null
    private var lastViewerUri: String? = null
    private var viewerScale = 1f
    private var viewerPanX = 0f
    private var viewerPanY = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val b = buffer ?: return true
            if (b.imageUri == null) return true
            b.imageScale *= detector.scaleFactor
            b.imageScale = b.imageScale.coerceIn(0.1f, 10f)
            invalidate()
            return true
        }
    })

    var onCursorMoved: (() -> Unit)? = null
    var onRightClick: ((Float, Float) -> Unit)? = null
    var onNextImage: (() -> Unit)? = null
    var onPrevImage: (() -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        updatePaintMetrics()
    }

    override fun onCheckIsTextEditor(): Boolean = buffer?.useVirtualInput ?: false

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (buffer?.useVirtualInput != true) return null
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return BaseInputConnection(this, false)
    }

    private fun updatePaintMetrics() {
        val text = "A"
        charWidth = textPaint.measureText(text)
        val spacing = buffer?.lineSpacing ?: 1.2f
        val standardHeight = textPaint.fontMetrics.bottom - textPaint.fontMetrics.top
        lineHeight = standardHeight * spacing
        
        listOf(syntaxTagPaint, syntaxAttrPaint, syntaxValuePaint, syntaxCommentPaint, syntaxKeywordPaint, syntaxSelectorPaint).forEach {
            it.textSize = textPaint.textSize
            it.typeface = textPaint.typeface
        }
    }

    fun setBuffer(buffer: EditorBuffer) {
        this.buffer = buffer
        textPaint.textSize = buffer.fontSize
        applyFontType(buffer.fontType)
        updatePaintMetrics()
        shouldScrollToCursor = true
        
        // Restart blink for the new buffer's settings
        removeCallbacks(blinkRunnable)
        if (buffer.cursorSettings.blinkEnabled) {
            blinkOn = true
            post(blinkRunnable)
        }
        
        pdfPageBitmaps.values.forEach { it.recycle() }
        pdfPageBitmaps.clear()
        pdfRenderer?.close()
        pdfRenderer = null
        pdfFileDescriptor?.close()
        pdfFileDescriptor = null
        lastPdfUri = null
        imageBitmap = null
        lastImageUri = null

        invalidate()
    }

    private fun applyFontType(type: String) {
        textPaint.typeface = when (type.uppercase()) {
            "SERIF" -> Typeface.SERIF
            "SANS_SERIF" -> Typeface.SANS_SERIF
            "MONOSPACE" -> Typeface.MONOSPACE
            "COURIER NEW", "COURIER" -> Typeface.create("monospace", Typeface.NORMAL)
            "CONSOLAS" -> Typeface.create("monospace", Typeface.NORMAL)
            "LUCIDA CONSOLE" -> Typeface.create("monospace", Typeface.NORMAL)
            "ROBOTO MONO" -> Typeface.create("monospace", Typeface.NORMAL)
            "TIMES NEW ROMAN" -> Typeface.create("serif", Typeface.NORMAL)
            "GEORGIA" -> Typeface.create("serif", Typeface.NORMAL)
            "ARIAL" -> Typeface.create("sans-serif", Typeface.NORMAL)
            "HELVETICA" -> Typeface.create("sans-serif", Typeface.NORMAL)
            "VERDANA" -> Typeface.create("sans-serif", Typeface.NORMAL)
            "TREBUCHET" -> Typeface.create("sans-serif", Typeface.NORMAL)
            "NOTO SANS" -> Typeface.create("sans-serif", Typeface.NORMAL)
            else -> Typeface.create(type, Typeface.NORMAL)
        }
    }

    fun setFontType(type: String) {
        buffer?.fontType = type
        applyFontType(type)
        updatePaintMetrics()
        invalidate()
    }

    fun setLineSpacing(spacing: Float) {
        buffer?.lineSpacing = spacing
        updatePaintMetrics()
        invalidate()
    }

    fun scrollToCursor() {
        shouldScrollToCursor = true
        invalidate()
    }

    fun setTextColor(color: Int) {
        textPaint.color = color
        updatePaintMetrics()
        invalidate()
    }

    fun setCursorColor(color: Int) {
        cursorPaint.color = color
        invalidate()
    }

    override fun setBackgroundColor(color: Int) {
        backgroundPaint.color = color
        invalidate()
    }

    fun setFontSize(size: Float) {
        textPaint.textSize = size
        buffer?.fontSize = size
        updatePaintMetrics()
        invalidate()
    }

    fun getFontSize(): Float = textPaint.textSize

    fun setSideMargin(marginPx: Float) {
        buffer?.sideMargin = marginPx
        invalidate()
    }

    fun getSideMargin(): Float = buffer?.sideMargin ?: 60f

    fun setTopBottomMargin(marginPx: Float) {
        buffer?.topBottomMargin = marginPx
        invalidate()
    }

    fun getTopBottomMargin(): Float = buffer?.topBottomMargin ?: 60f

    fun setVerticalOffset(offset: Float) {
        buffer?.verticalOffset = offset
        invalidate()
    }

    fun getVerticalOffset(): Float = buffer?.verticalOffset ?: 0f

    fun getIndexAtViewportCenter(): Int {
        return getIndexAt(width / 2f, height / 2f)
    }

    fun estimateMetrics(size: Float): Pair<Float, Float> {
        val p = Paint(textPaint).apply { textSize = size }
        val charW = p.measureText("A")
        val spacing = buffer?.lineSpacing ?: 1.2f
        val lineH = (p.fontMetrics.bottom - p.fontMetrics.top) * spacing
        return Pair(charW, lineH)
    }

    private val blinkRunnable = object : Runnable {
        override fun run() {
            blinkOn = !blinkOn
            invalidate()
            val b = buffer ?: return
            val interval = b.cursorSettings.blinkIntervalMs
            // Keep blinking if either finding cursor or standard blink is enabled
            if (isFindingCursor || b.cursorSettings.blinkEnabled) {
                postDelayed(this, interval)
            }
        }
    }

    private var isFindingCursor = false
    private var blinkOn = false

    fun startFindCursor() {
        isFindingCursor = true
        blinkOn = true
        removeCallbacks(blinkRunnable)
        post(blinkRunnable)
    }

    fun stopFindCursor() {
        isFindingCursor = false
        removeCallbacks(blinkRunnable)
        invalidate()
    }

    fun toggleTypewriterMode() {
        isTypewriterActive = !isTypewriterActive
        if (isTypewriterActive) {
            val coords = getCursorCoordinates(buffer?.cursorPosition ?: 0)
            typewriterTargetY = coords.second
        }
        invalidate()
    }

    fun isTypewriterActive(): Boolean = isTypewriterActive

    fun recenterTypewriter() {
        if (!isTypewriterActive) isTypewriterActive = true
        val coords = getCursorCoordinates(buffer?.cursorPosition ?: 0)
        typewriterTargetY = coords.second
        invalidate()
    }

    fun setViewerImage(uri: String, isRemote: Boolean) {
        viewerImageUri = uri
        isViewerRemote = isRemote
        viewerScale = 1f
        viewerPanX = 0f
        viewerPanY = 0f
        invalidate()
    }

    fun clearViewerImage() {
        viewerImageUri = null
        viewerBitmap = null
        lastViewerUri = null
        invalidate()
    }

    fun isViewerActive(): Boolean = viewerImageUri != null

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_BUTTON_PRESS && 
            event.buttonState == MotionEvent.BUTTON_SECONDARY) {
            onRightClick?.invoke(event.x, event.y)
            return true
        }

        if (event.action == MotionEvent.ACTION_SCROLL && 
            (event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER) || event.isFromSource(InputDevice.SOURCE_MOUSE))) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (vScroll != 0f) {
                if (viewerImageUri != null) {
                    if (event.metaState and KeyEvent.META_CTRL_ON != 0) {
                        viewerScale *= (1f + vScroll * 0.1f)
                        viewerScale = viewerScale.coerceIn(0.1f, 10f)
                        invalidate()
                    } else {
                        if (vScroll > 0) onNextImage?.invoke() else onPrevImage?.invoke()
                    }
                    return true
                } else if (buffer?.imageUri != null) {
                    buffer?.let {
                        it.imageScale *= (1f + vScroll * 0.1f)
                        it.imageScale = it.imageScale.coerceIn(0.1f, 10f)
                        invalidate()
                    }
                } else if (buffer?.pdfUri != null) {
                    scrollYOffset -= vScroll * lineHeight * 5
                    scrollYOffset = scrollYOffset.coerceIn(0f, max(0f, totalPdfHeight - height))
                    invalidate()
                } else {
                    scrollYOffset -= vScroll * lineHeight * 3
                    scrollYOffset = max(0f, scrollYOffset)
                    shouldScrollToCursor = false
                    invalidate()
                }
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && 
            (event.buttonState == MotionEvent.BUTTON_SECONDARY || event.isButtonPressed(MotionEvent.BUTTON_SECONDARY))) {
            onRightClick?.invoke(event.x, event.y)
            return true
        }

        val b = buffer ?: return super.onTouchEvent(event)
        
        if (viewerImageUri != null) {
            scaleGestureDetector.onTouchEvent(event)
            if (!scaleGestureDetector.isInProgress) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (event.buttonState == MotionEvent.BUTTON_PRIMARY || event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) {
                            val dx = event.x - lastTouchX
                            val dy = event.y - lastTouchY
                            viewerPanX += dx
                            viewerPanY += dy
                            lastTouchX = event.x
                            lastTouchY = event.y
                            invalidate()
                        }
                    }
                }
            }
            return true
        }
        
        if (b.imageUri != null) {
            scaleGestureDetector.onTouchEvent(event)
            if (!scaleGestureDetector.isInProgress) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        b.imagePanX += dx
                        b.imagePanY += dy
                        lastTouchX = event.x
                        lastTouchY = event.y
                        invalidate()
                    }
                }
            }
            return true
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                performClick()
                val index = getIndexAt(event.x, event.y)
                buffer?.setCursor(index, false)
                invalidate()
                onCursorMoved?.invoke()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = getIndexAt(event.x, event.y)
                buffer?.setCursor(index, true)
                invalidate()
                onCursorMoved?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun getIndexAt(targetX: Float, targetY: Float): Int {
        val currentBuffer = buffer ?: return 0
        val text = currentBuffer.getText()
        val sideMargin = currentBuffer.sideMargin
        val topBottomMargin = currentBuffer.topBottomMargin
        val verticalOffset = currentBuffer.verticalOffset
        val folds = currentBuffer.folds
        val effectiveSideMargin = if (currentBuffer.showMargins) sideMargin + 2 * charWidth else sideMargin
        val availableHeight = height - 2 * topBottomMargin
        val fullLines = floor(availableHeight / lineHeight).coerceAtLeast(1f).toInt()
        val perfectHeight = fullLines * lineHeight
        val extraPadding = (availableHeight - perfectHeight) / 2
        val effectiveTop = topBottomMargin + extraPadding
        val xStart = effectiveSideMargin
        val yBase = effectiveTop + verticalOffset - textPaint.fontMetrics.top
        var x = xStart
        var y = yBase - scrollYOffset
        var i = 0
        var bestIndex = 0
        var foundLine = false
        val maxWidth = width - effectiveSideMargin

        while (i <= text.length) {
            val isAtEnd = i == text.length
            val lineTop = y + textPaint.fontMetrics.top
            if (targetY >= lineTop && targetY < lineTop + lineHeight) {
                foundLine = true
                if (targetX < x + charWidth / 2f) return i
                bestIndex = i
            }
            if (isAtEnd) break
            if (y > targetY + lineHeight) break
            val fold = folds.find { it.start == i }
            if (fold != null) {
                x += charWidth
                i = fold.end + 1
            } else {
                val char = text[i]
                if (char == '\n') {
                    if (foundLine) return i 
                    x = xStart
                    y += lineHeight
                } else {
                    if (!char.isWhitespace()) {
                        val wordEnd = findWordEnd(text, i)
                        val wordWidth = (wordEnd - i) * charWidth
                        if (x > xStart && x + wordWidth > maxWidth) {
                            if (foundLine) return i
                            x = xStart
                            y += lineHeight
                        }
                    }
                    x += charWidth
                }
                i++
            }
            if (x > maxWidth) {
                if (foundLine) return i
                x = xStart
                y += lineHeight
            }
        }
        return if (foundLine) bestIndex else (if (targetY < height / 2f) 0 else text.length)
    }

    private fun findWordEnd(text: String, start: Int): Int {
        var end = start
        while (end < text.length && !text[end].isWhitespace() && text[end] != '\n') end++
        return end
    }

    fun moveVisualLine(direction: Int, selecting: Boolean) {
        val currentBuffer = buffer ?: return
        if (currentBuffer.imageUri != null) return
        val pos = currentBuffer.cursorPosition
        val (curX, curY) = getCursorCoordinates(pos)
        val targetY = if (direction < 0) curY - lineHeight else curY + lineHeight
        val newIndex = getIndexAt(curX, targetY)
        currentBuffer.setCursor(newIndex, selecting)
        shouldScrollToCursor = true
        invalidate()
        onCursorMoved?.invoke()
    }

    fun moveVisualLineBoundary(toEnd: Boolean, selecting: Boolean) {
        val currentBuffer = buffer ?: return
        val pos = currentBuffer.cursorPosition
        val (_, curY) = getCursorCoordinates(pos)
        val targetX = if (toEnd) width.toFloat() else 0f
        val newIndex = getIndexAt(targetX, curY)
        currentBuffer.setCursor(newIndex, selecting)
        shouldScrollToCursor = true
        invalidate()
        onCursorMoved?.invoke()
    }

    private fun getCursorCoordinates(targetPos: Int): Pair<Float, Float> {
        val currentBuffer = buffer ?: return Pair(0f, 0f)
        val text = currentBuffer.getText()
        val sideMargin = currentBuffer.sideMargin
        val topBottomMargin = currentBuffer.topBottomMargin
        val verticalOffset = currentBuffer.verticalOffset
        val folds = currentBuffer.folds
        val effectiveSideMargin = if (currentBuffer.showMargins) sideMargin + 2 * charWidth else sideMargin
        val availableHeight = height - 2 * topBottomMargin
        val fullLines = floor(availableHeight / lineHeight).coerceAtLeast(1f).toInt()
        val extraPadding = (availableHeight - (fullLines * lineHeight)) / 2
        val effectiveTop = topBottomMargin + extraPadding
        val xStart = effectiveSideMargin
        val yBase = effectiveTop + verticalOffset - textPaint.fontMetrics.top
        var x = xStart
        var y = yBase - scrollYOffset
        val maxWidth = width - effectiveSideMargin
        var i = 0
        while (i <= targetPos && i <= text.length) {
            if (i == targetPos) return Pair(x, y)
            if (i == text.length) break
            val fold = folds.find { it.start == i }
            if (fold != null) {
                x += charWidth
                i = fold.end + 1
            } else {
                val char = text[i]
                if (char == '\n') {
                    x = xStart
                    y += lineHeight
                } else {
                    if (!char.isWhitespace()) {
                        val wordEnd = findWordEnd(text, i)
                        val wordWidth = (wordEnd - i) * charWidth
                        if (x > xStart && x + wordWidth > maxWidth) {
                            x = xStart
                            y += lineHeight
                        }
                    }
                    x += charWidth
                }
                i++
            }
            if (x > maxWidth) {
                x = xStart
                y += lineHeight
            }
        }
        return Pair(x, y)
    }

    private fun loadImage(uriStr: String) {
        try {
            val inputStream = context.contentResolver.openInputStream(Uri.parse(uriStr))
            imageBitmap = BitmapFactory.decodeStream(inputStream)
            lastImageUri = uriStr
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadPdf(uriStr: String) {
        try {
            pdfRenderer?.close()
            pdfFileDescriptor?.close()
            pdfPageBitmaps.values.forEach { it.recycle() }
            pdfPageBitmaps.clear()
            val uri = Uri.parse(uriStr)
            pdfFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            pdfFileDescriptor?.let {
                pdfRenderer = PdfRenderer(it)
                lastPdfUri = uriStr
                totalPdfHeight = 0f
                val renderer = pdfRenderer!!
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val pageWidth = width - 2 * (buffer?.sideMargin ?: 60f)
                    val scale = pageWidth / page.width
                    val pageHeight = page.height * scale
                    totalPdfHeight += pageHeight + 20f
                    page.close()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPaint(backgroundPaint)

        if (viewerImageUri != null) {
            drawViewerImage(canvas)
            return
        }

        val currentBuffer = buffer ?: return
        val sideMargin = currentBuffer.sideMargin
        val topBottomMargin = currentBuffer.topBottomMargin
        val availableHeight = height - 2 * topBottomMargin
        val fullLines = floor(availableHeight / lineHeight).coerceAtLeast(1f).toInt()
        val perfectHeight = fullLines * lineHeight
        val extraPadding = (availableHeight - perfectHeight) / 2
        val effectiveTop = topBottomMargin + extraPadding
        val effectiveBottom = effectiveTop + perfectHeight

        if (currentBuffer.imageUri != null) {
            if (lastImageUri != currentBuffer.imageUri) loadImage(currentBuffer.imageUri!!)
            imageBitmap?.let { bmp ->
                canvas.save()
                canvas.clipRect(sideMargin, effectiveTop, width - sideMargin, effectiveBottom)
                imageMatrix.reset()
                val scaleX = (width - 2 * sideMargin) / bmp.width
                val scaleY = (effectiveBottom - effectiveTop) / bmp.height
                val fitScale = min(scaleX, scaleY)
                imageMatrix.postScale(fitScale * currentBuffer.imageScale, fitScale * currentBuffer.imageScale)
                val dx = sideMargin + (width - 2 * sideMargin - bmp.width * fitScale * currentBuffer.imageScale) / 2f + currentBuffer.imagePanX
                val dy = effectiveTop + (effectiveBottom - effectiveTop - bmp.height * fitScale * currentBuffer.imageScale) / 2f + currentBuffer.imagePanY
                imageMatrix.postTranslate(dx, dy)
                canvas.drawBitmap(bmp, imageMatrix, null)
                canvas.restore()
            }
        } else if (currentBuffer.pdfUri != null) {
            if (lastPdfUri != currentBuffer.pdfUri) loadPdf(currentBuffer.pdfUri!!)
            drawPdf(canvas, effectiveTop, effectiveBottom)
        } else if (currentBuffer.isMarkdownMode) {
            drawMarkdown(canvas, effectiveTop, effectiveBottom)
        } else {
            val text = currentBuffer.getText()
            val cursor = currentBuffer.cursorPosition
            val selectionRange = currentBuffer.getSelectionRange()
            val verticalOffset = currentBuffer.verticalOffset
            val bookmarks = currentBuffer.bookmarks
            val folds = currentBuffer.folds
            val effectiveSideMargin = if (currentBuffer.showMargins) sideMargin + 2 * charWidth else sideMargin

            canvas.save()
            canvas.clipRect(0f, effectiveTop, width.toFloat(), effectiveBottom)

            val xStart = effectiveSideMargin
            val yBase = effectiveTop + verticalOffset - textPaint.fontMetrics.top
            var x = xStart
            var y = yBase - scrollYOffset
            val maxWidth = width - effectiveSideMargin
            var cursorYInViewport = -1f

            val ext = currentBuffer.fileName?.lowercase() ?: ""
            val isHtml = ext.endsWith(".html")
            val isCss = ext.endsWith(".css")
            val syntaxTokens = if (isHtml || isCss) highlightSyntax(text, isHtml) else emptyList()

            var i = 0
            while (i <= text.length) {
                if (bookmarks.contains(i)) canvas.drawText("○", 5f, y, bookmarkPaint)
                val fold = folds.find { it.start == i }
                if (fold != null) {
                    if (i == cursor) { cursorYInViewport = y; drawCursor(canvas, x, y) }
                    if (y + lineHeight > effectiveTop && y < effectiveBottom + lineHeight) {
                        canvas.drawText("+", x, y, foldPaint)
                    }
                    x += charWidth; i = fold.end + 1; continue
                }
                if (i in selectionRange) {
                    canvas.drawRect(x, y + textPaint.fontMetrics.top, x + charWidth, y + textPaint.fontMetrics.bottom, selectionPaint)
                }
                if (i == cursor) {
                    cursorYInViewport = y; drawCursor(canvas, x, y)
                    if (isFindingCursor && blinkOn) {
                        canvas.drawRect(x, y + textPaint.fontMetrics.top, x + charWidth, y + textPaint.fontMetrics.bottom, textPaint)
                    }
                }
                if (i == text.length) break
                val char = text[i]
                if (char == '\n') {
                    x = xStart
                    y += lineHeight
                } else {
                    if (!char.isWhitespace()) {
                        val wordEnd = findWordEnd(text, i)
                        val wordWidth = (wordEnd - i) * charWidth
                        if (x > xStart && x + wordWidth > maxWidth) {
                            x = xStart
                            y += lineHeight
                        }
                    }
                    if (y + lineHeight > effectiveTop && y < effectiveBottom + lineHeight) {
                        val activePaint = if (isHtml || isCss) {
                            syntaxTokens.find { i >= it.start && i < it.end }?.paint ?: textPaint
                        } else textPaint
                        canvas.drawText(char.toString(), x, y, activePaint)
                    }
                    x += charWidth
                }
                if (x > maxWidth) { x = xStart; y += lineHeight }
                i++
                if (y > effectiveBottom + lineHeight && i > cursor) break 
            }
            canvas.restore()

        if (isTypewriterActive) {
            val coords = getCursorCoordinates(currentBuffer.cursorPosition)
            val currentScreenY = coords.second
            val diff = currentScreenY - typewriterTargetY
            if (diff != 0f) {
                scrollYOffset += diff
                postInvalidate()
            }
            
            // Draw 'T' in bottom left
            val originalColor = textPaint.color
            textPaint.color = Color.GRAY
            val oldSize = textPaint.textSize
            textPaint.textSize = 30f
            canvas.drawText("T", 20f, height - 20f, textPaint)
            textPaint.textSize = oldSize
            textPaint.color = originalColor
        } else if (shouldScrollToCursor && cursorYInViewport != -1f) {
                if (cursorYInViewport < effectiveTop) {
                    scrollYOffset -= (effectiveTop - cursorYInViewport)
                    scrollYOffset = max(0f, scrollYOffset)
                    postInvalidate()
                } else if (cursorYInViewport > effectiveBottom - lineHeight) {
                    scrollYOffset += (cursorYInViewport - (effectiveBottom - lineHeight))
                    postInvalidate()
                }
                shouldScrollToCursor = false
            }
        }

        if (currentBuffer.showMargins) {
            val bgColor = backgroundPaint.color
            val luminance = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor))
            marginPaint.color = if (luminance > 128) Color.argb(40, 0, 0, 0) else Color.argb(40, 255, 255, 255)
            canvas.drawRect(0f, 0f, width.toFloat(), effectiveTop, marginPaint)
            canvas.drawRect(0f, effectiveBottom, width.toFloat(), height.toFloat(), marginPaint)
            canvas.drawRect(0f, effectiveTop, sideMargin, effectiveBottom, marginPaint)
            canvas.drawRect(width - sideMargin, effectiveTop, width.toFloat(), effectiveBottom, marginPaint)
        }
    }

    private data class SyntaxToken(val start: Int, val end: Int, val paint: Paint)
    private fun highlightSyntax(text: String, isHtml: Boolean): List<SyntaxToken> {
        val result = mutableListOf<SyntaxToken>()
        if (isHtml) {
            val tagM = Pattern.compile("<[^>]+>").matcher(text)
            while (tagM.find()) {
                val tagStr = tagM.group()
                result.add(SyntaxToken(tagM.start(), tagM.end(), syntaxTagPaint))
                val attrM = Pattern.compile("\\s+([a-zA-Z\\-]+)=").matcher(tagStr)
                while (attrM.find()) result.add(SyntaxToken(tagM.start() + attrM.start(1), tagM.start() + attrM.end(1), syntaxAttrPaint))
                val valM = Pattern.compile("=\"([^\"]*)\"").matcher(tagStr)
                while (valM.find()) result.add(SyntaxToken(tagM.start() + valM.start() + 1, tagM.start() + valM.end(), syntaxValuePaint))
            }
            val commM = Pattern.compile("<!--[\\s\\S]*?-->").matcher(text)
            while (commM.find()) result.add(SyntaxToken(commM.start(), commM.end(), syntaxCommentPaint))
        } else {
            val selM = Pattern.compile("[^\\{]+\\s*\\{").matcher(text)
            while (selM.find()) result.add(SyntaxToken(selM.start(), selM.end() - 1, syntaxSelectorPaint))
            val commM = Pattern.compile("/\\*[\\s\\S]*?\\*/").matcher(text)
            while (commM.find()) result.add(SyntaxToken(commM.start(), commM.end(), syntaxCommentPaint))
        }
        return result
    }

    private fun drawPdf(canvas: Canvas, top: Float, bottom: Float) {
        val renderer = pdfRenderer ?: return
        val sideMargin = buffer?.sideMargin ?: 60f
        val pageWidth = width - 2 * sideMargin
        canvas.save()
        canvas.clipRect(sideMargin, top, width - sideMargin, bottom)
        var currentY = top - scrollYOffset
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val scale = pageWidth / page.width
            val pageHeight = page.height * scale
            if (currentY + pageHeight > top && currentY < bottom) {
                var bitmap = pdfPageBitmaps[i]
                if (bitmap == null || bitmap.width != pageWidth.toInt()) {
                    bitmap?.recycle()
                    bitmap = Bitmap.createBitmap(pageWidth.toInt(), pageHeight.toInt(), Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pdfPageBitmaps[i] = bitmap
                }
                canvas.drawBitmap(bitmap!!, sideMargin, currentY, null)
            }
            currentY += pageHeight + 20f
            page.close()
        }
        canvas.restore()
    }

    private fun drawMarkdown(canvas: Canvas, top: Float, bottom: Float) {
        val b = buffer ?: return
        val text = b.getCleanText()
        val sideMargin = b.sideMargin
        val effectiveSideMargin = if (b.showMargins) sideMargin + 2 * charWidth else sideMargin
        val maxWidth = width - effectiveSideMargin
        canvas.save()
        canvas.clipRect(0f, top, width.toFloat(), bottom)
        var y = top + 40f - scrollYOffset
        val mdPaint = Paint(textPaint)
        for (line in text.split("\n")) {
            mdPaint.textSize = textPaint.textSize; mdPaint.typeface = Typeface.MONOSPACE; mdPaint.isFakeBoldText = false
            var pl = line
            if (line.startsWith("# ")) { mdPaint.textSize = textPaint.textSize * 1.5f; mdPaint.isFakeBoldText = true; pl = line.substring(2) }
            else if (line.startsWith("## ")) { mdPaint.textSize = textPaint.textSize * 1.2f; mdPaint.isFakeBoldText = true; pl = line.substring(3) }
            else if (line.startsWith("- ")) { pl = "• " + line.substring(2) }
            if (pl.startsWith("**") && pl.endsWith("**")) { mdPaint.isFakeBoldText = true; pl = pl.substring(2, pl.length - 2) }
            val wrappedLines = wrapTextForMd(pl, mdPaint, maxWidth - effectiveSideMargin)
            for (wl in wrappedLines) {
                if (y + mdPaint.textSize > top && y < bottom + mdPaint.textSize) canvas.drawText(wl, effectiveSideMargin, y, mdPaint)
                y += mdPaint.fontMetrics.bottom - mdPaint.fontMetrics.top + 10f
            }
            y += 10f
        }
        canvas.restore()
    }

    private fun wrapTextForMd(text: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = ""
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) currentLine = testLine
            else { if (currentLine.isNotEmpty()) result.add(currentLine); currentLine = word }
        }
        result.add(currentLine)
        return result
    }

    private fun drawCursor(canvas: Canvas, x: Float, y: Float) {
        val b = buffer ?: return
        val settings = b.cursorSettings
        val isBlinkingActive = settings.blinkEnabled || isFindingCursor
        
        // If blinking is active and we are in the "OFF" phase, don't draw
        if (isBlinkingActive && !blinkOn) return

        val paint = cursorPaint
        val top = y + textPaint.fontMetrics.top
        val bottom = y + textPaint.fontMetrics.bottom
        val height = bottom - top

        when (settings.style) {
            EditorBuffer.CursorStyle.THIN_CARET -> {
                canvas.drawRect(x, top, x + 4f, bottom, paint)
            }
            EditorBuffer.CursorStyle.THICK_CARET -> {
                canvas.drawRect(x, top, x + 10f, bottom, paint)
            }
            EditorBuffer.CursorStyle.BLOCK -> {
                canvas.drawRect(x, top, x + charWidth, bottom, paint)
            }
            EditorBuffer.CursorStyle.UNDERLINE -> {
                canvas.drawRect(x, bottom - 6f, x + charWidth, bottom, paint)
            }
            EditorBuffer.CursorStyle.INVERTED -> {
                // Draw solid block
                canvas.drawRect(x, top, x + charWidth, bottom, paint)
                // Draw character on top with background color
                val text = b.getText()
                if (b.cursorPosition < text.length) {
                    val char = text[b.cursorPosition]
                    if (char != '\n') {
                        val oldColor = textPaint.color
                        textPaint.color = backgroundPaint.color
                        canvas.drawText(char.toString(), x, y, textPaint)
                        textPaint.color = oldColor
                    }
                }
            }
            EditorBuffer.CursorStyle.RETRO_BLOCK -> {
                // Green block with a bit of glow effect (simulated with slightly wider rect)
                val oldAlpha = paint.alpha
                paint.alpha = 100
                canvas.drawRect(x - 2f, top - 2f, x + charWidth + 2f, bottom + 2f, paint)
                paint.alpha = 255
                canvas.drawRect(x, top, x + charWidth, bottom, paint)
                paint.alpha = oldAlpha
            }
            EditorBuffer.CursorStyle.DIM_CARET -> {
                val oldAlpha = paint.alpha
                paint.alpha = 120
                canvas.drawRect(x, top, x + 4f, bottom, paint)
                paint.alpha = oldAlpha
            }
        }
    }

    private fun drawViewerImage(canvas: Canvas) {
        val uri = viewerImageUri ?: return
        val b = buffer ?: return
        
        if (lastViewerUri != uri) {
            try {
                if (isViewerRemote) {
                    // Pre-loaded by MainActivity
                } else {
                    val inputStream = context.contentResolver.openInputStream(Uri.parse(uri))
                    viewerBitmap = BitmapFactory.decodeStream(inputStream)
                }
                lastViewerUri = uri
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        viewerBitmap?.let { bmp ->
            val sideMargin = b.sideMargin
            val topBottomMargin = b.topBottomMargin
            val availableWidth = width.toFloat() - 2 * sideMargin
            val availableHeight = height.toFloat() - 2 * topBottomMargin
            
            val scaleX = availableWidth / bmp.width
            val scaleY = availableHeight / bmp.height
            val fitScale = min(scaleX, scaleY)
            
            imageMatrix.reset()
            imageMatrix.postScale(fitScale * viewerScale, fitScale * viewerScale)
            val dx = sideMargin + (availableWidth - bmp.width * fitScale * viewerScale) / 2f + viewerPanX
            val dy = topBottomMargin + (availableHeight - bmp.height * fitScale * viewerScale) / 2f + viewerPanY
            imageMatrix.postTranslate(dx, dy)
            
            canvas.save()
            canvas.clipRect(sideMargin, topBottomMargin, width - sideMargin, height - topBottomMargin)
            canvas.drawBitmap(bmp, imageMatrix, null)
            canvas.restore()
        }
    }

    fun setViewerBitmap(bmp: Bitmap?, uri: String) {
        viewerBitmap = bmp
        viewerImageUri = uri
        isViewerRemote = true
        lastViewerUri = uri
        viewerScale = 1f
        viewerPanX = 0f
        viewerPanY = 0f
        invalidate()
    }
}
