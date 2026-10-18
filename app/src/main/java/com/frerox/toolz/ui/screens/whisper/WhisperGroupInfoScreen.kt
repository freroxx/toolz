/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperGroupCapability
import com.frerox.toolz.data.whisper.groupsEnabled
import com.frerox.toolz.ui.components.ExpressiveLinearProgressIndicator
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch

/**
 * Group info: profile header, quick actions, settings, requests, members,
 * invites and the danger zone. Authorization comes from the reconstructed
 * POLICY state (the owner delegates capabilities per role); the owner also
 * gets the who-can-do-what editor. Everyone gets mute + leave.
 */
@Composable
fun WhisperGroupInfoScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPermissions: (String) -> Unit,
    viewModel: WhisperGroupInfoViewModel = hiltViewModel(),
) {
    if (!groupsEnabled()) return
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDescEdit by remember { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }
    var showDisband by remember { mutableStateOf(false) }
    var showMuteDurations by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }
    var memberQuery by remember { mutableStateOf("") }
    var memberSheet by remember { mutableStateOf<WhisperGroupInfoViewModel.MemberEntry?>(null) }
    var pendingBlock by remember { mutableStateOf<String?>(null) }
    var transferTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var reportTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(state.left) {
        if (state.left) onNavigateBack()
    }

    val membership = state.membership
    val me = state.myId
    // Authorization is the reconstructed POLICY state, not a role check: the
    // owner delegates capabilities per role in Who-can-do-what, and the derived
    // compat policy keeps legacy CREATE-only groups behaving exactly as before.
    val amOwner = state.amOwner
    val amAdmin = membership?.isAdmin(me) == true
    val canInvite = membership?.canInvite(me) == true
    val canManageRoles = membership?.allows(me, WhisperGroupCapability.PROMOTE_DEMOTE) == true
    val canRemoveMembers = membership?.allows(me, WhisperGroupCapability.REMOVE_MEMBERS) == true
    val canApproveRequests = membership?.allows(me, WhisperGroupCapability.APPROVE_JOIN_REQUESTS) == true
    val canEditInfo = membership?.allows(me, WhisperGroupCapability.EDIT_INFO) == true
    val canEditPicture = membership?.allows(me, WhisperGroupCapability.EDIT_PICTURE) == true
    val groupName = membership?.name?.ifBlank { "Group" } ?: "Group"
    val adminCount = membership?.members?.count { it.value.wire == "admin" } ?: 0

    val mq = memberQuery.trim().lowercase()
    val visibleMembers = remember(state.members, mq) {
        if (mq.isBlank()) state.members else state.members.filter { it.name.lowercase().contains(mq) }
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showGroupNotice(msg) } }

    Scaffold(
        modifier = Modifier.fillMaxSize().toolzBackground(),
        snackbarHost = { GroupSnackbarHost(state = snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.st_Whisper_Groups_Info), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = GroupCd.back())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        },
    ) { padding ->
        if (state.isLoading && membership == null) {
            GroupUiCenterLoading(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            GroupAnimatedReveal(visible = state.isWorking) {
                ExpressiveLinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            GroupUiBanner(
                message = state.error.orEmpty(),
                visible = state.error != null,
                isError = true,
                actionLabel = stringResource(R.string.st_Whisper_Groups_Retry),
                onAction = { viewModel.load() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().groupScrollFade(listState),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "hero") {
                    GroupHero(
                        name = groupName,
                        groupId = viewModel.groupId,
                        pictureBytes = state.pictureBytes,
                        canChangePicture = canEditPicture,
                        onPickPicture = { bytes, mime -> viewModel.setPicture(bytes, mime) },
                        onPictureError = notify,
                        canRename = canEditInfo,
                        onRename = { showRename = true },
                        memberCount = state.members.size,
                        inviteLabel = stringResource(
                            if (membership?.inviteAdminsOnly == true) R.string.st_Whisper_Groups_InviteAdmins
                            else R.string.st_Whisper_Groups_InviteAll,
                        ),
                        description = membership?.description.orEmpty(),
                        canEditDescription = canEditInfo,
                        onEditDescription = { showDescEdit = true },
                    )
                }
                if (state.degraded) {
                    item(key = "degraded") {
                        Column(Modifier.padding(vertical = 6.dp)) { GroupDegradedBanner() }
                    }
                }
                item(key = "actions") {
                    val mutedUntil = remember(state.mutedUntilMs) { formatMutedUntil(state.mutedUntilMs) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuickActionTile(
                            icon = if (state.isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive,
                            label = stringResource(
                                if (state.isMuted) R.string.st_Whisper_Groups_Unmute else R.string.st_Whisper_Groups_Mute,
                            ),
                            supporting = if (state.isMuted && mutedUntil.isNotBlank()) {
                                stringResource(R.string.st_Whisper_Groups_MutedUntil, mutedUntil)
                            } else null,
                            active = state.isMuted,
                            onClick = {
                                if (state.isMuted) viewModel.toggleMute() else showMuteDurations = true
                            },
                            modifier = Modifier.weight(1f),
                        )
                        QuickActionTile(
                            icon = Icons.Rounded.Share,
                            label = stringResource(R.string.st_Whisper_Groups_Share),
                            onClick = { showShare = true },
                            modifier = Modifier.weight(1f),
                        )
                        QuickActionTile(
                            icon = Icons.Rounded.PhotoLibrary,
                            label = stringResource(R.string.st_Whisper_Groups_Gallery),
                            onClick = { viewModel.loadGallery(); showGallery = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ── Settings (connected list) ────────────────────────────────
                val canEditDesc = canEditInfo
                // Toggles write a signed SETTINGS event the server validates
                // against policy.allows(EDIT_INFO) — same gate as the apply path.
                val canToggleSettings = canEditInfo
                val settingsCount = 1 + (if (canToggleSettings) 2 else 0) + (if (canEditDesc) 1 else 0)
                item(key = "settings_label") {
                    GroupSectionLabel(stringResource(R.string.st_Whisper_Groups_Settings))
                }
                item(key = "set_notif") {
                    SettingSwitchRow(
                        shape = segmentShape(0, settingsCount),
                        icon = Icons.Rounded.Notifications,
                        title = stringResource(R.string.st_Whisper_Groups_NotifToggle),
                        supporting = stringResource(
                            when {
                                !state.notifOn -> R.string.st_Whisper_Groups_NotifToggleOff
                                amAdmin || amOwner -> R.string.st_Whisper_Groups_NotifScopeOwner
                                else -> R.string.st_Whisper_Groups_NotifScopeMember
                            },
                        ),
                        checked = state.notifOn,
                        onToggle = { viewModel.toggleNotif() },
                    )
                }
                if (canToggleSettings) {
                    item(key = "set_send") {
                        SettingSwitchRow(
                            shape = segmentShape(1, settingsCount),
                            icon = Icons.Rounded.Lock,
                            title = stringResource(R.string.st_Whisper_Groups_AdminOnlySend),
                            supporting = stringResource(R.string.st_Whisper_Groups_AdminOnlySendDesc),
                            checked = membership?.adminOnlySend == true,
                            onToggle = { viewModel.saveSettings(adminOnlySend = membership?.adminOnlySend != true) },
                        )
                    }
                    item(key = "set_edit") {
                        SettingSwitchRow(
                            shape = segmentShape(2, settingsCount),
                            icon = Icons.Rounded.AdminPanelSettings,
                            title = stringResource(R.string.st_Whisper_Groups_MembersCanEdit),
                            supporting = stringResource(R.string.st_Whisper_Groups_MembersCanEditDesc),
                            checked = membership?.membersCanEdit == true,
                            onToggle = { viewModel.saveSettings(editMembers = membership?.membersCanEdit != true) },
                        )
                    }
                }
                if (canEditDesc) {
                    item(key = "set_desc") {
                        GroupListRow(
                            shape = segmentShape(settingsCount - 1, settingsCount),
                            onClick = { showDescEdit = true },
                            leading = {
                                Icon(
                                    Icons.Rounded.Edit, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            title = stringResource(R.string.st_Whisper_Groups_Description),
                            supporting = membership?.description.orEmpty()
                                .ifBlank { stringResource(R.string.st_Whisper_Groups_DescriptionHint) },
                            trailing = {
                                Text(
                                    stringResource(R.string.st_Whisper_Groups_EditDescription),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                    }
                }

                // ── Who-can-do-what (owner-only editor) ──────────────────────
                if (amOwner) {
                    item(key = "perm_row") {
                        GroupListRow(
                            shape = segmentShape(0, 1),
                            onClick = { onNavigateToPermissions(viewModel.groupId) },
                            leading = {
                                Icon(
                                    Icons.Rounded.ManageAccounts, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            title = stringResource(R.string.st_Whisper_Groups_Permissions),
                            supporting = stringResource(R.string.st_Whisper_Groups_PermissionsDesc),
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }

                // ── Join requests (policy-gated) ─────────────────────────────
                if (canApproveRequests && state.requests.isNotEmpty()) {
                    item(key = "req_label") {
                        GroupSectionLabel(
                            stringResource(R.string.st_Whisper_Groups_Requests),
                            trailing = "${state.requests.size}",
                        )
                    }
                    items(state.requests.size, key = { "req_${state.requests[it].userId}" }) { idx ->
                        val req = state.requests[idx]
                        RequestRow(
                            shape = segmentShape(idx, state.requests.size),
                            userId = req.userId,
                            name = req.userName,
                            onApprove = { viewModel.approveRequest(req.userId) },
                            onDeny = { viewModel.denyRequest(req.userId) },
                        )
                    }
                }

                // ── Members ──────────────────────────────────────────────────
                item(key = "members_label") {
                    GroupSectionLabel(
                        stringResource(R.string.st_Whisper_Groups_Members),
                        trailing = "${state.members.size}",
                    )
                }
                if (state.members.size > 8) {
                    item(key = "member_search") {
                        GroupSearchField(
                            value = memberQuery,
                            onValueChange = { memberQuery = it },
                            placeholder = stringResource(R.string.st_Whisper_Groups_MemberSearchHint),
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
                val memberSegments = visibleMembers.size + if (canInvite) 1 else 0
                if (canInvite) {
                    item(key = "member_add") {
                        GroupListRow(
                            shape = segmentShape(0, memberSegments),
                            onClick = { showAdd = true },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            leading = {
                                Icon(
                                    Icons.Rounded.PersonAdd, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            },
                            title = stringResource(R.string.st_Whisper_Groups_AddMembers),
                            titleColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                items(visibleMembers.size, key = { "member_${visibleMembers[it].userId}" }) { idx ->
                    val member = visibleMembers[idx]
                    MemberRow(
                        shape = segmentShape(idx + if (canInvite) 1 else 0, memberSegments),
                        member = member,
                        onClick = if (member.isMe) null else ({ memberSheet = member }),
                    )
                }

                // ── Outstanding invites (only someone who may invite) ────────
                if (canInvite && state.pending.isNotEmpty()) {
                    item(key = "pending_label") {
                        GroupSectionLabel(
                            stringResource(R.string.st_Whisper_Groups_InvitedLabel),
                            trailing = "${state.pending.size}",
                        )
                    }
                    items(state.pending.size, key = { "pending_${state.pending[it].userId}" }) { idx ->
                        val entry = state.pending[idx]
                        PendingInviteRow(
                            shape = segmentShape(idx, state.pending.size),
                            userId = entry.userId,
                            name = entry.name,
                            onCancel = { viewModel.cancelInvite(entry.userId) },
                        )
                    }
                }

                // ── Danger zone (leave / disband live here only) ─────────────
                item(key = "danger_gap") { Spacer(Modifier.height(18.dp)) }
                val dangerCount = if (state.canDisband) 2 else 1
                item(key = "danger_leave") {
                    GroupListRow(
                        shape = segmentShape(0, dangerCount),
                        onClick = { showLeave = true },
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        leading = {
                            Icon(
                                Icons.AutoMirrored.Rounded.Logout, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        title = stringResource(R.string.st_Whisper_Groups_Leave),
                        titleColor = MaterialTheme.colorScheme.error,
                    )
                }
                // Last member standing: leave is blocked server-side, so disband
                // (delete the group) is offered next to it.
                if (state.canDisband) {
                    item(key = "danger_disband") {
                        GroupListRow(
                            shape = segmentShape(1, dangerCount),
                            onClick = { showDisband = true },
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            leading = {
                                Icon(
                                    Icons.Rounded.DeleteForever, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            title = stringResource(R.string.st_Whisper_Groups_Disband),
                            titleColor = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    // ── Sheets & dialogs ─────────────────────────────────────────────────────
    if (showAdd) {
        AddMemberSheet(
            loadFriends = { cb -> viewModel.loadFriends(cb) },
            excludedIds = state.members.map { it.userId }.toSet() + state.pending.map { it.userId }.toSet(),
            onAdd = { ids -> viewModel.addMembers(ids) },
            onDismiss = { showAdd = false },
        )
    }
    if (showRename) {
        GroupTextEditDialog(
            title = stringResource(R.string.st_Whisper_Groups_Rename),
            label = stringResource(R.string.st_Whisper_Groups_NameHint),
            initial = membership?.name.orEmpty(),
            maxLength = 64,
            confirmLabel = stringResource(R.string.st_Whisper_Groups_Rename),
            allowBlank = false,
            singleLine = true,
            onDismiss = { showRename = false },
            onConfirm = { viewModel.rename(it); showRename = false },
        )
    }
    if (showDescEdit) {
        GroupTextEditDialog(
            title = stringResource(R.string.st_Whisper_Groups_Description),
            label = stringResource(R.string.st_Whisper_Groups_DescriptionHint),
            initial = membership?.description.orEmpty(),
            maxLength = 140,
            confirmLabel = stringResource(R.string.st_Whisper_Groups_EditDescription),
            allowBlank = true,
            singleLine = false,
            onDismiss = { showDescEdit = false },
            onConfirm = { viewModel.saveSettings(description = it); showDescEdit = false },
        )
    }
    if (showMuteDurations) {
        MuteDurationSheet(
            onDismiss = { showMuteDurations = false },
            onPick = { durationMs -> viewModel.muteFor(durationMs) },
        )
    }
    if (showShare) {
        ShareGroupSheet(
            groupId = viewModel.groupId,
            groupName = groupName,
            onDismiss = { showShare = false },
            onMessage = notify,
        )
    }
    if (showGallery) {
        GroupGalleryDialog(
            photos = state.gallery,
            bytesById = state.galleryBytes,
            onRequestBytes = { viewModel.loadGalleryImage(it) },
            onDismiss = { showGallery = false },
            onMessage = notify,
        )
    }
    memberSheet?.let { member ->
        MemberActionsSheet(
            member = member,
            canManageRoles = canManageRoles,
            canDemote = canManageRoles && member.role == "admin" && adminCount > 1,
            canRemove = canRemoveMembers,
            canTransfer = amOwner,
            onDismiss = { memberSheet = null },
            onPromote = { viewModel.promoteMember(member.userId) },
            onDemote = { viewModel.demoteMember(member.userId) },
            onMakeOwner = { transferTarget = member.userId to member.name },
            onRemove = { viewModel.removeMember(member.userId) },
            onBlock = { pendingBlock = member.userId },
            onReport = { reportTarget = member.userId to member.name },
        )
    }
    if (showLeave) {
        GroupConfirmDialog(
            title = stringResource(R.string.st_Whisper_Groups_Leave),
            text = stringResource(R.string.st_Whisper_Groups_LeaveConfirm),
            confirmLabel = stringResource(R.string.st_Whisper_Groups_Leave),
            destructive = true,
            onConfirm = { showLeave = false; viewModel.leave() },
            onDismiss = { showLeave = false },
        )
    }
    if (showDisband) {
        GroupConfirmDialog(
            title = stringResource(R.string.st_Whisper_Groups_Disband),
            text = stringResource(R.string.st_Whisper_Groups_DisbandConfirm),
            confirmLabel = stringResource(R.string.st_Whisper_Groups_Disband),
            destructive = true,
            onConfirm = { showDisband = false; viewModel.disband() },
            onDismiss = { showDisband = false },
        )
    }
    transferTarget?.let { (uid, name) ->
        GroupConfirmDialog(
            title = stringResource(R.string.st_Whisper_Groups_MakeOwner),
            text = stringResource(R.string.st_Whisper_Groups_MakeOwnerConfirm, name),
            confirmLabel = stringResource(R.string.st_Whisper_Groups_MakeOwner),
            onConfirm = { viewModel.transferOwnership(uid); transferTarget = null },
            onDismiss = { transferTarget = null },
        )
    }
    reportTarget?.let { (uid, name) ->
        GroupConfirmDialog(
            title = stringResource(R.string.st_Whisper_Groups_Report),
            text = stringResource(R.string.st_Whisper_Groups_ReportConfirm, name),
            confirmLabel = stringResource(R.string.st_Whisper_Groups_Report),
            destructive = true,
            onConfirm = { viewModel.reportMember(uid); reportTarget = null },
            onDismiss = { reportTarget = null },
        )
    }
    pendingBlock?.let { uid ->
        GroupConfirmDialog(
            title = stringResource(R.string.st_Whisper_Groups_Block),
            text = stringResource(R.string.st_Whisper_Groups_BlockConfirm),
            confirmLabel = stringResource(R.string.st_Whisper_Groups_Block),
            destructive = true,
            onConfirm = { pendingBlock = null; viewModel.blockMember(uid) },
            onDismiss = { pendingBlock = null },
        )
    }
}

/** Short local date ("3 Oct") for a timed mute; empty for forever / not muted. */
private fun formatMutedUntil(ms: Long): String {
    if (ms == 0L || ms == Long.MAX_VALUE) return ""
    return runCatching {
        val z = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
        java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault()).format(z)
    }.getOrDefault("")
}
