package com.frerox.toolz.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.util.OfflineState

/**
 * Offline transition overlay — pure M3 Expressive.
 *
 * Phases:
 *  1. Loading  — ContainedLoadingIndicator (M3 Expressive) + descriptive label
 *  2. Ready    — Check icon + "Ready" label via spring-scale AnimatedContent
 *  3. Auto-dismiss after 1.2 s once [isReady] = true
 *
 * Design choices:
 *  • Scrim uses M3 surface tonal layering instead of raw black overlay
 *  • Central card uses SquircleShape / surfaceContainerHigh for M3 compliance
 *  • Cloud icon pulses with spring (M3 MotionTokens energy), respects [performanceMode]
 *  • All enter/exit transitions use M3-spec spring dampingRatio + stiffness pairs
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OfflineTransitionOverlay(
    state: OfflineState,
    visible: Boolean,
    isReady: Boolean,
    performanceMode: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                    initialScale = 0.94f),
        exit    = fadeOut(tween(320)) +
                scaleOut(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
                    targetScale = 1.04f),
        modifier = Modifier.zIndex(9999f),
    ) {
        // M3 scrim — surface tonal rather than raw black
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.46f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier            = Modifier.offset(y = (-32).dp),
            ) {

                // ── Pulsing cloud icon ────────────────────────────────────────
                CloudPulseIcon(
                    state           = state,
                    isReady         = isReady,
                    performanceMode = performanceMode,
                )

                // ── Status card ───────────────────────────────────────────────
                AnimatedContent(
                    targetState = isReady,
                    transitionSpec = {
                        // Slide up & fade in on "Ready"; slide up & fade out on loading
                        (slideInVertically(
                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                        ) { it / 2 } + fadeIn(tween(260)))
                            .togetherWith(
                                slideOutVertically(
                                    spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
                                ) { -it / 2 } + fadeOut(tween(200))
                            )
                    },
                    label = "statusCard",
                ) { ready ->
                    StatusCard(
                        state    = state,
                        isReady  = ready,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CLOUD PULSE ICON
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CloudPulseIcon(
    state: OfflineState,
    isReady: Boolean,
    performanceMode: Boolean,
) {
    // Spring-based icon scale — bouncy entry, clean exit
    val iconScale by animateFloatAsState(
        targetValue   = if (isReady) 1.1f else 1f,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label         = "iconScale",
    )

    // Ripple ring — only when loading and not in perf mode
    val ripple = rememberInfiniteTransition(label = "ripple")
    val rippleScale by ripple.animateFloat(
        initialValue  = 1f, targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label         = "rippleScale",
    )
    val rippleAlpha by ripple.animateFloat(
        initialValue  = 0.45f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label         = "rippleAlpha",
    )

    Box(contentAlignment = Alignment.Center) {
        // Ripple ring
        if (!performanceMode && !isReady) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        scaleX = rippleScale
                        scaleY = rippleScale
                        alpha  = rippleAlpha
                    }
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), CircleShape)
            )
        }

        // Icon container
        Surface(
            shape           = CircleShape,
            color           = MaterialTheme.colorScheme.primary,
            modifier        = Modifier
                .size(80.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
            shadowElevation = 14.dp,
            tonalElevation  = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Cross-fade icon when state flips
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                                scaleIn(spring(Spring.DampingRatioMediumBouncy), initialScale = 0.8f))
                            .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.8f))
                    },
                    label = "cloudIcon",
                ) { s ->
                    Icon(
                        imageVector        = if (s == OfflineState.OFFLINE) Icons.Rounded.CloudOff else Icons.Rounded.Cloud,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onPrimary,
                        modifier           = Modifier.size(40.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STATUS CARD — loading OR ready
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusCard(
    state: OfflineState,
    isReady: Boolean,
) {
    Surface(
        shape          = RoundedCornerShape(28.dp),
        color          = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier              = Modifier
                .padding(horizontal = 28.dp, vertical = 18.dp)
                .animateContentSize(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isReady) {
                // Check mark badge
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape    = CircleShape,
                    color    = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector        = Icons.Rounded.Check,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text       = "Ready",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                // M3 Expressive ContainedLoadingIndicator
                ExpressiveContainedLoadingIndicator(
                    modifier = Modifier.size(36.dp),
                    color    = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Column {
                    Text(
                        text       = if (state == OfflineState.OFFLINE) "Going offline" else "Going online",
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color      = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        text       = if (state == OfflineState.OFFLINE)
                            "Hiding network features…"
                        else
                            "Restoring network features…",
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEWS
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Overlay — Loading (Offline)", showBackground = true)
@Composable
private fun PreviewLoadingOffline() {
    ToolzTheme {
        Box(Modifier.fillMaxSize()) {
            OfflineTransitionOverlay(
                state   = OfflineState.OFFLINE,
                visible = true,
                isReady = false,
            )
        }
    }
}

@Preview(name = "Overlay — Ready (Online)", showBackground = true)
@Composable
private fun PreviewReadyOnline() {
    ToolzTheme {
        Box(Modifier.fillMaxSize()) {
            OfflineTransitionOverlay(
                state   = OfflineState.ONLINE,
                visible = true,
                isReady = true,
            )
        }
    }
}

@Preview(name = "Overlay — Dark / Loading", showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES or android.content.res.Configuration.UI_MODE_NIGHT_MASK)
@Composable
private fun PreviewDarkLoading() {
    ToolzTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize()) {
            OfflineTransitionOverlay(
                state   = OfflineState.ONLINE,
                visible = true,
                isReady = false,
            )
        }
    }
}