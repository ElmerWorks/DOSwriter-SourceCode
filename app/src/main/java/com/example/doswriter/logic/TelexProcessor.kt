package com.example.doswriter.logic

class TelexProcessor {
    enum class KeyboardMode {
        STANDARD, VIETNAMESE_TELEX
    }

    data class ProcessResult(
        val handled: Boolean,
        val output: String = "",
        val backspaceCount: Int = 0
    )

    private val baseVowels = "aeiouy"
    private val toneKeys = "sfrxj"
    private val modifiedVowels = "âêôơưăđÂÊÔƠƯĂĐ"
    
    // Mappings for Stage 1 (Base character conversion)
    private val baseMap = mapOf(
        "aa" to "â", "aw" to "ă", "ee" to "ê", "oo" to "ô", "ow" to "ơ", "uw" to "ư", "dd" to "đ",
        "AA" to "Â", "AW" to "Ă", "EE" to "Ê", "OO" to "Ô", "OW" to "Ơ", "UW" to "Ư", "DD" to "Đ",
        "Aw" to "Ă", "Ow" to "Ơ", "Uw" to "Ư"
    )

    private val toneMarks = mapOf(
        's' to '\u0301', // Acute
        'f' to '\u0300', // Grave
        'r' to '\u0309', // Hook Above
        'x' to '\u0303', // Tilde
        'j' to '\u0323'  // Dot Below
    )

    fun process(char: Char, currentSyllable: String): ProcessResult {
        // Stage 1: Base Character Conversion
        if (currentSyllable.isNotEmpty()) {
            val lastChar = currentSyllable.last()
            val combo = lastChar.toString() + char
            baseMap[combo]?.let {
                return ProcessResult(true, it, 1)
            }
        }

        // Stage 2 & 3: Intelligent Tone Marks
        if (toneKeys.contains(char.lowercaseChar()) && currentSyllable.isNotEmpty()) {
            val toneChar = char.lowercaseChar()
            val primaryVowelIdx = findPrimaryVowelIndex(currentSyllable)
            
            if (primaryVowelIdx != -1) {
                val vowel = currentSyllable[primaryVowelIdx]
                // Check if this tone is already applied (for Stage 4 cancellation later)
                val currentSyllableWithoutTones = removeTonesFromSyllable(currentSyllable)
                val combined = applyTone(removeExistingTone(vowel), toneChar)
                
                if (combined != null) {
                    val result = currentSyllableWithoutTones.substring(0, primaryVowelIdx) + 
                                 combined + 
                                 currentSyllableWithoutTones.substring(primaryVowelIdx + 1)
                    return ProcessResult(true, result, currentSyllable.length)
                }
            }
        }

        return ProcessResult(false)
    }

    private fun findPrimaryVowelIndex(syllable: String): Int {
        val vowelIndices = mutableListOf<Int>()
        for (i in syllable.indices) {
            if (isVowel(syllable[i])) vowelIndices.add(i)
        }

        if (vowelIndices.isEmpty()) return -1
        if (vowelIndices.size == 1) return vowelIndices[0]

        val cleanSyllable = removeTonesFromSyllable(syllable).lowercase()
        val firstVowelIdx = vowelIndices[0]
        val lastVowelIdx = vowelIndices.last()
        val hasFinalConsonant = lastVowelIdx < syllable.length - 1

        // Triphthongs: always the middle one (uôi, iêu, ươi...)
        if (vowelIndices.size == 3) return vowelIndices[1]

        // Diphthongs
        if (vowelIndices.size == 2) {
            val v1 = cleanSyllable[vowelIndices[0]]
            val v2 = cleanSyllable[vowelIndices[1]]
            
            // Special cases for oa, oe, uy (Standard/New style)
            if (v1 == 'o' && (v2 == 'a' || v2 == 'e')) return vowelIndices[1]
            if (v1 == 'u' && v2 == 'y') return vowelIndices[1]
            
            // If has final consonant, tone usually goes to the second vowel
            if (hasFinalConsonant) return vowelIndices[1]
            
            // Otherwise, place on the first (ia, ua, uơ)
            return vowelIndices[0]
        }

        // Fallback: use the last vowel
        return lastVowelIdx
    }

    private fun isVowel(c: Char): Boolean {
        val norm = java.text.Normalizer.normalize(c.toString(), java.text.Normalizer.Form.NFD)
        val base = norm[0].lowercaseChar()
        return baseVowels.contains(base) || modifiedVowels.lowercase().contains(base)
    }

    private fun removeTonesFromSyllable(syllable: String): String {
        val sb = StringBuilder()
        for (c in syllable) {
            sb.append(removeExistingTone(c))
        }
        return sb.toString()
    }

    private fun removeExistingTone(c: Char): Char {
        val normalized = java.text.Normalizer.normalize(c.toString(), java.text.Normalizer.Form.NFD)
        val filtered = normalized.filter { it.toInt() !in 0x0300..0x0309 && it.toInt() != 0x0323 }
        val result = java.text.Normalizer.normalize(filtered, java.text.Normalizer.Form.NFC)
        return if (result.isNotEmpty()) result[0] else c
    }

    private fun applyTone(vowel: Char, toneKey: Char): String? {
        val toneMark = toneMarks[toneKey] ?: return null
        val normalized = java.text.Normalizer.normalize(vowel.toString(), java.text.Normalizer.Form.NFD)
        val result = normalized + toneMark
        return java.text.Normalizer.normalize(result, java.text.Normalizer.Form.NFC)
    }
}
