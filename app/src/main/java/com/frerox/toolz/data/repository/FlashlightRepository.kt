package com.frerox.toolz.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlashlightRepository @Inject constructor() {
    private val _isOn = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    private val _mode = MutableStateFlow(com.frerox.toolz.ui.screens.light.FlashlightMode.STEADY)
    val mode: StateFlow<com.frerox.toolz.ui.screens.light.FlashlightMode> = _mode.asStateFlow()

    private val _brightness = MutableStateFlow(1.0f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    fun setOn(value: Boolean) {
        _isOn.value = value
    }

    fun setMode(value: com.frerox.toolz.ui.screens.light.FlashlightMode) {
        _mode.value = value
    }

    fun setBrightness(value: Float) {
        _brightness.value = value
    }
}
