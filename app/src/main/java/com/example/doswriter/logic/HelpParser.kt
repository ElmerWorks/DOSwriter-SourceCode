package com.example.doswriter.logic

import java.util.*

class HelpParser(
    private val deviceType: Int, // 0: Backlit, 1: Color E-ink, 2: Mono E-ink
    private val deviceInfo: DeviceProfiler.DeviceInfo,
    private val isTablet: Boolean
) {

    fun parse(rawText: String): String {
        return processConditions(rawText)
    }

    /**
     * Extracts pages from [OVERVIEW]...[/OVERVIEW] block, split by lines starting with "**".
     */
    fun getOverviewScreens(rawText: String): List<String> {
        val startTag = "[OVERVIEW]"
        val endTag = "[/OVERVIEW]"
        val startIndex = rawText.indexOf(startTag)
        val endIndex = rawText.indexOf(endTag)
        
        if (startIndex == -1 || endIndex <= startIndex) return emptyList()
        
        val content = rawText.substring(startIndex + startTag.length, endIndex)
        val filtered = processConditions(content).trim()
        
        val screens = mutableListOf<String>()
        val currentScreen = StringBuilder()
        
        filtered.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("**")) {
                if (currentScreen.toString().trim().isNotEmpty()) {
                    screens.add(currentScreen.toString().trim())
                }
                currentScreen.setLength(0)
                // Keep the header line but remove the stars for rendering
                currentScreen.append("# ").append(trimmedLine.removePrefix("**").trim()).append("\n\n")
            } else {
                currentScreen.append(line).append("\n")
            }
        }
        if (currentScreen.toString().trim().isNotEmpty()) {
            screens.add(currentScreen.toString().trim())
        }
        
        return screens
    }

    /**
     * Finds all lines starting with "## " to build a topic menu.
     */
    fun getTopicList(rawText: String): List<String> {
        val filtered = processConditions(rawText)
        return filtered.lines()
            .filter { it.startsWith("## ") }
            .map { it.removePrefix("## ").trim() }
    }

    /**
     * Extracts text under a specific "## " header until the next "## " or "# " header.
     */
    fun getSection(rawText: String, topic: String): String {
        val filtered = processConditions(rawText)
        val lines = filtered.lines()
        val result = StringBuilder()
        var found = false

        for (line in lines) {
            if (line.startsWith("## ") && line.removePrefix("## ").trim() == topic) {
                found = true
                continue
            }
            if (found && (line.startsWith("## ") || line.startsWith("# "))) {
                break
            }
            if (found) {
                result.append(line).append("\n")
            }
        }
        return result.toString().trim()
    }

    /**
     * Extracts pages from [HELP-POPUP]...[/HELP-POPUP] block, split by lines starting with "**".
     */
    fun getHelpPopupScreens(rawText: String): List<String> {
        val startTag = "[HELP-POPUP]"
        val endTag = "[/HELP-POPUP]"
        val startIndex = rawText.indexOf(startTag)
        val endIndex = rawText.indexOf(endTag)
        
        if (startIndex == -1 || endIndex <= startIndex) return emptyList()
        
        val content = rawText.substring(startIndex + startTag.length, endIndex)
        val filtered = processConditions(content).trim()
        
        val screens = mutableListOf<String>()
        val currentScreen = StringBuilder()
        
        filtered.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("**")) {
                if (currentScreen.toString().trim().isNotEmpty()) {
                    screens.add(currentScreen.toString().trim())
                }
                currentScreen.setLength(0)
                currentScreen.append("# ").append(trimmedLine.removePrefix("**").trim()).append("\n\n")
            } else {
                currentScreen.append(line).append("\n")
            }
        }
        if (currentScreen.toString().trim().isNotEmpty()) {
            screens.add(currentScreen.toString().trim())
        }
        
        return screens
    }

    /**
     * Extracts lines from [EXAMPLES]...[/EXAMPLES] block.
     */
    fun getExampleList(rawText: String): List<String> {
        val startTag = "[EXAMPLES]"
        val endTag = "[/EXAMPLES]"
        return extractList(rawText, startTag, endTag)
    }

    /**
     * Extracts lines from [TODO-EXAMPLES]...[/TODO-EXAMPLES] block.
     */
    fun getTodoExampleList(rawText: String): List<String> {
        val startTag = "[TODO-EXAMPLES]"
        val endTag = "[/TODO-EXAMPLES]"
        return extractList(rawText, startTag, endTag)
    }

    /**
     * Extracts categorized lists from examples-structure.md or INSTRUCTIONS.md
     */
    fun getCategorizedExamples(rawText: String): Map<String, List<String>> {
        val categories = listOf("TEXT", "MARKDOWN", "IMAGE", "THUMBNAIL", "TODO")
        val result = mutableMapOf<String, List<String>>()
        
        categories.forEach { cat ->
            val list = extractList(rawText, "[$cat-EXAMPLES]", "[/$cat-EXAMPLES]")
            if (list.isNotEmpty()) {
                result[cat] = list
            }
        }
        return result
    }

    private fun extractList(rawText: String, startTag: String, endTag: String): List<String> {
        val startIndex = rawText.indexOf(startTag)
        val endIndex = rawText.indexOf(endTag)
        
        if (startIndex == -1 || endIndex <= startIndex) return emptyList()
        
        val content = rawText.substring(startIndex + startTag.length, endIndex)
        return content.lines()
            .map { it.trim() }
            .filter { it.startsWith("*") }
            .map { it.removePrefix("*").trim() }
    }

    private fun processConditions(text: String): String {
        val lines = text.lines()
        val result = StringBuilder()
        val stack = Stack<Boolean>()
        stack.push(true)

        var insideExclusionBlock = false
        val blockTags = listOf("[OVERVIEW]", "[/OVERVIEW]", "[HELP-POPUP]", "[/HELP-POPUP]", "[EXAMPLES]", "[/EXAMPLES]", "[TODO-EXAMPLES]", "[/TODO-EXAMPLES]")

        for (line in lines) {
            val trimmedLine = line.trim()
            
            if (trimmedLine in blockTags) {
                insideExclusionBlock = trimmedLine.startsWith("[") && !trimmedLine.startsWith("[/")
                continue
            }

            if (insideExclusionBlock) continue

            when {
                trimmedLine.startsWith("[IF_") -> {
                    val condition = trimmedLine.removePrefix("[").removeSuffix("]")
                    val isTrue = evaluateCondition(condition)
                    stack.push(stack.peek() && isTrue)
                    continue
                }
                trimmedLine == "[ELSE]" -> {
                    val last = stack.pop()
                    val parent = stack.peek()
                    stack.push(parent && !last)
                    continue
                }
                trimmedLine == "[/IF]" -> {
                    if (stack.size > 1) stack.pop()
                    continue
                }
            }

            if (stack.peek()) {
                result.append(line).append("\n")
            }
        }

        return result.toString().trimEnd()
    }

    private fun evaluateCondition(condition: String): Boolean {
        return when {
            condition == "IF_EINK" -> deviceType == 1 || deviceType == 2
            condition == "IF_MONO" -> deviceType == 2
            condition == "IF_COLOR" -> deviceType == 0 || deviceType == 1
            condition == "IF_BACKLIT" -> deviceType == 0
            condition == "IF_TABLET" -> isTablet
            condition == "IF_PHONE" -> !isTablet
            condition.startsWith("IF_MODEL:") -> {
                val model = condition.removePrefix("IF_MODEL:").trim()
                deviceInfo.model.contains(model, ignoreCase = true)
            }
            condition.startsWith("IF_BRAND:") -> {
                val brand = condition.removePrefix("IF_BRAND:").trim()
                deviceInfo.brand.contains(brand, ignoreCase = true)
            }
            condition.startsWith("IF_MANUFACTURER:") -> {
                val manufacturer = condition.removePrefix("IF_MANUFACTURER:").trim()
                deviceInfo.manufacturer.contains(manufacturer, ignoreCase = true)
            }
            else -> false
        }
    }
}
