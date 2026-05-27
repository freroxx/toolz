package com.frerox.toolz.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingScreen(
    viewModel: LoadingViewModel = hiltViewModel(),
    onLoadingComplete: () -> Unit,
) {
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    val isInitialized by viewModel.isInitialized.collectAsState()
    val loadingMessage by viewModel.loadingMessage.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()

    // ── Spring-physics logo entrance ──────────────────────────────────────────
    // Starts compressed at 0.3 scale, bounces into 1.0 with LowBouncy physics.
    val logoScale = remember { Animatable(0.3f) }
    val logoAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        )
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    // ── Haptic hand-off: fire once, exactly when isInitialized flips true ────
    var hapticFired by remember { mutableStateOf(false) }
    LaunchedEffect(isInitialized) {
        if (isInitialized && !hapticFired) {
            hapticFired = true
            vibrationManager?.vibrateClick()
            // One-frame delay so the haptic is felt before the screen transition
            kotlinx.coroutines.delay(16)
            onLoadingComplete()
        }
    }

    // ── Ambient background glow – only in full-fidelity mode ─────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "LoadingAmbient")

    val glowPulse by if (!performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(3800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "GlowPulse",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    // Slow micro-rotation on the outer wavy ring
    val orbitRotation by if (!performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
            ),
            label = "OrbitRotation",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // Counter-rotating inner ring for organic feel
    val innerOrbitRotation by if (!performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 360f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000, easing = LinearEasing),
            ),
            label = "InnerOrbitRotation",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // Pulsing core scale
    val coreScale by if (!performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 0.97f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "CorePulse",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    // Loading text alpha
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "TextAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground(),
        contentAlignment = Alignment.Center,
    ) {

        // ── Deep ambient radial glow ──────────────────────────────────────────
        if (!performanceMode) {
            Box(
                modifier = Modifier
                    .size(480.dp)
                    .scale(glowPulse)
                    .blur(100.dp)
                    .alpha(0.18f)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f),
                                Color.Transparent,
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )
            // Secondary accent glow offset top-right
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-40).dp)
                    .blur(80.dp)
                    .alpha(0.12f)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.secondary,
                                Color.Transparent,
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {

            // ── Wavy progress rings + bouncy logo ─────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(260.dp)
                    .graphicsLayer {
                        alpha = logoAlpha.value
                    },
            ) {

                // Outer deterministic wavy circular progress
                if (!performanceMode) {
                    ToolzWavyCircularProgressIndicator(
                        progress = { loadingProgress },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = orbitRotation },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.25f),
                    )
                } else {
                    // Performance mode: standard determinate circular indicator
                    CircularProgressIndicator(
                        progress = { loadingProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f),
                    )
                }

                // Inner indeterminate wavy ring (secondary color, counter-rotates)
                if (!performanceMode) {
                    ToolzWavyCircularProgressIndicator(
                        modifier = Modifier
                            .size(180.dp)
                            .graphicsLayer { rotationZ = innerOrbitRotation },
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                        trackColor = Color.Transparent,
                    )
                }

                // ── Bouncy logo squircle core ─────────────────────────────────
                Surface(
                    modifier = Modifier
                        .size(108.dp)
                        .graphicsLayer {
                            scaleX = logoScale.value * coreScale
                            scaleY = logoScale.value * coreScale
                            // Subtle organic tilt that follows the outer orbit
                            rotationZ = orbitRotation * 0.06f
                        },
                    shape = SquircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = if (performanceMode) 0.dp else 24.dp,
                    border = BorderStroke(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "T",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 60.sp,
                                letterSpacing = (-2).sp,
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(56.dp))

            // ── Brand wordmark ────────────────────────────────────────────────
            Text(
                text = "TOOLZ",
                style = MaterialTheme.typography.displaySmall.copy(
                    letterSpacing = 14.sp,
                    fontWeight = FontWeight.Black,
                ),
                modifier = Modifier.graphicsLayer { alpha = logoAlpha.value },
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Wavy linear deterministic progress bar ────────────────────────
            // This is the primary "real" progress signal — deterministic, not looping.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ToolzWavyLinearProgressIndicator(
                    progress = { loadingProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Animated loading stage label
                AnimatedContent(
                    targetState = loadingMessage,
                    transitionSpec = {
                        (fadeIn(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        ) + slideInVertically { it / 3 }) togetherWith
                                (fadeOut(animationSpec = tween(120)) + slideOutVertically { -it / 3 })
                    },
                    label = "LoadingMessageTransition",
                ) { message ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        shape = SmallExpressiveShape,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        ),
                    ) {
                        Text(
                            text = message.uppercase(),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge.copy(
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Black,
                            ),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = textAlpha),
                        )
                    }
                }
            }
        }

        // ── Version badge ─────────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 52.dp)
                .graphicsLayer { alpha = logoAlpha.value },
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
            shape = SmallExpressiveShape,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
            ),
        ) {
            Text(
                text = "BUILD v1.0.9",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "Loading Screen — Light", showBackground = true)
@Composable
private fun LoadingScreenLightPreview() {
    ToolzTheme(darkTheme = false) {
        LoadingScreenPreviewContent()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(name = "Loading Screen — Dark", showBackground = true)
@Composable
private fun LoadingScreenDarkPreview() {
    ToolzTheme(darkTheme = true) {
        LoadingScreenPreviewContent()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingScreenPreviewContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
                ToolzWavyCircularProgressIndicator(
                    progress = { 0.65f },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.25f),
                )
                Surface(
                    modifier = Modifier.size(108.dp),
                    shape = SquircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 24.dp,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "T",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 60.sp,
                                letterSpacing = (-2).sp,
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(48.dp))
            Text(
                text = "TOOLZ",
                style = MaterialTheme.typography.displaySmall.copy(
                    letterSpacing = 14.sp,
                    fontWeight = FontWeight.Black,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(28.dp))
            ToolzWavyLinearProgressIndicator(
                progress = { 0.65f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(18.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                shape = SmallExpressiveShape,
            ) {
                Text(
                    text = "SYNCING INTELLIGENCE",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Black,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}