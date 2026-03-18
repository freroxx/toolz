package com.frerox.toolz.ui.screens.light

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
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

private const val TAG = "FlashlightVM"

// ─────────────────────────────────────────────────────────────
//  Domain models
// ─────────────────────────────────────────────────────────────

enum class FlashlightMode {
    STEADY, STROBE, SOS
}

data class FlashlightState(
    val isOn: Boolean                  = false,
    val mode: FlashlightMode           = FlashlightMode.STEADY,
    /** 0.0 – 1.0 normalised brightness level. */
    val brightness: Float              = 1.0f,
    /** True only when the hardware exposes >1 distinct strength levels. */
    val isBrightnessSupported: Boolean = false,
    /** Raw camera2 max strength level (≥ 1). */
    val maxBrightness: Int             = 1,
    /** Strobe interval in milliseconds per half-cycle (on or off). */
    val strobeIntervalMs: Long         = 80L,
    /** True while the torch is physically lit (updated via TorchCallback). */
    val isPhysicallyOn: Boolean        = false,
)

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class FlashlightViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /** The camera ID that has a torch unit. */
    private var cameraId: String? = null

    private val _uiState = MutableStateFlow(FlashlightState())
    val uiState: StateFlow<FlashlightState> = _uiState.asStateFlow()

    /** Active SOS / strobe coroutine job. */
    private var modeJob: Job? = null

    // ── TorchCallback — keeps UI in sync with physical torch state ─────────
    /**
     * Registered so that if the system or another app (camera, video call)
     * turns off the torch externally, the UI immediately reflects "OFF"
     * instead of staying stuck on "BEAM ACTIVE".
     */
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) {
                _uiState.update { it.copy(isPhysicallyOn = enabled) }
                // If something external turned us off while we think we're on,
                // sync the logical state too (but don't cancel modeJob —
                // let the next iteration of the loop try to re-enable).
                if (!enabled && _uiState.value.isOn &&
                    _uiState.value.mode == FlashlightMode.STEADY) {
                    _uiState.update { it.copy(isOn = false) }
                }
            }
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) {
                _uiState.update { it.copy(isOn = false, isPhysicallyOn = false) }
                modeJob?.cancel()
            }
        }
    }

    // ── Init ───────────────────────────────────────────────────────────────

    init {
        findCameraId()
        cameraManager.registerTorchCallback(torchCallback, null)
    }

    private fun findCameraId() {
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                if (hasFlash) {
                    cameraId = id
                    checkBrightnessSupport(chars)
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enumerate cameras", e)
        }
    }

    /**
     * Detects hardware torch-strength support on API 33+ devices.
     *
     * Why field reflection instead of the direct symbol
     * [CameraCharacteristics.FLASH_INFO_STRENGTH_MAX_LEVEL]:
     *
     * The direct symbol requires compileSdk ≥ 33. When the project targets a
     * lower compileSdk the compiler reports "Unresolved reference" even though
     * the method is guarded by a Build.VERSION check. Using field reflection
     * on the **static field** (not a key-by-name string) retrieves the properly
     * typed [CameraCharacteristics.Key<Int>] object with the correct internal
     * Camera2 integer tag — i.e. it works correctly at runtime on API 33+
     * regardless of the compileSdk level.
     */
    private fun checkBrightnessSupport(characteristics: CameraCharacteristics) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        try {
            @Suppress("UNCHECKED_CAST")
            val key = CameraCharacteristics::class.java
                .getDeclaredField("FLASH_INFO_STRENGTH_MAX_LEVEL")
                .get(null) as? CameraCharacteristics.Key<Int> ?: return

            // Explicit Int annotation prevents the generic Comparable<T> issue
            val maxLevel: Int = characteristics.get(key) ?: return

            if (maxLevel >= 2) {
                _uiState.update {
                    it.copy(
                        isBrightnessSupported = true,
                        maxBrightness         = maxLevel,
                        brightness            = 1.0f,
                    )
                }
                Log.d(TAG, "Torch strength supported: max = $maxLevel")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Torch strength not available: ${e.message}")
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    fun toggleFlashlight() {
        val newOn = !_uiState.value.isOn
        _uiState.update { it.copy(isOn = newOn) }
        if (newOn) startMode() else stopMode()
    }

    fun setMode(mode: FlashlightMode) {
        val wasOn = _uiState.value.isOn
        _uiState.update { it.copy(mode = mode) }
        if (wasOn) {
            // Cancel old job without physically toggling off first
            // to avoid the visible flash-off glitch during mode switch.
            modeJob?.cancel()
            startMode()
        }
    }

    fun setBrightness(normalised: Float) {
        _uiState.update { it.copy(brightness = normalised.coerceIn(0.1f, 1.0f)) }
        // Apply immediately if STEADY and on
        if (_uiState.value.isOn && _uiState.value.mode == FlashlightMode.STEADY) {
            applyCurrentBrightness()
        }
    }

    /**
     * Sets the strobe half-cycle duration in milliseconds.
     * The effective strobe frequency = 1000 / (2 * intervalMs) Hz.
     * E.g. 80 ms → ~6.25 Hz.  Range: 40 ms (12.5 Hz) – 500 ms (1 Hz).
     */
    fun setStrobeInterval(ms: Long) {
        val clamped = ms.coerceIn(40L, 500L)
        _uiState.update { it.copy(strobeIntervalMs = clamped) }
        // If already strobing, restart with new timing
        if (_uiState.value.isOn && _uiState.value.mode == FlashlightMode.STROBE) {
            modeJob?.cancel()
            startMode()
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private fun startMode() {
        modeJob?.cancel()
        modeJob = viewModelScope.launch {
            when (_uiState.value.mode) {
                FlashlightMode.STEADY -> {
                    applyCurrentBrightness()
                }
                FlashlightMode.STROBE -> runStrobe()
                FlashlightMode.SOS    -> runSos()
            }
        }
    }

    private fun stopMode() {
        modeJob?.cancel()
        modeJob = null
        setTorchRaw(false)
    }

    /**
     * Applies the current brightness setting to the physical torch.
     *
     * On API 33+ devices that report ≥ 2 strength levels, calls
     * [CameraManager.turnOnTorchWithStrengthLevel] via method reflection for
     * the same compileSdk-independence reason as [checkBrightnessSupport].
     * Falls back to a simple on/off for older APIs or unsupported hardware.
     */
    private fun applyCurrentBrightness() {
        val id = cameraId ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            _uiState.value.isBrightnessSupported) {
            applyStrengthLevel(id)
        } else {
            setTorchRaw(true)
        }
    }

    private fun applyStrengthLevel(id: String) {
        try {
            val max   = _uiState.value.maxBrightness
            val level = (_uiState.value.brightness * max).toInt().coerceIn(1, max)
            // Method reflection — avoids compile-time API 33 symbol dependency.
            // CameraManager.turnOnTorchWithStrengthLevel(String, int) is a real
            // public method on API 33+ devices; the reflection lookup is reliable.
            cameraManager.javaClass
                .getMethod(
                    "turnOnTorchWithStrengthLevel",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                )
                .invoke(cameraManager, id, level)
            Log.d(TAG, "Torch strength set to $level / $max")
        } catch (e: Exception) {
            Log.w(TAG, "turnOnTorchWithStrengthLevel failed: ${e.message}")
            setTorchRaw(true)
        }
    }

    /** Low-level boolean torch toggle via [CameraManager.setTorchMode]. */
    private fun setTorchRaw(enabled: Boolean) {
        try {
            cameraId?.let { cameraManager.setTorchMode(it, enabled) }
        } catch (e: Exception) {
            Log.e(TAG, "setTorchMode($enabled) failed: ${e.message}")
        }
    }

    // International Morse SOS: · · ·  — — —  · · ·
    private suspend fun runSos() {
        val dotMs  = 200L
        val dashMs = 600L
        val gapMs  = 200L
        val wordMs = 2_000L

        while (true) {
            // S: · · ·
            repeat(3) { setTorchRaw(true); delay(dotMs);  setTorchRaw(false); delay(gapMs) }
            delay(gapMs * 2)
            // O: — — —
            repeat(3) { setTorchRaw(true); delay(dashMs); setTorchRaw(false); delay(gapMs) }
            delay(gapMs * 2)
            // S: · · ·
            repeat(3) { setTorchRaw(true); delay(dotMs);  setTorchRaw(false); delay(gapMs) }
            delay(wordMs)
        }
    }

    private suspend fun runStrobe() {
        while (true) {
            val interval = _uiState.value.strobeIntervalMs
            setTorchRaw(true)
            delay(interval)
            setTorchRaw(false)
            delay(interval)
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        try {
            cameraManager.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {}
        stopMode()
    }
}