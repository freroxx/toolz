/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.AiSettingsManager
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.DeviceSpecHelper
import com.frerox.toolz.data.update.UpdateCheckResult
import com.frerox.toolz.data.update.UpdateRepository
import com.frerox.toolz.util.shizuku.ShizukuHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val aiSettingsManager: AiSettingsManager,
    private val updateRepository: UpdateRepository
) : ViewModel() {

    data class OnboardingState(
        val name: String = "",
        val themeMode: String = "SYSTEM",
        val dynamicColor: Boolean = true,
        val backgroundGradient: Boolean = true,
        val performanceMode: Boolean = false,
        val groqApiKey: String = "",
        val notificationsEnabled: Boolean = true,
        val backgroundNotificationsEnabled: Boolean = true,
        val vaultEnabled: Boolean = true,
        val whisperNotificationsEnabled: Boolean = true,
        val shizukuAuthorized: Boolean = false,
        val deviceSpecs: DeviceSpecHelper.DeviceSpecs? = null,
        val updateState: UpdateCheckResult? = null,
        // Step 6 — Preferences (curated subset grouped into AI / Notifications /
        // Interface & HUD / News). Defaults mirror SettingsRepository.
        val offlineMode: Boolean = false,
        val autoOffline: Boolean = true,
        val offlinePopup: Boolean = true,
        val aiSearchChat: Boolean = true,
        val clipboardAi: Boolean = true,
        val aiClipboardMonitoring: Boolean = false,
        val pdfAiTools: Boolean = true,
        val notepadAiTools: Boolean = true,
        val musicAi: Boolean = true,
        val hapticFeedback: Boolean = true,
        val showRecentTools: Boolean = true,
        val showQuickNotes: Boolean = true,
        val showDashboardStats: Boolean = true,
        val showToolzPill: Boolean = true,
        val newsEnabled: Boolean = true,
        val newsNotifications: Boolean = true,
        // Step 7 — Language (codes match locales_config + Settings).
        val appLanguage: String = "en",
        // App Skin step — icon + skin (defaults mirror SettingsRepository)
        val iconPresetId: String = "grape-haze",
        val iconShape: String = "HEXAGON",
        val iconAdaptTheme: Boolean = true,
        val iconFollowDynamic: Boolean = true,
        val activeSkinId: String? = null,
    )

    private val _uiState = MutableStateFlow(OnboardingState())
    val uiState = _uiState.asStateFlow()

    init {
        val specs = DeviceSpecHelper.getDeviceSpecs(context)
        _uiState.update { it.copy(
            deviceSpecs = specs,
            performanceMode = specs.recommendPerformanceMode,
            shizukuAuthorized = ShizukuHelper.isAuthorized()
        ) }
    }

    fun refreshShizukuStatus() {
        _uiState.update { it.copy(shizukuAuthorized = ShizukuHelper.isAuthorized()) }
    }

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }
    fun updateTheme(mode: String) = _uiState.update { it.copy(themeMode = mode) }
    fun updateDynamicColor(enabled: Boolean) = _uiState.update { it.copy(dynamicColor = enabled) }
    fun updateGradient(enabled: Boolean) = _uiState.update { it.copy(backgroundGradient = enabled) }
    fun updatePerformanceMode(enabled: Boolean) = _uiState.update { it.copy(performanceMode = enabled) }
    fun updateGroqKey(key: String) = _uiState.update { it.copy(groqApiKey = key) }
    fun updateNotifications(enabled: Boolean) = _uiState.update { it.copy(notificationsEnabled = enabled) }
    fun updateBackgroundNotifications(enabled: Boolean) = _uiState.update { it.copy(backgroundNotificationsEnabled = enabled) }
    fun updateVault(enabled: Boolean) = _uiState.update { it.copy(vaultEnabled = enabled) }
    fun updateWhisperNotifications(enabled: Boolean) = _uiState.update { it.copy(whisperNotificationsEnabled = enabled) }
    fun updateOfflineMode(enabled: Boolean) = _uiState.update { it.copy(offlineMode = enabled) }
    fun updateAutoOffline(enabled: Boolean) = _uiState.update { it.copy(autoOffline = enabled) }
    fun updateOfflinePopup(enabled: Boolean) = _uiState.update { it.copy(offlinePopup = enabled) }
    fun updateAiSearchChat(enabled: Boolean) = _uiState.update { it.copy(aiSearchChat = enabled) }
    fun updateClipboardAi(enabled: Boolean) = _uiState.update { it.copy(clipboardAi = enabled) }
    fun updateAiClipboardMonitoring(enabled: Boolean) = _uiState.update { it.copy(aiClipboardMonitoring = enabled) }
    fun updatePdfAiTools(enabled: Boolean) = _uiState.update { it.copy(pdfAiTools = enabled) }
    fun updateNotepadAiTools(enabled: Boolean) = _uiState.update { it.copy(notepadAiTools = enabled) }
    fun updateMusicAi(enabled: Boolean) = _uiState.update { it.copy(musicAi = enabled) }
    fun updateHapticFeedback(enabled: Boolean) = _uiState.update { it.copy(hapticFeedback = enabled) }
    fun updateShowRecentTools(enabled: Boolean) = _uiState.update { it.copy(showRecentTools = enabled) }
    fun updateShowQuickNotes(enabled: Boolean) = _uiState.update { it.copy(showQuickNotes = enabled) }
    fun updateShowDashboardStats(enabled: Boolean) = _uiState.update { it.copy(showDashboardStats = enabled) }
    fun updateShowToolzPill(enabled: Boolean) = _uiState.update { it.copy(showToolzPill = enabled) }
    fun updateNewsEnabled(enabled: Boolean) = _uiState.update { it.copy(newsEnabled = enabled) }
    fun updateNewsNotifications(enabled: Boolean) = _uiState.update { it.copy(newsNotifications = enabled) }
    fun updateAppLanguage(lang: String) = _uiState.update { it.copy(appLanguage = lang) }
    fun updateIconPreset(id: String) = _uiState.update { it.copy(iconPresetId = id, activeSkinId = null) }
    fun updateIconShape(shape: String) = _uiState.update { it.copy(iconShape = shape) }
    fun updateIconAdaptTheme(enabled: Boolean) = _uiState.update { it.copy(iconAdaptTheme = enabled) }
    fun updateIconFollowDynamic(enabled: Boolean) = _uiState.update { it.copy(iconFollowDynamic = enabled) }
    fun applySkinPreset(skin: com.frerox.toolz.data.skin.SkinDto) = _uiState.update {
        it.copy(
            themeMode = skin.themeMode,
            dynamicColor = skin.dynamicColor,
            iconPresetId = skin.iconPresetId,
            iconShape = skin.iconShape,
            iconAdaptTheme = skin.iconAdaptTheme,
            iconFollowDynamic = skin.iconFollowDynamic,
            activeSkinId = skin.id,
        )
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(updateState = null) }
            delay(1500) // Aesthetic delay for animation
            val result = updateRepository.checkForUpdates(false)
            _uiState.update { it.copy(updateState = result) }
        }
    }

    fun finishOnboarding(onFinish: () -> Unit) {
        viewModelScope.launch {
            val s = uiState.value
            settingsRepository.setUserName(s.name)
            settingsRepository.setThemeMode(s.themeMode)
            settingsRepository.setDynamicColor(s.dynamicColor)
            settingsRepository.setBackgroundGradientEnabled(s.backgroundGradient)
            settingsRepository.setPerformanceMode(s.performanceMode)
            settingsRepository.setNotificationsEnabled(s.notificationsEnabled)
            settingsRepository.setBackgroundNotificationsEnabled(s.backgroundNotificationsEnabled)
            settingsRepository.setNotificationVaultEnabled(s.vaultEnabled)
            settingsRepository.setWhisperNotificationsEnabled(s.whisperNotificationsEnabled)
            // Preferences step
            settingsRepository.setOfflineModeEnabled(s.offlineMode)
            settingsRepository.setAutoOfflineEnabled(s.autoOffline)
            settingsRepository.setOfflinePopupEnabled(s.offlinePopup)
            settingsRepository.setAiSearchChatEnabled(s.aiSearchChat)
            settingsRepository.setClipboardAiEnabled(s.clipboardAi)
            settingsRepository.setAiClipboardMonitoringEnabled(s.aiClipboardMonitoring)
            settingsRepository.setPdfAiToolsEnabled(s.pdfAiTools)
            settingsRepository.setNotepadAiToolsEnabled(s.notepadAiTools)
            settingsRepository.setMusicAiEnabled(s.musicAi)
            settingsRepository.setHapticFeedback(s.hapticFeedback)
            settingsRepository.setShowRecentTools(s.showRecentTools)
            settingsRepository.setShowQuickNotes(s.showQuickNotes)
            settingsRepository.setShowDashboardStats(s.showDashboardStats)
            settingsRepository.setShowToolzPill(s.showToolzPill)
            settingsRepository.setNewsEnabled(s.newsEnabled)
            settingsRepository.setNewsNotificationsEnabled(s.newsNotifications)
            // Language step
            settingsRepository.setAppLanguage(s.appLanguage)
            // App Skin step
            settingsRepository.setAppIconPresetId(s.iconPresetId)
            settingsRepository.setAppIconShape(s.iconShape)
            settingsRepository.setAppIconAdaptTheme(s.iconAdaptTheme)
            settingsRepository.setAppIconFollowDynamic(s.iconFollowDynamic)
            settingsRepository.setActiveSkinId(s.activeSkinId)
            
            if (s.groqApiKey.isNotBlank()) {
                aiSettingsManager.setApiKey(s.groqApiKey, "Groq")
                aiSettingsManager.setAiProvider("Groq")
            }
            
            settingsRepository.setOnboardingCompleted(true)
            onFinish()
        }
    }

    fun skipOnboarding(onFinish: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
            onFinish()
        }
    }
}
