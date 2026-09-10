/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.media.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frerox.toolz.R
import com.frerox.toolz.ui.screens.media.PreviewBackground
import com.frerox.toolz.ui.theme.SquircleShape
import androidx.compose.ui.draw.clip

/**
 * M3 Expressive canvas for Background Remover.
 * - Checkerboard for transparent (drawn once into a cached bitmap, not every frame)
 * - Zoom/pan via transform gestures (1x..4x), pan bounded by actual content geometry
 * - Zoom survives result arrival; resets only on a new photo or compare toggle
 * - Solid color / blur preview modes, crossfaded Isolated ↔ Original
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

    // New photo → reset. Result arrival keeps the user's zoom.
    LaunchedEffect(original) {
        scale = 1f
        offset = Offset.Zero
    }
    // Toggling compare reframes — deliberate, keeps both views inspectable from 1x.
    LaunchedEffect(showOriginal) {
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

    BoxWithConstraints(
        modifier = modifier
            .clip(SquircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        // Pan bound derived from real geometry: at scale s the content overflows by
        // (s − 1) × canvasSize / 2 on each side. No magic constants.
        val maxOffset = remember(scale, maxWidth, maxHeight, density) {
            with(density) {
                val mx = ((scale - 1f) * maxWidth.toPx() / 2f).coerceAtLeast(0f)
                val my = ((scale - 1f) * maxHeight.toPx() / 2f).coerceAtLeast(0f)
                Offset(mx, my)
            }
        }
        // Latest bound without restarting the gesture detector mid-pinch.
        val maxOffsetNow by rememberUpdatedState(maxOffset)
        val checker = rememberCheckerboard()

        // Background layer — blur now shares the exact same Fit geometry + zoom as the foreground
        when {
            showOriginal -> Unit
            result != null -> {
                when (previewBackground) {
                    is PreviewBackground.Transparent -> Checkerboard(checker, Modifier.fillMaxSize())
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
                        } else Checkerboard(checker, Modifier.fillMaxSize())
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
            else -> Checkerboard(checker, Modifier.fillMaxSize())
        }

        if (active != null) {
            val description = if (showOriginal) stringResource(R.string.st_BackgroundRemover_CanvasOriginal)
            else stringResource(R.string.st_BackgroundRemover_CanvasResult)
            Crossfade(
                targetState = active,
                animationSpec = tween(180),
                label = "compare_crossfade",
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
                            if (newScale > 1.02f) {
                                scale = newScale
                                val bound = maxOffsetNow
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-bound.x, bound.x),
                                    (offset.y + pan.y).coerceIn(-bound.y, bound.y),
                                )
                            } else {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        }
                    },
            ) { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = description,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

/** Checker tile bitmap drawn once per theme — Canvas blits it instead of stroking rects per frame. */
@Composable
private fun rememberCheckerboard(): ImageBitmap {
    val c1 = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    val c2 = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
    val density = LocalDensity.current
    return remember(c1, c2, density) {
        val tilePx = with(density) { 22.dp.toPx() }.toInt().coerceAtLeast(8)
        val size = tilePx * 2
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val p1 = android.graphics.Paint().apply { color = android.graphics.Color.argb(
            (c1.alpha * 255).toInt(), (c1.red * 255).toInt(), (c1.green * 255).toInt(), (c1.blue * 255).toInt()) }
        val p2 = android.graphics.Paint().apply { color = android.graphics.Color.argb(
            (c2.alpha * 255).toInt(), (c2.red * 255).toInt(), (c2.green * 255).toInt(), (c2.blue * 255).toInt()) }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p2)
        canvas.drawRect(0f, 0f, tilePx.toFloat(), tilePx.toFloat(), p1)
        canvas.drawRect(tilePx.toFloat(), tilePx.toFloat(), size.toFloat(), size.toFloat(), p1)
        bmp.asImageBitmap()
    }
}

@Composable
fun CheckerboardPattern(modifier: Modifier = Modifier) {
    Checkerboard(rememberCheckerboard(), modifier)
}

@Composable
private fun Checkerboard(checker: ImageBitmap, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val tile = checker.width.toFloat()
        if (tile <= 0f) return@Canvas
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawImage(checker, topLeft = Offset(x, y))
                x += tile
            }
            y += tile
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
