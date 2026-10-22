/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.developer

import com.frerox.toolz.data.developer.SpoofParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofParamsTest {

    @Test
    fun `round trip keeps every fixture value`() {
        val params = SpoofParams(
            batteryLevel = 42,
            batteryTempC = 36.5f,
            batteryCharging = true,
            stepsToday = 12_345,
            networkPingMs = 87,
            networkDownMbps = 12.5f,
            networkSsid = "Test Wi-Fi",
            compassDeg = 12,
            soundDb = 65,
            lightLux = 900,
            speedKmh = 55,
            altitudeM = 430,
            storageFullPct = 73,
            deviceModel = "Fixture Phone",
            musicTrack = "Test Track",
            musicArtist = "Test Artist",
            timerSecs = 90,
            qrPayload = "https://example.test/fixture",
        )

        assertEquals(params, SpoofParams.parse(params.toJson()))
    }

    @Test
    fun `parse clamps numeric values and caps text`() {
        val raw = """
            {
              "batteryLevel": 999,
              "batteryTempC": 999,
              "stepsToday": -4,
              "networkPingMs": 999999,
              "networkDownMbps": 999999,
              "compassDeg": -1,
              "soundDb": 999,
              "lightLux": -20,
              "speedKmh": 99999,
              "altitudeM": -99999,
              "storageFullPct": 300,
              "timerSecs": 0,
              "networkSsid": "${"x".repeat(120)}",
              "musicTrack": "${"y".repeat(200)}",
              "qrPayload": "${"z".repeat(700)}"
            }
        """.trimIndent()

        val parsed = SpoofParams.parse(raw)

        assertEquals(SpoofParams.BATTERY_LEVEL_MAX, parsed.batteryLevel)
        assertEquals(SpoofParams.BATTERY_TEMP_MAX, parsed.batteryTempC)
        assertEquals(SpoofParams.STEPS_MIN, parsed.stepsToday)
        assertEquals(SpoofParams.PING_MAX, parsed.networkPingMs)
        assertEquals(SpoofParams.DOWNLOAD_MBPS_MAX, parsed.networkDownMbps)
        assertEquals(SpoofParams.COMPASS_MIN, parsed.compassDeg)
        assertEquals(SpoofParams.SOUND_MAX, parsed.soundDb)
        assertEquals(SpoofParams.LIGHT_MIN, parsed.lightLux)
        assertEquals(SpoofParams.SPEED_MAX, parsed.speedKmh)
        assertEquals(SpoofParams.ALTITUDE_MIN, parsed.altitudeM)
        assertEquals(SpoofParams.STORAGE_MAX, parsed.storageFullPct)
        assertEquals(SpoofParams.TIMER_MIN, parsed.timerSecs)
        assertEquals(64, parsed.networkSsid.length)
        assertEquals(128, parsed.musicTrack.length)
        assertEquals(512, parsed.qrPayload.length)
    }

    @Test
    fun `parse falls back for malformed and non-finite input`() {
        assertEquals(SpoofParams(), SpoofParams.parse("not json"))
        assertEquals(SpoofParams(), SpoofParams.parse(null))
        assertEquals(SpoofParams(), SpoofParams.parse("   "))

        val nonFinite = SpoofParams.parse("""{"batteryTempC":1e999,"networkDownMbps":1e999}""")
        assertEquals(47.5f, nonFinite.batteryTempC)
        assertEquals(0.2f, nonFinite.networkDownMbps)
    }

    @Test
    fun `defaults stay inside every documented bound`() {
        val defaults = SpoofParams()
        assertTrue(defaults.batteryLevel in SpoofParams.BATTERY_LEVEL_MIN..SpoofParams.BATTERY_LEVEL_MAX)
        assertTrue(defaults.batteryTempC in SpoofParams.BATTERY_TEMP_MIN..SpoofParams.BATTERY_TEMP_MAX)
        assertTrue(defaults.stepsToday in SpoofParams.STEPS_MIN..SpoofParams.STEPS_MAX)
        assertTrue(defaults.networkPingMs in SpoofParams.PING_MIN..SpoofParams.PING_MAX)
        assertTrue(defaults.networkDownMbps in SpoofParams.DOWNLOAD_MBPS_MIN..SpoofParams.DOWNLOAD_MBPS_MAX)
        assertTrue(defaults.compassDeg in SpoofParams.COMPASS_MIN..SpoofParams.COMPASS_MAX)
        assertTrue(defaults.soundDb in SpoofParams.SOUND_MIN..SpoofParams.SOUND_MAX)
        assertTrue(defaults.lightLux in SpoofParams.LIGHT_MIN..SpoofParams.LIGHT_MAX)
        assertTrue(defaults.speedKmh in SpoofParams.SPEED_MIN..SpoofParams.SPEED_MAX)
        assertTrue(defaults.altitudeM in SpoofParams.ALTITUDE_MIN..SpoofParams.ALTITUDE_MAX)
        assertTrue(defaults.storageFullPct in SpoofParams.STORAGE_MIN..SpoofParams.STORAGE_MAX)
        assertTrue(defaults.timerSecs in SpoofParams.TIMER_MIN..SpoofParams.TIMER_MAX)
    }
}
