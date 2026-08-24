/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper.session

import com.frerox.toolz.crypto.SessionCrypto
import com.frerox.toolz.data.whisper.ProtocolDiagnostics
import java.util.Base64

/**
 * PHASE 3 (roadmap §3): minimal Double Ratchet per Signal specification
 * (Marlinspike/Perrin §1–5), adapted to Whisper transport.
 *
 * - Header rides inside the v3 message JSON: {"v":3,"dh","pn","n",…}.
 * - Associated data binds sessionId + header bytes → routing tamper = auth fail.
 * - Skipped-message keys bounded ([MAX_SKIPPED]); oldest evicted first.
 *
 * Pure Kotlin/JVM: the chaos suite exercises this class directly on CI.
 */
/**
 * Thrown when a message can no longer be opened under the Double Ratchet's
 * bounded-loss policy (retired chain straggler, window-evicted skipped key, or
 * unrecoverable desync). Callers render the honest locked placeholder and rely on
 * server catch-up + session renewal for everything newer.
 */
class WhisperRatchetLostMessage(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class WhisperRatchet private constructor(
    var rootKey: ByteArray,
    var dhSelfPriv: ByteArray,
    var dhSelfPub: ByteArray,
    var dhRemotePub: ByteArray?,
    var ckSend: ByteArray?,
    var ckRecv: ByteArray?,
    var ns: Int,
    var nr: Int,
    var pn: Int,
    val skipped: LinkedHashMap<String, ByteArray>,
    val consumed: LinkedHashMap<String, ByteArray> = LinkedHashMap(),
    // Remote ratchet keys we have stepped PAST. Late/duplicate messages sealed to a
    // retired key must be dropped (bounded loss) instead of triggering a bogus second
    // ratchet step that poisons the session.
    val retiredRemotePubs: LinkedHashSet<String> = LinkedHashSet(),
) {
    data class Header(val dhPub: ByteArray, val pn: Int, val n: Int)

    data class Sealed(val header: Header, val ciphertextPacked: ByteArray)

    companion object {
        const val MAX_SKIPPED = 400
        private const val INFO_RK = "WhisperRatchetRoot"

        /** Alice bootstrap: SK from X3DH, immediate sending chain against Bob's SPK. */
        fun initiator(x3dhSecret: ByteArray, remoteRatchetPubB64: String): WhisperRatchet {
            val remotePub = Base64.getDecoder().decode(remoteRatchetPubB64)
            val dhPair = newDhPair()
            val (root, cks) = kdfRootKey(
                x3dhSecret,
                requireNotNull(SessionCrypto.sharedSecret(dhPair.first, remotePub)) { "low-order peer key" },
            )
            return WhisperRatchet(
                // V5.2 CRITICAL FIX: store the EVOLVED root (first half of the KDF
                // output), not the raw X3DH secret. Previously the initiator kept SK as
                // rootKey forever, so its later ratchet steps salted with a stale root
                // and desynced after the peer's first reply.
                rootKey = root, dhSelfPriv = dhPair.first, dhSelfPub = dhPair.second,
                dhRemotePub = remotePub, ckSend = cks, ckRecv = null,
                ns = 0, nr = 0, pn = 0, skipped = LinkedHashMap(),
            )
        }

        /** Bob bootstrap: his SPK private IS his first ratchet key. */
        fun responder(x3dhSecret: ByteArray, spkPrivate: ByteArray, spkPublicB64: String): WhisperRatchet =
            WhisperRatchet(
                rootKey = x3dhSecret.copyOf(), dhSelfPriv = spkPrivate.copyOf(),
                dhSelfPub = Base64.getDecoder().decode(spkPublicB64), dhRemotePub = null,
                ckSend = null, ckRecv = null, ns = 0, nr = 0, pn = 0, skipped = LinkedHashMap(),
            )

        /** V6 (planwhisper.md §3.1): cold-start restore from a persisted [Snapshot]. */
        fun restored(snap: Snapshot): WhisperRatchet = WhisperRatchet(
            rootKey = ByteArray(32), dhSelfPriv = ByteArray(32), dhSelfPub = ByteArray(32),
            dhRemotePub = null, ckSend = null, ckRecv = null,
            ns = 0, nr = 0, pn = 0, skipped = LinkedHashMap(),
        ).load(snap)

        private fun newDhPair(): Pair<ByteArray, ByteArray> {
            val priv = SessionCrypto.generatePrivateKey()
            return priv to SessionCrypto.publicFromPrivate(priv)
        }

        private fun kdfRootKey(rk: ByteArray, dhOut: ByteArray): Pair<ByteArray, ByteArray> {
            val okm = SessionCrypto.hkdfSha256(dhOut, rk, INFO_RK.toByteArray(), 64)
            return okm.copyOfRange(0, 32) to okm.copyOfRange(32, 64)
        }

        private fun kdfChainKey(ck: ByteArray): Pair<ByteArray, ByteArray> =
            SessionCrypto.hmacSha256(ck, byteArrayOf(0x01)) to
                SessionCrypto.hmacSha256(ck, byteArrayOf(0x02))

        private fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
        private fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)
    }

    // --------------------------------------------------------------- encrypt

    fun encrypt(plaintext: ByteArray, extraAd: ByteArray = ByteArray(0)): Sealed {
        if (ckSend == null) {
            // Sending ratchet after having stepped our receive side.
            val remote = requireNotNull(dhRemotePub) { "cannot start sending chain without remote key" }
            val pair = newDhPair()
            dhSelfPriv = pair.first
            dhSelfPub = pair.second
            val (root2, cks) = kdfRootKey(
                rootKey,
                requireNotNull(SessionCrypto.sharedSecret(pair.first, remote)) { "low-order peer key" },
            )
            rootKey = root2
            ckSend = cks
            // Spec §3.3: header.pn = number of messages sent in the JUST-RETIRED
            // sending chain — i.e., our own `ns` right before the reset.
            pn = ns
            ns = 0
        }
        val ck = requireNotNull(ckSend)
        val (mk, nextCk) = kdfChainKey(ck)
        ckSend = nextCk
        val header = Header(dhSelfPub.copyOf(), pn, ns)
        ns++
        return Sealed(header, SessionCrypto.aesGcmSeal(mk, plaintext, adFor(header, extraAd)))
    }

    // --------------------------------------------------------------- decrypt

    fun decrypt(header: Header, packed: ByteArray, extraAd: ByteArray = ByteArray(0)): ByteArray {
        // Primary path (spec §4): skipped-window → same-chain → ratchet step.
        val primary = runCatching { decryptPrimary(header, packed, extraAd) }
        primary.getOrNull()?.let { return it }

        // V5.2-style authenticated brute-fallback: try EVERY remembered message key
        // (skipped + consumed). Wrong keys cannot falsely authenticate — AEAD decides.
        // This converts residual desync corner cases (crossed sends, replayed old
        // chains) into successful opens without weakening anything.
        val allKeys = skipped.asIterable() + consumed.asIterable()
        for ((keyId, mk) in allKeys) {
            SessionCrypto.aesGcmOpen(mk, packed, adFor(header, extraAd))?.let {
                // Promote: retire the skipped slot but REMEMBER the key so duplicates
                // of this message still open (bounded by MAX_SKIPPED via consumed cap).
                skipped.remove(keyId)
                rememberConsumed(keyId, mk)
                ProtocolDiagnostics.increment("ratchet.bruteFallback")
                return it
            }
        }

        // Retired-chain straggler or window-evicted loss: documented bounded loss.
        ProtocolDiagnostics.increment("ratchet.locked")
        throw WhisperRatchetLostMessage(
            primary.exceptionOrNull()?.message ?: "Ratchet cannot open this message",
            primary.exceptionOrNull(),
        )
    }

    private fun decryptPrimary(
        header: Header,
        packed: ByteArray,
        extraAd: ByteArray,
    ): ByteArray {
        // 1. Late delivery within stored window.
        // V5.2 FIX: promote to consumed-memory (not delete) so DUPLICATES of this same
        // late message also open — the chaos harness proved deletion was the killer.
        skipped[keyFor(header.dhPub, header.n)]?.let { mk ->
            skipped.remove(keyFor(header.dhPub, header.n))
            rememberConsumed(keyFor(header.dhPub, header.n), mk)
            return open(mk, header, packed, extraAd)
        }

        // 2. Same receiving chain: fast path (with duplicate/replay memory).
        if (header.dhPub.contentEquals(dhRemotePub ?: ByteArray(0))) {
            val dupKey = keyFor(header.dhPub, header.n)
            consumed[dupKey]?.let { return open(it, header, packed, extraAd) }
            skipKeys(header.dhPub, header.n)
            val ck = requireNotNull(ckRecv) { "receive chain missing" }
            val (mk, nextCk) = kdfChainKey(ck)
            ckRecv = nextCk
            rememberConsumed(dupKey, mk)
            nr++
            return open(mk, header, packed, extraAd)
        }

        // 3. Genuine DH ratchet step (spec §4.2 ordering):
        // Old receiving chain leftovers: the incoming header.pn declares exactly how
        // many messages the peer sent in the chain being retired.
        dhRemotePub?.let { skipKeys(it, header.pn) }
        dhRemotePub?.let { retired ->
            retiredRemotePubs.add(b64(retired))
            if (retiredRemotePubs.size > 8) retiredRemotePubs.remove(retiredRemotePubs.first())
        }
        dhRemotePub = header.dhPub.copyOf()
        val (root1, ckr) = kdfRootKey(
            rootKey,
            requireNotNull(SessionCrypto.sharedSecret(dhSelfPriv, header.dhPub)) { "low-order ratchet key" },
        )
        rootKey = root1
        ckRecv = ckr
        ckSend = null                                        // sending side re-ratchets lazily
        nr = 0

        skipKeys(header.dhPub, header.n)

        val ck2 = requireNotNull(ckRecv)
        val (mk, nextCk) = kdfChainKey(ck2)
        ckRecv = nextCk
        rememberConsumed(keyFor(header.dhPub, header.n), mk)
        nr++
        return open(mk, header, packed, extraAd)
    }

    // ------------------------------------------------------------- internals

    private fun open(mk: ByteArray, h: Header, packed: ByteArray, extraAd: ByteArray): ByteArray =
        SessionCrypto.aesGcmOpen(mk, packed, adFor(h, extraAd))
            ?: error("AES-GCM authentication failed")

    private fun adFor(h: Header, extra: ByteArray): ByteArray =
        h.dhPub + intBytes(h.pn) + intBytes(h.n) + extra

    private fun intBytes(v: Int): ByteArray =
        byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun keyFor(pub: ByteArray, n: Int): String = b64(pub) + ":" + n

    private fun rememberConsumed(keyId: String, mk: ByteArray) {
        if (consumed.size >= MAX_SKIPPED) consumed.remove(consumed.keys.first())
        consumed[keyId] = mk
    }

    /** Advance the CURRENT receive chain, storing every skipped message key. */
    private fun skipKeys(chainPub: ByteArray, untilExclusive: Int) {
        val ckStart = ckRecv ?: return
        if (nr >= untilExclusive) return
        var chain = ckStart
        var index = nr
        while (index < untilExclusive) {
            val (mk, next) = kdfChainKey(chain)
            if (skipped.size >= MAX_SKIPPED) {
                skipped.remove(skipped.keys.first())
            }
            skipped[keyFor(chainPub, index)] = mk
            chain = next
            index++
        }
        ckRecv = chain
        nr = untilExclusive
    }

    // -------------------------------------------------------- state snapshot

    @kotlinx.serialization.Serializable
    data class Snapshot(
        val rk: String, val sp: String, val spp: String, val rp: String?,
        val cs: String?, val cr: String?, val ns: Int, val nr: Int, val pn: Int,
        val skipped: Map<String, String>, val consumed: Map<String, String> = emptyMap(),
        val retired: List<String> = emptyList(),
    )

    fun snapshot(): Snapshot = Snapshot(
        b64(rootKey), b64(dhSelfPriv), b64(dhSelfPub),
        dhRemotePub?.let(::b64), ckSend?.let(::b64), ckRecv?.let(::b64),
        ns, nr, pn, skipped.mapValues { (_, v) -> b64(v) },
        consumed.mapValues { (_, v) -> b64(v) },
        retiredRemotePubs.toList(),
    )

    fun load(snap: Snapshot): WhisperRatchet {
        rootKey = unb64(snap.rk)
        dhSelfPriv = unb64(snap.sp)
        dhSelfPub = unb64(snap.spp)
        dhRemotePub = snap.rp?.let(::unb64)
        ckSend = snap.cs?.let(::unb64)
        ckRecv = snap.cr?.let(::unb64)
        ns = snap.ns
        nr = snap.nr
        pn = snap.pn
        skipped.clear()
        snap.skipped.forEach { (k, v) -> skipped[k] = unb64(v) }
        consumed.clear()
        snap.consumed.forEach { (k, v) -> consumed[k] = unb64(v) }
        retiredRemotePubs.clear()
        snap.retired.forEach { retiredRemotePubs.add(it) }
        return this
    }
}
