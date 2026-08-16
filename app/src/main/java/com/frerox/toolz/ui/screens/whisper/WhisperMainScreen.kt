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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.*
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch

/**
 * The main Whisper hub screen with 3 bottom-nav tabs:
 * Chats (Merged Chats & Friends) · Discover · Profile
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WhisperMainScreen(
    onNavigateToChat: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: WhisperViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    val betaWarningShown by viewModel.betaWarningShown.collectAsStateWithLifecycle()
    val haptic = rememberToolzHapticFeedback()
    val toastState = rememberWhisperToastState()
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    var isLoggingOut by remember { mutableStateOf(false) }

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated == false && !isLoggingOut) {
            isLoggingOut = true
            onLoggedOut()
        }
    }

    if (isAuthenticated == null) {
        Box(Modifier.fillMaxSize().toolzBackground(), contentAlignment = Alignment.Center) {
            ToolzLoadingIndicator()
        }
        return
    }

    var selectedFriendForOptions by remember { mutableStateOf<WhisperProfile?>(null) }
    var selectedConvoForOptions by remember { mutableStateOf<WhisperConversation?>(null) }
    var showAvatarOptions by remember { mutableStateOf(false) }
    var profileForFullView by remember { mutableStateOf<WhisperProfile?>(null) }

    var hasUnsavedProfileChanges by remember { mutableStateOf(false) }
    var pendingTabSwitchIndex by remember { mutableStateOf<Int?>(null) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var triggerProfileSaveFromDialog by remember { mutableStateOf(0) }
    var triggerProfileDiscardFromDialog by remember { mutableStateOf(0) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            toastState.show(it, WhisperToastType.ERROR)
            viewModel.clearError()
        }
    }

    val tabs = listOf(
        Triple(stringResource(R.string.st_Whisper_Tab_Chats), Icons.AutoMirrored.Rounded.Chat, Icons.AutoMirrored.Rounded.Chat),
        Triple(stringResource(R.string.st_Whisper_Tab_Discover), Icons.Rounded.Explore, Icons.Rounded.Explore),
        Triple(stringResource(R.string.st_Whisper_Tab_Profile), Icons.Rounded.Person, Icons.Rounded.Person),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                ExpressiveTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.st_Whisper_Title), fontWeight = FontWeight.Black)
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    stringResource(R.string.st_Whisper_Beta_Badge),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    actions = {
                        val totalUnread = uiState.conversations.sumOf { it.unreadCount }
                        if (totalUnread > 0 && pagerState.currentPage != 0) {
                            Badge { Text(totalUnread.coerceAtMost(99).toString()) }
                            Spacer(Modifier.width(16.dp))
                        }
                    }
                )
            },
            bottomBar = {
                ExpressiveNavigationBar {
                    tabs.forEachIndexed { index, (label, icon, selectedIcon) ->
                        val unread = if (index == 0) uiState.conversations.sumOf { it.unreadCount } else 0
                        val pendingCount = if (index == 0) uiState.pendingIncoming.size else 0
                        ExpressiveNavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                haptic.click()
                                if (pagerState.currentPage == 2 && hasUnsavedProfileChanges && index != 2) {
                                    pendingTabSwitchIndex = index
                                    showUnsavedChangesDialog = true
                                } else {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                }
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (unread > 0 || pendingCount > 0) {
                                            Badge { Text(((unread + pendingCount).coerceAtMost(99)).toString()) }
                                        }
                                    }
                                ) {
                                    Icon(if (pagerState.currentPage == index) selectedIcon else icon, label)
                                }
                            },
                            label = { Text(label) },
                        )
                    }
                }
            },
            containerColor = Color.Transparent,
            modifier = Modifier.toolzBackground(),
        ) { paddingValues ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.loadAll(isRefresh = true) },
                modifier = Modifier.padding(paddingValues),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = pagerState.currentPage != 2 || !hasUnsavedProfileChanges,
                ) { page ->
                    when (page) {
                        0 -> MergedChatsAndFriendsTab(
                            uiState = uiState,
                            viewModel = viewModel,
                            onNavigateToChat = onNavigateToChat,
                            onNavigateToProfile = onNavigateToProfile,
                            onLongClickConvo = { selectedConvoForOptions = it },
                            onLongClickFriend = { selectedFriendForOptions = it },
                            onViewAvatarFull = { profileForFullView = it }
                        )
                        1 -> DiscoverTab(
                            uiState = uiState,
                            viewModel = viewModel,
                            onNavigateToChat = onNavigateToChat,
                            onNavigateToProfile = onNavigateToProfile,
                            onViewAvatarFull = { profileForFullView = it }
                        )
                        2 -> ProfileTab(
                            uiState = uiState,
                            viewModel = viewModel,
                            toastState = toastState,
                            saveTrigger = triggerProfileSaveFromDialog,
                            discardTrigger = triggerProfileDiscardFromDialog,
                            onUnsavedChangesChanged = { hasUnsavedProfileChanges = it },
                            onLoggedOut = {
                                if (!isLoggingOut) {
                                    isLoggingOut = true
                                    onLoggedOut()
                                }
                            },
                            onShowAvatarOptions = { showAvatarOptions = true },
                            onViewAvatarFull = { profileForFullView = it }
                        )
                    }
                }
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

    // Material 3 Expressive Avatar Options Bottom Sheet
    if (showAvatarOptions) {
        val profile = uiState.currentProfile
        if (profile != null) {
            AvatarOptionsSheet(
                profile = profile,
                onDismiss = { showAvatarOptions = false },
                onChoosePhoto = {
                    showAvatarOptions = false
                    viewModel.triggerPickPhoto()
                },
                onDeletePhoto = {
                    haptic.click()
                    viewModel.deleteAvatar()
                    showAvatarOptions = false
                    toastState.show("Profile photo removed", WhisperToastType.INFO)
                }
            )
        }
    }

    // Material 3 Expressive Friend Options Bottom Sheet
    selectedFriendForOptions?.let { friend ->
        FriendOptionsSheet(
            friend = friend,
            onDismiss = { selectedFriendForOptions = null },
            onChat = {
                val fId = friend.id
                selectedFriendForOptions = null
                onNavigateToChat(fId)
            },
            onViewProfile = {
                val fId = friend.id
                selectedFriendForOptions = null
                onNavigateToProfile(fId)
            },
            onUnfriend = {
                val fId = friend.id
                selectedFriendForOptions = null
                haptic.click()
                viewModel.unfriend(fId)
                toastState.show("Removed friend", WhisperToastType.INFO)
            }
        )
    }

    // Material 3 Expressive Chat Options Bottom Sheet
    selectedConvoForOptions?.let { convo ->
        var isBlocked by remember(convo.otherUser.id) { mutableStateOf(false) }
        LaunchedEffect(convo.otherUser.id) {
            isBlocked = viewModel.isBlockedByMe(convo.otherUser.id)
        }
        ChatOptionsSheet(
            convo = convo,
            isBlocked = isBlocked,
            onDismiss = { selectedConvoForOptions = null },
            onViewProfile = {
                val uId = convo.otherUser.id
                selectedConvoForOptions = null
                onNavigateToProfile(uId)
            },
            onClearChat = {
                val uId = convo.otherUser.id
                selectedConvoForOptions = null
                haptic.click()
                viewModel.clearChatHistory(uId)
                toastState.show("Chat history cleared", WhisperToastType.INFO)
            },
            onToggleMute = {
                val uId = convo.otherUser.id
                selectedConvoForOptions = null
                viewModel.toggleMuteUser(uId)
                toastState.show(if (convo.isMuted) "Unmuted notifications" else "Muted notifications", WhisperToastType.INFO)
            },
            onToggleBlock = {
                val uId = convo.otherUser.id
                selectedConvoForOptions = null
                haptic.click()
                viewModel.toggleBlockUser(uId)
                toastState.show(if (isBlocked) "User unblocked" else "User blocked", WhisperToastType.INFO)
            },
            onDeleteChat = {
                val uId = convo.otherUser.id
                selectedConvoForOptions = null
                haptic.click()
                viewModel.hideChat(uId)
                toastState.show("Chat hidden", WhisperToastType.INFO)
            }
        )
    }

    // Material 3 Expressive Full Screen Avatar Dialog
    profileForFullView?.let { p ->
        val isSelf = p.id == uiState.currentProfile?.id
        FullScreenAvatarDialog(
            profile = p,
            isSelf = isSelf,
            onDismiss = { profileForFullView = null },
            onMessage = {
                if (!isSelf) {
                    val pId = p.id
                    profileForFullView = null
                    onNavigateToChat(pId)
                }
            }
        )
    }

    // Material 3 Expressive Unsaved Profile Changes Dialog
    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = {
                showUnsavedChangesDialog = false
                pendingTabSwitchIndex = null
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Save, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                }
            },
            title = {
                Text("Unsaved Changes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    "You have unsaved changes in your profile. Would you like to save them before switching tabs?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        showUnsavedChangesDialog = false
                        triggerProfileSaveFromDialog++
                        hasUnsavedProfileChanges = false
                        pendingTabSwitchIndex?.let { target ->
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                        pendingTabSwitchIndex = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save & Switch", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolzOutlinedExpressiveButton(
                        onClick = {
                            showUnsavedChangesDialog = false
                            triggerProfileDiscardFromDialog++
                            hasUnsavedProfileChanges = false
                            pendingTabSwitchIndex?.let { target ->
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                            pendingTabSwitchIndex = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Discard")
                    }
                    ToolzOutlinedExpressiveButton(
                        onClick = {
                            showUnsavedChangesDialog = false
                            pendingTabSwitchIndex = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Whisper Beta Warning Dialog
    if (betaWarningShown == false) {
        AlertDialog(
            onDismissRequest = { /* Must accept to enter */ },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                }
            },
            title = {
                Text(stringResource(R.string.st_Whisper_Beta_Warning_Title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    stringResource(R.string.st_Whisper_Beta_Warning_Desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        haptic.success()
                        viewModel.markBetaWarningAsShown()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.st_Whisper_Beta_Warning_Confirm), fontWeight = FontWeight.Bold)
                }
            },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
    }
}

// ─────────────────────────────────────────────────────────────
// MERGED CHATS + FRIENDS TAB
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MergedChatsAndFriendsTab(
    uiState: WhisperUiState,
    viewModel: WhisperViewModel,
    onNavigateToChat: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onLongClickConvo: (WhisperConversation) -> Unit,
    onLongClickFriend: (WhisperProfile) -> Unit,
    onViewAvatarFull: (WhisperProfile) -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()

    if (uiState.isLoading && uiState.conversations.isEmpty() && uiState.friends.isEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("Friends") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(5) { FriendSkeleton() }
                }
            }
            item { SectionHeader("Messages") }
            items(5) { ConversationSkeleton() }
        }
        return
    }

    if (uiState.conversations.isEmpty() && uiState.friends.isEmpty() && uiState.pendingIncoming.isEmpty()) {
        WhisperEmptyState(
            icon = Icons.AutoMirrored.Rounded.Chat,
            title = stringResource(R.string.st_Whisper_Chats_EmptyTitle),
            subtitle = "Search for users in Discover to start end-to-end encrypted chats.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges(top = 16.dp, bottom = 32.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Incoming Friend Requests Banner
        if (uiState.pendingIncomingRequests.isNotEmpty()) {
            item {
                SectionHeader("Friend Requests (${uiState.pendingIncomingRequests.size})")
            }
            items(uiState.pendingIncomingRequests, key = { "req_${it.friendship.id}" }) { reqItem ->
                FriendRequestCard(
                    reqItem = reqItem,
                    onAccept = { haptic.success(); viewModel.acceptFriendRequest(reqItem.friendship.id) },
                    onDecline = { haptic.click(); viewModel.declineFriendRequest(reqItem.friendship.id) },
                )
            }
        } else if (uiState.pendingIncoming.isNotEmpty()) {
            item {
                SectionHeader("Friend Requests (${uiState.pendingIncoming.size})")
            }
            items(uiState.pendingIncoming, key = { "req_${it.id}" }) { friendship ->
                FriendRequestCard(
                    reqItem = WhisperFriendRequestItem(friendship, null),
                    onAccept = { haptic.success(); viewModel.acceptFriendRequest(friendship.id) },
                    onDecline = { haptic.click(); viewModel.declineFriendRequest(friendship.id) },
                )
            }
        }

        // Friends Quick Bar
        if (uiState.friends.isNotEmpty()) {
            item {
                SectionHeader("Friends (${uiState.friends.size})")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uiState.friends, key = { "friend_chip_${it.id}" }) { friend ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(68.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .combinedClickable(
                                    onClick = { haptic.click(); onNavigateToChat(friend.id) },
                                    onLongClick = { haptic.longClick(); onLongClickFriend(friend) }
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            Box {
                                WhisperAvatar(
                                    profile = friend,
                                    size = 48.dp,
                                    onLongClick = { haptic.longClick(); onViewAvatarFull(friend) }
                                )
                                if (friend.onlineStatus == "Online") {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50))
                                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = friend.effectiveName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Conversations List
        if (uiState.conversations.isNotEmpty()) {
            item {
                SectionHeader("Messages (${uiState.conversations.size})")
            }
            itemsIndexed(uiState.conversations, key = { _, c -> c.otherUser.id }) { _, convo ->
                ConversationCard(
                    conversation = convo,
                    onClick = {
                        haptic.click()
                        onNavigateToChat(convo.otherUser.id)
                    },
                    onLongClick = {
                        haptic.longClick()
                        onLongClickConvo(convo)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationCard(
    conversation: WhisperConversation,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val unread = conversation.unreadCount > 0

    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WhisperAvatar(conversation.otherUser, 50.dp)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            conversation.otherUser.effectiveName,
                            fontWeight = if (unread) FontWeight.Black else FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                        Text(
                            conversation.otherUser.onlineStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (conversation.otherUser.onlineStatus == "Online") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        if (conversation.isMuted) {
                            Icon(
                                Icons.Rounded.NotificationsOff,
                                contentDescription = "Muted",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Text(
                        conversation.lastMessage.createdAt.formatTimestamp(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        conversation.lastMessage.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    if (conversation.unreadCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Text(conversation.unreadCount.coerceAtMost(99).toString(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRequestCard(
    reqItem: WhisperFriendRequestItem,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val profile = reqItem.senderProfile
    val friendship = reqItem.friendship

    ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (profile != null) {
                WhisperAvatar(profile, 44.dp)
            } else {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = profile?.effectiveName ?: stringResource(R.string.st_Whisper_Friends_FriendRequest),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (profile != null) "@${profile.effectiveUsername}" else friendship.userA.take(8) + "…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolzTonalExpressiveButton(onClick = onAccept) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.st_Whisper_Friends_Accept), style = MaterialTheme.typography.labelSmall)
                }
                ToolzOutlinedExpressiveButton(onClick = onDecline) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// DISCOVER TAB (WITH FRIENDS-OF-FRIENDS RECOMMENDATIONS)
// ─────────────────────────────────────────────────────────────

@Composable
private fun DiscoverTab(
    uiState: WhisperUiState,
    viewModel: WhisperViewModel,
    onNavigateToChat: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onViewAvatarFull: (WhisperProfile) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val haptic = rememberToolzHapticFeedback()

    LaunchedEffect(searchQuery) {
        viewModel.searchProfiles(searchQuery)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            ExpressiveSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.st_Whisper_Discover_SearchPlaceholder)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (searchQuery.isNotBlank()) {
            if (uiState.isLoading && uiState.searchResults.isEmpty()) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(5) { DiscoverSkeleton() }
                }
            } else if (uiState.searchResults.isEmpty()) {
                WhisperEmptyState(
                    icon = Icons.Rounded.PersonSearch,
                    title = stringResource(R.string.st_Whisper_Discover_NoResultsTitle),
                    subtitle = stringResource(R.string.st_Whisper_Discover_NoResultsSubtitle),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fadingEdges(top = 8.dp, bottom = 24.dp),
                ) {
                    itemsIndexed(uiState.searchResults, key = { _, p -> p.id }) { _, profile ->
                        val isAlreadyFriend = uiState.friends.any { it.id == profile.id }
                        val isPendingOutgoing = uiState.pendingOutgoing.any { it.userB == profile.id }

                        DiscoverUserCard(
                            profile = profile,
                            isAlreadyFriend = isAlreadyFriend,
                            isPendingOutgoing = isPendingOutgoing,
                            onChat = { haptic.click(); onNavigateToChat(profile.id) },
                            onViewProfile = { haptic.click(); onNavigateToProfile(profile.id) },
                            onAddFriend = { haptic.success(); viewModel.sendFriendRequest(profile.id) },
                            onViewAvatarFull = { onViewAvatarFull(profile) },
                        )
                    }
                }
            }
        } else {
            if (uiState.isLoading && uiState.recommendedProfiles.isEmpty()) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { SectionHeader("Suggested For You") }
                    items(3) { DiscoverSkeleton() }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fadingEdges(top = 8.dp, bottom = 24.dp),
                ) {
                    if (uiState.recommendedProfiles.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                SectionHeader("Suggested For You")
                            }
                        }

                        items(uiState.recommendedProfiles, key = { "rec_${it.id}" }) { profile ->
                            val isAlreadyFriend = uiState.friends.any { it.id == profile.id }
                            val isPendingOutgoing = uiState.pendingOutgoing.any { it.userB == profile.id }

                            DiscoverUserCard(
                                profile = profile,
                                isAlreadyFriend = isAlreadyFriend,
                                isPendingOutgoing = isPendingOutgoing,
                                onChat = { haptic.click(); onNavigateToChat(profile.id) },
                                onViewProfile = { haptic.click(); onNavigateToProfile(profile.id) },
                                onAddFriend = { haptic.success(); viewModel.sendFriendRequest(profile.id) },
                                onViewAvatarFull = { onViewAvatarFull(profile) },
                            )
                        }
                    } else {
                        item {
                            WhisperEmptyState(
                                icon = Icons.Rounded.Search,
                                title = stringResource(R.string.st_Whisper_Discover_EmptyTitle),
                                subtitle = stringResource(R.string.st_Whisper_Discover_EmptySubtitle),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverUserCard(
    profile: WhisperProfile,
    isAlreadyFriend: Boolean,
    isPendingOutgoing: Boolean,
    onChat: () -> Unit,
    onViewProfile: () -> Unit,
    onAddFriend: () -> Unit,
    onViewAvatarFull: () -> Unit,
) {
    ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WhisperAvatar(
                profile = profile,
                size = 48.dp,
                onClick = if (!profile.isPrivate) onViewProfile else null,
                onLongClick = onViewAvatarFull,
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(profile.effectiveName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    if (profile.isPrivate) {
                        Icon(Icons.Rounded.Lock, "Private", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                    }
                    if (isAlreadyFriend) {
                        Icon(Icons.Rounded.VerifiedUser, "Friend", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("@${profile.effectiveUsername}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                    Text(profile.onlineStatus, style = MaterialTheme.typography.labelSmall, color = if (profile.onlineStatus == "Online") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!profile.isPrivate && !profile.bio.isNullOrBlank()) {
                    Text(
                        profile.bio,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Actions row — Hide +Add button if already friends!
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolzTonalExpressiveButton(
                onClick = onChat,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Chat, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.st_Whisper_Discover_Message))
            }

            if (!isAlreadyFriend) {
                if (isPendingOutgoing) {
                    ToolzOutlinedExpressiveButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.HourglassTop, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.st_Whisper_Friends_Pending))
                    }
                } else {
                    ToolzOutlinedExpressiveButton(
                        onClick = onAddFriend,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.PersonAdd, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.st_Whisper_Discover_Add))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// MATERIAL 3 EXPRESSIVE PROFILE TAB
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTab(
    uiState: WhisperUiState,
    viewModel: WhisperViewModel,
    toastState: WhisperToastState,
    saveTrigger: Int,
    discardTrigger: Int,
    onUnsavedChangesChanged: (Boolean) -> Unit,
    onLoggedOut: () -> Unit,
    onShowAvatarOptions: () -> Unit,
    onViewAvatarFull: (WhisperProfile) -> Unit,
) {
    val profile = uiState.currentProfile ?: return
    val haptic = rememberToolzHapticFeedback()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val initialDisplayName = profile.displayName ?: ""
    val initialBio = profile.bio ?: ""
    val initialIsPrivate = profile.isPrivate
    val initialIsHidden = profile.isHiddenFromDiscover

    var displayName by remember(profile.id) { mutableStateOf(initialDisplayName) }
    var bio by remember(profile.id) { mutableStateOf(initialBio) }
    var isPrivate by remember(profile.id) { mutableStateOf(initialIsPrivate) }
    var isHidden by remember(profile.id) { mutableStateOf(initialIsHidden) }
    
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showDiscoveryWarningDialog by remember { mutableStateOf(false) }

    // Track unsaved changes and notify parent
    val hasUnsaved = displayName != initialDisplayName || bio != initialBio || 
                    isPrivate != initialIsPrivate || isHidden != initialIsHidden
    
    LaunchedEffect(hasUnsaved) {
        onUnsavedChangesChanged(hasUnsaved)
    }

    // Save trigger from UnsavedChangesDialog
    LaunchedEffect(saveTrigger) {
        if (saveTrigger > 0) {
            viewModel.updateProfile(displayName, bio, isPrivate, isHidden) {
                toastState.show("Profile saved", WhisperToastType.SUCCESS)
            }
        }
    }

    // Discard trigger from UnsavedChangesDialog
    LaunchedEffect(discardTrigger) {
        if (discardTrigger > 0) {
            displayName = initialDisplayName
            bio = initialBio
            isPrivate = initialIsPrivate
            isHidden = initialIsHidden
        }
    }

    fun doSave() {
        val prevName = initialDisplayName
        val prevBio = initialBio
        val prevPrivate = initialIsPrivate
        val prevHidden = initialIsHidden
        
        viewModel.updateProfile(displayName, bio, isPrivate, isHidden) {
            val toasts = mutableListOf<String>()
            if (displayName != prevName) toasts.add("Display name updated")
            if (bio != prevBio) toasts.add("Bio updated")
            if (isPrivate != prevPrivate) toasts.add(if (isPrivate) "Profile set to Private" else "Profile set to Public")
            if (isHidden != prevHidden) toasts.add(if (isHidden) "Hidden from Discover" else "Visible in Discover")
            if (toasts.isEmpty()) toasts.add("Profile saved")
            toastState.show(toasts.joinToString(" · "), WhisperToastType.SUCCESS)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                viewModel.uploadAvatar(bytes, mimeType)
            }
        }
    }

    val pickTrigger by viewModel.pickPhotoTrigger.collectAsStateWithLifecycle()
    LaunchedEffect(pickTrigger) {
        if (pickTrigger > 0) {
            imagePickerLauncher.launch("image/*")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .fadingEdges(top = 20.dp, bottom = 32.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Hero Avatar Section
        Box(contentAlignment = Alignment.BottomEnd) {
            WhisperAvatar(
                profile = profile,
                size = 104.dp,
                onClick = onShowAvatarOptions,
                onLongClick = { onViewAvatarFull(profile) },
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 4.dp,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .size(36.dp)
                    .bouncyClick(onClick = onShowAvatarOptions)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.CameraAlt,
                        stringResource(R.string.cd_ChangePhoto),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Header Title & Username
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                profile.effectiveName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.clickable {
                    haptic.click()
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString("@${profile.effectiveUsername}"))
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "@${profile.effectiveUsername}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy username", modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Status Badges
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        if (isPrivate) Icons.Rounded.Lock else Icons.Rounded.Public,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (isPrivate) "Private" else "Public",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (profile.publicKey != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.Key,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (profile.publicKey != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "E2EE (P-256)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (profile.publicKey != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // My encryption fingerprint card
        val myFingerprint = viewModel.myFingerprint
        if (myFingerprint != null) {
            ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "My Encryption Fingerprint",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Share this with friends so they can verify your identity. The fingerprint is generated from the key on this device and never leaves it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                myFingerprint,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            ToolzExpressiveIconButton(onClick = {
                                haptic.click()
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(myFingerprint))
                            }) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy fingerprint", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Profile Form Fields
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(R.string.st_Whisper_Profile_DisplayName)) },
                leadingIcon = { Icon(Icons.Rounded.Badge, null) },
                singleLine = true,
                shape = MediumExpressiveShape,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 160) bio = it },
                label = { Text(stringResource(R.string.st_Whisper_Profile_Bio)) },
                leadingIcon = { Icon(Icons.Rounded.Info, null) },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MediumExpressiveShape,
                supportingText = { Text("${bio.length}/160") },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader("Privacy & Discovery")

            // Profile Visibility: Public vs Private
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Profile Visibility", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                ToolzConnectedButtonGroup(
                    selectedIndex = if (isPrivate) 1 else 0,
                    options = listOf("Public", "Private"),
                    onOptionSelected = { isPrivate = it == 1 },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (isPrivate) stringResource(R.string.st_Whisper_Profile_PrivateDesc) else stringResource(R.string.st_Whisper_Profile_PublicDesc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Hide from Discover Toggle
            ExpressiveCard(
                onClick = {
                    if (!isHidden) {
                        showDiscoveryWarningDialog = true
                    } else {
                        isHidden = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.PersonSearch,
                        null,
                        tint = if (isHidden) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hide from Discover", fontWeight = FontWeight.Bold)
                        Text(
                            "You won't appear in recommendations or search. People can only find you if you add them first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ExpressiveSwitch(
                        checked = isHidden,
                        onCheckedChange = {
                            if (it) showDiscoveryWarningDialog = true
                            else isHidden = false
                        },
                    )
                }
            }
        }

        // Save Button
        val saveButtonAnim by animateFloatAsState(
            targetValue = if (hasUnsaved) 1f else 0.7f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        )
        ToolzExpressiveButton(
            onClick = {
                haptic.success()
                doSave()
            },
            modifier = Modifier.fillMaxWidth().height(54.dp).graphicsLayer { alpha = saveButtonAnim },
            enabled = hasUnsaved,
        ) {
            Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (hasUnsaved) stringResource(R.string.st_Whisper_Profile_SaveChanges) else "No changes",
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Delete Account
        ToolzOutlinedExpressiveButton(
            onClick = { haptic.click(); showDeleteAccountDialog = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Rounded.DeleteForever, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Delete Account", fontWeight = FontWeight.Bold)
        }

        // Logout
        val logoutInteractionSource = remember { MutableInteractionSource() }
        val isLogoutPressed by logoutInteractionSource.collectIsPressedAsState()

        LaunchedEffect(isLogoutPressed) {
            if (isLogoutPressed) {
                delay(3000.milliseconds)
                haptic.success()
                viewModel.resetOnboarding()
            }
        }

        ToolzOutlinedExpressiveButton(
            onClick = { haptic.click(); showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            interactionSource = logoutInteractionSource,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.st_Whisper_Profile_LogOut), fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(20.dp))
    }

    // Material 3 Expressive Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
                }
            },
            title = { Text(stringResource(R.string.st_Whisper_Profile_LogOutTitle), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.st_Whisper_Profile_LogOutDesc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.signOut {
                            onLoggedOut()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.st_Whisper_Profile_LogOut), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(onClick = { showLogoutDialog = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.st_Whisper_Friends_Cancel), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // M3 Expressive Account Deletion Dialog
    if (showDeleteAccountDialog) {
        val isTokenUser = viewModel.isAnonymousTokenUser
        var passwordInput by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
                }
            },
            title = {
                Text(
                    "Delete Account",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (isTokenUser)
                            "This will permanently delete your account and all your messages. This cannot be undone."
                        else
                            "This will permanently delete your account and all your messages. Enter your password to confirm.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isTokenUser) {
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        viewModel.deleteAccount(
                            password = if (isTokenUser) null else passwordInput.ifBlank { null }
                        ) {
                            onLoggedOut()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = isTokenUser || passwordInput.isNotBlank(),
                ) {
                    Text("Delete Account", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(
                    onClick = { showDeleteAccountDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.st_Whisper_Friends_Cancel), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Whisper Discovery Warning Dialog
    if (showDiscoveryWarningDialog) {
        AlertDialog(
            onDismissRequest = { showDiscoveryWarningDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.VisibilityOff, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
                }
            },
            title = {
                Text("Hide from Discover?", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    "Activating this means no one will ever find you on Whisper unless you reach out first. You will be removed from all search results and recommendations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        haptic.success()
                        isHidden = true
                        showDiscoveryWarningDialog = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Hide Me", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(
                    onClick = { showDiscoveryWarningDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Keep Visible")
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// MATERIAL 3 EXPRESSIVE BOTTOM SHEETS FOR MAIN SCREEN
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvatarOptionsSheet(
    profile: WhisperProfile,
    onDismiss: () -> Unit,
    onChoosePhoto: () -> Unit,
    onDeletePhoto: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CameraAlt, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text("Profile Photo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Choose or update your profile picture", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(4.dp))

            ToolzTonalExpressiveButton(
                onClick = onChoosePhoto,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Choose from Gallery", fontWeight = FontWeight.SemiBold)
            }

            if (!profile.avatarUrl.isNullOrBlank()) {
                ToolzOutlinedExpressiveButton(
                    onClick = onDeletePhoto,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Remove Current Photo", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendOptionsSheet(
    friend: WhisperProfile,
    onDismiss: () -> Unit,
    onChat: () -> Unit,
    onViewProfile: () -> Unit,
    onUnfriend: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WhisperAvatar(friend, 52.dp)
                Column {
                    Text(friend.effectiveName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("@${friend.effectiveUsername}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            ToolzExpressiveButton(
                onClick = onChat,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.Chat, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.st_Whisper_Discover_Message), fontWeight = FontWeight.Bold)
            }

            ToolzTonalExpressiveButton(
                onClick = onViewProfile,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Rounded.Person, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("View Full Profile", fontWeight = FontWeight.SemiBold)
            }

            ToolzOutlinedExpressiveButton(
                onClick = onUnfriend,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Rounded.PersonRemove, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.st_Whisper_Profile_RemoveFriend), fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatOptionsSheet(
    convo: WhisperConversation,
    isBlocked: Boolean,
    onDismiss: () -> Unit,
    onViewProfile: () -> Unit,
    onClearChat: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleBlock: () -> Unit,
    onDeleteChat: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Chat Options",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            // View Profile
            ListItem(
                headlineContent = { Text("View Profile", fontWeight = FontWeight.Medium) },
                leadingContent = { Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onViewProfile() }
            )

            // Clear Chat
            ListItem(
                headlineContent = { Text("Clear chat history", fontWeight = FontWeight.Medium) },
                leadingContent = { Icon(Icons.Rounded.CleaningServices, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onClearChat() }
            )

            // Mute / Unmute
            ListItem(
                headlineContent = { Text(if (convo.isMuted) "Unmute notifications" else "Mute notifications", fontWeight = FontWeight.Medium) },
                leadingContent = {
                    Icon(
                        if (convo.isMuted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                        null,
                        tint = if (convo.isMuted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onToggleMute() }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Block / Unblock
            ListItem(
                headlineContent = {
                    Text(
                        if (isBlocked) "Unblock user" else "Block user",
                        fontWeight = FontWeight.SemiBold,
                        color = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                },
                leadingContent = {
                    Icon(
                        if (isBlocked) Icons.Rounded.LockOpen else Icons.Rounded.Block,
                        null,
                        tint = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onToggleBlock() }
            )

            // Delete chat (hides from the chats tab)
            ListItem(
                headlineContent = {
                    Text("Delete chat", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                },
                leadingContent = {
                    Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onDeleteChat() }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// M3 EXPRESSIVE FULL-SCREEN AVATAR DIALOG
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenAvatarDialog(
    profile: WhisperProfile,
    isSelf: Boolean = false,
    onDismiss: () -> Unit,
    onMessage: () -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val scale = remember { androidx.compose.animation.core.Animatable(0.85f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .clickable(enabled = false, onClick = {}) // consume clicks so outer dismiss works
                    .padding(horizontal = 32.dp)
            ) {
                // Avatar display
                var isImageError by remember(profile.avatarUrl) { mutableStateOf(false) }
                if (!profile.avatarUrl.isNullOrBlank() && !isImageError) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = profile.effectiveName,
                        contentScale = ContentScale.Fit,
                        onError = { isImageError = true },
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(28.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.secondaryContainer,
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            profile.avatarInitial,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Black,
                            fontSize = 96.sp,
                        )
                    }
                }

                // Name & username
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        profile.effectiveName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    Text(
                        "@${profile.effectiveUsername}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    if (!profile.bio.isNullOrBlank()) {
                        Text(
                            profile.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ToolzOutlinedExpressiveButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Close", fontWeight = FontWeight.SemiBold)
                    }
                    if (!isSelf) {
                        ToolzExpressiveButton(
                            onClick = { haptic.click(); onMessage() }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Chat, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Message", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WhisperAvatar(
    profile: WhisperProfile,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    var isImageError by remember(profile.avatarUrl) { mutableStateOf(false) }

    val baseModifier = modifier
        .size(size)
        .clip(CircleShape)
        .then(
            when {
                onClick != null && onLongClick != null -> Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                onClick != null -> Modifier.bouncyClick(onClick = onClick)
                onLongClick != null -> Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
                else -> Modifier
            }
        )

    if (!profile.avatarUrl.isNullOrBlank() && !isImageError) {
        AsyncImage(
            model = profile.avatarUrl,
            contentDescription = profile.effectiveName,
            contentScale = ContentScale.Crop,
            onError = { isImageError = true },
            modifier = baseModifier,
        )
    } else {
        Box(
            modifier = baseModifier.background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                    )
                )
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profile.avatarInitial,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

@Composable
fun WhisperEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ConversationSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = shimmerAlpha),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = shimmerAlpha * 0.5f))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(14.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = shimmerAlpha * 0.5f))
                    )
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(10.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = shimmerAlpha * 0.3f))
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(10.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = shimmerAlpha * 0.3f))
                )
            }
        }
    }
}

@Composable
private fun FriendSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp).padding(vertical = 4.dp).alpha(shimmerAlpha)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(8.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        )
    }
}

@Composable
private fun DiscoverSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = shimmerAlpha),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = shimmerAlpha * 0.5f))
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(14.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = shimmerAlpha * 0.5f))
                    )
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(10.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = shimmerAlpha * 0.3f))
                    )
                }
            }
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )
            }
        }
    }
}

@Composable
fun String.formatTimestamp(): String {
    val yesterday = stringResource(R.string.st_Whisper_Chat_Yesterday)
    return try {
        val dt = java.time.OffsetDateTime.parse(this)
        val now = java.time.OffsetDateTime.now()
        val days = java.time.temporal.ChronoUnit.DAYS.between(dt.toLocalDate(), now.toLocalDate())
        when {
            days == 0L -> "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
            days == 1L -> yesterday
            days < 7L  -> dt.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            else       -> "${dt.dayOfMonth}/${dt.monthValue}"
        }
    } catch (_: Exception) { "" }
}
