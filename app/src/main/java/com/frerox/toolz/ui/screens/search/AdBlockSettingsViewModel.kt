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
import okhttp3.HttpUrl.Companion.toHttpUrl
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
    val isNextDnsEnabled: Boolean = false,
    val adScriptEnabled: Boolean = false
)@HiltViewModel
class AdBlockSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val repository: com.frerox.toolz.data.search.WebSearchRepository,
    private val dnsEngine: com.frerox.toolz.util.network.DnsEngine,
    private val adBlockManager: com.frerox.toolz.util.network.AdBlockManager,
    private val nextDnsClient: com.frerox.toolz.data.browser.nextdns.NextDnsClient,
) : ViewModel() {

    private val _isFetching = MutableStateFlow(false)
    private val _nextDnsHealth = MutableStateFlow(NextDnsHealth.UNKNOWN)
    
    // Local state for inputs to prevent "buggy" behavior while typing
    private val _nextDnsIdInput = MutableStateFlow<String?>(null)
    private val _nextDnsUrlInput = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AdBlockSettingsUiState> = combine(
        settingsRepository.searchNextDnsId,
        settingsRepository.searchNextDnsDnsUrl,
        settingsRepository.searchEnabledImportedLists,
        settingsRepository.searchAdBlockBlocklists,
        settingsRepository.searchAdBlockAllowlists,
        settingsRepository.searchAdBlockImportedCount,
        settingsRepository.searchDnsProvider,
        settingsRepository.searchAdBlockScriptEnabled,
        _nextDnsHealth,
        _isFetching,
        _nextDnsIdInput,
        _nextDnsUrlInput
    ) { args: Array<Any?> ->
        val repoId    = args[0] as String
        val repoUrl   = args[1] as String
        @Suppress("UNCHECKED_CAST")
        val imported  = args[2] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val blocked   = args[3] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val allowed   = args[4] as Set<String>
        val domCount  = args[5] as Int
        val provider  = args[6] as String
        val adScript  = args[7] as Boolean
        val health    = args[8] as NextDnsHealth
        val fetching  = args[9] as Boolean
        val inputId   = args[10] as? String
        val inputUrl  = args[11] as? String
        AdBlockSettingsUiState(
            blocklists            = blocked,
            allowlists            = allowed,
            enabledImportedLists  = imported,
            importedDomainCount   = domCount,
            nextDnsId             = inputId ?: repoId,
            nextDnsUrl            = inputUrl ?: repoUrl,
            isFetching            = fetching,
            nextDnsHealth         = health,
            // Enabled means NextDNS is the ACTIVE provider, not just that an ID was typed
            isNextDnsEnabled      = provider.equals("NEXTDNS", ignoreCase = true),
            adScriptEnabled       = adScript
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AdBlockSettingsUiState()
    )

    init {
        viewModelScope.launch {
            checkDnsHealth()
        }
        // AI's choice: keep the status chip fresh while the screen is open
        viewModelScope.launch {
            while (true) {
                delay(60_000)
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

        _nextDnsHealth.value = NextDnsHealth.UNKNOWN
        // Run on IO dispatcher and await completion so callers that show the
        // refreshed badge right after toggling actually see the new result.
        _nextDnsHealth.value = withContext(Dispatchers.IO) {
            nextDnsClient.checkHealth(id)
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
            "OISD_BASIC" to "https://small.oisd.nl",
            "ADGUARD_BASE" to "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            "STEVENBLACK" to "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            "EASYLIST" to "https://easylist.to/easylist/easylist.txt",
            "FANBOY_ANNOYANCE" to "https://secure.fanboy.co.nz/fanboy-annoyance.txt",
            "PETER_LOWE" to "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            "UBLOCK_ORIGIN" to "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt"
        )
    }

    fun toggleNextDns(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                settingsRepository.setDnsProvider("NEXTDNS")
                // Refresh the health badge now that the provider actually changed
                checkDnsHealth()
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
                checkDnsHealth()
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
                // Always persist the bare profile id — hostnames/URLs break DoH resolution.
                settingsRepository.setSearchNextDnsId(
                    com.frerox.toolz.data.browser.nextdns.NextDnsClient.sanitizeId(id)
                )
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

    fun toggleAdScriptBlock(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSearchAdBlockScriptEnabled(enabled)
            com.frerox.toolz.data.browser.AdBlockList.isAdScriptBlockingEnabled = enabled
        }
    }
}
