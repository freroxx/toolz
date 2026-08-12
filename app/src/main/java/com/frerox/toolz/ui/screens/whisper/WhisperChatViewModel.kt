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

import androidx.lifecycle.SavedStateHandle
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
class WhisperChatViewModel @Inject constructor(
    private val repository: WhisperRepository,
    private val authManager: WhisperAuthManager,
    private val crypto: WhisperCrypto,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val otherUserId: String = checkNotNull(savedStateHandle["otherUserId"])
    val myUserId: String get() = authManager.currentUserId ?: ""

    private val _uiState = MutableStateFlow(WhisperChatUiState())
    val uiState: StateFlow<WhisperChatUiState> = _uiState.asStateFlow()

    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText.asStateFlow()

    private var partnerPublicKey: String? = null
    private var realtimeJob: Job? = null

    init {
        loadInitialData()
        subscribeToMessages()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load other user's profile
            repository.getProfile(otherUserId)
                .onSuccess { profile ->
                    partnerPublicKey = profile.publicKey
                    _uiState.update { it.copy(otherUser = profile) }
                }

            // Load friendship status
            repository.getFriendshipStatus(otherUserId)
                .onSuccess { (status, friendship) ->
                    _uiState.update {
                        it.copy(
                            friendStatus = status,
                            iAmRequester = friendship?.iRequested(myUserId) ?: false,
                        )
                    }
                }

            // Load messages
            loadMessages()
        }
    }

    fun loadMessages() {
        viewModelScope.launch {
            repository.getMessages(otherUserId)
                .onSuccess { messages ->
                    _uiState.update { it.copy(messages = messages, isLoading = false) }
                    // Mark unread messages as read
                    repository.markMessagesAsRead(otherUserId)
                }
                .onFailure { err ->
                    _uiState.update { it.copy(error = err.message, isLoading = false) }
                }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        
        val originalText = content
        _draftText.value = ""
        val trimmedContent = content.trim()
        val optimisticMsg = WhisperMessage(
            id = "pending_${System.currentTimeMillis()}",
            senderId = myUserId,
            receiverId = otherUserId,
            content = trimmedContent
        )

        _uiState.update { state ->
            state.copy(messages = state.messages + optimisticMsg)
        }

        viewModelScope.launch {
            repository.sendMessage(otherUserId, trimmedContent)
                .onSuccess { newMsg ->
                    _uiState.update { state ->
                        val filtered = state.messages.filter { it.id != optimisticMsg.id }
                        state.copy(messages = filtered + newMsg)
                    }
                }
                .onFailure { err ->
                    _draftText.value = originalText
                    _uiState.update { state ->
                        val filtered = state.messages.filter { it.id != optimisticMsg.id }
                        state.copy(error = err.message, messages = filtered)
                    }
                }
        }
    }

    fun sendFriendRequest() {
        viewModelScope.launch {
            repository.sendFriendRequest(otherUserId)
                .onSuccess {
                    _uiState.update { it.copy(friendStatus = FriendStatus.PENDING, iAmRequester = true) }
                }
                .onFailure { _uiState.update { s -> s.copy(error = it.message) } }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun updateDraft(text: String) {
        _draftText.value = text
    }

    private fun subscribeToMessages() {
        realtimeJob = viewModelScope.launch {
            try {
                repository.subscribeToIncomingMessages().collect { newMsg ->
                    if (newMsg.senderId == otherUserId) {
                        val decryptedContent = if (newMsg.contentIv != null && partnerPublicKey != null) {
                            crypto.decryptMessage(newMsg.content, newMsg.contentIv, partnerPublicKey!!)
                        } else newMsg.content
                        
                        _uiState.update { state ->
                            val updatedMessages = state.messages + newMsg.copy(content = decryptedContent)
                            state.copy(messages = updatedMessages)
                        }
                        repository.markMessagesAsRead(otherUserId)
                    }
                }
            } catch (_: Exception) { /* handled by Realtime reconnect */ }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
    }
}
