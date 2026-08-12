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
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhisperUiState())
    val uiState: StateFlow<WhisperUiState> = _uiState.asStateFlow()

    val isAuthenticated: StateFlow<Boolean?> = authManager.isAuthenticated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var realtimeJob: Job? = null

    init {
        loadAll()
        subscribeToMessages()
    }

    fun loadAll(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) _uiState.update { it.copy(isRefreshing = true) }
            else _uiState.update { it.copy(isLoading = true) }

            coroutineScope {
                val profileDeferred = async {
                    repository.getMyProfile()
                        .onSuccess { profile -> _uiState.update { it.copy(currentProfile = profile) } }
                        .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
                }
                val convosDeferred = async {
                    repository.getConversations()
                        .onSuccess { convos -> _uiState.update { it.copy(conversations = convos) } }
                        .onFailure { err -> _uiState.update { it.copy(error = err.message) } }
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
        }
    }

    fun sendFriendRequest(targetUserId: String) {
        viewModelScope.launch {
            repository.sendFriendRequest(targetUserId)
                .onSuccess { loadAll() }
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    fun acceptFriendRequest(friendshipId: String) {
        viewModelScope.launch {
            repository.acceptFriendRequest(friendshipId)
                .onSuccess { loadAll() }
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    fun declineFriendRequest(friendshipId: String) {
        viewModelScope.launch {
            repository.deleteFriendship(friendshipId)
                .onSuccess { loadAll() }
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
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
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    fun uploadAvatar(imageBytes: ByteArray, mimeType: String) {
        viewModelScope.launch {
            repository.uploadAvatar(imageBytes, mimeType)
                .onSuccess { url ->
                    repository.updateProfile(WhisperProfileUpdate(avatarUrl = url))
                        .onSuccess { loadAll() }
                }
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    fun signOut() {
        viewModelScope.launch { authManager.signOut() }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun subscribeToMessages() {
        realtimeJob = viewModelScope.launch {
            try {
                repository.subscribeToIncomingMessages().collect { loadAll() }
            } catch (_: Exception) { }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
    }
}
