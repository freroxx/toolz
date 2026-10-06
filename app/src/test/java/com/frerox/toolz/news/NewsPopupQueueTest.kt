package com.frerox.toolz.news

import com.frerox.toolz.data.news.NewsEntity
import com.frerox.toolz.data.news.NewsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsPopupQueueTest {
    private fun entity(id: String, priority: String) = NewsEntity(
        id = id, title = "T", body = "B", imageUrl = null, actionLabel = null, actionUrl = null,
        priority = priority, status = "published", pinned = false, publishAt = 0L, expiresAt = null,
        minAppVersion = null, maxAppVersion = null, onlyVersionsCsv = "", excludedVersionsCsv = "",
        delaySeconds = 0, frequency = "once", intervalHours = null, maxImpressions = null,
        dismissible = true, showInHistory = true, requiresAction = false, notify = true,
        disappearing = false, receivedAt = 0L
    )

    @Test
    fun critical_replaces_nonCritical_but_not_vice_versa() {
        val promo = entity("p", "promo")
        val critical = entity("c", "critical")
        assertTrue(NewsRepository.shouldReplacePopup(null, promo))
        assertFalse(NewsRepository.shouldReplacePopup(promo, promo))
        assertTrue(NewsRepository.shouldReplacePopup(promo, critical))
        assertFalse(NewsRepository.shouldReplacePopup(critical, promo))
        assertFalse(NewsRepository.shouldReplacePopup(critical, null))
    }
}
