/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * H-4 FIX (reviewwhisper.md): persists the clear-chat UNDO buffer.
 *
 * The old buffer lived only in the ChatViewModel's memory, so a process death during
 * the 30-second undo window silently destroyed the restore data while the partner's
 * rows were already tombstoned remotely — an unrecoverable loss. Now the ciphertext
 * buffer (never plaintext) survives process death and a fresh ViewModel can resume
 * the countdown window from disk.
 *
 * Only ciphertext/tombstone rows are stored here: [WhisperMessage] is serialized as-is
 * and every entry must carry `contentIv` or be a tombstone (same invariant that
 * repository.restoreMessages enforces before re-inserting).
 */
@Singleton
class WhisperUndoBufferStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("whisper_undo_buffer", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    /** Persisted envelope so the resumed countdown can distinguish fresh vs stale buffers. */
    fun savedAtMs(): Long = prefs.getLong(KEY_SAVED_AT, 0L)

    suspend fun save(messages: List<WhisperMessage>) {
        if (messages.isEmpty()) {
            clear()
            return
        }
        mutex.withLock {
            val now = System.currentTimeMillis()
            val payload = kotlinx.serialization.json.buildJsonObject {
                put("savedAt", now)
                put("messages", json.encodeToString(messages.takeLast(MAX_BUFFERED_MESSAGES)))
            }
            withContext(Dispatchers.IO) {
                prefs.edit()
                    .putString(KEY_PAYLOAD, payload.toString())
                    .putLong(KEY_SAVED_AT, now)
                    .commit()
            }
        }
    }

    suspend fun load(): List<WhisperMessage> = mutex.withLock {
        val raw = prefs.getString(KEY_PAYLOAD, null) ?: return@withLock emptyList()
        runCatching {
            val obj = json.parseToJsonElement(raw).jsonObjectSafe()
            val messagesJson = obj?.get("messages")?.jsonArray ?: return@withLock emptyList()
            messagesJson.mapNotNull { element ->
                // Defense in depth: never resurrect plaintext without IV/tombstone marker.
                runCatching { json.decodeFromString<WhisperMessage>(element.toString()) }.getOrNull()
            }.filter { msg ->
                msg.contentIv != null || WhisperTombstone.isTombstone(msg.content) ||
                    msg.content == WhisperTombstone.LEGACY_ENCRYPTED
            }
        }.getOrDefault(emptyList())
    }

    suspend fun clear() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                prefs.edit().remove(KEY_PAYLOAD).remove(KEY_SAVED_AT).commit()
            }
        }
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectSafe(): kotlinx.serialization.json.JsonObject? =
        this as? kotlinx.serialization.json.JsonObject

    private companion object {
        const val KEY_PAYLOAD = "undo_payload"
        const val KEY_SAVED_AT = "saved_at"
        const val MAX_BUFFERED_MESSAGES = 2_000
    }
}
