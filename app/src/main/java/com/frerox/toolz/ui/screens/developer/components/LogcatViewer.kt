/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Live log terminal for Developer Mode. It merges the local DevLogHub ring
 * buffer with logcat, filters by severity and text, follows the tail unless
 * paused, and never uploads anything. Full-system scope is available only
 * through an authorized Shizuku service.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.developer.DevShizukuState
import com.frerox.toolz.data.developer.MAX_LOGCAT_LINES
import com.frerox.toolz.data.developer.LogcatLevel
import com.frerox.toolz.data.developer.LogcatScope
import com.frerox.toolz.data.developer.LogcatSeverity
import com.frerox.toolz.data.developer.filterLogcatLines
import com.frerox.toolz.data.developer.formatLogcatClock
import com.frerox.toolz.data.developer.logLineLevel
import com.frerox.toolz.data.developer.prettyLogcatLine

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogcatViewer(
    lines: List<String>,
    tail: Boolean,
    scope: LogcatScope,
    shizuku: DevShizukuState,
    clearedAtMs: Long?,
    onToggleTail: () -> Unit,
    onScopeChange: (LogcatScope) -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onCopyAll: () -> Unit,
    onCopyWarnings: () -> Unit,
    onSendTestLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf(LogcatSeverity.ALL) }
    val filtered = remember(lines, query, severity) {
        filterLogcatLines(lines, query = query, severity = severity)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(filtered, tail) {
        if (tail && filtered.isNotEmpty()) {
            listState.scrollToItem(filtered.lastIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilledTonalIconButton(onClick = onToggleTail) {
                Icon(
                    imageVector = if (tail) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (tail) "Pause live log" else "Resume live log",
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh logcat")
            }
            IconButton(onClick = onCopyAll, enabled = lines.isNotEmpty()) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy all log lines")
            }
            IconButton(onClick = onCopyWarnings, enabled = lines.isNotEmpty()) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = "Copy warnings and errors")
            }
            IconButton(onClick = onClear, enabled = lines.isNotEmpty()) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear the log stream")
            }
            TextButton(onClick = onSendTestLog) {
                Text("Test entry", style = MaterialTheme.typography.labelMedium)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Filter lines") },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear filter")
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Severity",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${filtered.size}/${lines.size} lines",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LogcatSeverity.entries.forEach { option ->
                FilterChip(
                    selected = severity == option,
                    onClick = { severity = option },
                    label = { Text(option.label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            LogcatScope.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = scope == option,
                    onClick = { onScopeChange(option) },
                    enabled = option == LogcatScope.APP || shizuku.authorized,
                    shape = SegmentedButtonDefaults.itemShape(index, LogcatScope.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (option == LogcatScope.SYSTEM && !shizuku.authorized) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(option.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        DevInlineNotice(
            icon = if (shizuku.authorized) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
            text = shizukuStatusText(shizuku),
            containerColor = if (shizuku.authorized) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            action = if (!shizuku.authorized && shizuku.installed && shizuku.available) {
                {
                    TextButton(onClick = onRequestShizukuPermission) {
                        Text("Authorize", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                null
            },
        )

        DevMonoBox(modifier = Modifier.fillMaxWidth().height(320.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = if (lines.isEmpty()) "No log lines yet" else "No lines match this filter",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                Text(
                                    text = if (tail) "Waiting for the next event…" else "Resume the tail or refresh the stream.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(filtered) { line ->
                        Text(
                            text = prettyLogcatLine(line),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            ),
                            color = logLineColor(line),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }

        Text(
            text = buildString {
                append(if (tail) "Tail running" else "Tail paused")
                append(" · ")
                append(if (scope == LogcatScope.SYSTEM) "system stream" else "app stream")
                append(" · ")
                append(if (clearedAtMs != null) "cleared ${formatLogcatClock(clearedAtMs)}" else "session buffer")
                append(" · newest $MAX_LOGCAT_LINES lines")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun shizukuStatusText(state: DevShizukuState): String = when {
    state.authorized -> "Shizuku authorized · full system logcat is available"
    !state.installed -> "Shizuku not installed · using the local app stream"
    !state.available -> "Shizuku installed but not running · using the local app stream"
    else -> "Authorize Shizuku to read logs from every process"
}

@Composable
private fun logLineColor(line: String): Color {
    return when (logLineLevel(line)) {
        LogcatLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogcatLevel.ERROR, LogcatLevel.FATAL -> MaterialTheme.colorScheme.error
        LogcatLevel.INFO -> MaterialTheme.colorScheme.onSurface
        LogcatLevel.DEBUG, LogcatLevel.VERBOSE -> MaterialTheme.colorScheme.onSurfaceVariant
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
