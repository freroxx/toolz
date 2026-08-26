/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperCrypto @Inject constructor(
    @ApplicationContext private val context: Context,
    // P1 (startup ANR fix): the keystore health checks (canary retries with sleeps +
    // identity-change detector) must never run on the thread that first injects this
    // singleton (historically Main). They now run on the app scope; the alias
    // bootstrap itself stays synchronous so first-use callers are unaffected.
    @com.frerox.toolz.di.ApplicationScope private val healthCheckScope: kotlinx.coroutines.CoroutineScope,
) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        /** Alias used by builds before staged rotation existed — adopted as active on first run. */
        private const val LEGACY_KEY_ALIAS = "whisper_e2ee_ec_key"
        private const val TAG_CRYPTO = "WhisperCrypto"
        // PHASE 1 (roadmap §1.1): hardware-bound P-256 SIGNING identity for the upcoming
        // prekey protocol — signs X25519 prekeys so a malicious server cannot substitute
        // them. SEPARATE alias from the ECDH agreement key: one key, one purpose.
        private const val PROTOCOL_SIGN_ALIAS = "whisper_protocol_sign_key"
        private const val STAGED_ALIAS_PREFIX = "whisper_e2ee_ec_key_staged_"
        private const val STATE_PREFS = "whisper_crypto_state"
        private const val PREF_ACTIVE_ALIAS = "active_alias"
        /** HOTFIX: committed rotations recorded so [hasStagedAliases] stays truthful
         *  (committed keys keep the staged prefix forever — see its KDoc). */
        private const val PREF_COMMITTED_STAGED_ALIASES = "committed_staged_aliases"
        // V6-R6: last-seen active public key (b64) for the unexpected-change detector.
        private const val PREF_LAST_PUB = "last_seen_pub_b64"
        private const val AES_GCM_TAG_LEN = 128
        private const val IV_LEN = 12
        private const val MAX_MESSAGE_CHARS = 8_192
        // ImgBB accepts 32 MB base64 input; ciphertext and base64 expansion require headroom.
        // AES-GCM appends a 16-byte tag, so plaintext must stay below the transport cap.
        private const val MAX_ATTACHMENT_BYTES = WhisperImageCipherTransport.MAX_CIPHER_BYTES - (AES_GCM_TAG_LEN / 8)
        private val ATTACHMENT_AAD = "whisper-attachment-v1".toByteArray(Charsets.UTF_8)
        private val MESSAGE_AAD = "whisper-message-v1".toByteArray(Charsets.UTF_8)

        /**
         * H-3 FIX (reviewwhisper.md): single switch controlling the pre-AAD decryption
         * fallbacks. Legacy rows created before direction-bound AAD existed can only be
         * decrypted without AAD (messages) or with constant-only AAD (attachments) — both
         * fallbacks weaken replay protection, so they are gated here.
         *
         * Keep this TRUE only until every device has had a full migration window
         * (cached ciphertext is re-fetched/decrypted once into the Room cache), then flip
         * to FALSE in a dedicated release so cross-chat replay protection has no silent
         * escape hatch anymore. New ciphertext is ALWAYS written with bound AAD.
         *
         * V3-FIX (scoped legacy-AAD retirement): this flag no longer applies globally —
         * it only gates the fallback for rows created BEFORE
         * [LEGACY_AAD_CUTOFF_EPOCH_MS] (see [legacyFallbackAllowed]). Post-cutoff
         * ciphertext never falls back regardless of this flag's value.
         */
        internal var LEGACY_AAD_FALLBACK_ENABLED: Boolean = true

        /**
         * V3-FIX (scoped legacy-AAD retirement): epoch millis of 2026-09-01T00:00:00Z.
         * Ciphertext CREATED strictly before this instant may still take the legacy
         * fallback paths (no-AAD messages / constant-AAD attachments); anything created
         * at or after the cutoff must authenticate through direction-bound AAD only.
         * Rows whose timestamp cannot be parsed are mapped to 0L by callers
         * (see [WhisperMessageEntity.parseSortEpoch]) — undated = treated as legacy.
         */
        const val LEGACY_AAD_CUTOFF_EPOCH_MS: Long = 1_788_220_800_000L

        /**
         * V3-FIX: pure, unit-test-friendly eligibility rule for the legacy fallback
         * branches. Lives beside the cutoff constant so the policy can never drift
         * from the constant itself.
         */
        fun legacyFallbackAllowed(createdAtEpochMs: Long): Boolean =
            createdAtEpochMs < LEGACY_AAD_CUTOFF_EPOCH_MS

        /**
         * SHA-256 fingerprint of a base64 public key, rendered as 8 groups of 4
         * uppercase hex chars ("AAAA-BBBB-…"). Single source of truth shared by
         * [fingerprint] and the profile screen so the two can never drift.
         *
         * Uses java.util.Base64 (minSdk 31) instead of android.util.Base64 so this
         * security-critical contract is testable in plain JVM unit tests (H-11).
         */
        fun computeFingerprint(base64PublicKey: String?): String? {
            if (base64PublicKey.isNullOrBlank()) return null
            return try {
                // Strict RFC 4648 decoder — malformed keys must yield null (never a
                // fingerprint of silently-stripped garbage), matching the previous
                // android.util.Base64.DEFAULT rejection semantics.
                val rawBytes = java.util.Base64.getDecoder().decode(base64PublicKey.trim())
                val digest = MessageDigest.getInstance("SHA-256").digest(rawBytes)
                digest.joinToString("") { "%02X".format(it) }.chunked(4).take(8).joinToString("-")
            } catch (_: Exception) { null }
        }

        /**
         * P1: single source for the SPK signature payload ("SPK:<kid><pubB64>") so the
         * signer ([WhisperPrekeyManager]) and every verifier ([WhisperSessionFactory],
         * [WhisperPrekeyManager]) construct byte-identical material by construction.
         */
        fun spkSignedPayload(spkKid: String, spkPublicKeyBase64: String): ByteArray =
            "SPK:$spkKid$spkPublicKeyBase64".toByteArray()

        /**
         * P7a RETIREMENT GATE for [LEGACY_AAD_FALLBACK_ENABLED]. The flag may ONLY be
         * flipped to false in a dedicated release once BOTH hold:
         *  1. `legacyFallbackRetireAllowed()` == true (cutoff + 90-day grace window,
         *     i.e. no NEW rows can ever be legacy anymore and caches have aged), and
         *  2. the `crypto.legacyAadFallback.*` diagnostics counters read ≈ 0 across
         *     the fleet (no live user still depends on the pre-AAD path — flipping
         *     earlier would permanently lock those rows).
         * Until then the flag stays TRUE; post-cutoff ciphertext never takes the
         * fallback regardless of the flag (see [legacyFallbackAllowed]).
         */
        const val LEGACY_FALLBACK_RETIRE_EARLIEST_EPOCH_MS: Long = 1_796_083_200_000L // 2026-12-01T00:00:00Z

        fun legacyFallbackRetireAllowed(nowMs: Long = System.currentTimeMillis()): Boolean =
            nowMs >= LEGACY_FALLBACK_RETIRE_EARLIEST_EPOCH_MS
    }

    /** A newly generated key pair waiting for the server publish to confirm it. */
    data class StagedKeyPair(val alias: String, val publicKeyBase64: String)

    private val rotationLock = Any()

    init {
        ensureKeyPairExists()
        // P1: health checks off the critical path — see constructor note.
        healthCheckScope.launch { runStartupHealthChecks() }
    }

    /**
     * Resolves (creating or adopting if needed) the ACTIVE key alias.
     * Rotation switches this pointer only AFTER the new public key is published,
     * so a failed publish can be rolled back by deleting the staged alias — the
     * old private key is never destroyed before the server acknowledges its replacement.
     */
    /**
     * V4-FIX: true while a staged rotation exists but has not been committed/aborted.
     * Used by the repository to distinguish an interrupted rotation from a reinstall
     * so the reinstall key-republish never fights the rotation protocol.
     *
     * HOTFIX (field bug "both sides only see their own messages"): this check used to
     * match ANY alias under the staged prefix — but a COMMITTED rotation keeps its
     * staged-prefixed alias as the active identity forever. Result: after the very
     * first successful rotation, [hasStagedAliases] returned true on every launch,
     * which permanently disabled `republishLocalKeyIfStale()` and the
     * getMyProfile divergence heal. The server then kept advertising an OLD public
     * key while the device held a newer private one; every contact sealed to keys
     * this device could not open ("envelope locked", keyDrift.sealedToUnknownOwnKid),
     * and symmetrically for the peer — each side could only read its own sends.
     *
     * Correct semantics: a staged alias counts as IN-FLIGHT only when it is neither
     * (a) the active-alias pointer nor (b) recorded in the committed set. Both checks
     * are cheap and crash-safe:
     *  - commit writes the pointer BEFORE recording, so any crash leaves either
     *    "pointer==staged" (rotation done) or "pointer old" (genuinely in flight);
     *  - startup sweep removes non-active orphans, so a stale uncommitted entry
     *    cannot wedge this flag across reboots.
     */
    fun hasStagedAliases(): Boolean = runCatching {
        val prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        val active = prefs.getString(PREF_ACTIVE_ALIAS, null)
        val committed = prefs.getStringSet(PREF_COMMITTED_STAGED_ALIASES, emptySet()).orEmpty()
        val keyStore = java.security.KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        keyStore.aliases().toList().any {
            it.startsWith(STAGED_ALIAS_PREFIX) && it != active && it !in committed
        }
    }.getOrDefault(false)

    private fun activeAlias(): String {
        synchronized(rotationLock) {
            val prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            prefs.getString(PREF_ACTIVE_ALIAS, null)?.let { return it }
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val alias = if (keyStore.containsAlias(LEGACY_KEY_ALIAS)) {
                LEGACY_KEY_ALIAS
            } else {
                val created = genStagedAlias()
                createKeyPairUnder(created)
                created
            }
            prefs.edit().putString(PREF_ACTIVE_ALIAS, alias).apply()
            return alias
        }
    }

    // V2-FIX W6: millisecond timestamps alone can collide (rapid rotations / clock
    // granularity); append 4 bytes of SecureRandom entropy so staged aliases stay unique.
    private fun genStagedAlias(): String {
        val entropy = ByteArray(4).also { SecureRandom().nextBytes(it) }
        return STAGED_ALIAS_PREFIX + System.currentTimeMillis() + "_" +
            entropy.joinToString("") { "%02x".format(it) }
    }

    private fun createKeyPairUnder(alias: String) {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER)
        val parameterSpec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_AGREE_KEY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .build()
        kpg.initialize(parameterSpec)
        kpg.generateKeyPair()
    }

    private fun activeAliasPrefSet(): Boolean = runCatching {
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            .getString(PREF_ACTIVE_ALIAS, null) != null
    }.getOrDefault(false)

    private fun selfTestRoundTrip(): Boolean = runCatching {
        val pub = getPublicKeyBase64() ?: return false
        val myId = "selftest"
        val (ct, iv) = encryptMessage("ping", pub, myId, myId) ?: return false
        decryptMessage(ct, iv, pub, myId, myId) == "ping"
    }.getOrDefault(false)

    // ---------------- PHASE 1: keystore-wrapped secret storage (roadmap §1.1) ----

    private val WRAP_ALIAS = "whisper_protocol_wrap_key"

    /** Encrypts arbitrary bytes under a dedicated Keystore AES-GCM key; returns b64(iv‖ct). */
    fun wrapWithKeystoreAes(plain: ByteArray): String? = runCatching {
        val keyStore = java.security.KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (!keyStore.containsAlias(WRAP_ALIAS)) {
            val generator = javax.crypto.KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER,
            )
            generator.init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    WRAP_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generator.generateKey()
        }
        val secret = keyStore.getKey(WRAP_ALIAS, null) as javax.crypto.SecretKey
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secret, javax.crypto.spec.GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain)
        Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }.getOrNull()

    /** Inverse of [wrapWithKeystoreAes]; null on tamper or missing key. */
    fun unwrapWithKeystoreAes(wrappedB64: String): ByteArray? = runCatching {
        val keyStore = java.security.KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val secret = keyStore.getKey(WRAP_ALIAS, null) as javax.crypto.SecretKey
        val packed = Base64.decode(wrappedB64, Base64.NO_WRAP)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secret, javax.crypto.spec.GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        cipher.doFinal(packed.copyOfRange(12, packed.size))
    }.getOrNull()

    /**
     * V4-FIX follow-up: the stage->publish->commit rotation lifecycle never survives
     * process death, so any staged alias found at startup is orphaned garbage from an
     * interrupted attempt. Sweeping it keeps [hasStagedAliases] truthful — otherwise a
     * single crash mid-rotation permanently disables the reinstall key-republish.
     */
    fun sweepOrphanedStagedAliases() {
        runCatching {
            val keyStore = java.security.KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            // V6-R6 CRITICAL FIX: the ACTIVE alias is frequently itself a staged alias
            // (both activeAlias() fallback-generation and rotation commits live under
            // the staged prefix). The old unconditional sweep DELETED THE ACTIVE KEY on
            // every process start — identity churn, unreadable history, the works.
            // The active alias is now explicitly protected; only truly orphaned
            // staged entries (interrupted rotations never committed) are removed.
            val prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            val protectedActive = prefs.getString(PREF_ACTIVE_ALIAS, null)
            val orphans = keyStore.aliases().toList().filter {
                it.startsWith(STAGED_ALIAS_PREFIX) && it != protectedActive
            }
            orphans.forEach {
                keyStore.deleteEntry(it)
                android.util.Log.w(TAG_CRYPTO, "Swept orphaned staged key alias: $it")
            }
            if (protectedActive != null && orphans.isNotEmpty()) {
                ProtocolDiagnostics.log("keypair.sweep: removed ${orphans.size} orphan(s), active alias protected")
            }
        }
    }

    private fun ensureKeyPairExists() {
        sweepOrphanedStagedAliases()
        ensureSigningKeyExists()
        try {
            // V6-R6 REPAIR: older builds' sweeps may have already deleted the active
            // alias, leaving PREF_ACTIVE_ALIAS dangling (private key null forever).
            // Clear the dangling pointer so activeAlias() falls back to the legacy key
            // or generates a fresh — now sweep-protected — identity.
            runCatching {
                val prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                val prefAlias = prefs.getString(PREF_ACTIVE_ALIAS, null)
                if (prefAlias != null) {
                    val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                    if (!ks.containsAlias(prefAlias)) {
                        ProtocolDiagnostics.increment("keypair.danglingActivePref")
                        android.util.Log.e(TAG_CRYPTO, "Active alias $prefAlias missing from keystore — resetting pointer")
                        prefs.edit().remove(PREF_ACTIVE_ALIAS).apply()
                    }
                }
            }
            // P1: the canary retry loop (Thread.sleep backoff) and the identity-change
            // detector moved to [runStartupHealthChecks] on the app scope — they must
            // never stall first injection. Alias bootstrap stays here so the very
            // first getPublicKeyBase64()/encrypt caller still resolves synchronously.
            activeAlias()
        } catch (e: Exception) {
            // V2-FIX W8: never swallow keystore bootstrap failures silently — degraded
            // keystore state (E2EE unavailable until fixed) must be diagnosable from logs.
            android.util.Log.w("WhisperCrypto", "Key pair generation failed; E2EE degraded until keystore recovers", e)
        }
    }

    /**
     * P1 (startup ANR fix): keystore health checks that used to run inside
     * [ensureKeyPairExists] on the injecting thread. Invoked once per process from
     * init on the app scope; also safe to call manually (debug diagnostics).
     *
     *  - Canary: prove the active key can actually round-trip before chat relies on
     *    it (V5); OEM keystore corruption otherwise surfaced only weeks later as the
     *    "[Encrypted message]" bug.
     *  - V6-R6 CRITICAL FIX preserved verbatim: a canary failure NEVER regenerates
     *    the identity — failures are retried, then surfaced through diagnostics.
     *  - V6-R6 detector: any change of the ACTIVE public key between process starts
     *    is logged loudly (identity churn forensics in diagnostics exports).
     */
    fun runStartupHealthChecks() {
        // P7a: surface retirement readiness in diagnostics exports — when this fires
        // (and counters are ~0), the next release may flip LEGACY_AAD_FALLBACK_ENABLED.
        if (LEGACY_AAD_FALLBACK_ENABLED && legacyFallbackRetireAllowed()) {
            ProtocolDiagnostics.log("crypto: legacy-AAD fallback retire window OPEN — evaluate counters, then flip")
        }

        // V5 canary — see notes above for the no-implicit-reset contract.
        if (activeAliasPrefSet()) {
            var ok = false
            for (attempt in 1..3) {
                if (selfTestRoundTrip()) { ok = true; break }
                android.util.Log.e(TAG_CRYPTO, "Keystore self-test FAILED (attempt $attempt/3)")
                ProtocolDiagnostics.increment("keypair.canaryFailed")
                Thread.sleep(150L * attempt)
            }
            if (!ok) {
                // Degraded-but-stable: keep the identity. Never implicit reset.
                android.util.Log.e(TAG_CRYPTO, "Keystore self-test failed 3× — keeping existing identity (NO implicit reset)")
                ProtocolDiagnostics.increment("keypair.degradedNoReset")
            }
        }

        // V6-R6: unexpected-identity-change detector. The canary no longer resets
        // implicitly (see above), so any change of the ACTIVE public key between
        // process starts is now noteworthy by definition — log it loudly so the
        // diagnostics export shows exactly when an identity churned.
        runCatching {
            val prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            val current = getPublicKeyBase64()
            val lastSeen = prefs.getString(PREF_LAST_PUB, null)
            if (current != null) {
                if (lastSeen != null && lastSeen != current) {
                    ProtocolDiagnostics.increment("keypair.changedUnexpectedly")
                    android.util.Log.e(
                        TAG_CRYPTO,
                        "Identity public key CHANGED since last start without explicit rotation!",
                    )
                }
                prefs.edit().putString(PREF_LAST_PUB, current).apply()
            }
        }
    }

    fun getPublicKeyBase64(): String? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val entry = keyStore.getEntry(activeAlias(), null) as? KeyStore.PrivateKeyEntry
            val publicKey = entry?.certificate?.publicKey ?: return null
            Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }

    /** SHA-256 fingerprint of a base64 public key, rendered as 8 groups of 4 uppercase hex chars ("AAAA-BBBB-…"). */
    fun fingerprint(base64PublicKey: String?): String? = computeFingerprint(base64PublicKey)

    private fun getPrivateKey(): PrivateKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val entry = keyStore.getEntry(activeAlias(), null) as? KeyStore.PrivateKeyEntry
            entry?.privateKey
        } catch (_: Exception) { null }
    }

    private fun parsePublicKey(base64PublicKey: String): PublicKey? {
        return try {
            val cleanKey = base64PublicKey.trim()
            // V2-FIX W5: strict RFC 4648 decoding via java.util.Base64 so malformed peer
            // keys fail closed instead of being leniently stripped (android.util.Base64
            // .DEFAULT skips invalid chars). Matches computeFingerprint semantics; server
            // keys are standard base64 so no caller regression. Decode errors are wrapped
            // by the surrounding try/catch and logged.
            val bytes = java.util.Base64.getDecoder().decode(cleanKey)
            val keySpec = X509EncodedKeySpec(bytes)
            val keyFactory = KeyFactory.getInstance("EC")
            keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            ProtocolDiagnostics.increment("crypto.parseKeyFail")
            android.util.Log.e("WhisperCrypto", "parsePublicKey failed: ${e.message}")
            null
        }
    }

    /**
     * Derives a symmetric AES-GCM session key using static ECDH (secp256r1) + HKDF-Extract/Expand.
     *
     * SECURITY NOTE (No Forward Secrecy):
     * Whisper v1.x uses long-term ECDH key agreement without an ephemeral Double Ratchet.
     * A compromise of the local Keystore private key allows decrypting past messages.
     * To limit exposure, users can rotate their key pair via [rotateKeyPair].
     */
    private fun deriveSharedKey(recipientPublicKeyBase64: String): SecretKeySpec? {
        val privateKey = getPrivateKey() ?: return null
        val recipientPubKey = parsePublicKey(recipientPublicKeyBase64) ?: return null
        return try {
            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(recipientPubKey, true)
            val sharedSecret = keyAgreement.generateSecret()

            // HKDF-Extract: PRK = HMAC-SHA256(salt, IKM=sharedSecret).
            // DEVIATION (documented, stable): we use a zero-filled 32-byte salt rather than
            // RFC 5869's empty salt. Both are deterministic; the IKM here is a uniformly
            // random ECDH secret, so extract quality is unaffected. This must NOT change
            // without a dual-derive migration — every stored message was derived with it.
            val mac = Mac.getInstance("HmacSHA256")
            val zeroSalt = ByteArray(32)
            mac.init(SecretKeySpec(zeroSalt, "HmacSHA256"))
            val prk = mac.doFinal(sharedSecret)

            // HKDF-Expand: OKM = T(1) where T(1) = HMAC-SHA256(PRK, info + 0x01)
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val info = "whisper-e2ee-v1".toByteArray(Charsets.UTF_8)
            val infoWithOne = ByteArray(info.size + 1)
            System.arraycopy(info, 0, infoWithOne, 0, info.size)
            infoWithOne[info.size] = 0x01.toByte()
            val okm = mac.doFinal(infoWithOne) // 32 bytes for AES-256

            SecretKeySpec(okm, "AES")
        } catch (e: Exception) {
            android.util.Log.e("WhisperCrypto", "Error deriving shared key: ${e.message}")
            null
        }
    }

    private fun aadFor(senderId: String, receiverId: String): ByteArray {
        val s = senderId.encodeToByteArray()
        val r = receiverId.encodeToByteArray()
        val sLen = byteArrayOf((s.size shr 24).toByte(), (s.size shr 16).toByte(), (s.size shr 8).toByte(), s.size.toByte())
        val rLen = byteArrayOf((r.size shr 24).toByte(), (r.size shr 16).toByte(), (r.size shr 8).toByte(), r.size.toByte())
        return MESSAGE_AAD + sLen + s + rLen + r
    }

    /**
     * Encrypts a chat message with AAD binding to the exact conversation direction
     * (senderId/receiverId from the message row), so ciphertext replayed into another
     * chat — or with swapped participants — can never authenticate.
     *
     * V2-FIX W3 (replay resistance, documented-only): AAD binds the conversation but
     * NOT a message sequence/counter, so re-sending identical ciphertext WITHIN the
     * same chat still authenticates. Closing that requires server-side uniqueness
     * enforcement (rejecting duplicate ciphertext/UUID per conversation) — deliberately
     * not enforced here mid-flight because changing the AAD format would orphan cached
     * history. The client's UUID primary key already dedupes INSERTS (OnConflictStrategy),
     * so replayed deliveries collapse instead of duplicating rows; do not change the
     * [aadFor] wire format without a dual-derive migration.
     */
    fun encryptMessage(
        plainText: String,
        peerPublicKeyBase64: String,
        senderId: String,
        receiverId: String,
    ): Pair<String, String>? {
        if (plainText.length > MAX_MESSAGE_CHARS) return null
        val secretKey = deriveSharedKey(peerPublicKeyBase64) ?: return null
        return try {
            val iv = ByteArray(IV_LEN).apply { SecureRandom().nextBytes(this) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(AES_GCM_TAG_LEN, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            cipher.updateAAD(aadFor(senderId, receiverId))
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val cipherTextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            Pair(cipherTextBase64, ivBase64)
        } catch (e: Exception) {
            android.util.Log.e("WhisperCrypto", "Encryption failed", e)
            null
        }
    }

    /**
     * Decrypts a chat message. Tries the direction-bound AAD first; if that fails the
     * payload is retried WITHOUT AAD so legacy (pre-AAD) rows keep decrypting — history
     * must never break after this upgrade.
     *
     * V2-FIX W3 (see [encryptMessage]): same-chat replay protection requires server-side
     * uniqueness enforcement; AAD format is intentionally unchanged. Duplicate inserts
     * are already collapsed by the client UUID primary key, so a replayed delivery
     * cannot create a second visible row.
     *
     * V3-FIX (scoped legacy-AAD retirement): the no-AAD retry runs ONLY when
     * [messageCreatedAtEpochMs] is pre-cutoff ([legacyFallbackAllowed]) AND
     * [LEGACY_AAD_FALLBACK_ENABLED] is set — new messages never fall back regardless of
     * the flag. The default [Long.MAX_VALUE] means "never legacy": callers decrypting
     * cached/historical rows MUST pass the row's createdAt parsed to epoch millis
     * (undated rows parse to 0L = treated as legacy-eligible).
     */
    fun decryptMessage(
        cipherText: String,
        ivBase64: String?,
        peerPublicKeyBase64: String?,
        senderId: String,
        receiverId: String,
        messageCreatedAtEpochMs: Long = Long.MAX_VALUE,
    ): String? {
        // Whisper v1 never accepts a cleartext downgrade. Only authenticated AEAD payloads
        // are renderable; legacy/plain broadcast payloads are intentionally rejected.
        if (ivBase64.isNullOrBlank() || peerPublicKeyBase64.isNullOrBlank()) {
            return null
        }
        val secretKey = deriveSharedKey(peerPublicKeyBase64) ?: return null
        return try {
            val iv = Base64.decode(ivBase64.trim(), Base64.DEFAULT)
            val cipherBytes = Base64.decode(cipherText.trim(), Base64.DEFAULT)
            if (iv.size != IV_LEN || cipherBytes.size < 16) return null
            val gcmSpec = GCMParameterSpec(AES_GCM_TAG_LEN, iv)

            val aad = aadFor(senderId, receiverId)
            val aadCipher = Cipher.getInstance("AES/GCM/NoPadding")
            aadCipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            aadCipher.updateAAD(aad)
            val aadPlainBytes = runCatching { aadCipher.doFinal(cipherBytes) }.getOrNull()
            if (aadPlainBytes != null) {
                String(aadPlainBytes, Charsets.UTF_8)
            } else if (LEGACY_AAD_FALLBACK_ENABLED && legacyFallbackAllowed(messageCreatedAtEpochMs)) {
                // Legacy rows predating AAD binding: decrypt without it. Gated by
                // LEGACY_AAD_FALLBACK_ENABLED — see the H-3 note in the companion — and
                // V3-FIX: additionally scoped to pre-cutoff rows only.
                // P0 TELEMETRY: retirement gate for LEGACY_AAD_FALLBACK_ENABLED — the
                // flip may only happen once this counter reads ~0 across the fleet.
                ProtocolDiagnostics.increment("crypto.legacyAadFallback.message")
                val legacyCipher = Cipher.getInstance("AES/GCM/NoPadding")
                legacyCipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
                val legacyPlainBytes = legacyCipher.doFinal(cipherBytes)
                String(legacyPlainBytes, Charsets.UTF_8)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("WhisperCrypto", "Decryption failed: ${e.message}")
            null
        }
    }

    /** Encrypts binary attachments — AAD-bound to sender/receiver like messages so ciphertext cannot be replayed across chats. */
    fun encryptAttachment(bytes: ByteArray, recipientPublicKeyBase64: String, senderId: String, receiverId: String): Pair<ByteArray, String>? {
        if (bytes.isEmpty() || bytes.size > MAX_ATTACHMENT_BYTES) return null
        val secretKey = deriveSharedKey(recipientPublicKeyBase64) ?: return null
        return try {
            val iv = ByteArray(IV_LEN).apply { SecureRandom().nextBytes(this) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(AES_GCM_TAG_LEN, iv))
            cipher.updateAAD(aadFor(senderId, receiverId) + ATTACHMENT_AAD)
            cipher.doFinal(bytes) to Base64.encodeToString(iv, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("WhisperCrypto", "Attachment encryption failed", e)
            null
        }
    }

    /**
     * V3-FIX (scoped legacy-AAD retirement): the constant-AAD retry for pre-fix rows
     * runs ONLY when [messageCreatedAtEpochMs] is pre-cutoff ([legacyFallbackAllowed])
     * AND [LEGACY_AAD_FALLBACK_ENABLED] is set. The default [Long.MAX_VALUE] means
     * "never legacy" — callers decrypting cached/historical attachments MUST pass the
     * message's createdAt parsed to epoch millis (undated rows parse to 0L = legacy).
     */
    fun decryptAttachment(
        cipherBytes: ByteArray,
        ivBase64: String?,
        senderPublicKeyBase64: String?,
        senderId: String,
        receiverId: String,
        messageCreatedAtEpochMs: Long = Long.MAX_VALUE,
    ): ByteArray? {
        if (cipherBytes.size < 16 || ivBase64.isNullOrBlank() || senderPublicKeyBase64.isNullOrBlank()) return null
        val secretKey = deriveSharedKey(senderPublicKeyBase64) ?: return null
        return try {
            val iv = Base64.decode(ivBase64.trim(), Base64.DEFAULT)
            if (iv.size != IV_LEN) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(AES_GCM_TAG_LEN, iv))
            // Try bound AAD first; fall back to legacy constant-only AAD for pre-fix rows
            // (gated by LEGACY_AAD_FALLBACK_ENABLED — see the H-3 note in the companion —
            // and V3-FIX: additionally scoped to pre-cutoff rows only).
            val boundAad = aadFor(senderId, receiverId) + ATTACHMENT_AAD
            val gcmSpec = GCMParameterSpec(AES_GCM_TAG_LEN, iv)
            val bound = runCatching {
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
                cipher.updateAAD(boundAad)
                cipher.doFinal(cipherBytes)
            }.getOrNull()
            if (bound != null) return bound
            if (!LEGACY_AAD_FALLBACK_ENABLED || !legacyFallbackAllowed(messageCreatedAtEpochMs)) return null
            // P0 TELEMETRY: see decryptMessage — retirement gate for the flag flip.
            ProtocolDiagnostics.increment("crypto.legacyAadFallback.attachment")
            val legacy = Cipher.getInstance("AES/GCM/NoPadding")
            legacy.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            legacy.updateAAD(ATTACHMENT_AAD)
            legacy.doFinal(cipherBytes)
        } catch (e: Exception) {
            android.util.Log.w("WhisperCrypto", "Attachment decryption failed: ${e.message}")
            null
        }
    }

    // H-3 FIX (reviewwhisper.md): the old convenience overloads
    //   encryptAttachment(bytes, recipientKey) / decryptAttachment(cipher, iv, senderKey)
    // silently bound AAD to senderId="" receiverId="", producing replayable ciphertext.
    // They had no remaining callers and have been deleted — every caller must pass the
    // real (senderId, receiverId) pair.

    fun isCurrentPublicKey(publicKeyBase64: String?): Boolean =
        !publicKeyBase64.isNullOrBlank() && publicKeyBase64 == getPublicKeyBase64()

    // ---------------- PHASE 1: protocol signing identity (roadmap §1.1) ----------------

    /** Lazily creates the P-256 signing subkey. Idempotent, keystore-backed. */
    fun ensureSigningKeyExists() {
        runCatching {
            val keyStore = java.security.KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (!keyStore.containsAlias(PROTOCOL_SIGN_ALIAS)) {
                val generator = java.security.KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC, KEYSTORE_PROVIDER,
                )
                generator.initialize(
                    android.security.keystore.KeyGenParameterSpec.Builder(
                        PROTOCOL_SIGN_ALIAS,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                    )
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setAlgorithmParameterSpec(
                            java.security.spec.ECGenParameterSpec("secp256r1"),
                        )
                        .build(),
                )
                generator.generateKeyPair()
            }
        }.onFailure {
            android.util.Log.e(TAG_CRYPTO, "Protocol signing key generation failed", it)
        }
    }

    /**
     * PHASE 2 consumers sign prekey bundles here. Returns base64(ECDSA-P256-SHA256),
     * or null when the signing key is unavailable (fail-closed upstream).
     */
    fun signProtocol(payload: ByteArray): String? = runCatching {
        val keyStore = java.security.KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val entry = keyStore.getEntry(PROTOCOL_SIGN_ALIAS, null) as? java.security.KeyStore.PrivateKeyEntry
            ?: return@runCatching null
        val signature = java.security.Signature.getInstance("SHA256withECDSA")
        signature.initSign(entry.privateKey)
        signature.update(payload)
        Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }.getOrNull()

    /** Verifies a [signProtocol] signature against any published public key (X509 b64). */
    fun verifyProtocol(payload: ByteArray, signatureBase64: String, signerPublicX509Base64: String): Boolean = runCatching {
        val pub = parsePublicKey(signerPublicX509Base64) ?: return false
        val signature = java.security.Signature.getInstance("SHA256withECDSA")
        signature.initVerify(pub)
        signature.update(payload)
        signature.verify(Base64.decode(signatureBase64, Base64.NO_WRAP))
    }.getOrDefault(false)

    /** X509 base64 of the protocol signing public key — published alongside prekeys. */
    fun protocolSigningPublicKeyBase64(): String? = runCatching {
        val keyStore = java.security.KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val cert = keyStore.getCertificate(PROTOCOL_SIGN_ALIAS)?.publicKey ?: return@runCatching null
        Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
    }.getOrNull()

    /**
     * Destroys the local key pair and generates a fresh one (account deletion).
     * Removes the active pair, any staged-but-uncommitted pair, and the pointer.
     */
    /**
     * V6-R6: send-time identity triage. Returns true when a repair was performed
     * (dangling active pointer cleared and a usable identity restored); callers must
     * then republish so the server row matches the (possibly new) public key.
     */
    fun repairActiveIdentityIfBroken(): Boolean {
        val hasPrivate = getPrivateKey() != null
        if (hasPrivate) return false
        android.util.Log.e(TAG_CRYPTO, "Active identity unusable — attempting repair")
        ProtocolDiagnostics.increment("keypair.repairAttempted")
        return try {
            synchronized(rotationLock) {
                context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                    .edit().remove(PREF_ACTIVE_ALIAS).apply()
            }
            ensureKeyPairExists()
            val repaired = getPrivateKey() != null
            if (repaired) {
                ProtocolDiagnostics.increment("keypair.repaired")
                android.util.Log.w(TAG_CRYPTO, "Identity repaired — caller MUST republish the new public key")
            }
            repaired
        } catch (_: Exception) {
            false
        }
    }

    fun resetKeyPair() {
        try {
            synchronized(rotationLock) {
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                val prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                val active = prefs.getString(PREF_ACTIVE_ALIAS, null) ?: LEGACY_KEY_ALIAS
                for (alias in listOf(active, LEGACY_KEY_ALIAS)) {
                    if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
                }
                // Best-effort: remove any orphaned staged aliases from interrupted rotations.
                for (entry in keyStore.aliases().toList()) {
                    if (entry.startsWith(STAGED_ALIAS_PREFIX)) keyStore.deleteEntry(entry)
                }
                prefs.edit().remove(PREF_ACTIVE_ALIAS).apply()
            }
            ensureKeyPairExists()
        } catch (e: Exception) {
            android.util.Log.w("WhisperCrypto", "Key pair deletion failed", e)
        }
    }

    // ── Crash-safe rotation (publish-before-switch, rollback on failure) ──

    /**
     * Generates a fresh key pair under a STAGED alias without touching the active one.
     * Returns the staged alias + its public key, or null on failure. The caller must
     * publish [StagedKeyPair.publicKeyBase64] and then call either
     * [commitStagedKeyPair] (server confirmed) or [abortStagedKeyPair] (publish failed).
     */
    fun stageNewKeyPair(): StagedKeyPair? {
        return try {
            synchronized(rotationLock) {
                val alias = genStagedAlias()
                createKeyPairUnder(alias)
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
                    ?: return@synchronized null
                StagedKeyPair(alias, Base64.encodeToString(entry.certificate.publicKey.encoded, Base64.NO_WRAP))
            }
        } catch (e: Exception) {
            android.util.Log.e("WhisperCrypto", "Key staging failed", e)
            null
        }
    }

    /** Promotes a staged pair to active and destroys the previous key. Call only after the server accepted the new public key. */
    fun commitStagedKeyPair(staged: StagedKeyPair): Boolean {
        var committed = false
        try {
            synchronized(rotationLock) {
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                if (!keyStore.containsAlias(staged.alias)) return@synchronized
                val prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                val previous = prefs.getString(PREF_ACTIVE_ALIAS, null)
                // V2-FIX W7: persist the active-alias pointer SYNCHRONOUSLY (commit)
                // before destroying anything, so a failed write or crash can never leave
                // us pointing at an already-deleted key.
                if (!prefs.edit().putString(PREF_ACTIVE_ALIAS, staged.alias).commit()) {
                    android.util.Log.e("WhisperCrypto", "Failed to persist active alias; previous key kept intact")
                    return@synchronized
                }
                committed = true
                // HOTFIX companion: record the committed alias so hasStagedAliases()
                // keeps meaning "rotation IN FLIGHT" (the pointer check already covers
                // the active one; the set covers a future rotation staging while this
                // committed alias is still active). Best-effort: pointer truthiness
                // alone is already sufficient for correctness.
                prefs.edit().putStringSet(
                    PREF_COMMITTED_STAGED_ALIASES,
                    (prefs.getStringSet(PREF_COMMITTED_STAGED_ALIASES, emptySet()).orEmpty() + staged.alias),
                ).apply()
                // V2-FIX W7: delete the PREVIOUS key only AFTER the pointer persisted,
                // and never let its failure fail the rotation. NOTE: history encrypted
                // under the old key becomes undecryptable once destroyed if the local
                // cache was not migrated first — accepted trade-off (re-encryption is
                // deliberately NOT implemented here).
                if (previous != null && previous != staged.alias && keyStore.containsAlias(previous)) {
                    runCatching { keyStore.deleteEntry(previous) }
                        .onFailure { android.util.Log.w("WhisperCrypto", "Previous key cleanup failed (rotation still valid)", it) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WhisperCrypto", "Key commit failed", e)
            return false
        }
        return committed
    }

    /** Rolls an unpublished staged pair back — the old key remains active and intact. */
    fun abortStagedKeyPair(staged: StagedKeyPair) {
        try {
            synchronized(rotationLock) {
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                if (keyStore.containsAlias(staged.alias)) keyStore.deleteEntry(staged.alias)
            }
        } catch (e: Exception) {
            android.util.Log.w("WhisperCrypto", "Key abort failed", e)
        }
    }
}
