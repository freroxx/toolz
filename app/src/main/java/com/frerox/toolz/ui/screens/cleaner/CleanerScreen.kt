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

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)

package com.frerox.toolz.ui.screens.cleaner

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.R
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.CleanResult
import com.frerox.toolz.data.cleaner.ScanState
import com.frerox.toolz.data.cleaner.StorageInfo
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.components.cleaner.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import java.io.File

@Composable
fun CleanerScreen(
    onBack: () -> Unit,
    onNavigateToPdf: (Uri, String) -> Unit = { _, _ -> },
    onNavigateToMusic: (Uri) -> Unit = {},
    viewModel: CleanerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val vibration = LocalVibrationManager.current
    val scanState by viewModel.scanState.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val hasPermission by viewModel.hasStoragePermission.collectAsState()
    val showPermDialog by viewModel.showPermissionDialog.collectAsState()
    val gridCategory by viewModel.gridCategory.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.checkPermission()
        viewModel.dismissPermissionDialog()
    }
    LaunchedEffect(Unit) { viewModel.checkPermission() }
    BackHandler(enabled = gridCategory != null) { viewModel.closeGridView() }

    if (showPermDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionDialog() },
            icon = { Icon(Icons.Rounded.FolderSpecial, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
            title = { Text("Storage Access Needed", fontWeight = FontWeight.Black) },
            text = { Text("Grant all-files access to find deep junk, app leftovers and duplicates.", textAlign = TextAlign.Center) },
            confirmButton = {
                ToolzExpressiveButton(onClick = {
                    vibration?.vibrateClick()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.fromParts("package", context.packageName, null) })
                    }
                }) { Text("Grant Access", fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissPermissionDialog() }) { Text("Not Now") } },
            shape = BouncyShape
        )
    }

    val showDetailSheet = gridCategory != null
    if (showDetailSheet) {
        CleanerDetailSheet(
            category = gridCategory!!,
            onToggleItem = { id -> viewModel.toggleCategoryItem(gridCategory!!.id, id) },
            onToggleDuplicate = { hash, path -> viewModel.toggleDuplicateFile(gridCategory!!.id, hash, path) },
            onOpenFile = { path -> openFile(context, path, onNavigateToPdf, onNavigateToMusic) },
            onDismiss = { viewModel.closeGridView() }
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    val title = when {
        gridCategory != null -> gridCategory!!.name
        else -> "File Cleaner"
    }
    val subtitle = when (val s = scanState) {
        is ScanState.Scanning -> s.currentCategory.ifBlank { "Scanning…" }
        is ScanState.Results -> "${s.filesScanned} items • ${Formatter.formatFileSize(context, s.totalCleanableBytes)}"
        is ScanState.Cleaning -> "Cleaning…"
        is ScanState.Done -> "Optimised"
        else -> "Deep storage analysis"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().toolzBackground().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            ExpressiveTopAppBar(
                title = title,
                subtitle = subtitle,
                navigationIcon = {
                    IconButton(onClick = {
                        vibration?.vibrateClick()
                        if (gridCategory != null) viewModel.closeGridView() else onBack()
                    }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (scanState is ScanState.Scanning) {
                        IconButton(onClick = { vibration?.vibrateClick(); viewModel.cancelScan() }) {
                            Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = scanState,
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn(tween(360, easing = EaseOutCubic)) + scaleIn(tween(360, easing = EaseOutCubic), initialScale = 0.96f)) togetherWith
                            (fadeOut(tween(200)) + scaleOut(targetScale = 1.02f))
                },
                label = "cleaner_state_v2"
            ) { state ->
                when (state) {
                    is ScanState.Idle -> IdleDashboardV2(
                        storageInfo = storageInfo,
                        hasPermission = hasPermission,
                        onScanClick = {
                            vibration?.vibrateClick()
                            if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) viewModel.showPermissionDialog() else viewModel.startScan()
                        }
                    )
                    is ScanState.Scanning -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CleanerScanProgress(
                            currentCategory = state.currentCategory,
                            progress = state.progress,
                            filesScanned = state.filesScanned,
                            foundSize = state.foundSize
                        )
                    }
                    is ScanState.Results -> ResultsV2(
                        state = state,
                        onToggleItem = { catId, itemId -> vibration?.vibrateClick(); viewModel.toggleCategoryItem(catId, itemId) },
                        onToggleDuplicate = { catId, hash, p -> vibration?.vibrateClick(); viewModel.toggleDuplicateFile(catId, hash, p) },
                        onClean = { vibration?.vibrateLongClick(); viewModel.deleteSelected() },
                        onRescan = { vibration?.vibrateClick(); viewModel.startScan() },
                        onOpenFile = { path -> openFile(context, path, onNavigateToPdf, onNavigateToMusic) },
                        onLongPressCategory = { cat -> vibration?.vibrateLongClick(); viewModel.openGridView(cat) }
                    )
                    is ScanState.Cleaning -> CleaningViewV2(state)
                    is ScanState.Done -> DoneViewV2(result = state.result, onDone = { vibration?.vibrateClick(); viewModel.resetState() })
                    is ScanState.Error -> ErrorViewV2(message = state.message, onRetry = { viewModel.startScan() }, onDismiss = { viewModel.resetState() })
                }
            }
        }
    }
}

@Composable
private fun IdleDashboardV2(
    storageInfo: StorageInfo,
    hasPermission: Boolean,
    onScanClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.3f))
        CleanerStorageArc(storageInfo = storageInfo, cleanableBytes = storageInfo.cleanableBytes, modifier = Modifier)
        Spacer(Modifier.height(20.dp))
        CleanerOverviewCard(storageInfo = storageInfo)
        Spacer(Modifier.height(20.dp))
        if (!hasPermission) {
            Surface(
                onClick = onScanClick,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                shape = MediumExpressiveShape,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.GppMaybe, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Full Access Recommended", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
                        Text("Grant storage permission for deeper analysis.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        ToolzExpressiveButton(
            onClick = onScanClick,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(22.dp)
        ) {
            Icon(Icons.Rounded.TravelExplore, null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Deep Scan", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.weight(1f))
        Text("9 analyzers • safe auto-select • trash with undo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ResultsV2(
    state: ScanState.Results,
    onToggleItem: (String, String) -> Unit,
    onToggleDuplicate: (String, String, String) -> Unit,
    onClean: () -> Unit,
    onRescan: () -> Unit,
    onOpenFile: (String) -> Unit,
    onLongPressCategory: (CleanCategory) -> Unit
) {
    val performanceMode = LocalPerformanceMode.current
    val allSelected = remember(state.categories) {
        state.categories.isNotEmpty() && state.categories.all { cat ->
            cat.items.all { item ->
                when (item) {
                    is CleanItem.GenericFile -> item.file.isSelected
                    is CleanItem.Corpse -> item.entry.isSelected
                    is CleanItem.Duplicate -> item.group.files.any { it.isSelected }
                    is CleanItem.EmptyDir -> item.entry.isSelected
                    is CleanItem.MediaFile -> item.entry.isSelected
                    is CleanItem.ApkFile -> item.entry.isSelected
                    is CleanItem.AppCache -> item.entry.isSelected
                    is CleanItem.UnusedApp -> item.entry.isSelected
                }
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().run { if (!performanceMode) fadingEdges(top = 16.dp, bottom = 24.dp) else this }
        ) {
            item {
                ResultsHeaderV2(
                    totalSize = state.totalCleanableBytes,
                    filesScanned = state.filesScanned,
                    allSelected = allSelected,
                    onToggleSelectAll = {
                        val target = !allSelected
                        state.categories.forEach { cat ->
                            cat.items.forEach { item ->
                                when (item) {
                                    is CleanItem.GenericFile -> if (item.file.isSelected != target) onToggleItem(cat.id, item.file.path)
                                    is CleanItem.Corpse -> if (item.entry.isSelected != target) onToggleItem(cat.id, item.entry.path)
                                    is CleanItem.EmptyDir -> if (item.entry.isSelected != target) onToggleItem(cat.id, item.entry.path)
                                    is CleanItem.MediaFile -> if (item.entry.isSelected != target) onToggleItem(cat.id, item.entry.path)
                                    is CleanItem.ApkFile -> if (item.entry.isSelected != target) onToggleItem(cat.id, item.entry.path)
                                    is CleanItem.AppCache -> if (item.entry.isSelected != target) onToggleItem(cat.id, item.entry.packageName)
                                    is CleanItem.UnusedApp -> if (item.entry.isSelected != target) onToggleItem(cat.id, item.entry.packageName)
                                    is CleanItem.Duplicate -> item.group.files.drop(1).forEach { f -> if (f.isSelected != target) onToggleDuplicate(cat.id, item.group.hash, f.path) }
                                }
                            }
                        }
                    },
                    onRescan = onRescan
                )
            }
            items(state.categories.size) { idx ->
                val cat = state.categories[idx]
                CleanerCategoryCard(
                    category = cat,
                    onToggleItem = { id -> onToggleItem(cat.id, id) },
                    onToggleDuplicate = { h, p -> onToggleDuplicate(cat.id, h, p) },
                    onOpenFile = onOpenFile,
                    onLongPress = { onLongPressCategory(cat) }
                )
            }
        }
        AnimatedVisibility(
            visible = state.selectedBytes > 0,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).navigationBarsPadding(),
            enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy)) { it / 2 } + fadeIn() + scaleIn(initialScale = 0.92f),
            exit = slideOutVertically { it / 2 } + fadeOut() + scaleOut(targetScale = 0.92f)
        ) {
            CleanerBottomBar(
                selectedBytes = state.selectedBytes,
                cleanableBytes = state.totalCleanableBytes,
                itemCount = state.categories.sumOf { it.items.size },
                isAllSelected = allSelected,
                onClean = onClean,
                onToggleSelectAll = {
                    val target = !allSelected
                    state.categories.forEach { cat ->
                        cat.items.forEach { item ->
                            when (item) {
                                is CleanItem.GenericFile -> if (item.file.isSelected != target) onToggleItem(cat.id, item.file.path)
                                is CleanItem.Corpse -> if (item.entry.isSelected != target) onToggleItem(cat.id, item.entry.path)
                                else -> {}
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ResultsHeaderV2(totalSize: Long, filesScanned: Int, allSelected:Boolean, onToggleSelectAll:()->Unit, onRescan:()->Unit) {
    val context = LocalContext.current
    Surface(modifier = Modifier.fillMaxWidth(), shape = LargeExpressiveShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("OPTIMISABLE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                Text(Formatter.formatFileSize(context, totalSize), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                Text("Found in $filesScanned items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolzExpressiveIconButton(onClick = onToggleSelectAll, modifier = Modifier.size(48.dp)) {
                    Icon(if (allSelected) Icons.Rounded.DoneAll else Icons.Rounded.SelectAll, null, Modifier.size(20.dp))
                }
                ToolzExpressiveIconButton(onClick = onRescan, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun CleaningViewV2(state: ScanState.Cleaning) {
    val primary = MaterialTheme.colorScheme.primary
    val anim by animateFloatAsState(state.progress, tween(400), label = "cleanProgV2")
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
            CircularProgressIndicator(progress = { 1f }, modifier = Modifier.fillMaxSize().padding(14.dp), strokeWidth = 12.dp, color = MaterialTheme.colorScheme.surfaceContainerHighest, strokeCap = StrokeCap.Round)
            CircularProgressIndicator(progress = { anim }, modifier = Modifier.fillMaxSize().padding(14.dp), strokeWidth = 12.dp, color = primary, strokeCap = StrokeCap.Round)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(anim*100).toInt()}%", style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black), color = primary)
                Text("CLEANING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(state.currentFile.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DoneViewV2(result: CleanResult, onDone: ()->Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(modifier = Modifier.size(120.dp), shape = ExtraLargeExpressiveShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.height(24.dp))
        Text("Device Optimised", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(Formatter.formatFileSize(context, result.freedBytes), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text("freed across ${result.deletedCount} items", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        if (result.failedCount > 0) Text("${result.failedCount} items skipped", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(32.dp))
        ToolzExpressiveButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(22.dp)) {
            Icon(Icons.Rounded.Home, null, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Return to Dashboard", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ErrorViewV2(message:String, onRetry:()->Unit, onDismiss:()->Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(modifier = Modifier.size(90.dp), shape = ExtraLargeExpressiveShape, color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(20.dp))
        Text("Scan Interrupted", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        ToolzExpressiveButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Try Again", fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        ToolzOutlinedExpressiveButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Go Back", fontWeight = FontWeight.Black) }
    }
}

// helpers
private fun openFile(context: Context, path:String, onPdf:(Uri,String)->Unit, onMusic:(Uri)->Unit) {
    val file = File(path)
    if (!file.exists()) return
    val ext = file.extension.lowercase()
    val mediaUri = getMediaStoreUri(context, path, ext)
    val uri = mediaUri ?: runCatching { FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file) }.getOrElse { Uri.fromFile(file) }
    when (ext) {
        "pdf" -> onPdf(uri, file.name)
        "mp3","wav","m4a","ogg","flac" -> onMusic(uri)
        else -> runCatching {
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            runCatching { context.startActivity(intent) }.getOrElse { context.startActivity(Intent.createChooser(intent, "Open file with")) }
        }
    }
}
private fun getMediaStoreUri(context: Context, path: String, ext: String): Uri? {
    val collection = when (ext) {
        "mp3","wav","m4a","ogg","flac" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        "pdf" -> MediaStore.Files.getContentUri("external")
        "jpg","jpeg","png","gif","webp","bmp" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        "mp4","mkv","avi","mov","webm" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else -> return null
    }
    return runCatching {
        context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.DATA}=?", arrayOf(path), null)?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null }
    }.getOrNull()
}
