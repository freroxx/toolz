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
 * Every spoofable tool. The [networkPolicy] declares what the sandbox must
 * block while the spoof is active. BLOCK_ALL means: never construct/call the
 * real network client, never hit Supabase / download API / update feed.
 *
 * Controlled-spoof contract: spoof data is RAM-only fixtures. Nothing is
 * written to AppDatabase real tables, nothing leaves the device.
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
        "Fake level, charging, temp, health. Blocks remote spec fetch.",
        SpoofNetworkPolicy.BLOCK_REMOTE_SPECS,
    ),
    DEVICE(
        "spoof_device",
        "Device info",
        "Fake model, RAM, storage-almost-full. Blocks spec API.",
        SpoofNetworkPolicy.BLOCK_REMOTE_SPECS,
    ),
    NETWORK(
        "spoof_network",
        "Network suite",
        "Fake 0.2 Mbps / 2500ms / FakeFiber SSID. Ignores real signal.",
        SpoofNetworkPolicy.SENSORS_OFF,
    ),
    STEPS(
        "spoof_steps",
        "Steps",
        "Fake 99,999 steps + 7-day history. Ignores Health Services.",
        SpoofNetworkPolicy.SENSORS_OFF,
    ),
    SENSORS(
        "spoof_sensors",
        "Sensors (compass/level/speed/alt/light/sound)",
        "Frozen gauge extremes. Unregisters real listeners.",
        SpoofNetworkPolicy.SENSORS_OFF,
    ),
    MUSIC(
        "spoof_music",
        "Now playing",
        "Fake track + synced lyrics. No media scan, no lyrics fetch.",
        SpoofNetworkPolicy.BLOCK_ALL,
    ),
    DOWNLOADER(
        "spoof_downloader",
        "Downloader",
        "Fake 2GB item stuck at 99% + fake 429. No HTTP, no WorkManager job.",
        SpoofNetworkPolicy.BLOCK_ALL,
    ),
    CLEANER(
        "spoof_cleaner",
        "Cleaner",
        "Fake 45GB junk breakdown. Delete disabled in sandbox.",
        SpoofNetworkPolicy.JOBS_PAUSED,
    ),
    NOTIFICATIONS(
        "spoof_notifications",
        "Notification vault",
        "50 in-memory fake notifications. Real Room table untouched.",
        SpoofNetworkPolicy.LOCAL_ONLY,
    ),
    CLIPBOARD(
        "spoof_clipboard",
        "Clipboard",
        "30 in-memory clips incl. OTP/links. Real clipboard DB untouched.",
        SpoofNetworkPolicy.LOCAL_ONLY,
    ),
    TASKS(
        "spoof_tasks",
        "Todo / Calendar / Notepad",
        "100 overdue todos in memory. No reminder alarms scheduled.",
        SpoofNetworkPolicy.LOCAL_ONLY,
    ),
    QR(
        "spoof_qr",
        "QR / Scanner",
        "Fake WIFI payload + fake OCR. Camera/MLKit idle.",
        SpoofNetworkPolicy.SENSORS_OFF,
    ),
    TIMERS(
        "spoof_timers",
        "Timer / Stopwatch / Pomodoro",
        "3-second timer, 999 laps preset. No real alarms.",
        SpoofNetworkPolicy.JOBS_PAUSED,
    ),
    UPDATE_NEWS(
        "spoof_update_news",
        "Update + News",
        "Fake v9.9.9 + critical news. Skips update/news network sync.",
        SpoofNetworkPolicy.BLOCK_ALL,
    ),
    WHISPER(
        "spoof_whisper",
        "Whisper chat",
        "OFFLINE SANDBOX: local contacts + thread. Supabase never touched.",
        SpoofNetworkPolicy.BLOCK_ALL,
    ),
    SEARCH(
        "spoof_search",
        "Search / Browser",
        "Canned results, ad-blocked=999. DoH + engine fetch disabled.",
        SpoofNetworkPolicy.BLOCK_ALL,
    ),
    FILES(
        "spoof_files",
        "PDF / Converter / Encrypter",
        "Fake 500-page PDF + 99% conversion. Worker never runs.",
        SpoofNetworkPolicy.JOBS_PAUSED,
    ),
    SYSTEM(
        "spoof_system",
        "Flashlight / Caffeinate / PurgeShot",
        "Fake SOS strobe + INFINITE caffeinate. No real wakelock, no file deletes.",
        SpoofNetworkPolicy.JOBS_PAUSED,
    ),
}

enum class SpoofNetworkPolicy {
    /** No network, no sensors, no jobs, no DB writes. Pure RAM fixtures. */
    BLOCK_ALL,

    /** Real DB/sensors stay, but remote spec/API fetch is skipped. */
    BLOCK_REMOTE_SPECS,

    /** Ignore OS sensor callbacks while active. */
    SENSORS_OFF,

    /** Pause WorkManager / AlarmManager side effects. */
    JOBS_PAUSED,

    /** In-memory fake list; real Room tables are never written. */
    LOCAL_ONLY,
}
