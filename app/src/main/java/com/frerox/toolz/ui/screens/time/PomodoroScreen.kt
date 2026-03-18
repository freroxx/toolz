package com.frerox.toolz.ui.screens.time

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(
    viewModel: PomodoroViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val performanceMode = LocalPerformanceMode.current
    val hapticEnabled = LocalHapticEnabled.current
    val vibrationManager = LocalVibrationManager.current

    val totalTime = state.mode.minutes * 60 * 1000L
    val animatedProgress by animateFloatAsState(
        targetValue = if (totalTime > 0) state.remainingTime.toFloat() / totalTime else 0f,
        animationSpec = if (performanceMode) snap() else tween(durationMillis = 1000, easing = LinearEasing),
        label = "Pomodoro Progress"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "FOCUS SESSIONS",
                        style = MaterialTheme.typography.titleMedium,
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
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
            .then(if (performanceMode) Modifier else Modifier.fadingEdge(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)), 24.dp))
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
                            PomodoroMode.WORK -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        },
                        shape = RoundedCornerShape(32.dp),
                        border = BorderStroke(
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
                                    PomodoroMode.WORK -> "FOCUS TIME"
                                    PomodoroMode.SHORT_BREAK -> "SHORT BREAK"
                                    PomodoroMode.LONG_BREAK -> "LONG BREAK"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = if (state.mode == PomodoroMode.WORK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        @Suppress("DEPRECATION")
                        Text(
                            text = "SESSION #${state.sessionsCompleted + 1}",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                // Central Timer Circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(320.dp)
                ) {
                    val activeColor = if (state.mode == PomodoroMode.WORK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
                    
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val glowAlpha by if (performanceMode) remember { mutableFloatStateOf(0.1f) } else infiniteTransition.animateFloat(
                        initialValue = 0.05f,
                        targetValue = 0.15f,
                        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                        label = ""
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(activeColor.copy(alpha = glowAlpha), Color.Transparent),
                                radius = size.width / 1.1f
                            )
                        )
                        drawCircle(
                            color = surfaceVariant.copy(alpha = 0.5f),
                            style = Stroke(width = 16.dp.toPx())
                        )
                    }

                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 16.dp,
                        strokeCap = StrokeCap.Round,
                        color = activeColor,
                        trackColor = Color.Transparent,
                    )
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatPomodoroTime(state.remainingTime),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 80.sp,
                                letterSpacing = (-4).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        AnimatedVisibility(
                            visible = state.isFinished && !state.isRunning,
                            enter = if (performanceMode) fadeIn() else (fadeIn() + scaleIn()),
                            exit = if (performanceMode) fadeOut() else (fadeOut() + scaleOut())
                        ) {
                            IconButton(
                                onClick = { 
                                    vibrationManager?.vibrateClick()
                                    viewModel.stopRingtone() 
                                },
                                modifier = Modifier
                                    .padding(top = 16.dp)
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Rounded.NotificationsOff, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }

                // Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp)
                        .navigationBarsPadding(),
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
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset", modifier = Modifier.size(32.dp))
                    }

                    Surface(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            if (state.isFinished) viewModel.stopRingtone()
                            viewModel.toggleStartStop() 
                        },
                        modifier = Modifier.size(100.dp).bouncyClick {},
                        shape = CircleShape,
                        color = if (state.isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                        shadowElevation = if (performanceMode) 0.dp else 16.dp
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
                            vibrationManager?.vibrateClick()
                            viewModel.skip() 
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Skip", modifier = Modifier.size(32.dp))
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
