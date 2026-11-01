# DOSwriter Development Summary
**Week Ending August 6, 2026**

This document summarizes the significant architectural improvements and new features integrated into DOSwriter during the past week.

---

## 1. Architectural Modernization
- **Modularization**: Successfully decomposed monolithic `MainActivity.kt` and `EditorView.kt` into a package-based architecture (`com.example.doswriter.ui` and `com.example.doswriter.editor`).
- **Handler System**: Extracted specific logic into dedicated handlers (e.g., `HardwareHandler`, `FileActionHandler`, `SettingsMenuHandler`) to improve maintainability and build speed.
- **Min SDK Compatibility**: Verified and refined all new UI components for Android 6.0 (API 23) compatibility, specifically optimizing for E-ink hardware constraints.

## 2. Typographic & Font System
- **CPL-Based Font Presets**: Replaced arbitrary font sizing with a "Characters Per Line" (CPL) paradigm. Presets include **Pocket** (45), **Compact** (60), **Standard** (75), **Comfortable** (90), and **Manuscript** (100).
- **Hardware Profiles**: Introduced Device Screen Profiles (**Phone**, **Tablet**, **Custom/E-ink**) that automatically apply density multipliers to font presets for optimal legibility.
- **Intelligent Recalculation**: The system now automatically adjusts font size when switching typefaces or rotating the screen to maintain the user's preferred CPL target.
- **Font Control**: 
  - `CTRL + ALT + B`: Instantly cycles through all installed and custom fonts.
  - `CTRL + ALT + =`: Quick-access to the Font Preset menu.

## 3. Buffer Workspace & Thumbnails
- **Persistent Thumbnails**: Implemented a visual thumbnail system for the 8+1 buffer workspace. Thumbnails are persistent and remain linked even when buffers are cleared or files are swapped.
- **Custom Text Thumbnails**: Users can now define a 48-character string (e.g., "Chapter 1 Outline") to serve as a high-contrast text thumbnail.
- **Global Image Option**: A single background image can now be applied to all buffer layout tiles simultaneously.
- **Layout Enhancements (`Esc` or `CTRL+ALT+B`)**:
  - **Gesture Support**: Added finger-tap selection to buffer tiles.
  - **Mode Toggles**: `CTRL+P` (Preview), `CTRL+T` (Image), and `ALT+T` (Text) allow instant mode-switching of all buffer tiles.
  - **High-Contrast Labels**: Labels now show buffer numbers, filenames, and status indicators (`*` for dirty, `[S]` for synced).

## 4. Enhanced Help & Markdown System
- **Markdown Tables**: The integrated Help system and the editor's rendered view (`ALT+R`) now fully support standard Markdown tables (`| cell |`).
- **Image Sizing**: Added a custom Markdown parameter `|WIDTH` (e.g., `![desc](path|50)`) to allow precise control over image scaling and centering in instructions.
- **Instructional Guides**: Integrated a new **"How to Use To-Do List (PDA)"** guide featuring high-contrast folding diagrams.

## 5. UI/UX Refinements
- **Search History**: Added a FIFO history (8 entries) to both Find and Replace fields. Users can cycle through recent queries using **UP/DOWN** arrows.
- **High-Contrast Markers**: 
  - Replaced the simple `+` and `▶` markers with high-contrast, themed `[+]` and `[BK]` icons.
  - Added **dimmed text previews** to the right of bookmarks and folds for immediate context.
- **Hotkey Logic**: 
  - `Esc` in normal mode now opens the Buffer Layout View.
  - Exit prompt is now protected and requires `CTRL+ALT+X` or the File Menu.
- **Virtual Keyboard**: Added the **⎈L** (CTRL Lock) sticky key to the Alpha layout to facilitate paragraph navigation and text selection without physical modifiers.

## 6. To-Do Mode (PDA) Improvements
- **Contextual Awareness**: `ALT+W` (Workspace Menu) now dynamically labels the available toggle based on the current mode ("Main Mode" vs "ToDo Mode").
- **Asset Integration**: `CTRL+E` (Examples) now automatically filters for task-specific examples when in To-Do mode.
- **Pre-population**: Buffers 1-8 are now automatically initialized with the standard `todo1.txt` - `todo8.txt` templates on the first launch.

## 7. Stability & Performance
- **Lifecycle Protection**: Implemented state-saving for the thumbnail picker to prevent app-restarts on low-RAM devices (e.g., Moto G).
- **Scripting Engine**:
  - Fixed scoped storage issues for absolute image paths.
  - Automated toast suppression during active script runs to prevent system UI lag.
