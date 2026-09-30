package com.frerox.toolz.data.notepad

import org.junit.Assert.*
import org.junit.Test

class NotepadHelpersTest {

    @Test
    fun parseHex_acceptsValid_rejectsBad() {
        assertNotNull(NoteColors.parseHexOrNull("#FFCCBC"))
        assertNotNull(NoteColors.parseHexOrNull("#263238"))
        assertNull(NoteColors.parseHexOrNull(null))
        assertNull(NoteColors.parseHexOrNull(""))
        assertNull(NoteColors.parseHexOrNull("red"))
        assertNull(NoteColors.parseHexOrNull("#ZZZZZZ"))
        assertNull(NoteColors.parseHexOrNull("#FFF"))
        // Never throws on hostile AI output.
        assertNull(NoteColors.parseHexOrNull("{colorHex: red}"))
    }

    @Test
    fun parseHex_fallback() {
        val fallback = 0xFF123456.toInt()
        assertEquals(fallback, NoteColors.parseHex("nope", fallback))
    }

    @Test
    fun toHex_roundTrip() {
        assertEquals("#FFCCBC", NoteColors.toHex(0xFFFFCCBC.toInt()))
    }

    @Test
    fun containerColor_zeroFallsBack() {
        val fb = androidx.compose.ui.graphics.Color.Red
        assertEquals(fb, NoteColors.containerColor(0, fb))
    }

    @Test
    fun wordCount_honestZero() {
        assertEquals(0, NoteText.wordCount(""))
        assertEquals(0, NoteText.wordCount("   "))
        assertEquals(2, NoteText.wordCount("hello  world"))
        assertEquals(0, NoteText.readingMinutes(""))
        assertEquals(1, NoteText.readingMinutes("hello world"))
    }

    @Test
    fun limits_clamp() {
        assertEquals(12f, NoteLimits.clampFontSize(2f))
        assertEquals(28f, NoteLimits.clampFontSize(99f))
        assertEquals(17f, NoteLimits.clampFontSize(17f))
    }

    @Test
    fun blankNote_guard() {
        assertTrue(NoteLimits.isBlankNote("  ", "  ", 0))
        assertFalse(NoteLimits.isBlankNote("t", "", 0))
        assertFalse(NoteLimits.isBlankNote("", "", 1))
    }

    @Test
    fun kind_validation() {
        assertTrue(NoteAttachmentKind.isValid(NoteAttachment.KIND_PDF))
        assertFalse(NoteAttachmentKind.isValid("pdf"))
        assertFalse(NoteAttachmentKind.isValid("DOCUMENT"))
        try {
            NoteAttachmentKind.requireValid("nope")
            fail("should throw")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun displayFileName_decodesAndKeepsExt() {
        assertEquals("my doc.pdf", NoteText.displayFileName(null, "content://x/my%20doc.pdf"))
        val long = "a".repeat(60) + ".pdf"
        val short = NoteText.displayFileName(long, "content://x/y")
        assertTrue(short.length <= 48)
        assertTrue(short.endsWith(".pdf"))
    }
}
