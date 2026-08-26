/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.frerox.toolz.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P3: persists IDs of messages deleted locally ("delete for me" / clear chat) —
 * now backed by the encrypted Room table `whisper_local_tombstones` instead of a
 * SharedPreferences string-set that was rewritten wholesale on every change.
 *
 * PUBLIC API AND SEMANTICS ARE UNCHANGED:
 *  - [deletedIds] StateFlow mirrors the persisted set for flow-level filtering;
 *  - writes are serialized, capped to the NEWEST [MAX_TOMBSTONES] by delete time;
 *  - the remote `whisper_deleted_tombstones` table stays the durable source of
 *    truth so evicted IDs never resurrect after reinstall.
 *
 * Migration: the legacy `whisper_deleted_msgs` prefs file is seeded SYNCHRONOUSLY
 * into the initial in-memory set (so the very first hub render already filters
 * correctly), then flushed into Room once on the app scope and removed. The Room
 * import is idempotent (REPLACE upserts).
 */
@Singleton
class WhisperDeletedMessagesStore @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    private val tombstoneDao: WhisperLocalTombstoneDao,
) {
    private val legacyPrefs: SharedPreferences = context.getSharedPreferences(LEGACY_PREFS_FILE, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _deletedIds = MutableStateFlow<Set<String>>(loadLegacyIds())
    val deletedIds: StateFlow<Set<String>> = _deletedIds.asStateFlow()

    init {
        appScope.launch(Dispatchers.IO) { importLegacyIntoRoomAndReload() }
    }

    /** Non-suspend fire-and-forget for UI callers; repository should use suspend variant. */
    fun markMessageDeleted(messageId: String) {
        if (messageId.isBlank()) return
        appScope.launch { markMessagesDeletedSuspend(setOf(messageId)) }
    }

    fun markMessagesDeleted(messageIds: Collection<String>) {
        val clean = messageIds.filter { it.isNotBlank() }.toSet()
        if (clean.isEmpty()) return
        appScope.launch { markMessagesDeletedSuspend(clean) }
    }

    suspend fun markMessageDeletedSuspend(messageId: String) {
        if (messageId.isBlank()) return
        markMessagesDeletedSuspend(setOf(messageId))
    }

    suspend fun markMessagesDeletedSuspend(messageIds: Collection<String>) {
        val clean = messageIds.filter { it.isNotBlank() }.toSet()
        if (clean.isEmpty()) return
        mutex.withLock {
            val now = System.currentTimeMillis()
            runCatching { tombstoneDao.upsertAll(clean.map { WhisperLocalTombstoneEntity(it, now) }) }
                .onFailure { Log.w(TAG, "tombstone upsert failed (${clean.size} id(s) at risk): ${it.message}") }
            reloadWithCapLocked()
        }
    }

    suspend fun unmarkMessagesDeleted(messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        mutex.withLock {
            runCatching { tombstoneDao.deleteAllByIds(messageIds.filter { it.isNotBlank() }) }
                .onFailure { Log.w(TAG, "tombstone unmark failed: ${it.message}") }
            reloadLocked()
        }
    }

    fun isMessageDeleted(messageId: String): Boolean = _deletedIds.value.contains(messageId)

    /** Evicts the oldest tombstone IDs when the store exceeds MAX_TOMBSTONES. */
    suspend fun evictOldest(): Int = mutex.withLock {
        val all = tombstoneDao.entriesOldestFirst()
        if (all.size <= MAX_TOMBSTONES) return@withLock 0
        val evicted = all.take(all.size - MAX_TOMBSTONES)
        runCatching { tombstoneDao.deleteAllByIds(evicted.map { it.messageId }) }
            .onFailure { Log.w(TAG, "evictOldest delete failed: ${it.message}") }
        reloadLocked()
        evicted.size
    }

    suspend fun clearAll() = mutex.withLock {
        runCatching { tombstoneDao.clearAll() }
        legacyPrefs.edit().remove(KEY_DELETED_IDS).commit()
        _deletedIds.value = emptySet()
    }

    // ------------------------------------------------------------------ internals

    private suspend fun reloadWithCapLocked() {
        val all = tombstoneDao.entriesOldestFirst()
        if (all.size > MAX_TOMBSTONES) {
            val evicted = all.take(all.size - MAX_TOMBSTONES)
            runCatching { tombstoneDao.deleteAllByIds(evicted.map { it.messageId }) }
        }
        reloadLocked()
    }

    private suspend fun reloadLocked() {
        runCatching {
            _deletedIds.value = tombstoneDao.entriesOldestFirst().mapTo(mutableSetOf()) { it.messageId }
        }
    }

    private suspend fun importLegacyIntoRoomAndReload() {
        mutex.withLock {
            runCatching {
                val legacy = loadLegacyEntries()
                if (legacy.isNotEmpty()) {
                    tombstoneDao.upsertAll(legacy.map { (id, ts) -> WhisperLocalTombstoneEntity(id, ts) })
                    // Clear only after the copy landed (same ordering contract as the
                    // KeyTrustStore ESP migration): a crash before this line re-imports
                    // harmlessly on next launch.
                    legacyPrefs.edit().remove(KEY_DELETED_IDS).commit()
                    Log.i(TAG, "Migrated ${legacy.size} local tombstones into Room")
                }
                reloadLocked()
            }.onFailure {
                Log.w(TAG, "legacy tombstone import deferred (retry next launch): ${it.message}")
            }
        }
    }

    /** Legacy reader — entries are "messageId|epochMillis". */
    private fun loadLegacyEntries(): List<Pair<String, Long>> =
        legacyPrefs.getStringSet(KEY_DELETED_IDS, emptySet()).orEmpty().mapNotNull { entry ->
            val split = entry.lastIndexOf('|')
            val id = if (split > 0) entry.substring(0, split) else entry
            val timestamp = if (split > 0) entry.substring(split + 1).toLongOrNull() else 0L
            timestamp?.let { id to it }
        }

    private fun loadLegacyIds(): Set<String> = loadLegacyEntries().map { it.first }.toSet()

    private companion object {
        const val TAG = "WhisperDeletedMsgs"
        const val MAX_TOMBSTONES = 5_000

        /** P3 legacy source — imported once into Room, then deleted. */
        const val LEGACY_PREFS_FILE = "whisper_deleted_msgs"
        const val KEY_DELETED_IDS = "deleted_message_ids"
    }
}
