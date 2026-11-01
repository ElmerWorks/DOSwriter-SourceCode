package com.example.doswriter.ui

import android.widget.Toast
import com.example.doswriter.MainActivity
import com.example.doswriter.logic.Command

internal fun MainActivity.showRemoteFilePicker(currentPath: String, onCancel: () -> Unit, onFileSelected: (String) -> Unit) {
    syncManager.fetchRemoteFileList(currentPath) { items ->
        if (items.isEmpty() && currentPath == ".") {
            Toast.makeText(this, "No files found on server", Toast.LENGTH_SHORT).show()
            onCancel()
            return@fetchRemoteFileList
        }

        val displayList = mutableListOf<String>()
        if (currentPath != ".") displayList.add(".. [BACK]")
        
        items.forEach { 
            val prefix = if (it.isDir) "[DIR] " else ""
            displayList.add("$prefix${it.name}")
        }

        selectionOverlay.show("Remote File Picker", displayList, 0, { index ->
            val adjustedIndex = if (currentPath != ".") index - 1 else index
            if (currentPath != "." && index == 0) {
                val parent = currentPath.substringBeforeLast("/", ".")
                showRemoteFilePicker(parent, onCancel, onFileSelected)
            } else {
                val item = items[adjustedIndex]
                if (item.isDir) {
                    showRemoteFilePicker(item.path, onCancel, onFileSelected)
                } else {
                    onFileSelected(item.path)
                }
            }
        }, onCancel)
    }
}

internal fun MainActivity.showPdfFavoriteAddInput() {
    val current = editorView.getCurrentPdfPage() + 1
    inputOverlay.show("Add Favorite for Page $current:", "", { input ->
        if (input.isNotEmpty()) {
            commandDispatcher.dispatch(Command.AddPdfFavoriteNamed(input))
        }
    }, {})
}

internal fun MainActivity.showPatternCollapseInput() {
    inputOverlay.show("Enter Collapse Pattern (up to 80 chars):", "", { input ->
        if (input.isNotEmpty()) {
            commandDispatcher.dispatch(Command.AddCollapsePattern(input))
        }
    }, {})
}
