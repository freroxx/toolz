package com.frerox.toolz.util.network

import android.app.Application
import com.frerox.toolz.data.browser.AdBlockList
import com.frerox.toolz.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdBlockManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val application: Application
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /**
         * Bump this version whenever the parser logic changes in a way that
         * makes previously cached data invalid. On mismatch the cache file
         * is treated as stale and a fresh network sync is performed.
         * v2 — fixed CSS element-hiding rule contamination in parseBlocklistText.
         */
        private const val CACHE_VERSION = 2
        private const val VERSION_HEADER = "#cache-version=$CACHE_VERSION"
        private const val CACHE_FILE = "imported_blocklist.txt"
    }

    init {
        // Startup Sequence: Fast & Guaranteed
        scope.launch {
            // 1. Ensure engine is ready with static rules immediately
            AdBlockList.refreshIndex()

            // 2. Load disk-based rules (persistent community lists)
            loadImportedDomains()
            
            // 3. Load current custom settings
            val customBlocked = settingsRepository.searchAdBlockBlocklists.first()
            val customAllowed = settingsRepository.searchAdBlockAllowlists.first()
            AdBlockList.updateCustomLists(customBlocked, customAllowed)

            // 4. Background-sync imported lists if enabled and cache is stale (>24h) or version-mismatched
            val enabledLists = settingsRepository.searchEnabledImportedLists.first()
            if (enabledLists.isNotEmpty()) {
                val file = File(application.filesDir, CACHE_FILE)
                val isAged = !file.exists() || (System.currentTimeMillis() - file.lastModified()) > 86_400_000L
                val isWrongVersion = !file.exists() || runCatching {
                    file.bufferedReader().readLine() != VERSION_HEADER
                }.getOrDefault(true)
                if (isAged || isWrongVersion) {
                    android.util.Log.d("AdBlockManager", "Startup: cache stale or version mismatch — re-syncing")
                    try {
                        syncImportedLists(enabledLists) { url -> fetchListFromNetwork(url) }
                    } catch (e: Exception) {
                        android.util.Log.w("AdBlockManager", "Startup sync failed", e)
                    }
                }
            }
            
            // 4. Observe changes for live updates
            combine(
                settingsRepository.searchAdBlockBlocklists,
                settingsRepository.searchAdBlockAllowlists
            ) { blocked, allowed ->
                AdBlockList.updateCustomLists(blocked, allowed)
            }.collect {}
        }
    }

    /**
     * Fetches a blocklist from network with a long timeout for large lists.
     * Used for both startup sync and user-triggered sync.
     */
    suspend fun fetchListFromNetwork(url: String): Set<String> = withContext(Dispatchers.IO) {
        try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Accept", "text/plain, application/octet-stream, */*")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptySet()
            val body = response.body?.string() ?: return@withContext emptySet()
            parseBlocklistText(body)
        } catch (e: Exception) {
            android.util.Log.e("AdBlockManager", "Failed to fetch $url", e)
            emptySet()
        }
    }

    /**
     * Parses raw blocklist text (hosts, ABP, plain domain) into a Set<String>.
     *
     * Correctly handles:
     *  - Plain domain lists (one domain per line)
     *  - Hosts file format (0.0.0.0 domain or 127.0.0.1 domain)
     *  - ABP network rules (||domain^, ||domain^$options, @@||exception^)
     *  - ABP exception rules (@@||safesite.com^)
     *
     * Explicitly skips (returns nothing for):
     *  - Comment lines (starting with !, #, [)
     *  - CSS element-hiding rules (containing ##, #@#, #?#) — these were the
     *    primary cause of community lists "not working": e.g. 'example.com##.ad'
     *    was being stripped to 'example.com' and saved as a domain block rule,
     *    which both polluted the blocklist and incorrectly blocked legitimate sites.
     */
    fun parseBlocklistText(text: String): Set<String> {
        val rules = mutableSetOf<String>()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()

            // Skip blank lines and comment-only lines
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!") || trimmed.startsWith("[")) return@forEach

            // ── Skip CSS element-hiding and snippet rules BEFORE any '#' stripping ──
            // These are NOT network rules and must not be parsed as domain blocks.
            // e.g. "example.com##.ad-banner", "example.com#@#.ad", "example.com#?#.ad"
            if (trimmed.contains("##") || trimmed.contains("#@#") || trimmed.contains("#?#")) return@forEach

            // Strip inline comments that appear after the rule (safe now that CSS ## is excluded)
            val rule = trimmed.substringBefore(" !").substringBefore(" #").trim()

            if (rule.startsWith("0.0.0.0") || rule.startsWith("127.0.0.1")) {
                // Hosts file format: "0.0.0.0 ads.example.com" or "127.0.0.1 ads.example.com"
                val parts = rule.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val domain = parts[1].lowercase().trim()
                    if (domain.contains(".") && domain != "localhost") {
                        rules.add(domain)
                    }
                }
            } else {
                // ABP network rules (||domain^, @@||exception^) and plain domains.
                // Pass raw so AdBlockList.cleanRule() can properly categorize them.
                if (rule.isNotBlank()) rules.add(rule)
            }
        }
        return rules
    }


    private suspend fun loadImportedDomains() {
        val file = File(application.filesDir, CACHE_FILE)
        if (file.exists()) {
            withContext(Dispatchers.IO) {
                try {
                    // Skip the version-header line when reading rules
                    val lines = file.readLines()
                    val domains = lines
                        .filter { it.isNotBlank() && it != VERSION_HEADER }
                        .toSet()
                    if (domains.isNotEmpty()) {
                        AdBlockList.updateImportedList(domains)
                        android.util.Log.d("AdBlockManager", "Persistence: Loaded ${domains.size} rules from disk")
                    } else {
                        android.util.Log.w("AdBlockManager", "Persistence: $CACHE_FILE is empty")
                        AdBlockList.refreshIndex()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AdBlockManager", "Failed to load persistence", e)
                    AdBlockList.refreshIndex()
                }
            }
        } else {
            android.util.Log.d("AdBlockManager", "Persistence: No $CACHE_FILE found. Using static rules.")
            AdBlockList.refreshIndex()
        }
    }

    suspend fun syncImportedLists(enabledLists: Set<String>, fetcher: suspend (String) -> Set<String>) {
        if (enabledLists.isEmpty()) {
            withContext(Dispatchers.IO) {
                File(application.filesDir, CACHE_FILE).delete()
            }
            AdBlockList.updateImportedList(emptySet())
            settingsRepository.setSearchAdBlockImportedCount(0)
            return
        }

        val allRules = mutableSetOf<String>()
        var successCount = 0
        android.util.Log.d("AdBlockManager", "Sync: Processing ${enabledLists.size} lists")
        
        enabledLists.forEach { id ->
            val url = com.frerox.toolz.ui.screens.search.AdBlockSettingsViewModel.POPULAR_LISTS[id] ?: return@forEach
            try {
                android.util.Log.d("AdBlockManager", "Sync: Fetching list $id from $url")
                val rules = fetcher(url)
                if (rules.isNotEmpty()) {
                    allRules.addAll(rules)
                    successCount++
                    android.util.Log.d("AdBlockManager", "Sync: List $id fetched. Got ${rules.size} rules.")
                } else {
                    android.util.Log.w("AdBlockManager", "Sync: List $id returned 0 rules.")
                }
            } catch (e: Exception) {
                android.util.Log.e("AdBlockManager", "Sync failed for list $id", e)
            }
        }
        
        // Only persist and update if at least one list was successfully fetched
        if (successCount > 0 || allRules.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(application.filesDir, CACHE_FILE)
                    // Prepend version header so startup can detect stale caches
                    file.writeText(VERSION_HEADER + "\n" + allRules.joinToString("\n"))
                    
                    // Update singleton & DataStore count
                    AdBlockList.updateImportedList(allRules)
                    settingsRepository.setSearchAdBlockImportedCount(allRules.size)
                    android.util.Log.d("AdBlockManager", "Sync: Success. ${allRules.size} rules parsed and active.")
                } catch (e: Exception) {
                    android.util.Log.e("AdBlockManager", "Failed to save sync results", e)
                }
            }
        } else {
            android.util.Log.w("AdBlockManager", "Sync: No rules fetched. local cache preserved.")
        }
    }
}
