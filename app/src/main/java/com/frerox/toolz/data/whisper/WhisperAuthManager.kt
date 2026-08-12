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
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles authentication for Whisper via two flows:
 *
 * 1. **Email flow** — Standard email + password via Supabase Auth.
 *    Email confirmation is disabled on the Supabase dashboard.
 *
 * 2. **Anonymous/zero-knowledge token flow** — A 64-char cryptographically
 *    random hex token is generated on-device. It is NEVER sent to the server raw.
 *    Instead:
 *      - `SHA-256(token)` → used as the virtual email: `<hash>@anon.toolz`
 *      - `SHA-512(token)` → used as the virtual password
 *    The token is device-independent: users can copy it to log in on other devices.
 */
@Singleton
class WhisperAuthManager @Inject constructor(
    private val supabase: SupabaseClient,
) {
    // ─────────────────────────────────────────────────────────
    // Session state
    // ─────────────────────────────────────────────────────────

    /** True when a valid session exists. */
    val isAuthenticated: Flow<Boolean>
        get() = supabase.auth.sessionStatus.map { it is SessionStatus.Authenticated }

    /** The authenticated user's UUID, or null if not signed in. */
    val currentUserId: String?
        get() = supabase.auth.currentUserOrNull()?.id

    // ─────────────────────────────────────────────────────────
    // Standard Email Auth
    // ─────────────────────────────────────────────────────────

    /**
     * Register a new account with email and password.
     * Supabase email confirmation must be DISABLED for this to work immediately.
     */
    suspend fun registerWithEmail(email: String, password: String): Result<Unit> =
        runCatching {
            supabase.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        }

    /**
     * Sign in with an existing email and password.
     */
    suspend fun loginWithEmail(email: String, password: String): Result<Unit> =
        runCatching {
            supabase.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
        }

    // ─────────────────────────────────────────────────────────
    // Anonymous Token Auth
    // ─────────────────────────────────────────────────────────

    /**
     * Generate a new 64-character cryptographically secure random token.
     * The user MUST save this — it is the only way to recover their account.
     */
    fun generateAnonToken(): WhisperAnonToken {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val token = bytes.toHexString()
        return WhisperAnonToken(
            token = token,
            virtualEmail = sha256(token) + "@anon.toolz",
        )
    }

    /**
     * Register a new anonymous account from an [anonToken].
     * Uses SHA-256 of the token as the virtual email and SHA-512 as the password.
     */
    suspend fun registerWithToken(anonToken: WhisperAnonToken): Result<Unit> =
        runCatching {
            supabase.auth.signUpWith(Email) {
                this.email = anonToken.virtualEmail
                this.password = sha512(anonToken.token)
            }
        }

    /**
     * Sign in using a raw 64-char token string.
     * Derives the virtual email and password on-device — the raw token is never transmitted.
     */
    suspend fun loginWithToken(rawToken: String): Result<Unit> = runCatching {
        val trimmed = rawToken.trim()
        require(trimmed.length == 64) { "Token must be exactly 64 characters." }
        supabase.auth.signInWith(Email) {
            this.email = sha256(trimmed) + "@anon.toolz"
            this.password = sha512(trimmed)
        }
    }

    // ─────────────────────────────────────────────────────────
    // Sign out
    // ─────────────────────────────────────────────────────────

    suspend fun signOut(): Result<Unit> = runCatching {
        supabase.auth.signOut()
    }

    // ─────────────────────────────────────────────────────────
    // Crypto helpers (private)
    // ─────────────────────────────────────────────────────────

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .toHexString()

    private fun sha512(input: String): String =
        MessageDigest.getInstance("SHA-512")
            .digest(input.toByteArray(Charsets.UTF_8))
            .toHexString()

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }
}
