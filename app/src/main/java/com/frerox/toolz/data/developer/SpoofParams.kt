/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Structured spoof parameters. Persisted as a single JSON string
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
    val stepsToday: Int = 99999,
    val networkPingMs: Int = 2500,
    val networkDownMbps: Float = 0.2f,
    val networkSsid: String = "FakeFiber-5G",
    val compassDeg: Int = 273,
    val soundDb: Int = 120,
    val lightLux: Int = 100000,
    val speedKmh: Int = 320,
    val altitudeM: Int = 8848,
    val storageFullPct: Int = 99,
    val deviceModel: String = "Pixel 9 Pro",
    val musicTrack: String = "Debug Symphony",
    val musicArtist: String = "Null Pointer",
    val timerSecs: Int = 3,
    val qrPayload: String = "WIFI:T:WPA;S:FakeNet;;",
    val downloadProgress: Int = 99,
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
        .put("downloadProgress", downloadProgress)
        .toString()

    companion object {
        fun parse(raw: String?): SpoofParams {
            if (raw.isNullOrBlank()) return SpoofParams()
            return try {
                val o = JSONObject(raw)
                SpoofParams(
                    batteryLevel = o.optInt("batteryLevel", 7).coerceIn(0, 100),
                    batteryTempC = o.optDouble("batteryTempC", 47.5).toFloat().coerceIn(-20f, 80f),
                    batteryCharging = o.optBoolean("batteryCharging", false),
                    stepsToday = o.optInt("stepsToday", 99999).coerceIn(0, 500000),
                    networkPingMs = o.optInt("networkPingMs", 2500).coerceIn(0, 10000),
                    networkDownMbps = o.optDouble("networkDownMbps", 0.2).toFloat().coerceIn(0f, 2000f),
                    networkSsid = o.optString("networkSsid", "FakeFiber-5G").take(64),
                    compassDeg = o.optInt("compassDeg", 273).coerceIn(0, 359),
                    soundDb = o.optInt("soundDb", 120).coerceIn(0, 160),
                    lightLux = o.optInt("lightLux", 100000).coerceIn(0, 200000),
                    speedKmh = o.optInt("speedKmh", 320).coerceIn(0, 2000),
                    altitudeM = o.optInt("altitudeM", 8848).coerceIn(-500, 12000),
                    storageFullPct = o.optInt("storageFullPct", 99).coerceIn(0, 100),
                    deviceModel = o.optString("deviceModel", "Pixel 9 Pro").take(64),
                    musicTrack = o.optString("musicTrack", "Debug Symphony").take(128),
                    musicArtist = o.optString("musicArtist", "Null Pointer").take(128),
                    timerSecs = o.optInt("timerSecs", 3).coerceIn(1, 3600),
                    qrPayload = o.optString("qrPayload", "WIFI:T:WPA;S:FakeNet;;").take(512),
                    downloadProgress = o.optInt("downloadProgress", 99).coerceIn(0, 100),
                )
            } catch (_: Exception) {
                SpoofParams()
            }
        }
    }
}
