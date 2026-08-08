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
import com.frerox.toolz.data.media.SegmentationModel
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.SquircleShape

@Composable
fun ModelHubContent(
    selectedModel: SegmentationModel?,
    isDownloading: Boolean,
    downloadProgress: Float,
    onModelSelect: (SegmentationModel) -> Unit,
    onDownloadClick: (SegmentationModel) -> Unit,
    onProceed: () -> Unit,
    isModelDownloaded: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Select Intelligence",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Choose a model optimized for your specific task.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(SegmentationModel.entries) { model ->
                ModelSelectionCard(
                    model = model,
                    isSelected = selectedModel == model,
                    onClick = { onModelSelect(model) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(
            visible = selectedModel != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            selectedModel?.let { model ->
                if (isModelDownloaded) {
                    ToolzExpressiveButton(
                        onClick = onProceed,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = SquircleShape
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Launch ${model.displayName}", fontWeight = FontWeight.Bold)
                    }
                } else {
                    ToolzExpressiveButton(
                        onClick = { onDownloadClick(model) },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = SquircleShape,
                        enabled = !isDownloading
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 3.dp,
                                progress = { downloadProgress }
                            )
                        } else {
                            Icon(Icons.Rounded.Download, null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isDownloading) "Downloading..." else "Download ${model.displayName} (${model.sizeLabel})",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModelSelectionCard(
    model: SegmentationModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) 
        MaterialTheme.colorScheme.primaryContainer 
    else 
        MaterialTheme.colorScheme.surfaceContainer

    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant

    Surface(
        onClick = onClick,
        shape = SquircleShape,
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor),
        tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = getIconForModel(model)
            Surface(
                shape = SquircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${model.resolution}px",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    model.features.forEach { feature ->
                        Badge(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(feature, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (isSelected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun getIconForModel(model: SegmentationModel): ImageVector {
    return when (model.id) {
        "selfie_light" -> Icons.Rounded.Bolt
        "selfie_multiclass" -> Icons.Rounded.Face
        "human_pro" -> Icons.Rounded.Portrait
        "deeplab_v3_pro" -> Icons.Rounded.FilterCenterFocus
        "is_net_hd" -> Icons.Rounded.AutoFixHigh
        "birefnet_ultra" -> Icons.Rounded.WorkspacePremium
        else -> Icons.Rounded.Category
    }
}
