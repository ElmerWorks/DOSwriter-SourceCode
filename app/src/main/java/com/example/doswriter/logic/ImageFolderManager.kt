package com.example.doswriter.logic

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ImageFolderManager(private val context: Context) {
    
    data class LinkedFolder(
        val name: String,
        val path: String,
        val isRemote: Boolean = false
    )

    enum class SortMode { NAME, DATE }

    var sortMode: SortMode = SortMode.NAME
    var slideshowIntervalSec: Int = 5
    private val folders = mutableListOf<LinkedFolder>()
    var currentFolder: LinkedFolder? = null
    private val gson = Gson()

    init {
        loadFolders()
    }

    fun getFolders(): List<LinkedFolder> = folders

    fun addFolder(name: String, path: String, isRemote: Boolean) {
        folders.add(LinkedFolder(name, path, isRemote))
        saveFolders()
    }

    fun removeFolder(index: Int) {
        if (index in folders.indices) {
            folders.removeAt(index)
            saveFolders()
        }
    }

    fun saveFolders() {
        val prefs = context.getSharedPreferences("image_folders", Context.MODE_PRIVATE)
        val data = gson.toJson(folders)
        prefs.edit().putString("folders", data)
            .putString("sort_mode", sortMode.name)
            .putInt("slideshow_interval", slideshowIntervalSec)
            .putString("current_folder_path", currentFolder?.path)
            .apply()
    }

    private fun loadFolders() {
        val prefs = context.getSharedPreferences("image_folders", Context.MODE_PRIVATE)
        val data = prefs.getString("folders", "") ?: ""
        if (data.isNotEmpty()) {
            val type = object : TypeToken<List<LinkedFolder>>() {}.type
            val loaded: List<LinkedFolder> = gson.fromJson(data, type)
            folders.clear()
            folders.addAll(loaded)
        }
        sortMode = SortMode.valueOf(prefs.getString("sort_mode", SortMode.NAME.name) ?: SortMode.NAME.name)
        slideshowIntervalSec = prefs.getInt("slideshow_interval", 5)
        
        val currentPath = prefs.getString("current_folder_path", null)
        if (currentPath != null) {
            currentFolder = folders.find { it.path == currentPath }
        }
        if (currentFolder == null && folders.isNotEmpty()) {
            currentFolder = folders[0]
        }
    }
}
