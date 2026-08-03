/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.ui.screens.sensors

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BubbleLevelScreen(
    viewModel: BubbleLevelViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.bubbleState.collectAsStateWithLifecycle()
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current
    var wasLevel by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    // Liquid-like bouncy spring physics for the bubble
    val animX by animateFloatAsState(
        targetValue = state.x,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "BubbleX"
    )
    val animY by animateFloatAsState(
        targetValue = state.y,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "BubbleY"
    )

    val isLevel = abs(state.x) < 0.15f && abs(state.y) < 0.15f
    
    LaunchedEffect(isLevel) {
        if (isLevel && !wasLevel) {
            vibrationManager?.vibrateSuccess()
        }
        wasLevel = isLevel
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "BUBBLE LEVEL",
                subtitle = "Precision Equilibrium",
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
                    ToolzExpressiveIconButton(
                        onClick = { viewModel.toggleHold() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (state.isHeld) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                            contentColor = if (state.isHeld) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = SmallExpressiveShape,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(if (state.isHeld) Icons.Rounded.Lock else Icons.Rounded.LockOpen, contentDescription = "Hold")
                    }
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
                            viewModel.calibrate()
                        },
                        modifier = Modifier.size(48.dp),
                        shape = SmallExpressiveShape
                    ) {
                        Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "Calibrate")
                    }
                },
                trailingContent = {
                    clickableItem(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            viewModel.resetCalibration()
                        },
                        icon = { Icon(Icons.Rounded.Refresh, null) },
                        label = "Reset"
                    )
                    clickableItem(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            val nextMode = when (state.mode) {
                                LevelMode.BULLSEYE -> LevelMode.HORIZONTAL
                                LevelMode.HORIZONTAL -> LevelMode.VERTICAL
                                LevelMode.VERTICAL -> LevelMode.BULLSEYE
                            }
                            viewModel.setMode(nextMode)
                        },
                        icon = { 
                            Icon(
                                when (state.mode) {
                                    LevelMode.BULLSEYE -> Icons.Rounded.TrackChanges
                                    LevelMode.HORIZONTAL -> Icons.Rounded.ViewStream
                                    LevelMode.VERTICAL -> Icons.Rounded.ViewArray
                                }, 
                                null
                            ) 
                        },
                        label = "Mode"
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
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                StaggeredEntrance(index = 0) {
                    ExpressiveCard(
                        onClick = { viewModel.toggleHold() },
                        modifier = Modifier.padding(bottom = 48.dp),
                        containerColor = if (isLevel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        border = BorderStroke(1.5.dp, if (isLevel) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val activeColor = if (isLevel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            Icon(
                                if (state.isHeld) Icons.Rounded.Lock else Icons.Rounded.Speed,
                                null,
                                tint = activeColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            
                            // Locked horizontally with headlineMedium for better fit and consistency
                            Text(
                                text = String.format(Locale.getDefault(), "X %.1f°  Y %.1f°", state.x, state.y),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = activeColor,
                                letterSpacing = (-1).sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                StaggeredEntrance(index = 1) {
                    // Main Level Container with organic Squircle Shape
                    Box(
                        modifier = Modifier
                            .size(320.dp)
                            .clip(SquircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.2f))
                            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)), SquircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        // Glassmorphism background effect
                        if (!performanceMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(40.dp)
                                    .alpha(0.1f)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(MaterialTheme.colorScheme.primary, Color.Transparent),
                                            center = Offset(160.dp.value, 160.dp.value)
                                        )
                                    )
                            )
                        }

                        // Dynamic background glow when level
                        if (isLevel && !performanceMode) {
                            val infiniteTransition = rememberInfiniteTransition(label = "LevelGlow")
                            val glowScale by infiniteTransition.animateFloat(
                                initialValue = 0.9f,
                                targetValue = 1.1f,
                                animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                                label = "Scale"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(glowScale)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), Color.Transparent)
                                        )
                                    )
                            )
                        }

                        // Minimalist Grid
                        val gridColor = if (isLevel) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        Canvas(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                            val center = Offset(size.width / 2, size.height / 2)
                            
                            when (state.mode) {
                                LevelMode.BULLSEYE -> {
                                    drawCircle(gridColor, radius = 40.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
                                    drawCircle(gridColor.copy(alpha = 0.5f), radius = 4.dp.toPx(), center = center)
                                }
                                LevelMode.HORIZONTAL -> {
                                    drawLine(gridColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
                                    drawLine(gridColor, Offset(size.width / 2 - 40.dp.toPx(), size.height / 2 - 20.dp.toPx()), Offset(size.width / 2 - 40.dp.toPx(), size.height / 2 + 20.dp.toPx()), 2.dp.toPx())
                                    drawLine(gridColor, Offset(size.width / 2 + 40.dp.toPx(), size.height / 2 - 20.dp.toPx()), Offset(size.width / 2 + 40.dp.toPx(), size.height / 2 + 20.dp.toPx()), 2.dp.toPx())
                                }
                                LevelMode.VERTICAL -> {
                                    drawLine(gridColor, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx())
                                    drawLine(gridColor, Offset(size.width / 2 - 20.dp.toPx(), size.height / 2 - 40.dp.toPx()), Offset(size.width / 2 + 20.dp.toPx(), size.height / 2 - 40.dp.toPx()), 2.dp.toPx())
                                    drawLine(gridColor, Offset(size.width / 2 - 20.dp.toPx(), size.height / 2 + 40.dp.toPx()), Offset(size.width / 2 + 20.dp.toPx(), size.height / 2 + 40.dp.toPx()), 2.dp.toPx())
                                }
                            }
                        }

                        // Liquid Bubble
                        val bubbleOffsetScale = 14f
                        val bubbleX = if (state.mode == LevelMode.VERTICAL) 0.dp else (animX * bubbleOffsetScale).dp
                        val bubbleY = if (state.mode == LevelMode.HORIZONTAL) 0.dp else (animY * bubbleOffsetScale).dp
                        
                        Box(
                            modifier = Modifier
                                .offset(x = -bubbleX, y = bubbleY)
                                .size(72.dp)
                                .scale(if (isLevel) 1.1f else 1f)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = if (isLevel) {
                                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                        } else {
                                            listOf(MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f))
                                        }
                                    )
                                )
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), CircleShape),
                            contentAlignment = Alignment.TopStart
                        ) {
                            // Minimalist liquid highlight
                            Box(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(20.dp, 10.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(64.dp))
                
                StaggeredEntrance(index = 2) {
                    // Equilibrium Status
                    AnimatedContent(
                        targetState = isLevel,
                        transitionSpec = { 
                            (scaleIn(animationSpec = spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith 
                            (scaleOut() + fadeOut())
                        }, 
                        label = "LevelStatus"
                    ) { level ->
                        Text(
                            text = if (level) "LEVEL" else "ADJUST",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            color = if (level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            letterSpacing = 4.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BubbleLevelPreview() {
    ToolzTheme {
        Box(Modifier.fillMaxSize().toolzBackground()) {
            // Preview logic
        }
    }
}
