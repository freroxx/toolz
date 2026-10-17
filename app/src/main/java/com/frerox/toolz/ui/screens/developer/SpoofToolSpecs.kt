/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * UI-side spec layer for the dev console: a real icon per tool, a live
 * fixture summary, and a data-driven list of editable fields per tool
 * (sliders / switches / text inputs bound to [SpoofParams]). One generic
 * configurator sheet renders all 18 tools from these specs — no bespoke
 * editors. Field bounds mirror the coercion in [SpoofParams.parse].
 */

package com.frerox.toolz.ui.screens.developer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.ui.graphics.vector.ImageVector
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.SpoofParams

/** A single editable fixture field of a tool's configurator. */
sealed class SpoofFieldSpec {
    abstract val label: String

    data class FixtureText(
        override val label: String,
        val get: (SpoofParams) -> String,
        val set: (SpoofParams, String) -> SpoofParams,
        val maxLength: Int = 64,
    ) : SpoofFieldSpec()

    data class FixtureSwitch(
        override val label: String,
        val get: (SpoofParams) -> Boolean,
        val set: (SpoofParams, Boolean) -> SpoofParams,
    ) : SpoofFieldSpec()

    data class FixtureSlider(
        override val label: String,
        val min: Float,
        val max: Float,
        val get: (SpoofParams) -> Float,
        val set: (SpoofParams, Float) -> SpoofParams,
        val format: (Float) -> String,
    ) : SpoofFieldSpec()
}

/** Per-tool editable fixture fields. A tool without specs still gets its fixed smart fixture. */
val DevToolId.fieldSpecs: List<SpoofFieldSpec>
    get() = when (this) {
        DevToolId.BATTERY -> listOf(
            SpoofFieldSpec.FixtureSlider("Battery level", 0f, 100f, { it.batteryLevel.toFloat() }, { p, v -> p.copy(batteryLevel = v.toInt()) }, { "${it.toInt()}%" }),
            SpoofFieldSpec.FixtureSlider("Battery temp", -10f, 65f, { it.batteryTempC }, { p, v -> p.copy(batteryTempC = v) }, { "${it}°C" }),
            SpoofFieldSpec.FixtureSwitch("Charging", { it.batteryCharging }, { p, v -> p.copy(batteryCharging = v) }),
        )
        DevToolId.STEPS -> listOf(
            SpoofFieldSpec.FixtureSlider("Steps today", 0f, 200000f, { it.stepsToday.toFloat() }, { p, v -> p.copy(stepsToday = v.toInt()) }, { "${it.toInt()} steps" }),
        )
        DevToolId.NETWORK -> listOf(
            SpoofFieldSpec.FixtureSlider("Ping", 0f, 10000f, { it.networkPingMs.toFloat() }, { p, v -> p.copy(networkPingMs = v.toInt()) }, { "${it.toInt()}ms" }),
            SpoofFieldSpec.FixtureSlider("Download", 0f, 2000f, { it.networkDownMbps }, { p, v -> p.copy(networkDownMbps = v) }, { "%.1f Mbps".format(it) }),
            SpoofFieldSpec.FixtureText("Fake SSID", { it.networkSsid }, { p, v -> p.copy(networkSsid = v) }),
        )
        DevToolId.SENSORS -> listOf(
            SpoofFieldSpec.FixtureSlider("Compass", 0f, 359f, { it.compassDeg.toFloat() }, { p, v -> p.copy(compassDeg = v.toInt()) }, { "${it.toInt()}°" }),
            SpoofFieldSpec.FixtureSlider("Sound", 0f, 160f, { it.soundDb.toFloat() }, { p, v -> p.copy(soundDb = v.toInt()) }, { "${it.toInt()}dB" }),
            SpoofFieldSpec.FixtureSlider("Light", 0f, 200000f, { it.lightLux.toFloat() }, { p, v -> p.copy(lightLux = v.toInt()) }, { "${it.toInt()} lux" }),
            SpoofFieldSpec.FixtureSlider("Speed", 0f, 2000f, { it.speedKmh.toFloat() }, { p, v -> p.copy(speedKmh = v.toInt()) }, { "${it.toInt()} km/h" }),
            SpoofFieldSpec.FixtureSlider("Altitude", -500f, 12000f, { it.altitudeM.toFloat() }, { p, v -> p.copy(altitudeM = v.toInt()) }, { "${it.toInt()} m" }),
        )
        DevToolId.DEVICE -> listOf(
            SpoofFieldSpec.FixtureText("Fake model", { it.deviceModel }, { p, v -> p.copy(deviceModel = v) }),
            SpoofFieldSpec.FixtureSlider("Storage full", 0f, 100f, { it.storageFullPct.toFloat() }, { p, v -> p.copy(storageFullPct = v.toInt()) }, { "${it.toInt()}%" }),
        )
        DevToolId.MUSIC -> listOf(
            SpoofFieldSpec.FixtureText("Track", { it.musicTrack }, { p, v -> p.copy(musicTrack = v) }, 128),
            SpoofFieldSpec.FixtureText("Artist", { it.musicArtist }, { p, v -> p.copy(musicArtist = v) }, 128),
        )
        DevToolId.DOWNLOADER -> listOf(
            SpoofFieldSpec.FixtureSlider("Progress", 0f, 100f, { it.downloadProgress.toFloat() }, { p, v -> p.copy(downloadProgress = v.toInt()) }, { "${it.toInt()}%" }),
        )
        DevToolId.CLEANER -> listOf(
            SpoofFieldSpec.FixtureSlider("Junk full", 0f, 100f, { it.storageFullPct.toFloat() }, { p, v -> p.copy(storageFullPct = v.toInt()) }, { "${it.toInt()}% full" }),
        )
        DevToolId.TIMERS -> listOf(
            SpoofFieldSpec.FixtureSlider("Fake timer", 1f, 3600f, { it.timerSecs.toFloat() }, { p, v -> p.copy(timerSecs = v.toInt()) }, { "${it.toInt()}s" }),
        )
        DevToolId.QR -> listOf(
            SpoofFieldSpec.FixtureText("Fake payload", { it.qrPayload }, { p, v -> p.copy(qrPayload = v) }, 512),
        )
        else -> emptyList()
    }

/** One honest icon per tool — no more first-letter circles. */
val DevToolId.icon: ImageVector
    get() = when (this) {
        DevToolId.BATTERY -> Icons.Rounded.BatteryFull
        DevToolId.DEVICE -> Icons.Rounded.PhoneAndroid
        DevToolId.NETWORK -> Icons.Rounded.Wifi
        DevToolId.STEPS -> Icons.Rounded.DirectionsWalk
        DevToolId.SENSORS -> Icons.Rounded.Speed
        DevToolId.MUSIC -> Icons.Rounded.MusicNote
        DevToolId.DOWNLOADER -> Icons.Rounded.Download
        DevToolId.CLEANER -> Icons.Rounded.CleaningServices
        DevToolId.NOTIFICATIONS -> Icons.Rounded.NotificationsActive
        DevToolId.CLIPBOARD -> Icons.Rounded.ContentPaste
        DevToolId.TASKS -> Icons.Rounded.Checklist
        DevToolId.QR -> Icons.Rounded.QrCode2
        DevToolId.TIMERS -> Icons.Rounded.Timer
        DevToolId.UPDATE_NEWS -> Icons.Rounded.Campaign
        DevToolId.WHISPER -> Icons.Rounded.Forum
        DevToolId.SEARCH -> Icons.Rounded.Search
        DevToolId.FILES -> Icons.Rounded.PictureAsPdf
        DevToolId.SYSTEM -> Icons.Rounded.FlashlightOn
    }

/** Short offline summary of the fixture this tool's sandbox shows. */
fun spoofFixtureSummary(tool: DevToolId, p: SpoofParams): String = when (tool) {
    DevToolId.BATTERY -> "${p.batteryLevel}% • ${p.batteryTempC}°C${if (p.batteryCharging) " • charging" else ""}"
    DevToolId.DEVICE -> "${p.deviceModel} • ${p.storageFullPct}% full"
    DevToolId.NETWORK -> "${p.networkSsid} • ${p.networkPingMs}ms • %.1f Mbps".format(p.networkDownMbps)
    DevToolId.STEPS -> "${p.stepsToday} steps"
    DevToolId.SENSORS -> "${p.compassDeg}° • ${p.soundDb}dB • ${p.speedKmh}km/h • ${p.altitudeM}m"
    DevToolId.MUSIC -> "${p.musicTrack} — ${p.musicArtist}"
    DevToolId.DOWNLOADER -> "${p.downloadProgress}% (stuck) + fake 429"
    DevToolId.CLEANER -> "${p.storageFullPct}% junk • delete disabled"
    DevToolId.NOTIFICATIONS -> "50 in-memory fake notifications"
    DevToolId.CLIPBOARD -> "30 in-memory clips incl. OTP"
    DevToolId.TASKS -> "100 overdue todos in memory"
    DevToolId.QR -> p.qrPayload.take(32)
    DevToolId.TIMERS -> "${p.timerSecs}s timer • 999 laps preset"
    DevToolId.UPDATE_NEWS -> "fake v9.9.9 + critical news"
    DevToolId.WHISPER -> "offline thread • no Supabase"
    DevToolId.SEARCH -> "canned results • ads blocked 999"
    DevToolId.FILES -> "fake 500-page PDF • no worker"
    DevToolId.SYSTEM -> "fake SOS strobe • wakelock blocked"
}

/** Grouping used by the Spoofs tab so 18 tiles read as sections. */
enum class DevToolGroup(val title: String) {
    HARDWARE("Hardware & sensors"),
    SYSTEM_LIKE("System & device"),
    NETWORK("Network & content"),
    APP_MACHINERY("App machinery"),
}

val DevToolId.group: DevToolGroup
    get() = when (this) {
        DevToolId.BATTERY, DevToolId.DEVICE, DevToolId.SENSORS, DevToolId.STEPS -> DevToolGroup.HARDWARE
        DevToolId.NETWORK, DevToolId.SEARCH, DevToolId.UPDATE_NEWS -> DevToolGroup.NETWORK
        DevToolId.CLIPBOARD, DevToolId.NOTIFICATIONS, DevToolId.TASKS, DevToolId.QR,
        DevToolId.TIMERS, DevToolId.FILES -> DevToolGroup.APP_MACHINERY
        DevToolId.MUSIC, DevToolId.DOWNLOADER, DevToolId.CLEANER, DevToolId.WHISPER,
        DevToolId.SYSTEM -> DevToolGroup.SYSTEM_LIKE
    }

/** Which primary screen the configurator's "Open tool" button navigates to. */
val DevToolId.openRoute: String?
    get() = when (this) {
        DevToolId.BATTERY -> "battery_info"
        DevToolId.DEVICE -> "device_info"
        DevToolId.NETWORK -> "network_power_suite"
        DevToolId.STEPS -> "step_counter"
        DevToolId.SENSORS -> "compass"
        DevToolId.MUSIC -> "music_player"
        DevToolId.DOWNLOADER -> "media_downloader"
        DevToolId.CLEANER -> "file_cleaner"
        DevToolId.NOTIFICATIONS -> "notification_vault"
        DevToolId.CLIPBOARD -> "clipboard"
        DevToolId.TASKS -> "todo"
        DevToolId.QR -> "qr_generator"
        DevToolId.TIMERS -> "timer"
        DevToolId.UPDATE_NEWS -> "update"
        DevToolId.WHISPER -> "whisper"
        DevToolId.SEARCH -> "search"
        DevToolId.FILES -> "file_converter"
        DevToolId.SYSTEM -> "flashlight"
    }
