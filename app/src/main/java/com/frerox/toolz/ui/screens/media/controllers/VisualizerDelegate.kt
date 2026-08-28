/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.ui.screens.media.controllers

import com.frerox.toolz.BuildConfig
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.ui.screens.media.MusicUiState
import com.frerox.toolz.util.MusicVisualizerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns startVisualizer, restartVisualizer, detachVisualizer, stopVisualizer,
 * visualizerAttachJob, uses MusicVisualizerManager, respects showVisualizer
 * + isKaraokeActive + performanceMode.
 */
class VisualizerDelegate(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MusicUiState>,
    private val visualizerManager: MusicVisualizerManager,
    private val settingsRepository: SettingsRepository
) {
    private val _visualizerData = MutableStateFlow(FloatArray(0))
    val visualizerData: StateFlow<FloatArray> = _visualizerData.asStateFlow()

    var visualizerAttachJob: Job? = null
        private set

    fun startVisualizer() {
        if (!uiState.value.showVisualizer || uiState.value.isKaraokeActive) {
            if (BuildConfig.DEBUG) android.util.Log.d("MusicVisualizer", "startVisualizer: skipped, showVisualizer=${uiState.value.showVisualizer} isKaraokeActive=${uiState.value.isKaraokeActive}")
            return
        }

        visualizerManager.setAutoSensitivity(uiState.value.visualizerAutoSensitivity)
        visualizerAttachJob?.cancel()
        visualizerAttachJob = scope.launch(Dispatchers.Default) {
            val emptySpectrum = FloatArray(64)
            var lastSpectrum = FloatArray(64)

            while (currentCoroutineContext().isActive && uiState.value.showVisualizer) {
                if (uiState.value.isPlaying) {
                    val spectrum = visualizerManager.getSpectrum()

                    val smoothed = FloatArray(64)
                    for (i in 0 until 64) {
                        val target = spectrum?.get(i) ?: 0f
                        val prev = lastSpectrum[i]
                        smoothed[i] = if (target > prev) target else prev * 0.78f
                    }
                    lastSpectrum = smoothed
                    _visualizerData.value = smoothed
                } else {
                    val current = _visualizerData.value
                    if (current.isNotEmpty() && current.any { it > 0.01f }) {
                        val decayed = current.map { it * 0.8f }.toFloatArray()
                        _visualizerData.value = decayed
                        lastSpectrum = decayed
                    } else if (current.isNotEmpty()) {
                        _visualizerData.value = emptySpectrum
                        lastSpectrum = emptySpectrum
                    }
                }
                val vInterval = if (uiState.value.performanceMode) 100L else 16L
                delay(vInterval)
            }
        }
    }

    fun restartVisualizer() {
        detachVisualizer()
        startVisualizer()
    }

    fun detachVisualizer() {
        visualizerAttachJob?.cancel()
        visualizerAttachJob = null
    }

    fun stopVisualizer() {
        detachVisualizer()
        _visualizerData.value = FloatArray(0)
    }

    fun setVisualizerSensitivity(sensitivity: Float) {
        scope.launch {
            settingsRepository.setMusicVisualizerSensitivity(sensitivity)
            uiState.update { it.copy(visualizerSensitivity = sensitivity) }
        }
    }

    fun setVisualizerAutoSensitivity(enabled: Boolean) {
        visualizerManager.setAutoSensitivity(enabled)
        scope.launch {
            settingsRepository.setMusicVisualizerAutoSensitivity(enabled)
            uiState.update { it.copy(visualizerAutoSensitivity = enabled) }
        }
    }

    fun clearVisualizerData() {
        _visualizerData.value = FloatArray(0)
    }

    fun onCleared() {
        detachVisualizer()
        _visualizerData.value = FloatArray(0)
    }
}
