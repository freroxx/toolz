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

    // ── Start defaults ("always set …") ────────────────────────────────────
    /** When true, [defaultMode] is selected every time the beam starts. */
    val defaultModeEnabled: Boolean           = false,
    val defaultMode: FlashlightMode           = FlashlightMode.STEADY,
    /** When true, [alwaysIntensity] replaces the last-used intensity on start. */
    val alwaysIntensityEnabled: Boolean       = false,
    val alwaysIntensity: Float                = 1.0f,
    /** When true, [alwaysStrobeMs] replaces the last-used strobe speed on start. */
    val alwaysStrobeEnabled: Boolean          = false,
    val alwaysStrobeMs: Long                  = 80L,
    /** When true, [alwaysPaceRange] replaces the last-used disco range on start. */
    val alwaysPaceEnabled: Boolean            = false,
    val alwaysPaceRange: Pair<Long, Long>     = 40L to 300L,
)

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class FlashlightViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val repository: FlashlightRepository,
    private val gate: com.frerox.toolz.data.developer.SpoofGate,
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
                // DEV-SPOOF: real service state must not override the fake toggle.
                if (!gate.sandboxed(com.frerox.toolz.data.developer.DevToolId.SYSTEM)) {
                    if (!isOn && _uiState.value.isOn) {
                        // The beam was stopped by the service timer or by an
                        // external camera grab: retire the UI countdown too.
                        timerJob?.cancel()
                        timerJob = null
                        _uiState.update { it.copy(isOn = false, remainingSeconds = null) }
                    } else {
                        _uiState.update { it.copy(isOn = isOn) }
                    }
                }
            }
        }
        viewModelScope.launch {
            // DEV-SPOOF: entering sandbox kills any real beam, then fakes SOS.
            gate.active(com.frerox.toolz.data.developer.DevToolId.SYSTEM).collect { spoofed ->
                if (spoofed) {
                    try {
                        context.startService(Intent(context, FlashlightService::class.java).apply {
                            action = FlashlightService.ACTION_STOP
                        })
                    } catch (_: Exception) { }
                    _uiState.update { it.copy(isOn = false, mode = FlashlightMode.SOS) }
                    android.util.Log.w("DevSpoof", "Flashlight sandbox active — real torch stopped")
                }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            dataStore.data.first().let { prefs ->
                val mode = FlashlightMode.valueOf(prefs[stringPreferencesKey("flashlight_mode")] ?: FlashlightMode.STEADY.name)
                val brightness = prefs[floatPreferencesKey("flashlight_brightness")] ?: 1.0f
                val strobeInterval = prefs[longPreferencesKey("flashlight_strobe_interval")] ?: 80L
                val discoMin = prefs[longPreferencesKey("flashlight_disco_min")] ?: 40L
                val discoMax = prefs[longPreferencesKey("flashlight_disco_max")] ?: 300L
                val timer = prefs[intPreferencesKey("flashlight_timer")] ?: 0
                val defaultModeEnabled =
                    prefs[booleanPreferencesKey("flashlight_default_mode_enabled")] ?: false
                val defaultMode = runCatching {
                    FlashlightMode.valueOf(
                        prefs[stringPreferencesKey("flashlight_default_mode")]
                            ?: FlashlightMode.STEADY.name
                    )
                }.getOrDefault(FlashlightMode.STEADY)
                val alwaysIntensityEnabled =
                    prefs[booleanPreferencesKey("flashlight_always_intensity_enabled")] ?: false
                val alwaysIntensity =
                    prefs[floatPreferencesKey("flashlight_always_intensity")] ?: brightness
                val alwaysStrobeEnabled =
                    prefs[booleanPreferencesKey("flashlight_always_strobe_enabled")] ?: false
                val alwaysStrobe =
                    prefs[longPreferencesKey("flashlight_always_strobe")] ?: strobeInterval
                val alwaysPaceEnabled =
                    prefs[booleanPreferencesKey("flashlight_always_pace_enabled")] ?: false
                val alwaysPaceMin =
                    prefs[longPreferencesKey("flashlight_always_pace_min")] ?: discoMin
                val alwaysPaceMax =
                    prefs[longPreferencesKey("flashlight_always_pace_max")] ?: discoMax

                _uiState.update { it.copy(
                    mode = mode,
                    brightness = brightness,
                    strobeIntervalMs = strobeInterval,
                    discoIntervalRange = discoMin to discoMax,
                    timerMinutes = timer,
                    defaultModeEnabled = defaultModeEnabled,
                    defaultMode = defaultMode,
                    alwaysIntensityEnabled = alwaysIntensityEnabled,
                    alwaysIntensity = alwaysIntensity.coerceIn(0.1f, 1.0f),
                    alwaysStrobeEnabled = alwaysStrobeEnabled,
                    alwaysStrobeMs = alwaysStrobe.coerceIn(40L, 500L),
                    alwaysPaceEnabled = alwaysPaceEnabled,
                    alwaysPaceRange = normalisePace(alwaysPaceMin, alwaysPaceMax),
                ) }
                
                repository.setMode(mode)
                repository.setBrightness(brightness)
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
     * [CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL]:
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
            val maxLevel = characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: return

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
        // DEV-SPOOF: fake SOS toggle — no service, no torch hardware.
        if (gate.sandboxed(com.frerox.toolz.data.developer.DevToolId.SYSTEM)) {
            val newOn = !_uiState.value.isOn
            _uiState.update { it.copy(isOn = newOn, mode = FlashlightMode.SOS) }
            android.util.Log.w("DevSpoof", "Flashlight sandbox active — fake SOS ${if (newOn) "ON" else "OFF"}, torch untouched")
            return
        }
        val newOn = !_uiState.value.isOn
        if (newOn) applyStartDefaults()

        val resolved = _uiState.value
        val intent = Intent(context, FlashlightService::class.java).apply {
            action = if (newOn) FlashlightService.ACTION_TOGGLE else FlashlightService.ACTION_STOP
            if (newOn) {
                putExtra("mode", resolved.mode.name)
                putExtra("brightness", resolved.brightness)
                putExtra("strobe", resolved.strobeIntervalMs)
                putExtra("disco_min", resolved.discoIntervalRange.first)
                putExtra("disco_max", resolved.discoIntervalRange.second)
                putExtra("timer", resolved.timerMinutes)
            }
        }
        context.startService(intent)

        // The countdown belongs to a running beam. Restart it on every start so
        // a timer configured while idle takes effect immediately, and cancel it
        // when the beam is turned off by hand.
        if (newOn) {
            if (resolved.timerMinutes > 0) startTimer(resolved.timerMinutes)
        } else {
            timerJob?.cancel()
            timerJob = null
            _uiState.update { it.copy(remainingSeconds = null) }
        }
    }

    /**
     * Resolves the "always set …" defaults once, right before the service is
     * started. Live in-app controls stay authoritative until the next start.
     */
    private fun applyStartDefaults() {
        val current = _uiState.value
        var resolved = current
        if (current.defaultModeEnabled) resolved = resolved.copy(mode = current.defaultMode)
        if (current.alwaysIntensityEnabled) {
            resolved = resolved.copy(brightness = current.alwaysIntensity.coerceIn(0.1f, 1.0f))
        }
        if (current.alwaysStrobeEnabled) {
            resolved = resolved.copy(strobeIntervalMs = current.alwaysStrobeMs.coerceIn(40L, 500L))
        }
        if (current.alwaysPaceEnabled) {
            val pace = normalisePace(current.alwaysPaceRange.first, current.alwaysPaceRange.second)
            resolved = resolved.copy(discoIntervalRange = pace)
        }
        if (resolved == current) return

        _uiState.value = resolved
        saveSetting("flashlight_mode", resolved.mode.name)
        saveSetting("flashlight_brightness", resolved.brightness)
        saveSetting("flashlight_strobe_interval", resolved.strobeIntervalMs)
        saveSetting("flashlight_disco_min", resolved.discoIntervalRange.first)
        saveSetting("flashlight_disco_max", resolved.discoIntervalRange.second)
        repository.setMode(resolved.mode)
        repository.setBrightness(resolved.brightness)
    }

    fun setMode(mode: FlashlightMode) {
        _uiState.update { it.copy(mode = mode) }
        saveSetting("flashlight_mode", mode.name)
        repository.setMode(mode)

        // DEV-SPOOF: never sync a live service in demo mode (the arm already
        // stopped it — a stray instance must not re-light the torch).
        if (!gate.sandboxed(com.frerox.toolz.data.developer.DevToolId.SYSTEM)) {
            FlashlightService.getInstance()?.setMode(mode)
        }

        if (_uiState.value.isOn) {
            startMode()
        }
    }

    fun setBrightness(normalised: Float) {
        val valClamped = normalised.coerceIn(0.1f, 1.0f)
        _uiState.update { it.copy(brightness = valClamped) }
        saveSetting("flashlight_brightness", valClamped)
        repository.setBrightness(valClamped)

        // DEV-SPOOF: no service sync / hardware apply while demo is armed.
        if (!gate.sandboxed(com.frerox.toolz.data.developer.DevToolId.SYSTEM)) {
            FlashlightService.getInstance()?.setBrightness(valClamped)
            if (_uiState.value.isOn) {
                applyCurrentBrightness()
            }
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
        
        // Sync with service if it's running
        FlashlightService.getInstance()?.setStrobeMs(clamped)

        // If already strobing or in disco, restart with new timing (disco uses it as base)
        if (_uiState.value.isOn && (_uiState.value.mode == FlashlightMode.STROBE || _uiState.value.mode == FlashlightMode.DISCO)) {
            modeJob?.cancel()
            startMode()
        }
    }

    fun setTimer(minutes: Int) {
        _uiState.update { it.copy(timerMinutes = minutes) }
        saveSetting("flashlight_timer", minutes)
        
        // Sync with service if it's running
        FlashlightService.getInstance()?.setTimer(minutes)

        if (minutes > 0) {
            // A timer only counts down while the beam is running. While idle it
            // is stored and starts on the next toggle.
            if (_uiState.value.isOn) {
                startTimer(minutes)
            } else {
                timerJob?.cancel()
                _uiState.update { it.copy(remainingSeconds = null) }
            }
        } else {
            timerJob?.cancel()
            _uiState.update { it.copy(remainingSeconds = null) }
        }
    }

    fun setDiscoRange(min: Long, max: Long) {
        _uiState.update { it.copy(discoIntervalRange = min to max) }
        saveSetting("flashlight_disco_min", min)
        saveSetting("flashlight_disco_max", max)
        
        // Sync with service if it's running
        FlashlightService.getInstance()?.setDiscoRange(min, max)

        // If in disco, no need to restart job as it picks up values dynamically
    }

    // ── Start defaults ("always set …") ────────────────────────────────────

    fun setDefaultModeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(defaultModeEnabled = enabled) }
        saveSetting("flashlight_default_mode_enabled", enabled)
    }

    fun setDefaultMode(mode: FlashlightMode) {
        _uiState.update { it.copy(defaultMode = mode) }
        saveSetting("flashlight_default_mode", mode.name)
    }

    fun setAlwaysIntensityEnabled(enabled: Boolean) {
        _uiState.update { it.copy(alwaysIntensityEnabled = enabled) }
        saveSetting("flashlight_always_intensity_enabled", enabled)
    }

    fun setAlwaysIntensity(normalised: Float) {
        val clamped = normalised.coerceIn(0.1f, 1.0f)
        _uiState.update { it.copy(alwaysIntensity = clamped) }
        saveSetting("flashlight_always_intensity", clamped)
    }

    fun setAlwaysStrobeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(alwaysStrobeEnabled = enabled) }
        saveSetting("flashlight_always_strobe_enabled", enabled)
    }

    fun setAlwaysStrobe(ms: Long) {
        val clamped = ms.coerceIn(40L, 500L)
        _uiState.update { it.copy(alwaysStrobeMs = clamped) }
        saveSetting("flashlight_always_strobe", clamped)
    }

    fun setAlwaysPaceEnabled(enabled: Boolean) {
        _uiState.update { it.copy(alwaysPaceEnabled = enabled) }
        saveSetting("flashlight_always_pace_enabled", enabled)
    }

    fun setAlwaysPaceRange(min: Long, max: Long) {
        val pace = normalisePace(min, max)
        _uiState.update { it.copy(alwaysPaceRange = pace) }
        saveSetting("flashlight_always_pace_min", pace.first)
        saveSetting("flashlight_always_pace_max", pace.second)
    }

    private fun normalisePace(min: Long, max: Long): Pair<Long, Long> =
        min.coerceIn(30L, 1000L).coerceAtMost(max.coerceIn(30L, 1000L)) to
            max.coerceIn(30L, 1000L).coerceAtLeast(min.coerceIn(30L, 1000L))

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
            // Always send STOP instead of toggling: if the service timer (or an
            // external camera grab) already turned the beam off, toggling here
            // would light it again.
            context.startService(
                Intent(context, FlashlightService::class.java).apply {
                    action = FlashlightService.ACTION_STOP
                }
            )
            _uiState.update { it.copy(remainingSeconds = null, timerMinutes = 0) }
            saveSetting("flashlight_timer", 0)
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private fun startMode() {
        // DEV-SPOOF: demo never runs real pulse loops — the fake toggle alone
        // drives the UI; strobe/SOS remain pure screen cosmetics there.
        if (gate.sandboxed(com.frerox.toolz.data.developer.DevToolId.SYSTEM)) return
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
        // DEV-SPOOF: no torch calls while demo mode is armed — mode loops keep
        // ticking so the UI sequence stays alive, but the camera never lights.
        if (gate.sandboxed(com.frerox.toolz.data.developer.DevToolId.SYSTEM)) return
        val id = cameraId ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            _uiState.value.isBrightnessSupported) {
            applyStrengthLevel(id)
        } else {
            setTorchRaw(true)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyStrengthLevel(id: String) {
        try {
            val max   = _uiState.value.maxBrightness
            val level = (_uiState.value.brightness * max).toInt().coerceIn(1, max)
            cameraManager.turnOnTorchWithStrengthLevel(id, level)
            Log.d(TAG, "Torch strength set to $level / $max")
        } catch (e: Exception) {
            Log.w(TAG, "turnOnTorchWithStrengthLevel failed: ${e.message}")
            setTorchRaw(true)
        }
    }

    /** Low-level boolean torch toggle via [CameraManager.setTorchMode]. */
    private fun setTorchRaw(enabled: Boolean) {
        // DEV-SPOOF: the physical torch is never touched in demo mode.
        if (gate.sandboxed(com.frerox.toolz.data.developer.DevToolId.SYSTEM)) return
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