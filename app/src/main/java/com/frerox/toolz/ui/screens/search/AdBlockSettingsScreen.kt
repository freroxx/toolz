package com.frerox.toolz.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.ui.components.ExpressiveFilterChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdBlockSettingsScreen(
    onBack: () -> Unit,
    onNavigateToNextDnsSetup: (String) -> Unit,
    viewModel: AdBlockSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var newBlockedDomain by remember { mutableStateOf("") }
    var newAllowedDomain by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ad Block Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Import Popular Lists ──────────────────────────────────────────
            item {
                SettingsSection(
                    title = "Import Popular Lists",
                    icon = Icons.Rounded.CloudDownload
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Community Blocklists",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                contentColor = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    "BETA",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            
                            Spacer(Modifier.weight(1f))
                            
                            if (uiState.importedDomainCount > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "${uiState.importedDomainCount} domains blocked",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        Text(
                            "Sync high-quality community-maintained blocklists for maximum protection.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AdBlockSettingsViewModel.POPULAR_LISTS.forEach { (id, _) ->
                                val enabled = uiState.enabledImportedLists.contains(id)
                                val label = when(id) {
                                    "OISD_BASIC" -> "OISD Basic (Recommended)"
                                    "ADGUARD_BASE" -> "AdGuard DNS Filter"
                                    "STEVENBLACK" -> "StevenBlack Unified"
                                    "EASYLIST" -> "EasyList"
                                    "FANBOY_ANNOYANCE" -> "Fanboy's Annoyance"
                                    "LIGHTSWITCH" -> "Lightswitch"
                                    "NOTRACK" -> "NoTrack Tracking"
                                    else -> id.lowercase().replaceFirstChar(Char::uppercase)
                                }
                                
                                val description = when(id) {
                                    "OISD_BASIC" -> "High reliability, low false positives"
                                    "ADGUARD_BASE" -> "Comprehensive ads + tracking"
                                    "STEVENBLACK" -> "Adware + malware + telemetry"
                                    "EASYLIST" -> "Primary web ad filter"
                                    "FANBOY_ANNOYANCE" -> "Blocks popups, cookie banners & widgets"
                                    "LIGHTSWITCH" -> "Aggressive telemetry & tracking blocker"
                                    "NOTRACK" -> "Focus on privacy and telemetry"
                                    else -> ""
                                }
                                
                                Surface(
                                    onClick = { viewModel.toggleImportedList(id) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = if (!enabled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            if (enabled) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                                            null,
                                            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                label,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (enabled) FontWeight.Bold else FontWeight.Medium
                                            )
                                            if (description.isNotEmpty()) {
                                                Text(
                                                    description,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.syncImportedLists(uiState.enabledImportedLists) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isFetching && uiState.enabledImportedLists.isNotEmpty(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (uiState.isFetching) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(10.dp))
                                Text("Fetching...")
                            } else {
                                Icon(Icons.Rounded.Sync, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Sync Enabled Lists")
                            }
                        }
                    }
                }
            }

            // ── NextDNS Section ───────────────────────────────────────────────
            item {
                SettingsSection(
                    title = "NextDNS Integration", 
                    icon = Icons.Rounded.Dns,
                    trailing = {
                        Switch(
                            checked = uiState.isNextDnsEnabled,
                            onCheckedChange = viewModel::toggleNextDns
                        )
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Status:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            val (color, label) = when (uiState.nextDnsHealth) {
                                NextDnsHealth.CONNECTED -> Color(0xFF4CAF50) to "Connected"
                                NextDnsHealth.NOT_LINKED -> Color(0xFFFFC107) to "Not Linked"
                                NextDnsHealth.ERROR -> Color(0xFFF44336) to "Error"
                                NextDnsHealth.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant to "Checking..."
                            }
                            
                            Surface(
                                color = color.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    label,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = color,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            "Connect your NextDNS account to use your own custom blocklists and analytics.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Button(
                            onClick = { onNavigateToNextDnsSetup("https://my.nextdns.io") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Icon(Icons.Rounded.OpenInBrowser, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Setup on NextDNS.io")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = uiState.nextDnsId,
                                onValueChange = viewModel::setNextDnsId,
                                label = { Text("NextDNS Configuration ID") },
                                placeholder = { Text("e.g. abcdef") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true
                            )
                            
                            IconButton(
                                onClick = { viewModel.applyNextDnsConfig() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (uiState.isNextDnsEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.size(56.dp).padding(top = 8.dp),
                                enabled = uiState.nextDnsId.isNotBlank()
                            ) {
                                Icon(if (uiState.isNextDnsEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.Check, null)
                            }
                        }

                        OutlinedTextField(
                            value = uiState.nextDnsUrl,
                            onValueChange = viewModel::setNextDnsUrl,
                            label = { Text("Custom DoH Hostname") },
                            placeholder = { Text("e.g. 221e93.dns.nextdns.io") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                    }
                }
            }

            // ── Custom Blocklist ─────────────────────────────────────────────
            item {
                SettingsSection(title = "Custom Blocklist", icon = Icons.Rounded.Block) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newBlockedDomain,
                                onValueChange = { newBlockedDomain = it },
                                placeholder = { Text("example.com") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            IconButton(
                                onClick = { viewModel.addBlockedDomain(newBlockedDomain); newBlockedDomain = "" },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(Icons.Rounded.Add, null)
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.blocklists.forEach { domain ->
                                DomainChip(domain = domain, onRemove = { viewModel.removeBlockedDomain(domain) }, color = MaterialTheme.colorScheme.errorContainer)
                            }
                        }
                    }
                }
            }

            // ── Custom Allowlist ─────────────────────────────────────────────
            item {
                SettingsSection(title = "Allowlist (Exceptions)", icon = Icons.Rounded.CheckCircle) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newAllowedDomain,
                                onValueChange = { newAllowedDomain = it },
                                placeholder = { Text("trusted.com") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            IconButton(
                                onClick = { viewModel.addAllowedDomain(newAllowedDomain); newAllowedDomain = "" },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(Icons.Rounded.Add, null)
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.allowlists.forEach { domain ->
                                DomainChip(domain = domain, onRemove = { viewModel.removeAllowedDomain(domain) }, color = MaterialTheme.colorScheme.secondaryContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            Box(modifier = Modifier.padding(20.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun DomainChip(domain: String, onRemove: () -> Unit, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(domain, style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable FlowRowScope.() -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = content
    )
}
