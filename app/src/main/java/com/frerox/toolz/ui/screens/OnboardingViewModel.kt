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
        val vaultEnabled: Boolean = true,
        val shizukuAuthorized: Boolean = false,
        val deviceSpecs: DeviceSpecHelper.DeviceSpecs? = null,
        val updateState: UpdateCheckResult? = null
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
    fun updateVault(enabled: Boolean) = _uiState.update { it.copy(vaultEnabled = enabled) }

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
            settingsRepository.setNotificationVaultEnabled(s.vaultEnabled)
            
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
