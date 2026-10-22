/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.data.developer

/**
 * Every sandboxable tool. The [networkPolicy] declares what the sandbox must
 * block while active. BLOCK_ALL means: never construct/call the real network
 * client, never hit Supabase / download API / update feed.
 *
 * Sandbox contract: fixture data is RAM-only. Nothing is written to real
 * AppDatabase tables, and nothing leaves the device.
 */
enum class DevToolId(
    val prefKey: String,
    val title: String,
    val subtitle: String,
    val networkPolicy: SpoofNetworkPolicy,
) {
    BATTERY(
        "spoof_battery",
        "Battery",
        "Sandboxed level, charging, temperature and health. Remote spec fetch is skipped.",
        SpoofNetworkPolicy.BLOCK_REMOTE_SPECS,
    ),
    DEVICE(
        "spoof_device",
        "Device info",
        "Sandboxed model, RAM and almost-full storage. The spec API is skipped.",
        SpoofNetworkPolicy.BLOCK_REMOTE_SPECS,
    ),
    NETWORK(
        "spoof_network",
        "Network suite",
        "Fixture SSID, ping and download speed. Real radios stay untouched.",
        SpoofNetworkPolicy.SENSORS_OFF,
    ),
    STEPS(
        "spoof_steps",
        "Steps",
        "Fixture step count with 7-day history. Health Services stays untouched.",
        SpoofNetworkPolicy.SENSORS_OFF,
    ),
    SENSORS(
        "spoof_sensors",
        "Sensors (compass/level/speed/alt/light/sound)",
        "Frozen gauge extremes. Real sensor listeners stay idle.",
        SpoofNetworkPolicy.SENSORS_OFF,
    ),
    MUSIC(
        "spoof_music",
        "Now playing",
        "Fixture track and lyrics. No media scan and no lyrics fetch.",
        SpoofNetworkPolicy.BLOCK_ALL,
    ),
    DOWNLOADER(
        "spoof_downloader",
        "Downloader",
        "Fixture history and a local extract. No HTTP and no WorkManager job.",
        SpoofNetworkPolicy.BLOCK_ALL,
    ),
    CLEANER(
        "spoof_cleaner",
        "Cleaner",
        "Fixture junk scan. Cleaned items go to RAM trash only.",
        SpoofNetworkPolicy.JOBS_PAUSED,
    ),
    NOTIFICATIONS(
        "spoof_notifications",
        "Notification vault",
        "11 fixture notifications. The real Room table stays untouched.",
        SpoofNetworkPolicy.LOCAL_ONLY,
    ),
    CLIPBOARD(
        "spoof_clipboard",
        "Clipboard",
        "30 fixture clips with OTP and links. The real clipboard stays untouched.",
        SpoofNetworkPolicy.LOCAL_ONLY,
    ),
    TASKS(
        "spoof_tasks",
        "Todo / Calendar / Notepad",
        "Fixture todo board and calendar. No reminder alarms are scheduled.",
        SpoofNetworkPolicy.LOCAL_ONLY,
    ),
    QR(
        "spoof_qr",
        "QR / Scanner",
        "Fixture Wi-Fi payload and OCR. Camera and ML Kit stay idle.",
        SpoofNetworkPolicy.SENSORS_OFF,
    ),
    TIMERS(
        "spoof_timers",
        "Timer / Stopwatch / Pomodoro",
        "Fixture timer and stopwatch. No real alarms.",
        SpoofNetworkPolicy.JOBS_PAUSED,
    ),
    UPDATE_NEWS(
        "spoof_update_news",
        "Update + News",
        "Fixture version and critical news. Update and news sync are skipped.",
        SpoofNetworkPolicy.BLOCK_ALL,
    ),
    WHISPER(
        "spoof_whisper",
        "Whisper chat",
        "Local contacts + scripted thread. Supabase never touched.",
        SpoofNetworkPolicy.BLOCK_ALL,
    ),
    SEARCH(
        "spoof_search",
        "Search / Browser",
        "Canned results. Network engines and DoH disabled.",
        SpoofNetworkPolicy.BLOCK_ALL,
    ),
    FILES(
        "spoof_files",
        "PDF / Converter / Encrypter",
        "Fixture documents and progress. File workers never start.",
        SpoofNetworkPolicy.JOBS_PAUSED,
    ),
    SYSTEM(
        "spoof_system",
        "Flashlight / Caffeinate / PurgeShot",
        "Fixture torch and caffeinate. No real wakelock or file deletion.",
        SpoofNetworkPolicy.JOBS_PAUSED,
    ),
}

enum class SpoofNetworkPolicy(val label: String) {
    /** No network, no sensors, no jobs, no DB writes. Pure RAM fixtures. */
    BLOCK_ALL("Offline"),

    /** Real DB/sensors stay, but remote spec/API fetch is skipped. */
    BLOCK_REMOTE_SPECS("No remote specs"),

    /** Ignore OS sensor callbacks while active. */
    SENSORS_OFF("Sensors idle"),

    /** Pause WorkManager / AlarmManager side effects. */
    JOBS_PAUSED("Jobs paused"),

    /** In-memory fake list; real Room tables are never written. */
    LOCAL_ONLY("Local only"),
}
