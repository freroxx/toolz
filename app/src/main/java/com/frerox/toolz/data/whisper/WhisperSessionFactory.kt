/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.util.Base64
import android.util.Log
import com.frerox.toolz.crypto.SessionCrypto
import io.github.jan.supabase.SupabaseClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PHASE 2 (roadmap §2.4): X3DH-adapted session establishment.
 *
 * INITIATOR: fetch+verify peer bundle → verify SPK signature against the peer's
 * published P-256 signer → 3-DH (+optional OPK) → SK → store → return the `x3dh`
 * header that must ride on the next outgoing envelope.
 *
 * RESPONDER: read the header, recover own SPK/OPK private halves by kid, recompute
 * SK identically, store.
 *
 * The 32-byte secret is stored per-peer (Keystore-AES wrapped) and used as an extra
 * envelope candidate key. Phase 3's Double Ratchet replaces the static key without
 * changing the transport shape.
 */
@Singleton
class WhisperSessionFactory @Inject constructor(
    private val supabase: SupabaseClient,
    private val crypto: WhisperCrypto,
    private val prekeyManager: WhisperPrekeyManager,
    // P2: shared hardened transport (replaces the hand-rolled connection here).
    private val edgeFunctions: EdgeFunctionClient,
) {
    @Serializable
    data class X3dhHeader(
        @SerialName("ik") val ikPubB64: String,
        @SerialName("ek") val ekPubB64: String,
        @SerialName("spk") val spkKid: String,
        @SerialName("opk") val opkKid: String? = null,
    )

    data class InitiatedSession(
        val sessionKey: ByteArray,
        val header: X3dhHeader,
        // V6 (planwhisper.md §3.2): seeds ratchet.initiator() and the store's
        // peer-identity pin used for deterministic handshake acceptance.
        val remoteRatchetPubB64: String,
        val remoteIdentityIkB64: String,
    )

    // ------------------------------------------------------------------ state

    private data class Stored(
        val sessionId: String,
        val keyWrapped: String,
        val establishedAtMs: Long,
    )

    private val sessions = java.util.concurrent.ConcurrentHashMap<String, Stored>()
    private val json = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------------ API

    fun hasSession(peerId: String): Boolean = sessions.containsKey(peerId)

    fun sessionKeyFor(peerId: String): ByteArray? =
        sessions[peerId]?.let {
            runCatching { crypto.unwrapWithKeystoreAes(it.keyWrapped) }.getOrNull()
        }

    fun sealWithSession(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray =
        SessionCrypto.aesGcmSeal(key, plaintext, aad)

    fun openWithSession(key: ByteArray, packed: ByteArray, aad: ByteArray): ByteArray? =
        SessionCrypto.aesGcmOpen(key, packed, aad)

    /** Initiator side. Header MUST ride on the next envelope to this peer. */
    suspend fun initiateSession(peerId: String): Result<InitiatedSession> = runCatching {
        // V6 (planwhisper.md §3.3): single fetch+verify source shared with the
        // repository's establishOutbound — signature failure is a HARD error.
        val bundle = fetchAndVerifyBundle(peerId).getOrThrow()

        val spkPub = Base64.decode(bundle.spkPubB64, Base64.NO_WRAP)
        val ikB = Base64.decode(bundle.identityIkB64, Base64.NO_WRAP)

        val ekPriv = SessionCrypto.generatePrivateKey()
        val ekPub = SessionCrypto.publicFromPrivate(ekPriv)
        val ikPriv = prekeyManager.identityPrivateKey()

        val opkPubBytes = bundle.opkPubB64?.let { Base64.decode(it, Base64.NO_WRAP) }

        val sk = if (opkPubBytes != null) {
            kdf(
                SessionCrypto.sharedSecret(ikPriv, spkPub),
                SessionCrypto.sharedSecret(ekPriv, ikB),
                SessionCrypto.sharedSecret(ekPriv, spkPub),
                SessionCrypto.sharedSecret(ekPriv, opkPubBytes),
            )
        } else {
            kdf(
                SessionCrypto.sharedSecret(ikPriv, spkPub),
                SessionCrypto.sharedSecret(ekPriv, ikB),
                SessionCrypto.sharedSecret(ekPriv, spkPub),
                null,
            )
        }

        val sessionId = sessionIdFrom(sk)
        storeSession(peerId, sessionId, sk)

        val header = X3dhHeader(
            ikPubB64 = Base64.encodeToString(SessionCrypto.publicFromPrivate(ikPriv), Base64.NO_WRAP),
            ekPubB64 = Base64.encodeToString(ekPub, Base64.NO_WRAP),
            spkKid = bundle.spkKid,
            opkKid = bundle.opkKid,
        )
        ProtocolDiagnostics.log("x3dh: initiated $sessionId with ${peerId.take(6)}…")
        InitiatedSession(sk, header, bundle.spkPubB64, bundle.identityIkB64)
    }.onFailure {
        Log.w(TAG, "initiateSession failed", it)
        ProtocolDiagnostics.increment("x3dh.initiateFail")
    }

    /** V6: deterministic session id both X3DH parties derive identically (sha256(SK) prefix). */
    fun sessionIdFor(sk: ByteArray): String = sessionIdFrom(sk)

    /** Responder side: returns true when the session key is now stored for [peerId]. */
    fun respondToHeader(peerId: String, header: X3dhHeader): Boolean = runCatching {
        val ikAPub = Base64.decode(header.ikPubB64, Base64.NO_WRAP)
        val ekAPub = Base64.decode(header.ekPubB64, Base64.NO_WRAP)
        val spkPriv = prekeyManager.privateKeyForKid(header.spkKid)
            ?: error("SPK private no longer held")
        // V6-R3 FIX: a missing OPK private used to fall back SILENTLY to 3-DH while
        // the initiator had sealed 4-DH — both sides then derived DIFFERENT session
        // keys (session ids diverge) and every subsequent frame locked forever with
        // no diagnostic trail. A consumed/lost OPK must hard-fail instead; callers
        // recover via the envelope fallback + re-handshake heal loop.
        val opkPriv = header.opkKid?.takeIf { it.isNotBlank() }?.let { prekeyManager.privateKeyForKid(it) }
        if (header.opkKid != null && header.opkKid.isNotBlank() && opkPriv == null) {
            ProtocolDiagnostics.increment("x3dh.opkMissing")
            error("OPK private for kid ${header.opkKid.take(6)}… no longer held")
        }
        val ikMe = prekeyManager.identityPrivateKey()

        val sk = if (opkPriv != null) {
            kdf(
                SessionCrypto.sharedSecret(spkPriv, ikAPub),
                SessionCrypto.sharedSecret(ikMe, ekAPub),
                SessionCrypto.sharedSecret(spkPriv, ekAPub),
                SessionCrypto.sharedSecret(opkPriv, ekAPub),
            )
        } else {
            kdf(
                SessionCrypto.sharedSecret(spkPriv, ikAPub),
                SessionCrypto.sharedSecret(ikMe, ekAPub),
                SessionCrypto.sharedSecret(spkPriv, ekAPub),
                null,
            )
        }

        storeSession(peerId, sessionIdFrom(sk), sk)
        ProtocolDiagnostics.log("x3dh: accepted session from ${peerId.take(6)}…")
        true
    }.getOrElse {
        Log.w(TAG, "respondToHeader failed", it)
        ProtocolDiagnostics.increment("x3dh.respondFail")
        false
    }

    /** PHASE 2 §2.4 verification half exposed for trust surfaces. */
    fun verifyBundleSignature(spkPublicKeyB64: String, spkKid: String, signatureB64: String, signerX509: String): Boolean =
        crypto.verifyProtocol(
            payload = WhisperCrypto.spkSignedPayload(spkKid, spkPublicKeyB64),
            signatureBase64 = signatureB64,
            signerPublicX509Base64 = signerX509,
        )

    // ------------------------------------------------------------------ network

    /**
     * P2: fetch the peer's prekey bundle and verify the SPK carries their
     * hardware-signed seal. An invalid signature is a HARD error — callers must
     * surface it as key-change UI, never silently proceed (that is the exact MITM
     * window this protocol exists to close).
     */
    suspend fun fetchAndVerifyBundle(peerId: String): Result<VerifiedBundle> = runCatching {
        val bundle = fetchBundle(peerId)
        val binding = requireNotNull(bundle.identity_binding) {
            "Peer has no identity binding (pre-Phase-2 client)"
        }
        val spkSignature = requireNotNull(bundle.spk.signature) { "Unsigned SPK rejected" }
        check(
            verifyBundleSignature(bundle.spk.public_key, bundle.spk.kid, spkSignature, binding.signer)
        ) { "Prekey bundle signature invalid for this contact." }
        VerifiedBundle(
            spkKid = bundle.spk.kid,
            spkPubB64 = bundle.spk.public_key,
            opkKid = bundle.opk?.kid,
            opkPubB64 = bundle.opk?.public_key,
            identityIkB64 = binding.ik,
        )
    }.onFailure {
        ProtocolDiagnostics.increment("x3dh.bundleVerifyFail")
    }

    /** Verified, X3DH-ready parts of a peer bundle. All fields are public material. */
    data class VerifiedBundle(
        val spkKid: String,
        val spkPubB64: String,
        val opkKid: String?,
        val opkPubB64: String?,
        val identityIkB64: String,
    )

    @Serializable
    internal data class BundleDto(
        @SerialName("identity_binding") val identity_binding: BindingDto? = null,
        @SerialName("spk") val spk: SpkDto,
        @SerialName("opk") val opk: OpkDto? = null,
    )

    @Serializable
    internal data class BindingDto(val ik: String, val signer: String)

    @Serializable
    internal data class SpkDto(
        val kid: String,
        @SerialName("public_key") val public_key: String,
        val signature: String? = null,
    )

    @Serializable
    internal data class OpkDto(val kid: String, @SerialName("public_key") val public_key: String)

    /** P2: transport moved to [EdgeFunctionClient]; behavior (URL, headers, error
     *  strings, timeouts) preserved verbatim. Suspend now — both callers were already
     *  suspend-context. The old @Synchronized serialization is preserved via a mutex
     *  (@Synchronized is not applicable to suspend functions). */
    private val fetchMutex = kotlinx.coroutines.sync.Mutex()

    internal suspend fun fetchBundle(peerId: String): BundleDto = fetchMutex.withLock {
        val response = edgeFunctions.execute(
            EdgeFunctionClient.Request(
                function = "whisper-bundle-fetch",
                jsonBody = """{"account":"$peerId"}""",
                authMode = EdgeFunctionClient.AuthMode.ANON,
                connectTimeoutMs = 10_000,
                readTimeoutMs = 15_000,
            ),
        )
        if (!response.is2xx) {
            error("Bundle fetch failed (${response.code}): ${response.body.take(160)}")
        }
        json.decodeFromString(BundleDto.serializer(), response.body)
    }

    // ------------------------------------------------------------------ internals

    /** Signal-spec framing: 0xFF pad ‖ concat(DHs) → HKDF-SHA256(32). */
    private fun kdf(vararg dh: ByteArray?): ByteArray {
        require(dh.isNotEmpty()) { "no DH inputs" }
        // Trailing null = absent OPK slot (legitimate); skip it.
        val parts = dh.filterNotNull()
        require(parts.isNotEmpty()) { "no DH inputs" }
        val ikm = ByteArray(32) { 0xFF.toByte() } + parts.reduce { acc, b -> acc + b }
        return SessionCrypto.hkdfSha256(ikm, ByteArray(32), "WhisperX3DH-v1".toByteArray(), 32)
    }

    private fun sessionIdFrom(sk: ByteArray): String =
        "s" + SessionCrypto.sha256(sk).joinToString("") { "%02x".format(it) }.take(12)

    private fun storeSession(peerId: String, sessionId: String, sk: ByteArray) {
        val wrapped = crypto.wrapWithKeystoreAes(sk) ?: error("Could not protect session key")
        sessions[peerId] = Stored(sessionId, wrapped, System.currentTimeMillis())
    }

    private companion object {
        const val TAG = "WhisperX3DH"
    }
}
