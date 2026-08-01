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

package com.frerox.toolz.ui.screens.sensors

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.steps.StepEntry
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StepCounterScreen(
    viewModel: StepCounterViewModel,
    onBack: () -> Unit,
    onNavigateToTrends: () -> Unit,
    onNavigateToAiAssistant: (Int) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val vibrationManager = LocalVibrationManager.current
    var showSettings by remember { mutableStateOf(false) }
    var showChatSheet by remember { mutableStateOf(false) }
    var showDebugDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showDebugDialog) {
        StepEngineDebugDialog(
            onDismissRequest = { showDebugDialog = false },
            logs = state.debugLogs,
            motionStatus = state.motionStatus,
            engineMode = state.stepEngineMode,
            onToggleEngine = { viewModel.updateStepEngineMode(if (it == "STRICT") "SIMPLE" else "STRICT") },
            onResetEngine = { viewModel.hardResetEngine() }
        )
    }
    
    val activityPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.ACTIVITY_RECOGNITION)
    } else {
        null
    }

    if (showSettings) {
        StepCounterSettingsBottomSheet(
            onDismissRequest = { showSettings = false },
            stepGoal = state.goal,
            onStepGoalChange = { viewModel.updateGoal(it) },
            retention = state.retention,
            onRetentionChange = { viewModel.updateRetention(it) },
            aiEnabled = state.aiEnabled,
            onAiEnabledChange = { viewModel.updateAiEnabled(it) },
            aiProvider = state.aiProvider,
            onAiProviderChange = { viewModel.updateAiProvider(it) },
            aiModel = state.aiModel,
            onAiModelChange = { viewModel.updateAiModel(it) },
            aiTone = state.aiTone,
            onAiToneChange = { viewModel.updateAiTone(it) },
            aiMood = state.aiMood,
            onAiMoodChange = { viewModel.updateAiMood(it) },
            aiStyle = state.aiStyle,
            onAiStyleChange = { viewModel.updateAiStyle(it) },
            stepLength = state.stepLength,
            onStepLengthChange = { viewModel.updateStepLength(it) },
            caloriesPer1k = state.caloriesPer1k,
            onCaloriesPer1kChange = { viewModel.updateCaloriesPer1k(it) },
            availableProviders = state.availableProviders,
            measurementSystem = state.measurementSystem,
            onMeasurementSystemChange = { viewModel.updateMeasurementSystem(it) },
            useGps = state.useGps,
            onUseGpsChange = { viewModel.toggleUseGps(it) },
            notificationsEnabled = state.stepNotifications,
            onNotificationsEnabledChange = { viewModel.toggleNotifications(it) },
            batterySaveEnabled = state.batterySave,
            onBatterySaveChange = { viewModel.toggleBatterySave(it) },
            stepSensitivity = state.stepSensitivity,
            onStepSensitivityChange = { viewModel.updateStepSensitivity(it) },
            stepEngineMode = state.stepEngineMode,
            onStepEngineModeChange = { viewModel.updateStepEngineMode(it) }
        )
    }

    if (showChatSheet) {
        val scope = rememberCoroutineScope()
        StepAiChatBottomSheet(
            onDismissRequest = { showChatSheet = false },
            state = state,
            onSendMessage = { viewModel.sendMessage(it) },
            onContinueInAssistant = {
                scope.launch {
                    val chatId = viewModel.getCoachChatId()
                    onNavigateToAiAssistant(chatId)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = {
                    Text(
                        text = "FITNESS",
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    vibrationManager?.vibrate(100L)
                                    showDebugDialog = true
                                }
                            )
                        }
                    )
                },
                subtitle = { Text("Step Tracker") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            showSettings = true
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            ToolzHorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.padding(bottom = 16.dp),
                content = {
                    FilledIconButton(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            onNavigateToTrends()
                        },
                        modifier = Modifier.size(48.dp),
                        shape = SmallExpressiveShape
                    ) {
                        Icon(Icons.Rounded.BarChart, contentDescription = "Trends")
                    }
                },
                trailingContent = {
                    if (state.aiEnabled && !state.isOffline) {
                        clickableItem(
                            onClick = { 
                                vibrationManager?.vibrateClick()
                                showChatSheet = true
                            },
                            icon = { 
                                BadgedBox(
                                    badge = {
                                        if (state.aiChatHistory.isNotEmpty()) {
                                            Badge(containerColor = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, null)
                                }
                            },
                            label = "COACH"
                        )
                    }
                    clickableItem(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            viewModel.toggleStepCounter(!state.isEnabledInSettings)
                        },
                        icon = { Icon(if (state.isEnabledInSettings) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null) },
                        label = if (state.isEnabledInSettings) "PAUSE" else "RESUME"
                    )
                }
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        val hasActivityPermission = activityPermissionState?.status?.isGranted ?: true
        
        Box(modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .padding(top = padding.calculateTopPadding())
        ) {
            when {
                !state.isEnabledInSettings -> {
                    DisabledInSettingsView { 
                        vibrationManager?.vibrateClick()
                        viewModel.toggleStepCounter(true) 
                    }
                }
                !hasActivityPermission -> {
                    PermissionDeniedView { 
                        vibrationManager?.vibrateClick()
                        activityPermissionState?.launchPermissionRequest() 
                    }
                }
                !state.isSensorPresent -> {
                    NoSensorView()
                }
                else -> {
                    StepContentLayout(state = state, onShowChat = { showChatSheet = true }, viewModel = viewModel, onNavigateToTrends = onNavigateToTrends)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepEngineDebugDialog(
    onDismissRequest: () -> Unit,
    logs: List<String>,
    motionStatus: String,
    engineMode: String,
    onToggleEngine: (String) -> Unit,
    onResetEngine: () -> Unit
) {
    val context = LocalContext.current
    val isStrict = engineMode == "STRICT"
    var logResetKey by remember { mutableIntStateOf(0) }

    // Derive live stats from the last few log lines
    val lastLog = logs.lastOrNull() ?: ""
    val cadenceBpm: String = run {
        val match = Regex("cadence=(\\d+)bpm").find(lastLog)
        match?.groupValues?.getOrNull(1)?.let { "$it bpm" } ?: "—"
    }
    val pendingSteps: String = run {
        val match = Regex("pending=(\\d+)").find(lastLog)
        match?.groupValues?.getOrNull(1) ?: "0"
    }
    val isGyroFrozen = logs.takeLast(10).any { it.contains("GYRO FREEZE") || it.contains("gyro", ignoreCase = true) }
    val candidateCount: String = run {
        val match = Regex("candidate=(\\d+)").find(lastLog)
        match?.groupValues?.getOrNull(1) ?: "—"
    }

    // Status color helper
    fun statusColor(status: String) = when (status) {
        "WALKING", "ACTIVE"   -> Color(0xFF4CAF50)
        "CANDIDATE"           -> Color(0xFF64B5F6)
        "IDLE"                -> Color(0xFFFF9800)
        "SUSPENDED"           -> Color(0xFFE57373)
        else                  -> Color.Gray
    }

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(LargeExpressiveShape)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = LargeExpressiveShape
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ENGINE DEBUG",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (isStrict) "STRICT — Rhythmic Validation" else "SIMPLE — Peak Detection",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isStrict) Color(0xFF4FC3F7) else Color(0xFF81C784)
                        )
                    }
                    // Live motion state badge
                    Surface(
                        color = statusColor(motionStatus).copy(alpha = 0.15f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, statusColor(motionStatus))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(statusColor(motionStatus), CircleShape)
                            )
                            Text(
                                text = motionStatus,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // ── FSM State Track (STRICT) / Status strip (SIMPLE) ──────────
                if (isStrict) {
                    val states = listOf("IDLE", "CANDIDATE", "WALKING", "SUSPENDED")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        states.forEach { s ->
                            val isActive = motionStatus == s
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = SmallExpressiveShape,
                                color = if (isActive) statusColor(s).copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = s,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isActive) FontWeight.Black else FontWeight.Normal,
                                    color = if (isActive) statusColor(s) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 2.dp)
                                )
                            }
                        }
                    }
                } else {
                    // SIMPLE — show active indicator
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallExpressiveShape,
                        color = (if (motionStatus == "SUSPENDED") Color(0xFFE57373) else Color(0xFF81C784)).copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (motionStatus == "SUSPENDED") Icons.Rounded.PauseCircle else Icons.Rounded.RadioButtonChecked,
                                null,
                                tint = if (motionStatus == "SUSPENDED") Color(0xFFE57373) else Color(0xFF81C784),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (motionStatus == "SUSPENDED") "Engine suspended — gyro freeze or GPS block"
                                       else "Engine active — counting accelerometer peaks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ── Live Stats Row ────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatChip(label = "CADENCE", value = cadenceBpm, modifier = Modifier.weight(1f))
                    if (isStrict) {
                        StatChip(label = "PENDING", value = pendingSteps, modifier = Modifier.weight(1f))
                        StatChip(label = "CANDIDATE", value = candidateCount, modifier = Modifier.weight(1f))
                    } else {
                        StatChip(label = "GYRO", value = if (isGyroFrozen) "FROZEN" else "OK",
                            valueColor = if (isGyroFrozen) Color(0xFFE57373) else Color(0xFF81C784),
                            modifier = Modifier.weight(1f))
                    }
                }

                // ── Log Section ───────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "LIVE TELEMETRY",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.outline
                        )
                        // Copy all logs button
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Engine Logs", logs.joinToString("\n"))
                                clipboard.setPrimaryClip(clip)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy logs",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        color = Color(0xFF0E0E0E),
                        shape = SmallExpressiveShape,
                        border = BorderStroke(1.dp, Color.DarkGray.copy(alpha = 0.4f))
                    ) {
                        AnimatedContent(
                            targetState = logResetKey,
                            label = "logResetAnimation",
                            transitionSpec = {
                                slideInVertically { height -> height } + fadeIn() togetherWith
                                slideOutVertically { height -> -height } + fadeOut()
                            }
                        ) { _ ->
                            val scrollState = rememberScrollState()
                            LaunchedEffect(logs.size) {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                            Box(modifier = Modifier.padding(10.dp)) {
                                if (logs.isEmpty()) {
                                    Text(
                                        "No telemetry yet. Start walking...",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp
                                        ),
                                        color = Color.DarkGray,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(scrollState),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        logs.forEach { log ->
                                            Text(
                                                text = log,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    lineHeight = 12.sp
                                                ),
                                                color = when {
                                                    log.contains("CONFIRMED") || log.contains("Emitted")
                                                        || log.contains("OS FLUSH") || log.contains("OS SAFETY NET")
                                                        || log.contains("Accel confirmed") -> Color(0xFF81C784)
                                                    log.contains("Rising") || log.contains("RISING")
                                                        || log.contains("CANDIDATE") || log.contains("OS BUFFERED") -> Color(0xFF64B5F6)
                                                    log.contains("Step #") || log.contains("SIMPLE: Step") -> Color(0xFF4FC3F7)
                                                    log.contains("RESET") || log.contains("TIMEOUT")
                                                        || log.contains("Safety net") || log.contains("safety net") -> Color(0xFFFFB74D)
                                                    log.contains("REJECTED") || log.contains("FAIL")
                                                        || log.contains("SUSPENDED") || log.contains("OS DROPPED") -> Color(0xFFE57373)
                                                    log.contains("GPS") || log.contains("vehicle")
                                                        || log.contains("driving") -> Color(0xFFCE93D8)
                                                    log.contains("IDLE") || log.contains("OS QUEUED")
                                                        || log.contains("Initializ") -> Color(0xFF8D8D8D)
                                                    else -> Color(0xFFBDBDBD)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Action Row ────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ToolzExpressiveButton(
                        onClick = {
                            logResetKey++
                            onResetEngine()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("RESET", fontWeight = FontWeight.Black)
                    }
                    ToolzExpressiveButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("CLOSE", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color(0xFF81C784)
) {
    Surface(
        modifier = modifier,
        shape = SmallExpressiveShape,
        color = Color(0xFF1A1A1A)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp
                ),
                color = Color(0xFF6D6D6D)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                fontWeight = FontWeight.Black,
                color = valueColor
            )
        }
    }
}

@Composable
private fun StepContentLayout(state: StepState, onShowChat: () -> Unit, viewModel: StepCounterViewModel, onNavigateToTrends: () -> Unit) {
    val performanceMode = LocalPerformanceMode.current
    val selectedChartEntry by viewModel.selectedChartEntry.collectAsStateWithLifecycle()
    val trendRange by viewModel.trendRange.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 24.dp, bottom = 24.dp)),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            StaggeredEntrance(index = 0) {
                StepProgressRingSection(
                    steps = state.steps,
                    goal = state.goal,
                    motionStatus = state.motionStatus
                )
            }
        }

        if (state.aiEnabled && !state.isOffline) {
            item {
                StaggeredEntrance(index = 1) {
                    AiFitnessAgentPill(state, onChat = onShowChat)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StaggeredEntrance(index = 2, modifier = Modifier.weight(1f)) {
                    ActivityStatCard(
                        title = "CALORIES",
                        value = "${state.calories}",
                        unit = "KCAL",
                        icon = Icons.Rounded.Whatshot,
                        color = Color(0xFFFF5722)
                    )
                }
                StaggeredEntrance(index = 3, modifier = Modifier.weight(1f)) {
                    ActivityStatCard(
                        title = "DISTANCE",
                        value = String.format(Locale.US, "%.2f", state.distanceDisplay),
                        unit = state.distanceUnit.uppercase(),
                        icon = Icons.Rounded.Route,
                        color = Color(0xFF2196F3)
                    )
                }
            }
        }

        item {
            StaggeredEntrance(index = 4) {
                TrendsSection(
                    state = state,
                    selectedEntry = selectedChartEntry,
                    onDaySelected = { viewModel.onChartBarSelected(it) },
                    onNavigateToTrends = onNavigateToTrends
                )
            }
        }

        item {
            StaggeredEntrance(index = 5) {
                MilestonesSection(state)
            }
        }
        
        item {
            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun StepProgressRingSection(
    steps: Int,
    goal: Int,
    motionStatus: String
) {
    val vibrationManager = LocalVibrationManager.current
    
    val animatedSteps by animateIntAsState(
        targetValue = steps,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "AnimatedSteps"
    )

    val progress = (animatedSteps.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "GoalProgress"
    )

    ExpressiveCard(
        onClick = { vibrationManager?.vibrateTick() },
        modifier = Modifier.fillMaxWidth(),
        shape = LargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                // Background Glow
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .graphicsLayer {
                            alpha = 0.15f * animatedProgress
                            scaleX = 1.2f + (0.2f * animatedProgress)
                            scaleY = 1.2f + (0.2f * animatedProgress)
                        }
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )

                ExpressiveCircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round
                )
                
                // Live detection glow — only when active
                val isActive = motionStatus == "WALKING" || motionStatus == "ACTIVE" || motionStatus == "CANDIDATE"
                val showGlow = remember { mutableStateOf(false) }
                
                LaunchedEffect(isActive) {
                    if (isActive) {
                        showGlow.value = true
                    } else {
                        delay(3000)
                        showGlow.value = false
                    }
                }

                val glowAlpha by animateFloatAsState(
                    targetValue = if (showGlow.value) 1f else 0f,
                    animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
                    label = "GlowAlpha"
                )
                
                if (glowAlpha > 0f) {
                    val infiniteTransition = rememberInfiniteTransition(label = "GlowPulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "PulseScale"
                    )

                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .graphicsLayer {
                                alpha = 0.2f * glowAlpha
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(MaterialTheme.colorScheme.primary, Color.Transparent)
                                ),
                                shape = CircleShape
                            )
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%,d".format(animatedSteps),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = (-3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "STEPS TODAY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("GOAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline)
                    Text("%,d".format(goal), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                
                val remaining = (goal - steps).coerceAtLeast(0)
                Column(horizontalAlignment = Alignment.End) {
                    Text("REMAINING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline)
                    Text("%,d".format(remaining), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrendsSection(
    state: StepState,
    selectedEntry: com.frerox.toolz.data.steps.StepEntry?,
    onDaySelected: (com.frerox.toolz.data.steps.StepEntry?) -> Unit,
    onNavigateToTrends: () -> Unit
) {
    var chartMode by remember { mutableStateOf(0) }
    val vibrationManager = LocalVibrationManager.current
    
    ExpressiveCard(
        onClick = { 
            vibrationManager?.vibrateClick()
            onNavigateToTrends()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        Color.Transparent
                    )
                )
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "ACTIVITY TRENDS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                        Text(
                            "Your progress over time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ToolzExpressiveButton(
                            onClick = { chartMode = (chartMode + 1) % 3 },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            val icon = when (chartMode) {
                                0 -> Icons.Rounded.BarChart
                                1 -> Icons.Rounded.Timeline
                                else -> Icons.AutoMirrored.Rounded.ShowChart
                            }
                            val text = when (chartMode) {
                                0 -> "BARS"
                                1 -> "LINE"
                                else -> "BOTH"
                            }
                            Icon(icon, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                InteractiveFitnessChart(
                    history = state.fullHistory,
                    goal = state.goal,
                    caloriesPer1k = state.caloriesPer1k,
                    stepLengthCm = state.stepLength,
                    chartMode = chartMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    onDaySelected = onDaySelected
                )
                // Selected-day detail strip — compact inline
            androidx.compose.animation.AnimatedVisibility(
                visible = selectedEntry != null,
                enter = androidx.compose.animation.expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) +
                        androidx.compose.animation.fadeIn(),
                exit  = androidx.compose.animation.shrinkVertically() +
                        androidx.compose.animation.fadeOut()
            ) {
                selectedEntry?.let { entry ->
                    val pct = if (state.goal > 0) (entry.steps * 100 / state.goal) else 0
                    val cal = com.frerox.toolz.util.StepTrackerUtils.calculateCalories(
                        entry.steps, state.caloriesPer1k
                    )
                    val goalMet = entry.steps >= state.goal
                    val dayLabel = try {
                        val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(entry.date)
                        java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault()).format(d!!)
                    } catch (e: Exception) { entry.date }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                dayLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "$pct% of goal  •  $cal kcal",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            "%,d".format(entry.steps),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = if (goalMet) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.OpenInFull, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                Spacer(Modifier.width(4.dp))
                Text(
                    "Tap to open full trends",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
            }
        }
    }
}
}

@Composable
private fun MilestonesSection(state: StepState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "MILESTONES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MilestoneCard(
                modifier = Modifier.weight(1f),
                title = "BEST DAY",
                value = "${state.bestDaySteps}",
                icon = Icons.Rounded.EmojiEvents,
                color = Color(0xFFFFD700)
            )
            MilestoneCard(
                modifier = Modifier.weight(1f),
                title = "AVERAGE",
                value = "${state.averageSteps}",
                icon = Icons.Rounded.Timeline,
                color = Color(0xFF4CAF50)
            )
        }
        
        MilestoneCard(
            modifier = Modifier.fillMaxWidth(),
            title = "ALL-TIME TOTAL",
            value = "${state.allTimeTotal}",
            icon = Icons.Rounded.Functions,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun MilestoneCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    ExpressiveCard(
        onClick = {},
        modifier = modifier.height(110.dp),
        shape = SquircleShape,
        containerColor = color.copy(alpha = 0.08f),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Icon(icon, null, tint = color.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
                Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = color.copy(alpha = 0.7f))
            }
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun AiFitnessAgentPill(state: StepState, onChat: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick = { 
            vibrationManager?.vibrateClick()
            onChat() 
        },
        modifier = Modifier.fillMaxWidth(),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        elevation = 0.dp
    ) {
        val gradientBrush = androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                Color.Transparent
            )
        )
        Box(modifier = Modifier.fillMaxWidth().background(gradientBrush)) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (state.isAiLoading) {
                            ExpressiveTypingDots(color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    val rawMsg = state.aiChatHistory.lastOrNull { !it.isUser }?.text
                        ?: state.aiAdvice ?: "How is your training going today?"
                    val latestMsg = rawMsg.replace(Regex("[*#_~]"), "")
                    
                    Text(
                        text = latestMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Chat with your AI Fitness Coach",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
                
                Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ActivityStatCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    color: Color
) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick = { vibrationManager?.vibrateTick() },
        modifier = Modifier.height(140.dp),
        shape = BouncyShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        elevation = 0.dp
    ) {
        val gradientBrush = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(color.copy(alpha = 0.25f), Color.Transparent),
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset(0f, 400f)
        )
        Box(modifier = Modifier.fillMaxSize().background(gradientBrush)) {
            Column(modifier = Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Surface(
                    modifier = Modifier.size(44.dp).align(Alignment.End),
                    shape = CircleShape,
                    color = color.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    }
                }
                Column {
                    Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text(text = "$unit $title", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = color, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}

@Composable
private fun DisabledInSettingsView(onEnable: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        ExpressiveCard(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            shape = LargeExpressiveShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            elevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PauseCircleFilled, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("PAUSED", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Your activity tracking is currently paused. Resume to continue recording steps.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                ToolzExpressiveButton(
                    onClick = onEnable,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("RESUME TRACKING", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun PermissionDeniedView(onGrant: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(100.dp).alpha(0.15f), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(40.dp))
        Text("ACCESS REQUIRED", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(
            "Motion sensors are required to quantify your daily movement. This data is stored locally and securely.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp, bottom = 48.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        ToolzExpressiveButton(onClick = onGrant, modifier = Modifier.fillMaxWidth().height(72.dp), shape = BouncyShape) {
            Text("GRANT ACCESS", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun NoSensorView() {
    Column(modifier = Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.SentimentVeryDissatisfied, null, modifier = Modifier.size(100.dp).alpha(0.15f), tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(40.dp))
        Text("HARDWARE MISSING", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(
            "This device does not appear to have the physical step counting hardware required for this feature.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
