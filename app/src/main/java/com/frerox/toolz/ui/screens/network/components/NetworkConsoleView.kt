/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.ui.screens.network.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.network.DiagnosticLog
import com.frerox.toolz.data.network.LogLevel
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveIconButton
import com.frerox.toolz.ui.theme.LocalVibrationManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NetworkConsoleView(
    logs: List<DiagnosticLog>,
    isShizukuReady: Boolean,
    consoleEnabled: Boolean,
    onToggleConsole: (Boolean) -> Unit,
    onExecuteRawCommand: (String) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val vibrationManager = LocalVibrationManager.current

    var commandInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    val listState = rememberLazyListState()

    val categories = listOf("ALL", "TWEAK", "COMMAND", "SYSTEM", "DIAG", "FIX")

    val filteredLogs = remember(logs, selectedCategory) {
        logs.filter { log ->
            if (selectedCategory == "ALL") true
            else log.tag.equals(selectedCategory, ignoreCase = true) || log.tag.contains(selectedCategory, ignoreCase = true)
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!consoleEnabled) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.secondary)
                        Text("Raw shell is disabled", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    }
                    Text(
                        "This console executes privileged commands as the shell user. It can change system behavior. Enable only if you know what you are doing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Developer mode", style = MaterialTheme.typography.labelLarge)
                        Switch(checked = false, onCheckedChange = { onToggleConsole(true) })
                    }
                }
            }
            return@Column
        }
        // Confirmation for destructive commands
        // Header with status and actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Diagnostic Console",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isShizukuReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    )
                    Text(
                        text = if (isShizukuReady) "Privileged Shell Active" else "Shell Restricted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        val text = logs.joinToString("\n") { "[${it.tag}] ${it.message}" }
                        clipboard.setText(AnnotatedString(text))
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
                }

                FilledTonalIconButton(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        onClearLogs()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                }
            }
        }

        // Filters
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = {
                        vibrationManager?.vibrateClick()
                        selectedCategory = category
                    },
                    label = { Text(category) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Terminal
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = Color.Black.copy(alpha = 0.03f),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            if (filteredLogs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No logs recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs) { log ->
                        ConsoleLogItem(log = log)
                    }
                }
            }
        }

        // Input
        OutlinedTextField(
            value = commandInput,
            onValueChange = { commandInput = it },
            placeholder = { Text("Enter shell command...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
            enabled = isShizukuReady,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (commandInput.isNotBlank()) {
                            vibrationManager?.vibrateClick()
                            onExecuteRawCommand(commandInput.trim())
                            commandInput = ""
                        }
                    },
                    enabled = isShizukuReady && commandInput.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, null)
                }
            }
        )
    }
}

@Composable
private fun ConsoleLogItem(log: DiagnosticLog) {
    val (tagColor, tagBg) = when (log.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        LogLevel.WARNING -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        LogLevel.SUCCESS -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        LogLevel.INFO -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = tagBg,
            modifier = Modifier.width(60.dp)
        ) {
            Text(
                text = log.tag,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = tagColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 16.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
