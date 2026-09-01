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
 * Lenient anchor-scraping fallback used when an engine's dedicated parser
 * returns zero results — usually because that engine changed its markup and
 * the specific CSS selectors no longer match. Rather than surfacing "0 results"
 * (which makes the engine look dead), this scans every plausible-looking outbound
 * link and its nearby text as a best-effort substitute.
 *
 * Only applies to web (ALL) results — image/video/news markup varies too much
 * for a generic anchor scan to produce anything meaningful.
 */
@Singleton
class GenericFallbackParser @Inject constructor() {

    private val ownSearchUrlMarkers = listOf(
        "yahoo.com/search", "bing.com/search", "qwant.com", "marginalia.nu",
    )

    fun parse(doc: Document, engine: EngineId, category: SearchCategory, adBlockEnabled: Boolean): List<SearchResult> {
        if (category != SearchCategory.ALL) return emptyList()

        val results = mutableListOf<SearchResult>()
        for (anchor in doc.select("a[href^=http]")) {
            if (results.size >= 15) break

            val href = anchor.attr("href").trim()
            if (href.isBlank() || ownSearchUrlMarkers.any { href.contains(it) }) continue
            // Yahoo sponsored-click redirect paths — never organic results.
            if (href.contains("/rdclks/") || href.contains("/secclk/")) continue
            if (adBlockEnabled && AdBlockList.isBlocked(href)) continue

            // Skip anchors inside sponsored containers (div.dd.ads on Yahoo).
            if (anchor.parents().any { it.hasClass("ads") }) continue

            val title = anchor.text().trim().ifBlank { anchor.attr("title").trim() }
            if (title.length !in 8..200) continue

            val parentBlock = anchor.parents().firstOrNull { it.select("p, div, span").isNotEmpty() } ?: anchor.parent()
            val snippet = parentBlock?.select("p, .snippet, .desc, .compText")?.firstOrNull()?.text()?.trim() ?: ""
            // Once we already have a handful of results, prefer entries that actually
            // have a snippet — early candidates with no snippet are more likely noise
            // (nav links, footer links) than genuine results.
            if (snippet.length < 10 && results.size > 5) continue

            val (date, cleanSnippet) = ParserSupport.extractLeadingDate(snippet)
            results += SearchResult(
                title = title, snippet = cleanSnippet, url = href,
                displayUrl = ParserSupport.safeHost(href), source = engine.label,
                date = date, engineRank = results.size,
            )
        }
        return results
    }
}
