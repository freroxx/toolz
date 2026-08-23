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
import com.frerox.toolz.BuildConfig
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
        // V2-FIX (reviewwhisper.md) H-7: only 401 means "session expired" — 402/403 are
        // distinct server outcomes (payment required / forbidden) and must not sign the
        // user out.
        if (throwable is RestException && (throwable.statusCode == 401 || throwable.error == "invalid_jwt" || throwable.error == "JWT expired")) {
            return true
        }
        val msg = throwable.message.orEmpty()
        return msg.contains("JWT expired", ignoreCase = true) ||
            msg.contains("invalid claim", ignoreCase = true) ||
            msg.contains("session expired", ignoreCase = true) ||
            // V2-FIX: user_not_found removed from expiry heuristics — it is a lookup
            // miss, not an expired session, and forced a needless sign-out.
            msg.contains("Unauthorized", ignoreCase = true)
    }

    /** True when the resource genuinely does not exist (as opposed to a network failure). */
    fun isNotFound(throwable: Throwable): Boolean =
        // V2-FIX (reviewwhisper.md): only a structured HTTP 404 counts. Loose substring
        // fallbacks ("404", "No rows found") misrouted unrelated failures; only an
        // anchored "HTTP 404" phrase is still accepted for non-RestException transports.
        (throwable is RestException && throwable.statusCode == 404) ||
            throwable.message?.contains("HTTP 404", ignoreCase = true) == true

    /** True when an insert was rejected because the row already exists (idempotent client UUID retry). */
    fun isDuplicateKey(throwable: Throwable): Boolean {
        if (throwable is RestException && throwable.statusCode == 409) return true
        val msg = throwable.message.orEmpty()
        return msg.contains("duplicate key", ignoreCase = true) ||
            msg.contains("23505", ignoreCase = true)
    }

    /** True when retrying cannot help: the request is rejected outright or the input is invalid. P2 FIX: 400-499 except 408/429 are permanent. */
    fun isPermanentError(throwable: Throwable): Boolean {
        if (throwable is IllegalArgumentException || throwable is IllegalStateException) return true
        if (throwable is RestException) {
            val code = throwable.statusCode
            // 408 timeout and 429 rate-limit are retryable; everything else 4xx permanent.
            if (code == 408 || code == 429) return false
            if (code in 400..499) return true
        }
        return false
    }

    /**
     * Maps a [Throwable] to a short, user-friendly [UiText].
     * Always logs the technical message to Logcat first.
     */
    fun map(throwable: Throwable, context: String = ""): UiText {
        // V2-FIX (reviewwhisper.md) L-13: cancellation is control flow, not an error —
        // rethrow immediately so structured concurrency is never broken by mapping.
        if (throwable is kotlinx.coroutines.CancellationException) throw throwable
        logTechnical(throwable, context)

        if (isSessionExpired(throwable)) {
            return UiText.DynamicString(SESSION_EXPIRED_SENTINEL)
        }

        val msg = throwable.message ?: return UiText.StringResource(R.string.st_Whisper_Error_Generic)

        // L-5 FIX (reviewwhisper.md): STRUCTURED checks (status codes, error codes) now run
        // BEFORE broad substring matching — the old order misrouted any server message
        // merely containing "blocked"/"connect"/"network" into unrelated buckets.
        // V2-FIX (reviewwhisper.md): the RestException status-code branches moved ABOVE the
        // English phrase-matching block so e.g. a 500 whose body mentions "blocked"
        // surfaces as ServerBusy, not Blocked.
        return when {
            // Structured HTTP status codes (RestException) before any keyword heuristics
            throwable is RestException && throwable.statusCode == 404 ->
                UiText.StringResource(R.string.st_Whisper_Error_NotFound)
            throwable is RestException && throwable.statusCode == 409 ->
                UiText.StringResource(R.string.st_Whisper_Error_AlreadyUpToDate)
            throwable is RestException && throwable.statusCode in 400..499 ->
                UiText.StringResource(R.string.st_Whisper_Error_RequestFailed)
            throwable is RestException && throwable.statusCode in 500..599 ->
                UiText.StringResource(R.string.st_Whisper_Error_ServerBusy)

            // Auth errors (specific phrases + error codes)
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
            // Discover rate-limit (whisper_discover_profiles RPC → P0002)
            msg.contains("rate_limited", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_RateLimit)

            // Token errors
            msg.contains("Token must be", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_InvalidTokenFormat)
            msg.contains("doesn't look right", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_InvalidToken)

            // Network & offline errors. V2-FIX (reviewwhisper.md): concrete transport
            // types are checked FIRST (ktor wraps java.net failures in IOException
            // subtypes); the message-substring scan remains only as last resort.
            throwable is java.net.UnknownHostException ||
                throwable is java.net.ConnectException ||
                throwable is java.net.SocketTimeoutException ||
                throwable is java.io.IOException ||
                msg.contains("offline", ignoreCase = true) ||
                msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("NoRouteToHostException", ignoreCase = true) ||
                msg.contains("SSLHandshakeException", ignoreCase = true) ||
                msg.contains("HttpTimeout", ignoreCase = true) ||
                msg.contains("unreachable", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) ||
                msg.contains("Failed to connect", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_Offline)

            // Permission / database errors — tightened phrases to avoid false positives
            // (L-5: a bare "blocked" substring used to swallow unrelated errors).
            msg.contains("row-level security", ignoreCase = true) || msg.contains("42501") ->
                UiText.StringResource(R.string.st_Whisper_Error_NotPermitted)
            msg.contains("You have blocked this user", ignoreCase = true) ||
                msg.contains("You have been blocked by this user", ignoreCase = true) ||
                msg.contains("blocked by this user", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_Blocked)
            msg.contains("duplicate key", ignoreCase = true) || msg.contains("23505") ->
                UiText.StringResource(R.string.st_Whisper_Error_AlreadyConnected)

            // Decryption sentinel
            msg.contains("Decryption failed", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_DecryptionFailed)

            else -> UiText.StringResource(R.string.st_Whisper_Error_RequestFailed)
        }
    }

    /** Logs an error without mapping it (for non-user-facing errors). */
    fun log(throwable: Throwable, context: String = "") {
        // V2-FIX (reviewwhisper.md) L-13: cancellation is control flow — never log, rethrow.
        if (throwable is kotlinx.coroutines.CancellationException) throw throwable
        logTechnical(throwable, context)
    }

    /**
     * V2-FIX (reviewwhisper.md): release-build log hygiene. Full throwable detail
     * (message + stack trace) only in debug builds; release logs just the exception
     * class name plus the caller's context prefix so no payload/URL detail leaks.
     */
    private fun logTechnical(throwable: Throwable, context: String) {
        val prefix = if (context.isNotBlank()) "[$context] " else ""
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "$prefix${throwable.javaClass.simpleName}: ${throwable.message}", throwable)
        } else {
            Log.e(TAG, "$prefix${throwable.javaClass.name}")
        }
    }

    private fun isInvalidCredentials(throwable: Throwable): Boolean =
        (throwable is RestException && throwable.error == "invalid_grant") ||
        throwable.message?.contains("Invalid login credentials", ignoreCase = true) == true
}
