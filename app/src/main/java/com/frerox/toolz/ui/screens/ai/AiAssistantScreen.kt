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
    onBack: () -> Unit,
) {
    val uiState         by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
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

    val isStarted = uiState.messages.isNotEmpty() || uiState.isLoading || uiState.streamingText.isNotEmpty()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { s ->
                viewModel.onImageSelected(android.graphics.BitmapFactory.decodeStream(s))
            }
        }
    }
    val configIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.updateCustomIcon(it) }
    }

    LaunchedEffect(uiState.streamingText) {
        if (uiState.streamingText.isNotEmpty() && uiState.messages.isNotEmpty())
            listState.scrollToItem(uiState.messages.size - 1)
    }
    LaunchedEffect(uiState.quotaExceeded) { if (uiState.quotaExceeded) showQuotaDialog = true }

    // ── Overlays ──────────────────────────────────────────────────────────────
    if (showSettings) AiSettingsDialog(
        state = settingsUiState, savedConfigs = uiState.savedConfigs,
        onDismiss = { showSettings = false },
        onProviderChange = viewModel::updateProvider, onApiKeyChange = viewModel::updateApiKey,
        onModelChange = viewModel::updateModel, onIconChange = viewModel::updateIcon,
        onCustomIconClick = { configIconPicker.launch("image/*") },
        onSave = { viewModel.onSettingsSaveRequest(); showSettings = false },
        onSaveConfig = viewModel::saveConfig, onDeleteConfig = viewModel::deleteConfig,
        onEditConfig = viewModel::editConfig, onMoveConfig = viewModel::moveConfig,
        onTest = viewModel::testConnection, onRefresh = viewModel::refreshRemoteKeys,
        performanceMode = performanceMode,
        onToggleDynamicPrompts = viewModel::toggleDynamicPrompts,
        onPromptFormatChange = viewModel::updatePromptFormat,
        aiSearchIconVisible = uiState.aiSearchIconVisible,
        onSetAiSearchIconVisible = viewModel::setAiSearchIconVisible,
    )

    if (showQuotaDialog) ModernAiDialog(
        title = "QUOTA EXCEEDED", icon = Icons.Rounded.LockClock,
        iconColor = MaterialTheme.colorScheme.error,
        description = "${settingsUiState.provider} has reached its limit.",
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
                    onSettings    = { vibration?.vibrateClick(); showSettings = true },
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
                AiInputBar(
                    inputText      = inputText,
                    isLoading      = uiState.isLoading,
                    selectedImage  = uiState.selectedImage,
                    supportsVision = AiSettingsHelper.supportsVision(settingsUiState.provider, settingsUiState.selectedModel),
                    supportsFiles  = AiSettingsHelper.supportsFiles(settingsUiState.provider, settingsUiState.selectedModel),
                    performanceMode = performanceMode,
                    onInputChange  = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank() || uiState.selectedImage != null) {
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
                Box(Modifier.fillMaxSize().padding(padding)) {
                    AnimatedVisibility(
                        visible = uiState.keysUnavailable,
                        enter = expandVertically() + fadeIn(),
                        exit  = shrinkVertically() + fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter).zIndex(2f),
                    ) {
                        KeysUnavailableBanner(
                            isSyncing = uiState.isSyncingKeys,
                            onRetrySync = { vibration?.vibrateTick(); viewModel.retrySyncKeys() },
                        )
                    }

                    AnimatedContent(
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
                                currentConfig   = uiState.savedConfigs.find { it.provider == settingsUiState.provider && it.model == settingsUiState.selectedModel },
                                performanceMode = performanceMode,
                                onRegenerate    = { viewModel.regenerateMessage(it) },
                                onLinkClick     = onNavigateToBrowser,
                                onLongPress     = { selectedMessageForActions = it },
                                onShowSources   = { selectedMessageForSources = it },
                                onScrollBottom  = { scope.launch { listState.animateScrollToItem((uiState.messages.size - 1).coerceAtLeast(0)) } },
                                onRetrySync     = { vibration?.vibrateTick(); viewModel.retrySyncKeys() },
                                loadingPhaseText = uiState.loadingPhaseText,
                                onDeepDive      = { viewModel.performDeepDive(it) },
                                onDismissDeepDive = { viewModel.dismissDeepDive(it) },
                                isCoachMode     = uiState.isCoachMode
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
    val providerColor = AiDesign.providerColor(settingsUiState.provider)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Animated provider chip
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
                                    "AI FITNESS COACH",
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
                                    getIconForConfig(settingsUiState.selectedIcon, settingsUiState.provider),
                                    null, Modifier.size(12.dp), tint = titleColor,
                                )
                                Text(
                                    settingsUiState.provider.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = titleColor,
                                    letterSpacing = 0.8.sp,
                                )
                            }
                        }
                    }
                    Text(
                        text = uiState.chats.find { it.id == uiState.currentChatId }?.title ?: "AI Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = AiDesign.textColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
    supportsFiles: Boolean,
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
        supportsFiles = supportsFiles,
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
    onRetrySync: () -> Unit,
    loadingPhaseText: String?,
    onDeepDive: (AiMessage) -> Unit,
    onDismissDeepDive: (AiMessage) -> Unit,
    isCoachMode: Boolean = false,
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
            if (error != null) item { ErrorMessage(error, onRetrySync) }
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

        Text("How can I help you?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = AiDesign.textColor(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("Ask anything, analyze images, search the web", style = MaterialTheme.typography.bodyMedium, color = AiDesign.textColor(0.5f), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(40.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = SmallExpressiveShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), modifier = Modifier.size(22.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Lightbulb, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                Text("SUGGESTED", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
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
            Text("HISTORY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
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
            Text("New Conversation", fontWeight = FontWeight.Black)
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
// Keys Unavailable Banner
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KeysUnavailableBanner(isSyncing: Boolean, onRetrySync: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(12.dp), BouncyShape, MaterialTheme.colorScheme.errorContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(modifier = Modifier.size(36.dp), shape = SmallExpressiveShape, color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.VpnKeyOff, null, Modifier.size(18.dp), MaterialTheme.colorScheme.error) }
            }
            Column(Modifier.weight(1f)) {
                Text("API Keys Unavailable", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                Text("Shared keys could not be synced.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f))
            }
            IconButton(onClick = onRetrySync, enabled = !isSyncing) {
                if (isSyncing) ToolzWavyCircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.error, trackColor = Color.Transparent)
                else Icon(Icons.Rounded.Sync, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error Message
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ErrorMessage(error: String, onRetry: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), LargeExpressiveShape, MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            Text(error, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            ToolzExpressiveIconButton(onRetry, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)), shape = SmallExpressiveShape) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp), MaterialTheme.colorScheme.error)
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
                    Text("CHAT SUMMARY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
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
                        Text("Analyzing conversation…", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium, color = AiDesign.textColor(0.55f), fontWeight = FontWeight.Medium)
                    }
                    hasSummary -> Surface(Modifier.fillMaxWidth(), LargeExpressiveShape, MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, AiDesign.glassBorder())) {
                        Text(text ?: "", Modifier.padding(18.dp), style = MaterialTheme.typography.bodyLarge, color = AiDesign.textColor(), lineHeight = 26.sp, fontWeight = FontWeight.Medium)
                    }
                    else -> Text("No summary available.", color = AiDesign.textColor(0.38f), style = MaterialTheme.typography.bodyMedium)
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
fun MessageActionsSheet(message: AiMessage, onDismiss: () -> Unit, onRegenerate: (Int) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context   = LocalContext.current

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
            Text("MESSAGE ACTIONS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))
            HorizontalDivider(Modifier.padding(bottom = 12.dp), color = AiDesign.glassBorder())

            ActionRow(Icons.Rounded.ContentCopy, "Copy Text", MaterialTheme.colorScheme.onSurface) {
                clipboard.setText(AnnotatedString(message.text)); onDismiss()
            }
            if (!message.isUser) ActionRow(Icons.Rounded.Refresh, "Regenerate", MaterialTheme.colorScheme.primary) {
                onRegenerate(message.id); onDismiss()
            }
            ActionRow(Icons.Rounded.Share, "Share", MaterialTheme.colorScheme.onSurface) {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message.text) }, "Share via"))
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
                    Text("SOURCES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
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
                        Text("Deep Dive", fontSize = 13.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
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
                                    Text("Open", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                                }
                                ToolzOutlinedExpressiveButton(
                                    onClick = { clipboard.setText(AnnotatedString(source.url)) },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = MediumExpressiveShape, contentPadding = PaddingValues(0.dp),
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy URL", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
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
                    OutlinedTextField(editedText, { editedText = it }, Modifier.fillMaxWidth(), shape = MediumExpressiveShape, label = { Text("Edit prompt") }, colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = AiDesign.glassColor(), unfocusedContainerColor = AiDesign.glassColor()))
                    ToolzExpressiveButton({ onEdit(prompt, editedText) }, Modifier.fillMaxWidth()) { Text("Save Changes", fontWeight = FontWeight.Black) }
                }
                else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), LargeExpressiveShape, AiDesign.glassColor(), border = BorderStroke(1.dp, AiDesign.glassBorder())) {
                        Text(prompt, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = AiDesign.textColor(0.8f), fontWeight = FontWeight.Medium)
                    }
                    ActionRow(Icons.Rounded.ContentCopy, "Copy Prompt", MaterialTheme.colorScheme.primary) { clipboard.setText(AnnotatedString(prompt)); onDismiss() }
                    ActionRow(Icons.Rounded.Edit, "Edit Prompt", MaterialTheme.colorScheme.secondary) { isEditing = true }
                    ActionRow(Icons.Rounded.Refresh, "Get New Suggestions", MaterialTheme.colorScheme.tertiary) { onRefresh() }
                    ActionRow(Icons.Rounded.VisibilityOff, "Never Show This", MaterialTheme.colorScheme.error) { onNeverShow(prompt) }
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
                TextButton(onDismiss, Modifier.padding(top = 8.dp)) { Text("Dismiss", style = MaterialTheme.typography.labelLarge, color = AiDesign.textColor(0.35f)) }
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
                Text("GROQ KEY REQUIRED", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                Text("Enable Web Search", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Web search uses a high-speed Groq model to extract queries. Please provide your Groq API key.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = AiDesign.textColor(0.58f), lineHeight = 22.sp)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("Groq API Key") }, placeholder = { Text("gsk_…") }, singleLine = true, shape = MediumExpressiveShape, colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = AiDesign.glassColor(), unfocusedContainerColor = AiDesign.glassColor()))
                Spacer(Modifier.height(28.dp))
                ToolzExpressiveButton({ if (key.isNotBlank()) onSave(key) }, Modifier.fillMaxWidth().height(56.dp), shape = BouncyShape, enabled = key.isNotBlank()) { Text("Save Key", fontWeight = FontWeight.Black) }
                Spacer(Modifier.height(10.dp))
                ToolzOutlinedExpressiveButton(onGetLink, Modifier.fillMaxWidth().height(56.dp), shape = BouncyShape) { Text("Get Key →", fontWeight = FontWeight.Bold) }
                TextButton(onDismiss, Modifier.padding(top = 8.dp)) { Text("Cancel", style = MaterialTheme.typography.labelLarge, color = AiDesign.textColor(0.35f)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Settings Dialog — M3 Expressive full upgrade
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiSettingsDialog(
    state: AiSettingsUiState, savedConfigs: List<AiConfig>, onDismiss: () -> Unit,
    onProviderChange: (String) -> Unit, onApiKeyChange: (String) -> Unit, onModelChange: (String) -> Unit, onIconChange: (String) -> Unit,
    onCustomIconClick: () -> Unit, onSave: () -> Unit, onSaveConfig: (String) -> Unit, onDeleteConfig: (AiConfig) -> Unit, onEditConfig: (AiConfig) -> Unit,
    @Suppress("UNUSED_PARAMETER") onMoveConfig: (Int, Int) -> Unit,
    onTest: () -> Unit, onRefresh: () -> Unit, performanceMode: Boolean,
    onToggleDynamicPrompts: (Boolean) -> Unit, onPromptFormatChange: (String) -> Unit,
    aiSearchIconVisible: Boolean, onSetAiSearchIconVisible: (Boolean) -> Unit,
) {
    val context         = LocalContext.current
    var configName      by remember(state.editingConfig) { mutableStateOf(state.editingConfig?.name ?: "") }
    var showConfigSave  by remember { mutableStateOf(false) }
    var showTutorial    by remember { mutableStateOf(false) }
    var showModelMenu   by remember { mutableStateOf(false) }
    var activeTab       by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        containerColor = AiDesign.surfaceColor(),
        shape = SquircleShape,
        title = {
            Column {
                Text("AI Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(14.dp))
                // M3 Expressive tab switch via ToolzConnectedButtonGroup
                ToolzConnectedButtonGroup(
                    selectedIndex = activeTab,
                    options = listOf("Setup", "Presets"),
                    onOptionSelected = { activeTab = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        if (targetState > initialState)
                            (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                        else
                            (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                    },
                    label = "settingsTab",
                ) { tab ->
                    if (tab == 0) {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            SettingsProviderRow(state.provider, onProviderChange)

                            SettingsModelSection(
                                selectedModel = state.selectedModel,
                                provider = state.provider,
                                modelAvailability = state.modelAvailability,
                                showModelMenu = showModelMenu,
                                onModelChange = onModelChange,
                                onShowModelMenuChange = { showModelMenu = it }
                            )

                            SettingsApiKeySection(
                                apiKey = state.apiKey,
                                provider = state.provider,
                                isRemoteKeyAvailable = state.isRemoteKeyAvailable,
                                onApiKeyChange = onApiKeyChange,
                                onShowTutorial = { showTutorial = true }
                            )

                            SettingsPromptsSection(
                                dynamicPromptsEnabled = state.dynamicPromptsEnabled,
                                promptFormat = state.promptFormat,
                                onToggleDynamicPrompts = onToggleDynamicPrompts,
                                onPromptFormatChange = onPromptFormatChange
                            )

                            SettingsAdvancedSection(
                                aiSearchIconVisible = aiSearchIconVisible,
                                onSetAiSearchIconVisible = onSetAiSearchIconVisible
                            )

                            SettingsTestSaveSection(
                                isTesting = state.isTesting,
                                testResult = state.testResult,
                                onTest = onTest,
                                onRefresh = onRefresh,
                                onShowConfigSave = { showConfigSave = true },
                                editingConfig = state.editingConfig
                            )

                            AnimatedVisibility(visible = showConfigSave || state.editingConfig != null) {
                                SettingsPresetEditSection(
                                    configName = configName,
                                    onConfigNameChange = { configName = it },
                                    selectedIcon = state.selectedIcon,
                                    onIconChange = onIconChange,
                                    customIconUri = state.customIconUri,
                                    onCustomIconClick = onCustomIconClick,
                                    provider = state.provider,
                                    onSaveConfig = {
                                        onSaveConfig(it)
                                        showConfigSave = false
                                    }
                                )
                            }
                        }
                    } else {
                        SettingsPresetsList(
                            savedConfigs = savedConfigs,
                            onEditConfig = onEditConfig,
                            onDeleteConfig = onDeleteConfig,
                            onActiveTabChange = { activeTab = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            ToolzExpressiveButton(onSave, shape = MediumExpressiveShape, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Apply", fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("Close", color = AiDesign.textColor(0.5f), fontWeight = FontWeight.Bold) }
        },
    )
    if (showTutorial) GuideDialog { showTutorial = false }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsProviderRow(
    currentProvider: String,
    onProviderChange: (String) -> Unit
) {
    SettingsSection("Provider") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AiSettingsHelper.providers) { p ->
                val isSelected = currentProvider == p
                val pColor = AiDesign.providerColor(p) ?: MaterialTheme.colorScheme.primary
                ExpressiveFilterChip(
                    selected = isSelected, onClick = { onProviderChange(p) },
                    label = { Text(p, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium) },
                    leadingIcon = { Icon(getIconForConfig("AUTO", p), null, Modifier.size(14.dp)) },
                    shape = MediumExpressiveShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = pColor.copy(alpha = 0.15f),
                        selectedLabelColor = pColor,
                        selectedLeadingIconColor = pColor,
                    ),
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected, selectedBorderColor = pColor.copy(alpha = 0.3f)),
                )
            }
        }
    }
}

@Composable
private fun SettingsModelSection(
    selectedModel: String,
    provider: String,
    modelAvailability: ModelAvailability,
    showModelMenu: Boolean,
    onModelChange: (String) -> Unit,
    onShowModelMenuChange: (Boolean) -> Unit
) {
    SettingsSection("Model") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                Surface(
                    onClick = { onShowModelMenuChange(true) },
                    shape = MediumExpressiveShape,
                    color = AiDesign.glassColor(),
                    border = BorderStroke(1.dp, AiDesign.glassBorder())
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Text(selectedModel, fontWeight = FontWeight.Bold, color = AiDesign.textColor())
                        Icon(Icons.Rounded.UnfoldMore, null, tint = AiDesign.textColor(0.6f))
                    }
                }
                DropdownMenu(
                    expanded = showModelMenu,
                    onDismissRequest = { onShowModelMenuChange(false) },
                    containerColor = AiDesign.cardColor(),
                    shape = LargeExpressiveShape
                ) {
                    AiSettingsHelper.getModels(provider).forEach { m ->
                        DropdownMenuItem({ Text(m) }, { onModelChange(m); onShowModelMenuChange(false) })
                    }
                }
            }

            // Custom Model Input
            OutlinedTextField(
                value = selectedModel,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Custom Model Name") },
                shape = MediumExpressiveShape,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AiDesign.glassColor(),
                    unfocusedContainerColor = AiDesign.glassColor(),
                    unfocusedBorderColor = AiDesign.glassBorder(),
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                trailingIcon = {
                    AnimatedContent(targetState = modelAvailability, label = "modelAvailability") { availability ->
                        when (availability) {
                            ModelAvailability.AVAILABLE -> Icon(Icons.Rounded.CheckCircle, "Available", tint = Color(0xFF4CAF50))
                            ModelAvailability.UNAVAILABLE -> Icon(Icons.Rounded.Error, "Unavailable", tint = MaterialTheme.colorScheme.error)
                            ModelAvailability.CHECKING -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            ModelAvailability.UNKNOWN -> null
                        }
                    }
                },
                supportingText = {
                    Text(
                        when (modelAvailability) {
                            ModelAvailability.AVAILABLE -> "Available"
                            ModelAvailability.UNAVAILABLE -> "Unavailable"
                            ModelAvailability.CHECKING -> "Checking..."
                            ModelAvailability.UNKNOWN -> ""
                        },
                        color = when (modelAvailability) {
                            ModelAvailability.AVAILABLE -> Color(0xFF4CAF50)
                            ModelAvailability.UNAVAILABLE -> MaterialTheme.colorScheme.error
                            else -> AiDesign.textColor(0.5f)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun SettingsApiKeySection(
    apiKey: String,
    provider: String,
    isRemoteKeyAvailable: Boolean,
    onApiKeyChange: (String) -> Unit,
    onShowTutorial: () -> Unit
) {
    val context = LocalContext.current
    SettingsSection("API Key") {
        OutlinedTextField(
            apiKey, onApiKeyChange, Modifier.fillMaxWidth(), shape = MediumExpressiveShape,
            placeholder = { Text(AiSettingsHelper.getApiKeyPlaceholder(provider), color = AiDesign.textColor(0.3f)) },
            trailingIcon = { if (apiKey.isNotEmpty()) IconButton({ onApiKeyChange("") }) { Icon(Icons.Rounded.Close, null, tint = AiDesign.textColor(0.5f)) } },
            supportingText = { if (apiKey.isEmpty() && isRemoteKeyAvailable) Text("Using shared key (limited quota)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = AiDesign.glassColor(), unfocusedContainerColor = AiDesign.glassColor(), unfocusedBorderColor = AiDesign.glassBorder(), focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        )
        Row(Modifier.fillMaxWidth(), Arrangement.End) {
            TextButton(onShowTutorial) { Text("Guide", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AiSettingsHelper.getApiKeyUrl(provider)))) }) { Text("Get Key →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SettingsPromptsSection(
    dynamicPromptsEnabled: Boolean,
    promptFormat: String,
    onToggleDynamicPrompts: (Boolean) -> Unit,
    onPromptFormatChange: (String) -> Unit
) {
    SettingsSection("Suggested Prompts") {
        Surface(Modifier.fillMaxWidth(), MediumExpressiveShape, AiDesign.glassColor(), border = BorderStroke(1.dp, AiDesign.glassBorder())) {
            Row(Modifier.padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dynamic Prompts", fontWeight = FontWeight.Bold)
                    Text("Generate based on chat history", style = MaterialTheme.typography.labelSmall, color = AiDesign.textColor(0.55f))
                }
                ExpressiveSwitch(checked = dynamicPromptsEnabled, onCheckedChange = onToggleDynamicPrompts)
            }
        }
        AnimatedVisibility(visible = dynamicPromptsEnabled) {
            Column(Modifier.padding(top = 8.dp)) {
                Text("Prompt Length", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
                ToolzConnectedButtonGroup(
                    selectedIndex = listOf("short", "medium", "long").indexOf(promptFormat).coerceAtLeast(0),
                    options = listOf("Short", "Medium", "Long"),
                    onOptionSelected = { onPromptFormatChange(listOf("short", "medium", "long")[it]) },
                )
            }
        }
    }
}

@Composable
private fun SettingsAdvancedSection(
    aiSearchIconVisible: Boolean,
    onSetAiSearchIconVisible: (Boolean) -> Unit
) {
    SettingsSection("Advanced") {
        Surface(Modifier.fillMaxWidth(), MediumExpressiveShape, AiDesign.glassColor(), border = BorderStroke(1.dp, AiDesign.glassBorder())) {
            Row(Modifier.padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Search Toggle Icon", fontWeight = FontWeight.Bold)
                    Text("Show web search toggle in input", style = MaterialTheme.typography.labelSmall, color = AiDesign.textColor(0.55f))
                }
                ExpressiveSwitch(checked = aiSearchIconVisible, onCheckedChange = onSetAiSearchIconVisible)
            }
        }
    }
}

@Composable
private fun SettingsTestSaveSection(
    isTesting: Boolean,
    testResult: String?,
    onTest: () -> Unit,
    onRefresh: () -> Unit,
    onShowConfigSave: () -> Unit,
    editingConfig: AiConfig?
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolzExpressiveButton(onTest, Modifier.weight(1f).height(48.dp), enabled = !isTesting, shape = MediumExpressiveShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                if (isTesting) ToolzWavyCircularProgressIndicator(Modifier.size(16.dp), MaterialTheme.colorScheme.secondary, Color.Transparent)
                else Text("Test Connection", fontWeight = FontWeight.Bold)
            }
            ToolzExpressiveIconButton(onRefresh, shape = MediumExpressiveShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = AiDesign.glassColor()), modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Sync, null, tint = AiDesign.textColor())
            }
        }
        if (testResult != null) {
            Surface(color = if (testResult.startsWith("✓")) Color(0xFF4CAF50).copy(alpha = 0.14f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), shape = MediumExpressiveShape) {
                Text(testResult, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        ToolzExpressiveButton(onShowConfigSave, Modifier.fillMaxWidth().height(48.dp), shape = MediumExpressiveShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)) {
            Text(if (editingConfig != null) "Update Preset" else "Save as Preset", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SettingsPresetEditSection(
    configName: String,
    onConfigNameChange: (String) -> Unit,
    selectedIcon: String,
    onIconChange: (String) -> Unit,
    customIconUri: String?,
    onCustomIconClick: () -> Unit,
    provider: String,
    onSaveConfig: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(configName, onConfigNameChange, Modifier.fillMaxWidth(), label = { Text("Preset Name") }, shape = MediumExpressiveShape, colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = AiDesign.glassColor(), unfocusedContainerColor = AiDesign.glassColor(), unfocusedBorderColor = AiDesign.glassBorder()))
        // Icon picker row
        Text("Preset Icon", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Surface(onClick = onCustomIconClick, modifier = Modifier.size(48.dp), shape = MediumExpressiveShape, color = if (selectedIcon == "CUSTOM") MaterialTheme.colorScheme.primaryContainer else AiDesign.glassColor(), border = BorderStroke(if (selectedIcon == "CUSTOM") 2.dp else 1.dp, if (selectedIcon == "CUSTOM") MaterialTheme.colorScheme.primary else AiDesign.glassBorder())) {
                    Box(contentAlignment = Alignment.Center) {
                        if (customIconUri != null) AsyncImage(customIconUri, null, Modifier.fillMaxSize().clip(MediumExpressiveShape), contentScale = ContentScale.Crop)
                        else Icon(Icons.Rounded.AddAPhoto, null, Modifier.size(20.dp), tint = AiDesign.textColor(0.6f))
                    }
                }
            }
            items(listOf("AUTO","GEMINI","CHATGPT","GROQ","CLAUDE","DEEPSEEK","BOT","SPARKLE")) { ik ->
                val isSelected = selectedIcon == ik
                Surface(onClick = { onIconChange(ik) }, modifier = Modifier.size(48.dp), shape = MediumExpressiveShape, color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else AiDesign.glassColor(), border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else AiDesign.glassBorder())) {
                    Box(contentAlignment = Alignment.Center) { Icon(getIconForConfig(ik, provider), null, Modifier.size(24.dp), tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else AiDesign.textColor(0.7f)) }
                }
            }
        }
        ToolzExpressiveButton({ onSaveConfig(configName) }, Modifier.fillMaxWidth().height(52.dp), enabled = configName.isNotBlank(), shape = MediumExpressiveShape) { Text("Save Preset", fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun SettingsPresetsList(
    savedConfigs: List<AiConfig>,
    onEditConfig: (AiConfig) -> Unit,
    onDeleteConfig: (AiConfig) -> Unit,
    onActiveTabChange: (Int) -> Unit
) {
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (savedConfigs.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(modifier = Modifier.size(64.dp), shape = SquircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bookmarks, null, Modifier.size(28.dp).alpha(0.3f), tint = AiDesign.textColor()) }
                    }
                    Text("No presets saved", color = AiDesign.textColor(0.4f), modifier = Modifier.padding(top = 12.dp), fontWeight = FontWeight.Medium)
                }
            }
        }
        items(savedConfigs, key = { it.name }) { config ->
            Surface(Modifier.fillMaxWidth(), MediumExpressiveShape, AiDesign.glassColor(), border = BorderStroke(1.dp, AiDesign.glassBorder())) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AiAvatar(config, 42.dp, performanceMode = true)
                    Column(Modifier.weight(1f)) {
                        Text(config.name, fontWeight = FontWeight.Black, color = AiDesign.textColor())
                        Text("${config.provider} · ${config.model}", style = MaterialTheme.typography.labelSmall, color = AiDesign.textColor(0.55f))
                    }
                    ToolzExpressiveIconButton({ onEditConfig(config); onActiveTabChange(0) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)), shape = SmallExpressiveShape) { Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                    ToolzExpressiveIconButton({ onDeleteConfig(config) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)), shape = SmallExpressiveShape) { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings Section helper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(width = 18.dp, height = 3.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.2.sp)
        }
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup Guide Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GuideDialog(onDismiss: () -> Unit) {
    val providers  = AiSettingsHelper.providers
    val pagerState = rememberPagerState { providers.size }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = SquircleShape, color = AiDesign.surfaceColor()) {
            Column(Modifier.padding(24.dp)) {
                Text("Setup Guide", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp))
                HorizontalPager(state = pagerState, modifier = Modifier.height(260.dp)) { page ->
                    val provider = providers[page]
                    val pColor   = AiDesign.providerColor(provider) ?: MaterialTheme.colorScheme.primary
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(modifier = Modifier.size(36.dp), shape = SmallExpressiveShape, color = pColor.copy(alpha = 0.12f)) {
                                Box(contentAlignment = Alignment.Center) { Icon(getIconForConfig("AUTO", provider), null, Modifier.size(18.dp), pColor) }
                            }
                            Text(provider, fontWeight = FontWeight.Black, color = pColor, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(AiSettingsHelper.getApiKeyPlaceholder(provider), color = AiDesign.textColor(0.65f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                // Pager indicators
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(providers.size) { i ->
                        val active = pagerState.currentPage == i
                        val w by animateDpAsState(if (active) 20.dp else 6.dp, spring(Spring.DampingRatioMediumBouncy), label = "dot")
                        Box(Modifier.padding(2.dp).clip(CircleShape).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest).size(width = w, height = 5.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
                ToolzExpressiveButton(onDismiss, Modifier.fillMaxWidth()) { Text("Got it", fontWeight = FontWeight.Black) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Icon resolver (unchanged public contract)
// ─────────────────────────────────────────────────────────────────────────────

fun getIconForConfig(selected: String, provider: String): ImageVector = when (selected) {
    "GEMINI"   -> Icons.Rounded.AutoAwesome
    "CHATGPT"  -> Icons.Rounded.Chat
    "GROQ"     -> Icons.Rounded.Bolt
    "CLAUDE"   -> Icons.Rounded.HistoryEdu
    "DEEPSEEK" -> Icons.Rounded.Troubleshoot
    "BOT"      -> Icons.Rounded.SmartToy
    "SPARKLE"  -> Icons.Rounded.AutoFixHigh
    else       -> when (provider) {
        "Gemini"   -> Icons.Rounded.AutoAwesome
        "Groq"     -> Icons.Rounded.Bolt
        "Claude"   -> Icons.Rounded.HistoryEdu
        "DeepSeek" -> Icons.Rounded.Troubleshoot
        else       -> Icons.Rounded.Chat
    }
}