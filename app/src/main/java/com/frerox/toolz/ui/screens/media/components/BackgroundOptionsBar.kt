/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.media.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.screens.media.PreviewBackground
import com.frerox.toolz.ui.theme.SquircleShape

/**
 * Floating background-mode selector. Compact: three labeled modes + color dots.
 */
@Composable
fun BackgroundOptionsBar(
    selected: PreviewBackground,
    onSelect: (PreviewBackground) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = SquircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        shadowElevation = 3.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            ModeChip(
                icon = Icons.Rounded.GridOn, label = "None",
                selected = selected is PreviewBackground.Transparent,
                onClick = { onSelect(PreviewBackground.Transparent) },
            )
            ModeChip(
                icon = Icons.Rounded.Circle, label = "White",
                selected = selected is PreviewBackground.White,
                onClick = { onSelect(PreviewBackground.White) },
            )
            ModeChip(
                icon = Icons.Rounded.BlurOn, label = "Blur",
                selected = selected is PreviewBackground.Blur,
                onClick = { onSelect(PreviewBackground.Blur) },
            )

            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(width = 1.dp, height = 22.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )

            ColorDot(Color(0xFF1C1B1F), selected matchesColor Color(0xFF1C1B1F)) {
                onSelect(PreviewBackground.Color(0xFF1C1B1F.toInt()))
            }
            ColorDot(Color(0xFF7C4DFF), selected matchesColor Color(0xFF7C4DFF)) {
                onSelect(PreviewBackground.Color(0xFF7C4DFF.toInt()))
            }
            ColorDot(Color(0xFFFF9800), selected matchesColor Color(0xFFFF9800)) {
                onSelect(PreviewBackground.Color(0xFFFF9800.toInt()))
            }
        }
    }
}

private infix fun PreviewBackground.matchesColor(color: Color): Boolean =
    this is PreviewBackground.Color && this.color == color.toArgb()

@Composable
private fun ModeChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = SquircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Icon(
                icon, null, modifier = Modifier.size(14.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(if (selected) 30.dp else 26.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(14.dp), tint = Color.White)
        }
    }
}
