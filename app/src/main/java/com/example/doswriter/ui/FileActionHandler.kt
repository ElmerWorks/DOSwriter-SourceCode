package com.example.doswriter.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.provider.OpenableColumns
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.example.doswriter.MainActivity
import com.example.doswriter.logic.*
import com.example.doswriter.editor.*
import java.io.File
import java.util.*
import kotlin.math.max
import kotlin.math.min

internal fun MainActivity.loadViewerImage() {
    if (viewerIndex < 0 || viewerIndex >= viewerFiles.size) return
    val path = viewerFiles[viewerIndex]
    
    val targetViews = mutableListOf<EditorView>()
    if (isSplitActive) {
        if (leftEditorView.isViewerActive()) targetViews.add(leftEditorView)
        if (rightEditorView.isViewerActive()) targetViews.add(rightEditorView)
        
        if (targetViews.isEmpty()) {
            targetViews.add(editorView)
        }
    } else {
        targetViews.add(editorView)
    }

    if (isViewerRemote) {
        syncManager.fetchRemoteImage(path) { bmp ->
            if (bmp != null) {
                targetViews.forEach { it.setViewerBitmap(bmp, path) }
            } else {
                Toast.makeText(this, "Failed to load remote image", Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        targetViews.forEach { it.setViewerImage(path, false) }
    }
}

internal fun MainActivity.moveViewer(delta: Int) {
    if (viewerFiles.isEmpty()) return
    viewerIndex = (viewerIndex + delta + viewerFiles.size) % viewerFiles.size
    loadViewerImage()
}

internal fun MainActivity.toggleSlideshow() {
    slideshowActive = !slideshowActive
    if (slideshowActive) {
        slideshowHandler.postDelayed(slideshowRunnable, imageFolderManager.slideshowIntervalSec * 1000L)
        showStatusToast("Slideshow: ON")
    } else {
        slideshowHandler.removeCallbacks(slideshowRunnable)
        showStatusToast("Slideshow: OFF")
    }
}

internal fun MainActivity.handleSyncEvent(event: SyncManager.Event) {
    when (event) {
        is SyncManager.Event.Connected -> { showStatusToast("Synced with Host"); applyTheme() }
        is SyncManager.Event.Disconnected -> { showStatusToast("Disconnected from Host"); applyTheme() }
        is SyncManager.Event.Error -> Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
        is SyncManager.Event.FileReceived -> {
            val ext = event.path.substringAfterLast(".", "").lowercase()
            val textExts = listOf("txt", "md", "html", "css", "json", "xml", "epol", "csv", "log")
            if (ext !in textExts && ext.isNotEmpty() && event.path.contains(".")) {
                Toast.makeText(this, "Remote Sync: Ignored non-text file ${event.path}", Toast.LENGTH_LONG).show()
                return
            }

            val buffer = bufferManager.getCurrentBuffer()
            buffer.setText(event.content)
            buffer.syncFilePath = event.path
            buffer.fileName = "[REMOTE] ${event.path.substringAfterLast("/")}"
            recentFileManager.addFile(event.path, event.path.substringAfterLast("/"), true)
            buffer.markClean()
            bufferManager.saveBuffers()
            editorView.setBuffer(buffer) 
            updateStatus()
            showStatusToast("Loaded ${buffer.fileName}")
        }
        is SyncManager.Event.SaveOk -> {
            showStatusToast("Sync Save OK: ${event.path.substringAfterLast("/")}")
        }
        is SyncManager.Event.RemoteFileModified -> {
            val buffer = bufferManager.getCurrentBuffer()
            if (buffer.syncFilePath == event.path) {
                if (buffer.isDirty) {
                    Toast.makeText(this, "REMOTE CHANGE DETECTED (Local modified - not reloaded)", Toast.LENGTH_LONG).show()
                } else {
                    syncManager.openRemoteFile(event.path)
                }
            }
        }
    }
}

internal fun MainActivity.triggerAutoSyncPush() {
    if (syncManager.isConnected() && bufferManager.isAutoSyncEnabled) {
        val buffer = bufferManager.getCurrentBuffer()
        val path = buffer.syncFilePath
        if (path != null && buffer.isDirty) {
            syncManager.saveRemoteFile(path, buffer.getCleanText())
            buffer.markClean()
            updateStatus()
            Log.d("Sync", "Auto-pushed $path")
        }
    }
    autoSyncHandler.postDelayed(autoSyncRunnable, bufferManager.autoSyncIntervalMins * 60000L)
}

internal fun MainActivity.resetAutoSyncTimer() {
    autoSyncHandler.removeCallbacks(autoSyncRunnable)
    autoSyncHandler.postDelayed(autoSyncRunnable, bufferManager.autoSyncIntervalMins * 60000L)
}

internal fun MainActivity.handleOutlineFileSelected(path: String, name: String, isRemote: Boolean, shiftPressed: Boolean) {
    if (shiftPressed) {
        val options = (0 until 8).map { "Buffer ${it + 1}: ${bufferManager.getBufferAt(it).fileName ?: "Empty"}" }
        selectionOverlay.show("Load Into Buffer:", options, 0, { index ->
            if (bufferManager.switchBuffer(index)) {
                editorView.setBuffer(bufferManager.getCurrentBuffer())
                if (isRemote) {
                    if (syncManager.isConnected()) syncManager.openRemoteFile(path)
                    else Toast.makeText(this, "Sync not connected", Toast.LENGTH_SHORT).show()
                } else {
                    handleDocumentOpened(Uri.parse(path))
                }
            }
        }, { })
        return
    }

    if (isRemote) {
        if (syncManager.isConnected()) {
            syncManager.openRemoteFile(path)
        } else {
            Toast.makeText(this, "Sync not connected", Toast.LENGTH_SHORT).show()
        }
    } else {
        handleDocumentOpened(Uri.parse(path))
    }
}

internal fun MainActivity.handleOutlineSetupAction(item: com.example.doswriter.logic.OutlineManager.OutlineItem?, action: OutlineOverlay.Action) {
    when (action) {
        OutlineOverlay.Action.ADD_FOLDER -> {
            inputOverlay.show("Folder Name:", "", { name ->
                outlineManager.addFolder(name)
                outlineOverlay.refresh()
            }, { })
        }
        OutlineOverlay.Action.ADD_CONTAINER -> {
            if (item is com.example.doswriter.logic.OutlineManager.OutlineItem.Folder) {
                val newContainer = outlineManager.addContainer(item, "")
                outlineOverlay.refresh(newContainer.id)
            }
        }
        OutlineOverlay.Action.DELETE -> {
            if (item != null) {
                val label = if (item is com.example.doswriter.logic.OutlineManager.OutlineItem.Folder) "Folder: ${item.name}" else "Container: ${item.name}"
                selectionOverlay.show("Delete $label?", listOf("No", "Yes"), 1, { choice ->
                    if (choice == 1) {
                        outlineManager.removeItem(item)
                        outlineOverlay.refresh()
                    }
                }, { })
            }
        }
        OutlineOverlay.Action.RENAME -> {
            if (item != null) {
                inputOverlay.show("New Name:", item.name, { name ->
                    item.name = name
                    outlineManager.markDirty()
                    outlineOverlay.refresh()
                }, { })
            }
        }
        OutlineOverlay.Action.LINK -> {
            if (item is com.example.doswriter.logic.OutlineManager.OutlineItem.FileContainer) {
                pendingLinkContainerId = item.id
                linkDocumentLauncher.launch(arrayOf("text/*", "image/jpeg", "image/png", "image/gif", "application/pdf", "application/octet-stream", "*/*"))
            }
        }
        OutlineOverlay.Action.LINK_REMOTE -> {
            if (item is com.example.doswriter.logic.OutlineManager.OutlineItem.FileContainer) {
                if (syncManager.isConnected()) {
                    pendingLinkRemoteContainerId = item.id
                    showRemoteFilePicker(".", { }) { path ->
                        outlineManager.linkRemoteFile(pendingLinkRemoteContainerId!!, path)
                        pendingLinkRemoteContainerId = null
                        outlineOverlay.refresh()
                    }
                } else Toast.makeText(this, "Sync not connected", Toast.LENGTH_SHORT).show()
            }
        }
        OutlineOverlay.Action.UNLINK -> {
            if (item is com.example.doswriter.logic.OutlineManager.OutlineItem.FileContainer) {
                outlineManager.unlinkFile(item.id)
                outlineOverlay.refresh()
            }
        }
    }
}

internal fun MainActivity.handleOutlineCreated(uri: Uri) {
    contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    outlineManager.currentFileUri = uri.toString()
    performOutlineSave(uri)
}

internal fun MainActivity.performOutlineSave(uri: Uri) {
    try {
        contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            val json = outlineManager.exportToJson()
            stream.write(json.toByteArray())
            outlineManager.markClean()
            showStatusToast("Outline saved")
        }
    } catch (e: Exception) { Toast.makeText(this, "Outline save failed", Toast.LENGTH_LONG).show() }
}

internal fun MainActivity.handleOutlineOpened(uri: Uri) {
    try {
        contentResolver.openInputStream(uri)?.use { stream ->
            val data = stream.bufferedReader().readText()
            outlineManager.importFromJson(data)
            outlineManager.currentFileUri = uri.toString()
            outlineManager.markClean()
            showStatusToast("Outline loaded")
        }
    } catch (e: Exception) { Toast.makeText(this, "Outline load failed", Toast.LENGTH_LONG).show() }
}

internal fun MainActivity.handleDocumentOpened(uri: Uri) {
    try {
        val isChildOfLinked = workingFolderManager.getFolders().any { uri.toString().startsWith(it.path) }
        
        if (!isChildOfLinked) {
            try {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (e: Exception) {
            }
        }

        val name = getFileName(uri)
        val buffer = bufferManager.getCurrentBuffer()
        
        buffer.reset()

        contentResolver.openInputStream(uri)?.use { stream ->
            if (name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".jpeg") || name.lowercase().endsWith(".png") || name.lowercase().endsWith(".gif")) {
                buffer.imageUri = uri.toString()
                buffer.fileName = name
                buffer.fileUri = uri.toString()
                recentFileManager.addFile(uri.toString(), name, false)
                editorView.setBuffer(buffer)
                updateStatus()
            } else if (name.lowercase().endsWith(".pdf")) {
                buffer.pdfUri = uri.toString()
                buffer.fileName = name
                buffer.fileUri = uri.toString()
                recentFileManager.addFile(uri.toString(), name, false)
                editorView.setBuffer(buffer)
                updateStatus()
            } else {
                val text = stream.bufferedReader().readText()
                buffer.setText(text)
                buffer.fileName = name
                buffer.fileUri = uri.toString()
                recentFileManager.addFile(uri.toString(), name, false)
                editorView.setBuffer(buffer) 
                updateStatus()
                showStatusToast("Loaded ${buffer.fileName}")

                val textLength = text.length
                val warningLimit = bufferManager.largeFileWarningLimit
                if (warningLimit > 0 && textLength > warningLimit) {
                    val sizeKb = textLength / 1000
                    Toast.makeText(this, "Large File Opened ($sizeKb KB). 50KB-100KB recommended per buffer.", Toast.LENGTH_LONG).show()
                }
            }
        }
        bufferManager.saveBuffers()
    } catch (e: Exception) { Toast.makeText(this, "Open failed", Toast.LENGTH_LONG).show() }
}

internal fun MainActivity.handleDocumentCreated(uri: Uri) {
    contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    val name = getFileName(uri)
    bufferManager.setBufferFile(uri.toString(), name)
    recentFileManager.addFile(uri.toString(), name, false)
    performUriSave(uri)
}

internal fun MainActivity.handlePdfFavoritesExported(uri: Uri) {
    try {
        val buffer = bufferManager.getCurrentBuffer()
        val text = "PDF Favorites for ${buffer.fileName ?: "Document"}\n\n" +
                buffer.pdfFavorites.joinToString("\n") { "Page ${it.pageIndex + 1}: ${it.identifier}" }
        
        contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        showStatusToast("Favorites exported")
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.handlePdfCreated(uri: Uri) {
    try {
        if (isMiniBookMode) {
            generateMiniBookPdf(uri)
            return
        }
        if (isManuscriptPdfMode) {
            generateManuscriptPdf(uri)
            return
        }
        contentResolver.openOutputStream(uri)?.use { stream ->
            val document = PdfDocument()
            val pageWidth = 595 
            val pageHeight = 842 
            val margin = 50f
            val contentWidth = pageWidth - 2 * margin
            var pageNum = 1

            for (bufferIdx in selectedBuffersForPdf) {
                val buffer = bufferManager.getBufferAt(bufferIdx)
                val text = buffer.getCleanText()
                val isMarkdown = buffer.fileName?.lowercase()?.endsWith(".md") == true

                var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                var page = document.startPage(pageInfo)
                var canvas = page.canvas
                var currentY = margin

                val headerPaint = Paint().apply { 
                    textSize = 14f
                    typeface = Typeface.MONOSPACE
                    isFakeBoldText = true
                }
                canvas.drawText("BUFFER ${bufferIdx + 1}: ${buffer.fileName ?: "Unnamed"}", margin, currentY, headerPaint)
                currentY += 30f

                if (isMarkdown) {
                    val mdPaint = TextPaint().apply { textSize = 12f; typeface = Typeface.MONOSPACE }
                    val lines = text.split("\n")
                    var i = 0
                    
                    while (i < lines.size) {
                        val line = lines[i]
                        var pl = line
                        var isHorizontalRule = false
                        var indent = 0f
                        
                        mdPaint.textSize = 12f
                        mdPaint.isFakeBoldText = false
                        mdPaint.typeface = Typeface.MONOSPACE

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
                                currentY = drawTableInPdf(canvas, tableRows, margin, currentY, contentWidth, mdPaint, pageHeight.toFloat())
                                i = j
                                continue
                            }
                        }

                        val imgMatch = Regex("!\\[(.*?)\\]\\((.*?)\\)").find(line)
                        if (imgMatch != null) {
                            val path = imgMatch.groupValues[2]
                            val bmp = loadBitmapForPdf(path)
                            if (bmp != null) {
                                val scale = contentWidth / bmp.width
                                val imgHeight = bmp.height * scale
                                
                                if (currentY + imgHeight > pageHeight - margin) {
                                    document.finishPage(page)
                                    pageNum++
                                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                                    page = document.startPage(pageInfo)
                                    canvas = page.canvas
                                    currentY = margin
                                }
                                
                                canvas.drawBitmap(bmp, null, RectF(margin, currentY, margin + contentWidth, currentY + imgHeight), null)
                                currentY += imgHeight + 10f
                            } else {
                                val errorPaint = Paint(mdPaint).apply { color = android.graphics.Color.RED }
                                canvas.drawText("[Error: Image not found ($path)]", margin, currentY + 15f, errorPaint)
                                currentY += 25f
                            }
                            i++
                            continue
                        }

                        if (line.trim() == "---" || line.trim() == "***" || line.trim() == "___") {
                            isHorizontalRule = true
                        } else if (line.startsWith("# ")) {
                            mdPaint.textSize = 18f
                            mdPaint.isFakeBoldText = true
                            pl = line.substring(2)
                        } else if (line.startsWith("## ")) {
                            mdPaint.textSize = 15f
                            mdPaint.isFakeBoldText = true
                            pl = line.substring(3)
                        } else if (line.startsWith("- ")) {
                            pl = "• " + line.substring(2)
                            indent = 20f
                        } else if (line.startsWith("> ")) {
                            pl = line.substring(2)
                            mdPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC)
                            indent = 30f
                        }

                        if (isHorizontalRule) {
                            if (currentY + 10f > pageHeight - margin) {
                                document.finishPage(page)
                                pageNum++
                                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                                page = document.startPage(pageInfo)
                                canvas = page.canvas
                                currentY = margin
                            }
                            canvas.drawLine(margin, currentY + 5f, margin + contentWidth, currentY + 5f, mdPaint)
                            currentY += 15f
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

                        val spanned = if (android.os.Build.VERSION.SDK_INT >= 24) {
                            Html.fromHtml(htmlLine, Html.FROM_HTML_MODE_LEGACY)
                        } else {
                            @Suppress("DEPRECATION")
                            Html.fromHtml(htmlLine)
                        }

                        val layout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            StaticLayout.Builder.obtain(spanned, 0, spanned.length, mdPaint, (contentWidth - indent).toInt())
                                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                .setLineSpacing(2f, 1.1f)
                                .build()
                        } else {
                            @Suppress("DEPRECATION")
                            StaticLayout(spanned, mdPaint, (contentWidth - indent).toInt(), Layout.Alignment.ALIGN_NORMAL, 1.1f, 2f, false)
                        }

                        if (currentY + layout.height > pageHeight - margin) {
                            document.finishPage(page)
                            pageNum++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                            page = document.startPage(pageInfo)
                            canvas = page.canvas
                            currentY = margin
                        }

                        canvas.save()
                        canvas.translate(margin + indent, currentY)
                        layout.draw(canvas)
                        canvas.restore()
                        currentY += layout.height + 5f
                        i++
                    }
                } else {
                    val textPaint = Paint().apply { textSize = 12f; typeface = Typeface.MONOSPACE }
                    val lines = wrapTextForPdf(text, textPaint, contentWidth.toInt())
                    for (line in lines) {
                        if (currentY > pageHeight - margin) {
                            document.finishPage(page)
                            pageNum++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                            page = document.startPage(pageInfo)
                            canvas = page.canvas
                            currentY = margin
                        }
                        canvas.drawText(line, margin, currentY + 12f, textPaint)
                        currentY += 14f
                    }
                }
                document.finishPage(page)
                pageNum++
            }
            document.writeTo(stream)
            document.close()
            showStatusToast("PDF created")
        }
    } catch (e: Exception) { 
        e.printStackTrace()
        Toast.makeText(this, "PDF failed: ${e.message}", Toast.LENGTH_LONG).show() 
    }
}

internal fun MainActivity.loadBitmapForPdf(path: String): Bitmap? {
    var cleanPath = path
    if (cleanPath.startsWith("//")) cleanPath = cleanPath.substring(1)
    if (cleanPath.startsWith("file:///")) cleanPath = cleanPath.substring(7)
    else if (cleanPath.startsWith("file://")) cleanPath = cleanPath.substring(7)

    try {
        val uri = Uri.parse(path)
        val stream = contentResolver.openInputStream(uri)
        return BitmapFactory.decodeStream(stream)
    } catch (e: Exception) {
        try {
            return BitmapFactory.decodeFile(cleanPath)
        } catch (e2: Exception) {}
    }
    return null
}

internal fun MainActivity.drawTableInPdf(
    canvas: Canvas,
    rows: List<List<String>>,
    xStart: Float,
    yStart: Float,
    maxWidth: Float,
    paint: Paint,
    pageHeight: Float
): Float {
    var currentY = yStart
    val colCount = rows.maxOf { it.size }
    val colWidth = maxWidth / colCount
    val cellPadding = 8f
    val linePaint = Paint(paint).apply { style = Paint.Style.STROKE; strokeWidth = 0.5f; color = android.graphics.Color.DKGRAY }

    rows.forEachIndexed { rowIndex, cells ->
        var maxHeight = 0f
        val layouts = mutableListOf<StaticLayout>()
        
        cells.forEach { cell ->
            val spanned = if (android.os.Build.VERSION.SDK_INT >= 24) {
                Html.fromHtml(cell, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(cell)
            }
            val layout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(spanned, 0, spanned.length, TextPaint(paint), (colWidth - 2 * cellPadding).toInt().coerceAtLeast(1)).build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(spanned, TextPaint(paint), (colWidth - 2 * cellPadding).toInt().coerceAtLeast(1), Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, false)
            }
            layouts.add(layout)
            maxHeight = max(maxHeight, layout.height.toFloat())
        }

        val rowHeight = maxHeight + 2 * cellPadding

        if (currentY + rowHeight > pageHeight - 50f) {
            return@forEachIndexed
        }

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
        
        currentY += rowHeight
    }
    
    return currentY
}

internal fun MainActivity.generateManuscriptPdf(uri: Uri) {
    try {
        contentResolver.openOutputStream(uri)?.use { stream ->
            val buffer = bufferManager.getCurrentBuffer()
            val dims = manuscriptEngine.getDimensions(buffer)
            val breaks = manuscriptEngine.paginate(buffer)
            
            val document = PdfDocument()

            breaks.forEachIndexed { index, pageBreak ->
                val pageInfo = PdfDocument.PageInfo.Builder(dims.width.toInt(), dims.height.toInt(), index + 1).create()
                val page = document.startPage(pageInfo)
                
                manuscriptEngine.drawPage(buffer, pageBreak, page.canvas, 1.0f)
                
                document.finishPage(page)
            }

            document.writeTo(stream)
            document.close()
            showStatusToast("Manuscript PDF created")
        }
    } catch (e: Exception) { 
        e.printStackTrace()
        Toast.makeText(this, "Manuscript Export failed", Toast.LENGTH_LONG).show() 
    }
}

internal fun MainActivity.generateMiniBookPdf(uri: Uri) {
    try {
        contentResolver.openOutputStream(uri)?.use { stream ->
            val document = PdfDocument()
            val paint = Paint().apply { textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE }
            
            val pageWidth = 792
            val pageHeight = 612
            val colCount = 4
            val rowCount = 2
            val colWidth = pageWidth / colCount
            val rowHeight = pageHeight / rowCount
            val margin = 12
            
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            
            val gridPaint = Paint().apply { color = android.graphics.Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.5f }
            for (i in 1 until colCount) canvas.drawLine((i * colWidth).toFloat(), 0f, (i * colWidth).toFloat(), pageHeight.toFloat(), gridPaint)
            canvas.drawLine(0f, rowHeight.toFloat(), pageWidth.toFloat(), rowHeight.toFloat(), gridPaint)

            for (idx in 0 until 8) {
                val col = idx % 4
                val row = idx / 4
                val startX = col * colWidth + margin
                val startY = row * rowHeight + margin
                
                val buffer = bufferManager.getBufferAt(idx)
                
                val lines = wrapTextForPdf(buffer.getCleanText(), paint, colWidth - 2 * margin)
                var currentY = startY + 15
                for (lineIdx in 0 until min(lines.size, 24)) {
                    canvas.drawText(lines[lineIdx].take(28), startX.toFloat(), currentY.toFloat(), paint)
                    currentY += 14 
                }
            }
            
            document.finishPage(page)
            document.writeTo(stream)
            document.close()
            showStatusToast("Mini-Book PDF created")
        }
    } catch (e: Exception) { e.printStackTrace(); Toast.makeText(this, "Mini-Book failed", Toast.LENGTH_LONG).show() }
}

internal fun MainActivity.wrapTextForPdf(text: String, paint: Paint, maxWidth: Int): List<String> {
    val result = mutableListOf<String>()
    for (line in text.split("\n")) {
        var currentLine = ""
        for (word in line.split(" ")) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) currentLine = testLine else { if (currentLine.isNotEmpty()) result.add(currentLine); currentLine = word }
        }
        result.add(currentLine)
    }
    return result
}

internal fun MainActivity.getFileName(uri: Uri): String {
    var name = "file.txt"
    contentResolver.query(uri, null, null, null, null)?.use { cursor -> if (cursor.moveToFirst()) name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) }
    return name
}

internal fun MainActivity.handleTransferToRemote(uri: Uri) {
    try {
        val name = getFileName(uri)
        val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        if (content != null) {
            syncManager.saveRemoteFile(name, content)
            Toast.makeText(this, "Transferring $name to host...", Toast.LENGTH_SHORT).show()
            enableImmersiveMode()
        }
    } catch (e: Exception) {
        Toast.makeText(this, "Transfer failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

internal fun MainActivity.performUriSave(uri: Uri) {
    try {
        contentResolver.openOutputStream(uri, "wt")?.use { it.write(bufferManager.getCurrentBuffer().getCleanText().toByteArray()) }
        bufferManager.getCurrentBuffer().markClean()
            showStatusToast("Saved successfully")
    } catch (e: Exception) { e.printStackTrace(); Toast.makeText(this, "Save failed", Toast.LENGTH_LONG).show() }
}
