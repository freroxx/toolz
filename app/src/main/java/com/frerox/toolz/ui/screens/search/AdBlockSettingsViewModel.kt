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

    private suspend fun fetchAndParseList(url: String): Set<String> = withContext(Dispatchers.IO) {
        try {
            val response = repository.fetchWebsiteContentRaw(url) ?: return@withContext emptySet()
            val domains = mutableSetOf<String>()
            
            response.lineSequence().forEach { line: String ->
                var trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!") || trimmed.startsWith("[")) return@forEach
                
                // Remove trailing comments
                if (trimmed.contains("#")) trimmed = trimmed.substringBefore("#").trim()
                if (trimmed.contains("!")) trimmed = trimmed.substringBefore("!").trim()

                // Preserve @@ for exceptions
                val isException = trimmed.startsWith("@@")
                val rule = if (isException) trimmed.substring(2) else trimmed

                when {
                    // hosts format: 0.0.0.0 domain.com or 127.0.0.1 domain.com
                    rule.startsWith("0.0.0.0") || rule.startsWith("127.0.0.1") -> {
                        val parts = rule.split(Regex("\\s+"))
                        if (parts.size >= 2) {
                            val d = parts[1].lowercase()
                            if (d != "localhost" && d.contains(".")) {
                                domains.add(if (isException) "@@$d" else d)
                            }
                        }
                    }
                    // AdBlock format: ||domain.com^
                    rule.startsWith("||") && rule.endsWith("^") -> {
                        val domain = rule.substring(2, rule.length - 1)
                        if (domain.contains(".")) {
                            domains.add(if (isException) "@@$domain" else domain.lowercase())
                        }
                    }
                    // Simple domain list or wildcard-less AdBlock
                    !rule.contains(" ") && rule.contains(".") -> {
                        val clean = rule.removePrefix("||").removeSuffix("^").removeSuffix("/")
                        if (clean.contains(".")) {
                            domains.add(if (isException) "@@$clean" else clean.lowercase())
                        }
                    }
                }
            }
            domains
        } catch (e: Exception) {
            emptySet()
        }
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
