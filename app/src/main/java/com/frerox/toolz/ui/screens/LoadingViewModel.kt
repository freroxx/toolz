package com.frerox.toolz.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.AiSettingsManager
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoadingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val aiSettingsManager: AiSettingsManager,
    private val updateRepository: UpdateRepository
) : ViewModel() {

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    private val _loadingMessage = MutableStateFlow("PREPARING WORKSPACE")
    val loadingMessage = _loadingMessage.asStateFlow()

    init {
        performInitialization()
    }

    private fun performInitialization() {
        viewModelScope.launch {
            val lastLoading = settingsRepository.lastLoadingTime.first()
            val currentTime = System.currentTimeMillis()
            val shouldSkipLoading = currentTime - lastLoading < 5 * 60 * 1000 // 5 minutes threshold

            if (shouldSkipLoading) {
                _isInitialized.value = true
                return@launch
            }

            val startTime = System.currentTimeMillis()

            // 1. Sync AI keys
            _loadingMessage.value = "SYNCING INTELLIGENCE"
            try {
                aiSettingsManager.syncRemoteKeys()
            } catch (e: Exception) {
                // Ignore sync errors during loading
            }

            // 2. Check for updates if needed
            _loadingMessage.value = "CHECKING FOR UPDATES"
            try {
                val lastCheck = settingsRepository.lastUpdateCheck.first()
                if (System.currentTimeMillis() - lastCheck > 24 * 60 * 60 * 1000) {
                    updateRepository.checkForUpdates()
                    settingsRepository.setLastUpdateCheck(System.currentTimeMillis())
                }
            } catch (e: Exception) {
                // Ignore update check errors during loading
            }

            // 3. Ensure a minimum loading time for visual consistency if it was too fast
            val elapsedTime = System.currentTimeMillis() - startTime
            val minLoadingTime = 1500L // Increased for better visual feedback
            if (elapsedTime < minLoadingTime) {
                delay(minLoadingTime - elapsedTime)
            }

            settingsRepository.setLastLoadingTime(System.currentTimeMillis())
            _loadingMessage.value = "READY"
            _isInitialized.value = true
        }
    }
}
