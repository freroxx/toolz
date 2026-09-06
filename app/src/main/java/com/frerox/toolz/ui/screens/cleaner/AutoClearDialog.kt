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
import androidx.compose.material.icons.rounded.Cached
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.frerox.toolz.service.AutoClearState
import com.frerox.toolz.service.FailedApp

@Composable
fun AutoClearDisclosure(
    appCount: Int,
    onEnableService: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Cached, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) },
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
    onClose: () -> Unit,
    onOpenAppSettings: (pkg: String) -> Unit = {}
) {
    Dialog(onDismissRequest = { if (!state.running) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth(0.94f)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        state.running -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                        state.done && state.failedApps.isEmpty() -> Icon(
                            Icons.Rounded.CheckCircle, null,
                            Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        else -> Icon(
                            Icons.Rounded.ErrorOutline, null,
                            Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                state.paused -> "Paused"
                                state.running -> "Auto-clearing…"
                                state.failedApps.isEmpty() -> "Auto-cleared ${state.cleared.size}"
                                else -> "Finished — ${state.cleared.size} cleared"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        )
                        if (state.total > 0) {
                            Text(
                                "${state.index} of ${state.total}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (state.total > 0 && (state.running || state.done)) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (state.index.toFloat() / state.total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                    )
                }
                if (state.running && state.current.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Now: ${state.current}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.done && state.failedApps.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${state.failedApps.size} need a manual tap",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        state.failedApps.forEach { failed: FailedApp ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        failed.label,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        failed.reason,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(onClick = { onOpenAppSettings(failed.pkg) }) {
                                    Text("Settings", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
                                }
                            }
                        }
                    }
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
                        Button(
                            onClick = onStop,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.Rounded.StopCircle, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Stop & Exit")
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
