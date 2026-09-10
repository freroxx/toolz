/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.media.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.WifiOff
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.R
import com.frerox.toolz.data.media.BackgroundModel
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.theme.SquircleShape

/**
 * AI model picker — honest store rows: what it is, what it costs (size/speed),
 * its license, and its exact state. No marketing fluff, no dead code.
 */
@Composable
fun ModelHubContent(
    selectedModel: BackgroundModel?,
    downloadingId: String?,
    downloadProgress: Float,
    downloadSpeed: String?,
    downloadedIds: Set<String>,
    meteredNow: Boolean,
    onModelSelect: (BackgroundModel) -> Unit,
    onDownloadClick: (BackgroundModel) -> Unit,
    onDeleteClick: (BackgroundModel) -> Unit,
    onCancelDownload: () -> Unit,
    onProceed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
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
                    stringResource(R.string.st_BackgroundRemover_Models),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    stringResource(R.string.st_BackgroundRemover_OfflineNote),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onProceed) {
                Icon(Icons.Rounded.Close, stringResource(R.string.st_Common_Close))
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (model in BackgroundModel.entries) {
                ModelCard(
                    model = model,
                    isSelected = selectedModel == model,
                    isDownloaded = downloadedIds.contains(model.id),
                    isDownloading = downloadingId == model.id,
                    downloadProgress = downloadProgress,
                    downloadSpeed = downloadSpeed,
                    meteredNow = meteredNow,
                    onClick = { onModelSelect(model) },
                    onDownload = { onDownloadClick(model) },
                    onCancel = onCancelDownload,
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
                            isDownloading -> stringResource(R.string.st_BackgroundRemover_Downloading)
                            isDownloaded -> stringResource(R.string.st_BackgroundRemover_UseModel, model.shortName)
                            else -> stringResource(R.string.st_BackgroundRemover_DownloadSize, model.sizeLabel)
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (!isDownloaded && !isDownloading && model.gatedOnWifi && meteredNow) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Rounded.WifiOff, null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.st_BackgroundRemover_WifiNote, model.sizeLabel),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
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
    downloadProgress: Float,
    downloadSpeed: String?,
    meteredNow: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = SquircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.6.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = SquircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
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
                            model.shortName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (model.isRecommended && !isDownloaded) {
                            Spacer(Modifier.width(6.dp))
                            Surface(shape = SquircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text(
                                    stringResource(R.string.st_BackgroundRemover_Recommended),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                        if (isDownloaded) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.CheckCircle, stringResource(R.string.st_BackgroundRemover_Ready), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpecDot(iconFor(model), model.sizeLabel)
                        SpecDot(Icons.Rounded.Bolt, speedText(model))
                        SpecDot(Icons.Rounded.Diamond, qualityText(model))
                    }
                    Text(
                        model.licenseName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }

                if (isDownloading) {
                    IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.st_Common_Cancel), modifier = Modifier.size(18.dp))
                    }
                } else if (isDownloaded) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.DeleteOutline, stringResource(R.string.st_BackgroundRemover_RemoveModel, model.shortName),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.66f),
                        )
                    }
                } else {
                    IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.CloudDownload, stringResource(R.string.st_BackgroundRemover_DownloadSize, model.sizeLabel),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (isDownloading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    downloadSpeed ?: "${(downloadProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun SpecDot(icon: ImageVector, text: String) {
    Icon(icon, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    Spacer(Modifier.width(3.dp))
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.width(10.dp))
}

private fun speedText(model: BackgroundModel): String = when (model.id) {
    "fast_general" -> "≈1–2s"
    "portrait_rvm" -> "≈2–4s"
    "pro_detail" -> "≈10–30s"
    else -> "Instant"
}

private fun qualityText(model: BackgroundModel): String = when (model.id) {
    "fast_general" -> "Good"
    "portrait_rvm" -> "Great hair"
    "pro_detail" -> "Max"
    else -> "Rough"
}

private fun iconFor(model: BackgroundModel): ImageVector = when (model.id) {
    "fast_general" -> Icons.Rounded.Bolt
    "portrait_rvm" -> Icons.Rounded.Person
    "pro_detail" -> Icons.Rounded.Diamond
    else -> Icons.Rounded.AutoAwesome
}
