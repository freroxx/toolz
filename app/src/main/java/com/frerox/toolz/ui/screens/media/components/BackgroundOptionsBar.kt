/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.media.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.ColorLens
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.R
import com.frerox.toolz.ui.screens.media.PreviewBackground
import com.frerox.toolz.ui.theme.SquircleShape

/**
 * Floating background-mode selector: Transparent, White, Blur, and a Background chip
 * that opens the full color/image picker sheet.
 */
@Composable
fun BackgroundOptionsBar(
    selected: PreviewBackground,
    onSelect: (PreviewBackground) -> Unit,
    onOpenColorPicker: () -> Unit,
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
                icon = Icons.Rounded.GridOn,
                label = stringResource(R.string.st_BackgroundRemover_ModeNone),
                selected = selected is PreviewBackground.Transparent,
                onClick = { onSelect(PreviewBackground.Transparent) },
            )
            ModeChip(
                icon = Icons.Rounded.Circle,
                label = stringResource(R.string.st_BackgroundRemover_ModeWhite),
                selected = selected is PreviewBackground.White,
                onClick = { onSelect(PreviewBackground.White) },
            )
            ModeChip(
                icon = Icons.Rounded.BlurOn,
                label = stringResource(R.string.st_BackgroundRemover_ModeBlur),
                selected = selected is PreviewBackground.Blur,
                onClick = { onSelect(PreviewBackground.Blur) },
            )

            // Background chip — opens full color / image picker
            val bgActive = selected is PreviewBackground.Color || selected is PreviewBackground.CustomImage
            val activeColor = (selected as? PreviewBackground.Color)?.color
            BgPickerChip(
                label = stringResource(R.string.st_BackgroundRemover_BgChip),
                active = bgActive,
                activeColor = activeColor?.let { Color(it) },
                onClick = onOpenColorPicker,
            )
        }
    }
}

@Composable
private fun BgPickerChip(
    label: String,
    active: Boolean,
    activeColor: Color?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = SquircleShape,
        color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = if (active) null else androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            if (active && activeColor != null) {
                // Show the active custom color as a small dot
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(activeColor)
                        .border(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f), CircleShape),
                )
            } else {
                Icon(
                    Icons.Rounded.ColorLens, null,
                    modifier = Modifier.size(14.dp),
                    tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

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
