package com.example.doswriter.logic

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RecentFileManager(private val context: Context) {
    data class RecentFile(
        val path: String,
        val name: String,
        val isRemote: Boolean
    )

    private val recentFiles = mutableListOf<RecentFile>()
    private val gson = Gson()
    private val MAX_RECENT = 8

    init {
        loadRecentFiles()
    }

    fun getRecentFiles(): List<RecentFile> = recentFiles

    fun addFile(path: String, name: String, isRemote: Boolean) {
        // Remove existing entry for the same path
        recentFiles.removeAll { it.path == path && it.isRemote == isRemote }
        
        // Add to the front
        recentFiles.add(0, RecentFile(path, name, isRemote))
        
        // Trim to MAX_RECENT
        if (recentFiles.size > MAX_RECENT) {
            recentFiles.removeAt(recentFiles.size - 1)
        }
        
        saveRecentFiles()
    }

    private fun saveRecentFiles() {
        val prefs = context.getSharedPreferences("recent_files", Context.MODE_PRIVATE)
        val data = gson.toJson(recentFiles)
        prefs.edit().putString("files", data).apply()
    }

    private fun loadRecentFiles() {
        val prefs = context.getSharedPreferences("recent_files", Context.MODE_PRIVATE)
        val data = prefs.getString("files", "") ?: ""
        if (data.isNotEmpty()) {
            val type = object : TypeToken<List<RecentFile>>() {}.type
            val loaded: List<RecentFile> = gson.fromJson(data, type)
            recentFiles.clear()
            recentFiles.addAll(loaded)
        }
    }
}
