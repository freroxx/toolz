/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.data.search

import androidx.compose.runtime.Immutable
import com.frerox.toolz.data.browser.AdBlockList
import com.frerox.toolz.data.search.engine.EngineHealth
import com.frerox.toolz.data.search.engine.EngineHealthTracker
import com.frerox.toolz.data.search.engine.EngineId
import com.frerox.toolz.data.search.engine.MetaMerger
import com.frerox.toolz.data.search.engine.parser.EngineParser
import com.frerox.toolz.data.search.engine.parser.EngineParserRegistry
import com.frerox.toolz.data.search.engine.parser.GenericFallbackParser
import com.frerox.toolz.data.search.http.FetchResult
import com.frerox.toolz.data.search.http.SearchHttpClient
import com.frerox.toolz.data.settings.SettingsRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

enum class SearchCategory { ALL, IMAGES, NEWS, VIDEOS }

@Immutable
@com.squareup.moshi.JsonClass(generateAdapter = true)
data class SearchResult(
    val title: String,
    val snippet: String,
    val url: String,
    val displayUrl: String,
    val source: String = "WEB",
    val date: String? = null,          // "Jun 12, 2025" or "3 days ago"
    val breadcrumb: String? = null,    // "Site › Section › Page"
    val imageUrl: String? = null,      // OG image if present
    val engineRank: Int = 0,           // original position in the engine's own results
    val engines: List<String> = emptyList(), // contributing engines, for META consensus badges
)

/**
 * Orchestrates web search across engines: resolves the user's engine/category/safe-search
 * settings, fans a query out to the right [EngineParser](s) via [SearchHttpClient], and
 * merges results — directly for a single engine, via [MetaMerger] for META.
 *
 * This class intentionally knows nothing about HTTP retry/UA rotation ([SearchHttpClient]'s
 * job), DNS ([com.frerox.toolz.data.search.dns.DohClientFactory]'s job), per-engine markup
 * ([EngineParser]'s job), or rate-limit bookkeeping ([EngineHealthTracker]'s job) — it only
 * coordinates them.
 */
@Singleton
class WebSearchRepository @Inject constructor(
    private val searchDao: SearchDao,
        private val settingsRepository: SettingsRepository,
            private val moshi: Moshi,
                private val adBlockManager: com.frerox.toolz.util.network.AdBlockManager,
                    private val metaMerger: MetaMerger,
                        private val httpClient: SearchHttpClient,
                            private val parsers: EngineParserRegistry,
                                private val genericFallback: GenericFallbackParser,
                                    private val healthTracker: EngineHealthTracker,
) {
    val history = searchDao.getRecentHistory()
    val bookmarks = searchDao.getBookmarks()
    val quickLinks = searchDao.getQuickLinks()

    // ─── Engine health (UI status indicator) ───────────────────────────────────

    fun engineHealthSnapshot(): Map<EngineId, EngineHealth> = healthTracker.healthSnapshot()

    // ─── Video domain allowlist ─────────────────────────────────────────────────

    fun isAllowedVideoTarget(url: String): Boolean = Companion.isAllowedVideoTarget(url)

    fun isAdDomain(url: String): Boolean = AdBlockList.isBlocked(url)

    // ─── Suggestions (Bing / Qwant OpenSearch) ─────────────────────────────────

    suspend fun fetchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val candidateUrls = listOf(
                "https://api.bing.com/osjson.aspx?query=$encoded",
                "https://api.qwant.com/v3/suggest?q=$encoded&client=opensearch",
            )
            for (url in candidateUrls) {
                val suggestions = fetchSuggestionsFrom(url)
                if (suggestions.isNotEmpty()) return@withContext suggestions
            }
            emptyList()
    }

    private suspend fun fetchSuggestionsFrom(url: String): List<String> {
        val result = httpClient.fetch(url, retries = 0)
        val body = (result as? FetchResult.Success)?.body ?: return emptyList()
        return try {
            val rawList = moshi.adapter<List<Any>>(
                Types.newParameterizedType(List::class.java, Any::class.java)
            ).fromJson(body) ?: return emptyList()
            @Suppress("UNCHECKED_CAST")
            (rawList.getOrNull(1) as? List<String>) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ─── Main search ────────────────────────────────────────────────────────────

    suspend fun search(
        query: String,
        offset: Int = 0,
        category: SearchCategory = SearchCategory.ALL,
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val adBlockEnabled = settingsRepository.searchAdBlockEnabled.first()
        val safeSearch = settingsRepository.searchSafeSearch.first()
        val engine = EngineId.fromString(settingsRepository.searchEngine.first())
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())

        when (category) {
            SearchCategory.IMAGES, SearchCategory.VIDEOS ->
            searchMedia(engine, encodedQuery, offset, category, safeSearch, adBlockEnabled)
            SearchCategory.ALL, SearchCategory.NEWS ->
            searchWebOrNews(engine, encodedQuery, offset, category, safeSearch, adBlockEnabled)
        }
    }

    /** Images/videos never use META consensus merging — fan out, fall back across engines, dedupe by URL. */
    private suspend fun searchMedia(
        engine: EngineId,
        encodedQuery: String,
        offset: Int,
        category: SearchCategory,
        safeSearch: Boolean,
        adBlockEnabled: Boolean,
    ): List<SearchResult> = coroutineScope {
        val candidateEngines = mediaEnginesFor(engine)
        val primary = candidateEngines.first()
        var results = fetchFromEngine(primary, encodedQuery, offset, category, safeSearch, adBlockEnabled)

        // Media engines rate-limit far more aggressively than web search (image/video
        // endpoints are bot-challenge magnets) — rotate through the other media-capable
        // engines rather than showing the user an empty media tab.
        if (results.isEmpty()) {
            for (alt in candidateEngines.drop(1)) {
                results = fetchFromEngine(alt, encodedQuery, offset, category, safeSearch, adBlockEnabled)
                if (results.isNotEmpty()) break
            }
        }

        val filtered = if (category == SearchCategory.VIDEOS) results.filter { isAllowedVideoTarget(it.url) } else results
        filtered.distinctBy { metaMerger.canonicalUrl(it.url) }.take(500)
    }

    /** Engines that support returning images/videos for the user's selected engine setting, in fallback order. */
    private fun mediaEnginesFor(engine: EngineId): List<EngineId> = when (engine) {
        EngineId.META -> listOf(EngineId.QWANT, EngineId.YAHOO, EngineId.BING)
        // Marginalia has no media search — start from the next maintained media engine
        // instead of guaranteeing an empty result (engine.label is preserved on results).
        EngineId.MARGINALIA -> listOf(EngineId.QWANT, EngineId.YAHOO, EngineId.BING)
        else -> listOf(engine) + EngineId.CONCRETE.filter { it != engine && it != EngineId.MARGINALIA }
    }

    private suspend fun searchWebOrNews(
        engine: EngineId,
        encodedQuery: String,
        offset: Int,
        category: SearchCategory,
        safeSearch: Boolean,
        adBlockEnabled: Boolean,
    ): List<SearchResult> = coroutineScope {
        val queryEngines = if (engine == EngineId.META) EngineId.META_MEMBERS else listOf(engine)

        val resultsByEngine = queryEngines
        .map { eng -> async { eng to fetchFromEngine(eng, encodedQuery, offset, category, safeSearch, adBlockEnabled) } }
        .awaitAll()
        .filter { (_, results) -> results.isNotEmpty() }
        .toMap()
        .toMutableMap()

        // Single-engine mode: if the chosen engine came back empty, rotate through the
        // other maintained engines rather than showing the user a dead search.
        if (engine != EngineId.META && resultsByEngine[engine].isNullOrEmpty()) {
            val fallbackEngine = EngineId.CONCRETE
            .filter { it != engine && healthTracker.isAvailable(it) }
            .firstNotNullOfOrNull { alt ->
                val altResults = fetchFromEngine(alt, encodedQuery, offset, category, safeSearch, adBlockEnabled)
                (alt to altResults).takeIf { altResults.isNotEmpty() }
            }
            fallbackEngine?.let { (alt, altResults) -> resultsByEngine[alt] = altResults }
        }

        if (engine == EngineId.META) {
            metaMerger.merge(resultsByEngine).take(500)
        } else {
            resultsByEngine.values.flatten().distinctBy { metaMerger.canonicalUrl(it.url) }.take(500)
        }
    }

    // ─── Per-engine fetch ───────────────────────────────────────────────────────

    private suspend fun fetchFromEngine(
        engine: EngineId,
        encodedQuery: String,
        offset: Int,
        category: SearchCategory,
        safeSearch: Boolean,
        adBlockEnabled: Boolean,
    ): List<SearchResult> {
        if (!healthTracker.isAvailable(engine)) return emptyList()
            val parser = parsers[engine] ?: return emptyList()

            for (url in parser.buildRequestUrls(encodedQuery, offset, category, safeSearch)) {
                val outcome = tryFetchAndParse(parser, engine, url, category, adBlockEnabled)
                when (outcome) {
                    EngineFetchOutcome.RateLimited -> {
                        healthTracker.recordRateLimited(engine)
                        break // this engine is cooling down — trying its other candidate URLs won't help
                    }
                    is EngineFetchOutcome.Results -> {
                        if (outcome.results.isNotEmpty()) {
                            healthTracker.recordSuccess(engine, outcome.results.size)
                            return outcome.results
                        }
                        // Empty parse — try the next candidate URL, if any.
                    }
                    EngineFetchOutcome.NoResults -> Unit // try next URL
                }
            }
            android.util.Log.w("WebSearchRepository", "[$engine] all candidate URLs exhausted with 0 results")
            return emptyList()
    }

    private sealed interface EngineFetchOutcome {
        data object RateLimited : EngineFetchOutcome
        data object NoResults : EngineFetchOutcome
        data class Results(val results: List<SearchResult>) : EngineFetchOutcome
    }

    private suspend fun tryFetchAndParse(
        parser: EngineParser,
        engine: EngineId,
        url: String,
        category: SearchCategory,
        adBlockEnabled: Boolean,
    ): EngineFetchOutcome {
        val fetch = httpClient.fetch(url)
        val body = when (fetch) {
            is FetchResult.Success -> fetch.body
            FetchResult.RateLimited -> return EngineFetchOutcome.RateLimited
            FetchResult.BotChallenge -> {
                android.util.Log.w("WebSearchRepository", "[$engine] bot challenge at $url — cooling down")
                healthTracker.recordRateLimited(engine)
                return EngineFetchOutcome.RateLimited
            }
            FetchResult.Timeout -> {
                android.util.Log.w("WebSearchRepository", "[$engine] timeout fetching $url")
                return EngineFetchOutcome.NoResults
            }
            is FetchResult.Failure -> {
                android.util.Log.w("WebSearchRepository", "[$engine] error fetching $url", fetch.cause)
                return EngineFetchOutcome.NoResults
            }
        }

        if (parser.looksLikeJson(body)) {
            val jsonResults = parser.parseJson(body, category, adBlockEnabled)
            if (jsonResults.isNotEmpty()) return EngineFetchOutcome.Results(jsonResults)
        }

        val doc = Jsoup.parse(body, url)
        val parsed = parser.parseHtml(doc, category, adBlockEnabled)
        if (parsed.isNotEmpty()) return EngineFetchOutcome.Results(parsed)

            // Specific parser yielded nothing — the engine likely changed its markup.
            // Try a lenient generic scrape before giving up on this URL entirely.
            val fallback = genericFallback.parse(doc, engine, category, adBlockEnabled)
            return if (fallback.isNotEmpty()) EngineFetchOutcome.Results(fallback) else EngineFetchOutcome.NoResults
    }

    // ─── Page content ───────────────────────────────────────────────────────────

    suspend fun fetchWebsiteContent(url: String): String = withContext(Dispatchers.IO) {
        try {
            val html = httpClient.fetchRaw(url) ?: return@withContext "Error: Empty response"
            val doc = Jsoup.parse(html, url)
            doc.select("script,style,nav,footer,header,aside,.ads,.sidebar,#cookie-banner").remove()
            val text = (doc.select("article,main,.content,.post-content,#content,.article-body").firstOrNull()
            ?: doc.body())?.text() ?: ""
            if (text.length > 4_000) text.take(4_000) + "… [truncated]" else text
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun fetchWebsiteContentRaw(url: String): String? = httpClient.fetchRaw(url)

    /** Dedicated fetcher for ad-block list text files (generous timeout, bypasses DoH). */
    suspend fun fetchBlocklistRaw(url: String): String? = httpClient.fetchBlocklist(url)

    // ─── DAO wrappers ───────────────────────────────────────────────────────────

    suspend fun addHistory(query: String) = searchDao.recordHistory(query)
    suspend fun deleteHistory(id: Long) = searchDao.deleteHistory(id)
    suspend fun clearHistory() = searchDao.clearHistory()
    suspend fun addBookmark(title: String, url: String) = searchDao.insertBookmark(BookmarkEntry(title = title, url = url))
    suspend fun removeBookmark(url: String) = searchDao.deleteBookmarkByUrl(url)
    suspend fun isBookmarked(url: String): Boolean = searchDao.isBookmarked(url)
    suspend fun addQuickLink(title: String, url: String) = searchDao.insertQuickLink(QuickLinkEntry(title = title, url = url))
    suspend fun removeQuickLink(id: Long) = searchDao.deleteQuickLink(id)
    suspend fun updateBookmark(id: Long, title: String, url: String) = searchDao.updateBookmark(id, title, url)
    suspend fun updateQuickLink(id: Long, title: String, url: String) = searchDao.updateQuickLink(id, title, url)
    suspend fun updateQuickLinks(entries: List<QuickLinkEntry>) = searchDao.updateQuickLinks(entries)

    companion object {
        val ALLOWED_VIDEO_DOMAINS = listOf(
            "youtube.com", "youtu.be",
            "tiktok.com",
            "vimeo.com",
            "twitch.tv",
            "dailymotion.com",
        )

        fun isAllowedVideoTarget(url: String): Boolean {
            val host = try { java.net.URI(url).host?.lowercase().orEmpty() } catch (_: Exception) { "" }
            return ALLOWED_VIDEO_DOMAINS.any { domain -> host == domain || host.endsWith(".$domain") }
        }
    }
}
