/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.ui.screens.media.controllers

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import com.frerox.toolz.ui.screens.media.MusicUiState
import com.frerox.toolz.util.VibrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns setSleepTimer, toggleSleepTimerDialog, fadeOutAndStop.
 */
class SleepTimerManager(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MusicUiState>,
    private val player: ExoPlayer,
    private val controllerProvider: () -> MediaController?,
    private val vibrationManager: VibrationManager
) {
    private val _showSleepTimer = MutableStateFlow(false)
    val showSleepTimer: StateFlow<Boolean> = _showSleepTimer.asStateFlow()

    private var sleepTimerJob: Job? = null

    private fun hapticClick() { if (!uiState.value.isKaraokeActive) vibrationManager.vibrateClick() }

    private fun playerOrController(): Player = controllerProvider() ?: player

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        uiState.update {
            it.copy(
                sleepTimerMinutes = minutes,
                sleepTimerActive = minutes != null,
                sleepTimerRemaining = minutes?.let { m -> m * 60_000L }
            )
        }
        if (minutes != null) {
            val end = System.currentTimeMillis() + minutes * 60_000L
            sleepTimerJob = scope.launch {
                while (System.currentTimeMillis() < end) {
                    uiState.update { it.copy(sleepTimerRemaining = (end - System.currentTimeMillis()).coerceAtLeast(0)) }
                    delay(1_000)
                }
                fadeOutAndStop()
            }
        }
        hapticClick()
    }

    fun toggleSleepTimerDialog() { _showSleepTimer.update { !it }; hapticClick() }

    private suspend fun fadeOutAndStop() {
        var vol = 1f
        while (vol > 0) { vol -= 0.05f; player.volume = vol.coerceAtLeast(0f); delay(100) }
        val p: Player = playerOrController()
        p.pause(); player.volume = 1f
        uiState.update { it.copy(sleepTimerMinutes = null, sleepTimerActive = false, sleepTimerRemaining = null) }
        vibrationManager.vibrateLongClick()
    }

    fun onCleared() {
        sleepTimerJob?.cancel()
    }
}
