package com.frerox.toolz.data.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperTombstoneTest {

    @Test
    fun testIsTombstone_matchesExpectedVariants() {
        assertTrue(WhisperTombstone.isTombstone("[deleted_by_sender]"))
        assertTrue(WhisperTombstone.isTombstone("This message has been deleted"))
        assertTrue(WhisperTombstone.isTombstone("[deleted_by_sender:Alice]"))
        assertTrue(WhisperTombstone.isTombstone("[deleted_by_sender: Bob ]"))

        assertFalse(WhisperTombstone.isTombstone("Hello world"))
        assertFalse(WhisperTombstone.isTombstone(""))
        assertFalse(WhisperTombstone.isTombstone("deleted_by_sender"))
    }

    @Test
    fun testExtractSenderName_extractsCorrectly() {
        assertEquals("Alice", WhisperTombstone.extractSenderName("[deleted_by_sender:Alice]"))
        assertEquals("Bob", WhisperTombstone.extractSenderName("[deleted_by_sender: Bob ]"))
        assertNull(WhisperTombstone.extractSenderName("[deleted_by_sender]"))
        assertNull(WhisperTombstone.extractSenderName("This message has been deleted"))
        assertNull(WhisperTombstone.extractSenderName("Normal message"))
    }

    @Test
    fun testLegacyEncryptedConstant() {
        assertEquals("[Legacy encrypted message]", WhisperTombstone.LEGACY_ENCRYPTED)
    }
}
