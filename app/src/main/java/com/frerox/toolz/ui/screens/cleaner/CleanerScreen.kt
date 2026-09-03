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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.ScanState
import com.frerox.toolz.data.cleaner.access.AccessGate
import com.frerox.toolz.data.cleaner.access.GateId
import com.frerox.toolz.data.cleaner.access.GateState
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.components.cleaner.*
import com.frerox.toolz.ui.theme.toolzBackground
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)

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
    val accessGates by viewModel.accessState.collectAsState()
    var viewerPath by remember { mutableStateOf<String?>(null) }
    var showMediaRationale by remember { mutableStateOf(false) }
    var scanQueued by remember { mutableStateOf(false) }
    // V4 auto-clear run state
    var autoClearAsk by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var autoClearOpen by remember { mutableStateOf(false) }
    var showShizukuSetup by remember { mutableStateOf(false) }
    val autoClearState by viewModel.autoClear.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Runtime Allow/Deny system dialogs (media on 33+). All-files/Usage/Automation are settings-only by OS design.
    val mediaPerms = remember { AccessGate.mediaRuntimePermissions() }
    val mediaPermissions = if (mediaPerms.isNotEmpty()) rememberMultiplePermissionsState(mediaPerms) else null

    val settingsReturnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.checkPermission(); viewModel.dismissPermissionDialog(); viewModel.refreshAccess()
    }
    // Refresh grants when returning from Settings / system dialogs (mandatory-gate source of truth)
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { viewModel.checkPermission(); viewModel.refreshShizuku(); viewModel.refreshAccess() }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) { viewModel.checkPermission(); viewModel.refreshShizuku(); viewModel.refreshAccess() }
    // After a runtime grant, continue a queued scan or refresh states
    LaunchedEffect(mediaPermissions?.allPermissionsGranted) {
        viewModel.refreshAccess()
        if (mediaPermissions?.allPermissionsGranted == true && scanQueued) {
            scanQueued = false
            if (viewModel.mandatoryMissing().isEmpty()) viewModel.startScan()
            else viewModel.showPermissionDialog()
        }
    }
    BackHandler(enabled = gridCategory != null) { viewModel.closeGridView() }

    // Mandatory-gate flow: media via system Allow/Deny dialog, all-files via settings (OS has no dialog for it)
    fun requestScanFlow() {
        val missing = viewModel.mandatoryMissing()
        if (missing.isEmpty()) { viewModel.startScan(); return }
        if (GateId.MEDIA in missing && mediaPermissions != null && !mediaPermissions.allPermissionsGranted) {
            if (mediaPermissions.shouldShowRationale) { showMediaRationale = true; return }
            scanQueued = true
            mediaPermissions.launchMultiplePermissionRequest()
            return
        }
        viewModel.showPermissionDialog()
    }
    // V4 auto-clear entry: disclosure first when the service isn't enabled,
    // otherwise start immediately and show live progress.
    fun requestAutoClear(pkgs: List<Pair<String, String>>) {
        if (pkgs.isEmpty()) return
        viewModel.refreshAccess()
        if (!viewModel.isAutoClearAvailable()) { autoClearAsk = pkgs; return }
        viewModel.resetAutoClear()
        if (viewModel.startAutoClear(pkgs.map { it.first })) autoClearOpen = true
    }

    if (showMediaRationale) {
        AlertDialog(
            onDismissRequest = { showMediaRationale = false; scanQueued = false },
            icon = { Icon(Icons.Rounded.PermMedia, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) },
            title = { Text("Media access", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp)) },
            text = { Text("File Cleaner needs photo, video and audio access to find media clutter and large media. Your files stay on-device.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), textAlign = TextAlign.Center) },
            confirmButton = {
                Button(onClick = { showMediaRationale = false; mediaPermissions?.launchMultiplePermissionRequest() }, shape = RoundedCornerShape(20.dp)) { Text("Allow", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showMediaRationale = false; viewModel.openGate(GateId.MEDIA) }) { Text("Settings", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
                    TextButton(onClick = { showMediaRationale = false; scanQueued = false }) { Text("Not now", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    autoClearAsk?.let { pkgs ->
        AutoClearDisclosure(
            appCount = pkgs.size,
            onEnableService = { autoClearAsk = null; viewModel.openGate(GateId.AUTOMATION) },
            onDismiss = { autoClearAsk = null }
        )
    }
    if (autoClearOpen) {
        AutoClearProgressDialog(
            state = autoClearState,
            onStop = { viewModel.stopAutoClear() },
            onRescan = { autoClearOpen = false; viewModel.startScan() },
            onClose = { autoClearOpen = false }
        )
    }

    if (showShizukuSetup) {
        ShizukuSetupBottomSheet(onDismiss = { showShizukuSetup = false; viewModel.refreshShizuku(); viewModel.refreshAccess() })
    }

    if (showPermDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionDialog() },
            icon = { Icon(Icons.Rounded.FolderSpecial, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) },
            title = { Text("Full access required", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp)) },
            text = { Text("Deep Scan is locked until you grant all-files access. Without it, leftovers, empty folders and system junk can't be seen — a scan would come back empty.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), textAlign = TextAlign.Center) },
            confirmButton = {
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        settingsReturnLauncher.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.fromParts("package", context.packageName, null) })
                    }
                }, shape = RoundedCornerShape(20.dp)) { Text("Open settings", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissPermissionDialog() }) { Text("Later", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) } },
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
            onSelectAll = { sel -> viewModel.setCategorySelected(gridCategory!!.id, sel) },
            allSelected = viewModel.isCategorySelected(gridCategory!!),
            onAutoClear = { requestAutoClear(viewModel.selectedAppCache()) },
            onAutoClearApp = { pkg, label -> requestAutoClear(listOf(pkg to label)) },
            onOpenAppSettings = { pkg -> viewModel.openAppSettings(pkg) },
            onExcludeApp = { pkg -> viewModel.exclude(pkg) },
            onClean = { viewModel.deleteSelected() },
            onOpenFile = { p ->
                val ext = p.substringAfterLast(".", "").lowercase()
                if (ext in setOf("jpg","jpeg","png","gif","webp","bmp","heic","heif","mp4","mkv","avi","mov","webm")) viewerPath = p else openFile(context, p, onNavigateToPdf, onNavigateToMusic)
            },
            onDismiss = { viewModel.closeGridView() }
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val title = "File Cleaner"
    val subtitle = when (val s = scanState) {
        is ScanState.Scanning -> s.currentCategory.ifBlank { "Scanning…" }
        is ScanState.Results -> "${Formatter.formatFileSize(context, s.totalCleanableBytes)} found"
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
        },
        snackbarHost = {
            val undoMsg by viewModel.undoEvent.collectAsState()
            if (undoMsg != null) {
                // Message-only confirmation: the Done screen holds the single Undo action.
                Snackbar(
                    modifier = Modifier.padding(12.dp),
                    dismissAction = { TextButton(onClick = { viewModel.consumeUndo() }) { Text("OK") } }
                ) { Text(undoMsg!!) }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Animate only on phase change (Idle/Scanning/Results/…) — checkbox toggles must not re-fade the list
            AnimatedContent(
                targetState = scanState::class.simpleName,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "cleaner_state"
            ) { _ ->
                when (val state = scanState) {
                    is ScanState.Idle -> {
                        val missing = viewModel.mandatoryMissing()
                        val missingLabel = missing.joinToString(" and ") {
                            when (it) {
                                com.frerox.toolz.data.cleaner.access.GateId.ALL_FILES -> "access to all files"
                                com.frerox.toolz.data.cleaner.access.GateId.MEDIA -> "access to photos and videos"
                                else -> "required access"
                            }
                        }
                        val showAutomationRow = accessGates.any {
                            it.id == com.frerox.toolz.data.cleaner.access.GateId.AUTOMATION &&
                                it.state != com.frerox.toolz.data.cleaner.access.GateState.GRANTED
                        }
                        IdleMinimal(storageInfo = storageInfo, scanLocked = missing.isNotEmpty(),
                            missingLabel = missingLabel, showAutomationRow = showAutomationRow,
                            onScan = { requestScanFlow() }, onSetupShizuku = { showShizukuSetup = true })
                    }
                    is ScanState.Scanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CleanerScanProgress(state.currentCategory, state.progress, state.filesScanned, state.foundSize) }
                    is ScanState.Results -> ResultsMinimal(state = state, onToggleItem = { c,i -> viewModel.toggleCategoryItem(c,i) }, onToggleDup = { c,h,p -> viewModel.toggleDuplicateFile(c,h,p) }, onClean = { viewModel.deleteSelected() }, onToggleAll = { sel -> viewModel.setAllSelected(sel) }, onToggleCat = { id, sel -> viewModel.setCategorySelected(id, sel) }, onRescan = { viewModel.startScan() }, onFixBlocked = { id -> when (id) { "app_cache" -> viewModel.openGate(com.frerox.toolz.data.cleaner.access.GateId.USAGE_ACCESS); else -> viewModel.openGate(com.frerox.toolz.data.cleaner.access.GateId.ALL_FILES) } }, onOpen = { p -> val ext = p.substringAfterLast(".", "").lowercase(); if (ext in setOf("jpg","jpeg","png","gif","webp","bmp","heic","heif","mp4","mkv","avi","mov","webm")) viewerPath = p else openFile(context, p, onNavigateToPdf, onNavigateToMusic) }, onOpenSheet = { cat -> viewModel.openGridView(cat) }, allSelected = viewModel.areAllSelected(state.categories))
                    is ScanState.Cleaning -> CleaningMinimal(state, onCancel = { viewModel.cancelScan() })
                    is ScanState.Done -> DoneMinimal(result = state.result, onUndo = { viewModel.undoClean() }, onDone = { viewModel.resetState() }, onFix = { item ->
                        when (item.fix) {
                            com.frerox.toolz.data.cleaner.CleanFix.OPEN_APP_SETTINGS -> viewModel.openAppSettings(item.pkg ?: item.key)
                            com.frerox.toolz.data.cleaner.CleanFix.OPEN_GATE_ALL_FILES -> viewModel.openGate(com.frerox.toolz.data.cleaner.access.GateId.ALL_FILES)
                            com.frerox.toolz.data.cleaner.CleanFix.OPEN_GATE_USAGE -> viewModel.openGate(com.frerox.toolz.data.cleaner.access.GateId.USAGE_ACCESS)
                            // Auto-clear (M16) handles internal caches; app settings is the per-app fallback.
                            com.frerox.toolz.data.cleaner.CleanFix.ENABLE_AUTO_CLEAR -> requestAutoClear(listOf((item.pkg ?: item.key) to item.label))
                            com.frerox.toolz.data.cleaner.CleanFix.OPEN_SHIZUKU_SETUP -> showShizukuSetup = true
                            null -> {}
                        }
                    })
                    is ScanState.Error -> ErrorMinimal(state.message, onRetry = { viewModel.startScan() }, onDismiss = { viewModel.resetState() })
                }
            }
        }
    }
}

@Composable
private fun IdleMinimal(storageInfo: com.frerox.toolz.data.cleaner.StorageInfo, scanLocked: Boolean, missingLabel: String, showAutomationRow: Boolean, onScan: ()->Unit, onSetupShizuku: ()->Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CleanerDashboardHeader(storageInfo = storageInfo, cleanableBytes = storageInfo.cleanableBytes)
        // Single access card: names exactly what is missing, one action that fixes it.
        if (scanLocked) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Lock, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("One step to start", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp))
                        Text("Allow $missingLabel so the scan can see everything.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) {
            Icon(if (scanLocked) Icons.Rounded.Lock else Icons.Rounded.TravelExplore, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (scanLocked) "Grant access to scan" else "Deep Scan", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp))
        }
        if (!scanLocked && showAutomationRow) {
            OutlinedButton(onClick = onSetupShizuku, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Rounded.Bolt, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Faster cache clearing with Shizuku", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp))
            }
        }
        Text("6 checks • deleted files stay in trash for 7 days", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun ResultsMinimal(state: ScanState.Results, onToggleItem: (String,String)->Unit, onToggleDup: (String,String,String)->Unit, onClean: ()->Unit, onToggleAll: (Boolean)->Unit, onToggleCat: (String, Boolean)->Unit, onRescan: ()->Unit, onFixBlocked: (String)->Unit, onOpen:(String)->Unit, onOpenSheet:(CleanCategory)->Unit, allSelected: Boolean) {
    // Genuinely empty scan → celebratory empty state, not a blank list
    if (state.categories.isEmpty() || state.categories.all { it.items.isEmpty() }) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } }
            Spacer(Modifier.height(16.dp))
            Text("Sparkling clean", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(4.dp))
            Text("${state.filesScanned} files checked • nothing worth deleting", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRescan, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) { Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Scan again", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            item {
                // One hero number. Rescan lives in the top bar — no duplicate button.
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Formatter.formatFileSize(LocalContext.current, state.totalCleanableBytes), style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                    Text("reclaimable • picked for you — uncheck anything to keep", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            items(state.categories.size, key = { idx -> state.categories[idx].id }) { idx ->
                val cat = state.categories[idx]
                CleanerCategoryCard(category = cat,
                    onToggleItem = { id -> onToggleItem(cat.id, id) },
                    onToggleDuplicate = { h, p -> onToggleDup(cat.id, h, p) },
                    onToggleAll = { sel -> onToggleCat(cat.id, sel) },
                    onOpenFile = onOpen, onOpenSheet = { onOpenSheet(cat) },
                    onFix = { onFixBlocked(cat.id) })
                if (cat.truncatedCount > 0) {
                    Text("Showing top ${cat.items.size} of ${cat.items.size + cat.truncatedCount} — biggest first",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                }
            }
        }
        // Always visible while results exist: disabled Clean with helper beats a vanishing CTA.
        CleanerBottomBar(selectedBytes = state.selectedBytes, cleanableBytes = state.totalCleanableBytes, itemCount = state.categories.sumOf { it.items.size }, isAllSelected = allSelected, onClean = onClean, onToggleSelectAll = { onToggleAll(!allSelected) }, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }
}

@Composable private fun CleaningMinimal(state: ScanState.Cleaning, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CleaningServices, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } }
        Spacer(Modifier.height(16.dp))
        Text("Cleaning…", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp))
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { state.progress.coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth(0.6f).height(6.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(20.dp), modifier = Modifier.height(44.dp)) { Text("Cancel", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
    }
}
@Composable private fun DoneMinimal(result: com.frerox.toolz.data.cleaner.CleanResult, onUndo:()->Unit, onDone:()->Unit, onFix:(com.frerox.toolz.data.cleaner.FailedItem)->Unit) {
    var showFailed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } }
        Spacer(Modifier.height(12.dp))
        Text("Cleaned", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp))
        Text(Formatter.formatFileSize(LocalContext.current, result.freedBytes) + " • ${result.clearedCount} cleared", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Honest breakdown — nothing-to-do is reported, never blamed on permissions.
        val extras = buildList {
            if (result.alreadyCleanCount > 0) add("${result.alreadyCleanCount} already clean")
            if (result.failedItems.isNotEmpty()) add("${result.failedItems.size} need attention")
        }
        if (extras.isNotEmpty()) Text(extras.joinToString(" • "), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (result.failedItems.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val visible = if (showFailed) result.failedItems else result.failedItems.take(3)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                visible.forEach { item ->
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), maxLines = 1)
                                Text(item.reason, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                            if (item.fix != null) {
                                TextButton(onClick = { onFix(item) }) {
                                    Text(when (item.fix) {
                                        com.frerox.toolz.data.cleaner.CleanFix.OPEN_APP_SETTINGS -> "Settings"
                                        com.frerox.toolz.data.cleaner.CleanFix.OPEN_GATE_ALL_FILES -> "Grant"
                                        com.frerox.toolz.data.cleaner.CleanFix.OPEN_GATE_USAGE -> "Grant"
                                        com.frerox.toolz.data.cleaner.CleanFix.ENABLE_AUTO_CLEAR -> "Clear"
                                        com.frerox.toolz.data.cleaner.CleanFix.OPEN_SHIZUKU_SETUP -> "Setup"
                                    }, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
                                }
                            }
                        }
                    }
                }
            }
            if (result.failedItems.size > 3) {
                TextButton(onClick = { showFailed = !showFailed }) {
                    Text(if (showFailed) "Show less" else "View all ${result.failedItems.size}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
                }
            }
        }
        if (result.emptyDirsRemoved > 0) Text("Also tidied ${result.emptyDirsRemoved} empty folders", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text("Deleted files stay in trash for 7 days", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDone, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Done", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)) }
        TextButton(onClick = onUndo) { Text("Undo clean", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
    }
}
@Composable private fun ErrorMinimal(msg:String, onRetry:()->Unit, onDismiss:()->Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.size(56.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onErrorContainer) } }
        Spacer(Modifier.height(16.dp))
        Text("The scan hit a snag", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp))
        Text(msg, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text("Your files were not touched.", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Try again", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
        TextButton(onClick = onDismiss) { Text("Start over", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
    }
}
private fun openFile(context: Context, path:String, onPdf:(Uri,String)->Unit, onMusic:(Uri)->Unit){ val file=File(path); if(!file.exists()) return; val ext=file.extension.lowercase(); val mediaUri=getMediaStoreUri(context,path,ext); val authority="${context.packageName}.fileprovider"; val uri=mediaUri ?: runCatching{ FileProvider.getUriForFile(context,authority,file)}.getOrElse{ Uri.fromFile(file)}; when(ext){ "pdf"->onPdf(uri,file.name); "mp3","wav","m4a","ogg","flac"->onMusic(uri); else->runCatching{ val mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"; val intent=Intent(Intent.ACTION_VIEW).apply{ setDataAndType(uri,mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)}; runCatching{ context.startActivity(intent)}.getOrElse{ context.startActivity(Intent.createChooser(intent,"Open"))}}}}
private fun getMediaStoreUri(context: Context, path:String, ext:String): Uri? { val file=File(path); val name=file.name; return runCatching {
    when(ext){
        "mp3","wav","m4a","ogg","flac","aac" -> context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(name), null)?.use { c-> if(c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,c.getLong(0)) else null }
        "jpg","jpeg","png","gif","webp","bmp","heic","heif" -> context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(name), null)?.use { c-> if(c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0)) else null }
        "mp4","mkv","avi","mov","webm" -> context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(name), null)?.use { c-> if(c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,c.getLong(0)) else null }
        else -> null
    }
}.getOrNull()}
