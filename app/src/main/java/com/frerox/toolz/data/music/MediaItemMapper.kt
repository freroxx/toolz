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

package com.frerox.toolz.data.music

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.frerox.toolz.data.catalog.CatalogTrack
import java.io.File

/**
 * P1-06 fix: single source of truth for MediaItem mapping.
 * Previously duplicated in MusicPlayerService.kt:739 and MusicPlayerViewModel.kt:1282
 * with subtle drift (path handling order). Now consolidated and unit-testable.
 */
fun MusicTrack.toMediaItem(): MediaItem {
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist ?: "Unknown Artist")
        .setAlbumTitle(album ?: "Unknown Album")
        .setDisplayTitle(title)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setIsPlayable(true)
        .setArtworkUri(thumbnailUri?.toUri())
        .apply {
            sourceUrl?.let { setExtras(Bundle().apply { putString("source_url", it) }) }
        }
        .build()
    val playableUri = when {
        path != null && File(path).exists() -> Uri.fromFile(File(path)).toString()
        uri.startsWith("content://") || uri.startsWith("file://") -> uri
        path != null && (path.startsWith("content://") || path.startsWith("file://")) -> path
        path != null && path.startsWith("/") -> Uri.fromFile(File(path)).toString()
        else -> uri
    }
    val parsedUri = if (playableUri.startsWith("/")) Uri.fromFile(File(playableUri)) else playableUri.toUri()
    return MediaItem.Builder()
        .setMediaId(uri)
        .setUri(parsedUri)
        .setMediaMetadata(meta)
        .build()
}

fun CatalogTrack.toMediaItem(): MediaItem {
    val meta = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle("YouTube Catalog")
        .setDisplayTitle(title)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setIsPlayable(true)
        .setArtworkUri(thumbnailUrl.toUri())
        .setExtras(Bundle().apply {
            putString("source_url", sourceUrl)
            putBoolean("is_catalog", true)
        }).build()
    return MediaItem.Builder()
        .setMediaId(sourceUrl)
        .setUri(sourceUrl.toUri())
        .setMediaMetadata(meta)
        .build()
}
