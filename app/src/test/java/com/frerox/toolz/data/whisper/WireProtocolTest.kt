/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P4a/P8: characterization tests for the pure wire-protocol decision core.
 * These pin the EXACT behavior that lived (untested) inside WhisperRepository —
 * including the P7b ratchet-first policy — so future refactors cannot silently
 * change negotiation, handshake gating, key-change classification or the
 * session self-heal freshness rule.
 */
class WireProtocolTest {

    // ───────────────────────── version negotiation ─────────────────────────

    @Test
    fun `negotiated version never exceeds our max speakable`() {
        assertEquals(3, WireProtocol.negotiatedVersion(ourVersion = 3, ourMaxSpeakable = 3, recordedPeerFloor = 99))
        assertEquals(2, WireProtocol.negotiatedVersion(ourVersion = 2, ourMaxSpeakable = 2, recordedPeerFloor = 3))
    }

    @Test
    fun `floor raises our minimum but never lowers it (v3 avoidance lives in shouldUseV3)`() {
        // PINNED ORIGINAL BEHAVIOR: negotiatedVersion only RAISES the floor toward
        // peers and clamps to OUR max; avoiding v3 to an envelope-only peer is the
        // job of shouldUseV3's floor gate, not this clamp.
        assertEquals(3, WireProtocol.negotiatedVersion(ourVersion = 3, ourMaxSpeakable = 3, recordedPeerFloor = 2))
        // Floor ABOVE what we speak must clamp back to our max.
        assertEquals(2, WireProtocol.negotiatedVersion(ourVersion = 2, ourMaxSpeakable = 2, recordedPeerFloor = 3))
        // No floor recorded: we speak our own version.
        assertEquals(3, WireProtocol.negotiatedVersion(ourVersion = 3, ourMaxSpeakable = 3, recordedPeerFloor = null))
    }

    @Test
    fun `floor merge keeps the lowest proven version and ignores non-positive`() {
        assertEquals(2, WireProtocol.mergePeerFloor(currentFloor = 3, incomingVersion = 2))
        assertEquals(2, WireProtocol.mergePeerFloor(currentFloor = 2, incomingVersion = 3))
        assertEquals(3, WireProtocol.mergePeerFloor(currentFloor = null, incomingVersion = 3))
        assertEquals(null.takeIf { false } ?: 0, WireProtocol.mergePeerFloor(currentFloor = null, incomingVersion = 0).takeIf { it == 0 } ?: -1)
    }

    // ───────────────────────── v3 gating (P7b ratchet-first) ─────────────────────────

    private val now = 1_000_000_000L

    @Test
    fun `ratchet disabled always falls back to envelopes`() {
        assertFalse(
            WireProtocol.shouldUseV3(
                ratchetEnabled = false, hasLiveSession = true, recordedPeerFloor = 3,
                ratchetProtocolVersion = 3, lastEstablishAttemptAtMs = null,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `live session always speaks v3 regardless of suppression`() {
        assertTrue(
            WireProtocol.shouldUseV3(
                ratchetEnabled = true, hasLiveSession = true, recordedPeerFloor = 3,
                ratchetProtocolVersion = 3, lastEstablishAttemptAtMs = now - 1,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `envelope-only peer is never re-attempted`() {
        assertFalse(
            WireProtocol.shouldUseV3(
                ratchetEnabled = true, hasLiveSession = false, recordedPeerFloor = 2,
                ratchetProtocolVersion = 3, lastEstablishAttemptAtMs = null,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `P7b fresh contact attempts v3 immediately (ratchet-first)`() {
        // The old envelope-first rule returned false without a cooldown window;
        // the new policy ALWAYS attempts establish for unproven peers.
        assertTrue(
            WireProtocol.shouldUseV3(
                ratchetEnabled = true, hasLiveSession = false, recordedPeerFloor = null,
                ratchetProtocolVersion = 3, lastEstablishAttemptAtMs = null,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `P7b recent failed attempt suppresses retries briefly then re-arms`() {
        val lastFailed = now - (WireProtocol.DEFAULT_V3_RETRY_SUPPRESSION_MS / 2)
        assertFalse(
            WireProtocol.shouldUseV3(
                ratchetEnabled = true, hasLiveSession = false, recordedPeerFloor = null,
                ratchetProtocolVersion = 3, lastEstablishAttemptAtMs = lastFailed,
                nowMs = now,
            ),
        )
        val longAgo = now - WireProtocol.DEFAULT_V3_RETRY_SUPPRESSION_MS - 1
        assertTrue(
            WireProtocol.shouldUseV3(
                ratchetEnabled = true, hasLiveSession = false, recordedPeerFloor = null,
                ratchetProtocolVersion = 3, lastEstablishAttemptAtMs = longAgo,
                nowMs = now,
            ),
        )
    }

    // ───────────────────────── key-change classification ─────────────────────────

    private val freshWindow = 30L * 60 * 1000          // FRESH_ROTATION_WINDOW_MS
    private val interval = 30L * 24 * 60 * 60 * 1000   // ROTATE_INTERVAL_MS

    @Test
    fun `same or first-contact keys are MATCH`() {
        assertEquals(
            KeyTrustStatus.MATCH,
            WireProtocol.classifyKeyChange("K1", "K1", serverRowUpdateAgeMs = 0, knownKeyAgeMs = 0, freshRotationWindowMs = freshWindow, rotationIntervalMs = interval),
        )
        assertEquals(
            KeyTrustStatus.MATCH,
            WireProtocol.classifyKeyChange(null, "K1", serverRowUpdateAgeMs = 0, knownKeyAgeMs = 0, freshRotationWindowMs = freshWindow, rotationIntervalMs = interval),
        )
        assertEquals(
            KeyTrustStatus.MATCH,
            WireProtocol.classifyKeyChange("K1", "", serverRowUpdateAgeMs = 0, knownKeyAgeMs = 0, freshRotationWindowMs = freshWindow, rotationIntervalMs = interval),
        )
    }

    @Test
    fun `fresh server rotation reads as ROTATED_AUTO not CHANGED`() {
        assertEquals(
            KeyTrustStatus.ROTATED_AUTO,
            WireProtocol.classifyKeyChange("OLD", "NEW", serverRowUpdateAgeMs = freshWindow - 1, knownKeyAgeMs = 0, freshRotationWindowMs = freshWindow, rotationIntervalMs = interval),
        )
    }

    @Test
    fun `aged-out pinned key reads as scheduled ROTATED_AUTO`() {
        assertEquals(
            KeyTrustStatus.ROTATED_AUTO,
            WireProtocol.classifyKeyChange("OLD", "NEW", serverRowUpdateAgeMs = Long.MAX_VALUE, knownKeyAgeMs = interval - 24L * 60 * 60 * 1000, freshRotationWindowMs = freshWindow, rotationIntervalMs = interval),
        )
    }

    @Test
    fun `unexpected early change with stale row is CHANGED (MITM warn)`() {
        assertEquals(
            KeyTrustStatus.CHANGED,
            WireProtocol.classifyKeyChange("OLD", "NEW", serverRowUpdateAgeMs = Long.MAX_VALUE, knownKeyAgeMs = 3L * 24 * 60 * 60 * 1000, freshRotationWindowMs = freshWindow, rotationIntervalMs = interval),
        )
    }

    @Test
    fun `isFreshServerRotation mirrors the window bounds`() {
        assertTrue(WireProtocol.isFreshServerRotation(0L, freshWindow))
        assertTrue(WireProtocol.isFreshServerRotation(freshWindow, freshWindow))
        assertFalse(WireProtocol.isFreshServerRotation(freshWindow + 1, freshWindow))
        assertFalse(WireProtocol.isFreshServerRotation(null, freshWindow))
        // Negative ages (clock skew far in the future) are not "fresh".
        assertFalse(WireProtocol.isFreshServerRotation(-1L, freshWindow))
    }

    // ───────────────────────── session self-heal freshness ─────────────────────────

    // Realistic production value: 5 minutes of clock-skew tolerance.
    private val skew = 5 * 60_000L

    @Test
    fun `rows older than session creation beyond skew never tear down state`() {
        // Row predates the session by far more than the slack ⇒ cached history.
        assertFalse(WireProtocol.shouldTeardownStaleSession(rowEpochMs = 0, sessionCreatedAtMs = 5_000_000, nowMs = 6_000_000, clockSkewSlackMs = skew))
    }

    @Test
    fun `rows future-dated beyond skew never tear down state`() {
        assertFalse(WireProtocol.shouldTeardownStaleSession(rowEpochMs = 9_000_000 + skew, sessionCreatedAtMs = 5_000_000, nowMs = 6_000_000, clockSkewSlackMs = skew))
    }

    @Test
    fun `fresh peer traffic tears down state`() {
        assertTrue(WireProtocol.shouldTeardownStaleSession(rowEpochMs = 5_500_000, sessionCreatedAtMs = 5_000_000, nowMs = 6_000_000, clockSkewSlackMs = skew))
    }

    @Test
    fun `boundaries are strict inequalities exactly as the repository had them`() {
        // row + skew == createdAt ⇒ NOT filtered as pre-session (falls through).
        assertTrue(WireProtocol.shouldTeardownStaleSession(rowEpochMs = 5_000_000 - skew, sessionCreatedAtMs = 5_000_000, nowMs = 6_000_000, clockSkewSlackMs = skew))
        // row - skew == now ⇒ NOT filtered as future-dated (falls through).
        assertTrue(WireProtocol.shouldTeardownStaleSession(rowEpochMs = 6_000_000 + skew, sessionCreatedAtMs = 5_000_000, nowMs = 6_000_000, clockSkewSlackMs = skew))
    }
}
