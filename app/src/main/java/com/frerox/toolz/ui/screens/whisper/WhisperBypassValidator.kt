/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.whisper

import com.frerox.toolz.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 *  • Constant-time comparison happens server-side; the client only sees ok/deny.
 *  • The function rate-limits guessing per user/IP (5 failures / 15 min → lockout).
 *  • Any network/server failure fails CLOSED: no verdict means no bypass.
 *  • Blank input short-circuits locally so a mistimed tap never hits the network.
 */
suspend fun isWhisperBypassPassword(input: String): Boolean {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.length > 256) return false

    return try {
        withContext(Dispatchers.IO) { verifyBypassRemotely(trimmed) } ?: false
    } catch (_: Exception) {
        // Fail closed — an offline attacker gets nothing.
        false
    }
}

/** Returns true/false from the edge function, or null when the service is unavailable. */
private fun verifyBypassRemotely(password: String): Boolean? {
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

        return when (connection.responseCode) {
            in 200..299 -> {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                runCatching { JSONObject(response).optBoolean("ok", false) }.getOrDefault(false)
            }
            // Wrong password and lockout both deny; 5xx means "no verdict" → fail closed.
            else -> if (connection.responseCode >= 500) null else false
        }
    } finally {
        connection.disconnect()
    }
}
