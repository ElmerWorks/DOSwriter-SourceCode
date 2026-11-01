# DOSwriter Tag System: Implementation Plan

## 1. Executive Summary
The Tag System provides project-level metadata for organizing and locating files within an Outline Manager project. It allows for rapid filtering of hundreds of documents without modifying the underlying text or image files.

**Dependency:** This feature should be implemented **AFTER** the Workspace Stack, as tags are naturally scoped to specific workspaces.

## 2. Architectural Vision
*   **Tag Decorators:** Tags are stored as a metadata array inside the `.epol` outline file, keeping the linked documents (TXT, MD, PDF) clean and standard.
*   **TagIndexService:** A singleton service that builds an in-memory index (`tag -> file_list`) for instant, zero-latency searching.
*   **Flattened Results:** When a search is active, the Outline UI flattens the project hierarchy into a simple list of matching files.

## 3. Staged Development Plan

### Stage 1: Metadata Foundation
*   Update `OutlineItem.FileContainer` with a `tags: MutableList<String>`.
*   Update `InputOverlay` to handle tag entry strings (e.g., `#draft, #research`).
*   Update `.epol` serialization to handle the new tag data.

### Stage 2: Outline UI Integration
*   Add a Search/Filter bar to the left panel of the `OutlineOverlay`.
*   Render tags next to filenames in the list using the `StaticLayout` engine for crisp e-ink display.
*   Update the Preview pane to display all tags associated with the selected file.

### Stage 3: Dynamic Pick List
*   Implement a "Tag Cloud" or dropdown list when the search bar is focused.
*   Display usage counts for each tag (e.g., `draft (4)`).
*   Add normalization (automatic lowercase, whitespace trimming, and sorting).

### Stage 4: Global Search (`Ctrl+Alt+F`)
*   Create a global overlay that allows users to find files by tag *without* opening the Outline Manager first.
*   Implement "Recent Searches" and "Pinned/Favorite" tags at the top of the results.

### Stage 5: Advanced Search & Automation
*   Implement Boolean logic: `draft review` (AND), `draft -complete` (NOT).
*   Add **Automatic System Tags**: `:text`, `:markdown`, `:image`, `:pdf`, and `:remote`.

## 4. Key Considerations for DOSwriter
*   **E-Ink Optimization:** Implement a 300ms debounce timer on the search bar. This prevents the e-ink screen from attempting to refresh on every single character stroke, updating only when the user pauses typing.
*   **Visual Distinctions:** Use specific text colors or border styles for tags to separate them from the filename. On monochrome e-ink, use `[brackets]` for tags.
*   **Workspace Integration:** Ensure `Ctrl+Alt+F` displays results from other workspaces if the user chooses "Search All Workspaces."
