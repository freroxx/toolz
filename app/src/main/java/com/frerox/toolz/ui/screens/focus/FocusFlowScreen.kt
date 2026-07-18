package com.frerox.toolz.ui.screens.focus

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import coil3.compose.rememberAsyncImagePainter
import com.frerox.toolz.data.focus.AppCategory
import com.frerox.toolz.data.focus.AppUsageInfo
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FocusFlowScreen — Refined iteration focusing on stabilization and performance.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FocusFlowScreen(
    onNavigateBack: () -> Unit,
    viewModel: FocusFlowViewModel = hiltViewModel(),
) {
    val usageStats        by viewModel.combinedUsageStats.collectAsState(initial = emptyList())
    val productivityScore by viewModel.productivityScore.collectAsState()
    val isWeekly          by viewModel.isWeekly.collectAsState()
    val isAiClassifying   by viewModel.isAiClassifying.collectAsState()
    val aiClassifiedPkgs  by viewModel.aiClassifiedPackages.collectAsState()
    val hasUsagePermission by viewModel.hasUsagePermission.collectAsState()
    val top5Apps          by viewModel.top5Apps.collectAsState()
    val offlineModeEnabled by viewModel.offlineModeEnabled.collectAsState(initial = false)
    val focusSession       by viewModel.focusSession.collectAsState()

    val performanceMode   = LocalPerformanceMode.current
    val haptic            = rememberToolzHapticFeedback()
    val context           = LocalContext.current
    val scope             = rememberCoroutineScope()

    val canDrawOverlays         = remember { Settings.canDrawOverlays(context) }
    var isAccessibilityEnabled  by remember { mutableStateOf(false) }
    var selectedAppForSettings  by remember { mutableStateOf<AppUsageInfo?>(null) }
    var appToRename             by remember { mutableStateOf<AppUsageInfo?>(null) }
    var showWeeklySheet         by remember { mutableStateOf(false) }
    var showTipsSheet           by remember { mutableStateOf(false) }
    var showSessionPicker       by remember { mutableStateOf(false) }
    var showResetAllConfirm     by remember { mutableStateOf(false) }

    val lifecycleEvent = rememberLifecycleEvent()
    LaunchedEffect(lifecycleEvent) {
        if (lifecycleEvent == Lifecycle.Event.ON_RESUME) {
            isAccessibilityEnabled = checkAccessibilityEnabled(context)
        }
    }

    LaunchedEffect(focusSession.state) {
        if (focusSession.state == FocusSessionState.COMPLETED) {
            haptic.success()
        }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Focus Flow",
                subtitle = if (isWeekly) "Detailed app usage" else "Daily attention analytics",
                navigationIcon = {
                    ToolzTonalExpressiveIconButton(
                        onClick  = { onNavigateBack() },
                        modifier = Modifier.padding(8.dp),
                        shape    = SquircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!offlineModeEnabled) {
                        AnimatedVisibility(
                            visible = isAiClassifying,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            ExpressiveStatePill(
                                text = "AI",
                                icon = Icons.Rounded.AutoAwesome,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = !isAiClassifying,
                            enter = fadeIn() + scaleIn(spring(Spring.DampingRatioMediumBouncy)),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            ToolzTonalExpressiveIconButton(
                                onClick = { viewModel.refreshAiCategories() },
                                shape = SquircleShape,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(0.55f)
                                )
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome, "Refresh AI",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(40.dp)
                            .clip(SquircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .combinedClickable(
                                onClick = {
                                    scope.launch {
                                        viewModel.refreshStats()
                                        delay(500)
                                        haptic.success()
                                    }
                                },
                                onLongClick = {
                                    haptic.longClick()
                                    showResetAllConfirm = true
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Refresh, "Refresh", modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                largeFlexible = false,
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { padding ->
        Box(Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (performanceMode) Modifier
                        else Modifier.fadingEdges(top = 16.dp, bottom = 16.dp)
                    ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (!hasUsagePermission) {
                    item {
                        UsagePermissionBanner(
                            onGrantClick = {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        )
                    }
                }

                if (!canDrawOverlays || !isAccessibilityEnabled) {
                    item {
                        PermissionBanner(
                            canDrawOverlays    = canDrawOverlays,
                            onResolveClick     = {
                                val intent = if (!canDrawOverlays) {
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                } else {
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                }
                                context.startActivity(intent)
                            },
                        )
                    }
                }

                item {
                    FocusSessionHeader(
                        score = productivityScore,
                        session = focusSession,
                        performanceMode = performanceMode,
                        offlineModeEnabled = offlineModeEnabled,
                        onStartClick = { showSessionPicker = true },
                        onPauseResumeClick = { viewModel.togglePauseFocusSession() },
                        onCancelClick = { viewModel.cancelFocusSession() },
                        onCompletedDismiss = { viewModel.dismissCompletedSession() },
                        onTipsLongClick = { showTipsSheet = true },
                        onDetailsClick = {
                            val intent = Intent(context, com.frerox.toolz.MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra("navigate_to", "pomodoro")
                            }
                            context.startActivity(intent)
                        }
                    )
                }

                item {
                    val weeklyStats = remember(isWeekly, usageStats) { viewModel.getWeeklyLocalStats() }
                    val totalTime = if (isWeekly) {
                        weeklyStats.sumOf { it.totalMillis }
                    } else {
                        usageStats.sumOf { it.usageTimeMillis }
                    }
                    val hours     = totalTime / 3_600_000
                    val minutes   = (totalTime % 3_600_000) / 60_000
                    
                    val appsCount = if (isWeekly) {
                        // Sum of unique packages across all days in weekly stats or just usageStats.size
                        usageStats.size 
                    } else {
                        usageStats.size
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetricCard(
                            label    = "Screen time",
                            value    = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                            icon     = Icons.Rounded.PhoneAndroid,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        MetricCard(
                            label    = "Apps used",
                            value    = "$appsCount",
                            icon     = Icons.Rounded.Apps,
                            color    = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (!isWeekly && top5Apps.isNotEmpty()) {
                    item {
                        val totalTime = usageStats.sumOf { it.usageTimeMillis }
                        Top5AppsSection(topApps = top5Apps, totalTime = totalTime)
                    }
                }

                val uncategorized = usageStats.filter { it.category == AppCategory.OTHER }
                if (uncategorized.isNotEmpty()) {
                    item {
                        UncategorizedAppsSection(
                            apps = uncategorized,
                            onClassify = { pkg, isProductive ->
                                viewModel.updateAppCategory(pkg, isProductive)
                            }
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        HorizontalDivider(modifier = Modifier.alpha(0.05f), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Analytics",
                                modifier = Modifier.padding(horizontal = 12.dp),
                                style    = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                Modifier.height(40.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, SquircleShape).padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ExpressiveFilterChip(
                                    selected = !isWeekly,
                                    onClick = { haptic.tick(); viewModel.toggleWeekly(false) },
                                    label = { Text("Daily") },
                                    shape = SquircleShape
                                )
                                ExpressiveFilterChip(
                                    selected = isWeekly,
                                    onClick = { haptic.tick(); viewModel.toggleWeekly(true) },
                                    label = { Text("Weekly") },
                                    shape = SquircleShape
                                )
                            }
                        }
                    }
                }

                item {
                    AnimatedContent(
                        targetState = isWeekly,
                        transitionSpec = {
                            if (targetState) {
                                (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessLow)) { it } + fadeIn()).togetherWith(
                                    slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessLow)) { -it } + fadeOut()
                                )
                            } else {
                                (slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessLow)) { -it } + fadeIn()).togetherWith(
                                    slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessLow)) { it } + fadeOut()
                                )
                            }.using(SizeTransform(clip = false))
                        },
                        label = "analytics_slide"
                    ) { weekly ->
                        if (weekly) {
                            WeeklySummaryCard(
                                stats = usageStats,
                                weeklyLocalStats = viewModel.getWeeklyLocalStats(),
                                onClick = { showWeeklySheet = true }
                            )
                        } else {
                            Spacer(Modifier.height(0.dp))
                        }
                    }
                }

                if (usageStats.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
                                Surface(
                                    shape  = SquircleShape,
                                    color  = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.size(100.dp),
                                ) {
                                    Icon(
                                        if (!hasUsagePermission) Icons.Rounded.Lock else Icons.Rounded.TimerOff, null,
                                        modifier = Modifier.padding(24.dp),
                                        tint     = MaterialTheme.colorScheme.outline,
                                    )
                                }
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    if (!hasUsagePermission) "Usage access not granted" else "No events recorded today",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (!hasUsagePermission)
                                        "Usage access is required to accurately track your screen time. Tap the banner above to grant it."
                                    else
                                        "Some OEMs (Xiaomi, Samsung) restrict background event access. Try opening a few apps and refreshing.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )

                                if (hasUsagePermission) {
                                    Spacer(Modifier.height(24.dp))
                                    ToolzExpressiveButton(
                                        onClick = {
                                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                        },
                                        shape = SquircleShape
                                    ) {
                                        Text("Re-grant usage access", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                items(usageStats, key = { it.packageName }) { info ->
                    StaggeredEntrance(index = usageStats.indexOf(info)) {
                        EnhancedUsageItem(
                            info            = info,
                            isAiClassified  = info.packageName in aiClassifiedPkgs,
                            onClick         = { selectedAppForSettings = info },
                            onLongClick     = { appToRename = info },
                            onQuickLimit    = { viewModel.setAppLimit(info.packageName, it) },
                        )
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        selectedAppForSettings?.let { app ->
            FocusAppSettingsSheet(
                app             = app,
                onDismiss       = { selectedAppForSettings = null },
                onSaveLimit     = { minutes ->
                    if (minutes > 0) viewModel.setAppLimit(app.packageName, minutes)
                    else viewModel.removeAppLimit(app.packageName)
                },
                onUpdateCategory = { isProductive ->
                    viewModel.updateAppCategory(app.packageName, isProductive)
                },
                onResetApp = {
                    haptic.tick()
                    viewModel.resetAppSettings(app.packageName)
                },
            )
        }

        if (showWeeklySheet) {
            WeeklyDetailedSheet(
                viewModel = viewModel,
                usageStats = usageStats,
                onDismiss = { showWeeklySheet = false }
            )
        }

        if (showTipsSheet) {
            ScreenTipsSheet(
                viewModel = viewModel,
                onDismiss = { showTipsSheet = false }
            )
        }

        if (showSessionPicker) {
            FocusSessionPickerSheet(
                onDismiss = { showSessionPicker = false },
                onStart = { minutes ->
                    haptic.success()
                    viewModel.startFocusSession(minutes)
                    showSessionPicker = false
                },
            )
        }

        appToRename?.let { app ->
            var nameInput by remember(app.packageName) { mutableStateOf(app.appName) }
            AlertDialog(
                onDismissRequest = { appToRename = null },
                containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape            = SquircleShape,
                icon             = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                title            = { Text("Rename app") },
                text             = {
                    OutlinedTextField(
                        value         = nameInput,
                        onValueChange = { nameInput = it },
                        label         = { Text("Custom name") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        shape         = SmallExpressiveShape,
                    )
                },
                confirmButton = {
                    ToolzExpressiveButton(
                        onClick = {
                            if (nameInput.isNotBlank()) viewModel.renameApp(app.packageName, nameInput.trim())
                            appToRename = null
                        },
                        shape = SquircleShape,
                    ) { Text("Save") }
                },
                dismissButton = {
                    ToolzExpressiveTextButton(onClick = { appToRename = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (showResetAllConfirm) {
            AlertDialog(
                onDismissRequest = { showResetAllConfirm = false },
                icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Reset all focus data?") },
                text = { Text("This will clear all custom app limits, categories, and renamed apps. This cannot be undone.") },
                confirmButton = {
                    ToolzExpressiveButton(
                        onClick = {
                            viewModel.resetAllFocusData()
                            showResetAllConfirm = false
                        },
                        shape = SquircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Reset everything") }
                },
                dismissButton = {
                    ToolzExpressiveTextButton(onClick = { showResetAllConfirm = false }) {
                        Text("Cancel")
                    }
                },
                shape = SquircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Permission banners
// ─────────────────────────────────────────────────────────────

@Composable
private fun PermissionBanner(canDrawOverlays: Boolean, onResolveClick: () -> Unit) {
    ExpressiveCard(
        onClick = onResolveClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = SquircleShape,
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = SquircleShape,
                color = MaterialTheme.colorScheme.error,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (!canDrawOverlays) Icons.Rounded.Layers else Icons.Rounded.AccessibilityNew,
                        null,
                        tint     = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (!canDrawOverlays) "Overlay required" else "Accessibility required",
                    fontWeight = FontWeight.SemiBold,
                    style      = MaterialTheme.typography.titleSmall,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (!canDrawOverlays)
                        "Needed to block distracting apps in real time"
                    else
                        "Needed for the real-time flow engine to run",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ToolzExpressiveButton(
                    onClick = onResolveClick,
                    modifier = Modifier.height(34.dp),
                    shape = SquircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Text("Grant permission", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun UsagePermissionBanner(onGrantClick: () -> Unit) {
    ExpressiveCard(
        onClick = onGrantClick,
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = SquircleShape,
                color = MaterialTheme.colorScheme.primary.copy(0.15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Visibility, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Accurate screen time",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Usage access required for accurate tracking. Tap to grant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.primary.copy(0.6f))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Metric card
// ─────────────────────────────────────────────────────────────

@Composable
fun MetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    ExpressiveCard(
        onClick = {},
        modifier = modifier,
        shape    = SquircleShape,
        containerColor    = color.copy(alpha = 0.05f), // Refined glassy look
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Subtle icon halo
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(color.copy(alpha = 0.06f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(color.copy(alpha = 0.1f), SmallExpressiveShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color      = color.copy(alpha = 0.7f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Focus session header
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusSessionHeader(
    score: Int,
    session: FocusSessionUiState,
    performanceMode: Boolean,
    offlineModeEnabled: Boolean,
    onStartClick: () -> Unit,
    onPauseResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    onCompletedDismiss: () -> Unit,
    onTipsLongClick: () -> Unit,
    onDetailsClick: () -> Unit,
) {
    val successColor = Color(0xFF2E7D32)
    val warningColor = Color(0xFFB07A00)
    val errorColor = MaterialTheme.colorScheme.error
    val scoreAccentColor = when {
        score >= 70 -> successColor
        score >= 40 -> MaterialTheme.colorScheme.primary
        score >= 20 -> warningColor
        else -> errorColor
    }

    val isSessionActive = session.state == FocusSessionState.RUNNING || session.state == FocusSessionState.PAUSED
    val isRunning = session.state == FocusSessionState.RUNNING
    val sessionAccentColor = MaterialTheme.colorScheme.primary

    // Optimized animations
    val infiniteTransition = rememberInfiniteTransition(label = "focus_refined")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = if (performanceMode) snap() else tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = if (performanceMode) snap() else tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradient"
    )

    val activeBrush = if (isSessionActive && !performanceMode) {
        Brush.linearGradient(
            colors = listOf(sessionAccentColor.copy(alpha = 0.05f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.03f), sessionAccentColor.copy(alpha = 0.05f)),
            start = Offset(gradientShift, 0f),
            end = Offset(gradientShift + 300f, 300f)
        )
    } else null

    val statusLabel = when {
        score >= 85 -> "Elite focus"
        score >= 70 -> "High focus"
        score >= 40 -> "Balanced"
        score >= 20 -> "Low focus"
        else -> "Time to refocus"
    }
    val supportLabel = when {
        score >= 70 -> "Keeping distractions in check."
        score >= 40 -> "A steady day — room to tighten."
        else -> "Distractions are winning."
    }

    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = if (performanceMode) snap() else tween(900, easing = FastOutSlowInEasing),
        label = "score",
    )
    val animatedScoreProgress = (score / 100f).coerceIn(0f, 1f)

    val sessionProgress = if (session.totalMillis > 0) {
        (1f - session.remainingMillis.toFloat() / session.totalMillis).coerceIn(0f, 1f)
    } else 0f

    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveMorphing(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (!isSessionActive) onStartClick() },
                onLongClick = onTipsLongClick,
            ),
        shape = BouncyShape,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        border = BorderStroke(
            width = if (isSessionActive) 1.5.dp else 1.dp,
            color = if (isSessionActive) sessionAccentColor.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        ),
    ) {
        Box(modifier = Modifier.then(if (activeBrush != null) Modifier.background(activeBrush) else Modifier)) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .then(if (isRunning) Modifier.scale(pulseScale) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        val ringColor = if (isSessionActive) sessionAccentColor else scoreAccentColor
                        val ringProgress = if (isSessionActive) sessionProgress else animatedScoreProgress

                        ToolzWavyCircularProgressIndicator(
                            progress = { ringProgress },
                            color = ringColor,
                            trackColor = ringColor.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isSessionActive) {
                            val remainingMin = (session.remainingMillis / 60_000L).toInt()
                            val remainingSec = ((session.remainingMillis % 60_000L) / 1000L).toInt()
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    String.format("%d:%02d", remainingMin, remainingSec),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = sessionAccentColor,
                                )
                                Icon(
                                    if (session.state == FocusSessionState.PAUSED) Icons.Rounded.Pause else Icons.Rounded.Bolt,
                                    null,
                                    modifier = Modifier.size(14.dp),
                                    tint = sessionAccentColor.copy(alpha = 0.7f),
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$animatedScore",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = scoreAccentColor,
                                )
                                Text(
                                    "%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scoreAccentColor.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (session.state) {
                            FocusSessionState.RUNNING, FocusSessionState.PAUSED -> {
                                ExpressiveStatePill(
                                    text = if (session.state == FocusSessionState.PAUSED) "Paused" else "Focus session",
                                    icon = if (session.state == FocusSessionState.PAUSED) Icons.Rounded.PauseCircle else Icons.Rounded.Timer,
                                    color = sessionAccentColor
                                )
                                Text(
                                    text = if (session.state == FocusSessionState.PAUSED) "Ready" else "Stay focused",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${session.totalMillis / 60_000L} min in progress",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FocusSessionState.COMPLETED -> {
                                ExpressiveStatePill(
                                    text = "Done",
                                    icon = Icons.Rounded.CheckCircle,
                                    color = successColor
                                )
                                Text(
                                    text = "Nice work",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Session complete.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            FocusSessionState.IDLE -> {
                                ExpressiveStatePill(
                                    text = "Flow state",
                                    icon = Icons.Rounded.Bolt,
                                    color = scoreAccentColor
                                )
                                Text(statusLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text(supportLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (session.state) {
                        FocusSessionState.IDLE -> {
                            ToolzExpressiveButton(
                                onClick = onStartClick,
                                shape = SquircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = sessionAccentColor.copy(alpha = 0.1f), contentColor = sessionAccentColor),
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Start focus session", fontWeight = FontWeight.SemiBold)
                            }
                            if (!performanceMode && !offlineModeEnabled) {
                                ToolzTonalExpressiveIconButton(
                                    onClick = onTipsLongClick,
                                    shape = SquircleShape,
                                    modifier = Modifier.size(48.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                        FocusSessionState.RUNNING, FocusSessionState.PAUSED -> {
                            ToolzExpressiveButton(
                                onClick = onPauseResumeClick,
                                shape = SquircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = sessionAccentColor.copy(alpha = 0.12f), contentColor = sessionAccentColor),
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) {
                                Icon(if (session.state == FocusSessionState.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (session.state == FocusSessionState.PAUSED) "Resume" else "Pause", fontWeight = FontWeight.SemiBold)
                            }
                            ToolzTonalExpressiveIconButton(
                                onClick = onDetailsClick,
                                shape = SquircleShape,
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f), contentColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                            }
                            ToolzTonalExpressiveIconButton(
                                onClick = onCancelClick,
                                shape = SquircleShape,
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                            }
                        }
                        FocusSessionState.COMPLETED -> {
                            ToolzExpressiveButton(
                                onClick = onCompletedDismiss,
                                shape = SquircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = successColor.copy(alpha = 0.1f), contentColor = successColor),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) {
                                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Dismiss", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Focus session picker
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSessionPickerSheet(
    onDismiss: () -> Unit,
    onStart: (minutes: Int) -> Unit,
) {
    var selectedMinutes by remember { mutableIntStateOf(25) }
    val presets = listOf(15, 25, 45, 60)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape            = ExtraLargeExpressiveShape,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp, 3.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.18f), CircleShape))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = SquircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                }
                Column {
                    Text("Focus session", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("How long for this block?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.forEach { mins ->
                    val isSelected = selectedMinutes == mins
                    ExpressiveFilterChip(
                        selected = isSelected,
                        onClick = { selectedMinutes = mins },
                        label = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text("$mins", fontWeight = FontWeight.Bold)
                                Text("min", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        modifier = Modifier.weight(1f).height(64.dp),
                        shape = SquircleShape
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            ToolzExpressiveButton(
                onClick = { onStart(selectedMinutes) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = SquircleShape,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start session", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Uncategorized apps
// ─────────────────────────────────────────────────────────────

@Composable
private fun UncategorizedAppsSection(
    apps: List<AppUsageInfo>,
    onClassify: (packageName: String, isProductive: Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Classification triage",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExpressiveStatePill(
                text = "${apps.size}",
                icon = Icons.Rounded.Apps,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        ExpressiveCarousel(
            items = apps,
            preferredItemWidth = 168.dp,
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) { app ->
            StaggeredEntrance(index = apps.indexOf(app)) {
                Surface(
                    shape = SquircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier.width(168.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(28.dp)) {
                                AppIcon(packageName = app.packageName, modifier = Modifier.fillMaxSize())
                            }
                            Text(
                                app.appName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ToolzTonalExpressiveIconButton(
                                onClick = { onClassify(app.packageName, true) },
                                modifier = Modifier.weight(1f).height(30.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Rounded.AddModerator, null, modifier = Modifier.size(14.dp))
                            }
                            ToolzTonalExpressiveIconButton(
                                onClick = { onClassify(app.packageName, false) },
                                modifier = Modifier.weight(1f).height(30.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Rounded.Block, null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Usage list item
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EnhancedUsageItem(
    info: AppUsageInfo,
    isAiClassified: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickLimit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hours = info.usageTimeMillis / 3_600_000
    val minutes = (info.usageTimeMillis % 3_600_000) / 60_000
    val timeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    val isOverLimit = info.limitMillis != null && info.usageTimeMillis >= info.limitMillis

    val limitProgress = info.limitMillis
        ?.takeIf { it > 0L }
        ?.let { info.usageTimeMillis.toFloat() / it }
        ?.coerceIn(0f, 1.2f)
    val barColor = when {
        limitProgress == null -> MaterialTheme.colorScheme.surfaceVariant
        limitProgress >= 1f -> MaterialTheme.colorScheme.error
        limitProgress >= 0.8f -> Color(0xFFB07A00)
        else -> MaterialTheme.colorScheme.primary
    }

    val suggestedLimitMinutes = remember(info.usageTimeMillis) {
        val usedMinutes = info.usageTimeMillis / 60_000L
        val roundedUp = (((usedMinutes / 15) + 1) * 15).coerceIn(15L, 120L)
        roundedUp
    }

    ExpressiveCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().expressivePressScale(interactionSource),
        shape = SquircleShape,
        containerColor = if (isOverLimit) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        },
        border = BorderStroke(
            width = if (isOverLimit) 1.5.dp else 1.dp,
            color = if (isOverLimit) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            },
        ),
        elevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = SquircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.size(50.dp),
                ) {
                    AppIcon(packageName = info.packageName, modifier = Modifier.padding(10.dp))
                }

                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            info.appName,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isAiClassified) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary.copy(0.12f),
                                shape = CircleShape,
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Text("AI", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (info.limitMillis != null) {
                            Icon(Icons.Rounded.Timer, null, modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "Limit ${info.limitMillis / 60_000}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        } else {
                            Text(
                                info.packageName.lowercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                val context = LocalContext.current
                val isToolzApp = info.packageName == context.packageName

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        timeStr,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    val catColor = when {
                        isToolzApp -> MaterialTheme.colorScheme.tertiary
                        info.category == AppCategory.TOOLZ -> MaterialTheme.colorScheme.primary
                        info.category == AppCategory.DISTRACTION -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                    Surface(
                        color = catColor.copy(alpha = 0.1f),
                        shape = CapsuleShape,
                        border = BorderStroke(1.dp, catColor.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = when {
                                isToolzApp -> "System"
                                info.category == AppCategory.TOOLZ -> "Focus"
                                info.category == AppCategory.DISTRACTION -> "Distract"
                                else -> "Other"
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = catColor
                        )
                    }
                }
            }

            if (info.limitMillis != null && limitProgress != null) {
                val limitMinutes = info.limitMillis / 60_000
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Daily limit ${limitMinutes}m",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = barColor,
                        )
                        Text(
                            text = "${(limitProgress * 100).toInt().coerceAtMost(120)}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ToolzWavyLinearProgressIndicator(
                        progress = { limitProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                        color = barColor,
                        trackColor = barColor.copy(alpha = 0.1f),
                        strokeCap = StrokeCap.Round
                    )
                }
            } else if (info.category == AppCategory.DISTRACTION) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.04f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Timer, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            Text(
                                "Distraction",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        ToolzTonalExpressiveButton(
                            onClick = { onQuickLimit(suggestedLimitMinutes) },
                            modifier = Modifier.height(30.dp),
                            shape = CapsuleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(
                                "Limit ${suggestedLimitMinutes}m",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Weekly summary card
// ─────────────────────────────────────────────────────────────

@Composable
fun WeeklySummaryCard(
    stats: List<AppUsageInfo>,
    weeklyLocalStats: List<FocusFlowViewModel.DailyLocalStat>,
    onClick: () -> Unit,
) {
    val totalTime = stats.sumOf { it.usageTimeMillis }
    val hours     = totalTime / 3_600_000
    val minutes   = (totalTime % 3_600_000) / 60_000

    val toolzTime      = stats.filter { it.category == AppCategory.TOOLZ }.sumOf { it.usageTimeMillis }
    val distractionTime = stats.filter { it.category == AppCategory.DISTRACTION }.sumOf { it.usageTimeMillis }
    val otherTime      = (totalTime - toolzTime - distractionTime).coerceAtLeast(0)

    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = BouncyShape,
        containerColor    = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.08f),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Timeline, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Text(
                        "Attention trend",
                        style         = MaterialTheme.typography.labelLarge,
                        fontWeight    = FontWeight.SemiBold,
                        color         = MaterialTheme.colorScheme.secondary,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            if (weeklyLocalStats.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                val maxDayMillis = weeklyLocalStats.maxOf { it.totalMillis }.coerceAtLeast(1L)
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    weeklyLocalStats.forEach { day ->
                        val isToday = day == weeklyLocalStats.last()
                        val targetFraction = (day.totalMillis.toFloat() / maxDayMillis).coerceIn(0.04f, 1f)

                        val animatedFraction by animateFloatAsState(
                            targetValue = targetFraction,
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "bar"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(animatedFraction)
                                .clip(SmallExpressiveShape)
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    weeklyLocalStats.forEach { day ->
                        val isToday = day == weeklyLocalStats.last()
                        Text(
                            day.date.take(2),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (totalTime > 0) {
                val toolzFraction      = (toolzTime.toFloat() / totalTime).coerceIn(0f, 1f)
                val distractionFraction = (distractionTime.toFloat() / totalTime).coerceIn(0f, 1f)
                val otherFraction       = (otherTime.toFloat() / totalTime).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(CapsuleShape)
                ) {
                    if (toolzFraction > 0f) {
                        Box(Modifier.fillMaxHeight().weight(toolzFraction).background(MaterialTheme.colorScheme.primary))
                    }
                    if (distractionFraction > 0f) {
                        Box(Modifier.fillMaxHeight().weight(distractionFraction).background(MaterialTheme.colorScheme.error))
                    }
                    if (otherFraction > 0f) {
                        Box(Modifier.fillMaxHeight().weight(otherFraction).background(MaterialTheme.colorScheme.outlineVariant.copy(0.3f)))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem("Focus", MaterialTheme.colorScheme.primary)
                LegendItem("Distract", MaterialTheme.colorScheme.error)
                LegendItem("Other", MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.8f))
    }
}

// ─────────────────────────────────────────────────────────────
//  App icon loader
// ─────────────────────────────────────────────────────────────

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconState = produceState<Drawable?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            try { context.packageManager.getApplicationIcon(packageName) }
            catch (_: Exception) { null }
        }
    }
    if (iconState.value != null) {
        Image(
            painter          = rememberAsyncImagePainter(iconState.value),
            contentDescription = null,
            modifier         = modifier,
            contentScale     = ContentScale.Fit,
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Android, null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Focus app settings bottom sheet
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusAppSettingsSheet(
    app: AppUsageInfo,
    onDismiss: () -> Unit,
    onSaveLimit: (Long) -> Unit,
    onUpdateCategory: (Boolean) -> Unit,
    onResetApp: () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val initialMinutes   = app.limitMillis?.div(60_000) ?: 0L
    var selectedHours    by remember(app.packageName, initialMinutes) { mutableStateOf((initialMinutes / 60).toInt()) }
    var selectedMins     by remember(app.packageName, initialMinutes) { mutableStateOf((initialMinutes % 60).toInt()) }
    var selectedCategory by remember(app.packageName, app.category) { mutableStateOf(app.category) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val isCurrentlyProductive = selectedCategory == AppCategory.TOOLZ

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape            = ExtraLargeExpressiveShape,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp, 3.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.18f), CircleShape))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 48.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape    = SmallExpressiveShape,
                    color    = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    AppIcon(packageName = app.packageName, modifier = Modifier.padding(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(app.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
                ToolzTonalExpressiveIconButton(onClick = { showResetConfirm = true }, shape = SquircleShape) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Classification", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(true to "Focus", false to "Distract").forEach { (isProd, label) ->
                    val isSelected  = isCurrentlyProductive == isProd
                    val activeColor = if (isProd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    
                    ToolzExpressiveButton(
                        onClick = {
                            selectedCategory = if (isProd) AppCategory.TOOLZ else AppCategory.DISTRACTION
                            onUpdateCategory(isProd)
                        },
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = SquircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) activeColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(if (isProd) Icons.Rounded.AddModerator else Icons.Rounded.Block, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            val context = LocalContext.current
            val isToolzApp = app.packageName == context.packageName

            if (!isToolzApp) {
                Text("Daily time limit", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 120).forEach { mins ->
                        val isSelected  = (selectedHours * 60 + selectedMins) == mins
                        ToolzExpressiveButton(
                            onClick = { selectedHours = mins / 60; selectedMins = mins % 60 },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (mins < 60) "${mins}m" else "${mins / 60}h", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    ToolzExpressiveButton(
                        onClick = {
                            selectedHours = 0
                            selectedMins = 0
                            onSaveLimit(0)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("None", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(20.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    shape    = SquircleShape,
                    color    = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(0.5f).height(48.dp),
                            color    = MaterialTheme.colorScheme.primary.copy(0.06f),
                            shape    = RoundedCornerShape(12.dp),
                        ) {}
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            ScrollableNumberPicker(0..23, selectedHours, { selectedHours = it }, "h")
                            Text(":", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 16.dp).alpha(0.3f))
                            ScrollableNumberPicker(0..59, selectedMins, { selectedMins = it }, "m", formatTwoDigits = true)
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                ToolzExpressiveButton(
                    onClick  = {
                        val totalMins = selectedHours * 60L + selectedMins
                        onSaveLimit(totalMins)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape    = SquircleShape,
                ) {
                    Text("Apply settings", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SquircleShape,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.tertiary)
                        Text(
                            "System application. Tracking and limits are restricted for core productivity tools.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                ToolzExpressiveButton(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape    = SquircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
            title = { Text("Reset app?") },
            text = { Text("Clear all custom settings for ${app.appName}?") },
            confirmButton = {
                ToolzExpressiveTextButton(onClick = {
                    onResetApp()
                    showResetConfirm = false
                    onDismiss()
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                ToolzExpressiveTextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            },
            shape = SquircleShape
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Scrollable number picker
// ─────────────────────────────────────────────────────────────

@Composable
fun ScrollableNumberPicker(
    range: IntRange,
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    label: String,
    formatTwoDigits: Boolean = false,
    visibleItemsCount: Int = 3,
) {
    val haptic = rememberToolzHapticFeedback()
    val items     = remember(range) { range.toList() }
    val itemHeight = 44.dp
    val listState  = rememberLazyListState(
        initialFirstVisibleItemIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    )

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && listState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
            val center   = listState.layoutInfo.viewportEndOffset / 2
            val closest  = listState.layoutInfo.visibleItemsInfo.minByOrNull {
                kotlin.math.abs((it.offset + it.size / 2) - center)
            } ?: return@LaunchedEffect
            val index = closest.index
            if (index in items.indices && items[index] != selectedItem) {
                onItemSelected(items[index])
                listState.animateScrollToItem(index)
                haptic.tick()
            }
        }
    }

    LaunchedEffect(selectedItem) {
        val index = items.indexOf(selectedItem)
        if (index >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem(index)
        }
    }

    Box(Modifier.height(itemHeight * visibleItemsCount).width(64.dp)) {
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItemsCount / 2)),
        ) {
            items(items) { item ->
                val isSelected = item == selectedItem
                val itemScale  by animateFloatAsState(
                    if (isSelected) 1.1f else 0.8f,
                    spring(Spring.DampingRatioMediumBouncy),
                    label = "scale",
                )
                val itemAlpha  by animateFloatAsState(
                    if (isSelected) 1f else 0.3f,
                    tween(150),
                    label = "alpha",
                )
                Box(
                    Modifier.height(itemHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text       = if (formatTwoDigits) String.format("%02d", item) else item.toString(),
                            style      = MaterialTheme.typography.headlineSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color      = MaterialTheme.colorScheme.onSurface,
                            modifier   = Modifier.scale(itemScale).alpha(itemAlpha),
                        )
                        if (isSelected) {
                            Text(
                                text       = label,
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.primary,
                                modifier   = Modifier.padding(start = 2.dp, bottom = 4.dp).alpha(itemAlpha),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun checkAccessibilityEnabled(context: android.content.Context): Boolean = try {
    val enabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
    if (enabled == 1) {
        val service  = "${context.packageName}/com.frerox.toolz.service.FocusFlowAccessibilityService"
        val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        services?.contains(service) == true
    } else false
} catch (_: Exception) { false }

// ─────────────────────────────────────────────────────────────
//  Weekly detailed bottom sheet
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyDetailedSheet(
    viewModel: FocusFlowViewModel,
    usageStats: List<AppUsageInfo>, // Pass usageStats to trigger updates
    onDismiss: () -> Unit,
) {
    val stats by produceState<List<FocusFlowViewModel.DailyLocalStat>>(emptyList(), usageStats) {
        value = viewModel.getWeeklyLocalStats()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape            = ExtraLargeExpressiveShape,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 40.dp).navigationBarsPadding()) {
            Text(
                "Daily breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp),
            ) {
                items(stats) { stat ->
                    val totalH = stat.totalMillis / 3600000
                    val totalM = (stat.totalMillis % 3600000) / 60_000
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = SquircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)),
                        modifier = Modifier.width(220.dp).height(300.dp)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                stat.date,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if(totalH > 0) "${totalH}h ${totalM}m" else "${totalM}m",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(16.dp))
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                stat.topApps.forEach { app ->
                                    val h = app.second / 3600000
                                    val m = (app.second % 3600000) / 60_000
                                    val ts = if (h > 0) "${h}h ${m}m" else "${m}m"
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            app.first,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            ts,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
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
}

// ─────────────────────────────────────────────────────────────
//  Top 5 apps section
// ─────────────────────────────────────────────────────────────

@Composable
private fun Top5AppsSection(topApps: List<AppUsageInfo>, totalTime: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Top usage",
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = BouncyShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.2f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                topApps.forEach { app ->
                    val h = app.usageTimeMillis / 3_600_000
                    val m = (app.usageTimeMillis % 3_600_000) / 60_000
                    val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                    val progress = if (totalTime > 0) app.usageTimeMillis.toFloat() / totalTime else 0f

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.size(32.dp)) {
                            AppIcon(packageName = app.packageName, modifier = Modifier.fillMaxSize())
                        }
                        Column(Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    app.appName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    timeStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            ToolzWavyLinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Screen tips bottom sheet
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScreenTipsSheet(viewModel: FocusFlowViewModel, onDismiss: () -> Unit) {
    val tips by viewModel.screenTips.collectAsState()
    val isLoading by viewModel.isLoadingTips.collectAsState()
    val customInstructions by viewModel.customInstructions.collectAsState()
    val haptic = rememberToolzHapticFeedback()

    var showInstructionsDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.generateScreenTips()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shape            = ExtraLargeExpressiveShape,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp).navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(SquircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(MaterialTheme.colorScheme.primary.copy(0.1f), MaterialTheme.colorScheme.tertiary.copy(0.1f))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lightbulb, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "AI focus insights",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .bouncyClick(onLongClick = { showInstructionsDialog = true }) {
                             viewModel.generateScreenTips(forceRefresh = true)
                        },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = SquircleShape,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, "Refresh tips")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = { haptic.click(); showInstructionsDialog = true },
                        onLongClick = { haptic.longClick(); showInstructionsDialog = true }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Text(
                    if (customInstructions.isBlank()) "Custom instructions" else "Edit instructions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(24.dp))

            if (isLoading && tips == null) {
                Column(Modifier.fillMaxWidth().height(200.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    ExpressiveLoadingWheel(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("Generating custom tips…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                }
            } else {
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = SquircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(20.dp)
                        ) {
                            if (tips != null) {
                                val segments = parseMarkdownToSegments(tips!!)
                                segments.forEach { seg ->
                                    MarkdownSegment(seg, modifier = Modifier.padding(vertical = 4.dp))
                                }
                            } else {
                                Text(
                                    "No tips available. Refresh to generate.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInstructionsDialog) {
        var textInput by remember { mutableStateOf(customInstructions) }
        AlertDialog(
            onDismissRequest = { showInstructionsDialog = false },
            icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
            title = { Text("AI guidance") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tell the AI your focus goals.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("e.g. Focus on reading more.") },
                        shape = SmallExpressiveShape
                    )
                }
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        viewModel.setCustomInstructions(textInput)
                        showInstructionsDialog = false
                        viewModel.generateScreenTips(forceRefresh = true)
                    },
                    shape = SmallExpressiveShape
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                ToolzExpressiveTextButton(onClick = { showInstructionsDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = SquircleShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

val CapsuleShape = CircleShape
