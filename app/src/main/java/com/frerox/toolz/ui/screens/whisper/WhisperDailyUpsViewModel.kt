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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
    private val notificationManager: com.frerox.toolz.data.whisper.WhisperNotificationManager,
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
        /** Absolute server-reconciled expiry; the only safe seen-clock anchor. */
        val expiresAtMs: Long,
        /** Time still on the story clock at snapshot time (for the progress UI). */
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

    /**
     * Poster whose ring this VM has reported as on screen. Declared BEFORE the
     * init block: property initializers run in declaration order, so a
     * `null` initializer placed after an inline-starting collector would wipe
     * the id the collector just set.
     */
    @Volatile private var viewingOwnerId: String? = null

    init {
        refresh()
        viewModelScope.launch { collectProfileNames() }
        // The ring is on screen: the shade card for that poster is stale now,
        // whether the viewer was opened from the hub, the stories bar or the
        // notification itself. AutoCancel can only fire on a card tap, so
        // without this the card lived until that person's next post.
        viewModelScope.launch {
            viewer
                .map { it?.currentSegment?.ownerId }
                .distinctUntilChanged()
                .collect { owner ->
                    // Suppress a push for the ring already on screen (the same
                    // open-surface gate chats get from currentChatId), and
                    // dismiss the shade card that announced it.
                    notificationManager.currentDailyUpOwnerId = owner
                    if (owner != null) {
                        viewingOwnerId = owner
                        notificationManager.cancelDailyUpNotification(owner)
                    }
                }
        }
    }

    override fun onCleared() {
        // viewModelScope is already cancelled here, so the collector cannot
        // emit its closing null: without this a stale id would suppress that
        // poster's Daily Up pushes for the rest of the process.
        val mine = viewingOwnerId
        if (mine != null && notificationManager.currentDailyUpOwnerId == mine) {
            notificationManager.currentDailyUpOwnerId = null
        }
        viewingOwnerId = null
        super.onCleared()
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
                expiresAtMs = row.expiresAtMs,
                remainingMs = (row.expiresAtMs - nowMs).coerceAtLeast(0L),
            )
        }

    fun closeViewer() {
        _viewer.value = null
        receiptedStories.clear()
        reactionLookups.clear()
        releaseViewerCaches()
    }

    /** True while [storyId] belongs to the viewer snapshot currently open. */
    private fun viewerContains(storyId: String): Boolean =
        _viewer.value?.segments?.any { segment ->
            segment.stories.any { it.storyId == storyId }
        } == true

    /**
     * Frees viewer-only state when the overlay closes.
     *
     * The decoded-image window is consumed by the overlay alone, yet up to six
     * ~2 MP ARGB frames (~8 MB each) stayed resident after the last frame
     * disappeared — roughly 48 MB of pure heap pressure on a hub that never
     * reads them. A decrypt still in flight when the overlay closes finishes
     * into the void (see [viewerContains]) instead of repopulating the window.
     * Receipt/reaction maps are pruned to stories the hub still lists so a day
     * of opens cannot accumulate them.
     */
    private fun releaseViewerCaches() {
        if (_images.value.isNotEmpty()) _images.value = emptyMap()
        if (_failedImages.value.isNotEmpty()) _failedImages.value = emptySet()
        val live = uiState.value.posterGroups
            .flatMapTo(HashSet()) { poster -> poster.stories.map { it.storyId } }
        _receipts.update {
            val stats = pruneToLiveStories(it.ownStats, live)
            val reactions = pruneToLiveStories(it.myReactions, live)
            if (stats === it.ownStats && reactions === it.myReactions) {
                it
            } else {
                it.copy(ownStats = stats, myReactions = reactions)
            }
        }
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
        // Bound the lookup exactly like the repository's boundedStoryIds():
        // the read is capped at SYNC_PAGE, and marking MORE ids as "looked up"
        // would permanently skip every story past the cap for this open.
        val missing = v.segments
            .flatMap { segment -> segment.stories }
            .filter { !it.isMine }
            .map { it.storyId }
            .filter { it !in reactionLookups && it !in _receipts.value.myReactions }
            .distinct()
            .take(WhisperDailyUpsConfig.SYNC_PAGE)
        if (missing.isEmpty()) return
        reactionLookups += missing
        viewModelScope.launch {
            repository.fetchMyDailyUpReactions(missing)
                .onSuccess { loaded ->
                    // update{} (not read-modify-write): a palette toggle
                    // settling at the same instant must not be overwritten.
                    _receipts.update { it.copy(myReactions = it.myReactions + loaded) }
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
        // Anchor the seen-clock to the story's ABSOLUTE expiry. The old
        // `now + remainingMs` re-based the snapshot's remaining time onto the
        // marking instant: a viewer who opened the ring minutes after the hub
        // snapshot would keep an already-expired story "unseen" far past its
        // real 24h window.
        seenStore.markSeen(item.storyId, item.expiresAtMs, nowMs)
        if (receiptedStories.add(item.storyId)) markViewedOnce(item.storyId)
    }

    /** One story image failed to load; the viewer page offers an inline retry. */
    fun retryImage(storyId: String) {
        _failedImages.update { it - storyId }
        ensureImage(storyId)
    }

    /**
     * Decrypt-once per storyId; concurrent loads collapse on [inFlightImages].
     * Every state write is an atomic [update] — the previous
     * `value = value + x` pattern lost updates when the current page and its
     * preload settled at the same instant.
     */
    private fun ensureImage(storyId: String) {
        if (storyId in _images.value) return
        if (storyId in inFlightImages.value) return
        _failedImages.update { it - storyId }
        inFlightImages.update { it + storyId }
        viewModelScope.launch {
            try {
                repository.downloadDailyUpImage(storyId)
                    .onSuccess { image ->
                        val bmp = withContext(Dispatchers.IO) { decodeDisplayBitmap(image.bytes) }
                        // The overlay may have closed (or the story may have
                        // been deleted) while this decrypt was in flight: never
                        // put the freed frame back.
                        if (!viewerContains(storyId)) return@launch
                        if (bmp != null) {
                            insertViewerImage(storyId, bmp)
                        } else {
                            _failedImages.update { it + storyId }
                            _viewerError.value = appContext.getString(R.string.st_Whisper_DailyUp_ErrCorrupt)
                        }
                    }
                    .onFailure { e ->
                        if (!viewerContains(storyId)) return@onFailure
                        _failedImages.update { it + storyId }
                        _viewerError.value = dailyUpErrorText(appContext, e)
                    }
            } finally {
                inFlightImages.update { it - storyId }
            }
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
        _images.update { current ->
            val merged = current + (storyId to bmp)
            if (merged.size > MAX_VIEWER_IMAGES) {
                merged.entries.drop(merged.size - MAX_VIEWER_IMAGES).associate { it.toPair() }
            } else {
                merged
            }
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
            // One bounded retry: the RPC is an idempotent upsert, and a single
            // transient failure used to lose the view for good because
            // [receiptedStories] blocks a second attempt in this open.
            repeat(2) { attempt ->
                if (repository.markDailyUpViewed(storyId).isSuccess) return@launch
                if (attempt == 0) delay(1_500)
            }
        }
    }

    private fun refreshOwnStats(storyId: String) {
        viewModelScope.launch {
            repository.fetchDailyUpReceipts(listOf(storyId))
                .onSuccess { list ->
                    val snap = list.firstOrNull() ?: return@onSuccess
                    _receipts.update {
                        it.copy(
                            ownStats = it.ownStats + (
                                storyId to OwnStoryStats(
                                    views = snap.viewerIds.size,
                                    reactions = snap.reactions.size,
                                )
                            ),
                        )
                    }
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
        val previous = _receipts.value.myReactions[storyId]
        _receipts.update {
            it.copy(
                myReactions = it.myReactions +
                    if (on) {
                        storyId to emoji
                    } else {
                        storyId to ""
                    },
            )
        }
        viewModelScope.launch {
            try {
                repository.toggleDailyUpReaction(storyId, emoji, on)
                    .onFailure { e ->
                        _receipts.update {
                            it.copy(myReactions = it.myReactions + (storyId to (previous ?: "")))
                        }
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
                    _images.update { it - storyId }
                    _failedImages.update { it - storyId }
                    inFlightImages.update { it - storyId }
                    receiptedStories -= storyId
                    reactionLookups -= storyId
                    // Only drop THIS page out of the snapshot. Closing the whole
                    // overlay would yank a reader who swiped on to another
                    // poster while the delete was still in flight.
                    dropViewerStory(storyId)
                    _quotaUsed.value = repository.dailyUpsPostedToday()
                }
                .onFailure { e -> _viewerError.value = dailyUpErrorText(appContext, e) }
        }
    }

    /**
     * Removes [storyId] from the open viewer snapshot, keeping the reader on
     * the story they are actually looking at. The deleted page itself closes
     * the overlay; an emptied poster segment is dropped and the flat position
     * is re-mapped so no unrelated story jumps under the finger.
     */
    private fun dropViewerStory(storyId: String) {
        val v = _viewer.value ?: return
        val flatDeleted = v.segments
            .flatMap { it.stories }
            .indexOfFirst { it.storyId == storyId }
        if (flatDeleted < 0) return
        val currentFlat = v.flatPosition
        if (flatDeleted == currentFlat) {
            _viewer.value = null
            releaseViewerCaches()
            return
        }
        val segments = v.segments.mapNotNull { segment ->
            val stories = segment.stories.filterNot { it.storyId == storyId }
            if (stories.isEmpty()) null else segment.copy(stories = stories)
        }
        if (segments.isEmpty()) {
            _viewer.value = null
            releaseViewerCaches()
            return
        }
        val target = if (flatDeleted < currentFlat) currentFlat - 1 else currentFlat
        val position = viewerPositionAtFlat(segments.map { it.stories.size }, target)
        if (position == null) {
            _viewer.value = null
            releaseViewerCaches()
        } else {
            _viewer.value = v.copy(
                segments = segments,
                segmentIndex = position.first,
                storyIndex = position.second,
            )
        }
    }

    private companion object {
        /** Viewer decoded-image window cap (see [insertViewerImage]). */
        const val MAX_VIEWER_IMAGES = 6
    }
}

/**
 * Keeps only receipt/reaction entries whose story the hub still lists.
 *
 * The maps are populated per viewer open; without this they grew for the whole
 * ViewModel lifetime (~24 h of expiring stories). Returns the SAME instance
 * when nothing is stale so a close with a live snapshot does not churn state.
 */
internal fun <V> pruneToLiveStories(map: Map<String, V>, live: Set<String>): Map<String, V> =
    if (map.keys.all(live::contains)) map else map.filterKeys { it in live }
