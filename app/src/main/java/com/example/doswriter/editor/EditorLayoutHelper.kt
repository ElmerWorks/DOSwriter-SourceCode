package com.example.doswriter.editor

import android.graphics.*
import android.net.Uri
import com.example.doswriter.model.EditorBuffer
import kotlin.math.*

internal fun EditorView.recalculateLayout() {
    val currentBuffer = activeBuffer ?: return
    val text = currentBuffer.getText()
    val version = currentBuffer.version

    val sideMargin = currentBuffer.sideMargin
    val effectiveSideMargin = if (currentBuffer.showMargins) sideMargin + 2 * charWidth else sideMargin
    val maxWidth = width - 2 * effectiveSideMargin
    val marginsHash = effectiveSideMargin.hashCode() xor width.hashCode()

    if (width <= 0 || (lastLayoutWidth == width.toFloat() && lastLayoutVersion == version && lastLayoutPaintHash == textPaint.hashCode() && lastLayoutMarginsHash == marginsHash)) {
        return
    }

    visualLines.clear()
    val activeFolds = currentBuffer.folds
    var foldIdx = 0
    var i = 0
    var lineStart = 0
    var currentX = 0f
    
    val textLen = text.length
    while (i < textLen) {
        while (foldIdx < activeFolds.size && activeFolds[foldIdx].start < i) {
            foldIdx++
        }

        if (foldIdx < activeFolds.size && i == activeFolds[foldIdx].start) {
            val fold = activeFolds[foldIdx]
            if (i > lineStart) visualLines.add(EditorView.VisualLine(lineStart, i - 1))
            visualLines.add(EditorView.VisualLine(fold.start, fold.end, isFolded = true))
            i = max(i + 1, fold.end + 1)
            lineStart = i
            currentX = 0f
            foldIdx++
            continue
        }

        val char = text[i]
        
        if (char == '\n') {
            visualLines.add(EditorView.VisualLine(lineStart, i))
            lineStart = i + 1
            currentX = 0f
            i++
            continue
        }

        if (char.isWhitespace()) {
            val charW = if (isMonospace) charWidth else textPaint.measureText(text, i, i + 1)
            if (currentX + charW > maxWidth && currentX > 0) {
                // If a space triggers a wrap, include it at the end of the current line
                // instead of starting the next line with an indentation.
                visualLines.add(EditorView.VisualLine(lineStart, i))
                lineStart = i + 1
                currentX = 0f
            } else {
                currentX += charW
            }
            i++
            continue
        }

        val wordEnd = findWordEnd(text, i)
        val safeWordEnd = if (foldIdx < activeFolds.size && activeFolds[foldIdx].start < wordEnd) {
            activeFolds[foldIdx].start
        } else {
            wordEnd
        }

        val wordWidth = if (isMonospace) (safeWordEnd - i) * charWidth else textPaint.measureText(text, i, safeWordEnd)
        
        if (wordWidth > maxWidth) {
            while (i < safeWordEnd) {
                val cw = if (isMonospace) charWidth else textPaint.measureText(text, i, i + 1)
                if (currentX + cw > maxWidth && currentX > 0) {
                    visualLines.add(EditorView.VisualLine(lineStart, i - 1))
                    lineStart = i
                    currentX = 0f
                }
                currentX += cw
                i++
            }
        } else if (currentX > 0 && currentX + wordWidth > maxWidth) {
            visualLines.add(EditorView.VisualLine(lineStart, i - 1))
            lineStart = i
            currentX = wordWidth
            i = safeWordEnd
        } else {
            currentX += wordWidth
            i = safeWordEnd
        }
    }
    
    if (i > lineStart) {
        visualLines.add(EditorView.VisualLine(lineStart, min(i - 1, textLen - 1)))
    } else if (i == lineStart && i <= textLen) {
        val isAfterNewline = i > 0 && i <= textLen && text[i-1] == '\n'
        if (i == 0 || isAfterNewline) {
            visualLines.add(EditorView.VisualLine(i, i))
        }
    }

    lastLayoutWidth = width.toFloat()
    lastLayoutVersion = version
    lastLayoutMarginsHash = marginsHash
}

internal fun EditorView.getIndexAt(x: Float, y: Float): Int {
    val currentBuffer = activeBuffer ?: return 0
    recalculateLayout()
    val sideMargin = currentBuffer.sideMargin
    val effectiveSideMargin = if (currentBuffer.showMargins) sideMargin + 2 * charWidth else sideMargin
    val yBase = currentBuffer.topBottomMargin + currentBuffer.verticalOffset - textPaint.fontMetrics.top
    
    val lineIdx = ((y - yBase + scrollYOffset) / lineHeight).toInt().coerceIn(0, visualLines.size - 1)
    if (lineIdx < 0 || lineIdx >= visualLines.size) return 0

    val line = visualLines[lineIdx]
    if (line.isFolded) return line.start

    val text = currentBuffer.getContent()
    var currentX = effectiveSideMargin
    for (i in line.start..line.end) {
        if (i >= text.length) break
        val charW = if (isMonospace) charWidth else textPaint.measureText(text[i].toString())
        if (x < currentX + charW / 2f) return i
        currentX += charW
    }
    return line.end + 1
}

internal fun EditorView.findWordEnd(text: CharSequence, start: Int): Int {
    var i = start
    while (i < text.length && !text[i].isWhitespace()) i++
    return i
}

internal fun EditorView.moveVisualLine(delta: Int, selecting: Boolean) {
    val currentBuffer = activeBuffer ?: return
    recalculateLayout()
    val currentIdx = currentBuffer.cursorPosition
    
    var low = 0
    var high = visualLines.size - 1
    var lineIdx = -1
    while (low <= high) {
        val mid = (low + high) / 2
        val line = visualLines[mid]
        if (currentIdx >= line.start && currentIdx <= line.end) {
            lineIdx = mid
            break
        } else if (currentIdx < line.start) {
            high = mid - 1
        } else {
            low = mid + 1
        }
    }
    
    if (lineIdx == -1 && visualLines.isNotEmpty() && currentIdx == visualLines.last().end + 1) {
        lineIdx = visualLines.size - 1
    }

    if (lineIdx == -1) return

    val nextLineIdx = (lineIdx + delta).coerceIn(0, visualLines.size - 1)
    if (nextLineIdx == lineIdx) return

    val currentCoords = getCursorCoordinates(currentIdx)
    val targetX = currentCoords.first
    val nextLine = visualLines[nextLineIdx]
    
    if (nextLine.isFolded) {
        currentBuffer.setCursor(nextLine.start, selecting)
    } else {
        val text = currentBuffer.getContent()
        val sideMargin = currentBuffer.sideMargin
        val effectiveSideMargin = if (currentBuffer.showMargins) sideMargin + 2 * charWidth else sideMargin
        var x = effectiveSideMargin
        var bestIdx = nextLine.start
        var minDiff = abs(targetX - x)

        for (i in nextLine.start..nextLine.end) {
            if (i >= text.length) break
            x += if (isMonospace) charWidth else textPaint.measureText(text[i].toString())
            val diff = abs(targetX - x)
            if (diff < minDiff) { minDiff = diff; bestIdx = i + 1 }
        }
        currentBuffer.setCursor(bestIdx, selecting)
    }
    invalidate(); onCursorMoved?.invoke(); scrollToCursor()
}

internal fun EditorView.moveVisualLineBoundary(toEnd: Boolean, selecting: Boolean) {
    val currentBuffer = activeBuffer ?: return
    recalculateLayout()
    val currentIdx = currentBuffer.cursorPosition
    var line = visualLines.find { currentIdx >= it.start && currentIdx <= it.end }
    
    if (line == null && visualLines.isNotEmpty() && currentIdx == visualLines.last().end + 1) {
        line = visualLines.last()
    }
    
    if (line == null) return
    
    val target = if (toEnd) {
        if (line == visualLines.last()) currentBuffer.getContent().length else line.end
    } else {
        line.start
    }
    
    currentBuffer.setCursor(target, selecting)
    invalidate(); onCursorMoved?.invoke(); scrollToCursor()
}

internal fun EditorView.getCursorCoordinates(targetPos: Int): Pair<Float, Float> {
    val currentBuffer = activeBuffer ?: return Pair(0f, 0f)
    recalculateLayout()
    
    val sideMargin = currentBuffer.sideMargin
    val topBottomMargin = currentBuffer.topBottomMargin
    val availableHeight = height - 2 * topBottomMargin
    val fullLines = floor(availableHeight / lineHeight).coerceAtLeast(1f).toInt()
    val extraPadding = (availableHeight - (fullLines * lineHeight)) / 2
    val effectiveTop = topBottomMargin + extraPadding
    val effectiveSideMargin = if (currentBuffer.showMargins) sideMargin + 2 * charWidth else sideMargin

    val xStart = effectiveSideMargin
    val yBase = effectiveTop + currentBuffer.verticalOffset - textPaint.fontMetrics.top
    
    var low = 0
    var high = visualLines.size - 1
    var lineIdx = -1
    while (low <= high) {
        val mid = (low + high) / 2
        val line = visualLines[mid]
        if (targetPos >= line.start && targetPos <= line.end) {
            lineIdx = mid
            break
        } else if (targetPos < line.start) {
            high = mid - 1
        } else {
            low = mid + 1
        }
    }

    if (lineIdx == -1) {
        if (visualLines.isNotEmpty() && targetPos == visualLines.last().end + 1) {
            val lastLine = visualLines.last()
            val y = yBase + ((visualLines.size - 1) * lineHeight) - scrollYOffset
            var x = xStart - scrollXOffset
            val text = currentBuffer.getContent()
            for (i in lastLine.start until targetPos) {
                if (i >= text.length) break
                x += if (isMonospace) charWidth else textPaint.measureText(text[i].toString())
            }
            return Pair(x, y)
        }
        return Pair(effectiveSideMargin - scrollXOffset, yBase - scrollYOffset)
    }

    val line = visualLines[lineIdx]
    val y = yBase + (lineIdx * lineHeight) - scrollYOffset
    if (line.isFolded) return Pair(effectiveSideMargin - scrollXOffset, y)

    val text = currentBuffer.getContent()
    var x = effectiveSideMargin - scrollXOffset
    for (i in line.start until targetPos) {
        if (i >= text.length) break
        x += if (isMonospace) charWidth else textPaint.measureText(text[i].toString())
    }
    
    return Pair(x, y)
}

internal fun EditorView.loadImage(uriStr: String) {
    lastImageUri = uriStr
    try {
        val uri = Uri.parse(uriStr)
        val inputStream = if (uri.scheme == null || uri.scheme == "file") {
            val path = uri.path ?: uriStr.removePrefix("file://")
            java.io.FileInputStream(java.io.File(path))
        } else {
            context.contentResolver.openInputStream(uri)
        }

        if (uriStr.lowercase().endsWith(".gif")) {
            val bytes = inputStream?.use { it.readBytes() }
            if (bytes != null) {
                imageMovie = android.graphics.Movie.decodeByteArray(bytes, 0, bytes.size)
                imageBitmap = null
            }
        } else {
            inputStream?.use {
                imageBitmap = BitmapFactory.decodeStream(it)
            }
            imageMovie = null
        }
        imageMovieStart = 0
    } catch (e: Exception) {
        android.util.Log.e("EditorView", "Error loading image background: $uriStr", e)
        imageBitmap = null
    }
}
