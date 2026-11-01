package com.example.doswriter.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.documentfile.provider.DocumentFile
import com.example.doswriter.logic.BufferManager
import com.example.doswriter.logic.ManuscriptEngine
import java.util.Calendar
import kotlin.math.min
import kotlin.math.max
import java.io.File

class WorkspaceLayoutOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class LayoutViewMode {
        DEFAULT,
        IMAGE_MODE,
        PREVIEW_MODE,
        TEXT_MODE
    }

    private var currentViewMode = LayoutViewMode.DEFAULT
    private var bufferManager: BufferManager? = null
    private var selectedIdx = 0
    private val tilesCount = 8
    
    private val engine = ManuscriptEngine(context)
    private val thumbnailCache = mutableMapOf<Int, Bitmap>()
    
    private val bgPaint = Paint().apply { color = Color.argb(235, 0, 0, 0) }
    private val borderPaint = Paint().apply { color = Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val highlightPaint = Paint().apply { color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 8f }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 24f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val pageRect = RectF()

    private var currentLabelSize = 22f

    private val snapshotTextPaint = android.text.TextPaint().apply { isAntiAlias = true }
    private val snapshotBgPaint = Paint().apply { style = Paint.Style.FILL }

    var onBufferSelected: ((Int) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    fun setMenuAppearance(fontSize: Float, bgColor: Int, textColor: Int) {
        bgPaint.color = bgColor
        textPaint.color = textColor
        textPaint.textSize = fontSize * 0.8f
        currentLabelSize = (fontSize * 0.5f).coerceIn(18f, 30f)
        
        val r = android.graphics.Color.red(bgColor)
        val g = android.graphics.Color.green(bgColor)
        val b = android.graphics.Color.blue(bgColor)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        borderPaint.color = if (luminance > 0.5) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        invalidate()
    }

    fun show(manager: BufferManager) {
        this.bufferManager = manager
        this.selectedIdx = manager.currentBufferIndex.coerceIn(0, 7)
        this.currentViewMode = LayoutViewMode.DEFAULT
        
        thumbnailCache.values.forEach { it.recycle() }
        thumbnailCache.clear()
        
        visibility = VISIBLE
        requestFocus()
        invalidate()
    }

    fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        if (visibility != VISIBLE) return false
        
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (selectedIdx > 0) selectedIdx--
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (selectedIdx < tilesCount - 1) selectedIdx++
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (selectedIdx >= 4) selectedIdx -= 4
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (selectedIdx < 4) selectedIdx += 4
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_TAB -> {
                if (event.isCtrlPressed || event.isAltPressed || event.isShiftPressed) {
                    selectedIdx = (selectedIdx - 1 + tilesCount) % tilesCount
                } else {
                    selectedIdx = (selectedIdx + 1) % tilesCount
                }
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                onBufferSelected?.invoke(selectedIdx)
                hide()
                return true
            }
            // Mode switching shortcuts
            KeyEvent.KEYCODE_P -> {
                if (event.isAltPressed || event.isCtrlPressed) {
                    currentViewMode = LayoutViewMode.PREVIEW_MODE
                    invalidate()
                    return true
                }
            }
            KeyEvent.KEYCODE_I -> {
                if (event.isAltPressed || event.isCtrlPressed) {
                    currentViewMode = LayoutViewMode.IMAGE_MODE
                    invalidate()
                    return true
                }
            }
            KeyEvent.KEYCODE_T -> {
                if (event.isAltPressed || event.isCtrlPressed) {
                    currentViewMode = LayoutViewMode.TEXT_MODE
                    invalidate()
                    return true
                }
            }
            // Numeric selection 1-8
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8 -> {
                val index = keyCode - KeyEvent.KEYCODE_1
                onBufferSelected?.invoke(index)
                hide()
                return true
            }
            // Function key selection F1-F8
            KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4,
            KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8 -> {
                val index = keyCode - KeyEvent.KEYCODE_F1
                onBufferSelected?.invoke(index)
                hide()
                return true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                hide()
                return true
            }
            KeyEvent.KEYCODE_B -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    hide()
                    return true
                }
            }
        }
        // Consume all other key events while in Buffer Layout View so accidental commands do not exit or leak
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        
        if (event.action == MotionEvent.ACTION_UP) {
            val manager = bufferManager ?: return false
            
            val cols = 4
            val rows = 2
            val edgePadding = 30f
            val gridGap = 20f
            
            val availableW = width - 2 * edgePadding
            val availableH = height - 2 * edgePadding
            
            val cellW = (availableW - (cols - 1) * gridGap) / cols
            val cellH = (availableH - (rows - 1) * gridGap) / rows

            val tx = event.x - edgePadding
            val ty = event.y - edgePadding
            
            if (tx >= 0 && ty >= 0) {
                val col = (tx / (cellW + gridGap)).toInt()
                val row = (ty / (cellH + gridGap)).toInt()
                
                val cellLocalX = tx % (cellW + gridGap)
                val cellLocalY = ty % (cellH + gridGap)
                
                if (col in 0 until cols && row in 0 until rows && 
                    cellLocalX <= cellW && cellLocalY <= cellH) {
                    val index = row * cols + col
                    if (index in 0 until tilesCount) {
                        onBufferSelected?.invoke(index)
                        hide()
                        return true
                    }
                }
            }
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun hide() {
        visibility = GONE
        thumbnailCache.values.forEach { it.recycle() }
        thumbnailCache.clear()
        onDismiss?.invoke()
    }

    private fun getThumbnailFromUri(uriStr: String, cacheKey: Int, drawW: Float, drawH: Float, isPdf: Boolean = false): Bitmap? {
        var thumb = thumbnailCache[cacheKey]
        if (thumb == null) {
            try {
                if (uriStr.startsWith("asset:///")) {
                    val assetPath = uriStr.substringAfter("asset:///").trimStart('/')
                    val cleanPath = assetPath.removePrefix("examples/").trimStart('/')
                    val pathsToTry = listOf(assetPath, cleanPath, "examples/$cleanPath")
                    
                    for (path in pathsToTry) {
                        try {
                            context.assets.open(path).use { stream ->
                                val bis = java.io.BufferedInputStream(stream)
                                val fullBmp = BitmapFactory.decodeStream(bis)
                                if (fullBmp != null) {
                                    val scale = min(drawW / fullBmp.width, drawH / fullBmp.height)
                                    val tw = (fullBmp.width * scale).toInt().coerceAtLeast(1)
                                    val th = (fullBmp.height * scale).toInt().coerceAtLeast(1)
                                    
                                    if (tw == fullBmp.width && th == fullBmp.height) {
                                        thumb = fullBmp
                                    } else {
                                        thumb = Bitmap.createScaledBitmap(fullBmp, tw, th, true)
                                        fullBmp.recycle()
                                    }
                                    if (thumb != null) break
                                }
                            }
                        } catch (e: Exception) { }
                    }
                } else {
                    val uri = Uri.parse(uriStr)
                    if (isPdf) {
                        val fd = context.contentResolver.openFileDescriptor(uri, "r")
                        fd?.let {
                            val renderer = PdfRenderer(it)
                            val page = renderer.openPage(0)
                            val pdfAspect = page.height.toFloat() / page.width.toFloat()
                            var tw = drawW.toInt()
                            var th = (tw * pdfAspect).toInt()
                            if (th > drawH) {
                                th = drawH.toInt()
                                tw = (th / pdfAspect).toInt()
                            }
                            thumb = Bitmap.createBitmap(tw.coerceAtLeast(1), th.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            page.render(thumb!!, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            renderer.close()
                            it.close()
                        }
                    } else {
                        val inputStream = if (uri.scheme == "file") {
                            val path = uri.path ?: uriStr.removePrefix("file://")
                            java.io.FileInputStream(java.io.File(path))
                        } else {
                            context.contentResolver.openInputStream(uri)
                        }
                        
                        inputStream?.use { stream ->
                            val bis = java.io.BufferedInputStream(stream)
                            val fullBmp = BitmapFactory.decodeStream(bis)
                            if (fullBmp != null) {
                                val scale = min(drawW / fullBmp.width, drawH / fullBmp.height)
                                val tw = (fullBmp.width * scale).toInt().coerceAtLeast(1)
                                val th = (fullBmp.height * scale).toInt().coerceAtLeast(1)
                                
                                if (tw == fullBmp.width && th == fullBmp.height) {
                                    thumb = fullBmp
                                } else {
                                    thumb = Bitmap.createScaledBitmap(fullBmp, tw, th, true)
                                    fullBmp.recycle()
                                }
                            }
                        }
                    }
                }
                if (thumb != null) thumbnailCache[cacheKey] = thumb!!
            } catch (e: Exception) { 
                android.util.Log.e("LayoutOverlay", "Error loading thumb: $uriStr", e)
            }
        }
        return thumb
    }

    private fun drawThumbnailText(canvas: Canvas, text: String, rect: RectF, drawW: Float, drawH: Float, buffer: com.example.doswriter.model.EditorBuffer, overlay: Boolean = false) {
        val manager = bufferManager
        val bgColorVal = bgPaint.color
        val r = Color.red(bgColorVal); val g = Color.green(bgColorVal); val b = Color.blue(bgColorVal)
        val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        val isLightBG = lum > 0.5
        
        var boxColor = if (isLightBG) Color.LTGRAY else Color.DKGRAY
        var textColor = if (isLightBG) Color.BLACK else Color.WHITE

        // Handle Dark/Light mode overrides (Global Setting)
        if (manager?.thumbnailTextColorMode == 1) { // Dark
            textColor = Color.BLACK
            if (!overlay) boxColor = Color.rgb(200, 200, 200)
        } else if (manager?.thumbnailTextColorMode == 2) { // Light
            textColor = Color.WHITE
            if (!overlay) boxColor = Color.rgb(40, 40, 40)
        }

        // Handle Theme Colors (Global Setting)
        if (manager?.useThemeColorsForThumbnail == true && !overlay) {
            val mainActivity = context as? com.example.doswriter.MainActivity
            if (mainActivity != null) {
                val cal = Calendar.getInstance()
                val currentTime = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                val dayStart = mainActivity.dayStartHour * 60 + mainActivity.dayStartMin
                val nightStart = mainActivity.nightStartHour * 60 + mainActivity.nightStartMin
                val isDay = if (mainActivity.themeMode == 1) true 
                           else if (mainActivity.themeMode == 2) false
                           else if (dayStart < nightStart) currentTime in dayStart until nightStart 
                           else currentTime >= dayStart || currentTime < nightStart

                val themeIdx = if (isDay) buffer.dayThemeIndex else buffer.nightThemeIndex
                val theme = mainActivity.themes.getOrNull(themeIdx)
                if (theme != null) {
                    boxColor = theme.backgroundColor
                    textColor = theme.textColor
                }
            }
        }
        
        if (!overlay) {
            canvas.drawRect(rect, Paint().apply { color = boxColor })
        } else {
            // Draw a subtle translucent box behind text for readability if overlaying on image
            val txR = Color.red(textColor); val txG = Color.green(textColor); val txB = Color.blue(textColor)
            val txLum = (0.299 * txR + 0.587 * txG + 0.114 * txB) / 255.0
            
            val scrimPaint = Paint().apply { 
                color = if (txLum > 0.5) Color.argb(160, 0, 0, 0) else Color.argb(160, 255, 255, 255)
            }
            // Estimate text height for scrim
            val tempPaint = android.text.TextPaint().apply { textSize = 32f; typeface = Typeface.DEFAULT_BOLD }
            val slTemp = android.text.StaticLayout.Builder.obtain(text, 0, text.length, tempPaint, (drawW - 20).toInt())
                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                .build()
            val scrimRect = RectF(rect.left + 5f, rect.centerY() - (slTemp.height / 2f) - 5f, rect.right - 5f, rect.centerY() + (slTemp.height / 2f) + 5f)
            canvas.drawRoundRect(scrimRect, 8f, 8f, scrimPaint)
        }

        val thumbTextPaint = android.text.TextPaint().apply {
            this.color = textColor
            textSize = 32f
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val padding = 10f
        val sl = android.text.StaticLayout.Builder.obtain(text, 0, text.length, thumbTextPaint, (drawW - 2 * padding).toInt())
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.1f)
            .setMaxLines(4)
            .build()
        
        canvas.save()
        canvas.translate(rect.left + padding, rect.top + (drawH - sl.height) / 2f)
        sl.draw(canvas)
        canvas.restore()
    }

    private fun getBufferTypeface(buffer: com.example.doswriter.model.EditorBuffer): Typeface {
        val type = buffer.fontType
        val normalizedType = type.uppercase().replace("_", " ").replace("-", " ")
        val assetName = type.replace(" ", "")
        
        // Try Custom Fonts
        buffer.customFontsPath?.let { path ->
            try {
                if (path.startsWith("content://")) {
                    val root = DocumentFile.fromTreeUri(context, Uri.parse(path))
                    val fontFile = root?.findFile(type) ?: root?.findFile("$type.ttf") ?: root?.findFile("$type.otf")
                    fontFile?.let {
                        context.contentResolver.openInputStream(it.uri)?.use { input ->
                            val temp = java.io.File(context.cacheDir, "font_preview_${type.hashCode()}.ttf")
                            if (!temp.exists()) {
                                temp.outputStream().use { output -> input.copyTo(output) }
                            }
                            return Typeface.createFromFile(temp)
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        // Try Assets
        val extensions = listOf(".ttf", ".otf")
        for (ext in extensions) {
            try {
                val assetPath = "fonts/$assetName$ext"
                context.assets.open(assetPath).close()
                return Typeface.createFromAsset(context.assets, assetPath)
            } catch (e: Exception) {}
        }

        // Fallback to Standard
        return when (normalizedType) {
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
            else -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (visibility != VISIBLE) return
        val manager = bufferManager ?: return
        val mainActivity = context as? com.example.doswriter.MainActivity

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        
        val cols = 4
        val rows = 2
        val edgePadding = 30f
        val gridGap = 20f
        
        val availableW = width - 2 * edgePadding
        val availableH = height - 2 * edgePadding
        
        val cellW = (availableW - (cols - 1) * gridGap) / cols
        val cellH = (availableH - (rows - 1) * gridGap) / rows
        
        val labelSize = currentLabelSize
        val labelPadding = 12f
        
        val drawW = cellW
        val drawH = cellH - (labelSize + labelPadding)

        val emptyPaint = Paint().apply { color = Color.DKGRAY; style = Paint.Style.FILL }
        val labelPaint = Paint().apply { color = textPaint.color; textSize = labelSize; textAlign = Paint.Align.CENTER; isAntiAlias = true }

        for (i in 0 until tilesCount) {
            val buffer = manager.getBufferAt(i)
            
            val r = i / cols
            val c = i % cols
            
            val startX = edgePadding + c * (cellW + gridGap)
            val startY = edgePadding + r * (cellH + gridGap)
            
            pageRect.set(startX, startY, startX + drawW, startY + drawH)
            
            if (i == selectedIdx) {
                canvas.drawRect(pageRect.left - 8, pageRect.top - 8, pageRect.right + 8, pageRect.bottom + labelSize + labelPadding + 5f, highlightPaint)
            }

            // Determine Theme Colors
            var tileBgColor = Color.WHITE
            var tileTextColor = Color.BLACK
            
            if (mainActivity != null) {
                val cal = Calendar.getInstance()
                val currentTime = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                val dayStart = mainActivity.dayStartHour * 60 + mainActivity.dayStartMin
                val nightStart = mainActivity.nightStartHour * 60 + mainActivity.nightStartMin
                val isDay = if (mainActivity.themeMode == 1) true 
                           else if (mainActivity.themeMode == 2) false
                           else if (dayStart < nightStart) currentTime in dayStart until nightStart 
                           else currentTime >= dayStart || currentTime < nightStart

                val themeIdx = if (isDay) buffer.dayThemeIndex else buffer.nightThemeIndex
                val theme = mainActivity.themes.getOrNull(themeIdx)
                if (theme != null) {
                    tileBgColor = theme.backgroundColor
                    tileTextColor = theme.textColor
                }
            }
            
            snapshotBgPaint.color = tileBgColor
            canvas.drawRect(pageRect, snapshotBgPaint)
            
            var drawHandled = false
            val effectiveText = buffer.getEffectiveThumbnailText(manager)
            val imgUri = manager.globalThumbnailUri ?: buffer.thumbnailUri ?: buffer.imageUri

            when (currentViewMode) {
                LayoutViewMode.PREVIEW_MODE -> {
                    drawHandled = false
                }
                LayoutViewMode.IMAGE_MODE -> {
                    if (imgUri != null) {
                        val cacheKey = if (manager.globalThumbnailUri != null) 999 else i + 100
                        val thumb = getThumbnailFromUri(imgUri, cacheKey, drawW, drawH)
                        if (thumb != null) {
                            val left = pageRect.left + (drawW - thumb.width) / 2f
                            val top = pageRect.top + (drawH - thumb.height) / 2f
                            canvas.drawBitmap(thumb, left, top, null)
                            
                            if (manager.overlayTextOnImage && effectiveText != null) {
                                drawThumbnailText(canvas, effectiveText, pageRect, drawW, drawH, buffer, overlay = true)
                            }
                            drawHandled = true
                        }
                    }
                    if (!drawHandled && effectiveText != null) {
                        drawThumbnailText(canvas, effectiveText, pageRect, drawW, drawH, buffer)
                        drawHandled = true
                    }
                }
                LayoutViewMode.TEXT_MODE -> {
                    if (effectiveText != null) {
                        drawThumbnailText(canvas, effectiveText, pageRect, drawW, drawH, buffer)
                        drawHandled = true
                    }
                }
                LayoutViewMode.DEFAULT -> {
                    if (manager.useFilenameForThumbnailText) {
                        if (manager.overlayTextOnImage && imgUri != null) {
                            val cacheKey = if (manager.globalThumbnailUri != null) 999 else i + 100
                            val thumb = getThumbnailFromUri(imgUri, cacheKey, drawW, drawH)
                            if (thumb != null) {
                                val left = pageRect.left + (drawW - thumb.width) / 2f
                                val top = pageRect.top + (drawH - thumb.height) / 2f
                                canvas.drawBitmap(thumb, left, top, null)
                                if (effectiveText != null) {
                                    drawThumbnailText(canvas, effectiveText, pageRect, drawW, drawH, buffer, overlay = true)
                                }
                                drawHandled = true
                            }
                        }
                        if (!drawHandled && effectiveText != null) {
                            drawThumbnailText(canvas, effectiveText, pageRect, drawW, drawH, buffer)
                            drawHandled = true
                        }
                    } else if (manager.overlayTextOnImage && imgUri != null) {
                        val cacheKey = if (manager.globalThumbnailUri != null) 999 else i + 100
                        val thumb = getThumbnailFromUri(imgUri, cacheKey, drawW, drawH)
                        if (thumb != null) {
                            val left = pageRect.left + (drawW - thumb.width) / 2f
                            val top = pageRect.top + (drawH - thumb.height) / 2f
                            canvas.drawBitmap(thumb, left, top, null)
                            if (effectiveText != null) {
                                drawThumbnailText(canvas, effectiveText, pageRect, drawW, drawH, buffer, overlay = true)
                            }
                            drawHandled = true
                        }
                    } else if (buffer.useThumbnail) {
                        val pref = buffer.thumbnailPreference
                        if (pref == 1 && imgUri != null) {
                            val cacheKey = if (manager.globalThumbnailUri != null) 999 else i + 100
                            val thumb = getThumbnailFromUri(imgUri, cacheKey, drawW, drawH)
                            if (thumb != null) {
                                val left = pageRect.left + (drawW - thumb.width) / 2f
                                val top = pageRect.top + (drawH - thumb.height) / 2f
                                canvas.drawBitmap(thumb, left, top, null)
                                if (manager.overlayTextOnImage && effectiveText != null) {
                                    drawThumbnailText(canvas, effectiveText, pageRect, drawW, drawH, buffer, overlay = true)
                                }
                                drawHandled = true
                            }
                        } else if (pref == 2 && effectiveText != null) {
                            drawThumbnailText(canvas, effectiveText, pageRect, drawW, drawH, buffer)
                            drawHandled = true
                        }
                    }
                }
            }

            if (!drawHandled && currentViewMode != LayoutViewMode.TEXT_MODE && currentViewMode != LayoutViewMode.PREVIEW_MODE) {
                // Background Rendering (Image or PDF)
                if (buffer.imageUri != null) {
                    val thumb = getThumbnailFromUri(buffer.imageUri!!, i, drawW, drawH)
                    if (thumb != null) {
                        val left = pageRect.left + (drawW - thumb.width) / 2f
                        val top = pageRect.top + (drawH - thumb.height) / 2f
                        canvas.drawBitmap(thumb, left, top, null)
                        if (manager.overlayTextOnImage && effectiveText != null) {
                            drawThumbnailText(canvas, effectiveText, pageRect, drawW, drawH, buffer, overlay = true)
                        }
                        drawHandled = true
                    } else {
                        canvas.drawRect(pageRect, emptyPaint)
                    }
                } else if (buffer.pdfUri != null) {
                    val thumb = getThumbnailFromUri(buffer.pdfUri!!, i + 200, drawW, drawH, isPdf = true)
                    if (thumb != null) {
                        val left = pageRect.left + (drawW - thumb.width) / 2f
                        val top = pageRect.top + (drawH - thumb.height) / 2f
                        canvas.drawBitmap(thumb, left, top, null)
                        if (manager.overlayTextOnImage && effectiveText != null) {
                            drawThumbnailText(canvas, effectiveText, pageRect, drawW, drawH, buffer, overlay = true)
                        }
                        drawHandled = true
                    } else {
                        canvas.drawRect(pageRect, emptyPaint)
                    }
                }
            }

            if (!drawHandled) {
                // Text Snapshot Rendering (Always draw if not handled by dedicated thumbnail mode)
                val cleanText = buffer.getCleanText()
                if (cleanText.isEmpty()) {
                    if (buffer.imageUri == null && buffer.pdfUri == null) {
                        canvas.drawRect(pageRect, emptyPaint)
                        canvas.drawText("[Empty]", pageRect.centerX(), pageRect.centerY() + 10f, textPaint.apply { textSize = 24f; color = Color.LTGRAY })
                    }
                } else {
                    canvas.save()
                    canvas.clipRect(pageRect)
                    val padding = 12f
                    
                    // Improved chunk selection: ensure we show content even if cursor is at the end
                    val chunk = if (cleanText.length <= 1000) {
                        cleanText.trimStart()
                    } else {
                        val cursorIdx = (buffer.cursorPosition - buffer.prefix.length).coerceIn(0, cleanText.length)
                        val start = if (cursorIdx > 500) (cursorIdx - 200).coerceAtMost(cleanText.length - 500) else 0
                        val end = min(cleanText.length, start + 1000)
                        cleanText.substring(start, end).trimStart()
                    }
                    
                    if (chunk.isNotEmpty()) {
                        snapshotTextPaint.apply {
                            color = tileTextColor
                            textSize = 26f
                            typeface = getBufferTypeface(buffer)
                            isFakeBoldText = buffer.isBold
                        }

                        val sl = android.text.StaticLayout.Builder.obtain(chunk, 0, chunk.length, snapshotTextPaint, (drawW - 2 * padding).toInt())
                            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                            .setLineSpacing(0f, 1.1f)
                            .setMaxLines(10)
                            .setEllipsize(android.text.TextUtils.TruncateAt.END)
                            .build()
                        
                        // If there is an image, we might want a small scrim to make text readable
                        if (buffer.imageUri != null || buffer.pdfUri != null) {
                            val r = Color.red(tileBgColor); val g = Color.green(tileBgColor); val b = Color.blue(tileBgColor)
                            val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                            val scrimPaint = Paint().apply {
                                color = if (lum > 0.5) Color.argb(140, 255, 255, 255) else Color.argb(140, 0, 0, 0)
                            }
                            canvas.drawRect(pageRect.left + 5f, pageRect.top + 5f, pageRect.right - 5f, pageRect.top + padding + sl.height + 5f, scrimPaint)
                        }

                        canvas.translate(pageRect.left + padding, pageRect.top + padding)
                        sl.draw(canvas)
                    } else if (buffer.imageUri == null && buffer.pdfUri == null) {
                        canvas.drawText("[Start of File]", pageRect.centerX(), pageRect.centerY(), textPaint.apply { color = Color.GRAY; textSize = 20f })
                    }
                    canvas.restore()
                }

                if ((manager.overlayTextOnImage || manager.useFilenameForThumbnailText) && effectiveText != null) {
                    drawThumbnailText(canvas, effectiveText, pageRect, drawW, drawH, buffer, overlay = true)
                }
            }
            
            canvas.drawRect(pageRect, borderPaint)
            
            // Label with smart truncation logic and buffer number
            val syncInd = if (buffer.syncFilePath != null) " [S]" else ""
            val dirtyInd = if (buffer.isDirty) "*" else ""
            val bufferPrefix = if (manager.isTodoWorkspace) "T${i+1}: " else "B${i+1}: "
            val baseName = buffer.fileName ?: "EMPTY"
            val fullName = bufferPrefix + baseName + syncInd + dirtyInd
            
            val displayName = if (fullName.length <= 20) {
                fullName
            } else {
                val lastDot = baseName.lastIndexOf('.')
                if (lastDot > 0) {
                    val ext = baseName.substring(lastDot)
                    val baseMax = 18 - ext.length - syncInd.length - dirtyInd.length - bufferPrefix.length
                    if (baseMax > 0) bufferPrefix + baseName.take(baseMax) + "~" + ext + syncInd + dirtyInd
                    else bufferPrefix + baseName.take(15) + "~" + syncInd + dirtyInd
                } else bufferPrefix + baseName.take(15) + "~" + syncInd + dirtyInd
            }
            val labelY = pageRect.bottom + labelSize + 2f
            canvas.drawText(displayName, pageRect.centerX(), labelY, labelPaint)
        }
    }
}
