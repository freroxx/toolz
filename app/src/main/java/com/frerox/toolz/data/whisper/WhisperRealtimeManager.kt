package com.frerox.toolz.data.whisper

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.RealtimeChannel.Status
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P0-6 FIX: Extracted from WhisperRepository god file (1884 lines).
 * Centralizes RealtimeChannel cache + Mutex + subscribe/double-subscribe
 * handling so Repository no longer owns channel lifecycle.
 * This file is the first of 5 split modules: Realtime, Conversations,
 * Friends, Presence, Tombstones. Next PR continues extraction.
 */
@Singleton
class WhisperRealtimeManager @Inject constructor(
    private val supabase: SupabaseClient,
) {
    private val broadcastChannelCache = mutableMapOf<String, RealtimeChannel>()
    private val channelMutex = Mutex()

    suspend fun getOrJoinBroadcastChannel(name: String): RealtimeChannel {
        val cached = channelMutex.withLock {
            broadcastChannelCache[name]?.let {
                try {
                    if (it.status.value == Status.SUBSCRIBED) return@withLock it
                } catch (_: Exception) {}
                runCatching { supabase.realtime.removeChannel(it) }
            }
            null
        }
        if (cached != null) return cached
        val channel = supabase.channel(name)
        channel.subscribe()
        return channelMutex.withLock {
            val concurrent = broadcastChannelCache[name]
            if (concurrent != null) {
                try {
                    if (concurrent.status.value == Status.SUBSCRIBED) {
                        runCatching { supabase.realtime.removeChannel(channel) }
                        return@withLock concurrent
                    }
                } catch (_: Exception) {}
            }
            broadcastChannelCache[name] = channel
            channel
        }
    }

    suspend fun removeCachedChannel(name: String, channel: RealtimeChannel) {
        channelMutex.withLock {
            if (broadcastChannelCache[name] === channel) broadcastChannelCache.remove(name)
        }
        runCatching { supabase.realtime.removeChannel(channel) }
    }

    suspend fun clearAll() {
        channelMutex.withLock {
            broadcastChannelCache.values.forEach { runCatching { supabase.realtime.removeChannel(it) } }
            broadcastChannelCache.clear()
        }
    }
}
