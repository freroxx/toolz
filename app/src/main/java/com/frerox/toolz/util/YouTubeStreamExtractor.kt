/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.util

import android.content.Context
import com.frerox.toolz.data.catalog.CatalogEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts direct stream URL for YouTube videoId using CatalogRepository (InnerTube + NewPipeExtractor).
 * Returns direct MP4 streaming URL playable natively by ExoPlayer.
 */
object YouTubeStreamExtractor {
    suspend fun extractYouTubeStreamUrl(videoId: String, maxHeight: Int = 720, context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                CatalogEntryPoint::class.java
            )
            val repository = entryPoint.catalogRepository()
            repository.resolveVideoStream("https://www.youtube.com/watch?v=$videoId", maxHeight)
        } catch (e: Exception) {
            android.util.Log.e("YouTubeExtractor", "extract failed for $videoId", e)
            null
        }
    }
}
