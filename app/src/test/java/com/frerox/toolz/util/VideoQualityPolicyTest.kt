/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.util

import com.frerox.toolz.data.catalog.CatalogRepository
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression tests for the 640x360 cap: presets must not silently accept a
 * 360p muxed stream for 720p/1080p requests.
 */
class VideoQualityPolicyTest {

    @Test
    fun `360p muxed rejected for 720p and 1080p requests`() {
        assertFalse(VideoQualityPolicy.isDirectAcceptable(360, 720))
        assertFalse(VideoQualityPolicy.isDirectAcceptable(360, 1080))
        assertFalse(VideoQualityPolicy.isDirectAcceptable(360, 2160))
    }

    @Test
    fun `exact and higher muxed accepted`() {
        assertTrue(VideoQualityPolicy.isDirectAcceptable(720, 720))
        assertTrue(VideoQualityPolicy.isDirectAcceptable(1080, 1080))
        assertTrue(VideoQualityPolicy.isDirectAcceptable(720, 480))
    }

    @Test
    fun `SD leniency keeps 480p requests working on 360p-only videos`() {
        assertTrue(VideoQualityPolicy.isDirectAcceptable(360, 480))
        assertTrue(VideoQualityPolicy.isDirectAcceptable(360, 360))
        assertTrue(VideoQualityPolicy.isDirectAcceptable(240, 240))
    }

    @Test
    fun `low DASH pairs rejected for HD asks`() {
        // 144p/360p DASH is not a substitute for 1080p — yt-dlp should try next.
        assertFalse(VideoQualityPolicy.isHdPairAcceptable(144, 1080))
        assertFalse(VideoQualityPolicy.isHdPairAcceptable(360, 1080))
        assertTrue(VideoQualityPolicy.isHdPairAcceptable(720, 1080))
        assertTrue(VideoQualityPolicy.isHdPairAcceptable(1080, 1080))
        assertTrue(VideoQualityPolicy.isHdPairAcceptable(720, 720))
        assertTrue(VideoQualityPolicy.isHdPairAcceptable(360, 360))
    }

    @Test
    fun `offered heights capped at real max`() {
        // 360p-only video must not offer 720p/1080p.
        assertEquals(listOf(360, 240), VideoQualityPolicy.offeredHeights(listOf(360)))
        // HD video offers full ladder down to SD.
        val hd = VideoQualityPolicy.offeredHeights(listOf(360, 720, 1080))
        assertTrue(hd.contains(1080) && hd.contains(720) && hd.contains(480))
        // Unknown probe offers everything (if-available UX).
        assertEquals(VideoQualityPolicy.ladder, VideoQualityPolicy.offeredHeights(emptyList()))
    }

    @Test
    fun `downgrade detection has tolerance`() {
        assertTrue(VideoQualityPolicy.isDowngrade(360, 1080))
        assertTrue(VideoQualityPolicy.isDowngrade(360, 720))
        assertFalse(VideoQualityPolicy.isDowngrade(720, 720))
        assertFalse(VideoQualityPolicy.isDowngrade(680, 720)) // within 60p tolerance
        assertFalse(VideoQualityPolicy.isDowngrade(null, 720))
    }

    @Test
    fun `stream height parser handles 720p60 and WxH`() {
        assertEquals(720, CatalogRepository.parseStreamHeight("720p"))
        assertEquals(720, CatalogRepository.parseStreamHeight("720p60"))
        assertEquals(720, CatalogRepository.parseStreamHeight("1280x720"))
        assertEquals(1080, CatalogRepository.parseStreamHeight("1080p"))
        assertNull(CatalogRepository.parseStreamHeight(null))
        assertNull(CatalogRepository.parseStreamHeight("unknown"))
    }
}
