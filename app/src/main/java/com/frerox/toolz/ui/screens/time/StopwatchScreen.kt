package com.frerox.toolz.ui.screens.time

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen(
    viewModel: StopwatchViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stopwatch", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Animated ring
                val infiniteTransition = rememberInfiniteTransition(label = "StopwatchRing")
                val rotation by infiniteTransition.animateFloat(
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
                        color = primaryColor.copy(alpha = 0.1f),
                        style = Stroke(width = 8.dp.toPx())
                    )
                    
                    if (state.isRunning) {
                        drawArc(
                            color = primaryColor,
                            startAngle = rotation - 90f,
                            sweepAngle = 60f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
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
                            letterSpacing = (-2).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    val lapTime = if (state.laps.isEmpty()) state.elapsedTime else state.elapsedTime - state.laps.first()
                    Text(
                        text = "Current Lap: ${formatTime(lapTime)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset Button
                OutlinedIconButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    border = IconButtonDefaults.outlinedIconButtonBorder(enabled = true)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset", modifier = Modifier.size(32.dp))
                }
                
                // Start/Stop Button
                LargeFloatingActionButton(
                    onClick = { viewModel.toggleStartStop() },
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    containerColor = if (state.isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isRunning) "Pause" else "Start",
                        modifier = Modifier.size(48.dp),
                        tint = if (state.isRunning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                    )
                }
                
                // Lap Button
                FilledTonalIconButton(
                    onClick = { viewModel.lap() },
                    modifier = Modifier.size(72.dp),
                    enabled = state.isRunning,
                    shape = CircleShape
                ) {
                    Icon(Icons.Rounded.Flag, contentDescription = "Lap", modifier = Modifier.size(32.dp))
                }
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                if (state.laps.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No laps recorded", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(state.laps) { index, lapTime ->
                            val duration = if (index == state.laps.size - 1) {
                                lapTime
                            } else {
                                lapTime - state.laps[index + 1]
                            }
                            LapItem(
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

@Composable
fun LapItem(lapNumber: Int, totalTime: Long, lapDuration: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "LAP $lapNumber",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = formatTime(lapDuration),
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
            )
        }
        Text(
            text = formatTime(totalTime),
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(timeMillis: Long): String {
    val totalSeconds = timeMillis / 1000
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    val millis = (timeMillis % 1000) / 10
    
    return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, millis)
}
