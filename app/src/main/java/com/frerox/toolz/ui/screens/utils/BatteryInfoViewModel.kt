package com.frerox.toolz.ui.screens.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class BatteryState(
    val level: Int = 0,
    val status: String = "Unknown",
    val health: String = "Good",
    val temperature: Float = 0f,
    val voltage: Int = 0,
    val technology: String = "",
    val isCharging: Boolean = false,
    val powerSource: String = "None",
    val currentNowMa: Int = 0, // Current in mA
    val capacityAh: Float = 0f, // Capacity in Ah
    val chargeCounterUah: Int = 0 // Charge counter in uAh
)

@HiltViewModel
class BatteryInfoViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatteryState())
    val uiState: StateFlow<BatteryState> = _uiState.asStateFlow()

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = level * 100 / scale.toFloat()

                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

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
                val capacity = getBatteryCapacity(context!!)

                _uiState.update { state ->
                    state.copy(
                        level = batteryPct.toInt(),
                        status = if (isCharging) "Charging" else "Discharging",
                        isCharging = isCharging,
                        powerSource = powerSource,
                        health = health,
                        temperature = temp,
                        voltage = volt,
                        technology = tech,
                        currentNowMa = currentNow,
                        chargeCounterUah = chargeCounter,
                        capacityAh = capacity
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
