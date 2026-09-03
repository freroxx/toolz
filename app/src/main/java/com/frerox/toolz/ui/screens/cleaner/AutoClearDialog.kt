/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * V4 auto-clear UX: Play-safe disclosure + live progress dialog.
 */

package com.frerox.toolz.ui.screens.cleaner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.frerox.toolz.service.AutoClearState

@Composable
fun AutoClearDisclosure(
    appCount: Int,
    onEnableService: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AutoFixHigh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) },
        title = { Text("Auto-clear internal caches?", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp), textAlign = TextAlign.Center) },
        text = {
            Text(
                "Toolz will open each of the $appCount selected app's system Settings page and tap “Clear cache” for you.\n\n" +
                    "• Starts only when you tap Auto-clear — never in the background\n" +
                    "• You can stop it at any time\n" +
                    "• Requires enabling “Toolz Cleaner Auto-clear” in Accessibility settings (reversible anytime)",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(onClick = onEnableService, shape = RoundedCornerShape(20.dp)) { Text("Enable service", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) } },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun AutoClearProgressDialog(
    state: AutoClearState,
    onStop: () -> Unit,
    onRescan: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = { if (!state.running) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth(0.94f)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.running) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                    else Icon(
                        if (state.failed.isEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null,
                        Modifier.size(26.dp),
                        tint = if (state.failed.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                state.running -> "Auto-clearing…"
                                state.failed.isEmpty() -> "Auto-clear done"
                                else -> "Auto-clear finished"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        )
                        Text(
                            "${state.cleared.size} cleared" + if (state.failed.isNotEmpty()) " • ${state.failed.size} need a manual tap" else "",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (state.current.isNotBlank() && state.running) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(state.current, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
                var showLog by remember { mutableStateOf(false) }
                if (showLog) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(state.log.takeLast(60)) { line ->
                            Text("• $line", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (state.log.size > 3) {
                    TextButton(onClick = { showLog = !showLog }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(if (showLog) "Hide details" else "Details", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.running) {
                        OutlinedButton(onClick = onStop, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f).height(46.dp)) {
                            Icon(Icons.Rounded.StopCircle, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Stop")
                        }
                    } else {
                        OutlinedButton(onClick = onClose, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f).height(46.dp)) { Text("Close") }
                        Button(onClick = onRescan, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f).height(46.dp)) { Text("Rescan") }
                    }
                }
            }
        }
    }
}
