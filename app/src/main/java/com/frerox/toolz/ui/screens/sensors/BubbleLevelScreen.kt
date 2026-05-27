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
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

    val isLevel = abs(state.x) < 0.2f && abs(state.y) < 0.2f
    
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
                    ExpressiveFabMenu(
                        items = listOf(
                            Triple("Recenter", Icons.Rounded.CenterFocusStrong, { vibrationManager?.vibrateClick() }),
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
                        Icon(Icons.Rounded.Sync, contentDescription = "Calibrate")
                    }
                },
                trailingContent = {
                    clickableItem(
                        onClick = { vibrationManager?.vibrateClick() },
                        icon = { Icon(Icons.Rounded.Info, null) },
                        label = "Sensors"
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
                // High-precision Metrics Display in a Bouncy Container
                Surface(
                    color = if (isLevel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    shape = BouncyShape,
                    modifier = Modifier.padding(bottom = 56.dp),
                    border = BorderStroke(1.5.dp, if (isLevel) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeColor = if (isLevel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Icon(
                            Icons.Rounded.CenterFocusStrong,
                            null,
                            tint = activeColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(20.dp))
                        Text(
                            text = String.format(Locale.getDefault(), "X: %.1f°  Y: %.1f°", state.x, state.y),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = activeColor,
                            letterSpacing = (-1).sp
                        )
                    }
                }

                // Main Level Container with organic Squircle Shape
                Box(
                    modifier = Modifier
                        .size(340.dp)
                        .clip(SquircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f))
                        .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)), SquircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Dynamic background glow when level
                    if (isLevel && !performanceMode) {
                        val infiniteTransition = rememberInfiniteTransition(label = "LevelGlow")
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
                                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), Color.Transparent)
                                    )
                                )
                        )
                    }

                    // Precise Grid Visualization
                    val gridColor = if (isLevel) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    Canvas(modifier = Modifier.fillMaxSize().padding(48.dp)) {
                        val center = Offset(size.width / 2, size.height / 2)
                        
                        // Target Circles
                        drawCircle(gridColor, radius = 40.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
                        drawCircle(gridColor.copy(alpha = 0.2f), radius = 100.dp.toPx(), center = center, style = Stroke(1.5.dp.toPx()))
                        drawCircle(gridColor.copy(alpha = 0.1f), radius = 160.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
                        
                        // Crosshair lines
                        drawLine(gridColor, Offset(size.width * 0.45f, size.height / 2), Offset(size.width * 0.55f, size.height / 2), 2.5.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(gridColor, Offset(size.width / 2, size.height * 0.45f), Offset(size.width / 2, size.height * 0.55f), 2.5.dp.toPx(), cap = StrokeCap.Round)
                    }

                    // Liquid Bubble with high-fidelity spring motion
                    val bubbleOffsetScale = 16f
                    val bubbleX = (animX * bubbleOffsetScale).dp
                    val bubbleY = (animY * bubbleOffsetScale).dp
                    
                    Surface(
                        modifier = Modifier
                            .offset(x = -bubbleX, y = bubbleY)
                            .size(80.dp)
                            .shadow(
                                elevation = if (isLevel && !performanceMode) 32.dp else 8.dp, 
                                shape = CircleShape, 
                                spotColor = if (isLevel) MaterialTheme.colorScheme.primary else Color.Black
                            ),
                        shape = CircleShape,
                        color = if (isLevel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        border = BorderStroke(4.dp, Color.White.copy(alpha = if (isLevel) 0.7f else 0.4f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // Organic 3D specular highlight
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .offset(x = (-12).dp, y = (-12).dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color.White.copy(alpha = 0.4f), Color.Transparent)
                                        )
                                    )
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(72.dp))
                
                // Equilibrium Status with Expressive Transitions
                AnimatedContent(
                    targetState = isLevel,
                    transitionSpec = { 
                        (scaleIn(animationSpec = spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith 
                        (scaleOut() + fadeOut())
                    }, 
                    label = "LevelStatus"
                ) { level ->
                    Surface(
                        color = if (level) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                        shape = BouncyShape
                    ) {
                        Text(
                            text = if (level) "SURFACE ALIGNED" else "ADJUSTING POSITION...",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            color = if (level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            letterSpacing = 1.5.sp
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
