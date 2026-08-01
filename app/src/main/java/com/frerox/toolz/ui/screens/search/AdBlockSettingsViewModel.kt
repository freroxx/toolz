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

package com.frerox.toolz.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class NextDnsHealth { UNKNOWN, CONNECTED, NOT_LINKED, ERROR }

data class AdBlockSettingsUiState(
    val blocklists: Set<String> = emptySet(),
    val allowlists: Set<String> = emptySet(),
    val enabledImportedLists: Set<String> = emptySet(),
    val importedDomainCount: Int = 0,
    val nextDnsId: String = "",
    val nextDnsUrl: String = "",
    val isFetching: Boolean = false,
    val nextDnsHealth: NextDnsHealth = NextDnsHealth.UNKNOWN,
    val isNextDnsEnabled: Boolean = false
)

@HiltViewModel
class AdBlockSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val repository: com.frerox.toolz.data.search.WebSearchRepository,
    private val dnsEngine: com.frerox.toolz.util.network.DnsEngine,
    private val adBlockManager: com.frerox.toolz.util.network.AdBlockManager,
) : ViewModel() {

    private val _isFetching = MutableStateFlow(false)
    private val _nextDnsHealth = MutableStateFlow(NextDnsHealth.UNKNOWN)
    
    // Local state for inputs to prevent "buggy" behavior while typing
    private val _nextDnsIdInput = MutableStateFlow<String?>(null)
    private val _nextDnsUrlInput = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AdBlockSettingsUiState> = combine(
        settingsRepository.searchAdBlockBlocklists,
        settingsRepository.searchAdBlockAllowlists,
        settingsRepository.searchEnabledImportedLists,
        settingsRepository.searchAdBlockImportedCount,
        settingsRepository.searchDnsProvider,
        settingsRepository.searchNextDnsId,
        settingsRepository.searchNextDnsDnsUrl,
        _isFetching,
        _nextDnsHealth,
        _nextDnsIdInput,
        _nextDnsUrlInput
    ) { args: Array<Any?> ->
        val blocked = args[0] as Set<String>
        val allowed = args[1] as Set<String>
        val imported = args[2] as Set<String>
        val count = args[3] as Int
        val dnsProvider = args[4] as String
        val id = args[5] as String
        val url = args[6] as String
        val fetching = args[7] as Boolean
        val health = args[8] as NextDnsHealth
        val idInput = args[9] as? String
        val urlInput = args[10] as? String

        AdBlockSettingsUiState(
            blocklists = blocked,
            allowlists = allowed,
            enabledImportedLists = imported,
            importedDomainCount = count,
            nextDnsId = idInput ?: id,
            nextDnsUrl = urlInput ?: url,
            isFetching = fetching,
            nextDnsHealth = health,
            isNextDnsEnabled = dnsProvider == "NEXTDNS"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdBlockSettingsUiState())

    init {
        viewModelScope.launch {
            checkDnsHealth()
        }
        
        // Background health monitor
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                checkDnsHealth()
            }
        }
    }

    private suspend fun checkDnsHealth() {
        val id = settingsRepository.searchNextDnsId.first()
        if (id.isBlank()) {
            _nextDnsHealth.value = NextDnsHealth.NOT_LINKED
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Use a separate client for health probe with standard User-Agent and cache buster
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val timestamp = System.currentTimeMillis()
                val request = okhttp3.Request.Builder()
                    .url("https://test.nextdns.io/?_=$timestamp")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
                    .header("Cache-Control", "no-cache")
                    .build()
                
                val response = client.newCall(request).execute()
                val content = response.body.string()
                
                // Parse NextDNS probe JSON
                val isOk = content.contains("\"status\": \"ok\"")
                val isUnconfigured = content.contains("\"status\": \"unconfigured\"")
                val hasConfig = content.contains("\"configuration\": \"$id\"")
                
                when {
                    isOk && hasConfig -> _nextDnsHealth.value = NextDnsHealth.CONNECTED
                    isOk && !hasConfig -> _nextDnsHealth.value = NextDnsHealth.NOT_LINKED
                    isUnconfigured -> _nextDnsHealth.value = NextDnsHealth.NOT_LINKED
                    else -> _nextDnsHealth.value = NextDnsHealth.ERROR
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _nextDnsHealth.value = NextDnsHealth.ERROR
            }
        }
    }

    fun toggleImportedList(listId: String) {
        viewModelScope.launch {
            val current = uiState.value.enabledImportedLists
            val updated = if (current.contains(listId)) current - listId else current + listId
            settingsRepository.setSearchEnabledImportedLists(updated)
            syncImportedLists(updated)
        }
    }

    fun syncImportedLists(enabledLists: Set<String>) {
        if (_isFetching.value) return
        viewModelScope.launch {
            _isFetching.value = true
            try {
                adBlockManager.syncImportedLists(enabledLists, ::fetchAndParseList)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isFetching.value = false
            }
        }
    }

    private suspend fun fetchAndParseList(url: String): Set<String> {
        // Delegate to AdBlockManager which has the canonical fetch+parse logic
        return adBlockManager.fetchListFromNetwork(url)
    }


    companion object {
        val POPULAR_LISTS = mapOf(
            "OISD_BASIC" to "https://small.oisd.nl/domains",
            "ADGUARD_BASE" to "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            "STEVENBLACK" to "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            "EASYLIST" to "https://easylist.to/easylist/easylist.txt",
            "FANBOY_ANNOYANCE" to "https://secure.fanboy.co.nz/fanboy-annoyance.txt",
            "LIGHTSWITCH" to "https://raw.githubusercontent.com/the-asf/lightswitch/master/blocklist.txt",
            "NOTRACK" to "https://raw.githubusercontent.com/quidsup/notrack/master/trackers.txt"
        )
    }

    fun toggleNextDns(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                settingsRepository.setDnsProvider("NEXTDNS")
            } else {
                // If disabling NextDNS, switch to the fastest alternative
                _isFetching.value = true // Show loading while benchmarking
                val providers = dnsEngine.providerLibrary().filter { it.id != "nextdns" }
                var fastestId = "CLOUDFLARE"
                var minLatency = Long.MAX_VALUE
                
                providers.forEach { p ->
                    val lat = dnsEngine.checkSingleLatency(p.addresses.first())
                    if (lat != null && lat < minLatency) {
                        minLatency = lat
                        fastestId = p.id.uppercase()
                    }
                }
                settingsRepository.setDnsProvider(fastestId)
                _isFetching.value = false
            }
        }
    }

    fun applyNextDnsConfig() {
        toggleNextDns(true)
    }

    fun addBlockedDomain(domain: String) {
        if (domain.isBlank()) return
        viewModelScope.launch {
            val current = uiState.value.blocklists
            settingsRepository.setSearchAdBlockBlocklists(current + domain.trim().lowercase())
        }
    }

    fun removeBlockedDomain(domain: String) {
        viewModelScope.launch {
            val current = uiState.value.blocklists
            settingsRepository.setSearchAdBlockBlocklists(current - domain)
        }
    }

    fun addAllowedDomain(domain: String) {
        if (domain.isBlank()) return
        viewModelScope.launch {
            val current = uiState.value.allowlists
            settingsRepository.setSearchAdBlockAllowlists(current + domain.trim().lowercase())
        }
    }

    fun removeAllowedDomain(domain: String) {
        viewModelScope.launch {
            val current = uiState.value.allowlists
            settingsRepository.setSearchAdBlockAllowlists(current - domain)
        }
    }

    fun setNextDnsId(id: String) {
        _nextDnsIdInput.value = id
        viewModelScope.launch {
            delay(500) // Debounce save to DataStore
            if (_nextDnsIdInput.value == id) {
                settingsRepository.setSearchNextDnsId(id.trim())
                _nextDnsIdInput.value = null
                checkDnsHealth()
            }
        }
    }

    fun setNextDnsUrl(url: String) {
        _nextDnsUrlInput.value = url
        viewModelScope.launch {
            delay(500)
            if (_nextDnsUrlInput.value == url) {
                settingsRepository.setSearchNextDnsDnsUrl(url.trim())
                _nextDnsUrlInput.value = null
            }
        }
    }
}
