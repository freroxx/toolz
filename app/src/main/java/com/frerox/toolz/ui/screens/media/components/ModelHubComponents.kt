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
 * AI model picker for the hub sheet. Pure state in, events out.
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

        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        ) {
            Box(Modifier.width(40.dp))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "AI models",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onProceed) {
                Icon(Icons.Rounded.Close, "Close")
            }
        }
        Text(
            "Download once · runs fully offline",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(BackgroundModel.entries) { model ->
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

        Spacer(Modifier.height(14.dp))

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
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        )
                        Text(
                            "${model.displayName} · ${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        )
                    }
                    ToolzExpressiveButton(
                        onClick = { if (isDownloaded) onProceed() else onDownloadClick(model) },
                        enabled = !isDownloading,
                        shape = SquircleShape,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
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
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Surface(
                shape = SquircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    Icon(
                        iconFor(model), null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

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
                                "PICK",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                    if (isDownloaded) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.CheckCircle, "Installed", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${speedLabel(model)} · ${model.resolution}p · ${model.sizeLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                )
            }

            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else if (isDownloaded) {
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Rounded.DeleteOutline, "Delete",
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
    "modnet_hd" -> "HD Matte"
    else -> model.displayName
}

private fun speedLabel(model: BackgroundModel): String = when (model.id) {
    "selfie_portrait" -> "Instant"
    "selfie_landscape" -> "Instant"
    "selfie_multiclass" -> "Finest detail"
    "deeplabv3_objects" -> "Fast"
    "modnet_hd" -> "Highest quality"
    else -> "Fast"
}

private fun iconFor(model: BackgroundModel): ImageVector = when (model.id) {
    "selfie_portrait" -> Icons.Rounded.Person
    "selfie_landscape" -> Icons.Rounded.Groups
    "selfie_multiclass" -> Icons.Rounded.Face
    "deeplabv3_objects" -> Icons.Rounded.Pets
    "modnet_hd" -> Icons.Rounded.WorkspacePremium
    else -> Icons.Rounded.AutoAwesome
}
