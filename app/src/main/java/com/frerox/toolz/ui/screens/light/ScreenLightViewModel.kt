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
    val isLocked: Boolean = false
)

@HiltViewModel
class ScreenLightViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(ScreenLightState())
    val uiState: StateFlow<ScreenLightState> = _uiState.asStateFlow()

    fun setColor(color: Color) {
        _uiState.update { it.copy(color = color) }
    }

    fun setBrightness(brightness: Float) {
        _uiState.update { it.copy(brightness = brightness) }
    }
    
    fun toggleLock() {
        _uiState.update { it.copy(isLocked = !it.isLocked) }
    }
}
