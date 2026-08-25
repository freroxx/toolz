/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0
 */
package com.frerox.toolz.ui.screens.whisper

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.whisper.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class WhisperViewModel @Inject constructor(
    // V6-R2 (review): app context lets notification/error fallbacks localize instead of
    // leaking hardcoded English ("Someone", "Profile not loaded", …).
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val repository: WhisperRepository,
    private val authManager: WhisperAuthManager,
    private val notificationManager: WhisperNotificationManager,
    private val mutePrefs: WhisperMutePreferences,
    private val hiddenChatsStore: WhisperHiddenChatsStore,
    private val settingsRepository: SettingsRepository,
    private val crypto: WhisperCrypto,
    private val outgoingQueue: WhisperOutgoingQueue,
    private val aubupManager: WhisperAubupManager,
    private val prekeyManager: com.frerox.toolz.data.whisper.WhisperPrekeyManager,
    private val keyRotationStore: WhisperKeyRotationStore,
    private val offlineManager: com.frerox.toolz.util.OfflineManager,
) : ViewModel() {
    private var profileSearchJob: Job? = null

    private val _uiState = MutableStateFlow(WhisperUiState())
    val uiState: StateFlow<WhisperUiState> = _uiState.asStateFlow()

    val betaWarningShown: StateFlow<Boolean?> = settingsRepository.whisperBetaWarningShown
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val onboardingShown: StateFlow<Boolean?> = settingsRepository.whisperOnboardingShown
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val screenshotBypassEnabled: StateFlow<Boolean> = settingsRepository.whisperScreenshotBypass
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setScreenshotBypass(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWhisperScreenshotBypass(enabled)
        }
    }

    fun markBetaWarningAsShown() {
        viewModelScope.launch {
            settingsRepository.setWhisperBetaWarningShown(true)
        }
    }

    private var onboardingInFlight = false
    fun markOnboardingAsShown(onDone: (() -> Unit)? = null) {
        // Guard against two rapid taps double-invoking onDone (e.g. switching tabs twice).
        if (onboardingInFlight) return
        onboardingInFlight = true
        viewModelScope.launch {
            try {
                settingsRepository.setWhisperOnboardingShown(true)
                onDone?.invoke()
            } finally {
                onboardingInFlight = false
            }
        }
    }

    private val _pickPhotoTrigger = MutableStateFlow(0)
    val pickPhotoTrigger: StateFlow<Int> = _pickPhotoTrigger.asStateFlow()

    fun triggerPickPhoto() {
        _pickPhotoTrigger.value += 1
    }

    // V2-FIX M-H1: one-shot consumption — after the screen launches the picker it resets
    // the trigger so a collection restart (tab re-entry, process recreation) can never
    // re-fire the last value and silently relaunch the photo picker.
    fun consumePickPhotoTrigger() {
        _pickPhotoTrigger.value = 0
    }

    val isAuthenticated: StateFlow<Boolean?> = authManager.isAuthenticated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var messagesJob: Job? = null
    private var friendsJob: Job? = null
    private var muteJob: Job? = null
    private var hiddenJob: Job? = null
    private var heartbeatJob: Job? = null
    private var droppedCollectorJob: Job? = null

    // Ids already surfaced to the user after their outbox entry was permanently dropped.
    private val droppedNotifiedClientIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    init {
        observeMutes()
        observeHiddenChats()
        startDroppedCollector()

        viewModelScope.launch {
            isAuthenticated.collect { auth ->
                if (auth == true) {
                    // Hydrate local tombstones from server so reinstall / new device never resurrects deletes.
                    launch { runCatching { repository.pullRemoteTombstones() }
                // PHASE 2 (roadmap §2.3): keep our signed prekey bundle published.
                viewModelScope.launch { prekeyManager.ensurePublished(authManager.currentUserId ?: return@launch) } }
                // V6-R4 FIX (#2): proactive identity-key self-heal at startup — if the
                // published profiles.public_key ever drifts from THIS device's key
                // (reinstall, interrupted rotation), republish immediately so partners'
                // messages open out of the box instead of triggering the rotate+verify
                // dance. Idempotent; no-op when already in sync.
                // V6-R6 (#4): ordering guarantee — runs AFTER getMyProfile() completes
                // (not a blind timer), so the divergence heal and this republish can't
                // race; a failed profile-load heal is retried here.
                viewModelScope.launch {
                    runCatching {
                        repository.getMyProfile().onSuccess {
                            repository.republishLocalKeyIfStale()
                        }
                    }.onFailure { WhisperErrorMapper.log(it, "startupRepublish") }
                }
                    loadAll()
                    subscribeToMessages()
                    subscribeToFriends()
                    startHeartbeat()
                    observeProcessLifecycleForPresence() // V6-R6: instant presence
                    // V2-FIX L-?: restart the dropped-entry collector — signOut cancels it.
                    startDroppedCollector()
                } else if (auth == false) {
                    // Tear down every subscription and cached account state so a dead
                    // session never keeps retrying against the network.
                    messagesJob?.cancel()
                    friendsJob?.cancel()
                    loadAllJob?.cancel()
                    stopHeartbeat()
                    // Clear account-scoped local state so next login doesn't see previous user's hidden chats/mutes.
                    hiddenChatsStore.clearAll()
                    // V2-FIX (reviewwhisper.md): resolved review question at this site — mutes are wiped
                    // GLOBALLY on sign-out, NOT scoped per account. Decision: WhisperMutePreferences is a
                    // single-account SharedPreferences store (no account-keyed prefix support), so per-
                    // account scoping would require a storage migration; the global wipe is the simplest
                    // correct guard against cross-account mute leakage between logins.
                    runCatching { mutePrefs.clearAll() }
                    runCatching { keyRotationStore.clear() }
                    droppedNotifiedClientIds.clear()
                    _uiState.update { WhisperUiState() }
                }
            }
        }
    }

    // Surfacing dropped outbox entries: if the delivery scheduler permanently gives
    // up on a queued message, the user must know it never arrived.
    // V2-FIX L-?: extracted so signOut can cancel the collector (it restarts on re-auth).
    private fun startDroppedCollector() {
        droppedCollectorJob?.cancel()
        droppedCollectorJob = viewModelScope.launch {
            outgoingQueue.droppedClientIds.collect { dropped ->
                // One notice per clientId; later duplicates are ignored. V2-FIX L-?: burst
                // drops are AGGREGATED into a single notice with the count instead of
                // stacking N identical toasts (the toast queue caps at 5 anyway).
                val fresh = dropped.filter { droppedNotifiedClientIds.add(it) }
                if (fresh.isEmpty()) return@collect
                _uiState.update { state ->
                    if (fresh.size == 1) {
                        state.copy(error = UiText.StringResource(R.string.st_Whisper_Error_MessageFailed))
                    } else {
                        // Aggregated notice with the count formatted via context.getString at
                        // display time (toast hosts resolve UiText through asString(context)).
                        state.copy(error = UiText.StringResource(R.string.st_Whisper_Error_MessagesRemoved, fresh.size))
                    }
                }
            }
        }
    }

    // M-11 FIX (reviewwhisper.md): realtime message events used to trigger a
    // conversations RPC per event. A trailing 1.5s debounce collapses bursts into a
    // single refresh while keeping the list feeling live.
    private var convoRefreshJob: Job? = null
    private fun scheduleConversationRefresh(forceRefresh: Boolean) {
        convoRefreshJob?.cancel()
        convoRefreshJob = viewModelScope.launch {
            delay(1_500)
            loadConversationsInternal(forceRefresh = forceRefresh)
        }
    }

    // V6-R6 (#presence): INSTANT online/offline transitions.
    //  ON_START → last_seen=now   (partners see "online" within one realtime tick)
    //  ON_STOP  → last_seen=now-3min (beyond the 120s window ⇒ instantly offline,
    //             rendered as "online Xm ago") — no schema change required.
    private var presenceLifecycleObserver: androidx.lifecycle.LifecycleEventObserver? = null

    private fun observeProcessLifecycleForPresence() {
        if (presenceLifecycleObserver != null) return
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START ->
                    viewModelScope.launch { runCatching { repository.updateLastSeen() } }
                androidx.lifecycle.Lifecycle.Event.ON_STOP ->
                    viewModelScope.launch { runCatching { repository.goOfflineInstantly() } }
                else -> {}
            }
        }
        presenceLifecycleObserver = observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (true) {
                try {
                    if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        repository.updateLastSeen()
                        // V6-R6: automatic 30-day identity rotation REMOVED here — the
                        // identity keypair is the history-decryption key; rotating it
                        // bricked all prior messages and forced the rotate+verify dance.
                        // FS comes from the Double Ratchet's per-message keys.
                        // V6-R6 (#2): heartbeat convergence — retries republish every
                        // 5 min if the startup attempt failed. Idempotent when in sync.
                        runCatching { repository.republishLocalKeyIfStale() }
                            .onFailure { WhisperErrorMapper.log(it, "heartbeatRepublish") }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (WhisperErrorMapper.isSessionExpired(e) || (e is io.github.jan.supabase.exceptions.RestException && e.statusCode in 401..403)) {
                        // Stop retrying on dead session
                        break
                    }
                    android.util.Log.w("WhisperVM", "heartbeat failed: ${e.message}")
                }
                // V6-R6 (#presence): 60s keepalive — presence reads as "online Xm ago"
                // with minute granularity; instant transitions are handled by the
                // lifecycle observer below, this only maintains freshness.
                delay(60_000)
            }
        }
    }

    // V6-R6: maybeAutoRotateKey() and its isOnline() helper were removed together with
    // the automatic-rotation trigger in startHeartbeat() — dead code that could be
    // accidentally re-invoked has no place in a security-critical path.

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun handleError(err: Throwable, context: String) {
        // Cancellation (e.g. a debounce cancelling the previous search job, or the VM
        // being cleared) is not an error — never surface it as a toast.
        if (err is kotlinx.coroutines.CancellationException) return
        val mapped = WhisperErrorMapper.map(err, context)
        if (WhisperErrorMapper.isSessionExpired(err)) {
            viewModelScope.launch {
                authManager.signOut()
            }
        } else {
            _uiState.update { it.copy(error = mapped) }
        }
    }

    private fun observeMutes() {
        muteJob = viewModelScope.launch {
            mutePrefs.mutedUsers.collect { mutedSet ->
                _uiState.update { state ->
                    val updatedConvos = state.conversations.map { convo ->
                        convo.copy(isMuted = convo.otherUser.id in mutedSet)
                    }
                    state.copy(mutedUserIds = mutedSet, conversations = updatedConvos)
                }
            }
        }
    }

    private var loadAllJob: Job? = null

    fun loadAll(isRefresh: Boolean = false) {
        // Guard against overlapping loads (e.g. refresh + auth-state re-entry)
        // Refresh must not be dropped if a previous load is still active.
        if (loadAllJob?.isActive == true) {
            if (!isRefresh) return
            loadAllJob?.cancel()
        }
        loadAllJob = viewModelScope.launch {
            try {
                if (isRefresh) _uiState.update { it.copy(isRefreshing = true) }
                else _uiState.update { it.copy(isLoading = true) }

                coroutineScope {
                    val profileDeferred = async {
                        repository.getMyProfile(forceRefresh = isRefresh)
                            .onSuccess { profile -> _uiState.update { it.copy(currentProfile = profile) } }
                            .onFailure { err -> handleError(err, "getMyProfile") }
                    }
                    val convosDeferred = async { loadConversationsInternal(forceRefresh = isRefresh) }
                    val friendsDeferred = async { loadFriendsInternal() }
                    val recommendedDeferred = async { loadRecommendationsInternal() }

                    profileDeferred.await()
                    convosDeferred.await()
                    friendsDeferred.await()
                    recommendedDeferred.await()
                }
            } finally {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    val isAnonymousTokenUser: Boolean
        get() = authManager.isAnonymousTokenUser

    private suspend fun loadConversationsInternal(forceRefresh: Boolean = false) {
        repository.getConversations(forceRefresh = forceRefresh)
            .onSuccess { convos ->
                val muted = mutePrefs.mutedUsers.value
                val mapped = convos.map { it.copy(isMuted = it.otherUser.id in muted) }
                // V2-FIX L-?: compute the unhide list OUTSIDE the _uiState.update lambda —
                // MutableStateFlow.update may re-run its lambda under contention, so any
                // side effect inside could fire twice (duplicate unhide writes).
                // The hidden snapshot is read immediately before the state write so a chat
                // hidden while this network call was in flight is still honored.
                val hidden = hiddenChatsStore.hiddenChats.value
                val toUnhide = mutableListOf<String>()
                val filtered = mapped.filter { convo ->
                    val hideTime = hidden[convo.otherUser.id] ?: return@filter true
                    val lastEpoch = runCatching {
                        java.time.OffsetDateTime.parse(convo.lastMessage.createdAt).toInstant().toEpochMilli()
                    }.getOrNull()
                    if (lastEpoch != null && lastEpoch > hideTime) {
                        // Partner sent a new message after the chat was hidden → bring it back
                        toUnhide.add(convo.otherUser.id)
                        true
                    } else {
                        false
                    }
                }
                _uiState.update { it.copy(conversations = filtered) }
                toUnhide.forEach { viewModelScope.launch { hiddenChatsStore.unhideChat(it) } }

                // Clear notifications for any conversations that have been read (read receipts)
                for (convo in convos) {
                    if (convo.unreadCount == 0) {
                        notificationManager.cancelMessageNotification(convo.otherUser.id)
                    }
                }
            }
            .onFailure { err ->
                handleError(err, "getConversations")
            }
    }

    private fun observeHiddenChats() {
        hiddenJob = viewModelScope.launch {
            hiddenChatsStore.hiddenChats.collect { hiddenMap ->
                _uiState.update { state ->
                    state.copy(
                        conversations = state.conversations.filter { convo ->
                            val hideTime = hiddenMap[convo.otherUser.id]
                            if (hideTime == null) return@filter true
                            val lastEpoch = runCatching {
                                java.time.OffsetDateTime.parse(convo.lastMessage.createdAt).toInstant().toEpochMilli()
                            }.getOrNull()
                            lastEpoch != null && lastEpoch > hideTime
                        }
                    )
                }
            }
        }
    }

    fun hideChat(userId: String) {
        // V2-FIX (reviewwhisper.md): store mutators are suspend + IO commit now.
        viewModelScope.launch { hiddenChatsStore.hideChat(userId) }
    }

    fun clearChatHistory(userId: String) {
        viewModelScope.launch {
            repository.clearMessagesForRange(userId, null, null)
                .onSuccess { loadConversationsInternal(forceRefresh = true) }
                .onFailure { handleError(it, "clearMessagesForRange") }
        }
    }

    // In-flight guard so a double-tap can't toggle the block twice.
    private val blockInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Toggles the block state for [userId]. [onResult] is invoked with the NEW state
     * (true = now blocked) after a successful toggle, so the caller can toast accurately
     * instead of guessing from a possibly-stale local snapshot.
     */
    fun toggleBlockUser(userId: String, onResult: (Boolean) -> Unit = {}) {
        // Double-tap guard: only one block toggle per user at a time.
        if (userId in blockInFlight) return
        blockInFlight.add(userId)
        viewModelScope.launch {
            try {
                val isBlocked = repository.isBlockedByMe(userId)
                if (isBlocked) {
                    repository.unblockUser(userId)
                        .onSuccess {
                            loadConversationsInternal(forceRefresh = true)
                            onResult(false)
                        }
                        .onFailure { handleError(it, "unblockUser") }
                } else {
                    repository.blockUser(userId)
                        .onSuccess {
                            loadConversationsInternal(forceRefresh = true)
                            onResult(true)
                        }
                        .onFailure { handleError(it, "blockUser") }
                }
            } finally {
                blockInFlight.remove(userId)
            }
        }
    }

    suspend fun isBlockedByMe(userId: String): Boolean = repository.isBlockedByMe(userId)

    private suspend fun loadFriendsInternal() {
        repository.getFriends()
            .onSuccess { friends -> _uiState.update { it.copy(friends = friends) } }
        repository.getPendingIncomingWithProfiles()
            .onSuccess { pendingRequests ->
                _uiState.update {
                    it.copy(pendingIncomingRequests = pendingRequests)
                }
            }
        repository.getPendingOutgoing()
            .onSuccess { pending -> _uiState.update { it.copy(pendingOutgoing = pending) } }
    }

    private suspend fun loadRecommendationsInternal() {
        repository.getFriendsOfFriends()
            .onSuccess { recommended -> 
                _uiState.update { it.copy(recommendedProfiles = recommended) }
                if (_uiState.value.discoverProfiles.isEmpty()) {
                    loadDiscoverProfiles(0)
                }
            }
    }

    private val _discoverLoadFailed = MutableStateFlow(false)
    val discoverLoadFailed: StateFlow<Boolean> = _discoverLoadFailed.asStateFlow()

    fun loadDiscoverProfiles(page: Int) {
        // Page 0 is a refresh and must never be blocked by pagination state; only
        // block subsequent pages when already loading or at end.
        if (page != 0 && (_uiState.value.isDiscoverLoadingNext || _uiState.value.hasReachedEndOfDiscover)) return
        if (page == 0) {
            _uiState.update { it.copy(discoverProfiles = emptyList(), discoverPage = 0, hasReachedEndOfDiscover = false) }
        }

        _uiState.update { it.copy(isDiscoverLoadingNext = true) }
        viewModelScope.launch {
            repository.getDiscoverProfiles(page)
                .onSuccess { results ->
                    _uiState.update { state ->
                        state.copy(
                            discoverProfiles = if (page == 0) results else state.discoverProfiles + results,
                            discoverPage = page,
                            isDiscoverLoadingNext = false,
                            hasReachedEndOfDiscover = results.isEmpty()
                        )
                    }
                    _discoverLoadFailed.value = false
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isDiscoverLoadingNext = false) }
                    _discoverLoadFailed.value = true
                    handleError(err, "loadDiscoverProfiles")
                }
        }
    }

    fun loadNextDiscoverPage() {
        loadDiscoverProfiles(_uiState.value.discoverPage + 1)
    }

    fun searchProfiles(query: String) {
        profileSearchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        profileSearchJob = viewModelScope.launch {
            delay(300)
            repository.searchProfiles(query)
                .onSuccess { results -> _uiState.update { it.copy(searchResults = results) } }
                .onFailure { err ->
                    // A keystroke cancels the previous job; the repository's runCatching
                    // turns that cancellation into a failure, so never toast it.
                    if (err is kotlinx.coroutines.CancellationException) return@onFailure
                    handleError(err, "searchProfiles")
                }
        }
    }

    fun sendFriendRequest(targetUserId: String) {
        viewModelScope.launch {
            repository.sendFriendRequest(targetUserId)
                .onSuccess { loadFriendsInternal(); loadRecommendationsInternal() }
                .onFailure { handleError(it, "sendFriendRequest") }
        }
    }

    fun acceptFriendRequest(friendshipId: String) {
        viewModelScope.launch {
            repository.acceptFriendRequest(friendshipId)
                .onSuccess { loadFriendsInternal(); loadRecommendationsInternal() }
                .onFailure { handleError(it, "acceptFriendRequest") }
        }
    }

    fun declineFriendRequest(friendshipId: String) {
        viewModelScope.launch {
            repository.deleteFriendship(friendshipId)
                .onSuccess { loadFriendsInternal() }
                .onFailure { handleError(it, "declineFriendRequest") }
        }
    }

    fun unfriend(targetUserId: String) {
        viewModelScope.launch {
            repository.getFriendshipStatus(targetUserId)
                .onSuccess { (_, friendship) ->
                    if (friendship != null) {
                        repository.deleteFriendship(friendship.id)
                            .onSuccess { loadFriendsInternal(); loadRecommendationsInternal() }
                            .onFailure { err -> handleError(err, "unfriend") }
                    }
                }
                .onFailure { err -> handleError(err, "getFriendshipStatus") }
        }
    }

    /**
     * Toggles mute for [userId]. [onResult] is invoked with the NEW state
     * (true = now muted) so callers toast accurately instead of guessing from a
     * possibly-stale conversation snapshot.
     */
    fun toggleMuteUser(userId: String, onResult: (Boolean) -> Unit = {}) {
        val nowMuted = if (mutePrefs.isMuted(userId)) {
            mutePrefs.unmuteUser(userId)
            false
        } else {
            mutePrefs.muteUser(userId)
            true
        }
        onResult(nowMuted)
    }

    fun updateProfile(
        displayName: String,
        bio: String,
        isPrivate: Boolean,
        isHiddenFromDiscover: Boolean = false,
        onSuccess: (() -> Unit)? = null,
    ) {
        val trimmedName = displayName.trim()
        if (trimmedName.isBlank()) {
            handleError(Exception("Display name cannot be empty"), "updateProfile")
            return
        }
        viewModelScope.launch {
            val update = WhisperProfileUpdate(
                displayName = trimmedName,
                bio = bio.trim().takeIf { it.isNotBlank() },
                isPrivate = isPrivate,
                isHiddenFromDiscover = isHiddenFromDiscover,
            )
            repository.updateProfile(update)
                .onSuccess {
                    repository.getMyProfile(forceRefresh = true).onSuccess { p ->
                        _uiState.update { it.copy(currentProfile = p) }
                    }
                    onSuccess?.invoke()
                }
                .onFailure { handleError(it, "updateProfile") }
        }
    }

    fun deleteAccount(password: String? = null, onComplete: () -> Unit) {
        viewModelScope.launch {
            authManager.deleteAccount(password)
                .onSuccess {
                    // Permanently wipe every local trace of the account after the server
                    // side confirmed deletion.
                    repository.clearAllLocalData()
                    onComplete()
                }
                .onFailure { handleError(it, "deleteAccount") }
        }
    }

    fun uploadAvatar(imageBytes: ByteArray, mimeType: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingAvatar = true) }
            try {
                // Compression is CPU-heavy; never run it on the main thread.
                val optimizedBytes = withContext(Dispatchers.Default) {
                    com.frerox.toolz.util.ImageUtils.downscaleAndCompress(imageBytes)
                }
                repository.uploadAvatar(optimizedBytes, mimeType)
                    .onSuccess { url ->
                        // V6-R7c/d FIX (not-applied): optimistic reflect so the UI shows the
                        // new picture instantly even before the forced profile refetch lands.
                        // Also reflect the sealing key (ownPub) so the primed cache decrypts
                        // with the same key that was used to seal; otherwise a stale row
                        // key would make the just-primed avatar undecryptable (initials).
                        val ownPub = crypto.getPublicKeyBase64()
                        _uiState.update { cur ->
                            val curProfile = cur.currentProfile
                            if (curProfile != null) {
                                cur.copy(
                                    currentProfile = curProfile.copy(
                                        avatarUrl = url,
                                        publicKey = if (!ownPub.isNullOrBlank() && curProfile.publicKey != ownPub) ownPub else curProfile.publicKey,
                                        updatedAt = java.time.Instant.now().toString()
                                    )
                                )
                            } else cur
                        }
                        repository.getMyProfile(forceRefresh = true)
                            .onSuccess { p ->
                                _uiState.update { it.copy(currentProfile = p) }
                            }
                            .onFailure { handleError(it, "getMyProfile") }
                    }
                    .onFailure { handleError(it, "uploadAvatar") }
            } finally {
                _uiState.update { it.copy(isUploadingAvatar = false) }
            }
        }
    }

    fun createAccessFile(
        whisperCode: String,
        onSuccess: (java.io.File) -> Unit,
        onError: (String) -> Unit,
    ) {
        val current = _uiState.value.currentProfile ?: run {
            // V6-R2 (review): was hardcoded English "Profile not loaded".
            onError(appContext.getString(R.string.st_Whisper_Error_ProfileNotLoaded))
            return
        }
        viewModelScope.launch {
            aubupManager.createAccessFileForUser(
                username = current.username,
                displayName = current.displayName,
                whisperCode = whisperCode,
                // V2-FIX B4: the exported profile is always the live session's user, so
                // pass an explicit token flag instead of relying on vault-name guessing.
                isToken = authManager.isAnonymousTokenUser,
            ).onSuccess { file ->
                onSuccess(file)
            }.onFailure { err ->
                // V6-R2 (review): raw throwable text leaked to users — map centrally.
                onError(WhisperErrorMapper.map(err).asString(appContext))
            }
        }
    }

    fun rotateEncryptionKey(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            // Staged rotation: publish first, switch active key only on success.
            val staged = crypto.stageNewKeyPair()
            if (staged == null) {
                onComplete(false)
                return@launch
            }
            val update = WhisperProfileUpdate(publicKey = staged.publicKeyBase64)
            repository.updateProfile(update)
                .onSuccess {
                    crypto.commitStagedKeyPair(staged)
                    // V2-FIX H-?: active key changed — recompute the cached fingerprint.
                    refreshFingerprint()
                    repository.getMyProfile(forceRefresh = true).onSuccess { p ->
                        _uiState.update { it.copy(currentProfile = p) }
                    }
                    keyRotationStore.markRotated()
                    onComplete(true)
                }
                .onFailure {
                    crypto.abortStagedKeyPair(staged)
                    onComplete(false)
                }
        }
    }

    fun deleteAvatar() {
        viewModelScope.launch {
            repository.deleteAvatar()
                .onSuccess {
                    repository.getMyProfile(forceRefresh = true).onSuccess { p ->
                        _uiState.update { it.copy(currentProfile = p) }
                    }
                }
                .onFailure { handleError(it, "deleteAvatar") }
        }
    }

    fun signOut(onComplete: () -> Unit = {}) {
        // Stop the heartbeat FIRST so a dead session never keeps writing last_seen,
        // then tear down realtime subscriptions so they don't retry against a dead session.
        stopHeartbeat()
        messagesJob?.cancel()
        friendsJob?.cancel()
        loadAllJob?.cancel()
        // V2-FIX L-?: also stop the dropped-entry collector — nothing surfaced after
        // sign-out belongs to the next session (it restarts on re-auth).
        droppedCollectorJob?.cancel()
        viewModelScope.launch {
            authManager.signOut()
                .onFailure { err -> android.util.Log.w("WhisperVM", "signOut failed: ${err.message}", err) }
            // Regardless of the network outcome, wipe every session-scoped cache and
            // reset the UI so the user is never left staring at a stale account.
            repository.clearSessionScopedCaches()
            hiddenChatsStore.clearAll()
            runCatching { mutePrefs.clearAll() }
            droppedNotifiedClientIds.clear()
            _uiState.update { WhisperUiState() }
            onComplete()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** V2-FIX L-?: clears the dedicated success/info message (see [WhisperUiState.infoMessage]). */
    fun clearInfo() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    // V2-FIX H-?: the fingerprint used to be `by lazy`, i.e. cached for the VM's lifetime —
    // after a key rotation it kept showing the OLD fingerprint until process death. It is
    // now private state recomputed via [refreshFingerprint] on every rotation success path.
    private var cachedMyFingerprint: String? = null
    private var isMyFingerprintComputed = false

    /**
     * Fingerprint of my own public key, for sharing with friends to verify in person.
     * Cached so composition doesn't hit KeyStore on every read; invalidated by
     * [refreshFingerprint] whenever the active key changes.
     */
    val myFingerprint: String?
        get() {
            if (!isMyFingerprintComputed) refreshFingerprint()
            return cachedMyFingerprint
        }

    /** Recomputes my public-key fingerprint from the CURRENT KeyStore entry. */
    private fun refreshFingerprint() {
        cachedMyFingerprint = crypto.getPublicKeyBase64()?.let { crypto.fingerprint(it) }
        isMyFingerprintComputed = true
    }

    private fun subscribeToMessages() {
        val myId = authManager.currentUserId ?: return
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.subscribeToIncomingMessages(myId)
                .retry { cause ->
                    // Stop retrying once the session is gone or the server rejects the token;
                    // infinite retries against a dead session only churn the network.
                    if (authManager.currentUserId == null || WhisperErrorMapper.isSessionExpired(cause) ||
                        (cause is io.github.jan.supabase.exceptions.RestException && cause.statusCode in 401..403)
                    ) {
                        false
                    } else {
                        android.util.Log.e("WhisperVM", "Incoming messages realtime error: ${cause.message}. Retrying in 3s...")
                        delay(3000)
                        true
                    }
                }
                .collect { msg ->
                    // Refresh the chats list for INCOMING messages only. Own outgoing sends
                    // already invalidate the conversations cache in the repository, and
                    // refreshing on every echo turned each sent message into a network storm.
                    // M-11: debounced — see scheduleConversationRefresh.
                    if (msg.senderId != myId) {
                        scheduleConversationRefresh(forceRefresh = true)
                    } else {
                        repository.invalidateConversationsCache()
                        scheduleConversationRefresh(forceRefresh = false)
                    }
                    if (msg.senderId != myId && !msg.isDeletedForEveryone) {
                        // Hidden and muted chats never notify.
                        if (hiddenChatsStore.isHidden(msg.senderId)) return@collect
                        if (mutePrefs.isMuted(msg.senderId)) return@collect
                        // V2-FIX L-?: notification-path profile lookups route through
                        // repository.getProfile, which is backed by its 5-minute profileCache —
                        // no extra per-notification RPC beyond the cache TTL.
                        val senderProfile = repository.getProfile(msg.senderId).getOrNull()
                        val senderName = senderProfile?.effectiveName
                            // V6-R2 (review): was hardcoded English "Someone".
                            ?: appContext.getString(R.string.st_Whisper_SomeoneDefault)
                        notificationManager.showMessageNotification(
                            senderId = msg.senderId,
                            senderName = senderName,
                        )
                    }
                }
        }
    }

    private fun subscribeToFriends() {
        friendsJob?.cancel()
        friendsJob = viewModelScope.launch {
            repository.subscribeToFriendUpdates()
                .retry { cause ->
                    if (authManager.currentUserId == null || WhisperErrorMapper.isSessionExpired(cause) ||
                        (cause is io.github.jan.supabase.exceptions.RestException && cause.statusCode in 401..403)
                    ) {
                        false
                    } else {
                        android.util.Log.e("WhisperVM", "Friends realtime error: ${cause.message}. Retrying in 3s...")
                        delay(3000)
                        true
                    }
                }
                .collect { friendship ->
                    loadFriendsInternal()
                    loadRecommendationsInternal()
                    val myId = authManager.currentUserId
                    if (friendship.userB == myId && friendship.status == "pending") {
                        if (mutePrefs.isMuted(friendship.userA)) return@collect
                        // V2-FIX L-?: same cached path as message notifications —
                        // repository.getProfile serves from its 5-minute profileCache.
                        val senderProfile = repository.getProfile(friendship.userA).getOrNull()
                        val senderName = senderProfile?.effectiveName
                            // V6-R2 (review): was hardcoded English "Someone".
                            ?: appContext.getString(R.string.st_Whisper_SomeoneDefault)
                        notificationManager.showFriendRequestNotification(friendship.userA, senderName)
                    }
                }
        }
    }


    override fun onCleared() {
        super.onCleared()
        presenceLifecycleObserver?.let {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
        }
        presenceLifecycleObserver = null
        messagesJob?.cancel()
        friendsJob?.cancel()
        muteJob?.cancel()
        hiddenJob?.cancel()
        loadAllJob?.cancel()
        heartbeatJob?.cancel()
        profileSearchJob?.cancel()
        droppedCollectorJob?.cancel()
        convoRefreshJob?.cancel()
    }
}
