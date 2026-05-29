package com.example.minimaltexteditor.logic

import android.view.KeyEvent

class KeybindingManager {
    fun getCommand(event: KeyEvent, isOverlayVisible: Boolean, isInputVisible: Boolean, isHelpVisible: Boolean, isSearchVisible: Boolean, isMultiVisible: Boolean, isOutlineVisible: Boolean, isViewerActive: Boolean, isVPressed: Boolean): Command? {
        if (event.action != KeyEvent.ACTION_DOWN) return null

        val isAlt = event.isAltPressed
        val isCtrl = event.isCtrlPressed
        val isShift = event.isShiftPressed

        // 0. Image Viewer Navigation
        if (isViewerActive) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> return Command.ImagePrev
                KeyEvent.KEYCODE_DPAD_RIGHT -> return Command.ImageNext
                KeyEvent.KEYCODE_S -> return Command.ToggleSlideshow
                KeyEvent.KEYCODE_ESCAPE -> return Command.OverlayCancel
            }
            return null
        }

        // 1. Search Overlay specific
        if (isSearchVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    return if (isCtrl) Command.OverlaySelectBackward else Command.OverlaySelect
                }
                KeyEvent.KEYCODE_ESCAPE -> return Command.OverlayCancel
                KeyEvent.KEYCODE_DEL -> return Command.InputDelete
                KeyEvent.KEYCODE_TAB -> return Command.SearchSwitchField
                KeyEvent.KEYCODE_DPAD_UP -> return Command.OverlayUp
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.OverlayDown
            }
            if (isAlt && event.keyCode == KeyEvent.KEYCODE_S) {
                return Command.SearchToggleScope
            }
            val unicode = event.unicodeChar
            if (unicode != 0) {
                return Command.InputChar(unicode.toChar())
            }
            return null
        }

        // 2. Input Overlay specific
        if (isInputVisible) {
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
            when (event.keyCode) {
                KeyEvent.KEYCODE_ESCAPE -> return Command.OverlayCancel
                KeyEvent.KEYCODE_H -> if (isCtrl) return Command.OverlayCancel
                KeyEvent.KEYCODE_DPAD_UP -> return Command.OverlayUp
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.OverlayDown
            }
            return null
        }

        // 4. Selection Overlay specific
        if (isOverlayVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> return Command.OverlayUp
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.OverlayDown
                KeyEvent.KEYCODE_ENTER -> return Command.OverlaySelect
                KeyEvent.KEYCODE_ESCAPE -> return Command.OverlayCancel
                KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
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
                KeyEvent.KEYCODE_ENTER -> return Command.OverlaySelect
                KeyEvent.KEYCODE_ESCAPE -> return Command.OverlayCancel
                KeyEvent.KEYCODE_SPACE -> return Command.OverlayToggle
            }
            if (event.keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_8) {
                return Command.OverlayJump(event.keyCode - KeyEvent.KEYCODE_1)
            }
            return null
        }

        // 6. Outline Manager specific
        if (isOutlineVisible) {
            if (isCtrl) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_S -> return if (isShift) Command.SaveOutlineAs else Command.SaveOutlineToFile
                    KeyEvent.KEYCODE_O -> return Command.OpenOutlineFile
                    KeyEvent.KEYCODE_N -> return Command.RequestClearOutline
                }
            }
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> return Command.OutlineUp
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.OutlineDown
                KeyEvent.KEYCODE_ENTER -> return Command.OutlineSelect
                KeyEvent.KEYCODE_ESCAPE -> return Command.OverlayCancel
                
                // Setup shortcuts
                KeyEvent.KEYCODE_F -> return Command.OutlineAddFolder
                KeyEvent.KEYCODE_C -> return Command.OutlineAddContainer
                KeyEvent.KEYCODE_D -> return Command.OutlineDelete
                KeyEvent.KEYCODE_L -> return Command.OutlineLinkFile
                KeyEvent.KEYCODE_K -> return Command.OutlineLinkRemote
                KeyEvent.KEYCODE_U -> return Command.OutlineUnlink
                KeyEvent.KEYCODE_R -> return Command.OutlineRename
            }
            if (event.keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_8) {
                return Command.OverlayJump(event.keyCode - KeyEvent.KEYCODE_1)
            }
            return null
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
                KeyEvent.KEYCODE_DPAD_LEFT -> return Command.MoveWordLeft(isShift)
                KeyEvent.KEYCODE_DPAD_RIGHT -> return Command.MoveWordRight(isShift)
                KeyEvent.KEYCODE_DPAD_UP -> return Command.MoveSentenceUp(isShift)
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.MoveSentenceDown(isShift)
                KeyEvent.KEYCODE_Z -> return Command.Redo
                KeyEvent.KEYCODE_T -> return Command.CycleThemeBackwards
                KeyEvent.KEYCODE_B -> return Command.PrevBuffer
                KeyEvent.KEYCODE_K -> return Command.OpenBookmarkMenu
                KeyEvent.KEYCODE_Q -> return Command.OpenFoldMenu
            }
        }

        // Ctrl combinations
        if (isCtrl && !isAlt) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_N -> return Command.RequestClearBuffer
                KeyEvent.KEYCODE_S -> return if (isShift) Command.SaveAs else Command.SaveToFile
                KeyEvent.KEYCODE_W -> return Command.CloseFile
                KeyEvent.KEYCODE_P -> return Command.OpenPdfExport
                KeyEvent.KEYCODE_O -> return Command.OpenFile
                KeyEvent.KEYCODE_A -> return Command.SelectAll
                KeyEvent.KEYCODE_EQUALS -> return Command.IncreaseFontSize
                KeyEvent.KEYCODE_MINUS -> return Command.DecreaseFontSize
                KeyEvent.KEYCODE_T -> return Command.CycleTheme
                KeyEvent.KEYCODE_B -> return Command.NextBuffer
                KeyEvent.KEYCODE_C -> return Command.Copy
                KeyEvent.KEYCODE_X -> return Command.Cut
                KeyEvent.KEYCODE_V -> return Command.Paste
                KeyEvent.KEYCODE_Z -> return Command.Undo
                KeyEvent.KEYCODE_K -> return if (isShift) Command.JumpLastBookmark else Command.AddBookmark
                KeyEvent.KEYCODE_Q -> return Command.CollapseSelection
                KeyEvent.KEYCODE_F -> return Command.OpenFind
                KeyEvent.KEYCODE_R -> return Command.OpenReplace
                KeyEvent.KEYCODE_H -> return Command.OpenHelpMenu
                KeyEvent.KEYCODE_L -> return Command.OpenSettingsMenu
                
                // Paragraph navigation
                KeyEvent.KEYCODE_DPAD_UP -> return Command.MoveParagraphUp(isShift)
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.MoveParagraphDown(isShift)
                KeyEvent.KEYCODE_DPAD_LEFT -> return Command.MoveWordLeft(isShift)
                KeyEvent.KEYCODE_DPAD_RIGHT -> return Command.MoveWordRight(isShift)
            }
        }

        // Alt combinations
        if (isAlt && !isCtrl) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4,
                KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8 -> {
                    return Command.SwitchBuffer(event.keyCode - KeyEvent.KEYCODE_1)
                }
                KeyEvent.KEYCODE_B -> return Command.OpenBufferMenu
                KeyEvent.KEYCODE_M -> return Command.ToggleShowMargins
                KeyEvent.KEYCODE_L -> return Command.OpenLineSpacingMenu
                KeyEvent.KEYCODE_S -> return Command.OpenSplitMenu
                KeyEvent.KEYCODE_N -> return Command.SwapSplitFocus
                KeyEvent.KEYCODE_W -> return Command.OpenWallpaperMenu
                KeyEvent.KEYCODE_X -> return Command.MinimizeApp
                KeyEvent.KEYCODE_R -> return Command.ToggleMarkdownRender
                KeyEvent.KEYCODE_H -> return Command.ToggleStatus
                KeyEvent.KEYCODE_C -> return Command.MoveToViewportCenter
                
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    return if (isVPressed) Command.AdjustVerticalOffset(-5f) else Command.MoveToVisualLineStart(isShift)
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    return if (isVPressed) Command.AdjustVerticalOffset(5f) else Command.MoveToVisualLineEnd(isShift)
                }
                KeyEvent.KEYCODE_DPAD_UP -> return Command.MoveToStart(isShift)
                KeyEvent.KEYCODE_DPAD_DOWN -> return Command.MoveToEnd(isShift)
                KeyEvent.KEYCODE_F4 -> return Command.RequestExit
            }
        }

        if (event.keyCode == KeyEvent.KEYCODE_F1 && isCtrl) {
            return Command.BrightnessDown
        }
        if (event.keyCode == KeyEvent.KEYCODE_F2 && isCtrl) {
            return Command.BrightnessUp
        }

        // Functional keys
        if (event.keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F8) {
            return Command.SwitchBuffer(event.keyCode - KeyEvent.KEYCODE_F1)
        }
        
        if (event.keyCode == KeyEvent.KEYCODE_F10 || event.keyCode == KeyEvent.KEYCODE_MENU) {
            // If already in a menu, don't re-trigger to prevent "flashing"
            if (isOverlayVisible) return null
            return Command.OpenSettingsMenu
        }

        if (event.keyCode == KeyEvent.KEYCODE_I) {
            if (isCtrl) return Command.OpenImageViewer
            if (isAlt) return Command.OpenImageSettings
        }

        if (event.keyCode == KeyEvent.KEYCODE_F11) {
            return if (isCtrl) Command.RecenterTypewriter else Command.ToggleTypewriterMode
        }

        if (event.keyCode == KeyEvent.KEYCODE_F12) {
            return if (isCtrl) Command.OpenOutlineSetup else if (isShift) Command.StartFindCursor else Command.OpenOutlineSelection
        }

        if (event.keyCode == KeyEvent.KEYCODE_F9) {
            return when {
                isCtrl -> Command.FlushScratchpadSave
                isAlt -> Command.FlushScratchpadNoSave
                else -> Command.OpenScratchpad
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
                KeyEvent.KEYCODE_DEL -> return Command.DeleteBackward
                KeyEvent.KEYCODE_ENTER -> return Command.InsertText("\n")
                KeyEvent.KEYCODE_TAB -> return Command.InsertText("    ")
            }
        }

        // Regular text input
        val unicodeChar = event.unicodeChar
        if (unicodeChar != 0 && !isCtrl && !isAlt) {
            return Command.InsertText(unicodeChar.toChar().toString())
        }

        return null
    }
}
