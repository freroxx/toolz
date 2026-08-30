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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.frerox.toolz.data.purgeshot.PurgeShotEntity
import com.frerox.toolz.data.purgeshot.PurgeShotPreset
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val performanceMode = LocalPerformanceMode.current
    val coroutineScope = rememberCoroutineScope()
    val shizukuAuthFlow by viewModel.shizukuAuthorized.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
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

    fun hasMediaPerm(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    fun hasNotifPerm(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    var hasMediaState: Boolean by remember { mutableStateOf(hasMediaPerm()) }
    var hasNotifState: Boolean by remember { mutableStateOf(hasNotifPerm()) }
    var hasOverlayState: Boolean by remember { mutableStateOf(if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) true else android.provider.Settings.canDrawOverlays(context)) }
    var hasA11yState: Boolean by remember { mutableStateOf(com.frerox.toolz.service.PurgeShotAccessibilityService.isEnabled(context)) }
    var hasAllFilesState: Boolean by remember { mutableStateOf(viewModel.hasAllFilesAccess()) }
    var shizukuAvailable: Boolean by remember { mutableStateOf(com.frerox.toolz.util.shizuku.ShizukuHelper.isAvailable()) }
    var shizukuAuthorized: Boolean by remember { mutableStateOf(com.frerox.toolz.util.shizuku.ShizukuHelper.isAuthorized()) }

    fun refreshAllStates() {
        hasMediaState = hasMediaPerm()
        hasNotifState = hasNotifPerm()
        hasOverlayState = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) true else android.provider.Settings.canDrawOverlays(context)
        hasAllFilesState = viewModel.hasAllFilesAccess()
        hasA11yState = com.frerox.toolz.service.PurgeShotAccessibilityService.isEnabled(context)
        shizukuAvailable = com.frerox.toolz.util.shizuku.ShizukuHelper.isAvailable()
        shizukuAuthorized = com.frerox.toolz.util.shizuku.ShizukuHelper.isAuthorized()
    }

    LaunchedEffect(pending.size) { hasAllFilesState = viewModel.hasAllFilesAccess() }
    LaunchedEffect(Unit) {
        refreshAllStates()
    }
    LaunchedEffect(shizukuAuthFlow) {
        refreshAllStates()
    }
    // Refresh on resume — when user returns from Settings
    androidx.compose.runtime.DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshAllStates()
                viewModel.refreshShizukuStatus {
                    refreshAllStates()
                }
            }
        }
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "PurgeShot",
                subtitle = when {
                    !enabled -> "Paused"
                    pending.isEmpty() -> if (totalDeleted > 0) "$totalDeleted deleted" else "Ready to save space"
                    else -> "${pending.size} queued • next ${nextPurge?.let { formatRemaining(it.scheduledDeleteAtMs - System.currentTimeMillis()) } ?: "—"}"
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
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdges(top = 12.dp, bottom = 16.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Shizuku 1-tap master grant button — when Shizuku is authorized and any permission is missing
                val anyPermMissing = !hasMediaState || !hasNotifState || !hasOverlayState || !hasA11yState || !hasAllFilesState
                if (shizukuAuthorized && anyPermMissing) {
                    item {
                        StaggeredEntrance(index = 0) {
                            ExpressiveCard(
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.forceGrantAllPermissionsViaShizuku { ok ->
                                            refreshAllStates()
                                            coroutineScope.launch {
                                                snackbarHost.showSnackbar(
                                                    if (ok) "⚡ All permissions force-granted via Shizuku!" else "Shizuku grant failed"
                                                )
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bolt, null, tint = Color.White) }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("⚡ Force Grant All Permissions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                        Text("1-tap auto-setup with Shizuku: Media, Notifications, Overlay Popup, Silent Delete & Accessibility.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null)
                                }
                            }
                        }
                    }
                }

                // Media permission card — required for detection outside Toolz
                if (!hasMediaState && !shizukuAuthorized) {
                    item {
                        StaggeredEntrance(index = 0) {
                            ExpressiveCard(
                                onClick = {
                                    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
                                    permissionLauncher.launch(arrayOf(perm))
                                },
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PhotoLibrary, null, tint = MaterialTheme.colorScheme.error) }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Grant photo access", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                        Text("PurgeShot needs to see screenshots anywhere — grant Media access.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f))
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null)
                                }
                            }
                        }
                    }
                }

                // Notification permission card — required for outside-Toolz heads-up
                if (!hasNotifState && !shizukuAuthorized) {
                    item {
                        StaggeredEntrance(index = 0) {
                            ExpressiveCard(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                    } else {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        runCatching { context.startActivity(intent) }
                                    }
                                },
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.error) }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Allow notifications", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                        Text("Outside Toolz, PurgeShot uses high-priority heads-up alerts — tap to allow.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f))
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null)
                                }
                            }
                        }
                    }
                }

                // Overlay permission card — required for instant popup dialog over other apps
                if (!hasOverlayState && !shizukuAuthorized) {
                    item {
                        StaggeredEntrance(index = 0) {
                            ExpressiveCard(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Layers, null, tint = MaterialTheme.colorScheme.error) }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Allow display over other apps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                        Text("Enables instant PurgeShot popup dialog outside Toolz — tap to grant.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f))
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null)
                                }
                            }
                        }
                    }
                }

                // All Files Permission card — required for silent delete
                if (!hasAllFilesState && !shizukuAuthorized) {
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
                                        Text("One-time All-files access — enables silent permanent auto-delete.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f))
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null)
                                }
                            }
                        }
                    }
                }

                // Outside-Toolz reliable detection: Accessibility + Shizuku cards
                if (!hasA11yState && !shizukuAuthorized) {
                    item {
                        StaggeredEntrance(index = 0) {
                            ExpressiveCard(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }
                                },
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AccessibilityNew, null, tint = MaterialTheme.colorScheme.secondary) }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Enable Accessibility for PurgeShot", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                        Text("Detects screenshots even when Toolz is killed — system-level, no battery drain.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f))
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null)
                                }
                            }
                        }
                    }
                } else if (hasA11yState) {
                    item {
                        StaggeredEntrance(index = 0) {
                            ExpressiveCard(
                                onClick = {},
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Accessibility active", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                        Text("Screenshots detected outside Toolz via system UI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f))
                                    }
                                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                if (!shizukuAuthorized) {
                    item {
                        StaggeredEntrance(index = 0) {
                            ExpressiveCard(
                                onClick = {
                                    if (shizukuAvailable) {
                                        com.frerox.toolz.util.shizuku.ShizukuHelper.requestPermission(1001)
                                    } else {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/guide/setup/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Terminal, null, tint = MaterialTheme.colorScheme.tertiary) }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(if (shizukuAvailable) "Grant Shizuku for PurgeShot" else "Install Shizuku for best reliability", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                        Text(if (shizukuAvailable) "Tap to authorize Shizuku & auto-grant all permissions." else "Shizuku gives instant file-system detection outside Toolz.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f))
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        StaggeredEntrance(index = 0) {
                            ExpressiveCard(
                                onClick = {
                                    coroutineScope.launch {
                                        viewModel.forceGrantAllPermissionsViaShizuku { ok ->
                                            refreshAllStates()
                                            coroutineScope.launch {
                                                snackbarHost.showSnackbar(if (ok) "Permissions re-applied via Shizuku" else "Shizuku sync failed")
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Shizuku privileged watch active", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                                        Text("File-system inotify active — tap to re-sync permissions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f))
                                    }
                                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                // Hero — fluid, simple, expressive
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
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Self-destructing screenshots", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Text("Save storage without thinking.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f))
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HeroStat(label = "Queued", value = "${pending.size}", modifier = Modifier.weight(1f))
                                    HeroStat(label = "Deleted", value = "$totalDeleted", modifier = Modifier.weight(1f))
                                    HeroStat(label = "Next", value = nextPurge?.let { formatRemaining(it.scheduledDeleteAtMs - System.currentTimeMillis()) } ?: "—", modifier = Modifier.weight(1f))
                                }
                                if (pending.isNotEmpty()) {
                                    ToolzWavyLinearProgressIndicator(
                                        progress = {
                                            nextPurge?.let { 1f - ((it.scheduledDeleteAtMs - System.currentTimeMillis()).toFloat() / it.durationMillis.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) } ?: 0f
                                        },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(8.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Controls — fluid toggle with spring
                item {
                    StaggeredEntrance(index = 2) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ToggleRow(
                                title = "PurgeShot",
                                subtitle = if (enabled) "Watching Screenshots" else "Paused",
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
                                        subtitle = if (smartAuto) "No popup — auto ${durationLabel(autoDuration)}" else "Show popup each time",
                                        icon = Icons.Rounded.AutoAwesome,
                                        checked = smartAuto,
                                        onCheckedChange = { viewModel.setSmartAuto(it) }
                                    )
                                    AutoTimeExpressiveCard(
                                        durationMillis = autoDuration,
                                        durationLabel = durationLabel(autoDuration),
                                        onClick = { showAutoPicker = true }
                                    )
                                }
                            }
                        }
                    }
                }

                // Timers — clear M3 Expressive grid, fixed (no bugged sheet height)
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
                                // Simple 3-column flow without nested LazyVerticalGrid bug — stable measurement inside LazyColumn
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    presets.chunked(3).forEachIndexed { rowIdx, row ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            row.forEach { p ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    TimerChip(preset = p, isAuto = p.label.equals("Auto", ignoreCase = true))
                                                }
                                            }
                                            // Fill empty cells to keep 3-col alignment
                                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                                Text("Tap Edit to choose up to 6. Auto follows Smart Auto.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Queue header
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        if (pending.isNotEmpty()) {
                            ExpressiveStatePill(text = "${pending.size} pending", icon = Icons.Rounded.HourglassEmpty, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (pending.isEmpty()) {
                    item {
                        StaggeredEntrance(index = 5) {
                            ExpressiveCard(onClick = {}, shape = RoundedCornerShape(24.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(28.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(modifier = Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)) {
                                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoDelete, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                                    }
                                    Text("No queued shots", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                                    Text("Take a screenshot — pick a timer or let Smart Auto handle it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                    if (com.frerox.toolz.BuildConfig.DEBUG) {
                                        ToolzTonalExpressiveButton(onClick = { viewModel.debugEnqueueDummy() }) {
                                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Demo shot", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(pending, key = { it.id }) { entry ->
                        StaggeredEntrance(index = 0) {
                            QueueCard(
                                entry = entry,
                                onKeep = { viewModel.cancelEntry(entry.id) },
                                onDeleteNow = { viewModel.deleteNow(entry.id) },
                                onExtend = { viewModel.extendEntry(entry.id, 15 * 60_000L) }
                            )
                        }
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
            options = viewModel.allOptions.filter { !it.label.equals("Auto", true) },
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
            text = { Text("Files stay — only timers are removed.") },
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
    // Fluid morph: value changes animate with spring
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
            AnimatedContent(targetState = value, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.88f)).togetherWith(fadeOut()) }, label = "heroStat") { v ->
                Text(v, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val scale by animateFloatAsState(targetValue = if (checked) 1f else 0.96f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f), label = "toggleScale")
    ExpressiveCard(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
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
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (pressed) 0.94f else 1f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 480f), label = "chip")
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isAuto) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        tonalElevation = if (isAuto) 1.dp else 0.dp
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
        Column(Modifier.padding(14.dp).animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Thumbnail preview — fluid
                Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    AsyncImage(
                        model = entry.fileUriString,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(entry.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.durationLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(10.dp), color = if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)) {
                    Text(if (overdue) "Deleting…" else formatRemaining(remaining), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = if (overdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            ToolzWavyLinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(8.dp)),
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ToolzTonalExpressiveButton(onClick = onExtend, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp)) {
                    Icon(Icons.Rounded.MoreTime, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("+15m", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                ToolzOutlinedExpressiveButton(onClick = onKeep, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp)) {
                    Text("Keep", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                ToolzExpressiveButton(onClick = onDeleteNow, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Now", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun AutoTimeExpressiveCard(
    durationMillis: Long,
    durationLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberToolzHapticFeedback()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "autoTimeScale"
    )
    // M3 Expressive: clear tonal card, large rounded, subtle border, spring press
    Surface(
        onClick = {
            haptic.tick()
            onClick()
        },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f)),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        interactionSource = interaction
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f)) {
                // Expressive leading container — primaryContainer circle with Timer icon
                Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text("Auto time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    AnimatedContent(
                        targetState = durationLabel,
                        transitionSpec = { (fadeIn() + slideInVertically { it / 3 }).togetherWith(fadeOut()) },
                        label = "autoLabel"
                    ) { label ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Schedule, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                        }
                    }
                    Text("Smart Auto deletes after this", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                }
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)) {
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(8.dp).size(18.dp))
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
    val haptic = rememberToolzHapticFeedback()
    val scrollState = rememberScrollState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 300f)),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header — Material 3 Expressive clear
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Choose timers", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    Text("Up to 6 • Auto follows Smart Auto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedContent(targetState = selected.size, label = "count") { count ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (count == 6) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 0.dp
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                if (count == 6) Icons.Rounded.CheckCircle else Icons.Rounded.Timer,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = if (count == 6) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text("$count/6", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = if (count == 6) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            // Selected — expressive cards, no LazyVerticalGrid bug (uses chunked Row)
            AnimatedVisibility(
                visible = selected.isNotEmpty(),
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.padding(6.dp).size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Text("Your popup • tap to remove", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        selected.chunked(3).forEachIndexed { rowIdx, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEachIndexed { colIdx, p ->
                                    val idx = rowIdx * 3 + colIdx
                                    Box(modifier = Modifier.weight(1f)) {
                                        StaggeredEntrance(index = idx) {
                                            ExpressiveCard(
                                                onClick = {
                                                    haptic.tick()
                                                    selected = selected.toMutableList().apply { removeAt(idx) }
                                                },
                                                shape = RoundedCornerShape(18.dp),
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Column(
                                                    Modifier.padding(14.dp).fillMaxWidth(),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Surface(modifier = Modifier.size(32.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) {
                                                        Box(contentAlignment = Alignment.Center) { Icon(iconFor(p.iconName), null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
                                                    }
                                                    Text(p.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                    Text("tap to remove", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f))
                                                }
                                            }
                                        }
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                }
            }

            // All durations — 3-col FilterChips using chunked Rows (no LazyVerticalGrid measurement bug)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("All durations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Tap to add • max 6", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    allOptions.chunked(3).forEachIndexed { rowIdx, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { opt ->
                                val isSelected = selected.any { it.label == opt.label && it.durationMillis == opt.durationMillis }
                                val canAdd = selected.size < 6
                                val enabled = isSelected || canAdd
                                Box(modifier = Modifier.weight(1f)) {
                                    StaggeredEntrance(index = rowIdx * 3 + 4) {
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                haptic.tick()
                                                selected = when {
                                                    isSelected -> selected.filterNot { it.label == opt.label && it.durationMillis == opt.durationMillis }.toMutableList()
                                                    canAdd -> (selected + opt).toMutableList()
                                                    else -> selected
                                                }
                                            },
                                            label = { Text(opt.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp) },
                                            leadingIcon = {
                                                Icon(
                                                    iconFor(opt.iconName), null,
                                                    modifier = Modifier.size(16.dp).graphicsLayer {
                                                        scaleX = if (isSelected) 1.08f else 1f
                                                        scaleY = if (isSelected) 1.08f else 1f
                                                    },
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            enabled = enabled,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth().bouncyClick(enabled = enabled) {},
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = enabled,
                                                selected = isSelected,
                                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f),
                                                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                            )
                                        )
                                    }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // Actions — M3 Expressive clear
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                ToolzOutlinedExpressiveButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) { Text("Cancel", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
                ToolzExpressiveButton(
                    onClick = { haptic.success(); onSave(selected) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    enabled = selected.isNotEmpty()
                ) { Text("Save ${selected.size}", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge) }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
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
    val haptic = rememberToolzHapticFeedback()
    val scrollState = rememberScrollState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header — simple clear M3 Expressive
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                    }
                    Column {
                        Text("Auto delete after", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        Text("Smart Auto queues with this delay", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Info, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("No popup — auto-queues instantly", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))

            // Options — clear selectable cards
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEachIndexed { idx, opt ->
                    val selected = opt.durationMillis == currentDuration
                    StaggeredEntrance(index = idx) {
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (pressed) 0.98f else 1f,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
                            label = "autoOptScale"
                        )
                        Surface(
                            onClick = {
                                haptic.tick()
                                onSelect(opt.durationMillis)
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = if (selected) 1.dp else 0.dp,
                            border = if (selected) androidx.compose.foundation.BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { scaleX = scale; scaleY = scale },
                            interactionSource = interaction
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f)) {
                                    Surface(
                                        modifier = Modifier.size(42.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(iconFor(opt.iconName), null, modifier = Modifier.size(20.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(opt.label, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                        Text(durationToHumane(opt.durationMillis) + " • ${opt.label}", style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                AnimatedContent(targetState = selected, label = "check") { isSel ->
                                    if (isSel) {
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.padding(6.dp).size(18.dp))
                                        }
                                    } else {
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))) {
                                            Box(modifier = Modifier.size(30.dp)) {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
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
