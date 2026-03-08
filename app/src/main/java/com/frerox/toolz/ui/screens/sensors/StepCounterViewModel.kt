package com.frerox.toolz.ui.screens.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StepState(
    val steps: Int = 0,
    val goal: Int = 10000,
    val isSensorPresent: Boolean = true
)

@HiltViewModel
class StepCounterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel(), SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val _uiState = MutableStateFlow(StepState())
    val uiState: StateFlow<StepState> = _uiState.asStateFlow()

    private var initialSteps = -1

    init {
        viewModelScope.launch {
            settingsRepository.stepGoal.collect { goal ->
                _uiState.update { it.copy(goal = goal) }
            }
        }
        if (stepSensor == null) {
            _uiState.update { it.copy(isSensorPresent = false) }
        }
    }

    fun startListening() {
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0].toInt()
            if (initialSteps == -1) {
                initialSteps = totalStepsSinceBoot
            }
            val stepsToday = totalStepsSinceBoot - initialSteps
            _uiState.update { it.copy(steps = stepsToday) }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
