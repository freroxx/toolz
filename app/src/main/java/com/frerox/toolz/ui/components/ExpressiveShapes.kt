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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shapes.
 * These are utilized for premium bouncy and organic UI elements.
 */

val BouncyShape = RoundedCornerShape(32.dp)
val SquircleShape = RoundedCornerShape(28.dp)
val ExtraLargeExpressiveShape = RoundedCornerShape(48.dp)
val LargeExpressiveShape = RoundedCornerShape(36.dp)
val MediumExpressiveShape = RoundedCornerShape(28.dp)
val SmallExpressiveShape = RoundedCornerShape(20.dp)

/**
 * Applies a morphing effect to the shape based on interaction.
 */
@Composable
fun Modifier.expressiveMorphing(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "morphScale"
    )
    val rotation by animateFloatAsState(
        targetValue = if (isPressed && enabled) 2f else 0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
        label = "morphRotation"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        rotationZ = rotation
    }
}
