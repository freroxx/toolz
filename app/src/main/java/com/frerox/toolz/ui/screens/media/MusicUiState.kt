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

package com.frerox.toolz.ui.screens.media

import androidx.media3.common.Player
import com.frerox.toolz.data.catalog.CatalogTrack
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.Playlist

// P1-02 fix: extracted from MusicPlayerViewModel.kt to reduce god-viewmodel size.
// Now slices and sections can import without pulling the entire VM.

enum class SortOrder { TITLE, ARTIST, RECENT }

data class MusicUiState(
    val tracks                  : List<MusicTrack>          = emptyList(),
    val playlists               : List<Playlist>            = emptyList(),
    val favoriteTracks          : List<MusicTrack>          = emptyList(),
    val recentlyPlayed          : List<MusicTrack>          = emptyList(),
    val mostPlayed              : List<MusicTrack>          = emptyList(),
    val currentTrack            : MusicTrack?               = null,
    val isPlaying               : Boolean                   = false,
    val playWhenReady           : Boolean                   = false,
    val isShuffleOn             : Boolean                   = false,
    val repeatMode              : Int                       = Player.REPEAT_MODE_OFF,
    val sortOrder               : SortOrder                 = SortOrder.RECENT,
    val isLoading               : Boolean                   = false,
    val sleepTimerMinutes       : Int?                      = null,
    val folders                 : Map<String, List<MusicTrack>> = emptyMap(),
    val selectedTracks          : Set<String>               = emptySet(),
    val isSelectionMode         : Boolean                   = false,
    val showVisualizer          : Boolean                   = false,
    val artShape                : String                    = "CIRCLE",
    val rotationEnabled         : Boolean                   = true,
    val hapticEnabled           : Boolean                   = true,
    val hapticIntensity         : Float                     = 0.5f,
    val pipEnabled              : Boolean                   = false,
    val sleepTimerActive        : Boolean                   = false,
    val sleepTimerRemaining     : Long?                     = null,
    val queue                   : List<QueueEntry>          = emptyList(),
    val currentQueueIndex       : Int                       = 0,
    val performanceMode         : Boolean                   = false,
    val playbackPosition        : Long                      = 0L,
    val duration                : Long                      = 0L,
    val isOnline                : Boolean                   = false,
    val isResolvingCatalog      : Boolean                   = false,
    val playbackSpeed           : Float                     = 1.0f,
    val equalizerPreset         : String                    = "Normal",
    val equalizerPresets        : List<String>              = listOf(
        "Normal", "Pop", "Rock", "Jazz", "Classical",
        "Dance", "Heavy Metal", "Hip Hop", "Flat", "Custom"
    ),
    val customEqualizerGains    : List<Float>               = List(5) { 0f },
    val visualizerSensitivity   : Float                     = 1.0f,
    val visualizerAutoSensitivity: Boolean                  = false,

    val showMusicSettings       : Boolean                   = false,
    val karaokeEnabled          : Boolean                   = true,
    val isKaraokeActive         : Boolean                   = false,
    val fastSeeking             : Boolean                   = true,
    val alwaysSync              : Boolean                   = true,
    val catalogResults          : List<CatalogTrack>        = emptyList(),
    val catalogStreamQuality    : String                    = "AUTO",
    val isMutedByAi             : Boolean                   = false,
    val karaokeSessionsCount    : Int                       = 0,
    val karaokeAvgScore         : Int                       = -1,
    val queueWarning            : String?                   = null,
    val downloadedOnlyFilter    : Boolean                   = false
)

data class QueueEntry(val id: String, val track: MusicTrack)
