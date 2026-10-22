/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Static catalog for Developer Mode: one-tap sandbox presets, crash-lab
 * cases, and the six notification channels used by the local test matrix.
 * Pure data only — the UI supplies icons and the ViewModel performs writes.
 */

package com.frerox.toolz.ui.screens.developer

import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.SpoofParams
import com.frerox.toolz.util.NotificationHelper

enum class DevPreset(
    val title: String,
    val description: String,
    val tools: Set<DevToolId>,
    val params: SpoofParams,
) {
    SHOWCASE(
        title = "Showcase",
        description = "Balanced, polished values for screenshots and walkthroughs.",
        tools = setOf(
            DevToolId.BATTERY,
            DevToolId.STEPS,
            DevToolId.NETWORK,
            DevToolId.SENSORS,
            DevToolId.MUSIC,
            DevToolId.TIMERS,
            DevToolId.DEVICE,
        ),
        params = SpoofParams(
            batteryLevel = 84,
            batteryTempC = 30.5f,
            batteryCharging = true,
            stepsToday = 8_420,
            networkPingMs = 14,
            networkDownMbps = 480f,
            networkSsid = "Studio Wi-Fi",
            compassDeg = 45,
            soundDb = 38,
            lightLux = 420,
            speedKmh = 0,
            altitudeM = 125,
            storageFullPct = 62,
            deviceModel = "Pixel 9 Pro",
            musicTrack = "Midnight Drive",
            musicArtist = "Toolz Studio",
            timerSecs = 300,
            qrPayload = "https://toolz.app",
        ),
    ),
    EDGE_CASES(
        title = "Edge cases",
        description = "Critical battery, extreme sensors and a full device.",
        tools = setOf(
            DevToolId.BATTERY,
            DevToolId.NETWORK,
            DevToolId.SENSORS,
            DevToolId.DEVICE,
            DevToolId.CLEANER,
            DevToolId.DOWNLOADER,
        ),
        params = SpoofParams(
            batteryLevel = 1,
            batteryTempC = 52f,
            batteryCharging = false,
            stepsToday = 500_000,
            networkPingMs = 9_876,
            networkDownMbps = 0.1f,
            networkSsid = "Weak-Public-WiFi",
            compassDeg = 359,
            soundDb = 158,
            lightLux = 199_000,
            speedKmh = 1_999,
            altitudeM = 12_000,
            storageFullPct = 100,
            deviceModel = "Constraint Test Device",
            musicTrack = "No Signal",
            musicArtist = "Static",
            timerSecs = 1,
            qrPayload = "EDGE-CASE-PAYLOAD",
        ),
    ),
    TRAVEL(
        title = "Travel",
        description = "Offline network, cruising altitude and a dim cabin.",
        tools = setOf(
            DevToolId.BATTERY,
            DevToolId.NETWORK,
            DevToolId.SENSORS,
            DevToolId.TIMERS,
        ),
        params = SpoofParams(
            batteryLevel = 72,
            batteryTempC = 26f,
            batteryCharging = false,
            stepsToday = 3_200,
            networkPingMs = 8_000,
            networkDownMbps = 0f,
            networkSsid = "In-Flight",
            compassDeg = 180,
            soundDb = 60,
            lightLux = 50,
            speedKmh = 860,
            altitudeM = 10_500,
            storageFullPct = 48,
            deviceModel = "Pixel 9 Pro",
            musicTrack = "Night Flight",
            musicArtist = "Ambient Air",
            timerSecs = 600,
            qrPayload = "WIFI:T:WPA;S:In-Flight;;",
        ),
    ),
    ;

    val toolCount: Int get() = tools.size
    val prefKeys: Set<String> get() = tools.mapTo(linkedSetOf()) { it.prefKey }
}

enum class DevCrashTest(
    /** Stable key stored in crash-lab history. */
    val historyType: String,
    val title: String,
    val description: String,
    val actionLabel: String,
) {
    NPE(
        historyType = "NPE",
        title = "Null pointer",
        description = "Throws a NullPointerException on the main thread.",
        actionLabel = "Throw NPE",
    ),
    ILLEGAL_STATE(
        historyType = "IllegalState",
        title = "Illegal state",
        description = "Throws an IllegalStateException on the main thread.",
        actionLabel = "Throw exception",
    ),
    ANR(
        historyType = "ANR",
        title = "Not responding",
        description = "Blocks the main thread for 10 seconds to provoke an ANR.",
        actionLabel = "Block 10s",
    ),
    OOM(
        historyType = "OOM",
        title = "Out of memory",
        description = "Allocates until the process runs out of heap.",
        actionLabel = "Exhaust heap",
    ),
}

const val DEV_NOTIFICATION_ID_START = 60_001
const val DEV_NOTIFICATION_ID_END = 60_006

/** Six representative channels, posted with stable IDs 60001–60006. */
enum class DevNotificationTest(
    val channelId: String,
    val label: String,
    val body: String,
    val id: Int,
) {
    ACTIVE(
        channelId = NotificationHelper.CHANNEL_TOOL_ACTIVE,
        label = "Active tool",
        body = "Foreground service test from Developer Mode.",
        id = DEV_NOTIFICATION_ID_START,
    ),
    ALARM(
        channelId = NotificationHelper.CHANNEL_TOOL_ALARM,
        label = "Alarm",
        body = "High-importance alarm channel test.",
        id = DEV_NOTIFICATION_ID_START + 1,
    ),
    STEPS(
        channelId = NotificationHelper.CHANNEL_STEP_COUNTER,
        label = "Step counter",
        body = "Low-importance status channel test.",
        id = DEV_NOTIFICATION_ID_START + 2,
    ),
    TASK(
        channelId = NotificationHelper.CHANNEL_TASK_REMINDERS,
        label = "Task reminder",
        body = "Reminder channel test from Developer Mode.",
        id = DEV_NOTIFICATION_ID_START + 3,
    ),
    BACKUP(
        channelId = NotificationHelper.CHANNEL_BACKUPS,
        label = "Backup",
        body = "Backup progress channel test.",
        id = DEV_NOTIFICATION_ID_START + 4,
    ),
    NEWS(
        channelId = NotificationHelper.CHANNEL_TOOLZ_NEWS,
        label = "Toolz news",
        body = "News channel test from Developer Mode.",
        id = DEV_NOTIFICATION_ID_START + 5,
    ),
    ;

    val title: String get() = "[DEV] $label"
}

fun isDevNotificationId(id: Int): Boolean = id in DEV_NOTIFICATION_ID_START..DEV_NOTIFICATION_ID_END
