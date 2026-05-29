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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.frerox.toolz.service.FlashlightService
import android.content.Intent
import kotlinx.coroutines.flow.first
import com.frerox.toolz.data.repository.FlashlightRepository
import javax.inject.Inject

private const val TAG = "FlashlightVM"

// ─────────────────────────────────────────────────────────────
//  Domain models
// ─────────────────────────────────────────────────────────────

enum class FlashlightMode {
    STEADY, STROBE, SOS, DISCO
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
    /** Min/Max interval for Disco mode in milliseconds. */
    val discoIntervalRange: Pair<Long, Long> = 40L to 300L,
    /** True while the torch is physically lit (updated via TorchCallback). */
    val isPhysicallyOn: Boolean        = false,
    /** Auto-off timer in minutes. 0 means disabled. */
    val timerMinutes: Int              = 0,
    /** Remaining time in seconds when timer is active. */
    val remainingSeconds: Int?         = null,
)

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class FlashlightViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val repository: FlashlightRepository
) : ViewModel() {

    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /** The camera ID that has a torch unit. */
    private var cameraId: String? = null

    private val _uiState = MutableStateFlow(FlashlightState())
    val uiState: StateFlow<FlashlightState> = _uiState.asStateFlow()

    /** Active SOS / strobe coroutine job. */
    private var modeJob: Job? = null
    /** Timer job. */
    private var timerJob: Job? = null

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
        loadSettings()
        
        viewModelScope.launch {
            repository.isOn.collect { isOn ->
                _uiState.update { it.copy(isOn = isOn) }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            dataStore.data.first().let { prefs ->
                _uiState.update { it.copy(
                    mode = FlashlightMode.valueOf(prefs[stringPreferencesKey("flashlight_mode")] ?: FlashlightMode.STEADY.name),
                    brightness = prefs[floatPreferencesKey("flashlight_brightness")] ?: 1.0f,
                    strobeIntervalMs = prefs[longPreferencesKey("flashlight_strobe_interval")] ?: 80L,
                    discoIntervalRange = (prefs[longPreferencesKey("flashlight_disco_min")] ?: 40L) to (prefs[longPreferencesKey("flashlight_disco_max")] ?: 300L),
                    timerMinutes = prefs[intPreferencesKey("flashlight_timer")] ?: 0
                ) }
            }
        }
    }

    private fun saveSetting(key: String, value: Any) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                when (value) {
                    is String -> prefs[stringPreferencesKey(key)] = value
                    is Float -> prefs[floatPreferencesKey(key)] = value
                    is Long -> prefs[longPreferencesKey(key)] = value
                    is Int -> prefs[intPreferencesKey(key)] = value
                }
            }
        }
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
                .getDeclaredField("FLASH_INFO_STRENGTH_MAXIMUM_LEVEL")
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

        val intent = Intent(context, FlashlightService::class.java).apply {
            action = if (newOn) FlashlightService.ACTION_TOGGLE else FlashlightService.ACTION_STOP
        }
        if (newOn) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun setMode(mode: FlashlightMode) {
        _uiState.update { it.copy(mode = mode) }
        saveSetting("flashlight_mode", mode.name)
        if (_uiState.value.isOn) {
            startMode()
        }
    }

    fun setBrightness(normalised: Float) {
        val valClamped = normalised.coerceIn(0.1f, 1.0f)
        _uiState.update { it.copy(brightness = valClamped) }
        saveSetting("flashlight_brightness", valClamped)
        if (_uiState.value.isOn) {
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
        saveSetting("flashlight_strobe_interval", clamped)
        // If already strobing or in disco, restart with new timing (disco uses it as base)
        if (_uiState.value.isOn && (_uiState.value.mode == FlashlightMode.STROBE || _uiState.value.mode == FlashlightMode.DISCO)) {
            modeJob?.cancel()
            startMode()
        }
    }

    fun setTimer(minutes: Int) {
        _uiState.update { it.copy(timerMinutes = minutes) }
        saveSetting("flashlight_timer", minutes)
        if (minutes > 0) {
            startTimer(minutes)
        } else {
            timerJob?.cancel()
            _uiState.update { it.copy(remainingSeconds = null) }
        }
    }

    fun setDiscoRange(min: Long, max: Long) {
        _uiState.update { it.copy(discoIntervalRange = min to max) }
        saveSetting("flashlight_disco_min", min)
        saveSetting("flashlight_disco_max", max)
        // If in disco, no need to restart job as it picks up values dynamically
    }

    private fun startTimer(minutes: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var seconds = minutes * 60
            while (seconds > 0) {
                _uiState.update { it.copy(remainingSeconds = seconds) }
                delay(1000)
                seconds--
            }
            _uiState.update { it.copy(remainingSeconds = 0) }
            if (_uiState.value.isOn) {
                toggleFlashlight()
            }
            _uiState.update { it.copy(remainingSeconds = null, timerMinutes = 0) }
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
                FlashlightMode.DISCO  -> runDisco()
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
            repeat(3) { applyCurrentBrightness(); delay(dotMs);  setTorchRaw(false); delay(gapMs) }
            delay(gapMs * 2)
            // O: — — —
            repeat(3) { applyCurrentBrightness(); delay(dashMs); setTorchRaw(false); delay(gapMs) }
            delay(gapMs * 2)
            // S: · · ·
            repeat(3) { applyCurrentBrightness(); delay(dotMs);  setTorchRaw(false); delay(gapMs) }
            delay(wordMs)
        }
    }

    private suspend fun runStrobe() {
        while (true) {
            val interval = _uiState.value.strobeIntervalMs
            applyCurrentBrightness()
            delay(interval)
            setTorchRaw(false)
            delay(interval)
        }
    }

    private suspend fun runDisco() {
        while (true) {
            val range = _uiState.value.discoIntervalRange
            val interval = (range.first..range.second).random()
            
            applyCurrentBrightness()
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
        timerJob?.cancel()
    }
}