/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Single source of truth for the route → sandboxed-tool map. The console's
 * screen launcher, the dashboard star, and the long-press dialog all use it.
 * On-screen sandbox watermarks were removed: fixture data is intentionally silent.
 */

package com.frerox.toolz.ui.screens.developer.components

import com.frerox.toolz.data.developer.DevToolId

/** Maps a nav route (with optional args) to its sandboxed tool, or null. */
fun routeToDevTool(route: String?): DevToolId? {
    val base = route?.substringBefore("?")?.substringBefore("/") ?: return null
    return when (base) {
        "battery_info" -> DevToolId.BATTERY
        "device_info" -> DevToolId.DEVICE
        "network_power_suite", "wifi_tweaks" -> DevToolId.NETWORK
        "step_counter", "step_trends" -> DevToolId.STEPS
        "compass", "bubble_level", "speedometer", "altimeter",
        "light_meter", "sound_meter" -> DevToolId.SENSORS
        "music_player" -> DevToolId.MUSIC
        "media_downloader" -> DevToolId.DOWNLOADER
        "file_cleaner" -> DevToolId.CLEANER
        "notification_vault" -> DevToolId.NOTIFICATIONS
        "clipboard" -> DevToolId.CLIPBOARD
        "todo", "calendar", "notepad" -> DevToolId.TASKS
        "qr_generator", "scanner" -> DevToolId.QR
        "timer", "stopwatch", "pomodoro" -> DevToolId.TIMERS
        "update", "toolz_news" -> DevToolId.UPDATE_NEWS
        "whisper", "whisper_chat", "whisper_group_chat",
        "whisper_group_info", "whisper_group_permissions",
        "whisper_profile" -> DevToolId.WHISPER
        "search", "browser", "tab_management",
        "site_permissions", "ad_block_settings", "next_dns_setup" -> DevToolId.SEARCH
        "file_converter", "pdf_reader", "smart_encrypter",
        "background_remover" -> DevToolId.FILES
        "flashlight", "screen_light", "caffeinate", "purge_shot" -> DevToolId.SYSTEM
        else -> null
    }
}
