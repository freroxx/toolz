/*
 * Copyright (C) 2026 Toolz Contributors
 * Uses backdrop library © Kyant, Apache License 2.0
 */

package com.kyant.backdrop.catalog.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

interface UISensor {
    val gravityAngle: Float
    val gravity: Offset
    fun start()
    fun stop()
}

/**
 * Static dummy sensor used in Performance Mode or when sensors are unavailable.
 * Completely eliminates sensor callbacks, wakeups, and 60fps re-draws.
 */
object StaticUISensor : UISensor {
    override val gravityAngle: Float = 135f
    override val gravity: Offset = Offset.Zero
    override fun start() {}
    override fun stop() {}
}

@Composable
fun rememberUISensor(): UISensor {
    val performanceMode = LocalPerformanceMode.current
    if (performanceMode) {
        return StaticUISensor
    }

    val context = LocalContext.current
    val sharedSensor = remember(context.applicationContext) {
        SharedUISensorManager.getInstance(context.applicationContext)
    }

    DisposableEffect(sharedSensor) {
        sharedSensor.start()
        onDispose { sharedSensor.stop() }
    }

    return sharedSensor
}

/**
 * Single shared sensor instance with reference-counted lifecycle and angle deadbanding.
 * Even with dozens of glass elements on screen, only 1 accelerometer listener is registered.
 */
internal class SharedUISensorManager private constructor(context: Context) : UISensor {

    companion object {
        @Volatile
        private var instance: SharedUISensorManager? = null

        fun getInstance(context: Context): SharedUISensorManager {
            return instance ?: synchronized(this) {
                instance ?: SharedUISensorManager(context.applicationContext).also { instance = it }
            }
        }
    }

    override var gravityAngle: Float by mutableFloatStateOf(135f)
        private set

    override var gravity: Offset by mutableStateOf(Offset.Zero)
        private set

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var activeSubscribers = 0

    private var rawAngle = 135f
    private var rawGravity = Offset.Zero

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

            val x = event.values[0]
            val y = event.values[1]
            val norm = sqrt(x * x + y * y + 9.81f * 9.81f)

            val alpha = 0.25f
            val targetAngle = atan2(y, x) * (180f / PI).toFloat()
            rawAngle = rawAngle * (1f - alpha) + targetAngle * alpha
            rawGravity = rawGravity * (1f - alpha) + Offset(x / norm, y / norm) * alpha

            // Deadband threshold: Only update Compose state if angle changed by >= 4.5 degrees
            // or gravity vector moved significantly.
            // This eliminates sensor jitter, wakeups, and unnecessary 60fps re-draws when held.
            if (abs(rawAngle - gravityAngle) >= 4.5f) {
                gravityAngle = rawAngle
            }
            if ((rawGravity - gravity).getDistance() >= 0.08f) {
                gravity = rawGravity
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    @Synchronized
    override fun start() {
        if (activeSubscribers == 0 && accelerometer != null && sensorManager != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
        activeSubscribers++
    }

    @Synchronized
    override fun stop() {
        if (activeSubscribers > 0) {
            activeSubscribers--
            if (activeSubscribers == 0) {
                sensorManager?.unregisterListener(listener)
            }
        }
    }
}
