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
 * Persists chats hidden from the chats tab ("delete chat").
 * Stores a map of partner user id -> hide timestamp (epoch millis).
 * A hidden chat comes back automatically when a new message arrives
 * after the hide timestamp, or when the chat is opened again.
 */
@Singleton
class WhisperHiddenChatsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("whisper_hidden_chats", Context.MODE_PRIVATE)

    private val _hiddenChats = MutableStateFlow<Map<String, Long>>(loadHiddenChats())
    val hiddenChats: StateFlow<Map<String, Long>> = _hiddenChats.asStateFlow()

    private fun loadHiddenChats(): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        for ((key, value) in prefs.all) {
            if (key.startsWith("hidden_") && value is Long) {
                map[key.removePrefix("hidden_")] = value
            }
        }
        return map
    }

    /** Hide a chat from the chats tab, recording when it was hidden. */
    fun hideChat(userId: String) {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("hidden_$userId", now).apply()
        _hiddenChats.value = _hiddenChats.value + (userId to now)
    }

    /** Bring a chat back to the chats tab. */
    fun unhideChat(userId: String) {
        if (userId !in _hiddenChats.value) return
        prefs.edit().remove("hidden_$userId").apply()
        _hiddenChats.value = _hiddenChats.value - userId
    }

    /** Hide timestamp (epoch millis) for a user, or null if the chat is not hidden. */
    fun hideTime(userId: String): Long? = _hiddenChats.value[userId]

    fun isHidden(userId: String): Boolean = _hiddenChats.value.containsKey(userId)

    /** Wipe every hidden-chat record on this device (account deletion). */
    fun clearAll() {
        prefs.edit().clear().apply()
        _hiddenChats.value = emptyMap()
    }
}
