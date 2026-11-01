# ElmerPad Virtual Keyboard Specification

Edit the tables below to define the layout and functionality for the new three-layer virtual keyboard system.

## Layer 1: Primary Alphabet (Alpha)
*Target: Standard writing and primary commands.*

| Row | Label | Width | Primary Func | Shift Func | Ctrl Func | Alt Func |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `ESC` | 1.5 | `CMD: EXIT_SPLIT` | - | - | - |
| 1 | `F1` | 1.0 | `CMD: BUFFER_1` | - | - | - |
| 2 | `Q` | 1.2 | `char "q"` | `char "Q"` | - | - |
| 3 | `A` | 1.2 | `char "a"` | `char "A"` | `CMD: SELECT_ALL` | - |
| 4 | `Z` | 1.2 | `char "z"` | `char "Z"` | `CMD: UNDO` | - |
| 5 | `NUM` | 2.5 | `KBD: NUMERIC` | - | - | - |
| 5 | `PUNC` | 2.5 | `KBD: PUNCTUATION` | - | - | - |

## Layer 2: Punctuation & Special Functions (Punc)
*Target: Symbols, formatting marks, and secondary app controls.*

| Row | Label | Width | Primary Func | Shift Func | Ctrl Func | Alt Func |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `ESC` | 1.5 | `KBD: ALPHA` | - | - | - |
| 2 | `.` | 1.2 | `char "."` | `char ">"` | - | - |
| 3 | `[` | 1.2 | `char "["` | `char "{"` | - | - |

## Layer 3: Numeric (Num)
*Target: Number pad and math symbols.*

| Row | Label | Width | Primary Func | Shift Func | Ctrl Func | Alt Func |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `ESC` | 1.5 | `KBD: ALPHA` | - | - | - |
| 2 | `1` | 1.2 | `char "1"` | `char "!"` | - | - |

---

## Global Design Rules
1. **F-Key Row**: (e.g., "Visible on all layers by default, toggleable in Settings")
2. **Alignment**: (e.g., "Stagger rows like a real typewriter" or "Grid aligned")
3. **Tactile Feedback**: (e.g., "Vibrate on every keypress")
4. **Modifier Behavior**: (e.g., "Latched (tap once to apply to next key)")

## Key Function Guide
Use these terms in the tables above:
* **`char "x"`**: Inserts a character.
* **`CMD: [Name]`**: Triggers app logic (e.g., `SAVE`, `UNDO`, `FIND`, `REPLACE`, `BRIGHT_UP`).
* **`MOD: [Name]`**: Latch `CTRL`, `ALT`, or `SHIFT`.
* **`KBD: [Name]`**: Switch to `ALPHA`, `PUNCTUATION`, or `NUMERIC` layout.
* **`SPACER`**: Empty space of specific width.
