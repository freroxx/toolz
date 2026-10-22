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

package com.frerox.toolz.ui.screens.settings

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.frerox.toolz.R
import com.frerox.toolz.data.developer.DevUiOverrider
import com.frerox.toolz.data.update.Eta
import com.frerox.toolz.data.update.PendingUpdate
import com.frerox.toolz.data.update.RelativeAge
import com.frerox.toolz.data.update.UpdateError
import com.frerox.toolz.data.update.UpdateManagerState
import com.frerox.toolz.data.update.etaOf
import com.frerox.toolz.data.update.formatBytes
import com.frerox.toolz.data.update.relativeAge
import com.frerox.toolz.ui.components.MarkdownContent
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzLoadingIndicator
import com.frerox.toolz.ui.components.ToolzWavyLinearProgressIndicator
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground

private const val RELEASES_URL = "https://github.com/freroxx/toolz/releases"

// Updater surfaces use tighter corners than the app-wide defaults so the
// screen reads as a compact utility rather than a stack of balloons.
private val CardShape = RoundedCornerShape(20.dp)
private val ButtonShape = RoundedCornerShape(16.dp)
private val ChipShape = RoundedCornerShape(8.dp)
private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val IconTileShape = RoundedCornerShape(16.dp)

private val DiscordBlurpleDeep = Color(0xFF4752C4)
private val DiscordGradient = listOf(Color(0xFF5865F2), Color(0xFF8A5CF6))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    currentVersionName: String,
    currentVersionCode: Long,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val managerState by viewModel.managerState.collectAsState()
    val preferredAbi by viewModel.preferredAbi.collectAsState()
    val autoUpdate by viewModel.autoUpdateEnabled.collectAsState()
    val lastChecked by viewModel.lastChecked.collectAsState()

    // The user may leave to grant "install unknown apps"; pick it up on return.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshInstallPermission()
        onPauseOrDispose { }
    }

    UpdateScreenContent(
        onBack = onBack,
        currentVersionName = currentVersionName,
        currentVersionCode = currentVersionCode,
        managerState = managerState,
        preferredAbi = preferredAbi,
        autoUpdateEnabled = autoUpdate,
        lastChecked = lastChecked,
        onCheck = { viewModel.checkForUpdates() },
        onDownload = { viewModel.startDownload(it) },
        onCancelDownload = { viewModel.cancelDownload() },
        onInstall = { viewModel.installReady() },
        onRetry = { viewModel.retry() },
        onSkip = { viewModel.skipVersion(it) },
        onDismissInstalled = { viewModel.dismissInstalled() },
        onSetPreferredAbi = { viewModel.setPreferredAbi(it) },
        onSetAutoUpdate = { viewModel.setAutoUpdateEnabled(it) },
        getDeviceAbi = { viewModel.getDeviceAbi() },
        onOpenInstallSettings = { ctx -> viewModel.openInstallSettings(ctx) },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateScreenContent(
    onBack: () -> Unit,
    currentVersionName: String,
    currentVersionCode: Long,
    managerState: UpdateManagerState,
    preferredAbi: String,
    autoUpdateEnabled: Boolean,
    lastChecked: Long,
    onCheck: () -> Unit,
    onDownload: (PendingUpdate?) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onSkip: (String) -> Unit,
    onDismissInstalled: () -> Unit,
    onSetPreferredAbi: (String) -> Unit,
    onSetAutoUpdate: (Boolean) -> Unit,
    getDeviceAbi: () -> String,
    onOpenInstallSettings: (Context) -> Unit,
) {
    var showAbiSheet by rememberSaveable { mutableStateOf(false) }
    var showHelpSheet by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.toolzBackground(),
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.st_Update_Title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.st_Update_Back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpSheet = true }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.HelpOutline,
                            contentDescription = stringResource(R.string.st_Update_About),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .scrollEdgeFade(scrollState)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VersionHero(
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                managerState = managerState,
            )

            AnimatedContent(
                targetState = managerState,
                // Keep progress ticks from replaying the transition; only a
                // change of state type is a new card.
                contentKey = { it::class },
                transitionSpec = {
                    (
                        fadeIn(tween(240)) +
                            slideInVertically(
                                animationSpec = tween(240, easing = LinearOutSlowInEasing),
                                initialOffsetY = { it / 14 },
                            ) +
                            scaleIn(initialScale = 0.98f, animationSpec = tween(240))
                        ).togetherWith(
                        fadeOut(tween(130)) +
                            slideOutVertically(
                                animationSpec = tween(130),
                                targetOffsetY = { -it / 24 },
                            )
                    ).using(SizeTransform(clip = false))
                },
                label = "update_state",
            ) { state ->
                when (state) {
                    is UpdateManagerState.Idle -> IdleCard(
                        currentVersionName = currentVersionName,
                        onCheck = onCheck,
                    )

                    is UpdateManagerState.Checking -> WorkingCard(
                        title = stringResource(R.string.st_Update_StatusChecking),
                        body = stringResource(R.string.st_Update_CheckingBody),
                    )

                    is UpdateManagerState.Available -> AvailableCard(
                        update = state.update,
                        onDownload = { onDownload(state.update) },
                        onSkip = { onSkip(state.update.version) },
                    )

                    is UpdateManagerState.Downloading -> DownloadingCard(
                        update = state.update,
                        downloaded = state.bytesDownloaded,
                        total = state.totalBytes,
                        speedBps = state.speedBps,
                        progress = state.progress,
                        onCancel = onCancelDownload,
                    )

                    is UpdateManagerState.Verifying -> WorkingCard(
                        title = stringResource(R.string.st_Update_VerifyingTitle),
                        body = stringResource(
                            R.string.st_Update_VerifyingBody,
                            state.update.version,
                        ),
                    )

                    is UpdateManagerState.ReadyToInstall -> ReadyCard(
                        update = state.update,
                        needsPermission = state.needsPermission,
                        onInstall = onInstall,
                        onOpenSettings = { ctx -> onOpenInstallSettings(ctx) },
                    )

                    is UpdateManagerState.Installing -> WorkingCard(
                        title = stringResource(R.string.st_Update_InstallingTitle),
                        body = stringResource(R.string.st_Update_InstallingBody),
                    )

                    is UpdateManagerState.Error -> ErrorCard(
                        state = state,
                        onRetry = onRetry,
                    )

                    is UpdateManagerState.UpToDate -> UpToDateCard(
                        currentVersionName = currentVersionName,
                        onCheck = onCheck,
                    )

                    is UpdateManagerState.Installed -> InstalledCard(
                        version = state.version,
                        onDone = onDismissInstalled,
                    )
                }
            }

            UpdateSettingsCard(
                autoUpdateEnabled = autoUpdateEnabled,
                preferredAbi = preferredAbi,
                deviceAbiLabel = abiLabel(getDeviceAbi()),
                lastChecked = lastChecked,
                onSetAutoUpdate = onSetAutoUpdate,
                onOpenAbiPicker = { showAbiSheet = true },
            )

            DiscordCard()

            Text(
                text = stringResource(R.string.st_Update_Footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }

    if (showAbiSheet) {
        AbiPickerSheet(
            preferredAbi = preferredAbi,
            deviceAbiLabel = abiLabel(getDeviceAbi()),
            onSelect = {
                onSetPreferredAbi(it)
                showAbiSheet = false
            },
            onDismiss = { showAbiSheet = false },
        )
    }

    if (showHelpSheet) {
        HelpSheet(onDismiss = { showHelpSheet = false })
    }
}

// ── Current version + status ────────────────────────────────────────────────

@Composable
private fun VersionHero(
    currentVersionName: String,
    currentVersionCode: Long,
    managerState: UpdateManagerState,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = IconTileShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Column {
                    Text(
                        text = "Toolz",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.st_Update_VersionBuild,
                            currentVersionName,
                            currentVersionCode,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusDot(managerState)
                AnimatedContent(
                    targetState = statusLine(managerState),
                    transitionSpec = {
                        fadeIn(tween(190)) togetherWith fadeOut(tween(120))
                    },
                    modifier = Modifier.weight(1f),
                    label = "update_status",
                ) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                AnimatedVisibility(
                    visible = (managerState as? UpdateManagerState.Available)
                        ?.update?.isCritical == true,
                    enter = fadeIn(tween(180)) + expandHorizontally(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(tween(180)),
                ) {
                    Surface(
                        shape = ChipShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.st_Update_CriticalUpdate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(state: UpdateManagerState) {
    val working = state is UpdateManagerState.Checking ||
        state is UpdateManagerState.Downloading ||
        state is UpdateManagerState.Verifying ||
        state is UpdateManagerState.Installing
    val color = when (state) {
        is UpdateManagerState.Error -> MaterialTheme.colorScheme.error
        is UpdateManagerState.UpToDate,
        is UpdateManagerState.Installed -> MaterialTheme.colorScheme.tertiary
        is UpdateManagerState.Idle ->
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.primary
    }
    val inspection = LocalInspectionMode.current
    val alpha = if (working && !inspection) {
        val transition = rememberInfiniteTransition(label = "status_dot")
        val pulse by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(850, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "status_dot_alpha",
        )
        pulse
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

/** Check failures are the only errors that mean "we couldn't look". */
private fun UpdateManagerState.Error.isCheckFailure(): Boolean =
    !retryDownload && when (error) {
        is UpdateError.Offline,
        is UpdateError.Timeout,
        is UpdateError.RateLimited,
        is UpdateError.Http,
        is UpdateError.NoCompatibleBuild -> true
        else -> false
    }

@Composable
private fun statusLine(state: UpdateManagerState): String = when (state) {
    is UpdateManagerState.Idle -> stringResource(R.string.st_Update_StatusIdle)
    is UpdateManagerState.Checking -> stringResource(R.string.st_Update_StatusChecking)
    is UpdateManagerState.Available -> stringResource(
        R.string.st_Update_StatusAvailable,
        state.update.version,
    )
    is UpdateManagerState.Downloading -> stringResource(
        R.string.st_Update_StatusDownloading,
        state.update.version,
    )
    is UpdateManagerState.Verifying -> stringResource(
        R.string.st_Update_StatusVerifying,
        state.update.version,
    )
    is UpdateManagerState.ReadyToInstall -> stringResource(
        R.string.st_Update_StatusReady,
        state.update.version,
    )
    is UpdateManagerState.Installing -> stringResource(
        R.string.st_Update_StatusInstalling,
        state.update.version,
    )
    is UpdateManagerState.Error -> stringResource(
        if (state.isCheckFailure()) R.string.st_Update_StatusErrorCheck
        else R.string.st_Update_StatusErrorDownload,
    )
    is UpdateManagerState.UpToDate -> stringResource(R.string.st_Update_StatusUpToDate)
    is UpdateManagerState.Installed -> if (state.version.isBlank()) {
        stringResource(R.string.st_Update_InstalledTitle)
    } else {
        stringResource(R.string.st_Update_StatusInstalled, state.version)
    }
}

// ── State cards ─────────────────────────────────────────────────────────────

@Composable
private fun IdleCard(
    currentVersionName: String,
    onCheck: () -> Unit,
) {
    StateCard {
        Text(
            text = stringResource(R.string.st_Update_IdleTitle),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.st_Update_IdleBody, currentVersionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryAction(
            text = stringResource(R.string.st_Update_CheckNow),
            icon = Icons.Rounded.Refresh,
            onClick = onCheck,
        )
    }
}

@Composable
private fun AvailableCard(
    update: PendingUpdate,
    onDownload: () -> Unit,
    onSkip: () -> Unit,
) {
    var expanded by rememberSaveable(update.version) { mutableStateOf(false) }
    val hasLongNotes = update.changelog.length > 420
    StateCard {
        Text(
            text = stringResource(R.string.st_Update_VersionTitle, update.version),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            update.sizeBytes?.let { MetaChip(formatBytes(it)) }
            if (update.abi.isNotBlank()) MetaChip(abiLabel(update.abi))
            if (update.isCritical) {
                Surface(
                    shape = ChipShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = stringResource(R.string.st_Update_Critical),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.st_Update_CheckedBeforeInstall),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (update.hasReleaseNotes) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(14.dp)) {
                    Box(
                        modifier = Modifier.heightIn(
                            max = if (expanded) 2_000.dp else 176.dp
                        )
                    ) {
                        MarkdownContent(
                            markdown = update.changelog,
                            baseFontSize = 14.sp,
                            textColor = MaterialTheme.colorScheme.onSurface,
                        )
                        androidx.compose.animation.AnimatedVisibility(
                            visible = hasLongNotes && !expanded,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(120)),
                            modifier = Modifier.align(Alignment.BottomCenter),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                            ),
                                        )
                                    )
                            )
                        }
                    }
                    if (hasLongNotes) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.align(Alignment.Start),
                        ) {
                            Text(
                                stringResource(
                                    if (expanded) R.string.st_Update_ShowLess
                                    else R.string.st_Update_ShowFullNotes
                                )
                            )
                        }
                    }
                }
            }
        }
        PrimaryAction(
            text = stringResource(R.string.st_Update_DownloadInstall),
            icon = Icons.Rounded.Download,
            onClick = onDownload,
        )
        if (update.isCritical) {
            Text(
                text = stringResource(R.string.st_Update_CriticalCantSkip),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        } else {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.st_Update_SkipVersion))
            }
        }
    }
}

@Composable
private fun DownloadingCard(
    update: PendingUpdate,
    downloaded: Long,
    total: Long,
    speedBps: Long,
    progress: Float,
    onCancel: () -> Unit,
) {
    val smoothProgress by animateFloatAsState(
        targetValue = (progress / 100f).coerceIn(0f, 1f),
        animationSpec = tween(450, easing = LinearOutSlowInEasing),
        label = "download_progress",
    )
    val shownPercent by animateIntAsState(
        targetValue = progress.toInt().coerceIn(0, 100),
        animationSpec = tween(450, easing = LinearOutSlowInEasing),
        label = "download_percent",
    )
    StateCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.st_Update_DownloadingTitle, update.version),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$shownPercent%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        ToolzWavyLinearProgressIndicator(
            progress = { smoothProgress },
            modifier = Modifier.fillMaxWidth().height(12.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (total > 0L) {
                    stringResource(
                        R.string.st_Update_Of,
                        formatBytes(downloaded),
                        formatBytes(total),
                    )
                } else {
                    formatBytes(downloaded)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            val eta = if (total > downloaded) etaLabel(total - downloaded, speedBps) else null
            Text(
                text = listOfNotNull(
                    speedBps.takeIf { it > 0L }?.let {
                        stringResource(R.string.st_Update_PerSecond, formatBytes(it))
                    },
                    eta,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.st_Update_CancelDownload))
        }
    }
}

@Composable
private fun ReadyCard(
    update: PendingUpdate,
    needsPermission: Boolean,
    onInstall: () -> Unit,
    onOpenSettings: (Context) -> Unit,
) {
    val context = LocalContext.current
    StateCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.st_Update_ReadyTitle, update.version),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = listOfNotNull(
                        update.sizeBytes?.let { formatBytes(it) },
                        stringResource(R.string.st_Update_Verified),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (needsPermission) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.st_Update_AllowInstallsTitle),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.st_Update_AllowInstallsBody),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    TextButton(onClick = { onOpenSettings(context) }) {
                        Text(stringResource(R.string.st_Update_OpenSettings))
                    }
                }
            }
        }
        PrimaryAction(
            text = stringResource(R.string.st_Update_InstallUpdate),
            icon = Icons.Rounded.InstallMobile,
            onClick = onInstall,
            enabled = !needsPermission,
        )
        Text(
            text = stringResource(R.string.st_Update_SystemInstallerHint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WorkingCard(
    title: String,
    body: String,
) {
    StateCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolzLoadingIndicator(modifier = Modifier.size(40.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    state: UpdateManagerState.Error,
    onRetry: () -> Unit,
) {
    StateCard {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(
                if (state.isCheckFailure()) R.string.st_Update_StatusErrorCheck
                else R.string.st_Update_ErrorDownloadTitle
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(state.error.messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryAction(
            text = stringResource(R.string.st_Update_TryAgain),
            icon = Icons.Rounded.Refresh,
            onClick = onRetry,
        )
    }
}

@Composable
private fun UpToDateCard(
    currentVersionName: String,
    onCheck: () -> Unit,
) {
    StateCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.st_Update_UpToDateTitle),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.st_Update_UpToDateBody,
                        currentVersionName,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.st_Update_CheckAgain))
        }
    }
}

@Composable
private fun InstalledCard(
    version: String,
    onDone: () -> Unit,
) {
    StateCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Column {
                Text(
                    text = if (version.isBlank()) {
                        stringResource(R.string.st_Update_InstalledTitle)
                    } else {
                        stringResource(R.string.st_Update_InstalledVersionTitle, version)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.st_Update_InstalledBody),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PrimaryAction(
            text = stringResource(R.string.st_Update_Done),
            icon = Icons.Rounded.CheckCircle,
            onClick = onDone,
        )
    }
}

// ── Settings ────────────────────────────────────────────────────────────────

@Composable
private fun UpdateSettingsCard(
    autoUpdateEnabled: Boolean,
    preferredAbi: String,
    deviceAbiLabel: String,
    lastChecked: Long,
    onSetAutoUpdate: (Boolean) -> Unit,
    onOpenAbiPicker: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.st_Update_AutoTitle),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.st_Update_AutoBody),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = autoUpdateEnabled, onCheckedChange = onSetAutoUpdate)
            }
            CardDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAbiPicker)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.st_Update_BuildTitle),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (preferredAbi == "AUTO") {
                            stringResource(R.string.st_Update_BuildAuto, deviceAbiLabel)
                        } else {
                            stringResource(R.string.st_Update_BuildManual)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CardDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.st_Update_LastChecked),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = lastCheckedLabel(lastChecked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun lastCheckedLabel(timestamp: Long): String {
    if (timestamp <= 0L) return stringResource(R.string.st_Update_NotYet)
    return when (val age = relativeAge(System.currentTimeMillis() - timestamp)) {
        is RelativeAge.JustNow -> stringResource(R.string.st_Update_JustNow)
        is RelativeAge.Minutes -> {
            val minutes = age.minutes.toInt()
            pluralStringResource(R.plurals.st_Update_MinutesAgo, minutes, minutes)
        }
        is RelativeAge.Hours -> {
            val hours = age.hours.toInt()
            pluralStringResource(R.plurals.st_Update_HoursAgo, hours, hours)
        }
        is RelativeAge.Days -> {
            val days = age.days.toInt()
            pluralStringResource(R.plurals.st_Update_DaysAgo, days, days)
        }
    }
}

@Composable
private fun etaLabel(remainingBytes: Long, bps: Long): String? {
    val eta = etaOf(remainingBytes, bps) ?: return null
    val text = when (eta) {
        is Eta.Seconds -> stringResource(R.string.st_Update_EtaSeconds, eta.seconds)
        is Eta.Minutes -> stringResource(
            R.string.st_Update_EtaMinutes,
            eta.minutes,
            eta.seconds,
        )
        is Eta.Hours -> stringResource(
            R.string.st_Update_EtaHours,
            eta.hours,
            eta.minutes,
        )
    }
    return stringResource(R.string.st_Update_TimeLeft, text)
}

// ── Discord ─────────────────────────────────────────────────────────────────

@Composable
private fun DiscordCard() {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brush.linearGradient(DiscordGradient))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.18f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_discord),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.st_Update_DiscordTitle),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    text = stringResource(R.string.st_Update_DiscordBody),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
        ToolzExpressiveButton(
            onClick = {
                runCatching { uriHandler.openUri(DevUiOverrider.TOOLZ_DISCORD_URL) }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = DiscordBlurpleDeep,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_discord),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.st_Update_DiscordAction),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── Build picker ────────────────────────────────────────────────────────────

private data class AbiOption(
    val value: String,
    val title: String,
    val description: String,
)

@Composable
private fun abiLabel(abi: String): String = when (abi) {
    "AUTO" -> stringResource(R.string.st_Update_AbiAutomatic)
    "arm64-v8a" -> stringResource(R.string.st_Update_AbiArm64)
    "armeabi-v7a" -> stringResource(R.string.st_Update_AbiArm32)
    "x86_64" -> stringResource(R.string.st_Update_AbiX86_64)
    "x86" -> stringResource(R.string.st_Update_AbiX86)
    else -> abi
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AbiPickerSheet(
    preferredAbi: String,
    deviceAbiLabel: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        AbiOption(
            value = "AUTO",
            title = stringResource(R.string.st_Update_AbiAutomatic),
            description = stringResource(R.string.st_Update_AbiAutomaticDesc, deviceAbiLabel),
        ),
        AbiOption(
            value = "arm64-v8a",
            title = stringResource(R.string.st_Update_AbiArm64),
            description = stringResource(R.string.st_Update_AbiArm64Desc),
        ),
        AbiOption(
            value = "armeabi-v7a",
            title = stringResource(R.string.st_Update_AbiArm32),
            description = stringResource(R.string.st_Update_AbiArm32Desc),
        ),
        AbiOption(
            value = "x86_64",
            title = stringResource(R.string.st_Update_AbiX86_64),
            description = stringResource(R.string.st_Update_AbiX86_64Desc),
        ),
        AbiOption(
            value = "x86",
            title = stringResource(R.string.st_Update_AbiX86),
            description = stringResource(R.string.st_Update_AbiX86Desc),
        ),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.st_Update_BuildTitle),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.st_Update_AbiIntro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option.value) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = preferredAbi == option.value,
                        onClick = { onSelect(option.value) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = option.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = option.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ── Help ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSheet(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = stringResource(R.string.st_Update_About),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.st_Update_HelpIntro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HelpSection(
                icon = Icons.Rounded.Security,
                title = stringResource(R.string.st_Update_HelpSecurityTitle),
                description = stringResource(R.string.st_Update_HelpSecurityBody),
            )
            HelpSection(
                icon = Icons.Rounded.Wifi,
                title = stringResource(R.string.st_Update_HelpWifiTitle),
                description = stringResource(R.string.st_Update_HelpWifiBody),
            )
            HelpSection(
                icon = Icons.Rounded.Refresh,
                title = stringResource(R.string.st_Update_HelpResumeTitle),
                description = stringResource(R.string.st_Update_HelpResumeBody),
            )
            PrimaryAction(
                text = stringResource(R.string.st_Update_SeeAllReleases),
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                onClick = { uriHandler.openUri(RELEASES_URL) },
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.st_Update_Close))
            }
        }
    }
}

@Composable
private fun HelpSection(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Shared building blocks ──────────────────────────────────────────────────

/**
 * Softens the top and bottom of a scroll viewport only while there is content
 * hidden in that direction. The mask fades to the live app background instead
 * of painting a solid scrim over it.
 */
@Composable
private fun Modifier.scrollEdgeFade(
    scrollState: ScrollState,
    topFade: Dp = 20.dp,
    bottomFade: Dp = 28.dp,
): Modifier {
    val topAlpha by animateFloatAsState(
        targetValue = if (scrollState.canScrollBackward) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "scroll_fade_top",
    )
    val bottomAlpha by animateFloatAsState(
        targetValue = if (scrollState.canScrollForward) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "scroll_fade_bottom",
    )
    val density = LocalDensity.current
    val topPx = with(density) { topFade.toPx() }
    val bottomPx = with(density) { bottomFade.toPx() }

    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if ((topAlpha <= 0f && bottomAlpha <= 0f) || size.height <= 0f) {
                return@drawWithContent
            }
            val topStop = (topPx / size.height).coerceIn(0f, 0.5f)
            val bottomStop = (1f - bottomPx / size.height).coerceIn(0.5f, 1f)
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 1f - topAlpha),
                        topStop to Color.White,
                        bottomStop to Color.White,
                        1f to Color.White.copy(alpha = 1f - bottomAlpha),
                    ),
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}

@Composable
private fun StateCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun PrimaryAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ToolzExpressiveButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = ButtonShape,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(
        shape = ChipShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ── Previews ────────────────────────────────────────────────────────────────

private val previewUpdate = PendingUpdate(
    version = "1.1.8",
    versionCode = 19,
    changelog = "### Highlights\n" +
        "- Faster update downloads with resume support.\n" +
        "- Clearer install flow with a verification step.\n" +
        "- Bug fixes and polish.",
    downloadUrl = "https://example.com/toolz.apk",
    sizeBytes = 42_000_000L,
    sha256 = null,
    isCritical = false,
    abi = "arm64-v8a",
    source = "manifest",
)

@Preview(showBackground = true)
@Composable
private fun UpdateAvailablePreview() {
    ToolzTheme {
        UpdateScreenContent(
            onBack = {},
            currentVersionName = "1.1.7",
            currentVersionCode = 18,
            managerState = UpdateManagerState.Available(previewUpdate),
            preferredAbi = "AUTO",
            autoUpdateEnabled = true,
            lastChecked = System.currentTimeMillis() - 3 * 60 * 60 * 1000L,
            onCheck = {}, onDownload = {}, onCancelDownload = {}, onInstall = {},
            onRetry = {}, onSkip = {}, onDismissInstalled = {},
            onSetPreferredAbi = {}, onSetAutoUpdate = {},
            getDeviceAbi = { "arm64-v8a" },
            onOpenInstallSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UpdateReadyPreview() {
    ToolzTheme {
        UpdateScreenContent(
            onBack = {},
            currentVersionName = "1.1.7",
            currentVersionCode = 18,
            managerState = UpdateManagerState.ReadyToInstall(
                update = previewUpdate,
                apkFile = java.io.File("/tmp/toolz.apk"),
                needsPermission = false,
            ),
            preferredAbi = "AUTO",
            autoUpdateEnabled = false,
            lastChecked = 0L,
            onCheck = {}, onDownload = {}, onCancelDownload = {}, onInstall = {},
            onRetry = {}, onSkip = {}, onDismissInstalled = {},
            onSetPreferredAbi = {}, onSetAutoUpdate = {},
            getDeviceAbi = { "arm64-v8a" },
            onOpenInstallSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UpdateDownloadingPreview() {
    ToolzTheme {
        UpdateScreenContent(
            onBack = {},
            currentVersionName = "1.1.7",
            currentVersionCode = 18,
            managerState = UpdateManagerState.Downloading(
                update = previewUpdate,
                bytesDownloaded = 18_000_000L,
                totalBytes = 42_000_000L,
                speedBps = 2_400_000L,
                progress = 42.9f,
            ),
            preferredAbi = "AUTO",
            autoUpdateEnabled = true,
            lastChecked = 0L,
            onCheck = {}, onDownload = {}, onCancelDownload = {}, onInstall = {},
            onRetry = {}, onSkip = {}, onDismissInstalled = {},
            onSetPreferredAbi = {}, onSetAutoUpdate = {},
            getDeviceAbi = { "arm64-v8a" },
            onOpenInstallSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UpdateErrorPreview() {
    ToolzTheme {
        UpdateScreenContent(
            onBack = {},
            currentVersionName = "1.1.7",
            currentVersionCode = 18,
            managerState = UpdateManagerState.Error(
                error = UpdateError.Offline,
                retryDownload = true,
            ),
            preferredAbi = "AUTO",
            autoUpdateEnabled = false,
            lastChecked = System.currentTimeMillis() - 20 * 60 * 1000L,
            onCheck = {}, onDownload = {}, onCancelDownload = {}, onInstall = {},
            onRetry = {}, onSkip = {}, onDismissInstalled = {},
            onSetPreferredAbi = {}, onSetAutoUpdate = {},
            getDeviceAbi = { "arm64-v8a" },
            onOpenInstallSettings = {},
        )
    }
}
