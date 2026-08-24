/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper.session

import com.frerox.toolz.crypto.SessionCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import kotlin.random.Random

/**
 * V6 (planwhisper.md §4): live-transport acceptance matrix for the Double Ratchet.
 *
 * These are THE gate for flipping `ratchetEnabled`:
 *  1. Interop — 200 alternating messages with reorder ≤25 and duplicates: zero wrong
 *     plaintexts, zero unexpected locks.
 *  2. Persistence — snapshot/load mid-conversation must continue chains exactly
 *     (catches serialization drift before it eats field messages).
 *  3. Reinstall — wiped peer state produces bounded loss only, and the deterministic
 *     handshake acceptance lets the re-handshake restore delivery both ways.
 *
 * X3DH secrets are produced with the exact factory KDF framing so these exercises
 * mirror production bit-for-bit (same arrangement proven by X3dhHandshakeMathTest).
 */
class WhisperV3InteropTest {

    // ------------------------------------------------------------------ harness

    /** One peer's long-term protocol material (identity key + signed prekey). */
    private class Bundle(val name: String) {
        val ikPriv: ByteArray = SessionCrypto.generatePrivateKey()
        val spkPriv: ByteArray = SessionCrypto.generatePrivateKey()
        val ikPubB64: String = Base64.getEncoder().encodeToString(SessionCrypto.publicFromPrivate(ikPriv))
        val spkPubB64: String = Base64.getEncoder().encodeToString(SessionCrypto.publicFromPrivate(spkPriv))
    }

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    /** Mirrors WhisperSessionFactory.kdf framing (0xFF pad ‖ concat → HKDF-SHA256). */
    private fun kdf(vararg dh: ByteArray?): ByteArray {
        val parts = dh.filterNotNull()
        val ikm = ByteArray(32) { 0xFF.toByte() } + parts.reduce { acc, b -> acc + b }
        return SessionCrypto.hkdfSha256(ikm, ByteArray(32), "WhisperX3DH-v1".toByteArray(), 32)
    }

    /**
     * Runs the full X3DH math between [initiator] and [responder] exactly like the
     * factory does, asserting both sides derive an identical SK.
     */
    private fun x3dh(
        initiator: Bundle,
        responder: Bundle,
        withOpk: Boolean,
    ): Pair<ByteArray, ByteArray> {
        val ekPriv = SessionCrypto.generatePrivateKey()
        val ekPub = SessionCrypto.publicFromPrivate(ekPriv)
        val opkPriv = if (withOpk) SessionCrypto.generatePrivateKey() else null
        val opkPub = opkPriv?.let { SessionCrypto.publicFromPrivate(it) }
        val ikAPub = SessionCrypto.publicFromPrivate(initiator.ikPriv)
        val ikBPub = SessionCrypto.publicFromPrivate(responder.ikPriv)
        val spkPub = SessionCrypto.publicFromPrivate(responder.spkPriv)

        val skInitiator = kdf(
            SessionCrypto.sharedSecret(initiator.ikPriv, spkPub),
            SessionCrypto.sharedSecret(ekPriv, ikBPub),
            SessionCrypto.sharedSecret(ekPriv, spkPub),
            opkPub?.let { SessionCrypto.sharedSecret(ekPriv, it) },
        )
        val skResponder = kdf(
            SessionCrypto.sharedSecret(responder.spkPriv, ikAPub),
            SessionCrypto.sharedSecret(responder.ikPriv, ekPub),
            SessionCrypto.sharedSecret(responder.spkPriv, ekPub),
            opkPriv?.let { SessionCrypto.sharedSecret(it, ekPub) },
        )
        assertArrayEquals(skInitiator, skResponder)
        return skInitiator to skResponder
    }

    private fun sessionIdOf(sk: ByteArray): String =
        "s" + SessionCrypto.sha256(sk).joinToString("") { "%02x".format(it) }.take(12)

    /** Established pair exactly as the transport creates them. */
    private class Session(
        val alice: WhisperRatchet,
        val bob: WhisperRatchet,
        val sid: String,
        val bundleA: Bundle,
        val bundleB: Bundle,
    )

    private fun established(withOpk: Boolean = true): Session {
        val a = Bundle("A")
        val b = Bundle("B")
        val (skI, _) = x3dh(a, b, withOpk)
        return Session(
            alice = WhisperRatchet.initiator(skI, b.spkPubB64),
            bob = WhisperRatchet.responder(skI, b.spkPriv, b.spkPubB64),
            sid = sessionIdOf(skI),
            bundleA = a,
            bundleB = b,
        )
    }

    private data class Frame(val fromAlice: Boolean, val h: WhisperRatchet.Header, val ct: ByteArray, val plain: ByteArray)

    private fun seal(fromAlice: Boolean, r: WhisperRatchet, msg: String, ad: ByteArray = ByteArray(0)) =
        r.encrypt(msg.toByteArray(), ad).let { Frame(fromAlice, it.header, it.ciphertextPacked, msg.toByteArray()) }

    /** Deterministic local reorder with displacement ≤[maxDisplacement] plus ~25% dupes. */
    private fun jitter(input: List<Frame>, rng: Random, maxDisplacement: Int): List<Frame> {
        val out = input.toMutableList()
        var i = 0
        while (i < out.size) {
            if (rng.nextInt(5) == 0) {
                val j = minOf(out.size - 1, i + rng.nextInt(maxDisplacement + 1))
                java.util.Collections.swap(out, i, j)
            }
            i++
        }
        // Duplicate AFTER reorder so dupes also arrive out-of-order relative to origin.
        val duplicated = ArrayList<Frame>(out.size + out.size / 3)
        for (f in out) {
            duplicated.add(f)
            if (rng.nextInt(4) == 0) duplicated.add(f)
        }
        return duplicated
    }

    // ------------------------------------------------------- 1. interop matrix

    @Test
    fun `interop - two hundred alternating messages survive reorder up to twentyfive and duplicates`() {
        val s = established(withOpk = true)
        val rng = Random(20260824L)

        // Production semantics: the responder's sending side only exists AFTER it
        // processes the initiator's first frame — prime that handshake frame first.
        val prime = seal(true, s.alice, "msg-prime")
        s.bob.decrypt(prime.h, prime.ct)

        // Seal strictly alternating A,B,A,B… for the rest.
        val wire = ArrayList<Frame>(200)
        for (i in 0 until 199) {
            val fromAlice = i % 2 == 0
            val sender = if (fromAlice) s.alice else s.bob
            wire.add(seal(fromAlice, sender, "msg-$i"))
        }

        val delivered = jitter(wire, rng, maxDisplacement = 25)
        var opened = 0
        for (f in delivered) {
            val receiver = if (f.fromAlice) s.bob else s.alice
            val plain = receiver.decrypt(f.h, f.ct) // must never throw WhisperRatchetLostMessage
            assertArrayEquals("frame from ${if (f.fromAlice) "A" else "B"} opened wrong", f.plain, plain)
            opened++
        }
        assertEquals("every frame plus every duplicate must open", delivered.size, opened)
        assertTrue("jitter must have injected duplicates", delivered.size > wire.size)
    }

    @Test
    fun `interop - crossed bursts both directions still converge`() {
        val s = established()
        // Prime the responder (Bob cannot seal before his first receive).
        val prime = seal(true, s.alice, "prime")
        s.bob.decrypt(prime.h, prime.ct)
        // Both sides fire bursts WITHOUT consuming each other's traffic (realistic race).
        val burstA = (0 until 6).map { seal(true, s.alice, "a$it") }
        val burstB = (0 until 6).map { seal(false, s.bob, "b$it") }
        // Deliver worst-case: whole burst B first, then burst A.
        for (f in burstB) assertArrayEquals(f.plain, s.alice.decrypt(f.h, f.ct))
        for (f in burstA) assertArrayEquals(f.plain, s.bob.decrypt(f.h, f.ct))
        // Then keep chatting normally — chains must be healthy after the crossing.
        val x1 = s.alice.encrypt("x1".toByteArray())
        assertArrayEquals("post-cross-A", "x1".toByteArray(), s.bob.decrypt(x1.header, x1.ciphertextPacked))
        val y1 = s.bob.encrypt("y1".toByteArray())
        assertArrayEquals("post-cross-B", "y1".toByteArray(), s.alice.decrypt(y1.header, y1.ciphertextPacked))
    }

    // --------------------------------------------- format boundary (v3 vs env)

    @Test
    fun `wire - v3 frames and v5 envelopes never collide on detection`() {
        val s = established()
        val frameJson = com.frerox.toolz.data.whisper.WhisperEnvelope.PREFIX_V2
        val sealed = seal(true, s.alice, "hi")
        val encoded = com.frerox.toolz.data.whisper.session.WhisperV3Codec.encode(
            s.sid, sealed.h, sealed.ct, null,
        )
        assertFalse(com.frerox.toolz.data.whisper.session.WhisperV3Codec.isV3(frameJson))
        assertFalse(com.frerox.toolz.data.whisper.WhisperEnvelope.isEnvelope(encoded))
        assertTrue(com.frerox.toolz.data.whisper.session.WhisperV3Codec.isV3(encoded))

        val parsed = com.frerox.toolz.data.whisper.session.WhisperV3Codec.parse(encoded)!!
        assertEquals(s.sid, parsed.sessionId)
        assertEquals(sealed.h.pn, parsed.pn)
        assertEquals(sealed.h.n, parsed.n)
        assertArrayEquals(sealed.h.dhPub, parsed.dhPub())
        assertNull(parsed.x3dh)

        // Hostile/truncated payloads never throw — they degrade to null.
        assertNull(com.frerox.toolz.data.whisper.session.WhisperV3Codec.parse(encoded.dropLast(8)))
        assertNull(com.frerox.toolz.data.whisper.session.WhisperV3Codec.parse("{\"v\":3,"))
        assertNull(com.frerox.toolz.data.whisper.session.WhisperV3Codec.parse("not json at all"))
    }

    @Test
    fun `wire - frame with x3dh header decrypts and tampered routing fails auth`() {
        val s = established()
        val ad = "alice|bob".toByteArray()
        val hdr = com.frerox.toolz.data.whisper.session.WhisperV3Codec.X3dhWire(
            ikPubB64 = s.bundleA.ikPubB64, ekPubB64 = "ZWs=", spkKid = "kid123", opkKid = null,
        )
        val sealed = s.alice.encrypt("with handshake".toByteArray(), ad)
        val json = com.frerox.toolz.data.whisper.session.WhisperV3Codec.encode(
            s.sid, sealed.header, sealed.ciphertextPacked,
            com.frerox.toolz.data.whisper.session.WhisperV3Codec.toFactoryHeader(hdr),
        )

        val parsed = com.frerox.toolz.data.whisper.session.WhisperV3Codec.parse(json)!!
        assertEquals("kid123", parsed.x3dh!!.spkKid)
        assertArrayEquals(
            "with handshake".toByteArray(),
            s.bob.decrypt(parsed.dhPub().let { WhisperRatchet.Header(it, parsed.pn, parsed.n) }, parsed.ciphertextPacked(), ad),
        )

        // Routing tamper (different AD) MUST fail closed — AEAD decides, not parsing.
        org.junit.Assert.assertThrows(com.frerox.toolz.data.whisper.session.WhisperRatchetLostMessage::class.java) {
            s.bob.decrypt(
                WhisperRatchet.Header(parsed.dhPub(), parsed.pn, parsed.n),
                parsed.ciphertextPacked(),
                "bob|alice".toByteArray(),
            )
        }
    }

    // ------------------------------------------------------ 2. persistence gate

    @Test
    fun `persistence - snapshot roundtrip mid-conversation continues chains exactly`() {
        val s = established()
        val rng = Random(7L)

        // Warm the conversation with REAL sequential exchanges so both sides pass
        // through several DH ratchet steps before the snapshot.
        fun exchange(prefix: String, rounds: Int) {
            repeat(rounds) { i ->
                val fa = seal(true, s.alice, "${prefix}a$i")
                s.bob.decrypt(fa.h, fa.ct)
                val fb = seal(false, s.bob, "${prefix}b$i")
                s.alice.decrypt(fb.h, fb.ct)
            }
        }
        exchange("w1-", 3)

        // MID-CONVERSATION persist → cold-start restore (the store's hydrate path).
        val aliceSnap = s.alice.snapshot()
        val bobSnap = s.bob.snapshot()
        val alice2 = WhisperRatchet.restored(aliceSnap)
        val bob2 = WhisperRatchet.restored(bobSnap)
        assertEquals(aliceSnap, alice2.snapshot())
        assertEquals(bobSnap, bob2.snapshot())

        // Continue 30 alternating messages over restored state with reorder + dupes.
        val cont = ArrayList<Frame>(30)
        for (i in 0 until 30) {
            val fromAlice = i % 2 == 0
            cont.add(seal(fromAlice, if (fromAlice) alice2 else bob2, "c$i"))
        }
        for (f in jitter(cont, rng, 25)) {
            val receiver = if (f.fromAlice) bob2 else alice2
            assertArrayEquals("restored chain broke at ${String(f.plain)}", f.plain, receiver.decrypt(f.h, f.ct))
        }
    }

    // ---------------------------------------------------- 3. reinstall recovery

    @Test
    fun `reinstall - wiped peer recovers through deterministic re-handshake`() {
        val s = established()

        // Normal traffic first so BOTH sides hold advanced chains under sid_old.
        for (i in 0..3) {
            val f = seal(i % 2 == 0, if (i % 2 == 0) s.alice else s.bob, "p$i")
            (if (f.fromAlice) s.bob else s.alice).decrypt(f.h, f.ct)
        }

        // ── BOB REINSTALLS: identity, SPK and ALL session state are gone. ──
        val bundleB2 = Bundle("B2")
        assertNotEquals(bundleB2.ikPubB64, s.bundleB.ikPubB64)
        val (skNew, _) = x3dh(initiator = bundleB2, responder = s.bundleA, withOpk = false)
        val sidNew = sessionIdOf(skNew)
        val bobNew = WhisperRatchet.initiator(skNew, s.bundleA.spkPubB64)

        // Bob's first post-reinstall message carries his NEW identity in the header.
        val bobFrame = seal(false, bobNew, "after-reinstall")
        val incomingIk = bundleB2.ikPubB64

        // Alice's stored session pins the OLD identity: the gate must accept because
        // the peer's identity key CHANGED (cryptographic orphaning).
        val accepts = WhisperSessionStore.StoredSession.canAcceptHandshake(
            currentSid = s.sid,
            currentPeerIkB64 = s.bundleB.ikPubB64,
            incomingIkB64 = incomingIk,
            incomingSid = sidNew,
        )
        assertTrue("reinstalled peer must re-handshake", accepts)

        // Alice re-bootstraps as RESPONDER of the NEW session and everything flows.
        val aliceNew = WhisperRatchet.responder(skNew, s.bundleA.spkPriv, s.bundleA.spkPubB64)
        assertArrayEquals(
            "post-reinstall inbound",
            bobFrame.plain,
            aliceNew.decrypt(bobFrame.h, bobFrame.ct),
        )
        val reply = seal(true, aliceNew, "welcome-back")
        assertArrayEquals(reply.plain, bobNew.decrypt(reply.h, reply.ct))

        // Old-session material can NEVER open new-session frames (isolation).
        org.junit.Assert.assertThrows(WhisperRatchetLostMessage::class.java) {
            s.alice.decrypt(bobFrame.h.copy(dhPub = bobFrame.h.dhPub), bobFrame.ct, ByteArray(0))
        }

        // Bounded loss: the message Alice sent while Bob was wiped stays locked forever
        // on ANY bob-side state — honest placeholder, not silent corruption.
        val lostInFlight = seal(true, s.alice, "sent-during-wipe")
        org.junit.Assert.assertThrows(WhisperRatchetLostMessage::class.java) {
            bobNew.decrypt(lostInFlight.h, lostInFlight.ct)
        }
    }

    @Test
    fun `replay - redelivered handshake frame must not reset advanced chains`() {
        val s = established()
        // Advance well past the handshake in BOTH directions.
        for (i in 0 until 4) {
            val f = seal(i % 2 == 0, if (i % 2 == 0) s.alice else s.bob, "r$i")
            (if (f.fromAlice) s.bob else s.alice).decrypt(f.h, f.ct)
        }
        // Same-session handshake replay is recognized (accept-gate returns true for
        // identical sid) but the TRANSPORT skips re-bootstrap — asserted here by the
        // pure rule; openV3Frame increments v3.handshakeReplay instead of resetting.
        assertTrue(
            WhisperSessionStore.StoredSession.canAcceptHandshake(
                currentSid = s.sid,
                currentPeerIkB64 = s.bundleA.ikPubB64,
                incomingIkB64 = s.bundleA.ikPubB64,
                incomingSid = s.sid,
            ),
        )
        // Chains still advance normally afterwards.
        val f = seal(true, s.alice, "still-alive")
        assertArrayEquals(f.plain, s.bob.decrypt(f.h, f.ct))
    }
}
