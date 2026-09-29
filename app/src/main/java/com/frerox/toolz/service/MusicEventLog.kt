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

package com.frerox.toolz.service

import android.os.SystemClock
import android.util.Log

/**
 * Process-wide ring buffer of music-player diagnostic events.
 *
 * The "resume instantly pauses" stall needs post-mortem evidence: when the
 * player latches paused, the trail (focus changes, steals, denials, noisy/BT
 * broadcasts, playback errors with codes, play/pause calls with callers) is
 * dumped to logcat via [dumpStall] and readable from the UI debug row.
 * Bounded (default 200) so it can't grow memory; thread-safe; never throws.
 */
object MusicEventLog {
    private const val TAG = "MusicEventLog"
    private const val MAX_EVENTS = 200

    data class Event(val elapsedMs: Long, val tag: String, val msg: String)

    private val lock = Any()
    private val events = ArrayDeque<Event>()

    /** Monotonic boot-relative base so relative timings stay comparable. */
    private val baseMs: Long = SystemClock.elapsedRealtime()

    fun add(tag: String, msg: String) {
        runCatching {
            val now = SystemClock.elapsedRealtime()
            synchronized(lock) {
                events.addLast(Event(now - baseMs, tag, msg))
                while (events.size > MAX_EVENTS) events.removeFirst()
            }
        }
    }

    fun snapshot(): List<Event> = runCatching {
        synchronized(lock) { events.toList() }
    }.getOrDefault(emptyList())

    fun clear() {
        runCatching { synchronized(lock) { events.clear() } }
    }

    /**
     * Log the whole trail at WARN so a bug report's logcat around a stall
     * contains the full story even if earlier lines rotated out.
     */
    fun dumpStall(reason: String) {
        runCatching {
            val trail = snapshot()
            Log.w(TAG, "===== MUSIC STALL DUMP: $reason (${trail.size} events) =====")
            trail.forEach { e ->
                Log.w(TAG, "[+${e.elapsedMs}ms][${e.tag}] ${e.msg}")
            }
            Log.w(TAG, "===== END STALL DUMP =====")
        }
    }
}
