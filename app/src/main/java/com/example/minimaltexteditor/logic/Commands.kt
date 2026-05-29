package com.example.minimaltexteditor.logic

sealed class Command {
    data class InsertText(val text: String) : Command()
    object DeleteBackward : Command()
    object DeleteForward : Command()
    object DeleteWord : Command()
    object DeleteLine : Command()
    data class MoveLeft(val selecting: Boolean = false) : Command()
    data class MoveRight(val selecting: Boolean = false) : Command()
    data class MoveUp(val selecting: Boolean = false) : Command()
    data class MoveDown(val selecting: Boolean = false) : Command()
    data class MoveWordLeft(val selecting: Boolean = false) : Command()
    data class MoveWordRight(val selecting: Boolean = false) : Command()
    data class MoveParagraphUp(val selecting: Boolean = false) : Command()
    data class MoveParagraphDown(val selecting: Boolean = false) : Command()
    data class MoveSentenceUp(val selecting: Boolean = false) : Command()
    data class MoveSentenceDown(val selecting: Boolean = false) : Command()
    data class MoveToLineStart(val selecting: Boolean = false) : Command()
    data class MoveToLineEnd(val selecting: Boolean = false) : Command()
    data class MoveToVisualLineStart(val selecting: Boolean = false) : Command()
    data class MoveToVisualLineEnd(val selecting: Boolean = false) : Command()
    data class MoveToStart(val selecting: Boolean = false) : Command()
    data class MoveToEnd(val selecting: Boolean = false) : Command()
    data class SwitchBuffer(val index: Int) : Command()
    object RequestClearBuffer : Command()
    object ClearBuffer : Command()
    object SaveBuffers : Command()
    object IncreaseFontSize : Command()
    object DecreaseFontSize : Command()
    object CycleTheme : Command()
    object CycleThemeBackwards : Command()
    
    // File Commands
    object SaveToFile : Command()
    object SaveAs : Command()
    object OpenFile : Command()
    object CloseFile : Command()
    object OpenPdfExport : Command()
    
    // Edit Commands
    object Copy : Command()
    object Cut : Command()
    object Paste : Command()
    object Undo : Command()
    object Redo : Command()
    object SelectAll : Command()
    object MoveToViewportCenter : Command()
    object StartFindCursor : Command()
    object StopFindCursor : Command()
    object OpenFind : Command()
    object OpenReplace : Command()
    object SearchToggleScope : Command()
    object SearchSwitchField : Command()
    object GlobalApplySettings : Command()
    object RequestExit : Command()
    object MinimizeApp : Command()
    object BrightnessUp : Command()
    object BrightnessDown : Command()
    
    // Outline Manager Commands
    object OpenOutlineSelection : Command()
    object OpenOutlineSetup : Command()
    object OutlineUp : Command()
    object OutlineDown : Command()
    object OutlineSelect : Command()
    object OutlineAddFolder : Command()
    object OutlineAddContainer : Command()
    object OutlineDelete : Command()
    object OutlineLinkFile : Command()
    object OutlineLinkRemote : Command()
    object OutlineUnlink : Command()
    object OutlineRename : Command()
    object SaveOutlineToFile : Command()
    object SaveOutlineAs : Command()
    object OpenOutlineFile : Command()
    object RequestClearOutline : Command()
    object ClearOutline : Command()
    
    // Split Screen Commands
    object OpenSplitMenu : Command()
    object SwapSplitFocus : Command()
    object ExitSplit : Command()
    object ToggleMarkdownRender : Command()
    object ToggleTypewriterMode : Command()
    object RecenterTypewriter : Command()
    object NextBuffer : Command()
    object PrevBuffer : Command()
    object OpenImageViewer : Command()
    object OpenImageSettings : Command()
    object ImageNext : Command()
    object ImagePrev : Command()
    object ToggleSlideshow : Command()
    
    // Overlay Commands
    object OpenBufferMenu : Command()
    object OpenThemeMenu : Command()
    object OpenMarginMenu : Command()
    object ToggleShowMargins : Command()
    object OpenFontMenu : Command()
    object OpenLineSpacingMenu : Command()
    data class AdjustVerticalOffset(val delta: Float) : Command()
    object ToggleStatus : Command()
    object OpenHelpMenu : Command()
    object OpenSettingsMenu : Command()
    object OpenWallpaperMenu : Command()
    object ShowHelp : Command()
    object ShowAbout : Command()
    object ShowKeymap : Command()
    object OpenCursorMenu : Command()
    object OverlayUp : Command()
    object OverlayDown : Command()
    data class OverlayJump(val index: Int) : Command()
    object OverlaySelect : Command()
    object OverlaySelectBackward : Command()
    object OverlayToggle : Command()
    object OverlayDelete : Command()
    object OverlayDeleteAll : Command()
    object OverlayCancel : Command()
    object OverlayToggleMarginPreview : Command()
    
    // Bookmark Commands
    object AddBookmark : Command()
    object OpenBookmarkMenu : Command()
    object JumpLastBookmark : Command()
    
    // Fold Commands
    object CollapseSelection : Command()
    object OpenFoldMenu : Command()
    
    // Scratchpad Commands
    object OpenScratchpad : Command()
    object FlushScratchpadSave : Command()
    object FlushScratchpadNoSave : Command()
    object OpenScratchpadSettings : Command()
    
    // Input Overlay Commands
    data class InputChar(val char: Char) : Command()
    object InputDelete : Command()
}

class CommandDispatcher(
    private val bufferManager: BufferManager,
    private val onBufferSwitched: (Int) -> Unit,
    private val onContentChanged: () -> Unit,
    private val onFontSizeChanged: (Boolean) -> Unit,
    private val onThemeChanged: (Boolean) -> Unit,
    private val onOverlayAction: (Command) -> Unit,
    private val onClipboardAction: (Command) -> Unit
) {
    fun dispatch(command: Command) {
        when (command) {
            is Command.InsertText -> {
                bufferManager.getCurrentBuffer().insert(command.text)
                onContentChanged()
            }
            is Command.DeleteBackward -> {
                bufferManager.getCurrentBuffer().deleteBackward()
                onContentChanged()
            }
            is Command.DeleteForward -> {
                bufferManager.getCurrentBuffer().deleteForward()
                onContentChanged()
            }
            is Command.DeleteWord -> {
                bufferManager.getCurrentBuffer().deleteWord()
                onContentChanged()
            }
            is Command.DeleteLine -> {
                bufferManager.getCurrentBuffer().deleteLine()
                onContentChanged()
            }
            is Command.MoveLeft -> {
                bufferManager.getCurrentBuffer().moveCursorLeft(command.selecting)
                onContentChanged()
            }
            is Command.MoveRight -> {
                bufferManager.getCurrentBuffer().moveCursorRight(command.selecting)
                onContentChanged()
            }
            is Command.MoveWordLeft -> {
                bufferManager.getCurrentBuffer().moveWordLeft(command.selecting)
                onContentChanged()
            }
            is Command.MoveWordRight -> {
                bufferManager.getCurrentBuffer().moveWordRight(command.selecting)
                onContentChanged()
            }
            is Command.MoveParagraphUp -> {
                bufferManager.getCurrentBuffer().moveParagraphUp(command.selecting)
                onContentChanged()
            }
            is Command.MoveParagraphDown -> {
                bufferManager.getCurrentBuffer().moveParagraphDown(command.selecting)
                onContentChanged()
            }
            is Command.MoveSentenceUp -> {
                bufferManager.getCurrentBuffer().moveSentenceUp(command.selecting)
                onContentChanged()
            }
            is Command.MoveSentenceDown -> {
                bufferManager.getCurrentBuffer().moveSentenceDown(command.selecting)
                onContentChanged()
            }
            is Command.MoveToLineStart -> {
                bufferManager.getCurrentBuffer().moveCursorToLineStart(command.selecting)
                onContentChanged()
            }
            is Command.MoveToLineEnd -> {
                bufferManager.getCurrentBuffer().moveCursorToLineEnd(command.selecting)
                onContentChanged()
            }
            is Command.MoveToVisualLineStart, is Command.MoveToVisualLineEnd -> {
                onOverlayAction(command)
            }
            is Command.MoveToStart -> {
                bufferManager.getCurrentBuffer().moveCursorToStart(command.selecting)
                onContentChanged()
            }
            is Command.MoveToEnd -> {
                bufferManager.getCurrentBuffer().moveCursorToEnd(command.selecting)
                onContentChanged()
            }
            is Command.SwitchBuffer -> {
                bufferManager.saveBuffers()
                if (bufferManager.switchBuffer(command.index)) {
                    onBufferSwitched(command.index + 1)
                    onContentChanged()
                }
            }
            Command.NextBuffer -> {
                bufferManager.saveBuffers()
                val nextIdx = (bufferManager.currentBufferIndex + 1) % 9
                if (bufferManager.switchBuffer(nextIdx)) {
                    onBufferSwitched(nextIdx + 1)
                    onContentChanged()
                }
            }
            Command.PrevBuffer -> {
                bufferManager.saveBuffers()
                val prevIdx = if (bufferManager.currentBufferIndex == 0) 8 else bufferManager.currentBufferIndex - 1
                if (bufferManager.switchBuffer(prevIdx)) {
                    onBufferSwitched(prevIdx + 1)
                    onContentChanged()
                }
            }
            is Command.ClearBuffer -> {
                bufferManager.getCurrentBuffer().clear()
                onContentChanged()
            }
            is Command.SaveBuffers -> {
                bufferManager.saveBuffers()
            }
            is Command.IncreaseFontSize -> {
                onFontSizeChanged(true)
            }
            is Command.DecreaseFontSize -> {
                onFontSizeChanged(false)
            }
            is Command.CycleTheme -> {
                onThemeChanged(true)
            }
            is Command.CycleThemeBackwards -> {
                onThemeChanged(false)
            }
            Command.Copy, Command.Cut, Command.Paste -> {
                onClipboardAction(command)
            }
            Command.Undo -> {
                bufferManager.getCurrentBuffer().undo()
                onContentChanged()
            }
            Command.Redo -> {
                bufferManager.getCurrentBuffer().redo()
                onContentChanged()
            }
            Command.SelectAll -> {
                bufferManager.getCurrentBuffer().selectAll()
                onContentChanged()
            }
            is Command.MoveUp,
            is Command.MoveDown,
            Command.MoveToViewportCenter,
            Command.StartFindCursor,
            Command.StopFindCursor,
            Command.OpenFind,
            Command.OpenReplace,
            Command.SearchToggleScope,
            Command.SearchSwitchField,
            Command.GlobalApplySettings,
            Command.RequestExit,
            Command.MinimizeApp,
            Command.BrightnessUp,
            Command.BrightnessDown,
            Command.RequestClearBuffer,
            Command.SaveToFile,
            Command.SaveAs,
            Command.OpenFile,
            Command.CloseFile,
            Command.OpenPdfExport,
            Command.OpenBufferMenu, 
            Command.OpenThemeMenu,
            Command.OpenMarginMenu,
            Command.ToggleShowMargins,
            Command.OpenFontMenu,
            Command.OpenLineSpacingMenu,
            is Command.AdjustVerticalOffset,
            Command.ToggleStatus,
            Command.OpenHelpMenu,
            Command.OpenSettingsMenu,
            Command.OpenWallpaperMenu,
            Command.ShowHelp,
            Command.ShowAbout,
            Command.ShowKeymap,
            Command.OpenCursorMenu,
            Command.OverlayUp, 
            Command.OverlayDown, 
            is Command.OverlayJump,
            Command.OverlaySelect, 
            Command.OverlaySelectBackward,
            Command.OverlayToggle,
            Command.OverlayDelete,
            Command.OverlayDeleteAll,
            Command.OverlayCancel,
            Command.OverlayToggleMarginPreview,
            Command.AddBookmark,
            Command.OpenBookmarkMenu,
            Command.JumpLastBookmark,
            Command.CollapseSelection,
            Command.OpenFoldMenu,
            Command.OpenOutlineSelection,
            Command.OpenImageViewer,
            Command.OpenImageSettings,
            Command.ImageNext,
            Command.ImagePrev,
            Command.ToggleSlideshow,
            Command.OpenOutlineSetup,
            Command.OutlineUp,
            Command.OutlineDown,
            Command.OutlineSelect,
            Command.OutlineAddFolder,
            Command.OutlineAddContainer,
            Command.OutlineDelete,
            Command.OutlineLinkFile,
            Command.OutlineLinkRemote,
            Command.OutlineUnlink,
            Command.OutlineRename,
            Command.SaveOutlineToFile,
            Command.SaveOutlineAs,
            Command.OpenOutlineFile,
            Command.RequestClearOutline,
            Command.ClearOutline,
            Command.OpenSplitMenu,
            Command.SwapSplitFocus,
            Command.ExitSplit,
            Command.ToggleMarkdownRender,
            Command.ToggleTypewriterMode,
            Command.RecenterTypewriter,
            Command.OpenScratchpad,
            Command.FlushScratchpadSave,
            Command.FlushScratchpadNoSave,
            Command.OpenScratchpadSettings,
            is Command.InputChar,
            Command.InputDelete -> {
                onOverlayAction(command)
            }
        }
    }
}
