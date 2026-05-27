package com.frerox.toolz.ui.screens.sensors

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
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
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    // Liquid rotation with custom spring physics
    val animatedAzimuth by animateFloatAsState(
        targetValue = state.azimuth,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "AzimuthRotation"
    )

    // Haptic feedback for cardinal direction alignment
    LaunchedEffect(state.displayAzimuth.toInt()) {
        val azimuth = state.displayAzimuth.toInt()
        if (azimuth % 90 == 0) {
            vibrationManager?.vibrateTick()
        } else if (azimuth % 30 == 0) {
            // Subtle tick for minor markers
            // vibrationManager?.vibrateTick() // Maybe too much
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "COMPASS",
                subtitle = "Magnetic Orientation",
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
                            Triple("True North", Icons.Rounded.Explore, { vibrationManager?.vibrateClick() }),
                            Triple("Qibla Finder", Icons.Rounded.Mosque, { vibrationManager?.vibrateClick() }),
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
                        onClick = { vibrationManager?.vibrateClick() },
                        modifier = Modifier.size(48.dp),
                        shape = SmallExpressiveShape
                    ) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = "Lock Direction")
                    }
                },
                trailingContent = {
                    clickableItem(
                        onClick = { vibrationManager?.vibrateClick() },
                        icon = { Icon(Icons.Rounded.Explore, null) },
                        label = "True North"
                    )
                }
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        val config = LocalConfiguration.current
        val dialSize = (config.screenWidthDp.dp * 0.95f).coerceAtMost(460.dp)

        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Heading Indicator in an organic Squircle Container
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                    shape = SquircleShape,
                    modifier = Modifier.padding(bottom = 40.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp)
                    ) {
                        Text(
                            text = "${state.displayAzimuth.toInt()}°",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 100.sp, 
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = (-6).sp
                            ),
                            color = onSurface
                        )
                        
                        Text(
                            text = getDirectionLabel(state.displayAzimuth).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = primaryColor,
                            letterSpacing = 4.sp
                        )
                    }
                }

                // Precision Compass Dial with fluid movement
                Box(
                    modifier = Modifier
                        .size(dialSize)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Organic squircle backing for the dial with depth
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = SquircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.25f),
                        border = BorderStroke(2.dp, onSurface.copy(alpha = 0.08f))
                    ) {}

                    // Rotating Dial Canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(-animatedAzimuth)
                    ) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val radius = size.width / 2

                        // Dynamic Dial markings
                        for (i in 0 until 360 step 2) {
                            val angleRad = Math.toRadians(i.toDouble() - 90).toFloat()
                            val isMain = i % 30 == 0
                            val isCardinal = i % 90 == 0
                            
                            val tickLength = if (isCardinal) 40.dp.toPx() else if (isMain) 24.dp.toPx() else 12.dp.toPx()
                            val strokeWidth = if (isCardinal) 5.dp.toPx() else if (isMain) 3.dp.toPx() else 1.5.dp.toPx()
                            val alpha = if (isMain) 1f else 0.3f
                            
                            val start = Offset(
                                center.x + (radius - 32.dp.toPx() - tickLength) * cos(angleRad),
                                center.y + (radius - 32.dp.toPx() - tickLength) * sin(angleRad)
                            )
                            val end = Offset(
                                center.x + (radius - 32.dp.toPx()) * cos(angleRad),
                                center.y + (radius - 32.dp.toPx()) * sin(angleRad)
                            )
                            
                            drawLine(
                                color = if (i == 0) Color.Red else onSurface.copy(alpha = alpha),
                                start = start,
                                end = end,
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Static Directional Pointer & Integrated Bubble Level
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Static indicator pointer
                        Icon(
                            Icons.Rounded.ExpandLess,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).offset(y = (-8).dp),
                            tint = Color.Red
                        )
                        
                        // Integrated horizontal stability gauge
                        val bubbleX by animateFloatAsState(
                            targetValue = (state.roll.coerceIn(-45f, 45f)),
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "LevelX"
                        )
                        val bubbleY by animateFloatAsState(
                            targetValue = (state.pitch.coerceIn(-45f, 45f)),
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "LevelY"
                        )
                        
                        Surface(
                            modifier = Modifier
                                .size(110.dp)
                                .shadow(
                                    elevation = if (performanceMode) 0.dp else 16.dp, 
                                    shape = CircleShape, 
                                    spotColor = primaryColor.copy(alpha = 0.3f)
                                ),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                            border = BorderStroke(2.dp, onSurface.copy(alpha = 0.12f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawLine(onSurface.copy(0.08f), Offset(size.width/2, 0f), Offset(size.width/2, size.height), 1.dp.toPx())
                                    drawLine(onSurface.copy(0.08f), Offset(0f, size.height/2), Offset(size.width, size.height/2), 1.dp.toPx())
                                    drawCircle(onSurface.copy(0.05f), radius = 10.dp.toPx(), style = Stroke(1.dp.toPx()))
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .offset(bubbleX.dp * 0.9f, bubbleY.dp * 0.9f)
                                        .clip(CircleShape)
                                        .background(if (state.isLevel) Color(0xFF4CAF50) else primaryColor)
                                        .border(2.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(56.dp))

                // Diagnostic Data Hub with Bouncy Shape
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                    shape = BouncyShape,
                    border = BorderStroke(1.dp, onSurface.copy(alpha = 0.15f)),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DiagnosticItemInternal(
                            label = "ACCURACY",
                            value = when (state.accuracy) {
                                3 -> "HIGH"
                                2 -> "MEDIUM"
                                else -> "LOW"
                            },
                            color = when (state.accuracy) {
                                3 -> Color(0xFF4CAF50)
                                2 -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                        )
                        
                        VerticalDivider(modifier = Modifier.height(44.dp).width(1.5.dp), color = onSurface.copy(alpha = 0.15f))
                        
                        DiagnosticItemInternal(
                            label = "TILT",
                            value = if (state.isLevel) "OPTIMAL" else "${abs(state.pitch).toInt()}°",
                            color = if (state.isLevel) Color(0xFF4CAF50) else primaryColor
                        )
                    }
                }
                
                Spacer(Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun DiagnosticItemInternal(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            letterSpacing = 2.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}

private fun getDirectionLabel(azimuth: Float): String {
    return when (azimuth) {
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
            // Preview
        }
    }
}
