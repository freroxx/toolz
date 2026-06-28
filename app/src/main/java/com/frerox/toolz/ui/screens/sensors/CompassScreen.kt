package com.frerox.toolz.ui.screens.sensors

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CompassScreen(
    viewModel: CompassViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompassContent(
        state = state,
        onBack = onBack,
        onToggleTarget = { viewModel.setTargetHeading(it) },
        onToggleFullScreen = { viewModel.toggleFullScreen() },
        startListening = { viewModel.startListening() },
        stopListening = { viewModel.stopListening() }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CompassContent(
    state: CompassState,
    onBack: () -> Unit,
    onToggleTarget: (Float?) -> Unit,
    onToggleFullScreen: () -> Unit,
    startListening: () -> Unit = {},
    stopListening: () -> Unit = {}
) {
    val haptic = rememberToolzHapticFeedback()
    val textMeasurer = rememberTextMeasurer()

    // Smooth rotation with custom spring physics
    val animatedAzimuth by animateFloatAsState(
        targetValue = state.azimuth,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "AzimuthRotation"
    )

    LaunchedEffect(state.displayAzimuth.toInt()) {
        if (state.displayAzimuth.toInt() % 90 == 0) {
            haptic.tick()
        }
    }

    DisposableEffect(Unit) {
        startListening()
        onDispose {
            stopListening()
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = !state.isFullScreen,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                ExpressiveTopAppBar(
                    title = "COMPASS",
                    subtitle = "Magnetic Orientation",
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                haptic.click()
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
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.statusBarsPadding()
                )
            }
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            ToolzHorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.padding(bottom = 16.dp),
                content = {
                    FilledIconButton(
                        onClick = { 
                            haptic.click()
                            onToggleTarget(if (state.targetHeading == null) state.displayAzimuth else null)
                        },
                        modifier = Modifier.size(48.dp),
                        shape = SmallExpressiveShape,
                        colors = if (state.targetHeading != null) 
                            IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            else IconButtonDefaults.filledIconButtonColors()
                    ) {
                        Icon(
                            if (state.targetHeading != null) Icons.Rounded.LocationDisabled else Icons.Rounded.MyLocation, 
                            contentDescription = "Target Heading"
                        )
                    }
                },
                trailingContent = {
                    clickableItem(
                        onClick = { 
                            haptic.click()
                            onToggleFullScreen()
                        },
                        icon = { Icon(if (state.isFullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, null) },
                        label = if (state.isFullScreen) "Exit Full" else "Full Screen"
                    )
                }
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        val config = LocalConfiguration.current
        val dialSize = if (state.isFullScreen) {
            (config.screenWidthDp.dp * 0.95f).coerceAtMost(600.dp)
        } else {
            (config.screenWidthDp.dp * 0.85f).coerceAtMost(450.dp)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = if (state.isFullScreen) 0.dp else padding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Heading Indicator - Bold & Refined
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = if (state.isFullScreen) 24.dp else 48.dp)
                ) {
                    Text(
                        text = "${state.displayAzimuth.toInt()}°",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = if (state.isFullScreen) 140.sp else 110.sp, 
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = (-8).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    ExpressiveStatePill(
                        text = getDirectionLabel(state.displayAzimuth),
                        icon = Icons.Rounded.Navigation,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Refined Circular Compass Dial
                Box(
                    modifier = Modifier
                        .size(dialSize)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Soft Outer Shadow/Ring
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.15f),
                        border = if (state.isFullScreen) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                    ) {}

                    val onSurface = MaterialTheme.colorScheme.onSurface
                    val cardinalLabelStyle = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = onSurface
                    )

                    // Rotating Dial Canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(-animatedAzimuth)
                    ) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val radius = size.width / 2

                        // Ticks and Labels
                        for (i in 0 until 360 step 2) {
                            val angleRad = Math.toRadians(i.toDouble() - 90).toFloat()
                            val isMain = i % 30 == 0
                            val isCardinal = i % 90 == 0
                            
                            val tickLength = if (isCardinal) 28.dp.toPx() else if (isMain) 16.dp.toPx() else 6.dp.toPx()
                            val strokeWidth = if (isCardinal) 3.dp.toPx() else if (isMain) 2.dp.toPx() else 1.dp.toPx()
                            
                            val start = Offset(
                                center.x + (radius - 20.dp.toPx() - tickLength) * cos(angleRad),
                                center.y + (radius - 20.dp.toPx() - tickLength) * sin(angleRad)
                            )
                            val end = Offset(
                                center.x + (radius - 20.dp.toPx()) * cos(angleRad),
                                center.y + (radius - 20.dp.toPx()) * sin(angleRad)
                            )
                            
                            drawLine(
                                color = if (i == 0) Color.Red else onSurface.copy(alpha = if (isMain) 0.5f else 0.15f),
                                start = start,
                                end = end,
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )

                            // Cardinal Labels (N, E, S, W)
                            if (isCardinal) {
                                val label = when (i) {
                                    0 -> "N"
                                    90 -> "E"
                                    180 -> "S"
                                    270 -> "W"
                                    else -> ""
                                }
                                val textLayoutResult = textMeasurer.measure(label, cardinalLabelStyle)
                                val labelRadius = radius - 64.dp.toPx()
                                drawText(
                                    textLayoutResult = textLayoutResult,
                                    topLeft = Offset(
                                        center.x + labelRadius * cos(angleRad) - textLayoutResult.size.width / 2,
                                        center.y + labelRadius * sin(angleRad) - textLayoutResult.size.height / 2
                                    )
                                )
                            }
                        }

                        // Target Heading indicator
                        state.targetHeading?.let { target ->
                            val targetRad = Math.toRadians(target.toDouble() - 90).toFloat()
                            drawCircle(
                                color = Color(0xFF4CAF50),
                                radius = 6.dp.toPx(),
                                center = Offset(
                                    center.x + (radius - 12.dp.toPx()) * cos(targetRad),
                                    center.y + (radius - 12.dp.toPx()) * sin(targetRad)
                                )
                            )
                        }
                    }

                    // Static Pointer (Fixed at top)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Canvas(modifier = Modifier.size(20.dp)) {
                            val path = Path().apply {
                                moveTo(size.width / 2, size.height)
                                lineTo(0f, 0f)
                                lineTo(size.width, 0f)
                                close()
                            }
                            drawPath(path, color = Color.Red)
                        }
                    }
                }

                // Diagnostics - Hidden in Full Screen
                AnimatedVisibility(
                    visible = !state.isFullScreen,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(48.dp))
                        ExpressiveCard(
                            onClick = { haptic.click() },
                            modifier = Modifier.padding(horizontal = 48.dp).fillMaxWidth(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                            shape = MediumExpressiveShape
                        ) {
                            Row(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DiagnosticItem(
                                    label = "ACCURACY",
                                    value = when (state.accuracy) {
                                        3 -> "HIGH"
                                        2 -> "MED"
                                        else -> "LOW"
                                    },
                                    color = when (state.accuracy) {
                                        3 -> Color(0xFF4CAF50)
                                        2 -> Color(0xFFFF9800)
                                        else -> Color(0xFFF44336)
                                    }
                                )
                                
                                Box(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))

                                DiagnosticItem(
                                    label = "TILT",
                                    value = "${abs(state.pitch).toInt()}°",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(if (state.isFullScreen) 0.dp else 80.dp))
            }
        }
    }
}

@Composable
private fun DiagnosticItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}

private fun getDirectionLabel(azimuth: Float): String {
    val norm = (azimuth % 360 + 360) % 360
    return when (norm) {
        in 337.5..360.0, in 0.0..22.5 -> "North"
        in 22.5..67.5 -> "North East"
        in 67.5..112.5 -> "East"
        in 112.5..157.5 -> "South East"
        in 157.5..202.5 -> "South"
        in 202.5..247.5 -> "South West"
        in 247.5..292.5 -> "West"
        in 292.5..337.5 -> "North West"
        else -> "North"
    }
}

@Preview(showBackground = true)
@Composable
fun CompassPreview() {
    ToolzTheme {
        Box(Modifier.fillMaxSize().toolzBackground()) {
            CompassContent(
                state = CompassState(displayAzimuth = 45f),
                onBack = {},
                onToggleTarget = {},
                onToggleFullScreen = {}
            )
        }
    }
}
