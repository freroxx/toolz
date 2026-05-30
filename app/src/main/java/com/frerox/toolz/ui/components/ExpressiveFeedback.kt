package com.frerox.toolz.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.util.VibrationManager

@Immutable
class ToolzHapticFeedback internal constructor(
    private val vibrationManager: VibrationManager?,
    private val enabled: Boolean,
    private val view: View,
) {
    fun click() {
        perform(
            localFeedback = HapticFeedbackConstants.KEYBOARD_TAP,
            vibration = { it.vibrateClick() },
        )
    }

    fun tick() {
        perform(
            localFeedback = HapticFeedbackConstants.CLOCK_TICK,
            vibration = { it.vibrateTick() },
        )
    }

    fun longClick() {
        perform(
            localFeedback = HapticFeedbackConstants.LONG_PRESS,
            vibration = { it.vibrateLongClick() },
        )
    }

    fun success() {
        perform(
            localFeedback = HapticFeedbackConstants.CONFIRM,
            vibration = { it.vibrateSuccess() },
        )
    }

    fun error() {
        perform(
            localFeedback = HapticFeedbackConstants.REJECT,
            vibration = { it.vibrateError() },
        )
    }

    private inline fun perform(
        localFeedback: Int,
        vibration: (VibrationManager) -> Unit,
    ) {
        if (!enabled) return
        val manager = vibrationManager
        if (manager != null) {
            vibration(manager)
        } else {
            view.performHapticFeedback(localFeedback)
        }
    }
}

@Composable
fun rememberToolzHapticFeedback(): ToolzHapticFeedback {
    val vibrationManager = LocalVibrationManager.current
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    return remember(vibrationManager, hapticEnabled, view) {
        ToolzHapticFeedback(
            vibrationManager = vibrationManager,
            enabled = hapticEnabled,
            view = view,
        )
    }
}
