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
import com.frerox.toolz.R
import io.github.jan.supabase.exceptions.RestException

/**
 * Central error mapping for Whisper — converts raw throwables into user-friendly [UiText] messages
 * while always logging the full technical detail to Logcat.
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

    /** True when the resource genuinely does not exist (as opposed to a network failure). */
    fun isNotFound(throwable: Throwable): Boolean =
        (throwable is RestException && throwable.statusCode == 404) ||
            throwable.message?.contains("404", ignoreCase = true) == true ||
            throwable.message?.contains("No rows found", ignoreCase = true) == true

    /**
     * Maps a [Throwable] to a short, user-friendly [UiText].
     * Always logs the technical message to Logcat first.
     */
    fun map(throwable: Throwable, context: String = ""): UiText {
        val prefix = if (context.isNotBlank()) "[$context] " else ""
        Log.e(TAG, "$prefix${throwable.javaClass.simpleName}: ${throwable.message}", throwable)

        if (isSessionExpired(throwable)) {
            return UiText.DynamicString(SESSION_EXPIRED_SENTINEL)
        }

        val msg = throwable.message ?: return UiText.StringResource(R.string.st_Whisper_Error_Generic)

        return when {
            // Auth errors
            isInvalidCredentials(throwable) ->
                UiText.StringResource(R.string.st_Whisper_Error_InvalidCredentials)
            msg.contains("User already registered", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_UsernameExists)
            msg.contains("Email not confirmed", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_EmailNotConfirmed)
            msg.contains("signup_disabled", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_SignupDisabled)
            msg.contains("over_email_send_rate_limit", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_RateLimit)

            // Token errors
            msg.contains("Token must be", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_InvalidTokenFormat)
            msg.contains("doesn't look right", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_InvalidToken)

            // Network & offline errors
            msg.contains("offline", ignoreCase = true) ||
            msg.contains("Unable to resolve host", ignoreCase = true) ||
            msg.contains("ConnectException", ignoreCase = true) ||
            msg.contains("SocketTimeoutException", ignoreCase = true) ||
            msg.contains("NoRouteToHostException", ignoreCase = true) ||
            msg.contains("SSLHandshakeException", ignoreCase = true) ||
            msg.contains("HttpTimeout", ignoreCase = true) ||
            msg.contains("network", ignoreCase = true) ||
            msg.contains("connect", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("unreachable", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_Offline)

            // Permission / database errors
            msg.contains("blocked", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_Blocked)
            msg.contains("duplicate", ignoreCase = true) || msg.contains("unique", ignoreCase = true) || msg.contains("23505") ->
                UiText.StringResource(R.string.st_Whisper_Error_AlreadyConnected)
            msg.contains("row-level security", ignoreCase = true) || msg.contains("42501") ->
                UiText.StringResource(R.string.st_Whisper_Error_NotPermitted)
            throwable is RestException && throwable.statusCode == 404 ->
                UiText.StringResource(R.string.st_Whisper_Error_NotFound)
            throwable is RestException && throwable.statusCode == 409 ->
                UiText.StringResource(R.string.st_Whisper_Error_AlreadyUpToDate)
            throwable is RestException && throwable.statusCode in 400..499 ->
                UiText.StringResource(R.string.st_Whisper_Error_RequestFailed)
            throwable is RestException && throwable.statusCode in 500..599 ->
                UiText.StringResource(R.string.st_Whisper_Error_ServerBusy)

            // Decryption sentinel
            msg.contains("Decryption failed", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_DecryptionFailed)

            else -> UiText.StringResource(R.string.st_Whisper_Error_RequestFailed)
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
