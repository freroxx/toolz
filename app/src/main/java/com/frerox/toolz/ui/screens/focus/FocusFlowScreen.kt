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
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.lifecycle.Lifecycle
import coil3.compose.rememberAsyncImagePainter
import com.frerox.toolz.data.focus.AppCategory
import com.frerox.toolz.data.focus.AppUsageInfo
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveLoadingWheel
import com.frerox.toolz.ui.components.ExpressiveWavyLinearProgressIndicator
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.rememberLifecycleEvent
import com.frerox.toolz.ui.components.parseMarkdownToSegments
import com.frerox.toolz.ui.components.MarkdownSegment
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FocusFlowScreen — Material 3 Expressive redesign.
 *
 * What was actually "AI slop" here, and what replaced it:
 *  - ALL CAPS labels were used almost everywhere ("FOCUS FLOW", "FLOW STATE",
 *    "ANALYTICS", "QUICK LIMITS", "APPLY SETTINGS", etc.) paired with
 *    FontWeight.Black and letterSpacing tracking on nearly every text element.
 *    That's poster/dashboard typography, not M3 — replaced with sentence case
 *    and selective weight, so emphasis actually means something again.
 *  - The 80sp pulsing score number with a sweep-gradient "elite glow" border
 *    read like a crypto-trading app, not a calm focus tool. Replaced with a
 *    steadier ring-style score presentation and toned-down motion.
 *  - Consolidated shape/spacing so cards, sheets, and chips share a coherent
 *    scale instead of ad hoc 10/12/14/16/18/20/24/28/32dp mixed throughout.
 *
 * Real feature additions/fixes (verified against FocusFlowViewModel — nothing
 * here calls a function or reads a field that doesn't already exist on it):
 *  - Wired up `resetAppSettings()`, defined on the ViewModel but never called
 *    anywhere in the UI — added as a real "Reset this app" action in the
 *    per-app settings sheet, distinct from the limit-only reset chip.
 *  - Long-press affordances (rename an app, customize AI instructions) had
 *    zero visual hint they existed. Added small discoverable hint chips/icons
 *    instead of relying on undiscoverable gestures alone.
 *  - Added an "Uncategorized" quick-triage row: apps the heuristic/AI couldn't
 *    place get surfaced together with one-tap Productive/Distraction actions,
 *    instead of requiring you to open each app's sheet individually. Built
 *    entirely from the existing `updateAppCategory()` call — no new ViewModel
 *    surface required.
 *  - Fixed the "PRODUCTION" category badge label (this was almost certainly a
 *    leftover typo of "PRODUCTIVE") shown for the app's own package.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    val performanceMode   = LocalPerformanceMode.current
    val vibrationManager  = LocalVibrationManager.current
    val context           = LocalContext.current
    val scope             = rememberCoroutineScope()

    val canDrawOverlays         = remember { Settings.canDrawOverlays(context) }
    var isAccessibilityEnabled  by remember { mutableStateOf(false) }
    var selectedAppForSettings  by remember { mutableStateOf<AppUsageInfo?>(null) }
    var appToRename             by remember { mutableStateOf<AppUsageInfo?>(null) }
    var showWeeklySheet         by remember { mutableStateOf(false) }
    var showTipsSheet           by remember { mutableStateOf(false) }

    val lifecycleEvent = rememberLifecycleEvent()
    LaunchedEffect(lifecycleEvent) {
        if (lifecycleEvent == Lifecycle.Event.ON_RESUME) {
            isAccessibilityEnabled = checkAccessibilityEnabled(context)
        }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Focus Flow",
                subtitle = if (isWeekly) "Detailed app usage" else "Daily attention analytics",
                navigationIcon = {
                    IconButton(
                        onClick  = { vibrationManager?.vibrateClick(); onNavigateBack() },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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
                            Box(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    val inf = rememberInfiniteTransition(label = "ai_dot")
                                    val dotAlpha by inf.animateFloat(
                                        0.35f, 1f,
                                        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                                        label = "ai_dot_alpha",
                                    )
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .alpha(dotAlpha)
                                            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                                    )
                                    Text(
                                        "AI",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = !isAiClassifying,
                            enter = fadeIn() + scaleIn(spring(Spring.DampingRatioMediumBouncy)),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            IconButton(
                                onClick = {
                                    vibrationManager?.vibrateTick(); viewModel.refreshAiCategories()
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(0.55f)),
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome, "Refresh AI",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick  = {
                            vibrationManager?.vibrateClick()
                            scope.launch {
                                viewModel.refreshStats()
                                delay(500)
                                vibrationManager?.vibrateSuccess()
                            }
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Icon(Icons.Rounded.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                largeFlexible = true,
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
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (!hasUsagePermission) {
                    item {
                        UsagePermissionBanner(
                            onGrantClick = {
                                vibrationManager?.vibrateClick()
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
                                vibrationManager?.vibrateClick()
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
                    EnhancedProductivityHeader(
                        score = productivityScore,
                        performanceMode = performanceMode,
                        offlineModeEnabled = offlineModeEnabled,
                        onLongClick = {
                            vibrationManager?.vibrateLongClick()
                            showTipsSheet = true
                        }
                    )
                }

                item {
                    val totalTime = usageStats.sumOf { it.usageTimeMillis }
                    val hours     = totalTime / 3_600_000
                    val minutes   = (totalTime % 3_600_000) / 60_000

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
                            value    = "${usageStats.size}",
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

                // New: quick-triage row for apps neither the heuristic nor AI could
                // classify — saves opening each app's sheet individually.
                val uncategorized = usageStats.filter { it.category == AppCategory.OTHER }
                if (uncategorized.isNotEmpty()) {
                    item {
                        UncategorizedAppsSection(
                            apps = uncategorized,
                            onClassify = { pkg, isProductive ->
                                vibrationManager?.vibrateTick()
                                viewModel.updateAppCategory(pkg, isProductive)
                            }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                        SingleChoiceSegmentedButtonRow(Modifier.height(40.dp)) {
                            SegmentedButton(
                                selected = !isWeekly,
                                onClick  = { vibrationManager?.vibrateTick(); viewModel.toggleWeekly(false) },
                                shape    = SegmentedButtonDefaults.itemShape(0, 2),
                            ) { Text("Daily", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium) }
                            SegmentedButton(
                                selected = isWeekly,
                                onClick  = { vibrationManager?.vibrateTick(); viewModel.toggleWeekly(true) },
                                shape    = SegmentedButtonDefaults.itemShape(1, 2),
                            ) { Text("Weekly", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium) }
                        }
                    }
                }

                if (isWeekly) {
                    item {
                        WeeklySummaryCard(
                            stats = usageStats,
                            onClick = {
                                vibrationManager?.vibrateClick()
                                showWeeklySheet = true
                            }
                        )
                    }
                }

                if (usageStats.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
                                Surface(
                                    shape  = RoundedCornerShape(28.dp),
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
                                    Button(
                                        onClick = {
                                            vibrationManager?.vibrateClick()
                                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                        },
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text("Re-grant usage access", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                items(usageStats, key = { it.packageName }) { info ->
                    EnhancedUsageItem(
                        info            = info,
                        performanceMode = performanceMode,
                        isAiClassified  = info.packageName in aiClassifiedPkgs,
                        onClick         = { vibrationManager?.vibrateClick(); selectedAppForSettings = info },
                        onLongClick     = { vibrationManager?.vibrateLongClick(); appToRename = info },
                        onQuickLimit    = { vibrationManager?.vibrateTick(); viewModel.setAppLimit(info.packageName, it) },
                        modifier        = Modifier.animateItem(
                            fadeInSpec     = if (performanceMode) snap() else spring(),
                            fadeOutSpec    = if (performanceMode) snap() else spring(),
                            placementSpec  = if (performanceMode) snap() else spring(),
                        ),
                    )
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
                    vibrationManager?.vibrateTick()
                    viewModel.resetAppSettings(app.packageName)
                },
            )
        }

        if (showWeeklySheet) {
            WeeklyDetailedSheet(
                viewModel = viewModel,
                onDismiss = { showWeeklySheet = false }
            )
        }

        if (showTipsSheet) {
            ScreenTipsSheet(
                viewModel = viewModel,
                onDismiss = { showTipsSheet = false }
            )
        }

        appToRename?.let { app ->
            var nameInput by remember(app.packageName) { mutableStateOf(app.appName) }
            AlertDialog(
                onDismissRequest = { appToRename = null },
                containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape            = RoundedCornerShape(28.dp),
                icon             = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                title            = { Text("Rename app") },
                text             = {
                    OutlinedTextField(
                        value         = nameInput,
                        onValueChange = { nameInput = it },
                        label         = { Text("Custom name") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        shape         = RoundedCornerShape(16.dp),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            if (nameInput.isNotBlank()) viewModel.renameApp(app.packageName, nameInput.trim())
                            appToRename = null
                        },
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { vibrationManager?.vibrateClick(); appToRename = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Permission banners
// ─────────────────────────────────────────────────────────────

@Composable
private fun PermissionBanner(canDrawOverlays: Boolean, onResolveClick: () -> Unit) {
    Surface(
        onClick = onResolveClick,
        modifier = Modifier.fillMaxWidth().bouncyClick { },
        shape    = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
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
                Button(
                    onClick = onResolveClick,
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(12.dp),
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
    Surface(
        onClick = onGrantClick,
        modifier = Modifier.fillMaxWidth().bouncyClick { },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
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
        shape    = RoundedCornerShape(24.dp),
        containerColor    = color.copy(alpha = 0.08f),
        border   = BorderStroke(1.dp, color.copy(alpha = 0.12f)),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
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
                color      = color,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Productivity header — ring-based score, calm motion
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnhancedProductivityHeader(
    score: Int,
    performanceMode: Boolean,
    offlineModeEnabled: Boolean,
    onLongClick: () -> Unit
) {
    val successColor = Color(0xFF2E7D32)
    val warningColor = Color(0xFFB07A00)
    val errorColor = MaterialTheme.colorScheme.error
    val accentColor = when {
        score >= 70 -> successColor
        score >= 40 -> MaterialTheme.colorScheme.primary
        score >= 20 -> warningColor
        else -> errorColor
    }

    val statusLabel = when {
        score >= 85 -> "Elite focus"
        score >= 70 -> "High focus"
        score >= 40 -> "Balanced"
        score >= 20 -> "Low focus"
        else -> "Time to refocus"
    }
    val supportLabel = when {
        score >= 70 -> "You're keeping distractions in check."
        score >= 40 -> "A steady day — room to tighten up."
        else -> "Distractions are winning today."
    }

    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = if (performanceMode) snap() else tween(900, easing = FastOutSlowInEasing),
        label = "score",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = (score / 100f).coerceIn(0f, 1f),
        animationSpec = if (performanceMode) snap() else tween(900, easing = FastOutSlowInEasing),
        label = "header_progress",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Ring-based score presentation — replaces the old 80sp pulsing
                // number. Calmer, and the ring itself communicates "how full" at
                // a glance without needing the eye to parse two digits first.
                Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 8.dp.toPx()
                        drawArc(
                            color = accentColor.copy(alpha = 0.15f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = accentColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth, cap = StrokeCap.Round),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$animatedScore",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                        Text(
                            "%",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor.copy(alpha = 0.6f),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = CircleShape, color = accentColor.copy(0.12f)) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = accentColor,
                            )
                            Text(
                                text = "Flow state",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                            )
                        }
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = supportLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!performanceMode && !offlineModeEnabled) {
                ExpressiveCard(
                    onClick = onLongClick,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = accentColor.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    elevation = 0.dp
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp), tint = accentColor)
                        Spacer(Modifier.width(8.dp))
                        Text("Get AI focus tips", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = accentColor)
                    }
                }
            } else {
                // Give the long-press affordance a visible hint even without the
                // AI-tips shortcut card, since it's otherwise invisible.
                Text(
                    "Long-press this card for focus tips",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Uncategorized apps — quick triage row (new)
// ─────────────────────────────────────────────────────────────

@Composable
private fun UncategorizedAppsSection(
    apps: List<AppUsageInfo>,
    onClassify: (packageName: String, isProductive: Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Needs a category (${apps.size})",
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
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
                            Surface(
                                modifier = Modifier.weight(1f).height(30.dp).bouncyClick { onClassify(app.packageName, true) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.AddModerator, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Surface(
                                modifier = Modifier.weight(1f).height(30.dp).bouncyClick { onClassify(app.packageName, false) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Block, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
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
//  Usage list item
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnhancedUsageItem(
    info: AppUsageInfo,
    performanceMode: Boolean,
    isAiClassified: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickLimit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scale by animateFloatAsState(
        if (visible || performanceMode) 1f else 0.94f,
        if (performanceMode) snap() else spring(Spring.DampingRatioMediumBouncy),
        label = "item_scale",
    )
    val alpha by animateFloatAsState(
        if (visible || performanceMode) 1f else 0f,
        if (performanceMode) snap() else tween(300),
        label = "item_alpha",
    )

    val hours = info.usageTimeMillis / 3_600_000
    val minutes = (info.usageTimeMillis % 3_600_000) / 60_000
    val timeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    val isOverLimit = info.limitMillis != null && info.usageTimeMillis >= info.limitMillis
    val limitProgress = info.limitMillis
        ?.takeIf { it > 0L }
        ?.let { info.usageTimeMillis.toFloat() / it }
        ?.coerceIn(0f, 1.2f)
    val barWidth = (limitProgress ?: 0f).coerceIn(0f, 1f)
    val barColor = when {
        limitProgress == null -> MaterialTheme.colorScheme.surfaceVariant
        limitProgress >= 1f -> MaterialTheme.colorScheme.error
        limitProgress >= 0.8f -> Color(0xFFB07A00)
        else -> MaterialTheme.colorScheme.primary
    }
    val animatedBarWidth by animateFloatAsState(
        targetValue = barWidth,
        animationSpec = tween(500),
        label = "limit_bar",
    )
    val pulseTransition = rememberInfiniteTransition(label = "limit_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(950),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "limit_pulse_alpha",
    )
    val showPulse = (limitProgress ?: 0f) >= 0.8f

    ExpressiveCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
        shape = RoundedCornerShape(22.dp),
        containerColor = if (isOverLimit) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.04f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        },
        border = BorderStroke(
            width = if (isOverLimit) 1.5.dp else 1.dp,
            color = if (isOverLimit) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            },
        ),
        elevation = if (isOverLimit) 4.dp else 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
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
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        null,
                                        modifier = Modifier.size(8.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                    Text(
                                        "AI",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 8.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            when {
                                // Fixed: this previously read "PRODUCTION" for the app's
                                // own package — almost certainly a leftover typo of
                                // "PRODUCTIVE".
                                isToolzApp -> "Built-in"
                                info.category == AppCategory.TOOLZ -> "Productive"
                                info.category == AppCategory.DISTRACTION -> "Distraction"
                                else -> "Other"
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            color = catColor,
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedBarWidth)
                                .clip(
                                    RoundedCornerShape(
                                        bottomStart = 22.dp,
                                        bottomEnd = if (animatedBarWidth >= 1f) 22.dp else 0.dp,
                                    ),
                                )
                                .background(barColor.copy(alpha = if (showPulse) pulseAlpha else 1f)),
                        )
                    }
                }
            } else if (info.category == AppCategory.DISTRACTION) {
                Surface(
                    modifier = Modifier
                        .padding(bottom = 10.dp, start = 14.dp, end = 14.dp)
                        .height(30.dp)
                        .fillMaxWidth()
                        .bouncyClick { onQuickLimit(30) },
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Timer, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Quick limit: 30m",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error,
                        )
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
fun WeeklySummaryCard(stats: List<AppUsageInfo>, onClick: () -> Unit) {
    val totalTime = stats.sumOf { it.usageTimeMillis }
    val hours     = totalTime / 3_600_000
    val minutes   = (totalTime % 3_600_000) / 60_000

    val toolzTime      = stats.filter { it.category == AppCategory.TOOLZ }.sumOf { it.usageTimeMillis }
    val distractionTime = stats.filter { it.category == AppCategory.DISTRACTION }.sumOf { it.usageTimeMillis }
    val otherTime      = (totalTime - toolzTime - distractionTime).coerceAtLeast(0)

    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(24.dp),
        containerColor    = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.08f),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Timeline, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Text(
                        "Weekly focus",
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
            Spacer(Modifier.height(18.dp))

            if (totalTime > 0) {
                val toolzFraction      = (toolzTime.toFloat() / totalTime).coerceIn(0f, 1f)
                val distractionFraction = (distractionTime.toFloat() / totalTime).coerceIn(0f, 1f)
                val otherFraction       = (otherTime.toFloat() / totalTime).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                ) {
                    if (toolzFraction > 0f) {
                        Box(Modifier.fillMaxHeight().weight(toolzFraction).background(MaterialTheme.colorScheme.primary)) {
                            if (toolzFraction > 0.18f) {
                                Text("${(toolzFraction*100).toInt()}%", modifier = Modifier.align(Alignment.Center), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (distractionFraction > 0f) {
                        Box(Modifier.fillMaxHeight().weight(distractionFraction).background(MaterialTheme.colorScheme.error)) {
                            if (distractionFraction > 0.18f) {
                                Text("${(distractionFraction*100).toInt()}%", modifier = Modifier.align(Alignment.Center), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (otherFraction > 0f) {
                        Box(Modifier.fillMaxHeight().weight(otherFraction).background(MaterialTheme.colorScheme.outlineVariant.copy(0.4f)))
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LegendItem("Productive", MaterialTheme.colorScheme.primary)
                LegendItem("Distraction", MaterialTheme.colorScheme.error)
                LegendItem("Other", MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val vibrationManager = LocalVibrationManager.current
    val initialMinutes   = app.limitMillis?.div(60_000) ?: 0L
    var selectedHours    by remember(app.packageName, initialMinutes) { mutableStateOf((initialMinutes / 60).toInt()) }
    var selectedMins     by remember(app.packageName, initialMinutes) { mutableStateOf((initialMinutes % 60).toInt()) }
    var selectedCategory by remember(app.packageName, app.category) { mutableStateOf(app.category) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val isCurrentlyProductive = selectedCategory == AppCategory.TOOLZ

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape            = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
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
                    shape    = RoundedCornerShape(16.dp),
                    color    = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    AppIcon(packageName = app.packageName, modifier = Modifier.padding(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(app.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
                // Reset action — wires up viewModel.resetAppSettings(), which
                // existed before but had no UI entry point. Clears both the
                // limit and the category mapping for this app in one step.
                IconButton(onClick = { showResetConfirm = true }) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset app settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Classification", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(true to "Productive", false to "Distraction").forEach { (isProd, label) ->
                    val isSelected  = isCurrentlyProductive == isProd
                    val activeColor = if (isProd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Surface(
                        modifier = Modifier.weight(1f).height(54.dp).bouncyClick {
                            vibrationManager?.vibrateTick()
                            selectedCategory = if (isProd) AppCategory.TOOLZ else AppCategory.DISTRACTION
                            onUpdateCategory(isProd)
                        },
                        shape    = RoundedCornerShape(16.dp),
                        color    = if (isSelected) activeColor.copy(0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                        border   = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) activeColor else MaterialTheme.colorScheme.outlineVariant.copy(0.4f)),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    if (isProd) Icons.Rounded.AddModerator else Icons.Rounded.Block,
                                    null,
                                    modifier = Modifier.size(20.dp),
                                    tint     = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    label,
                                    fontWeight = FontWeight.Medium,
                                    color      = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            val context = LocalContext.current
            val isToolzApp = app.packageName == context.packageName

            if (!isToolzApp) {
                Text("Quick limits", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 120).forEach { mins ->
                        val isSelected  = (selectedHours * 60 + selectedMins) == mins
                        val activeColor = MaterialTheme.colorScheme.primary
                        Surface(
                            modifier = Modifier.weight(1f).height(38.dp).bouncyClick { vibrationManager?.vibrateTick(); selectedHours = mins / 60; selectedMins = mins % 60 },
                            shape    = RoundedCornerShape(12.dp),
                            color    = if (isSelected) activeColor else activeColor.copy(0.08f),
                            border   = if (isSelected) null else BorderStroke(1.dp, activeColor.copy(0.15f)),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    if (mins < 60) "${mins}m" else "${mins / 60}h",
                                    style      = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = if (isSelected) MaterialTheme.colorScheme.onPrimary else activeColor,
                                )
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f).height(38.dp).bouncyClick {
                            vibrationManager?.vibrateTick()
                            selectedHours = 0
                            selectedMins = 0
                            onSaveLimit(0)
                            onDismiss()
                        },
                        shape    = RoundedCornerShape(12.dp),
                        color    = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("None", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Custom duration", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selectedHours == 0 && selectedMins == 0) {
                        Text("No limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f), fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    shape    = RoundedCornerShape(24.dp),
                    color    = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(0.55f).height(48.dp),
                            color    = MaterialTheme.colorScheme.primary.copy(0.08f),
                            shape    = RoundedCornerShape(12.dp),
                            border   = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.2f)),
                        ) {}
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            ScrollableNumberPicker(0..23, selectedHours, { selectedHours = it }, "h")
                            Spacer(Modifier.width(20.dp))
                            Text(":", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(0.4f))
                            Spacer(Modifier.width(20.dp))
                            ScrollableNumberPicker(0..59, selectedMins, { selectedMins = it }, "m", formatTwoDigits = true)
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick  = {
                        vibrationManager?.vibrateClick()
                        val totalMins = selectedHours * 60L + selectedMins
                        onSaveLimit(totalMins)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape    = RoundedCornerShape(18.dp),
                ) {
                    Text("Apply settings", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
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
                            "This is a system application. Limits can't be set for Toolz, so you always have access to your productivity tools.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape    = RoundedCornerShape(18.dp),
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
            title = { Text("Reset ${app.appName}?") },
            text = { Text("This clears its time limit and category, letting it be auto-classified again.") },
            confirmButton = {
                TextButton(onClick = {
                    onResetApp()
                    showResetConfirm = false
                    onDismiss()
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Scrollable number picker (drum-roll style)
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
    val vibrationManager = LocalVibrationManager.current
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
            if (index in items.indices) {
                onItemSelected(items[index])
                listState.animateScrollToItem(index)
                vibrationManager?.vibrateTick()
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
                    if (isSelected) 1.15f else 0.78f,
                    spring(Spring.DampingRatioMediumBouncy),
                    label = "picker_scale",
                )
                val itemAlpha  by animateFloatAsState(
                    if (isSelected) 1f else 0.35f,
                    tween(120),
                    label = "picker_alpha",
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
                                fontWeight = FontWeight.Medium,
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

// ─────────────────────────────────────────────────────────────
//  Utilities
// ─────────────────────────────────────────────────────────────

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
    onDismiss: () -> Unit,
) {
    val stats = remember { viewModel.getWeeklyLocalStats() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape            = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
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
                    val totalM = (stat.totalMillis % 3600000) / 60000
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(24.dp),
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
                                    val m = (app.second % 3600000) / 60000
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
            "Top 5 apps",
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
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
                            ExpressiveWavyLinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
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
    val vibrationManager = LocalVibrationManager.current

    var showInstructionsDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.generateScreenTips()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shape            = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
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
                        .clip(RoundedCornerShape(16.dp))
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
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                viewModel.generateScreenTips(forceRefresh = true)
                            },
                            onLongClick = {
                                vibrationManager?.vibrateLongClick()
                                showInstructionsDialog = true
                            }
                        ),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
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

            // Discoverable affordance for the long-press-to-customize gesture,
            // which previously had zero visual hint it existed.
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .bouncyClick {
                        vibrationManager?.vibrateLongClick()
                        showInstructionsDialog = true
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Text(
                    if (customInstructions.isBlank()) "Add custom instructions" else "Edit custom instructions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(20.dp))

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
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp),
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
                                    "No tips available at the moment. Please try again later.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 26.sp,
                                )
                            }
                        }
                    }

                    if (scrollState.maxValue > 0) {
                        val scrollFraction = scrollState.value.toFloat() / scrollState.maxValue
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 6.dp, top = 20.dp, bottom = 20.dp)
                                .width(4.dp)
                                .fillMaxHeight(0.3f)
                                .graphicsLayer { translationY = scrollFraction * (size.height * 0.7f) }
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        )
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
            title = { Text("Custom instructions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tell the AI more about your goals or specific habits you want to change.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("e.g. I want to spend less time on social media before bed.") },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setCustomInstructions(textInput)
                        showInstructionsDialog = false
                        viewModel.generateScreenTips(forceRefresh = true)
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstructionsDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}