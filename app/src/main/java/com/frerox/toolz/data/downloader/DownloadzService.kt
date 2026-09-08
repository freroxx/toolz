/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.downloader

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service for toolz-downloadz-api.
 * Mirrors the Next.js proxy contract (toolz-downloadz/src/app/api/extract/route.js):
 *   GET /api/extract?url=&audio_only=
 * Auth via X-API-KEY header (see check_auth in api/index.py:804).
 */
interface DownloadzService {

    @GET("api/extract")
    suspend fun extract(
        @Query("url") url: String,
        @Query("audio_only") audioOnly: Boolean = false,
    ): DownloadzExtractResponse

    @GET("api/platforms")
    suspend fun platforms(): DownloadzPlatformsResponse
}
