package com.frerox.toolz.ui.screens.search

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.browser.TabEntry
import com.frerox.toolz.data.browser.TabManager
import com.frerox.toolz.data.search.BookmarkEntry
import com.frerox.toolz.data.search.QuickLinkEntry
import com.frerox.toolz.data.search.SearchHistoryEntry
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.data.search.WebSearchRepository
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Sealed error hierarchy ───────────────────────────────────────────────────

sealed class SearchError {
    data object NoResults    : SearchError()
    data object NetworkError : SearchError()
    data object RateLimited  : SearchError()
    data class  Unknown(val message: String) : SearchError()

    fun userMessage(): String = when (this) {
        is NoResults    -> "Try different keywords or check your spelling."
        is NetworkError -> "Please check your internet connection and try again."
        is RateLimited  -> "Too many requests. Please wait a moment and try again."
        is Unknown      -> message.takeIf { it.isNotBlank() } ?: "An unexpected error occurred."
    }
}

// ─── Search phase ─────────────────────────────────────────────────────────────

enum class SearchPhase { Idle, Loading, LoadingMore, Results }

// ─── Stable settings state (rarely changes — settings + tabs) ────────────────
// Marked @Immutable so Compose's compiler can skip stability checks.

@Immutable
data class SearchSettingsState(
    val adBlockEnabled:        Boolean       = true,
    val dnsProvider:           String        = "ADGUARD",
    val customDns:             String        = "",
    val recentDns:             List<String>  = emptyList(),
    val isIncognito:           Boolean       = false,
    val searchEngine:          String        = "DUCKDUCKGO",
    val safeSearch:            Boolean       = true,
    val region:                String        = "wt-wt",
    val customEngineUrl:       String        = "",
    val searchAutofillEnabled: Boolean       = true,
    val userName:              String        = "",
    val nextDnsId:             String        = "",
    val tabs:                  List<TabEntry> = emptyList(),
    val activeTabId:           String?       = null,
    val dnsBenchmarks:         Map<String, Long?> = emptyMap(),
    val isBenchmarkingDns:     Boolean       = false,
)

// ─── Fast-changing query state (changes on every keystroke) ──────────────────

@Immutable
data class SearchQueryState(
    val query:       String             = "",
    val suggestions: List<String>       = emptyList(),
    val phase:       SearchPhase        = SearchPhase.Idle,
    val results:     List<SearchResult> = emptyList(),
    val error:       SearchError?       = null,
    val canLoadMore: Boolean            = false,
    val isActive:    Boolean            = false,
)

// ─── Combined UI state (backward-compat — remove once screens are fully migrated) ──

@Immutable
data class SearchUiState(
    val query:       String             = "",
    val results:     List<SearchResult> = emptyList(),
    val suggestions: List<String>       = emptyList(),
    val phase:       SearchPhase        = SearchPhase.Idle,
    val error:       SearchError?       = null,
    val canLoadMore: Boolean            = false,
    val isActive:    Boolean            = false,

    // Settings
    val adBlockEnabled:        Boolean      = true,
    val dnsProvider:           String       = "ADGUARD",
    val customDns:             String       = "",
    val recentDns:             List<String> = emptyList(),
    val isIncognito:           Boolean      = false,
    val searchEngine:          String       = "DUCKDUCKGO",
    val safeSearch:            Boolean      = true,
    val region:                String       = "wt-wt",
    val customEngineUrl:       String       = "",
    val searchAutofillEnabled: Boolean      = true,
    val userName:              String       = "",
    val nextDnsId:             String       = "",

    // Tabs
    val tabs:        List<TabEntry> = emptyList(),
    val activeTabId: String?        = null,
    
    // DNS Benchmarking
    val dnsBenchmarks:     Map<String, Long?> = emptyMap(),
    val isBenchmarkingDns: Boolean = false,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@Stable
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository:        WebSearchRepository,
    private val settingsRepository: SettingsRepository,
    private val tabManager:        TabManager,
    private val dnsEngine:         com.frerox.toolz.util.network.DnsEngine,
) : ViewModel() {

    // ── Split flows ───────────────────────────────────────────────────────────

    private val _settings = MutableStateFlow(SearchSettingsState())
    val settingsState: StateFlow<SearchSettingsState> = _settings.asStateFlow()

    private val _query = MutableStateFlow(SearchQueryState())
    val queryState: StateFlow<SearchQueryState> = _query.asStateFlow()

    // ── Backward-compat combined flow ─────────────────────────────────────────
    // Only recomputes when either _settings or _query emit a new value.
    // Because _settings rarely changes, fast typing only triggers _query updates —
    // components that only read settings fields skip recomposition entirely.

    val uiState: StateFlow<SearchUiState> = combine(_settings, _query) { s, q ->
        SearchUiState(
            query                = q.query,
            results              = q.results,
            suggestions          = q.suggestions,
            phase                = q.phase,
            error                = q.error,
            canLoadMore          = q.canLoadMore,
            isActive             = q.isActive,
            adBlockEnabled       = s.adBlockEnabled,
            dnsProvider          = s.dnsProvider,
            customDns            = s.customDns,
            recentDns            = s.recentDns,
            isIncognito          = s.isIncognito,
            searchEngine         = s.searchEngine,
            safeSearch           = s.safeSearch,
            region               = s.region,
            customEngineUrl      = s.customEngineUrl,
            searchAutofillEnabled = s.searchAutofillEnabled,
            userName             = s.userName,
            nextDnsId            = s.nextDnsId,
            tabs                 = s.tabs,
            activeTabId          = s.activeTabId,
            dnsBenchmarks        = s.dnsBenchmarks,
            isBenchmarkingDns    = s.isBenchmarkingDns,
        )
    }.stateIn(
        scope       = viewModelScope,
        started     = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(),
    )

    // ── DAO-backed flows (stable, no Hilt needed in composable) ───────────────

    val history    = repository.history
    val bookmarks  = repository.bookmarks
    val quickLinks = repository.quickLinks
    val isFirstTime = settingsRepository.searchFirstTime

    // ── Coroutine job handles ─────────────────────────────────────────────────

    private var suggestionJob: Job? = null
    private var searchJob: Job?     = null

    // ── Init: collect all settings into _settings ─────────────────────────────

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.userName,
                settingsRepository.searchIncognitoEnabled,
                settingsRepository.searchAdBlockEnabled,
                settingsRepository.searchDnsProvider,
                settingsRepository.searchCustomDns,
                settingsRepository.searchNextDnsId,
            ) { args: Array<Any?> ->
                _settings.update {
                    it.copy(
                        userName       = args[0] as String,
                        isIncognito    = args[1] as Boolean,
                        adBlockEnabled = args[2] as Boolean,
                        dnsProvider    = args[3] as String,
                        customDns      = args[4] as String,
                        nextDnsId      = args[5] as String,
                    )
                }
            }.catch { /* non-fatal */ }.collect {}
        }

        viewModelScope.launch {
            combine(
                settingsRepository.searchRecentDns,
                settingsRepository.searchEngine,
                settingsRepository.searchSafeSearch,
                settingsRepository.searchRegion,
                settingsRepository.searchCustomEngineUrl,
            ) { recent, engine, safe, region, customUrl ->
                _settings.update {
                    it.copy(
                        recentDns       = recent.toList(),
                        searchEngine    = engine,
                        safeSearch      = safe,
                        region          = region,
                        customEngineUrl = customUrl,
                    )
                }
            }.catch { }.collect {}
        }

        viewModelScope.launch {
            settingsRepository.searchAutofillEnabled.collect { enabled ->
                _settings.update { it.copy(searchAutofillEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            combine(tabManager.tabs, tabManager.activeTabId) { tabs, activeId ->
                _settings.update { it.copy(tabs = tabs, activeTabId = activeId) }
            }.catch { }.collect {}
        }
    }

    // ─── Query / Suggestions ──────────────────────────────────────────────────

    fun onQueryChange(newQuery: String) {
        // Only update the fast-changing query flow — settings composables unaffected
        _query.update { it.copy(query = newQuery, error = null) }

        suggestionJob?.cancel()
        if (newQuery.length >= 2) {
            suggestionJob = viewModelScope.launch {
                delay(280)
                val suggestions = runCatching { repository.fetchSuggestions(newQuery) }
                    .getOrDefault(emptyList())
                _query.update { it.copy(suggestions = suggestions) }
            }
        } else {
            _query.update { it.copy(suggestions = emptyList()) }
        }
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    fun onSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) { clearSearch(); return }

        suggestionJob?.cancel()
        searchJob?.cancel()

        _query.update {
            it.copy(
                query       = trimmed,
                phase       = SearchPhase.Loading,
                isActive    = false,
                error       = null,
                results     = emptyList(),
                suggestions = emptyList(),
                canLoadMore = false,
            )
        }

        searchJob = viewModelScope.launch {
            if (!_settings.value.isIncognito) {
                runCatching { repository.addHistory(trimmed) }
            }

            runCatching { repository.search(trimmed) }
                .onSuccess { results ->
                    _query.update {
                        it.copy(
                            results     = results,
                            phase       = SearchPhase.Results,
                            canLoadMore = results.isNotEmpty(),
                            error       = if (results.isEmpty()) SearchError.NoResults else null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _query.update {
                        it.copy(phase = SearchPhase.Results, error = mapThrowableToError(throwable))
                    }
                }
        }
    }

    fun retrySearch() {
        val q = _query.value.query
        if (q.isNotEmpty()) onSearch(q)
    }

    // ─── Load More ────────────────────────────────────────────────────────────

    fun loadMore() {
        val q = _query.value
        if (q.phase == SearchPhase.LoadingMore || !q.canLoadMore || q.query.isEmpty()) return
        if (q.results.size >= 500) { _query.update { it.copy(canLoadMore = false) }; return }

        _query.update { it.copy(phase = SearchPhase.LoadingMore) }

        viewModelScope.launch {
            runCatching { repository.search(q.query, q.results.size) }
                .onSuccess { newResults ->
                    if (newResults.isEmpty()) {
                        _query.update { it.copy(phase = SearchPhase.Results, canLoadMore = false) }
                    } else {
                        val combined = (q.results + newResults).distinctBy { it.url }.take(500)
                        _query.update {
                            it.copy(
                                results     = combined,
                                phase       = SearchPhase.Results,
                                canLoadMore = combined.size < 500 && newResults.isNotEmpty(),
                            )
                        }
                    }
                }
                .onFailure { _query.update { it.copy(phase = SearchPhase.Results) } }
        }
    }

    // ─── UI state helpers ─────────────────────────────────────────────────────

    fun onActiveChange(active: Boolean) = _query.update { it.copy(isActive = active) }

    fun clearSearch() {
        searchJob?.cancel()
        suggestionJob?.cancel()
        _query.update {
            it.copy(
                results     = emptyList(),
                query       = "",
                isActive    = false,
                error       = null,
                canLoadMore = false,
                phase       = SearchPhase.Idle,
                suggestions = emptyList(),
            )
        }
    }

    // ─── Settings mutations (always target _settings) ─────────────────────────

    fun toggleIncognito(enabled: Boolean) = launch { settingsRepository.setSearchIncognitoEnabled(enabled) }
    fun toggleAdBlock(enabled: Boolean)   = launch { settingsRepository.setSearchAdBlockEnabled(enabled) }
    fun toggleAutofill(enabled: Boolean)  = launch { settingsRepository.setSearchAutofillEnabled(enabled) }
    fun setDnsProvider(provider: String)  = launch { settingsRepository.setDnsProvider(provider) }
    fun setCustomDns(dns: String)         = launch { settingsRepository.setCustomDns(dns) }
    fun setSearchEngine(engine: String)   = launch { settingsRepository.setSearchEngine(engine) }
    fun setSafeSearch(enabled: Boolean)   = launch { settingsRepository.setSearchSafeSearch(enabled) }
    fun setRegion(region: String)         = launch { settingsRepository.setSearchRegion(region) }
    fun setCustomEngineUrl(url: String)   = launch { settingsRepository.setSearchCustomEngineUrl(url) }
    fun removeRecentDns(dns: String)      = launch { settingsRepository.removeRecentDns(dns) }
    fun dismissFirstTime()                = launch { settingsRepository.setSearchFirstTime(false) }

    fun runDnsBenchmark() {
        if (_settings.value.isBenchmarkingDns) return
        _settings.update { it.copy(isBenchmarkingDns = true, dnsBenchmarks = emptyMap()) }
        
        viewModelScope.launch {
            val providers = dnsEngine.providerLibrary()
            val results = mutableMapOf<String, Long?>()
            
            providers.forEach { provider ->
                val latency = dnsEngine.checkSingleLatency(provider.addresses.first())
                results[provider.id] = latency
                _settings.update { it.copy(dnsBenchmarks = results.toMap()) }
            }
            _settings.update { it.copy(isBenchmarkingDns = false) }
        }
    }

    fun applyFastestDns() {
        viewModelScope.launch {
            _settings.update { it.copy(isBenchmarkingDns = true) }
            val providers = dnsEngine.providerLibrary()
            var fastest: com.frerox.toolz.data.network.DnsProvider? = null
            var minLatency = Long.MAX_VALUE
            
            providers.forEach { provider ->
                val latency = dnsEngine.checkSingleLatency(provider.addresses.first())
                if (latency != null && latency < minLatency) {
                    minLatency = latency
                    fastest = provider
                }
            }
            
            fastest?.let { 
                settingsRepository.setDnsProvider(it.id.uppercase())
            }
            _settings.update { it.copy(isBenchmarkingDns = false) }
        }
    }

    fun setSecurityPreset(preset: String) = viewModelScope.launch {
        when (preset) {
            "LOW" -> {
                settingsRepository.setSearchAdBlockEnabled(true)
                settingsRepository.setDnsProvider("ADGUARD")
                settingsRepository.setSearchIncognitoEnabled(false)
            }
            "BASIC" -> {
                settingsRepository.setSearchAdBlockEnabled(true)
                settingsRepository.setDnsProvider("CLOUDFLARE")
                settingsRepository.setSearchEngine("DUCKDUCKGO")
                settingsRepository.setSearchIncognitoEnabled(false)
            }
            "MAX" -> {
                settingsRepository.setSearchAdBlockEnabled(true)
                settingsRepository.setDnsProvider("QUAD9")
                settingsRepository.setSearchEngine("DUCKDUCKGO")
                settingsRepository.setSearchIncognitoEnabled(true)
            }
        }
    }

    // ─── Data mutations ───────────────────────────────────────────────────────

    fun deleteHistory(id: Long)     = launch { repository.deleteHistory(id) }
    fun clearHistory()              = launch { repository.clearHistory() }
    fun removeBookmark(url: String) = launch { repository.removeBookmark(url) }
    fun addQuickLink(title: String, url: String) = launch { repository.addQuickLink(title, url) }
    fun removeQuickLink(id: Long)   = launch { repository.removeQuickLink(id) }
    fun updateBookmark(id: Long, title: String, url: String) = launch { repository.updateBookmark(id, title, url) }
    fun updateQuickLink(id: Long, title: String, url: String) = launch { repository.updateQuickLink(id, title, url) }

    fun toggleBookmark(result: SearchResult) = viewModelScope.launch {
        if (repository.isBookmarked(result.url)) repository.removeBookmark(result.url)
        else repository.addBookmark(result.title, result.url)
    }

    fun reorderQuickLinks(from: Int, to: Int) = viewModelScope.launch {
        val links = repository.quickLinks.firstOrNull() ?: return@launch
        val mutable = links.toMutableList()
        if (from !in mutable.indices || to !in mutable.indices) return@launch
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        repository.updateQuickLinks(mutable.mapIndexed { i, link -> link.copy(sortOrder = i) })
    }

    // ─── Tab actions ──────────────────────────────────────────────────────────

    fun openTab(url: String)  { tabManager.addTab(url) }
    fun closeTab(id: String)  { tabManager.removeTab(id) }
    fun switchTab(id: String) { tabManager.switchTab(id) }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun launch(block: suspend () -> Unit): Job =
        viewModelScope.launch { runCatching { block() } }

    private fun mapThrowableToError(t: Throwable): SearchError = when {
        t is java.net.UnknownHostException   -> SearchError.NetworkError
        t is java.net.SocketTimeoutException -> SearchError.NetworkError
        t is javax.net.ssl.SSLException      -> SearchError.NetworkError
        t.message?.contains("429") == true   -> SearchError.RateLimited
        t.message?.contains("403") == true   -> SearchError.RateLimited
        else                                 -> SearchError.Unknown(t.localizedMessage ?: "")
    }
}