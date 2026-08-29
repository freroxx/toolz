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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.frerox.toolz.data.purgeshot.PurgeShotPreset
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import kotlinx.coroutines.delay

/**
 * PurgeShot M3 Expressive popup — clean, fluid, no blur.
 *
 * - M3 expressive motion (spring, stagger, morph)
 * - Drag handle + swipe-to-dismiss, auto-dismiss 12s progress, success morph
 * - Metadata: name / size / status
 * - Clear scrim (no blur), built only from ui/components/ primitives
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PurgeShotPopup(
    screenshotUri: Uri?,
    displayName: String,
    presets: List<PurgeShotPreset>, // max 6
    autoDurationMillis: Long,
    fileSizeLabel: String? = null, // e.g. "2.4 MB • 1080×2400"
    onSelectDuration: (PurgeShotPreset) -> Unit,
    onDismiss: () -> Unit,
    onKeepForever: () -> Unit = onDismiss,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = rememberToolzHapticFeedback()
    val performanceMode = LocalPerformanceMode.current

    var selected by remember { mutableStateOf<PurgeShotPreset?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    // Auto-dismiss safety: 12s keeps file forever (never auto-deletes without consent)
    var autoDismissProgress by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        if (performanceMode) return@LaunchedEffect
        val total = 12_000L
        val step = 50L
        var elapsed = 0L
        while (elapsed < total && selected == null) {
            delay(step)
            elapsed += step
            autoDismissProgress = 1f - (elapsed.toFloat() / total)
        }
        if (selected == null) onDismiss()
    }
    LaunchedEffect(selected) {
        if (selected != null) {
            isSuccess = true
            delay(520)
            onSelectDuration(selected!!)
        }
    }

    // Entrance spring for card
    val cardScale by animateFloatAsState(
        targetValue = if (isSuccess) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "cardScale"
    )
    val cardCorner by animateDpAsState(
        targetValue = if (isSuccess) 48.dp else 36.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "cardCorner"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f))
            .clickable(enabled = selected == null) { haptic.tick(); onDismiss() }
            .semantics { contentDescription = "PurgeShot scrim, tap to keep screenshot" },
        contentAlignment = Alignment.Center
    ) {
        // Card — drag to dismiss
        Box(
            modifier = Modifier
                .offset(y = dragOffsetY.dp)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    alpha = (1f - (kotlin.math.abs(dragOffsetY) / 520f)).coerceIn(0.6f, 1f)
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (kotlin.math.abs(dragOffsetY) > 120) {
                                haptic.tick()
                                onDismiss()
                            } else dragOffsetY = 0f
                        }
                    ) { _, dragAmount -> dragOffsetY += dragAmount * 0.55f }
                }
        ) {
            StaggeredEntrance(index = 0) {
                ExpressiveCard(
                    onClick = {},
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .padding(horizontal = 18.dp)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(cardCorner),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    elevation = 0.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.10f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top auto-dismiss wavy progress (premium, not alarming)
                        if (selected == null && !performanceMode) {
                            ToolzWavyLinearProgressIndicator(
                                progress = { autoDismissProgress },
                                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(8.dp)),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        // Drag handle
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                .semantics { contentDescription = "Drag to dismiss" }
                        )
                        Spacer(Modifier.height(14.dp))

                        // Header with morphing success
                        AnimatedContent(
                            targetState = isSuccess,
                            transitionSpec = {
                                (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.6f))).togetherWith(
                                    fadeOut(tween(180)) + scaleOut()
                                )
                            },
                            label = "headerMorph"
                        ) { success ->
                            if (success) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(26.dp))
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text("Scheduled", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                        Text(
                                            "${selected?.label} • will be deleted permanently",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                                        Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                                            }
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text("PurgeShot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1)
                                            Text("Delete this screenshot?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                            if (fileSizeLabel != null) {
                                                Text(fileSizeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    IconButton(onClick = { haptic.tick(); onDismiss() }, modifier = Modifier.semantics { contentDescription = "Keep forever" }) {
                                        Icon(Icons.Rounded.Close, "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Thumbnail with expressive loading & scrim label
                        if (!isSuccess) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 148.dp, max = 196.dp),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                tonalElevation = 0.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    var imageState by remember { mutableStateOf<AsyncImagePainter.State?>(null) }
                                    AsyncImage(
                                        model = screenshotUri,
                                        contentDescription = "Screenshot preview: $displayName",
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                                        contentScale = ContentScale.Crop,
                                        onState = { imageState = it }
                                    )
                                    if (imageState is AsyncImagePainter.State.Loading && !performanceMode) {
                                        // Subtle shimmer placeholder
                                        Box(
                                            Modifier.fillMaxSize().background(
                                                androidx.compose.ui.graphics.Brush.linearGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                                    )
                                                )
                                            )
                                        )
                                    }
                                    if (imageState is AsyncImagePainter.State.Error) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Rounded.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                            Text("Preview unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    // Bottom scrim label
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth()
                                            .background(
                                                Color.Black.copy(alpha = 0.46f),
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
                                            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
                                                Text(
                                                    "PNG",
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        } else {
                            // Success confetti-ish surface
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(96.dp),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Text(
                                            "Undo in settings → PurgeShot queue",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        // Subtitle with storage hint
                        if (!isSuccess) {
                            Text(
                                "Choose when it vanishes — freed space is instant.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        // Preset grid
                        if (!isSuccess) {
                            val visiblePresets = presets.take(6)
                            val columns = if (visiblePresets.size <= 3) 3 else if (visiblePresets.size == 4) 2 else 3
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 232.dp),
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
                                        onClick = { haptic.success(); selected = preset }
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        // Bottom actions
                        if (!isSuccess) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ToolzOutlinedExpressiveButton(
                                    onClick = { haptic.tick(); onKeepForever() },
                                    modifier = Modifier.weight(1f).semantics { contentDescription = "Keep forever, don't delete" },
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(vertical = 14.dp)
                                ) {
                                    Icon(Icons.Rounded.Block, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Keep", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                                }
                                // Auto pill — tappable to set auto quickly
                                Surface(
                                    onClick = {
                                        haptic.tick()
                                        val autoPreset = presets.find { it.durationMillis == autoDurationMillis }
                                            ?: PurgeShotPreset(presetLabelFor(autoDurationMillis), autoDurationMillis, "timer")
                                        selected = autoPreset
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.weight(1f).semantics { contentDescription = "Use auto time ${presetLabelFor(autoDurationMillis)}" }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Auto: ${presetLabelFor(autoDurationMillis)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Tap outside or drag to keep • ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                if (onOpenSettings != null) {
                                    Text(
                                        "Smart Auto skips this",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable { onOpenSettings.invoke() }
                                    )
                                }
                            }
                        } else {
                            // Success bar
                            LinearProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(8.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    }
                }
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
            targetValue = if (isPressed) 0.94f else 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
            label = "chipScale"
        )
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(22.dp),
            color = if (isAuto) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
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
                Spacer(Modifier.height(6.dp))
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

private fun presetLabelFor(duration: Long): String = when (duration) {
    30_000L -> "30 sec"
    60_000L -> "1 min"
    5 * 60_000L -> "5 min"
    15 * 60_000L -> "15 min"
    30 * 60_000L -> "30 min"
    60 * 60_000L -> "1 hour"
    6 * 60 * 60_000L -> "6 hours"
    12 * 60 * 60_000L -> "12 hours"
    24 * 60 * 60_000L -> "1 day"
    3 * 24 * 60 * 60_000L -> "3 days"
    7 * 24 * 60 * 60_000L -> "1 week"
    14 * 24 * 60 * 60_000L -> "2 weeks"
    30L * 24 * 60 * 60_000L -> "1 month"
    else -> "${duration / 60_000} min"
}
