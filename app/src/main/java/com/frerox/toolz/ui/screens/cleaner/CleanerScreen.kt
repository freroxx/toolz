package com.frerox.toolz.ui.screens.cleaner

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.frerox.toolz.data.cleaner.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerScreen(
    onBack: () -> Unit,
    viewModel: CleanerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    val scanState by viewModel.scanState.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val hasPermission by viewModel.hasStoragePermission.collectAsState()
    val showPermDialog by viewModel.showPermissionDialog.collectAsState()

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkPermission()
        viewModel.dismissPermissionDialog()
    }

    // Re-check permission on resume
    LaunchedEffect(Unit) {
        viewModel.checkPermission()
        viewModel.resetState()
    }

    if (showPermDialog) {
        PermissionEducationDialog(
            onGrantClick = {
                vibrationManager?.vibrateClick()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    permissionLauncher.launch(intent)
                }
            },
            onDismiss = { viewModel.dismissPermissionDialog() }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "FILE CLEANER",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        vibrationManager?.vibrateClick()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    if (scanState is ScanState.Scanning) {
                        IconButton(onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.cancelScan()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = scanState) {
                is ScanState.Idle -> {
                    IdleDashboard(
                        storageInfo = storageInfo,
                        hasPermission = hasPermission,
                        onScanClick = {
                            vibrationManager?.vibrateClick()
                            if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                viewModel.showPermissionDialog()
                            } else {
                                viewModel.startScan()
                            }
                        },
                        performanceMode = performanceMode
                    )
                }

                is ScanState.Scanning -> {
                    ScanningView(
                        state = state,
                        currentPath = currentPath,
                        performanceMode = performanceMode
                    )
                }

                is ScanState.Results -> {
                    ResultsView(
                        state = state,
                        onToggleDuplicate = { hash, path ->
                            vibrationManager?.vibrateClick()
                            viewModel.toggleDuplicateFile(hash, path)
                        },
                        onToggleCorpse = { path ->
                            vibrationManager?.vibrateClick()
                            viewModel.toggleCorpse(path)
                        },
                        onClean = {
                            viewModel.deleteSelected()
                        },
                        onRescan = {
                            vibrationManager?.vibrateClick()
                            viewModel.startScan()
                        },
                        performanceMode = performanceMode
                    )
                }

                is ScanState.Cleaning -> {
                    CleaningView(state = state, performanceMode = performanceMode)
                }

                is ScanState.Done -> {
                    DoneView(
                        result = state.result,
                        onDone = {
                            vibrationManager?.vibrateClick()
                            viewModel.resetState()
                        }
                    )
                }

                is ScanState.Error -> {
                    ErrorView(
                        message = state.message,
                        onRetry = {
                            vibrationManager?.vibrateClick()
                            viewModel.startScan()
                        },
                        onDismiss = {
                            vibrationManager?.vibrateClick()
                            viewModel.resetState()
                        }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Idle Dashboard
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun IdleDashboard(
    storageInfo: StorageInfo,
    hasPermission: Boolean,
    onScanClick: () -> Unit,
    performanceMode: Boolean
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StorageArcIndicator(
            storageInfo = storageInfo,
            cleanableBytes = storageInfo.cleanableBytes
        )

        Spacer(Modifier.height(32.dp))

        // Storage breakdown
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                StorageRow("Total", storageInfo.totalBytes, MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                StorageRow("Used", storageInfo.usedBytes, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                StorageRow("Free", storageInfo.freeBytes, MaterialTheme.colorScheme.tertiary)
            }
        }

        Spacer(Modifier.height(40.dp))

        // Scan button
        Button(
            onClick = onScanClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                "DEEP SCAN",
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (!hasPermission) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Limited access — tap to grant full storage permission",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StorageRow(label: String, bytes: Long, color: Color) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            Formatter.formatFileSize(context, bytes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Scanning View
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ScanningView(
    state: ScanState.Scanning,
    currentPath: String,
    performanceMode: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ScanningRadar()

        Spacer(Modifier.height(32.dp))

        ScanStatsRow(
            filesScanned = state.filesScanned,
            duplicatesFound = state.duplicatesFound,
            corpsesFound = state.corpsesFound
        )

        Spacer(Modifier.height(24.dp))

        FileStreamTicker(
            currentPath = currentPath,
            filesScanned = state.filesScanned
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Results View
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ResultsView(
    state: ScanState.Results,
    onToggleDuplicate: (String, String) -> Unit,
    onToggleCorpse: (String) -> Unit,
    onClean: () -> Unit,
    onRescan: () -> Unit,
    performanceMode: Boolean
) {
    val context = LocalContext.current
    val hasCleanable = state.totalCleanableBytes > 0

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = if (hasCleanable) 100.dp else 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary header
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "SCAN COMPLETE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp
                            )
                            Text(
                                "${state.filesScanned} files analyzed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onRescan) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Rescan",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Duplicates section
            if (state.duplicateGroups.isNotEmpty()) {
                item {
                    SectionLabel(
                        "DUPLICATES",
                        "${state.duplicateGroups.size} groups · ${
                            Formatter.formatFileSize(
                                context,
                                state.duplicateGroups.sumOf { g -> g.files.filter { it.isSelected }.sumOf { g.sizeBytes } }
                            )
                        }"
                    )
                }

                items(state.duplicateGroups, key = { it.hash }) { group ->
                    DuplicateGroupCard(
                        group = group,
                        onToggle = { filePath -> onToggleDuplicate(group.hash, filePath) }
                    )
                }
            }

            // Corpses section
            if (state.corpseEntries.isNotEmpty()) {
                item {
                    SectionLabel(
                        "ORPHANED APP DATA",
                        "${state.corpseEntries.size} entries · ${
                            Formatter.formatFileSize(
                                context,
                                state.corpseEntries.filter { it.isSelected }.sumOf { it.sizeBytes }
                            )
                        }"
                    )
                }

                items(state.corpseEntries, key = { it.path }) { corpse ->
                    CorpseListItem(
                        corpse = corpse,
                        onToggle = { onToggleCorpse(corpse.path) }
                    )
                }
            }

            // Empty state
            if (state.duplicateGroups.isEmpty() && state.corpseEntries.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Your storage is clean!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "No duplicates or orphaned data found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Slide to clean FAB
        if (hasCleanable) {
            SlideToCleanButton(
                cleanableBytes = state.totalCleanableBytes,
                onClean = onClean,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Duplicate Group Card
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    onToggle: (String) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val fileName = remember(group.files) {
        group.files.firstOrNull()?.path?.substringAfterLast('/') ?: "Unknown"
    }
    val isMediaFile = remember(fileName) {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "mp4", "mkv", "avi", "mov", "3gp")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail if media
                if (isMediaFile) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(group.files.firstOrNull()?.path)
                            .crossfade(true)
                            .size(80)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.FileCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${group.files.size} copies · ${Formatter.formatFileSize(context, group.sizeBytes)} each",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded file list
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    group.files.forEachIndexed { index, file ->
                        val shortPath = file.path
                            .removePrefix(Environment.getExternalStorageDirectory().absolutePath)
                            .trimStart('/')

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToggle(file.path) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = file.isSelected,
                                onCheckedChange = { onToggle(file.path) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.error,
                                    uncheckedColor = MaterialTheme.colorScheme.outline
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    shortPath,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (file.isSelected)
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        if (index == 0) "ORIGINAL" else "COPY",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = if (index == 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error,
                                        fontSize = 9.sp,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                            .format(java.util.Date(file.lastModified)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Corpse list item
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CorpseListItem(
    corpse: CorpseEntry,
    onToggle: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = corpse.isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.error,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    corpse.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (corpse.isSelected) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${corpse.type.name} · ${Formatter.formatFileSize(context, corpse.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Cleaning View (progress)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CleaningView(
    state: ScanState.Cleaning,
    performanceMode: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.size(120.dp),
            strokeWidth = 8.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            color = MaterialTheme.colorScheme.primary,
            strokeCap = StrokeCap.Round
        )

        Spacer(Modifier.height(32.dp))

        Text(
            "CLEANING",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp
        )
        Text(
            "${(state.progress * 100).toInt()}%",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(16.dp))

        Text(
            state.currentFile.substringAfterLast('/'),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Done View
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DoneView(
    result: CleanResult,
    onDone: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            "CLEANING COMPLETE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            Formatter.formatFileSize(context, result.freedBytes),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "freed from ${result.deletedCount} items",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (result.failedCount > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${result.failedCount} items could not be deleted (permission restricted)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("DONE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Error View
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Scan Error",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("RETRY", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onDismiss) {
            Text("GO BACK", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
        }
    }
}
