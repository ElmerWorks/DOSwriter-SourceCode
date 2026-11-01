** Markdown Editing
Automatic Detection: Markdown mode is automatically activated for any buffer with a .md file extension.

Rich Text Support: High-quality rendering of Headers (H1, H2), Bold, Italics, Strikethrough, Blockquotes, and Horizontal Rules.

Dynamic Grids (Tables): Automatically detects standard Markdown table syntax (| cell |). Renders them as formatted grids with shaded headers and high-contrast borders for E-ink clarity.

Inline Multimedia: Displays images using ![alt](path). Supports relative paths in assets (e.g., images/diagram.png) and scales images responsively to the viewport width.

Live Preview (Alt + S): Split-screen mode showing the plain-text source on the left and the rendered document on the right with synchronized scaling.

Professional Export (Ctrl + P): Compiles the buffer into a fully formatted PDF. The engine calculates block heights for perfect pagination and embeds high-resolution versions of your inline images.


** Collapse on Text Pattern
Define Pattern (Ctrl + Alt + J): Opens a prompt to enter a custom text string to trigger a collapse.

Clipboard Support: Automatically pre-fills the input box with any text currently in your clipboard (up to 80 characters).

Automated Folding: Instantly collapses all text starting from the end of a line containing the pattern down to the start of the next matching line.

Pattern Manager (Ctrl + J): Opens a scrolling FIFO (First-In-First-Out) list containing up to 8 active patterns.

Pattern Operations:

Rename: Modify a pattern string to update the folding logic.

Delete: Remove a specific pattern from the list and un-collapse its sections.

Batch Actions: Quick-access buttons within the manager to [UNCOLLAPSE ALL] text or [RECOLLAPSE ALL] based on your saved list.

Persistence: Your collapse pattern list is saved per buffer, so your organizational structure is restored whenever you reload a project.