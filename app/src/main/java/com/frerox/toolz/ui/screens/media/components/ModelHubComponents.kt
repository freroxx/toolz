/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.media.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.media.BackgroundModel
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.theme.SquircleShape

/**
 * AI model picker — clear M3 Expressive, minimal chrome.
 */
@Composable
fun ModelHubContent(
    selectedModel: BackgroundModel?,
    downloadingId: String?,
    downloadProgress: Float,
    downloadedIds: Set<String>,
    onModelSelect: (BackgroundModel) -> Unit,
    onDownloadClick: (BackgroundModel) -> Unit,
    onDeleteClick: (BackgroundModel) -> Unit,
    onProceed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        // Clean header — no extra spacers, expressive title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        ) {
            Surface(
                shape = SquircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "AI models",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Offline after download",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onProceed) {
                Icon(Icons.Rounded.Close, "Close")
            }
        }

        // Simple flat list — no sections, expressive cards with generous whitespace
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (model in allModels) {
                ModelCard(
                    model = model,
                    isSelected = selectedModel == model,
                    isDownloaded = downloadedIds.contains(model.id),
                    isDownloading = downloadingId == model.id,
                    onClick = { onModelSelect(model) },
                    onDelete = { onDeleteClick(model) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(
            visible = selectedModel != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            selectedModel?.let { model ->
                val isDownloaded = downloadedIds.contains(model.id)
                val isDownloading = downloadingId == model.id

                Column {
                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        )
                        Text(
                            "${model.displayName} · ${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                    }
                    ToolzExpressiveButton(
                        onClick = { if (isDownloaded) onProceed() else onDownloadClick(model) },
                        enabled = !isDownloading,
                        shape = SquircleShape,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(
                                if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.CloudDownload,
                                null, modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when {
                                isDownloading -> "Downloading…"
                                isDownloaded -> "Use ${shortName(model)}"
                                else -> "Download · ${model.sizeLabel}"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: BackgroundModel,
    isSelected: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = SquircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isSelected) BorderStroke(1.6.dp, MaterialTheme.colorScheme.primary) else null,
        shadowElevation = if (isSelected) 0.dp else 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Surface(
                shape = SquircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(42.dp)) {
                    Icon(
                        iconFor(model), null,
                        modifier = Modifier.size(21.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        shortName(model),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (model.isRecommended && !isDownloaded) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = SquircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(
                                "RECOMMENDED",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                    if (isDownloaded) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.CheckCircle, "Ready", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                // Clear: only essentials — removed long description truncation & marketing labels
                Text(
                    "${model.resolution}p · ${model.sizeLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else if (isDownloaded) {
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.DeleteOutline, "Remove",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
                    )
                }
            }
        }
    }
}

/** Short display names — the enum names carry marketing fluff; UI stays clean. */
private fun shortName(model: BackgroundModel): String = when (model.id) {
    "selfie_portrait" -> "Portrait"
    "selfie_landscape" -> "Group"
    "selfie_multiclass" -> "Detail+"
    "deeplabv3_objects" -> "Objects"
    "modnet_hd" -> "Portrait HD"
    else -> model.displayName
}

private fun speedLabel(model: BackgroundModel): String = when (model.id) {
    "selfie_portrait" -> "Instant"
    "selfie_landscape" -> "Instant"
    "selfie_multiclass" -> "Hair & clothing detail"
    "deeplabv3_objects" -> "Fast"
    "modnet_hd" -> "Best quality for people"
    else -> "Fast"
}

private val allModels = listOf(
    BackgroundModel.SELFIE_PORTRAIT,
    BackgroundModel.SELFIE_LANDSCAPE,
    BackgroundModel.MODNET_HD,
    BackgroundModel.DEEPLABV3_OBJECTS,
    BackgroundModel.SELFIE_MULTICLASS,
)

private val peopleModels = allModels.take(4)
private val objectModels = listOf(BackgroundModel.DEEPLABV3_OBJECTS)

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
    )
}

private fun iconFor(model: BackgroundModel): ImageVector = when (model.id) {
    "selfie_portrait" -> Icons.Rounded.Person
    "selfie_landscape" -> Icons.Rounded.Groups
    "selfie_multiclass" -> Icons.Rounded.Face
    "deeplabv3_objects" -> Icons.Rounded.Pets
    "modnet_hd" -> Icons.Rounded.WorkspacePremium
    else -> Icons.Rounded.AutoAwesome
}
