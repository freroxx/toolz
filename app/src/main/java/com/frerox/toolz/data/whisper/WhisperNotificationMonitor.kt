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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch

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
        const val RETRY_DELAY_MS = 3_000L
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
        repository.subscribeToIncomingMessages(userId)
            .retry { cause -> retryable(userId, cause) }
            .collect { msg ->
                // Shared flow first: the hub may only be refreshing its list.
                _messageEvents.tryEmit(msg)

                // Same suppression contract as before, now independent of any
                // screen: read/deleted/own rows never notify.
                if (msg.isRead || msg.isDeletedForEveryone) return@collect
                if (msg.senderId == userId) return@collect
                if (mutePrefs.isMuted(msg.senderId)) return@collect

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
        repository.subscribeToFriendUpdates()
            .retry { cause -> retryable(userId, cause) }
            .collect { friendship ->
                _friendshipEvents.tryEmit(friendship)

                val incomingPending =
                    friendship.userB == userId && friendship.status == "pending"
                if (!incomingPending) return@collect
                if (mutePrefs.isMuted(friendship.userA)) return@collect

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

    private suspend fun retryable(userId: String, cause: Throwable): Boolean {
        val stillCurrent =
            authManager.currentUserId == userId &&
                !gate.sandboxed(DevToolId.WHISPER) &&
                !WhisperErrorMapper.isSessionExpired(cause) &&
                !(cause is io.github.jan.supabase.exceptions.RestException &&
                    cause.statusCode in 401..403)
        if (!stillCurrent) return false
        android.util.Log.e(TAG, "Realtime lane dropped: ${cause.message}. Retrying in ${RETRY_DELAY_MS}ms")
        delay(RETRY_DELAY_MS)
        return true
    }
}
