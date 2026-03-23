package com.frerox.toolz.ui.screens.focus

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.frerox.toolz.ui.components.SquigglySlider
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.rememberLifecycleEvent
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────
//  Main screen
// ─────────────────────────────────────────────────────────────

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
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "FOCUS FLOW",
                                fontWeight = FontWeight.Black,
                                style      = MaterialTheme.typography.labelMedium,
                                letterSpacing = 2.sp,
                            )
                            Text(
                                if (isWeekly) "WEEKLY PERFORMANCE" else "TODAY'S USAGE",
                                style     = MaterialTheme.typography.labelSmall,
                                color     = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
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
                        // Pulsing "AI" pill while Groq is classifying
                        AnimatedVisibility(
                            visible = isAiClassifying,
                            enter   = fadeIn() + scaleIn(),
                            exit    = fadeOut() + scaleOut(),
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
                                        0.3f, 1f,
                                        infiniteRepeatable(tween(600), RepeatMode.Reverse),
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
                                        style      = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color      = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                        }

                        // AI refresh — clears cache and re-classifies all apps
                        AnimatedVisibility(
                            visible = !isAiClassifying,
                            enter   = fadeIn() + scaleIn(spring(Spring.DampingRatioMediumBouncy)),
                            exit    = fadeOut() + scaleOut(),
                        ) {
                            IconButton(
                                onClick  = { vibrationManager?.vibrateTick(); viewModel.refreshAiCategories() },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(0.55f)),
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome, "Refresh AI",
                                    modifier = Modifier.size(18.dp),
                                    tint     = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }

                        // Usage stats refresh
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
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color     = MaterialTheme.colorScheme.outlineVariant.copy(0.4f),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Box(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (performanceMode) Modifier
                        else Modifier.fadingEdges(top = 16.dp, bottom = 16.dp)
                    ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── Permission banner ──────────────────────────────────────
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

                // ── Productivity header ────────────────────────────────────
                item {
                    EnhancedProductivityHeader(
                        score = productivityScore,
                        performanceMode = performanceMode,
                        onLongClick = {
                            vibrationManager?.vibrateLongClick()
                            showTipsSheet = true
                        }
                    )
                }

                // ── Metric summary row ─────────────────────────────────────
                item {
                    val totalTime = usageStats.sumOf { it.usageTimeMillis }
                    val hours     = totalTime / 3_600_000
                    val minutes   = (totalTime % 3_600_000) / 60_000

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MetricCard(
                            label    = "SCREEN TIME",
                            value    = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                            icon     = Icons.Rounded.PhoneAndroid,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        MetricCard(
                            label    = "APPS USED",
                            value    = "${usageStats.size}",
                            icon     = Icons.Rounded.Apps,
                            color    = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ── Analytics toggle + weekly chart ───────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            "ANALYTICS",
                            modifier = Modifier.padding(horizontal = 12.dp),
                            style    = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color    = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                        )
                        SingleChoiceSegmentedButtonRow(Modifier.height(40.dp)) {
                            SegmentedButton(
                                selected = !isWeekly,
                                onClick  = { vibrationManager?.vibrateTick(); viewModel.toggleWeekly(false) },
                                shape    = SegmentedButtonDefaults.itemShape(0, 2),
                            ) { Text("Daily", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                            SegmentedButton(
                                selected = isWeekly,
                                onClick  = { vibrationManager?.vibrateTick(); viewModel.toggleWeekly(true) },
                                shape    = SegmentedButtonDefaults.itemShape(1, 2),
                            ) { Text("Weekly", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
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

                // ── Empty state ────────────────────────────────────────────
                if (usageStats.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape  = RoundedCornerShape(28.dp),
                                    color  = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.size(100.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Assessment, null,
                                        modifier = Modifier.padding(24.dp),
                                        tint     = MaterialTheme.colorScheme.outline,
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "No usage data collected yet.",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // ── App list ───────────────────────────────────────────────
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

        // ── App settings bottom sheet ──────────────────────────────────────
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
            )
        }

        // ── Weekly detailed sheet ──────────────────────────────────────────
        if (showWeeklySheet) {
            WeeklyDetailedSheet(
                viewModel = viewModel,
                onDismiss = { showWeeklySheet = false }
            )
        }

        // ── Screen tips sheet ──────────────────────────────────────────────
        if (showTipsSheet) {
            ScreenTipsSheet(
                viewModel = viewModel,
                onDismiss = { showTipsSheet = false }
            )
        }

        // ── Rename dialog ──────────────────────────────────────────────────
        appToRename?.let { app ->
            var nameInput by remember(app.packageName) { mutableStateOf(app.appName) }
            AlertDialog(
                onDismissRequest = { appToRename = null },
                containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape            = RoundedCornerShape(28.dp),
                title            = { Text("Rename App", fontWeight = FontWeight.Black) },
                text             = {
                    OutlinedTextField(
                        value         = nameInput,
                        onValueChange = { nameInput = it },
                        label         = { Text("Custom Name") },
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
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("SAVE", fontWeight = FontWeight.Black) }
                },
                dismissButton = {
                    TextButton(onClick = { vibrationManager?.vibrateClick(); appToRename = null }) {
                        Text("CANCEL")
                    }
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Permission banner
// ─────────────────────────────────────────────────────────────

@Composable
private fun PermissionBanner(canDrawOverlays: Boolean, onResolveClick: () -> Unit) {
    Surface(
        onClick = onResolveClick,
        modifier = Modifier.fillMaxWidth().bouncyClick { },
        shape    = RoundedCornerShape(24.dp),
        color    = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
        border   = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (!canDrawOverlays) Icons.Rounded.Layers else Icons.Rounded.AccessibilityNew,
                    null,
                    tint     = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (!canDrawOverlays) "OVERLAY REQUIRED" else "ACCESSIBILITY REQUIRED",
                    fontWeight    = FontWeight.Black,
                    color         = MaterialTheme.colorScheme.error,
                    style         = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (!canDrawOverlays)
                        "Needed to block distracting apps"
                    else
                        "Accessibility service helps the flow engine",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onResolveClick,
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("AUTHORIZE", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                }
            }
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
    Surface(
        modifier = modifier.bouncyClick { },
        shape    = RoundedCornerShape(24.dp),
        color    = color.copy(alpha = 0.08f),
        border   = BorderStroke(1.dp, color.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
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
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style         = MaterialTheme.typography.labelSmall,
                fontWeight    = FontWeight.Bold,
                color         = color,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Productivity header — clean linear design (no rotating arc)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnhancedProductivityHeader(score: Int, performanceMode: Boolean, onLongClick: () -> Unit) {
    val successColor = Color(0xFF43A047)
    val warningColor = Color(0xFFFFA726)
    val errorColor = MaterialTheme.colorScheme.error
    val accentColor = when {
        score >= 70 -> successColor
        score >= 40 -> MaterialTheme.colorScheme.primary
        score >= 20 -> warningColor
        else -> errorColor
    }
    val isElite = score >= 85

    val statusLabel = when {
        score >= 85 -> "Elite Productivity"
        score >= 70 -> "High Focus"
        score >= 40 -> "Balanced"
        score >= 20 -> "Low Focus"
        else -> "Time To Refocus"
    }
    val supportLabel = when {
        score >= 70 -> "Keep going."
        score >= 40 -> "Not bad."
        else -> "Stop wasting your time."
    }

    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = if (performanceMode) snap() else tween(1200, easing = FastOutSlowInEasing),
        label = "score",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = (score / 100f).coerceIn(0f, 1f),
        animationSpec = if (performanceMode) snap() else tween(1200, easing = FastOutSlowInEasing),
        label = "header_progress",
    )

    val pulseTransition = rememberInfiniteTransition(label = "header_pulse")
    val pulseRaw by pulseTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "header_pulse_alpha",
    )
    val glowStrength = if (isElite && !performanceMode) pulseRaw else 0.25f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .graphicsLayer {
                if (isElite && !performanceMode) {
                    shadowElevation = 18.dp.toPx()
                    spotShadowColor = accentColor.copy(alpha = 0.35f)
                }
            },
        shape = RoundedCornerShape(28.dp),
        color = accentColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = glowStrength)),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isElite) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = accentColor,
                            )
                        }
                        Text(
                            text = "FLOW STATE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = accentColor,
                            letterSpacing = 1.5.sp,
                        )
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = supportLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accentColor.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = "LONG PRESS FOR AI TIPS",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = accentColor,
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$animatedScore",
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 72.sp),
                            fontWeight = FontWeight.Black,
                            color = accentColor,
                            modifier = Modifier.graphicsLayer {
                                if (isElite && !performanceMode) {
                                    scaleX = 1f + (pulseRaw - 0.7f) * 0.1f
                                    scaleY = 1f + (pulseRaw - 0.7f) * 0.1f
                                }
                            },
                        )
                        Text(
                            text = "%",
                            modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = accentColor.copy(alpha = 0.45f),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(accentColor.copy(alpha = 0.55f), accentColor),
                                ),
                            ),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    Text("50", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    Text("100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                }
            }

            SquigglySlider(
                value = score.toFloat(),
                onValueChange = {},
                valueRange = 0f..100f,
                isPlaying = !performanceMode,
                activeColor = accentColor,
                inactiveColor = accentColor.copy(alpha = 0.12f),
            )
        }
    }
}
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
        if (visible || performanceMode) 1f else 0.92f,
        if (performanceMode) snap() else spring(Spring.DampingRatioMediumBouncy),
        label = "item_scale",
    )
    val alpha by animateFloatAsState(
        if (visible || performanceMode) 1f else 0f,
        if (performanceMode) snap() else tween(400),
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
        limitProgress >= 0.8f -> Color(0xFFFFA000)
        else -> MaterialTheme.colorScheme.primary
    }
    val animatedBarWidth by animateFloatAsState(
        targetValue = barWidth,
        animationSpec = tween(600),
        label = "limit_bar",
    )
    val pulseTransition = rememberInfiniteTransition(label = "limit_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(950),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "limit_pulse_alpha",
    )
    val showPulse = (limitProgress ?: 0f) >= 0.8f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(24.dp),
        color = if (isOverLimit) {
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
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isAiClassified) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(5.dp),
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
                                        fontWeight = FontWeight.Black,
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
                                "Limit: ${info.limitMillis / 60_000}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else {
                            Text(
                                info.packageName.lowercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        timeStr,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    val catColor = when (info.category) {
                        AppCategory.TOOLZ -> MaterialTheme.colorScheme.primary
                        AppCategory.DISTRACTION -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                    Surface(
                        color = catColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            when (info.category) {
                                AppCategory.TOOLZ -> "PRODUCTIVE"
                                AppCategory.DISTRACTION -> "DISTRACTION"
                                else -> "OTHER"
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontSize = 8.sp,
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
                            fontWeight = FontWeight.Bold,
                            color = barColor,
                        )
                        Text(
                            text = "${(limitProgress * 100).toInt().coerceAtMost(120)}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedBarWidth)
                                .clip(
                                    RoundedCornerShape(
                                        bottomStart = 24.dp,
                                        bottomEnd = if (animatedBarWidth >= 1f) 24.dp else 0.dp,
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
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun WeeklySummaryCard(stats: List<AppUsageInfo>, onClick: () -> Unit) {
    val totalTime = stats.sumOf { it.usageTimeMillis }
    val hours     = totalTime / 3_600_000
    val minutes   = (totalTime % 3_600_000) / 60_000

    Surface(
        modifier = Modifier.fillMaxWidth().bouncyClick { onClick() },
        shape    = RoundedCornerShape(24.dp),
        color    = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.08f),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Timeline, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Text(
                    "WEEKLY SCREEN TIME",
                    style         = MaterialTheme.typography.labelMedium,
                    fontWeight    = FontWeight.Black,
                    color         = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 1.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                style      = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(18.dp))

            val toolzTime      = stats.filter { it.category == AppCategory.TOOLZ }.sumOf { it.usageTimeMillis }
            val distractionTime = stats.filter { it.category == AppCategory.DISTRACTION }.sumOf { it.usageTimeMillis }

            // Stacked bar
            if (totalTime > 0) {
                val toolzFraction      = (toolzTime.toFloat() / totalTime).coerceIn(0f, 1f)
                val distractionFraction = (distractionTime.toFloat() / totalTime).coerceIn(0f, 1f)
                val otherFraction       = (1f - toolzFraction - distractionFraction).coerceAtLeast(0f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                ) {
                    if (toolzFraction > 0f) {
                        Box(Modifier.fillMaxHeight().weight(toolzFraction).background(MaterialTheme.colorScheme.primary))
                    }
                    if (distractionFraction > 0f) {
                        Box(Modifier.fillMaxHeight().weight(distractionFraction).background(MaterialTheme.colorScheme.error))
                    }
                    if (otherFraction > 0f) {
                        Box(Modifier.fillMaxHeight().weight(otherFraction).background(MaterialTheme.colorScheme.outlineVariant.copy(0.4f)))
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LegendItem("Productive", MaterialTheme.colorScheme.primary)
                LegendItem("Distraction", MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
) {
    val vibrationManager = LocalVibrationManager.current
    val initialMinutes   = app.limitMillis?.div(60_000) ?: 0L
    var selectedHours    by remember(app.packageName, initialMinutes) { mutableStateOf((initialMinutes / 60).toInt()) }
    var selectedMins     by remember(app.packageName, initialMinutes) { mutableStateOf((initialMinutes % 60).toInt()) }
    var selectedCategory by remember(app.packageName, app.category) { mutableStateOf(app.category) }
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
            // App header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    modifier = Modifier.size(60.dp),
                    shape    = RoundedCornerShape(18.dp),
                    color    = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    AppIcon(packageName = app.packageName, modifier = Modifier.padding(10.dp))
                }
                Column {
                    Text(app.appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(28.dp))

            // Classification
            Text("CLASSIFICATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(true to "Productive", false to "Distraction").forEach { (isProd, label) ->
                    val isSelected  = isCurrentlyProductive == isProd
                    val activeColor = if (isProd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Surface(
                        modifier = Modifier.weight(1f).height(56.dp).bouncyClick {
                            vibrationManager?.vibrateTick()
                            selectedCategory = if (isProd) AppCategory.TOOLZ else AppCategory.DISTRACTION
                            onUpdateCategory(isProd)
                        },
                        shape    = RoundedCornerShape(18.dp),
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
                                    fontWeight = FontWeight.Bold,
                                    color      = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Quick limit chips
            Text("QUICK LIMITS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
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
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
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
                        Text("RESET", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Custom duration picker
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("CUSTOM DURATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                if (selectedHours == 0 && selectedMins == 0) {
                    Text("No Limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                shape    = RoundedCornerShape(24.dp),
                color    = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Selection highlight
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
                        Text(":", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(0.4f))
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
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape    = RoundedCornerShape(18.dp),
            ) {
                Text("APPLY SETTINGS", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
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
                    if (isSelected) 1.2f else 0.75f,
                    spring(Spring.DampingRatioMediumBouncy),
                    label = "picker_scale",
                )
                val itemAlpha  by animateFloatAsState(
                    if (isSelected) 1f else 0.3f,
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
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
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
//  Weekly Detailed Bottom Sheet
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
                "DAILY BREAKDOWN",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
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
                                stat.date.uppercase(), 
                                style = MaterialTheme.typography.labelMedium, 
                                fontWeight = FontWeight.Black, 
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if(totalH > 0) "${totalH}h ${totalM}m" else "${totalM}m",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black
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
                                            fontWeight = FontWeight.Bold, 
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
//  Screen Tips Bottom Sheet
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTipsSheet(viewModel: FocusFlowViewModel, onDismiss: () -> Unit) {
    val tips by viewModel.screenTips.collectAsState()
    val isLoading by viewModel.isLoadingTips.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.generateScreenTips()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        shape            = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp).navigationBarsPadding()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
                        "AI FOCUS INSIGHTS", 
                        style = MaterialTheme.typography.labelLarge, 
                        fontWeight = FontWeight.Black, 
                        color = MaterialTheme.colorScheme.primary, 
                        letterSpacing = 1.sp
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            if (isLoading) {
                Column(Modifier.fillMaxWidth().height(200.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Generating custom tips...", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    Text(
                        tips ?: "No tips available at the moment. Please try again later.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 26.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }
    }
}


