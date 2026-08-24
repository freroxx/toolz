/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.media.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.media.BackgroundModel
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.SquircleShape

/**
 * Model Hub — Revamp 2026.
 * - Designed for ModalBottomSheet (not fullscreen overlay)
 * - Shows verified state, size + resolution pills, recommended badge
 * - Keeps compact cards but adds richer meta
 */
@Composable
fun ModelHubContent(
    selectedModel: BackgroundModel?,
    isDownloading: Boolean,
    downloadProgress: Float,
    onModelSelect: (BackgroundModel) -> Unit,
    onDownloadClick: (BackgroundModel) -> Unit,
    onDeleteClick: (BackgroundModel) -> Unit,
    onProceed: () -> Unit,
    isExistingModel: (BackgroundModel) -> Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Drag handle
        Surface(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .padding(bottom = 4.dp),
            shape = SquircleShape,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ) {}
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("AI Model Hub", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(8.dp))
            Surface(shape = SquircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    "Offline",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        Text(
            "Pick one model. All run 100% offline — download once, keep forever.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 14.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(BackgroundModel.entries) { model ->
                CompactModelCard(
                    model = model,
                    isSelected = selectedModel == model,
                    isDownloaded = isExistingModel(model),
                    onClick = { onModelSelect(model) },
                    onDelete = { onDeleteClick(model) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        AnimatedVisibility(
            visible = selectedModel != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            selectedModel?.let { model ->
                val isDownloaded = isExistingModel(model)
                Column {
                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        )
                    }
                    ToolzExpressiveButton(
                        onClick = { if (isDownloaded) onProceed() else onDownloadClick(model) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = SquircleShape,
                        enabled = !isDownloading,
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.CloudDownload,
                                null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = when {
                                isDownloading -> "Downloading… ${(downloadProgress * 100).toInt()}%"
                                isDownloaded -> "Use ${model.displayName}"
                                else -> "Download ${model.displayName} • ${model.sizeLabel}"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                    if (isDownloaded) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ready offline • ${model.resolution}p • ${model.fileName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${model.resolution}p • ${model.features.joinToString(" • ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactModelCard(
    model: BackgroundModel,
    isSelected: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    else MaterialTheme.colorScheme.surfaceContainerLow

    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)

    Surface(
        onClick = onClick,
        shape = SquircleShape,
        color = containerColor,
        border = BorderStroke(1.2.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = getIconForModel(model)
            Surface(
                shape = SquircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(19.dp), tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (model.isRecommended) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = SquircleShape, color = MaterialTheme.colorScheme.primary) {
                            Text("Recommended", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    } else if (isDownloaded) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.CheckCircle, "Downloaded", modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(model.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 12.sp, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModelPill("${model.resolution}p")
                    ModelPill(model.sizeLabel)
                    model.features.take(2).forEach { ModelPill(it) }
                }
            }

            if (isDownloaded) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.65f))
                }
            } else if (isSelected) {
                Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ModelPill(text: String) {
    Surface(shape = SquircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)) {
        Text(text, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 10.sp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun getIconForModel(model: BackgroundModel): ImageVector = when (model.id) {
    "selfie_portrait" -> Icons.Rounded.Person
    "selfie_landscape" -> Icons.Rounded.Groups
    "selfie_multiclass" -> Icons.Rounded.AutoAwesome
    "deeplabv3_objects" -> Icons.Rounded.Pets
    "modnet_hd" -> Icons.Rounded.WorkspacePremium
    else -> Icons.Rounded.Category
}
