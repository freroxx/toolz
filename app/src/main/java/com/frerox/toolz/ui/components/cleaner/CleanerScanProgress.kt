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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.components.ExtraLargeExpressiveShape
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.theme.LocalPerformanceMode

@Composable
fun CleanerScanProgress(currentCategory: String, progress: Float, filesScanned: Int, foundSize: Long, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CleanerScanningIndicator()
        Spacer(Modifier.height(28.dp))
        Text(currentCategory, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        val anim by animateFloatAsState(progress, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "scanProg")
        LinearProgressIndicator(progress = { anim }, modifier = Modifier.fillMaxWidth(0.85f).height(8.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, strokeCap = StrokeCap.Round)
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CleanerStatPill(Icons.Rounded.FindInPage, "Files", "$filesScanned")
            CleanerStatPill(Icons.Rounded.DeleteSweep, "Junk", Formatter.formatFileSize(LocalContext.current, foundSize))
        }
    }
}
@Composable
private fun CleanerStatPill(icon: ImageVector, label: String, value: String) {
    Surface(shape = LargeExpressiveShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
private fun CleanerScanningIndicator(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val performanceMode = LocalPerformanceMode.current
    val infinite = rememberInfiniteTransition(label = "scanV2")
    val rotation by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "rotV2")
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(220.dp)) {
        if (!performanceMode) {
            val pulse by infinite.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulseV2")
            Box(modifier = Modifier.size(160.dp).scale(pulse).background(primary.copy(alpha = 0.09f), CircleShape).border(1.dp, primary.copy(alpha = 0.18f), CircleShape))
        }
        Canvas(modifier = Modifier.size(190.dp)) {
            val sw = 6.dp.toPx()
            drawCircle(primary.copy(alpha = 0.05f), size.minDimension / 2f, style = Stroke(sw))
            rotate(rotation) {
                drawArc(Brush.sweepGradient(0f to primary.copy(alpha = 0f), 0.5f to primary, 1f to primary.copy(alpha = 0f)), 0f, 180f, false, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        Surface(modifier = Modifier.size(78.dp), shape = ExtraLargeExpressiveShape, color = primary, shadowElevation = 14.dp) {
            Box(contentAlignment = Alignment.Center) {
                val s by infinite.animateFloat(0.8f, 1.1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "icS")
                Icon(Icons.Rounded.TravelExplore, null, Modifier.size(36.dp).scale(s), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
