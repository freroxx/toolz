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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.FriendStatus
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.toolzBackground

/**
 * User Profile Viewer Screen — displays another user's profile card, privacy status,
 * E2EE security verification badge, and relationship actions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhisperUserProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    viewModel: WhisperUserProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = rememberToolzHapticFeedback()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ExpressiveTopAppBar(
                title = { Text(uiState.profile?.effectiveName ?: "", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    ToolzExpressiveIconButton(onClick = { haptic.click(); onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        containerColor = Color.Transparent,
        modifier = Modifier.toolzBackground(),
    ) { paddingValues ->
        if (uiState.isLoading && uiState.profile == null) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                ToolzLoadingIndicator()
            }
        } else {
            val profile = uiState.profile
            if (profile != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // Avatar
                    StaggeredEntrance(index = 0) {
                        WhisperAvatar(profile, 108.dp)
                    }

                    // User identity
                    StaggeredEntrance(index = 1) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                profile.effectiveName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "@${profile.username}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Badges (Privacy & E2EE Status)
                    StaggeredEntrance(index = 2) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Privacy badge
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        if (profile.isPrivate) stringResource(R.string.st_Whisper_Discover_PrivateProfile)
                                        else stringResource(R.string.st_Whisper_Discover_PublicProfile)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (profile.isPrivate) Icons.Rounded.Lock else Icons.Rounded.Public,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                            )

                            // E2EE Key badge
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(if (profile.publicKey != null) "E2EE Secured" else "Standard Auth")
                                },
                                leadingIcon = {
                                    Icon(
                                        if (profile.publicKey != null) Icons.Rounded.Key else Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = if (profile.publicKey != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                            )
                        }
                    }

                    // Bio Card
                    if (!profile.bio.isNullOrBlank()) {
                        StaggeredEntrance(index = 3) {
                            ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        stringResource(R.string.st_Whisper_Profile_Bio),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        profile.bio,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    // Action buttons
                    StaggeredEntrance(index = 4) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // Primary Message Button
                            ToolzExpressiveButton(
                                onClick = { haptic.success(); onNavigateToChat(profile.id) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Chat, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.st_Whisper_Discover_Message), fontWeight = FontWeight.Bold)
                            }

                            // Relationship Action Button
                            val status = uiState.friendshipStatus
                            val record = uiState.friendshipRecord
                            val iSentRequest = record?.userA == viewModel.targetUserId

                            when (status) {
                                FriendStatus.NONE -> {
                                    ToolzTonalExpressiveButton(
                                        onClick = { haptic.click(); viewModel.sendFriendRequest() },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                    ) {
                                        Icon(Icons.Rounded.PersonAdd, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.st_Whisper_Chat_AddFriend), fontWeight = FontWeight.Bold)
                                    }
                                }
                                FriendStatus.PENDING -> {
                                    if (iSentRequest) {
                                        ToolzTonalExpressiveButton(
                                            onClick = { haptic.click(); viewModel.acceptFriendRequest() },
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                        ) {
                                            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.st_Whisper_Friends_Accept), fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        ToolzOutlinedExpressiveButton(
                                            onClick = { haptic.click(); viewModel.removeFriendship() },
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                        ) {
                                            Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.st_Whisper_Friends_Cancel), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                FriendStatus.ACCEPTED -> {
                                    ToolzOutlinedExpressiveButton(
                                        onClick = { haptic.click(); viewModel.removeFriendship() },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    ) {
                                        Icon(Icons.Rounded.PersonRemove, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Remove Friend", fontWeight = FontWeight.Bold)
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}
