/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.media.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.screens.media.PreviewBackground
import com.frerox.toolz.ui.theme.SquircleShape
import androidx.compose.ui.draw.clip

/**
 * M3 Expressive canvas for Background Remover.
 * - Checkerboard for transparent
 * - Zoom/pan via transform gestures (1x..4x)
 * - Solid color / blur preview modes
 */
@Composable
fun BackgroundCanvas(
    original: Bitmap?,
    result: Bitmap?,
    previewBackground: PreviewBackground,
    showOriginal: Boolean,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // reset on image change
    LaunchedEffect(original, result, showOriginal) {
        scale = 1f
        offset = Offset.Zero
    }

    val active = when {
        original == null -> null
        showOriginal -> original
        result != null -> result
        else -> original
    }

    // Blur once — cached, computed off main thread via remember
    val blurredOriginal = remember(original, previewBackground) {
        if (previewBackground is PreviewBackground.Blur && original != null) blurBitmapLight(original) else null
    }

    Box(
        modifier = modifier
            .clip(SquircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        // Background layer — blur now shares the exact same Fit geometry + zoom as the foreground
        when {
            showOriginal -> Unit
            result != null -> {
                when (previewBackground) {
                    is PreviewBackground.Transparent -> CheckerboardPattern(Modifier.fillMaxSize())
                    is PreviewBackground.White -> Box(Modifier.fillMaxSize().background(Color.White))
                    is PreviewBackground.Color -> Box(Modifier.fillMaxSize().background(Color(previewBackground.color)))
                    is PreviewBackground.Blur -> {
                        if (blurredOriginal != null) {
                            // Same Fit + padding + zoom as the cutout foreground — keeps them perfectly aligned
                            Image(
                                bitmap = blurredOriginal.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y,
                                    ),
                                contentScale = ContentScale.Fit,
                            )
                            // subtle dim, covers whole canvas (not zoomed) to keep blur soft
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.06f)))
                        } else CheckerboardPattern(Modifier.fillMaxSize())
                    }
                    is PreviewBackground.CustomImage -> {
                        Image(
                            bitmap = previewBackground.bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                ),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
            else -> CheckerboardPattern(Modifier.fillMaxSize())
        }

        if (active != null) {
            Image(
                bitmap = active.asImageBitmap(),
                contentDescription = if (showOriginal) "Original photo" else "Isolated subject",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
                    .pointerInput(active) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 4f)
                            // only allow pan when zoomed
                            if (newScale > 1.02f) {
                                scale = newScale
                                // damp pan
                                offset += pan
                                // clamp loosely
                                val max = 400f * (scale - 1f)
                                offset = Offset(offset.x.coerceIn(-max, max), offset.y.coerceIn(-max, max))
                            } else {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        }
                    },
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
fun CheckerboardPattern(modifier: Modifier = Modifier) {
    // M3 tonal checker — contrasts on both light/dark
    val c1 = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    val c2 = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
    val tile = 22.dp
    Canvas(modifier = modifier) {
        val tilePx = tile.toPx()
        val cols = (size.width / tilePx).toInt() + 1
        val rows = (size.height / tilePx).toInt() + 1
        for (r in 0 until rows) {
            for (col in 0 until cols) {
                drawRect(
                    color = if ((r + col) % 2 == 0) c1 else c2,
                    topLeft = Offset(col * tilePx, r * tilePx),
                    size = Size(tilePx, tilePx),
                )
            }
        }
    }
}

// Lightweight box blur approximation for preview only
private fun blurBitmapLight(src: Bitmap): Bitmap {
    return try {
        val w = (src.width * 0.12f).toInt().coerceAtLeast(1)
        val h = (src.height * 0.12f).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        Bitmap.createScaledBitmap(small, src.width, src.height, true).also {
            if (small != it) small.recycle()
        }
    } catch (_: Throwable) {
        src
    }
}
