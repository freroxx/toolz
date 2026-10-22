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

package com.frerox.toolz.data.developer

import com.frerox.toolz.data.music.MusicTrack

/**
 * Fixtures for the music sandbox. RAM-only: these tracks are never
 * persisted to Room, never queued into a real media session, and no file on
 * disk backs them (`fixture://` URIs). The head track is whatever title/artist
 * the console sliders are configured with.
 */
object FakeMusicSandbox {

    /** Full-length fake durations so progress bars and the mini-player behave. */
    private val pool = listOf(
        Triple("Unit Test Waltz", "Null Pointer", 179_000L),
        Triple("Regression Blues", "The Overflows", 243_000L),
        Triple("Nullptr Interlude", "Sandbox Ensemble", 61_000L),
        Triple("Race Condition", "The Overflows", 221_000L),
        Triple("Cold Flow", "Sandbox Ensemble", 198_000L),
        Triple("Merge Conflict", "The Overflows", 254_000L),
    )

    /** The simulated library, headed by the console-configured track. */
    fun library(p: SpoofParams): List<MusicTrack> {
        val head = MusicTrack(
            uri = "fixture://0",
            title = p.musicTrack.ifBlank { "Debug Symphony" },
            artist = p.musicArtist.ifBlank { "Null Pointer" },
            album = "Fixture Sessions",
            duration = 205_000L,
        )
        return listOf(head) + pool.mapIndexed { i, (title, artist, duration) ->
            MusicTrack(
                uri = "fixture://${i + 1}",
                title = title,
                artist = artist,
                album = "Fixture Sessions",
                duration = duration,
            )
        }
    }
}
