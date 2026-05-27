package com.frerox.toolz.ui.screens.sensors

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.steps.StepEntry
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StepCounterScreen(
    viewModel: StepCounterViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val vibrationManager = LocalVibrationManager.current
    
    val activityPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.ACTIVITY_RECOGNITION)
    } else {
        null
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "TRACKER",
                subtitle = "Active Daily Progress",
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
                    ExpressiveFabMenu(
                        items = listOf(
                            Triple("Step Goal", Icons.Rounded.EmojiEvents, { vibrationManager?.vibrateClick() }),
                            Triple("History", Icons.Rounded.History, { vibrationManager?.vibrateClick() }),
                            Triple("Settings", Icons.Rounded.Settings, { vibrationManager?.vibrateClick() })
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
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
                        },
                        modifier = Modifier.size(48.dp),
                        shape = SmallExpressiveShape
                    ) {
                        Icon(Icons.Rounded.DirectionsRun, contentDescription = "Sync")
                    }
                },
                trailingContent = {
                    clickableItem(
                        onClick = { vibrationManager?.vibrateClick() },
                        icon = { Icon(Icons.Rounded.BarChart, null) },
                        label = "STATS"
                    )
                    clickableItem(
                        onClick = { vibrationManager?.vibrateClick() },
                        icon = { Icon(Icons.Rounded.Favorite, null) },
                        label = "HEALTH"
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
                    StepContentLayout(state)
                }
            }
        }
    }
}

@Composable
private fun StepContentLayout(state: StepState) {
    val performanceMode = LocalPerformanceMode.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 24.dp, bottom = 24.dp)),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            StaggeredEntrance(index = 0) {
                StepProgressCardExpressive(state.steps, state.goal)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StaggeredEntrance(index = 1, modifier = Modifier.weight(1f)) {
                    ActivityStatCard(
                        title = "CALORIES",
                        value = "${(state.steps * 0.045).toInt()}",
                        unit = "KCAL",
                        icon = Icons.Rounded.Whatshot,
                        color = Color(0xFFFF5722)
                    )
                }
                StaggeredEntrance(index = 2, modifier = Modifier.weight(1f)) {
                    ActivityStatCard(
                        title = "DISTANCE",
                        value = String.format("%.2f", state.steps * 0.00078),
                        unit = "KM",
                        icon = Icons.Rounded.Route,
                        color = Color(0xFF2196F3)
                    )
                }
            }
        }

        item {
            StaggeredEntrance(index = 3) {
                ActivityHistoryCardExpressive(state.weeklyHistory, state.goal)
            }
        }
        
        item {
            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun StepProgressCardExpressive(steps: Int, goal: Int) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick = { vibrationManager?.vibrateTick() },
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
                val progress = (steps.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                    label = "GoalProgress"
                )
                
                // Official Circular Wavy Progress Indicator
                ToolzWavyCircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = steps.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 80.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = (-4).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "STEPS TODAY",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("PROGRESS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline, letterSpacing = 1.sp)
                    Text("${(steps.toFloat() / goal.toFloat() * 100).toInt()}% OF DAILY GOAL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = BouncyShape
                ) {
                    Text(
                        "GOAL: $goal",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
        containerColor = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = SmallExpressiveShape,
                color = color.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                }
            }
            Column {
                Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text(text = "$unit $title", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = color.copy(alpha = 0.8f), letterSpacing = 0.5.sp)
            }
        }
    }
}

@Composable
private fun ActivityHistoryCardExpressive(history: List<StepEntry>, goal: Int) {
    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                @Suppress("DEPRECATION")
                Text(
                    "WEEKLY PERFORMANCE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )
                Icon(
                    Icons.Rounded.TrendingUp,
                    null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(40.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val days = (0..6).map { i ->
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -i)
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                }.reversed()

                days.forEachIndexed { index, dateStr ->
                    val entry = history.find { dateStr == it.date }
                    val steps = entry?.steps ?: 0
                    val progress = (steps.toFloat() / goal.toFloat()).coerceIn(0.05f, 1.2f)
                    
                    val animatedHeight by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "BarHeight"
                    )

                    val dayName = try {
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
                        SimpleDateFormat("EEE", Locale.getDefault()).format(date!!).uppercase()
                    } catch (e: Exception) { "D" }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .fillMaxHeight(animatedHeight.coerceAtMost(1f))
                                .clip(CircleShape)
                                .background(
                                    if (steps >= goal) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        @Suppress("DEPRECATION")
                        Text(
                            text = dayName.take(1),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = if (index == 6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisabledInSettingsView(onEnable: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(160.dp),
            shape = LargeExpressiveShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.DirectionsRun, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
        Text("TRACKING PAUSED", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Text(
            "The activity sensor is currently inactive. Re-enable to resume your fitness journey.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp, bottom = 48.dp)
        )
        ToolzExpressiveButton(onClick = onEnable, modifier = Modifier.fillMaxWidth().height(72.dp), shape = BouncyShape) {
            Text("RESUME TRACKING", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
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
