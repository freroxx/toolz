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
import com.frerox.toolz.data.settings.SettingsRepository
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import org.jsoup.Jsoup
import java.net.InetAddress
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// ─── Enriched SearchResult ────────────────────────────────────────────────────

enum class SearchCategory { ALL, IMAGES, NEWS, VIDEOS }

@Immutable
@JsonClass(generateAdapter = true)
data class SearchResult(
    val title:      String,
    val snippet:    String,
    val url:        String,
    val displayUrl: String,
    val source:     String       = "WEB",
    // New enriched fields (all optional — zero migration cost)
    val date:       String?      = null,   // "Jun 12, 2025" or "3 days ago"
    val breadcrumb: String?      = null,   // "Site › Section › Page"
    val imageUrl:   String?      = null,   // OG image if present
    val engineRank: Int          = 0,      // original position in the engine results
    val engines:    List<String> = emptyList(), // Engine sources for META consensus badges
)

// ─── DNS client cache ─────────────────────────────────────────────────────────

private data class DnsClientCacheKey(val provider: String, val customDns: String, val nextDnsId: String)

// ─── Repository ───────────────────────────────────────────────────────────────

@Singleton
class WebSearchRepository @Inject constructor(
    private val searchDao:          SearchDao,
    private val settingsRepository: SettingsRepository,
    private val moshi:              Moshi,
    private val adBlockManager:     com.frerox.toolz.util.network.AdBlockManager,
    private val metaMerger:         com.frerox.toolz.data.search.engine.MetaMerger,
) {
    val history    = searchDao.getRecentHistory()
    val bookmarks  = searchDao.getBookmarks()
    val quickLinks = searchDao.getQuickLinks()

    // ─── HTTP Client ──────────────────────────────────────────────────────────

    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val dnsClientCache = AtomicReference<Pair<DnsClientCacheKey, OkHttpClient>?>(null)

    private suspend fun getDnsClient(): OkHttpClient {
        val provider  = settingsRepository.searchDnsProvider.first()
        val customDns = settingsRepository.searchCustomDns.first()
        val nextDnsId = settingsRepository.searchNextDnsId.first()
        val key = DnsClientCacheKey(provider, customDns, nextDnsId)

        dnsClientCache.get()?.let { (cached, client) ->
            if (cached == key) return client
        }

        val dns: Dns = withContext(Dispatchers.IO) {
            try {
                val primaryDns = when (provider) {
                    "ADGUARD"           -> doh("https://dns.adguard-dns.com/dns-query", "94.140.14.14")
                    "ADGUARD_FAMILY"    -> doh("https://dns-family.adguard-dns.com/dns-query", "94.140.14.15")
                    "CLOUDFLARE"        -> doh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1")
                    "CLOUDFLARE_FAMILY" -> doh("https://family.cloudflare-dns.com/dns-query", "1.1.1.3")
                    "GOOGLE"            -> doh("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4")
                    "QUAD9"             -> doh("https://dns.quad9.net/dns-query", "9.9.9.9")
                    "OPENDNS"           -> doh("https://doh.opendns.com/dns-query", "208.67.222.222")
                    "NEXTDNS"           -> {
                        if (nextDnsId.isBlank()) {
                            android.util.Log.w("SearchDns","NEXTDNS id blank — fallback to SYSTEM")
                            Dns.SYSTEM
                        } else {
                            val url = "https://dns.nextdns.io/$nextDnsId"
                            doh(url, "45.90.28.0", "45.90.30.0")
                        }
                    }
                    "CONTROLD"          -> doh("https://freedns.controld.com/p1", "76.76.2.0")
                    "CLEANBROWSING"     -> doh("https://doh.cleanbrowsing.org/doh/family-filter/", "185.228.168.168")
                    "CLEANBROWSING_SECURITY" -> doh("https://doh.cleanbrowsing.org/doh/security-filter/", "185.228.168.168")
                    "CUSTOM"            -> {
                        val url = if (customDns.startsWith("http")) {
                            customDns
                        } else if (customDns.isNotBlank()) {
                            "https://$customDns/dns-query"
                        } else {
                            ""
                        }
                        if (url.startsWith("http")) doh(url) else Dns.SYSTEM
                    }
                    else                -> Dns.SYSTEM
                }
                if (primaryDns === Dns.SYSTEM) Dns.SYSTEM else ResilientDns(primaryDns, Dns.SYSTEM)
            } catch (e: Exception) { 
                e.printStackTrace()
                Dns.SYSTEM 
            }
        }

        val client = baseClient.newBuilder()
            .dns(dns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
        dnsClientCache.set(key to client)
        return client
    }

    private fun doh(url: String, vararg bootstrapIps: String): Dns {
        val builder = DnsOverHttps.Builder()
            .client(baseClient)
            .url(url.toHttpUrl())
        
        if (bootstrapIps.isNotEmpty()) {
            val ips = bootstrapIps.map { java.net.InetAddress.getByName(it) }
            builder.bootstrapDnsHosts(ips)
        }
        
        return builder.build()
    }

    private class ResilientDns(private val primary: Dns, private val fallback: Dns = Dns.SYSTEM) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                primary.lookup(hostname)
            } catch (e: Exception) {
                try {
                    fallback.lookup(hostname)
                } catch (_: Exception) {
                    throw e
                }
            }
        }
    }

    // ─── Engine cooldown (CAPTCHA / 429 resilience) ───────────────────────────

    private data class EngineCooldown(val until: Long)
    private val engineCooldowns = ConcurrentHashMap<String, EngineCooldown>()
    private val COOLDOWN_MS     = 30L * 1000L // 30 seconds

    fun isEngineAvailable(engine: String): Boolean {
        val cd = engineCooldowns[engine] ?: return true
        return System.currentTimeMillis() >= cd.until
    }

    fun setCooldown(engine: String) {
        engineCooldowns[engine] = EngineCooldown(until = System.currentTimeMillis() + COOLDOWN_MS)
    }

    // ─── Per-engine health (AI's choice: visible engine status) ───────────────

    enum class EngineHealth { OK, COOLDOWN, FAILING }

    private data class EngineStats(val lastSuccessAt: Long, val lastResultCount: Int)
    private val engineStats = ConcurrentHashMap<String, EngineStats>()

    fun recordEngineSuccess(engine: String, count: Int) {
        engineStats[engine] = EngineStats(System.currentTimeMillis(), count)
    }

    /** Snapshot of engine health for the UI status indicator. */
    fun engineHealthSnapshot(): Map<String, EngineHealth> {
        val names = listOf("YAHOO", "QWANT", "MARGINALIA", "BING")
        return names.associateWith { eng ->
            when {
                !isEngineAvailable(eng) -> EngineHealth.COOLDOWN
                engineStats[eng] != null -> EngineHealth.OK
                else -> EngineHealth.FAILING
            }
        }
    }

    // Maintained engines fallback order
    private val FALLBACK_ORDER = listOf("YAHOO", "QWANT", "MARGINALIA", "BING")

    // ─── Video Domain Filtering ────────────────────────────────────────────────
    fun isAllowedVideoTarget(url: String): Boolean = Companion.isAllowedVideoTarget(url)

    // ─── Ad block ─────────────────────────────────────────────────────────────

    fun isAdDomain(url: String): Boolean = AdBlockList.isBlocked(url)

    // ─── User agents ──────────────────────────────────────────────────────────

    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
    )

    private fun randomUA() = userAgents.random()

    // ─── Retry with exponential back-off ─────────────────────────────────────

    private suspend fun <T> withRetry(
        times: Int = 3,
        initialDelay: Long = 400L,
        block: suspend () -> T,
    ): T {
        var lastException: Exception? = null
        repeat(times) { attempt ->
            try { return block() } catch (e: Exception) {
                lastException = e
                if (attempt < times - 1) {
                    kotlinx.coroutines.delay(initialDelay * (1L shl attempt))
                }
            }
        }
        throw lastException ?: Exception("Retry exhausted")
    }

    // ─── Date extraction from snippet ─────────────────────────────────────────

    private fun extractDateFromSnippet(snippet: String): Pair<String?, String> {
        val patterns = listOf(
            Regex("""^([A-Z][a-z]{2} \d{1,2}, \d{4})\s*[–—·]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""^(\d{1,2} [A-Z][a-z]{2} \d{4})\s*[-–—·]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""^(\d+ (?:day|hour|week|month|year)s? ago)\s*[·•]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""^(\d{4}-\d{2}-\d{2})\s*[–—·]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
        )
        for (pattern in patterns) {
            val match = pattern.find(snippet.trim()) ?: continue
            return match.groupValues[1].trim() to match.groupValues[2].trim()
        }
        return null to snippet
    }

    // ─── Suggestions (Bing / Qwant OpenSearch, no DuckDuckGo) ─────────────────

    suspend fun fetchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val candidateUrls = listOf(
                "https://api.bing.com/osjson.aspx?query=$encoded",
                "https://api.qwant.com/v3/suggest?q=$encoded&client=opensearch",
            )
            val client = getDnsClient()
            for (url in candidateUrls) {
                try {
                    val request = Request.Builder().url(url)
                        .header("User-Agent", randomUA())
                        .header("Accept", "application/json")
                        .build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) continue
                    val json = response.body?.string() ?: continue
                    val rawList = moshi.adapter<List<Any>>(
                        Types.newParameterizedType(List::class.java, Any::class.java)
                    ).fromJson(json) ?: continue
                    @Suppress("UNCHECKED_CAST")
                    val items = (rawList.getOrNull(1) as? List<String>) ?: emptyList()
                    if (items.isNotEmpty()) return@withContext items
                } catch (_: Exception) {}
            }
            emptyList()
        } catch (_: Exception) { emptyList() }
    }

    // ─── Main Search ──────────────────────────────────────────────────────────

    suspend fun search(
        query: String,
        offset: Int = 0,
        category: SearchCategory = SearchCategory.ALL
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val adBlockEnabled    = settingsRepository.searchAdBlockEnabled.first()
        val rawEngine         = settingsRepository.searchEngine.first()
        val engineId          = com.frerox.toolz.data.search.engine.EngineId.fromString(rawEngine)
        val engine            = engineId.name
        val safeSearch        = settingsRepository.searchSafeSearch.first()
        val customUrlTemplate = settingsRepository.searchCustomEngineUrl.first()

        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val safeSearchBing = if (safeSearch) "&adlt=strict" else "&adlt=off"
        val safeSearchQwantParam = if (safeSearch) 1 else -1
        val safeSearchYahoo = if (safeSearch) "&vm=r" else ""

        val client = getDnsClient()

        // ── Category: Images ──────────────────────────────────────────────────
        if (category == SearchCategory.IMAGES) {
            val imageEngines = when (engine) {
                "BING" -> listOf("BING")
                "QWANT" -> listOf("QWANT")
                "YAHOO" -> listOf("YAHOO")
                else -> listOf("QWANT", "YAHOO", "BING")
            }
            val deferredImages = imageEngines.map { eng ->
                async {
                    fetchFromEngine(
                        eng, encodedQuery, offset, safeSearchBing, safeSearchQwantParam,
                        safeSearchYahoo, client, adBlockEnabled, category
                    )
                }
            }
            val allImages = deferredImages.awaitAll().flatten()
            return@withContext allImages.distinctBy { metaMerger.canonicalUrl(it.url) }.take(500)
        }

        // ── Category: Videos ──────────────────────────────────────────────────
        if (category == SearchCategory.VIDEOS) {
            val videoEngines = when (engine) {
                "BING" -> listOf("BING")
                "QWANT" -> listOf("QWANT")
                "YAHOO" -> listOf("YAHOO")
                else -> listOf("QWANT", "YAHOO", "BING")
            }
            val deferredVideos = videoEngines.map { eng ->
                async {
                    fetchFromEngine(
                        eng, encodedQuery, offset, safeSearchBing, safeSearchQwantParam,
                        safeSearchYahoo, client, adBlockEnabled, category
                    )
                }
            }
            val rawVideos = deferredVideos.awaitAll().flatten()
            // Strictly filter to allowed video targets: YouTube, TikTok, Vimeo, Twitch, Dailymotion
            val filteredVideos = rawVideos.filter { isAllowedVideoTarget(it.url) }
            return@withContext filteredVideos.distinctBy { metaMerger.canonicalUrl(it.url) }.take(500)
        }

        // ── Category: Web (ALL / NEWS) ────────────────────────────────────────
        val mainEngines = when (engine) {
            "META" -> listOf("YAHOO", "QWANT", "MARGINALIA", "BING")
            else -> listOf(engine)
        }

        val resultsByEngine = mutableMapOf<String, List<SearchResult>>()

        val deferred = mainEngines.map { eng ->
            async {
                eng to fetchFromEngine(
                    eng, encodedQuery, offset, safeSearchBing, safeSearchQwantParam,
                    safeSearchYahoo, client, adBlockEnabled, category
                )
            }
        }
        deferred.awaitAll().forEach { (eng, results) ->
            if (results.isNotEmpty()) resultsByEngine[eng] = results
        }

        // Rotation fallback: if selected single engine fails/empty, try other maintained engines
        if (engine != "META" && resultsByEngine[engine].isNullOrEmpty()) {
            val alternatives = FALLBACK_ORDER.filter { it != engine && isEngineAvailable(it) }
            for (altEng in alternatives) {
                val altResults = fetchFromEngine(
                    altEng, encodedQuery, offset, safeSearchBing, safeSearchQwantParam,
                    safeSearchYahoo, client, adBlockEnabled, category
                )
                if (altResults.isNotEmpty()) {
                    resultsByEngine[altEng] = altResults
                    break
                }
            }
        }

        if (engine == "META") {
            metaMerger.merge(resultsByEngine).take(500)
        } else {
            resultsByEngine.values.flatten().distinctBy { metaMerger.canonicalUrl(it.url) }.take(500)
        }
    }

    // ─── Engine fetcher ───────────────────────────────────────────────────────

    private suspend fun fetchFromEngine(
        eng: String,
        encodedQuery: String,
        offset: Int,
        safeSearchBing: String,
        safeSearchQwantParam: Int,
        safeSearchYahoo: String,
        client: OkHttpClient,
        adBlockEnabled: Boolean,
        category: SearchCategory = SearchCategory.ALL,
    ): List<SearchResult> {
        if (!isEngineAvailable(eng)) return emptyList()

        val urls = when (eng) {
            "BING" -> when (category) {
                SearchCategory.IMAGES -> listOf("https://www.bing.com/images/search?q=$encodedQuery&first=$offset$safeSearchBing")
                SearchCategory.NEWS -> listOf("https://www.bing.com/news/search?q=$encodedQuery&first=$offset$safeSearchBing")
                SearchCategory.VIDEOS -> listOf("https://www.bing.com/videos/search?q=$encodedQuery&first=$offset$safeSearchBing")
                else -> listOf("https://www.bing.com/search?q=$encodedQuery&first=$offset$safeSearchBing")
            }
            "YAHOO" -> {
                val bParam = if (offset > 0) "&b=${offset + 1}" else ""
                when (category) {
                    SearchCategory.NEWS -> listOf("https://news.search.yahoo.com/search?p=$encodedQuery$bParam$safeSearchYahoo")
                    SearchCategory.IMAGES -> listOf("https://images.search.yahoo.com/search/images?p=$encodedQuery$safeSearchYahoo")
                    SearchCategory.VIDEOS -> listOf("https://video.search.yahoo.com/search/video?p=$encodedQuery$safeSearchYahoo")
                    else -> listOf("https://search.yahoo.com/search?p=$encodedQuery$bParam$safeSearchYahoo")
                }
            }
            "QWANT" -> {
                val offsetParam = if (offset > 0) "&offset=$offset" else ""
                when (category) {
                    SearchCategory.IMAGES -> listOf(
                        "https://api.qwant.com/v3/search/images?q=$encodedQuery&count=30$offsetParam&locale=en_US&device=desktop&safesearch=$safeSearchQwantParam"
                    )
                    SearchCategory.NEWS -> listOf(
                        "https://api.qwant.com/v3/search/news?q=$encodedQuery&count=25$offsetParam&locale=en_US&device=desktop&safesearch=$safeSearchQwantParam"
                    )
                    SearchCategory.VIDEOS -> listOf(
                        "https://api.qwant.com/v3/search/videos?q=$encodedQuery&count=25$offsetParam&locale=en_US&device=desktop&safesearch=$safeSearchQwantParam"
                    )
                    else -> listOf(
                        "https://api.qwant.com/v3/search/web?q=$encodedQuery&count=25$offsetParam&locale=en_US&device=desktop&safesearch=$safeSearchQwantParam"
                    )
                }
            }
            "MARGINALIA" -> {
                // Public API is the only reliably accessible surface (the HTML page is a JS SPA now)
                listOf(
                    "https://api.marginalia.nu/public/search/$encodedQuery"
                )
            }
            else -> emptyList()
        }

        for (tryUrl in urls) {
            try {
                val request  = buildRequest(tryUrl)
                val response = withTimeoutOrNull(8_000) { client.newCall(request).execute() } ?: run {
                    android.util.Log.w("Search", "[$eng] Timeout fetching $tryUrl")
                    continue
                }

                if (!response.isSuccessful) {
                    android.util.Log.w("Search", "[$eng] HTTP ${response.code} for $tryUrl")
                    if (response.code in listOf(429, 403)) {
                        setCooldown(eng)
                        break
                    }
                    continue
                }

                val bodyStr = response.body?.string() ?: continue
                if (bodyStr.isBlank()) continue

                if (bodyStr.contains("detected suspicious activity") ||
                    bodyStr.contains("unusual traffic") ||
                    bodyStr.contains("cf-browser-verification") ||
                    bodyStr.contains("challenge-form") ||
                    bodyStr.contains("captcha-delivery")
                ) {
                    android.util.Log.w("Search", "[$eng] Bot challenge detected at $tryUrl — cooling down")
                    setCooldown(eng)
                    continue
                }

                // Check if JSON response (e.g. Qwant API / Marginalia API)
                val trimmed = bodyStr.trim()
                if (trimmed.startsWith("{") && eng == "QWANT") {
                    val jsonResults = parseQwantJson(trimmed, adBlockEnabled, category)
                    if (jsonResults.isNotEmpty()) { recordEngineSuccess(eng, jsonResults.size); return jsonResults }
                }
                if (trimmed.startsWith("{") && eng == "MARGINALIA") {
                    val jsonResults = parseMarginaliaJson(trimmed, adBlockEnabled)
                    if (jsonResults.isNotEmpty()) { recordEngineSuccess(eng, jsonResults.size); return jsonResults }
                }

                val doc = Jsoup.parse(bodyStr, tryUrl)
                val parsed = when (eng) {
                    "BING" -> parseBingResults(doc, adBlockEnabled, category)
                    "YAHOO" -> parseYahooResults(doc, adBlockEnabled, category)
                    "QWANT" -> parseQwantResults(doc, adBlockEnabled, category)
                    "MARGINALIA" -> parseMarginaliaResults(doc, adBlockEnabled)
                    else -> parseBingResults(doc, adBlockEnabled, category)
                }
                if (parsed.isNotEmpty()) { recordEngineSuccess(eng, parsed.size); return parsed }
                android.util.Log.w("Search", "[$eng] Fetched $tryUrl but parsed 0 results")
            } catch (e: Exception) {
                android.util.Log.w("Search", "[$eng] Error fetching $tryUrl", e)
                continue
            }
        }
        android.util.Log.w("Search", "[$eng] All candidate URLs exhausted — returning 0 results")
        return emptyList()
    }

    // ─── Request builder ─────────────────────────────────────────────────────

    private fun buildRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", randomUA())
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json,image/avif,image/webp,*/*;q=0.8")
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("Referer", "https://www.google.com/")
        .build()

    // ─── Bing parser (Web, News, Images, Videos) ─────────────────────────────

    private fun parseBingResults(
        doc: org.jsoup.nodes.Document,
        adBlockEnabled: Boolean,
        category: SearchCategory = SearchCategory.ALL,
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        if (category == SearchCategory.IMAGES) {
            doc.select("div.imgpt, div.dg_b, div.iuscp, li.dgControl_item, a.iusc").forEachIndexed { rank, el ->
                val linkEl   = el.select("a[href]").firstOrNull() ?: el.takeIf { it.tagName() == "a" } ?: return@forEachIndexed
                val imgEl    = el.select("img").firstOrNull()
                val title    = el.select(".infn, .inflnk, img[alt]").firstOrNull()?.text()?.trim()
                    ?: imgEl?.attr("alt")?.trim() ?: "Image"
                val rawUrl   = linkEl.attr("href")
                val cleanUrl = if (rawUrl.contains("mediaurl=")) {
                    rawUrl.substringAfter("mediaurl=").substringBefore("&").decodeUrl()
                } else if (rawUrl.contains("murl\":\"")) {
                    rawUrl.substringAfter("murl\":\"").substringBefore("\"")
                } else rawUrl.takeIf { it.startsWith("http") } ?: return@forEachIndexed
                if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
                val imgSrc   = imgEl?.attr("src")?.takeIf { it.startsWith("http") }
                    ?: imgEl?.attr("data-src")?.takeIf { it.startsWith("http") }
                results += SearchResult(
                    title = title, snippet = "", url = cleanUrl,
                    displayUrl = safeHost(cleanUrl), source = "Bing",
                    imageUrl = imgSrc, engineRank = rank,
                )
            }
            return results
        }

        if (category == SearchCategory.NEWS) {
            doc.select("div.newsAnswerArticle, div.news-card, .news-result, li.news_divid, article").forEachIndexed { rank, el ->
                val titleEl   = el.select("a.title, a.news-card-title, h3 a, h4 a, h2 a").firstOrNull() ?: return@forEachIndexed
                val cleanUrl  = titleEl.attr("href").takeIf { it.startsWith("http") } ?: return@forEachIndexed
                if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
                val snippetEl = el.select(".snippet, .news-card-body, p").firstOrNull()
                val dateEl    = el.select(".source, time, .age").firstOrNull()
                val snippetText = snippetEl?.text()?.trim() ?: ""
                val (parsedDate, cleanSnippet) = extractDateFromSnippet(snippetText)
                results += SearchResult(
                    title = titleEl.text().trim().takeIf { it.isNotBlank() } ?: return@forEachIndexed,
                    snippet = cleanSnippet, url = cleanUrl,
                    displayUrl = safeHost(cleanUrl), source = "Bing",
                    date = dateEl?.text()?.trim() ?: parsedDate,
                    breadcrumb = safeHost(cleanUrl), engineRank = rank,
                )
            }
            return results
        }

        if (category == SearchCategory.VIDEOS) {
            doc.select("li.dg_u, div.mc_vtvc, div.dg_b, div.vr_card").forEachIndexed { rank, el ->
                val linkEl    = el.select("a[href]").firstOrNull() ?: return@forEachIndexed
                val titleEl   = el.select(".mc_vtvc_title, .tl, .tilte, h3").firstOrNull() ?: return@forEachIndexed
                val cleanUrl  = linkEl.attr("href").takeIf { it.startsWith("http") } ?: return@forEachIndexed
                if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
                val snippetEl = el.select(".mc_vtvc_meta_block, .dur, p").firstOrNull()
                val imgEl     = el.select("img").firstOrNull()
                val imgSrc    = imgEl?.attr("src")?.takeIf { it.startsWith("http") } ?: imgEl?.attr("data-src")
                val snippetText = snippetEl?.text()?.trim() ?: ""
                val (date, cleanSnippet) = extractDateFromSnippet(snippetText)
                results += SearchResult(
                    title = titleEl.text().trim().takeIf { it.isNotBlank() } ?: return@forEachIndexed,
                    snippet = cleanSnippet, url = cleanUrl,
                    displayUrl = safeHost(cleanUrl), source = "Bing",
                    date = date, imageUrl = imgSrc, engineRank = rank,
                )
            }
            return results
        }

        // Web (ALL)
        doc.select("li.b_algo, div.b_algo").forEachIndexed { rank, el ->
            val titleEl   = el.select("h2 a, h3 a").firstOrNull() ?: return@forEachIndexed
            val snippetEl = el.select("p, div.b_caption p, .b_algoSlug").firstOrNull()
            val cleanUrl  = titleEl.attr("href").takeIf { it.startsWith("http") } ?: return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed

            val snippetText = snippetEl?.text()?.trim() ?: ""
            val (date, cleanSnippet) = extractDateFromSnippet(snippetText)
            val breadcrumb = el.select(".b_attribution cite, cite").firstOrNull()?.text()?.trim()?.ifBlank { null }

            results += SearchResult(
                title      = titleEl.text().trim().ifBlank { safeHost(cleanUrl) },
                snippet    = cleanSnippet,
                url        = cleanUrl,
                displayUrl = breadcrumb ?: safeHost(cleanUrl),
                source     = "Bing",
                date       = date,
                breadcrumb = breadcrumb,
                engineRank = rank,
            )
        }
        return results
    }

    // ─── Yahoo parser (Web, News, Images, Videos) ────────────────────────────

    private fun parseYahooResults(
        doc: org.jsoup.nodes.Document,
        adBlockEnabled: Boolean,
        category: SearchCategory = SearchCategory.ALL
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        if (category == SearchCategory.IMAGES) {
            doc.select("li.ld, a.img, div.img, a[href*=\"imgurl=\"]").forEachIndexed { rank, el ->
                val linkEl = el.select("a[href]").firstOrNull() ?: el.takeIf { it.tagName() == "a" } ?: return@forEachIndexed
                val imgEl = el.select("img").firstOrNull()
                val rawHref = linkEl.attr("href")
                val cleanUrl = if (rawHref.contains("imgurl=")) {
                    rawHref.substringAfter("imgurl=").substringBefore("&").decodeUrl()
                } else if (rawHref.contains("RU=")) {
                    try {
                        val encoded = rawHref.substringAfter("RU=").substringBefore("/RK=").substringBefore("&")
                        java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
                    } catch (_: Exception) { rawHref }
                } else rawHref.takeIf { it.startsWith("http") } ?: return@forEachIndexed

                if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
                val title = imgEl?.attr("alt")?.trim() ?: el.attr("title").trim().ifBlank { "Image" }
                val imgSrc = imgEl?.attr("src")?.takeIf { it.startsWith("http") }
                    ?: imgEl?.attr("data-src")?.takeIf { it.startsWith("http") }

                results += SearchResult(
                    title = title,
                    snippet = "",
                    url = cleanUrl,
                    displayUrl = safeHost(cleanUrl),
                    source = "Yahoo",
                    imageUrl = imgSrc,
                    engineRank = rank
                )
            }
            return results
        }

        if (category == SearchCategory.VIDEOS) {
            doc.select("li.vlist, div.v-meta, div.v-title, div.v-card").forEachIndexed { rank, el ->
                val linkEl = el.select("a[href]").firstOrNull() ?: return@forEachIndexed
                val rawHref = linkEl.attr("href")
                val cleanUrl = if (rawHref.contains("RU=")) {
                    try {
                        val encoded = rawHref.substringAfter("RU=").substringBefore("/RK=").substringBefore("&")
                        java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
                    } catch (_: Exception) { rawHref }
                } else rawHref.takeIf { it.startsWith("http") } ?: return@forEachIndexed

                if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
                val titleEl = el.select("h3, h4, .v-title, a[title]").firstOrNull() ?: linkEl
                val imgEl = el.select("img").firstOrNull()
                val imgSrc = imgEl?.attr("src")?.takeIf { it.startsWith("http") } ?: imgEl?.attr("data-src")
                val descEl = el.select(".v-desc, p, .v-meta").firstOrNull()
                val (date, snippet) = extractDateFromSnippet(descEl?.text()?.trim() ?: "")

                results += SearchResult(
                    title = titleEl.text().trim().ifBlank { safeHost(cleanUrl) },
                    snippet = snippet,
                    url = cleanUrl,
                    displayUrl = safeHost(cleanUrl),
                    source = "Yahoo",
                    date = date,
                    imageUrl = imgSrc,
                    engineRank = rank
                )
            }
            return results
        }

        if (category == SearchCategory.NEWS) {
            doc.select("div.NewsArticle, li.NewsArticle, div.dd.news, div.compArticle, li.algo").forEachIndexed { rank, el ->
                val titleEl = el.select("h4.s-title a, h4 a, h3 a, a.thmb").firstOrNull() ?: return@forEachIndexed
                val rawHref = titleEl.attr("href")
                val cleanUrl = if (rawHref.contains("RU=")) {
                    try {
                        val encoded = rawHref.substringAfter("RU=").substringBefore("/RK=").substringBefore("&")
                        java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
                    } catch (_: Exception) { rawHref }
                } else rawHref.takeIf { it.startsWith("http") } ?: return@forEachIndexed

                if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
                val descEl = el.select(".compText, .fz-m, p, .fc-falcon").firstOrNull()
                val dateEl = el.select(".fc-2nd, time, .source").firstOrNull()
                val imgEl = el.select("img").firstOrNull()
                val imgSrc = imgEl?.attr("src")?.takeIf { it.startsWith("http") } ?: imgEl?.attr("data-src")
                val (parsedDate, cleanSnippet) = extractDateFromSnippet(descEl?.text()?.trim() ?: "")

                results += SearchResult(
                    title = titleEl.text().trim().ifBlank { safeHost(cleanUrl) },
                    snippet = cleanSnippet,
                    url = cleanUrl,
                    displayUrl = safeHost(cleanUrl),
                    source = "Yahoo",
                    date = dateEl?.text()?.trim() ?: parsedDate,
                    imageUrl = imgSrc,
                    engineRank = rank
                )
            }
            if (results.isNotEmpty()) return results
        }

        // Web (ALL)
        val h3s = doc.select("h3, h4.s-title")
        var rank = 0

        for (h3 in h3s) {
            val a = h3.parent()?.takeIf { it.tagName().equals("a", ignoreCase = true) }
                ?: h3.select("a").firstOrNull() ?: continue
            val rawHref = a.attr("href")
            if (rawHref.isBlank() || !rawHref.startsWith("http")) continue

            // Yahoo search uses redirect links: https://r.search.yahoo.com/.../RU=https%3a%2f%2f.../RK=...
            val cleanUrl = if (rawHref.contains("RU=")) {
                try {
                    val encoded = rawHref.substringAfter("RU=").substringBefore("/RK=").substringBefore("&")
                    java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
                } catch (_: Exception) { rawHref }
            } else rawHref

            if (cleanUrl.contains("yahoo.com/search") || cleanUrl.contains("r.search.yahoo.com")) continue
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) continue

            val title = h3.text().trim().takeIf { it.isNotBlank() } ?: continue

            // Find description snippet from parent container
            val parentContainer = h3.parents().firstOrNull { it.tagName().equals("li", ignoreCase = true) || it.hasClass("algo") || it.hasClass("dd") }
            val descEl = parentContainer?.select(".compText, .fz-m, p, .fc-falcon, .compText p")?.firstOrNull()
            val snippetText = descEl?.text()?.trim() ?: ""
            val (date, cleanSnippet) = extractDateFromSnippet(snippetText)

            results += SearchResult(
                title      = title,
                snippet    = cleanSnippet,
                url        = cleanUrl,
                displayUrl = safeHost(cleanUrl),
                source     = "Yahoo",
                date       = date,
                engineRank = rank++,
            )
        }
        return results
    }

    // ─── Qwant JSON & HTML parsers ───────────────────────────────────────────

    private fun parseQwantJson(
        jsonStr: String,
        adBlockEnabled: Boolean,
        category: SearchCategory
    ): List<SearchResult> {
        return try {
            val root = org.json.JSONObject(jsonStr)
            val data = root.optJSONObject("data") ?: return emptyList()
            val result = data.optJSONObject("result") ?: return emptyList()
            val results = mutableListOf<SearchResult>()

            if (category == SearchCategory.IMAGES) {
                val items = result.optJSONArray("items") ?: return emptyList()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val title = item.optString("title", "Image")
                    val url = item.optString("url")
                    val media = item.optString("media")
                    val thumb = item.optString("thumbnail")
                    val targetUrl = media.takeIf { it.startsWith("http") } ?: url
                    if (targetUrl.isBlank() || !targetUrl.startsWith("http")) continue
                    if (adBlockEnabled && AdBlockList.isBlocked(targetUrl)) continue
                    results += SearchResult(
                        title = title,
                        snippet = "",
                        url = targetUrl,
                        displayUrl = safeHost(targetUrl),
                        source = "Qwant",
                        imageUrl = thumb.takeIf { it.startsWith("http") } ?: media,
                        engineRank = i
                    )
                }
                return results
            }

            if (category == SearchCategory.VIDEOS) {
                val items = result.optJSONArray("items") ?: return emptyList()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val title = item.optString("title")
                    val url = item.optString("url")
                    val thumb = item.optString("thumbnail")
                    val desc = item.optString("desc")
                    if (url.isBlank() || !url.startsWith("http")) continue
                    if (adBlockEnabled && AdBlockList.isBlocked(url)) continue
                    results += SearchResult(
                        title = title.ifBlank { safeHost(url) },
                        snippet = desc,
                        url = url,
                        displayUrl = safeHost(url),
                        source = "Qwant",
                        imageUrl = thumb.takeIf { it.startsWith("http") },
                        engineRank = i
                    )
                }
                return results
            }

            val itemsObj = result.optJSONObject("items")
            val mainArr = itemsObj?.optJSONArray("main")
                ?: result.optJSONArray("items")
                ?: return emptyList()

            for (i in 0 until mainArr.length()) {
                val item = mainArr.optJSONObject(i) ?: continue
                val title = item.optString("title")
                val url = item.optString("url")
                val desc = item.optString("desc")
                val date = item.optString("date").takeIf { it.isNotBlank() }
                if (url.isBlank() || !url.startsWith("http")) continue
                if (adBlockEnabled && AdBlockList.isBlocked(url)) continue
                results += SearchResult(
                    title = title.ifBlank { safeHost(url) },
                    snippet = desc,
                    url = url,
                    displayUrl = safeHost(url),
                    source = "Qwant",
                    date = date,
                    engineRank = i
                )
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseQwantResults(
        doc: org.jsoup.nodes.Document,
        adBlockEnabled: Boolean,
        category: SearchCategory = SearchCategory.ALL
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        if (category == SearchCategory.IMAGES) {
            doc.select("div.image-item, a[data-testid=image-item], div[data-testid=image]").forEachIndexed { rank, el ->
                val linkEl = el.select("a[href]").firstOrNull() ?: el.takeIf { it.tagName() == "a" } ?: return@forEachIndexed
                val imgEl = el.select("img").firstOrNull()
                val title = imgEl?.attr("alt")?.trim() ?: "Image"
                val cleanUrl = linkEl.attr("href").takeIf { it.startsWith("http") } ?: return@forEachIndexed
                if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
                val imgSrc = imgEl?.attr("src")?.takeIf { it.startsWith("http") }
                    ?: imgEl?.attr("data-src")?.takeIf { it.startsWith("http") }
                results += SearchResult(
                    title = title, snippet = "", url = cleanUrl,
                    displayUrl = safeHost(cleanUrl), source = "Qwant",
                    imageUrl = imgSrc, engineRank = rank,
                )
            }
            return results
        }

        doc.select("[data-testid=result], .result, article, li.result").forEachIndexed { rank, el ->
            val linkEl = el.select("a[href]").firstOrNull() ?: return@forEachIndexed
            val cleanUrl = linkEl.attr("href").takeIf { it.startsWith("http") } ?: return@forEachIndexed
            if (cleanUrl.contains("qwant.com")) return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val titleEl = el.select("a[href] h2, a[href] h3, h2, h3").firstOrNull() ?: linkEl
            val snippetEl = el.select("p, .result-snippet, .desc").firstOrNull()
            val (date, snippet) = extractDateFromSnippet(snippetEl?.text()?.trim() ?: "")
            results += SearchResult(
                title = titleEl.text().trim().ifBlank { safeHost(cleanUrl) },
                snippet = snippet,
                url = cleanUrl,
                displayUrl = safeHost(cleanUrl),
                source = "Qwant",
                date = date,
                engineRank = rank
            )
        }
        return results
    }

    // ─── Marginalia parser ───────────────────────────────────────────────────

    // Public API JSON: { results: [ { url, title, description, quality, ... } ] }
    private fun parseMarginaliaJson(
        jsonStr: String,
        adBlockEnabled: Boolean
    ): List<SearchResult> {
        return try {
            val root = org.json.JSONObject(jsonStr)
            val arr = root.optJSONArray("results") ?: return emptyList()
            val results = mutableListOf<SearchResult>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val url = item.optString("url")
                if (!url.startsWith("http")) continue
                if (adBlockEnabled && AdBlockList.isBlocked(url)) continue
                val title = item.optString("title").ifBlank { safeHost(url) }
                val description = item.optString("description")
                val (date, clean) = extractDateFromSnippet(description)
                results += SearchResult(
                    title = title,
                    snippet = clean,
                    url = url,
                    displayUrl = safeHost(url),
                    source = "Marginalia",
                    engineRank = i,
                )
            }
            results
        } catch (e: Exception) {
            android.util.Log.w("Search", "[MARGINALIA] JSON parse failed", e)
            emptyList()
        }
    }

    private fun parseMarginaliaResults(
        doc: org.jsoup.nodes.Document,
        adBlockEnabled: Boolean
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        doc.select(".search-result, article, .query-result, li.result").forEachIndexed { rank, el ->
            val linkEl = el.select("a[href]").firstOrNull() ?: return@forEachIndexed
            val rawHref = linkEl.attr("href")
            val cleanUrl = (if (rawHref.startsWith("/")) "https://search.marginalia.nu$rawHref" else rawHref)
                .takeIf { it.startsWith("http") } ?: return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
            val a = el.select("a[href] h2, h2 a, h3 a, a.title").firstOrNull() ?: linkEl
            val title = a.text().trim().ifBlank { safeHost(cleanUrl) }
            val snippet = el.select("p, .description, .snippet").firstOrNull()?.text()?.trim() ?: ""
            val (date, clean) = extractDateFromSnippet(snippet)
            results += SearchResult(
                title = title,
                snippet = clean,
                url = cleanUrl,
                displayUrl = safeHost(cleanUrl),
                source = "Marginalia",
                date = date,
                engineRank = rank
            )
        }
        return results
    }

    // ─── URL helpers ─────────────────────────────────────────────────────────

    private fun String.decodeUrl(): String = try {
        java.net.URLDecoder.decode(this, StandardCharsets.UTF_8.name())
    } catch (_: Exception) { this }

    private fun safeHost(url: String): String = try {
        java.net.URI(url).host?.removePrefix("www.") ?: url
    } catch (_: Exception) { url }

    // ─── Page content ─────────────────────────────────────────────────────────

    suspend fun fetchWebsiteContent(url: String): String = withContext(Dispatchers.IO) {
        try {
            val html = fetchWebsiteContentRaw(url) ?: return@withContext "Error: Empty response"
            val doc  = Jsoup.parse(html, url)
            doc.select("script,style,nav,footer,header,aside,.ads,.sidebar,#cookie-banner").remove()
            val text = (doc.select("article,main,.content,.post-content,#content,.article-body")
                .firstOrNull() ?: doc.body())?.text() ?: ""
            if (text.length > 4_000) text.take(4_000) + "… [truncated]" else text
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    suspend fun fetchWebsiteContentRaw(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val client   = getDnsClient()
            val request  = buildRequest(url)
            val response = withRetry { client.newCall(request).execute() }
            if (!response.isSuccessful) return@withContext null
            response.body?.string()
        } catch (e: Exception) { null }
    }

    /**
     * Dedicated fetcher for blocklist text files.
     * Uses a generous 60 s read timeout (EasyList ~2 MB) and requests plain text.
     * Bypasses the DNS-routed client to avoid DoH overhead on large downloads.
     */
    suspend fun fetchBlocklistRaw(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (compatible; AdBlockFetcher/1.0)")
                .header("Accept", "text/plain, text/*, */*;q=0.8")
                .header("Cache-Control", "no-cache")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            response.body?.string()
        } catch (e: Exception) {
            android.util.Log.e("WebSearchRepository", "fetchBlocklistRaw failed for $url", e)
            null
        }
    }

    // ─── DAO wrappers ────────────────────────────────────────────────────────

    suspend fun addHistory(query: String)               = searchDao.insertHistory(SearchHistoryEntry(query = query))
    suspend fun deleteHistory(id: Long)                 = searchDao.deleteHistory(id)
    suspend fun clearHistory()                          = searchDao.clearHistory()
    suspend fun addBookmark(title: String, url: String) = searchDao.insertBookmark(BookmarkEntry(title = title, url = url))
    suspend fun removeBookmark(url: String)             = searchDao.deleteBookmarkByUrl(url)
    suspend fun isBookmarked(url: String): Boolean      = searchDao.isBookmarked(url)
    suspend fun addQuickLink(title: String, url: String)= searchDao.insertQuickLink(QuickLinkEntry(title = title, url = url))
    suspend fun removeQuickLink(id: Long)               = searchDao.deleteQuickLink(id)
    suspend fun updateBookmark(id: Long, title: String, url: String) = searchDao.updateBookmark(id, title, url)
    suspend fun updateQuickLink(id: Long, title: String, url: String)= searchDao.updateQuickLink(id, title, url)
    suspend fun updateQuickLinks(entries: List<QuickLinkEntry>)      = searchDao.updateQuickLinks(entries)

    companion object {
        // Video domain allowlist: YouTube, TikTok, Vimeo, Twitch, Dailymotion
        val ALLOWED_VIDEO_DOMAINS = listOf(
            "youtube.com", "youtu.be",
            "tiktok.com",
            "vimeo.com",
            "twitch.tv",
            "dailymotion.com"
        )

        fun isAllowedVideoTarget(url: String): Boolean {
            val host = try { java.net.URI(url).host?.lowercase().orEmpty() } catch (_: Exception) { "" }
            return ALLOWED_VIDEO_DOMAINS.any { domain -> host == domain || host.endsWith(".$domain") }
        }
    }
}