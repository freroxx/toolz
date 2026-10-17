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

package com.frerox.toolz.data.developer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * DEV-SPOOF simulation engine: turns the static console sliders into *live mock
 * data*. Every tool sandboxed through [SpoofGate] samples here instead of frozen
 * one-shot values, so gauges behave like the real thing:
 *
 *   - Speedometer runs a full drive cycle (accelerate → cruise → coast → repeat).
 *   - Compass drifts and leans, the lux meter wanders indoors/outdoors, the sound
 *     meter breathes with ambient noise, the bubble level wobbles, the altimeter
 *     climbs and descends, ping jitters and download rates surge.
 *
 * Design: values are pure functions of `SystemClock.elapsedRealtime()` plus the
 * persisted [SpoofParams]. That gives us — with zero shared state and zero
 * background timers —
 *
 *   - **smoothness** (sums of sines are infinitely differentiable, sampled at
 *     whatever rate each screen happens to tick at),
 *   - **determinism** (any collector sampling at any cadence sees the same
 *     timeline, so nothing desyncs),
 *   - **RAM-only honesty** (nothing persists, nothing leaves the device).
 *
 * All callers pass `tMillis = SystemClock.elapsedRealtime()` and the current
 * [SpoofParams]; a slider move retargets the simulation fluidly because the
 * base amplitudes are read at every sample.
 */
object SpoofSim {

    // region Primitives

    /** 2π / period helper: sin at frequency [periodMs]. */
    private fun osc(tMs: Long, periodMs: Long, phase: Float = 0f): Float =
        sin((tMs * (2 * PI / periodMs)) + phase).toFloat()

    /** Smooth cosine ramp on `x` clipped to 0..1 — no visible velocity kinks. */
    private fun smooth(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return 0.5f - 0.5f * cos(c * PI.toFloat())
    }

    // endregion

    // region Periodic event helpers (deterministic "randomness")

    /**
     * Once per [cycleMs] there is an event window; returns a 0..1 envelope for
     * the window when the current time is inside one, else 0. Deterministic
     * stand-in for "sometimes a cloud passes / sometimes a car goes by".
     */
    private fun eventEnvelope(tMs: Long, cycleMs: Long, widthMs: Long): Float {
        val phase = ((tMs % cycleMs) + cycleMs) % cycleMs
        if (phase >= widthMs) return 0f
        // hold 60% of the window, then release smoothly over the rest.
        val hold = widthMs * 0.6f
        return if (phase < hold) 1f else smooth(1f - (phase - hold) / (widthMs - hold))
    }

    // endregion

    // region Speedometer — real drive cycle

    /** Length of one accelerate→cruise→decelerate→crawl repeat. */
    private const val DRIVE_CYCLE_MS = 75_000L

    /** Ride height of the cruise wobble. */
    private const val CRUISE_WOBBLE = 0.035f

    /**
     * Synthetic speed in **m/s** at time [tMs]. The cycle (fraction `u` of 75s):
     *
     *   0.00–0.32  accelerate   0 → 1 (ease-in-out, like flooring it from a light)
     *   0.32–0.62  cruise       1, wobbling ±3.5% (traffic breathing)
     *   0.62–0.82  decelerate   1 → 0.12 (rolling off toward a stop)
     *   0.82–1.00  crawl/idle   0.12 → ramp back up (next cycle's launch)
     *
     * A slider of 120 km/h therefore *drives like a car pegged around 120* —
     * the dial sweeps from standstill, wobbles on cruise, and rolls off, which
     * also exercises max/avg/min stats, the trend chart and the gauge rescaling.
     */
    fun speedMps(tMs: Long, p: SpoofParams): Float {
        val topKmh = p.speedKmh.coerceAtLeast(0)
        if (topKmh == 0) return 0f
        val cycle = ((tMs % DRIVE_CYCLE_MS) + DRIVE_CYCLE_MS) % DRIVE_CYCLE_MS
        val u = cycle.toFloat() / DRIVE_CYCLE_MS

        val shape: Float = when {
            u < 0.32f -> smooth(u / 0.32f)
            u < 0.62f -> 1f
            u < 0.82f -> 1f - 0.88f * smooth((u - 0.62f) / 0.20f)
            else -> 0.12f + 0.88f * smooth((u - 0.82f) / 0.18f)
        }
        val wobble = CRUISE_WOBBLE * osc(tMs, 4_300) + 0.018f * osc(tMs, 1_700, 1.1f)

        // Floor it at walking pace while the cycle is moving; true zero only at the
        // very bottom of the deceleration valley, exactly like a real drive.
        val speedKmh = topKmh * (shape + wobble * if (shape > 0.25f) 1f else 0f)
        return (speedKmh / 3.6f).coerceAtLeast(0f)
    }

    /** Acceleration estimate (m/s²) at [tMs] — useful for a G-meter style readout. */
    fun speedAccelMps2(tMs: Long, p: SpoofParams): Float {
        val dt = 200L
        val a = speedMps(tMs - dt, p)
        val b = speedMps(tMs + dt, p)
        return (b - a) / (2 * dt.toFloat() / 1_000f)
    }

    // endregion

    // region Compass — drifting heading

    /**
     * Heading in degrees at [tMs]: swings around the slider base ±28° on a slow
     * wander plus a quicker small lean, like carrying the phone around.
     */
    fun headingDeg(tMs: Long, p: SpoofParams): Float {
        val base = p.compassDeg.toFloat()
        return ((base + 28f * osc(tMs, 23_000) + 9f * osc(tMs, 7_100, 1.4f) + 360f) % 360f +
            360f) % 360f
    }

    // endregion

    // region Sound meter — ambient breathing

    /**
     * Ambient level in dB(SP L) at [tMs]: slow ±10% breathing, a mid shimmer and
     * a soft fast flutter, plus a periodic passing-event dip. Never below 20dB,
     * never more than 3dB over the slider set.
     */
    fun decibels(tMs: Long, p: SpoofParams): Float {
        val base = p.soundDb.toFloat().coerceIn(0f, 160f)
        if (base <= 25f) return base.coerceAtLeast(0f)
        val noise = base * (0.10f * osc(tMs, 9_000) + 0.04f * osc(tMs, 2_700, 0.8f) +
            0.022f * osc(tMs, 700, 1.9f))
        // A passer-by every ~41s gently hollows the reading out for five seconds.
        val passerBy = 6f * eventEnvelope(tMs, 41_000, 5_000)
        return (base + noise - passerBy).coerceIn(20f, base + 3f)
    }

    // endregion

    // region Light meter — indoor/outdoor wander + passing clouds

    /**
     * Illuminance in lux at [tMs]: ±22% wander, a mid-scale shimmer and,
     * every 47s, a cloud that multiplies the reading down smoothly to 45%.
     */
    fun lux(tMs: Long, p: SpoofParams): Float {
        val base = p.lightLux.toFloat().coerceIn(0f, 200_000f)
        val wander = base * (0.16f * osc(tMs, 11_000) + 0.06f * osc(tMs, 3_300, 0.9f))
        val cloudGate = eventEnvelope(tMs, 47_000, 6_000)
        val cloud = base * 0.55f * cloudGate * cloudGate
        return (base + wander - cloud).coerceAtLeast(0f)
    }

    // endregion

    // region Bubble level — living tilt

    /**
     * Surface tilt (degrees) at [tMs] as `x to y`. Gentle wanders within a
     * ~±3.2° envelope like a phone settling on a hand, instead of a stuck value.
     */
    fun bubbleTilt(tMs: Long): Pair<Float, Float> {
        val x = 2.6f * osc(tMs, 6_400) + 0.8f * osc(tMs, 2_100, 0.5f)
        val y = 2.2f * osc(tMs, 7_900, 1.1f) + 0.7f * osc(tMs, 2_400, 2.0f)
        return x to y
    }

    // endregion

    // region Altimeter — climbs and descents

    /** Altitude (meters) at [tMs]: slow ±65m climb/dive with a mid ripple. */
    fun altitudeM(tMs: Long, p: SpoofParams): Float {
        val base = p.altitudeM.toFloat()
        return base + 48f * osc(tMs, 63_000) + 13f * osc(tMs, 14_000, 0.7f)
    }

    /** Vertical speed (m/s) at [tMs] — analytic derivative of [altitudeM]. */
    fun altitudeRateMps(tMs: Long, p: SpoofParams): Float {
        val w1 = 2 * PI / 63_000
        val w2 = 2 * PI / 14_000
        return -(48f * w1 * sin((tMs * w1).toDouble()) +
            13f * w2 * sin((tMs * w2).toDouble() + 0.7)).toFloat()
    }

    /**
     * Consistent barometric pressure (hPa) for a given altitude — ISA-lite so
     * the pressure readout agrees with the fake altitude.
     */
    fun pressureHpa(altitudeMeters: Float): Float {
        val a = max(altitudeMeters, -500f)
        return 1013.25f * (1f - a / 44_330f).pow(5.255f)
            .coerceAtLeast(15f)
    }

    // endregion

    // region Network — jittery ping, surging throughput

    /** Latency (ms) at [tMs]: breathe around the slider plus spike waves. */
    fun pingMs(tMs: Long, p: SpoofParams): Long {
        val base = p.networkPingMs.toFloat().coerceIn(1f, 10_000f)
        val jitter = base * (0.22f * osc(tMs, 4_100) + 0.10f * osc(tMs, 1_300, 1.2f))
        val spike = base * 1.6f * eventEnvelope(tMs, 29_000, 2_400)
        return (base + jitter + spike).toLong().coerceIn(1L, 10_000L)
    }

    /** Download rate (Mbps) at [tMs]: surges and dips around the slider. */
    fun downMbps(tMs: Long, p: SpoofParams): Float {
        val base = p.networkDownMbps.coerceIn(0f, 2_000f)
        val surge = base * (0.45f * osc(tMs, 6_900) + 0.18f * osc(tMs, 2_100, 1.6f))
        val stall = -base * 0.65f * eventEnvelope(tMs, 37_000, 3_200)
        return (base + surge + stall).coerceAtLeast(0f)
    }

    /** Wi-Fi RSSI (dBm) at [tMs] around the fixture anchor. */
    fun rssi(tMs: Long, anchor: Int = -110): Int {
        return (anchor + 6f * osc(tMs, 12_000) + 3f * osc(tMs, 4_200, 0.9f)).toInt()
    }

    // endregion

    // region Steps — walking cadence

    /**
     * Steps accumulated over [elapsedMs] of open-screen walking. The cadence
     * breathes between ~1.1 and ~2.2 steps/s (analytically integrated sines, so
     * the count is exact for any sample window and monotonic).
     */
    fun addedSteps(elapsedMs: Long): Int {
        if (elapsedMs <= 0L) return 0
        val s = elapsedMs / 1000.0
        // 1.65 steps/s mean, ±0.55 modulated at ~0.05Hz exact integral (cos, no drift).
        val meanSteps = 1.65 * s
        val wobble = -0.55 * (cos(s * (2 * PI / 19.5)) - 1.0) / (2 * PI / 19.5)
        return max(0.0, meanSteps + wobble).toInt()
    }

    /** Instantaneous cadence (steps/s) matching [addedSteps]' derivative. */
    fun cadenceStepsPerSec(elapsedMs: Long): Float {
        val s = elapsedMs / 1000.0
        return (1.65 + 0.55 * sin(s * (2 * PI / 19.5))).toFloat().coerceIn(0f, 2.4f)
    }

    // endregion

    // region Battery — slow discharge

    /**
     * Battery percentage after [elapsedMs] of simulated-plugged time.
     * Discharges ~1% per 90s; charges ~1% per 60s when [p] says charging.
     */
    fun batteryLevel(elapsedMs: Long, p: SpoofParams): Int {
        val perPctMs = if (p.batteryCharging) 60_000f else 90_000f
        val delta = elapsedMs / perPctMs
        val level = p.batteryLevel + (if (p.batteryCharging) delta else -delta)
        return min(level, 100f).toInt().coerceIn(0, 100)
    }

    /** Battery temperature (°C): tiny thermal drift around the slider. */
    fun batteryTempC(elapsedMs: Long, p: SpoofParams): Float {
        return p.batteryTempC + 0.4f * osc(elapsedMs, 41_000)
    }

    // endregion
}
