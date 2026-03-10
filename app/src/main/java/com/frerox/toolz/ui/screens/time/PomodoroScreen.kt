package com.frerox.toolz.ui.screens.time

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
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
        targetValue = if (totalTime > 0) state.remainingTime.toFloat() / totalTime else 0f,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "Pomodoro Progress"
    )

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                CenterAlignedTopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text("FOCUS SESSIONS", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .fadingEdge(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.05f to Color.Black,
                    0.95f to Color.Black,
                    1f to Color.Transparent
                ),
                length = 24.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Info
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = when (state.mode) {
                            PomodoroMode.WORK -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.tertiaryContainer
                        },
                        shape = RoundedCornerShape(28.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp, 
                            (if (state.mode == PomodoroMode.WORK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary).copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (state.mode == PomodoroMode.WORK) Icons.Rounded.CenterFocusStrong else Icons.Rounded.Coffee,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (state.mode == PomodoroMode.WORK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = when (state.mode) {
                                    PomodoroMode.WORK -> "WORK TIME"
                                    PomodoroMode.SHORT_BREAK -> "SHORT BREAK"
                                    PomodoroMode.LONG_BREAK -> "LONG BREAK"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = if (state.mode == PomodoroMode.WORK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "SESSION #${state.sessionsCompleted + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }

                // Central Timer Circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(340.dp)
                ) {
                    // Background Glow
                    Surface(
                        modifier = Modifier.size(280.dp).scale(1.15f),
                        shape = CircleShape,
                        color = (if (state.mode == PomodoroMode.WORK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary).copy(alpha = 0.08f)
                    ) {}

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color.LightGray.copy(alpha = 0.15f),
                            style = Stroke(width = 16.dp.toPx())
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
                                fontSize = 86.sp,
                                letterSpacing = (-4).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        AnimatedVisibility(
                            visible = state.isFinished && !state.isRunning,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            Surface(
                                onClick = { viewModel.stopRingtone() },
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(64.dp).padding(top = 8.dp).bouncyClick {},
                                shadowElevation = 8.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.NotificationsActive, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                }

                // Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 40.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset
                    IconButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset", modifier = Modifier.size(28.dp))
                    }

                    // Play/Pause
                    Surface(
                        onClick = { 
                            if (state.isFinished) viewModel.stopRingtone()
                            viewModel.toggleStartStop() 
                        },
                        modifier = Modifier.size(96.dp).bouncyClick {},
                        shape = RoundedCornerShape(28.dp),
                        color = if (state.isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isRunning) "Pause" else "Start",
                                modifier = Modifier.size(52.dp),
                                tint = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Skip
                    IconButton(
                        onClick = { viewModel.skip() },
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Skip", modifier = Modifier.size(28.dp))
                    }
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
