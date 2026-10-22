/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Structured sandbox parameters. Persisted as a single JSON string
 * (SPOOF_PARAMS_JSON) so sliders survive process death, while the
 * fixtures themselves stay RAM-only. org.json ships with Android —
 * no new dependency.
 */

package com.frerox.toolz.data.developer

import org.json.JSONObject

data class SpoofParams(
    val batteryLevel: Int = 7,
    val batteryTempC: Float = 47.5f,
    val batteryCharging: Boolean = false,
    val stepsToday: Int = 99_999,
    val networkPingMs: Int = 2_500,
    val networkDownMbps: Float = 0.2f,
    val networkSsid: String = "FakeFiber-5G",
    val compassDeg: Int = 273,
    val soundDb: Int = 120,
    val lightLux: Int = 100_000,
    val speedKmh: Int = 320,
    val altitudeM: Int = 8_848,
    val storageFullPct: Int = 99,
    val deviceModel: String = "Pixel 9 Pro",
    val musicTrack: String = "Debug Symphony",
    val musicArtist: String = "Null Pointer",
    val timerSecs: Int = 3,
    val qrPayload: String = "WIFI:T:WPA;S:FakeNet;;",
) {
    fun toJson(): String = JSONObject()
        .put("batteryLevel", batteryLevel)
        .put("batteryTempC", batteryTempC)
        .put("batteryCharging", batteryCharging)
        .put("stepsToday", stepsToday)
        .put("networkPingMs", networkPingMs)
        .put("networkDownMbps", networkDownMbps)
        .put("networkSsid", networkSsid)
        .put("compassDeg", compassDeg)
        .put("soundDb", soundDb)
        .put("lightLux", lightLux)
        .put("speedKmh", speedKmh)
        .put("altitudeM", altitudeM)
        .put("storageFullPct", storageFullPct)
        .put("deviceModel", deviceModel)
        .put("musicTrack", musicTrack)
        .put("musicArtist", musicArtist)
        .put("timerSecs", timerSecs)
        .put("qrPayload", qrPayload)
        .toString()

    companion object {
        const val BATTERY_LEVEL_MIN = 0
        const val BATTERY_LEVEL_MAX = 100
        const val BATTERY_TEMP_MIN = -20f
        const val BATTERY_TEMP_MAX = 80f
        const val STEPS_MIN = 0
        const val STEPS_MAX = 500_000
        const val PING_MIN = 0
        const val PING_MAX = 10_000
        const val DOWNLOAD_MBPS_MIN = 0f
        const val DOWNLOAD_MBPS_MAX = 2_000f
        const val COMPASS_MIN = 0
        const val COMPASS_MAX = 359
        const val SOUND_MIN = 0
        const val SOUND_MAX = 160
        const val LIGHT_MIN = 0
        const val LIGHT_MAX = 200_000
        const val SPEED_MIN = 0
        const val SPEED_MAX = 2_000
        const val ALTITUDE_MIN = -500
        const val ALTITUDE_MAX = 12_000
        const val STORAGE_MIN = 0
        const val STORAGE_MAX = 100
        const val TIMER_MIN = 1
        const val TIMER_MAX = 3_600

        fun parse(raw: String?): SpoofParams {
            if (raw.isNullOrBlank()) return SpoofParams()
            return try {
                val o = JSONObject(raw)
                SpoofParams(
                    batteryLevel = o.optInt("batteryLevel", 7)
                        .coerceIn(BATTERY_LEVEL_MIN, BATTERY_LEVEL_MAX),
                    batteryTempC = finiteOr(
                        fallback = 47.5f,
                        value = o.optDouble("batteryTempC", 47.5).toFloat(),
                        min = BATTERY_TEMP_MIN,
                        max = BATTERY_TEMP_MAX,
                    ),
                    batteryCharging = o.optBoolean("batteryCharging", false),
                    stepsToday = o.optInt("stepsToday", 99_999).coerceIn(STEPS_MIN, STEPS_MAX),
                    networkPingMs = o.optInt("networkPingMs", 2_500).coerceIn(PING_MIN, PING_MAX),
                    networkDownMbps = finiteOr(
                        fallback = 0.2f,
                        value = o.optDouble("networkDownMbps", 0.2).toFloat(),
                        min = DOWNLOAD_MBPS_MIN,
                        max = DOWNLOAD_MBPS_MAX,
                    ),
                    networkSsid = o.optString("networkSsid", "FakeFiber-5G").take(64),
                    compassDeg = o.optInt("compassDeg", 273).coerceIn(COMPASS_MIN, COMPASS_MAX),
                    soundDb = o.optInt("soundDb", 120).coerceIn(SOUND_MIN, SOUND_MAX),
                    lightLux = o.optInt("lightLux", 100_000).coerceIn(LIGHT_MIN, LIGHT_MAX),
                    speedKmh = o.optInt("speedKmh", 320).coerceIn(SPEED_MIN, SPEED_MAX),
                    altitudeM = o.optInt("altitudeM", 8_848).coerceIn(ALTITUDE_MIN, ALTITUDE_MAX),
                    storageFullPct = o.optInt("storageFullPct", 99)
                        .coerceIn(STORAGE_MIN, STORAGE_MAX),
                    deviceModel = o.optString("deviceModel", "Pixel 9 Pro").take(64),
                    musicTrack = o.optString("musicTrack", "Debug Symphony").take(128),
                    musicArtist = o.optString("musicArtist", "Null Pointer").take(128),
                    timerSecs = o.optInt("timerSecs", 3).coerceIn(TIMER_MIN, TIMER_MAX),
                    qrPayload = o.optString("qrPayload", "WIFI:T:WPA;S:FakeNet;;").take(512),
                )
            } catch (_: Exception) {
                SpoofParams()
            }
        }

        private fun finiteOr(fallback: Float, value: Float, min: Float, max: Float): Float =
            if (value.isFinite()) value.coerceIn(min, max) else fallback
    }
}
