/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Diagnostics tab: a local report, live logcat, deliberate crash tests and
 * the notification-channel matrix. Everything stays on this device.
 */

package com.frerox.toolz.ui.screens.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.developer.CrashLabEvent
import com.frerox.toolz.data.developer.DevAppInfo
import com.frerox.toolz.data.developer.DevShizukuState
import com.frerox.toolz.data.developer.LogcatScope
import com.frerox.toolz.ui.screens.developer.components.CrashHistoryCard
import com.frerox.toolz.ui.screens.developer.components.DevActionRow
import com.frerox.toolz.ui.screens.developer.components.DevCard
import com.frerox.toolz.ui.screens.developer.components.DevDangerCard
import com.frerox.toolz.ui.screens.developer.components.DevInlineNotice
import com.frerox.toolz.ui.screens.developer.components.DevKeyValue
import com.frerox.toolz.ui.screens.developer.components.DevPill
import com.frerox.toolz.ui.screens.developer.components.DevSectionTitle
import com.frerox.toolz.ui.screens.developer.components.DevToggleRow
import com.frerox.toolz.ui.screens.developer.components.LogcatViewer
import com.frerox.toolz.ui.screens.diagnostics.FastReportInfoButton
import com.frerox.toolz.ui.screens.diagnostics.FastReportInfoDialog

@Composable
fun DeveloperDiagnosticsTab(
    appInfo: DevAppInfo,
    logs: List<String>,
    logcatTail: Boolean,
    logcatScope: LogcatScope,
    shizuku: DevShizukuState,
    clearedAtMs: Long?,
    crashNext: Boolean,
    crashHistory: List<CrashLabEvent>,
    notificationsEnabled: Boolean,
    postedNotificationIds: Set<Int>,
    onToggleTail: () -> Unit,
    onScopeChange: (LogcatScope) -> Unit,
    onRequestShizuku: () -> Unit,
    onRefreshLogcat: () -> Unit,
    onClearLogcat: () -> Unit,
    onCopyAllLogs: () -> Unit,
    onCopyWarnLogs: () -> Unit,
    onSendTestLog: () -> Unit,
    onSetCrashNext: (Boolean) -> Unit,
    onCrash: (DevCrashTest) -> Unit,
    onLogNonFatal: () -> Unit,
    onClearCrashHistory: () -> Unit,
    onSendNotification: (DevNotificationTest) -> Unit,
    onClearNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit,
    onShareFastReport: () -> Unit,
) {
    var fastReportInfoOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "report") {
            DevCard {
                DevSectionTitle(
                    title = "Diagnostic report",
                    supporting = "Local snapshot · no logs · no account data",
                    action = { DevPill("On device") },
                )
                DevKeyValue("App", "${appInfo.versionName} (${appInfo.versionCode})")
                DevKeyValue("Build", appInfo.buildType, monospace = true)
                DevKeyValue("Device", appInfo.deviceLabel)
                DevKeyValue("Android", appInfo.androidLabel)
                DevKeyValue("PID", appInfo.pid.toString(), monospace = true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onShareFastReport,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(17.dp),
                        )
                        Text("Fast report", maxLines = 1)
                    }
                    FastReportInfoButton(
                        onClick = { fastReportInfoOpen = true },
                        size = 48.dp,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onCopyReport, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(17.dp),
                        )
                        Text("Copy", maxLines = 1)
                    }
                    OutlinedButton(onClick = onShareReport, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(17.dp),
                        )
                        Text("Share", maxLines = 1)
                    }
                }
                Text(
                    text = "Fast report writes a Markdown (.md) file with recent warnings and errors plus a " +
                        "full device snapshot, ready to drop in the Toolz Discord. Copy and Share below send " +
                        "the short report only.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "logcat") {
            DevCard {
                DevSectionTitle(
                    title = "Logcat",
                    supporting = "Local ring buffer · app stream, or full system with Shizuku",
                    action = { DevPill("${logs.size}") },
                )
                LogcatViewer(
                    lines = logs,
                    tail = logcatTail,
                    scope = logcatScope,
                    shizuku = shizuku,
                    clearedAtMs = clearedAtMs,
                    onToggleTail = onToggleTail,
                    onScopeChange = onScopeChange,
                    onRequestShizukuPermission = onRequestShizuku,
                    onRefresh = onRefreshLogcat,
                    onClear = onClearLogcat,
                    onCopyAll = onCopyAllLogs,
                    onCopyWarnings = onCopyWarnLogs,
                    onSendTestLog = onSendTestLog,
                )
            }
        }

        item(key = "crash_lab") {
            DevDangerCard {
                DevSectionTitle(
                    title = "Crash lab",
                    supporting = "Deliberate failures for recovery testing",
                    action = { DevPill("Real exceptions") },
                )
                DevToggleRow(
                    icon = Icons.Rounded.BugReport,
                    title = "Crash on next launch",
                    supporting = "Saves a marker, then throws once after the next cold start.",
                    checked = crashNext,
                    onCheckedChange = onSetCrashNext,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
                DevCrashTest.entries.forEach { test ->
                    DevActionRow(
                        icon = Icons.Rounded.WarningAmber,
                        title = test.title,
                        supporting = test.description,
                        onClick = { onCrash(test) },
                        trailing = { DevPill(test.historyType) },
                    )
                }
                TextButton(onClick = onLogNonFatal) {
                    Text("Log a handled exception")
                }
                Text(
                    text = "History is written before the crash; if recording fails, the test aborts. The event is cleared automatically by Reset Developer Mode.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "crash_history") {
            CrashHistoryCard(
                events = crashHistory,
                onClear = onClearCrashHistory,
            )
        }

        item(key = "notifications") {
            DevCard {
                DevSectionTitle(
                    title = "Notification lab",
                    supporting = "Stable IDs 60001–60006 · real channels",
                    action = {
                        DevPill(
                            text = "${postedNotificationIds.size} active",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                if (!notificationsEnabled) {
                    DevInlineNotice(
                        icon = Icons.Rounded.NotificationsOff,
                        text = "Notifications are blocked for Toolz. Allow them in system settings to run these tests.",
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        action = {
                            TextButton(onClick = onOpenNotificationSettings) {
                                Text("Settings")
                            }
                        },
                    )
                }
                DevNotificationTest.entries.forEachIndexed { index, test ->
                    DevActionRow(
                        icon = Icons.Rounded.Notifications,
                        title = test.label,
                        supporting = test.channelId,
                        enabled = notificationsEnabled,
                        onClick = { onSendNotification(test) },
                        trailing = if (test.id in postedNotificationIds) {
                            { DevPill("Active") }
                        } else {
                            null
                        },
                    )
                    if (index != DevNotificationTest.entries.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        )
                    }
                }
                if (postedNotificationIds.isNotEmpty()) {
                    TextButton(onClick = onClearNotifications) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(17.dp),
                        )
                        Text("Clear test notifications")
                    }
                }
                Text(
                    text = "Clearing only cancels IDs in the developer band; real app notifications are untouched.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (fastReportInfoOpen) {
        FastReportInfoDialog(onDismiss = { fastReportInfoOpen = false })
    }
}
