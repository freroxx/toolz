/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Command catalog for the Developer Mode palette. Navigation, sandbox
 * bulk actions, diagnostics and maintenance are all reachable from one
 * search field. Every action is local; destructive ones open a confirmation.
 */

package com.frerox.toolz.ui.screens.developer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WarningAmber
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.LogcatScope
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.screens.developer.components.DevCommand
import java.util.Locale

internal fun buildDeveloperCommands(
    viewModel: DeveloperModeViewModel,
    logcatTail: Boolean,
    onToggleLogcatTail: () -> Unit,
    onSelectTab: (DeveloperTab) -> Unit,
    onOpenRoute: (String) -> Unit,
    onOpenRouteLauncher: () -> Unit,
    onCrash: (DevCrashTest) -> Unit,
    onResetFixtures: () -> Unit,
    onResetDeveloperMode: () -> Unit,
    onClearDatabase: () -> Unit,
    onResetOnboarding: () -> Unit,
    onRestart: () -> Unit,
): List<DevCommand> = buildList {
    // Navigation
    DeveloperTab.entries.forEach { tab ->
        add(
            DevCommand(
                title = tab.title,
                subtitle = when (tab) {
                    DeveloperTab.OVERVIEW -> "Master control, active sandboxes and runtime stats"
                    DeveloperTab.SANDBOXES -> "Search and configure fixture controls"
                    DeveloperTab.DIAGNOSTICS -> "Report, logcat, crash lab and notifications"
                    DeveloperTab.SYSTEM -> "Runtime context, storage, preferences and maintenance"
                },
                icon = when (tab) {
                    DeveloperTab.OVERVIEW -> Icons.Rounded.Home
                    DeveloperTab.SANDBOXES -> Icons.Rounded.Science
                    DeveloperTab.DIAGNOSTICS -> Icons.Rounded.BugReport
                    DeveloperTab.SYSTEM -> Icons.Rounded.Settings
                },
                run = { onSelectTab(tab) },
            ),
        )
    }

    // Master and sandboxes
    add(
        DevCommand(
            title = "Turn off Developer Mode",
            subtitle = "Close the console and keep sandbox selections",
            icon = Icons.Rounded.PowerSettingsNew,
            run = { viewModel.setDevMode(false) },
        ),
    )
    add(
        DevCommand(
            title = "Enable all sandboxes",
            subtitle = "Turn on all ${DevToolId.entries.size} fixture controls",
            icon = Icons.Rounded.Science,
            run = viewModel::spoofAll,
        ),
    )
    add(
        DevCommand(
            title = "Return all to live data",
            subtitle = "Turn off every sandbox without clearing fixture values",
            icon = Icons.Rounded.WarningAmber,
            run = viewModel::clearAllSpoofs,
        ),
    )
    DevPreset.entries.forEach { preset ->
        add(
            DevCommand(
                title = "Apply ${preset.title} preset",
                subtitle = preset.description,
                icon = when (preset) {
                    DevPreset.SHOWCASE -> Icons.Rounded.AutoAwesome
                    DevPreset.EDGE_CASES -> Icons.Rounded.WarningAmber
                    DevPreset.TRAVEL -> Icons.Rounded.Public
                },
                run = { viewModel.applyPreset(preset) },
            ),
        )
    }

    // Diagnostics and reports
    add(
        DevCommand(
            title = "Copy diagnostic report",
            subtitle = "App, device, active sandboxes and crash history",
            icon = Icons.Rounded.Description,
            run = viewModel::copyBugReport,
        ),
    )
    add(
        DevCommand(
            title = "Share diagnostic report",
            subtitle = "Send the local report through the system share sheet",
            icon = Icons.Rounded.Share,
            run = viewModel::shareBugReport,
        ),
    )
    add(
        DevCommand(
            title = "Share fast report",
            subtitle = "Markdown file with warnings, errors and a full device snapshot",
            icon = Icons.Rounded.Description,
            run = viewModel::shareFastReport,
        ),
    )
    add(
        DevCommand(
            title = "Copy warnings and errors",
            subtitle = "Only warning, error and fatal log lines",
            icon = Icons.Rounded.ContentCopy,
            run = viewModel::copyWarningsAndErrors,
        ),
    )
    add(
        DevCommand(
            title = if (logcatTail) "Pause live logcat" else "Resume live logcat",
            subtitle = if (logcatTail) "Stop following new lines" else "Follow new lines again",
            icon = if (logcatTail) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            run = onToggleLogcatTail,
        ),
    )
    add(
        DevCommand(
            title = "Refresh logcat",
            subtitle = "Fetch the latest PID-filtered lines now",
            icon = Icons.Rounded.Refresh,
            run = viewModel::refreshLogcat,
        ),
    )
    add(
        DevCommand(
            title = "Clear log stream",
            subtitle = "Empty the local ring buffer and visible log",
            icon = Icons.Rounded.DeleteSweep,
            run = viewModel::clearLogcat,
        ),
    )
    add(
        DevCommand(
            title = "Use app-only logcat",
            subtitle = "PID-filtered stream that always works",
            icon = Icons.Rounded.Terminal,
            run = { viewModel.setLogcatScope(LogcatScope.APP) },
        ),
    )
    add(
        DevCommand(
            title = "Use full system logcat",
            subtitle = "Every process via authorized Shizuku",
            icon = Icons.Rounded.Terminal,
            run = { viewModel.setLogcatScope(LogcatScope.SYSTEM) },
        ),
    )
    add(
        DevCommand(
            title = "Authorize Shizuku",
            subtitle = "Needed for full-system logcat",
            icon = Icons.Rounded.Lock,
            run = viewModel::requestShizukuPermission,
        ),
    )
    add(
        DevCommand(
            title = "Send test log entry",
            subtitle = "Write one INFO line to the local logcat hub",
            icon = Icons.Rounded.Terminal,
            run = { viewModel.sendTestLog() },
        ),
    )
    DevCrashTest.entries.forEach { test ->
        add(
            DevCommand(
                title = "Crash: ${test.title.lowercase(Locale.ROOT)}",
                subtitle = test.description,
                icon = Icons.Rounded.BugReport,
                danger = true,
                run = { onCrash(test) },
            ),
        )
    }
    add(
        DevCommand(
            title = "Send active-tool notification",
            subtitle = "Posts developer ID 60001 on the active-tool channel",
            icon = Icons.Rounded.Notifications,
            run = { viewModel.sendTestNotification(DevNotificationTest.ACTIVE) },
        ),
    )

    // System and maintenance
    add(
        DevCommand(
            title = "Refresh system inspectors",
            subtitle = "Storage, Room table counts and preferences",
            icon = Icons.Rounded.Refresh,
            run = {
                viewModel.refreshStorageInfo()
                viewModel.refreshTableCounts()
                viewModel.refreshPrefs()
            },
        ),
    )
    add(
        DevCommand(
            title = "Clear cache",
            subtitle = "Remove temporary files without touching databases",
            icon = Icons.Rounded.DeleteSweep,
            run = viewModel::clearCache,
        ),
    )
    add(
        DevCommand(
            title = "Open any screen",
            subtitle = "Search ${devRoutes.size} argument-free routes",
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
            run = onOpenRouteLauncher,
        ),
    )
    add(
        DevCommand(
            title = "Open appearance settings",
            subtitle = "Theme, language, font size and layout",
            icon = Icons.Rounded.Settings,
            run = { onOpenRoute(Screen.Settings.route) },
        ),
    )
    add(
        DevCommand(
            title = "Android developer options",
            subtitle = "Open the system developer settings",
            icon = Icons.Rounded.Settings,
            run = viewModel::openSystemDeveloperOptions,
        ),
    )
    add(
        DevCommand(
            title = "App notification settings",
            subtitle = "Permission and channel controls for Toolz",
            icon = Icons.Rounded.Notifications,
            run = viewModel::openNotificationSettings,
        ),
    )
    add(
        DevCommand(
            title = "Toolz Discord",
            subtitle = "Community support and release notes",
            icon = Icons.Rounded.Forum,
            run = viewModel::openDiscord,
        ),
    )
    add(
        DevCommand(
            title = "Restart app",
            subtitle = "Apply current sandboxes to every screen",
            icon = Icons.Rounded.RestartAlt,
            danger = true,
            run = onRestart,
        ),
    )
    add(
        DevCommand(
            title = "Reset onboarding",
            subtitle = "Show the welcome flow on the next launch",
            icon = Icons.Rounded.Refresh,
            run = onResetOnboarding,
        ),
    )
    add(
        DevCommand(
            title = "Reset fixture values",
            subtitle = "Restore defaults and keep the current selection",
            icon = Icons.Rounded.Restore,
            run = onResetFixtures,
        ),
    )
    add(
        DevCommand(
            title = "Reset Developer Mode",
            subtitle = "Turn off all sandboxes and clear crash history",
            icon = Icons.Rounded.Warning,
            danger = true,
            run = onResetDeveloperMode,
        ),
    )
    add(
        DevCommand(
            title = "Clear database tables",
            subtitle = "Delete every Room row; cannot be undone",
            icon = Icons.Rounded.DeleteForever,
            danger = true,
            run = onClearDatabase,
        ),
    )
}
