/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.purgeshot

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

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
    val totalDeleted by viewModel.totalDeleted.collectAsStateWithLifecycle()
    val nextPurge by viewModel.nextPurgeEntity.collectAsStateWithLifecycle()
    val pendingBytes by viewModel.estimatedPendingBytes.collectAsStateWithLifecycle()
    val savedBytes by viewModel.estimatedSavedBytes.collectAsStateWithLifecycle()
    val undoItem by viewModel.undoAvailable.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val performanceMode = LocalPerformanceMode.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.READ_MEDIA_IMAGES
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
    }

    var showPresetSheet by remember { mutableStateOf(false) }
    var showAutoPicker by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(undoItem) {
        undoItem?.let {
            val res = snackbarHost.showSnackbar(
                message = "${it.displayName} unscheduled",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) viewModel.undoCancel()
        }
    }

    val hasAllFiles = remember { viewModel.hasAllFilesAccess() }
    var hasAllFilesState by remember { mutableStateOf(hasAllFiles) }
    LaunchedEffect(pending.size) { hasAllFilesState = viewModel.hasAllFilesAccess() }

    val groupedPending = remember(pending) { groupByDay(pending) }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "PurgeShot",
                subtitle = when {
                    !enabled -> "Paused — screenshots kept"
                    pending.isEmpty() -> "Ready • $totalDeleted purged • ${formatBytes(savedBytes)} saved"
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
                modifier = Modifier.fillMaxSize().fadingEdges(top = 12.dp, bottom = 16.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Permission banner — production ready (scoped storage on Android 13+)
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
                                shape = RoundedCornerShape(28.dp),
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Surface(modifier = Modifier.size(42.dp), shape = CircleShape, color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ShieldMoon, null, tint = MaterialTheme.colorScheme.error) }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Allow deleting screenshots", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                        Text("Without All-files access, deletions need your tap each time. Grant once — PurgeShot then purges silently.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f))
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                }

                // Hero stats — storage saved & next purge
                item {
                    StaggeredEntrance(index = 1) {
                        ExpressiveCard(onClick = {}, shape = RoundedCornerShape(32.dp), containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Screenshots that self-destruct", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text("Queue survives updates & clear data — timers keep ticking.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f))
                                    }
                                    if (!performanceMode) {
                                        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                            ExpressivePulseIndicator(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                                            Icon(Icons.Rounded.AutoDelete, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    StatChip(label = "QUEUED", value = "${pending.size}", sub = formatBytes(pendingBytes), icon = Icons.Rounded.HourglassEmpty, modifier = Modifier.weight(1f))
                                    StatChip(label = "SAVED", value = formatBytes(savedBytes), sub = "$totalDeleted files", icon = Icons.Rounded.Savings, modifier = Modifier.weight(1f))
                                    StatChip(
                                        label = "NEXT",
                                        value = nextPurge?.let { formatRemaining(it.scheduledDeleteAtMs - System.currentTimeMillis()) } ?: "—",
                                        sub = nextPurge?.durationLabel ?: "idle",
                                        icon = Icons.Rounded.Timer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pending.isNotEmpty()) {
                                    ToolzWavyLinearProgressIndicator(
                                        progress = { nextPurge?.let { 1f - ((it.scheduledDeleteAtMs - System.currentTimeMillis()).toFloat() / it.durationMillis.coerceAtLeast(1)) .coerceIn(0f, 1f) } ?: 0f },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(8.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Controls
                item {
                    StaggeredEntrance(index = 2) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsToggleCard(
                                title = "PurgeShot enabled",
                                subtitle = if (enabled) "Watching Screenshots folder • popup on capture" else "Paused — no screenshots will be auto-deleted",
                                icon = Icons.Rounded.ScreenshotMonitor,
                                checked = enabled,
                                onCheckedChange = { viewModel.setEnabled(it) }
                            )
                            AnimatedVisibility(visible = enabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SettingsToggleCard(
                                        title = "Smart Auto",
                                        subtitle = if (smartAuto) "Skips popup — auto-queues ${durationLabel(autoDuration)}" else "Show popup so you choose each time",
                                        icon = Icons.Rounded.AutoAwesome,
                                        checked = smartAuto,
                                        onCheckedChange = { viewModel.setSmartAuto(it) }
                                    )
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
                                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                                                Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.padding(8.dp).size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Preset editor
                item {
                    StaggeredEntrance(index = 3) {
                        ExpressiveCard(onClick = {}, shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Quick timers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                        Text("${presets.size}/6 • popup buttons • auto is highlighted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    ToolzTonalExpressiveButton(onClick = { showPresetSheet = true }) {
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
                                        PresetPreviewChip(preset = p, isAuto = p.durationMillis == autoDuration)
                                    }
                                }
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)) {
                                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Info, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                        Text("Tip: long-press a timer in the popup to keep the file.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                    }
                                }
                            }
                        }
                    }
                }

                // Queue header + batch
                item {
                    StaggeredEntrance(index = 4) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                if (pending.isNotEmpty()) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("${pending.size}", modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (pending.size > 1) {
                                    ToolzTonalExpressiveButton(onClick = { pending.forEach { viewModel.extendEntry(it.id, 15 * 60_000L) } }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                        Icon(Icons.Rounded.MoreTime, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("+15m all", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                ExpressiveStatePill(
                                    text = if (pending.isEmpty()) "idle" else "${formatBytes(pendingBytes)} pending",
                                    icon = if (pending.isEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.HourglassEmpty,
                                    color = if (pending.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (pending.isEmpty()) {
                    item {
                        StaggeredEntrance(index = 5) {
                            ExpressiveCard(onClick = {}, shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(28.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Surface(modifier = Modifier.size(72.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                                    }
                                    Text("All clear — screenshots stay until you shoot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                                    Text("Take a screenshot to see the PurgeShot popup. With Smart Auto, it queues silently and frees space on schedule.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        ToolzExpressiveButton(onClick = { viewModel.debugEnqueueDummy() }) {
                                            Icon(Icons.Rounded.BugReport, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Demo", fontWeight = FontWeight.Black)
                                        }
                                        ToolzOutlinedExpressiveButton(onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://media/external/images/media"))
                                            runCatching { context.startActivity(intent) }
                                        }) {
                                            Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Open gallery")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Group by day for timeline polish — groupedPending computed above (remember) for performance
                    groupedPending.forEach { (label, items) ->
                        item {
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
                                Text("${items.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(items, key = { it.id }) { entry ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    when (value) {
                                        SwipeToDismissBoxValue.StartToEnd -> { viewModel.cancelEntry(entry.id); true }
                                        SwipeToDismissBoxValue.EndToStart -> { viewModel.deleteNow(entry.id); true }
                                        else -> false
                                    }
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val dir = dismissState.targetValue
                                    val color = when (dir) {
                                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.tertiaryContainer
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                        else -> Color.Transparent
                                    }
                                    val icon = when (dir) {
                                        SwipeToDismissBoxValue.StartToEnd -> Icons.Rounded.Undo
                                        SwipeToDismissBoxValue.EndToStart -> Icons.Rounded.DeleteForever
                                        else -> null
                                    }
                                    val label = when (dir) {
                                        SwipeToDismissBoxValue.StartToEnd -> "Keep"
                                        SwipeToDismissBoxValue.EndToStart -> "Delete now"
                                        else -> ""
                                    }
                                    Box(
                                        Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(color).padding(horizontal = 20.dp),
                                        contentAlignment = if (dir == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                    ) {
                                        if (icon != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(icon, null, tint = if (dir == SwipeToDismissBoxValue.StartToEnd) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer)
                                            Text(label, fontWeight = FontWeight.Black, color = if (dir == SwipeToDismissBoxValue.StartToEnd) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                    }
                                }
                            ) {
                                PurgeQueueItem(
                                    entry = entry,
                                    onCancel = { viewModel.cancelEntry(entry.id) },
                                    onDeleteNow = { viewModel.deleteNow(entry.id) },
                                    onExtend = { viewModel.extendEntry(entry.id, 15 * 60_000L) }
                                )
                            }
                        }
                    }
                }

                // Recent activity — timeline
                val terminal = allQueue.filter { it.status != PurgeShotEntity.STATUS_PENDING }.take(20)
                if (terminal.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Recent activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
                        }
                    }
                    items(terminal, key = { it.id }) { entry ->
                        TerminalQueueItem(entry = entry)
                    }
                }

                // Durability explainer — polished with icons
                item {
                    StaggeredEntrance(index = 6) {
                        ExpressiveCard(
                            onClick = {},
                            shape = RoundedCornerShape(28.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f))
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                                    }
                                    Column {
                                        Text("Never-break queue", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                        Text("Survives update, reboot & clear data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DurabilityRow(icon = Icons.Rounded.Storage, text = "Room DB is source-of-truth — transactional, observable")
                                    DurabilityRow(icon = Icons.Rounded.Description, text = "Mirrored to Documents/Toolz/.purgeshot_queue.json — survives clear data")
                                    DurabilityRow(icon = Icons.Rounded.CloudUpload, text = "Auto Backup includes DB + DataStore — survives reinstall")
                                    DurabilityRow(icon = Icons.Rounded.Alarm, text = "WorkManager + AlarmManager dual-scheduled — survives Doze & reboot")
                                }
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
                                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Text("Verified on boot & every app launch — pending timers are re-hydrated automatically.", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    if (showPresetSheet) {
        PurgePresetEditorSheet(
            current = presets,
            allOptions = viewModel.allOptions,
            onDismiss = { showPresetSheet = false },
            onSave = { viewModel.saveCustomPresets(it); showPresetSheet = false }
        )
    }
    if (showAutoPicker) {
        PurgeAutoPickerSheet(
            currentDuration = autoDuration,
            options = viewModel.allOptions,
            onDismiss = { showAutoPicker = false },
            onSelect = { viewModel.setAutoDuration(it); showAutoPicker = false }
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Clear ${pending.size} queued?", fontWeight = FontWeight.Black) },
            text = { Text("This keeps the files and only unschedules their deletion. You can re-queue them by taking another screenshot.") },
            confirmButton = {
                ToolzExpressiveButton(onClick = { viewModel.clearAllPending(); showClearConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Clear queue", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = { ToolzOutlinedExpressiveButton(onClick = { showClearConfirm = false }) { Text("Keep") } }
        )
    }
}

@Composable
private fun StatChip(label: String, value: String, sub: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, fontSize = 11.sp)
        }
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
            Text(preset.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isAuto) Text("AUTO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp)
            else Text(durationToHumane(preset.durationMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PurgeQueueItem(entry: PurgeShotEntity, onCancel: () -> Unit, onDeleteNow: () -> Unit, onExtend: () -> Unit) {
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
    var showMenu by remember { mutableStateOf(false) }
    ExpressiveCard(onClick = {}, shape = RoundedCornerShape(24.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.primary) }
                }
                Column(Modifier.weight(1f)) {
                    Text(entry.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.fileUriString.take(44) + if (entry.fileUriString.length > 44) "…" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, shape = RoundedCornerShape(20.dp)) {
                        DropdownMenuItem(text = { Text("Extend +15 min") }, onClick = { showMenu = false; onExtend() }, leadingIcon = { Icon(Icons.Rounded.MoreTime, null) })
                        DropdownMenuItem(text = { Text("Copy path") }, onClick = { showMenu = false }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) })
                    }
                }
                ExpressiveStatePill(text = entry.durationLabel, icon = Icons.Rounded.Timer, color = MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!overdue) Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) else Icon(Icons.Rounded.Warning, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                        Text(if (overdue) "Deleting…" else formatRemaining(remaining), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    Text(entry.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
                ToolzWavyLinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)), color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ToolzOutlinedExpressiveButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                    Icon(Icons.Rounded.Undo, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Keep", fontWeight = FontWeight.Bold)
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
                Text(entry.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${entry.durationLabel} • ${entry.status}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(timeAgo(entry.scheduledDeleteAtMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DurabilityRow(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Customize timers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Up to 6 • drag to reorder (next: hold)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("${selected.size}/6", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            if (selected.isNotEmpty()) {
                Text("Your popup", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(max = 180.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
                    itemsIndexed(selected, key = { _, p -> p.label + p.durationMillis }) { idx, p ->
                        Surface(
                            onClick = { selected = selected.toMutableList().apply { removeAt(idx) } },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(iconFor(p.iconName), null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(p.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                                Text("tap to remove", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Text("All durations", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(max = 300.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(allOptions) { _, opt ->
                    val isSelected = selected.any { it.label == opt.label && it.durationMillis == opt.durationMillis }
                    val canAdd = selected.size < 6
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) selected = selected.toMutableList().apply { removeIf { it.label == opt.label && it.durationMillis == opt.durationMillis } }
                            else if (canAdd) selected = selected.toMutableList().apply { add(opt) }
                        },
                        label = { Text(opt.label, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                        leadingIcon = { Icon(iconFor(opt.iconName), null, modifier = Modifier.size(16.dp)) },
                        enabled = isSelected || canAdd,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolzOutlinedExpressiveButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                ToolzExpressiveButton(onClick = { onSave(selected) }, modifier = Modifier.weight(1f), enabled = selected.isNotEmpty()) { Text("Save ${selected.size}", fontWeight = FontWeight.Black) }
            }
            Spacer(Modifier.navigationBarsPadding())
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
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Auto delete after", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("Skips popup — every screenshot auto-deletes after this.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 460.dp)) {
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
                                Column {
                                    Text(opt.label, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold)
                                    Text(durationToHumane(opt.durationMillis) + " • ${if (opt.durationMillis < 60_000L) "instant" else "grace period"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

// --- helpers ---
private fun formatRemaining(ms: Long): String {
    if (ms <= 0) return "deleting…"
    val s = ms / 1000
    val m = s / 60
    val h = m / 60
    val d = h / 24
    return when {
        d > 0 -> "${d}d ${h % 24}h"
        h > 0 -> "${h}h ${m % 60}m"
        m > 0 -> "${m}m ${s % 60}s"
        else -> "${s}s"
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
        else -> "now"
    }
}
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> "${(mb * 10).roundToInt() / 10.0} MB"
        kb >= 1 -> "${kb.roundToInt()} KB"
        else -> "$bytes B"
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
private fun durationToHumane(millis: Long): String {
    val s = millis / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        s < 86400 -> "${s / 3600}h"
        else -> "${s / 86400}d"
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
    else -> Icons.Rounded.Timer
}

private fun groupByDay(list: List<PurgeShotEntity>): List<Pair<String, List<PurgeShotEntity>>> {
    if (list.isEmpty()) return emptyList()
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val today = fmt.format(Date(System.currentTimeMillis()))
    val yesterday = fmt.format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L))
    val groups = list.groupBy { fmt.format(Date(it.scheduledDeleteAtMs)) }
    return groups.entries.sortedByDescending { it.key }.map { (k, v) ->
        val label = when (k) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> try {
                SimpleDateFormat("MMM d", Locale.getDefault()).format(fmt.parse(k)!!)
            } catch (_: Exception) { k }
        }
        label to v.sortedBy { it.scheduledDeleteAtMs }
    }
}
