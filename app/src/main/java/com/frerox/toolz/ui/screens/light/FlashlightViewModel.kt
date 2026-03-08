package com.frerox.toolz.ui.screens.light

import android.content.Context
import android.hardware.camera2.CameraManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FlashlightMode {
    STEADY, SOS, STROBE
}

data class FlashlightState(
    val isOn: Boolean = false,
    val mode: FlashlightMode = FlashlightMode.STEADY
)

@HiltViewModel
class FlashlightViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraId: String? = null

    private val _uiState = MutableStateFlow(FlashlightState())
    val uiState: StateFlow<FlashlightState> = _uiState.asStateFlow()

    private var modeJob: Job? = null

    init {
        try {
            cameraId = cameraManager.cameraIdList[0]
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleFlashlight() {
        val newState = !_uiState.value.isOn
        _uiState.update { it.copy(isOn = newState) }
        
        if (newState) {
            startMode()
        } else {
            stopMode()
        }
    }

    fun setMode(mode: FlashlightMode) {
        _uiState.update { it.copy(mode = mode) }
        if (_uiState.value.isOn) {
            stopMode()
            startMode()
        }
    }

    private fun startMode() {
        modeJob?.cancel()
        modeJob = viewModelScope.launch {
            when (_uiState.value.mode) {
                FlashlightMode.STEADY -> setTorch(true)
                FlashlightMode.SOS -> runSos()
                FlashlightMode.STROBE -> runStrobe()
            }
        }
    }

    private fun stopMode() {
        modeJob?.cancel()
        setTorch(false)
    }

    private fun setTorch(enabled: Boolean) {
        try {
            cameraId?.let { cameraManager.setTorchMode(it, enabled) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun runSos() {
        val dot = 200L
        val dash = 600L
        val gap = 200L
        val letterGap = 600L

        while (true) {
            // S: ...
            repeat(3) { 
                setTorch(true); delay(dot); setTorch(false); delay(gap) 
            }
            delay(letterGap)
            // O: ---
            repeat(3) { 
                setTorch(true); delay(dash); setTorch(false); delay(gap) 
            }
            delay(letterGap)
            // S: ...
            repeat(3) { 
                setTorch(true); delay(dot); setTorch(false); delay(gap) 
            }
            delay(2000L)
        }
    }

    private suspend fun runStrobe() {
        while (true) {
            setTorch(true)
            delay(100)
            setTorch(false)
            delay(100)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMode()
    }
}
