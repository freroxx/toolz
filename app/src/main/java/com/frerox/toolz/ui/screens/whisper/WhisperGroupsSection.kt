/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.frerox.toolz.ui.screens.whisper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.GroupInviteInfo
import com.frerox.toolz.data.whisper.groupsEnabled
import com.frerox.toolz.data.whisper.readBoundedGroupImageBytes
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveTextButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveButton
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Groups hub: the Chats-tab section (flag-gated) + the create / join flows.
 * Renders nothing at all while the flag is off. Rows keep the 1:1
 * conversation-card language (ExpressiveCard, bouncy press) so groups feel
 * native to the tab; everything transient lives in bottom sheets.
 */
@Composable
fun WhisperGroupsSection(
    onNavigateToGroup: (String) -> Unit,
    // Daily Ups stories strip: a friend circle opens that poster in the hub,
    // the own "+" opens compose.
    onNavigateToDailyUps: (String) -> Unit = {},
    onNavigateToDailyUpsCompose: () -> Unit = {},
    viewModel: WhisperGroupsViewModel = hiltViewModel(),
) {
    if (!groupsEnabled()) return
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mutedIds by viewModel.mutedGroupIds.collectAsStateWithLifecycle()
    val workingInvites by viewModel.workingInvites.collectAsStateWithLifecycle()
    val pictures by viewModel.pictures.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var showJoinId by remember { mutableStateOf(false) }

    LaunchedEffect(state.createdGroupId) {
        state.createdGroupId?.let {
            viewModel.consumeCreated()
            showCreate = false
            onNavigateToGroup(it)
        }
    }
    // The "request sent" notice clears itself; it never needs a manual dismiss.
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(4_000)
            viewModel.clearNotice()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Instagram-stories circles first (own + live friends), then the groups
        // header — the strip owns the very top like stories sit above the feed.
        DailyUpsStoriesBar(
            onCompose = {
                if (viewModel.acceptNavigation()) onNavigateToDailyUpsCompose()
            },
            onOpenPoster = { ownerId ->
                if (viewModel.acceptNavigation()) onNavigateToDailyUps(ownerId)
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(
                if (state.groups.isEmpty()) stringResource(R.string.st_Whisper_Groups_Title)
                else "${stringResource(R.string.st_Whisper_Groups_Title)} (${state.groups.size})",
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolzExpressiveTextButton(onClick = { showJoinId = true }) {
                    Text(stringResource(R.string.st_Whisper_Groups_JoinById))
                }
                ToolzTonalExpressiveButton(
                    onClick = { showCreate = true },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        stringResource(R.string.st_Whisper_Groups_New),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        GroupUiBanner(
            message = state.notice.orEmpty(),
            visible = state.notice != null,
            isError = false,
            onDismiss = { viewModel.clearNotice() },
        )
        GroupUiBanner(
            message = state.error.orEmpty(),
            visible = state.error != null && !showCreate,
            isError = true,
            actionLabel = stringResource(R.string.st_Whisper_Groups_Retry),
            onAction = { viewModel.load() },
        )
        if (state.isLoading && state.groups.isEmpty()) {
            repeat(2) { ConversationSkeleton() }
        }
        if (!state.isLoading && state.groups.isEmpty() && state.invites.isEmpty() && state.error == null) {
            GroupEmptyPanel(
                icon = Icons.Rounded.Groups,
                message = stringResource(R.string.st_Whisper_Groups_Empty),
            )
        }
        state.invites.forEach { invite ->
            key("invite_${invite.groupId}_${invite.invitedBy}") {
                GroupInviteCard(
                    invite = invite,
                    working = invite.groupId in workingInvites,
                    onJoin = { viewModel.join(invite.groupId, onNavigateToGroup) },
                    onDecline = { viewModel.decline(invite.groupId) },
                )
            }
        }
        state.groups.forEach { group ->
            key(group.id) {
                val muted = group.id in mutedIds
                SwipeToMuteRow(
                    muted = muted,
                    onToggleMute = { viewModel.toggleMute(group.id) },
                ) {
                    GroupRowCard(
                        name = group.name.ifBlank { "Group" },
                        groupId = group.id,
                        muted = muted,
                        memberCount = state.memberCounts[group.id] ?: 0,
                        preview = state.previews[group.id],
                        picture = pictures[group.id],
                        onOpen = {
                            if (viewModel.acceptNavigation()) onNavigateToGroup(group.id)
                        },
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateGroupSheet(viewModel = viewModel, onDismiss = { showCreate = false })
    }
    if (showJoinId) {
        JoinByIdSheet(
            onDismiss = { showJoinId = false },
            onRequest = { id -> showJoinId = false; viewModel.requestJoin(id) },
        )
    }
}

/** Persistent invite card: survives restarts, dies only by join/decline/cancel. */
@Composable
private fun GroupInviteCard(
    invite: GroupInviteInfo,
    working: Boolean,
    onJoin: () -> Unit,
    onDecline: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupAvatar(name = invite.groupName, groupId = invite.groupId, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        invite.groupName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${invite.invitedByName} · ${stringResource(R.string.st_Whisper_Groups_InvitedLabel)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSecondaryContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolzExpressiveButton(
                    onClick = onJoin,
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) {
                    if (working) GroupUiInlineLoading()
                    else Text(stringResource(R.string.st_Whisper_Groups_Join), fontWeight = FontWeight.Bold)
                }
                ToolzTonalExpressiveButton(
                    onClick = onDecline,
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.st_Whisper_Groups_Decline), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Swipe-to-mute row: end-to-start swipe toggles the group mute and springs
 * back (the row never actually dismisses). The reveal only paints while
 * dragging so the card's rounded corners stay clean.
 */
@Composable
private fun SwipeToMuteRow(
    muted: Boolean,
    onToggleMute: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentToggle by rememberUpdatedState(onToggleMute)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target == SwipeToDismissBoxValue.EndToStart) {
                haptic.click()
                currentToggle()
            }
            false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.3f },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(end = 22.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            if (muted) R.string.st_Whisper_Groups_Unmute else R.string.st_Whisper_Groups_Mute,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        if (muted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
        content = { content() },
    )
}

@Composable
private fun GroupRowCard(
    name: String,
    groupId: String,
    muted: Boolean,
    memberCount: Int,
    preview: WhisperGroupsViewModel.GroupPreview?,
    picture: ByteArray?,
    onOpen: () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val scheme = MaterialTheme.colorScheme
    val unread = preview?.unread ?: 0
    val hasUnread = unread > 0

    val photoLabel = stringResource(R.string.st_Whisper_Groups_Photo)
    val pollLabel = stringResource(R.string.st_Whisper_Groups_Poll)
    val membersLabel = stringResource(R.string.st_Whisper_Groups_Members)
    val hasPreview = preview != null &&
        (preview.text.isNotBlank() || preview.hasImage || preview.hasPoll)
    val line = if (hasPreview && preview != null) {
        val sender = preview.senderName.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()
        sender + preview.text.ifBlank {
            when {
                preview.hasPoll -> pollLabel
                preview.hasImage -> photoLabel
                else -> ""
            }
        }
    } else {
        "$memberCount $membersLabel"
    }
    val mediaIcon = if (hasPreview && preview != null) {
        when {
            preview.hasPoll -> Icons.Rounded.BarChart
            preview.hasImage -> Icons.Rounded.Image
            else -> null
        }
    } else null

    ExpressiveCard(
        onClick = {
            haptic.click()
            onOpen()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GroupAvatar(name = name, groupId = groupId, size = 56.dp, picture = picture)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (muted) {
                        Icon(
                            Icons.Rounded.NotificationsOff,
                            contentDescription = stringResource(R.string.st_Whisper_Groups_CdMute),
                            tint = scheme.outline,
                            modifier = Modifier.padding(start = 6.dp).size(14.dp),
                        )
                    }
                    if (preview != null && preview.time.isNotBlank()) {
                        Text(
                            formatGroupListTime(preview.time),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasUnread && !muted) scheme.primary else scheme.onSurfaceVariant,
                            fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mediaIcon != null) {
                        Icon(
                            mediaIcon,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp).size(16.dp),
                        )
                    }
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hasUnread) scheme.onSurface else scheme.onSurfaceVariant,
                        fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (preview?.mentionedMe == true) {
                        Surface(
                            shape = CircleShape,
                            color = scheme.tertiaryContainer,
                            contentColor = scheme.onTertiaryContainer,
                            modifier = Modifier.padding(start = 6.dp),
                        ) {
                            Text(
                                stringResource(R.string.st_Whisper_Groups_MentionedYou),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = hasUnread,
                        enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                .clip(CircleShape)
                                .background(if (muted) scheme.surfaceContainerHighest else scheme.primary)
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (unread > 99) "99+" else "$unread",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (muted) scheme.onSurfaceVariant else scheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Create group
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CreateGroupSheet(
    viewModel: WhisperGroupsViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    var adminsOnly by remember { mutableStateOf(true) }
    var pictureBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pictureMime by remember { mutableStateOf("image/jpeg") }
    var pictureError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // A failed create hands the button back so the user can retry.
    LaunchedEffect(state.error) { if (state.error != null) submitting = false }

    val canCreate = name.isNotBlank() && picked.isNotEmpty() && !submitting
    val visibleFriends = remember(state.friends, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) state.friends
        else state.friends.filter {
            it.effectiveName.lowercase().contains(q) || it.username.lowercase().contains(q)
        }
    }
    val friendsById = remember(state.friends) { state.friends.associateBy { it.id } }

    val pictureTooBig = stringResource(R.string.st_Whisper_Error_ImageTooLarge)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pictureError = null
        runCatching {
            val mime = context.contentResolver.getType(uri)
            val bytes = context.contentResolver.openInputStream(uri)?.use { readBoundedGroupImageBytes(it) }
            mime to bytes
        }.onSuccess { (mime, bytes) ->
            if (bytes != null) {
                pictureBytes = bytes
                if (mime != null) pictureMime = mime
            }
        }.onFailure {
            pictureError = pictureTooBig
        }
    }

    GroupSheet(onDismiss = onDismiss) { close ->
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                        .clickable { picker.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    GroupAvatar(
                        name = name.ifBlank { "G" },
                        groupId = name.ifBlank { "preview" },
                        size = 72.dp,
                        picture = pictureBytes,
                    )
                    Box(
                        modifier = Modifier.matchParentSizeScrim(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.AddPhotoAlternate,
                            contentDescription = GroupCd.picture(),
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.st_Whisper_Groups_CreateTitle),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (picked.isEmpty()) stringResource(R.string.st_Whisper_Groups_AddMembers)
                        else "${picked.size} ${stringResource(R.string.st_Whisper_Groups_Members)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            GroupUiBanner(
                message = pictureError.orEmpty(),
                visible = pictureError != null,
                isError = true,
                onDismiss = { pictureError = null },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 64) name = it },
                label = { Text(stringResource(R.string.st_Whisper_Groups_NameHint)) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().groupScrollFade(listState),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (picked.isNotEmpty()) {
                    item(key = "picked") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
                            items(picked.toList(), key = { it }) { id ->
                                InputChip(
                                    selected = false,
                                    onClick = { picked.remove(id) },
                                    label = { Text(friendsById[id]?.effectiveName ?: "…", maxLines = 1) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.st_Whisper_Groups_Remove),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                item(key = "mode") {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.st_Whisper_Groups_WhoCanInvite),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ToolzConnectedButtonGroup(
                            selectedIndex = if (adminsOnly) 0 else 1,
                            options = listOf(
                                stringResource(R.string.st_Whisper_Groups_InviteAdmins),
                                stringResource(R.string.st_Whisper_Groups_InviteAll),
                            ),
                            onOptionSelected = { adminsOnly = it == 0 },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item(key = "search") {
                    GroupSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.st_Whisper_Groups_SearchHint),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
                if (state.friends.isEmpty() && !state.isLoading) {
                    item(key = "no_friends") {
                        GroupUiBanner(
                            message = stringResource(R.string.st_Whisper_Groups_NoFriendsForGroup),
                            visible = true,
                            isError = false,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                } else if (visibleFriends.isEmpty()) {
                    item(key = "no_match") {
                        Text(
                            stringResource(R.string.st_Whisper_Groups_NoFriendsFound),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        )
                    }
                }
                items(visibleFriends, key = { "friend_${it.id}" }) { friend ->
                    val selected = friend.id in picked
                    FriendPickRow(
                        friend = friend,
                        selected = selected,
                        onToggle = { if (selected) picked.remove(friend.id) else picked.add(friend.id) },
                    )
                }
            }
            GroupUiBanner(
                message = state.error.orEmpty(),
                visible = state.error != null,
                isError = true,
                onDismiss = { viewModel.clearError() },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolzExpressiveTextButton(onClick = close) {
                    Text(stringResource(R.string.st_Whisper_Cancel))
                }
                ToolzExpressiveButton(
                    onClick = {
                        submitting = true
                        viewModel.create(name, picked.toList(), adminsOnly, pictureBytes, pictureMime)
                    },
                    enabled = canCreate,
                    modifier = Modifier.weight(1f),
                ) {
                    if (submitting) {
                        GroupUiInlineLoading()
                    } else {
                        Text(
                            if (picked.isEmpty()) stringResource(R.string.st_Whisper_Groups_Create)
                            else "${stringResource(R.string.st_Whisper_Groups_Create)} (${picked.size})",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/** Dark scrim fill for the avatar's edit affordance. */
private fun Modifier.matchParentSizeScrim(): Modifier = this
    .size(72.dp)
    .clip(CircleShape)
    .background(Color.Black.copy(alpha = 0.28f))

// ─────────────────────────────────────────────────────────────────────────────
// Join by id
// ─────────────────────────────────────────────────────────────────────────────

/** Returns true for a bare UUID or a `WHISPER-GROUP:<uuid>` QR payload. */
internal fun isGroupJoinIdValid(raw: String): Boolean {
    val id = raw.trim().removePrefix("WHISPER-GROUP:")
    return id.isNotBlank() && runCatching { java.util.UUID.fromString(id) }.isSuccess
}

/** Join-by-ID: paste a group id (share-sheet payload) to request access. */
@Composable
private fun JoinByIdSheet(onDismiss: () -> Unit, onRequest: (String) -> Unit) {
    var id by remember { mutableStateOf("") }
    val trimmed = id.trim().removePrefix("WHISPER-GROUP:")
    val valid = isGroupJoinIdValid(id)
    val showError = id.isNotBlank() && !valid
    GroupSheet(onDismiss = onDismiss) { close ->
        GroupSheetTitle(stringResource(R.string.st_Whisper_Groups_JoinById))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = id,
                onValueChange = { id = it },
                label = { Text(stringResource(R.string.st_Whisper_Groups_JoinByIdHint)) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                isError = showError,
                supportingText = if (showError) {
                    { Text(stringResource(R.string.st_Whisper_Groups_InvalidId)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            // The #1 usability gap of this flow: ids come from ANOTHER member's
            // group profile (Share → Copy ID). Without this, "Join with ID"
            // looks like it leads nowhere.
            Text(
                stringResource(R.string.st_Whisper_Groups_JoinByIdHelp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolzExpressiveTextButton(onClick = close) {
                    Text(stringResource(R.string.st_Whisper_Cancel))
                }
                ToolzExpressiveButton(
                    onClick = { onRequest(trimmed) },
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.st_Whisper_Groups_JoinById), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fullscreen image viewer (chat bubbles + info gallery)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Shared fullscreen group-image viewer: pinch to zoom, pan when zoomed,
 * double-tap to zoom in/out, tap to hide the chrome. Save mirrors the 1:1
 * save-to-gallery path (MediaStore, no extra permission on Q+).
 */
@Composable
fun GroupImageViewer(bytes: ByteArray, onDismiss: () -> Unit, onMessage: (String) -> Unit = {}) {
    val bitmap = rememberGroupBitmap(bytes, 2048)
    val context = LocalContext.current
    val saveScope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var chromeVisible by remember { mutableStateOf(true) }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { boxSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { chromeVisible = !chromeVisible },
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 5f)
                        val maxX = boxSize.width * (next - 1f) / 2f
                        val maxY = boxSize.height * (next - 1f) / 2f
                        offset = if (next <= 1f) Offset.Zero else Offset(
                            (offset.x + pan.x).coerceIn(-maxX, maxX),
                            (offset.y + pan.y).coerceIn(-maxY, maxY),
                        )
                        scale = next
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.st_Whisper_Groups_Photo),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    contentScale = ContentScale.Fit,
                )
            } else {
                GroupUiCenterLoading()
            }
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { showSaveConfirm = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = GroupCd.save())
                    }
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = GroupCd.close())
                    }
                }
            }
        }
        // S-3 parity: saving exports the DECRYPTED image out of the E2EE
        // boundary — the user confirms explicitly, exactly like the 1:1
        // viewer. The saved bytes leave the device's protected sandbox.
        if (showSaveConfirm) {
            AlertDialog(
                onDismissRequest = { showSaveConfirm = false },
                title = { Text(stringResource(R.string.st_Whisper_SaveToGallery_Title)) },
                text = { Text(stringResource(R.string.st_Whisper_SaveToGallery_Desc)) },
                confirmButton = {
                    TextButton(onClick = {
                        showSaveConfirm = false
                        saveScope.launch {
                            val ok = saveGroupImageToGallery(context, bytes)
                            onMessage(
                                context.getString(
                                    if (ok) R.string.st_Whisper_Groups_SavedToGallery
                                    else R.string.st_Whisper_Groups_SaveFailed,
                                ),
                            )
                        }
                    }) {
                        Text(stringResource(R.string.st_Whisper_Save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveConfirm = false }) {
                        Text(stringResource(R.string.st_Whisper_Cancel))
                    }
                },
            )
        }
    }
}

/** MediaStore save for decrypted group images (mirrors the 1:1 helper). */
internal suspend fun saveGroupImageToGallery(context: android.content.Context, bytes: ByteArray): Boolean =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            val filename = "Whisper_${System.currentTimeMillis()}.jpg"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(
                        android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/Whisper",
                    )
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues,
                ) ?: return@withContext false
                try {
                    resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@withContext false
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                } catch (e: Exception) {
                    runCatching { resolver.delete(uri, null, null) }
                    return@withContext false
                }
            } else {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                }
                val uri = resolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues,
                ) ?: return@withContext false
                val wrote = resolver.openOutputStream(uri)?.use { it.write(bytes) } != null
                if (!wrote) {
                    runCatching { resolver.delete(uri, null, null) }
                    return@withContext false
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
