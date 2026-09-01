/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine.parser

import com.frerox.toolz.data.browser.AdBlockList
import com.frerox.toolz.data.search.SearchCategory
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.data.search.engine.EngineId
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for DuckDuckGo's HTML endpoint (html.duckduckgo.com).
 *
 * Live-verified behaviour (2026): GET requests carrying a query string are served
 * an "anomaly" bot-challenge interstitial (HTTP 202, zero results) — but POST form
 * requests to the same endpoint return full server-rendered results. Requests are
 * therefore POSTed; the anomaly page itself is detected downstream as a bot
 * challenge (`SearchHttpClient` marker), putting the engine into cooldown so the
 * repository's fallback rotation takes over.
 */
@Singleton
class DuckDuckGoParser @Inject constructor() : EngineParser {
    override val id = EngineId.DUCKDUCKGO

    override fun buildRequestUrls(query: String, offset: Int, category: SearchCategory, safeSearch: Boolean): List<String> =
        listOf("https://html.duckduckgo.com/html/")

    override fun buildRequestFormFields(query: String, offset: Int, category: SearchCategory, safeSearch: Boolean): Map<String, String> {
        val fields = mutableMapOf(
            "q" to query,
            // kp=-1 off, kp=1 strict
            "kp" to if (safeSearch) "1" else "-1",
        )
        // Offset pagination via the `s` ("results to skip") parameter.
        if (offset > 0) fields["s"] = offset.toString()
        when (category) {
            SearchCategory.IMAGES -> { fields["iax"] = "images"; fields["ia"] = "images" }
            SearchCategory.VIDEOS -> { fields["iax"] = "videos"; fields["ia"] = "videos" }
            SearchCategory.NEWS -> { fields["iar"] = "news"; fields["ia"] = "news" }
            SearchCategory.ALL -> fields["ia"] = "web"
        }
        return fields
    }

    override fun parseHtml(doc: Document, category: SearchCategory, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        // Web results: div.result / div.web-result with .result__a title link
        doc.select("div.result, div.web-result").forEachIndexed { rank, el ->
            val titleEl = el.select("a.result__a, h2 a").firstOrNull() ?: return@forEachIndexed
            val rawUrl = titleEl.attr("href")
            // DDG wraps outbound links in //duckduckgo.com/l/?uddg=<encoded>&rut=...
            val cleanUrl = unwrapDdgRedirect(rawUrl) ?: return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val title = titleEl.text().trim().ifBlank { return@forEachIndexed }
            val snippet = el.select(".result__snippet, .result-snippet").firstOrNull()?.text()?.trim() ?: ""
            results += SearchResult(
                title = title,
                snippet = snippet,
                url = cleanUrl,
                displayUrl = ParserSupport.safeHost(cleanUrl),
                source = id.label,
                engineRank = rank,
            )
        }

        // Media results (images/videos come back as tile grids on the same endpoint)
        if (results.isEmpty() && (category == SearchCategory.IMAGES || category == SearchCategory.VIDEOS)) {
            doc.select(".tile--img, .tile--vid, .result--media, .tile").forEachIndexed { rank, el ->
                val imgEl = el.select("img").firstOrNull()
                val linkEl = el.select("a[href]").firstOrNull() ?: return@forEachIndexed
                val cleanUrl = unwrapDdgRedirect(linkEl.attr("href")) ?: return@forEachIndexed
                if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
                val imgSrc = imgEl?.attr("src")?.takeIf { it.startsWith("http") }
                    ?: imgEl?.attr("data-src")?.takeIf { it.startsWith("http") }
                results += SearchResult(
                    title = imgEl?.attr("alt")?.trim().takeUnless { it.isNullOrBlank() } ?: "Media",
                    snippet = "",
                    url = cleanUrl,
                    displayUrl = ParserSupport.safeHost(cleanUrl),
                    source = id.label,
                    imageUrl = imgSrc,
                    engineRank = rank,
                )
            }
        }
        return results
    }

    /** Resolves DDG's `//duckduckgo.com/l/?uddg=<encoded>` link wrapper to the real target. */
    private fun unwrapDdgRedirect(rawHref: String): String? = when {
        rawHref.contains("uddg=") -> {
            val encoded = rawHref.substringAfter("uddg=").substringBefore("&")
            val decoded = ParserSupport.decodeUrl(encoded)
            decoded.takeIf { it.startsWith("http") }
        }
        rawHref.startsWith("http") -> rawHref
        rawHref.startsWith("//") -> "https:$rawHref"
        else -> null
    }
}
