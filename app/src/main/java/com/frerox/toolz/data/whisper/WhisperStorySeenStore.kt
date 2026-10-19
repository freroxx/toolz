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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local Daily Ups seen set: opaque `storyId -> expiresAtMs` pairs only.
 *
 * This mirrors [WhisperGroupReadStore] in shape and hygiene: SharedPreferences,
 * an in-memory StateFlow, no server call, no database migration. It is
 * deliberately NOT account-keyed — story ids are globally unique and the value
 * carries nothing beyond an expiry. Expired rows are filtered on load, pruned
 * from disk on refresh/write, and capped so the file can never grow without
 * bound.
 */
@Singleton
class WhisperStorySeenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("whisper_story_seen", Context.MODE_PRIVATE)

    private val _seen = MutableStateFlow(loadAll())
    val seen: StateFlow<Map<String, Long>> = _seen.asStateFlow()

    private fun loadAll(): Map<String, Long> =
        WhisperSeenCodec.decode(prefs.all, System.currentTimeMillis())

    /**
     * Records [storyId] as seen until [expiresAtMs]. Blank ids, non-positive
     * expiries and already-expired stories are ignored; the store prunes and
     * caps as part of the write.
     */
    fun markSeen(storyId: String, expiresAtMs: Long, nowMs: Long) {
        if (storyId.isBlank() || expiresAtMs <= 0L || expiresAtMs <= nowMs) return
        val next = WhisperSeenCodec.cap(
            _seen.value.filterValues { it > nowMs } + (storyId to expiresAtMs),
        )
        if (next == _seen.value) return

        val stored = prefs.all
        val editor = prefs.edit()
        stored.keys.forEach { key ->
            if (key.startsWith(WhisperSeenCodec.KEY_PREFIX) &&
                key.removePrefix(WhisperSeenCodec.KEY_PREFIX) !in next
            ) {
                editor.remove(key)
            }
        }
        next.forEach { (id, expiry) ->
            val key = WhisperSeenCodec.key(id)
            if (stored[key] != expiry) editor.putLong(key, expiry)
        }
        editor.apply()
        _seen.value = next
    }

    /** Drops expired entries from memory and disk. Safe to call on every refresh. */
    fun prune(nowMs: Long) {
        val current = _seen.value
        val next = current.filterValues { it > nowMs }
        if (next == current) return
        val editor = prefs.edit()
        (current.keys - next.keys).forEach { id ->
            editor.remove(WhisperSeenCodec.key(id))
        }
        editor.apply()
        _seen.value = next
    }

    /** Wipe on account deletion (same hygiene as the other whisper prefs). */
    suspend fun clearAll() {
        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            prefs.edit().clear().commit()
        }
        if (!ok) android.util.Log.w(TAG, "clearAll commit failed")
        _seen.value = emptyMap()
    }

    private companion object {
        const val TAG = "WhisperStorySeen"
    }
}

/**
 * Pure encode/decode rules for [WhisperStorySeenStore], extracted so the
 * corrupt-value and cap behavior can be pinned without Android.
 */
internal object WhisperSeenCodec {
    const val KEY_PREFIX = "seen_"
    const val MAX_ENTRIES = 512

    fun key(storyId: String): String = KEY_PREFIX + storyId

    /**
     * Decodes a raw preference snapshot. Only prefixed Long values newer than
     * [nowMs] survive; malformed values and keys are dropped. The newest
     * [MAX_ENTRIES] expiries win, with the story id as a deterministic
     * tie-break.
     */
    fun decode(prefs: Map<String, Any?>, nowMs: Long): Map<String, Long> {
        val parsed = buildMap<String, Long> {
            prefs.forEach { (rawKey, rawValue) ->
                if (!rawKey.startsWith(KEY_PREFIX) || rawValue !is Long) return@forEach
                val storyId = rawKey.removePrefix(KEY_PREFIX)
                if (storyId.isBlank() || rawValue <= nowMs) return@forEach
                put(storyId, rawValue)
            }
        }
        return cap(parsed)
    }

    /** Latest-expiring [MAX_ENTRIES] entries, sorted for deterministic state. */
    fun cap(entries: Map<String, Long>): Map<String, Long> =
        entries.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Long>> { it.value }
                    .thenBy { it.key },
            )
            .take(MAX_ENTRIES)
            .associate { it.key to it.value }
}
