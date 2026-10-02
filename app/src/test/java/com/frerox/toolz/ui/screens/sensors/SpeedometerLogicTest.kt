/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.sensors

import org.junit.Assert.*
import org.junit.Test

class SpeedometerLogicTest {

    @Test
    fun `drift stays at zero below exit threshold`() {
        val smoother = SpeedSmoother()
        repeat(10) { assertEquals(0f, smoother.update(0.4f, 5f, 0.5f)) }
        assertTrue(smoother.stationary)
    }

    @Test
    fun `sustained motion exits stationary and drift re-enters it`() {
        val smoother = SpeedSmoother()
        var out = 0f
        repeat(10) { out = smoother.update(5f, 5f, 0.5f) }
        assertTrue(out > 0f)
        assertFalse(smoother.stationary)
        repeat(10) { out = smoother.update(0f, 5f, 0.5f) }
        assertEquals(0f, out)
        assertTrue(smoother.stationary)
    }

    @Test
    fun `single spike is killed by the median window`() {
        val smoother = SpeedSmoother()
        repeat(5) { smoother.update(10f, 5f, 0.5f) }
        val out = smoother.update(30f, 5f, 0.5f)
        assertTrue("single spike leaked: $out", out < 12f)
    }

    @Test
    fun `sustained spike on poor fix is clamped not followed`() {
        val smoother = SpeedSmoother()
        repeat(5) { smoother.update(10f, 5f, 0.5f) }
        var out = 0f
        repeat(3) { out = smoother.update(30f, 40f, null) }
        assertTrue("spike not clamped: $out", out < 22f)
    }

    @Test
    fun `bad speed accuracy slows adaptation`() {
        val trusted = SpeedSmoother()
        val untrusted = SpeedSmoother()
        repeat(3) {
            trusted.update(0f, 5f, 0.5f)
            untrusted.update(0f, 5f, 0.5f)
        }
        var trustedOut = 0f
        var untrustedOut = 0f
        repeat(3) {
            trustedOut = trusted.update(5f, 5f, 0.5f)
            untrustedOut = untrusted.update(5f, 5f, 9f)
        }
        assertTrue("trusted=$trustedOut untrusted=$untrustedOut", trustedOut > untrustedOut)
    }

    @Test
    fun `altitude smoothing converges without jumping`() {
        var alt: Double? = null
        repeat(10) { alt = smoothAltitude(alt, 100.0) }
        assertEquals(100.0, alt!!, 0.001)
        val step = smoothAltitude(alt, 110.0)
        assertTrue(step > 100.0 && step < 110.0)
    }

    @Test
    fun `auto-start only when enabled idle and not paused`() {
        assertTrue(shouldAutoStart(TripState.IDLE, autoRecord = true, userPaused = false))
        assertFalse(shouldAutoStart(TripState.IDLE, autoRecord = false, userPaused = false))
        assertFalse(shouldAutoStart(TripState.IDLE, autoRecord = true, userPaused = true))
        assertFalse(shouldAutoStart(TripState.RECORDING, autoRecord = true, userPaused = false))
        assertFalse(shouldAutoStart(TripState.PAUSED, autoRecord = true, userPaused = false))
    }

    @Test
    fun `reset stays recording unless paused or disabled`() {
        assertTrue(resetStaysRecording(autoRecord = true, userPaused = false))
        assertFalse(resetStaysRecording(autoRecord = false, userPaused = false))
        assertFalse(resetStaysRecording(autoRecord = true, userPaused = true))
    }

    @Test
    fun `step cadence converts to speed`() {
        // 3 steps/s at 75 cm stride = 2.25 m/s (~8 km/h run).
        assertEquals(2.25f, stepSpeedMps(3, 1.0, 75), 0.001f)
        assertEquals(0f, stepSpeedMps(0, 1.0, 75))
        assertEquals(0f, stepSpeedMps(3, 0.0, 75))
        assertEquals(0f, stepSpeedMps(3, 1.0, 0))
    }
}
