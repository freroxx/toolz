/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.data.catalog

/**
 * Converts WebVTT / SRT caption content to LRC-format timestamps
 * compatible with the existing LyricsLine parser in NowPlayingAiModels.
 *
 * Input example (VTT):
 *   00:00:12.500 --> 00:00:15.000
 *   Hello world
 *
 * Output (LRC):
 *   [00:12.50]Hello world
 */
object CaptionConverter {

    private val VTT_TIMESTAMP_REGEX = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[.,](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[.,](\d{1,3})"""
    )

    private val SRT_TIMESTAMP_REGEX = Regex(
        """(\d{2}):(\d{2}):(\d{2}),(\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2}),(\d{3})"""
    )

    /**
     * Converts VTT or SRT subtitle content to an LRC-format string.
     * Each line becomes `[mm:ss.xx]text`.
     */
    fun convertToLrc(subtitleContent: String): String {
        val lines = subtitleContent.lines()
        val lrcLines = mutableListOf<String>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            // Try to match a VTT or SRT timestamp line
            val vttMatch = VTT_TIMESTAMP_REGEX.find(line)
            val srtMatch = SRT_TIMESTAMP_REGEX.find(line)
            val match = vttMatch ?: srtMatch

            if (match != null) {
                val hours = match.groupValues[1].toInt()
                val minutes = match.groupValues[2].toInt()
                val seconds = match.groupValues[3].toInt()
                val millis = match.groupValues[4].padEnd(3, '0').toInt()

                val totalMinutes = hours * 60 + minutes
                val centiseconds = millis / 10

                val timestamp = String.format("[%02d:%02d.%02d]", totalMinutes, seconds, centiseconds)

                // Collect all text lines until next blank line or timestamp
                i++
                val textLines = mutableListOf<String>()
                while (i < lines.size) {
                    val textLine = lines[i].trim()
                    if (textLine.isEmpty() || VTT_TIMESTAMP_REGEX.containsMatchIn(textLine) || SRT_TIMESTAMP_REGEX.containsMatchIn(textLine)) {
                        break
                    }
                    // Strip VTT cue tags like <c> </c> <b> etc.
                    val cleanText = textLine
                        .replace(Regex("<[^>]+>"), "")
                        .trim()
                    if (cleanText.isNotEmpty()) {
                        textLines.add(cleanText)
                    }
                    i++
                }

                if (textLines.isNotEmpty()) {
                    val combined = textLines.joinToString(" ")
                    lrcLines.add("$timestamp$combined")
                }
            } else {
                i++
            }
        }

        return lrcLines.joinToString("\n")
    }

    /**
     * Converts the total milliseconds to an LRC timestamp tag.
     */
    fun millisToLrcTag(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val centis = (millis % 1000) / 10
        return String.format("[%02d:%02d.%02d]", minutes, seconds, centis)
    }
}
