/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Searchable route launcher for Developer Mode. Only routes without required
 * arguments are listed so every tap is guaranteed to resolve.
 */

package com.frerox.toolz.ui.screens.developer

import com.frerox.toolz.ui.navigation.Screen

enum class DevRouteCategory(val label: String) {
    TOOLS("Tools"),
    SYSTEM("App & system"),
}

data class DevRoute(
    val label: String,
    val route: String,
    val category: DevRouteCategory,
)

val devRoutes: List<DevRoute> = listOf(
    DevRoute("Dashboard", Screen.Dashboard.route, DevRouteCategory.SYSTEM),
    DevRoute("Settings", Screen.Settings.route, DevRouteCategory.SYSTEM),
    DevRoute("App skin", Screen.AppSkin.route, DevRouteCategory.SYSTEM),
    DevRoute("Tool shortcuts", Screen.ToolShortcuts.route, DevRouteCategory.SYSTEM),
    DevRoute("Backup & restore", Screen.BackupRestore.createRoute(), DevRouteCategory.SYSTEM),
    DevRoute("Living widgets", Screen.LivingWidgets.route, DevRouteCategory.SYSTEM),
    DevRoute("Updates", Screen.Update.route, DevRouteCategory.SYSTEM),
    DevRoute("Toolz news", Screen.ToolzNews.route, DevRouteCategory.SYSTEM),
    DevRoute("Site permissions", Screen.SitePermissions.route, DevRouteCategory.SYSTEM),
    DevRoute("Ad blocking", Screen.AdBlockConfig.route, DevRouteCategory.SYSTEM),
    DevRoute("Tab management", Screen.TabManagement.route, DevRouteCategory.SYSTEM),
    DevRoute("Battery info", Screen.BatteryInfo.route, DevRouteCategory.TOOLS),
    DevRoute("Device info", Screen.DeviceInfo.route, DevRouteCategory.TOOLS),
    DevRoute("Network suite", Screen.NetworkPowerSuite.route, DevRouteCategory.TOOLS),
    DevRoute("Wi-Fi tweaks", Screen.WifiTweaks.route, DevRouteCategory.TOOLS),
    DevRoute("Step counter", Screen.StepCounter.route, DevRouteCategory.TOOLS),
    DevRoute("Compass", Screen.Compass.route, DevRouteCategory.TOOLS),
    DevRoute("Bubble level", Screen.BubbleLevel.route, DevRouteCategory.TOOLS),
    DevRoute("Speedometer", Screen.Speedometer.route, DevRouteCategory.TOOLS),
    DevRoute("Altimeter", Screen.Altimeter.route, DevRouteCategory.TOOLS),
    DevRoute("Sound meter", Screen.SoundMeter.route, DevRouteCategory.TOOLS),
    DevRoute("Light meter", Screen.LightMeter.route, DevRouteCategory.TOOLS),
    DevRoute("Music player", Screen.MusicPlayer.createRoute(tab = 0), DevRouteCategory.TOOLS),
    DevRoute("Media downloader", Screen.MediaDownloader.homeRoute, DevRouteCategory.TOOLS),
    DevRoute("File cleaner", Screen.FileCleaner.route, DevRouteCategory.TOOLS),
    DevRoute("Notification vault", Screen.NotificationVault.route, DevRouteCategory.TOOLS),
    DevRoute("Clipboard", Screen.Clipboard.route, DevRouteCategory.TOOLS),
    DevRoute("Todo", Screen.Todo.route, DevRouteCategory.TOOLS),
    DevRoute("Calendar", Screen.Calendar.route, DevRouteCategory.TOOLS),
    DevRoute("Notepad", Screen.Notepad.route, DevRouteCategory.TOOLS),
    DevRoute("QR generator", Screen.QrGenerator.route, DevRouteCategory.TOOLS),
    DevRoute("Scanner", Screen.Scanner.createRoute(), DevRouteCategory.TOOLS),
    DevRoute("Timer", Screen.Timer.route, DevRouteCategory.TOOLS),
    DevRoute("Stopwatch", Screen.Stopwatch.route, DevRouteCategory.TOOLS),
    DevRoute("Pomodoro", Screen.Pomodoro.route, DevRouteCategory.TOOLS),
    DevRoute("Whisper", Screen.Whisper.route, DevRouteCategory.TOOLS),
    DevRoute("Search", Screen.Search.homeRoute, DevRouteCategory.TOOLS),
    DevRoute("File converter", Screen.FileConverter.createRoute(), DevRouteCategory.TOOLS),
    DevRoute("Flashlight", Screen.Flashlight.route, DevRouteCategory.TOOLS),
    DevRoute("Caffeinate", Screen.Caffeinate.route, DevRouteCategory.TOOLS),
    DevRoute("PurgeShot", Screen.PurgeShot.route, DevRouteCategory.TOOLS),
    DevRoute("Calculator", Screen.Calculator.route, DevRouteCategory.TOOLS),
    DevRoute("Unit converter", Screen.UnitConverter.route, DevRouteCategory.TOOLS),
    DevRoute("Password vault", Screen.PasswordVault.route, DevRouteCategory.TOOLS),
    DevRoute("PDF reader", Screen.PdfReader.route, DevRouteCategory.TOOLS),
)

fun filterDevRoutes(query: String): List<DevRoute> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return devRoutes
    return devRoutes.filter { route ->
        route.label.contains(trimmed, ignoreCase = true) ||
            route.route.contains(trimmed, ignoreCase = true) ||
            route.category.label.contains(trimmed, ignoreCase = true)
    }
}
