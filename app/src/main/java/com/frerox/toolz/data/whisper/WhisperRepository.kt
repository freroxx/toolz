/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.data.whisper

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast.BroadcastPayload
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeOldRecord
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

import com.frerox.toolz.R
import com.frerox.toolz.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

@Singleton
class WhisperRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val crypto: WhisperCrypto,
    private val encryptedImageHost: WhisperEncryptedImageHost,
    private val deletedStore: WhisperDeletedMessagesStore,
    private val outgoingQueue: WhisperOutgoingQueue,
    private val deliveryScheduler: WhisperDeliveryScheduler,
    private val offlineManager: com.frerox.toolz.util.OfflineManager,
    private val messageDao: WhisperMessageDao,
    private val keyTrustStore: WhisperKeyTrustStore,
    private val hiddenChatsStore: WhisperHiddenChatsStore,
    private val mutePrefs: WhisperMutePreferences,
    // H-1 FIX (reviewwhisper.md): channel teardown must run even though the flow's
    // ProducerScope is already cancelled when awaitClose fires — a plain `launch {}`
    // there never executes. This app-lifetime scope survives collection cancellation.
    @ApplicationScope private val appScope: kotlinx.coroutines.CoroutineScope,
) {
    private companion object {
        const val MAX_MESSAGE_CHARS = 8_192
        const val EVENT_DEDUPE_TTL_MS = 30_000L
        const val MAX_RECENT_EVENT_IDS = 1_024
        val SUPPORTED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        const val MIN_IMAGE_EXPIRY_SECONDS = 60L
        const val MAX_IMAGE_EXPIRY_SECONDS = 15_552_000L
    }
    private val db get() = supabase.postgrest
    private val store get() = supabase.storage
    private val realtime get() = supabase.realtime
    val myId get() = supabase.auth.currentUserOrNull()?.id ?: ""

    private val profileCache = ConcurrentHashMap<String, WhisperProfile>()
    private val profileCacheTs = ConcurrentHashMap<String, Long>()
    private val PROFILE_CACHE_TTL_MS = 5 * 60 * 1000L

    /** Cached only when fresh enough — a permanent cache made profile edits ghost until process death. */
    private fun cachedProfile(userId: String): WhisperProfile? {
        val ts = profileCacheTs[userId] ?: return null
        if (System.currentTimeMillis() - ts > PROFILE_CACHE_TTL_MS) return null
        return profileCache[userId]
    }

    private fun cacheProfile(userId: String, profile: WhisperProfile) {
        profileCache[userId] = profile
        profileCacheTs[userId] = System.currentTimeMillis()
    }

    // In-memory partner public keys for offline decryption of cached ciphertext.
    // Only ever populated from authenticated profile reads; the persisted fallback
    // (keyTrustStore) holds the key the user last accepted, so a changed key can
    // never silently decrypt old or new material.
    private val peerKeys = MutableStateFlow<Map<String, String>>(emptyMap())

    // Pairs (userId|publicKey) already surfaced through receiveKeyChanged, so a changed
    // key is signalled only once per change instead of on every profile read.
    private val receiveKeyNotified = Collections.synchronizedSet(mutableSetOf<String>())
    private val _receiveKeyChanged = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Emits a partner userId when their fresh server key differs from the accepted trusted key. */
    val receiveKeyChanged: Flow<String> = _receiveKeyChanged

    private fun cachePeerKey(userId: String, publicKey: String?) {
        if (!publicKey.isNullOrBlank()) {
            peerKeys.update { it + (userId to publicKey) }
            // Signal a possible key change (MITM / reinstall) once per (user, key) pair so
            // the chat screen can prompt a safety-number review without spamming.
            val trusted = keyTrustStore.knownKey(userId)
            if (trusted != null && trusted != publicKey && receiveKeyNotified.add("$userId|$publicKey")) {
                _receiveKeyChanged.tryEmit(userId)
            }
        }
    }

    // Users this account has blocked, kept in memory for flow-level filtering of cached history.
    private val blockedIds = MutableStateFlow<Set<String>>(emptySet())

    /** Trusted key first (TOFU): the in-memory fresh server key is only a first-contact fallback. */
    private fun peerKeyFor(userId: String): String? =
        keyTrustStore.knownKey(userId) ?: peerKeys.value[userId]

    /** Decrypts a cached ciphertext message for display, falling back to a neutral marker. */
    private fun WhisperMessage.decryptContent(peerKey: String?): WhisperMessage {
        if (isDeletedForEveryone || contentIv == null) return this
        val key = peerKey ?: return copy(content = "[Encrypted message]")
        // H-7 FIX (reviewwhisper.md): memoized — getMessagesFlow re-decrypts up to 500
        // cached rows on EVERY Room emission; each miss costs an ECDH+HKDF+GCM round
        // through AndroidKeyStore. The memo bounds that to one derive per unique row.
        val decrypted = decryptMemoized(content, contentIv, key, senderId, receiverId)
        return copy(content = decrypted ?: "[Encrypted message]")
    }

    /**
     * LRU memo for [crypto.decryptMessage]. Keyed by (direction, key, iv, ciphertext);
     * bounded to 512 entries so plaintext memory stays capped. Only successful
     * decryptions are cached (failures stay cheap and retryable).
     */
    private val decryptMemo = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > 512
    }

    private fun decryptMemoized(
        rawCipher: String,
        ivBase64: String?,
        peerKey: String,
        senderId: String,
        receiverId: String,
    ): String? {
        if (ivBase64.isNullOrBlank()) return null
        val memoKey = "$senderId\u0000$receiverId\u0000$peerKey\u0000$ivBase64\u0000$rawCipher"
        synchronized(decryptMemo) { decryptMemo[memoKey]?.let { return it } }
        val out = crypto.decryptMessage(rawCipher, ivBase64, peerKey, senderId, receiverId) ?: return null
        synchronized(decryptMemo) { decryptMemo[memoKey] = out }
        return out
    }
    // Cache conversations list to avoid full reload on every new message in the chats hub
    // L-3 FIX (reviewwhisper.md): one @Volatile immutable snapshot instead of two
    // independently-volatile fields (value + timestamp could tear across threads).
    private data class ConversationsCacheEntry(val value: List<WhisperConversation>, val at: Long)
    @Volatile private var conversationsCache: ConversationsCacheEntry? = null
    private val CONVERSATIONS_CACHE_TTL = 30_000L // 30 seconds

    // Persistent broadcast channels keyed by channel name — shared across send/react/delete
    // so we don't subscribe to a brand-new channel object for each outgoing event.
    private val broadcastChannelCache = mutableMapOf<String, io.github.jan.supabase.realtime.RealtimeChannel>()
    private val channelMutex = Mutex()
    // Serializes outbox flushes so concurrent worker lanes can never double-insert a row.
    private val flushMutex = Mutex()
    private val recentlyEmittedMessageIds = ConcurrentHashMap<String, Long>()

    private suspend fun getOrJoinBroadcastChannel(name: String): io.github.jan.supabase.realtime.RealtimeChannel {
        // Read the cache and channel status under the lock, but never hold the mutex across
        // subscribe(): subscribing may block, and holding it would stall every other channel op.
        val cached = channelMutex.withLock {
            broadcastChannelCache[name]?.let {
                try {
                    if (it.status.value == io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED) {
                        return@withLock it
                    }
                } catch (_: Exception) {}
                runCatching { realtime.removeChannel(it) }
            }
            null
        }
        if (cached != null) return cached

        val channel = supabase.channel(name)
        channel.subscribe()

        // Re-check under the lock before inserting: another lane may have joined the same
        // channel while we were subscribing. The double-subscribe race is acceptable; the
        // cache keeps a single entry, preferring the one that is already SUBSCRIBED.
        return channelMutex.withLock {
            val concurrent = broadcastChannelCache[name]
            if (concurrent != null) {
                try {
                    if (concurrent.status.value == io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED) {
                        runCatching { realtime.removeChannel(channel) }
                        return@withLock concurrent
                    }
                } catch (_: Exception) {}
            }
            broadcastChannelCache[name] = channel
            channel
        }
    }

    private suspend fun removeCachedChannel(name: String, channel: io.github.jan.supabase.realtime.RealtimeChannel) {
        channelMutex.withLock {
            if (broadcastChannelCache[name] === channel) broadcastChannelCache.remove(name)
        }
        runCatching { realtime.removeChannel(channel) }
    }

    /** Broadcast and Postgres changes describe the same write; emit it only once to consumers. */
    private fun shouldEmitMessage(id: String): Boolean {
        if (id.isBlank()) return true
        val now = System.currentTimeMillis()
        val previous = recentlyEmittedMessageIds.putIfAbsent(id, now)
        if (recentlyEmittedMessageIds.size > MAX_RECENT_EVENT_IDS) {
            // Age-based eviction keeps the map bounded; entries older than 30s are stale
            // enough that a duplicate may safely be emitted again.
            recentlyEmittedMessageIds.entries.removeIf { (_, timestamp) -> now - timestamp > EVENT_DEDUPE_TTL_MS }
            // If still over the cap (burst of long-lived ids), drop the oldest third
            // regardless of age so the map can never grow unbounded.
            if (recentlyEmittedMessageIds.size > MAX_RECENT_EVENT_IDS) {
                val idsByAge = recentlyEmittedMessageIds.entries.sortedBy { it.value }
                idsByAge.take(idsByAge.size / 3).forEach { recentlyEmittedMessageIds.remove(it.key) }
            }
        }
        if (previous == null) return true
        if (now - previous <= EVENT_DEDUPE_TTL_MS) return false
        recentlyEmittedMessageIds[id] = now
        return true
    }

    fun invalidateConversationsCache() {
        conversationsCache = null
    }    /**
     * L-4: performs up to [times] TOTAL attempts (the final attempt happens after the
     * loop). 4xx (except 408/429) and CancellationException abort immediately.
     */
    private suspend fun <T> retryWithBackoff(
        times: Int = 3,
        initialDelayMs: Long = 300,
        maxDelayMs: Long = 1200,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e is io.github.jan.supabase.exceptions.RestException && e.statusCode in 400..404) throw e
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
            }
        }
        return block()
    }

    // PROFILES
    suspend fun getMyProfile(forceRefresh: Boolean = false): Result<WhisperProfile> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) error("User not authenticated")

        if (!forceRefresh) {
            cachedProfile(currentId)?.let { cached ->
                if (cached.publicKey != null) return Result.success(cached)
            }
        }

        val existing = db.from("profiles")
            .select { filter { eq("id", currentId) } }
            .decodeList<WhisperProfile>()
            .firstOrNull()

        val pubKey = crypto.getPublicKeyBase64()

        val profile = if (existing == null) {
            val user = supabase.auth.currentUserOrNull()
            val metadata = user?.userMetadata
            val metaUsername = metadata?.get("username")?.toString()?.removeSurrounding("\"")?.takeIf { it != "null" }
            val metaDisplayName = metadata?.get("display_name")?.toString()?.removeSurrounding("\"")?.takeIf { it != "null" }
            
            val initialUsername = metaUsername ?: "user_${currentId.take(8)}"
            val insertData = WhisperProfileInsert(
                id = currentId,
                username = initialUsername,
                displayName = metaDisplayName,
                isPrivate = false,
                publicKey = pubKey
            )
            db.from("profiles").insert(insertData) { defaultToNull = false }
            WhisperProfile(id = currentId, username = initialUsername, displayName = metaDisplayName, isPrivate = false, publicKey = pubKey)
        } else {
            val user = supabase.auth.currentUserOrNull()
            val metadata = user?.userMetadata
            val metaDisplayName = metadata?.get("display_name")?.toString()?.removeSurrounding("\"")?.takeIf { it != "null" }

            // Check if username is an ugly 64-char token string and normalize it
            val isUglyHexUsername = existing.username.length >= 32 &&
                existing.username.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

            val cleanUsername = if (isUglyHexUsername) "anon_${existing.username.take(6)}" else null
            
            val needsDisplayName = existing.displayName.isNullOrBlank() && !metaDisplayName.isNullOrBlank()
            // Self-heal only when the server has NO key (fresh row after reinstall/clear
            // data). Reinstalling with a DIFFERENT key now flows through the key-change
            // review flow instead of silently clobbering the server's key.
            val needsKey = pubKey != null && existing.publicKey.isNullOrBlank()

            if (needsKey || cleanUsername != null || needsDisplayName) {
                // Build the body without nulls: a null field would be serialized as
                // "field": null and NULL the server column (e.g. username) on self-heal.
                val body = buildJsonObject {
                    if (needsKey) put("public_key", pubKey!!)
                    cleanUsername?.let { put("username", it) }
                    if (needsDisplayName) put("display_name", metaDisplayName!!)
                }
                db.from("profiles").update(body) { filter { eq("id", currentId) } }
                existing.copy(
                    publicKey = if (needsKey) pubKey else existing.publicKey,
                    username = cleanUsername ?: existing.username,
                    displayName = if (needsDisplayName) metaDisplayName else existing.displayName
                )
            } else existing
        }
        cacheProfile(currentId, profile)
        profile
    }

    suspend fun getProfile(userId: String, forceRefresh: Boolean = false): Result<WhisperProfile> = runCatching {
        if (!forceRefresh) {
            cachedProfile(userId)?.let { cached ->
                if (!cached.publicKey.isNullOrBlank() || userId == myId) {
                    cachePeerKey(userId, cached.publicKey)
                    return Result.success(cached)
                }
            }
        }
        val p = db.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingle<WhisperProfile>()
        cacheProfile(userId, p)
        cachePeerKey(userId, p.publicKey)
        p
    }

    suspend fun searchProfiles(query: String): Result<List<WhisperProfile>> = runCatching {
        val q = query.trim()
        // Gate trivial queries: a single-char ilike '%x%' scan per debounce wastes DB
        // cycles and produces noisy result churn while typing.
        if (q.length < 2) return@runCatching emptyList()
        // M-13 FIX (reviewwhisper.md): escape the backslash FIRST — a literal "\" in the
        // query would otherwise neutralize the %/_ escapes that follow it.
        val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        db.from("profiles")
            .select {
                filter {
                    or {
                        ilike("username", "%$escaped%")
                        ilike("display_name", "%$escaped%")
                    }
                    neq("id", myId)
                    eq("hide_from_discover", false)
                    eq("is_private", false)
                }
                limit(30)
            }
            .decodeList()
    }

    suspend fun checkUsernameAvailable(username: String): Result<Boolean> = runCatching {
        val results = db.from("profiles")
            .select { filter { eq("username", username.trim().lowercase()) } }
            .decodeList<WhisperProfile>()
        results.isEmpty()
    }

    suspend fun updateProfile(update: WhisperProfileUpdate): Result<Unit> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) error("User not authenticated")
        val pubKey = crypto.getPublicKeyBase64()
        // Build the body explicitly so unset fields (especially public_key) are OMITTED:
        // supabase-kt serializes nulls by default, and "public_key": null would NULL the
        // server's column. The local key is only pushed when explicitly requested or when
        // the server row has none to begin with — never overwritten with a mismatch.
        val body = buildJsonObject {
            update.username?.let { put("username", it) }
            // Empty strings must reach the server to clear a field; null means "don't touch".
            if (update.displayName != null) put("display_name", update.displayName)
            if (update.bio != null) put("bio", update.bio)
            if (update.avatarUrl != null) put("avatar_url", update.avatarUrl)
            update.isPrivate?.let { put("is_private", it) }
            update.isHiddenFromDiscover?.let { put("hide_from_discover", it) }
            update.lastSeenAt?.let { put("last_seen_at", it) }
            when {
                update.publicKey != null -> put("public_key", update.publicKey!!)
                pubKey != null -> {
                    val serverKeyResult = runCatching {
                        db.from("profiles").select { filter { eq("id", currentId) } }
                            .decodeSingleOrNull<WhisperProfile>()?.publicKey
                    }
                    if (serverKeyResult.isSuccess && serverKeyResult.getOrNull().isNullOrBlank()) put("public_key", pubKey)
                }
            }
        }
        db.from("profiles").update(body) { filter { eq("id", currentId) } }
        profileCache.remove(currentId); profileCacheTs.remove(currentId)
    }

    suspend fun updateLastSeen(): Result<Unit> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return@runCatching
        val now = java.time.OffsetDateTime.now().toString()
        val body = buildJsonObject { put("last_seen_at", now) }
        db.from("profiles").update(body) {
            filter { eq("id", currentId) }
        }
    }

    suspend fun uploadAvatar(imageBytes: ByteArray, mimeType: String): Result<String> = runCatching {
        val ext = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val path = "$myId/avatar.$ext"
        store.from("whisper-avatars").upload(path, imageBytes) { upsert = true }
        // L-16 FIX (reviewwhisper.md): persist the CLEAN public URL. The old `?t=` cache
        // buster was stored server-side, defeating every OTHER viewer's image cache on
        // each upload; busting is now done at render time from profile.updatedAt.
        val publicUrl = store.from("whisper-avatars").publicUrl(path)
        updateProfile(WhisperProfileUpdate(avatarUrl = publicUrl))
        profileCache.remove(myId); profileCacheTs.remove(myId)
        publicUrl
    }

    suspend fun deleteAvatar(): Result<Unit> = runCatching {
        // Remove the underlying blob before clearing the reference so failed deletions
        // never leave an orphaned file behind.
        runCatching {
            val current = db.from("profiles").select { filter { eq("id", myId) } }
                .decodeSingleOrNull<WhisperProfile>()
            current?.avatarUrl?.let { url ->
                // Robust object-path extraction from the public URL:
                //   <origin>/storage/v1/object/public/whisper-avatars/<userId>/avatar.ext?...
                // Parse via URL segments instead of substringAfter so encoded chars,
                // repeated substrings, or query strings can never corrupt the path.
                val path = runCatching {
                    val parsed = java.net.URL(url)
                    val segments = parsed.path.split("/").filter { it.isNotBlank() }
                    val bucketIdx = segments.indexOf("whisper-avatars")
                    if (bucketIdx >= 0 && bucketIdx < segments.lastIndex) {
                        segments.drop(bucketIdx + 1).joinToString("/") { java.net.URLDecoder.decode(it, "UTF-8") }
                    } else ""
                }.getOrDefault("")
                if (path.isNotBlank()) store.from("whisper-avatars").delete(path)
            }
        }
        db.from("profiles").update(mapOf("avatar_url" to null as String?)) {
            filter { eq("id", myId) }
        }
        profileCache.remove(myId); profileCacheTs.remove(myId)
    }

    // ─────────────────────────────────────────────────────────────
    // MESSAGES & REALTIME CHAT
    // ─────────────────────────────────────────────────────────────

    private fun conversationKey(userA: String, userB: String): String =
        if (userA < userB) "${userA}_${userB}" else "${userB}_${userA}"

    fun getMessagesFlow(otherUserId: String): Flow<List<WhisperMessage>> {
        val currentId = myId
        if (currentId.isBlank()) return flowOf(emptyList())
        // Room stores only ciphertext. Decrypt at read time with the accepted peer key so
        // nothing plaintext ever lands on disk, while the UI still renders full history.
        return combine(
            messageDao.getMessages(currentId, otherUserId),
            peerKeys.map { it[otherUserId] },
            blockedIds
        ) { entities, inMemoryKey, blocked ->
            val peerKey = keyTrustStore.knownKey(otherUserId) ?: inMemoryKey
            entities
                // Never resurrect delete-for-me tombstones from the Room cache.
                .filterNot { deletedStore.isMessageDeleted(it.id) }
                // Hidden/blocked partners keep only the user's own outgoing rows.
                .filter { it.senderId == currentId || otherUserId !in blocked }
                .map { it.toModel().decryptContent(peerKey) }
        }
    }

    /** Key used to decrypt a partner's cached ciphertext (accepted key, never a changed one). */
    fun getDecryptionKey(peerId: String): String? = peerKeyFor(peerId)

    suspend fun getMessages(otherUserId: String, limit: Int = 100, beforeCreatedAt: String? = null): Result<List<WhisperMessage>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())
        
        val partnerProfile = getProfile(otherUserId, forceRefresh = true).getOrNull()

        // If I blocked this user, do not load their incoming messages
        val isBlocked = isUserBlockedByMe(otherUserId)

        val rawMessages = retryWithBackoff {
            db.from("messages")
                .select {
                    filter {
                        or {
                            and {
                                eq("sender_id", currentId)
                                eq("receiver_id", otherUserId)
                            }
                            and {
                                eq("sender_id", otherUserId)
                                eq("receiver_id", currentId)
                            }
                        }
                        if (beforeCreatedAt != null) lt("created_at", beforeCreatedAt)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<WhisperMessage>()
        }

        // Filter out locally deleted messages (and the partner's rows while blocked)
        val visibleRaw = rawMessages
            .filter { msg -> (!isBlocked || msg.senderId == currentId) && !deletedStore.isMessageDeleted(msg.id) }

        // Decrypt with the trusted key only (never a changed fresh server key), falling
        // back to a neutral marker instead of leaking raw ciphertext.
        val decryptedMessages = visibleRaw
            .map { msg -> msg.decryptContent(peerKeyFor(otherUserId)) }

        // Fetch reactions for all messages
        val messageIds = decryptedMessages.map { it.id }.filter { it.isNotBlank() }
        val reactionsMap = runCatching { getReactionsForMessages(messageIds).getOrDefault(emptyMap()) }.getOrDefault(emptyMap())

        val idToMsgMap = decryptedMessages.associateBy { it.id }

        // Enrich with reply-to snippets and reactions
        val finalMessages = decryptedMessages.map { msg ->
            val replySnippet = msg.replyToId?.let { replyId ->
                idToMsgMap[replyId]?.let { target ->
                    val senderName = if (target.senderId == currentId) {
                        "You"
                    } else {
                        partnerProfile?.effectiveName ?: "User"
                    }
                    val content = if (target.content.startsWith("whisper:image:")) {
                        "Image"
                    } else {
                        target.content.take(100)
                    }
                    Pair(content, senderName)
                }
            }
            msg.copy(
                replyToContent = replySnippet?.first,
                replyToSenderName = replySnippet?.second,
                reactions = reactionsMap[msg.id] ?: emptyList()
            )
        }
        
        // Cache fetched messages as ciphertext only (never the decrypted plaintext)
        if (visibleRaw.isNotEmpty()) {
            messageDao.insertMessages(visibleRaw.map { it.toEntity() })
        }

        finalMessages.reversed()
    }

    suspend fun sendMessage(
        receiverId: String,
        content: String,
        replyToId: String? = null
    ): Result<WhisperMessage> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) error("User not authenticated")
        
        if (isUserBlockedByMe(receiverId)) {
            error("You have blocked this user. Unblock to send messages.")
        }
        if (isUserBlockedByOther(receiverId)) {
            error("You have been blocked by this user.")
        }
        require(content.isNotBlank() && content.length <= MAX_MESSAGE_CHARS) { "Message must be between 1 and $MAX_MESSAGE_CHARS characters." }
        val receiverProfile = getProfile(receiverId, forceRefresh = true).getOrNull()
        val receiverPubKey = receiverProfile?.publicKey
        val knownKey = keyTrustStore.knownKey(receiverId)
        if (knownKey != null && receiverPubKey != null && knownKey != receiverPubKey) {
            // P0-1 FIX (reviewwhisper.md): previously EVERY key mismatch hard-blocked
            // sending — including routine monthly fleet auto-rotations — bricking
            // conversations until manual review. Now only a genuinely unexpected
            // (stale, non-fresh) change blocks; fresh scheduled rotations are
            // auto-accepted and pinned so subsequent sends are consistent.
            if (isFreshServerRotation(receiverProfile)) {
                keyTrustStore.rememberKey(receiverId, receiverPubKey)
                cachePeerKey(receiverId, receiverPubKey)
            } else {
                error("Safety number changed for this contact. Review and accept the new key before sending.")
            }
        }
        val encryptedPair = receiverPubKey?.let { key -> crypto.encryptMessage(content, key, currentId, receiverId) }
            ?: error("Secure delivery is unavailable because this user has no valid encryption key.")
        // Client-generated UUID makes the insert idempotent: if the server accepted the row
        // but the response was lost, retries and the outbox flush hit the same primary key
        // and are recognized as already-delivered instead of duplicating the message.
        val clientId = java.util.UUID.randomUUID().toString()
        val insert = run {
            WhisperMessageInsert(
                id = clientId,
                senderId = currentId,
                receiverId = receiverId,
                content = encryptedPair.first,
                contentIv = encryptedPair.second,
                replyToId = replyToId
            )
        }
        val insertedMsg = runCatching {
            retryWithBackoff(times = 4, initialDelayMs = 500, maxDelayMs = 4_000) {
                db.from("messages").insert(insert) { select() }.decodeSingle<WhisperMessage>()
            }
        }.getOrElse {
            // Preserve only ciphertext. The periodic network-constrained worker retries after
            // process death or an offline transition without leaving cleartext on disk.
            val queued = WhisperQueuedMessage(
                clientId = clientId,
                senderId = currentId,
                receiverId = receiverId,
                encryptedContent = encryptedPair.first,
                contentIv = encryptedPair.second,
                replyToId = replyToId,
                createdAt = java.time.Instant.now().toString(),
            )
            outgoingQueue.enqueue(queued)
            deliveryScheduler.scheduleNow()
            
            val pendingMsg = WhisperMessage(
                id = clientId,
                senderId = currentId,
                receiverId = receiverId,
                content = queued.encryptedContent,
                contentIv = queued.contentIv,
                replyToId = replyToId,
                isPending = true,
                createdAt = queued.createdAt
            )
            // Cache pending ciphertext (never plaintext) so Room never holds cleartext;
            // getMessagesFlow decrypts it on read via peerKeyFor.
            messageDao.insertMessage(pendingMsg.toEntity())
            // Return plaintext for immediate UI; only ciphertext is persisted.
            return@runCatching pendingMsg.copy(content = content, contentIv = null)
        }
        
        val clearMsg = insertedMsg.copy(content = content)
        // Cache the successfully sent message as ciphertext only (never the plaintext).
        // getMessagesFlow decrypts it on read using the recipient's accepted public key.
        messageDao.insertMessage(insertedMsg.toEntity())

        // Invalidate conversation cache so the chats list refreshes
        invalidateConversationsCache()

        clearMsg
    }

    /** Encrypts image bytes on-device, then uploads only ciphertext through the authenticated Edge Function. */
    suspend fun sendEncryptedImage(
        receiverId: String,
        imageBytes: ByteArray,
        mimeType: String,
        expiresAfterSeconds: Long? = null,
        replyToId: String? = null,
    ): Result<WhisperMessage> = runCatching {
        require(mimeType in SUPPORTED_IMAGE_TYPES) { "Whisper supports JPEG, PNG, and WebP images." }
        require(expiresAfterSeconds == null || expiresAfterSeconds in MIN_IMAGE_EXPIRY_SECONDS..MAX_IMAGE_EXPIRY_SECONDS) {
            "Disappearing images must expire between 1 minute and 180 days."
        }
        val myIdNow = myId
        if (myIdNow.isBlank()) error("User not authenticated")
        val receiverKey = getProfile(receiverId, forceRefresh = true).getOrNull()?.publicKey
            ?: error("Secure image delivery is unavailable because this user has no encryption key.")
        val (cipherBytes, iv) = crypto.encryptAttachment(imageBytes, receiverKey, myIdNow, receiverId)
            ?: error("This image is too large or could not be encrypted.")
        
        val (uploadUrl, attachmentId) = encryptedImageHost.upload(
            cipherBytes = cipherBytes,
            name = "whisper_${System.currentTimeMillis()}",
            expirationSeconds = expiresAfterSeconds,
        ).getOrThrow()
        
        val expiresAt = expiresAfterSeconds?.let { java.time.Instant.now().epochSecond + it }
        val attachment = WhisperImageAttachment(
            url = uploadUrl,
            iv = iv,
            mimeType = mimeType,
            attachmentId = attachmentId,
            expiresAtEpochSeconds = expiresAt,
            sizeBytes = imageBytes.size,
        )
        sendMessage(receiverId, attachment.toMessageContent(), replyToId).getOrThrow()
    }

    suspend fun downloadEncryptedImage(
        attachment: WhisperImageAttachment,
        peerId: String?,
        peerPublicKey: String?,
    ): Result<ByteArray> = runCatching {
        if (attachment.expiresAtEpochSeconds != null && java.time.Instant.now().epochSecond >= attachment.expiresAtEpochSeconds) {
            error("This disappearing image has expired.")
        }
        val rawBytes = encryptedImageHost.download(attachment.url).getOrThrow()

        // 1. Attempt to decode via lossless PNG transport (ImgBB host)
        val decodedPng = WhisperImageCipherTransport.decode(rawBytes)
        val candidateCipher = decodedPng ?: rawBytes

        // 2. Decrypt bound to the original direction. The AAD binds (senderId, receiverId),
        // so we MUST try both real orderings: the partner sent it to me (peer, me), or I
        // sent it to the partner (me, peer). decryptAttachment also keeps an internal
        // constant-AAD fallback for legacy rows created before direction binding.
        val myIdNow = myId
        fun tryDecrypt(bytes: ByteArray, sender: String, receiver: String) =
            crypto.decryptAttachment(bytes, attachment.iv, peerPublicKey, sender, receiver)

        val boundPairs: List<Pair<String, String>> = when {
            peerId.isNullOrBlank() || peerId == myIdNow -> listOf("" to myIdNow, myIdNow to "")
            else -> listOf(peerId to myIdNow, myIdNow to peerId)
        }
        val decrypted = boundPairs.firstNotNullOfOrNull { (sender, receiver) ->
            tryDecrypt(candidateCipher, sender, receiver)
        } ?: if (decodedPng != null) {
            boundPairs.firstNotNullOfOrNull { (sender, receiver) ->
                tryDecrypt(rawBytes, sender, receiver)
            }
        } else null
        decrypted ?: error("Unable to decrypt this image on this device.")
    }


    /** True when the server rejected an insert because the row already exists (idempotent client UUID retry). */
    private fun isDuplicateKeyError(e: Throwable): Boolean =
        (e is io.github.jan.supabase.exceptions.RestException && e.statusCode == 409) ||
            e.message?.contains("duplicate key", ignoreCase = true) == true ||
            e.message?.contains("23505") == true

    /** Replays only this signed-in user's ciphertext outbox; safe to call repeatedly. */
    suspend fun flushOutgoingMessages(): Int {
        if (myId.isBlank()) return 0
        return flushMutex.withLock {
            var delivered = 0
            outgoingQueue.entries().filter { it.senderId == myId }.forEach { queued ->
                if (queued.attempts >= 8) {
                    android.util.Log.w("WhisperRepo", "Dropping undeliverable queued message after ${queued.attempts} attempts (clientId=${queued.clientId})")
                    outgoingQueue.remove(queued.clientId)
                    // Surface the permanent loss so the UI can toast it instead of silently
                    // swallowing the user's message.
                    outgoingQueue.noteDropped(queued.clientId)
                    return@forEach
                }
                val result = runCatching {
                    db.from("messages").insert(
                        WhisperMessageInsert(
                            id = queued.clientId,
                            senderId = queued.senderId,
                            receiverId = queued.receiverId,
                            content = queued.encryptedContent,
                            contentIv = queued.contentIv,
                            replyToId = queued.replyToId,
                            createdAt = queued.createdAt,
                        )
                    )
                }
                if (result.isSuccess) {
                    outgoingQueue.remove(queued.clientId)
                    // Remove the pending ghost row from Room and insert authoritative ciphertext
                    // so UI shows it even before getMessages realtime catch-up.
                    runCatching { messageDao.deleteMessage(queued.clientId) }
                    runCatching {
                        val authoritative = WhisperMessage(
                            id = queued.clientId,
                            senderId = queued.senderId,
                            receiverId = queued.receiverId,
                            content = queued.encryptedContent,
                            contentIv = queued.contentIv,
                            replyToId = queued.replyToId,
                            createdAt = queued.createdAt
                        )
                        messageDao.insertMessage(authoritative.toEntity())
                    }
                    delivered++
                } else {
                    val error = result.exceptionOrNull()
                    if (error != null && isDuplicateKeyError(error)) {
                        // The row already exists server-side (a prior attempt delivered it but
                        // the response was lost): treat as delivered, never duplicate.
                        outgoingQueue.remove(queued.clientId)
                        runCatching { messageDao.deleteMessage(queued.clientId) }
                        runCatching {
                            val authoritative = WhisperMessage(
                                id = queued.clientId,
                                senderId = queued.senderId,
                                receiverId = queued.receiverId,
                                content = queued.encryptedContent,
                                contentIv = queued.contentIv,
                                replyToId = queued.replyToId,
                                createdAt = queued.createdAt
                            )
                            messageDao.insertMessage(authoritative.toEntity())
                        }
                        delivered++
                    } else {
                        outgoingQueue.replace(queued.copy(attempts = queued.attempts + 1))
                    }
                }
            }
            if (delivered > 0) invalidateConversationsCache()
            delivered
        }
    }

    // ─────────────────────────────────────────────────────────────
    // REACTIONS
    // ─────────────────────────────────────────────────────────────

    suspend fun toggleReaction(messageId: String, emoji: String, otherUserId: String? = null): Result<Unit> = runCatching {
        if (messageId.isBlank()) return@runCatching
        val existing = db.from("message_reactions").select {
            filter {
                eq("message_id", messageId)
                eq("user_id", myId)
                eq("emoji", emoji)
            }
        }.decodeList<WhisperMessageReactionRow>().firstOrNull()

        if (existing != null) {
            db.from("message_reactions").delete {
                filter { eq("id", existing.id) }
            }
        } else {
            db.from("message_reactions").insert(
                WhisperMessageReactionInsert(messageId = messageId, userId = myId, emoji = emoji)
            )
        }

        if (!otherUserId.isNullOrBlank()) {
            runCatching {
                val convoKey = conversationKey(myId, otherUserId)
                val chatChannel = getOrJoinBroadcastChannel("chat_$convoKey")
                chatChannel.broadcast(
                    event = "reaction_update",
                    payload = BroadcastPayload.Json(
                        buildJsonObject {
                            put("message_id", messageId)
                            put("user_id", myId)
                            put("emoji", emoji)
                        }
                    )
                )
            }
        }
    }

    suspend fun getReactionsForMessages(messageIds: List<String>): Result<Map<String, List<WhisperReactionSummary>>> = runCatching {
        if (messageIds.isEmpty()) return@runCatching emptyMap()
        val rows = db.from("message_reactions").select {
            filter {
                isIn("message_id", messageIds)
            }
        }.decodeList<WhisperMessageReactionRow>()

        val result = mutableMapOf<String, MutableList<WhisperReactionSummary>>()
        val groupedByMsg = rows.groupBy { it.messageId }
        for ((msgId, msgRows) in groupedByMsg) {
            val byEmoji = msgRows.groupBy { it.emoji }
            val summaries = byEmoji.map { (emoji, rList) ->
                WhisperReactionSummary(
                    emoji = emoji,
                    count = rList.size,
                    userIds = rList.map { it.userId },
                    reactedByMe = rList.any { it.userId == myId }
                )
            }
            result[msgId] = summaries.toMutableList()
        }
        result
    }

    // H-5 FIX (reviewwhisper.md): the unused `senderDisplayName` parameter was removed —
    // it invited drift with WhisperTombstone.CONTENT_PREFIX while the server write only
    // ever used DISPLAY_TEXT. The tombstone is written from the single shared constant.
    suspend fun deleteMessageForEveryone(messageId: String, otherUserId: String): Result<Unit> = runCatching {
        // A message still in the local outbox was never delivered server-side: drop it
        // locally instead of attempting a server tombstone that cannot match a row.
        // (Client ids are now plain UUIDs, so also check queue membership directly.)
        if (messageId.startsWith("queued_") || outgoingQueue.entries().any { it.clientId == messageId }) {
            outgoingQueue.remove(messageId)
            messageDao.deleteMessage(messageId)
            invalidateConversationsCache()
            return@runCatching
        }

        // 1. Fetch message to check for attachments. Room holds ciphertext, so decrypt with
        // the accepted peer key before parsing the attachment envelope.
        val message = messageDao.getMessageById(messageId)
        val attachment: WhisperImageAttachment? = message?.let { m ->
            val raw = if (m.contentIv != null) {
                crypto.decryptMessage(m.content, m.contentIv, peerKeyFor(otherUserId), m.senderId, m.receiverId) ?: m.content
            } else m.content
            WhisperImageAttachment.fromMessageContent(raw)
        }
        
        // 2. Update database with tombstone, selecting back the rows it affected so a
        // mismatch (0 rows) is surfaced instead of silently "succeeding".
        val tombstone = WhisperTombstone.DISPLAY_TEXT
        val updated = db.from("messages").update(
            buildJsonObject {
                put("content", tombstone)
                put("content_iv", null as String?)
            }
        ) {
            filter {
                eq("id", messageId)
                eq("sender_id", myId)
            }
            select()
        }.decodeList<WhisperMessage>()
        
        // 3. Erase from local cache only when the server accepted the tombstone.
        if (updated.isNotEmpty()) {
            messageDao.deleteMessage(messageId)
            invalidateConversationsCache()
            // 4. Only now delete remote attachment — if tombstone RLS rejects (0 rows),
            // the blob must remain so a retry can still succeed.
            attachment?.let { att ->
                runCatching { encryptedImageHost.delete(att.url, att.attachmentId) }
            }
        } else {
            error("This message could not be deleted. It may have been removed already.")
        }
    }

    suspend fun deleteMessageForMe(messageId: String): Result<Unit> = runCatching {
        if (messageId.isBlank()) return@runCatching
        // 1. Mark as deleted locally (commit on IO) and mirror remotely so eviction never resurrects
        deletedStore.markMessageDeletedSuspend(messageId)
        runCatching { syncDeletedTombstonesRemote(listOf(messageId)) }
        // 2. Erase from local Room cache
        messageDao.deleteMessage(messageId)
        // 3. Invalidate conversation cache
        invalidateConversationsCache()
    }

    suspend fun markMessagesAsRead(senderId: String): Result<Unit> = runCatching {
        db.from("messages").update({ set("is_read", true) }) {
            filter { eq("sender_id", senderId); eq("receiver_id", myId); eq("is_read", false) }
        }
        messageDao.markAsRead(senderId, myId)
    }

    /**
     * Realtime decrypt with TOFU: try the trusted (accepted) key first; only on a FIRST
     * contact (no trusted key yet) fetch the fresh server key once; anything else renders
     * as a neutral placeholder instead of chasing profiles or leaking raw ciphertext.
     */
    private suspend fun decryptRealtimeMessage(msg: WhisperMessage, peerId: String): String {
        if (msg.isDeletedForEveryone) return msg.content
        val contentIv = msg.contentIv ?: return msg.content
        val trustedKey = peerKeyFor(peerId)
        trustedKey?.let { key ->
            crypto.decryptMessage(msg.content, contentIv, key, msg.senderId, msg.receiverId)?.let { return it }
        }
        if (trustedKey == null) {
            getProfile(peerId).getOrNull()?.publicKey?.let { key ->
                crypto.decryptMessage(msg.content, contentIv, key, msg.senderId, msg.receiverId)?.let { return it }
            }
        }
        return "[Encrypted message]"
    }

    /**
     * Dual-Channel Realtime Chat Flow:
     * Combines Supabase Realtime Broadcast (<50ms) + Postgres Changes for messages and reactions.
     */
    fun subscribeToChat(otherUserId: String): Flow<WhisperChatEvent> = callbackFlow {
        val currentId = myId
        if (currentId.isEmpty() || otherUserId.isEmpty()) {
            close()
            return@callbackFlow
        }

        val convoKey = conversationKey(currentId, otherUserId)
        val channelName = "chat_$convoKey"
        
        // CRITICAL: Supabase caches channel objects. If we get a cached channel that's already
        // subscribed, calling postgresChangeFlow will crash. We MUST remove any existing channel
        // first to ensure we start with a clean, unsubscribed channel object.
        channelMutex.withLock {
            broadcastChannelCache[channelName]?.let {
                runCatching { realtime.removeChannel(it) }
                broadcastChannelCache.remove(channelName)
            }
        }
        
        val channel = supabase.channel(channelName)

        // Listen for Postgres Changes on messages. Realtime broadcasts are deliberately
        // NOT consumed: they cannot prove sender identity and are therefore never used as
        // a message source, cache fill, or notification trigger.
        //
        // P0-2 FIX (reviewwhisper.md): both flows now carry SERVER-SIDE filters instead of
        // streaming every RLS-visible row of the whole table and filtering client-side.
        // Realtime supports a single-column filter per subscription, so the conversation is
        // expressed as TWO subscriptions on one channel; RLS guarantees rows outside this
        // conversation can never appear under these filters:
        //   • partner → me:      sender_id = otherUserId  (∩ RLS: receiver_id = me)
        //   • me → partner:      receiver_id = otherUserId (∩ RLS: sender_id = me)
        val incomingChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
            // Single-column filter per subscription (API constraint); RLS supplies the
            // receiver_id = auth.uid() half of the conjunction.
            filter("sender_id", FilterOperator.EQ, otherUserId)
        }
        val outgoingChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
            filter("receiver_id", FilterOperator.EQ, otherUserId)
        }
        val pMsgJob = launch {
            merge(incomingChanges, outgoingChanges).collect { action ->
                try {
                    val msg = when (action) {
                        is PostgresAction.Insert -> action.decodeRecord<WhisperMessage>()
                        is PostgresAction.Update -> action.decodeRecord<WhisperMessage>()
                        is PostgresAction.Delete -> action.decodeOldRecord<WhisperMessage>()
                        else -> null
                    }
                    if (msg != null && (
                        (msg.senderId == otherUserId && msg.receiverId == myId) ||
                        (msg.senderId == myId && msg.receiverId == otherUserId)
                    )) {
                        if (isUserBlockedByMe(msg.senderId)) return@collect

                        // Hard server-side row deletion: mirror it in the cache immediately.
                        if (action is PostgresAction.Delete) {
                            launch { messageDao.deleteMessage(msg.id) }
                            if (shouldEmitMessage(msg.id)) trySend(WhisperChatEvent.DeleteEvent(msg.id))
                            return@collect
                        }

                        // Decrypt with the trusted key only (TOFU): a changed key can never
                        // silently decrypt material, and decrypt failures never leak ciphertext.
                        val decrypted = decryptRealtimeMessage(msg, otherUserId)

                        val finalMsg = msg.copy(content = decrypted)
                        
                        // Sync ciphertext to cache in background (never the decrypted plaintext).
                        // Tombstones erase the row; delete-for-me tombstones must never resurrect
                        // through a realtime Update, so skipped rows are simply not re-inserted.
                        launch {
                            when {
                                msg.isDeletedForEveryone -> messageDao.deleteMessage(msg.id)
                                deletedStore.isMessageDeleted(msg.id) -> Unit
                                else -> messageDao.insertMessage(msg.toEntity())
                            }
                        }

                        // Tombstone updates bypass the dedupe window (a message emitted
                        // minutes earlier must still surface its deletion), and so do all
                        // UPDATE events: read-receipt flips typically land within seconds of
                        // the INSERT and must never be swallowed by the 30 s dedupe window.
                        if (action is PostgresAction.Update || msg.isDeletedForEveryone || shouldEmitMessage(msg.id)) trySend(WhisperChatEvent.MessageEvent(finalMsg))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WhisperRepo", "Postgres message realtime error: ${e.message}", e)
                }
            }
        }

        // Listen for Postgres Changes on message_reactions
        val postgresReactionChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "message_reactions"
        }
        val pReactionJob = launch {
            postgresReactionChanges.collect { action ->
                try {
                    val row = when (action) {
                        is PostgresAction.Insert -> action.decodeRecord<WhisperMessageReactionRow>()
                        is PostgresAction.Update -> action.decodeRecord<WhisperMessageReactionRow>()
                        is PostgresAction.Delete -> action.decodeOldRecord<WhisperMessageReactionRow>()
                        else -> null
                    }
                    if (row != null) {
                        trySend(WhisperChatEvent.ReactionEvent(row.messageId, row.userId, row.emoji))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WhisperRepo", "Postgres reaction realtime error: ${e.message}", e)
                }
            }
        }

        channel.subscribe()
        channelMutex.withLock {
            broadcastChannelCache[channelName] = channel
        }

        awaitClose {
            pMsgJob.cancel()
            pReactionJob.cancel()
            // H-1 FIX: appScope, not ProducerScope — the flow scope is already cancelled
            // when this callback runs, so a plain launch here would never execute.
            appScope.launch {
                removeCachedChannel(channelName, channel)
            }
        }
    }

    fun subscribeToIncomingMessages(userId: String): Flow<WhisperMessage> = callbackFlow {
        if (userId.isEmpty()) {
            close()
            return@callbackFlow
        }

        val channelName = "whisper-user-inbox-$userId"
        channelMutex.withLock {
            broadcastChannelCache[channelName]?.let { runCatching { realtime.removeChannel(it) } }
            broadcastChannelCache.remove(channelName)
        }

        val channel = supabase.channel(channelName)

        // Listen to database messages table changes. Realtime broadcasts are deliberately
        // NOT consumed: they cannot prove sender identity and are never used as a
        // message source or notification trigger.
        //
        // P0-2 FIX: scoped server-side to rows involving [userId] via two single-column
        // filters (Realtime allows one column filter per subscription). RLS constrains
        // visibility, so the pair exactly covers "every message this user participates in"
        // instead of the whole table.
        val asReceiverChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
            filter("receiver_id", FilterOperator.EQ, userId)
        }
        val asSenderChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
            filter("sender_id", FilterOperator.EQ, userId)
        }
        val dbJob = launch {
            merge(asReceiverChanges, asSenderChanges).collect { action ->
                try {
                    val msg = when (action) {
                        is PostgresAction.Insert -> action.decodeRecord<WhisperMessage>()
                        is PostgresAction.Update -> action.decodeRecord<WhisperMessage>()
                        else -> null
                    }
                    if (msg != null && (msg.receiverId == userId || msg.senderId == userId)) {
                        if (isUserBlockedByMe(msg.senderId)) return@collect
                        // Never surface or re-notify a message the user deleted for themselves.
                        if (deletedStore.isMessageDeleted(msg.id)) return@collect

                        val otherId = if (msg.senderId == userId) msg.receiverId else msg.senderId

                        // Decrypt with the trusted key only (TOFU); first contact fetches
                        // the fresh key once, otherwise the placeholder is shown.
                        val decrypted = decryptRealtimeMessage(msg, otherId)

                        if (shouldEmitMessage(msg.id)) trySend(msg.copy(content = decrypted))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WhisperRepo", "Realtime collect error: ${e.message}", e)
                }
            }
        }

        channel.subscribe()
        channelMutex.withLock {
            broadcastChannelCache[channelName] = channel
        }

        awaitClose {
            dbJob.cancel()
            // H-1 FIX: see subscribeToChat — cleanup must survive collection cancellation.
            appScope.launch {
                removeCachedChannel(channelName, channel)
            }
        }
    }


    // CLEAR CHAT
    suspend fun clearMessagesForRange(
        otherUserId: String,
        fromIso: String? = null,
        toIso: String? = null
    ): Result<List<WhisperMessage>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())

        // Paginated select to avoid silent truncation (was single limit 500 → 600-msg history left 100 resurrecting).
        // "clear history" must remove partner's messages locally too, not just my sent rows.
        val allToDelete = mutableListOf<WhisperMessage>()
        var page = 0
        while (true) {
            val batch = db.from("messages").select {
                filter {
                    or {
                        and { eq("sender_id", currentId); eq("receiver_id", otherUserId) }
                        and { eq("sender_id", otherUserId); eq("receiver_id", currentId) }
                    }
                    if (fromIso != null) gte("created_at", fromIso)
                    if (toIso != null) lte("created_at", toIso)
                }
                range((page * 500L), ((page + 1) * 500L - 1))
            }.decodeList<WhisperMessage>()
            if (batch.isEmpty()) break
            allToDelete.addAll(batch)
            if (batch.size < 500) break
            page++
            if (page > 100) break // safety cap 50k — covers hoarders, prevents infinite loop on stable feed
        }

        val toDeleteIds = allToDelete.map { it.id }.filter { it.isNotBlank() }
        if (toDeleteIds.isNotEmpty()) {
            // Await durability before erasing Room — otherwise reload could resurrect.
            deletedStore.markMessagesDeletedSuspend(toDeleteIds)
            // Mirror locally-capped tombstones remotely so eviction never resurrects after reinstall.
            runCatching { syncDeletedTombstonesRemote(toDeleteIds) }
            toDeleteIds.forEach { messageDao.deleteMessage(it) }
            // Server-side deletion limited by RLS to rows I own (my sent messages).
            // Delete exactly the my-sent subset via id list to keep local/server in sync.
            val mySentIds = allToDelete.filter { it.senderId == currentId }.map { it.id }.filter { it.isNotBlank() }
            if (mySentIds.isNotEmpty()) {
                // Batch delete by ids to avoid range mismatch; chunk 200 per request.
                for (chunk in mySentIds.chunked(200)) {
                    db.from("messages").delete {
                        filter { isIn("id", chunk) }
                    }
                }
            }
        }
        invalidateConversationsCache()
        allToDelete
    }

    // ── Server tombstones: mirror local deletes so eviction never resurrects ──
    @Serializable
    private data class TombstoneRow(@SerialName("message_id") val messageId: String)

    private suspend fun syncDeletedTombstonesRemote(messageIds: List<String>) {
        if (messageIds.isEmpty() || myId.isBlank()) return
        val rows = messageIds.filter { it.isNotBlank() }.map { mapOf("user_id" to myId, "message_id" to it) }
        // Upsert idempotently; ignore RLS/unique errors (best-effort, local is authoritative)
        for (chunk in rows.chunked(200)) {
            runCatching { db.from("whisper_deleted_tombstones").upsert(chunk) }
        }
    }

    private suspend fun removeRemoteTombstones(messageIds: List<String>) {
        if (messageIds.isEmpty() || myId.isBlank()) return
        for (chunk in messageIds.filter { it.isNotBlank() }.chunked(200)) {
            runCatching { db.from("whisper_deleted_tombstones").delete { filter { isIn("message_id", chunk) } } }
        }
    }

    suspend fun pullRemoteTombstones(): Result<Unit> = runCatching {
        if (myId.isBlank()) return@runCatching
        val rows = db.from("whisper_deleted_tombstones").select { filter { eq("user_id", myId) } }.decodeList<TombstoneRow>()
        if (rows.isNotEmpty()) deletedStore.markMessagesDeletedSuspend(rows.map { it.messageId })
    }

    suspend fun restoreMessages(messages: List<WhisperMessage>): Result<Unit> = runCatching {
        if (messages.isEmpty()) return@runCatching
        // P2 FIX: Ensure caller passes ciphertext (undo path does). Plaintext must never resurrect.
        messages.forEach { msg ->
            require(msg.contentIv != null || WhisperTombstone.isTombstone(msg.content) || msg.content == WhisperTombstone.LEGACY_ENCRYPTED) {
                "restoreMessages requires ciphertext: id=${msg.id} has plain content without IV/tombstone"
            }
        }
        // RLS only allows inserting rows I authored (sender_id = auth.uid()), so a clear-chat
        // undo can NEVER re-insert the partner's rows server-side. Order matters:
        //  1. Re-insert MY rows first — if this fails (offline etc.) nothing else changed,
        //     so the undo bar stays alive and the user can retry within the window.
        //  2. Remove my tombstones for ALL ids — always permitted by RLS (own rows), and
        //     required so eviction/reinstall cannot re-delete what we just restored.
        //  3. Restore locally: partner rows resurrect from the Room cache only.
        val mine = messages.filter { it.senderId == myId }
        if (mine.isNotEmpty()) {
            val inserts = mine.map { msg ->
                WhisperMessageInsert(
                    id = msg.id,
                    senderId = msg.senderId,
                    receiverId = msg.receiverId,
                    content = msg.content,
                    contentIv = msg.contentIv,
                    isRead = msg.isRead,
                    createdAt = msg.createdAt
                )
            }
            db.from("messages").insert(inserts)
        }
        removeRemoteTombstones(messages.map { it.id })
        deletedStore.unmarkMessagesDeleted(messages.map { it.id })
        // Restore ALL rows locally (mine + partner's) from the persisted undo buffer;
        // partner rows live only on this device since RLS forbids re-inserting them server-side.
        // M-12 FIX (reviewwhisper.md): restored partner rows are marked unread — the old
        // code resurrected stale read receipts that had already been reported server-side.
        messageDao.insertMessages(messages.map { msg ->
            if (msg.senderId == myId) msg.toEntity() else msg.copy(isRead = false).toEntity()
        })
        invalidateConversationsCache()
    }

    /**
     * Resolves partner profiles (and the blocker set) in ONE query pair instead of an
     * N+1 fan of per-user requests. Keeps the same in-memory side effects as getProfile
     * (profile cache + peer key cache) so decryption behavior is unchanged.
     */
    private suspend fun batchProfilesById(userIds: List<String>): Pair<Set<String>, Map<String, WhisperProfile>> {
        if (userIds.isEmpty()) return emptySet<String>() to emptyMap()
        val blockedPartnerIds = runCatching {
            db.from("whisper_blocks").select { filter { eq("blocker_id", myId) } }
                .decodeList<WhisperBlock>().map { it.blockedId }.toSet()
        }.getOrDefault(emptySet())
        val profilesById = runCatching {
            db.from("profiles").select { filter { isIn("id", userIds) } }
                .decodeList<WhisperProfile>().associateBy { it.id }
        }.getOrDefault(emptyMap())
        profilesById.values.forEach { profile ->
            cacheProfile(profile.id, profile)
            cachePeerKey(profile.id, profile.publicKey)
        }
        return blockedPartnerIds to profilesById
    }

    suspend fun getConversations(forceRefresh: Boolean = false): Result<List<WhisperConversation>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())

        val now = System.currentTimeMillis()
        if (!forceRefresh) conversationsCache?.let { cached ->
            if (now - cached.at < CONVERSATIONS_CACHE_TTL) return Result.success(cached.value)
        }
        @Serializable
        data class ConvRow(
            @SerialName("partner_id") val partnerId: String = "",
            @SerialName("last_content") val lastContent: String = "",
            @SerialName("last_content_iv") val lastContentIv: String? = null,
            @SerialName("last_created_at") val lastCreatedAt: String = "",
            @SerialName("unread_count") val unreadCount: Long = 0,
        )

        val rows = runCatching {
            db.rpc("get_conversations", buildJsonObject { put("p_user_id", currentId) })
                .decodeList<ConvRow>()
        }.getOrNull()

        if (rows != null) {
            // Batch the block + profile lookups into single queries instead of N+1 per row.
            val (blockedPartnerIds, profilesById) = batchProfilesById(rows.map { it.partnerId })
            val conversations = mutableListOf<WhisperConversation>()
            for (row in rows) {
                if (row.partnerId in blockedPartnerIds) continue
                val profile = profilesById[row.partnerId] ?: continue
                val decryptedContent = if (WhisperTombstone.isTombstone(row.lastContent)) {
                    WhisperTombstone.DISPLAY_TEXT
                } else if (row.lastContentIv != null && profile.publicKey != null) {
                    // TOFU-consistent with getMessages: prefer the ACCEPTED trusted key so a
                    // changed (possibly malicious) server key can never silently decrypt the
                    // preview; fall back to the fresh key only on true first contact.
                    // M-2 FIX: routed through the memoized decrypt — the double direction
                    // attempt used to cost two full ECDH derives per conversation row.
                    val previewKey = peerKeyFor(row.partnerId) ?: profile.publicKey
                    decryptMemoized(row.lastContent, row.lastContentIv, previewKey, row.partnerId, myId)
                        ?: decryptMemoized(row.lastContent, row.lastContentIv, previewKey, myId, row.partnerId)
                        ?: "🔒 Encrypted message"
                } else if (row.lastContentIv != null) {
                    "🔒 Encrypted message"
                } else WhisperTombstone.LEGACY_ENCRYPTED

                val fakeMsg = WhisperMessage(
                    id = "",
                    senderId = row.partnerId,
                    receiverId = myId,
                    content = decryptedContent,
                    isRead = row.unreadCount == 0L,
                    createdAt = row.lastCreatedAt,
                )
                conversations.add(WhisperConversation(profile, fakeMsg, row.unreadCount.toInt()))
            }
            conversations.also { result ->
                conversationsCache = ConversationsCacheEntry(result, System.currentTimeMillis())
            }
        } else {
            // Fallback when RPC is unavailable: paginate instead of truncating at 200, otherwise
            // users with >200 messages silently lose conversations.
            // P1-13 FIX: Cap raised from 2000 to 10000 (20 pages) to cover power users.
            val allMessages = mutableListOf<WhisperMessage>()
            var fbPage = 0
            while (allMessages.size < 10000) {
                val batch = db.from("messages")
                    .select {
                        filter {
                            or {
                                eq("sender_id", myId)
                                eq("receiver_id", myId)
                            }
                        }
                        order("created_at", Order.DESCENDING)
                        range((fbPage * 500L), ((fbPage + 1) * 500L - 1))
                    }
                    .decodeList<WhisperMessage>()
                if (batch.isEmpty()) break
                allMessages.addAll(batch)
                if (batch.size < 500) break
                fbPage++
                if (fbPage > 19) break // cap 10000, enough to rebuild convos for hoarders
            }

            val grouped = allMessages.groupBy { msg ->
                if (msg.senderId == myId) msg.receiverId else msg.senderId
            }
            // Batch the block + profile lookups into single queries instead of N+1 per row.
            val (blockedPartnerIds, profilesById) = batchProfilesById(grouped.keys.toList())
            val conversations = mutableListOf<WhisperConversation>()
            for ((partnerId, msgs) in grouped) {
                if (partnerId in blockedPartnerIds) continue
                val visibleMsgs = msgs.filter { !deletedStore.isMessageDeleted(it.id) }
                if (visibleMsgs.isEmpty()) continue
                val profile = profilesById[partnerId] ?: continue
                val lastMsg = visibleMsgs.first()
                // TOFU-consistent preview decryption (see RPC path above).
                val previewKey = peerKeyFor(partnerId) ?: profile.publicKey
                val decryptedContent = if (lastMsg.isDeletedForEveryone) {
                    WhisperTombstone.DISPLAY_TEXT
                } else if (lastMsg.contentIv != null && previewKey != null) {
                    crypto.decryptMessage(lastMsg.content, lastMsg.contentIv, previewKey, lastMsg.senderId, lastMsg.receiverId) ?: "🔒 Encrypted message"
                } else if (lastMsg.contentIv != null) {
                    "🔒 Encrypted message"
                } else WhisperTombstone.LEGACY_ENCRYPTED

                val unread = visibleMsgs.count { it.receiverId == myId && !it.isRead }
                conversations.add(WhisperConversation(profile, lastMsg.copy(content = decryptedContent), unread))
            }
            conversations.sortedByDescending { it.lastMessage.createdAt }
        }.also { result ->
            conversationsCache = ConversationsCacheEntry(result, System.currentTimeMillis())
        }
    }

    // FRIENDS
    suspend fun getFriendships(): Result<List<WhisperFriendship>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())
        db.from("friends").select {
            filter { or { eq("user_a", currentId); eq("user_b", currentId) } }
        }.decodeList()
    }

    suspend fun getFriends(): Result<List<WhisperProfile>> = runCatching {
        val friendships = getFriendships().getOrThrow().filter { it.status == "accepted" }
        // Batch profile resolution into one query instead of N+1 per friendship.
        val partnerIds = friendships.map { it.otherUserId(myId) }.distinct()
        if (partnerIds.isEmpty()) return@runCatching emptyList()
        val (_, profilesById) = batchProfilesById(partnerIds)
        friendships.mapNotNull { profilesById[it.otherUserId(myId)] }
    }

    suspend fun getPendingIncoming(): Result<List<WhisperFriendship>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())
        db.from("friends").select { filter { eq("user_b", currentId); eq("status", "pending") } }.decodeList()
    }

    suspend fun getPendingIncomingWithProfiles(): Result<List<WhisperFriendRequestItem>> = runCatching {
        val pending = getPendingIncoming().getOrThrow()
        // Batch profile resolution into one query instead of N+1 per request.
        val senderIds = pending.map { it.userA }.distinct()
        if (senderIds.isEmpty()) return@runCatching emptyList()
        val (_, profilesById) = batchProfilesById(senderIds)
        pending.map { f ->
            WhisperFriendRequestItem(friendship = f, senderProfile = profilesById[f.userA])
        }
    }

    suspend fun getPendingOutgoing(): Result<List<WhisperFriendship>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())
        db.from("friends").select { filter { eq("user_a", currentId); eq("status", "pending") } }.decodeList()
    }

    suspend fun sendFriendRequest(targetUserId: String): Result<Unit> = runCatching {
        if (targetUserId == myId) return@runCatching
        if (isUserBlockedByMe(targetUserId)) error("You have blocked this user. Unblock to send a friend request.")
        if (isUserBlockedByOther(targetUserId)) error("You can't send a friend request to a user who blocked you.")
        val existingPair = getFriendshipStatus(targetUserId).getOrNull()
        val (status, record) = existingPair ?: Pair(FriendStatus.NONE, null)

        if (status == FriendStatus.ACCEPTED) return@runCatching
        if (record != null) {
            if (record.userA == targetUserId && record.userB == myId) {
                acceptFriendRequest(record.id).getOrThrow()
                return@runCatching
            }
            if (record.userA == myId && record.userB == targetUserId) return@runCatching
        }
        val inserted = db.from("friends")
            .insert(WhisperFriendshipInsert(userA = myId, userB = targetUserId)) { select() }
            .decodeSingleOrNull<WhisperFriendship>()

        // Instant Realtime Peer Broadcast to target user
        runCatching {
            val myProfile = getMyProfile().getOrNull()
            val friendsChannel = getOrJoinBroadcastChannel("whisper-friends-all-$targetUserId")
            friendsChannel.broadcast(
                event = "friend_update",
                payload = BroadcastPayload.Json(
                    buildJsonObject {
                        put("id", inserted?.id ?: "")
                        put("user_a", myId)
                        put("user_b", targetUserId)
                        put("status", "pending")
                    }
                )
            )

            val notifChannel = getOrJoinBroadcastChannel("whisper-user-inbox-$targetUserId")
            notifChannel.broadcast(
                event = "incoming_notification",
                payload = BroadcastPayload.Json(
                    buildJsonObject {
                        put("id", inserted?.id ?: "")
                        put("sender_id", myId)
                        put("sender_name", myProfile?.effectiveName ?: "Someone")
                        put("type", "friend_request")
                        put("content", "sent you a friend request")
                        put("created_at", java.time.Instant.now().toString())
                    }
                )
            )
        }
    }

    suspend fun acceptFriendRequest(friendshipId: String): Result<Unit> = runCatching {
        val existing = db.from("friends").select { filter { eq("id", friendshipId) } }
            .decodeSingleOrNull<WhisperFriendship>() ?: error("This friend request no longer exists.")
        // Only the recipient of a pending request may accept it.
        if (existing.userB != myId) error("You can only accept friend requests sent to you.")
        if (existing.status != "pending") return@runCatching

        db.from("friends").update({ set("status", "accepted") }) { filter { eq("id", friendshipId) } }

        // L-1 FIX: removed the dead `if (existing != null)` wrapper — existing is
        // guaranteed non-null by the decodeSingleOrNull ?: error() above.
        val uA = existing.userA
        val uB = existing.userB
        val otherId = if (uA == myId) uB else uA

        runCatching {
            val friendsChannel = getOrJoinBroadcastChannel("whisper-friends-all-$otherId")
            friendsChannel.broadcast(
                event = "friend_update",
                payload = BroadcastPayload.Json(
                    buildJsonObject {
                        put("id", friendshipId)
                        put("user_a", uA)
                        put("user_b", uB)
                        put("status", "accepted")
                    }
                )
            )
        }

        runCatching {
            db.from("friends").delete {
                filter {
                    neq("id", friendshipId)
                    or {
                        and { eq("user_a", uA); eq("user_b", uB) }
                        and { eq("user_a", uB); eq("user_b", uA) }
                    }
                }
            }
        }
    }

    suspend fun deleteFriendship(friendshipId: String): Result<Unit> = runCatching {
        val existing = db.from("friends").select { filter { eq("id", friendshipId) } }
            .decodeSingleOrNull<WhisperFriendship>() ?: error("This friendship no longer exists.")
        if (existing.userA != myId && existing.userB != myId) {
            error("This friendship is not yours to remove.")
        }
        db.from("friends").delete { filter { eq("id", friendshipId) } }
        // L-1 FIX: dead null-check removed (same as acceptFriendRequest).
        run {
            val otherId = if (existing.userA == myId) existing.userB else existing.userA
            runCatching {
                val friendsChannel = getOrJoinBroadcastChannel("whisper-friends-all-$otherId")
                friendsChannel.broadcast(
                    event = "friend_update",
                    payload = BroadcastPayload.Json(
                        buildJsonObject {
                            put("id", friendshipId)
                            put("user_a", existing.userA)
                            put("user_b", existing.userB)
                            put("status", "deleted")
                        }
                    )
                )
            }
        }
    }

    suspend fun getFriendshipStatus(otherUserId: String): Result<Pair<FriendStatus, WhisperFriendship?>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(Pair(FriendStatus.NONE, null))
        val records = db.from("friends").select {
            filter {
                or {
                    and { eq("user_a", currentId); eq("user_b", otherUserId) }
                    and { eq("user_a", otherUserId); eq("user_b", currentId) }
                }
            }
        }.decodeList<WhisperFriendship>()

        val accepted = records.firstOrNull { it.status == "accepted" }
        if (accepted != null) return@runCatching Pair(FriendStatus.ACCEPTED, accepted)

        val pending = records.firstOrNull { it.status == "pending" }
        if (pending != null) return@runCatching Pair(FriendStatus.PENDING, pending)

        Pair(records.firstOrNull()?.friendStatus() ?: FriendStatus.NONE, records.firstOrNull())
    }

    fun subscribeToFriendUpdates(): Flow<WhisperFriendship> = callbackFlow {
        val currentId = myId
        if (currentId.isBlank()) {
            close()
            return@callbackFlow
        }
        val channelName = "whisper-friends-all-$currentId"
        channelMutex.withLock {
            broadcastChannelCache[channelName]?.let {
                runCatching { realtime.removeChannel(it) }
                broadcastChannelCache.remove(channelName)
            }
        }
        val channel = supabase.channel(channelName)

        // H-8 FIX (reviewwhisper.md): realtime BROADCASTS on this channel are no longer
        // consumed. Broadcasts cannot prove sender identity — any user who knows the
        // channel name could fabricate friendship state (fake requests/acceptances) and
        // spoof the UI until a Postgres event corrected it. Postgres Changes below ARE
        // authenticated (RLS-filtered, publication-wired with REPLICA IDENTITY FULL)
        // and are the single authoritative source for friendship updates.

        // Postgres Change flow fallback
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "friends" }
        val pJob = launch {
            changes.collect { action ->
                try {
                    val record = when (action) {
                        is PostgresAction.Insert -> action.decodeRecord<WhisperFriendship>()
                        is PostgresAction.Update -> action.decodeRecord<WhisperFriendship>()
                        else -> null
                    }
                    if (record != null && (record.userA == currentId || record.userB == currentId)) {
                        trySend(record)
                    }
                } catch (_: Exception) { }
            }
        }
        channel.subscribe()
        channelMutex.withLock {
            broadcastChannelCache[channelName] = channel
        }
        awaitClose {
            pJob.cancel()
            // H-1 FIX: see subscribeToChat — cleanup must survive collection cancellation.
            appScope.launch {
                removeCachedChannel(channelName, channel)
            }
        }
    }


    private val blockedByMeCache = Collections.synchronizedSet(mutableSetOf<String>())

    // H-6 FIX (reviewwhisper.md): full blocker-set cache with TTL. Previously every
    // incoming realtime message event for a NON-blocked sender triggered a
    // whisper_blocks REST round-trip (negatives were never cached).
    private var blockedCacheLoadedAtMs = 0L
    private val BLOCK_CACHE_TTL_MS = 5 * 60 * 1000L
    // H-6: negative-result TTL for isUserBlockedByOther (the other party's blocks can
    // only be probed per-pair under RLS, so a short-TTL memo is the best available).
    private val blockedByOtherCheckedAtMs = ConcurrentHashMap<String, Long>()
    private val BLOCKED_BY_OTHER_TTL_MS = 30_000L

    /** Reloads the complete blocker set at most once per [BLOCK_CACHE_TTL_MS]. */
    private suspend fun ensureBlockCachesFresh(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - blockedCacheLoadedAtMs < BLOCK_CACHE_TTL_MS) return
        runCatching {
            db.from("whisper_blocks").select { filter { eq("blocker_id", myId) } }
                .decodeList<WhisperBlock>().map { it.blockedId }.toSet()
        }.onSuccess { currentlyBlocked ->
            blockedIds.value = currentlyBlocked
            blockedByMeCache.clear()
            blockedByMeCache.addAll(currentlyBlocked)
            blockedCacheLoadedAtMs = System.currentTimeMillis()
        }
    }

    private suspend fun isUserBlockedByMe(userId: String): Boolean {
        ensureBlockCachesFresh()
        return blockedIds.value.contains(userId) || blockedByMeCache.contains(userId)
    }

    private suspend fun isUserBlockedByOther(userId: String): Boolean {
        // H-6: short-TTL memo for the negative result — this check runs per send and
        // per incoming event, and a "not blocked" answer stays valid briefly.
        blockedByOtherCheckedAtMs[userId]?.let { checkedAt ->
            if (System.currentTimeMillis() - checkedAt < BLOCKED_BY_OTHER_TTL_MS) return false
        }
        val isBlocked = runCatching {
            val blocks = db.from("whisper_blocks").select {
                filter {
                    eq("blocker_id", userId)
                    eq("blocked_id", myId)
                }
            }.decodeList<WhisperBlock>()
            blocks.isNotEmpty()
        }.getOrDefault(false)
        if (isBlocked) {
            blockedByOtherCheckedAtMs.remove(userId)
        } else {
            blockedByOtherCheckedAtMs[userId] = System.currentTimeMillis()
        }
        return isBlocked
    }

    /** Re-reads the block table for this account so in-memory caches never drift from server truth. */
    private suspend fun refreshBlockCachesFor(targetUserId: String) {
        // H-6: force = bypass TTL, this is called right after an optimistic mutation.
        runCatching {
            db.from("whisper_blocks").select { filter { eq("blocker_id", myId) } }
                .decodeList<WhisperBlock>().map { it.blockedId }.toSet()
        }.onSuccess { currentlyBlocked ->
            blockedIds.value = currentlyBlocked
            blockedByMeCache.clear()
            if (targetUserId in currentlyBlocked) blockedByMeCache.add(targetUserId) else blockedByMeCache.remove(targetUserId)
            blockedCacheLoadedAtMs = System.currentTimeMillis()
            blockedByOtherCheckedAtMs.remove(targetUserId)
        }.onFailure {
            // Keep existing cache on network failure; optimistic state already applied by caller.
        }
    }

    suspend fun getBlockStatus(otherUserId: String): Pair<Boolean, Boolean> {
        val byMe = isUserBlockedByMe(otherUserId)
        val byOther = isUserBlockedByOther(otherUserId)
        return Pair(byMe, byOther)
    }

    suspend fun blockUser(targetUserId: String): Result<Unit> = runCatching {
        require(targetUserId.isNotBlank() && targetUserId != myId) { "Choose another user to block." }
        db.from("whisper_blocks").insert(WhisperBlockInsert(blockerId = myId, blockedId = targetUserId))
        blockedIds.update { it + targetUserId }
        blockedByMeCache.add(targetUserId)
        refreshBlockCachesFor(targetUserId)
    }

    suspend fun unblockUser(targetUserId: String): Result<Unit> = runCatching {
        db.from("whisper_blocks").delete { filter { eq("blocker_id", myId); eq("blocked_id", targetUserId) } }
        blockedIds.update { it - targetUserId }
        blockedByMeCache.remove(targetUserId)
        refreshBlockCachesFor(targetUserId)
    }

    suspend fun isBlockedByMe(otherUserId: String): Boolean = isUserBlockedByMe(otherUserId)
    suspend fun isBlockedByOther(otherUserId: String): Boolean = isUserBlockedByOther(otherUserId)

    suspend fun getFriendsOfFriends(): Result<List<WhisperProfile>> = runCatching {
        val myFriendIds = getFriendships().getOrThrow()
            .filter { it.status == "accepted" }
            .map { it.otherUserId(myId) }
            .toSet()

        if (myFriendIds.isEmpty()) return Result.success(emptyList())

        // Fetch accepted friendships involving our friends
        val candidateFriendships = db.from("friends").select {
            filter { 
                eq("status", "accepted") 
                or {
                    isIn("user_a", myFriendIds.toList())
                    isIn("user_b", myFriendIds.toList())
                }
            }
            limit(1_000)
        }.decodeList<WhisperFriendship>()

        val fofIds = mutableSetOf<String>()
        for (f in candidateFriendships) {
            val a = f.userA
            val b = f.userB
            if (a in myFriendIds && b !in myFriendIds && b != myId) fofIds.add(b)
            if (b in myFriendIds && a !in myFriendIds && a != myId) fofIds.add(a)
        }

        if (fofIds.isEmpty()) return Result.success(emptyList())

        // Resolve profiles in ONE query instead of N+1 per-id getProfile calls.
        val (blockedIds, profilesById) = batchProfilesById(fofIds.take(20))
        val recommended = profilesById.values
            .filter { !it.isPrivate && !it.isHiddenFromDiscover && it.id !in blockedIds }
            .take(15)
        recommended
    }

    /**
     * Fetches paginated discoverable profiles via the [whisper_discover_profiles] RPC.
     *
     * The RPC enforces:
     *   • 60 pages/hour per calling user (returns P0002 "rate_limited" on breach).
     *   • Server-side filtering of private / hide_from_discover / own profile.
     *   • Server-side exclusion of profiles that have blocked the caller.
     *
     * Friends are filtered out client-side to avoid a cross-join on the server.
     */
    suspend fun getDiscoverProfiles(page: Int, pageSize: Int = 20): Result<List<WhisperProfile>> = runCatching {
        val myFriendIds = getFriendships().getOrThrow()
            .filter { it.status == "accepted" }
            .map { it.otherUserId(myId) }
            .toSet()

        db.rpc(
            "whisper_discover_profiles",
            buildJsonObject {
                put("p_page", page)
                put("p_page_size", pageSize.coerceIn(1, 30))
            }
        )
            .decodeList<WhisperProfile>()
            .filter { it.id !in myFriendIds }
    }

    suspend fun sendTypingStatus(targetUserId: String, isTyping: Boolean) {
        runCatching {
            val channelKey = conversationKey(myId, targetUserId)
            val channel = getOrJoinBroadcastChannel("typing_$channelKey")
            channel.broadcast(
                event = "typing",
                payload = BroadcastPayload.Json(buildJsonObject { put("sender_id", myId); put("is_typing", isTyping) })
            )
        }
    }

    fun subscribeToTypingStatus(otherUserId: String): Flow<Boolean> = callbackFlow {
        val channelKey = conversationKey(myId, otherUserId)
        val name = "typing_$channelKey"
        val channel = getOrJoinBroadcastChannel(name)
        val broadcasts = channel.broadcastFlow<JsonObject>("typing")
        val job = launch { broadcasts.collect { json ->
            // Typing is a cosmetic, boolean-only presence hint. It carries no message content,
            // so accepting it from the broadcast channel cannot inject user-visible state.
            try {
                val senderId = json["sender_id"]?.jsonPrimitive?.content
                val isTyping = json["is_typing"]?.jsonPrimitive?.booleanOrNull ?: false
                if (senderId == otherUserId && !isUserBlockedByMe(otherUserId)) {
                    trySend(isTyping)
                }
            } catch (_: Exception) { }
        } }
        awaitClose { job.cancel(); appScope.launch { removeCachedChannel(name, channel) } } // H-1 FIX
    }

    suspend fun sendPresence(targetUserId: String, isOnline: Boolean) {
        runCatching {
            val channelKey = conversationKey(myId, targetUserId)
            val channel = getOrJoinBroadcastChannel("presence_$channelKey")
            channel.broadcast(
                event = "presence",
                payload = BroadcastPayload.Json(buildJsonObject { put("sender_id", myId); put("is_online", isOnline); put("timestamp", java.time.Instant.now().toString()) })
            )
        }
    }

    fun subscribeToPresence(otherUserId: String): Flow<Pair<Boolean, String?>> = callbackFlow {
        val channelKey = conversationKey(myId, otherUserId)
        val name = "presence_$channelKey"
        val channel = getOrJoinBroadcastChannel(name)
        
        // 1. Instant Realtime Broadcasts (app in foreground status) — boolean-only presence
        // hints with no message content; the DB window below remains the authoritative source.
        val broadcasts = channel.broadcastFlow<JsonObject>("presence")
        val bJob = launch { 
            broadcasts.collect { json ->
                try {
                    val senderId = json["sender_id"]?.jsonPrimitive?.content
                    val isOnline = json["is_online"]?.jsonPrimitive?.booleanOrNull ?: false
                    val ts = json["timestamp"]?.jsonPrimitive?.content
                    if (senderId == otherUserId && !isUserBlockedByMe(otherUserId)) {
                        trySend(Pair(isOnline, ts))
                    }
                } catch (_: Exception) { }
            } 
        }

        // 2. Database Changes (last_seen_at updates)
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "profiles"
        }
        val dbJob = launch {
            changes.collect { action ->
                try {
                    val profile = when (action) {
                        is PostgresAction.Insert -> action.decodeRecord<WhisperProfile>()
                        is PostgresAction.Update -> action.decodeRecord<WhisperProfile>()
                        else -> null
                    }
                    if (profile != null && profile.id == otherUserId) {
                        val lastSeen = profile.lastSeenAt
                        if (lastSeen != null) {
                            val lastSeenTs = java.time.OffsetDateTime.parse(lastSeen).toInstant()
                            val isOnline = java.time.Instant.now().minusSeconds(120).isBefore(lastSeenTs)
                            trySend(Pair(isOnline, lastSeen))
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        awaitClose {
            bJob.cancel()
            dbJob.cancel()
            appScope.launch { removeCachedChannel(name, channel) } // H-1 FIX
        }
    }

    // ─────────────────────────────────────────────────────────────
    // KEY TRUST & VERIFICATION
    // ─────────────────────────────────────────────────────────────

    /**
     * P0-1 FIX (reviewwhisper.md): single source of truth for classifying a partner key
     * change. The old code had three contradictory signals (30-day rotation interval,
     * a 6-day "expected" heuristic, and copy claiming weekly rotation), which made
     * nearly every fleet-wide auto-rotation surface as a scary CHANGED warning AND
     * hard-block sending until manual review.
     *
     * Classification now:
     *  - known == current                     → MATCH
     *  - server row updated < FRESH_WINDOW    → ROTATED_AUTO (just rotated — calm info)
     *  - known key age ≥ interval − 24h       → ROTATED_AUTO (aged-out scheduled rotation)
     *  - anything else                        → CHANGED (genuine unexpected change → warn)
     */
    private suspend fun classifyKeyChange(
        otherUserId: String,
        profile: WhisperProfile,
    ): KeyTrustStatus {
        val current = profile.publicKey.orEmpty()
        val known = keyTrustStore.knownKey(otherUserId)
        if (known == null || known == current || current.isBlank()) return KeyTrustStatus.MATCH

        val serverUpdatedAgeMs = runCatching {
            java.time.OffsetDateTime.parse(profile.updatedAt).toInstant().toEpochMilli()
                .let { System.currentTimeMillis() - it }
        }.getOrNull() ?: Long.MAX_VALUE

        if (serverUpdatedAgeMs in 0..WhisperKeyRotationStore.FRESH_ROTATION_WINDOW_MS) {
            return KeyTrustStatus.ROTATED_AUTO
        }

        val knownTs = keyTrustStore.knownKeyTimestamp(otherUserId)
        val knownAge = if (knownTs == 0L) Long.MAX_VALUE else System.currentTimeMillis() - knownTs
        val expectedWindow = WhisperKeyRotationStore.ROTATE_INTERVAL_MS - 24L * 60 * 60 * 1000
        if (knownAge >= expectedWindow) {
            return KeyTrustStatus.ROTATED_AUTO
        }
        return KeyTrustStatus.CHANGED
    }

    /**
     * True when a partner key mismatch is a routine fresh rotation and messaging may
     * continue without a manual safety-number review. Used by [sendMessage] so monthly
     * fleet rotations no longer brick conversations (P0-1).
     */
    private fun isFreshServerRotation(profile: WhisperProfile?): Boolean {
        val updatedAt = profile?.updatedAt ?: return false
        val ageMs = runCatching {
            java.time.OffsetDateTime.parse(updatedAt).toInstant().toEpochMilli()
                .let { System.currentTimeMillis() - it }
        }.getOrNull() ?: return false
        return ageMs in 0..WhisperKeyRotationStore.FRESH_ROTATION_WINDOW_MS
    }

    /**
     * Builds a [KeyTrustInfo] for a conversation partner. On first encounter the key is
     * silently remembered as known (TOFU — M-1: this read-path pinning is deliberate:
     * the first chat/profile view IS first contact, and the pin must be durable so a
     * later server-side key swap can never silently decrypt old or new material).
     * Only a *change* of a previously known key ever surfaces via [classifyKeyChange].
     */
    suspend fun getKeyTrustInfo(otherUserId: String): KeyTrustInfo {
        val myFingerprint = crypto.getPublicKeyBase64()?.let { crypto.fingerprint(it) }
        // Force a fresh read so a changed key is never hidden behind the profile cache.
        val profile = getProfile(otherUserId, forceRefresh = true).getOrNull() ?: return KeyTrustInfo(myFingerprint = myFingerprint)
        val currentKey = profile.publicKey
        if (currentKey.isNullOrBlank()) return KeyTrustInfo(myFingerprint = myFingerprint)

        val known = keyTrustStore.knownKey(otherUserId)
        if (known == null) {
            // TOFU first contact (see KDoc): record and report MATCH without scary UI.
            keyTrustStore.rememberKey(otherUserId, currentKey)
            return KeyTrustInfo(status = KeyTrustStatus.MATCH, partnerFingerprint = crypto.fingerprint(currentKey), myFingerprint = myFingerprint, isVerified = false)
        }

        val status = classifyKeyChange(otherUserId, profile)

        // P0-1: copy aligned to the REAL 30-day interval (was "every week").
        val rotateMsg = when (status) {
            KeyTrustStatus.ROTATED_AUTO -> UiText.StringResource(
                R.string.st_Whisper_KeyRotate_AutoMonthly,
                WhisperKeyRotationStore.ROTATE_INTERVAL_MS / (24L * 60 * 60 * 1000),
            )
            KeyTrustStatus.ROTATED_MANUAL -> UiText.StringResource(
                R.string.st_Whisper_KeyRotate_Manual,
                profile.effectiveName,
            )
            else -> null
        }
        return KeyTrustInfo(
            status = status,
            partnerFingerprint = crypto.fingerprint(currentKey),
            myFingerprint = myFingerprint,
            isVerified = status == KeyTrustStatus.MATCH && keyTrustStore.verifiedKey(otherUserId) == currentKey,
            rotateMessage = rotateMsg,
            isExpectedRotation = status == KeyTrustStatus.ROTATED_AUTO,
        )
    }

    /** Mark the partner's current key as verified (fingerprint compared in person). */
    suspend fun verifyUserKey(otherUserId: String): Boolean {
        val profile = getProfile(otherUserId, forceRefresh = true).getOrNull() ?: return false
        val currentKey = profile.publicKey ?: return false
        keyTrustStore.markVerified(otherUserId, currentKey)
        cachePeerKey(otherUserId, currentKey)
        return true
    }

    /** Accept the partner's new key without verifying it in person. */
    suspend fun acceptNewKey(otherUserId: String): Boolean {
        val profile = getProfile(otherUserId, forceRefresh = true).getOrNull() ?: return false
        val currentKey = profile.publicKey ?: return false
        keyTrustStore.rememberKey(otherUserId, currentKey)
        cachePeerKey(otherUserId, currentKey)
        return true
    }

    /**
     * H-2 FIX (reviewwhisper.md): tears down every cached realtime channel (leaves the
     * socket-level subscriptions). Previously sign-out/account-deletion left channels
     * named with the old user's IDs subscribed until process death.
     */
    suspend fun removeAllCachedChannels() {
        val toRemove = channelMutex.withLock { broadcastChannelCache.values.toList() }
        broadcastChannelCache.clear()
        toRemove.forEach { runCatching { realtime.removeChannel(it) } }
    }

    /**
     * Drops every in-memory cache scoped to the signed-in account so a sign-out can
     * never bleed one user's conversations, keys, blocks or dedupe state into the next.
     * Does NOT touch persisted stores (Room, outbox, key trust) — those are account data.
     */
    fun clearSessionScopedCaches() {
        conversationsCache = null
        profileCache.clear(); profileCacheTs.clear()
        peerKeys.value = emptyMap()
        blockedIds.value = emptySet()
        blockedByMeCache.clear()
        blockedCacheLoadedAtMs = 0L
        blockedByOtherCheckedAtMs.clear()
        recentlyEmittedMessageIds.clear()
        receiveKeyNotified.clear()
        synchronized(decryptMemo) { decryptMemo.clear() }
        // H-2: channels are torn down asynchronously on the app scope — sign-out must
        // not block on network I/O, but the stale channels must not survive either.
        appScope.launch { removeAllCachedChannels() }
    }

    /**
     * Wipes every byte of whisper data this device holds for the signed-in account:
     * Room cache, tombstones, outbox, key trust records, hidden chats, mutes, caches,
     * realtime channels and the E2EE key pair. Called after a successful server-side
     * account deletion.
     */
    suspend fun clearAllLocalData() {
        removeAllCachedChannels()
        messageDao.clearAll()
        deletedStore.clearAll()
        outgoingQueue.clearAll()
        keyTrustStore.clearAll()
        hiddenChatsStore.clearAll()
        mutePrefs.clearAll()
        profileCache.clear(); profileCacheTs.clear()
        peerKeys.value = emptyMap()
        blockedIds.value = emptySet()
        blockedByMeCache.clear()
        blockedCacheLoadedAtMs = 0L
        blockedByOtherCheckedAtMs.clear()
        conversationsCache = null
        synchronized(decryptMemo) { decryptMemo.clear() }
        crypto.resetKeyPair()
    }
}
