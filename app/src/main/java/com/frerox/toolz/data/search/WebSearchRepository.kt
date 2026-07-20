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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// ─── Enriched SearchResult ────────────────────────────────────────────────────

@Immutable
@JsonClass(generateAdapter = true)
data class SearchResult(
    val title:      String,
    val snippet:    String,
    val url:        String,
    val displayUrl: String,
    val source:     String  = "WEB",
    // New enriched fields (all optional — zero migration cost)
    val date:       String? = null,   // "Jun 12, 2025" or "3 days ago"
    val breadcrumb: String? = null,   // "Site › Section › Page"
    val imageUrl:   String? = null,   // OG image if present
    val engineRank: Int     = 0,      // original position in the engine results
)

// ─── DNS client cache ─────────────────────────────────────────────────────────

private data class DnsClientCacheKey(val provider: String, val customDns: String)

// ─── Repository ───────────────────────────────────────────────────────────────

@Singleton
class WebSearchRepository @Inject constructor(
    private val searchDao:          SearchDao,
    private val settingsRepository: SettingsRepository,
    private val moshi:              Moshi,
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
        val key = DnsClientCacheKey(provider, customDns + nextDnsId)

        dnsClientCache.get()?.let { (cached, client) ->
            if (cached == key) return client
        }

        val dns: Dns = withContext(Dispatchers.IO) {
            try {
                when (provider) {
                    "ADGUARD"           -> doh("https://dns.adguard-dns.com/dns-query", "94.140.14.14")
                    "ADGUARD_FAMILY"    -> doh("https://dns-family.adguard-dns.com/dns-query", "94.140.14.15")
                    "CLOUDFLARE"        -> doh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1")
                    "CLOUDFLARE_FAMILY" -> doh("https://family.cloudflare-dns.com/dns-query", "1.1.1.3")
                    "GOOGLE"            -> doh("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4")
                    "QUAD9"             -> doh("https://dns.quad9.net/dns-query", "9.9.9.9")
                    "OPENDNS"           -> doh("https://doh.opendns.com/dns-query", "208.67.222.222")
                    "NEXTDNS"           -> {
                        val url = if (nextDnsId.isNotBlank()) {
                            "https://dns.nextdns.io/$nextDnsId"
                        } else {
                            "https://dns.nextdns.io/dns-query"
                        }
                        doh(url, "45.90.28.0", "45.90.30.0")
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

    // ─── Engine cooldown (CAPTCHA / 429 resilience) ───────────────────────────

    private data class EngineCooldown(val until: Long)
    private val engineCooldowns = ConcurrentHashMap<String, EngineCooldown>()
    private val COOLDOWN_MS     = 4L * 60L * 1000L // 4 minutes

    private fun isEngineAvailable(engine: String): Boolean {
        val cd = engineCooldowns[engine] ?: return true
        return System.currentTimeMillis() >= cd.until
    }

    private fun setCooldown(engine: String) {
        engineCooldowns[engine] = EngineCooldown(until = System.currentTimeMillis() + COOLDOWN_MS)
    }

    // Privacy-ordered fallback chain — DDG first, Google last
    private val FALLBACK_ORDER = listOf("DUCKDUCKGO", "BRAVE", "BING", "GOOGLE")

    // ─── Ad block — delegated to AdBlockList singleton ────────────────────────

    fun isAdDomain(url: String): Boolean = AdBlockList.isBlocked(url)

    // ─── User agents ──────────────────────────────────────────────────────────

    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
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

    /**
     * Many snippets begin with a date prefix like "Jun 12, 2025 — rest" or
     * "3 days ago · rest".  This extracts it and returns (date, cleanedSnippet).
     */
    private fun extractDateFromSnippet(snippet: String): Pair<String?, String> {
        val patterns = listOf(
            // "Jan 12, 2025 — rest" / "Jan 12, 2025 · rest"
            Regex("""^([A-Z][a-z]{2} \d{1,2}, \d{4})\s*[–—·]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
            // "12 Jan 2025 - rest"
            Regex("""^(\d{1,2} [A-Z][a-z]{2} \d{4})\s*[-–—·]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
            // "3 days ago · rest" / "2 hours ago · rest"
            Regex("""^(\d+ (?:day|hour|week|month|year)s? ago)\s*[·•]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
            // ISO "2025-06-12 — rest"
            Regex("""^(\d{4}-\d{2}-\d{2})\s*[–—·]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
        )
        for (pattern in patterns) {
            val match = pattern.find(snippet.trim()) ?: continue
            return match.groupValues[1].trim() to match.groupValues[2].trim()
        }
        return null to snippet
    }

    // ─── Suggestions ──────────────────────────────────────────────────────────

    suspend fun fetchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded  = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url      = "https://duckduckgo.com/ac/?q=$encoded&type=list"
            val request  = Request.Builder().url(url)
                .header("User-Agent", randomUA())
                .header("Accept", "application/json")
                .build()
            val client   = getDnsClient()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val json = response.body?.string() ?: return@withContext emptyList()
            val rawList  = moshi.adapter<List<Any>>(
                Types.newParameterizedType(List::class.java, Any::class.java)
            ).fromJson(json) ?: return@withContext emptyList()
            @Suppress("UNCHECKED_CAST")
            (rawList.getOrNull(1) as? List<String>) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    // ─── Main Search ──────────────────────────────────────────────────────────

    suspend fun search(query: String, offset: Int = 0): List<SearchResult> = withContext(Dispatchers.IO) {
        val rawAdBlockEnabled = settingsRepository.searchAdBlockEnabled.first()
        val dnsProvider       = settingsRepository.searchDnsProvider.first()
        // Only disable in-app blocking if NextDNS is taking care of it
        val adBlockEnabled    = rawAdBlockEnabled && dnsProvider != "NEXTDNS"
        
        val engine            = settingsRepository.searchEngine.first()
        val safeSearch        = settingsRepository.searchSafeSearch.first()
        val region            = settingsRepository.searchRegion.first()
        val customUrlTemplate = settingsRepository.searchCustomEngineUrl.first()

        val encodedQuery     = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val offsetParam      = if (offset > 0) "&s=$offset&dc=$offset&v=l" else ""
        val safeSearchDDG    = if (safeSearch) "&kp=1" else "&kp=-1"
        val safeSearchGoogle = if (safeSearch) "&safe=active" else "&safe=off"
        val safeSearchBing   = if (safeSearch) "&adlt=strict" else "&adlt=off"
        val regionParam      = if (region.isNotBlank() && region != "wt-wt") "&kl=$region" else ""

        val mainEngines = when (engine) {
            "META"   -> listOf("GOOGLE", "DUCKDUCKGO", "BRAVE")
            "CUSTOM" -> listOf("CUSTOM")
            else     -> listOf(engine)
        }

        val client = getDnsClient()
        val resultsByEngine = mutableMapOf<String, List<SearchResult>>()

        // Parallel fetch for primary engines
        val deferred = mainEngines.map { eng ->
            async {
                eng to fetchFromEngine(
                    eng, encodedQuery, offset, offsetParam,
                    safeSearchDDG, safeSearchGoogle, safeSearchBing,
                    regionParam, customUrlTemplate, client, adBlockEnabled,
                )
            }
        }
        deferred.awaitAll().forEach { (eng, results) -> resultsByEngine[eng] = results }

        // Rotation fallback: if primary engine is empty or cooled down, try alternatives
        if (engine != "META" && engine != "CUSTOM") {
            val primaryEmpty = resultsByEngine[engine].isNullOrEmpty()
            if (primaryEmpty) {
                val alternatives = FALLBACK_ORDER.filter { it != engine && isEngineAvailable(it) }
                for (altEng in alternatives) {
                    val altResults = fetchFromEngine(
                        altEng, encodedQuery, offset, offsetParam,
                        safeSearchDDG, safeSearchGoogle, safeSearchBing,
                        regionParam, customUrlTemplate, client, adBlockEnabled,
                    )
                    if (altResults.isNotEmpty()) {
                        resultsByEngine[altEng] = altResults
                        break
                    }
                }
            }
        }

        // ── META relevance scoring — replaces naive round-robin ──────────────
        val final = mutableListOf<SearchResult>()

        if (engine == "META") {
            // Map url → list of (engine, rank) appearances
            val urlToAppearances = mutableMapOf<String, MutableList<Pair<String, Int>>>()
            for ((eng, list) in resultsByEngine) {
                list.forEachIndexed { rank, result ->
                    urlToAppearances.getOrPut(result.url) { mutableListOf() }.add(eng to rank)
                }
            }

            // Score each unique result
            data class Scored(val result: SearchResult, val score: Double)

            val scored = resultsByEngine.values.flatten()
                .distinctBy { it.url }
                .map { result ->
                    val appearances  = urlToAppearances[result.url] ?: emptyList()
                    // Rank score: 1/(rank+1) summed across engines
                    val rankScore    = appearances.sumOf { (_, rank) -> 1.0 / (rank + 1) }
                    // Consensus bonus: appears in 2+ engines
                    val consensus    = if (appearances.size >= 2) 1.5 else 1.0
                    // Snippet quality bonus
                    val snippet      = if (result.snippet.isNotBlank()) 1.1 else 1.0
                    // Date bonus: fresh content floats up slightly
                    val freshness    = if (result.date != null) 1.05 else 1.0
                    Scored(result, rankScore * consensus * snippet * freshness)
                }
                .sortedByDescending { it.score }

            final.addAll(scored.map { it.result })
        } else {
            resultsByEngine.values.forEach { final.addAll(it) }
        }

        final.distinctBy { it.url }.take(50)
    }

    // ─── Engine fetcher ───────────────────────────────────────────────────────

    private suspend fun fetchFromEngine(
        eng: String,
        encodedQuery: String,
        offset: Int,
        offsetParam: String,
        safeSearchDDG: String,
        safeSearchGoogle: String,
        safeSearchBing: String,
        regionParam: String,
        customUrlTemplate: String,
        client: OkHttpClient,
        adBlockEnabled: Boolean,
    ): List<SearchResult> {
        if (!isEngineAvailable(eng)) return emptyList()

        val urls = when (eng) {
            "GOOGLE" -> listOf(
                "https://www.google.com/search?q=$encodedQuery&start=$offset$safeSearchGoogle",
                "https://www.google.com/search?q=$encodedQuery&start=$offset$safeSearchGoogle&gbv=1",
                "https://www.google.com/search?q=$encodedQuery&num=100",
            )
            "BRAVE" -> listOf(
                "https://search.brave.com/search?q=$encodedQuery&source=web",
            )
            "BING" -> listOf(
                "https://www.bing.com/search?q=$encodedQuery&first=$offset$safeSearchBing",
            )
            "CUSTOM" -> listOf(
                if (customUrlTemplate.contains("{query}"))
                    customUrlTemplate.replace("{query}", encodedQuery)
                else
                    "https://html.duckduckgo.com/html/?q=$encodedQuery$offsetParam$safeSearchDDG$regionParam"
            )
            else -> listOf(
                "https://html.duckduckgo.com/html/?q=$encodedQuery$offsetParam$safeSearchDDG$regionParam",
                "https://duckduckgo.com/lite/?q=$encodedQuery$offsetParam$safeSearchDDG$regionParam",
            )
        }

        for (tryUrl in urls) {
            try {
                val request  = buildRequest(tryUrl)
                val response = withTimeoutOrNull(8_000) { client.newCall(request).execute() } ?: continue

                if (!response.isSuccessful) {
                    if (response.code in listOf(429, 403)) {
                        setCooldown(eng)
                        break
                    }
                    continue
                }

                val html = response.body?.string() ?: continue
                if (html.isBlank()) continue

                // Bot / CAPTCHA detection — mark engine as temporarily blocked
                if (html.contains("detected suspicious activity") ||
                    html.contains("unusual traffic") ||
                    html.contains("captcha", ignoreCase = true) ||
                    html.contains("cf-browser-verification") ||
                    html.contains("To continue, please type") ||
                    html.contains("press and hold")
                ) {
                    setCooldown(eng)
                    continue
                }

                val doc = Jsoup.parse(html, tryUrl)
                val parsed = when (eng) {
                    "GOOGLE" -> parseGoogleResults(doc, adBlockEnabled)
                    "BRAVE"  -> parseBraveResults(doc, adBlockEnabled)
                    "BING"   -> parseBingResults(doc, adBlockEnabled)
                    else     -> parseDuckDuckGoResults(doc, adBlockEnabled)
                }
                if (parsed.isNotEmpty()) return parsed
            } catch (_: Exception) { continue }
        }
        return emptyList()
    }

    // ─── Request builder ─────────────────────────────────────────────────────

    private fun buildRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", randomUA())
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("Cache-Control", "no-cache")
        .header("Sec-Fetch-Dest", "document")
        .header("Sec-Fetch-Mode", "navigate")
        .header("Sec-Fetch-Site", "none")
        .header("Sec-Fetch-User", "?1")
        .header("Upgrade-Insecure-Requests", "1")
        .build()

    // ─── DuckDuckGo parser (enriched) ────────────────────────────────────────

    private fun parseDuckDuckGoResults(doc: org.jsoup.nodes.Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        // HTML version
        doc.select(".results .result, #links .result, .result").forEachIndexed { rank, el ->
            val titleEl   = el.select(".result__a, a.result__title, .result-link").first() ?: return@forEachIndexed
            val snippetEl = el.select(".result__snippet, .result-snippet").first()
            val urlEl     = el.select(".result__url, .result-url").first()

            val rawUrl   = titleEl.attr("href")
            val cleanUrl = cleanDuckDuckGoUrl(rawUrl)
            if (cleanUrl.isBlank() || (cleanUrl.startsWith("/") && !cleanUrl.startsWith("//"))) return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed

            val snippetText = snippetEl?.text()?.trim() ?: ""
            val (date, cleanSnippet) = extractDateFromSnippet(snippetText)

            // Breadcrumb from display URL element
            val breadcrumb = urlEl?.text()?.trim()?.ifBlank { null }

            results += SearchResult(
                title      = titleEl.text().trim(),
                snippet    = cleanSnippet,
                url        = cleanUrl,
                displayUrl = urlEl?.text()?.trim() ?: safeHost(cleanUrl),
                source     = "DDG",
                date       = date,
                breadcrumb = breadcrumb,
                engineRank = rank,
            )
        }

        // Lite/table fallback
        if (results.isEmpty()) {
            val rows = doc.select("table tr")
            var i = 0; var rank = 0
            while (i < rows.size) {
                val row     = rows.getOrNull(i) ?: break
                val titleEl = row.select("a.result-link").first()
                val snip    = rows.getOrNull(i + 1)?.select(".result-snippet")?.first()
                if (titleEl != null && titleEl.text().isNotBlank()) {
                    val rawUrl   = titleEl.attr("href")
                    val cleanUrl = cleanDuckDuckGoUrl(rawUrl)
                    if (cleanUrl.isNotBlank() && !(adBlockEnabled && AdBlockList.isBlocked(cleanUrl))) {
                        val snippetText = snip?.text()?.trim() ?: ""
                        val (date, cleanSnippet) = extractDateFromSnippet(snippetText)
                        results += SearchResult(
                            title      = titleEl.text().trim(),
                            snippet    = cleanSnippet,
                            url        = cleanUrl,
                            displayUrl = safeHost(cleanUrl),
                            source     = "DDG",
                            date       = date,
                            engineRank = rank++,
                        )
                    }
                }
                i += 4
            }
        }
        return results
    }

    // ─── Google parser (enriched) ────────────────────────────────────────────

    private fun parseGoogleResults(doc: org.jsoup.nodes.Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        val selectors = listOf(
            "div.g, div[data-sokoban-feature], div.srKDX",
            "div.MjjYud, div.kvH3C, div.WwS67c",
            "div.hlcw0c, div.tF2Cxc, div.yuRUbf",
            "div[data-async-context] div.g",
        )

        for (selector in selectors) {
            val elements = doc.select(selector)
            if (elements.isEmpty()) continue

            elements.forEachIndexed { rank, el ->
                val titleEl   = el.select("h3, .LC20lb, .vvA7ic").firstOrNull() ?: return@forEachIndexed
                val linkEl    = el.select("a[href]").firstOrNull() ?: return@forEachIndexed
                val snippetEl = el.select(
                    "div[style*='webkit-line-clamp'], span.aCOpRe, div.IsZvec, div[data-sncf], .VwiC3b, div[class*='lyLwlc'], .it_content, .yD7j9"
                ).firstOrNull()

                val rawUrl   = linkEl.attr("href")
                val cleanUrl = when {
                    rawUrl.startsWith("/url?q=") -> rawUrl.removePrefix("/url?q=").substringBefore("&").decodeUrl()
                    rawUrl.startsWith("http")   -> rawUrl
                    else -> return@forEachIndexed
                }
                
                if (cleanUrl.contains("google.com/search") || cleanUrl.contains("accounts.google.com")) return@forEachIndexed
                if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed

                val title = titleEl.text().trim().takeIf { it.isNotBlank() } ?: return@forEachIndexed

                val snippetText = snippetEl?.text()?.trim() ?: ""
                val (date, cleanSnippet) = extractDateFromSnippet(snippetText)

                // Google breadcrumb from <cite>
                val breadcrumb = el.select("cite, .iUh30, .UPmit, .qzEoUe, .Tbwj9b").firstOrNull()
                    ?.text()?.trim()?.ifBlank { null }

                results += SearchResult(
                    title      = title,
                    snippet    = cleanSnippet,
                    url        = cleanUrl,
                    displayUrl = safeHost(cleanUrl),
                    source     = "Google",
                    date       = date,
                    breadcrumb = breadcrumb,
                    engineRank = rank,
                )
            }
            if (results.isNotEmpty()) break
        }

        // gbv=1 basic HTML fallback / mobile fallback
        if (results.isEmpty()) {
            doc.select("li.g, div.r, h3.r, div.ZINbbc, .Gx5Zad").forEachIndexed { rank, el ->
                val linkEl   = el.select("a").firstOrNull() ?: return@forEachIndexed
                val rawUrl   = linkEl.attr("href")
                val cleanUrl = when {
                    rawUrl.startsWith("/url?q=") -> rawUrl.removePrefix("/url?q=").substringBefore("&").decodeUrl()
                    rawUrl.startsWith("http") -> rawUrl
                    else -> return@forEachIndexed
                }
                
                if (cleanUrl.contains("google.com/search")) return@forEachIndexed
                if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed
                
                val titleEl = el.select("h3, .vvA7ic").firstOrNull()
                val title = titleEl?.text() ?: linkEl.text()
                if (title.isBlank()) return@forEachIndexed
                
                val snippetText = el.select("div.st, span.st, .BNeawe.s3v9rd.AP7Wnd, .yD7j9").firstOrNull()?.text()?.trim() ?: ""
                val (date, cleanSnippet) = extractDateFromSnippet(snippetText)

                results += SearchResult(
                    title      = title.trim(),
                    snippet    = cleanSnippet,
                    url        = cleanUrl,
                    displayUrl = safeHost(cleanUrl),
                    source     = "Google",
                    date       = date,
                    engineRank = rank,
                )
            }
        }
        return results
    }

    // ─── Brave parser (enriched) ─────────────────────────────────────────────

    private fun parseBraveResults(doc: org.jsoup.nodes.Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        doc.select(".snippet, #results .snippet").forEachIndexed { rank, el ->
            val titleEl   = el.select(".snippet-title, .title, h2").firstOrNull() ?: return@forEachIndexed
            val linkEl    = el.select("a.url, a[href]").firstOrNull() ?: return@forEachIndexed
            val snippetEl = el.select(".snippet-description, .description, .description-container").firstOrNull()

            val cleanUrl = linkEl.attr("href")
            if (!cleanUrl.startsWith("http")) return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed

            val snippetText = snippetEl?.text()?.trim() ?: ""
            val (date, cleanSnippet) = extractDateFromSnippet(snippetText)

            // Brave shows a URL breadcrumb under the title
            val breadcrumb = el.select(".url, .result-url, cite").firstOrNull()
                ?.text()?.trim()?.ifBlank { null }

            results += SearchResult(
                title      = titleEl.text().trim(),
                snippet    = cleanSnippet,
                url        = cleanUrl,
                displayUrl = safeHost(cleanUrl),
                source     = "Brave",
                date       = date,
                breadcrumb = breadcrumb,
                engineRank = rank,
            )
        }
        return results
    }

    // ─── Bing parser (enriched) ──────────────────────────────────────────────

    private fun parseBingResults(doc: org.jsoup.nodes.Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        doc.select("li.b_algo, div.b_algo").forEachIndexed { rank, el ->
            val titleEl   = el.select("h2 a, h3 a").firstOrNull() ?: return@forEachIndexed
            val snippetEl = el.select("p, div.b_caption p, .b_algoSlug").firstOrNull()

            val cleanUrl = titleEl.attr("href").takeIf { it.startsWith("http") } ?: return@forEachIndexed
            if (adBlockEnabled && AdBlockList.isBlocked(cleanUrl)) return@forEachIndexed

            val snippetText = snippetEl?.text()?.trim() ?: ""
            val (date, cleanSnippet) = extractDateFromSnippet(snippetText)

            // Bing breadcrumb from cite
            val breadcrumb = el.select(".b_attribution cite, cite").firstOrNull()
                ?.text()?.trim()?.ifBlank { null }

            results += SearchResult(
                title      = titleEl.text().trim().takeIf { it.isNotBlank() } ?: safeHost(cleanUrl),
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

    // ─── URL helpers ─────────────────────────────────────────────────────────

    private fun cleanDuckDuckGoUrl(rawUrl: String): String = try {
        when {
            rawUrl.startsWith("//duckduckgo.com/l/?uddg=") -> {
                val enc = rawUrl.substringAfter("uddg=").substringBefore("&")
                java.net.URLDecoder.decode(enc, StandardCharsets.UTF_8.name())
            }
            rawUrl.startsWith("//duckduckgo.com") || rawUrl.startsWith("http") -> rawUrl
            rawUrl.startsWith("/l/?uddg=") -> {
                val enc = rawUrl.substringAfter("uddg=").substringBefore("&")
                java.net.URLDecoder.decode(enc, StandardCharsets.UTF_8.name())
            }
            else -> rawUrl
        }
    } catch (_: Exception) { rawUrl }

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
}