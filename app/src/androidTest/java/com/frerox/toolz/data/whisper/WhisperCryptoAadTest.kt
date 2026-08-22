/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.whisper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AAD direction-binding regression tests.
 *
 * These exist because of review finding P0-1: attachment decryption tried the wrong
 * (senderId, receiverId) pair, so GCM authentication failed for every newly sent
 * encrypted image while no test noticed. These tests pin the contract:
 *   - ciphertext decrypts ONLY under its original (sender, receiver) direction,
 *   - the swapped direction must fail (replay across direction is impossible),
 *   - attachment round-trips work end-to-end through the bound API used by send/download,
 *   - staged rotation commits/aborts without ever losing the active key.
 */
@RunWith(AndroidJUnit4::class)
class WhisperCryptoAadTest {

    private val crypto = WhisperCrypto(InstrumentationRegistry.getInstrumentation().targetContext)

    private fun myKey(): String = crypto.getPublicKeyBase64()
        ?: throw IllegalStateException("Keystore key missing — ensureKeyPairExists failed")

    @Test
    fun messageRoundTrip_sameDirectionSucceeds() {
        val key = myKey()
        val (cipher, iv) = crypto.encryptMessage("hello aad", key, "senderA", "receiverB")!!
        assertEquals("hello aad", crypto.decryptMessage(cipher, iv, key, "senderA", "receiverB"))
    }

    @Test
    fun messageRoundTrip_swappedDirectionMustFail() {
        val key = myKey()
        val (cipher, iv) = crypto.encryptMessage("hello aad", key, "senderA", "receiverB")!!
        // The exact bug class behind P0-1: wrong direction must never authenticate.
        assertTrue(crypto.decryptMessage(cipher, iv, key, "receiverB", "senderA") == null)
        assertTrue(crypto.decryptMessage(cipher, iv, key, "senderA", "senderA") == null)
    }

    @Test
    fun attachmentRoundTrip_boundDirectionSucceeds() {
        val key = myKey()
        val plain = ByteArray(4096) { (it % 251).toByte() }
        val (cipherBytes, iv) = crypto.encryptAttachment(plain, key, "senderA", "receiverB")!!
        assertEquals(
            plain.toList(),
            crypto.decryptAttachment(cipherBytes, iv, key, "senderA", "receiverB")!!.toList()
        )
    }

    @Test
    fun attachmentRoundTrip_swappedDirectionMustFail() {
        val key = myKey()
        val plain = "direction-bound payload".toByteArray(Charsets.UTF_8)
        val (cipherBytes, iv) = crypto.encryptAttachment(plain, key, "senderA", "receiverB")!!
        assertTrue(crypto.decryptAttachment(cipherBytes, iv, key, "receiverB", "senderA") == null)
        assertTrue(crypto.decryptAttachment(cipherBytes, iv, key, "senderA", "senderA") == null)
    }

    @Test
    fun stagedRotation_commitSwitchesAndAbortKeepsOld() {
        val before = myKey()
        val staged = crypto.stageNewKeyPair() ?: throw AssertionError("staging failed")
        assertNotNull(staged.publicKeyBase64)

        // Abort: active key unchanged, staged alias gone.
        crypto.abortStagedKeyPair(staged)
        assertEquals(before, myKey())

        // Commit: active pointer moves to the staged key.
        val staged2 = crypto.stageNewKeyPair() ?: throw AssertionError("staging failed")
        assertTrue(crypto.commitStagedKeyPair(staged2))
        assertEquals(staged2.publicKeyBase64, myKey())
        // Restore a clean state for other tests: stage+commit once more is unnecessary —
        // leaving the committed key active is fine; it behaves like any rotation.
    }

    @Test
    fun fingerprint_isStableAndFormatted() {
        val fp1 = WhisperCrypto.computeFingerprint(myKey())
        val fp2 = WhisperCrypto.computeFingerprint(myKey())
        assertEquals(fp1, fp2)
        assertTrue(fp1?.split("-")?.size == 8)
        assertTrue(WhisperCrypto.computeFingerprint(null) == null)
        assertTrue(WhisperCrypto.computeFingerprint("!!!not-base64!!!") == null)
    }
}
