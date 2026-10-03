/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper.session

import com.frerox.toolz.crypto.SessionCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * EXTERNAL reference vectors for the Whisper v3 transport.
 *
 * Scope note — read before extending: `WhisperV3InteropTest` is SELF-interop
 * (both ends of every exchange are this codebase, so a systematic framing bug
 * would pass there unnoticed). THIS file pins EXTERNAL known answers instead:
 * every expected value below comes from an RFC (7748 / 5869) or from an
 * independent Python (stdlib hmac/hashlib) cross-computation, never from this
 * repo's own output. If any test here fails, the wire framing drifted from the
 * outside world — do not "fix" the expected values without an external source.
 *
 * External review of the full Signal-compat claim is still needed (see
 * WHISPER.md §10): the X3DH test below reuses RFC 7748 §6.1 key material in the
 * Signal DH arrangement rather than the official Signal X3DH spec vectors, and
 * the ratchet KATs pin our HKDF/HMAC framing rather than a third-party
 * Double-Ratchet transcript.
 */
class WhisperRefVectorTest {

    private fun hex(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)

    // ------------------------------------------------- X25519 (RFC 7748, external)

    /**
     * RFC 7748 §5.2 test vector 1 — the raw DH our X3DH initiator/responder and
     * every ratchet step build on. If this fails, the X25519 backend (Tink) or
     * its wiring changed, and nothing above it can be trusted.
     */
    @Test
    fun `ref - rfc7748 section5_2 vector1 DH pair`() {
        val k = hex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4")
        val u = hex("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c")
        assertEquals(
            "c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552",
            SessionCrypto.sharedSecret(k, u)!!.toHex(),
        )
    }

    /**
     * Signal-style X3DH agreement computed through [SessionCrypto] with RFC 7748
     * §6.1 Alice/Bob key material: DH1 = X25519(IK_A, SPK_B) MUST equal the
     * §6.1 shared secret (external pin), and initiator/responder KDF outputs
     * MUST agree bit-for-bit under the exact `WhisperX3DH-v1` framing the
     * factory uses (0xFF pad ‖ DH concat → HKDF-SHA256, 32 zero salt).
     */
    @Test
    fun `ref - signal-style X3DH agreement matches both sides`() {
        val ikAPriv = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val ikBPriv = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
        val ikAPub = SessionCrypto.publicFromPrivate(ikAPriv)
        val ikBPub = SessionCrypto.publicFromPrivate(ikBPriv)
        // Responder's signed prekey plays the SPK_B role; any fixed scalar works
        // as the initiator ephemeral (vector-2 k is known-good key material).
        val spkBPriv = ikBPriv
        val spkBPub = ikBPub
        val ekPriv = hex("4b66e9d4d1b4673c5ad22691957d6af5c11b6421e0ea01d42ca4169e7918ba0d")
        val ekPub = SessionCrypto.publicFromPrivate(ekPriv)

        // DH1 pins to the EXTERNAL §6.1 shared secret — not to our own output.
        val dh1 = SessionCrypto.sharedSecret(ikAPriv, spkBPub)
        assertEquals(
            "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742",
            dh1!!.toHex(),
        )

        fun kdf(vararg dh: ByteArray?): ByteArray {
            val parts = dh.filterNotNull()
            val ikm = ByteArray(32) { 0xFF.toByte() } + parts.reduce { acc, b -> acc + b }
            return SessionCrypto.hkdfSha256(ikm, ByteArray(32), "WhisperX3DH-v1".toByteArray(), 32)
        }

        val skInitiator = kdf(
            dh1,
            SessionCrypto.sharedSecret(ekPriv, ikBPub),
            SessionCrypto.sharedSecret(ekPriv, spkBPub),
        )
        val skResponder = kdf(
            SessionCrypto.sharedSecret(spkBPriv, ikAPub),
            SessionCrypto.sharedSecret(ikBPriv, ekPub),
            SessionCrypto.sharedSecret(spkBPriv, ekPub),
        )
        assertEquals(32, skInitiator.size)
        assertArrayEquals(skInitiator, skResponder)

        // Session-ID framing both sides derive identically.
        val sid = "s" + SessionCrypto.sha256(skInitiator).toHex().take(12)
        assertEquals(13, sid.length)
        assertTrue(sid.startsWith("s"))
    }

    // ---------------------------------------------------- HKDF (RFC 5869, external)

    /**
     * RFC 5869 Appendix A test case 2 (80-octet IKM/salt/info, 82-octet OKM).
     * Cross-checked against Python stdlib hmac/hashlib before hardcoding —
     * `SessionCryptoVectorTest` covers cases 1 and 3; this file independently
     * pins case 2 so multi-block HKDF expansion is externally anchored too.
     */
    @Test
    fun `ref - hkdf rfc5869 appendix A case 2`() {
        val ikm = ByteArray(80) { (it and 0xFF).toByte() }
        val salt = ByteArray(80) { ((0x60 + it) and 0xFF).toByte() }
        val info = ByteArray(80) { ((0xB0 + it) and 0xFF).toByte() }
        val okm = SessionCrypto.hkdfSha256(ikm, salt, info, 82)
        assertEquals(
            "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac7827271cb41c65e590e09da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87",
            okm.toHex(),
        )
    }

    // --------------------------------- Double-Ratchet KDF known answers (external)

    /**
     * Double-Ratchet step known answers with the EXACT framing
     * `WhisperRatchet` uses (root: HKDF-SHA256 info `WhisperRatchetRoot`, 64
     * bytes split root‖chain; chain: HMAC 0x01 message key / 0x02 next chain).
     * Expected values were computed with an independent Python (stdlib
     * hmac/hashlib) implementation of the same framing — never with this
     * repo's output — so a drift in info strings, split order, or domain
     * separation bytes fails here even though self-interop would still pass.
     */
    @Test
    fun `ref - double-ratchet root and chain KDF known answers`() {
        val dhOut = hex("11".repeat(32))
        val rk = hex("22".repeat(32))
        val okm = SessionCrypto.hkdfSha256(dhOut, rk, "WhisperRatchetRoot".toByteArray(), 64)
        val newRoot = okm.copyOfRange(0, 32)
        val chainKey = okm.copyOfRange(32, 64)
        assertEquals("0363d2809d58db9752300996bfbfe493f6313456bf9409e4ac35e2619e9e24e6", newRoot.toHex())
        assertEquals("e40c7010bf5e79498a99f17adaeebd82701ebe49696ffa7dfe6e19da74b33dde", chainKey.toHex())

        val messageKey = SessionCrypto.hmacSha256(chainKey, byteArrayOf(0x01))
        val nextChain = SessionCrypto.hmacSha256(chainKey, byteArrayOf(0x02))
        assertEquals("fe64dd84a9fc34fd681ccef378a4e26544684110691d21c77569cee8a99535c8", messageKey.toHex())
        assertEquals("03f092e84f91a4d359207f7a57f2395b863be913b94bebc431b11aabeffed74f", nextChain.toHex())

        // Domain separation is load-bearing: message/next keys must differ.
        assertFalse(messageKey.contentEquals(nextChain))
    }

    // ------------------------------------------- env cutoff (retirement policy)

    /**
     * env-after-cutoff-rejected: rows created after 2026-12-01T00:00:00Z must
     * never open via the legacy `env` insurance copy (fail-closed); anything at
     * or before the cutoff stays decodable for backward compat. Undated rows
     * map to 0L upstream (treated as legacy-eligible).
     */
    @Test
    fun `ref - env after cutoff rejected`() {
        val cutoff = WhisperV3Codec.LEGACY_ENV_CUTOFF_MS
        assertEquals(1_796_083_200_000L, cutoff) // 2026-12-01T00:00:00Z
        assertTrue(WhisperV3Codec.insuranceAllowed(0L))
        assertTrue(WhisperV3Codec.insuranceAllowed(cutoff - 1))
        assertTrue(WhisperV3Codec.insuranceAllowed(cutoff))
        assertFalse(WhisperV3Codec.insuranceAllowed(cutoff + 1))
        assertFalse(WhisperV3Codec.insuranceAllowed(Long.MAX_VALUE))
    }

    /**
     * New sends never populate `env` (Phase-1A): the compat param is accepted
     * but ignored, so freshly encoded frames carry no insurance copy for a
     * post-cutoff peer to trip over.
     */
    @Test
    fun `ref - new sends never carry insurance env`() {
        val alicePriv = SessionCrypto.generatePrivateKey()
        val alicePubB64 = b64(SessionCrypto.publicFromPrivate(alicePriv))
        val sk = ByteArray(32) { 0x07 }
        val alice = WhisperRatchet.initiator(sk, alicePubB64)
        val sealed = alice.encrypt("hello".toByteArray())
        val json = WhisperV3Codec.encode("s0123456789ab", sealed.header, sealed.ciphertextPacked, null, "should-be-ignored")
        val parsed = WhisperV3Codec.parse(json)
        assertNotNull(parsed)
        @Suppress("DEPRECATION")
        assertNull(parsed!!.insuranceEnvelope)
        assertNull(parsed.x3dh)
        assertTrue(WhisperV3Codec.isV3(json))
    }
}
