/*
 * Copyright (C) 2026 Toolz Contributors
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
import com.frerox.toolz.ui.screens.network.suite.NetCard
import com.frerox.toolz.ui.screens.network.suite.NetTokens
import com.frerox.toolz.ui.theme.LocalVibrationManager

/** Clear, simple M3 Expressive console — two cards: gate + terminal, 50dp chips, tonal input. */
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
        logs.filter { if (selectedCategory == "ALL") true else it.tag.contains(selectedCategory, ignoreCase = true) }
    }
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.animateScrollToItem(0) }

    var pendingDestructive by remember { mutableStateOf<String?>(null) }
    val destructiveKeywords = remember { setOf("rm ", "pm clear", "pm uninstall", "svc data disable", "svc wifi disable", "reboot", "mkfs", "dd ", ">:") }
    fun isDestructive(cmd: String): Boolean {
        val lower = cmd.lowercase().trim()
        return destructiveKeywords.any { lower.contains(it) } || lower.matches(Regex(".*\\b(rm\\s+-rf|mkfs|dd\\s+if=).*"))
    }
    pendingDestructive?.let { cmd ->
        AlertDialog(
            onDismissRequest = { pendingDestructive = null },
            icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Confirm privileged command") },
            text = { Text("This looks destructive:\n\n$cmd", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) },
            confirmButton = {
                Button(onClick = { val c = pendingDestructive!!; pendingDestructive = null; vibrationManager?.vibrateClick(); onExecuteRawCommand(c) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Run anyway") }
            },
            dismissButton = { TextButton(onClick = { pendingDestructive = null }) { Text("Cancel") } },
            shape = RoundedCornerShape(24.dp)
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(NetTokens.SpacingL)) {
        if (!consoleEnabled) {
            NetCard(title = "Developer console", subtitle = "Raw shell is disabled", icon = Icons.Rounded.Lock) {
                Text("This console runs privileged shell commands via Shizuku. Enable only if you know what you are doing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable developer mode", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Switch(checked = false, onCheckedChange = { onToggleConsole(true) })
                }
            }
            return@Column
        }

        // Status + actions — single NetCard header, tonal buttons, 50dp
        NetCard(
            title = "Diagnostic Console",
            subtitle = if (isShizukuReady) "Privileged shell active" else "Shell restricted — Shizuku not bound",
            icon = Icons.Rounded.Terminal,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = { vibrationManager?.vibrateClick(); clipboard.setText(AnnotatedString(logs.joinToString("\n") { "[${it.tag}] ${it.message}" })) }, shape = RoundedCornerShape(50)) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy logs", modifier = Modifier.size(18.dp))
                    }
                    FilledTonalIconButton(
                        onClick = { vibrationManager?.vibrateClick(); onClearLogs() },
                        shape = RoundedCornerShape(50),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                    ) { Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(18.dp)) }
                }
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isShizukuReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error))
                Text(if (isShizukuReady) "Ready to execute" else "Enable Shizuku to run commands", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Filters — 50dp pill, single scroll row, expressive
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { cat ->
                    FilterChip(selected = selectedCategory == cat, onClick = { vibrationManager?.vibrateClick(); selectedCategory = cat }, label = { Text(cat) }, shape = RoundedCornerShape(50))
                }
            }
        }

        // Terminal — one card, surfaceContainerHigh, monospaced, empty state centered, 20dp radius
        NetCard(title = "Logs", subtitle = "${filteredLogs.size} entries • ${logs.size} total", icon = Icons.Rounded.ReceiptLong) {
            Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 420.dp).weight(1f, fill = false), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(20.dp)) {
                if (filteredLogs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Terminal, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                            Text("No logs yet — run a scan or tweak", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredLogs, key = { it.timestamp to it.tag }) { log -> ConsoleLogItem(log = log) }
                    }
                }
            }
            // Input — tonal, 50dp, send as FilledIconButton, disabled when not Shizuku
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                placeholder = { Text(if (isShizukuReady) "Enter shell command…" else "Shizuku required to type", style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                singleLine = true,
                enabled = isShizukuReady,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                trailingIcon = {
                    FilledIconButton(
                        onClick = {
                            if (commandInput.isNotBlank()) {
                                val trimmed = commandInput.trim().take(2048)
                                commandInput = ""
                                vibrationManager?.vibrateClick()
                                if (isDestructive(trimmed)) pendingDestructive = trimmed else onExecuteRawCommand(trimmed)
                            }
                        },
                        enabled = isShizukuReady && commandInput.isNotBlank(),
                        modifier = Modifier.size(40.dp)
                    ) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send") }
                }
            )
        }
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(8.dp), color = tagBg, modifier = Modifier.width(64.dp)) {
            Text(text = log.tag, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = tagColor, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(text = log.message, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 16.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}
