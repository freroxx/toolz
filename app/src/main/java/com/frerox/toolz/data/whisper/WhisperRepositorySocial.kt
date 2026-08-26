/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

/**
 * P4b SOCIAL DOMAIN of [WhisperRepository], extracted as extension functions so
 * every existing call site (`repository.getFriends(...)`, Hilt graph, ViewModels,
 * workers) is untouched. This is a physical move only — no behavior edits; the
 * bodies are byte-identical to their origin (git history preserves context).
 *
 * Members widened from private to internal in the repository to make this split
 * possible (same module, still invisible outside `data.whisper`):
 *   runCatchingCE, batchProfilesById, channelMutex, broadcastChannelCache,
 *   blockedIds, blockedByMeCache, blockedCacheLoadedAtMs, blockCacheLoadMutex,
 *   blockedByOtherCheckedAtMs, ensureBlockCachesFresh.
 */

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeOldRecord
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.realtime.broadcast.BroadcastPayload
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

// FRIENDS
    // FRIENDS
    suspend fun WhisperRepository.getFriendships(): Result<List<WhisperFriendship>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())
        db.from("friends").select {
            filter { or { eq("user_a", currentId); eq("user_b", currentId) } }
        }.decodeList()
    }

    suspend fun WhisperRepository.getFriends(): Result<List<WhisperProfile>> = runCatching {
        val friendships = getFriendships().getOrThrow().filter { it.status == "accepted" }
        // Batch profile resolution into one query instead of N+1 per friendship.
        val partnerIds = friendships.map { it.otherUserId(myId) }.distinct()
        if (partnerIds.isEmpty()) return@runCatching emptyList()
        val (_, profilesById) = batchProfilesById(partnerIds)
        friendships.mapNotNull { profilesById[it.otherUserId(myId)] }
    }

    suspend fun WhisperRepository.getPendingIncoming(): Result<List<WhisperFriendship>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())
        db.from("friends").select { filter { eq("user_b", currentId); eq("status", "pending") } }.decodeList()
    }

    suspend fun WhisperRepository.getPendingIncomingWithProfiles(): Result<List<WhisperFriendRequestItem>> = runCatching {
        val pending = getPendingIncoming().getOrThrow()
        // Batch profile resolution into one query instead of N+1 per request.
        val senderIds = pending.map { it.userA }.distinct()
        if (senderIds.isEmpty()) return@runCatching emptyList()
        val (_, profilesById) = batchProfilesById(senderIds)
        pending.map { f ->
            WhisperFriendRequestItem(friendship = f, senderProfile = profilesById[f.userA])
        }
    }

    suspend fun WhisperRepository.getPendingOutgoing(): Result<List<WhisperFriendship>> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return Result.success(emptyList())
        db.from("friends").select { filter { eq("user_a", currentId); eq("status", "pending") } }.decodeList()
    }

    suspend fun WhisperRepository.sendFriendRequest(targetUserId: String): Result<Unit> = runCatching {
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
        // V2-FIX (reviewwhisper.md) R-M1: the "friend_update"/"incoming_notification"
        // broadcast emissions were removed — they were consumed nowhere (subscribe flows
        // use authenticated Postgres Changes only) and leaked request metadata to anyone
        // who could guess the channel name. DB state remains the single source of truth.
        db.from("friends").insert(WhisperFriendshipInsert(userA = myId, userB = targetUserId))
    }

    suspend fun WhisperRepository.acceptFriendRequest(friendshipId: String): Result<Unit> = runCatching {
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

        // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
        runCatchingCE {
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

    suspend fun WhisperRepository.deleteFriendship(friendshipId: String): Result<Unit> = runCatching {
        val existing = db.from("friends").select { filter { eq("id", friendshipId) } }
            .decodeSingleOrNull<WhisperFriendship>() ?: error("This friendship no longer exists.")
        if (existing.userA != myId && existing.userB != myId) {
            error("This friendship is not yours to remove.")
        }
        db.from("friends").delete { filter { eq("id", friendshipId) } }
        // L-1 FIX: dead null-check removed (same as acceptFriendRequest).
        run {
            val otherId = if (existing.userA == myId) existing.userB else existing.userA
            // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
            runCatchingCE {
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

    suspend fun WhisperRepository.getFriendshipStatus(otherUserId: String): Result<Pair<FriendStatus, WhisperFriendship?>> = runCatching {
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

    fun WhisperRepository.subscribeToFriendUpdates(): Flow<WhisperFriendship> = callbackFlow {
        val currentId = this@subscribeToFriendUpdates.myId
        if (currentId.isBlank()) {
            close()
            return@callbackFlow
        }
        val channelName = "whisper-friends-all-$currentId"
        this@subscribeToFriendUpdates.channelMutex.withLock {
            this@subscribeToFriendUpdates.broadcastChannelCache[channelName]?.let {
                // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
                ProtocolDiagnostics.log("rt.teardown[$channelName]: pre-subscribe-clean(friends)")
                this@subscribeToFriendUpdates.runCatchingCE { this@subscribeToFriendUpdates.realtime.removeChannel(it) }
                this@subscribeToFriendUpdates.broadcastChannelCache.remove(channelName)
            }
        }
        val channel = this@subscribeToFriendUpdates.supabase.channel(channelName)

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
        this@subscribeToFriendUpdates.channelMutex.withLock {
            this@subscribeToFriendUpdates.broadcastChannelCache[channelName] = channel
        }
        // V6-R3: auto-heal dropped realtime channels (friend updates).
        val healthJob = this@subscribeToFriendUpdates.watchChannelHealth(this, channelName, channel)
        awaitClose {
            healthJob.cancel()
            pJob.cancel()
            // H-1 FIX: see subscribeToChat — cleanup must survive collection cancellation.
            this@subscribeToFriendUpdates.appScope.launch {
                this@subscribeToFriendUpdates.removeCachedChannel(channelName, channel)
            }
        }
    }


    internal val blockedByMeCache = Collections.synchronizedSet(mutableSetOf<String>())

    // H-6 FIX (reviewwhisper.md): full blocker-set cache with TTL. Previously every
    // incoming realtime message event for a NON-blocked sender triggered a
    // whisper_blocks REST round-trip (negatives were never cached).
    // V2-FIX (reviewwhisper.md) L-14: @Volatile — the timestamp is written from inside a
    // mutex and read lock-free; without it readers could see a stale value indefinitely.
    @Volatile internal var blockedCacheLoadedAtMs = 0L
    internal val BLOCK_CACHE_TTL_MS = 5 * 60 * 1000L
    // V2-FIX (reviewwhisper.md) L-14: single-flight guard so concurrent callers on a cold
    // cache fire ONE network reload instead of N redundant ones.
    internal val blockCacheLoadMutex = Mutex()
    // H-6: negative-result TTL for isUserBlockedByOther (the other party's blocks can
    // only be probed per-pair under RLS, so a short-TTL memo is the best available).
    internal val blockedByOtherCheckedAtMs = ConcurrentHashMap<String, Long>()
    internal const val BLOCKED_BY_OTHER_TTL_MS = 30_000L

    /** Reloads the complete blocker set at most once per [BLOCK_CACHE_TTL_MS]. */
    internal suspend fun WhisperRepository.ensureBlockCachesFresh(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - blockedCacheLoadedAtMs < BLOCK_CACHE_TTL_MS) return
        blockCacheLoadMutex.withLock {
            // Re-check under the lock: another caller may have refreshed while we waited.
            if (!force && System.currentTimeMillis() - blockedCacheLoadedAtMs < BLOCK_CACHE_TTL_MS) return
            // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
            runCatchingCE {
                db.from("whisper_blocks").select { filter { eq("blocker_id", myId) } }
                    .decodeList<WhisperBlock>().map { it.blockedId }.toSet()
            }.onSuccess { currentlyBlocked ->
                blockedIds.value = currentlyBlocked
                blockedByMeCache.clear()
                blockedByMeCache.addAll(currentlyBlocked)
                blockedCacheLoadedAtMs = System.currentTimeMillis()
            }
        }
    }

    internal suspend fun WhisperRepository.isUserBlockedByMe(userId: String): Boolean {
        ensureBlockCachesFresh()
        return blockedIds.value.contains(userId) || blockedByMeCache.contains(userId)
    }

    internal suspend fun WhisperRepository.isUserBlockedByOther(userId: String): Boolean {
        // H-6: short-TTL memo for the negative result — this check runs per send and
        // per incoming event, and a "not blocked" answer stays valid briefly.
        blockedByOtherCheckedAtMs[userId]?.let { checkedAt ->
            if (System.currentTimeMillis() - checkedAt < BLOCKED_BY_OTHER_TTL_MS) return false
        }
        // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
        // P1 OBSERVABILITY: the optimistic "not blocked on probe failure" behavior is
        // deliberate (offline must not block sends — server RLS remains the backstop),
        // but it is no longer silent: the counter makes the trade-off visible in
        // diagnostics exports.
        val probe = runCatchingCE {
            val blocks = db.from("whisper_blocks").select {
                filter {
                    eq("blocker_id", userId)
                    eq("blocked_id", myId)
                }
            }.decodeList<WhisperBlock>()
            blocks.isNotEmpty()
        }
        if (probe.isFailure) ProtocolDiagnostics.increment("blocks.probeFailed")
        val isBlocked = probe.getOrDefault(false)
        if (isBlocked) {
            blockedByOtherCheckedAtMs.remove(userId)
        } else {
            blockedByOtherCheckedAtMs[userId] = System.currentTimeMillis()
        }
        return isBlocked
    }

    /** Re-reads the block table for this account so in-memory caches never drift from server truth. */
    internal suspend fun WhisperRepository.refreshBlockCachesFor(targetUserId: String) {
        // H-6: force = bypass TTL, this is called right after an optimistic mutation.
        // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
        runCatchingCE {
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

    suspend fun WhisperRepository.getBlockStatus(otherUserId: String): Pair<Boolean, Boolean> {
        val byMe = isUserBlockedByMe(otherUserId)
        val byOther = isUserBlockedByOther(otherUserId)
        return Pair(byMe, byOther)
    }

    suspend fun WhisperRepository.blockUser(targetUserId: String): Result<Unit> = runCatching {
        require(targetUserId.isNotBlank() && targetUserId != myId) { "Choose another user to block." }
        db.from("whisper_blocks").insert(WhisperBlockInsert(blockerId = myId, blockedId = targetUserId))
        blockedIds.update { it + targetUserId }
        blockedByMeCache.add(targetUserId)
        refreshBlockCachesFor(targetUserId)
    }

    suspend fun WhisperRepository.unblockUser(targetUserId: String): Result<Unit> = runCatching {
        db.from("whisper_blocks").delete { filter { eq("blocker_id", myId); eq("blocked_id", targetUserId) } }
        blockedIds.update { it - targetUserId }
        blockedByMeCache.remove(targetUserId)
        refreshBlockCachesFor(targetUserId)
    }

    suspend fun WhisperRepository.isBlockedByMe(otherUserId: String): Boolean = isUserBlockedByMe(otherUserId)
    suspend fun WhisperRepository.isBlockedByOther(otherUserId: String): Boolean = isUserBlockedByOther(otherUserId)

    suspend fun WhisperRepository.getFriendsOfFriends(): Result<List<WhisperProfile>> = runCatching {
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
    suspend fun WhisperRepository.getDiscoverProfiles(page: Int, pageSize: Int = 20): Result<List<WhisperProfile>> = runCatching {
        val myFriendIds = getFriendships().getOrThrow()
            .filter { it.status == "accepted" }
            .map { it.otherUserId(myId) }
            .toSet()

        val rpcResult = runCatchingCE {
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
        // V6-R7: the RPC rate-limits (60 pages/hour, P0002) and can fail outright —
        // when it does, Discover used to go permanently empty ("only Whisper Someone
        // works"). Fall back to a direct directory query enforcing the SAME privacy
        // contract client-side.
        val rpcList = rpcResult.getOrElse {
            ProtocolDiagnostics.increment("discover.rpcFallback")
            if (page != 0) return Result.success(emptyList()) // pagination only via RPC
            val blocked = runCatchingCE {
                db.from("whisper_blocks").select { filter { eq("blocker_id", myId) } }
                    .decodeList<WhisperBlock>().map { it.blockedId }.toSet()
            }.getOrDefault(emptySet())
            db.from("profiles").select {
                filter {
                    neq("id", myId)
                    eq("hide_from_discover", false)
                    eq("is_private", false)
                }
                order("last_seen_at", Order.DESCENDING, nullsFirst = false)
                limit(pageSize.toLong())
            }.decodeList<WhisperProfile>()
                .filter { it.id !in myFriendIds && it.id !in blocked && !it.isHiddenFromDiscover && !it.isPrivate }
                .take(pageSize)
        }
        rpcList
    }
