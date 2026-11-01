package com.example.doswriter.logic

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.Keep
import com.example.doswriter.model.EditorBuffer
import com.google.gson.Gson
import okhttp3.*
import java.util.concurrent.TimeUnit

class SyncManager(
    private val bufferManager: BufferManager,
    private val onSyncEvent: (Event) -> Unit
) {
    sealed class Event {
        object Connected : Event()
        object Disconnected : Event()
        data class Error(val message: String) : Event()
        data class FileReceived(val path: String, val content: String) : Event()
        data class SaveOk(val path: String) : Event()
        data class RemoteFileModified(val path: String) : Event()
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isConnected(): Boolean = webSocket != null

    fun connect() {
        if (webSocket != null) return
        
        val host = bufferManager.syncHost
        val port = bufferManager.syncPort
        val token = bufferManager.syncToken
        
        if (host.isEmpty()) {
            onSyncEvent(Event.Error("Host IP is empty"))
            return
        }

        val request = Request.Builder()
            .url("ws://$host:$port/ws")
            .addHeader("X-API-Token", token)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendHandshake()
                mainHandler.post { onSyncEvent(Event.Connected) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@SyncManager.webSocket = null
                mainHandler.post { onSyncEvent(Event.Disconnected) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                this@SyncManager.webSocket = null
                mainHandler.post { 
                    onSyncEvent(Event.Error(t.message ?: "Connection failed"))
                    onSyncEvent(Event.Disconnected)
                }
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnect")
        webSocket = null
    }

    fun openRemoteFile(path: String) {
        val msg = mapOf("type" to "open", "path" to path)
        webSocket?.send(gson.toJson(msg))
    }

    fun saveRemoteFile(path: String, content: String) {
        val msg = mapOf("type" to "save", "path" to path, "content" to content)
        webSocket?.send(gson.toJson(msg))
    }

    fun fetchRemoteImage(path: String, onResult: (android.graphics.Bitmap?) -> Unit) {
        val host = bufferManager.syncHost
        val port = bufferManager.syncPort
        val token = bufferManager.syncToken
        
        val request = Request.Builder()
            .url("http://$host:$port/api/file?path=$path")
            .addHeader("X-API-Token", token)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                mainHandler.post { onResult(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val bytes = response.body?.bytes()
                if (bytes != null) {
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    mainHandler.post { onResult(bmp) }
                } else {
                    mainHandler.post { onResult(null) }
                }
            }
        })
    }

    @Keep
    data class RemoteFile(val name: String, val isDir: Boolean, val path: String)

    fun fetchRemoteFileList(path: String = ".", onResult: (List<RemoteFile>) -> Unit) {
        val host = bufferManager.syncHost
        val port = bufferManager.syncPort
        val token = bufferManager.syncToken
        
        val request = Request.Builder()
            .url("http://$host:$port/api/list?path=$path")
            .addHeader("X-API-Token", token)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                mainHandler.post { onSyncEvent(Event.Error("List fetch failed: ${e.message}")) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                try {
                    val listType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                    val list: List<Map<String, Any>> = gson.fromJson(body, listType)
                    
                    val result = list.map {
                        val name = it["name"] as String
                        val isDir = it["isDir"] as Boolean
                        // Construct the path relative to sync root
                        val remotePath = if (path == ".") name else "$path/$name"
                        RemoteFile(name, isDir, remotePath)
                    }
                    mainHandler.post { onResult(result) }
                } catch (e: Exception) {
                    mainHandler.post { onSyncEvent(Event.Error("Failed to parse file list")) }
                }
            }
        })
    }

    private fun sendHandshake() {
        val msg = mapOf("type" to "hello", "client" to "DOSwriter", "version" to "1.0")
        webSocket?.send(gson.toJson(msg))
    }

    private fun handleIncomingMessage(json: String) {
        try {
            val m = gson.fromJson(json, Map::class.java)
            val type = m["type"] as? String ?: return
            
            mainHandler.post {
                when (type) {
                    "hello_ok" -> { /* Handshake success */ }
                    "file" -> {
                        val path = m["path"] as? String ?: ""
                        val content = m["content"] as? String ?: ""
                        onSyncEvent(Event.FileReceived(path, content))
                    }
                    "save_ok" -> {
                        val path = m["path"] as? String ?: ""
                        onSyncEvent(Event.SaveOk(path))
                    }
                    "fs_event" -> {
                        val path = m["path"] as? String ?: ""
                        val event = m["event"] as? String ?: ""
                        if (event == "modified") {
                            onSyncEvent(Event.RemoteFileModified(path))
                        }
                    }
                    "error" -> {
                        val err = m["error"] as? String ?: "unknown"
                        onSyncEvent(Event.Error("Server error: $err"))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
