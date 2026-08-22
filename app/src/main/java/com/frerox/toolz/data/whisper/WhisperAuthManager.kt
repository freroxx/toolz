/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import com.frerox.toolz.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperAuthManager @Inject constructor(
    private val supabase: SupabaseClient,
) {
    sealed interface EmailRegistrationResult {
        data object SignedIn : EmailRegistrationResult
        data class VerificationRequired(val email: String) : EmailRegistrationResult
    }
    val sessionStatus: Flow<SessionStatus>
        get() = supabase.auth.sessionStatus
    val isAuthenticated: Flow<Boolean?>
        get() = sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Initializing -> null
                is SessionStatus.Authenticated -> true
                else -> false
            }
        }
    val isInitializing: Flow<Boolean>
        get() = sessionStatus.map { it is SessionStatus.Initializing }
    val currentUserId: String?
        get() = supabase.auth.currentUserOrNull()?.id
    val currentUserEmail: String?
        get() = supabase.auth.currentUserOrNull()?.email
    val currentUserMetadata: Map<String, Any?>
        get() = supabase.auth.currentUserOrNull()?.userMetadata ?: emptyMap()
    val isReady: Boolean
        get() = supabase.auth.sessionStatus.value !is SessionStatus.Initializing
    val isAnonymousTokenUser: Boolean
        get() = supabase.auth.currentUserOrNull()?.email?.endsWith("@whisper.toolz.app") == true

    val isCurrentEmailVerified: Boolean
        get() = supabase.auth.currentUserOrNull()?.emailConfirmedAt != null

    suspend fun deleteAccount(password: String? = null): Result<Unit> = runCatching {
        val user = supabase.auth.currentUserOrNull() ?: error("Not authenticated")
        val email = user.email ?: error("No account associated")
        val isTokenUser = email.endsWith("@whisper.toolz.app")

        if (!isTokenUser) {
            require(!password.isNullOrBlank()) { "Password is required to delete account." }
            // Re-authenticate to verify password before deletion
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }

        // Ask the edge function (service role, JWT-verified) to permanently delete the
        // GoTrue user. The caller wipes local data after this returns.
        // P0-4 FIX: Also send X-Whisper-Password so the edge function can independently
        // verify re-auth. A stolen JWT alone can no longer delete the account.
        val token = supabase.auth.currentSessionOrNull()?.accessToken ?: error("Sign in before deleting your account.")
        withContext(Dispatchers.IO) {
            val connection = (URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/whisper-delete-account").openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 30_000
                connection.doOutput = false
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Content-Type", "application/json")
                // P0-4: forward password confirmation for server-side verification (omitted for anon token users)
                if (!isTokenUser && !password.isNullOrBlank()) {
                    connection.setRequestProperty("X-Whisper-Password", password)
                }
                // Also include a fresh confirmation nonce (timestamp) to prevent replay beyond 5 min
                connection.setRequestProperty("X-Whisper-Confirm-Ts", System.currentTimeMillis().toString())
                if (connection.responseCode !in 200..299) {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    error(errorBody.take(200).ifBlank { "HTTP ${connection.responseCode}" })
                }
            } finally {
                connection.disconnect()
            }
        }

        // M-5 FIX (reviewwhisper.md): ALL server-side data cleanup now happens INSIDE
        // whisper-delete-account BEFORE the GoTrue user is deleted. The old client-side
        // postgrest deletes ran AFTER user deletion, relying on RLS matching a JWT sub
        // that no longer corresponds to a live user — fragile against session revocation
        // tightening, and it silently no-op'd if Supabase changed that behavior.
        //
        // FK-cascaded tables (whisper_upload_quota, whisper_deleted_tombstones,
        // whisper_discover_quota) clean themselves automatically.

        // Sign out
        supabase.auth.signOut()
    }

    suspend fun registerWithUsername(username: String, password: String, displayName: String): Result<Unit> = runCatching {
        val cleanUsername = username.trim().lowercase()
        val cleanDisplayName = displayName.trim()
        val virtualEmail = "$cleanUsername@u.whisper.local"
        
        require(password.length >= MIN_PASSWORD_LENGTH) { "Password must be at least $MIN_PASSWORD_LENGTH characters." }
        require(USERNAME_PATTERN.matches(cleanUsername)) { "Username must be 3-20 lowercase letters, numbers, or underscores." }
        require(cleanDisplayName.length in 1..60) { "Display name must be 1-60 characters." }
        
        supabase.auth.signUpWith(Email) {
            this.email = virtualEmail
            this.password = password
            this.data = buildJsonObject {
                put("username", cleanUsername)
                put("display_name", cleanDisplayName)
            }
        }
        
        // With email confirmation OFF in Supabase, this signs in immediately
        if (supabase.auth.currentSessionOrNull() == null) {
            val firstAttempt = runCatching {
                supabase.auth.signInWith(Email) {
                    this.email = virtualEmail
                    this.password = password
                }
            }
            if (firstAttempt.isFailure) {
                val firstError = firstAttempt.exceptionOrNull()
                // A transient failure right after signup leaves the account created but
                // no session: retry the sign-in once so a hiccup doesn't strand the user.
                if (firstError != null && !isInvalidCredentials(firstError)) {
                    val retry = loginWithUsername(username, password)
                    if (retry.isFailure) {
                        val retryError = retry.exceptionOrNull()
                        if (retryError != null && isInvalidCredentials(retryError)) {
                            error("Your account was created, but the automatic sign-in failed. Please sign in with your username and password.")
                        }
                        throw retryError ?: firstError
                    }
                } else {
                    throw firstError ?: Exception("Sign-in failed after registration")
                }
            }
        }
    }

    suspend fun loginWithUsername(username: String, password: String): Result<Unit> = runCatching {
        val cleanUsername = username.trim().lowercase()
        val virtualEmail = "$cleanUsername@u.whisper.local"
        
        supabase.auth.signInWith(Email) {
            this.email = virtualEmail
            this.password = password
        }
    }

    suspend fun refreshUser(): Result<Unit> = runCatching {
        supabase.auth.retrieveUserForCurrentSession(updateSession = true)
    }

    // Removed email verification and password reset as they require real emails

    fun generateAnonToken(): WhisperAnonToken {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val token = bytes.toHexString()
        // P1-15 FIX: Use full 64-char SHA256 hex (256-bit) for new accounts; 32 was 128-bit truncation.
        // Login retains fallback to 32 for pre-fix accounts.
        return WhisperAnonToken(token = token, virtualEmail = sha256(token) + "@whisper.toolz.app")
    }

    suspend fun registerWithToken(anonToken: WhisperAnonToken, username: String, displayName: String): Result<Unit> = runCatching {
        val cleanToken = normalizeToken(anonToken.token)
        require(isValidToken(cleanToken)) { "Token must be a valid 64-character hex string." }
        val virtualEmail = sha256(cleanToken) + "@whisper.toolz.app"
        val virtualPassword = sha256("pwd_" + cleanToken)
        val cleanUsername = username.trim().lowercase()
        val cleanDisplayName = displayName.trim()
        supabase.auth.signUpWith(Email) {
            this.email = virtualEmail
            this.password = virtualPassword
            this.data = buildJsonObject {
                put("username", cleanUsername)
                put("display_name", cleanDisplayName)
            }
        }
        // Ensure session is started immediately
        if (supabase.auth.currentSessionOrNull() == null) {
            supabase.auth.signInWith(Email) {
                this.email = virtualEmail
                this.password = virtualPassword
            }
        }
    }

    suspend fun loginWithToken(rawToken: String): Result<Unit> = runCatching {
        val cleanToken = normalizeToken(rawToken)
        require(isValidToken(cleanToken)) { "That token doesn't look right. Check for missing or extra characters." }
        // P0-NOTE FIX (reviewwhisper.md): the old 2-emails × 5-passwords nested loop
        // could fire up to TEN sequential GoTrue password grants per login, tripping
        // Supabase's per-identity rate limits and locking legacy users out for an hour.
        // Replaced with the four (email, password) combinations that actually existed
        // historically, most-likely-first, with a hard cap of 4 network attempts.
        val fullHash = sha256(cleanToken)
        val candidates: List<Pair<String, String>> = listOf(
            // Current scheme (P1-15, 2026+): full 256-bit hash email.
            fullHash + "@whisper.toolz.app" to sha256("pwd_" + cleanToken),
            // Earliest era: truncated 128-bit email + SHA-512-truncated password.
            fullHash.take(32) + "@whisper.toolz.app" to sha512(cleanToken).take(72),
            fullHash.take(32) + "@whisper.toolz.app" to sha512(cleanToken),
            fullHash.take(32) + "@whisper.toolz.app" to sha256(cleanToken).take(32),
        ).distinctBy { it.first + it.second }

        var lastException: Throwable? = null
        for ((virtualEmail, virtualPassword) in candidates) {
            val attempt = runCatching {
                supabase.auth.signInWith(Email) {
                    this.email = virtualEmail
                    this.password = virtualPassword
                }
            }
            if (attempt.isSuccess) {
                return@runCatching
            } else {
                val ex = attempt.exceptionOrNull()
                lastException = ex
                if (ex != null && !isInvalidCredentials(ex)) {
                    throw ex
                }
                // Slow down brute-force attempts over the derivation candidates.
                delay(TOKEN_ATTEMPT_DELAY_MS)
            }
        }
        throw lastException ?: Exception("Invalid login credentials")
    }

    fun normalizeToken(raw: String): String = raw.trim().replace(Regex("[^0-9a-fA-F]"), "").lowercase()
    fun isValidToken(token: String): Boolean = token.length == 64 && token.all { it in '0'..'9' || it in 'a'..'f' }

    fun isInvalidCredentials(throwable: Throwable): Boolean =
        (throwable is RestException && throwable.error == "invalid_grant") ||
        throwable.message?.contains("Invalid login credentials", ignoreCase = true) == true

    suspend fun signOut(): Result<Unit> = runCatching {
        supabase.auth.signOut()
    }

    private fun sha256(input: String): String = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).toHexString()
    private fun sha512(input: String): String = MessageDigest.getInstance("SHA-512").digest(input.toByteArray(Charsets.UTF_8)).toHexString()
    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 10
        const val TOKEN_ATTEMPT_DELAY_MS = 500L
        val USERNAME_PATTERN = Regex("^[a-z0-9](?:[a-z0-9_]{1,18}[a-z0-9])?$")
    }
}
