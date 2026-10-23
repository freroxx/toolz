/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper.session

import com.frerox.toolz.crypto.SessionCrypto
import com.frerox.toolz.data.whisper.WhisperSessionFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * V6 (planwhisper.md §4.2): the session store's pure pipeline — deterministic
 * handshake acceptance rules and the serialize→hydrate roundtrip that must NEVER
 * drift, because a serialization bug silently eats field conversations.
 * The protector is faked; the Keystore-bound production wrapper is a one-line
 * delegation covered by device smoke tests.
 */
class WhisperSessionStoreRecordTest {

    /** Deterministic in-memory wrap (prefix-tagged so tamper paths are observable). */
    private class FakeProtector : WhisperSessionSecretProtector {
        override fun wrap(plain: ByteArray): String =
            "wrapped:" + Base64.getEncoder().encodeToString(plain)

        override fun unwrap(wrappedB64: String): ByteArray? =
            if (!wrappedB64.startsWith("wrapped:")) null
            else Base64.getDecoder().decode(wrappedB64.removePrefix("wrapped:"))
    }

    private fun freshPair(): Pair<WhisperRatchet, WhisperRatchet> {
        val sk = SessionCrypto.hkdfSha256("seed".toByteArray(), ByteArray(32), "t".toByteArray(), 32)
        val spkPriv = SessionCrypto.generatePrivateKey()
        val spkPubB64 = Base64.getEncoder().encodeToString(SessionCrypto.publicFromPrivate(spkPriv))
        return WhisperRatchet.initiator(sk, spkPubB64) to
            WhisperRatchet.responder(sk, spkPriv, spkPubB64)
    }

    // ------------------------------------------------------- acceptance rules

    @Test
    fun `acceptance - same sid replays are accepted`() {
        assertTrue(
            WhisperSessionStore.StoredSession.canAcceptHandshake(
                currentSid = "sAAA", currentPeerIkB64 = "IK1",
                incomingIkB64 = "IK1", incomingSid = "sAAA",
            ),
        )
    }

    @Test
    fun `acceptance - changed peer identity always accepted`() {
        assertTrue(
            WhisperSessionStore.StoredSession.canAcceptHandshake(
                currentSid = "sZZZ", currentPeerIkB64 = "OLD",
                incomingIkB64 = "NEW", incomingSid = "sMMM",
            ),
        )
    }

    @Test
    fun `acceptance - unknown pinned identity falls back to accept`() {
        assertTrue(
            WhisperSessionStore.StoredSession.canAcceptHandshake(
                currentSid = "sZZZ", currentPeerIkB64 = null,
                incomingIkB64 = "ANY", incomingSid = "sAAA",
            ),
        )
    }

    @Test
    fun `acceptance - racing initiators converge on lower sid`() {
        val lowerWins = WhisperSessionStore.StoredSession.canAcceptHandshake(
            currentSid = "sMMM", currentPeerIkB64 = "IK1",
            incomingIkB64 = "IK1", incomingSid = "sAAA",
        )
        val higherRejected = WhisperSessionStore.StoredSession.canAcceptHandshake(
            currentSid = "sAAA", currentPeerIkB64 = "IK1",
            incomingIkB64 = "IK1", incomingSid = "sMMM",
        )
        assertTrue(lowerWins)
        assertFalse(higherRejected)
    }

    // ------------------------------------- same-install (two accounts, one device)

    @Test
    fun `same-install - later session wins on both sides`() {
        // Account A created sOLD at t=1000; account B created sNEW at t=2000.
        // The older holder adopts, the newer holder keeps → both end on sNEW.
        assertTrue(
            WhisperSessionStore.StoredSession.acceptSameInstall(
                currentSid = "sOLD", currentCreatedAtMs = 1_000L,
                incomingSid = "sNEW", incomingCreatedAtMs = 2_000L,
            ),
        )
        assertFalse(
            WhisperSessionStore.StoredSession.acceptSameInstall(
                currentSid = "sNEW", currentCreatedAtMs = 2_000L,
                incomingSid = "sOLD", incomingCreatedAtMs = 1_000L,
            ),
        )
    }

    @Test
    fun `same-install - identical timestamp falls back to lower sid`() {
        assertTrue(
            WhisperSessionStore.StoredSession.acceptSameInstall(
                currentSid = "sZZZ", currentCreatedAtMs = 5_000L,
                incomingSid = "sAAA", incomingCreatedAtMs = 5_000L,
            ),
        )
        assertFalse(
            WhisperSessionStore.StoredSession.acceptSameInstall(
                currentSid = "sAAA", currentCreatedAtMs = 5_000L,
                incomingSid = "sZZZ", incomingCreatedAtMs = 5_000L,
            ),
        )
    }

    @Test
    fun `same-install - legacy header without timestamp uses sid tie-break`() {
        assertTrue(
            WhisperSessionStore.StoredSession.acceptSameInstall(
                currentSid = "sZZZ", currentCreatedAtMs = 5_000L,
                incomingSid = "sAAA", incomingCreatedAtMs = 0L,
            ),
        )
        assertFalse(
            WhisperSessionStore.StoredSession.acceptSameInstall(
                currentSid = "sAAA", currentCreatedAtMs = 5_000L,
                incomingSid = "sZZZ", incomingCreatedAtMs = 0L,
            ),
        )
    }

    @Test
    fun `same-install - cross-adopted deadlock converges on the newer session`() {
        // Regression for the October 2026 account-switch report: unconditional
        // same-install acceptance left A holding S_B and B holding S_A, so each
        // read only its own outgoing messages. Newer-wins picks exactly one
        // survivor: S_NEW (t=2000). S_OLD's holder (t=1000) yields.
        val aHoldsOld = WhisperSessionStore.StoredSession.acceptSameInstall(
            currentSid = "sOLD", currentCreatedAtMs = 1_000L,
            incomingSid = "sNEW", incomingCreatedAtMs = 2_000L,
        )
        val bHoldsNew = WhisperSessionStore.StoredSession.acceptSameInstall(
            currentSid = "sNEW", currentCreatedAtMs = 2_000L,
            incomingSid = "sOLD", incomingCreatedAtMs = 1_000L,
        )
        assertTrue("older session must adopt the newer one", aHoldsOld)
        assertFalse("newer session must stay authoritative", bHoldsNew)
    }

    @Test
    fun `codec - x3dh creation timestamp round-trips and legacy defaults to zero`() {
        val header = WhisperSessionFactory.X3dhHeader(
            ikPubB64 = "ik", ekPubB64 = "ek", spkKid = "spk", opkKid = "opk",
            createdMs = 1_700_000_000_000L,
        )
        assertEquals(header, WhisperV3Codec.x3dhFromJson(WhisperV3Codec.x3dhJson(header)))

        val legacyObj = buildJsonObject {
            put("ik", "ik")
            put("ek", "ek")
            put("spk", "spk")
        }
        val legacy = WhisperV3Codec.x3dhFromJson(legacyObj)
        assertNotNull(legacy)
        assertEquals(0L, legacy!!.createdMs)
    }

    // ------------------------------------------------- serialization pipeline

    @Test
    fun `pipeline - live state survives serialize hydrate with working chains`() {
        val (alice, bob) = freshPair()
        // Advance both chains so the snapshot carries non-trivial state.
        val m0 = alice.encrypt("a0".toByteArray())
        bob.decrypt(m0.header, m0.ciphertextPacked)
        val b0 = bob.encrypt("b0".toByteArray())
        alice.decrypt(b0.header, b0.ciphertextPacked)
        val a1 = alice.encrypt("a1".toByteArray())
        bob.decrypt(a1.header, a1.ciphertextPacked)

        val protector = FakeProtector()
        val live = WhisperSessionStore.Live(
            sessionId = "stest12345678",
            x3dhKeyWrapped = protector.wrap("shared-secret".toByteArray()),
            peerIkB64 = "ik-of-peer",
            createdAtMs = 1727000000000L,
            pendingHeader = WhisperSessionFactory.X3dhHeader("ik", "ek", "spk-kid", "opk-kid"),
            ratchet = alice,
        )

        val stored = serializeSession(live, protector)
        assertNotNull(stored)
        // The wrapped blob must not contain raw snapshot JSON.
        assertFalse(stored!!.ratchetWrapped!!.contains("\"rk\""))

        val jsonText = Json.encodeToString(WhisperSessionStore.StoredSession.serializer(), stored)
        val roundTripped = Json.decodeFromString(WhisperSessionStore.StoredSession.serializer(), jsonText)
        val hydrated = hydrateSession(roundTripped, protector)!!

        assertEquals(live.sessionId, hydrated.sessionId)
        assertEquals(live.peerIkB64, hydrated.peerIkB64)
        assertEquals(live.createdAtMs, hydrated.createdAtMs)
        assertEquals(live.pendingHeader, hydrated.pendingHeader)
        assertNotNull(hydrated.ratchet)

        // Continuity: restored instance keeps talking to the untouched peer.
        val a2 = hydrated.ratchet!!.encrypt("a2-after-restore".toByteArray())
        assertArrayEquals("a2-after-restore".toByteArray(), bob.decrypt(a2.header, a2.ciphertextPacked))
        val b1 = bob.encrypt("b1-to-restored".toByteArray())
        assertArrayEquals("b1-to-restored".toByteArray(), hydrated.ratchet!!.decrypt(b1.header, b1.ciphertextPacked))
    }

    @Test
    fun `pipeline - corrupt wrapped material hydrates to null never throws`() {
        val (alice, _) = freshPair()
        val protector = FakeProtector()
        val stored = serializeSession(
            WhisperSessionStore.Live(
                sessionId = "sx", x3dhKeyWrapped = protector.wrap(byteArrayOf(1)),
                peerIkB64 = null, createdAtMs = 1L, pendingHeader = null,
                ratchet = alice,
            ),
            protector,
        )!!
        val corrupted = stored.copy(ratchetWrapped = "NOT-WRAPPED")
        assertNull(hydrateSession(corrupted, protector))

        val badKey = stored.copy(x3dhKeyWrapped = "ALSO-NOT-WRAPPED")
        assertNull(hydrateSession(badKey, protector))
    }
}
