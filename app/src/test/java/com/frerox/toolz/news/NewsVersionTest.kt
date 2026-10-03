package com.frerox.toolz.news

import com.frerox.toolz.data.news.NewsVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsVersionTest {
    @Test
    fun compare_ordersVersions() {
        assertTrue(NewsVersion.compare("1.1.6", "1.1.0") > 0)
        assertTrue(NewsVersion.compare("1.2.0", "1.1.6") > 0)
        assertTrue(NewsVersion.compare("1.0.0", "1.1.0") < 0)
    }

    @Test
    fun compare_treatsShortAndPrereleaseEqual() {
        assertEquals(0, NewsVersion.compare("1.1", "1.1.0"))
        assertEquals(0, NewsVersion.compare("1.2.0-beta", "1.2.0"))
    }

    @Test
    fun eligibility_rangeAndLists() {
        assertTrue(NewsVersion.isEligible("1.1.6", "1.1.0", "1.9.9", emptyList(), emptyList()))
        assertFalse(NewsVersion.isEligible("1.0.0", "1.1.0", null, emptyList(), emptyList()))
        assertTrue(NewsVersion.isEligible("1.1.6", null, null, listOf("1.1.6", "1.2.0"), emptyList()))
        assertFalse(NewsVersion.isEligible("1.2.0", null, null, listOf("1.1.6"), emptyList()))
        assertFalse(NewsVersion.isEligible("1.1.6", null, null, emptyList(), listOf("1.1.6")))
    }
}
