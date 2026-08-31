/*
 * Copyright (C) 2026 Toolz Contributors
 */
package com.frerox.toolz.ui.components.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.cleaner.StorageInfo

@Composable
fun CleanerStorageArc(storageInfo: StorageInfo, cleanableBytes: Long = storageInfo.cleanableBytes, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val total = storageInfo.totalBytes.coerceAtLeast(1L).toFloat()
    val usedF = (storageInfo.usedBytes.toFloat() / total).coerceIn(0f, 1f)
    val cleanF = (cleanableBytes.toFloat() / total).coerceIn(0f, 1f)
    val baseF = (usedF - cleanF).coerceAtLeast(0f)
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Simple linear bar
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (baseF > 0f) Box(modifier = Modifier.weight(baseF).fillMaxHeight().background(MaterialTheme.colorScheme.primary)) {}
                if (cleanF > 0f) Box(modifier = Modifier.weight(cleanF).fillMaxHeight().background(MaterialTheme.colorScheme.error.copy(alpha = 0.7f))) {}
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(Formatter.formatFileSize(context, storageInfo.usedBytes), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp))
                Text("Used of ${Formatter.formatFileSize(context, storageInfo.totalBytes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (cleanableBytes > 0) {
                Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp)) {
                    Text("${Formatter.formatFileSize(context, cleanableBytes)} cleanable", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
