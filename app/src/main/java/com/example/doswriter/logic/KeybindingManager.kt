package com.example.doswriter.logic

import android.view.KeyEvent

class KeybindingManager {
    fun getCommand(
        event: KeyEvent, 
        isOverlayVisible: Boolean, 
        isInputVisible: Boolean, 
        isHelpVisible: Boolean, 
        isSearchVisible: Boolean, 
        isMultiVisible: Boolean, 
        isOutlineVisible: Boolean, 
        isMacroVisible: Boolean, 
        isViewerActive: Boolean, 
        isPdfActive: Boolean, 
        isVPressed: Boolean, 
        isPersistentScreenshotEnabled: Boolean, 
        isScriptActive: Boolean, 
        isFileBrowserVisible: Boolean,
        isScriptConsoleVisible: Boolean = false
    ): Command? {
        if (event.action != KeyEvent.ACTION_DOWN) return null

        val isAlt = event.isAltPressed
        val isCtrl = event.isCtrlPressed
        val isShift = event.isShiftPressed

        // -1. Global Persistent Shortcuts
        if (isPersistentScreenshotEnabled && isCtrl && isShift && !isAlt && event.keyCode == KeyEvent.KEYCODE_I) {
            return Command.Screenshot
        }

        // 0. Image Viewer Navigation
        if (isViewerActive && !isAlt && !isCtrl) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> return Command.ImagePrev
                KeyEvent.KEYCODE_DPAD_RIGHT -> return Command.ImageNext
                KeyEvent.KEYCODE_S -> return Command.ToggleSlideshow
                KeyEvent.KEYCODE_ESCAPE -> return Command.OverlayCancel
            }
        }

        // --- NEW: Priority check for File Browser ---
        val isAnyDialogActive = isSearchVisible || isOverlayVisible || isInputVisible || isHelpVisible || isMultiVisible || isMacroVisible || isOutlineVisible || isScriptConsoleVisible
        
        if (isFileBrowserVisible && !isAnyDialogActive) {
            return null // FileBrowserOverlay handles its own keys directly via interceptor
        }

        if (isScriptConsoleVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER -> return Command.OverlaySelect
                KeyEvent.KEYCODE_ESCAPE -> return Command.OverlayCancel
                KeyEvent.KEYCODE_DEL -> return Command.InputDelete
                KeyEvent.KEYCODE_DPAD_UP -> return Command.OverlayUp
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.OverlayDown
            }
            val unicode = event.unicodeChar
            if (unicode != 0) {
                return Command.InputChar(unicode.toChar())
            }
            return null
        }
        if (isSearchVisible) {
            if (isCtrl && !isAlt) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_V -> return Command.Paste
                    KeyEvent.KEYCODE_C -> return Command.Copy
                    KeyEvent.KEYCODE_X -> return Command.Cut
                }
            }
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    return if (isCtrl) Command.OverlaySelectBackward else Command.OverlaySelect
                }
                KeyEvent.KEYCODE_ESCAPE -> return Command.OverlayCancel
                KeyEvent.KEYCODE_DEL -> return Command.InputDelete
                KeyEvent.KEYCODE_TAB -> return Command.SearchSwitchField
                KeyEvent.KEYCODE_DPAD_UP -> return Command.OverlayUp
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.OverlayDown
                KeyEvent.KEYCODE_U -> if (isCtrl) return Command.OverlayUp
                KeyEvent.KEYCODE_Y -> if (isCtrl) return Command.OverlayDown
            }
            if (isAlt && event.keyCode == KeyEvent.KEYCODE_S) {
                return Command.SearchToggleScope
            }
            if (isAlt && event.keyCode == KeyEvent.KEYCODE_A) {
                return Command.SearchReplaceAll
            }
            val unicode = event.unicodeChar
            if (unicode != 0) {
                return Command.InputChar(unicode.toChar())
            }
            return null
        }

        // 2. Input Overlay specific
        if (isInputVisible) {
            if (isCtrl && !isAlt) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_V -> return Command.Paste
                    KeyEvent.KEYCODE_C -> return Command.Copy
                    KeyEvent.KEYCODE_X -> return Command.Cut
                }
            }
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER -> return Command.OverlaySelect
                KeyEvent.KEYCODE_ESCAPE -> return Command.OverlayCancel
                KeyEvent.KEYCODE_DEL -> return Command.InputDelete
            }
            val unicode = event.unicodeChar
            if (unicode != 0) {
                return Command.InputChar(unicode.toChar())
            }
            return null
        }

        // 3. Help Overlay specific
        if (isHelpVisible) {
            if (isCtrl && !isAlt) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_EQUALS -> return Command.IncreaseFontSize
                    KeyEvent.KEYCODE_MINUS -> return Command.DecreaseFontSize
                }
            }
            when (event.keyCode) {
                KeyEvent.KEYCODE_ESCAPE -> return Command.OverlayCancel
                KeyEvent.KEYCODE_H -> if (isCtrl) return Command.OverlayCancel
                KeyEvent.KEYCODE_DPAD_UP -> return Command.OverlayUp
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.OverlayDown
                KeyEvent.KEYCODE_DPAD_LEFT -> return Command.OverlayLeft
                KeyEvent.KEYCODE_DPAD_RIGHT -> return Command.OverlayRight
                KeyEvent.KEYCODE_U -> if (isCtrl) return Command.OverlayUp
                KeyEvent.KEYCODE_Y -> if (isCtrl) return Command.OverlayDown
            }
            return null
        }

        // 4. Selection Overlay specific
        if (isOverlayVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> return Command.OverlayUp
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.OverlayDown
                KeyEvent.KEYCODE_DPAD_LEFT -> return Command.OverlayLeft
                KeyEvent.KEYCODE_DPAD_RIGHT -> return Command.OverlayRight
                KeyEvent.KEYCODE_U -> if (isCtrl) return Command.OverlayUp
                KeyEvent.KEYCODE_Y -> if (isCtrl) return Command.OverlayDown
                KeyEvent.KEYCODE_ENTER -> return Command.OverlaySelect
                KeyEvent.KEYCODE_ESCAPE -> return Command.OverlayCancel
                KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
                    return if (isShift) Command.OverlayDeleteAll else Command.OverlayDelete
                }
            }
            if (isAlt && event.keyCode == KeyEvent.KEYCODE_M) {
                return Command.OverlayToggleMarginPreview
            }
            if (event.keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_8) {
                return Command.OverlayJump(event.keyCode - KeyEvent.KEYCODE_1)
            }
            return null
        }

        // 5. Multi-Selection Overlay specific
        if (isMultiVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> return Command.OverlayUp
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.OverlayDown
                KeyEvent.KEYCODE_U -> if (isCtrl) return Command.OverlayUp
                KeyEvent.KEYCODE_Y -> if (isCtrl) return Command.OverlayDown
                KeyEvent.KEYCODE_ENTER -> return Command.OverlaySelect
                KeyEvent.KEYCODE_ESCAPE -> return Command.OverlayCancel
                KeyEvent.KEYCODE_SPACE -> return Command.OverlayToggle
            }
            if (event.keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_8) {
                return Command.OverlayJump(event.keyCode - KeyEvent.KEYCODE_1)
            }
            return null
        }

        // 5b. Macro Overlay specific
        if (isMacroVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> return Command.OverlayUp
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.OverlayDown
                KeyEvent.KEYCODE_U -> if (isCtrl) return Command.OverlayUp
                KeyEvent.KEYCODE_Y -> if (isCtrl) return Command.OverlayDown
                KeyEvent.KEYCODE_ENTER -> return Command.OverlaySelect
                KeyEvent.KEYCODE_ESCAPE -> return Command.OverlayCancel
                KeyEvent.KEYCODE_DEL -> return Command.InputDelete
                KeyEvent.KEYCODE_A -> if (!isAlt && isShift) return Command.AddMacroContext else null
            }
            // Allow Ctrl+Enter to also select
            if (isCtrl && event.keyCode == KeyEvent.KEYCODE_ENTER) return Command.OverlaySelect

            val unicode = event.unicodeChar
            if (unicode != 0) {
                return Command.InputChar(unicode.toChar())
            }
            return null
        }

        // 6. Outline Manager specific
        if (isOutlineVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_S -> {
                    if (isCtrl) return if (isShift) Command.SaveOutlineAs else Command.SaveOutlineToFile
                    return Command.SaveOutlineToFile
                }
                KeyEvent.KEYCODE_O -> {
                    if (isCtrl) return Command.OpenOutlineFile
                    return Command.OpenOutlineFile
                }
                KeyEvent.KEYCODE_N -> if (isCtrl) return Command.RequestClearOutline
                KeyEvent.KEYCODE_U -> return if (isCtrl) Command.OutlineUp else Command.OutlineUnlink
                KeyEvent.KEYCODE_Y -> if (isCtrl) return Command.OutlineDown
                KeyEvent.KEYCODE_DPAD_UP -> return Command.OutlineUp
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.OutlineDown
                KeyEvent.KEYCODE_ENTER -> return Command.OutlineSelect(isShift)
                KeyEvent.KEYCODE_ESCAPE -> return Command.OverlayCancel
                
                // Setup shortcuts
                KeyEvent.KEYCODE_F -> return if (!isCtrl && !isAlt) Command.OutlineAddFolder else null
                KeyEvent.KEYCODE_C -> return if (!isCtrl && !isAlt) Command.OutlineAddContainer else null
                KeyEvent.KEYCODE_D -> return if (!isCtrl && !isAlt) Command.OutlineDelete else null
                KeyEvent.KEYCODE_L -> return if (!isCtrl && !isAlt) Command.OutlineLinkFile else null
                KeyEvent.KEYCODE_K -> return if (!isCtrl && !isAlt) Command.OutlineLinkRemote else null
                KeyEvent.KEYCODE_R -> return if (!isCtrl && !isAlt) Command.OutlineRename else null
            }
            if (event.keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_8) {
                return Command.OverlayJump(event.keyCode - KeyEvent.KEYCODE_1)
            }
            return null
        }

        // 7. PDF Mode specific
        if (isPdfActive && !isOverlayVisible && !isInputVisible && !isSearchVisible) {
            if (isCtrl && isAlt) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_K -> return Command.AddPdfFavorite
                    KeyEvent.KEYCODE_P -> return Command.ExportPdfFavorites
                }
            }
            if (isCtrl && !isAlt) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_K -> return Command.OpenPdfFavoritePicker
                    KeyEvent.KEYCODE_G -> return Command.OpenPdfGotoMenu
                    KeyEvent.KEYCODE_R -> return Command.PdfReset
                    KeyEvent.KEYCODE_DPAD_UP -> return Command.MoveParagraphUp(false)
                    KeyEvent.KEYCODE_DPAD_DOWN -> return Command.MoveParagraphDown(false)
                    KeyEvent.KEYCODE_DPAD_LEFT -> return Command.MoveWordLeft(false)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> return Command.MoveWordRight(false)
                }
            }
            if (!isCtrl && !isAlt) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_N -> return Command.PdfNextPage
                    KeyEvent.KEYCODE_P -> return Command.PdfPrevPage
                    KeyEvent.KEYCODE_F -> return Command.OpenPdfSearch
                    KeyEvent.KEYCODE_V -> return Command.OpenPdfFavorites
                    KeyEvent.KEYCODE_DPAD_LEFT -> return Command.PdfPrevPage
                    KeyEvent.KEYCODE_DPAD_RIGHT -> return Command.PdfNextPage
                }
            }
            if (isAlt && !isCtrl) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> return Command.PdfFirstPage
                    KeyEvent.KEYCODE_DPAD_DOWN -> return Command.PdfLastPage
                }
            }
            
            // Allow Global Shortcuts (Modifiers, F-keys, Esc) to pass through
            val isGlobalShortcut = isCtrl || isAlt || event.keyCode == KeyEvent.KEYCODE_ESCAPE || 
                                 event.keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12
            
            // Allow vertical scrolling arrows to pass through to standard logic ( MainActivity translates to fine scroll)
            val isScrollKey = !isCtrl && !isAlt && !isShift && 
                             (event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN)

            if (!isGlobalShortcut && !isScrollKey) {
                return null // Block plain alpha/numeric typing
            }
        }

        // --- Standard Logic ---

        // Deletion handling
        if (event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
            return when {
                isShift && isCtrl -> Command.DeleteLine
                isCtrl -> Command.DeleteWord
                isShift -> Command.DeleteLine
                !isAlt -> Command.DeleteForward
                else -> null
            }
        }

        // Ctrl + Alt combinations (Highest priority for combined modifiers)
        if (isCtrl && isAlt) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_X -> return Command.RequestExit
                KeyEvent.KEYCODE_S -> return Command.ShareBuffer
                KeyEvent.KEYCODE_C -> return Command.CopyBufferToSystem
                KeyEvent.KEYCODE_U -> return Command.UnwrapParagraphs
                KeyEvent.KEYCODE_O -> return Command.OpenOutlineSetup
                KeyEvent.KEYCODE_9 -> return Command.OpenScratchpad
                KeyEvent.KEYCODE_W -> return Command.CloseAll
                KeyEvent.KEYCODE_Q -> return Command.OpenManuscriptFilmstrip
                KeyEvent.KEYCODE_I -> return Command.OpenImageViewer
                KeyEvent.KEYCODE_DPAD_LEFT -> return Command.OpenSplitMenu
                KeyEvent.KEYCODE_DPAD_RIGHT -> return Command.OpenSplitMenu
                KeyEvent.KEYCODE_DPAD_UP -> return Command.MoveToStart(isShift)
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.MoveToEnd(isShift)
                KeyEvent.KEYCODE_Z -> return Command.Redo
                KeyEvent.KEYCODE_T -> return Command.CycleThemeBackwards
                KeyEvent.KEYCODE_B -> return Command.CycleFonts(true)
                KeyEvent.KEYCODE_H -> return Command.ShowHelpPopup
                KeyEvent.KEYCODE_J -> return Command.OpenPatternCollapseInput
                KeyEvent.KEYCODE_DEL -> return Command.DeleteWord
                KeyEvent.KEYCODE_F -> return Command.OpenFileBrowser
                KeyEvent.KEYCODE_M -> return Command.FastInsertMacro // Paste Text Macro (Sticky Picker)
                KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD -> return Command.OpenFontPresetMenu
                KeyEvent.KEYCODE_1 -> if (isShift) return Command.OpenFontPresetMenu // Alias for German keyboards
                KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> return Command.SetBold(false)
            }
        }

        // Ctrl combinations
        if (isCtrl && !isAlt) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_E -> return Command.OpenExamplesMenu
                KeyEvent.KEYCODE_M -> return if (isShift) Command.ToggleShowMargins else Command.PasteLastMacro
                KeyEvent.KEYCODE_MOVE_HOME -> return Command.MoveToStart(isShift)
                KeyEvent.KEYCODE_MOVE_END -> return Command.MoveToEnd(isShift)
                KeyEvent.KEYCODE_D -> return Command.OpenSyncMenu
                KeyEvent.KEYCODE_N -> return Command.RequestClearBuffer
                KeyEvent.KEYCODE_C -> return if (isShift) Command.ClearBufferNoWatermark else Command.Copy
                KeyEvent.KEYCODE_S -> return if (isShift) Command.SaveAs else Command.SaveToFile
                KeyEvent.KEYCODE_W -> return Command.CloseFile
                KeyEvent.KEYCODE_P -> return Command.InstantPdf
                KeyEvent.KEYCODE_Q -> return if (isShift) Command.OpenManuscriptLayout else Command.OpenManuscriptMenu
                KeyEvent.KEYCODE_O -> return if (isShift) Command.OpenRecentFiles else Command.OpenFile
                KeyEvent.KEYCODE_A -> return Command.SelectAll
                KeyEvent.KEYCODE_1 -> if (isShift) return Command.DecreaseFontSize
                KeyEvent.KEYCODE_2 -> if (isShift) return Command.IncreaseFontSize
                KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD -> return Command.IncreaseFontSize
                KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> return Command.DecreaseFontSize
                KeyEvent.KEYCODE_T -> return if (isShift) Command.DisplayTheme else Command.CycleTheme
                KeyEvent.KEYCODE_B -> return Command.ToggleBold
                KeyEvent.KEYCODE_J -> return if (isShift) Command.RecollapseLastExpansion else Command.CollapseSelection
                KeyEvent.KEYCODE_X -> return Command.Cut
                KeyEvent.KEYCODE_V -> return Command.Paste
                KeyEvent.KEYCODE_I -> return if (isShift) Command.Screenshot else Command.ToggleSlideshow
                KeyEvent.KEYCODE_DEL -> return if (isShift) Command.DeleteLine else Command.DeleteWord
                KeyEvent.KEYCODE_Z -> return Command.Undo
                KeyEvent.KEYCODE_Y -> return Command.ToggleTypewriterMode
                KeyEvent.KEYCODE_K -> return if (isShift) Command.JumpLastBookmark else Command.AddBookmark
                KeyEvent.KEYCODE_F -> return if (isShift) Command.DisplayFont else Command.OpenFind
                KeyEvent.KEYCODE_R -> return Command.OpenReplace
                KeyEvent.KEYCODE_H -> return Command.ToggleStatus
                KeyEvent.KEYCODE_L -> return Command.CycleLineHeight
                KeyEvent.KEYCODE_9 -> return if (isShift) Command.FlushScratchpadNoSave else Command.FlushScratchpadSave
                
                // Paragraph navigation
                KeyEvent.KEYCODE_DPAD_UP -> return Command.MoveParagraphUp(isShift)
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.MoveParagraphDown(isShift)
                KeyEvent.KEYCODE_DPAD_LEFT -> return Command.MoveWordLeft(isShift)
                KeyEvent.KEYCODE_DPAD_RIGHT -> return Command.MoveWordRight(isShift)
                KeyEvent.KEYCODE_BACKSLASH -> return Command.MoveToStart(isShift)
                KeyEvent.KEYCODE_APOSTROPHE -> return Command.MoveToEnd(isShift)
                KeyEvent.KEYCODE_COMMA -> return if (!isAlt) Command.AdjustVerticalOffset(-5f) else null
                KeyEvent.KEYCODE_PERIOD -> return if (!isAlt) Command.AdjustVerticalOffset(5f) else null
                KeyEvent.KEYCODE_LEFT_BRACKET -> return Command.PageUp(isShift)
                KeyEvent.KEYCODE_RIGHT_BRACKET -> return Command.PageDown(isShift)
            }
        }

        // Alt combinations
        if (isAlt && !isCtrl) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MOVE_HOME -> return Command.MoveToParagraphStart(isShift)
                KeyEvent.KEYCODE_MOVE_END -> return Command.MoveToParagraphEnd(isShift)
                KeyEvent.KEYCODE_F -> return Command.OpenAppMenu(0)
                KeyEvent.KEYCODE_E -> return Command.OpenAppMenu(1)
                KeyEvent.KEYCODE_V -> return Command.OpenAppMenu(2)
                KeyEvent.KEYCODE_W -> return Command.OpenAppMenu(3)
                KeyEvent.KEYCODE_T -> return Command.OpenAppMenu(4)
                KeyEvent.KEYCODE_Q -> return Command.OpenManuscriptSettings
                KeyEvent.KEYCODE_Y -> return Command.OpenAppMenu(5)
                KeyEvent.KEYCODE_P -> return Command.OpenAppMenu(6)
                KeyEvent.KEYCODE_S -> return Command.OpenAppMenu(7)
                KeyEvent.KEYCODE_H -> return Command.OpenAppMenu(8)
                KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4,
                KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8 -> {
                    return Command.SwitchBuffer(event.keyCode - KeyEvent.KEYCODE_1)
                }
                KeyEvent.KEYCODE_9 -> return Command.OpenScratchpad
                KeyEvent.KEYCODE_B -> return Command.OpenBufferMenu
                KeyEvent.KEYCODE_M -> return Command.OpenMacroMenu // Text Macro Console
                KeyEvent.KEYCODE_L -> return Command.OpenLineSpacingMenu
                KeyEvent.KEYCODE_N -> return Command.SwapSplitFocus
                KeyEvent.KEYCODE_O -> return Command.OpenOutlineSelection
                KeyEvent.KEYCODE_P -> return Command.OpenPublishMenu
                KeyEvent.KEYCODE_K -> return Command.OpenBookmarkMenu
                KeyEvent.KEYCODE_X -> return Command.MinimizeApp
                KeyEvent.KEYCODE_Z -> return Command.OpenScriptConsole
                KeyEvent.KEYCODE_R -> return Command.ToggleMarkdownRender
                KeyEvent.KEYCODE_D -> return Command.TypewriterDemo
                KeyEvent.KEYCODE_C -> return Command.MoveToViewportCenter
                KeyEvent.KEYCODE_I -> return if (isShift) Command.OpenImageViewer else Command.OpenImageSettings
                KeyEvent.KEYCODE_J -> return Command.OpenPatternCollapsePicker
                KeyEvent.KEYCODE_DEL -> return Command.DeleteBackward
                KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD -> {
                    return if (isVPressed) Command.VolumeUp else Command.BrightnessUp
                }
                KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> {
                    return if (isVPressed) Command.VolumeDown else Command.BrightnessDown
                }
                
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    return Command.MoveToVisualLineStart(isShift)
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    return Command.MoveToVisualLineEnd(isShift)
                }
                KeyEvent.KEYCODE_DPAD_UP -> return Command.MoveSentenceUp(isShift)
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.MoveSentenceDown(isShift)
            }
        }

        if (event.keyCode == KeyEvent.KEYCODE_F1 && isCtrl) {
            return Command.BrightnessDown
        }
        if (event.keyCode == KeyEvent.KEYCODE_F2 && isCtrl) {
            return Command.BrightnessUp
        }

        // Functional keys (No modifiers for buffer switching)
        if (!isCtrl && !isAlt && event.keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F7) {
            return Command.SwitchBuffer(event.keyCode - KeyEvent.KEYCODE_F1)
        }
        
        if (event.keyCode == KeyEvent.KEYCODE_F10 || event.keyCode == KeyEvent.KEYCODE_MENU) {
            // If already in a menu, don't re-trigger to prevent "flashing"
            if (isOverlayVisible) return null
            return Command.OpenSettingsMenu
        }

        if (event.keyCode == KeyEvent.KEYCODE_F11) {
            return if (isCtrl) Command.RecenterTypewriter else Command.ToggleTypewriterMode
        }

        if (event.keyCode == KeyEvent.KEYCODE_F12) {
            return if (isCtrl) Command.OpenOutlineSetup else Command.OpenOutlineSelection
        }

        if (event.keyCode == KeyEvent.KEYCODE_F9) {
            return when {
                isCtrl -> Command.FlushScratchpadSave
                isAlt -> Command.FlushScratchpadNoSave
                else -> Command.OpenScratchpad
            }
        }

        if (event.keyCode == KeyEvent.KEYCODE_F8) {
            return when {
                isCtrl && isAlt -> Command.StopScript
                isCtrl -> Command.RunScript
                isScriptActive -> Command.ScriptStep
                else -> Command.SwitchBuffer(7)
            }
        }

        // Basic navigation (No modifiers)
        if (!isCtrl && !isAlt) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_ESCAPE -> return Command.ExitSplit
                KeyEvent.KEYCODE_DPAD_LEFT -> return Command.MoveLeft(isShift)
                KeyEvent.KEYCODE_DPAD_RIGHT -> return Command.MoveRight(isShift)
                KeyEvent.KEYCODE_DPAD_UP -> return Command.MoveUp(isShift)
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.MoveDown(isShift)
                KeyEvent.KEYCODE_MOVE_HOME -> return Command.MoveToVisualLineStart(isShift)
                KeyEvent.KEYCODE_MOVE_END -> return Command.MoveToVisualLineEnd(isShift)
                KeyEvent.KEYCODE_DEL -> return Command.DeleteBackward
                KeyEvent.KEYCODE_ENTER -> return Command.InsertText("\n")
                KeyEvent.KEYCODE_TAB -> return null // Handled by Activity to access buffer settings
            }
        }

        // Regular text input
        val unicodeChar = event.unicodeChar
        if (unicodeChar != 0 && !isCtrl && !isAlt) {
            return null // Handled by Activity to handle Tab size correctly
        }

        return null
    }
}
