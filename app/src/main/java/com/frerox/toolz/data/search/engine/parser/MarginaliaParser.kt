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
 * Marginalia only indexes/serves general web results — it has no image, video,
 * or news surface, so [buildRequestUrls] and [parseHtml]/[parseJson] ignore
 * [SearchCategory] entirely and always return web results (or nothing, for
 * non-ALL categories — see [com.frerox.toolz.data.search.WebSearchRepository]'s
 * per-category engine selection, which excludes Marginalia from image/video/news search).
 *
 * Live-verified (2026): the public JSON API (api.marginalia.nu) is dead and
 * search.marginalia.nu is a JS SPA, but old-search.marginalia.nu serves complete
 * server-rendered HTML results (`section.search-result` cards).
 */
@Singleton
class MarginaliaParser @Inject constructor() : EngineParser {
    override val id = EngineId.MARGINALIA

    override fun buildRequestUrls(query: String, offset: Int, category: SearchCategory, safeSearch: Boolean): List<String> {
        // The old-search frontend paginates via `page` (1-based, 10-15 results/page).
        val page = if (offset > 0) "&page=${offset / 10 + 1}" else ""
        return listOf("https://old-search.marginalia.nu/search?query=$query$page")
    }

    override fun parseHtml(doc: Document, category: SearchCategory, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        doc.select("section.search-result, section.card").forEachIndexed { rank, el ->
            // The title link carries the real target (rel=nofollow external).
            val titleEl = el.select("a.title, h2 a.title, h2 a").firstOrNull() ?: return@forEachIndexed
            val cleanUrl = titleEl.attr("href").takeIf { it.startsWith("http") } ?: return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val title = titleEl.text().trim().ifBlank { return@forEachIndexed }
            val snippetText = el.select("p.description, p").firstOrNull()?.text()?.trim() ?: ""
            val (date, cleanSnippet) = ParserSupport.extractLeadingDate(snippetText)
            results += SearchResult(
                title = title,
                snippet = cleanSnippet,
                url = cleanUrl,
                displayUrl = ParserSupport.safeHost(cleanUrl),
                source = id.label,
                date = date,
                engineRank = rank,
            )
        }
        return results
    }
}
