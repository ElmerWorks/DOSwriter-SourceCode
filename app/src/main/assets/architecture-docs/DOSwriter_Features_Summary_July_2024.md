# DOSwriter Feature & Command Summary (July 2024)

This document provides a comprehensive list of features and keyboard commands added or significantly enhanced over the past 3 weeks.

---

## 1. Physical Manuscript Engine
A professional layout engine that simulates physical paper pages independent of screen resolution.

- **Dual View Modes**: Choose between **Layout View** (Grid) and **Filmstrip View** (Continuous).
- **Configurable Grid**: Supports 4 or 8 page tile layouts (`Settings > Manuscript View`).
- **Top-Aligned Navigation**: Pressing **Enter** on a page in the viewer jumps the editor to that paragraph and pins it to the top margin of the screen.
- **WYSWYG Export**: PDF Export now uses the physical engine to ensure exact visual matching.

## 2. Context-Aware Macro Library
The macro system now adapts to the type of file you are editing.

- **Auto-Context**: Detects `.md` or `.txt` and loads the appropriate context (Markdown vs Text).
- **Sticky Picker**: The picker menu stays open for multiple pastes until **ESC** is pressed.
- **Instant Paste**: Insert your last used macro immediately without menus.

## 3. High-Performance Document Folding
Advanced tools for managing large manuscripts and long outlines.

- **Expansion Stack**: The app now remembers a history of the last **8 sections** you expanded.
- **Recollapse Logic**: Quickly close sections you just edited using a single shortcut.
- **Mass Collapse**: Fold every paragraph in a document instantly to see a "birds-eye" outline.

## 4. Professional Typography & UI
- **Embedded Noto Suite**: 15 professional weights (Sans, Serif, and Mono) are now built into the app APK.
- **Stationary Selection**: In long lists (Fonts/Macros), the bar stays fixed while the list scrolls through it, reducing eye fatigue on E-ink.
- **4-Line Font Preview**: A large preview box shows character detail, numerals, and word flow for the highlighted font.
- **Help Task-Switching**: Help is now a "background process" that remembers your last viewed topic and scroll position.

## 5. Vietnamese TELEX Engine
- **Stages 1-3 Complete**: Full support for base character conversion (`aa` -> `â`) and intelligent tone placement in complex syllables (`truowngf` -> `trường`).
- **Decoupled Architecture**: Integrated into DOSwriter without interfering with standard editing commands.

---

## 6. Keyboard Command Reference

| Function | Command | Shortcut |
| :--- | :--- | :--- |
| **Help Task-Switch** | Toggle Help (Background) | `Shift + Tab` |
| **Manuscript Viewer** | Choose Grid/Filmstrip View | `CTRL + Q` |
| **Hide/Show Margins** | Toggle visual margins | `CTRL + SHIFT + M` |
| **Recollapse Fold** | Zip up the last expanded section | `ALT + J` |
| **Collapse All** | Fold all paragraphs in buffer | `CTRL + SHIFT + J` |
| **Cycle Buffers** | Rotate forward through Buffers 1-8 | `CTRL + B` |
| **Cycle Buffers Back** | Rotate backward through Buffers 1-8 | `CTRL + ALT + B` |
| **Buffer Menu** | Select a specific buffer from list | `ALT + B` |
| **Center Cursor** | Re-center screen & trigger locator blink | `ALT + C` |
| **Find Cursor** | Trigger locator blink only | `CTRL + ALT + F` |
| **Macro Picker** | Open sticky macro menu | `CTRL + ALT + M` |
| **Macro Console** | Manage contexts and definitions | `ALT + M` |
| **PDF Navigation** | Jump to start/end of document | `ALT + Up/Down` |
| **PDF Page Turn** | Next/Previous page | `N / P` (or Arrows) |

---

## 7. New Settings Options

### Settings > Cursor
- **Style**: Preview active style (Block, Caret, etc.) directly in the menu.
- **Locator Blink Duration**: Set duration of the `ALT+C` visual "ping."

### Settings > Text Display > Menus and Help
- **Help Task-Switch**: Enable or disable the `Shift+Tab` background toggle.

### Settings > Manuscript View
- **Layout Grid Tiles**: Switch between 4 and 8 page overview modes.

### Settings > Dev (Hidden/Power User)
- **External Font Scan**: Toggle scanning of linked folders (Default: OFF).
- **Pattern Collapse Limit**: Set the size limit for automated background folding.
