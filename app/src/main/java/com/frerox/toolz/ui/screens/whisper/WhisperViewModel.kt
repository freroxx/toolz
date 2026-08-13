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
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhisperUiState())
    val uiState: StateFlow<WhisperUiState> = _uiState.asStateFlow()

    val isAuthenticated: StateFlow<Boolean?> = authManager.isAuthenticated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var messagesJob: Job? = null
    private var friendsJob: Job? = null

    init {
        loadAll()
        subscribeToMessages()
        subscribeToFriends()
    }

    fun loadAll(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) _uiState.update { it.copy(isRefreshing = true) }
            else _uiState.update { it.copy(isLoading = true) }

            coroutineScope {
                val profileDeferred = async {
                    repository.getMyProfile()
                        .onSuccess { profile -> _uiState.update { it.copy(currentProfile = profile) } }
                        .onFailure { err -> _uiState.update { it.copy(error = WhisperErrorMapper.map(err, "getMyProfile")) } }
                }
                val convosDeferred = async {
                    repository.getConversations()
                        .onSuccess { convos -> _uiState.update { it.copy(conversations = convos) } }
                        .onFailure { err -> _uiState.update { it.copy(error = WhisperErrorMapper.map(err, "getConversations")) } }
                }
                val friendsDeferred = async { loadFriendsInternal() }
                profileDeferred.await()
                convosDeferred.await()
                friendsDeferred.await()
            }

            _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
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

    fun searchProfiles(query: String) {
        if (query.isBlank()) { _uiState.update { it.copy(searchResults = emptyList()) }; return }
        viewModelScope.launch {
            repository.searchProfiles(query)
                .onSuccess { results -> _uiState.update { it.copy(searchResults = results) } }
                .onFailure { err -> _uiState.update { it.copy(error = WhisperErrorMapper.map(err, "searchProfiles")) } }
        }
    }

    fun sendFriendRequest(targetUserId: String) {
        viewModelScope.launch {
            repository.sendFriendRequest(targetUserId)
                .onSuccess { loadAll() }
                .onFailure { _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(it, "sendFriendRequest")) } }
        }
    }

    fun acceptFriendRequest(friendshipId: String) {
        viewModelScope.launch {
            repository.acceptFriendRequest(friendshipId)
                .onSuccess { loadAll() }
                .onFailure { _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(it, "acceptFriendRequest")) } }
        }
    }

    fun declineFriendRequest(friendshipId: String) {
        viewModelScope.launch {
            repository.deleteFriendship(friendshipId)
                .onSuccess { loadAll() }
                .onFailure { _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(it, "declineFriendRequest")) } }
        }
    }

    fun unfriend(targetUserId: String) {
        viewModelScope.launch {
            repository.getFriendshipStatus(targetUserId)
                .onSuccess { (_, friendship) ->
                    if (friendship != null) {
                        repository.deleteFriendship(friendship.id)
                            .onSuccess { loadAll() }
                            .onFailure { err -> _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(err, "unfriend")) } }
                    }
                }
                .onFailure { err -> _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(err, "getFriendshipStatus")) } }
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
                .onSuccess { loadAll() }
                .onFailure { _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(it, "updateProfile")) } }
        }
    }

    fun uploadAvatar(imageBytes: ByteArray, mimeType: String) {
        viewModelScope.launch {
            // Downscale to ~1024px max and 80% quality to stay well within Supabase limits
            val optimizedBytes = com.frerox.toolz.util.ImageUtils.downscaleAndCompress(imageBytes)
            
            repository.uploadAvatar(optimizedBytes, mimeType)
                .onSuccess { url ->
                    repository.updateProfile(WhisperProfileUpdate(avatarUrl = url))
                        .onSuccess { loadAll() }
                }
                .onFailure { _uiState.update { s -> s.copy(error = WhisperErrorMapper.map(it, "uploadAvatar")) } }
        }
    }

    fun deleteAvatar() {
        viewModelScope.launch {
            repository.deleteAvatar()
                .onSuccess { loadAll() }
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
        messagesJob = viewModelScope.launch {
            try {
                repository.subscribeToIncomingMessages().collect { msg ->
                    loadAll()
                    // Send in-process notification if message is from another user
                    val senderProfile = repository.getProfile(msg.senderId).getOrNull()
                    val senderName = senderProfile?.effectiveName ?: "Someone"
                    notificationManager.showMessageNotification(
                        senderId = msg.senderId,
                        senderName = senderName,
                        preview = msg.content
                    )
                }
            } catch (_: Exception) { }
        }
    }

    private fun subscribeToFriends() {
        friendsJob = viewModelScope.launch {
            try {
                repository.subscribeToFriendUpdates().collect { friendship ->
                    loadAll()
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
    }
}
