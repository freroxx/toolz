/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.whisper.GroupInviteInfo
import com.frerox.toolz.data.whisper.GroupNotifScope
import com.frerox.toolz.data.whisper.WhisperGroupEntity
import com.frerox.toolz.data.whisper.WhisperGroupMembership
import com.frerox.toolz.data.whisper.WhisperGroupNotifPrefs
import com.frerox.toolz.data.whisper.WhisperGroupNotifTemplates
import com.frerox.toolz.data.whisper.WhisperMutePreferences
import com.frerox.toolz.data.whisper.WhisperNotificationManager
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.data.whisper.WhisperRepository
import com.frerox.toolz.data.whisper.cachedGroupMembers
import com.frerox.toolz.data.whisper.cachedGroups
import com.frerox.toolz.data.whisper.createGroup
import com.frerox.toolz.data.whisper.declineGroupInvite
import com.frerox.toolz.data.whisper.flushGroupOutbox
import com.frerox.toolz.data.whisper.getFriends
import com.frerox.toolz.data.whisper.groupsEnabled
import com.frerox.toolz.data.whisper.joinGroup
import com.frerox.toolz.data.whisper.myGroupInvites
import com.frerox.toolz.data.whisper.observeGroupLog
import com.frerox.toolz.data.whisper.observeGroupMessagesLive
import com.frerox.toolz.data.whisper.observeMyGroupInvites
import com.frerox.toolz.data.whisper.setGroupPicture
import com.frerox.toolz.data.whisper.shouldNotifyGroupEvent
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
    private val notificationManager: WhisperNotificationManager,
    private val notifPrefs: WhisperGroupNotifPrefs,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    data class UiState(
        val groups: List<WhisperGroupEntity> = emptyList(),
        val memberCounts: Map<String, Int> = emptyMap(),
        val invites: List<GroupInviteInfo> = emptyList(),
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

    // Invite ids already pinged this process (realtime resubscribes must not re-ping).
    private val pingedInviteIds = mutableSetOf<String>()
    private var inviteWatchJob: kotlinx.coroutines.Job? = null
    // Group ids with a join/decline in flight (double-tap guard for card buttons).
    private val _workingInvites = MutableStateFlow<Set<String>>(emptySet())
    val workingInvites: StateFlow<Set<String>> = _workingInvites.asStateFlow()
    // Event ids already seen (join/leave/decline pings fire once per event).
    private val seenEventIds = mutableSetOf<String>()
    private var logWatchJobs: List<kotlinx.coroutines.Job> = emptyList()
    private var lastMemberships: List<WhisperGroupMembership> = emptyList()
    private var lastFriendNames: Map<String, String> = emptyMap()
    private var lastGroups: List<WhisperGroupEntity> = emptyList()

    init {
        if (groupsEnabled()) {
            load()
            watchInvites()
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // Drain queued group sends first so they land before the sync reads.
            runCatching { repository.flushGroupOutbox() }
            // Cached rows render instantly; the verified sync replaces them.
            _uiState.update { it.copy(groups = repository.cachedGroups()) }
            val friends = repository.getFriends().getOrNull().orEmpty()
            val invites = repository.myGroupInvites().getOrNull().orEmpty()
            pingNewInvites(invites)
            val synced = repository.syncGroups()
            synced.onSuccess { memberships ->
                val fresh = repository.cachedGroups().sortedByDescending { g -> g.updatedAtMs }
                val counts = fresh.associate { g ->
                    g.id to repository.cachedGroupMembers(g.id).size
                }
                val freshInvites = repository.myGroupInvites().getOrNull().orEmpty()
                pingNewInvites(freshInvites)
                lastMemberships = memberships
                lastFriendNames = friends.associate { it.id to it.effectiveName }
                lastGroups = fresh
                watchGroupLogs()
                _uiState.update {
                    it.copy(
                        groups = fresh,
                        memberCounts = counts,
                        invites = freshInvites,
                        friends = friends,
                        isLoading = false,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        invites = invites,
                        friends = friends,
                        isLoading = false,
                        error = if (it.groups.isEmpty()) err(e) else null,
                    )
                }
            }
        }
    }

    /** Realtime invite lane: refetch + ping only genuinely new invites. */
    private fun watchInvites() {
        inviteWatchJob?.cancel()
        inviteWatchJob = viewModelScope.launch {
            runCatching {
                repository.observeMyGroupInvites().collect {
                    val fresh = repository.myGroupInvites().getOrNull().orEmpty()
                    pingNewInvites(fresh)
                    _uiState.update { it.copy(invites = fresh) }
                }
            }
        }
    }

    private fun pingNewInvites(invites: List<GroupInviteInfo>) {
        invites.forEach { inv ->
            val key = "${inv.groupId}:${inv.invitedBy}"
            if (pingedInviteIds.add(key)) {
                notificationManager.showGroupInviteNotification(inv.groupId, inv.groupName, inv.invitedByName)
            }
        }
    }

    fun join(groupId: String, onJoined: (String) -> Unit = {}) {
        if (groupId in _workingInvites.value) return
        _workingInvites.update { it + groupId }
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            repository.joinGroup(groupId)
                .onSuccess {
                    load()
                    onJoined(groupId)
                }
                .onFailure { e -> _uiState.update { it.copy(error = err(e)) } }
            _workingInvites.update { it - groupId }
        }
    }

    fun decline(groupId: String) {
        if (groupId in _workingInvites.value) return
        _workingInvites.update { it + groupId }
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            repository.declineGroupInvite(groupId)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(error = err(e)) } }
            _workingInvites.update { it - groupId }
        }
    }

    /** One log watcher per group: join/leave/decline pings for opted-in roles. */
    private fun watchGroupLogs() {
        logWatchJobs.forEach { it.cancel() }
        val me = repository.myId
        if (me.isBlank()) return
        logWatchJobs = lastMemberships.flatMap { m ->
            listOf(
                viewModelScope.launch {
                    runCatching {
                        repository.observeGroupLog(m.groupId).collect { row ->
                            if (!seenEventIds.add("evt:${row.id}")) return@collect
                            onGroupEventRow(m, row, me)
                        }
                    }
                },
                viewModelScope.launch {
                    runCatching {
                        repository.observeGroupMessagesLive(m.groupId).collect { ping ->
                            if (ping.senderId == me) return@collect
                            if (!seenEventIds.add("msg:${ping.clientId}")) return@collect
                            val group = lastGroups.firstOrNull { it.id == m.groupId }
                            notificationManager.showGroupEventNotification(
                                groupId = m.groupId,
                                title = group?.name?.ifBlank { "Group" } ?: "Group",
                                text = appContext.getString(com.frerox.toolz.R.string.st_Whisper_Notif_NewMessage),
                                dedupeKey = "msg:${ping.clientId}",
                            )
                        }
                    }
                },
            )
        }
    }

    private fun onGroupEventRow(
        m: WhisperGroupMembership,
        row: com.frerox.toolz.data.whisper.GroupEventRow,
        me: String,
    ) {
        val scope = when (row.type) {
            "join" -> GroupNotifScope.JOIN
            "leave" -> GroupNotifScope.LEAVE
            "decline" -> GroupNotifScope.DECLINE
            else -> return
        }
        val group = lastGroups.firstOrNull { it.id == m.groupId }
        val amOwner = group?.createdBy == me
        val amOwnerOrAdmin = amOwner || m.isAdmin(me)
        val toggleOn = notifPrefs.isEnabled(m.groupId, amOwnerOrAdmin)
        if (!shouldNotifyGroupEvent(scope, row.actor, me, amOwner, toggleOn)) return
        val actorName = lastFriendNames[row.actor] ?: "Someone"
        val groupName = group?.name?.ifBlank { "Group" } ?: "Group"
        val text = when (scope) {
            GroupNotifScope.JOIN -> WhisperGroupNotifTemplates.joinText(actorName, groupName)
            GroupNotifScope.LEAVE -> WhisperGroupNotifTemplates.leaveText(actorName, groupName)
            GroupNotifScope.DECLINE -> WhisperGroupNotifTemplates.declineText(actorName, groupName)
        }
        notificationManager.showGroupEventNotification(
            groupId = m.groupId,
            title = groupName,
            text = text,
            dedupeKey = "evt:${row.id}",
        )
    }

    override fun onCleared() {
        inviteWatchJob?.cancel()
        logWatchJobs.forEach { it.cancel() }
        super.onCleared()
    }

    fun create(
        name: String,
        memberIds: List<String>,
        inviteAdminsOnly: Boolean,
        pictureBytes: ByteArray? = null,
        pictureMime: String = "image/jpeg",
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val created = repository.createGroup(name, memberIds, inviteAdminsOnly)
            created.onSuccess { groupId ->
                // Picture is best-effort after the group exists (needs the id);
                // a picture failure must not lose the created group.
                if (pictureBytes != null) {
                    repository.setGroupPicture(groupId, pictureBytes, pictureMime)
                        .onFailure { e -> _uiState.update { it.copy(error = err(e)) } }
                }
                load()
                _uiState.update { it.copy(createdGroupId = groupId) }
            }
            created.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = err(e)) }
            }
        }
    }

    fun consumeCreated() {
        _uiState.update { it.copy(createdGroupId = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun err(e: Throwable?): String =
        com.frerox.toolz.data.whisper.groupErrorText(appContext, e)
}
