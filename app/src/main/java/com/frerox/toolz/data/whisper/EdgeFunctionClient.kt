/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import com.frerox.toolz.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P2: ONE hardened transport for Supabase Edge Function calls.
 *
 * Replaces four hand-rolled [java.net.HttpURLConnection] sites (bundle fetch,
 * account deletion, encrypted-image upload/delete) that each re-implemented
 * timeouts, header wiring and error-body handling. Centralizing them means:
 *  - timeouts are mandatory defaults, not per-site afterthoughts;
 *  - release-log hygiene (no bodies logged) lives in exactly one place;
 *  - call sites shrink to intent ("POST this payload to that function").
 *
 * Wire behavior stays compatible with the old sites: same URLs, same headers,
 * same status-code semantics. Callers keep constructing their user-facing errors
 * from [Response] so no exception text changes shape.
 */
@Singleton
class EdgeFunctionClient @Inject constructor(
    private val supabase: SupabaseClient,
) {
    enum class AuthMode {
        /** Anonymous gate only (Authorization: Bearer <anon key>) — bundle fetch. */
        ANON,

        /** Authenticated caller — Authorization: Bearer <supabase access token>. */
        USER,

        /** Explicit token override (image-host 401-refresh retry path). */
        TOKEN,
    }

    data class Request(
        val function: String,
        val jsonBody: String,
        val authMode: AuthMode,
        /** Token for [AuthMode.TOKEN]; ignored otherwise. */
        val bearerToken: String? = null,
        val extraHeaders: Map<String, String> = emptyMap(),
        val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    )

    data class Response(
        val code: Int,
        val body: String,
    ) {
        val is2xx: Boolean get() = code in 200..299

        /**
         * Mirrors the historical error extraction: prefer a structured
         * {"error":"…"} field, else the first 200 chars of the raw body.
         */
        fun errorText(): String = runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
        }.getOrNull() ?: body.take(200)
    }

    private val http by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout)
            engine {
                config {
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

    /**
     * Executes the edge-function POST. Network I/O is confined to IO. Never logs
     * request/response bodies (release hygiene inherited from the old sites).
     */
    suspend fun execute(request: Request): Response = withContext(Dispatchers.IO) {
        val url = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/${request.function}"
        val bearer = when (request.authMode) {
            AuthMode.ANON -> BuildConfig.SUPABASE_ANON_KEY
            AuthMode.USER -> supabase.auth.currentSessionOrNull()?.accessToken.orEmpty()
            AuthMode.TOKEN -> request.bearerToken.orEmpty()
        }
        val httpResponse: HttpResponse = http.post(url) {
            header("Authorization", "Bearer $bearer")
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            contentType(ContentType.Application.Json)
            request.extraHeaders.forEach { (k, v) -> header(k, v) }
            setBody(request.jsonBody)
            timeout {
                connectTimeoutMillis = request.connectTimeoutMs.toLong()
                socketTimeoutMillis = request.readTimeoutMs.toLong()
                requestTimeoutMillis = request.readTimeoutMs.toLong() + 5_000L
            }
        }
        Response(code = httpResponse.status.value, body = httpResponse.bodyAsText())
    }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000
        const val DEFAULT_READ_TIMEOUT_MS = 30_000
    }
}
