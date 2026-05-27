package com.frerox.toolz.ui.screens.time

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppBarRowScope.clickableItem(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean = true
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StopwatchScreen(
    viewModel: StopwatchViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current
    val primaryColor = MaterialTheme.colorScheme.primary

    // Energetic spring for the primary timer value
    val animatedTime by animateFloatAsState(
        targetValue = state.elapsedTime.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "StopwatchTime"
    )

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "CHRONOMETER",
                subtitle = "Precision Lap Timing",
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
                            Triple("Share Splits", Icons.Rounded.Share, { vibrationManager?.vibrateClick() }),
                            Triple("Export CSV", Icons.Rounded.FileDownload, { vibrationManager?.vibrateClick() }),
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
                            viewModel.toggleStartStop()
                        },
                        modifier = Modifier.size(56.dp),
                        shape = SmallExpressiveShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                trailingContent = {
                    clickableItem(
                        onClick = {
                            vibrationManager?.vibrateTick()
                            if (state.isRunning) viewModel.lap()
                        },
                        icon = { Icon(Icons.Rounded.Flag, null) },
                        label = "LAP",
                        enabled = state.isRunning
                    )
                    clickableItem(
                        onClick = {
                            vibrationManager?.vibrateLongClick()
                            viewModel.reset()
                        },
                        icon = { Icon(Icons.Rounded.Refresh, null) },
                        label = "RESET"
                    )
                }
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 120.dp))
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Main Chronometer Display in an organic Squircle Container
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    
                    // Liquid rotating ring for active timing
                    if (state.isRunning && !performanceMode) {
                        val infiniteTransition = rememberInfiniteTransition(label = "StopwatchSpin")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
                            label = "Spin"
                        )
                        
                        ToolzWavyCircularProgressIndicator(
                            progress = { 0.25f },
                            modifier = Modifier.size(340.dp).rotate(rotation),
                            color = primaryColor,
                            trackColor = Color.Transparent,
                            strokeCap = StrokeCap.Round
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(320.dp),
                            shape = SquircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        ) {}
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatTime(state.elapsedTime),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 72.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-4).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val lapTime = if (state.laps.isEmpty()) state.elapsedTime else state.elapsedTime - state.laps.first()
                        Surface(
                            color = primaryColor.copy(alpha = 0.15f),
                            shape = BouncyShape,
                            modifier = Modifier.padding(top = 24.dp),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = "DELTA: +${formatTime(lapTime)}",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                                color = primaryColor,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
                
                // Splits Index with Staggered Entrance
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = SmallExpressiveShape
                        ) {
                            Text(
                                "SPLITS INDEX", 
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall, 
                                fontWeight = FontWeight.Black, 
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp
                            )
                        }
                        if (state.laps.isNotEmpty()) {
                            Text(
                                "${state.laps.size} DATA POINTS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.outline,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    
                    if (state.laps.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Flag, null, modifier = Modifier.size(100.dp).alpha(0.1f), tint = primaryColor)
                                Spacer(Modifier.height(24.dp))
                                Text("AWAITING INITIALIZATION", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), letterSpacing = 1.5.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 140.dp)
                        ) {
                            itemsIndexed(state.laps) { index, lapTime ->
                                val duration = if (index == state.laps.size - 1) {
                                    lapTime
                                } else {
                                    lapTime - state.laps[index + 1]
                                }
                                StaggeredEntrance(index = index % 5) {
                                    LapCardExpressive(
                                        lapNumber = state.laps.size - index, 
                                        totalTime = lapTime,
                                        lapDuration = duration
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

@Composable
fun LapCardExpressive(lapNumber: Int, totalTime: Long, lapDuration: Long) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick = { vibrationManager?.vibrateTick() },
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = SmallExpressiveShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = String.format("%02d", lapNumber),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column {
                    Text(
                        text = "INTERVAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = formatTime(lapDuration),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "TOTAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = formatTime(totalTime),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun formatTime(timeMillis: Long): String {
    val totalSeconds = timeMillis / 1000
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    val millis = (timeMillis % 1000) / 10
    
    return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, millis)
}
