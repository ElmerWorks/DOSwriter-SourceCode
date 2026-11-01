package com.example.doswriter.editor

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.text.TextPaint
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.util.LruCache
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.doswriter.model.EditorBuffer
import java.io.File
import java.util.*
import java.util.regex.Pattern
import kotlin.math.*

class EditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var activeBuffer: EditorBuffer? = null
    val textPaint = TextPaint().apply { color = Color.WHITE; textSize = 48f; isAntiAlias = true; typeface = Typeface.MONOSPACE }
    internal var cursorPaint = Paint().apply { color = Color.rgb(0, 255, 0); style = Paint.Style.FILL }
    internal var selectionPaint = Paint().apply { color = Color.argb(100, 0, 150, 0); style = Paint.Style.FILL }
    internal var backgroundPaint = Paint().apply { color = Color.BLACK }
    internal var bookmarkPaint = Paint().apply { color = Color.rgb(0, 200, 255); textSize = 40f; textAlign = Paint.Align.LEFT }
    internal var foldPaint = Paint().apply { color = Color.rgb(200, 200, 0); textSize = 48f; textAlign = Paint.Align.LEFT; typeface = Typeface.DEFAULT_BOLD }
    internal var marginPaint = Paint().apply { color = Color.argb(40, 255, 255, 255); style = Paint.Style.FILL }

    internal var syntaxTagPaint = Paint(textPaint).apply { color = Color.rgb(86, 156, 214) } 
    internal var syntaxAttrPaint = Paint(textPaint).apply { color = Color.rgb(156, 220, 254) } 
    internal var syntaxValuePaint = Paint(textPaint).apply { color = Color.rgb(206, 145, 120) } 
    internal var syntaxCommentPaint = Paint(textPaint).apply { color = Color.rgb(106, 153, 85); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC) } 
    internal var syntaxKeywordPaint = Paint(textPaint).apply { color = Color.rgb(197, 134, 192) } 
    internal var syntaxSelectorPaint = Paint(textPaint).apply { color = Color.rgb(215, 186, 125) } 

    internal data class VisualLine(val start: Int, val end: Int, val isFolded: Boolean = false)
    internal val visualLines = mutableListOf<VisualLine>()
    internal var lastLayoutWidth = 0f
    internal var lastLayoutVersion = -1
    internal var lastLayoutPaintHash = 0
    internal var lastLayoutMarginsHash = 0
    var isCursorVisible = true
        set(value) {
            field = value
            invalidate()
        }
    var isWatermarkGlobalHidden = false
        set(value) {
            field = value
            invalidate()
        }

    internal var imageMatrix = Matrix()
    internal var charWidth = 0f
    internal var lineHeight = 0f
    internal var isMonospace = true
    internal var scrollYOffset = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
                onScrollChanged?.invoke(value)
                if (activeBuffer?.pdfUri != null) schedulePdfRender()
            }
        }
    internal var scrollXOffset = 0f
    internal var shouldScrollToCursor = false
    internal var shouldScrollToCursorTop = false

    internal var imageBitmap: Bitmap? = null
    internal var lastImageUri: String? = null
    internal var pdfRenderer: PdfRenderer? = null
    internal var pdfFileDescriptor: ParcelFileDescriptor? = null
    internal var lastPdfUri: String? = null
    internal var pdfPageOffsets = mutableListOf<Float>()
    internal var pdfPageBaseHeights = mutableListOf<Float>()
    internal var totalPdfHeight = 0f

    internal var pdfCache = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 4).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }
    internal var renderThread: HandlerThread? = null
    internal var renderHandler: Handler? = null
    internal var lastScheduledPdfUri: String? = null

    internal var typewriterTargetY = 0f

    var viewerImageUri: String? = null
        private set
    internal var isViewerRemote = false
    internal var viewerBitmap: Bitmap? = null
    internal var lastViewerUri: String? = null
    internal var viewerScale = 1f
    internal var viewerPanX = 0f
    internal var viewerPanY = 0f
    internal var viewerMovie: android.graphics.Movie? = null
    internal var movieStart: Long = 0
    internal var imageMovie: android.graphics.Movie? = null
    internal var imageMovieStart: Long = 0
    var isDemoRunning = false
    internal var totalMdHeight = 0f
    internal var mdImageCache = mutableMapOf<String, Bitmap>()
    internal var mdImageRect = RectF()

    internal var isPdfSelecting = false
    internal var pdfSelectionStart = PointF()
    internal var pdfSelectionEnd = PointF()
    internal val pdfSelectionRect = RectF()

    internal var lastTouchX = 0f
    internal var lastTouchY = 0f
    internal var isDragging = false
    internal var isSelectingText = false
    
    var isImageFadeEnabled = true
    internal var imageAlpha = 1.0f
    internal var imageFadeAnimator: android.animation.ValueAnimator? = null
    internal var fadeOutBitmap: Bitmap? = null
    internal val fadeOutMatrix = Matrix()
    internal var fadeOutAlpha = 0f
    internal val imagePaint = Paint().apply { isFilterBitmap = true }

    internal fun startImageFade(fadeIn: Boolean) {
        if (!isImageFadeEnabled) {
            imageAlpha = if (fadeIn) 1f else 0f
            invalidate()
            return
        }
        
        imageFadeAnimator?.cancel()
        imageFadeAnimator = android.animation.ValueAnimator.ofFloat(imageAlpha, if (fadeIn) 1f else 0f).apply {
            duration = 400
            addUpdateListener {
                imageAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    internal val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (viewerImageUri != null) {
                viewerScale *= detector.scaleFactor
                viewerScale = viewerScale.coerceIn(0.1f, 10f)
                invalidate()
                return true
            }
            if (activeBuffer?.pdfUri != null) {
                adjustPdfZoom(detector.scaleFactor - 1.0f)
                return true
            }
            return false
        }
    })

    var forceMarkdownRender = false
    var onScrollChanged: ((Float) -> Unit)? = null
    var onCursorMoved: (() -> Unit)? = null
    var onRequestedShowKeyboard: (() -> Unit)? = null
    var onRightClick: ((Float, Float) -> Unit)? = null
    var onNextImage: (() -> Unit)? = null
    var onPrevImage: (() -> Unit)? = null

    var isVirtualKeyboardEnabled: Boolean = false
        set(value) {
            field = value
            if (!value) {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(windowToken, 0)
            }
        }
    
    var suppressWatermark = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        updatePaintMetrics()
        renderThread = HandlerThread("PdfRenderThread").apply { start() }
        renderHandler = Handler(renderThread!!.looper)
    }

    override fun onCheckIsTextEditor(): Boolean = false
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? = null

    fun refreshMetrics() {
        activeBuffer?.let { b ->
            textPaint.textSize = b.fontSize
            applyFontType(b.fontType)
            updatePaintMetrics()
            invalidate()
        }
    }

    internal fun updatePaintMetrics() {
        val text = "A"
        charWidth = textPaint.measureText(text)
        isMonospace = textPaint.typeface == Typeface.MONOSPACE || textPaint.measureText("i") == charWidth
        val spacing = activeBuffer?.lineSpacing ?: 1.2f
        textPaint.isFakeBoldText = activeBuffer?.isBold ?: false
        val standardHeight = textPaint.fontMetrics.bottom - textPaint.fontMetrics.top
        lineHeight = standardHeight * spacing
        
        listOf(syntaxTagPaint, syntaxAttrPaint, syntaxValuePaint, syntaxCommentPaint, syntaxKeywordPaint, syntaxSelectorPaint).forEach {
            it.textSize = textPaint.textSize
            it.typeface = textPaint.typeface
        }
        lastLayoutPaintHash = textPaint.hashCode() xor textPaint.textSize.hashCode()
    }

    fun setBuffer(buffer: EditorBuffer) {
        this.activeBuffer = buffer
        textPaint.textSize = buffer.fontSize
        applyFontType(buffer.fontType)
        updatePaintMetrics()
        scrollYOffset = 0f
        
        if (lastImageUri != buffer.imageUri) {
            fadeOutBitmap = null
            imageBitmap = null
            imageMovie = null
        }
        imageAlpha = if (buffer.imageUri != null) 1.0f else 0f
        imageFadeAnimator?.cancel()

        mainHandler.removeCallbacks(blinkRunnable)
        if (buffer.cursorSettings.blinkAlways) {
            mainHandler.post(blinkRunnable)
        }

        invalidate()
    }

    internal fun applyFontType(type: String) {
        val buffer = activeBuffer
        val normalizedType = type.uppercase().replace("_", " ").replace("-", " ")
        val assetName = type.replace(" ", "")
        
        buffer?.customFontsPath?.let { path ->
            try {
                if (path.startsWith("content://")) {
                    val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, Uri.parse(path))
                    val fontFile = root?.findFile(type) 
                        ?: root?.findFile("$type.ttf") 
                        ?: root?.findFile("$type.otf")
                    
                    fontFile?.let {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.contentResolver.openFileDescriptor(it.uri, "r")?.use { pfd ->
                                textPaint.typeface = Typeface.Builder(pfd.fileDescriptor).build()
                                return
                            }
                        } else {
                            context.contentResolver.openInputStream(it.uri)?.use { input ->
                                val temp = java.io.File(context.cacheDir, "font_${type.hashCode()}.ttf")
                                if (!temp.exists()) {
                                    temp.outputStream().use { output -> input.copyTo(output) }
                                }
                                textPaint.typeface = Typeface.createFromFile(temp)
                                return
                            }
                        }
                    }
                } else {
                    val extensions = listOf("", ".ttf", ".otf")
                    for (ext in extensions) {
                        val fontFile = File(path, "$type$ext")
                        if (fontFile.exists()) {
                            textPaint.typeface = Typeface.createFromFile(fontFile)
                            return
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        val extensions = listOf(".ttf", ".otf")
        for (ext in extensions) {
            try {
                val assetPath = "fonts/$assetName$ext"
                context.assets.open(assetPath).close()
                textPaint.typeface = Typeface.createFromAsset(context.assets, assetPath)
                return
            } catch (e: Exception) {}
        }

        textPaint.typeface = when (normalizedType) {
            "MONOSPACE" -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            "COURIER NEW" -> Typeface.create("serif-monospace", Typeface.NORMAL)
            "SERIF" -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            "SANS SERIF" -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            "ROBOTO MONO" -> Typeface.create("monospace", Typeface.NORMAL)
            "TIMES NEW ROMAN" -> Typeface.create("serif", Typeface.NORMAL)
            "GEORGIA" -> Typeface.create("serif", Typeface.NORMAL)
            "ARIAL" -> Typeface.create("sans-serif", Typeface.NORMAL)
            "HELVETICA" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            "VERDANA" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            "TREBUCHET" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            "CONSOLAS", "LUCIDA CONSOLE" -> Typeface.create("monospace", Typeface.NORMAL)
            "NOTO SANS" -> Typeface.create("sans-serif", Typeface.NORMAL)
            else -> {
                val tf = Typeface.create(type, Typeface.NORMAL)
                if (tf == Typeface.DEFAULT && normalizedType != "DEFAULT") {
                    if (normalizedType.contains("MONO")) Typeface.MONOSPACE
                    else if (normalizedType.contains("SERIF")) Typeface.SERIF
                    else tf
                } else tf
            }
        }
        recalculateCurrentPreset()
    }

    fun setFontType(type: String) {
        activeBuffer?.fontType = type
        applyFontType(type)
        updatePaintMetrics()
        invalidate()
    }

    fun setLineSpacing(spacing: Float) {
        activeBuffer?.lineSpacing = spacing
        updatePaintMetrics()
        invalidate()
    }

    fun setFontBold(bold: Boolean) {
        activeBuffer?.isBold = bold
        updatePaintMetrics()
        invalidate()
    }

    fun scrollToCursor() {
        shouldScrollToCursor = true
        invalidate()
    }

    fun scrollToCursorTop() {
        shouldScrollToCursorTop = true
        invalidate()
    }

    fun setTextColor(color: Int) { textPaint.color = color; invalidate() }
    fun setCursorColor(color: Int) { cursorPaint.color = color; invalidate() }
    fun setCustomBackgroundColor(color: Int) { backgroundPaint.color = color; invalidate() }
    fun setFontSize(size: Float) {
        activeBuffer?.fontSize = size
        activeBuffer?.activePreset = "Custom"
        textPaint.textSize = size
        updatePaintMetrics()
        invalidate()
    }

    fun applyFontPreset(presetName: String) {
        val buffer = activeBuffer ?: return
        val targetCPL = when (presetName) {
            "Pocket" -> 45
            "Compact" -> 60
            "Standard" -> 75
            "Comfortable" -> 90
            "Manuscript" -> 100
            else -> return
        }

        val sideMargin = buffer.sideMargin
        val effectiveSideMargin = if (buffer.showMargins) sideMargin + 2 * charWidth else sideMargin
        val availableWidth = width - 2 * effectiveSideMargin
        if (availableWidth <= 0) return

        // 1. Density Modifier based on Hardware Profile
        val densityMultiplier = when (buffer.deviceProfile) {
            0 -> 1.0f // Phone
            1 -> 1.2f // Tablet (viewed further away)
            2 -> 1.1f // Custom (E-ink default)
            else -> 1.0f
        }

        // 2. Binary search for best fit size to reach target CPL
        var low = 8f
        var high = 120f
        var bestSize = buffer.fontSize
        val testString = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789"
        val p = TextPaint(textPaint)

        repeat(10) {
            val mid = (low + high) / 2
            p.textSize = mid
            val avgCharW = p.measureText(testString) / testString.length
            val estimatedCPL = availableWidth / avgCharW
            
            if (estimatedCPL > targetCPL) {
                bestSize = mid
                low = mid
            } else {
                high = mid
            }
        }

        buffer.activePreset = presetName
        buffer.fontSize = bestSize * densityMultiplier
        textPaint.textSize = buffer.fontSize
        updatePaintMetrics()
        invalidate()
    }

    fun recalculateCurrentPreset() {
        val buffer = activeBuffer ?: return
        if (buffer.activePreset != "Custom") {
            applyFontPreset(buffer.activePreset)
        }
    }
    
    fun refreshCursorSettings() {
        mainHandler.removeCallbacks(blinkRunnable)
        if (activeBuffer?.cursorSettings?.blinkAlways == true) {
            mainHandler.post(blinkRunnable)
        }
        invalidate()
    }
    fun getFontSize(): Float = textPaint.textSize

    fun setSideMargin(margin: Float) { activeBuffer?.sideMargin = margin; invalidate() }
    fun getSideMargin(): Float = activeBuffer?.sideMargin ?: 60f
    fun setTopBottomMargin(margin: Float) { activeBuffer?.topBottomMargin = margin; invalidate() }
    fun getTopBottomMargin(): Float = activeBuffer?.topBottomMargin ?: 60f
    fun setVerticalOffset(offset: Float) { activeBuffer?.verticalOffset = offset; invalidate() }
    fun getVerticalOffset(): Float = activeBuffer?.verticalOffset ?: 10f

    fun getIndexAtViewportCenter(): Int {
        val yCenter = scrollYOffset + height / 2f
        val lineIdx = (yCenter / lineHeight).toInt().coerceIn(0, visualLines.size - 1)
        if (lineIdx >= 0 && lineIdx < visualLines.size) {
            return visualLines[lineIdx].start
        }
        return 0
    }

    fun estimateMetrics(size: Float): Pair<Float, Float> {
        val p = TextPaint(textPaint).apply { textSize = size }
        val cw = p.measureText("A")
        val lh = (p.fontMetrics.bottom - p.fontMetrics.top) * (activeBuffer?.lineSpacing ?: 1.2f)
        return Pair(cw, lh)
    }

    internal var mainHandler = Handler(Looper.getMainLooper())

    internal var blinkRunnable = object : Runnable {
        override fun run() {
            val b = activeBuffer ?: return
            val settings = b.cursorSettings
            
            if (isFindingCursor || isLocatorBlinking || settings.blinkAlways) {
                blinkOn = !blinkOn
                invalidate()
                val interval = if (isFindingCursor || isLocatorBlinking) 300L else settings.blinkIntervalMs
                mainHandler.postDelayed(this, interval)
            } else {
                blinkOn = true
                invalidate()
            }
        }
    }
    internal var isFindingCursor = false
    internal var isLocatorBlinking = false
    internal var blinkOn = true

    internal var locatorStopRunnable = Runnable {
        isLocatorBlinking = false
        invalidate()
    }

    fun startLocatorBlink() {
        val b = activeBuffer ?: return
        isLocatorBlinking = true
        blinkOn = true
        invalidate()
        mainHandler.removeCallbacks(blinkRunnable)
        mainHandler.post(blinkRunnable)
        
        mainHandler.removeCallbacks(locatorStopRunnable)
        mainHandler.postDelayed(locatorStopRunnable, b.cursorSettings.locatorBlinkDurationMs)
    }

    fun startFindCursor() {
        isFindingCursor = true
        blinkOn = true
        invalidate()
        mainHandler.removeCallbacks(blinkRunnable)
        mainHandler.post(blinkRunnable)
    }

    fun stopFindCursor() {
        isFindingCursor = false
        mainHandler.removeCallbacks(blinkRunnable)
        invalidate()
    }

    fun toggleTypewriterMode() {
        val buffer = activeBuffer ?: return
        buffer.cursorSettings.typewriter.isEnabled = !buffer.cursorSettings.typewriter.isEnabled
        if (buffer.cursorSettings.typewriter.isEnabled) {
            recenterTypewriter()
        } else {
            scrollXOffset = 0f
        }
        invalidate()
    }

    fun isTypewriterActive(): Boolean {
        val buffer = activeBuffer ?: return false
        return buffer.cursorSettings.typewriter.isEnabled || (buffer.autoTypewriterOnLargeFiles && buffer.isLargeFile())
    }

    fun recenterTypewriter(atBottom: Boolean = false) {
        val currentBuffer = activeBuffer ?: return
        if (!currentBuffer.cursorSettings.typewriter.isEnabled) {
            currentBuffer.cursorSettings.typewriter.isEnabled = true
        }
        
        if (atBottom) {
            currentBuffer.cursorSettings.typewriter.anchor = EditorBuffer.TypewriterAnchor.BOTTOM
        }

        val sideMargin = currentBuffer.sideMargin
        val topBottomMargin = currentBuffer.topBottomMargin
        val availableHeight = height - 2 * topBottomMargin
        val fullLines = kotlin.math.floor(availableHeight / lineHeight).coerceAtLeast(1f).toInt()
        val perfectHeight = fullLines * lineHeight
        val extraPadding = (availableHeight - perfectHeight) / 2
        val effectiveBottom = topBottomMargin + extraPadding + perfectHeight

        typewriterTargetY = when(currentBuffer.cursorSettings.typewriter.anchor) {
            EditorBuffer.TypewriterAnchor.CENTER -> height / 2f
            EditorBuffer.TypewriterAnchor.BOTTOM -> effectiveBottom - lineHeight
        }

        val coords = getCursorCoordinates(currentBuffer.cursorPosition)
        scrollYOffset += (coords.second - typewriterTargetY)
        
        if (currentBuffer.cursorSettings.typewriter.isFixed) {
            scrollXOffset = coords.first - (width / 2f)
        } else {
            scrollXOffset = 0f
        }

        invalidate()
    }

    fun setViewerImage(uri: String, remote: Boolean = false) {
        viewerImageUri = uri
        isViewerRemote = remote
        viewerBitmap = null
        viewerMovie = null
        lastViewerUri = null
        movieStart = 0
        viewerScale = 1f
        viewerPanX = 0f
        viewerPanY = 0f
        invalidate()
    }

    fun clearViewerImage() {
        viewerImageUri = null
        viewerBitmap?.recycle()
        viewerBitmap = null
        viewerMovie = null
        movieStart = 0
        invalidate()
    }

    fun isViewerActive(): Boolean = viewerImageUri != null

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (handleGenericMotionEvent(event)) return true
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (handleTouchEvent(event)) return true
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderThread?.quitSafely()
        renderThread = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderDraw(canvas)
    }

    fun setViewerBitmap(bmp: Bitmap?, uri: String) {
        viewerBitmap = bmp
        viewerImageUri = uri
        isViewerRemote = true
        lastViewerUri = uri
        viewerMovie = null
        movieStart = 0
        viewerScale = 1f
        viewerPanX = 0f
        viewerPanY = 0f
        invalidate()
    }

    fun scrollManually(dy: Float) {
        val currentBuffer = activeBuffer ?: return
        val maxScroll = if (currentBuffer.isMarkdownMode || forceMarkdownRender) {
            max(0f, totalMdHeight - height + 200f)
        } else if (currentBuffer.pdfUri != null) {
            max(0f, totalPdfHeight - height + 200f)
        } else {
            max(0f, visualLines.size * lineHeight - height + 200f)
        }
        scrollYOffset = (scrollYOffset + dy).coerceIn(0f, maxScroll)
        invalidate()
    }

    fun getPdfPageCount(): Int = pdfRenderer?.pageCount ?: 0

    fun getCurrentPdfPage(): Int {
        if (pdfPageOffsets.isEmpty()) return 0
        val idx = pdfPageOffsets.indexOfLast { it <= scrollYOffset }
        return max(0, idx)
    }

    fun jumpToPdfPage(index: Int) {
        if (index in pdfPageOffsets.indices) {
            scrollYOffset = pdfPageOffsets[index]
            invalidate()
        }
    }

    fun panPdf(dx: Float) {
        val b = activeBuffer ?: return
        if (b.pdfUri != null) {
            b.pdfPanX += dx
            invalidate()
        }
    }

    fun panPdfToEdge(toEnd: Boolean) {
        val b = activeBuffer ?: return
        if (b.pdfUri != null) {
            b.pdfPanX = if (toEnd) -1000f else 0f 
            invalidate()
        }
    }

    fun adjustPdfZoom(factor: Float, updateBuffer: Boolean = true) {
        val b = activeBuffer ?: return
        if (b.pdfUri != null) {
            if (updateBuffer) {
                b.pdfScale = (b.pdfScale * (1f + factor)).coerceIn(0.1f, 10f)
            }
            pdfCache.evictAll()
            recalculatePdfOffsets()
            invalidate()
        }
    }

    fun adjustImageZoom(factor: Float, updateBuffer: Boolean = true) {
        val b = activeBuffer ?: return
        if (b.imageUri != null) {
            if (updateBuffer) {
                b.imageScale = (b.imageScale * (1f + factor)).coerceIn(0.1f, 10f)
            }
            invalidate()
        }
    }

    fun pdfReset() {
        val b = activeBuffer ?: return
        if (b.pdfUri != null) {
            b.pdfScale = 1f
            b.pdfPanX = 0f
            scrollYOffset = 0f
            pdfCache.evictAll()
            recalculatePdfOffsets()
            invalidate()
        }
    }

    fun scrollToTop() {
        scrollYOffset = 0f
        invalidate()
    }

    fun getScrollYOffset(): Float = scrollYOffset

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        typewriterTargetY = 0f
        recalculateCurrentPreset()
    }

    fun scrollToBottom() {
        val currentBuffer = activeBuffer ?: return
        val maxScroll = if (currentBuffer.isMarkdownMode || forceMarkdownRender) {
            max(0f, totalMdHeight - height + 200f)
        } else if (currentBuffer.pdfUri != null) {
            max(0f, totalPdfHeight - height + 200f)
        } else {
            max(0f, visualLines.size * lineHeight - height + 200f)
        }
        scrollYOffset = maxScroll
        invalidate()
    }
}
