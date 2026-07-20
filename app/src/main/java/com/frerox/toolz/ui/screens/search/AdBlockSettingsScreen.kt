package com.frerox.toolz.ui.screens.search

import androidx.compose.animation.*
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
            // ── NextDNS Section ───────────────────────────────────────────────
            item {
                SettingsSection(title = "NextDNS Integration", icon = Icons.Rounded.Dns) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Connect your NextDNS account to use your own custom blocklists and analytics.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Button(
                            onClick = { onNavigateToNextDnsSetup("https://my.nextdns.io") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.OpenInBrowser, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open NextDNS Setup")
                        }

                        OutlinedTextField(
                            value = uiState.nextDnsId,
                            onValueChange = viewModel::setNextDnsId,
                            label = { Text("NextDNS Configuration ID") },
                            placeholder = { Text("e.g. abcdef") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = uiState.nextDnsUrl,
                            onValueChange = viewModel::setNextDnsUrl,
                            label = { Text("Custom DoH URL") },
                            placeholder = { Text("https://dns.nextdns.io/abcdef") },
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
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
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
