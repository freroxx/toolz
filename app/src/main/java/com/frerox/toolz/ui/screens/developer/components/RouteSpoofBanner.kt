/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Global spoof watermark overlay. Mounted once around the NavHost: whenever
 * the current route belongs to a sandboxed tool, a banner floats at the top.
 * Central placement means new screens can never forget their watermark —
 * the route→tool map below is the single source of truth.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.ui.screens.developer.SpoofStateViewModel

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
 * Wraps app content and floats a sandbox banner whenever [currentRoute]'s
 * tool is actively spoofed. Tap opens the dev console.
 */
@Composable
fun SpoofWatermarkOverlay(
    currentRoute: String?,
    onOpenControls: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content()
        val tool = routeToDevTool(currentRoute)
        if (tool != null) {
            val vm: SpoofStateViewModel = hiltViewModel()
            val sandboxed by vm.isSandbox(tool).collectAsState(initial = false)
            if (sandboxed) {
                SpoofBanner(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    text = "${tool.title.uppercase()} SANDBOX — fake local data",
                    onOpenControls = onOpenControls,
                )
            }
        }
    }
}

/**
 * NavController overload: observes the back stack itself. Mount once around
 * the app [androidx.navigation.NavHost] and every spoofed tool is watermarked.
 */
@Composable
fun SpoofWatermarkOverlay(
    navController: androidx.navigation.NavHostController,
    onOpenControls: () -> Unit,
    content: @Composable () -> Unit,
) {
    val entry by navController.currentBackStackEntryAsState()
    SpoofWatermarkOverlay(
        currentRoute = entry?.destination?.route,
        onOpenControls = onOpenControls,
        content = content,
    )
}

/**
 * Per-screen host for tool ViewModels that want the banner inline instead of
 * floating. One line in the screen, no DataStore access needed.
 */
@Composable
fun SandboxWatermarkHost(
    tool: DevToolId,
    onOpenControls: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val vm: SpoofStateViewModel = hiltViewModel()
    val sandboxed by vm.isSandbox(tool).collectAsState(initial = false)
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        if (sandboxed) {
            SpoofBanner(
                text = "${tool.title.uppercase()} SANDBOX — fake local data",
                onOpenControls = onOpenControls,
            )
        }
        content()
    }
}
