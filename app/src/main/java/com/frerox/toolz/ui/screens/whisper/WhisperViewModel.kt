/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0
 */
package com.frerox.toolz.ui.screens.whisper

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class WhisperViewModel @Inject constructor(
    private val repository: WhisperRepository,
    private val authManager: WhisperAuthManager,
    private val notificationManager: WhisperNotificationManager,
    private val mutePrefs: WhisperMutePreferences,
    private val hiddenChatsStore: WhisperHiddenChatsStore,
    private val settingsRepository: SettingsRepository,
    private val crypto: WhisperCrypto,
) : ViewModel() {
    private var profileSearchJob: Job? = null

    private val _uiState = MutableStateFlow(WhisperUiState())
    val uiState: StateFlow<WhisperUiState> = _uiState.asStateFlow()

    val betaWarningShown: StateFlow<Boolean?> = settingsRepository.whisperBetaWarningShown
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val onboardingShown: StateFlow<Boolean?> = settingsRepository.whisperOnboardingShown
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun markBetaWarningAsShown() {
        viewModelScope.launch {
            settingsRepository.setWhisperBetaWarningShown(true)
        }
    }

    fun markOnboardingAsShown(onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            settingsRepository.setWhisperOnboardingShown(true)
            onDone?.invoke()
        }
    }

    private val _pickPhotoTrigger = MutableStateFlow(0)
    val pickPhotoTrigger: StateFlow<Int> = _pickPhotoTrigger.asStateFlow()

    fun triggerPickPhoto() {
        _pickPhotoTrigger.value += 1
    }

    val isAuthenticated: StateFlow<Boolean?> = authManager.isAuthenticated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var messagesJob: Job? = null
    private var friendsJob: Job? = null
    private var muteJob: Job? = null
    private var hiddenJob: Job? = null
    private var heartbeatJob: Job? = null

    init {
        observeMutes()
        observeHiddenChats()
        
        viewModelScope.launch {
            isAuthenticated.collect { auth ->
                if (auth == true) {
                    loadAll()
                    subscribeToMessages()
                    subscribeToFriends()
                    startHeartbeat()
                } else if (auth == false) {
                    // Tear down every subscription and cached account state so a dead
                    // session never keeps retrying against the network.
                    messagesJob?.cancel()
                    friendsJob?.cancel()
                    loadAllJob?.cancel()
                    stopHeartbeat()
                    _uiState.update { WhisperUiState() }
                }
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (true) {
                // Don't burn battery or write last_seen while the app is backgrounded.
                if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    repository.updateLastSeen()
                }
                delay(60_000) // Update every minute
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun handleError(err: Throwable, context: String) {
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
        if (loadAllJob?.isActive == true) return
        loadAllJob = viewModelScope.launch {
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

            _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
        }
    }

    val isAnonymousTokenUser: Boolean
        get() = authManager.isAnonymousTokenUser

    private suspend fun loadConversationsInternal(forceRefresh: Boolean = false) {
        repository.getConversations(forceRefresh = forceRefresh)
            .onSuccess { convos ->
                val muted = mutePrefs.mutedUsers.value
                val hidden = hiddenChatsStore.hiddenChats.value
                val mapped = convos
                    .map { it.copy(isMuted = it.otherUser.id in muted) }
                    .filter { convo ->
                        val hideTime = hidden[convo.otherUser.id] ?: return@filter true
                        val lastEpoch = runCatching {
                            java.time.OffsetDateTime.parse(convo.lastMessage.createdAt).toInstant().toEpochMilli()
                        }.getOrNull()
                        if (lastEpoch != null && lastEpoch > hideTime) {
                            // Partner sent a new message after the chat was hidden → bring it back
                            viewModelScope.launch { hiddenChatsStore.unhideChat(convo.otherUser.id) }
                            true
                        } else {
                            false
                        }
                    }
                _uiState.update { it.copy(conversations = mapped) }

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
        hiddenChatsStore.hideChat(userId)
    }

    fun clearChatHistory(userId: String) {
        viewModelScope.launch {
            repository.clearMessagesForRange(userId, null, null)
                .onSuccess { loadConversationsInternal(forceRefresh = true) }
                .onFailure { handleError(it, "clearMessagesForRange") }
        }
    }

    fun toggleBlockUser(userId: String) {
        viewModelScope.launch {
            val isBlocked = repository.isBlockedByMe(userId)
            if (isBlocked) {
                repository.unblockUser(userId)
                    .onSuccess { loadConversationsInternal(forceRefresh = true) }
                    .onFailure { handleError(it, "unblockUser") }
            } else {
                repository.blockUser(userId)
                    .onSuccess { loadConversationsInternal(forceRefresh = true) }
                    .onFailure { handleError(it, "blockUser") }
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
                    it.copy(
                        pendingIncomingRequests = pendingRequests,
                        pendingIncoming = pendingRequests.map { r -> r.friendship }
                    )
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

    fun loadDiscoverProfiles(page: Int) {
        if (page == 0) {
            _uiState.update { it.copy(discoverProfiles = emptyList(), discoverPage = 0, hasReachedEndOfDiscover = false) }
        }
        
        if (_uiState.value.isDiscoverLoadingNext || _uiState.value.hasReachedEndOfDiscover) return

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
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isDiscoverLoadingNext = false) }
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
                .onFailure { err -> handleError(err, "searchProfiles") }
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

    fun toggleMuteUser(userId: String) {
        if (mutePrefs.isMuted(userId)) {
            mutePrefs.unmuteUser(userId)
        } else {
            mutePrefs.muteUser(userId)
        }
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
            // Compression is CPU-heavy; never run it on the main thread.
            val optimizedBytes = withContext(Dispatchers.Default) {
                com.frerox.toolz.util.ImageUtils.downscaleAndCompress(imageBytes)
            }
            repository.uploadAvatar(optimizedBytes, mimeType)
                .onSuccess { url ->
                    repository.getMyProfile(forceRefresh = true).onSuccess { p ->
                        _uiState.update { it.copy(currentProfile = p) }
                    }
                }
                .onFailure { handleError(it, "uploadAvatar") }
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
        // Stop realtime subscriptions first so they don't retry against a dead session
        messagesJob?.cancel()
        friendsJob?.cancel()
        loadAllJob?.cancel()
        viewModelScope.launch {
            authManager.signOut()
            onComplete()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Fingerprint of my own public key, for sharing with friends to verify in person. */
    val myFingerprint: String?
        get() = crypto.getPublicKeyBase64()?.let { crypto.fingerprint(it) }

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
                    // Force refresh conversations from server on new incoming message
                    loadConversationsInternal(forceRefresh = true)
                    if (msg.senderId != myId && !msg.isDeletedForEveryone) {
                        // Hidden and muted chats never notify.
                        if (hiddenChatsStore.isHidden(msg.senderId)) return@collect
                        if (mutePrefs.isMuted(msg.senderId)) return@collect
                        val senderProfile = repository.getProfile(msg.senderId).getOrNull()
                        val senderName = senderProfile?.effectiveName ?: "Someone"
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
                        val senderProfile = repository.getProfile(friendship.userA).getOrNull()
                        val senderName = senderProfile?.effectiveName ?: "Someone"
                        notificationManager.showFriendRequestNotification(senderName)
                    }
                }
        }
    }


    override fun onCleared() {
        super.onCleared()
        messagesJob?.cancel()
        friendsJob?.cancel()
        muteJob?.cancel()
        hiddenJob?.cancel()
        loadAllJob?.cancel()
    }
}
