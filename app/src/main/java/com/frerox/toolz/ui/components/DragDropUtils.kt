package com.frerox.toolz.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState = rememberLazyListState(),
    onMove: (Int, Int) -> Unit
): DragDropState {
    val scope = rememberCoroutineScope()
    return remember(lazyListState) {
        DragDropState(
            state = lazyListState,
            onMove = onMove,
            scope = scope
        )
    }
}

class DragDropState(
    val state: LazyListState,
    private val onMove: (Int, Int) -> Unit,
    private val scope: CoroutineScope
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    internal var draggedDistance by mutableFloatStateOf(0f)
    internal var draggingItemOffset by mutableFloatStateOf(0f)

    private var initialItemOffset = 0
    
    fun onDragStart(offset: Offset) {
        state.layoutInfo.visibleItemsInfo
            .find { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
            ?.let {
                draggingItemIndex = it.index
                initialItemOffset = it.offset
            }
    }

    fun onDragInterrupted() {
        draggingItemIndex = null
        draggedDistance = 0f
        draggingItemOffset = 0f
    }

    fun onDrag(dragAmount: Offset) {
        draggedDistance += dragAmount.y
        
        val currentDraggingItemIndex = draggingItemIndex ?: return
        val layoutInfo = state.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val draggingItem = visibleItems.find { it.index == currentDraggingItemIndex } ?: return

        val currentOffset = draggingItem.offset + draggedDistance
        val middleOffset = currentOffset + draggingItem.size / 2f

        val targetItem = visibleItems.find { item ->
            middleOffset.toInt() in item.offset..item.offset + item.size &&
                    currentDraggingItemIndex != item.index
        }

        if (targetItem != null) {
            val displacement = targetItem.offset - draggingItem.offset
            onMove(currentDraggingItemIndex, targetItem.index)
            draggingItemIndex = targetItem.index
            draggedDistance -= displacement
        }
        
        // Auto-scroll
        val scrollThreshold = 100f
        val containerHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val distanceFromTop = currentOffset - layoutInfo.viewportStartOffset
        val distanceFromBottom = layoutInfo.viewportEndOffset - (currentOffset + draggingItem.size)
        
        if (distanceFromTop < scrollThreshold) {
            scope.launch {
                state.scrollBy(-(scrollThreshold - distanceFromTop) / 10f)
            }
        } else if (distanceFromBottom < scrollThreshold) {
            scope.launch {
                state.scrollBy((scrollThreshold - distanceFromBottom) / 10f)
            }
        }
    }
}

fun Modifier.dragDropItem(
    index: Int,
    state: DragDropState
): Modifier = this.then(
    Modifier
        .zIndex(if (index == state.draggingItemIndex) 10f else 0f)
        .graphicsLayer {
            val isDragging = index == state.draggingItemIndex
            if (isDragging) {
                translationY = state.draggedDistance
                scaleX = 1.04f
                scaleY = 1.04f
                alpha = 0.95f
                shadowElevation = 16f
            }
        }
)

fun Modifier.dragDropColumn(
    dragDropState: DragDropState,
    haptic: HapticFeedback? = null
): Modifier = this.then(
    Modifier.pointerInput(dragDropState) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset -> 
                dragDropState.onDragStart(offset)
                haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                dragDropState.onDrag(dragAmount)
            },
            onDragEnd = { dragDropState.onDragInterrupted() },
            onDragCancel = { dragDropState.onDragInterrupted() }
        )
    }
)
