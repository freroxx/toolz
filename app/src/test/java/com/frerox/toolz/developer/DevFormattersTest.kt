/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.developer

import com.frerox.toolz.data.developer.CrashLabEvent
import com.frerox.toolz.data.developer.DevAppInfo
import com.frerox.toolz.data.developer.buildDiagnosticReport
import com.frerox.toolz.data.developer.formatBytes
import com.frerox.toolz.data.developer.formatDuration
import com.frerox.toolz.data.developer.parseCrashHistory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevFormattersTest {

    @Test
    fun `byte formatting uses binary units and never goes negative`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1024L * 1024L * 3L / 2L))
        assertEquals("2.00 GB", formatBytes(2L * 1024L * 1024L * 1024L))
        assertEquals("0 B", formatBytes(-50))
    }

    @Test
    fun `duration formatting stays compact at every scale`() {
        assertEquals("0s", formatDuration(0))
        assertEquals("1s", formatDuration(1_999))
        assertEquals("4m 12s", formatDuration(252_000))
        assertEquals("2h 05m", formatDuration(7_500_000))
        assertEquals("3d 4h", formatDuration(273_600_000))
        assertEquals("0s", formatDuration(-1))
    }

    @Test
    fun `crash history parser tolerates corrupt entries`() {
        val array = JSONArray()
            .put(JSONObject().put("type", "NPE").put("atMs", 100L).put("note", "test"))
            .put(JSONObject().put("atMs", 200L))
            .put("not an object")
        val events = parseCrashHistory(array.toString())

        assertEquals(listOf(CrashLabEvent("NPE", 100L, "test")), events)
        assertTrue(parseCrashHistory("broken").isEmpty())
        assertTrue(parseCrashHistory(null).isEmpty())
    }

    @Test
    fun `diagnostic report contains sorted context but never logs`() {
        val info = DevAppInfo(
            versionName = "1.2.3",
            versionCode = 42,
            packageName = "com.frerox.toolz",
            pid = 123,
            processUptimeMs = 252_000,
            heapUsedBytes = 1024L * 1024L,
            heapMaxBytes = 4L * 1024L * 1024L,
            nativeHeapBytes = 2L * 1024L * 1024L,
            threads = 17,
            heapUsedFraction = 0.25f,
            deviceLabel = "Test Device",
            androidLabel = "Android 16",
            abi = "arm64-v8a",
            buildType = "debug",
            densityDpi = 420,
        )
        val crashes = listOf(
            CrashLabEvent("ANR", 1_000L, "old"),
            CrashLabEvent("NPE", 3_000L, "latest"),
        )

        val report = buildDiagnosticReport(
            info = info,
            activeSandboxes = listOf("Whisper", "Battery"),
            crashes = crashes,
            generatedAtMs = 2_000L,
            themeMode = "DARK",
            languageTag = "en-US",
        )

        assertTrue(report.startsWith("Toolz diagnostic report"))
        assertTrue(report.contains("Toolz 1.2.3 (42) · debug"))
        assertTrue(report.contains("Process: PID 123 · uptime 4m 12s"))
        assertTrue(report.contains("Runtime: heap 1.0 MB / 4.0 MB (25%) · native 2.0 MB · 17 threads"))
        assertTrue(report.contains("Appearance: theme dark · language en-US"))
        assertTrue(report.contains("Sandboxes (2 active):\n- Battery\n- Whisper"))
        assertTrue(report.indexOf("NPE") < report.indexOf("ANR"))
        assertTrue(report.contains("Nothing is uploaded automatically."))
        assertFalse(report.contains("logcat", ignoreCase = true))
    }

    @Test
    fun `empty report sections are explicit`() {
        val report = buildDiagnosticReport(
            info = DevAppInfo(),
            activeSandboxes = emptyList(),
            crashes = emptyList(),
            generatedAtMs = 1_000L,
            themeMode = "",
            languageTag = "",
        )

        assertTrue(report.contains("Sandboxes: none"))
        assertTrue(report.contains("Crash-lab history: none"))
        assertTrue(report.contains("unknown"))
    }
}
