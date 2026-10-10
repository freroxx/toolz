/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.whisper.WhisperGroupChatMessage
import com.frerox.toolz.data.whisper.WhisperGroupMembership
import com.frerox.toolz.data.whisper.WhisperMutePreferences
import com.frerox.toolz.data.whisper.WhisperNotificationManager
import com.frerox.toolz.data.whisper.WhisperRepository
import com.frerox.toolz.data.whisper.fetchGroupMessages
import com.frerox.toolz.data.whisper.groupsEnabled
import com.frerox.toolz.data.whisper.observeGroupLog
import com.frerox.toolz.data.whisper.sendGroupMessage
import com.frerox.toolz.data.whisper.syncGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase-2 groups: one group conversation (messages + send + mute).
 * Every load re-verifies the event log; every send seals fresh per member.
 */
@HiltViewModel
class WhisperGroupChatViewModel @Inject constructor(
    private val repository: WhisperRepository,
    private val mutePrefs: WhisperMutePreferences,
    private val notificationManager: WhisperNotificationManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val groupId: String = savedStateHandle.get<String>("groupId").orEmpty()

    data class UiState(
        val membership: WhisperGroupMembership? = null,
        val messages: List<WhisperGroupChatMessage> = emptyList(),
        val isLoading: Boolean = true,
        val isSending: Boolean = false,
        val isMuted: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState(isMuted = mutePrefs.isMuted(muteKey(groupId))))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var logWatchJob: kotlinx.coroutines.Job? = null

    init {
        if (groupsEnabled() && groupId.isNotBlank()) {
            // Open-chat suppression id (mirrors 1:1 currentChatId).
            notificationManager.currentChatId = "group:$groupId"
            load()
            watchLog()
        }
    }

    /** Live refresh on new rows (messages + membership events); pings stay with the section watcher. */
    private fun watchLog() {
        logWatchJob?.cancel()
        logWatchJob = viewModelScope.launch {
            runCatching {
                repository.observeGroupLog(groupId).collect { load() }
            }
        }
    }

    override fun onCleared() {
        logWatchJob?.cancel()
        if (notificationManager.currentChatId == "group:$groupId") {
            notificationManager.currentChatId = null
        }
        super.onCleared()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val membership = repository.syncGroup(groupId)
            membership.onSuccess { m ->
                val messages = repository.fetchGroupMessages(groupId)
                messages.onSuccess { list ->
                    _uiState.update { it.copy(membership = m, messages = list, isLoading = false) }
                }.onFailure { e ->
                    _uiState.update { it.copy(membership = m, isLoading = false, error = e.message) }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun send(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            repository.sendGroupMessage(groupId, text)
                .onSuccess { load() }
                .onFailure { e ->
                    _uiState.update { it.copy(isSending = false, error = e.message) }
                }
        }
    }

    fun toggleMute() {
        val key = muteKey(groupId)
        if (mutePrefs.isMuted(key)) mutePrefs.unmuteUser(key) else mutePrefs.muteUser(key)
        _uiState.update { it.copy(isMuted = mutePrefs.isMuted(key)) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        /** Group mutes reuse the 1:1 mute store under a reserved namespace. */
        fun muteKey(groupId: String) = "group:$groupId"
    }
}
