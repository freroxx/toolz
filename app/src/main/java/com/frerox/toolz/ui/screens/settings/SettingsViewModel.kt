package com.frerox.toolz.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    val stepGoal: StateFlow<Int> = repository.stepGoal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10000)

    val ringtoneUri: StateFlow<String?> = repository.ringtoneUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val themeMode: StateFlow<String> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val dynamicColor: StateFlow<Boolean> = repository.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val customPrimaryColor: StateFlow<Int?> = repository.customPrimaryColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val shutterSoundEnabled: StateFlow<Boolean> = repository.shutterSoundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setStepGoal(goal: Int) {
        viewModelScope.launch {
            repository.setStepGoal(goal)
        }
    }

    fun setRingtoneUri(uri: String) {
        viewModelScope.launch {
            repository.setRingtoneUri(uri)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDynamicColor(enabled)
        }
    }

    fun setCustomPrimaryColor(color: Int?) {
        viewModelScope.launch {
            repository.setCustomPrimaryColor(color)
        }
    }

    fun setShutterSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setShutterSoundEnabled(enabled)
        }
    }
}
