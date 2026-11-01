package com.example.doswriter.logic

import android.content.Context
import java.util.UUID

class OutlineManager(private val context: Context) {
    
    sealed class OutlineItem {
        abstract val id: String
        var name: String = ""

        data class Folder(
            override val id: String = UUID.randomUUID().toString(),
            val containers: MutableList<FileContainer> = mutableListOf()
        ) : OutlineItem()

        data class FileContainer(
            override val id: String = UUID.randomUUID().toString(),
            var linkedUri: String? = null,
            var fileName: String? = null,
            var remotePath: String? = null
        ) : OutlineItem()
    }

    private val folders = mutableListOf<OutlineItem.Folder>()
    var isDirty = false
        private set
    var currentFileUri: String? = null

    init {
        loadOutlineFromPrefs()
    }

    fun getFolders(): List<OutlineItem.Folder> = folders

    fun addFolder(name: String) {
        val folder = OutlineItem.Folder()
        folder.name = name
        folders.add(folder)
        isDirty = true
        saveOutlineToPrefs()
    }

    fun addContainer(folder: OutlineItem.Folder, name: String): OutlineItem.FileContainer {
        val container = OutlineItem.FileContainer()
        // We now leave the name empty as requested. 
        // The overlay will handle showing "(empty)".
        container.name = name
        folder.containers.add(container)
        isDirty = true
        saveOutlineToPrefs()
        return container
    }

    fun removeItem(item: OutlineItem) {
        if (item is OutlineItem.Folder) {
            folders.remove(item)
        } else if (item is OutlineItem.FileContainer) {
            for (f in folders) {
                if (f.containers.remove(item)) break
            }
        }
        isDirty = true
        saveOutlineToPrefs()
    }

    fun linkLocalFile(containerId: String, uri: String, fileName: String) {
        val container = findContainer(containerId)
        if (container != null) {
            container.linkedUri = uri
            container.fileName = fileName
            container.name = fileName
            container.remotePath = null
            isDirty = true
            saveOutlineToPrefs()
        }
    }

    fun linkRemoteFile(containerId: String, path: String) {
        val container = findContainer(containerId)
        if (container != null) {
            container.remotePath = path
            val name = path.substringAfterLast("/")
            container.fileName = "[REMOTE] $name"
            container.name = name
            container.linkedUri = null
            isDirty = true
            saveOutlineToPrefs()
        }
    }

    fun unlinkFile(containerId: String) {
        val container = findContainer(containerId)
        if (container != null) {
            container.linkedUri = null
            container.remotePath = null
            container.fileName = null
            isDirty = true
            saveOutlineToPrefs()
        }
    }

    private fun findContainer(id: String): OutlineItem.FileContainer? {
        for (f in folders) {
            val c = f.containers.find { it.id == id }
            if (c != null) return c
        }
        return null
    }

    fun markDirty() {
        isDirty = true
    }

    fun clear() {
        folders.clear()
        currentFileUri = null
        isDirty = false
        saveOutlineToPrefs()
    }

    fun markClean() {
        isDirty = false
    }

    private fun saveOutlineToPrefs() {
        val prefs = context.getSharedPreferences("outline", Context.MODE_PRIVATE)
        prefs.edit().putString("structure", serialize()).apply()
    }

    private fun loadOutlineFromPrefs() {
        val prefs = context.getSharedPreferences("outline", Context.MODE_PRIVATE)
        val data = prefs.getString("structure", "") ?: ""
        if (data.isNotEmpty()) {
            try {
                deserialize(data)
                isDirty = false
            } catch (e: Exception) {
                folders.clear()
            }
        }
    }

    fun exportToJson(): String = serialize()
    fun importFromJson(json: String) = deserialize(json)

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("MONKWRITE_OUTLINE_V2").append(";")
        sb.append(folders.size).append(";")
        for (f in folders) {
            sb.append(f.id).append("|").append(f.name).append("|").append(f.containers.size).append(";")
            for (c in f.containers) {
                sb.append(c.id).append("|")
                    .append(c.name).append("|")
                    .append(c.linkedUri ?: "null").append("|")
                    .append(c.fileName ?: "null").append("|")
                    .append(c.remotePath ?: "null").append(";")
            }
        }
        return sb.toString()
    }

    fun deserialize(data: String) {
        val parts = data.split(";")
        if (parts.isEmpty()) throw IllegalArgumentException("Invalid file")
        
        val version = parts[0]
        if (version != "MONKWRITE_OUTLINE_V1" && version != "MONKWRITE_OUTLINE_V2") {
            throw IllegalArgumentException("Invalid file version")
        }

        var idx = 1
        val folderCount = parts[idx++].toInt()
        val newFolders = mutableListOf<OutlineItem.Folder>()
        
        repeat(folderCount) {
            val fData = parts[idx++].split("|")
            val folder = OutlineItem.Folder(fData[0])
            folder.name = fData[1]
            val contCount = fData[2].toInt()
            repeat(contCount) {
                val cData = parts[idx++].split("|")
                val container = OutlineItem.FileContainer(cData[0])
                container.name = cData[1]
                container.linkedUri = if (cData[2] == "null") null else cData[2]
                container.fileName = if (cData[3] == "null") null else cData[3]
                if (version == "MONKWRITE_OUTLINE_V2") {
                    container.remotePath = if (cData[4] == "null") null else cData[4]
                }
                folder.containers.add(container)
            }
            newFolders.add(folder)
        }
        
        folders.clear()
        folders.addAll(newFolders)
        isDirty = true
        saveOutlineToPrefs()
    }
}
