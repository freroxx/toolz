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

package com.frerox.toolz.ui.screens.whisper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

import com.frerox.toolz.data.settings.SettingsRepository

@HiltViewModel
class WhisperChatViewModel @Inject constructor(
    private val repository: WhisperRepository,
    private val authManager: WhisperAuthManager,
    private val crypto: WhisperCrypto,
    private val notificationManager: WhisperNotificationManager,
    private val mutePrefs: WhisperMutePreferences,
    private val hiddenChatsStore: WhisperHiddenChatsStore,
    private val settingsRepository: SettingsRepository,
    // M-10 FIX (reviewwhisper.md): injected app scope replaces the ad-hoc
    // `CoroutineScope(NonCancellable + Dispatchers.IO)` in onCleared().
    @com.frerox.toolz.di.ApplicationScope private val appScope: CoroutineScope,
    private val undoBufferStore: com.frerox.toolz.data.whisper.WhisperUndoBufferStore,
    // V3-FIX (task F): encrypted disk tier between the memory LRU and the network so
    // scrolling no longer re-downloads/re-decrypts every image after process death.
    private val imageDiskCache: WhisperImageDiskCache,
    // V2-FIX (reviewwhisper.md) V-15: handle is retained so the draft can survive process death.
    private val savedStateHandle: SavedStateHandle,
    // V6-R3 FIX (#3): lets the chat self-heal after connectivity outages instead of
    // requiring exit/re-entry to see new messages.
    private val offlineManager: com.frerox.toolz.util.OfflineManager,
) : ViewModel() {

    val otherUserId: String = checkNotNull(savedStateHandle["otherUserId"])
    val myUserId: String get() = authManager.currentUserId ?: ""

    val screenshotBypassEnabled: StateFlow<Boolean> = settingsRepository.whisperScreenshotBypass
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setScreenshotBypass(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWhisperScreenshotBypass(enabled)
        }
    }

    private val _uiState = MutableStateFlow(WhisperChatUiState())
    val uiState: StateFlow<WhisperChatUiState> = _uiState.asStateFlow()

    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText.asStateFlow()

    private val _undoState = MutableStateFlow(WhisperUndoUiState())
    val undoState: StateFlow<WhisperUndoUiState> = _undoState.asStateFlow()

    private val _sessionExpired = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    private var partnerPublicKey: String? = null
    private var realtimeJob: Job? = null
    private var typingSubscriptionJob: Job? = null
    private var typingDebounceJob: Job? = null
    private var presenceJob: Job? = null
    private var undoTimerJob: Job? = null
    private val reactionSyncJobs = mutableMapOf<String, Job>()
    private var messagesCollectionJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var isCurrentlyTyping = false

    // V2-FIX (reviewwhisper.md) V-1: read receipts only fire while the chat is on screen.
    // Wired from the Screen's ON_START/ON_STOP; gates both the Room collector and the
    // realtime auto-mark-read path (see requestMarkPartnerRead).
    @Volatile private var isChatVisible = false

    // V2-FIX (reviewwhisper.md) V-2: optimistic sends live ONLY in uiState today, so any
    // Room re-emission used to make them vanish. They are mirrored here by clientId and
    // re-included in every merge until their server row shows up in Room (echo match) or
    // the send resolves.
    private val pendingMessagesById = java.util.concurrent.ConcurrentHashMap<String, WhisperMessage>()

    // V2-FIX (reviewwhisper.md) L: in-flight image downloads are deduped by message id —
    // recomposition used to fire parallel identical downloads for one bubble.
    private val inFlightImageLoads = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

    // V2-FIX (reviewwhisper.md) V-3: pagination state lives outside WhisperChatUiState —
    // the models file is outside this task's edit scope. Same semantics, separate flows.
    private val _isLoadingOlder = MutableStateFlow(false)
    val isLoadingOlder: StateFlow<Boolean> = _isLoadingOlder.asStateFlow()
    private val _reachedOldest = MutableStateFlow(false)
    val reachedOldest: StateFlow<Boolean> = _reachedOldest.asStateFlow()

    // H-4 FIX (reviewwhisper.md): the undo buffer is now WRITE-THROUGH persisted via
    // WhisperUndoBufferStore — a process death inside the 30-second window no longer
    // silently destroys the restore data while partner rows are tombstoned remotely.
    private val deletedMessagesUndoBuffer = mutableListOf<WhisperMessage>()
    // V2-FIX (reviewwhisper.md) V-12: all undo-buffer persistence is serialized through a
    // single Job; later requests cancel superseded ones so the terminal wipe always wins.
    private var undoSaveJob: Job? = null
    // V2-FIX (reviewwhisper.md) V-12: countdown restarts while a window is open are capped.
    private var undoCountdownExtensions = 0
    private var pendingIdCounter = 0L
    // Per-message in-flight reactions prevent double-tap races against the server.
    private val pendingReactions = mutableMapOf<String, MutableSet<String>>()

    // V2-FIX (reviewwhisper.md) V-15: draft persistence for process-death recovery.
    private var draftPersistJob: Job? = null

    companion object {
        // V2-FIX (reviewwhisper.md) V-5: 20 decoded images was ~2× what a chat session
        // needs resident; tightened to 8 with true LRU eviction (see imageCache below).
        private const val MAX_DECRYPTED_IMAGES = 8

        /** H-4: persisted buffers older than 2× the undo window are dropped on resume. */
        const val UNDO_RESUME_WINDOW_MS = 60_000L

        /** V2-FIX (reviewwhisper.md) V-12: max countdown restarts within one undo window. */
        private const val MAX_UNDO_EXTENSIONS = 5

        /** V2-FIX (reviewwhisper.md) L-17: max reaction-sync deferrals before forcing. */
        private const val MAX_REACTION_SYNC_DEFERRALS = 10

        /** V2-FIX (reviewwhisper.md) V-3: page size when fetching older history. */
        private const val OLDER_PAGE_SIZE = 50

        /** V2-FIX (reviewwhisper.md) V-15: SavedStateHandle key for the draft text. */
        private const val KEY_DRAFT = "whisper_chat_draft"
    }

    // V2-FIX (reviewwhisper.md) V-5: true LRU cache (accessOrder=true) guarded by a Mutex.
    // The old FIFO "drop the first key" eviction could evict the most-recently-viewed
    // image; access-order LinkedHashMap evicts the least-recently-used one. The uiState
    // map stays an immutable snapshot of this authoritative cache.
    private val imageCacheMutex = Mutex()
    private val decryptedImageCache = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>): Boolean =
            size > MAX_DECRYPTED_IMAGES
    }

    // H-9 FIX (reviewwhisper.md): read receipts are COALESCED. Every incoming message /
    // read-flip event used to fire markMessagesAsRead immediately; bursts produced one
    // REST write per event. A trailing 250 ms debounce collapses them into one write.
    private var markReadJob: Job? = null
    private fun requestMarkPartnerRead() {
        // V2-FIX (reviewwhisper.md) V-1: backgrounded chats must not burn read receipts —
        // the partner would see "read" for messages the user never looked at. This single
        // gate covers both the Room collector path and the realtime auto-mark path.
        if (!isChatVisible) return
        markReadJob?.cancel()
        markReadJob = viewModelScope.launch {
            delay(250)
            runCatching { repository.markMessagesAsRead(otherUserId) }
                .onFailure { WhisperErrorMapper.log(it, "markMessagesAsRead") }
            notificationManager.cancelMessageNotification(otherUserId)
        }
    }

    /** V2-FIX (reviewwhisper.md) V-1: wired from the Screen lifecycle (ON_START/ON_STOP). */
    fun onChatVisibilityChanged(visible: Boolean) {
        val wasVisible = isChatVisible
        isChatVisible = visible
        // Coming back to the foreground runs exactly one mark-read pass for anything
        // that arrived while the chat was hidden.
        if (visible && !wasVisible) requestMarkPartnerRead()
    }

    init {
        notificationManager.currentChatId = otherUserId
        notificationManager.cancelMessageNotification(otherUserId)
        // V2-FIX (reviewwhisper.md): unhideChat is suspend + IO commit now.
        viewModelScope.launch { hiddenChatsStore.unhideChat(otherUserId) }
        _uiState.update { it.copy(isMuted = mutePrefs.isMuted(otherUserId)) }
        // Keep the mute flag in sync when a timed mute expires while the chat is open.
        viewModelScope.launch {
            mutePrefs.mutedUsers.collect { muted ->
                _uiState.update { it.copy(isMuted = otherUserId in muted) }
            }
        }
        // V2-FIX (reviewwhisper.md) V-15: restore the draft after process death.
        _draftText.value = savedStateHandle.get<String>(KEY_DRAFT).orEmpty()
        // V6-R2 (review): the repository's receiveKeyChanged signal had ZERO collectors —
        // the "passive key-change banner" it was built for never existed. Collect it here
        // so an auto-accepted fresh rotation while the chat is open re-renders the trust
        // banner immediately instead of waiting for the next chat open.
        viewModelScope.launch {
            repository.receiveKeyChanged.collect { changedUserId ->
                if (changedUserId == otherUserId) loadKeyTrust()
            }
        }
        // V6-R3 FIX (#3): silent catch-up the moment connectivity returns after an
        // outage — visible history must never depend on the realtime socket alone.
        var wasOnline = true
        viewModelScope.launch {
            offlineManager.offlineState.collect { state ->
                val online = state == com.frerox.toolz.util.OfflineState.ONLINE
                if (online && !wasOnline) loadMessages()
                wasOnline = online
            }
        }
        restorePersistedUndoBuffer()
        loadInitialData()
        subscribeToChat()
        subscribeToTyping()
        subscribeToPresence()
        sendPresenceSignal(true)
    }

    /**
     * H-4: if the process died during an undo window, resume it — the persisted buffer
     * is adopted into memory and a fresh countdown starts. Stale buffers (older than
     * 2× the window) are dropped: the user has moved on and the data would be a surprise.
     */
    private fun restorePersistedUndoBuffer() {
        viewModelScope.launch {
            val savedAt = undoBufferStore.savedAtMs()
            val persisted = undoBufferStore.load()
            if (persisted.isEmpty()) return@launch
            if (savedAt == 0L || System.currentTimeMillis() - savedAt > UNDO_RESUME_WINDOW_MS) {
                undoBufferStore.clear()
                return@launch
            }
            deletedMessagesUndoBuffer.addAll(persisted)
            startUndoCountdown()
        }
    }

    private fun handleError(err: Throwable, context: String) {
        val mapped = WhisperErrorMapper.map(err, context)
        if (WhisperErrorMapper.isSessionExpired(err)) {
            // V2-FIX (reviewwhisper.md) L: stop every live subscription BEFORE signOut so
            // dying collectors can't fire more authenticated requests or UI events.
            cancelLiveJobs()
            viewModelScope.launch {
                authManager.signOut()
                _sessionExpired.emit(Unit)
            }
        } else {
            _uiState.update { it.copy(error = mapped) }
        }
    }

    /** V2-FIX (reviewwhisper.md) L: single teardown for all tracked live jobs. */
    private fun cancelLiveJobs() {
        realtimeJob?.cancel()
        typingSubscriptionJob?.cancel()
        typingDebounceJob?.cancel()
        presenceJob?.cancel()
        markReadJob?.cancel()
        searchDebounceJob?.cancel()
        reactionSyncJobs.values.forEach { it.cancel() }
        reactionSyncJobs.clear()
        messagesCollectionJob?.cancel()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Entrance lag fix: messages are the only thing that blocks the list —
            // start its Room collector + network sync immediately, before any
            // metadata RPC. The three metadata fetches then run in parallel on IO
            // so a slow profile/presence call never stalls the list.
            loadMessages()
            kotlinx.coroutines.coroutineScope {
                val profileDeferred = async(Dispatchers.IO) {
                    repository.getProfile(otherUserId, forceRefresh = true)
                }
                val friendshipDeferred = async(Dispatchers.IO) {
                    repository.getFriendshipStatus(otherUserId)
                }
                val blockDeferred = async(Dispatchers.IO) {
                    runCatching { repository.getBlockStatus(otherUserId) }
                }
                profileDeferred.await()
                    .onSuccess { profile ->
                        partnerPublicKey = profile.publicKey
                        _uiState.update { it.copy(otherUser = profile) }
                    }
                    .onFailure { err -> handleError(err, "getProfile") }

                friendshipDeferred.await()
                    .onSuccess { (status, friendship) ->
                        _uiState.update {
                            it.copy(
                                friendStatus = status,
                                iAmRequester = friendship?.iRequested(myUserId) ?: false,
                                isFriendStatusLoaded = true,
                            )
                        }
                    }
                    .onFailure { err ->
                        handleError(err, "getFriendshipStatus")
                        _uiState.update { it.copy(isFriendStatusLoaded = true) }
                    }

                blockDeferred.await()
                    .onSuccess { (blockedByMe, blockedByOther) ->
                        _uiState.update { it.copy(isBlockedByMe = blockedByMe, isBlockedByOther = blockedByOther) }
                    }
                    .onFailure { err -> handleError(err, "getBlockStatus") }
            }
            // Key trust depends on having seen the profile at least once but is
            // non-critical for first paint — load it after the parallel batch.
            loadKeyTrust()
        }
    }

    fun loadKeyTrust() {
        viewModelScope.launch {
            val info = repository.getKeyTrustInfo(otherUserId)
            _uiState.update { it.copy(keyTrust = info) }
        }
    }

    fun verifyKey() {
        viewModelScope.launch {
            // V2-FIX (reviewwhisper.md) H-4/V-4: failures used to vanish silently, leaving
            // the user thinking the key was verified when it wasn't.
            runCatching { repository.verifyUserKey(otherUserId) }
                .onSuccess { verified ->
                    if (!verified) {
                        _uiState.update {
                            it.copy(error = UiText.StringResource(R.string.st_Whisper_KeyVerify_Failed))
                        }
                    }
                    loadKeyTrust()
                }
                .onFailure { err -> handleError(err, "verifyUserKey") }
        }
    }

    fun acceptNewKey() {
        viewModelScope.launch {
            // V2-FIX (reviewwhisper.md) H-4/V-4: same silent-failure hole as verifyKey.
            runCatching { repository.acceptNewKey(otherUserId) }
                .onSuccess { accepted ->
                    if (!accepted) {
                        _uiState.update {
                            it.copy(error = UiText.StringResource(R.string.st_Whisper_KeyAccept_Failed))
                        }
                    }
                    loadKeyTrust()
                }
                .onFailure { err -> handleError(err, "acceptNewKey") }
        }
    }

    fun loadMessages() {
        // 1. Instant loading from Room cache (ciphertext is decrypted by the repository).
        // The live collector must be a SINGLE permanent subscription: re-invoking
        // loadMessages() from failure handlers / undo must never stack a second collector,
        // while the immediate server fetch below still runs on every call.
        if (messagesCollectionJob?.isActive != true) {
            messagesCollectionJob = viewModelScope.launch {
                repository.getMessagesFlow(otherUserId).collect { newMessages ->
                    // V2-FIX (reviewwhisper.md) H-2/V-2: retire optimistic pendings whose
                    // server echo just appeared in Room (same sender + identical content),
                    // BEFORE merging — the update lambda below stays side-effect free.
                    // Kept OUTSIDE _uiState.update because that lambda may be re-executed.
                    pendingMessagesById.entries.removeAll { (_, pendingMsg) ->
                        newMessages.any { roomRow ->
                            roomRow.senderId == pendingMsg.senderId &&
                                !roomRow.isPending &&
                                roomRow.content.trim() == pendingMsg.content.trim()
                        }
                    }
                    // Entrance lag fix: merging + sorting 500 rows off Main so the
                    // collector never blocks composition; only the final copy runs on Main.
                    val snapshot = _uiState.value
                    val searchQuery = snapshot.searchQuery
                    val existingMessages = snapshot.messages
                    val reactionInFlightSnapshot = pendingReactions.filterValues { it.isNotEmpty() }.keys.toSet()
                    val isReactionInFlight: (String) -> Boolean = { id -> id in reactionInFlightSnapshot }
                    val pendingValues = pendingMessagesById.values.toList()
                    val computed = withContext(Dispatchers.Default) {
                        val newIds = newMessages.mapTo(mutableSetOf()) { it.id }
                        val merged = ChatMessageMerger.mergeRoomEmission(
                            existing = existingMessages,
                            newMessages = newMessages,
                            isReactionToggleInFlight = isReactionInFlight,
                        )
                        val unresolvedPending =
                            pendingValues.filter { it.id !in newIds && !it.isDeletedForEveryone }
                        val sorted = sortedMessages(merged + unresolvedPending)
                        val matching = if (searchQuery.isNotBlank()) {
                            sorted.filter { !it.isDeletedForEveryone && it.content.contains(searchQuery, ignoreCase = true) }.map { it.id }.toSet()
                        } else emptySet()
                        sorted to matching
                    }
                    val (sorted, matchingIds) = computed
                    _uiState.update { state ->
                        state.copy(
                            messages = sorted,
                            isLoading = false,
                            matchingMessageIds = matchingIds,
                        )
                    }
                    if (newMessages.any { it.senderId == otherUserId && !it.isRead }) {
                        requestMarkPartnerRead()
                    }
                }
            }
        }

        // 2. Background sync with Supabase (runs on every call for the immediate path)
        viewModelScope.launch {
            repository.getMessages(otherUserId)
                .onFailure { err ->
                    handleError(err, "getMessagesSync")
                }
        }
    }

    /**
     * V2-FIX (reviewwhisper.md) H-3/V-3: fetch the previous history page using the oldest
     * loaded row as the beforeCreatedAt cursor. The repository already upserts fetched
     * rows into Room, so prepending happens through the existing live flow — no manual
     * list surgery here. Page exhaustion flips [reachedOldest].
     */
    fun loadOlderMessages() {
        if (_isLoadingOlder.value || _reachedOldest.value) return
        val oldestLoaded = uiState.value.messages
            .firstOrNull { !it.isPending && it.createdAt.isNotBlank() } ?: return
        _isLoadingOlder.value = true
        viewModelScope.launch {
            try {
                repository.getMessages(otherUserId, limit = OLDER_PAGE_SIZE, beforeCreatedAt = oldestLoaded.createdAt)
                    .onSuccess { page ->
                        if (page.size < OLDER_PAGE_SIZE) _reachedOldest.value = true
                    }
                    .onFailure { err -> handleError(err, "getMessagesPage") }
            } finally {
                _isLoadingOlder.value = false
            }
        }
    }

    /**
     * P2-7 FIX: Chronological order with pending pinned last.
     * P4a: implementation extracted to [ChatMessageMerger.sorted] (unit-tested);
     * this delegate keeps every existing call site untouched.
     */
    private fun sortedMessages(messages: List<WhisperMessage>): List<WhisperMessage> =
        ChatMessageMerger.sorted(messages)

    // ── REPLY-TO MESSAGE ──
    fun setReplyTarget(message: WhisperMessage) {
        _uiState.update { it.copy(replyingToMessage = message) }
    }

    fun clearReplyTarget() {
        _uiState.update { it.copy(replyingToMessage = null) }
    }

    // ── EMOJI REACTIONS ──
    fun toggleReaction(message: WhisperMessage, emoji: String) {
        if (message.id.isBlank() || message.isPending) return
        // Guard against double-taps: only one in-flight toggle per message/emoji.
        val inFlight = pendingReactions.getOrPut(message.id) { mutableSetOf() }
        if (!inFlight.add(emoji)) return
        // Optimistic local state update
        _uiState.update { state ->
            val updatedMsgs = state.messages.map { msg ->
                if (msg.id == message.id) {
                    val currentReactions = msg.reactions.toMutableList()
                    val existingIndex = currentReactions.indexOfFirst { it.emoji == emoji }
                    if (existingIndex >= 0) {
                        val existing = currentReactions[existingIndex]
                        if (existing.reactedByMe) {
                            if (existing.count <= 1) {
                                currentReactions.removeAt(existingIndex)
                            } else {
                                currentReactions[existingIndex] = existing.copy(
                                    count = existing.count - 1,
                                    reactedByMe = false,
                                    userIds = existing.userIds.filter { it != myUserId }
                                )
                            }
                        } else {
                            currentReactions[existingIndex] = existing.copy(
                                count = existing.count + 1,
                                reactedByMe = true,
                                userIds = existing.userIds + myUserId
                            )
                        }
                    } else {
                        currentReactions.add(
                            WhisperReactionSummary(
                                emoji = emoji,
                                count = 1,
                                userIds = listOf(myUserId),
                                reactedByMe = true
                            )
                        )
                    }
                    msg.copy(reactions = currentReactions)
                } else msg
            }
            state.copy(messages = updatedMsgs)
        }

        viewModelScope.launch {
            repository.toggleReaction(message.id, emoji, otherUserId = otherUserId)
                .onFailure { err ->
                    pendingReactions[message.id]?.remove(emoji)
                    handleError(err, "toggleReaction")
                    loadMessages()
                }
                .onSuccess {
                    pendingReactions[message.id]?.remove(emoji)
                    // Reflect the server-confirmed state once the optimistic update is no
                    // longer in flight (own ReactionEvent echoes are skipped by the UI).
                    scheduleReactionSync(message.id, delayMs = 600)
                }
        }
    }

    /**
     * Debounced authoritative reaction fetch. While any of MY pending toggles for a message
     * is still in flight, the server snapshot would clobber the optimistic state — defer
     * instead of overwriting, until the pending set drains.
     */
    private fun scheduleReactionSync(messageId: String, delayMs: Long = 400, deferrals: Int = 0) {
        reactionSyncJobs[messageId]?.cancel()
        reactionSyncJobs[messageId] = viewModelScope.launch {
            delay(delayMs)
            // V2-FIX (reviewwhisper.md) L-17: the deferral loop was unbounded — a stuck
            // in-flight toggle deferred forever. After 10 deferrals force one sync.
            val forceAfterCap = deferrals >= MAX_REACTION_SYNC_DEFERRALS
            if (pendingReactions[messageId].isNullOrEmpty() || forceAfterCap) {
                val reactionMap = repository.getReactionsForMessages(listOf(messageId)).getOrNull()
                if (reactionMap != null) {
                    val updatedList = reactionMap[messageId] ?: emptyList()
                    _uiState.update { state ->
                        val updated = state.messages.map { msg ->
                            if (msg.id == messageId) msg.copy(reactions = updatedList) else msg
                        }
                        state.copy(messages = updated)
                    }
                }
            } else {
                // My optimistic toggle is still in flight — re-schedule instead of clobbering.
                scheduleReactionSync(messageId, delayMs = 800, deferrals = deferrals + 1)
            }
        }
    }

    // ── IN-CHAT MESSAGE SEARCH ──
    fun toggleSearch(active: Boolean? = null) {
        _uiState.update { state ->
            val newActive = active ?: !state.isSearchActive
            state.copy(
                isSearchActive = newActive,
                searchQuery = if (newActive) state.searchQuery else "",
                matchingMessageIds = if (newActive) state.matchingMessageIds else emptySet(),
                activeSearchMatchIndex = if (newActive) state.activeSearchMatchIndex else -1
            )
        }
    }

    fun updateSearchQuery(query: String) {
        // The text field updates immediately; the O(n) match scan is debounced so typing
        // never runs a full-list scan per keystroke on the main thread.
        _uiState.update { it.copy(searchQuery = query) }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(250)
            _uiState.update { state ->
                val matchedIds = if (state.searchQuery.isBlank()) {
                    emptySet()
                } else {
                    state.messages
                        .filter { !it.isDeletedForEveryone && it.content.contains(state.searchQuery, ignoreCase = true) }
                        .map { it.id }
                        .toSet()
                }
                state.copy(
                    matchingMessageIds = matchedIds,
                    activeSearchMatchIndex = if (matchedIds.isNotEmpty()) 0 else -1
                )
            }
        }
    }

    fun navigateSearchMatch(direction: Int) {
        _uiState.update { state ->
            val size = state.matchingMessageIds.size
            if (size == 0) return@update state.copy(activeSearchMatchIndex = -1)
            val next = (state.activeSearchMatchIndex + direction).mod(size)
            state.copy(activeSearchMatchIndex = next)
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        if (uiState.value.isBlockedByOther) {
            _uiState.update { it.copy(error = UiText.StringResource(R.string.st_Whisper_Chat_BlockedByOther)) }
            return
        }
        if (uiState.value.isBlockedByMe) {
            _uiState.update { it.copy(error = UiText.StringResource(R.string.st_Whisper_Chat_InputUnblock)) }
            return
        }

        val originalText = content
        val replyTarget = uiState.value.replyingToMessage
        _draftText.value = ""
        clearReplyTarget()
        // V2-FIX (reviewwhisper.md) M-6/V-9: sending ends typing — cancel the debounce so
        // it can't flip the typing flag back on after the message went out.
        typingDebounceJob?.cancel()
        isCurrentlyTyping = false
        sendTypingSignal(false)
        val trimmedContent = content.trim()
        val replySnippet = replyTarget?.let { target ->
            // Image targets use the attachment prefix marker so the UI can detect them
            // model-robustly (normalized back to display labels at render time).
            if (target.content.startsWith("whisper:image:")) {
                WhisperImageAttachment.MESSAGE_PREFIX
            } else {
                target.content.take(100)
            }
        }

        val optimisticMsg = WhisperMessage(
            id = "pending_${System.currentTimeMillis()}_${pendingIdCounter++}",
            senderId = myUserId,
            receiverId = otherUserId,
            content = trimmedContent,
            replyToId = replyTarget?.id,
            replyToContent = replySnippet,
            // V2-FIX (reviewwhisper.md) M-5/V-8: never persist display names ("You"/"User").
            // The quoted sender is resolved from the id at render time; the VM stores ids only.
            replyToSenderName = null,
            isPending = true,
            createdAt = java.time.Instant.now().toString()
        )
        // V2-FIX (reviewwhisper.md) H-2/V-2: register the optimistic row so Room
        // re-emissions can't make it vanish mid-flight.
        pendingMessagesById[optimisticMsg.id] = optimisticMsg

        _uiState.update { state ->
            state.copy(messages = sortedMessages(state.messages + optimisticMsg))
        }

        viewModelScope.launch {
            repository.sendMessage(otherUserId, trimmedContent, replyTarget?.id)
                .onSuccess { newMsg ->
                    pendingMessagesById.remove(optimisticMsg.id)
                    _uiState.update { state ->
                        val filtered = state.messages.filter { it.id != optimisticMsg.id && it.id != newMsg.id }
                        // V2-FIX (reviewwhisper.md) M-5/V-8: id-only enrichment — the quoted
                        // sender name is resolved at render time from the stored ids.
                        val enrichedMsg = newMsg.copy(
                            replyToContent = replySnippet,
                            replyToSenderName = null
                        )
                        state.copy(messages = sortedMessages(filtered + enrichedMsg))
                    }
                }
                .onFailure { err ->
                    pendingMessagesById.remove(optimisticMsg.id)
                    // Only restore the draft if the user hasn't already typed something
                    // newer; the reply target likewise survives only if still unset.
                    if (_draftText.value.isBlank()) _draftText.value = originalText
                    _uiState.update { state ->
                        val filtered = state.messages.filter { it.id != optimisticMsg.id }
                        state.copy(
                            messages = filtered,
                            replyingToMessage = if (state.replyingToMessage == null) replyTarget else state.replyingToMessage
                        )
                    }
                    handleError(err, "sendMessage")
                }
        }
    }

    fun sendImage(imageBytes: ByteArray, mimeType: String, expiresAfterSeconds: Long?) {
        if (uiState.value.isBlockedByMe || uiState.value.isBlockedByOther) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAttachment = true) }
            repository.sendEncryptedImage(otherUserId, imageBytes, mimeType, expiresAfterSeconds)
                .onSuccess { message ->
                    _uiState.update { state ->
                        state.copy(
                            messages = sortedMessages(state.messages.filterNot { it.id == message.id } + message),
                            isUploadingAttachment = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isUploadingAttachment = false) }
                    handleError(error, "sendEncryptedImage")
                }
        }
    }

    /** Reads + compresses a picked image and sends it, all in viewModelScope so leaving the
     *  screen mid-upload no longer silently cancels the send (unlike a composition scope). */
    fun sendImageFromUri(context: Context, uri: android.net.Uri, expiresAfterSeconds: Long?) {
        viewModelScope.launch {
            // Bounded read off the main thread; the spinner stays up until sendImage resolves.
            // V2-FIX (reviewwhisper.md) M-9/V-10: readBoundedImageBytes' size cap throws
            // IllegalArgumentException with a specific user-facing message — the old
            // runCatching collapsed it into the generic "couldn't read" text.
            var oversizeMessage: UiText? = null
            val bytes = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { readBoundedImageBytes(it, context) }
                } catch (e: IllegalArgumentException) {
                    oversizeMessage = e.message?.let { UiText.DynamicString(it) }
                    null
                }
            }
            if (bytes == null) {
                _uiState.update {
                    it.copy(error = oversizeMessage ?: UiText.StringResource(R.string.st_Whisper_Error_ReadImage))
                }
                return@launch
            }
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            // Compress before encrypt to stay within the edge-function body limit.
            // V2-FIX (reviewwhisper.md) M-10/V-14: the chosen format is returned explicitly —
            // alpha-preserving PNG output is no longer mislabeled (and re-encoded) as JPEG.
            val (compressed, outMime) = compressImageForUpload(bytes, mimeType)
            sendImage(compressed, outMime, expiresAfterSeconds)
        }
    }

    fun loadEncryptedImage(message: WhisperMessage) {
        val attachment = WhisperImageAttachment.fromMessageContent(message.content) ?: return
        // Never cache bytes for a disappearing image that has already expired.
        if (attachment.expiresAtEpochSeconds != null &&
            java.time.Instant.now().epochSecond >= attachment.expiresAtEpochSeconds
        ) {
            viewModelScope.launch {
                imageCacheMutex.withLock { decryptedImageCache.remove(message.id) }
                _uiState.update { it.copy(decryptedImageBytes = it.decryptedImageBytes - message.id) }
            }
            return
        }
        // V2-FIX (reviewwhisper.md) L-19: dedupe concurrent downloads of the same message.
        if (!inFlightImageLoads.add(message.id)) return
        viewModelScope.launch {
            try {
                // V2-FIX (reviewwhisper.md) V-5: a cache hit also REFRESHES LRU recency.
                val cached = imageCacheMutex.withLock { decryptedImageCache[message.id] }
                if (cached != null) return@launch
                // Ensure we have a public key for decryption. If it's a message from 'me',
                // we need the receiver's key (since we encrypted it for them).
                // If it's from someone else, we need the sender's key.
                val peerId = if (message.senderId == myUserId) message.receiverId else message.senderId

                val key = partnerPublicKey.takeIf { peerId == otherUserId }
                    ?: repository.getProfile(peerId).getOrNull()?.publicKey
                    // Fall back to the key this device last accepted for that peer so
                    // cached ciphertext stays decryptable after a process restart.
                    ?: repository.getDecryptionKey(peerId)

                if (key != null && peerId == otherUserId) {
                    partnerPublicKey = key
                }

                // V3-FIX (task F): disk-cache tier. The entry is bound to the fingerprint
                // of the key the bytes decrypt under, so a partner key change can never
                // serve stale ciphertext under this conversation's name. Any fault here
                // is a silent miss (the cache logs it once) and falls through to download.
                val peerKeyFp = key?.let { crypto.fingerprint(it) }
                if (peerKeyFp != null) {
                    val fromDisk = imageDiskCache.get(
                        messageId = message.id,
                        keyFp = peerKeyFp,
                        expiresAtEpochMs = attachment.expiresAtEpochSeconds?.times(1000),
                    )
                    if (fromDisk != null) {
                        val snapshot = imageCacheMutex.withLock {
                            decryptedImageCache[message.id] = fromDisk
                            decryptedImageCache.toMap()
                        }
                        _uiState.update { it.copy(decryptedImageBytes = snapshot) }
                        return@launch
                    }
                }

                // V3-FIX (scoped legacy-AAD retirement): pass the message's creation time so
                // the constant-AAD legacy retry only applies to pre-cutoff images.
                repository.downloadEncryptedImage(
                    attachment,
                    peerId,
                    key,
                    WhisperMessageEntity.parseSortEpoch(message.createdAt),
                )
                    .onSuccess { bytes ->
                        // V3-FIX (task F): persist the decrypted bytes as Keystore-encrypted
                        // ciphertext BEFORE the memory insert; best-effort (never throws).
                        // Only the encrypted blob lands on disk — never plaintext.
                        peerKeyFp?.let { fp -> imageDiskCache.put(message.id, fp, bytes) }
                        // V2-FIX (reviewwhisper.md) V-5: insert into the true-LRU cache under
                        // the mutex; eviction (eldest by ACCESS order) happens automatically.
                        val snapshot = imageCacheMutex.withLock {
                            decryptedImageCache[message.id] = bytes
                            decryptedImageCache.toMap()
                        }
                        _uiState.update { it.copy(decryptedImageBytes = snapshot) }
                    }
                    .onFailure { error -> handleError(error, "downloadEncryptedImage") }
            } finally {
                inFlightImageLoads.remove(message.id)
            }
        }
    }


    // ── MESSAGE DELETION ──
    fun deleteMessageForEveryone(message: WhisperMessage) {
        // Optimistic local update. WhisperMessage.isDeletedForEveryone is a computed getter
        // derived from content, so mirroring the server's exact tombstone text both marks the
        // message deleted and keeps local and remote state identical until the next reload.
        // H-5 FIX (reviewwhisper.md): use the SINGLE shared constant — the old hardcoded
        // literal could drift from WhisperTombstone.DISPLAY_TEXT silently.
        val tombstone = WhisperTombstone.DISPLAY_TEXT
        _uiState.update { state ->
            val updated = state.messages.map {
                if (it.id == message.id) it.copy(content = tombstone, contentIv = null) else it
            }
            state.copy(messages = updated)
        }

        viewModelScope.launch {
            repository.deleteMessageForEveryone(message.id, otherUserId)
                .onFailure { err ->
                    handleError(err, "deleteMessage")
                    loadMessages()
                }
        }
    }

    fun deleteMessageForMe(message: WhisperMessage) {
        _uiState.update { state ->
            state.copy(messages = state.messages.filter { it.id != message.id })
        }

        viewModelScope.launch {
            repository.deleteMessageForMe(message.id)
                .onFailure { loadMessages() }
        }
    }

    fun sendFriendRequest() {
        viewModelScope.launch {
            repository.sendFriendRequest(otherUserId)
                .onSuccess {
                    repository.getFriendshipStatus(otherUserId).onSuccess { (status, friendship) ->
                        _uiState.update {
                            it.copy(
                                friendStatus = status,
                                iAmRequester = friendship?.iRequested(myUserId) ?: false,
                                isFriendStatusLoaded = true
                            )
                        }
                    }
                }
                .onFailure { handleError(it, "sendFriendRequest") }
        }
    }

    fun acceptFriendRequest() {
        viewModelScope.launch {
            repository.getFriendshipStatus(otherUserId)
                .onSuccess { (_, friendship) ->
                    val recordId = friendship?.id ?: run {
                        handleError(Exception("No friendship record found"), "acceptFriendRequest")
                        return@launch
                    }
                    repository.acceptFriendRequest(recordId)
                        .onSuccess {
                            _uiState.update { it.copy(friendStatus = FriendStatus.ACCEPTED, iAmRequester = false) }
                        }
                        .onFailure { handleError(it, "acceptFriendRequest") }
                }
                .onFailure { handleError(it, "acceptFriendRequest") }
        }
    }

    // ── CLEAR CHAT WITH 30s LIVE COUNTDOWN UNDO ──
    fun clearChat(range: ClearChatTimeRange, customStartIso: String? = null, customEndIso: String? = null) {
        viewModelScope.launch {
            val now = Instant.now()
            val (fromIso, toIso) = when (range) {
                ClearChatTimeRange.PAST_24_HOURS -> Pair(now.minus(24, ChronoUnit.HOURS).toString(), now.toString())
                ClearChatTimeRange.PAST_7_DAYS   -> Pair(now.minus(7, ChronoUnit.DAYS).toString(), now.toString())
                ClearChatTimeRange.PAST_30_DAYS  -> Pair(now.minus(30, ChronoUnit.DAYS).toString(), now.toString())
                ClearChatTimeRange.ALL_TIME      -> Pair(null, null)
                ClearChatTimeRange.CUSTOM        -> Pair(customStartIso, customEndIso)
            }

            repository.clearMessagesForRange(otherUserId, fromIso, toIso)
                .onSuccess { deletedList ->
                    // Accumulate: a second clear while the first undo window is open must
                    // not destroy the earlier batch — undo restores everything together.
                    deletedMessagesUndoBuffer.addAll(deletedList)
                    // H-4: durable write-through so process death cannot eat the buffer.
                    // V2-FIX (reviewwhisper.md) V-12: routed through the single serialized
                    // save channel (was an untracked fire-and-forget launch racing the wipe).
                    persistUndoBuffer(deletedMessagesUndoBuffer.toList())
                    _uiState.update { state ->
                        val deletedIds = deletedList.map { it.id }.toSet()
                        state.copy(messages = state.messages.filter { it.id !in deletedIds })
                    }
                    startUndoCountdown()
                }
                .onFailure { err ->
                    handleError(err, "clearChat")
                }
        }
    }

    /**
     * V2-FIX (reviewwhisper.md) V-12: ONE serialized persistence channel for the undo
     * buffer. Each request cancels the previous save, so an older snapshot can never land
     * AFTER the terminal wipe and resurrect restore data for already-discarded messages.
     */
    private fun persistUndoBuffer(entries: List<WhisperMessage>) {
        undoSaveJob?.cancel()
        undoSaveJob = appScope.launch {
            runCatching { undoBufferStore.save(entries) }
                .onFailure { WhisperErrorMapper.log(it, "undoBufferSave") }
        }
    }

    /**
     * Shared 30s countdown; expires by discarding the persisted + in-memory buffers.
     * V2-FIX (reviewwhisper.md) V-12: restarts while a window is open are capped at
     * [MAX_UNDO_EXTENSIONS] — past the cap the window finalizes immediately instead of
     * being extended indefinitely by repeated clears.
     */
    private fun startUndoCountdown() {
        val extendingWindow = undoTimerJob?.isActive == true || _undoState.value.secondsRemaining > 0
        if (extendingWindow) {
            if (undoCountdownExtensions >= MAX_UNDO_EXTENSIONS) {
                finalizeUndoWindow(cancelTimer = true)
                return
            }
            undoCountdownExtensions++
        } else {
            undoCountdownExtensions = 0
        }
        _undoState.value = WhisperUndoUiState(clearedCount = deletedMessagesUndoBuffer.size, secondsRemaining = 30)
        undoTimerJob?.cancel()
        undoTimerJob = viewModelScope.launch {
            for (sec in 30 downTo 1) {
                _undoState.update { it.copy(secondsRemaining = sec) }
                delay(1_000)
            }
            finalizeUndoWindow()
        }
    }

    /** V2-FIX (reviewwhisper.md) V-12: terminal wipe — always wins over pending saves. */
    private fun finalizeUndoWindow(cancelTimer: Boolean = false) {
        if (cancelTimer) undoTimerJob?.cancel()
        deletedMessagesUndoBuffer.clear()
        persistUndoBuffer(emptyList())
        _undoState.value = WhisperUndoUiState()
        undoCountdownExtensions = 0
    }

    fun undoClearChat() {
        if (deletedMessagesUndoBuffer.isEmpty()) return
        val toRestore = deletedMessagesUndoBuffer.toList()
        undoTimerJob?.cancel()
        _undoState.value = WhisperUndoUiState()

        viewModelScope.launch {
            repository.restoreMessages(toRestore)
                .onSuccess {
                    deletedMessagesUndoBuffer.clear()
                    undoCountdownExtensions = 0
                    undoBufferStore.clear()
                    loadMessages()
                }
                .onFailure { err ->
                    handleError(err, "restoreMessages")
                    // Keep the undo bar alive so the user can retry within the window;
                    // H-4: re-persist the (unchanged) buffer for the restarted countdown.
                    startUndoCountdown()
                }
        }
    }

    // ── MUTE / UNMUTE ──
    fun toggleMute(durationMs: Long = Long.MAX_VALUE) {
        val currentlyMuted = uiState.value.isMuted
        if (currentlyMuted) {
            mutePrefs.unmuteUser(otherUserId)
            _uiState.update { it.copy(isMuted = false) }
        } else {
            val until = if (durationMs == Long.MAX_VALUE) Long.MAX_VALUE else System.currentTimeMillis() + durationMs
            mutePrefs.muteUser(otherUserId, until)
            _uiState.update { it.copy(isMuted = true) }
        }
    }

    // ── BLOCK / UNBLOCK ──
    fun toggleBlock() {
        val isBlocked = uiState.value.isBlockedByMe
        viewModelScope.launch {
            if (isBlocked) {
                repository.unblockUser(otherUserId)
                    .onSuccess {
                        _uiState.update { it.copy(isBlockedByMe = false) }
                    }
                    .onFailure { err ->
                        handleError(err, "unblockUser")
                    }
            } else {
                repository.blockUser(otherUserId)
                    .onSuccess {
                        _uiState.update { it.copy(isBlockedByMe = true) }
                    }
                    .onFailure { err ->
                        handleError(err, "blockUser")
                    }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun updateDraft(text: String) {
        _draftText.value = text
        // V2-FIX (reviewwhisper.md) V-15: debounce-persist the draft so a process death
        // mid-typing doesn't lose it; restored in init from the same SavedStateHandle.
        draftPersistJob?.cancel()
        draftPersistJob = viewModelScope.launch {
            delay(300)
            savedStateHandle[KEY_DRAFT] = text
        }
        if (text.isNotBlank()) {
            if (!isCurrentlyTyping) {
                isCurrentlyTyping = true
                sendTypingSignal(true)
            }
            typingDebounceJob?.cancel()
            typingDebounceJob = viewModelScope.launch {
                delay(2500)
                isCurrentlyTyping = false
                sendTypingSignal(false)
            }
        } else {
            if (isCurrentlyTyping) {
                isCurrentlyTyping = false
                sendTypingSignal(false)
            }
            typingDebounceJob?.cancel()
        }
    }

    private fun sendTypingSignal(isTyping: Boolean) {
        viewModelScope.launch {
            repository.sendTypingStatus(otherUserId, isTyping)
        }
    }

    private fun sendPresenceSignal(isOnline: Boolean) {
        viewModelScope.launch {
            // V6-R6 (#presence): repository.sendPresence is an intentional no-op
            // (broadcast lane removed). Entering a chat must STAMP last_seen so the
            // partner sees "online" immediately, not up to a minute later.
            if (isOnline) runCatching { repository.updateLastSeen() }
        }
    }

    private fun subscribeToPresence() {
        presenceJob = viewModelScope.launch {
            // Transient subscription failures (offline blips, channel teardown) must not
            // kill presence permanently: re-subscribe every 3s, capped at 10 consecutive
            // failures. Cancellation is always rethrown.
            var retries = 0
            while (isActive) {
                try {
                    repository.subscribeToPresence(otherUserId).collect { (isOnline, ts) ->
                        _uiState.update { it.copy(isPartnerOnline = isOnline, partnerLastSeen = ts) }
                    }
                    break
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    retries++
                    if (retries > 10) {
                        android.util.Log.w("WhisperChatVM", "Presence subscription gave up after $retries failures")
                        break
                    }
                    delay(3000)
                }
            }
        }
    }

    private fun subscribeToChat() {
        val myId = myUserId
        if (myId.isEmpty()) return

        realtimeJob = viewModelScope.launch {
            // Consecutive-failure cap prevents an endless retry loop after e.g. a revoked
            // session or a permanently broken channel; each successful event resets it.
            var consecutiveFailures = 0
            repository.subscribeToChat(otherUserId)
                .retry { cause ->
                    if (authManager.currentUserId == null) {
                        // Session is gone: the screen will navigate back; retrying would
                        // just spam failed auth requests forever.
                        android.util.Log.w("WhisperChatVM", "Realtime subscription stopped: session expired (${cause.message})")
                        false
                    } else if (consecutiveFailures >= 10) {
                        android.util.Log.w("WhisperChatVM", "Realtime subscription gave up after 10 consecutive failures: ${cause.message}")
                        _uiState.update { it.copy(isRealtimeDisconnected = true) }
                        false
                    } else {
                        consecutiveFailures++
                        android.util.Log.e("WhisperVM", "Realtime subscription error: ${cause.message}. Retrying in 3s...")
                        delay(3000)
                        true // Retry while the screen is active (capped above)
                    }
                }
                .collect { event ->
                    consecutiveFailures = 0
                    if (_uiState.value.isRealtimeDisconnected) {
                        _uiState.update { it.copy(isRealtimeDisconnected = false) }
                    }
                    when (event) {
                        is WhisperChatEvent.MessageEvent -> {
                            val newMsg = event.message
                            _uiState.update { state ->
                                val existingIndex = state.messages.indexOfFirst { it.id == newMsg.id }
                                if (existingIndex >= 0) {
                                    val mutableList = state.messages.toMutableList()
                                    val current = mutableList[existingIndex]
                                    mutableList[existingIndex] = current.copy(
                                        content = newMsg.content,
                                        contentIv = newMsg.contentIv,
                                        reactions = if (newMsg.reactions.isNotEmpty()) newMsg.reactions else current.reactions,
                                        isRead = newMsg.isRead || current.isRead
                                    )
                                    state.copy(messages = mutableList)
                                } else {
                                    // Enrich reply metadata for live message
                                    // V2-FIX (reviewwhisper.md) M-5/V-8: ids only — the quoted
                                    // sender name is resolved at render time on the Screen.
                                    val enrichedMsg = if (newMsg.replyToId != null && (newMsg.replyToContent == null || newMsg.replyToContent.startsWith("whisper:image:"))) {
                                        val replyTarget = state.messages.find { it.id == newMsg.replyToId }
                                        if (replyTarget != null) {
                                            // Image targets use the attachment prefix marker so the UI can
                                            // detect them model-robustly (rendered as a localized label).
                                            val content = if (replyTarget.content.startsWith("whisper:image:")) {
                                                WhisperImageAttachment.MESSAGE_PREFIX
                                            } else {
                                                replyTarget.content.take(100)
                                            }
                                            newMsg.copy(
                                                replyToContent = content,
                                                replyToSenderName = null
                                            )
                                        } else newMsg
                                    } else newMsg

                                    // Deduplicate: remove pending messages that likely match this incoming one
                                    // Use a stricter match or just let Room handle the cleanup if repository persists it.
                                    // Repository DOES persist it, so Room will eventually emit the cleaned list.
                                    // We add it here for ultra-low-latency UI updates.
                                    val filtered = state.messages.toMutableList()
                                    if (enrichedMsg.senderId == myUserId) {
                                        // Remove only the FIRST pending message with identical content:
                                        // two identical pending sends must not both be dropped by one echo.
                                        val echoIndex = filtered.indexOfFirst {
                                            it.id.startsWith("pending_") && it.content.trim() == enrichedMsg.content.trim()
                                        }
                                        if (echoIndex >= 0) {
                                            // V2-FIX (reviewwhisper.md) V-2: retire the optimistic
                                            // mirror too, so the Room merge won't resurrect it.
                                            pendingMessagesById.remove(filtered[echoIndex].id)
                                            filtered.removeAt(echoIndex)
                                        } else {
                                            filtered.removeAll { it.id == enrichedMsg.id }
                                        }
                                    } else {
                                        filtered.removeAll { it.id == enrichedMsg.id }
                                    }
                                    state.copy(messages = sortedMessages(filtered + enrichedMsg))
                                }
                            }
                            if (newMsg.senderId == otherUserId) {
                                requestMarkPartnerRead()
                            }
                        }
                        is WhisperChatEvent.ReactionSnapshotEvent -> {
                            // V6-R4: authoritative state from the polling lane. Never
                            // clobber messages with in-flight local toggles; those
                            // reconcile via scheduleReactionSync after the write lands.
                            if (pendingReactions[event.messageId].isNullOrEmpty()) {
                                _uiState.update { state ->
                                    val updated = state.messages.map { msg ->
                                        if (msg.id == event.messageId) {
                                            if (msg.reactions == event.summaries) msg else msg.copy(reactions = event.summaries)
                                        } else msg
                                    }
                                    state.copy(messages = updated)
                                }
                                // V6-R7 (#cache): persist so re-entry renders instantly.
                                viewModelScope.launch {
                                    repository.cacheReactions(event.messageId, event.summaries)
                                }
                            }
                        }
                        is WhisperChatEvent.ReactionEvent -> {
                            // Skip echoes of my own toggles: the optimistic UI update already
                            // applied this change, and re-applying would double-flip it.
                            if (event.userId != myUserId) {
                                _uiState.update { state ->
                                    val updated = state.messages.map { msg ->
                                        if (msg.id == event.messageId) {
                                            val curReactions = msg.reactions.toMutableList()
                                            val idx = curReactions.indexOfFirst { it.emoji == event.emoji }
                                            if (idx >= 0) {
                                                val existing = curReactions[idx]
                                                val containsUser = existing.userIds.contains(event.userId)
                                                if (containsUser) {
                                                    val newUserIds = existing.userIds.filter { it != event.userId }
                                                    if (newUserIds.isEmpty()) {
                                                        curReactions.removeAt(idx)
                                                    } else {
                                                        curReactions[idx] = existing.copy(
                                                            count = newUserIds.size,
                                                            userIds = newUserIds,
                                                            reactedByMe = if (event.userId == myUserId) false else existing.reactedByMe
                                                        )
                                                    }
                                                } else {
                                                    val newUserIds = existing.userIds + event.userId
                                                    curReactions[idx] = existing.copy(
                                                        count = newUserIds.size,
                                                        userIds = newUserIds,
                                                        reactedByMe = if (event.userId == myUserId) true else existing.reactedByMe
                                                    )
                                                }
                                            } else {
                                                curReactions.add(
                                                    WhisperReactionSummary(
                                                        emoji = event.emoji,
                                                        count = 1,
                                                        userIds = listOf(event.userId),
                                                        reactedByMe = event.userId == myUserId
                                                    )
                                                )
                                            }
                                            msg.copy(reactions = curReactions)
                                        } else msg
                                    }
                                    state.copy(messages = updated)
                                }
                            }
                            // V6-R7 (#cache): persist so re-entry renders instantly.
                            viewModelScope.launch {
                                _uiState.value.messages.firstOrNull { it.id == event.messageId }
                                    ?.let { repository.cacheReactions(it.id, it.reactions) }
                            }
                            // 2. Authoritative sync with DB (debounced: bursts of reactions
                            // only trigger one round-trip). Messages with in-flight pending
                            // toggles are deferred, never overwritten (see scheduleReactionSync).
                            scheduleReactionSync(event.messageId)
                        }
                        is WhisperChatEvent.DeleteEvent -> {
                            // V2-FIX (reviewwhisper.md) M-11/V-13: a remote delete used to remove
                            // the row outright, which reads like the message never existed (and
                            // desynced from Room, which still holds the row until re-sync). Mirror
                            // the LOCAL delete-for-everyone rendering: same tombstone model.
                            _uiState.update { state ->
                                val updated = state.messages.map { msg ->
                                    if (msg.id == event.messageId && !msg.isDeletedForEveryone) {
                                        msg.copy(content = WhisperTombstone.DISPLAY_TEXT, contentIv = null)
                                    } else msg
                                }
                                state.copy(messages = updated)
                            }
                        }
                    }
                }
        }
    }

    /** Reconnects all realtime listeners after a network failure or retry cap. */
    fun reconnectRealtime() {
        _uiState.update { it.copy(isRealtimeDisconnected = false) }
        // V2-FIX (reviewwhisper.md) M-2/V-6: the old subscriptions were never cancelled, so
        // every reconnect stacked ANOTHER collector per channel — duplicated UI events and
        // leaked jobs. Cancel the old jobs before respawning their replacements.
        realtimeJob?.cancel()
        typingSubscriptionJob?.cancel()
        presenceJob?.cancel()
        subscribeToChat()
        subscribeToTyping()
        subscribeToPresence()
    }

    private fun subscribeToTyping() {
        typingSubscriptionJob = viewModelScope.launch {
            // Transient subscription failures must not kill typing status permanently:
            // re-subscribe every 3s, capped at 10 consecutive failures. Cancellation is
            // always rethrown.
            var retries = 0
            while (isActive) {
                try {
                    repository.subscribeToTypingStatus(otherUserId).collect { isTyping ->
                        _uiState.update { it.copy(isPartnerTyping = isTyping) }
                    }
                    break
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    retries++
                    if (retries > 10) {
                        android.util.Log.w("WhisperChatVM", "Typing subscription gave up after $retries failures")
                        break
                    }
                    delay(3000)
                }
            }
        }
    }

    // V2-FIX (reviewwhisper.md) L-16: onScrolledUp/onScrolledToBottom removed — they
    // maintained unreadMessagesScrolledUp but nothing ever called them (dead state).

    override fun onCleared() {
        super.onCleared()
        notificationManager.currentChatId = null
        // M-10 FIX (reviewwhisper.md): viewModelScope is cancelled the moment onCleared()
        // starts, so presence-off fires on the injected application scope instead of the
        // old ad-hoc CoroutineScope(NonCancellable) anti-pattern (uncancellable,
        // unsupervised, uninjectable in tests).
        appScope.launch {
            runCatching { repository.sendPresence(otherUserId, false) }
                .onFailure { android.util.Log.w("WhisperChatVM", "presence-off signal failed", it) }
        }
        // V2-FIX (reviewwhisper.md) L-20: shared teardown for all tracked live jobs.
        cancelLiveJobs()
        undoTimerJob?.cancel()
        draftPersistJob?.cancel()
        undoSaveJob?.cancel()
        // V2-FIX (reviewwhisper.md) V-15: flush the debounced draft synchronously — the
        // debounced write would otherwise be cancelled with viewModelScope.
        savedStateHandle[KEY_DRAFT] = _draftText.value
    }
}

private const val MAX_LOCAL_IMAGE_BYTES = 5 * 1024 * 1024 - 16 // transport cap minus AES-GCM tag

/** V2-FIX (reviewwhisper.md) V-14: source formats that can carry transparency. */
private val ALPHA_CAPABLE_MIMES = setOf("image/png", "image/webp")

private fun readBoundedImageBytes(input: java.io.InputStream, context: Context): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        require(total <= MAX_LOCAL_IMAGE_BYTES) { context.getString(R.string.st_Whisper_Error_ImageTooLarge) }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

/**
 * V2-FIX (reviewwhisper.md) V-14: returns the upload payload plus its ACTUAL mime —
 * sources with alpha (PNG/WebP detectable via bitmap.hasAlpha()) are re-encoded as PNG
 * instead of being flattened into JPEG, which composites transparency onto black.
 */
private suspend fun compressImageForUpload(bytes: ByteArray, mimeType: String): Pair<ByteArray, String> =
    withContext(Dispatchers.Default) {
        runCatching {
            // P1-10 FIX: Handle EXIF rotation so portrait photos don't upload sideways.
            var bitmap = decodeBoundedBitmap(bytes, 1920, 1920) ?: return@withContext bytes to mimeType
            // Apply EXIF orientation before scaling — use original bytes' EXIF (JPEG).
            try {
                val exif = androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
                val orientation = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
                val matrix = android.graphics.Matrix()
                when (orientation) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    else -> {}
                }
                if (!matrix.isIdentity) {
                    val rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (rotated != bitmap) { bitmap.recycle(); bitmap = rotated }
                }
            } catch (_: Exception) {}
            val maxDimension = 1920
            val width = bitmap.width
            val height = bitmap.height
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = min(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
                val newWidth = (width * ratio).roundToInt().coerceAtLeast(1)
                val newHeight = (height * ratio).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }
            // V2-FIX (reviewwhisper.md) V-14: keep transparency for alpha-capable sources.
            val keepAlpha = mimeType in ALPHA_CAPABLE_MIMES && scaledBitmap.hasAlpha()
            val format = if (keepAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val outMime = if (keepAlpha) "image/png" else "image/jpeg"
            val out = ByteArrayOutputStream()
            scaledBitmap.compress(format, 82, out)
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()
            val result = out.toByteArray()
            if (result.isNotEmpty() && result.size < bytes.size) result to outMime else bytes to mimeType
        }.getOrDefault(bytes to mimeType)
    }
