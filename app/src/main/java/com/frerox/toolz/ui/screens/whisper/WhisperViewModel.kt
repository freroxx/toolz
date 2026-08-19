/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0
 */
package com.frerox.toolz.ui.screens.whisper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.whisper.*
import dagger.hilt.android.lifecycle.HiltViewModel
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

    fun resetOnboarding() {
        viewModelScope.launch {
            settingsRepository.setWhisperOnboardingShown(false)
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
                } else {
                    stopHeartbeat()
                }
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (true) {
                repository.updateLastSeen()
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

    fun loadAll(isRefresh: Boolean = false) {
        viewModelScope.launch {
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
            .onSuccess { recommended -> _uiState.update { it.copy(recommendedProfiles = recommended) } }
    }

    fun searchProfiles(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
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
        viewModelScope.launch {
            val update = WhisperProfileUpdate(
                displayName = displayName.takeIf { it.isNotBlank() },
                bio = bio.takeIf { it.isNotBlank() },
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
                    onComplete()
                }
                .onFailure { handleError(it, "deleteAccount") }
        }
    }

    fun uploadAvatar(imageBytes: ByteArray, mimeType: String) {
        viewModelScope.launch {
            val optimizedBytes = com.frerox.toolz.util.ImageUtils.downscaleAndCompress(imageBytes)
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
                    android.util.Log.e("WhisperVM", "Incoming messages realtime error: ${cause.message}. Retrying in 3s...")
                    delay(3000)
                    true
                }
                .collect { msg ->
                    // Force refresh conversations from server on new incoming message
                    loadConversationsInternal(forceRefresh = true)
                    if (msg.senderId != myId) {
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
                    android.util.Log.e("WhisperVM", "Friends realtime error: ${cause.message}. Retrying in 3s...")
                    delay(3000)
                    true
                }
                .collect { friendship ->
                    loadFriendsInternal()
                    loadRecommendationsInternal()
                    val myId = authManager.currentUserId
                    if (friendship.userB == myId && friendship.status == "pending") {
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
    }
}
