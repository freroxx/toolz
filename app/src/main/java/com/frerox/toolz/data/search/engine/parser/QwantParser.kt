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

/**
 * Qwant-slot engine parser.
 *
 * Live-verified (2026): Qwant's own surfaces are unusable for scraping —
 * api.qwant.com/v3 is behind a DataDome JS challenge (403 + captcha-delivery on
 * every request, all header/UA variants) and www.qwant.com renders client-side
 * behind the same wall. Rather than keep a dead slot in the META fan-out, this
 * parser queries **Brave Search** — an independent, privacy-first index, unlike
 * Yahoo/Bing — and labels results with [EngineId.QWANT]'s slot so engine
 * settings, health tracking, and META consensus keep working unchanged.
 */
@Singleton
class QwantParser @Inject constructor() : EngineParser {
    override val id = EngineId.QWANT

    override fun buildRequestUrls(query: String, offset: Int, category: SearchCategory, safeSearch: Boolean): List<String> {
        val offsetParam = OffsetBasedPagination.offsetParam(offset, id)
        val safeParam = if (safeSearch) "&safesearch=strict" else ""
        return when (category) {
            SearchCategory.IMAGES -> listOf(
                "https://search.brave.com/images?q=$query$offsetParam$safeParam",
            )
            SearchCategory.VIDEOS -> listOf(
                "https://search.brave.com/videos?q=$query$offsetParam$safeParam",
            )
            SearchCategory.NEWS -> listOf(
                "https://search.brave.com/news?q=$query$offsetParam$safeParam",
            )
            SearchCategory.ALL -> listOf(
                "https://search.brave.com/search?q=$query$offsetParam$safeParam",
            )
        }
    }

    override fun parseHtml(doc: Document, category: SearchCategory, adBlockEnabled: Boolean): List<SearchResult> = when (category) {
        SearchCategory.IMAGES -> parseImages(doc, adBlockEnabled)
        SearchCategory.VIDEOS -> parseVideos(doc, adBlockEnabled)
        SearchCategory.NEWS -> parseWeb(doc, adBlockEnabled)
        SearchCategory.ALL -> parseWeb(doc, adBlockEnabled)
    }

    /**
     * Brave's Svelte-rendered HTML keeps stable semantic classes across its CSS
     * hash churn: `div.snippet[data-type="web"]` for web results, `.title` for
     * the headline, `.snippet-description` / `.generic-snippet` for text.
     */
    private fun parseWeb(doc: Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        doc.select("div.snippet[data-type=web]").forEachIndexed { rank, el ->
            val linkEl = el.select("a[href]").firstOrNull() ?: return@forEachIndexed
            val cleanUrl = linkEl.attr("href").takeIf { it.startsWith("http") } ?: return@forEachIndexed
            if (cleanUrl.contains("search.brave.com") || cleanUrl.contains("brave.com/search")) return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val title = el.select(".title").firstOrNull()?.text()?.trim()
                ?: linkEl.select("[class~=title]").firstOrNull()?.text()?.trim()
                ?: return@forEachIndexed
            val snippetText = el.select(".snippet-description, .generic-snippet, .description")
                .firstOrNull()?.text()?.trim() ?: ""
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

    /**
     * Live-verified (2026) Brave images markup: `button.image-result` tiles with
     * `div.image-wrapper img` thumbnails whose src embeds the ORIGINAL image URL
     * as a base64 path segment (`/aHR0...` = "http…" in base64) behind
     * imgs.search.brave.com, `span.image-metadata-title` for the caption.
     */
    private fun parseImages(doc: Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        doc.select("button.image-result, .image-wrapper").forEachIndexed { rank, el ->
            val imgEl = el.select("img").firstOrNull() ?: return@forEachIndexed
            val thumbSrc = imgEl.absUrl("src").ifBlank { imgEl.attr("src") }
            // Decode Brave's base64 thumb path to recover the full-size original.
            val fullImage = ParserSupport.decodeBraveThumb(thumbSrc)
            val displayImage = fullImage ?: thumbSrc.takeIf { it.startsWith("http") } ?: return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(displayImage)) return@forEachIndexed
            val title = el.select(".image-metadata-title").firstOrNull()?.text()?.trim()
                ?: imgEl.attr("alt").trim()
            results += SearchResult(
                title = title.ifBlank { "Image" },
                snippet = "",
                url = displayImage,
                displayUrl = ParserSupport.safeHost(displayImage),
                source = id.label,
                imageUrl = displayImage,
                engineRank = rank,
            )
        }
        return results
    }

    /**
     * Live-verified (2026) Brave videos markup: `div.snippet[data-type=videos]`
     * blocks, each with a thumbnail anchor `a[href*=watch]` (containing the
     * duration in `.duration`), and `div.title` + `.description` in the content
     * section.
     */
    private fun parseVideos(doc: Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        doc.select("div.snippet[data-type=videos]").forEachIndexed { rank, el ->
            val linkEl = el.select("a[href*=watch], a.result-content a, a[href]").firstOrNull() ?: return@forEachIndexed
            val cleanUrl = linkEl.attr("href").takeIf { it.startsWith("http") } ?: return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val title = el.select(".result-content .title, div.title").firstOrNull()?.text()?.trim()
                ?: linkEl.attr("title").trim().ifBlank { return@forEachIndexed }
            val duration = el.select(".duration").firstOrNull()?.text()?.trim().orEmpty()
            // Thumbnail: base64-decoded Brave thumb if possible, else the raw src.
            val thumbEl = el.select(".thumbnail img, img").firstOrNull()
            val thumbSrc = thumbEl?.absUrl("src").ifBlankOrNull() ?: thumbEl?.attr("src")?.takeIf { it.startsWith("http") }
            val thumb = thumbSrc?.let { ParserSupport.decodeBraveThumb(it) ?: it }
            results += SearchResult(
                title = title,
                snippet = duration,
                url = cleanUrl,
                displayUrl = ParserSupport.safeHost(cleanUrl),
                source = id.label,
                imageUrl = thumb,
                engineRank = rank,
            )
        }
        return results
    }

    private fun String?.ifBlankOrNull(): String? = this?.takeIf { it.isNotBlank() }
}
