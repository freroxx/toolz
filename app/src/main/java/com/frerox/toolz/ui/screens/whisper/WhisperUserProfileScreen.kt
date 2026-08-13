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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.FriendStatus
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.toolzBackground
import java.security.MessageDigest

/**
 * User Profile Viewer Screen — displays another user's profile card, privacy status,
 * E2EE security verification badge, security fingerprint, and relationship actions.
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
    val toastState = rememberWhisperToastState()

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
                    title = { Text(uiState.profile?.effectiveName ?: "", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        ToolzExpressiveIconButton(onClick = { haptic.click(); onNavigateBack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_Back))
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
                if (profile == null) {
                    WhisperEmptyState(
                        icon = Icons.Rounded.PersonOff,
                        title = "User not found",
                        subtitle = "This profile does not exist or has been removed.",
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        // User Avatar & Name Card
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            WhisperAvatar(profile, 96.dp)

                            Text(
                                profile.effectiveName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )

                            Text(
                                "@${profile.username}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Privacy status badge
                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            if (profile.isPrivate) Icons.Rounded.Lock else Icons.Rounded.Public,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            if (profile.isPrivate) stringResource(R.string.st_Whisper_Discover_PrivateProfile) else stringResource(R.string.st_Whisper_Discover_PublicProfile),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }

                                // E2EE badge
                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = if (profile.publicKey != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            if (profile.publicKey != null) Icons.Rounded.Key else Icons.Rounded.Shield,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (profile.publicKey != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                        Text(
                                            if (profile.publicKey != null) stringResource(R.string.st_Whisper_Profile_E2EEBadge) else "Standard Auth",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }

                        // Bio Card
                        if (!profile.bio.isNullOrBlank()) {
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

                        // E2EE Security Verification Card (Feature 7)
                        if (profile.publicKey != null) {
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
                                            "E2EE Security Fingerprint",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        "Compare this key fingerprint with ${profile.effectiveName} to verify your end-to-end encrypted session is secure.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = computeFingerprint(profile.publicKey),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Action buttons
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
                                        Text(stringResource(R.string.st_Whisper_Profile_RemoveFriend), fontWeight = FontWeight.Bold)
                                    }
                                }
                                else -> {}
                            }
                        }
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
}

/** Compute a SHA-256 fingerprint from a base64 public key */
private fun computeFingerprint(base64PublicKey: String): String {
    return try {
        val bytes = MessageDigest.getInstance("SHA-256").digest(base64PublicKey.trim().toByteArray(Charsets.UTF_8))
        val hex = bytes.joinToString("") { "%02X".format(it) }
        hex.chunked(4).take(4).joinToString("-")
    } catch (_: Exception) {
        "UNVERIFIED"
    }
}
