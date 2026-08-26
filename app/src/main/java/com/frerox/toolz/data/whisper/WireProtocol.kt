/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

/**
 * P4a: PURE wire-protocol decision core, extracted verbatim from
 * [WhisperRepository] so the negotiation / handshake-gating / key-change rules are
 * unit-testable on the JVM without Android or network dependencies.
 *
 * CONTRACT: every function here is side-effect-free and total (no throwing, no I/O).
 * The repository owns the mutable maps and session lookups and feeds plain values
 * in; the decisions come back out. Behavior was moved, not changed — the original
 * expressions are quoted in each KDoc so a future reader can diff against git.
 */
object WireProtocol {

    // ───────────────────────── protocol versions ─────────────────────────

    /** Version we must address a peer with. Never above what this build can speak.
     *  (Repository original: negotiatedVersionFor) */
    fun negotiatedVersion(
        ourVersion: Int,
        ourMaxSpeakable: Int,
        recordedPeerFloor: Int?,
    ): Int = maxOf(ourVersion, recordedPeerFloor ?: 0).coerceAtMost(ourMaxSpeakable)

    /**
     * Per-peer floor merge = LOWEST version that peer has ever sent us (they proved
     * they can't parse below it). Non-positive values are ignored entirely.
     * (Repository original: recordPeerProtocolFloor + map.merge)
     */
    fun mergePeerFloor(currentFloor: Int?, incomingVersion: Int): Int {
        if (incomingVersion <= 0) return currentFloor ?: 0
        val cur = currentFloor ?: return incomingVersion
        return minOf(cur, incomingVersion)
    }

    // ───────────────────────── v3 gating ─────────────────────────

    /** P7b: short suppression after a FAILED establish so per-send keystrokes never
     *  hammer the prekey-bundle endpoint. Delivery during suppression rides the
     *  proven envelope fallback — nothing is ever blocked. */
    const val DEFAULT_V3_RETRY_SUPPRESSION_MS = 30_000L

    /**
     * P7b RATCHET-FIRST send rule (user-approved policy):
     *  - ratchet disabled ⇒ false (legacy config escape hatch);
     *  - live session ⇒ true;
     *  - peer proved it can't parse v3 (floor below ratchet version) ⇒ false;
     *  - otherwise TRUE — fresh contacts ALWAYS attempt X3DH+ratchet first.
     *
     * The V5 envelope ladder stays intact as the unconditional fallback inside
     * sendMessage: ANY seal failure degrades to envelopes, so "a message can never
     * be blocked by session problems" is structurally preserved. What changed vs
     * the old envelope-first rule is only WHICH path new conversations try first
     * (forward secrecy by default) and that failures suppress retries briefly
     * instead of suppressing the protocol itself for a full minute.
     *
     * @param lastFailedOrRecentAttemptAtMs timestamp of the last establish ATTEMPT
     *   (successes move the peer into hasLiveSession=true territory anyway).
     */
    fun shouldUseV3(
        ratchetEnabled: Boolean,
        hasLiveSession: Boolean,
        recordedPeerFloor: Int?,
        ratchetProtocolVersion: Int,
        lastEstablishAttemptAtMs: Long?,
        nowMs: Long,
        retrySuppressionMs: Long = DEFAULT_V3_RETRY_SUPPRESSION_MS,
    ): Boolean {
        if (!ratchetEnabled) return false
        if (hasLiveSession) return true
        val floor = recordedPeerFloor
        if (floor != null && floor < ratchetProtocolVersion) return false
        val last = lastEstablishAttemptAtMs ?: return true
        return nowMs - last > retrySuppressionMs
    }

    // ───────────────────────── key-change classification ─────────────────────────

    /**
     * P0-1 classification, made pure. Ages are pre-computed by the caller
     * (Long.MAX_VALUE = unknown/unparseable).
     *
     * Classification:
     *  - known == current (or no prior key / blank current) → MATCH
     *  - server row updated within FRESH_ROTATION_WINDOW   → ROTATED_AUTO
     *  - known key age ≥ interval − 24h                    → ROTATED_AUTO (aged out)
     *  - anything else                                     → CHANGED (warn)
     *
     * (Repository original: classifyKeyChange)
     */
    fun classifyKeyChange(
        knownKey: String?,
        currentKey: String?,
        serverRowUpdateAgeMs: Long,
        knownKeyAgeMs: Long,
        freshRotationWindowMs: Long,
        rotationIntervalMs: Long,
    ): KeyTrustStatus {
        if (knownKey == null || knownKey == currentKey || currentKey.isNullOrBlank()) {
            return KeyTrustStatus.MATCH
        }
        if (serverRowUpdateAgeMs in 0..freshRotationWindowMs) {
            return KeyTrustStatus.ROTATED_AUTO
        }
        val expectedWindow = rotationIntervalMs - 24L * 60 * 60 * 1000
        if (knownKeyAgeMs >= expectedWindow) {
            return KeyTrustStatus.ROTATED_AUTO
        }
        return KeyTrustStatus.CHANGED
    }

    /**
     * True when a partner key mismatch is a routine fresh rotation and messaging may
     * continue without manual review. (Repository original: isFreshServerRotation)
     */
    fun isFreshServerRotation(serverRowUpdateAgeMs: Long?, freshRotationWindowMs: Long): Boolean {
        val ageMs = serverRowUpdateAgeMs ?: return false
        return ageMs in 0..freshRotationWindowMs
    }

    // ───────────────────────── session self-heal freshness ─────────────────────────

    /**
     * V6 self-heal gate: decide whether a row timestamp proves the PEER lost their
     * session (fresh non-v3 text ⇒ drop ours). Rows predating session creation or
     * future-dated beyond skew tolerance never tear down state.
     *
     * @return true when the caller SHOULD delete the stored session.
     * (Repository original: maybeTeardownStaleSession's two guard lines)
     */
    fun shouldTeardownStaleSession(
        rowEpochMs: Long,
        sessionCreatedAtMs: Long,
        nowMs: Long,
        clockSkewSlackMs: Long,
    ): Boolean {
        if (rowEpochMs + clockSkewSlackMs < sessionCreatedAtMs) return false // pre-session row
        if (rowEpochMs - clockSkewSlackMs > nowMs) return false              // future-dated row
        return true
    }
}
