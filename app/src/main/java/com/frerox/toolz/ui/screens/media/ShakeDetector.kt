package com.frerox.toolz.ui.screens.media

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import kotlin.math.sqrt

class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {

    private var lastUpdate: Long = 0
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    
    // Threshold for heavy shakes (increased for reliability)
    private val SHAKE_THRESHOLD = 5000.0 
    // Counter for continuous shakes
    private var shakeCount = 0
    private var lastShakeTimestamp: Long = 0
    private val MIN_CONTINUOUS_SHAKES = 5
    private val SHAKE_WINDOW_MS = 1200L

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

            if (speed > SHAKE_THRESHOLD) {
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
