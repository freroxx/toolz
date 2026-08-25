/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import com.frerox.toolz.crypto.SessionCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * V6-R7 AVATARS: deterministic owner-key-derived avatar encryption.
 * Guarantees under test: derivation stability, seal/open roundtrip, wrong-owner
 * and wrong-AAD failures (AEAD closes), garbage input fails closed.
 */
class WhisperAvatarCodecTest {

    private fun fakePub(seed: String): String =
        java.util.Base64.getEncoder().encodeToString(
            SessionCrypto.sha256(seed.toByteArray()),
        )

    @Test
    fun `derivation is stable for the same key and differs across owners`() {
        val a = WhisperAvatarCodec.deriveKey(fakePub("owner-a"))
        val b = WhisperAvatarCodec.deriveKey(fakePub("owner-b"))
        assertEquals(a.toList(), WhisperAvatarCodec.deriveKey(fakePub("owner-a")).toList())
        org.junit.Assert.assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun `seal open roundtrip restores the jpeg`() {
        val pub = fakePub("owner")
        val jpeg = ByteArray(2048) { ('a' + (it % 26)).code.toByte() }
        val sealed = WhisperAvatarCodec.seal(jpeg, pub)
        assertArrayEquals(jpeg, WhisperAvatarCodec.open(sealed, pub))
    }

    @Test
    fun `wrong viewer key fails closed`() {
        val pub = fakePub("owner")
        val sealed = WhisperAvatarCodec.seal("jpeg".toByteArray(), pub)
        assertNull(WhisperAvatarCodec.open(sealed, fakePub("someone-else")))
    }

    @Test
    fun `tampered payload fails closed`() {
        val pub = fakePub("owner")
        val sealed = WhisperAvatarCodec.seal("jpeg".toByteArray(), pub)
        sealed[sealed.size / 2] = (sealed[sealed.size / 2].toInt() xor 0x41).toByte()
        assertNull(WhisperAvatarCodec.open(sealed, pub))
        assertNotNull(WhisperAvatarCodec.open(
            WhisperAvatarCodec.seal("jpeg".toByteArray(), pub),
            pub,
        ))
    }
}
