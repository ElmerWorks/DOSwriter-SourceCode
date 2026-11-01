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

class ManuscriptFilmstripOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var activeBuffer: EditorBuffer? = null
    private var pageBreaks: List<ManuscriptEngine.PageBreak> = emptyList()
    private var currentPageIdx = 0
    private val engine = ManuscriptEngine(context)
    
    private var pageBitmap: Bitmap? = null
    private val destRect = RectF()
    private val bgPaint = Paint().apply { color = Color.argb(200, 0, 0, 0) }
    private val pageBgPaint = Paint().apply { color = Color.WHITE }
    private val borderPaint = Paint().apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val infoPaint = Paint().apply { color = Color.WHITE; textSize = 40f; textAlign = Paint.Align.CENTER }

    private var workingFolderUri: String? = null

    var onPageSelected: ((Int) -> Unit)? = null
    var onPdfRequested: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    fun show(buffer: EditorBuffer, folderUri: String? = null) {
        this.activeBuffer = buffer
        this.workingFolderUri = folderUri
        this.pageBreaks = engine.paginate(buffer, workingFolderUri)
        
        // Find page containing current cursor
        val cursor = buffer.cursorPosition
        currentPageIdx = pageBreaks.indexOfFirst { cursor >= it.startCharIndex && cursor < it.endCharIndex }.coerceAtLeast(0)
        
        visibility = VISIBLE
        renderCurrentPage()
    }

    private fun renderCurrentPage() {
        val buffer = activeBuffer ?: return
        if (currentPageIdx !in pageBreaks.indices) return
        
        val dims = engine.getDimensions(buffer)
        
        // Create bitmap matching physical aspect ratio but scaled to screen
        // We'll use a fixed width for the bitmap and calculate height
        val bmpWidth = 1000 
        val bmpHeight = (bmpWidth * (dims.height / dims.width)).toInt()
        
        if (pageBitmap == null || pageBitmap?.width != bmpWidth || pageBitmap?.height != bmpHeight) {
            pageBitmap?.recycle()
            pageBitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        }
        
        engine.renderPage(buffer, pageBreaks[currentPageIdx], pageBitmap!!, workingFolderUri)
        invalidate()
    }

    fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        if (visibility != VISIBLE) return false
        
        if (event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_P) {
            onPdfRequested?.invoke()
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (currentPageIdx > 0) {
                    currentPageIdx--
                    renderCurrentPage()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (currentPageIdx < pageBreaks.size - 1) {
                    currentPageIdx++
                    renderCurrentPage()
                }
                return true
            }
            KeyEvent.KEYCODE_ENTER -> {
                if (activeBuffer?.id == 17) {
                    val entries = engine.getScratchLogEntries(activeBuffer, workingFolderUri)
                    if (currentPageIdx in entries.indices) {
                        activeBuffer?.setText(entries[currentPageIdx].text)
                    }
                    activeBuffer?.setCursor(activeBuffer!!.prefix.length, false)
                    onPageSelected?.invoke(0)
                } else {
                    val startIdx = pageBreaks.getOrNull(currentPageIdx)?.startCharIndex ?: 0
                    onPageSelected?.invoke(startIdx)
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

    fun hide() {
        visibility = GONE
        onDismiss?.invoke()
    }

    override fun onDraw(canvas: Canvas) {
        if (visibility != VISIBLE) return
        val buffer = activeBuffer ?: return
        val bitmap = pageBitmap ?: return
        
        // Draw dimmed background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        
        // Calculate centered Rect for the page
        val screenW = width.toFloat()
        val screenH = height.toFloat()
        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()
        
        // Scale to fit screen with margins
        val availableW = screenW * 0.9f
        val availableH = screenH * 0.8f
        val scale = min(availableW / bmpW, availableH / bmpH)
        
        val drawW = bmpW * scale
        val drawH = bmpH * scale
        val left = (screenW - drawW) / 2f
        val top = (screenH - drawH) / 2f
        
        destRect.set(left, top, left + drawW, top + drawH)
        
        // Draw page shadow/border
        canvas.drawRect(destRect.left - 4, destRect.top - 4, destRect.right + 4, destRect.bottom + 4, borderPaint)
        canvas.drawBitmap(bitmap, null, destRect, null)
        
        // Draw info
        val info = "Page ${currentPageIdx + 1} of ${pageBreaks.size}"
        canvas.drawText(info, screenW / 2f, destRect.bottom + 60f, infoPaint)
        canvas.drawText("Enter to jump | Esc to exit", screenW / 2f, destRect.bottom + 110f, infoPaint.apply { textSize = 30f })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        // Simple swipe detection could be added here
        return true
    }
}
