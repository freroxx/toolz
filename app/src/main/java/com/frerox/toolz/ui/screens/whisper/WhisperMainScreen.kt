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

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.BugReport
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.frerox.toolz.BuildConfig
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
    // V2-FIX (verify in-flight guard): track password verification locally so the dialog
    // stays open, locks its buttons and shows progress until the verdict arrives.
    var isVerifyingBypass by remember { mutableStateOf(false) }

    // Whisper notification permission banner (slides down from top app bar on Chats tab)
    var hasNotificationPermission by remember { mutableStateOf(hasWhisperNotificationPermission(context)) }
    var bannerDismissed by rememberSaveable { mutableStateOf(false) }
    fun refreshNotificationPermission() {
        hasNotificationPermission = hasWhisperNotificationPermission(context)
        if (hasNotificationPermission) bannerDismissed = false
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshNotificationPermission()
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshNotificationPermission()
            }
        }
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    // Conversation previews contain decrypted content — never allow screenshots/recents capture.
    SecureWindow(bypassEnabled = screenshotBypassEnabled)

    if (showBypassDialog) {
        // M-17 FIX (reviewwhisper.md): the password is now required BOTH to enable and to
        // disable the bypass (previously disabling needed no verification at all), and
        // the toast wording is unified + localized.
        WhisperScreenshotBypassDialog(
            isVerifying = isVerifyingBypass,
            onDismiss = { if (!isVerifyingBypass) showBypassDialog = false },
            onConfirm = { password ->
                if (isVerifyingBypass) return@WhisperScreenshotBypassDialog
                scope.launch {
                    isVerifyingBypass = true
                    try {
                        // FIX: surface WHY verification failed instead of blaming the password
                        // for lockouts/service errors.
                        when (verifyWhisperBypass(password)) {
                            WhisperBypassVerdict.Granted -> {
                                val enabling = !screenshotBypassEnabled
                                viewModel.setScreenshotBypass(enabling)
                                toastState.show(
                                    context.getString(
                                        if (enabling) R.string.st_Whisper_Bypass_ProtectionOff
                                        else R.string.st_Whisper_Bypass_ProtectionOn
                                    ),
                                    WhisperToastType.SUCCESS
                                )
                            }
                            WhisperBypassVerdict.Denied ->
                                toastState.show(context.getString(R.string.st_Whisper_Error_InvalidCredentials), WhisperToastType.ERROR)
                            WhisperBypassVerdict.RateLimited ->
                                toastState.show(context.getString(R.string.st_Whisper_Bypass_RateLimited), WhisperToastType.ERROR)
                            WhisperBypassVerdict.Unavailable ->
                                toastState.show(context.getString(R.string.st_Whisper_Bypass_Unavailable), WhisperToastType.ERROR)
                        }
                    } finally {
                        isVerifyingBypass = false
                        showBypassDialog = false
                    }
                }
            }
        )
    }

    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    var isLoggingOut by rememberSaveable { mutableStateOf(false) }

    // V2-FIX M-M1: aggregate unread once per conversations change instead of re-summing
    // inside both the top bar badge and every bottom-nav item on each recomposition.
    val totalUnreadCount by remember { derivedStateOf { uiState.conversations.sumOf { it.unreadCount } } }

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

    // V2-FIX M-H3: system back while the profile tab holds unsaved edits must hit the
    // same intercept path as a tab tap — open the existing unsaved-changes dialog with
    // no target tab, so Save/Discard resolve exactly like the intercept route.
    BackHandler(enabled = hasUnsavedProfileChanges && pagerState.currentPage == 2) {
        pendingTabSwitchIndex = null
        showUnsavedChangesDialog = true
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            toastState.show(it.asString(context), WhisperToastType.ERROR)
            viewModel.clearError()
        }
    }

    // V2-FIX (reviewwhisper.md): success/info events (e.g. auto key rotation) surface
    // through the dedicated info channel instead of the error one.
    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let {
            toastState.show(it.asString(context), WhisperToastType.SUCCESS)
            viewModel.clearInfo()
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
                        // M-17: toggling in EITHER direction requires the server-verified
                        // password; the dialog applies the toggle on success.
                        showBypassDialog = true
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
                        // V2-FIX M-M1: reads the hoisted aggregation (see above).
                        if (totalUnreadCount > 0 && pagerState.currentPage != 0) {
                            Badge { Text(totalUnreadCount.coerceAtMost(99).toString()) }
                            Spacer(Modifier.width(16.dp))
                        }
                    }
                )
            },
            bottomBar = {
                ExpressiveNavigationBar {
                    tabs.forEachIndexed { index, (label, icon, selectedIcon) ->
                        val unread = if (index == 0) totalUnreadCount else 0
                        val pendingCount = if (index == 0) uiState.pendingIncomingRequests.size else 0
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
                            onViewAvatarFull = { profileForFullView = it },
                            showPermissionBanner = !hasNotificationPermission && !bannerDismissed,
                            onGrantPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onDismissBanner = { bannerDismissed = true }
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
        // V2-FIX M-H4: the sheet used to render before isBlocked resolved, flashing
        // "Block" for a user who was actually blocked (and vice versa). Fetch starts at
        // selection; null = unresolved and keeps the block row disabled until it lands.
        var blockState by remember(convo.otherUser.id) { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(convo.otherUser.id) {
            blockState = runCatching { viewModel.isBlockedByMe(convo.otherUser.id) }.getOrNull()
        }
        ChatOptionsSheet(
            convo = convo,
            isBlocked = blockState == true,
            blockStateResolved = blockState != null,
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
                // Toast from the toggle RESULT, not the possibly-stale conversation
                // snapshot — the snapshot may not match the persisted mute state.
                viewModel.toggleMuteUser(uId) { nowMuted ->
                    toastState.show(if (nowMuted) mutedMsg else unmutedMsg, WhisperToastType.INFO)
                }
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
    showPermissionBanner: Boolean = false,
    onGrantPermission: () -> Unit = {},
    onDismissBanner: () -> Unit = {},
) {
    val haptic = rememberToolzHapticFeedback()

    // V2-FIX M-M4: an initial-load failure used to render as the generic empty state.
    // Track whether an error was observed while every list was empty and offer a retry.
    var sawErrorWhileEmpty by remember { mutableStateOf(false) }
    val listsEmpty = uiState.conversations.isEmpty() && uiState.friends.isEmpty() &&
        uiState.pendingIncomingRequests.isEmpty()
    LaunchedEffect(uiState.error, listsEmpty) {
        if (uiState.error != null && listsEmpty) sawErrorWhileEmpty = true
    }
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) sawErrorWhileEmpty = false
    }

    // V2-FIX L16: a server paging race can hand back two rows for the same partner;
    // the list below keys on otherUser.id, so de-duplicate first or LazyColumn throws
    // "duplicate key". (WhisperConversation exposes no conversationId to fall back to.)
    val conversations = remember(uiState.conversations) { uiState.conversations.distinctBy { it.otherUser.id } }

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

    if (listsEmpty) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showPermissionBanner) {
                WhisperPermissionBanner(
                    onGrant = onGrantPermission,
                    onDismiss = onDismissBanner,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // V2-FIX M-M4: modest retry banner above the empty state after a failure.
            if (sawErrorWhileEmpty) {
                InitialLoadRetryBanner(onRetry = { haptic.click(); viewModel.loadAll() })
            }
            WhisperEmptyState(
                icon = Icons.AutoMirrored.Rounded.Chat,
                title = stringResource(R.string.st_Whisper_Chats_EmptyTitle),
                subtitle = stringResource(R.string.st_Whisper_Chats_EmptySubtitle),
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges(top = 16.dp, bottom = 32.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Whisper notification permission banner — slides down from top app bar, below it, friends below
        if (showPermissionBanner) {
            item {
                WhisperPermissionBanner(
                    onGrant = onGrantPermission,
                    onDismiss = onDismissBanner,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        // Incoming Friend Requests Banner — pendingIncomingRequests is the single source
        // of truth (L-2: duplicate mirror list removed).
        if (uiState.pendingIncomingRequests.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.st_Whisper_FriendRequestsCount, uiState.pendingIncomingRequests.size))
            }
            items(uiState.pendingIncomingRequests, key = { "req_${it.friendship.id}" }) { reqItem ->
                FriendRequestCard(
                    reqItem = reqItem,
                    onAccept = { haptic.success(); viewModel.acceptFriendRequest(reqItem.friendship.id) },
                    onDecline = { haptic.click(); viewModel.declineFriendRequest(reqItem.friendship.id) },
                    // V2-FIX L14: the card now opens the requester's profile instead of being inert.
                    onOpenProfile = { onNavigateToProfile(reqItem.friendship.userA) },
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
                                // V3-FIX (item 8a): enum-derived presence replaces the
                                // `== "Online"` literal; the localized label is attached to
                                // the color-only dot as an accessibility bonus for TalkBack.
                                val friendPresence = friend.presence
                                // V3-FIX: whisperPresenceLabel is @Composable and cannot be
                                // invoked inside the non-composable semantics lambda — hoist it.
                                val friendPresenceLabel = whisperPresenceLabel(friendPresence)
                                if (friendPresence == WhisperPresence.ONLINE) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(WhisperOnlineGreen)
                                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                            .semantics {
                                                contentDescription = friendPresenceLabel
                                            }
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
        if (conversations.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.st_Whisper_MessagesCount, conversations.size))
            }
            itemsIndexed(conversations, key = { _, c -> c.otherUser.id }) { _, convo ->
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

// V2-FIX M-M4: compact error+retry banner for an empty first load that failed.
@Composable
private fun InitialLoadRetryBanner(onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.CloudOff, null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.st_Whisper_LoadFailed_Chats),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            ToolzTonalExpressiveButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.st_Whisper_Retry), style = MaterialTheme.typography.labelSmall)
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
    // V3-FIX (item 8a): typed presence instead of the "Online" literal compare.
    val presence = conversation.otherUser.presence
    val isOnline = presence == WhisperPresence.ONLINE

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
                            .background(WhisperOnlineGreen)
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
                        // V3-FIX (item 8a): RECENT now gets a localized label too, and the
                        // text derives from the presence enum instead of the raw flag.
                        if (isOnline || presence == WhisperPresence.RECENT) {
                            Text(
                                whisperPresenceLabel(presence),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                        // V6-R2 (review): lock sentinels from the repository render as
                        // localized placeholders instead of raw English sentinels.
                        // Fix: images showed raw `whisper:image:{"url":...` — map to localized "📷 Image".
                        // Also handles not-yet-decrypted envelopes (v2/v3) that would otherwise
                        // leak `{"v":2,...}` JSON into the subtitle.
                        val rawPreview = conversation.lastMessage.content.trim()
                        val previewText = when {
                            rawPreview.isBlank() -> ""
                            com.frerox.toolz.data.whisper.WhisperTombstone.isLockedMarker(rawPreview) ->
                                stringResource(R.string.st_Whisper_Locked_Short)
                            com.frerox.toolz.data.whisper.WhisperImageAttachment.fromMessageContent(rawPreview) != null ||
                                rawPreview.startsWith(com.frerox.toolz.data.whisper.WhisperImageAttachment.MESSAGE_PREFIX) ->
                                "📷 " + stringResource(R.string.st_Whisper_Image)
                            // Not-yet-decrypted envelope/ratchet frames must never leak raw JSON.
                            // This also fixes "only images" bug: text envelopes were showing
                            // `{"v":2,...}` and being mistaken for missing preview.
                            rawPreview.startsWith("{\"v\":") ||
                                rawPreview.startsWith(WhisperEnvelope.PREFIX_V2) ||
                                rawPreview.startsWith(com.frerox.toolz.data.whisper.session.WhisperV3Codec.PREFIX) ->
                                stringResource(R.string.st_Whisper_Locked_Short)
                            else -> rawPreview
                        }
                        Text(
                            previewText,
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
    // V2-FIX L14: card-level action (friendship.userA is always the requester).
    onOpenProfile: () -> Unit,
) {
    val profile = reqItem.senderProfile
    val friendship = reqItem.friendship

    ExpressiveCard(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) {
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
                    Icon(Icons.Rounded.Close, stringResource(R.string.cd_Whisper_DeclineRequest), Modifier.size(16.dp))
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

    // V2-FIX M-M2: O(1) membership checks for the per-row friend/pending badges instead
    // of scanning both lists once per visible card on every recomposition.
    val friendIds = remember(uiState.friends) { HashSet(uiState.friends.map { it.id }) }
    val pendingOutgoingIds = remember(uiState.pendingOutgoing) { HashSet(uiState.pendingOutgoing.map { it.userB }) }

    // M-15 FIX (reviewwhisper.md): the UI-side 400ms debounce on top of the VM's own
    // 300ms debounce added ~700ms perceived lag; the VM-side debounce alone governs.
    LaunchedEffect(searchQuery) {
        viewModel.searchProfiles(searchQuery)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            ExpressiveSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.st_Whisper_Discover_SearchPlaceholder)) },
                // V2-FIX M-M3: WhisperViewModel exposes no isSearching flag (out of scope),
                // so distinguish query-active work from global loading locally — a small
                // inline progress ring only while a search query is actually in flight.
                trailingIcon = {
                    if (uiState.isLoading && searchQuery.isNotBlank()) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                },
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
                        val isAlreadyFriend = profile.id in friendIds
                        val isPendingOutgoing = profile.id in pendingOutgoingIds

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
                    item { SectionHeader(stringResource(R.string.st_Whisper_SuggestedForYou)) }
                    items(3) { DiscoverSkeleton() }
                }
            } else {
                // Infinite scroll trigger.
                // L-14 FIX (reviewwhisper.md): the old `remember(flags){derivedStateOf{...}}`
                // captured the flags at remember-time and only re-created when they flipped;
                // pagination state read through rememberUpdatedState stays live instead.
                val reachedEndState by rememberUpdatedState(uiState.hasReachedEndOfDiscover)
                val loadingNextState by rememberUpdatedState(uiState.isDiscoverLoadingNext)
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val layoutInfo = lazyListState.layoutInfo
                        val totalItems = layoutInfo.totalItemsCount
                        val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        totalItems > 0 && lastVisibleItem >= totalItems - 5 && !reachedEndState && !loadingNextState
                    }
                }

                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) {
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
                                SectionHeader(stringResource(R.string.st_Whisper_SuggestedForYou))
                            }
                        }

                        items(uiState.recommendedProfiles, key = { "rec_${it.id}" }) { profile ->
                            val isAlreadyFriend = profile.id in friendIds
                            val isPendingOutgoing = profile.id in pendingOutgoingIds

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
                            SectionHeader(stringResource(R.string.st_Whisper_WhisperSomeone))
                        }
                    }

                    items(uiState.discoverProfiles, key = { "disc_${it.id}" }) { profile ->
                        val isAlreadyFriend = profile.id in friendIds
                        val isPendingOutgoing = profile.id in pendingOutgoingIds

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
    // V2-FIX L14: the card itself now opens the profile instead of being an inert
    // clickable wrapper around its own buttons.
    ExpressiveCard(onClick = onViewProfile, modifier = Modifier.fillMaxWidth()) {
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
                        Icon(Icons.Rounded.VerifiedUser, stringResource(R.string.cd_Whisper_FriendBadge), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("@${profile.effectiveUsername}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                    // V3-FIX (item 8a): localized presence label instead of the raw
                    // English model string; enum drives the color.
                    val discoverPresence = profile.presence
                    Text(
                        whisperPresenceLabel(discoverPresence),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (discoverPresence == WhisperPresence.ONLINE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
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
    // V2-FIX M-H4: false while the block state is still being fetched — the block row
    // renders indeterminate/disabled instead of flashing the wrong action.
    blockStateResolved: Boolean,
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
                stringResource(R.string.st_Whisper_ChatOptions_Title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            // M-16 FIX (reviewwhisper.md): all rows render through the shared
            // WhisperOptionsListItem so this sheet and ChatScreen's sheet can never drift.
            WhisperOptionsListItem(
                leadingIcon = Icons.Rounded.Person,
                label = stringResource(R.string.st_Whisper_ViewProfile),
                onClick = onViewProfile,
            )

            WhisperOptionsListItem(
                leadingIcon = Icons.Rounded.CleaningServices,
                label = stringResource(R.string.st_Whisper_ClearHistory),
                onClick = onClearChat,
            )

            WhisperOptionsListItem(
                leadingIcon = if (convo.isMuted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                iconTint = if (convo.isMuted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = if (convo.isMuted) stringResource(R.string.st_Whisper_UnmuteNotifications)
                else stringResource(R.string.st_Whisper_MuteNotifications),
                onClick = onToggleMute,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            WhisperOptionsListItem(
                leadingIcon = if (blockStateResolved && isBlocked) Icons.Rounded.LockOpen else Icons.Rounded.Block,
                iconTint = if (blockStateResolved && isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                label = if (blockStateResolved && isBlocked) stringResource(R.string.st_Whisper_UnblockUser)
                else stringResource(R.string.st_Whisper_BlockUser),
                labelColor = if (blockStateResolved && isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                enabled = blockStateResolved,
                onClick = onToggleBlock,
            )

            // Hide chat (hides from the chats tab, non-destructive)
            WhisperOptionsListItem(
                leadingIcon = Icons.Rounded.VisibilityOff,
                iconTint = MaterialTheme.colorScheme.error,
                label = stringResource(R.string.st_Whisper_HideChat),
                labelColor = MaterialTheme.colorScheme.error,
                onClick = onDeleteChat,
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
        // V2-FIX L19: draw the dialog edge-to-edge so the scrim covers the status and
        // navigation bars; interactive content stays clear of them via safeDrawing
        // padding below while the text keeps its white color.
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = true,
        ),
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
                    // V2-FIX M-M6: clickable(enabled = false) does NOT consume pointers —
                    // taps fell through to the scrim and dismissed the dialog. A no-op
                    // clickable with a real interaction source and null indication
                    // consumes every pointer event silently.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 32.dp)
            ) {
                // Avatar display
                // V6-R7 FIX (noise avatars): ImgBB avatars are AES-GCM ciphertext packed
                // into a PNG — rendering the raw URL here showed colored static. Route
                // through the shared WhisperAvatar decrypt pipeline (loader + codec).
                WhisperAvatar(
                    profile = profile,
                    size = 240.dp,
                    shape = RoundedCornerShape(28.dp),
                    bustCache = true, // L-16: bust right after a self-upload/edit
                )

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

