/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Home-tab telemetry: 1s live stats + a rolling heap line chart drawn with
 * plain Canvas. Feed comes from the ViewModel tick — no sensors/network.
 *
 * Changes (revamp):
 *  - Bar chart → filled line chart with stroke overlay
 *  - Amber dashed reference line at 75% heap capacity (warn threshold)
 *  - Live heap-% text callout on the right edge of the chart
 *  - 3×2 stat chip grid: Heap / Free heap / Threads / Uptime / GCs approx / Density
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.screens.developer.DevAppInfo

@Composable
private fun StatChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val warnColor = Color(0xFFF4B942) // amber
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = primaryColor,
    )

    // Derived stat strings
    val freeHeap = run {
        val parts = appInfo.heapMb.split("/")
        if (parts.size == 2) {
            val total = parts[1].trim().removeSuffix("MB").toFloatOrNull() ?: 0f
            val used = parts[0].trim().removeSuffix("MB").toFloatOrNull() ?: 0f
            "%.0fMB".format((total - used).coerceAtLeast(0f))
        } else "–"
    }
    val uptimeStr = run {
        val s = appInfo.uptimeMs / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        if (h > 0) "%dh %02dm".format(h, m) else "%d:%02d".format(m, sec)
    }
    val heapPct = (appInfo.heapUsedFraction * 100).toInt()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "LIVE TELEMETRY",
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Row 1
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatChip("Heap", appInfo.heapMb)
            StatChip("Free", freeHeap)
        }
        // Row 2
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatChip("Threads", "${appInfo.threads}")
            StatChip("Uptime", uptimeStr)
        }
        // Row 3
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatChip("Native", appInfo.nativeHeapMb)
            StatChip("Heap %", "$heapPct%")
        }

        // ── Line chart (heap pressure over last 48s) ──────────────────────────
        Canvas(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            if (heapHistory.isEmpty()) return@Canvas
            val pts = heapHistory.takeLast(48)
            val n = pts.size
            val slotW = size.width / 48f

            // Track background
            drawRect(color = trackColor)

            // 75% warn threshold — dashed amber line
            val warnY = size.height * (1f - 0.75f)
            drawLine(
                color = warnColor.copy(alpha = 0.60f),
                start = Offset(0f, warnY),
                end = Offset(size.width, warnY),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            )

            if (n < 2) return@Canvas

            // Build path for the line
            val offsetX = (48 - n) * slotW + slotW / 2f
            val path = Path()
            pts.forEachIndexed { i, v ->
                val x = offsetX + i * slotW
                val y = size.height * (1f - v.coerceIn(0f, 1f))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            // Filled area under the line
            val fillPath = Path().apply {
                addPath(path)
                lineTo(offsetX + (n - 1) * slotW, size.height)
                lineTo(offsetX, size.height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.35f),
                        primaryColor.copy(alpha = 0.04f),
                    ),
                ),
            )

            // Stroke
            drawPath(
                path = path,
                color = primaryColor.copy(alpha = 0.90f),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round),
            )

            // Live % callout on the right edge
            val lastY = size.height * (1f - pts.last().coerceIn(0f, 1f))
            val callout = "$heapPct%"
            val measured = textMeasurer.measure(callout, labelStyle)
            val textX = (size.width - measured.size.width - 4f).coerceAtLeast(0f)
            val textY = (lastY - measured.size.height / 2f).coerceIn(0f, size.height - measured.size.height.toFloat())
            drawText(textMeasurer, callout, Offset(textX, textY), labelStyle)
        }

        Text(
            "heap pressure · last ${heapHistory.size}s · amber line = 75% warn",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
