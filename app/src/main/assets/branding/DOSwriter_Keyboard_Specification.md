# DOSwriter Virtual Keyboard Specification

Edit the tables below to define the layout and functionality for the new three-layer virtual keyboard system.

## Layer 1: Primary Alphabet (Alpha)
*Target: Standard writing and primary commands.*

| Row | Label | Width | Primary Func | Shift Func | Ctrl Func | Alt Func |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `ESC` | 1.5 | `CMD: EXIT_SPLIT` | - | - | - |
| 1 | `F1` | 1.0 | `CMD: BUFFER_1` | - | - | - |
| 1 | `F2` | 1.0 | `CMD: BUFFER_2` | - | - | - |
| 1 | `F3` | 1.0 | `CMD: BUFFER_3` | - | - | - |
| 1 | `F4` | 1.0 | `CMD: BUFFER_4` | - | - | - |
| 1 | `F5` | 1.0 | `CMD: BUFFER_5` | - | - | - |
| 1 | `F6` | 1.0 | `CMD: BUFFER_6` | - | - | - |
| 1 | `F7` | 1.0 | `CMD: BUFFER_7` | - | - | - |
| 1 | `F8` | 1.0 | `CMD: BUFFER_8` | - | - | - |
| 1 | `F9` | 1.0 | `CMD: SCRATCHPAD` | - | - | - |
| 1 | `F10` | 1.0 | `CMD: SETTINGS` | - | - | - |
| 1 | `F11` | 1.0 | `CMD: TYPEWRITER` | - | - | - |
| 1 | `F12` | 1.0 | `CMD: OUTLINE` | - | - | - |
| 2 | `TAB` | 2.0 | `char "\t"` | - | - | - |
| 2 | `Q` | 1.3 | `char "q"` | `char "Q"` | - | - |
| 2 | `W` | 1.3 | `char "w"` | `char "W"` | - | - |
| 2 | `E` | 1.3 | `char "e"` | `char "E"` | - | - |
| 2 | `R` | 1.3 | `char "r"` | `char "R"` | - | `CMD: RENDER` |
| 2 | `T` | 1.3 | `char "t"` | `char "T"` | - | `CMD: TYPEWRITER` |
| 2 | `Y` | 1.3 | `char "y"` | `char "Y"` | - | - |
| 2 | `U` | 1.3 | `char "u"` | `char "U"` | - | - |
| 2 | `I` | 1.3 | `char "i"` | `char "I"` | `CMD: SCREENSHOT` | - |
| 2 | `O` | 1.3 | `char "o"` | `char "O"` | `CMD: OPEN` | - |
| 2 | `P` | 1.3 | `char "p"` | `char "P"` | `CMD: PDF_EXPORT` | - |
| 2 | `⌫` | 2.5 | `CMD: BACKSPACE` | - | - | - |
| 3 | `CTRL` | 2.0 | `MOD: CTRL` | - | - | - |
| 3 | `A` | 1.3 | `char "a"` | `char "A"` | `CMD: SELECT_ALL` | - |
| 3 | `S` | 1.3 | `char "s"` | `char "S"` | `CMD: SAVE` | `CMD: SPLIT` |
| 3 | `D` | 1.3 | `char "d"` | `char "D"` | - | `CMD: SYNC` |
| 3 | `F` | 1.3 | `char "f"` | `char "F"` | `CMD: FIND` | - |
| 3 | `G` | 1.3 | `char "g"` | `char "G"` | - | - |
| 3 | `H` | 1.3 | `char "h"` | `char "H"` | `CMD: HELP` | `CMD: STATUS` |
| 3 | `J` | 1.3 | `char "j"` | `char "J"` | - | - |
| 3 | `K` | 1.3 | `char "k"` | `char "K"` | `CMD: BOOKMARK` | `CMD: V_KBD` |
| 3 | `L` | 1.3 | `char "l"` | `char "L"` | `CMD: SETTINGS` | - |
| 3 | `ENTER`| 3.8 | `char "\n"` | - | - | - |
| 4 | `SHIFT`| 3.4 | `MOD: SHIFT` | - | - | - |
| 4 | `Z` | 1.3 | `char "z"` | `char "Z"` | `CMD: UNDO` | - |
| 4 | `X` | 1.3 | `char "x"` | `char "X"` | `CMD: CUT` | `CMD: EXIT` |
| 4 | `C` | 1.3 | `char "c"` | `char "C"` | `CMD: COPY` | `CMD: CENTER` |
| 4 | `V` | 1.3 | `char "v"` | `char "V"` | `CMD: PASTE` | - |
| 4 | `B` | 1.3 | `char "b"` | `char "B"` | `CMD: BUFFERS` | `CMD: BUFFERS` |
| 4 | `N` | 1.3 | `char "n"` | `char "N"` | `CMD: NEW` | `CMD: FOCUS` |
| 4 | `M` | 1.3 | `char "m"` | `char "M"` | - | `CMD: MARGINS` |
| 4 | `↑` | 2.5 | `CMD: UP` | `CMD: SELECT_UP`| - | - |
| 4 | `DEL` | 2.5 | `CMD: DELETE` | - | - | - |
| 5 | `NUM` | 2.5 | `KBD: NUMERIC` | - | - | - |
| 5 | `ALT` | 2.5 | `MOD: ALT` | - | - | - |
| 5 | `SPACE`| 5.0 | `char " "` | - | - | - |
| 5 | `←` | 2.5 | `CMD: LEFT` | `CMD: SEL_LEFT` | - | - |
| 5 | `↓` | 2.5 | `CMD: DOWN` | `CMD: SEL_DOWN` | - | - |
| 5 | `→` | 2.5 | `CMD: RIGHT` | `CMD: SEL_RIGHT`| - | - |

## Layer 2: Punctuation & Special Functions (Punc)
*Target: Symbols, formatting marks, and secondary app controls.*

| Row | Label | Width | Primary Func | Shift Func | Ctrl Func | Alt Func |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `ESC` | 2.0 | `KBD: ALPHA` | - | - | - |
| 1 | `↕+` | 1.25| `CMD: KB_UP` | - | - | - |
| 1 | `↕-` | 1.25| `CMD: KB_DOWN` | - | - | - |
| 1 | `PT` | 2.0 | `CMD: TOGGLE_PT` | - | - | - |
| 1 | `UNDO` | 2.0 | `CMD: UNDO` | - | - | - |
| 2 | `!` | 1.0 | `char "!"` | - | - | - |
| 2 | `@` | 1.0 | `char "@"` | - | - | - |
| 2 | `#` | 1.0 | `char "#"` | - | - | - |
| 2 | `$` | 1.0 | `char "$"` | - | - | - |
| 2 | `%` | 1.0 | `char "%"` | - | - | - |
| 2 | `^` | 1.0 | `char "^"` | - | - | - |
| 2 | `&` | 1.0 | `char "&"` | - | - | - |
| 2 | `*` | 1.0 | `char "*"` | - | - | - |
| 2 | `(` | 1.0 | `char "("` | - | - | - |
| 2 | `)` | 1.0 | `char ")"` | - | - | - |
| 3 | `-` | 1.0 | `char "-"` | - | `CMD: FONT_DOWN`| - |
| 3 | `_` | 1.0 | `char "_"` | - | - | - |
| 3 | `=` | 1.0 | `char "="` | - | - | - |
| 3 | `+` | 1.0 | `char "+"` | - | `CMD: FONT_UP` | - |
| 3 | `[` | 1.0 | `char "["` | - | - | - |
| 3 | `]` | 1.0 | `char "]"` | - | - | - |
| 3 | `{` | 1.0 | `char "{"` | - | - | - |
| 3 | `}` | 1.0 | `char "}"` | - | - | - |
| 3 | `\` | 1.0 | `char "\\"` | - | - | - |
| 3 | `\|` | 1.0 | `char "\|"` | - | - | - |
| 4 | `;` | 1.0 | `char ";"` | - | - | - |
| 4 | `:` | 1.0 | `char ":"` | - | - | - |
| 4 | `'` | 1.0 | `char "'"` | - | - | - |
| 4 | `"` | 1.0 | `char "\""` | - | - | - |
| 4 | `,` | 1.0 | `char ","` | - | - | - |
| 4 | `<` | 1.0 | `char "<"` | - | - | - |
| 4 | `.` | 1.0 | `char "."` | - | - | - |
| 4 | `>` | 1.0 | `char ">"` | - | - | - |
| 4 | `/` | 1.0 | `char "/"` | - | - | - |
| 4 | `?` | 1.0 | `char "?"` | - | - | - |

## Layer 3: Numeric (Num)
*Target: Number pad and math symbols.*

| Row | Label | Width | Primary Func | Shift Func | Ctrl Func | Alt Func |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `ESC` | 2.0 | `KBD: ALPHA` | - | - | - |
| 2 | `1` | 1.0 | `char "1"` | - | - | - |
| 2 | `2` | 1.0 | `char "2"` | - | - | - |
| 2 | `3` | 1.0 | `char "3"` | - | - | - |
| 3 | `4` | 1.0 | `char "4"` | - | - | - |
| 3 | `5` | 1.0 | `char "5"` | - | - | - |
| 3 | `6` | 1.0 | `char "6"` | - | - | - |
| 4 | `7` | 1.0 | `char "7"` | - | - | - |
| 4 | `8` | 1.0 | `char "8"` | - | - | - |
| 4 | `9` | 1.0 | `char "9"` | - | - | - |
| 5 | `0` | 2.0 | `char "0"` | - | - | - |
| 5 | `.` | 1.0 | `char "."` | - | - | - |

---

## Global Design Rules
1. **F-Key Row**: Visible on Alpha layer by default.
2. **Alignment**: Staggered rows (Standard QWERTY layout).
3. **Tactile Feedback**: Haptic vibration on every keypress.
4. **Modifier Behavior**: Latched (tap once to apply to next key). double-tap to lock.

## Key Function Guide
Use these terms in the tables above:
* **`char "x"`**: Inserts a character.
* **`CMD: [Name]`**: Triggers app logic (e.g., `SAVE`, `UNDO`, `FIND`, `REPLACE`, `BRIGHT_UP`).
* **`MOD: [Name]`**: Latch `CTRL`, `ALT`, or `SHIFT`.
* **`KBD: [Name]`**: Switch to `ALPHA`, `PUNCTUATION`, or `NUMERIC` layout.
* **`SPACER`**: Empty space of specific width.
