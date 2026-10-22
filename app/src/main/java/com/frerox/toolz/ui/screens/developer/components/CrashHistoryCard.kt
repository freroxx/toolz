/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Crash-lab history for the Diagnostics tab. Events are recorded locally in
 * DataStore before a deliberate crash, so the next launch can show what was
 * triggered. Nothing is uploaded.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.developer.CrashLabEvent
import com.frerox.toolz.data.developer.formatAbsoluteTime
import com.frerox.toolz.data.developer.formatRelativeTime

@Composable
fun CrashHistoryCard(
    events: List<CrashLabEvent>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DevCard(modifier = modifier) {
        DevSectionTitle(
            title = "Crash-lab history",
            supporting = if (events.isEmpty()) {
                "No test events recorded"
            } else {
                "${events.size} local ${if (events.size == 1) "event" else "events"} · newest first"
            },
            action = if (events.isNotEmpty()) {
                {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Clear crash-lab history",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                null
            },
        )

        if (events.isEmpty()) {
            DevEmptyState(
                icon = Icons.Rounded.History,
                title = "Nothing has crashed",
                body = "Crash-lab events are written here before a deliberate crash, so you can verify the next launch.",
            )
        } else {
            events.takeLast(6).reversed().forEach { event ->
                CrashEventRow(event)
            }
        }
    }
}

@Composable
private fun CrashEventRow(event: CrashLabEvent) {
    val error = MaterialTheme.colorScheme.error
    val container = error.copy(alpha = 0.08f)
    val title = crashTitle(event.type)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = error.copy(alpha = 0.16f),
                contentColor = error,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.BugReport,
                    contentDescription = null,
                    modifier = Modifier.padding(7.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (event.note.isNotBlank()) {
                    Text(
                        text = event.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = formatAbsoluteTime(event.atMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DevPill(
                text = formatRelativeTime(event.atMs),
                containerColor = error.copy(alpha = 0.14f),
                contentColor = error,
            )
        }
    }
}

private fun crashTitle(type: String): String = when (type) {
    "NPE" -> "Null pointer"
    "ANR" -> "Not responding"
    "OOM" -> "Out of memory"
    "IllegalState" -> "Illegal state"
    else -> type.ifBlank { "Unknown test event" }
}
