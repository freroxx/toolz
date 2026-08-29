/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.purgeshot

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.purgeshot.PurgeShotEntity
import com.frerox.toolz.data.purgeshot.PurgeShotPreset
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PurgeShotScreen(
    onBack: () -> Unit,
    viewModel: PurgeShotViewModel = hiltViewModel()
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val smartAuto by viewModel.smartAuto.collectAsStateWithLifecycle()
    val autoDuration by viewModel.autoDurationMs.collectAsStateWithLifecycle()
    val presets by viewModel.activePresets.collectAsStateWithLifecycle()
    val pending by viewModel.pendingQueue.collectAsStateWithLifecycle()
    val allQueue by viewModel.allQueue.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        }
        // Request notification permission for auto-queued feedback
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var showPresetSheet by remember { mutableStateOf(false) }
    var showAutoPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "PurgeShot",
                subtitle = if (enabled) "${pending.size} queued • auto-purge active" else "Disabled — screenshots kept forever",
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (pending.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAllPending() }, modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))) {
                            Icon(Icons.Rounded.DeleteSweep, "Clear all", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                largeFlexible = true,
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().fadingEdges(top = 12.dp, bottom = 16.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero card — what is PurgeShot
                item {
                    StaggeredEntrance(index = 0) {
                        ExpressiveCard(onClick = {}, shape = RoundedCornerShape(32.dp), containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)) {
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Surface(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Screenshots that self-destruct", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text("Save storage — every screenshot vanishes on your timer. Queue survives updates & even clear data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }

                // Controls: enabled + smart auto
                item {
                    StaggeredEntrance(index = 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsToggleCard(
                                title = "PurgeShot enabled",
                                subtitle = "Watch for new screenshots and show purge popup",
                                icon = Icons.Rounded.Screenshot,
                                checked = enabled,
                                onCheckedChange = { viewModel.setEnabled(it) }
                            )
                            AnimatedVisibility(visible = enabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SettingsToggleCard(
                                        title = "Smart Auto",
                                        subtitle = "Skip popup, auto-purge every screenshot after your chosen time",
                                        icon = Icons.Rounded.AutoAwesome,
                                        checked = smartAuto,
                                        onCheckedChange = { viewModel.setSmartAuto(it) }
                                    )
                                    // Auto duration picker
                                    ExpressiveCard(onClick = { showAutoPicker = true }, shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                                                }
                                                Column {
                                                    Text("Auto time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                                    Text(durationLabel(autoDuration), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Preset editor — up to 6 buttons, entirely customizable
                item {
                    StaggeredEntrance(index = 2) {
                        ExpressiveCard(onClick = {}, shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("Quick timers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                        Text("${presets.size}/6 buttons • tap + hold to customize", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    ToolzTonalExpressiveButton(onClick = { showPresetSheet = true }) {
                                        Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Edit", fontWeight = FontWeight.Bold)
                                    }
                                }
                                // Preview of current presets as expressive chips
                                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(max = 200.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(presets) { _, p ->
                                        PresetPreviewChip(preset = p, isAuto = p.durationMillis == autoDuration)
                                    }
                                }
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)) {
                                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Info, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                        Text("Popup shows these ${presets.size} timers. Auto button is highlighted.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                    }
                                }
                            }
                        }
                    }
                }

                // Queue manager — background queue service
                item {
                    StaggeredEntrance(index = 3) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            ExpressiveStatePill(text = "${pending.size} pending", icon = Icons.Rounded.HourglassEmpty, color = if (pending.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (pending.isEmpty()) {
                    item {
                        StaggeredEntrance(index = 4) {
                            ExpressiveCard(onClick = {}, shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(modifier = Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                                    }
                                    Text("All clear", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                    Text("No screenshots queued. Take a screenshot to see the PurgeShot popup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                    ToolzOutlinedExpressiveButton(onClick = { viewModel.debugEnqueueDummy() }) {
                                        Text("Add demo entry", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(pending, key = { it.id }) { entry ->
                        StaggeredEntrance(index = 4) {
                            PurgeQueueItem(entry = entry, onCancel = { viewModel.cancelEntry(entry.id) }, onDeleteNow = { viewModel.deleteNow(entry.id) })
                        }
                    }
                }

                // History / terminal entries
                val terminal = allQueue.filter { it.status != PurgeShotEntity.STATUS_PENDING }.take(20)
                if (terminal.isNotEmpty()) {
                    item {
                        Text("Recent activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(terminal, key = { it.id }) { entry ->
                        TerminalQueueItem(entry = entry)
                    }
                }

                // Durability explainer
                item {
                    StaggeredEntrance(index = 5) {
                        ExpressiveCard(onClick = {}, shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Never-break guarantee", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                }
                                Text("• Room DB is primary • Mirrored to Documents/Toolz/.purgeshot_queue.json (survives clear data) • Auto Backup includes DB+DataStore • WorkManager + AlarmManager dual-scheduled • Re-hydrated on boot & app update", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Clear cache, update the app, reboot — your queue is restored and timers keep ticking.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showPresetSheet) {
        PurgePresetEditorSheet(
            current = presets,
            allOptions = viewModel.allOptions,
            onDismiss = { showPresetSheet = false },
            onSave = { newPresets -> viewModel.saveCustomPresets(newPresets); showPresetSheet = false }
        )
    }
    if (showAutoPicker) {
        PurgeAutoPickerSheet(
            currentDuration = autoDuration,
            options = viewModel.allOptions,
            onDismiss = { showAutoPicker = false },
            onSelect = { dur -> viewModel.setAutoDuration(dur); showAutoPicker = false }
        )
    }
}

@Composable
private fun SettingsToggleCard(title: String, subtitle: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ExpressiveCard(onClick = { onCheckedChange(!checked) }, shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun PresetPreviewChip(preset: PurgeShotPreset, isAuto: Boolean) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isAuto) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().then(if (isAuto) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(18.dp)) else Modifier)
    ) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(iconFor(preset.iconName), null, modifier = Modifier.size(18.dp), tint = if (isAuto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(preset.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1)
            if (isAuto) Text("AUTO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp)
        }
    }
}

@Composable
private fun PurgeQueueItem(entry: PurgeShotEntity, onCancel: () -> Unit, onDeleteNow: () -> Unit) {
    var remaining by remember(entry.scheduledDeleteAtMs) { mutableStateOf(entry.scheduledDeleteAtMs - System.currentTimeMillis()) }
    LaunchedEffect(entry.scheduledDeleteAtMs) {
        while (true) {
            remaining = entry.scheduledDeleteAtMs - System.currentTimeMillis()
            if (remaining <= 0) break
            delay(1000)
        }
    }
    val progress = remember(remaining, entry.durationMillis) { 
        if (entry.durationMillis <= 0) 0f else (1f - (remaining.toFloat() / entry.durationMillis.toFloat())).coerceIn(0f, 1f)
    }
    val overdue = remaining <= 0
    ExpressiveCard(onClick = {}, shape = RoundedCornerShape(24.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.primary) }
                }
                Column(Modifier.weight(1f)) {
                    Text(entry.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(entry.fileUriString.take(48) + if (entry.fileUriString.length > 48) "…" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                ExpressiveStatePill(text = entry.durationLabel, icon = Icons.Rounded.Timer, color = MaterialTheme.colorScheme.primary)
            }
            // Squiggly / wavy progress for time remaining
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (overdue) "Deleting…" else formatRemaining(remaining), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text(entry.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ToolzWavyLinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ToolzOutlinedExpressiveButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
                ToolzExpressiveButton(onClick = onDeleteNow, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(vertical = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete now", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun TerminalQueueItem(entry: PurgeShotEntity) {
    val color = when (entry.status) {
        PurgeShotEntity.STATUS_DELETED -> MaterialTheme.colorScheme.primary
        PurgeShotEntity.STATUS_EXPIRED -> MaterialTheme.colorScheme.tertiary
        PurgeShotEntity.STATUS_CANCELLED -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.error
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = color.copy(alpha = 0.14f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (entry.status) {
                            PurgeShotEntity.STATUS_DELETED -> Icons.Rounded.Check
                            PurgeShotEntity.STATUS_EXPIRED -> Icons.Rounded.HourglassEmpty
                            PurgeShotEntity.STATUS_CANCELLED -> Icons.Rounded.Block
                            else -> Icons.Rounded.Error
                        },
                        null, tint = color, modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(entry.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("${entry.durationLabel} • ${entry.status}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(timeAgo(entry.scheduledDeleteAtMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurgePresetEditorSheet(
    current: List<PurgeShotPreset>,
    allOptions: List<PurgeShotPreset>,
    onDismiss: () -> Unit,
    onSave: (List<PurgeShotPreset>) -> Unit
) {
    var selected by remember(current) { mutableStateOf(current.toMutableList()) }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Customize timers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("Choose up to 6 timers for your popup. Tap to add/remove.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Selected preview
            if (selected.isNotEmpty()) {
                Text("Your popup (${selected.size}/6)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(max = 160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(selected) { idx, p ->
                        Surface(
                            onClick = { selected = selected.toMutableList().apply { removeAt(idx) } },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(iconFor(p.iconName), null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(p.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Text("tap to remove", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Text("All durations", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(max = 260.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(allOptions) { _, opt ->
                    val isSelected = selected.any { it.label == opt.label && it.durationMillis == opt.durationMillis }
                    val canAdd = selected.size < 6
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) selected = selected.toMutableList().apply { removeIf { it.label == opt.label && it.durationMillis == opt.durationMillis } }
                            else if (canAdd) selected = selected.toMutableList().apply { add(opt) }
                        },
                        label = { Text(opt.label, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(iconFor(opt.iconName), null, modifier = Modifier.size(16.dp)) },
                        enabled = isSelected || canAdd,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolzOutlinedExpressiveButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                ToolzExpressiveButton(onClick = { onSave(selected) }, modifier = Modifier.weight(1f), enabled = selected.isNotEmpty()) { Text("Save ${selected.size} timers", fontWeight = FontWeight.Black) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurgeAutoPickerSheet(
    currentDuration: Long,
    options: List<PurgeShotPreset>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Auto delete after", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("Used when Smart Auto is on — popup is skipped and screenshots auto-delete after this time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                items(options.size) { idx ->
                    val opt = options[idx]
                    val selected = opt.durationMillis == currentDuration
                    Surface(
                        onClick = { onSelect(opt.durationMillis) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest) {
                                    Box(contentAlignment = Alignment.Center) { Icon(iconFor(opt.iconName), null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                Text(opt.label, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold)
                            }
                            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// --- helpers ---
private fun formatRemaining(ms: Long): String {
    if (ms <= 0) return "Deleting…"
    val s = ms / 1000
    val m = s / 60
    val h = m / 60
    val d = h / 24
    return when {
        d > 0 -> "${d}d ${h % 24}h left"
        h > 0 -> "${h}h ${m % 60}m left"
        m > 0 -> "${m}m ${s % 60}s left"
        else -> "${s}s left"
    }
}
private fun timeAgo(epoch: Long): String {
    val delta = System.currentTimeMillis() - epoch
    val m = TimeUnit.MILLISECONDS.toMinutes(delta)
    val h = TimeUnit.MILLISECONDS.toHours(delta)
    val d = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        d > 0 -> "${d}d ago"
        h > 0 -> "${h}h ago"
        m > 0 -> "${m}m ago"
        else -> "just now"
    }
}
private fun durationLabel(duration: Long): String = when (duration) {
    30_000L -> "30 sec"
    60_000L -> "1 min"
    5 * 60_000L -> "5 min"
    15 * 60_000L -> "15 min"
    30 * 60_000L -> "30 min"
    60 * 60_000L -> "1 hour"
    6 * 60 * 60_000L -> "6 hours"
    12 * 60 * 60_000L -> "12 hours"
    24 * 60 * 60_000L -> "1 day"
    3 * 24 * 60 * 60_000L -> "3 days"
    7 * 24 * 60 * 60_000L -> "1 week"
    14 * 24 * 60 * 60_000L -> "2 weeks"
    30L * 24 * 60 * 60_000L -> "1 month"
    else -> "${duration / 60_000} min"
}
private fun iconFor(name: String): ImageVector = when (name.lowercase()) {
    "timer" -> Icons.Rounded.Timer
    "schedule" -> Icons.Rounded.Schedule
    "hourglass_top" -> Icons.Rounded.HourglassTop
    "hourglass_empty" -> Icons.Rounded.HourglassEmpty
    "today" -> Icons.Rounded.Today
    "date_range" -> Icons.Rounded.DateRange
    "wb_sunny" -> Icons.Rounded.WbSunny
    "nights_stay" -> Icons.Rounded.NightsStay
    "calendar_today" -> Icons.Rounded.CalendarToday
    "event_repeat" -> Icons.Rounded.EventRepeat
    "calendar_month" -> Icons.Rounded.CalendarMonth
    else -> Icons.Rounded.Timer
}
