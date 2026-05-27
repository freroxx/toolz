package com.frerox.toolz.ui.screens.time

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.ToolzWavyCircularProgressIndicator
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import com.frerox.toolz.ui.theme.LocalHapticEnabled
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
fun TimerScreen(
    viewModel: TimerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    // Bouncy spring for the countdown progress
    val animatedProgress by animateFloatAsState(
        targetValue = if (state.initialTime > 0) state.remainingTime.toFloat() / state.initialTime else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "TimerProgress"
    )

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "TIMER",
                subtitle = "Precision Countdown",
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
                            Triple("Add 1 min", Icons.Rounded.Add, { 
                                vibrationManager?.vibrateClick()
                                viewModel.addTime(60000L) 
                            }),
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
                            if (state.isFinished) viewModel.stopRingtone()
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
                    .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 24.dp, bottom = 24.dp))
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.initialTime == 0L || (state.isFinished && !state.isRunning)) {
                    // SELECTION UI with Expressive Time Pickers
                    Spacer(Modifier.height(16.dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = BouncyShape
                    ) {
                        Text(
                            "CONFIGURE SESSION",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                    }

                    Spacer(Modifier.weight(0.6f))

                    Row(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModernTimePickerExpressive(
                            value = state.selectedMinutes,
                            onValueChange = { viewModel.onTimeSelectedChange(it, state.selectedSeconds) },
                            label = "MINUTES"
                        )
                        
                        Box(modifier = Modifier.padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                            }
                        }

                        ModernTimePickerExpressive(
                            value = state.selectedSeconds,
                            onValueChange = { viewModel.onTimeSelectedChange(state.selectedMinutes, it) },
                            label = "SECONDS"
                        )
                    }

                    Spacer(Modifier.weight(0.4f))

                    // Presets Hub with Bouncy Cards
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "QUICK PRESETS", 
                            style = MaterialTheme.typography.labelSmall, 
                            fontWeight = FontWeight.Black, 
                            color = MaterialTheme.colorScheme.outline, 
                            letterSpacing = 2.sp
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(5, 10, 25, 45).forEach { min ->
                                ExpressiveCard(
                                    onClick = { 
                                        vibrationManager?.vibrateClick()
                                        viewModel.onTimeSelectedChange(min, 0)
                                    },
                                    modifier = Modifier.weight(1f).height(64.dp),
                                    shape = BouncyShape,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                                    elevation = 0.dp
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("${min}m", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    
                    Spacer(Modifier.height(100.dp))
                } else {
                    // ACTIVE TIMER UI with Wavy Dial
                    Spacer(Modifier.weight(0.6f))
                    
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(340.dp)) {
                        val primaryColor = MaterialTheme.colorScheme.primary
                        
                        // Dynamic background glow
                        if (!performanceMode) {
                            val infiniteTransition = rememberInfiniteTransition(label = "TimerGlow")
                            val glowAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.05f,
                                targetValue = 0.15f,
                                animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                                label = "Glow"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            listOf(primaryColor.copy(alpha = glowAlpha), Color.Transparent)
                                        ),
                                        CircleShape
                                    )
                            )
                        }
                        
                        // Official Circular Wavy Progress Indicator
                        ToolzWavyCircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize(0.9f),
                            color = if (state.isFinished) MaterialTheme.colorScheme.error else primaryColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatTimerTime(state.remainingTime),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 88.sp,
                                    letterSpacing = (-6).sp
                                ),
                                color = if (state.isFinished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            
                            AnimatedVisibility(
                                visible = state.isPaused,
                                enter = fadeIn() + scaleIn(animationSpec = spring(Spring.DampingRatioMediumBouncy)),
                                exit = fadeOut() + scaleOut()
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                    shape = BouncyShape,
                                    modifier = Modifier.padding(top = 16.dp)
                                ) {
                                    Text(
                                        "PAUSED", 
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelSmall, 
                                        fontWeight = FontWeight.Black, 
                                        color = MaterialTheme.colorScheme.secondary,
                                        letterSpacing = 2.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(0.2f))

                    // Action Hub: Quick Add Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        listOf(60, 300).forEach { sec ->
                            ExpressiveCard(
                                onClick = { 
                                    vibrationManager?.vibrateTick()
                                    viewModel.addTime(sec * 1000L)
                                },
                                modifier = Modifier.weight(1f).height(72.dp),
                                shape = BouncyShape,
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                elevation = 0.dp
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (sec < 60) "${sec}S" else "${sec/60}M", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    
                    Spacer(Modifier.height(120.dp))
                }
            }

            // Finished Overlay with Energetic Pulsing
            AnimatedVisibility(
                visible = state.isFinished,
                enter = fadeIn() + scaleIn(animationSpec = spring(Spring.DampingRatioLowBouncy)),
                exit = fadeOut() + scaleOut()
            ) {
                TimerFinishedOverlayExpressive(
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
fun ModernTimePickerExpressive(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String
) {
    val vibrationManager = LocalVibrationManager.current
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.width(130.dp).height(210.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
            shape = SquircleShape,
            border = BorderStroke(1.5.dp, primaryColor.copy(alpha = 0.15f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Selector Highlight
                Surface(
                    modifier = Modifier.fillMaxWidth(0.8f).height(80.dp),
                    color = primaryColor.copy(alpha = 0.12f),
                    shape = BouncyShape
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
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowUp, null, tint = primaryColor, modifier = Modifier.size(32.dp))
                    }
                    
                    Text(
                        text = String.format(Locale.getDefault(), "%02d", value),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 72.sp,
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
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = primaryColor, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = label, 
            style = MaterialTheme.typography.labelSmall, 
            fontWeight = FontWeight.Black, 
            color = primaryColor.copy(alpha = 0.7f), 
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun TimerFinishedOverlayExpressive(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)).padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val infiniteTransition = rememberInfiniteTransition(label = "AlarmPulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "Scale"
            )

            Surface(
                modifier = Modifier.size(200.dp).scale(pulseScale),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
                border = BorderStroke(6.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.NotificationsActive, null, modifier = Modifier.size(100.dp), tint = Color.White)
                }
            }
            
            Spacer(Modifier.height(64.dp))
            Text("SESSION EXPIRED", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 2.sp)
            
            Spacer(Modifier.height(64.dp))
            
            ToolzExpressiveButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                shape = BouncyShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
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
