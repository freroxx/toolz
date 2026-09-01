/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton

enum class ErrorType { NO_RESULTS, OFFLINE, DNS_ERROR, NETWORK_ERROR, RATE_LIMITED, GENERIC }

/**
 * Animated error/empty state with a breathing icon halo and a primary action
 * that's chosen based on [errorType] — e.g. "Fix DNS settings" for
 * [ErrorType.DNS_ERROR] — falling back to a generic retry otherwise.
 */
@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    errorType: ErrorType = ErrorType.GENERIC,
    onReturnToDashboard: (() -> Unit)? = null,
    onOpenDnsSettings: (() -> Unit)? = null,
    onOpenEngineSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it / 4 },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            val breathe = rememberInfiniteTransition(label = "breathe")
            val pulse by breathe.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "pulse",
            )

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulse)
                        .background(errorContainerFor(errorType).copy(alpha = 0.18f), CircleShape),
                )
                Surface(
                    shape = CircleShape,
                    color = errorContainerFor(errorType).copy(alpha = 0.55f),
                    modifier = Modifier.size(68.dp).scale(pulse * 0.97f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconFor(errorType),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            tint = errorTintFor(errorType),
                        )
                    }
                }
            }

            ExpressiveCard(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
                elevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 20.sp)
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                when {
                    errorType == ErrorType.OFFLINE && onReturnToDashboard != null -> {
                        ToolzExpressiveButton(onClick = onReturnToDashboard, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Go home")
                        }
                    }
                    errorType == ErrorType.DNS_ERROR && onOpenDnsSettings != null -> {
                        ToolzExpressiveButton(onClick = onOpenDnsSettings, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                            Icon(Icons.Rounded.Dns, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Fix DNS settings")
                        }
                    }
                    errorType == ErrorType.RATE_LIMITED && onOpenEngineSettings != null -> {
                        ToolzExpressiveButton(onClick = onOpenEngineSettings, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                            Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Switch search engine")
                        }
                    }
                    else -> Unit
                }

                ToolzOutlinedExpressiveButton(onClick = onRetry, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(retryLabelFor(errorType))
                }
            }
        }
    }
}

@Composable
private fun errorContainerFor(type: ErrorType): Color = when (type) {
    ErrorType.OFFLINE, ErrorType.NETWORK_ERROR -> MaterialTheme.colorScheme.errorContainer
    ErrorType.DNS_ERROR, ErrorType.RATE_LIMITED -> MaterialTheme.colorScheme.tertiaryContainer
    ErrorType.NO_RESULTS -> MaterialTheme.colorScheme.secondaryContainer
    ErrorType.GENERIC -> MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
private fun errorTintFor(type: ErrorType): Color = when (type) {
    ErrorType.OFFLINE, ErrorType.NETWORK_ERROR -> MaterialTheme.colorScheme.error
    ErrorType.DNS_ERROR, ErrorType.RATE_LIMITED -> MaterialTheme.colorScheme.tertiary
    ErrorType.NO_RESULTS -> MaterialTheme.colorScheme.secondary
    ErrorType.GENERIC -> MaterialTheme.colorScheme.onSurface
}

private fun iconFor(type: ErrorType): ImageVector = when (type) {
    ErrorType.OFFLINE, ErrorType.NETWORK_ERROR -> Icons.Rounded.CloudOff
    ErrorType.DNS_ERROR -> Icons.Rounded.Dns
    ErrorType.RATE_LIMITED -> Icons.Rounded.HourglassEmpty
    ErrorType.NO_RESULTS -> Icons.Rounded.SearchOff
    ErrorType.GENERIC -> Icons.Rounded.ErrorOutline
}

private fun retryLabelFor(type: ErrorType): String = when (type) {
    ErrorType.OFFLINE -> "Retry connection"
    ErrorType.NETWORK_ERROR -> "Check connection"
    ErrorType.DNS_ERROR -> "Retry DNS query"
    ErrorType.RATE_LIMITED -> "Try again"
    ErrorType.NO_RESULTS -> "Try again"
    ErrorType.GENERIC -> "Try again"
}
