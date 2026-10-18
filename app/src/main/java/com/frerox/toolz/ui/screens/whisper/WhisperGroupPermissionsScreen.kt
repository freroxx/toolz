/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Owner-only permission editor behind one signed POLICY event. Admins get the
 * full capability list, members only the owner-delegable subset, and the
 * payload goes out through [WhisperGroupCapability]-level toggles so unknown
 * values can never be minted client-side.
 */

private data class CapRow(val cap: WhisperGroupCapability, val icon: ImageVector)

private val ADMIN_CAP_ROWS = listOf(
    CapRow(WhisperGroupCapability.SEND_MESSAGES, Icons.AutoMirrored.Rounded.Send),
    CapRow(WhisperGroupCapability.INVITE_MEMBERS, Icons.Rounded.PersonAdd),
    CapRow(WhisperGroupCapability.APPROVE_JOIN_REQUESTS, Icons.Rounded.HowToReg),
    CapRow(WhisperGroupCapability.REMOVE_MEMBERS, Icons.Rounded.PersonRemove),
    CapRow(WhisperGroupCapability.PROMOTE_DEMOTE, Icons.Rounded.Star),
    CapRow(WhisperGroupCapability.EDIT_INFO, Icons.Rounded.Edit),
    CapRow(WhisperGroupCapability.EDIT_PICTURE, Icons.Rounded.AddPhotoAlternate),
    CapRow(WhisperGroupCapability.CREATE_POLLS, Icons.Rounded.BarChart),
    CapRow(WhisperGroupCapability.CLOSE_POLLS, Icons.Rounded.Lock),
)

private val MEMBER_CAP_ROWS = listOf(
    CapRow(WhisperGroupCapability.SEND_MESSAGES, Icons.AutoMirrored.Rounded.Send),
    CapRow(WhisperGroupCapability.INVITE_MEMBERS, Icons.Rounded.PersonAdd),
    CapRow(WhisperGroupCapability.EDIT_INFO, Icons.Rounded.Edit),
    CapRow(WhisperGroupCapability.EDIT_PICTURE, Icons.Rounded.AddPhotoAlternate),
    CapRow(WhisperGroupCapability.CREATE_POLLS, Icons.Rounded.BarChart),
)

@Composable
fun WhisperGroupPermissionsScreen(
    onNavigateBack: () -> Unit,
    viewModel: WhisperGroupPermissionsViewModel = hiltViewModel(),
) {
    if (!groupsEnabled()) return
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // A successful save lands (event verified and re-synced) — show it, then
    // hand the user back to the info sheet where the boundaries now apply.
    LaunchedEffect(state.saved) {
        if (state.saved) {
            delay(1_200)
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().toolzBackground(),
        snackbarHost = { GroupSnackbarHost(state = snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.st_Whisper_Groups_Permissions), fontWeight = FontWeight.Bold) },
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
        if (state.isLoading && state.membership == null) {
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
            val membership = state.membership
            if (membership == null) return@Column
            val adminCount = membership.members.count { it.value.wire == "admin" }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().groupScrollFade(listState),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "owner_note") {
                    GroupListRow(
                        shape = segmentShape(0, 1),
                        leading = {
                            Icon(
                                Icons.Rounded.ManageAccounts, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        title = stringResource(R.string.st_Whisper_Groups_PermissionsDesc),
                        supporting = stringResource(R.string.st_Whisper_Groups_PermissionsOwnerNote),
                    )
                }
                item(key = "admin_label") {
                    GroupSectionLabel(
                        stringResource(R.string.st_Whisper_Groups_PermissionsAdmins),
                        trailing = "$adminCount",
                    )
                }
                items(ADMIN_CAP_ROWS.size, key = { "admin_${ADMIN_CAP_ROWS[it].cap.wire}" }) { idx ->
                    val row = ADMIN_CAP_ROWS[idx]
                    SettingSwitchRow(
                        shape = segmentShape(idx, ADMIN_CAP_ROWS.size),
                        icon = row.icon,
                        title = capTitle(row.cap),
                        supporting = capDesc(row.cap),
                        checked = row.cap in state.adminCaps,
                        onToggle = { viewModel.toggleAdmin(row.cap) },
                    )
                }
                item(key = "member_label") {
                    GroupSectionLabel(stringResource(R.string.st_Whisper_Groups_PermissionsMembers))
                }
                items(MEMBER_CAP_ROWS.size, key = { "member_${MEMBER_CAP_ROWS[it].cap.wire}" }) { idx ->
                    val row = MEMBER_CAP_ROWS[idx]
                    SettingSwitchRow(
                        shape = segmentShape(idx, MEMBER_CAP_ROWS.size),
                        icon = row.icon,
                        title = capTitle(row.cap),
                        supporting = capDesc(row.cap),
                        checked = row.cap in state.memberCaps,
                        onToggle = { viewModel.toggleMember(row.cap) },
                    )
                }
                item(key = "save_gap") { Spacer(Modifier.height(14.dp)) }
                item(key = "save") {
                    val changed = viewModel.changedCount()
                    GroupListRow(
                        shape = segmentShape(0, 1),
                        onClick = { if (!state.isWorking && changed > 0) viewModel.save() },
                        containerColor = if (changed > 0) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        leading = {
                            Icon(
                                Icons.Rounded.AdminPanelSettings, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        title = stringResource(R.string.st_Whisper_Groups_PermissionsSave),
                        titleColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        supporting = if (changed > 0) null
                        else stringResource(R.string.st_Whisper_Groups_PermissionsSaved),
                    )
                }
            }
        }
    }
}

@Composable
private fun capTitle(cap: WhisperGroupCapability): String = stringResource(
    when (cap) {
        WhisperGroupCapability.SEND_MESSAGES -> R.string.st_Whisper_Groups_CapSend
        WhisperGroupCapability.INVITE_MEMBERS -> R.string.st_Whisper_Groups_CapInvite
        WhisperGroupCapability.APPROVE_JOIN_REQUESTS -> R.string.st_Whisper_Groups_CapApproveJoin
        WhisperGroupCapability.REMOVE_MEMBERS -> R.string.st_Whisper_Groups_CapRemove
        WhisperGroupCapability.PROMOTE_DEMOTE -> R.string.st_Whisper_Groups_CapManageRoles
        WhisperGroupCapability.EDIT_INFO -> R.string.st_Whisper_Groups_CapEditInfo
        WhisperGroupCapability.EDIT_PICTURE -> R.string.st_Whisper_Groups_CapEditPicture
        WhisperGroupCapability.CREATE_POLLS -> R.string.st_Whisper_Groups_CapCreatePoll
        WhisperGroupCapability.CLOSE_POLLS -> R.string.st_Whisper_Groups_CapClosePoll
    },
)

@Composable
private fun capDesc(cap: WhisperGroupCapability): String = stringResource(
    when (cap) {
        WhisperGroupCapability.SEND_MESSAGES -> R.string.st_Whisper_Groups_CapSendDesc
        WhisperGroupCapability.INVITE_MEMBERS -> R.string.st_Whisper_Groups_CapInviteDesc
        WhisperGroupCapability.APPROVE_JOIN_REQUESTS -> R.string.st_Whisper_Groups_CapApproveJoinDesc
        WhisperGroupCapability.REMOVE_MEMBERS -> R.string.st_Whisper_Groups_CapRemoveDesc
        WhisperGroupCapability.PROMOTE_DEMOTE -> R.string.st_Whisper_Groups_CapManageRolesDesc
        WhisperGroupCapability.EDIT_INFO -> R.string.st_Whisper_Groups_CapEditInfoDesc
        WhisperGroupCapability.EDIT_PICTURE -> R.string.st_Whisper_Groups_CapEditPictureDesc
        WhisperGroupCapability.CREATE_POLLS -> R.string.st_Whisper_Groups_CapCreatePollDesc
        WhisperGroupCapability.CLOSE_POLLS -> R.string.st_Whisper_Groups_CapClosePollDesc
    },
)
