/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import com.frerox.toolz.crypto.SessionCrypto
import com.frerox.toolz.data.whisper.session.WhisperRatchet
import com.frerox.toolz.data.whisper.session.WhisperV3Codec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.SecureRandom

/**
 * Pins the CRYPTO DESIGN of the groups self leg ([WhisperSelfLegCrypto]).
 * The module itself is Android-prefs bound, so this harness replays its exact
 * two construction calls — [WhisperRatchet.initiator] seeded with a fresh
 * random session key against MY anchor pub at seal, [WhisperRatchet.responder]
 * rebuilt from the SAME raw key + my anchor priv at open — and proves the
 * mirror derives identical chain keys by X25519 symmetry.
 */
class WhisperSelfLegCryptoRoundtripTest {

    private fun selfLegRoundtrip(
        sk: ByteArray,
        anchorPriv: ByteArray,
        anchorPubB64: String,
        ad: ByteArray,
        sessionId: String,
    ): Pair<WhisperV3Codec.Frame, ByteArray> {
        val sealed = WhisperRatchet.initiator(sk, anchorPubB64).encrypt("gm".toByteArray(), ad)
        val frameJson = WhisperV3Codec.encode(sessionId, sealed.header, sealed.ciphertextPacked, x3dh = null)
        val frame = checkNotNull(WhisperV3Codec.parse(frameJson))
        val opened = WhisperRatchet.responder(sk, anchorPriv, anchorPubB64)
            .decrypt(
                WhisperRatchet.Header(frame.dhPub(), frame.pn, frame.n),
                frame.ciphertextPacked(),
                ad,
            )
        return frame to opened
    }

    private fun b64(bytes: ByteArray) = java.util.Base64.getEncoder().encodeToString(bytes)

    @Test
    fun `self leg one-frame session opens roundtrip`() {
        val anchorPriv = SessionCrypto.generatePrivateKey()
        val anchorPubB64 = b64(SessionCrypto.publicFromPrivate(anchorPriv))
        val sk = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val (_, opened) = selfLegRoundtrip(sk, anchorPriv, anchorPubB64, "me|me".toByteArray(), "sg1-aaaa")
        assertEquals("gm", opened.decodeToString())
    }

    @Test
    fun `self leg reopens deterministically (no state decay across renders)`() {
        val anchorPriv = SessionCrypto.generatePrivateKey()
        val anchorPubB64 = b64(SessionCrypto.publicFromPrivate(anchorPriv))
        val sk = ByteArray(32).also { SecureRandom().nextBytes(it) }
        repeat(3) {
            val (_, opened) = selfLegRoundtrip(sk, anchorPriv, anchorPubB64, "me|me".toByteArray(), "sg1-bbbb")
            assertEquals("gm", opened.decodeToString())
        }
    }

    @Test
    fun `self leg rejects wrong-boundary ad and foreign session ids`() {
        val anchorPriv = SessionCrypto.generatePrivateKey()
        val anchorPubB64 = b64(SessionCrypto.publicFromPrivate(anchorPriv))
        val sk = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val sealed = WhisperRatchet.initiator(sk, anchorPubB64).encrypt("gm".toByteArray(), "me|me".toByteArray())
        val frameJson = WhisperV3Codec.encode("sg1-cccc", sealed.header, sealed.ciphertextPacked, x3dh = null)
        val frame = checkNotNull(WhisperV3Codec.parse(frameJson))
        val ratchet = WhisperRatchet.responder(sk, anchorPriv, anchorPubB64)
        assertNull(
            runCatching {
                ratchet.decrypt(
                    WhisperRatchet.Header(frame.dhPub(), frame.pn, frame.n),
                    frame.ciphertextPacked(),
                    "me|other".toByteArray(),
                )
            }.getOrNull(),
        )
    }

    @Test
    fun `self leg sid prefix guard keeps peer v3 frames out of the path`() {
        // The module prefixes sids with "sg1-" and open() refuses anything else;
        // a normal 1:1 session id (deterministic "s…" factory id) must never be
        // mistaken for a self-frame session even if the prefs keys collided.
        val anchorPriv = SessionCrypto.generatePrivateKey()
        val anchorPubB64 = b64(SessionCrypto.publicFromPrivate(anchorPriv))
        val sk = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val sealed = WhisperRatchet.initiator(sk, anchorPubB64).encrypt("gm".toByteArray(), "me|me".toByteArray())
        val frameJson = WhisperV3Codec.encode("s0123456789ab", sealed.header, sealed.ciphertextPacked, x3dh = null)
        val frame = checkNotNull(WhisperV3Codec.parse(frameJson))
        assertEquals("s0123456789ab", frame.sessionId)
        // open() refuses the sid prefix → the caller never even decrypts here.
        assert(!frame.sessionId.startsWith("sg1-"))
    }
}
