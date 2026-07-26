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
            // 1. Load disk-based rules first
            loadImportedDomains()
            
            // 2. Load current custom settings (one-shot)
            val customBlocked = settingsRepository.searchAdBlockBlocklists.first()
            val customAllowed = settingsRepository.searchAdBlockAllowlists.first()
            AdBlockList.updateCustomLists(customBlocked, customAllowed)
            
            // 3. Observe changes for live updates
            combine(
                settingsRepository.searchAdBlockBlocklists,
                settingsRepository.searchAdBlockAllowlists
            ) { blocked, allowed ->
                AdBlockList.updateCustomLists(blocked, allowed)
            }.collect {}
        }
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
