/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.whisper.WhisperGroupChatMessage
import com.frerox.toolz.data.whisper.WhisperGroupMembership
import com.frerox.toolz.data.whisper.WhisperGroupReadStore
import com.frerox.toolz.data.whisper.WhisperMutePreferences
import com.frerox.toolz.data.whisper.WhisperNotificationManager
import com.frerox.toolz.data.whisper.WhisperRepository
import com.frerox.toolz.data.whisper.clearGroupTyping
import com.frerox.toolz.data.whisper.deleteGroupMessageForEveryone
import com.frerox.toolz.data.whisper.deleteGroupMessageForMe
import com.frerox.toolz.data.whisper.fetchGroupMessages
import com.frerox.toolz.data.whisper.fetchGroupMessageByClientId
import com.frerox.toolz.data.whisper.fetchGroupReceipts
import com.frerox.toolz.data.whisper.friendNameMap
import com.frerox.toolz.data.whisper.flushGroupOutbox
import com.frerox.toolz.data.whisper.groupsEnabled
import com.frerox.toolz.data.whisper.markGroupSeen
import com.frerox.toolz.data.whisper.observeGroupLog
import com.frerox.toolz.data.whisper.observeGroupMessageDeletes
import com.frerox.toolz.data.whisper.observeGroupMessagesLive
import com.frerox.toolz.data.whisper.GroupMessageRow
import com.frerox.toolz.data.whisper.observeGroupReceiptEvents
import com.frerox.toolz.data.whisper.observeGroupTyping
import com.frerox.toolz.data.whisper.openGroupImage
import com.frerox.toolz.data.whisper.openGroupRowToMessage
import com.frerox.toolz.data.whisper.GroupPoll
import com.frerox.toolz.data.whisper.GroupReplyRef
import com.frerox.toolz.data.whisper.GroupVote
import com.frerox.toolz.data.whisper.WhisperGroupContent
import com.frerox.toolz.data.whisper.GROUP_REACTION_EMOJI
import com.frerox.toolz.data.whisper.cachedGroupSystemEvents
import com.frerox.toolz.data.whisper.fetchGroupReactions
import com.frerox.toolz.data.whisper.WhisperGroupReactionEvent
import com.frerox.toolz.data.whisper.observeGroupReactionEvents
import com.frerox.toolz.data.whisper.sendGroupContent
import com.frerox.toolz.data.whisper.toggleGroupReaction
import com.frerox.toolz.data.whisper.sendGroupImage
import com.frerox.toolz.data.whisper.sendGroupMessage
import com.frerox.toolz.data.whisper.sendGroupTyping
import com.frerox.toolz.data.whisper.syncGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Phase-2 groups: one group conversation (messages + send + mute + images +
 * receipts + typing + paging). Every load re-verifies the event log; every
 * send seals fresh per member.
 */
@HiltViewModel
class WhisperGroupChatViewModel @Inject constructor(
    private val repository: WhisperRepository,
    private val mutePrefs: WhisperMutePreferences,
    private val notificationManager: WhisperNotificationManager,
    private val readStore: WhisperGroupReadStore,
    // Screenshot-override parity with the 1:1 chat — the same password-gated
    // user setting decides when group E2EE content may be captured.
    private val settingsRepository: SettingsRepository,
    // M-10 pattern: `viewModelScope` is cancelled the moment onCleared starts —
    // residual cleanup (typing off) must ride an application-lifetime scope.
    @com.frerox.toolz.di.ApplicationScope private val appScope: kotlinx.coroutines.CoroutineScope,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val groupId: String = savedStateHandle.get<String>("groupId").orEmpty()

    /** My user id for mention matching ("mentioned you" highlighting). */
    val myUserId: String get() = repository.myId

    /** Screenshot-override parity with the 1:1 chat (same shared setting). */
    val screenshotBypassEnabled: StateFlow<Boolean> = settingsRepository.whisperScreenshotBypass
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    data class UiState(
        val membership: WhisperGroupMembership? = null,
        val messages: List<WhisperGroupChatMessage> = emptyList(),
        val isLoading: Boolean = true,
        val loadingMore: Boolean = false,
        val hasMore: Boolean = true,
        val isSending: Boolean = false,
        val isUploadingImage: Boolean = false,
        val isMuted: Boolean = false,
        /** True when the event log carries an unverifiable event (banner). */
        val degraded: Boolean = false,
        val typingNames: List<String> = emptyList(),
        /** Decrypted image bytes by row id (loaded lazily, never re-fetched). */
        val images: Map<String, ByteArray> = emptyMap(),
        /** Seen-by user ids by message client_id (my messages only matter). */
        val receipts: Map<String, List<String>> = emptyMap(),
        val memberNames: Map<String, String> = emptyMap(),
        /** Poll tallies by poll id (latest vote per sender wins). */
        val pollTallies: Map<String, PollTally> = emptyMap(),
        /** Closed poll ids (admin froze the tally; voting disabled). */
        val closedPolls: Set<String> = emptySet(),
        /** Reactions by message client_id (emoji to user ids). */
        val reactions: Map<String, Map<String, List<String>>> = emptyMap(),
        /** Row ids whose images expired (rendered as expired, never fetched). */
        val expiredImages: Set<String> = emptySet(),
        /** Row ids whose image load FAILED (inline tap-to-retry in the bubble). */
        val failedImages: Set<String> = emptySet(),
        /** In-timeline activity (join/leave/rename/role/picture/settings). */
        val systemEvents: List<SysLine> = emptyList(),
        /** First unread message id at load (unread separator anchor). */
        val firstUnreadId: String? = null,
        /** Unread count at load (latest-FAB badge). */
        val unreadCount: Int = 0,
        /** Optimistic outgoing text rows (shown until the real row lands). */
        val pending: List<PendingMessage> = emptyList(),
        val error: String? = null,
    )

    /** A text message that is sending (or failed) and not in the log yet. */
    data class PendingMessage(
        val localId: String,
        val text: String,
        val reply: GroupReplyRef?,
        val mentionIds: List<String>,
        val createdAtMs: Long,
        val failed: Boolean = false,
    )

    /** One centered activity line from the verified event log. */
    data class SysLine(
        val seq: Long,
        val text: String,
        val atMs: Long,
    )

    /** Vote tally for one poll card. */
    data class PollTally(
        val myVote: Int? = null,
        val counts: Map<Int, Int> = emptyMap(),
    )

    private val _uiState = MutableStateFlow(UiState(isMuted = mutePrefs.isMuted(muteKey(groupId))))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var watchJobs: List<Job> = emptyList()
    private var lastTypingSentMs = 0L

    /** Text sends go out strictly in order (parallel seals could reorder). */
    private val sendMutex = Mutex()

    // V-5 parity with the 1:1 chat: a bounded ACCESS-ORDER window of decrypted
    // image bytes. The groups map used to grow for the whole session (every
    // opened photo, up to megabytes each, for the VM's lifetime) — a straight
    // OOM path on busy groups. Evicted ids silently re-fetch when scrolled
    // back into view (LaunchedEffect(msg.id) re-fires).
    private val imageCacheMutex = Mutex()
    private val decryptedImages = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>): Boolean =
            size > MAX_DECRYPTED_IMAGES
    }

    /** Group chat image decode lanes are main-dispatcher guarded. */
    private val inFlightImages = mutableSetOf<String>()

    init {
        if (groupsEnabled() && groupId.isNotBlank()) {
            // Open-chat suppression id (mirrors 1:1 currentChatId).
            notificationManager.currentChatId = "group:$groupId"
            load()
            watchAll()
        }
    }

    /**
     * Realtime subscriptions are SELF-HEALING. The previous
     * `runCatching { flow.collect {} }` died silently on the FIRST thrown
     * event — one dropped channel took every further update down with it for
     * the rest of the screen's life. Every watcher now resubscribes through
     * [watchLive] with a capped backoff until the VM leaves.
     */
    private fun watchLive(tag: String, block: suspend () -> Unit): Job = viewModelScope.launch {
        var backoff = 1_500L
        while (isActive) {
            try {
                block()
                backoff = 1_500L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("WhisperGroupChatVM", "$tag watcher dropped; resubscribing", e)
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }

    private fun watchAll() {
        watchJobs.forEach { it.cancel() }
        val logJob = watchLive("log") {
            repository.observeGroupLog(groupId).collect { load() }
        }
        // New rows ARRIVE with their sealed frame: the insert is ingested
        // locally (decrypt + merge, zero refetch). Wipes and pings without a
        // decodable row still fall back to the full reload lane.
        val msgJob = watchLive("rows") {
            repository.observeGroupMessagesLive(groupId).collect { ping ->
                val row = ping.row
                if (row != null) ingestRow(row) else load()
            }
        }
        val delJob = watchLive("deletes") {
            repository.observeGroupMessageDeletes(groupId).collect { load() }
        }
        val typingJob = watchLive("typing") {
            repository.observeGroupTyping(groupId).collect { ids ->
                val names = _uiState.value.memberNames
                _uiState.update { it.copy(typingNames = ids.map { id -> names[id] ?: "Someone" }) }
            }
        }
        // Reaction chips merge in place (Applied/Removed); Refetch is the rare
        // unresolvable delete that falls back to the bulk read.
        val reactJob = watchLive("reactions") {
            repository.observeGroupReactionEvents(groupId).collect { evt ->
                when (evt) {
                    is WhisperGroupReactionEvent.Applied ->
                        applyReaction(evt.messageId, evt.userId, evt.emoji)
                    is WhisperGroupReactionEvent.Removed ->
                        applyReaction(evt.messageId, evt.userId, null)
                    WhisperGroupReactionEvent.Refetch -> refreshReactions()
                }
            }
        }
        val receiptJob = watchLive("receipts") {
            repository.observeGroupReceiptEvents(groupId).collect { row ->
                if (row.userId == repository.myId) return@collect
                _uiState.update { s ->
                    val users = s.receipts[row.messageId].orEmpty().filter { it != row.userId } + row.userId
                    s.copy(receipts = s.receipts + (row.messageId to users))
                }
            }
        }
        watchJobs = listOf(logJob, msgJob, delJob, typingJob, reactJob, receiptJob)
    }

    override fun onCleared() {
        watchJobs.forEach { it.cancel() }
        // NOT viewModelScope (cancelled at onCleared entry — dead code gone):
        // release my typing signal from app scope; the 8s freshness window
        // covers any residual row server-side.
        appScope.launch {
            runCatching { repository.clearGroupTyping(groupId) }
        }
        if (notificationManager.currentChatId == "group:$groupId") {
            notificationManager.currentChatId = null
        }
        super.onCleared()
    }

    private var loadJob: Job? = null

    @Volatile
    private var loadQueued = false

    /**
     * Timeline reload, SINGLE-FLIGHT: a burst of triggers (realtime events,
     * retries, deletes) collapses into ONE full pass plus at most one queued
     * re-run. Serially launched passes used to interleave their `_uiState`
     * writes — a slower OLDER pass could overwrite a newer one's membership
     * and unread counts. Signature unchanged (still returns a Job).
     */
    fun load(): Job {
        loadQueued = true
        loadJob?.takeIf { it.isActive }?.let { return it }
        return viewModelScope.launch {
            try {
                do {
                    loadQueued = false
                    runLoadOnce()
                } while (loadQueued)
            } finally {
                loadJob = null
            }
        }.also { loadJob = it }
    }

    private suspend fun runLoadOnce() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        // Drain queued group sends first so they land before the sync reads.
        runCatching { repository.flushGroupOutbox() }
        // Capture the read watermark BEFORE this load marks everything read.
        val markBefore = readStore.readAt(groupId)
        val membership = repository.syncGroup(groupId)
        membership.onSuccess { m ->
            val messages = repository.fetchGroupMessages(groupId)
            messages.onSuccess { list ->
                val names = repository.friendNameMap()
                val receipts = repository.fetchGroupReceipts(groupId).getOrNull().orEmpty()
                val reactionMap = repository.fetchGroupReactions(groupId).getOrNull().orEmpty()
                val myId = repository.myId
                val unread = list.filter { it.createdAt > markBefore && it.senderId != myId }
                val systems = repository.cachedGroupSystemEvents(groupId).mapNotNull { raw ->
                    sysText(raw.type, raw.actor, raw.payload, names, myId)?.let { text ->
                        SysLine(seq = raw.seq, text = text, atMs = raw.createdAtMs)
                    }
                }
                _uiState.update {
                    it.copy(
                        membership = m,
                        messages = list,
                        isLoading = false,
                        hasMore = list.size >= PAGE_SIZE,
                        degraded = m.degraded,
                        isSending = false,
                        isUploadingImage = false,
                        receipts = receipts,
                        memberNames = names,
                        pollTallies = tallyPolls(list, myId),
                        closedPolls = closedPollIds(list),
                        reactions = reactionMap,
                        systemEvents = systems,
                        firstUnreadId = unread.firstOrNull()?.id,
                        unreadCount = unread.size,
                    )
                }
                // Seen receipts + read watermark (fire-and-forget).
                viewModelScope.launch {
                    runCatching { repository.markGroupSeen(groupId, list.map { msg -> msg.clientId }) }
                    list.lastOrNull()?.createdAt?.let { readStore.markRead(groupId, it) }
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        membership = m, isLoading = false, degraded = m.degraded,
                        isSending = false, isUploadingImage = false, error = err(e),
                    )
                }
            }
        }.onFailure { e ->
            _uiState.update {
                it.copy(isLoading = false, isSending = false, isUploadingImage = false, error = err(e))
            }
        }
    }

    /**
     * Full-history search: pages the whole log (up to 500 rows) decrypting
     * each page. Server rows are opaque, so search is client-side by
     * necessity; results are newest-last like the timeline.
     */
    suspend fun searchAll(query: String): List<WhisperGroupChatMessage> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        val out = mutableListOf<WhisperGroupChatMessage>()
        var cursor: String? = null
        var guard = 0
        while (guard++ < 10) {
            val page = repository.fetchGroupMessages(groupId, PAGE_SIZE, before = cursor)
                .getOrNull().orEmpty()
            if (page.isEmpty()) break
            out.addAll(page.filter { it.vote == null && it.pollClose == null && it.body.lowercase().contains(q) })
            cursor = page.firstOrNull()?.createdAt ?: break
            if (page.size < PAGE_SIZE) break
        }
        return out.sortedWith(compareBy({ it.createdAt }, { it.id }))
    }

    /** Prepends the next older page (cursor = oldest loaded created_at). */
    fun loadMore() {
        val cur = _uiState.value
        if (cur.loadingMore || !cur.hasMore || cur.isLoading) return
        val oldest = cur.messages.firstOrNull()?.createdAt ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMore = true) }
            repository.fetchGroupMessages(groupId, PAGE_SIZE, before = oldest)
                .onSuccess { older ->
                    _uiState.update {
                        val merged = (older + it.messages).distinctBy { msg -> msg.id }
                            .sortedWith(compareBy({ msg -> msg.createdAt }, { msg -> msg.id }))
                        it.copy(
                            messages = merged,
                            loadingMore = false,
                            hasMore = older.size >= PAGE_SIZE,
                            pollTallies = tallyPolls(merged, repository.myId),
                            closedPolls = closedPollIds(merged),
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loadingMore = false, error = err(e)) }
                }
        }
    }

    fun send(
        text: String,
        reply: GroupReplyRef? = null,
        mentionIds: List<String> = emptyList(),
    ) {
        val clean = text.trim()
        if (clean.isBlank()) return
        if (clean.length > WhisperRepository.MAX_MESSAGE_CHARS) {
            _uiState.update {
                it.copy(error = appContext.getString(com.frerox.toolz.R.string.st_Whisper_Groups_MsgTooLong))
            }
            return
        }
        viewModelScope.launch { repository.clearGroupTyping(groupId) }
        val pending = PendingMessage(
            localId = java.util.UUID.randomUUID().toString(),
            text = clean,
            reply = reply,
            mentionIds = mentionIds,
            createdAtMs = System.currentTimeMillis(),
        )
        _uiState.update { it.copy(pending = it.pending + pending, error = null) }
        dispatchPending(pending)
    }

    private fun dispatchPending(p: PendingMessage) {
        viewModelScope.launch {
            _uiState.update { s ->
                s.copy(
                    isSending = true,
                    pending = s.pending.map { if (it.localId == p.localId) it.copy(failed = false) else it },
                )
            }
            val result = sendMutex.withLock {
                repository.sendGroupContent(
                    groupId,
                    WhisperGroupContent(t = p.text, reply = p.reply, mentions = p.mentionIds),
                )
            }
            result
                .onSuccess { clientId ->
                    // Swap the optimistic row for the real one in one beat:
                    // a single targeted select decodes the pending entry —
                    // never the full reload burst (the realtime ingest of the
                    // same row dedupes by id anyway).
                    viewModelScope.launch {
                        val real = repository
                            .fetchGroupMessageByClientId(groupId, clientId, _uiState.value.membership)
                            .getOrNull()
                        _uiState.update { s ->
                            val merged = if (real != null) {
                                (s.messages + real).distinctBy { it.id }
                                    .sortedWith(compareBy({ it.createdAt }, { it.id }))
                            } else {
                                s.messages
                            }
                            s.copy(
                                messages = merged,
                                pending = s.pending.filterNot { it.localId == p.localId },
                                isSending = false,
                            )
                        }
                        if (real == null) refreshTimelineHead()
                        // The open screen counts MY message as seen, exactly
                        // like a reload used to do.
                        real?.clientId?.let {
                            runCatching { repository.markGroupSeen(groupId, listOf(it)) }
                        }
                        real?.createdAt?.takeIf { it.isNotBlank() }?.let { readStore.markRead(groupId, it) }
                    }
                }
                .onFailure { e ->
                    _uiState.update { s ->
                        s.copy(
                            isSending = false,
                            pending = s.pending.map { if (it.localId == p.localId) it.copy(failed = true) else it },
                            error = err(e),
                        )
                    }
                }
        }
    }

    fun retryPending(localId: String) {
        _uiState.value.pending.firstOrNull { it.localId == localId && it.failed }?.let { dispatchPending(it) }
    }

    fun discardPending(localId: String) {
        _uiState.update { s -> s.copy(pending = s.pending.filterNot { it.localId == localId }) }
    }

    /** Sends a poll card (question + 2..6 options). */
    fun sendPoll(question: String, options: List<String>) {
        val q = question.trim()
        val opts = options.map { it.trim() }.filter { it.isNotBlank() }.take(6)
        if (q.isBlank() || opts.size < 2) {
            _uiState.update {
                it.copy(error = appContext.getString(com.frerox.toolz.R.string.st_Whisper_Groups_PollInvalid))
            }
            return
        }
        // Same-frame double-confirm guard (see the field documentation above):
        // identical question+options inside 2 s is one send.
        val key = "$q\u0000${opts.joinToString("\u0000")}"
        val now = System.currentTimeMillis()
        if (key == lastPollSendKey && now - lastPollSendAtMs < 2_000) return
        lastPollSendKey = key
        lastPollSendAtMs = now
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            val poll = GroupPoll(id = java.util.UUID.randomUUID().toString(), q = q, opts = opts)
            repository.sendGroupContent(groupId, WhisperGroupContent(t = "\ud83d\udcca $q", poll = poll))
                .onSuccess {
                    _uiState.update { it.copy(isSending = false) }
                    refreshTimelineHead()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSending = false, error = err(e)) }
                }
        }
    }

    /** Double-tap guard: the same vote option / same close action fires once. */
    private val pollActionsInFlight = mutableSetOf<String>()

    /**
     * Same-frame double-confirm guards for the poll/photo send sheets. Both
     * sheets remove themselves on the first send tap, but a frozen frame can
     * deliver both taps to the still-composed button — two signed events (and,
     * for a photo, a second sealed upload that burns hosting quota). Debounced
     * on PAYLOAD identity inside a short window, never on a shared busy flag:
     * a flag would silently drop a genuinely different send made while the
     * first upload is still in flight. Refilling a sheet with new content
     * within the window is humanly impossible (attach → picker/typing takes
     * seconds), so the window never eats a real message.
     */
    private var lastPollSendKey: String? = null
    private var lastPollSendAtMs = 0L
    private var lastImageSendKey: Int? = null
    private var lastImageSendAtMs = 0L

    /** Votes (or re-votes — latest per sender wins the tally). */
    fun vote(pollId: String, opt: Int) {
        if (pollId.isBlank()) return
        if (pollId in _uiState.value.closedPolls) return
        val actionKey = "v:$pollId:$opt"
        if (actionKey in pollActionsInFlight) return
        pollActionsInFlight += actionKey
        viewModelScope.launch {
            try {
                repository.sendGroupContent(groupId, WhisperGroupContent(vote = GroupVote(pollId, opt)))
                    .onSuccess { refreshTimelineHead() }
                    .onFailure { e -> _uiState.update { it.copy(error = err(e)) } }
            } finally {
                pollActionsInFlight -= actionKey
            }
        }
    }

    /** Admins freeze a poll (tally stops, voting disables). */
    fun closePoll(pollId: String) {
        if (pollId.isBlank() || pollId in _uiState.value.closedPolls) return
        val actionKey = "c:$pollId"
        if (actionKey in pollActionsInFlight) return
        pollActionsInFlight += actionKey
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = null) }
                repository.sendGroupContent(groupId, WhisperGroupContent(t = "poll closed", pollClose = pollId))
                    .onSuccess { refreshTimelineHead() }
                    .onFailure { e -> _uiState.update { it.copy(error = err(e)) } }
            } finally {
                pollActionsInFlight -= actionKey
            }
        }
    }

    /** Emoji tap: same emoji removes, anything else replaces. */
    fun toggleReaction(msg: WhisperGroupChatMessage, emoji: String) {
        if (emoji !in GROUP_REACTION_EMOJI || msg.clientId.isBlank()) return
        val me = repository.myId
        // Optimistic flip first — the chip reacts instantly, the realtime
        // channel confirms with the authoritative event; failures roll the
        // map back with a bulk refetch.
        val mine = _uiState.value.reactions[msg.clientId]?.get(emoji)?.contains(me) == true
        applyReaction(msg.clientId, me, if (mine) null else emoji)
        viewModelScope.launch {
            repository.toggleGroupReaction(groupId, msg.clientId, emoji)
                .onFailure { e ->
                    _uiState.update { it.copy(error = err(e)) }
                    refreshReactions()
                }
        }
    }

    private fun refreshReactions() {
        viewModelScope.launch {
            val map = repository.fetchGroupReactions(groupId).getOrNull()
            if (map != null) _uiState.update { it.copy(reactions = map) }
        }
    }

    /**
     * Realtime ingest: a freshly arrived row is decrypted locally and merged —
     * no outbox flush, no membership sync, no bulk refetch. The realtime
     * Insert already carries the sealed frame, so a new message lands with a
     * single decrypt. Anything the ingest cannot settle in this state (no
     * membership yet, undecodable payload) falls back to the full [load].
     */
    private fun ingestRow(row: GroupMessageRow) {
        viewModelScope.launch {
            val s = _uiState.value
            if (s.messages.any { it.id == row.id || it.clientId == row.clientId }) return@launch
            val m = s.membership ?: run { load(); return@launch }
            val me = repository.myId
            val msg = runCatching {
                repository.openGroupRowToMessage(row, groupId, m, me, s.memberNames)
            }.getOrNull()
            if (msg == null) {
                load()
                return@launch
            }
            // Watermark BEFORE any read-mark, exactly like a reload would do.
            val markBefore = readStore.readAt(groupId)
            _uiState.update { st ->
                val merged = (st.messages + msg).distinctBy { it.id }
                    .sortedWith(compareBy({ it.createdAt }, { it.id }))
                val unread = merged.filter { it.createdAt > markBefore && it.senderId != me }
                st.copy(
                    messages = merged,
                    pollTallies = tallyPolls(merged, me),
                    closedPolls = closedPollIds(merged),
                    firstUnreadId = st.firstUnreadId ?: unread.firstOrNull()?.id,
                    unreadCount = unread.size,
                )
            }
            // The open screen counts as read, same as a reload does.
            runCatching { repository.markGroupSeen(groupId, listOf(msg.clientId)) }
            msg.createdAt.takeIf { it.isNotBlank() }?.let { readStore.markRead(groupId, it) }
        }
    }

    /** One reaction changes hands or flips away; patch the map in place. */
    private fun applyReaction(messageId: String, userId: String, emoji: String?) {
        _uiState.update { s ->
            val current = s.reactions[messageId].orEmpty()
            // Replace semantics: a user holds at most one emoji per message.
            val stripped = current.entries
                .mapNotNull { (e, users) -> e to users.filter { it != userId } }
                .toMap()
                .filterValues { it.isNotEmpty() }
            val next = if (emoji != null) {
                stripped + (emoji to (stripped[emoji].orEmpty() + userId))
            } else {
                stripped
            }
            s.copy(reactions = s.reactions + (messageId to next))
        }
    }

    /**
     * Head-only refetch (one bounded select — never the full 5-call reload).
     * The belt-and-suspenders lane for sends/votes when the realtime echo
     * misses (cold subscribe, channel down): the next 16 rows cover the tail
     * of activity without re-syncing membership.
     */
    private fun refreshTimelineHead() {
        viewModelScope.launch {
            val membership = _uiState.value.membership ?: return@launch
            // fetchGroupMessages is already a Result — no outer runCatching
            // (double-wrapping would hide the list type from the chain).
            val head = repository
                .fetchGroupMessages(groupId, TIMELINE_HEAD_PAGE, membership = membership)
                .getOrNull()
                .orEmpty()
            if (head.isEmpty()) return@launch
            _uiState.update { s ->
                val merged = (head + s.messages).distinctBy { it.id }
                    .sortedWith(compareBy({ it.createdAt }, { it.id }))
                s.copy(
                    messages = merged,
                    pollTallies = tallyPolls(merged, repository.myId),
                    closedPolls = closedPollIds(merged),
                )
            }
        }
    }

    fun sendImage(bytes: ByteArray, mime: String, expiresAfterSeconds: Long? = null) {
        // Same-frame double-confirm guard: identical bytes inside 1.5 s are one
        // upload (a frozen frame can fire the sheet's Send twice); a genuinely
        // different pick — a new picker round-trip is seconds — always passes,
        // so concurrent distinct sends stay possible.
        val now = System.currentTimeMillis()
        val key = bytes.contentHashCode()
        if (key == lastImageSendKey && now - lastImageSendAtMs < 1_500) return
        lastImageSendKey = key
        lastImageSendAtMs = now
        viewModelScope.launch { repository.clearGroupTyping(groupId) }
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingImage = true, error = null) }
            repository.sendGroupImage(groupId, bytes, mime, expiresAfterSeconds = expiresAfterSeconds)
                .onSuccess {
                    // The image row arrives via the realtime ingest; this is
                    // only the belt lane when the echo misses. The spinner
                    // always clears here — the head refetch may be a no-op.
                    _uiState.update { it.copy(isUploadingImage = false) }
                    refreshTimelineHead()
                    if (_uiState.value.membership == null) load()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isUploadingImage = false, error = err(e)) }
                }
        }
    }

    /** Throttled typing signal (~3s) driven by the compose text field. */
    fun onTyping() {
        val now = System.currentTimeMillis()
        if (now - lastTypingSentMs < 3_000) return
        lastTypingSentMs = now
        viewModelScope.launch { runCatching { repository.sendGroupTyping(groupId) } }
    }

    /** Bubble retry affordance: clears the failed mark, then reloads. */
    fun retryImage(msg: WhisperGroupChatMessage) {
        _uiState.update { it.copy(failedImages = it.failedImages - msg.id) }
        loadImage(msg)
    }

    fun loadImage(msg: WhisperGroupChatMessage) {
        val ref = msg.image ?: return
        val current = _uiState.value
        if (msg.id in current.expiredImages || msg.id in current.failedImages) return
        // Settled (already decoded): no refetch. Eviction is visibility-driven
        // through rememberDecryptedImage's snapshots, so the state mirrors the
        // live LRU exactly.
        if (msg.id in current.images) return
        // Local expiry gate (openGroupImage also fails closed server-blind).
        if (ref.exp != null && ref.exp * 1000 < System.currentTimeMillis()) {
            _uiState.update { it.copy(expiredImages = it.expiredImages + msg.id) }
            return
        }
        if (msg.id in inFlightImages) return
        inFlightImages += msg.id
        viewModelScope.launch {
            try {
                val out = repository.openGroupImage(ref, "gmsg:${msg.id}")
                out.getOrNull()?.let { bytes -> rememberDecryptedImage(msg.id, bytes) }
                if (out.getOrNull() == null) {
                    val message = out.exceptionOrNull()?.message.orEmpty()
                    if (message.contains("expired")) {
                        _uiState.update { it.copy(expiredImages = it.expiredImages + msg.id) }
                    } else {
                        // A failed load must be VISIBLE and escapable: mark the
                        // row failed so the bubble offers tap-to-retry instead
                        // of spinning forever in silence.
                        _uiState.update { it.copy(failedImages = it.failedImages + msg.id) }
                    }
                }
            } finally {
                inFlightImages -= msg.id
            }
        }
    }

    /** Bounded LRU insert (V-5 parity): publishes the warmed window to uiState. */
    private suspend fun rememberDecryptedImage(id: String, bytes: ByteArray) {
        val snapshot = imageCacheMutex.withLock {
            decryptedImages[id] = bytes
            decryptedImages.toMap()
        }
        _uiState.update { it.copy(images = snapshot, failedImages = it.failedImages - id) }
    }

    fun deleteForMe(msg: WhisperGroupChatMessage) {
        viewModelScope.launch {
            runCatching { repository.deleteGroupMessageForMe(msg.clientId) }
            load()
        }
    }

    fun deleteForEveryone(msg: WhisperGroupChatMessage) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            repository.deleteGroupMessageForEveryone(groupId, msg.id)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(error = err(e)) } }
        }
    }

    fun toggleMute() {
        val key = muteKey(groupId)
        if (mutePrefs.isMuted(key)) mutePrefs.unmuteUser(key) else mutePrefs.muteUser(key)
        _uiState.update { it.copy(isMuted = mutePrefs.isMuted(key)) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Human line for one verified event, or null when the type carries no
     * timeline meaning (create shows once, invites are cards, votes aren't
     * events). Subject payloads resolve via friend names ("You" for me).
     */
    private fun sysText(
        type: String,
        actor: String,
        payload: String,
        names: Map<String, String>,
        me: String,
    ): String? {
        fun name(id: String): String = if (id == me) "You" else names[id] ?: "Someone"
        fun subject(): String = payload.trim().trim('"').takeIf {
            it.length >= 10 && it.all { c -> c.isLetterOrDigit() || c == '-' }
        }?.let { name(it) } ?: "Someone"
        return when (type) {
            "create" -> "${name(actor)} created the group"
            "join" -> "${name(actor)} joined"
            "leave" -> "${name(actor)} left"
            "remove" -> "${name(actor)} removed ${subject()}"
            "promote" -> "${name(actor)} made ${subject()} an admin"
            "demote" -> "${name(actor)} removed ${subject()} as admin"
            "rename" -> "${name(actor)} changed the group name"
            "picture" -> "${name(actor)} updated the group picture"
            "settings" -> "${name(actor)} updated the group settings"
            "decline" -> "${name(actor)} declined the invite"
            "cancel" -> "${name(actor)} revoked an invite for ${subject()}"
            "invite" -> "${name(actor)} invited ${subject()}"
            else -> null
        }
    }

    private fun err(e: Throwable?): String =
        com.frerox.toolz.data.whisper.groupErrorText(appContext, e)

    companion object {
        /** Group mutes reuse the 1:1 mute store under a reserved namespace. */
        fun muteKey(groupId: String) = "group:$groupId"
        const val PAGE_SIZE = 50

        /** V-5 parity: resident decrypted-image window (true LRU, see [decryptedImages]). */
        private const val MAX_DECRYPTED_IMAGES = 8

        /** Bounded head refetch for send/vote echo fallbacks (see [refreshTimelineHead]). */
        const val TIMELINE_HEAD_PAGE = 16

        /** Poll ids frozen by an admin close (any close row wins, latest wins ties). */
        fun closedPollIds(messages: List<WhisperGroupChatMessage>): Set<String> =
            messages.mapNotNullTo(mutableSetOf()) { it.pollClose?.takeIf { id -> id.isNotBlank() } }

        /** Latest vote per sender per poll wins; my latest vote is tracked. */
        fun tallyPolls(messages: List<WhisperGroupChatMessage>, me: String): Map<String, PollTally> {
            val latest = mutableMapOf<String, MutableMap<String, Int>>()
            messages.forEach { msg ->
                val v = msg.vote ?: return@forEach
                if (v.opt < 0) return@forEach
                latest.getOrPut(v.poll) { mutableMapOf() }[msg.senderId] = v.opt
            }
            return latest.mapValues { (_, bySender) ->
                val counts = mutableMapOf<Int, Int>()
                bySender.values.forEach { opt -> counts[opt] = (counts[opt] ?: 0) + 1 }
                PollTally(myVote = bySender[me], counts = counts)
            }
        }
    }
}
