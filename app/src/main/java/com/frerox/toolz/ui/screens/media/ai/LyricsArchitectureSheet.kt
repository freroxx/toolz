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

package com.frerox.toolz.ui.screens.media.ai

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.ToolzExpressiveButton

data class ArchitectureLayerInfo(
    val stepIndex: String,
    val title: String,
    val badge: String,
    val summary: String,
    val technicalDetails: String,
    val icon: ImageVector,
    val isLocal: Boolean
)

/**
 * Material 3 Expressive Bouncy Click Modifier for tactile visual feedback.
 */
@Composable
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bouncyScale"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

enum class ArchitectureFilterTab { ALL, LOCAL, ONLINE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsArchitectureSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedFilter by remember { mutableStateOf(ArchitectureFilterTab.ALL) }
    var expandedIndex by remember { mutableStateOf<Int?>(null) }

    val allLayers = remember {
        listOf(
            ArchitectureLayerInfo(
                stepIndex = "01",
                title = "Local Database Cache",
                badge = "FASTEST",
                summary = "Instant retrieval from Room DB cache without disk/network delays.",
                technicalDetails = "Stores retrieved LRC strings indexed by track URI. Bypasses disk scanning and network calls for previously fetched tracks.",
                icon = Icons.Rounded.Storage,
                isLocal = true
            ),
            ArchitectureLayerInfo(
                stepIndex = "02",
                title = "Embedded Audio Metadata",
                badge = "ID3 / FLAC",
                summary = "Extracts embedded USLT/SYLT frames directly from file headers.",
                technicalDetails = "Uses MediaMetadataRetriever (METADATA_KEY_LYRICS on API 31+) to parse embedded unsynchronized and synchronized lyrics frames in under 10ms.",
                icon = Icons.Rounded.AudioFile,
                isLocal = true
            ),
            ArchitectureLayerInfo(
                stepIndex = "03",
                title = "Storage Sidecar Scanner",
                badge = "SIDECAR .LRC",
                summary = "Scans track folder for matching .lrc or .txt sidecar files.",
                technicalDetails = "Looks for candidate files (track_name.lrc, track_name.txt) in the same directory. Parses timecodes and verifies formatting before loading.",
                icon = Icons.Rounded.FolderZip,
                isLocal = true
            ),
            ArchitectureLayerInfo(
                stepIndex = "04",
                title = "LRCLIB Synced Engine",
                badge = "LRCLIB SYNC",
                summary = "Multi-stage fuzzy query against open LRCLIB database.",
                technicalDetails = "Queries https://lrclib.net/api with sanitized title/artist parameters. Handles time-synchronized LRC files with sub-second accuracy.",
                icon = Icons.Rounded.CloudDownload,
                isLocal = false
            ),
            ArchitectureLayerInfo(
                stepIndex = "05",
                title = "Catalog Captions Engine",
                badge = "STREAMING",
                summary = "Converts WebVTT/SRT subtitle streams into LRC sync lines.",
                technicalDetails = "Converts stream caption tracks into standardized LRC format compatible with karaoke word sync and smooth scrolling UI.",
                icon = Icons.Rounded.ClosedCaption,
                isLocal = false
            ),
            ArchitectureLayerInfo(
                stepIndex = "06",
                title = "Secondary Public API Fallback",
                badge = "FAILSAFE API",
                summary = "Failsafe resolver querying secondary public lyrics web endpoints.",
                technicalDetails = "Queries public REST endpoints using sanitized query parameters. Ensures high coverage even for obscure or indies tracks.",
                icon = Icons.Rounded.AutoAwesome,
                isLocal = false
            )
        )
    }

    val visibleLayers = remember(selectedFilter, allLayers) {
        when (selectedFilter) {
            ArchitectureFilterTab.ALL -> allLayers
            ArchitectureFilterTab.LOCAL -> allLayers.filter { it.isLocal }
            ArchitectureFilterTab.ONLINE -> allLayers.filter { !it.isLocal }
        }
    }

    // Pulse animation for active pipeline indicator
    val pulseTransition = rememberInfiniteTransition(label = "pulseAnim")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 8.dp)
                    .size(40.dp, 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            // Header Hero Banner - M3 Expressive
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Architecture,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Lyrics Engine",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    // Live Active Indicator Dot
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .graphicsLayer { alpha = pulseAlpha }
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }

                                Text(
                                    text = "6-stage resolution & fallback pipeline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Surface(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Segmented Filter Bar
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = selectedFilter == ArchitectureFilterTab.ALL,
                    onClick = { selectedFilter = ArchitectureFilterTab.ALL },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) {
                    Text("All (6)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                SegmentedButton(
                    selected = selectedFilter == ArchitectureFilterTab.LOCAL,
                    onClick = { selectedFilter = ArchitectureFilterTab.LOCAL },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) {
                    Text("Local (3)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                SegmentedButton(
                    selected = selectedFilter == ArchitectureFilterTab.ONLINE,
                    onClick = { selectedFilter = ArchitectureFilterTab.ONLINE },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) {
                    Text("Online (3)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Timeline Stepper Node List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                itemsIndexed(visibleLayers, key = { _, layer -> layer.stepIndex }) { index, layer ->
                    val isExpanded = expandedIndex == index

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Timeline Stepper Node Indicator
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(36.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape,
                                color = if (isExpanded) MaterialTheme.colorScheme.primary
                                        else if (layer.isLocal) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.tertiaryContainer,
                                border = BorderStroke(
                                    width = 1.5.dp,
                                    color = if (isExpanded) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = layer.stepIndex,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = if (isExpanded) MaterialTheme.colorScheme.onPrimary
                                                else if (layer.isLocal) MaterialTheme.colorScheme.onSecondaryContainer
                                                else MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Vertical timeline connector line
                            if (index < visibleLayers.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(if (isExpanded) 110.dp else 46.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.outlineVariant,
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                                )
                                            )
                                        )
                                )
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        // Compact Architecture Node Card
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .bouncyClickable {
                                    expandedIndex = if (isExpanded) null else index
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isExpanded) MaterialTheme.colorScheme.surfaceContainerHigh
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = if (isExpanded) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                     else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            layer.icon,
                                            contentDescription = null,
                                            tint = if (layer.isLocal) MaterialTheme.colorScheme.secondary
                                                   else MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(18.dp)
                                        )

                                        Text(
                                            text = layer.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (layer.isLocal) MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                                                else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = layer.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp,
                                            color = if (layer.isLocal) MaterialTheme.colorScheme.secondary
                                                    else MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    text = layer.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )

                                // Expandable Spec Info Box
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                                    exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(top = 10.dp)
                                            .fillMaxWidth()
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Code,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )

                                                Text(
                                                    text = layer.technicalDetails,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 11.sp,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    ToolzExpressiveButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Got it",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }
}
