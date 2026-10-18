/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Crash-lab history: test events recorded on-device (DataStore), cleared
 * by the same auto-reset net as everything else. Purely diagnostic —
 * nothing here leaves the device automatically.
 *
 * Revamp changes:
 *  - Each event row gets a 3dp left accent border coloured by crash type:
 *    NPE = amber, ANR = red, OOM = purple, other = primary
 *  - Timestamps shown as relative ("3 min ago", "just now") instead of raw
 *    absolute time (absolute is kept as a subtitle)
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.screens.developer.CrashLabEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun crashTypeColor(type: String): Color = when {
    type.contains("NPE", ignoreCase = true) -> Color(0xFFF4B942)   // amber
    type.contains("ANR", ignoreCase = true) -> Color(0xFFEF5350)   // red
    type.contains("OOM", ignoreCase = true) -> Color(0xFFAB47BC)   // purple
    else -> Color(0xFF42A5F5)                                        // blue
}

private fun relativeTime(atMs: Long): String {
    val diff = System.currentTimeMillis() - atMs
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}

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
                val accentColor = crashTypeColor(e.type)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                        .background(accentColor.copy(alpha = 0.07f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 3dp left accent border
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(38.dp)
                            .background(accentColor.copy(alpha = 0.80f)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).padding(vertical = 5.dp)) {
                        Text(
                            e.type,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = accentColor,
                        )
                        Text(
                            fmt.format(Date(e.atMs)),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        relativeTime(e.atMs),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}
