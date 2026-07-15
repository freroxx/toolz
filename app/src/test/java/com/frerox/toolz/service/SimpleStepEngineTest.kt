package com.frerox.toolz.service

import org.junit.Assert.*
import org.junit.Test

class SimpleStepEngineTest {

    @Test
    fun `SimpleStepEngine basic step detection`() {
        var steps = 0
        val engine = SimpleStepEngine(
            onStepDetected = { count, _ -> steps = count },
            useHardwareStepCounter = false
        )

        val t = System.currentTimeMillis()
        fun s(time: Long) {
            // Pulse
            engine.processAccelerometer(floatArrayOf(0f, 0f, 30.0f), time)
            // Rest samples to bring smoothedMag below threshold
            for (i in 1..20) {
                engine.processAccelerometer(floatArrayOf(0f, 0f, 9.8f), time + (i * 20))
            }
        }

        repeat(5) { i -> s(t + (i * 1000)) }
        assertTrue("Expected some steps but got $steps", steps > 0)
    }

    @Test
    fun `SimpleStepEngine hardware counter validation`() {
        var steps = 0
        val engine = SimpleStepEngine(
            onStepDetected = { count, _ -> steps = count },
            useHardwareStepCounter = true
        )

        val t = System.currentTimeMillis()
        // Motion peak
        engine.processAccelerometer(floatArrayOf(0f, 0f, 30.0f), t - 1000)
        for (i in 1..20) {
            engine.processAccelerometer(floatArrayOf(0f, 0f, 9.8f), t - 1000 + (i * 20))
        }
        
        // Deliver hardware steps
        engine.onOsStepDetected(10)
        assertEquals("Steps should be flushed after motion validation", 10, steps)
    }

    @Test
    fun `SimpleStepEngine gyro gate suppression`() {
        var steps = 0
        val engine = SimpleStepEngine(
            onStepDetected = { count, _ -> steps = count },
            useHardwareStepCounter = false
        )

        var timeOffset = 0L
        val baseTime = System.currentTimeMillis()
        fun s() {
            engine.processAccelerometer(floatArrayOf(0f, 0f, 30.0f), baseTime + timeOffset)
            timeOffset += 20L
            for (i in 1..20) {
                engine.processAccelerometer(floatArrayOf(0f, 0f, 9.8f), baseTime + timeOffset)
                timeOffset += 20L
            }
        }

        s()
        val initial = steps
        assertTrue("Initial step failed", initial > 0)

        // Gyro spike
        engine.processGyroscope(10.0f, baseTime + timeOffset)
        timeOffset += 20L
        
        // Attempt step during gate
        s()
        assertEquals("Step should be blocked by gyro", initial, steps)

        // Wait and attempt again
        timeOffset += 1000L
        s()
        assertTrue("Step should be allowed after gate", steps > initial)
    }
}
