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

package com.frerox.toolz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val placeholderPalette = listOf(
    Color(0xFFFF6B6B),
    Color(0xFF4ECDC4),
    Color(0xFFFFD93D),
    Color(0xFF6C5CE7),
    Color(0xFFA29BFE),
    Color(0xFFFD79A8),
    Color(0xFF00CEC9),
    Color(0xFFE17055),
    Color(0xFF0984E3),
    Color(0xFF00B894),
    Color(0xFFE84393),
    Color(0xFF6D214F),
    Color(0xFFB33771),
    Color(0xFF0ABDE3),
    Color(0xFFF97F51),
    Color(0xFF25CCF7),
)

private val placeholderIcons = listOf(
    Icons.Rounded.MusicNote,
    Icons.Rounded.Audiotrack,
    Icons.Rounded.LibraryMusic,
    Icons.Rounded.GraphicEq,
)

private fun pickColor(seed: String): Color {
    val hash = seed.hashCode()
    val index = (hash and Int.MAX_VALUE) % placeholderPalette.size
    return placeholderPalette[index]
}

private fun pickIcon(seed: String): ImageVector {
    val hash = seed.hashCode()
    val index = ((hash ushr 8) and Int.MAX_VALUE) % placeholderIcons.size
    return placeholderIcons[index]
}

@Composable
fun AlbumArtImage(
    url: String?,
    seed: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    iconSize: Dp = 28.dp,
) {
    val color = remember(seed) { pickColor(seed) }
    val icon = remember(seed) { pickIcon(seed) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxSize().background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(iconSize), tint = color)
        }

        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
