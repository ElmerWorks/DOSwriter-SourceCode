package com.example.doswriter.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.example.doswriter.logic.ManuscriptEngine
import com.example.doswriter.model.EditorBuffer
import kotlin.math.max
import kotlin.math.min

class ManuscriptLayoutOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ViewMode { GRID, DETAIL }
    private var viewMode = ViewMode.GRID

    private var activeBuffer: EditorBuffer? = null
    private var pageBreaks: List<ManuscriptEngine.PageBreak> = emptyList()
    private var windowStartIdx = 0 // The index of the first page in the window
    private var selectedIdxInWindow = 0 
    private var tilesCount = 8
    
    private val engine = ManuscriptEngine(context)
    private val thumbnailCache = mutableMapOf<Int, Bitmap>()
    private var detailBitmap: Bitmap? = null
    
    private val bgPaint = Paint().apply { color = Color.argb(230, 0, 0, 0) }
    private val borderPaint = Paint().apply { color = Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val highlightPaint = Paint().apply { color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 6f }
    private val infoPaint = Paint().apply { color = Color.WHITE; textSize = 32f; textAlign = Paint.Align.CENTER }
    private val pageRect = RectF()

    private var workingFolderUri: String? = null

    var onPageSelected: ((Int) -> Unit)? = null
    var onPdfRequested: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    fun show(buffer: EditorBuffer, folderUri: String? = null) {
        this.activeBuffer = buffer
        this.workingFolderUri = folderUri
        this.pageBreaks = engine.paginate(buffer, workingFolderUri)
        this.viewMode = ViewMode.GRID
        this.tilesCount = buffer.manuscriptSettings.layoutTileCount
        
        // Center the window on the current cursor position
        val cursor = buffer.cursorPosition
        val currentGlobalIdx = pageBreaks.indexOfFirst { cursor >= it.startCharIndex && cursor < it.endCharIndex }.coerceAtLeast(0)
        
        windowStartIdx = (currentGlobalIdx / tilesCount) * tilesCount
        selectedIdxInWindow = currentGlobalIdx % tilesCount
        
        thumbnailCache.values.forEach { it.recycle() }
        thumbnailCache.clear()
        detailBitmap?.recycle()
        detailBitmap = null
        
        visibility = VISIBLE
        invalidate()
    }

    fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        if (visibility != VISIBLE) return false
        
        val isCtrl = event.isCtrlPressed
        if (isCtrl && keyCode == KeyEvent.KEYCODE_P) {
            onPdfRequested?.invoke()
            return true
        }

        if (viewMode == ViewMode.DETAIL) {
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    val globalIdx = windowStartIdx + selectedIdxInWindow
                    if (globalIdx in pageBreaks.indices) {
                        if (activeBuffer?.id == 17) {
                            val entries = engine.getScratchLogEntries(activeBuffer, workingFolderUri)
                            if (globalIdx in entries.indices) {
                                activeBuffer?.setText(entries[globalIdx].text)
                            }
                            activeBuffer?.setCursor(activeBuffer!!.prefix.length, false)
                            onPageSelected?.invoke(0)
                        } else {
                            onPageSelected?.invoke(pageBreaks[globalIdx].startCharIndex)
                        }
                    }
                    hide()
                    return true
                }
                KeyEvent.KEYCODE_ESCAPE -> {
                    viewMode = ViewMode.GRID
                    invalidate()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (windowStartIdx + selectedIdxInWindow > 0) {
                        if (selectedIdxInWindow > 0) {
                            selectedIdxInWindow--
                        } else {
                            windowStartIdx -= tilesCount
                            selectedIdxInWindow = tilesCount - 1
                        }
                        renderDetail()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (windowStartIdx + selectedIdxInWindow < pageBreaks.size - 1) {
                        if (selectedIdxInWindow < tilesCount - 1) {
                            selectedIdxInWindow++
                        } else {
                            windowStartIdx += tilesCount
                            selectedIdxInWindow = 0
                        }
                        renderDetail()
                    }
                    return true
                }
            }
            return true // Consume all keys in detail mode
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isCtrl) {
                    windowStartIdx = max(0, windowStartIdx - tilesCount)
                } else {
                    if (selectedIdxInWindow > 0) {
                        selectedIdxInWindow--
                    } else if (windowStartIdx > 0) {
                        windowStartIdx -= tilesCount
                        selectedIdxInWindow = tilesCount - 1
                    }
                }
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isCtrl) {
                    if (windowStartIdx + tilesCount < pageBreaks.size) {
                        windowStartIdx += tilesCount
                        // Ensure selection doesn't exceed new page count
                        if (windowStartIdx + selectedIdxInWindow >= pageBreaks.size) {
                            selectedIdxInWindow = (pageBreaks.size - 1) % tilesCount
                        }
                    }
                } else {
                    if (selectedIdxInWindow < tilesCount - 1 && (windowStartIdx + selectedIdxInWindow + 1) < pageBreaks.size) {
                        selectedIdxInWindow++
                    } else if (windowStartIdx + tilesCount < pageBreaks.size) {
                        windowStartIdx += tilesCount
                        selectedIdxInWindow = 0
                    }
                }
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                val cols = if (tilesCount == 4) 2 else 4
                if (selectedIdxInWindow >= cols) {
                    selectedIdxInWindow -= cols
                } else if (windowStartIdx > 0) {
                    windowStartIdx = max(0, windowStartIdx - tilesCount)
                }
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val cols = if (tilesCount == 4) 2 else 4
                if (selectedIdxInWindow < cols && (windowStartIdx + selectedIdxInWindow + cols) < pageBreaks.size) {
                    selectedIdxInWindow += cols
                } else if (windowStartIdx + tilesCount < pageBreaks.size) {
                    windowStartIdx += tilesCount
                }
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_ENTER -> {
                if (isCtrl) {
                    viewMode = ViewMode.DETAIL
                    renderDetail()
                    return true
                }
                val globalIdx = windowStartIdx + selectedIdxInWindow
                if (globalIdx in pageBreaks.indices) {
                    if (activeBuffer?.id == 17) {
                        val entries = engine.getScratchLogEntries(activeBuffer)
                        if (globalIdx in entries.indices) {
                            activeBuffer?.setText(entries[globalIdx].text)
                        }
                        activeBuffer?.setCursor(activeBuffer!!.prefix.length, false)
                        onPageSelected?.invoke(0)
                    } else {
                        onPageSelected?.invoke(pageBreaks[globalIdx].startCharIndex)
                    }
                }
                hide()
                return true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                hide()
                return true
            }
        }
        return false
    }

    private fun renderDetail() {
        val buffer = activeBuffer ?: return
        val globalIdx = windowStartIdx + selectedIdxInWindow
        if (globalIdx !in pageBreaks.indices) return

        val dims = engine.getDimensions(buffer)
        val aspect = dims.height / dims.width
        
        val targetW = 1200
        val targetH = (targetW * aspect).toInt()

        if (detailBitmap == null || detailBitmap?.width != targetW || detailBitmap?.height != targetH) {
            detailBitmap?.recycle()
            detailBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        }

        engine.renderPage(buffer, pageBreaks[globalIdx], detailBitmap!!, workingFolderUri)
        invalidate()
    }

    fun hide() {
        visibility = GONE
        detailBitmap?.recycle()
        detailBitmap = null
        onDismiss?.invoke()
    }

    override fun onDraw(canvas: Canvas) {
        if (visibility != VISIBLE) return
        val buffer = activeBuffer ?: return
        
        if (viewMode == ViewMode.DETAIL) {
            drawDetailView(canvas)
            return
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        
        val cols = if (tilesCount == 4) 2 else 4
        val rows = 2
        val edgePadding = 20f
        val gridGap = 15f
        
        val availableW = width - 2 * edgePadding
        val availableH = height - 2 * edgePadding
        
        val cellW = availableW / cols
        val cellH = availableH / rows
        
        val dims = engine.getDimensions(buffer)
        val pageAspectRatio = dims.height / dims.width
        
        // Calculate maximum fit for the page within the cell
        var drawW = cellW - gridGap
        var drawH = drawW * pageAspectRatio
        if (drawH > cellH - gridGap) {
            drawH = cellH - gridGap
            drawW = drawH / pageAspectRatio
        }

        val circlePaint = Paint().apply { color = Color.argb(180, 0, 0, 0); style = Paint.Style.FILL }
        val circleNumPaint = Paint().apply { color = Color.WHITE; textSize = 24f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }

        for (i in 0 until tilesCount) {
            val globalIdx = windowStartIdx + i
            if (globalIdx >= pageBreaks.size) break
            
            val r = i / cols
            val c = i % cols
            
            val centerX = edgePadding + c * cellW + cellW / 2f
            val centerY = edgePadding + r * cellH + cellH / 2f
            
            pageRect.set(centerX - drawW / 2f, centerY - drawH / 2f, centerX + drawW / 2f, centerY + drawH / 2f)
            
            // Draw page background
            canvas.drawRect(pageRect, Paint().apply { color = Color.WHITE })
            
            var thumb = thumbnailCache[globalIdx]
            if (thumb == null) {
                val tw = 400 // Slightly higher res for larger view
                val th = (tw * pageAspectRatio).toInt()
                thumb = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
                engine.renderPage(buffer, pageBreaks[globalIdx], thumb, workingFolderUri)
                thumbnailCache[globalIdx] = thumb
            }
            
            canvas.drawBitmap(thumb!!, null, pageRect, null)
            canvas.drawRect(pageRect, borderPaint)
            
            // Draw highlight if selected
            if (i == selectedIdxInWindow) {
                canvas.drawRect(pageRect.left - 5, pageRect.top - 5, pageRect.right + 5, pageRect.bottom + 5, highlightPaint)
            }
            
            // --- Page Number Overlay Circle ---
            val radius = 25f
            val cx = pageRect.left + radius + 10f
            val cy = pageRect.top + radius + 10f
            canvas.drawCircle(cx, cy, radius, circlePaint)
            canvas.drawText("${globalIdx + 1}", cx, cy + 8f, circleNumPaint)
        }
        
        // --- Floating Info Box ---
        val windowInfo = if (tilesCount == 1) "Page ${windowStartIdx + 1} of ${pageBreaks.size}" 
                        else "Pages ${windowStartIdx + 1} - ${min(windowStartIdx + tilesCount, pageBreaks.size)} of ${pageBreaks.size}"
        val boxW = 400f
        val boxH = 60f
        val boxRect = RectF(width/2f - boxW/2f, height - boxH - 30f, width/2f + boxW/2f, height - 30f)
        canvas.drawRoundRect(boxRect, 10f, 10f, circlePaint)
        canvas.drawText(windowInfo, width / 2f, boxRect.centerY() + 12f, infoPaint.apply { textSize = 28f })
    }

    private fun drawDetailView(canvas: Canvas) {
        val bitmap = detailBitmap ?: return
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val screenW = width.toFloat()
        val screenH = height.toFloat()
        
        val availableW = screenW * 0.95f
        val availableH = screenH * 0.9f
        
        val scale = min(availableW / bitmap.width, availableH / bitmap.height)
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        
        val left = (screenW - drawW) / 2f
        val top = (screenH - drawH) / 2f
        
        destRect.set(left, top, left + drawW, top + drawH)
        canvas.drawRect(destRect.left - 2, destRect.top - 2, destRect.right + 2, destRect.bottom + 2, borderPaint)
        canvas.drawBitmap(bitmap, null, destRect, null)

        val globalIdx = windowStartIdx + selectedIdxInWindow
        val info = "Page ${globalIdx + 1} of ${pageBreaks.size}"
        canvas.drawText(info, screenW / 2f, screenH - 70f, infoPaint.apply { textSize = 32f })
        canvas.drawText("Enter to Select | Esc for Grid", screenW / 2f, screenH - 30f, infoPaint.apply { textSize = 24f; color = Color.LTGRAY })
    }

    private val destRect = RectF()
}
