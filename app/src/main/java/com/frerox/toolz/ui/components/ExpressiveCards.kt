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

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.ToolzTheme

/**
 * Premium card with physics-based scaling and expressive state changes.
 */
@Composable
fun ExpressiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(32.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    elevation: Dp = 2.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val performanceMode = LocalPerformanceMode.current
    val liquidGlassEnabled = com.frerox.toolz.ui.liquidglass.LocalLiquidGlassEnabled.current
    val backdrop = com.frerox.toolz.ui.liquidglass.LocalLiquidGlassBackdrop.current
    val isDark = isSystemInDarkTheme()
    val surfaceColor = if (isDark) Color(0xFF1C1B1F) else Color(0xFFFFFBFE)
    val effectiveBackdrop = if (liquidGlassEnabled && !performanceMode) {
        backdrop ?: com.kyant.backdrop.backdrops.rememberCanvasBackdrop {
            drawRect(surfaceColor)
        }
    } else null

    if (liquidGlassEnabled && !performanceMode && effectiveBackdrop != null) {
        val glassBorder = border ?: BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
        com.frerox.toolz.ui.liquidglass.GlassCard(
            backdrop = effectiveBackdrop,
            modifier = modifier
                .then(
                    if (enabled) {
                        Modifier.bouncyClick(
                            enabled = enabled,
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    } else Modifier
                )
                .then(
                    if (glassBorder != null) {
                        Modifier.border(glassBorder, shape)
                    } else Modifier
                ),
            shape = shape,
            tint = containerColor.copy(alpha = 0.12f),
            content = content
        )
    } else {
        Surface(
            modifier = modifier
                .then(
                    if (enabled) {
                        Modifier.bouncyClick(
                            enabled = enabled,
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    } else Modifier
                ),
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = border
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(32.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    elevation: Dp = 2.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ExpressiveCard(
        onClick = {},
        modifier = modifier,
        enabled = false,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        border = border,
        content = content
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExpressiveCardPreview() {
    ToolzTheme(dynamicColor = false) {
        ExpressiveCard(
            onClick = {},
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Expressive Card",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}
