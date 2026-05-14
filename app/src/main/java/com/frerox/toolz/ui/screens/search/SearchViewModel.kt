package com.frerox.toolz.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.search.BookmarkEntry
import com.frerox.toolz.data.search.QuickLinkEntry
import com.frerox.toolz.data.search.SearchHistoryEntry
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.data.search.WebSearchRepository
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.frerox.toolz.data.browser.TabManager
import com.frerox.toolz.data.browser.TabEntry
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: String? = null,
    val active: Boolean = false,
    val adBlockEnabled: Boolean = true,
    val dnsProvider: String = "ADGUARD",
    val customDns: String = "",
    val recentDns: List<String> = emptyList(),
    val isIncognito: Boolean = false,
    val searchEngine: String = "DUCKDUCKGO",
    val safeSearch: Boolean = true,
    val region: String = "wt-wt",
    val customEngineUrl: String = "",
    val tabs: List<TabEntry> = emptyList(),
    val activeTabId: String? = null,
    val userName: String = "",
    val searchAutofillEnabled: Boolean = true
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: WebSearchRepository,
    private val settingsRepository: SettingsRepository,
    private val tabManager: TabManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    val history = repository.history
    val bookmarks = repository.bookmarks
    val quickLinks = repository.quickLinks
    val isFirstTime = settingsRepository.searchFirstTime
    val adBlockEnabled = settingsRepository.searchAdBlockEnabled
    val dnsProvider = settingsRepository.searchDnsProvider
    val customDns = settingsRepository.searchCustomDns
    val recentDns = settingsRepository.searchRecentDns
    val searchEngine = settingsRepository.searchEngine
    val safeSearch = settingsRepository.searchSafeSearch
    val region = settingsRepository.searchRegion
    val customEngineUrl = settingsRepository.searchCustomEngineUrl
    val isIncognito = settingsRepository.searchIncognitoEnabled
    val userName = settingsRepository.userName
    val searchAutofillEnabled = settingsRepository.searchAutofillEnabled

    init {
        viewModelScope.launch {
            userName.collect { name ->
                _uiState.value = _uiState.value.copy(userName = name)
            }
        }
        viewModelScope.launch {
            isIncognito.collect { enabled ->
                _uiState.value = _uiState.value.copy(isIncognito = enabled)
            }
        }
        viewModelScope.launch {
            tabManager.tabs.collect { tabs ->
                _uiState.value = _uiState.value.copy(tabs = tabs)
            }
        }
        viewModelScope.launch {
            tabManager.activeTabId.collect { id ->
                _uiState.value = _uiState.value.copy(activeTabId = id)
            }
        }
        viewModelScope.launch {
            adBlockEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(adBlockEnabled = enabled)
            }
        }
        viewModelScope.launch {
            dnsProvider.collect { provider ->
                _uiState.value = _uiState.value.copy(dnsProvider = provider)
            }
        }
        viewModelScope.launch {
            searchAutofillEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(searchAutofillEnabled = enabled)
            }
        }
        viewModelScope.launch {
            customDns.collect { dns ->
                _uiState.value = _uiState.value.copy(customDns = dns)
            }
        }
        viewModelScope.launch {
            recentDns.collect { recent ->
                _uiState.value = _uiState.value.copy(recentDns = recent.toList())
            }
        }
        viewModelScope.launch {
            searchEngine.collect { engine ->
                _uiState.value = _uiState.value.copy(searchEngine = engine)
            }
        }
        viewModelScope.launch {
            safeSearch.collect { enabled ->
                _uiState.value = _uiState.value.copy(safeSearch = enabled)
            }
        }
        viewModelScope.launch {
            region.collect { value ->
                _uiState.value = _uiState.value.copy(region = value)
            }
        }
        viewModelScope.launch {
            customEngineUrl.collect { url ->
                _uiState.value = _uiState.value.copy(customEngineUrl = url)
            }
        }
    }

    private var suggestionJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        
        suggestionJob?.cancel()
        if (newQuery.length >= 2) {
            suggestionJob = viewModelScope.launch {
                delay(300)
                val suggestions = repository.fetchSuggestions(newQuery)
                _uiState.value = _uiState.value.copy(suggestions = suggestions)
            }
        } else {
            _uiState.value = _uiState.value.copy(suggestions = emptyList())
        }
    }

    fun onSearch(query: String) {
        if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), query = "", active = false, error = null, canLoadMore = false)
            return
        }
        
        suggestionJob?.cancel()
        val currentState = _uiState.value
        _uiState.value = currentState.copy(query = query, isLoading = true, active = false, error = null, suggestions = emptyList())
        
        viewModelScope.launch {
            try {
                if (!currentState.isIncognito) {
                    repository.addHistory(query)
                }
                val results = repository.search(query)
                _uiState.value = _uiState.value.copy(
                    results = results, 
                    isLoading = false, 
                    canLoadMore = results.isNotEmpty(),
                    error = if (results.isEmpty()) "No results found" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage ?: "Search failed")
            }
        }
    }

    fun loadMore() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || !currentState.canLoadMore || currentState.query.isEmpty()) return
        
        if (currentState.results.size >= 500) {
            _uiState.value = currentState.copy(canLoadMore = false)
            return
        }

        _uiState.value = currentState.copy(isLoadingMore = true)
        viewModelScope.launch {
            try {
                val offset = currentState.results.size
                val newResults = repository.search(currentState.query, offset)
                
                if (newResults.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false, canLoadMore = false)
                } else {
                    val combined = currentState.results + newResults
                    val cappedResults = combined.take(500)
                    _uiState.value = _uiState.value.copy(
                        results = cappedResults,
                        isLoadingMore = false,
                        canLoadMore = cappedResults.size < 500 && newResults.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    fun onActiveChange(active: Boolean) {
        _uiState.value = _uiState.value.copy(active = active)
    }

    fun toggleIncognito(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSearchIncognitoEnabled(enabled)
        }
    }

    fun toggleAdBlock(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSearchAdBlockEnabled(enabled)
        }
    }

    fun setDnsProvider(provider: String) {
        viewModelScope.launch {
            settingsRepository.setDnsProvider(provider)
        }
    }

    fun setCustomDns(dns: String) {
        viewModelScope.launch {
            settingsRepository.setCustomDns(dns)
        }
    }

    fun setSearchEngine(engine: String) {
        viewModelScope.launch {
            settingsRepository.setSearchEngine(engine)
        }
    }

    fun setSafeSearch(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSearchSafeSearch(enabled)
        }
    }

    fun setRegion(region: String) {
        viewModelScope.launch {
            settingsRepository.setSearchRegion(region)
        }
    }

    fun setCustomEngineUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setSearchCustomEngineUrl(url)
        }
    }

    fun removeRecentDns(dns: String) {
        viewModelScope.launch {
            settingsRepository.removeRecentDns(dns)
        }
    }

    fun updateBookmark(id: Long, title: String, url: String) {
        viewModelScope.launch {
            repository.updateBookmark(id, title, url)
        }
    }

    fun updateQuickLink(id: Long, title: String, url: String) {
        viewModelScope.launch {
            repository.updateQuickLink(id, title, url)
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch {
            repository.deleteHistory(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch {
            repository.removeBookmark(url)
        }
    }

    fun addQuickLink(title: String, url: String) {
        viewModelScope.launch {
            repository.addQuickLink(title, url)
        }
    }

    fun removeQuickLink(id: Long) {
        viewModelScope.launch {
            repository.removeQuickLink(id)
        }
    }

    fun reorderQuickLinks(from: Int, to: Int) {
        viewModelScope.launch {
            val currentLinks = repository.quickLinks.firstOrNull() ?: return@launch
            val mutableLinks = currentLinks.toMutableList()
            if (from !in mutableLinks.indices || to !in mutableLinks.indices) return@launch
            
            val item = mutableLinks.removeAt(from)
            mutableLinks.add(to, item)
            
            // Update sortOrder for all items
            val updatedLinks = mutableLinks.mapIndexed { index, link ->
                link.copy(sortOrder = index)
            }
            repository.updateQuickLinks(updatedLinks)
        }
    }

    fun dismissFirstTime() {
        viewModelScope.launch {
            settingsRepository.setSearchFirstTime(false)
        }
    }

    fun setSecurityPreset(preset: String) {
        viewModelScope.launch {
            when (preset) {
                "LOW" -> {
                    settingsRepository.setSearchAdBlockEnabled(true)
                    settingsRepository.setDnsProvider("ADGUARD")
                    settingsRepository.setSearchSafeSearch(false)
                }
                "BASIC" -> {
                    settingsRepository.setSearchAdBlockEnabled(true)
                    settingsRepository.setDnsProvider("CLOUDFLARE")
                    settingsRepository.setSearchEngine("DUCKDUCKGO")
                    settingsRepository.setSearchSafeSearch(true)
                }
                "MAX" -> {
                    settingsRepository.setSearchAdBlockEnabled(true)
                    settingsRepository.setDnsProvider("QUAD9")
                    settingsRepository.setSearchEngine("DUCKDUCKGO")
                    settingsRepository.setSearchSafeSearch(true)
                    settingsRepository.setSearchIncognitoEnabled(true)
                }
            }
        }
    }

    fun toggleAutofill(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSearchAutofillEnabled(enabled)
        }
    }

    fun openTab(url: String) {
        tabManager.addTab(url)
    }

    fun closeTab(id: String) {
        tabManager.removeTab(id)
    }

    fun switchTab(id: String) {
        tabManager.switchTab(id)
    }

    fun toggleBookmark(result: SearchResult) {
        viewModelScope.launch {
            if (repository.isBookmarked(result.url)) {
                repository.removeBookmark(result.url)
            } else {
                repository.addBookmark(result.title, result.url)
            }
        }
    }
}
