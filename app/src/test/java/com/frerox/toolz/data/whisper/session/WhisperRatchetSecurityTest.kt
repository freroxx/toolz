/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper.session

import com.frerox.toolz.crypto.SessionCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Phase 1B protocol security matrix (WHISPER.md §2.3): copy-commit decrypt,
 * skip-limit rejection, and bounded-loss honesty.
 *
 * - out-of-order / replay: legitimate reordering and duplicates open.
 * - reinstall desync: wiped peer state yields bounded loss only (no corruption).
 * - key-change strict: unknown dhPub fails closed without mutating chains.
 * - skipped-limit: far-future n rejected, window left intact.
 * - forged-header-no-mutation: failed decrypt leaves snapshot bit-identical.
 *
 * X3DH secrets here use the same HKDF framing as WhisperSessionFactory
 * (0xFF pad ‖ concat → HKDF-SHA256/WhisperX3DH-v1); RFC 7748 DH vectors stay
 * pinned in SessionCryptoVectorTest.
 */
class WhisperRatchetSecurityTest {

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    private fun freshPair(): Pair<WhisperRatchet, WhisperRatchet> {
        val sk = SessionCrypto.hkdfSha256("sec-seed".toByteArray(), ByteArray(32), "t".toByteArray(), 32)
        val spkPriv = SessionCrypto.generatePrivateKey()
        val spkPubB64 = b64(SessionCrypto.publicFromPrivate(spkPriv))
        return WhisperRatchet.initiator(sk, spkPubB64) to
            WhisperRatchet.responder(sk, spkPriv, spkPubB64)
    }

    // ---------------------------------------------------------- out-of-order

    @Test
    fun `out-of-order - three messages open in any arrival order`() {
        val (alice, bob) = freshPair()
        val sealed = (0 until 3).map { i -> alice.encrypt("ooo-$i".toByteArray()) }
        // Deliver 2, 0, 1.
        assertArrayEquals("ooo-2".toByteArray(), bob.decrypt(sealed[2].header, sealed[2].ciphertextPacked))
        assertArrayEquals("ooo-0".toByteArray(), bob.decrypt(sealed[0].header, sealed[0].ciphertextPacked))
        assertArrayEquals("ooo-1".toByteArray(), bob.decrypt(sealed[1].header, sealed[1].ciphertextPacked))
    }

    // --------------------------------------------------------------- replay

    @Test
    fun `replay - duplicate delivery opens identically and chains stay healthy`() {
        val (alice, bob) = freshPair()
        val s0 = alice.encrypt("replay-me".toByteArray())
        val first = bob.decrypt(s0.header, s0.ciphertextPacked)
        val second = bob.decrypt(s0.header, s0.ciphertextPacked)
        assertArrayEquals(first, second)
        assertArrayEquals("replay-me".toByteArray(), first)
        // Chains still advance after the duplicate.
        val s1 = alice.encrypt("after-replay".toByteArray())
        assertArrayEquals("after-replay".toByteArray(), bob.decrypt(s1.header, s1.ciphertextPacked))
    }

    // ------------------------------------------------------ reinstall desync

    @Test
    fun `reinstall - wiped peer state yields bounded loss only`() {
        val (alice, bob) = freshPair()
        val hello = alice.encrypt("hello".toByteArray())
        assertArrayEquals("hello".toByteArray(), bob.decrypt(hello.header, hello.ciphertextPacked))

        // Bob reinstalls: fresh identity, fresh X3DH secret, empty ratchet.
        val sk2 = SessionCrypto.hkdfSha256("other-seed".toByteArray(), ByteArray(32), "t".toByteArray(), 32)
        val spkPriv2 = SessionCrypto.generatePrivateKey()
        val spkPubB64_2 = b64(SessionCrypto.publicFromPrivate(spkPriv2))
        val bobNew = WhisperRatchet.responder(
            sk2, spkPriv2,
            spkPubB64_2,
        )

        // In-flight message from the OLD session never opens on wiped state.
        val inFlight = alice.encrypt("sent-during-wipe".toByteArray())
        assertThrows(WhisperRatchetLostMessage::class.java) {
            bobNew.decrypt(inFlight.header, inFlight.ciphertextPacked)
        }
        // And the new session's frames are isolated from the old session.
        val aliceOldNext = alice.encrypt("old-session-next".toByteArray())
        assertThrows(WhisperRatchetLostMessage::class.java) {
            bobNew.decrypt(aliceOldNext.header, aliceOldNext.ciphertextPacked)
        }
    }

    // -------------------------------------------------------- key-change strict

    @Test
    fun `key-change - unknown dhPub fails closed without mutating state`() {
        val (alice, bob) = freshPair()
        val prime = alice.encrypt("prime".toByteArray())
        assertArrayEquals("prime".toByteArray(), bob.decrypt(prime.header, prime.ciphertextPacked))

        val before = bob.snapshot()
        // Attacker forges a header with a fresh unknown DH key + garbage body.
        val attackerPriv = SessionCrypto.generatePrivateKey()
        val attackerPub = SessionCrypto.publicFromPrivate(attackerPriv)
        val forged = WhisperRatchet.Header(attackerPub, 0, 0)
        assertThrows(WhisperRatchetLostMessage::class.java) {
            bob.decrypt(forged, ByteArray(48) { it.toByte() })
        }
        assertEquals("forged unknown-dhPub must not mutate ratchet", before, bob.snapshot())

        // Session still healthy afterwards.
        val next = alice.encrypt("still-alive".toByteArray())
        assertArrayEquals("still-alive".toByteArray(), bob.decrypt(next.header, next.ciphertextPacked))
    }

    // ---------------------------------------------------------- skipped limit

    @Test
    fun `skipped-limit - far-future n rejected and window left intact`() {
        val (alice, bob) = freshPair()
        // Warm one legitimate exchange so the receive chain exists.
        val p0 = alice.encrypt("p0".toByteArray())
        bob.decrypt(p0.header, p0.ciphertextPacked)

        val before = bob.snapshot()
        val beforeSkipped = bob.skipped.size
        // Same-chain far-future n (gap 500 > MAX 400) with garbage body.
        val farHeader = WhisperRatchet.Header(p0.header.dhPub.copyOf(), 0, 500)
        val ex = assertThrows(WhisperRatchetLostMessage::class.java) {
            bob.decrypt(farHeader, ByteArray(64) { 0x41 })
        }
        assertTrue("must name the skip window", (ex.message ?: "").contains("skipped-key"))
        assertEquals("window must stay intact", before, bob.snapshot())
        assertEquals(beforeSkipped, bob.skipped.size)

        // Legitimate next message still opens (no poisoning).
        val legit = alice.encrypt("legit-after-far".toByteArray())
        assertArrayEquals(
            "legit-after-far".toByteArray(),
            bob.decrypt(legit.header, legit.ciphertextPacked),
        )
    }

    @Test
    fun `skipped-limit - full window rejects overflow instead of evicting`() {
        val (alice, bob) = freshPair()
        // Alice fires 401 messages on one chain; Bob opens ONLY the last (n=400),
        // banking exactly MAX_SKIPPED skipped keys (0..399).
        val burst = (0 until 401).map { i -> alice.encrypt("burst-$i".toByteArray()) }
        val last = burst.last()
        assertEquals(400, last.header.n)
        assertArrayEquals("burst-400".toByteArray(), bob.decrypt(last.header, last.ciphertextPacked))
        assertEquals(WhisperRatchet.MAX_SKIPPED, bob.skipped.size)

        val before = bob.snapshot()
        // Alice sends 4 more (n=401..404); the n=404 frame would need 4 new skip
        // keys on top of a full window → must throw, keeping all 400 intact.
        val more = (401 until 405).map { i -> alice.encrypt("burst-$i".toByteArray()) }
        val overflowFrame = more.last()
        assertThrows(WhisperRatchetLostMessage::class.java) {
            bob.decrypt(overflowFrame.header, overflowFrame.ciphertextPacked)
        }
        assertEquals("full window must not evict oldest", before, bob.snapshot())
        assertEquals(WhisperRatchet.MAX_SKIPPED, bob.skipped.size)

        // The banked keys still open (oldest was NOT evicted).
        assertArrayEquals(
            "burst-0".toByteArray(),
            bob.decrypt(burst.first().header, burst.first().ciphertextPacked),
        )
    }

    // ------------------------------------------------- forged-header-no-mutation

    @Test
    fun `forged-header - failed decrypt leaves snapshot bit-identical`() {
        val (alice, bob) = freshPair()
        val s0 = alice.encrypt("s0".toByteArray())
        bob.decrypt(s0.header, s0.ciphertextPacked)
        val s1 = alice.encrypt("s1".toByteArray())
        bob.decrypt(s1.header, s1.ciphertextPacked)

        fun assertNoMutation(header: WhisperRatchet.Header, packed: ByteArray, ad: ByteArray = ByteArray(0)) {
            val before = bob.snapshot()
            assertThrows(WhisperRatchetLostMessage::class.java) {
                bob.decrypt(header, packed, ad)
            }
            assertEquals("failed decrypt mutated ratchet state", before, bob.snapshot())
        }

        // 1. Tampered ciphertext under a valid header.
        val legit = alice.encrypt("victim".toByteArray())
        val tampered = legit.ciphertextPacked.copyOf().also {
            it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte()
        }
        assertNoMutation(legit.header, tampered)

        // 2. Wrong associated data (routing tamper).
        assertNoMutation(legit.header, legit.ciphertextPacked, "wrong-ad".toByteArray())

        // 3. Forged DH key (would previously trigger a poisoning DH step).
        val forgedDh = WhisperRatchet.Header(SessionCrypto.publicFromPrivate(SessionCrypto.generatePrivateKey()), 0, 0)
        assertNoMutation(forgedDh, ByteArray(48) { 0x7f })

        // 4. Far-future n on the live chain.
        val farN = WhisperRatchet.Header(legit.header.dhPub.copyOf(), legit.header.pn, legit.header.n + 999)
        assertNoMutation(farN, ByteArray(48) { 0x11 })

        // 5. Negative counter.
        val negN = WhisperRatchet.Header(legit.header.dhPub.copyOf(), 0, -1)
        assertNoMutation(negN, ByteArray(48) { 0x22 })

        // Session fully healthy after all forgeries: the real message opens.
        assertArrayEquals("victim".toByteArray(), bob.decrypt(legit.header, legit.ciphertextPacked))
    }
}
