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

package com.frerox.toolz.ui.screens.ai

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import com.frerox.toolz.data.ai.*
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.components.AiDesign
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.launch

// Design tokens removed - using com.frerox.toolz.ui.components.AiDesign

// ─────────────────────────────────────────────────────────────────────────────
// Ambient background — soft primary/secondary radial glow
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExpressiveBackground(performanceMode: Boolean) {
    if (performanceMode) {
        Box(Modifier.fillMaxSize().background(AiDesign.surfaceColor()))
        return
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    MaterialTheme.colorScheme.surface,
                )
            )
        )
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "bg")
        val shift by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing)), "bg_shift")
        Box(
            Modifier
                .fillMaxSize()
                .alpha(0.22f)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), Color.Transparent),
                        radius = 900f,
                    )
                )
                .graphicsLayer { translationX = shift * 120f; translationY = -shift * 60f },
        )
        Box(
            Modifier
                .fillMaxSize()
                .alpha(0.15f)
                .blur(100.dp)
                .align(Alignment.BottomEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f), Color.Transparent),
                        radius = 700f,
                    )
                )
                .graphicsLayer { translationX = -shift * 80f },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Root screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiAssistantScreen(
    viewModel: AiAssistantViewModel = hiltViewModel(),
    onNavigateToBrowser: (String) -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    val uiState         by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    val isOnline        by viewModel.isOnline.collectAsStateWithLifecycle()
    val listState       = rememberLazyListState()
    val scope           = rememberCoroutineScope()
    val context         = LocalContext.current
    val vibration       = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current
    val drawerState     = rememberDrawerState(DrawerValue.Closed)

    var inputText                  by remember { mutableStateOf("") }
    var showSettings               by remember { mutableStateOf(false) }
    var showSummary                by remember { mutableStateOf(false) }
    var showQuotaDialog            by remember { mutableStateOf(false) }
    var selectedMessageForActions  by remember { mutableStateOf<AiMessage?>(null) }
    var selectedMessageForSources  by remember { mutableStateOf<AiMessage?>(null) }

    val openSettings = {
        if (onNavigateToSettings != null) {
            onNavigateToSettings()
        } else {
            showSettings = true
        }
    }

    val isStarted = uiState.messages.isNotEmpty() || uiState.isLoading || uiState.streamingText.isNotEmpty()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { s ->
                viewModel.onImageSelected(android.graphics.BitmapFactory.decodeStream(s))
            }
        }
    }

    LaunchedEffect(uiState.streamingText) {
        // Follow the stream at the REAL bottom (last item = spacer/error bubble),
        // not messages.size-1 which lands at the start of the last AI bubble.
        if (uiState.streamingText.isNotEmpty()) {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) {
                try { listState.scrollToItem(total - 1) } catch (_: Exception) {}
            }
        }
    }
    // New messages (user send / AI finish) also pin to the real bottom.
    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        if (uiState.messages.isNotEmpty()) {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                // Auto-follow only when already near the bottom so reading
                // history mid-chat doesn't yank the list on every new chunk.
                if (lastVisible >= total - 3 || !listState.canScrollForward) {
                    try { listState.scrollToItem(total - 1) } catch (_: Exception) {}
                }
            }
        }
    }
    LaunchedEffect(uiState.quotaExceeded) { if (uiState.quotaExceeded) showQuotaDialog = true }

    // ── Full-screen Settings View (replaces pop up dialog) ───────────────────
    if (showSettings) {
        AiSettingsScreen(
            viewModel = viewModel,
            onNavigateToBrowser = onNavigateToBrowser,
            onBack = {
                showSettings = false
                viewModel.cancelConfigSwitch()
                viewModel.discardSettingsDraft()
            }
        )
        return
    }

    if (showQuotaDialog) ModernAiDialog(
        title = stringResource(R.string.st_AiAssistantScreen_8f1a), icon = Icons.Rounded.LockClock,
        iconColor = MaterialTheme.colorScheme.error,
        description = "${uiState.activeProvider} has reached its limit.",
        supportingText = "Switch to ${uiState.suggestedProvider} or use your own API key.",
        primaryButtonText = "SWITCH TO ${uiState.suggestedProvider?.uppercase() ?: "OTHER"}",
        onPrimaryClick = { uiState.suggestedProvider?.let { viewModel.switchProvider(it) }; showQuotaDialog = false },
        onDismiss = { showQuotaDialog = false },
    )

    if (showSummary) ChatSummarySheet(
        summary = uiState.chatSummary, isSummarizing = uiState.isSummarizing,
        onDismiss = { showSummary = false; viewModel.clearChatSummary() },
        onRefresh = viewModel::summarizeChat,
    )

    if (selectedMessageForActions != null) MessageActionsSheet(
        message = selectedMessageForActions!!,
        onDismiss = { selectedMessageForActions = null },
        onRegenerate = { viewModel.regenerateMessage(it); selectedMessageForActions = null },
        onShowSources = { selectedMessageForSources = it; selectedMessageForActions = null },
    )

    if (selectedMessageForSources != null) MessageSourcesSheet(
        message = selectedMessageForSources!!,
        onDismiss = { selectedMessageForSources = null },
        onLinkClick = onNavigateToBrowser,
        onDeepDive = { viewModel.performDeepDive(it) },
    )

    if (settingsUiState.showGroqKeyMissingDialog) GroqKeyRequiredDialog(
        onDismiss = viewModel::dismissGroqDialog,
        onSave = viewModel::saveGroqKey,
        onGetLink = { onNavigateToBrowser("https://console.groq.com/keys") },
    )

    // Unsaved-switch confirmation: Save was tapped with an active chat, so the
    // new provider/model waits here instead of silently discarding (or worse,
    // silently applying mid-chat). Either choice also closes Settings.
    if (uiState.pendingConfig != null) ModernAiDialog(
        title = "Apply new settings?",
        icon = Icons.Rounded.Tune,
        iconColor = MaterialTheme.colorScheme.primary,
        description = "Switch to ${uiState.pendingConfig!!.provider} • ${uiState.pendingConfig!!.model}?",
        supportingText = "The current chat keeps its original settings. A fresh chat starts with the new ones — stored keys are never deleted.",
        primaryButtonText = "APPLY & NEW CHAT",
        onPrimaryClick = { viewModel.confirmConfigSwitch(); showSettings = false },
        secondaryButtonText = "KEEP EDITING",
        onSecondaryClick = { viewModel.cancelConfigSwitch() },
        onDismiss = { viewModel.cancelConfigSwitch() },
    )

    // Missing-key warning: the chosen provider has no key set, so chats on
    // it would fail. User can go back and paste one, or save anyway.
    if (settingsUiState.showNoKeyWarningFor != null) ModernAiDialog(
        title = "No API key",
        icon = Icons.Rounded.VpnKey,
        iconColor = MaterialTheme.colorScheme.error,
        description = "No API key set for ${settingsUiState.showNoKeyWarningFor}.",
        supportingText = "Chats on this provider will fail until you add one. Paste a key in the field above, or save anyway and add it later.",
        primaryButtonText = "SAVE ANYWAY",
        onPrimaryClick = { viewModel.confirmSaveWithoutKey() },
        secondaryButtonText = "GO BACK",
        onSecondaryClick = { viewModel.dismissNoKeyWarning() },
        onDismiss = { viewModel.dismissNoKeyWarning() },
    )

    // ── Main layout ───────────────────────────────────────────────────────────
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = AiDesign.surfaceColor(),
                drawerTonalElevation = 0.dp,
                modifier = Modifier.width(320.dp),
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
            ) {
                AiHistoryDrawer(
                    chats = uiState.chats,
                    currentChatId = uiState.currentChatId,
                    onChatSelect = { vibration?.vibrateClick(); viewModel.loadChat(it); scope.launch { drawerState.close() } },
                    onNewChat    = { vibration?.vibrateClick(); viewModel.createNewChat(); scope.launch { drawerState.close() } },
                    onDeleteChat = { vibration?.vibrateLongClick(); viewModel.deleteChat(it) },
                )
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.imePadding(),
            topBar = {
                AiTopBar(
                    settingsUiState = settingsUiState,
                    uiState = uiState,
                    performanceMode = performanceMode,
                    onBack        = { vibration?.vibrateClick(); onBack() },
                    onNewChat     = { vibration?.vibrateClick(); viewModel.createNewChat() },
                    onSettings    = { vibration?.vibrateClick(); openSettings() },
                    onHistory     = { vibration?.vibrateClick(); scope.launch { drawerState.open() } },
                    onConfigSelect = { vibration?.vibrateClick(); viewModel.onConfigRequest(it) },
                    onSummarize   = {
                        vibration?.vibrateTick(); showSummary = true
                        if (uiState.chatSummary == null && !uiState.isSummarizing) viewModel.summarizeChat()
                    },
                    onRefreshTitle = { vibration?.vibrateTick(); viewModel.refreshChatTitle() },
                )
            },
            bottomBar = {
                // Online-only tool: no input while offline (gate below explains why).
                // Vision gate uses the *applied* provider/model (what sendMessage
                // will actually use), never the un-saved settings draft.
                if (isOnline) AiInputBar(
                    inputText      = inputText,
                    isLoading      = uiState.isLoading,
                    selectedImage  = uiState.selectedImage,
                    supportsVision = AiSettingsHelper.supportsVision(uiState.activeProvider, uiState.activeModel),
                    performanceMode = performanceMode,
                    onInputChange  = { inputText = it },
                    onSend = {
                        if (!uiState.hasApiKey && settingsUiState.apiKey.isBlank()) {
                            vibration?.vibrateError()
                            openSettings()
                        } else if (inputText.isNotBlank() || uiState.selectedImage != null) {
                            vibration?.vibrateClick(); viewModel.sendMessage(inputText); inputText = ""
                        }
                    },
                    onCancel       = viewModel::cancelRequest,
                    onAttach       = { imagePicker.launch("image/*") },
                    onRemoveImage  = { viewModel.onImageSelected(null) },
                    aiSearchEnabled = uiState.aiSearchEnabled,
                    aiSearchIconVisible = uiState.aiSearchIconVisible,
                    onToggleAiSearch = viewModel::toggleAiSearch,
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                ExpressiveBackground(performanceMode)
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (!uiState.hasApiKey && settingsUiState.apiKey.isBlank()) {
                        ApiKeyWarningBanner(
                            provider = uiState.activeProvider,
                            onConfigureClick = openSettings
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (!isOnline) {
                            // Assistant is online-only: chats, models and web
                            // search are all server-side. Auto-clears on reconnect.
                            OfflineGate(
                                performanceMode = performanceMode,
                                onRetry = viewModel::retryConnection,
                            )
                        } else AnimatedContent(
                            targetState = isStarted,
                            transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(400)) },
                            label = "chat_root",
                        ) { started ->
                            if (started) {
                                ChatMessageList(
                                    messages        = uiState.messages,
                                    streamingText   = uiState.streamingText,
                                    isLoading       = uiState.isLoading,
                                    error           = uiState.error,
                                    listState       = listState,
                                    currentConfig   = uiState.savedConfigs.find { it.provider == uiState.activeProvider && it.model == uiState.activeModel },
                                    performanceMode = performanceMode,
                                    onRegenerate    = { viewModel.regenerateMessage(it) },
                                    onLinkClick     = onNavigateToBrowser,
                                    onLongPress     = { selectedMessageForActions = it },
                                    onShowSources   = { selectedMessageForSources = it },
                                    onScrollBottom  = {
                                        scope.launch {
                                            // Real bottom = last LazyColumn item (spacer after
                                            // error/streaming bubbles), not messages.size-1 which
                                            // stops at the start of the last AI response.
                                            val total = listState.layoutInfo.totalItemsCount
                                            if (total > 0) {
                                                try { listState.animateScrollToItem(total - 1) }
                                                catch (_: Exception) {
                                                    try { listState.scrollToItem(total - 1) } catch (_: Exception) {}
                                                }
                                            }
                                        }
                                    },
                                    loadingPhaseText = uiState.loadingPhaseText,
                                    onDeepDive      = { viewModel.performDeepDive(it) },
                                    onDismissDeepDive = { viewModel.dismissDeepDive(it) },
                                    isCoachMode     = uiState.isCoachMode,
                                    onRetryError    = viewModel::retryLastMessage,
                                )
                            } else {
                                EmptyChatState(
                                    performanceMode  = performanceMode,
                                    onSuggestionClick = { inputText = it },
                                    suggestedPrompts  = uiState.suggestedPrompts,
                                    isGeneratingPrompts = uiState.isGeneratingPrompts,
                                    onRefresh   = viewModel::refreshPrompts,
                                    onNeverShow = viewModel::neverShowPrompt,
                                    onEdit      = viewModel::editPrompt,
                                    onReset     = viewModel::resetPrompts,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar — M3 Expressive
// Provider-colored pill in title, SquircleShape action buttons
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AiTopBar(
    settingsUiState: AiSettingsUiState,
    uiState: AiAssistantUiState,
    performanceMode: Boolean,
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onConfigSelect: (AiConfig) -> Unit,
    onSummarize: () -> Unit,
    onRefreshTitle: () -> Unit,
) {
    // Tag always reflects the *applied* provider (what inference actually uses),
    // never the un-saved draft inside the settings dialog.
    val appliedProvider = uiState.activeProvider
    val providerColor = AiDesign.providerColor(appliedProvider)
        ?: MaterialTheme.colorScheme.primary

    val titleColor by animateColorAsState(providerColor, tween(500), label = "titleColor")

    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = AiDesign.surfaceColor().copy(alpha = 0.92f),
        ),
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.combinedClickable(
                    onClick = onRefreshTitle,
                    onLongClick = {},
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ),
            ) {
                // Animated provider chip (applied provider only)
                if (uiState.isCoachMode) {
                    Surface(
                        shape = SmallExpressiveShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                stringResource(R.string.st_AiAssistantScreen_3d5b),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.8.sp,
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = SmallExpressiveShape,
                        color = titleColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, titleColor.copy(alpha = 0.22f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                getIconForConfig("AUTO", appliedProvider),
                                null, Modifier.size(12.dp), tint = titleColor,
                            )
                            Text(
                                appliedProvider.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = titleColor,
                                letterSpacing = 0.8.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                // Chat title — same marquee + fading-edges pattern as the
                // music player's now-playing title: long titles auto-scroll
                // horizontally instead of collapsing to "...".
                val chatTitle = uiState.chats.find { it.id == uiState.currentChatId }?.title
                    ?: stringResource(R.string.st_AiAssistantScreen_9e2c)
                if (!performanceMode && chatTitle.length > 18) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 170.dp)
                            .horizontalFadingEdges(left = 12.dp, right = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = chatTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = AiDesign.textColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                velocity = 30.dp
                            )
                        )
                    }
                } else {
                    Text(
                        text = chatTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = AiDesign.textColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 170.dp),
                    )
                }
                AnimatedVisibility(visible = uiState.isGeneratingTitle) {
                    ToolzWavyLinearProgressIndicator(
                        modifier = Modifier.width(44.dp).height(2.dp).padding(top = 3.dp),
                        color = providerColor,
                        trackColor = Color.Transparent,
                    )
                }
            }
        },
        navigationIcon = {
            ToolzExpressiveIconButton(
                onClick = onBack,
                modifier = Modifier.padding(start = 8.dp).size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                shape = MediumExpressiveShape,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, modifier = Modifier.size(20.dp))
            }
        },
        actions = {
            ToolzExpressiveIconButton(
                onClick = onSummarize,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                shape = MediumExpressiveShape,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(20.dp), tint = titleColor)
            }
            ToolzExpressiveIconButton(
                onClick = onNewChat, modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = MediumExpressiveShape,
            ) { Icon(Icons.Rounded.Add, null, Modifier.size(22.dp)) }
            ToolzExpressiveIconButton(
                onClick = onHistory, modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = MediumExpressiveShape,
            ) { Icon(Icons.Rounded.History, null, Modifier.size(20.dp)) }
            ToolzExpressiveIconButton(
                onClick = onSettings,
                modifier = Modifier.padding(end = 8.dp).size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = MediumExpressiveShape,
            ) { Icon(Icons.Rounded.Tune, null, Modifier.size(20.dp)) }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Input Bar — M3 Expressive
// BouncyShape container, sweep-gradient border when loading,
// spring-animated send button, character counter, web search toggle
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AiInputBar(
    inputText: String,
    isLoading: Boolean,
    selectedImage: Bitmap?,
    supportsVision: Boolean,
    performanceMode: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onAttach: () -> Unit,
    onRemoveImage: () -> Unit,
    aiSearchEnabled: Boolean,
    aiSearchIconVisible: Boolean,
    onToggleAiSearch: () -> Unit,
) {
    SharedAiInputBar(
        inputText = inputText,
        isLoading = isLoading,
        selectedImage = selectedImage,
        supportsVision = supportsVision,
        performanceMode = performanceMode,
        onInputChange = onInputChange,
        onSend = onSend,
        onCancel = onCancel,
        onAttach = onAttach,
        onRemoveImage = onRemoveImage,
        aiSearchEnabled = aiSearchEnabled,
        aiSearchIconVisible = aiSearchIconVisible,
        onToggleAiSearch = onToggleAiSearch
    )
}

// Private components removed - using SharedChatBubble from com.frerox.toolz.ui.components

// ─────────────────────────────────────────────────────────────────────────────
// Chat Message List
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ChatMessageList(
    messages: List<AiMessage>,
    streamingText: String,
    isLoading: Boolean,
    error: String?,
    listState: LazyListState,
    currentConfig: AiConfig?,
    performanceMode: Boolean,
    onRegenerate: (Int) -> Unit,
    onLinkClick: (String) -> Unit,
    onLongPress: (AiMessage) -> Unit,
    onShowSources: (AiMessage) -> Unit,
    onScrollBottom: () -> Unit,
    loadingPhaseText: String?,
    onDeepDive: (AiMessage) -> Unit,
    onDismissDeepDive: (AiMessage) -> Unit,
    isCoachMode: Boolean = false,
    onRetryError: (() -> Unit)? = null,
) {
    val isAtBottom by remember { derivedStateOf { !listState.canScrollForward } }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val stops = floatArrayOf(0f, 0.04f, 0.96f, 1f)
                val colors = listOf(Color.Transparent, Color.Black, Color.Black, Color.Transparent)
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = stops.zip(colors).toTypedArray(),
                        startY = 0f, endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 20.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                SharedChatBubble(
                    message = msg, currentConfig = currentConfig, performanceMode = performanceMode,
                    isCoach = isCoachMode,
                    onRegenerate = onRegenerate, onLinkClick = onLinkClick, onLongPress = onLongPress,
                    onShowSources = onShowSources, onDeepDive = onDeepDive, onDismissDeepDive = onDismissDeepDive,
                )
            }
            if (isLoading || streamingText.isNotEmpty()) {
                item {
                    ActiveAiBubble(
                        isLoading = isLoading, loadingPhaseText = loadingPhaseText ?: "",
                        streamingText = streamingText, currentConfig = currentConfig,
                        performanceMode = performanceMode, onLinkClick = onLinkClick,
                    )
                }
            }
            if (error != null) item { ErrorMessage(error, onRetry = onRetryError) }
            item { Spacer(Modifier.height(100.dp)) }
        }

        // Scroll-to-bottom FAB
        AnimatedVisibility(
            visible = !isAtBottom && messages.isNotEmpty(),
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit  = scaleOut(tween(200)) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Surface(
                onClick = onScrollBottom,
                modifier = Modifier.size(40.dp),
                shape = MediumExpressiveShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 8.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chat Bubble — M3 Expressive
// AI: BouncyShape with glass surface + sources pill
// User: asymmetric SquircleShape with primary fill
// Both: spring-physics entrance, bouncyClick long-press, quick reaction row
// ─────────────────────────────────────────────────────────────────────────────

// Private components removed - using com.frerox.toolz.ui.components.SharedChatBubble

// ─────────────────────────────────────────────────────────────────────────────
// Active AI Bubble (loading / streaming)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActiveAiBubble(
    isLoading: Boolean,
    loadingPhaseText: String,
    streamingText: String,
    currentConfig: AiConfig?,
    performanceMode: Boolean,
    onLinkClick: (String) -> Unit,
) {
    val isTypingOnly = isLoading && streamingText.isEmpty()

    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AiAvatar(currentConfig, 32.dp, performanceMode = performanceMode)

        Surface(
            shape  = RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
            color  = AiDesign.glassColor(),
            border = BorderStroke(1.dp, AiDesign.glassBorder()),
            modifier = Modifier.widthIn(max = 300.dp).animateContentSize(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)),
        ) {
            AnimatedContent(
                targetState = isTypingOnly,
                transitionSpec = { (fadeIn(tween(350)) + scaleIn(initialScale = 0.85f, animationSpec = tween(350, easing = EaseOutBack))) togetherWith fadeOut(tween(180)) },
                label = "bubbleExpansion",
            ) { typing ->
                if (typing) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ExpressiveTypingDots(color = MaterialTheme.colorScheme.primary)
                        AnimatedContent(
                            targetState = loadingPhaseText,
                            transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                            label = "phaseText",
                        ) { text ->
                            Text(text, style = MaterialTheme.typography.bodyMedium, color = AiDesign.textColor(0.65f), fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        parseMarkdownToSegments(streamingText).forEach { seg ->
                            MarkdownSegment(
                                seg = seg, baseFontSize = 15.sp,
                                modifier = Modifier.padding(vertical = 3.dp).animateContentSize(),
                                textColor = AiDesign.textColor(), onLinkClick = onLinkClick,
                            )
                        }
                        if (isLoading) {
                            // Blinking cursor (new feature)
                            val inf = rememberInfiniteTransition(label = "cursor")
                            val cursorAlpha by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse), "cursor")
                            Box(
                                Modifier
                                    .padding(top = 4.dp)
                                    .size(width = 2.dp, height = 16.dp)
                                    .alpha(cursorAlpha)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Typing Dots — spring-staggered M3 Expressive bounce
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExpressiveTypingDots(color: Color) {
    val inf = rememberInfiniteTransition(label = "dots")
    @Composable
    fun dot(delay: Int): Float {
        val scale by inf.animateFloat(
            0.6f, 1.4f,
            infiniteRepeatable(
                animation    = keyframes { durationMillis = 900; 1.4f at 300 using FastOutSlowInEasing; 0.6f at 600 },
                initialStartOffset = StartOffset(delay),
            ),
            "dot$delay",
        )
        return scale
    }
    val s1 = dot(0); val s2 = dot(150); val s3 = dot(300)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(s1, s2, s3).forEach { s ->
            Box(Modifier.size(8.dp).scale(s).background(color, CircleShape))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Avatar — SquircleShape instead of Circle for M3E identity
// ─────────────────────────────────────────────────────────────────────────────

// Removed - using com.frerox.toolz.ui.components.AiAvatar

// ─────────────────────────────────────────────────────────────────────────────
// Sources Pill
// ─────────────────────────────────────────────────────────────────────────────

// Private components removed - using com.frerox.toolz.ui.components.SharedChatBubble

// Private components removed - using com.frerox.toolz.ui.components.SharedChatBubble

// ─────────────────────────────────────────────────────────────────────────────
// Empty Chat State — M3 Expressive welcome screen
// Pulsing SquircleShape icon, StaggeredEntrance prompt cards, ExpressiveCard
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyChatState(
    performanceMode: Boolean,
    onSuggestionClick: (String) -> Unit,
    suggestedPrompts: List<String>,
    isGeneratingPrompts: Boolean,
    onRefresh: () -> Unit,
    onNeverShow: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    onReset: () -> Unit,
) {
    var showPromptActions by remember { mutableStateOf<String?>(null) }
    val infiniteTransition = rememberInfiniteTransition(label = "emptyPulse")
    val pulseScale by infiniteTransition.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(0.08f, 0.22f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), "pAlpha")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Pulsing icon
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(bottom = 28.dp)) {
            if (!performanceMode) {
                Box(
                    Modifier.size(120.dp)
                        .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale; alpha = pulseAlpha }
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
            Surface(
                modifier = Modifier.size(80.dp), shape = SquircleShape,
                color    = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = if (performanceMode) 0.dp else 18.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Text(stringResource(R.string.st_AiAssistantScreen_1a2b), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = AiDesign.textColor(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.st_AiAssistantScreen_7c4d), style = MaterialTheme.typography.bodyMedium, color = AiDesign.textColor(0.5f), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(28.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = SmallExpressiveShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), modifier = Modifier.size(22.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Lightbulb, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                Text(stringResource(R.string.st_AiAssistantScreen_5f6e), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
            }
            Row {
                IconButton(onClick = onReset, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp), tint = AiDesign.textColor(0.35f)) }
                AnimatedContent(isGeneratingPrompts, label = "refreshBtn") { gen ->
                    if (gen) IconButton({}, Modifier.size(36.dp), enabled = false) {
                        ToolzWavyCircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
                    }
                    else IconButton(onRefresh, Modifier.size(36.dp)) { Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp), tint = AiDesign.textColor(0.35f)) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            suggestedPrompts.forEachIndexed { i, prompt ->
                StaggeredEntrance(index = i) {
                    ExpressiveCard(
                        onClick = { onSuggestionClick(prompt) },
                        shape = LargeExpressiveShape,
                        containerColor = AiDesign.glassColor(),
                        elevation = 0.dp,
                        border = BorderStroke(1.dp, AiDesign.glassBorder()),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(prompt, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = AiDesign.textColor(0.82f), fontWeight = FontWeight.Medium, lineHeight = 22.sp)
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                onClick = { showPromptActions = prompt },
                                modifier = Modifier.size(28.dp),
                                shape = SmallExpressiveShape,
                                color = AiDesign.glassBorder().copy(alpha = 0.4f),
                            ) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MoreVert, null, Modifier.size(14.dp), tint = AiDesign.textColor(0.35f)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPromptActions != null) PromptActionSheet(
        prompt = showPromptActions!!,
        onDismiss = { showPromptActions = null },
        onNeverShow = { onNeverShow(it); showPromptActions = null },
        onEdit = { old, new -> onEdit(old, new); showPromptActions = null },
        onCopy = {},
        onRefresh = { onRefresh(); showPromptActions = null },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// History Drawer — M3 Expressive
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiHistoryDrawer(
    chats: List<AiChat>,
    currentChatId: Int?,
    onChatSelect: (Int) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (AiChat) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 20.dp)) {
            Surface(modifier = Modifier.size(32.dp), shape = SmallExpressiveShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.History, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
            }
            Text(stringResource(R.string.st_AiAssistantScreen_2b8a), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
        }

        // New chat button
        ToolzExpressiveButton(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = BouncyShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        ) {
            Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.st_AiAssistantScreen_4d9c), fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(ChatGroup.TODAY, ChatGroup.YESTERDAY, ChatGroup.THIS_WEEK, ChatGroup.OLDER).forEach { group ->
                val groupChats = chats.filter { it.chatGroup() == group }
                if (groupChats.isNotEmpty()) {
                    item {
                        Text(
                            group.name.replace("_", " "),
                            Modifier.padding(top = 16.dp, bottom = 6.dp, start = 4.dp),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black,
                            color = AiDesign.textColor(0.38f), letterSpacing = 1.sp,
                        )
                    }
                    items(groupChats, key = { it.id }) { chat ->
                        val isSelected = chat.id == currentChatId
                        Surface(
                            onClick = { onChatSelect(chat.id) },
                            shape   = MediumExpressiveShape,
                            color   = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent,
                            border  = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)) else null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    if (isSelected) Icons.Rounded.ChatBubble else Icons.Rounded.ChatBubbleOutline,
                                    null, Modifier.size(16.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else AiDesign.textColor(0.4f),
                                )
                                Text(
                                    chat.title, Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AiDesign.textColor() else AiDesign.textColor(0.75f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Surface(
                                    onClick = { onDeleteChat(chat) },
                                    modifier = Modifier.size(28.dp), shape = SmallExpressiveShape,
                                    color = Color.Transparent,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(14.dp), tint = AiDesign.textColor(0.28f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Action Row helper
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionRow(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MediumExpressiveShape, color = Color.Transparent, modifier = Modifier.bouncyClick(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(modifier = Modifier.size(40.dp), shape = SmallExpressiveShape, color = color.copy(alpha = 0.1f)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = color) }
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AiDesign.textColor())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Offline Gate — the assistant is online-only, so offline gets a full
// takeover (not a banner): no chat, no input. Clears itself on reconnect.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OfflineGate(
    performanceMode: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(80.dp), shape = SquircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = if (performanceMode) 0.dp else 18.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CloudOff, null, Modifier.size(38.dp), tint = AiDesign.textColor(0.55f))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "You're offline",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = AiDesign.textColor(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "AI Assistant needs a connection — chats, models and web search all run server-side.",
            style = MaterialTheme.typography.bodyMedium,
            color = AiDesign.textColor(0.55f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(24.dp))
        ToolzExpressiveButton(onClick = onRetry, shape = BouncyShape, modifier = Modifier.height(52.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Text("Try again", fontWeight = FontWeight.Black)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error Message
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ErrorMessage(error: String, onRetry: (() -> Unit)? = null) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), LargeExpressiveShape, MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                Text(error, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (onRetry != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = onRetry,
                        shape = MediumExpressiveShape,
                        color = MaterialTheme.colorScheme.error,
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(15.dp), Color.White)
                            Text("Retry", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    // Quick hint for 404s: jump to settings is faster than retrying blindly
                    if (error.contains("404", true) || error.contains("retired", true)) {
                        Text(
                            "Tip: Settings → model list shows live models. FREE-tagged ones cost \$0.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chat Summary Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatSummarySheet(summary: String?, isSummarizing: Boolean, onDismiss: () -> Unit, onRefresh: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor  = AiDesign.surfaceColor(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp, 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), CircleShape))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(modifier = Modifier.size(28.dp), shape = SmallExpressiveShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(14.dp), MaterialTheme.colorScheme.primary) }
                    }
                    Text(stringResource(R.string.st_AiAssistantScreen_w9x1), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                }
                IconButton(onRefresh, enabled = !isSummarizing) {
                    if (isSummarizing) ToolzWavyCircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
                    else Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp), tint = AiDesign.textColor(0.45f))
                }
            }
            HorizontalDivider(color = AiDesign.glassBorder())
            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = Triple(isSummarizing, summary != null, summary),
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "sumContent",
            ) { (summarizing, hasSummary, text) ->
                when {
                    summarizing && !hasSummary -> Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ToolzWavyCircularProgressIndicator(Modifier.size(44.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                        Text(stringResource(R.string.st_AiAssistantScreen_y1z2), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium, color = AiDesign.textColor(0.55f), fontWeight = FontWeight.Medium)
                    }
                    hasSummary -> Surface(Modifier.fillMaxWidth(), LargeExpressiveShape, MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, AiDesign.glassBorder())) {
                        Text(text ?: "", Modifier.padding(18.dp), style = MaterialTheme.typography.bodyLarge, color = AiDesign.textColor(), lineHeight = 26.sp, fontWeight = FontWeight.Medium)
                    }
                    else -> Text(stringResource(R.string.st_AiAssistantScreen_a3b4), color = AiDesign.textColor(0.38f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Message Actions Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MessageActionsSheet(message: AiMessage, onDismiss: () -> Unit, onRegenerate: (Int) -> Unit, onShowSources: ((AiMessage) -> Unit)? = null) {
    val clipboard = LocalClipboardManager.current
    val context   = LocalContext.current
    // Second entry point to the sources sheet (long-press menu), so sources
    // stay reachable even if the inline pill is off-screen or missed.
    val sourceCount = remember(message.searchSources) {
        if (message.searchSources.isNullOrBlank()) 0
        else runCatching {
            val moshi = Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
            val type  = Types.newParameterizedType(List::class.java, SearchResult::class.java)
            moshi.adapter<List<SearchResult>>(type).fromJson(message.searchSources)?.size ?: 0
        }.getOrDefault(0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp, 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), CircleShape))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp)) {
            // Snippet preview
            Surface(Modifier.fillMaxWidth().padding(bottom = 18.dp), LargeExpressiveShape, MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, AiDesign.glassBorder())) {
                Text(message.text.take(120) + if (message.text.length > 120) "…" else "", Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = AiDesign.textColor(0.6f), lineHeight = 18.sp)
            }
            // Provenance: exact model that replied + wall-clock inference time.
            // (Null on user messages and pre-v58 rows — row hidden then.)
            if (!message.isUser && (message.modelName != null || message.responseTimeMs != null)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 18.dp, start = 4.dp),
                ) {
                    Icon(Icons.Rounded.SmartToy, null, Modifier.size(15.dp), tint = AiDesign.textColor(0.5f))
                    Text(
                        message.modelName ?: "Unknown model",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AiDesign.textColor(0.7f),
                    )
                    message.responseTimeMs?.let { ms ->
                        Text(
                            "• " + if (ms < 1000) "${ms}ms" else "%.1fs".format(ms / 1000f),
                            style = MaterialTheme.typography.labelMedium,
                            color = AiDesign.textColor(0.45f),
                        )
                    }
                }
            }
            Text(stringResource(R.string.st_AiAssistantScreen_m3n4_v2), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))
            HorizontalDivider(Modifier.padding(bottom = 12.dp), color = AiDesign.glassBorder())

            ActionRow(Icons.Rounded.ContentCopy, stringResource(R.string.st_AiAssistantScreen_6a1b), MaterialTheme.colorScheme.onSurface) {
                clipboard.setText(AnnotatedString(message.text)); onDismiss()
            }
            if (!message.isUser) ActionRow(Icons.Rounded.Refresh, stringResource(R.string.st_AiAssistantScreen_1b2c), MaterialTheme.colorScheme.primary) {
                onRegenerate(message.id); onDismiss()
            }
            if (sourceCount > 0 && onShowSources != null) ActionRow(Icons.Rounded.Language, "View $sourceCount sources", MaterialTheme.colorScheme.tertiary) {
                onShowSources(message); onDismiss()
            }
            ActionRow(Icons.Rounded.Share, stringResource(R.string.st_AiAssistantScreen_3c4d), MaterialTheme.colorScheme.onSurface) {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message.text) }, context.getString(R.string.st_AiAssistantScreen_5d6e)))
                onDismiss()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Message Sources Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MessageSourcesSheet(message: AiMessage, onDismiss: () -> Unit, onLinkClick: (String) -> Unit, onDeepDive: (AiMessage) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val sources   = remember(message.searchSources) {
        if (message.searchSources.isNullOrBlank()) emptyList()
        else runCatching {
            val moshi = Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
            val type  = Types.newParameterizedType(List::class.java, SearchResult::class.java)
            moshi.adapter<List<SearchResult>>(type).fromJson(message.searchSources) ?: emptyList()
        }.getOrElse { emptyList() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp, 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), CircleShape))
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 36.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(modifier = Modifier.size(28.dp), shape = SmallExpressiveShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Language, null, Modifier.size(14.dp), MaterialTheme.colorScheme.primary) }
                    }
                    Text(stringResource(R.string.st_AiAssistantScreen_7e8f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                }
                if (message.canDeepDive && message.deepDiveState == DeepDiveState.PENDING) {
                    ToolzExpressiveButton(
                        onClick = { onDeepDive(message); onDismiss() },
                        shape = MediumExpressiveShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.st_AiAssistantScreen_9f0a), fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (sources.isEmpty()) {
                    item {
                        Text(
                            "No sources attached to this message. Sources appear on replies written with web search on.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AiDesign.textColor(0.55f),
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                itemsIndexed(sources, key = { i, s -> "${s.url}_$i" }) { _, source ->
                    Surface(shape = LargeExpressiveShape, color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, AiDesign.glassBorder())) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SourceFavicon(source.url, 22.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(source.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                    Text(source.displayUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }
                            }
                            if (source.snippet.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(source.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp, color = AiDesign.textColor(0.7f))
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ToolzExpressiveButton(
                                    onClick = { onLinkClick(source.url) }, modifier = Modifier.weight(1f).height(40.dp),
                                    shape = MediumExpressiveShape, contentPadding = PaddingValues(0.dp),
                                ) {
                                    Icon(Icons.Rounded.Public, null, Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.st_AiAssistantScreen_a1b2), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                                }
                                ToolzOutlinedExpressiveButton(
                                    onClick = { clipboard.setText(AnnotatedString(source.url)) },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = MediumExpressiveShape, contentPadding = PaddingValues(0.dp),
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.st_AiAssistantScreen_c3d4), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Prompt Action Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PromptActionSheet(prompt: String, onDismiss: () -> Unit, onNeverShow: (String) -> Unit, onEdit: (String, String) -> Unit, onCopy: (String) -> Unit, onRefresh: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    var isEditing  by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf(prompt) }
    val clipboard  = LocalClipboardManager.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = AiDesign.surfaceColor(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.padding(bottom = 36.dp).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedContent(isEditing, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "promptEdit") { editing ->
                if (editing) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(editedText, { editedText = it }, Modifier.fillMaxWidth(), shape = MediumExpressiveShape, label = { Text(stringResource(R.string.st_AiAssistantScreen_e5f6)) }, colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = AiDesign.glassColor(), unfocusedContainerColor = AiDesign.glassColor()))
                    ToolzExpressiveButton({ onEdit(prompt, editedText) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.st_AiAssistantScreen_g7h8), fontWeight = FontWeight.Black) }
                }
                else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), LargeExpressiveShape, AiDesign.glassColor(), border = BorderStroke(1.dp, AiDesign.glassBorder())) {
                        Text(prompt, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = AiDesign.textColor(0.8f), fontWeight = FontWeight.Medium)
                    }
                    ActionRow(Icons.Rounded.ContentCopy, stringResource(R.string.st_AiAssistantScreen_i9j0), MaterialTheme.colorScheme.primary) { clipboard.setText(AnnotatedString(prompt)); onDismiss() }
                    ActionRow(Icons.Rounded.Edit, stringResource(R.string.st_AiAssistantScreen_k1l2), MaterialTheme.colorScheme.secondary) { isEditing = true }
                    ActionRow(Icons.Rounded.Refresh, stringResource(R.string.st_AiAssistantScreen_m3n4), MaterialTheme.colorScheme.tertiary) { onRefresh() }
                    ActionRow(Icons.Rounded.VisibilityOff, stringResource(R.string.st_AiAssistantScreen_o5p6), MaterialTheme.colorScheme.error) { onNeverShow(prompt) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Modern AI Dialog (Quota, errors etc.)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModernAiDialog(title: String, icon: ImageVector, iconColor: Color, description: String, supportingText: String, primaryButtonText: String, onPrimaryClick: () -> Unit, secondaryButtonText: String? = null, onSecondaryClick: (() -> Unit)? = null, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(24.dp).fillMaxWidth(), SquircleShape, AiDesign.surfaceColor(), border = BorderStroke(1.dp, AiDesign.glassBorder()), shadowElevation = 24.dp) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(modifier = Modifier.size(80.dp), shape = SquircleShape, color = iconColor.copy(alpha = 0.12f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(38.dp), tint = iconColor) }
                }
                Spacer(Modifier.height(24.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = iconColor, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                Text(description, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(supportingText, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = AiDesign.textColor(0.58f), lineHeight = 22.sp)
                Spacer(Modifier.height(32.dp))
                ToolzExpressiveButton(onPrimaryClick, Modifier.fillMaxWidth().height(56.dp), shape = BouncyShape, colors = ButtonDefaults.buttonColors(containerColor = iconColor)) { Text(primaryButtonText, fontWeight = FontWeight.Black) }
                if (secondaryButtonText != null && onSecondaryClick != null) {
                    Spacer(Modifier.height(12.dp))
                    ToolzOutlinedExpressiveButton(onSecondaryClick, Modifier.fillMaxWidth().height(56.dp), shape = BouncyShape) { Text(secondaryButtonText, fontWeight = FontWeight.Bold) }
                }
                TextButton(onDismiss, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.st_AiAssistantScreen_q7r8), style = MaterialTheme.typography.labelLarge, color = AiDesign.textColor(0.35f)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Groq Key Required Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroqKeyRequiredDialog(onDismiss: () -> Unit, onSave: (String) -> Unit, onGetLink: () -> Unit) {
    var key by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(24.dp).fillMaxWidth(), SquircleShape, AiDesign.surfaceColor(), border = BorderStroke(1.dp, AiDesign.glassBorder()), shadowElevation = 24.dp) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(modifier = Modifier.size(76.dp), shape = SquircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.VpnKey, null, Modifier.size(36.dp), MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.st_AiAssistantScreen_s9t0), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.st_AiAssistantScreen_u1v2), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.st_AiAssistantScreen_w3x4), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = AiDesign.textColor(0.58f), lineHeight = 22.sp)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.st_AiAssistantScreen_y5z6)) }, placeholder = { Text(stringResource(R.string.st_AiAssistantScreen_a7b8)) }, singleLine = true, shape = MediumExpressiveShape, colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = AiDesign.glassColor(), unfocusedContainerColor = AiDesign.glassColor()))
                Spacer(Modifier.height(28.dp))
                ToolzExpressiveButton({ if (key.isNotBlank()) onSave(key) }, Modifier.fillMaxWidth().height(56.dp), shape = BouncyShape, enabled = key.isNotBlank()) { Text(stringResource(R.string.st_AiAssistantScreen_c9d0), fontWeight = FontWeight.Black) }
                Spacer(Modifier.height(10.dp))
                ToolzOutlinedExpressiveButton(onGetLink, Modifier.fillMaxWidth().height(56.dp), shape = BouncyShape) { Text(stringResource(R.string.st_AiAssistantScreen_e1f2), fontWeight = FontWeight.Bold) }
                TextButton(onDismiss, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.st_AiAssistantScreen_g3h4), style = MaterialTheme.typography.labelLarge, color = AiDesign.textColor(0.35f)) }
            }
        }
    }
}

@Composable
private fun ApiKeyWarningBanner(
    provider: String,
    onConfigureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onConfigureClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MediumExpressiveShape,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.VpnKeyOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "API Key Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "An API key is required to use $provider. Tap here to set up your API key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            FilledTonalButton(
                onClick = onConfigureClick,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                )
            ) {
                Text("Add Key", fontWeight = FontWeight.Bold)
            }
        }
    }
}