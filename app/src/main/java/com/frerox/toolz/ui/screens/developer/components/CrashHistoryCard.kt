/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Crash-lab history: test events recorded on-device (DataStore), cleared
 * by the same auto-reset net as everything else. Purely diagnostic —
 * nothing here leaves the device automatically.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.screens.developer.CrashLabEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CrashHistoryCard(
    events: List<CrashLabEvent>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "CRASH HISTORY (TEST ONLY)",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )
            if (events.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    modifier = Modifier.size(22.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${events.size}",
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Rounded.DeleteSweep,
                        contentDescription = "Clear crash history",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        if (events.isEmpty()) {
            Text(
                "No recorded crash-lab events.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val fmt = remember { SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()) }
            events.asReversed().take(8).forEach { e ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        e.type,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        fmt.format(Date(e.atMs)),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
