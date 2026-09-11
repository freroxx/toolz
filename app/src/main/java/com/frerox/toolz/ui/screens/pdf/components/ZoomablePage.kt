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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged

/**
 * Document-wide zoom state. The whole page list scales as one canvas, so
 * zoomed pages can never overlap each other.
 *
 * Remembered per document: [rememberDocZoomState] takes the doc key so
 * opening another file always starts unzoomed.
 */
class DocZoomState internal constructor() {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewWidth by mutableIntStateOf(0)
        internal set
    var viewHeight by mutableIntStateOf(0)
        internal set

    val zoomed: Boolean get() = scale > 1.02f

    internal fun applyZoom(zoomChange: Float, panChange: Offset, centroid: Offset, maxScale: Float) {
        if (zoomChange.isNaN() || panChange.x.isNaN()) return
        val cur = scale
        val ns = (cur * zoomChange).coerceIn(1f, maxScale)
        if (ns <= 1.02f) {
            scale = 1f
            offset = Offset.Zero
            return
        }
        val w = viewWidth.toFloat().takeIf { it > 0 } ?: return
        val h = viewHeight.toFloat().takeIf { it > 0 } ?: return
        val sc = ns / cur.coerceAtLeast(0.01f)
        val focal = centroid - Offset(w / 2f, h / 2f)
        val raw = (offset + panChange) * sc + focal * (1f - sc)
        // The canvas is taller than the viewport, so allow generous vertical
        // travel instead of clamping to the visible window.
        val mx = (w * (ns - 1f)) / 2f
        val my = (h * (ns - 1f)) / 2f + h / 2f
        scale = ns
        offset = Offset(raw.x.coerceIn(-mx, mx), raw.y.coerceIn(-my, my))
    }

    internal fun applyPan(drag: Offset) {
        if (!zoomed || drag == Offset.Zero) return
        val w = viewWidth.toFloat().takeIf { it > 0 } ?: return
        val h = viewHeight.toFloat().takeIf { it > 0 } ?: return
        val mx = (w * (scale - 1f)) / 2f
        val my = (h * (scale - 1f)) / 2f + h / 2f
        val raw = offset + drag
        offset = Offset(raw.x.coerceIn(-mx, mx), raw.y.coerceIn(-my, my))
    }
}

@Composable
fun rememberDocZoomState(docKey: Any?): DocZoomState {
    return remember(docKey) { DocZoomState() }
}

/**
 * Pinch-to-zoom for the whole document canvas. Attach once, around the
 * page list — never per page.
 *
 * - One finger at 1x: events pass through untouched, the list scrolls.
 * - Two fingers: pinch zoom around the focal point (events consumed).
 * - One finger while zoomed: pans the canvas (events consumed), so the
 *   list stays put until the user pinches back out.
 */
fun Modifier.docZoomable(
    state: DocZoomState,
    maxScale: Float = 5f
): Modifier = this
    .onSizeChanged {
        state.viewWidth = it.width
        state.viewHeight = it.height
    }
    .pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var lastCentroid: Offset? = null
            var lastDistance = 0f
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val a = pressed[0].position
                    val b = pressed[1].position
                    val centroid = (a + b) / 2f
                    val distance = (a - b).getDistance()
                    val prevCentroid = lastCentroid
                    if (prevCentroid != null && lastDistance > 0f && distance > 0f) {
                        val zoomChange = distance / lastDistance
                        val panChange = centroid - prevCentroid
                        if (zoomChange.isFinite() && zoomChange > 0f) {
                            state.applyZoom(zoomChange, panChange, centroid, maxScale)
                            event.changes.forEach { it.consume() }
                        }
                    }
                    lastCentroid = centroid
                    lastDistance = distance
                } else {
                    lastCentroid = null
                    lastDistance = 0f
                    if (pressed.size == 1 && state.zoomed) {
                        val drag = pressed[0].positionChange()
                        if (drag != Offset.Zero) {
                            state.applyPan(drag)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            } while (pressed.isNotEmpty())
        }
    }
    .graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.offset.x
        translationY = state.offset.y
    }
