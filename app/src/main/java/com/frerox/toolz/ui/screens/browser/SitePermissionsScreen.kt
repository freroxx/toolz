package com.frerox.toolz.ui.screens.browser

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.browser.BrowserPermissionType
import com.frerox.toolz.data.browser.BrowserSitePermission
import com.frerox.toolz.ui.components.LargeExpressiveShape

@Composable
fun SitePermissionsScreen(
    onBack: () -> Unit,
    viewModel: WebViewViewModel = hiltViewModel()
) {
    var refresh by remember { mutableStateOf(0) }
    val allPerms = remember(refresh) { viewModel.getAllSitePermissions() }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(allPerms, searchQuery) {
        if (searchQuery.isBlank()) allPerms
        else allPerms.filter { (host, _) -> host.contains(searchQuery, ignoreCase = true) }
    }
    val totalPerms = allPerms.values.sumOf { it.size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top chrome (matches search home screen style) ────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledIconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack, null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Site permissions",
                            style = typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (allPerms.isEmpty()) "Nothing to manage yet"
                            else "$totalPerms permission${if (totalPerms == 1) "" else "s"} across ${allPerms.size} site${if (allPerms.size == 1) "" else "s"}",
                            style = typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    if (allPerms.isNotEmpty()) {
                        TextButton(onClick = {
                            viewModel.clearAllSitePermissions()
                            refresh++
                        }) {
                            Icon(Icons.Rounded.DeleteSweep, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clear all", style = typography.labelLarge)
                        }
                    }
                }

                // Search pill (matches search screen's rounded pill)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Search, null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (searchQuery.isBlank()) {
                                    Text(
                                        "Search sites",
                                        style = typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }
            }
        }

        // ── Content ──────────────────────────────────────────────────────────
        if (filtered.isEmpty()) {
            EmptyPermissionsState(hasAny = allPerms.isNotEmpty(), isFiltering = searchQuery.isNotBlank())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered.entries.toList(), key = { it.key }) { (host, perms) ->
                    SitePermissionCard(
                        host = host,
                        perms = perms,
                        onToggle = { type, newPerm ->
                            val origin = "https://$host"
                            viewModel.setSitePermission(origin, type, newPerm)
                            refresh++
                        },
                        onClear = {
                            viewModel.resetSitePermission("https://$host")
                            refresh++
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPermissionsState(hasAny: Boolean, isFiltering: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(76.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Shield, null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
            Text(
                if (isFiltering) "No matching sites" else "No site permissions",
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (isFiltering) "Try a different search term."
                else "Sites you grant camera, microphone, notification or location access to will appear here.",
                style = typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun SitePermissionCard(
    host: String,
    perms: Map<BrowserPermissionType, BrowserSitePermission>,
    onToggle: (BrowserPermissionType, BrowserSitePermission) -> Unit,
    onClear: () -> Unit
) {
    Surface(
        shape = LargeExpressiveShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            host.take(1).uppercase(),
                            style = typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        host,
                        style = typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${perms.size} permission${if (perms.size == 1) "" else "s"}",
                        style = typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                TextButton(
                    onClick = onClear,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Rounded.DeleteSweep, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear", style = typography.labelSmall)
                }
            }

            // Permission rows
            perms.forEach { (type, perm) ->
                PermissionRow(
                    type = type,
                    perm = perm,
                    onToggle = { onToggle(type, it) },
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    type: BrowserPermissionType,
    perm: BrowserSitePermission,
    onToggle: (BrowserSitePermission) -> Unit,
) {
    val isAllowed = perm == BrowserSitePermission.ALLOW
    val (icon, label) = when (type) {
        BrowserPermissionType.CAMERA -> Icons.Rounded.Videocam to "Camera"
        BrowserPermissionType.MICROPHONE -> Icons.Rounded.Mic to "Microphone"
        BrowserPermissionType.NOTIFICATION -> Icons.Rounded.Notifications to "Notifications"
        BrowserPermissionType.GEOLOCATION -> Icons.Rounded.LocationOn to "Location"
    }
    val containerColor = if (isAllowed) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
    }
    val contentColor = if (isAllowed) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                icon, null,
                modifier = Modifier.size(18.dp),
                tint = if (isAllowed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
            )
            Text(
                label,
                style = typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            // Toggle pill — simple two-state allow/block switch
            Surface(
                onClick = { onToggle(if (isAllowed) BrowserSitePermission.DENY else BrowserSitePermission.ALLOW) },
                shape = RoundedCornerShape(50),
                color = containerColor,
                modifier = Modifier.animateContentSize(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        if (isAllowed) Icons.Rounded.CheckCircle else Icons.Rounded.Block,
                        null,
                        modifier = Modifier.size(13.dp),
                        tint = contentColor,
                    )
                    Text(
                        if (isAllowed) "Allowed" else "Blocked",
                        style = typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                    )
                }
            }
        }
    }
}
