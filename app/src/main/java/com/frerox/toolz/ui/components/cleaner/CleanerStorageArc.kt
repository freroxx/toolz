/*
 * Copyright (C) 2026 Toolz Contributors
 */
package com.frerox.toolz.ui.components.cleaner

import android.text.format.Formatter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    // Double precision: Float loses MBs above 16MB total
    val total = storageInfo.totalBytes.coerceAtLeast(1L).toDouble()
    val usedF = (storageInfo.usedBytes.toDouble() / total).coerceIn(0.0, 1.0)
    val cleanF = (cleanableBytes.toDouble() / total).coerceIn(0.0, 1.0)
    val baseF = (usedF - cleanF).coerceAtLeast(0.0)

    val animBase by animateFloatAsState(
        targetValue = baseF.toFloat(),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "anim_storage_base"
    )
    val animClean by animateFloatAsState(
        targetValue = cleanF.toFloat(),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "anim_storage_clean"
    )

    val usedPercent = (usedF * 100).toInt()
    val freePercent = (100 - usedPercent).coerceAtLeast(0)

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Multi-segment storage bar: used-clean | cleanable | free
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (animBase > 0.001f) {
                    Box(modifier = Modifier.weight(animBase).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
                if (animClean > 0.001f) {
                    Box(modifier = Modifier.weight(animClean).fillMaxHeight().background(MaterialTheme.colorScheme.error.copy(alpha = 0.85f)))
                }
                val remainder = (1f - (animBase + animClean)).coerceAtLeast(0f)
                if (remainder > 0.001f) {
                    Spacer(modifier = Modifier.weight(remainder))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        Formatter.formatFileSize(context, storageInfo.usedBytes),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "$usedPercent% used",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    "Total ${Formatter.formatFileSize(context, storageInfo.totalBytes)} • ${Formatter.formatFileSize(context, storageInfo.freeBytes)} ($freePercent%) free",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (cleanableBytes > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "${Formatter.formatFileSize(context, cleanableBytes)} cleanable",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
