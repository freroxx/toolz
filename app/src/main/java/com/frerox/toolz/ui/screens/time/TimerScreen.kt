package com.frerox.toolz.ui.screens.time

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    val animatedProgress by animateFloatAsState(
        targetValue = if (state.initialTime > 0) state.remainingTime.toFloat() / state.initialTime else 0f,
        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
        label = "Timer Progress"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "PRECISION TIMER",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleHaptic() },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                if (state.initialTime == 0L || (state.isFinished && !state.isRunning)) {
                    // SELECTION UI
                    Spacer(Modifier.height(8.dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "SET DURATION",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModernTimePicker(
                            value = state.selectedMinutes,
                            onValueChange = { 
                                viewModel.onTimeSelectedChange(it, state.selectedSeconds)
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                            label = "MINUTES"
                        )
                        
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
                            }
                        }

                        ModernTimePicker(
                            value = state.selectedSeconds,
                            onValueChange = { 
                                viewModel.onTimeSelectedChange(state.selectedMinutes, it)
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                            label = "SECONDS"
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("QUICK PRESETS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline, letterSpacing = 1.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf(1, 5, 10, 15).forEach { min ->
                                Surface(
                                    onClick = { 
                                        viewModel.onTimeSelectedChange(min, 0)
                                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(60.dp)
                                        .bouncyClick {},
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${min}M", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Button(
                        onClick = {
                            if (state.selectedMinutes > 0 || state.selectedSeconds > 0) {
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setTimer(state.selectedMinutes, state.selectedSeconds)
                                viewModel.toggleStartStop()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .bouncyClick {},
                        shape = RoundedCornerShape(20.dp),
                        elevation = ButtonDefaults.buttonColors().containerColor.let { ButtonDefaults.buttonElevation(defaultElevation = 8.dp) }
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("START COUNTDOWN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                } else {
                    // ACTIVE UI
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(300.dp)
                            .padding(16.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val glowAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.05f,
                            targetValue = 0.12f,
                            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                            label = ""
                        )
                        
                        val primaryColor = MaterialTheme.colorScheme.primary

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryColor.copy(alpha = glowAlpha), Color.Transparent),
                                    radius = size.width / 1.1f
                                )
                            )
                        }
                        
                        CircularProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 14.dp,
                            strokeCap = StrokeCap.Round,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 14.dp,
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
                                    fontSize = 72.sp,
                                    letterSpacing = (-3).sp
                                ),
                                color = if (state.isFinished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            AnimatedVisibility(
                                visible = state.isPaused,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text(
                                        "PAUSED", 
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall, 
                                        fontWeight = FontWeight.Black, 
                                        color = MaterialTheme.colorScheme.secondary,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        listOf(30, 60).forEach { sec ->
                            Surface(
                                onClick = { 
                                    viewModel.addTime(sec * 1000L)
                                    if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .bouncyClick {},
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("+${sec}S", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { 
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.reset() 
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                        
                        Surface(
                            onClick = { 
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (state.isFinished) viewModel.stopRingtone()
                                viewModel.toggleStartStop() 
                            },
                            modifier = Modifier
                                .size(90.dp)
                                .bouncyClick {},
                            shape = CircleShape,
                            color = if (state.isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                            shadowElevation = 12.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 
                                    null, 
                                    modifier = Modifier.size(44.dp),
                                    tint = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        IconButton(
                            onClick = { 
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.reset() 
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // Finished Overlay
            AnimatedVisibility(
                visible = state.isFinished,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                TimerFinishedOverlay(
                    onDismiss = {
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .width(130.dp)
                .height(180.dp)
                .shadow(16.dp, RoundedCornerShape(32.dp), spotColor = primaryColor.copy(alpha = 0.3f)),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.1f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Background Highlight
                Surface(
                    modifier = Modifier.fillMaxWidth(0.8f).height(72.dp),
                    color = primaryColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                ) {}

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { onValueChange((value + 1) % 60) }, 
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Up", tint = primaryColor.copy(alpha = 0.6f))
                    }
                    
                    Text(
                        text = String.format(Locale.getDefault(), "%02d", value),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 64.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    IconButton(
                        onClick = { onValueChange((value - 1 + 60) % 60) }, 
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Down", tint = primaryColor.copy(alpha = 0.6f))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = label, 
            style = MaterialTheme.typography.labelSmall, 
            fontWeight = FontWeight.ExtraBold, 
            color = primaryColor, 
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
fun TimerFinishedOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "iconScale"
            )

            Surface(
                modifier = Modifier
                    .size(160.dp)
                    .scale(pulseScale),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                shadowElevation = 24.dp,
                border = BorderStroke(4.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.NotificationsActive, null, modifier = Modifier.size(80.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.height(48.dp))
            Text("TIME'S UP!", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 2.sp, textAlign = TextAlign.Center)
            Text("Your countdown has completed", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            
            Spacer(Modifier.height(64.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .bouncyClick {},
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(24.dp)
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
