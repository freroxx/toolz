package com.frerox.toolz.ui.screens.network.suite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.frerox.toolz.data.network.NetworkPowerUiState
import com.frerox.toolz.data.network.ProcessNetworkUsage
import kotlin.math.roundToInt

@Composable
internal fun PublicIpCard(
    state: NetworkPowerUiState,
    onRefresh: () -> Unit
) {
    NetCard(
        title = "Public IP",
        subtitle = "What the internet sees",
        icon = Icons.Rounded.Public,
        trailing = {
            IconButton(onClick = onRefresh, enabled = !state.isRefreshingPublicIp) {
                if (state.isRefreshingPublicIp) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.CloudSync, contentDescription = "Refresh")
                }
            }
        }
    ) {
        if (state.publicIpInfo.ip == "Unknown" || state.isRefreshingPublicIp) {
            Text("Tap refresh to fetch your public address", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRowSimple("Address", state.publicIpInfo.ip)
                DetailRowSimple("Provider", state.publicIpInfo.isp)
                if (state.publicIpInfo.city != "Unknown" || state.publicIpInfo.country != "Unknown") {
                    DetailRowSimple("Location", "${state.publicIpInfo.city}, ${state.publicIpInfo.country}".trim().removePrefix(", "))
                }
                if (state.publicIpInfo.asn != "Unknown") {
                    DetailRowSimple("ASN", state.publicIpInfo.asn)
                }
            }
        }
    }
}

@Composable
private fun DetailRowSimple(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceMeshCard(
    state: NetworkPowerUiState,
    onScanDevices: () -> Unit,
    onScanPortsForHost: (String) -> Unit = {},
    onWakeHost: (String, String) -> Unit = { _, _ -> },
    hostPortResults: Map<String, List<com.frerox.toolz.data.network.ScannedPort>> = emptyMap(),
    hostPortScanning: Set<String> = emptySet()
) {
    val devices = state.scannedDevices
    val gateway = devices.firstOrNull { it.isGateway }
    val named = devices.count { it.hostname != "Unknown" }
    val subnet = gateway?.ip?.let { ip -> ip.substringBeforeLast(".") + ".0/24" } ?: state.wifiState.gateway.substringBeforeLast(".").let { if (it.contains(".")) "$it.0/24" else "—" }
    val clipboard = LocalClipboardManager.current
    var selectedDevice by remember { mutableStateOf<com.frerox.toolz.data.network.NetworkDevice?>(null) }

    NetCard(
        title = "Local network",
        subtitle = when {
            state.isScanningDevices -> "Scanning $subnet • ${devices.size} found"
            devices.isEmpty() -> "No scan yet • $subnet"
            else -> "${devices.size} devices • $named named • $subnet"
        },
        icon = Icons.Rounded.Hub,
        trailing = {
            Button(onClick = onScanDevices, enabled = !state.isScanningDevices, shape = RoundedCornerShape(50)) {
                if (state.isScanningDevices) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Scan")
                }
            }
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Devices", "${devices.size}", modifier = Modifier.weight(1f))
            StatTile("Gateway", gateway?.ip?.substringAfterLast(".")?.let { "…$it" } ?: "—", subvalue = gateway?.vendor?.takeIf { it != "Unknown" }, modifier = Modifier.weight(1f))
            StatTile("Named", "$named", modifier = Modifier.weight(1f))
        }

        if (devices.isNotEmpty() && state.newDeviceIps.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NetPill("${state.newDeviceIps.size} new", emphasized = true)
                Text("since last scan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }

        // Expressive mapping — label + improved canvas (clear hierarchy)
        Text("Topology", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        NetworkMap(state)

        if (devices.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                Text("No devices discovered yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Make sure you're on Wi-Fi and tap Scan. Devices that respond to ping will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Devices", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                devices.sortedWith(
                    compareByDescending<com.frerox.toolz.data.network.NetworkDevice> { it.isGateway }
                        .thenByDescending { it.ip in state.newDeviceIps }
                        .thenByDescending { it.hostname != "Unknown" }
                        .thenBy { it.ip }
                ).forEach { dev ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (dev.isGateway) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().clickable { selectedDevice = dev }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(36.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        when {
                                            dev.isGateway -> Icons.Rounded.Router
                                            dev.typeLabel.contains("Printer", true) -> Icons.Rounded.Print
                                            dev.typeLabel.contains("Chromecast", true) || dev.typeLabel.contains("cast", true) -> Icons.Rounded.Cast
                                            else -> Icons.Rounded.Devices
                                        },
                                        contentDescription = null,
                                        tint = if (dev.isGateway) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        dev.hostname.ifBlank { dev.ip }.let { if (it == "Unknown") dev.ip else it },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (dev.ip in state.newDeviceIps) NetPill("NEW", emphasized = true)
                                    if (dev.isGateway) NetPill("GW")
                                }
                                Text(
                                    dev.ip + " • " + (dev.vendor.takeIf { it != "Unknown" } ?: dev.typeLabel),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (dev.mac != "Unknown" && dev.mac.isNotBlank()) {
                                    Text(dev.mac, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                dev.latencyMs?.let { Text("${it}ms", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium) }
                                if (dev.isGateway) {
                                    AssistChip(onClick = {}, label = { Text("Router") }, enabled = false, colors = AssistChipDefaults.assistChipColors(disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedDevice != null) {
        ModalBottomSheet(onDismissRequest = { selectedDevice = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            val dev = selectedDevice!!
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(if (dev.isGateway) Icons.Rounded.Router else Icons.Rounded.Devices, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                    Column {
                        Text(dev.hostname.takeIf { it != "Unknown" } ?: dev.ip, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(dev.vendor.takeIf { it != "Unknown" } ?: dev.typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
                DetailRowSimple("IP", dev.ip)
                DetailRowSimple("MAC", dev.mac)
                DetailRowSimple("Vendor", dev.vendor)
                DetailRowSimple("Type", dev.typeLabel)
                dev.latencyMs?.let { DetailRowSimple("Latency", "${it}ms") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(dev.ip)) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy IP")
                    }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(dev.mac)) }, modifier = Modifier.weight(1f), enabled = dev.mac != "Unknown") {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy MAC")
                    }
                }

                // P9 WoL magic packet — no permission, huge utility win
                OutlinedButton(
                    onClick = { onWakeHost(dev.mac, dev.ip) },
                    enabled = dev.mac != "Unknown",
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.st_Net_WakeOnLan))
                }

                HorizontalDivider()

                // Per-host port scan
                val scanning = hostPortScanning.contains(dev.ip)
                val ports = hostPortResults[dev.ip]
                OutlinedButton(onClick = { onScanPortsForHost(dev.ip) }, enabled = !scanning, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) {
                    if (scanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Scanning ports…")
                    } else {
                        Text("Scan ports")
                    }
                }
                if (ports != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                        val open = ports.filter { it.isOpen }
                        if (open.isEmpty()) Text("No common ports open", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else open.forEach { p ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${p.port} · ${p.service}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("open${p.latencyMs?.let { " · ${it}ms" } ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
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
    val audit = state.cellularAudit
    val isEnabled = state.isDataEnabled
    NetCard(
        title = "Mobile data",
        subtitle = when {
            !privilegedReady -> "Shizuku required to toggle"
            audit.mobileDataEnabled == null -> "Current state unknown"
            audit.mobileDataEnabled == true -> "Enabled • ${audit.tech}"
            else -> "Disabled • ${audit.tech}"
        },
        icon = Icons.Rounded.CellTower,
        trailing = {
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                enabled = privilegedReady,
                thumbContent = {
                    if (!privilegedReady) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(12.dp))
                    }
                }
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NetPill(if (audit.tech != "Unknown") audit.tech else "Cellular")
                NetPill(if (isEnabled) "Active" else "Off", emphasized = isEnabled)
                if (audit.isAvailable) NetPill("Available") else NetPill("Unavailable")
            }
            // Clean parsed radio metrics — no raw dumpsys blobs
            if (audit.rsrpDbm != null || audit.rssiDbm != null || audit.snrDb != null || audit.rsrqDb != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        audit.rsrpDbm?.let { DetailRowSimple("RSRP", "$it dBm") }
                        audit.rsrqDb?.let { DetailRowSimple("RSRQ", "$it dB") }
                        audit.rssiDbm?.let { DetailRowSimple("RSSI", "$it dBm") }
                        audit.snrDb?.let { DetailRowSimple("SNR", "$it dB") }
                        if (audit.cellId != "Unknown" && audit.cellId.isNotBlank()) {
                            DetailRowSimple("Cell", audit.cellId)
                        }
                    }
                }
            } else if (audit.signalStrength != "Unknown" && audit.signalStrength.isNotBlank()) {
                Text("Signal: ${audit.signalStrength}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!privilegedReady) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Info, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Connect Shizuku to enable one-tap toggle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PortScanCard(
    state: NetworkPowerUiState,
    onScanPorts: () -> Unit
) {
    NetCard(title = "Gateway ports", icon = Icons.Rounded.Security, subtitle = "Quick check for open services on your router") {
        OutlinedButton(onClick = onScanPorts, enabled = !state.isScanningPorts, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) {
            if (state.isScanningPorts) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Scanning…")
            } else {
                Text("Scan gateway ports")
            }
        }
        val results = state.scannedPorts
        if (results.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                results.forEach { p ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(6.dp), color = if (p.isOpen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
                                Text("${p.port}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (p.isOpen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(p.service, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                        Text(if (p.isOpen) "open • ${p.latencyMs ?: "—"}ms" else "closed", style = MaterialTheme.typography.labelSmall, color = if (p.isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Text("Tap scan to check common ports (22, 53, 80, 443, etc.)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LatencyStreamCard(state: NetworkPowerUiState) {
    NetCard(title = "Latency", icon = Icons.Rounded.Timeline, subtitle = "Gateway ping over time") {
        if (state.pingSamples.isEmpty() || state.pingSamples.all { it.latencyMs == null }) {
            Text("Collecting samples… keep this screen open", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LatencyChart(samples = state.pingSamples, modifier = Modifier.fillMaxWidth().height(96.dp))
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniMetric("Avg", "${state.stabilityInfo.avgLatency}ms")
                MiniMetric("Jitter", "${state.stabilityInfo.jitter}ms")
                MiniMetric("Loss", "${state.stabilityInfo.packetLoss.roundToInt()}%")
            }
            if (state.stabilityInfo.jitter >= 12) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text("Jitter spike — connection unstable", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
internal fun CellularAuditCard(state: NetworkPowerUiState) {
    val a = state.cellularAudit
    NetCard(title = "Cellular", icon = Icons.Rounded.CellTower, subtitle = a.tech.takeIf { it != "Unknown" } ?: "Radio details") {
        if (!a.isAvailable) {
            Text("No cellular radio reported", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (a.rsrpDbm != null) DetailRowSimple("RSRP", "${a.rsrpDbm} dBm")
                if (a.rsrqDb != null) DetailRowSimple("RSRQ", "${a.rsrqDb} dB")
                if (a.rssiDbm != null) DetailRowSimple("RSSI", "${a.rssiDbm} dBm")
                if (a.snrDb != null) DetailRowSimple("SNR", "${a.snrDb} dB")
                if (a.cellId != "Unknown") DetailRowSimple("Cell ID", a.cellId)
                if (a.tac != "Unknown") DetailRowSimple("TAC", a.tac)
                a.mobileDataEnabled?.let { DetailRowSimple("Mobile data", if (it) "Enabled" else "Disabled") }
                a.airplaneModeEnabled?.let { DetailRowSimple("Airplane mode", if (it) "On" else "Off") }
            }
        }
    }
}

@Composable
internal fun RoutesAuditCard(state: NetworkPowerUiState) {
    NetCard(title = "Routes", icon = Icons.Rounded.Hub, subtitle = "System routing table") {
        if (state.ipAudit.routes.isEmpty() && state.ipAudit.neighbors.isEmpty()) {
            Text("Available with Shizuku — route table and ARP peers will appear here", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.ipAudit.routes.isNotEmpty()) {
                    Text("Routes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.ipAudit.routes.take(5).forEach { r ->
                        Text(r, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
                    }
                }
                if (state.ipAudit.neighbors.isNotEmpty()) {
                    Text("Neighbors", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    state.ipAudit.neighbors.take(6).forEach { n ->
                        Text(n, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SocketsCard(state: NetworkPowerUiState) {
    NetCard(title = "Active sockets", icon = Icons.Rounded.Security, subtitle = "Live connections (requires Shizuku for full list)") {
        if (state.activeProcesses.isEmpty()) {
            Text(
                "No active sockets or Shizuku not bound. Open an app that uses network and rescan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.activeProcesses.take(12).forEach { p ->
                    TrafficRow(p)
                }
                if (state.activeProcesses.size > 12) {
                    Text("+${state.activeProcesses.size - 12} more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}


