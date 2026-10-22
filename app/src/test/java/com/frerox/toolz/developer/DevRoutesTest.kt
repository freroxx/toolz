/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.developer

import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.ui.screens.developer.DevRouteCategory
import com.frerox.toolz.ui.screens.developer.devRoutes
import com.frerox.toolz.ui.screens.developer.filterDevRoutes
import com.frerox.toolz.ui.screens.developer.components.routeToDevTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevRoutesTest {

    @Test
    fun `every sandboxed tool is reachable from a real route`() {
        val samples = mapOf(
            DevToolId.BATTERY to "battery_info",
            DevToolId.DEVICE to "device_info",
            DevToolId.NETWORK to "network_power_suite",
            DevToolId.STEPS to "step_counter",
            DevToolId.SENSORS to "compass",
            DevToolId.MUSIC to "music_player",
            DevToolId.DOWNLOADER to "media_downloader",
            DevToolId.CLEANER to "file_cleaner",
            DevToolId.NOTIFICATIONS to "notification_vault",
            DevToolId.CLIPBOARD to "clipboard",
            DevToolId.TASKS to "todo",
            DevToolId.QR to "qr_generator",
            DevToolId.TIMERS to "timer",
            DevToolId.UPDATE_NEWS to "update",
            DevToolId.WHISPER to "whisper",
            DevToolId.SEARCH to "search",
            DevToolId.FILES to "pdf_reader",
            DevToolId.SYSTEM to "flashlight",
        )

        assertEquals(DevToolId.entries.toSet(), samples.keys)
        samples.forEach { (tool, route) -> assertEquals(tool, routeToDevTool(route)) }
    }

    @Test
    fun `route lookup ignores query strings and path arguments`() {
        assertEquals(DevToolId.WHISPER, routeToDevTool("whisper_chat/42?from=notification"))
        assertEquals(DevToolId.NETWORK, routeToDevTool("wifi_tweaks?tab=2"))
        assertEquals(DevToolId.FILES, routeToDevTool("background_remover"))
    }

    @Test
    fun `unknown and blank routes do not claim a sandbox`() {
        assertNull(routeToDevTool(null))
        assertNull(routeToDevTool(""))
        assertNull(routeToDevTool("settings"))
        assertNull(routeToDevTool("unknown_route/42"))
    }

    @Test
    fun `route catalog is unique and searchable`() {
        assertEquals(devRoutes.size, devRoutes.map { it.route }.toSet().size)
        assertTrue(devRoutes.all { it.label.isNotBlank() && it.route.isNotBlank() })
        assertEquals(devRoutes, filterDevRoutes("  "))
        assertEquals(devRoutes, filterDevRoutes(""))
        assertTrue(filterDevRoutes("pdf").any { it.route == "pdf_reader" })
        assertTrue(filterDevRoutes("APP & SYSTEM").all { it.category == DevRouteCategory.SYSTEM })
    }
}
