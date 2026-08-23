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
        // V2-FIX (reviewwhisper.md): read-modify-write via StateFlow.update — the old
        // `_value = _value + x` pattern could lose concurrent drops.
        _droppedClientIds.update { capDropped(it + clientId) }
    }

    /** Drop the whole outbox (account deletion). */
    suspend fun clearAll() = mutex.withLock {
        saveInternal(emptyList())
        // V2-FIX (reviewwhisper.md): a wiped account must not keep surfacing stale
        // drop notices from the previous account's outbox.
        _droppedClientIds.update { emptySet() }
    }

    private suspend fun saveInternal(entries: List<WhisperQueuedMessage>) {
        val toPersist = if (entries.size > MAX_ENTRIES) {
            // V2-FIX (reviewwhisper.md): retain the OLDEST entries instead of the newest so
            // delivery order stays monotonic; the evicted newest go to the drop ledger.
            val evicted = entries.drop(MAX_ENTRIES)
            _droppedClientIds.update { capDropped(it + evicted.map { q -> q.clientId }) }
            entries.take(MAX_ENTRIES)
        } else {
            entries
        }
        // M-8: serialize under the caller's lock; decode uses the same Json config.
        val encoded = json.encodeToString(toPersist)
        // commit() must survive process death, but never on Main — withContext(IO) keeps ANR-free
        // while still synchronous (commit returns only after fsync).
        val ok = withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY, encoded).commit()
        }
        if (!ok) {
            // V2-FIX (reviewwhisper.md): a failed commit silently lost the queue before;
            // mirror every affected client id into the drop ledger so the loss surfaces.
            Log.w(TAG, "Outbox commit failed; ${toPersist.size} queued message(s) marked dropped")
            _droppedClientIds.update { capDropped(it + toPersist.map { q -> q.clientId }) }
        }
    }

    /** Keeps the in-memory drop ledger bounded. */
    private fun capDropped(ids: Set<String>): Set<String> =
        if (ids.size > MAX_DROPPED_LEDGER) ids.toList().takeLast(MAX_DROPPED_LEDGER).toSet() else ids

    private companion object {
        const val TAG = "WhisperOutbox"
        const val KEY = "ciphertext_outbox"
        const val MAX_ENTRIES = 100
        const val MAX_DROPPED_LEDGER = 200
    }
}
