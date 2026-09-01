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
import org.json.JSONObject
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QwantParser @Inject constructor() : EngineParser {
    override val id = EngineId.QWANT

    override fun buildRequestUrls(query: String, offset: Int, category: SearchCategory, safeSearch: Boolean): List<String> {
        val safeSearchParam = if (safeSearch) 1 else -1
        val offsetParam = OffsetBasedPagination.offsetParam(offset, id)
        val endpoint = when (category) {
            SearchCategory.IMAGES -> "images"
            SearchCategory.NEWS -> "news"
            SearchCategory.VIDEOS -> "videos"
            SearchCategory.ALL -> "web"
        }
        val count = if (category == SearchCategory.IMAGES) 30 else 25
        return listOf(
            "https://api.qwant.com/v3/search/$endpoint?q=$query&count=$count$offsetParam&locale=en_US&device=desktop&safesearch=$safeSearchParam"
        )
    }

    // The Qwant API always returns JSON — HTML parsing below is a defensive fallback
    // in case the API endpoint is ever unreachable and the caller retries against
    // the plain qwant.com HTML search page instead.
    override fun looksLikeJson(body: String): Boolean = body.trimStart().startsWith("{")

    override fun parseJson(body: String, category: SearchCategory, adBlockEnabled: Boolean): List<SearchResult> = try {
        val root = JSONObject(body)
        val data = root.optJSONObject("data") ?: root
        val result = data.optJSONObject("result") ?: data
        when (category) {
            SearchCategory.IMAGES -> parseJsonItems(result, data, adBlockEnabled) { item, rank ->
                val title = item.optString("title", "Image").ifBlank { "Image" }
                val media = item.optString("media").takeIf { it.isNotBlank() }
                    ?: item.optJSONObject("media")?.optString("url") ?: ""
                val thumb = item.optString("thumbnail").takeIf { it.isNotBlank() } ?: item.optString("thumb")
                val fallbackUrl = item.optString("url").takeIf { it.startsWith("http") } ?: ""
                val target = media.takeIf { it.startsWith("http") } ?: fallbackUrl
                if (target.isBlank() || !target.startsWith("http")) return@parseJsonItems null
                if (adBlockEnabled && AdBlockList.isBlocked(target)) return@parseJsonItems null
                SearchResult(
                    title = title, snippet = "", url = target,
                    displayUrl = ParserSupport.safeHost(target), source = id.label,
                    imageUrl = (thumb ?: media).takeIf { it.startsWith("http") }, engineRank = rank,
                )
            }
            SearchCategory.VIDEOS -> parseJsonItems(result, data, adBlockEnabled) { item, rank ->
                val url = item.optString("url")
                if (url.isBlank() || !url.startsWith("http")) return@parseJsonItems null
                if (adBlockEnabled && AdBlockList.isBlocked(url)) return@parseJsonItems null
                val title = item.optString("title").ifBlank { ParserSupport.safeHost(url) }
                val desc = item.optString("desc").takeIf { it.isNotBlank() } ?: item.optString("description") ?: ""
                val thumb = item.optString("thumbnail").takeIf { it.isNotBlank() } ?: item.optString("thumb")
                SearchResult(
                    title = title, snippet = desc, url = url,
                    displayUrl = ParserSupport.safeHost(url), source = id.label,
                    imageUrl = thumb?.takeIf { it.startsWith("http") }, engineRank = rank,
                )
            }
            else -> parseWebOrNewsJson(root, data, result, adBlockEnabled)
        }
    } catch (e: Exception) {
        android.util.Log.w("QwantParser", "JSON parse failed", e)
        emptyList()
    }

    /** Runs [transform] over `result.items` (or `data.items` as a fallback shape), dropping nulls. */
    private inline fun parseJsonItems(
        result: JSONObject,
        data: JSONObject,
        adBlockEnabled: Boolean,
        transform: (JSONObject, Int) -> SearchResult?,
    ): List<SearchResult> {
        val items = result.optJSONArray("items") ?: data.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<SearchResult>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            transform(item, i)?.let { out += it }
        }
        return out
    }

    private fun parseWebOrNewsJson(root: JSONObject, data: JSONObject, result: JSONObject, adBlockEnabled: Boolean): List<SearchResult> {
        // Web/News responses nest items under items.main; some variants put them directly under items.
        val mainArr = result.optJSONObject("items")?.optJSONArray("main")
            ?: result.optJSONArray("items")
            ?: data.optJSONArray("items")
            ?: root.optJSONArray("items")
            ?: return emptyList()

        val results = mutableListOf<SearchResult>()
        for (i in 0 until mainArr.length()) {
            val item = mainArr.optJSONObject(i) ?: continue
            val url = item.optString("url").takeIf { it.startsWith("http") } ?: item.optString("uri") ?: ""
            if (url.isBlank() || !url.startsWith("http")) continue
            if (adBlockEnabled && AdBlockList.isBlocked(url)) continue
            val title = item.optString("title").takeIf { it.isNotBlank() } ?: item.optString("name") ?: ""
            val desc = item.optString("desc").takeIf { it.isNotBlank() }
                ?: item.optString("description").takeIf { it.isNotBlank() }
                ?: item.optString("excerpt") ?: ""
            val date = item.optString("date").takeIf { it.isNotBlank() } ?: item.optString("age").takeIf { it.isNotBlank() }
            results += SearchResult(
                title = title.ifBlank { ParserSupport.safeHost(url) },
                snippet = desc, url = url,
                displayUrl = ParserSupport.safeHost(url), source = id.label,
                date = date, engineRank = i,
            )
        }
        return results
    }

    override fun parseHtml(doc: Document, category: SearchCategory, adBlockEnabled: Boolean): List<SearchResult> {
        if (category == SearchCategory.IMAGES) return parseImagesHtml(doc, adBlockEnabled)
        val results = mutableListOf<SearchResult>()
        doc.select("[data-testid=result], .result, article, li.result").forEachIndexed { rank, el ->
            val linkEl = el.select("a[href]").firstOrNull() ?: return@forEachIndexed
            val cleanUrl = linkEl.attr("href").takeIf { it.startsWith("http") } ?: return@forEachIndexed
            if (cleanUrl.contains("qwant.com")) return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val titleEl = el.select("a[href] h2, a[href] h3, h2, h3").firstOrNull() ?: linkEl
            val (date, snippet) = ParserSupport.extractLeadingDate(
                el.select("p, .result-snippet, .desc").firstOrNull()?.text()?.trim() ?: ""
            )
            results += SearchResult(
                title = titleEl.text().trim().ifBlank { ParserSupport.safeHost(cleanUrl) },
                snippet = snippet, url = cleanUrl,
                displayUrl = ParserSupport.safeHost(cleanUrl), source = id.label,
                date = date, engineRank = rank,
            )
        }
        return results
    }

    private fun parseImagesHtml(doc: Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        doc.select("div.image-item, a[data-testid=image-item], div[data-testid=image]").forEachIndexed { rank, el ->
            val linkEl = el.select("a[href]").firstOrNull() ?: el.takeIf { it.tagName() == "a" } ?: return@forEachIndexed
            val imgEl = el.select("img").firstOrNull()
            val cleanUrl = linkEl.attr("href").takeIf { it.startsWith("http") } ?: return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val imgSrc = imgEl?.attr("src")?.takeIf { it.startsWith("http") }
                ?: imgEl?.attr("data-src")?.takeIf { it.startsWith("http") }
            results += SearchResult(
                title = imgEl?.attr("alt")?.trim()?.ifBlank { "Image" } ?: "Image",
                snippet = "", url = cleanUrl,
                displayUrl = ParserSupport.safeHost(cleanUrl), source = id.label,
                imageUrl = imgSrc, engineRank = rank,
            )
        }
        return results
    }
}
