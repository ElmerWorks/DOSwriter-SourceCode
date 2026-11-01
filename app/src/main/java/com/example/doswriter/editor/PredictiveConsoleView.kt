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
import java.util.Locale

class PredictiveConsoleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint().apply { color = Color.rgb(20, 20, 20) }
    private val consolePaint = Paint().apply { color = Color.rgb(40, 40, 40); style = Paint.Style.FILL }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 36f; isAntiAlias = true }
    private val suggestionPaint = Paint().apply { color = Color.rgb(60, 60, 60); style = Paint.Style.FILL }
    private val highlightPaint = Paint().apply { color = Color.rgb(0, 120, 0); style = Paint.Style.FILL }

    var composingText = StringBuilder()
    private var suggestions = mutableListOf<String>()
    
    private val suggestionRects = mutableListOf<Pair<RectF, String>>()
    private val copyRect = RectF()
    private val clearRect = RectF()

    var onSuggestionSelected: ((String) -> Unit)? = null
    var onCopyClicked: ((String) -> Unit)? = null
    var onClearClicked: (() -> Unit)? = null

    private class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        var isWord = false
    }
    private val root = TrieNode()

    fun initDictionary(words: List<String>) {
        words.forEach { word ->
            var curr = root
            word.lowercase().forEach { char ->
                curr = curr.children.getOrPut(char) { TrieNode() }
            }
            curr.isWord = true
        }
    }

    fun updateComposing(text: String) {
        composingText.setLength(0)
        composingText.append(text)
        updateSuggestions()
        invalidate()
    }

    fun deleteLastChar() {
        if (composingText.isNotEmpty()) {
            composingText.deleteCharAt(composingText.length - 1)
            updateSuggestions()
            invalidate()
        }
    }

    private fun updateSuggestions() {
        suggestions.clear()
        if (composingText.isEmpty()) return

        val lastWord = composingText.split(" ").last().lowercase()
        if (lastWord.isEmpty()) return

        var curr = root
        for (char in lastWord) {
            curr = curr.children[char] ?: return
        }

        // BFS to find completions
        val results = mutableListOf<String>()
        findWords(curr, lastWord, results)
        suggestions.addAll(results.take(5))
    }

    private fun findWords(node: TrieNode, prefix: String, results: MutableList<String>) {
        if (results.size >= 10) return
        if (node.isWord) results.add(prefix)
        node.children.keys.sorted().forEach { char ->
            findWords(node.children[char]!!, prefix + char, results)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawPaint(bgPaint)

        // Console area (top ~62% based on proportions)
        val consoleH = h * 0.62f
        
        // Clear Icon (top left)
        clearRect.set(10f, 10f, 90f, consoleH - 5f)
        canvas.drawRect(clearRect, consolePaint)
        textPaint.textSize = 40f
        canvas.drawText("X", clearRect.centerX() - 10f, clearRect.centerY() + 15f, textPaint)

        // Main Console box (center)
        canvas.drawRect(100f, 10f, w - 100f, consoleH - 5f, consolePaint)
        
        // Match font size to console height (approx 70% of area)
        textPaint.textSize = consoleH * 0.5f
        canvas.drawText(composingText.toString() + "_", 120f, consoleH * 0.65f, textPaint)

        // Copy Icon (top right)
        copyRect.set(w - 90f, 10f, w - 10f, consoleH - 5f)
        canvas.drawRect(copyRect, consolePaint)
        textPaint.textSize = 40f
        canvas.drawText("📋", copyRect.centerX() - 20f, copyRect.centerY() + 15f, textPaint)

        // Suggestions area (bottom row)
        suggestionRects.clear()
        if (suggestions.isNotEmpty()) {
            val sugW = (w - 20f) / suggestions.size
            var currentX = 10f
            suggestions.forEach { sug ->
                val rect = RectF(currentX, consoleH + 5f, currentX + sugW - 10f, h - 10f)
                suggestionRects.add(rect to sug)
                canvas.drawRoundRect(rect, 8f, 8f, suggestionPaint)
                
                textPaint.textSize = 32f
                val label = if (sug.length > 12) sug.take(9) + ".." else sug
                canvas.drawText(label, rect.centerX() - textPaint.measureText(label)/2, rect.centerY() + 12f, textPaint)
                currentX += sugW
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            // Check suggestions
            suggestionRects.forEach { (rect, sug) ->
                if (rect.contains(event.x, event.y)) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val fullText = composingText.toString()
                    val lastSpace = fullText.lastIndexOf(" ")
                    val newText = if (lastSpace == -1) sug else fullText.substring(0, lastSpace + 1) + sug
                    composingText.setLength(0)
                    composingText.append(newText + " ")
                    updateSuggestions()
                    invalidate()
                    onSuggestionSelected?.invoke(composingText.toString())
                    return true
                }
            }
            // Check copy
            if (copyRect.contains(event.x, event.y)) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onCopyClicked?.invoke(composingText.toString())
                return true
            }
            // Check clear
            if (clearRect.contains(event.x, event.y)) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClearClicked?.invoke()
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
}
