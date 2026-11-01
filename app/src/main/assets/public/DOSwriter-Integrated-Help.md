# DOSwriter Integrated Help System

DOSwriter features a comprehensive, context-aware help system designed to be accessible without leaving the editing environment. The system is optimized for both backlit and E-ink screens.

## Accessing Help

- **Help Menu:** `ALT + H` opens the main integrated help menu.
- **Quick Reference Popup:** `CTRL + ALT + H` (or `F10` shortcut) displays a fast-access keyboard map and command reference.
- **Startup Tutorial:** The app can be configured to show a quick tutorial on launch (`ALT + S > Advanced Features > Text Display`).

---

## Core Help Functions

The `ALT + H` menu provides several ways to learn and master DOSwriter:

### 1. Quick Keyboard Shortcuts Reference
A multi-page popup providing instant reminders for the most common editing, navigation, and file management commands. Ideal for new users or as a rapid refresher.

### 2. Quick Tutorial (Overview)
A curated, interactive walkthrough of DOSwriter's philosophy and primary features. It covers everything from buffer management to the typewriter engine in a series of easy-to-read screens.

### 3. Menus and Keyboard Shortcuts (Topic Navigation)
A categorized library of help topics extracted directly from the user manual. It allows you to jump to specific sections such as:
- File Management
- Markdown Editing
- Remote Desktop Sync
- Advanced Formatting

### 4. Virtual Keyboard Maps
Visual diagrams showing the layout and secondary functions of the built-in on-screen keyboard layers:
- **Alpha Layer**: Standard text entry.
- **Punctuation Layer**: Symbols and hotkeys.
- **Numeric Layer**: Predictive text and workspace toggles.

### 5. Typewriter Demo
An automated simulation that demonstrates the **Typewriter Scrolling Engine**. It visually shows how the engine maintains a fixed gaze point by "typing" the current buffer's text to the screen in real-time.

### 6. How to Use To-Do List (PDA)
Opens a dedicated manual for the **PDA (ToDo)** workspace. It explains how to manage task lists, navigate between the 8 task buffers, and export your lists as a printable mini-book.

---

## Intelligent Parsing and Customization
The help system utilizes an internal **Help Parser** that intelligently filters content based on your device type:
- **IF_COLOR / IF_MONO**: Content is automatically adjusted for color LCDs or monochrome E-ink displays.
- **IF_TABLET / IF_PHONE**: Layout instructions change based on your hardware's screen dimensions.

## About Screen
Displays version information, build details, and licensing for the open-source components that power DOSwriter.
