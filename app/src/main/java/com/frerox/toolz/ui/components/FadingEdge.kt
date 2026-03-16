package com.frerox.toolz.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Enhanced fading edge that supports both top and bottom fades.
 * Optimized for a "lighter" feel by using a more gradual gradient.
 */
fun Modifier.fadingEdges(
    top: Dp = 0.dp,
    bottom: Dp = 0.dp
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        
        val topPx = top.toPx()
        val bottomPx = bottom.toPx()
        val height = size.height
        
        if (height > 0f && (topPx > 0f || bottomPx > 0f)) {
            val topStop = (topPx / height).coerceIn(0f, 1f)
            val bottomStop = ((height - bottomPx) / height).coerceIn(0f, 1f)
            
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    // Add middle stops for a smoother, "lighter" fade
                    (topStop * 0.5f) to Color.Black.copy(alpha = 0.5f),
                    topStop to Color.Black,
                    bottomStop to Color.Black,
                    (bottomStop + (1f - bottomStop) * 0.5f) to Color.Black.copy(alpha = 0.5f),
                    1f to Color.Transparent
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

/**
 * Original brush-based implementation preserved for compatibility.
 * Updated to support the "lighter" better fading edge requirement.
 */
fun Modifier.fadingEdge(
    brush: Brush,
    length: Dp
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(
            brush = brush,
            blendMode = BlendMode.DstIn
        )
    }
