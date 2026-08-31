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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.cleaner.StorageInfo
import com.frerox.toolz.ui.theme.LocalPerformanceMode

@Composable
fun CleanerStorageArc(storageInfo: StorageInfo, cleanableBytes: Long = storageInfo.cleanableBytes, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val performanceMode = LocalPerformanceMode.current
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    val total = storageInfo.totalBytes.coerceAtLeast(1L).toFloat()
    val usedF = (storageInfo.usedBytes.toFloat() / total).coerceIn(0f, 1f)
    val cleanF = (cleanableBytes.toFloat() / total).coerceIn(0f, 1f)
    val baseF = (usedF - cleanF).coerceAtLeast(0f)
    val animBase by animateFloatAsState(targetValue = baseF, animationSpec = if (performanceMode) tween(300) else spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow), label = "arcBaseV2")
    val animClean by animateFloatAsState(targetValue = cleanF, animationSpec = if (performanceMode) tween(300) else spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow), label = "arcCleanV2")
    Box(modifier = modifier.size(260.dp), contentAlignment = Alignment.Center) {
        if (!performanceMode) {
            val infinite = rememberInfiniteTransition(label = "arcGlowV2")
            val glowScale by infinite.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "glowV2")
            Box(modifier = Modifier.size(210.dp).graphicsLayer { scaleX = glowScale; scaleY = glowScale }.background(Brush.radialGradient(listOf(primary.copy(alpha = 0.12f), Color.Transparent)), CircleShape))
        }
        val primaryBrush = remember(primary) { Brush.sweepGradient(listOf(primary.copy(alpha = 0.7f), primary)) }
        val errorBrush = remember(error) { Brush.sweepGradient(listOf(error.copy(alpha = 0.7f), error)) }
        Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            val sw = 36f
            val arcSz = Size(size.width - sw, size.height - sw)
            val tl = Offset(sw / 2f, sw / 2f)
            val start = 135f
            val sweep = 270f
            drawArc(outline, start, sweep, false, tl, arcSz, style = Stroke(sw, cap = StrokeCap.Round))
            if (animBase > 0f) drawArc(primaryBrush, start, sweep * animBase, false, tl, arcSz, style = Stroke(sw, cap = StrokeCap.Round))
            if (animClean > 0f) drawArc(errorBrush, start + sweep * animBase, sweep * animClean, false, tl, arcSz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Formatter.formatFileSize(context, storageInfo.usedBytes), style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp))
            Text("USED OF ${Formatter.formatFileSize(context, storageInfo.totalBytes)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.5.sp)
            if (cleanableBytes > 0) {
                Surface(color = Color(0xFF4CAF50).copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f)), modifier = Modifier.padding(top = 12.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.AutoDelete, null, Modifier.size(14.dp), tint = Color(0xFF4CAF50))
                        Text("${Formatter.formatFileSize(context, cleanableBytes)} cleanable", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
                    }
                }
            }
        }
    }
}
