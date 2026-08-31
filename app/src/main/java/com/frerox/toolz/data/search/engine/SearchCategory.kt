/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine

import androidx.compose.runtime.Immutable

enum class SearchCategory { ALL, IMAGES, NEWS, VIDEOS, SHOPPING }
enum class SafeSearchLevel { OFF, MODERATE, STRICT }
@Immutable
data class EngineCapabilities(val supportsImages: Boolean = true, val supportsNews: Boolean = true, val supportsVideos: Boolean = true, val supportsSafeSearch: Boolean = true, val safeSearchParam: String = "")
val EngineCapabilitiesMatrix: Map<EngineId, EngineCapabilities> = mapOf(
    EngineId.YAHOO to EngineCapabilities(supportsImages = false, supportsVideos = false),
    EngineId.QWANT to EngineCapabilities(supportsImages = true, supportsNews = true, supportsVideos = true),
    EngineId.MARGINALIA to EngineCapabilities(supportsImages = false, supportsNews = false, supportsVideos = false),
    EngineId.BING to EngineCapabilities(supportsImages = true, supportsNews = true, supportsVideos = true),
    EngineId.META to EngineCapabilities(supportsImages = true, supportsNews = true, supportsVideos = true),
    EngineId.CUSTOM to EngineCapabilities()
)
