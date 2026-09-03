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

package com.frerox.toolz.data.catalog.innertube

import kotlinx.serialization.Serializable

@Serializable
data class InnerTubeSearchResponse(
    val contents: Contents? = null
)

@Serializable
data class InnerTubePlayerResponse(
    val playabilityStatus: PlayabilityStatus? = null,
    val streamingData: StreamingData? = null
)

@Serializable
data class PlayabilityStatus(
    val status: String? = null,
    val reason: String? = null
)

@Serializable
data class StreamingData(
    val formats: List<AdaptiveFormat>? = null,
    val adaptiveFormats: List<AdaptiveFormat>? = null
)

@Serializable
data class AdaptiveFormat(
    val itag: Int? = null,
    val url: String? = null,
    val signatureCipher: String? = null,
    val mimeType: String? = null,
    val bitrate: Long? = null,
    val averageBitrate: Long? = null,
    val contentLength: String? = null,
    val audioQuality: String? = null,
    val approxDurationMs: String? = null,
    val audioSampleRate: String? = null,
    val audioChannels: Int? = null,
    val loudnessDb: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    val qualityLabel: String? = null
)

@Serializable
data class Contents(
    val tabbedSearchResultsRenderer: TabbedSearchResultsRenderer? = null
)

@Serializable
data class TabbedSearchResultsRenderer(
    val tabs: List<Tab>? = null
)

@Serializable
data class Tab(
    val tabRenderer: TabRenderer? = null
)

@Serializable
data class TabRenderer(
    val content: TabContent? = null
)

@Serializable
data class TabContent(
    val sectionListRenderer: SectionListRenderer? = null
)

@Serializable
data class SectionListRenderer(
    val contents: List<SectionContent>? = null
)

@Serializable
data class SectionContent(
    val musicShelfRenderer: MusicShelfRenderer? = null
)

@Serializable
data class MusicShelfRenderer(
    val contents: List<MusicShelfContent>? = null,
    val continuations: List<Continuation>? = null
)

@Serializable
data class Continuation(
    val nextContinuationData: NextContinuationData? = null
)

@Serializable
data class NextContinuationData(
    val continuation: String? = null
)

@Serializable
data class MusicShelfContent(
    val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null
)

@Serializable
data class MusicResponsiveListItemRenderer(
    val flexColumns: List<FlexColumn>? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val playlistItemData: PlaylistItemData? = null
)

@Serializable
data class FlexColumn(
    val musicResponsiveListItemFlexColumnRenderer: MusicResponsiveListItemFlexColumnRenderer? = null
)

@Serializable
data class MusicResponsiveListItemFlexColumnRenderer(
    val text: TextRenderer? = null
)

@Serializable
data class TextRenderer(
    val runs: List<TextRun>? = null
)

@Serializable
data class TextRun(
    val text: String? = null
)

@Serializable
data class ThumbnailRenderer(
    val musicThumbnailRenderer: MusicThumbnailRenderer? = null
)

@Serializable
data class MusicThumbnailRenderer(
    val thumbnail: Thumbnails? = null
)

@Serializable
data class Thumbnails(
    val thumbnails: List<Thumbnail>? = null
)

@Serializable
data class Thumbnail(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class PlaylistItemData(
    val videoId: String? = null
)
