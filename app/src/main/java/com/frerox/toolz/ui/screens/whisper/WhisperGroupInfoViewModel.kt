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
import com.frerox.toolz.data.whisper.demoteGroupMember
import com.frerox.toolz.data.whisper.disbandGroup
import com.frerox.toolz.data.whisper.transferGroupOwnership
import com.frerox.toolz.data.whisper.updateGroupSettings
import com.frerox.toolz.data.whisper.fetchGroupMessages
import com.frerox.toolz.data.whisper.getFriends
import com.frerox.toolz.data.whisper.openGroupImage
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
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
        /** True when I'm the only member left: disband replaces leave. */
        val canDisband: Boolean = false,
        val isMuted: Boolean = false,
        /** Mute expiry epoch-ms (Long.MAX_VALUE = forever, 0 = not muted). */
        val mutedUntilMs: Long = 0L,
        /** Effective group-notifications toggle (role default until overridden). */
        val notifOn: Boolean = true,
        val pictureBytes: ByteArray? = null,
        /** Shared-photo refs for the gallery (bytes load lazily per thumb). */
        val gallery: List<GalleryItem> = emptyList(),
        val galleryBytes: Map<String, ByteArray> = emptyMap(),
        val isLoading: Boolean = true,
        val isWorking: Boolean = false,
        val left: Boolean = false,
        /** True when the event log carries an unverifiable event (banner). */
        val degraded: Boolean = false,
        val error: String? = null,
    ) {
        // ByteArray breaks data-class equals; identity is by group state instead.
        override fun equals(other: Any?): Boolean = other is UiState &&
            membership == other.membership && members == other.members &&
            pending == other.pending && myId == other.myId && amOwner == other.amOwner &&
            canDisband == other.canDisband &&
            isMuted == other.isMuted && mutedUntilMs == other.mutedUntilMs && notifOn == other.notifOn &&
            isLoading == other.isLoading && isWorking == other.isWorking &&
            left == other.left && degraded == other.degraded && error == other.error
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
                val muteKey = WhisperGroupChatViewModel.muteKey(groupId)
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
                        canDisband = m.members.size <= 1 && m.isMember(me),
                        mutedUntilMs = mutePrefs.mutedUntilMs(muteKey),
                        notifOn = notifPrefs.isEnabled(
                            groupId,
                            (repository.cachedGroups().firstOrNull { it.id == groupId }?.createdBy == me) ||
                                m.isAdmin(me),
                        ),
                        isLoading = false,
                        isWorking = false,
                        degraded = m.degraded,
                    )
                }
                loadPicture()
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, isWorking = false, error = err(e)) }
            }
        }
    }

    private fun work(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            block().onSuccess { load() }.onFailure { e ->
                _uiState.update { it.copy(isWorking = false, error = err(e)) }
            }
        }
    }

    private fun err(e: Throwable?): String =
        com.frerox.toolz.data.whisper.groupErrorText(appContext, e)

    fun addMember(userId: String) = work({ repository.inviteGroupMember(groupId, userId) })

    /** Invites several friends in one go (single reload + single summary error). */
    fun addMembers(userIds: List<String>) {
        val ids = userIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            var failed = 0
            ids.forEach { uid ->
                if (repository.inviteGroupMember(groupId, uid).isFailure) failed++
            }
            if (failed > 0) {
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        error = if (failed == ids.size) "Couldn't invite anyone. Check your connection and retry."
                        else "Couldn't invite $failed of ${ids.size} friend(s).",
                    )
                }
                load()
            } else {
                load()
            }
        }
    }
    fun removeMember(userId: String) = work({ repository.removeGroupMember(groupId, userId) })
    fun promoteMember(userId: String) = work({ repository.promoteGroupMember(groupId, userId) })
    fun rename(name: String) = work({ repository.renameGroup(groupId, name) })
    fun blockMember(userId: String) = work({ repository.blockUser(userId) })
    fun cancelInvite(userId: String) = work({ repository.cancelGroupInvite(groupId, userId) })
    fun demoteMember(userId: String) = work({ repository.demoteGroupMember(groupId, userId) })
    fun transferOwnership(userId: String) = work({ repository.transferGroupOwnership(groupId, userId) })

    /** Admin-only send toggle and/or description (either may be null = unchanged). */
    fun saveSettings(adminOnlySend: Boolean? = null, description: String? = null) =
        work({ repository.updateGroupSettings(groupId, adminOnlySend, description) })

    /** Report = block the member 1:1-wide, then leave the group. */
    fun reportMember(userId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            // Fail-closed BEFORE blocking: a last-admin leave would fail after
            // the block already landed, stranding a block with no exit.
            val m = repository.syncGroup(groupId).getOrNull()
            val me = repository.myId
            if (m != null && m.isAdmin(me) && m.admins.size <= 1) {
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        error = appContext.getString(com.frerox.toolz.R.string.st_Whisper_Groups_ErrLastAdmin),
                    )
                }
                return@launch
            }
            repository.blockUser(userId)
                .onSuccess {
                    repository.leaveGroup(groupId)
                        .onSuccess { _uiState.update { it.copy(isWorking = false, left = true) } }
                        .onFailure { e -> _uiState.update { it.copy(isWorking = false, error = err(e)) } }
                }
                .onFailure { e -> _uiState.update { it.copy(isWorking = false, error = err(e)) } }
        }
    }

    fun setPicture(bytes: ByteArray, mime: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            repository.setGroupPicture(groupId, bytes, mime)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(isWorking = false, error = err(e)) } }
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
                .onFailure { e -> _uiState.update { it.copy(isWorking = false, error = err(e)) } }
        }
    }

    /** Deletes the whole group (last-member-only, server-enforced). */
    fun disband() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            repository.disbandGroup(groupId)
                .onSuccess { _uiState.update { it.copy(isWorking = false, left = true) } }
                .onFailure { e -> _uiState.update { it.copy(isWorking = false, error = err(e)) } }
        }
    }

    fun toggleMute() {
        val key = WhisperGroupChatViewModel.muteKey(groupId)
        if (mutePrefs.isMuted(key)) mutePrefs.unmuteUser(key) else mutePrefs.muteUser(key)
        _uiState.update { it.copy(isMuted = mutePrefs.isMuted(key), mutedUntilMs = mutePrefs.mutedUntilMs(key)) }
    }

    /** Mutes for a duration (Long.MAX_VALUE = forever). */
    fun muteFor(durationMs: Long) {
        val key = WhisperGroupChatViewModel.muteKey(groupId)
        val until = if (durationMs == Long.MAX_VALUE) Long.MAX_VALUE else System.currentTimeMillis() + durationMs
        mutePrefs.muteUser(key, until)
        _uiState.update { it.copy(isMuted = true, mutedUntilMs = until) }
    }

    data class GalleryItem(
        val rowId: String,
        val ref: com.frerox.toolz.data.whisper.GroupImageRef,
        val caption: String,
    )

    /** Loads shared-photo refs (newest 100 rows); bytes stay lazy per thumb. */
    fun loadGallery() {
        viewModelScope.launch {
            val items = repository.fetchGroupMessages(groupId, 100).getOrNull().orEmpty()
                .filter { it.image != null }
                .map { GalleryItem(rowId = it.id, ref = it.image!!, caption = it.body) }
            _uiState.update { it.copy(gallery = items) }
        }
    }

    fun loadGalleryImage(item: GalleryItem) {
        if (_uiState.value.galleryBytes[item.rowId] != null) return
        viewModelScope.launch {
            repository.openGroupImage(item.ref, "gmsg:${item.rowId}").getOrNull()?.let { bytes ->
                _uiState.update { it.copy(galleryBytes = it.galleryBytes + (item.rowId to bytes)) }
            }
        }
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
