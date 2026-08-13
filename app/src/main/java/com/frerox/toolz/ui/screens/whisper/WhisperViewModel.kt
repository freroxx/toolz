/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0
 */
package com.frerox.toolz.ui.screens.whisper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.whisper.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhisperViewModel @Inject constructor(
    private val repository: WhisperRepository,
    private val authManager: WhisperAuthManager,
    private val notificationManager: WhisperNotificationManager,
    private val mutePrefs: WhisperMutePreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhisperUiState())
    val uiState: StateFlow<WhisperUiState> = _uiState.asStateFlow()

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

    init {
        observeMutes()
        loadAll()
        subscribeToMessages()
        subscribeToFriends()
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
                        .onFailure { err -> _uiState.update { it.copy(error = WhisperErrorMapper.map(err, "getMyProfile")) } }
                }
                val convosDeferred = async { loadConversationsInternal() }
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

    private suspend fun loadConversationsInternal() {
        repository.getConversations()
            .onSuccess { convos ->
                val muted = mutePrefs.mutedUsers.value
                val mapped = convos.map { it.copy(isMuted = it.otherUser.id in muted) }
                _uiState.update { it.copy(conversations = mapped) }
            }
            .onFailure { err ->
                _uiState.update { it.copy(error = WhisperErrorMapper.map(err, "getConversations")) }
            }
    }

    private suspend fun loadFriendsInternal() {
        repository.getFriends()
            .onSuccess { friends -> _uiState.update { it.copy(friends = friends) } }
        repository.getPendingIncoming()
            .onSuccess { pending -> _uiState.update { it.copy(pendingIncoming = pending) } }
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
                .onFailure { err -> _uiState.update { it.copy(error = WhisperErrorMapper.map(err, "searchProfiles")) } }
        }
    }

    fun sendFriendRequest(targetUserId: String) {
        viewModelScope.launch {
            repository.sendFriendRequest(targetUserId)
                .onSuccess { loadFriendsInternal(); loadRecommendationsInternal() }
                .onFailure { _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(it, "sendFriendRequest")) } }
        }
    }

    fun acceptFriendRequest(friendshipId: String) {
        viewModelScope.launch {
            repository.acceptFriendRequest(friendshipId)
                .onSuccess { loadFriendsInternal(); loadRecommendationsInternal() }
                .onFailure { _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(it, "acceptFriendRequest")) } }
        }
    }

    fun declineFriendRequest(friendshipId: String) {
        viewModelScope.launch {
            repository.deleteFriendship(friendshipId)
                .onSuccess { loadFriendsInternal() }
                .onFailure { _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(it, "declineFriendRequest")) } }
        }
    }

    fun unfriend(targetUserId: String) {
        viewModelScope.launch {
            repository.getFriendshipStatus(targetUserId)
                .onSuccess { (_, friendship) ->
                    if (friendship != null) {
                        repository.deleteFriendship(friendship.id)
                            .onSuccess { loadFriendsInternal(); loadRecommendationsInternal() }
                            .onFailure { err -> _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(err, "unfriend")) } }
                    }
                }
                .onFailure { err -> _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(err, "getFriendshipStatus")) } }
        }
    }

    fun toggleMuteUser(userId: String) {
        if (mutePrefs.isMuted(userId)) {
            mutePrefs.unmuteUser(userId)
        } else {
            mutePrefs.muteUser(userId)
        }
    }

    fun updateProfile(displayName: String, bio: String, isPrivate: Boolean) {
        viewModelScope.launch {
            val update = WhisperProfileUpdate(
                displayName = displayName.takeIf { it.isNotBlank() },
                bio = bio.takeIf { it.isNotBlank() },
                isPrivate = isPrivate,
            )
            repository.updateProfile(update)
                .onSuccess {
                    repository.getMyProfile(forceRefresh = true).onSuccess { p ->
                        _uiState.update { it.copy(currentProfile = p) }
                    }
                }
                .onFailure { _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(it, "updateProfile")) } }
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
                .onFailure { _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(it, "uploadAvatar")) } }
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
                .onFailure { _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(it, "deleteAvatar")) } }
        }
    }

    fun signOut() {
        viewModelScope.launch { authManager.signOut() }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun subscribeToMessages() {
        val myId = authManager.currentUserId ?: return

        messagesJob = viewModelScope.launch {
            try {
                repository.subscribeToIncomingMessages(myId).collect { msg ->
                    loadConversationsInternal()
                    if (msg.senderId != myId) {
                        val senderProfile = repository.getProfile(msg.senderId).getOrNull()
                        val senderName = senderProfile?.effectiveName ?: "Someone"
                        notificationManager.showMessageNotification(
                            senderId = msg.senderId,
                            senderName = senderName,
                            preview = msg.content
                        )
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun subscribeToFriends() {
        friendsJob = viewModelScope.launch {
            try {
                repository.subscribeToFriendUpdates().collect { friendship ->
                    loadFriendsInternal()
                    loadRecommendationsInternal()
                    val myId = authManager.currentUserId
                    if (friendship.userB == myId && friendship.status == "pending") {
                        val senderProfile = repository.getProfile(friendship.userA).getOrNull()
                        val senderName = senderProfile?.effectiveName ?: "Someone"
                        notificationManager.showFriendRequestNotification(senderName)
                    }
                }
            } catch (_: Exception) { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        messagesJob?.cancel()
        friendsJob?.cancel()
        muteJob?.cancel()
    }
}
