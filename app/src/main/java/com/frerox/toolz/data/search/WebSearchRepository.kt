package com.frerox.toolz.data.search

import com.frerox.toolz.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.InetAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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

    private suspend fun getDnsClient(): OkHttpClient {
        val provider = settingsRepository.searchDnsProvider.first()
        val customDns = settingsRepository.searchCustomDns.first()

        val dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return try {
                    when (provider) {
                        "ADGUARD" -> InetAddress.getAllByName(hostname).toList() // Simplified, real DoH would be better
                        "CLOUDFLARE" -> InetAddress.getAllByName(hostname).toList()
                        "GOOGLE" -> InetAddress.getAllByName(hostname).toList()
                        "CUSTOM" -> if (customDns.isNotEmpty()) listOf(InetAddress.getByName(customDns)) else Dns.SYSTEM.lookup(hostname)
                        else -> Dns.SYSTEM.lookup(hostname)
                    }
                } catch (e: Exception) {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
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

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://html.duckduckgo.com/html/?q=$encodedQuery"
            val client = getDnsClient()
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val html = response.body?.string() ?: return@withContext emptyList()
            val doc = Jsoup.parse(html, url)

            val results = mutableListOf<SearchResult>()
            // Support both standard and mobile layouts
            val elements = doc.select(".result, .web-result")

            for (element in elements) {
                val titleElement = element.select(".result__title .result__a, .result__a").first()
                val snippetElement = element.select(".result__snippet, .result__body, .snippet").first()
                val urlElement = element.select(".result__url, .result__extras__url").first()

                if (titleElement != null) {
                    val rawUrl = titleElement.attr("href")
                    if (rawUrl.isNullOrBlank() || rawUrl.startsWith("/")) continue
                    
                    val cleanUrl = cleanDuckDuckGoUrl(rawUrl)
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
