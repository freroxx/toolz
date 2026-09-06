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
import androidx.compose.foundation.clickable
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
    val gridCategory by viewModel.gridCategory.collectAsState()
    val trashEntries by viewModel.trashEntries.collectAsState()
    val trashTotalBytes by viewModel.trashTotalBytes.collectAsState()
    var showTrashSheet by remember { mutableStateOf(false) }
    var viewerPath by remember { mutableStateOf<String?>(null) }
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

    // Mandatory-gate flow with zero custom dialogs: media via the system Allow/Deny
    // dialog, all-files via settings (the OS offers no dialog for it). When the
    // system asks for a rationale first, we go straight to settings instead.
    fun openAllFilesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            settingsReturnLauncher.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.fromParts("package", context.packageName, null) })
        }
    }
    fun requestScanFlow() {
        val missing = viewModel.mandatoryMissing()
        if (missing.isEmpty()) { viewModel.startScan(); return }
        if (GateId.MEDIA in missing && mediaPermissions != null && !mediaPermissions.allPermissionsGranted) {
            if (mediaPermissions.shouldShowRationale) { viewModel.openGate(GateId.MEDIA); return }
            scanQueued = true
            mediaPermissions.launchMultiplePermissionRequest()
            return
        }
        if (GateId.ALL_FILES in missing) { openAllFilesSettings(); return }
    }
    // V4 auto-clear entry: disclosure first when the service isn't enabled,
    // otherwise start immediately and show live progress.
    fun requestAutoClear(pkgs: List<Pair<String, String>>) {
        if (pkgs.isEmpty()) return
        viewModel.refreshAccess()
        if (!viewModel.isAutoClearAvailable()) { autoClearAsk = pkgs; return }
        viewModel.resetAutoClear()
        if (viewModel.startAutoClearApps(pkgs)) autoClearOpen = true
        else viewModel.postNotice("Auto-clear service is still starting — try again in a moment")
    }

    // Refresh grants when returning from Settings / system dialogs (mandatory-gate source of truth)
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermission()
                viewModel.refreshShizuku()
                viewModel.refreshAccess()
                val pending = viewModel.pollPendingAutoClear()
                if (pending != null && viewModel.isAutoClearAvailable()) {
                    requestAutoClear(pending)
                }
            }
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
            else viewModel.openGate(viewModel.mandatoryMissing().first())
        }
    }
    BackHandler(enabled = gridCategory != null || showTrashSheet) {
        if (showTrashSheet) showTrashSheet = false
        else viewModel.closeGridView()
    }

    autoClearAsk?.let { pkgs ->
        AutoClearDisclosure(
            appCount = pkgs.size,
            onEnableService = {
                viewModel.setPendingAutoClear(pkgs)
                autoClearAsk = null
                viewModel.openGate(GateId.AUTOMATION)
            },
            onDismiss = { autoClearAsk = null }
        )
    }
    if (autoClearOpen) {
        AutoClearProgressDialog(
            state = autoClearState,
            onStop = { viewModel.stopAutoClear() },
            onRescan = { autoClearOpen = false; viewModel.startScan() },
            onOpenAppSettings = { pkg -> viewModel.openAppSettings(pkg) },
            onClose = { autoClearOpen = false }
        )
    }

    if (showShizukuSetup) {
        ShizukuSetupBottomSheet(onDismiss = { showShizukuSetup = false; viewModel.refreshShizuku(); viewModel.refreshAccess() }, onAuthorized = { viewModel.refreshShizuku(); viewModel.refreshAccess() })
    }

    var showCleanConfirm by remember { mutableStateOf(false) }

    if (showCleanConfirm) {
        val currentResults = scanState as? ScanState.Results
        val selBytes = currentResults?.selectedBytes ?: 0L
        AlertDialog(
            onDismissRequest = { showCleanConfirm = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Clean selected items?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${Formatter.formatFileSize(context, selBytes)} will be freed.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Deleted files and folders are safely preserved in Cleaner Trash for 7 days so you can undo anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanConfirm = false
                        viewModel.deleteSelected()
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Clean Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (viewerPath != null) {
        val currentResults = scanState as? ScanState.Results
        var isViewerSelected = false
        var toggleViewerAction: (() -> Unit)? = null
        if (currentResults != null) {
            for (cat in currentResults.categories) {
                for (item in cat.items) {
                    when (item) {
                        is com.frerox.toolz.data.cleaner.CleanItem.GenericFile -> if (item.file.path == viewerPath) {
                            isViewerSelected = item.file.isSelected
                            toggleViewerAction = { viewModel.toggleCategoryItem(cat.id, item.file.path) }
                        }
                        is com.frerox.toolz.data.cleaner.CleanItem.MediaFile -> if (item.entry.path == viewerPath) {
                            isViewerSelected = item.entry.isSelected
                            toggleViewerAction = { viewModel.toggleCategoryItem(cat.id, item.entry.path) }
                        }
                        is com.frerox.toolz.data.cleaner.CleanItem.ApkFile -> if (item.entry.path == viewerPath) {
                            isViewerSelected = item.entry.isSelected
                            toggleViewerAction = { viewModel.toggleCategoryItem(cat.id, item.entry.path) }
                        }
                        is com.frerox.toolz.data.cleaner.CleanItem.Duplicate -> {
                            val f = item.group.files.find { it.path == viewerPath }
                            if (f != null) {
                                isViewerSelected = f.isSelected
                                toggleViewerAction = { viewModel.toggleDuplicateFile(cat.id, item.group.hash, f.path) }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
        CleanerMediaViewer(
            filePath = viewerPath!!,
            isSelected = if (toggleViewerAction != null) isViewerSelected else null,
            onToggleSelect = toggleViewerAction,
            onDismiss = { viewerPath = null }
        )
    }

    if (gridCategory != null) {
        CleanerDetailSheet(
            category = gridCategory!!,
            onToggleItem = { id -> viewModel.toggleCategoryItem(gridCategory!!.id, id) },
            onToggleDuplicate = { h, p -> viewModel.toggleDuplicateFile(gridCategory!!.id, h, p) },
            onSetDuplicateKeeper = { h, p -> viewModel.setDuplicateKeeper(gridCategory!!.id, h, p) },
            onSetAllDuplicateKeepers = { newest -> viewModel.setAllDuplicateKeepers(newest) },
            onSetItemsSelected = { ids, sel -> viewModel.setItemsSelected(gridCategory!!.id, ids, sel) },
            onAutoClear = { requestAutoClear(viewModel.selectedAppCache()) },
            onAutoClearApp = { pkg, label -> requestAutoClear(listOf(pkg to label)) },
            onOpenAppSettings = { pkg -> viewModel.openAppSettings(pkg) },
            onExcludeApp = { pkg -> viewModel.exclude(pkg) },
            onClean = { showCleanConfirm = true },
            onOpenFile = { p ->
                val ext = p.substringAfterLast(".", "").lowercase()
                if (ext in setOf("jpg","jpeg","png","gif","webp","bmp","heic","heif","mp4","mkv","avi","mov","webm")) viewerPath = p else openFile(context, p, onNavigateToPdf, onNavigateToMusic)
            },
            onDismiss = { viewModel.closeGridView() }
        )
    }

    if (showTrashSheet) {
        CleanerTrashSheet(
            trashEntries = trashEntries,
            trashTotalBytes = trashTotalBytes,
            onRestoreItem = { id -> viewModel.restoreTrashItem(id) },
            onRestoreSelected = { ids -> viewModel.restoreSelectedTrash(ids) },
            onRestoreAll = { viewModel.restoreAllTrash() },
            onDeleteItemPermanently = { id -> viewModel.deleteTrashItemPermanently(id) },
            onDeleteSelectedPermanently = { ids -> viewModel.deleteSelectedTrashPermanently(ids) },
            onEmptyTrash = { viewModel.emptyTrash() },
            onDismiss = { showTrashSheet = false }
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
                navigationIcon = { IconButton(onClick = { if (showTrashSheet) showTrashSheet = false else if (gridCategory != null) viewModel.closeGridView() else onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (scanState is ScanState.Scanning) {
                        TextButton(onClick = { viewModel.cancelScan() }) {
                            Text("Cancel", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
                        }
                    } else {
                        if (scanState is ScanState.Results || scanState is ScanState.Done) {
                            IconButton(onClick = { viewModel.startScan() }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Rescan")
                            }
                        }
                        IconButton(onClick = { showTrashSheet = true }) {
                            BadgedBox(
                                badge = {
                                    if (trashEntries.isNotEmpty()) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text("${trashEntries.size}")
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Cleaner Trash")
                            }
                        }
                    }
                },
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
                        IdleMinimal(
                            storageInfo = storageInfo,
                            trashTotalBytes = trashTotalBytes,
                            trashCount = trashEntries.size,
                            missing = missing,
                            onGrantAllFiles = { openAllFilesSettings() },
                            onGrantMedia = {
                                if (mediaPermissions != null && !mediaPermissions.allPermissionsGranted && !mediaPermissions.shouldShowRationale) {
                                    scanQueued = true
                                    mediaPermissions.launchMultiplePermissionRequest()
                                } else viewModel.openGate(GateId.MEDIA)
                            },
                            onScan = { requestScanFlow() },
                            onOpenTrash = { showTrashSheet = true }
                        )
                    }
                    is ScanState.Scanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CleanerScanProgress(state.currentCategory, state.progress, state.filesScanned, state.foundSize) }
                    is ScanState.Results -> {
                        val allSel = viewModel.areAllSelected(state.categories)
                        ResultsMinimal(
                            state = state,
                            storageInfo = storageInfo,
                            trashBytes = trashTotalBytes,
                            trashCount = trashEntries.size,
                            allSelected = allSel,
                            onToggleAll = { sel -> viewModel.setAllSelected(sel) },
                            onSelectSafeOnly = { viewModel.selectSafeOnly() },
                            onToggleItem = { c, i -> viewModel.toggleCategoryItem(c, i) },
                            onToggleDup = { c, h, p -> viewModel.toggleDuplicateFile(c, h, p) },
                            onClean = { showCleanConfirm = true },
                            onToggleCat = { id, sel -> viewModel.setCategorySelected(id, sel) },
                            onRescan = { viewModel.startScan() },
                            onFixBlocked = { id -> when (id) { "app_cache" -> viewModel.openGate(com.frerox.toolz.data.cleaner.access.GateId.USAGE_ACCESS); "corpse" -> showShizukuSetup = true; else -> viewModel.openGate(com.frerox.toolz.data.cleaner.access.GateId.ALL_FILES) } },
                            onOpen = { p -> val ext = p.substringAfterLast(".", "").lowercase(); if (ext in setOf("jpg","jpeg","png","gif","webp","bmp","heic","heif","mp4","mkv","avi","mov","webm")) viewerPath = p else openFile(context, p, onNavigateToPdf, onNavigateToMusic) },
                            onOpenSheet = { cat -> viewModel.openGridView(cat) },
                            onOpenTrash = { showTrashSheet = true }
                        )
                    }
                    is ScanState.Cleaning -> CleaningMinimal(state, onCancel = { viewModel.cancelScan() })
                    is ScanState.Done -> DoneMinimal(
                        result = state.result,
                        onUndo = { viewModel.undoClean() },
                        onDone = { viewModel.resetState() },
                        onScheduleReminder = { viewModel.scheduleWeeklyReminder() },
                        onAutoClearBatch = { pkgs -> requestAutoClear(pkgs) },
                        onFix = { item ->
                            when (item.fix) {
                                com.frerox.toolz.data.cleaner.CleanFix.OPEN_APP_SETTINGS -> viewModel.openAppSettings(item.pkg ?: item.key)
                                com.frerox.toolz.data.cleaner.CleanFix.OPEN_GATE_ALL_FILES -> viewModel.openGate(com.frerox.toolz.data.cleaner.access.GateId.ALL_FILES)
                                com.frerox.toolz.data.cleaner.CleanFix.OPEN_GATE_USAGE -> viewModel.openGate(com.frerox.toolz.data.cleaner.access.GateId.USAGE_ACCESS)
                                // Auto-clear (M16) handles internal caches; app settings is the per-app fallback.
                                com.frerox.toolz.data.cleaner.CleanFix.ENABLE_AUTO_CLEAR -> requestAutoClear(listOf((item.pkg ?: item.key) to item.label))
                                com.frerox.toolz.data.cleaner.CleanFix.OPEN_SHIZUKU_SETUP -> showShizukuSetup = true
                                null -> {}
                            }
                        }
                    )
                    is ScanState.Error -> ErrorMinimal(state.message, onRetry = { viewModel.startScan() }, onDismiss = { viewModel.resetState() })
                }
            }
        }
    }
}

@Composable
private fun IdleMinimal(
    storageInfo: com.frerox.toolz.data.cleaner.StorageInfo,
    trashTotalBytes: Long,
    trashCount: Int,
    missing: List<GateId>,
    onGrantAllFiles: () -> Unit,
    onGrantMedia: () -> Unit,
    onScan: () -> Unit,
    onOpenTrash: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CleanerDashboardHeader(
            storageInfo = storageInfo,
            cleanableBytes = storageInfo.cleanableBytes,
            trashBytes = trashTotalBytes,
            onOpenTrash = onOpenTrash
        )

        // Dedicated Trash Card if trash contains deleted files
        if (trashTotalBytes > 0) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTrash() }
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                null,
                                Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Cleaner Trash",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        )
                        Text(
                            "${Formatter.formatFileSize(context, trashTotalBytes)} in trash • $trashCount item(s)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = onOpenTrash,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Open", style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp))
                    }
                }
            }
        }

        // One row per missing permission: icon + one-line title + Grant action. Zero dialogs.
        missing.forEach { id ->
            when (id) {
                GateId.ALL_FILES -> {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.Lock, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Allow all-files access", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp), modifier = Modifier.weight(1f))
                            Button(onClick = onGrantAllFiles, shape = RoundedCornerShape(20.dp)) { Text("Grant", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
                        }
                    }
                }
                GateId.MEDIA -> {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.PermMedia, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Allow photos & videos", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp), modifier = Modifier.weight(1f))
                            Button(onClick = onGrantMedia, shape = RoundedCornerShape(20.dp)) { Text("Grant", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
                        }
                    }
                }
                else -> {}
            }
        }
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) {
            Icon(if (missing.isNotEmpty()) Icons.Rounded.Lock else Icons.Rounded.TravelExplore, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (missing.isNotEmpty()) "Grant access to scan" else "Scan", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp))
        }
    }
}

@Composable
private fun ResultsMinimal(
    state: ScanState.Results,
    storageInfo: com.frerox.toolz.data.cleaner.StorageInfo,
    trashBytes: Long,
    trashCount: Int,
    allSelected: Boolean,
    onToggleAll: (Boolean) -> Unit,
    onSelectSafeOnly: () -> Unit,
    onToggleItem: (String, String) -> Unit,
    onToggleDup: (String, String, String) -> Unit,
    onClean: () -> Unit,
    onToggleCat: (String, Boolean) -> Unit,
    onRescan: () -> Unit,
    onFixBlocked: (String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenSheet: (CleanCategory) -> Unit,
    onOpenTrash: () -> Unit
) {
    val context = LocalContext.current
    // Genuinely empty scan → celebratory empty state, not a blank list
    if (state.categories.isEmpty() || state.categories.all { it.items.isEmpty() }) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } }
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
        androidx.compose.foundation.lazy.LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Live storage projection bar: shows reclaimable storage in real time against used/total
            item {
                CleanerDashboardHeader(
                    storageInfo = storageInfo,
                    cleanableBytes = state.selectedBytes,
                    trashBytes = trashBytes,
                    onOpenTrash = onOpenTrash
                )
            }

            // Selection actions header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${Formatter.formatFileSize(context, state.selectedBytes)} selected",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onToggleAll(true) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Select All", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium))
                        }
                        OutlinedButton(
                            onClick = { onToggleAll(false) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Deselect All", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                }
            }

            if (trashBytes > 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTrash() }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                null,
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Trash: ${Formatter.formatFileSize(context, trashBytes)}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    "$trashCount item(s) • Empty now to reclaim space permanently",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = onOpenTrash) {
                                Text("View", style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp))
                            }
                        }
                    }
                }
            }

            items(state.categories.size, key = { idx -> state.categories[idx].id }) { idx ->
                val cat = state.categories[idx]
                CleanerCategoryCard(
                    category = cat,
                    totalCleanableBytes = state.totalCleanableBytes,
                    onToggleItem = { id -> onToggleItem(cat.id, id) },
                    onToggleDuplicate = { h, p -> onToggleDup(cat.id, h, p) },
                    onToggleAll = { sel -> onToggleCat(cat.id, sel) },
                    onOpenFile = onOpen,
                    onOpenSheet = { onOpenSheet(cat) },
                    onFix = { onFixBlocked(cat.id) }
                )
                if (cat.truncatedCount > 0) {
                    Text(
                        "Showing top ${cat.items.size} of ${cat.items.size + cat.truncatedCount} — biggest first",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }
        }
        CleanerBottomBar(
            selectedBytes = state.selectedBytes,
            cleanableBytes = state.totalCleanableBytes,
            itemCount = state.categories.sumOf { it.items.size },
            onClean = onClean,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        )
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

@Composable
private fun DoneMinimal(
    result: com.frerox.toolz.data.cleaner.CleanResult,
    onUndo: () -> Unit,
    onDone: () -> Unit,
    onScheduleReminder: () -> Unit,
    onAutoClearBatch: ((List<Pair<String, String>>) -> Unit)? = null,
    onFix: (com.frerox.toolz.data.cleaner.FailedItem) -> Unit
) {
    var showFailed by remember { mutableStateOf(false) }
    var reminderScheduled by remember { mutableStateOf(false) }
    val actionable = remember(result) { result.failedItems.filter { it.fix != null } }
    val autoClearItems = remember(actionable) {
        actionable.filter { it.fix == com.frerox.toolz.data.cleaner.CleanFix.ENABLE_AUTO_CLEAR }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } }
        Spacer(Modifier.height(12.dp))
        Text("Cleaning Complete", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp))
        Text(Formatter.formatFileSize(LocalContext.current, result.freedBytes) + " freed • ${result.clearedCount} cleared", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Honest breakdown — nothing-to-do is reported, never blamed on permissions.
        val extras = buildList {
            if (result.alreadyCleanCount > 0) add("${result.alreadyCleanCount} already clean")
            if (result.emptyDirsRemoved > 0) add("${result.emptyDirsRemoved} empty folders tidied")
            if (actionable.isNotEmpty()) add("${actionable.size} need attention")
        }
        if (extras.isNotEmpty()) Text(extras.joinToString(" • "), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Restore, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Protected in Trash for 7 days", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (autoClearItems.isNotEmpty() && onAutoClearBatch != null) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onAutoClearBatch(autoClearItems.map { (it.pkg ?: it.key) to it.label }) },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Icon(Icons.Rounded.Cached, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Auto-clear ${autoClearItems.size} App Caches",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }

        if (actionable.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val visible = if (showFailed) actionable else actionable.take(3)
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
            if (actionable.size > 3) {
                TextButton(onClick = { showFailed = !showFailed }) {
                    Text(if (showFailed) "Show less" else "View all ${actionable.size}", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onDone, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Done", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onUndo) { Text("Undo clean", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)) }
            if (!reminderScheduled) {
                TextButton(onClick = {
                    reminderScheduled = true
                    onScheduleReminder()
                }) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remind weekly", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp))
                }
            }
        }
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
