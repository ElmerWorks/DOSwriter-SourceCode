package com.example.doswriter.editor

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.documentfile.provider.DocumentFile
import com.example.doswriter.logic.BufferManager
import java.io.File
import kotlin.math.max
import kotlin.math.min

class FileBrowserOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { OPEN, SAVE_AS, BROWSE }
    private var mode = Mode.BROWSE
    
    private var rootDir: DocumentFile? = null
    private var currentDir: DocumentFile? = null
    private val navigationStack = mutableListOf<DocumentFile>()
    private var files = mutableListOf<DocumentFile>()
    
    private data class FileItem(val file: DocumentFile?, val isUp: Boolean = false)
    private val displayItems = mutableListOf<FileItem>()

    private var selectedIndex = 0
    private var scrollOffset = 0
    
    private var onFileAction: ((DocumentFile, Mode, String?) -> Unit)? = null
    private var onDeleteRequested: ((DocumentFile) -> Unit)? = null
    private var onNewFolderRequested: ((DocumentFile) -> Unit)? = null
    
    private var sortMode = 0 // 0: NAME, 1: MOST RECENT
    private var fileNameInput = StringBuilder()
    private var inputCursorPos = 0
    private var showInput = false
    private var isInputFocused = true

    private val bgPaint = Paint().apply { color = Color.rgb(25, 25, 25); style = Paint.Style.FILL }
    private val borderPaint = Paint().apply { color = Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 4f }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 38f; isAntiAlias = true }
    private val selectionPaint = Paint().apply { color = Color.rgb(0, 255, 0); style = Paint.Style.FILL }
    
    private var itemHeight = 70f
    private var isEinkMode = false
    private var menuTextColor = Color.WHITE

    private var previewBitmap: Bitmap? = null
    private var previewText: String? = null
    private val previewHandler = Handler(Looper.getMainLooper())
    private val previewRunnable = Runnable { updatePreview() }
    private var lastPreviewUri: Uri? = null

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

    fun show(mode: Mode, initialDirUri: String?, rootUri: String? = null, defaultName: String? = null, sortMode: Int = 0, onAction: (DocumentFile, Mode, String?) -> Unit, onDelete: ((DocumentFile) -> Unit)? = null, onNewFolder: ((DocumentFile) -> Unit)? = null) {
        this.mode = mode
        this.onFileAction = onAction
        this.onDeleteRequested = onDelete
        this.onNewFolderRequested = onNewFolder
        this.showInput = mode == Mode.SAVE_AS
        this.sortMode = sortMode
        
        // Start with focus on the Navigation Tree to allow immediate browsing/subfolder selection
        this.isInputFocused = false 

        this.fileNameInput.setLength(0)
        defaultName?.let { fileNameInput.append(it) }
        this.inputCursorPos = fileNameInput.length
        
        navigationStack.clear()
        
        if (rootUri != null) {
            try {
                rootDir = DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
            } catch (e: Exception) {}
        } else {
            rootDir = null
        }

        if (initialDirUri != null) {
            try {
                currentDir = DocumentFile.fromTreeUri(context, Uri.parse(initialDirUri))
            } catch (e: Exception) {
                Log.e("FileBrowser", "Failed to load initial dir", e)
            }
        }
        
        refreshFileList()
        visibility = VISIBLE
        invalidate()
    }

    fun refresh() {
        refreshFileList()
        invalidate()
    }

    private fun refreshFileList() {
        displayItems.clear()
        files.clear()
        
        // Show [PARENT FOLDER] if we have history OR if we are not at the logical root
        val isAtRoot = rootDir != null && currentDir?.uri == rootDir?.uri
        if (navigationStack.isNotEmpty() || currentDir?.parentFile != null || !isAtRoot) {
            displayItems.add(FileItem(null, true))
        }

        currentDir?.listFiles()?.let { list ->
            val sorted = if (sortMode == 0) {
                // Sort by Name (Folders first)
                list.sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() }))
            } else {
                // Sort by Most Recent (Folders first)
                list.sortedWith(compareBy({ !it.isDirectory }, { -it.lastModified() }))
            }

            sorted.forEach { 
                displayItems.add(FileItem(it))
                files.add(it)
            }
        }
        
        selectedIndex = if (displayItems.isNotEmpty()) 0 else -1
        scrollOffset = 0
        schedulePreview()
    }

    fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        if (visibility != VISIBLE) return false

        if (showInput) {
            when (keyCode) {
                KeyEvent.KEYCODE_TAB -> {
                    isInputFocused = !isInputFocused
                    invalidate()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (isInputFocused) {
                        inputCursorPos = max(0, inputCursorPos - 1)
                        invalidate()
                        return true
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (isInputFocused) {
                        inputCursorPos = min(fileNameInput.length, inputCursorPos + 1)
                        invalidate()
                        return true
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> { moveUp(); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { moveDown(); return true }
                KeyEvent.KEYCODE_ENTER -> {
                    confirmAction()
                    return true
                }
                KeyEvent.KEYCODE_DEL -> {
                    // Backspace: deletes if focused on box, otherwise navigates UP
                    if (isInputFocused) {
                        if (inputCursorPos > 0) {
                            fileNameInput.deleteCharAt(inputCursorPos - 1)
                            inputCursorPos--
                            invalidate()
                        }
                    } else if (!isInputFocused) {
                        goUp()
                    }
                    return true
                }
                KeyEvent.KEYCODE_FORWARD_DEL -> {
                    // Forward delete always targets the background list item
                    if (selectedIndex >= 0 && selectedIndex < displayItems.size) {
                        val item = displayItems[selectedIndex]
                        if (!item.isUp && item.file != null) {
                            onDeleteRequested?.invoke(item.file)
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_ESCAPE -> {
                    hide()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> return true
            }
            
            // Text input: ONLY allowed if focuses on Name box
            if (isInputFocused) {
                val unicode = event.unicodeChar
                // Block control characters from being appended (Tab=9, Enter=10/13)
                if (unicode >= 32) { 
                    fileNameInput.insert(inputCursorPos, unicode.toChar())
                    inputCursorPos++
                    invalidate()
                    return true
                }
            }
            
            if (event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_N) {
                currentDir?.let { onNewFolderRequested?.invoke(it) }
                return true
            }

            return true // Consume ALL other keys in input mode
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { moveUp(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { moveDown(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> return true // Consume but do nothing
            KeyEvent.KEYCODE_ENTER -> { confirmAction(); return true }
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_BACK -> {
                if (selectedIndex >= 0 && selectedIndex < displayItems.size) {
                    val item = displayItems[selectedIndex]
                    if (!item.isUp && item.file != null) {
                        onDeleteRequested?.invoke(item.file)
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_ESCAPE -> { hide(); return true }
        }
        
        if (event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_N) {
            currentDir?.let { onNewFolderRequested?.invoke(it) }
            return true
        }
        
        // If we're not in input mode but browser is visible, consume alpha keys to prevent typing behind the overlay
        val unicode = event.unicodeChar
        if (unicode != 0) return true

        return false
    }

    private fun moveUp() {
        if (selectedIndex > 0) {
            selectedIndex--
            updateScroll()
            schedulePreview()
            invalidate()
        }
    }

    private fun moveDown() {
        if (selectedIndex < displayItems.size - 1) {
            selectedIndex++
            updateScroll()
            schedulePreview()
            invalidate()
        }
    }

    private fun goUp() {
        if (navigationStack.isNotEmpty()) {
            currentDir = navigationStack.removeAt(navigationStack.size - 1)
        } else {
            val parent = currentDir?.parentFile
            if (parent != null) {
                currentDir = parent
            } else if (rootDir != null && currentDir?.uri != rootDir?.uri) {
                // Last resort: jump back to root if we are stuck in a retained subfolder
                currentDir = rootDir
            } else {
                return
            }
        }
        refreshFileList()
        invalidate()
    }

    private fun confirmAction() {
        if (mode == Mode.SAVE_AS && isInputFocused) {
            // BRANCH 1: Focus is on the Name Box -> Perform the SAVE operation
            // Allow confirmation even if directory is empty (selectedIndex == -1)
            if (fileNameInput.isNotEmpty() && currentDir != null) {
                onFileAction?.invoke(currentDir!!, Mode.SAVE_AS, fileNameInput.toString())
                hide()
            }
            return
        }

        if (selectedIndex < 0 || selectedIndex >= displayItems.size) return
        val item = displayItems[selectedIndex]

        if (mode == Mode.SAVE_AS) {
            // BRANCH 2: Focus is on the Folder Tree -> Navigation or Filename Selection
            if (item.isUp) {
                goUp()
            } else {
                val file = item.file
                if (file?.isDirectory == true) {
                    // Navigate into subfolder
                    currentDir?.let { navigationStack.add(it) }
                    currentDir = file
                    refreshFileList()
                    invalidate()
                } else if (file != null) {
                    // Highlighted a file while focused on list: copy name and switch focus
                    fileNameInput.setLength(0)
                    fileNameInput.append(file.name ?: "")
                    inputCursorPos = fileNameInput.length
                    isInputFocused = true
                    invalidate()
                }
            }
            return
        }

        // Standard BROWSE/OPEN logic
        if (item.isUp) {
            goUp()
            return
        }

        val file = item.file ?: return
        if (file.isDirectory) {
            currentDir?.let { navigationStack.add(it) }
            currentDir = file
            refreshFileList()
            invalidate()
            return
        }

        onFileAction?.invoke(file, mode, null)
        hide()
    }

    private fun schedulePreview() {
        previewHandler.removeCallbacks(previewRunnable)
        previewHandler.postDelayed(previewRunnable, 500)
    }

    private fun updatePreview() {
        if (selectedIndex < 0 || selectedIndex >= displayItems.size) return
        val item = displayItems[selectedIndex]
        val file = item.file
        if (item.isUp || file == null || file.isDirectory || file.uri == lastPreviewUri) {
            if (item.isUp || file == null || file.isDirectory) clearPreview()
            return
        }

        lastPreviewUri = file.uri
        clearPreview()

        try {
            val name = file.name?.lowercase() ?: ""
            val type = context.contentResolver.getType(file.uri) ?: ""

            if (type.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif")) {
                val stream = context.contentResolver.openInputStream(file.uri)
                previewBitmap = BitmapFactory.decodeStream(stream)
            } else if (type == "application/pdf" || name.endsWith(".pdf")) {
                val fd = context.contentResolver.openFileDescriptor(file.uri, "r")
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
                val text = context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
                previewText = text?.take(1000)
            }
        } catch (e: Exception) {
            previewText = "Preview Error: ${e.message}"
        }
        invalidate()
    }

    private fun clearPreview() {
        previewBitmap?.recycle()
        previewBitmap = null
        previewText = null
    }

    private fun updateScroll() {
        val maxVisible = calculateMaxVisible()
        if (maxVisible <= 0) return
        if (selectedIndex < scrollOffset) scrollOffset = selectedIndex
        else if (selectedIndex >= scrollOffset + maxVisible) scrollOffset = selectedIndex - maxVisible + 1
        scrollOffset = scrollOffset.coerceIn(0, max(0, displayItems.size - maxVisible))
    }

    private fun calculateMaxVisible(): Int {
        val availableHeight = height * 0.65f
        return (availableHeight / itemHeight).toInt()
    }

    fun hide() {
        visibility = GONE
        clearPreview()
    }

    private fun getRelativePath(): String {
        val root = rootDir ?: return mode.name
        val current = currentDir ?: return root.name ?: "Root"
        
        val rootName = root.name ?: "Root"
        if (root.uri == current.uri) return rootName

        val pathParts = mutableListOf<String>()
        
        // We use navigationStack to build the breadcrumb path
        // If the browser was opened directly in a subfolder, navigationStack might be empty
        // but we still want the path from root.
        
        var foundRootInStack = false
        for (dir in navigationStack) {
            if (foundRootInStack) {
                pathParts.add(dir.name ?: "")
            } else if (dir.uri == root.uri) {
                foundRootInStack = true
            }
        }
        
        // Always add current dir name if it's not the root
        pathParts.add(current.name ?: "")

        val subPath = pathParts.filter { it.isNotEmpty() }.joinToString("\\")
        return "$rootName\\$subPath"
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val left = w * 0.05f
        val top = h * 0.05f
        val right = w * 0.95f
        val bottom = h * 0.95f
        
        canvas.drawRect(left, top, right, bottom, bgPaint)
        canvas.drawRect(left, top, right, bottom, borderPaint)

        val splitX = w * 0.55f
        canvas.drawLine(splitX, top, splitX, bottom, borderPaint)

        val startX = w * 0.08f
        val startY = h * 0.16f
        val maxVisible = calculateMaxVisible()

        // Title Area: Display Folder Path
        textPaint.color = menuTextColor
        textPaint.isFakeBoldText = true
        
        val modeStr = when(mode) {
            Mode.OPEN -> "[OPEN] "
            Mode.SAVE_AS -> "[SAVE] "
            Mode.BROWSE -> "[FILE] "
        }
        val folderPath = getRelativePath()
        val title = modeStr + folderPath
        
        // Shrink font if path is too long
        val originalTitleSize = textPaint.textSize
        var titleSize = originalTitleSize
        while (textPaint.measureText(title) > (right - startX - 20f) && titleSize > 24f) {
            titleSize -= 2f
            textPaint.textSize = titleSize
        }
        
        canvas.drawText(title, startX, h * 0.10f, textPaint)
        textPaint.textSize = originalTitleSize
        textPaint.isFakeBoldText = false

        // File List
        if (displayItems.isEmpty()) {
            canvas.drawText("(empty folder)", startX, h * 0.5f, textPaint)
        } else {
            for (i in 0 until maxVisible) {
                val idx = scrollOffset + i
                if (idx >= displayItems.size) break
                val item = displayItems[idx]
                val y = startY + (i * itemHeight)

                if (idx == selectedIndex) {
                    val highlightColor = if (showInput && isInputFocused) Color.rgb(40, 40, 40) else selectionPaint.color
                    val oldColor = selectionPaint.color
                    selectionPaint.color = highlightColor
                    canvas.drawRect(w * 0.05f, y - (itemHeight * 0.65f), splitX, y + (itemHeight * 0.3f), selectionPaint)
                    selectionPaint.color = oldColor
                    textPaint.color = if (showInput && isInputFocused) menuTextColor else Color.BLACK
                } else {
                    textPaint.color = if (item.isUp) Color.YELLOW else if (item.file?.isDirectory == true) (if (isEinkMode) Color.rgb(0, 80, 150) else Color.CYAN) else menuTextColor
                }
                
                val label = when {
                    item.isUp -> "⤴ [PARENT FOLDER]"
                    item.file?.isDirectory == true -> "📁 " + item.file.name
                    else -> "📄 " + (item.file?.name ?: "unnamed")
                }
                canvas.drawText(label, startX, y, textPaint)
            }
        }

        // Input Field for Save As
        if (showInput) {
            val inputY = bottom - 100f
            // Use selection color (Green) background when focused for Black text legibility
            val fillPaint = if (isInputFocused) selectionPaint else bgPaint
            canvas.drawRect(left + 20f, inputY - 60f, splitX - 20f, inputY + 20f, fillPaint)
            
            val borderCol = if (isInputFocused) selectionPaint.color else borderPaint.color
            val oldBCol = borderPaint.color
            val oldWidth = borderPaint.strokeWidth
            borderPaint.color = borderCol
            if (isInputFocused) borderPaint.strokeWidth = 10f // Thicker line for focus
            canvas.drawRect(left + 20f, inputY - 60f, splitX - 20f, inputY + 20f, borderPaint)
            borderPaint.color = oldBCol
            borderPaint.strokeWidth = oldWidth

            textPaint.color = if (isInputFocused) Color.BLACK else menuTextColor
            val prefix = if (isInputFocused) "Name (Focused): " else "Name: "
            if (isInputFocused) textPaint.isFakeBoldText = true
            
            val prefixX = left + 40f
            canvas.drawText(prefix, prefixX, inputY - 10f, textPaint)
            
            val nameStr = fileNameInput.toString()
            val nameX = prefixX + textPaint.measureText(prefix)
            
            // Draw the full filename first so character spacing is natural and never shifts
            canvas.drawText(nameStr, nameX, inputY - 10f, textPaint)
            
            if (isInputFocused) {
                // Overlay the underscore cursor at the correct character position
                val cursorX = nameX + textPaint.measureText(nameStr.take(inputCursorPos))
                canvas.drawText("_", cursorX, inputY - 10f, textPaint)
            }

            textPaint.isFakeBoldText = false
        }

        // Preview
        val previewLeft = splitX + 20f
        val previewTop = top + 80f
        val previewWidth = right - previewLeft - 20f
        val previewHeight = bottom - previewTop - 100f

        if (previewBitmap != null) {
            val bmp = previewBitmap!!
            val scale = min(previewWidth / bmp.width, previewHeight / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            canvas.drawBitmap(bmp, null, RectF(previewLeft + (previewWidth - dw)/2f, previewTop + (previewHeight - dh)/2f, previewLeft + (previewWidth + dw)/2f, previewTop + (previewHeight + dh)/2f), null)
        } else if (previewText != null) {
            val oldSize = textPaint.textSize
            textPaint.textSize = oldSize * 0.6f
            val layout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(previewText!!, 0, previewText!!.length, TextPaint(textPaint), previewWidth.toInt()).build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(previewText!!, TextPaint(textPaint), previewWidth.toInt(), Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, false)
            }
            canvas.save()
            canvas.clipRect(previewLeft, previewTop, right, bottom - 100f)
            canvas.translate(previewLeft, previewTop)
            layout.draw(canvas)
            canvas.restore()
            textPaint.textSize = oldSize
        }

        // Legend
        val baseSize = textPaint.textSize
        textPaint.textSize = baseSize * 0.6f
        textPaint.color = if (isEinkMode) Color.BLACK else Color.rgb(200, 200, 200)
        
        val legend = if (showInput) {
            if (isInputFocused) "[Enter] SAVE FILE | [Tab] Navigate Tree | [Del] Bspace"
            else "[Enter] Open Folder | [Tab] Edit Name | [Del] Up"
        } else {
            "[Enter] Open/Enter | [Del] Delete | [Ctrl+N] New Folder | [Esc] Exit"
        }

        canvas.drawText(legend, startX, bottom - 30f, textPaint)
        textPaint.textSize = baseSize // RESTORE SIZE
    }
}
