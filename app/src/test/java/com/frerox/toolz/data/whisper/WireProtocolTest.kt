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
            WireProtocol.classifyKeyChangeStrict("K1", "K1"),
        )
        assertEquals(
            KeyTrustStatus.MATCH,
            WireProtocol.classifyKeyChangeStrict(null, "K1"),
        )
        assertEquals(
            KeyTrustStatus.MATCH,
            WireProtocol.classifyKeyChangeStrict("K1", ""),
        )
    }

    @Test
    @Suppress("DEPRECATION")
    fun `legacy window overload is fail-closed (ignores freshness windows)`() {
        // Phase-1A: both auto-accept paths removed — the compat overload must
        // agree with the strict path (CHANGED without a cert).
        assertEquals(
            KeyTrustStatus.CHANGED,
            WireProtocol.classifyKeyChange("OLD", "NEW", serverRowUpdateAgeMs = freshWindow - 1, knownKeyAgeMs = 0, freshRotationWindowMs = freshWindow, rotationIntervalMs = interval),
        )
        assertEquals(
            KeyTrustStatus.CHANGED,
            WireProtocol.classifyKeyChange("OLD", "NEW", serverRowUpdateAgeMs = Long.MAX_VALUE, knownKeyAgeMs = interval - 24L * 60 * 60 * 1000, freshRotationWindowMs = freshWindow, rotationIntervalMs = interval),
        )
    }

    @Test
    fun `fresh server rotation without cert is CHANGED not ROTATED_AUTO`() {
        assertEquals(
            KeyTrustStatus.CHANGED,
            WireProtocol.classifyKeyChangeStrict("OLD", "NEW"),
        )
    }

    @Test
    fun `aged-out pinned key without cert is CHANGED not ROTATED_AUTO`() {
        assertEquals(
            KeyTrustStatus.CHANGED,
            WireProtocol.classifyKeyChangeStrict("OLD", "NEW"),
        )
    }

    @Test
    fun `verified peer never auto-rotates even with valid-shaped input`() {
        // Verified safety numbers require manual re-verify: CHANGED even though
        // an unverified peer with a valid cert would read ROTATED_AUTO.
        assertEquals(
            KeyTrustStatus.CHANGED,
            WireProtocol.classifyKeyChangeStrict("OLD", "NEW", isVerified = true, rotationCert = "bogus"),
        )
    }

    @Test
    fun `rotation cert with garbage never auto-accepts`() {
        assertEquals(
            KeyTrustStatus.CHANGED,
            WireProtocol.classifyKeyChangeStrict("OLD", "NEW", rotationCert = "not-a-signature"),
        )
        assertFalse(WireProtocol.verifyRotationCert("OLD", "NEW", null))
        assertFalse(WireProtocol.verifyRotationCert("OLD", "NEW", ""))
        assertFalse(WireProtocol.verifyRotationCert("", "NEW", "x"))
    }

    @Test
    fun `filterKeysChainedToPinned drops unproven server keys`() {
        val pinned = "PINNED"
        val candidates = mapOf("a" to "PINNED", "b" to "ROGUE")
        assertEquals(
            mapOf("a" to "PINNED"),
            WireProtocol.filterKeysChainedToPinned(pinned, candidates),
        )
        // First contact (no pin): TOFU bootstrap passes everything through.
        assertEquals(candidates, WireProtocol.filterKeysChainedToPinned(null, candidates))
    }

    @Test
    fun `unexpected early change with stale row is CHANGED (MITM warn)`() {
        assertEquals(
            KeyTrustStatus.CHANGED,
            WireProtocol.classifyKeyChangeStrict("OLD", "NEW"),
        )
    }

    @Test
    @Suppress("DEPRECATION")
    fun `isFreshServerRotation mirrors the window bounds (deprecated compat)`() {
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

    // ───────────────────────── residual-downgrade N-block ─────────────────────────

    @Test
    fun `downgrade blocked after N consecutive establish failures without live proven session`() {
        assertTrue(WireProtocol.shouldBlockDowngradeAfterFails(hasLiveSession = false, sessionProven = false, failedEstablishCount = 3))
        assertTrue(WireProtocol.shouldBlockDowngradeAfterFails(hasLiveSession = false, sessionProven = false, failedEstablishCount = 10))
        // Below threshold: fallback still allowed.
        assertFalse(WireProtocol.shouldBlockDowngradeAfterFails(hasLiveSession = false, sessionProven = false, failedEstablishCount = 2))
        assertFalse(WireProtocol.shouldBlockDowngradeAfterFails(hasLiveSession = false, sessionProven = false, failedEstablishCount = 0))
        // Live or proven session: never blocked by this rule (other rules own those).
        assertFalse(WireProtocol.shouldBlockDowngradeAfterFails(hasLiveSession = true, sessionProven = false, failedEstablishCount = 99))
        assertFalse(WireProtocol.shouldBlockDowngradeAfterFails(hasLiveSession = false, sessionProven = true, failedEstablishCount = 99))
        // Threshold override honored.
        assertTrue(WireProtocol.shouldBlockDowngradeAfterFails(hasLiveSession = false, sessionProven = false, failedEstablishCount = 1, threshold = 1))
        assertEquals(3, WireProtocol.DOWNGRADE_BLOCK_AFTER_N_FAILS)
    }

    // ───────────────────────── ROTv2 rotation anti-replay ─────────────────────────

    private fun genP256Signer(): Triple<String, java.security.PrivateKey, String> {
        val kpg = java.security.KeyPairGenerator.getInstance("EC")
        kpg.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        val kp = kpg.generateKeyPair()
        val pubB64 = java.util.Base64.getEncoder().encodeToString(kp.public.encoded)
        return Triple(pubB64, kp.private, pubB64)
    }

    private fun signEc(priv: java.security.PrivateKey, payload: ByteArray): String {
        val s = java.security.Signature.getInstance("SHA256withECDSA")
        s.initSign(priv)
        s.update(payload)
        return java.util.Base64.getEncoder().encodeToString(s.sign())
    }

    @Test
    fun `ROT v2 payload binds counter and timestamp`() {
        assertEquals(
            "ROTv2:A:B:7:123",
            String(WireProtocol.rotationPayloadV2("A", "B", 7L, 123L)),
        )
        assertEquals(24L * 60 * 60 * 1_000, WireProtocol.ROTATION_CERT_MAX_AGE_MS)
    }

    @Test
    fun `ROT v2 verify accepts fresh monotonic cert`() {
        val (prev, priv, _) = genP256Signer()
        val now = 1_700_000_000_000L
        val cert = signEc(priv, WireProtocol.rotationPayloadV2(prev, "NEW", 1L, now))
        assertTrue(WireProtocol.verifyRotationCert(prev, "NEW", cert, counter = 1L, tsMs = now, lastCounter = 0L, nowMs = now))
    }

    @Test
    fun `ROT v2 verify rejects replayed counter`() {
        val (prev, priv, _) = genP256Signer()
        val now = 1_700_000_000_000L
        val cert = signEc(priv, WireProtocol.rotationPayloadV2(prev, "NEW", 1L, now))
        // Same counter as already accepted → replay, even with a valid signature.
        assertFalse(WireProtocol.verifyRotationCert(prev, "NEW", cert, counter = 1L, tsMs = now, lastCounter = 1L, nowMs = now))
        assertFalse(WireProtocol.verifyRotationCert(prev, "NEW", cert, counter = 1L, tsMs = now, lastCounter = 5L, nowMs = now))
    }

    @Test
    fun `ROT v2 verify rejects stale timestamp`() {
        val (prev, priv, _) = genP256Signer()
        val now = 1_700_000_000_000L
        val stale = now - WireProtocol.ROTATION_CERT_MAX_AGE_MS - 1
        val cert = signEc(priv, WireProtocol.rotationPayloadV2(prev, "NEW", 2L, stale))
        assertFalse(WireProtocol.verifyRotationCert(prev, "NEW", cert, counter = 2L, tsMs = stale, lastCounter = 0L, nowMs = now))
        // Future-dated beyond the window is equally rejected (clock-skew tolerance is symmetric).
        val future = now + WireProtocol.ROTATION_CERT_MAX_AGE_MS + 1
        val certFuture = signEc(priv, WireProtocol.rotationPayloadV2(prev, "NEW", 2L, future))
        assertFalse(WireProtocol.verifyRotationCert(prev, "NEW", certFuture, counter = 2L, tsMs = future, lastCounter = 0L, nowMs = now))
    }

    @Test
    fun `ROT v2 verify rejects tampered keys and garbage`() {
        val (prev, priv, _) = genP256Signer()
        val now = 1_700_000_000_000L
        val cert = signEc(priv, WireProtocol.rotationPayloadV2(prev, "NEW", 1L, now))
        assertFalse(WireProtocol.verifyRotationCert(prev, "OTHER", cert, counter = 1L, tsMs = now, lastCounter = 0L, nowMs = now))
        assertFalse(WireProtocol.verifyRotationCert(prev, "NEW", "not-a-signature", counter = 1L, tsMs = now, lastCounter = 0L, nowMs = now))
        assertFalse(WireProtocol.verifyRotationCert(prev, "NEW", null, counter = 1L, tsMs = now, lastCounter = 0L, nowMs = now))
    }

    @Test
    fun `ROT v2 strict classification auto-rotates only with fresh monotonic cert`() {
        val (prev, priv, _) = genP256Signer()
        val now = 1_700_000_000_000L
        val cert = signEc(priv, WireProtocol.rotationPayloadV2(prev, "NEW", 1L, now))
        assertEquals(
            KeyTrustStatus.ROTATED_AUTO,
            WireProtocol.classifyKeyChangeStrict(prev, "NEW", isVerified = false, rotationCert = cert, counter = 1L, tsMs = now, lastCounter = 0L, nowMs = now),
        )
        // Verified safety numbers still need manual re-verify, even with a valid ROTv2 cert.
        assertEquals(
            KeyTrustStatus.CHANGED,
            WireProtocol.classifyKeyChangeStrict(prev, "NEW", isVerified = true, rotationCert = cert, counter = 1L, tsMs = now, lastCounter = 0L, nowMs = now),
        )
        // Replayed counter degrades to CHANGED.
        assertEquals(
            KeyTrustStatus.CHANGED,
            WireProtocol.classifyKeyChangeStrict(prev, "NEW", rotationCert = cert, counter = 1L, tsMs = now, lastCounter = 1L, nowMs = now),
        )
        // Legacy v1 overload untouched: no cert still means CHANGED.
        assertEquals(KeyTrustStatus.CHANGED, WireProtocol.classifyKeyChangeStrict(prev, "NEW"))
    }

    // ───────────────────────── ratchet-gap fresh handshake ─────────────────────────

    @Test
    fun `gap loss detected only for skipped-key window overflows`() {
        assertTrue(WireProtocol.isGapLoss("skipped-key limit exceeded: gap 401 beyond window 400 (nr=0 until=401)"))
        assertTrue(WireProtocol.isGapLoss("skipped-key limit exceeded: n=500 beyond window 400"))
        assertTrue(WireProtocol.isGapLoss("skipped-key window full: 400 stored + 5 new beyond 400"))
        assertFalse(WireProtocol.isGapLoss("message sealed to retired ratchet key"))
        assertFalse(WireProtocol.isGapLoss("AES-GCM authentication failed"))
        assertFalse(WireProtocol.isGapLoss(null))
        assertFalse(WireProtocol.isGapLoss(""))
    }

    @Test
    fun `gap refresh fires immediately then rate-limits to thirty seconds`() {
        val now = 2_000_000_000L
        assertTrue(WireProtocol.shouldRefreshOnGap(lastGapHandshakeAtMs = null, nowMs = now))
        assertFalse(WireProtocol.shouldRefreshOnGap(lastGapHandshakeAtMs = now - 1_000, nowMs = now))
        assertFalse(WireProtocol.shouldRefreshOnGap(lastGapHandshakeAtMs = now - WireProtocol.GAP_HANDSHAKE_COOLDOWN_MS, nowMs = now))
        assertTrue(WireProtocol.shouldRefreshOnGap(lastGapHandshakeAtMs = now - WireProtocol.GAP_HANDSHAKE_COOLDOWN_MS - 1, nowMs = now))
        assertEquals(30_000L, WireProtocol.GAP_HANDSHAKE_COOLDOWN_MS)
    }
}
