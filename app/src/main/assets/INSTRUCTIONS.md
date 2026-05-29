# ElmerPad - User Instructions

ElmerPad is a minimalist distraction-free Android writing environment inspired by dedicated writing devices and keyboard-centric workflows such as the Alphasmart Neo and DOS/Unix text editing tools. It is designed for fast, keyboard-driven writing with a focus on stability and simplicity. The keyboard workflow is intuitive and easy to learn.

## Key Features

- Immersive Mode: Full-screen writing with no system distractions.
- 8 Independent Buffers: Work on multiple drafts simultaneously.
- Scratchpad Buffer: Character-limited buffer with fast save or purge.
- Color Themes: Classic and e-ink color themes. Custom user themes.
- Split-Screen Mode: View and edit any pair of buffers side-by-side.
- View Control: Adjustable fonts and margins; toggle margin visibility.
- Collapsible Text Mode: Compress sections of text to icons.
- Bookmarks: Mark specific text positions and jump to them instantly.
- Persistent Storage: Text, cursor positions, and settings are automatically saved.
- Outline Manager: Organize your project into folders and linked files.
- Typewriter Mode: Keep your focus fixed on a single line.
- Multimedia Support: Display images and reference PDFs side-by-side with your text.
- Code Support: Syntax highlighting for HTML and CSS.
- Desktop Sync: Real-time synchronization with a Go-based host server, featuring auto-reload on remote edits and background auto-save.

## Menus

- Buffers (Alt + B): Select named buffers 1-8.
- Bookmarks (Ctrl + Alt + K): Select from bookmark list.
- Settings (F10 or Ctrl + L): Consolidated menu for Cursor, Font, Margin, Theme, Scratchpad, and Desktop Sync settings.
- Outline Manager (F12): Select from file list, add folders and files.

## Keyboard & Mouse Shortcuts

Buffer & Project Management
- Alt + 1 to Alt + 8 OR F1 to F8: Switch to the corresponding buffer instantly.
- Ctrl + B: Cycle forward through buffers 1-9.
- Ctrl + Alt + B: Cycle backward through buffers 1-9.
- Alt + B: Open the Buffer Selection Menu (shows filenames).
- F12: Open Outline Manager Selection screen.
- Ctrl + F12: Open Outline Manager Setup screen.
- F10 or Ctrl + L: Open Settings Menu (Cursor, Fonts, Margins, Themes, Scratchpad).
- F9: Open Scratchpad buffer (with char limit).
- Ctrl + F9: Flush Scratchpad to log file and clear.
- Alt + F9: Flush Scratchpad without saving.
- Alt + S: Open the Split Screen Menu.
- Alt + N: Swap focus between panels in Split Mode.
- Alt + R: Toggle Markdown Render mode (for `.md` files).
- Alt + W: Set device wallpaper to solid color.
- Ctrl + I: Open Image Viewer Selection menu.
- Alt + I: Open Image Viewer Settings (linked folders).
- F11: Toggle Typewriter Mode (Fixed-line scrolling).
- Ctrl + F11: Recenter Typewriter focus at current cursor.
- Alt + X: Minimize app (Move to background).
- Alt + F4 / Ctrl + Alt + X / Esc: Exit app (Prompts for confirmation).
- Ctrl + N: Clear the current buffer (Prompts for confirmation).

File Operations
- Ctrl + S: Save current buffer to file.
- Ctrl + O: Open file (.txt, .md, .html, .css, .jpg, .pdf).
- Ctrl + W: Close the current buffer (Prompts to save if modified).
- Ctrl + Shift + S: Save current buffer as a new file.
- Ctrl + P: Export selected buffers to a single PDF file.

Navigation & Editing
- Arrow Keys: Move by character or Terminal Line.
- Shift + Alt + Up/Down: Select (Highlight) text by terminal line.
- Ctrl + Alt + Up/Down: Jump to start of previous/next Sentence.
- Ctrl + Up/Down: Jump to start of previous/next Paragraph.
- Ctrl + Left/Right: Jump to previous/next Word.
- Alt + Left/Right: Jump to beginning/end of line.
- Alt + Up/Down: Jump to beginning/end of buffer.
- Alt + C: Jump to center of viewport.
- Alt + V + Left/Right: Adjust vertical viewport offset (5px steps).
- Shift + F12: Find cursor (Hold to blink cursor location).
- Mouse Wheel: Scroll text (or Zoom if an image is loaded).
- Left/Right Arrows (in Viewer): Move to previous/next image.
- Pinch Gesture: Zoom in/out on images.
- Ctrl + C / X / V: Copy / Cut / Paste.
- Ctrl + Z / Ctrl + Alt + Z: Undo / Redo (100 steps).
- Ctrl + F / R: Open Find or Replace overlay.
- Ctrl + K: Bookmark current line.
- Ctrl + Alt + K: Open Bookmark Menu.
- Ctrl + Shift + K: Jump to last bookmark and delete it.
- Ctrl + Q: Collapse selected text (Folding).
- Esc: Exit Menu, Split Mode, Render Mode, or Typewriter Mode.

View Options
- Ctrl + = (or Ctrl + +): Increase font size.
- Ctrl + -: Decrease font size.
- Ctrl + F1: Dim screen brightness.
- Ctrl + F2: Brighten screen brightness.
- Ctrl + T: Cycle forward through color themes.
- Ctrl + Alt + T: Cycle backward through color themes.
- Alt + M: Toggle Margin visibility.
- Ctrl + H: Toggle Help visibility (Instructions).
- Alt + H: Toggle Status Bar visibility.

## Available Color Themes
Access via F10 or Ctrl + L -> Theme Selection Menu. Includes standard high-contrast, console, and paper-like themes.
- Custom Themes: Select "Custom..." at the bottom of the theme list to create your own by entering RGB values.

## Available Fonts
Access via F10 or Ctrl + L -> Font Selection Menu. Supports Monospace (Consolas, Roboto Mono), Serif (Times New Roman, Georgia), and Sans-Serif (Arial, Verdana).

## Status Line
The bottom right shows: Filename | Pos: [Word Position] | Words: [Total Count].
In Typewriter mode, a "T" indicator appears in the bottom left.
In Split Mode, focus is indicated by « (Left) or » (Right).

## Tips
- Connect a Bluetooth keyboard for a "Digital Typewriter" experience.
- Use Split Mode (Alt + S) to reference a PDF or Image while you write.
- Use the Outline Manager (Ctrl + F12) to manage chapters or complex project structures. Use the K key in setup to link remote files.
