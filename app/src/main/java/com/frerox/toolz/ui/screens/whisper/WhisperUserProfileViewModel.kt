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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WhisperUserProfileUiState(
    val isLoading: Boolean = false,
    val profile: WhisperProfile? = null,
    val friendshipStatus: FriendStatus = FriendStatus.NONE,
    val friendshipRecord: WhisperFriendship? = null,
    val error: String? = null,
)

@HiltViewModel
class WhisperUserProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WhisperRepository,
    private val authManager: WhisperAuthManager,
) : ViewModel() {

    val targetUserId: String = checkNotNull(savedStateHandle["userId"])

    private val _uiState = MutableStateFlow(WhisperUserProfileUiState())
    val uiState: StateFlow<WhisperUserProfileUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun handleError(err: Throwable, context: String) {
        val mapped = WhisperErrorMapper.map(err, context)
        if (mapped == WhisperErrorMapper.SESSION_EXPIRED_SENTINEL || WhisperErrorMapper.isSessionExpired(err)) {
            viewModelScope.launch {
                authManager.signOut()
            }
        } else {
            _uiState.update { it.copy(error = mapped) }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            repository.getProfile(targetUserId, forceRefresh = true)
                .onSuccess { profile ->
                    _uiState.update { it.copy(profile = profile) }
                }
                .onFailure { err ->
                    handleError(err, "getProfile")
                }

            repository.getFriendshipStatus(targetUserId)
                .onSuccess { (status, record) ->
                    _uiState.update { it.copy(friendshipStatus = status, friendshipRecord = record, isLoading = false) }
                }
                .onFailure { err ->
                    _uiState.update { s -> s.copy(isLoading = false) }
                    handleError(err, "getFriendshipStatus")
                }
        }
    }

    fun sendFriendRequest() {
        viewModelScope.launch {
            repository.sendFriendRequest(targetUserId)
                .onSuccess { loadData() }
                .onFailure { err -> handleError(err, "sendFriendRequest") }
        }
    }

    fun acceptFriendRequest() {
        val recordId = uiState.value.friendshipRecord?.id ?: return
        viewModelScope.launch {
            repository.acceptFriendRequest(recordId)
                .onSuccess { loadData() }
                .onFailure { err -> handleError(err, "acceptFriendRequest") }
        }
    }

    fun removeFriendship() {
        val recordId = uiState.value.friendshipRecord?.id ?: return
        viewModelScope.launch {
            repository.deleteFriendship(recordId)
                .onSuccess { loadData() }
                .onFailure { err -> handleError(err, "deleteFriendship") }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
