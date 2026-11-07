/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import com.frerox.toolz.R
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.SpoofGate
import com.frerox.toolz.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * App-scoped Whisper realtime monitor.
 *
 * Field reports showed notifications stopping as soon as the Whisper hub screen
 * left the composition: the inbox and friend realtime channels were owned by
 * [com.frerox.toolz.ui.screens.whisper.WhisperViewModel], so closing the screen
 * cancelled the only collectors. This singleton owns exactly ONE subscription
 * per lane for the whole process lifetime:
 *
 *  • inbox messages (`subscribeToIncomingMessages`) → notification + shared flow
 *    for the hub conversation list;
 *  • friendship updates (`subscribeToFriendUpdates`) → friend-request
 *    notification + shared flow for the friends/hub list.
 *
 * The monitor is idempotent (`start()` can be called from Application and from
 * any screen) and stops its collectors on sign-out or when the Whisper sandbox
 * is enabled. Both repository flows already contain REST fallbacks, so a dead
 * websocket no longer silences notifications.
 */
@Singleton
class WhisperNotificationMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: WhisperAuthManager,
    private val repository: WhisperRepository,
    private val notificationManager: WhisperNotificationManager,
    private val mutePrefs: WhisperMutePreferences,
    private val gate: SpoofGate,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private companion object {
        const val TAG = "WhisperNotifMonitor"
        const val MAX_JITTER_MS = 1_000L
        const val LOG_EVERY_N_ATTEMPTS = 4
    }

    private val _messageEvents = MutableSharedFlow<WhisperMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messageEvents: SharedFlow<WhisperMessage> = _messageEvents.asSharedFlow()

    private val _friendshipEvents = MutableSharedFlow<WhisperFriendship>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val friendshipEvents: SharedFlow<WhisperFriendship> = _friendshipEvents.asSharedFlow()

    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Idempotent; safe to call from every process entry point. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch(start = CoroutineStart.UNDISPATCHED) { observeSession() }
    }

    private suspend fun observeSession() {
        var currentUserId: String? = null
        var collectors: Job? = null

        combine(
            authManager.isAuthenticated,
            gate.active(DevToolId.WHISPER),
        ) { authenticated, sandboxed ->
            // A sandboxed Whisper has no Supabase session by design; never start
            // realtime collectors against the fake fixture account.
            if (authenticated == true && !sandboxed) authManager.currentUserId else null
        }
            .distinctUntilChanged()
            .collect { userId ->
                if (userId == null) {
                    collectors?.cancel()
                    collectors = null
                    currentUserId = null
                    return@collect
                }
                if (userId == currentUserId && collectors?.isActive == true) return@collect
                collectors?.cancel()
                currentUserId = userId
                collectors = appScope.launch { collectFor(userId) }
            }
    }

    private suspend fun collectFor(userId: String) = coroutineScope {
        // Structured concurrency: cancelling the session job (sign-out / account
        // switch / sandbox) cancels BOTH lanes; neither can outlive the other.
        launch { collectMessages(userId) }
        launch { collectFriendships(userId) }
    }

    private suspend fun collectMessages(userId: String) {
        collectWithReconnect(
            userId = userId,
            lane = "messages",
            source = { repository.subscribeToIncomingMessages(userId) },
        ) { msg ->
            // Shared flow first: the hub may only be refreshing its list.
            _messageEvents.tryEmit(msg)

            // Same suppression contract as before, now independent of any
            // screen: read/deleted/own rows never notify.
            if (msg.isRead || msg.isDeletedForEveryone) return@collectWithReconnect
            if (msg.senderId == userId) return@collectWithReconnect
            if (mutePrefs.isMuted(msg.senderId)) return@collectWithReconnect

            notificationManager.showMessageNotification(
                senderId = msg.senderId,
                // Notification text is intentionally content-free, so the
                // sender id is enough for the privacy-safe body.
                senderName = msg.senderId,
                messageId = msg.id,
                isRead = msg.isRead,
            )
        }
    }

    private suspend fun collectFriendships(userId: String) {
        collectWithReconnect(
            userId = userId,
            lane = "friendships",
            source = { repository.subscribeToFriendUpdates() },
        ) { friendship ->
            _friendshipEvents.tryEmit(friendship)

            val incomingPending =
                friendship.userB == userId && friendship.status == "pending"
            if (!incomingPending) return@collectWithReconnect
            if (mutePrefs.isMuted(friendship.userA)) return@collectWithReconnect

            // The notification manager dedupes per sender/friendship id, so
            // FCM + realtime (and poll seeds) can never double-post.
            val senderName = repository.getProfile(friendship.userA)
                .getOrNull()
                ?.effectiveName
                ?: context.getString(R.string.st_Whisper_SomeoneDefault)

            notificationManager.showFriendRequestNotification(
                fromId = friendship.userA,
                fromName = senderName,
                friendshipId = friendship.id,
            )
        }
    }

    /**
     * Reconnects ONE lane with exponential backoff. The previous `.retry` operator
     * retried forever every 3 s and, worse, an exception escaping this lane
     * cancelled its sibling via structured concurrency. Catching inside the lane
     * keeps the friend-request lane alive when the message socket is unhealthy
     * (and vice versa).
     */
    private suspend fun <T> collectWithReconnect(
        userId: String,
        lane: String,
        source: () -> Flow<T>,
        onItem: suspend (T) -> Unit,
    ) {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            try {
                source().collect { item ->
                    // A delivered item proves the lane is healthy: restart the
                    // backoff so the next transient drop retries immediately.
                    attempt = 0
                    onItem(item)
                }
                // A clean completion is unusual (realtime flows stay open); treat
                // it like a drop and reconnect with backoff.
                if (!stillCurrent(userId, null)) return
            } catch (c: CancellationException) {
                throw c
            } catch (cause: Throwable) {
                if (!stillCurrent(userId, cause)) return
            }

            val jitter = Random.nextLong(0L, MAX_JITTER_MS)
            val retryInMs = WhisperRealtimeBackoff.delayMs(attempt, jitter)
            if (attempt == 0 || attempt % LOG_EVERY_N_ATTEMPTS == 0) {
                android.util.Log.w(
                    TAG,
                    "Realtime lane '$lane' dropped — reconnecting in ${retryInMs}ms (attempt ${attempt + 1})",
                )
            }
            attempt++
            delay(retryInMs)
        }
    }

    private fun stillCurrent(userId: String, cause: Throwable?): Boolean =
        authManager.currentUserId == userId &&
            !gate.sandboxed(DevToolId.WHISPER) &&
            cause?.let {
                !WhisperErrorMapper.isSessionExpired(it) &&
                    !(it is io.github.jan.supabase.exceptions.RestException &&
                        it.statusCode in 401..403)
            } != false
}
