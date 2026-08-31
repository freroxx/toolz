package com.frerox.toolz.data.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFilteringTest {

    @Test
    fun `allowed video domains pass filter`() {
        val allowedUrls = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=12345",
            "https://www.tiktok.com/@user/video/1234567890",
            "https://vimeo.com/12345678",
            "https://www.twitch.tv/videos/123456",
            "https://www.dailymotion.com/video/x8xyz",
        )

        for (url in allowedUrls) {
            assertTrue("Expected allowed for $url", WebSearchRepository.isAllowedVideoTarget(url))
        }
    }

    @Test
    fun `disallowed domains are blocked from native video results`() {
        val blockedUrls = listOf(
            "https://example.com/video.mp4",
            "https://random-blog.net/embed/123",
            "https://search.brave.com/videos",
            "https://duckduckgo.com/?q=video",
            "https://bing.com/videos/search",
            "https://ad-network.com/tracker",
        )

        for (url in blockedUrls) {
            assertFalse("Expected blocked for $url", WebSearchRepository.isAllowedVideoTarget(url))
        }
    }
}
