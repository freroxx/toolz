/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Home-tab telemetry: 1s live stats + a rolling heap sparkline drawn with
 * plain Canvas. Feed comes from the ViewModel tick — no sensors/network.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.screens.developer.DevAppInfo

@Composable
private fun StatChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Text(value, fontWeight = FontWeight.Black, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun TelemetryCard(
    appInfo: DevAppInfo,
    heapHistory: List<Float>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "LIVE TELEMETRY",
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatChip("Heap", appInfo.heapMb)
            StatChip("Native", appInfo.nativeHeapMb)
        }
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatChip("Threads", "${appInfo.threads}")
            StatChip(
                "Uptime",
                run {
                    val s = appInfo.uptimeMs / 1000
                    val h = s / 3600
                    val m = (s % 3600) / 60
                    val sec = s % 60
                    if (h > 0) "%dh %02dm".format(h, m) else "%d:%02d".format(m, sec)
                },
            )
        }

        // Rolling heap usage — one bar per second, primary color, quiet.
        val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        val track = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
        Canvas(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            if (heapHistory.isEmpty()) return@Canvas
            val slot = size.width / 48f
            heapHistory.takeLast(48).forEachIndexed { i, v ->
                val x = (48 - kotlin.math.min(48, heapHistory.size) + i) * slot
                val barH = (v.coerceIn(0f, 1f)) * size.height
                drawRoundRect(
                    color = track,
                    topLeft = Offset(x, 0f),
                    size = Size(slot * 0.7f, size.height),
                    cornerRadius = CornerRadius(6f, 6f),
                )
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, size.height - barH),
                    size = Size(slot * 0.7f, barH.coerceAtLeast(4f)),
                    cornerRadius = CornerRadius(6f, 6f),
                )
            }
        }
        Text(
            "heap pressure, last ${heapHistory.size}s",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
