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

package com.frerox.toolz.ui.screens.pdf.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

data class ZoomState(
    val scale: Float,
    val offset: Offset,
    val onReset: () -> Unit
)

/**
 * Per-page pinch zoom. Unlike the old whole-list graphicsLayer hack, each page
 * owns its transform so scroll never breaks and zoom stays sharp (the page
 * re-renders at higher width past 1.5x via [onZoomChanged]).
 */
@Composable
fun rememberZoomableState(
    minScale: Float = 1f,
    maxScale: Float = 5f,
    onZoomChanged: (Float) -> Unit = {}
): ZoomStateHolder {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    return remember {
        ZoomStateHolder(
            getScale = { scale },
            getOffset = { offset },
            setTransform = { s, o ->
                scale = s.coerceIn(minScale, maxScale)
                offset = if (scale <= 1.02f) Offset.Zero else o
                onZoomChanged(scale)
            },
            reset = {
                scale = 1f
                offset = Offset.Zero
                onZoomChanged(1f)
            },
            getViewSize = { viewSize },
            setViewSize = { viewSize = it }
        )
    }
}

class ZoomStateHolder(
    val getScale: () -> Float,
    val getOffset: () -> Offset,
    val setTransform: (Float, Offset) -> Unit,
    val reset: () -> Unit,
    val getViewSize: () -> IntSize,
    val setViewSize: (IntSize) -> Unit
)

fun Modifier.zoomablePage(
    holder: ZoomStateHolder,
    maxScale: Float = 5f,
    enabled: Boolean = true
): Modifier = this
    .onSizeChanged { holder.setViewSize(it) }
    .pointerInput(enabled, maxScale) {
        if (!enabled) return@pointerInput
        detectTransformGestures { centroid, pan, zoom, _ ->
            val size = holder.getViewSize()
            val cur = holder.getScale()
            val ns = (cur * zoom).coerceIn(1f, maxScale)
            val sc = if (cur == 0f) 1f else ns / cur
            val focal = centroid - Offset(size.width / 2f, size.height / 2f)
            val raw = (holder.getOffset() + pan) * sc + focal * (1f - sc)
            val clamped = if (ns <= 1.02f) {
                Offset.Zero
            } else {
                val mx = (size.width * (ns - 1f)) / 2f
                val my = (size.height * (ns - 1f)) / 2f
                Offset(raw.x.coerceIn(-mx, mx), raw.y.coerceIn(-my, my))
            }
            holder.setTransform(ns, clamped)
        }
    }
    .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures(onDoubleTap = { pos ->
            val size = holder.getViewSize()
            if (holder.getScale() > 1.1f) {
                holder.reset()
            } else {
                val target = 2.5f
                val focal = pos - Offset(size.width / 2f, size.height / 2f)
                val raw = -focal * (target - 1f)
                val mx = (size.width * (target - 1f)) / 2f
                val my = (size.height * (target - 1f)) / 2f
                holder.setTransform(target, Offset(raw.x.coerceIn(-mx, mx), raw.y.coerceIn(-my, my)))
            }
        })
    }
    .graphicsLayer {
        scaleX = holder.getScale()
        scaleY = holder.getScale()
        translationX = holder.getOffset().x
        translationY = holder.getOffset().y
    }
