package com.frerox.toolz.data.whisper

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H-11 FIX (reviewwhisper.md): pure-JVM invariant tests for the model layer.
 * These pin behaviors that previously had zero coverage:
 *  - username normalization (anon_ masking of raw-hash handles),
 *  - message status derivation,
 *  - the encrypted-image envelope round-trip (the ONLY sanctioned plaintext-in-ciphertext
 *    container) including prefix rejection,
 *  - tombstone integration on [WhisperMessage].
 */
class WhisperModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── effectiveUsername / effectiveName ──

    @Test
    fun effectiveUsername_masksLongHexHandles() {
        val profile = WhisperProfile(username = "a1b2c3d4e5f60718293a4b5c6d7e8f90") // 32 hex chars
        assertEquals("anon_a1b2c3", profile.effectiveUsername)
    }

    @Test
    fun effectiveUsername_keepsNormalUsernames() {
        assertEquals("alice", WhisperProfile(username = "alice").effectiveUsername)
        assertEquals("short", WhisperProfile(username = "short").effectiveUsername)
        // Hex-looking but too short → untouched.
        assertEquals("abc123", WhisperProfile(username = "abc123").effectiveUsername)
        // Long but not hex (contains 'z') → untouched.
        assertEquals("longusername_with_z", WhisperProfile(username = "longusername_with_z").effectiveUsername)
    }

    @Test
    fun effectiveName_prefersDisplayNameThenFallsBack() {
        val p1 = WhisperProfile(username = "a1b2c3d4e5f60718293a4b5c6d7e8f90", displayName = "Alice")
        assertEquals("Alice", p1.effectiveName)
        val p2 = WhisperProfile(username = "a1b2c3d4e5f60718293a4b5c6d7e8f90")
        assertEquals("anon_a1b2c3", p2.effectiveName)
        // Blank display name falls through to username.
        val p3 = WhisperProfile(username = "bob", displayName = "   ")
        assertEquals("bob", p3.effectiveName)
    }

    @Test
    fun avatarInitial_isFirstLetterUppercaseOrPlaceholder() {
        assertEquals("A", WhisperProfile(username = "alice").avatarInitial)
        assertEquals("?", WhisperProfile(username = "").avatarInitial)
    }

    // ── status derivation ──

    @Test
    fun messageStatus_mapsPendingReadSentCorrectly() {
        val base = WhisperMessage(id = "1", senderId = "a", receiverId = "b")
        assertEquals(WhisperMessageStatus.SENT, base.status("a"))
        assertTrue(base.copy(isPending = true).status("a") == WhisperMessageStatus.PENDING)
        assertTrue(base.copy(isRead = true).status("a") == WhisperMessageStatus.READ)
        // Pending wins over read (optimistic bubble).
        assertEquals(WhisperMessageStatus.PENDING, base.copy(isPending = true, isRead = true).status("a"))
    }

    // ── image attachment envelope ──

    @Test
    fun imageEnvelope_roundTripsThroughMessageContent() {
        val attachment = WhisperImageAttachment(
            url = "https://i.ibb.co/abc/whisper.png",
            iv = "AAECAw==",
            mimeType = "image/jpeg",
            attachmentId = "https://ibb.co/delete",
            expiresAtEpochSeconds = 1_800_000_000L,
            sizeBytes = 12_345,
        )
        val content = attachment.toMessageContent()
        assertTrue(content.startsWith(WhisperImageAttachment.MESSAGE_PREFIX))
        val parsed = WhisperImageAttachment.fromMessageContent(content)
        assertNotNull(parsed)
        assertEquals(attachment.url, parsed!!.url)
        assertEquals(attachment.iv, parsed.iv)
        assertEquals(attachment.mimeType, parsed.mimeType)
        assertEquals(attachment.attachmentId, parsed.attachmentId)
        assertEquals(attachment.expiresAtEpochSeconds, parsed.expiresAtEpochSeconds)
        assertEquals(attachment.sizeBytes, parsed.sizeBytes)
    }

    @Test
    fun imageEnvelope_rejectsNonPrefixedAndGarbageContent() {
        assertNull(WhisperImageAttachment.fromMessageContent("hello world"))
        assertNull(WhisperImageAttachment.fromMessageContent(""))
        assertNull(WhisperImageAttachment.fromMessageContent(WhisperImageAttachment.MESSAGE_PREFIX + "not-json"))
    }

    @Test
    fun imageEnvelope_jsonIgnoresUnknownFields() {
        val content = WhisperImageAttachment.MESSAGE_PREFIX +
            """{"version":99,"url":"https://i.ibb.co/x/y.png","iv":"iv","mimeType":"image/png","futureField":true}"""
        val parsed = WhisperImageAttachment.fromMessageContent(content)
        assertNotNull(parsed)
        assertEquals("image/png", parsed!!.mimeType)
    }

    // ── tombstone integration on the message model ──

    @Test
    fun deletedForEveryone_andSenderName_deriveFromTombstoneContent() {
        val legacy = WhisperMessage(id = "1", content = WhisperTombstone.CONTENT_LEGACY)
        assertTrue(legacy.isDeletedForEveryone)
        assertNull(legacy.deletedSenderName)

        val named = WhisperMessage(id = "2", content = "[deleted_by_sender:Alice]")
        assertTrue(named.isDeletedForEveryone)
        assertEquals("Alice", named.deletedSenderName)

        val normal = WhisperMessage(id = "3", content = "hi")
        assertFalse(normal.isDeletedForEveryone)
    }

    // ── serialization sanity for the wire format ──

    @Test
    fun whisperMessage_serializesSnakeCaseAndSkipsTransient() {
        val msg = WhisperMessage(
            id = "x", senderId = "s", receiverId = "r", content = "c",
            replyToId = null, isRead = true, createdAt = "2026-01-01T00:00:00Z",
            replyToContent = "TRANSIENT", reactions = listOf(WhisperReactionSummary(emoji = "❤️", count = 1)),
        )
        val encoded = json.encodeToString(WhisperMessage.serializer(), msg)
        assertTrue(encoded.contains("\"sender_id\""))
        assertTrue(encoded.contains("\"is_read\""))
        assertFalse(encoded.contains("reply_to_content")) // @Transient must never hit the wire
        val decoded = json.decodeFromString(WhisperMessage.serializer(), encoded)
        assertEquals(msg.id, decoded.id)
        assertEquals(msg.isRead, decoded.isRead)
    }
}
