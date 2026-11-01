package com.example.doswriter.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var layout: LayoutDef? = null
    private val keyRects = mutableListOf<Pair<RectF, KeyDef>>()
    var keyboardTag: String = ""

    private val bgPaint = Paint().apply { color = Color.rgb(30, 30, 30) }
    private val keyPaint = Paint().apply { color = Color.rgb(60, 60, 60); style = Paint.Style.FILL }
    private val keyStrokePaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val textPaint = Paint().apply { 
        color = Color.WHITE
        textSize = 38f 
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val activeModifierPaint = Paint().apply { color = Color.rgb(0, 100, 0); style = Paint.Style.FILL }

    private val repeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var repeatingKey: KeyDef? = null
    private val repeatRunnable = object : Runnable {
        override fun run() {
            repeatingKey?.let {
                onKeyAction?.invoke(it)
                repeatHandler.postDelayed(this, 50)
            }
        }
    }

    var onKeyAction: ((KeyDef) -> Unit)? = null
    
    // Modifier states
    var ctrlActive = false
    var altActive = false
    var shiftActive = false
    var capsLockActive = false
    var showDevOverlay = false
    var showKeyBorder = true

    private val gridPaint = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val overlayTextPaint = Paint().apply { color = Color.RED; textSize = 20f; textAlign = Paint.Align.LEFT }

    fun loadLayout(json: String) {
        val type = object : TypeToken<LayoutDef>() {}.type
        val def: LayoutDef = Gson().fromJson(json, type)
        loadLayoutDef(def)
    }

    fun loadLayoutDef(def: LayoutDef) {
        Log.d("KeyboardView", "loadLayoutDef: ${def.layout_name}")
        layout = def
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        Log.d("KeyboardView", "onLayout: width=${right-left}, height=${bottom-top}")
    }

    fun setThemeColors(textColor: Int, bgColor: Int, highlightColor: Int = -1) {
        bgPaint.color = bgColor
        val r = Color.red(bgColor); val g = Color.green(bgColor); val b = Color.blue(bgColor)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
        
        if (luminance > 128) {
            // Light theme: make keys a solid, slightly darker version of the background
            keyPaint.color = Color.rgb(
                (r * 0.85).toInt(),
                (g * 0.85).toInt(),
                (b * 0.85).toInt()
            )
        } else {
            // Dark theme: make keys a solid, slightly lighter version of the background
            keyPaint.color = Color.rgb(
                minOf(255, r + 40),
                minOf(255, g + 40),
                minOf(255, b + 40)
            )
        }
        
        // Use provided highlight color (cursor color) or default to text color
        activeModifierPaint.color = if (highlightColor != -1) highlightColor else textColor
        
        textPaint.color = textColor
        keyStrokePaint.color = textColor
        keyStrokePaint.alpha = 255 // Zero transparency for the key outlines
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val l = layout ?: run {
            Log.d("KeyboardView", "onDraw: layout is null")
            return
        }
        if (width == 0 || height == 0) {
            Log.d("KeyboardView", "onDraw: size is 0 (w=$width, h=$height)")
            return
        }

        canvas.drawPaint(bgPaint)
        Log.d("KeyboardView", "onDraw: Drawing ${l.rows.size} rows")

        keyRects.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        val rowHeight = h / l.rows.size
        
        // IMPORTANT: Save the base text size to restore it after each key draw
        val baseTextSize = textPaint.textSize

        var currentY = 0f
        l.rows.forEachIndexed { rowIndex, row ->
            val isNumericRow = (rowIndex == 0 && keyboardTag == "alpha" && l.rows.size == 6)
            
            val actualRowHeight = if (isNumericRow) {
                rowHeight * 1.3f
            } else if (keyboardTag == "alpha" && l.rows.size == 6) {
                rowHeight * (1.0f - 0.3f / 5f)
            } else {
                rowHeight
            }

            val totalWeight = row.keys.sumOf { it.weight.toDouble() }.toFloat()
            val keyWidthUnit = w / totalWeight
            var currentX = 0f
            
            row.keys.forEach { key ->
                val keyW = key.weight * keyWidthUnit
                val rect = RectF(currentX + 4, currentY + 4, currentX + keyW - 4, currentY + actualRowHeight - 4)
                keyRects.add(rect to key)
                
                val isActiveModifier = (key.type == "MODIFIER" && key.value == "CTRL" && ctrlActive) || 
                                     (key.type == "MODIFIER" && key.value == "ALT" && altActive) || 
                                     (key.type == "MODIFIER" && key.value == "SHIFT" && shiftActive) ||
                                     (key.type == "MODIFIER" && key.value == "CAPS" && capsLockActive) ||
                                     (key.cmd == "CTRL_LOCK" && ctrlActive)
                
                if (key.type != "SPACER") {
                    canvas.drawRoundRect(rect, 8f, 8f, if (isActiveModifier) activeModifierPaint else keyPaint)
                    if (showKeyBorder) {
                        canvas.drawRoundRect(rect, 8f, 8f, keyStrokePaint)
                    }
                    
                    val label = if (shiftActive && key.type == "CHAR" && key.value?.length == 1) key.label.uppercase() else key.label
                    
                    // Apply custom scaling for this specific key
                    val currentKeyTextSize = key.font_scale?.let { baseTextSize * it } ?: baseTextSize
                    textPaint.textSize = currentKeyTextSize
                    
                    // For highlighted modifiers, ensure text is readable by contrasting with the highlight color
                    val originalTextColor = textPaint.color
                    if (isActiveModifier) {
                        // Use background color for text when key is highlighted
                        textPaint.color = bgPaint.color
                    }
                    
                    val isArrow = label in listOf("↑", "←", "↓", "→")
                    if (isArrow) textPaint.textSize = currentKeyTextSize * 1.5f
                    
                    if (key.icon_res != null) {
                        val resId = context.resources.getIdentifier(key.icon_res, "drawable", context.packageName)
                        if (resId != 0) {
                            val drawable = context.getDrawable(resId)
                            drawable?.let {
                                val iconSize = (rect.height() * 0.5f).toInt()
                                val left = (rect.centerX() - iconSize / 2).toInt()
                                val top = (rect.centerY() - iconSize / 2).toInt()
                                it.setBounds(left, top, left + iconSize, top + iconSize)
                                it.setTint(textPaint.color)
                                it.draw(canvas)
                            }
                        }
                    } else if (label.contains("\n")) {
                        val parts = label.split("\n")
                        val lineSpacing = textPaint.textSize * 1.1f
                        var startY = rect.centerY() - (parts.size - 1) * lineSpacing / 2f
                        parts.forEach { part ->
                            canvas.drawText(part, rect.centerX(), startY - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
                            startY += lineSpacing
                        }
                    } else {
                        canvas.drawText(label, rect.centerX(), rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2, textPaint)
                    }
                    
                    // Reset text paint state before moving to next key
                    textPaint.textSize = baseTextSize
                    textPaint.color = originalTextColor
                }
                
                currentX += keyW
            }
            currentY += actualRowHeight
        }

        if (showDevOverlay) {
            drawDevOverlay(canvas, w, h, l)
        }
    }

    private fun drawDevOverlay(canvas: Canvas, w: Float, h: Float, l: LayoutDef) {
        val cols = 10
        val rows = l.rows.size
        val cellW = w / cols
        val cellH = h / rows
        
        for (i in 0..cols) {
            val x = i * cellW
            canvas.drawLine(x, 0f, x, h, gridPaint)
        }
        for (j in 0..rows) {
            val y = j * cellH
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        for (j in 0 until rows) {
            for (i in 0 until cols) {
                canvas.drawText("R${j+1}C${i+1}", i * cellW + 5, j * cellH + 25, overlayTextPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                keyRects.forEach { (rect, key) ->
                    if (rect.contains(event.x, event.y)) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        handleKeyInternal(key)
                        onKeyAction?.invoke(key)
                        if (key.cmd == "DELETE" || key.cmd == "DELETE_FORWARD" || key.cmd == "UP" || key.cmd == "DOWN" || key.cmd == "LEFT" || key.cmd == "RIGHT") {
                            repeatingKey = key
                            repeatHandler.postDelayed(repeatRunnable, 400)
                        }
                        invalidate()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                repeatHandler.removeCallbacks(repeatRunnable)
                repeatingKey = null
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleKeyInternal(key: KeyDef) {
        if (key.type == "MODIFIER") {
            when (key.value) {
                "CTRL" -> { ctrlActive = !ctrlActive; invalidate() }
                "ALT" -> { altActive = !altActive; invalidate() }
                "SHIFT" -> { shiftActive = !shiftActive; invalidate() }
                "CAPS" -> { capsLockActive = !capsLockActive; invalidate() }
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
