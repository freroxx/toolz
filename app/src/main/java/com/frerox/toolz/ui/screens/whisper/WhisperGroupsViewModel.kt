/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.whisper.GroupInviteInfo
import com.frerox.toolz.data.whisper.GroupNotifScope
import com.frerox.toolz.data.whisper.WhisperGroupChatMessage
import com.frerox.toolz.data.whisper.WhisperGroupEntity
import com.frerox.toolz.data.whisper.WhisperGroupMembership
import com.frerox.toolz.data.whisper.WhisperGroupNotifPrefs
import com.frerox.toolz.data.whisper.WhisperGroupNotifTemplates
import com.frerox.toolz.data.whisper.WhisperGroupReadStore
import com.frerox.toolz.data.whisper.WhisperMutePreferences
import com.frerox.toolz.data.whisper.WhisperNotificationManager
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.data.whisper.WhisperRepository
import com.frerox.toolz.data.whisper.cachedGroupMembers
import com.frerox.toolz.data.whisper.cachedGroups
import com.frerox.toolz.data.whisper.countGroupUnread
import com.frerox.toolz.data.whisper.createGroup
import com.frerox.toolz.data.whisper.declineGroupInvite
import com.frerox.toolz.data.whisper.fetchGroupMessages
import com.frerox.toolz.data.whisper.flushGroupOutbox
import com.frerox.toolz.data.whisper.getFriends
import com.frerox.toolz.data.whisper.groupsEnabled
import com.frerox.toolz.data.whisper.joinGroup
import com.frerox.toolz.data.whisper.myGroupInvites
import com.frerox.toolz.data.whisper.observeGroupLog
import com.frerox.toolz.data.whisper.observeGroupMessagesLive
import com.frerox.toolz.data.whisper.observeMyGroupInvites
import com.frerox.toolz.data.whisper.openGroupPicture
import com.frerox.toolz.data.whisper.setGroupPicture
import com.frerox.toolz.data.whisper.shouldNotifyGroupEvent
import com.frerox.toolz.data.whisper.syncGroups
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val readStore: WhisperGroupReadStore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    /** Latest-message preview + unread for one group row (all String-safe equals). */
    data class GroupPreview(
        val text: String = "",
        val time: String = "",
        val senderName: String = "",
        val unread: Int = 0,
        val hasImage: Boolean = false,
        val hasPoll: Boolean = false,
        /** True when an unread line mentions me (row shows an @You chip). */
        val mentionedMe: Boolean = false,
    )

    data class UiState(
        val groups: List<WhisperGroupEntity> = emptyList(),
        val memberCounts: Map<String, Int> = emptyMap(),
        val previews: Map<String, GroupPreview> = emptyMap(),
        val invites: List<GroupInviteInfo> = emptyList(),
        val friends: List<WhisperProfile> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
        /** Set on successful create so the UI can navigate once. */
        val createdGroupId: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Decrypted group pictures by group id. ByteArray values defeat data-class
     * equals, so this rides a SEPARATE flow updated only when bytes actually
     * change (content-guarded) — rows recompose on real picture changes only.
     */
    private val _pictures = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val pictures: StateFlow<Map<String, ByteArray>> = _pictures.asStateFlow()

    /** Muted group ids (the mute store is shared with 1:1 under `group:` keys). */
    val mutedGroupIds: StateFlow<Set<String>> = mutePrefs.mutedUsers
        .map { set -> set.filter { it.startsWith("group:") }.map { it.removePrefix("group:") }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // Invite ids already pinged this process (realtime resubscribes must not re-ping).
    private val pingedInviteIds = mutableSetOf<String>()
    private var inviteWatchJob: kotlinx.coroutines.Job? = null
    // Last seen invite keys for retracting withdrawn pings (cancel/join/decline).
    private var lastInviteKeys: Set<String> = emptySet()
    private var lastInviteNames: Map<String, String> = emptyMap()
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
                // Previews + pictures resolve off the main sync path (decrypts).
                refreshPreviews(fresh)
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

    /**
     * Latest-message previews + unread counts + picture thumbs, resolved after
     * the main sync. Per-group work runs concurrently (each needs its own
     * verified sync + frame decrypts); a slow group never head-blocks the
     * rest. Failures stay silent — a row without a preview still opens.
     * Unread counts come from a decrypt-free server count (no 20-cap).
     */
    private fun refreshPreviews(groups: List<WhisperGroupEntity>) {
        viewModelScope.launch {
            val me = repository.myId
            val results = coroutineScope {
                groups.map { g ->
                    async {
                        runCatching {
                            val recent = repository.fetchGroupMessages(g.id, 20).getOrThrow()
                            val mark = readStore.readAt(g.id)
                            val last = recent.lastOrNull()
                            val unread = repository.countGroupUnread(g.id, mark, me)
                            val preview = GroupPreview(
                                text = last?.let { previewText(it) }.orEmpty(),
                                time = last?.createdAt.orEmpty(),
                                senderName = last?.senderName.orEmpty(),
                                unread = if (last == null) 0 else unread,
                                hasImage = last?.image != null,
                                hasPoll = last?.poll != null,
                                mentionedMe = recent.any {
                                    me in it.mentions && it.senderId != me && it.createdAt > mark
                                },
                            )
                            // Picture thumb (content-guarded at merge time).
                            val bytes = repository.openGroupPicture(g.id).getOrNull()
                            Triple(g.id, preview, bytes)
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }
            val previews = results.associate { it.first to it.second }
            _uiState.update { it.copy(previews = previews) }
            val pics = _pictures.value.toMutableMap()
            var picsChanged = false
            results.forEach { (id, _, bytes) ->
                if (bytes != null) {
                    if (pics[id]?.contentEquals(bytes) != true) {
                        pics[id] = bytes
                        picsChanged = true
                    }
                } else if (pics.remove(id) != null) {
                    picsChanged = true
                }
            }
            // Drop thumbs for groups that left the list (evicted/kicked).
            val stalePics = pics.keys - groups.map { it.id }.toSet()
            stalePics.forEach { pics.remove(it).let { picsChanged = true } }
            if (picsChanged) _pictures.value = pics
            Unit
        }
    }

    private fun previewText(m: WhisperGroupChatMessage): String = when {
        m.poll != null -> "📊 ${m.poll.q.ifBlank { "Poll" }}"
        m.image != null && m.body.isNotBlank() -> "📷 ${m.body}"
        m.image != null -> "📷 Photo"
        m.vote != null -> "Voted"
        else -> m.body
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
        val freshKeys = invites.map { "${it.groupId}:${it.invitedBy}" to it }.toMap()
        // Retract pings for invites that vanished (cancel/decline/join consumed).
        val gone = lastInviteKeys - freshKeys.keys
        gone.forEach { key ->
            val sep = key.indexOf(':')
            if (sep > 0) {
                val gid = key.substring(0, sep)
                val name = lastInviteNames[key].orEmpty()
                // The inviter name disambiguates the stable notif id.
                if (name.isNotBlank()) notificationManager.cancelGroupInviteNotification(gid, name)
                pingedInviteIds.remove(key)
            }
        }
        lastInviteKeys = freshKeys.keys.toSet()
        lastInviteNames = freshKeys.mapValues { it.value.invitedByName }
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
                            // Votes are tally data: open chats refresh silently
                            // via load(), but they never ping (push layer agrees).
                            if (ping.kind == "vote") return@collect
                            if (!seenEventIds.add("msg:${ping.clientId}")) return@collect
                            val group = lastGroups.firstOrNull { it.id == m.groupId }
                            val sender = lastFriendNames[ping.senderId]?.takeIf { it.isNotBlank() }
                            val text = if (sender != null) {
                                appContext.getString(
                                    com.frerox.toolz.R.string.st_Whisper_Groups_SenderNewMessage,
                                    sender,
                                )
                            } else {
                                appContext.getString(com.frerox.toolz.R.string.st_Whisper_Notif_NewMessage)
                            }
                            notificationManager.showGroupEventNotification(
                                groupId = m.groupId,
                                title = group?.name?.ifBlank { "Group" } ?: "Group",
                                text = text,
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
        if (row.actor == me) return
        val group = lastGroups.firstOrNull { it.id == m.groupId }
        val groupName = group?.name?.ifBlank { "Group" } ?: "Group"
        val actorName = lastFriendNames[row.actor] ?: "Someone"
        // Admin-action events behave like message pings: mute + self only,
        // no event-toggle gate (the manager still enforces mute/open-chat).
        when (row.type) {
            "remove", "promote", "demote", "rename", "picture", "settings" -> {
                val subjectId = row.payload.trim().trim('"').takeIf {
                    it.length >= 10 && it.all { c -> c.isLetterOrDigit() || c == '-' }
                }.orEmpty()
                val subjectName = lastFriendNames[subjectId] ?: "Someone"
                val text = when (row.type) {
                    "remove" -> if (subjectId == me) {
                        appContext.getString(com.frerox.toolz.R.string.st_Whisper_Groups_RemovedYou, groupName)
                    } else {
                        appContext.getString(
                            com.frerox.toolz.R.string.st_Whisper_Groups_RemovedOther, subjectName, groupName,
                        )
                    }
                    "promote" -> if (subjectId == me) {
                        appContext.getString(com.frerox.toolz.R.string.st_Whisper_Groups_PromotedYou, groupName)
                    } else {
                        appContext.getString(
                            com.frerox.toolz.R.string.st_Whisper_Groups_PromotedOther, subjectName, groupName,
                        )
                    }
                    "demote" -> if (subjectId == me) {
                        appContext.getString(com.frerox.toolz.R.string.st_Whisper_Groups_DemotedYou, groupName)
                    } else {
                        appContext.getString(
                            com.frerox.toolz.R.string.st_Whisper_Groups_DemotedOther, subjectName, groupName,
                        )
                    }
                    "rename" -> appContext.getString(com.frerox.toolz.R.string.st_Whisper_Groups_Renamed, actorName)
                    "picture" -> appContext.getString(
                        com.frerox.toolz.R.string.st_Whisper_Groups_PictureUpdated, actorName,
                    )
                    else -> appContext.getString(
                        com.frerox.toolz.R.string.st_Whisper_Groups_SettingsUpdated, actorName,
                    )
                }
                notificationManager.showGroupEventNotification(
                    groupId = m.groupId,
                    title = groupName,
                    text = text,
                    dedupeKey = "evt:${row.id}",
                )
                return
            }
            else -> {}
        }
        val scope = when (row.type) {
            "join" -> GroupNotifScope.JOIN
            "leave" -> GroupNotifScope.LEAVE
            "decline" -> GroupNotifScope.DECLINE
            else -> return
        }
        val amOwner = group?.createdBy == me
        val amOwnerOrAdmin = amOwner || m.isAdmin(me)
        val toggleOn = notifPrefs.isEnabled(m.groupId, amOwnerOrAdmin)
        if (!shouldNotifyGroupEvent(scope, row.actor, me, amOwner, toggleOn)) return
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
