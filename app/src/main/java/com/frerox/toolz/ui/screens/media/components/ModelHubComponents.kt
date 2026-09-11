/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.media.components

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
import androidx.compose.material.icons.rounded.WorkspacePremium
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
import com.frerox.toolz.ui.theme.SquircleShape

/**
 * AI model picker. One card per model, one action per card — the trailing icon
 * downloads, deletes, or shows progress. Tapping a ready card selects it and
 * closes the sheet. No duplicated CTA, no guessed timings: only facts
 * (download size, input resolution) plus the license, always visible.
 */
@Composable
fun ModelHubContent(
    selectedModel: BackgroundModel?,
    downloadingId: String?,
    downloadedBytes: Long,
    totalBytes: Long,
    downloadSpeedBps: Long,
    downloadedIds: Set<String>,
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
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    downloadSpeedBps = downloadSpeedBps,
                    onClick = { onModelSelect(model) },
                    onDownload = { onDownloadClick(model) },
                    onCancel = onCancelDownload,
                    onDelete = { onDeleteClick(model) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ModelCard(
    model: BackgroundModel,
    isSelected: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadedBytes: Long,
    totalBytes: Long,
    downloadSpeedBps: Long,
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
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${model.sizeLabel} · ${model.inputSize}px · ${model.licenseName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
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
                    IconButton(onClick = onDownload, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Rounded.CloudDownload, stringResource(R.string.st_BackgroundRemover_DownloadSize, model.sizeLabel),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (isDownloading) {
                Spacer(Modifier.height(10.dp))
                // Byte truth: determinate when the server reports a total, live
                // MB counter otherwise — the bar can never freeze at 1% again.
                if (totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    downloadStatusLine(downloadedBytes, totalBytes, downloadSpeedBps),
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

private fun iconFor(model: BackgroundModel): ImageVector = when (model.id) {
    "fast_general" -> Icons.Rounded.Bolt
    "portrait_rvm" -> Icons.Rounded.Person
    "pro_detail" -> Icons.Rounded.Diamond
    "ultra_birefnet" -> Icons.Rounded.WorkspacePremium
    else -> Icons.Rounded.AutoAwesome
}

/** Universal byte units — no locale needed. Shared with the editor's thin bar. */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576f)
    bytes >= 1024 -> "%d KB".format(bytes / 1024)
    else -> "%d B".format(bytes.coerceAtLeast(0))
}

/** "42% · 6.3 / 15.0 MB · 3.1 MB/s" or "6.3 MB downloaded · 3.1 MB/s" when total unknown. */
fun downloadStatusLine(downloaded: Long, total: Long, bps: Long): String {
    val left = if (total > 0) {
        "${(downloaded * 100 / total).toInt()}% · ${formatBytes(downloaded)} / ${formatBytes(total)}"
    } else {
        "${formatBytes(downloaded)} downloaded"
    }
    val speed = if (bps > 0) " · ${formatBytes(bps)}/s" else ""
    return left + speed
}
