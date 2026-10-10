/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.whisper.WhisperGroupMembership
import com.frerox.toolz.data.whisper.WhisperGroupNotifPrefs
import com.frerox.toolz.data.whisper.WhisperMutePreferences
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.data.whisper.WhisperRepository
import com.frerox.toolz.data.whisper.inviteGroupMember
import com.frerox.toolz.data.whisper.blockUser
import com.frerox.toolz.data.whisper.cachedGroups
import com.frerox.toolz.data.whisper.cancelGroupInvite
import com.frerox.toolz.data.whisper.getFriends
import com.frerox.toolz.data.whisper.groupsEnabled
import com.frerox.toolz.data.whisper.leaveGroup
import com.frerox.toolz.data.whisper.openGroupPicture
import com.frerox.toolz.data.whisper.promoteGroupMember
import com.frerox.toolz.data.whisper.removeGroupMember
import com.frerox.toolz.data.whisper.renameGroup
import com.frerox.toolz.data.whisper.setGroupPicture
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
    private val notifPrefs: WhisperGroupNotifPrefs,
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
        val pending: List<MemberEntry> = emptyList(),
        val myId: String = "",
        val amOwner: Boolean = false,
        val isMuted: Boolean = false,
        /** Effective group-notifications toggle (role default until overridden). */
        val notifOn: Boolean = true,
        val pictureBytes: ByteArray? = null,
        val isLoading: Boolean = true,
        val isWorking: Boolean = false,
        val left: Boolean = false,
        val error: String? = null,
    ) {
        // ByteArray breaks data-class equals; identity is by group state instead.
        override fun equals(other: Any?): Boolean = other is UiState &&
            membership == other.membership && members == other.members &&
            pending == other.pending && myId == other.myId && amOwner == other.amOwner &&
            isMuted == other.isMuted && notifOn == other.notifOn &&
            isLoading == other.isLoading && isWorking == other.isWorking &&
            left == other.left && error == other.error
        override fun hashCode(): Int = membership.hashCode()
    }

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
                        pending = m.pendingInvites.sorted().map { uid ->
                            MemberEntry(
                                userId = uid,
                                name = names[uid] ?: "Member",
                                role = "invited",
                                isMe = false,
                            )
                        },
                        myId = me,
                        amOwner = repository.cachedGroups().firstOrNull { it.id == groupId }?.createdBy == me,
                        notifOn = notifPrefs.isEnabled(
                            groupId,
                            (repository.cachedGroups().firstOrNull { it.id == groupId }?.createdBy == me) ||
                                m.isAdmin(me),
                        ),
                        isLoading = false,
                    )
                }
                loadPicture()
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

    fun addMember(userId: String) = work({ repository.inviteGroupMember(groupId, userId) })
    fun removeMember(userId: String) = work({ repository.removeGroupMember(groupId, userId) })
    fun promoteMember(userId: String) = work({ repository.promoteGroupMember(groupId, userId) })
    fun rename(name: String) = work({ repository.renameGroup(groupId, name) })
    fun blockMember(userId: String) = work({ repository.blockUser(userId) })
    fun cancelInvite(userId: String) = work({ repository.cancelGroupInvite(groupId, userId) })

    fun setPicture(bytes: ByteArray, mime: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            repository.setGroupPicture(groupId, bytes, mime)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(isWorking = false, error = e.message) } }
        }
    }

    private fun loadPicture() {
        viewModelScope.launch {
            val bytes = repository.openGroupPicture(groupId).getOrNull()
            _uiState.update { it.copy(pictureBytes = bytes) }
        }
    }

    fun toggleNotif() {
        val cur = _uiState.value
        val next = !cur.notifOn
        notifPrefs.setEnabled(groupId, next)
        _uiState.update { it.copy(notifOn = next) }
    }

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
