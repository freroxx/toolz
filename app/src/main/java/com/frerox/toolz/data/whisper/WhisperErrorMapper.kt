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

import android.util.Log
import io.github.jan.supabase.exceptions.RestException

/**
 * Central error mapping for Whisper — converts raw throwables into user-friendly,
 * privacy-safe messages while always logging the full technical detail to Logcat.
 */
object WhisperErrorMapper {

    private const val TAG = "WhisperError"

    const val SESSION_EXPIRED_SENTINEL = "%%SESSION_EXPIRED%%"

    fun isSessionExpired(throwable: Throwable): Boolean {
        if (throwable is RestException && (throwable.statusCode in 401..403 || throwable.error == "invalid_jwt" || throwable.error == "JWT expired")) {
            return true
        }
        val msg = throwable.message.orEmpty()
        return msg.contains("JWT expired", ignoreCase = true) ||
            msg.contains("invalid claim", ignoreCase = true) ||
            msg.contains("session expired", ignoreCase = true) ||
            msg.contains("user_not_found", ignoreCase = true)
    }

    /**
     * Maps a [Throwable] to a short, user-friendly string.
     * Always logs the technical message to Logcat first.
     */
    fun map(throwable: Throwable, context: String = ""): String {
        val prefix = if (context.isNotBlank()) "[$context] " else ""
        Log.e(TAG, "$prefix${throwable.javaClass.simpleName}: ${throwable.message}", throwable)

        if (isSessionExpired(throwable)) {
            return SESSION_EXPIRED_SENTINEL
        }

        val msg = throwable.message ?: return "Something went wrong. Try again."

        return when {
            // Auth errors
            isInvalidCredentials(throwable) ->
                "Incorrect credentials. Double-check your details."
            msg.contains("User already registered", ignoreCase = true) ->
                "An account with these details already exists."
            msg.contains("Email not confirmed", ignoreCase = true) ->
                "Please confirm your email before signing in."
            msg.contains("signup_disabled", ignoreCase = true) ->
                "Sign-up is temporarily unavailable. Try again later."
            msg.contains("over_email_send_rate_limit", ignoreCase = true) ->
                "Too many attempts. Wait a moment before trying again."

            // Token errors
            msg.contains("Token must be", ignoreCase = true) ->
                "That token doesn't look right — it should be a 64-character code."
            msg.contains("doesn't look right", ignoreCase = true) ->
                "Token format is invalid. Check for missing or extra characters."

            // Network errors
            msg.contains("network", ignoreCase = true) ||
            msg.contains("connect", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("unreachable", ignoreCase = true) ->
                "Can't reach Whisper servers. Check your connection."

            // Permission / database errors
            throwable is RestException && throwable.statusCode in 400..499 ->
                "Request failed. Please try again."
            throwable is RestException && throwable.statusCode in 500..599 ->
                "Server error. Please try again in a moment."

            // Duplicate / constraint
            msg.contains("duplicate", ignoreCase = true) ||
            msg.contains("unique", ignoreCase = true) ->
                "This already exists. Please choose something different."

            // Decryption sentinel
            msg.contains("Decryption failed", ignoreCase = true) ->
                "⚠️ Could not decrypt this message."

            else -> "Something went wrong. Please try again."
        }
    }

    /** Logs an error without mapping it (for non-user-facing errors). */
    fun log(throwable: Throwable, context: String = "") {
        val prefix = if (context.isNotBlank()) "[$context] " else ""
        Log.e(TAG, "$prefix${throwable.javaClass.simpleName}: ${throwable.message}", throwable)
    }

    private fun isInvalidCredentials(throwable: Throwable): Boolean =
        (throwable is RestException && throwable.error == "invalid_grant") ||
        throwable.message?.contains("Invalid login credentials", ignoreCase = true) == true
}
