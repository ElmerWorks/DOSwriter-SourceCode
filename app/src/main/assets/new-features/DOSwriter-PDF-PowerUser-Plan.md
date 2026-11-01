# DOSwriter PDF Power-User Features: Implementation Plan

## 1. Executive Summary
This plan upgrades DOSwriter's PDF handling from a basic "image-style" viewer to a professional reference tool. The goal is to allow writers to efficiently navigate, search, and reference large PDF documents while taking notes in single or split-view modes.

## 2. Architectural Vision
*   **PDF Command Mode:** When a buffer contains a PDF, the keyboard logic shifts to a document-aware mode where alpha keys (N, P, F) act as navigation and search commands.
*   **Background Rendering:** Rendering large pages is moved to a background thread to prevent UI "stuttering" or input lag.
*   **Page-Centric Model:** Navigation shifts from purely vertical scrolling to index-based page jumping, which is significantly faster for thousands of pages.

## 3. Staged Development Plan

### Stage 1: Basic Navigation & Command Mode
*   Implement `KeybindingManager` logic to detect "PDF active" state.
*   Add **Next/Prev (`N`/`P`)**, **First/Last (`Alt-Up`/`Alt-Down`)** commands.
*   Update `EditorView` to support jumping directly to a page index.

### Stage 2: Performance & Rendering Optimization
*   Implement a **Bitmap Cache** for the current page and its immediate neighbors.
*   Offload PDF rendering to a background `Handler` to keep the UI thread fluid.
*   Implement "Low-Res Preview" during active zooming/scrolling to maintain responsiveness.

### Stage 3: Navigation Overlays (Goto & Favorites)
*   **Goto Page (`Ctrl-G`):** Use the `InputOverlay` to accept numeric page input.
*   **Favorites (`V`):** Create a pick-list for up to 8 "pinned" pages per document.
*   **Zoom Controls:** Implement `Z+` and `Z-` chords and support for trackball/mouse-wheel scaling.

### Stage 4: Text Search & Selection
*   **Find (`F`):** Attempt to extract the text layer from the PDF.
*   **Validation:** If no text layer is detected, show: *"Find does not work on scanned PDFs."*
*   **Selection & Copy:** Research and implement coordinate-to-text mapping to allow selecting text blocks for copying to the DOSwriter clipboard.

## 4. Key Considerations for DOSwriter
*   **E-Ink Optimization:** Ensure page transitions are clean and minimize full-screen refreshes during zoom.
*   **Split-View Synergy:** Ensure `Alt-N` (Swap focus) remains the highest priority so users can quickly jump between their notes and the PDF reference.
*   **Memory Safety:** Large PDFs can easily exceed Android's memory limits. Implement strict bitmap recycling to prevent crashes during long research sessions.

## 5. Summary of Benefits
*   **Efficiency:** Rapidly navigate huge reference works without leaving the keyboard.
*   **Integration:** Transforms DOSwriter into a dual-purpose writing and research station.
*   **Professional Grade:** Provides capabilities usually reserved for desktop PDF suites while maintaining a minimalist, distraction-free interface.
