/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
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
    val isAuthenticated: Flow<Boolean>
        get() = supabase.auth.sessionStatus.map { it is SessionStatus.Authenticated }
    val isInitializing: Flow<Boolean>
        get() = supabase.auth.sessionStatus.map { it is SessionStatus.Initializing }
    val currentUserId: String?
        get() = supabase.auth.currentUserOrNull()?.id
    val isReady: Boolean
        get() = supabase.auth.sessionStatus.value !is SessionStatus.Initializing

    suspend fun registerWithEmail(email: String, password: String, username: String, displayName: String): Result<Unit> = runCatching {
        val cleanEmail = email.trim()
        val cleanUsername = username.trim().lowercase()
        val cleanDisplayName = displayName.trim()
        supabase.auth.signUpWith(Email) {
            this.email = cleanEmail
            this.password = password
            this.data = buildJsonObject {
                put("username", cleanUsername)
                put("display_name", cleanDisplayName)
            }
        }
        if (supabase.auth.currentSessionOrNull() == null) {
            supabase.auth.signInWith(Email) {
                this.email = cleanEmail
                this.password = password
            }
        }
    }

    suspend fun loginWithEmail(email: String, password: String): Result<Unit> = runCatching {
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    fun generateAnonToken(): WhisperAnonToken {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val token = bytes.toHexString()
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
        val virtualEmail = sha256(cleanToken) + "@whisper.toolz.app"

        val candidatePasswords = listOf(
            sha256("pwd_" + cleanToken),
            sha512(cleanToken).take(72),
            sha256(cleanToken),
            sha512(cleanToken)
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
}