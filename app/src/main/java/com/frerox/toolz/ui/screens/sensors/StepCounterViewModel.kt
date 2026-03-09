package com.frerox.toolz.ui.screens.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.steps.StepEntry
import com.frerox.toolz.data.steps.StepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StepState(
    val steps: Int = 0,
    val goal: Int = 10000,
    val isSensorPresent: Boolean = true,
    val weeklyHistory: List<StepEntry> = emptyList()
)

@HiltViewModel
class StepCounterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val stepRepository: StepRepository
) : ViewModel() {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val uiState: StateFlow<StepState> = combine(
        stepRepository.currentSteps,
        settingsRepository.stepGoal,
        stepRepository.weeklySteps
    ) { steps, goal, history ->
        StepState(
            steps = steps,
            goal = goal,
            isSensorPresent = stepSensor != null,
            weeklyHistory = history
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StepState())

    fun updateGoal(newGoal: Int) {
        viewModelScope.launch {
            settingsRepository.setStepGoal(newGoal)
        }
    }
}
