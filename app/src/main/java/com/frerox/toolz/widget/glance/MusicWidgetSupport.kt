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

package com.frerox.toolz.widget.glance

import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------------------
//  Small, dependency-free helpers shared by MusicPlayerService (writer) and
//  MusicGlanceWidget (reader). Kept out of both so neither file has to own
//  JSON/format details that aren't really about "how the service updates
//  state" or "how the widget lays out".
// ---------------------------------------------------------------------------

/** Encodes the up-next queue for DataStore storage. Empty list -> "[]", never null. */
fun encodeQueueJson(queue: List<QueueTrackInfo>): String {
    val array = JSONArray()
    queue.forEach { track ->
        array.put(
            JSONObject().apply {
                put("id", track.mediaId)
                put("title", track.title)
                put("artist", track.artist)
                put("index", track.queueIndex,)
            }
        )
    }
    return array.toString()
}

/** Inverse of [encodeQueueJson]. Malformed/blank input decodes to an empty list rather than throwing. */
fun decodeQueueJson(json: String?): List<QueueTrackInfo> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            QueueTrackInfo(
                mediaId = obj.optString("id"),
                title = obj.optString("title"),
                artist = obj.optString("artist"),
                queueIndex = obj.optInt("index"),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Derives the current playback position by adding elapsed wall-clock time
 * since [capturedAtElapsedMs] to [positionAtCaptureMs], instead of trusting
 * a value that's only fresh at the instant it was broadcast. Clamped to
 * [0, durationMs] and frozen (no extrapolation) while paused, since a
 * paused position doesn't advance no matter how long the widget sits idle.
 */
fun liveProgressFraction(
    positionAtCaptureMs: Long,
    durationMs: Long,
    capturedAtElapsedMs: Long,
    isPlaying: Boolean,
    nowElapsedMs: Long
): Float {
    if (durationMs <= 0L) return 0f
    val effectivePositionMs = if (isPlaying) {
        positionAtCaptureMs + (nowElapsedMs - capturedAtElapsedMs).coerceAtLeast(0L)
    } else {
        positionAtCaptureMs
    }
    return (effectivePositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}