# DOSwriter Remote File Sync Client Specification

## 1. Overview
The DOSwriter Remote File Sync client provides real-time synchronization between the Android application and a desktop host (Windows or Linux) running the DOSwriter Go-based sync server. Communication occurs over a Local Area Network (LAN) using WebSockets for real-time updates and HTTP for RESTful API calls.

## 2. Technical Stack
- **Library**: OkHttp (WebSocket and HTTP client)
- **Data Format**: JSON (via Google Gson)
- **Protocol**: 
    - WebSocket (`ws://host:port/ws`) for persistent bi-directional communication.
    - HTTP (`http://host:port/api/...`) for stateless operations like file listing and image fetching.

## 3. Communication Protocol

### 3.1 Authentication
Every request (both WebSocket handshake and HTTP calls) must include the following header:
- `X-API-Token`: A security token defined by the host server.

### 3.2 WebSocket Lifecycle
- **Handshake**: Upon connection, the client sends a "hello" message:
  ```json
  {"type": "hello", "client": "DOSwriter", "version": "1.0"}
  ```
- **Server Response**: The server acknowledges with `hello_ok`.
- **Keep-Alive**: Persistent connection is maintained for real-time notifications from the host.

### 3.3 Message Types (Client to Server)
- **Open File**: Requests the content of a specific remote path.
  ```json
  {"type": "open", "path": "documents/draft.txt"}
  ```
- **Save File**: Pushes local buffer content to the host.
  ```json
  {"type": "save", "path": "documents/draft.txt", "content": "..."}
  ```

### 3.4 Message Types (Server to Client)
- **File Received**: Server provides requested file content.
  ```json
  {"type": "file", "path": "...", "content": "..."}
  ```
- **Save OK**: Server confirms a successful write operation.
  ```json
  {"type": "save_ok", "path": "..."}
  ```
- **File System Event**: Server notifies the client of external modifications (e.g., if the user edits the file in VS Code on the desktop).
  ```json
  {"type": "fs_event", "path": "...", "event": "modified"}
  ```
- **Error**: Server reports an internal failure.
  ```json
  {"type": "error", "error": "message"}
  ```

## 4. HTTP API Endpoints

### 4.1 File Listing (`/api/list`)
- **Method**: GET
- **Parameter**: `path` (defaults to `.`)
- **Return**: A JSON array of file objects:
  ```json
  [
    {"name": "drafts", "isDir": true},
    {"name": "notes.txt", "isDir": false}
  ]
  ```

### 4.2 Image Fetching (`/api/file`)
- **Method**: GET
- **Parameter**: `path`
- **Return**: Raw binary data of the file (typically JPEG). Used for the DOSwriter Multimedia Viewer.

## 5. Client Logic & Edge Cases

### 5.1 Real-Time Conflict Resolution
- **Auto-Reload**: If a `fs_event` (modified) is received for the active buffer and the buffer is "Clean" (no unsaved local changes), the client reloads the content instantly.
- **Merge Prompt**: If the buffer is "Dirty" (unsaved local changes), the client prompts the user to either discard local changes and reload or ignore the remote update.

### 5.2 Background Sync
- **Auto-Push**: When enabled, the client maintains a timer that resets with every keystroke. After the user-defined timeout (1-30 minutes) of inactivity, the client automatically sends a `save` command to the host if the buffer is dirty.

### 5.3 Connection Management
- The client monitors connection stability. If a `onFailure` or `onClosed` event occurs, it updates the internal state and notifies the UI (Status Bar) to remove the `[SYNCED]` indicator.

## 6. Security & Privacy
- **LAN Restriction**: Communication is designed for private local networks.
- **Local Data Only**: No document content or voice data is transmitted to third-party cloud services.
- **Cleartext Policy**: Support for unencrypted traffic is explicitly allowed in `AndroidManifest.xml` to facilitate local development without SSL overhead.
