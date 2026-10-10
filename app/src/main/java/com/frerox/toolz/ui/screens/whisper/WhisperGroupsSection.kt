/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.groupsEnabled
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveButton
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback

/**
 * Phase-2 groups: the Chats-tab section (flag-gated) + the create-group flow.
 * Renders nothing at all while the flag is off. Row styling mirrors the 1:1
 * [ConversationCard] (ExpressiveCard, 52.dp avatar, mute bell) so groups feel
 * native to the tab instead of bolted on.
 */
@Composable
fun WhisperGroupsSection(
    onNavigateToGroup: (String) -> Unit,
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

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(
                if (state.groups.isEmpty()) stringResource(R.string.st_Whisper_Groups_Title)
                else "${stringResource(R.string.st_Whisper_Groups_Title)} (${state.groups.size})",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showJoinId = true }) {
                    Text(stringResource(R.string.st_Whisper_Groups_JoinById))
                }
                ToolzTonalExpressiveButton(onClick = { showCreate = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.st_Whisper_Groups_New), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        GroupUiBanner(
            message = state.notice.orEmpty(),
            visible = state.notice != null,
            isError = false,
            onDismiss = { viewModel.clearNotice() },
        )
        if (state.isLoading && state.groups.isEmpty()) {
            repeat(2) { ConversationSkeleton() }
        }
        GroupUiBanner(
            message = state.error.orEmpty(),
            visible = state.error != null,
            isError = true,
            actionLabel = stringResource(R.string.st_Whisper_Groups_Retry),
            onAction = { viewModel.load() },
        )
        if (!state.isLoading && state.groups.isEmpty() && state.invites.isEmpty() && state.error == null) {
            GroupEmptyState()
        }
        state.invites.forEach { invite ->
            GroupInviteCard(
                invite = invite,
                working = invite.groupId in workingInvites,
                onJoin = { viewModel.join(invite.groupId, onNavigateToGroup) },
                onDecline = { viewModel.decline(invite.groupId) },
            )
        }
        state.groups.forEach { group ->
            SwipeableGroupRow(
                muted = group.id in mutedIds,
                onToggleMute = { viewModel.toggleMute(group.id) },
            ) {
                GroupRowCard(
                    name = group.name.ifBlank { "Group" },
                    groupId = group.id,
                    muted = group.id in mutedIds,
                    memberCount = state.memberCounts[group.id] ?: 0,
                    preview = state.previews[group.id],
                    picture = pictures[group.id],
                    onOpen = { onNavigateToGroup(group.id) },
                )
            }
        }
    }

    if (showCreate) {
        CreateGroupDialog(
            viewModel = viewModel,
            onDismiss = { showCreate = false },
        )
    }

    if (showJoinId) {
        JoinByIdDialog(
            onDismiss = { showJoinId = false },
            onRequest = { id -> showJoinId = false; viewModel.requestJoin(id) },
        )
    }
}

/** Persistent invite card: survives restarts, dies only by join/decline/cancel. */
@Composable
private fun GroupInviteCard(
    invite: com.frerox.toolz.data.whisper.GroupInviteInfo,
    working: Boolean,
    onJoin: () -> Unit,
    onDecline: () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupAvatar(name = invite.groupName, groupId = invite.groupId, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        invite.groupName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${invite.invitedByName} · ${stringResource(R.string.st_Whisper_Groups_InvitedLabel)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolzExpressiveButton(
                    onClick = { haptic.click(); onJoin() },
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) {
                    if (working) {
                        GroupUiInlineLoading()
                    } else {
                        Text(stringResource(R.string.st_Whisper_Groups_Join), fontWeight = FontWeight.Bold)
                    }
                }
                ToolzTonalExpressiveButton(
                    onClick = { haptic.click(); onDecline() },
                    enabled = !working,
                    modifier = Modifier.weight(1f),
                ) {
                    if (working) {
                        GroupUiInlineLoading()
                    } else {
                        Text(stringResource(R.string.st_Whisper_Groups_Decline), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Swipe-to-mute row: end-to-start swipe toggles the group mute. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableGroupRow(
    muted: Boolean,
    onToggleMute: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            haptic.click()
            onToggleMute()
            dismissState.reset()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    if (muted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                    contentDescription = stringResource(
                        if (muted) R.string.st_Whisper_Groups_CdUnmute else R.string.st_Whisper_Groups_CdMute,
                    ),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(end = 20.dp).size(22.dp),
                )
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
    val bitmap = remember(picture) {
        picture?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
    }
    ExpressiveCard(
        onClick = { haptic.click(); onOpen() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.st_Whisper_Groups_CdPicture),
                    modifier = Modifier.size(52.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                GroupAvatar(name = name, groupId = groupId, size = 52.dp)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (muted) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Rounded.NotificationsOff,
                            contentDescription = stringResource(R.string.st_Whisper_Groups_CdMute),
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (preview != null && preview.time.isNotBlank()) {
                        Text(
                            formatGroupListTime(preview.time),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (preview.unread > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (preview.unread > 0) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (preview != null && (preview.text.isNotBlank() || preview.hasImage || preview.hasPoll)) {
                            val sender = preview.senderName.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()
                            sender + preview.text.ifBlank {
                                when {
                                    preview.hasPoll -> stringResource(R.string.st_Whisper_Groups_Poll)
                                    preview.hasImage -> stringResource(R.string.st_Whisper_Groups_Photo)
                                    else -> ""
                                }
                            }
                        } else {
                            "$memberCount ${stringResource(R.string.st_Whisper_Groups_Members)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if ((preview?.unread ?: 0) > 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if ((preview?.unread ?: 0) > 0) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val unreadCount = preview?.unread ?: 0
                    if (preview?.mentionedMe == true) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier.clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.st_Whisper_Groups_MentionedYou),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                    if (unreadCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (unreadCount > 99) "99+" else "$unreadCount",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Row timestamp: localized time today, Yesterday, else a medium date. */
@Composable
internal fun formatGroupListTime(iso: String): String {
    val yesterday = stringResource(R.string.st_Whisper_Groups_Yesterday)
    val context = LocalContext.current
    return runCatching {
        val odt = java.time.OffsetDateTime.parse(iso)
        val today = java.time.LocalDate.now()
        val date = odt.toLocalDate()
        when {
            date.isEqual(today) -> java.text.DateFormat.getTimeInstance(
                java.text.DateFormat.SHORT, context.resources.configuration.locales.get(0),
            ).format(java.util.Date.from(odt.toInstant()))
            date.isEqual(today.minusDays(1)) -> yesterday
            else -> java.text.DateFormat.getDateInstance(
                java.text.DateFormat.MEDIUM, context.resources.configuration.locales.get(0),
            ).format(java.util.Date.from(odt.toInstant()))
        }
    }.getOrDefault("")
}


/** Inline empty state (the header pill above is the single create entry). */
@Composable
private fun GroupEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            stringResource(R.string.st_Whisper_Groups_Empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Deterministic group avatar: initial on a stable container color. */
@Composable
fun GroupAvatar(name: String, groupId: String, size: Dp) {
    val containers = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
    )
    val (bg, fg) = containers[kotlin.math.abs(groupId.hashCode()) % containers.size]
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull()?.uppercase() ?: "G",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = fg,
        )
    }
}

@Composable
private fun CreateGroupDialog(
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
    val canCreate = name.isNotBlank() && picked.isNotEmpty() && !state.isLoading
    val visibleFriends = remember(state.friends, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) state.friends
        else state.friends.filter {
            it.effectiveName.lowercase().contains(q) || it.username.lowercase().contains(q)
        }
    }
    val pictureTooBig = stringResource(R.string.st_Whisper_Error_ImageTooLarge)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pictureError = null
        runCatching {
            context.contentResolver.getType(uri)?.let { pictureMime = it }
            context.contentResolver.openInputStream(uri)?.use {
                com.frerox.toolz.data.whisper.readBoundedGroupImageBytes(it)
            }
        }.onSuccess { bytes ->
            if (bytes != null) pictureBytes = bytes
        }.onFailure {
            pictureError = pictureTooBig
        }
    }
    val previewBitmap = remember(pictureBytes) {
        pictureBytes?.let {
            runCatching {
                android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
            }.getOrNull()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with picture picker + live preview.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape).clickable { picker.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = GroupCd.picture(),
                                modifier = Modifier.size(64.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            GroupAvatar(name = name.ifBlank { "G" }, groupId = name.ifBlank { "preview" }, size = 64.dp)
                        }
                        Box(
                            modifier = Modifier.size(64.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.BottomEnd,
                        ) {
                            Icon(
                                Icons.Rounded.AddPhotoAlternate,
                                contentDescription = GroupCd.picture(),
                                tint = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(20.dp).padding(2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.st_Whisper_Groups_CreateTitle),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            if (picked.isEmpty()) stringResource(R.string.st_Whisper_Groups_AddMembers)
                            else "${picked.size} ${stringResource(R.string.st_Whisper_Groups_Members)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = GroupCd.close())
                    }
                }
                pictureError?.let { err ->
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp).groupFadingEdge(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Spacer(Modifier.size(2.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 64) name = it },
                        label = { Text(stringResource(R.string.st_Whisper_Groups_NameHint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (picked.isNotEmpty()) {
                        PickedChipsRow(
                            picked = picked.toList(),
                            friends = state.friends,
                            onRemove = { picked.remove(it) },
                        )
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.st_Whisper_Groups_SearchHint)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = GroupCd.search()) },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.st_Whisper_Groups_WhoCanInvite),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = adminsOnly,
                            onClick = { adminsOnly = true },
                            label = { Text(stringResource(R.string.st_Whisper_Groups_InviteAdmins)) },
                        )
                        FilterChip(
                            selected = !adminsOnly,
                            onClick = { adminsOnly = false },
                            label = { Text(stringResource(R.string.st_Whisper_Groups_InviteAll)) },
                        )
                    }
                    if (visibleFriends.isEmpty()) {
                        Text(
                            stringResource(R.string.st_Whisper_Groups_NoFriendsFound),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    visibleFriends.forEach { friend ->
                        val selected = picked.contains(friend.id)
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                                if (selected) picked.remove(friend.id) else picked.add(friend.id)
                            }.background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            ).padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WhisperAvatar(profile = friend, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(friend.effectiveName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!friend.username.isBlank() && friend.displayName?.isNotBlank() == true) {
                                    Text(
                                        "@${friend.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Checkbox(
                                checked = selected,
                                onCheckedChange = {
                                    if (selected) picked.remove(friend.id) else picked.add(friend.id)
                                },
                            )
                        }
                    }
                    GroupUiBanner(
                        message = state.error.orEmpty(),
                        visible = state.error != null,
                        isError = true,
                        onDismiss = { viewModel.clearError() },
                    )
                    Spacer(Modifier.size(4.dp))
                }
                // Bottom action bar.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.st_Whisper_Cancel)) }
                    ToolzExpressiveButton(
                        onClick = { viewModel.create(name, picked.toList(), adminsOnly, pictureBytes, pictureMime) },
                        enabled = canCreate,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isLoading) GroupUiInlineLoading() else
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

/** Removable chips for the picked members (tap × to drop without scrolling). */
@Composable
private fun PickedChipsRow(
    picked: List<String>,
    friends: List<com.frerox.toolz.data.whisper.WhisperProfile>,
    onRemove: (String) -> Unit,
) {
    val byId = remember(friends) { friends.associateBy { it.id } }
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        picked.forEach { id ->
            FilterChip(
                selected = true,
                onClick = { onRemove(id) },
                label = { Text(byId[id]?.effectiveName ?: "…", maxLines = 1) },
                trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.st_Whisper_Groups_Remove), modifier = Modifier.size(14.dp)) },
            )
        }
    }
}

/**
 * Shared degraded-log banner (chat + info screens): the event log carries an
 * unverifiable event, so membership/history cover the verified prefix only.
 * Non-private so both screens reuse the identical copy and styling.
 */
@Composable
fun GroupDegradedBanner() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.st_Whisper_Groups_Degraded),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/**
 * Shared fullscreen group-image viewer (chat bubbles + info gallery).
 * Non-private so both screens reuse it. Save mirrors the 1:1 save-to-gallery
 * path (MediaStore, no extra permission on Q+).
 */
@Composable
fun GroupImageViewer(bytes: ByteArray, onDismiss: () -> Unit, onMessage: (String) -> Unit = {}) {
    val bitmap = remember(bytes) {
        runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }
    val context = LocalContext.current
    val saveScope = rememberCoroutineScope()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.st_Whisper_Groups_Photo),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
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
                    Icon(Icons.Rounded.Download, contentDescription = GroupCd.save(), tint = MaterialTheme.colorScheme.surface)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = GroupCd.close(), tint = MaterialTheme.colorScheme.surface)
                }
            }
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

/** Returns true for a bare UUID or a `WHISPER-GROUP:<uuid>` QR payload. */
internal fun isGroupJoinIdValid(raw: String): Boolean {
    val id = raw.trim().removePrefix("WHISPER-GROUP:")
    return id.isNotBlank() && runCatching { java.util.UUID.fromString(id) }.isSuccess
}

/** Join-by-ID: paste a group id (or scanned QR payload) to request access. */
@Composable
private fun JoinByIdDialog(onDismiss: () -> Unit, onRequest: (String) -> Unit) {
    var id by remember { mutableStateOf("") }
    val trimmed = id.trim().removePrefix("WHISPER-GROUP:")
    val showError = id.isNotBlank() && runCatching { java.util.UUID.fromString(trimmed) }.isFailure
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.st_Whisper_Groups_JoinById), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text(stringResource(R.string.st_Whisper_Groups_JoinByIdHint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    isError = showError,
                    supportingText = if (showError) {
                        {
                            Text(
                                stringResource(R.string.st_Whisper_Groups_InvalidId),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            ToolzExpressiveButton(
                onClick = { onRequest(trimmed) },
                enabled = isGroupJoinIdValid(id),
            ) {
                Text(stringResource(R.string.st_Whisper_Groups_JoinById), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.st_Whisper_Cancel)) }
        },
    )
}
