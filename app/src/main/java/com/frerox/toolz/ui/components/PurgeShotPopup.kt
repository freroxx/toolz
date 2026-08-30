/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.components

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.frerox.toolz.data.purgeshot.PurgeShotPreset
import com.frerox.toolz.data.purgeshot.PurgeShotUtils
import com.frerox.toolz.ui.theme.LocalPerformanceMode

/**
 * PurgeShot M3 Expressive popup — clean, fluid, no blur.
 *
 * Supports batched screenshots (multiple URIs shown as a grid of thumbnails).
 * No auto-dismiss — user MUST make an explicit choice (Keep / Auto / Delete / timer).
 * No delayed scheduled popup — immediately calls back so scheduled notification is posted.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PurgeShotPopup(
    screenshotUri: Uri?,
    displayName: String,
    presets: List<PurgeShotPreset>,
    autoDurationMillis: Long,
    fileSizeLabel: String? = null,
    onSelectDuration: (PurgeShotPreset) -> Unit,
    onDismiss: () -> Unit,
    onDeleteNow: (() -> Unit)? = null,
    onKeepForever: () -> Unit = onDismiss,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    screenshotUris: List<Uri> = listOfNotNull(screenshotUri)
) {
    val haptic = rememberToolzHapticFeedback()
    val isMultiple = screenshotUris.size > 1
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f))
            .semantics { contentDescription = "PurgeShot popup" },
        contentAlignment = Alignment.Center
    ) {
        // Card with vertical drag gesture to dismiss
        Box(
            modifier = Modifier
                .offset(y = dragOffsetY.dp)
                .graphicsLayer {
                    alpha = (1f - (kotlin.math.abs(dragOffsetY) / 520f)).coerceIn(0.6f, 1f)
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (kotlin.math.abs(dragOffsetY) > 120) {
                                haptic.tick()
                                onKeepForever()
                            } else dragOffsetY = 0f
                        }
                    ) { _, dragAmount -> dragOffsetY += dragAmount * 0.55f }
                }
        ) {
            StaggeredEntrance(index = 0) {
                ExpressiveCard(
                    onClick = {},
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(36.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    elevation = 0.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Drag handle
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        )
                        Spacer(Modifier.height(16.dp))

                        // ── Header ──────────────────────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.DeleteSweep, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(R.string.st_PurgeShot_Title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1
                                )
                                AnimatedContent(
                                    targetState = screenshotUris.size,
                                    transitionSpec = { (fadeIn() + slideInVertically()).togetherWith(fadeOut() + slideOutVertically()) },
                                    label = "popupTitleMorph"
                                ) { count ->
                                    Text(
                                        if (count > 1) stringResource(R.string.st_PurgeShot_DeleteTheseScreenshots, count) else stringResource(R.string.st_PurgeShot_DeleteThisScreenshot),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                if (!isMultiple && fileSizeLabel != null) {
                                    Text(
                                        fileSizeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // ── Thumbnail(s) ─────────────────────────────────────────
                        AnimatedContent(
                            targetState = isMultiple,
                            transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(180))) },
                            label = "thumbnailMorph"
                        ) { multiple ->
                            if (multiple) {
                                MultiScreenshotGrid(uris = screenshotUris, count = screenshotUris.size)
                            } else {
                                SingleThumbnail(uri = screenshotUris.firstOrNull() ?: screenshotUri, displayName = displayName)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // ── Timer preset grid ────────────────────────────────────
                        Text(
                            stringResource(R.string.st_PurgeShot_ChooseWhenToDelete),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Spacer(Modifier.height(14.dp))

                        val visiblePresets = presets.take(6)
                        val columns = if (visiblePresets.size <= 3) 3 else 3
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            userScrollEnabled = false
                        ) {
                            itemsIndexed(visiblePresets) { idx, preset ->
                                val isAuto = preset.label.equals("Auto", ignoreCase = true)
                                PurgePresetChip(
                                    preset = preset,
                                    isAuto = isAuto,
                                    index = idx,
                                    onClick = {
                                        haptic.success()
                                        onSelectDuration(preset)
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))

                        // ── Action buttons ───────────────────────────────────
                        // Row 1: Keep + Auto
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolzOutlinedExpressiveButton(
                                onClick = { haptic.tick(); onKeepForever() },
                                modifier = Modifier.weight(1f).semantics { contentDescription = "Keep forever" },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(vertical = 14.dp)
                            ) {
                                Icon(Icons.Rounded.Block, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.st_PurgeShot_Keep), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                            }

                            // Auto button
                            Surface(
                                onClick = {
                                    haptic.tick()
                                    val autoPreset = presets.find { it.durationMillis == autoDurationMillis }
                                        ?: PurgeShotPreset(presetLabelFor(autoDurationMillis), autoDurationMillis, "timer")
                                    onSelectDuration(autoPreset)
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.weight(1f).semantics { contentDescription = "Auto time" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.st_PurgeShot_AutoWithTime, presetLabelFor(autoDurationMillis)),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Row 2: Delete now button — full-width, error container color, below keep/auto
                        if (onDeleteNow != null) {
                            Spacer(Modifier.height(10.dp))
                            ToolzExpressiveButton(
                                onClick = { haptic.success(); onDeleteNow() },
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Delete immediately" },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(vertical = 14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (isMultiple) stringResource(R.string.st_PurgeShot_DeleteAllNow) else stringResource(R.string.st_PurgeShot_DeleteNow),
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        if (onOpenSettings != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    stringResource(R.string.st_PurgeShot_SmartAutoSkipsPopup),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { onOpenSettings.invoke() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleThumbnail(uri: Uri?, displayName: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 200.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            var imageState by remember { mutableStateOf<AsyncImagePainter.State?>(null) }
            AsyncImage(
                model = uri,
                contentDescription = "Screenshot: $displayName",
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop,
                onState = { imageState = it }
            )
            if (imageState is AsyncImagePainter.State.Error) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text(stringResource(R.string.st_PurgeShot_PreviewUnavailable), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Bottom scrim label
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Color.Black.copy(alpha = 0.45f),
                        RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Image, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(14.dp))
                    Text(
                        displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiScreenshotGrid(uris: List<Uri>, count: Int) {
    val displayUris = uris.take(4)
    val remaining = count - displayUris.size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val rows = displayUris.chunked(2)
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { idx, uri ->
                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(90.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Box {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (idx == displayUris.lastIndex && remaining > 0) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "+$remaining",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    repeat(2 - row.size) { Box(modifier = Modifier.weight(1f)) {} }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.st_PurgeShot_ScreenshotsCount, count),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PurgePresetChip(
    preset: PurgeShotPreset,
    isAuto: Boolean,
    index: Int,
    onClick: () -> Unit
) {
    val icon = iconFor(preset.iconName)
    StaggeredEntrance(index = index + 1, spatialOffset = androidx.compose.ui.unit.IntOffset(0, 18)) {
        val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val isPressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.93f else 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
            label = "chipScale"
        )
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(22.dp),
            color = if (isAuto) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .then(
                    if (isAuto) Modifier.border(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f), RoundedCornerShape(22.dp)) else Modifier
                )
                .semantics { contentDescription = "${preset.label}${if (isAuto) ", auto" else ""}" },
            tonalElevation = if (isAuto) 1.dp else 0.dp,
            interactionSource = interaction
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = if (isAuto) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, modifier = Modifier.size(16.dp), tint = if (isAuto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    preset.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isAuto) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isAuto) {
                    Text(
                        "AUTO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp
                    )
                } else {
                    Text(
                        durationToHumane(preset.durationMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun durationToHumane(millis: Long): String {
    val s = millis / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        s < 86400 -> "${s / 3600}h"
        else -> "${s / 86400}d"
    }
}

private fun iconFor(name: String): ImageVector = when (name.lowercase()) {
    "timer" -> Icons.Rounded.Timer
    "schedule" -> Icons.Rounded.Schedule
    "hourglass_top" -> Icons.Rounded.HourglassTop
    "hourglass_empty" -> Icons.Rounded.HourglassEmpty
    "today" -> Icons.Rounded.Today
    "date_range" -> Icons.Rounded.DateRange
    "wb_sunny" -> Icons.Rounded.WbSunny
    "nights_stay" -> Icons.Rounded.NightsStay
    "calendar_today" -> Icons.Rounded.CalendarToday
    "event_repeat" -> Icons.Rounded.EventRepeat
    "calendar_month" -> Icons.Rounded.CalendarMonth
    "auto_awesome" -> Icons.Rounded.AutoAwesome
    else -> Icons.Rounded.Timer
}

private fun presetLabelFor(duration: Long): String = PurgeShotUtils.formatDurationLabel(duration)
