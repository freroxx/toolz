/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Command palette: one searchable overlay for quick console actions —
 * toggle master, spoof/clear, open tools, fire labs. Actions are plain
 * lambdas supplied by the screen; this file only renders + filters.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class DevCommand(
    val title: String,
    val subtitle: String = "",
    val icon: ImageVector? = null,
    val danger: Boolean = false,
    val run: () -> Unit,
)

@Composable
fun CommandPalette(
    visible: Boolean,
    commands: List<DevCommand>,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { focus.requestFocus() } catch (_: Exception) { }
    }

    val filtered = remember(commands, query) {
        if (query.isBlank()) commands
        else commands.filter {
            it.title.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Column(Modifier.padding(vertical = 14.dp, horizontal = 6.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Type a command…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .focusRequester(focus),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filtered, key = { it.title }) { cmd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    cmd.run()
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            cmd.icon?.let { icon ->
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (cmd.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    cmd.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (cmd.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                                if (cmd.subtitle.isNotBlank()) {
                                    Text(
                                        cmd.subtitle,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text("↵", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                "No command matches \"$query\".",
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "All actions are local. Crash actions ask for confirmation first.",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
