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

package com.frerox.toolz.service

import org.junit.Assert.*
import org.junit.Test

class StrictEngineTest {

    @Test
    fun `StrictEngine 6-step buffer logic`() {
        var emittedSteps = 0
        val engine = StrictEngine(
            onStepEmitted = { count -> emittedSteps += count },
            onLog = { println(it) }
        )

        val t = 1000000L
        
        // Helper to simulate a peak
        fun simulatePeak(time: Long) {
            // Rising edge
            engine.processAccelerometer(floatArrayOf(0f, 0f, 15.0f), time)
            // Descending edge
            engine.processAccelerometer(floatArrayOf(0f, 0f, 5.0f), time + 20)
        }

        // 1st peak
        simulatePeak(t)
        assertEquals(EngineState.SEARCHING, engine.state)
        assertEquals(0, emittedSteps)

        // Peaks 2-5 (Valid intervals 500ms)
        for (i in 1..4) {
            simulatePeak(t + (i * 500))
            assertEquals(EngineState.SEARCHING, engine.state)
            assertEquals(0, emittedSteps)
        }

        // 6th peak
        simulatePeak(t + (5 * 500))
        assertEquals(EngineState.TRACKING, engine.state)
        assertEquals(6, emittedSteps)

        // 7th peak
        simulatePeak(t + (6 * 500))
        assertEquals(7, emittedSteps)
    }

    @Test
    fun `StrictEngine frequency filter - too fast`() {
        val engine = StrictEngine(onStepEmitted = {}, onLog = { println(it) })
        val t = 1000000L
        
        fun simulatePeak(time: Long) {
            engine.processAccelerometer(floatArrayOf(0f, 0f, 15.0f), time)
            engine.processAccelerometer(floatArrayOf(0f, 0f, 5.0f), time + 20)
        }

        simulatePeak(t)
        assertEquals(EngineState.SEARCHING, engine.state)

        // Fast peak (200ms < 360ms)
        simulatePeak(t + 200)
        assertEquals("Should reset to IDLE on fast shaking", EngineState.IDLE, engine.state)
    }

    @Test
    fun `StrictEngine frequency filter - too slow`() {
        val engine = StrictEngine(onStepEmitted = {}, onLog = { println(it) })
        val t = 1000000L
        
        fun simulatePeak(time: Long) {
            engine.processAccelerometer(floatArrayOf(0f, 0f, 15.0f), time)
            engine.processAccelerometer(floatArrayOf(0f, 0f, 5.0f), time + 20)
        }

        simulatePeak(t)
        assertEquals(EngineState.SEARCHING, engine.state)

        // Slow peak (1000ms > 750ms)
        simulatePeak(t + 1000)
        assertEquals("Should reset to IDLE on slow tilting", EngineState.IDLE, engine.state)
    }

    @Test
    fun `StrictEngine grace period in TRACKING`() {
        var emittedSteps = 0
        val engine = StrictEngine(
            onStepEmitted = { count -> emittedSteps += count },
            onLog = { println(it) }
        )
        val t = 1000000L
        
        fun simulatePeak(time: Long) {
            engine.processAccelerometer(floatArrayOf(0f, 0f, 15.0f), time)
            engine.processAccelerometer(floatArrayOf(0f, 0f, 5.0f), time + 20)
        }

        // Get to TRACKING
        for (i in 0..5) {
            simulatePeak(t + (i * 500))
        }
        assertEquals(EngineState.TRACKING, engine.state)
        assertEquals(6, emittedSteps)

        // 1st invalid peak (1000ms) - should stay in TRACKING
        simulatePeak(t + 2500 + 1000) 
        assertEquals("Should stay in TRACKING after 1 invalid peak", EngineState.TRACKING, engine.state)
        assertEquals(6, emittedSteps)

        // 2nd invalid peak
        simulatePeak(t + 2500 + 2000)
        assertEquals("Should stay in TRACKING after 2 invalid peaks", EngineState.TRACKING, engine.state)

        // 3rd invalid peak
        simulatePeak(t + 2500 + 3000)
        assertEquals("Should drop to IDLE after 3 invalid peaks", EngineState.IDLE, engine.state)
    }

    @Test
    fun `StrictEngine vehicle detection`() {
        val engine = StrictEngine(onStepEmitted = {}, onLog = { println(it) })
        val t = 1000000L
        
        // Gentle movement (magnitude < 0.7f)
        for (i in 0..5000 step 20) {
            engine.processAccelerometer(floatArrayOf(0f, 0f, 9.8f + 0.3f), t + i)
        }
        
        assertEquals("Should be SUSPENDED due to continuous low amplitude", EngineState.SUSPENDED, engine.state)
        
        // Large movement should clear vehicle detection
        engine.processAccelerometer(floatArrayOf(0f, 0f, 15.0f), t + 5100)
        assertEquals("Should clear SUSPENDED on large movement", EngineState.IDLE, engine.state)
    }

    @Test
    fun `StrictEngine reset on global timeout`() {
        val engine = StrictEngine(onStepEmitted = {}, onLog = { println(it) })
        val t = 1000000L
        
        engine.processAccelerometer(floatArrayOf(0f, 0f, 15.0f), t)
        engine.processAccelerometer(floatArrayOf(0f, 0f, 5.0f), t + 20)
        assertEquals(EngineState.SEARCHING, engine.state)

        // Long pause without any accelerometer events
        engine.processAccelerometer(floatArrayOf(0f, 0f, 9.8f), t + 3000)
        assertEquals("Should reset on global idle timeout", EngineState.IDLE, engine.state)
    }
}
