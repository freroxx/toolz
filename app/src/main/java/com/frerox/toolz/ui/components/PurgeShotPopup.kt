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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.frerox.toolz.data.purgeshot.PurgeShotPreset
import com.frerox.toolz.ui.theme.LocalPerformanceMode

/**
 * Material 3 Expressive popup card for PurgeShot — appears instantly after screenshot.
 * Built entirely from ui/components/ expressive primitives (ExpressiveCard, ToolzExpressiveButton, etc.)
 *
 * Features:
 *  - StaggeredEntrance + scale/fade for cinematic pop
 *  - Up to 6 customizable timer buttons (2x3 grid or 3x2 depending on count)
 *  - Live countdown preview, thumbnail, auto-highlight
 *  - Haptics via rememberToolzHapticFeedback()
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurgeShotPopup(
    screenshotUri: Uri?,
    displayName: String,
    presets: List<PurgeShotPreset>, // max 6
    autoDurationMillis: Long,
    onSelectDuration: (PurgeShotPreset) -> Unit,
    onDismiss: () -> Unit,
    onKeepForever: () -> Unit = onDismiss,
    modifier: Modifier = Modifier
) {
    val haptic = rememberToolzHapticFeedback()
    val performanceMode = LocalPerformanceMode.current

    // Dismiss on scrim tap
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f))
            .clickable { haptic.tick(); onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        StaggeredEntrance(index = 0) {
            ExpressiveCard(
                onClick = {},
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .padding(horizontal = 20.dp)
                    .clickable(enabled = false) {}, // absorb clicks
                shape = RoundedCornerShape(36.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                elevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header: drag handle + title
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                                }
                            }
                            Column {
                                Text("PurgeShot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                Text("Delete this screenshot?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { haptic.tick(); onDismiss() }) {
                            Icon(Icons.Rounded.Close, "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Thumbnail preview (expressive squircle card)
                    if (screenshotUri != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            tonalElevation = 2.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = screenshotUri,
                                    contentDescription = "Screenshot preview",
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                // Subtle scrim + label at bottom
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
                                    Text(
                                        displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.primary)
                                Text(displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Expressive subtitle: choose when to purge
                    Text(
                        "Choose when it vanishes — saved storage guaranteed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(Modifier.height(20.dp))

                    // Preset grid — up to 6, 2 or 3 columns adaptive
                    val visiblePresets = presets.take(6)
                    val columns = if (visiblePresets.size <= 3) 3 else if (visiblePresets.size == 4) 2 else 3

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(visiblePresets) { idx, preset ->
                            val isAuto = preset.durationMillis == autoDurationMillis
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

                    // Bottom actions: Keep forever + auto badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolzOutlinedExpressiveButton(
                            onClick = { haptic.tick(); onKeepForever() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(Icons.Rounded.Block, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Keep", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                        }
                        // Auto caption pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Auto: ${presetLabelFor(autoDurationMillis)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tip: enable Smart Auto in settings to skip this popup.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
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
    val performanceMode = LocalPerformanceMode.current
    val icon = iconFor(preset.iconName)
    // Staggered entrance per chip for expressive delight
    StaggeredEntrance(index = index + 1, spatialOffset = androidx.compose.ui.unit.IntOffset(0, 18)) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(22.dp),
            color = if (isAuto) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
                .then(
                    if (isAuto) Modifier.border(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(22.dp)) else Modifier
                ),
            tonalElevation = if (isAuto) 2.dp else 0.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon bubble
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = if (isAuto) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
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
                    maxLines = 1
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
                }
            }
        }
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
