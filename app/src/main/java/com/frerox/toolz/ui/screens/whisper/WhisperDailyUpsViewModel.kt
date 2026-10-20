/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperDailyUpsConfig
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.data.whisper.WhisperRepository
import com.frerox.toolz.data.whisper.WhisperStorySeenStore
import com.frerox.toolz.data.whisper.dailyUpErrorText
import com.frerox.toolz.data.whisper.dailyUpsPostedToday
import com.frerox.toolz.data.whisper.deleteDailyUp
import com.frerox.toolz.data.whisper.downloadDailyUpImage
import com.frerox.toolz.data.whisper.fetchDailyUpReceipts
import com.frerox.toolz.data.whisper.fetchMyDailyUpReactions
import com.frerox.toolz.data.whisper.getMyProfile
import com.frerox.toolz.data.whisper.markDailyUpViewed
import com.frerox.toolz.data.whisper.observeFriendDailyUps
import com.frerox.toolz.data.whisper.observeMyDailyUps
import com.frerox.toolz.data.whisper.syncDailyUps
import com.frerox.toolz.data.whisper.toggleDailyUpReaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Daily Ups hub + viewer state machine.
 *
 * Everything is LOCAL-CACHE DRIVEN: Room rows (sealed envelopes + lifecycle
 * metadata) flow into the hub; the viewer opens envelopes on demand and holds
 * decoded images only in memory. Seen state is device-local only
 * ([WhisperStorySeenStore]): opaque story ids + expiry, used for ring
 * brightness and first-unseen entry, never synced to the server. Re-opening an
 * all-seen ring starts at its first story and still replays it.
 * Server truth arrives through [refresh] (pull): stories deliberately have no
 * realtime channel — the content-free push ping re-opens the hub instead.
 *
 * Failed loads are scoped honestly: a hub sync failure renders INLINE above
 * the rail (it must never masquerade as an empty day), while viewer failures
 * (image, reaction, delete) surface as a snackbar. No dialog, no retry queue —
 * the pull-retry is the load interaction itself (any refresh re-runs sync).
 */
@HiltViewModel
class WhisperDailyUpsViewModel @Inject constructor(
    private val repository: WhisperRepository,
    private val seenStore: WhisperStorySeenStore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    /** Screen-owned navigation gate: survives recomposition; one push per window. */
    private val navGate = ElapsedTapGate()

    fun acceptNavigation(nowMs: Long = SystemClock.elapsedRealtime()): Boolean =
        navGate.tryAccept(nowMs)

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

    /** Everything the hub renders: poster-grouped rings + honest sync state. */
    data class HubUiState(
        val loading: Boolean = true,
        /** Mine first, then friends unseen-first (exactly one entry per poster). */
        val posterGroups: List<StoryPoster> = emptyList(),
        val seenIds: Set<String> = emptySet(),
        val myId: String = "",
        val myName: String = "",
        val quotaUsed: Int = 0,
        /** Hub-scoped sync failure: inline banner, never a snackbar. */
        val error: String? = null,
        /** Viewer-scoped failure (image/receipt/delete): snackbar, never inline. */
        val viewerError: String? = null,
    )

    /** One poster's stories inside the viewer. Immutable for one open gesture. */
    data class ViewerSegment(
        val ownerId: String,
        val ownerName: String?,
        val isMine: Boolean,
        val stories: List<StoryItem>,
    )

    /** Full-screen viewer overlay; null = hidden. Immutable per open gesture. */
    data class ViewerState(
        val segments: List<ViewerSegment>,
        val segmentIndex: Int,
        val storyIndex: Int,
    ) {
        val segmentSizes: List<Int> get() = segments.map { it.stories.size }
        val currentSegment: ViewerSegment? get() = segments.getOrNull(segmentIndex)
        val currentStory: StoryItem? get() = currentSegment?.stories?.getOrNull(storyIndex)
        val flatTotal: Int get() = segments.sumOf { it.stories.size }
        val flatPosition: Int
            get() = viewerFlatPosition(segmentSizes, segmentIndex, storyIndex) ?: 0
    }

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

    /** Hub-scoped sync failure. Inline banner; cleared on refresh start/success. */
    private val _error = MutableStateFlow<String?>(null)
    /** Viewer-scoped failure. Snackbar; cleared when the snackbar is dismissed. */
    private val _viewerError = MutableStateFlow<String?>(null)
    private val _viewer = MutableStateFlow<ViewerState?>(null)
    private val _images = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    /** Story ids whose image decode failed (inline retry affordance in the viewer). */
    private val _failedImages = MutableStateFlow<Set<String>>(emptySet())
    private val _receipts = MutableStateFlow(ReceiptsUi())
    private val _quotaUsed = MutableStateFlow(0)
    private val _refreshing = MutableStateFlow(true)
    /** Coalescing handle: open-time sync + pull-to-refresh share one flight. */
    private var refreshJob: Job? = null
    private val _profiles = MutableStateFlow<Map<String, WhisperProfile>>(emptyMap())

    /** Minute ticker so "ends in 22h" labels stay honest while parked on the hub. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    private data class HubData(
        val groups: List<StoryPoster>,
        val seenIds: Set<String>,
        val myId: String,
        val myName: String,
    )

    /** Rows→poster groups; names land via [collectProfileNames], seen via the store. */
    private val hubData = combine(
        repository.observeMyDailyUps(),
        repository.observeFriendDailyUps(),
        _profiles,
        seenStore.seen,
        ticker,
    ) { myRows, friendRows, profiles, seen, nowMs ->
        HubData(
            groups = buildPosterGroups(myRows, friendRows, profiles, seen.keys, nowMs),
            seenIds = seen.keys.toSet(),
            myId = repository.myId,
            myName = profiles[repository.myId]?.effectiveName.orEmpty(),
        )
    }

    val uiState: StateFlow<HubUiState> = combine(
        hubData,
        _refreshing,
        _quotaUsed,
        _error,
        _viewerError,
    ) { data, refreshing, quotaUsed, error, viewerError ->
        HubUiState(
            loading = refreshing,
            posterGroups = data.groups,
            seenIds = data.seenIds,
            myId = data.myId,
            myName = data.myName,
            quotaUsed = quotaUsed,
            error = error,
            viewerError = viewerError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HubUiState())

    val viewer: StateFlow<ViewerState?> = _viewer.asStateFlow()
    val images: StateFlow<Map<String, ImageBitmap>> = _images.asStateFlow()
    val failedImages: StateFlow<Set<String>> = _failedImages.asStateFlow()
    val receipts: StateFlow<ReceiptsUi> = _receipts.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { collectProfileNames() }
    }

    /** Hub open/refresh: sync server truth + read back the day's quota.
     *  One sync in flight — a pull or re-open landing mid-sync coalesces. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        // A new attempt owns the banner: stale failures must not linger while
        // the retry is in flight. Success simply leaves this null.
        _error.value = null
        _refreshing.value = true
        refreshJob = viewModelScope.launch {
            repository.syncDailyUps()
                .onFailure { e -> _error.value = dailyUpErrorText(appContext, e) }
            // Drop seen ids whose story expired while the app was away. Runs on
            // both success and failure: the local clock is authority for expiry.
            seenStore.prune(System.currentTimeMillis())
            _quotaUsed.value = repository.dailyUpsPostedToday()
            _refreshing.value = false
        }
    }

    /** Manual dismiss for the hub's inline failure banner. */
    fun clearError() {
        _error.value = null
    }

    /** Dismiss the viewer snackbar (called after it has been shown). */
    fun clearViewerError() {
        _viewerError.value = null
    }

    /** One profile batch per new poster; names across the hub + viewer. */
    private suspend fun collectProfileNames() {
        val me = repository.myId
        // My own profile powers the Your-day card even on a day with no ups.
        repository.getMyProfile()
            .onSuccess { profile -> _profiles.update { it + (me to profile) } }
        combine(repository.observeMyDailyUps(), repository.observeFriendDailyUps()) { mine, friends ->
            (mine.map { it.ownerId } + friends.map { it.ownerId }).distinct()
        }.collect { ids ->
            // batchProfilesById deliberately propagates network faults (R-H2).
            // Unguarded here, a single flaky batch would kill this collector AND
            // the whole viewModelScope coroutine (app crash on open). The
            // collector must survive; the next row emission retries the names.
            val missing = ids.filter { it != me && it !in _profiles.value }
            if (missing.isNotEmpty()) {
                runCatching { repository.batchProfilesById(missing) }
                    .onSuccess { (blocked, fetched) ->
                        _profiles.update { current ->
                            current + fetched.filterKeys { it !in blocked }
                        }
                    }
            }
        }
    }

    // ── Viewer ─────────────────────────────────────────────────────────────

    /**
     * Opens the in-place viewer for [ownerId] (null = hub order: mine, then
     * friends), starting at [startStoryId] when it is still live in that
     * poster or else at that poster's first unseen story. Unknown or expired
     * owners are a silent no-op, so a stale deep link never opens an empty
     * black overlay.
     */
    fun openViewerForOwner(ownerId: String? = null, startStoryId: String? = null) {
        val state = uiState.value
        val order = viewerOrderFor(state.posterGroups, ownerId)
        if (order.isEmpty()) return
        val nowMs = System.currentTimeMillis()
        val segments = order.mapNotNull { poster ->
            val items = poster.toStoryItems(nowMs, state.myId)
            if (items.isEmpty()) {
                null
            } else {
                ViewerSegment(
                    ownerId = poster.ownerId,
                    ownerName = if (poster.isMine) null else poster.ownerName,
                    isMine = poster.isMine,
                    stories = items,
                )
            }
        }
        if (segments.isEmpty()) return
        val segmentIndex = if (ownerId != null) {
            segments.indexOfFirst { it.ownerId == ownerId }
        } else {
            0
        }
        if (segmentIndex !in segments.indices) return
        val segment = segments[segmentIndex]
        val explicit = startStoryId
            ?.let { id -> segment.stories.indexOfFirst { it.storyId == id } }
            ?: -1
        val firstUnseen = segment.stories.indexOfFirst { it.storyId !in state.seenIds }
        val storyIndex = if (explicit >= 0) explicit else firstUnseen.coerceAtLeast(0)
        receiptedStories.clear()
        reactionLookups.clear()
        _viewer.value = ViewerState(
            segments = segments,
            segmentIndex = segmentIndex,
            storyIndex = storyIndex.coerceIn(segment.stories.indices),
        )
        // Pre-warm the first frame so the 5 s clock starts on pixels, not on a spinner.
        segment.stories.getOrNull(storyIndex)?.let { ensureImage(it.storyId) }
    }

    /** My own ups only: never pages into friends from my card. */
    fun openMyViewer() {
        val mine = uiState.value.posterGroups.firstOrNull { it.isMine } ?: return
        openViewerForOwner(mine.ownerId)
    }

    private fun StoryPoster.toStoryItems(nowMs: Long, myId: String): List<StoryItem> =
        stories.map { row ->
            StoryItem(
                storyId = row.storyId,
                ownerId = row.ownerId,
                ownerName = if (row.ownerId == myId) "" else ownerName.orEmpty(),
                isMine = isMine,
                receiptsEnabled = row.receiptsEnabled,
                remainingMs = (row.expiresAtMs - nowMs).coerceAtLeast(0L),
            )
        }

    fun closeViewer() {
        _viewer.value = null
        receiptedStories.clear()
        reactionLookups.clear()
    }

    /** Advance one story; crossing a poster boundary is part of one open order. */
    fun viewerNext() {
        val v = _viewer.value ?: return
        val next = viewerNextPosition(v.segmentSizes, v.segmentIndex, v.storyIndex)
        if (next == null) {
            closeViewer()
        } else {
            _viewer.value = v.copy(segmentIndex = next.first, storyIndex = next.second)
        }
    }

    /** Back one story; the very first story clamps instead of closing. */
    fun viewerPrev() {
        val v = _viewer.value ?: return
        val prev = viewerPrevPosition(v.segmentSizes, v.segmentIndex, v.storyIndex) ?: return
        _viewer.value = v.copy(segmentIndex = prev.first, storyIndex = prev.second)
    }

    /** Absolute seek from the settled pager; out-of-range closes defensively. */
    fun seekViewer(page: Int) {
        val v = _viewer.value ?: return
        val position = viewerPositionAtFlat(v.segmentSizes, page)
        if (position == null) {
            closeViewer()
        } else if (position.first != v.segmentIndex || position.second != v.storyIndex) {
            _viewer.value = v.copy(segmentIndex = position.first, storyIndex = position.second)
        }
    }

    /** Pre-warms the next story's decrypted frame; failures surface as usual. */
    fun preloadStory(storyId: String) {
        ensureImage(storyId)
    }

    private val inFlightImages = MutableStateFlow(emptySet<String>())

    /** Stories already credited with a view receipt in this viewer session. */
    private val receiptedStories = mutableSetOf<String>()

    /**
     * Friend stories whose "my reaction" row was already looked up this open.
     * The query returns only existing reactions, so without this marker every
     * page turn would re-fetch the whole list for the reactions that are
     * legitimately absent.
     */
    private val reactionLookups = mutableSetOf<String>()

    /**
     * Called by the UI when a story is CURRENTLY on screen: loads its image
     * (decrypt-once) and owns the receipt/reaction reads for this open.
     * Local seen + view receipt are separate: only [markCurrentSeen] fires
     * them, and only once the image is actually ready.
     */
    fun onStoryDisplayed(storyId: String) {
        val v = _viewer.value ?: return
        val item = v.currentStory?.takeIf { it.storyId == storyId } ?: return
        ensureImage(storyId)
        if (item.isMine && item.receiptsEnabled) refreshOwnStats(storyId)
        val missing = v.segments
            .flatMap { segment -> segment.stories }
            .filter { !it.isMine }
            .map { it.storyId }
            .filter { it !in reactionLookups && it !in _receipts.value.myReactions }
            .distinct()
        if (missing.isEmpty()) return
        reactionLookups += missing
        viewModelScope.launch {
            repository.fetchMyDailyUpReactions(missing)
                .onSuccess { loaded ->
                    _receipts.value = _receipts.value.copy(myReactions = _receipts.value.myReactions + loaded)
                }
                .onFailure {
                    // Transient read failure: allow the next page turn to retry.
                    reactionLookups -= missing.toSet()
                }
        }
    }

    /**
     * Marks the current story seen exactly when its pixels are ready. Mine is
     * never written; loading/failed pages never write. The ring clears on the
     * next hub frame and the server receipt is sent at most once per open.
     */
    fun markCurrentSeen() {
        val item = _viewer.value?.currentStory ?: return
        if (item.isMine) return
        val nowMs = System.currentTimeMillis()
        seenStore.markSeen(item.storyId, nowMs + item.remainingMs, nowMs)
        if (receiptedStories.add(item.storyId)) markViewedOnce(item.storyId)
    }

    /** One story image failed to load; the viewer page offers an inline retry. */
    fun retryImage(storyId: String) {
        _failedImages.value = _failedImages.value - storyId
        ensureImage(storyId)
    }

    /** Decrypt-once per storyId; concurrent loads collapse on [inFlightImages]. */
    private fun ensureImage(storyId: String) {
        if (storyId in _images.value) return
        if (storyId in inFlightImages.value) return
        _failedImages.value = _failedImages.value - storyId
        inFlightImages.value = inFlightImages.value + storyId
        viewModelScope.launch {
            repository.downloadDailyUpImage(storyId)
                .onSuccess { image ->
                    val bmp = withContext(Dispatchers.IO) { decodeDisplayBitmap(image.bytes) }
                    if (bmp != null) {
                        insertViewerImage(storyId, bmp)
                    } else {
                        _failedImages.value = _failedImages.value + storyId
                        _viewerError.value = appContext.getString(R.string.st_Whisper_DailyUp_ErrCorrupt)
                    }
                }
                .onFailure { e ->
                    _failedImages.value = _failedImages.value + storyId
                    _viewerError.value = dailyUpErrorText(appContext, e)
                }
            inFlightImages.value = inFlightImages.value - storyId
        }
    }

    /**
     * Bounded in-memory window for decoded story bits. The hub never decrypts;
     * ONLY the viewer consumes this map, and a pager composes at most the
     * current page ±1. A whole day of ~2 MP ups (≈8 MB each) unbounded was a
     * straight OOM path — keep the viewer window resident, page back re-decodes
     * (decrypt-once per session trades for memory safety).
     */
    private fun insertViewerImage(storyId: String, bmp: ImageBitmap) {
        val merged = _images.value + (storyId to bmp)
        _images.value = if (merged.size > MAX_VIEWER_IMAGES) {
            merged.entries.drop(merged.size - MAX_VIEWER_IMAGES).associate { it.toPair() }
        } else {
            merged
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

    /** Optimistic palette toggle (revert + snackbar on failure). One toggle in
     *  flight per story: palette double-taps must not flip the reaction right
     *  back off while the first request settles. */
    private val reactionInFlight = mutableSetOf<String>()

    /** One-delete window anti-double-confirm (same class as [reactionInFlight]). */
    private var lastDeleteStoryKey: String? = null
    private var lastDeleteStoryAtMs = 0L

    fun toggleReaction(storyId: String, emoji: String, on: Boolean) {
        if (storyId in reactionInFlight) return
        reactionInFlight += storyId
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
            try {
                repository.toggleDailyUpReaction(storyId, emoji, on)
                    .onFailure { e ->
                        _receipts.value = _receipts.value.copy(
                            myReactions = _receipts.value.myReactions + (storyId to (previous ?: "")),
                        )
                        _viewerError.value = dailyUpErrorText(appContext, e)
                    }
                    .onSuccess {
                        val v = _viewer.value ?: return@onSuccess
                        val shown = v.currentStory?.takeIf { it.storyId == storyId }
                        if (shown?.isMine == true) refreshOwnStats(storyId)
                    }
            } finally {
                reactionInFlight -= storyId
            }
        }
    }

    /** Delete my up: server lifecycle first, then the local row + hosted copy. */
    fun deleteMyStory(storyId: String) {
        // Same-frame double-confirm guard (established VM-side pattern): the
        // delete-confirm button can receive both taps in a frozen frame — the
        // second delete then fails server-side with an "already gone" error
        // snackbar right after the first one succeeded.
        val now = System.currentTimeMillis()
        if (storyId == lastDeleteStoryKey && now - lastDeleteStoryAtMs < 2_000) return
        lastDeleteStoryKey = storyId
        lastDeleteStoryAtMs = now
        viewModelScope.launch {
            repository.deleteDailyUp(storyId)
                .onSuccess {
                    _images.value = _images.value - storyId
                    _viewer.value = null
                    _quotaUsed.value = repository.dailyUpsPostedToday()
                }
                .onFailure { e -> _viewerError.value = dailyUpErrorText(appContext, e) }
        }
    }

    private companion object {
        /** Viewer decoded-image window cap (see [insertViewerImage]). */
        const val MAX_VIEWER_IMAGES = 6
    }
}
