/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Runtime stats for the Overview tab. The ViewModel ticks once a
 * second; this card only draws the last 48 samples and real process counters.
 * No sensors, no network, no synthetic GC numbers.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.developer.DevAppInfo
import com.frerox.toolz.data.developer.formatBytes
import com.frerox.toolz.data.developer.formatDuration

@Composable
fun RuntimeStatsCard(
    appInfo: DevAppInfo,
    heapHistory: List<Float>,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val warning = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    val guide = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val heapPercent = (appInfo.heapUsedFraction * 100f).toInt().coerceIn(0, 100)
    val samples = heapHistory.takeLast(48)

    DevCard(modifier = modifier) {
        DevSectionTitle(
            title = "Live process",
            supporting = "Updates once per second from the running app process",
            action = {
                Icon(
                    imageVector = Icons.Rounded.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DevStatTile(
                label = "Heap",
                value = formatBytes(appInfo.heapUsedBytes),
                supporting = "$heapPercent% of ${formatBytes(appInfo.heapMaxBytes)}",
                modifier = Modifier.weight(1f),
            )
            DevStatTile(
                label = "Native",
                value = formatBytes(appInfo.nativeHeapBytes),
                supporting = "${appInfo.threads} threads",
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DevStatTile(
                label = "Uptime",
                value = formatDuration(appInfo.processUptimeMs),
                supporting = "PID ${appInfo.pid}",
                modifier = Modifier.weight(1f),
            )
            DevStatTile(
                label = "Density",
                value = "${appInfo.densityDpi} dpi",
                supporting = if (appInfo.debuggable) "Debug build" else "Release build",
                modifier = Modifier.weight(1f),
            )
        }

        Canvas(
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(14.dp))
                .semantics {
                    contentDescription = "Heap usage chart, $heapPercent percent of maximum heap"
                },
        ) {
            drawRect(color = track)

            // Quarter guides keep the chart readable without axis clutter.
            listOf(0.25f, 0.5f).forEach { fraction ->
                val y = size.height * (1f - fraction)
                drawLine(
                    color = guide,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }

            val warnY = size.height * 0.25f
            drawLine(
                color = warning.copy(alpha = 0.65f),
                start = Offset(0f, warnY),
                end = Offset(size.width, warnY),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)),
            )

            if (samples.size < 2) return@Canvas

            val slotWidth = size.width / 48f
            val offsetX = (48 - samples.size) * slotWidth + slotWidth / 2f
            val path = Path()
            samples.forEachIndexed { index, value ->
                val x = offsetX + index * slotWidth
                val y = size.height * (1f - value.coerceIn(0f, 1f))
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            val fill = Path().apply {
                addPath(path)
                lineTo(offsetX + (samples.size - 1) * slotWidth, size.height)
                lineTo(offsetX, size.height)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.32f),
                        primary.copy(alpha = 0.03f),
                    ),
                ),
            )
            drawPath(
                path = path,
                color = primary,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round),
            )

            drawCircle(
                color = primary,
                radius = 3.5f,
                center = Offset(
                    offsetX + (samples.size - 1) * slotWidth,
                    size.height * (1f - samples.last().coerceIn(0f, 1f)),
                ),
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Heap pressure · last ${samples.size}s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Dashed = 75% warning",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
