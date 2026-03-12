package com.frerox.toolz.ui.screens.time

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import com.frerox.toolz.ui.components.SquigglySlider
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
    
    var selectedMinutes by remember { mutableIntStateOf(0) }
    var selectedSeconds by remember { mutableIntStateOf(0) }

    val animatedProgress by animateFloatAsState(
        targetValue = if (state.initialTime > 0) state.remainingTime.toFloat() / state.initialTime else 0f,
        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
        label = "Timer Progress"
    )

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color.Transparent).statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    
                    Text(
                        text = "TIMER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    )

                    IconButton(
                        onClick = { viewModel.toggleHaptic() },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(
                            if (hapticEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Icon(
                            if (hapticEnabled) Icons.Rounded.Vibration else Icons.Rounded.CommentsDisabled,
                            contentDescription = "Haptic Toggle",
                            tint = if (hapticEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent
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
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        "SET DURATION",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModernTimePicker(
                            value = selectedMinutes,
                            onValueChange = { 
                                selectedMinutes = it
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                            label = "MINUTES"
                        )
                        Text(
                            ":", 
                            style = MaterialTheme.typography.displayLarge,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 40.dp),
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        ModernTimePicker(
                            value = selectedSeconds,
                            onValueChange = { 
                                selectedSeconds = it
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
                                        selectedMinutes = min
                                        selectedSeconds = 0
                                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(64.dp)
                                        .bouncyClick {},
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                            if (selectedMinutes > 0 || selectedSeconds > 0) {
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setTimer(selectedMinutes, selectedSeconds)
                                viewModel.toggleStartStop()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .bouncyClick {},
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("START COUNTDOWN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    }
                } else {
                    // ACTIVE UI
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(320.dp)
                            .padding(16.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color.LightGray.copy(alpha = 0.1f),
                                style = Stroke(width = 20.dp.toPx())
                            )
                        }
                        
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 20.dp,
                            strokeCap = StrokeCap.Round,
                            color = if (state.isFinished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent,
                        )
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatTimerTime(state.remainingTime),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 84.sp,
                                    letterSpacing = (-4).sp
                                ),
                                color = if (state.isFinished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            AnimatedVisibility(
                                visible = state.isPaused,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                    shape = CircleShape,
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text(
                                        "PAUSED", 
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelLarge, 
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
                                    .height(68.dp)
                                    .bouncyClick {},
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(6.6.dp))
                                    Text("+${sec}S", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
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
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                        
                        Surface(
                            onClick = { 
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (state.isFinished) viewModel.stopRingtone()
                                viewModel.toggleStartStop() 
                            },
                            modifier = Modifier
                                .size(100.dp)
                                .bouncyClick {},
                            shape = CircleShape,
                            color = if (state.isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 
                                    null, 
                                    modifier = Modifier.size(52.dp),
                                    tint = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        IconButton(
                            onClick = { 
                                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.reset() 
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
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
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2 % 60) + value - 1)
    
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = listState.firstVisibleItemIndex + 1
            onValueChange(centerIndex % 60)
            listState.animateScrollToItem(listState.firstVisibleItemIndex)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(110.dp)
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {}

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(vertical = 55.dp),
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
            ) {
                items(Int.MAX_VALUE) { index ->
                    val item = index % 60
                    val isSelected = (listState.firstVisibleItemIndex + 1) == index
                    Text(
                        text = String.format(Locale.getDefault(), "%02d", item),
                        style = if (isSelected) MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black) 
                                else MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        modifier = Modifier.padding(vertical = 4.dp),
                        fontSize = if (isSelected) 48.sp else 32.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
    }
}

@Composable
fun TimerFinishedOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.25f,
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
                shadowElevation = 24.dp,
                border = androidx.compose.foundation.BorderStroke(4.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.NotificationsActive, null, modifier = Modifier.size(90.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.height(56.dp))
            Text("TIME'S UP!", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 2.sp, textAlign = TextAlign.Center)
            Text("Your countdown has completed", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            
            Spacer(Modifier.height(72.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .bouncyClick {},
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(28.dp)
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
