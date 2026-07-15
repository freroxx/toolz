package com.frerox.toolz.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val updateRepository: UpdateRepository,
) : ViewModel() {

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    private val _isVisible = MutableStateFlow(true)
    val isVisible = _isVisible.asStateFlow()

    private val _loadingMessage = MutableStateFlow("PREPARING WORKSPACE")
    val loadingMessage = _loadingMessage.asStateFlow()

    // Deterministic progress [0f → 1f] — wired to ExpressiveContainedLoadingIndicator
    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress = _loadingProgress.asStateFlow()

    init {
        performInitialization()
    }

    fun skipLoading() {
        viewModelScope.launch {
            _loadingProgress.value = 1f
            _isInitialized.value = true
            _isVisible.value = false
        }
    }

    private fun performInitialization() {
        viewModelScope.launch {
            val lastLoading = settingsRepository.lastLoadingTime.first()
            val currentTime = System.currentTimeMillis()
            val shouldSkipLoading = currentTime - lastLoading < 5 * 60 * 1000L // 5-minute threshold

            if (shouldSkipLoading) {
                // Fast-path: snap to 100% and exit immediately
                _loadingProgress.value = 1f
                _isInitialized.value = true
                delay(100) // Minimal breather for Compose to render before hiding
                _isVisible.value = false
                return@launch
            }

            // ── Stage 1: Environment ready ────────────────────────────────
            _loadingMessage.value = "PREPARING WORKSPACE"
            _loadingProgress.value = 0.10f

            // ── Stage 2 + 3: Run update check ────
            _loadingMessage.value = "CHECKING FOR UPDATES"
            _loadingProgress.value = 0.30f

            try {
                val lastCheck = settingsRepository.lastUpdateCheck.first()
                if (currentTime - lastCheck > 24 * 60 * 60 * 1000L) {
                    updateRepository.checkForUpdates()
                    settingsRepository.setLastUpdateCheck(System.currentTimeMillis())
                }
            } catch (_: Exception) { /* Non-fatal */ }

            _loadingProgress.value = 0.90f

            // ── Stage 4: Complete ─────────────────────────────────────────
            _loadingMessage.value = "READY"
            _loadingProgress.value = 1f
            settingsRepository.setLastLoadingTime(System.currentTimeMillis())

            _isInitialized.value = true
            delay(200) // Allow the dashboard to render behind the overlay
            _isVisible.value = false
        }
    }
}