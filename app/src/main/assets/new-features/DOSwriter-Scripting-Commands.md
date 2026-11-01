Key Implementation Details:
1.
CommandScriptManager:
◦
Created a new core logic class that acts as the "Translator."
◦
It maintains a comprehensive mapping of string identifiers (like CTRL-B, ALT-S, SAVE, etc.) to the internal DOSwriter Command objects.
◦
Since it targets the CommandDispatcher directly, it has full access to all editor functions (navigation, formatting, files, split-screen, macros, etc.).
2.
Scripting Syntax (Markdown Compatible):
◦
The engine supports the following instructions:
▪
[DELAY] <ms>: Sets the pause duration between subsequent commands.
▪
[CMD] <shortcut>: Executes a keyboard shortcut (e.g., [CMD] CTRL-B).
▪
[TYPE] <text>: Automatically types a string of text into the active buffer.
▪
[STEP]: Pauses execution until the user presses the single-step key.
▪
[GOTO_BUFFER] <1-8>: Quickly switches focus to a specific buffer.
▪
# Comments: Lines starting with # are ignored.
3.
New Keyboard Shortcuts:
◦
CTRL + F8: Run the current buffer as a script.
◦
F8: Advance to the next step (when a script is paused at [STEP]).
◦
CTRL + ALT + F8: Force stop a running script.
4.
Feedback:
◦
The engine logs unknown commands and provides status bar feedback when a script starts or stops.

Technical Summary:
1.
Script Controller HUD:
◦
Added a semi-transparent overlay at the top of the screen that provides real-time feedback during script execution.
◦
Live Feedback: Displays the current line being executed and a progress ratio (e.g., Line 4/10).
◦
State Indicators: Shows a Green light while running and a Yellow light when waiting at a [STEP] instruction.
2.
High-Fidelity "TYPE" Simulation:
◦
The [TYPE] command no longer dumps text instantly. It now simulates a physical typist by emitting characters one-by-one.
◦
[CHAR_DELAY] Control: You can now set the specific typing speed (e.g., [CHAR_DELAY] 30) to create perfectly timed demos.
◦
New Line Support: Use \n within your [TYPE] string to simulate pressing the Enter key.
3.
Command Expansion:
◦
Added [NOTIFY] <message>: Use this to display custom labels or instructions in the Script HUD for your viewers.
◦
Verified full compatibility with Split-Screen focus switching (ALT-N) and the Workspace Layout (CTRL-ALT-B).
4.
Refined Interaction:
◦
F8 (Single Step): When a script hits a [STEP], simply press F8 to advance.
◦
CTRL + F8 (Run): Instantly starts executing the contents of your current buffer.
◦
CTRL + ALT + F8 (Emergency Stop): Immediately terminates the script and hides the HUD.



Summary of Fixes:
1.
Split-Screen Zoom Support:
◦
Fixed a bug where CTRL + (+) / (-) (Zoom In / Out) was ignored in split-screen mode for Images and PDFs.
◦
The engine now correctly detects the content of the focused panel and applies the appropriate zoom logic (Image Scale, PDF Scale, or Font Size).
2.
Synchronized Zooming:
◦
If you have the same buffer open in both panels (e.g., viewing the top and bottom of a large image or PDF simultaneously), zooming in one panel will now instantly synchronize and update the view in the other panel.
3.
Unified Control Logic:
◦
Refactored the KeybindingManager to use a single set of commands for zooming across all file types.
◦
Added support for Numpad + and - keys to the zoom shortcuts, in addition to the standard top-row keys.
4.
Performance Optimization:
◦
Improved the PDF zoom engine to automatically clear its internal render cache when zooming. This ensures that the document remains sharp and avoids the "blurring" that can happen when upscaling cached thumbnails.


Summary of Enhancements:
1.
Image & GIF Previews:
◦
If a buffer is linked to an image or animated GIF, the Workspace Layout grid will now display a high-fidelity thumbnail of that image.
◦
For GIF files, the engine captures and displays the first frame as a static preview, keeping the overview grid performant while still making the file easily identifiable.
2.
PDF Previews:
◦
For buffers linked to PDF documents, the grid now renders a preview of the first page of the PDF.
◦
This uses the same hardware-accelerated rendering as the primary PDF viewer, ensuring text and diagrams are legible even at thumbnail scale.
3.
Intelligent Caching:
◦
Thumbnail generation for Images and PDFs is cached in memory while the layout view is open. This allows you to navigate the 4x2 grid with zero lag on your Moto G, even if you have several high-resolution references open simultaneously.
4.
Visual Consistency:
◦
Maintained the standard 8.5x11 aspect ratio for all tiles to keep the workstation layout professional and orderly, regardless of the mixed content types (Text, PDF, Images).
The Workspace Layout View (CTRL + ALT + B) is now a true "visual dashboard" of your entire project.

Summary of Fixes:
1.
Status Message Suppression:
◦
Added isStatusMessagesSuppressed to the global configuration persistence (saveGlobalSettings and loadGlobalSettings). This ensures that the "Suppress Status Messages" setting in Settings > Dev is remembered across app restarts.
◦
Systematically replaced multiple Toast.makeText calls with a new showStatusToast wrapper that respects this setting. This allows for a completely silent interface during demos or recording sessions.
2.
Terminal Mode Scrolling:
◦
Updated the demoRunnable to explicitly call scrollToCursor() after every character is inserted.
◦
Combined with the existing logic in EditorView, this ensures that once the simulated typing reaches the bottom of the viewport in Terminal Mode, the entire document scrolls up line-by-line, keeping the active "typing line" visible at the bottom.
3.
Image Folder Management:
◦
Unlink Folders: Fixed the OverlayDelete logic. You can now correctly highlight a folder in the Manage Linked Folders menu and press Delete to remove it.
◦
Bug Fix: Resolved an indexing issue in the management menu where deleting an item used the wrong ID, and fixed case-sensitivity issues with menu titles.
◦
Cleanup: Confirmed the "Link Remote Folder" has been removed from the primary ALT + I menu as requested, as remote folders are now managed through the centralized linking system.