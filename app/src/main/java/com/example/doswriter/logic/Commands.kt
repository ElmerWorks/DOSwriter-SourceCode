package com.example.doswriter.logic

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
    data class MoveToParagraphStart(val selecting: Boolean = false) : Command()
    data class MoveToParagraphEnd(val selecting: Boolean = false) : Command()
    data class MoveSentenceUp(val selecting: Boolean = false) : Command()
    data class MoveSentenceDown(val selecting: Boolean = false) : Command()
    data class MoveToLineStart(val selecting: Boolean = false) : Command()
    data class MoveToLineEnd(val selecting: Boolean = false) : Command()
    data class MoveToVisualLineStart(val selecting: Boolean = false) : Command()
    data class MoveToVisualLineEnd(val selecting: Boolean = false) : Command()
    data class MoveToStart(val selecting: Boolean = false) : Command()
    data class MoveToEnd(val selecting: Boolean = false) : Command()
    data class PageUp(val selecting: Boolean = false) : Command()
    data class PageDown(val selecting: Boolean = false) : Command()
    data class SwitchBuffer(val index: Int) : Command()
    object RequestClearBuffer : Command()
    object ClearBuffer : Command()
    object ClearBufferNoWatermark : Command()
    object HideCursor : Command()
    object ShowCursor : Command()
    object HideEmptyBufferMsg : Command()
    object ShowEmptyBufferMsg : Command()
    data class ClearBufferAtIndex(val index: Int) : Command()
    object SaveBuffers : Command()
    object IncreaseFontSize : Command()
    object DecreaseFontSize : Command()
    object CycleTheme : Command()
    object CycleThemeBackwards : Command()
    object DisplayTheme : Command()
    object ToggleBold : Command()
    data class SetBold(val enabled: Boolean) : Command()
    
    // File Commands
    object SaveToFile : Command()
    object SaveAs : Command()
    object OpenFileBrowser : Command()
    object OpenFile : Command()
    object OpenRecentFiles : Command()
    object CloseFile : Command()
    object CloseAll : Command()
    object OpenPublishMenu : Command()
    object InstantPdf : Command()
    
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
    object SearchReplaceAll : Command()
    object GlobalApplySettings : Command()
    object RequestExit : Command()
    object MinimizeApp : Command()
    object ToggleVirtualKeyboard : Command()
    object BrightnessUp : Command()
    object BrightnessDown : Command()
    object VolumeUp : Command()
    object VolumeDown : Command()
    object Screenshot : Command()
    object ShareBuffer : Command()
    object CopyBufferToSystem : Command()
    object FullRefresh : Command()
    object TypewriterDemo : Command()
    
    // Outline Manager Commands
    object OpenOutlineSelection : Command()
    object OpenOutlineSetup : Command()
    object OutlineUp : Command()
    object OutlineDown : Command()
    data class OutlineSelect(val shiftPressed: Boolean = false) : Command()
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
    data class FocusPanel(val index: Int) : Command()
    data class SplitScreen(val orientation: Int, val otherBufferIndex: Int) : Command()
    object ExitSplit : Command()
    object ToggleMarkdownRender : Command()
    data class ForceMarkdownRender(val enabled: Boolean) : Command()
    object ToggleTypewriterMode : Command()
    object OpenTypewriterSettings : Command()
    object RecenterTypewriter : Command()
    object NextBuffer : Command()
    object PrevBuffer : Command()
    object OpenImageViewer : Command()
    object OpenImageSettings : Command()
    data class LoadNamedImage(val name: String) : Command()
    data class LoadNamedImageToBuffer(val name: String, val bufferIndex: Int) : Command()
    object ImageNext : Command()
    object ImagePrev : Command()
    object ToggleSlideshow : Command()
    
    // PDF Navigation Commands
    object PdfNextPage : Command()
    object PdfPrevPage : Command()
    object PdfFirstPage : Command()
    object PdfLastPage : Command()
    data class PdfGotoPage(val pageIndex: Int) : Command()
    object OpenPdfGotoMenu : Command()
    object PdfReset : Command()
    object OpenPdfSearch : Command()
    object AddPdfFavorite : Command()
    data class AddPdfFavoriteNamed(val identifier: String) : Command()
    object OpenPdfFavoritePicker : Command()
    data class SelectPdfFavorite(val index: Int) : Command()
    object ExportPdfFavorites : Command()
    object OpenPdfFavorites : Command() // Legacy? User mentioned 'v' but I'll use new ones
    object PdfZoomIn : Command()
    object PdfZoomOut : Command()
    
    // Overlay Commands
    object OpenBufferMenu : Command()
    object OpenThemeMenu : Command()
    object OpenMarginMenu : Command()
    object ToggleShowMargins : Command()
    object OpenFontMenu : Command()
    object OpenFontPresetMenu : Command()
    object DisplayFont : Command()
    data class CycleFonts(val forward: Boolean) : Command()
    object OpenLineSpacingMenu : Command()
    object CycleLineHeight : Command()
    data class AdjustVerticalOffset(val delta: Float) : Command()
    data class OpenAppMenu(val index: Int) : Command()
    object ToggleStatus : Command()
    object OpenHelpMenu : Command()
    object OpenSettingsMenu : Command()
    object OpenSyncMenu : Command()
    object OpenWallpaperMenu : Command()
    object ShowHelp : Command()
    object ShowHelpPopup : Command()
    object ShowAbout : Command()
    object ShowKeymap : Command()
    object OpenCursorMenu : Command()
    object OverlayUp : Command()
    object OverlayDown : Command()
    object OverlayLeft : Command()
    object OverlayRight : Command()
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
    object RecollapseLastExpansion : Command()
    object OpenFoldMenu : Command()
    
    // Scratchpad Commands
    object OpenScratchpad : Command()
    object FlushScratchpadSave : Command()
    object FlushScratchpadNoSave : Command()
    object OpenScratchpadSettings : Command()
    
    // Macro Commands
    // Pattern Collapse Commands
    object OpenPatternCollapseInput : Command()
    data class AddCollapsePattern(val pattern: String) : Command()
    object OpenPatternCollapsePicker : Command()
    data class ToggleCollapsePattern(val index: Int) : Command()
    data class DeleteCollapsePattern(val index: Int) : Command()
    data class RenameCollapsePattern(val index: Int, val newPattern: String) : Command()
    object UncollapseAll : Command()
    object RecollapseActivePatterns : Command()
    object CollapseAllParagraphs : Command()
    object UnwrapParagraphs : Command()

    object OpenExamplesMenu : Command()

    object OpenMacroMenu : Command()
    object OpenManuscriptMenu : Command()
    object OpenManuscriptFilmstrip : Command()
    object OpenManuscriptLayout : Command()
    object OpenManuscriptSettings : Command()
    object OpenWorkspaceLayout : Command()
    object FastInsertMacro : Command()
    object PasteLastMacro : Command()
    object MacroEdit : Command()
    object MacroInsert : Command()
    object OpenMacroContextPicker : Command()
    object AddMacroContext : Command()
    
    // Scripting Commands
    object OpenScriptConsole : Command()
    data class ExecuteScriptLine(val line: String) : Command()
    object RunScript : Command()
    object StopScript : Command()
    object ScriptStep : Command()
    object Beep : Command()

    // Help Task-Switching
    object ToggleHelpTaskSwitch : Command()
    
    // Workspace Commands
    object ToggleTodoWorkspace : Command()
    
    // Keyboard Mode Commands
    data class SetKeyboardMode(val mode: Int) : Command()

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
    private val onBoldChanged: (Boolean) -> Unit,
    private val onOverlayAction: (Command) -> Unit,
    private val onClipboardAction: (Command) -> Unit,
    private val onFontCycle: (Boolean) -> Unit = {},
    private val onKeyboardModeChanged: (Int) -> Unit = {}
) {
    fun dispatch(command: Command) {
        when (command) {
            is Command.CycleFonts -> {
                onFontCycle(command.forward)
            }
            is Command.SetKeyboardMode -> {
                onKeyboardModeChanged(command.mode)
            }
            is Command.SetBold -> {
                onBoldChanged(command.enabled)
            }
            is Command.InsertText -> {
                val buffer = bufferManager.getCurrentBuffer()
                if (command.text == "\n" && buffer.isAtFold()) {
                    buffer.uncollapseAtCursor()
                } else {
                    buffer.insert(command.text)
                }
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
                if (bufferManager.getCurrentBuffer().pdfUri != null) {
                    onOverlayAction(command)
                } else {
                    bufferManager.getCurrentBuffer().moveWordLeft(command.selecting)
                    onContentChanged()
                }
            }
            is Command.MoveWordRight -> {
                if (bufferManager.getCurrentBuffer().pdfUri != null) {
                    onOverlayAction(command)
                } else {
                    bufferManager.getCurrentBuffer().moveWordRight(command.selecting)
                    onContentChanged()
                }
            }
            is Command.MoveParagraphUp -> {
                if (bufferManager.getCurrentBuffer().pdfUri != null) {
                    onOverlayAction(command)
                } else {
                    bufferManager.getCurrentBuffer().moveParagraphUp(command.selecting)
                    onContentChanged()
                }
            }
            is Command.MoveParagraphDown -> {
                if (bufferManager.getCurrentBuffer().pdfUri != null) {
                    onOverlayAction(command)
                } else {
                    bufferManager.getCurrentBuffer().moveParagraphDown(command.selecting)
                    onContentChanged()
                }
            }
            is Command.MoveToParagraphStart -> {
                bufferManager.getCurrentBuffer().moveToParagraphStart(command.selecting)
                onContentChanged()
            }
            is Command.MoveToParagraphEnd -> {
                bufferManager.getCurrentBuffer().moveToParagraphEnd(command.selecting)
                onContentChanged()
            }
            is Command.MoveSentenceUp -> {
                if (bufferManager.getCurrentBuffer().pdfUri != null) {
                    onOverlayAction(command)
                } else {
                    bufferManager.getCurrentBuffer().moveSentenceUp(command.selecting)
                    onContentChanged()
                }
            }
            is Command.MoveSentenceDown -> {
                if (bufferManager.getCurrentBuffer().pdfUri != null) {
                    onOverlayAction(command)
                } else {
                    bufferManager.getCurrentBuffer().moveSentenceDown(command.selecting)
                    onContentChanged()
                }
            }
            is Command.MoveToLineStart -> {
                if (bufferManager.getCurrentBuffer().pdfUri != null) {
                    onOverlayAction(command)
                } else {
                    bufferManager.getCurrentBuffer().moveCursorToLineStart(command.selecting)
                    onContentChanged()
                }
            }
            is Command.MoveToLineEnd -> {
                if (bufferManager.getCurrentBuffer().pdfUri != null) {
                    onOverlayAction(command)
                } else {
                    bufferManager.getCurrentBuffer().moveCursorToLineEnd(command.selecting)
                    onContentChanged()
                }
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
            is Command.PageUp, is Command.PageDown -> {
                onOverlayAction(command)
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
            Command.ClearBufferNoWatermark -> {
                bufferManager.getCurrentBuffer().clear()
                onOverlayAction(command)
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
            Command.ToggleBold -> {
                val currentBold = bufferManager.getCurrentBuffer().isBold
                onBoldChanged(!currentBold)
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
            Command.OverlayUp,
            Command.OverlayDown,
            Command.OverlayLeft,
            Command.OverlayRight,
            Command.MoveToViewportCenter,
            Command.StartFindCursor,
            Command.StopFindCursor,
            Command.OpenFind,
            Command.OpenReplace,
            Command.SearchToggleScope,
            Command.SearchSwitchField,
            Command.SearchReplaceAll,
            Command.GlobalApplySettings,
            Command.HideCursor,
            Command.ShowCursor,
            Command.HideEmptyBufferMsg,
            Command.ShowEmptyBufferMsg,
            Command.RequestExit,
            Command.MinimizeApp,
            Command.ToggleVirtualKeyboard,
            Command.BrightnessUp,
            Command.BrightnessDown,
            Command.VolumeUp,
            Command.VolumeDown,
            Command.Screenshot,
            Command.ShareBuffer,
            Command.CopyBufferToSystem,
            Command.FullRefresh,
            Command.TypewriterDemo,
            Command.RequestClearBuffer,
            Command.SaveToFile,
            Command.SaveAs,
            Command.OpenFile,
            Command.OpenRecentFiles,
            Command.CloseFile,
            Command.CloseAll,
            Command.OpenPublishMenu,
            Command.InstantPdf,
            Command.OpenBufferMenu,
            Command.OpenThemeMenu,
            Command.OpenMarginMenu,
            Command.ToggleShowMargins,
            Command.OpenFontMenu,
            Command.OpenFontPresetMenu,
            Command.DisplayFont,
            is Command.CycleFonts,
            Command.DisplayTheme,
            Command.OpenLineSpacingMenu,
            Command.CycleLineHeight,
            is Command.AdjustVerticalOffset,
            is Command.ClearBufferAtIndex,
            Command.ToggleStatus,
            is Command.OpenAppMenu,
            Command.OpenHelpMenu,
            Command.OpenSettingsMenu,
            Command.OpenSyncMenu,
            Command.OpenWallpaperMenu,
            Command.ShowHelp,
            Command.ShowHelpPopup,
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
            Command.RecollapseLastExpansion,
            Command.OpenFoldMenu,
            Command.OpenOutlineSelection,
            Command.OpenImageViewer,
            Command.OpenImageSettings,
            is Command.LoadNamedImage,
            is Command.LoadNamedImageToBuffer,
            Command.ImageNext,
            Command.ImagePrev,
            Command.ToggleSlideshow,
            Command.OpenOutlineSetup,
            Command.OutlineUp,
            Command.OutlineDown,
            is Command.OutlineSelect,
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
            is Command.FocusPanel,
            is Command.SplitScreen,
            Command.ExitSplit,
            Command.ToggleMarkdownRender,
            is Command.ForceMarkdownRender,
            Command.ToggleTypewriterMode,
            Command.OpenTypewriterSettings,
            Command.RecenterTypewriter,
            Command.OpenScratchpad,
            Command.FlushScratchpadSave,
            Command.FlushScratchpadNoSave,
            Command.OpenScratchpadSettings,
            Command.PdfNextPage,
            Command.PdfPrevPage,
            Command.PdfFirstPage,
            Command.PdfLastPage,
            is Command.PdfGotoPage,
            Command.OpenPdfGotoMenu,
            Command.PdfReset,
            Command.OpenPdfSearch,
            Command.AddPdfFavorite,
            is Command.AddPdfFavoriteNamed,
            Command.OpenPdfFavoritePicker,
            is Command.SelectPdfFavorite,
            Command.ExportPdfFavorites,
            Command.OpenPdfFavorites,
            Command.PdfZoomIn,
            Command.PdfZoomOut,
            Command.OpenPatternCollapseInput,
            is Command.AddCollapsePattern,
            Command.OpenPatternCollapsePicker,
            is Command.ToggleCollapsePattern,
            is Command.DeleteCollapsePattern,
            is Command.RenameCollapsePattern,
            Command.UncollapseAll,
            Command.RecollapseActivePatterns,
            Command.CollapseAllParagraphs,
            Command.UnwrapParagraphs,
            Command.OpenExamplesMenu,
            Command.OpenManuscriptMenu,
            Command.OpenManuscriptFilmstrip,
            Command.OpenManuscriptLayout,
            Command.OpenManuscriptSettings,
            Command.OpenWorkspaceLayout,
            Command.OpenMacroMenu,
            Command.FastInsertMacro,
            Command.PasteLastMacro,
            Command.MacroEdit,
            Command.MacroInsert,
            Command.OpenMacroContextPicker,
            Command.AddMacroContext,
            Command.OpenScriptConsole,
            is Command.ExecuteScriptLine,
            Command.RunScript,
            Command.StopScript,
            Command.ScriptStep,
            Command.Beep,
            Command.ToggleHelpTaskSwitch,
            Command.ToggleTodoWorkspace,
            is Command.InputChar,
            Command.InputDelete,
            Command.OpenFileBrowser -> {
                onOverlayAction(command)
            }
        }
    }
}
