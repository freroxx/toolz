package com.frerox.toolz.data.whisper

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperAubupManagerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testIsValidWhisperCode_validatesSixDigitsOnly() {
        assertTrue(WhisperAubupManager.isValidWhisperCode("000000"))
        assertTrue(WhisperAubupManager.isValidWhisperCode("123456"))
        assertTrue(WhisperAubupManager.isValidWhisperCode("999999"))

        assertFalse(WhisperAubupManager.isValidWhisperCode(""))
        assertFalse(WhisperAubupManager.isValidWhisperCode("1234"))
        assertFalse(WhisperAubupManager.isValidWhisperCode("12345"))
        assertFalse(WhisperAubupManager.isValidWhisperCode("1234567"))
        assertFalse(WhisperAubupManager.isValidWhisperCode("12a456"))
        assertFalse(WhisperAubupManager.isValidWhisperCode("abcdef"))
        assertFalse(WhisperAubupManager.isValidWhisperCode(" 123456 "))
    }

    @Test
    fun testWhisperAccessPayload_serializationRoundtrip() {
        val payload = WhisperAccessPayload(
            version = 1,
            app = "toolz_whisper",
            username = "alice_test",
            authType = "TOKEN",
            credential = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
            displayName = "Alice",
            createdAt = 1755878400000L
        )

        val encoded = json.encodeToString(payload)
        val decoded = json.decodeFromString<WhisperAccessPayload>(encoded)

        assertEquals(payload.version, decoded.version)
        assertEquals(payload.app, decoded.app)
        assertEquals(payload.username, decoded.username)
        assertEquals(payload.authType, decoded.authType)
        assertEquals(payload.credential, decoded.credential)
        assertEquals(payload.displayName, decoded.displayName)
        assertEquals(payload.createdAt, decoded.createdAt)
    }

    @Test
    fun testWhisperAccessPayload_requiresCorrectAppTag() {
        val jsonString = """{"version":1,"app":"another_app","username":"bob","auth_type":"PASSWORD","credential":"secret_password"}"""
        val decoded = json.decodeFromString<WhisperAccessPayload>(jsonString)
        assertEquals("another_app", decoded.app)
        // Ensure callers can detect mismatched app signatures
        assertFalse(decoded.app == "toolz_whisper")
    }

    @Test
    fun testCanonicalAccessFileStem_stripsMediaStoreDuplicateSuffix() {
        assertEquals(
            "whisper_access_Alice",
            WhisperAubupManager.canonicalAccessFileStem("whisper_access_Alice.enc"),
        )
        assertEquals(
            "whisper_access_Alice",
            WhisperAubupManager.canonicalAccessFileStem("whisper_access_Alice (1).enc"),
        )
        assertEquals(
            "whisper_access_Alice",
            WhisperAubupManager.canonicalAccessFileStem("whisper_access_Alice (12).ENC"),
        )
        // Case is preserved: a case-variant name is a different account/file.
        assertEquals(
            "whisper_access_alice",
            WhisperAubupManager.canonicalAccessFileStem("whisper_access_alice.enc"),
        )
        // Anything outside the access-file namespace is never sweep-eligible.
        assertEquals(null, WhisperAubupManager.canonicalAccessFileStem("whisper_notes.enc"))
        assertEquals(null, WhisperAubupManager.canonicalAccessFileStem("notes.enc"))
        assertEquals(null, WhisperAubupManager.canonicalAccessFileStem("whisper_access_Alice.txt"))
    }

    @Test
    fun testPlanSupersededAccessFileDeletions_keepsNewestNonPending() {
        val rows = listOf(
            MediaStoreEncRow(1, "whisper_access_Alice.enc", "Download/Toolz/", isPending = false),
            MediaStoreEncRow(2, "whisper_access_Alice (1).enc", "Download/Toolz/", isPending = false),
            MediaStoreEncRow(3, "whisper_access_Alice (2).enc", "Download/Toolz/", isPending = false),
        )
        assertEquals(
            setOf(1L, 2L),
            WhisperAubupManager.planSupersededAccessFileDeletions(rows).toSet(),
        )
    }

    @Test
    fun testPlanSupersededAccessFileDeletions_pendingRowsNeverDeletedOrChosen() {
        val rows = listOf(
            MediaStoreEncRow(1, "whisper_access_Alice.enc", "Download/Toolz/", isPending = false),
            MediaStoreEncRow(2, "whisper_access_Alice (1).enc", "Download/Toolz/", isPending = false),
            // A newer write is still in flight: never delete it, and keep the newest
            // settled row (id 2) until it completes.
            MediaStoreEncRow(3, "whisper_access_Alice (2).enc", "Download/Toolz/", isPending = true),
        )
        assertEquals(
            listOf(1L),
            WhisperAubupManager.planSupersededAccessFileDeletions(rows),
        )
    }

    @Test
    fun testPlanSupersededAccessFileDeletions_singleRowIsNeverDeleted() {
        val rows = listOf(
            MediaStoreEncRow(1, "whisper_access_Alice.enc", "Download/Toolz/", isPending = false),
        )
        assertEquals(emptyList<Long>(), WhisperAubupManager.planSupersededAccessFileDeletions(rows))
    }

    @Test
    fun testPlanSupersededAccessFileDeletions_separatesStemsAndFolders() {
        val rows = listOf(
            MediaStoreEncRow(1, "whisper_access_Alice.enc", "Download/Toolz/", isPending = false),
            MediaStoreEncRow(2, "whisper_access_Bob (1).enc", "Download/Toolz/", isPending = false),
            MediaStoreEncRow(3, "whisper_access_Alice (1).enc", "Download/Other/", isPending = false),
        )
        assertEquals(emptyList<Long>(), WhisperAubupManager.planSupersededAccessFileDeletions(rows))
    }

    @Test
    fun testPlanSupersededAccessFileDeletions_ignoresUnrelatedAndCaseVariantFiles() {
        val rows = listOf(
            MediaStoreEncRow(1, "whisper_notes.enc", "Download/Toolz/", isPending = false),
            MediaStoreEncRow(2, "whisper_notes (1).enc", "Download/Toolz/", isPending = false),
            MediaStoreEncRow(3, "notes.enc", "Download/Toolz/", isPending = false),
            // Same letters, different case: distinct files, never collapsed.
            MediaStoreEncRow(4, "whisper_access_alice.enc", "Download/Toolz/", isPending = false),
            MediaStoreEncRow(5, "whisper_access_Alice.enc", "Download/Toolz/", isPending = false),
        )
        assertEquals(emptyList<Long>(), WhisperAubupManager.planSupersededAccessFileDeletions(rows))
    }
}
