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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
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
import com.frerox.toolz.data.search.engine.MetaMerger
import com.frerox.toolz.ui.screens.search.components.youTubeVideoId
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
    val searchDurationMs: Long?              = null,
    /** Non-null when the result list is filtered down to a single domain ("site:"). */
    val siteFilter:       String?            = null,
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
    val searchDurationMs: Long?              = null,
    val siteFilter:       String?            = null,

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
    private val metaMerger:        MetaMerger,
) : ViewModel() {

    // ─── Offline state (cached as StateFlow for synchronous .value access) ───

    private val _isOffline = MutableStateFlow(false)

    // ── Split flows ───────────────────────────────────────────────────────────

    private val _settings = MutableStateFlow(SearchSettingsState())
    val settingsState: StateFlow<SearchSettingsState> = _settings.asStateFlow()

    private val _query = MutableStateFlow(SearchQueryState())
    val queryState: StateFlow<SearchQueryState> = _query.asStateFlow()

    // AI's choice: visible per-engine health for the status indicator
    private val _engineHealth = MutableStateFlow<Map<String, com.frerox.toolz.data.search.engine.EngineHealth>>(emptyMap())
    val engineHealth: StateFlow<Map<String, com.frerox.toolz.data.search.engine.EngineHealth>> = _engineHealth.asStateFlow()

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
            searchDurationMs      = q.searchDurationMs,
            siteFilter            = q.siteFilter,
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
            // Immediate local history matches on keystroke (0ms delay)
            viewModelScope.launch {
                val localMatches = repository.history.first()
                    .map { it.query }
                    .filter { it.contains(newQuery, ignoreCase = true) && !it.equals(newQuery, ignoreCase = true) }
                    .take(3)
                if (_query.value.query == newQuery && _query.value.suggestions.isEmpty()) {
                    _query.update { it.copy(suggestions = localMatches) }
                }
            }

            suggestionJob = viewModelScope.launch {
                delay(280)
                // Merge remote suggestions with matching local history so the user's own
                // past searches surface immediately, before the network round-trip lands.
                val localMatches = repository.history.first()
                    .map { it.query }
                    .filter { it.contains(newQuery, ignoreCase = true) && !it.equals(newQuery, ignoreCase = true) }
                    .take(3)
                val remote = runCatching { repository.fetchSuggestions(newQuery) }
                    .getOrDefault(emptyList())
                _query.update {
                    it.copy(suggestions = (localMatches + remote).distinctBy { s -> s.lowercase() }.take(8))
                }
            }
        } else {
            _query.update { it.copy(suggestions = emptyList()) }
        }
    }

    private fun tryEvaluateMath(query: String): MathResult? {
        val trimmed = query.trim()
        if (trimmed.length < 3) return null
        val mathCharsRegex = Regex("""^[0-9\s\+\-\*\/\^\(\)\.\%]+$|^[0-9\s\+\-\*\/\^\(\)\.\%]*sqrt\([0-9\s\+\-\*\/\^\(\)\.\%]+\)[0-9\s\+\-\*\/\^\(\)\.\%]*$""", RegexOption.IGNORE_CASE)
        if (!mathCharsRegex.matches(trimmed)) return null
        if (!trimmed.any { it.isDigit() }) return null
        if (!trimmed.any { it in "+-*/^%" } && !trimmed.contains("sqrt", ignoreCase = true)) return null

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
                    suggestions     = emptyList(),
                    canLoadMore     = false,
                    searchDurationMs = null,
                )
            }
            return
        }

        _query.update {
            it.copy(
                query            = trimmed,
                category         = category,
                phase            = SearchPhase.Loading,
                isActive         = false,
                error            = null,
                results          = emptyList(),
                suggestions      = emptyList(),
                canLoadMore      = false,
                searchDurationMs = null,
            )
        }

        searchJob = viewModelScope.launch {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            if (!_settings.value.isIncognito) {
                runCatching { repository.addHistory(trimmed) }
            }

            runCatching { repository.search(trimmed, 0, category) }
                .onSuccess { results ->
                    _engineHealth.value = repository.engineHealthSnapshot().mapKeys { it.key.name }
                    val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
                    _query.update {
                        it.copy(
                            results     = results,
                            phase       = SearchPhase.Results,
                            // Pagination revamp: allow load-more for ALL categories including images/videos now that real HTML parsing is enabled with proper offset handling
                            canLoadMore = results.isNotEmpty() && results.size < 500,
                            error       = if (results.isEmpty()) SearchError.NoResults else null,
                            searchDurationMs = elapsed,
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

    // ─── Site filter ("show only results from this domain") ───────────────────

    /** Filters the current result list down to a single host — local-only, instant, no new request. */
    fun applySiteFilter(host: String) {
        _query.update { it.copy(siteFilter = host.trim().removePrefix("www.")) }
    }

    fun clearSiteFilter() {
        _query.update { it.copy(siteFilter = null) }
    }

    private val cdnHosts = setOf(
        "googleusercontent.com",
        "amazonaws.com",
        "akamaihd.net",
        "cloudfront.net",
        "fastly.net",
        "cloudflare.com",
        "ytimg.com",
        "ggpht.com"
    )

    /** Distinct hosts across the current results, most frequent first — powers the quick filter chips. */
    fun siteFilterCandidates(results: List<SearchResult>): List<String> =
        results.asSequence()
            .map { runCatching { java.net.URI(it.url).host?.removePrefix("www.") }.getOrNull() }
            .filterNotNull()
            .filter { host -> host.isNotBlank() && cdnHosts.none { host.endsWith(it) } }
            .groupBy { it }
            .map { (host, urls) -> host to urls.size }
            .sortedByDescending { it.second }
            .take(4)
            .map { it.first }

    // ─── Load More ────────────────────────────────────────────────────────────

    fun loadMore() {
        val snapshot = _query.value
        if (snapshot.phase == SearchPhase.LoadingMore || !snapshot.canLoadMore || snapshot.query.isEmpty()) return
        if (snapshot.results.size >= 500) { _query.update { it.copy(canLoadMore = false) }; return }

        _query.update { it.copy(phase = SearchPhase.LoadingMore) }

        viewModelScope.launch {
            runCatching { repository.search(snapshot.query, snapshot.results.size, snapshot.category) }
                .onSuccess { newResults ->
                    if (newResults.isEmpty()) {
                        _query.update { it.copy(phase = SearchPhase.Results, canLoadMore = false) }
                    } else {
                        val base = snapshot.results
                        val seen = base.mapTo(mutableSetOf()) { metaMerger.canonical(it.url) }
                        val dedupedNew = newResults.filter { seen.add(metaMerger.canonical(it.url)) }
                        // If all newResults were duplicates, treat as no new page rather than infinite loop
                        if (dedupedNew.isEmpty()) {
                            _query.update { it.copy(phase = SearchPhase.Results, canLoadMore = false) }
                            return@onSuccess
                        }
                        val combined = (base + dedupedNew).take(500)
                        _query.update {
                            it.copy(
                                results     = combined,
                                phase       = SearchPhase.Results,
                                // Keep canLoadMore true unless we hit 500 or new page yielded zero unique
                                canLoadMore = combined.size < 500 && dedupedNew.isNotEmpty(),
                            )
                        }
                    }
                }
                .onFailure {
                    // Do not clear canLoadMore on transient failure — allow retry
                    _query.update { it.copy(phase = SearchPhase.Results) }
                }
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

    /** Currently playing YouTube embed (video id), or null when no play-mode is active. */
    private val _activeVideoId = mutableStateOf<String?>(null)
    val activeVideoId: State<String?> = _activeVideoId

    /** Result whose thumbnail started the current embed — used for the banner label. */
    private val _activeVideoResult = mutableStateOf<SearchResult?>(null)
    val activeVideoResult: State<SearchResult?> = _activeVideoResult

    /** Download-sheet target (YouTube video result), or null when the sheet is closed. */
    private val _videoDownloadTarget = mutableStateOf<SearchResult?>(null)
    val videoDownloadTarget: State<SearchResult?> = _videoDownloadTarget

    fun playVideo(result: SearchResult) {
        youTubeVideoId(result.url)?.let { id ->
            _activeVideoResult.value = result
            _activeVideoId.value = id
        }
    }

    fun stopVideoPlayback() {
        // Clearing both leaves no dangling state: the overlay composable leaves the
        // composition, destroying the WebView player; the card list renders as before.
        _activeVideoId.value = null
        _activeVideoResult.value = null
    }

    fun showVideoDownloadSheet(result: SearchResult) { _videoDownloadTarget.value = result }
    fun dismissVideoDownloadSheet() { _videoDownloadTarget.value = null }

    /**
     * Downloads a YouTube video as MP3 via dedicated MP3 worker (Catalog pattern).
     * Falls back to Video worker MP3 path if dedicated worker unavailable.
     */
    fun downloadYouTubeMp3(videoUrl: String, title: String, thumbnailUrl: String?, context: android.content.Context) {
        viewModelScope.launch {
            val appContext = context.applicationContext
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                try {
                    val perm = androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS)
                    if (perm != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        android.util.Log.w("SearchViewModel", "POST_NOTIFICATIONS not granted — mp3 download will proceed but notification may be suppressed")
                    }
                } catch (_: Exception) {}
            }
            try { android.widget.Toast.makeText(context, context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_toast_mp3_downloading), android.widget.Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
            try {
                // Prefer dedicated MP3 worker (uses CatalogRepository fallback like Music)
                val mp3Request = androidx.work.OneTimeWorkRequestBuilder<com.frerox.toolz.worker.YouTubeMp3DownloadWorker>()
                    .setInputData(
                        androidx.work.workDataOf(
                            com.frerox.toolz.worker.YouTubeMp3DownloadWorker.KEY_SOURCE_URL to videoUrl,
                            com.frerox.toolz.worker.YouTubeMp3DownloadWorker.KEY_TITLE to title,
                            com.frerox.toolz.worker.YouTubeMp3DownloadWorker.KEY_THUMBNAIL_URL to thumbnailUrl,
                        )
                    )
                    .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                    .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(com.frerox.toolz.worker.YouTubeMp3DownloadWorker.TAG_MP3_DOWNLOAD)
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueueUniqueWork(
                    "mp3_download_${title.hashCode()}_${System.currentTimeMillis()}",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    mp3Request,
                )
                android.util.Log.d("SearchViewModel", "MP3 download enqueued: $videoUrl id=${mp3Request.id}")
                kotlinx.coroutines.delay(600)
                try { android.widget.Toast.makeText(context, context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_toast_mp3_queued, title), android.widget.Toast.LENGTH_LONG).show() } catch (_: Exception) {}
            } catch (e: Exception) {
                android.util.Log.e("SearchViewModel", "MP3 enqueue failed, fallback to Video worker MP3", e)
                // Fallback to Video worker MP3 path
                downloadYouTubeVideo(videoUrl, title, thumbnailUrl, "MP3", context)
            }
        }
    }

    /**
     * Downloads a YouTube video through the music player tool's yt-dlp pipeline,
     * routed at the requested quality (240p–1080p + MP3 audio).
     */
    fun downloadYouTubeVideo(videoUrl: String, title: String, thumbnailUrl: String?, quality: String, context: android.content.Context) {
        viewModelScope.launch {
            val appContext = context.applicationContext
            // Check notification permission on Android 13+ — do not block download, just log and toast fallback
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                try {
                    val perm = androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS)
                    if (perm != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        android.util.Log.w("SearchViewModel", "POST_NOTIFICATIONS not granted — download will proceed but notification may be suppressed")
                    }
                    val nm = androidx.core.app.NotificationManagerCompat.from(appContext)
                    if (!nm.areNotificationsEnabled()) {
                        android.util.Log.w("SearchViewModel", "Notifications disabled globally — download enqueued without visible notification")
                    }
                } catch (_: Exception) {}
            }
            try {
                android.widget.Toast.makeText(context, context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_toast_video_downloading, quality), android.widget.Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
            try {
                val request = androidx.work.OneTimeWorkRequestBuilder<com.frerox.toolz.worker.VideoDownloadWorker>()
                    .setInputData(
                        androidx.work.workDataOf(
                            com.frerox.toolz.worker.VideoDownloadWorker.KEY_SOURCE_URL to videoUrl,
                            com.frerox.toolz.worker.VideoDownloadWorker.KEY_TITLE to title,
                            com.frerox.toolz.worker.VideoDownloadWorker.KEY_THUMBNAIL_URL to thumbnailUrl,
                            com.frerox.toolz.worker.VideoDownloadWorker.KEY_QUALITY to quality,
                        )
                    )
                    .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                    .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(com.frerox.toolz.worker.VideoDownloadWorker.TAG_VIDEO_DOWNLOAD)
                    .build()
                androidx.work.WorkManager.getInstance(appContext).enqueueUniqueWork(
                    "video_download_${title.hashCode()}_${System.currentTimeMillis()}",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    request,
                )
                android.util.Log.d("SearchViewModel", "Video download enqueued: $videoUrl $quality id=${request.id}")
                kotlinx.coroutines.delay(600)
                try {
                    android.widget.Toast.makeText(context, context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_toast_video_queued, title, quality), android.widget.Toast.LENGTH_LONG).show()
                } catch (_: Exception) {}
            } catch (e: Exception) {
                android.util.Log.e("SearchViewModel", "Video download enqueue failed", e)
                try { android.widget.Toast.makeText(context, context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_toast_queue_failed, e.message ?: ""), android.widget.Toast.LENGTH_LONG).show() } catch (_: Exception) {}
                // Fallback notification if enqueue fails
                try {
                    val nm = appContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                    nm?.notify((System.currentTimeMillis()%Int.MAX_VALUE).toInt(), androidx.core.app.NotificationCompat.Builder(appContext, com.frerox.toolz.util.NotificationHelper.CHANNEL_VIDEO_DOWNLOADS)
                        .setContentTitle(appContext.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_notif_queue_failed_title)).setContentText(e.message?.take(60) ?: appContext.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_error_unknown)).setSmallIcon(com.frerox.toolz.R.drawable.ic_launcher_foreground).setAutoCancel(true).build())
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Downloads an image result into Pictures/Toolz via MediaStore. Uses a direct
     * OkHttp fetch instead of DownloadManager: search-engine thumbnail proxies
     * (imgs.search.brave.com, ts*.mm.bing.net) reject DownloadManager's requests
     * (missing headers → error pages silently saved as 0-byte files).
     */
    fun downloadImage(imageUrl: String, context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Polished image download with Toolz branding and correct progress
            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            val channelId = "toolz_image_downloads"
            val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            fun createImageChannel() {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val channel = android.app.NotificationChannel(channelId, context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_img_channel), android.app.NotificationManager.IMPORTANCE_LOW).apply {
                        description = context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_img_channel_desc)
                        setShowBadge(false)
                        enableVibration(false)
                    }
                    try { notificationManager?.createNotificationChannel(channel) } catch (_: Exception) {}
                }
            }
            fun showImageNotification(title: String, progress: Int, ongoing: Boolean) {
                try {
                    val large = com.frerox.toolz.util.NotificationHelper.toolzLargeIcon(context)
                    val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                        .setContentTitle(title)
                        .setSmallIcon(com.frerox.toolz.R.drawable.ic_launcher_foreground)
                        .setLargeIcon(large)
                        .setOngoing(ongoing)
                        .setOnlyAlertOnce(true)
                        .setAutoCancel(!ongoing)
                        .setProgress(100, progress.coerceIn(0,100), progress==0 && ongoing)
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                        .setCategory(androidx.core.app.NotificationCompat.CATEGORY_PROGRESS)
                    if (progress in 1..99) builder.setContentText(context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_notif_progress, progress))
                    else if (!ongoing) builder.setContentText(context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_notif_tap_gallery))
                    // Rounded corners via largeIcon is already circular foreground
                    notificationManager?.notify(notificationId, builder.build())
                } catch (_: Exception) {}
            }
            createImageChannel()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                try { android.widget.Toast.makeText(context, context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_toast_image_downloading), android.widget.Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
                showImageNotification(context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_toast_image_downloading), 0, true)
            }
            showImageNotification(context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_toast_image_downloading), 15, true)
            val result = runCatching {
                val normalizedUrl = when {
                    imageUrl.startsWith("//") -> "https:$imageUrl"
                    imageUrl.startsWith("/") -> imageUrl // shouldn't happen for image search but guard
                    else -> imageUrl
                }
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url(normalizedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                    .header("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
                    .header("Referer", "https://www.google.com/")
                    .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val bytes = response.body?.bytes() ?: error("Empty body")
                    check(bytes.size > 512) { "Response too small (${bytes.size}B) — likely an error page" }

                    val mime = response.header("Content-Type")?.substringBefore(";")
                        ?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
                    val ext = when (mime) {
                        "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"
                        "image/jpeg" -> "jpg"; "image/jpg" -> "jpg"
                        else -> "jpg"
                    }
                    val name = "toolz_${System.currentTimeMillis()}.$ext"

                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, mime)
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                            android.os.Environment.DIRECTORY_PICTURES + "/Toolz")
                        put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("MediaStore insert failed")
                    resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Stream open failed")
                    values.clear()
                    values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
            }
            // Progress 60% while saving
            try { showImageNotification(context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_toast_saving_image), 60, true) } catch (_: Exception) {}
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                result.onSuccess {
                    try { android.widget.Toast.makeText(context, context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_toast_image_saved), android.widget.Toast.LENGTH_LONG).show() } catch (_: Exception) {}
                    try {
                        val large = try { android.graphics.BitmapFactory.decodeResource(context.resources, com.frerox.toolz.R.drawable.ic_launcher_foreground) } catch (_: Exception) { null }
                        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                            .setContentTitle(context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_notif_image_saved_title))
                            .setContentText(context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_notif_saved_gallery))
                            .setSmallIcon(com.frerox.toolz.R.drawable.ic_launcher_foreground)
                            .setLargeIcon(large)
                            .setAutoCancel(true)
                            .setOnlyAlertOnce(false)
                            .setOngoing(false)
                            .setProgress(0,0,false)
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_PROGRESS)
                        notificationManager?.notify(notificationId, builder.build())
                        // Auto-dismiss after 3s to polished UX — cancel progress notification
                        kotlinx.coroutines.delay(3000)
                        try { notificationManager?.cancel(notificationId) } catch (_: Exception) {}
                    } catch (_: Exception) {}
                    android.util.Log.d("SearchViewModel", "Image download success: $imageUrl")
                }.onFailure {
                    try { android.widget.Toast.makeText(context, context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_toast_download_failed, it.message ?: ""), android.widget.Toast.LENGTH_LONG).show() } catch (_: Exception) {}
                    try {
                        val large = try { android.graphics.BitmapFactory.decodeResource(context.resources, com.frerox.toolz.R.drawable.ic_launcher_foreground) } catch (_: Exception) { null }
                        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                            .setContentTitle(context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_notif_image_failed_title))
                            .setContentText(it.message?.take(80) ?: context.getString(com.frerox.toolz.R.string.st_SearchScreen_ws_error_unknown))
                            .setSmallIcon(com.frerox.toolz.R.drawable.ic_launcher_foreground)
                            .setLargeIcon(large)
                            .setAutoCancel(true)
                            .setOnlyAlertOnce(false)
                        notificationManager?.notify(notificationId, builder.build())
                    } catch (_: Exception) {}
                    android.util.Log.w("SearchViewModel", "Image download failed for $imageUrl: ${it.message}", it)
                }
            }
        }
    }
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
