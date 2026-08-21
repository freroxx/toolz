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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.network.NetworkPowerUiState
import kotlin.math.roundToInt

/**
 * Feature cards salvaged from the retired NetworkPowerSuite Overview/Traffic tabs.
 * Each consumes [NetworkPowerUiState] from NetworkViewModel inside the unified suite.
 */

@Composable
internal fun PublicIpCard(
    state: NetworkPowerUiState,
    onRefresh: () -> Unit
) {
    GlassCard(
        title = "Public identity",
        icon = Icons.Rounded.Public,
        subtitle = "Exit IP as the internet sees it",
        trailing = {
            IconButton(onClick = onRefresh, enabled = !state.isRefreshingPublicIp) {
                if (state.isRefreshingPublicIp) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.CloudSync, contentDescription = "Refresh public IP")
                }
            }
        }
    ) {
        MetricRow("IP", state.publicIpInfo.ip)
        MetricRow("ISP", state.publicIpInfo.isp)
        if (state.publicIpInfo.country != "Unknown") {
            MetricRow("Country", state.publicIpInfo.country)
        }
    }
}

@Composable
internal fun DeviceMeshCard(
    state: NetworkPowerUiState,
    onScanDevices: () -> Unit
) {
    GlassCard(
        title = "Local mesh",
        icon = Icons.Rounded.Hub,
        subtitle = "${state.scannedDevices.size} devices discovered",
        trailing = {
            Button(onClick = onScanDevices, enabled = !state.isScanningDevices, shape = RoundedCornerShape(20.dp)) {
                Text(if (state.isScanningDevices) "Scanning" else "Scan")
            }
        }
    ) {
        NetworkMap(state)
    }
}

@Composable
internal fun MobileDataCard(
    state: NetworkPowerUiState,
    privilegedReady: Boolean,
    onToggle: (Boolean) -> Unit
) {
    GlassCard(title = "Mobile data", icon = Icons.Rounded.CellTower, subtitle = "Radio control") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(if (state.isDataEnabled) "Enabled" else "Disabled", fontWeight = FontWeight.Bold)
            Switch(
                checked = state.isDataEnabled,
                onCheckedChange = onToggle,
                thumbContent = {
                    if (!privilegedReady) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.height(12.dp))
                    }
                }
            )
        }
        Spacer(Modifier.height(10.dp))
        StatusBadge("Privileged", if (privilegedReady) "Bound" else "Locked")
    }
}

@Composable
internal fun PortScanCard(
    state: NetworkPowerUiState,
    onScanPorts: () -> Unit
) {
    GlassCard(title = "Gateway probe", icon = Icons.Rounded.Security, subtitle = "Common service ports") {
        OutlinedButton(onClick = onScanPorts, enabled = !state.isScanningPorts, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Text(if (state.isScanningPorts) "Probing…" else "Probe gateway")
        }
        val open = state.scannedPorts.filter { it.isOpen }
        if (open.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            open.forEach { p ->
                StatusBadge("${p.port}", "${p.service}${p.latencyMs?.let { " · ${it}ms" } ?: ""}")
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
internal fun LatencyStreamCard(state: NetworkPowerUiState) {
    GlassCard(title = "Latency stream", icon = Icons.Rounded.Timeline, subtitle = "Rolling gateway samples with jitter alert") {
        LatencyChart(samples = state.pingSamples, modifier = Modifier.fillMaxWidth().height(148.dp))
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiniMetric("Latency", "${state.stabilityInfo.avgLatency}ms")
            MiniMetric("Jitter", "${state.stabilityInfo.jitter}ms")
            MiniMetric("Loss", "${state.stabilityInfo.packetLoss.roundToInt()}%")
        }
        if (state.stabilityInfo.jitter >= 12) {
            Spacer(Modifier.height(10.dp))
            StatusBadge("Alert", "Jitter spike detected")
        }
    }
}

@Composable
internal fun CellularAuditCard(state: NetworkPowerUiState) {
    GlassCard(title = "Cellular audit", icon = Icons.Rounded.CellTower, subtitle = "Access tech and signal context") {
        MetricRow("Tech", state.cellularAudit.tech)
        MetricRow("Cell ID", state.cellularAudit.cellId)
        MetricRow("Signal", state.cellularAudit.signalStrength.take(24))
        state.cellularAudit.airplaneModeEnabled?.let { MetricRow("Airplane", if (it) "On" else "Off") }
    }
}

@Composable
internal fun RoutesAuditCard(state: NetworkPowerUiState) {
    GlassCard(title = "Routes & peers", icon = Icons.Rounded.Hub, subtitle = "Privileged route-table awareness") {
        if (state.ipAudit.routes.isEmpty() && state.ipAudit.neighbors.isEmpty()) {
            Text(
                "Loads automatically when Shizuku access is available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.ipAudit.routes.take(3).forEach { route ->
                StatusBadge("Route", route.take(42))
                Spacer(Modifier.height(6.dp))
            }
            state.ipAudit.neighbors.take(4).forEach { neighbor ->
                StatusBadge("Peer", neighbor.take(42))
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
internal fun SocketsCard(state: NetworkPowerUiState) {
    GlassCard(title = "Live sockets", icon = Icons.Rounded.Security, subtitle = "Established connections inventory") {
        if (state.activeProcesses.isEmpty()) {
            Text(
                "Waiting for socket state from the privileged layer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.activeProcesses, key = { "${it.localAddr}-${it.remoteAddr}" }) { process ->
                    TrafficRow(process)
                }
            }
        }
    }
}
