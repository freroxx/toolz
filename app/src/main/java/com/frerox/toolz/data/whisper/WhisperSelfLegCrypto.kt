/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import android.util.Base64
import com.frerox.toolz.crypto.SessionCrypto
import com.frerox.toolz.data.whisper.session.WhisperRatchet
import com.frerox.toolz.data.whisper.session.WhisperV3Codec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-addressed group frames ("self leg").
 *
 * Group fan-out seals one frame per member into `whisper_group_messages.frames`
 * and the SENDER is always one of those members: the sender's own frame is what
 * renders their own history on this device through the server rows, without any
 * plaintext cache. That leg cannot ride the 1:1 ratchet path — X3DH
 * establishment fetches the peer's prekey bundle from the edge, and the edge
 * refuses self-bundles by contract ("self-bundles are local") — so before this
 * module every group send failed on its own leg first (practically ALL group
 * sends, since the sender is always in the fan-out targets).
 *
 * Design (deliberately degenerate, one fresh session per frame):
 *  - A per-install anchor X25519 keypair is generated once; the private half is
 *    stored Keystore-wrapped in this module's prefs — the same protection tier
 *    as the prekey private halves. Choosing a dedicated anchor (rather than the
 *    live SPK in [WhisperPrekeyManager]) means weekly SPK rotation and OPK
 *    consumption can never orphan an already-sent self frame.
 *  - Each frame gets a fresh random 32-byte session key `sk` anchored through
 *    [WhisperRatchet.initiator] (throwaway sending half, DH'd against MY
 *    anchor pub) and opened through [WhisperRatchet.responder] rebuilt from the
 *    SAME raw `sk` + my anchor priv + anchor pub — X25519 symmetry makes both
 *    halves derive identical root/chain keys, so the single frame opens on the
 *    first receive-chain key. No ratchet state is advanced or persisted on
 *    open, so every later re-render of cached history reopens the row
 *    deterministically instead of decaying through a consumed-key window.
 *  - `sk` is persisted Keystore-wrapped (`sk:<uid>:<sid>`): possession of the
 *    wrapped copy alone does not open anything — the anchor private stays
 *    behind hardware Keystore, exactly like the OPK/SPK halves.
 *  - Losing the prefs (reinstall / data wipe) locks past self frames — the
 *    accepted 1:1 parity behavior (fresh install ⇒ fresh prekeys; old frames
 *    stay sealed). There is no server fallback for self legs BY DESIGN.
 *  - Session ids carry the `sg1-` prefix and [open] refuses frames without it,
 *    so a peer v3 frame can never be demoted into this path.
 *
 * Frames are valid [WhisperV3Codec] v3 JSON without an x3dh wire — the
 * transport column contract is unchanged; this is a new LEG, not a new format.
 */
@Singleton
class WhisperSelfLegCrypto @Inject constructor(
    @ApplicationContext appContext: Context,
    private val crypto: WhisperCrypto,
) {
    // Class is public only because Hilt injects it into the public WhisperRepository
    // constructor; every member below stays internal.
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val anchorLock = Any()
    private var anchorPubB64Cache: String? = null

    // ---------------------------------------------------------------- anchor

    /**
     * Per-install anchor PUBLIC key, creating the pair on first use. Null =
     * Keystore wrap failed; callers must fail this leg closed (same posture as
     * failing to wrap an OPK private half).
     */
    internal fun anchorPubB64(): String? {
        synchronized(anchorLock) {
            anchorPubB64Cache?.let { return it }
            val wrapped = prefs.getString(KEY_ANCHOR_PRIV, null)
            if (wrapped == null) {
                val priv = SessionCrypto.generatePrivateKey()
                val pubB64 = SessionCrypto.publicFromPrivate(priv).b64()
                val wrappedNew = crypto.wrapWithKeystoreAes(priv) ?: return null
                priv.fill(0)
                prefs.edit().putString(KEY_ANCHOR_PRIV, wrappedNew).apply()
                return pubB64.also { anchorPubB64Cache = it }
            }
            val priv = crypto.unwrapWithKeystoreAes(wrapped) ?: return null
            try {
                val pubB64 = SessionCrypto.publicFromPrivate(priv).b64()
                return pubB64.also { anchorPubB64Cache = it }
            } finally {
                priv.fill(0)
            }
        }
    }

    /** Anchor private (freshly unwrapped) with its matching public base64. */
    private fun unlockedAnchor(): Pair<ByteArray, String>? {
        val wrapped = prefs.getString(KEY_ANCHOR_PRIV, null) ?: return null
        val priv = crypto.unwrapWithKeystoreAes(wrapped) ?: return null
        val pubB64 = SessionCrypto.publicFromPrivate(priv).b64()
        return priv to pubB64
    }

    // ------------------------------------------------------------------ seal

    /**
     * Seals one self-addressed frame. Returns (frameJson, sessionId); null on
     * ANY failure — the fan-out treats the self leg exactly like a peer leg.
     * The caller owns the framing ([extraAd] binds sender|receiver, 1:1 style).
     */
    internal fun seal(plaintext: ByteArray, uid: String, extraAd: ByteArray): Pair<String, String>? =
        runCatching {
            val anchorPub = anchorPubB64() ?: error("self-leg anchor unavailable")
            val sk = ByteArray(32).also { SecureRandom().nextBytes(it) }
            try {
                val sealed = WhisperRatchet.initiator(sk, anchorPub).encrypt(plaintext, extraAd)
                val sid = newSid()
                val skWrapped = crypto.wrapWithKeystoreAes(sk)
                    ?: error("could not protect self-leg session key")
                prefs.edit().putString(skKey(uid, sid), skWrapped).apply()
                WhisperV3Codec.encode(sid, sealed.header, sealed.ciphertextPacked, x3dh = null) to sid
            } finally {
                sk.fill(0)
            }
        }.getOrNull()

    // ------------------------------------------------------------------ open

    /**
     * Opens one self-addressed frame by rebuilding the responder from the
     * persisted raw session key. Deliberately stateless: no ratchet advancement
     * is committed anywhere, so repeated renders of the same row keep opening.
     * Null on any failure (missing/wrapped-key refuses, wrong session id).
     */
    internal fun open(frameJson: String, uid: String, extraAd: ByteArray): ByteArray? =
        runCatching {
            val frame = WhisperV3Codec.parse(frameJson) ?: return null
            if (!frame.sessionId.startsWith(SID_PREFIX)) return null
            val skWrapped = prefs.getString(skKey(uid, frame.sessionId), null) ?: return null
            val sk = crypto.unwrapWithKeystoreAes(skWrapped) ?: return null
            try {
                val (anchorPriv, anchorPubB64) = unlockedAnchor() ?: return null
                try {
                    val ratchet = WhisperRatchet.responder(sk, anchorPriv, anchorPubB64)
                    ratchet.decrypt(
                        WhisperRatchet.Header(frame.dhPub(), frame.pn, frame.n),
                        frame.ciphertextPacked(),
                        extraAd,
                    )
                } finally {
                    anchorPriv.fill(0)
                }
            } finally {
                sk.fill(0)
            }
        }.getOrNull()

    // ------------------------------------------------------------- internals

    private fun skKey(uid: String, sid: String): String = "sk:$uid:$sid"

    private fun newSid(): String {
        val raw = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val hex = raw.joinToString("") { "%02x".format(it) }
        return SID_PREFIX + hex
    }

    private fun ByteArray.b64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    internal companion object {
        private const val PREFS_NAME = "whisper_self_leg"
        private const val KEY_ANCHOR_PRIV = "anchor_priv_wrapped"
        private const val SID_PREFIX = "sg1-"
    }
}
