package com.frerox.toolz.data.whisper

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val mutex = Mutex()

    // Client ids of messages permanently dropped so the UI can surface the loss.
    private val _droppedClientIds = MutableStateFlow<Set<String>>(emptySet())
    val droppedClientIds: StateFlow<Set<String>> = _droppedClientIds.asStateFlow()

    fun entries(): List<WhisperQueuedMessage> =
        runCatching { json.decodeFromString<List<WhisperQueuedMessage>>(prefs.getString(KEY, "[]") ?: "[]") }
            .getOrDefault(emptyList())

    suspend fun enqueue(entry: WhisperQueuedMessage) = mutex.withLock {
        val current = entries()
        saveInternal(current.filterNot { it.clientId == entry.clientId } + entry)
    }

    suspend fun replace(entry: WhisperQueuedMessage) = enqueue(entry)

    suspend fun remove(clientId: String) = mutex.withLock {
        saveInternal(entries().filterNot { it.clientId == clientId })
    }

    /** Entry reached the server; drop it from the outbox. */
    suspend fun markDelivered(clientId: String) = remove(clientId)

    /** Records that a message was permanently dropped so the UI can surface the loss. */
    fun noteDropped(clientId: String) {
        // StateFlow update is lock-free; keep synchronous so callers don't need to be suspend.
        _droppedClientIds.value = (_droppedClientIds.value + clientId).let { if (it.size > 200) it.toList().takeLast(200).toSet() else it }
    }

    /** Drop the whole outbox (account deletion). */
    suspend fun clearAll() = mutex.withLock {
        saveInternal(emptyList())
    }

    // P1 FIX: Previously runBlocking on arbitrary dispatcher — ANR if called on Main.
    // Now strictly suspend-only. Blocking path removed; callers must be coroutine.
    // Kept as @Deprecated trampoline that enforces IO and throws if misused on Main.
    @Deprecated("Use suspend enqueue() — blocking path removed to prevent ANR", ReplaceWith("enqueue(entry)"))
    fun enqueueBlocking(entry: WhisperQueuedMessage) {
        check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            "enqueueBlocking must not be called on Main thread — use suspend enqueue()"
        }
        kotlinx.coroutines.runBlocking { enqueue(entry) }
    }

    private suspend fun saveInternal(entries: List<WhisperQueuedMessage>) {
        val toPersist = if (entries.size > MAX_ENTRIES) {
            val overflow = entries.dropLast(MAX_ENTRIES)
            _droppedClientIds.value = (_droppedClientIds.value + overflow.map { it.clientId }).let { if (it.size > 200) it.toList().takeLast(200).toSet() else it }
            entries.takeLast(MAX_ENTRIES)
        } else {
            entries
        }
        val encoded = json.encodeToString(toPersist)
        // commit() must survive process death, but never on Main — withContext(IO) keeps ANR-free
        // while still synchronous (commit returns only after fsync).
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY, encoded).commit()
        }
    }

    private companion object {
        const val KEY = "ciphertext_outbox"
        const val MAX_ENTRIES = 100
    }
}
