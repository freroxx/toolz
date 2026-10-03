package com.frerox.toolz.news

import com.frerox.toolz.data.news.NewsFeedDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NewsDtoTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun feed_parsesAndToleratesUnknownFields() {
        val raw = """{"version":1,"appVersion":"1.1.6","count":1,"news":[{"schemaVersion":1,"id":"abc","title":"Hi","body":"Yo","priority":"critical","status":"published","futureField":"kept-out"}],"extra":42}"""
        val feed = json.decodeFromString(NewsFeedDto.serializer(), raw)
        assertEquals(1, feed.news.size)
        assertEquals("abc", feed.news[0].id)
        assertEquals("critical", feed.news[0].priority)
        assertFalse(feed.degraded)
    }
}
