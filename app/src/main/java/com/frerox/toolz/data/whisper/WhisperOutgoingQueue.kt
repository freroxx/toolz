/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P3: ciphertext-only outbox that survives process death and transient connectivity
 * loss — now backed by the encrypted Room database (`whisper_outbox`, SQLCipher via
 * AppDatabase) instead of a single JSON SharedPreferences blob.
 *
 * PUBLIC API AND SEMANTICS ARE UNCHANGED from the prefs implementation:
 *  - FIFO ordering, [MAX_ENTRIES] cap retaining the OLDEST entries;
 *  - every permanently-dropped client id surfaces through [droppedClientIds];
 *  - all mutations serialized through one mutex;
 *  - ciphertext-only rows, never plaintext (see [WhisperQueuedMessage]).
 *
 * One-time migration: a legacy `whisper_outbox` prefs file (JSON array) is imported
 * into Room on first use, then its key is removed. The import is idempotent — a
 * crash between insert and key removal just re-imports REPLACE rows harmlessly.
 */
@Singleton
class WhisperOutgoingQueue @Inject constructor(
    @ApplicationContext context: Context,
    private val outboxDao: WhisperOutboxDao,
) {
    private val legacyPrefs: SharedPreferences = context.getSharedPreferences(LEGACY_PREFS_FILE, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var legacyImportChecked = false

    // Client ids of messages permanently dropped so the UI can surface the loss.
    private val _droppedClientIds = MutableStateFlow<Set<String>>(emptySet())
    val droppedClientIds: StateFlow<Set<String>> = _droppedClientIds.asStateFlow()

    /** Imports the legacy prefs blob once per process (no-op when absent/already done). */
    private suspend fun ensureLegacyMigratedLocked() {
        if (legacyImportChecked) return
        legacyImportChecked = true
        runCatching {
            val raw = legacyPrefs.getString(KEY, null) ?: return
            val parsed = json.decodeFromString<List<WhisperQueuedMessage>>(raw)
            if (parsed.isNotEmpty()) {
                outboxDao.upsertAll(parsed.mapIndexed { idx, q ->
                    WhisperOutboxEntity.fromQueued(q, enqueuedAtMs = q.createdAt.parseEpochMsOrNull() ?: (Long.MAX_VALUE - parsed.size + idx))
                })
            }
            legacyPrefs.edit().remove(KEY).commit()
            Log.i(TAG, "Migrated ${parsed.size} outbox entr(ies) into Room")
        }.onFailure {
            // Leave the legacy blob intact for a retry on next launch.
            Log.w(TAG, "Legacy outbox import failed — will retry next launch: ${it.message}")
        }
    }

    suspend fun entries(): List<WhisperQueuedMessage> = mutex.withLock {
        ensureLegacyMigratedLocked()
        outboxDao.entries().map { it.toQueued() }
    }

    suspend fun enqueue(entry: WhisperQueuedMessage) = mutex.withLock {
        ensureLegacyMigratedLocked()
        runCatching {
            outboxDao.upsert(WhisperOutboxEntity.fromQueued(entry, System.currentTimeMillis()))
            trimToCapLocked()
        }.onFailure {
            // A failed local write mirrors the old commit-failure contract: surface
            // the loss instead of silently pretending the message is queued.
            Log.w(TAG, "Outbox write failed; entry marked dropped: ${entry.clientId}")
            noteDroppedInternal(entry.clientId)
        }
    }

    suspend fun replace(entry: WhisperQueuedMessage) = enqueue(entry)

    suspend fun remove(clientId: String) = mutex.withLock {
        ensureLegacyMigratedLocked()
        runCatching { outboxDao.delete(clientId) }
    }

    /** Records that a message was permanently dropped so the UI can surface the loss. */
    fun noteDropped(clientId: String) {
        noteDroppedInternal(clientId)
    }

    /** Drop the whole outbox (account deletion / sign-out wipe). */
    suspend fun clearAll() = mutex.withLock {
        runCatching { outboxDao.clearAll() }
        legacyPrefs.edit().remove(KEY).commit()
        // V2-FIX (reviewwhisper.md): a wiped account must not keep surfacing stale
        // drop notices from the previous account's outbox.
        _droppedClientIds.update { emptySet() }
    }

    // ------------------------------------------------------------------ internals

    private fun noteDroppedInternal(clientId: String) {
        // V2-FIX (reviewwhisper.md): read-modify-write via StateFlow.update — the old
        // `_value = _value + x` pattern could lose concurrent drops.
        _droppedClientIds.update { capDropped(it + clientId) }
    }

    /** Caller MUST hold [mutex]. Keeps the OLDEST [MAX_ENTRIES]; drops the rest. */
    private suspend fun trimToCapLocked() {
        val all = outboxDao.entries()
        if (all.size <= MAX_ENTRIES) return
        val evicted = all.drop(MAX_ENTRIES)
        evicted.forEach { runCatching { outboxDao.delete(it.clientId) } }
        // V2-FIX (reviewwhisper.md): retain the OLDEST entries instead of the newest so
        // delivery order stays monotonic; the evicted newest go to the drop ledger.
        _droppedClientIds.update { capDropped(it + evicted.map { q -> q.clientId }) }
    }

    /** Keeps the in-memory drop ledger bounded. */
    private fun capDropped(ids: Set<String>): Set<String> =
        if (ids.size > MAX_DROPPED_LEDGER) ids.toList().takeLast(MAX_DROPPED_LEDGER).toSet() else ids

    private fun String.parseEpochMsOrNull(): Long? =
        runCatching { java.time.Instant.parse(this).toEpochMilli() }.getOrNull()

    private companion object {
        const val TAG = "WhisperOutbox"
        const val MAX_ENTRIES = 100
        const val MAX_DROPPED_LEDGER = 200

        /** P3 legacy source — imported once into Room, then deleted. */
        const val LEGACY_PREFS_FILE = "whisper_outbox"
        const val KEY = "ciphertext_outbox"
    }
}
