/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.media.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.screens.media.PreviewBackground
import com.frerox.toolz.ui.theme.SquircleShape

/**
 * M3 Expressive pill bar for background preview mode.
 * Simple clear options: Transparent / White / Blur / Custom color.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundOptionsBar(
    selected: PreviewBackground,
    onSelect: (PreviewBackground) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = SquircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BgPill(
                label = "Transparent",
                icon = Icons.Rounded.GridView,
                selected = selected is PreviewBackground.Transparent,
                onClick = { onSelect(PreviewBackground.Transparent) },
            )
            BgPill(
                label = "White",
                icon = Icons.Rounded.Circle,
                selected = selected is PreviewBackground.White,
                onClick = { onSelect(PreviewBackground.White) },
            )
            BgPill(
                label = "Blur",
                icon = Icons.Rounded.BlurOn,
                selected = selected is PreviewBackground.Blur,
                onClick = { onSelect(PreviewBackground.Blur) },
            )
            // Color presets row — small dots
            ColorDot(Color(0xFF111111), selected is PreviewBackground.Color && selected.color == 0xFF111111.toInt(), onClick = { onSelect(PreviewBackground.Color(0xFF111111.toInt())) })
            ColorDot(Color(0xFF6750A4), selected is PreviewBackground.Color && selected.color == 0xFF6750A4.toInt(), onClick = { onSelect(PreviewBackground.Color(0xFF6750A4.toInt())) })
            ColorDot(Color(0xFFFB8C00), selected is PreviewBackground.Color && selected.color == 0xFFFB8C00.toInt(), onClick = { onSelect(PreviewBackground.Color(0xFFFB8C00.toInt())) })
            ColorDot(Color(0xFFE91E63), selected is PreviewBackground.Color && selected.color == 0xFFE91E63.toInt(), onClick = { onSelect(PreviewBackground.Color(0xFFE91E63.toInt())) })
        }
    }
}

@Composable
private fun RowScope.BgPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = SquircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(if (selected) 30.dp else 26.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f), CircleShape)
            .clickable(onClick = onClick),
    )
}
