/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import android.util.Log
import com.frerox.toolz.crypto.SessionCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PHASE 2 (docs/WHISPER_ROADMAP.md §2.2/§2.3): publishes and maintains this
 * device's signed prekey bundle.
 *
 * Trust chain: a hardware-bound P-256 key (WhisperCrypto.signProtocol) signs each
 * Signed PreKey. A malicious server can hide or withhold bundles, but every bundle
 * it *serves* must carry a valid signature over (kind|kid|pub) — substituting keys
 * becomes cryptographically detectable instead of merely suspicious.
 *
 * One-time prekeys are consumed server-side on fetch; [ensurePublished] tops the
 * OPK pool back up whenever it runs low.
 */
@Singleton
class WhisperPrekeyManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val supabase: SupabaseClient,
    private val crypto: WhisperCrypto,
) {
    @Serializable
    private data class PrekeyRow(
        @SerialName("account") val account: String,
        @SerialName("kid") val kid: String,
        @SerialName("kind") val kind: String,
        @SerialName("public_key") val publicKey: String,
        @SerialName("signature") val signature: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Local record of unpublished/consumed state so we top up idempotently. */
    private var lastPublishedAtMs: Long = 0L

    // Wrapped private halves, keyed by kid. Keystore-AES protected at rest.
    private val privateKeysWrapped = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * PHASE 3 bridge: the private half for a prekey this device published. Returns
     * null for unknown/consumed kids — callers treat that as "cannot complete".
     */
    fun privateKeyForKid(kid: String): ByteArray? =
        privateKeysWrapped[kid]?.let { crypto.unwrapWithKeystoreAes(it) }

    /** Current Signed PreKey kid, if one was published this session. */
    var currentSpkKid: String? = null
        private set

    /**
     * Called at app start (auth-gated). Idempotent:
     *  1. ensure identity binding row exists (software X25519 Ik + P-256 signer pub),
     *  2. ensure a valid SPK exists (rotate weekly),
     *  3. top OPK pool up to [OPK_TARGET].
     */
    suspend fun ensurePublished(currentUserId: String): Result<Unit> = runCatching {
        if (currentUserId.isBlank()) return Result.success(Unit)

        // -- 1. Identity binding -------------------------------------------------
        val ikPriv = getOrCreateIdentitySeed()
        val ikPub = SessionCrypto.publicFromPrivate(ikPriv)
        val signerX509 = crypto.protocolSigningPublicKeyBase64()
            ?: error("Protocol signing key unavailable")
        val binding = buildJsonObject {
            put("ik", android.util.Base64.encodeToString(ikPub, android.util.Base64.NO_WRAP))
            put("signer", signerX509)
            put("v", 1)
        }
        supabase.postgrest.from("profiles").update(
            buildJsonObject { put("identity_binding", binding) },
        ) { filter { eq("id", currentUserId) } }

        // -- 2. Signed prekey (weekly rotation) ----------------------------------
        val spkDue = System.currentTimeMillis() - lastPublishedAtMs > SPK_ROTATE_MS ||
            countKind(currentUserId, "SPK") == 0
        if (spkDue) {
            val spkPriv = SessionCrypto.generatePrivateKey()
            val spkPub = SessionCrypto.publicFromPrivate(spkPriv)
            val kid = kidOf(spkPub)
            val signPayload = "SPK:$kid${android.util.Base64.encodeToString(spkPub, android.util.Base64.NO_WRAP)}"
                .toByteArray()
            val signature = crypto.signProtocol(signPayload)
                ?: error("Prekey signing failed — refusing to publish unsigned bundle")
            privateKeysWrapped[kid] = crypto.wrapWithKeystoreAes(spkPriv)
                ?: error("Could not protect SPK private half")
            supabase.postgrest.from("whisper_prekeys").upsert(
                PrekeyRow(
                    account = currentUserId, kid = kid, kind = "SPK",
                    publicKey = android.util.Base64.encodeToString(spkPub, android.util.Base64.NO_WRAP),
                    signature = signature,
                ),
            )
            currentSpkKid = kid
            lastPublishedAtMs = System.currentTimeMillis()
            ProtocolDiagnostics.log("prekeys: SPK published ($kid)")
        }

        // -- 3. One-time prekey pool ---------------------------------------------
        val currentOpks = countKind(currentUserId, "OPK")
        if (currentOpks < OPK_TARGET) {
            repeat(OPK_TARGET - currentOpks) {
                val priv = SessionCrypto.generatePrivateKey()
                val pub = SessionCrypto.publicFromPrivate(priv)
                val kid = kidOf(pub)
                privateKeysWrapped[kid] = crypto.wrapWithKeystoreAes(priv)
                    ?: error("Could not protect OPK private half")
                supabase.postgrest.from("whisper_prekeys").insert(
                    PrekeyRow(
                        account = currentUserId, kid = kid, kind = "OPK",
                        publicKey = android.util.Base64.encodeToString(pub, android.util.Base64.NO_WRAP),
                    ),
                )
            }
            ProtocolDiagnostics.log("prekeys: topped OPK pool to $OPK_TARGET")
        }
        Unit
    }.onFailure {
        Log.e("WhisperPrekeys", "ensurePublished failed", it)
        ProtocolDiagnostics.increment("prekeys.publishFail")
    }

    /**
     * PHASE 2 §2.4 verification half: validate that a fetched bundle's SPK is signed
     * by the peer's published P-256 signer. Returns true only on a valid chain.
     */
    fun verifyBundleSignature(
        spkPublicKeyB64: String,
        spkKid: String,
        signatureB64: String,
        signerPublicX509Base64: String,
    ): Boolean = crypto.verifyProtocol(
        payload = "SPK:$spkKid$spkPublicKeyB64".toByteArray(),
        signatureBase64 = signatureB64,
        signerPublicX509Base64 = signerPublicX509Base64,
    )

    // ------------------------------------------------------------------ internals

    private suspend fun countKind(userId: String, kind: String): Int =
        runCatching {
            supabase.postgrest.from("whisper_prekeys").select {
                filter { eq("account", userId); eq("kind", kind) }
            }.decodeList<CountRow>().size
        }.getOrDefault(0)

    @Serializable
    private data class CountRow(@SerialName("kid") val kid: String)

    private fun kidOf(publicKey: ByteArray): String =
        WhisperEnvelope.keyId(android.util.Base64.encodeToString(publicKey, android.util.Base64.NO_WRAP))

    /**
     * Software X25519 identity seed, encrypted at rest under the Keystore AES-GCM
     * key (same pattern as KeystoreSessionManager). Generated once per install.
     */
    @Volatile
    private var cachedIdentitySeed: ByteArray? = null

    @Synchronized
    private fun getOrCreateIdentitySeed(): ByteArray {
        cachedIdentitySeed?.let { return it }
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_IK_SEED, null)
        if (existing != null) {
            val unwrapped = crypto.unwrapWithKeystoreAes(existing)
                ?: error("Identity seed unwrapping failed — protocol state corrupted")
            return unwrapped.also { cachedIdentitySeed = it }
        }
        val seed = SessionCrypto.generatePrivateKey()
        val wrapped = crypto.wrapWithKeystoreAes(seed)
            ?: error("Could not protect identity seed")
        prefs.edit().putString(KEY_IK_SEED, wrapped).commit()
        return seed.also { cachedIdentitySeed = it }
    }

    /** PHASE 2 §2.4: this device's software X25519 identity private (for DH1). */
    @Synchronized
    fun identityPrivateKey(): ByteArray = getOrCreateIdentitySeed()

    companion object {
        private const val PREFS = "whisper_protocol_state"
        private const val KEY_IK_SEED = "identity_seed_wrapped"
        private const val SPK_ROTATE_MS = 7L * 24 * 60 * 60 * 1000
        private const val OPK_TARGET = 50
    }
}
