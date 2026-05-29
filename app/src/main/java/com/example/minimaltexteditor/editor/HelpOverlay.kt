package com.example.minimaltexteditor.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class HelpOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class RenderedLine(val text: String, val paint: Paint, val indent: Float)

    private var renderedLines: List<RenderedLine> = emptyList()
    private var scrollOffset = 0f
    private var totalHeight = 0f
    
    private val bgPaint = Paint().apply {
        color = Color.rgb(5, 5, 5) // Fully opaque
        style = Paint.Style.FILL
    }

    private val basePaint = Paint().apply {
        color = Color.LTGRAY
        textSize = 32f
        isAntiAlias = true
        typeface = Typeface.SANS_SERIF
    }

    private val h1Paint = Paint(basePaint).apply {
        color = Color.WHITE
        textSize = 48f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val h2Paint = Paint(basePaint).apply {
        color = Color.CYAN
        textSize = 40f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val boldPaint = Paint(basePaint).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    fun show(text: String) {
        visibility = VISIBLE
        // Post to ensure width is available for wrapping
        post {
            prepareRendering(text)
            scrollOffset = 0f
            invalidate()
        }
    }

    private fun prepareRendering(text: String) {
        val result = mutableListOf<RenderedLine>()
        val maxWidth = width - 80f // Margin
        
        text.lines().forEach { line ->
            var paint = basePaint
            var indent = 0f
            var processedLine = line.trimEnd()

            if (processedLine.startsWith("# ")) {
                paint = h1Paint
                processedLine = processedLine.substring(2)
            } else if (processedLine.startsWith("## ")) {
                paint = h2Paint
                processedLine = processedLine.substring(3)
            } else if (processedLine.startsWith("- ")) {
                indent = 30f
                processedLine = "• " + processedLine.substring(2)
            }

            // Bold detection for entire line (simple version)
            if (processedLine.startsWith("**") && processedLine.endsWith("**")) {
                paint = boldPaint
                processedLine = processedLine.substring(2, processedLine.length - 2)
            }

            if (processedLine.isEmpty()) {
                result.add(RenderedLine("", basePaint, 0f))
            } else {
                val wrapped = wrapText(processedLine, paint, maxWidth - indent)
                wrapped.forEach { result.add(RenderedLine(it, paint, indent)) }
            }
        }
        renderedLines = result
        
        // Calculate total height
        totalHeight = renderedLines.sumOf { (it.paint.fontMetrics.bottom - it.paint.fontMetrics.top).toDouble() + 8.0 }.toFloat()
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines
    }

    fun hide() {
        visibility = GONE
    }

    fun scrollUp() {
        scrollOffset = max(0f, scrollOffset - 100f)
        invalidate()
    }

    fun scrollDown() {
        scrollOffset = min(max(0f, totalHeight - height), scrollOffset + 100f)
        invalidate()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && 
            (event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER) || event.isFromSource(InputDevice.SOURCE_MOUSE))) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (vScroll != 0f) {
                scrollOffset -= vScroll * 150f
                scrollOffset = scrollOffset.coerceIn(0f, max(0f, totalHeight - height))
                invalidate()
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        if (renderedLines.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val startX = 40f
        var currentY = 60f - scrollOffset

        renderedLines.forEach { rLine ->
            val metrics = rLine.paint.fontMetrics
            val lineHeight = metrics.bottom - metrics.top + 8f
            
            if (currentY + lineHeight > 0 && currentY < h + lineHeight) {
                if (rLine.text.isNotEmpty()) {
                    canvas.drawText(rLine.text, startX + rLine.indent, currentY - metrics.top, rLine.paint)
                }
            }
            currentY += lineHeight
        }
    }
}
