package com.frerox.toolz.data.whisper

import javax.inject.Inject
import javax.inject.Singleton

/**
 * P0-6 FIX: Extracted conversation cache from WhisperRepository.
 * In-memory TTL + cache-key that includes blocks/hidden/mutes so stale
 * conversations cannot resurrect after a block/mute/hide toggle.
 * Repository delegates getConversations cache check to this manager.
 */
@Singleton
class WhisperConversationCache @Inject constructor() {
    @Volatile private var cache: List<WhisperConversation>? = null
    @Volatile private var cacheTime: Long = 0L
    private val ttlMs = 30_000L

    fun getIfFresh(
        forceRefresh: Boolean,
        blocksHash: Int,
        hiddenHash: Int,
        muteHash: Int,
    ): List<WhisperConversation>? {
        if (forceRefresh) return null
        val now = System.currentTimeMillis()
        val cached = cache ?: return null
        if ((now - cacheTime) > ttlMs) return null
        // Cache key includes mutes/blocks/hidden so UI never sees stale filtered list.
        // Stored hashes are simple xor of set hashCodes; collisions negligible for cache.
        val key = blocksHash xor hiddenHash xor muteHash
        // If key mismatched we treat cache as stale — caller will forceRefresh.
        // We store last key alongside cache for comparison.
        if (key != lastKey) return null
        return cached
    }

    @Volatile private var lastKey: Int = 0

    fun put(conversations: List<WhisperConversation>, blocksHash: Int, hiddenHash: Int, muteHash: Int) {
        cache = conversations
        cacheTime = System.currentTimeMillis()
        lastKey = blocksHash xor hiddenHash xor muteHash
    }

    fun invalidate() {
        cache = null
        cacheTime = 0L
        lastKey = 0
    }
}
