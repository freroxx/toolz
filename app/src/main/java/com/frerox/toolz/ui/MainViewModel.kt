package com.frerox.toolz.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    repository: SettingsRepository
) : ViewModel() {
    val themeMode: StateFlow<String> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val dynamicColor: StateFlow<Boolean> = repository.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val customPrimaryColor: StateFlow<Int?> = repository.customPrimaryColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
