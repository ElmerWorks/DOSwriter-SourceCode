package com.example.doswriter.editor

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.doswriter.model.EditorBuffer
import kotlin.math.*

internal fun EditorView.getMdBitmap(path: String): Bitmap? {
    val sanitizedPath = path.replace("\\", "/")
    if (mdImageCache.containsKey(sanitizedPath)) return mdImageCache[sanitizedPath]
    
    var cleanPath = sanitizedPath
    if (cleanPath.startsWith("//")) cleanPath = cleanPath.substring(1)
    if (cleanPath.startsWith("file:///")) cleanPath = cleanPath.substring(7)
    else if (cleanPath.startsWith("file://")) cleanPath = cleanPath.substring(7)

    try {
        var uri = Uri.parse(sanitizedPath)
        if (uri.scheme == null) {
            uri = Uri.parse("file://$cleanPath")
        }

        val stream = context.contentResolver.openInputStream(uri)
        val bmp = BitmapFactory.decodeStream(stream)
        if (bmp != null) {
            mdImageCache[sanitizedPath] = bmp
            return bmp
        }
    } catch (e: Exception) {
        try {
            val bmp = BitmapFactory.decodeFile(cleanPath)
            if (bmp != null) {
                mdImageCache[sanitizedPath] = bmp
                return bmp
            }
        } catch (e2: Exception) {}
    }
    return null
}

internal fun EditorView.schedulePdfRender() {
    val uri = activeBuffer?.pdfUri ?: return
    val currentHandler = renderHandler ?: return
    
    currentHandler.removeCallbacksAndMessages(null)
    currentHandler.postDelayed({
        renderVisiblePages(uri)
    }, 100)
}

internal fun EditorView.renderVisiblePages(uri: String) {
    val renderer = pdfRenderer ?: return
    val b = activeBuffer ?: return
    if (b.pdfUri != uri) return

    val sideMargin = b.sideMargin
    val pdfScale = b.pdfScale
    val pageWidth = (width - 2 * sideMargin) * pdfScale
    if (pageWidth <= 0) return

    val currentPage = getCurrentPdfPage()
    val pagesToRender = listOf(currentPage, currentPage + 1, currentPage - 1)
        .filter { it in 0 until renderer.pageCount }

    pagesToRender.forEach { pageIdx ->
        val cached = pdfCache.get(pageIdx)
        if (cached == null || cached.width != pageWidth.toInt()) {
            try {
                val page = renderer.openPage(pageIdx)
                val scale = pageWidth / page.width
                val pageHeight = (page.height * scale).toInt().coerceAtLeast(1)
                val targetWidth = pageWidth.toInt().coerceAtLeast(1)
                
                val bitmap = Bitmap.createBitmap(targetWidth, pageHeight, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pdfCache.put(pageIdx, bitmap)
                page.close()
                postInvalidate()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

internal fun EditorView.loadPdf(uriStr: String) {
    try {
        pdfRenderer?.close()
        pdfFileDescriptor?.close()
        pdfCache.evictAll()
        pdfPageOffsets.clear()
        pdfPageBaseHeights.clear()
        
        val uri = Uri.parse(uriStr)
        pdfFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
        pdfFileDescriptor?.let {
            pdfRenderer = PdfRenderer(it)
            lastPdfUri = uriStr
            val renderer = pdfRenderer!!
            
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                pdfPageBaseHeights.add(page.height.toFloat() / page.width.toFloat())
                page.close()
            }
            recalculatePdfOffsets()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

internal fun EditorView.drawPdf(canvas: Canvas, top: Float, bottom: Float) {
    val renderer = pdfRenderer ?: return
    val b = activeBuffer ?: return
    val sideMargin = b.sideMargin
    val pdfScale = b.pdfScale
    val pageWidth = (width - 2 * sideMargin) * pdfScale
    if (pageWidth <= 0) return

    canvas.save()
    canvas.clipRect(sideMargin, top, width - sideMargin, bottom)
    val xPos = sideMargin + b.pdfPanX
    
    for (i in 0 until renderer.pageCount) {
        val pageOffset = if (i < pdfPageOffsets.size) pdfPageOffsets[i] else 0f
        val y = top + pageOffset - scrollYOffset
        
        val nextOffset = if (i + 1 < pdfPageOffsets.size) pdfPageOffsets[i+1] else totalPdfHeight
        val pageHeight = nextOffset - pageOffset - 20f * pdfScale

        if (y + pageHeight > top && y < bottom) {
            val cached = pdfCache.get(i)
            if (cached != null && cached.width == pageWidth.toInt()) {
                canvas.drawBitmap(cached, xPos, y, null)
            } else {
                val paint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 2f }
                canvas.drawRect(xPos, y, xPos + pageWidth, y + pageHeight, paint)
                canvas.drawText("Loading Page ${i + 1}...", xPos + 20f, y + pageHeight / 2f, textPaint)
                schedulePdfRender()
            }
        }
    }
    canvas.restore()
}

internal fun EditorView.drawMarkdown(canvas: Canvas, top: Float, bottom: Float) {
    val b = activeBuffer ?: return
    val text = b.getCleanText()
    val sideMargin = b.sideMargin
    val effectiveSideMargin = if (b.showMargins) sideMargin + 2 * charWidth else sideMargin
    val maxWidth = width - effectiveSideMargin
    canvas.save()
    canvas.clipRect(0f, top, width.toFloat(), bottom)
    var y = top + 40f - scrollYOffset
    val mdPaint = Paint(textPaint)
    
    var runningHeight = 40f
    val lines = text.split("\n")
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        mdPaint.textSize = textPaint.textSize; mdPaint.typeface = Typeface.MONOSPACE; mdPaint.isFakeBoldText = false
        var pl = line
        var isHorizontalRule = false
        var indent = 0f

        if (line.contains("|") && i + 1 < lines.size && lines[i+1].contains("|") && lines[i+1].contains("-")) {
            val tableRows = mutableListOf<List<String>>()
            var j = i
            while (j < lines.size && lines[j].contains("|")) {
                if (!lines[j].matches(Regex("^[\\s|\\-:]+$"))) {
                    val cells = lines[j].split("|")
                        .filterIndexed { idx, _ -> idx > 0 || lines[j].startsWith("|") }
                        .map { it.trim() }
                        .filter { it.isNotEmpty() || !lines[j].endsWith("|") }
                    if (cells.isNotEmpty()) tableRows.add(cells)
                }
                j++
            }
            
            if (tableRows.isNotEmpty()) {
                val tableHeight = drawTable(canvas, tableRows, effectiveSideMargin, y, maxWidth - effectiveSideMargin, mdPaint, top, bottom)
                y += tableHeight + 20f
                runningHeight += tableHeight + 20f
                i = j
                continue
            }
        }

        val imgMatch = Regex("!\\[(.*?)\\]\\((.*?)\\)").find(line)
        if (imgMatch != null) {
            val path = imgMatch.groupValues[2]
            val bmp = getMdBitmap(path)
            if (bmp != null) {
                val imgWidth = (maxWidth - effectiveSideMargin).coerceAtLeast(1f)
                val scale = imgWidth / bmp.width
                val imgHeight = bmp.height * scale
                if (y + imgHeight > top && y < bottom) {
                    mdImageRect.set(effectiveSideMargin, y, effectiveSideMargin + imgWidth, y + imgHeight)
                    canvas.drawBitmap(bmp, null, mdImageRect, null)
                }
                val h = imgHeight + 20f
                y += h
                runningHeight += h
            } else {
                mdPaint.color = Color.RED
                val errorMsg = "Error: Image not found ($path)"
                if (y + 40f > top && y < bottom) {
                    canvas.drawText(errorMsg, effectiveSideMargin, y + 30f, mdPaint)
                }
                mdPaint.color = textPaint.color
                val h = 60f
                y += h
                runningHeight += h
            }
            i++
            continue
        }
        
        if (line.trim() == "---" || line.trim() == "***" || line.trim() == "___") {
            isHorizontalRule = true
        } else if (line.startsWith("# ")) { 
            mdPaint.textSize = textPaint.textSize * 1.5f; mdPaint.isFakeBoldText = true; pl = line.substring(2) 
        } else if (line.startsWith("## ")) { 
            mdPaint.textSize = textPaint.textSize * 1.2f; mdPaint.isFakeBoldText = true; pl = line.substring(3) 
        } else if (line.startsWith("### ")) { 
            mdPaint.textSize = textPaint.textSize * 1.0f; mdPaint.isFakeBoldText = true; pl = line.substring(4)
        } else if (line.startsWith("- ")) { 
            pl = "• " + line.substring(2) 
            indent = 30f
        } else if (line.startsWith("> ")) {
            pl = "<i>" + line.substring(2) + "</i>"
            indent = 40f
        } else if (Regex("^[0-9]+\\.\\s+").find(line.trim()) != null) {
            pl = line.trim()
            indent = 30f
        }
        
        if (isHorizontalRule) {
            if (y > top && y < bottom) {
                val centerY = y + 10f
                canvas.drawLine(effectiveSideMargin, centerY, width - effectiveSideMargin, centerY, mdPaint)
            }
            val h = 30f
            y += h
            runningHeight += h
            i++
            continue
        }

        val htmlLine = pl
            .replace(Regex("\\*\\*\\*(.*?)\\*\\*\\*"), "<b><i>$1</i></b>")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
            .replace(Regex("\\*(.*?)\\*"), "<i>$1</i>")
            .replace(Regex("___(.*?)___"), "<b><i>$1</i></b>")
            .replace(Regex("__(.*?)__"), "<b>$1</b>")
            .replace(Regex("_(.*?)_"), "<i>$1</i>")
            .replace(Regex("~~(.*?)~~"), "<strike>$1</strike>")
            .replace(Regex("`(.*?)`"), "<tt>$1</tt>")

        val spanned = if (Build.VERSION.SDK_INT >= 24) {
            Html.fromHtml(htmlLine, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(htmlLine)
        }

        val blockWidth = (maxWidth - effectiveSideMargin - indent).toInt().coerceAtLeast(1)
        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(spanned, 0, spanned.length, TextPaint(mdPaint), blockWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(10f, 1.1f)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(spanned, TextPaint(mdPaint), blockWidth, Layout.Alignment.ALIGN_NORMAL, 1.1f, 10f, false)
        }

        if (y + layout.height > top && y < bottom) {
            canvas.save()
            canvas.translate(effectiveSideMargin + indent, y)
            layout.draw(canvas)
            canvas.restore()
        }
        
        val h = layout.height.toFloat() + 10f
        y += h
        runningHeight += h
        i++
    }
    totalMdHeight = runningHeight
    canvas.restore()
}

internal fun EditorView.drawTable(canvas: Canvas, rows: List<List<String>>, xStart: Float, yStart: Float, maxWidth: Float, paint: Paint, top: Float, bottom: Float): Float {
    if (rows.isEmpty()) return 0f
    val colCount = rows.maxOf { it.size }
    if (colCount == 0) return 0f
    val colWidth = maxWidth / colCount
    val cellPadding = 10f
    var currentY = yStart
    val linePaint = Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.GRAY }

    rows.forEachIndexed { rowIndex, cells ->
        var maxHeight = 0f
        val layouts = mutableListOf<StaticLayout>()
        cells.forEach { cell ->
            val htmlCell = cell
                .replace(Regex("\\*\\*\\*(.*?)\\*\\*\\*"), "<b><i>$1</i></b>")
                .replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
                .replace(Regex("\\*(.*?)\\*"), "<i>$1</i>")
                .replace(Regex("___(.*?)___"), "<b><i>$1</i></b>")
                .replace(Regex("__(.*?)__"), "<b>$1</b>")
                .replace(Regex("_(.*?)_"), "<i>$1</i>")
                .replace(Regex("~~(.*?)~~"), "<strike>$1</strike>")
                .replace(Regex("`(.*?)`"), "<tt>$1</tt>")

            val spanned = if (Build.VERSION.SDK_INT >= 24) {
                Html.fromHtml(htmlCell, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(htmlCell)
            }
            val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(spanned, 0, spanned.length, TextPaint(paint), (colWidth - 2 * cellPadding).toInt().coerceAtLeast(1)).build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(spanned, TextPaint(paint), (colWidth - 2 * cellPadding).toInt().coerceAtLeast(1), Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, false)
            }
            layouts.add(layout)
            maxHeight = max(maxHeight, layout.height.toFloat())
        }
        
        val rowHeight = maxHeight + 2 * cellPadding
        if (currentY + rowHeight > top && currentY < bottom) {
            if (rowIndex == 0) {
                val oldColor = paint.color
                canvas.drawRect(xStart, currentY, xStart + maxWidth, currentY + rowHeight, paint.apply { alpha = 30 })
                paint.alpha = 255
                paint.color = oldColor
            }
            layouts.forEachIndexed { colIndex, layout ->
                val cx = xStart + colIndex * colWidth
                canvas.save()
                canvas.translate(cx + cellPadding, currentY + cellPadding)
                layout.draw(canvas)
                canvas.restore()
                canvas.drawRect(cx, currentY, cx + colWidth, currentY + rowHeight, linePaint)
            }
        }
        currentY += rowHeight
    }
    return currentY - yStart
}

internal fun EditorView.wrapTextForMd(text: String, paint: Paint, maxWidth: Float): List<String> {
    val result = mutableListOf<String>()
    val words = text.split(" ")
    var currentLine = ""
    for (word in words) {
        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (paint.measureText(testLine) <= maxWidth) currentLine = testLine
        else { if (currentLine.isNotEmpty()) result.add(currentLine); currentLine = word }
    }
    result.add(currentLine)
    return result
}

internal fun EditorView.recalculatePdfOffsets() {
    val b = activeBuffer ?: return
    if (pdfPageBaseHeights.isEmpty()) return
    
    pdfPageOffsets.clear()
    totalPdfHeight = 0f
    val sideMargin = b.sideMargin
    val pdfScale = b.pdfScale
    val pageWidth = (width - 2 * sideMargin) * pdfScale
    if (pageWidth <= 0) return

    pdfPageBaseHeights.forEach { aspectRatio ->
        val pageHeight = pageWidth * aspectRatio
        pdfPageOffsets.add(totalPdfHeight)
        totalPdfHeight += pageHeight + 20f * pdfScale
    }
}

internal fun EditorView.drawCursor(canvas: Canvas, x: Float, y: Float) {
    if (!isCursorVisible) return
    val b = activeBuffer ?: return
    val settings = b.cursorSettings
    val isBlinkingActive = settings.blinkAlways || (settings.blinkOnShortcut && (isFindingCursor || isLocatorBlinking)) || (isDemoRunning && settings.typewriter.isTerminalMode)
    if (isBlinkingActive && !blinkOn) return

    val paint = cursorPaint
    val top = y + textPaint.fontMetrics.top
    val bottom = y + textPaint.fontMetrics.bottom

    val hideForDemo = isDemoRunning && !settings.typewriter.isTerminalMode

    when (settings.style) {
        EditorBuffer.CursorStyle.THIN_CARET -> if (!hideForDemo) canvas.drawRect(x, top, x + 4f, bottom, paint)
        EditorBuffer.CursorStyle.THICK_CARET -> if (!hideForDemo) canvas.drawRect(x, top, x + 10f, bottom, paint)
        EditorBuffer.CursorStyle.BLOCK -> if (!hideForDemo) canvas.drawRect(x, top, x + charWidth, bottom, paint)
        EditorBuffer.CursorStyle.UNDERLINE -> if (!hideForDemo) canvas.drawRect(x, bottom - 6f, x + charWidth, bottom, paint)
        EditorBuffer.CursorStyle.INVERTED -> {
            if (!hideForDemo) canvas.drawRect(x, top, x + charWidth, bottom, paint)
            val text = b.getContent()
            if (b.cursorPosition < text.length) {
                val char = text[b.cursorPosition]
                if (char != '\n') {
                    val oldColor = textPaint.color
                    textPaint.color = backgroundPaint.color
                    canvas.drawText(char.toString(), x, y, textPaint)
                    textPaint.color = oldColor
                }
            }
        }
        EditorBuffer.CursorStyle.RETRO_BLOCK -> {
            if (!hideForDemo) {
                val oldAlpha = paint.alpha
                paint.alpha = 100
                canvas.drawRect(x - 2f, top - 2f, x + charWidth + 2f, bottom + 2f, paint)
                paint.alpha = 255
                canvas.drawRect(x, top, x + charWidth, bottom, paint)
                paint.alpha = oldAlpha
            }
        }
        EditorBuffer.CursorStyle.DIM_CARET -> {
            if (!hideForDemo) {
                val oldAlpha = paint.alpha
                paint.alpha = 100
                canvas.drawRect(x, top, x + charWidth, bottom, paint)
                paint.alpha = oldAlpha
            }
        }
    }
}

internal fun EditorView.drawViewerImage(canvas: Canvas) {
    val uriStr = viewerImageUri ?: return
    val b = activeBuffer ?: return
    
    if (lastViewerUri != uriStr) {
        lastViewerUri = uriStr
        try {
            if (!isViewerRemote) {
                val uri = Uri.parse(uriStr)
                val inputStream = if (uri.scheme == "file") {
                    val file = java.io.File(uri.path ?: uriStr.removePrefix("file://"))
                    if (file.exists() && file.canRead()) {
                        java.io.FileInputStream(file)
                    } else {
                        context.contentResolver.openInputStream(uri)
                    }
                } else if (uri.scheme == null) {
                    java.io.FileInputStream(java.io.File(uriStr))
                } else {
                    context.contentResolver.openInputStream(uri)
                }

                if (uriStr.lowercase().endsWith(".gif")) {
                    val bytes = inputStream?.use { it.readBytes() }
                    if (bytes != null) {
                        viewerMovie = android.graphics.Movie.decodeByteArray(bytes, 0, bytes.size)
                        viewerBitmap = null
                    }
                } else {
                    inputStream?.use {
                        viewerBitmap = BitmapFactory.decodeStream(it)
                    }
                    viewerMovie = null
                }
            }
            movieStart = 0
        } catch (e: Exception) {
            android.util.Log.e("EditorView", "Error loading viewer image: $uriStr", e)
            viewerBitmap = null
            viewerMovie = null
        }
    }

    val sideMargin = b.sideMargin
    val topBottomMargin = b.topBottomMargin
    val availableWidth = width.toFloat() - 2 * sideMargin
    val availableHeight = height.toFloat() - 2 * topBottomMargin

    viewerMovie?.let { movie ->
        val now = android.os.SystemClock.uptimeMillis()
        if (movieStart == 0L) movieStart = now
        val duration = if (movie.duration() == 0) 1000 else movie.duration()
        val relTime = ((now - movieStart) % duration).toInt()
        movie.setTime(relTime)

        val scaleX = availableWidth / movie.width()
        val scaleY = availableHeight / movie.height()
        val fitScale = min(scaleX, scaleY)
        
        imageMatrix.reset()
        imageMatrix.postScale(fitScale * viewerScale, fitScale * viewerScale)
        val dx = sideMargin + (availableWidth - movie.width() * fitScale * viewerScale) / 2f + viewerPanX
        val dy = topBottomMargin + (availableHeight - movie.height() * fitScale * viewerScale) / 2f + viewerPanY
        imageMatrix.postTranslate(dx, dy)

        canvas.save()
        canvas.clipRect(sideMargin, topBottomMargin, width - sideMargin, height - topBottomMargin)
        canvas.concat(imageMatrix)
        movie.draw(canvas, 0f, 0f)
        canvas.restore()
        invalidate()
    } ?: viewerBitmap?.let { bmp ->
        val scaleX = availableWidth / bmp.width
        val scaleY = availableHeight / bmp.height
        val fitScale = min(scaleX, scaleY)
        imageMatrix.reset()
        imageMatrix.postScale(fitScale * viewerScale, fitScale * viewerScale)
        val dx = sideMargin + (availableWidth - bmp.width * fitScale * viewerScale) / 2f + viewerPanX
        val dy = topBottomMargin + (availableHeight - bmp.height * fitScale * viewerScale) / 2f + viewerPanY
        imageMatrix.postTranslate(dx, dy)
        canvas.save()
        canvas.clipRect(sideMargin, topBottomMargin, width - sideMargin, height - topBottomMargin)
        canvas.drawBitmap(bmp, imageMatrix, null)
        canvas.restore()
    }
}

internal fun EditorView.renderDraw(canvas: Canvas) {
    canvas.drawPaint(backgroundPaint)

    if (viewerImageUri != null) {
        drawViewerImage(canvas)
        return
    }

    val currentBuffer = activeBuffer ?: return
    recalculateLayout()
    
    val sideMargin = currentBuffer.sideMargin
    val topBottomMargin = currentBuffer.topBottomMargin
    val availableHeight = height - 2 * topBottomMargin
    val fullLines = floor(availableHeight / lineHeight).coerceAtLeast(1f).toInt()
    val perfectHeight = fullLines * lineHeight
    val extraPadding = (availableHeight - perfectHeight) / 2
    val effectiveTop = topBottomMargin + extraPadding
    val effectiveBottom = effectiveTop + perfectHeight

    if (lastImageUri != currentBuffer.imageUri) {
        if (currentBuffer.imageUri == null) {
            if (imageBitmap != null) {
                fadeOutBitmap = imageBitmap
                fadeOutAlpha = imageAlpha
                fadeOutMatrix.set(imageMatrix)
                imageBitmap = null
                startImageFade(false)
            } else {
                imageAlpha = 0f
            }
            lastImageUri = null
        } else {
            if (imageBitmap != null) {
                fadeOutBitmap = imageBitmap
                fadeOutAlpha = imageAlpha
                fadeOutMatrix.set(imageMatrix)
            }
            if (imageBitmap == null && imageAlpha < 0.1f) {
                imageAlpha = 0f
            }
            imageBitmap = null
            loadImage(currentBuffer.imageUri!!)
            if (imageAlpha < 1.0f) {
                startImageFade(true)
            }
        }
    }

    if (fadeOutBitmap != null) {
        val progress = if (currentBuffer.imageUri == null) imageAlpha else 1.0f - imageAlpha
        if (progress > 0f) {
            canvas.save()
            canvas.clipRect(sideMargin, effectiveTop, width - sideMargin, effectiveBottom)
            imagePaint.alpha = (fadeOutAlpha * progress * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(fadeOutBitmap!!, fadeOutMatrix, imagePaint)
            canvas.restore()
        } else {
            fadeOutBitmap = null
        }
    }

    if (currentBuffer.imageUri != null) {
        imageMovie?.let { movie ->
            val now = android.os.SystemClock.uptimeMillis()
            if (imageMovieStart == 0L) imageMovieStart = now
            val duration = if (movie.duration() == 0) 1000 else movie.duration()
            val relTime = ((now - imageMovieStart) % duration).toInt()
            movie.setTime(relTime)

            val availableWidth = width.toFloat() - 2 * sideMargin
            val availableHeightImg = effectiveBottom - effectiveTop
            val scaleX = availableWidth / movie.width()
            val scaleY = availableHeightImg / movie.height()
            val fitScale = min(scaleX, scaleY)
            
            imageMatrix.reset()
            imageMatrix.postScale(fitScale * currentBuffer.imageScale, fitScale * currentBuffer.imageScale)
            val dx = sideMargin + (availableWidth - movie.width() * fitScale * currentBuffer.imageScale) / 2f + currentBuffer.imagePanX
            val dy = effectiveTop + (availableHeightImg - movie.height() * fitScale * currentBuffer.imageScale) / 2f + currentBuffer.imagePanY
            imageMatrix.postTranslate(dx, dy)

            canvas.save()
            canvas.clipRect(sideMargin, effectiveTop, width - sideMargin, effectiveBottom)
            if (imageAlpha < 1f) canvas.saveLayer(null, imagePaint.apply { alpha = (imageAlpha * 255).toInt() })
            canvas.concat(imageMatrix)
            movie.draw(canvas, 0f, 0f)
            if (imageAlpha < 1f) canvas.restore()
            canvas.restore()
            invalidate()
        } ?: imageBitmap?.let { bmp ->
            canvas.save()
            canvas.clipRect(sideMargin, effectiveTop, width - sideMargin, effectiveBottom)
            imageMatrix.reset()
            val scaleX = (width - 2 * sideMargin) / bmp.width
            val scaleY = (effectiveBottom - effectiveTop) / bmp.height
            val fitScale = min(scaleX, scaleY)
            imageMatrix.postScale(fitScale * currentBuffer.imageScale, fitScale * currentBuffer.imageScale)
            val dx = sideMargin + (width - 2 * sideMargin - bmp.width * fitScale * currentBuffer.imageScale) / 2f + currentBuffer.imagePanX
            val dy = effectiveTop + (effectiveBottom - effectiveTop - bmp.height * fitScale * currentBuffer.imageScale) / 2f + currentBuffer.imagePanY
            imageMatrix.postTranslate(dx, dy)
            
            imagePaint.alpha = (imageAlpha * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(bmp, imageMatrix, imagePaint)
            canvas.restore()
        }
    } else if (currentBuffer.pdfUri != null) {
        if (lastPdfUri != currentBuffer.pdfUri) loadPdf(currentBuffer.pdfUri!!)
        drawPdf(canvas, effectiveTop, effectiveBottom)
        if (isPdfSelecting) {
            pdfSelectionRect.set(min(pdfSelectionStart.x, pdfSelectionEnd.x), min(pdfSelectionStart.y, pdfSelectionEnd.y), max(pdfSelectionStart.x, pdfSelectionEnd.x), max(pdfSelectionStart.y, pdfSelectionEnd.y))
            canvas.drawRect(pdfSelectionRect, selectionPaint)
        }
    } else if (currentBuffer.isMarkdownMode || forceMarkdownRender) {
        drawMarkdown(canvas, effectiveTop, effectiveBottom)
    } else {
        val text = currentBuffer.getContent()
        val cursor = currentBuffer.cursorPosition
        val selectionRange = currentBuffer.getSelectionRange()
        val verticalOffset = currentBuffer.verticalOffset
        val bookmarks = currentBuffer.bookmarks
        val effectiveSideMargin = if (currentBuffer.showMargins) sideMargin + 2 * charWidth else sideMargin

        canvas.save()
        canvas.clipRect(sideMargin, effectiveTop, width - sideMargin, effectiveBottom)

        val xStart = effectiveSideMargin - scrollXOffset
        val yBase = effectiveTop + verticalOffset - textPaint.fontMetrics.top
        
        if (!isWatermarkGlobalHidden && !suppressWatermark && currentBuffer.getCleanText().isEmpty()) {
            val oldColor = textPaint.color
            textPaint.color = Color.GRAY
            textPaint.alpha = 100
            canvas.drawText("DOSwriter    Menus : ALT-F    Help : ALT-H", sideMargin - scrollXOffset, yBase - scrollYOffset + (currentBuffer.prefix.count { it == '\n' } * lineHeight), textPaint)
            textPaint.color = oldColor
            textPaint.alpha = 255
        }

        val firstVisibleLine = (scrollYOffset / lineHeight).toInt().coerceIn(0, visualLines.size - 1)
        val lastVisibleLine = ((scrollYOffset + availableHeight) / lineHeight).toInt().coerceIn(0, visualLines.size - 1)

        for (lineIdx in firstVisibleLine..lastVisibleLine) {
            if (lineIdx >= visualLines.size) break
            val line = visualLines[lineIdx]
            val y = yBase + (lineIdx * lineHeight) - scrollYOffset
            
            if (bookmarks.any { it >= line.start && it <= line.end }) {
                val symbol = "[BK]"
                val boxPadding = 4f
                val oldFoldSize = foldPaint.textSize
                foldPaint.textSize = textPaint.textSize * 0.7f
                val symbolWidth = foldPaint.measureText(symbol)
                
                val bgColor = backgroundPaint.color
                val lum = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor))
                val boxColor = if (lum > 128) Color.BLACK else Color.WHITE
                val textColor = if (lum > 128) Color.WHITE else Color.BLACK

                val iconX = xStart
                
                val fm = foldPaint.fontMetrics
                val boxTop = y + fm.ascent - boxPadding
                val boxBottom = y + fm.descent + boxPadding
                val rect = RectF(iconX - boxPadding, boxTop, iconX + symbolWidth + boxPadding, boxBottom)
                
                val boxPaint = Paint().apply { color = boxColor; style = Paint.Style.FILL; isAntiAlias = true }
                canvas.drawRoundRect(rect, 8f, 8f, boxPaint)
                
                val oldFoldColor = foldPaint.color
                val oldFoldTypeface = foldPaint.typeface
                foldPaint.color = textColor
                foldPaint.typeface = Typeface.MONOSPACE
                canvas.drawText(symbol, iconX, y, foldPaint)
                
                val content = currentBuffer.getContent()
                val previewBuilder = StringBuilder()
                var previewPtr = line.start
                while (previewPtr <= line.end && previewPtr < content.length) {
                    val c = content[previewPtr]
                    if (c == '\n') break
                    previewBuilder.append(c)
                    if (previewBuilder.length > 200) break
                    previewPtr++
                }
                val previewText = previewBuilder.toString().trim()
                if (previewText.isNotEmpty()) {
                    val previewX = iconX + symbolWidth + boxPadding * 8
                    val availW = (width - sideMargin) - previewX
                    if (availW > 20f) {
                        val truncated = android.text.TextUtils.ellipsize(previewText, textPaint, availW, android.text.TextUtils.TruncateAt.END).toString()
                        val oldAlpha = textPaint.alpha
                        textPaint.alpha = 200 
                        canvas.drawText(truncated, previewX, y, textPaint)
                        textPaint.alpha = oldAlpha
                    }
                }

                foldPaint.color = oldFoldColor
                foldPaint.typeface = oldFoldTypeface
                foldPaint.textSize = oldFoldSize
                
                // Skip drawing the original text line so it doesn't overlap
                continue
            }

            if (line.isFolded) {
                if (cursor >= line.start && cursor <= line.end) drawCursor(canvas, xStart, y)
                
                val fm = foldPaint.fontMetrics
                val symbol = "[+]"
                val symbolWidth = foldPaint.measureText(symbol)
                val boxPadding = 4f
                
                // High contrast background box
                val bgColor = backgroundPaint.color
                val lum = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor))
                
                val boxColor = if (lum > 128) Color.BLACK else Color.WHITE
                val textColor = if (lum > 128) Color.WHITE else Color.BLACK
                
                val oldColor = foldPaint.color
                val oldTypeface = foldPaint.typeface
                
                // Draw the box
                val boxTop = y + fm.ascent - boxPadding
                val boxBottom = y + fm.descent + boxPadding
                val rect = RectF(xStart - boxPadding, boxTop, xStart + symbolWidth + boxPadding, boxBottom)
                
                val boxPaint = Paint().apply { color = boxColor; style = Paint.Style.FILL; isAntiAlias = true }
                canvas.drawRoundRect(rect, 8f, 8f, boxPaint)
                
                // Draw the symbol
                foldPaint.color = textColor
                foldPaint.typeface = Typeface.MONOSPACE
                canvas.drawText(symbol, xStart, y, foldPaint)
                
                foldPaint.color = oldColor
                foldPaint.typeface = oldTypeface

                // Draw preview of collapsed text
                val content = currentBuffer.getContent()
                val previewBuilder = StringBuilder()
                var previewPtr = line.start
                while (previewPtr <= line.end && previewPtr < content.length) {
                    val c = content[previewPtr]
                    if (c == '\n') break
                    previewBuilder.append(c)
                    if (previewBuilder.length > 200) break
                    previewPtr++
                }
                val previewText = previewBuilder.toString().trim()
                if (previewText.isNotEmpty()) {
                    val previewX = xStart + symbolWidth + boxPadding * 4
                    val availW = (width - sideMargin) - previewX
                    if (availW > 20f) {
                        val truncated = android.text.TextUtils.ellipsize(previewText, textPaint, availW, android.text.TextUtils.TruncateAt.END).toString()
                        val oldAlpha = textPaint.alpha
                        textPaint.alpha = 160
                        canvas.drawText(truncated, previewX, y, textPaint)
                        textPaint.alpha = oldAlpha
                    }
                }
                continue
            }

            var x = xStart
            for (i in line.start..line.end) {
                if (i == cursor) {
                    drawCursor(canvas, x, y)
                    if (isFindingCursor && blinkOn) canvas.drawRect(x, y + textPaint.fontMetrics.top, x + charWidth, y + textPaint.fontMetrics.bottom, textPaint)
                }
                if (i == text.length) break
                val char = text[i]
                if (i in selectionRange) {
                    val charW = if (isMonospace) charWidth else textPaint.measureText(char.toString())
                    canvas.drawRect(x, y + textPaint.fontMetrics.top, x + charW, y + textPaint.fontMetrics.bottom, selectionPaint)
                }
                if (char == '\n') {
                    if (currentBuffer.lineEndingSymbol != 0) {
                        val symbol = when (currentBuffer.lineEndingSymbol) {
                            1 -> "\u21B5"; 2 -> "\u00B6"; 3 -> "\u23CE"; else -> ""
                        }
                        canvas.drawText(symbol, x, y, textPaint)
                    }
                } else {
                    canvas.drawText(char.toString(), x, y, textPaint)
                    x += if (isMonospace) charWidth else textPaint.measureText(char.toString())
                }
            }
            if (cursor == text.length && lineIdx == visualLines.size - 1 && line.end != text.length) drawCursor(canvas, x, y)
        }
        canvas.restore()

        if (isTypewriterActive()) {
            val settings = currentBuffer.cursorSettings.typewriter
            
            // 1. Calculate the Y target based on anchor (Center or Bottom)
            if (typewriterTargetY == 0f) {
                typewriterTargetY = when(settings.anchor) {
                    EditorBuffer.TypewriterAnchor.CENTER -> height / 2f
                    EditorBuffer.TypewriterAnchor.BOTTOM -> effectiveBottom - lineHeight
                }
            }

            val coords = getCursorCoordinates(currentBuffer.cursorPosition)
            val currentScreenY = coords.second

            // 2. Perform Vertical Scroll
            if (settings.isTerminalMode) {
                // TERMINAL MODE: Text fills from top. Scroll only once we hit the target Y.
                // We only scroll DOWN (increase scrollYOffset).
                if (currentScreenY > typewriterTargetY) {
                    val diffY = currentScreenY - typewriterTargetY
                    scrollYOffset += diffY
                    postInvalidate()
                }
            } else {
                // FIXED MODE: Cursor is locked to target Y at all times.
                // Scroll in any direction to match target.
                val diffY = currentScreenY - typewriterTargetY
                if (diffY != 0f) {
                    scrollYOffset += diffY
                    postInvalidate()
                }
            }

            // 3. Perform Horizontal Scroll (Fixed only)
            if (settings.isFixed && !settings.isTerminalMode) {
                val currentScreenX = coords.first
                val targetX = width / 2f
                val diffX = currentScreenX - targetX
                if (diffX != 0f) {
                    scrollXOffset += diffX
                    postInvalidate()
                }
            } else {
                scrollXOffset = 0f
            }
            
            // 4. Draw Guide Graphics
            if (settings.showGuide && settings.isFixed && !settings.isTerminalMode) {
                val guideX = width / 2f
                val guideY = typewriterTargetY
                val guidePaint = Paint().apply { color = textPaint.color; strokeWidth = 2f; style = Paint.Style.STROKE }
                val size = 20f
                canvas.drawLine(guideX - size, guideY - size, guideX - size, guideY + size, guidePaint)
                canvas.drawLine(guideX + size, guideY - size, guideX + size, guideY + size, guidePaint)
                canvas.drawLine(guideX - size, guideY - size, guideX - (size/2), guideY - size, guidePaint)
                canvas.drawLine(guideX - size, guideY + size, guideX - (size/2), guideY + size, guidePaint)
                canvas.drawLine(guideX + size, guideY - size, guideX + (size/2), guideY - size, guidePaint)
                canvas.drawLine(guideX + size, guideY + size, guideX + (size/2), guideY + size, guidePaint)
            }

            val originalColor = textPaint.color
            textPaint.color = Color.GRAY
            val oldSize = textPaint.textSize
            textPaint.textSize = 30f
            val statusText = if (settings.isTerminalMode) "TERM" else if (settings.isFixed) "T[F]" else "T"
            canvas.drawText(statusText, 20f, height - 20f, textPaint)
            textPaint.textSize = oldSize
            textPaint.color = originalColor
        } else if (shouldScrollToCursorTop) {
            val coords = getCursorCoordinates(currentBuffer.cursorPosition)
            val cursorY = coords.second
            val yBaseCoord = effectiveTop + currentBuffer.verticalOffset - textPaint.fontMetrics.top
            scrollYOffset += (cursorY - yBaseCoord)
            shouldScrollToCursorTop = false
            postInvalidate()
        } else {
            if (scrollXOffset != 0f) { scrollXOffset = 0f; postInvalidate() }
            if (shouldScrollToCursor) {
                val coords = getCursorCoordinates(currentBuffer.cursorPosition); val cursorY = coords.second
                if (currentBuffer.cursorPosition <= currentBuffer.prefix.length) { scrollYOffset = 0f; postInvalidate() }
                else if (cursorY < effectiveTop) { scrollYOffset -= (effectiveTop - cursorY); postInvalidate() }
                else if (cursorY > effectiveBottom - lineHeight) { scrollYOffset += (cursorY - (effectiveBottom - lineHeight)); postInvalidate() }
                shouldScrollToCursor = false
            }
        }
    }

    if (currentBuffer.showMargins) {
        val bgColor = backgroundPaint.color
        val luminance = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor))
        marginPaint.color = if (luminance > 128) Color.argb(40, 0, 0, 0) else Color.argb(40, 255, 255, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), effectiveTop, marginPaint)
        canvas.drawRect(0f, effectiveBottom, width.toFloat(), height.toFloat(), marginPaint)
        canvas.drawRect(0f, effectiveTop, sideMargin, effectiveBottom, marginPaint)
        canvas.drawRect(width - sideMargin, effectiveTop, width.toFloat(), effectiveBottom, marginPaint)
    }
}
