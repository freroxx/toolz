/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.whisper.WhisperGroupEntity
import com.frerox.toolz.data.whisper.WhisperMutePreferences
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.data.whisper.WhisperRepository
import com.frerox.toolz.data.whisper.cachedGroupMembers
import com.frerox.toolz.data.whisper.cachedGroups
import com.frerox.toolz.data.whisper.createGroup
import com.frerox.toolz.data.whisper.getFriends
import com.frerox.toolz.data.whisper.groupsEnabled
import com.frerox.toolz.data.whisper.syncGroups
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase-2 groups: list + create state for the Chats-tab section.
 * Unreachable with the flag off (the section never composes).
 */
@HiltViewModel
class WhisperGroupsViewModel @Inject constructor(
    private val repository: WhisperRepository,
    mutePrefs: WhisperMutePreferences,
) : ViewModel() {

    data class UiState(
        val groups: List<WhisperGroupEntity> = emptyList(),
        val memberCounts: Map<String, Int> = emptyMap(),
        val friends: List<WhisperProfile> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        /** Set on successful create so the UI can navigate once. */
        val createdGroupId: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Muted group ids (the mute store is shared with 1:1 under `group:` keys). */
    val mutedGroupIds: StateFlow<Set<String>> = mutePrefs.mutedUsers
        .map { set -> set.filter { it.startsWith("group:") }.map { it.removePrefix("group:") }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init {
        if (groupsEnabled()) load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // Cached rows render instantly; the verified sync replaces them.
            _uiState.update { it.copy(groups = repository.cachedGroups()) }
            val friends = repository.getFriends().getOrNull().orEmpty()
            val synced = repository.syncGroups()
            synced.onSuccess {
                val fresh = repository.cachedGroups().sortedByDescending { g -> g.updatedAtMs }
                val counts = fresh.associate { g ->
                    g.id to repository.cachedGroupMembers(g.id).size
                }
                _uiState.update {
                    it.copy(
                        groups = fresh,
                        memberCounts = counts,
                        friends = friends,
                        isLoading = false,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        friends = friends,
                        isLoading = false,
                        error = if (it.groups.isEmpty()) e.message else null,
                    )
                }
            }
        }
    }

    fun create(name: String, memberIds: List<String>, inviteAdminsOnly: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.createGroup(name, memberIds, inviteAdminsOnly)
                .onSuccess { groupId ->
                    load()
                    _uiState.update { it.copy(createdGroupId = groupId) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun consumeCreated() {
        _uiState.update { it.copy(createdGroupId = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
