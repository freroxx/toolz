/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.groupsEnabled
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.theme.toolzBackground

/**
 * Phase-2 groups: admin console + member list + danger zone.
 * Owner = first admin (creation order); admins manage members, rename,
 * invites follow the group's invite mode; everyone gets mute + leave.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperGroupInfoScreen(
    onNavigateBack: () -> Unit,
    viewModel: WhisperGroupInfoViewModel = hiltViewModel(),
) {
    if (!groupsEnabled()) return
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }
    var pendingBlock by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.left) {
        if (state.left) onNavigateBack()
    }

    val membership = state.membership
    val iAmAdmin = membership?.isAdmin(state.myId) == true
    val canInvite = membership?.canInvite(state.myId) == true

    Scaffold(
        modifier = Modifier.fillMaxSize().toolzBackground(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.st_Whisper_Groups_Info), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        },
    ) { padding ->
        if (state.isLoading && membership == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.size(4.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    GroupAvatar(name = membership?.name?.ifBlank { "Group" } ?: "Group", groupId = viewModel.groupId, size = 84.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        membership?.name?.ifBlank { "Group" } ?: "Group",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "${state.members.size} ${stringResource(R.string.st_Whisper_Groups_Members)} · " +
                            if (membership?.inviteAdminsOnly == true) stringResource(R.string.st_Whisper_Groups_InviteAdmins)
                            else stringResource(R.string.st_Whisper_Groups_InviteAll),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (iAmAdmin) {
                        TextButton(onClick = { showRename = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.st_Whisper_Groups_Rename))
                        }
                    }
                }
            }
            item {
                MuteRow(muted = state.isMuted, onToggle = { viewModel.toggleMute() })
            }
            item {
                SectionHeader(stringResource(R.string.st_Whisper_Groups_Members))
            }
            if (canInvite) {
                item {
                    ToolzExpressiveButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.st_Whisper_Groups_AddMembers), fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(state.members, key = { it.userId }) { member ->
                MemberRow(
                    name = member.name,
                    userId = member.userId,
                    role = member.role,
                    isMe = member.isMe,
                    iAmAdmin = iAmAdmin,
                    onPromote = { viewModel.promoteMember(member.userId) },
                    onRemove = { viewModel.removeMember(member.userId) },
                    onBlock = { pendingBlock = member.userId },
                )
            }
            item {
                Spacer(Modifier.size(4.dp))
                ToolzExpressiveButton(
                    onClick = { showLeave = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.st_Whisper_Groups_Leave), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(24.dp))
            }
            state.error?.let { err ->
                item {
                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showAdd) {
        AddMemberDialog(viewModel = viewModel, knownIds = state.members.map { it.userId }.toSet(), onDismiss = { showAdd = false })
    }
    if (showRename) {
        RenameDialog(
            current = membership?.name.orEmpty(),
            onDismiss = { showRename = false },
            onConfirm = { viewModel.rename(it); showRename = false },
        )
    }
    if (showLeave) {
        AlertDialog(
            onDismissRequest = { showLeave = false },
            title = { Text(stringResource(R.string.st_Whisper_Groups_Leave), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.st_Whisper_Groups_LeaveConfirm)) },
            confirmButton = {
                ToolzExpressiveButton(onClick = { showLeave = false; viewModel.leave() }) {
                    Text(stringResource(R.string.st_Whisper_Groups_Leave), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeave = false }) { Text(stringResource(R.string.st_Whisper_Cancel)) }
            },
        )
    }
    pendingBlock?.let { uid ->
        AlertDialog(
            onDismissRequest = { pendingBlock = null },
            title = { Text(stringResource(R.string.st_Whisper_Groups_Block), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.st_Whisper_Groups_BlockConfirm)) },
            confirmButton = {
                ToolzExpressiveButton(onClick = { pendingBlock = null; viewModel.blockMember(uid) }) {
                    Text(stringResource(R.string.st_Whisper_Groups_Block), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBlock = null }) { Text(stringResource(R.string.st_Whisper_Cancel)) }
            },
        )
    }
}

@Composable
private fun MuteRow(muted: Boolean, onToggle: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                if (muted) stringResource(R.string.st_Whisper_Groups_Unmute) else stringResource(R.string.st_Whisper_Groups_Mute),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = muted, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun MemberRow(
    name: String,
    userId: String,
    role: String,
    isMe: Boolean,
    iAmAdmin: Boolean,
    onPromote: () -> Unit,
    onRemove: () -> Unit,
    onBlock: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GroupAvatar(name = name, groupId = userId, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (role == "admin") {
                        Text(
                            stringResource(R.string.st_Whisper_Groups_Admin),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (expanded && !isMe) {
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (iAmAdmin && role != "admin") {
                        TextButton(onClick = onPromote) {
                            Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.st_Whisper_Groups_MakeAdmin))
                        }
                    }
                    if (iAmAdmin) {
                        TextButton(onClick = onRemove) {
                            Icon(Icons.Rounded.PersonRemove, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.st_Whisper_Groups_Remove))
                        }
                    }
                    TextButton(onClick = onBlock) {
                        Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.st_Whisper_Groups_Block))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMemberDialog(
    viewModel: WhisperGroupInfoViewModel,
    knownIds: Set<String>,
    onDismiss: () -> Unit,
) {
    var friends by remember { mutableStateOf<List<com.frerox.toolz.data.whisper.WhisperProfile>>(emptyList()) }
    var picked by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.loadFriends { friends = it.filter { it.id !in knownIds } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.st_Whisper_Groups_AddMembers), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                items(friends, key = { it.id }) { friend ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            picked = if (picked == friend.id) null else friend.id
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = picked == friend.id, onCheckedChange = {
                            picked = if (picked == friend.id) null else friend.id
                        })
                        Spacer(Modifier.width(8.dp))
                        Text(friend.effectiveName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            ToolzExpressiveButton(onClick = { picked?.let { viewModel.addMember(it) }; onDismiss() }) {
                Text(stringResource(R.string.st_Whisper_Groups_AddMembers), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.st_Whisper_Cancel)) }
        },
    )
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.st_Whisper_Groups_Rename), fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 64) name = it },
                label = { Text(stringResource(R.string.st_Whisper_Groups_NameHint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            ToolzExpressiveButton(onClick = { onConfirm(name) }) {
                Text(stringResource(R.string.st_Whisper_Groups_Rename), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.st_Whisper_Cancel)) }
        },
    )
}
