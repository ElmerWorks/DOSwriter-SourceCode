package com.example.minimaltexteditor.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class InputOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var title: String = ""
    private val inputText = StringBuilder()
    private var onSubmitted: ((String) -> Unit)? = null
    private var onCancelled: (() -> Unit)? = null

    private val bgPaint = Paint().apply {
        color = Color.rgb(20, 20, 20) // Fully opaque
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        isAntiAlias = true
    }

    fun show(title: String, initialValue: String = "", onSubmit: (String) -> Unit, onCancel: () -> Unit) {
        this.title = title
        inputText.setLength(0)
        inputText.append(initialValue)
        onSubmitted = onSubmit
        onCancelled = onCancel
        visibility = VISIBLE
        invalidate()
    }

    fun handleKey(char: Char) {
        inputText.append(char)
        invalidate()
    }

    fun deleteChar() {
        if (inputText.isNotEmpty()) {
            inputText.deleteCharAt(inputText.length - 1)
            invalidate()
        }
    }

    fun confirm() {
        val callback = onSubmitted
        hide()
        callback?.invoke(inputText.toString())
    }

    fun hide() {
        visibility = GONE
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            val width = width.toFloat()
            val height = height.toFloat()
            if (event.x < width * 0.1f || event.x > width * 0.9f || event.y < height * 0.3f || event.y > height * 0.6f) {
                hide()
                onCancelled?.invoke()
                return true
            }
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        
        canvas.drawRect(width * 0.1f, height * 0.3f, width * 0.9f, height * 0.6f, bgPaint)

        canvas.drawText(title.uppercase(), width * 0.15f, height * 0.4f, textPaint.apply { color = Color.GRAY })
        canvas.drawText(inputText.toString() + "_", width * 0.15f, height * 0.5f, textPaint.apply { color = Color.WHITE })
    }
}
