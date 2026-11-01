# DOSwriter Manuscript Viewer

The Manuscript Viewer is a specialized visualization engine designed to help writers see their work in a physical, paginated context. Unlike the standard editor, which is optimized for fluid digital entry, the Manuscript Viewer renders text as individual pages with fixed physical dimensions, margins, and font sizes.

## Access and Shortcuts

- **Main Menu:** `CTRL + Q` opens the selector to choose between **Layout** or **Filmstrip** view.
- **Instant Filmstrip:** `CTRL + ALT + Q` (or `SHIFT + Q` on some keyboards) opens the single-page viewer directly.
- **Instant Layout:** `CTRL + SHIFT + Q` opens the multi-page grid overview directly.
- **Viewer Settings:** `ALT + Q` opens the configuration menu for physical layout attributes.

---

## Viewing Modes

### 1. Layout View (Grid Overview)
Provides a "bird's-eye view" of your entire document. It is ideal for visualizing chapter lengths, paragraph density, and the overall "shape" of your manuscript.
- **Navigation:** Use `Arrow Keys` to move focus between pages.
- **Bulk Navigation:** `CTRL + Left/Right Arrow` jumps forward or backward by a full grid (4 or 8 pages).
- **Customization:** Toggle between a **4-page** or **8-page** grid in the `ALT + Q` settings.

### 2. Filmstrip View (Single Page)
A focused, distraction-free way to flip through your document one page at a time.
- **Navigation:** Use `Left/Right Arrow` keys to "turn" pages.
- **Performance:** Pages are paginated in real-time, ensuring that even extremely large buffers are navigated smoothly.

---

## Editor Integration

The Manuscript Viewer is not just for reading; it is a powerful navigation tool for the main editor:
- **[Enter] (Jump to Page):** Pressing Enter on any page in either viewer will close the overlay and jump the main editor's cursor exactly to the start of that specific page.
- **[Esc] (Cancel):** Closes the viewer and returns you to your exact original editing position.

---

## Configuration (`ALT + Q`)

Configure how your manuscript is paginated and displayed:

- **Page Size:** Select from industry-standard physical dimensions:
    - `8.5 x 11` (Standard Letter)
    - `6 x 9` (Trade Paperback)
    - `5 x 8`, `4 x 6`, `3 x 4` (Small Formats)
- **Orientation:** Toggle between **Portrait** and **Landscape**.
- **Reference Font Size:** Set the layout density using serif-based font metrics from **10pt** to **18pt**.
- **Page Margins:** Adjust the physical white-space on the page from **0.25"** to **1.5"**.
- **Paragraph Collapse:** An advanced feature that hides the body of paragraphs to show only the first sentence, allowing you to see the structural flow of your entire project in a single grid.

---

## Compilation to PDF
While inside either the Layout or Filmstrip view, you can press **`CTRL + P`** to instantly compile your document into a professional PDF that exactly matches the settings (size, margins, fonts) currently active in the viewer.
