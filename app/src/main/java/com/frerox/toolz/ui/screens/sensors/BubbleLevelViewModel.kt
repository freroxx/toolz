package com.frerox.toolz.ui.screens.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * State representing the inclination on X and Y axes.
 */
data class BubbleState(
    val x: Float = 0f,
    val y: Float = 0f,
    val rawX: Float = 0f,
    val rawY: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val isHeld: Boolean = false,
    val isListening: Boolean = false,
    val mode: LevelMode = LevelMode.BULLSEYE
)

enum class LevelMode {
    BULLSEYE, HORIZONTAL, VERTICAL
}

@HiltViewModel
class BubbleLevelViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel(), SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _bubbleState = MutableStateFlow(BubbleState())
    val bubbleState: StateFlow<BubbleState> = _bubbleState.asStateFlow()

    // Low-pass filter constant
    private val alpha = 0.15f // Smoother for expressive feel
    private var currentX = 0f
    private var currentY = 0f

    fun startListening() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            _bubbleState.update { it.copy(isListening = true) }
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        _bubbleState.update { it.copy(isListening = false) }
    }

    fun calibrate() {
        _bubbleState.update { 
            it.copy(
                offsetX = currentX,
                offsetY = currentY
            )
        }
    }

    fun resetCalibration() {
        _bubbleState.update { 
            it.copy(
                offsetX = 0f,
                offsetY = 0f
            )
        }
    }

    fun toggleHold() {
        _bubbleState.update { it.copy(isHeld = !it.isHeld) }
    }

    fun setMode(mode: LevelMode) {
        _bubbleState.update { it.copy(mode = mode) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && !_bubbleState.value.isHeld) {
            // Apply low-pass filter for smoother bubble movement
            currentX = currentX + alpha * (event.values[0] - currentX)
            currentY = currentY + alpha * (event.values[1] - currentY)
            
            _bubbleState.update { 
                it.copy(
                    rawX = currentX,
                    rawY = currentY,
                    x = currentX - it.offsetX,
                    y = currentY - it.offsetY
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
