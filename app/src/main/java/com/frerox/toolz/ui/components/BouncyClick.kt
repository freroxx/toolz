package com.frerox.toolz.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager

enum class ButtonState { Pressed, Idle }

fun Modifier.bouncyClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.94f,
    haptic: Boolean = true,
    onClick: () -> Unit
) = composed {
    var buttonState by remember { mutableStateOf(ButtonState.Idle) }
    val view = LocalView.current
    val hapticEnabled = LocalHapticEnabled.current
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current
    
    val scale by animateFloatAsState(
        targetValue = if (buttonState == ButtonState.Pressed && enabled && !performanceMode) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "BouncyClickScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = remember { MutableInteractionSource() },
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
            }
        )
        .pointerInput(enabled) {
            if (!enabled || performanceMode) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(false)
                    buttonState = ButtonState.Pressed
                    waitForUpOrCancellation()
                    buttonState = ButtonState.Idle
                }
            }
        }
}
