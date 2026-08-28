/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.ui.screens.media.controllers

import android.media.audiofx.Equalizer
import androidx.media3.exoplayer.ExoPlayer
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.ui.screens.media.MusicUiState
import com.frerox.toolz.util.VibrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns Equalizer(0, sessionId), initEqualizer, applyEqualizerPreset,
 * applyCustomEqualizer, setCustomEqualizerGain, setEqualizerPreset.
 */
class EqualizerController(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MusicUiState>,
    private val player: ExoPlayer,
    private val settingsRepository: SettingsRepository,
    private val vibrationManager: VibrationManager
) {
    private var equalizer: Equalizer? = null

    private val _equalizerPresets = MutableStateFlow<List<String>>(emptyList())
    val equalizerPresets: StateFlow<List<String>> = _equalizerPresets.asStateFlow()

    private fun hapticClick() { if (!uiState.value.isKaraokeActive) vibrationManager.vibrateClick() }

    fun initEqualizer() {
        if (equalizer != null || player.audioSessionId == 0) return
        runCatching {
            equalizer = Equalizer(0, player.audioSessionId).apply { enabled = true }
            val presets = (0 until (equalizer?.numberOfPresets?.toInt() ?: 0))
                .mapNotNull { equalizer?.getPresetName(it.toShort()) }
                .toMutableList()
            listOf("Bass Boost", "Vocal Booster", "Treble Booster", "Electronic")
                .forEach { if (!presets.contains(it)) presets.add(it) }
            _equalizerPresets.value = presets.distinct()
            scope.launch { applyEqualizerPreset(settingsRepository.musicEqualizerPreset.first()) }
        }
    }

    fun applyEqualizerPreset(preset: String) {
        if (preset == "Custom") { applyCustomEqualizer(uiState.value.customEqualizerGains); return }
        val eq = equalizer ?: return
        runCatching {
            for (i in 0 until eq.numberOfPresets.toInt()) {
                if (eq.getPresetName(i.toShort()).equals(preset, ignoreCase = true)) {
                    eq.usePreset(i.toShort()); return
                }
            }
            val numBands = eq.numberOfBands.toInt()
            when (preset) {
                "Bass Boost" -> {
                    for (i in 0 until numBands) {
                        val f = eq.getCenterFreq(i.toShort())
                        eq.setBandLevel(i.toShort(), (if (f < 500_000) 1000 else 0).toShort())
                    }
                }
                "Vocal Booster" -> {
                    for (i in 0 until numBands) {
                        val f = eq.getCenterFreq(i.toShort())
                        eq.setBandLevel(i.toShort(), (if (f in 500_000..3_000_000) 800 else -200).toShort())
                    }
                }
                "Treble Booster" -> {
                    for (i in 0 until numBands) {
                        val f = eq.getCenterFreq(i.toShort())
                        eq.setBandLevel(i.toShort(), (if (f > 3_000_000) 1000 else 0).toShort())
                    }
                }
                "Electronic" -> {
                    for (i in 0 until numBands) {
                        val f = eq.getCenterFreq(i.toShort())
                        val lv = when {
                            f < 250_000 -> 800
                            f in 250_000..1_000_000 -> 0
                            f > 4_000_000 -> 600
                            else -> 200
                        }
                        eq.setBandLevel(i.toShort(), lv.toShort())
                    }
                }
            }
        }
    }

    fun applyCustomEqualizer(gains: List<Float>) {
        val eq = equalizer ?: return
        runCatching {
            gains.forEachIndexed { i, gain ->
                if (i < eq.numberOfBands) {
                    eq.setBandLevel(i.toShort(), (gain * 1500).toInt().coerceIn(-1500, 1500).toShort())
                }
            }
        }
    }

    fun setCustomEqualizerGain(band: Int, gain: Float) {
        val gains = uiState.value.customEqualizerGains.toMutableList()
        if (band !in gains.indices) return
        gains[band] = gain
        uiState.update { it.copy(customEqualizerGains = gains) }
        scope.launch { settingsRepository.setMusicCustomEqualizer(gains.joinToString(",")) }
        if (uiState.value.equalizerPreset == "Custom") applyCustomEqualizer(gains)
    }

    fun setEqualizerPreset(preset: String) {
        scope.launch {
            settingsRepository.setMusicEqualizerPreset(preset)
            uiState.update { it.copy(equalizerPreset = preset) }
            applyEqualizerPreset(preset)
            hapticClick()
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
    }

    fun onCleared() {
        release()
    }
}
