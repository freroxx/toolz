package com.frerox.toolz.data.search

import com.frerox.toolz.data.settings.SettingsRepository
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
    private val settingsRepository: SettingsRepository
) {
    val history = searchDao.getRecentHistory()
    val bookmarks = searchDao.getBookmarks()
    val quickLinks = searchDao.getQuickLinks()

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val adBlockDomains = setOf(
        "doubleclick.net", "google-analytics.com", "facebook.com", "amazon-adsystem.com",
        "adnxs.com", "criteo.com", "taboola.com", "outbrain.com", "scorecardresearch.com",
        "quantserve.com", "adsrvr.org", "rubiconproject.com", "pubmatic.com", "openx.net"
    )

    private fun isAdDomain(url: String): Boolean {
        return try {
            val host = java.net.URI(url).host ?: return false
            adBlockDomains.any { host == it || host.endsWith(".\$it") }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun getDnsClient(): OkHttpClient {
        val provider = settingsRepository.searchDnsProvider.first()
        val customDnsPrimary = settingsRepository.searchCustomDns.first()
        val customDnsSecondary = settingsRepository.searchCustomDnsSecondary.first()

        val dns: Dns = try {
            when (provider) {
                "ADGUARD" -> DnsOverHttps.Builder().client(baseClient)
                    .url("https://dns.adguard-dns.com/dns-query".toHttpUrl())
                    .build()
                "CLOUDFLARE" -> DnsOverHttps.Builder().client(baseClient)
                    .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
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
                "CUSTOM" -> {
                    // Expect custom DoH URL (e.g. https://my-dns/dns-query).
                    // If primary is not a valid URL, try secondary, else fallback to system.
                    val validUrl = listOf(customDnsPrimary, customDnsSecondary).firstOrNull { it.startsWith("http") }
                    if (validUrl != null) {
                        DnsOverHttps.Builder().client(baseClient)
                            .url(validUrl.toHttpUrl())
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
        
        return baseClient.newBuilder().dns(dns).build()
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

    suspend fun search(query: String, offset: Int = 0): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val adBlockEnabled = settingsRepository.searchAdBlockEnabled.first()
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val offsetParam = if (offset > 0) "&s=$offset&dc=$offset" else ""
            val url = "https://html.duckduckgo.com/html/?q=$encodedQuery$offsetParam"
            val client = getDnsClient()
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Referer", "https://html.duckduckgo.com/")
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val html = response.body?.string() ?: return@withContext emptyList()
            val doc = Jsoup.parse(html, url)

            val results = mutableListOf<SearchResult>()
            // Support multiple layouts: DuckDuckGo HTML, mobile, and variations
            val elements = doc.select(".result, .web-result, .links_main")

            for (element in elements) {
                // Try multiple selectors for each part
                val titleElement = element.select(".result__title .result__a, .result__a, a.result__title").first()
                val snippetElement = element.select(".result__snippet, .result__body, .snippet, .result__body__snippet").first()
                val urlElement = element.select(".result__url, .result__extras__url, .result__url__domain").first()

                if (titleElement != null) {
                    val rawUrl = titleElement.attr("href")
                    if (rawUrl.isNullOrBlank() || rawUrl.startsWith("/")) continue
                    
                    val cleanUrl = cleanDuckDuckGoUrl(rawUrl)
                    if (adBlockEnabled && isAdDomain(cleanUrl)) continue
                    // Ensure we don't have duplicate results
                    if (results.any { it.url == cleanUrl }) continue

                    val snippet = snippetElement?.text() ?: ""
                    
                    results.add(
                        SearchResult(
                            title = titleElement.text(),
                            snippet = snippet,
                            url = cleanUrl,
                            displayUrl = urlElement?.text() ?: cleanUrl
                        )
                    )
                }
            }
            // Fallback: If no results found with specific selectors, try a broader approach
            if (results.isEmpty()) {
                val links = doc.select("a.result__a")
                for (link in links) {
                    val url = cleanDuckDuckGoUrl(link.attr("href"))
                    if (url.startsWith("http")) {
                        results.add(SearchResult(link.text(), "", url, url))
                    }
                }
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
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
