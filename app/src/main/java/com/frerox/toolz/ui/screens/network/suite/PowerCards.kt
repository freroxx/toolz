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
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.rounded.Hub
import com.frerox.toolz.ui.screens.network.suite.NetPill
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Router
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
    NetCard(
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
    val devices = state.scannedDevices
    val gateway = devices.firstOrNull { it.isGateway }
    val named = devices.count { it.hostname != "Unknown" }
    NetCard(
        title = "Local mesh",
        subtitle = when {
            state.isScanningDevices -> "Scanning 254 addresses…"
            devices.isEmpty() -> "Nothing scanned yet"
            else -> "${devices.size} device${if (devices.size == 1) "" else "s"} · $named named"
        },
        icon = Icons.Rounded.Hub,
        trailing = {
            Button(onClick = onScanDevices, enabled = !state.isScanningDevices, shape = RoundedCornerShape(50)) {
                if (state.isScanningDevices) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Scan")
                }
            }
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Found", "${devices.size}", modifier = Modifier.weight(1f))
            StatTile(
                "Gateway",
                gateway?.ip?.substringAfterLast(".")?.let { "…$it" } ?: "—",
                subvalue = gateway?.vendor?.takeIf { it != "Unknown" },
                modifier = Modifier.weight(1f)
            )
            StatTile("New", "${state.newDeviceIps.size}", tint = if (state.newDeviceIps.isEmpty()) null else MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
        }

        NetworkMap(state)

        if (devices.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                devices.sortedWith(
                    compareByDescending<com.frerox.toolz.data.network.NetworkDevice> { it.isGateway }
                        .thenByDescending { it.hostname != "Unknown" }
                        .thenBy { it.ip }
                ).take(8).forEach { dev ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                when {
                                    dev.isGateway -> Icons.Rounded.Router
                                    dev.typeLabel.contains("Printer", true) -> Icons.Rounded.Print
                                    dev.typeLabel.contains("Chromecast", true) || dev.typeLabel.contains("cast", true) -> Icons.Rounded.Cast
                                    else -> Icons.Rounded.Devices
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    dev.hostname.ifBlank { dev.ip }.let { if (it == "Unknown") dev.ip else it },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    buildString {
                                        append(dev.ip)
                                        dev.vendor.takeIf { it != "Unknown" }?.let { append(" · "); append(it) }
                                        dev.latencyMs?.let { append(" · ${it}ms") }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (dev.ip in state.newDeviceIps) NetPill("NEW", emphasized = true)
                            if (dev.isGateway) NetPill("GW", emphasized = true)
                        }
                    }
                }
                if (devices.size > 8) {
                    Text(
                        "+${devices.size - 8} more on this network",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun MobileDataCard(
    state: NetworkPowerUiState,
    privilegedReady: Boolean,
    onToggle: (Boolean) -> Unit
) {
    NetCard(
        title = "Mobile data",
        subtitle = when {
            !privilegedReady -> "Requires Shizuku"
            state.cellularAudit.mobileDataEnabled == null -> "State reported by system"
            else -> "System reports: " + if (state.isDataEnabled) "enabled" else "disabled"
        },
        icon = Icons.Rounded.CellTower,
        trailing = {
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
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NetPill(if (privilegedReady) "Shizuku bound" else "Shizuku locked", emphasized = privilegedReady)
            state.cellularAudit.tech.takeIf { it != "Unknown" }?.let { NetPill(it) }
        }
    }
}

@Composable
internal fun PortScanCard(
    state: NetworkPowerUiState,
    onScanPorts: () -> Unit
) {
    NetCard(title = "Gateway probe", icon = Icons.Rounded.Security, subtitle = "Common service ports") {
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
    NetCard(title = "Latency stream", icon = Icons.Rounded.Timeline, subtitle = "Rolling gateway samples with jitter alert") {
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
    NetCard(title = "Cellular audit", icon = Icons.Rounded.CellTower, subtitle = "Access tech and signal context") {
        MetricRow("Tech", state.cellularAudit.tech)
        MetricRow("Cell ID", state.cellularAudit.cellId)
        MetricRow("Signal", state.cellularAudit.signalStrength.take(24))
        state.cellularAudit.airplaneModeEnabled?.let { MetricRow("Airplane", if (it) "On" else "Off") }
    }
}

@Composable
internal fun RoutesAuditCard(state: NetworkPowerUiState) {
    NetCard(title = "Routes & peers", icon = Icons.Rounded.Hub, subtitle = "Privileged route-table awareness") {
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
    NetCard(title = "Live sockets", icon = Icons.Rounded.Security, subtitle = "Established connections inventory") {
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
