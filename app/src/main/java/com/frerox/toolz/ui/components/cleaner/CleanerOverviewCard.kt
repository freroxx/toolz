/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.components.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.cleaner.StorageInfo

@Composable
fun CleanerOverviewCard(
    storageInfo: StorageInfo,
    cleanableBytes: Long = storageInfo.cleanableBytes,
    trashBytes: Long = 0L,
    onOpenTrash: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniStat(
            modifier = Modifier.weight(1f),
            label = "Used",
            value = Formatter.formatFileSize(context, storageInfo.usedBytes),
            icon = Icons.Rounded.Storage,
            accent = MaterialTheme.colorScheme.primary
        )
        MiniStat(
            modifier = Modifier.weight(1f),
            label = "Free",
            value = Formatter.formatFileSize(context, storageInfo.freeBytes),
            icon = Icons.Rounded.FolderOpen,
            accent = Color(0xFF4CAF50)
        )
        if (cleanableBytes > 0) {
            MiniStat(
                modifier = Modifier.weight(1f),
                label = "Reclaimable",
                value = Formatter.formatFileSize(context, cleanableBytes),
                icon = Icons.Rounded.Storage,
                accent = MaterialTheme.colorScheme.error
            )
        } else if (trashBytes > 0) {
            MiniStat(
                modifier = Modifier.weight(1f),
                label = "In Trash",
                value = Formatter.formatFileSize(context, trashBytes),
                icon = Icons.Rounded.DeleteOutline,
                accent = MaterialTheme.colorScheme.error,
                onClick = onOpenTrash
            )
        }
    }
}

@Composable
private fun MiniStat(
    modifier: Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(16.dp), tint = accent)
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CleanerDashboardHeader(
    storageInfo: StorageInfo,
    cleanableBytes: Long,
    trashBytes: Long = 0L,
    onOpenTrash: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            CleanerStorageArc(storageInfo, cleanableBytes)
        }
    }
}
