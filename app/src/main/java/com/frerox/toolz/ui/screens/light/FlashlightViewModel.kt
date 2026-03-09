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
                    
                    // Use reflection to avoid "Unresolved reference" errors at compile time
                    // if the SDK environment is not properly set up for API 33+
                    val maxLevel = try {
                        val keyField = CameraCharacteristics::class.java.getDeclaredField("FLASH_INFO_STRENGTH_MAX_LEVEL")
                        @Suppress("UNCHECKED_CAST")
                        val key = keyField.get(null) as CameraCharacteristics.Key<Int>
                        characteristics.get(key)
                    } catch (e: Exception) {
                        // Fallback: manually construct the key using its documented string identifier
                        try {
                            val manualKey = CameraCharacteristics.Key("android.flash.info.strengthMaxLevel", Int::class.javaObjectType)
                            characteristics.get(manualKey)
                        } catch (e2: Exception) {
                            null
                        }
                    }
                    
                    if (maxLevel != null && maxLevel > 1) {
                        _uiState.update { it.copy(
                            isBrightnessSupported = true,
                            maxBrightness = maxLevel,
                            brightness = 1.0f
                        ) }
                    }
                }
            } catch (e: Exception) {
                // Not supported
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
            updateTorchStrength()
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

    private fun updateTorchStrength() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && _uiState.value.isBrightnessSupported) {
            try {
                cameraId?.let { id ->
                    val max = _uiState.value.maxBrightness
                    val level = (_uiState.value.brightness * max).toInt().coerceIn(1, max)
                    
                    // Use reflection to call turnOnTorchWithStrengthLevel to avoid compilation errors
                    try {
                        val method = cameraManager.javaClass.getMethod("turnOnTorchWithStrengthLevel", String::class.java, Int::class.javaPrimitiveType)
                        method.invoke(cameraManager, id, level)
                    } catch (e: Exception) {
                        setTorchManual(true)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            setTorchManual(true)
        }
    }

    private fun setTorch(enabled: Boolean) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && _uiState.value.isBrightnessSupported && _uiState.value.mode == FlashlightMode.STEADY) {
                updateTorchStrength()
            } else {
                setTorchManual(true)
            }
        } else {
            setTorchManual(false)
        }
    }

    private fun setTorchManual(enabled: Boolean) {
        try {
            cameraId?.let { id ->
                cameraManager.setTorchMode(id, enabled)
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
            repeat(3) { setTorchManual(true); delay(dot); setTorchManual(false); delay(gap) }
            delay(letterGap)
            repeat(3) { setTorchManual(true); delay(dash); setTorchManual(false); delay(gap) }
            delay(letterGap)
            repeat(3) { setTorchManual(true); delay(dot); setTorchManual(false); delay(gap) }
            delay(2000L)
        }
    }

    private suspend fun runStrobe() {
        while (true) {
            setTorchManual(true)
            delay(100)
            setTorchManual(false)
            delay(100)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMode()
    }
}
