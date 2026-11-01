package com.example.doswriter.logic

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class WorkingFolderManager(private val context: Context) {
    
    data class WorkingFolder(
        val name: String,
        val path: String
    )

    private val folders = mutableListOf<WorkingFolder>()
    var currentFolder: WorkingFolder? = null
    var lastUsedFolderUri: String? = null
    private val gson = Gson()

    init {
        loadFolders()
    }

    fun getFolders(): List<WorkingFolder> = folders

    fun addFolder(name: String, path: String) {
        if (folders.none { it.path == path }) {
            folders.add(WorkingFolder(name, path))
            if (currentFolder == null) currentFolder = folders.last()
            saveFolders()
        }
    }

    fun removeFolder(index: Int) {
        if (index in folders.indices) {
            val removed = folders.removeAt(index)
            if (currentFolder?.path == removed.path) {
                currentFolder = if (folders.isNotEmpty()) folders[0] else null
            }
            saveFolders()
        }
    }

    fun saveFolders() {
        val prefs = context.getSharedPreferences("working_folders", Context.MODE_PRIVATE)
        val data = gson.toJson(folders)
        prefs.edit().putString("folders", data)
            .putString("current_folder_path", currentFolder?.path)
            .putString("last_used_folder_uri", lastUsedFolderUri)
            .apply()
    }

    private fun loadFolders() {
        val prefs = context.getSharedPreferences("working_folders", Context.MODE_PRIVATE)
        val data = prefs.getString("folders", "") ?: ""
        if (data.isNotEmpty()) {
            val type = object : TypeToken<List<WorkingFolder>>() {}.type
            val loaded: List<WorkingFolder> = gson.fromJson(data, type)
            folders.clear()
            folders.addAll(loaded)
        }
        
        val currentPath = prefs.getString("current_folder_path", null)
        if (currentPath != null) {
            currentFolder = folders.find { it.path == currentPath }
        }
        if (currentFolder == null && folders.isNotEmpty()) {
            currentFolder = folders[0]
        }
        lastUsedFolderUri = prefs.getString("last_used_folder_uri", null)
    }
}
