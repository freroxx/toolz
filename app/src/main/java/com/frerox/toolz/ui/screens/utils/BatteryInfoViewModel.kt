package com.frerox.toolz.ui.screens.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.device.DeviceSpecMapper
import com.frerox.toolz.data.device.DeviceSpecUiModel
import com.frerox.toolz.data.device.DeviceSpecsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BatteryState(
    val level: Int = 0,
    val status: String = "Unknown",
    val health: String = "Good",
    val temperature: Float = 0f,
    val voltage: Int = 0,
    val technology: String = "",
    val isCharging: Boolean = false,
    val isFull: Boolean = false,
    val powerSource: String = "None",
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
    private val specsRepository: DeviceSpecsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatteryState())
    val uiState: StateFlow<BatteryState> = _uiState.asStateFlow()

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val modelQuery: String by lazy {
        Build.MODEL.trim().ifBlank { Build.DEVICE.trim() }.ifBlank { "Unknown" }
    }

    private var cachedCapacityMah: Int = -1

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (scale > 0) level * 100 / scale.toFloat() else 0f

                val statusInt = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING
                val isFull = statusInt == BatteryManager.BATTERY_STATUS_FULL || (isCharging && batteryPct >= 100f)

                val status = when {
                    isFull -> "Full"
                    isCharging -> "Charging"
                    statusInt == BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    statusInt == BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                    else -> "Unknown"
                }

                val chargePlug = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                val powerSource = when (chargePlug) {
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC Adapter"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> "Battery"
                }

                val healthInt = it.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                val health = when (healthInt) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                    else -> "Unknown"
                }

                val temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
                val volt = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                val tech = it.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""

                // Get dynamic properties
                val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000 // to mA
                val chargeCounter = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) // in uAh
                
                if (cachedCapacityMah == -1) {
                    cachedCapacityMah = getBatteryCapacity(context!!).toInt()
                }

                _uiState.update { state ->
                    state.copy(
                        level = batteryPct.toInt(),
                        status = status,
                        isCharging = isCharging || isFull,
                        isFull = isFull,
                        powerSource = powerSource,
                        health = health,
                        temperature = temp,
                        voltage = volt,
                        technology = tech,
                        currentNowMa = currentNow,
                        chargeCounterUah = chargeCounter,
                        capacityMah = cachedCapacityMah
                    )
                }
            }
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
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
        loadRemoteSpecs()
    }

    fun loadRemoteSpecs(forceRefresh: Boolean = false) {
        if (_uiState.value.remoteSpec != null && !forceRefresh) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isRemoteLoading = true, remoteError = null) }
            
            try {
                val result = withContext(Dispatchers.IO) {
                    specsRepository.getDeviceSpecs(modelQuery, forceRefresh = forceRefresh)
                }
                
                result.map { (response, isCached) -> DeviceSpecMapper.mapResponse(response, isCached) }
                    .onSuccess { specs ->
                        _uiState.update { it.copy(remoteSpec = specs, isRemoteLoading = false) }
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(isRemoteLoading = false, remoteError = error.message) }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRemoteLoading = false, remoteError = e.message) }
            }
        }
    }

    fun stopListening() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Ignored
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
