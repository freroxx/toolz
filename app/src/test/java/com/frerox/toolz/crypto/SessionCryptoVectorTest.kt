/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PHASE 1 acceptance (roadmap §6.2): primitives are pinned to official
 * known-answer vectors. Any change to [SessionCrypto] must keep this suite green.
 */
class SessionCryptoVectorTest {

    private fun hex(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    // ---------------------------------------------------------- X25519 (RFC 7748)

    /** RFC 7748 §5.2 — Test Vector 1 */
    @Test
    fun x25519_rfc7748_vector1() {
        val k = hex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4")
        val u = hex("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c")
        val expected = "c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552"
        assertEquals(expected, SessionCrypto.sharedSecret(k, u)!!.toHex())
    }

    /** RFC 7748 §5.2 — Test Vector 2 */
    @Test
    fun x25519_rfc7748_vector2() {
        val k = hex("4b66e9d4d1b4673c5ad22691957d6af5c11b6421e0ea01d42ca4169e7918ba0d")
        val u = hex("e5210f12786811d3f4b7959d0538ae2c31dbe7106fc03c3efc4cd549c715a493")
        val expected = "95cbde9476e8907d7aade45cb4b873f88b595a68799fa152e6f8f7647aac7957"
        assertEquals(expected, SessionCrypto.sharedSecret(k, u)!!.toHex())
    }

    /** RFC 7748 §6.1 — full key exchange between Alice and Bob */
    @Test
    fun x25519_rfc7748_keyExchange() {
        val alicePriv = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val bobPriv = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
        val alicePubExpected = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"
        val bobPubExpected = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f"

        val alicePub = SessionCrypto.publicFromPrivate(alicePriv)
        val bobPub = SessionCrypto.publicFromPrivate(bobPriv)
        assertEquals(alicePubExpected, alicePub.toHex())
        assertEquals(bobPubExpected, bobPub.toHex())

        val kA = SessionCrypto.sharedSecret(alicePriv, bobPub)
        val kB = SessionCrypto.sharedSecret(bobPriv, alicePub)
        assertNotNull(kA)
        assertNotNull(kB)
        assertArrayEquals(kA, kB)
        assertEquals("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742", kA!!.toHex())
    }

    /** RFC 7748 §5.2 — Iterated 1,000 times (intermediate result after 1k rounds) */
    @Test
    fun x25519_rfc7748_iterated1000() {
        var k = hex("0900000000000000000000000000000000000000000000000000000000000000")
        var u = k.clone()
        repeat(1000) {
            val newU = SessionCrypto.sharedSecret(k, u)!!
            u = k.clone()
            k = newU
        }
        // After 1 iteration pair the spec gives 422c8e7a…; after 1000 pairs of ops:
        assertEquals(
            "684cf59ba83309552800ef566f2f4d3c1c3887c49360e3875f2eb94d99532c51",
            k.toHex(),
        )
    }

    /** Low-order peer points MUST yield null (RFC 7748 mandated all-zero check). */
    @Test
    fun x25519_lowOrderPointRejected() {
        val priv = SessionCrypto.generatePrivateKey()
        val zeroPoint = ByteArray(32)
        assertNull(SessionCrypto.sharedSecret(priv, zeroPoint))
    }

    /** Property: fresh keys produce valid publics and a symmetric shared secret. */
    @Test
    fun x25519_randomPairAgrees() {
        repeat(8) {
            val a = SessionCrypto.generatePrivateKey()
            val b = SessionCrypto.generatePrivateKey()
            val pubA = SessionCrypto.publicFromPrivate(a)
            val pubB = SessionCrypto.publicFromPrivate(b)
            assertTrue(pubA.any { it != 0.toByte() })
            assertArrayEquals(SessionCrypto.sharedSecret(a, pubB), SessionCrypto.sharedSecret(b, pubA))
        }
    }

    // ------------------------------------------------------------ HKDF (RFC 5869)

    /** RFC 5869 Appendix A — Test Case 1 */
    @Test
    fun hkdf_rfc5869_case1() {
        val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val okm = SessionCrypto.hkdfSha256(ikm, salt, info, 42)
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.toHex(),
        )
    }

    /** RFC 5869 Appendix A — Test Case 3 (empty salt → zero-filled per RFC) */
    @Test
    fun hkdf_rfc5869_case3_emptySalt() {
        val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val okm = SessionCrypto.hkdfSha256(ikm, ByteArray(0), ByteArray(0), 42)
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            okm.toHex(),
        )
    }

    // ------------------------------------------------------------ AES-GCM round trip

    @Test
    fun aead_roundTrip_and_tamperReject() {
        val key = SessionCrypto.hkdfSha256("seed".toByteArray(), ByteArray(32), "aead".toByteArray(), 32)
        val aad = "direction-binding".toByteArray()
        val sealed = SessionCrypto.aesGcmSeal(key, "hello whisper".toByteArray(), aad)
        assertEquals("hello whisper", String(SessionCrypto.aesGcmOpen(key, sealed, aad)!!))

        // Tampered ciphertext must fail closed.
        val tampered = sealed.clone().also { it[it.size - 1] = ((it[it.size - 1].toInt() xor 1).toByte()) }
        assertNull(SessionCrypto.aesGcmOpen(key, tampered, aad))
        // Wrong AAD must fail closed.
        assertNull(SessionCrypto.aesGcmOpen(key, sealed, "other".toByteArray()))
    }
}
