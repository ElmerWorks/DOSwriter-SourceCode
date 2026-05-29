# MonkWrite - Project Chat Prompts

This file contains a chronological capture of the user instructions and prompts used to build the **MonkWrite** editor during this session.

1. **Find Cursor Implementation**: Implement a find cursor function. Use Alt-c to activate (I will update center cursor later). When Alt-c is pressed and held, the character text space at the cursor location blanks out (to the same color as the font color) and blinks on/off until Alt-c is released.
2. **Find Cursor Remap**: Alt-c did not work for highlighting the cursor. It also had problems with the center function. Map F12 to find Cursor function to test this.
3. **Theme Cycle Feedback**: For Ctrl-t theme cycle, display theme names while cycling, similar to how filenames are displayed during buffer change.
4. **Viewport Alignment & Tuning**: Try to fix viewport alignment by positioning first line of text buffer one character space below top margin. Also implement Adjust Vertical Viewport function Alt-v + left/right arrow adjusts by 5px (0 to 60px).
5. **Theme Management**: Remove theme "Green Console 1". Add new theme "Yellow Moon" (Background RGB: 13,13,13, Font RGB: 242,226,5).
6. **Theme Cleanup**: Remove theme "Deep Sea".
7. **Build Error Recovery**: Fix XML errors caused during icon addition (Processing instruction target matching "[xX][mM][lL]" is not allowed).
8. **Rebranding**: Use MonkWriteLogo.png as app icon. Change app name to MonkWrite. Prepare for Play Store (Minify/Shrink).
9. **Word Wrapping & Custom Margins**: Implement whole-word wrapping. Add "Custom..." option to margin menus for discrete pixel value entry.
10. **Wireless Debugging**: Guidance on debugging over Wi-Fi after pairing.
11. **Scratchpad Feature**: Add Scratchpad (F9) with char limit (280). Ctrl-F9 to flush to log, Alt-F9 to flush without saving, Ctrl-Alt-F9 for settings.
12. **Global Sync Updates**: Apply Global settings to Scratchpad. Set default theme to Wheat and default margins to 60px.
13. **Persistence & Flush Fixes**: Ensure buffers are persistent after initial install wipe. Fix Scratchpad flush so it clears the screen visually.
14. **Vertical Padding Fixes**: Add 4 non-deletable newlines to Scratchpad to fix top-line cutoff. Constrain font size to keep limit within viewport.
15. **Universal Padding**: Add 2 non-deletable newlines to every buffer. Remove these newlines automatically when saving to .txt.
16. **PDF Export**: Add Ctrl-P function to compile 1-8 buffers into a single PDF with buffer selection menu.
17. **Navigation Overhaul**: 
    - Up/Down: Move by terminal (visual) line.
    - Shift+Alt+Up/Down: Select by terminal line.
    - Ctrl+Alt+Up/Down: Jump by sentence.
18. **Scrolling & Performance**: Fix viewport scrolling for single-line jumps. Optimize rendering for large files to reduce latency.
19. **Close File**: Add Ctrl-W to close current buffer with "Save/Don't Save" confirmation logic.
20. **Show Margins**: Add "Show Margin" toggle in menu. Use 20% alpha mask (dark for light themes, light for dark themes).
21. **Margin Padding**: When showing margins, add 2 character spaces of internal padding at the edges.
22. **Cursor Persistence**: Ensure cursor position and selection state are saved and restored for all buffers across app restarts.
23. **Status Bar Metrics**: Change status bar to show `filename | Pos:word position | Words:word count`.
24. **Launcher Branding**: Use "MonkWriteLogo Launcher.png" for home screen and "MonkWriteLogo.png" for round icon.
25. **Compatibility Overhaul**: Support Android 6.0 (API 23) for legacy tablets (Samsung SM-T580).
26. **Split-Screen Mode**: Add Alt-S split-screen mode for side-by-side buffer comparison. Use Alt-N to swap focus and Esc to exit.
27. **Requirements Summary**: Generate markdown requirements and code architecture summary.
