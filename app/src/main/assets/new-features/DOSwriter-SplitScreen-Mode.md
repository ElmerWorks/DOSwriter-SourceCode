# DOSwriter Split-Screen Mode

Split-Screen Mode allows you to view and edit two buffers simultaneously. This is ideal for comparing different chapters of a manuscript, taking notes from a PDF reference, or live-previewing Markdown formatting.

## Access and Shortcuts

- **Open Split Menu:** `CTRL + ALT + LEFT ARROW` or `CTRL + ALT + RIGHT ARROW`.
- **Swap Focus:** `ALT + N` toggles keyboard focus between the active panels.
- **Exit Split Mode:** `ESC` closes the secondary panel and returns to single-buffer editing.

---

## Split Configurations

When entering Split-Screen mode, you are presented with orientation choices:

### 1. Vertical (Side-by-Side)
Splits the screen into left and right panels. This is the default recommendation for tablet devices and landscape orientation.

### 2. Horizontal (Top/Bottom)
Splits the screen into upper and lower panels. Best suited for portrait orientation on phones or for viewing narrow documents.

---

## specialized Behaviors

### 📝 Markdown Live Preview
If you are editing a file with a `.md` extension and trigger Split-Screen mode:
- DOSwriter automatically enters **Live Preview Mode**.
- The left/top panel displays the raw Markdown text for editing.
- The right/bottom panel displays the **Live Rendered** version of the same document.
- As you type, the preview panel stays synchronized, ensuring your most recent changes are visible.

### 📄 PDF Reference & Note Taking
You can open a PDF in one buffer and a text file in another:
- Use Split-Screen to keep your reference document visible in one panel while writing your notes or manuscript in the other.
- Use `ALT + N` to quickly jump between navigating the PDF and typing your text.

### 🔄 Multi-Buffer Comparison
You can load any two of the 8 available buffers into the split view. This allows for seamless cross-referencing between different sections of your project without needing to switch back and forth.

---

## Technical Features

- **Independent Themes:** While both panels typically share the global Day/Night theme setting, the rendering engine respects individual buffer settings for font size and line spacing.
- **Auto-Sync:** Buffer states and cursor positions are automatically saved when swapping focus or exiting split mode, ensuring no progress is lost during the transition.
- **E-Ink Optimization:** Split transitions are designed to minimize full-screen refreshes on e-paper displays, providing a smooth experience even when working with dual panels.
