/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper.session

import android.content.Context
import android.util.Log
import com.frerox.toolz.data.whisper.ProtocolDiagnostics
import com.frerox.toolz.data.whisper.WhisperSessionFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V6 (planwhisper.md §3.1): per-peer persistent Double Ratchet session state.
 *
 * THE lesson this store encodes (see planwhisper.md §7): anything RAM-only becomes
 * a field bug later — process death between an X3DH handshake and the next message
 * used to mean a permanently unreadable conversation. Every session therefore lives
 * in `filesDir/whisper_sessions/<sha256(accountId)>/<sha256(peerId)>.json` AND in a
 * shared in-memory map of LIVE [WhisperRatchet] instances; disk is only a cold-start
 * restore path. Pre-scoping single-level files are adopted per peer on first use
 * (see [adoptLegacySession]) — never in one sweep, which used to hand every
 * account's sessions to whichever account happened to run first.
 *
 * SECURITY POSTURE — never plaintext key material at rest:
 *  - The X3DH shared secret is stored Keystore-AES wrapped ([StoredSession.x3dhKeyWrapped]).
 *  - The ENTIRE ratchet snapshot (root/chain/message keys, skipped windows) is
 *    serialized, then wrapped as one blob ([StoredSession.ratchetWrapped]).
 *  - Only public framing (session id, pending X3DH header, peer identity pub)
 *    touches disk unwrapped.
 *
 * FAILURE PHILOSOPHY: a corrupt/unopenable file is deleted and treated as "no
 * session" (logged once) — callers fall back to the proven V5 envelope path and a
 * fresh handshake re-establishes secrecy. A session file can NEVER block delivery.
 */
@Singleton
class WhisperSessionStore @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val protector: WhisperSessionSecretProtector,
    // ACCOUNT-SCOPING (field defect, October 2026): the crypto identity is
    // install-wide, but a peer's client keys its ratchet state by our USER id.
    // Two accounts on the same install that talk to the same peer therefore
    // need two independent sessions here as well, or they share one ratchet and
    // every message on one side becomes undecryptable. Scoping reads the
    // signed-in id at call time; a loaded Live remembers the scope it came from
    // so a late save can never land in the next account's file.
    private val supabase: SupabaseClient,
) {
    companion object {
        private const val TAG = "WhisperSessionStore"
        private const val DIR_NAME = "whisper_sessions"

        /** Sentinel scope while signed out — no chat can run, but keeps keys distinct. */
        private const val ANON_SCOPE = "\u0000signed-out"

        /**
         * Residual-downgrade block threshold: after this many consecutive X3DH
         * establish failures (no live/proven session), the repository blocks the
         * insecure envelope fallback instead of retrying it forever. Mirrors
         * `WireProtocol.DOWNGRADE_BLOCK_AFTER_N_FAILS`; the pure block rule lives
         * there (`WireProtocol.shouldBlockDowngradeAfterFails`).
         */
        const val MAX_FAILED_ESTABLISH_BEFORE_DOWNGRADE_BLOCK = 3

        /** Superseded sessions retained per peer for decrypting in-flight frames. */
        private const val MAX_ALT_SESSIONS = 3
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val baseDir: File get() = File(appContext.filesDir, DIR_NAME)
    private val failureLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Signed-in account id; scopes all in-memory and on-disk session state. */
    private fun accountScope(): String =
        supabase.auth.currentUserOrNull()?.id?.takeIf { it.isNotBlank() } ?: ANON_SCOPE

    private fun scoped(peerId: String): String = "${accountScope()}\u0000$peerId"

    /** Serializable, secret-free-framing record persisted to disk. */
    @Serializable
    data class StoredSession(
        val sessionId: String,
        val x3dhKeyWrapped: String,
        val ratchetWrapped: String? = null,
        val pendingHeader: WhisperV3Codec.X3dhWire? = null,
        val peerIkB64: String? = null,
        val createdAtMs: Long,
        /**
         * FIX-2: true once a frame from THIS session was opened through the pure
         * ratchet path (both sides proven). Unproven sessions keep carrying the
         * handshake-insurance envelope copy on outbound frames.
         */
        val sessionProven: Boolean = false,
    ) {
    /**
     * V6 acceptance gate for an INCOMING x3dh header when a session already
     * exists for this peer (pure rule — unit-tested):
     *  - same session id → replay/idempotent redelivery → accept (caller must not
     *    re-bootstrap an existing ratchet);
     *  - peer identity key changed (reinstall / new device) → old session is
     *     cryptographically orphaned → accept unconditionally;
     *  - otherwise deterministic tie-break so two racing initiators converge:
     *    the LOWER session id wins, the higher-sid side adopts responder role.
     */
    fun canAcceptHandshake(incomingIkB64: String, incomingSid: String): Boolean =
        canAcceptHandshake(sessionId, peerIkB64, incomingIkB64, incomingSid)

    companion object {
        /** Pure form of [canAcceptHandshake] shared by [Live]. */
        fun canAcceptHandshake(
            currentSid: String,
            currentPeerIkB64: String?,
            incomingIkB64: String,
            incomingSid: String,
        ): Boolean =
            currentSid == incomingSid ||
                currentPeerIkB64 == null ||
                currentPeerIkB64 != incomingIkB64 ||
                incomingSid < currentSid

        /**
         * TWO ACCOUNTS, ONE INSTALL acceptance rule (October 2026 defect).
         *
         * Both accounts share this device's software X25519 identity, so the
         * `peer identity changed` signal can never fire between them and the
         * racing-initiator tie-break is actively harmful: applied independently
         * on each side it can leave account A holding session S_B while account B
         * holds session S_A — each side reads its own messages and locks the
         * other's, forever (exactly the reported symptom).
         *
         * Accounts on one install are sequential (only one is signed in), so
         * there is no real race. The session CREATED LATER on this device is
         * authoritative and both sides converge on it:
         *  - newer header → accept (the older side adopts responder role);
         *  - older header → reject;
         *  - identical timestamp → lower sid wins (deterministic);
         *  - legacy header without a timestamp → tie-break only.
         *
         * Timestamps are trustworthy here precisely because it is one clock.
         */
        fun acceptSameInstall(
            currentSid: String,
            currentCreatedAtMs: Long,
            incomingSid: String,
            incomingCreatedAtMs: Long,
        ): Boolean =
            incomingCreatedAtMs > currentCreatedAtMs ||
                (incomingCreatedAtMs == currentCreatedAtMs && incomingSid < currentSid) ||
                (incomingCreatedAtMs <= 0L && incomingSid < currentSid)
    }
    }

    /** Hydrated live state: serializable record + the actual ratchet object. */
    class Live(
        val sessionId: String,
        val x3dhKeyWrapped: String,
        val peerIkB64: String?,
        val createdAtMs: Long,
        @Volatile var pendingHeader: WhisperSessionFactory.X3dhHeader?,
        val ratchet: WhisperRatchet?,
        /** FIX-2 — see [StoredSession.sessionProven]. Volatile: set from decrypt lanes. */
        @Volatile var sessionProven: Boolean = false,
        /**
         * Account scope this session was loaded/created under. Pinned on first save
         * so an operation that spans a sign-out still writes to the correct
         * account's file instead of the next account's.
         */
        @Volatile var accountScope: String = "",
    ) {
        /** Set whenever ratchet/header state mutated; cleared by [WhisperSessionStore.save]. */
        @Volatile var dirty: Boolean = true

        /** Acceptance gate for an incoming handshake against THIS live session. */
        fun canAcceptHandshake(incomingIkB64: String, incomingSid: String): Boolean =
            StoredSession.canAcceptHandshake(sessionId, peerIkB64, incomingIkB64, incomingSid)

        /** Same-install ("newer session wins") gate — see [StoredSession.acceptSameInstall]. */
        fun acceptSameInstall(incomingSid: String, incomingCreatedMs: Long): Boolean =
            StoredSession.acceptSameInstall(sessionId, createdAtMs, incomingSid, incomingCreatedMs)
    }

    private val memory = ConcurrentHashMap<String, Live>()
    private val lastWrittenJson = ConcurrentHashMap<String, String>()

    /**
     * Superseded sessions, keyed `scope \0 peer \0 sessionId`. They are NEVER
     * used for sending; they exist only so frames from the losing session of a
     * same-install convergence (or a stale session we displaced) still open.
     * Bounded by [MAX_ALT_SESSIONS], both in memory and on disk.
     */
    private val altMemory = ConcurrentHashMap<String, Live>()

    /**
     * Residual-downgrade bookkeeping: consecutive X3DH establish failures per
     * peer (in-memory only by design — a process restart resets the count and
     * the envelope fallback is attempted again, so a block can never wedge
     * across reboots). Incremented by the repository on every establish
     * failure, reset on establish success. At
     * [MAX_FAILED_ESTABLISH_BEFORE_DOWNGRADE_BLOCK] with no live/proven session,
     * the send path blocks the insecure envelope fallback (see
     * `WireProtocol.shouldBlockDowngradeAfterFails`) with a retry-later warning
     * and the `send.blocked.downgrade_N` diagnostics counter.
     */
    private val failedEstablishCounts = ConcurrentHashMap<String, Int>()

    /** Consecutive establish failures for [peerId] (0 when never failed). */
    fun failedEstablishCount(peerId: String): Int = failedEstablishCounts[scoped(peerId)] ?: 0

    /** Records one establish failure; returns the new consecutive count. */
    fun recordEstablishFailure(peerId: String): Int {
        val key = scoped(peerId)
        val n = (failedEstablishCounts[key] ?: 0) + 1
        failedEstablishCounts[key] = n
        ProtocolDiagnostics.increment("v3.establishFailCount")
        if (n >= MAX_FAILED_ESTABLISH_BEFORE_DOWNGRADE_BLOCK) {
            android.util.Log.w(TAG, "Handshake repeatedly failed (${n}x) — insecure fallback will block, retry later")
            ProtocolDiagnostics.log("v3: establish failed ${n}x — insecure fallback blocked until success")
        }
        return n
    }

    /** Resets the failure count after a successful establish (or proven session). */
    fun recordEstablishSuccess(peerId: String) {
        if (failedEstablishCounts.remove(scoped(peerId)) != null) {
            ProtocolDiagnostics.increment("v3.establishRecovered")
        }
    }

    /**
     * Desync flags: set when an incoming frame proves the peer is using a
     * session we do not hold (or one older than ours), cleared when we adopt a
     * peer handshake or start a fresh outbound session. Scoped per account so a
     * flag from one signed-in account can never trigger a reset in the next.
     */
    private val desyncPeers = ConcurrentHashMap<String, Boolean>()

    fun markSessionDesync(peerId: String) {
        desyncPeers[scoped(peerId)] = true
    }

    fun sessionDesyncDetected(peerId: String): Boolean = desyncPeers.containsKey(scoped(peerId))

    fun clearSessionDesync(peerId: String) {
        desyncPeers.remove(scoped(peerId))
    }

    // One writer at a time per peer; encrypt/decrypt callers ALSO hold this mutex so
    // concurrent flow collectors can never advance one ratchet from two threads.
    private val locks = ConcurrentHashMap<String, Mutex>()
    fun mutexFor(peerId: String): Mutex = locks.computeIfAbsent(scoped(peerId)) { Mutex() }

    // ------------------------------------------------------------------ API

    /** Memory-first lookup with cold-start hydration from disk. Null = no usable session. */
    suspend fun load(peerId: String): Live? {
        val scope = accountScope()
        val key = "$scope\u0000$peerId"
        memory[key]?.let { return it }
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            adoptLegacySession(peerId, scope)
            val file = fileFor(peerId, scope)
            if (!file.exists()) return@withContext null
            val stored = runCatching {
                json.decodeFromString(StoredSession.serializer(), file.readText())
            }.getOrNull()
            if (stored == null) {
                logCorruptionOnce(peerId, "unparseable session file")
                file.delete()
                return@withContext null
            }
            val live = hydrate(stored)
            if (live == null) {
                logCorruptionOnce(peerId, "key unwrap failed")
                file.delete()
                return@withContext null
            }
            live.accountScope = scope
            lastWrittenJson[key] = file.readText()
            memory[key] = live
            live
        }
    }

    fun peek(peerId: String): Live? = memory[scoped(peerId)]

    /** Persists [live] under [peerId] (memory + atomic disk write). Skips no-op writes. */
    suspend fun save(peerId: String, live: Live) {
        // The scope is pinned on the Live instance so an operation that started
        // under one account can never write into the next account's file.
        val scope = live.accountScope.ifBlank {
            accountScope().also { live.accountScope = it }
        }
        val key = "$scope\u0000$peerId"
        // A session with a DIFFERENT id replaces the primary. Keep the displaced
        // one readable (bounded) so frames already sent on it still open — this is
        // what makes a same-install convergence non-destructive.
        memory[key]?.let { previous ->
            if (previous !== live && previous.sessionId != live.sessionId) {
                // Best-effort: failing to persist the superseded copy must never
                // block adopting the new primary (it is already in altMemory).
                runCatching { saveSecondary(peerId, previous) }
            }
        }
        memory[key] = live
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            adoptLegacySession(peerId, scope)
            val stored = serialize(live) ?: run {
                Log.w(TAG, "refusing to persist session for ${peerId.take(6)}… — wrap failed")
                return@withContext
            }
            val text = json.encodeToString(StoredSession.serializer(), stored)
            // Identical serialized state → skip the disk write entirely; chat scrolls
            // re-decrypt whole history and would otherwise rewrite the file per row.
            val unchanged = lastWrittenJson.put(key, text) == text
            if (!unchanged) writeFileAtomic(fileFor(peerId, scope), text)
            live.dirty = false
        }
    }

    /**
     * Reads a superseded (non-primary) session by [sessionId]. Used ONLY to open
     * frames still arriving on the session that lost the same-install "newer
     * wins" convergence; never used for sending.
     */
    suspend fun loadSecondary(peerId: String, sessionId: String): Live? {
        val scope = accountScope()
        val key = "$scope\u0000$peerId\u0000$sessionId"
        altMemory[key]?.let { return it }
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val file = WhisperSessionFiles.altFileFor(baseDir, peerId, scope, sessionId)
            if (!file.isFile) return@withContext null
            val stored = runCatching {
                json.decodeFromString(StoredSession.serializer(), file.readText())
            }.getOrNull()
            if (stored == null || stored.sessionId != sessionId) {
                file.delete()
                return@withContext null
            }
            val live = hydrate(stored) ?: run {
                file.delete()
                return@withContext null
            }
            live.accountScope = scope
            altMemory[key] = live
            live
        }
    }

    /** Persists [live] as a superseded, decrypt-only session. */
    suspend fun saveSecondary(peerId: String, live: Live) {
        val scope = live.accountScope.ifBlank {
            accountScope().also { live.accountScope = it }
        }
        val key = "$scope\u0000$peerId\u0000${live.sessionId}"
        altMemory[key] = live
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val stored = serialize(live) ?: return@withContext
            val text = json.encodeToString(StoredSession.serializer(), stored)
            writeFileAtomic(
                WhisperSessionFiles.altFileFor(baseDir, peerId, scope, live.sessionId),
                text,
            )
            pruneSecondaries(peerId, scope)
            live.dirty = false
        }
    }

    private fun pruneSecondaries(peerId: String, scope: String) {
        val files = WhisperSessionFiles.altFilesFor(baseDir, peerId, scope)
        if (files.size > MAX_ALT_SESSIONS) {
            files.map { file ->
                val created = runCatching {
                    json.decodeFromString(StoredSession.serializer(), file.readText()).createdAtMs
                }.getOrDefault(0L)
                created to file
            }.sortedBy { it.first }
                .dropLast(MAX_ALT_SESSIONS)
                .forEach { it.second.delete() }
        }
        val memPrefix = "$scope\u0000$peerId\u0000"
        val entries = altMemory.entries.filter { it.key.startsWith(memPrefix) }
        if (entries.size > MAX_ALT_SESSIONS) {
            entries.sortedBy { it.value.createdAtMs }
                .dropLast(MAX_ALT_SESSIONS)
                .forEach { altMemory.remove(it.key) }
        }
    }

    suspend fun delete(peerId: String) {
        val scope = accountScope()
        val key = "$scope\u0000$peerId"
        memory.remove(key)
        lastWrittenJson.remove(key)
        desyncPeers.remove(key)
        val altPrefix = "$scope\u0000$peerId\u0000"
        altMemory.keys.filter { it.startsWith(altPrefix) }.forEach { altMemory.remove(it) }
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            adoptLegacySession(peerId, scope)
            fileFor(peerId, scope).delete()
            WhisperSessionFiles.altFilesFor(baseDir, peerId, scope).forEach { it.delete() }
        }
    }

    /**
     * Drops every in-memory session without touching disk. Called on sign-out:
     * the next account must not inherit the previous account's live ratchets,
     * but its own history must still hydrate from disk when it signs back in.
     * [locks] are intentionally kept so concurrent callers still share a mutex.
     */
    fun clearMemory() {
        memory.clear()
        altMemory.clear()
        lastWrittenJson.clear()
        failedEstablishCounts.clear()
        desyncPeers.clear()
    }

    /** Sign-out / account-deletion wipe (planwhisper.md §3.1). */
    suspend fun deleteAll() {
        memory.clear()
        altMemory.clear()
        lastWrittenJson.clear()
        failedEstablishCounts.clear()
        desyncPeers.clear()
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            baseDir.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------ internals

    private fun serialize(live: Live): StoredSession? =
        serializeSession(live, protector, json)

    private fun hydrate(stored: StoredSession): Live? =
        hydrateSession(stored, protector, json)

    /**
     * Per-account file: `whisper_sessions/<sha256(account)>/<sha256(peer)>.json`.
     *
     * Builds before account scoping stored `<sha256(peer)>.json` directly under
     * `whisper_sessions/`. [adoptLegacySession] claims those per peer, on demand.
     */
    private fun fileFor(peerId: String, scope: String): File =
        WhisperSessionFiles.fileFor(baseDir, peerId, scope)

    /**
     * Upgrade-only, per-peer adoption of session files written before account
     * scoping. Deliberately NOT a directory sweep: the old files name only the
     * peer, so sweeping all of them into whichever account was active first
     * handed one account another account's sessions — which is exactly how a
     * same-device account switch turned whole conversations locked. Adopting a
     * file only when the active account actually opens that peer keeps each
     * account's own sessions (for two accounts A and B on one install, A claims
     * `<sha256(B)>` and B claims `<sha256(A)>` — different files, no conflict).
     *
     * Also reclaims files mis-swept by the earlier eager migration: a snapshot
     * for peer P sitting inside account P's own directory cannot be legitimate
     * (an account has no session whose peer is itself), so it is the other
     * account's session for P and is moved where it can be used.
     */
    private fun adoptLegacySession(peerId: String, scope: String) {
        if (WhisperSessionFiles.adopt(baseDir, peerId, scope) != null) {
            ProtocolDiagnostics.log("session: adopted a pre-scoping file into the active account")
        }
    }

    /** Atomic temp+rename write (same pattern as WhisperImageDiskCache). */
    private fun writeFileAtomic(target: File, text: String) {
        runCatching {
            val parent = target.parentFile ?: return@runCatching
            parent.mkdirs()
            val tmp = File.createTempFile("wsess_", ".tmp", parent)
            try {
                tmp.writeText(text)
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            } finally {
                tmp.delete()
            }
        }.onFailure { logCorruptionOnce("write", it.message ?: "io error") }
    }

    private fun logCorruptionOnce(what: String, detail: String) {
        if (failureLogged.compareAndSet(false, true)) {
            Log.w(TAG, "Session store fault ($what): $detail — treating as absent")
        }
        ProtocolDiagnostics.increment("session.storeFault")
    }
}

/**
 * On-disk layout for session snapshots. Pure `java.io.File` maths — no Android
 * imports — so the upgrade/adoption rules are unit-testable on the JVM.
 *
 * Live layout: `whisper_sessions/<sha256(ownerAccountId)>/<sha256(peerUserId)>.json`.
 */
internal object WhisperSessionFiles {
    private const val FILE_EXT = ".json"
    private const val ALT_DIR = "alt"

    fun fileFor(baseDir: File, peerId: String, scope: String): File =
        File(File(baseDir, sha256Hex(scope)), sha256Hex(peerId) + FILE_EXT)

    /**
     * Superseded session snapshot, kept read-only so frames still in flight on a
     * session that lost the same-install "newer wins" race remain decryptable:
     * `whisper_sessions/<sha256(owner)>/alt/<sha256(peer)>_<sha256(sessionId)>.json`.
     */
    fun altFileFor(baseDir: File, peerId: String, scope: String, sessionId: String): File =
        File(
            File(File(baseDir, sha256Hex(scope)), ALT_DIR),
            sha256Hex(peerId) + "_" + sha256Hex(sessionId) + FILE_EXT,
        )

    /** Every superseded snapshot for one (account, peer) pair. */
    fun altFilesFor(baseDir: File, peerId: String, scope: String): List<File> {
        val dir = File(File(baseDir, sha256Hex(scope)), ALT_DIR)
        val prefix = sha256Hex(peerId) + "_"
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(FILE_EXT) }
            ?: emptyList()
    }

    /**
     * A leftover snapshot that may be adopted for ([scope], [peerId]), or null.
     *
     *  1. Pre-scoping layout: `<sha256(peer)>.json` directly under the root.
     *  2. Mis-swept layout: `<sha256(peer)>/<sha256(peer)>.json` — a snapshot
     *     for peer P inside account P's own directory. That location is never
     *     legitimate (an account holds no session whose peer is itself), so it
     *     can only be a counterpart account's session for P.
     *
     * Never adopts for a self-peer.
     */
    fun legacySource(baseDir: File, peerId: String, scope: String): File? {
        if (peerId == scope) return null
        val hash = sha256Hex(peerId)
        val singleLevel = File(baseDir, hash + FILE_EXT)
        if (singleLevel.isFile) return singleLevel
        val misSwept = File(File(baseDir, hash), hash + FILE_EXT)
        return misSwept.takeIf { it.isFile }
    }

    /**
     * Moves a leftover described by [legacySource] into [scope]'s directory.
     * Returns the target when a file was adopted, null when there was nothing
     * to do (already scoped, or no leftover).
     */
    fun adopt(baseDir: File, peerId: String, scope: String): File? {
        val target = fileFor(baseDir, peerId, scope)
        if (target.isFile) return null
        val source = legacySource(baseDir, peerId, scope) ?: return null
        return runCatching {
            target.parentFile?.mkdirs()
            val moved = source.renameTo(target) || run {
                source.copyTo(target, overwrite = true)
                source.delete()
                target.isFile
            }
            target.takeIf { moved }
        }.getOrNull()
    }

    fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

private val SESSION_JSON = Json { ignoreUnknownKeys = true }

/**
 * Pure serialization pipeline (unit-testable without an Android Context):
 * live state → snapshot JSON → protector-wrapped record. Returns null when the
 * protector refuses — plaintext key material must never reach the caller.
 */
internal fun serializeSession(
    live: WhisperSessionStore.Live,
    protector: WhisperSessionSecretProtector,
    json: Json = SESSION_JSON,
): WhisperSessionStore.StoredSession? {
    val snapJson = live.ratchet?.let {
        runCatching {
            json.encodeToString(WhisperRatchet.Snapshot.serializer(), it.snapshot())
        }.getOrNull()
    }
    var wrappedRatchet: String? = null
    if (snapJson != null) {
        wrappedRatchet = protector.wrap(snapJson.toByteArray())
        if (wrappedRatchet == null) return null
    }
    return WhisperSessionStore.StoredSession(
        sessionId = live.sessionId,
        x3dhKeyWrapped = live.x3dhKeyWrapped,
        ratchetWrapped = wrappedRatchet,
        pendingHeader = live.pendingHeader?.let { WhisperV3Codec.encodeX3dhWire(it) },
        peerIkB64 = live.peerIkB64,
        createdAtMs = live.createdAtMs,
        sessionProven = live.sessionProven,
    )
}

/**
 * Inverse of [serializeSession]: wrapped record → hydrated live state with a
 * functional ratchet. Null on ANY unwrap/parse failure (caller deletes the file).
 */
internal fun hydrateSession(
    stored: WhisperSessionStore.StoredSession,
    protector: WhisperSessionSecretProtector,
    json: Json = SESSION_JSON,
): WhisperSessionStore.Live? {
    // Unwrap validates the Keystore path is alive for this install; the raw SK copy
    // is wiped immediately — Live carries only the wrapped form.
    val sk = protector.unwrap(stored.x3dhKeyWrapped) ?: return null
    try {
        val ratchet: WhisperRatchet? = stored.ratchetWrapped?.let { wrapped ->
            val snapJson = protector.unwrap(wrapped)?.decodeToString() ?: return null
            val snap = runCatching {
                json.decodeFromString(WhisperRatchet.Snapshot.serializer(), snapJson)
            }.getOrNull() ?: return null
            WhisperRatchet.restored(snap)
        }
        return WhisperSessionStore.Live(
            sessionId = stored.sessionId,
            x3dhKeyWrapped = stored.x3dhKeyWrapped,
            peerIkB64 = stored.peerIkB64,
            createdAtMs = stored.createdAtMs,
            pendingHeader = stored.pendingHeader?.let { WhisperV3Codec.toFactoryHeader(it) },
            ratchet = ratchet,
            sessionProven = stored.sessionProven,
        )
    } finally {
        sk.fill(0)
    }
}
