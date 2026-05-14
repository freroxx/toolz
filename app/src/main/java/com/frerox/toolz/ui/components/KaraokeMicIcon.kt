package com.frerox.toolz.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.screens.media.rememberDynamicColors
import com.frerox.toolz.ui.theme.LocalPerformanceMode

// ─────────────────────────────────────────────────────────────────────────────
// KaraokeMicIcon
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun KaraokeMicIcon(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    thumbnailUri: String? = null,
    isLoading: Boolean = false
) {
    val dynamicColors = rememberDynamicColors(thumbnailUri)
    val performanceMode = LocalPerformanceMode.current
    val inf = rememberInfiniteTransition(label = "micIconInf")

    // Outer ripple ring (only when active + not in performance mode)
    val rippleScale by inf.animateFloat(
        1f, 1.55f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "rippleScale"
    )
    val rippleAlpha by inf.animateFloat(
        0.45f, 0f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "rippleAlpha"
    )

    // Icon scale spring on active toggle
    val baseScale by animateFloatAsState(
        targetValue = if (isActive) 1.14f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "micBaseScale"
    )

    // Glow alpha
    val glowAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.55f else 0f,
        animationSpec = tween(500),
        label = "micGlow"
    )

    // Container color
    val containerColor by animateColorAsState(
        targetValue = if (isActive) dynamicColors.primary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        animationSpec = tween(300),
        label = "micContainerColor"
    )

    // Icon tint
    val iconColor by animateColorAsState(
        targetValue = if (isActive) dynamicColors.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        animationSpec = tween(300),
        label = "micIconColor"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(baseScale)
            .bouncyClick { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Ripple ring
        if (isActive && !performanceMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(rippleScale)
                    .background(
                        dynamicColors.primary.copy(alpha = rippleAlpha),
                        CircleShape
                    )
            )
        }

        // Radial glow halo
        if (isActive && !performanceMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                dynamicColors.primary.copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
        }

        // Drop shadow when active
        val shadowMod = if (isActive) {
            Modifier.shadow(
                elevation = 10.dp,
                shape = CircleShape,
                spotColor = dynamicColors.primary.copy(alpha = 0.5f),
                ambientColor = dynamicColors.primary.copy(alpha = 0.2f)
            )
        } else Modifier

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .then(shadowMod),
            shape = CircleShape,
            color = containerColor,
            onClick = onClick
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(iconSize + 8.dp),
                        color = dynamicColors.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = if (isActive) "Exit Karaoke" else "Enter Karaoke",
                        tint = iconColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SmallKaraokeMicIcon — compact variant used in Now Playing row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SmallKaraokeMicIcon(
    isVisible: Boolean,
    isActive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailUri: String? = null,
    isLoading: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "smallMicScale"
    )

    if (isVisible || scale > 0.01f) {
        KaraokeMicIcon(
            isActive = isActive,
            onClick = onClick,
            modifier = modifier.scale(scale),
            size = 36.dp,
            iconSize = 18.dp,
            thumbnailUri = thumbnailUri,
            isLoading = isLoading
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LiveMicIcon — pulsing variant used inside active karaoke as a status badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LiveMicIcon(
    isRecording: Boolean,
    thumbnailUri: String? = null,
    modifier: Modifier = Modifier
) {
    val dynamicColors = rememberDynamicColors(thumbnailUri)
    val performanceMode = LocalPerformanceMode.current
    val inf = rememberInfiniteTransition(label = "liveMicInf")

    val pulse by inf.animateFloat(
        1f, 1.25f,
        infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "livePulse"
    )

    val color by animateColorAsState(
        targetValue = if (isRecording) Color.Red else dynamicColors.primary,
        animationSpec = tween(400),
        label = "liveMicColor"
    )

    Box(
        modifier = modifier
            .size(32.dp)
            .scale(if (isRecording && !performanceMode) pulse else 1f),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording && !performanceMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color.copy(alpha = 0.18f), CircleShape)
            )
        }
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Mic,
                    contentDescription = if (isRecording) "Recording" else "Mic",
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}