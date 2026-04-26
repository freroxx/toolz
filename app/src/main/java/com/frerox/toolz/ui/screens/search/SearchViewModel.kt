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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: String? = null,
    val active: Boolean = false,
    val adBlockEnabled: Boolean = true,
    val dnsProvider: String = "ADGUARD",
    val customDns: String = "",
    val recentDns: List<String> = emptyList(),
    val isIncognito: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: WebSearchRepository,
    private val settingsRepository: SettingsRepository
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

    init {
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
            customDns.collect { dns ->
                _uiState.value = _uiState.value.copy(customDns = dns)
            }
        }
        viewModelScope.launch {
            recentDns.collect { recent ->
                _uiState.value = _uiState.value.copy(recentDns = recent.toList())
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
    }

    fun onSearch(query: String) {
        if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), query = "", active = false, error = null, canLoadMore = false)
            return
        }
        val currentState = _uiState.value
        _uiState.value = currentState.copy(query = query, isLoading = true, active = false, error = null)
        viewModelScope.launch {
            try {
                if (!currentState.isIncognito) {
                    repository.addHistory(query)
                }
                val results = repository.search(query)
                _uiState.value = _uiState.value.copy(results = results, isLoading = false, canLoadMore = results.isNotEmpty())
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
        _uiState.value = _uiState.value.copy(isIncognito = enabled)
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
}
