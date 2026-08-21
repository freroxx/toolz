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
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists IDs of messages deleted locally ("delete for me" / clear chat).
 * Ensures that deleted messages never reappear when reloading messages from the database or cache.
 *
 * Tombstones never expire by age — time-based pruning would resurrect deleted messages —
 * so the set is bounded only by a count cap that evicts the OLDEST ids first.
 */
@Singleton
class WhisperDeletedMessagesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("whisper_deleted_msgs", Context.MODE_PRIVATE)

    private val lock = Any()
    private val _deletedIds = MutableStateFlow<Set<String>>(loadDeletedIds())
    val deletedIds: StateFlow<Set<String>> = _deletedIds.asStateFlow()

    private fun loadDeletedIds(): Set<String> = capById(loadAll()).keys

    /** Reads every stored tombstone with no time-based pruning. Legacy entries (no
     * timestamp) get a 0 timestamp so the count cap evicts them first. */
    private fun loadAll(): Map<String, Long> = prefs.getStringSet("deleted_message_ids", emptySet()).orEmpty()
        .mapNotNull { entry ->
            val split = entry.lastIndexOf('|')
            val id = if (split > 0) entry.substring(0, split) else entry
            val timestamp = if (split > 0) entry.substring(split + 1).toLongOrNull() else 0L
            timestamp?.let { id to it }
        }
        .toMap()

    /** Bounds the tombstone set by count, evicting the OLDEST ids by stored timestamp. */
    private fun capById(entries: Map<String, Long>): Map<String, Long> {
        if (entries.size <= MAX_TOMBSTONES) return entries
        val byAge = entries.entries.sortedBy { it.value }
        return byAge.takeLast(MAX_TOMBSTONES).associate { it.key to it.value }
    }

    private fun persist(ids: Set<String>) {
        val existing = loadAll()
        val now = System.currentTimeMillis()
        val merged = capById(existing + ids.associateWith { now })
        // With up to MAX_TOMBSTONES entries the prefs string grows to a few hundred KB;
        // commit() ensures tombstones survive process death immediately — delete-for-me
        // must never resurrect after a crash — and the cap keeps storage bounded.
        prefs.edit().putStringSet("deleted_message_ids", merged.map { (id, timestamp) -> "$id|$timestamp" }.toSet()).commit()
        _deletedIds.value = merged.keys
    }

    /**
     * Mark a message as deleted for this device.
     */
    fun markMessageDeleted(messageId: String) {
        if (messageId.isBlank()) return
        synchronized(lock) { persist(setOf(messageId)) }
    }

    /**
     * Mark multiple messages as deleted for this device.
     */
    fun markMessagesDeleted(messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        synchronized(lock) { persist(messageIds.filter { it.isNotBlank() }.toSet()) }
    }

    /**
     * Unmark messages as deleted (e.g. on Undo clear chat).
     */
    fun unmarkMessagesDeleted(messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        synchronized(lock) {
            val existing = loadAll() - messageIds.toSet()
            prefs.edit().putStringSet("deleted_message_ids", existing.map { (id, timestamp) -> "$id|$timestamp" }.toSet()).commit()
            _deletedIds.value = existing.keys
        }
    }

    /**
     * Check if a message is marked as deleted.
     */
    fun isMessageDeleted(messageId: String): Boolean {
        return _deletedIds.value.contains(messageId)
    }

    /**
     * Enforces the count cap only — tombstones never expire by age, so deleted messages
     * can never resurrect. Evicts the OLDEST ids when the cap is exceeded.
     */
    fun purgeExpired(): Int = synchronized(lock) {
        val raw = loadAll()
        val capped = capById(raw)
        val removed = raw.size - capped.size
        if (removed > 0) {
            prefs.edit().putStringSet("deleted_message_ids", capped.map { (id, timestamp) -> "$id|$timestamp" }.toSet()).commit()
            _deletedIds.value = capped.keys
        }
        removed
    }

    /** Wipe every tombstone on this device (account deletion). */
    fun clearAll() = synchronized(lock) {
        prefs.edit().remove("deleted_message_ids").commit()
        _deletedIds.value = emptySet()
    }

    private companion object {
        const val MAX_TOMBSTONES = 5_000
    }
}
