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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.FriendStatus
import com.frerox.toolz.data.whisper.KeyTrustStatus
import com.frerox.toolz.data.whisper.WhisperCrypto
import com.frerox.toolz.data.whisper.asString
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.toolzBackground

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
    val screenshotBypassEnabled by viewModel.screenshotBypassEnabled.collectAsStateWithLifecycle()
    val haptic = rememberToolzHapticFeedback()
    val toastState = rememberWhisperToastState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showBypassDialog by remember { mutableStateOf(false) }
    // V2-FIX (verify in-flight guard): track password verification locally so the dialog
    // stays open, locks its buttons and shows progress until the verdict arrives.
    var isVerifyingBypass by remember { mutableStateOf(false) }

    // User profile data is sensitive — never capture this screen.
    SecureWindow(bypassEnabled = screenshotBypassEnabled)

    if (showBypassDialog) {
        // M-17 FIX (reviewwhisper.md): password required to enable AND disable; unified copy.
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

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            toastState.show(it.asString(context), WhisperToastType.ERROR)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                ExpressiveTopAppBar(
                    modifier = Modifier.screenshotBypassGesture {
                        // M-17: verification happens inside the dialog for both directions.
                        showBypassDialog = true
                    },
                    // V2-FIX: placeholder title while the profile loads instead of a blank bar.
                    title = {
                        val loadedProfile = uiState.profile
                        if (loadedProfile == null) {
                            Text(
                                stringResource(R.string.st_Whisper_Loading),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(loadedProfile.effectiveName, fontWeight = FontWeight.Bold)
                        }
                    },
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
                    if (uiState.loadFailed) {
                        // Load failed (offline, server error, …) — offer a retry instead of
                        // pretending the user does not exist.
                        Column(
                            modifier = Modifier.fillMaxSize().padding(paddingValues),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            WhisperEmptyState(
                                icon = Icons.Rounded.CloudOff,
                                title = stringResource(R.string.st_Whisper_Error_Offline),
                                subtitle = stringResource(R.string.st_Whisper_Profile_LoadFailedDesc),
                            )
                            ToolzExpressiveButton(
                                onClick = { haptic.click(); viewModel.loadData() },
                                modifier = Modifier.padding(horizontal = 24.dp),
                            ) {
                                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.st_Whisper_Retry), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        WhisperEmptyState(
                            icon = Icons.Rounded.PersonOff,
                            title = stringResource(R.string.st_Whisper_Profile_NotFoundTitle),
                            subtitle = stringResource(R.string.st_Whisper_Profile_NotFoundDesc),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                            .fadingEdges(top = 16.dp, bottom = 24.dp)
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
                                "@${profile.effectiveUsername}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            
                            // V2-FIX: WhisperProfile exposes onlineStatus only as a formatted
                            // String (no enum/status type in WhisperModels), so the literal
                            // compare stays for now.
                            // DEFERRED-LOCALIZE/TODO-MODEL: switch to a typed presence enum when
                            // one is added to the model — localizing the raw status string needs
                            // a model-layer change, not a resource swap.
                            Text(
                                profile.onlineStatus,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (profile.onlineStatus == "Online") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                            if (profile.publicKey != null) stringResource(R.string.st_Whisper_Profile_E2EEBadge) else stringResource(R.string.st_Whisper_Profile_StandardAuth),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }

                        // Bio Card
                        if (!profile.bio.isNullOrBlank()) {
                            // V2-FIX L14: inert ExpressiveCard(onClick = {}) replaced with a
                            // visually identical Surface — a bio has no card-level action.
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(32.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
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

                        // E2EE Security Verification Card
                        if (profile.publicKey != null) {
                            val keyTrust = uiState.keyTrust
                            // V2-FIX: hash the public key once per key value — this used to run
                            // SHA-256 inside the Text on every recomposition.
                            val computedFingerprint = remember(profile.publicKey) { profile.publicKey?.let { computeFingerprint(it) } }
                            // V2-FIX L14: inert ExpressiveCard(onClick = {}) replaced with a
                            // visually identical Surface; actions live on the buttons below.
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(32.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            if (keyTrust?.isVerified == true) Icons.Rounded.VerifiedUser else Icons.Rounded.Shield,
                                            contentDescription = null,
                                            tint = if (keyTrust?.isVerified == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            if (keyTrust?.isVerified == true) stringResource(R.string.st_Whisper_Profile_KeyVerifiedTitle) else stringResource(R.string.st_Whisper_Profile_KeyUnverifiedTitle),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        when {
                                            keyTrust?.isVerified == true ->
                                                stringResource(R.string.st_Whisper_Profile_KeyVerifiedDesc)
                                            keyTrust?.status == KeyTrustStatus.CHANGED ->
                                                stringResource(R.string.st_Whisper_Profile_KeyChangedDesc)
                                            keyTrust?.status == KeyTrustStatus.ROTATED_AUTO ->
                                                keyTrust.rotateMessage?.asString(context)
                                                    ?: stringResource(R.string.st_Whisper_Profile_KeyRotatedAutoFallback)
                                            keyTrust?.status == KeyTrustStatus.ROTATED_MANUAL ->
                                                keyTrust.rotateMessage?.asString(context)
                                                    ?: stringResource(R.string.st_Whisper_Profile_KeyRotatedManualFallback, profile.effectiveName)
                                            else ->
                                                stringResource(R.string.st_Whisper_Profile_KeyFingerprintDesc, profile.effectiveName)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = uiState.keyTrust?.partnerFingerprint ?: computedFingerprint ?: stringResource(R.string.st_Whisper_Unverified),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }

                                    when {
                                        keyTrust?.status == KeyTrustStatus.CHANGED || keyTrust?.status == KeyTrustStatus.ROTATED_AUTO || keyTrust?.status == KeyTrustStatus.ROTATED_MANUAL -> {
                                            val isWarning = keyTrust?.status == KeyTrustStatus.CHANGED
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                ToolzTonalExpressiveButton(
                                                    onClick = { haptic.success(); viewModel.verifyKey() },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(if (isWarning) stringResource(R.string.st_Whisper_Verify) else stringResource(R.string.st_Whisper_VerifyOptional), fontWeight = FontWeight.Bold)
                                                }
                                                ToolzOutlinedExpressiveButton(
                                                    onClick = { haptic.click(); viewModel.acceptNewKey() },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(if (isWarning) stringResource(R.string.st_Whisper_Profile_AcceptNewKey) else stringResource(R.string.st_Whisper_AcceptShort), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        keyTrust != null && !keyTrust.isVerified -> {
                                            Text(
                                                stringResource(R.string.st_Whisper_Profile_NotVerifiedNote),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
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
                            // userA is always the requester: if the TARGET sent it, I can accept.
                            val partnerSentRequest = record?.userA == viewModel.targetUserId

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
                                    if (record == null) {
                                        // V2-FIX: PENDING without a friendship record yet — render a
                                        // neutral pending row instead of a dead Cancel button whose
                                        // action requires record.id.
                                        ToolzOutlinedExpressiveButton(
                                            onClick = {},
                                            enabled = false,
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                        ) {
                                            Icon(Icons.Rounded.HourglassTop, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.st_Whisper_Friends_Pending), fontWeight = FontWeight.Bold)
                                        }
                                    } else if (partnerSentRequest) {
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

/**
 * Fingerprint of a base64 public key — delegates to WhisperCrypto.computeFingerprint,
 * the single source of truth (this file previously carried a duplicated hash
 * implementation that could silently drift from the crypto module's algorithm).
 */
internal fun whisperFingerprint(base64Key: String): String =
    WhisperCrypto.computeFingerprint(base64Key) ?: "UNVERIFIED"

/** Compute a SHA-256 fingerprint from a base64 public key — P1-17 delegates to single source. */
private fun computeFingerprint(base64PublicKey: String): String = whisperFingerprint(base64PublicKey)
