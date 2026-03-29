package com.frerox.toolz.ui.screens.time

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen(
    viewModel: StopwatchViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "CHRONOMETER",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            viewModel.reset() 
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .padding(top = padding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "StopwatchRing")
                    val rotation by if (performanceMode) remember { mutableFloatStateOf(0f) } else infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "Rotation"
                    )

                    val primaryColor = MaterialTheme.colorScheme.primary
                    
                    Canvas(modifier = Modifier.size(300.dp)) {
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.08f),
                            style = Stroke(width = 16.dp.toPx())
                        )
                        
                        if (state.isRunning && !performanceMode) {
                            drawArc(
                                color = primaryColor,
                                startAngle = rotation - 90f,
                                sweepAngle = 90f,
                                useCenter = false,
                                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatTime(state.elapsedTime),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 64.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-3).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val lapTime = if (state.laps.isEmpty()) state.elapsedTime else state.elapsedTime - state.laps.first()
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(top = 20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = "+ ${formatTime(lapTime)}",
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { 
                            vibrationManager?.vibrateLongClick()
                            viewModel.reset() 
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset", modifier = Modifier.size(32.dp))
                    }
                    
                    Surface(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            viewModel.toggleStartStop() 
                        },
                        modifier = Modifier
                            .size(100.dp)
                            .bouncyClick {},
                        shape = CircleShape,
                        color = if (state.isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                        shadowElevation = if (performanceMode) 0.dp else 12.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isRunning) "Pause" else "Start",
                                modifier = Modifier.size(48.dp),
                                tint = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = { 
                            vibrationManager?.vibrateTick()
                            if (state.isRunning) viewModel.lap() 
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                if (state.isRunning) MaterialTheme.colorScheme.secondaryContainer 
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                        enabled = state.isRunning
                    ) {
                        Icon(
                            Icons.Rounded.Flag, 
                            contentDescription = "Lap", 
                            modifier = Modifier.size(32.dp),
                            tint = if (state.isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
                
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            @Suppress("DEPRECATION")
                            Text(
                                "RECORDS INDEX", 
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall, 
                                fontWeight = FontWeight.Black, 
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp
                            )
                        }
                        if (state.laps.isNotEmpty()) {
                            @Suppress("DEPRECATION")
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
                                Icon(Icons.Rounded.Flag, null, modifier = Modifier.size(100.dp).alpha(0.1f), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(20.dp))
                                Text("AWAITING INITIALIZATION", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), letterSpacing = 1.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().then(if (performanceMode) Modifier else Modifier.fadingEdge(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)), 24.dp)),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 40.dp)
                        ) {
                            itemsIndexed(state.laps) { index, lapTime ->
                                val duration = if (index == state.laps.size - 1) {
                                    lapTime
                                } else {
                                    lapTime - state.laps[index + 1]
                                }
                                LapCard(
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

@Composable
fun LapCard(lapNumber: Int, totalTime: Long, lapDuration: Long) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        shape = RoundedCornerShape(36.dp),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = String.format("%02d", lapNumber),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
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
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "CUMULATIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = formatTime(totalTime),
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
