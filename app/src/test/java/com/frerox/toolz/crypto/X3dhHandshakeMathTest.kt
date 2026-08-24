/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/**
 * PHASE 2 §2.4 acceptance: proves the X3DH-adapted KDF produces IDENTICAL session
 * secrets on initiator and responder sides for both the 3-DH (no OPK) and 4-DH
 * (OPK present) variants. This is the mathematical heart of the prekey protocol;
 * the network glue in WhisperSessionFactory reuses exactly this arrangement.
 */
class X3dhHandshakeMathTest {

    private fun b32(seed: Byte) = ByteArray(32) { seed }

    private fun sessionIdFrom(sk: ByteArray): String =
        "s" + MessageDigest.getInstance("SHA-256").digest(sk)
            .joinToString("") { "%02x".format(it) }.take(12)

    /** Mirrors WhisperSessionFactory.kdf framing. */
    private fun kdf(vararg dh: ByteArray?): ByteArray {
        // Trailing null = absent OPK slot (legitimate); skip it.
        val parts = dh.filterNotNull()
        require(parts.isNotEmpty()) { "no DH inputs" }
        val ikm = ByteArray(32) { 0xFF.toByte() } + parts.reduce { acc, b -> acc + b }
        return SessionCrypto.hkdfSha256(ikm, ByteArray(32), "WhisperX3DH-v1".toByteArray(), 32)
    }

    @Test
    fun threeDh_initiatorAndResponderDeriveSameSecret() {
        val ikA = SessionCrypto.generatePrivateKey()
        val ikB = SessionCrypto.generatePrivateKey()
        val spkB = SessionCrypto.generatePrivateKey()
        val ekA = SessionCrypto.generatePrivateKey()

        val ikAPub = SessionCrypto.publicFromPrivate(ikA)
        val ikBPub = SessionCrypto.publicFromPrivate(ikB)
        val ekAPub = SessionCrypto.publicFromPrivate(ekA)
        val spkPub = SessionCrypto.publicFromPrivate(spkB)

        // Initiator: DH(ikA,spk) ‖ DH(ekA,ikB) ‖ DH(ekA,spk)
        val skInitiator = kdf(
            SessionCrypto.sharedSecret(ikA, spkPub),
            SessionCrypto.sharedSecret(ekA, ikBPub),
            SessionCrypto.sharedSecret(ekA, spkPub),
            null,
        )
        // Responder: mirrored privates.
        val skResponder = kdf(
            SessionCrypto.sharedSecret(spkB, ikAPub),
            SessionCrypto.sharedSecret(ikB, ekAPub),
            SessionCrypto.sharedSecret(spkB, ekAPub),
            null,
        )
        assertArrayEquals(skInitiator, skResponder)
        assertEquals(sessionIdFrom(skInitiator), sessionIdFrom(skResponder))
        assertTrue(skInitiator.any { it != 0.toByte() })
    }

    @Test
    fun fourDh_withOneTimePrekey_alsoMatches() {
        val ikA = SessionCrypto.generatePrivateKey()
        val ikB = SessionCrypto.generatePrivateKey()
        val spkB = SessionCrypto.generatePrivateKey()
        val opkB = SessionCrypto.generatePrivateKey()
        val ekA = SessionCrypto.generatePrivateKey()

        val ikAPub = SessionCrypto.publicFromPrivate(ikA)
        val ikBPub = SessionCrypto.publicFromPrivate(ikB)
        val ekAPub = SessionCrypto.publicFromPrivate(ekA)
        val spkPub = SessionCrypto.publicFromPrivate(spkB)
        val opkPub = SessionCrypto.publicFromPrivate(opkB)

        val skI = kdf(
            SessionCrypto.sharedSecret(ikA, spkPub),
            SessionCrypto.sharedSecret(ekA, ikBPub),
            SessionCrypto.sharedSecret(ekA, spkPub),
            SessionCrypto.sharedSecret(ekA, opkPub),
        )
        val skR = kdf(
            SessionCrypto.sharedSecret(spkB, ikAPub),
            SessionCrypto.sharedSecret(ikB, ekAPub),
            SessionCrypto.sharedSecret(spkB, ekAPub),
            SessionCrypto.sharedSecret(opkB, ekAPub),
        )
        assertArrayEquals(skI, skR)
    }

    /** Different peer pairs must never collide on a session id. */
    @Test
    fun differentInputs_differSessionIds() {
        fun pair(): Pair<ByteArray, ByteArray> {
            val priv = SessionCrypto.generatePrivateKey()
            return priv to SessionCrypto.publicFromPrivate(priv)
        }
        val (ikA, ikAPub) = pair()
        val (spkB, spkPub) = pair()
        val ekPriv = SessionCrypto.generatePrivateKey()

        val s1 = kdf(
            SessionCrypto.sharedSecret(ikA, spkPub),
            SessionCrypto.sharedSecret(ekPriv, ikAPub),
            null,
        )
        val (ikA2, ikA2Pub) = pair()
        val s2 = kdf(
            SessionCrypto.sharedSecret(ikA2, spkPub),
            SessionCrypto.sharedSecret(ekPriv, ikA2Pub),
            null,
        )
        org.junit.Assert.assertNotEquals(sessionIdFrom(s1), sessionIdFrom(s2))
    }
}
