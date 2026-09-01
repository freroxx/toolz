/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fades the top and bottom edges of the content to transparent — used on
 * scrollable lists so content doesn't hard-clip at the container bounds.
 */
fun Modifier.fadingEdges(
    top: Dp = 24.dp,
    bottom: Dp = top,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (size.height <= 0f) return@drawWithContent
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                (top.toPx() / size.height).coerceIn(0f, 0.5f) to Color.Black,
                (1f - bottom.toPx() / size.height).coerceIn(0.5f, 1f) to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * Fades the left and/or right edges of horizontally scrolling content.
 */
fun Modifier.horizontalFadingEdges(
    fadeSize: Dp = 16.dp,
    start: Boolean = true,
    end: Boolean = true,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (size.width <= 0f) return@drawWithContent
        val fade = fadeSize.toPx()
        drawRect(
            brush = Brush.horizontalGradient(
                0f to if (start) Color.Transparent else Color.Black,
                (fade / size.width).coerceAtMost(0.5f) to Color.Black,
                (1f - fade / size.width).coerceAtLeast(0.5f) to Color.Black,
                1f to if (end) Color.Transparent else Color.Black,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
