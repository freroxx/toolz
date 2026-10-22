/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.push

import android.content.Context
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UnifiedPush push entry point (F-Droid swap for the old FirebaseMessagingService):
 * receives the data-only wake pings sent by the `whisper-push-send` Edge Function
 * when a message lands for a user whose realtime socket is dead (app
 * backgrounded/killed).
 *
 * The service is registered against whichever UnifiedPush distributor was chosen
 * at startup — by default the bundled embedded distributor (zero setup on devices
 * with Google Play Services or microG), or an external distributor (ntfy,
 * NextPush, Sunup, ...) the user already has installed — see
 * [WhisperPushRegistrar].
 *
 * Privacy contract: the payload carries NO message content — only
 * `{whisper_new_message: true, senderId, ...}`. WebPush targets (embedded
 * distributor, Sunup, gCompat-UP, ...) additionally travel as RFC 8291-encrypted
 * blobs end-to-end; plain targets (ntfy/NextPush) see only the same id/name keys
 * the old FCM pings already exposed.
 */
@AndroidEntryPoint
class WhisperPushService : PushService() {

    @Inject lateinit var notifier: WhisperPushNotifier
    @Inject lateinit var targetStore: WhisperPushTargetStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessage(message: PushMessage, instance: String) {
        val json = runCatching { String(message.content, Charsets.UTF_8) }.getOrDefault("")
        if (json.isBlank()) return
        // The connector has already decrypted RFC 8291 WebPush payloads when the
        // distributor provides key sets; plain-distributor payloads arrive as sent.
        notifier.handlePayload(json)
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        if (endpoint.url.isBlank()) return
        // Fired on install/endpoint rotation. If the user isn't signed in yet, the
        // endpoint is parked locally and uploaded on the next launch once a session
        // exists (see WhisperPushTargetStore).
        serviceScope.launch { runCatching { targetStore.register(endpoint) } }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        // Transient (NETWORK / VAPID_REQUIRED / ...): WhisperPushRegistrar
        // re-registers on the next app start; nothing to clean up here.
        Log.w(TAG, "UnifiedPush registration failed: $reason")
    }

    override fun onUnregistered(instance: String) {
        // Registration revoked (user switched/uninstalled the distributor): stop
        // sending pings to this endpoint server-side.
        serviceScope.launch { runCatching { targetStore.clear() } }
    }

    override fun onDestroy() {
        serviceScope.coroutineContext.cancelChildren()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WhisperPush"
    }
}

/**
 * Persists the current UnifiedPush registration to `whisper_push_targets`
 * (RLS-scoped to the signed-in row owner) so the `whisper-push-send` Edge
 * Function can reach this device. Handles the "endpoint arrives before login"
 * race by parking it locally and retrying on subsequent launches.
 *
 * A row's `provider` mirrors the WebPush capability of the distributor:
 * `webpush` (endpoint came with RFC 8291 key sets — server encrypts pings) or
 * `plain` (endpoint expects the raw payload bytes).
 */
@Singleton
class WhisperPushTargetStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
) {
    @Serializable
    private data class TargetRow(
        @SerialName("user_id") val userId: String,
        @SerialName("provider") val provider: String,
        @SerialName("endpoint") val endpoint: String,
        @SerialName("keys_p256dh") val keysP256dh: String? = null,
        @SerialName("keys_auth") val keysAuth: String? = null,
    )

    private data class PendingTarget(
        val provider: String,
        val url: String,
        val p256dh: String?,
        val auth: String?,
    )

    suspend fun register(endpoint: PushEndpoint) {
        val url = endpoint.url
        if (url.isBlank()) return
        val provider = if (endpoint.pubKeySet != null) PROVIDER_WEBPUSH else PROVIDER_PLAIN
        val keys = endpoint.pubKeySet
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId == null) {
            parkPending(provider, url, keys?.pubKey, keys?.auth)
            return
        }
        val uploaded = runCatching {
            // user_id is the table PK: a plain upsert replaces the previous target.
            supabase.postgrest.from(TARGETS_TABLE).upsert(
                TargetRow(
                    userId = userId,
                    provider = provider,
                    endpoint = url,
                    keysP256dh = keys?.pubKey,
                    keysAuth = keys?.auth,
                ),
            )
        }.isSuccess
        if (uploaded) clearPending()
    }

    /** Called once per app start: uploads any target parked before sign-in. */
    suspend fun retryPendingIfAny() {
        val pending = pendingTarget() ?: return
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        val uploaded = runCatching {
            supabase.postgrest.from(TARGETS_TABLE).upsert(
                TargetRow(
                    userId = userId,
                    provider = pending.provider,
                    endpoint = pending.url,
                    keysP256dh = pending.p256dh,
                    keysAuth = pending.auth,
                ),
            )
        }.isSuccess
        if (uploaded) clearPending()
    }

    /** Registration was revoked (unregistered by the distributor / user action / logout). */
    suspend fun clear(explicitUserId: String? = null) {
        clearPending()
        val userId = explicitUserId ?: supabase.auth.currentUserOrNull()?.id ?: return
        runCatching {
            supabase.postgrest.from(TARGETS_TABLE).delete { filter { eq("user_id", userId) } }
        }
    }

    private fun prefs() =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun parkPending(provider: String, url: String, p256dh: String?, auth: String?) {
        prefs().edit()
            .putString(KEY_PROVIDER, provider)
            .putString(KEY_URL, url)
            .putString(KEY_P256DH, p256dh)
            .putString(KEY_AUTH, auth)
            .commit()
    }

    private fun pendingTarget(): PendingTarget? {
        val url = prefs().getString(KEY_URL, null)
        if (url.isNullOrBlank()) return null
        return PendingTarget(
            provider = prefs().getString(KEY_PROVIDER, null) ?: PROVIDER_PLAIN,
            url = url,
            p256dh = prefs().getString(KEY_P256DH, null),
            auth = prefs().getString(KEY_AUTH, null),
        )
    }

    private fun clearPending() {
        prefs().edit().clear().commit()
    }

    private companion object {
        const val PREFS = "whisper_push_pending"
        const val KEY_PROVIDER = "pending_provider"
        const val KEY_URL = "pending_url"
        const val KEY_P256DH = "pending_p256dh"
        const val KEY_AUTH = "pending_auth"
        const val TARGETS_TABLE = "whisper_push_targets"
        const val PROVIDER_WEBPUSH = "webpush"
        const val PROVIDER_PLAIN = "plain"
    }
}
