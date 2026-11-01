# DOSwriter Workspace Stack: Implementation Plan

## 1. Executive Summary
The Workspace Stack transforms DOSwriter from a single "multi-tasking notepad" into a full-scale writing workstation. It allows users to manage up to 16 independent writing environments (Workspaces), each with its own set of 8 buffers, To-Do lists, and Outline project.

**Recommendation:** This feature should be implemented **FIRST** as it defines the "container" that the Tag System will live inside.

## 2. Architectural Vision
*   **State Multiplication:** Move from global state to an encapsulated `WorkspaceState` object.
*   **Lazy Loading:** To maintain high performance on e-ink, only the *metadata* (name, last used) of all workspaces is held in memory. Document text is only loaded when a Workspace is activated.
*   **Persistence:** A new `.epws` (DOSwriter Workspace) JSON format will store the session state.

## 3. Staged Development Plan

### Stage 1: State Encapsulation
*   Refactor `BufferManager` and `OutlineManager` to be properties of a new `Workspace` class.
*   Ensure UI components (`EditorView`, `OutlineOverlay`) are "stateless"—they simply render the active workspace's data.

### Stage 2: Persistence Layer
*   Implement saving the current session to `active_project.epws`.
*   Maintain a `registry.json` to track names and descriptions of the 16 workspace slots.

### Stage 3: Workspace Selector UI
*   Implement `Ctrl+Alt+W` overlay.
*   Display a high-contrast list of the 16 slots with project names.
*   Add keyboard navigation (Arrows to select, Enter to swap).

### Stage 4: Workspace Lifecycle
*   **New Workspace (`Ctrl+Alt+N`):** Initialize a clean state.
*   **Duplicate (`Ctrl+Alt+D`):** Clone an existing `.epws` file to a new slot.
*   **Rename (`Ctrl+Alt+R`):** Update metadata in the registry.

### Stage 5: Deep State Preservation
*   Save and restore scroll positions for Text, Markdown, and PDF views.
*   Save and restore zoom/pan positions for Images.
*   Silo the Undo/Redo history so it is unique to each workspace.

## 4. Key Considerations for DOSwriter
*   **E-Ink Optimization:** Use a "Switching Workspace..." splash message during loads to mask the layout recalculation and prevent flickering.
*   **Safety:** Implement an automatic "Dirty Check" before switching. If a buffer in the current workspace has unsaved changes, prompt the user before moving to the next workspace.
*   **Shared Settings:** Keep global settings (Theme Mode, Font Type, Menu Sizes) separate from workspace settings so the app's look-and-feel remains consistent.
