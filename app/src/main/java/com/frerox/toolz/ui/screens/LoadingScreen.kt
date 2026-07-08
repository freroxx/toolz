package com.frerox.toolz.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.toolzBackground

@Composable
fun LoadingOverlay(
    isVisible: Boolean,
    loadingMessage: String,
    loadingProgress: Float,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(400)) + scaleIn(initialScale = 1.1f, animationSpec = tween(400)),
        exit = fadeOut(tween(600, easing = EaseOutCubic)) + scaleOut(targetScale = 1.05f, animationSpec = tween(600)),
        modifier = Modifier.fillMaxSize()
    ) {
        val logoAlpha = remember { Animatable(0f) }
        val contentScale = remember { Animatable(0.9f) }

        LaunchedEffect(Unit) {
            logoAlpha.animateTo(1f, tween(800, easing = EaseOutCubic))
            contentScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background) // Ensure solid base
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
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(120.dp).clip(ExtraLargeExpressiveShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
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

                // Official Contained Loading Indicator from components
                ExpressiveContainedLoadingIndicator(
                    modifier = Modifier.size(140.dp, 80.dp),
                    progress = { loadingProgress },
                    color = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh // No transparency
                )

                Spacer(Modifier.height(32.dp))

                // Smooth fade-only transition for status messages
                AnimatedContent(
                    targetState = loadingMessage,
                    transitionSpec = {
                        fadeIn(tween(600)) togetherWith fadeOut(tween(400))
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
}
