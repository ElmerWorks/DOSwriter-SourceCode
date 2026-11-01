# DOSwriter - User Instructions

[OVERVIEW]
** Welcome to DOSwriter
A powerful but minimalist text writing environment. 

Core features are keyboard control of visual appearance, text navigation, and quick-access of file buffers.

Press Right Arrow (→) to continue. Left Arrow (←) to go back.
Esc to exit tutorial anytime.

You can view this tutorial in Help>Tutorial

While editing text, Ctrl-H shows DOSwriter Help and Ctrl-L shows Settings.

** Writing
DOSwriter is a text editor optimized for keyboard writing. 

It operates on Unicode text fonts, not rendered fonts such as MS Word. Use it to perform distraction-free writing for later publication formatting on a PC or laptop.

The primary function is to provide fast keyboard text editing on multiple large files. 

It provides ergonomic font size and viewport control on Android phones, tablets, and e-inks.

** USING DOSwriter
The core of the app is using the keyboard to control your editing experience. Visual appeal from colors, margins, font sizes, and screen brightness can be instantly adjusted through keyboard shortcuts. Editing, text navigation, and file handling functions are based on standard Linux\Windows conventions. 

Typewriter scrolling can be toggled with Alt-T.

File Buffer Status at page bottom is toggled with Alt-H.

** FONTS AND MARGINS
Use the keyboard to change font size and margins (the text viewport).

Ctrl+ : Increase Font Size
Ctrl- : Decrease Font Size

Ctrl+Alt+[plus key] : Bold On 
Ctrl+Alt+[minus key] : Bold Off
Change Font style in Settings>Font

Ctrl + Shift + M : Show or Hide Margin
Change Margin size in Settings>Margins

** SCREEN COLOR AND TONE
DOSwriter has a variety of font and background color themes designed for eronomic text viewing. Custom themes can be created on backlit color and e-ink color devices. On B&W devices optimized themes for monochrome e-inks are provided.

Font colors and background colors are changed in Settings>Theme where you can preview different themes. Ctrl-T will cycle through available themes. Margin color is a shade of the background color.

You can choose different themes for daylight or evening  in Settings>Theme>Day/Night Mode.

** BUFFERS
Important DOSwriter concepts are the 8 file buffers (Alt+1-Alt+8) and 1 Scratchpad buffer (Alt-9). These are like browser tabs and swap instantly. Think of buffer 1-8 as chapters in a book. There are also 8 To-Do buffers which create a printable task list.

Text can be copied and pasted between buffers. Buffers 1-8 are for long text files. The Scratchpad has an adjustable character limit and can be wiped or logged with a keystroke, and is useful for short texts such as social media posts. All buffer settings and files are retained when app reboots.

Ctrl-B : Quick swap buffer list. 
Alt-B : Cycles through buffers 1-8. 
Alt-S : Shares any buffer in a split-screen view with any other buffer. 
Alt+P : Toggles between Main and To-Do workspaces.

** TEXT NAVIGATION
The text handling commands are similar to PC/laptop  key combinations such as Ctrl-C/V/X (copy, paste, cut). The Ctrl and Alt keys combined with arrow and alphanumeric keys accomplish text navigation and editing, such as for jumping words, sentences, and paragraphs. The keyboard workflow is intuitive and easy to learn.

DOSwriter can navigate large text files by key commands or finger/trackball scrolling. Typewriter mode text entry and scrolling keeps focus on a user-defined point in the screen. Page Up and Page Down (Alt + F / Alt + G) can be set to various lengths depending on font size and margin viewport.

** FILES
File operations are similar to Linux and Windows : 

Ctrl + I: Take a screenshot and save to Pictures.
Ctrl + O: Open file (.txt, .md, .html, .css, .jpg, .png, .pdf).
Ctrl + Shift + O: Open most recently used files.
Ctrl + Alt + C: Copy entire buffer content to system clipboard.
Ctrl + S: Save current buffer to file.
Ctrl + Alt + S: Share buffer with another Android app
Ctrl + W: Close the current buffer.

** FILE MANAGEMENT
An Outline Manager provides a way to organize your writing into projects.

Alt+O : Open Outline Manager, your files orgainzed into folders, Open Outline projects
Ctrl+Alt+O : Open Outline Manager Setup, Add folder and files, Save Outline projects 

A Remote Desktop Server provides linking, editing, and auto-saving of text files on your desktop ot laptop PC.

Help>Outline Manager and Help>Remote Sync describe how to use these tools.

** KEYBOARDS

DOSwriter is optimized for Bluetooth or attached keyboards with arrow keys. Arrow keys are required for the app to function.

Since F key (top row) behavior is often dedicated to Android device functions, the numeric keys 1-9 are used for buffers.

When a keyboard is not attached, a Virtual Keyboard is displayed. This may be useful when you don't have your keyboard.

Press Right Arrow (→) or Escape to finish this overview.

[/OVERVIEW]

DOSwriter is a minimalist distraction-free Android writing environment inspired by dedicated writing devices and keyboard-centric workflows such as the Alphasmart Neo and DOS/Unix text editing tools. It is designed for fast, keyboard-driven writing with a focus on stability and simplicity. The keyboard workflow is intuitive and easy to learn.

## Key Features

- Immersive Mode: Full-screen writing with no system distractions.
- 8 Independent Buffers: Work on multiple drafts simultaneously.
- To-Do Workspace: A secondary persistent stack of 8 buffers for task lists.
- Scratchpad Buffer: Character-limited buffer with fast save or purge.
- Color Themes: Classic and e-ink color themes. Custom user themes.
- Split-Screen Mode: View and edit any pair of buffers side-by-side.
- View Control: Adjustable fonts and margins; toggle margin visibility.
- Collapsible Text Mode: Compress sections of text to icons.
- Bookmarks: Mark specific text positions and jump to them instantly.
- Persistent Storage: Text, cursor positions, and settings are automatically saved.
- Outline Manager: Organize your project into folders and linked files.
- Typewriter Mode: Keep your focus fixed on a single line.
- Typewriter Demo: accessible via Help > Typewriter Demo.
- Multimedia Support: Display images and reference PDFs side-by-side with your text.
- Code Support: Syntax highlighting for HTML and CSS.
- Desktop Sync: Real-time synchronization with a Go-based host server.
- Virtual Keyboard: Optimized internal keyboard with Alpha, Punc, and Num layers.

## Requirements

A Bluetooth or attached keyboard with F1-F12 is ideal but it works without the F keys. A trackball or mouse helps but is not required.

## Using DOSwriter

The core of the app is using the keyboard to control your editing experience. Visual appeal from colors, margins, font sizes, and screen brightness can be instantly adjusted through keyboard shortcuts. Editing and text navigation functions are based on standard Linux\Windows conventions. Typewriter scrolling can be toggled.

DOSwriter has 8 file buffers (F1 - F8 or Alt1- Alt8) and 1 Scratchpad buffer (F9 or Alt-9). These are like browser tabs and swap instantly. Ctrl-B brings up a quick swap buffer list. Each buffer can have its own color theme and font settings.

### Workspaces (Main vs. To-Do)
DOSwriter features two independent "stacks" of buffers. 
- **Main Mode**: Your primary workspace for writing projects.
- **To-Do Mode**: A secondary workspace containing 8 task-oriented buffers (e.g., Financial, Security, Shopping).
- Toggle between modes using **Alt + P** or the **TD** key on the virtual numeric keyboard. Both stacks are saved persistently.

### Remote Desktop Sync
A remote desktop server will link files on a Linux or Windows host machine on the same local network as the Android running DOSwriter. This allows editing and saving of files with the host.

### Outline Manager
An Outline Manager (F12 or Alt-O) provides writing project organization with hierarchical folder and files, including remote linked files.

### Multimedia & PDF
[IF_COLOR]
DOSwriter supports viewing JPEG and PNG images. You can view these in any buffer or side-by-side in split-mode.
[/IF]
[IF_MONO]
On monochrome e-ink, images are converted to grayscale. To reduce ghosting, use the standard e-ink theme (Alt-T).
[/IF]
An image viewer (Alt-I) will load single images or a slideshow. Images can also be loaded into a buffer (Ctrl-O). Text and images cannot be mixed.

## Virtual Keyboard

DOSwriter has a built-in virtual keyboard that activates when no physical keyboard is detected.
- **Layers**: Tap **&@** for punctuation or **#+** for numbers. Tap **ABC** to return to the alpha layer.
- **Grid Design**: The keyboard is based on a precise 10-column layout.
- **Orientation**: Rotating to Landscape automatically sets the keyboard to 50% screen height for balanced writing.
- **Customization**: Use F10 > Virtual Keyboard to toggle **Keyboard Outlines** or change the background theme.
- **Predictive Text (PT)**: A single-line console will open. Touch suggested words to complete them, and press Enter to paste the console line into your buffer.

## Menus

- Buffers (Ctrl + B): Select named buffers 1-8.
- Desktop Sync (Ctrl + D): Configure and manage remote host synchronization.
- Bookmarks (Ctrl + Alt + K): Select from bookmark list.
- Settings (F10 or Ctrl + L): Consolidated menu for Cursor, Font, Margin, Theme, Virtual Keyboard, and Desktop Sync.
- Outline Manager (F12): Select from file list, add folders and files.
- Help (Ctrl + H): View Key Assignments, Instructions, and Virtual Keyboard Grid Maps.

## Keyboard & Mouse Shortcuts

Buffer & Project Management
- Alt + 1 to Alt + 8 OR F1 to F8: Switch to the corresponding buffer instantly.
- Alt + P: Toggle between Main Workspace and To-Do Workspace.
- Alt + B: Cycle forward through buffers 1-9.
- Ctrl + Alt + B: Cycle backward through buffers 1-9.
- Ctrl + B: Open the Buffer Selection Menu (shows filenames).
- Ctrl + J: Open the String Pattern Manager.
- Ctrl + Shift + J: Collapse All Paragraphs.
- Alt + K: Toggle Virtual Keyboard (on-screen keyboard) visibility.
- F12 or Alt + O: Open Outline Manager Selection screen.
- Ctrl + F12 or Ctrl + Alt + O: Open Outline Manager Setup screen.
- F10 or Ctrl + L: Open Settings Menu.
- F9 or Alt + 9: Open Scratchpad buffer.
- Ctrl + F9 or Ctrl + 9: Flush Scratchpad to log file and clear.
- Alt + F9 or Ctrl + Shift + 9: Flush Scratchpad without saving.
- Alt + S: Open the Split Screen Menu.
- Alt + N: Swap focus between panels in Split Mode.
- Alt + R: Toggle Markdown Render mode.
- Alt + X: Minimize app (Move to background).
- Alt + F4 / Esc: Exit app (Prompts for confirmation).
- Ctrl + N: Clear the current buffer (Prompts for confirmation).

File Operations
- Ctrl + I: Take a screenshot and save to Pictures.
- Ctrl + Alt + S: Trigger Android System Share for current buffer.
- Ctrl + Alt + C: Copy entire buffer content to system clipboard.
- Ctrl + S: Save current buffer to file.
- Ctrl + O: Open file (.txt, .md, .html, .css, .jpg, .png, .pdf).
- Ctrl + Shift + O: Open most recently used files.
- Ctrl + W: Close the current buffer.
- Ctrl + P: Export PDF. Choose between "Standard Compile" or "8-in-1 Mini-Book" (landscape grid).

Navigation & Editing
- Arrow Keys: Move by character or Terminal Line.
- Alt + Up/Down: Jump to start of previous/next Sentence.
- Ctrl + Up/Down: Jump to start of previous/next Paragraph.
- Ctrl + Left/Right: Jump to previous/next Word.
- Alt + Left/Right: Jump to beginning/end of line.
- Ctrl + Alt + Up/Down, Ctrl + \ / Ctrl + ': Jump to beginning/end of buffer.
- Alt + F / Alt + G: Page Up / Page Down.
- Shift + F12: Find cursor (Hold to blink cursor location).
- Ctrl + C / X / V: Copy / Cut / Paste selected text.
- Ctrl + Z / Ctrl + Alt + Z: Undo / Redo (100 steps).
- Ctrl + F / R: Open Find or Replace overlay.
- Ctrl + K / Ctrl + Alt + K: Add bookmark / Open Bookmark Menu.
- Ctrl + Q: Collapse selected text (Folding).
- Esc: Exit Menu, Split Mode, Render Mode, or Overlays.

View Options
- Ctrl + = / Ctrl + -: Increase / Decrease font size.
- Ctrl + F1 / Ctrl + F2 OR Alt+/Alt-: Dim / Brighten screen brightness.
- Ctrl + T / Ctrl + Alt + T: Cycle color themes forward / backward.
- Ctrl + Shift + M: Toggle Margin visibility.
- Ctrl + H: Toggle Help visibility.
- Alt + H: Toggle Status Bar visibility.

## Available Fonts
Access via F10 or Ctrl + L -> Font Selection Menu. Supports system fonts and custom `.ttf` / `.otf` files placed in `app/src/main/assets/fonts/`.

## Status Line
The bottom right shows: Filename | Pos: [Word Position] | Words: [Total Count].
In Typewriter mode, a "T" indicator appears in the bottom left.

## Tips
- Use the **8-in-1 Mini-Book** PDF export to print a foldable task list.
- Use **Alt + P** to keep your "To-Do" lists separate from your active drafts.
- Connect a Bluetooth keyboard to automatically disable the virtual keyboard globally.
