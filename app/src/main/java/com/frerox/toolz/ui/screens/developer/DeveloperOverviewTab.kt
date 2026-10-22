/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Overview tab: master control, active sandboxes, live runtime stats, curated
 * presets, and a shortcut to the local diagnostic report.
 */

package com.frerox.toolz.ui.screens.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Science
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.developer.DevAppInfo
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.ui.screens.developer.components.ActiveToolChip
import com.frerox.toolz.ui.screens.developer.components.DevActionRow
import com.frerox.toolz.ui.screens.developer.components.DevCard
import com.frerox.toolz.ui.screens.developer.components.DevEmptyState
import com.frerox.toolz.ui.screens.developer.components.DevInlineNotice
import com.frerox.toolz.ui.screens.developer.components.DevPill
import com.frerox.toolz.ui.screens.developer.components.DevSectionTitle
import com.frerox.toolz.ui.screens.developer.components.RuntimeStatsCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeveloperOverviewTab(
    spoofs: Map<DevToolId, Boolean>,
    appInfo: DevAppInfo,
    heapHistory: List<Float>,
    onToggleDevMode: (Boolean) -> Unit,
    onEnableAll: () -> Unit,
    onClearAll: () -> Unit,
    onDisableTool: (DevToolId) -> Unit,
    onApplyPreset: (DevPreset) -> Unit,
    onOpenSandboxes: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit,
) {
    val activeTools = DevToolId.entries.filter { spoofs[it] == true }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "master") {
            DeveloperMasterCard(
                activeCount = activeTools.size,
                onToggle = onToggleDevMode,
                onEnableAll = onEnableAll,
                onClearAll = onClearAll,
            )
        }

        item(key = "notice") {
            DevInlineNotice(
                icon = Icons.Rounded.Science,
                text = "Fixture data lives in RAM. No database rows, files, network or sensor readings are changed.",
            )
        }

        item(key = "active") {
            DevCard {
                DevSectionTitle(
                    title = "Active sandboxes",
                    supporting = if (activeTools.isEmpty()) {
                        "No sandboxes are active"
                    } else {
                        "Remove a chip to return that tool to live data"
                    },
                    action = if (activeTools.isNotEmpty()) {
                        { DevPill("${activeTools.size}") }
                    } else {
                        null
                    },
                )
                if (activeTools.isEmpty()) {
                    DevEmptyState(
                        icon = Icons.Rounded.Science,
                        title = "No sandboxes active",
                        body = "Pick a preset below or open Sandboxes to choose individual tools.",
                        actionLabel = "Open Sandboxes",
                        onAction = onOpenSandboxes,
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        activeTools.forEach { tool ->
                            ActiveToolChip(
                                title = tool.title,
                                onRemove = { onDisableTool(tool) },
                            )
                        }
                    }
                    TextButton(onClick = onOpenSandboxes) {
                        Text("Manage all sandboxes")
                    }
                }
            }
        }

        item(key = "runtime_stats") {
            RuntimeStatsCard(
                appInfo = appInfo,
                heapHistory = heapHistory,
            )
        }

        item(key = "presets") {
            DevCard {
                DevSectionTitle(
                    title = "Presets",
                    supporting = "Replace the current selection and fixture values",
                )
                DevPreset.entries.forEachIndexed { index, preset ->
                    DevActionRow(
                        icon = presetIcon(preset),
                        title = preset.title,
                        supporting = preset.description,
                        onClick = { onApplyPreset(preset) },
                        trailing = { DevPill("${preset.toolCount} tools") },
                    )
                    if (index != DevPreset.entries.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
                Text(
                    text = "Applying a preset also replaces the current fixture values.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "report") {
            DevCard {
                DevSectionTitle(
                    title = "Diagnostic report",
                    supporting = "App, device, active sandbox names and crash history",
                    action = {
                        DevPill("Local")
                    },
                )
                Text(
                    text = "A readable snapshot with no log lines and no account data. Copy it or send it through the system share sheet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onCopyReport,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(17.dp),
                        )
                        Text("Copy", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onShareReport,
                        modifier = Modifier.weight(1f),
                    ) {
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
                DevActionRow(
                    icon = Icons.Rounded.Description,
                    title = "Open Diagnostics",
                    supporting = "Logcat, crash lab, notifications and this report",
                    onClick = onOpenDiagnostics,
                )
            }
        }
    }
}

private fun presetIcon(preset: DevPreset): ImageVector = when (preset) {
    DevPreset.SHOWCASE -> Icons.Rounded.AutoAwesome
    DevPreset.EDGE_CASES -> Icons.Rounded.WarningAmber
    DevPreset.TRAVEL -> Icons.Rounded.Public
}
