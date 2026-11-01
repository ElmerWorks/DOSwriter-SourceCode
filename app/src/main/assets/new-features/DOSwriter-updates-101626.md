# DOSwriter Development & Architectural Update Summary
**October 16, 2026**

This document summarizes the significant architectural improvements, trackball/mouse integration features, bug fixes, and performance enhancements integrated into DOSwriter between August 19, 2026 and October 16, 2026.

---

## 1. Multi-Buffer & Workspace Stability (`.epws` / To-Do Mode)
- **Safe Index Mapping**: Resolved a critical startup crash (`ArrayIndexOutOfBoundsException`) when launching the application with To-Do Workspace (`ALT+W`) enabled.
- **Global Buffer Iteration**: Refactored global iteration loops in `Initializer.kt`, `HardwareHandler.kt`, `MenuManager.kt`, `OverlayHandler.kt`, and `SettingsMenuHandler.kt` to use `bufferManager.getAllBuffers()` and `getBufferAbsolute()`.
- **OutOfBounds Protection**: Implemented `getBufferAt()` bounds coercion to guarantee array bounds safety across all 17 buffers (8 Main + 8 To-Do + 1 Scratchpad).

---

## 2. Trackball & Mouse Interaction Subsystem
- **Right-Click Context Menu**: Replaced native Android `PopupMenu` with DOSwriter's high-contrast `selectionOverlay` containing **Cut**, **Copy**, **Paste**, **Select All**, and **Trackball & Mouse Options**.
- **Touch & Click Target Geometry**: Corrected a vertical offset calculation in `SelectionOverlay.kt`, resolving off-by-one item selection errors when clicking with a mouse or trackball.
- **Scroll Wheel Engine**: Added configurable **Scroll Wheel Mode** (`ALT-S > Advanced Features > Trackball & Mouse` or Right-Click):
  - **Lines Mode**: Configurable line jump per notch (1, 2, 3, 5, or 10 lines).
  - **Page Jump Mode**: Full viewport page scroll per notch ($\text{Screen Height} - 40\text{px}$) calculated dynamically based on active font and line height.
- **Left-Click & Drag Text Selection**: Re-engineered left mouse/trackball button dragging to perform Windows/Linux-style text selection (setting selection anchor on click-down and expanding range on drag-move).
- **CTRL + Left-Click Viewport Pan**: Holding `CTRL` while clicking and dragging with the left mouse/trackball button pans/scrolls the text viewport.

---

## 3. Buffer Layout View (`Ctrl+Alt+B` / `Esc`) & Thumbnail System
- **Stateful View Modes**:
  - **Preview Mode (`CTRL+P`)**: Displays live buffer text sample snapshots.
  - **Image Mode (`CTRL+I`)**: Displays thumbnail images (with optional text overlay when `Overlay Text on Image` is ON).
  - **Thumbnail Text Mode (`CTRL+T`)**: Displays Filename or Defined Thumbnail Text.
- **Use Filename Option**: `ALT-S > Images > Buffer Thumbnail > Thumbnail Text > Use Filename` dynamically displays buffer filenames (`filename`, `filename*`, `EMPTY`, `EMPTY*`) as text thumbnails and overlays.
- **Text Snapshot Preview Fix**: Fixed a chunk bounds calculation bug where short, unsaved buffer drafts displayed `"[Start of File]"` instead of rendering actual text.
- **Keyboard Lockdown**: Enforced strict input filtering in Buffer Layout View so only valid commands (`Esc`, `ALT-I`, `ALT-P`, `ALT-T`, `1-8`, `F1-F8`, Arrows, `Enter`, `CTRL-ALT-B`) are processed, swallowing accidental keystrokes.

---

## 4. Scratchpad Logging & Working Folder Integration
- **Working Folder Logging**: Refactored `flushScratchpad()` to append `scratchlog.txt` directly inside the user's active File Manager Working Folder using Android SAF `DocumentFile`.
- **First-Flush Folder Prompt**: Prompts to link a Working Folder via the Android file browser if no Working Folder is currently set.
- **Timestamped Log Headers**: Appends human-readable timestamp headers before logged text entries (`--- MMddyyyy hh:mma`).
- **Immediate Screen Clearing**: Added `EditorView` invalidation on Scratchpad flush (`CTRL+F9` / `CTRL+9`) so text immediately clears from the screen upon saving.
- **Status Bar Clean Format**: Status bar displays `ScratchPad : scratchlog.txt` when Scratchpad buffer (`F9`) is active.

---

## 5. File Manager & Settings UI Refinements
- **Settings Navigation Fix**: Corrected `Settings > Advanced Features > Back` navigation to return to the main `Settings` menu (`ALT-S`).
- **Settings Image Menu**: Added **`Images`** directly to the main `ALT-S` Settings menu (`Settings > Images`).
- **Working Folders UI**: Updated title to **`WORKING FOLDERS (DEL TO UNLINK)`** and added a `"Back"` option returning to `Settings > File Manager`.

---

## 6. Performance Guidance & Large File Handling
- **First-Run Installation Advisory**: Added a **Performance Note** popup in Step 3/4 of the First-Run Setup Wizard advising:
  > *"50KB - 100KB (~10,000 - 20,000 words) recommended per buffer for peak performance."*
- **Configurable Large File Warning**: Added **`Large File Warning`** setting in `Settings > Advanced Features > Dev` (`50 KB`, `100 KB`, `250 KB`, `OFF`). Displays a notification toast when opening files exceeding the threshold.
- **Splash Popup Suppression**: Suppressed the yellow startup helper splash message (`"DOSwriter\nMenus : ALT-F\nHelp  : ALT-H"`) during initial installation setup wizard.

---

## 7. Build System, Signing & Device Compatibility
- **Signed Release & Debug Builds**: Configured `signingConfigs` in `app/build.gradle.kts` so release builds output signed, installable APKs.
- **Motorola / Android 11 Side-Loading Fix**: Incremented `versionCode = 2` and `versionName = "1.1"` to prevent package update/signature conflicts on Motorola Moto G Power devices.
- **ProGuard / R8 Optimization**: Added keep rules in `proguard-rules.pro` for `com.example.doswriter.**` model/logic classes and Onyx SDK reflection.
