/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.Context
import android.os.Build
import com.frerox.toolz.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** One server row returned by `whisper_device_list()`. */
@Serializable
data class WhisperDeviceRow(
    @SerialName("device_id") val deviceId: String,
    val name: String,
    val platform: String,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("public_key") val publicKey: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("revoked_at") val revokedAt: String? = null,
)

/** UI model for one connected install. */
data class WhisperDevice(
    val id: String,
    val name: String,
    val platform: String,
    val appVersion: String?,
    val createdAtMs: Long,
    val lastSeenAtMs: Long,
    val revoked: Boolean,
    val isCurrent: Boolean,
)

/**
 * Registers this install and keeps the account's device list in sync.
 *
 * The server table is owner-only and the RPCs derive `user_id` from
 * `auth.uid()`; this manager never sends a user id. Disconnect is a soft
 * revoke and is gated by a fresh secret proof in the ViewModel before the RPC
 * is called.
 */
@Singleton
class WhisperDeviceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
    private val authManager: WhisperAuthManager,
    private val crypto: WhisperCrypto,
) {
    companion object {
        private const val PREFS = "whisper_device_identity"
        private const val KEY_DEVICE_ID = "device_id"
        private const val MAX_NAME_LENGTH = 80
    }

    /** Stable per-install id. Generated once and never account-scoped. */
    fun currentDeviceId(): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null && runCatching { UUID.fromString(existing) }.isSuccess) {
            return existing
        }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    /**
     * Human-readable install label. Kept deliberately boring: manufacturer +
     * model, stripped of control characters and capped to the server's limit.
     */
    fun currentDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val raw = when {
            model.isBlank() && manufacturer.isBlank() -> "Android device"
            model.isBlank() -> manufacturer
            manufacturer.isBlank() -> model
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
        val cleaned = raw
            .filter { !it.isISOControl() }
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Android device" }
        return cleaned.take(MAX_NAME_LENGTH)
    }

    fun isCurrent(deviceId: String): Boolean =
        deviceId.equals(currentDeviceId(), ignoreCase = true)

    /** Upsert this install. Best-effort; a failure must never block sign-in. */
    suspend fun register(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireAuthenticated()
            val publicKey = runCatching { crypto.getPublicKeyBase64() }.getOrNull()
            supabase.postgrest.rpc(
                "whisper_device_register",
                buildJsonObject {
                    put("p_device_id", currentDeviceId())
                    put("p_name", currentDeviceName())
                    put("p_platform", "android")
                    put("p_app_version", BuildConfig.VERSION_NAME.take(40))
                    if (!publicKey.isNullOrBlank()) {
                        put("p_public_key", publicKey.take(512))
                    }
                },
            )
            Unit
        }
    }

    /** Refreshes `last_seen_at`. False means the row is gone/revoked. */
    suspend fun heartbeat(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            requireAuthenticated()
            supabase.postgrest.rpc(
                "whisper_device_heartbeat",
                buildJsonObject { put("p_device_id", currentDeviceId()) },
            ).decodeAs<Boolean>()
        }
    }

    suspend fun list(): Result<List<WhisperDevice>> = withContext(Dispatchers.IO) {
        runCatching {
            requireAuthenticated()
            val current = currentDeviceId()
            supabase.postgrest.rpc("whisper_device_list")
                .decodeList<WhisperDeviceRow>()
                .map { row ->
                    WhisperDevice(
                        id = row.deviceId,
                        name = row.name.ifBlank { "Android device" },
                        platform = row.platform,
                        appVersion = row.appVersion,
                        createdAtMs = parseInstant(row.createdAt),
                        lastSeenAtMs = parseInstant(row.lastSeenAt),
                        revoked = row.revokedAt != null,
                        isCurrent = row.deviceId.equals(current, ignoreCase = true),
                    )
                }
                .sortedWith(
                    compareBy<WhisperDevice> { it.revoked }
                        .thenByDescending { it.lastSeenAtMs }
                )
        }
    }

    /**
     * Soft-revokes another install. The caller MUST have re-proved the account
     * secret first (see [reauthenticate]); this method intentionally does not
     * accept a password so it cannot be called without that gate.
     */
    suspend fun revoke(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireAuthenticated()
            require(!isCurrent(deviceId)) { "Use sign out to disconnect this device." }
            val revoked = supabase.postgrest.rpc(
                "whisper_device_revoke",
                buildJsonObject { put("p_device_id", deviceId) },
            ).decodeAs<Boolean>()
            require(revoked) { "That device is no longer connected." }
        }
    }

    /**
     * Re-proves the account secret before a sensitive device action.
     *
     * Password accounts re-run the normal sign-in (which also refreshes the
     * JWT). Token accounts re-derive their virtual credential from the token.
     * A wrong secret leaves the existing session untouched.
     */
    suspend fun reauthenticate(secret: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(secret.isNotBlank()) { "Enter your account password." }
            val email = authManager.currentUserEmail
                ?: error("Not authenticated")
            if (email.endsWith("@whisper.toolz.app")) {
                authManager.loginWithToken(secret).getOrThrow()
            } else {
                val username = authManager.currentUserMetadata["username"]
                    ?.toString()
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?.takeIf { it.isNotBlank() }
                    ?: email.substringBefore('@')
                authManager.loginWithUsername(username, secret).getOrThrow()
            }
        }
    }

    private fun requireAuthenticated() {
        check(supabase.auth.currentUserOrNull() != null) { "Not authenticated" }
    }

    private fun parseInstant(value: String?): Long =
        if (value.isNullOrBlank()) 0L else runCatching { Instant.parse(value).toEpochMilli() }
            .getOrDefault(0L)
}
