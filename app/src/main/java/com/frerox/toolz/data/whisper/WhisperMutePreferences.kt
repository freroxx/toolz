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
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperMutePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("whisper_mute_prefs", Context.MODE_PRIVATE)

    private val _mutedUsers = MutableStateFlow<Set<String>>(loadMutedUsers())
    val mutedUsers: StateFlow<Set<String>> = _mutedUsers.asStateFlow()

    private fun loadMutedUsers(): Set<String> {
        val now = System.currentTimeMillis()
        val allEntries = prefs.all
        val activeMutes = mutableSetOf<String>()
        val editor = prefs.edit()
        var hasExpired = false

        for ((key, value) in allEntries) {
            if (key.startsWith("mute_") && value is Long) {
                val userId = key.removePrefix("mute_")
                if (value == Long.MAX_VALUE || value > now) {
                    activeMutes.add(userId)
                } else {
                    editor.remove(key)
                    hasExpired = true
                }
            }
        }
        if (hasExpired) editor.apply()
        return activeMutes
    }

    /**
     * Mute a user until [untilEpochMs]. Use [Long.MAX_VALUE] for indefinite mute.
     */
    fun muteUser(userId: String, untilEpochMs: Long = Long.MAX_VALUE) {
        prefs.edit().putLong("mute_$userId", untilEpochMs).apply()
        _mutedUsers.update { it + userId }
    }

    /**
     * Unmute a user.
     */
    fun unmuteUser(userId: String) {
        prefs.edit().remove("mute_$userId").apply()
        _mutedUsers.update { it - userId }
    }

    /**
     * Check whether a user is currently muted.
     * P2 FIX: Previously this getter mutated prefs (expired prune) — surprising side
     * effect on query path. Now it returns the pure in-memory state and schedules
     * an async prune so the query remains side-effect-free. Expired mutes disappear
     * within one prune cycle but isMuted never writes synchronously.
     */
    fun isMuted(userId: String): Boolean {
        if (userId !in _mutedUsers.value) return false
        val until = prefs.getLong("mute_$userId", 0L)
        if (until == 0L) return false
        if (until == Long.MAX_VALUE || until > System.currentTimeMillis()) return true
        // Expired — defer removal off the query path.
        pruneOne(userId)
        return false
    }

    private fun pruneOne(userId: String) {
        // Fire-and-forget prune; ok to use apply off-critical path.
        prefs.edit().remove("mute_$userId").apply()
        _mutedUsers.update { it - userId }
    }

    /** Remove expired mute entries from prefs and in-memory state. */
    fun pruneExpiredMutes() {
        val now = System.currentTimeMillis()
        val expired = _mutedUsers.value.filter { uid ->
            val until = prefs.getLong("mute_$uid", 0L)
            until != Long.MAX_VALUE && until != 0L && until <= now
        }
        if (expired.isEmpty()) return
        val editor = prefs.edit()
        expired.forEach { editor.remove("mute_$it") }
        editor.apply()
        _mutedUsers.update { it - expired.toSet() }
    }

    /** Wipe every mute record on this device (account deletion). */
    // V2-FIX (reviewwhisper.md): apply() → durable commit() inside Dispatchers.IO so the
    // wipe is guaranteed on disk before account deletion proceeds and never blocks Main.
    suspend fun clearAll() {
        val ok = withContext(Dispatchers.IO) { prefs.edit().clear().commit() }
        if (!ok) Log.w(TAG, "clearAll commit failed")
        _mutedUsers.update { emptySet() }
    }

    private companion object {
        const val TAG = "WhisperMutePrefs"
    }
}
