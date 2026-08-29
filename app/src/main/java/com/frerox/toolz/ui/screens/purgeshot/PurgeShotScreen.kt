/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.purgeshot

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
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
    val totalDeleted by viewModel.totalDeleted.collectAsStateWithLifecycle()
    val nextPurge by viewModel.nextPurgeEntity.collectAsStateWithLifecycle()
    val undoItem by viewModel.undoAvailable.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.READ_MEDIA_IMAGES)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
    }

    var showPresetSheet by remember { mutableStateOf(false) }
    var showAutoPicker by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(undoItem) {
        undoItem?.let {
            val res = snackbarHost.showSnackbar(
                message = "${it.displayName} kept",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) viewModel.undoCancel()
        }
    }

    val hasAllFiles = remember { viewModel.hasAllFilesAccess() }
    var hasAllFilesState by remember { mutableStateOf(hasAllFiles) }
    LaunchedEffect(pending.size) { hasAllFilesState = viewModel.hasAllFilesAccess() }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "PurgeShot",
                subtitle = when {
                    !enabled -> "Paused"
                    pending.isEmpty() -> if (totalDeleted > 0) "$totalDeleted deleted" else "Ready"
                    else -> "${pending.size} queued • next in ${nextPurge?.let { formatRemaining(it.scheduledDeleteAtMs - System.currentTimeMillis()) } ?: "—"}"
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    if (pending.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f))
                        ) { Icon(Icons.Rounded.DeleteSweep, "Clear all", tint = MaterialTheme.colorScheme.error) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                largeFlexible = true,
                modifier = Modifier.statusBarsPadding()
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHost) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary
                )
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Permission – only when needed
                if (!hasAllFilesState && pending.isNotEmpty()) {
                    item {
                        StaggeredEntrance(index = 0) {
                            ExpressiveCard(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    runCatching { context.startActivity(intent) }
                                },
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ShieldMoon, null, tint = MaterialTheme.colorScheme.error) }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Allow deleting screenshots", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                        Text("Grant All-files access once — then deletions happen silently.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f))
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null)
                                }
                            }
                        }
                    }
                }

                // Hero — simple, animated
                item {
                    StaggeredEntrance(index = 1) {
                        ExpressiveCard(
                            onClick = {},
                            shape = RoundedCornerShape(28.dp),
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.DeleteSweep, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Screenshots that self-destruct", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text("Pick a timer when you shoot — we handle the rest.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f))
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HeroStat(label = "Queued", value = "${pending.size}", modifier = Modifier.weight(1f))
                                    HeroStat(label = "Deleted", value = "$totalDeleted", modifier = Modifier.weight(1f))
                                    HeroStat(label = "Next", value = nextPurge?.let { formatRemaining(it.scheduledDeleteAtMs - System.currentTimeMillis()) } ?: "—", modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Controls — enable + smart auto + auto time
                item {
                    StaggeredEntrance(index = 2) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ToggleRow(
                                title = "PurgeShot",
                                subtitle = if (enabled) "Watching screenshots" else "Paused",
                                icon = Icons.Rounded.ScreenshotMonitor,
                                checked = enabled,
                                onCheckedChange = { viewModel.setEnabled(it) }
                            )
                            AnimatedVisibility(
                                visible = enabled,
                                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ToggleRow(
                                        title = "Smart Auto",
                                        subtitle = if (smartAuto) "Auto-queues ${durationLabel(autoDuration)} — no popup" else "Show popup to pick each time",
                                        icon = Icons.Rounded.AutoAwesome,
                                        checked = smartAuto,
                                        onCheckedChange = { viewModel.setSmartAuto(it) }
                                    )
                                    ExpressiveCard(
                                        onClick = { showAutoPicker = true },
                                        shape = RoundedCornerShape(24.dp),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp)) }
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

                // Timers
                item {
                    StaggeredEntrance(index = 3) {
                        ExpressiveCard(onClick = {}, shape = RoundedCornerShape(24.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Timers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                    TextButton(onClick = { showPresetSheet = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                                        Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Edit", fontWeight = FontWeight.Bold)
                                    }
                                }
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier.heightIn(max = 220.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    userScrollEnabled = false
                                ) {
                                    itemsIndexed(presets) { _, p ->
                                        TimerChip(preset = p, isAuto = p.label.equals("Auto", ignoreCase = true))
                                    }
                                }
                            }
                        }
                    }
                }

                // Queue header
                item {
                    StaggeredEntrance(index = 4) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            if (pending.isNotEmpty()) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text("${pending.size}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }
                }

                if (pending.isEmpty()) {
                    item {
                        StaggeredEntrance(index = 5) {
                            ExpressiveCard(onClick = {}, shape = RoundedCornerShape(24.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(modifier = Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                                    }
                                    Text("All clear", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                                    Text("Screenshots you choose will appear here. With Smart Auto they queue silently.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                } else {
                    items(pending, key = { it.id }) { entry ->
                        QueueCard(
                            entry = entry,
                            onKeep = { viewModel.cancelEntry(entry.id) },
                            onDeleteNow = { viewModel.deleteNow(entry.id) },
                            onExtend = { viewModel.extendEntry(entry.id, 15 * 60_000L) }
                        )
                    }
                }

                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    if (showPresetSheet) {
        PresetSheet(
            current = presets,
            allOptions = viewModel.allOptions,
            onDismiss = { showPresetSheet = false },
            onSave = { viewModel.saveCustomPresets(it); showPresetSheet = false }
        )
    }
    if (showAutoPicker) {
        AutoPickerSheet(
            currentDuration = autoDuration,
            options = viewModel.allOptions,
            onDismiss = { showAutoPicker = false },
            onSelect = { viewModel.setAutoDuration(it); showAutoPicker = false }
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Clear ${pending.size} queued?", fontWeight = FontWeight.Black) },
            text = { Text("Files stay on device — only their timers are removed.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearAllPending(); showClearConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear", fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ExpressiveCard(onClick = { onCheckedChange(!checked) }, shape = RoundedCornerShape(24.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun TimerChip(preset: PurgeShotPreset, isAuto: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isAuto) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(iconFor(preset.iconName), null, modifier = Modifier.size(18.dp), tint = if (isAuto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(preset.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (isAuto) "AUTO" else durationToHumane(preset.durationMillis),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isAuto) FontWeight.Black else FontWeight.Normal,
                color = if (isAuto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = if (isAuto) 9.sp else 10.sp
            )
        }
    }
}

@Composable
private fun QueueCard(entry: PurgeShotEntity, onKeep: () -> Unit, onDeleteNow: () -> Unit, onExtend: () -> Unit) {
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
    ExpressiveCard(onClick = {}, shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                }
                Column(Modifier.weight(1f)) {
                    Text(entry.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.durationLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)) {
                    Text(if (overdue) "Deleting…" else formatRemaining(remaining), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
            ToolzWavyLinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(8.dp)),
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onExtend, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                    Icon(Icons.Rounded.MoreTime, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("+15m", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onKeep, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                    Icon(Icons.Rounded.Undo, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Keep")
                }
                Button(onClick = onDeleteNow, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(vertical = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSheet(
    current: List<PurgeShotPreset>,
    allOptions: List<PurgeShotPreset>,
    onDismiss: () -> Unit,
    onSave: (List<PurgeShotPreset>) -> Unit
) {
    var selected by remember(current) { mutableStateOf(current.toMutableList()) }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Edit timers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("${selected.size}/6", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            if (selected.isNotEmpty()) {
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(max = 160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
                    itemsIndexed(selected, key = { _, p -> p.label + p.durationMillis }) { idx, p ->
                        Surface(
                            onClick = { selected = selected.toMutableList().apply { removeAt(idx) } },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(iconFor(p.iconName), null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(p.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                                Text("tap to remove", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            }
            Text("All durations", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(max = 260.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(allOptions) { _, opt ->
                    val isSelected = selected.any { it.label == opt.label && it.durationMillis == opt.durationMillis }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selected = when {
                                isSelected -> selected.filterNot { it.label == opt.label && it.durationMillis == opt.durationMillis }.toMutableList()
                                selected.size < 6 -> (selected + opt).toMutableList()
                                else -> selected
                            }
                        },
                        label = { Text(opt.label, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(iconFor(opt.iconName), null, modifier = Modifier.size(14.dp)) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Cancel") }
                Button(onClick = { onSave(selected) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Save", fontWeight = FontWeight.Black) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoPickerSheet(
    currentDuration: Long,
    options: List<PurgeShotPreset>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Auto delete after", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("Used when Smart Auto is on — no popup, just queue.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            options.forEach { opt ->
                val selected = opt.durationMillis == currentDuration
                Surface(
                    onClick = { onSelect(opt.durationMillis) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(iconFor(opt.iconName), null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(opt.label, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                        }
                        if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun durationLabel(millis: Long): String = when (millis) {
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
    else -> "${millis / 60_000} min"
}

private fun durationToHumane(millis: Long): String {
    val s = millis / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        s < 86400 -> "${s / 3600}h"
        else -> "${s / 86400}d"
    }
}

private fun formatRemaining(millis: Long): String {
    if (millis <= 0) return "now"
    val s = millis / 1000
    return when {
        s < 60 -> "${s}s left"
        s < 3600 -> "${s / 60}m left"
        s < 86400 -> "${s / 3600}h left"
        else -> "${s / 86400}d left"
    }
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
    "auto_awesome" -> Icons.Rounded.AutoAwesome
    else -> Icons.Rounded.Timer
}
