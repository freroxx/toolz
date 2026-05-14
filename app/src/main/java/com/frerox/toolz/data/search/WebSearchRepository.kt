package com.frerox.toolz.data.search

import com.frerox.toolz.data.settings.SettingsRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import org.jsoup.Jsoup
import java.net.InetAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchResult(
    val title: String,
    val snippet: String,
    val url: String,
    val displayUrl: String
)

@Singleton
class WebSearchRepository @Inject constructor(
    private val searchDao: SearchDao,
    private val settingsRepository: SettingsRepository,
    private val moshi: Moshi
) {
    val history = searchDao.getRecentHistory()
    val bookmarks = searchDao.getBookmarks()
    val quickLinks = searchDao.getQuickLinks()

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var cachedDnsClient: OkHttpClient? = null
    private var lastDnsProvider: String? = null
    private var lastCustomDns: String? = null

    private val adBlockDomains = setOf(
        // General Ads & Tracking
        "doubleclick.net", "google-analytics.com", "facebook.com", "amazon-adsystem.com",
        "adnxs.com", "criteo.com", "taboola.com", "outbrain.com", "scorecardresearch.com",
        "quantserve.com", "adsrvr.org", "rubiconproject.com", "pubmatic.com", "openx.net",
        "advertising.com", "yieldmo.com", "moatads.com", "adtech.de", "advertising.com",
        "adform.net", "adskeeper.co.uk", "mgid.com", "revcontent.com", "outbrainimg.com",
        "googletagservices.com", "googletagmanager.com", "app-measurement.com",
        "analytics.google.com", "clicky.com", "hotjar.com", "mixpanel.com", "segment.io",
        "ad-delivery.net", "adgrx.com", "adhigh.net", "adlightning.com", "popcash.net",
        "ad-score.com", "ad-sys.com", "ad-traffic.com", "adsco.re", "exelator.com",
        "pixel.facebook.com", "static.ads-twitter.com", "ads-api.twitter.com",
        "pagead2.googlesyndication.com", "ad.doubleclick.net", "clarity.ms",
        "smartadserver.com", "openx.com", "360yield.com", "mookie1.com", "sitescout.com",
        "adzerk.net", "adpushup.com", "media.net", "buysellads.com", "carbonads.net",
        "coingecko.com", "coinmarketcap.com", "brave.com", "duckduckgo.com/t.js",
        // Analytics & Tracking
        "newrelic.com", "datadoghq.com", "sentry.io", "loggly.com", "intercom.io",
        "drift.com", "fullstory.com", "mouseflow.com", "inspectlet.com", "luckyorange.com"
    )

    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    )

    private fun isAdDomain(url: String): Boolean {
        return try {
            val host = java.net.URI(url).host ?: return false
            adBlockDomains.any { host == it || host.endsWith(".$it") }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun getDnsClient(): OkHttpClient {
        val provider = settingsRepository.searchDnsProvider.first()
        val customDnsPrimary = settingsRepository.searchCustomDns.first()

        if (cachedDnsClient != null && provider == lastDnsProvider && (provider != "CUSTOM" || customDnsPrimary == lastCustomDns)) {
            return cachedDnsClient!!
        }

        val dns: Dns = try {
            when (provider) {
                "ADGUARD" -> DnsOverHttps.Builder().client(baseClient)
                    .url("https://dns.adguard-dns.com/dns-query".toHttpUrl())
                    .build()
                "ADGUARD_FAMILY" -> DnsOverHttps.Builder().client(baseClient)
                    .url("https://dns-family.adguard-dns.com/dns-query".toHttpUrl())
                    .build()
                "CLOUDFLARE" -> DnsOverHttps.Builder().client(baseClient)
                    .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                    .build()
                "CLOUDFLARE_FAMILY" -> DnsOverHttps.Builder().client(baseClient)
                    .url("https://family.cloudflare-dns.com/dns-query".toHttpUrl())
                    .build()
                "GOOGLE" -> DnsOverHttps.Builder().client(baseClient)
                    .url("https://dns.google/dns-query".toHttpUrl())
                    .build()
                "QUAD9" -> DnsOverHttps.Builder().client(baseClient)
                    .url("https://dns.quad9.net/dns-query".toHttpUrl())
                    .build()
                "OPENDNS" -> DnsOverHttps.Builder().client(baseClient)
                    .url("https://doh.opendns.com/dns-query".toHttpUrl())
                    .build()
                "CLEANBROWSING" -> DnsOverHttps.Builder().client(baseClient)
                    .url("https://doh.cleanbrowsing.org/doh/family-filter/".toHttpUrl())
                    .build()
                "CUSTOM" -> {
                    if (customDnsPrimary.startsWith("http")) {
                        DnsOverHttps.Builder().client(baseClient)
                            .url(customDnsPrimary.toHttpUrl())
                            .build()
                    } else {
                        Dns.SYSTEM
                    }
                }
                else -> Dns.SYSTEM
            }
        } catch (e: Exception) {
            Dns.SYSTEM
        }
        
        lastDnsProvider = provider
        lastCustomDns = customDnsPrimary
        cachedDnsClient = baseClient.newBuilder().dns(dns).build()
        return cachedDnsClient!!
    }

    suspend fun addHistory(query: String) {
        searchDao.insertHistory(SearchHistoryEntry(query = query))
    }

    suspend fun deleteHistory(id: Long) {
        searchDao.deleteHistory(id)
    }

    suspend fun clearHistory() {
        searchDao.clearHistory()
    }

    suspend fun addBookmark(title: String, url: String) {
        searchDao.insertBookmark(BookmarkEntry(title = title, url = url))
    }

    suspend fun removeBookmark(url: String) {
        searchDao.deleteBookmarkByUrl(url)
    }

    suspend fun isBookmarked(url: String) = searchDao.isBookmarked(url)

    suspend fun addQuickLink(title: String, url: String) {
        searchDao.insertQuickLink(QuickLinkEntry(title = title, url = url))
    }

    suspend fun removeQuickLink(id: Long) {
        searchDao.deleteQuickLink(id)
    }

    suspend fun updateBookmark(id: Long, title: String, url: String) {
        searchDao.updateBookmark(id, title, url)
    }

    suspend fun updateQuickLink(id: Long, title: String, url: String) {
        searchDao.updateQuickLink(id, title, url)
    }

    suspend fun updateQuickLinks(entries: List<QuickLinkEntry>) {
        searchDao.updateQuickLinks(entries)
    }

    suspend fun fetchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://duckduckgo.com/ac/?q=$encodedQuery&type=list"
            val request = Request.Builder().url(url)
                .header("User-Agent", userAgents.random())
                .build()
            
            val client = getDnsClient()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            
            val json = response.body?.string() ?: return@withContext emptyList()
            val type = Types.newParameterizedType(List::class.java, String::class.java)
            val adapter = moshi.adapter<List<String>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchWebsiteContent(url: String): String = withContext(Dispatchers.IO) {
        try {
            val client = getDnsClient()
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext "Error: Failed to fetch $url (HTTP ${response.code})"

            val html = response.body?.string() ?: return@withContext "Error: Empty response from $url"
            val doc = Jsoup.parse(html, url)
            
            // Remove unnecessary elements
            doc.select("script, style, nav, footer, header, aside, .ads, .sidebar").remove()
            
            // Get main content candidates
            val content = doc.select("article, main, .content, .post-content, #content, .article-body").firstOrNull() 
                ?: doc.body()
            
            val text = content?.text() ?: ""
            if (text.length > 3000) text.take(3000) + "... [truncated]" else text
        } catch (e: Exception) {
            "Error fetching website content: ${e.message}"
        }
    }

    suspend fun search(query: String, offset: Int = 0): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val adBlockEnabled = settingsRepository.searchAdBlockEnabled.first()
            val engine = settingsRepository.searchEngine.first()
            val safeSearch = settingsRepository.searchSafeSearch.first()
            val region = settingsRepository.searchRegion.first()
            val customUrlTemplate = settingsRepository.searchCustomEngineUrl.first()
            
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            
            // DuckDuckGo HTML pagination parameters
            val offsetParam = if (offset > 0) "&s=$offset&dc=$offset&v=l" else ""
            val safeSearchParamDDG = if (safeSearch) "&kp=1" else "&kp=-1"
            val safeSearchParamGoogle = if (safeSearch) "&safe=active" else "&safe=off"
            val safeSearchParamBing = if (safeSearch) "&adlt=strict" else "&adlt=off"
            val regionParam = if (region.isNotBlank() && region != "wt-wt") "&kl=$region" else ""
            
            val enginesToSearch = when (engine) {
                "META" -> listOf("GOOGLE", "DUCKDUCKGO", "BING")
                else -> listOf(engine)
            }

            val resultsMap = mutableMapOf<String, MutableList<SearchResult>>()
            val client = getDnsClient()

            for (currentEngine in enginesToSearch) {
                val urlsToTry = when (currentEngine) {
                    "GOOGLE" -> listOf(
                        "https://www.google.com/search?q=$encodedQuery&start=$offset$safeSearchParamGoogle",
                        "https://www.google.com/search?q=$encodedQuery&start=$offset$safeSearchParamGoogle&gbv=1"
                    )
                    "BING" -> listOf(
                        "https://www.bing.com/search?q=$encodedQuery&first=$offset$safeSearchParamBing"
                    )
                    "CUSTOM" -> {
                        if (customUrlTemplate.contains("{query}")) {
                            listOf(customUrlTemplate.replace("{query}", encodedQuery))
                        } else {
                            listOf("https://html.duckduckgo.com/html/?q=$encodedQuery$offsetParam$safeSearchParamDDG$regionParam")
                        }
                    }
                    else -> listOf( // Default to DDG
                        "https://html.duckduckgo.com/html/?q=$encodedQuery$offsetParam$safeSearchParamDDG$regionParam",
                        "https://duckduckgo.com/lite/?q=$encodedQuery$offsetParam$safeSearchParamDDG$regionParam"
                    )
                }

                resultsMap[currentEngine] = mutableListOf()

                for (tryUrl in urlsToTry) {
                    try {
                        val request = Request.Builder().url(tryUrl)
                            .header("User-Agent", userAgents.random())
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                            .header("Accept-Language", "en-US,en;q=0.9")
                            .header("Cache-Control", "max-age=0")
                            .header("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                            .header("Sec-Ch-Ua-Mobile", "?0")
                            .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                            .header("Sec-Fetch-Dest", "document")
                            .header("Sec-Fetch-Mode", "navigate")
                            .header("Sec-Fetch-Site", "none")
                            .header("Sec-Fetch-User", "?1")
                            .header("Upgrade-Insecure-Requests", "1")
                            .header("X-Requested-With", "com.android.chrome")
                            .build()
                        
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val html = response.body?.string() ?: ""
                            if (html.isNotBlank()) {
                                val doc = Jsoup.parse(html, tryUrl)
                                val engineResults = when (currentEngine) {
                                    "GOOGLE" -> parseGoogleResults(doc, adBlockEnabled)
                                    "BING" -> parseBingResults(doc, adBlockEnabled)
                                    else -> parseDuckDuckGoResults(doc, adBlockEnabled)
                                }
                                if (engineResults.isNotEmpty()) {
                                    resultsMap[currentEngine]?.addAll(engineResults)
                                    break // Found results for this engine
                                } else if (html.contains("detected suspicious activity") || html.contains("To continue, please type the characters")) {
                                    // Google bot detection, try next URL/UA
                                    continue
                                }
                            }
                        } else if (response.code == 429 || response.code == 403) {
                            // Rate limited or forbidden, maybe log or try next URL/UA
                            continue
                        }
                    } catch (e: Exception) {
                        continue
                    }
                }
            }

            // Interleave results for META search
            val interleavedResults = mutableListOf<SearchResult>()
            if (engine == "META") {
                var index = 0
                while (true) {
                    var added = false
                    for (engineName in enginesToSearch) {
                        val engineResults = resultsMap[engineName]
                        if (engineResults != null && index < engineResults.size) {
                            interleavedResults.add(engineResults[index])
                            added = true
                        }
                    }
                    if (!added) break
                    index++
                }
            } else {
                resultsMap.values.forEach { interleavedResults.addAll(it) }
            }

            // Deduplicate and return
            interleavedResults.distinctBy { it.url }.take(50)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseDuckDuckGoResults(doc: org.jsoup.nodes.Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        
        // DuckDuckGo HTML Version Selectors
        val ddgHtmlResults = doc.select(".results .result, #links .result, .result")
        for (element in ddgHtmlResults) {
            val titleElement = element.select(".result__title .result__a, .result__a, a.result__title, .result-link, .result__title a").first()
            val snippetElement = element.select(".result__snippet, .result-snippet, .result__body").first()
            val urlElement = element.select(".result__url, .result-url").first()

            if (titleElement != null && titleElement.text().isNotBlank()) {
                val rawUrl = titleElement.attr("href")
                val cleanUrl = cleanDuckDuckGoUrl(rawUrl)
                if (cleanUrl.isBlank() || (cleanUrl.startsWith("/") && !cleanUrl.startsWith("//") && !cleanUrl.startsWith("http"))) continue
                if (adBlockEnabled && isAdDomain(cleanUrl)) continue

                results.add(
                    SearchResult(
                        title = titleElement.text().trim(),
                        snippet = snippetElement?.text()?.trim() ?: "",
                        url = cleanUrl,
                        displayUrl = urlElement?.text()?.trim() ?: try { java.net.URI(cleanUrl).host ?: cleanUrl } catch(_:Exception) { cleanUrl }
                    )
                )
            }
        }

        // DuckDuckGo Lite Version Selectors (Table based)
        if (results.isEmpty()) {
            val ddgLiteResults = doc.select("table tr")
            for (i in 0 until ddgLiteResults.size step 4) {
                val row = ddgLiteResults.getOrNull(i) ?: break
                val titleElement = row.select("a.result-link").first()
                val snippetRow = ddgLiteResults.getOrNull(i + 1)
                val snippetElement = snippetRow?.select(".result-snippet")?.first()
                
                if (titleElement != null && titleElement.text().isNotBlank()) {
                    val rawUrl = titleElement.attr("href")
                    val cleanUrl = cleanDuckDuckGoUrl(rawUrl)
                    if (cleanUrl.isBlank()) continue
                    if (adBlockEnabled && isAdDomain(cleanUrl)) continue

                    results.add(
                        SearchResult(
                            title = titleElement.text().trim(),
                            snippet = snippetElement?.text()?.trim() ?: "",
                            url = cleanUrl,
                            displayUrl = try { java.net.URI(cleanUrl).host ?: cleanUrl } catch(_:Exception) { cleanUrl }
                        )
                    )
                }
            }
        }
        return results
    }

    private fun parseGoogleResults(doc: org.jsoup.nodes.Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        // Google Search Results Selectors (Updated for more robustness)
        val googleResults = doc.select(".g, div.tF2Cxc, div.MjjYud, div[data-hveid], div.ZIN6ue, .Ww46ce, .Gx5Zad, .fK779e, .j7vYIc, .yuRUbf")
        for (element in googleResults) {
            val titleElement = element.select("h3, .vv79be, .X79v8b, .LC20lb").first()
            val linkElement = element.select("a[href]").first()
            val snippetElement = element.select(".VwiC3b, .yXB77d, .st, .kb09N, .LC20lb, .V86pEc, .BNeawe, .yD709e, .MUwY0b").first()

            if (titleElement != null && linkElement != null) {
                var rawUrl = linkElement.attr("href")
                
                // Handle cases where href is nested or different
                if (rawUrl.isEmpty() || rawUrl == "#" || rawUrl.startsWith("javascript:")) {
                    val nestedLink = element.select("a[href]").firstOrNull { 
                        val href = it.attr("href")
                        (href.startsWith("http") || href.startsWith("/url?")) && !href.contains("google.com/search")
                    }
                    rawUrl = nestedLink?.attr("href") ?: rawUrl
                }

                val cleanUrl = cleanGoogleUrl(rawUrl)
                if (cleanUrl.isBlank() || !cleanUrl.startsWith("http") || cleanUrl.contains("google.com/search")) continue
                if (adBlockEnabled && isAdDomain(cleanUrl)) continue
                
                val title = titleElement.text().trim()
                if (title.isEmpty()) continue

                results.add(
                    SearchResult(
                        title = title,
                        snippet = snippetElement?.text()?.trim() ?: "",
                        url = cleanUrl,
                        displayUrl = try { java.net.URI(cleanUrl).host ?: cleanUrl } catch(_:Exception) { cleanUrl }
                    )
                )
            }
        }
        return results
    }

    private fun cleanGoogleUrl(url: String): String {
        return if (url.startsWith("/url?q=")) {
            try {
                val encodedUrl = url.substringAfter("/url?q=").substringBefore("&")
                URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.name())
            } catch (e: Exception) {
                url
            }
        } else {
            cleanDuckDuckGoUrl(url)
        }
    }

    private fun parseBingResults(doc: org.jsoup.nodes.Document, adBlockEnabled: Boolean): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val bingResults = doc.select(".b_algo")
        for (element in bingResults) {
            val titleElement = element.select("h2 a").first()
            val snippetElement = element.select(".b_caption p, .b_algoSnippet").first()

            if (titleElement != null) {
                val rawUrl = titleElement.attr("href")
                val cleanUrl = cleanDuckDuckGoUrl(rawUrl)
                if (cleanUrl.isBlank() || !cleanUrl.startsWith("http")) continue
                if (adBlockEnabled && isAdDomain(cleanUrl)) continue

                results.add(
                    SearchResult(
                        title = titleElement.text().trim(),
                        snippet = snippetElement?.text()?.trim() ?: "",
                        url = cleanUrl,
                        displayUrl = try { java.net.URI(cleanUrl).host ?: cleanUrl } catch(_:Exception) { cleanUrl }
                    )
                )
            }
        }
        return results
    }

    private fun cleanDuckDuckGoUrl(url: String): String {
        return if (url.contains("uddg=")) {
            val parts = url.split("uddg=")
            if (parts.size > 1) {
                val encodedUrl = parts[1].split("&").first()
                URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.name())
            } else {
                url
            }
        } else if (url.startsWith("//")) {
            "https:$url"
        } else {
            url
        }
    }
}
