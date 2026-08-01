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

package com.frerox.toolz.ui.screens.light

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ScreenLightState(
    val color: Color = Color.White,
    val brightness: Float = 1.0f,
    val isLocked: Boolean = false,
    val isStrobeEnabled: Boolean = false,
    val strobeInterval: Long = 500L
)

@HiltViewModel
class ScreenLightViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(ScreenLightState())
    val uiState: StateFlow<ScreenLightState> = _uiState.asStateFlow()

    fun setColor(color: Color) {
        _uiState.update { it.copy(color = color, isStrobeEnabled = false) }
    }

    fun setBrightness(brightness: Float) {
        _uiState.update { it.copy(brightness = brightness) }
    }
    
    fun toggleLock() {
        _uiState.update { it.copy(isLocked = !it.isLocked) }
    }

    fun toggleStrobe() {
        _uiState.update { it.copy(isStrobeEnabled = !it.isStrobeEnabled) }
    }

    fun setStrobeInterval(interval: Long) {
        _uiState.update { it.copy(strobeInterval = interval) }
    }
}
