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
            "https://www.reddit.com/r/videos/comments/123",
            "https://odysee.com/@user/video",
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

    @Test
    fun `image results bypass video domain filter completely`() {
        val imageResults = listOf(
            SearchResult(
                title = "Cute Cat",
                snippet = "A cute cat photo",
                url = "https://random-cat-blog.org/photos/cat1.jpg",
                displayUrl = "random-cat-blog.org"
            ),
            SearchResult(
                title = "Reddit Discussion Image",
                snippet = "Reddit cat",
                url = "https://www.reddit.com/r/cats/cat.png",
                displayUrl = "reddit.com"
            ),
            SearchResult(
                title = "External Image",
                snippet = "External image",
                url = "https://example.com/image.png",
                displayUrl = "example.com"
            )
        )

        val filtered = WebSearchRepository.filterMediaResults(SearchCategory.IMAGES, imageResults)
        org.junit.Assert.assertEquals(3, filtered.size)
        org.junit.Assert.assertEquals(imageResults, filtered)
    }

    @Test
    fun `video results filter to allowed domains when allowed exist`() {
        val videoResults = listOf(
            SearchResult(
                title = "YouTube Video",
                snippet = "Watch on YouTube",
                url = "https://www.youtube.com/watch?v=123",
                displayUrl = "youtube.com"
            ),
            SearchResult(
                title = "Blocked Blog Video",
                snippet = "Blog video",
                url = "https://random-blog.com/video/456",
                displayUrl = "random-blog.com"
            )
        )

        val filtered = WebSearchRepository.filterMediaResults(SearchCategory.VIDEOS, videoResults)
        org.junit.Assert.assertEquals(1, filtered.size)
        org.junit.Assert.assertEquals("https://www.youtube.com/watch?v=123", filtered.first().url)
    }

    @Test
    fun `video results fallback to unfiltered when no allowed domains survive`() {
        val unknownDomainVideos = listOf(
            SearchResult(
                title = "Indie Video 1",
                snippet = "Snippet 1",
                url = "https://indie-site.org/video/1",
                displayUrl = "indie-site.org"
            ),
            SearchResult(
                title = "Indie Video 2",
                snippet = "Snippet 2",
                url = "https://another-domain.net/video/2",
                displayUrl = "another-domain.net"
            )
        )

        val filtered = WebSearchRepository.filterMediaResults(SearchCategory.VIDEOS, unknownDomainVideos)
        org.junit.Assert.assertEquals(2, filtered.size)
        org.junit.Assert.assertEquals(unknownDomainVideos, filtered)
    }
}
