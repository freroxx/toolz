/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.developer

import com.frerox.toolz.data.developer.LogcatLevel
import com.frerox.toolz.data.developer.LogcatSeverity
import com.frerox.toolz.data.developer.filterLogcatLines
import com.frerox.toolz.data.developer.filterLogcatSince
import com.frerox.toolz.data.developer.formatLogcatClock
import com.frerox.toolz.data.developer.logLineLevel
import com.frerox.toolz.data.developer.logLineTimestampMs
import com.frerox.toolz.data.developer.mergeLogcatLines
import com.frerox.toolz.data.developer.prettyLogcatLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class DevLogcatTest {

    private fun line(
        offsetSeconds: Long,
        level: Char = 'I',
        message: String = "message",
        pid: Int = 100,
        tid: Int = 101,
        millis: String = "250",
    ): String = "${BASE_EPOCH_SECONDS + offsetSeconds}.$millis $pid $tid $level/Tag: $message"

    @Test
    fun `epoch prefix and level are parsed from real logcat lines`() {
        val warning = line(0, 'W', "careful")

        assertEquals(BASE_EPOCH_SECONDS * 1_000L + 250L, logLineTimestampMs(warning))
        assertEquals(LogcatLevel.WARN, logLineLevel(warning))
        assertEquals(LogcatLevel.FATAL, logLineLevel(line(1, 'F')))
        assertEquals(null, logLineTimestampMs("--------- beginning of main"))
        assertEquals(null, logLineLevel("    at com.example.Test.run(Test.kt:1)"))
    }

    @Test
    fun `merge keeps stack traces with parents and sorts chronologically`() {
        val early = line(10, message = "early")
        val late = line(20, 'E', "boom")
        val trace = "\tat com.example.Test.run(Test.kt:1)"

        val merged = mergeLogcatLines(
            listOf(
                listOf(late, trace),
                listOf(early),
            ),
        )

        assertEquals(listOf(early, late, trace), merged)
    }

    @Test
    fun `merge keeps banner records before timestamped records`() {
        val banner = "--------- beginning of main"
        val first = line(1, message = "first")
        val second = line(2, message = "second")

        val merged = mergeLogcatLines(listOf(listOf(first, second), listOf(banner)))

        assertEquals(listOf(banner, first, second), merged)
    }

    @Test
    fun `merge removes mirrored records only inside the two second window`() {
        val local = line(10, message = "same")
        val mirrored = line(10, message = "same", millis = "900")
        val outside = line(13, message = "same")

        val merged = mergeLogcatLines(
            listOf(listOf(local), listOf(mirrored, outside)),
        )

        assertEquals(listOf(local, outside), merged)
    }

    @Test
    fun `merge caps whole records and keeps the newest`() {
        val lines = (1..5).map { line(it.toLong(), message = "line $it") }

        val merged = mergeLogcatLines(listOf(lines), maxLines = 3)

        assertEquals(listOf(lines[2], lines[3], lines[4]), merged)
        assertEquals(emptyList<String>(), mergeLogcatLines(listOf(lines), maxLines = 0))
    }

    @Test
    fun `merge keeps the parent of a single oversized record`() {
        val parent = line(1, 'E', "boom")
        val trace = (1..10).map { "\tat com.example.Trace$it.run(Trace.kt:$it)" }
        val merged = mergeLogcatLines(listOf(listOf(parent) + trace), maxLines = 4)

        assertEquals(listOf(parent) + trace.take(3), merged)
    }

    @Test
    fun `cutoff drops stale parents and their continuations`() {
        val stale = line(1, 'E', "stale")
        val staleTrace = "\tat stale.Stack(Stale.kt:1)"
        val fresh = line(3, 'I', "fresh")
        val freshTrace = "\tat fresh.Stack(Fresh.kt:1)"

        val filtered = filterLogcatSince(
            listOf(stale, staleTrace, fresh, freshTrace),
            cutoffMs = BASE_EPOCH_SECONDS * 1_000L + 2_000L,
        )

        assertEquals(listOf(fresh, freshTrace), filtered)
        assertEquals(
            listOf(stale, staleTrace, fresh, freshTrace),
            filterLogcatSince(listOf(stale, staleTrace, fresh, freshTrace), null),
        )
    }

    @Test
    fun `filters match whole records and preserve traces`() {
        val info = line(1, 'I', "starting")
        val warn = line(2, 'W', "careful")
        val error = line(3, 'E', "boom")
        val errorTrace = "\tat com.example.Boom.run(Boom.kt:42)"

        val warnings = filterLogcatLines(
            lines = listOf(info, warn, error, errorTrace),
            severity = LogcatSeverity.WARNINGS,
        )
        assertEquals(listOf(warn, error, errorTrace), warnings)

        val byTrace = filterLogcatLines(
            lines = listOf(info, error, errorTrace),
            query = "Boom.kt",
        )
        assertEquals(listOf(error, errorTrace), byTrace)

        val byCaseInsensitiveMessage = filterLogcatLines(
            lines = listOf(info, error, errorTrace),
            query = "STARTING",
        )
        assertEquals(listOf(info), byCaseInsensitiveMessage)
    }

    @Test
    fun `pretty clock replaces only the epoch prefix`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val pretty = prettyLogcatLine(line(0, 'I', "hello", millis = "125"))

            assertEquals("22:13:20.125 100 101 I/Tag: hello", pretty)
            assertEquals("--:--:--", formatLogcatClock(0))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun `blank lines are ignored without losing the next record`() {
        val first = line(1, message = "first")
        val second = line(2, message = "second")

        val filtered = filterLogcatLines(listOf("", first, "   ", second))

        assertEquals(listOf(first, second), filtered)
        assertTrue(logLineTimestampMs(second)!! > logLineTimestampMs(first)!!)
    }

    private companion object {
        const val BASE_EPOCH_SECONDS = 1_700_000_000L
    }
}
