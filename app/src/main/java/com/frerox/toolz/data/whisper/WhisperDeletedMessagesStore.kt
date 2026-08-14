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

    private val _deletedIds = MutableStateFlow<Set<String>>(loadDeletedIds())
    val deletedIds: StateFlow<Set<String>> = _deletedIds.asStateFlow()

    private fun loadDeletedIds(): Set<String> {
        return prefs.getStringSet("deleted_message_ids", emptySet())?.toSet() ?: emptySet()
    }

    /**
     * Mark a message as deleted for this device.
     */
    fun markMessageDeleted(messageId: String) {
        if (messageId.isBlank()) return
        val current = _deletedIds.value
        val updated = current + messageId
        _deletedIds.value = updated
        prefs.edit().putStringSet("deleted_message_ids", updated).apply()
    }

    /**
     * Mark multiple messages as deleted for this device.
     */
    fun markMessagesDeleted(messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        val current = _deletedIds.value
        val updated = current + messageIds
        _deletedIds.value = updated
        prefs.edit().putStringSet("deleted_message_ids", updated).apply()
    }

    /**
     * Unmark messages as deleted (e.g. on Undo clear chat).
     */
    fun unmarkMessagesDeleted(messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        val current = _deletedIds.value
        val updated = current - messageIds.toSet()
        _deletedIds.value = updated
        prefs.edit().putStringSet("deleted_message_ids", updated).apply()
    }

    /**
     * Check if a message is marked as deleted.
     */
    fun isMessageDeleted(messageId: String): Boolean {
        return _deletedIds.value.contains(messageId)
    }
}
