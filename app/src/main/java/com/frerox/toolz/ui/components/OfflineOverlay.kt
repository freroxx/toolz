package com.frerox.toolz.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.util.OfflineState

/**
 * Redesigned Offline transition overlay.
 * Simple, Material 3 Expressive, and robust.
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
        enter = fadeIn(animationSpec = tween(400)) + scaleIn(
            initialScale = 0.92f,
            animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
        ),
        exit = fadeOut(animationSpec = tween(300)) + scaleOut(
            targetScale = 0.92f,
            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
        ),
        modifier = Modifier.zIndex(9999f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .widthIn(min = 220.dp)
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(vertical = 32.dp, horizontal = 24.dp)
                ) {
                    // Central Icon with simple scale animation
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(64.dp)
                    ) {
                        AnimatedContent(
                            targetState = state,
                            transitionSpec = {
                                (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                            },
                            label = "icon"
                        ) { s ->
                            Icon(
                                imageVector = if (s == OfflineState.OFFLINE) Icons.Rounded.CloudOff else Icons.Rounded.Cloud,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Main Text
                    Text(
                        text = if (state == OfflineState.OFFLINE) "Going offline" else "Back online",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Status Indicator (Loading or Check)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.height(40.dp)
                    ) {
                        AnimatedContent(
                            targetState = isReady,
                            transitionSpec = {
                                (fadeIn() + slideInVertically { it / 2 }).togetherWith(fadeOut() + slideOutVertically { -it / 2 })
                            },
                            label = "status"
                        ) { ready ->
                            if (ready) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Done",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                if (!performanceMode) {
                                    LoadingIndicator(
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewOffline() {
    ToolzTheme {
        OfflineTransitionOverlay(state = OfflineState.OFFLINE, visible = true, isReady = false)
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewOnlineReady() {
    ToolzTheme {
        OfflineTransitionOverlay(state = OfflineState.ONLINE, visible = true, isReady = true)
    }
}
