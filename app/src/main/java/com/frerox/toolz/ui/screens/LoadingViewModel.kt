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
    private val updateRepository: UpdateRepository,
) : ViewModel() {

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    private val _loadingMessage = MutableStateFlow("PREPARING WORKSPACE")
    val loadingMessage = _loadingMessage.asStateFlow()

    // Deterministic progress [0f → 1f] — wired to ToolzWavyLinearProgressIndicator
    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress = _loadingProgress.asStateFlow()

    init {
        performInitialization()
    }

    private fun performInitialization() {
        viewModelScope.launch {
            val lastLoading = settingsRepository.lastLoadingTime.first()
            val currentTime = System.currentTimeMillis()
            val shouldSkipLoading = currentTime - lastLoading < 5 * 60 * 1000L // 5-minute threshold

            if (shouldSkipLoading) {
                // Fast-path: animate progress to 100 quickly and exit
                animateProgressTo(1f, durationMs = 350)
                _isInitialized.value = true
                return@launch
            }

            val startTime = System.currentTimeMillis()

            // ── Stage 1: Environment check ────────────────────────────────────
            _loadingMessage.value = "PREPARING WORKSPACE"
            animateProgressTo(0.15f)

            // ── Stage 2: Sync AI keys ─────────────────────────────────────────
            _loadingMessage.value = "SYNCING INTELLIGENCE"
            try {
                aiSettingsManager.syncRemoteKeys()
            } catch (_: Exception) {
                // Non-fatal — continue loading
            }
            animateProgressTo(0.50f)

            // ── Stage 3: Update check ─────────────────────────────────────────
            _loadingMessage.value = "CHECKING FOR UPDATES"
            try {
                val lastCheck = settingsRepository.lastUpdateCheck.first()
                if (System.currentTimeMillis() - lastCheck > 24 * 60 * 60 * 1000L) {
                    updateRepository.checkForUpdates()
                    settingsRepository.setLastUpdateCheck(System.currentTimeMillis())
                }
            } catch (_: Exception) {
                // Non-fatal — continue loading
            }
            animateProgressTo(0.80f)

            // ── Stage 4: Minimum visual duration for polish ───────────────────
            _loadingMessage.value = "ALMOST THERE"
            val elapsedTime = System.currentTimeMillis() - startTime
            val minLoadingTimeMs = 1000L // Reduced from 1600L for instant feel
            if (elapsedTime < minLoadingTimeMs) {
                delay(minLoadingTimeMs - elapsedTime)
            }

            // ── Stage 5: Complete ─────────────────────────────────────────────
            animateProgressTo(1f, durationMs = 200) // Faster final progress
            settingsRepository.setLastLoadingTime(System.currentTimeMillis())
            _loadingMessage.value = "READY"

            // Brief pause at 100% so the user sees the completed state
            delay(100)
            _isInitialized.value = true
        }
    }

    /**
     * Smoothly interpolates [_loadingProgress] toward [target] over [durationMs].
     * Uses small ticks to produce a fluid animation without flooding StateFlow.
     */
    private suspend fun animateProgressTo(
        target: Float,
        durationMs: Long = 400L,
        stepMs: Long = 16L,
    ) {
        val start = _loadingProgress.value
        if (start >= target) return
        val steps = (durationMs / stepMs).coerceAtLeast(1)
        val delta = target - start
        for (i in 1..steps) {
            _loadingProgress.value = start + delta * (i.toFloat() / steps)
            delay(stepMs)
        }
        _loadingProgress.value = target
    }
}