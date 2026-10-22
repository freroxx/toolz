/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.developer

import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.SpoofParams
import com.frerox.toolz.ui.screens.developer.DevCrashTest
import com.frerox.toolz.ui.screens.developer.DevNotificationTest
import com.frerox.toolz.ui.screens.developer.DevPreset
import com.frerox.toolz.ui.screens.developer.DEV_NOTIFICATION_ID_END
import com.frerox.toolz.ui.screens.developer.DEV_NOTIFICATION_ID_START
import com.frerox.toolz.ui.screens.developer.DevToolGroup
import com.frerox.toolz.ui.screens.developer.fieldSpecs
import com.frerox.toolz.ui.screens.developer.filterSandboxTools
import com.frerox.toolz.ui.screens.developer.group
import com.frerox.toolz.ui.screens.developer.isDevNotificationId
import com.frerox.toolz.ui.screens.developer.spoofFixtureSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevCatalogTest {

    @Test
    fun `all eighteen sandbox preference keys are stable and unique`() {
        val expected = listOf(
            "spoof_battery",
            "spoof_device",
            "spoof_network",
            "spoof_steps",
            "spoof_sensors",
            "spoof_music",
            "spoof_downloader",
            "spoof_cleaner",
            "spoof_notifications",
            "spoof_clipboard",
            "spoof_tasks",
            "spoof_qr",
            "spoof_timers",
            "spoof_update_news",
            "spoof_whisper",
            "spoof_search",
            "spoof_files",
            "spoof_system",
        )

        val actual = DevToolId.entries.map { it.prefKey }

        assertEquals(expected, actual)
        assertEquals(18, actual.size)
        assertEquals(actual.size, actual.toSet().size)
        assertTrue(actual.all { it.startsWith("spoof_") })
    }

    @Test
    fun `every tool has exactly one group and a visible label`() {
        val grouped = DevToolId.entries.groupBy { it.group }

        assertEquals(DevToolId.entries.toSet(), grouped.values.flatten().toSet())
        assertTrue(DevToolId.entries.all { it.title.isNotBlank() && it.subtitle.isNotBlank() })
        assertTrue(DevToolGroup.entries.all { it.title.isNotBlank() })
    }

    @Test
    fun `presets contain valid tools and round-trip their fixture values`() {
        assertEquals(3, DevPreset.entries.size)
        assertEquals(DevPreset.entries.size, DevPreset.entries.map { it.title }.toSet().size)

        DevPreset.entries.forEach { preset ->
            assertTrue("${preset.title} is empty", preset.tools.isNotEmpty())
            assertEquals(preset.tools.size, preset.toolCount)
            assertEquals(preset.tools.map { it.prefKey }.toSet(), preset.prefKeys)
            assertEquals(preset.params, SpoofParams.parse(preset.params.toJson()))
        }
    }

    @Test
    fun `crash lab has four stable history keys`() {
        val types = DevCrashTest.entries.map { it.historyType }

        assertEquals(listOf("NPE", "IllegalState", "ANR", "OOM"), types)
        assertTrue(DevCrashTest.entries.all { it.title.isNotBlank() && it.actionLabel.isNotBlank() })
    }

    @Test
    fun `notification tests use the reserved id band`() {
        val ids = DevNotificationTest.entries.map { it.id }

        assertEquals((DEV_NOTIFICATION_ID_START..DEV_NOTIFICATION_ID_END).toList(), ids)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ids.size, DevNotificationTest.entries.map { it.channelId }.toSet().size)
        assertTrue(DevNotificationTest.entries.all { it.title.startsWith("[DEV] ") })
        assertTrue(isDevNotificationId(DEV_NOTIFICATION_ID_START))
        assertTrue(isDevNotificationId(DEV_NOTIFICATION_ID_END))
        assertFalse(isDevNotificationId(DEV_NOTIFICATION_ID_START - 1))
        assertFalse(isDevNotificationId(DEV_NOTIFICATION_ID_END + 1))
    }

    @Test
    fun `field specs are well formed and editable`() {
        val params = SpoofParams()

        DevToolId.entries.forEach { tool ->
            tool.fieldSpecs.forEach { spec ->
                assertTrue("${tool.name} has a blank field label", spec.label.isNotBlank())
                when (spec) {
                    is com.frerox.toolz.ui.screens.developer.SpoofFieldSpec.FixtureSlider -> {
                        assertTrue("${tool.name}/${spec.label} has inverted bounds", spec.min <= spec.max)
                        val atMin = spec.set(params, spec.min)
                        val atMax = spec.set(params, spec.max)
                        assertEquals(spec.min, spec.get(atMin), 0.001f)
                        assertEquals(spec.max, spec.get(atMax), 0.001f)
                        assertTrue(spec.format(spec.min).isNotBlank())
                    }
                    is com.frerox.toolz.ui.screens.developer.SpoofFieldSpec.FixtureText -> {
                        assertTrue(spec.maxLength > 0)
                        assertTrue(spec.get(spec.set(params, "fixture")).isNotBlank())
                    }
                    is com.frerox.toolz.ui.screens.developer.SpoofFieldSpec.FixtureSwitch -> {
                        assertEquals(!spec.get(params), spec.get(spec.set(params, !spec.get(params))))
                    }
                }
            }
        }
    }

    @Test
    fun `fixture summaries use the current parameter values`() {
        val params = SpoofParams(
            batteryLevel = 42,
            batteryTempC = 47.5f,
            stepsToday = 12_345,
            networkSsid = "",
            deviceModel = "",
        )

        assertEquals("42% • 47.5°C", spoofFixtureSummary(DevToolId.BATTERY, params))
        assertEquals("12,345 steps", spoofFixtureSummary(DevToolId.STEPS, params))
        assertTrue(spoofFixtureSummary(DevToolId.NETWORK, params).startsWith("Hidden network"))
        assertTrue(spoofFixtureSummary(DevToolId.DEVICE, params).startsWith("Unknown model"))
        assertTrue(DevToolId.entries.all { spoofFixtureSummary(it, params).isNotBlank() })
    }

    @Test
    fun `sandbox filtering combines group query and active state`() {
        assertEquals(DevToolId.entries, filterSandboxTools())
        assertEquals(
            listOf(DevToolId.WHISPER),
            filterSandboxTools(query = "whisper"),
        )
        assertEquals(
            DevToolId.entries.filter { it.group == DevToolGroup.HARDWARE }.toSet(),
            filterSandboxTools(group = DevToolGroup.HARDWARE).toSet(),
        )
        assertEquals(
            listOf(DevToolId.BATTERY),
            filterSandboxTools(
                query = "battery",
                group = DevToolGroup.HARDWARE,
                activeOnly = true,
                activeTools = setOf(DevToolId.BATTERY, DevToolId.WHISPER),
            ),
        )
        assertTrue(filterSandboxTools(query = "no such sandbox").isEmpty())
        assertTrue(
            filterSandboxTools(query = "HARDWARE")
                .all { it.group == DevToolGroup.HARDWARE },
        )
    }
}
