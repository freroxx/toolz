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
}
