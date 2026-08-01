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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Remembers a [DragDropState] scoped to [lazyListState]. Rebuilt only if the
 * list identity changes; the [onMove] callback is kept fresh independently so
 * passing a new lambda every recomposition doesn't reset an in-flight drag.
 */
@Composable
fun rememberDragDropState(
    lazyListState: LazyListState = rememberLazyListState(),
    onMove: (Int, Int) -> Unit
): DragDropState {
    val scope = rememberCoroutineScope()
    val state = remember(lazyListState) {
        DragDropState(state = lazyListState, scope = scope)
    }
    SideEffect { state.onMoveState = onMove }
    return state
}

/**
 * Drives long-press drag-to-reorder for a [LazyColumn]/[LazyListState].
 *
 * Compared to a naive implementation, this version:
 *  - Runs auto-scroll as a single supervised job that's cancelled the instant
 *    the finger leaves the scroll zone or the drag ends, instead of spawning
 *    a new [scrollBy] coroutine on every pointer-move event.
 *  - Tracks the dragged item's live visual offset as a plain
 *    [mutableFloatStateOf] write instead of routing every pointer-move
 *    through a launched coroutine calling `Animatable.snapTo`. A drag can
 *    easily fire dozens of move events per second; suspending and
 *    dispatching a coroutine for each one is real, avoidable overhead for
 *    what is just an instantaneous value write competing with the gesture
 *    thread. [Animatable] is kept, but only for the one moment that actually
 *    benefits from animation: the spring back to rest after a drop.
 *  - Only commits a swap once the dragged item's center has cleared a real
 *    margin past the target's center (hysteresis), so two same-size rows
 *    don't flicker back and forth every frame right at the boundary — the
 *    visible jitter that reads as an unstable drag.
 *  - Re-resolves the dragged item's live position every frame instead of
 *    trusting stale layout info, so it degrades gracefully if the item is
 *    scrolled to the edge of the visible window mid-gesture.
 *  - Exposes [isDragging] / [draggingItemIndex] as the single source of
 *    truth for row-level shape/elevation/scale treatments — no parallel
 *    pressed-state tracking needed in the row composable.
 */
class DragDropState(
    val state: LazyListState,
    private val scope: CoroutineScope
) {
    internal var onMoveState: (Int, Int) -> Unit by mutableStateOf({ _, _ -> })

    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    /**
     * Set to the just-dropped item's index for the duration of the release
     * spring-back only, so [dragDropItem] keeps applying [draggedOffset] to
     * that row until it's visually settled, instead of the offset becoming
     * a no-op the instant [draggingItemIndex] clears on drop.
     */
    private var releasingItemIndex by mutableStateOf<Int?>(null)

    /**
     * The row index [dragDropItem] should currently treat as "lifted" —
     * either actively being dragged, or still springing back into place
     * right after release.
     */
    internal val activeItemIndex: Int? get() = draggingItemIndex ?: releasingItemIndex

    val isDragging: Boolean get() = draggingItemIndex != null

    /**
     * Live visual offset (px) applied to the dragged item via [dragDropItem].
     * Written synchronously on every pointer move — no coroutine per frame.
     * Only animated (via [releaseAnimatable]) for the release spring-back.
     */
    internal var draggedOffset by mutableFloatStateOf(0f)
        private set

    /** Backs the spring-back-to-rest animation on drop; idle while dragging. */
    private val releaseAnimatable = Animatable(0f)
    private var releaseJob: Job? = null

    private var anchorOffset = 0
    private var anchorSize = 0
    private var autoScrollJob: Job? = null

    private fun visibleItemInfoOf(index: Int): LazyListItemInfo? =
        state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

    fun onDragStart(offset: Offset, haptic: HapticFeedback? = null) {
        // A release animation from a previous drag could still be running;
        // a fresh pickup always wins and starts from a clean slate.
        releaseJob?.cancel()
        releasingItemIndex = null

        val hit = state.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
            ?: return

        draggingItemIndex = hit.index
        anchorOffset = hit.offset
        anchorSize = hit.size
        draggedOffset = 0f
        haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onDrag(dragAmount: Offset, haptic: HapticFeedback? = null) {
        val currentIndex = draggingItemIndex ?: return
        draggedOffset += dragAmount.y

        val layoutInfo = state.layoutInfo
        val draggingItem = visibleItemInfoOf(currentIndex)

        if (draggingItem != null) {
            anchorOffset = draggingItem.offset
            anchorSize = draggingItem.size

            val currentTop = draggingItem.offset + draggedOffset
            val middle = currentTop + draggingItem.size / 2f

            val target = layoutInfo.visibleItemsInfo.firstOrNull { item ->
                item.index != currentIndex &&
                    middle.toInt() in item.offset..(item.offset + item.size)
            }

            if (target != null) {
                val targetMiddle = target.offset + target.size / 2f
                // Require the dragged item's center to have crossed well
                // past the target's own center rather than merely touching
                // its bounds — this is what stops a swap-back-and-forth
                // flicker when the pointer sits near the row boundary.
                val crossedEnough = abs(middle - targetMiddle) < target.size * 0.33f
                if (crossedEnough) {
                    // Shrink the live offset by exactly the distance the
                    // swap moves the anchor, so the item's on-screen
                    // position doesn't pop the moment the swap commits.
                    val displacement = target.offset - draggingItem.offset
                    onMoveState(currentIndex, target.index)
                    draggingItemIndex = target.index
                    draggedOffset -= displacement
                    // Note: newer Compose UI (1.8+) adds a dedicated SegmentTick
                    // haptic that reads better here. Swap this in once the
                    // project's Compose UI version guarantees it's available.
                    haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }

        updateAutoScroll(layoutInfo)
    }

    private fun updateAutoScroll(layoutInfo: LazyListLayoutInfo) {
        val scrollZone = 96f
        val maxScrollPerFrame = 18f
        val viewportStart = layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
        val currentTop = anchorOffset + draggedOffset
        val currentBottom = currentTop + anchorSize

        val distanceFromTop = currentTop - viewportStart
        val distanceFromBottom = viewportEnd - currentBottom

        val scrollDelta = when {
            distanceFromTop < scrollZone ->
                -maxScrollPerFrame * (1f - (distanceFromTop.coerceAtLeast(0f) / scrollZone))
            distanceFromBottom < scrollZone ->
                maxScrollPerFrame * (1f - (distanceFromBottom.coerceAtLeast(0f) / scrollZone))
            else -> 0f
        }

        if (scrollDelta == 0f) {
            autoScrollJob?.cancel()
            autoScrollJob = null
            return
        }

        if (autoScrollJob?.isActive == true) return

        autoScrollJob = scope.launch {
            while (isActive && draggingItemIndex != null) {
                val stillVisible = draggingItemIndex?.let { visibleItemInfoOf(it) } != null
                if (!stillVisible) break
                state.scrollBy(scrollDelta)
                delay(16)
            }
        }
    }

    fun onDragInterrupted() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        val droppedIndex = draggingItemIndex
        draggingItemIndex = null

        if (droppedIndex == null) {
            draggedOffset = 0f
            releasingItemIndex = null
            return
        }

        // Keep applying the offset to the row that was just dropped until
        // the spring-back finishes settling, so it doesn't visually snap
        // the instant draggingItemIndex clears.
        releasingItemIndex = droppedIndex

        // Spring the last live offset back to rest. This is the one place
        // an actual animation (and therefore a coroutine) is worth it, since
        // it only runs once per drop rather than once per pointer move.
        releaseJob?.cancel()
        releaseJob = scope.launch {
            releaseAnimatable.snapTo(draggedOffset)
            releaseAnimatable.animateTo(
                0f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
            ) {
                draggedOffset = value
            }
            releasingItemIndex = null
        }
    }
}

/** Applies the live drag transform + elevation/scale treatment to a reorderable row. */
fun Modifier.dragDropItem(
    index: Int,
    state: DragDropState
): Modifier = this.then(
    Modifier
        .zIndex(if (index == state.activeItemIndex) 10f else 0f)
        .graphicsLayer {
            // The offset applies for both the active drag and the brief
            // release spring-back that follows it, so the row doesn't pop
            // back to zero the instant it's dropped.
            if (index == state.activeItemIndex) {
                translationY = state.draggedOffset
            }
            // The "picked up" chrome (scale/alpha/shadow) is tied to the
            // actual drag only — it should look lifted while held, and
            // settle immediately on release while the position itself
            // keeps easing back underneath it.
            if (index == state.draggingItemIndex) {
                scaleX = 1.035f
                scaleY = 1.035f
                alpha = 0.97f
                shadowElevation = 18f
            } else {
                shadowElevation = 0f
            }
        }
)

/** Installs the long-press-to-drag gesture recognizer on a reorderable column. */
fun Modifier.dragDropColumn(
    dragDropState: DragDropState,
    haptic: HapticFeedback? = null
): Modifier = this.then(
    Modifier.pointerInput(dragDropState) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset -> dragDropState.onDragStart(offset, haptic) },
            onDrag = { change, dragAmount ->
                change.consume()
                dragDropState.onDrag(dragAmount, haptic)
            },
            onDragEnd = { dragDropState.onDragInterrupted() },
            onDragCancel = { dragDropState.onDragInterrupted() }
        )
    }
)
