package com.frerox.toolz.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val themeMode = repository.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")
    val dynamicColor = repository.dynamicColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val customPrimaryColor = repository.customPrimaryColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val customSecondaryColor = repository.customSecondaryColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    val stepGoal = repository.stepGoal.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10000)
    val ringtoneUri = repository.ringtoneUri.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val shutterSoundEnabled = repository.shutterSoundEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val shutterSoundUri = repository.shutterSoundUri.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    val notificationsEnabled = repository.notificationsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val stepNotifications = repository.stepNotifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val timerNotifications = repository.timerNotifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val voiceRecordNotifications = repository.voiceRecordNotifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val musicNotifications = repository.musicNotifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val widgetBackgroundColor = repository.widgetBackgroundColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0xFFFFFFFF.toInt())
    val widgetAccentColor = repository.widgetAccentColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0xFF4CAF50.toInt())
    val widgetOpacity = repository.widgetOpacity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.9f)
    
    val hapticFeedback = repository.hapticFeedback.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val hapticIntensity = repository.hapticIntensity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.5f)
    val unitSystem = repository.unitSystem.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "METRIC")
    val showQibla = repository.showQibla.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val stepCounterEnabled = repository.stepCounterEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    
    val musicAudioFocus = repository.musicAudioFocus.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val musicShakeToSkip = repository.musicShakeToSkip.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val musicPlaybackSpeed = repository.musicPlaybackSpeed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)
    val musicEqualizerPreset = repository.musicEqualizerPreset.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Normal")
    val showMusicVisualizer = repository.showMusicVisualizer.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showToolzPill = repository.showToolzPill.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val userName = repository.userName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun setThemeMode(mode: String) { viewModelScope.launch { repository.setThemeMode(mode) } }
    fun setDynamicColor(enabled: Boolean) { viewModelScope.launch { repository.setDynamicColor(enabled) } }
    fun setCustomPrimaryColor(color: Int?) { viewModelScope.launch { repository.setCustomPrimaryColor(color) } }
    fun setCustomSecondaryColor(color: Int?) { viewModelScope.launch { repository.setCustomSecondaryColor(color) } }
    fun setStepGoal(goal: Int) { viewModelScope.launch { repository.setStepGoal(goal) } }
    fun setRingtoneUri(uri: String) { viewModelScope.launch { repository.setRingtoneUri(uri) } }
    fun setShutterSoundEnabled(enabled: Boolean) { viewModelScope.launch { repository.setShutterSoundEnabled(enabled) } }
    fun setShutterSoundUri(uri: String) { viewModelScope.launch { repository.setShutterSoundUri(uri) } }
    fun setNotificationsEnabled(enabled: Boolean) { viewModelScope.launch { repository.setNotificationsEnabled(enabled) } }
    fun setStepNotifications(enabled: Boolean) { viewModelScope.launch { repository.setStepNotifications(enabled) } }
    fun setTimerNotifications(enabled: Boolean) { viewModelScope.launch { repository.setTimerNotifications(enabled) } }
    fun setVoiceRecordNotifications(enabled: Boolean) { viewModelScope.launch { repository.setVoiceRecordNotifications(enabled) } }
    fun setMusicNotifications(enabled: Boolean) { viewModelScope.launch { repository.setMusicNotifications(enabled) } }
    fun setWidgetBackgroundColor(color: Int) { viewModelScope.launch { repository.setWidgetBackgroundColor(color) } }
    fun setWidgetAccentColor(color: Int) { viewModelScope.launch { repository.setWidgetAccentColor(color) } }
    fun setWidgetOpacity(opacity: Float) { viewModelScope.launch { repository.setWidgetOpacity(opacity) } }
    fun setHapticFeedback(enabled: Boolean) { viewModelScope.launch { repository.setHapticFeedback(enabled) } }
    fun setHapticIntensity(intensity: Float) { viewModelScope.launch { repository.setHapticIntensity(intensity) } }
    fun setUnitSystem(unit: String) { viewModelScope.launch { repository.setUnitSystem(unit) } }
    fun setShowQibla(enabled: Boolean) { viewModelScope.launch { repository.setShowQibla(enabled) } }
    fun setStepCounterEnabled(enabled: Boolean) { viewModelScope.launch { repository.setStepCounterEnabled(enabled) } }
    fun setMusicAudioFocus(enabled: Boolean) { viewModelScope.launch { repository.setMusicAudioFocus(enabled) } }
    fun setMusicShakeToSkip(enabled: Boolean) { viewModelScope.launch { repository.setMusicShakeToSkip(enabled) } }
    fun setMusicPlaybackSpeed(speed: Float) { viewModelScope.launch { repository.setMusicPlaybackSpeed(speed) } }
    fun setMusicEqualizerPreset(preset: String) { viewModelScope.launch { repository.setMusicEqualizerPreset(preset) } }
    fun setShowMusicVisualizer(enabled: Boolean) { viewModelScope.launch { repository.setShowMusicVisualizer(enabled) } }
    fun setShowToolzPill(enabled: Boolean) { viewModelScope.launch { repository.setShowToolzPill(enabled) } }
    
    fun resetOnboarding() {
        viewModelScope.launch {
            repository.setOnboardingCompleted(false)
        }
    }
}
