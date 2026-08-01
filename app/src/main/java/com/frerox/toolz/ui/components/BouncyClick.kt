/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.ui.components

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
import com.frerox.toolz.ui.theme.LocalPerformanceMode

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
    val performanceMode = LocalPerformanceMode.current
    val hapticFeedback = rememberToolzHapticFeedback()

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
                if (haptic) {
                    hapticFeedback.click()
                }
                onClick()
            },
            onLongClick = onLongClick?.let { longClickAction ->
                {
                    if (haptic) {
                        hapticFeedback.longClick()
                    }
                    longClickAction()
                }
            }
        )
}
