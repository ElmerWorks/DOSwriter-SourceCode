package com.example.doswriter.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import com.example.doswriter.logic.OutlineManager
import kotlin.math.max
import kotlin.math.min

class OutlineOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { SELECT, SETUP }
    private var mode = Mode.SELECT
    private var manager: OutlineManager? = null
    
    private val items = mutableListOf<DisplayItem>()
    private var selectedIndex = 0
    private var scrollOffset = 0
    
    private var onFileSelected: ((String, String, Boolean, Boolean) -> Unit)? = null
    private var onSetupAction: ((OutlineManager.OutlineItem?, Action) -> Unit)? = null

    private var previewBitmap: Bitmap? = null
    private var previewText: String? = null
    
    private data class PreviewLayout(val layout: StaticLayout?, val indent: Float, val image: Bitmap? = null, val error: String? = null)
    private var previewLayouts = mutableListOf<PreviewLayout>()

    private val previewHandler = Handler(Looper.getMainLooper())
    private val previewRunnable = Runnable { updatePreview() }
    private var lastPreviewPath: String? = null

    enum class Action { ADD_FOLDER, ADD_CONTAINER, DELETE, LINK, LINK_REMOTE, RENAME, UNLINK }

    private val bgPaint = Paint().apply {
        color = Color.rgb(25, 25, 25) // Slightly lighter for better e-ink distinction
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 38f 
        isAntiAlias = true
    }

    private val selectionPaint = Paint().apply {
        color = Color.rgb(0, 255, 0) // Brighter green for e-ink contrast
        style = Paint.Style.FILL
    }

    private var itemHeight = 70f

    private var isEinkMode = false
    private var menuTextColor = Color.WHITE

    fun setMenuAppearance(textSize: Float, backgroundColor: Int, textColor: Int) {
        textPaint.textSize = textSize
        this.menuTextColor = textColor
        textPaint.color = textColor
        bgPaint.color = backgroundColor
        val r = Color.red(backgroundColor)
        val g = Color.green(backgroundColor)
        val b = Color.blue(backgroundColor)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
        isEinkMode = luminance > 128
        itemHeight = textSize * 1.8f
        invalidate()
    }

    private data class DisplayItem(
        val item: OutlineManager.OutlineItem,
        val isFolder: Boolean,
        val depth: Int
    )

    fun show(mode: Mode, manager: OutlineManager, onSelect: (String, String, Boolean, Boolean) -> Unit, onSetup: (OutlineManager.OutlineItem?, Action) -> Unit) {
        this.mode = mode
        this.manager = manager
        this.onFileSelected = onSelect
        this.onSetupAction = onSetup
        rebuildItems()
        selectedIndex = if (items.isNotEmpty()) 0 else -1
        scrollOffset = 0
        visibility = VISIBLE
        schedulePreview()
        invalidate()
    }

    private fun rebuildItems() {
        items.clear()
        manager?.getFolders()?.forEach { folder ->
            items.add(DisplayItem(folder, true, 0))
            folder.containers.forEach { container ->
                items.add(DisplayItem(container, false, 1))
            }
        }
    }

    fun moveUp() {
        if (selectedIndex > 0) {
            selectedIndex--
            updateScroll()
            schedulePreview()
            invalidate()
        }
    }

    fun moveDown() {
        if (selectedIndex < items.size - 1) {
            selectedIndex++
            updateScroll()
            schedulePreview()
            invalidate()
        }
    }

    private fun schedulePreview() {
        previewHandler.removeCallbacks(previewRunnable)
        previewHandler.postDelayed(previewRunnable, 500)
    }

    private fun updatePreview() {
        if (selectedIndex < 0 || selectedIndex >= items.size) return
        val item = items[selectedIndex].item
        if (item !is OutlineManager.OutlineItem.FileContainer) {
            clearPreview()
            return
        }

        val path = item.linkedUri ?: item.remotePath
        if (path == null || path == lastPreviewPath) return
        lastPreviewPath = path

        clearPreview()

        if (item.remotePath != null) {
            previewText = "[Remote File: ${item.name}]\nSync server required for preview."
            invalidate()
            return
        }

        try {
            val uri = Uri.parse(path)
            val type = context.contentResolver.getType(uri) ?: ""
            val name = item.fileName?.lowercase() ?: ""

            if (type.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif")) {
                val stream = context.contentResolver.openInputStream(uri)
                previewBitmap = BitmapFactory.decodeStream(stream)
            } else if (type == "application/pdf" || name.endsWith(".pdf")) {
                val fd = context.contentResolver.openFileDescriptor(uri, "r")
                fd?.let {
                    val renderer = PdfRenderer(it)
                    val page = renderer.openPage(0)
                    val bitmap = Bitmap.createBitmap(500, 700, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    previewBitmap = bitmap
                    page.close()
                    renderer.close()
                    it.close()
                }
            } else {
                // Text preview
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (text != null) {
                    if (name.endsWith(".md")) {
                        prepareMarkdownPreview(text.take(2000))
                    } else {
                        previewText = text.take(1000)
                    }
                }
            }
        } catch (e: Exception) {
            previewText = "Preview Error: ${e.message}"
        }
        invalidate()
    }

    private fun prepareMarkdownPreview(text: String) {
        val w = width.toFloat()
        val splitX = w * 0.55f
        val right = w * 0.95f
        val previewWidth = (right - splitX - 40f).toInt().coerceAtLeast(1)
        
        val mdPaint = TextPaint(textPaint)
        mdPaint.textSize = textPaint.textSize * 0.6f

        text.lines().forEach { line ->
            var paint = mdPaint
            var processedLine = line.trimEnd()
            var isHorizontalRule = false
            var indent = 0f

            val imgMatch = Regex("!\\[(.*?)\\]\\((.*?)\\)").find(line)
            if (imgMatch != null) {
                val path = imgMatch.groupValues[2]
                val sanitizedPath = path.replace("\\", "/")
                var cleanPath = sanitizedPath
                if (cleanPath.startsWith("//")) cleanPath = cleanPath.substring(1)
                if (cleanPath.startsWith("file:///")) cleanPath = cleanPath.substring(7)
                else if (cleanPath.startsWith("file://")) cleanPath = cleanPath.substring(7)

                try {
                    var uri = Uri.parse(sanitizedPath)
                    if (uri.scheme == null) uri = Uri.parse("file://$cleanPath")

                    val stream = context.contentResolver.openInputStream(uri)
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        previewLayouts.add(PreviewLayout(null, 0f, bmp))
                        return@forEach
                    }
                } catch (e: Exception) {
                    try {
                        val bmp = BitmapFactory.decodeFile(cleanPath)
                        if (bmp != null) {
                            previewLayouts.add(PreviewLayout(null, 0f, bmp))
                            return@forEach
                        }
                    } catch (e2: Exception) {}
                }
                previewLayouts.add(PreviewLayout(null, 0f, null, "Image not found: $path"))
                return@forEach
            }
            
            if (line.trim() == "---" || line.trim() == "***" || line.trim() == "___") {
                isHorizontalRule = true
            } else if (processedLine.startsWith("# ")) {
                paint = TextPaint(mdPaint).apply { textSize = mdPaint.textSize * 1.4f; isFakeBoldText = true }
                processedLine = processedLine.substring(2)
            } else if (processedLine.startsWith("## ")) {
                paint = TextPaint(mdPaint).apply { textSize = mdPaint.textSize * 1.2f; isFakeBoldText = true }
                processedLine = processedLine.substring(3)
            } else if (processedLine.startsWith("- ")) {
                processedLine = "• " + processedLine.substring(2)
                indent = 20f
            } else if (processedLine.startsWith("> ")) {
                processedLine = "<i>" + processedLine.substring(2) + "</i>"
                indent = 25f
            } else if (Regex("^[0-9]+\\.\\s+").find(processedLine.trim()) != null) {
                processedLine = processedLine.trim()
                indent = 20f
            }

            if (isHorizontalRule) {
                val spanned = Html.fromHtml("<hr>") // Dummy layout for height
                val layout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, previewWidth.toInt()).build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(spanned, paint, previewWidth.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.1f, 2f, false)
                }
                previewLayouts.add(PreviewLayout(layout, 0f))
                return@forEach
            }

            val htmlLine = processedLine
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

            val curWidth = (previewWidth - indent).toInt().coerceAtLeast(1)
            val layout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, curWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1.1f)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(spanned, paint, curWidth, Layout.Alignment.ALIGN_NORMAL, 1.1f, 2f, false)
            }
            
            previewLayouts.add(PreviewLayout(layout, indent))
        }
    }

    private fun clearPreview() {
        previewBitmap?.recycle()
        previewBitmap = null
        previewText = null
        previewLayouts.clear()
    }

    fun confirm(shiftPressed: Boolean = false) {
        if (selectedIndex < 0 || selectedIndex >= items.size) return
        val displayItem = items[selectedIndex]
        val item = displayItem.item

        if (mode == Mode.SELECT) {
            if (item is OutlineManager.OutlineItem.FileContainer) {
                if (item.remotePath != null) {
                    onFileSelected?.invoke(item.remotePath!!, item.fileName ?: "remote.txt", true, shiftPressed)
                    hide()
                } else if (item.linkedUri != null) {
                    onFileSelected?.invoke(item.linkedUri!!, item.fileName ?: "file.txt", false, shiftPressed)
                    hide()
                }
            }
        } else {
            if (item is OutlineManager.OutlineItem.FileContainer) {
                onSetupAction?.invoke(item, Action.LINK)
            } else {
                onSetupAction?.invoke(item, Action.RENAME)
            }
        }
    }

    fun triggerAction(action: Action) {
        val selectedItem = if (selectedIndex >= 0 && selectedIndex < items.size) items[selectedIndex].item else null
        onSetupAction?.invoke(selectedItem, action)
    }

    fun selectItem(id: String) {
        val index = items.indexOfFirst { it.item.id == id }
        if (index != -1) {
            selectedIndex = index
            updateScroll()
            schedulePreview()
            invalidate()
        }
    }

    fun refresh(selectId: String? = null) {
        rebuildItems()
        if (selectId != null) {
            val index = items.indexOfFirst { it.item.id == selectId }
            if (index != -1) {
                selectedIndex = index
            }
        } else {
            if (selectedIndex < 0 && items.isNotEmpty()) {
                selectedIndex = 0
            } else if (selectedIndex >= items.size) {
                selectedIndex = items.size - 1
            }
        }
        updateScroll()
        schedulePreview()
        invalidate()
    }

    fun hide() {
        visibility = GONE
    }

    private fun updateScroll() {
        val maxVisible = calculateMaxVisible()
        if (maxVisible <= 0) return

        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex
        } else if (selectedIndex >= scrollOffset + maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1
        }
        scrollOffset = scrollOffset.coerceIn(0, max(0, items.size - maxVisible))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        if (event.action == MotionEvent.ACTION_DOWN) {
            val w = width.toFloat()
            val h = height.toFloat()
            // Narrower margins for touch detection too
            if (event.x < w * 0.05f || event.x > w * 0.95f || event.y < h * 0.05f || event.y > h * 0.95f) {
                hide()
                return true
            }
            performClick()
            val startY = h * 0.16f
            val clickedIndex = ((event.y - startY) / itemHeight).toInt() + scrollOffset
            if (clickedIndex >= 0 && clickedIndex < items.size) {
                selectedIndex = clickedIndex
                invalidate()
                if (mode == Mode.SELECT) confirm()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun calculateMaxVisible(): Int {
        val availableHeight = height * 0.70f 
        return (availableHeight / itemHeight).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        
        // Background box with small margin at screen edges
        val left = w * 0.05f
        val top = h * 0.05f
        val right = w * 0.95f
        val bottom = h * 0.95f
        
        canvas.drawRect(left, top, right, bottom, bgPaint)
        canvas.drawRect(left, top, right, bottom, borderPaint)

        val splitX = w * 0.55f
        canvas.drawLine(splitX, top, splitX, bottom, borderPaint)

        val startX = w * 0.08f

        if (items.isEmpty() && mode == Mode.SELECT) {
            textPaint.color = menuTextColor
            textPaint.isFakeBoldText = true
            val msg1 = "No items. Enter Ctrl-Alt-O"
            val msg2 = "to set up Outline Manager."
            canvas.drawText(msg1, startX, h * 0.48f, textPaint)
            canvas.drawText(msg2, startX, h * 0.54f, textPaint)
            textPaint.isFakeBoldText = false
            return
        }

        val startY = h * 0.16f 
        val maxVisible = calculateMaxVisible()

        val title = if (mode == Mode.SELECT) "OUTLINE SELECTION" else "OUTLINE SETUP"
        textPaint.color = menuTextColor
        textPaint.isFakeBoldText = true
        canvas.drawText(title, startX, h * 0.10f, textPaint)
        textPaint.isFakeBoldText = false

        for (i in 0 until maxVisible) {
            val itemIdx = scrollOffset + i
            if (itemIdx >= items.size) break
            
            val displayItem = items[itemIdx]
            val y = startY + (i * itemHeight)

            if (itemIdx == selectedIndex) {
                canvas.drawRect(w * 0.05f, y - (itemHeight * 0.65f), splitX, y + (itemHeight * 0.3f), selectionPaint)
                textPaint.color = Color.BLACK 
            } else {
                textPaint.color = if (displayItem.isFolder) {
                    if (isEinkMode) Color.rgb(0, 80, 150) else Color.CYAN
                } else {
                    menuTextColor
                }
            }
            
            val depthPadding = displayItem.depth * 30f
            var label = if (displayItem.isFolder) "📁 " + displayItem.item.name else displayItem.item.name
            
            if (label.isEmpty()) {
                label = "(empty)"
            }
            
            // Truncate label to fit half screen
            val availableLabelWidth = splitX - startX - depthPadding - 20f
            if (textPaint.measureText(label) > availableLabelWidth) {
                var tempLabel = label
                while (tempLabel.isNotEmpty() && textPaint.measureText(tempLabel + "...") > availableLabelWidth) {
                    tempLabel = tempLabel.dropLast(1)
                }
                label = tempLabel + "..."
            }
            
            canvas.drawText(label, startX + depthPadding, y, textPaint)
        }

        // --- PREVIEW RENDERING ---
        val previewLeft = splitX + 20f
        val previewTop = top + 80f
        val previewWidth = right - previewLeft - 20f
        val previewHeight = bottom - previewTop - 100f

        if (previewBitmap != null) {
            val bmp = previewBitmap!!
            val scale = min(previewWidth / bmp.width, previewHeight / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val dx = previewLeft + (previewWidth - dw) / 2f
            val dy = previewTop + (previewHeight - dh) / 2f
            canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), null)
        } else if (previewLayouts.isNotEmpty()) {
            var ty = previewTop
            previewLayouts.forEach { preview ->
                val layout = preview.layout
                val indent = preview.indent
                if (ty > bottom - 60f) return@forEach

                if (preview.image != null) {
                    val bmp = preview.image
                    val scale = previewWidth / bmp.width
                    val imgHeight = bmp.height * scale
                    canvas.drawBitmap(bmp, null, RectF(previewLeft, ty, previewLeft + previewWidth, ty + imgHeight), null)
                    ty += imgHeight + 10f
                } else if (preview.error != null) {
                    val oldSize = textPaint.textSize
                    textPaint.textSize = oldSize * 0.5f
                    textPaint.color = Color.RED
                    canvas.drawText(preview.error, previewLeft, ty + 20f, textPaint)
                    textPaint.textSize = oldSize
                    ty += 40f
                } else if (layout != null) {
                    canvas.save()
                    canvas.translate(previewLeft + indent, ty)
                    layout.draw(canvas)
                    canvas.restore()
                    ty += layout.height + 10f
                }
            }
        } else if (previewText != null) {
            val oldSize = textPaint.textSize
            val mdPaint = TextPaint(textPaint)
            mdPaint.textSize = oldSize * 0.6f
            mdPaint.color = menuTextColor
            
            val blockWidth = previewWidth.toInt().coerceAtLeast(1)
            val spanned = android.text.SpannableString(previewText)
            val layout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(spanned, 0, spanned.length, mdPaint, blockWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1.1f)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(spanned, mdPaint, blockWidth, Layout.Alignment.ALIGN_NORMAL, 1.1f, 2f, false)
            }

            canvas.save()
            canvas.clipRect(previewLeft, previewTop, right, bottom - 60f)
            canvas.translate(previewLeft, previewTop)
            layout.draw(canvas)
            canvas.restore()
        } else {
            textPaint.color = if (isEinkMode) Color.LTGRAY else Color.DKGRAY
            canvas.drawText("No Preview", previewLeft + 20f, h * 0.5f, textPaint)
        }

        // Legend at the bottom
        val baseSize = textPaint.textSize
        textPaint.color = if (isEinkMode) Color.BLACK else Color.rgb(200, 200, 200)
        textPaint.textSize = baseSize * 0.60f
        val legend = if (mode == Mode.SELECT) "[Enter] Load | [Shift+Enter] Buffer 1-8 | [Esc] Exit" 
                    else "[F] Folder | [C] Container | [D] Delete | [L] Link | [K] Link Remote | [U] Unlink | [R] Rename | [S] Save | [O] Load | [Esc] Exit"
        
        canvas.drawText(legend, startX, h * 0.92f, textPaint)
        textPaint.textSize = baseSize
    }
}
