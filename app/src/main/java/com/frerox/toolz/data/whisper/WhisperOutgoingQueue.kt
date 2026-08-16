package com.frerox.toolz.data.whisper

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Small ciphertext-only outbox that survives process death and transient connectivity loss. */
@Singleton
class WhisperOutgoingQueue @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("whisper_outbox", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    fun entries(): List<WhisperQueuedMessage> = synchronized(lock) {
        runCatching { json.decodeFromString<List<WhisperQueuedMessage>>(prefs.getString(KEY, "[]") ?: "[]") }
            .getOrDefault(emptyList())
    }

    fun enqueue(entry: WhisperQueuedMessage) = synchronized(lock) {
        save(entries().filterNot { it.clientId == entry.clientId } + entry)
    }

    fun replace(entry: WhisperQueuedMessage) = enqueue(entry)

    fun remove(clientId: String) = synchronized(lock) {
        save(entries().filterNot { it.clientId == clientId })
    }

    private fun save(entries: List<WhisperQueuedMessage>) {
        prefs.edit().putString(KEY, json.encodeToString(entries.takeLast(MAX_ENTRIES))).apply()
    }

    private companion object {
        const val KEY = "ciphertext_outbox"
        const val MAX_ENTRIES = 100
    }
}
