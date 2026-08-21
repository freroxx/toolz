/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.ui.screens.network.suite

import android.os.Parcelable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.network.DnsCategory
import com.frerox.toolz.data.network.NetworkPowerUiState
import com.frerox.toolz.data.network.PingSample
import com.frerox.toolz.data.network.ProcessNetworkUsage
import com.frerox.toolz.ui.components.ExpressiveCard
import kotlinx.parcelize.Parcelize
import kotlin.math.roundToInt

/**
 * Salvaged + unified building blocks from the retired NetworkPowerSuiteScreen.
 * All `internal` so the single NetworkSuiteScreen shell can compose them.
 */

@Parcelize
internal data class ShizukuPrompt(
    val featureName: String,
    val supportingText: String
) : Parcelable

internal fun categoryTitle(category: DnsCategory): String = when (category) {
    DnsCategory.PRIVACY -> "Privacy"
    DnsCategory.SPEED -> "Speed"
    DnsCategory.SECURITY -> "Security"
    DnsCategory.FAMILY -> "Family"
}

internal fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)

// ── Primitives ──────────────────────────────────────────────────────────────

@Composable
internal fun GlassCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ExpressiveCard(
        onClick = {},
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.width(8.dp))
                    trailing.invoke()
                }
            }
            content()
        }
    }
}

@Composable
internal fun StatusBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    ) {
        Text(
            "$label: $value",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
internal fun MiniMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun ProtectedActionButton(
    label: String,
    privilegedReady: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(),
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
        colors = if (privilegedReady) colors else ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        if (!privilegedReady) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
internal fun LockHint(text: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
internal fun RunningStatePill(label: String, running: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "state_pill")
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "state_pill_anim"
    )
    val brush = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = if (running) 0.42f else 0.16f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = if (running) 0.32f else 0.12f),
            MaterialTheme.colorScheme.primary.copy(alpha = if (running) 0.42f else 0.16f)
        ),
        start = Offset.Zero,
        end = Offset(300f * shimmer, 120f)
    )
    Surface(shape = RoundedCornerShape(20.dp), color = Color.Transparent) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(brush).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (running) Icons.Rounded.Speed else Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun LatencyChart(samples: List<PingSample>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val points = samples.takeLast(60)
        if (points.size < 2) return@Canvas
        val validLatencies = points.mapNotNull { it.latencyMs }
        val maxLatency = maxOf(80L, (validLatencies.maxOrNull() ?: 50L) + 10L).toFloat()
        val linePath = Path()
        points.forEachIndexed { index, sample ->
            val x = (index.toFloat() / points.lastIndex.coerceAtLeast(1)) * size.width
            val normalized = (sample.latencyMs ?: maxLatency.toLong()).coerceAtMost(maxLatency.toLong()) / maxLatency
            val y = size.height - (normalized * size.height)
            if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }
        val fillPath = Path().apply {
            addPath(linePath); lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(fillPath, brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.22f), Color.Transparent)))
        drawPath(linePath, color = lineColor, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
internal fun TrafficRow(process: ProcessNetworkUsage) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(process.name, fontWeight = FontWeight.Bold)
                StatusBadge(process.protocol, process.state)
            }
            Spacer(Modifier.height(6.dp))
            Text(process.localAddr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(process.remoteAddr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun NetworkMap(uiState: NetworkPowerUiState) {
    val nodes = uiState.topology.nodes
    val edges = uiState.topology.edges
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val infiniteTransition = rememberInfiniteTransition(label = "topology")
    val pulseOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing)),
        label = "pulse"
    )

    if (nodes.isEmpty()) {
        Text("Start a device scan to paint the local node graph.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            edges.forEach { edge ->
                val from = nodes.firstOrNull { it.id == edge.from } ?: return@forEach
                val to = nodes.firstOrNull { it.id == edge.to } ?: return@forEach
                val start = Offset(from.xBias * size.width, from.yBias * size.height)
                val end = Offset(to.xBias * size.width, to.yBias * size.height)
                drawLine(primary.copy(alpha = 0.15f), start, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                val pulsePos = Offset(start.x + (end.x - start.x) * pulseOffset, start.y + (end.y - start.y) * pulseOffset)
                drawCircle(color = primary.copy(alpha = 0.6f), radius = 3.dp.toPx(), center = pulsePos)
            }
            nodes.forEach { node ->
                val center = Offset(node.xBias * size.width, node.yBias * size.height)
                val glowRadius = if (node.isPrimary) 24.dp.toPx() else 16.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(listOf(primary.copy(alpha = 0.15f), Color.Transparent)),
                    radius = glowRadius * 1.5f, center = center
                )
                drawCircle(color = if (node.isPrimary) primary else secondary, radius = if (node.isPrimary) 14.dp.toPx() else 10.dp.toPx(), center = center)
                drawCircle(color = Color.White.copy(alpha = 0.3f), radius = if (node.isPrimary) 20.dp.toPx() else 14.dp.toPx(), center = center, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            nodes.take(3).forEach { node -> StatusBadge(node.label, if (node.isPrimary) "Active" else "Peer") }
        }
    }
}

@Composable
internal fun ShizukuAccessDialog(
    prompt: ShizukuPrompt,
    isServiceReady: Boolean,
    isAuthorized: Boolean,
    isReachable: Boolean,
    onDismiss: () -> Unit,
    onRequestAccess: () -> Unit,
    onVerify: () -> Unit
) {
    val status = when {
        isServiceReady -> "Connected"
        isAuthorized -> "Authorized, waiting for service"
        isReachable -> "Service reachable, permission needed"
        else -> "Shizuku service offline"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (isServiceReady) Icons.Rounded.VerifiedUser else Icons.Rounded.Lock, contentDescription = null) },
        title = { Text("Unlock ${prompt.featureName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(prompt.supportingText)
                StatusBadge("Status", status)
                Text(
                    "1. Start or pair Shizuku.\n2. Grant the permission prompt.\n3. Verify access and retry the locked action.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onRequestAccess) {
                Text(
                    when {
                        !isReachable -> "Open Shizuku first"
                        !isAuthorized -> "Grant access"
                        else -> "Retry binding"
                    }
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onVerify) { Text("Verify") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
