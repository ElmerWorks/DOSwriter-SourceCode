# DOSwriter Software Specification
# Vietnamese TELEX Input Method

**Document Version:** 1.0

---

# Purpose

Implement an optional Vietnamese TELEX input mode within DOSwriter to allow efficient entry of Vietnamese text using a standard US QWERTY keyboard.

The implementation shall operate entirely within DOSwriter and shall not require an external Android Input Method Editor (IME).

The editor shall continue to store all text as standard UTF-8 Unicode.

---

# Design Goals

- Support standard Vietnamese TELEX typing.
- Preserve normal editor performance.
- Operate consistently on Android phones, tablets, and e-ink devices.
- Be independent of Android keyboard implementations.
- Maintain complete compatibility with UTF-8 files.
- Permit future expansion to VNI and other input methods.

---

# Stage 1 — Base Character Conversion

## Objective

Implement conversion of Latin character sequences into Vietnamese base characters.

## Character Mapping

| Input | Output |
|-------|--------|
| aa | â |
| aw | ă |
| ee | ê |
| oo | ô |
| ow | ơ |
| uw | ư |
| dd | đ |

Uppercase equivalents shall also be supported.

Examples

```
aa → â
aw → ă
AA → Â
Aw → Ă
dd → đ
DD → Đ
```

## Functional Requirements

- Conversion occurs immediately after the second character.
- Cursor remains after the converted character.
- Undo restores the original keystrokes.
- Invalid combinations remain unchanged.

---

# Stage 2 — Tone Mark Processing

## Objective

Implement Vietnamese tone marks.

## Tone Keys

| Key | Tone |
|-----|------|
| s | Acute (sắc) |
| f | Grave (huyền) |
| r | Hook (hỏi) |
| x | Tilde (ngã) |
| j | Dot Below (nặng) |

Examples

```
a + s → á
a + f → à
a + r → ả
a + x → ã
a + j → ạ
```

The same applies to modified vowels.

```
â + s → ấ
ă + s → ắ
ê + s → ế
ô + s → ố
ơ + s → ớ
ư + s → ứ
```

## Functional Requirements

The editor shall

- locate the active vowel
- apply the requested tone
- replace an existing tone
- never stack multiple tone marks

---

# Stage 3 — Vietnamese Syllable Processing

## Objective

Correctly position tone marks within complete Vietnamese syllables.

Examples

```
tieengs → tiếng
truowng → trường
nguyeenx → Nguyễn
```

## Functional Requirements

The processor shall

- examine only the current syllable
- identify Vietnamese vowel groups
- determine the principal vowel
- apply the tone to the proper character
- require no dictionary lookup

## Editing Behavior

- Backspace functions normally.
- Undo restores previous text.
- Cursor movement ends the current composition.
- Editing inside an existing word shall continue to function normally.

---

# Stage 4 — Complete TELEX Compatibility

## Objective

Provide behavior substantially compatible with UniKey, Gboard, and other standard Vietnamese TELEX implementations.

## Functional Requirements

Implement

- repeated modifier handling
- modifier cancellation where appropriate
- correction of existing composed characters
- editing inside composed words
- uppercase support
- mixed-case support
- punctuation boundaries
- line boundaries
- copy/paste compatibility
- UTF-8 compatibility

---

# User Interface

Add a keyboard setting.

```
Keyboard Mode

• Standard
• Vietnamese TELEX
```

Future versions may include

```
• Vietnamese VNI
• DOSwriter Vietnamese
```

---

# Internal Software Architecture

The Vietnamese processor shall be implemented as an independent input filter.

```
Physical Keyboard
        │
        ▼
Keyboard Event
        │
        ▼
Vietnamese TELEX Processor
        │
        ▼
Unicode Character Stream
        │
        ▼
Editor Buffer
```

The editor shall never store TELEX sequences.

Only completed Unicode characters shall exist within the document.

---

# Recommended Class Design

The Vietnamese processor should be completely independent of the editor UI.

Recommended class:

```
TelexProcessor
```

Responsibilities

- Receive keyboard events.
- Maintain composition state.
- Detect Vietnamese syllables.
- Perform character substitutions.
- Apply tone marks.
- Return completed Unicode text.

The editor should not contain Vietnamese-specific logic.

Instead, every key press should be routed through the processor.

```
Keyboard Event

        │

        ▼

TelexProcessor.processKey(...)

        │

        ▼

Unicode Result

        │

        ▼

Insert Into Text Buffer
```

## Suggested Public Interface

```java
class TelexProcessor {

    ProcessResult processKey(
        String currentSyllable,
        char keyPressed,
        int cursorPosition
    );

    void reset();

}
```

Where `ProcessResult` contains

```java
class ProcessResult {

    String updatedText;

    int newCursorPosition;

    boolean textChanged;

}
```

This architecture keeps all Vietnamese logic inside one reusable component.

Future processors can implement the same interface.

Examples

```
TelexProcessor
VniProcessor
DOSwriterVietnameseProcessor
```

allowing the editor to switch processors without changing editor code.

---

# Data Storage

Documents remain standard UTF-8 text.

No proprietary encoding shall be introduced.

---

# Performance Requirements

- No perceptible typing delay.
- Processing limited to the active syllable.
- No whole-document scanning.
- Suitable for very large documents.
- Optimized for e-ink devices.

---

# Error Handling

Unknown key sequences shall remain unchanged.

Invalid combinations shall never corrupt document contents.

Unexpected input shall fall back to normal keyboard behavior.

---

# Testing

## Stage 1

```
aa → â
aw → ă
dd → đ
```

## Stage 2

```
a + s → á
â + s → ấ
ơ + f → ờ
ư + j → ự
```

## Stage 3

```
tieengs → tiếng
truowng → trường
nguyeenx → Nguyễn
```

## Stage 4

Verify compatibility with standard TELEX implementations using a representative Vietnamese text corpus.

---

# Future Enhancements

- Vietnamese VNI input
- DOSwriter Vietnamese input method
- Vietnamese virtual keyboard
- User-configurable TELEX options
- Live composition highlighting
- Automatic language detection
- Additional Latin-script input processors

---

# Success Criteria

The implementation is complete when

1. All Vietnamese characters can be entered using TELEX.
2. Behavior matches standard TELEX implementations.
3. Documents remain UTF-8 compatible.
4. Typing speed is indistinguishable from normal editing.
5. Operation is consistent across Android phones, tablets, and e-ink devices.