/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.downloader

import kotlinx.serialization.Serializable

/**
 * DTOs mirroring toolz-downloadz-api `shape()` response.
 * See toolz-downloadz-api/api/index.py:605 shape() + normalize().
 */
@Serializable
data class DownloadzFormatDto(
    val format_id: String? = null,
    val ext: String? = null,
    val resolution: String? = null,
    val url: String? = null,
    val filesize: Long? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val height: Int? = null,
    val tbr: Double? = null,
    val abr: Double? = null,
    val headers: Map<String, String> = emptyMap(),
    val cookies: String? = null,
)

@Serializable
data class DownloadzFormatsDto(
    val video: List<DownloadzFormatDto> = emptyList(),
    val audio: List<DownloadzFormatDto> = emptyList(),
)

@Serializable
data class DownloadzStatsDto(
    val view_count: Long? = null,
    val like_count: Long? = null,
    val comment_count: Long? = null,
)

@Serializable
data class DownloadzQualityOption(
    val f: String = "",
    val label: String = "",
)

@Serializable
data class DownloadzExtractResponse(
    /** Opaque v1 extraction handle. It never contains an upstream media URL. */
    val id: String? = null,
    val platform: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val duration: Long? = null,
    val uploader: String? = null,
    val uploader_url: String? = null,
    val stats: DownloadzStatsDto? = null,
    val upload_date: String? = null,
    val description: String? = null,
    val download_url: String? = null,
    val download_headers: Map<String, String> = emptyMap(),
    val download_cookies: String? = null,
    val ext: String? = null,
    val blocked: Boolean = false,
    val blocked_message: String? = null,
    val source: String? = null,
    val formats: DownloadzFormatsDto = DownloadzFormatsDto(),
    val quality_options: List<DownloadzQualityOption> = emptyList(),
    val original_url: String? = null,
    val media_kind: String? = null,
    val assets: List<DownloadzAssetDto> = emptyList(),
)

/** Safe asset metadata returned by /api/v1/extractions. */
@Serializable
data class DownloadzAssetDto(
    val id: String = "",
    val kind: String = "video",
    val ext: String? = null,
    val mime_type: String? = null,
    val filesize: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Long? = null,
    val resolution: String? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val has_audio: Boolean? = null,
    val thumbnail: String? = null,
    val download_path: String? = null,
)

@Serializable
data class DownloadzGuestSessionRequest(val installation_id: String)

@Serializable
data class DownloadzGuestSessionResponse(
    val access_token: String = "",
    val token_type: String = "Bearer",
    val expires_in: Long = 0,
)

@Serializable
data class DownloadzV1ExtractRequest(
    val url: String,
    val audio_only: Boolean = false,
)

@Serializable
data class DownloadzPlatformDto(
    val id: String = "",
    val name: String? = null,
)

@Serializable
data class DownloadzPlatformsResponse(
    val platforms: List<DownloadzPlatformDto> = emptyList(),
)
