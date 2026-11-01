The tagging specification is exceptionally well-aligned with ElmerPad’s current trajectory. It transitions the app from a simple buffer-swapper into a sophisticated Long-form Writing Environment or Personal Knowledge Management (PKM) tool.
1. Architectural Feasibility: Very High
The current structure is already optimized for this:
•
Data Model: The OutlineManager already uses a hierarchical JSON-based storage (.epol). Adding a tags array to FileContainer is a non-breaking change to the existing schema.
•
UI Foundation: The recent implementation of StaticLayout and the split-screen OutlineOverlay provides the exact rendering engine needed to display tags and search results crisply on E-ink.
•
Global State: The BufferManager is already designed to swap state quickly, making "Workspace-aware" tagging logically straightforward.
2. Level of Effort (LOE): High
While the data side is simple, the "Global Search" and "Workspace Support" aspects increase the complexity.
•
Data Layer (Medium): Updating the model, normalization logic, and tag-counting engine.
•
Outline UI (Medium): Adding the search/filter bar to the left panel.
•
Global Search (Medium-High): Implementing Ctrl-Alt-F to work outside the Outline context.
•
Workspace Support (High): Managing tag indices for inactive workspaces (loading .epol metadata without the text).
3. Staged Development Plan
Stage 1: The Metadata Foundation
•
Update OutlineItem.FileContainer with a tags: MutableList<String>.
•
Modify InputOverlay to accept tag input (e.g., #draft #important).
•
Update serialization to save/load these tags in the .epol file.
Stage 2: Integrated Outline Filter
•
Add a "Search Mode" to the OutlineOverlay (triggered by / or a UI element).
•
Create a "Flattened Results" view: If a filter is active, the Outline ignores folders and displays a simple list of matching files.
•
Render tags next to filenames (e.g., Chapter 1 [#draft]) using the StaticLayout engine.
Stage 3: The Tag Picker & Counters
•
Build a "Tag Cloud" or list that appears when the search bar is focused.
•
Implement the tag-counting logic (draft (12), etc.).
•
Implement normalization (automatic lowercase and duplicate removal).
Stage 4: Global "Open by Tag" (Ctrl-Alt-F)
•
Create a new global overlay for rapid file switching based on tags.
•
Implement "History" and "Pinned/Favorite" tags at the top of this menu.
Stage 5: Multi-Workspace Search
•
Develop a lightweight "Metadata Cache" that lets ElmerPad see the tags in Novel.epol while the user is working in Journal.epol.
4. Architectural Recommendations
1.
The "TagIndexService": Create a singleton service to manage the tag index. When a workspace is loaded, this service scans the .epol file once and builds an in-memory map of tag -> list of file paths. This makes the search "instant," even for hundreds of files.
2.
Tag Decorators: Do not bake tags into the name property. Keep name and tags strictly separate. This allows the user to change project organization (moving a file from #draft to #final) without ever changing the file’s identity or the URI link.
3.
Automatic "System Tags": Use your existing file-type detection to inject :text, :markdown, and :image tags automatically into the search engine. This provides value to the user immediately without them having to type a single tag manually.
4.
E-Ink Optimization: For the search bar, use a "Type-to-Filter" approach but avoid full-screen refreshes on every keystroke. Use a 300ms debounce timer (similar to your preview timer) so the E-ink screen only updates when the user pauses typing.
5.
Workspace Scaling: Since you plan to add more workspaces, ensure the global search engine (Ctrl-Alt-F) returns results formatted as [Workspace Name] > Filename. This provides crucial context when jumping between projects.
Overall Verdict: This is a robust and valuable feature. It leverages ElmerPad's strengths (metadata-heavy projects) while keeping the documents themselves (the text files) clean and standard.