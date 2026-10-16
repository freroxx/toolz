/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Shake-to-open-dev-console detector. Only runs while Developer Mode AND
 * the shake toggle are both ON — zero sensor cost otherwise. Coarse
 * threshold + 2s cooldown so it never fires in a pocket.
 */

package com.frerox.toolz.data.developer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class ShakeDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var manager: SensorManager? = null
    private var listening = false
    private var lastShakeAt = 0L
    private var onShake: (() -> Unit)? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values.getOrNull(0) ?: return
            val y = event.values.getOrNull(1) ?: return
            val z = event.values.getOrNull(2) ?: return
            val g = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
            if (g > 2.6) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastShakeAt > 2000) {
                    lastShakeAt = now
                    try { onShake?.invoke() } catch (_: Exception) { }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start(callback: () -> Unit) {
        if (listening) {
            onShake = callback
            return
        }
        onShake = callback
        try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
            val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            manager = sm
            listening = true
        } catch (_: Exception) { }
    }

    fun stop() {
        onShake = null
        if (!listening) return
        try { manager?.unregisterListener(listener) } catch (_: Exception) { }
        manager = null
        listening = false
    }
}
