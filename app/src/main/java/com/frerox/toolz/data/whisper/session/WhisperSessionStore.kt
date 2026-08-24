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
 * in `filesDir/whisper_sessions/<sha256(peerId)>.json` AND in a shared in-memory
 * map of LIVE [WhisperRatchet] instances; disk is only a cold-start restore path.
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
) {
    companion object {
        private const val TAG = "WhisperSessionStore"
        private const val DIR_NAME = "whisper_sessions"
        private const val FILE_EXT = ".json"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val dir: File get() = File(appContext.filesDir, DIR_NAME)
    private val failureLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Serializable, secret-free-framing record persisted to disk. */
    @Serializable
    data class StoredSession(
        val sessionId: String,
        val x3dhKeyWrapped: String,
        val ratchetWrapped: String? = null,
        val pendingHeader: WhisperV3Codec.X3dhWire? = null,
        val peerIkB64: String? = null,
        val createdAtMs: Long,
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
    ) {
        /** Set whenever ratchet/header state mutated; cleared by [WhisperSessionStore.save]. */
        @Volatile var dirty: Boolean = true

        /** Acceptance gate for an incoming handshake against THIS live session. */
        fun canAcceptHandshake(incomingIkB64: String, incomingSid: String): Boolean =
            StoredSession.canAcceptHandshake(sessionId, peerIkB64, incomingIkB64, incomingSid)
    }

    private val memory = ConcurrentHashMap<String, Live>()
    private val lastWrittenJson = ConcurrentHashMap<String, String>()

    // One writer at a time per peer; encrypt/decrypt callers ALSO hold this mutex so
    // concurrent flow collectors can never advance one ratchet from two threads.
    private val locks = ConcurrentHashMap<String, Mutex>()
    fun mutexFor(peerId: String): Mutex = locks.computeIfAbsent(peerId) { Mutex() }

    // ------------------------------------------------------------------ API

    /** Memory-first lookup with cold-start hydration from disk. Null = no usable session. */
    suspend fun load(peerId: String): Live? {
        memory[peerId]?.let { return it }
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val file = fileFor(peerId)
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
            lastWrittenJson[peerId] = file.readText()
            memory[peerId] = live
            live
        }
    }

    fun peek(peerId: String): Live? = memory[peerId]

    /** Persists [live] under [peerId] (memory + atomic disk write). Skips no-op writes. */
    suspend fun save(peerId: String, live: Live) {
        memory[peerId] = live
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val stored = serialize(live) ?: run {
                Log.w(TAG, "refusing to persist session for ${peerId.take(6)}… — wrap failed")
                return@withContext
            }
            val text = json.encodeToString(StoredSession.serializer(), stored)
            // Identical serialized state → skip the disk write entirely; chat scrolls
            // re-decrypt whole history and would otherwise rewrite the file per row.
            val unchanged = lastWrittenJson.put(peerId, text) == text
            if (!unchanged) writeFileAtomic(fileFor(peerId), text)
            live.dirty = false
        }
    }

    suspend fun delete(peerId: String) {        memory.remove(peerId)
        lastWrittenJson.remove(peerId)
        kotlinx.coroutines.withContext(Dispatchers.IO) { fileFor(peerId).delete() }
    }

    /** Sign-out / account-deletion wipe (planwhisper.md §3.1). */
    suspend fun deleteAll() {
        memory.clear()
        lastWrittenJson.clear()
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    // ------------------------------------------------------------------ internals

    private fun serialize(live: Live): StoredSession? =
        serializeSession(live, protector, json)

    private fun hydrate(stored: StoredSession): Live? =
        hydrateSession(stored, protector, json)

    private fun fileFor(peerId: String): File =
        File(dir, sha256Hex(peerId) + FILE_EXT)

    /** Atomic temp+rename write (same pattern as WhisperImageDiskCache). */
    private fun writeFileAtomic(target: File, text: String) {
        runCatching {
            dir.mkdirs()
            val tmp = File.createTempFile("wsess_", ".tmp", dir)
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

    private fun sha256Hex(s: String): String =
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
        )
    } finally {
        sk.fill(0)
    }
}
