package com.frerox.toolz.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.browser.AdBlockList
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdBlockSettingsUiState(
    val blocklists: Set<String> = emptySet(),
    val allowlists: Set<String> = emptySet(),
    val nextDnsId: String = "",
    val nextDnsUrl: String = "",
)

@HiltViewModel
class AdBlockSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<AdBlockSettingsUiState> = combine(
        settingsRepository.searchAdBlockBlocklists,
        settingsRepository.searchAdBlockAllowlists,
        settingsRepository.searchNextDnsId,
        settingsRepository.searchNextDnsDnsUrl
    ) { blocked, allowed, id, url ->
        // Sync singleton when data changes
        AdBlockList.updateCustomLists(blocked, allowed)
        
        AdBlockSettingsUiState(
            blocklists = blocked,
            allowlists = allowed,
            nextDnsId = id,
            nextDnsUrl = url
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdBlockSettingsUiState())

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
        viewModelScope.launch {
            settingsRepository.setSearchNextDnsId(id.trim())
        }
    }

    fun setNextDnsUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setSearchNextDnsDnsUrl(url.trim())
        }
    }
}
