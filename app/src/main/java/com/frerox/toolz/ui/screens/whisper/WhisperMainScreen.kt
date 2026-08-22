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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val screenshotBypassEnabled by viewModel.screenshotBypassEnabled.collectAsStateWithLifecycle()
    val haptic = rememberToolzHapticFeedback()
    val toastState = rememberWhisperToastState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showBypassDialog by remember { mutableStateOf(false) }

    // Conversation previews contain decrypted content — never allow screenshots/recents capture.
    SecureWindow(bypassEnabled = screenshotBypassEnabled)

    if (showBypassDialog) {
        WhisperScreenshotBypassDialog(
            onDismiss = { showBypassDialog = false },
            onConfirm = { password ->
                scope.launch {
                    if (isWhisperBypassPassword(password)) {
                        viewModel.setScreenshotBypass(true)
                        toastState.show("Successfully bypassed screenshot block", WhisperToastType.SUCCESS)
                    } else {
                        toastState.show(context.getString(R.string.st_Whisper_Error_InvalidCredentials), WhisperToastType.ERROR)
                    }
                }
                showBypassDialog = false
            }
        )
    }

    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    var isLoggingOut by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated == false && !isLoggingOut) {
            isLoggingOut = true
            onLoggedOut()
        } else if (isAuthenticated == true) {
            isLoggingOut = false
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
    var convoPendingClear by remember { mutableStateOf<WhisperConversation?>(null) }
    var pendingUnfriend by remember { mutableStateOf<WhisperProfile?>(null) }
    var pendingRemovePhoto by remember { mutableStateOf(false) }
    var pendingHideChat by remember { mutableStateOf<WhisperConversation?>(null) }
    var showAvatarOptions by remember { mutableStateOf(false) }
    var profileForFullView by remember { mutableStateOf<WhisperProfile?>(null) }

    var hasUnsavedProfileChanges by remember { mutableStateOf(false) }
    var pendingTabSwitchIndex by remember { mutableStateOf<Int?>(null) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var triggerProfileSaveFromDialog by remember { mutableStateOf(0) }
    var triggerProfileDiscardFromDialog by remember { mutableStateOf(0) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            toastState.show(it.asString(context), WhisperToastType.ERROR)
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
                    modifier = Modifier.screenshotBypassGesture {
                        if (screenshotBypassEnabled) {
                            viewModel.setScreenshotBypass(false)
                            toastState.show("Successfully enabled screenshot block", WhisperToastType.SUCCESS)
                        } else {
                            showBypassDialog = true
                        }
                    },
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
                        // Computed once and shared by the top bar and nav badge.
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
                        val totalUnread = uiState.conversations.sumOf { it.unreadCount }
                        val unread = if (index == 0) totalUnread else 0
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
                            onProfileSaved = {
                                hasUnsavedProfileChanges = false
                                pendingTabSwitchIndex?.let { target ->
                                    scope.launch { pagerState.animateScrollToPage(target) }
                                }
                                pendingTabSwitchIndex = null
                            },
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
                    showAvatarOptions = false
                    // Deleting the photo is destructive, so it always asks first.
                    pendingRemovePhoto = true
                }
            )
        }
    }

    // Clear-history confirmation (destructive and permanent)
    convoPendingClear?.let { convo ->
        val clearedMsg = stringResource(R.string.st_Whisper_ChatHistoryCleared)
        AlertDialog(
            onDismissRequest = { convoPendingClear = null },
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
                    Icon(Icons.Rounded.CleaningServices, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
                }
            },
            title = { Text(stringResource(R.string.st_Whisper_ClearHistoryConfirmTitle), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.st_Whisper_ClearHistoryConfirmDesc, convo.otherUser.effectiveName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        val uId = convo.otherUser.id
                        convoPendingClear = null
                        haptic.success()
                        viewModel.clearChatHistory(uId)
                        toastState.show(clearedMsg, WhisperToastType.SUCCESS)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                ) { Text(stringResource(R.string.st_Whisper_Delete), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(onClick = { convoPendingClear = null }) {
                    Text(stringResource(R.string.st_Whisper_Cancel))
                }
            },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        )
    }

    // Unfriend confirmation (removes the friend permanently)
    pendingUnfriend?.let { friend ->
        val unfriendedMsg = stringResource(R.string.st_Whisper_RemovedFriend)
        AlertDialog(
            onDismissRequest = { pendingUnfriend = null },
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
                    Icon(Icons.Rounded.PersonRemove, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
                }
            },
            title = { Text(stringResource(R.string.st_Whisper_UnfriendTitle), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.st_Whisper_UnfriendDesc, friend.effectiveName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        val fId = friend.id
                        pendingUnfriend = null
                        haptic.success()
                        viewModel.unfriend(fId)
                        toastState.show(unfriendedMsg, WhisperToastType.INFO)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                ) { Text(stringResource(R.string.st_Whisper_UnfriendConfirm), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(onClick = { pendingUnfriend = null }) {
                    Text(stringResource(R.string.st_Whisper_Cancel))
                }
            },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        )
    }

    // Remove-profile-photo confirmation (deletes the avatar permanently)
    if (pendingRemovePhoto) {
        AlertDialog(
            onDismissRequest = { pendingRemovePhoto = false },
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
                    Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
                }
            },
            title = { Text(stringResource(R.string.st_Whisper_RemovePhotoTitle), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.st_Whisper_RemovePhotoDesc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        pendingRemovePhoto = false
                        haptic.success()
                        viewModel.deleteAvatar()
                        // No optimistic toast: failures surface through the shared error handler.
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                ) { Text(stringResource(R.string.st_Whisper_Delete), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(onClick = { pendingRemovePhoto = false }) {
                    Text(stringResource(R.string.st_Whisper_Cancel))
                }
            },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        )
    }

    // Hide-chat confirmation (hides the conversation locally, non-destructive)
    pendingHideChat?.let { convo ->
        val hiddenMsg = stringResource(R.string.st_Whisper_ChatHidden)
        AlertDialog(
            onDismissRequest = { pendingHideChat = null },
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
                    Icon(Icons.Rounded.VisibilityOff, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                }
            },
            title = { Text(stringResource(R.string.st_Whisper_HideChatTitle), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.st_Whisper_HideChatDesc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        val uId = convo.otherUser.id
                        pendingHideChat = null
                        haptic.success()
                        viewModel.hideChat(uId)
                        toastState.show(hiddenMsg, WhisperToastType.INFO)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.st_Whisper_HideChat), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(onClick = { pendingHideChat = null }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.st_Whisper_Cancel))
                }
            },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        )
    }

    // Material 3 Expressive Friend Options Bottom Sheet
    selectedFriendForOptions?.let { friend ->
        val unfriendedMsg = stringResource(R.string.st_Whisper_RemovedFriend)
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
                val friend = friend
                selectedFriendForOptions = null
                haptic.click()
                // Unfriending is destructive, so it always asks for confirmation first.
                pendingUnfriend = friend
            }
        )
    }

    // Material 3 Expressive Chat Options Bottom Sheet
    selectedConvoForOptions?.let { convo ->
        val unmutedMsg = stringResource(R.string.st_Whisper_UnmutedNotifications)
        val mutedMsg = stringResource(R.string.st_Whisper_MutedNotifications)
        val unblockedMsg = stringResource(R.string.st_Whisper_UserUnblocked)
        val blockedMsg = stringResource(R.string.st_Whisper_UserBlocked)
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
                val convo = convo
                selectedConvoForOptions = null
                haptic.click()
                // Clearing is permanent and cannot be undone from this screen, so it
                // always asks for confirmation first.
                convoPendingClear = convo
            },
            onToggleMute = {
                val uId = convo.otherUser.id
                selectedConvoForOptions = null
                viewModel.toggleMuteUser(uId)
                toastState.show(if (convo.isMuted) unmutedMsg else mutedMsg, WhisperToastType.INFO)
            },
            onToggleBlock = {
                val uId = convo.otherUser.id
                selectedConvoForOptions = null
                haptic.click()
                // Toast from the toggle RESULT, not the possibly-stale local snapshot:
                // the callback carries the new state, so the message can never invert.
                viewModel.toggleBlockUser(uId) { isBlockedNow ->
                    toastState.show(if (isBlockedNow) blockedMsg else unblockedMsg, WhisperToastType.INFO)
                }
            },
            onDeleteChat = {
                val convo = convo
                selectedConvoForOptions = null
                haptic.click()
                // Hiding is not destructive, but confirm anyway so the action is explicit.
                pendingHideChat = convo
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
                Text(stringResource(R.string.st_Whisper_UnsavedTitle), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    stringResource(R.string.st_Whisper_UnsavedDesc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        showUnsavedChangesDialog = false
                        triggerProfileSaveFromDialog++
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.st_Whisper_SaveAndSwitch), fontWeight = FontWeight.Bold)
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
                        Text(stringResource(R.string.st_Whisper_Discard))
                    }
                    ToolzOutlinedExpressiveButton(
                        onClick = {
                            showUnsavedChangesDialog = false
                            pendingTabSwitchIndex = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.st_Whisper_Cancel))
                    }
                }
            }
        )
    }

    // Whisper Beta Warning Dialog
    if (betaWarningShown == false) {
        AlertDialog(
            onDismissRequest = { viewModel.markBetaWarningAsShown() },
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
            item { SectionHeader(stringResource(R.string.st_Whisper_FriendsHeader)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(5) { FriendSkeleton() }
                }
            }
            item { SectionHeader(stringResource(R.string.st_Whisper_MessagesHeader)) }
            items(5) { ConversationSkeleton() }
        }
        return
    }

    if (uiState.conversations.isEmpty() && uiState.friends.isEmpty() && uiState.pendingIncoming.isEmpty()) {
        WhisperEmptyState(
            icon = Icons.AutoMirrored.Rounded.Chat,
            title = stringResource(R.string.st_Whisper_Chats_EmptyTitle),
            subtitle = stringResource(R.string.st_Whisper_Chats_EmptySubtitle),
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
        // Incoming Friend Requests Banner. pendingIncomingRequests is the single source
        // of truth (it mirrors pendingIncoming, which is kept for the nav badge only).
        if (uiState.pendingIncomingRequests.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.st_Whisper_FriendRequestsCount, uiState.pendingIncomingRequests.size))
            }
            items(uiState.pendingIncomingRequests, key = { "req_${it.friendship.id}" }) { reqItem ->
                FriendRequestCard(
                    reqItem = reqItem,
                    onAccept = { haptic.success(); viewModel.acceptFriendRequest(reqItem.friendship.id) },
                    onDecline = { haptic.click(); viewModel.declineFriendRequest(reqItem.friendship.id) },
                )
            }
        }

        // Friends Quick Bar
        if (uiState.friends.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.st_Whisper_FriendsCount, uiState.friends.size))
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
                SectionHeader(stringResource(R.string.st_Whisper_MessagesCount, uiState.conversations.size))
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
    val isOnline = conversation.otherUser.onlineStatus == "Online"

    // Animated online dot pulse — only runs for contacts that are actually online,
    // so offline conversations never burn battery with an infinite animation.
    val performanceMode = LocalPerformanceMode.current
    val infiniteTransition = rememberInfiniteTransition(label = "onlinePulse")
    val onlineDotScale by if (isOnline && !performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 1f, targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    // Fix: pass onLongClick to ExpressiveCard directly (no duplicate combinedClickable)
    ExpressiveCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (unread)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else
            MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Avatar with animated online dot
            Box {
                WhisperAvatar(conversation.otherUser, 52.dp)
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .align(Alignment.BottomEnd)
                            .graphicsLayer {
                                scaleX = onlineDotScale
                                scaleY = onlineDotScale
                            }
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                            .border(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            conversation.otherUser.effectiveName,
                            fontWeight = if (unread) FontWeight.Black else FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (conversation.isMuted) {
                            Icon(
                                Icons.Rounded.NotificationsOff,
                                contentDescription = "Muted",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(13.dp)
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
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Online status as small colored label
                        if (isOnline) {
                            Text(
                                "Online",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                        Text(
                            conversation.lastMessage.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (conversation.unreadCount > 0) {
                        Spacer(Modifier.width(8.dp))
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
                    text = if (profile != null) "@${profile.effectiveUsername}" else stringResource(R.string.st_Whisper_UserDefault),
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
    val discoverLoadFailed by viewModel.discoverLoadFailed.collectAsStateWithLifecycle()

    // Debounced search: a network call per keystroke is wasteful, so wait 400ms of
    // stillness before asking the VM (the VM also cancels the previous in-flight job).
    LaunchedEffect(searchQuery) {
        delay(400)
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
            val lazyListState = rememberLazyListState()
            if (uiState.isLoading && uiState.recommendedProfiles.isEmpty()) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { SectionHeader("Suggested For You") }
                    items(3) { DiscoverSkeleton() }
                }
            } else {
                // Infinite scroll trigger
                val shouldLoadMore = remember(uiState.hasReachedEndOfDiscover, uiState.isDiscoverLoadingNext) {
                    derivedStateOf {
                        val layoutInfo = lazyListState.layoutInfo
                        val totalItems = layoutInfo.totalItemsCount
                        val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        totalItems > 0 && lastVisibleItem >= totalItems - 5 && !uiState.hasReachedEndOfDiscover && !uiState.isDiscoverLoadingNext
                    }
                }
                
                LaunchedEffect(shouldLoadMore.value) {
                    if (shouldLoadMore.value) {
                        viewModel.loadNextDiscoverPage()
                    }
                }

                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fadingEdges(top = 8.dp, bottom = 24.dp),
                ) {
                    // 1. Suggested For You Section
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
                    }

                    // 2. Whisper Someone Section
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Explore, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            SectionHeader("Whisper Someone")
                        }
                    }

                    items(uiState.discoverProfiles, key = { "disc_${it.id}" }) { profile ->
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

                    if (uiState.isDiscoverLoadingNext) {
                        items(2) { DiscoverSkeleton() }
                    }

                    // A failed page load leaves the infinite scroll stuck (the load
                    // effect only re-fires when the scroll/loading state changes), so
                    // offer an explicit retry.
                    if (discoverLoadFailed) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                ToolzOutlinedExpressiveButton(
                                    onClick = {
                                        haptic.click()
                                        viewModel.loadNextDiscoverPage()
                                    },
                                ) {
                                    Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.st_Whisper_Retry), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    
                    if (uiState.recommendedProfiles.isEmpty() && uiState.discoverProfiles.isEmpty() && !uiState.isLoading) {
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
                        Icon(Icons.Rounded.Lock, stringResource(R.string.st_Whisper_Private), tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
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
    onProfileSaved: () -> Unit,
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

    // Reset the form only when a different account loads; refreshed profile data after a
    // save should not wipe edits the user is still making. rememberSaveable keeps the
    // in-progress edits across rotation/process recreation too.
    var displayName by rememberSaveable(profile.id) { mutableStateOf(initialDisplayName) }
    var bio by rememberSaveable(profile.id) { mutableStateOf(initialBio) }
    var isPrivate by rememberSaveable(profile.id) { mutableStateOf(initialIsPrivate) }
    var isHidden by rememberSaveable(profile.id) { mutableStateOf(initialIsHidden) }
    
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showDiscoveryWarningDialog by remember { mutableStateOf(false) }
    var showAubupInfoDialog by remember { mutableStateOf(false) }
    var showCreateAccessFileDialog by remember { mutableStateOf(false) }
    var showRotateKeyDialog by remember { mutableStateOf(false) }
    var isRotatingKey by remember { mutableStateOf(false) }

    // Resolved in composition scope: stringResource is composable-only and cannot be
    // called from the non-composable doSave() callback.
    val nameUpdatedMsg = stringResource(R.string.st_Whisper_DisplayNameUpdated)
    val bioUpdatedMsg = stringResource(R.string.st_Whisper_BioUpdated)
    val setPrivateMsg = stringResource(R.string.st_Whisper_ProfileSetPrivate)
    val setPublicMsg = stringResource(R.string.st_Whisper_ProfileSetPublic)
    val hiddenMsg = stringResource(R.string.st_Whisper_HiddenFromDiscover)
    val visibleMsg = stringResource(R.string.st_Whisper_VisibleInDiscover)
    val profileSavedMsg = stringResource(R.string.st_Whisper_ProfileSaved)

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
                toastState.show(profileSavedMsg, WhisperToastType.SUCCESS)
                onProfileSaved()
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
            if (displayName != prevName) toasts.add(nameUpdatedMsg)
            if (bio != prevBio) toasts.add(bioUpdatedMsg)
            if (isPrivate != prevPrivate) toasts.add(if (isPrivate) setPrivateMsg else setPublicMsg)
            if (isHidden != prevHidden) toasts.add(if (isHidden) hiddenMsg else visibleMsg)
            if (toasts.isEmpty()) toasts.add(profileSavedMsg)
            toastState.show(toasts.joinToString(" · "), WhisperToastType.SUCCESS)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // Reads and bounded-size checks never run on the main thread; any failure
            // (including readBounded's require for oversized files) is caught here and
            // surfaced as a toast instead of crashing the picker.
            scope.launch(Dispatchers.IO) {
                val result = runCatching {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: error("Could not open picked image")
                    stream.use { s ->
                        // Bound the read so a huge picker image can never exhaust memory.
                        val bytes = s.readBounded(MAX_AVATAR_READ_BYTES)
                        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        bytes to mimeType
                    }
                }
                result
                    .onSuccess { (bytes, mimeType) -> viewModel.uploadAvatar(bytes, mimeType) }
                    .onFailure { err ->
                        if (err is kotlinx.coroutines.CancellationException) return@onFailure
                        toastState.show(context.getString(R.string.st_Whisper_Error_ReadImage), WhisperToastType.ERROR)
                    }
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
                        if (isPrivate) stringResource(R.string.st_Whisper_Private) else stringResource(R.string.st_Whisper_Public),
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
                            stringResource(R.string.st_Whisper_Fingerprint_Title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        stringResource(R.string.st_Whisper_Fingerprint_Desc),
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
                    ToolzOutlinedExpressiveButton(
                        onClick = {
                            haptic.click()
                            showRotateKeyDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.SyncLock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Rotate Encryption Key", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // AUBUP: Auth User Backup Program Card
        ExpressiveCard(
            onClick = { showCreateAccessFileDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.EnhancedEncryption,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        stringResource(R.string.st_Whisper_Aubup_SaveAccessFile),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    ToolzExpressiveIconButton(onClick = {
                        haptic.click()
                        showAubupInfoDialog = true
                    }) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = "Access File Info",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    stringResource(R.string.st_Whisper_Aubup_SaveAccessFileDesc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToolzOutlinedExpressiveButton(
                    onClick = {
                        haptic.click()
                        showCreateAccessFileDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.st_Whisper_Aubup_CreateBackup), fontWeight = FontWeight.SemiBold)
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
                Text(stringResource(R.string.st_Whisper_ProfileVisibility), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                ToolzConnectedButtonGroup(
                    selectedIndex = if (isPrivate) 1 else 0,
                    options = listOf(stringResource(R.string.st_Whisper_Public), stringResource(R.string.st_Whisper_Private)),
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
                        Text(stringResource(R.string.st_Whisper_HideFromDiscover), fontWeight = FontWeight.Bold)
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
                if (hasUnsaved) stringResource(R.string.st_Whisper_Profile_SaveChanges) else stringResource(R.string.st_Whisper_Profile_NoChanges),
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
            Text(stringResource(R.string.st_Whisper_DeleteAccount), fontWeight = FontWeight.Bold)
        }

        // Logout — plain tap opens the confirmation dialog; the old hidden "hold 3s"
        // gesture is gone because it silently reset onboarding without signing out.
        ToolzOutlinedExpressiveButton(
            onClick = { haptic.click(); showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.st_Whisper_Profile_LogOut), fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(20.dp))
    }

    // AUBUP Info Dialog
    if (showAubupInfoDialog) {
        AlertDialog(
            onDismissRequest = { showAubupInfoDialog = false },
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    Icons.Rounded.EnhancedEncryption,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    stringResource(R.string.st_Whisper_Aubup_InfoTitle),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    stringResource(R.string.st_Whisper_Aubup_InfoDesc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                ToolzExpressiveButton(onClick = { showAubupInfoDialog = false }) {
                    Text(stringResource(R.string.st_Whisper_Aubup_GotIt), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // AUBUP Create Access File Dialog
    if (showCreateAccessFileDialog) {
        var whisperCode by remember { mutableStateOf("") }
        var confirmWhisperCode by remember { mutableStateOf("") }
        var codeError by remember { mutableStateOf<String?>(null) }
        var isCreating by remember { mutableStateOf(false) }

        val codeMismatchMsg = stringResource(R.string.st_Whisper_Aubup_CodeMismatch)
        val codeLengthMsg = stringResource(R.string.st_Whisper_Aubup_CodeLengthError)

        AlertDialog(
            onDismissRequest = { if (!isCreating) showCreateAccessFileDialog = false },
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    stringResource(R.string.st_Whisper_Aubup_SaveAccessFile),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.st_Whisper_Aubup_EnterWhisperCode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = whisperCode,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                whisperCode = it
                                codeError = null
                            }
                        },
                        label = { Text(stringResource(R.string.st_Whisper_Aubup_WhisperCodeLabel)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = MediumExpressiveShape,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmWhisperCode,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                confirmWhisperCode = it
                                codeError = null
                            }
                        },
                        label = { Text(stringResource(R.string.st_Whisper_Aubup_ConfirmCodeLabel)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = MediumExpressiveShape,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (codeError != null) {
                        Text(
                            text = codeError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        if (whisperCode.length != 6) {
                            codeError = codeLengthMsg
                            return@ToolzExpressiveButton
                        }
                        if (whisperCode != confirmWhisperCode) {
                            codeError = codeMismatchMsg
                            return@ToolzExpressiveButton
                        }
                        isCreating = true
                        viewModel.createAccessFile(
                            whisperCode = whisperCode,
                            onSuccess = { file ->
                                isCreating = false
                                showCreateAccessFileDialog = false
                                haptic.success()
                                toastState.show(
                                    context.getString(R.string.st_Whisper_Aubup_FileCreatedSuccess, profile.effectiveUsername),
                                    WhisperToastType.SUCCESS
                                )
                            },
                            onError = { err ->
                                isCreating = false
                                codeError = err
                                toastState.show(err, WhisperToastType.ERROR)
                            }
                        )
                    },
                    enabled = whisperCode.length == 6 && confirmWhisperCode.length == 6 && !isCreating
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.st_Whisper_Aubup_CreateBackup), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(
                    onClick = { showCreateAccessFileDialog = false },
                    enabled = !isCreating
                ) {
                    Text(stringResource(R.string.st_Whisper_Friends_Cancel), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Rotate Key Dialog
    if (showRotateKeyDialog) {
        AlertDialog(
            onDismissRequest = { if (!isRotatingKey) showRotateKeyDialog = false },
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            },
            title = { Text("Rotate Encryption Key", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Rotating your P-256 key generates a fresh key pair. Friends will be prompted to re-verify your new key fingerprint. Past message history on this device is preserved, and all future messages will use the new key.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        isRotatingKey = true
                        viewModel.rotateEncryptionKey { success ->
                            isRotatingKey = false
                            showRotateKeyDialog = false
                            if (success) {
                                haptic.success()
                                toastState.show("Encryption key rotated successfully", WhisperToastType.SUCCESS)
                            } else {
                                toastState.show("Failed to rotate encryption key", WhisperToastType.ERROR)
                            }
                        }
                    },
                    enabled = !isRotatingKey
                ) {
                    if (isRotatingKey) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Rotate Key", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(
                    onClick = { showRotateKeyDialog = false },
                    enabled = !isRotatingKey
                ) {
                    Text(stringResource(R.string.st_Whisper_Friends_Cancel), fontWeight = FontWeight.SemiBold)
                }
            }
        )
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
                            label = { Text(stringResource(R.string.st_Whisper_Bypass_PasswordLabel)) },
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
                    Text(stringResource(R.string.st_Whisper_DeleteAccount), fontWeight = FontWeight.Bold)
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
                Text(stringResource(R.string.st_Whisper_HideDiscoverConfirmTitle), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
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
                    Text(stringResource(R.string.st_Whisper_HideMe), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(
                    onClick = { showDiscoveryWarningDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.st_Whisper_KeepVisible))
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
                    Text(stringResource(R.string.st_Whisper_ProfilePhoto), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.st_Whisper_ProfilePhotoDesc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(4.dp))

            ToolzTonalExpressiveButton(
                onClick = onChoosePhoto,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.st_Whisper_ChooseFromGallery), fontWeight = FontWeight.SemiBold)
            }

            if (!profile.avatarUrl.isNullOrBlank()) {
                ToolzOutlinedExpressiveButton(
                    onClick = onDeletePhoto,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.st_Whisper_RemovePhoto), fontWeight = FontWeight.SemiBold)
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
                Text(stringResource(R.string.st_Whisper_ViewFullProfile), fontWeight = FontWeight.SemiBold)
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
                leadingContent = { Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onViewProfile() }
            ) {
                Text(stringResource(R.string.st_Whisper_ViewProfile), fontWeight = FontWeight.Medium)
            }

            // Clear Chat
            ListItem(
                leadingContent = { Icon(Icons.Rounded.CleaningServices, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onClearChat() }
            ) {
                Text(stringResource(R.string.st_Whisper_ClearHistory), fontWeight = FontWeight.Medium)
            }

            // Mute / Unmute
            ListItem(
                leadingContent = {
                    Icon(
                        if (convo.isMuted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                        null,
                        tint = if (convo.isMuted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onToggleMute() }
            ) {
                Text(if (convo.isMuted) "Unmute notifications" else "Mute notifications", fontWeight = FontWeight.Medium)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Block / Unblock
            ListItem(
                leadingContent = {
                    Icon(
                        if (isBlocked) Icons.Rounded.LockOpen else Icons.Rounded.Block,
                        null,
                        tint = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onToggleBlock() }
            ) {
                Text(
                    if (isBlocked) "Unblock user" else "Block user",
                    fontWeight = FontWeight.SemiBold,
                    color = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            // Hide chat (hides from the chats tab, non-destructive)
            ListItem(
                leadingContent = {
                    Icon(Icons.Rounded.VisibilityOff, null, tint = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onDeleteChat() }
            ) {
                Text(stringResource(R.string.st_Whisper_HideChat), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            }

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
                        Text(stringResource(R.string.st_Whisper_Close), fontWeight = FontWeight.SemiBold)
                    }
                    if (!isSelf) {
                        ToolzExpressiveButton(
                            onClick = { haptic.click(); onMessage() }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Chat, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.st_Whisper_MessageAction), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

