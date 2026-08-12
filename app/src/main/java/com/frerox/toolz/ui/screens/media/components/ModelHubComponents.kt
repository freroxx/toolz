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

package com.frerox.toolz.ui.screens.media.components

import androidx.compose.animation.*
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

@Composable
fun ModelHubContent(
    selectedModel: BackgroundModel?,
    isDownloading: Boolean,
    downloadProgress: Float,
    onModelSelect: (BackgroundModel) -> Unit,
    onDownloadClick: (BackgroundModel) -> Unit,
    onDeleteClick: (BackgroundModel) -> Unit,
    onProceed: () -> Unit,
    isExistingModel: (BackgroundModel) -> Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "AI Model Hub",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(BackgroundModel.entries) { model ->
                CompactModelCard(
                    model = model,
                    isSelected = selectedModel == model,
                    isDownloaded = isExistingModel(model),
                    onClick = { onModelSelect(model) },
                    onDelete = { onDeleteClick(model) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        AnimatedVisibility(
            visible = selectedModel != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            selectedModel?.let { model ->
                val isDownloaded = isExistingModel(model)
                
                ToolzExpressiveButton(
                    onClick = { if (isDownloaded) onProceed() else onDownloadClick(model) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = SquircleShape,
                    enabled = !isDownloading
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            progress = { downloadProgress }
                        )
                    } else {
                        Icon(
                            if (isDownloaded) Icons.Rounded.RocketLaunch else Icons.Rounded.CloudDownload,
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isDownloading) "Downloading... ${(downloadProgress * 100).toInt()}%" 
                               else if (isDownloaded) "Activate ${model.displayName}" 
                               else "Download ${model.displayName} (${model.sizeLabel})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
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
    onDelete: () -> Unit
) {
    val containerColor = if (isSelected) 
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
    else 
        MaterialTheme.colorScheme.surfaceContainerLow

    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    Surface(
        onClick = onClick,
        shape = SquircleShape,
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.2.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = getIconForModel(model)
            Surface(
                shape = SquircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (isDownloaded) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Downloaded",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 12.sp,
                    maxLines = 2
                )
            }

            if (isDownloaded) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

private fun getIconForModel(model: BackgroundModel): ImageVector {
    return when (model.id) {
        "selfie_portrait" -> Icons.Rounded.Person
        "selfie_landscape" -> Icons.Rounded.Groups
        "selfie_multiclass" -> Icons.Rounded.AutoAwesome
        "deeplabv3_objects" -> Icons.Rounded.Pets
        "modnet_hd" -> Icons.Rounded.WorkspacePremium
        else -> Icons.Rounded.Category
    }
}
