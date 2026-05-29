# MonkWrite - Full Development Chat Log

This document contains a chronological record of the development session for the **MonkWrite** text editor, capturing all feature requests and technical implementations.

---

### **Turn 1: Find Cursor Implementation**
**User**: Implement a find cursor function. Use Alt-c to activate (I will update center cursor later). When Alt-c is pressed and held, the character text space at the cursor location blanks out (to the same color as the font color) and blinks on/off until Alt-c is released.
**Assistant**: Implemented `startFindCursor` and `stopFindCursor` in `EditorView.kt` using a `blinkRunnable`. Updated `onDraw` to render the blinking block. Added `StartFindCursor` and `StopFindCursor` commands.

### **Turn 2: Hardware Conflict & F12 Remapping**
**User**: Alt-c did not work for highlighting the cursor. It also had problems with the center function. Perhaps Alt-c is a problem with my keyboard, not your code. Map F12 to find Cursor function to test this.
**Assistant**: Remapped Find Cursor to `F12` (hold to blink) and restored `Alt + C` as a dedicated "Jump to Viewport Center" command. Updated documentation.

### **Turn 3: Theme Cycle Feedback**
**User**: For Ctrl-t theme cycle, display theme names while cycling, similar to how filenames are displayed during buffer change.
**Assistant**: Updated `MainActivity.kt` to trigger a `Toast` message displaying the theme name and mode (Day/Night) during cycling.

### **Turn 4: Vertical Viewport Tuning**
**User**: Sometimes the viewport/margin alignment is a little off and shows partial lines... Fix viewport alignment... implement Adjust Vertical Viewport function Alt-v + left/right arrow adjusts by 5px.
**Assistant**: Added `verticalOffset` property to `EditorBuffer`. Implemented `Alt + V` + `Arrows` to nudge the viewport. Fixed `Alt + C` math to account for the offset.

### **Turn 5: Theme Adjustments (Yellow Moon)**
**User**: Remove theme "Green Console 1". Add new theme "Yellow Moon", Background RGB : 13, 13, 13, Font RGB : 242, 226, 5.
**Assistant**: Swapped the themes in `MainActivity.kt` and updated the instruction manual.

### **Turn 6: Theme Cleanup**
**User**: Remove theme "Deep Sea".
**Assistant**: Removed the "Deep Sea" theme from the list and documentation.

### **Turn 7: Build Error Recovery (XML)**
**User**: Not sure what error I caused with XML file when I added an icon .png. Let me know if I need to re-add the logo or how to fix error.
**Assistant**: Identified the issue as having comments before the `<?xml` declaration. Fixed `ic_launcher_background.xml` and adaptive icon files.

### **Turn 8: Rebranding to "MonkWrite"**
**User**: Yes I want to use the MonkWriteLogo.png as the app icon. I also want to change the name of the app to MonkWrite. Prepare all assets for Play Store... Remove debug information...
**Assistant**: Renamed the app in `strings.xml`. Migrated logo to `mipmap` folders. Enabled code minification/shrinking in `build.gradle.kts`.

### **Turn 9: Smart Word Wrapping & Custom Margins**
**User**: Margin adjustment works as desired but needs improvement... For words that extend past the margin... keep them whole and display them on the next line. Also consider adding a "custom" margin option...
**Assistant**: Implemented whole-word wrapping logic in `EditorView`. Added "Custom..." input option to the margin selection menus.

### **Turn 10: Wireless Debugging Setup**
**User**: I want to debug over wifi instead of installing an apk. How do I do that after pairing my device?
**Assistant**: Provided guidance on enabling Wireless Debugging in Developer Options and connecting via Android Studio.

### **Turn 11: "App" Icon/Run Button Issue**
**User**: I think everything is correct and my device is paired but only the "app" icon appears where the Run icon usually is.
**Assistant**: Performed a remote deploy via ADB to verify the connection. Advised on Android Studio UI layouts.

### **Turn 12: Scratchpad Feature**
**User**: Add a Scratchpad buffer : F9 brings up scratchpad buffer with a user-defined character limit (default 280 characters). Ctrl-F9 flushes... Alt-F9 flushes without saving... Ctrl-Alt-F9 for options.
**Assistant**: Added a 9th buffer for the Scratchpad. Implemented character limit enforcement and file-append logic for "flushing" notes.

### **Turn 13: Global Sync & Startup Defaults**
**User**: Make Global Apply Settings apply to scratchpad buffer also. The default theme... will be Wheat. Default margins... will be 60. Clear the buffers for each new build...
**Assistant**: Updated `BufferManager` defaults. Expanded "Global Apply" to all 9 buffers. Implemented a one-time `wipeAllData()` for the next build.

### **Turn 14: Persistence & Flush Fixes**
**User**: The scratchpad works but Ctrl-F9 doesn't clear the buffer after appending to log file... Make sure buffer contents are persistent after closing the app.
**Assistant**: Fixed UI refresh bug during flush. Corrected persistence logic to only wipe data on the absolute first run, preserving it for all subsequent restarts.

### **Turn 15: Vertical Padding Spacers**
**User**: Add 4 new line characters at the beginning of the [scratchpad] buffer... Constrain font size so the scratch buffer limit and extra newline characters stays within the viewing area.
**Assistant**: Implemented non-deletable `prefix` spacers. Added font-size calculation in `MainActivity` to prevent clipping in the Scratchpad.

### **Turn 16: PDF Export Tool**
**User**: If possible add save buffers to pdf function, Ctrl-P. Bring up menu with clickboxes or buffer selection to compile 1 up to 8 buffers into a single PDF.
**Assistant**: Created `MultiSelectionOverlay`. Integrated Android's `PdfDocument` API to compile drafts with pagination and word-wrapping.

### **Turn 17: Universal Spacers**
**User**: Let's try adding 2 newlines to every buffer when empty or prepending 2 newlines when a file loads... remove the extra newlines before saving.
**Assistant**: Added a universal 2-line spacer to all 9 buffers. Updated `getCleanText()` to strip these spacers during file exports and PDF generation.

### **Turn 18: Terminal Line Navigation Overhaul**
**User**: Modify arrow behavior... Up/Down arrow jump to previous/next terminal line... Shift-Alt-Up/Down jump up/down and highlight one terminal line... Ctrl-Alt-Up/Down jump to previous/next sentence.
**Assistant**: Implemented visual-line coordinate mapping in `EditorView`. Added `moveSentence` logic to `EditorBuffer`. Overhauled navigation shortcuts.

### **Turn 19: Performance & Scrolling Fix**
**User**: The up/down arrow single line jump behavior is correct except the text does not scroll... Also there is slight latency... Try to optimize.
**Assistant**: Optimized `onDraw` and coordinate lookup algorithms. Added `shouldScrollToCursor` trigger to the visual line movement logic.

### **Turn 20: Close File Function**
**User**: Add close file function with Ctrl-w. Close the current buffer if saved. If not saved query user to save or don't save.
**Assistant**: Implemented `isDirty` state tracking. Added `CloseFile` command and confirmation overlay logic.

### **Turn 21: Show Margins Visual Mask**
**User**: Create a Show Margins function... Show margin by changing its RGB value... 20% deeper shade for light, 20% lighter for dark.
**Assistant**: Implemented high-performance alpha masking in `EditorView` that automatically adapts based on background luminance. Added menu toggle.

### **Turn 22: Internal Margin Padding**
**User**: When showing margins add 2 character spaces to the side margins to provide small amount of space at edges.
**Assistant**: Added dynamic `effectiveSideMargin` calculations based on `charWidth`. Coordinated click-detection and navigation math to respect the new padding.

### **Turn 23: Cursor and Selection Persistence**
**User**: The cursor position should be persistent for all buffers... lost sometimes when I change buffer or if I shut the app down.
**Assistant**: Expanded `BufferManager` to save/restore `cursorPosition` and `selectionAnchor` for every buffer across sessions.

### **Turn 24: Status Bar Metrics (Words)**
**User**: Change the Status bar message. Instead of character count show : filename|Pos:word position|Words:word count.
**Assistant**: Implemented word-counting and word-position algorithms in `EditorBuffer`. Updated the status bar string format.

### **Turn 25: Updated Launcher Branding**
**User**: Update the app icon with MonkWriteLogo.png. Update the app launcher icon with MonkWriteLogo Launcher.png.
**Assistant**: Migrated new PNG assets and updated `AndroidManifest.xml` to distinguish between the primary launcher icon and the system round icon.

### **Turn 26: SM-T580 Compatibility Fix**
**User**: The debug apk... fails to launch on a Samsung SM-T580 tablet.
**Assistant**: Lowered `minSdk` to 23 (Android 6.0). Swapped modern insets logic for `WindowInsetsControllerCompat` to ensure stable launches on legacy hardware.

### **Turn 27: Markdown Loading & Rendering**
**User**: Add function to load and edit an .md markdown file... render the markdown output with Alt-r. Use Esc to return to text edit mode.
**Assistant**: Implemented `isMarkdownMode`. Added a manual Markdown Canvas renderer for Headers, Lists, and Bold text. Supported dual-view (raw vs rendered) in Split-Screen.

### **Turn 28: PDF Multi-Page Reference**
**User**: Add a function to display (render) a PDF file if opened with Ctrl-o. Allow scrolling if it is a multi-page PDF document.
**Assistant**: Integrated `PdfRenderer` into the buffer view. Added page caching and independent scrolling for PDF reference buffers.

### **Turn 29: Font Selection Recovery**
**User**: Font selection appears to have broken... lists Monotype, Serif, and Sans Serif but nothing happens.
**Assistant**: Fixed case-sensitivity in font mapping. Added Mouse/Touch interaction support to all overlays (Click-to-Select).

### **Turn 30: Final Font Library Expansion**
**User**: Previous version had these fonts... Update the font selection with missing fonts (Monospace, Courier New, Serif, Sans-serif, Consolas, Lucida Console, Roboto Mono, Times New Roman, Georgia, Arial, Helvetica, Verdana, Trebuchet, Noto Sans).
**Assistant**: Expanded the font list to 14 choices and implemented safe fallbacks for typefaces not native to the Android system.

### **Turn 31: App Exit Confirmation**
**User**: Provide app exit function. Use Alt-F4 and Esc to exit app when in text editing mode. Query user if they want to exit.
**Assistant**: Implemented `RequestExit` command with a "Yes/No" selection prompt. Added `Ctrl + Alt + X` as a safe fallback for hardware remapping issues.

### **Turn 32: Minimize Function**
**User**: Add Alt-x function to minimize the app without closing it.
**Assistant**: Implemented `Alt + X` to trigger `moveTaskToBack(true)`, allowing users to switch apps without losing their drafting context.
