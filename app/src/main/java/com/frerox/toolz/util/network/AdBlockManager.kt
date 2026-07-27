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
         * v3 — comprehensive hosts (IPv4/v6, multi-domain lines) & ABP rule parser.
         */
        private const val CACHE_VERSION = 3
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
     *  - Hosts file format (0.0.0.0, 127.0.0.1, ::1, ::) including multi-domain lines
     *  - ABP network rules (||domain^, ||domain^$options, @@||exception^)
     *  - ABP exception rules (@@||safesite.com^)
     *
     * Explicitly skips:
     *  - Comment lines (starting with !, #, [, ;)
     *  - CSS element-hiding / procedural / snippet rules (##, #@#, #?#, #$#)
     */
    fun parseBlocklistText(text: String): Set<String> {
        val rules = mutableSetOf<String>()
        text.lineSequence().forEach { line ->
            var trimmed = line.trim()

            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!") || 
                trimmed.startsWith("[") || trimmed.startsWith(";")) return@forEach

            // Skip CSS element-hiding and procedural rules
            if (trimmed.contains("##") || trimmed.contains("#@#") || 
                trimmed.contains("#?#") || trimmed.contains("#$#")) return@forEach

            // Strip inline comments on hosts / rule lines (handles both spaces and tabs before # or !)
            val commentIdx = indexOfComment(trimmed)
            if (commentIdx != -1) {
                trimmed = trimmed.substring(0, commentIdx).trim()
            }

            if (trimmed.isEmpty()) return@forEach

            // Check if hosts file entry (0.0.0.0, 127.0.0.1, ::1, ::)
            if (isHostsLine(trimmed)) {
                val parts = trimmed.split(Regex("\\s+"))
                // Skip the IP (parts[0]), collect all domain targets on the line
                for (i in 1 until parts.size) {
                    val domain = parts[i].lowercase().trim()
                    if (domain.contains(".") && domain != "localhost" && domain != "broadcasthost" && !domain.startsWith("#")) {
                        rules.add(domain)
                    }
                }
            } else {
                rules.add(trimmed)
            }
        }
        return rules
    }

    private fun isHostsLine(line: String): Boolean {
        return line.startsWith("0.0.0.0") || line.startsWith("127.0.0.1") || 
               line.startsWith("::1") || line.startsWith("::")
    }

    private fun indexOfComment(line: String): Int {
        val hashIdx = line.indexOf('#')
        val exclIdx = line.indexOf('!')
        val candidates = listOf(hashIdx, exclIdx).filter { it > 0 }
        return candidates.minOrNull() ?: -1
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
