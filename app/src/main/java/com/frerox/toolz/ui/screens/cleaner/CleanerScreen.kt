/*
 * Copyright (C) 2026 Toolz Contributors
 */
@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.ScanState
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.components.cleaner.*
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
    val scanState by viewModel.scanState.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val hasPermission by viewModel.hasStoragePermission.collectAsState()
    val showPermDialog by viewModel.showPermissionDialog.collectAsState()
    val gridCategory by viewModel.gridCategory.collectAsState()
    val isShizukuGranted by viewModel.isShizukuGranted.collectAsState()
    var viewerPath by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.checkPermission(); viewModel.dismissPermissionDialog()
    }
    LaunchedEffect(Unit) { viewModel.checkPermission(); viewModel.refreshShizuku() }
    BackHandler(enabled = gridCategory != null) { viewModel.closeGridView() }

    if (showPermDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionDialog() },
            icon = { Icon(Icons.Rounded.FolderSpecial, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) },
            title = { Text("Storage Access Needed", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp)) },
            text = { Text("Grant all-files access to scan junk and leftovers.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), textAlign = TextAlign.Center) },
            confirmButton = {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.fromParts("package", context.packageName, null) })
                    }
                }, shape = RoundedCornerShape(20.dp)) { Text("Grant", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissPermissionDialog() }) { Text("Not now", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) } },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (viewerPath != null) {
        CleanerMediaViewer(filePath = viewerPath!!, onDismiss = { viewerPath = null })
    }

    if (gridCategory != null) {
        CleanerDetailSheet(
            category = gridCategory!!,
            onToggleItem = { id -> viewModel.toggleCategoryItem(gridCategory!!.id, id) },
            onToggleDuplicate = { h, p -> viewModel.toggleDuplicateFile(gridCategory!!.id, h, p) },
            onOpenFile = { p ->
                val ext = p.substringAfterLast(".", "").lowercase()
                if (ext in setOf("jpg","jpeg","png","gif","webp","bmp","heic","heif","mp4","mkv","avi","mov","webm")) viewerPath = p else openFile(context, p, onNavigateToPdf, onNavigateToMusic)
            },
            onDismiss = { viewModel.closeGridView() }
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val title = if (gridCategory != null) gridCategory!!.name else "File Cleaner"
    val subtitle = when (val s = scanState) {
        is ScanState.Scanning -> s.currentCategory.ifBlank { "Scanning…" }
        is ScanState.Results -> "${s.filesScanned} items • ${Formatter.formatFileSize(context, s.totalCleanableBytes)}"
        is ScanState.Cleaning -> "Cleaning…"
        is ScanState.Done -> "Done"
        else -> "Free up space"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().toolzBackground().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            ExpressiveTopAppBar(
                title = title,
                subtitle = subtitle,
                navigationIcon = { IconButton(onClick = { if (gridCategory != null) viewModel.closeGridView() else onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = { if (scanState is ScanState.Scanning) TextButton(onClick = { viewModel.cancelScan() }) { Text("Cancel", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp)) } },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = scanState,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "cleaner_state"
            ) { state ->
                when (state) {
                    is ScanState.Idle -> IdleMinimal(storageInfo = storageInfo, hasPermission = hasPermission, isShizukuGranted = isShizukuGranted, onScan = { if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) viewModel.showPermissionDialog() else viewModel.startScan() }, onGrantShizuku = { viewModel.requestShizukuPermission(); viewModel.refreshShizuku() }, onDismissShizuku = {})
                    is ScanState.Scanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CleanerScanProgress(state.currentCategory, state.progress, state.filesScanned, state.foundSize) }
                    is ScanState.Results -> ResultsMinimal(state = state, isShizukuGranted = isShizukuGranted, onToggleItem = { c,i -> viewModel.toggleCategoryItem(c,i) }, onToggleDup = { c,h,p -> viewModel.toggleDuplicateFile(c,h,p) }, onClean = { viewModel.deleteSelected() }, onOpen = { p -> val ext = p.substringAfterLast(".", "").lowercase(); if (ext in setOf("jpg","jpeg","png","gif","webp","bmp","heic","heif","mp4","mkv","avi","mov","webm")) viewerPath = p else openFile(context, p, onNavigateToPdf, onNavigateToMusic) }, onLongPress = { cat -> viewModel.openGridView(cat) }, onGrantShizuku = { viewModel.requestShizukuPermission() })
                    is ScanState.Cleaning -> CleaningMinimal(state)
                    is ScanState.Done -> DoneMinimal(state.result) { viewModel.resetState() }
                    is ScanState.Error -> ErrorMinimal(state.message, onRetry = { viewModel.startScan() }, onDismiss = { viewModel.resetState() })
                }
            }
        }
    }
}

@Composable
private fun IdleMinimal(storageInfo: com.frerox.toolz.data.cleaner.StorageInfo, hasPermission: Boolean, isShizukuGranted: Boolean, onScan: ()->Unit, onGrantShizuku: ()->Unit, onDismissShizuku: ()->Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CleanerDashboardHeader(storageInfo = storageInfo, cleanableBytes = storageInfo.cleanableBytes)
        if (!isShizukuGranted) ShizukuBanner(isGranted = false, onGrantClick = onGrantShizuku, onDismiss = onDismissShizuku)
        if (!hasPermission) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.12f)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.GppMaybe, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Column(Modifier.weight(1f)) { Text("Full access recommended", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)); Text("Grant for deeper scan", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) {
            Icon(Icons.Rounded.TravelExplore, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Deep Scan", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp))
        }
        Text("9 analyzers • safe auto-select", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun ResultsMinimal(state: ScanState.Results, isShizukuGranted: Boolean, onToggleItem: (String,String)->Unit, onToggleDup: (String,String,String)->Unit, onClean: ()->Unit, onOpen:(String)->Unit, onLongPress:(CleanCategory)->Unit, onGrantShizuku: ()->Unit) {
    val allSelected = remember(state.categories) { state.categories.isNotEmpty() && state.categories.all { cat -> cat.items.all { item -> when(item){ is com.frerox.toolz.data.cleaner.CleanItem.GenericFile->item.file.isSelected; is com.frerox.toolz.data.cleaner.CleanItem.Corpse->item.entry.isSelected; is com.frerox.toolz.data.cleaner.CleanItem.Duplicate->item.group.files.any{it.isSelected}; is com.frerox.toolz.data.cleaner.CleanItem.EmptyDir->item.entry.isSelected; is com.frerox.toolz.data.cleaner.CleanItem.MediaFile->item.entry.isSelected; is com.frerox.toolz.data.cleaner.CleanItem.ApkFile->item.entry.isSelected; is com.frerox.toolz.data.cleaner.CleanItem.AppCache->item.entry.isSelected; is com.frerox.toolz.data.cleaner.CleanItem.UnusedApp->item.entry.isSelected } } } }
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Reclaimable", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.primary); Text(Formatter.formatFileSize(LocalContext.current, state.totalCleanableBytes), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp)); Text("${state.filesScanned} items scanned", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton(onClick = {}) { Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)) }
                    }
                }
            }
            if (!isShizukuGranted && state.categories.any { it.requiresShizuku }) {
                item { ShizukuBanner(isGranted = false, onGrantClick = onGrantShizuku, onDismiss = {}) }
            }
            items(state.categories.size) { idx ->
                val cat = state.categories[idx]
                CleanerCategoryCard(category = cat, isShizukuGranted = isShizukuGranted, onToggleItem = { id -> onToggleItem(cat.id, id) }, onToggleDuplicate = { h,p -> onToggleDup(cat.id, h, p) }, onOpenFile = onOpen, onLongPress = { onLongPress(cat) })
            }
        }
        if (state.selectedBytes > 0) {
            CleanerBottomBar(selectedBytes = state.selectedBytes, cleanableBytes = state.totalCleanableBytes, itemCount = state.categories.sumOf { it.items.size }, isAllSelected = allSelected, onClean = onClean, onToggleSelectAll = {}, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
        }
    }
}

@Composable private fun CleaningMinimal(state: ScanState.Cleaning) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CleaningServices, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { state.progress.coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth(0.6f).height(4.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.height(12.dp))
        Text(state.currentFile.substringAfterLast('/').ifBlank { "Cleaning…" }, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
@Composable private fun DoneMinimal(result: com.frerox.toolz.data.cleaner.CleanResult, onDone:()->Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } }
        Spacer(Modifier.height(12.dp))
        Text("Cleaned", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp))
        Text(Formatter.formatFileSize(LocalContext.current, result.freedBytes) + " • ${result.deletedCount} items", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (result.failedCount>0) Text("${result.failedCount} skipped", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDone, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Done", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)) }
    }
}
@Composable private fun ErrorMinimal(msg:String, onRetry:()->Unit, onDismiss:()->Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha=0.12f), modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error) } }
        Spacer(Modifier.height(12.dp))
        Text("Something went wrong", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp))
        Text(msg, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Try again", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
        TextButton(onClick = onDismiss) { Text("Dismiss", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
    }
}
private fun openFile(context: Context, path:String, onPdf:(Uri,String)->Unit, onMusic:(Uri)->Unit){ val file=File(path); if(!file.exists()) return; val ext=file.extension.lowercase(); val mediaUri=getMediaStoreUri(context,path,ext); val uri=mediaUri ?: runCatching{ FileProvider.getUriForFile(context,"com.frerox.toolz.fileprovider",file)}.getOrElse{ Uri.fromFile(file)}; when(ext){ "pdf"->onPdf(uri,file.name); "mp3","wav","m4a","ogg","flac"->onMusic(uri); else->runCatching{ val mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"; val intent=Intent(Intent.ACTION_VIEW).apply{ setDataAndType(uri,mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)}; runCatching{ context.startActivity(intent)}.getOrElse{ context.startActivity(Intent.createChooser(intent,"Open"))}}}}
private fun getMediaStoreUri(context: Context, path:String, ext:String): Uri? { val collection=when(ext){ "mp3","wav","m4a","ogg","flac"->MediaStore.Audio.Media.EXTERNAL_CONTENT_URI; "pdf"->MediaStore.Files.getContentUri("external"); "jpg","jpeg","png","gif","webp","bmp"->MediaStore.Images.Media.EXTERNAL_CONTENT_URI; "mp4","mkv","avi","mov","webm"->MediaStore.Video.Media.EXTERNAL_CONTENT_URI; else->return null}; return runCatching{ context.contentResolver.query(collection,arrayOf(MediaStore.MediaColumns._ID),"${MediaStore.MediaColumns.DATA}=?",arrayOf(path),null)?.use{ c-> if(c.moveToFirst()) ContentUris.withAppendedId(collection,c.getLong(0)) else null}}.getOrNull()}
