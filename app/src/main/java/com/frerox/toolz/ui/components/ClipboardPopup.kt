/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.MainActivity
import com.frerox.toolz.ui.screens.clipboard.ClipboardViewModel

/**
 * Quick switcher launched from the QS tile: the 5 most recent clips,
 * tap to copy back to the system clipboard, plus paste-current + open history.
 */
@Composable
fun ClipboardPopup(
    onDismiss: () -> Unit,
    onManageHistory: () -> Unit,
    viewModel: ClipboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val recent = remember(entries) { entries.take(5) }
    val haptic = rememberToolzHapticFeedback()

    // Capture anything new while the popup is visible (foreground read allowed).
    LaunchedEffect(Unit) { viewModel.refreshClipboard() }

    fun copy(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Toolz", text))
        haptic.success()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize()
                .clickable(onClick = onDismiss),
        )
        ExpressiveCard(
            onClick = {},
            modifier = Modifier.width(360.dp).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Rounded.ContentPaste, null, Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Clipboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (recent.isEmpty()) "No clips yet" else "${entries.size} clips • tap to copy",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                    }
                }

                if (recent.isEmpty()) {
                    Text(
                        "Copy something, then reopen — or paste the current clip.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { viewModel.pasteCurrent() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.ContentPaste, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Paste current")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(recent, key = { it.id }) { entry ->
                            Surface(
                                onClick = { copy(entry.content); onDismiss() },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            entry.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            entry.type.lowercase().replaceFirstChar { c -> c.uppercase() },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (entry.isPinned) {
                                        Icon(Icons.Rounded.PushPin, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        haptic.click()
                        val intent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            putExtra("navigate_to", "clipboard")
                        }
                        context.startActivity(intent)
                        onManageHistory()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open full history", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.ArrowForward, null, Modifier.size(16.dp))
                }
            }
        }
    }
}
