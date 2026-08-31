package com.frerox.toolz.ui.components.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
fun CleanerOverviewCard(storageInfo: StorageInfo, cleanableBytes: Long = storageInfo.cleanableBytes, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniStat(Modifier.weight(1f), "Used", Formatter.formatFileSize(LocalContext.current, storageInfo.usedBytes), Icons.Rounded.Storage, MaterialTheme.colorScheme.primary)
        MiniStat(Modifier.weight(1f), "Free", Formatter.formatFileSize(LocalContext.current, storageInfo.freeBytes), Icons.Rounded.FolderOpen, Color(0xFF4CAF50))
        if (cleanableBytes > 0) MiniStat(Modifier.weight(1f), "Cleanable", Formatter.formatFileSize(LocalContext.current, cleanableBytes), Icons.Rounded.Storage, MaterialTheme.colorScheme.error)
    }
}
@Composable private fun MiniStat(modifier: Modifier, label: String, value: String, icon: ImageVector, accent: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(16.dp), tint = accent)
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun CleanerDashboardHeader(storageInfo: StorageInfo, cleanableBytes: Long, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CleanerStorageArc(storageInfo, cleanableBytes)
        CleanerOverviewCard(storageInfo, cleanableBytes)
    }
}
