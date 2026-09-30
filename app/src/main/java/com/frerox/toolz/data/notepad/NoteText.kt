package com.frerox.toolz.data.notepad

import java.util.Locale

/** Shared text helpers — one Regex instance, locale-correct casing. */
object NoteText {
    val WORD_SPLIT = Regex("\\s+")
    const val WORDS_PER_MINUTE = 200

    fun wordCount(content: String): Int {
        val t = content.trim()
        if (t.isEmpty()) return 0
        return t.split(WORD_SPLIT).size
    }

    fun readingMinutes(content: String): Int {
        val w = wordCount(content)
        if (w == 0) return 0
        return (w / WORDS_PER_MINUTE).coerceAtLeast(1)
    }

    fun titleUpper(title: String, locale: Locale = Locale.ROOT): String =
        title.uppercase(locale)

    fun displayFileName(displayName: String?, uri: String, maxLen: Int = 48): String {
        val raw = displayName?.takeIf { it.isNotBlank() } ?: uri.substringAfterLast('/')
        val decoded = try {
            java.net.URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }
        if (decoded.length <= maxLen) return decoded
        // Keep extension visible.
        val ext = decoded.substringAfterLast('.', "")
        val stem = decoded.substringBeforeLast('.')
        if (ext.isEmpty() || ext.length > 6) return decoded.take(maxLen - 1) + "…"
        return stem.take(maxLen - ext.length - 2) + "…." + ext
    }
}
