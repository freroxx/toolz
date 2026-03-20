package com.frerox.toolz.ui.screens.media.ai

import androidx.compose.runtime.Immutable

@Immutable
data class AiSong(
    val title: String,
    val artist: String,
    val album: String,
    val durationInMillis: Long,
    val coverUrl: String? = null
)

@Immutable
data class AiLyricsState(
    val lyrics: String = "",
    val isLoading: Boolean = false,
    val isAutoScrollEnabled: Boolean = true,
    val syncedLyrics: List<LyricsLine> = emptyList(),
    val isSynced: Boolean = false
)

data class LyricsLine(
    val timeMs: Long,
    val content: String
)

@Immutable
data class AiMoreInfoState(
    val artistVitals: String = "",
    val songMeaning: String = "",
    val isLoading: Boolean = false
)

@Immutable
data class AiRecommendation(
    val title: String,
    val artist: String,
    val explanation: String
)

@Immutable
data class AiTasteState(
    val recommendations: List<AiRecommendation> = emptyList(),
    val isLoading: Boolean = false
)

sealed class AiTab(val index: Int, val title: String) {
    object Lyrics : AiTab(0, "Lyrics")
    object MoreInfo : AiTab(1, "More Info")
    object MusicTaste : AiTab(2, "Music Taste")
}

data class NowPlayingAiUiState(
    val currentSong: AiSong? = null,
    val selectedTab: AiTab = AiTab.Lyrics,
    val lyricsState: AiLyricsState = AiLyricsState(),
    val moreInfoState: AiMoreInfoState = AiMoreInfoState(),
    val tasteState: AiTasteState = AiTasteState(),
    val error: String? = null,
    val isAiEnabled: Boolean = true,
    val isExpandedPill: Boolean = false
)
