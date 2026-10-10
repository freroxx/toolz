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
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(state.createdGroupId) {
        state.createdGroupId?.let {
            viewModel.consumeCreated()
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
            ToolzTonalExpressiveButton(onClick = { showCreate = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.st_Whisper_Groups_New), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (state.isLoading && state.groups.isEmpty()) {
            repeat(2) { ConversationSkeleton() }
        }
        state.error?.let { err ->
            GroupErrorBanner(message = err, onRetry = { viewModel.load() })
        }
        if (!state.isLoading && state.groups.isEmpty() && state.invites.isEmpty() && state.error == null) {
            GroupEmptyState()
        }
        state.invites.forEach { invite ->
            GroupInviteCard(
                invite = invite,
                onJoin = { viewModel.join(invite.groupId, onNavigateToGroup) },
                onDecline = { viewModel.decline(invite.groupId) },
            )
        }
        state.groups.forEach { group ->
            GroupRowCard(
                name = group.name.ifBlank { "Group" },
                subtitle = "${state.memberCounts[group.id] ?: 0} ${stringResource(R.string.st_Whisper_Groups_Members)}",
                groupId = group.id,
                muted = group.id in mutedIds,
                onOpen = { onNavigateToGroup(group.id) },
            )
        }
    }

    if (showCreate) {
        CreateGroupDialog(
            viewModel = viewModel,
            onDismiss = { showCreate = false },
        )
    }
}

/** Persistent invite card: survives restarts, dies only by join/decline/cancel. */
@Composable
private fun GroupInviteCard(
    invite: com.frerox.toolz.data.whisper.GroupInviteInfo,
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
                ToolzExpressiveButton(onClick = { haptic.click(); onJoin() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.st_Whisper_Groups_Join), fontWeight = FontWeight.Bold)
                }
                ToolzTonalExpressiveButton(onClick = { haptic.click(); onDecline() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.st_Whisper_Groups_Decline), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GroupRowCard(
    name: String,
    subtitle: String,
    groupId: String,
    muted: Boolean,
    onOpen: () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    ExpressiveCard(
        onClick = { haptic.click(); onOpen() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GroupAvatar(name = name, groupId = groupId, size = 52.dp)
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
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Compact error + retry banner, mirroring the tab's initial-load banner. */
@Composable
private fun GroupErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ToolzTonalExpressiveButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.st_Whisper_Retry), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
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
    val canCreate = name.isNotBlank() && picked.isNotEmpty() && !state.isLoading
    val visibleFriends = remember(state.friends, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) state.friends
        else state.friends.filter {
            it.effectiveName.lowercase().contains(q) || it.username.lowercase().contains(q)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.getType(uri)?.let { pictureMime = it }
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()?.let { pictureBytes = it }
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
                                contentDescription = null,
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
                                contentDescription = null,
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
                        Icon(Icons.Rounded.Close, contentDescription = null)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
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
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
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
                    state.error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
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
                trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp)) },
            )
        }
    }
}
