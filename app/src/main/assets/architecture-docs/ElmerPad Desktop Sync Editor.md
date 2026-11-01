Android/Desktop Sync Editor Architecture Specification
Go Host + HTML Front End + Android Native Client
This specification describes a minimalist cross-platform text editor ecosystem consisting of:

* Native Android editor app
* Go-based desktop host service
* HTML/JavaScript browser editor UI
* Real-time synchronization of a shared text document

The design prioritizes:

* Minimal dependencies
* Cross-platform portability
* Low RAM usage
* Low CPU usage
* Plain-text workflows
* LAN-only operation
* Simple deployment
* Keyboard-centric editing
* E-ink compatibility

1. System Overview
Core Concept
A Go executable runs on Linux or Windows and acts as:

* local HTTP server
* websocket sync server
* file manager
* synchronization coordinator

The Android app acts as:

* native text editor
* websocket sync client
* local cache manager

The desktop browser UI acts as:

* secondary editor
* monitoring/editor interface
* optional desktop editing environment

All editors operate on a single shared plain-text file.

2. Design Goals
Primary Goals
Minimalism
Avoid:

* databases
* cloud infrastructure
* heavy frameworks
* Electron
* collaborative editing engines
* external runtimes

Use:

* plain text
* native browser rendering
* lightweight websocket sync

Cross-Platform
Desktop host must run identically on:

* Linux
* Windows

Android app must:

* communicate identically
* use standard networking APIs

Small Footprint
Target:

* Host executable under 20 MB
* Idle RAM under 50 MB
* Instant startup

LAN-Oriented
Initial versions operate only on:

* localhost
* LAN Wi-Fi
* USB tethering networks

No Internet/cloud required.

3. High-Level Architecture
+--------------------------------------------------+
|                Desktop Host Machine              |
|                                                  |
|  +--------------------------------------------+  |
|  |             Go Host Service                |  |
|  |--------------------------------------------|  |
|  | HTTP Server                                |  |
|  | WebSocket Server                           |  |
|  | File Watcher                               |  |
|  | Sync Coordinator                           |  |
|  | Local File Manager                         |  |
|  +--------------------------------------------+  |
|                   |                              |
|          localhost:8080                         |
|                   |                              |
|  +--------------------------------------------+  |
|  |           Browser Editor UI                |  |
|  |      HTML/CSS/JavaScript Front End         |  |
|  +--------------------------------------------+  |
+--------------------------------------------------+

                LAN / Wi-Fi / USB

+--------------------------------------------------+
|                Android Device                    |
|                                                  |
|  +--------------------------------------------+  |
|  |           Android Native App               |  |
|  |--------------------------------------------|  |
|  | Text Editor                                |  |
|  | WebSocket Client                           |  |
|  | Sync Manager                               |  |
|  | Local Cache                                |  |
|  +--------------------------------------------+  |
+--------------------------------------------------+


4. Desktop Host Specification
Language
Recommended:

* Go

Reasons:

* static binaries
* built-in HTTP support
* excellent networking
* cross-compilation
* low memory usage

5. Desktop Host Responsibilities
HTTP Server
Serve:

* editor.html
* editor.js
* style.css

Example:
http://localhost:8080


WebSocket Sync Server
Maintain persistent connections with:

* browser editor
* Android app

Broadcast:

* text changes
* save events
* reload notifications
* file status

File Watcher
Monitor:

* current document

Detect:

* external changes
* save events
* timestamp changes

Recommended Go package:

* fsnotify


Synchronization Coordinator
Track:

* current document state
* revision number
* active editor
* lock ownership

Local File Manager
Responsibilities:

* open/save file
* autosave
* backup versions
* path validation

6. Browser Front-End Specification
Technologies
Use:

* plain HTML
* plain CSS
* plain JavaScript

Avoid:

* React
* Angular
* Electron
* Node dependency chains

7. Browser UI Responsibilities
Text Editing
Provide:

* textarea/editor surface
* keyboard shortcuts
* cursor movement
* themes
* status bar
* optional line numbering

WebSocket Client
Connect to:
ws://localhost:8080/ws

Handle:

* incoming updates
* outgoing edits
* save notifications

UI State
Display:

* connection state
* sync status
* active editor
* read-only status

8. Android App Specification
Platform
Android Studio
Recommended language:

* Kotlin


9. Android App Responsibilities
Native Text Editor
Features:

* plain text editing
* low-latency typing
* keyboard support
* fullscreen mode
* optional markdown support

WebSocket Client
Maintain persistent websocket connection to host.
Recommended library:

* OkHttp WebSocket


Local Cache
Store:

* current text buffer
* last revision number
* temporary offline edits

Sync Manager
Responsibilities:

* send local edits
* receive remote edits
* apply updates
* detect conflicts
* maintain lock state

10. Initial Synchronization Strategy
Phase 1: Full-Document Sync
Simplest implementation.
Each save transmits:

* entire document text
* revision number

Example message:
{
  "type":"save",
  "revision":12,
  "content":"entire file contents"
}

Advantages:

* simple
* reliable
* easy debugging

Disadvantages:

* inefficient for large files

Acceptable for initial versions.

11. Locking Model
Single Active Editor
Only one editor has write permission at a time.
Other editors become:

* read-only

This avoids:

* merge conflicts
* OT algorithms
* CRDT complexity

Lock Ownership
Possible states:

* Android active
* Browser active
* Host idle

12. Future Synchronization Improvements
Phase 2
Incremental edits:
{
  "type":"patch",
  "start":120,
  "end":130,
  "text":"replacement"
}


Phase 3
Possible additions:

* collaborative editing
* operational transforms
* CRDT sync
* multi-document projects

Not required initially.

13. Suggested WebSocket Message Protocol
Connection
{
  "type":"hello",
  "client":"android",
  "version":"1.0"
}


File Update
{
  "type":"update",
  "revision":15,
  "content":"document text"
}


Lock Request
{
  "type":"lock_request",
  "client":"android"
}


Lock Granted
{
  "type":"lock_granted",
  "owner":"android"
}


Read-Only Notification
{
  "type":"readonly",
  "enabled":true
}


14. Networking
Initial Discovery
Manual IP entry initially:
192.168.1.20:8080


Future Enhancements
Possible:

* mDNS discovery
* QR-code pairing
* automatic LAN discovery

15. Security Model
Initial Version
LAN-only trusted environment.
No authentication required initially.

Future Security
Possible additions:

* PIN pairing
* TLS
* local encryption
* access tokens

16. File Format
Primary format:
UTF-8 plain text

Optional:

* Markdown
* Fountain
* code files

No database dependency.

17. Deployment Model
Desktop
Single executable:
editorhost.exe

or:
editorhost

No installer required.

Android
Standard APK install.

18. Development Recommendations
Desktop Development
Use:

* Visual Studio Code
* Go

Android Development
Use:

* Android Studio
* Kotlin
* OkHttp websocket library

19. Recommended Initial Milestones
Milestone 1
Desktop host:

* serve HTML page
* open text file
* basic browser editing

Milestone 2
Add websocket sync.

Milestone 3
Android app connects.

Milestone 4
Implement locking system.

Milestone 5
Implement autosave and reload.

20. Architectural Philosophy
This system intentionally favors:

* simplicity over feature density
* reliability over collaboration complexity
* plain text over databases
* local ownership over cloud dependence
* small binaries over frameworks
* fast startup over abstraction layers

The intended result is a lightweight writing ecosystem optimized for:

* writers
* programmers
* e-ink users
* Linux users
* distraction-free workflows
* privacy-focused users.