package com.frerox.toolz.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.delay

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

    val logoAlpha = remember { Animatable(0f) }
    val contentScale = remember { Animatable(0.9f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(800, easing = EaseOutCubic))
        contentScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }

    var hapticFired by remember { mutableStateOf(false) }
    LaunchedEffect(isInitialized) {
        if (isInitialized && !hapticFired) {
            hapticFired = true
            vibrationManager?.vibrateClick()
            delay(100)
            onLoadingComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(40.dp)
                .graphicsLayer {
                    alpha = logoAlpha.value
                    scaleX = contentScale.value
                    scaleY = contentScale.value
                }
        ) {
            // Simplified Expressive Logo
            Surface(
                modifier = Modifier.size(120.dp),
                shape = ExtraLargeExpressiveShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "TOOLZ",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(48.dp))

            // Modern Contained Loading Indicator
            ExpressiveContainedLoadingIndicator(
                modifier = Modifier.size(140.dp, 80.dp),
                progress = { loadingProgress },
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(32.dp))

            AnimatedContent(
                targetState = loadingMessage,
                transitionSpec = {
                    fadeIn(tween(400)) + slideInVertically { it / 2 } togetherWith
                            fadeOut(tween(300)) + slideOutVertically { -it / 2 }
                },
                label = "msg"
            ) { msg ->
                Text(
                    text = msg.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }
        }

        // Minimal Build Info
        Text(
            text = "EXPRESSIVE EDITION",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .alpha(0.4f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
    }
}

@Preview(name = "Loading Light")
@Composable
private fun LoadingScreenLightPreview() {
    ToolzTheme(darkTheme = false) {
        LoadingScreenPreviewContent()
    }
}

@Preview(name = "Loading Dark")
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
            modifier = Modifier.padding(40.dp)
        ) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = ExtraLargeExpressiveShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("TOOLZ", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, letterSpacing = 8.sp)
            Spacer(Modifier.height(48.dp))
            ExpressiveContainedLoadingIndicator(
                modifier = Modifier.size(140.dp, 80.dp),
                progress = { 0.65f }
            )
            Spacer(Modifier.height(32.dp))
            Text("INITIALIZING SYSTEMS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        }
    }
}
