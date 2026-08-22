/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.whisper

import com.frerox.toolz.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Screenshot-bypass verification — SERVER-SIDE via the `whisper-bypass-verify`
 * Edge Function. The password is never stored on-device: it lives as an Edge
 * Function secret (`WHISPER_BYPASS_PASSWORD`), so a decompiled APK reveals nothing.
 *
 * Hardening:
 *  • Constant-time comparison happens server-side; the client only sees a verdict.
 *  • The function rate-limits guessing per user/IP (5 failures / 15 min → lockout).
 *  • Any network/server failure fails CLOSED: no verdict means no bypass.
 *  • Blank input short-circuits locally so a mistimed tap never hits the network.
 *
 * FIX (user report: "correct password shows Invalid credentials"): the old Boolean API
 * collapsed FOUR distinct outcomes into `false` — wrong password, 429 lockout,
 * unconfigured secret, missing RPC — making field diagnosis impossible. [verifyWhisperBypass]
 * now surfaces the actual outcome so the UI can show an actionable message.
 */
sealed interface WhisperBypassVerdict {
    /** Password accepted — safe to toggle FLAG_SECURE. */
    data object Granted : WhisperBypassVerdict

    /** Password rejected server-side. */
    data object Denied : WhisperBypassVerdict

    /** Identity locked out (>=5 failures / 15 min). Even the CORRECT password is denied mid-lockout. */
    data object RateLimited : WhisperBypassVerdict

    /** Service unreachable/misconfigured (secret unset, RPC missing, network down). Fails closed. */
    data object Unavailable : WhisperBypassVerdict
}

suspend fun verifyWhisperBypass(input: String): WhisperBypassVerdict {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.length > 256) return WhisperBypassVerdict.Denied

    return try {
        withContext(Dispatchers.IO) { verifyBypassRemotely(trimmed) } ?: WhisperBypassVerdict.Unavailable
    } catch (_: Exception) {
        // Fail closed — an offline attacker gets nothing.
        WhisperBypassVerdict.Unavailable
    }
}

/** Back-compat Boolean wrapper (unit tests + simple callers). */
suspend fun isWhisperBypassPassword(input: String): Boolean =
    verifyWhisperBypass(input) == WhisperBypassVerdict.Granted

/** Returns the verdict, or null when the service is unavailable (no verdict at all). */
private fun verifyBypassRemotely(password: String): WhisperBypassVerdict? {
    val body = JSONObject().put("password", password).toString().toByteArray(Charsets.UTF_8)
    val connection = URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/whisper-bypass-verify")
        .openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(body.size)
        connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body) }

        val responseText = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (connection.responseCode !in 200..299) {
            // Field-diagnosis aid: name the exact status/body behind a non-verdict.
            android.util.Log.w(
                "WhisperBypass",
                "bypass-verify HTTP ${connection.responseCode}: ${responseText.take(200)}",
            )
        }

        return when {
            connection.responseCode in 200..299 ->
                if (runCatching { JSONObject(responseText).optBoolean("ok", false) }.getOrDefault(false)) {
                    WhisperBypassVerdict.Granted
                } else {
                    WhisperBypassVerdict.Denied
                }
            // Wrong password AND lockout both deny; distinguish them so users stop
            // blaming the password during a lockout window.
            connection.responseCode == 429 -> WhisperBypassVerdict.RateLimited
            connection.responseCode >= 500 -> null // "no verdict" → fail closed
            else -> WhisperBypassVerdict.Unavailable
        }
    } finally {
        connection.disconnect()
    }
}
