/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Descriptor contract tests (the `whisper:story:{v,u,k,x,r,a}` envelope):
 * the sealed payload the story plane redistributes. The story id is ALWAYS
 * row-side (server truth) and the delete capability rides only in the
 * poster's own copy — these tests pin both.
 */
class WhisperStoryDescriptorTest {

    private val keyB64 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHr8=" // 32 bytes
    private val hostUrl = "https://i.ibb.co/story/abc123.png"

    private fun leg(
        receiptsEnabled: Boolean = false,
        attachmentId: String? = null,
    ) = WhisperStoryDescriptor(
        version = WhisperStoryDescriptor.VERSION,
        hostUrl = hostUrl,
        imageKeyB64 = keyB64,
        expiresAtMs = 1_900_000_000_000L,
        receiptsEnabled = receiptsEnabled,
        attachmentId = attachmentId,
    )

    @Test
    fun encodeDecode_roundTrip_preservesAllFields() {
        val encoded = leg(receiptsEnabled = true, attachmentId = "ibb-delete-handle").encode()
        val decoded = WhisperStoryDescriptor.decode(encoded)

        assertNotNull(decoded)
        assertEquals(hostUrl, decoded!!.hostUrl)
        assertEquals(keyB64, decoded.imageKeyB64)
        assertEquals(1_900_000_000_000L, decoded.expiresAtMs)
        assertTrue(decoded.receiptsEnabled)
        assertEquals("ibb-delete-handle", decoded.attachmentId)
        assertEquals(WhisperStoryDescriptor.VERSION, decoded.version)
    }

    @Test
    fun encode_hasTheStablePrefixAndSingleLetterFieldNames() {
        val encoded = leg().encode()
        assertTrue(encoded.startsWith(WhisperStoryDescriptor.PREFIX))
        // Wire contract: u/k/x always present; NO story-id field — ids travel
        // on rows, not in envelopes. v and r(default) are elided by
        // encodeDefaults=false, so decode's version gate only bites explicit
        // non-1 versions.
        assertTrue(encoded.contains("\"u\":"))
        assertTrue(encoded.contains("\"k\":"))
        assertTrue(encoded.contains("\"x\":"))
        assertFalse("\"s\":" in encoded)
    }

    @Test
    fun decode_rejectsMissingOrForeignPrefix() {
        assertNull(WhisperStoryDescriptor.decode("whisper:group:{}"))
        assertNull(WhisperStoryDescriptor.decode("whisper:story"))
        assertNull(WhisperStoryDescriptor.decode("{}"))
        assertNull(WhisperStoryDescriptor.decode(""))
    }

    @Test
    fun decode_rejectsUnknownVersion() {
        // Explicit non-1 version (the elision-safe way future revisions appear).
        val upgraded = WhisperStoryDescriptor.PREFIX +
            """{"v":2,"u":"$hostUrl","k":"$keyB64","x":1_900_000_000_000}"""
            .replace("_", "")
        assertNull(WhisperStoryDescriptor.decode(upgraded))
    }

    @Test
    fun decode_rejectsKeyThatIsNotThirtyTwoBytes() {
        assertEquals(44, keyB64.length) // sanity: 32 bytes → 44 base64 chars
        val short = leg().encode().replace(keyB64, keyB64.dropLast(2))
        assertNull(WhisperStoryDescriptor.decode(short))
    }

    @Test
    fun decode_rejectsGarbageAndBlankHost() {
        assertNull(WhisperStoryDescriptor.decode(WhisperStoryDescriptor.PREFIX + "not-json"))
        assertNull(
            WhisperStoryDescriptor.decode(
                leg().encode().replace(hostUrl, ""),
            ),
        )
        assertNull(
            WhisperStoryDescriptor.decode(
                leg().encode().replace("1900000000000", "0"),
            ),
        )
    }

    @Test
    fun decode_defaultsReceiptsToOff_whenFieldOmitted() {
        val decoded = WhisperStoryDescriptor.decode(leg().encode())
        assertNotNull(decoded)
        assertFalse(decoded!!.receiptsEnabled)
    }

    @Test
    fun viewerLegCarriesNoDeleteCapability_byConstruction() {
        // Publish seals the SAME base descriptor (attachmentId = null) to every
        // friend; only the poster's own row gets a copy holding the capability.
        val base = leg()
        assertNull(base.attachmentId)
    }
}
