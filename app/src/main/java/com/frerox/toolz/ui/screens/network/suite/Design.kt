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

package com.frerox.toolz.ui.screens.network.suite

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * THE design system for the Network suite. Simple M3 Expressive:
 *  - one card shape (28dp), tonal surfaces, no alpha-stacking, no outlines
 *  - color roles only (primary / tertiary / error mapped by score) — zero hardcoded hexes
 *  - value-first stat tiles, label-over-content section headers
 * Every new/edited component must compose these; raw Card/ElevatedCard usage in
 * feature code is a review failure.
 */
object NetTokens {
    val CardShape = RoundedCornerShape(28.dp)
    val InnerShape = RoundedCornerShape(16.dp)
    val SpacingS = 8.dp
    val SpacingM = 12.dp
    val SpacingL = 16.dp
    val SpacingXL = 24.dp
}

/** Theme-mapped severity tint. The ONLY sanctioned score→color mapping. */
@Composable
fun healthTint(score: Int): Color = when {
    score >= 80 -> MaterialTheme.colorScheme.primary
    score >= 55 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

/**
 * The single surface for all feature content.
 * Tonal (surfaceContainerLow), 28dp, optional header row: [icon] tile + title + subtitle + trailing.
 */
@Composable
fun NetCard(
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = NetTokens.SpacingL,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = NetTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(NetTokens.SpacingM)
        ) {
            if (title != null || trailing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NetTokens.SpacingM)
                ) {
                    if (icon != null) {
                        Surface(
                            shape = NetTokens.InnerShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (title != null) {
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

/** Small uppercase label introducing a group of cards. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = NetTokens.SpacingS, top = NetTokens.SpacingS)
    )
}

/** Value-first metric tile — number leads, label whispers below. */
@Composable
fun StatTile(label: String, value: String, subvalue: String? = null, tint: Color? = null, modifier: Modifier = Modifier) {
    Surface(
        shape = NetTokens.InnerShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = NetTokens.SpacingM, vertical = NetTokens.SpacingM),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = tint ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (subvalue != null) {
                Text(subvalue, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Compact status pill using tertiary/secondary containers — replaces ad-hoc StatusBadge hexes. */
@Composable
fun NetPill(text: String, emphasized: Boolean = false, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (emphasized) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Minimal single-color health arc (track + progress), no rainbow gradients. */
@Composable
fun ScoreArc(score: Int, sizeDp: Int = 84, modifier: Modifier = Modifier) {
    val tint = healthTint(score)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val stroke = 9.dp
    Box(modifier = modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sweep = 280f
            val startAngle = 130f
            drawArc(
                color = track,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = tint,
                startAngle = startAngle,
                sweepAngle = sweep * (score.coerceIn(0, 100) / 100f),
                useCenter = false,
                style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${score.coerceIn(0, 100)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = tint)
            Text("SCORE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
