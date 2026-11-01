package com.example.doswriter.editor

import android.view.KeyEvent
import android.view.MotionEvent
import com.example.doswriter.MainActivity
import com.example.doswriter.model.EditorBuffer
import kotlin.math.abs
import kotlin.math.max

internal fun EditorView.handleGenericMotionEvent(event: MotionEvent): Boolean {
    val manager = (context as? MainActivity)?.bufferManager
    if (event.action == MotionEvent.ACTION_SCROLL) {
        val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        if (vScroll != 0f) {
            val direction = if (vScroll > 0f) -1f else 1f
            if (activeBuffer?.imageUri != null) {
                activeBuffer?.imageScale = (activeBuffer?.imageScale ?: 1f) * (1f + vScroll * 0.1f)
                invalidate()
                return true
            } else if (activeBuffer?.pdfUri != null) {
                val scrollAmount = if (manager?.scrollWheelMode == 1) height.toFloat() else (manager?.scrollWheelLines ?: 3) * 80f
                scrollYOffset = (scrollYOffset + direction * scrollAmount).coerceAtLeast(0f)
                invalidate()
                return true
            } else if (viewerImageUri != null) {
                viewerScale *= (1f + vScroll * 0.1f)
                invalidate()
                return true
            } else {
                val scrollAmount = if (manager?.scrollWheelMode == 1) {
                    (height.toFloat() - 40f).coerceAtLeast(lineHeight)
                } else {
                    lineHeight * (manager?.scrollWheelLines ?: 3)
                }
                scrollManually(direction * scrollAmount)
                return true
            }
        }
    }
    return false
}

internal fun EditorView.handleTouchEvent(event: MotionEvent): Boolean {
    val manager = (context as? MainActivity)?.bufferManager

    if (activeBuffer?.pdfUri != null) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
                
                if (event.buttonState == MotionEvent.BUTTON_PRIMARY) {
                    isPdfSelecting = true
                    pdfSelectionStart.set(event.x, event.y)
                    pdfSelectionEnd.set(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPdfSelecting) {
                    pdfSelectionEnd.set(event.x, event.y)
                    invalidate()
                    return true
                }

                val dy = event.y - lastTouchY
                if (abs(dy) > 10 || isDragging) {
                    isDragging = true
                    scrollYOffset -= dy
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isPdfSelecting) {
                    isPdfSelecting = false
                    pdfSelectionStart.set(0f, 0f)
                    pdfSelectionEnd.set(0f, 0f)
                    invalidate()
                    return true
                }
                isDragging = false
                return true
            }
        }
    }

    if (viewerImageUri != null) {
        scaleGestureDetector.onTouchEvent(event)
        if (!scaleGestureDetector.isInProgress) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastTouchX = event.x; lastTouchY = event.y; isDragging = true }
                MotionEvent.ACTION_MOVE -> if (isDragging) { viewerPanX += (event.x - lastTouchX); viewerPanY += (event.y - lastTouchY); lastTouchX = event.x; lastTouchY = event.y; invalidate() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isDragging = false
            }
        }
        return true
    }

    if (event.action == MotionEvent.ACTION_DOWN && (event.buttonState == MotionEvent.BUTTON_SECONDARY || event.isButtonPressed(MotionEvent.BUTTON_SECONDARY))) {
        onRightClick?.invoke(event.x, event.y)
        return true
    }

    val isCtrl = (event.metaState and KeyEvent.META_CTRL_ON) != 0
    val isSelectMode = manager?.isLeftDragSelectMode ?: true

    when (event.action) {
        MotionEvent.ACTION_DOWN -> {
            lastTouchX = event.x
            lastTouchY = event.y
            isDragging = false

            if (!isCtrl && isSelectMode) {
                isSelectingText = true
                val idx = getIndexAt(event.x, event.y)
                activeBuffer?.setSelection(idx, idx)
                invalidate()
                onCursorMoved?.invoke()
            }
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            val dx = abs(event.x - lastTouchX)
            val dy = event.y - lastTouchY

            if (isCtrl || !isSelectMode) {
                if (abs(dy) > 5 || isDragging) {
                    isDragging = true
                    scrollYOffset = (scrollYOffset - dy).coerceAtLeast(0f)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
            } else if (isSelectingText) {
                if (dx > 5 || abs(dy) > 5) {
                    isDragging = true
                    val currentIdx = getIndexAt(event.x, event.y)
                    activeBuffer?.setCursor(currentIdx, selecting = true)
                    invalidate()
                    onCursorMoved?.invoke()
                    return true
                }
            }
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (isSelectingText && !isDragging) {
                val idx = getIndexAt(event.x, event.y)
                activeBuffer?.setCursor(idx, selecting = false)
                invalidate()
                onCursorMoved?.invoke()
                if (!isVirtualKeyboardEnabled) onRequestedShowKeyboard?.invoke()
            }
            isSelectingText = false
            isDragging = false
            return true
        }
    }
    return false
}
