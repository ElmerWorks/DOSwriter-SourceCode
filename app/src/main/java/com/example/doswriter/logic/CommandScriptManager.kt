package com.example.doswriter.logic

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

class CommandScriptManager(
    private val context: Context,
    private val dispatcher: CommandDispatcher,
    private val bufferManager: BufferManager
) {

    interface Listener {
        fun onScriptStatusChanged(instruction: String, progress: String, isPaused: Boolean)
        fun onScriptFinished()
    }

    private val handler = Handler(Looper.getMainLooper())
    private var scriptLines = listOf<String>()
    private var currentIndex = 0
    private var isRunning = false
    private var isStepMode = false
    private var lineDelay = 500L
    private var charDelay = 50L
    private var defaultPauseTime = 1000L
    
    private var typingText = ""
    private var typingIndex = 0
    private var isTyping = false
    private var pauseOverrideDelay: Long? = null
    private var isPresentationMode = false
    private val runtimeVariables = mutableMapOf<String, String>()

    var listener: Listener? = null
    var textPreprocessor: (String) -> String = { it }
    var rootProvider: (() -> List<String>)? = null

    private val commandMap = mutableMapOf<String, Command>()

    init {
        setupCommandMap()
    }

    private fun setupCommandMap() {
        // Standard Shortcuts & Keyboard Combinations
        commandMap["CTRL-B"] = Command.ToggleBold
        commandMap["CTRL-ALT-B"] = Command.OpenWorkspaceLayout
        commandMap["ALT-B"] = Command.OpenBufferMenu
        commandMap["CTRL-S"] = Command.SaveToFile
        commandMap["CTRL-SHIFT-S"] = Command.SaveAs
        commandMap["CTRL-O"] = Command.OpenFile
        commandMap["CTRL-SHIFT-O"] = Command.OpenRecentFiles
        commandMap["CTRL-N"] = Command.RequestClearBuffer
        commandMap["CTRL-W"] = Command.CloseFile
        commandMap["CTRL-ALT-W"] = Command.CloseAll
        commandMap["CTRL-P"] = Command.InstantPdf
        commandMap["ALT-P"] = Command.OpenPublishMenu
        commandMap["CTRL-Q"] = Command.OpenManuscriptMenu
        commandMap["CTRL-ALT-Q"] = Command.OpenManuscriptFilmstrip
        commandMap["CTRL-SHIFT-Q"] = Command.OpenManuscriptLayout
        commandMap["CTRL-PLUS"] = Command.IncreaseFontSize
        commandMap["CTRL-MINUS"] = Command.DecreaseFontSize
        commandMap["CTRL-SHIFT-1"] = Command.DecreaseFontSize
        commandMap["CTRL-SHIFT-2"] = Command.IncreaseFontSize
        commandMap["CTRL-M"] = Command.FastInsertMacro
        commandMap["CTRL-SHIFT-M"] = Command.PasteLastMacro
        commandMap["CTRL-ALT-M"] = Command.OpenMacroMenu
        commandMap["ALT-F"] = Command.OpenAppMenu(0) // File
        commandMap["ALT-E"] = Command.OpenAppMenu(1) // Edit
        commandMap["ALT-V"] = Command.OpenAppMenu(2) // View
        commandMap["ALT-W"] = Command.OpenAppMenu(3) // Workspace
        commandMap["ALT-T"] = Command.OpenAppMenu(4) // Tools
        commandMap["ALT-P"] = Command.OpenAppMenu(5) // Publish
        commandMap["ALT-S"] = Command.OpenAppMenu(6) // Settings
        commandMap["ALT-H"] = Command.OpenAppMenu(7) // Help
        commandMap["ALT-N"] = Command.SwapSplitFocus
        commandMap["ALT-J"] = Command.OpenPatternCollapsePicker
        commandMap["CTRL-J"] = Command.CollapseSelection
        commandMap["CTRL-SHIFT-J"] = Command.RecollapseLastExpansion
        commandMap["CTRL-ALT-J"] = Command.OpenPatternCollapseInput
        commandMap["ALT-C"] = Command.MoveToViewportCenter
        commandMap["CTRL-ALT-F"] = Command.StartFindCursor
        commandMap["CTRL-F"] = Command.OpenFind
        commandMap["CTRL-R"] = Command.OpenReplace
        commandMap["ALT-R"] = Command.ToggleMarkdownRender
        commandMap["ALT-T"] = Command.OpenAppMenu(4) // Tools
        commandMap["ALT-Y"] = Command.OpenTypewriterSettings
        commandMap["CTRL-Y"] = Command.ToggleTypewriterMode
        commandMap["CTRL-F11"] = Command.RecenterTypewriter
        commandMap["CTRL-H"] = Command.ToggleStatus
        commandMap["CTRL-ALT-H"] = Command.ShowHelpPopup
        commandMap["ALT-H"] = Command.OpenAppMenu(7) // Help
        commandMap["CTRL-L"] = Command.CycleLineHeight
        commandMap["ALT-L"] = Command.OpenLineSpacingMenu
        commandMap["ALT-M"] = Command.ToggleShowMargins
        commandMap["ALT-K"] = Command.OpenBookmarkMenu
        commandMap["CTRL-K"] = Command.AddBookmark
        commandMap["CTRL-SHIFT-C"] = Command.ClearBufferNoWatermark
        commandMap["CTRL-ALT-C"] = Command.ClearBufferNoWatermark
        commandMap["CTRL-I"] = Command.ToggleSlideshow
        commandMap["CTRL-SHIFT-I"] = Command.Screenshot
        commandMap["ALT-SHIFT-I"] = Command.OpenImageViewer
        commandMap["CTRL-ALT-I"] = Command.OpenImageViewer
        commandMap["ALT-D"] = Command.TypewriterDemo
        commandMap["ESC"] = Command.ExitSplit
        commandMap["ENTER"] = Command.InsertText("\n")
        commandMap["TAB"] = Command.InsertText("\t")
        
        // Navigation / Arrows
        commandMap["UP"] = Command.MoveUp()
        commandMap["DOWN"] = Command.MoveDown()
        commandMap["LEFT"] = Command.MoveLeft()
        commandMap["RIGHT"] = Command.MoveRight()
        commandMap["CTRL-UP"] = Command.MoveParagraphUp()
        commandMap["CTRL-DOWN"] = Command.MoveParagraphDown()
        commandMap["CTRL-LEFT"] = Command.MoveWordLeft()
        commandMap["CTRL-RIGHT"] = Command.MoveWordRight()
        commandMap["ALT-UP"] = Command.MoveSentenceUp()
        commandMap["ALT-DOWN"] = Command.MoveSentenceDown()
        commandMap["ALT-LEFT"] = Command.MoveToVisualLineStart()
        commandMap["ALT-RIGHT"] = Command.MoveToVisualLineEnd()
        commandMap["HOME"] = Command.MoveToStart()
        commandMap["END"] = Command.MoveToEnd()
        commandMap["CTRL-BACKSLASH"] = Command.MoveToStart()
        commandMap["CTRL-APOSTROPHE"] = Command.MoveToEnd()
        commandMap["CTRL-ALT-LEFT"] = Command.OpenSplitMenu
        commandMap["CTRL-ALT-RIGHT"] = Command.OpenSplitMenu
        commandMap["CTRL-ALT-UP"] = Command.MoveToStart()
        commandMap["CTRL-ALT-DOWN"] = Command.MoveToEnd()
        commandMap["CTRL-LEFT-BRACKET"] = Command.PageUp()
        commandMap["CTRL-RIGHT-BRACKET"] = Command.PageDown()
        commandMap["CTRL-COMMA"] = Command.AdjustVerticalOffset(-5f)
        commandMap["CTRL-PERIOD"] = Command.AdjustVerticalOffset(5f)
        
        // Selection variants
        commandMap["SHIFT-UP"] = Command.MoveUp(true)
        commandMap["SHIFT-DOWN"] = Command.MoveDown(true)
        commandMap["SHIFT-LEFT"] = Command.MoveLeft(true)
        commandMap["SHIFT-RIGHT"] = Command.MoveRight(true)

        // Explicit names for convenience
        commandMap["SAVE"] = Command.SaveToFile
        commandMap["UNDO"] = Command.Undo
        commandMap["REDO"] = Command.Redo
        commandMap["COPY"] = Command.Copy
        commandMap["CUT"] = Command.Cut
        commandMap["PASTE"] = Command.Paste
        commandMap["SELECT_ALL"] = Command.SelectAll
        commandMap["EXIT_SPLIT"] = Command.ExitSplit
        commandMap["SPLIT_SCREEN"] = Command.OpenSplitMenu
        commandMap["SPLIT-SCREEN"] = Command.OpenSplitMenu
        commandMap["MANUSCRIPT"] = Command.OpenManuscriptMenu
        commandMap["WORKSPACE"] = Command.OpenWorkspaceLayout
    }

    fun executeScript(script: String, baseUri: String? = null) {
        val resolved = resolveIncludes(script, baseUri)
        if (resolved == null) {
            stopScript()
            return
        }
        
        val preFilteredLines = resolved.lines()
        val filteredLines = mutableListOf<String>()
        
        // Pass 0: Strip [BYPASS] blocks
        var bypassActive = false
        for (line in preFilteredLines) {
            val trimmed = line.trim().uppercase()
            if (trimmed == "[BYPASS]") {
                bypassActive = true
                continue
            }
            if (trimmed == "[END-BYPASS]") {
                bypassActive = false
                continue
            }
            if (!bypassActive) {
                filteredLines.add(line)
            }
        }

        val macros = mutableMapOf<String, List<String>>()
        val initialProgramFlow = mutableListOf<String>()
        runtimeVariables.clear()
        
        // Pass 1: Extract Macros
        var i = 0
        while (i < filteredLines.size) {
            val line = filteredLines[i].trim()
            if (line.isEmpty() || line.startsWith("//")) {
                initialProgramFlow.add(filteredLines[i]) // Keep formatting/comments in flow
                i++
                continue
            }

            if (line.startsWith("[MACRO]", ignoreCase = true)) {
                val name = line.substringAfter("[MACRO]").trim().uppercase()
                Log.d("Script", "Defining Macro: $name")
                val body = mutableListOf<String>()
                i++
                while (i < filteredLines.size) {
                    val bodyLine = filteredLines[i].trim()
                    if (bodyLine.equals("[ENDMACRO]", ignoreCase = true)) break
                    body.add(filteredLines[i])
                    i++
                }
                if (name.isNotEmpty()) macros[name] = body
            } else {
                initialProgramFlow.add(filteredLines[i])
            }
            i++
        }

        // Pass 2: Macro Expansion (Flat flow)
        val expandedLines = mutableListOf<String>()
        for (line in initialProgramFlow) {
            val trimmed = line.trim()
            val upper = trimmed.uppercase()
            if (macros.containsKey(upper)) {
                Log.d("Script", "Expanding Macro: $upper")
                expandedLines.addAll(macros[upper]!!)
            } else {
                expandedLines.add(line)
            }
        }
        
        scriptLines = expandedLines

        currentIndex = 0
        isRunning = true
        isStepMode = false
        isTyping = false
        isPresentationMode = false
        lineDelay = 500L
        charDelay = 50L
        defaultPauseTime = 1000L
        executeNext()
    }

    private val scriptRunnable = Runnable { executeNext() }
    private val typingRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            if (typingIndex < typingText.length) {
                dispatcher.dispatch(Command.InsertText(typingText[typingIndex].toString()))
                typingIndex++
                updateUI("Typing: ${typingText.take(typingIndex)}...")
                handler.postDelayed(this, charDelay)
            } else {
                isTyping = false
                handler.postDelayed(scriptRunnable, lineDelay)
            }
        }
    }

    private fun updateUI(instruction: String) {
        val progress = "${currentIndex}/${scriptLines.size}"
        listener?.onScriptStatusChanged(instruction, progress, isStepMode)
    }

    private fun executeNext() {
        if (!isRunning) return
        
        if (currentIndex >= scriptLines.size) {
            stopScript()
            return
        }

        var line = scriptLines[currentIndex].trim()
        currentIndex++

        if (line.isEmpty()) {
            executeNext()
            return
        }

        // Handle Comments: '//' is always a comment.
        if (line.startsWith("//")) {
            executeNext()
            return
        }

        // 1. Handle Runtime Variable Assignment FIRST
        // Detects: [VAR] : val, [VAR]: val, [VAR]:val
        val colonIdx = line.indexOf(':')
        if (line.startsWith("[") && colonIdx != -1) {
            val potentialName = line.substring(0, colonIdx).trim()
            if (potentialName.endsWith("]") && !potentialName.contains(" ")) {
                val value = line.substring(colonIdx + 1).trim()
                Log.d("Script", "Assigning Variable: $potentialName = $value")
                runtimeVariables[potentialName.uppercase()] = value
                executeNext() 
                return
            }
        }

        // 2. Live Variable Substitution
        // We iterate through variables and replace placeholders in the current line
        runtimeVariables.forEach { (name, value) ->
            line = line.replace(name, value, ignoreCase = true)
        }

        processLine(line)
        Log.d("Script", "Executed: $line")

        if (!isStepMode && !isTyping) {
            val delay = pauseOverrideDelay ?: lineDelay
            pauseOverrideDelay = null
            handler.postDelayed(scriptRunnable, delay)
        }
    }

    fun executeSingleLine(line: String) {
        if (line.isBlank()) return
        Log.d("Script", "Executing Single Line: $line")
        processLine(line)
    }

    internal fun processLine(line: String) {
        val trimmed = line.trim()

        // --- NEW: Markdown-style Image Command for absolute paths ---
        // Detects ![desc](uri) and treats it as an image open command
        if (trimmed.startsWith("![") && trimmed.contains("](") && trimmed.endsWith(")")) {
            val uri = trimmed.substringAfter("](").substringBeforeLast(")")
            if (uri.isNotEmpty()) {
                dispatcher.dispatch(Command.LoadNamedImage(uri))
                return
            }
        }

        if (trimmed == "---" && isPresentationMode) {
            dispatcher.dispatch(Command.ClearBufferNoWatermark)
            isStepMode = true
            updateUI("SLIDE BREAK (F8 to Next)")
            return
        }

        // In Presentation Mode, treat non-commands as content, BUT ignore comments
        if (!trimmed.startsWith("[") && isPresentationMode) {
            if (trimmed.isNotEmpty() && !trimmed.startsWith("//")) {
                // NEW: Skip lines that result in empty Markdown or NULL content
                val contentPart = trimmed.dropWhile { it == '#' || it == '>' || it.isWhitespace() }
                if (contentPart.isEmpty() || contentPart.equals("NULL", ignoreCase = true)) {
                    Log.d("Script", "Bypassing empty/NULL Markdown line: $trimmed")
                    return
                }

                val content = textPreprocessor(trimmed)
                dispatcher.dispatch(Command.InsertText(content + "\n"))
            }
            return
        }

        val parts = line.split(" ", limit = 2)
        val action = parts[0].uppercase()
        val arg = if (parts.size > 1) parts[1].trim() else ""

        updateUI(line)

        when (action) {
            "[LINE-DELAY]" -> {
                lineDelay = arg.toLongOrNull() ?: 500L
            }
            "[DELAY]" -> {
                lineDelay = arg.toLongOrNull() ?: 500L
            }
            "[PRESENTATION]" -> {
                isPresentationMode = true
                dispatcher.dispatch(Command.ForceMarkdownRender(true))
                updateUI("Presentation Mode ON")
            }
            "[CHAR_DELAY]" -> {
                charDelay = arg.toLongOrNull() ?: 50L
            }
            "[CMD]" -> {
                val normalizedArg = arg.uppercase().replace(" ", "").replace("+", "-")
                val cmd = commandMap[normalizedArg]
                if (cmd != null) {
                    dispatcher.dispatch(cmd)
                } else {
                    Log.w("Script", "Unknown command identifier: $arg (normalized to $normalizedArg)")
                }
            }
            "[TYPE]" -> {
                var content = arg.replace("\\n", "\n")
                val internalClip = bufferManager.internalClipboard ?: ""
                content = content.replace("{CLIPBOARD}", internalClip)
                
                // Preprocess for TELEX if requested
                content = textPreprocessor(content)
                
                typingText = content + "\n"
                typingIndex = 0
                isTyping = true
                handler.post(typingRunnable)
            }
            "[TYPE_CLIPBOARD]" -> {
                val internalClip = bufferManager.internalClipboard ?: ""
                typingText = textPreprocessor(internalClip) + "\n"
                typingIndex = 0
                isTyping = true
                handler.post(typingRunnable)
            }
            "[PASTE]" -> {
                var content = arg.replace("\\n", "\n")
                val internalClip = bufferManager.internalClipboard ?: ""
                content = content.replace("{CLIPBOARD}", internalClip)
                content = textPreprocessor(content)
                dispatcher.dispatch(Command.InsertText(content + "\n"))
            }
            "[PAUSETIME]" -> {
                val seconds = arg.toLongOrNull()?.coerceIn(1, 60) ?: 1L
                defaultPauseTime = seconds * 1000L
            }
            "[PAUSE]" -> {
                val seconds = if (arg.isEmpty()) null else arg.toLongOrNull()?.coerceIn(1, 60)
                pauseOverrideDelay = if (seconds != null) seconds * 1000L else defaultPauseTime
            }
            "[CR]" -> {
                dispatcher.dispatch(Command.InsertText("\n"))
            }
            "[BR]" -> {
                dispatcher.dispatch(Command.InsertText("\n"))
            }
            "[IMAGE]" -> {
                dispatcher.dispatch(Command.LoadNamedImage(arg))
            }
            "[IMAGE>BUF]" -> {
                val pts = arg.split(",", limit = 2)
                if (pts.size == 2) {
                    val bufIdx = (pts[0].trim().toIntOrNull()?.minus(1)) ?: -1
                    val imgName = pts[1].trim()
                    if (bufIdx in 0..7) {
                        dispatcher.dispatch(Command.LoadNamedImageToBuffer(imgName, bufIdx))
                    } else {
                        Log.w("Script", "Invalid buffer index for [IMAGE>BUF]: ${pts[0]}")
                    }
                } else {
                    Log.w("Script", "[IMAGE>BUF] requires 'BufferNum, ImageName'")
                }
            }
            "[CLOSE-IMAGE]" -> {
                val idx = arg.toIntOrNull()?.minus(1)
                if (idx != null && idx in 0..7) {
                    bufferManager.getBufferAt(idx).imageUri = null
                } else {
                    bufferManager.getCurrentBuffer().imageUri = null
                }
                dispatcher.dispatch(Command.OverlayCancel) // Trigger redraw
            }
            "[STEP]" -> {
                isStepMode = true
                updateUI("PAUSED AT STEP")
            }
            "[GOTO_BUFFER]" -> {
                val idx = arg.toIntOrNull()?.minus(1) ?: 0
                if (idx in 0..7) dispatcher.dispatch(Command.SwitchBuffer(idx))
            }
            "[HIDE-CURSOR]" -> {
                dispatcher.dispatch(Command.HideCursor)
            }
            "[SHOW-CURSOR]" -> {
                dispatcher.dispatch(Command.ShowCursor)
            }
            "[HIDE-EMPTYBUFFER-MSG]" -> {
                dispatcher.dispatch(Command.HideEmptyBufferMsg)
            }
            "[SHOW-EMPTYBUFFER-MSG]" -> {
                dispatcher.dispatch(Command.ShowEmptyBufferMsg)
            }
            "[CLEAR]" -> {
                val idx = arg.toIntOrNull()?.minus(1) ?: 0
                if (idx in 0..7) dispatcher.dispatch(Command.ClearBufferAtIndex(idx))
            }
            "[NOTIFY]" -> {
                updateUI("NOTIFY: $arg")
            }
            "[BEEP]" -> {
                dispatcher.dispatch(Command.Beep)
            }
            "[FOCUS-LEFT]" -> {
                dispatcher.dispatch(Command.FocusPanel(0))
            }
            "[FOCUS-TOP]" -> {
                dispatcher.dispatch(Command.FocusPanel(0))
            }
            "[FOCUS-RIGHT]" -> {
                dispatcher.dispatch(Command.FocusPanel(1))
            }
            "[FOCUS-BOTTOM]" -> {
                dispatcher.dispatch(Command.FocusPanel(1))
            }
            "[SPLIT_SCREEN_V]" -> {
                val otherBuf = arg.toIntOrNull()?.minus(1) ?: 0
                if (otherBuf in 0..7) {
                    dispatcher.dispatch(Command.SplitScreen(0, otherBuf))
                }
            }
            "[SPLIT-SCREEN-V]" -> {
                val otherBuf = arg.toIntOrNull()?.minus(1) ?: 0
                if (otherBuf in 0..7) {
                    dispatcher.dispatch(Command.SplitScreen(0, otherBuf))
                }
            }
            "[SPLIT_SCREEN_H]" -> {
                val otherBuf = arg.toIntOrNull()?.minus(1) ?: 0
                if (otherBuf in 0..7) {
                    dispatcher.dispatch(Command.SplitScreen(1, otherBuf))
                }
            }
            "[SPLIT-SCREEN-H]" -> {
                val otherBuf = arg.toIntOrNull()?.minus(1) ?: 0
                if (otherBuf in 0..7) {
                    dispatcher.dispatch(Command.SplitScreen(1, otherBuf))
                }
            }
            else -> {
                // Fallback: Check if the action itself is a command identifier (e.g. CTRL-W)
                val cmd = commandMap[action]
                if (cmd != null) {
                    dispatcher.dispatch(cmd)
                } else if (action.startsWith("[") && action.endsWith("]")) {
                    Log.w("Script", "Unrecognized directive or undefined variable: $action")
                }
            }
        }
    }

    fun step() {
        if (isRunning && isStepMode) {
            isStepMode = false
            executeNext()
        }
    }

    fun stopScript() {
        isRunning = false
        isStepMode = false
        isTyping = false
        handler.removeCallbacks(scriptRunnable)
        handler.removeCallbacks(typingRunnable)
        listener?.onScriptFinished()
    }

    fun isScriptRunning() = isRunning
    fun isAtStep() = isStepMode

    private fun resolveIncludes(script: String, baseUri: String?, depth: Int = 0): String? {
        if (depth > 5) return script // Safety limit

        val lines = script.lines()
        val result = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[INCLUDE]", ignoreCase = true)) {
                var fileName = trimmed.substringAfter("[INCLUDE]").trim()
                if (fileName.startsWith(":")) fileName = fileName.substring(1).trim()
                
                if (fileName.isNotEmpty()) {
                    Log.d("Script", "Resolving include: $fileName")
                    val includeContent = readIncludeSource(fileName, baseUri)
                    if (includeContent != null) {
                        val nested = resolveIncludes(includeContent, baseUri, depth + 1)
                        if (nested == null) return null
                        result.append(nested)
                        result.append("\n")
                        continue
                    } else {
                        val msg = "ERROR: Could not resolve [INCLUDE] $fileName in script folder or assets."
                        Log.e("Script", msg)
                        listener?.onScriptStatusChanged(msg, "Error", false)
                        return null // Stop interpretation
                    }
                }
            }
            result.append(line).append("\n")
        }

        return result.toString()
    }

    private fun readIncludeSource(fileName: String, baseUri: String?): String? {
        if (baseUri != null) {
            try {
                val uri = Uri.parse(baseUri)
                if (uri.scheme == "content") {
                    val docFile = DocumentFile.fromSingleUri(context, uri)
                    val parent = docFile?.parentFile
                    if (parent != null) {
                        val target = parent.findFile(fileName)
                        if (target != null && target.exists()) {
                            return context.contentResolver.openInputStream(target.uri)?.bufferedReader()?.use { it.readText() }
                        }
                    }
                } else if (uri.scheme == "file") {
                    val file = File(uri.path!!)
                    val sibling = File(file.parentFile, fileName)
                    if (sibling.exists()) {
                        Log.d("Script", "Found sibling include: ${sibling.absolutePath}")
                        return sibling.readText()
                    }
                }
            } catch (e: Exception) {
                Log.w("Script", "Include resolution failed for $fileName: ${e.message}")
            }
        }
        
        // 2. Try the Script Image Folder as a fallback root
        bufferManager.scriptImageFolderUri?.let { folderUri ->
            try {
                val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                val target = folder?.findFile(fileName)
                if (target != null && target.exists()) {
                    return context.contentResolver.openInputStream(target.uri)?.bufferedReader()?.use { it.readText() }
                }
            } catch (e: Exception) {}
        }

        // 3. NEW: Try all linked working folders via rootProvider
        rootProvider?.invoke()?.forEach { rootUri ->
            try {
                val folder = DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
                val target = folder?.findFile(fileName)
                if (target != null && target.exists()) {
                    return context.contentResolver.openInputStream(target.uri)?.bufferedReader()?.use { it.readText() }
                }
            } catch (e: Exception) {}
        }

        // 4. Fallback to Assets (for example scripts)
        try {
            return context.assets.open("new-features/scripts/$fileName").bufferedReader().use { it.readText() }
        } catch (e: Exception) {}

        return null
    }

    private fun readScriptSource(fileName: String, baseUri: String?): String? {
        // 1. Primary: Try explicitly in the Script Image Folder (The logical "Scripts Root")
        bufferManager.scriptImageFolderUri?.let { folderUri ->
            try {
                val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                val target = folder?.findFile(fileName)
                if (target != null && target.exists()) {
                    return context.contentResolver.openInputStream(target.uri)?.bufferedReader()?.use { it.readText() }
                }
            } catch (e: Exception) {
                Log.w("Script", "Linked folder resolution failed for $fileName")
            }
        }

        // 1b. Try all linked working folders via rootProvider
        rootProvider?.invoke()?.forEach { rootUri ->
            try {
                val folder = DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
                val target = folder?.findFile(fileName)
                if (target != null && target.exists()) {
                    return context.contentResolver.openInputStream(target.uri)?.bufferedReader()?.use { it.readText() }
                }
            } catch (e: Exception) {}
        }

        // 2. Secondary: Try resolving relative to the script's own folder (Works for 'file://' schemes)
        if (baseUri != null) {
            try {
                val uri = Uri.parse(baseUri)
                if (uri.scheme == "file") {
                    val file = File(uri.path!!)
                    val sibling = File(file.parentFile, fileName)
                    if (sibling.exists()) return sibling.readText()
                }
            } catch (e: Exception) {
                Log.w("Script", "Local resolution failed for $fileName: ${e.message}")
            }
        }

        // 3. Fallback to Assets (for example scripts)
        return try {
            context.assets.open("new-features/scripts/$fileName").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            // 4. Fallback to default local data dir
            try {
                val file = File(context.getExternalFilesDir(null), fileName)
                if (file.exists()) file.readText() else null
            } catch (e2: Exception) {
                null
            }
        }
    }
}
