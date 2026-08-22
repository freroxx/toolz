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
}
