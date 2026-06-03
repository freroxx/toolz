package com.frerox.toolz.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.BackupWorker
import com.frerox.toolz.util.VibrationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    val vibrationManager: VibrationManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    val stepGoal = repository.stepGoal
    val themeMode = repository.themeMode
    val dynamicColor = repository.dynamicColor
    val customPrimaryColor = repository.customPrimaryColor
    val customSecondaryColor = repository.customSecondaryColor
    val backgroundGradientEnabled = repository.backgroundGradientEnabled
    val worldClockZones = repository.worldClockZones
    
    val dashboardView = repository.dashboardView
    val showRecentTools = repository.showRecentTools
    val showQuickNotes = repository.showQuickNotes
    val showDashboardStats = repository.showDashboardStats

    val notificationsEnabled = repository.notificationsEnabled
    val notificationVaultEnabled = repository.notificationVaultEnabled
    val stepNotifications = repository.stepNotifications
    val timerNotifications = repository.timerNotifications
    val voiceRecordNotifications = repository.voiceRecordNotifications
    val musicNotifications = repository.musicNotifications
    val fileConversionNotifications = repository.fileConversionNotifications
    val appUpdateNotifications = repository.appUpdateNotifications
    val taskReminderNotifications = repository.taskReminderNotifications
    val eventReminderNotifications = repository.eventReminderNotifications
    val pomodoroNotifications = repository.pomodoroNotifications
    val flashlightNotificationsEnabled = repository.flashlightNotificationsEnabled
    val notificationRetentionDays = repository.notificationRetentionDays

    val widgetBackgroundColor = repository.widgetBackgroundColor
    val widgetAccentColor = repository.widgetAccentColor
    val widgetOpacity = repository.widgetOpacity

    val hapticFeedback = repository.hapticFeedback
    val hapticIntensity = repository.hapticIntensity
    val unitSystem = repository.unitSystem
    val showQibla = repository.showQibla
    val stepCounterEnabled = repository.stepCounterEnabled
    val showToolzPill = repository.showToolzPill
    val fillThePillEnabled = repository.fillThePillEnabled
    val pillTodoEnabled = repository.pillTodoEnabled
    val pillFocusEnabled = repository.pillFocusEnabled
    val liveVpnNotifications = repository.liveVpnNotifications
    val liveDnsNotifications = repository.liveDnsNotifications

    fun setLiveVpnNotifications(enabled: Boolean) {
        viewModelScope.launch { repository.setLiveVpnNotifications(enabled) }
    }

    fun setLiveDnsNotifications(enabled: Boolean) {
        viewModelScope.launch { repository.setLiveDnsNotifications(enabled) }
    }
    val backupFrequency = repository.backupFrequency
    val userName = repository.userName
    val autoUpdateEnabled = repository.autoUpdateEnabled
    val offlineModeEnabled = repository.offlineModeEnabled

    val musicAudioFocus = repository.musicAudioFocus
    val musicShakeToSkip = repository.musicShakeToSkip
    val musicShakeSensitivity = repository.musicShakeSensitivity
    val musicPlaybackSpeed = repository.musicPlaybackSpeed
    val musicEqualizerPreset = repository.musicEqualizerPreset
    val showMusicVisualizer = repository.showMusicVisualizer
    val musicAiEnabled = repository.musicAiEnabled
    val musicKeepScreenOnLyrics = repository.musicKeepScreenOnLyrics
    val karaokeEnabled = repository.karaokeEnabled
    val aiClipboardMonitoringEnabled = repository.aiClipboardMonitoringEnabled

    val performanceMode = repository.performanceMode

    val converterCustomOutputPath = repository.converterCustomOutputPath
    val pdfAiOcrEnhance = repository.pdfAiOcrEnhance

    fun setStepGoal(goal: Int) = viewModelScope.launch { repository.setStepGoal(goal) }
    fun setThemeMode(mode: String) = viewModelScope.launch { repository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repository.setDynamicColor(enabled) }
    fun setCustomPrimaryColor(color: Int?) = viewModelScope.launch { repository.setCustomPrimaryColor(color) }
    fun setCustomSecondaryColor(color: Int?) = viewModelScope.launch { repository.setCustomSecondaryColor(color) }
    fun setBackgroundGradientEnabled(enabled: Boolean) = viewModelScope.launch { repository.setBackgroundGradientEnabled(enabled) }
    
    fun setDashboardView(view: String) = viewModelScope.launch { repository.setDashboardView(view) }
    fun setShowRecentTools(enabled: Boolean) = viewModelScope.launch { repository.setShowRecentTools(enabled) }
    fun setShowQuickNotes(enabled: Boolean) = viewModelScope.launch { repository.setShowQuickNotes(enabled) }
    fun setShowDashboardStats(enabled: Boolean) = viewModelScope.launch { repository.setShowDashboardStats(enabled) }

    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch { repository.setNotificationsEnabled(enabled) }
    fun setNotificationVaultEnabled(enabled: Boolean) = viewModelScope.launch { repository.setNotificationVaultEnabled(enabled) }
    fun setStepNotifications(enabled: Boolean) = viewModelScope.launch { repository.setStepNotifications(enabled) }
    fun setTimerNotifications(enabled: Boolean) = viewModelScope.launch { repository.setTimerNotifications(enabled) }
    fun setVoiceRecordNotifications(enabled: Boolean) = viewModelScope.launch { repository.setVoiceRecordNotifications(enabled) }
    fun setMusicNotifications(enabled: Boolean) = viewModelScope.launch { repository.setMusicNotifications(enabled) }
    fun setFileConversionNotifications(enabled: Boolean) = viewModelScope.launch { repository.setFileConversionNotifications(enabled) }
    fun setAppUpdateNotifications(enabled: Boolean) = viewModelScope.launch { repository.setAppUpdateNotifications(enabled) }
    fun setTaskReminderNotifications(enabled: Boolean) = viewModelScope.launch { repository.setTaskReminderNotifications(enabled) }
    fun setEventReminderNotifications(enabled: Boolean) = viewModelScope.launch { repository.setEventReminderNotifications(enabled) }
    fun setPomodoroNotifications(enabled: Boolean) = viewModelScope.launch { repository.setPomodoroNotifications(enabled) }
    fun setFlashlightNotificationsEnabled(enabled: Boolean) = viewModelScope.launch { repository.setFlashlightNotificationsEnabled(enabled) }
    fun setNotificationRetentionDays(days: Int) = viewModelScope.launch { repository.setNotificationRetentionDays(days) }

    fun setWidgetBackgroundColor(color: Int) = viewModelScope.launch { repository.setWidgetBackgroundColor(color) }
    fun setWidgetAccentColor(color: Int) = viewModelScope.launch { repository.setWidgetAccentColor(color) }
    fun setWidgetOpacity(opacity: Float) = viewModelScope.launch { repository.setWidgetOpacity(opacity) }

    fun setHapticFeedback(enabled: Boolean) = viewModelScope.launch { repository.setHapticFeedback(enabled) }
    fun setHapticIntensity(intensity: Float) = viewModelScope.launch { repository.setHapticIntensity(intensity) }
    fun setUnitSystem(unit: String) = viewModelScope.launch { repository.setUnitSystem(unit) }
    fun setShowQibla(enabled: Boolean) = viewModelScope.launch { repository.setShowQibla(enabled) }
    fun setStepCounterEnabled(enabled: Boolean) = viewModelScope.launch { repository.setStepCounterEnabled(enabled) }
    fun setShowToolzPill(enabled: Boolean) = viewModelScope.launch { repository.setShowToolzPill(enabled) }
    fun setFillThePillEnabled(enabled: Boolean) = viewModelScope.launch { repository.setFillThePillEnabled(enabled) }
    fun setPillTodoEnabled(enabled: Boolean) = viewModelScope.launch { repository.setPillTodoEnabled(enabled) }
    fun setPillFocusEnabled(enabled: Boolean) = viewModelScope.launch { repository.setPillFocusEnabled(enabled) }
    fun setBackupFrequency(freq: String) = viewModelScope.launch { 
        repository.setBackupFrequency(freq)
        scheduleBackup(freq)
    }
    fun setUserName(name: String) = viewModelScope.launch { repository.setUserName(name) }
    fun setAutoUpdateEnabled(enabled: Boolean) = viewModelScope.launch { repository.setAutoUpdateEnabled(enabled) }
    fun setOfflineModeEnabled(enabled: Boolean) = viewModelScope.launch { repository.setOfflineModeEnabled(enabled) }

    fun setMusicAudioFocus(enabled: Boolean) = viewModelScope.launch { repository.setMusicAudioFocus(enabled) }
    fun setMusicShakeToSkip(enabled: Boolean) = viewModelScope.launch { repository.setMusicShakeToSkip(enabled) }
    fun setMusicShakeSensitivity(sensitivity: Float) = viewModelScope.launch { repository.setMusicShakeSensitivity(sensitivity) }
    fun setMusicPlaybackSpeed(speed: Float) = viewModelScope.launch { repository.setMusicPlaybackSpeed(speed) }
    fun setShowMusicVisualizer(enabled: Boolean) = viewModelScope.launch { repository.setShowMusicVisualizer(enabled) }
    fun setMusicAiEnabled(enabled: Boolean) = viewModelScope.launch { repository.setMusicAiEnabled(enabled) }
    fun setMusicKeepScreenOnLyrics(enabled: Boolean) = viewModelScope.launch { repository.setMusicKeepScreenOnLyrics(enabled) }
    fun setKaraokeEnabled(enabled: Boolean) = viewModelScope.launch { repository.setKaraokeEnabled(enabled) }

    fun setPerformanceMode(enabled: Boolean) = viewModelScope.launch { repository.setPerformanceMode(enabled) }

    fun setConverterCustomOutputPath(path: String?) = viewModelScope.launch { repository.setConverterCustomOutputPath(path) }

    fun setPdfAiOcrEnhance(enabled: Boolean) = viewModelScope.launch { repository.setPdfAiOcrEnhance(enabled) }
    fun setAiClipboardMonitoringEnabled(enabled: Boolean) = viewModelScope.launch { repository.setAiClipboardMonitoringEnabled(enabled) }

    fun scheduleBackup(frequency: String) {
        val workManager = WorkManager.getInstance(context)
        val repeatInterval = when(frequency) {
            "Daily" -> 1L
            "Weekly" -> 7L
            "Monthly" -> 30L
            else -> 0L
        }

        if (repeatInterval == 0L) {
            workManager.cancelUniqueWork("backup_work")
            return
        }

        val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(repeatInterval, java.util.concurrent.TimeUnit.DAYS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "backup_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun resetOnboarding() = viewModelScope.launch {
        repository.setOnboardingCompleted(false)
        repository.setUserName("")
    }

}
