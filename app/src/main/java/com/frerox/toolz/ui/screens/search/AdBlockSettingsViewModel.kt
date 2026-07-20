package com.frerox.toolz.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.browser.AdBlockList
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
    private val application: android.app.Application
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

        // Sync singleton for custom lists
        AdBlockList.updateCustomLists(blocked, allowed)
        
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
        // Load existing imported domains from file on startup
        viewModelScope.launch {
            loadImportedDomains()
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
                // Use a standard probe or check if the custom endpoint is reachable
                val content = repository.fetchWebsiteContentRaw("https://test.nextdns.io")
                if (content?.contains("\"status\": \"ok\"") == true) {
                    _nextDnsHealth.value = NextDnsHealth.CONNECTED
                } else if (content?.contains("unconfigured") == true) {
                    _nextDnsHealth.value = NextDnsHealth.NOT_LINKED
                } else {
                    _nextDnsHealth.value = NextDnsHealth.ERROR
                }
            } catch (e: Exception) {
                _nextDnsHealth.value = NextDnsHealth.ERROR
            }
        }
    }

    private suspend fun loadImportedDomains() {
        withContext(Dispatchers.IO) {
            val file = File(application.filesDir, "imported_blocklist.txt")
            if (file.exists()) {
                val domains = file.readLines().toSet()
                AdBlockList.updateImportedList(domains)
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
                val allDomains = mutableSetOf<String>()
                
                enabledLists.forEach { id ->
                    val url = POPULAR_LISTS[id] ?: return@forEach
                    val domains = fetchAndParseList(url)
                    allDomains.addAll(domains)
                }
                
                // Save to file
                withContext(Dispatchers.IO) {
                    val file = File(application.filesDir, "imported_blocklist.txt")
                    file.writeText(allDomains.joinToString("\n"))
                }
                
                AdBlockList.updateImportedList(allDomains)
                settingsRepository.setSearchAdBlockImportedCount(allDomains.size)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isFetching.value = false
            }
        }
    }

    private suspend fun fetchAndParseList(url: String): Set<String> = withContext(Dispatchers.IO) {
        try {
            val response = repository.fetchWebsiteContentRaw(url) ?: return@withContext emptySet()
            val domains = mutableSetOf<String>()
            
            response.lineSequence().forEach { line: String ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) return@forEach
                
                // Handle hosts format: 0.0.0.0 domain.com
                if (trimmed.startsWith("0.0.0.0") || trimmed.startsWith("127.0.0.1")) {
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        domains.add(parts[1].lowercase())
                    }
                } else if (!trimmed.contains(" ") && trimmed.contains(".")) {
                    // Assume it's a simple domain list
                    domains.add(trimmed.lowercase())
                }
                // (Optional) Handle AdBlock format like ||domain.com^
                else if (trimmed.startsWith("||") && trimmed.endsWith("^")) {
                    val domain = trimmed.substring(2, trimmed.length - 1)
                    domains.add(domain.lowercase())
                }
            }
            domains
        } catch (e: Exception) {
            emptySet()
        }
    }

    companion object {
        val POPULAR_LISTS = mapOf(
            "OISD_BASIC" to "https://small.oisd.nl/",
            "ADGUARD_BASE" to "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            "STEVENBLACK" to "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            "EASYLIST" to "https://easylist.to/easylist/easylist.txt",
            "NOTRACK" to "https://raw.githubusercontent.com/quidsup/notrack/master/trackers.txt"
        )
    }

    fun applyNextDnsConfig() {
        viewModelScope.launch {
            settingsRepository.setDnsProvider("NEXTDNS")
        }
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
