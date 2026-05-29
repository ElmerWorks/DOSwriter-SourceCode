# MonkWrite - Project Summary & Architecture

## 1. Requirements Overview

**MonkWrite** is a distraction-free, hardware-optimized writing environment for Android, inspired by dedicated drafting devices like the Alphasmart Neo and Pomera DM250.

### Core Functional Requirements
*   **Multi-Buffer System**: 8 independent drafting buffers + 1 dedicated **Scratchpad** (F9) with a configurable character limit.
*   **Persistence**: Automatic saving of text, cursor position, selection state, themes, and margins for every buffer across app restarts.
*   **Professional Typography**: Support for 14 monospaced, serif, and sans-serif fonts with adjustable line spacing and font sizes.
*   **Advanced Navigation**:
    *   **Visual Line Movement**: Arrows move by terminal (wrapped) lines.
    *   **Semantic Jumps**: Jump by Word (Ctrl), Sentence (Ctrl+Alt), or Paragraph (Ctrl).
    *   **Boundary Jumps**: Jump to Line Start/End (Alt+Left/Right) or File Start/End (Alt+Up/Down).
    *   **Viewport Tuning**: Manual 5px vertical offset adjustment (Alt+V) to clear physical screen margins.
*   **Productivity Tools**:
    *   **Undo/Redo**: 100-level deep history stack per buffer.
    *   **Find/Replace**: Search within the current buffer or across all 8 buffers simultaneously.
    *   **Bookmarks**: Up to 8 bookmarks per buffer for quick navigation.
    *   **Folding**: Collapse selected text into a `[+]` indicator to manage long drafts.
*   **Split-Screen Mode**: Side-by-side comparison/editing of any two buffers (Alt+S) with independent focus (Alt+N).
*   **File I/O**:
    *   Import/Export plain text (`.txt`) via Android Storage Access Framework.
    *   Compile multiple buffers into a single paginated **PDF** document.
    *   Automatic "Flush" of Scratchpad notes to a localized log file.

### UI/UX Requirements
*   **Immersive Mode**: Full-screen writing with hidden status/navigation bars.
*   **High-Visibility Cursor**: Find cursor function (F12) that blinks the current character space.
*   **Dynamic Theming**: 20+ color schemes with automated Day/Night switching based on user-defined schedules.
*   **Show Margin**: Visual depth effect that shades margins based on background luminance.

---

## 2. Technical Architecture

The project follows a modified **Model-View-Intent (MVI)** pattern, optimized for low-latency Canvas rendering and physical keyboard handling.

### A. The Model (`EditorBuffer.kt`)
The "Brain" of the application.
*   **Text Engine**: Uses a `StringBuilder` for the content and a `HistoryState` stack for Undo/Redo.
*   **Prefix Logic**: Implements a non-deletable 2-line vertical spacer (`\n\n`) to ensure the first line of text is always visible below the top margin.
*   **Coordinate Math**: Translates character indices to logical "Word Positions" and "Word Counts" for the status bar.

### B. The View (`EditorView.kt`)
A high-performance custom `View` component.
*   **Canvas Rendering**: Draws text manually via `drawText`. This allows for features impossible with standard Android widgets, such as the 20% luminance-based margin masks and custom cursor shapes.
*   **Smart Word-Wrap**: A look-ahead algorithm that keeps words whole and wraps them to the next line based on the dynamic `sideMargin` and `charWidth`.
*   **Viewport Logic**: Calculates the "Perfect Frame"—automatically adjusting vertical padding to ensure only whole lines of text are visible at the top and bottom of the screen.

### C. The Controller Layer
*   **`MainActivity.kt`**: The orchestrator. Handles system-level events, theme scheduling, SAF file picking, and manages the dual-editor view for Split-Screen mode.
*   **`BufferManager.kt`**: Handles persistence via `SharedPreferences`. It serializes the state of all 9 buffers into JSON-like strings and coordinates the "Global Apply" settings feature.
*   **`KeybindingManager.kt`**: A semantic mapper. It intercepts raw `KeyEvent` codes and translates them into high-level `Command` objects (e.g., `KeyEvent.KEYCODE_W` + `Ctrl` → `Command.CloseFile`).
*   **`CommandDispatcher.kt`**: Executes the commands. It decouples the UI buttons/keys from the underlying buffer and view logic, allowing the same logic to work for both touch and keyboard inputs.

### D. Overlay System
To maintain a distraction-free "Zen" environment, the app avoids standard Android Menus/Toolbars.
*   **Custom Overlays**: `SelectionOverlay`, `InputOverlay`, and `SearchOverlay` are drawn directly over the editor. They support both keyboard navigation (Arrows/Enter) and mouse/touch interaction (Click-to-Select).

---

## 3. Compatibility & Standards
*   **Minimum Version**: Android 6.0 (Marshmallow / API 23) to support legacy e-ink and writing tablets.
*   **Target Version**: Android 15 (API 35).
*   **Input**: Optimized for Bluetooth/USB hardware keyboards and mice/trackballs.
*   **Performance**: Rendering is O(V) where V is the number of visible characters, ensuring 60fps responsiveness even in large files.
