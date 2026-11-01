# DOSwriter Major Functional Updates (July 2024)

This document summarizes the significant features and keyboard command updates implemented over the past two weeks.

---

## 1. Manuscript Workstation (Physical Layout)
DOSwriter now includes a line-by-line physical pagination engine that simulates how text will look on printed paper, independent of screen pixels.

*   **New Viewer (`CTRL+Q`)**: Opens a menu to choose between **Layout View** (grid overview) or **Filmstrip View** (single page scrolling).
*   **Grid Scaling**: Layout View now supports either a **4-page** or **8-page** grid (configurable in `Settings > Manuscript View`).
*   **Editor Sync**: Pressing **Enter** on any page in the viewer jumps the editor to that exact paragraph and uses "Scroll-to-Top" logic to pin the selected text to the top of the viewport.
*   **WYSWYG Export**: The **PDF Export (`CTRL+P`)** now uses the same physical engine as the viewer, ensuring the printed document matches your preview.

## 2. Intelligent Macro System
The macro system was upgraded from a single list to a context-aware library.

*   **Context Auto-Switching**: The app detects your file type. If editing a `.md` file, it uses the **Markdown** context; otherwise, it defaults to **Text**.
*   **Sticky Fast-Paste (`CTRL+M`)**: The macro picker remains open after you paste a selection. This allows you to rapidly sequence multiple macros without reopening the menu. Press **ESC** to exit.
*   **Paste Last Macro (`CTRL+SHIFT+M`)**: Instantly inserts the most recently used macro at the cursor position without opening any menus.
*   **Macro Management (`CTRL+ALT+M`)**: Opens the console to define new strings or create new named Contexts (e.g., "Code", "Research").

## 3. Advanced Document Folding (Collapse)
Tools for structural outlining and managing 50KB+ documents.

*   **Recollapse Last Expansion (`ALT+J`)**: If you expand a folded section to edit it, `ALT+J` will instantly "zip" that specific section back up.
*   **Collapse All Paragraphs (`CTRL+SHIFT+J`)**: Instantly collapses every paragraph in the buffer, leaving only the first line/sentence visible for quick outlining.
*   **String Pattern Manager (`CTRL+J`)**: Manages a FIFO list of 8 string patterns that automatically fold the document.
*   **Performance Safety**: Added a **Pattern Collapse Limit** (`Settings > Dev`) to prevent lag on massive files.

## 4. Font & Visual Ergonomics
*   **Embedded Noto Sans Mono**: Five professional weights (**Light, Regular, Medium, Bold, Black**) are now built into the app (prefixed with `NSM-`).
*   **Stationary Selection Menus**: In Font and Macro menus, the selection bar stays fixed at Row 1 while the list scrolls through it—designed to reduce eye strain.
*   **Multi-Line Font Preview**: The Font Selection menu now features a large 4-line preview box showing character distinctness, numbers, symbols, and word flow for the highlighted font.
*   **Cursor Locator Blink**: Using **ALT+C** (Center Cursor) now triggers a rapid "ping" blink for a set duration (default 3s).

---

## 5. Updated Keyboard Command Reference

| Command | Shortcut | Description |
| :--- | :--- | :--- |
| **Manuscript Viewer** | `CTRL + Q` | Toggle Layout (Grid) or Filmstrip view. |
| **Cycle Buffers** | `CTRL + B` | Quickly rotate through buffers 1-8. |
| **Buffer Menu** | `ALT + B` | Open the full list of buffers to pick a specific one. |
| **Paste Last Macro** | `CTRL + SHIFT + M` | Instantly paste the last used macro. |
| **Recollapse Fold** | `ALT + J` | Recollapse the most recently expanded text section. |
| **Collapse All** | `CTRL + SHIFT + J` | Collapse all paragraphs in the current buffer. |
| **Find Cursor** | `CTRL + ALT + F` | Blink the cursor rapidly to locate it. |
| **Center Cursor** | `ALT + C` | Center text on cursor and trigger Locator Blink. |
| **Macro Picker** | `CTRL + M` | Open sticky macro list (remains open for multiple pastes). |
| **Macro Console** | `CTRL + ALT + M` | Define macros and manage contexts. |
| **Screenshot** | `CTRL + I` | Capture the screen and save to the Pictures folder. |

---

## 6. New Settings Entries
*   **Settings > Manuscript View > Layout Grid Tiles**: Toggle 4 or 8 page view.
*   **Settings > Cursor > Locator Blink Duration**: Set how long the cursor "pings" after recentering.
*   **Settings > Dev > External Font Scan**: Toggle scanning of linked folders (default OFF).
*   **Settings > Dev > Pattern Collapse Limit**: Set the max file size for automated folding.
