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
    val isSeekEnabled: Boolean = true,
    val syncedLyrics: List<LyricsLine> = emptyList(),
    val isSynced: Boolean = false,
    val layout: LyricsLayout = LyricsLayout.LEFT,
    val fontFamily: LyricsFont = LyricsFont.SANS_SERIF,
    val alwaysSync: Boolean = false
)

enum class LyricsLayout {
    LEFT, CENTER, RIGHT
}

enum class LyricsFont {
    SANS_SERIF, SERIF, MONOSPACE, CURSIVE, DISPLAY, HANDWRITING
}

@Immutable
data class LyricsLine(
    val timeMs: Long,
    val content: String,
    val words: List<LyricsWord> = emptyList()
)

@Immutable
data class LyricsWord(
    val word: String,
    val startTimeMs: Long,
    val durationMs: Long
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
    val explanation: String,
    val thumbnailUrl: String? = null,
    val videoId: String? = null
)

@Immutable
data class AiTasteState(
    val curatedRecommendations: List<AiRecommendation> = emptyList(),
    val artistRecommendations: List<AiRecommendation> = emptyList(),
    val isLoadingCurated: Boolean = false,
    val isLoadingArtist: Boolean = false
) {
    val isLoading: Boolean get() = isLoadingCurated || isLoadingArtist
}

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
    val isExpandedPill: Boolean = false,
    val performanceMode: Boolean = false
)
