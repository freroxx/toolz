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
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.whisper.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WhisperUserProfileUiState(
    val isLoading: Boolean = false,
    val profile: WhisperProfile? = null,
    val loadFailed: Boolean = false,
    val friendshipStatus: FriendStatus = FriendStatus.NONE,
    val friendshipRecord: WhisperFriendship? = null,
    val keyTrust: KeyTrustInfo? = null,
    val error: UiText? = null,
)

@HiltViewModel
class WhisperUserProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WhisperRepository,
    private val authManager: WhisperAuthManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val targetUserId: String = checkNotNull(savedStateHandle["userId"])

    val screenshotBypassEnabled: StateFlow<Boolean> = settingsRepository.whisperScreenshotBypass
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setScreenshotBypass(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWhisperScreenshotBypass(enabled)
        }
    }

    private val _uiState = MutableStateFlow(WhisperUserProfileUiState())
    val uiState: StateFlow<WhisperUserProfileUiState> = _uiState.asStateFlow()

    init {
        loadData()
        // V6-R2 (review): consume the repository's key-change signal for THIS profile so
        // an auto-accepted rotation refreshes the trust card live (the flow previously
        // had zero collectors anywhere in the app).
        viewModelScope.launch {
            repository.receiveKeyChanged.collect { changedUserId ->
                if (changedUserId == targetUserId) loadData()
            }
        }
    }

    private fun handleError(err: Throwable, context: String) {
        // V2-FIX (CE guard): cancellation is control flow, not an error — never surface
        // it as a toast or trigger a sign-out because the screen was left mid-load.
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

    private var loadDataJob: Job? = null

    // V2-FIX (coalesce): set when loadData() arrives while a load is already running;
    // honored right after completion instead of being silently dropped.
    @Volatile
    private var loadRequestedWhileRunning = false

    fun loadData() {
        // V2-FIX (coalesce): job-based in-flight guard replaces the isLoading check —
        // re-entrant calls are coalesced and re-run once after the active load settles,
        // so a state change landing mid-load is never lost (double-taps included).
        if (loadDataJob?.isActive == true) {
            loadRequestedWhileRunning = true
            return
        }
        loadDataJob = viewModelScope.launch {
            performLoad()
            if (loadRequestedWhileRunning) {
                loadRequestedWhileRunning = false
                performLoad()
            }
        }
    }

    private suspend fun performLoad() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        try {
            // V2-FIX (parallelize): profile, key-trust and friendship lookups are mutually
            // independent — run them concurrently; the key-trust read stays soft (only a
            // real failure there is swallowed, never one that blocks the other branches).
            coroutineScope {
                val profileDeferred = async {
                    repository.getProfile(targetUserId, forceRefresh = true)
                        .onSuccess { profile ->
                            _uiState.update { it.copy(profile = profile) }
                        }
                        .onFailure { err ->
                            // A real 404 means "profile not found"; anything else is a load
                            // failure that deserves a retry instead of a dead end.
                            if (!WhisperErrorMapper.isNotFound(err)) {
                                _uiState.update { it.copy(loadFailed = true) }
                                handleError(err, "getProfile")
                            }
                        }
                    Unit
                }
                val keyTrustDeferred = async {
                    // Key trust is a soft-read: a failure here must not block friendship status.
                    val info = runCatching { repository.getKeyTrustInfo(targetUserId) }
                        .onFailure { err ->
                            // V2-FIX (CE guard): rethrow so runCatching can't eat cancellation.
                            if (err is kotlinx.coroutines.CancellationException) throw err
                        }
                        .getOrNull()
                    _uiState.update { it.copy(keyTrust = info) }
                }
                val friendshipDeferred = async {
                    repository.getFriendshipStatus(targetUserId)
                        .onSuccess { (status, record) ->
                            _uiState.update { it.copy(friendshipStatus = status, friendshipRecord = record) }
                        }
                        .onFailure { err -> handleError(err, "getFriendshipStatus") }
                }
                profileDeferred.await()
                keyTrustDeferred.await()
                friendshipDeferred.await()
            }
        } finally {
            // Single terminal write once every branch has settled (success OR failure).
            _uiState.update { it.copy(isLoading = false) }
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

    fun verifyKey() {
        viewModelScope.launch {
            runCatching { repository.verifyUserKey(targetUserId) }
                .onSuccess { verified ->
                    if (verified) {
                        loadData()
                    } else {
                        // No exception thrown but the key could not be verified (e.g. profile
                        // missing); surface it instead of silently succeeding.
                        _uiState.update { it.copy(error = UiText.StringResource(R.string.st_Whisper_Error_KeyVerifyFailed)) }
                    }
                }
                .onFailure { err ->
                    // V2-FIX (CE guard): rethrow so cancellation propagates, not maps to a toast.
                    if (err is kotlinx.coroutines.CancellationException) throw err
                    handleError(err, "verifyKey")
                }
        }
    }

    fun acceptNewKey() {
        viewModelScope.launch {
            runCatching { repository.acceptNewKey(targetUserId) }
                .onSuccess { accepted ->
                    if (accepted) {
                        loadData()
                    } else {
                        _uiState.update { it.copy(error = UiText.StringResource(R.string.st_Whisper_Error_KeyAcceptFailed)) }
                    }
                }
                .onFailure { err ->
                    // V2-FIX (CE guard): rethrow so cancellation propagates, not maps to a toast.
                    if (err is kotlinx.coroutines.CancellationException) throw err
                    handleError(err, "acceptNewKey")
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
