/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.frerox.toolz.ui.screens.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.data.clipboard.ClipboardGate
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ─── Type metadata (restrained palette) ──────────────────────────────────────

private data class TypeMeta(val icon: ImageVector, val label: String)

private fun typeMeta(type: String): TypeMeta = when (type) {
    "URL" -> TypeMeta(Icons.Rounded.Language, "Link")
    "SOCIAL" -> TypeMeta(Icons.Rounded.Public, "Social")
    "PHONE" -> TypeMeta(Icons.Rounded.Call, "Phone")
    "OTP" -> TypeMeta(Icons.Rounded.Lock, "Code")
    "EMAIL" -> TypeMeta(Icons.Rounded.Mail, "Email")
    "MATHS" -> TypeMeta(Icons.Rounded.Functions, "Math")
    "CODE" -> TypeMeta(Icons.Rounded.Terminal, "Code")
    "ADDRESS" -> TypeMeta(Icons.Rounded.Place, "Address")
    "CRYPTO" -> TypeMeta(Icons.Rounded.CurrencyBitcoin, "Crypto")
    "TODO" -> TypeMeta(Icons.Rounded.Checklist, "Task")
    "COLOR" -> TypeMeta(Icons.Rounded.Palette, "Color")
    else -> TypeMeta(Icons.Rounded.Notes, type.lowercase().replaceFirstChar { it.uppercase() })
}

private fun Long.toRelativeTime(): String {
    val diff = System.currentTimeMillis() - this
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000}h ago"
        diff < 604_800_000L -> "${diff / 86_400_000}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(this))
    }
}

private fun Long.toFullTimestamp(): String =
    SimpleDateFormat("EEE, MMM d 'at' HH:mm", Locale.getDefault()).format(Date(this))

// ─── Root ────────────────────────────────────────────────────────────────────

@Composable
fun ClipboardScreen(
    viewModel: ClipboardViewModel,
    onBack: () -> Unit,
    onConvertToTask: (String) -> Unit = {},
) {
    val filteredEntries by viewModel.filteredEntries.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val isSummarizingId by viewModel.isSummarizing.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val gateStatus by viewModel.gateStatus.collectAsStateWithLifecycle()
    val monitoringEnabled by viewModel.monitoringEnabled.collectAsStateWithLifecycle()
    val canShowAi by viewModel.canShowAi.collectAsStateWithLifecycle()
    val autoAi by viewModel.autoAiEnabled.collectAsStateWithLifecycle()
    val retentionDays by viewModel.retentionDays.collectAsStateWithLifecycle()
    val excludeSensitive by viewModel.excludeSensitive.collectAsStateWithLifecycle()
    val clipboardAiMaster by viewModel.clipboardAiEnabled.collectAsStateWithLifecycle()
    val accessibilityOn by viewModel.accessibilityEnabled.collectAsStateWithLifecycle()
    val shizukuOn by viewModel.shizukuAuthorized.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val vibration = LocalVibrationManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val groups = remember(filteredEntries) { viewModel.groupedEntries(filteredEntries) }

    var selectedEntry by remember { mutableStateOf<ClipboardEntry?>(null) }
    var activeFilter by remember { mutableStateOf<String?>(null) } // null = all, "PINNED" = pinned
    var showClearDialog by remember { mutableStateOf(false) }
    var showShizukuSetup by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshGate()
        viewModel.refreshClipboard()
        viewModel.ensureServiceState()
    }

    // ViewModel one-shot messages -> snackbar
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                "saved" -> snackbar.showSnackbar("Saved to history")
                "bumped" -> snackbar.showSnackbar("Already in history — moved to top")
                "sensitive_skipped" -> snackbar.showSnackbar("Skipped: looks like a password or code")
                "clipboard_empty" -> snackbar.showSnackbar("Clipboard is empty")
                "non_text_clip" -> snackbar.showSnackbar("Only text clips are stored")
                "clipboard_denied" -> snackbar.showSnackbar("Clipboard blocked — keep Toolz in foreground and retry")
                "open_app_to_paste" -> snackbar.showSnackbar("Open Toolz first, then paste")
                "deleted" -> {
                    val res = snackbar.showSnackbar("Deleted", actionLabel = "Undo")
                    if (res == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                }
            }
        }
    }

    fun copyToClipboard(entry: ClipboardEntry) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Toolz Clip", entry.content))
        vibration?.vibrateClick()
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    val displayGroups = remember(groups, activeFilter) {
        when (activeFilter) {
            null -> groups
            "PINNED" -> groups.filter { it.label == "Pinned" }
            else -> groups.map { g -> g.copy(entries = g.entries.filter { it.type == activeFilter }) }
                .filter { it.entries.isNotEmpty() }
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().toolzBackground().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            ExpressiveTopAppBar(
                title = "Clipboard",
                subtitle = when {
                    entries.isEmpty() -> "No clips yet"
                    entries.size == 1 -> "1 clip"
                    else -> "${entries.size} clips"
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.pasteCurrent() }) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste current")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (monitoringEnabled) "Pause monitoring" else "Resume monitoring") },
                                onClick = {
                                    showMenu = false
                                    viewModel.setMonitoringEnabled(!monitoringEnabled)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Clipboard settings") },
                                onClick = { showMenu = false; showSettings = true },
                            )
                            if (entries.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Clear unpinned") },
                                    onClick = { showMenu = false; showClearDialog = true },
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.pasteCurrent() },
                icon = { Icon(Icons.Rounded.ContentPaste, null) },
                text = { Text("Paste", fontWeight = FontWeight.Bold) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "status") {
                ClipboardStatusCard(
                    status = gateStatus,
                    monitoringEnabled = monitoringEnabled,
                    shizukuOn = shizukuOn,
                    accessibilityOn = accessibilityOn,
                    onToggleMonitoring = { viewModel.setMonitoringEnabled(!monitoringEnabled) },
                    onSetupShizuku = { showShizukuSetup = true },
                    onOpenAccessibility = {
                        runCatching {
                            context.startActivity(ClipboardGate.accessibilitySettingsIntent())
                        }
                    },
                )
            }

            item(key = "search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search clips") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Rounded.Close, null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                )
            }

            item(key = "filters") {
                ClipboardFilterRow(
                    entries = entries,
                    activeFilter = activeFilter,
                    onFilter = { picked ->
                        activeFilter = if (picked == "__CLEAR__" || activeFilter == picked) null else picked
                    },
                )
            }

            if (entries.isEmpty()) {
                item(key = "empty") {
                    ClipboardEmptyState(
                        gateOk = gateStatus == ClipboardGate.Status.ACTIVE_SHIZUKU ||
                            gateStatus == ClipboardGate.Status.ACTIVE_ACCESSIBILITY,
                        onPaste = { viewModel.pasteCurrent() },
                        onSetupShizuku = { showShizukuSetup = true },
                        onOpenAccessibility = {
                            runCatching { context.startActivity(ClipboardGate.accessibilitySettingsIntent()) }
                        },
                    )
                }
            } else if (displayGroups.isEmpty()) {
                item(key = "no-results") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No matches for \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            displayGroups.forEach { group ->
                item(key = "header_${group.label}") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            group.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                "${group.entries.size}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                items(group.entries, key = { it.id }) { entry ->
                    ClipboardRowCard(
                        entry = entry,
                        onClick = { selectedEntry = entry },
                        onCopy = { copyToClipboard(entry) },
                        onPin = { viewModel.togglePin(entry.id) },
                        onDelete = { viewModel.deleteEntry(entry) },
                        onConvertToTask = { onConvertToTask(entry.content) },
                        onAction = { action ->
                            if (action == "convert_to_task") onConvertToTask(entry.content)
                            else handleContextualAction(context, action, entry)
                        },
                    )
                }
            }
        }
    }

    // ── Detail sheet (single) ─────────────────────────────────────────────
    selectedEntry?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { scope.launch { selectedEntry = null } },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            ClipboardDetailContent(
                entry = entry,
                canShowAi = canShowAi,
                isSummarizing = isSummarizingId == entry.id,
                onCopy = { copyToClipboard(entry) },
                onDelete = { viewModel.deleteEntry(entry); selectedEntry = null },
                onPin = { viewModel.togglePin(entry.id) },
                onSummarize = { viewModel.summarizeEntry(entry) },
                onAction = { action ->
                    if (action == "convert_to_task") {
                        selectedEntry = null
                        onConvertToTask(entry.content)
                    } else handleContextualAction(context, action, entry)
                },
            )
        }
    }

    if (showShizukuSetup) {
        ShizukuSetupBottomSheet(
            onDismiss = {
                showShizukuSetup = false
                viewModel.refreshGate()
                viewModel.ensureServiceState()
            },
        )
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            ClipboardSettingsSheet(
                monitoringEnabled = monitoringEnabled,
                autoAi = autoAi,
                canShowAiMaster = clipboardAiMaster,
                retentionDays = retentionDays,
                excludeSensitive = excludeSensitive,
                onToggleMonitoring = viewModel::setMonitoringEnabled,
                onToggleMasterAi = viewModel::setClipboardAiEnabled,
                onToggleAutoAi = viewModel::setAutoAiEnabled,
                onRetention = viewModel::setRetentionDays,
                onExclude = viewModel::setExcludeSensitive,
            )
        }
    }

    if (showClearDialog) {
        val pinnedCount = entries.count { it.isPinned }
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear history?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Deletes ${entries.size - pinnedCount} clips. $pinnedCount pinned ${if (pinnedCount == 1) "clip stays" else "clips stay"}.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearAll(); showClearDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Clear", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ─── Status card ─────────────────────────────────────────────────────────────

@Composable
private fun ClipboardStatusCard(
    status: ClipboardGate.Status,
    monitoringEnabled: Boolean,
    shizukuOn: Boolean,
    accessibilityOn: Boolean,
    onToggleMonitoring: () -> Unit,
    onSetupShizuku: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    val container = when (status) {
        ClipboardGate.Status.ACTIVE_SHIZUKU,
        ClipboardGate.Status.ACTIVE_ACCESSIBILITY -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ClipboardGate.Status.PAUSED -> MaterialTheme.colorScheme.surfaceContainerHigh
        ClipboardGate.Status.SETUP_REQUIRED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = container,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    when (status) {
                        ClipboardGate.Status.ACTIVE_SHIZUKU -> Icons.Rounded.Memory
                        ClipboardGate.Status.ACTIVE_ACCESSIBILITY -> Icons.Rounded.Accessibility
                        ClipboardGate.Status.PAUSED -> Icons.Rounded.PauseCircle
                        ClipboardGate.Status.SETUP_REQUIRED -> Icons.Rounded.Warning
                    },
                    null,
                    tint = when (status) {
                        ClipboardGate.Status.SETUP_REQUIRED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        when (status) {
                            ClipboardGate.Status.ACTIVE_SHIZUKU -> "Watching via Shizuku"
                            ClipboardGate.Status.ACTIVE_ACCESSIBILITY -> "Watching via Accessibility"
                            ClipboardGate.Status.PAUSED -> "Monitoring paused"
                            ClipboardGate.Status.SETUP_REQUIRED -> "Setup required"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when (status) {
                            ClipboardGate.Status.ACTIVE_SHIZUKU,
                            ClipboardGate.Status.ACTIVE_ACCESSIBILITY -> "New copies are saved automatically."
                            ClipboardGate.Status.PAUSED -> "History still available. Resume to capture."
                            ClipboardGate.Status.SETUP_REQUIRED -> "Android 10+ blocks background reads. Enable one method."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = monitoringEnabled, onCheckedChange = { onToggleMonitoring() })
            }
            if (status == ClipboardGate.Status.SETUP_REQUIRED && monitoringEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onSetupShizuku,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Memory, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (shizukuOn) "Shizuku on" else "Setup Shizuku")
                    }
                    OutlinedButton(
                        onClick = onOpenAccessibility,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Accessibility, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (accessibilityOn) "Access on" else "Enable access")
                    }
                }
                Text(
                    "Toolz needs Shizuku OR accessibility access to see copies made in other apps. Nothing leaves your device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Filters ─────────────────────────────────────────────────────────────────

@Composable
private fun ClipboardFilterRow(
    entries: List<ClipboardEntry>,
    activeFilter: String?,
    onFilter: (String) -> Unit,
) {
    if (entries.isEmpty()) return
    val pinnedCount = entries.count { it.isPinned }
    val topTypes = entries.groupingBy { it.type }.eachCount()
        .toList().sortedByDescending { it.second }.take(5).map { it.first }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        item {
            FilterChip(
                selected = activeFilter == null,
                onClick = { onFilter("__CLEAR__") },
                label = { Text("All") },
            )
        }
        if (pinnedCount > 0) {
            item {
                FilterChip(
                    selected = activeFilter == "PINNED",
                    onClick = { onFilter("PINNED") },
                    label = { Text("Pinned • $pinnedCount") },
                    leadingIcon = { Icon(Icons.Rounded.PushPin, null, Modifier.size(14.dp)) },
                )
            }
        }
        items(topTypes) { type ->
            val meta = typeMeta(type)
            FilterChip(
                selected = activeFilter == type,
                onClick = { onFilter(type) },
                label = { Text(meta.label) },
                leadingIcon = { Icon(meta.icon, null, Modifier.size(14.dp)) },
            )
        }
    }
}

// ─── Row card (single, calm) ─────────────────────────────────────────────────

@Composable
private fun ClipboardRowCard(
    entry: ClipboardEntry,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onConvertToTask: () -> Unit,
    onAction: (String) -> Unit,
) {
    val meta = typeMeta(entry.type)
    ExpressiveCard(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(meta.icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(meta.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (entry.isPinned) {
                    Icon(Icons.Rounded.PushPin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    entry.timestamp.toRelativeTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                entry.content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (entry.type == "CODE") FontFamily.Monospace else FontFamily.Default,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCopy) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }
                IconButton(onClick = onPin, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.PushPin, null, Modifier.size(17.dp),
                        tint = if (entry.isPinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(17.dp))
                }
                Spacer(Modifier.weight(1f))
                when (entry.type) {
                    "URL", "SOCIAL" -> TextButton(onClick = { onAction("open_url") }) { Text("Open") }
                    "PHONE" -> TextButton(onClick = { onAction("call") }) { Text("Call") }
                    "EMAIL" -> TextButton(onClick = { onAction("email") }) { Text("Email") }
                    else -> TextButton(onClick = onConvertToTask) { Text("To task") }
                }
            }
        }
    }
}

// ─── Detail ──────────────────────────────────────────────────────────────────

@Composable
private fun ClipboardDetailContent(
    entry: ClipboardEntry,
    canShowAi: Boolean,
    isSummarizing: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onSummarize: () -> Unit,
    onAction: (String) -> Unit,
) {
    val meta = typeMeta(entry.type)
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                Icon(meta.icon, null, Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(meta.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(entry.timestamp.toFullTimestamp(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalIconButton(onClick = onPin) {
                Icon(Icons.Rounded.PushPin, null, tint = if (entry.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            SelectionContainer {
                Text(
                    entry.content,
                    Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = if (entry.type == "CODE") FontFamily.Monospace else FontFamily.Default,
                    ),
                )
            }
        }
        if (canShowAi && (entry.summary != null || isSummarizing)) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text(if (isSummarizing) "Summarizing…" else entry.summary.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
            if (canShowAi && entry.summary == null) {
                OutlinedButton(onClick = onSummarize, modifier = Modifier.weight(1f), enabled = !isSummarizing) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Summarize")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onAction("share") }) {
                Icon(Icons.Rounded.Share, null, Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Share")
            }
            OutlinedButton(onClick = { onAction("convert_to_task") }) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("To task")
            }
            when (entry.type) {
                "URL", "SOCIAL" -> OutlinedButton(onClick = { onAction("open_url") }) { Text("Open link") }
                "PHONE" -> OutlinedButton(onClick = { onAction("call") }) { Text("Call") }
                "EMAIL" -> OutlinedButton(onClick = { onAction("email") }) { Text("Email") }
            }
        }
        Button(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Delete", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

// ─── Settings sheet ──────────────────────────────────────────────────────────

@Composable
private fun ClipboardSettingsSheet(
    monitoringEnabled: Boolean,
    autoAi: Boolean,
    canShowAiMaster: Boolean,
    retentionDays: Int,
    excludeSensitive: Boolean,
    onToggleMonitoring: (Boolean) -> Unit,
    onToggleMasterAi: (Boolean) -> Unit,
    onToggleAutoAi: (Boolean) -> Unit,
    onRetention: (Int) -> Unit,
    onExclude: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Clipboard settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Background monitoring", fontWeight = FontWeight.SemiBold)
                Text("Requires Shizuku or accessibility.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = monitoringEnabled, onCheckedChange = onToggleMonitoring)
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Clipboard AI", fontWeight = FontWeight.SemiBold)
                Text("Show Summarize and AI badges.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = canShowAiMaster, onCheckedChange = onToggleMasterAi)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto-summarize new clips", fontWeight = FontWeight.SemiBold)
                Text("Uses network. Off by default.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = autoAi, onCheckedChange = onToggleAutoAi, enabled = canShowAiMaster)
        }
        HorizontalDivider()
        Text("Keep history for", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7 to "7 days", 30 to "30 days", 0 to "Until full").forEach { (days, label) ->
                FilterChip(selected = retentionDays == days, onClick = { onRetention(days) }, label = { Text(label) })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Skip passwords & codes", fontWeight = FontWeight.SemiBold)
                Text("Ignores OTP and password-like clips.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = excludeSensitive, onCheckedChange = onExclude)
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

// ─── Empty state ─────────────────────────────────────────────────────────────

@Composable
private fun ClipboardEmptyState(
    gateOk: Boolean,
    onPaste: () -> Unit,
    onSetupShizuku: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)) {
            Icon(Icons.Rounded.ContentPaste, null, Modifier.padding(22.dp).size(30.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text("No clips yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (gateOk) "Copy something — it will appear here."
            else "Copy text anywhere, then paste it here. For automatic capture, enable Shizuku or accessibility.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Button(onClick = onPaste) {
            Icon(Icons.Rounded.ContentPaste, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Paste current")
        }
        if (!gateOk) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSetupShizuku) { Text("Shizuku") }
                OutlinedButton(onClick = onOpenAccessibility) { Text("Accessibility") }
            }
        }
    }
}

// ─── Actions ─────────────────────────────────────────────────────────────────

private fun handleContextualAction(context: Context, action: String, entry: ClipboardEntry) {
    when (action) {
        "call" -> runCatching {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${entry.content.trim()}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        "whatsapp" -> {
            runCatching {
                val phone = entry.content.trim().replace("[^\\d+]".toRegex(), "")
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }.onFailure { Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show() }
        }
        "open_url" -> {
            runCatching {
                val raw = entry.content.trim()
                val url = when {
                    raw.startsWith("http") -> raw
                    raw.startsWith("www.") -> "https://$raw"
                    entry.type == "CRYPTO" && raw.startsWith("0x") -> "https://etherscan.io/address/$raw"
                    entry.type == "CRYPTO" -> "https://www.blockchain.com/explorer/addresses/btc/$raw"
                    else -> "https://www.google.com/search?q=${Uri.encode(raw.take(200))}"
                }
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }.onFailure { Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show() }
        }
        "email" -> runCatching {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${entry.content.trim()}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        "share" -> runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, entry.content) },
                    "Share via",
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            )
        }
    }
}
