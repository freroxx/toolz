package com.frerox.toolz.ui.components.cleaner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M3-Expressive style circular selector. Replaces square checkboxes
 * across the cleaner for a softer, modern feel.
 */
@Composable
fun CircleSelect(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 26.dp
) {
    val cs = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        if (checked) cs.primary else cs.surfaceContainerHighest,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "circleFill"
    )
    val scale by animateFloatAsState(
        if (checked) 1f else 0.94f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "circlePop"
    )
    val alpha = if (enabled) 1f else 0.38f
    Surface(
        shape = CircleShape,
        color = fill.copy(alpha = if (checked) alpha else 1f),
        border = if (!checked) BorderStroke(2.dp, cs.outlineVariant.copy(alpha = alpha)) else null,
        tonalElevation = if (checked) 2.dp else 0.dp,
        modifier = modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .scale(scale)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (checked) {
                Icon(
                    Icons.Rounded.Check, contentDescription = null,
                    tint = cs.onPrimary, modifier = Modifier.size(size * 0.58f)
                )
            }
        }
    }
}
