package com.frerox.toolz.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager

/**
 * An optimized Material 3 Expressive bouncy click modifier.
 * Uses unified interaction streams to deliver fluid, elastic physics
 * and synchronized tactical haptic feedback.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bouncyClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.95f, // Tuned to the M3 Expressive sweet spot
    haptic: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) = composed {
    val view = LocalView.current
    val hapticEnabled = LocalHapticEnabled.current
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    // Unified interaction tracking system
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Premium M3 Expressive Elastic Physics
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !performanceMode) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f, // Delivers a clean, responsive overshoot bounce
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "M3ExpressiveBouncyScale"
    )

    // Tactile Feedback on initial touch-down
    LaunchedEffect(isPressed) {
        if (isPressed && enabled && haptic && hapticEnabled) {
            vibrationManager?.vibrateTick()
        }
    }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                if (haptic && hapticEnabled) {
                    if (vibrationManager != null) {
                        vibrationManager.vibrateClick()
                    } else {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }
                onClick()
            },
            onLongClick = onLongClick?.let { longClickAction ->
                {
                    if (haptic && hapticEnabled) {
                        if (vibrationManager != null) {
                            vibrationManager.vibrateLongClick()
                        } else {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                    longClickAction()
                }
            }
        )
}