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
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.BatteryFull
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
import androidx.compose.ui.graphics.vector.ImageVector
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.SpoofParams
import java.util.Locale

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
        val subtitle: String = "",
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
            SpoofFieldSpec.FixtureSlider("Battery level", SpoofParams.BATTERY_LEVEL_MIN.toFloat(), SpoofParams.BATTERY_LEVEL_MAX.toFloat(), { it.batteryLevel.toFloat() }, { p, v -> p.copy(batteryLevel = v.toInt()) }, { "${it.toInt()}%" }),
            SpoofFieldSpec.FixtureSlider("Battery temp", SpoofParams.BATTERY_TEMP_MIN, SpoofParams.BATTERY_TEMP_MAX, { it.batteryTempC }, { p, v -> p.copy(batteryTempC = v) }, { String.format(Locale.US, "%.1f°C", it) }),
            SpoofFieldSpec.FixtureSwitch("Charging", { it.batteryCharging }, { p, v -> p.copy(batteryCharging = v) }),
        )
        DevToolId.STEPS -> listOf(
            SpoofFieldSpec.FixtureSlider("Steps today", SpoofParams.STEPS_MIN.toFloat(), SpoofParams.STEPS_MAX.toFloat(), { it.stepsToday.toFloat() }, { p, v -> p.copy(stepsToday = v.toInt()) }, { String.format(Locale.US, "%,d steps", it.toInt()) }),
        )
        DevToolId.NETWORK -> listOf(
            SpoofFieldSpec.FixtureSlider("Ping", SpoofParams.PING_MIN.toFloat(), SpoofParams.PING_MAX.toFloat(), { it.networkPingMs.toFloat() }, { p, v -> p.copy(networkPingMs = v.toInt()) }, { "${it.toInt()}ms" }),
            SpoofFieldSpec.FixtureSlider("Download", SpoofParams.DOWNLOAD_MBPS_MIN, SpoofParams.DOWNLOAD_MBPS_MAX, { it.networkDownMbps }, { p, v -> p.copy(networkDownMbps = v) }, { "%.1f Mbps".format(Locale.US, it) }),
            SpoofFieldSpec.FixtureText("SSID", { it.networkSsid }, { p, v -> p.copy(networkSsid = v) }),
        )
        DevToolId.SENSORS -> listOf(
            SpoofFieldSpec.FixtureSlider("Compass", SpoofParams.COMPASS_MIN.toFloat(), SpoofParams.COMPASS_MAX.toFloat(), { it.compassDeg.toFloat() }, { p, v -> p.copy(compassDeg = v.toInt()) }, { "${it.toInt()}°" }),
            SpoofFieldSpec.FixtureSlider("Sound", SpoofParams.SOUND_MIN.toFloat(), SpoofParams.SOUND_MAX.toFloat(), { it.soundDb.toFloat() }, { p, v -> p.copy(soundDb = v.toInt()) }, { "${it.toInt()}dB" }),
            SpoofFieldSpec.FixtureSlider("Light", SpoofParams.LIGHT_MIN.toFloat(), SpoofParams.LIGHT_MAX.toFloat(), { it.lightLux.toFloat() }, { p, v -> p.copy(lightLux = v.toInt()) }, { "${it.toInt()} lux" }),
            SpoofFieldSpec.FixtureSlider("Speed", SpoofParams.SPEED_MIN.toFloat(), SpoofParams.SPEED_MAX.toFloat(), { it.speedKmh.toFloat() }, { p, v -> p.copy(speedKmh = v.toInt()) }, { "${it.toInt()} km/h" }),
            SpoofFieldSpec.FixtureSlider("Altitude", SpoofParams.ALTITUDE_MIN.toFloat(), SpoofParams.ALTITUDE_MAX.toFloat(), { it.altitudeM.toFloat() }, { p, v -> p.copy(altitudeM = v.toInt()) }, { "${it.toInt()} m" }),
        )
        DevToolId.DEVICE -> listOf(
            SpoofFieldSpec.FixtureText("Device model", { it.deviceModel }, { p, v -> p.copy(deviceModel = v) }),
            SpoofFieldSpec.FixtureSlider("Storage full", SpoofParams.STORAGE_MIN.toFloat(), SpoofParams.STORAGE_MAX.toFloat(), { it.storageFullPct.toFloat() }, { p, v -> p.copy(storageFullPct = v.toInt()) }, { "${it.toInt()}%" }),
        )
        DevToolId.MUSIC -> listOf(
            SpoofFieldSpec.FixtureText("Track", { it.musicTrack }, { p, v -> p.copy(musicTrack = v) }, 128),
            SpoofFieldSpec.FixtureText("Artist", { it.musicArtist }, { p, v -> p.copy(musicArtist = v) }, 128),
        )
        DevToolId.CLEANER -> listOf(
            SpoofFieldSpec.FixtureSlider("Storage full", SpoofParams.STORAGE_MIN.toFloat(), SpoofParams.STORAGE_MAX.toFloat(), { it.storageFullPct.toFloat() }, { p, v -> p.copy(storageFullPct = v.toInt()) }, { "${it.toInt()}% full" }),
        )
        DevToolId.TIMERS -> listOf(
            SpoofFieldSpec.FixtureSlider("Timer", SpoofParams.TIMER_MIN.toFloat(), SpoofParams.TIMER_MAX.toFloat(), { it.timerSecs.toFloat() }, { p, v -> p.copy(timerSecs = v.toInt()) }, { formatTimerDuration(it.toInt()) }),
        )
        DevToolId.QR -> listOf(
            SpoofFieldSpec.FixtureText("QR payload", { it.qrPayload }, { p, v -> p.copy(qrPayload = v) }, 512),
        )
        else -> emptyList()
    }

/** One honest icon per tool — no more first-letter circles. */
val DevToolId.icon: ImageVector
    get() = when (this) {
        DevToolId.BATTERY -> Icons.Rounded.BatteryFull
        DevToolId.DEVICE -> Icons.Rounded.PhoneAndroid
        DevToolId.NETWORK -> Icons.Rounded.Wifi
        DevToolId.STEPS -> Icons.AutoMirrored.Rounded.DirectionsWalk
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
private fun Float.oneDecimal(): String = String.format(Locale.US, "%.1f", this)

private fun formatTimerDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3_600
    val minutes = (safe % 3_600) / 60
    val remainder = safe % 60
    return buildList {
        if (hours > 0) add("${hours}h")
        if (minutes > 0) add("${minutes}m")
        if (remainder > 0 || isEmpty()) add("${remainder}s")
    }.joinToString(" ")
}

private fun String.fixturePreview(maxLength: Int = 36): String =
    if (length <= maxLength) this else take(maxLength - 1).trimEnd() + "…"

fun spoofFixtureSummary(tool: DevToolId, p: SpoofParams): String = when (tool) {
    DevToolId.BATTERY -> "${p.batteryLevel}% • ${p.batteryTempC.oneDecimal()}°C${if (p.batteryCharging) " • charging" else ""}"
    DevToolId.DEVICE -> "${p.deviceModel.ifBlank { "Unknown model" }} • ${p.storageFullPct}% full"
    DevToolId.NETWORK -> "${p.networkSsid.ifBlank { "Hidden network" }} • ${p.networkPingMs}ms • ${p.networkDownMbps.oneDecimal()} Mbps"
    DevToolId.STEPS -> "${String.format(Locale.US, "%,d", p.stepsToday)} steps"
    DevToolId.SENSORS -> "${p.compassDeg}° • ${p.soundDb}dB • ${p.speedKmh}km/h • ${p.altitudeM}m"
    DevToolId.MUSIC -> "${p.musicTrack.ifBlank { "Untitled" }} — ${p.musicArtist.ifBlank { "Unknown artist" }}"
    DevToolId.DOWNLOADER -> "Fixture extract • 3-row history"
    DevToolId.CLEANER -> "${p.storageFullPct}% storage full • local scan fixture"
    DevToolId.NOTIFICATIONS -> "11 fixture notifications • 6 apps"
    DevToolId.CLIPBOARD -> "30 fixture clips • OTP + links"
    DevToolId.TASKS -> "6-item fixture board + calendar"
    DevToolId.QR -> p.qrPayload.ifBlank { "Empty payload" }.fixturePreview()
    DevToolId.TIMERS -> "${formatTimerDuration(p.timerSecs)} timer • stopwatch + pomodoro"
    DevToolId.UPDATE_NEWS -> "Fixture v9.9.9 + critical news"
    DevToolId.WHISPER -> "Local contacts • scripted thread"
    DevToolId.SEARCH -> "Canned results • engines offline"
    DevToolId.FILES -> "Fixture PDF/conversion • workers idle"
    DevToolId.SYSTEM -> "Torch + caffeinate fixtures • hardware off"
}

/** Grouping used by the Sandboxes tab so 18 tools read as sections. */
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
val DevToolId.openRoute: String
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
