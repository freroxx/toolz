/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.developer

import com.frerox.toolz.data.developer.CapturedCrash
import com.frerox.toolz.data.developer.DevAppInfo
import com.frerox.toolz.data.developer.buildFastReportMarkdown
import com.frerox.toolz.data.developer.parseCapturedCrash
import com.frerox.toolz.data.developer.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastReportFormatterTest {

    private val info = DevAppInfo(
        versionName = "1.2.3",
        versionCode = 42L,
        packageName = "com.frerox.toolz",
        pid = 1234,
        processUptimeMs = 252_000L,
        heapUsedBytes = 48L * 1024L * 1024L,
        heapMaxBytes = 256L * 1024L * 1024L,
        nativeHeapBytes = 12L * 1024L * 1024L,
        threads = 37,
        heapUsedFraction = 0.1875f,
        deviceLabel = "Pixel 9 · komodo",
        androidLabel = "Android 16 (API 36)",
        abi = "arm64-v8a",
        buildType = "release",
        densityDpi = 420,
        debuggable = false,
    )

    private val crash = CapturedCrash(
        atMs = 1_700_000_500_000L,
        threadName = "main",
        throwableName = "java.lang.IllegalStateException",
        message = "Boom",
        stackTrace = "java.lang.IllegalStateException: Boom\n\tat com.frerox.toolz.MainActivity.onCreate(MainActivity.kt:42)",
        logs = listOf("E/Toolz: first failure", "W/Toolz: deprecation"),
        appVersionName = "1.2.3-beta1",
        appVersionCode = 43L,
        deviceLabel = "Pixel 9",
        androidLabel = "Android 16",
    )

    private fun report(
        crash: CapturedCrash? = this.crash,
        logs: List<String> = listOf("E/Toolz: first failure", "W/Toolz: deprecation"),
        sandboxes: List<String> = listOf("Weather", "Calendar"),
    ) = buildFastReportMarkdown(
        info = info,
        crash = crash,
        warnErrorLogs = logs,
        activeSandboxes = sandboxes,
        generatedAtMs = 1_700_001_000_000L,
        discordUrl = "https://discord.gg/aAswRUerwh",
        languageTag = "en-US",
    )

    @Test
    fun `crash report contains every section a maintainer needs`() {
        val markdown = report()

        assertTrue(markdown.startsWith("# Toolz fast report"))
        listOf(
            "## What happened",
            "## App",
            "## Device",
            "## Active sandboxes",
            "## Recent warnings and errors",
            "## Stack trace",
            "## How to send this report",
            "## Privacy",
        ).forEach { section -> assertTrue("Missing $section", markdown.contains(section)) }

        assertTrue(markdown.contains("java.lang.IllegalStateException"))
        assertTrue(markdown.contains("**Message:** Boom"))
        assertTrue(markdown.contains("**Thread:** `main`"))
        assertTrue(markdown.contains("Toolz 1.2.3-beta1 (43)"))
        assertTrue(markdown.contains("**Package:** `com.frerox.toolz`"))
        assertTrue(markdown.contains("PID 1234"))
        assertTrue(markdown.contains("**Model:** Pixel 9"))
        assertTrue(markdown.contains("**ABI:** arm64-v8a"))
        assertTrue(markdown.contains("heap 48.0 MB / 256.0 MB (18%)"))
        assertTrue(markdown.contains("https://discord.gg/aAswRUerwh"))
        assertTrue(markdown.contains("bug-reports"))
    }

    @Test
    fun `sandboxes are sorted and rendered as a list`() {
        val markdown = report(sandboxes = listOf("Weather", "Calendar", "Battery"))

        assertTrue(markdown.contains("- Battery\n- Calendar\n- Weather"))
    }

    @Test
    fun `manual snapshot omits the stack trace and explains why it exists`() {
        val markdown = report(crash = null)

        assertTrue(markdown.contains("no crash was recorded for this report"))
        assertTrue(markdown.contains("manual diagnostic snapshot"))
        assertFalse(markdown.contains("## Stack trace"))
        assertTrue(markdown.contains("Toolz 1.2.3 (42)"))
        assertTrue(markdown.contains("**Model:** Pixel 9 · komodo"))
    }

    @Test
    fun `empty logs and sandboxes use readable placeholders`() {
        val markdown = report(logs = emptyList(), sandboxes = emptyList())

        assertTrue(markdown.contains("_No warning or error lines were captured._"))
        assertTrue(markdown.contains("_No sandboxes were active._"))
    }

    @Test
    fun `log and trace fences survive backticks and tilde fences from content`() {
        val hostileLogs = listOf(
            "E/Toolz: ```kotlin",
            "E/Toolz: ~~~~text",
            "E/Toolz: closing ````",
        )
        val hostileCrash = crash.copy(
            stackTrace = "java.lang.RuntimeException: bad\n~~~~\n```\n",
        )

        val markdown = report(crash = hostileCrash, logs = hostileLogs)

        assertEquals(2, "~~~~text".toRegex().findAll(markdown).count())
        assertTrue(markdown.contains("~ ~ ~ ~"))
        assertTrue(markdown.contains("```kotlin"))
        assertTrue(markdown.contains("closing ````"))
    }

    @Test
    fun `log block keeps the newest lines when the stream is long`() {
        val manyLines = (0 until 400).map { index -> "E/Toolz: line-$index" }

        val markdown = report(logs = manyLines)

        assertFalse(markdown.contains("line-99"))
        assertTrue(markdown.contains("line-100"))
        assertTrue(markdown.contains("line-399"))
    }

    @Test
    fun `multi-line exception messages collapse into one summary line`() {
        val multiline = crash.copy(message = "First line\n  second line\nthird line")

        val markdown = report(crash = multiline)

        assertTrue(markdown.contains("**Message:** First line second line third line"))
    }

    @Test
    fun `crash identity wins over the live snapshot`() {
        val markdown = report()

        assertTrue(markdown.contains("Toolz 1.2.3-beta1 (43)"))
        assertFalse(markdown.contains("Toolz 1.2.3 (42)"))
    }

    @Test
    fun `captured crash survives a json round trip`() {
        val parsed = parseCapturedCrash(crash.toJson())

        assertEquals(crash, parsed)
    }

    @Test
    fun `invalid or empty snapshots are rejected`() {
        assertNull(parseCapturedCrash(null))
        assertNull(parseCapturedCrash(""))
        assertNull(parseCapturedCrash("   "))
        assertNull(parseCapturedCrash("not json"))
        assertNull(parseCapturedCrash("""{"atMs":1,"stackTrace":""}"""))
        assertNull(parseCapturedCrash("""{"atMs":1}"""))
    }

    @Test
    fun `blank optional fields fall back to safe defaults`() {
        val parsed = parseCapturedCrash(
            """{"atMs":5,"stackTrace":"at x","threadName":"","throwableName":"","logs":["","E/x"]}""",
        )

        assertEquals("unknown", parsed?.threadName)
        assertEquals("Unknown error", parsed?.throwableName)
        assertEquals(listOf("E/x"), parsed?.logs)
    }
}
