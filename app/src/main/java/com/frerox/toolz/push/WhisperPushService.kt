/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.push

import android.content.Context
import com.frerox.toolz.data.whisper.WhisperNotificationManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * V3-FIX (reviewwhisper.md item 6): receives FCM data-only wake pings sent by the
 * `whisper-push-send` Edge Function when a message lands for a user whose realtime
 * socket is dead (app backgrounded/killed).
 *
 * Privacy contract: the payload carries NO message content — only
 * `{whisper_new_message: true, senderId}`. Nothing readable ever transits Google's
 * push transport; the encrypted body stays in Supabase until the app syncs.
 */
@AndroidEntryPoint
class WhisperPushService : FirebaseMessagingService() {

    @Inject lateinit var notificationManager: WhisperNotificationManager
    @Inject lateinit var supabase: SupabaseClient
    @Inject lateinit var tokenStore: WhisperPushTokenStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        // Fired on install/rotate. If the user isn't signed in yet, the token is parked
        // and uploaded on the next launch once a session exists (see TokenStore).
        serviceScope.launch { tokenStore.register(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val senderId = message.data["senderId"].orEmpty()
        if (!message.data["whisper_new_message"].toBoolean() || senderId.isEmpty()) return

        // Generic, content-free notification — identical body to the in-app realtime
        // path so lock-screen behavior never leaks text (VISIBILITY_PRIVATE).
        notificationManager.showMessageNotification(senderId = senderId, senderName = senderId)
    }

    override fun onDestroy() {
        serviceScope.coroutineContext.cancelChildren()
        super.onDestroy()
    }
}

/**
 * Persists the current FCM registration token to `whisper_fcm_tokens` (RLS-scoped to
 * the signed-in row owner). Handles the "token arrives before login" race by parking
 * it locally and retrying on subsequent launches.
 */
@Singleton
class WhisperPushTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
) {
    @Serializable
    private data class TokenRow(
        @SerialName("user_id") val userId: String,
        @SerialName("token") val token: String,
    )

    suspend fun register(token: String) {
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId == null) {
            parkPending(token)
            return
        }
        val uploaded = runCatching {
            // user_id is the table PK, so a plain upsert merges duplicates.
            supabase.postgrest.from(TOKENS_TABLE).upsert(TokenRow(userId = userId, token = token))
        }.isSuccess
        if (uploaded) clearPending()
    }

    /** Called once per app start: uploads any token parked before sign-in. */
    suspend fun retryPendingIfAny() {
        val pending = pendingToken() ?: return
        register(pending)
    }

    private fun prefs() =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun parkPending(token: String) {
        prefs().edit().putString(KEY_PENDING, token).commit()
    }

    private fun pendingToken(): String? =
        prefs().getString(KEY_PENDING, null)

    private fun clearPending() {
        prefs().edit().remove(KEY_PENDING).commit()
    }

    private companion object {
        const val PREFS = "whisper_push_pending"
        const val KEY_PENDING = "pending_token"
        const val TOKENS_TABLE = "whisper_fcm_tokens"
    }
}
