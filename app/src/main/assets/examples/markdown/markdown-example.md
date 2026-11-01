# DOSwriter Markdown Rendering Test

This document is intended to test common Markdown rendering features in **DOSwriter**. It includes headings, lists, formatting, links, code blocks, tables, blockquotes, and other elements.

---

## Text Formatting

Normal text should appear as a standard paragraph.

**Bold text** should be rendered with a heavier weight.

*Italic text* should be slanted.

***Bold and italic*** combines both styles.

~~Strikethrough~~ should appear crossed out.

Inline code such as `printf("Hello World");` should use a monospace font.

---

## Lists

### Unordered List

* Apples
* Bananas
* Cherries

  * Dark Cherries
  * Rainier Cherries
* Dates

### Ordered List

1. First item
2. Second item
3. Third item

   1. Nested item A
   2. Nested item B
4. Fourth item

### Task List

* [x] Completed task
* [x] Another completed task
* [ ] Incomplete task
* [ ] Future enhancement

---

## Block Quote

> Simplicity is the ultimate sophistication.
>
> — Often attributed to Leonardo da Vinci

---

## Horizontal Rule

The line below should appear as a divider.

---

## Code Blocks

### Plain Text

```text
This is a plain text block.
It should preserve spacing.
    Indented text remains indented.
```

### Java

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, DOSwriter!");
    }
}
```

### Python

```python
def fibonacci(n):
    a, b = 0, 1
    while n > 0:
        print(a)
        a, b = b, a + b
        n -= 1

fibonacci(10)
```

---

## Table

| Feature  | Status | Notes                        |
| -------- | ------ | ---------------------------- |
| Headings | Pass   | H1-H6 support                |
| Lists    | Pass   | Nested lists included        |
| Tables   | Pass   | Alignment optional           |
| Code     | Pass   | Syntax highlighting optional |
| Images   | TBD    | Depends on renderer          |

---

## Links

OpenAI: https://www.openai.com

GitHub: https://github.com

Markdown Guide: https://www.markdownguide.org

---

## Images

![Image text description](file:///storage/emulated/0/Pictures/figures.jpg)


## Mixed Formatting Example

The **DOSwriter** editor is designed for *focused writing* with support for:

1. Multiple buffers
2. Keyboard shortcuts
3. Markdown editing
4. E-ink devices

A sentence containing **bold**, *italic*, `inline code`, and a URL:
https://example.com

---

## Sample Notes

### Buffer 1

The quick brown fox jumps over the lazy dog.

### Buffer 2

Pack my box with five dozen liquor jugs.

### Buffer 3

Sphinx of black quartz, judge my vow.

### Buffer 4

How vexingly quick daft zebras jump!

---

## Escaped Characters

The following characters should appear literally:

*asterisks*

# not a heading

`not code`

_underscores_

---

## Final Paragraph

Markdown is intentionally simple. A good renderer should handle headings, lists, emphasis, links, tables, blockquotes, code blocks, and horizontal rules consistently across phones, tablets, and e-ink devices.

**End of Test Document**
    