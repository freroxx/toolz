package com.frerox.toolz.ui.screens.light

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
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
    val mode: FlashlightMode = FlashlightMode.STEADY,
    val brightness: Float = 1.0f,
    val isBrightnessSupported: Boolean = false,
    val maxBrightness: Int = 1
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
            val ids = cameraManager.cameraIdList
            if (ids.isNotEmpty()) {
                cameraId = ids[0]
                checkBrightnessSupport()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkBrightnessSupport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                cameraId?.let { id ->
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val maxLevel = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAX_LEVEL)
                    
                    if (maxLevel != null && maxLevel > 1) {
                        _uiState.update { it.copy(
                            isBrightnessSupported = true,
                            maxBrightness = maxLevel,
                            brightness = 1.0f
                        ) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    fun setBrightness(level: Float) {
        _uiState.update { it.copy(brightness = level) }
        if (_uiState.value.isOn && _uiState.value.mode == FlashlightMode.STEADY) {
            setTorch(true)
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
            cameraId?.let { id ->
                if (enabled) {
                    if (_uiState.value.isBrightnessSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val level = (_uiState.value.brightness * _uiState.value.maxBrightness).toInt().coerceIn(1, _uiState.value.maxBrightness)
                        cameraManager.turnOnTorchWithStrengthLevel(id, level)
                    } else {
                        cameraManager.setTorchMode(id, true)
                    }
                } else {
                    cameraManager.setTorchMode(id, false)
                }
            }
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
            repeat(3) { setTorch(true); delay(dot); setTorch(false); delay(gap) }
            delay(letterGap)
            repeat(3) { setTorch(true); delay(dash); setTorch(false); delay(gap) }
            delay(letterGap)
            repeat(3) { setTorch(true); delay(dot); setTorch(false); delay(gap) }
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
