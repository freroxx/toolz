/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine.parser

import com.frerox.toolz.data.browser.AdBlockList
import com.frerox.toolz.data.search.SearchCategory
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.data.search.engine.EngineId
import com.frerox.toolz.data.search.pagination.OffsetBasedPagination
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BingParser @Inject constructor() : EngineParser {
    override val id = EngineId.BING

    override fun buildRequestUrls(query: String, offset: Int, category: SearchCategory, safeSearch: Boolean): List<String> {
        val adlt = if (safeSearch) "&adlt=strict" else "&adlt=off"
        val path = when (category) {
            SearchCategory.IMAGES -> "images/search"
            SearchCategory.NEWS -> "news/search"
            SearchCategory.VIDEOS -> "videos/search"
            SearchCategory.ALL -> "search"
        }
        val offsetParam = OffsetBasedPagination.offsetParam(offset, id)
        return listOf("https://www.bing.com/$path?q=$query$offsetParam$adlt")
    }

    override fun parseHtml(doc: Document, category: SearchCategory, adBlockEnabled: Boolean): List<SearchResult> = when (category) {
        SearchCategory.IMAGES -> parseImages(doc, adBlockEnabled)
        SearchCategory.NEWS -> parseNews(doc, adBlockEnabled)
        SearchCategory.VIDEOS -> parseVideos(doc, adBlockEnabled)
        SearchCategory.ALL -> parseWeb(doc, adBlockEnabled)
    }

    private fun parseImages(doc: Document, adBlockEnabled: Boolean): List<SearchResult> {
        // Live-verified (2026): Bing image results embed a per-result JSON blob in
        // the m="…" attribute of a.iusc elements, HTML-escaped in the raw source
        // (&quot;). Extract those blobs from entity-unescaped HTML.
        val unescaped = ParserSupport.unescapeHtml(doc.html())
        val mBlobs = Regex("m=\"(\\{.*?\\})\"").findAll(unescaped)
        val results = mutableListOf<SearchResult>()
        mBlobs.forEachIndexed { idx, m ->
            val blob = m.groupValues[1]
            val murl = ParserSupport.jsonStringField(blob, "murl")?.takeIf { it.startsWith("http") } ?: return@forEachIndexed
            val purl = ParserSupport.jsonStringField(blob, "purl")?.takeIf { it.startsWith("http") } ?: murl
            if (adBlockEnabled && AdBlockList.isBlocked(purl)) return@forEachIndexed
            results += SearchResult(
                title = ParserSupport.jsonStringField(blob, "t")?.ifBlank { null } ?: "Image",
                snippet = "",
                url = purl,
                displayUrl = ParserSupport.safeHost(purl),
                source = id.label,
                imageUrl = ParserSupport.jsonStringField(blob, "turl")?.takeIf { it.startsWith("http") } ?: murl,
                engineRank = idx,
            )
        }
        if (results.isNotEmpty()) return results

        // Legacy DOM selector pass as a safety net if the m-attribute format moves.
        doc.select("div.imgpt, div.dg_b, div.iuscp, li.dgControl_item, a.iusc").forEachIndexed { rank, el ->
            val linkEl = el.select("a[href]").firstOrNull() ?: el.takeIf { it.tagName() == "a" } ?: return@forEachIndexed
            val imgEl = el.select("img").firstOrNull()
            val title = el.select(".infn, .inflnk, img[alt]").firstOrNull()?.text()?.trim()
                ?: imgEl?.attr("alt")?.trim() ?: "Image"
            val rawUrl = linkEl.attr("href")
            val cleanUrl = when {
                rawUrl.contains("mediaurl=") -> ParserSupport.decodeUrl(rawUrl.substringAfter("mediaurl=").substringBefore("&"))
                rawUrl.startsWith("http") -> rawUrl
                else -> return@forEachIndexed
            }
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val imgSrc = imgEl?.attr("src")?.takeIf { it.startsWith("http") }
                ?: imgEl?.attr("data-src")?.takeIf { it.startsWith("http") }
            results += SearchResult(
                title = title, snippet = "", url = cleanUrl,
                displayUrl = ParserSupport.safeHost(cleanUrl), source = id.label,
                imageUrl = imgSrc, engineRank = rank,
            )
        }
        return results
    }

    private fun parseNews(doc: Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        doc.select("div.newsAnswerArticle, div.news-card, .news-result, li.news_divid, article").forEachIndexed { rank, el ->
            val titleEl = el.select("a.title, a.news-card-title, h3 a, h4 a, h2 a").firstOrNull() ?: return@forEachIndexed
            val cleanUrl = titleEl.attr("href").takeIf { it.startsWith("http") } ?: return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val snippetText = el.select(".snippet, .news-card-body, p").firstOrNull()?.text()?.trim() ?: ""
            val (parsedDate, cleanSnippet) = ParserSupport.extractLeadingDate(snippetText)
            val dateFromDom = el.select(".source, time, .age").firstOrNull()?.text()?.trim()
            val title = titleEl.text().trim().ifBlank { return@forEachIndexed }
            results += SearchResult(
                title = title, snippet = cleanSnippet, url = cleanUrl,
                displayUrl = ParserSupport.safeHost(cleanUrl), source = id.label,
                date = dateFromDom ?: parsedDate, breadcrumb = ParserSupport.safeHost(cleanUrl), engineRank = rank,
            )
        }
        return results
    }

    private fun parseVideos(doc: Document, adBlockEnabled: Boolean): List<SearchResult> {
        // Live-verified (2026): Bing video results carry the same escaped m="…" JSON
        // blobs as images — with murl = the real watch URL (youtube.com/…),
        // vt = title, du = duration, thid = thumbnail id.
        val unescaped = ParserSupport.unescapeHtml(doc.html())
        val mBlobs = Regex("m=\"(\\{.*?\\})\"").findAll(unescaped)
        val results = mutableListOf<SearchResult>()
        mBlobs.forEachIndexed { idx, m ->
            val blob = m.groupValues[1]
            val watchUrl = ParserSupport.jsonStringField(blob, "murl")?.takeIf { it.startsWith("http") }
                ?: ParserSupport.jsonStringField(blob, "pgurl")?.takeIf { it.startsWith("http") }
                ?: return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(watchUrl)) return@forEachIndexed
            val title = ParserSupport.jsonStringField(blob, "vt") ?: ParserSupport.safeHost(watchUrl)
            val thumbId = ParserSupport.jsonStringField(blob, "thid")
            val thumb = thumbId?.let { "https://ts4.mm.bing.net/th?id=$it&w=480&h=360" }
            val duration = ParserSupport.jsonStringField(blob, "du")
            results += SearchResult(
                title = title,
                snippet = duration ?: "",
                url = watchUrl,
                displayUrl = ParserSupport.safeHost(watchUrl),
                source = id.label,
                imageUrl = thumb,
                engineRank = idx,
            )
        }
        if (results.isNotEmpty()) return results

        // Legacy DOM selector pass as a safety net.
        doc.select("li.dg_u, div.mc_vtvc, div.dg_b, div.vr_card").forEachIndexed { rank, el ->
            val linkEl = el.select("a[href]").firstOrNull() ?: return@forEachIndexed
            val titleEl = el.select(".mc_vtvc_title, .tl, .tilte, h3").firstOrNull() ?: return@forEachIndexed
            val cleanUrl = linkEl.attr("href").takeIf { it.startsWith("http") } ?: return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val title = titleEl.text().trim().ifBlank { return@forEachIndexed }
            val imgEl = el.select("img").firstOrNull()
            val imgSrc = imgEl?.attr("src")?.takeIf { it.startsWith("http") } ?: imgEl?.attr("data-src")
            val snippetText = el.select(".mc_vtvc_meta_block, .dur, p").firstOrNull()?.text()?.trim() ?: ""
            val (date, cleanSnippet) = ParserSupport.extractLeadingDate(snippetText)
            results += SearchResult(
                title = title, snippet = cleanSnippet, url = cleanUrl,
                displayUrl = ParserSupport.safeHost(cleanUrl), source = id.label,
                date = date, imageUrl = imgSrc, engineRank = rank,
            )
        }
        return results
    }

    private fun parseWeb(doc: Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        doc.select("li.b_algo, div.b_algo").forEachIndexed { rank, el ->
            val titleEl = el.select("h2 a, h3 a").firstOrNull() ?: return@forEachIndexed
            val cleanUrl = titleEl.attr("href").takeIf { it.startsWith("http") } ?: return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val snippetText = el.select("p, div.b_caption p, .b_algoSlug").firstOrNull()?.text()?.trim() ?: ""
            val (date, cleanSnippet) = ParserSupport.extractLeadingDate(snippetText)
            val breadcrumb = el.select(".b_attribution cite, cite").firstOrNull()?.text()?.trim()?.ifBlank { null }
            results += SearchResult(
                title = titleEl.text().trim().ifBlank { ParserSupport.safeHost(cleanUrl) },
                snippet = cleanSnippet, url = cleanUrl,
                displayUrl = breadcrumb ?: ParserSupport.safeHost(cleanUrl), source = id.label,
                date = date, breadcrumb = breadcrumb, engineRank = rank,
            )
        }
        return results
    }
}
