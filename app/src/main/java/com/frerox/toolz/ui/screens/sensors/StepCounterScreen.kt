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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.steps.StepEntry
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.theme.toolzBackground
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun StepCounterScreen(
    viewModel: StepCounterViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    
    val activityPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.ACTIVITY_RECOGNITION)
    } else {
        null
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color.Transparent).statusBarsPadding()) {
                CenterAlignedTopAppBar(
                    title = {
                        @Suppress("DEPRECATION")
                        Text(
                            text = "FITNESS TRACKER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .padding(8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { /* History action */ },
                            modifier = Modifier
                                .padding(8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = "History")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val hasActivityPermission = activityPermissionState?.status?.isGranted ?: true
        
        Box(modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .padding(top = padding.calculateTopPadding())
        ) {
            when {
                !state.isEnabledInSettings -> {
                    DisabledInSettingsState(onEnable = { viewModel.toggleStepCounter(true) })
                }
                !hasActivityPermission -> {
                    PermissionDeniedState { activityPermissionState?.launchPermissionRequest() }
                }
                !state.isSensorPresent -> {
                    NoSensorState()
                }
                else -> {
                    StepContent(state)
                }
            }
        }
    }
}

@Composable
fun DisabledInSettingsState(onEnable: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(140.dp),
            shape = RoundedCornerShape(48.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.DirectionsRun,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            "TRACKING DISABLED",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            letterSpacing = (-1).sp
        )
        Text(
            "Step counter is currently disabled. Enable it to start tracking your daily progress and reach your goals.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp, bottom = 40.dp)
        )
        Button(
            onClick = onEnable,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .bouncyClick {},
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("ENABLE TRACKER", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun StepContent(state: StepState) {
    val performanceMode = LocalPerformanceMode.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges(top = 24.dp, bottom = 24.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }
            AnimatedVisibility(
                visible = visible, 
                enter = if (performanceMode) fadeIn() else (fadeIn() + expandVertically())
            ) {
                StepProgressCard(state.steps, state.goal)
            }
        }

        item {
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(100); visible = true }
            AnimatedVisibility(
                visible = visible, 
                enter = if (performanceMode) fadeIn() else (fadeIn() + slideInVertically { it / 2 })
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "CALORIES",
                        value = "${(state.steps * 0.04).toInt()}",
                        unit = "KCAL",
                        icon = Icons.Rounded.Whatshot,
                        color = Color(0xFFFF5722)
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "DISTANCE",
                        value = String.format("%.2f", state.steps * 0.000762),
                        unit = "KM",
                        icon = Icons.Rounded.Route,
                        color = Color(0xFF2196F3)
                    )
                }
            }
        }

        item {
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(200); visible = true }
            AnimatedVisibility(
                visible = visible, 
                enter = if (performanceMode) fadeIn() else (fadeIn() + slideInVertically { it / 2 })
            ) {
                WeeklyHistoryCard(state.weeklyHistory, state.goal)
            }
        }
        
        item {
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(300); visible = true }
            AnimatedVisibility(
                visible = visible, 
                enter = if (performanceMode) fadeIn() else (fadeIn() + slideInVertically { it / 2 })
            ) {
                DailyGoalSection(state.goal)
            }
        }
        
        item {
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun StepProgressCard(steps: Int, goal: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(48.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
                val progress = (steps.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(1500, easing = FastOutSlowInEasing),
                    label = "Step Progress"
                )
                
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 24.dp,
                    strokeCap = StrokeCap.Round,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                )
                
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 24.dp,
                    strokeCap = StrokeCap.Round,
                    color = MaterialTheme.colorScheme.primary,
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = steps.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 60.sp,
                            letterSpacing = (-2).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    @Suppress("DEPRECATION")
                    Text(
                        text = "STEPS TODAY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
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
                    @Suppress("DEPRECATION")
                    Text("GOAL PROGRESS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline)
                    Text("${(steps.toFloat() / goal.toFloat() * 100).toInt()}% COMPLETED", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    @Suppress("DEPRECATION")
                    Text(
                        "TARGET: $goal",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    color: Color
) {
    Surface(
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(32.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                }
            }
            Column {
                Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                @Suppress("DEPRECATION")
                Text(text = "$unit $title", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
fun WeeklyHistoryCard(history: List<StepEntry>, goal: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(40.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                @Suppress("DEPRECATION")
                Text(
                    "WEEKLY ACTIVITY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )
                Icon(
                    Icons.Rounded.BarChart,
                    null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val calendar = Calendar.getInstance()
                val days = (0..6).map { i ->
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -i)
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                }.reversed()

                days.forEachIndexed { index, dateStr ->
                    val entry = history.find { dateStr == it.date }
                    val steps = entry?.steps ?: 0
                    val progress = (steps.toFloat() / goal.toFloat()).coerceIn(0.05f, 1f)
                    
                    val animatedHeight by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                        label = "BarHeight"
                    )

                    val dayName = try {
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
                        SimpleDateFormat("EEE", Locale.getDefault()).format(date!!).uppercase()
                    } catch (e: Exception) {
                        dateStr.takeLast(2)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .fillMaxHeight(animatedHeight)
                                .clip(CircleShape)
                                .background(
                                    if (steps >= goal) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        @Suppress("DEPRECATION")
                        Text(
                            text = dayName.first().toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = if (index == 6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyGoalSection(goal: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth().bouncyClick {},
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFC107).copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = Color(0xFFFFC107))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    @Suppress("DEPRECATION")
                    Text("DAILY GOAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline)
                    Text("$goal STEPS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                }
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun PermissionDeniedState(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(80.dp).alpha(0.2f), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))
        Text("PERMISSION REQUIRED", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(
            "Tracking your activity requires permission to use physical sensors. This data stays private on your device.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp, bottom = 40.dp)
        )
        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().height(64.dp).bouncyClick {},
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("GRANT PERMISSION", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun NoSensorState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(80.dp).alpha(0.2f), tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(32.dp))
        Text("SENSOR NOT FOUND", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(
            "Unfortunately, your device does not support the required hardware for step counting.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
