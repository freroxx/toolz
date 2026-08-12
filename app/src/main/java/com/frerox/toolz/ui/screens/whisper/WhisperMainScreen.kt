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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import coil3.compose.AsyncImage
import com.frerox.toolz.data.whisper.*
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.toolzBackground

/**
 * The main Whisper hub screen with 4 bottom-nav tabs:
 * Chats · Friends · Discover · Profile
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
    val haptic = rememberToolzHapticFeedback()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }

    // Automatic logout navigation: reactive session observation fixes the "stuck" UI
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated == false) {
            onLoggedOut()
        }
    }

    // Show errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val tabs = listOf(
        Triple(stringResource(R.string.st_Whisper_Tab_Chats), Icons.AutoMirrored.Rounded.Chat, Icons.AutoMirrored.Rounded.Chat),
        Triple(stringResource(R.string.st_Whisper_Tab_Friends), Icons.Rounded.Group, Icons.Rounded.Group),
        Triple(stringResource(R.string.st_Whisper_Tab_Discover), Icons.Rounded.Explore, Icons.Rounded.Explore),
        Triple(stringResource(R.string.st_Whisper_Tab_Profile), Icons.Rounded.Person, Icons.Rounded.Person),
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ExpressiveTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.st_Whisper_Title), fontWeight = FontWeight.Black)
                    }
                },
                actions = {
                    // Unread badge on chats tab icon in top bar
                    val totalUnread = uiState.conversations.sumOf { it.unreadCount }
                    if (totalUnread > 0 && selectedTab != 0) {
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
                    val pendingCount = if (index == 1) uiState.pendingIncoming.size else 0
                    ExpressiveNavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { haptic.click(); selectedTab = index },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (unread > 0 || pendingCount > 0) {
                                        Badge { Text(((unread + pendingCount).coerceAtMost(99)).toString()) }
                                    }
                                }
                            ) {
                                Icon(if (selectedTab == index) selectedIcon else icon, label)
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
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(tween(300), 0.95f))
                        .togetherWith(fadeOut(tween(250)) + scaleOut(tween(250), 0.95f))
                },
                label = "tabContent",
            ) { tab ->
                when (tab) {
                    0 -> ChatsTab(uiState, onNavigateToChat)
                    1 -> FriendsTab(uiState, viewModel, onNavigateToChat)
                    2 -> DiscoverTab(uiState, viewModel, onNavigateToChat, onNavigateToProfile)
                    3 -> ProfileTab(uiState, viewModel, onLoggedOut)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// CHATS TAB
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsTab(
    uiState: WhisperUiState,
    onNavigateToChat: (String) -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()

    // Shimmer loading skeleton
    if (uiState.isLoading && uiState.conversations.isEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(5) { ConversationSkeleton() }
        }
        return
    }

    if (uiState.conversations.isEmpty()) {
        WhisperEmptyState(
            icon = Icons.AutoMirrored.Rounded.Chat,
            title = stringResource(R.string.st_Whisper_Chats_EmptyTitle),
            subtitle = stringResource(R.string.st_Whisper_Chats_EmptySubtitle),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(uiState.conversations, key = { _, c -> c.otherUser.id }) { index, convo ->
            StaggeredEntrance(index = index) {
                ConversationCard(
                    conversation = convo,
                    onClick = {
                        haptic.click()
                        onNavigateToChat(convo.otherUser.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun ConversationCard(
    conversation: WhisperConversation,
    onClick: () -> Unit,
) {
    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WhisperAvatar(profile = conversation.otherUser, size = 50.dp)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        conversation.otherUser.effectiveName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        conversation.lastMessage.createdAt.formatTimestamp(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        conversation.lastMessage.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (conversation.unreadCount > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text(
                                conversation.unreadCount.coerceAtMost(99).toString(),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// FRIENDS TAB
// ─────────────────────────────────────────────────────────────

@Composable
private fun FriendsTab(
    uiState: WhisperUiState,
    viewModel: WhisperViewModel,
    onNavigateToChat: (String) -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()

    if (uiState.friends.isEmpty() && uiState.pendingIncoming.isEmpty() && uiState.pendingOutgoing.isEmpty()) {
        WhisperEmptyState(
            icon = Icons.Rounded.Group,
            title = stringResource(R.string.st_Whisper_Friends_EmptyTitle),
            subtitle = stringResource(R.string.st_Whisper_Friends_EmptySubtitle),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Pending incoming
        if (uiState.pendingIncoming.isNotEmpty()) {
            item {
                SectionHeader("${stringResource(R.string.st_Whisper_Friends_Requests)} (${uiState.pendingIncoming.size})")
            }
            itemsIndexed(uiState.pendingIncoming) { index, friendship ->
                StaggeredEntrance(index = index) {
                    FriendRequestCard(
                        friendship = friendship,
                        onAccept = { haptic.success(); viewModel.acceptFriendRequest(friendship.id) },
                        onDecline = { haptic.click(); viewModel.declineFriendRequest(friendship.id) },
                    )
                }
            }
        }

        // Pending outgoing
        if (uiState.pendingOutgoing.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.st_Whisper_Friends_SentRequests)) }
            itemsIndexed(uiState.pendingOutgoing) { index, friendship ->
                StaggeredEntrance(index = index) {
                    OutgoingRequestCard(
                        friendship = friendship,
                        onCancel = { haptic.click(); viewModel.declineFriendRequest(friendship.id) },
                    )
                }
            }
        }

        // Accepted friends
        if (uiState.friends.isNotEmpty()) {
            item { SectionHeader("${stringResource(R.string.st_Whisper_Tab_Friends)} (${uiState.friends.size})") }
            itemsIndexed(uiState.friends) { index, friend ->
                StaggeredEntrance(index = index) {
                    FriendCard(
                        friend = friend,
                        onChat = { haptic.click(); onNavigateToChat(friend.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendRequestCard(
    friendship: WhisperFriendship,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.st_Whisper_Friends_FriendRequest), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(friendship.userA.take(8) + "\u2026", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ToolzTonalExpressiveButton(onClick = onAccept) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.st_Whisper_Friends_Accept)) }
            ToolzOutlinedExpressiveButton(onClick = onDecline) { Icon(Icons.Rounded.Close, null) }
        }
    }
}

@Composable
private fun OutgoingRequestCard(friendship: WhisperFriendship, onCancel: () -> Unit) {
    ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.HourglassTop, null, tint = MaterialTheme.colorScheme.onTertiaryContainer) }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.st_Whisper_Friends_Pending), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(friendship.userB.take(8) + "…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ToolzOutlinedExpressiveButton(onClick = onCancel) { Text(stringResource(R.string.st_Whisper_Friends_Cancel)) }
        }
    }
}

@Composable
private fun FriendCard(
    friend: WhisperProfile,
    onChat: (() -> Unit)? = null,
) {
    ExpressiveCard(
        onClick = { onChat?.invoke() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WhisperAvatar(friend, 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(friend.effectiveName, fontWeight = FontWeight.Bold)
                Text("@${friend.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onChat != null) {
                Icon(
                    Icons.AutoMirrored.Rounded.Chat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// DISCOVER TAB
// ─────────────────────────────────────────────────────────────

@Composable
private fun DiscoverTab(
    uiState: WhisperUiState,
    viewModel: WhisperViewModel,
    onNavigateToChat: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val haptic = rememberToolzHapticFeedback()

    LaunchedEffect(searchQuery) {
        viewModel.searchProfiles(searchQuery)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search field
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            ExpressiveSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.st_Whisper_Discover_SearchPlaceholder)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (searchQuery.isBlank()) {
            WhisperEmptyState(
                icon = Icons.Rounded.Search,
                title = stringResource(R.string.st_Whisper_Discover_EmptyTitle),
                subtitle = stringResource(R.string.st_Whisper_Discover_EmptySubtitle),
            )
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
            ) {
                itemsIndexed(uiState.searchResults, key = { _, p -> p.id }) { index, profile ->
                    StaggeredEntrance(index = index) {
                        DiscoverUserCard(
                            profile = profile,
                            onChat = { haptic.click(); onNavigateToChat(profile.id) },
                            onViewProfile = { haptic.click(); onNavigateToProfile(profile.id) },
                            onAddFriend = { haptic.success(); viewModel.sendFriendRequest(profile.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverUserCard(
    profile: WhisperProfile,
    onChat: () -> Unit,
    onViewProfile: () -> Unit,
    onAddFriend: () -> Unit,
) {
    ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WhisperAvatar(profile, 48.dp, onClick = if (!profile.isPrivate) onViewProfile else null)

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(profile.effectiveName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    if (profile.isPrivate) {
                        Icon(Icons.Rounded.Lock, "Private", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                    }
                }
                Text("@${profile.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Email intentionally omitted — privacy
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

        // Actions row
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

// ─────────────────────────────────────────────────────────────
// PROFILE TAB
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileTab(
    uiState: WhisperUiState,
    viewModel: WhisperViewModel,
    onLoggedOut: () -> Unit,
) {
    val profile = uiState.currentProfile ?: return
    val haptic = rememberToolzHapticFeedback()
    val context = LocalContext.current

    // username is read-only — chosen at registration, never editable
    val username = profile.username
    var displayName by remember(profile) { mutableStateOf(profile.displayName ?: "") }
    var bio by remember(profile) { mutableStateOf(profile.bio ?: "") }
    var isPrivate by remember(profile) { mutableStateOf(profile.isPrivate) }
    var showLogoutDialog by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Avatar
        StaggeredEntrance(index = 0) {
            Box(contentAlignment = Alignment.BottomEnd) {
                WhisperAvatar(profile, 96.dp, onClick = { imagePickerLauncher.launch("image/*") })
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .bouncyClick(onClick = { imagePickerLauncher.launch("image/*") }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CameraAlt, stringResource(R.string.cd_ChangePhoto), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Profile form
        StaggeredEntrance(index = 1) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = username,
                    onValueChange = {},  // Read-only: username is immutable
                    readOnly = true,
                    label = { Text(stringResource(R.string.st_Whisper_Profile_Username)) },
                    leadingIcon = { Icon(Icons.Rounded.AlternateEmail, null) },
                    trailingIcon = { Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp)) },
                    supportingText = { Text(stringResource(R.string.st_Whisper_Profile_UsernameCannotChange), style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    shape = MediumExpressiveShape,
                    modifier = Modifier.fillMaxWidth(),
                )
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

                // Privacy toggle
                ExpressiveCard(onClick = { isPrivate = !isPrivate }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            if (isPrivate) Icons.Rounded.Lock else Icons.Rounded.Public,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (isPrivate) stringResource(R.string.st_Whisper_Discover_PrivateProfile) else stringResource(R.string.st_Whisper_Discover_PublicProfile), fontWeight = FontWeight.Bold)
                            Text(
                                if (isPrivate) stringResource(R.string.st_Whisper_Profile_PrivateDesc) else stringResource(R.string.st_Whisper_Profile_PublicDesc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ExpressiveSwitch(
                            checked = isPrivate,
                            onCheckedChange = { isPrivate = it },
                        )
                    }
                }
            }
        }

        // Save button
        StaggeredEntrance(index = 2) {
            ToolzExpressiveButton(
                onClick = {
                    haptic.success()
                    viewModel.updateProfile(displayName, bio, isPrivate)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.st_Whisper_Profile_SaveChanges), fontWeight = FontWeight.Bold)
            }
        }

        HorizontalDivider()

        // Logout
        StaggeredEntrance(index = 3) {
            ToolzOutlinedExpressiveButton(
                onClick = { haptic.click(); showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.st_Whisper_Profile_LogOut), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
            title = { Text(stringResource(R.string.st_Whisper_Profile_LogOutTitle), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.st_Whisper_Profile_LogOutDesc)) },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.signOut()
                        onLoggedOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.st_Whisper_Profile_LogOut)) }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(onClick = { showLogoutDialog = false }) { Text(stringResource(R.string.st_Whisper_Friends_Cancel)) }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Shared UI Utilities
// ─────────────────────────────────────────────────────────────

@Composable
fun WhisperAvatar(
    profile: WhisperProfile,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val baseModifier = modifier
        .size(size)
        .clip(CircleShape)
        .then(if (onClick != null) Modifier.bouncyClick(onClick = onClick) else Modifier)

    if (!profile.avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = profile.avatarUrl,
            contentDescription = profile.effectiveName,
            contentScale = ContentScale.Crop,
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

/** Animated shimmer skeleton for a single conversation row while loading */
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = shimmerAlpha))
            .padding(16.dp),
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
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = shimmerAlpha * 0.5f))
            )
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

/** Formats ISO timestamp to a human-friendly short form */
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
