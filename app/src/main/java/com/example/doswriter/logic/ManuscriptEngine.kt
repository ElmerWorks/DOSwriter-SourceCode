package com.example.doswriter.logic

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.doswriter.model.EditorBuffer
import java.io.File
import kotlin.math.max
import kotlin.math.min

class ManuscriptEngine(private val context: Context) {

    data class PageDimensions(val width: Float, val height: Float, val margin: Float, val contentWidth: Int, val contentHeight: Int)
    data class ScratchLogEntry(val delimiter: String, val text: String)

    fun parseScratchLog(logText: String): List<ScratchLogEntry> {
        if (logText.isEmpty()) return emptyList()
        val entries = mutableListOf<ScratchLogEntry>()
        val lines = logText.split("\n")
        var currentDelimiter = ""
        var currentText = StringBuilder()

        for (line in lines) {
            if (line.startsWith("--")) {
                if (currentDelimiter.isNotEmpty() || currentText.isNotEmpty()) {
                    entries.add(ScratchLogEntry(currentDelimiter, currentText.toString().trim()))
                    currentText = StringBuilder()
                }
                currentDelimiter = line
            } else {
                if (currentDelimiter.isNotEmpty()) {
                    if (currentText.isNotEmpty()) currentText.append("\n")
                    currentText.append(line)
                } else {
                    if (line.isNotBlank()) {
                        currentDelimiter = "-- Log Entry"
                        currentText.append(line)
                    }
                }
            }
        }
        if (currentDelimiter.isNotEmpty() || currentText.isNotEmpty()) {
            entries.add(ScratchLogEntry(currentDelimiter, currentText.toString().trim()))
        }
        return entries
    }

    fun getScratchLogEntries(buffer: EditorBuffer? = null, workingFolderUriStr: String? = null): List<ScratchLogEntry> {
        var logText = ""
        try {
            if (!workingFolderUriStr.isNullOrEmpty()) {
                val root = DocumentFile.fromTreeUri(context, Uri.parse(workingFolderUriStr))
                if (root != null && root.exists()) {
                    val file = root.findFile("scratchlog.txt")
                    if (file != null && file.exists()) {
                        val inputStream = context.contentResolver.openInputStream(file.uri)
                        if (inputStream != null) {
                            logText = inputStream.bufferedReader().use { it.readText() }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (logText.isEmpty()) {
            try {
                val logFile = File(context.getExternalFilesDir(null), "scratchlog.txt")
                if (logFile.exists()) {
                    logText = logFile.readText()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (logText.isEmpty()) {
            try {
                val logFile = File(context.filesDir, "scratchlog.txt")
                if (logFile.exists()) {
                    logText = logFile.readText()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val entries = parseScratchLog(logText)
        if (entries.isEmpty() && buffer != null && !buffer.getCleanText().isEmpty()) {
            return listOf(ScratchLogEntry("-- Current Scratchpad", buffer.getCleanText()))
        }
        return entries
    }

    fun getDimensions(buffer: EditorBuffer): PageDimensions {
        val settings = buffer.manuscriptSettings
        val sizeParts = settings.pageSize.split("x")
        var rawWidth = if (sizeParts.size >= 2) sizeParts[0].toFloat() * 72f else 8.5f * 72f
        var rawHeight = if (sizeParts.size >= 2) sizeParts[1].toFloat() * 72f else 11f * 72f
        
        if (!settings.isPortrait) {
            val temp = rawWidth
            rawWidth = rawHeight
            rawHeight = temp
        }

        val marginPts = settings.margins * 72f
        val contentWidth = (rawWidth - 2 * marginPts).toInt().coerceAtLeast(100)
        val contentHeight = (rawHeight - 2 * marginPts).toInt().coerceAtLeast(100)
        
        return PageDimensions(rawWidth, rawHeight, marginPts, contentWidth, contentHeight)
    }

    data class PageBreak(val startCharIndex: Int, val endCharIndex: Int)

    private fun getBufferTypeface(buffer: EditorBuffer): Typeface {
        val type = buffer.fontType
        val normalizedType = type.uppercase().replace("_", " ").replace("-", " ")
        val assetName = type.replace(" ", "")
        val extensions = listOf(".ttf", ".otf")
        
        for (ext in extensions) {
            try {
                val assetPath = "fonts/$assetName$ext"
                context.assets.open(assetPath).close()
                return Typeface.createFromAsset(context.assets, assetPath)
            } catch (e: Exception) {}
        }

        return when (normalizedType) {
            "SERIF", "TIMES NEW ROMAN", "GEORGIA", "GARAMOND" -> Typeface.SERIF
            "SANS SERIF", "ARIAL", "HELVETICA", "VERDANA", "TREBUCHET", "NOTO SANS", "ROBOTO" -> Typeface.SANS_SERIF
            "MONOSPACE", "COURIER NEW", "COURIER", "CONSOLAS", "LUCIDA CONSOLE", "ROBOTO MONO", "JETBRAINS MONO", "FIXED" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
    }

    private fun getFirstSentence(text: String): String {
        if (text.isEmpty()) return ""
        val terminators = charArrayOf('.', '!', '?')
        var firstTerminator = -1
        for (i in text.indices) {
            if (text[i] in terminators) {
                if (i == text.length - 1 || text[i+1].isWhitespace()) {
                    firstTerminator = i
                    break
                }
            }
        }
        return if (firstTerminator != -1) {
            text.substring(0, firstTerminator + 1)
        } else {
            text 
        }
    }

    private fun getTruncatedParagraph(para: String, paint: TextPaint, maxWidth: Int, maxHeight: Float): String {
        if (para.isEmpty()) return ""
        
        val spannedFull = if (android.os.Build.VERSION.SDK_INT >= 24) Html.fromHtml(para, Html.FROM_HTML_MODE_LEGACY) else @Suppress("DEPRECATION") Html.fromHtml(para)
        val layoutFull = StaticLayout.Builder.obtain(spannedFull, 0, spannedFull.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.2f).build()
        if (layoutFull.height <= maxHeight) return para

        val sentences = mutableListOf<String>()
        var remaining = para
        while (remaining.isNotEmpty()) {
            val sentence = getFirstSentence(remaining)
            if (sentence.isEmpty()) break
            sentences.add(sentence)
            remaining = remaining.substring(sentence.length).trimStart()
        }

        val result = StringBuilder()
        sentences.forEach { sentence ->
            val nextTry = if (result.isEmpty()) sentence else "$result $sentence"
            val spanned = if (android.os.Build.VERSION.SDK_INT >= 24) Html.fromHtml(nextTry, Html.FROM_HTML_MODE_LEGACY) else @Suppress("DEPRECATION") Html.fromHtml(nextTry)
            val layout = StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.2f).build()
            
            if (layout.height <= maxHeight) {
                if (result.isNotEmpty()) result.append(" ")
                result.append(sentence)
            } else {
                return@forEach
            }
        }
        
        return if (result.isEmpty()) getFirstSentence(para) else result.toString()
    }

    fun paginate(buffer: EditorBuffer, workingFolderUriStr: String? = null): List<PageBreak> {
        val settings = buffer.manuscriptSettings
        
        if (buffer.id == 17) {
            val entries = getScratchLogEntries(buffer, workingFolderUriStr)
            if (entries.isEmpty()) {
                return listOf(PageBreak(0, 0))
            }
            return entries.indices.map { idx -> PageBreak(idx, idx + 1) }
        }

        val cleanText = buffer.getCleanText()
        if (cleanText.isEmpty()) return emptyList()

        val dims = getDimensions(buffer)
        val paint = TextPaint().apply {
            textSize = settings.fontSize
            typeface = getBufferTypeface(buffer)
            isAntiAlias = true
        }

        val pageBreaks = mutableListOf<PageBreak>()
        var currentPageStart = 0
        var currentPageHeight = 0f
        
        if (settings.isParagraphCollapseEnabled) {
            var pos = 0
            while (pos < cleanText.length) {
                // Find actual start of paragraph (skipping whitespace)
                while (pos < cleanText.length && cleanText[pos].isWhitespace()) pos++
                if (pos >= cleanText.length) break
                
                val startOfPara = pos
                var endOfPara = cleanText.indexOf("\n\n", pos)
                if (endOfPara == -1) endOfPara = cleanText.length
                
                val paraText = cleanText.substring(startOfPara, endOfPara)
                val displayContent = if (settings.isFirstSentenceOnly) {
                    getFirstSentence(paraText)
                } else {
                    getTruncatedParagraph(paraText, paint, dims.contentWidth, dims.contentHeight.toFloat())
                }

                val spanned = if (android.os.Build.VERSION.SDK_INT >= 24) {
                    Html.fromHtml(displayContent, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(displayContent)
                }
                
                val layout = StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, dims.contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.2f)
                    .build()

                val layoutHeight = min(layout.height.toFloat(), dims.contentHeight.toFloat())

                if (settings.isFirstSentenceOnly) {
                    // One page per paragraph start
                    pageBreaks.add(PageBreak(startOfPara, endOfPara))
                    currentPageStart = endOfPara
                } else {
                    // Stacking paragraphs
                    if (currentPageHeight + layoutHeight > dims.contentHeight && currentPageHeight > 0) {
                        pageBreaks.add(PageBreak(currentPageStart, startOfPara))
                        currentPageStart = startOfPara
                        currentPageHeight = 0f
                    }
                    currentPageHeight += layoutHeight + paint.textSize
                }
                
                pos = endOfPara + 2 // Advance past the double newline
            }
        } else {
            // Standard line-by-line pagination
            var absolutePos = 0
            val lines = cleanText.split("\n")
            lines.forEach { line ->
                val spanned = if (android.os.Build.VERSION.SDK_INT >= 24) {
                    Html.fromHtml(line, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(line)
                }
                
                val layout = StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, dims.contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.2f)
                    .build()

                var lineIdx = 0
                while (lineIdx < layout.lineCount) {
                    val layoutLineHeight = layout.getLineBottom(lineIdx) - layout.getLineTop(lineIdx)

                    if (currentPageHeight + layoutLineHeight > dims.contentHeight && currentPageHeight > 0) {
                        val charOffset = layout.getLineStart(lineIdx)
                        pageBreaks.add(PageBreak(currentPageStart, absolutePos + charOffset))
                        currentPageStart = absolutePos + charOffset
                        currentPageHeight = 0f
                    }
                    
                    currentPageHeight += layoutLineHeight
                    lineIdx++
                }
                absolutePos += line.length + 1
            }
        }

        if (currentPageStart < cleanText.length) {
            pageBreaks.add(PageBreak(currentPageStart, cleanText.length))
        }

        return pageBreaks
    }

    fun renderPage(buffer: EditorBuffer, pageBreak: PageBreak, bitmap: Bitmap, workingFolderUriStr: String? = null) {
        val dims = getDimensions(buffer)
        val canvas = Canvas(bitmap)
        val scale = bitmap.width.toFloat() / dims.width
        drawPage(buffer, pageBreak, canvas, scale, workingFolderUriStr)
    }

    fun drawPage(buffer: EditorBuffer, pageBreak: PageBreak, canvas: Canvas, scale: Float, workingFolderUriStr: String? = null) {
        val dims = getDimensions(buffer)
        val settings = buffer.manuscriptSettings
        
        canvas.drawColor(Color.WHITE)

        val paint = TextPaint().apply {
            textSize = settings.fontSize * scale
            typeface = getBufferTypeface(buffer)
            color = Color.BLACK
            isAntiAlias = true
        }

        canvas.save()
        canvas.translate(dims.margin * scale, dims.margin * scale)

        if (buffer.id == 17) {
            val entries = getScratchLogEntries(buffer, workingFolderUriStr)
            val entryIndex = pageBreak.startCharIndex
            val pageText = if (entryIndex in entries.indices) {
                val entry = entries[entryIndex]
                "${entry.delimiter}\n${entry.text}"
            } else {
                "No log entries found in scratchlog.txt"
            }

            val spanned = if (android.os.Build.VERSION.SDK_INT >= 24) {
                Html.fromHtml(pageText.replace("\n", "<br>"), Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(pageText.replace("\n", "<br>"))
            }

            val layout = StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, (dims.contentWidth * scale).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .build()

            layout.draw(canvas)
            canvas.restore()
            return
        }

        val fullText = buffer.getCleanText()
        val textInRange = if (pageBreak.startCharIndex < fullText.length) {
            fullText.substring(pageBreak.startCharIndex, min(pageBreak.endCharIndex, fullText.length))
        } else ""

        if (textInRange.isNotEmpty()) {
            if (settings.isParagraphCollapseEnabled) {
                // Determine display content using the same indices/trimming as pagination
                var currentY = 0f
                var pos = 0
                while (pos < textInRange.length) {
                    // Skip leading whitespace to find para start
                    while (pos < textInRange.length && textInRange[pos].isWhitespace()) pos++
                    if (pos >= textInRange.length) break
                    
                    var endOfPara = textInRange.indexOf("\n\n", pos)
                    if (endOfPara == -1) endOfPara = textInRange.length
                    
                    val paraText = textInRange.substring(pos, endOfPara)
                    val displayContent = if (settings.isFirstSentenceOnly) {
                        getFirstSentence(paraText)
                    } else {
                        getTruncatedParagraph(paraText, paint, (dims.contentWidth * scale).toInt(), (dims.contentHeight * scale))
                    }

                    val spanned = if (android.os.Build.VERSION.SDK_INT >= 24) {
                        Html.fromHtml(displayContent, Html.FROM_HTML_MODE_LEGACY)
                    } else {
                        @Suppress("DEPRECATION")
                        Html.fromHtml(displayContent)
                    }

                    val layout = StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, (dims.contentWidth * scale).toInt())
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.2f)
                        .build()
                    
                    val remainingH = (dims.contentHeight * scale) - currentY
                    if (remainingH <= 0) break
                    
                    canvas.save()
                    canvas.clipRect(0f, 0f, dims.contentWidth * scale, remainingH)
                    layout.draw(canvas)
                    canvas.restore()

                    val advance = min(layout.height.toFloat(), remainingH) + (paint.textSize)
                    canvas.translate(0f, advance)
                    currentY += advance
                    
                    pos = endOfPara + 2
                }
            } else {
                val spanned = if (android.os.Build.VERSION.SDK_INT >= 24) {
                    Html.fromHtml(textInRange.replace("\n", "<br>"), Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(textInRange.replace("\n", "<br>"))
                }

                val layout = StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, (dims.contentWidth * scale).toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.2f)
                    .build()

                layout.draw(canvas)
            }
        }

        canvas.restore()
    }
}
