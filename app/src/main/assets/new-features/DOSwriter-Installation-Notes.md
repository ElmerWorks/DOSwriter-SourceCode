# DOSwriter Installation & Startup Logic

This document details the internal initialization sequence and configuration requirements for DOSwriter. Understanding these stages is critical for troubleshooting file operation errors and preparing user-facing startup instructions.

## 1. Initial Launch Sequence (The "First Run")

When DOSwriter is launched for the first time, it performs several one-time setup tasks:

### Data Wipe & SharedPreferences
- If the `first_run_complete` flag is missing from private settings, the app wipes all existing buffer and setting data to ensure a clean state.
- It then initializes 17 buffers: **8 Main buffers**, **8 To-Do buffers**, and **1 Scratchpad**.

### Asset Pre-population
- **To-Do Workspace**: The 8 To-Do buffers are automatically populated with sample task lists found in `assets/examples/PDA/`.
- **Thumbnails**: The 8 Main buffers are automatically linked to the **Haekel** biological illustrations found in `assets/examples/thumbnails/haekel/`. These are set to **Image Priority** mode so they appear immediately in the Workspace Layout View (`Esc`).

---

## 2. Permissions and Hardware Discovery

### Android Permissions
On first startup, the app requests the following:
- **Storage Access**: Required for reading/writing files and linking custom fonts.
- **Media Access**: Specifically for the **Pictures** folder and the **Linked Image Folder** used for screenshots and the slideshow viewer.
- **Brightness Permission**: A manual utility in `ALT + S > Advanced Features` allows the user to grant DOSwriter permission to sync the app's brightness slider with the Android system level.

### Hardware Profile Wizard
If the app cannot auto-detect the display type, it triggers a setup wizard:
1. **Step 1: Display Type**: User selects between Backlit (LCD), Color E-ink, or Monochrome E-ink.
2. **Step 2: Manufacturer**: User selects between BOOX, Bigme, Supernote, or Other.
3. **Step 3: Storage Setup**: The app prompts the user to **Link a Working Folder** immediately. This is highly recommended to avoid file permission issues later. Users can opt-out by selecting "I'll Do This Later."
4. **Step 4: Initialization**: The app automatically sets optimized defaults (e.g., forces high-contrast "E-Ink" theme, increases menu font size for Mono devices, and sets status bar positions).

---

## 3. The "Working Folder" Requirement (Critical for File Operations)

A common cause of "Save Failed" or "File Not Found" errors is the lack of a **Linked Working Folder**.

### Technical Note: Folder "Activation"
Some Android implementations of the Storage Access Framework (SAF) may fail to write to a newly linked empty folder until the system "recognizes" it as an active directory. 
- **Workaround**: If saving fails even after linking, try creating a single subfolder using the standard Android File Manager inside your linked directory. This "activates" the directory for the system, after which DOSwriter can reliably create and edit files within it.
- **Onboarding**: To mitigate this, the setup wizard now guides users to perform this link during the first five minutes of use.

### Redirection Logic
- DOSwriter does not have permission to browse the entire Android file system. It requires the user to designate one or more "Linked Folders" as its working directory.
- If a user attempts to access the **FILE** menu (`ALT + F`) or the **File Browser** (`CTRL + ALT + F`) without a linked folder, the app will:
    1. Display a long toast message explaining the requirement.
    2. Automatically redirect the user to `Settings > File Manager`.
- Once a folder is linked using the Android `DocumentTree` picker, the app remembers this location and uses it for all future Open/Save operations.

### System vs. DOSwriter Browser
Users have a choice in `Settings > File Manager`:
- **DOSwriter Browser (Default)**: A keyboard-optimized, vintage-style browser. Requires a Linked Folder.
- **System Browser**: Uses the standard Android "Files" picker. Does not require a linked folder but is less ergonomic for keyboard use.

---

## 4. Default Application Settings

### Global Settings Mode
By default, **Global Settings** is enabled (`ALT + S > Global Settings: ON`). This means any change to Font, Margin, or Theme in the current buffer is instantly propagated to all other buffers. This ensures a consistent writing experience but can be disabled if a user wants independent styling per buffer.

### Help & Onboarding
- **Startup Tutorial**: Enabled by default. Shows a multi-page interactive guide on every cold boot until manually disabled in `Settings > Advanced Features > Text Display`.
- **Status Reminders**: Short help prompts appear in the status bar to guide users through complex modes like PDF navigation or Markdown rendering.

## 5. Potential Startup Failure Points (Troubleshooting)

1.  **Restricted URI Permissions**: If a user links a folder on a removable SD card or a cloud-synced folder, Android may occasionally revoke the "persistable permission," causing "Save Failed." Re-linking the folder in Settings resolves this.
2.  **Conflict with Hardware Keyboards**: Some keyboards intercept `F1-F8` keys. Users may need to toggle their `Fn` lock or use the `ALT + 1-8` alternative for buffer swapping.
3.  **Pathing and Case Sensitivity**: When using the DOSwriter File Browser, manual folder creation and file naming must adhere to standard file system rules.
