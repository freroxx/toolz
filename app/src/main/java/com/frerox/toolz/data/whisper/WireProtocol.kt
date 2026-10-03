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

    // ───────────────────────── residual-downgrade N-block ─────────────────────────

    /**
     * Residual-downgrade block: after [DOWNGRADE_BLOCK_AFTER_N_FAILS] consecutive
     * X3DH establish failures with no live/proven session, the envelope fallback
     * must NOT silently drop to static-ECDH (no forward secrecy) under persistent
     * failure — the send blocks with a retry-later warning instead. Pure rule so
     * the repository's send path stays unit-testable.
     */
    const val DOWNGRADE_BLOCK_AFTER_N_FAILS = 3

    fun shouldBlockDowngradeAfterFails(
        hasLiveSession: Boolean,
        sessionProven: Boolean,
        failedEstablishCount: Int,
        threshold: Int = DOWNGRADE_BLOCK_AFTER_N_FAILS,
    ): Boolean = !hasLiveSession && !sessionProven && failedEstablishCount >= threshold

    // ───────────────────────── key-change classification ─────────────────────────

    /**
     * Phase-1A FAIL-CLOSED classification.
     *
     * MATCH iff known == current (or no prior pin / blank current). ANY other
     * difference is CHANGED — there is deliberately NO time-based auto-accept:
     * freshness of the server row and age of the pinned key prove nothing about
     * who controls the new key (a MITM rotates "fresh" too).
     *
     * Automatic trust (ROTATED_AUTO) now requires a cryptographic rotation
     * certificate — see [classifyKeyChangeStrict]. This legacy overload is kept
     * for binary/source compat only; window params are ignored.
     *
     * (Repository original: classifyKeyChange — both auto-accept paths removed.)
     */
    @Deprecated(
        "Fail-closed: time windows no longer auto-accept. Use classifyKeyChangeStrict with a rotation cert.",
        ReplaceWith("classifyKeyChangeStrict(knownKey, currentKey)"),
    )
    fun classifyKeyChange(
        knownKey: String?,
        currentKey: String?,
        serverRowUpdateAgeMs: Long,
        knownKeyAgeMs: Long,
        freshRotationWindowMs: Long,
        rotationIntervalMs: Long,
    ): KeyTrustStatus {
        @Suppress("DEPRECATION")
        return classifyKeyChangeStrict(knownKey = knownKey, currentKey = currentKey)
    }

    /**
     * Strict key-change decision.
     *
     *  - known == current (or no prior key / blank current) → MATCH
     *  - verified peer whose key differs (even with a valid cert) → CHANGED:
     *    QR-verified safety numbers never auto-rotate; the user must re-verify
     *    in person (manual re-verify required).
     *  - valid rotation cert chaining newKey to knownKey → ROTATED_AUTO
     *  - anything else → CHANGED (warn + block send until review).
     *
     * @param isVerified true when the pinned key was QR-verified in person.
     * @param rotationCert optional base64 ECDSA-P256 signature over
     *   [rotationPayload](knownKey, currentKey), verified with knownKey as the
     *   signer (old-key-signs-new). Null/blank = no proof → CHANGED.
     */
    fun classifyKeyChangeStrict(
        knownKey: String?,
        currentKey: String?,
        isVerified: Boolean = false,
        rotationCert: String? = null,
    ): KeyTrustStatus {
        if (knownKey == null || knownKey == currentKey || currentKey.isNullOrBlank()) {
            return KeyTrustStatus.MATCH
        }
        if (isVerified) return KeyTrustStatus.CHANGED
        if (!rotationCert.isNullOrBlank() &&
            verifyRotationCert(knownKey, currentKey, rotationCert)
        ) {
            return KeyTrustStatus.ROTATED_AUTO
        }
        return KeyTrustStatus.CHANGED
    }

    /**
     * ROTv2 strict key-change decision with rotation anti-replay.
     *
     * Same fail-closed shape as [classifyKeyChangeStrict], but the rotation cert
     * must be a ROTv2 cert (see [rotationPayloadV2]) whose counter is strictly
     * greater than [lastCounter] and whose timestamp is within
     * [ROTATION_CERT_MAX_AGE_MS] of [nowMs]. Verified peers still always need
     * manual re-verify. The legacy 4-arg overload above is kept for compat with
     * v1 certs (no replay binding).
     */
    fun classifyKeyChangeStrict(
        knownKey: String?,
        currentKey: String?,
        isVerified: Boolean = false,
        rotationCert: String? = null,
        counter: Long,
        tsMs: Long,
        lastCounter: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): KeyTrustStatus {
        if (knownKey == null || knownKey == currentKey || currentKey.isNullOrBlank()) {
            return KeyTrustStatus.MATCH
        }
        if (isVerified) return KeyTrustStatus.CHANGED
        if (!rotationCert.isNullOrBlank() &&
            verifyRotationCert(knownKey, currentKey, rotationCert, counter, tsMs, lastCounter, nowMs = nowMs)
        ) {
            return KeyTrustStatus.ROTATED_AUTO
        }
        return KeyTrustStatus.CHANGED
    }

    /**
     * Canonical rotation-certificate payload: `"WHISPER-ROTATE-v1:<prev>:<new>"`.
     * Single source of truth — signers and verifiers must build byte-identical
     * material through this function. Kept for v1 certs; new certs use ROTv2
     * (see [rotationPayloadV2]) with counter + timestamp anti-replay binding.
     */
    fun rotationPayload(prevKeyB64: String, newKeyB64: String): ByteArray =
        "WHISPER-ROTATE-v1:$prevKeyB64:$newKeyB64".toByteArray(Charsets.UTF_8)

    /**
     * ROTv2 rotation-certificate payload with anti-replay binding:
     * `"ROTv2:<prev>:<new>:<counter>:<ts>"`. The monotonic [counter] (strictly
     * greater than the peer's last accepted counter) defeats cert replay; [tsMs]
     * bounds cert age to [ROTATION_CERT_MAX_AGE_MS]. Single source of truth —
     * signers ([WhisperCrypto.signRotationCert]) and verifiers
     * ([verifyRotationCert]) must build byte-identical material here.
     */
    fun rotationPayloadV2(
        prevKeyB64: String,
        newKeyB64: String,
        counter: Long,
        tsMs: Long,
    ): ByteArray =
        "ROTv2:$prevKeyB64:$newKeyB64:$counter:$tsMs".toByteArray(Charsets.UTF_8)

    /** Maximum age of a ROTv2 rotation cert (24h clock-skew-tolerant window). */
    const val ROTATION_CERT_MAX_AGE_MS: Long = 24L * 60 * 60 * 1_000

    /**
     * One peer's published rotation-certificate transport record, as
     * read from their profile row (`rotation_cert` + `rotation_counter`).
     * All fields are public material.
     */
    data class RotationCertRecord(
        val certB64: String,
        val counter: Long,
    )

    /**
     * Fail-closed decoder for a peer's published rotation record:
     * a blank cert or a non-positive counter decodes to `null`
     * ("no usable cert published") instead of a trusted-but-unproven
     * rotation. Pure and total — never throws.
     */
    fun rotationCertRecordOf(certB64: String?, counter: Long?): RotationCertRecord? =
        if (certB64.isNullOrBlank() || counter == null || counter <= 0L) {
            null
        } else {
            RotationCertRecord(certB64.trim(), counter)
        }

    /**
     * Verifies `cert = sign(prevKey, newKey)` — an ECDSA/SHA256 signature over
     * [rotationPayload] verified with the PREVIOUS key as the P-256 signer
     * (old-key-signs-new chaining). Pure and total: any malformed input is
     * simply false, never throws.
     *
     * @param signerPubB64 defaults to [prevKeyB64] (standard chaining). A
     *   separate protocol-signer pub may be passed when certs migrate to the
     *   `whisper_protocol_sign_key` identity.
     */
    fun verifyRotationCert(
        prevKeyB64: String,
        newKeyB64: String,
        certB64: String?,
        signerPubB64: String = prevKeyB64,
    ): Boolean {
        if (certB64.isNullOrBlank() || prevKeyB64.isBlank() || newKeyB64.isBlank()) return false
        return try {
            val keyBytes = java.util.Base64.getDecoder().decode(signerPubB64.trim())
            val pub = java.security.KeyFactory.getInstance("EC")
                .generatePublic(java.security.spec.X509EncodedKeySpec(keyBytes))
            val sig = java.security.Signature.getInstance("SHA256withECDSA")
            sig.initVerify(pub)
            sig.update(rotationPayload(prevKeyB64, newKeyB64))
            sig.verify(java.util.Base64.getDecoder().decode(certB64.trim()))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * ROTv2 rotation-cert verification with anti-replay: requires
     * `counter > lastCounter` (monotonic per peer, stored in
     * [WhisperKeyTrustStore.rotationCounter]) and `|now - ts| <=
     * [ROTATION_CERT_MAX_AGE_MS]` (24h), then ECDSA/SHA256-verifies the
     * [rotationPayloadV2] signature with the previous key. Pure and total: any
     * malformed input or replayed/stale cert is simply false, never throws.
     * The legacy 4-arg overload above is kept for v1 cert compat.
     */
    fun verifyRotationCert(
        prevKeyB64: String,
        newKeyB64: String,
        certB64: String?,
        counter: Long,
        tsMs: Long,
        lastCounter: Long,
        signerPubB64: String = prevKeyB64,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (certB64.isNullOrBlank() || prevKeyB64.isBlank() || newKeyB64.isBlank()) return false
        if (counter <= lastCounter) return false
        if (kotlin.math.abs(nowMs - tsMs) > ROTATION_CERT_MAX_AGE_MS) return false
        return try {
            val keyBytes = java.util.Base64.getDecoder().decode(signerPubB64.trim())
            val pub = java.security.KeyFactory.getInstance("EC")
                .generatePublic(java.security.spec.X509EncodedKeySpec(keyBytes))
            val sig = java.security.Signature.getInstance("SHA256withECDSA")
            sig.initVerify(pub)
            sig.update(rotationPayloadV2(prevKeyB64, newKeyB64, counter, tsMs))
            sig.verify(java.util.Base64.getDecoder().decode(certB64.trim()))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fail-closed recipient filter: only the pinned key, plus candidates that
     * chain to it via a valid rotation cert, may receive ciphertext.
     *
     *  - pinned == null (first contact, TOFU) → candidates unchanged.
     *  - otherwise → keep exactly: candidate == pinned, or
     *    verifyRotationCert(pinned, candidate, certs[candidate]) == true.
     * A server "fresh" key without a cert chaining to the pin is DROPPED here;
     * callers must then block the send and surface the key-change warning
     * instead of fan-out sealing to an unproven key.
     */
    fun filterKeysChainedToPinned(
        pinnedKeyB64: String?,
        candidates: Map<String, String>,
        certs: Map<String, String?> = emptyMap(),
    ): Map<String, String> {
        if (pinnedKeyB64.isNullOrBlank()) return candidates
        return candidates.filterValues { pub ->
            pub == pinnedKeyB64 ||
                verifyRotationCert(pinnedKeyB64, pub, certs[pub])
        }
    }

    /**
     * True when a partner key mismatch is a routine fresh rotation and messaging may
     * continue without manual review. (Repository original: isFreshServerRotation)
     *
     * Phase-1A: DEPRECATED — time-window auto-accept is removed. Kept for compat;
     * new code must use [classifyKeyChangeStrict] + [verifyRotationCert].
     */
    @Deprecated("Fail-closed: use classifyKeyChangeStrict with a rotation cert.")
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

    // ───────────────────────── ratchet-gap fresh handshake ─────────────────────────

    /** Cooldown between gap-triggered fresh handshakes per peer (fire-and-forget, rate-limited). */
    const val GAP_HANDSHAKE_COOLDOWN_MS = 30_000L

    /**
     * True when a [WhisperRatchetLostMessage] was caused by a skipped-key gap
     * overflow (> MAX_SKIPPED) rather than ordinary bounded loss (retired-chain
     * straggler, bad AEAD). Only gap overflow triggers a fresh handshake — the
     * session can never catch up, while other losses may still resolve via
     * server catch-up. Pure and total: null/foreign messages are false.
     */
    fun isGapLoss(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return message.contains("skipped-key limit") ||
            message.contains("beyond window") ||
            message.contains("window full")
    }

    /**
     * Rate-limit gate for gap-triggered handshakes: first gap always refreshes,
     * later ones only after [cooldownMs]. Pure rule so the repository's
     * fire-and-forget helper stays unit-testable.
     */
    fun shouldRefreshOnGap(
        lastGapHandshakeAtMs: Long?,
        nowMs: Long,
        cooldownMs: Long = GAP_HANDSHAKE_COOLDOWN_MS,
    ): Boolean {
        val last = lastGapHandshakeAtMs ?: return true
        return nowMs - last > cooldownMs
    }
}
