package com.frerox.toolz.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.data.search.WebSearchRepository
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val active: Boolean = false,
    val adBlockEnabled: Boolean = true,
    val dnsProvider: String = "ADGUARD",
    val customDns: String = "",
    val recentDns: List<String> = emptyList()
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
        if (query.isBlank()) return
        _uiState.value = _uiState.value.copy(isLoading = true, active = false)
        viewModelScope.launch {
            repository.addHistory(query)
            val results = repository.search(query)
            _uiState.value = _uiState.value.copy(results = results, isLoading = false)
        }
    }

    fun onActiveChange(active: Boolean) {
        _uiState.value = _uiState.value.copy(active = active)
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

    fun dismissFirstTime() {
        viewModelScope.launch {
            settingsRepository.setSearchFirstTime(false)
        }
    }
}
