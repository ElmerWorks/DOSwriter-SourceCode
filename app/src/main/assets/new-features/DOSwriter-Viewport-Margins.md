# DOSwriter Viewport & Margins

DOSwriter provides a flexible viewport management system, allowing writers to define the exact dimensions of their text area. This ensures a comfortable writing environment across different screen sizes and hardware, from narrow phones to wide E-ink tablets.

## Accessing Margin Settings

- **Margin Menu:** `ALT + S > Margins` opens the primary configuration menu.
- **Toggle Visibility:** `ALT + M` instantly shows or hides the high-contrast margin boundaries.

---

## Viewport Controls

The editor supports independent control over horizontal and vertical spacing to optimize the "distraction-free" experience.

### 1. Side Margins (Horizontal)
Defines the padding on the left and right sides of the text.
- **Menu Options:** 0px, 20px, 40px, 60px, 80px, 100px, 150px, or Custom.
- **Smart Effective Margin:** When "Show Margins" is enabled (`ALT + M`), the engine automatically adds a 2-character width buffer to ensure text never touches the visual boundary.

### 2. Top/Bottom Margins (Vertical)
Defines the padding at the top and bottom of the buffer.
- **Menu Options:** 0px, 20px, 40px, 60px, 80px, 100px, 150px, or Custom.
- **Ergonomic Alignment:** Used in conjunction with **Typewriter Mode** to perfectly center your active writing line.

### 3. Vertical Offset (Shift)
Fine-tune the vertical position of your text block without changing the margin settings.
- **Shift Up:** `CTRL + ,` (Comma) decreases the offset.
- **Shift Down:** `CTRL + .` (Period) increases the offset.
- This is particularly useful for adjusting the focal point on devices with large system status bars or navigation areas.

---

## Specialized Margin Modes

### 📄 Manuscript Margins (Physical)
When using the **Manuscript Previewer** (`CTRL + Q`), the app switches from pixel-based screen margins to physical paper margins.
- **Units:** Measured in inches (e.g., 0.25", 0.5", 1.0").
- **Purpose:** Simulates how the text will actually look when printed or compiled to a PDF.
- **Access:** `ALT + Q > Page Margins`.

---

## Global Application

Settings can be applied to individual buffers or synced across the entire workspace:
- **Apply Margins Globally:** Found in the `ALT + S > Margins` menu. This instantly copies the current buffer's Side, Top/Bottom, and Offset settings to all 16 buffers.

## Visual Customization
Margin colors are automatically calculated based on the active **Color Theme**. The margin area is rendered as a subtle translucent overlay, providing a clear visual structure without being intrusive.
