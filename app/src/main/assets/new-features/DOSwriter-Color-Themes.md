# DOSwriter Color Themes

DOSwriter includes a flexible color theme engine designed for ergonomic writing and optimal legibility across various display types, including high-resolution mobile screens and high-contrast E-ink hardware.

## Accessing Theme Settings

- **Theme Menu:** `ALT + S > Theme` opens the main theme configuration menu.
- **Quick Cycle:** `CTRL + T` cycles forward through available themes; `CTRL + ALT + T` cycles backward.
- **Show Active Theme:** `CTRL + SHIFT + T` displays the name of the currently active theme in the status bar.

---

## Core Theme Functions

The `Theme Settings` menu provides granular control over the visual environment:

### 1. Theme Selection
Opens a visual picker where you can select from over 20 curated themes. 
- **E-Ink Optimization:** When "Monochrome E-Ink" is selected as the device profile, the menu automatically filters for high-contrast themes like **"Classic"**, **"E-Ink"**, and **"Soft Paper Gray"**.
- **Visual Previews:** The selection menu shows live color swatches for both background and text colors.

### 2. Custom Themes
Users can define their own color schemes by selecting **"Custom..."** at the end of the Theme Selection list.
- **Format:** Enter the configuration in the format `Name|TextColorHex|BgColorHex` (e.g., `DeepSea|#AABBCC|#001122`).
- **Persistence:** Custom themes are saved to the device and appear at the end of your selection list.

### 3. Day/Night Mode
Automate your writing environment with time-based theme swapping.
- **Mode:** Toggle between **Auto** (time-based), **Always Day**, or **Always Night**.
- **Custom Schedule:** Define precise start times for your Day and Night cycles (e.g., Day at 07:00, Night at 19:00).
- **Dual Slots:** Each buffer can store two independent theme preferences—one for the daytime and one for the evening.

### 4. Component Toggles
- **Keyboard Background Theme:** Toggle whether the virtual keyboard uses the theme's background color or its font color for the keys.
- **App Menu Theme:** Toggle the global menu system between **Light** (high contrast) and **Dark** (low emission) modes.

---

## Example Themes

DOSwriter includes a variety of aesthetics for different writing moods:
- **Classic**: Traditional White on Black.
- **Monokai**: Professional developer-style dark theme.
- **Solarized Light**: Ergonomic, low-contrast "cream" background.
- **Retro Terminal**: Vintage "phosphor" look with a subtle glow.
- **Engineer Notebook**: High-legibility grid-like aesthetic.
- **Warm Wheat Paper**: Soft, non-backlit feel for long writing sessions.

## Global Application
Use **"Apply Theme Globally"** to instantly sync your current theme and mode settings across all 16 buffers, ensuring a consistent aesthetic throughout your entire project.
