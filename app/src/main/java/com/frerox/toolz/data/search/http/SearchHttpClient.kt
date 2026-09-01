/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.http

import com.frerox.toolz.data.search.dns.DohClientFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of fetching a single URL from a search engine. */
sealed interface FetchResult {
    data class Success(val body: String) : FetchResult
    /** Engine returned a rate-limit / bot-block status (429/403) — caller should apply a cooldown. */
    data object RateLimited : FetchResult
    /** Body looked successful but matched a known bot-challenge page (Cloudflare, PerimeterX, etc). */
    data object BotChallenge : FetchResult
    data object Timeout : FetchResult
    data class Failure(val cause: Exception) : FetchResult
}

/**
 * Shared HTTP concerns for querying search engines: user-agent rotation (search
 * engines fingerprint and rate-limit by UA), realistic browser headers, retry
 * with exponential backoff for transient failures, and detection of bot-challenge
 * pages so callers can back off instead of mis-parsing a CAPTCHA page as "0 results".
 *
 * Engine-specific parsing lives in [com.frerox.toolz.data.search.engine.parser] —
 * this class only knows how to fetch bytes, not how to read them.
 */
@Singleton
class SearchHttpClient @Inject constructor(
    private val dohClientFactory: DohClientFactory,
) {
    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
    )

    private val botChallengeMarkers = listOf(
        "detected suspicious activity",
        "unusual traffic",
        "cf-browser-verification",
        "challenge-form",
        "captcha-delivery",
        // DuckDuckGo anomaly interstitial — served with HTTP 202 and no results.
        "anomaly.js",
        "anomaly-modal",
    )

    /**
     * Fetches [url] via the user's configured DNS client, with a hard [timeoutMs] budget
     * and up to [retries] attempts on transient (non-4xx) failures. Does not retry on
     * 429/403 or bot-challenge detection — those are terminal for this call; the caller
     * decides whether to cool the engine down and try elsewhere.
     */
    suspend fun fetch(
        url: String,
        timeoutMs: Long = 8_000,
        retries: Int = 2,
        initialBackoffMs: Long = 400L,
    ): FetchResult {
        var lastFailure: Exception? = null
        repeat(retries + 1) { attempt ->
            val outcome = attemptFetch(url, timeoutMs)
            when (outcome) {
                is FetchResult.Success, FetchResult.RateLimited, FetchResult.BotChallenge -> return outcome
                is FetchResult.Failure -> lastFailure = outcome.cause
                FetchResult.Timeout -> lastFailure = null
            }
            if (attempt < retries) delay(initialBackoffMs * (1L shl attempt))
        }
        return lastFailure?.let { FetchResult.Failure(it) } ?: FetchResult.Timeout
    }

    /**
     * Same contract as [fetch], but issues a POST with [formFields] as the request
     * body. Needed for engines (DuckDuckGo) that serve results on POST but show a
     * bot-challenge interstitial to GET requests carrying a query string.
     */
    suspend fun fetchPost(
        url: String,
        formFields: Map<String, String>,
        timeoutMs: Long = 8_000,
        retries: Int = 2,
        initialBackoffMs: Long = 400L,
    ): FetchResult {
        var lastFailure: Exception? = null
        repeat(retries + 1) { attempt ->
            val outcome = attemptFetch(url, timeoutMs, formFields)
            when (outcome) {
                is FetchResult.Success, FetchResult.RateLimited, FetchResult.BotChallenge -> return outcome
                is FetchResult.Failure -> lastFailure = outcome.cause
                FetchResult.Timeout -> lastFailure = null
            }
            if (attempt < retries) delay(initialBackoffMs * (1L shl attempt))
        }
        return lastFailure?.let { FetchResult.Failure(it) } ?: FetchResult.Timeout
    }

    private suspend fun attemptFetch(url: String, timeoutMs: Long, formFields: Map<String, String>? = null): FetchResult {
        val client = dohClientFactory.getClient()
        val formBody = formFields?.takeIf { it.isNotEmpty() }?.let { fields ->
            okhttp3.FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        }
        val request = buildRequest(url, formBody)
        val response = try {
            withTimeoutOrNull(timeoutMs) { client.newCall(request).execute() } ?: return FetchResult.Timeout
        } catch (e: Exception) {
            return FetchResult.Failure(e)
        }
        return response.use { classifyResponse(it) }
    }

    private fun classifyResponse(response: Response): FetchResult {
        if (!response.isSuccessful) {
            return if (response.code == 429 || response.code == 403) {
                FetchResult.RateLimited
            } else {
                FetchResult.Failure(java.io.IOException("HTTP ${response.code}"))
            }
        }
        val body = try {
            response.body?.string()
        } catch (e: Exception) {
            return FetchResult.Failure(e)
        }
        if (body.isNullOrBlank()) return FetchResult.Failure(java.io.IOException("Empty body"))
        if (botChallengeMarkers.any { body.contains(it, ignoreCase = true) }) return FetchResult.BotChallenge
        return FetchResult.Success(body)
    }

    fun buildRequest(url: String, formBody: okhttp3.RequestBody? = null): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgents.random())
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", "https://www.google.com/")
        if (formBody != null) builder.post(formBody)
        return builder.build()
    }

    /**
     * Dedicated client for large plain-text downloads (ad-block lists can run ~2MB).
     * Deliberately bypasses [DohClientFactory] — DoH resolution overhead isn't worth it
     * for a one-shot large download to a fixed, trusted host — and uses a much longer
     * read timeout than search queries need.
     */
    private val blocklistClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun fetchBlocklist(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (compatible; AdBlockFetcher/1.0)")
                .header("Accept", "text/plain, text/*, */*;q=0.8")
                .header("Cache-Control", "no-cache")
                .build()
            blocklistClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (e: Exception) {
            android.util.Log.e("SearchHttpClient", "fetchBlocklist failed for $url", e)
            null
        }
    }

    /** Fetches raw page content for content-extraction use cases (reader mode, etc). */
    suspend fun fetchRaw(url: String): String? {
        val result = fetch(url, retries = 1)
        return (result as? FetchResult.Success)?.body
    }
}
