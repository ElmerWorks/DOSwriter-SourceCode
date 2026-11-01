# ElmerPad Virtual Keyboard Specification

## Layer 1: Primary Alphabet (Alpha)
*Target: Standard writing and primary commands.*

| Row | Label | Width | Primary Func | Shift Func | Ctrl Func | Alt Func |
|---|---|---:|---|---|---|---|
|1|ESC|1.5|CMD: EXIT_SPLIT|-|-|-|
|1|F1|1.0|CMD: F1|-|-|-|
|1|F2|1.0|CMD: F2|-|-|-|
|1|F3|1.0|CMD: F3|-|-|-|
|1|F4|1.0|CMD: F4|-|-|-|
|1|F5|1.0|CMD: F5|-|-|-|
|1|F6|1.0|CMD: F6|-|-|-|
|1|F7|1.0|CMD: F7|-|-|-|
|1|F8|1.0|CMD: F8|-|-|-|
|1|F9|1.0|CMD: F9|-|-|-|
|1|F10|1.0|CMD: F10|-|-|-|
|1|F11|1.0|CMD: F11|-|-|-|
|1|F12|1.0|CMD: F12|-|-|-|
|2|ESC|1.5|CMD: EXIT_SPLIT|-|-|-|
|2|Q|1.0|char "q"|char "Q"|-|-|
|2|W|1.0|char "w"|char "W"|-|-|
|2|E|1.0|char "e"|char "E"|-|-|
|2|R|1.0|char "r"|char "R"|-|-|
|2|T|1.0|char "t"|char "T"|-|-|
|2|U|1.0|char "u"|char "U"|-|-|
|2|I|1.0|char "i"|char "I"|-|-|
|2|O|1.0|char "o"|char "O"|-|-|
|2|P|1.0|char "p"|char "P"|-|-|
|2|NUM|1.5|KBD: NUMERIC|-|-|-|
|3|PUNC|1.5|KBD: PUNCTUATION|-|-|-|
|3|A|1.0|char "a"|char "A"|CMD: SELECT_ALL|-|
|3|S|1.0|char "s"|char "S"|CMD: SAVE|-|
|3|D|1.0|char "d"|char "D"|-|-|
|3|F|1.0|char "f"|char "F"|CMD: FIND|-|
|3|G|1.0|char "g"|char "G"|-|-|
|3|H|1.0|char "h"|char "H"|-|-|
|3|J|1.0|char "j"|char "J"|-|-|
|3|K|1.0|char "k"|char "K"|-|-|
|3|L|1.0|char "l"|char "L"|-|-|
|3|BCK/DEL|1.5|CMD: BACKSPACE|CMD: DELETE|-|-|
|4|SHIFT|1.5|MOD: SHIFT|-|-|-|
|4|Z|1.0|char "z"|char "Z"|CMD: UNDO|-|
|4|X|1.0|char "x"|char "X"|CMD: CUT|-|
|4|C|1.0|char "c"|char "C"|CMD: COPY|-|
|4|V|1.0|char "v"|char "V"|CMD: PASTE|-|
|4|B|1.0|char "b"|char "B"|-|-|
|4|N|1.0|char "n"|char "N"|-|-|
|4|M|1.0|char "m"|char "M"|-|-|
|4|UP|1.0|CMD: CURSOR_UP|-|-|-|
|4|ENTER|1.5|CMD: ENTER|-|-|-|
|5|CTRL|1.5|MOD: CTRL|-|-|-|
|5|ALT|1.5|MOD: ALT|-|-|-|
|5|SPACE|3.0|char " "|-|-|-|
|5|LEFT|1.0|CMD: CURSOR_LEFT|-|-|-|
|5|DOWN|1.0|CMD: CURSOR_DOWN|-|-|-|
|5|RIGHT|1.0|CMD: CURSOR_RIGHT|-|-|-|

## Layer 2: Punctuation & Special Functions (Punc)

| Row | Label | Width | Primary Func | Shift Func | Ctrl Func | Alt Func |
|---|---|---:|---|---|---|---|
|1|ESC|1.5|KBD: ALPHA|-|-|-|
|1|\|-|1.0|char "\|"|CMD: DECREASE_INDENT|-|-|
|1|\|+|1.0|char "\|"|CMD: INCREASE_INDENT|-|-|
|1|F-|1.0|CMD: FIND_PREV|-|-|-|
|1|F+|1.0|CMD: FIND_NEXT|-|-|-|
|1|B-|1.0|CMD: BRIGHT_DOWN|-|-|-|
|1|B+|1.0|CMD: BRIGHT_UP|-|-|-|
|1|V-|1.0|CMD: VOL_DOWN|-|-|-|
|1|V+|1.0|CMD: VOL_UP|-|-|-|
|1|NUM|1.5|KBD: NUMERIC|-|-|-|
|2|!|1.0|char "!"|-|-|-|
|2|@|1.0|char "@"|-|-|-|
|2|#|1.0|char "#"|-|-|-|
|2|$|1.0|char "$"|-|-|-|
|2|%|1.0|char "%"|-|-|-|
|2|^|1.0|char "^"|-|-|-|
|2|&|1.0|char "&"|-|-|-|
|2|?|1.0|char "?"|-|-|-|
|2|"|1.0|char "\""|-|-|-|
|2|BACKSPACE|1.5|CMD: BACKSPACE|-|-|-|
|3|_|1.0|char "_"|-|-|-|
|3|[|1.0|char "["|char "{"|-|-|
|3|]|1.0|char "]"|char "}"|-|-|
|3|{|1.0|char "{"|-|-|-|
|3|}|1.0|char "}"|-|-|-|
|3|\|1.0|char "\\"|-|-|-|
|3|;|1.0|char ";"|-|-|-|
|3|,|1.0|char ","|char "<"|-|-|
|3|.|1.0|char "."|char ">"|-|-|
|4|TAB|1.5|CMD: TAB|-|-|-|
|4|(|1.0|char "("|-|-|-|
|4|)|1.0|char ")"|-|-|-|
|4|<|1.0|char "<"|-|-|-|
|4|>|1.0|char ">"|-|-|-|
|4|\||1.0|char "\|"|-|-|-|
|4|:|1.0|char ":"|-|-|-|
|4|UP|1.0|CMD: CURSOR_UP|-|-|-|
|4|DEL|1.5|CMD: DELETE|-|-|-|
|5|CTRL|1.5|MOD: CTRL|-|-|-|
|5|F10|1.0|CMD: F10|-|-|-|
|5|F11|1.0|CMD: F11|-|-|-|
|5|F12|1.0|CMD: F12|-|-|-|
|5|PT|1.0|CMD: PASTE_TEXT|-|-|-|
|5|CP|1.0|CMD: COPY|-|-|-|
|5|LEFT|1.0|CMD: CURSOR_LEFT|-|-|-|
|5|DOWN|1.0|CMD: CURSOR_DOWN|-|-|-|
|5|RIGHT|1.0|CMD: CURSOR_RIGHT|-|-|-|

## Layer 3: Numeric (Num)

| Row | Label | Width | Primary Func | Shift Func | Ctrl Func | Alt Func |
|---|---|---:|---|---|---|---|
|1|ESC|1.5|KBD: ALPHA|-|-|-|
|1|BLANK|1.0|SPACER|-|-|-|
|1|1|1.0|char "1"|-|-|-|
|1|2|1.0|char "2"|-|-|-|
|1|3|1.0|char "3"|-|-|-|
|1|BLANK|1.0|SPACER|-|-|-|
|1|PUNC|1.5|KBD: PUNCTUATION|-|-|-|
|2|UNDO|1.5|CMD: UNDO|-|-|-|
|2|BLANK|1.0|SPACER|-|-|-|
|2|4|1.0|char "4"|-|-|-|
|2|5|1.0|char "5"|-|-|-|
|2|6|1.0|char "6"|-|-|-|
|2|BLANK|1.0|SPACER|-|-|-|
|2|/|1.0|char "/"|-|-|-|
|2|+|1.0|char "+"|-|-|-|
|3|(|1.0|char "("|char "<"|-|-|
|3|)|1.0|char ")"|char ">"|-|-|
|3|7|1.0|char "7"|-|-|-|
|3|8|1.0|char "8"|-|-|-|
|3|9|1.0|char "9"|-|-|-|
|3|BLANK|1.0|SPACER|-|-|-|
|3|=|1.0|char "="|-|-|-|
|3|-|1.0|char "-"|-|-|-|
|4|$|1.0|char "$"|char "#"|-|-|
|4|%|1.0|char "%"|char "^"|-|-|
|4|,|1.0|char ","|-|-|-|
|4|UP|1.0|CMD: CURSOR_UP|-|-|-|
|4|*|1.0|char "*"|-|-|-|
|5|SHIFT|1.5|MOD: SHIFT|-|-|-|
|5|BLANK|1.0|SPACER|-|-|-|
|5|0|1.0|char "0"|-|-|-|
|5|LEFT|1.0|CMD: CURSOR_LEFT|-|-|-|
|5|DOWN|1.0|CMD: CURSOR_DOWN|-|-|-|
|5|RIGHT|1.0|CMD: CURSOR_RIGHT|-|-|-|
