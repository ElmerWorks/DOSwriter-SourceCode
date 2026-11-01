# DOSwriter Typewriter Mode

DOSwriter features an advanced **Typewriter Scrolling Engine** designed to keep your focus exactly where you need it: on the line you are currently writing. This mode eliminates the need for manual scrolling and ensures a consistent vertical gaze point, similar to a physical typewriter.

## Access and Toggling

- **Menu Access:** `ALT + Y` opens the top-level **TYPEWRITER** menu.
- **Instant Toggle:** `CTRL + Y` switches Typewriter Mode ON or OFF from anywhere in the editor.
- **Recenter View:** `CTRL + F11` manually forces the viewport to align with the typewriter anchor.

## Core Functions and Settings

The `ALT + Y` menu provides granular control over the engine's behavior:

### 1. Typewriter Active (ON/OFF)
The master toggle for the engine. When active, the text automatically scrolls to keep the cursor at the defined **Position**.

### 2. Stationary Cursor
- **OFF (Standard)**: The cursor moves horizontally as you type, and the viewport only scrolls vertically when you hit a new line.
- **ON (Fixed Focus)**: The cursor remains locked in the horizontal center of the screen. As you type, the text itself "pushes" left, providing a perfectly fixed focal point for your eyes.

### 3. Position (Anchor)
Defines where on the screen the active line of text is maintained:
- **CENTER**: The active line stays in the middle of the screen.
- **BOTTOM**: The active line stays near the bottom margin (ideal for mobile devices with high-positioned virtual keyboards).

### 4. Type Guide Graphic
Toggles a high-contrast visual indicator (similar to a vintage typewriter's paper guide) at the exact vertical position of the active line. 

### 5. Terminal Mode
An intelligent scrolling logic that allows the editor to fill naturally from the top of the screen (like a classic terminal). The typewriter scrolling behavior only kicks in once the text reaches your chosen **Position** (Center or Bottom).

---

## Typewriter Demo

Accessible via `HELP > Typewriter Demo` or the **`ALT + D`** shortcut.

The Demo simulates a human typing experience by automatically emitting the current buffer's contents to the screen at a controlled pace. This is useful for presentations, testing font/margin aesthetics, or observing the engine's behavior.

### Demo Speed Controls
You can adjust the simulation speed in the `ALT + Y` menu:
- **Char Delay**: The pause between individual characters (0ms to 200ms).
- **CRLF Delay**: The pause after hitting the Enter key (New Line).

## Optimization for E-Ink
The Typewriter Engine is specifically optimized for E-ink devices. By maintaining a stationary gaze point and using "Terminal Mode" to minimize full-screen refreshes, it significantly reduces ghosting and improves the battery life of e-paper hardware.
