# ElmerPad - Desktop Sync Overview (Android Client)

ElmerPad features a lightweight, real-time synchronization system designed to bridge your mobile writing environment with your desktop workstation (Windows or Linux) over a Local Area Network (LAN).

## 1. Connection Setup
Access the sync controls via **F10 (Settings) -> Desktop Sync**.

*   **Host IP & Port**: Enter the local IP address of your PC and the port specified by your Go daemon (default: `12345`).
*   **Token**: Enter the API security token defined in your server's startup command.
*   **Connect/Disconnect**: Establishes or terminates the persistent WebSocket link.

## 2. File Synchronization Functions
Once connected, ElmerPad offers several ways to interact with your desktop files:

*   **Sync with Remote File**: Browse your desktop sync folder hierarchically. Selecting a file will pull its content into the active ElmerPad buffer and establish a persistent sync link.
*   **Save Buffer to Remote Host**: Upload a local buffer to your PC. You will be prompted to name the file, after which it is created on the host and automatically linked for future syncs.
*   **Manual Push Update**: Instantly force-saves your current tablet buffer to the desktop host.

## 3. Real-Time Synchronization Logic
Sync is designed to be "invisible" so you can focus entirely on drafting:

*   **Background Auto-Save**: When enabled, ElmerPad pushes your changes to the desktop automatically 3 seconds after you stop typing.
*   **Auto-Reload**: If you edit the synced file on your desktop (e.g., using VS Code), ElmerPad detects the change. If your buffer is "clean" (no unsaved tablet changes), it reloads the text instantly. If you have typed new text on the tablet, it will prompt you before overwriting.
*   **Visual Indicators**:
    *   **[SYNCED]**: Appears next to the filename in the status bar when a remote link is active.
    *   **(S)**: Appears in Split-Screen mode to denote a synced buffer.

## 4. Outline Manager (Binder) Integration
You can build complex project structures using the **Outline Manager (Ctrl + F12)** that live entirely on your desktop:

*   **Remote Linking (K)**: While setting up your outline, press **K** to link a file container to a specific path on your remote host.
*   **Project Persistence**: Selecting a remote-linked container from the **F12 Selection Menu** will automatically fetch and load the file from your desktop host into the current buffer.

## 5. Security & Network Policy
*   **LAN Only**: Communication is restricted to your local network; no data is sent to the cloud.
*   **Cleartext Support**: ElmerPad is configured to allow unencrypted local traffic to your Go daemon to avoid the complexity of local SSL certificates.
