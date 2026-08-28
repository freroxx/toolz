/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.ui.screens.media.controllers

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import com.frerox.toolz.data.catalog.CatalogRepository
import com.frerox.toolz.data.catalog.CatalogTrack
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.toMediaItem
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.ui.screens.media.MusicUiState
import com.frerox.toolz.util.VibrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns playback transport: playTrack, executePlay, playUri, playCatalogTracks,
 * enqueueCatalogTrack, togglePlayPause, play/pause, skipNext/Prev, seekTo,
 * onSliderChange, fadeVolume, setPlaybackSpeed. Owns progress polling.
 */
class PlaybackTransport(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MusicUiState>,
    private val player: ExoPlayer,
    private val controllerProvider: () -> MediaController?,
    private val repository: MusicRepository,
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val context: Context,
    private val vibrationManager: VibrationManager,
    private val queueManager: QueueManager,
    private val onStartService: () -> Unit
) {
    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _sliderPosition = MutableStateFlow<Long?>(null)
    val sliderPosition: StateFlow<Long?> = _sliderPosition.asStateFlow()

    private var progressJob: Job? = null
    private var fadeJob: Job? = null

    private fun hapticClick() { if (!uiState.value.isKaraokeActive) vibrationManager.vibrateClick() }
    private fun hapticSuccess() { if (!uiState.value.isKaraokeActive) vibrationManager.vibrateSuccess() }

    private fun playerOrController(): Player = controllerProvider() ?: player

    // ── Slider ───────────────────────────────────────────────────────────────

    fun onSliderChange(position: Long) { _sliderPosition.value = position }

    fun onSliderChangeFinished() {
        _sliderPosition.value?.let { pos ->
            val p: Player = playerOrController()
            p.seekTo(pos)
            uiState.update { it.copy(playbackPosition = pos) }
            _playbackPosition.value = pos
            hapticClick()
        }
        _sliderPosition.value = null
    }

    // ── Progress polling ───────────────────────────────────────────────────

    fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                if (_sliderPosition.value == null) {
                    val p: Player = playerOrController()
                    val pos = p.currentPosition.coerceAtLeast(0)
                    _playbackPosition.value = pos

                    val dur = p.duration
                    if (uiState.value.isKaraokeActive && dur > 0 && pos >= dur - 800 && p.isPlaying) {
                        p.pause()
                    }
                }
                val isSynced = uiState.value.currentTrack?.aiLyrics?.contains("[0") == true
                val interval = when {
                    uiState.value.performanceMode -> 500L
                    uiState.value.fastSeeking || isSynced || uiState.value.isKaraokeActive -> 16L
                    else -> 100L
                }
                delay(interval)
            }
        }
    }

    fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    fun updateDuration(value: Long) {
        _duration.value = value
    }

    fun updatePlaybackPosition(value: Long) {
        _playbackPosition.value = value
    }

    // ── Fade ───────────────────────────────────────────────────────────────

    fun fadeVolume(toVolume: Float, duration: Long, onEnd: () -> Unit = {}) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val startVolume = player.volume
            val steps = 15
            val interval = duration / steps
            val delta = (toVolume - startVolume) / steps
            for (i in 1..steps) {
                delay(interval)
                player.volume = (startVolume + delta * i).coerceIn(0f, 1f)
            }
            player.volume = toVolume
            onEnd()
        }
    }

    // ── Playback controls ─────────────────────────────────────────────────

    fun playTrack(track: MusicTrack, tracks: List<MusicTrack> = uiState.value.tracks) {
        onStartService()
        hapticClick()
        scope.launch {
            if (track.path == null && track.sourceUrl != null && !track.uri.startsWith("content://") && !track.uri.startsWith("file://")) {
                uiState.update { it.copy(isResolvingCatalog = true) }
                try {
                    val quality = settingsRepository.catalogStreamQuality.first()
                    val resolvedUrl = withContext(Dispatchers.IO) {
                        catalogRepository.resolveAudioStream(track.sourceUrl, quality)
                    }
                    val resolvedTrack = track.copy(uri = resolvedUrl)
                    val resolvedTracks = tracks.map { if (it.uri == track.uri) resolvedTrack else it }
                    executePlay(resolvedTrack, resolvedTracks)
                } catch (e: Exception) {
                    uiState.update { it.copy(isResolvingCatalog = false) }
                } finally {
                    uiState.update { it.copy(isResolvingCatalog = false) }
                }
            } else {
                executePlay(track, tracks)
            }
        }
    }

    private suspend fun executePlay(track: MusicTrack, tracks: List<MusicTrack>) {
        scope.launch(Dispatchers.Default) {
            val trackUris = tracks.map { it.uri }
            val isSameQueue = trackUris == queueManager.currentQueueUris
            val mediaItems = tracks.map { t -> t.toMediaItem() }
            val startIndex = tracks.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)

            withContext(Dispatchers.Main) {
                val p: Player = playerOrController()
                if (isSameQueue) {
                    val idx = trackUris.indexOf(track.uri)
                    if (idx != -1) { p.seekTo(idx, 0L); p.play(); return@withContext }
                }
                p.stop()
                p.setMediaItems(mediaItems, startIndex, 0L)
                p.prepare()
                p.play()
            }
        }
    }

    fun playUri(
        uri: Uri,
        title: String? = null,
        artist: String? = null,
        thumbUrl: String? = null,
        sourceUrl: String? = null
    ) {
        onStartService()
        hapticClick()
        scope.launch {
            var displayTitle = title ?: "External Audio"
            if (title == null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx != -1 && cursor.moveToFirst()) displayTitle = cursor.getString(idx)
                        }
                    }
                }
            }

            val metaBuilder = MediaMetadata.Builder()
                .setTitle(displayTitle).setDisplayTitle(displayTitle)
                .setArtist(artist ?: "Unknown Artist")
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setIsPlayable(true)
            thumbUrl?.let { metaBuilder.setArtworkUri(Uri.parse(it)) }
            sourceUrl?.let {
                metaBuilder.setExtras(android.os.Bundle().apply { putString("source_url", it) })
            }

            val item = MediaItem.Builder()
                .setMediaId(uri.toString()).setUri(uri)
                .setMediaMetadata(metaBuilder.build()).build()

            withContext(Dispatchers.Main) {
                val p: Player = playerOrController()
                p.stop()
                p.setMediaItem(item)
                p.prepare()
                p.play()
            }
        }
    }

    fun addToQueue(track: MusicTrack) {
        val p: Player = playerOrController()
        p.addMediaItem(track.toMediaItem())
        hapticClick()
    }

    fun playNext(track: MusicTrack) {
        val p: Player = playerOrController()
        val nextIndex = if (p.mediaItemCount > 0) p.currentMediaItemIndex + 1 else 0
        p.addMediaItem(nextIndex, track.toMediaItem())
        hapticClick()
    }

    fun playCatalogTracks(tracks: List<CatalogTrack>, startIndex: Int = 0) {
        onStartService()
        hapticClick()
        scope.launch {
            uiState.update { it.copy(isResolvingCatalog = true) }
            try {
                val items = tracks.map { track ->
                    val localTrack = withContext(Dispatchers.IO) {
                        repository.getTrackBySourceUrl(track.sourceUrl)
                    }
                    val playableUri = if (localTrack != null && localTrack.uri.isNotBlank()) {
                        localTrack.uri
                    } else {
                        track.sourceUrl
                    }

                    val meta = MediaMetadata.Builder()
                        .setTitle(track.title).setArtist(track.artist)
                        .setAlbumTitle("YouTube Catalog").setDisplayTitle(track.title)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setIsPlayable(true)
                        .setArtworkUri(track.thumbnailUrl?.toUri())
                        .setExtras(android.os.Bundle().apply {
                            putString("source_url", track.sourceUrl)
                            putBoolean("is_catalog", true)
                        }).build()

                    MediaItem.Builder()
                        .setMediaId(track.sourceUrl).setUri(playableUri.toUri())
                        .setMediaMetadata(meta).build()
                }

                withContext(Dispatchers.Main) {
                    val p: Player = playerOrController()
                    p.stop(); p.setMediaItems(items, startIndex, 0L); p.prepare(); p.play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                uiState.update { it.copy(isResolvingCatalog = false) }
            }
        }
    }

    fun addToQueue(track: CatalogTrack) {
        val p: Player = playerOrController()
        p.addMediaItem(track.toMediaItem())
        hapticClick()
    }

    fun playNext(track: CatalogTrack) {
        val p: Player = playerOrController()
        val idx = if (p.mediaItemCount > 0) p.currentMediaItemIndex + 1 else 0
        p.addMediaItem(idx, track.toMediaItem())
        hapticClick()
    }

    fun setResolvingCatalog(resolving: Boolean) {
        uiState.update { it.copy(isResolvingCatalog = resolving) }
    }

    fun enqueueCatalogTrack(track: CatalogTrack, playNext: Boolean) {
        scope.launch {
            val localTrack = withContext(Dispatchers.IO) {
                repository.getTrackBySourceUrl(track.sourceUrl)
            }
            val item = if (localTrack != null && localTrack.uri.isNotBlank()) {
                localTrack.toMediaItem()
            } else {
                track.toMediaItem()
            }
            withContext(Dispatchers.Main) {
                val p: Player = playerOrController()
                if (playNext) {
                    val idx = if (p.mediaItemCount > 0) p.currentMediaItemIndex + 1 else 0
                    p.addMediaItem(idx, item)
                } else {
                    p.addMediaItem(item)
                }
                if (!p.isPlaying && !p.playWhenReady) p.prepare()
                hapticClick()
            }
        }
    }

    fun playPlaylist(tracks: List<MusicTrack>, shuffle: Boolean) {
        val p: Player = playerOrController()
        if (tracks.isEmpty()) return
        if (shuffle) {
            p.shuffleModeEnabled = true
            uiState.update { it.copy(isShuffleOn = true) }
            // play random track from list
            val random = tracks.random()
            playTrack(random, tracks)
        } else {
            p.shuffleModeEnabled = false
            uiState.update { it.copy(isShuffleOn = false) }
            playTrack(tracks.first(), tracks)
        }
        hapticSuccess()
    }

    fun togglePlayPause() {
        onStartService()
        val p: Player = playerOrController()
        if (p.isPlaying) p.pause() else p.play()
        hapticClick()
    }

    fun play() {
        onStartService()
        val p: Player = playerOrController()
        if (!p.isPlaying) {
            p.volume = 0f
            p.play()
            if (!uiState.value.isMutedByAi) {
                fadeVolume(1f, 100)
            } else {
                p.volume = 0f
            }
        }
        hapticClick()
    }

    fun pause() {
        val p: Player = playerOrController()
        if (p.isPlaying) {
            p.pause()
            if (!uiState.value.isMutedByAi) {
                fadeVolume(0f, 80) {
                    p.volume = 1f
                }
            }
        }
        hapticClick()
    }

    fun stop() {
        val p: Player = playerOrController()
        p.stop(); p.clearMediaItems()
        uiState.update { it.copy(currentTrack = null, isPlaying = false, playbackPosition = 0L, duration = 0L, isResolvingCatalog = false) }
        _playbackPosition.value = 0L; _duration.value = 0L
        hapticClick()
    }

    fun seekTo(position: Long) {
        val p: Player = playerOrController()
        p.seekTo(position)
        uiState.update { it.copy(playbackPosition = position) }
        _playbackPosition.value = position
        hapticClick()
    }

    fun setVolume(volume: Float) {
        fadeJob?.cancel()
        val p: Player = playerOrController()
        p.volume = volume
    }

    fun setMutedByAi(muted: Boolean) {
        uiState.update { it.copy(isMutedByAi = muted) }
        setVolume(if (muted) 0f else 1f)
    }

    fun skipNext() {
        val p: Player = playerOrController()
        if (p.hasNextMediaItem()) p.seekToNext()
        else if (p.repeatMode == Player.REPEAT_MODE_ALL) p.seekTo(0, 0)
        hapticClick()

        if (!uiState.value.isMutedByAi) {
            val target = p.volume
            p.volume = 0f
            fadeVolume(target.takeIf { it > 0f } ?: 1f, 120)
        } else {
            p.volume = 0f
        }
    }

    fun skipPrevious() {
        val p: Player = playerOrController()
        if (p.currentPosition > 3_000) p.seekTo(0)
        else if (p.hasPreviousMediaItem()) p.seekToPrevious()
        hapticClick()

        if (!uiState.value.isMutedByAi) {
            val target = p.volume
            p.volume = 0f
            fadeVolume(target.takeIf { it > 0f } ?: 1f, 120)
        } else {
            p.volume = 0f
        }
    }

    fun toggleShuffle() {
        val p: Player = playerOrController()
        val new = !p.shuffleModeEnabled
        p.shuffleModeEnabled = new
        uiState.update { it.copy(isShuffleOn = new) }
        if (new) hapticSuccess() else hapticClick()
    }

    fun toggleRepeat() {
        val p: Player = playerOrController()
        val new = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        p.repeatMode = new
        uiState.update { it.copy(repeatMode = new) }
        if (new != Player.REPEAT_MODE_OFF) hapticSuccess() else hapticClick()
    }

    fun setSortOrder(order: com.frerox.toolz.ui.screens.media.SortOrder) {
        uiState.update { it.copy(sortOrder = order) }
        hapticClick()
    }

    fun setPlaybackSpeed(speed: Float) {
        scope.launch {
            settingsRepository.setMusicPlaybackSpeed(speed)
            val p: Player = playerOrController()
            p.playbackParameters = PlaybackParameters(speed)
            uiState.update { it.copy(playbackSpeed = speed) }
            hapticClick()
        }
    }

    fun setCatalogStreamQuality(quality: String, onRefresh: (String) -> Unit) {
        scope.launch {
            settingsRepository.setCatalogStreamQuality(quality)
            uiState.update { it.copy(catalogStreamQuality = quality) }

            val currentTrack = uiState.value.currentTrack
            if (currentTrack != null && currentTrack.sourceUrl != null && currentTrack.path == null) {
                onRefresh(quality)
            } else {
                hapticClick()
            }
        }
    }

    fun refreshCurrentCatalogStream(
        track: MusicTrack,
        quality: String,
        getCurrentMediaItem: () -> MediaItem?
    ) {
        scope.launch {
            val sourceUrl = track.sourceUrl ?: return@launch
            val streamUrl = try {
                catalogRepository.resolveAudioStream(sourceUrl, quality)
            } catch (e: Exception) {
                hapticClick()
                return@launch
            }

            val p: Player = playerOrController()
            val index = p.currentMediaItemIndex
            if (index < 0) {
                hapticClick()
                return@launch
            }

            val wasPlaying = p.isPlaying
            val resumePosition = p.currentPosition.coerceAtLeast(0L)
            val metadata = getCurrentMediaItem()?.mediaMetadata ?: MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist ?: "Unknown Artist")
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .setArtworkUri(track.thumbnailUri?.toUri())
                .setExtras(android.os.Bundle().apply {
                    putString("source_url", sourceUrl)
                    putBoolean("is_catalog", true)
                })
                .build()

            val replacement = MediaItem.Builder()
                .setMediaId(streamUrl)
                .setUri(streamUrl.toUri())
                .setMediaMetadata(metadata)
                .build()

            withContext(Dispatchers.Main) {
                p.replaceMediaItem(index, replacement)
                p.prepare()
                p.seekTo(index, resumePosition)
                if (wasPlaying) p.play() else p.pause()
            }
            hapticClick()
        }
    }

    fun onCleared() {
        progressJob?.cancel()
        fadeJob?.cancel()
    }
}
