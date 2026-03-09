package com.frerox.toolz.ui.screens.time

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(
    viewModel: PomodoroViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val totalTime = state.mode.minutes * 60 * 1000L
    val animatedProgress by animateFloatAsState(
        targetValue = state.remainingTime.toFloat() / totalTime,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "Pomodoro Progress"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus Mode", fontWeight = FontWeight.ExtraBold) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = when (state.mode) {
                        PomodoroMode.WORK -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (state.mode == PomodoroMode.WORK) Icons.Rounded.CenterFocusStrong else Icons.Rounded.Coffee,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (state.mode == PomodoroMode.WORK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (state.mode) {
                                PomodoroMode.WORK -> "FOCUS SESSION"
                                PomodoroMode.SHORT_BREAK -> "SHORT BREAK"
                                PomodoroMode.LONG_BREAK -> "LONG BREAK"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (state.mode == PomodoroMode.WORK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Session #${state.sessionsCompleted + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(320.dp)
            ) {
                // Static background circle
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.LightGray.copy(alpha = 0.2f),
                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 16.dp,
                    strokeCap = StrokeCap.Round,
                    color = when(state.mode) {
                        PomodoroMode.WORK -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                    trackColor = Color.Transparent,
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatPomodoroTime(state.remainingTime),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 80.sp
                        )
                    )
                    if (state.isFinished && !state.isRunning) {
                        IconButton(onClick = { viewModel.stopRingtone() }) {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = "Stop Ringtone", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedIconButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset", modifier = Modifier.size(28.dp))
                }

                FilledTonalIconButton(
                    onClick = { 
                        if (state.isFinished) viewModel.stopRingtone()
                        viewModel.toggleStartStop() 
                    },
                    modifier = Modifier.size(96.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (state.isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isRunning) "Pause" else "Start",
                        modifier = Modifier.size(48.dp),
                        tint = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                FilledIconButton(
                    onClick = { viewModel.skip() },
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Skip", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

private fun formatPomodoroTime(timeMillis: Long): String {
    val totalSeconds = (timeMillis + 999) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
