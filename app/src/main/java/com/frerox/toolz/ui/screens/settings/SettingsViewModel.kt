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

    val shutterSoundUri: StateFlow<String?> = repository.shutterSoundUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Notifications
    val notificationsEnabled: StateFlow<Boolean> = repository.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val stepNotifications: StateFlow<Boolean> = repository.stepNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val timerNotifications: StateFlow<Boolean> = repository.timerNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val voiceRecordNotifications: StateFlow<Boolean> = repository.voiceRecordNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Widgets
    val widgetBackgroundColor: StateFlow<Int> = repository.widgetBackgroundColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0xFFFFFFFF.toInt())
    val widgetAccentColor: StateFlow<Int> = repository.widgetAccentColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0xFF4CAF50.toInt())
    val widgetOpacity: StateFlow<Float> = repository.widgetOpacity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.9f)

    fun setStepGoal(goal: Int) { viewModelScope.launch { repository.setStepGoal(goal) } }
    fun setRingtoneUri(uri: String) { viewModelScope.launch { repository.setRingtoneUri(uri) } }
    fun setThemeMode(mode: String) { viewModelScope.launch { repository.setThemeMode(mode) } }
    fun setDynamicColor(enabled: Boolean) { viewModelScope.launch { repository.setDynamicColor(enabled) } }
    fun setCustomPrimaryColor(color: Int?) { viewModelScope.launch { repository.setCustomPrimaryColor(color) } }
    fun setShutterSoundEnabled(enabled: Boolean) { viewModelScope.launch { repository.setShutterSoundEnabled(enabled) } }
    fun setShutterSoundUri(uri: String) { viewModelScope.launch { repository.setShutterSoundUri(uri) } }

    // Notification setters
    fun setNotificationsEnabled(enabled: Boolean) { viewModelScope.launch { repository.setNotificationsEnabled(enabled) } }
    fun setStepNotifications(enabled: Boolean) { viewModelScope.launch { repository.setStepNotifications(enabled) } }
    fun setTimerNotifications(enabled: Boolean) { viewModelScope.launch { repository.setTimerNotifications(enabled) } }
    fun setVoiceRecordNotifications(enabled: Boolean) { viewModelScope.launch { repository.setVoiceRecordNotifications(enabled) } }

    // Widget setters
    fun setWidgetBackgroundColor(color: Int) { viewModelScope.launch { repository.setWidgetBackgroundColor(color) } }
    fun setWidgetAccentColor(color: Int) { viewModelScope.launch { repository.setWidgetAccentColor(color) } }
    fun setWidgetOpacity(opacity: Float) { viewModelScope.launch { repository.setWidgetOpacity(opacity) } }
}
