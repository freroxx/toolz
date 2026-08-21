package com.frerox.toolz.data.whisper

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // Client ids of messages permanently dropped (attempt cap / outbox overflow) so the
    // UI can surface the loss instead of the outbox silently swallowing user messages.
    private val _droppedClientIds = MutableStateFlow<Set<String>>(emptySet())
    val droppedClientIds: StateFlow<Set<String>> = _droppedClientIds.asStateFlow()

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

    /** Entry reached the server; drop it from the outbox. */
    fun markDelivered(clientId: String) = remove(clientId)

    /** Records that a message was permanently dropped so the UI can surface the loss. */
    fun noteDropped(clientId: String) = synchronized(lock) {
        _droppedClientIds.value = (_droppedClientIds.value + clientId).let { if (it.size > 200) it.toList().takeLast(200).toSet() else it }
    }

    /** Drop the whole outbox (account deletion). */
    fun clearAll() = synchronized(lock) {
        save(emptyList())
    }

    private fun save(entries: List<WhisperQueuedMessage>) {
        // Never silently truncate the outbox: when the cap is exceeded, the overflow
        // client ids are surfaced through droppedClientIds instead of vanishing silently.
        // Cap at 100 to stay under 1 MB Binder limit (500*8KB ≈ 6 MB → TransactionTooLarge).
        val toPersist = if (entries.size > MAX_ENTRIES) {
            val overflow = entries.dropLast(MAX_ENTRIES)
            _droppedClientIds.value = (_droppedClientIds.value + overflow.map { it.clientId }).let { if (it.size > 200) it.toList().takeLast(200).toSet() else it }
            entries.takeLast(MAX_ENTRIES)
        } else {
            entries
        }
        // commit() not apply() — the outbox must survive process death immediately after
        // enqueue; apply() is async and can be lost if the process dies before flush.
        prefs.edit().putString(KEY, json.encodeToString(toPersist)).commit()
    }

    private companion object {
        const val KEY = "ciphertext_outbox"
        const val MAX_ENTRIES = 100
    }
}
