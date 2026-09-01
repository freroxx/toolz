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
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.NoSessionFoundException
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
        // NoSession is expected on fresh install — not permanent, not an error to retry loudly
        if (throwable is NoSessionFoundException) return false
        if (throwable is IllegalArgumentException) return true
        if (throwable is IllegalStateException) {
            // "No session found in storage" is the normal logged-out signal — not a permanent failure
            val msg = throwable.message.orEmpty()
            if (msg.contains("No session", ignoreCase = true)) return false
            return true
        }
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

        // V6-R6: repository guards throw plain IllegalStateException("User not
        // authenticated") outside any RestException path — without this branch the
        // session-expired sentinel never fired and every send died as "request failed"
        // while the UI looked logged-in.
        if (msg.contains("User not authenticated", ignoreCase = true) ||
            msg.contains("not authenticated", ignoreCase = true)
        ) {
            return UiText.DynamicString(SESSION_EXPIRED_SENTINEL)
        }

        // L-5 FIX (reviewwhisper.md): STRUCTURED checks now correctly ordered —
        // AUTH-SPECIFIC error codes (invalid credentials, user exists, provider disabled etc.)
        // MUST outrank generic 400..499 bucket, otherwise every 400 AuthRestException becomes
        // generic RequestFailed (the bug that hid InvalidCredentials behind RequestFailed).
        // V2-FIX: RestException 404/409 + block/42501 still outrank generic, but auth codes are above generic 400.
        return when {
            // Structured 404/409 before anything
            throwable is RestException && throwable.statusCode == 404 ->
                UiText.StringResource(R.string.st_Whisper_Error_NotFound)
            throwable is RestException && throwable.statusCode == 409 ->
                UiText.StringResource(R.string.st_Whisper_Error_AlreadyUpToDate)
            // P0 Fix: block enforcement phrase must outrank generic 4xx/5xx
            msg.contains("You have blocked this user", ignoreCase = true) ||
                msg.contains("You have been blocked by this user", ignoreCase = true) ||
                msg.contains("blocked by this user", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_Blocked)
            // Permission must also outrank generic 4xx/5xx
            msg.contains("row-level security", ignoreCase = true) || msg.contains("42501") ->
                UiText.StringResource(R.string.st_Whisper_Error_NotPermitted)
            // ——— AUTH-SPECIFIC checks BEFORE generic 400/500 ———
            isInvalidCredentials(throwable) ->
                UiText.StringResource(R.string.st_Whisper_Error_InvalidCredentials)
            // User already exists: GoTrue returns 422 error_code user_already_exists / email_exists with phrase "User already registered"
            (throwable is RestException && (throwable.error == "user_already_exists" || throwable.error == "email_exists")) ||
                (throwable is AuthRestException && (throwable.errorCode == AuthErrorCode.UserAlreadyExists || throwable.errorCode == AuthErrorCode.EmailExists)) ||
                msg.contains("User already registered", ignoreCase = true) ||
                msg.contains("user_already_exists", ignoreCase = true) ||
                msg.contains("email_exists", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_UsernameExists)
            (throwable is RestException && throwable.error == "email_not_confirmed") ||
                (throwable is AuthRestException && throwable.errorCode == AuthErrorCode.EmailNotConfirmed) ||
                msg.contains("Email not confirmed", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_EmailNotConfirmed)
            (throwable is RestException && throwable.error in setOf("signup_disabled", "provider_disabled", "email_provider_disabled", "anonymous_provider_disabled")) ||
                (throwable is AuthRestException && throwable.errorCode in setOf(AuthErrorCode.SignupDisabled, AuthErrorCode.ProviderDisabled, AuthErrorCode.EmailProviderDisabled, AuthErrorCode.AnonymousProviderDisabled)) ||
                msg.contains("signup_disabled", ignoreCase = true) ||
                msg.contains("email_provider_disabled", ignoreCase = true) ||
                msg.contains("provider_disabled", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_SignupDisabled)
            // Weak password: sign-up rejects 422 weak_password
            (throwable is RestException && throwable.error == "weak_password") ||
                (throwable is AuthRestException && throwable.errorCode == AuthErrorCode.WeakPassword) ||
                msg.contains("weak_password", ignoreCase = true) ||
                msg.contains("Password should be", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_WeakPassword)
            (throwable is RestException && throwable.error in setOf("over_email_send_rate_limit", "over_request_rate_limit", "over_sms_send_rate_limit")) ||
                (throwable is AuthRestException && throwable.errorCode in setOf(AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverSmsSendRateLimit)) ||
                msg.contains("over_email_send_rate_limit", ignoreCase = true) ||
                msg.contains("over_request_rate_limit", ignoreCase = true) ||
                msg.contains("over_sms_send_rate_limit", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_RateLimit)
            // Discover rate-limit RPC → P0002 with phrase rate_limited
            msg.contains("rate_limited", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_RateLimit)
            // Captcha / hook failures that also surface as 400s
            (throwable is AuthRestException && throwable.errorCode == AuthErrorCode.CaptchaFailed) ||
                msg.contains("captcha_failed", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_RateLimit)
            // ——— GENERIC HTTP buckets AFTER auth-specific ———
            throwable is RestException && throwable.statusCode in 400..499 ->
                UiText.StringResource(R.string.st_Whisper_Error_RequestFailed)
            throwable is RestException && throwable.statusCode in 500..599 ->
                UiText.StringResource(R.string.st_Whisper_Error_ServerBusy)

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

            msg.contains("duplicate key", ignoreCase = true) || msg.contains("23505") ->
                UiText.StringResource(R.string.st_Whisper_Error_AlreadyConnected)

            // V6-R2 (review): sendMessage's key-change guard used to fall through to the
            // generic RequestFailed text — surface the actual reason instead.
            msg.contains("Safety number changed", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_KeyChanged)

            // AUBUP / Access File recovery and decryption errors
            msg.contains("Incorrect Whisper Code", ignoreCase = true) ||
                msg.contains("Invalid password or corrupted data", ignoreCase = true) ||
                msg.contains("corrupted access file", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_AubupIncorrectCode)

            msg.contains("not a valid Whisper Access File", ignoreCase = true) ||
                msg.contains("Malformed encrypted data", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_AubupInvalidFile)

            msg.contains("Whisper Code must be exactly 6 digits", ignoreCase = true) ||
                msg.contains("Whisper Codes do not match", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Aubup_CodeLengthError)

            msg.contains("No credentials found in Password Vault", ignoreCase = true) ||
                msg.contains("No credentials found for", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_AubupNoCredentials)

            msg.contains("Encryption failed with the provided Whisper Code", ignoreCase = true) ||
                msg.contains("Encryption failed", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_AubupEncryptionFailed)

            msg.contains("Access file exceeds", ignoreCase = true) ||
                msg.contains("Could not read the selected file", ignoreCase = true) ||
                msg.contains("max 1 MB", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_AccessFileUnreadable)

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
     * (message + stack trace) only in debug builds; release logs limited diagnosable
     * info (class + status + error code) so AuthRestException root cause is visible
     * without leaking payload/URL.
     */
    private fun logTechnical(throwable: Throwable, context: String) {
        val prefix = if (context.isNotBlank()) "[$context] " else ""
        if (throwable is NoSessionFoundException) {
            // Expected on fresh install — not an error, just debug
            if (BuildConfig.DEBUG) Log.d(TAG, "${prefix}NoSessionFound (expected logged-out)")
            return
        }
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "$prefix${throwable.javaClass.simpleName}: ${throwable.message}", throwable)
        } else {
            val code = (throwable as? RestException)?.statusCode?.toString() ?: "-"
            val err = (throwable as? RestException)?.error ?: throwable.message?.take(120) ?: "-"
            Log.e(TAG, "$prefix${throwable.javaClass.simpleName} code=$code err=$err")
        }
    }

    private fun isInvalidCredentials(throwable: Throwable): Boolean {
        if (throwable is AuthRestException) {
            if (throwable.errorCode == AuthErrorCode.InvalidCredentials) return true
            if (throwable.error == "invalid_grant" || throwable.error == "invalid_credentials") return true
        }
        if (throwable is RestException) {
            if (throwable.error == "invalid_grant" || throwable.error == "invalid_credentials") return true
        }
        return throwable.message?.contains("Invalid login credentials", ignoreCase = true) == true ||
            throwable.message?.contains("invalid_credentials", ignoreCase = true) == true
    }
}
