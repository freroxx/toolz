package com.frerox.toolz.data.notepad

/**
 * Single place for every notepad limit so UI copy, ViewModel caps and
 * worker logic never drift (previously "10 PDFs" was hardcoded in 3 spots).
 */
object NoteLimits {
    const val MAX_PDF_PER_NOTE = 10
    const val MAX_IMAGE_PER_NOTE = 5
    const val MAX_AUDIO_PER_NOTE = 5
    const val MAX_ATTACHMENTS_PER_NOTE = 15
    const val MAX_TITLE_CHARS = 200
    const val MAX_CONTENT_CHARS = 50_000
    const val MAX_PROMPT_CHARS = 2_000
    const val MIN_FONT_SIZE = 12f
    const val MAX_FONT_SIZE = 28f
    const val SEARCH_LIMIT = 200
    const val TRASH_RETENTION_DAYS = 30L

    fun clampFontSize(v: Float): Float = v.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
    fun isBlankNote(title: String, content: String, attachmentCount: Int): Boolean =
        title.isBlank() && content.isBlank() && attachmentCount == 0
}
