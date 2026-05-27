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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PomodoroScreen(
    viewModel: PomodoroViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    val totalTime = state.mode.minutes * 60 * 1000L
    
    // Smooth bouncy progress tracking
    val animatedProgress by animateFloatAsState(
        targetValue = if (totalTime > 0) state.remainingTime.toFloat() / totalTime else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "PomodoroProgress"
    )

    // Energetic mode transition
    val activeColor = if (state.mode == PomodoroMode.WORK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "FOCUS FLOW",
                subtitle = "Deep Work Protocol",
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
                            Triple("Customize", Icons.Rounded.Edit, { vibrationManager?.vibrateClick() }),
                            Triple("Stats", Icons.Rounded.BarChart, { vibrationManager?.vibrateClick() }),
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
                            containerColor = if (state.isRunning) MaterialTheme.colorScheme.error else activeColor
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
                    clickableItem(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.skip()
                        },
                        icon = { Icon(Icons.Rounded.SkipNext, null) },
                        label = "SKIP"
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Mode Indicator with Bouncy Shape
                StaggeredEntrance(index = 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            color = activeColor.copy(alpha = 0.12f),
                            shape = BouncyShape,
                            border = BorderStroke(1.5.dp, activeColor.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (state.mode == PomodoroMode.WORK) Icons.Rounded.CenterFocusStrong else Icons.Rounded.Coffee,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = activeColor
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = if (state.mode == PomodoroMode.WORK) "FOCUS PHASE" else "RECOVERY PHASE",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                    color = activeColor,
                                    letterSpacing = 2.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                            shape = SmallExpressiveShape
                        ) {
                            Text(
                                text = "CYCLE SEQUENCE: #${state.sessionsCompleted + 1}",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(56.dp))

                // Session Countdown with Wavy Dial in Squircle Container
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(340.dp)) {
                    // Dynamic background glow
                    if (!performanceMode) {
                        val infiniteTransition = rememberInfiniteTransition(label = "PomodoroGlow")
                        val glowScale by infiniteTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.05f,
                            animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                            label = "Scale"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(glowScale)
                                .background(
                                    Brush.radialGradient(
                                        listOf(activeColor.copy(alpha = 0.1f), Color.Transparent)
                                    ),
                                    CircleShape
                                )
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        shape = SquircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = formatPomodoroTime(state.remainingTime),
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 96.sp,
                                        letterSpacing = (-6).sp
                                    ),
                                    color = if (state.isRunning) activeColor else MaterialTheme.colorScheme.onSurface
                                )
                                
                                AnimatedVisibility(
                                    visible = !state.isRunning && state.remainingTime > 0,
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut()
                                ) {
                                    Text(
                                        "READY TO SYNC",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = activeColor.copy(alpha = 0.6f),
                                        letterSpacing = 3.sp
                                    )
                                }
                            }
                        }
                    }

                    // Official Circular Wavy Progress Indicator
                    ToolzWavyCircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = activeColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f),
                    )
                }

                Spacer(modifier = Modifier.height(64.dp))
                
                // Energetic Finish Overlay
                AnimatedVisibility(
                    visible = state.isFinished && !state.isRunning,
                    enter = fadeIn() + scaleIn(animationSpec = spring(Spring.DampingRatioLowBouncy)),
                    exit = fadeOut() + scaleOut()
                ) {
                    ExpressiveCard(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            viewModel.stopRingtone() 
                        },
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        shape = BouncyShape,
                        containerColor = MaterialTheme.colorScheme.error,
                        elevation = 0.dp
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.NotificationsActive, null, modifier = Modifier.size(28.dp), tint = Color.White)
                                Spacer(Modifier.width(16.dp))
                                Text("SESSION COMPLETE • TAP TO SILENCE", fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(100.dp))
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
