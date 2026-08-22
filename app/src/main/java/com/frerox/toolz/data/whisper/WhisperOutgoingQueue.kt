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

    /**
     * M-8 FIX (reviewwhisper.md): reads now go through the same mutex as writes.
     * Previously `entries()` read the raw pref snapshot outside the lock, which only
     * worked by accident of SharedPreferences snapshot semantics.
     */
    suspend fun entries(): List<WhisperQueuedMessage> = mutex.withLock { entriesInternal() }

    /** Caller MUST hold [mutex]. */
    private fun entriesInternal(): List<WhisperQueuedMessage> =
        runCatching { json.decodeFromString<List<WhisperQueuedMessage>>(prefs.getString(KEY, "[]") ?: "[]") }
            .getOrDefault(emptyList())

    suspend fun enqueue(entry: WhisperQueuedMessage) = mutex.withLock {
        val current = entriesInternal()
        saveInternal(current.filterNot { it.clientId == entry.clientId } + entry)
    }

    suspend fun replace(entry: WhisperQueuedMessage) = enqueue(entry)

    suspend fun remove(clientId: String) = mutex.withLock {
        saveInternal(entriesInternal().filterNot { it.clientId == clientId })
    }

    /** Records that a message was permanently dropped so the UI can surface the loss. */
    fun noteDropped(clientId: String) {
        // StateFlow update is lock-free; keep synchronous so callers don't need to be suspend.
        _droppedClientIds.value = (_droppedClientIds.value + clientId).let { if (it.size > 200) it.toList().takeLast(200).toSet() else it }
    }

    /** Drop the whole outbox (account deletion). */
    suspend fun clearAll() = mutex.withLock {
        saveInternal(emptyList())
    }

    private suspend fun saveInternal(entries: List<WhisperQueuedMessage>) {
        val toPersist = if (entries.size > MAX_ENTRIES) {
            val overflow = entries.dropLast(MAX_ENTRIES)
            _droppedClientIds.value = (_droppedClientIds.value + overflow.map { it.clientId }).let { if (it.size > 200) it.toList().takeLast(200).toSet() else it }
            entries.takeLast(MAX_ENTRIES)
        } else {
            entries
        }
        // M-8: serialize under the caller's lock; decode uses the same Json config.
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
