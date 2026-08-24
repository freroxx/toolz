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

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast.BroadcastPayload
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeOldRecord
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

import com.frerox.toolz.R
import com.frerox.toolz.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

@Singleton
class WhisperRepository @Inject constructor(
    private val supabase: SupabaseClient,
    // V3-FIX (task F): app context lets the image decode path honor the device pixel
    // budget (maxPixelsForDevice) — previously this class had no Context injection.
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val crypto: WhisperCrypto,
    private val encryptedImageHost: WhisperEncryptedImageHost,
    private val deletedStore: WhisperDeletedMessagesStore,
    private val outgoingQueue: WhisperOutgoingQueue,
    private val deliveryScheduler: WhisperDeliveryScheduler,
    private val messageDao: WhisperMessageDao,
    private val keyTrustStore: WhisperKeyTrustStore,
    private val hiddenChatsStore: WhisperHiddenChatsStore,
    private val mutePrefs: WhisperMutePreferences,
    // H-1 FIX (reviewwhisper.md): channel teardown must run even though the flow's
    // ProducerScope is already cancelled when awaitClose fires — a plain `launch {}`
    // there never executes. This app-lifetime scope survives collection cancellation.
    @ApplicationScope private val appScope: kotlinx.coroutines.CoroutineScope,
    // V3-FIX (task F): wired so clearAllLocalData also wipes the encrypted image disk
    // cache — sign-out must not leave another account's cached images readable.
    private val imageDiskCache: WhisperImageDiskCache,
    // V6 (planwhisper.md §3.2): Double Ratchet live transport — persistent session
    // state, X3DH factory and SPK private halves for responder bootstrap.
    private val sessionStore: com.frerox.toolz.data.whisper.session.WhisperSessionStore,
    private val sessionFactory: WhisperSessionFactory,
    private val prekeyManager: WhisperPrekeyManager,
) {
    private companion object {
        const val MAX_MESSAGE_CHARS = 8_192
        const val EVENT_DEDUPE_TTL_MS = 30_000L
        const val MAX_RECENT_EVENT_IDS = 1_024
        val SUPPORTED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        const val MIN_IMAGE_EXPIRY_SECONDS = 60L
        const val MAX_IMAGE_EXPIRY_SECONDS = 15_552_000L
        // V6-R3: bounded resubscribe attempts per channel per process.
        const val MAX_RESUBSCRIBE_ATTEMPTS = 6
        // V6-R6 (#3): cooldown so the watcher can never amplify a teardown war.
        const val RESUBSCRIBE_MIN_INTERVAL_MS = 2_500L
        // V6-R5 (#4): a typing-signal row younger than this means "typing".
        const val TYPING_SIGNAL_FRESH_MS = 8_000L
    }
    private val db get() = supabase.postgrest
    private val store get() = supabase.storage
    private val realtime get() = supabase.realtime
    val myId get() = supabase.auth.currentUserOrNull()?.id ?: ""

    // V2-FIX (reviewwhisper.md) L-13: runCatching that never swallows coroutine
    // cancellation — plain runCatching around suspend calls converts a cancelled job
    // into Result.failure, so callers keep "working" a dead scope.
    private inline fun <T> runCatchingCE(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private val profileCache = ConcurrentHashMap<String, WhisperProfile>()
    private val profileCacheTs = ConcurrentHashMap<String, Long>()
    private val PROFILE_CACHE_TTL_MS = 5 * 60 * 1000L

    /** Cached only when fresh enough — a permanent cache made profile edits ghost until process death. */
    private fun cachedProfile(userId: String): WhisperProfile? {
        val ts = profileCacheTs[userId] ?: return null
        if (System.currentTimeMillis() - ts > PROFILE_CACHE_TTL_MS) return null
        return profileCache[userId]
    }

    private fun cacheProfile(userId: String, profile: WhisperProfile) {
        profileCache[userId] = profile
        profileCacheTs[userId] = System.currentTimeMillis()
    }

    // In-memory partner public keys for offline decryption of cached ciphertext.
    // Only ever populated from authenticated profile reads; the persisted fallback
    // (keyTrustStore) holds the key the user last accepted, so a changed key can
    // never silently decrypt old or new material.
    private val peerKeys = MutableStateFlow<Map<String, String>>(emptyMap())

    // Pairs (userId|publicKey) already surfaced through receiveKeyChanged, so a changed
    // key is signalled only once per change instead of on every profile read.
    private val receiveKeyNotified = Collections.synchronizedSet(mutableSetOf<String>())
    private val _receiveKeyChanged = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Emits a partner userId when their fresh server key differs from the accepted trusted key. */
    val receiveKeyChanged: Flow<String> = _receiveKeyChanged

    private fun cachePeerKey(userId: String, publicKey: String?) {
        if (!publicKey.isNullOrBlank()) {
            peerKeys.update { it + (userId to publicKey) }
            // Signal a possible key change (MITM / reinstall) once per (user, key) pair so
            // the chat screen can prompt a safety-number review without spamming.
            val trusted = keyTrustStore.knownKey(userId)
            if (trusted != null && trusted != publicKey && receiveKeyNotified.add("$userId|$publicKey")) {
                _receiveKeyChanged.tryEmit(userId)
            }
        }
    }

    // Users this account has blocked, kept in memory for flow-level filtering of cached history.
    private val blockedIds = MutableStateFlow<Set<String>>(emptySet())

    /** Trusted key first (TOFU): the in-memory fresh server key is only a first-contact fallback. */
    private fun peerKeyFor(userId: String): String? =
        keyTrustStore.knownKey(userId) ?: peerKeys.value[userId]

    // ===================== V5 SELF-HEALING ENVELOPES =====================
    // Root cause eliminated: the v1 format encrypted to exactly ONE recipient key, so
    // any drift between published and held keys produced permanently unreadable text
    // ("[Encrypted message]"). v5 wraps every message/image/typing event in a
    // kid-tagged multi-key envelope: the sender encrypts to EVERY recipient key it
    // knows about (TOFU-pinned + fresher server-published), and whichever copy matches
    // a key the recipient actually controls opens the message. Key drift degrades to a
    // few minutes of dual-ciphertext instead of permanent loss.

    private val IV_ENVELOPE = "env2"

    // V6 (planwhisper.md §3.2): sentinel for v3 Double Ratchet rows in content_iv.
    private val IV_V3 = com.frerox.toolz.data.whisper.session.WhisperV3Codec.IV_MARK

    // PHASE 1 (roadmap §1.2): protocol version negotiation scaffold.
    // V6: raised to 3 while the ratchet flag is on; envelope-only fleets keep v2.
    private val OUR_PROTOCOL_VERSION: Int
        get() = if (WhisperProtocolConfig.ratchetEnabled)
            WhisperProtocolConfig.RATCHET_PROTOCOL_VERSION
        else WhisperProtocolConfig.LIVE_PROTOCOL_VERSION
    // Per-peer floor = LOWEST version that peer has ever sent us. Rule: never send a
    // version below the recorded floor (the peer proved it can't parse those).
    // Session-scoped by design for now; persists with the session store in Phase 3.
    private val peerProtocolFloors = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private fun recordPeerProtocolFloor(userId: String, theirVersion: Int) {
        if (theirVersion <= 0) return
        peerProtocolFloors.merge(userId, theirVersion) { oldV, newV -> minOf(oldV, newV) }
    }

    /** Version we must address `userId` with. Never above what this build can speak. */
    private fun negotiatedVersionFor(userId: String): Int =
        maxOf(OUR_PROTOCOL_VERSION, peerProtocolFloors[userId] ?: 0)
            .coerceAtMost(
                if (WhisperProtocolConfig.ratchetEnabled)
                    WhisperProtocolConfig.RATCHET_PROTOCOL_VERSION
                else WhisperProtocolConfig.LIVE_PROTOCOL_VERSION,
            )

    // ───────────────────────── V6 DOUBLE RATCHET (v3 wire) ─────────────────────────

    /** One establish attempt per peer per window — a dead bundle must not retry per send. */
    private val ESTABLISH_COOLDOWN_MS = 60_000L
    /** Slack when dating rows vs session creation (device clock skew tolerance). */
    private val CLOCK_SKEW_SLACK_MS = 5 * 60_000L
    private val establishAttemptAtMs = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Send rule (planwhisper.md §3.5): speak v3 only to peers that already hold an
     * established ratchet session here, or to unproven peers while the establish
     * cooldown is clear (fresh conversations bootstrap this way). Peers recorded at
     * floor 2 (envelope-only) are never re-attempted within the cooldown.
     */
    private fun shouldUseV3(peerId: String): Boolean {
        if (!WhisperProtocolConfig.ratchetEnabled) return false
        if (sessionStore.peek(peerId) != null) return true
        val floor = peerProtocolFloors[peerId]
        if (floor != null && floor < WhisperProtocolConfig.RATCHET_PROTOCOL_VERSION) return false
        val last = establishAttemptAtMs[peerId] ?: 0L
        return System.currentTimeMillis() - last > ESTABLISH_COOLDOWN_MS
    }

    /** Associated data binds every ratchet frame to its routing direction. */
    private fun adBytes(senderId: String, receiverId: String): ByteArray =
        "$senderId|$receiverId".toByteArray(Charsets.UTF_8)

    /**
     * Outgoing ratchet seal. Returns (v3-frame-json, "v3") or null on ANY failure —
     * callers fall back to the proven envelope path. NEVER blocks a message.
     */
    private suspend fun sealWithRatchet(
        senderId: String,
        receiverId: String,
        plaintext: String,
    ): Pair<String, String>? = try {
        sessionStore.mutexFor(receiverId).withLock {
            val live = sessionStore.load(receiverId) ?: establishOutboundLocked(receiverId)
            val ratchet = live?.ratchet ?: return@withLock null
            val sealed = ratchet.encrypt(plaintext.toByteArray(Charsets.UTF_8), adBytes(senderId, receiverId))
            // The handshake header rides exactly ONE frame: once sealed into this
            // ciphertext it must never attach again (a duplicate would force the peer
            // through a second X3DH and orphan both ratchets).
            val x3dh = live.pendingHeader
            live.pendingHeader = null
            live.dirty = true
            sessionStore.save(receiverId, live)
            ProtocolDiagnostics.increment("v3.sealed")
            com.frerox.toolz.data.whisper.session.WhisperV3Codec.encode(
                live.sessionId, sealed.header, sealed.ciphertextPacked, x3dh,
            ) to IV_V3
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        ProtocolDiagnostics.increment("v3.sealFail")
        android.util.Log.w("WhisperRepo", "v3 seal failed — falling back to envelope", e)
        null
    }

    /**
     * X3DH initiate → Double Ratchet initiator state, persisted with its pending
     * handshake header. Caller MUST hold [sessionStore.mutexFor]. Null on any failure.
     */
    private suspend fun establishOutboundLocked(peerId: String): com.frerox.toolz.data.whisper.session.WhisperSessionStore.Live? {
        establishAttemptAtMs[peerId] = System.currentTimeMillis()
        val initiated = sessionFactory.initiateSession(peerId).getOrNull() ?: run {
            ProtocolDiagnostics.increment("v3.establishFail")
            return null
        }
        val skWrapped = crypto.wrapWithKeystoreAes(initiated.sessionKey) ?: run {
            ProtocolDiagnostics.increment("v3.establishFail")
            return null
        }
        val ratchet = runCatching {
            com.frerox.toolz.data.whisper.session.WhisperRatchet.initiator(
                initiated.sessionKey, initiated.remoteRatchetPubB64,
            )
        }.getOrNull() ?: run {
            ProtocolDiagnostics.increment("v3.establishFail")
            return null
        }
        val live = com.frerox.toolz.data.whisper.session.WhisperSessionStore.Live(
            sessionId = sessionFactory.sessionIdFor(initiated.sessionKey),
            x3dhKeyWrapped = skWrapped,
            peerIkB64 = initiated.remoteIdentityIkB64,
            createdAtMs = System.currentTimeMillis(),
            pendingHeader = initiated.header,
            ratchet = ratchet,
        )
        sessionStore.save(peerId, live)
        ProtocolDiagnostics.log("v3: established outbound session ${live.sessionId}")
        return live
    }

    /**
     * V5.2 OUT-OF-THE-BOX guarantee: whenever this device detects that incoming mail
     * was sealed to a key it does not hold, it republishes its CURRENT public key
     * immediately. The sender's very next message then opens cleanly — no rotation,
     * no verification popups. Idempotent; no-ops when already in sync.
     */
    suspend fun republishLocalKeyIfStale(): Boolean {
        val currentId = myId
        if (currentId.isBlank()) return false
        val localPub = crypto.getPublicKeyBase64() ?: return false
        val remote = runCatchingCE {
            db.from("profiles").select { filter { eq("id", currentId) } }
                .decodeSingleOrNull<WhisperProfile>()
        }.getOrNull() ?: return false
        val serverKey = remote.publicKey
        if (serverKey == localPub) return false // already in sync
        if (serverKey != null && crypto.hasStagedAliases()) return false // mid-rotation
        val body = buildJsonObject {
            put("public_key", localPub)
            put("updated_at", java.time.Instant.now().toString())
        }
        runCatching { updateProfileRowWithFreshness(currentId, body) }.onFailure {
            android.util.Log.e("WhisperRepo", "republish failed", it)
            return false
        }
        profileCache.remove(currentId); profileCacheTs.remove(currentId)
        ProtocolDiagnostics.increment("key.republished")
        ProtocolDiagnostics.log("keyHeal: republished current key on stale detection")
        return true
    }

    /**
     * V5.1 RELIABILITY: profile updates that publish a NEW public key must also move
     * `updated_at`, otherwise every contact classifies the change as CHANGED instead
     * of ROTATED_AUTO and the manual verify dance returns. Some hosted schemas gate
     * that column behind a trigger — so we attempt the bump and transparently retry
     * without it if PostgREST rejects the field.
     */
    private suspend fun updateProfileRowWithFreshness(
        currentId: String,
        body: kotlinx.serialization.json.JsonObject,
    ) {
        runCatching {
            db.from("profiles").update(body) { filter { eq("id", currentId) } }
        }.getOrElse { failed ->
            val msg = failed.message.orEmpty()
            val restStatus = (failed as? io.github.jan.supabase.exceptions.RestException)?.statusCode
            val stampRejected = restStatus == 400 || msg.contains("updated_at", ignoreCase = true)
            val stripped = kotlinx.serialization.json.JsonObject(
                body.toMap() - "updated_at",
            )
            if (!stampRejected || stripped == body) throw failed
            android.util.Log.w("WhisperRepo", "updated_at bump rejected; retried without stamp")
            db.from("profiles").update(stripped) { filter { eq("id", currentId) } }
        }
    }

    private fun oldKeyPlaceholder(): String =
        WhisperTombstone.LOCKED_OLDER_KEY

    /**
     * Every recipient public key this device would plausibly need to encrypt for,
     * keyed by kid (short hash) and capped: [trusted pinned] + [freshly seen server].
     */
    private fun recipientKeyCandidates(userId: String): LinkedHashMap<String, String> {
        val candidates = LinkedHashMap<String, String>()
        peerKeys.value[userId]?.let { candidates[WhisperEnvelope.keyId(it)] = it }
        peerKeyFor(userId)?.let { candidates.putIfAbsent(WhisperEnvelope.keyId(it), it) }
        return LinkedHashMap(candidates.entries.take(3).associateBy({ it.key }) { it.value })
    }

    /**
     * V5.1 RELIABILITY: when a decrypt succeeds against the FRESH server key while a
     * different older key was pinned, adopt the fresh key automatically whenever the
     * server classifies the rotation as recent. This is what removes the manual
     * rotate-and-verify dance: peers converge on the new key transparently, and the
     * UI shows only the passive "rotated automatically" notice.
     */
    private suspend fun adoptFreshKeyIfFresh(userId: String, freshPublicKey: String?) {
        if (freshPublicKey.isNullOrBlank()) return
        val trusted = peerKeyFor(userId)
        if (trusted == freshPublicKey) return
        val profile = profileCache[userId]
        if (!isFreshServerRotation(profile ?: run {
            // adopt path may be called right after a forced fetch that refreshed cache
            profileCacheTs[userId]?.let { cachedProfile(userId) } ?: profileCache[userId]
        })) {
            ProtocolDiagnostics.log("keyAdopt: skipped (not fresh) for ${userId.take(6)}…")
            return
        }
        runCatchingCE {
            keyTrustStore.rememberKey(userId, freshPublicKey)
        }
        cachePeerKey(userId, freshPublicKey)
        ProtocolDiagnostics.increment("key.autoAdopted")
        ProtocolDiagnostics.log("keyAdopt: adopted fresh key for ${userId.take(6)}…")
    }

    /**
     * Single decryption entry point for every stored form:
     *  - v3 ratchet frames (iv == "v3" sentinel) -> per-peer Double Ratchet;
     *  - legacy v1 rows (real per-row iv) -> memoized direct decrypt;
     *  - v5 envelopes (iv == sentinel) -> try each entry against the kids this device
     *    can open (its own current key, or the partner key used when I sent it).
     * Returns null when nothing opens the content (caller renders the locked marker).
     */
    private suspend fun decryptUnified(
        rawContent: String,
        ivMark: String?,
        partnerId: String,
        msgSenderId: String,
        msgReceiverId: String,
        createdAtEpochMs: Long,
        extraFreshPeerKey: String? = null,
    ): String? {
        // V6 (planwhisper.md §3.4): v3 Double Ratchet frames open first; the entire
        // legacy ladder below stays intact for peers without a session.
        if (ivMark == IV_V3 || com.frerox.toolz.data.whisper.session.WhisperV3Codec.isV3(rawContent)) {
            return openV3Frame(rawContent, partnerId, msgSenderId, msgReceiverId)
        }
        // V5.2 — THE decisive fix for the field-reported "[Encrypted with an older
        // key]" bug: candidate PEER keys are tried exhaustively per envelope entry.
        // The previous version selected the right ciphertext copy but then derived the
        // shared secret with the (possibly stale) pinned key only, discarding the very
        // fresh key the retry had just fetched.
        val isEnvelope = ivMark == IV_ENVELOPE || WhisperEnvelope.isEnvelope(rawContent)

        if (!isEnvelope) {
            var result = peerKeyFor(partnerId)?.let {
                decryptMemoized(rawContent, ivMark, it, msgSenderId, msgReceiverId, createdAtEpochMs)
            }
            if (result == null && extraFreshPeerKey != null && extraFreshPeerKey != peerKeyFor(partnerId)) {
                result = decryptMemoized(rawContent, ivMark, extraFreshPeerKey, msgSenderId, msgReceiverId, createdAtEpochMs)
            }
            return maybeHealFromPlaintext(result, msgSenderId, partnerId, createdAtEpochMs)
        }

        val entries = WhisperEnvelope.decode(rawContent)
        if (entries == null) {
            ProtocolDiagnostics.increment("envelope.decodeFail")
            return null
        }

        // Every partner public key we might need to derive with, most-likely-first.
        // V6-R7: the envelope's IN-BAND sender key joins the trial set — delivery no
        // longer depends on any server-stored view of the partner being fresh.
        val peerCandidates = buildList {
            add(peerKeyFor(partnerId))
            add(extraFreshPeerKey)
            add(peerKeys.value[partnerId])
            add(WhisperEnvelope.inBandSenderKey(rawContent))
        }.filterNotNull().distinct()

        val myKid = WhisperEnvelope.ownKid(crypto)
        val sentByMe = msgSenderId == myId

        var ladderResult: String? = null
        loop@ for ((kid, ivB64, ctB64) in entries) {
            val peerPubs: List<String> = when {
                // Copy encrypted to MY key (incoming): derive with partner's key(s).
                !sentByMe -> peerCandidates
                // Copy encrypted to the PARTNER key named by kid (my sent rows):
                // use exactly that key — recover it from candidates by kid match.
                else -> peerCandidates.filter { WhisperEnvelope.keyId(it) == kid }
            }
            for (pub in peerPubs) {
                val plain = decryptMemoized(ctB64, ivB64, pub, msgSenderId, msgReceiverId, createdAtEpochMs)
                if (plain != null) { ladderResult = plain; break@loop }
            }
        }

        if (ladderResult == null) {
            ProtocolDiagnostics.increment("envelope.locked")
            // V6-R6: throttled — render loops re-decrypt whole history and this line
            // used to flood the 60-line ring buffer, crowding out keystate/rt.* lines.
            ProtocolDiagnostics.logThrottled(
                "envLocked", "envelope.locked",
                event = "envelope locked: entries=${entries.map { it.first }} peers=${peerCandidates.map { WhisperEnvelope.keyId(it) }} own=$myKid sentByMe=$sentByMe",
            )
            // V6-R6 (#2): convergence trigger — an incoming envelope sealed to a kid
            // that is NOT our current one means the partner used a stale copy of OUR
            // published key. Fire the republish immediately so the partner's NEXT send
            // (force-refreshing) converges without any manual rotate+verify step.
            // Deduped by the method's own server-compare.
            if (!sentByMe && entries.none { (kid, _, _) -> kid == myKid }) {
                ProtocolDiagnostics.increment("keyDrift.sealedToUnknownOwnKid")
                appScope.launch { runCatching { republishLocalKeyIfStale() } }
            }
        }
        return maybeHealFromPlaintext(ladderResult, msgSenderId, partnerId, createdAtEpochMs)
    }

    /**
     * V6-R2 self-heal gate (review fix): heal decisions need the OPENED plaintext,
     * because image attachments ride the SAME envelope shape as text — tearing down
     * on wire shape alone made every received image nuke an otherwise-healthy ratchet
     * session. Only a successfully opened TEXT payload from the partner proves their
     * current (non-v3) capability; locked rows and images must never heal.
     */
    private suspend fun maybeHealFromPlaintext(
        opened: String?,
        msgSenderId: String,
        partnerId: String,
        createdAtEpochMs: Long,
    ): String? {
        if (opened != null && msgSenderId != myId &&
            !opened.startsWith(WhisperImageAttachment.MESSAGE_PREFIX)
        ) {
            maybeTeardownStaleSession(partnerId, createdAtEpochMs)
        }
        return opened
    }

    /**
     * V6 (planwhisper.md §3.4): opens an incoming v3 Double Ratchet frame.
     *
     * Handshake handling — the `x3dh` key rides only on a session's FIRST frame:
     *  - no stored session, or acceptance gate passes (identity changed / lower-sid
     *    tie-break) → respondToHeader + responder ratchet bootstrap;
     *  - same-session replay → skipped (re-running X3DH would reset advanced chains);
     *  - rejected racing handshake → frame renders locked once; the deterministic
     *    tie-break guarantees both sides converge on ONE session.
     */
    private suspend fun openV3Frame(
        rawContent: String,
        partnerId: String,
        msgSenderId: String,
        msgReceiverId: String,
    ): String? {
        val codec = com.frerox.toolz.data.whisper.session.WhisperV3Codec
        val frame = codec.parse(rawContent) ?: run {
            ProtocolDiagnostics.increment("v3.parseFail")
            return null
        }
        return sessionStore.mutexFor(partnerId).withLock {
            // 1. Optional first-frame handshake.
            val wire = frame.x3dh
            if (wire != null) {
                val existing = sessionStore.load(partnerId)
                when {
                    // Idempotent redelivery of a handshake we already bootstrapped:
                    // re-running X3DH here would reset the advanced ratchet chains.
                    existing != null && existing.sessionId == frame.sessionId && existing.ratchet != null ->
                        ProtocolDiagnostics.increment("v3.handshakeReplay")
                    // Racing initiator lost the deterministic tie-break (or a stale
                    // post-reinstall header): one locked frame, then convergence.
                    existing != null && !existing.canAcceptHandshake(wire.ikPubB64, frame.sessionId) -> {
                        ProtocolDiagnostics.increment("v3.handshakeRejected")
                        return@withLock null
                    }
                    else -> {
                        val hdr = codec.toFactoryHeader(wire)
                        if (!sessionFactory.respondToHeader(partnerId, hdr)) {
                            // SPK/OPK privates no longer held (rotation raced us).
                            ProtocolDiagnostics.increment("v3.respondFail")
                            return@withLock null
                        }
                        val sk = sessionFactory.sessionKeyFor(partnerId)
                        val spkPriv = prekeyManager.privateKeyForKid(hdr.spkKid)
                        if (sk == null || spkPriv == null) {
                            ProtocolDiagnostics.increment("v3.respondFail")
                            return@withLock null
                        }
                        val skWrapped = crypto.wrapWithKeystoreAes(sk) ?: run {
                            // Refusing to persist unprotected key material is fatal
                            // to THIS handshake only — envelope fallback still works.
                            ProtocolDiagnostics.increment("v3.respondFail")
                            return@withLock null
                        }
                        // Bob bootstrap: his SPK private IS his first ratchet key; the
                        // public half is derived back from it so both sides agree.
                        sessionStore.save(
                            partnerId,
                            com.frerox.toolz.data.whisper.session.WhisperSessionStore.Live(
                                sessionId = frame.sessionId,
                                x3dhKeyWrapped = skWrapped,
                                peerIkB64 = wire.ikPubB64,
                                createdAtMs = System.currentTimeMillis(),
                                pendingHeader = null,
                                ratchet = com.frerox.toolz.data.whisper.session.WhisperRatchet.responder(
                                    sk, spkPriv,
                                    java.util.Base64.getEncoder().encodeToString(
                                        com.frerox.toolz.crypto.SessionCrypto.publicFromPrivate(spkPriv),
                                    ),
                                ),
                            ),
                        )
                        ProtocolDiagnostics.log("v3: accepted peer session ${frame.sessionId}")
                    }
                }
            }

            // 2. Locate ratchet state for THIS session id.
            val live = sessionStore.load(partnerId)
            val ratchet = live?.ratchet
            if (live == null || ratchet == null || live.sessionId != frame.sessionId) {
                ProtocolDiagnostics.increment("v3.noSession")
                return@withLock null
            }

            // 3. Decrypt and persist the advanced state immediately — process death
            // between decrypt and save would otherwise replay-consume message keys.
            val plain = try {
                ratchet.decrypt(
                    com.frerox.toolz.data.whisper.session.WhisperRatchet.Header(frame.dhPub(), frame.pn, frame.n),
                    frame.ciphertextPacked(),
                    adBytes(msgSenderId, msgReceiverId),
                )
            } catch (e: com.frerox.toolz.data.whisper.session.WhisperRatchetLostMessage) {
                ProtocolDiagnostics.increment("v3.locked")
                android.util.Log.w("WhisperRepo", "v3 frame locked (${e.message})")
                return@withLock null
            }
            recordPeerProtocolFloor(partnerId, WhisperProtocolConfig.RATCHET_PROTOCOL_VERSION)
            live.dirty = true
            sessionStore.save(partnerId, live)
            ProtocolDiagnostics.increment("v3.opened")
            plain.decodeToString()
        }
    }

    /**
     * V6 self-heal (planwhisper.md §4.3): fresh non-v3 TEXT traffic from a peer we
     * hold a session with means THEY lost it (reinstall / lost handshake frame).
     * Drop ours so the next outbound re-initiates. Callers gate on opened plaintext
     * ([maybeHealFromPlaintext]) so images and locked rows never trigger this;
     * freshness is judged against the session's creation time with clock-skew slack
     * so cached history never tears down state either.
     */
    private suspend fun maybeTeardownStaleSession(peerId: String, rowEpochMs: Long) {
        val live = sessionStore.peek(peerId) ?: return
        val now = System.currentTimeMillis()
        if (rowEpochMs + CLOCK_SKEW_SLACK_MS < live.createdAtMs) return // pre-session row
        if (rowEpochMs - CLOCK_SKEW_SLACK_MS > now) return             // future-dated row
        sessionStore.delete(peerId)
        establishAttemptAtMs.remove(peerId)
        ProtocolDiagnostics.increment("v3.peerReset")
        ProtocolDiagnostics.log("v3: non-v3 traffic from ${peerId.take(6)}… — dropping session for re-handshake")
    }


    /** Decrypts a cached ciphertext message for display, falling back to a neutral marker. */
    private suspend fun WhisperMessage.decryptContent(peerKey: String?, extraFresh: String? = null): WhisperMessage {
        // Server rows carry no version column; infer from the wire shape.
        val inferredVersion = when {
            protocolVersion > 0 -> protocolVersion
            com.frerox.toolz.data.whisper.session.WhisperV3Codec.isV3(content) ->
                WhisperProtocolConfig.RATCHET_PROTOCOL_VERSION
            WhisperEnvelope.isEnvelope(content) -> WhisperEnvelope.VERSION
            else -> 0
        }
        // V6: only INCOMING rows prove a partner's capability — our own sent frames
        // must never raise the recorded floor for them.
        if (inferredVersion > 0 && senderId != myId) {
            recordPeerProtocolFloor(
                if (senderId == myId) receiverId else senderId,
                inferredVersion,
            )
        }
        if (isDeletedForEveryone) return this
        if (contentIv == null && !WhisperEnvelope.isEnvelope(content)) return this
        val partnerId = if (senderId == myId) receiverId else senderId
        val decrypted = runCatchingCE {
            decryptUnified(
                rawContent = content,
                ivMark = contentIv,
                partnerId = partnerId,
                msgSenderId = senderId,
                msgReceiverId = receiverId,
                createdAtEpochMs = WhisperMessageEntity.parseSortEpoch(createdAt),
                extraFreshPeerKey = extraFresh ?: peerKey,
            )
        }.getOrNull()
        if (decrypted == null) {
            // V6-R6: throttled — see envelope.locked note above.
            ProtocolDiagnostics.increment("decrypt.locked")
            ProtocolDiagnostics.logThrottled("decryptLocked", "decrypt.locked", event = "decrypt locked (cached row)")
        }
        return copy(content = decrypted ?: oldKeyPlaceholder())
    }

    /**
     * LRU memo for [crypto.decryptMessage]. Keyed by (direction, key, iv, ciphertext);
     * bounded to 512 entries so plaintext memory stays capped. Only successful
     * decryptions are cached (failures stay cheap and retryable).
     */
    private val decryptMemo = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > 512
    }

    private fun decryptMemoized(
        rawCipher: String,
        ivBase64: String?,
        peerKey: String,
        senderId: String,
        receiverId: String,
        messageCreatedAtEpochMs: Long,
    ): String? {
        if (ivBase64.isNullOrBlank()) return null
        val memoKey = "$senderId\u0000$receiverId\u0000$peerKey\u0000$ivBase64\u0000$rawCipher"
        synchronized(decryptMemo) { decryptMemo[memoKey]?.let { return it } }
        // V3-FIX: forwards the row's creation time so crypto can scope its legacy fallback.
        val out = crypto.decryptMessage(rawCipher, ivBase64, peerKey, senderId, receiverId, messageCreatedAtEpochMs)
            ?: return null
        synchronized(decryptMemo) { decryptMemo[memoKey] = out }
        return out
    }
    // Cache conversations list to avoid full reload on every new message in the chats hub
    // L-3 FIX (reviewwhisper.md): one @Volatile immutable snapshot instead of two
    // independently-volatile fields (value + timestamp could tear across threads).
    private data class ConversationsCacheEntry(val value: List<WhisperConversation>, val at: Long)
    @Volatile private var conversationsCache: ConversationsCacheEntry? = null
    private val CONVERSATIONS_CACHE_TTL = 30_000L // 30 seconds

    // Persistent broadcast channels keyed by channel name — shared across send/react/delete
    // so we don't subscribe to a brand-new channel object for each outgoing event.
    private val broadcastChannelCache = mutableMapOf<String, io.github.jan.supabase.realtime.RealtimeChannel>()
    private val channelMutex = Mutex()
    // Serializes outbox flushes so concurrent worker lanes can never double-insert a row.
    private val flushMutex = Mutex()
    private val recentlyEmittedMessageIds = ConcurrentHashMap<String, Long>()

    private suspend fun getOrJoinBroadcastChannel(name: String): io.github.jan.supabase.realtime.RealtimeChannel {
        // Read the cache and channel status under the lock, but never hold the mutex across
        // subscribe(): subscribing may block, and holding it would stall every other channel op.
        val cached = channelMutex.withLock {
            broadcastChannelCache[name]?.let {
                try {
                    if (it.status.value == io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED) {
                        return@withLock it
                    }
                } catch (_: Exception) {}
                // V6-R6 (#3): attribution log — see watchChannelHealth cooldown note.
                ProtocolDiagnostics.log("rt.teardown[$name]: join-evict(not-SUBSCRIBED)")
                runCatchingCE { realtime.removeChannel(it) }
            }
            null
        }
        if (cached != null) return cached

        val channel = supabase.channel(name)
        channel.subscribe()

        // Re-check under the lock before inserting: another lane may have joined the same
        // channel while we were subscribing. The double-subscribe race is acceptable; the
        // cache keeps a single entry, preferring the one that is already SUBSCRIBED.
        return channelMutex.withLock {
            val concurrent = broadcastChannelCache[name]
            if (concurrent != null) {
                try {
                    if (concurrent.status.value == io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED) {
                        // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
                        ProtocolDiagnostics.log("rt.teardown[$name]: join-race-loser")
                        runCatchingCE { realtime.removeChannel(channel) }
                        return@withLock concurrent
                    }
                } catch (_: Exception) {}
            }
            broadcastChannelCache[name] = channel
            channel
        }
    }

    private suspend fun removeCachedChannel(name: String, channel: io.github.jan.supabase.realtime.RealtimeChannel) {
        channelMutex.withLock {
            if (broadcastChannelCache[name] === channel) broadcastChannelCache.remove(name)
        }
        // V6-R6 (#3): every teardown names its reason — the "SUBSCRIBED → UNSUBSCRIBING
        // within 70ms" flap in field logs was unattributable without this.
        ProtocolDiagnostics.log("rt.teardown[$name]: collector-cancelled")
        // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
        runCatchingCE { realtime.removeChannel(channel) }
    }

    /**
     * V6-R3 FIX (field report "realtime dead until chat re-entry"): hosted realtime
     * silently drops channels on network switches, socket idle, or device sleep —
     * Postgres flows then never emit again while collectors stay attached. The
     * client keeps the SAME channel object usable, so watching [RealtimeChannel.status]
     * and re-subscribing that instance resumes delivery without rebuilding any
     * collector. Bounded retries; the returned job must be cancelled before
     * [removeCachedChannel] during teardown (otherwise it would resurrect the channel).
     */
    private fun watchChannelHealth(
        scope: kotlinx.coroutines.CoroutineScope,
        name: String,
        channel: io.github.jan.supabase.realtime.RealtimeChannel,
    ): kotlinx.coroutines.Job = scope.launch {
        var wasSubscribed = false
        var attempts = 0
        var prevStatus: io.github.jan.supabase.realtime.RealtimeChannel.Status? = null
        // V6-R6 (#3): minimum interval between our own subscribe attempts. Field logs
        // showed a teardown war (subscribe → instant remove → resubscribe, ~500ms
        // cycles) that this cooldown defuses while the attribution logs name the
        // real canceller.
        var lastSubscribeAttemptAtMs = 0L
        channel.status.collect { st ->
            // V6-R5 (#3): every transition lands in the diagnostics buffer — a dead
            // lane with a healthy-looking status is exactly what the exports must show.
            if (st != prevStatus) {
                ProtocolDiagnostics.log("rt.status[$name]: $prevStatus → $st")
                prevStatus = st
            }
            when {
                st == io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED -> {
                    wasSubscribed = true
                    attempts = 0
                    lastSubscribeAttemptAtMs = System.currentTimeMillis()
                }
                st == io.github.jan.supabase.realtime.RealtimeChannel.Status.UNSUBSCRIBED &&
                    wasSubscribed && attempts < MAX_RESUBSCRIBE_ATTEMPTS -> {
                    // V6-R6 FIX: the previous skip-when-too-soon logic left the lane
                    // PERMANENTLY dead when a teardown landed inside the cooldown
                    // window (status never transitions again → collector idles).
                    // Cooldown is now a delayed retry — always recovers.
                    attempts++
                    val now = System.currentTimeMillis()
                    val earliest = lastSubscribeAttemptAtMs + RESUBSCRIBE_MIN_INTERVAL_MS
                    if (earliest > now) kotlinx.coroutines.delay(earliest - now)
                    lastSubscribeAttemptAtMs = System.currentTimeMillis()
                    ProtocolDiagnostics.increment("rt.resubscribe")
                    android.util.Log.w("WhisperRepo", "realtime channel $name dropped — resubscribing ($attempts)")
                    kotlinx.coroutines.delay(500L * attempts)
                    runCatchingCE { channel.subscribe(blockUntilSubscribed = false) }
                        .onFailure { ProtocolDiagnostics.increment("rt.resubscribeFail") }
                }
            }
        }
    }

    /** Broadcast and Postgres changes describe the same write; emit it only once to consumers. */
    private fun shouldEmitMessage(id: String): Boolean {
        if (id.isBlank()) return true
        val now = System.currentTimeMillis()
        val previous = recentlyEmittedMessageIds.putIfAbsent(id, now)
        if (recentlyEmittedMessageIds.size > MAX_RECENT_EVENT_IDS) {
            // Age-based eviction keeps the map bounded; entries older than 30s are stale
            // enough that a duplicate may safely be emitted again.
            recentlyEmittedMessageIds.entries.removeIf { (_, timestamp) -> now - timestamp > EVENT_DEDUPE_TTL_MS }
            // If still over the cap (burst of long-lived ids), drop the oldest third
            // regardless of age so the map can never grow unbounded.
            if (recentlyEmittedMessageIds.size > MAX_RECENT_EVENT_IDS) {
                val idsByAge = recentlyEmittedMessageIds.entries.sortedBy { it.value }
                idsByAge.take(idsByAge.size / 3).forEach { recentlyEmittedMessageIds.remove(it.key) }
            }
        }
        if (previous == null) return true
        if (now - previous <= EVENT_DEDUPE_TTL_MS) return false
        recentlyEmittedMessageIds[id] = now
        return true
    }

    fun invalidateConversationsCache() {
        conversationsCache = null
    }

    /**
     * L-4: performs up to [times] TOTAL attempts (the final attempt happens after the
     * loop). 4xx (except 408/429) and CancellationException abort immediately.
     */
    private suspend fun <T> retryWithBackoff(
        times: Int = 3,
        initialDelayMs: Long = 300,
        maxDelayMs: Long = 1200,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                // V2-FIX (reviewwhisper.md) L-16: only transport-level faults are worth a
                // retry. Deterministic failures (malformed payload / serialization /
                // bad argument) and CancellationException must surface immediately.
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e is kotlinx.serialization.SerializationException) throw e
                if (e is IllegalArgumentException) throw e
                if (e is io.github.jan.supabase.exceptions.RestException && e.statusCode in 400..404) throw e
                if (e !is java.io.IOException) throw e
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
            }
        }
        return block()
    }

    // PROFILES
    suspend fun getMyProfile(forceRefresh: Boolean = false): Result<WhisperProfile> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) error("User not authenticated")

        if (!forceRefresh) {
            cachedProfile(currentId)?.let { cached ->
                if (cached.publicKey != null) return Result.success(cached)
            }
        }

        val existing = db.from("profiles")
            .select { filter { eq("id", currentId) } }
            .decodeList<WhisperProfile>()
            .firstOrNull()

        val pubKey = crypto.getPublicKeyBase64()

        val profile = if (existing == null) {
            val user = supabase.auth.currentUserOrNull()
            val metadata = user?.userMetadata
            // V2-FIX (reviewwhisper.md) L-18: jsonPrimitive.contentOrNull instead of
            // removeSurrounding("\"") — correctly handles JSON null, escapes and
            // values that legitimately contain quote characters.
            val metaUsername = metadata?.get("username")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it != "null" && it.isNotBlank() }
            val metaDisplayName = metadata?.get("display_name")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it != "null" && it.isNotBlank() }
            val initialUsername = metaUsername ?: "user_${currentId.take(8)}"
            val insertData = WhisperProfileInsert(
                id = currentId,
                username = initialUsername,
                displayName = metaDisplayName,
                isPrivate = false,
                publicKey = pubKey
            )
            db.from("profiles").insert(insertData) { defaultToNull = false }
            WhisperProfile(id = currentId, username = initialUsername, displayName = metaDisplayName, isPrivate = false, publicKey = pubKey)
        } else {
            val user = supabase.auth.currentUserOrNull()
            val metadata = user?.userMetadata
            // V2-FIX (reviewwhisper.md) L-18: safe JSON access instead of quote stripping.
            val metaDisplayName = metadata?.get("display_name")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it != "null" && it.isNotBlank() }

            // Check if username is an ugly 64-char token string and normalize it.
            // V2-FIX (reviewwhisper.md) L-17: only rename when the account looks untouched —
            // require hex length >= 32 AND no display_name set, so a real personalized
            // account can never lose its chosen username over a coincidental hex string.
            val isUglyHexUsername = existing.username.length >= 32 &&
                existing.username.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } &&
                existing.displayName.isNullOrBlank()

            val cleanUsername = if (isUglyHexUsername) {
                android.util.Log.i("WhisperRepo", "Self-healing raw-token username for ${currentId.take(8)}…: renaming to anon_${existing.username.take(6)}")
                "anon_${existing.username.take(6)}"
            } else null
            
            val needsDisplayName = existing.displayName.isNullOrBlank() && !metaDisplayName.isNullOrBlank()
            // Self-heal only when the server has NO key (fresh row after reinstall/clear
            // data). Reinstalling with a DIFFERENT key now flows through the key-change
            // review flow instead of silently clobbering the server's key.
            val needsKey = pubKey != null && existing.publicKey.isNullOrBlank()
            // V4-FIX (field report): reinstall divergence — uninstalling wipes the
            // AndroidKeyStore identity, but this profile row keeps the PREVIOUS
            // install's public key. Every contact then encrypts to a key this device
            // can no longer open ("[Encrypted message]" both directions) and the two
            // fingerprints disagree. Whisper is single-device per account, so when no
            // staged rotation is in flight we republish THIS device's key; contacts
            // receive it as a fresh server update and auto-pin within the freshness
            // window, restoring readability automatically.
            val reinstallDivergence = pubKey != null &&
                !existing.publicKey.isNullOrBlank() &&
                existing.publicKey != pubKey &&
                !crypto.hasStagedAliases()
            ProtocolDiagnostics.log(
                "keyHeal: divergence=$reinstallDivergence hasStaged=${crypto.hasStagedAliases()}"
            )
            if (reinstallDivergence || (!existing.publicKey.isNullOrBlank() && pubKey != null)) {
                // V4-FIX diagnostics: field reports said the heal "did nothing". Log the
                // full decision inputs so any silent blocker is visible in logcat.
                android.util.Log.w(
                    "WhisperRepo",
                    "KeyHeal check: serverKey=${existing.publicKey?.take(12)}… localKey=${pubKey?.take(12)}… " +
                        "equal=${existing.publicKey == pubKey} hasStaged=${crypto.hasStagedAliases()} " +
                        "-> divergence=$reinstallDivergence"
                )
            }

            if (needsKey || reinstallDivergence || cleanUsername != null || needsDisplayName) {
                // Build the body without nulls: a null field would be serialized as
                // "field": null and NULL the server column (e.g. username) on self-heal.
                val body = buildJsonObject {
                    if (needsKey || reinstallDivergence) put("public_key", pubKey!!)
                    cleanUsername?.let { put("username", it) }
                    if (needsDisplayName) put("display_name", metaDisplayName!!)
                    // V5.1: republishing a key MUST look fresh to contacts, or they
                    // classify CHANGED and the manual verify dance returns.
                    if (needsKey || reinstallDivergence) {
                        put("updated_at", java.time.Instant.now().toString())
                    }
                }
                // V6-R6 (#4): a FAILED divergence-republish used to vanish inside the
                // outer runCatching — server kept the stale key, every contact sealed
                // to it, and the reinstall dance returned with zero diagnostic trail.
                runCatching { updateProfileRowWithFreshness(currentId, body) }
                    .onFailure { healError ->
                        ProtocolDiagnostics.increment("heal.republishFailed")
                        ProtocolDiagnostics.log("heal: divergence-republish FAILED: ${healError.message}")
                        throw healError
                    }
                existing.copy(
                    publicKey = if (needsKey || reinstallDivergence) pubKey else existing.publicKey,
                    username = cleanUsername ?: existing.username,
                    displayName = if (needsDisplayName) metaDisplayName else existing.displayName
                )
            } else existing
        }
        cacheProfile(currentId, profile)
        profile
    }

    suspend fun getProfile(userId: String, forceRefresh: Boolean = false): Result<WhisperProfile> = runCatching {
        if (!forceRefresh) {
            cachedProfile(userId)?.let { cached ->
                if (!cached.publicKey.isNullOrBlank() || userId == myId) {
                    cachePeerKey(userId, cached.publicKey)
                    return Result.success(cached)
                }
            }
        }
        val p = db.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingle<WhisperProfile>()
        cacheProfile(userId, p)
        cachePeerKey(userId, p.publicKey)
        p
    }

    suspend fun searchProfiles(query: String): Result<List<WhisperProfile>> = runCatching {
        val q = query.trim()
        // Gate trivial queries: a single-char ilike '%x%' scan per debounce wastes DB
        // cycles and produces noisy result churn while typing.
        if (q.length < 2) return@runCatching emptyList()
        // M-13 FIX (reviewwhisper.md): escape the backslash FIRST — a literal "\" in the
        // query would otherwise neutralize the %/_ escapes that follow it.
        val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        db.from("profiles")
            .select {
                filter {
                    or {
                        ilike("username", "%$escaped%")
                        ilike("display_name", "%$escaped%")
                    }
                    neq("id", myId)
                    eq("hide_from_discover", false)
                    eq("is_private", false)
                }
                limit(30)
            }
            .decodeList()
    }

    suspend fun checkUsernameAvailable(username: String): Result<Boolean> = runCatching {
        val results = db.from("profiles")
            .select { filter { eq("username", username.trim().lowercase()) } }
            .decodeList<WhisperProfile>()
        results.isEmpty()
    }

    suspend fun updateProfile(update: WhisperProfileUpdate): Result<Unit> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) error("User not authenticated")
        val pubKey = crypto.getPublicKeyBase64()
        // Build the body explicitly so unset fields (especially public_key) are OMITTED:
        // supabase-kt serializes nulls by default, and "public_key": null would NULL the
        // server's column. The local key is only pushed when explicitly requested or when
        // the server row has none to begin with — never overwritten with a mismatch.
        val body = buildJsonObject {
            update.username?.let { put("username", it) }
            // Empty strings must reach the server to clear a field; null means "don't touch".
            if (update.displayName != null) put("display_name", update.displayName)
            if (update.bio != null) put("bio", update.bio)
            if (update.avatarUrl != null) put("avatar_url", update.avatarUrl)
            update.isPrivate?.let { put("is_private", it) }
            update.isHiddenFromDiscover?.let { put("hide_from_discover", it) }
            update.lastSeenAt?.let { put("last_seen_at", it) }
            when {
                update.publicKey != null -> put("public_key", update.publicKey!!)
                pubKey != null -> {
                    // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
                    val serverKeyResult = runCatchingCE {
                        db.from("profiles").select { filter { eq("id", currentId) } }
                            .decodeSingleOrNull<WhisperProfile>()?.publicKey
                    }
                    if (serverKeyResult.isSuccess && serverKeyResult.getOrNull().isNullOrBlank()) {
                        put("public_key", pubKey)
                        // V5.1: a first publish is also a key event — keep it fresh.
                        put("updated_at", java.time.Instant.now().toString())
                    }
                }
            }
        }
        updateProfileRowWithFreshness(currentId, body)
        profileCache.remove(currentId); profileCacheTs.remove(currentId)
    }

    suspend fun updateLastSeen(): Result<Unit> = updateLastSeenInternal(backdateMinutes = 0)

    /**
     * V6-R6: instant OFFLINE — backdates last_seen beyond the 120s presence window so
     * partners see "offline / online Xm ago" the moment the app leaves foreground,
     * without waiting for the window to age out. No schema change needed.
     */
    suspend fun goOfflineInstantly(): Result<Unit> = updateLastSeenInternal(backdateMinutes = 3)

    private suspend fun updateLastSeenInternal(backdateMinutes: Long): Result<Unit> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return@runCatching
        val now = java.time.OffsetDateTime.now().minusMinutes(backdateMinutes).toString()
        val body = buildJsonObject { put("last_seen_at", now) }
        db.from("profiles").update(body) {
            filter { eq("id", currentId) }
        }
    }

    suspend fun uploadAvatar(imageBytes: ByteArray, mimeType: String): Result<String> = runCatching {
        val ext = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val path = "$myId/avatar.$ext"
        store.from("whisper-avatars").upload(path, imageBytes) { upsert = true }
        // L-16 FIX (reviewwhisper.md): persist the CLEAN public URL. The old `?t=` cache
        // buster was stored server-side, defeating every OTHER viewer's image cache on
        // each upload; busting is now done at render time from profile.updatedAt.
        val publicUrl = store.from("whisper-avatars").publicUrl(path)
        updateProfile(WhisperProfileUpdate(avatarUrl = publicUrl))
        profileCache.remove(myId); profileCacheTs.remove(myId)
        publicUrl
    }

    suspend fun deleteAvatar(): Result<Unit> = runCatchingCE {
        // V2-FIX (reviewwhisper.md) R-M7: honor the delete-before-null ordering rationale —
        // only null avatar_url after the blob deletion succeeded OR failed because the
        // object is already gone (404/400 treated as success). On a genuine network
        // failure the reference is kept so a retry remains possible.
        val current = db.from("profiles").select { filter { eq("id", myId) } }
            .decodeSingleOrNull<WhisperProfile>()
        current?.avatarUrl?.let { url ->
            // Robust object-path extraction from the public URL:
            //   <origin>/storage/v1/object/public/whisper-avatars/<userId>/avatar.ext?...
            // Parse via URL segments instead of substringAfter so encoded chars,
            // repeated substrings, or query strings can never corrupt the path.
            val path = runCatching {
                val parsed = java.net.URL(url)
                val segments = parsed.path.split("/").filter { it.isNotBlank() }
                val bucketIdx = segments.indexOf("whisper-avatars")
                if (bucketIdx >= 0 && bucketIdx < segments.lastIndex) {
                    segments.drop(bucketIdx + 1).joinToString("/") { java.net.URLDecoder.decode(it, "UTF-8") }
                } else ""
            }.getOrDefault("")
            if (path.isNotBlank()) {
                val blobResult = runCatchingCE { store.from("whisper-avatars").delete(path) }
                blobResult.exceptionOrNull()?.let { e ->
                    val restStatus = (e as? io.github.jan.supabase.exceptions.RestException)?.statusCode
                    val msg = e.message.orEmpty().lowercase()
                    val objectMissing = restStatus == 404 || restStatus == 400 ||
                        msg.contains("404") || msg.contains("not found")
                    if (!objectMissing) throw e
                    android.util.Log.w("WhisperRepo", "deleteAvatar: blob already gone ($path); clearing reference anyway")
                }
            }
        }
        db.from("profiles").update(mapOf("avatar_url" to null as String?)) {
            filter { eq("id", myId) }
        }
        profileCache.remove(myId); profileCacheTs.remove(myId)
    }

    // ─────────────────────────────────────────────────────────────
    // MESSAGES & REALTIME CHAT
    // ─────────────────────────────────────────────────────────────

    private fun conversationKey(userA: String, userB: String): String =
        if (userA < userB) "${userA}_${userB}" else "${userB}_${userA}"

    fun getMessagesFlow(otherUserId: String): Flow<List<WhisperMessage>> {
        val currentId = myId
        if (currentId.isBlank()) return flowOf(emptyList())
        // Room stores only ciphertext. Decrypt at read time with the accepted peer key so
        // nothing plaintext ever lands on disk, while the UI still renders full history.
        return combine(
            messageDao.getMessages(currentId, otherUserId),
            peerKeys.map { it[otherUserId] },
            blockedIds
        ) { entities, inMemoryKey, blocked ->
            val peerKey = keyTrustStore.knownKey(otherUserId) ?: inMemoryKey
            entities
                // Never resurrect delete-for-me tombstones from the Room cache.
                .filterNot { deletedStore.isMessageDeleted(it.id) }
                // Hidden/blocked partners keep only the user's own outgoing rows.
                .filter { it.senderId == currentId || otherUserId !in blocked }
                .map { it.toModel().decryptContent(peerKey) }
        }
    }

    /** Key used to decrypt a partner's cached ciphertext (accepted key, never a changed one). */
    fun getDecryptionKey(peerId: String): String? = peerKeyFor(peerId)

    suspend fun getMessages(otherUserId: String, limit: Int = 100, beforeCreatedAt: String? = null): Result<List<WhisperMessage>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())
        
        val partnerProfile = getProfile(otherUserId, forceRefresh = true).getOrNull()
        // V5.1: converge on the peer's current key before touching cached rows.
        partnerProfile?.publicKey?.let { adoptFreshKeyIfFresh(otherUserId, it) }

        // If I blocked this user, do not load their incoming messages
        val isBlocked = isUserBlockedByMe(otherUserId)

        val rawMessages = retryWithBackoff {
            db.from("messages")
                .select {
                    filter {
                        or {
                            and {
                                eq("sender_id", currentId)
                                eq("receiver_id", otherUserId)
                            }
                            and {
                                eq("sender_id", otherUserId)
                                eq("receiver_id", currentId)
                            }
                        }
                        if (beforeCreatedAt != null) lt("created_at", beforeCreatedAt)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<WhisperMessage>()
        }

        // Filter out locally deleted messages (and the partner's rows while blocked)
        val visibleRaw = rawMessages
            .filter { msg -> (!isBlocked || msg.senderId == currentId) && !deletedStore.isMessageDeleted(msg.id) }

        // Decrypt with the trusted key only (never a changed fresh server key), falling
        // back to a neutral marker instead of leaking raw ciphertext.
        val decryptedMessages = visibleRaw
            .map { msg -> msg.decryptContent(peerKeyFor(otherUserId), partnerProfile?.publicKey) }

        // Fetch reactions for all messages
        val messageIds = decryptedMessages.map { it.id }.filter { it.isNotBlank() }
        // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
        val reactionsMap = runCatchingCE { getReactionsForMessages(messageIds).getOrDefault(emptyMap()) }.getOrDefault(emptyMap())

        val idToMsgMap = decryptedMessages.associateBy { it.id }

        // Enrich with reply-to snippets and reactions
        val finalMessages = decryptedMessages.map { msg ->
            val replySnippet = msg.replyToId?.let { replyId ->
                idToMsgMap[replyId]?.let { target ->
                    val senderName = if (target.senderId == currentId) {
                        "You"
                    } else {
                        partnerProfile?.effectiveName ?: "User"
                    }
                    val content = if (target.content.startsWith("whisper:image:")) {
                        "Image"
                    } else {
                        target.content.take(100)
                    }
                    Pair(content, senderName)
                }
            }
            msg.copy(
                replyToContent = replySnippet?.first,
                replyToSenderName = replySnippet?.second,
                reactions = reactionsMap[msg.id] ?: emptyList()
            )
        }
        
        // Cache fetched messages as ciphertext only (never the decrypted plaintext)
        if (visibleRaw.isNotEmpty()) {
            messageDao.insertMessages(visibleRaw.map { it.toEntity() })
        }

        finalMessages.reversed()
    }

    suspend fun sendMessage(
        receiverId: String,
        content: String,
        replyToId: String? = null
    ): Result<WhisperMessage> = runCatching {
        val currentId = myId
        // V6-R6: every send-failure exit names itself in diagnostics — field exports
        // previously showed only "IllegalStateException" with zero attribution.
        fun sendFail(code: String, detail: String): Nothing {
            ProtocolDiagnostics.increment("send.fail.$code")
            ProtocolDiagnostics.log("send.fail[$code]: $detail")
            error(detail)
        }
        if (currentId.isBlank()) sendFail("noauth", "User not authenticated")
        if (isUserBlockedByMe(receiverId)) {
            sendFail("blocked_by_me", "You have blocked this user. Unblock to send messages.")
        }
        if (isUserBlockedByOther(receiverId)) {
            sendFail("blocked_by_other", "You have been blocked by this user.")
        }
        require(content.isNotBlank() && content.length <= MAX_MESSAGE_CHARS) { "Message must be between 1 and $MAX_MESSAGE_CHARS characters." }
        val receiverProfile = getProfile(receiverId, forceRefresh = true).getOrNull()
        val receiverPubKey = receiverProfile?.publicKey
        val knownKey = keyTrustStore.knownKey(receiverId)
        if (knownKey != null && receiverPubKey != null && knownKey != receiverPubKey) {
            // P0-1 FIX (reviewwhisper.md): previously EVERY key mismatch hard-blocked
            // sending — including routine monthly fleet auto-rotations — bricking
            // conversations until manual review.
            // V6-R4 FIX (field report "texting requires rotate+verify on BOTH devices"):
            // the remaining CHANGED branch still hard-blocked, which turned any stale
            // divergence into a manual dance. Sending now NEVER blocks: the envelope
            // carries copies for EVERY known candidate key (pinned + fresh below), so
            // whichever key the recipient actually controls opens it. The mismatch is
            // surfaced passively via receiveKeyChanged (banner), and the receiver's
            // proof-of-decryption adopts the right key (see decryptRealtimeMessage).
            if (receiveKeyNotified.add("$receiverId|$receiverPubKey")) {
                _receiveKeyChanged.tryEmit(receiverId)
                ProtocolDiagnostics.log("keyDrift: sending with both pinned+fresh candidates to ${receiverId.take(6)}…")
            }
        }
        // V5: encrypt to EVERY recipient key we know (fresh + pinned). Whichever copy
        // matches a key the recipient controls opens the message — key drift can no
        // longer produce unreadable text.
        // V6-R6 (#2): identity triage BEFORE any encryption — a dangling active alias
        // (historic sweep bug) made getPrivateKey() null and failed every seal. Repair
        // inline, then republish so the server row matches the restored identity.
        if (crypto.repairActiveIdentityIfBroken()) {
            ProtocolDiagnostics.log("send: identity repaired — republishing public key")
            appScope.launch { runCatching { republishLocalKeyIfStale() } }
        }
        // V6 negotiation: the format actually sent is chosen per-peer below — v3
        // ratchet frames when a session exists, the V5 envelope ladder otherwise.
        val negotiatedVersion = negotiatedVersionFor(receiverId)
        check(negotiatedVersion <= OUR_PROTOCOL_VERSION) {
            "Peer requires wire version $negotiatedVersion which this client cannot speak yet."
        }
        // V6 (planwhisper.md §3.2): Double Ratchet first; ANY failure falls through to
        // the proven V5 envelope — a handshake problem can never block a message.
        var encryptedPair: Pair<String, String>? = null
        if (shouldUseV3(receiverId)) {
            ProtocolDiagnostics.log("send: negotiated v3 to ${receiverId.take(6)}…")
            encryptedPair = sealWithRatchet(currentId, receiverId, content)
        } else {
            ProtocolDiagnostics.log("send: negotiated v$negotiatedVersion to ${receiverId.take(6)}… (envelope)")
        }
        if (encryptedPair == null) {
            val candidates = recipientKeyCandidates(receiverId).toMutableMap()
            receiverPubKey?.let { candidates.putIfAbsent(WhisperEnvelope.keyId(it), it) }
            if (candidates.isEmpty()) sendFail(
                "no_key",
                "Secure delivery unavailable: no encryption key for this user (server row blank or unreadable).",
            )
            // V6-R7: ride our current public key IN-BAND so the receiver can open this
            // message even if their cached/server view of us is stale or polluted.
            val envelope = WhisperEnvelope.encode(
                candidates.mapNotNull { (kid, pub) ->
                    crypto.encryptMessage(content, pub, currentId, receiverId)?.let { Triple(kid, it.second, it.first) }
                },
                senderPublicKeyBase64 = crypto.getPublicKeyBase64(),
            ) ?: sendFail(
                "encrypt_failed",
                "Secure delivery failed: could not encrypt for any known key.",
            )
            encryptedPair = envelope to IV_ENVELOPE
        }
        // Client-generated UUID makes the insert idempotent: if the server accepted the row
        // but the response was lost, retries and the outbox flush hit the same primary key
        // and are recognized as already-delivered instead of duplicating the message.
        val clientId = java.util.UUID.randomUUID().toString()
        val insert = run {
            WhisperMessageInsert(
                id = clientId,
                senderId = currentId,
                receiverId = receiverId,
                content = encryptedPair.first,
                contentIv = encryptedPair.second,
                replyToId = replyToId
            )
        }
        // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend retry loop.
        val insertedMsg = runCatchingCE {
            retryWithBackoff(times = 4, initialDelayMs = 500, maxDelayMs = 4_000) {
                db.from("messages").insert(insert) { select() }.decodeSingle<WhisperMessage>()
            }
        }.getOrElse {
            // Preserve only ciphertext. The periodic network-constrained worker retries after
            // process death or an offline transition without leaving cleartext on disk.
            val queued = WhisperQueuedMessage(
                clientId = clientId,
                senderId = currentId,
                receiverId = receiverId,
                encryptedContent = encryptedPair.first,
                contentIv = encryptedPair.second,
                replyToId = replyToId,
                createdAt = java.time.Instant.now().toString(),
            )
            outgoingQueue.enqueue(queued)
            deliveryScheduler.scheduleNow()
            
            val pendingMsg = WhisperMessage(
                id = clientId,
                senderId = currentId,
                receiverId = receiverId,
                content = queued.encryptedContent,
                contentIv = queued.contentIv,
                replyToId = replyToId,
                isPending = true,
                createdAt = queued.createdAt
            )
            // Cache pending ciphertext (never plaintext) so Room never holds cleartext;
            // getMessagesFlow decrypts it on read via peerKeyFor.
            messageDao.insertMessage(pendingMsg.toEntity())
            // Return plaintext for immediate UI; only ciphertext is persisted.
            return@runCatching pendingMsg.copy(content = content, contentIv = null)
        }
        
        val clearMsg = insertedMsg.copy(content = content)
        // Cache the successfully sent message as ciphertext only (never the plaintext).
        // getMessagesFlow decrypts it on read using the recipient's accepted public key.
        messageDao.insertMessage(insertedMsg.toEntity())

        // Invalidate conversation cache so the chats list refreshes
        invalidateConversationsCache()

        clearMsg
    }

    /** Encrypts image bytes on-device, then uploads only ciphertext through the authenticated Edge Function. */
    suspend fun sendEncryptedImage(
        receiverId: String,
        imageBytes: ByteArray,
        mimeType: String,
        expiresAfterSeconds: Long? = null,
        replyToId: String? = null,
    ): Result<WhisperMessage> = runCatching {
        require(mimeType in SUPPORTED_IMAGE_TYPES) { "Whisper supports JPEG, PNG, and WebP images." }
        require(expiresAfterSeconds == null || expiresAfterSeconds in MIN_IMAGE_EXPIRY_SECONDS..MAX_IMAGE_EXPIRY_SECONDS) {
            "Disappearing images must expire between 1 minute and 180 days."
        }
        val myIdNow = myId
        if (myIdNow.isBlank()) error("User not authenticated")
        // V2-FIX (reviewwhisper.md) L-15: hoist the same block-status checks sendMessage
        // performs ABOVE the ciphertext upload — a blocked pair previously burned
        // bandwidth storing an encrypted blob host-side before failing on send.
        if (isUserBlockedByMe(receiverId)) {
            error("You have blocked this user. Unblock to send messages.")
        }
        if (isUserBlockedByOther(receiverId)) {
            error("You have been blocked by this user.")
        }
        getProfile(receiverId, forceRefresh = true).getOrNull()?.publicKey
            ?: error("Secure image delivery is unavailable because this user has no encryption key.")
        // V5: kid-tagged envelope over the attachment cipher bytes; the PNG transport is
        // unaware — it just carries the envelope JSON instead of a single ciphertext.
        val imgCandidates = recipientKeyCandidates(receiverId).toMutableMap()
        if (imgCandidates.isEmpty()) error("Secure image delivery is unavailable because this user has no encryption key.")
        val imgEntries = imgCandidates.mapNotNull { (kid, pub) ->
            crypto.encryptAttachment(imageBytes, pub, myIdNow, receiverId)?.let {
                Triple(kid, it.second, android.util.Base64.encodeToString(it.first, android.util.Base64.NO_WRAP))
            }
        }.ifEmpty { error("This image is too large or could not be encrypted.") }
        // V6-R5 FIX (#1): self-addressed copy. The recipient-key copies are only
        // re-openable by the sender while the exact partner pub used stays known —
        // after ANY partner rotation the sender could never display their own sent
        // image again ("very long loading → Couldn't load", sender-only). A copy
        // sealed to OUR OWN current public key makes the sender view deterministic;
        // receivers skip this entry by kid-match (they don't hold our private key).
        val allEntries = buildList {
            addAll(imgEntries)
            crypto.getPublicKeyBase64()?.let { ownPub ->
                crypto.encryptAttachment(imageBytes, ownPub, myIdNow, receiverId)?.let { sealed ->
                    val kid = WhisperEnvelope.keyId(ownPub)
                    if (none { it.first == kid }) {
                        add(Triple(
                            kid,
                            sealed.second,
                            android.util.Base64.encodeToString(sealed.first, android.util.Base64.NO_WRAP),
                        ))
                    }
                }
            }
        }
        val cipherBytes = WhisperEnvelope.encode(allEntries, crypto.getPublicKeyBase64())!!.toByteArray(Charsets.UTF_8)
        val iv = IV_ENVELOPE
        
        val (uploadUrl, attachmentId) = encryptedImageHost.upload(
            cipherBytes = cipherBytes,
            name = "whisper_${System.currentTimeMillis()}",
            expirationSeconds = expiresAfterSeconds,
        ).getOrThrow()
        
        val expiresAt = expiresAfterSeconds?.let { java.time.Instant.now().epochSecond + it }
        val attachment = WhisperImageAttachment(
            url = uploadUrl,
            iv = iv,
            mimeType = mimeType,
            attachmentId = attachmentId,
            expiresAtEpochSeconds = expiresAt,
            sizeBytes = imageBytes.size,
        )
        sendMessage(receiverId, attachment.toMessageContent(), replyToId).getOrThrow()
    }

    suspend fun downloadEncryptedImage(
        attachment: WhisperImageAttachment,
        peerId: String?,
        peerPublicKey: String?,
        // V3-FIX (scoped legacy-AAD retirement): creation time of the message carrying
        // this attachment; scopes crypto's constant-AAD legacy retry to pre-cutoff
        // images only. Default = never-legacy for any caller that cannot date the row.
        messageCreatedAtEpochMs: Long = Long.MAX_VALUE,
    ): Result<ByteArray> = runCatching {
        if (attachment.expiresAtEpochSeconds != null && java.time.Instant.now().epochSecond >= attachment.expiresAtEpochSeconds) {
            error("This disappearing image has expired.")
        }
        // V6-R4: stage timings in the diagnostics buffer — "very long loading then
        // Couldn't load" reports need the failing STAGE, not just the exception class.
        val t0 = System.currentTimeMillis()
        val rawBytes = encryptedImageHost.download(attachment.url).getOrThrow()
        ProtocolDiagnostics.log("img: downloaded ${rawBytes.size}B in ${System.currentTimeMillis() - t0}ms")

        // 1. Attempt to decode via lossless PNG transport (ImgBB host)
        // V3-FIX (task F): thread the device pixel budget through decode() — low-RAM
        // devices now cap at 4 MP instead of the blanket 8 MP default. The default
        // parameter on decode() is kept so unit tests without a Context still compile.
        val decodedPng = WhisperImageCipherTransport.decode(
            rawBytes,
            WhisperImageCipherTransport.maxPixelsForDevice(appContext),
        )
        val candidateCipher = decodedPng ?: rawBytes

        // 2. Decrypt bound to the original direction. The AAD binds (senderId, receiverId),
        // so we MUST try both real orderings: the partner sent it to me (peer, me), or I
        // sent it to the partner (me, peer). decryptAttachment also keeps an internal
        // constant-AAD fallback for legacy rows created before direction binding.
        val myIdNow = myId
        // V3-FIX: forward the message timestamp so the fallback stays pre-cutoff-only.
        fun tryDecrypt(bytes: ByteArray, iv: String?, sender: String, receiver: String, pub: String) =
            crypto.decryptAttachment(bytes, iv ?: attachment.iv, pub, sender, receiver, messageCreatedAtEpochMs)

        // V5.2: try EVERY plausible partner public key, not just the one handed in —
        // stale pins must never decide whether an image opens.
        // V6-R5 FIX (#1): include OUR OWN public key as a candidate — the envelope now
        // carries a self-addressed copy so senders can always re-open their own sends.
        val peerCandidates = buildList {
            add(peerPublicKey)
            peerId?.takeIf { it != myIdNow }?.let {
                add(peerKeys.value[it])
                add(peerKeyFor(it))
            }
            add(crypto.getPublicKeyBase64())
        }.filterNotNull().distinct()

        val envelopeEntries = runCatching {
            WhisperEnvelope.decode(candidateCipher.decodeToString())
                ?: decodedPng?.let { WhisperEnvelope.decode(rawBytes.decodeToString()) }
        }.getOrNull()

        // V6-R7: in-band sender key joins the trial set (same rationale as text path).
        val inBandPub = runCatching {
            WhisperEnvelope.inBandSenderKey(candidateCipher.decodeToString())
                ?: decodedPng?.let { WhisperEnvelope.inBandSenderKey(rawBytes.decodeToString()) }
        }.getOrNull()
        val baseCandidates = (peerCandidates + listOfNotNull(inBandPub)).distinct()

        val ownKid = WhisperEnvelope.ownKid(crypto)

        // V6-R3 FIX (field report "image sending damaged"): after a partner rotates,
        // every LOCAL view of their key can be stale simultaneously. The whole matrix
        // is therefore one retryable attempt; on total failure we force ONE fresh
        // profile fetch and retry before surfacing the honest failure.
        fun attempt(candidates: List<String>): ByteArray? {
            // V6-R5 FIX (#1): explicit DIRECTION PAIRS. The old "sent-by-me" family was
            // Triple(myId, "", pub) whose empty-receiver resolution produced an impossible
            // (me, me) AAD — own sent images only ever opened via the legacy fallback.
            val directions: List<Pair<String, String>> = buildList {
                if (!peerId.isNullOrBlank() && peerId != myIdNow) {
                    add(peerId to myIdNow)  // partner's sends: AAD (partner, me)
                    add(myIdNow to peerId)  // MY sends:        AAD (me, partner)
                }
                add("" to myIdNow)          // legacy constant-direction rows
            }
            return if (envelopeEntries != null) {
                envelopeEntries.firstNotNullOfOrNull { (kid, ivB64, ctB64) ->
                    val bytes = runCatching {
                        android.util.Base64.decode(ctB64, android.util.Base64.NO_WRAP)
                    }.getOrNull() ?: return@firstNotNullOfOrNull null
                    val pubs = when {
                        kid == ownKid || peerId.isNullOrBlank() || peerId == myIdNow -> candidates
                        else -> candidates.filter { WhisperEnvelope.keyId(it) == kid }.ifEmpty { candidates }
                    }
                    pubs.firstNotNullOfOrNull { pub ->
                        directions.firstNotNullOfOrNull { (sender, receiver) ->
                            tryDecrypt(
                                bytes, ivB64,
                                sender.ifEmpty { peerId ?: "" },
                                receiver,
                                pub,
                            )
                        } ?: runCatching {
                            // direct direction attempt with this pub
                            crypto.decryptAttachment(bytes, ivB64, pub, peerId ?: myIdNow, myIdNow, messageCreatedAtEpochMs)
                        }.getOrNull()
                    }
                }
            } else {
                candidates.firstNotNullOfOrNull { pub ->
                    directions.firstNotNullOfOrNull { (sender, receiver) ->
                        tryDecrypt(candidateCipher, null, sender.ifEmpty { peerId ?: "" }, receiver, pub)
                    }
                } ?: decodedPng?.let {
                    candidates.firstNotNullOfOrNull { pub ->
                        directions.firstNotNullOfOrNull { (sender, receiver) ->
                            tryDecrypt(rawBytes, null, sender.ifEmpty { peerId ?: "" }, receiver, pub)
                        }
                    }
                }
            }
        }

        var decrypted = attempt(baseCandidates)
        if (decrypted == null && !peerId.isNullOrBlank() && peerId != myIdNow) {
            val fresh = getProfile(peerId, forceRefresh = true).getOrNull()?.publicKey
            if (fresh != null && fresh !in baseCandidates) {
                ProtocolDiagnostics.log("imageHeal: retrying with freshly fetched partner key")
                decrypted = attempt(baseCandidates + fresh)
            }
        }
        decrypted ?: run {
            ProtocolDiagnostics.increment("image.decryptFail")
            ProtocolDiagnostics.log(
                "img: decrypt FAILED after ${System.currentTimeMillis() - t0}ms — entries=${envelopeEntries?.size ?: 0}, candidates=${peerCandidates.size}, png=${decodedPng != null}",
            )
            error("Unable to decrypt this image on this device.")
        }
    }


    /** True when the server rejected an insert because the row already exists (idempotent client UUID retry). */
    private fun isDuplicateKeyError(e: Throwable): Boolean =
        (e is io.github.jan.supabase.exceptions.RestException && e.statusCode == 409) ||
            e.message?.contains("duplicate key", ignoreCase = true) == true ||
            e.message?.contains("23505") == true

    /**
     * V2-FIX (reviewwhisper.md) L-21: single builder for the authoritative cached row of a
     * delivered queue entry — previously duplicated verbatim in the success and
     * duplicate-key branches and able to drift apart silently.
     */
    private fun deliveredRowFor(queued: WhisperQueuedMessage): WhisperMessage = WhisperMessage(
        id = queued.clientId,
        senderId = queued.senderId,
        receiverId = queued.receiverId,
        content = queued.encryptedContent,
        contentIv = queued.contentIv,
        replyToId = queued.replyToId,
        createdAt = queued.createdAt
    )

    /** Commits a delivered queue entry into the Room cache, replacing the pending ghost row. */
    private suspend fun commitDeliveredRow(queued: WhisperQueuedMessage) {
        runCatchingCE { messageDao.deleteMessage(queued.clientId) }
        runCatchingCE { messageDao.insertMessage(deliveredRowFor(queued).toEntity()) }
    }

    /** Replays only this signed-in user's ciphertext outbox; safe to call repeatedly. */
    suspend fun flushOutgoingMessages(): Int {
        if (myId.isBlank()) return 0
        return flushMutex.withLock {
            var delivered = 0
            outgoingQueue.entries().filter { it.senderId == myId }.forEach { queued ->
                if (queued.attempts >= 8) {
                    android.util.Log.w("WhisperRepo", "Dropping undeliverable queued message after ${queued.attempts} attempts (clientId=${queued.clientId})")
                    outgoingQueue.remove(queued.clientId)
                    // Surface the permanent loss so the UI can toast it instead of silently
                    // swallowing the user's message.
                    outgoingQueue.noteDropped(queued.clientId)
                    return@forEach
                }
                // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
                val result = runCatchingCE {
                    db.from("messages").insert(
                        WhisperMessageInsert(
                            id = queued.clientId,
                            senderId = queued.senderId,
                            receiverId = queued.receiverId,
                            content = queued.encryptedContent,
                            contentIv = queued.contentIv,
                            replyToId = queued.replyToId,
                            createdAt = queued.createdAt,
                        )
                    )
                }
                if (result.isSuccess || result.exceptionOrNull()?.let { isDuplicateKeyError(it) } == true) {
                    if (result.isFailure) {
                        // The row already exists server-side (a prior attempt delivered it but
                        // the response was lost): treat as delivered, never duplicate.
                        android.util.Log.i("WhisperRepo", "Queued message ${queued.clientId} already delivered server-side")
                    }
                    outgoingQueue.remove(queued.clientId)
                    // Remove the pending ghost row from Room and insert authoritative ciphertext
                    // so UI shows it even before getMessages realtime catch-up.
                    commitDeliveredRow(queued)
                    delivered++
                } else {
                    outgoingQueue.replace(queued.copy(attempts = queued.attempts + 1))
                }
            }
            if (delivered > 0) invalidateConversationsCache()
            delivered
        }
    }

    // ─────────────────────────────────────────────────────────────
    // REACTIONS
    // ─────────────────────────────────────────────────────────────

    suspend fun toggleReaction(messageId: String, emoji: String, otherUserId: String? = null): Result<Unit> = runCatching {
        if (messageId.isBlank()) return@runCatching
        val existing = db.from("message_reactions").select {
            filter {
                eq("message_id", messageId)
                eq("user_id", myId)
                eq("emoji", emoji)
            }
        }.decodeList<WhisperMessageReactionRow>().firstOrNull()

        if (existing != null) {
            db.from("message_reactions").delete {
                filter { eq("id", existing.id) }
            }
        } else {
            // V2-FIX (reviewwhisper.md) R-M8: a concurrent device may insert the same
            // reaction between our SELECT and INSERT — duplicate key then means
            // "already reacted", so the toggle must proceed to the DELETE path instead
            // of surfacing an error.
            val inserted = runCatchingCE {
                db.from("message_reactions").insert(
                    WhisperMessageReactionInsert(messageId = messageId, userId = myId, emoji = emoji)
                )
            }
            val insertError = inserted.exceptionOrNull()
            when {
                inserted.isSuccess -> Unit
                insertError != null && isDuplicateKeyError(insertError) -> {
                    db.from("message_reactions").delete {
                        filter {
                            eq("user_id", myId)
                            eq("message_id", messageId)
                            eq("emoji", emoji)
                        }
                    }
                }
                else -> throw insertError ?: IllegalStateException("Reaction toggle failed")
            }
        }

        // V2-FIX (reviewwhisper.md) R-M1: dead reaction_update broadcast removed — it was
        // consumed nowhere and leaked interaction metadata. Reactions propagate via the
        // Postgres Changes subscription in subscribeToChat.
    }

    suspend fun getReactionsForMessages(messageIds: List<String>): Result<Map<String, List<WhisperReactionSummary>>> = runCatching {
        if (messageIds.isEmpty()) return@runCatching emptyMap()
        val rows = db.from("message_reactions").select {
            filter {
                isIn("message_id", messageIds)
            }
        }.decodeList<WhisperMessageReactionRow>()

        val result = mutableMapOf<String, MutableList<WhisperReactionSummary>>()
        val groupedByMsg = rows.groupBy { it.messageId }
        for ((msgId, msgRows) in groupedByMsg) {
            val byEmoji = msgRows.groupBy { it.emoji }
            val summaries = byEmoji.map { (emoji, rList) ->
                WhisperReactionSummary(
                    emoji = emoji,
                    count = rList.size,
                    userIds = rList.map { it.userId },
                    reactedByMe = rList.any { it.userId == myId }
                )
            }
            result[msgId] = summaries.toMutableList()
        }
        result
    }

    // H-5 FIX (reviewwhisper.md): the unused `senderDisplayName` parameter was removed —
    // it invited drift with WhisperTombstone.CONTENT_PREFIX while the server write only
    // ever used DISPLAY_TEXT. The tombstone is written from the single shared constant.
    suspend fun deleteMessageForEveryone(messageId: String, otherUserId: String): Result<Unit> = runCatching {
        // A message still in the local outbox was never delivered server-side: drop it
        // locally instead of attempting a server tombstone that cannot match a row.
        // (Client ids are now plain UUIDs, so also check queue membership directly.)
        if (messageId.startsWith("queued_") || outgoingQueue.entries().any { it.clientId == messageId }) {
            outgoingQueue.remove(messageId)
            messageDao.deleteMessage(messageId)
            invalidateConversationsCache()
            return@runCatching
        }

        // 1. Fetch message to check for attachments. Room holds ciphertext, so decrypt with
        // the accepted peer key before parsing the attachment envelope.
        fun attachmentOf(m: WhisperMessage): WhisperImageAttachment? {
            val raw = if (m.contentIv != null) {
                // V3-FIX: pass the row's createdAt so the legacy fallback stays pre-cutoff-only.
                crypto.decryptMessage(
                    m.content, m.contentIv, peerKeyFor(otherUserId), m.senderId, m.receiverId,
                    WhisperMessageEntity.parseSortEpoch(m.createdAt),
                ) ?: m.content
            } else m.content
            return WhisperImageAttachment.fromMessageContent(raw)
        }
        val message = messageDao.getMessageById(messageId)
        var attachment: WhisperImageAttachment? = message?.let { attachmentOf(it.toModel()) }
        if (attachment == null) {
            // V2-FIX (reviewwhisper.md) R-M3: the Room row may be missing or undecryptable
            // (evicted cache / other device). Own sent rows are RLS-visible — fetch the
            // row server-side and retry decryption before giving up on blob deletion.
            // If still undecryptable, log and continue with the tombstone.
            val remoteRow = runCatchingCE {
                db.from("messages").select {
                    filter { eq("id", messageId); eq("sender_id", myId) }
                    limit(1)
                }.decodeList<WhisperMessage>().firstOrNull()
            }.getOrNull()
            attachment = remoteRow?.let(::attachmentOf)
            if (remoteRow != null && attachment == null) {
                android.util.Log.w("WhisperRepo", "deleteMessageForEveryone: attachment content still undecryptable for $messageId; continuing with tombstone only")
            }
        }
        
        // 2. Update database with tombstone, selecting back the rows it affected so a
        // mismatch (0 rows) is surfaced instead of silently "succeeding".
        val tombstone = WhisperTombstone.DISPLAY_TEXT
        val updated = db.from("messages").update(
            buildJsonObject {
                put("content", tombstone)
                put("content_iv", null as String?)
            }
        ) {
            filter {
                eq("id", messageId)
                eq("sender_id", myId)
            }
            select()
        }.decodeList<WhisperMessage>()
        
        // 3. Erase from local cache only when the server accepted the tombstone.
        if (updated.isNotEmpty()) {
            messageDao.deleteMessage(messageId)
            invalidateConversationsCache()
            // 4. Only now delete remote attachment — if tombstone RLS rejects (0 rows),
            // the blob must remain so a retry can still succeed.
            attachment?.let { att ->
                // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
                runCatchingCE { encryptedImageHost.delete(att.url, att.attachmentId) }
            }
        } else {
            error("This message could not be deleted. It may have been removed already.")
        }
    }

    suspend fun deleteMessageForMe(messageId: String): Result<Unit> = runCatching {
        if (messageId.isBlank()) return@runCatching
        // 1. Mark as deleted locally (commit on IO) and mirror remotely so eviction never resurrects
        deletedStore.markMessageDeletedSuspend(messageId)
        // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
        runCatchingCE { syncDeletedTombstonesRemote(listOf(messageId)) }
        // 2. Erase from local Room cache
        messageDao.deleteMessage(messageId)
        // 3. Invalidate conversation cache
        invalidateConversationsCache()
    }

    suspend fun markMessagesAsRead(senderId: String): Result<Unit> = runCatching {
        db.from("messages").update({ set("is_read", true) }) {
            filter { eq("sender_id", senderId); eq("receiver_id", myId); eq("is_read", false) }
        }
        messageDao.markAsRead(senderId, myId)
    }

    /**
     * Realtime decrypt with TOFU: try the trusted (accepted) key first; only on a FIRST
     * contact (no trusted key yet) fetch the fresh server key once; anything else renders
     * as a neutral placeholder instead of chasing profiles or leaking raw ciphertext.
     */
    private suspend fun decryptRealtimeMessage(msg: WhisperMessage, peerId: String): String {
        // Server rows carry no version column; infer from wire shape.
        val wireVersion = when {
            com.frerox.toolz.data.whisper.session.WhisperV3Codec.isV3(msg.content) ->
                WhisperProtocolConfig.RATCHET_PROTOCOL_VERSION
            WhisperEnvelope.isEnvelope(msg.content) -> WhisperEnvelope.VERSION
            else -> 0
        }
        // V6: only the partner's own frames prove their capability.
        if (wireVersion > 0 && msg.senderId != myId) recordPeerProtocolFloor(peerId, wireVersion)
        if (msg.isDeletedForEveryone) return msg.content
        if (msg.contentIv == null && !WhisperEnvelope.isEnvelope(msg.content)) return msg.content
        // V3-FIX: date the row once — scopes the legacy fallback to pre-cutoff messages.
        val createdAtEpochMs = WhisperMessageEntity.parseSortEpoch(msg.createdAt)
        // V5.1: never give up on the first (possibly stale) pin — retry against a
        // freshly fetched key and ADOPT it when it opens the message. This is the
        // anti-"rotate on both devices" fix for the live path.
        val trusted = peerKeyFor(peerId)
        val direct = decryptUnified(
            rawContent = msg.content,
            ivMark = msg.contentIv,
            partnerId = peerId,
            msgSenderId = msg.senderId,
            msgReceiverId = msg.receiverId,
            createdAtEpochMs = createdAtEpochMs,
        )
        if (direct != null) return direct

        val fresh = getProfile(peerId, forceRefresh = true).getOrNull()?.publicKey
        if (fresh != null && fresh != trusted) {
            val retried = decryptUnified(
                rawContent = msg.content,
                ivMark = msg.contentIv,
                partnerId = peerId,
                msgSenderId = msg.senderId,
                msgReceiverId = msg.receiverId,
                createdAtEpochMs = createdAtEpochMs,
                extraFreshPeerKey = fresh,
            )
            if (retried != null) {
                // V6-R4 FIX: PROOF-based adoption — the fresh key just successfully
                // authenticated a message from this peer, which is stronger evidence
                // than any updated_at heuristic. Pin it immediately so every later
                // send/preview uses the key the partner actually controls (this is
                // what removes the manual rotate+verify dance end-to-end).
                runCatchingCE { keyTrustStore.rememberKey(peerId, fresh) }
                cachePeerKey(peerId, fresh)
                ProtocolDiagnostics.increment("key.adoptedByProof")
                return retried
            }
        }
        // V5.2 out-of-the-box: incoming mail we cannot open means OUR published key is
        // stale — republish now so the partner's NEXT message opens without any manual
        // step. (The current message may stay locked; that ciphertext predates the fix.)
        runCatching {
            if (msg.senderId != myId) republishLocalKeyIfStale()
        }
        return oldKeyPlaceholder()
    }

    /**
     * Dual-Channel Realtime Chat Flow:
     * Combines Supabase Realtime Broadcast (<50ms) + Postgres Changes for messages and reactions.
     */
    fun subscribeToChat(otherUserId: String): Flow<WhisperChatEvent> = callbackFlow {
        // V2-FIX (reviewwhisper.md) R-M4: buffer enlarged (256) so a slow UI collector
        // cannot silently drop realtime events via a failed trySend on the default
        // 64-slot rendezvous channel; every failed delivery is now logged too.
        val currentId = myId
        if (currentId.isEmpty() || otherUserId.isEmpty()) {
            close()
            return@callbackFlow
        }

        val convoKey = conversationKey(currentId, otherUserId)
        // V2-FIX (reviewwhisper.md) R-M2: this Postgres-changes lane previously shared
        // the "chat_<conversationKey>" name with broadcast-lane channels, so two lanes
        // could remove each other's cached channel object (orphaned subscriptions).
        // The chat subscription now owns a distinct "chatpg_" namespace.
        val channelName = "chatpg_$convoKey"

        // CRITICAL: Supabase caches channel objects. If we get a cached channel that's already
        // subscribed, calling postgresChangeFlow will crash. We MUST remove any existing channel
        // first to ensure we start with a clean, unsubscribed channel object.
        channelMutex.withLock {
            broadcastChannelCache[channelName]?.let {
                // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
                ProtocolDiagnostics.log("rt.teardown[$channelName]: pre-subscribe-clean(chatpg)")
                runCatchingCE { realtime.removeChannel(it) }
                broadcastChannelCache.remove(channelName)
            }
        }
        
        val channel = supabase.channel(channelName)

        // V6-R5: shared realtime-activity clock — updated by BOTH postgres collectors
        // below and read by the polling fallback. Declared before the collectors.
        val lastRealtimeEventAtMs = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())

        // Listen for Postgres Changes on messages. Realtime broadcasts are deliberately
        // NOT consumed: they cannot prove sender identity and are therefore never used as
        // a message source, cache fill, or notification trigger.
        //
        // P0-2 FIX (reviewwhisper.md): both flows now carry SERVER-SIDE filters instead of
        // streaming every RLS-visible row of the whole table and filtering client-side.
        // Realtime supports a single-column filter per subscription, so the conversation is
        // expressed as TWO subscriptions on one channel; RLS guarantees rows outside this
        // conversation can never appear under these filters:
        //   • partner → me:      sender_id = otherUserId  (∩ RLS: receiver_id = me)
        //   • me → partner:      receiver_id = otherUserId (∩ RLS: sender_id = me)
        val incomingChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
            // Single-column filter per subscription (API constraint); RLS supplies the
            // receiver_id = auth.uid() half of the conjunction.
            filter("sender_id", FilterOperator.EQ, otherUserId)
        }
        val outgoingChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
            filter("receiver_id", FilterOperator.EQ, otherUserId)
        }
        val pMsgJob = launch {
            merge(incomingChanges, outgoingChanges).collect { action ->
                try {
                    if (action != null) lastRealtimeEventAtMs.set(System.currentTimeMillis())
                    val msg = when (action) {
                        is PostgresAction.Insert -> action.decodeRecord<WhisperMessage>()
                        is PostgresAction.Update -> action.decodeRecord<WhisperMessage>()
                        is PostgresAction.Delete -> action.decodeOldRecord<WhisperMessage>()
                        else -> null
                    }
                    if (msg != null && (
                        (msg.senderId == otherUserId && msg.receiverId == myId) ||
                        (msg.senderId == myId && msg.receiverId == otherUserId)
                    )) {
                        if (isUserBlockedByMe(msg.senderId)) return@collect

                        // Hard server-side row deletion: mirror it in the cache immediately.
                        if (action is PostgresAction.Delete) {
                            launch { messageDao.deleteMessage(msg.id) }
                            // V2-FIX (reviewwhisper.md) R-M4: log failed deliveries instead of dropping silently.
                            if (shouldEmitMessage(msg.id)) {
                                if (trySend(WhisperChatEvent.DeleteEvent(msg.id)).isFailure) {
                                    android.util.Log.w("WhisperRepo", "DeleteEvent dropped for ${msg.id} (collector slow or closed)")
                                }
                            }
                            return@collect
                        }

                        // Decrypt with the trusted key only (TOFU): a changed key can never
                        // silently decrypt material, and decrypt failures never leak ciphertext.
                        val decrypted = decryptRealtimeMessage(msg, otherUserId)

                        val finalMsg = msg.copy(content = decrypted)
                        
                        // Sync ciphertext to cache in background (never the decrypted plaintext).
                        // Tombstones erase the row; delete-for-me tombstones must never resurrect
                        // through a realtime Update, so skipped rows are simply not re-inserted.
                        launch {
                            when {
                                msg.isDeletedForEveryone -> messageDao.deleteMessage(msg.id)
                                deletedStore.isMessageDeleted(msg.id) -> Unit
                                else -> messageDao.insertMessage(msg.toEntity())
                            }
                        }

                        // Tombstone updates bypass the dedupe window (a message emitted
                        // minutes earlier must still surface its deletion), and so do all
                        // UPDATE events: read-receipt flips typically land within seconds of
                        // the INSERT and must never be swallowed by the 30 s dedupe window.
                        if (action is PostgresAction.Update || msg.isDeletedForEveryone || shouldEmitMessage(msg.id)) {
                            // V2-FIX (reviewwhisper.md) R-M4: log failed deliveries instead of dropping silently.
                            if (trySend(WhisperChatEvent.MessageEvent(finalMsg)).isFailure) {
                                android.util.Log.w("WhisperRepo", "MessageEvent dropped for ${finalMsg.id} (collector slow or closed)")
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WhisperRepo", "Postgres message realtime error: ${e.message}", e)
                }
            }
        }

        // Listen for Postgres Changes on message_reactions
        val postgresReactionChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "message_reactions"
        }
        val pReactionJob = launch {
            postgresReactionChanges.collect { action ->
                try {
                    if (action != null) lastRealtimeEventAtMs.set(System.currentTimeMillis())
                    val row = when (action) {
                        is PostgresAction.Insert -> action.decodeRecord<WhisperMessageReactionRow>()
                        is PostgresAction.Update -> action.decodeRecord<WhisperMessageReactionRow>()
                        is PostgresAction.Delete -> action.decodeOldRecord<WhisperMessageReactionRow>()
                        else -> null
                    }
                    if (row != null) {
                        // V2-FIX (reviewwhisper.md) R-M4: log failed deliveries instead of dropping silently.
                        if (trySend(WhisperChatEvent.ReactionEvent(row.messageId, row.userId, row.emoji)).isFailure) {
                            android.util.Log.w("WhisperRepo", "ReactionEvent dropped for ${row.messageId} (collector slow or closed)")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WhisperRepo", "Postgres reaction realtime error: ${e.message}", e)
                }
            }
        }

        channel.subscribe()
        channelMutex.withLock {
            broadcastChannelCache[channelName] = channel
        }
        // V6-R3: auto-heal dropped realtime channels (messages + reactions).
        val healthJob = watchChannelHealth(this, channelName, channel)

        // ── V6-R4 POLLING FALLBACK (field report: live lanes dead until re-entry) ──
        // Websocket delivery must never be a single point of failure. While the
        // channel is NOT subscribed, this lane polls recent rows over REST and emits
        // the SAME events the realtime collector would — new messages, read-receipt
        // flips, tombstones and authoritative reaction snapshots.
        //
        // V6-R5 FIX (#3), two blind spots removed:
        //  1. SEED SWALLOW — the first pass used to silently mark rows "seen", so any
        //     message arriving between screen-open and the first tick was never emitted
        //     (exact "must re-enter the chat" symptom). Rows newer than [flowStartMs]
        //     now ALWAYS emit, even during seeding.
        //  2. STATUS TRUST — polling was skipped while status *claimed* SUBSCRIBED; a
        //     half-dead socket silenced everything. Suppression is now activity-based:
        //     poll whenever no realtime event has landed for >15s.
        val seenState = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Boolean>>()
        val reactionSig = java.util.concurrent.ConcurrentHashMap<String, String>()
        val flowStartMs = System.currentTimeMillis()
        var seeded = false
        val pollJob = launch {
            while (isActive) {
                kotlinx.coroutines.delay(7_000)
                try {
                    // V6-R5: activity-based suppression (see block comment above).
                    if (System.currentTimeMillis() - lastRealtimeEventAtMs.get() < 15_000) {
                        continue
                    }
                    ProtocolDiagnostics.increment("rt.pollTick")
                    val rows = retryWithBackoff(times = 2, initialDelayMs = 200, maxDelayMs = 400) {
                        db.from("messages").select {
                            filter {
                                or {
                                    and { eq("sender_id", currentId); eq("receiver_id", otherUserId) }
                                    and { eq("sender_id", otherUserId); eq("receiver_id", currentId) }
                                }
                            }
                            order("created_at", Order.DESCENDING)
                            limit(30)
                        }.decodeList<WhisperMessage>()
                    }
                    for (msg in rows.reversed()) {
                        if (deletedStore.isMessageDeleted(msg.id)) continue
                        if (msg.isDeletedForEveryone) {
                            if (seenState.remove(msg.id) != null) {
                                messageDao.deleteMessage(msg.id)
                                trySend(WhisperChatEvent.DeleteEvent(msg.id))
                            }
                            continue
                        }
                        val stateKey = msg.content to msg.isRead
                        val prev = seenState.put(msg.id, stateKey)
                        if (prev == stateKey && seeded) continue // unchanged, already emitted
                        if (!seeded && prev == null) {
                            // Seed pass: emit ONLY rows that arrived after the flow
                            // started; pre-existing history was already rendered.
                            val arrivedAfterOpen = runCatching {
                                WhisperMessageEntity.parseSortEpoch(msg.createdAt) > flowStartMs
                            }.getOrDefault(false)
                            if (!arrivedAfterOpen) continue
                        }
                        val decrypted = decryptRealtimeMessage(msg, otherUserId)
                        val finalMsg = msg.copy(content = decrypted)
                        launch {
                            if (!deletedStore.isMessageDeleted(msg.id)) {
                                messageDao.insertMessage(msg.toEntity())
                            }
                        }
                        if (prev != null || shouldEmitMessage(msg.id)) {
                            trySend(WhisperChatEvent.MessageEvent(finalMsg))
                        }
                    }
                    seeded = true
                    // Authoritative reaction snapshots for everything on screen.
                    val ids = seenState.keys.toList()
                    if (ids.isNotEmpty()) {
                        val map = getReactionsForMessages(ids).getOrDefault(emptyMap())
                        for ((mid, summaries) in map) {
                            val sig = summaries.joinToString("|") { s ->
                                "${s.emoji}:${s.userIds.sorted().joinToString(",")}"
                            }
                            if (reactionSig.put(mid, sig) != sig && summaries.isNotEmpty()) {
                                trySend(WhisperChatEvent.ReactionSnapshotEvent(mid, summaries))
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ProtocolDiagnostics.increment("rt.pollError")
                    android.util.Log.w("WhisperRepo", "chat poll failed: ${e.message}")
                }
            }
        }

        awaitClose {
            healthJob.cancel()
            pollJob.cancel()
            pMsgJob.cancel()
            pReactionJob.cancel()
            // H-1 FIX: appScope, not ProducerScope — the flow scope is already cancelled
            // when this callback runs, so a plain launch here would never execute.
            appScope.launch {
                removeCachedChannel(channelName, channel)
            }
        }
    }.buffer(capacity = 256)

    fun subscribeToIncomingMessages(userId: String): Flow<WhisperMessage> = callbackFlow {
        // V2-FIX (reviewwhisper.md) R-M4: buffer enlarged + failed deliveries logged
        // (see subscribeToChat).
        if (userId.isEmpty()) {
            close()
            return@callbackFlow
        }

        val channelName = "whisper-user-inbox-$userId"
        channelMutex.withLock {
            broadcastChannelCache[channelName]?.let {
                // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
                ProtocolDiagnostics.log("rt.teardown[$channelName]: pre-subscribe-clean(inbox)")
                runCatchingCE { realtime.removeChannel(it) }
            }
            broadcastChannelCache.remove(channelName)
        }

        val channel = supabase.channel(channelName)

        // Listen to database messages table changes. Realtime broadcasts are deliberately
        // NOT consumed: they cannot prove sender identity and are never used as a
        // message source or notification trigger.
        //
        // P0-2 FIX: scoped server-side to rows involving [userId] via two single-column
        // filters (Realtime allows one column filter per subscription). RLS constrains
        // visibility, so the pair exactly covers "every message this user participates in"
        // instead of the whole table.
        val asReceiverChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
            filter("receiver_id", FilterOperator.EQ, userId)
        }
        val asSenderChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
            filter("sender_id", FilterOperator.EQ, userId)
        }
        val dbJob = launch {
            merge(asReceiverChanges, asSenderChanges).collect { action ->
                try {
                    val msg = when (action) {
                        is PostgresAction.Insert -> action.decodeRecord<WhisperMessage>()
                        is PostgresAction.Update -> action.decodeRecord<WhisperMessage>()
                        else -> null
                    }
                    if (msg != null && (msg.receiverId == userId || msg.senderId == userId)) {
                        if (isUserBlockedByMe(msg.senderId)) return@collect
                        // Never surface or re-notify a message the user deleted for themselves.
                        if (deletedStore.isMessageDeleted(msg.id)) return@collect

                        val otherId = if (msg.senderId == userId) msg.receiverId else msg.senderId

                        // Decrypt with the trusted key only (TOFU); first contact fetches
                        // the fresh key once, otherwise the placeholder is shown.
                        val decrypted = decryptRealtimeMessage(msg, otherId)

                        // V2-FIX (reviewwhisper.md) R-M4: log failed deliveries instead of dropping silently.
                        if (shouldEmitMessage(msg.id)) {
                            if (trySend(msg.copy(content = decrypted)).isFailure) {
                                android.util.Log.w("WhisperRepo", "Incoming message event dropped for ${msg.id} (collector slow or closed)")
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WhisperRepo", "Realtime collect error: ${e.message}", e)
                }
            }
        }

        channel.subscribe()
        channelMutex.withLock {
            broadcastChannelCache[channelName] = channel
        }
        // V6-R3: auto-heal dropped realtime channels (inbox lane).
        val healthJob = watchChannelHealth(this, channelName, channel)

        awaitClose {
            healthJob.cancel()
            dbJob.cancel()
            // H-1 FIX: see subscribeToChat — cleanup must survive collection cancellation.
            appScope.launch {
                removeCachedChannel(channelName, channel)
            }
        }
    }.buffer(capacity = 256)


    // CLEAR CHAT
    suspend fun clearMessagesForRange(
        otherUserId: String,
        fromIso: String? = null,
        toIso: String? = null
    ): Result<List<WhisperMessage>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())

        // Paginated select to avoid silent truncation (was single limit 500 → 600-msg history left 100 resurrecting).
        // "clear history" must remove partner's messages locally too, not just my sent rows.
        val allToDelete = mutableListOf<WhisperMessage>()
        var page = 0
        while (true) {
            val batch = db.from("messages").select {
                filter {
                    or {
                        and { eq("sender_id", currentId); eq("receiver_id", otherUserId) }
                        and { eq("sender_id", otherUserId); eq("receiver_id", currentId) }
                    }
                    if (fromIso != null) gte("created_at", fromIso)
                    if (toIso != null) lte("created_at", toIso)
                }
                range((page * 500L), ((page + 1) * 500L - 1))
            }.decodeList<WhisperMessage>()
            if (batch.isEmpty()) break
            allToDelete.addAll(batch)
            if (batch.size < 500) break
            page++
            if (page > 100) break // safety cap 50k — covers hoarders, prevents infinite loop on stable feed
        }

        val toDeleteIds = allToDelete.map { it.id }.filter { it.isNotBlank() }
        // V6-R6 SCOPE FIX: clearing history is now STRICTLY LOCAL — the partner's view
        // is untouched. The old flow deleted MY sent rows server-side, which erased
        // them from the partner's chat too (they were my rows, after all). Now:
        //   - local tombstones (per-user, persistent) hide everything on THIS device;
        //   - remote per-user tombstone mirror kept ONLY for this account's own
        //     reinstall consistency (partner never reads another user's rows);
        //   - NO server message deletion at all.
        if (toDeleteIds.isNotEmpty()) {
            // Await durability before erasing Room — otherwise reload could resurrect.
            deletedStore.markMessagesDeletedSuspend(toDeleteIds)
            runCatchingCE { syncDeletedTombstonesRemote(toDeleteIds) }
            toDeleteIds.forEach { messageDao.deleteMessage(it) }
        }
        invalidateConversationsCache()
        allToDelete
    }

    // ── Server tombstones: mirror local deletes so eviction never resurrects ──
    @Serializable
    private data class TombstoneRow(@SerialName("message_id") val messageId: String)

    private suspend fun syncDeletedTombstonesRemote(messageIds: List<String>) {
        if (messageIds.isEmpty() || myId.isBlank()) return
        val rows = messageIds.filter { it.isNotBlank() }.map { mapOf("user_id" to myId, "message_id" to it) }
        // Upsert idempotently; ignore RLS/unique errors (best-effort, local is authoritative)
        for (chunk in rows.chunked(200)) {
            // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
            runCatchingCE { db.from("whisper_deleted_tombstones").upsert(chunk) }
        }
    }

    private suspend fun removeRemoteTombstones(messageIds: List<String>) {
        if (messageIds.isEmpty() || myId.isBlank()) return
        for (chunk in messageIds.filter { it.isNotBlank() }.chunked(200)) {
            // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
            runCatchingCE { db.from("whisper_deleted_tombstones").delete { filter { isIn("message_id", chunk) } } }
        }
    }

    suspend fun pullRemoteTombstones(): Result<Unit> = runCatchingCE {
        if (myId.isBlank()) return@runCatchingCE
        // V2-FIX (reviewwhisper.md) L-19: paginate — a >500-row tombstone table used to be
        // silently truncated, resurrecting cleared messages after reinstall.
        var page = 0
        while (true) {
            val rows = db.from("whisper_deleted_tombstones").select {
                filter { eq("user_id", myId) }
                range(page * 500L, ((page + 1) * 500L - 1))
            }.decodeList<TombstoneRow>()
            if (rows.isEmpty()) break
            deletedStore.markMessagesDeletedSuspend(rows.map { it.messageId })
            if (rows.size < 500) break
            page++
        }
    }

    suspend fun restoreMessages(messages: List<WhisperMessage>): Result<Unit> = runCatching {
        if (messages.isEmpty()) return@runCatching
        // P2 FIX: Ensure caller passes ciphertext (undo path does). Plaintext must never resurrect.
        messages.forEach { msg ->
            require(msg.contentIv != null || WhisperTombstone.isTombstone(msg.content) || msg.content == WhisperTombstone.LEGACY_ENCRYPTED) {
                "restoreMessages requires ciphertext: id=${msg.id} has plain content without IV/tombstone"
            }
        }
        // RLS only allows inserting rows I authored (sender_id = auth.uid()), so a clear-chat
        // undo can NEVER re-insert the partner's rows server-side. Order matters:
        //  1. Re-insert MY rows first — if this fails (offline etc.) nothing else changed,
        //     so the undo bar stays alive and the user can retry within the window.
        //  2. Remove my tombstones for ALL ids — always permitted by RLS (own rows), and
        //     required so eviction/reinstall cannot re-delete what we just restored.
        //  3. Restore locally: partner rows resurrect from the Room cache only.
        val mine = messages.filter { it.senderId == myId }
        if (mine.isNotEmpty()) {
            fun insertFor(msg: WhisperMessage) = WhisperMessageInsert(
                id = msg.id,
                senderId = msg.senderId,
                receiverId = msg.receiverId,
                content = msg.content,
                contentIv = msg.contentIv,
                isRead = msg.isRead,
                createdAt = msg.createdAt
            )
            // V2-FIX (reviewwhisper.md) R-M5: make restore idempotent — a duplicate-key
            // error means some/all of MY rows already exist server-side from an earlier
            // attempt; replay one row at a time so genuinely-new rows still land while
            // duplicates are treated as already-done instead of failing the batch.
            val batchInsert = runCatchingCE { db.from("messages").insert(mine.map { insertFor(it) }) }
            val batchError = batchInsert.exceptionOrNull()
            when {
                batchInsert.isSuccess -> Unit
                batchError != null && isDuplicateKeyError(batchError) ->
                    mine.forEach { msg ->
                        val single = runCatchingCE { db.from("messages").insert(listOf(insertFor(msg))) }
                        val singleError = single.exceptionOrNull()
                        if (singleError != null && !isDuplicateKeyError(singleError)) throw singleError
                    }
                else -> throw batchError ?: IllegalStateException("restoreMessages failed")
            }
        }
        removeRemoteTombstones(messages.map { it.id })
        deletedStore.unmarkMessagesDeleted(messages.map { it.id })
        // Restore ALL rows locally (mine + partner's) from the persisted undo buffer;
        // partner rows live only on this device since RLS forbids re-inserting them server-side.
        // M-12 FIX (reviewwhisper.md): restored partner rows are marked unread — the old
        // code resurrected stale read receipts that had already been reported server-side.
        messageDao.insertMessages(messages.map { msg ->
            if (msg.senderId == myId) msg.toEntity() else msg.copy(isRead = false).toEntity()
        })
        invalidateConversationsCache()
    }

    /**
     * Resolves partner profiles (and the blocker set) in ONE query pair instead of an
     * N+1 fan of per-user requests. Keeps the same in-memory side effects as getProfile
     * (profile cache + peer key cache) so decryption behavior is unchanged.
     */
    private suspend fun batchProfilesById(userIds: List<String>): Pair<Set<String>, Map<String, WhisperProfile>> {
        if (userIds.isEmpty()) return emptySet<String>() to emptyMap()
        val blockedPartnerIds = runCatching {
            db.from("whisper_blocks").select { filter { eq("blocker_id", myId) } }
                .decodeList<WhisperBlock>().map { it.blockedId }.toSet()
        }.getOrDefault(emptySet())
        // V2-FIX (reviewwhisper.md) R-H2: a failed profile batch query must NOT be conflated
        // with an empty result — that silently rendered every conversation/friend as missing
        // ("empty hub") on transient network errors. The exception now propagates so the
        // Result-returning callers (getConversations/getFriends/…) report failure instead.
        val profilesById = db.from("profiles").select { filter { isIn("id", userIds) } }
            .decodeList<WhisperProfile>().associateBy { it.id }
        profilesById.values.forEach { profile ->
            cacheProfile(profile.id, profile)
            cachePeerKey(profile.id, profile.publicKey)
        }
        return blockedPartnerIds to profilesById
    }

    suspend fun getConversations(forceRefresh: Boolean = false): Result<List<WhisperConversation>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())

        val now = System.currentTimeMillis()
        if (!forceRefresh) conversationsCache?.let { cached ->
            if (now - cached.at < CONVERSATIONS_CACHE_TTL) return Result.success(cached.value)
        }
        @Serializable
        data class ConvRow(
            @SerialName("partner_id") val partnerId: String = "",
            @SerialName("last_content") val lastContent: String = "",
            @SerialName("last_content_iv") val lastContentIv: String? = null,
            @SerialName("last_created_at") val lastCreatedAt: String = "",
            @SerialName("unread_count") val unreadCount: Long = 0,
        )

        // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around the suspend RPC call.
        val rows = runCatchingCE {
            db.rpc("get_conversations", buildJsonObject { put("p_user_id", currentId) })
                .decodeList<ConvRow>()
        }.getOrNull()

        if (rows != null) {
            // Batch the block + profile lookups into single queries instead of N+1 per row.
            val (blockedPartnerIds, profilesById) = batchProfilesById(rows.map { it.partnerId })
            val conversations = mutableListOf<WhisperConversation>()
            for (row in rows) {
                if (row.partnerId in blockedPartnerIds) continue
                val profile = profilesById[row.partnerId] ?: continue
                val decryptedContent = if (WhisperTombstone.isTombstone(row.lastContent)) {
                    WhisperTombstone.DISPLAY_TEXT
                } else if (row.lastContentIv != null && profile.publicKey != null) {
                    // TOFU-consistent with getMessages: prefer the ACCEPTED trusted key so a
                    // changed (possibly malicious) server key can never silently decrypt the
                    // preview; fall back to the fresh key only on true first contact.
                    // M-2 FIX: routed through the memoized decrypt — the double direction
                    // attempt used to cost two full ECDH derives per conversation row.
                    // V3-FIX: lastCreatedAt scopes the legacy fallback to pre-cutoff rows.
                    // V6-R2: previewKey removed — decryptUnified resolves keys internally;
                    // the variable was dead and its TOFU comment misleading.
                    val createdAtEpochMs = WhisperMessageEntity.parseSortEpoch(row.lastCreatedAt)
                    decryptUnified(row.lastContent, row.lastContentIv, row.partnerId, row.partnerId, myId, createdAtEpochMs)
                        ?: decryptUnified(row.lastContent, row.lastContentIv, row.partnerId, myId, row.partnerId, createdAtEpochMs)
                        ?: WhisperTombstone.LOCKED_PLACEHOLDER
                } else if (row.lastContentIv != null) {
                    WhisperTombstone.LOCKED_PLACEHOLDER
                } else WhisperTombstone.LEGACY_ENCRYPTED

                // V2-FIX (reviewwhisper.md) L-24: renamed — "fake" implied mock/test data.
                val previewMsg = WhisperMessage(
                    id = "",
                    senderId = row.partnerId,
                    receiverId = myId,
                    content = decryptedContent,
                    isRead = row.unreadCount == 0L,
                    createdAt = row.lastCreatedAt,
                )
                conversations.add(WhisperConversation(profile, previewMsg, row.unreadCount.toInt()))
            }
            conversations.also { result ->
                conversationsCache = ConversationsCacheEntry(result, System.currentTimeMillis())
            }
        } else {
            // Fallback when RPC is unavailable: paginate instead of truncating at 200, otherwise
            // users with >200 messages silently lose conversations.
            // P1-13 FIX: Cap raised from 2000 to 10000 (20 pages) to cover power users.
            val allMessages = mutableListOf<WhisperMessage>()
            var fbPage = 0
            while (allMessages.size < 10000) {
                val batch = db.from("messages")
                    .select {
                        filter {
                            or {
                                eq("sender_id", myId)
                                eq("receiver_id", myId)
                            }
                        }
                        order("created_at", Order.DESCENDING)
                        range((fbPage * 500L), ((fbPage + 1) * 500L - 1))
                    }
                    .decodeList<WhisperMessage>()
                if (batch.isEmpty()) break
                allMessages.addAll(batch)
                if (batch.size < 500) break
                fbPage++
                if (fbPage > 19) break // cap 10000, enough to rebuild convos for hoarders
            }

            val grouped = allMessages.groupBy { msg ->
                if (msg.senderId == myId) msg.receiverId else msg.senderId
            }
            // Batch the block + profile lookups into single queries instead of N+1 per row.
            val (blockedPartnerIds, profilesById) = batchProfilesById(grouped.keys.toList())
            val conversations = mutableListOf<WhisperConversation>()
            for ((partnerId, msgs) in grouped) {
                if (partnerId in blockedPartnerIds) continue
                val visibleMsgs = msgs.filter { !deletedStore.isMessageDeleted(it.id) }
                if (visibleMsgs.isEmpty()) continue
                val profile = profilesById[partnerId] ?: continue
                val lastMsg = visibleMsgs.first()
                // TOFU-consistent preview decryption (see RPC path above).
                val decryptedContent = if (lastMsg.isDeletedForEveryone) {
                    WhisperTombstone.DISPLAY_TEXT
                } else if (lastMsg.contentIv != null && (peerKeyFor(partnerId) ?: profile.publicKey) != null) {
                    // V2-FIX (reviewwhisper.md) L-22: route the fallback preview decryption
                    // through decryptMemoized like the RPC path — every hub rebuild used to
                    // pay a full ECDH derive per conversation for the same cached ciphertext.
                    // Actual direction first, then the reversed fallback for legacy rows.
                    // V3-FIX: createdAt scopes the legacy fallback to pre-cutoff rows.
                    val previewEpochMs = WhisperMessageEntity.parseSortEpoch(lastMsg.createdAt)
                    decryptUnified(lastMsg.content, lastMsg.contentIv, partnerId, lastMsg.senderId, lastMsg.receiverId, previewEpochMs)
                        ?: decryptUnified(lastMsg.content, lastMsg.contentIv, partnerId, lastMsg.receiverId, lastMsg.senderId, previewEpochMs)
                        ?: WhisperTombstone.LOCKED_PLACEHOLDER
                } else if (lastMsg.contentIv != null) {
                    WhisperTombstone.LOCKED_PLACEHOLDER
                } else WhisperTombstone.LEGACY_ENCRYPTED

                val unread = visibleMsgs.count { it.receiverId == myId && !it.isRead }
                conversations.add(WhisperConversation(profile, lastMsg.copy(content = decryptedContent), unread))
            }
            conversations.sortedByDescending { it.lastMessage.createdAt }
        }.also { result ->
            conversationsCache = ConversationsCacheEntry(result, System.currentTimeMillis())
        }
    }

    // FRIENDS
    suspend fun getFriendships(): Result<List<WhisperFriendship>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())
        db.from("friends").select {
            filter { or { eq("user_a", currentId); eq("user_b", currentId) } }
        }.decodeList()
    }

    suspend fun getFriends(): Result<List<WhisperProfile>> = runCatching {
        val friendships = getFriendships().getOrThrow().filter { it.status == "accepted" }
        // Batch profile resolution into one query instead of N+1 per friendship.
        val partnerIds = friendships.map { it.otherUserId(myId) }.distinct()
        if (partnerIds.isEmpty()) return@runCatching emptyList()
        val (_, profilesById) = batchProfilesById(partnerIds)
        friendships.mapNotNull { profilesById[it.otherUserId(myId)] }
    }

    suspend fun getPendingIncoming(): Result<List<WhisperFriendship>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())
        db.from("friends").select { filter { eq("user_b", currentId); eq("status", "pending") } }.decodeList()
    }

    suspend fun getPendingIncomingWithProfiles(): Result<List<WhisperFriendRequestItem>> = runCatching {
        val pending = getPendingIncoming().getOrThrow()
        // Batch profile resolution into one query instead of N+1 per request.
        val senderIds = pending.map { it.userA }.distinct()
        if (senderIds.isEmpty()) return@runCatching emptyList()
        val (_, profilesById) = batchProfilesById(senderIds)
        pending.map { f ->
            WhisperFriendRequestItem(friendship = f, senderProfile = profilesById[f.userA])
        }
    }

    suspend fun getPendingOutgoing(): Result<List<WhisperFriendship>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())
        db.from("friends").select { filter { eq("user_a", currentId); eq("status", "pending") } }.decodeList()
    }

    suspend fun sendFriendRequest(targetUserId: String): Result<Unit> = runCatching {
        if (targetUserId == myId) return@runCatching
        if (isUserBlockedByMe(targetUserId)) error("You have blocked this user. Unblock to send a friend request.")
        if (isUserBlockedByOther(targetUserId)) error("You can't send a friend request to a user who blocked you.")
        val existingPair = getFriendshipStatus(targetUserId).getOrNull()
        val (status, record) = existingPair ?: Pair(FriendStatus.NONE, null)

        if (status == FriendStatus.ACCEPTED) return@runCatching
        if (record != null) {
            if (record.userA == targetUserId && record.userB == myId) {
                acceptFriendRequest(record.id).getOrThrow()
                return@runCatching
            }
            if (record.userA == myId && record.userB == targetUserId) return@runCatching
        }
        // V2-FIX (reviewwhisper.md) R-M1: the "friend_update"/"incoming_notification"
        // broadcast emissions were removed — they were consumed nowhere (subscribe flows
        // use authenticated Postgres Changes only) and leaked request metadata to anyone
        // who could guess the channel name. DB state remains the single source of truth.
        db.from("friends").insert(WhisperFriendshipInsert(userA = myId, userB = targetUserId))
    }

    suspend fun acceptFriendRequest(friendshipId: String): Result<Unit> = runCatching {
        val existing = db.from("friends").select { filter { eq("id", friendshipId) } }
            .decodeSingleOrNull<WhisperFriendship>() ?: error("This friend request no longer exists.")
        // Only the recipient of a pending request may accept it.
        if (existing.userB != myId) error("You can only accept friend requests sent to you.")
        if (existing.status != "pending") return@runCatching

        db.from("friends").update({ set("status", "accepted") }) { filter { eq("id", friendshipId) } }

        // L-1 FIX: removed the dead `if (existing != null)` wrapper — existing is
        // guaranteed non-null by the decodeSingleOrNull ?: error() above.
        val uA = existing.userA
        val uB = existing.userB
        val otherId = if (uA == myId) uB else uA

        // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
        runCatchingCE {
            val friendsChannel = getOrJoinBroadcastChannel("whisper-friends-all-$otherId")
            friendsChannel.broadcast(
                event = "friend_update",
                payload = BroadcastPayload.Json(
                    buildJsonObject {
                        put("id", friendshipId)
                        put("user_a", uA)
                        put("user_b", uB)
                        put("status", "accepted")
                    }
                )
            )
        }

        runCatching {
            db.from("friends").delete {
                filter {
                    neq("id", friendshipId)
                    or {
                        and { eq("user_a", uA); eq("user_b", uB) }
                        and { eq("user_a", uB); eq("user_b", uA) }
                    }
                }
            }
        }
    }

    suspend fun deleteFriendship(friendshipId: String): Result<Unit> = runCatching {
        val existing = db.from("friends").select { filter { eq("id", friendshipId) } }
            .decodeSingleOrNull<WhisperFriendship>() ?: error("This friendship no longer exists.")
        if (existing.userA != myId && existing.userB != myId) {
            error("This friendship is not yours to remove.")
        }
        db.from("friends").delete { filter { eq("id", friendshipId) } }
        // L-1 FIX: dead null-check removed (same as acceptFriendRequest).
        run {
            val otherId = if (existing.userA == myId) existing.userB else existing.userA
            // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
            runCatchingCE {
                val friendsChannel = getOrJoinBroadcastChannel("whisper-friends-all-$otherId")
                friendsChannel.broadcast(
                    event = "friend_update",
                    payload = BroadcastPayload.Json(
                        buildJsonObject {
                            put("id", friendshipId)
                            put("user_a", existing.userA)
                            put("user_b", existing.userB)
                            put("status", "deleted")
                        }
                    )
                )
            }
        }
    }

    suspend fun getFriendshipStatus(otherUserId: String): Result<Pair<FriendStatus, WhisperFriendship?>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(Pair(FriendStatus.NONE, null))
        val records = db.from("friends").select {
            filter {
                or {
                    and { eq("user_a", currentId); eq("user_b", otherUserId) }
                    and { eq("user_a", otherUserId); eq("user_b", currentId) }
                }
            }
        }.decodeList<WhisperFriendship>()

        val accepted = records.firstOrNull { it.status == "accepted" }
        if (accepted != null) return@runCatching Pair(FriendStatus.ACCEPTED, accepted)

        val pending = records.firstOrNull { it.status == "pending" }
        if (pending != null) return@runCatching Pair(FriendStatus.PENDING, pending)

        Pair(records.firstOrNull()?.friendStatus() ?: FriendStatus.NONE, records.firstOrNull())
    }

    fun subscribeToFriendUpdates(): Flow<WhisperFriendship> = callbackFlow {
        val currentId = myId
        if (currentId.isBlank()) {
            close()
            return@callbackFlow
        }
        val channelName = "whisper-friends-all-$currentId"
        channelMutex.withLock {
            broadcastChannelCache[channelName]?.let {
                // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
                ProtocolDiagnostics.log("rt.teardown[$channelName]: pre-subscribe-clean(friends)")
                runCatchingCE { realtime.removeChannel(it) }
                broadcastChannelCache.remove(channelName)
            }
        }
        val channel = supabase.channel(channelName)

        // H-8 FIX (reviewwhisper.md): realtime BROADCASTS on this channel are no longer
        // consumed. Broadcasts cannot prove sender identity — any user who knows the
        // channel name could fabricate friendship state (fake requests/acceptances) and
        // spoof the UI until a Postgres event corrected it. Postgres Changes below ARE
        // authenticated (RLS-filtered, publication-wired with REPLICA IDENTITY FULL)
        // and are the single authoritative source for friendship updates.

        // Postgres Change flow fallback
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "friends" }
        val pJob = launch {
            changes.collect { action ->
                try {
                    val record = when (action) {
                        is PostgresAction.Insert -> action.decodeRecord<WhisperFriendship>()
                        is PostgresAction.Update -> action.decodeRecord<WhisperFriendship>()
                        else -> null
                    }
                    if (record != null && (record.userA == currentId || record.userB == currentId)) {
                        trySend(record)
                    }
                } catch (_: Exception) { }
            }
        }
        channel.subscribe()
        channelMutex.withLock {
            broadcastChannelCache[channelName] = channel
        }
        // V6-R3: auto-heal dropped realtime channels (friend updates).
        val healthJob = watchChannelHealth(this, channelName, channel)
        awaitClose {
            healthJob.cancel()
            pJob.cancel()
            // H-1 FIX: see subscribeToChat — cleanup must survive collection cancellation.
            appScope.launch {
                removeCachedChannel(channelName, channel)
            }
        }
    }


    private val blockedByMeCache = Collections.synchronizedSet(mutableSetOf<String>())

    // H-6 FIX (reviewwhisper.md): full blocker-set cache with TTL. Previously every
    // incoming realtime message event for a NON-blocked sender triggered a
    // whisper_blocks REST round-trip (negatives were never cached).
    // V2-FIX (reviewwhisper.md) L-14: @Volatile — the timestamp is written from inside a
    // mutex and read lock-free; without it readers could see a stale value indefinitely.
    @Volatile private var blockedCacheLoadedAtMs = 0L
    private val BLOCK_CACHE_TTL_MS = 5 * 60 * 1000L
    // V2-FIX (reviewwhisper.md) L-14: single-flight guard so concurrent callers on a cold
    // cache fire ONE network reload instead of N redundant ones.
    private val blockCacheLoadMutex = Mutex()
    // H-6: negative-result TTL for isUserBlockedByOther (the other party's blocks can
    // only be probed per-pair under RLS, so a short-TTL memo is the best available).
    private val blockedByOtherCheckedAtMs = ConcurrentHashMap<String, Long>()
    private val BLOCKED_BY_OTHER_TTL_MS = 30_000L

    /** Reloads the complete blocker set at most once per [BLOCK_CACHE_TTL_MS]. */
    private suspend fun ensureBlockCachesFresh(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - blockedCacheLoadedAtMs < BLOCK_CACHE_TTL_MS) return
        blockCacheLoadMutex.withLock {
            // Re-check under the lock: another caller may have refreshed while we waited.
            if (!force && System.currentTimeMillis() - blockedCacheLoadedAtMs < BLOCK_CACHE_TTL_MS) return
            // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
            runCatchingCE {
                db.from("whisper_blocks").select { filter { eq("blocker_id", myId) } }
                    .decodeList<WhisperBlock>().map { it.blockedId }.toSet()
            }.onSuccess { currentlyBlocked ->
                blockedIds.value = currentlyBlocked
                blockedByMeCache.clear()
                blockedByMeCache.addAll(currentlyBlocked)
                blockedCacheLoadedAtMs = System.currentTimeMillis()
            }
        }
    }

    private suspend fun isUserBlockedByMe(userId: String): Boolean {
        ensureBlockCachesFresh()
        return blockedIds.value.contains(userId) || blockedByMeCache.contains(userId)
    }

    private suspend fun isUserBlockedByOther(userId: String): Boolean {
        // H-6: short-TTL memo for the negative result — this check runs per send and
        // per incoming event, and a "not blocked" answer stays valid briefly.
        blockedByOtherCheckedAtMs[userId]?.let { checkedAt ->
            if (System.currentTimeMillis() - checkedAt < BLOCKED_BY_OTHER_TTL_MS) return false
        }
        // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
        val isBlocked = runCatchingCE {
            val blocks = db.from("whisper_blocks").select {
                filter {
                    eq("blocker_id", userId)
                    eq("blocked_id", myId)
                }
            }.decodeList<WhisperBlock>()
            blocks.isNotEmpty()
        }.getOrDefault(false)
        if (isBlocked) {
            blockedByOtherCheckedAtMs.remove(userId)
        } else {
            blockedByOtherCheckedAtMs[userId] = System.currentTimeMillis()
        }
        return isBlocked
    }

    /** Re-reads the block table for this account so in-memory caches never drift from server truth. */
    private suspend fun refreshBlockCachesFor(targetUserId: String) {
        // H-6: force = bypass TTL, this is called right after an optimistic mutation.
        // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
        runCatchingCE {
            db.from("whisper_blocks").select { filter { eq("blocker_id", myId) } }
                .decodeList<WhisperBlock>().map { it.blockedId }.toSet()
        }.onSuccess { currentlyBlocked ->
            blockedIds.value = currentlyBlocked
            blockedByMeCache.clear()
            if (targetUserId in currentlyBlocked) blockedByMeCache.add(targetUserId) else blockedByMeCache.remove(targetUserId)
            blockedCacheLoadedAtMs = System.currentTimeMillis()
            blockedByOtherCheckedAtMs.remove(targetUserId)
        }.onFailure {
            // Keep existing cache on network failure; optimistic state already applied by caller.
        }
    }

    suspend fun getBlockStatus(otherUserId: String): Pair<Boolean, Boolean> {
        val byMe = isUserBlockedByMe(otherUserId)
        val byOther = isUserBlockedByOther(otherUserId)
        return Pair(byMe, byOther)
    }

    suspend fun blockUser(targetUserId: String): Result<Unit> = runCatching {
        require(targetUserId.isNotBlank() && targetUserId != myId) { "Choose another user to block." }
        db.from("whisper_blocks").insert(WhisperBlockInsert(blockerId = myId, blockedId = targetUserId))
        blockedIds.update { it + targetUserId }
        blockedByMeCache.add(targetUserId)
        refreshBlockCachesFor(targetUserId)
    }

    suspend fun unblockUser(targetUserId: String): Result<Unit> = runCatching {
        db.from("whisper_blocks").delete { filter { eq("blocker_id", myId); eq("blocked_id", targetUserId) } }
        blockedIds.update { it - targetUserId }
        blockedByMeCache.remove(targetUserId)
        refreshBlockCachesFor(targetUserId)
    }

    suspend fun isBlockedByMe(otherUserId: String): Boolean = isUserBlockedByMe(otherUserId)
    suspend fun isBlockedByOther(otherUserId: String): Boolean = isUserBlockedByOther(otherUserId)

    suspend fun getFriendsOfFriends(): Result<List<WhisperProfile>> = runCatching {
        val myFriendIds = getFriendships().getOrThrow()
            .filter { it.status == "accepted" }
            .map { it.otherUserId(myId) }
            .toSet()

        if (myFriendIds.isEmpty()) return Result.success(emptyList())

        // Fetch accepted friendships involving our friends
        val candidateFriendships = db.from("friends").select {
            filter { 
                eq("status", "accepted") 
                or {
                    isIn("user_a", myFriendIds.toList())
                    isIn("user_b", myFriendIds.toList())
                }
            }
            limit(1_000)
        }.decodeList<WhisperFriendship>()

        val fofIds = mutableSetOf<String>()
        for (f in candidateFriendships) {
            val a = f.userA
            val b = f.userB
            if (a in myFriendIds && b !in myFriendIds && b != myId) fofIds.add(b)
            if (b in myFriendIds && a !in myFriendIds && a != myId) fofIds.add(a)
        }

        if (fofIds.isEmpty()) return Result.success(emptyList())

        // Resolve profiles in ONE query instead of N+1 per-id getProfile calls.
        val (blockedIds, profilesById) = batchProfilesById(fofIds.take(20))
        val recommended = profilesById.values
            .filter { !it.isPrivate && !it.isHiddenFromDiscover && it.id !in blockedIds }
            .take(15)
        recommended
    }

    /**
     * Fetches paginated discoverable profiles via the [whisper_discover_profiles] RPC.
     *
     * The RPC enforces:
     *   • 60 pages/hour per calling user (returns P0002 "rate_limited" on breach).
     *   • Server-side filtering of private / hide_from_discover / own profile.
     *   • Server-side exclusion of profiles that have blocked the caller.
     *
     * Friends are filtered out client-side to avoid a cross-join on the server.
     */
    suspend fun getDiscoverProfiles(page: Int, pageSize: Int = 20): Result<List<WhisperProfile>> = runCatching {
        val myFriendIds = getFriendships().getOrThrow()
            .filter { it.status == "accepted" }
            .map { it.otherUserId(myId) }
            .toSet()

        db.rpc(
            "whisper_discover_profiles",
            buildJsonObject {
                put("p_page", page)
                put("p_page_size", pageSize.coerceIn(1, 30))
            }
        )
            .decodeList<WhisperProfile>()
            .filter { it.id !in myFriendIds }
    }

    // V2-FIX (reviewwhisper.md) R-M1: typing/presence BROADCAST emissions removed — they
    // were consumed nowhere meaningful and leaked interaction metadata (who types to whom,
    // when devices go online/offline) to anyone guessing the channel name. Presence stays
    // authoritative via profiles.last_seen_at ([updateLastSeen]); the subscribe flows below
    // keep their public Flow APIs but now rely on authenticated Postgres/DB data only.
    // ── V6-R5 (#4): DB-backed typing signal (see 20260831_whisper_typing_signal.sql) ──

    private val typingSignalLastSentAtMs = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Throttled to ~1 write per 4s while typing; token is opaque random per write. */
    private suspend fun sendTypingSignalDb(receiverId: String) {
        val now = System.currentTimeMillis()
        if (now - (typingSignalLastSentAtMs[receiverId] ?: 0L) < 3_000) return
        typingSignalLastSentAtMs[receiverId] = now
        runCatchingCE {
            db.from("whisper_typing_signals").upsert(
                mapOf(
                    "sender_id" to myId,
                    "receiver_id" to receiverId,
                    "signal" to java.util.UUID.randomUUID().toString().replace("-", ""),
                    "updated_at" to java.time.OffsetDateTime.now().toString(),
                ),
            )
        }.onFailure { ProtocolDiagnostics.increment("typing.dbWriteFail") }
    }

    private suspend fun clearTypingSignalDb(receiverId: String) {
        if (typingSignalLastSentAtMs.remove(receiverId) == null) return // never sent → nothing to clear
        runCatchingCE {
            db.from("whisper_typing_signals").delete {
                filter { eq("sender_id", myId); eq("receiver_id", receiverId) }
            }
        }
    }

    @Serializable
    private data class TypingSignalRow(
        @SerialName("signal") val signal: String,
        @SerialName("updated_at") val updatedAt: String,
    )

    /**
     * Receiver half of the DB-backed typing lane: REST read of the partner's row.
     * A row younger than [TYPING_SIGNAL_FRESH_MS] means the partner is typing.
     * Returns null when the row is absent/unreadable; age decides liveness —
     * a crashed sender needs no cleanup write since the row simply goes stale.
     */
    private suspend fun readTypingSignal(receiverId: String): Boolean? = runCatchingCE {
        db.from("whisper_typing_signals").select {
            filter { eq("sender_id", receiverId); eq("receiver_id", myId) }
            limit(1)
        }.decodeList<TypingSignalRow>().firstOrNull()
    }.getOrNull()?.let { row ->
        runCatching {
            val age = System.currentTimeMillis() -
                java.time.OffsetDateTime.parse(row.updatedAt).toInstant().toEpochMilli()
            age in 0..TYPING_SIGNAL_FRESH_MS
        }.getOrDefault(false)
    }

    suspend fun sendTypingStatus(targetUserId: String, isTyping: Boolean) {
        // V4-FIX (field report): typing died when the plaintext R-M1 emissions were
        // removed. Restored with an ENCRYPTED payload — the boolean is sealed with the
        // pairwise attachment cipher (bound AAD), so eavesdroppers on the public
        // channel see random bytes and outsiders cannot forge valid events.
        runCatching {
            // V6-R5 (#4): DB-backed signal — reliable even when broadcasts die.
            if (isTyping) sendTypingSignalDb(targetUserId) else clearTypingSignalDb(targetUserId)
            // V6-R3: cached read (no RPC when fresh) keeps the candidate keys from
            // going fully stale across the partner's rotation — sealing typing pings
            // to a long-expired key made receivers skip them by kid-match alone.
            getProfile(targetUserId).getOrNull()
            // V5: seal the typing ping for EVERY known recipient key so key drift can
            // never silently kill typing again.
            val candidates = recipientKeyCandidates(targetUserId)
            if (candidates.isEmpty()) return
            val plain = buildJsonObject {
                put("is_typing", isTyping)
                put("ts", java.time.Instant.now().toEpochMilli())
            }.toString().toByteArray()
            val entries = candidates.mapNotNull { (kid, pub) ->
                crypto.encryptAttachment(plain, pub, myId, targetUserId)?.let {
                    Triple(kid, it.second, android.util.Base64.encodeToString(it.first, android.util.Base64.NO_WRAP))
                }
            }
            if (entries.isEmpty()) return
            val channel = getOrJoinBroadcastChannel("typing_" + conversationKey(myId, targetUserId))
            channel.broadcast(
                event = "typing",
                payload = BroadcastPayload.Json(buildJsonObject {
                    put("env", WhisperEnvelope.encode(entries, crypto.getPublicKeyBase64())!!)
                }),
            )
        }
    }

    fun subscribeToTypingStatus(otherUserId: String): Flow<Boolean> = callbackFlow {
        val name = "typing_" + conversationKey(myId, otherUserId)
        val channel = getOrJoinBroadcastChannel(name)
        val broadcasts = channel.broadcastFlow("typing")
        // V6-R3: broadcast channels die with the socket just like postgres lanes —
        // heal them identically or typing indicators silently vanish.
        val healthJob = watchChannelHealth(this, name, channel)

        // V6-R5 (#4): PRIMARY typing lane — poll the DB signal every 3s. Deterministic
        // and socket-independent; the broadcast collector below stays as a fast-path.
        var lastEmittedTyping: Boolean? = null
        fun emitTyping(value: Boolean) {
            if (value != lastEmittedTyping) {
                lastEmittedTyping = value
                trySend(value)
            }
        }
        val dbPollJob = launch {
            while (isActive) {
                kotlinx.coroutines.delay(1_500)
                try {
                    // V6-R6: null (no/stale row) must map to FALSE and be emitted —
                    // skipping null froze the indicator "on" whenever the sender's
                    // clear-delete landed without a broadcast to carry the false.
                    emitTyping(readTypingSignal(otherUserId) == true)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ProtocolDiagnostics.increment("typing.pollError")
                }
            }
        }
        val job = launch { broadcasts.collect { rb ->
            try {
                val outer = (rb.payload as? BroadcastPayload.Json)?.value?.jsonObject ?: return@collect
                val env = outer["env"]?.jsonPrimitive?.content ?: return@collect
                val entries = WhisperEnvelope.decode(env) ?: return@collect
                if (isUserBlockedByMe(otherUserId)) return@collect
                // Open with whichever of OUR keys the sender sealed for; only a holder
                // of the pairwise secret can produce ciphertext that authenticates.
                val ownKid = WhisperEnvelope.ownKid(crypto) ?: return@collect
                val entry = entries.firstOrNull { it.first == ownKid } ?: return@collect
                fun openWith(pub: String?) = pub?.let {
                    crypto.decryptAttachment(
                        cipherBytes = android.util.Base64.decode(entry.third, android.util.Base64.NO_WRAP),
                        ivBase64 = entry.second,
                        senderPublicKeyBase64 = it,
                        senderId = otherUserId,
                        receiverId = myId,
                        messageCreatedAtEpochMs = System.currentTimeMillis(),
                    )
                }
                // V6-R3 FIX (field report "typing indicators vanished"): the sender may
                // have sealed to a NEWER key than any local view holds after their
                // rotation. Try every local candidate, then force ONE fresh profile
                // fetch and retry before giving up.
                val candidates = buildList {
                    add(peerKeys.value[otherUserId])
                    add(peerKeyFor(otherUserId))
                    add(WhisperEnvelope.inBandSenderKey(env)) // V6-R7
                }.filterNotNull().distinct()
                var plain = candidates.firstNotNullOfOrNull(::openWith)
                if (plain == null) {
                    val fresh = getProfile(otherUserId, forceRefresh = true).getOrNull()?.publicKey
                    if (fresh != null && fresh !in candidates) {
                        plain = openWith(fresh)
                    }
                }
                plain ?: return@collect
                val isTyping = Json.parseToJsonElement(plain.decodeToString()).jsonObject["is_typing"]?.jsonPrimitive?.booleanOrNull ?: false
                emitTyping(isTyping)
            } catch (_: Exception) { }
        } }
        awaitClose {
            healthJob.cancel()
            dbPollJob.cancel()
            job.cancel(); appScope.launch { removeCachedChannel(name, channel) }
        }
    }

    suspend fun sendPresence(targetUserId: String, isOnline: Boolean) {
        // Intentional no-op (broadcast emission removed); callers keep last_seen_at fresh
        // via [updateLastSeen] instead.
    }

    fun subscribeToPresence(otherUserId: String): Flow<Pair<Boolean, String?>> = callbackFlow {
        val name = "presence_${conversationKey(myId, otherUserId)}"
        val channel = supabase.channel(name)

        // V2-FIX (reviewwhisper.md) R-M1: the realtime broadcast lane was removed;
        // DB last_seen_at updates are the sole remaining presence source.
        // V2-FIX (reviewwhisper.md) R-M6: server-side filter scoped to exactly this
        // partner's profile row instead of streaming every RLS-visible profile change.
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "profiles"
            filter("id", FilterOperator.EQ, otherUserId)
        }
        val dbJob = launch {
            changes.collect { action ->
                try {
                    val profile = when (action) {
                        is PostgresAction.Insert -> action.decodeRecord<WhisperProfile>()
                        is PostgresAction.Update -> action.decodeRecord<WhisperProfile>()
                        else -> null
                    }
                    if (profile != null && profile.id == otherUserId) {
                        val lastSeen = profile.lastSeenAt
                        if (lastSeen != null) {
                            val lastSeenTs = java.time.OffsetDateTime.parse(lastSeen).toInstant()
                            val isOnline = java.time.Instant.now().minusSeconds(120).isBefore(lastSeenTs)
                            trySend(Pair(isOnline, lastSeen))
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        channel.subscribe()
        channelMutex.withLock {
            broadcastChannelCache[name] = channel
        }
        // V6-R3: auto-heal dropped realtime channels (presence).
        val presenceHealthJob = watchChannelHealth(this, name, channel)

        // V6-R4: presence polling fallback — online/last-seen stays fresh even when
        // the websocket lane is down (REST read of the partner's profile row).
        val presencePollJob = launch {
            while (isActive) {
                kotlinx.coroutines.delay(20_000)
                try {
                    if (channel.status.value == io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED) {
                        continue
                    }
                    val profile = runCatchingCE {
                        db.from("profiles").select { filter { eq("id", otherUserId) } }
                            .decodeList<WhisperProfile>().firstOrNull()
                    }.getOrNull() ?: continue
                    val lastSeen = profile.lastSeenAt ?: continue
                    val lastSeenTs = java.time.OffsetDateTime.parse(lastSeen).toInstant()
                    trySend(Pair(java.time.Instant.now().minusSeconds(120).isBefore(lastSeenTs), lastSeen))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    ProtocolDiagnostics.increment("rt.presencePollError")
                }
            }
        }

        awaitClose {
            presenceHealthJob.cancel()
            presencePollJob.cancel()
            dbJob.cancel()
            appScope.launch { removeCachedChannel(name, channel) } // H-1 FIX
        }
    }

    // ─────────────────────────────────────────────────────────────
    // KEY TRUST & VERIFICATION
    // ─────────────────────────────────────────────────────────────

    /**
     * P0-1 FIX (reviewwhisper.md): single source of truth for classifying a partner key
     * change. The old code had three contradictory signals (30-day rotation interval,
     * a 6-day "expected" heuristic, and copy claiming weekly rotation), which made
     * nearly every fleet-wide auto-rotation surface as a scary CHANGED warning AND
     * hard-block sending until manual review.
     *
     * Classification now:
     *  - known == current                     → MATCH
     *  - server row updated < FRESH_WINDOW    → ROTATED_AUTO (just rotated — calm info)
     *  - known key age ≥ interval − 24h       → ROTATED_AUTO (aged-out scheduled rotation)
     *  - anything else                        → CHANGED (genuine unexpected change → warn)
     */
    private suspend fun classifyKeyChange(
        otherUserId: String,
        profile: WhisperProfile,
    ): KeyTrustStatus {
        val current = profile.publicKey.orEmpty()
        val known = keyTrustStore.knownKey(otherUserId)
        if (known == null || known == current || current.isBlank()) return KeyTrustStatus.MATCH

        val serverUpdatedAgeMs = runCatching {
            java.time.OffsetDateTime.parse(profile.updatedAt).toInstant().toEpochMilli()
                .let { System.currentTimeMillis() - it }
        }.getOrNull() ?: Long.MAX_VALUE

        if (serverUpdatedAgeMs in 0..WhisperKeyRotationStore.FRESH_ROTATION_WINDOW_MS) {
            return KeyTrustStatus.ROTATED_AUTO
        }

        val knownTs = keyTrustStore.knownKeyTimestamp(otherUserId)
        val knownAge = if (knownTs == 0L) Long.MAX_VALUE else System.currentTimeMillis() - knownTs
        val expectedWindow = WhisperKeyRotationStore.ROTATE_INTERVAL_MS - 24L * 60 * 60 * 1000
        if (knownAge >= expectedWindow) {
            return KeyTrustStatus.ROTATED_AUTO
        }
        return KeyTrustStatus.CHANGED
    }

    /**
     * True when a partner key mismatch is a routine fresh rotation and messaging may
     * continue without a manual safety-number review. Used by [sendMessage] so monthly
     * fleet rotations no longer brick conversations (P0-1).
     */
    private fun isFreshServerRotation(profile: WhisperProfile?): Boolean {
        val updatedAt = profile?.updatedAt ?: return false
        val ageMs = runCatching {
            java.time.OffsetDateTime.parse(updatedAt).toInstant().toEpochMilli()
                .let { System.currentTimeMillis() - it }
        }.getOrNull() ?: return false
        return ageMs in 0..WhisperKeyRotationStore.FRESH_ROTATION_WINDOW_MS
    }

    /**
     * Builds a [KeyTrustInfo] for a conversation partner. On first encounter the key is
     * silently remembered as known (TOFU — M-1: this read-path pinning is deliberate:
     * the first chat/profile view IS first contact, and the pin must be durable so a
     * later server-side key swap can never silently decrypt old or new material).
     * Only a *change* of a previously known key ever surfaces via [classifyKeyChange].
     */
    suspend fun getKeyTrustInfo(otherUserId: String): KeyTrustInfo {
        val myFingerprint = crypto.getPublicKeyBase64()?.let { crypto.fingerprint(it) }
        // Force a fresh read so a changed key is never hidden behind the profile cache.
        val profile = getProfile(otherUserId, forceRefresh = true).getOrNull() ?: return KeyTrustInfo(myFingerprint = myFingerprint)
        val currentKey = profile.publicKey
        if (currentKey.isNullOrBlank()) return KeyTrustInfo(myFingerprint = myFingerprint)

        val known = keyTrustStore.knownKey(otherUserId)
        if (known == null) {
            // TOFU first contact (see KDoc): record and report MATCH without scary UI.
            keyTrustStore.rememberKey(otherUserId, currentKey)
            return KeyTrustInfo(status = KeyTrustStatus.MATCH, partnerFingerprint = crypto.fingerprint(currentKey), myFingerprint = myFingerprint, isVerified = false)
        }

        val status = classifyKeyChange(otherUserId, profile)

        // V6-R5 (#2): per-chat-open key-state snapshot in the diagnostics buffer.
        // Only kid prefixes (8-hex, already public on the wire) and booleans — never
        // raw keys or fingerprints. This is what makes residual drift visible in a
        // diagnostics export instead of requiring guesswork.
        ProtocolDiagnostics.log(
            "keystate[${otherUserId.take(6)}…]: pinned=${known?.let { WhisperEnvelope.keyId(it) } ?: "-"} " +
                "server=${WhisperEnvelope.keyId(currentKey)} " +
                "device=${crypto.getPublicKeyBase64()?.let { WhisperEnvelope.keyId(it) } ?: "-"} " +
                "match=${status == KeyTrustStatus.MATCH} verified=${keyTrustStore.verifiedKey(otherUserId) == currentKey} " +
                "staged=${crypto.hasStagedAliases()}",
        )

        // V2-FIX (reviewwhisper.md) R-H1: an auto-accepted rotation detected here also
        // emits the passive key-change signal — once per (user,key) via receiveKeyNotified,
        // matching cachePeerKey/sendMessage so the UI can show a non-blocking banner.
        if (status == KeyTrustStatus.ROTATED_AUTO && receiveKeyNotified.add("$otherUserId|$currentKey")) {
            _receiveKeyChanged.tryEmit(otherUserId)
        }

        // P0-1: copy aligned to the REAL 30-day interval (was "every week").
        // V2-FIX (reviewwhisper.md) L-12: ROTATED_MANUAL branch pruned — classifyKeyChange
        // can only return MATCH / ROTATED_AUTO / CHANGED, so that arm was unreachable.
        // The enum value itself is kept (the UI still references it).
        val rotateMsg = when (status) {
            KeyTrustStatus.ROTATED_AUTO -> UiText.StringResource(
                R.string.st_Whisper_KeyRotate_AutoMonthly,
                WhisperKeyRotationStore.ROTATE_INTERVAL_MS / (24L * 60 * 60 * 1000),
            )
            else -> null
        }
        return KeyTrustInfo(
            status = status,
            partnerFingerprint = crypto.fingerprint(currentKey),
            myFingerprint = myFingerprint,
            isVerified = status == KeyTrustStatus.MATCH && keyTrustStore.verifiedKey(otherUserId) == currentKey,
            rotateMessage = rotateMsg,
            isExpectedRotation = status == KeyTrustStatus.ROTATED_AUTO,
        )
    }

    /** Mark the partner's current key as verified (fingerprint compared in person). */
    suspend fun verifyUserKey(otherUserId: String): Boolean {
        val profile = getProfile(otherUserId, forceRefresh = true).getOrNull() ?: return false
        val currentKey = profile.publicKey ?: return false
        keyTrustStore.markVerified(otherUserId, currentKey)
        cachePeerKey(otherUserId, currentKey)
        return true
    }

    /** Accept the partner's new key without verifying it in person. */
    suspend fun acceptNewKey(otherUserId: String): Boolean {
        val profile = getProfile(otherUserId, forceRefresh = true).getOrNull() ?: return false
        val currentKey = profile.publicKey ?: return false
        keyTrustStore.rememberKey(otherUserId, currentKey)
        cachePeerKey(otherUserId, currentKey)
        return true
    }

    /**
     * H-2 FIX (reviewwhisper.md): tears down every cached realtime channel (leaves the
     * socket-level subscriptions). Previously sign-out/account-deletion left channels
     * named with the old user's IDs subscribed until process death.
     */
    suspend fun removeAllCachedChannels() {
        val toRemove = channelMutex.withLock { broadcastChannelCache.values.toList() }
        broadcastChannelCache.clear()
        // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
        ProtocolDiagnostics.log("rt.teardown[all]: signout-wipe (${toRemove.size} channels)")
        toRemove.forEach { runCatchingCE { realtime.removeChannel(it) } }
    }

    /**
     * Drops every in-memory cache scoped to the signed-in account so a sign-out can
     * never bleed one user's conversations, keys, blocks or dedupe state into the next.
     * Does NOT touch persisted stores (Room, outbox, key trust) — those are account data.
     */
    fun clearSessionScopedCaches() {
        conversationsCache = null
        profileCache.clear(); profileCacheTs.clear()
        peerKeys.value = emptyMap()
        blockedIds.value = emptySet()
        blockedByMeCache.clear()
        blockedCacheLoadedAtMs = 0L
        blockedByOtherCheckedAtMs.clear()
        recentlyEmittedMessageIds.clear()
        receiveKeyNotified.clear()
        synchronized(decryptMemo) { decryptMemo.clear() }
        // H-2: channels are torn down asynchronously on the app scope — sign-out must
        // not block on network I/O, but the stale channels must not survive either.
        appScope.launch { removeAllCachedChannels() }
    }

    /**
     * Wipes every byte of whisper data this device holds for the signed-in account:
     * Room cache, tombstones, outbox, key trust records, hidden chats, mutes, caches,
     * realtime channels and the E2EE key pair. Called after a successful server-side
     * account deletion.
     */
    suspend fun clearAllLocalData() {
        removeAllCachedChannels()
        // V3-FIX (task F): drop the encrypted image disk cache with the rest of the
        // account's local data so a following sign-in starts from a clean slate.
        imageDiskCache.clearAll()
        messageDao.clearAll()
        deletedStore.clearAll()
        // V6 (planwhisper.md §3.1): ratchet sessions die with the account — a later
        // sign-in must never inherit another identity's chains.
        sessionStore.deleteAll()
        outgoingQueue.clearAll()
        keyTrustStore.clearAll()
        hiddenChatsStore.clearAll()
        mutePrefs.clearAll()
        profileCache.clear(); profileCacheTs.clear()
        peerKeys.value = emptyMap()
        blockedIds.value = emptySet()
        blockedByMeCache.clear()
        blockedCacheLoadedAtMs = 0L
        blockedByOtherCheckedAtMs.clear()
        conversationsCache = null
        // V2-FIX (reviewwhisper.md) L-20: also clear the realtime dedupe set and the
        // key-change notification memory so a fresh account never inherits them.
        recentlyEmittedMessageIds.clear()
        receiveKeyNotified.clear()
        synchronized(decryptMemo) { decryptMemo.clear() }
        crypto.resetKeyPair()
    }
}
