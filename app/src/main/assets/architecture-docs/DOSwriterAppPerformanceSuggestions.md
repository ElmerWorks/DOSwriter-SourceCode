For a plain-text editor, 5,000--10,000 words (roughly 30--60 KB) should not normally cause performance problems, even on a low-end e-ink Android tablet. The slowdown is often caused by how the text is being processed, not the file size itself.
Since DOSwriter is an Android editor with multiple buffers, word counts, outline support, typewriter mode, markdown rendering, etc., I'd investigate these areas:
1. Stop Reprocessing the Entire Document on Every Keystroke
The most common cause.
Bad:
afterTextChanged() {
    updateWordCount();      // scans entire document
    updateOutline();        // scans entire document
    saveBuffer();           // writes entire document
    renderMarkdown();       // parses entire document
}

Good:
afterTextChanged() {
    markWordCountDirty();
    scheduleBackgroundUpdate();
}

Then update after 500-1000 ms of idle time.

2. Use Incremental Word Counting
Instead of:
String text = editText.getText().toString();
int words = text.split("\\s+").length;

which scans the entire file every keystroke,
maintain a running count.
Example:
wordCount += deltaWords;

or recalculate only when user pauses typing.

3. Avoid Excessive String Copies
A major Android performance killer.
Bad:
String text = editText.getText().toString();

inside:
cursor movement
status updates
autosave
rendering
Every call allocates a new String containing the entire document.
For a 50 KB file, dozens of these per second create garbage collection pressure.
Prefer:
Editable text = editText.getText();

and work directly on the Editable when possible.

4. Move Autosave Off the UI Thread
If every edit causes:
FileOutputStream.write(...)

the user will feel lag.
Use:
Handler.postDelayed(...)

or
ExecutorService

and save only after:
5 seconds idle
buffer switch
app pause

5. Disable Live Markdown Rendering for Large Files
If DOSwriter parses markdown continuously:
# Heading

## Subheading

every keystroke may trigger a complete parse.
Suggested:
File Size
Behavior
< 5 KB
Live rendering
5--25 KB
Delayed rendering
> 25 KB
Manual refresh

Many editors do this.

6. Limit Syntax Highlighting Scope
If you add markdown highlighting:
Bad:
highlightEntireDocument();

Good:
highlightCurrentParagraph();

or only visible lines.

7. Check Span Usage
Android's EditText can slow dramatically if many spans exist.
Examples:
markdown styling spans
search highlighting spans
custom cursor spans
outline markers
Thousands of spans can make cursor movement sluggish.

8. Avoid Full Buffer Re-layout
Typewriter mode can accidentally trigger this.
Bad:
scrollToCenterCursor();
requestLayout();
invalidate();

on every keystroke.
Instead:
recenter only when cursor leaves a tolerance zone
perhaps every 10--20 lines
This is especially important on e-ink devices.

9. Virtualize the Outline Manager
If outline mode scans:
# Heading 1
# Heading 2
# Heading 3
...

the entire document after every keypress, performance collapses.
Instead:
maintain cached heading positions
update only affected section

10. Profile Garbage Collection
On Android Studio:
Profiler ? Memory
Watch for:
GC every second
GC_FOR_ALLOC
GC_CONCURRENT

If you see constant GC activity while typing, you're allocating too many temporary objects.
Common offenders:
split()
substring()
replaceAll()
Pattern.matcher()
toString()

on the whole document.

11. Consider a "Large File Mode"
Many editors do this.
Example thresholds:
Size
Mode
< 25 KB
Full features
25--100 KB
Reduced live updates
> 100 KB
Large File Mode

Large File Mode disables:
live word count
live outline updates
live markdown rendering
aggressive autosave
while preserving editing speed.

12. The Most Likely Culprit in DOSwriter
Based on the features you've described previously, I'd first suspect:
Word-count recalculation on every edit.
Outline-manager rescanning the entire buffer.
Markdown-render preview updates.
Repeated getText().toString() calls.
Autosave writing the whole buffer too frequently.
A plain EditText should comfortably handle 10,000 words on even modest hardware. If Bigme users are seeing severe lag at only 10k words, something in the editing pipeline is probably doing O(N) work on every keystroke, turning typing into O(N�) behavior as the file grows.
A useful diagnostic is to create a special build where you temporarily disable:
word count
status line updates
autosave
outline updates
markdown rendering
and test a 20,000-word file. If performance suddenly becomes smooth, you've identified that the issue is in the background processing rather than Android's text widget itself.