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
import android.os.Handler
import android.os.Looper
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
    @Volatile private var onShake: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dispatch = Runnable {
        try { onShake?.invoke() } catch (_: Exception) { }
    }

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values.getOrNull(0) ?: return
            val y = event.values.getOrNull(1) ?: return
            val z = event.values.getOrNull(2) ?: return
            if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return
            val g = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
            if (g > 2.6) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastShakeAt > 2_000) {
                    lastShakeAt = now
                    // Sensor callbacks are not on the main thread; navigation must be.
                    mainHandler.post(dispatch)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start(callback: () -> Unit) {
        onShake = callback
        if (listening) return
        try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sm == null || accel == null) {
                onShake = null
                return
            }
            lastShakeAt = 0L
            if (!sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)) {
                onShake = null
                return
            }
            manager = sm
            listening = true
        } catch (_: Exception) {
            onShake = null
            manager = null
            listening = false
        }
    }

    fun stop() {
        onShake = null
        mainHandler.removeCallbacks(dispatch)
        if (!listening) return
        try { manager?.unregisterListener(listener) } catch (_: Exception) { }
        manager = null
        listening = false
    }
}
