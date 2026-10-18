/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperDailyUpsConfig
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.data.whisper.WhisperRepository
import com.frerox.toolz.data.whisper.dailyUpErrorText
import com.frerox.toolz.data.whisper.dailyUpsPostedToday
import com.frerox.toolz.data.whisper.deleteDailyUp
import com.frerox.toolz.data.whisper.downloadDailyUpImage
import com.frerox.toolz.data.whisper.fetchDailyUpReceipts
import com.frerox.toolz.data.whisper.fetchMyDailyUpReactions
import com.frerox.toolz.data.whisper.markDailyUpViewed
import com.frerox.toolz.data.whisper.observeFriendDailyUps
import com.frerox.toolz.data.whisper.observeMyDailyUps
import com.frerox.toolz.data.whisper.syncDailyUps
import com.frerox.toolz.data.whisper.toggleDailyUpReaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Daily Ups hub + viewer state machine.
 *
 * Everything is LOCAL-CACHE DRIVEN: Room rows (sealed envelopes + lifecycle
 * metadata) flow into the hub; the viewer opens envelopes on demand and holds
 * decoded images only in memory ("seen" state is intentionally NOT tracked —
 * stories replay every open, exactly like tapping any message again).
 * Server truth arrives through [refresh] (pull): stories deliberately have no
 * realtime channel — the content-free push ping re-opens the hub instead.
 *
 * Failed loads resolve to SNACKBAR errors only; no dialog, no retry queue —
 * the pull-retry is the load interaction itself (any refresh re-runs sync).
 */
@HiltViewModel
class WhisperDailyUpsViewModel @Inject constructor(
    private val repository: WhisperRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    /** One fully-presented ring entry (mine or a friend's). */
    data class StoryItem(
        val storyId: String,
        val ownerId: String,
        val ownerName: String,
        val isMine: Boolean,
        val receiptsEnabled: Boolean,
        /** Time still on the story clock (server-reconciled local ms). */
        val remainingMs: Long,
    )

    /** Everything the hub strip renders. */
    data class HubUiState(
        val loading: Boolean = true,
        val mine: List<StoryItem> = emptyList(),
        val friends: List<StoryItem> = emptyList(),
        val quotaUsed: Int = 0,
        val error: String? = null,
    )

    /** Full-screen viewer overlay; null = hidden. Flat list, one paged page per story. */
    data class ViewerState(
        val stories: List<StoryItem>,
        val index: Int,
    )

    /** Snapshot of one of MY stories in the viewer's bottom bar. */
    data class OwnStoryStats(
        val views: Int,
        val reactions: Int,
    )

    data class ReceiptsUi(
        val ownStats: Map<String, OwnStoryStats> = emptyMap(),
        /**
         * My current reaction per story, "" = none (server PK is one emoji per
         * viewer; toggling re-writes the single row).
         */
        val myReactions: Map<String, String> = emptyMap(),
    )

    private val _error = MutableStateFlow<String?>(null)
    private val _viewer = MutableStateFlow<ViewerState?>(null)
    private val _images = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    private val _receipts = MutableStateFlow(ReceiptsUi())
    private val _quotaUsed = MutableStateFlow(0)
    private val _refreshing = MutableStateFlow(true)
    private val _profiles = MutableStateFlow<Map<String, WhisperProfile>>(emptyMap())

    /** Minute ticker so "ends in 22h" labels stay honest while parked on the hub. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    /** Rows→items join; poster names land via [collectProfileNames]. */
    private val hubItems = combine(
        repository.observeMyDailyUps(),
        repository.observeFriendDailyUps(),
        _profiles,
        ticker,
    ) { myRows, friendRows, profiles, nowMs ->
        val mine = myRows.map { row ->
            StoryItem(
                storyId = row.storyId,
                ownerId = row.ownerId,
                ownerName = if (row.ownerId == repository.myId) "" else profiles[row.ownerId]?.effectiveName ?: "…",
                isMine = true,
                receiptsEnabled = row.receiptsEnabled,
                remainingMs = (row.expiresAtMs - nowMs).coerceAtLeast(0L),
            )
        }
        val others = friendRows.map { row ->
            StoryItem(
                storyId = row.storyId,
                ownerId = row.ownerId,
                ownerName = profiles[row.ownerId]?.effectiveName ?: "…",
                isMine = false,
                receiptsEnabled = row.receiptsEnabled,
                remainingMs = (row.expiresAtMs - nowMs).coerceAtLeast(0L),
            )
        }
        mine to others
    }

    val uiState: StateFlow<HubUiState> = combine(
        hubItems,
        _refreshing,
        _quotaUsed,
        _error,
    ) { items, refreshing, quotaUsed, error ->
        HubUiState(
            loading = refreshing,
            mine = items.first,
            friends = items.second,
            quotaUsed = quotaUsed,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HubUiState())

    val viewer: StateFlow<ViewerState?> = _viewer.asStateFlow()
    val images: StateFlow<Map<String, ImageBitmap>> = _images.asStateFlow()
    val receipts: StateFlow<ReceiptsUi> = _receipts.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { collectProfileNames() }
    }

    /** Hub open/refresh: sync server truth + read back the day's quota. */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            repository.syncDailyUps()
                .onFailure { e -> _error.value = dailyUpErrorText(appContext, e) }
            _quotaUsed.value = repository.dailyUpsPostedToday()
            _refreshing.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    /** One profile batch per new poster; names across the hub + viewer. */
    private suspend fun collectProfileNames() {
        val me = repository.myId
        combine(repository.observeMyDailyUps(), repository.observeFriendDailyUps()) { mine, friends ->
            (mine.map { it.ownerId } + friends.map { it.ownerId })
                .filter { it != me }
                .distinct()
        }.collect { ids ->
            val resolved = _profiles.value
            if (ids.any { it !in resolved }) {
                val (blocked, fetched) = repository.batchProfilesById(ids)
                _profiles.value = resolved + fetched.filterKeys { it !in blocked }
            }
        }
    }

    // ── Viewer ─────────────────────────────────────────────────────────────

    /** Viewer reading order: friends first, then my own (WhatsApp order). */
    fun openViewer(storyId: String) {
        val state = uiState.value
        val flat = state.friends + state.mine
        val index = flat.indexOfFirst { it.storyId == storyId }
        if (index < 0) return
        _viewer.value = ViewerState(stories = flat, index = index)
    }

    fun closeViewer() {
        _viewer.value = null
    }

    fun moveViewer(delta: Int) {
        val v = _viewer.value ?: return
        val next = v.index + delta
        _viewer.value = if (next in v.stories.indices) v.copy(index = next) else null
    }

    /** Absolute seek from the pager (drag settles); out-of-range closes. */
    fun seekViewer(page: Int) {
        val v = _viewer.value ?: return
        _viewer.value = if (page in v.stories.indices) v.copy(index = page) else null
    }

    private val inFlightImages = MutableStateFlow(emptySet<String>())

    /**
     * Called by the UI when a story is CURRENTLY on screen: loads its image
     * (decrypt-once), records the view receipt, and refreshes own-story stats.
     */
    fun onStoryDisplayed(storyId: String) {
        val v = _viewer.value ?: return
        val item = v.stories.getOrNull(v.index)?.takeIf { it.storyId == storyId } ?: return
        ensureImage(storyId)
        if (!item.isMine) markViewedOnce(storyId)
        if (item.isMine && item.receiptsEnabled) refreshOwnStats(storyId)
        viewModelScope.launch {
            val friendStoryIds = v.stories.filter { !it.isMine }.map { it.storyId }
            val missing = friendStoryIds.filter { it !in _receipts.value.myReactions }
            if (missing.isNotEmpty()) {
                repository.fetchMyDailyUpReactions(missing)
                    .onSuccess { loaded ->
                        _receipts.value = _receipts.value.copy(myReactions = _receipts.value.myReactions + loaded)
                    }
            }
        }
    }

    /** Decrypt-once per storyId; concurrent loads collapse on [inFlightImages]. */
    private fun ensureImage(storyId: String) {
        if (storyId in _images.value) return
        if (storyId in inFlightImages.value) return
        inFlightImages.value = inFlightImages.value + storyId
        viewModelScope.launch {
            repository.downloadDailyUpImage(storyId)
                .onSuccess { image ->
                    val bmp = withContext(Dispatchers.IO) { decodeDisplayBitmap(image.bytes) }
                    if (bmp != null) {
                        _images.value = _images.value + (storyId to bmp)
                    } else {
                        _error.value = appContext.getString(R.string.st_Whisper_DailyUp_ErrCorrupt)
                    }
                }
                .onFailure { e -> _error.value = dailyUpErrorText(appContext, e) }
            inFlightImages.value = inFlightImages.value - storyId
        }
    }

    /** Display decode on a bounded budget (~2 MP — every screen today). */
    private fun decodeDisplayBitmap(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > 2_000_000L) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            },
        ) ?: return null
        return bmp.asImageBitmap()
    }

    /** Receipts are advisory: a miss only means the poster never learns. */
    private fun markViewedOnce(storyId: String) {
        viewModelScope.launch {
            repository.markDailyUpViewed(storyId).onFailure {
                // Deliberately silent (see KDoc: receipts must never nag).
            }
        }
    }

    private fun refreshOwnStats(storyId: String) {
        viewModelScope.launch {
            repository.fetchDailyUpReceipts(listOf(storyId))
                .onSuccess { list ->
                    val snap = list.firstOrNull() ?: return@onSuccess
                    _receipts.value = _receipts.value.copy(
                        ownStats = _receipts.value.ownStats + (
                            storyId to OwnStoryStats(
                                views = snap.viewerIds.size,
                                reactions = snap.reactions.size,
                            )
                        ),
                    )
                }
        }
    }

    /** Optimistic palette toggle; revert + snackbar on failure. */
    fun toggleReaction(storyId: String, emoji: String, on: Boolean) {
        val myReactions = _receipts.value
        val previous = myReactions.myReactions[storyId]
        _receipts.value = myReactions.copy(
            myReactions = myReactions.myReactions +
                if (on) {
                    storyId to emoji
                } else {
                    storyId to ""
                },
        )
        viewModelScope.launch {
            repository.toggleDailyUpReaction(storyId, emoji, on)
                .onFailure { e ->
                    _receipts.value = _receipts.value.copy(
                        myReactions = _receipts.value.myReactions + (storyId to (previous ?: "")),
                    )
                    _error.value = dailyUpErrorText(appContext, e)
                }
                .onSuccess {
                    val v = _viewer.value ?: return@onSuccess
                    val shown = v.stories.getOrNull(v.index)?.takeIf { it.storyId == storyId }
                    if (shown?.isMine == true) refreshOwnStats(storyId)
                }
        }
    }

    /** Delete my up: server lifecycle first, then the local row + hosted copy. */
    fun deleteMyStory(storyId: String) {
        viewModelScope.launch {
            repository.deleteDailyUp(storyId)
                .onSuccess {
                    _images.value = _images.value - storyId
                    _viewer.value = null
                    _quotaUsed.value = repository.dailyUpsPostedToday()
                }
                .onFailure { e -> _error.value = dailyUpErrorText(appContext, e) }
        }
    }
}
