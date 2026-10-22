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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.frerox.toolz.ui.screens.calendar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.frerox.toolz.data.calendar.CalDavSyncClient
import com.frerox.toolz.data.calendar.UserCalendar
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton
import com.frerox.toolz.ui.components.ToolzWavyCircularProgressIndicator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CalendarSettingsSheet(
    onDismiss: () -> Unit,
    viewModel: CalendarViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var showClearAllDialog by remember { mutableStateOf(false) }

    // Permission launcher for phone calendar
    var hasCalendarPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCalendarPermission = granted
        if (granted) {
            viewModel.loadPhoneCalendars()
        }
    }

    // File picker for .ics import
    val icsPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importIcsFromUri(it) }
    }

    // Share export helper
    fun shareExport(uri: Uri) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Export Calendar"))
    }

    LaunchedEffect(hasCalendarPermission) {
        if (hasCalendarPermission && uiState.phoneCalendars.isEmpty()) {
            viewModel.loadPhoneCalendars()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MediumExpressiveShape,
        dragHandle = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(36.dp, 4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                            CircleShape
                        )
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Calendar Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Import, export, sync & customize",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Syncing bar indicator
            AnimatedVisibility(visible = uiState.isSyncing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(CircleShape)
                )
            }

            // Status message banner
            uiState.settingsMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = SmallExpressiveShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearSettingsMessage() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Scrollable settings body
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // ─────────────────────────────────────────────────────────────
                // OVERVIEW & SYNC DASHBOARD
                // ─────────────────────────────────────────────────────────────
                item {
                    Card(
                        shape = MediumExpressiveShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "${uiState.events.size} Events Stored",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (uiState.lastSyncMs > 0) {
                                        "Last synced: " + SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(uiState.lastSyncMs))
                                    } else {
                                        "Never synced"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            ToolzExpressiveButton(
                                onClick = { viewModel.syncAllNow() },
                                enabled = !uiState.isSyncing
                            ) {
                                Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sync All")
                            }
                        }
                    }
                }

                // ─────────────────────────────────────────────────────────────
                // SECTION: Calendars (Create & Manage)
                // ─────────────────────────────────────────────────────────────
                item {
                    SettingsSection(
                        title = "My Calendars",
                        icon = Icons.Rounded.CalendarMonth,
                        subtitle = "Create custom calendars and export them independently"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Calendars list
                            uiState.userCalendars.forEach { cal ->
                                CalendarRowItem(
                                    calendar = cal,
                                    onExport = {
                                        viewModel.exportCalendarAsIcs(cal.name) { shareExport(it) }
                                    },
                                    onDelete = if (uiState.userCalendars.size > 1) {
                                        { viewModel.removeUserCalendar(cal.id) }
                                    } else null
                                )
                            }

                            // Create new calendar section
                            CreateCalendarCard(onCreate = { name, color ->
                                viewModel.addUserCalendar(name, color)
                            })
                        }
                    }
                }

                // ─────────────────────────────────────────────────────────────
                // SECTION: Universal Import & Export (ICS)
                // ─────────────────────────────────────────────────────────────
                item {
                    SettingsSection(
                        title = "Import & Export",
                        icon = Icons.Rounded.ImportExport,
                        subtitle = "Open RFC 5545 iCalendar standard (.ics). Compatible with Google, Apple, Outlook, Nextcloud & Thunderbird"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ToolzOutlinedExpressiveButton(
                                onClick = {
                                    icsPickerLauncher.launch(arrayOf("text/calendar", "*/*"))
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import .ics", fontWeight = FontWeight.Bold)
                            }

                            ToolzExpressiveButton(
                                onClick = {
                                    viewModel.exportCalendarAsIcs("All") { shareExport(it) }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Upload, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export .ics", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // ─────────────────────────────────────────────────────────────
                // SECTION: Phone Calendar Sync
                // ─────────────────────────────────────────────────────────────
                item {
                    SettingsSection(
                        title = "Phone Calendars",
                        icon = Icons.Rounded.PhoneAndroid,
                        subtitle = "Read and import events from Android's system calendars (Google, Device, etc.)"
                    ) {
                        if (!hasCalendarPermission) {
                            Card(
                                shape = SmallExpressiveShape,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Calendar access needed",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Grant calendar read permission to import events from your phone's existing calendar accounts.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    ToolzExpressiveButton(
                                        onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Grant Permission")
                                    }
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (uiState.phoneCalendars.isEmpty()) {
                                    Text(
                                        text = "No phone calendars detected yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    ToolzOutlinedExpressiveButton(
                                        onClick = { viewModel.loadPhoneCalendars() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Scan for Calendars")
                                    }
                                } else {
                                    uiState.phoneCalendars.forEach { pCal ->
                                        val isSelected = uiState.selectedPhoneCalendarIds.contains(pCal.id)
                                        Card(
                                            shape = SmallExpressiveShape,
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainer
                                                }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.togglePhoneCalendar(pCal.id) }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .background(Color(pCal.color), CircleShape)
                                                    )
                                                    Column {
                                                        Text(
                                                            text = pCal.displayName,
                                                            fontWeight = FontWeight.SemiBold,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                        if (pCal.accountName.isNotBlank()) {
                                                            Text(
                                                                text = pCal.accountName,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { viewModel.togglePhoneCalendar(pCal.id) }
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        ToolzOutlinedExpressiveButton(
                                            onClick = { viewModel.loadPhoneCalendars() },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Refresh")
                                        }
                                        ToolzExpressiveButton(
                                            onClick = { viewModel.importSelectedPhoneCalendars() },
                                            enabled = uiState.selectedPhoneCalendarIds.isNotEmpty(),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Import (${uiState.selectedPhoneCalendarIds.size})")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ─────────────────────────────────────────────────────────────
                // SECTION: CalDAV Cloud Sync
                // ─────────────────────────────────────────────────────────────
                item {
                    SettingsSection(
                        title = "CalDAV Cloud Sync",
                        icon = Icons.Rounded.CloudSync,
                        subtitle = "Open protocol (RFC 4791) for Nextcloud, ownCloud, Radicale, Baikal, Fastmail, etc."
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Accounts list
                            uiState.calDavAccounts.forEach { acc ->
                                Card(
                                    shape = SmallExpressiveShape,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = acc.displayName.ifBlank { "CalDAV Account" },
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "${acc.username} @ ${acc.url}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = { viewModel.syncCalDavAccount(acc) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Sync,
                                                    contentDescription = "Sync Now",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.removeCalDavAccount(acc.id) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Add CalDAV Account Form
                            AddCalDavCard(
                                onTest = { url, u, p, name, callback ->
                                    viewModel.testCalDav(
                                        CalDavSyncClient.CalDavAccount(url = url, username = u, password = p, displayName = name),
                                        callback
                                    )
                                },
                                onSave = { url, u, p, name ->
                                    viewModel.addCalDavAccount(url, u, p, name)
                                }
                            )
                        }
                    }
                }

                // ─────────────────────────────────────────────────────────────
                // SECTION: Auto-Sync
                // ─────────────────────────────────────────────────────────────
                item {
                    SettingsSection(
                        title = "Auto-Sync",
                        icon = Icons.Rounded.Update,
                        subtitle = "Periodic background synchronization using WorkManager"
                    ) {
                        Card(
                            shape = SmallExpressiveShape,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Automatic background sync",
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = if (uiState.lastSyncMs > 0) {
                                                val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                                "Last synced: ${fmt.format(Date(uiState.lastSyncMs))}"
                                            } else {
                                                "Not synced yet"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = uiState.autoSyncEnabled,
                                        onCheckedChange = { viewModel.setAutoSyncEnabled(it) }
                                    )
                                }

                                if (uiState.autoSyncEnabled) {
                                    Text(
                                        text = "Sync Frequency",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    val intervals = listOf(
                                        1 to "1 hr",
                                        3 to "3 hrs",
                                        6 to "6 hrs",
                                        12 to "12 hrs",
                                        24 to "24 hrs"
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        intervals.forEach { (hrs, label) ->
                                            val isSelected = uiState.autoSyncIntervalHours == hrs
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { viewModel.setAutoSyncInterval(hrs) },
                                                label = { Text(label) },
                                                leadingIcon = if (isSelected) {
                                                    { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                        }
                                    }
                                }

                                ToolzOutlinedExpressiveButton(
                                    onClick = { viewModel.syncAllNow() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sync All Now")
                                }
                            }
                        }
                    }
                }

                // ─────────────────────────────────────────────────────────────
                // SECTION: Data Management & Clean Up
                // ─────────────────────────────────────────────────────────────
                item {
                    SettingsSection(
                        title = "Data Management",
                        icon = Icons.Rounded.DeleteSweep,
                        subtitle = "Clean up duplicates or wipe stored calendar events"
                    ) {
                        Card(
                            shape = SmallExpressiveShape,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Remove Duplicates",
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Find and remove redundant event copies created by repeated syncs",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    ToolzOutlinedExpressiveButton(
                                        onClick = { viewModel.removeDuplicates() },
                                        enabled = !uiState.isSyncing
                                    ) {
                                        Icon(Icons.Rounded.CleaningServices, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Deduplicate")
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Clear All Events",
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "Permanently delete all events from local calendar storage",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    ToolzExpressiveButton(
                                        onClick = { showClearAllDialog = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    ) {
                                        Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Clear All")
                                    }
                                }
                            }
                        }
                    }
                }

                // ─────────────────────────────────────────────────────────────
                // SECTION: Display Preferences
                // ─────────────────────────────────────────────────────────────
                item {
                    SettingsSection(
                        title = "Display Preferences",
                        icon = Icons.Rounded.Tune,
                        subtitle = "Calendar layout and numbering options"
                    ) {
                        Card(
                            shape = SmallExpressiveShape,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "First day of week",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val days = listOf(
                                            Calendar.MONDAY to "Monday",
                                            Calendar.SUNDAY to "Sunday",
                                            Calendar.SATURDAY to "Saturday"
                                        )
                                        days.forEach { (dayInt, label) ->
                                            val isSel = uiState.firstDayOfWeek == dayInt
                                            FilterChip(
                                                selected = isSel,
                                                onClick = { viewModel.setFirstDayOfWeek(dayInt) },
                                                label = { Text(label, fontSize = 12.sp) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Show week numbers",
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Display ISO week number in agenda and views",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = uiState.showWeekNumbers,
                                        onCheckedChange = { viewModel.setShowWeekNumbers(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Clear All Events?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will permanently delete all ${uiState.events.size} calendar events from local storage. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllEvents()
                        showClearAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete Everything", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Subcomponents
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun CalendarRowItem(
    calendar: UserCalendar,
    onExport: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val calColor = runCatching { Color(android.graphics.Color.parseColor(calendar.color)) }.getOrDefault(Color.Blue)
    Card(
        shape = SmallExpressiveShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(calColor, CircleShape)
                )
                Text(
                    text = calendar.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onExport,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.Upload,
                        contentDescription = "Export .ics",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateCalendarCard(
    onCreate: (name: String, color: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var calName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(EventColorPalette.first()) }

    Card(
        shape = SmallExpressiveShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (!expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Create New Calendar",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "New Calendar",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )

                    OutlinedTextField(
                        value = calName,
                        onValueChange = { calName = it },
                        label = { Text("Calendar Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallExpressiveShape
                    )

                    // Color picker
                    Text(
                        "Calendar Color",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EventColorPalette.forEach { hex ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            val isSelected = selectedColor == hex
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { expanded = false }) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        ToolzExpressiveButton(
                            onClick = {
                                if (calName.isNotBlank()) {
                                    onCreate(calName.trim(), selectedColor)
                                    calName = ""
                                    expanded = false
                                }
                            },
                            enabled = calName.isNotBlank()
                        ) {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCalDavCard(
    onTest: (url: String, u: String, p: String, name: String, callback: (String) -> Unit) -> Unit,
    onSave: (url: String, u: String, p: String, name: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Card(
        shape = SmallExpressiveShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (!expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Add CalDAV Account",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "CalDAV Account Setup",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name (optional)") },
                        placeholder = { Text("Nextcloud Personal") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallExpressiveShape
                    )

                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("https://cloud.example.com/remote.php/dav/calendars/user/calendar/") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallExpressiveShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallExpressiveShape
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password or App Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallExpressiveShape,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        }
                    )

                    testResult?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (msg.startsWith("Connected")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (url.isNotBlank() && username.isNotBlank()) {
                                    isTesting = true
                                    testResult = "Connecting..."
                                    onTest(url, username, password, displayName) { res ->
                                        isTesting = false
                                        testResult = res
                                    }
                                }
                            },
                            enabled = url.isNotBlank() && username.isNotBlank() && !isTesting
                        ) {
                            Text("Test Connection")
                        }

                        Row {
                            TextButton(onClick = { expanded = false }) {
                                Text("Cancel")
                            }
                            Spacer(Modifier.width(8.dp))
                            ToolzExpressiveButton(
                                onClick = {
                                    if (url.isNotBlank() && username.isNotBlank()) {
                                        onSave(url, username, password, displayName)
                                        expanded = false
                                        url = ""
                                        username = ""
                                        password = ""
                                        displayName = ""
                                        testResult = null
                                    }
                                },
                                enabled = url.isNotBlank() && username.isNotBlank()
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }
}
