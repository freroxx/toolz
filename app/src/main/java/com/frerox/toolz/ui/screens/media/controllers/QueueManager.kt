/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.ui.screens.media.controllers

import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.ui.screens.media.MusicUiState
import com.frerox.toolz.ui.screens.media.QueueEntry
import com.frerox.toolz.util.VibrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns currentQueueUris, queue warning, and queue mutations.
 * Consumes MusicRepository via UiState flows + ExoPlayer controller.
 */
class QueueManager(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MusicUiState>,
    private val player: ExoPlayer,
    private val controllerProvider: () -> MediaController?,
    private val vibrationManager: VibrationManager
) {
    var currentQueueUris: List<String> = emptyList()
        private set

    val queueFlow: StateFlow<List<QueueEntry>> = uiState.map { it.queue }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentQueueIndexFlow: StateFlow<Int> = uiState.map { it.currentQueueIndex }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    private fun hapticClick() { if (!uiState.value.isKaraokeActive) vibrationManager.vibrateClick() }
    private fun hapticTick() { if (!uiState.value.isKaraokeActive) vibrationManager.vibrateTick() }

    private fun playerOrController(): Player = controllerProvider() ?: player

    fun updateQueue() {
        scope.launch(Dispatchers.Main) {
            val p: Player = playerOrController()
            val trackMap = uiState.value.tracks.associateBy { it.uri }
            val entries = mutableListOf<QueueEntry>()
            val uris = mutableListOf<String>()
            val timeline = p.currentTimeline
            val window = Timeline.Window()

            for (i in 0 until p.mediaItemCount) {
                val item = p.getMediaItemAt(i)
                uris.add(item.mediaId)
                val stableId = if (!timeline.isEmpty && i < timeline.windowCount) {
                    timeline.getWindow(i, window).uid.toString()
                } else "${item.mediaId}_$i"

                val track = trackMap[item.mediaId] ?: run {
                    val meta = item.mediaMetadata
                    val tTitle = meta.title?.toString() ?: "External Audio"
                    val tArtist = meta.artist?.toString() ?: "Unknown"
                    MusicTrack(
                        uri = item.mediaId,
                        title = tTitle,
                        artist = tArtist,
                        album = meta.albumTitle?.toString() ?: "Unknown",
                        duration = 0L,
                        stableId = item.mediaId.hashCode().toString() + "_" + tTitle.hashCode()
                    )
                }
                entries.add(QueueEntry(id = stableId, track = track))
            }

            currentQueueUris = uris
            val missingCount = uris.count { uri ->
                trackMap[uri] == null && uri.startsWith("content://")
            }
            val warning = if (missingCount > 0) {
                android.util.Log.w("MusicPlayerVM", "Queue pruned $missingCount missing tracks (deleted/moved)")
                "$missingCount track(s) unavailable — removed from queue"
            } else null
            uiState.update { it.copy(queue = entries, currentQueueIndex = p.currentMediaItemIndex.coerceAtLeast(0), queueWarning = warning) }
        }
    }

    fun consumeQueueWarning() {
        uiState.update { it.copy(queueWarning = null) }
    }

    fun seekToQueueIndex(index: Int) {
        val p: Player = playerOrController()
        if (index in 0 until p.mediaItemCount) {
            p.seekTo(index, 0L)
            p.play()
            hapticClick()
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val p: Player = playerOrController()
        if (fromIndex in 0 until p.mediaItemCount && toIndex in 0 until p.mediaItemCount) {
            p.moveMediaItem(fromIndex, toIndex)
            hapticTick()
        }
    }

    // P3-11 undo buffer for swipe-to-delete
    private var lastRemoved: Pair<Int, androidx.media3.common.MediaItem>? = null

    fun removeQueueItem(index: Int) {
        val p: Player = playerOrController()
        if (index in 0 until p.mediaItemCount) {
            lastRemoved = index to p.getMediaItemAt(index)
            p.removeMediaItem(index)
            hapticClick()
        }
    }

    fun undoRemove() {
        val (idx, item) = lastRemoved ?: return
        val p: Player = playerOrController()
        val safeIdx = idx.coerceIn(0, p.mediaItemCount)
        p.addMediaItem(safeIdx, item)
        lastRemoved = null
        hapticTick()
    }

    fun hasUndo(): Boolean = lastRemoved != null

    fun shuffleRemaining() {
        val p: Player = playerOrController()
        val cur = p.currentMediaItemIndex
        if (p.mediaItemCount <= cur + 2) return
        val remaining = (cur + 1 until p.mediaItemCount).map { p.getMediaItemAt(it) }.shuffled()
        // remove tail then re-add shuffled
        for (i in p.mediaItemCount - 1 downTo cur + 1) p.removeMediaItem(i)
        remaining.forEach { p.addMediaItem(it) }
        hapticTick()
    }

    fun clearQueue() {
        val p: Player = playerOrController()
        p.clearMediaItems()
        lastRemoved = null
        hapticClick()
    }
}
