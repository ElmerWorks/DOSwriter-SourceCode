package com.example.doswriter.logic

import android.content.Context

class MacroManager(private val context: Context) {
    private val contexts = mutableMapOf<String, MutableList<String>>()
    var activeContext = "TEXT"
    var lastMacroUsed: String? = null
    private val MAX_MACROS = 8

    init {
        // Initialize default contexts
        contexts["TEXT"] = mutableListOf()
        contexts["CODE"] = mutableListOf("{ }", "( )", "[ ]", "if () { }", "for () { }")
        contexts["MARKDOWN"] = mutableListOf("# ", "## ", "### ", "**BOLD**", "*ITALIC*", "> ", "---", "![Alt](path)")
        
        loadAll()
    }

    fun getContextNames(): List<String> = contexts.keys.toList().sortedBy { 
        // Keep standard ones at top
        when(it.uppercase()) {
            "TEXT" -> 0
            "MARKDOWN" -> 1
            "CODE" -> 2
            else -> 3
        }
    }

    fun getMacros(contextName: String = activeContext): List<String> = contexts[contextName] ?: emptyList()

    fun addContext(name: String) {
        if (name.isNotEmpty() && !contexts.containsKey(name)) {
            contexts[name] = mutableListOf()
            saveAll()
        }
    }

    fun addMacro(text: String, contextName: String = activeContext) {
        if (text.isEmpty()) return
        lastMacroUsed = text
        val list = contexts.getOrPut(contextName) { mutableListOf() }
        list.remove(text)
        list.add(0, text)
        if (list.size > MAX_MACROS) {
            list.removeAt(list.size - 1)
        }
        saveAll()
    }

    fun updateMacro(oldText: String, newText: String, contextName: String = activeContext) {
        val list = contexts[contextName] ?: return
        if (newText.isEmpty()) {
            list.remove(oldText)
        } else {
            val idx = list.indexOf(oldText)
            if (idx != -1) {
                list[idx] = newText
                list.removeAt(idx)
                list.add(0, newText)
            } else {
                addMacro(newText, contextName)
            }
        }
        saveAll()
    }

    fun moveToTop(text: String, contextName: String = activeContext) {
        if (text.isEmpty()) return
        lastMacroUsed = text
        val list = contexts[contextName] ?: return
        list.remove(text)
        list.add(0, text)
        saveAll()
    }

    private fun saveAll() {
        val prefs = context.getSharedPreferences("macros_v2", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Save list of contexts
        editor.putString("context_list", contexts.keys.joinToString("|||"))
        
        // Save each context's macros
        contexts.forEach { (name, list) ->
            editor.putString("macros_$name", list.joinToString("|||"))
        }
        editor.putString("last_macro_used", lastMacroUsed)
        editor.apply()
    }

    private fun loadAll() {
        val prefs = context.getSharedPreferences("macros_v2", Context.MODE_PRIVATE)
        val contextData = prefs.getString("context_list", "") ?: ""
        
        if (contextData.isNotEmpty()) {
            val names = contextData.split("|||")
            names.forEach { name ->
                val upperName = name.uppercase()
                val listData = prefs.getString("macros_$name", "") ?: ""
                val list = mutableListOf<String>()
                if (listData.isNotEmpty()) {
                    list.addAll(listData.split("|||"))
                }
                
                // Deduplicate lowercase vs uppercase contexts
                if (contexts.containsKey(upperName)) {
                    val existing = contexts[upperName]!!
                    list.forEach { if (!existing.contains(it)) existing.add(it) }
                } else {
                    contexts[upperName] = list
                }
            }
        }
        
        // Remove lowercase redundant standard contexts if they were loaded/created
        listOf("Text", "Code", "Markdown").forEach { contexts.remove(it) }

        lastMacroUsed = prefs.getString("last_macro_used", null)
        
        // Legacy migration if needed
        val oldPrefs = context.getSharedPreferences("macros", Context.MODE_PRIVATE)
        val oldData = oldPrefs.getString("macro_list", "") ?: ""
        if (oldData.isNotEmpty() && (contexts["TEXT"]?.isEmpty() == true)) {
            contexts["TEXT"]?.addAll(oldData.split("|||"))
            oldPrefs.edit().clear().apply() // Clean up legacy
            saveAll()
        }
    }
}
