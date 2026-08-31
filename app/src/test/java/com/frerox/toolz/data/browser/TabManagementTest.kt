package com.frerox.toolz.data.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabManagementTest {

    @Test
    fun `tab entry initialization handles normal and private flags`() {
        val normalTab = TabEntry(url = "https://example.com", title = "Example", isPrivate = false)
        val privateTab = TabEntry(url = "https://secret.org", title = "Secret", isPrivate = true)

        assertFalse(normalTab.isPrivate)
        assertTrue(privateTab.isPrivate)
        assertNotNull(normalTab.id)
        assertNotNull(privateTab.id)
        assertTrue(normalTab.createdAt > 0)
    }

    @Test
    fun `tab entry copy updates fields correctly`() {
        val tab = TabEntry(url = "https://example.com", title = "Example")
        val updated = tab.copy(
            url = "https://example.com/new",
            title = "New Title",
            isDesktopMode = true
        )

        assertEquals("https://example.com/new", updated.url)
        assertEquals("New Title", updated.title)
        assertTrue(updated.isDesktopMode)
        assertEquals(tab.id, updated.id)
    }
}
