/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * A shimmer loading skeleton whose card dimensions match [SearchResultCard]
 * pixel-for-pixel, so the results list doesn't visibly reflow once real
 * content arrives.
 */
@Composable
fun SearchShimmer(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(6) { i -> ShimmerCard(delayMs = i * 70) }
    }
}

/**
 * Skeleton for the images tab — a 2-up grid of thumbnail cards matching the
 * paired [com.frerox.toolz.ui.screens.search.components.NativeImageCard] rows
 * (140dp thumbs + meta strip), so the grid doesn't reflow when results land.
 */
@Composable
fun ImageGridShimmer(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { col ->
                    ShimmerImageTile(delayMs = (row * 2 + col) * 90, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Skeleton for the videos tab — stacked 16:9 thumbnail cards with title/snippet
 * bars matching [com.frerox.toolz.ui.screens.search.components.NativeVideoCard].
 */
@Composable
fun VideoListShimmer(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { i -> ShimmerVideoCard(delayMs = i * 90) }
    }
}

@Composable
private fun ShimmerImageTile(delayMs: Int, modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush(delayMs)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
        modifier = modifier,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(brush),
            )
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShimmerBox(brush, Modifier.fillMaxWidth(0.85f).height(12.dp).clip(RoundedCornerShape(6.dp)))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBox(brush, Modifier.size(14.dp).clip(CircleShape))
                    ShimmerBox(brush, Modifier.fillMaxWidth(0.5f).height(10.dp).clip(RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}

@Composable
private fun ShimmerVideoCard(delayMs: Int) {
    val brush = rememberShimmerBrush(delayMs)
    Surface(
        shape = com.frerox.toolz.ui.components.LargeExpressiveShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(210.dp).background(brush)) {
                // Centered play-button placeholder, mirroring the real video card.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape),
                )
            }
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ShimmerBox(brush, Modifier.fillMaxWidth(0.92f).height(15.dp).clip(RoundedCornerShape(7.dp)))
                ShimmerBox(brush, Modifier.fillMaxWidth(0.60f).height(15.dp).clip(RoundedCornerShape(7.dp)))
                ShimmerBox(brush, Modifier.fillMaxWidth(0.45f).height(11.dp).clip(RoundedCornerShape(5.dp)))
            }
        }
    }
}

@Composable
private fun rememberShimmerBrush(delayMs: Int): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer$delayMs")
    val progress by transition.animateFloat(
        initialValue = -0.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = delayMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress$delayMs",
    )
    return Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        startX = progress * 900f,
        endX = progress * 900f + 500f,
    )
}

@Composable
private fun ShimmerCard(delayMs: Int) {
    val transition = rememberInfiniteTransition(label = "shimmer$delayMs")
    val progress by transition.animateFloat(
        initialValue = -0.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = delayMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress$delayMs",
    )
    val brush = Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        startX = progress * 900f,
        endX = progress * 900f + 500f,
    )

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .defaultMinSize(minHeight = 98.dp)
                    .background(brush, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 13.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBox(brush, Modifier.size(16.dp).clip(CircleShape))
                    ShimmerBox(brush, Modifier.fillMaxWidth(0.38f).height(10.dp).clip(RoundedCornerShape(5.dp)))
                    Spacer(Modifier.weight(1f))
                    ShimmerBox(brush, Modifier.width(28.dp).height(14.dp).clip(RoundedCornerShape(6.dp)))
                }
                Spacer(Modifier.height(2.dp))
                ShimmerBox(brush, Modifier.fillMaxWidth(0.92f).height(14.dp).clip(RoundedCornerShape(6.dp)))
                ShimmerBox(brush, Modifier.fillMaxWidth(0.62f).height(14.dp).clip(RoundedCornerShape(6.dp)))
                Spacer(Modifier.height(2.dp))
                ShimmerBox(brush, Modifier.fillMaxWidth(1.00f).height(11.dp).clip(RoundedCornerShape(5.dp)))
                ShimmerBox(brush, Modifier.fillMaxWidth(1.00f).height(11.dp).clip(RoundedCornerShape(5.dp)))
                ShimmerBox(brush, Modifier.fillMaxWidth(0.66f).height(11.dp).clip(RoundedCornerShape(5.dp)))
            }
        }
    }
}

@Composable
private fun ShimmerBox(brush: Brush, modifier: Modifier) {
    Box(modifier = modifier.background(brush))
}
