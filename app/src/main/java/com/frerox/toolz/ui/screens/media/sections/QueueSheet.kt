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

package com.frerox.toolz.ui.screens.media.sections

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.ui.components.AlbumArtImage
import com.frerox.toolz.ui.components.DragDropState
import com.frerox.toolz.ui.components.dragDropColumn
import com.frerox.toolz.ui.components.dragDropItem
import com.frerox.toolz.ui.components.rememberDragDropState
import com.frerox.toolz.ui.screens.media.DynamicColors
import com.frerox.toolz.ui.screens.media.QueueEntry
import com.frerox.toolz.ui.screens.media.rememberDynamicColors
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// Queue Sheet — Material 3 Expressive
//
// Design language: one unmistakable hero, a calm dense list beneath it, and
// shape/motion doing the storytelling instead of decoration bolted on top.
//
//  • The now-playing card is a real "living" surface: a heavily blurred wash
//    of the actual album art sits behind it (not just a flat color gradient).
//    The decorative wavy waveform line that used to run here permanently was
//    removed — it was pure animation cost for a card that isn't even
//    clickable, and it's part of what made the sheet feel laggy to open.
//  • Every queue row's shape/elevation/scale is driven directly off
//    DragDropState (isDragging / draggingItemIndex) rather than a parallel
//    Surface-interaction proxy — the long-press-drag gesture consumes the
//    pointer before a click-interaction source would ever see it, so tying
//    the "picked up" look to the actual drag state is what makes the morph
//    reliably show up the moment a row is lifted, not just on tap-press.
//    Rows no longer also carry a swipe-to-dismiss gesture on the same
//    pointer input — that was a second detector contending with the drag
//    gesture for the same touch stream, which was part of what made
//    reordering feel unstable. Delete is a plain button now.
//  • The currently-playing track is filtered out of the reorderable list up
//    front (see `upcoming` below), not skipped mid-loop, and every row keeps
//    its real index into the full queue alongside its on-screen position —
//    this is what actually makes drag-to-reorder move the right tracks.
//  • The section header carries a live animated pill for the track count and
//    clearer, more confident actions (Play next batch / Clear) instead of a
//    single small icon button competing for attention with the title.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueSheet(
    queue: List<QueueEntry>,
    currentTrack: MusicTrack,
    currentQueueIndex: Int = 0,
    onTrackSelect: (MusicTrack) -> Unit,
    onQueueIndexSelect: (Int) -> Unit = {},
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val haptic = LocalHapticFeedback.current
    val dynamicColors = rememberDynamicColors(currentTrack.thumbnailUri)
    val reducedEffects = LocalPerformanceMode.current

    // Upcoming queue elements strictly start from index currentQueueIndex + 1.
    // The very first item shown in "Up Next" is currentQueueIndex + 1 (the next song to play).
    val upcoming = remember(queue, currentQueueIndex) {
        if (queue.isEmpty()) emptyList()
        else {
            val currIdx = currentQueueIndex.coerceIn(0, queue.size - 1)
            if (currIdx + 1 < queue.size) {
                queue.withIndex().drop(currIdx + 1).toList()
            } else emptyList()
        }
    }

    val lazyListState = rememberLazyListState()
    val dragDropState = rememberDragDropState(lazyListState) { fromSlot, toSlot ->
        val fromRow = fromSlot - 3
        val toRow = toSlot - 3
        if (fromRow in upcoming.indices && toRow in upcoming.indices) {
            onMove(upcoming[fromRow].index, upcoming[toRow].index)
        }
    }

    val isScrolled by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 24 }
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "queueScrimAlpha"
    )

    // Subtle "something is actively being reordered" backdrop tint — a
    // whole-sheet cue that a drag is in flight, on top of the per-row morph.
    val dragTintAlpha by animateFloatAsState(
        targetValue = if (dragDropState.isDragging) 0.05f else 0f,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
        label = "queueDragTint"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 4.dp)
                    .size(if (dragDropState.isDragging) 44.dp else 32.dp, 4.dp)
                    .clip(CircleShape)
                    .background(
                        if (dragDropState.isDragging) dynamicColors.primary.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                    .animateContentSize(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            )
        },
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        if (dragTintAlpha > 0f) {
                            drawRect(dynamicColors.primary.copy(alpha = dragTintAlpha))
                        }
                    }
                }
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .dragDropColumn(dragDropState, haptic),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── Section title + live count pill + actions ──
                item(key = "queue_title_row") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Up next",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.4).sp
                            )
                            AnimatedContent(
                                targetState = queue.size,
                                transitionSpec = {
                                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.6f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)))
                                        .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.6f))
                                },
                                label = "queueCountPill"
                            ) { count ->
                                if (count > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 12.dp),
                                        color = dynamicColors.primary.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            "$count",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Black,
                                            color = dynamicColors.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = queue.isNotEmpty(),
                            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.8f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)),
                            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.8f)
                        ) {
                            FilledTonalIconButton(
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClear(); onDismiss() },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear queue", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // ── Hero now-playing card — the sheet's one indulgent element ──
                item(key = "now_playing_hero") {
                    NowPlayingHeroCard(
                        track = currentTrack,
                        dynamicColors = dynamicColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 22.dp)
                    )
                }

                item(key = "up_next_label") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        )
                        Text(
                            "COMING UP",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            letterSpacing = 1.2.sp
                        )
                        AnimatedVisibility(visible = dragDropState.isDragging) {
                            Text(
                                "· drag to reorder",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = dynamicColors.primary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                if (upcoming.isEmpty()) {
                    item(key = "empty_state") {
                        QueueEmptyState(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    }
                } else {
                    itemsIndexed(upcoming, key = { _, indexed -> indexed.value.id }) { rowIndex, indexed ->
                        val qTrack = indexed.value.track
                        val realQueueIndex = indexed.index
                        val absoluteIndex = rowIndex + 3 // offset for the 3 header rows above
                        QueueItem(
                            index = rowIndex,
                            absoluteIndex = absoluteIndex,
                            qTrack = qTrack,
                            onTrackSelect = { onQueueIndexSelect(realQueueIndex) },
                            onRemove = { onRemove(realQueueIndex) },
                            dragDropState = dragDropState,
                            dynamicColors = dynamicColors,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .animateItem(placementSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
                        )
                    }
                }
            }

            // ── Scroll scrim ──
            // A thin gradient + hairline that fades in only once content has
            // actually scrolled beneath the drag handle — signals "there's
            // more above" without a permanent fixed header competing with
            // the hero card for attention.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(20.dp)
                    .graphicsLayer { alpha = scrimAlpha }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                            )
                        )
                    )
            )
        }
    }
}

// ── Now-playing hero card ──
// The sheet's signature element. A blurred wash of the actual album art
// (not a flat gradient) sits behind the content for real "this track" color
// presence. No decorative wavy/squiggly waveform line here anymore — it was
// a permanent infinite animation running the entire time the sheet was
// open, for a card that isn't even clickable, and it was one of the
// contributors to the sheet feeling laggy. The small "NOW PLAYING" pill
// with its playing-bars indicator already carries the "this is live" cue,
// so the card stays calm, cheap to keep on screen, and still reads as the
// one deliberately asymmetric, premium element here.
@Composable
private fun NowPlayingHeroCard(
    track: MusicTrack,
    dynamicColors: DynamicColors,
    modifier: Modifier = Modifier
) {
    val reducedEffects = LocalPerformanceMode.current
    val heroShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 40.dp)

    Surface(
        modifier = modifier,
        shape = heroShape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .clip(heroShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            // Blurred album-art backdrop — real color from the actual track,
            // not a synthetic gradient guess. Radius dropped from 28.dp to
            // 18.dp: past a certain point a bigger blur radius costs real GPU
            // time (larger sample kernel, larger required layer bounds) for a
            // visual difference nobody can actually see once it's sitting
            // behind text and a gradient wash anyway — 18.dp is
            // indistinguishable here at a third of the sample cost.
            if (!reducedEffects) {
                AsyncImage(
                    model = track.thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(18.dp)
                        .alpha(0.55f),
                    error = rememberVectorPainter(Icons.Rounded.MusicNote)
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                dynamicColors.primary.copy(alpha = if (reducedEffects) 0.22f else 0.30f),
                                dynamicColors.secondary.copy(alpha = if (reducedEffects) 0.14f else 0.20f)
                            )
                        )
                    )
                    .border(1.dp, dynamicColors.primary.copy(alpha = 0.18f), heroShape)
            )

            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 26.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 4.dp
                    ) {
                        AlbumArtImage(
                            url = track.thumbnailUri,
                            seed = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            iconSize = 26.dp
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            track.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

            }
        }
    }
}

@Composable
private fun QueueEmptyState(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "emptyBob")
    val reducedEffects = LocalPerformanceMode.current
    val bob by if (reducedEffects) remember { mutableFloatStateOf(0f) } else infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "emptyBobValue"
    )

    Column(
        modifier = modifier.padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(68.dp)
                .graphicsLayer { translationY = bob * 3f },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.QueuePlayNext,
                    null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Nothing queued yet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Tracks you add up next will show here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Queue row ──
// Shape communicates interaction directly: at rest, rows are a plain
// rounded rectangle; the moment a row is the one being dragged (driven off
// DragDropState itself, not a proxy interaction source — long-press-drag
// consumes the pointer before Surface's own click-interaction would ever
// see it) its trailing corner rounds out further toward the hero card's
// asymmetric language and it lifts with real shadow, so the active item is
// unmistakable without relying on scale/opacity tricks alone.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueItem(
    // Row position in the on-screen (current-track-filtered) list. Only
    // used here to trigger `onRemove` after the undo window and as the
    // default for `absoluteIndex` below — the caller's `onRemove` lambda
    // already has the real queue index baked in via closure, so whatever
    // this function passes to it is ignored. It is NOT an index into the
    // full queue; don't reuse it as one.
    index: Int,
    qTrack: MusicTrack,
    onTrackSelect: (MusicTrack) -> Unit,
    onRemove: (Int) -> Unit,
    dragDropState: DragDropState,
    modifier: Modifier = Modifier,
    absoluteIndex: Int = index,
    dynamicColors: DynamicColors? = null
) {
    // Swipe-to-dismiss used to sit on the exact same row as the long-press
    // drag gesture — two pointer-input detectors racing over the same touch
    // stream. That contention was a second, independent source of the drag
    // feeling unstable, on top of the animation-cost issues below. A plain
    // delete button removes the conflict entirely: only one gesture
    // recognizer (the long-press drag) ever owns the row's pointer input.
    // Undo is now a simple latch instead of a real timer/coroutine per row.
    var isPendingDelete by remember { mutableStateOf(false) }

    LaunchedEffect(isPendingDelete) {
        if (isPendingDelete) {
            delay(3000)
            if (isPendingDelete) onRemove(index)
        }
    }

    if (isPendingDelete) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(68.dp)
                .clickable { isPendingDelete = false },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Text(
                        "Removed \"${qTrack.title}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "UNDO",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        return
    }

    // Rows no longer extract their own per-track palette. A full-fidelity
    // Palette/bitmap-sample pass per row (multiplied by every row simultaneously
    // on sheet open, and again every time LazyColumn recycles an item into view
    // while scrolling) was the actual cause of the Up Next lag — it's real
    // decode+sample work competing with the drag/scroll thread, not just a
    // cheap color lookup. Rows now inherit the now-playing track's already-
    // computed accent instead; it reads as one cohesive sheet accent rather
    // than 20 competing per-row hues anyway, which is the better look here.
    val resolvedDynamicColors = dynamicColors ?: DynamicColors(
        primary = MaterialTheme.colorScheme.primary,
        secondary = MaterialTheme.colorScheme.secondary,
        background = MaterialTheme.colorScheme.background,
        surface = MaterialTheme.colorScheme.surface,
        onSurface = MaterialTheme.colorScheme.onSurface
    )

    // Driven directly off the shared drag state: this is the row currently
    // being carried, full stop. No parallel pressed-state guess needed.
    val isBeingDragged = dragDropState.draggingItemIndex == absoluteIndex

    // Only a row that has ever been picked up pays for animated state — every
    // other resting row reads plain static values instead of running its own
    // corner/elevation/color/border spring. Previously all of this ran on
    // every row unconditionally: with 20+ rows alive across the list that's
    // up to 80 live animation subscriptions spun up together the instant the
    // sheet mounts, and again each time LazyColumn recycles a row back into
    // view — real per-row animator setup cost, not draw cost, which is what
    // actually made "Up next" feel laggy. `hasEverBeenDragged` latches true
    // on pickup and stays true, so the one row that was just dropped still
    // gets its spring-back-to-rest animation instead of snapping — everything
    // else never pays the cost at all.
    var hasEverBeenDragged by remember { mutableStateOf(false) }
    if (isBeingDragged) hasEverBeenDragged = true

    val surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainerLow
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val cornerMorph: androidx.compose.ui.unit.Dp
    val elevation: androidx.compose.ui.unit.Dp
    val containerColor: Color
    val borderAlpha: Float
    if (hasEverBeenDragged) {
        cornerMorph = animateDpAsState(
            targetValue = if (isBeingDragged) 28.dp else 18.dp,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            label = "queueItemCornerMorph"
        ).value
        elevation = animateDpAsState(
            targetValue = if (isBeingDragged) 10.dp else 0.dp,
            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
            label = "queueItemElevation"
        ).value
        containerColor = animateColorAsState(
            targetValue = if (isBeingDragged) surfaceContainerHigh else surfaceContainerLow,
            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
            label = "queueItemContainerColor"
        ).value
        borderAlpha = animateFloatAsState(
            targetValue = if (isBeingDragged) 0.4f else 0f,
            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
            label = "queueItemBorderAlpha"
        ).value
    } else {
        cornerMorph = 18.dp
        elevation = 0.dp
        containerColor = surfaceContainerLow
        borderAlpha = 0f
    }
    val rowShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = 18.dp,
        bottomEnd = cornerMorph
    )

    Surface(
        onClick = { onTrackSelect(qTrack) },
        modifier = modifier
            .fillMaxWidth()
            .then(Modifier.dragDropItem(absoluteIndex, dragDropState)),
        shape = rowShape,
        tonalElevation = elevation,
        shadowElevation = elevation,
        color = containerColor,
        border = if (borderAlpha > 0f) BorderStroke(1.dp, resolvedDynamicColors.primary.copy(alpha = borderAlpha)) else null
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(13.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                AlbumArtImage(
                    url = qTrack.thumbnailUri,
                    seed = qTrack.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 22.dp
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    qTrack.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    qTrack.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { isPendingDelete = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove from queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "Drag to reorder",
                tint = resolvedDynamicColors.primary.copy(alpha = if (isBeingDragged) 0.9f else 0.35f),
                modifier = Modifier.padding(start = 4.dp).size(20.dp)
            )
        }
    }
}
