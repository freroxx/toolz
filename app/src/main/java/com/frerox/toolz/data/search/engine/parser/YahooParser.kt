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
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YahooParser @Inject constructor() : EngineParser {
    override val id = EngineId.YAHOO

    override fun buildRequestUrls(query: String, offset: Int, category: SearchCategory, safeSearch: Boolean): List<String> {
        val vm = if (safeSearch) "&vm=r" else ""
        val b = OffsetBasedPagination.offsetParam(offset, id)
        return when (category) {
            SearchCategory.NEWS -> listOf("https://news.search.yahoo.com/search?p=$query$b$vm")
            SearchCategory.IMAGES -> listOf("https://images.search.yahoo.com/search/images?p=$query$vm")
            SearchCategory.VIDEOS -> listOf("https://video.search.yahoo.com/search/video?p=$query$vm")
            SearchCategory.ALL -> listOf("https://search.yahoo.com/search?p=$query$b$vm")
        }
    }

    override fun parseHtml(doc: Document, category: SearchCategory, adBlockEnabled: Boolean): List<SearchResult> = when (category) {
        SearchCategory.IMAGES -> parseImages(doc, adBlockEnabled)
        SearchCategory.VIDEOS -> parseVideos(doc, adBlockEnabled)
        SearchCategory.NEWS -> parseNews(doc, adBlockEnabled)
        SearchCategory.ALL -> parseWeb(doc, adBlockEnabled)
    }

    /** Yahoo wraps outbound links in a `.../RU=<target>/RK=...` redirect — unwrap before use. */
    private fun resolveHref(el: Element): String? {
        val raw = el.attr("href")
        val resolved = ParserSupport.unwrapYahooRedirect(raw)
        return resolved.takeIf { it.startsWith("http") }
    }

    private fun parseImages(doc: Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        doc.select("li.ld, a.img, div.img, a[href*=\"imgurl=\"]").forEachIndexed { rank, el ->
            val linkEl = el.select("a[href]").firstOrNull() ?: el.takeIf { it.tagName() == "a" } ?: return@forEachIndexed
            val imgEl = el.select("img").firstOrNull()
            val rawHref = linkEl.attr("href")
            val cleanUrl = when {
                rawHref.contains("imgurl=") -> ParserSupport.decodeUrl(rawHref.substringAfter("imgurl=").substringBefore("&"))
                rawHref.contains("RU=") -> ParserSupport.unwrapYahooRedirect(rawHref)
                rawHref.startsWith("http") -> rawHref
                else -> return@forEachIndexed
            }
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val title = imgEl?.attr("alt")?.trim()?.ifBlank { null } ?: el.attr("title").trim().ifBlank { "Image" }
            val imgSrc = imgEl?.attr("src")?.takeIf { it.startsWith("http") }
                ?: imgEl?.attr("data-src")?.takeIf { it.startsWith("http") }
            results += SearchResult(
                title = title, snippet = "", url = cleanUrl,
                displayUrl = ParserSupport.safeHost(cleanUrl), source = id.label,
                imageUrl = imgSrc, engineRank = rank,
            )
        }
        if (results.isEmpty()) results += parseImagesFromRawHtml(doc.html(), adBlockEnabled)
        return results
    }

    private fun parseImagesFromRawHtml(html: String, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        Regex("imgurl=([^&\"']+)").findAll(html).forEachIndexed { idx, m ->
            val raw = ParserSupport.decodeUrl(m.groupValues[1])
            if (!raw.startsWith("http")) return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(raw)) return@forEachIndexed
            results += SearchResult(
                title = "Image", snippet = "", url = raw,
                displayUrl = ParserSupport.safeHost(raw), source = id.label, engineRank = idx,
            )
        }
        return results
    }

    private fun parseVideos(doc: Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        // Legacy markup
        doc.select("li.vlist, div.v-meta, div.v-title, div.v-card").forEachIndexed { rank, el ->
            val linkEl = el.select("a[href]").firstOrNull() ?: return@forEachIndexed
            val cleanUrl = resolveHref(linkEl) ?: return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val titleEl = el.select("h3, h4, .v-title, a[title]").firstOrNull() ?: linkEl
            val imgEl = el.select("img").firstOrNull()
            val imgSrc = imgEl?.attr("src")?.takeIf { it.startsWith("http") } ?: imgEl?.attr("data-src")
            val (date, snippet) = ParserSupport.extractLeadingDate(el.select(".v-desc, p, .v-meta").firstOrNull()?.text()?.trim() ?: "")
            results += SearchResult(
                title = titleEl.text().trim().ifBlank { ParserSupport.safeHost(cleanUrl) },
                snippet = snippet, url = cleanUrl,
                displayUrl = ParserSupport.safeHost(cleanUrl), source = id.label,
                date = date, imageUrl = imgSrc, engineRank = rank,
            )
        }

        // 2026 UDS markup: results are `li.tile` with the real target in
        // data-referenceurl (or imgurl= on the tracking href) and the thumbnail
        // on img.tile-image — the older vlist/v-meta selectors no longer match.
        if (results.isEmpty()) {
            doc.select("li.tile").forEachIndexed { rank, el ->
                val linkEl = el.select("a[href]").firstOrNull() ?: return@forEachIndexed
                val rawHref = linkEl.attr("href")
                val target = el.attr("data-referenceurl").takeIf { it.startsWith("http") }
                    ?: rawHref.takeIf { it.contains("imgurl=") }
                        ?.let { ParserSupport.decodeUrl(it.substringAfter("imgurl=").substringBefore("&")) }
                    ?: resolveHref(linkEl)
                    ?: return@forEachIndexed
                if (adBlockEnabled && AdBlockList.isBlocked(target)) return@forEachIndexed
                val imgEl = el.select("img").firstOrNull()
                val imgSrc = imgEl?.attr("src")?.takeIf { it.startsWith("http") }
                    ?: imgEl?.attr("data-src")?.takeIf { it.startsWith("http") }
                val title = el.select(".tile-title").firstOrNull()?.text()?.trim()?.ifBlank { null }
                    ?: imgEl?.attr("alt")?.trim()?.ifBlank { null }
                    ?: linkEl.attr("title").trim().ifBlank { null }
                    ?: ParserSupport.safeHost(target)
                val snippet = el.select(".tile-description").firstOrNull()?.text()?.trim() ?: ""
                results += SearchResult(
                    title = title, snippet = snippet, url = target,
                    displayUrl = ParserSupport.safeHost(target), source = id.label,
                    imageUrl = imgSrc, engineRank = rank,
                )
            }
        }
        return results
    }

    private fun parseNews(doc: Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        doc.select("div.NewsArticle, li.NewsArticle, div.dd.news, div.compArticle, li.algo").forEachIndexed { rank, el ->
            if (el.hasClass("ads")) return@forEachIndexed
            val titleEl = el.select("h4.s-title a, h4 a, h3 a, a.thmb").firstOrNull() ?: return@forEachIndexed
            val cleanUrl = resolveHref(titleEl) ?: return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val descText = el.select(".compText, .fz-m, p, .fc-falcon").firstOrNull()?.text()?.trim() ?: ""
            val (parsedDate, cleanSnippet) = ParserSupport.extractLeadingDate(descText)
            val dateFromDom = el.select(".fc-2nd, time, .source").firstOrNull()?.text()?.trim()
            val imgEl = el.select("img").firstOrNull()
            val imgSrc = imgEl?.attr("src")?.takeIf { it.startsWith("http") } ?: imgEl?.attr("data-src")
            results += SearchResult(
                title = titleEl.text().trim().ifBlank { ParserSupport.safeHost(cleanUrl) },
                snippet = cleanSnippet, url = cleanUrl,
                displayUrl = ParserSupport.safeHost(cleanUrl), source = id.label,
                date = dateFromDom ?: parsedDate, imageUrl = imgSrc, engineRank = rank,
            )
        }
        return results
    }

    private fun parseWeb(doc: Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        var rank = 0
        for (heading in doc.select("h3, h4.s-title")) {
            val link = heading.parent()?.takeIf { it.tagName().equals("a", ignoreCase = true) }
                ?: heading.select("a").firstOrNull() ?: continue
            val rawHref = link.attr("href")
            if (rawHref.isBlank() || !rawHref.startsWith("http")) continue

            val cleanUrl = ParserSupport.unwrapYahooRedirect(rawHref)
            if (cleanUrl.contains("yahoo.com/search") || cleanUrl.contains("r.search.yahoo.com")) continue
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) continue

            val title = heading.text().trim().ifBlank { continue }

            val resultContainer = heading.parents().firstOrNull {
                it.tagName().equals("li", ignoreCase = true) || it.hasClass("algo") || it.hasClass("dd")
            }
            // Sponsored rows live in div.dd.ads (verified against live 2026 markup: class
            // "dd fst ads bcan1 relsrch AdTop" etc.). Their targets are rdclks redirects on
            // r.search.yahoo.com — the same host the allowlist exempts, so AdBlockList can
            // never filter them; skip on the container class instead.
            if (resultContainer?.hasClass("ads") == true) continue
            // rdclks/secclk are Yahoo's sponsored-click redirect paths — never organic results.
            if (rawHref.contains("/rdclks/") || rawHref.contains("/secclk/")) continue
            val snippetText = resultContainer
                ?.select(".compText, .fz-m, p, .fc-falcon, .compText p")
                ?.firstOrNull()?.text()?.trim() ?: ""
            val (date, cleanSnippet) = ParserSupport.extractLeadingDate(snippetText)

            results += SearchResult(
                title = title, snippet = cleanSnippet, url = cleanUrl,
                displayUrl = ParserSupport.safeHost(cleanUrl), source = id.label,
                date = date, engineRank = rank++,
            )
        }
        return results
    }
}
