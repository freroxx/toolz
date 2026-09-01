/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine.parser

import com.frerox.toolz.data.browser.AdBlockList
import com.frerox.toolz.data.search.SearchCategory
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.data.search.engine.EngineId
import org.json.JSONObject
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Marginalia only indexes/serves general web results — it has no image, video,
 * or news surface, so [buildRequestUrls] and [parseHtml]/[parseJson] ignore
 * [SearchCategory] entirely and always return web results (or nothing, for
 * non-ALL categories — see [com.frerox.toolz.data.search.WebSearchRepository]'s
 * per-category engine selection, which excludes Marginalia from image/video/news search).
 */
@Singleton
class MarginaliaParser @Inject constructor() : EngineParser {
    override val id = EngineId.MARGINALIA

    override fun buildRequestUrls(query: String, offset: Int, category: SearchCategory, safeSearch: Boolean): List<String> {
        // Marginalia's HTML page is now a JS SPA and unscrapable — the public JSON API
        // is the only reliable surface. Pagination isn't supported by that API.
        return listOf("https://api.marginalia.nu/public/search/$query")
    }

    override fun looksLikeJson(body: String): Boolean = body.trimStart().startsWith("{")

    override fun parseJson(body: String, category: SearchCategory, adBlockEnabled: Boolean): List<SearchResult> = try {
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val results = mutableListOf<SearchResult>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val url = item.optString("url")
            if (!url.startsWith("http")) continue
            if (adBlockEnabled && AdBlockList.isBlocked(url)) continue
            val (date, cleanDescription) = ParserSupport.extractLeadingDate(item.optString("description"))
            results += SearchResult(
                title = item.optString("title").ifBlank { ParserSupport.safeHost(url) },
                snippet = cleanDescription, url = url,
                displayUrl = ParserSupport.safeHost(url), source = id.label,
                date = date, engineRank = i,
            )
        }
        results
    } catch (e: Exception) {
        android.util.Log.w("MarginaliaParser", "JSON parse failed", e)
        emptyList()
    }

    override fun parseHtml(doc: Document, category: SearchCategory, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        doc.select(".search-result, article, .query-result, li.result").forEachIndexed { rank, el ->
            val linkEl = el.select("a[href]").firstOrNull() ?: return@forEachIndexed
            val rawHref = linkEl.attr("href")
            val cleanUrl = (if (rawHref.startsWith("/")) "https://search.marginalia.nu$rawHref" else rawHref)
                .takeIf { it.startsWith("http") } ?: return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val titleEl = el.select("a[href] h2, h2 a, h3 a, a.title").firstOrNull() ?: linkEl
            val snippetText = el.select("p, .description, .snippet").firstOrNull()?.text()?.trim() ?: ""
            val (date, cleanSnippet) = ParserSupport.extractLeadingDate(snippetText)
            results += SearchResult(
                title = titleEl.text().trim().ifBlank { ParserSupport.safeHost(cleanUrl) },
                snippet = cleanSnippet, url = cleanUrl,
                displayUrl = ParserSupport.safeHost(cleanUrl), source = id.label,
                date = date, engineRank = rank,
            )
        }
        return results
    }
}
