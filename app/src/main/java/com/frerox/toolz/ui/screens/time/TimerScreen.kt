package com.frerox.toolz.ui.screens.time

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.style.TextAlign
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
fun TimerScreen(
    viewModel: TimerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val performanceMode = LocalPerformanceMode.current
    val hapticEnabled = LocalHapticEnabled.current
    val vibrationManager = LocalVibrationManager.current

    val animatedProgress by animateFloatAsState(
        targetValue = if (state.initialTime > 0) state.remainingTime.toFloat() / state.initialTime else 0f,
        animationSpec = if (performanceMode) snap() else tween(durationMillis = 500, easing = LinearOutSlowInEasing),
        label = "Timer Progress"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "PRECISION TIMER",
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
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            viewModel.toggleHaptic() 
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (hapticEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                    ) {
                        Icon(
                            if (hapticEnabled) Icons.Rounded.Vibration else Icons.Rounded.CommentsDisabled,
                            contentDescription = "Haptic Toggle",
                            tint = if (hapticEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
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
            .then(if (performanceMode) Modifier else Modifier.fadingEdge(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)), 24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.initialTime == 0L || (state.isFinished && !state.isRunning)) {
                    // SELECTION UI
                    Spacer(Modifier.height(16.dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "SET DURATION",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                    }

                    Spacer(Modifier.weight(0.5f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModernTimePicker(
                            value = state.selectedMinutes,
                            onValueChange = { 
                                viewModel.onTimeSelectedChange(it, state.selectedSeconds)
                                if (hapticEnabled) vibrationManager?.vibrateTick()
                            },
                            label = "MINUTES"
                        )
                        
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 32.dp)
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                            }
                        }

                        ModernTimePicker(
                            value = state.selectedSeconds,
                            onValueChange = { 
                                viewModel.onTimeSelectedChange(state.selectedMinutes, it)
                                if (hapticEnabled) vibrationManager?.vibrateTick()
                            },
                            label = "SECONDS"
                        )
                    }

                    Spacer(Modifier.weight(0.5f))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "QUICK PRESETS", 
                            style = MaterialTheme.typography.labelSmall, 
                            fontWeight = FontWeight.Black, 
                            color = MaterialTheme.colorScheme.outline, 
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf(1, 5, 10, 15).forEach { min ->
                                Surface(
                                    onClick = { 
                                        vibrationManager?.vibrateClick()
                                        viewModel.onTimeSelectedChange(min, 0)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(64.dp)
                                        .bouncyClick {},
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${min}m", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Button(
                        onClick = {
                            if (state.selectedMinutes > 0 || state.selectedSeconds > 0) {
                                vibrationManager?.vibrateLongClick()
                                viewModel.setTimer(state.selectedMinutes, state.selectedSeconds)
                                viewModel.toggleStartStop()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(bottom = 24.dp),
                        shape = RoundedCornerShape(28.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("START ENGINE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                } else {
                    // ACTIVE UI
                    Spacer(Modifier.weight(0.5f))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val glowAlpha by if (performanceMode) remember { mutableFloatStateOf(0.06f) } else infiniteTransition.animateFloat(
                            initialValue = 0.04f,
                            targetValue = 0.1f,
                            animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
                            label = ""
                        )
                        
                        val primaryColor = MaterialTheme.colorScheme.primary

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryColor.copy(alpha = glowAlpha), Color.Transparent),
                                    radius = size.width / 1.2f
                                )
                            )
                        }
                        
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(0.85f),
                            strokeWidth = 16.dp,
                            strokeCap = StrokeCap.Round,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        )
                        
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize(0.85f),
                            strokeWidth = 16.dp,
                            strokeCap = StrokeCap.Round,
                            color = if (state.isFinished) MaterialTheme.colorScheme.error else primaryColor,
                            trackColor = Color.Transparent,
                        )
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatTimerTime(state.remainingTime),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 80.sp,
                                    letterSpacing = (-4).sp
                                ),
                                color = if (state.isFinished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            
                            Spacer(Modifier.height(8.dp))
                            
                            AnimatedVisibility(
                                visible = state.isPaused,
                                enter = if (performanceMode) fadeIn() else (fadeIn() + expandVertically()),
                                exit = if (performanceMode) fadeOut() else (fadeOut() + shrinkVertically())
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        "PAUSED", 
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall, 
                                        fontWeight = FontWeight.Black, 
                                        color = MaterialTheme.colorScheme.secondary,
                                        letterSpacing = 1.5.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        listOf(30, 60).forEach { sec ->
                            Surface(
                                onClick = { 
                                    vibrationManager?.vibrateTick()
                                    viewModel.addTime(sec * 1000L)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .bouncyClick {},
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("+${sec}s", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    
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
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                        
                        Surface(
                            onClick = { 
                                vibrationManager?.vibrateClick()
                                if (state.isFinished) viewModel.stopRingtone()
                                viewModel.toggleStartStop() 
                            },
                            modifier = Modifier
                                .size(100.dp)
                                .bouncyClick {},
                            shape = CircleShape,
                            color = if (state.isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                            shadowElevation = if (performanceMode) 0.dp else 16.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 
                                    null, 
                                    modifier = Modifier.size(48.dp),
                                    tint = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

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
                            Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // Finished Overlay
            AnimatedVisibility(
                visible = state.isFinished,
                enter = if (performanceMode) fadeIn() else (fadeIn() + scaleIn()),
                exit = if (performanceMode) fadeOut() else (fadeOut() + scaleOut())
            ) {
                TimerFinishedOverlay(
                    onDismiss = {
                        vibrationManager?.vibrateClick()
                        viewModel.stopRingtone()
                        viewModel.reset()
                    }
                )
            }
        }
    }
}

@Composable
fun ModernTimePicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .width(130.dp)
                .height(190.dp)
                .then(if (performanceMode) Modifier else Modifier.shadow(24.dp, RoundedCornerShape(40.dp), spotColor = primaryColor.copy(alpha = 0.25f))),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(40.dp),
            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.1f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Background Highlight
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(80.dp),
                    color = primaryColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(20.dp)
                ) {}

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { 
                            vibrationManager?.vibrateTick()
                            onValueChange((value + 1) % 60) 
                        }, 
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Up", tint = primaryColor.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
                    }
                    
                    Text(
                        text = String.format(Locale.getDefault(), "%02d", value),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 68.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = (-2).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    IconButton(
                        onClick = { 
                            vibrationManager?.vibrateTick()
                            onValueChange((value - 1 + 60) % 60) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Down", tint = primaryColor.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = label, 
            style = MaterialTheme.typography.labelSmall, 
            fontWeight = FontWeight.Black, 
            color = primaryColor, 
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
fun TimerFinishedOverlay(onDismiss: () -> Unit) {
    val performanceMode = LocalPerformanceMode.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by if (performanceMode) remember { mutableFloatStateOf(1f) } else infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "iconScale"
            )

            Surface(
                modifier = Modifier
                    .size(180.dp)
                    .scale(pulseScale),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                shadowElevation = if (performanceMode) 0.dp else 32.dp,
                border = BorderStroke(4.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.NotificationsActive, null, modifier = Modifier.size(96.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.height(48.dp))
            Text("TIME'S UP", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 2.sp, textAlign = TextAlign.Center)
            Text("Your precision countdown finished", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            
            Spacer(Modifier.height(64.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(32.dp)
            ) {
                Text("DISMISS ALARM", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
        }
    }
}

private fun formatTimerTime(timeMillis: Long): String {
    val totalSeconds = (timeMillis + 999) / 1000
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
