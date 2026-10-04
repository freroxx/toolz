/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.whisper.WhisperGroupMembership
import com.frerox.toolz.data.whisper.WhisperMutePreferences
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.data.whisper.WhisperRepository
import com.frerox.toolz.data.whisper.addGroupMember
import com.frerox.toolz.data.whisper.blockUser
import com.frerox.toolz.data.whisper.getFriends
import com.frerox.toolz.data.whisper.groupsEnabled
import com.frerox.toolz.data.whisper.leaveGroup
import com.frerox.toolz.data.whisper.promoteGroupMember
import com.frerox.toolz.data.whisper.removeGroupMember
import com.frerox.toolz.data.whisper.renameGroup
import com.frerox.toolz.data.whisper.syncGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase-2 groups: group info + admin controls (members, roles, invites,
 * rename, mute, leave, block-a-member). Every op re-syncs the verified log.
 */
@HiltViewModel
class WhisperGroupInfoViewModel @Inject constructor(
    private val repository: WhisperRepository,
    private val mutePrefs: WhisperMutePreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val groupId: String = savedStateHandle.get<String>("groupId").orEmpty()

    data class MemberEntry(
        val userId: String,
        val name: String,
        val role: String,
        val isMe: Boolean,
    )

    data class UiState(
        val membership: WhisperGroupMembership? = null,
        val members: List<MemberEntry> = emptyList(),
        val myId: String = "",
        val isMuted: Boolean = false,
        val isLoading: Boolean = true,
        val isWorking: Boolean = false,
        val left: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(
        UiState(isMuted = mutePrefs.isMuted(WhisperGroupChatViewModel.muteKey(groupId))),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        if (groupsEnabled() && groupId.isNotBlank()) load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val me = repository.myId
            val membership = repository.syncGroup(groupId)
            membership.onSuccess { m ->
                val names = repository.getFriends().getOrNull().orEmpty()
                    .associate { it.id to it.effectiveName }
                _uiState.update {
                    it.copy(
                        membership = m,
                        members = m.members.entries.map { (uid, role) ->
                            MemberEntry(
                                userId = uid,
                                name = if (uid == me) "You" else names[uid] ?: "Member",
                                role = role.wire,
                                isMe = uid == me,
                            )
                        }.sortedWith(compareBy({ it.role != "admin" }, { it.name })),
                        myId = me,
                        isLoading = false,
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun work(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            block().onSuccess { load() }.onFailure { e ->
                _uiState.update { it.copy(isWorking = false, error = e.message) }
            }
        }
    }

    fun addMember(userId: String) = work({ repository.addGroupMember(groupId, userId) })
    fun removeMember(userId: String) = work({ repository.removeGroupMember(groupId, userId) })
    fun promoteMember(userId: String) = work({ repository.promoteGroupMember(groupId, userId) })
    fun rename(name: String) = work({ repository.renameGroup(groupId, name) })
    fun blockMember(userId: String) = work({ repository.blockUser(userId) })

    fun leave() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            repository.leaveGroup(groupId)
                .onSuccess { _uiState.update { it.copy(isWorking = false, left = true) } }
                .onFailure { e -> _uiState.update { it.copy(isWorking = false, error = e.message) } }
        }
    }

    fun toggleMute() {
        val key = WhisperGroupChatViewModel.muteKey(groupId)
        if (mutePrefs.isMuted(key)) mutePrefs.unmuteUser(key) else mutePrefs.muteUser(key)
        _uiState.update { it.copy(isMuted = mutePrefs.isMuted(key)) }
    }

    fun loadFriends(onResult: (List<WhisperProfile>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getFriends().getOrNull().orEmpty())
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
