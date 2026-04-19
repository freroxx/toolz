package com.frerox.toolz.data.catalog

import androidx.compose.runtime.Immutable

/**
 * Lightweight model for catalog (cloud) tracks fetched via NewPipeExtractor.
 * Not persisted in Room — purely in-memory for streaming.
 */
@Immutable
data class CatalogTrack(
    val id: String,             // YouTube video ID
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val streamUrl: String? = null, // Resolved lazily via CatalogRepository.resolveAudioStream()
    val duration: Long,         // Duration in millis
    val sourceUrl: String       // Original YouTube URL for re-extraction
)
