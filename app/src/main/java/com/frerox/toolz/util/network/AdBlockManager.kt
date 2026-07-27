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

            // 3. Background-sync imported lists if enabled and cache is stale (>24h)
            val enabledLists = settingsRepository.searchEnabledImportedLists.first()
            if (enabledLists.isNotEmpty()) {
                val file = File(application.filesDir, "imported_blocklist.txt")
                val isStale = !file.exists() || (System.currentTimeMillis() - file.lastModified()) > 86_400_000L
                if (isStale) {
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
     */
    fun parseBlocklistText(text: String): Set<String> {
        val rules = mutableSetOf<String>()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!") || trimmed.startsWith("[")) return@forEach
            
            // Handle both plain domains, hosts file format (0.0.0.0 domain), and basic ABP patterns (||domain^)
            val rule = if (trimmed.contains("#")) trimmed.substringBefore("#").trim() else trimmed
            
            if (rule.startsWith("0.0.0.0") || rule.startsWith("127.0.0.1")) {
                val parts = rule.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val domain = parts[1].lowercase().trim()
                    if (domain.contains(".") && domain != "localhost") {
                        rules.add(domain)
                    }
                }
            } else {
                // Keep the rule as is for AdBlockList to clean/categorize (preserves paths)
                if (rule.isNotBlank()) rules.add(rule)
            }
        }
        return rules
    }


    private suspend fun loadImportedDomains() {
        val file = File(application.filesDir, "imported_blocklist.txt")
        if (file.exists()) {
            withContext(Dispatchers.IO) {
                try {
                    val domains = file.readLines().toSet()
                    AdBlockList.updateImportedList(domains)
                    android.util.Log.d("AdBlockManager", "Persistence: Loaded ${domains.size} rules from disk")
                } catch (e: Exception) {
                    android.util.Log.e("AdBlockManager", "Failed to load persistence", e)
                }
            }
        } else {
            AdBlockList.refreshIndex() // Ensure static rules are ready at least
        }
    }

    suspend fun syncImportedLists(enabledLists: Set<String>, fetcher: suspend (String) -> Set<String>) {
        if (enabledLists.isEmpty()) {
            withContext(Dispatchers.IO) {
                File(application.filesDir, "imported_blocklist.txt").delete()
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
                val rules = fetcher(url)
                if (rules.isNotEmpty()) {
                    allRules.addAll(rules)
                    successCount++
                }
            } catch (e: Exception) {
                android.util.Log.e("AdBlockManager", "Sync failed for list $id", e)
            }
        }
        
        // Only persist and update if at least one list was successfully fetched
        // or if we explicitly enabled lists that returned nothing (unlikely for ad block lists)
        if (successCount > 0 || allRules.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(application.filesDir, "imported_blocklist.txt")
                    file.writeText(allRules.joinToString("\n"))
                    
                    // Update singleton & DataStore count
                    AdBlockList.updateImportedList(allRules)
                    settingsRepository.setSearchAdBlockImportedCount(allRules.size)
                    android.util.Log.d("AdBlockManager", "Sync: Success. ${allRules.size} rules active.")
                } catch (e: Exception) {
                    android.util.Log.e("AdBlockManager", "Failed to save sync results", e)
                }
            }
        } else {
            android.util.Log.w("AdBlockManager", "Sync: No rules fetched. local cache preserved.")
        }
    }
}
