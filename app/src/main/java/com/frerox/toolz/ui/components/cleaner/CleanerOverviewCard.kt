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

package com.frerox.toolz.ui.components.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.cleaner.StorageInfo
import com.frerox.toolz.ui.components.MediumExpressiveShape

@Composable
fun CleanerOverviewCard(storageInfo: StorageInfo, cleanableBytes: Long = storageInfo.cleanableBytes, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CleanerMiniStat(modifier = Modifier.weight(1f), label = "Used", value = Formatter.formatFileSize(LocalContext.current, storageInfo.usedBytes), icon = Icons.Rounded.Storage, accent = MaterialTheme.colorScheme.primary)
        CleanerMiniStat(modifier = Modifier.weight(1f), label = "Free", value = Formatter.formatFileSize(LocalContext.current, storageInfo.freeBytes), icon = Icons.Rounded.FolderOpen, accent = Color(0xFF4CAF50))
        if (cleanableBytes > 0) {
            CleanerMiniStat(modifier = Modifier.weight(1f), label = "Cleanable", value = Formatter.formatFileSize(LocalContext.current, cleanableBytes), icon = Icons.Rounded.Storage, accent = MaterialTheme.colorScheme.error)
        }
    }
}
@Composable
private fun CleanerMiniStat(modifier: Modifier, label: String, value: String, icon: ImageVector, accent: Color) {
    Surface(modifier = modifier, shape = MediumExpressiveShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = accent)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
fun CleanerDashboardHeader(storageInfo: StorageInfo, cleanableBytes: Long, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CleanerStorageArc(storageInfo = storageInfo, cleanableBytes = cleanableBytes)
        Spacer(Modifier.height(20.dp))
        CleanerOverviewCard(storageInfo = storageInfo, cleanableBytes = cleanableBytes)
    }
}
