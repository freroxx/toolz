package com.frerox.toolz.data.whisper

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists IDs of messages deleted locally ("delete for me" / clear chat).
 * Local cache is capped at 5k for disk, but remote `whisper_deleted_tombstones`
 * is the source of truth so evicted IDs never resurrect after reinstall.
 */
@Singleton
class WhisperDeletedMessagesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("whisper_deleted_msgs", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    // P2: Previously uncancelled CoroutineScope(Dispatchers.IO) looked like a leak.
    // For @Singleton the scope lifetime == app lifetime, so not a true leak. Mark with
    // SupervisorJob for structured error handling and explicit app-scope.
    private val ioScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private val _deletedIds = MutableStateFlow<Set<String>>(loadDeletedIds())
    val deletedIds: StateFlow<Set<String>> = _deletedIds.asStateFlow()

    private fun loadDeletedIds(): Set<String> = capById(loadAll()).keys

    private fun loadAll(): Map<String, Long> = prefs.getStringSet("deleted_message_ids", emptySet()).orEmpty()
        .mapNotNull { entry ->
            val split = entry.lastIndexOf('|')
            val id = if (split > 0) entry.substring(0, split) else entry
            val timestamp = if (split > 0) entry.substring(split + 1).toLongOrNull() else 0L
            timestamp?.let { id to it }
        }.toMap()

    private fun capById(entries: Map<String, Long>): Map<String, Long> {
        if (entries.size <= MAX_TOMBSTONES) return entries
        val byAge = entries.entries.sortedBy { it.value }
        return byAge.takeLast(MAX_TOMBSTONES).associate { it.key to it.value }
    }

    private suspend fun persist(ids: Set<String>) {
        val existing = loadAll()
        val now = System.currentTimeMillis()
        val merged = capById(existing + ids.associateWith { now })
        withContext(Dispatchers.IO) {
            prefs.edit().putStringSet("deleted_message_ids", merged.map { (id, timestamp) -> "$id|$timestamp" }.toSet()).commit()
        }
        _deletedIds.value = merged.keys
    }

    /** Non-suspend fire-and-forget for UI callers; repository should use suspend variant. */
    fun markMessageDeleted(messageId: String) {
        if (messageId.isBlank()) return
        ioScope.launch { mutex.withLock { persist(setOf(messageId)) } }
    }

    fun markMessagesDeleted(messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        val clean = messageIds.filter { it.isNotBlank() }.toSet()
        if (clean.isEmpty()) return
        ioScope.launch { mutex.withLock { persist(clean) } }
    }

    suspend fun markMessageDeletedSuspend(messageId: String) {
        if (messageId.isBlank()) return
        mutex.withLock { persist(setOf(messageId)) }
    }

    suspend fun markMessagesDeletedSuspend(messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        val clean = messageIds.filter { it.isNotBlank() }.toSet()
        if (clean.isEmpty()) return
        mutex.withLock { persist(clean) }
    }

    suspend fun unmarkMessagesDeleted(messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        mutex.withLock {
            val existing = loadAll() - messageIds.toSet()
            withContext(Dispatchers.IO) {
                prefs.edit().putStringSet("deleted_message_ids", existing.map { (id, timestamp) -> "$id|$timestamp" }.toSet()).commit()
            }
            _deletedIds.value = existing.keys
        }
    }

    fun isMessageDeleted(messageId: String): Boolean = _deletedIds.value.contains(messageId)

    /** Evicts the oldest tombstone IDs when store exceeds MAX_TOMBSTONES. */
    suspend fun evictOldest(): Int = mutex.withLock {
        val raw = loadAll()
        val capped = capById(raw)
        val removed = raw.size - capped.size
        if (removed > 0) {
            withContext(Dispatchers.IO) {
                prefs.edit().putStringSet("deleted_message_ids", capped.map { (id, timestamp) -> "$id|$timestamp" }.toSet()).commit()
            }
            _deletedIds.value = capped.keys
        }
        removed
    }

    @Deprecated("Renamed to evictOldest to clarify size-based capping", ReplaceWith("evictOldest()"))
    suspend fun purgeExpired(): Int = evictOldest()

    suspend fun clearAll() = mutex.withLock {
        withContext(Dispatchers.IO) { prefs.edit().remove("deleted_message_ids").commit() }
        _deletedIds.value = emptySet()
    }

    fun clearAllBlocking() = kotlinx.coroutines.runBlocking { clearAll() }

    private companion object {
        const val MAX_TOMBSTONES = 5_000
    }
}
