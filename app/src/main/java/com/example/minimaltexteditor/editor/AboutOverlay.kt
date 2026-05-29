package com.example.minimaltexteditor.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class AboutOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint().apply { color = Color.rgb(5, 5, 5) } // Fully opaque
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val lines = listOf(
        "ElmerPad Android text editing app",
        "",
        "Build version : ElmerPadv0.1-051726  May 17, 2026",
        "",
        "This program is distributed in the hope that it will be useful,",
        "but WITHOUT ANY WARRANTY; without even the implied warranty",
        "of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE."
    )

    fun show() {
        visibility = VISIBLE
        invalidate()
    }

    fun hide() {
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        var currentY = h * 0.3f // Start a bit lower since logo is gone

        lines.forEach { line ->
            if (line.isNotEmpty()) {
                canvas.drawText(line, w / 2f, currentY, textPaint)
            }
            currentY += 50f
        }
    }
}
