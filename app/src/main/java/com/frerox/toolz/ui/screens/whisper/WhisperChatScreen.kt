/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.ClearChatTimeRange
import com.frerox.toolz.data.whisper.FriendStatus
import com.frerox.toolz.data.whisper.WhisperMessage
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.toolzBackground

/**
 * Individual conversation screen with 3-dot feature menu (Clear Chat, Mute, Block).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WhisperChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
    viewModel: WhisperChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = rememberToolzHapticFeedback()
    val toastState = rememberWhisperToastState()

    var showMenu by remember { mutableStateOf(false) }
    var showClearChatSheet by remember { mutableStateOf(false) }
    var showMuteDialog by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            toastState.show(it, WhisperToastType.ERROR)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                ExpressiveTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = uiState.otherUser != null) {
                                    haptic.click()
                                    uiState.otherUser?.id?.let { onNavigateToProfile(it) }
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        ) {
                            uiState.otherUser?.let { user ->
                                WhisperAvatar(user, 36.dp)
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(user.effectiveName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        if (uiState.isMuted) {
                                            Icon(
                                                Icons.Rounded.NotificationsOff,
                                                contentDescription = "Muted",
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Text("@${user.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } ?: Text("Chat", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        ToolzExpressiveIconButton(onClick = { haptic.click(); onNavigateBack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_Back))
                        }
                    },
                    actions = {
                        val status = uiState.friendStatus
                        if (status == FriendStatus.ACCEPTED) {
                            Icon(
                                Icons.Rounded.VerifiedUser,
                                contentDescription = stringResource(R.string.st_Whisper_Friends_Accept),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // 3-dot feature menu button
                        Box {
                            ToolzExpressiveIconButton(onClick = { haptic.click(); showMenu = !showMenu }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Chat options")
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                // Feature 1: Clear Chat
                                DropdownMenuItem(
                                    text = { Text("Clear chat") },
                                    leadingIcon = { Icon(Icons.Rounded.CleaningServices, null) },
                                    onClick = {
                                        showMenu = false
                                        haptic.click()
                                        showClearChatSheet = true
                                    }
                                )

                                // Undo Clear Chat option if active
                                if (uiState.clearedUndoMessagesCount > 0) {
                                    DropdownMenuItem(
                                        text = { Text("Undo last clear (${uiState.clearedUndoMessagesCount} msgs)") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Undo, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            showMenu = false
                                            haptic.success()
                                            viewModel.undoClearChat()
                                            toastState.show("Messages restored", WhisperToastType.SUCCESS)
                                        }
                                    )
                                }

                                // Feature 2: Mute / Unmute
                                DropdownMenuItem(
                                    text = { Text(if (uiState.isMuted) "Unmute notifications" else "Mute notifications") },
                                    leadingIcon = {
                                        Icon(
                                            if (uiState.isMuted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                                            null
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        haptic.click()
                                        if (uiState.isMuted) {
                                            viewModel.toggleMute()
                                            toastState.show("Notifications unmuted", WhisperToastType.INFO)
                                        } else {
                                            showMuteDialog = true
                                        }
                                    }
                                )

                                HorizontalDivider()

                                // Feature 3: Block / Unblock
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (uiState.isBlockedByMe) "Unblock user" else "Block user",
                                            color = if (uiState.isBlockedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (uiState.isBlockedByMe) Icons.Rounded.LockOpen else Icons.Rounded.Block,
                                            null,
                                            tint = if (uiState.isBlockedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        haptic.click()
                                        if (uiState.isBlockedByMe) {
                                            viewModel.toggleBlock()
                                            toastState.show("User unblocked", WhisperToastType.INFO)
                                        } else {
                                            showBlockConfirmDialog = true
                                        }
                                    }
                                )
                            }
                        }
                    },
                )
            },
            containerColor = Color.Transparent,
            modifier = Modifier.toolzBackground(),
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding(),
            ) {
                // Friend gate banner: ONLY show when friend status is loaded and not accepted
                AnimatedVisibility(
                    visible = uiState.isFriendStatusLoaded && uiState.friendStatus != FriendStatus.ACCEPTED && !uiState.isBlockedByMe && !uiState.isBlockedByOther,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    FriendGateBanner(
                        friendStatus = uiState.friendStatus,
                        iAmRequester = uiState.iAmRequester,
                        onSendRequest = { haptic.success(); viewModel.sendFriendRequest() },
                    )
                }

                // Blocked state banners
                if (uiState.isBlockedByMe) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                            Text(
                                "You have blocked this user. Unblock to resume conversation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            ToolzTonalExpressiveButton(onClick = { viewModel.toggleBlock() }) {
                                Text("Unblock")
                            }
                        }
                    }
                } else if (uiState.isBlockedByOther) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Text(
                                "You cannot message this user.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Messages list
                val listState = rememberLazyListState()
                val messages = uiState.messages

                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.lastIndex)
                    }
                }

                if (uiState.isLoading && messages.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ToolzLoadingIndicator()
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                            val isMine = message.isSentByMe(viewModel.myUserId)
                            val isPending = message.id.startsWith("pending_")

                            val showDateSeparator = index == 0 ||
                                messages[index - 1].createdAt.extractDate() != message.createdAt.extractDate()

                            if (showDateSeparator) {
                                DateSeparator(message.createdAt.extractDate())
                            }

                            MessageBubble(
                                message = message,
                                isMine = isMine,
                                isPending = isPending,
                            )
                        }
                    }
                }

                // Undo banner for cleared messages
                AnimatedVisibility(
                    visible = uiState.clearedUndoMessagesCount > 0,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Rounded.CleaningServices, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(16.dp))
                            Text(
                                "Cleared ${uiState.clearedUndoMessagesCount} messages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            ToolzTonalExpressiveButton(
                                onClick = {
                                    haptic.success()
                                    viewModel.undoClearChat()
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Undo, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Undo (60s)")
                            }
                        }
                    }
                }

                // Typing indicator banner
                AnimatedVisibility(
                    visible = uiState.isPartnerTyping,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "${uiState.otherUser?.effectiveName ?: "Partner"} is typing…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // Message input bar
                val canSendMessage = uiState.friendStatus == FriendStatus.ACCEPTED && !uiState.isBlockedByMe && !uiState.isBlockedByOther
                val draftText by viewModel.draftText.collectAsStateWithLifecycle()
                MessageInputBar(
                    enabled = canSendMessage,
                    draftText = draftText,
                    onDraftChanged = { viewModel.updateDraft(it) },
                    onSend = { text ->
                        haptic.success()
                        viewModel.sendMessage(text)
                    },
                )
            }
        }

        // Expressive Toast Host
        WhisperToastHost(
            hostState = toastState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
        )
    }

    // Clear Chat Bottom Sheet
    if (showClearChatSheet) {
        ClearChatSheet(
            onDismiss = { showClearChatSheet = false },
            onSelectRange = { range ->
                showClearChatSheet = false
                haptic.click()
                viewModel.clearChat(range)
                toastState.show("Clearing chat… (60s to undo)", WhisperToastType.INFO)
            }
        )
    }

    // Mute Dialog
    if (showMuteDialog) {
        MuteOptionsDialog(
            onDismiss = { showMuteDialog = false },
            onSelectDuration = { durationMs ->
                showMuteDialog = false
                haptic.click()
                viewModel.toggleMute(durationMs)
                toastState.show("Notifications muted", WhisperToastType.INFO)
            }
        )
    }

    // Block Confirmation Dialog
    if (showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            icon = { Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Block ${uiState.otherUser?.effectiveName ?: "this user"}?") },
            text = {
                Text("Blocked users cannot send you messages or view your online activity. You can unblock at any time from this menu.")
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        showBlockConfirmDialog = false
                        haptic.error()
                        viewModel.toggleBlock()
                        toastState.show("User blocked", WhisperToastType.INFO)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Block")
                }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearChatSheet(
    onDismiss: () -> Unit,
    onSelectRange: (ClearChatTimeRange) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.CleaningServices, null, tint = MaterialTheme.colorScheme.primary)
                Text("Clear Chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                "Choose time period to clear your messages. You'll have 60 seconds to undo if needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            val options = listOf(
                Pair("Past 24 hours", ClearChatTimeRange.PAST_24_HOURS),
                Pair("Past 7 days", ClearChatTimeRange.PAST_7_DAYS),
                Pair("Past 30 days", ClearChatTimeRange.PAST_30_DAYS),
                Pair("All time (Everything)", ClearChatTimeRange.ALL_TIME),
            )

            options.forEach { (label, range) ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectRange(range) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MuteOptionsDialog(
    onDismiss: () -> Unit,
    onSelectDuration: (Long) -> Unit,
) {
    val durations = listOf(
        Pair("1 Hour", 3_600_000L),
        Pair("8 Hours", 8 * 3_600_000L),
        Pair("1 Week", 7 * 24 * 3_600_000L),
        Pair("Until I turn it back on", Long.MAX_VALUE),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.NotificationsOff, null) },
        title = { Text("Mute notifications", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                durations.forEach { (label, durationMs) ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDuration(durationMs) }
                    ) {
                        Row(modifier = Modifier.padding(14.dp)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            ToolzOutlinedExpressiveButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FriendGateBanner(
    friendStatus: FriendStatus,
    iAmRequester: Boolean,
    onSendRequest: () -> Unit,
) {
    val (icon, message, showButton) = when {
        friendStatus == FriendStatus.PENDING && iAmRequester ->
            Triple(Icons.Rounded.HourglassTop, stringResource(R.string.st_Whisper_Chat_FriendGateSent), false)
        friendStatus == FriendStatus.PENDING && !iAmRequester ->
            Triple(Icons.Rounded.PersonAdd, stringResource(R.string.st_Whisper_Chat_FriendGateReceived), false)
        friendStatus == FriendStatus.NONE ->
            Triple(Icons.Rounded.Lock, stringResource(R.string.st_Whisper_Chat_FriendGateNone), true)
        else ->
            Triple(Icons.Rounded.Block, stringResource(R.string.st_Whisper_Chat_FriendGateBlocked), false)
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            if (showButton) {
                ToolzTonalExpressiveButton(onClick = onSendRequest) {
                    Text(stringResource(R.string.st_Whisper_Chat_AddFriend), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DateSeparator(date: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            date,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MessageBubble(
    message: WhisperMessage,
    isMine: Boolean,
    isPending: Boolean = false,
) {
    val bubbleShape = when {
        isMine  -> RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp,  bottomStart = 20.dp, bottomEnd = 20.dp)
        else    -> RoundedCornerShape(topStart = 4.dp,  topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    }
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor   = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Column(
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .alpha(if (isPending) 0.7f else 1f)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column {
                Text(
                    message.content,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    if (isPending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = textColor.copy(alpha = 0.6f)
                        )
                    } else {
                        Text(
                            message.createdAt.formatTimestamp(),
                            color = textColor.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (isMine) {
                            Icon(
                                if (message.isRead) Icons.Rounded.DoneAll else Icons.Rounded.Done,
                                contentDescription = if (message.isRead) "Read" else "Sent",
                                tint = if (message.isRead) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageInputBar(
    enabled: Boolean,
    draftText: String,
    onDraftChanged: (String) -> Unit,
    onSend: (String) -> Unit,
) {
    val canSend = draftText.isNotBlank() && enabled

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draftText,
                onValueChange = onDraftChanged,
                placeholder = {
                    Text(
                        if (enabled) stringResource(R.string.st_Whisper_Chat_InputPlaceholder) else stringResource(R.string.st_Whisper_Chat_InputPlaceholderDisabled),
                        color = MaterialTheme.colorScheme.outline,
                    )
                },
                enabled = enabled,
                minLines = 1,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = ExtraLargeExpressiveShape,
                modifier = Modifier.weight(1f),
            )

            val sendScale by animateFloatAsState(
                targetValue = if (canSend) 1f else 0.85f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                label = "sendBtnScale"
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .animateContentSize()
                    .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                    .clip(CircleShape)
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .bouncyClick(enabled = canSend, onClick = {
                        onSend(draftText.trim())
                    }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = null,
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Extract date string (YYYY-MM-DD) from ISO timestamp */
fun String.extractDate(): String = if (length >= 10) take(10) else ""
