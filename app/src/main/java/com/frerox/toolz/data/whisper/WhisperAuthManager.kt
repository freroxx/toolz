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
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
        get() = supabase.auth.currentUserOrNull() != null

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
        
        // ... (rest of deletion logic remains)

        // Delete user's profile and friends entries
        try {
            supabase.postgrest.from("profiles").delete { filter { eq("id", user.id) } }
        } catch (_: Exception) {}

        try {
            supabase.postgrest.from("friends").delete {
                filter { eq("user_a", user.id) }
            }
        } catch (_: Exception) {}

        try {
            supabase.postgrest.from("friends").delete {
                filter { eq("user_b", user.id) }
            }
        } catch (_: Exception) {}

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
            supabase.auth.signInWith(Email) {
                this.email = virtualEmail
                this.password = password
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
        return WhisperAnonToken(token = token, virtualEmail = sha256(token).take(32) + "@whisper.toolz.app")
    }

    suspend fun registerWithToken(anonToken: WhisperAnonToken, username: String, displayName: String): Result<Unit> = runCatching {
        val cleanToken = normalizeToken(anonToken.token)
        require(isValidToken(cleanToken)) { "Token must be a valid 64-character hex string." }
        val virtualEmail = sha256(cleanToken).take(32) + "@whisper.toolz.app"
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
        val virtualEmail = sha256(cleanToken).take(32) + "@whisper.toolz.app"

        val candidatePasswords = listOf(
            sha256("pwd_" + cleanToken),
            sha512(cleanToken).take(72),
            sha256(cleanToken),
            sha512(cleanToken),
            sha256(cleanToken).take(32) // Fallback for shortened hash if used as pwd before
        )

        var lastException: Throwable? = null
        for (candidatePwd in candidatePasswords) {
            val attempt = runCatching {
                supabase.auth.signInWith(Email) {
                    this.email = virtualEmail
                    this.password = candidatePwd
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
        val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        val USERNAME_PATTERN = Regex("^[a-z0-9](?:[a-z0-9_]{1,18}[a-z0-9])?$")
    }
}
