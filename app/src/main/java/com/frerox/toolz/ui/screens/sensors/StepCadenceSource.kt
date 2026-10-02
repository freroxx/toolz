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

package com.frerox.toolz.ui.screens.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.min

/**
 * Emits per-second step deltas from the hardware step counter for indoor
 * (treadmill) speed estimation: cadence x stride length.
 *
 * Deliberately independent of [com.frerox.toolz.service.StepCounterService]:
 * indoor speed works even when the step-tracker tool is disabled. The OS
 * step counter is ultra-low-power and needs no foreground service, just
 * ACTIVITY_RECOGNITION (already declared in the manifest).
 */
class StepCadenceSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    val hasSensor: Boolean
        get() = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * One emission per second: steps counted during that second, capped at a
     * sanity bound (10/s; elite sprint cadence is ~4-5). Quiet seconds emit 0,
     * so stopping decays the speed without extra staleness bookkeeping.
     * Closes immediately without permission or hardware.
     */
    fun stepsPerSecond(): Flow<Int> = callbackFlow {
        if (!hasSensor || !hasPermission()) {
            close()
            return@callbackFlow
        }
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        var lastRaw: Long? = null
        var pending = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
                val raw = event.values[0].toLong()
                val previous = lastRaw
                lastRaw = raw
                if (previous == null) return
                val delta = raw - previous
                // Counter went backwards (reboot/sensor reset): rebase silently.
                if (delta < 0) return
                pending += delta
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }

        sensorManager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            Handler(Looper.getMainLooper()),
        )

        val ticker = launch {
            while (true) {
                delay(1_000L)
                val steps = min(pending, MAX_STEPS_PER_SECOND).toInt()
                pending = 0L
                trySend(steps)
            }
        }

        awaitClose {
            ticker.cancel()
            sensorManager.unregisterListener(listener)
        }
    }

    private companion object {
        const val MAX_STEPS_PER_SECOND = 10L
    }
}
