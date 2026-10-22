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

package com.frerox.toolz.ui.screens.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.R
import com.frerox.toolz.data.device.DeviceSpecMapper
import com.frerox.toolz.data.device.DeviceSpecUiModel
import com.frerox.toolz.data.device.DeviceSpecsRepository
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.SpoofParams
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BatteryState(
    val level: Int = 0,
    @StringRes val statusRes: Int = R.string.st_BatteryInfoScreen_status_unknown,
    @StringRes val healthRes: Int = R.string.st_BatteryInfoScreen_health_good,
    val temperature: Float = 0f,
    val voltage: Int = 0,
    val technology: String = "",
    val isCharging: Boolean = false,
    val isFull: Boolean = false,
    @StringRes val powerSourceRes: Int = R.string.st_BatteryInfoScreen_power_none,
    val currentNowMa: Int = 0, // Current in mA
    val capacityMah: Int = 0, // Capacity in mAh
    val chargeCounterUah: Int = 0, // Charge counter in uAh
    val remoteSpec: DeviceSpecUiModel? = null,
    val isRemoteLoading: Boolean = false,
    val remoteError: String? = null
)

@HiltViewModel
class BatteryInfoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val specsRepository: DeviceSpecsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatteryState())
    val uiState: StateFlow<BatteryState> = _uiState.asStateFlow()

    private val batterySandboxed: Flow<Boolean> = combine(
        settingsRepository.developerModeEnabled,
        settingsRepository.spoofFlag(BATTERY_SPOOF_KEY),
    ) { master, flag -> master && flag }.distinctUntilChanged()

    private var listeningJob: Job? = null
    private var receiverRegistered = false

    /**
     * Guards against a broadcast already queued on the main looper landing
     * after the fixture readout is applied. Real battery data must never
     * overwrite an active sandbox.
     */
    @Volatile private var sandboxActive = false

    /**
     * Fixture values are read from DataStore in the caller's coroutine. They
     * must never block the main thread, and remote specs are hidden while the
     * sandbox is active so real device data cannot mix into the fixture view.
     */
    private suspend fun emitSpoofBattery() {
        val p = try {
            SpoofParams.parse(settingsRepository.spoofParamsJson.first())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            SpoofParams()
        }
        _uiState.update {
            it.copy(
                level = p.batteryLevel,
                statusRes = if (p.batteryCharging) R.string.st_BatteryInfoScreen_status_charging else R.string.st_BatteryInfoScreen_status_discharging,
                healthRes = if (p.batteryTempC >= 45f) R.string.st_BatteryInfoScreen_health_overheat else R.string.st_BatteryInfoScreen_health_good,
                temperature = p.batteryTempC,
                voltage = 3420,
                technology = "Li-Po (fixture)",
                isCharging = p.batteryCharging,
                isFull = false,
                powerSourceRes = R.string.st_BatteryInfoScreen_power_battery,
                currentNowMa = if (p.batteryCharging) 1800 else -1250,
                chargeCounterUah = 210000,
                capacityMah = 5000,
                remoteSpec = null,
                isRemoteLoading = false,
                remoteError = null,
            )
        }
    }

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val modelQuery: String by lazy {
        Build.MODEL.trim().ifBlank { Build.DEVICE.trim() }.ifBlank { "Unknown" }
    }

    private var cachedCapacityMah: Int = -1

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) applyBatteryIntent(intent)
        }
    }

    private fun applyBatteryIntent(intent: Intent) {
        if (sandboxActive) return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (scale > 0) level * 100 / scale.toFloat() else 0f

        val statusInt = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING
        val isFull = statusInt == BatteryManager.BATTERY_STATUS_FULL || (isCharging && batteryPct >= 100f)

        val status = when {
            isFull -> R.string.st_BatteryInfoScreen_status_full
            isCharging -> R.string.st_BatteryInfoScreen_status_charging
            statusInt == BatteryManager.BATTERY_STATUS_DISCHARGING -> R.string.st_BatteryInfoScreen_status_discharging
            statusInt == BatteryManager.BATTERY_STATUS_NOT_CHARGING -> R.string.st_BatteryInfoScreen_status_not_charging
            else -> R.string.st_BatteryInfoScreen_status_unknown
        }

        val chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val powerSource = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_USB -> R.string.st_BatteryInfoScreen_power_usb
            BatteryManager.BATTERY_PLUGGED_AC -> R.string.st_BatteryInfoScreen_power_ac
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> R.string.st_BatteryInfoScreen_power_wireless
            else -> R.string.st_BatteryInfoScreen_power_battery
        }

        val healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> R.string.st_BatteryInfoScreen_health_good
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.st_BatteryInfoScreen_health_overheat
            BatteryManager.BATTERY_HEALTH_DEAD -> R.string.st_BatteryInfoScreen_health_dead
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.st_BatteryInfoScreen_health_over_voltage
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> R.string.st_BatteryInfoScreen_health_failure
            else -> R.string.st_BatteryInfoScreen_health_unknown
        }

        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val volt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""

        val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000
        val chargeCounter = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)

        if (cachedCapacityMah == -1) {
            cachedCapacityMah = getBatteryCapacity(context).toInt()
        }

        _uiState.update { state ->
            state.copy(
                level = batteryPct.toInt(),
                statusRes = status,
                isCharging = isCharging || isFull,
                isFull = isFull,
                powerSourceRes = powerSource,
                healthRes = health,
                temperature = temp,
                voltage = volt,
                technology = tech,
                currentNowMa = currentNow,
                chargeCounterUah = chargeCounter,
                capacityMah = cachedCapacityMah
            )
        }
    }

    private fun getBatteryCapacity(context: Context): Float {
        val mPowerProfile: Any?
        var batteryCapacity = 0.0
        val POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile"

        try {
            mPowerProfile = Class.forName(POWER_PROFILE_CLASS)
                .getConstructor(Context::class.java)
                .newInstance(context)

            batteryCapacity = Class.forName(POWER_PROFILE_CLASS)
                .getMethod("getBatteryCapacity")
                .invoke(mPowerProfile) as Double
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return batteryCapacity.toFloat()
    }

    fun startListening() {
        if (listeningJob?.isActive == true) return
        listeningJob = viewModelScope.launch {
            try {
                // DataStore emits the persisted value on collection, so this
                // decides the initial mode without a second blocking read.
                batterySandboxed.collect { sandboxed ->
                    if (sandboxed) {
                        sandboxActive = true
                        unregisterBatteryReceiver()
                        emitSpoofBattery()
                        android.util.Log.w("DevSpoof", "Battery sandbox active — real receiver + spec fetch skipped")
                    } else {
                        sandboxActive = false
                        if (!registerBatteryReceiver()) {
                            // Do not leave fixture-only numbers on screen when
                            // the real receiver cannot be registered.
                            _uiState.value = BatteryState()
                        }
                        loadRemoteSpecs()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("DevSpoof", "Battery sandbox listener stopped", e)
                sandboxActive = false
                unregisterBatteryReceiver()
                if (!registerBatteryReceiver()) _uiState.value = BatteryState()
                loadRemoteSpecs()
            }
        }
    }

    private fun registerBatteryReceiver(): Boolean {
        if (receiverRegistered) return true
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        return try {
            val sticky = context.registerReceiver(batteryReceiver, filter)
            receiverRegistered = true
            sticky?.let(::applyBatteryIntent)
            true
        } catch (e: Exception) {
            android.util.Log.w("DevSpoof", "Battery receiver registration failed", e)
            false
        }
    }

    private fun unregisterBatteryReceiver() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            android.util.Log.w("DevSpoof", "Battery receiver unregistration failed", e)
        } finally {
            receiverRegistered = false
        }
    }

    fun loadRemoteSpecs(forceRefresh: Boolean = false) {
        if (_uiState.value.remoteSpec != null && !forceRefresh) return
        viewModelScope.launch {
            // DEV-SPOOF: never fetch remote specs while sandboxed.
            if (settingsRepository.isSandboxNow(BATTERY_SPOOF_KEY)) {
                android.util.Log.w("DevSpoof", "Blocked remote spec fetch (battery sandbox)")
                return@launch
            }
            _uiState.update { it.copy(isRemoteLoading = true, remoteError = null) }

            try {
                val result = withContext(Dispatchers.IO) {
                    specsRepository.getDeviceSpecs(modelQuery, forceRefresh = forceRefresh)
                }

                // The sandbox may have turned on while the request was in
                // flight; never surface real specs over fixture values.
                if (settingsRepository.isSandboxNow(BATTERY_SPOOF_KEY)) {
                    _uiState.update { it.copy(isRemoteLoading = false) }
                    return@launch
                }

                result.map { (response, isCached) -> DeviceSpecMapper.mapResponse(response, isCached) }
                    .onSuccess { specs ->
                        _uiState.update { it.copy(remoteSpec = specs, isRemoteLoading = false) }
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(isRemoteLoading = false, remoteError = error.message) }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isRemoteLoading = false, remoteError = e.message) }
            }
        }
    }

    fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
        unregisterBatteryReceiver()
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }

    private companion object {
        val BATTERY_SPOOF_KEY = DevToolId.BATTERY.prefKey
    }
}
