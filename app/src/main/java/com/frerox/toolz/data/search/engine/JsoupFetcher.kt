/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsoupFetcher @Inject constructor(
    private val client: OkHttpClient
) {
    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    )
    private fun randomUA() = userAgents.random()

    fun buildRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", randomUA())
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("Cookie", "SOCS=CAESHAgBEhJnd3NfMjAyNDA2MTAtMF9SQzEaAmVuIAEaBgiAo_uwBg; CONSENT=PENDING+999")
        .header("Referer", if (url.contains("duckduckgo")) "https://html.duckduckgo.com/" else "https://www.google.com/")
        .header("Sec-Ch-Ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"126\"")
        .header("Sec-Fetch-Dest", "document")
        .header("Upgrade-Insecure-Requests", "1")
        .build()

    suspend fun fetch(url: String, timeoutMs: Long = 8000): Document? {
        repeat(3) { attempt ->
            try {
                val req = buildRequest(url)
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    if (resp.code in listOf(429, 403)) return null
                    return null
                }
                val html = resp.body?.string() ?: return null
                if (isCaptcha(html)) return null
                return Jsoup.parse(html, url)
            } catch (e: Exception) {
                if (attempt < 2) {
                    delay(400L * (1L shl attempt))
                }
            }
        }
        return null
    }

    fun isCaptcha(html: String): Boolean {
        val l = html.lowercase()
        return l.contains("captcha") || l.contains("detected suspicious activity") || l.contains("unusual traffic") ||
                l.contains("anomaly-modal") || l.contains("challenge-form") || l.contains("anomaly.js") ||
                l.contains("cf-browser-verification") || l.contains("press and hold")
    }

    fun extractDateFromSnippet(snippet: String): Pair<String?, String> {
        val patterns = listOf(
            Regex("""^([A-Z][a-z]{2} \d{1,2}, \d{4})\s*[–—·]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""^(\d{1,2} [A-Z][a-z]{2} \d{4})\s*[-–—·]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""^(\d+ (?:day|hour|week|month|year)s? ago)\s*[·•]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""^(\d{4}-\d{2}-\d{2})\s*[–—·]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
        )
        for (p in patterns) {
            val m = p.find(snippet.trim()) ?: continue
            return m.groupValues[1].trim() to m.groupValues[2].trim()
        }
        return null to snippet
    }

    fun safeHost(url: String): String = try { java.net.URI(url).host?.removePrefix("www.") ?: url } catch (_: Exception) { url }
    fun decodeUrl(s: String): String = try { java.net.URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }
    fun cleanDuckDuckGoUrl(raw: String): String = try {
        when {
            raw.startsWith("//duckduckgo.com/l/?uddg=") -> java.net.URLDecoder.decode(raw.substringAfter("uddg=").substringBefore("&"), "UTF-8")
            raw.startsWith("/l/?uddg=") -> java.net.URLDecoder.decode(raw.substringAfter("uddg=").substringBefore("&"), "UTF-8")
            else -> raw
        }
    } catch (_: Exception) { raw }
}
