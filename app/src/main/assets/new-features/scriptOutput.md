1.
Fixed App Menu Commands:
◦
Resolved the misalignment in the menu list pointer. Script commands like [CMD] ALT-F (File), ALT-E (Edit), ALT-V (View), and ALT-W (Workspace) now correctly trigger their respective menus.
◦
Synchronized all App Menu categories (0 through 7) to match the latest workstation layout.
2.
Resolved [STEP] / F8 Conflict:
◦
Removed the F8 shortcut from the Buffer Switcher in the global keybinding map.
◦
This ensures that when a script is paused with [STEP], pressing F8 will correctly resume script execution instead of switching to Buffer 8.
3.
Enhanced [TYPE] Command:
◦
Clipboard Placeholder: Added support for the {CLIPBOARD} placeholder. You can now use [TYPE] Current text: {CLIPBOARD} to automatically insert the contents of your internal clipboard into the script stream.
◦
Auto-Termination: The [TYPE] command now automatically appends a newline (\n) at the end of every typed block, ensuring cleaner document generation without manual "Enter" commands.
4.
Workstation Mnemonics:
◦
Added the SPLIT_SCREEN mnemonic to the command map for better readability in scripts (replaces or augments CTRL-ALT-RIGHT).
◦
Mapped standard keyboard combinations like CTRL-ALT-LEFT and CTRL-ALT-RIGHT to the Split-Screen orientation menu.
5.
Documentation Update:
◦
Revised app/src/main/assets/new-features/DOSwriter-Command-Syntax.md to document the {CLIPBOARD} placeholder and provide an updated list of supported navigation and workstation mnemonics.