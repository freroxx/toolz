/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase-2 groups parity slice: per-group read watermarks (ISO created_at of
 * the newest message the user has seen). Unread counts = rows newer than the
 * watermark that aren't mine. Local-only, like mute prefs.
 */
@Singleton
class WhisperGroupReadStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("whisper_group_read", Context.MODE_PRIVATE)

    private val _marks = MutableStateFlow(loadAll())
    val marks: StateFlow<Map<String, String>> = _marks.asStateFlow()

    private fun loadAll(): Map<String, String> =
        prefs.all.mapNotNull { (k, v) ->
            if (k.startsWith(KEY_PREFIX) && v is String) k.removePrefix(KEY_PREFIX) to v else null
        }.toMap()

    /** ISO created_at watermark for [groupId], "" when never opened. */
    fun readAt(groupId: String): String = _marks.value[groupId].orEmpty()

    fun markRead(groupId: String, createdAt: String) {
        if (groupId.isBlank() || createdAt.isBlank()) return
        if ((readAt(groupId)) >= createdAt) return
        prefs.edit().putString(KEY_PREFIX + groupId, createdAt).apply()
        _marks.update { it + (groupId to createdAt) }
    }

    /** Drop one group's mark (group left/disbanded). */
    fun clearGroup(groupId: String) {
        prefs.edit().remove(KEY_PREFIX + groupId).apply()
        _marks.update { it - groupId }
    }

    /** Wipe on account deletion (same hygiene as mute/notif prefs). */
    suspend fun clearAll() {
        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            prefs.edit().clear().commit()
        }
        if (!ok) android.util.Log.w(TAG, "clearAll commit failed")
        _marks.update { emptyMap() }
    }

    private companion object {
        const val TAG = "WhisperGroupRead"
        const val KEY_PREFIX = "gread_"
    }
}
