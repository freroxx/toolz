/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        if (!state.isLoading && state.groups.isEmpty() && state.error == null) {
            GroupEmptyState(onCreate = { showCreate = true })
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

/** Inline empty state with icon + create affordance (tab-level empty stays 1:1). */
@Composable
private fun GroupEmptyState(onCreate: () -> Unit) {
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
        ToolzTonalExpressiveButton(onClick = onCreate) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.st_Whisper_Groups_New), style = MaterialTheme.typography.labelLarge)
        }
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
    var name by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    var adminsOnly by remember { mutableStateOf(true) }
    val canCreate = name.isNotBlank() && picked.isNotEmpty() && !state.isLoading

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.st_Whisper_Groups_CreateTitle), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 64) name = it },
                    label = { Text(stringResource(R.string.st_Whisper_Groups_NameHint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.st_Whisper_Groups_AddMembers), style = MaterialTheme.typography.labelLarge)
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                    items(state.friends, key = { it.id }) { friend ->
                        val selected = picked.contains(friend.id)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (selected) picked.remove(friend.id) else picked.add(friend.id)
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = {
                                    if (selected) picked.remove(friend.id) else picked.add(friend.id)
                                },
                            )
                            Spacer(Modifier.width(4.dp))
                            WhisperAvatar(profile = friend, size = 40.dp)
                            Spacer(Modifier.width(10.dp))
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
                        }
                    }
                }
                Text(stringResource(R.string.st_Whisper_Groups_WhoCanInvite), style = MaterialTheme.typography.labelLarge)
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
                state.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            ToolzExpressiveButton(
                onClick = { viewModel.create(name, picked.toList(), adminsOnly) },
                enabled = canCreate,
            ) {
                Text(
                    if (picked.isEmpty()) stringResource(R.string.st_Whisper_Groups_Create)
                    else "${stringResource(R.string.st_Whisper_Groups_Create)} (${picked.size})",
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.st_Whisper_Cancel)) }
        },
    )
}
