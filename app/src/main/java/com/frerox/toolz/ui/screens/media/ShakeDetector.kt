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

package com.frerox.toolz.ui.screens.media

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import kotlin.math.sqrt

class ShakeDetector(
    private var threshold: Double = 3500.0,
    private val onShake: () -> Unit
) : SensorEventListener {

    private var lastUpdate: Long = 0
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    
    // Counter for continuous shakes
    private var shakeCount = 0
    private var lastShakeTimestamp: Long = 0
    private val MIN_CONTINUOUS_SHAKES = 3
    private val SHAKE_WINDOW_MS = 1200L

    fun updateThreshold(sensitivity: Float) {
        // sensitivity 0.0 -> 8000.0 (hard), 1.0 -> 2000.0 (easy)
        threshold = 8000.0 - (sensitivity * 6000.0)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val curTime = System.currentTimeMillis()
        
        if (curTime - lastUpdate > 100) {
            val diffTime = (curTime - lastUpdate).toFloat()
            lastUpdate = curTime

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val deltaX = x - lastX
            val deltaY = y - lastY
            val deltaZ = z - lastZ
            
            val speed = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()) / diffTime * 10000

            if (speed > threshold) {
                // Check if this shake is part of a continuous sequence
                if (curTime - lastShakeTimestamp < SHAKE_WINDOW_MS) {
                    shakeCount++
                } else {
                    shakeCount = 1
                }
                
                lastShakeTimestamp = curTime
                
                // Only trigger after heavy continuous shakes
                if (shakeCount >= MIN_CONTINUOUS_SHAKES) {
                    onShake()
                    shakeCount = 0 // Reset after trigger
                }
            }

            lastX = x
            lastY = y
            lastZ = z
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
