/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine.parser

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Small pure helpers shared by every [EngineParser] implementation — URL
 * decoding/unwrapping, host extraction, and splitting a leading date off a
 * search-result snippet. Kept dependency-free so parsers stay unit-testable
 * without Android or network mocks.
 */
object ParserSupport {

    fun decodeUrl(raw: String): String = try {
        URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
    } catch (_: Exception) {
        raw
    }

    /** Resolves HTML entities (&quot; &amp; &#39; etc.) so escaped JSON attributes become parseable. */
    fun unescapeHtml(raw: String): String = try {
        org.jsoup.parser.Parser.unescapeEntities(raw, false)
    } catch (_: Exception) {
        raw
    }

    /**
     * Extracts a string field from a flat JSON blob without org.json — the Android
     * org.json classes are stubs on the JVM unit-test path, so parsers stay
     * testable by using this instead. Handles escaped quotes inside values.
     */
    fun jsonStringField(json: String, key: String): String? {
        val match = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json) ?: return null
        return match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\/", "/")
            .replace("\\u002f", "/")
            .takeIf { it.isNotBlank() }
    }

    /**
     * Brave Search proxies thumbnails through imgs.search.brave.com, embedding the
     * original media URL as a base64 path segment starting with "aHR0" ("http").
     * The segment contains literal "/" line-splitting separators that must be
     * stripped before decoding. Returns null when the URL isn't a Brave thumb
     * (callers keep the raw src).
     */
    fun decodeBraveThumb(src: String): String? {
        val idx = src.indexOf("/aHR0")
        if (idx == -1) return null
        val b64 = ("aHR0" + src.substring(idx + 5)).replace("/", "").trimEnd('=')
        val padded = b64 + "=".repeat((4 - b64.length % 4) % 4)
        return try {
            val decoded = String(java.util.Base64.getDecoder().decode(padded), Charsets.UTF_8)
            decoded.takeIf { it.startsWith("http") && !it.contains("favicon") }
        } catch (_: Exception) {
            null
        }
    }

    fun safeHost(url: String): String = try {
        URI(url).host?.removePrefix("www.") ?: url
    } catch (_: Exception) {
        url
    }

    /**
     * Unwraps a redirect-tracking URL of the form `.../RU=<encoded-target>/RK=...`
     * (Yahoo's link-tracking format) to the real target URL. Returns [rawHref]
     * unchanged if it doesn't match that shape.
     */
    fun unwrapYahooRedirect(rawHref: String): String {
        if (!rawHref.contains("RU=")) return rawHref
        return try {
            val encoded = rawHref.substringAfter("RU=").substringBefore("/RK=").substringBefore("&")
            URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            rawHref
        }
    }

    /**
     * Splits a leading date/relative-time prefix off a result snippet, e.g.
     * `"Jun 12, 2025 — Some article text"` → `("Jun 12, 2025", "Some article text")`.
     * Returns `(null, snippet)` unchanged if no recognized date prefix is present.
     */
    fun extractLeadingDate(snippet: String): Pair<String?, String> {
        for (pattern in DATE_PREFIX_PATTERNS) {
            val match = pattern.find(snippet.trim()) ?: continue
            return match.groupValues[1].trim() to match.groupValues[2].trim()
        }
        return null to snippet
    }

    private val DATE_PREFIX_PATTERNS = listOf(
        Regex("""^([A-Z][a-z]{2} \d{1,2}, \d{4})\s*[–—·]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
        Regex("""^(\d{1,2} [A-Z][a-z]{2} \d{4})\s*[-–—·]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
        Regex("""^(\d+ (?:day|hour|week|month|year)s? ago)\s*[·•]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
        Regex("""^(\d{4}-\d{2}-\d{2})\s*[–—·]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
    )
}
