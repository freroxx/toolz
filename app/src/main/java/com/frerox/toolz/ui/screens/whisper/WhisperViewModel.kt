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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.whisper.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhisperViewModel @Inject constructor(
    private val repository: WhisperRepository,
    private val authManager: WhisperAuthManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhisperUiState())
    val uiState: StateFlow<WhisperUiState> = _uiState.asStateFlow()

    private var realtimeJob: Job? = null

    init {
        loadAll()
        subscribeToMessages()
    }

    fun loadAll() {
        loadProfile()
        loadConversations()
        loadFriends()
    }

    fun loadProfile() {
        viewModelScope.launch {
            repository.getMyProfile()
                .onSuccess { profile ->
                    _uiState.update { it.copy(currentProfile = profile) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = err.message) }
                }
        }
    }

    fun loadConversations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getConversations()
                .onSuccess { convos ->
                    _uiState.update { it.copy(conversations = convos, isLoading = false) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = err.message, isLoading = false) }
                }
        }
    }

    fun loadFriends() {
        viewModelScope.launch {
            repository.getFriends()
                .onSuccess { friends ->
                    _uiState.update { it.copy(friends = friends) }
                }
            repository.getPendingIncoming()
                .onSuccess { pending ->
                    _uiState.update { it.copy(pendingIncoming = pending) }
                }
            repository.getPendingOutgoing()
                .onSuccess { pending ->
                    _uiState.update { it.copy(pendingOutgoing = pending) }
                }
        }
    }

    fun searchProfiles(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            repository.searchProfiles(query)
                .onSuccess { results ->
                    _uiState.update { it.copy(searchResults = results) }
                }
        }
    }

    fun sendFriendRequest(targetUserId: String) {
        viewModelScope.launch {
            repository.sendFriendRequest(targetUserId)
                .onSuccess { loadFriends() }
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    fun acceptFriendRequest(friendshipId: String) {
        viewModelScope.launch {
            repository.acceptFriendRequest(friendshipId)
                .onSuccess { loadFriends() }
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    fun declineFriendRequest(friendshipId: String) {
        viewModelScope.launch {
            repository.deleteFriendship(friendshipId)
                .onSuccess { loadFriends() }
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        isPrivate: Boolean,
    ) {
        viewModelScope.launch {
            val update = WhisperProfileUpdate(
                username = username.takeIf { it.isNotBlank() },
                displayName = displayName.takeIf { it.isNotBlank() },
                bio = bio.takeIf { it.isNotBlank() },
                isPrivate = isPrivate,
            )
            repository.updateProfile(update)
                .onSuccess { loadProfile() }
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    fun uploadAvatar(imageBytes: ByteArray, mimeType: String) {
        viewModelScope.launch {
            repository.uploadAvatar(imageBytes, mimeType)
                .onSuccess { url ->
                    repository.updateProfile(WhisperProfileUpdate(avatarUrl = url))
                        .onSuccess { loadProfile() }
                }
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun subscribeToMessages() {
        realtimeJob = viewModelScope.launch {
            try {
                repository.subscribeToIncomingMessages().collect {
                    // Refresh conversations to show new message
                    loadConversations()
                }
            } catch (_: Exception) { /* ignore; reconnect handled by Realtime */ }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
    }
}
