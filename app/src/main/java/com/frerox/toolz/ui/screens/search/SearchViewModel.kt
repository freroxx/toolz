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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.browser.TabEntry
import com.frerox.toolz.data.browser.TabManager
import com.frerox.toolz.data.browser.BrowserHistoryStore
import com.frerox.toolz.data.browser.BrowserHistoryItem
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
import com.frerox.toolz.data.search.SearchCategory
import javax.inject.Inject

// ─── Math Evaluation Result ──────────────────────────────────────────────────

@Immutable
data class MathResult(
    val expression: String,
    val result:     String
)

// ─── Sealed error hierarchy ───────────────────────────────────────────────────

sealed class SearchError {
    data object NoResults    : SearchError()
    data object Offline      : SearchError()
    data object DnsError     : SearchError()
    data object NetworkError : SearchError()
    data object RateLimited  : SearchError()
    data class  Unknown(val message: String) : SearchError()

    fun userMessage(): String = when (this) {
        is NoResults    -> "Try different keywords or check your spelling."
        is Offline      -> "You are currently offline. Check your network or return to dashboard."
        is DnsError     -> "DNS resolution failed. Check your DNS provider settings."
        is NetworkError -> "Please check your internet connection and try again."
        is RateLimited  -> "Too many requests / CAPTCHA detected. Try switching engines."
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
    val searchEngine:          String        = "META",
    val safeSearch:            Boolean       = false,
    val region:                String        = "wt-wt",
    val customEngineUrl:       String        = "",
    val searchAutofillEnabled: Boolean       = true,
    val userName:              String        = "",
    val nextDnsId:             String        = "",
    val showGreetingCard:      Boolean       = false,
    val tabs:                  List<TabEntry> = emptyList(),
    val activeTabId:           String?       = null,
    val dnsBenchmarks:         Map<String, Long?> = emptyMap(),
    val isBenchmarkingDns:     Boolean       = false,
)

// ─── Fast-changing query state (changes on every keystroke) ──────────────────

@Immutable
data class SearchQueryState(
    val query:            String             = "",
    val suggestions:      List<String>       = emptyList(),
    val phase:            SearchPhase        = SearchPhase.Idle,
    val results:          List<SearchResult> = emptyList(),
    val error:            SearchError?       = null,
    val canLoadMore:      Boolean            = false,
    val isActive:         Boolean            = false,
    val category:         SearchCategory     = SearchCategory.ALL,
    val mathResult:       MathResult?        = null,
)

// ─── Combined UI state (backward-compat — remove once screens are fully migrated) ──

@Immutable
data class SearchUiState(
    val query:            String             = "",
    val results:          List<SearchResult> = emptyList(),
    val suggestions:      List<String>       = emptyList(),
    val phase:            SearchPhase        = SearchPhase.Idle,
    val error:            SearchError?       = null,
    val canLoadMore:      Boolean            = false,
    val isActive:         Boolean            = false,
    val category:         SearchCategory     = SearchCategory.ALL,
    val mathResult:       MathResult?        = null,

    // Settings
    val adBlockEnabled:        Boolean      = true,
    val dnsProvider:           String       = "ADGUARD",
    val customDns:             String       = "",
    val recentDns:             List<String> = emptyList(),
    val isIncognito:           Boolean      = false,
    val searchEngine:          String       = "META",
    val safeSearch:            Boolean      = false,
    val region:                String       = "wt-wt",
    val customEngineUrl:       String       = "",
    val searchAutofillEnabled: Boolean      = true,
    val userName:              String       = "",
    val nextDnsId:             String       = "",
    val showGreetingCard:      Boolean      = false,

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
    private val browserHistoryStore: BrowserHistoryStore,
    private val dnsEngine:         com.frerox.toolz.util.network.DnsEngine,
    private val offlineManager:    com.frerox.toolz.util.OfflineManager,
) : ViewModel() {

    // ─── Offline state (cached as StateFlow for synchronous .value access) ───

    private val _isOffline = MutableStateFlow(false)

    // ── Split flows ───────────────────────────────────────────────────────────

    private val _settings = MutableStateFlow(SearchSettingsState())
    val settingsState: StateFlow<SearchSettingsState> = _settings.asStateFlow()

    private val _query = MutableStateFlow(SearchQueryState())
    val queryState: StateFlow<SearchQueryState> = _query.asStateFlow()

    // AI's choice: visible per-engine health for the status indicator
    private val _engineHealth = MutableStateFlow<Map<String, WebSearchRepository.EngineHealth>>(emptyMap())
    val engineHealth: StateFlow<Map<String, WebSearchRepository.EngineHealth>> = _engineHealth.asStateFlow()

    // ── Backward-compat combined flow ─────────────────────────────────────────

    val uiState: StateFlow<SearchUiState> = combine(_settings, _query) { s, q ->
        SearchUiState(
            query                 = q.query,
            results               = q.results,
            suggestions           = q.suggestions,
            phase                 = q.phase,
            error                 = q.error,
            canLoadMore           = q.canLoadMore,
            isActive              = q.isActive,
            category              = q.category,
            mathResult            = q.mathResult,
            adBlockEnabled        = s.adBlockEnabled,
            dnsProvider           = s.dnsProvider,
            customDns             = s.customDns,
            recentDns             = s.recentDns,
            isIncognito           = s.isIncognito,
            searchEngine          = s.searchEngine,
            safeSearch            = s.safeSearch,
            region                = s.region,
            customEngineUrl       = s.customEngineUrl,
            searchAutofillEnabled = s.searchAutofillEnabled,
            userName              = s.userName,
            nextDnsId             = s.nextDnsId,
            showGreetingCard      = s.showGreetingCard,
            tabs                  = s.tabs,
            activeTabId           = s.activeTabId,
            dnsBenchmarks         = s.dnsBenchmarks,
            isBenchmarkingDns     = s.isBenchmarkingDns,
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(),
    )

    // ── DAO-backed flows ───────────────────────────────────────────────────────

    val history     = repository.history
    val bookmarks   = repository.bookmarks
    val quickLinks  = repository.quickLinks
    val isFirstTime = settingsRepository.searchFirstTime
    val browserHistory = browserHistoryStore.items

    // ── Coroutine job handles ─────────────────────────────────────────────────

    private var suggestionJob: Job? = null
    private var searchJob: Job?     = null

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.userName,
                settingsRepository.searchIncognitoEnabled,
                settingsRepository.searchAdBlockEnabled,
                settingsRepository.searchDnsProvider,
                settingsRepository.searchCustomDns,
                settingsRepository.searchNextDnsId,
                settingsRepository.searchShowGreetingCard,
            ) { args: Array<Any?> ->
                _settings.update {
                    it.copy(
                        userName         = args[0] as String,
                        isIncognito      = args[1] as Boolean,
                        adBlockEnabled   = args[2] as Boolean,
                        dnsProvider      = args[3] as String,
                        customDns        = args[4] as String,
                        nextDnsId        = args[5] as String,
                        showGreetingCard = args[6] as Boolean,
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

        // Live offline monitoring — convert to StateFlow so onSearch can check .value synchronously
        viewModelScope.launch {
            offlineManager.offlineState.collect { offlineState ->
                val nowOffline = offlineState == com.frerox.toolz.util.OfflineState.OFFLINE
                _isOffline.value = nowOffline
                if (nowOffline && _query.value.results.isEmpty() && _query.value.query.isNotBlank()) {
                    _query.update { it.copy(error = SearchError.Offline, phase = SearchPhase.Results) }
                }
            }
        }
    }

    // ─── Query / Suggestions / Math Evaluation ───────────────────────────────

    fun onQueryChange(newQuery: String) {
        val mathRes = tryEvaluateMath(newQuery)
        _query.update { it.copy(query = newQuery, error = null, mathResult = mathRes) }

        suggestionJob?.cancel()
        if (newQuery.length >= 2 && mathRes == null) {
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

    private fun tryEvaluateMath(query: String): MathResult? {
        val trimmed = query.trim()
        if (trimmed.length < 3) return null
        val mathCharsRegex = Regex("""^[0-9\s\+\-\*\/\^\(\)\.\%]+$|^sqrt\([0-9\.\s]+\)$""", RegexOption.IGNORE_CASE)
        if (!mathCharsRegex.matches(trimmed)) return null
        if (!trimmed.any { it.isDigit() }) return null
        if (!trimmed.any { it in "+-*/^%" } && !trimmed.startsWith("sqrt", ignoreCase = true)) return null

        return try {
            val sanitized = trimmed.replace("%", "/100")
            val expr = net.objecthunter.exp4j.ExpressionBuilder(sanitized).build()
            val valResult = expr.evaluate()
            if (valResult.isNaN() || valResult.isInfinite()) return null
            val formatted = if (valResult % 1.0 == 0.0) valResult.toLong().toString() else "%.6f".format(valResult).trimEnd('0').trimEnd('.')
            MathResult(expression = trimmed, result = formatted)
        } catch (_: Exception) {
            null
        }
    }

    fun setSearchCategory(category: SearchCategory) {
        if (_query.value.category == category) return
        _query.update { it.copy(category = category) }
        val q = _query.value.query
        if (q.isNotEmpty()) {
            onSearch(q, category)
        }
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    fun onSearch(query: String, category: SearchCategory = _query.value.category) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) { clearSearch(); return }

        suggestionJob?.cancel()
        searchJob?.cancel()

        // 1. Real-time Offline Check
        if (_isOffline.value) {
            _query.update {
                it.copy(
                    query       = trimmed,
                    category    = category,
                    phase       = SearchPhase.Results,
                    isActive    = false,
                    error       = SearchError.Offline,
                    results     = emptyList(),
                    suggestions = emptyList(),
                    canLoadMore = false,
                )
            }
            return
        }

        _query.update {
            it.copy(
                query       = trimmed,
                category    = category,
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

            runCatching { repository.search(trimmed, 0, category) }
                .onSuccess { results ->
                    _engineHealth.value = repository.engineHealthSnapshot()
                    _query.update {
                        it.copy(
                            results     = results,
                            phase       = SearchPhase.Results,
                            // Pagination revamp: allow load-more for ALL categories including images/videos now that real HTML parsing is enabled with proper offset handling
                            canLoadMore = results.isNotEmpty() && results.size < 500,
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
            runCatching { repository.search(q.query, q.results.size, q.category) }
                .onSuccess { newResults ->
                    if (newResults.isEmpty()) {
                        _query.update { it.copy(phase = SearchPhase.Results, canLoadMore = false) }
                    } else {
                        val combined = (q.results + newResults).distinctBy { it.url }.take(500)
                        _query.update {
                            it.copy(
                                results     = combined,
                                phase       = SearchPhase.Results,
                                // Keep allowing load-more unless we hit the 500 cap or engine returned nothing
                                canLoadMore = combined.size < 500,
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

    fun setShowGreetingCard(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setSearchShowGreetingCard(enabled)
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
                settingsRepository.setSearchEngine("META")
                settingsRepository.setSearchIncognitoEnabled(false)
            }
            "MAX" -> {
                settingsRepository.setSearchAdBlockEnabled(true)
                settingsRepository.setDnsProvider("QUAD9")
                settingsRepository.setSearchEngine("META")
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

    /** Search results inherit the current privacy context instead of leaking out of it. */
    fun openTab(url: String)  { tabManager.addTab(url, isPrivate = _settings.value.isIncognito) }
    fun closeTab(id: String)  { tabManager.removeTab(id) }
    fun switchTab(id: String) { tabManager.switchTab(id) }
    fun removeBrowserHistory(url: String) { browserHistoryStore.remove(url) }
    fun clearBrowserHistory() { browserHistoryStore.clear() }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun launch(block: suspend () -> Unit): Job =
        viewModelScope.launch { runCatching { block() } }

    private fun mapThrowableToError(t: Throwable): SearchError = when {
        t is java.net.UnknownHostException   -> SearchError.DnsError
        t is java.net.SocketTimeoutException -> SearchError.NetworkError
        t is javax.net.ssl.SSLException      -> SearchError.NetworkError
        t.message?.contains("429") == true   -> SearchError.RateLimited
        t.message?.contains("403") == true   -> SearchError.RateLimited
        else                                 -> SearchError.Unknown(t.localizedMessage ?: "")
    }
}
