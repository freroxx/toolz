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
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.awaitClose
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

import javax.inject.Inject
import javax.inject.Singleton
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
) {
    private companion object {
        const val MAX_MESSAGE_CHARS = 8_192
        const val EVENT_DEDUPE_TTL_MS = 60_000L
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

    // In-memory partner public keys for offline decryption of cached ciphertext.
    // Only ever populated from authenticated profile reads; the persisted fallback
    // (keyTrustStore) holds the key the user last accepted, so a changed key can
    // never silently decrypt old or new material.
    private val peerKeys = MutableStateFlow<Map<String, String>>(emptyMap())

    private fun cachePeerKey(userId: String, publicKey: String?) {
        if (!publicKey.isNullOrBlank()) {
            peerKeys.update { it + (userId to publicKey) }
        }
    }

    // Users this account has blocked, kept in memory for flow-level filtering of cached history.
    private val blockedIds = MutableStateFlow<Set<String>>(emptySet())

    private fun peerKeyFor(userId: String): String? =
        peerKeys.value[userId] ?: keyTrustStore.knownKey(userId)

    /** Decrypts a cached ciphertext message for display, falling back to a neutral marker. */
    private fun WhisperMessage.decryptContent(peerKey: String?): WhisperMessage {
        if (isDeletedForEveryone || contentIv == null) return this
        val key = peerKey ?: return copy(content = "[Encrypted message]")
        return copy(content = crypto.decryptMessage(content, contentIv, key) ?: "[Encrypted message]")
    }
    // Cache conversations list to avoid full reload on every new message in the chats hub
    @Volatile private var conversationsCache: List<WhisperConversation>? = null
    @Volatile private var conversationsCacheTime: Long = 0L
    private val CONVERSATIONS_CACHE_TTL = 30_000L // 30 seconds

    // Persistent broadcast channels keyed by channel name — shared across send/react/delete
    // so we don't subscribe to a brand-new channel object for each outgoing event.
    private val broadcastChannelCache = mutableMapOf<String, io.github.jan.supabase.realtime.RealtimeChannel>()
    private val channelMutex = Mutex()
    private val recentlyEmittedMessageIds = ConcurrentHashMap<String, Long>()

    private suspend fun getOrJoinBroadcastChannel(name: String): io.github.jan.supabase.realtime.RealtimeChannel {
        return channelMutex.withLock {
            broadcastChannelCache[name]?.let {
                try {
                    val status = it.status.value
                    if (status == io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED) {
                        return@withLock it
                    }
                } catch (_: Exception) {}
                runCatching { realtime.removeChannel(it) }
            }
            val channel = supabase.channel(name)
            channel.subscribe()
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
            recentlyEmittedMessageIds.entries.removeIf { (_, timestamp) -> now - timestamp > EVENT_DEDUPE_TTL_MS }
        }
        if (previous == null) return true
        if (now - previous <= EVENT_DEDUPE_TTL_MS) return false
        recentlyEmittedMessageIds[id] = now
        return true
    }

    fun invalidateConversationsCache() {
        conversationsCache = null
    }

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
        
        if (!forceRefresh && profileCache.containsKey(currentId)) {
            val cached = profileCache[currentId]
            if (cached?.publicKey != null) return Result.success(cached)
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
            // If local key exists but differs from server, update server (happens on reinstall/clear data)
            val needsKey = pubKey != null && existing.publicKey != pubKey

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
        profileCache[currentId] = profile
        profile
    }

    suspend fun getProfile(userId: String, forceRefresh: Boolean = false): Result<WhisperProfile> = runCatching {
        if (!forceRefresh && profileCache.containsKey(userId)) {
            val cached = profileCache[userId]
            if (cached != null && (!cached.publicKey.isNullOrBlank() || userId == myId)) {
                cachePeerKey(userId, cached.publicKey)
                return Result.success(cached)
            }
        }
        val p = db.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingle<WhisperProfile>()
        profileCache[userId] = p
        cachePeerKey(userId, p.publicKey)
        p
    }

    suspend fun searchProfiles(query: String): Result<List<WhisperProfile>> = runCatching {
        val q = query.trim()
        db.from("profiles")
            .select {
                filter {
                    or {
                        ilike("username", "%$q%")
                        ilike("display_name", "%$q%")
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
            update.displayName?.let { put("display_name", it) }
            update.bio?.let { put("bio", it) }
            update.avatarUrl?.let { put("avatar_url", it) }
            update.isPrivate?.let { put("is_private", it) }
            update.isHiddenFromDiscover?.let { put("hide_from_discover", it) }
            update.lastSeenAt?.let { put("last_seen_at", it) }
            when {
                update.publicKey != null -> put("public_key", update.publicKey!!)
                pubKey != null -> {
                    val serverKey = runCatching {
                        db.from("profiles").select { filter { eq("id", currentId) } }
                            .decodeSingleOrNull<WhisperProfile>()?.publicKey
                    }.getOrNull()
                    if (serverKey.isNullOrBlank()) put("public_key", pubKey)
                }
            }
        }
        db.from("profiles").update(body) { filter { eq("id", currentId) } }
        profileCache.remove(currentId)
    }

    suspend fun updateLastSeen(): Result<Unit> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return@runCatching
        val now = java.time.OffsetDateTime.now().toString()
        db.from("profiles").update(WhisperProfileUpdate(lastSeenAt = now)) {
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
        val publicUrl = store.from("whisper-avatars").publicUrl(path)
        val urlWithCacheBuster = "$publicUrl?t=${System.currentTimeMillis()}"
        updateProfile(WhisperProfileUpdate(avatarUrl = urlWithCacheBuster))
        profileCache.remove(myId)
        urlWithCacheBuster
    }

    suspend fun deleteAvatar(): Result<Unit> = runCatching {
        // Remove the underlying blob before clearing the reference so failed deletions
        // never leave an orphaned file behind.
        runCatching {
            val current = db.from("profiles").select { filter { eq("id", myId) } }
                .decodeSingleOrNull<WhisperProfile>()
            current?.avatarUrl?.substringAfter("whisper-avatars/", "")?.substringBefore("?")?.let { path ->
                if (path.isNotBlank()) store.from("whisper-avatars").delete(path)
            }
        }
        db.from("profiles").update(mapOf("avatar_url" to null as String?)) {
            filter { eq("id", myId) }
        }
        profileCache.remove(myId)
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
            val peerKey = inMemoryKey ?: keyTrustStore.knownKey(otherUserId)
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
        val partnerPubKey = partnerProfile?.publicKey

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

        // Decrypt messages first, filtering out locally deleted messages
        val visibleRaw = rawMessages
            .filter { msg -> (!isBlocked || msg.senderId == currentId) && !deletedStore.isMessageDeleted(msg.id) }
        val decryptedMessages = visibleRaw
            .map { msg ->
                if (msg.isDeletedForEveryone) {
                    msg
                } else if (msg.contentIv != null && partnerPubKey != null) {
                    val decrypted = crypto.decryptMessage(msg.content, msg.contentIv, partnerPubKey)
                        ?: "[Encrypted message]"
                    msg.copy(content = decrypted)
                } else msg
            }

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
            error("Safety number changed for this contact. Review and accept the new key before sending.")
        }
        val encryptedPair = receiverPubKey?.let { key -> crypto.encryptMessage(content, key) }
            ?: error("Secure delivery is unavailable because this user has no valid encryption key.")
        val insert = run {
            WhisperMessageInsert(
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
            val clientId = "queued_${java.util.UUID.randomUUID()}"
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
            
            val fakeMsg = WhisperMessage(
                id = clientId,
                senderId = currentId,
                receiverId = receiverId,
                content = content,
                replyToId = replyToId,
                isPending = true,
                createdAt = queued.createdAt
            )
            // Cache pending message
            messageDao.insertMessage(fakeMsg.toEntity())
            
            return@runCatching fakeMsg
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
        val receiverKey = getProfile(receiverId, forceRefresh = true).getOrNull()?.publicKey
            ?: error("Secure image delivery is unavailable because this user has no encryption key.")
        val (cipherBytes, iv) = crypto.encryptAttachment(imageBytes, receiverKey)
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

    suspend fun downloadEncryptedImage(attachment: WhisperImageAttachment, peerPublicKey: String?): Result<ByteArray> = runCatching {
        if (attachment.expiresAtEpochSeconds != null && java.time.Instant.now().epochSecond >= attachment.expiresAtEpochSeconds) {
            error("This disappearing image has expired.")
        }
        val rawBytes = encryptedImageHost.download(attachment.url).getOrThrow()
        
        // 1. Attempt to decode via lossless PNG transport (ImgBB host)
        val decodedPng = WhisperImageCipherTransport.decode(rawBytes)
        val candidateCipher = decodedPng ?: rawBytes
        
        // 2. Decrypt with candidateCipher, and fallback to rawBytes if needed
        val decrypted = crypto.decryptAttachment(candidateCipher, attachment.iv, peerPublicKey)
            ?: (if (decodedPng != null) crypto.decryptAttachment(rawBytes, attachment.iv, peerPublicKey) else null)
            ?: error("Unable to decrypt this image on this device.")
            
        decrypted
    }


    /** Replays only this signed-in user's ciphertext outbox; safe to call repeatedly. */
    suspend fun flushOutgoingMessages(): Int {
        if (myId.isBlank()) return 0
        var delivered = 0
        outgoingQueue.entries().filter { it.senderId == myId }.forEach { queued ->
            if (queued.attempts >= 8) {
                android.util.Log.w("WhisperRepo", "Dropping undeliverable queued message after ${queued.attempts} attempts (clientId=${queued.clientId})")
                outgoingQueue.remove(queued.clientId)
                return@forEach
            }
            val sent = runCatching {
                db.from("messages").insert(
                    WhisperMessageInsert(
                        senderId = queued.senderId,
                        receiverId = queued.receiverId,
                        content = queued.encryptedContent,
                        contentIv = queued.contentIv,
                        replyToId = queued.replyToId,
                        createdAt = queued.createdAt,
                    )
                )
            }.isSuccess
            if (sent) {
                outgoingQueue.remove(queued.clientId)
                delivered++
            } else {
                outgoingQueue.replace(queued.copy(attempts = queued.attempts + 1))
            }
        }
        if (delivered > 0) invalidateConversationsCache()
        return delivered
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

    suspend fun deleteMessageForEveryone(messageId: String, otherUserId: String, senderDisplayName: String): Result<Unit> = runCatching {
        // A message still in the local outbox was never delivered server-side: drop it
        // locally instead of attempting a server tombstone that cannot match a row.
        if (messageId.startsWith("queued_")) {
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
                crypto.decryptMessage(m.content, m.contentIv, peerKeyFor(otherUserId)) ?: m.content
            } else m.content
            WhisperImageAttachment.fromMessageContent(raw)
        }
        
        // 2. Perform remote attachment deletion if applicable
        attachment?.let { att ->
            runCatching { encryptedImageHost.delete(att.url, att.attachmentId) }
        }

        // 3. Update database with tombstone, selecting back the rows it affected so a
        // mismatch (0 rows) is surfaced instead of silently "succeeding".
        val tombstone = "This message has been deleted"
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
        
        // 4. Erase from local cache only when the server accepted the tombstone.
        if (updated.isNotEmpty()) {
            messageDao.deleteMessage(messageId)
            invalidateConversationsCache()
        } else {
            error("This message could not be deleted. It may have been removed already.")
        }
    }

    suspend fun deleteMessageForMe(messageId: String): Result<Unit> = runCatching {
        if (messageId.isBlank()) return@runCatching
        // 1. Mark as deleted locally in persistent store so it never reappears on reload
        deletedStore.markMessageDeleted(messageId)
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
        val postgresMessageChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }
        val pMsgJob = launch {
            postgresMessageChanges.collect { action ->
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

                        var otherKey = getProfile(otherUserId).getOrNull()?.publicKey
                        if (otherKey == null) {
                            otherKey = getProfile(otherUserId, forceRefresh = true).getOrNull()?.publicKey
                        }

                        val decrypted = if (msg.isDeletedForEveryone) {
                            msg.content
                        } else if (msg.contentIv != null && otherKey != null) {
                            crypto.decryptMessage(msg.content, msg.contentIv, otherKey)
                                ?: crypto.decryptMessage(msg.content, msg.contentIv, getProfile(otherUserId, forceRefresh = true).getOrNull()?.publicKey)
                                ?: "[Encrypted message]"
                        } else msg.content
                        
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

                        // Tombstone updates bypass the dedupe window: a message emitted minutes
                        // earlier must still surface its later deletion to this device.
                        if (msg.isDeletedForEveryone || shouldEmitMessage(msg.id)) trySend(WhisperChatEvent.MessageEvent(finalMsg))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WhisperRepo", "Postgres message realtime error: ${e.message}")
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
                    android.util.Log.e("WhisperRepo", "Postgres reaction realtime error: ${e.message}")
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
            launch {
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
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }
        val dbJob = launch {
            changes.collect { action ->
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
                        var otherKey = getProfile(otherId).getOrNull()?.publicKey
                        
                        if (otherKey == null) {
                            otherKey = getProfile(otherId, forceRefresh = true).getOrNull()?.publicKey
                        }

                        val decrypted = if (msg.isDeletedForEveryone) {
                            msg.content
                        } else if (msg.contentIv != null && otherKey != null) {
                            crypto.decryptMessage(msg.content, msg.contentIv, otherKey)
                                ?: crypto.decryptMessage(msg.content, msg.contentIv, getProfile(otherId, forceRefresh = true).getOrNull()?.publicKey)
                                ?: "[Encrypted message]"
                        } else msg.content

                        if (shouldEmitMessage(msg.id)) trySend(msg.copy(content = decrypted))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WhisperRepo", "Realtime collect error: ${e.message}")
                }
            }
        }

        channel.subscribe()
        channelMutex.withLock {
            broadcastChannelCache[channelName] = channel
        }

        awaitClose {
            dbJob.cancel()
            launch {
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

        val toDelete = db.from("messages").select {
            filter {
                eq("sender_id", currentId)
                eq("receiver_id", otherUserId)
                if (fromIso != null) gte("created_at", fromIso)
                if (toIso != null) lte("created_at", toIso)
            }
        }.decodeList<WhisperMessage>()

        val toDeleteIds = toDelete.map { it.id }.filter { it.isNotBlank() }
        if (toDeleteIds.isNotEmpty()) {
            deletedStore.markMessagesDeleted(toDeleteIds)
            // Clear only the messages selected by the same server predicate. Clearing the
            // whole Room conversation here made received messages disappear then resurrect.
            toDeleteIds.forEach { messageDao.deleteMessage(it) }
            db.from("messages").delete {
                filter {
                    eq("sender_id", currentId)
                    eq("receiver_id", otherUserId)
                    if (fromIso != null) gte("created_at", fromIso)
                    if (toIso != null) lte("created_at", toIso)
                }
            }
        }
        invalidateConversationsCache()
        toDelete
    }

    suspend fun restoreMessages(messages: List<WhisperMessage>): Result<Unit> = runCatching {
        if (messages.isEmpty()) return@runCatching
        deletedStore.unmarkMessagesDeleted(messages.map { it.id })
        val inserts = messages.map { msg ->
            WhisperMessageInsert(
                senderId = msg.senderId,
                receiverId = msg.receiverId,
                content = msg.content,
                contentIv = msg.contentIv,
                isRead = msg.isRead,
                createdAt = msg.createdAt
            )
        }
        db.from("messages").insert(inserts)
        messageDao.insertMessages(messages.map { it.toEntity() })
        invalidateConversationsCache()
    }

    suspend fun getConversations(forceRefresh: Boolean = false): Result<List<WhisperConversation>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())

        val now = System.currentTimeMillis()
        if (!forceRefresh && conversationsCache != null && (now - conversationsCacheTime) < CONVERSATIONS_CACHE_TTL) {
            return Result.success(conversationsCache!!)
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
            val conversations = mutableListOf<WhisperConversation>()
            for (row in rows) {
                if (isUserBlockedByMe(row.partnerId)) continue
                val profile = getProfile(row.partnerId).getOrNull() ?: continue
                val decryptedContent = if (row.lastContent.startsWith("[deleted_by_sender")) {
                    "Message deleted"
                } else if (row.lastContentIv != null && profile.publicKey != null) {
                    crypto.decryptMessage(row.lastContent, row.lastContentIv, profile.publicKey) ?: "🔒 Encrypted message"
                } else if (row.lastContentIv != null) {
                    "🔒 Encrypted message"
                } else row.lastContent

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
                conversationsCache = result
                conversationsCacheTime = System.currentTimeMillis()
            }
        } else {
            val allMessages = db.from("messages")
                .select {
                    filter {
                        or {
                            eq("sender_id", myId)
                            eq("receiver_id", myId)
                        }
                    }
                    order("created_at", Order.DESCENDING)
                    limit(200)
                }
                .decodeList<WhisperMessage>()

            val grouped = allMessages.groupBy { msg ->
                if (msg.senderId == myId) msg.receiverId else msg.senderId
            }
            val conversations = mutableListOf<WhisperConversation>()
            for ((partnerId, msgs) in grouped) {
                if (isUserBlockedByMe(partnerId)) continue
                val visibleMsgs = msgs.filter { !deletedStore.isMessageDeleted(it.id) }
                if (visibleMsgs.isEmpty()) continue
                val profile = getProfile(partnerId).getOrNull() ?: continue
                val lastMsg = visibleMsgs.first()
                val decryptedContent = if (lastMsg.isDeletedForEveryone) {
                    "Message deleted"
                } else if (lastMsg.contentIv != null && profile.publicKey != null) {
                    crypto.decryptMessage(lastMsg.content, lastMsg.contentIv, profile.publicKey) ?: "🔒 Encrypted message"
                } else if (lastMsg.contentIv != null) {
                    "🔒 Encrypted message"
                } else lastMsg.content

                val unread = visibleMsgs.count { it.receiverId == myId && !it.isRead }
                conversations.add(WhisperConversation(profile, lastMsg.copy(content = decryptedContent), unread))
            }
            conversations.sortedByDescending { it.lastMessage.createdAt }
        }.also { result ->
            conversationsCache = result
            conversationsCacheTime = System.currentTimeMillis()
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
        friendships.mapNotNull { friendship -> getProfile(friendship.otherUserId(myId)).getOrNull() }
    }

    suspend fun getPendingIncoming(): Result<List<WhisperFriendship>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())
        db.from("friends").select { filter { eq("user_b", currentId); eq("status", "pending") } }.decodeList()
    }

    suspend fun getPendingIncomingWithProfiles(): Result<List<WhisperFriendRequestItem>> = runCatching {
        val pending = getPendingIncoming().getOrThrow()
        pending.map { f ->
            val senderProfile = getProfile(f.userA).getOrNull()
            WhisperFriendRequestItem(friendship = f, senderProfile = senderProfile)
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

        if (existing != null) {
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
    }

    suspend fun deleteFriendship(friendshipId: String): Result<Unit> = runCatching {
        val existing = db.from("friends").select { filter { eq("id", friendshipId) } }
            .decodeSingleOrNull<WhisperFriendship>() ?: error("This friendship no longer exists.")
        if (existing.userA != myId && existing.userB != myId) {
            error("This friendship is not yours to remove.")
        }
        db.from("friends").delete { filter { eq("id", friendshipId) } }
        if (existing != null) {
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

        // 1. Instant Realtime Broadcast flow
        val broadcastFlow = channel.broadcastFlow<JsonObject>("friend_update")
        val bJob = launch {
            broadcastFlow.collect { json ->
                try {
                    val id = json["id"]?.jsonPrimitive?.content ?: ""
                    val uA = json["user_a"]?.jsonPrimitive?.content ?: ""
                    val uB = json["user_b"]?.jsonPrimitive?.content ?: ""
                    val status = json["status"]?.jsonPrimitive?.content ?: "pending"
                    if (uA.isNotBlank() && uB.isNotBlank()) {
                        trySend(WhisperFriendship(id = id, userA = uA, userB = uB, status = status))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WhisperRepo", "Friend broadcast parse error: ${e.message}")
                }
            }
        }

        // 2. Postgres Change flow fallback
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
            bJob.cancel()
            pJob.cancel()
            launch {
                removeCachedChannel(channelName, channel)
            }
        }
    }


    private val blockedByMeCache = mutableSetOf<String>()

    private suspend fun isUserBlockedByMe(userId: String): Boolean {
        if (blockedByMeCache.contains(userId)) return true
        val isBlocked = runCatching {
            val blocks = db.from("whisper_blocks").select {
                filter {
                    eq("blocker_id", myId)
                    eq("blocked_id", userId)
                }
            }.decodeList<WhisperBlock>()
            blocks.isNotEmpty()
        }.getOrDefault(false)
        if (isBlocked) blockedByMeCache.add(userId)
        return isBlocked
    }

    private suspend fun isUserBlockedByOther(userId: String): Boolean {
        return runCatching {
            val blocks = db.from("whisper_blocks").select {
                filter {
                    eq("blocker_id", userId)
                    eq("blocked_id", myId)
                }
            }.decodeList<WhisperBlock>()
            blocks.isNotEmpty()
        }.getOrDefault(false)
    }

    suspend fun getBlockStatus(otherUserId: String): Pair<Boolean, Boolean> {
        val byMe = isUserBlockedByMe(otherUserId)
        val byOther = isUserBlockedByOther(otherUserId)
        return Pair(byMe, byOther)
    }

    suspend fun blockUser(targetUserId: String): Result<Unit> = runCatching {
        require(targetUserId.isNotBlank() && targetUserId != myId) { "Choose another user to block." }
        db.from("whisper_blocks").insert(WhisperBlockInsert(blockerId = myId, blockedId = targetUserId))
        blockedByMeCache.add(targetUserId)
        blockedIds.update { it + targetUserId }
    }

    suspend fun unblockUser(targetUserId: String): Result<Unit> = runCatching {
        db.from("whisper_blocks").delete { filter { eq("blocker_id", myId); eq("blocked_id", targetUserId) } }
        blockedByMeCache.remove(targetUserId)
        blockedIds.update { it - targetUserId }
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

        // Resolve profiles for these IDs
        val recommended = mutableListOf<WhisperProfile>()
        for (id in fofIds.take(20)) {
            getProfile(id).getOrNull()?.let { p ->
                if (!p.isPrivate && !p.isHiddenFromDiscover && !isUserBlockedByMe(p.id)) {
                    recommended.add(p)
                }
            }
            if (recommended.size >= 15) break
        }
        recommended
    }

    suspend fun getDiscoverProfiles(page: Int, pageSize: Int = 20): Result<List<WhisperProfile>> = runCatching {
        val myFriendIds = getFriendships().getOrThrow()
            .filter { it.status == "accepted" }
            .map { it.otherUserId(myId) }
            .toSet()

        db.from("profiles")
            .select {
                filter {
                    eq("is_private", false)
                    eq("hide_from_discover", false)
                    neq("id", myId)
                }
                order("last_seen_at", Order.DESCENDING)
                range((page * pageSize).toLong(), ((page + 1) * pageSize - 1).toLong())
            }
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
        awaitClose { job.cancel(); launch { removeCachedChannel(name, channel) } }
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
            launch { removeCachedChannel(name, channel) } 
        }
    }

    // ─────────────────────────────────────────────────────────────
    // KEY TRUST & VERIFICATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Builds a [KeyTrustInfo] for a conversation partner. On first encounter the
     * key is silently remembered as known (keeps existing behavior), so only a
     * *change* of a previously known key ever surfaces as [KeyTrustStatus.CHANGED].
     */
    suspend fun getKeyTrustInfo(otherUserId: String): KeyTrustInfo {
        val myFingerprint = crypto.getPublicKeyBase64()?.let { crypto.fingerprint(it) }
        // Force a fresh read so a changed key is never hidden behind the profile cache.
        val profile = getProfile(otherUserId, forceRefresh = true).getOrNull() ?: return KeyTrustInfo(myFingerprint = myFingerprint)
        val currentKey = profile.publicKey
        if (currentKey.isNullOrBlank()) return KeyTrustInfo(myFingerprint = myFingerprint)

        val known = keyTrustStore.knownKey(otherUserId)
        val status = when {
            known == null -> {
                keyTrustStore.rememberKey(otherUserId, currentKey)
                KeyTrustStatus.MATCH
            }
            known == currentKey -> KeyTrustStatus.MATCH
            else -> KeyTrustStatus.CHANGED
        }
        return KeyTrustInfo(
            status = status,
            partnerFingerprint = crypto.fingerprint(currentKey),
            myFingerprint = myFingerprint,
            isVerified = keyTrustStore.verifiedKey(otherUserId) == currentKey,
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
     * Wipes every byte of whisper data this device holds for the signed-in account:
     * Room cache, tombstones, outbox, key trust records, hidden chats, mutes, caches
     * and the E2EE key pair. Called after a successful server-side account deletion.
     */
    suspend fun clearAllLocalData() {
        messageDao.clearAll()
        deletedStore.clearAll()
        outgoingQueue.clearAll()
        keyTrustStore.clearAll()
        hiddenChatsStore.clearAll()
        mutePrefs.clearAll()
        profileCache.clear()
        peerKeys.value = emptyMap()
        blockedIds.value = emptySet()
        blockedByMeCache.clear()
        conversationsCache = null
        conversationsCacheTime = 0L
        crypto.resetKeyPair()
    }
}
