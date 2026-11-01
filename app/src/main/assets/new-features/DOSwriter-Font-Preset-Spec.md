# DOSwriter Software Specification
# DOSwriter-Font-Presets-Spec.md

**Document Version:** 1.0

---

# Purpose

Provide an intelligent font sizing system that works consistently across phones, tablets, and e-ink devices while maintaining DOSwriter's keyboard-centric philosophy.

The system shall provide:

- Fine font size adjustment using keyboard shortcuts.
- Named font presets.
- Automatic scaling based on device type.
- Device-independent text layout.
- User-selectable hardware profiles.

---

# Design Goals

- Minimize configuration.
- Produce consistent reading and writing experiences across devices.
- Preserve approximately the same number of characters per line.
- Continue using Unicode TrueType/OpenType fonts.
- Allow future expansion without changing the editor architecture.

---

# Font Size Storage

The editor shall internally store the font size as a floating-point value expressed in **sp (scale-independent pixels)**.

Example

```
12.0 sp
13.0 sp
14.0 sp
...
30.0 sp
```

The actual numeric value shall remain hidden from normal users.

---

# Font Size Adjustment

Existing keyboard shortcuts remain the primary adjustment mechanism.

Each key press increases or decreases the current font size by a small increment.

Example

```
Increase Font Size
Decrease Font Size
```

The increment value shall be configurable internally.

---

# Font Preset Menu

The Font Preset menu shall be opened with

```
CTRL + ALT + Plus
```

This shortcut shall immediately display the preset selection dialog.

---

# Font Presets

The editor shall provide the following presets.

| Preset | Target Characters Per Line |
|---------|---------------------------:|
| Pocket | 45 |
| Compact | 60 |
| Standard | 75 |
| Comfortable | 90 |
| Manuscript | 100 |

These values represent target layout widths rather than fixed font sizes.

---

# Font Size Calculation

The editor shall calculate the font size dynamically.

Inputs include

- Device screen width
- Device orientation
- Selected font
- Average glyph width
- Target characters per line

The calculated font size becomes the initial size for the selected preset.

Users may then fine-tune the size using the existing hotkeys.

---

# Font Selection Interaction

Changing the editor font shall automatically recalculate the font size so that the selected preset continues to approximate the same number of characters per line.

Example

```
Preset: Standard

Font A
≈75 characters per line

↓

Switch to Font B

↓

Automatically adjust font size

↓

≈75 characters per line
```

---

# Hardware Profiles

A new configuration option shall be added.

```
Settings

    Advanced Features

        Hardware Settings
```

Hardware Settings shall contain

```
Device Screen

• Phone
• Tablet
• Custom
```

---

# Phone Profile

Optimized for Android phones.

Characteristics

- Smaller displays
- Portrait operation
- One-handed use
- Bluetooth keyboard optional

---

# Tablet Profile

Optimized for Android tablets.

Characteristics

- Larger displays
- Landscape operation
- External keyboard expected

---

# Custom Profile

Designed primarily for e-ink devices and unusual hardware.

Examples

- Boox
- Bigme
- Supernote
- Raspberry Pi displays
- External HDMI displays
- Future hardware

The Custom profile shall allow future expansion without requiring changes to existing profiles.

---

# Automatic Scaling

The editor shall use the selected hardware profile when calculating font sizes.

The same preset may therefore produce different point sizes on different hardware while maintaining approximately the same physical reading experience.

Example

```
Preset: Standard

Phone
15 sp

Tablet
18 sp

13-inch e-ink
20 sp
```

The actual values are implementation-dependent.

---

# Internal Architecture

The editor shall separate

- font selection
- font size
- hardware profile
- preset calculation

This permits future enhancements without modifying editor rendering.

Suggested architecture

```
Font Preset

        │

        ▼

Hardware Profile

        │

        ▼

Layout Calculator

        │

        ▼

Calculated Font Size

        │

        ▼

Text Renderer
```

---

# User Workflow

Typical usage

```
Open Font Presets

CTRL + ALT + Plus

        │

Select

Pocket
Compact
Standard
Comfortable
Manuscript

        │

Automatic Font Size Calculation

        │

Optional Fine Adjustment

Increase Font
Decrease Font

        │

Continue Writing
```

---

# Persistence

The editor shall save

- selected preset
- calculated font size
- selected font
- hardware profile

These settings shall be restored automatically when DOSwriter starts.

---

# Performance

Font size calculations shall occur

- when changing presets
- when changing fonts
- when changing hardware profile
- when changing orientation

No recalculation shall occur during normal typing.

---

# Future Enhancements

Possible future additions

- User-defined presets
- Per-workspace font presets
- Separate editor and Markdown preview font sizes
- Automatic landscape/portrait presets
- Page width preview
- Reading mode presets
- Device auto-detection with manual override

---

# Success Criteria

The implementation shall be considered complete when

1. Font presets produce consistent reading experiences across phones, tablets, and e-ink devices.
2. Existing font size hotkeys continue to provide fine adjustment.
3. Font changes preserve the approximate characters-per-line target.
4. Hardware profiles influence preset calculations appropriately.
5. All settings persist across application restarts.