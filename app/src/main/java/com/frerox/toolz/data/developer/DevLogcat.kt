/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Pure logcat parsing/filtering. Android's `logcat -v epoch` prefix is
 * "seconds.millis pid tid LEVEL Tag: message"; DevLogHub writes the same
 * shape so both sources can be merged chronologically. Stack traces and
 * other continuation lines have no prefix and stay attached to the preceding
 * timestamped line.
 */

package com.frerox.toolz.data.developer

import java.util.Locale
import java.util.TimeZone

const val MAX_LOGCAT_LINES = 400

enum class LogcatLevel(val letter: Char, val label: String, val priority: Int) {
    VERBOSE('V', "Verbose", 0),
    DEBUG('D', "Debug", 1),
    INFO('I', "Info", 2),
    WARN('W', "Warning", 3),
    ERROR('E', "Error", 4),
    FATAL('F', "Fatal", 5),
    ;

    companion object {
        fun fromLetter(letter: Char): LogcatLevel? = when (letter.uppercaseChar()) {
            'V' -> VERBOSE
            'D' -> DEBUG
            'I' -> INFO
            'W' -> WARN
            'E' -> ERROR
            'F', 'A' -> FATAL
            else -> null
        }
    }
}

enum class LogcatSeverity(val label: String, val minimum: LogcatLevel?) {
    ALL("All", null),
    WARNINGS("Warn+", LogcatLevel.WARN),
    ERRORS("Errors", LogcatLevel.ERROR),
    ;

    fun allows(level: LogcatLevel): Boolean = minimum == null || level.priority >= minimum.priority
}

private val EPOCH_PREFIX = Regex("""^\s*(\d{9,}(?:\.\d+)?)\s""")
private val EPOCH_LEVEL = Regex("""^\s*\d{9,}(?:\.\d+)?\s+\d+\s+\d+\s+([VDIWEFA])(?:\s|/)""")
private val BRIEF_LEVEL = Regex("""(?:^|\s)([VDIWEFA])/""")
private const val LOGCAT_BANNER_PREFIX = "--------- "
private const val DEDUPE_WINDOW_MS = 2_000L

/** One logcat record: a timestamped parent plus its continuation lines. */
private data class LogcatBlock(val lines: List<String>, val timestampMs: Long?) {
    val firstLine: String get() = lines.first()
}

private fun parseEpochMillis(value: String): Long? {
    val dot = value.indexOf('.')
    val secondsPart = if (dot >= 0) value.substring(0, dot) else value
    val seconds = secondsPart.toLongOrNull() ?: return null
    if (seconds <= 0L) return null
    val fraction = if (dot >= 0) value.substring(dot + 1) else ""
    val millis = when {
        fraction.isEmpty() -> 0L
        else -> fraction.take(3).padEnd(3, '0').toLongOrNull() ?: return null
    }
    return seconds * 1_000L + millis
}

/** Epoch milliseconds parsed from a `-v epoch` line, or null for banners/continuations. */
fun logLineTimestampMs(line: String): Long? {
    val value = EPOCH_PREFIX.find(line)?.groupValues?.getOrNull(1) ?: return null
    return parseEpochMillis(value)
}

fun logLineLevel(line: String): LogcatLevel? {
    val letter = EPOCH_LEVEL.find(line)?.groupValues?.getOrNull(1)
        ?: BRIEF_LEVEL.find(line)?.groupValues?.getOrNull(1)
        ?: return null
    return LogcatLevel.fromLetter(letter.first())
}

private fun toBlocks(lines: List<String>): List<LogcatBlock> {
    val blocks = ArrayList<LogcatBlock>()
    var current = ArrayList<String>()

    fun flush() {
        if (current.isNotEmpty()) {
            val first = current.first()
            blocks += LogcatBlock(current, logLineTimestampMs(first))
            current = ArrayList()
        }
    }

    for (raw in lines) {
        for (untrimmed in raw.lines()) {
            val line = untrimmed.trimEnd()
            if (line.isBlank()) continue
            val timestamp = logLineTimestampMs(line)
            val startsBlock = timestamp != null || line.startsWith(LOGCAT_BANNER_PREFIX)
            if (startsBlock) flush()
            if (current.isEmpty() && timestamp == null) {
                // A banner or arbitrary leading line: keep it as a standalone block.
                current += line
                flush()
                continue
            }
            current += line
        }
    }
    flush()
    return blocks
}

private fun normalizedBlockKey(line: String): String = EPOCH_PREFIX.replace(line, "")

private fun dedupeAcrossSources(groups: List<List<String>>): List<LogcatBlock> {
    val seen = ArrayList<Pair<Long, String>>()
    val seenText = HashSet<String>()
    val result = ArrayList<LogcatBlock>()

    for (group in groups) {
        for (block in toBlocks(group)) {
            val timestamp = block.timestampMs
            if (timestamp == null) {
                val text = block.lines.joinToString("\n")
                if (seenText.add(text)) result += block
                continue
            }
            val key = normalizedBlockKey(block.firstLine)
            val duplicate = seen.any { (seenAt, seenKey) ->
                seenKey == key && kotlin.math.abs(seenAt - timestamp) <= DEDUPE_WINDOW_MS
            }
            if (duplicate) continue
            seen += timestamp to key
            result += block
        }
    }
    return result
}

/**
 * Merges the local ring buffer with system logcat as chronological records,
 * so stack traces stay with their parent line. Identical records mirrored by
 * both sources are de-duplicated within a small time window. The result is
 * capped at [maxLines] whole records where possible.
 */
fun mergeLogcatLines(groups: List<List<String>>, maxLines: Int = MAX_LOGCAT_LINES): List<String> {
    if (maxLines <= 0) return emptyList()
    // Banner / continuation-only blocks carry no wall-clock timestamp. Keep
    // them before timestamped records instead of treating them as newest.
    val sorted = dedupeAcrossSources(groups)
        .sortedBy { it.timestampMs ?: Long.MIN_VALUE }

    val selected = ArrayDeque<LogcatBlock>()
    var selectedLines = 0
    for (block in sorted.asReversed()) {
        val size = block.lines.size
        if (selectedLines + size > maxLines) {
            if (selected.isEmpty()) {
                // A single oversized record: keep its parent line and the
                // beginning of the trace instead of dropping the header.
                selected.addFirst(block.copy(lines = block.lines.take(maxLines)))
            }
            break
        }
        selected.addFirst(block)
        selectedLines += size
    }
    return selected.flatMap { it.lines }
}

/**
 * Drops stale records after the user clears the stream, including the
 * continuation lines that belong to a stale timestamped parent.
 */
fun filterLogcatSince(lines: List<String>, cutoffMs: Long?): List<String> {
    if (cutoffMs == null) return lines
    val result = ArrayList<String>(lines.size)
    var include = false
    for (line in lines) {
        val timestamp = logLineTimestampMs(line)
        if (timestamp != null) include = timestamp > cutoffMs
        if (include) result += line
    }
    return result
}

/**
 * Severity and query filters operate on whole records. A matching parent
 * brings its stack trace along, and a query that only matches a continuation
 * line still keeps the parent so the trace remains readable.
 */
fun filterLogcatLines(
    lines: List<String>,
    query: String = "",
    severity: LogcatSeverity = LogcatSeverity.ALL,
): List<String> {
    val trimmed = query.trim()
    val result = ArrayList<String>(lines.size)
    for (block in toBlocks(lines)) {
        val level = logLineLevel(block.firstLine)
        val severityAllowed = level == null || severity.allows(level)
        val queryAllowed = trimmed.isEmpty() ||
            block.lines.any { it.contains(trimmed, ignoreCase = true) }
        if (severityAllowed && queryAllowed) result += block.lines
    }
    return result
}

private val CLOCK_FORMAT = object : ThreadLocal<java.text.SimpleDateFormat>() {
    override fun initialValue(): java.text.SimpleDateFormat =
        java.text.SimpleDateFormat("HH:mm:ss.SSS", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
}

fun formatLogcatClock(ms: Long): String {
    if (ms <= 0L) return "--:--:--"
    return CLOCK_FORMAT.get()!!.format(java.util.Date(ms))
}

/** Replaces the epoch prefix with a compact wall-clock time for the terminal. */
fun prettyLogcatLine(line: String): String {
    val match = EPOCH_PREFIX.find(line) ?: return line
    val timestamp = parseEpochMillis(match.groupValues[1]) ?: return line
    return formatLogcatClock(timestamp) + " " + line.removePrefix(match.value)
}
