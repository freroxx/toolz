package com.frerox.toolz.ui.screens.light

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.sensors.LightSensorManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LightMeterViewModel @Inject constructor(
    private val lightSensorManager: LightSensorManager
) : ViewModel() {

    val luxValue: StateFlow<Float> = lightSensorManager.getLightLevel()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0f
        )

    val hasSensor: Boolean = lightSensorManager.hasSensor()
}
