/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.R
import com.frerox.toolz.data.update.UpdateRepository
import com.frerox.toolz.data.update.WhisperBlockState
import com.frerox.toolz.data.whisper.WhisperPinConfig
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton
import com.frerox.toolz.ui.theme.toolzBackground
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Official release page offered from every locally blocked card. */
private const val OFFICIAL_RELEASES_URL = "https://github.com/ferroxx/toolz/releases"

// Block reasons surfaced by [gateWhisperAccess] (LOCAL-ONLY runtime tamper gate).
const val REASON_UNOFFICIAL_BUILD = "unofficial build"
const val REASON_DEBUGGABLE_BUILD = "debuggable build"
const val REASON_UPDATE_REQUIRED = "update required"

/** Result of the LOCAL runtime gate [gateWhisperAccess]. */
sealed interface WhisperGateAccess {
    data object Ok : WhisperGateAccess
    data class Blocked(
        val reason: String,
        val officialDownloadUrl: String = OFFICIAL_RELEASES_URL,
    ) : WhisperGateAccess
}

/**
 * LOCAL-ONLY runtime build-integrity gate — no network, no edge
 * dependency, fail-closed on any doubt. Offline tools are NOT
 * affected: only Whisper entry screens consult this gate.
 *
 * Checks, in order:
 * 1. Signing-cert pin (release builds) — the runtime signing
 *    certificate SHA-256 (PackageManager, upper-case hex) must
 *    match [WhisperPinConfig.OFFICIAL_CERT_SHA256] after
 *    normalization (case/colon/whitespace-insensitive, same
 *    comparison as PlayIntegrityAttestor). Debug builds ship the
 *    self-signed debug-keystore cert and pass locally — the
 *    server-side attestation remains authoritative. A blank pin
 *    is not yet provisioned and skips the check. Mismatch →
 *    [REASON_UNOFFICIAL_BUILD].
 * 2. Debuggable flag — a non-debug (release) build that is still
 *    debuggable was rebuilt/repackaged → [REASON_DEBUGGABLE_BUILD].
 * 3. Version floor — installed versionCode below the last
 *    signature-verified manifest floor ([cachedMinimumVersionCode])
 *    or the static [WhisperPinConfig.MIN_VERSION_CODE] →
 *    [REASON_UPDATE_REQUIRED].
 */
fun gateWhisperAccess(
    context: Context,
    cachedMinimumVersionCode: Int? = null,
): WhisperGateAccess {
    val certPin = WhisperPinConfig.OFFICIAL_CERT_SHA256
    if (certPin.isNotBlank() && !com.frerox.toolz.BuildConfig.DEBUG) {
        val expected = normalizeCertPin(certPin)
        val signerPins = runtimeSigningCertSha256s(context)
        if (signerPins.none { normalizeCertPin(it) == expected }) {
            return WhisperGateAccess.Blocked(REASON_UNOFFICIAL_BUILD)
        }
    }

    val isDebuggable =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!com.frerox.toolz.BuildConfig.DEBUG && isDebuggable) {
        return WhisperGateAccess.Blocked(REASON_DEBUGGABLE_BUILD)
    }

    val floor = maxOf(
        (cachedMinimumVersionCode ?: 0).toLong(),
        WhisperPinConfig.MIN_VERSION_CODE
    )
    if (floor > 0) {
        val installedCode = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .longVersionCode
        if (installedCode < floor) {
            return WhisperGateAccess.Blocked(REASON_UPDATE_REQUIRED)
        }
    }
    return WhisperGateAccess.Ok
}

/** Case-, colon- and whitespace-insensitive comparison, as in PlayIntegrityAttestor. */
private fun normalizeCertPin(raw: String): String =
    raw.replace(":", "").replace(WHITESPACE, "").uppercase()

private val WHITESPACE = Regex("\\s")

/**
 * SHA-256 of every signing cert of this APK, upper-case hex
 * without colons — the same format as
 * [WhisperPinConfig.OFFICIAL_CERT_SHA256].
 */
private fun runtimeSigningCertSha256s(context: Context): List<String> {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
        val signing = packageInfo.signingInfo
        val signers = when {
            signing == null -> emptyList()
            signing.hasMultipleSigners() -> signing.apkContentsSigners.toList()
            else -> signing.signingCertificateHistory.toList()
        }
        signers.mapNotNull { signer ->
            runCatching {
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(signer.toByteArray())
                    .joinToString("") { "%02X".format(it) }
            }.getOrNull()
        }
    } catch (e: Exception) {
        emptyList() // unknown signers never match a non-blank pin → blocked
    }
}

/**
 * Anti-mod nav gate for Whisper entry screens.
 *
 * Usage (top of [WhisperMainScreen] / [WhisperAuthScreen], before content):
 * ```
 * if (WhisperBuildGate()) return
 * ```
 * Returns true when the screen must stop rendering (a blocking card or dialog
 * is showing instead). Covers THREE locks:
 * - runtime tamper gate ([gateWhisperAccess], LOCAL-ONLY): unofficial
 *   signing cert, debuggable release build, or version floor — shown as a
 *   full-screen card with the block reason and the official download URL;
 * - update-lock: installed build below manifest `minimumVersionCode`, or a
 *   critical release is newer than the install;
 * - unofficial-build: latest edge call returned 428 (surfaced via
 *   [WhisperUnofficialBuildDialog], driven by the caller's own state).
 */
@Composable
fun WhisperBuildGate(
    gateViewModel: WhisperGateViewModel = hiltViewModel(),
): Boolean {
    val gateState by gateViewModel.gateState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { gateViewModel.refresh() }

    val blocked = gateState as? WhisperBlockState.Blocked ?: return false
    WhisperUpdateBlockedScreen(blocked = blocked, onRefresh = { gateViewModel.refresh() })
    return true
}

@HiltViewModel
class WhisperGateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _gateState = MutableStateFlow<WhisperBlockState>(WhisperBlockState.NotBlocked)
    val gateState: StateFlow<WhisperBlockState> = _gateState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            // Local runtime tamper gate first (offline-safe, fail-closed
            // on mismatched cert pin / debuggable release build / old
            // version floor). Only when it passes does the network
            // update-lock apply.
            val local = gateWhisperAccess(
                context,
                updateRepository.cachedMinimumVersionCode()
            )
            _gateState.value = when (local) {
                is WhisperGateAccess.Ok -> updateRepository.checkWhisperBlocked()
                is WhisperGateAccess.Blocked -> WhisperBlockState.Blocked(
                    latestVersionName = "",
                    latestVersionCode = 0,
                    isCritical = false,
                    reason = local.reason,
                    officialDownloadUrl = local.officialDownloadUrl,
                )
            }
        }
    }
}

/** Full-screen blocking card: no chat UI, no send path reachable. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhisperUpdateBlockedScreen(
    blocked: WhisperBlockState.Blocked,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    // Locally blocked (runtime tamper gate): a reason is set and the
    // download button points at the official releases page.
    val isLocalBlock = blocked.reason == REASON_UNOFFICIAL_BUILD ||
        blocked.reason == REASON_DEBUGGABLE_BUILD
    val titleRes = if (isLocalBlock) {
        R.string.st_Whisper_Unofficial_Title
    } else {
        R.string.st_Whisper_UpdateRequired_Title
    }
    val message = when (blocked.reason) {
        REASON_UNOFFICIAL_BUILD -> stringResource(R.string.st_Whisper_Unofficial_Message)
        REASON_DEBUGGABLE_BUILD -> stringResource(R.string.st_Whisper_Debuggable_Blocked)
        REASON_UPDATE_REQUIRED -> stringResource(R.string.st_Whisper_UpdateRequired_Local)
        else -> if (blocked.isCritical) {
            stringResource(R.string.st_Whisper_UpdateRequired_Critical)
        } else {
            stringResource(R.string.st_Whisper_UpdateRequired_Message, blocked.latestVersionName)
        }
    }
    val downloadUrl = blocked.officialDownloadUrl
        ?: "https://github.com/freroxx/toolz/releases/latest"
    val downloadLabelRes = if (isLocalBlock) {
        R.string.st_Whisper_Unofficial_GetOfficial
    } else {
        R.string.st_Whisper_UpdateRequired_UpdateNow
    }
    Box(
        modifier = Modifier.fillMaxSize().toolzBackground().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isLocalBlock) Icons.Rounded.Security else Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            ToolzExpressiveButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, downloadUrl.toUri()))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(downloadLabelRes),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            ToolzOutlinedExpressiveButton(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.st_Whisper_Retry))
            }
        }
    }
}

/**
 * 428 dialog: shown when an edge call reports an unofficial build. Points at
 * the official release page; dismiss just closes the dialog (Whisper stays
 * unusable until the official APK is installed — the server keeps refusing).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhisperUnofficialBuildDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
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
                Icon(
                    Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        title = {
            Text(
                stringResource(R.string.st_Whisper_Unofficial_Title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                stringResource(R.string.st_Whisper_Unofficial_Message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            ToolzExpressiveButton(
                onClick = {
                    val url = "https://github.com/freroxx/toolz/releases/latest"
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.st_Whisper_Unofficial_GetOfficial),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            ToolzOutlinedExpressiveButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.st_Whisper_Cancel))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    )
}
