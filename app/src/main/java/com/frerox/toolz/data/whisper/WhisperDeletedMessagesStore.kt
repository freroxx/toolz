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
 */
@Singleton
class WhisperDeletedMessagesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("whisper_deleted_msgs", Context.MODE_PRIVATE)

    private val lock = Any()
    private val _deletedIds = MutableStateFlow<Set<String>>(loadDeletedIds())
    val deletedIds: StateFlow<Set<String>> = _deletedIds.asStateFlow()

    private fun loadDeletedIds(): Set<String> = cleanExpired(prefs.getStringSet("deleted_message_ids", emptySet()).orEmpty()).keys

    private fun cleanExpired(entries: Set<String>, now: Long = System.currentTimeMillis()): Map<String, Long> {
        val cutoff = now - TOMBSTONE_RETENTION_MS
        val parsed = entries.mapNotNull { entry ->
            val split = entry.lastIndexOf('|')
            // Legacy entries had no timestamp. Retain them for one cleanup window rather than forever.
            val id = if (split > 0) entry.substring(0, split) else entry
            val timestamp = if (split > 0) entry.substring(split + 1).toLongOrNull() else now
            timestamp?.let { id to it }
        }.filter { (_, timestamp) -> timestamp >= cutoff }
        return parsed.toMap()
    }

    private fun persist(ids: Set<String>) {
        val existing = cleanExpired(prefs.getStringSet("deleted_message_ids", emptySet()).orEmpty())
        val now = System.currentTimeMillis()
        val merged = existing + ids.associateWith { now }
        prefs.edit().putStringSet("deleted_message_ids", merged.map { (id, timestamp) -> "$id|$timestamp" }.toSet()).apply()
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
            val existing = cleanExpired(prefs.getStringSet("deleted_message_ids", emptySet()).orEmpty()) - messageIds.toSet()
            prefs.edit().putStringSet("deleted_message_ids", existing.map { (id, timestamp) -> "$id|$timestamp" }.toSet()).apply()
            _deletedIds.value = existing.keys
        }
    }

    /**
     * Check if a message is marked as deleted.
     */
    fun isMessageDeleted(messageId: String): Boolean {
        return _deletedIds.value.contains(messageId)
    }

    /** Bounds the local delete-for-me index so it cannot grow indefinitely. */
    fun purgeExpired(): Int = synchronized(lock) {
        val raw = prefs.getStringSet("deleted_message_ids", emptySet()).orEmpty()
        val cleaned = cleanExpired(raw)
        val removed = raw.size - cleaned.size
        if (removed > 0) {
            prefs.edit().putStringSet("deleted_message_ids", cleaned.map { (id, timestamp) -> "$id|$timestamp" }.toSet()).apply()
            _deletedIds.value = cleaned.keys
        }
        removed
    }

    private companion object {
        const val TOMBSTONE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
