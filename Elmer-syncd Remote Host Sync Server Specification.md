**Brief**  
A concise Android-side spec for the Go host so Android Studio can implement `SyncManager` (OkHttp WebSocket) and the UI integration.

**Protocol Summary**
- Transport: WebSocket primary (persistent). REST for listing & file fetch/save fallback.
- Auth: short token in `X-API-Token` header or sent in `hello.token`.
- Message format: JSON text frames. All messages include a `type` field.

**WebSocket messages (client ? server)**

- Hello (client -> server)
```json
{ "type":"hello", "client":"DOSwriter", "version":"1.0", "token":"<opt>" }
```
- Hello OK (server -> client)
```json
{ "type":"hello_ok", "server":"elmer-syncd", "root":"<server-root>" }
```
- Open (client -> server) --- request file contents
```json
{ "type":"open", "path":"notes/todo.txt" }
```
- File (server -> client) --- response to `open`
```json
{ "type":"file", "path":"notes/todo.txt", "content":"...file text...", "hash":"<sha256>" }
```
- Lock request (client -> server)
```json
{ "type":"lock_request", "path":"notes/todo.txt" }
```
- Lock granted / denied (server -> client)
```json
{ "type":"lock_granted", "path":"notes/todo.txt", "owner":"client-id" }
{ "type":"lock_denied", "path":"notes/todo.txt", "owner":"other" }
```
- Save (client -> server)
```json
{ "type":"save", "path":"notes/todo.txt", "content":"...","hash":"<sha256-of-content>" }
```
- Save OK (server -> client)
```json
{ "type":"save_ok", "path":"notes/todo.txt" }
```
- FS event (server -> clients)
```json
{ "type":"fs_event", "event":"created|modified|deleted", "path":"notes/todo.txt", "hash":"<sha256?>" }
```
- Ping / Pong (optional application-level)
```json
{ "type":"ping" } -> { "type":"pong" }
```
- Error (server -> client)
```json
{ "type":"error", "error":"not_found|forbidden|save_failed", "msg":"..." }
```

**REST endpoints**
- GET `/api/list?path=<relative>` -> JSON directory listing (name,isDir,size,mtime)
- GET `/api/file?path=<relative>` -> raw text
- POST `/api/file` -> JSON {path,content} (atomic save) --- use `X-API-Token` header

Use REST for initial directory browser and large reads if needed; prefer WS for live workflows.

**Client responsibilities (SyncManager)**
1. Connection
   - Use OkHttp WebSocket. Send `hello` immediately after open.
   - Provide token via header and/or `hello.token`.
   - Maintain heartbeat: send app-level `ping` every 30s or rely on WebSocket ping/pong.
   - Auto-reconnect with exponential backoff (start 1s, cap ~30s). On reconnect send `hello` then re-open any files currently viewed.

2. Open file flow
   - Send `open` -> receive `file`. Persist server `hash` with buffer.
   - Display content in editor buffer. Track `isRemoteLocked` flag (see Lock flow).

3. Lock model (Single Active Editor)
   - To allow desktop editing, client should:
     - Option A (preferred simple): request `lock_request` before local edits (for Android-initiated edit). If server/host is editing, server will `lock_denied` and client should set buffer read-only and show `REMOTE LOCK`.
     - If server requests lock (server may support host-initiated), client must set buffer read-only.
   - Lock timeouts: client should allow server to notify lock release; if a lock owner disconnects, server may auto-expire --- UI should reflect.

4. Save flow
   - When user saves locally:
     - If `isRemoteLocked` and owner != this client: block save or prompt conflict resolution.
     - Otherwise send `save` (include client-calculated SHA256 `hash`).
     - Wait for `save_ok`. On success update local file hash.
   - Use REST `POST /api/file` as fallback when WS is unavailable.

5. Handling `fs_event`
   - If event `path` is not open, ignore or refresh directory listing.
   - If event path is open:
     - If local buffer has no unsaved changes: fetch new contents (if not provided) and replace buffer.
     - If local buffer has unsaved changes: notify user of remote change, offer:
       - Keep local changes (upload as new save or ask for merge)
       - Overwrite local with remote
       - Open manual merge view (save a backup copy)
   - Use server `hash` to decide if local/remote differ.

6. Conflict resolution & backups
   - Always keep a local timestamped backup before overwriting (e.g., `<file>.backup-20260523T...`).
   - Offer simple three-way merge option later (base=server hash on open, local edits, newest server version).

7. Debounce and autosave
   - Client-side autosave can be debounced (e.g., 500--1000ms) to reduce network churn.
   - Respect server-side debounce (server currently debounces FS events ~200ms).

8. Discovery & connection UX
   - Support manual IP/port + token entry.
   - Optionally implement mDNS discovery to find `elmer-syncd` on LAN.
   - Show connection state in UI: Connected, Reconnecting, Disconnected, Auth failed.

9. Error handling
   - If server returns `forbidden` or `unauthorized`, prompt for token/credentials.
   - On `save_failed`, show error and keep unsaved buffer.
   - If WS closed unexpectedly, fall back to REST for read-only browsing.

10. Performance & battery
    - Keep WS alive but minimize messages: only send when user explicitly saves or requested operations.
    - Use keep-alive intervals of 30s and exponential reconnect backoff.

**Testing checklist for Android devs**
- Handshake:
  - Connect with correct token ? get `hello_ok`.
  - Connect with bad token ? receive `error` or connection closed.
- Open & read:
  - `open` returns `file` with expected content & hash.
- Save:
  - Send `save` ? server writes file, returns `save_ok`, other clients receive `fs_event`.
- Concurrency:
  - Simulate two clients editing same file: verify `lock_denied`/`REMOTE LOCK` behavior; verify conflict UX preserves both versions.
- FS events:
  - Edit file externally (from host/CLI), confirm client receives `fs_event` and updates appropriately.

**Implementation notes for Android engineers**
- Use OkHttp WebSocket (com.squareup.okhttp3:okhttp). Keep a `SyncConnection` wrapper that:
  - Queues outgoing messages while reconnecting.
  - Parses incoming JSON to model classes.
  - Exposes LiveData/Flow/Callback for `file`, `fs_event`, `lock_*`, `save_ok`, `error`.
- Provide a `SyncManager` that:
  - Manages current open file state: `path`, `serverHash`, `localDirty`, `lockedBy`.
  - Handles autosave debounce and manual save.
  - Integrates with `EditorBuffer` to apply remote updates only when safe.
- Compute SHA256 with standard Android crypto for `hash` fields.

