/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Route → demo-tool mapping + (formerly) the watermark overlay. WATERMARKS ARE
 * GONE: demo mode is silent by design — the fake data IS the giveaway, and a
 * banner on every screen defeats the purpose of a realistic showcase. The
 * routeToDevTool map below stays: the dashboard star logic and console reuse
 * it as the single source of truth.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.runtime.Composable
import com.frerox.toolz.data.developer.DevToolId

/** Maps a nav route (without args) to its spoofable tool, or null. */
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
        "whisper_group_info", "whisper_profile" -> DevToolId.WHISPER
        "search", "browser", "tab_management",
        "site_permissions", "ad_block_settings", "next_dns_setup" -> DevToolId.SEARCH
        "file_converter", "pdf_reader", "smart_encrypter",
        "background_remover" -> DevToolId.FILES
        "flashlight", "screen_light", "caffeinate", "purge_shot" -> DevToolId.SYSTEM
        else -> null
    }
}

/**
 * Kept for call-site compatibility (mounted around the NavHost and per-screen
 * hosts) but renders CONTENT ONLY — no banner, no overlay, nothing appears on
 * tool screens while demo mode is armed.
 */
@Composable
fun SpoofWatermarkOverlay(
    currentRoute: String?,
    onOpenControls: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    content()
}

/** NavController overload: observes the back stack itself. */
@Composable
fun SpoofWatermarkOverlay(
    navController: androidx.navigation.NavHostController,
    onOpenControls: () -> Unit,
    content: @Composable () -> Unit,
) {
    content()
}

/** Per-screen host variant — content only, no banner. */
@Composable
fun SandboxWatermarkHost(
    tool: DevToolId,
    onOpenControls: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    content()
}
