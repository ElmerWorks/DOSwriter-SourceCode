package com.example.doswriter.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CommandScriptOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentInstruction = ""
    private var progressText = ""
    private var isPaused = false
    
    private val bgPaint = Paint().apply { color = Color.argb(200, 0, 0, 0) }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 28f; isAntiAlias = true }
    private val subTextPaint = Paint().apply { color = Color.LTGRAY; textSize = 20f; isAntiAlias = true }
    private val accentPaint = Paint().apply { color = Color.CYAN; style = Paint.Style.FILL }
    private val bgRect = RectF()

    fun update(instruction: String, progress: String, paused: Boolean) {
        currentInstruction = instruction
        progressText = progress
        isPaused = paused
        visibility = VISIBLE
        invalidate()
    }

    fun hide() {
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        if (visibility != VISIBLE) return

        val margin = 20f
        val h = 100f
        val w = width * 0.6f
        val x = (width - w) / 2f
        val y = 40f // Show at the top

        bgRect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(bgRect, 10f, 10f, bgPaint)
        
        // Status indicator
        val indicatorColor = if (isPaused) Color.YELLOW else Color.GREEN
        accentPaint.color = indicatorColor
        canvas.drawCircle(x + 30f, y + h / 2f, 10f, accentPaint)

        val textX = x + 60f
        val mainY = y + 45f
        canvas.drawText("Script: $currentInstruction", textX, mainY, textPaint)
        
        val status = if (isPaused) "PAUSED [F8 to Step]" else "RUNNING [Ctrl-Alt-F8 to Stop]"
        canvas.drawText("$progressText | $status", textX, mainY + 35f, subTextPaint)
    }
}
