package com.frerox.toolz.ui.screens.ai

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.frerox.toolz.data.search.SearchResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.frerox.toolz.data.ai.AiChat
import com.frerox.toolz.data.ai.AiConfig
import com.frerox.toolz.data.ai.AiMessage
import com.frerox.toolz.data.ai.AiSettingsHelper
import com.frerox.toolz.ui.components.MarkdownSegment
import com.frerox.toolz.ui.components.parseMarkdownToSegments
import com.frerox.toolz.ui.theme.LocalVibrationManager
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  Design Constants
// ─────────────────────────────────────────────────────────────

object AiDesign {
    val CornerLarge = 28.dp
    val CornerMedium = 20.dp
    val CornerSmall = 16.dp
    val ChatBubblePadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

    @Composable fun glassColor() = if (isSystemInDarkTheme()) {
        Color(0xFF2C2C2C).copy(alpha = 0.8f)
    } else {
        Color(0xFFF5F5F5).copy(alpha = 0.9f)
    }

    @Composable fun glassBorder() = if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
    
    @Composable fun surfaceColor() = MaterialTheme.colorScheme.surface
    @Composable fun cardColor() = MaterialTheme.colorScheme.surfaceContainer
    @Composable fun textColor(alpha: Float = 1f) = if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = alpha)
    } else {
        Color.Black.copy(alpha = alpha)
    }
}

// ─────────────────────────────────────────────────────────────
//  Main Screen
// ─────────────────────────────────────────────────────────────

@Composable
fun ExpressiveBackground(performanceMode: Boolean) {
    if (performanceMode) {
        Box(Modifier.fillMaxSize().background(AiDesign.surfaceColor()))
        return
    }
    val colors = listOf(
        MaterialTheme.colorScheme.primary.copy(0.08f),
        MaterialTheme.colorScheme.secondary.copy(0.05f),
        MaterialTheme.colorScheme.surface
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors))) {
        Box(Modifier.fillMaxSize().alpha(0.3f).blur(100.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.Cyan.copy(0.1f), radius = 600f, center = center.copy(x = 100f, y = 100f))
                drawCircle(Color.Magenta.copy(0.1f), radius = 800f, center = center.copy(x = size.width - 100f, y = size.height - 100f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: AiAssistantViewModel = hiltViewModel(),
    onNavigateToBrowser: (String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vibration = LocalVibrationManager.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val performanceMode = false

    var inputText by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }
    var showQuotaDialog by remember { mutableStateOf(false) }
    var selectedMessageForActions by remember { mutableStateOf<AiMessage?>(null) }
    var selectedMessageForSources by remember { mutableStateOf<AiMessage?>(null) }
    val isStarted = uiState.messages.isNotEmpty() || uiState.isLoading || uiState.streamingText.isNotEmpty()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> viewModel.onImageSelected(android.graphics.BitmapFactory.decodeStream(stream)) } }
    }
    val configIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.updateCustomIcon(it) }
    }

    LaunchedEffect(uiState.streamingText) {
        if (uiState.streamingText.isNotEmpty() && uiState.messages.isNotEmpty()) listState.scrollToItem(uiState.messages.size - 1)
    }
    LaunchedEffect(uiState.quotaExceeded) { if (uiState.quotaExceeded) showQuotaDialog = true }

    // Dialogs / Sheets
    if (showSettings) {
        AiSettingsDialog(
            state = settingsUiState,
            savedConfigs = uiState.savedConfigs,
            onDismiss = { showSettings = false },
            onProviderChange = viewModel::updateProvider,
            onApiKeyChange = viewModel::updateApiKey,
            onModelChange = viewModel::updateModel,
            onIconChange = viewModel::updateIcon,
            onCustomIconClick = { configIconPicker.launch("image/*") },
            onSave = { viewModel.onSettingsSaveRequest(); showSettings = false },
            onSaveConfig = viewModel::saveConfig,
            onDeleteConfig = viewModel::deleteConfig,
            onEditConfig = viewModel::editConfig,
            onMoveConfig = viewModel::moveConfig,
            onTest = viewModel::testConnection,
            onRefresh = viewModel::refreshRemoteKeys,
            performanceMode = performanceMode,
            onToggleDynamicPrompts = viewModel::toggleDynamicPrompts,
            onPromptFormatChange = viewModel::updatePromptFormat,
            aiSearchIconVisible = uiState.aiSearchIconVisible,
            onSetAiSearchIconVisible = viewModel::setAiSearchIconVisible
        )
    }

    if (showQuotaDialog) {
        ModernAiDialog(title = "QUOTA EXCEEDED", icon = Icons.Rounded.LockClock, iconColor = MaterialTheme.colorScheme.error,
            description = "${settingsUiState.provider} has reached its limit.",
            supportingText = "Switch to ${uiState.suggestedProvider} or use your own API key.",
            primaryButtonText = "SWITCH TO ${uiState.suggestedProvider?.uppercase() ?: "OTHER"}",
            onPrimaryClick = { uiState.suggestedProvider?.let { viewModel.switchProvider(it) }; showQuotaDialog = false },
            onDismiss = { showQuotaDialog = false }
        )
    }

    if (showSummary) {
        ChatSummarySheet(
            summary = uiState.chatSummary,
            isSummarizing = uiState.isSummarizing,
            onDismiss = { showSummary = false; viewModel.clearChatSummary() },
            onRefresh = { viewModel.summarizeChat() },
        )
    }

    if (selectedMessageForActions != null) {
        MessageActionsSheet(
            message = selectedMessageForActions!!,
            onDismiss = { selectedMessageForActions = null },
            onRegenerate = { viewModel.regenerateMessage(it); selectedMessageForActions = null }
        )
    }

    if (selectedMessageForSources != null) {
        MessageSourcesSheet(
            message = selectedMessageForSources!!,
            onDismiss = { selectedMessageForSources = null },
            onLinkClick = onNavigateToBrowser
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = AiDesign.surfaceColor(),
                drawerTonalElevation = 0.dp,
                modifier = Modifier.width(320.dp),
                drawerShape = RoundedCornerShape(topEnd = AiDesign.CornerLarge, bottomEnd = AiDesign.CornerLarge),
            ) {
                AiHistoryDrawer(
                    chats = uiState.chats,
                    currentChatId = uiState.currentChatId,
                    onChatSelect = { vibration?.vibrateClick(); viewModel.loadChat(it); scope.launch { drawerState.close() } },
                    onNewChat = { vibration?.vibrateClick(); viewModel.createNewChat(); scope.launch { drawerState.close() } },
                    onDeleteChat = { vibration?.vibrateLongClick(); viewModel.deleteChat(it) },
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.imePadding(),
            topBar = {
                ChatTopBarRedesign(
                    settingsUiState = settingsUiState,
                    uiState = uiState,
                    performanceMode = performanceMode,
                    onBack = { vibration?.vibrateClick(); onBack() },
                    onNewChat = { vibration?.vibrateClick(); viewModel.createNewChat() },
                    onSettings = { vibration?.vibrateClick(); showSettings = true },
                    onHistory = { vibration?.vibrateClick(); scope.launch { drawerState.open() } },
                    onConfigSelect = { vibration?.vibrateClick(); viewModel.onConfigRequest(it) },
                    onSummarize = {
                        vibration?.vibrateTick()
                        showSummary = true
                        if (uiState.chatSummary == null && !uiState.isSummarizing) viewModel.summarizeChat()
                    },
                    onRefreshTitle = { vibration?.vibrateTick(); viewModel.refreshChatTitle() },
                )
            },
            bottomBar = {
                ChatInputBarRedesign(
                    inputText = inputText, isLoading = uiState.isLoading, selectedImage = uiState.selectedImage,
                    supportsVision = AiSettingsHelper.supportsVision(settingsUiState.provider, settingsUiState.selectedModel),
                    supportsFiles = AiSettingsHelper.supportsFiles(settingsUiState.provider, settingsUiState.selectedModel),
                    performanceMode = performanceMode,
                    onInputChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank() || uiState.selectedImage != null) {
                            vibration?.vibrateClick(); viewModel.sendMessage(inputText); inputText = ""
                        }
                    },
                    onCancel = { viewModel.cancelRequest() },
                    onAttach = { imagePicker.launch("image/*") },
                    onRemoveImage = { viewModel.onImageSelected(null) },
                    aiSearchEnabled = uiState.aiSearchEnabled,
                    aiSearchIconVisible = uiState.aiSearchIconVisible,
                    onToggleAiSearch = viewModel::toggleAiSearch
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                ExpressiveBackground(performanceMode)

                Box(Modifier.fillMaxSize().padding(padding)) {
                    // Keys banner
                    AnimatedVisibility(
                        visible = uiState.keysUnavailable,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
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
                        label = "chat_content",
                    ) { started ->
                        if (started) {
                            ChatMessageList(
                                messages = uiState.messages,
                                streamingText = uiState.streamingText,
                                isLoading = uiState.isLoading,
                                error = uiState.error,
                                listState = listState,
                                currentConfig = uiState.savedConfigs.find { it.provider == settingsUiState.provider && it.model == settingsUiState.selectedModel },
                                performanceMode = performanceMode,
                                onRegenerate = { viewModel.regenerateMessage(it) },
                                onLinkClick = onNavigateToBrowser,
                                onLongPress = { selectedMessageForActions = it },
                                onShowSources = { selectedMessageForSources = it },
                                onScrollBottom = { scope.launch { listState.animateScrollToItem(uiState.messages.size.coerceAtLeast(1) - 1) } },
                                onRetrySync = { vibration?.vibrateTick(); viewModel.retrySyncKeys() },
                                loadingPhaseText = uiState.loadingPhaseText,
                            )
                        } else {
                            EmptyChatStateRedesign(
                                performanceMode = performanceMode,
                                onSuggestionClick = { inputText = it },
                                suggestedPrompts = uiState.suggestedPrompts,
                                isGeneratingPrompts = uiState.isGeneratingPrompts,
                                onRefresh = { viewModel.refreshPrompts() },
                                onNeverShow = { viewModel.neverShowPrompt(it) },
                                onEdit = { original, edited -> viewModel.editPrompt(original, edited) },
                                onReset = { viewModel.resetPrompts() }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Top Bar Redesign
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBarRedesign(
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val providerColor = when(settingsUiState.provider) {
        "Gemini" -> Color(0xFF1A73E8)
        "ChatGPT" -> Color(0xFF10A37F)
        "Claude" -> Color(0xFFD97757)
        "DeepSeek" -> Color(0xFF007BFF)
        else -> MaterialTheme.colorScheme.primary
    }

    CenterAlignedTopAppBar(
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = AiDesign.surfaceColor().copy(0.9f)),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onRefreshTitle() }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = providerColor.copy(0.1f), shape = CircleShape) {
                        Icon(getIconForConfig(settingsUiState.selectedIcon, settingsUiState.provider), null, Modifier.padding(4.dp).size(14.dp), tint = providerColor)
                    }
                    Text(uiState.chats.find { it.id == uiState.currentChatId }?.title ?: "AI Assistant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AiDesign.textColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (uiState.isGeneratingTitle) LinearProgressIndicator(Modifier.width(40.dp).height(2.dp).padding(top = 2.dp), color = providerColor, trackColor = Color.Transparent)
            }
        },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBackIosNew, null, Modifier.size(20.dp), tint = AiDesign.textColor(0.7f)) } },
        actions = {
            IconButton(onClick = onSummarize) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(22.dp), tint = AiDesign.textColor(0.7f)) }
            IconButton(onClick = onNewChat) { Icon(Icons.Rounded.Add, null, Modifier.size(24.dp), tint = AiDesign.textColor(0.7f)) }
            IconButton(onClick = onHistory) { Icon(Icons.Rounded.History, null, Modifier.size(22.dp), tint = AiDesign.textColor(0.7f)) }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, null, Modifier.size(22.dp), tint = AiDesign.textColor(0.7f)) }
        }
    )
}

// ─────────────────────────────────────────────────────────────
//  Input Bar Redesign
// ─────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBarRedesign(
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
    aiSearchEnabled: Boolean = false,
    aiSearchIconVisible: Boolean = true,
    onToggleAiSearch: () -> Unit = {},
) {
    var showMediaMenu by remember { mutableStateOf(false) }
    val supportsMedia = supportsVision || supportsFiles
    val isIdle = inputText.isEmpty() && !isLoading && selectedImage == null

    // Idle Glowing Animation
    val infiniteTransition = rememberInfiniteTransition(label = "input_glow")
    val glowAlpha by if (isIdle && !performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 0.05f,
            targetValue = 0.15f,
            animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
            label = "glow_alpha"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, AiDesign.surfaceColor().copy(0.9f), AiDesign.surfaceColor())
                )
            )
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 12.dp)
    ) {
        AnimatedVisibility(
            visible = selectedImage != null,
            enter = scaleIn(tween(400, easing = EaseOutBack)) + fadeIn(),
            exit = scaleOut(tween(300)) + fadeOut()
        ) {
            Box(Modifier.padding(bottom = 12.dp).size(80.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, AiDesign.glassBorder(), RoundedCornerShape(16.dp))) {
                AsyncImage(model = selectedImage, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                IconButton(onClick = onRemoveImage, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).background(Color.Black.copy(0.6f), CircleShape)) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(12.dp), tint = Color.White)
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp),
            shape = RoundedCornerShape(26.dp),
            color = AiDesign.surfaceColor(),
            tonalElevation = 8.dp,
            shadowElevation = if (performanceMode) 0.dp else 4.dp,
            border = BorderStroke(
                width = 1.dp,
                brush = if (isIdle && !performanceMode) {
                    Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary.copy(glowAlpha), Color.Transparent, MaterialTheme.colorScheme.primary.copy(glowAlpha)))
                } else {
                    SolidColor(AiDesign.glassBorder())
                }
            )
        ) {
            Row(
                Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (supportsMedia) {
                    Box {
                        IconButton(onClick = { showMediaMenu = true }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.Add, "Attach", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                        DropdownMenu(
                            expanded = showMediaMenu,
                            onDismissRequest = { showMediaMenu = false },
                            offset = androidx.compose.ui.unit.DpOffset(0.dp, (-8).dp),
                            shape = RoundedCornerShape(20.dp),
                            containerColor = AiDesign.surfaceColor()
                        ) {
                            if (supportsVision) {
                                DropdownMenuItem(
                                    text = { Text("Photo", fontWeight = FontWeight.Medium) },
                                    onClick = { showMediaMenu = false; onAttach() },
                                    leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(20.dp)) }
                                )
                            }
                            if (supportsFiles) {
                                DropdownMenuItem(
                                    text = { Text("Document", fontWeight = FontWeight.Medium) },
                                    onClick = { showMediaMenu = false },
                                    leadingIcon = { Icon(Icons.Rounded.Description, null, Modifier.size(20.dp)) }
                                )
                            }
                        }
                    }
                }

                TextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    placeholder = { Text("Message...", color = AiDesign.textColor(0.4f), fontSize = 16.sp) },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = AiDesign.textColor(),
                        unfocusedTextColor = AiDesign.textColor()
                    ),
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    trailingIcon = {
                        if (aiSearchIconVisible) {
                            val searchIconColor by animateColorAsState(
                                targetValue = if (aiSearchEnabled) MaterialTheme.colorScheme.primary else AiDesign.textColor(0.3f),
                                label = "search_icon_color"
                            )
                            val searchScale by animateFloatAsState(
                                targetValue = if (aiSearchEnabled) 1.2f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "search_scale"
                            )

                            IconButton(onClick = onToggleAiSearch) {
                                Icon(
                                    imageVector = if (aiSearchEnabled) Icons.Rounded.Language else Icons.Rounded.PublicOff,
                                    contentDescription = "Web Search",
                                    tint = searchIconColor,
                                    modifier = Modifier.size(20.dp).graphicsLayer {
                                        scaleX = searchScale
                                        scaleY = searchScale
                                    }
                                )
                            }
                        }
                    }
                )

                Box(Modifier.padding(end = 4.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = isLoading,
                        transitionSpec = {
                            (scaleIn(tween(300, easing = EaseOutBack)) + fadeIn())
                                .togetherWith(scaleOut(tween(200)) + fadeOut())
                        },
                        label = "send_stop_transition"
                    ) { loading ->
                        if (loading) {
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.error.copy(0.1f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.Stop, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            val canSend = inputText.isNotBlank() || selectedImage != null
                            val sendBtnColor by animateColorAsState(
                                targetValue = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(0.05f),
                                label = "send_btn_color"
                            )
                            IconButton(
                                onClick = onSend,
                                enabled = canSend,
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(sendBtnColor, CircleShape)
                            ) {
                                Icon(
                                    Icons.Rounded.ArrowUpward,
                                    null,
                                    tint = if (canSend) Color.White else AiDesign.textColor(0.2f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────
//  Components
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatBubble(
    message: AiMessage,
    currentConfig: AiConfig?,
    performanceMode: Boolean,
    onRegenerate: (Int) -> Unit,
    onLinkClick: (String) -> Unit,
    onLongPress: (AiMessage) -> Unit,
    onShowSources: (AiMessage) -> Unit
) {
    val isUser = message.isUser
    val segments = parseMarkdownToSegments(message.text)
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    val sources = remember(message.searchSources) {
        if (message.searchSources.isNullOrBlank()) {
            emptyList<SearchResult>()
        } else {
            try {
                // Use a Moshi instance with Kotlin Json Adapter
                val moshi = Moshi.Builder()
                    .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
                val listType = Types.newParameterizedType(List::class.java, SearchResult::class.java)
                val adapter = moshi.adapter<List<SearchResult>>(listType)
                adapter.fromJson(message.searchSources) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(600, easing = EaseOutBack)) + slideInVertically(
            initialOffsetY = { 40 },
            animationSpec = tween(600, easing = EaseOutBack)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                if (!isUser) AiAvatar(currentConfig, 32.dp, performanceMode = performanceMode)

                Surface(
                    shape = RoundedCornerShape(
                        topStart = if (isUser) 24.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 24.dp,
                        bottomStart = 24.dp,
                        bottomEnd = 24.dp
                    ),
                    color = if (isUser) MaterialTheme.colorScheme.primary else AiDesign.glassColor(),
                    border = if (isUser) null else BorderStroke(1.dp, AiDesign.glassBorder()),
                    shadowElevation = if (performanceMode) 0.dp else 4.dp,
                    modifier = Modifier.combinedClickable(
                        onLongClick = { onLongPress(message) },
                        onClick = { /* Standard tap - could toggle timestamp or something */ }
                    )
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        segments.forEach { seg ->
                            MarkdownSegment(
                                seg = seg,
                                baseFontSize = 15.sp,
                                modifier = Modifier.padding(vertical = 4.dp),
                                textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else AiDesign.textColor(),
                                onLinkClick = onLinkClick
                            )
                        }

                        if (sources.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                thickness = 0.5.dp,
                                color = (if (isUser) MaterialTheme.colorScheme.onPrimary else AiDesign.textColor()).copy(alpha = 0.15f)
                            )
                            SourcesPill(
                                sources = sources,
                                isUser = isUser,
                                onClick = { onShowSources(message) }
                            )
                            // Extra padding after pill
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourcesPill(sources: List<SearchResult>, isUser: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(0.5.dp, (if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline).copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                sources.take(4).forEach { source ->
                    SourceFavicon(url = source.url, size = 18.dp)
                }
            }
            Text(
                text = "${sources.size} sources",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SourceFavicon(url: String, size: Dp) {
    val domain = try { java.net.URI(url).host?.removePrefix("www.") ?: "" } catch (_: Exception) { "" }
    val faviconUrl = "https://www.google.com/s2/favicons?sz=64&domain=$domain"
    
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f))
    ) {
        AsyncImage(
            model = faviconUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(2.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun EmptyChatStateRedesign(
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

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(Modifier.size(80.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(0.1f)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.height(24.dp))
        Text("How can I help you today?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = AiDesign.textColor())
        Spacer(Modifier.height(40.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("SUGGESTED", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
            Row {
                IconButton(onClick = onReset) { Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp), tint = AiDesign.textColor(0.4f)) }
                IconButton(onClick = onRefresh, enabled = !isGeneratingPrompts) {
                    if (isGeneratingPrompts) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp), tint = AiDesign.textColor(0.4f))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            suggestedPrompts.forEach { prompt ->
                Surface(
                    onClick = { onSuggestionClick(prompt) },
                    shape = RoundedCornerShape(AiDesign.CornerMedium),
                    color = AiDesign.glassColor(),
                    border = BorderStroke(1.dp, AiDesign.glassBorder())
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(prompt, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = AiDesign.textColor(0.8f))
                        IconButton(onClick = { showPromptActions = prompt }, Modifier.size(24.dp)) { Icon(Icons.Rounded.MoreVert, null, Modifier.size(16.dp), tint = AiDesign.textColor(0.4f)) }
                    }
                }
            }
        }
    }

    if (showPromptActions != null) {
        PromptActionSheet(
            prompt = showPromptActions!!,
            onDismiss = { showPromptActions = null },
            onNeverShow = { onNeverShow(it); showPromptActions = null },
            onEdit = { old, new -> onEdit(old, new); showPromptActions = null },
            onCopy = { /* handle copy */ },
            onRefresh = { onRefresh(); showPromptActions = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptActionSheet(
    prompt: String,
    onDismiss: () -> Unit,
    onNeverShow: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    onCopy: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var isEditing by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf(prompt) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = AiDesign.surfaceColor()) {
        Column(Modifier.padding(bottom = 32.dp).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isEditing) {
                OutlinedTextField(
                    value = editedText, onValueChange = { editedText = it },
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AiDesign.textColor(), unfocusedTextColor = AiDesign.textColor())
                )
                Button(onClick = { onEdit(prompt, editedText) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save Changes") }
            } else {
                Text(prompt, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = AiDesign.textColor(), modifier = Modifier.padding(bottom = 16.dp))
                ActionRow(Icons.Rounded.ContentCopy, "Copy Prompt", MaterialTheme.colorScheme.primary) { 
                    clipboard.setText(AnnotatedString(prompt))
                    onDismiss() 
                }
                ActionRow(Icons.Rounded.Edit, "Edit Prompt", MaterialTheme.colorScheme.secondary) { isEditing = true }
                ActionRow(Icons.Rounded.Refresh, "Get New Suggestions", MaterialTheme.colorScheme.tertiary) { onRefresh() }
                ActionRow(Icons.Rounded.VisibilityOff, "Never Show This", MaterialTheme.colorScheme.error) { onNeverShow(prompt) }
            }
        }
    }
}

@Composable
fun ActionRow(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(36.dp).background(color.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = color) }
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = AiDesign.textColor())
        }
    }
}

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
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val colors = listOf(Color.Transparent, Color.Black, Color.Black, Color.Transparent)
                val stops = floatArrayOf(0f, 0.05f, 0.95f, 1f)
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = stops.zip(colors).toTypedArray(),
                        startY = 0f,
                        endY = size.height
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            items(messages, key = { it.id }) { ChatBubble(it, currentConfig, performanceMode, onRegenerate, onLinkClick, onLongPress, onShowSources) }
            if (isLoading || streamingText.isNotEmpty()) {
                item { 
                    ActiveAiBubble(
                        isLoading = isLoading, 
                        loadingPhaseText = loadingPhaseText ?: "",
                        streamingText = streamingText, 
                        currentConfig = currentConfig, 
                        performanceMode = performanceMode, 
                        onLinkClick = onLinkClick
                    ) 
                }
            }
            if (error != null) item { ErrorMessage(error, onRetrySync) }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun AiAvatar(config: AiConfig?, size: Dp, modifier: Modifier = Modifier, performanceMode: Boolean) {
    val color = when(config?.provider) {
        "Gemini" -> Color(0xFF1A73E8)
        "ChatGPT" -> Color(0xFF10A37F)
        "Claude" -> Color(0xFFD97757)
        "DeepSeek" -> Color(0xFF007BFF)
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(modifier.size(size), shape = CircleShape, color = color.copy(0.1f), border = BorderStroke(1.dp, color.copy(0.2f))) {
        Box(contentAlignment = Alignment.Center) {
            Icon(getIconForConfig(config?.iconRes ?: "AUTO", config?.provider ?: ""), null, Modifier.size(size * 0.6f), tint = color)
        }
    }
}

@Composable
fun ActiveAiBubble(
    isLoading: Boolean,
    loadingPhaseText: String,
    streamingText: String,
    currentConfig: AiConfig?,
    performanceMode: Boolean,
    onLinkClick: (String) -> Unit
) {
    val isTypingOnly = isLoading && streamingText.isEmpty()
    
    // Animate bubble width based on state
    val bubbleModifier = Modifier.animateContentSize(
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        AiAvatar(currentConfig, 32.dp, performanceMode = performanceMode)
        
        Surface(
            shape = RoundedCornerShape(4.dp, AiDesign.CornerMedium, AiDesign.CornerMedium, AiDesign.CornerMedium),
            color = AiDesign.glassColor(),
            border = BorderStroke(1.dp, AiDesign.glassBorder()),
            modifier = bubbleModifier
        ) {
            AnimatedContent(
                targetState = isTypingOnly,
                transitionSpec = {
                    (fadeIn(tween(400)) + scaleIn(initialScale = 0.8f, animationSpec = tween(400, easing = EaseOutBack)))
                        .togetherWith(fadeOut(tween(200)))
                }, label = "bubble_expansion"
            ) { typing ->
                if (typing) {
                    // Loading Phase
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TypingIndicatorDots(MaterialTheme.colorScheme.primary)
                        AnimatedContent(
                            targetState = loadingPhaseText,
                            transitionSpec = {
                                slideInVertically { height -> height } + fadeIn() togetherWith
                                slideOutVertically { height -> -height } + fadeOut()
                            }, label = "loading_text"
                        ) { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AiDesign.textColor(0.7f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    // Typewriter Phase
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        val segments = parseMarkdownToSegments(streamingText)
                        segments.forEach { seg ->
                            MarkdownSegment(
                                seg = seg,
                                baseFontSize = 15.sp,
                                modifier = Modifier.padding(vertical = 4.dp).animateContentSize(),
                                textColor = AiDesign.textColor(),
                                onLinkClick = onLinkClick
                            )
                        }
                        if (isLoading) {
                            TypingIndicatorDots(MaterialTheme.colorScheme.primary.copy(0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorDots(color: Color) {
    val transition = rememberInfiniteTransition()
    val dotAlpha1 by transition.animateFloat(0.2f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse))
    val dotAlpha2 by transition.animateFloat(0.2f, 1f, infiniteRepeatable(tween(600, 200), RepeatMode.Reverse))
    val dotAlpha3 by transition.animateFloat(0.2f, 1f, infiniteRepeatable(tween(600, 400), RepeatMode.Reverse))

    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).background(color.copy(dotAlpha1), CircleShape))
        Box(Modifier.size(6.dp).background(color.copy(dotAlpha2), CircleShape))
        Box(Modifier.size(6.dp).background(color.copy(dotAlpha3), CircleShape))
    }
}

fun getIconForConfig(selected: String, provider: String): ImageVector = when(selected) {
    "GEMINI" -> Icons.Rounded.AutoAwesome
    "CHATGPT" -> Icons.Rounded.Chat
    "GROQ" -> Icons.Rounded.Bolt
    "CLAUDE" -> Icons.Rounded.HistoryEdu
    "DEEPSEEK" -> Icons.Rounded.Troubleshoot
    "BOT" -> Icons.Rounded.SmartToy
    "SPARKLE" -> Icons.Rounded.AutoFixHigh
    else -> when(provider) {
        "Gemini" -> Icons.Rounded.AutoAwesome
        "Groq" -> Icons.Rounded.Bolt
        "Claude" -> Icons.Rounded.HistoryEdu
        "DeepSeek" -> Icons.Rounded.Troubleshoot
        else -> Icons.Rounded.Chat
    }
}

@Composable
fun KeysUnavailableBanner(isSyncing: Boolean, onRetrySync: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.2f))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.VpnKeyOff, null, tint = MaterialTheme.colorScheme.error)
            Column(Modifier.weight(1f)) {
                Text("API Keys Unavailable", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Shared keys could not be synced. Check your connection.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(0.7f))
            }
            IconButton(onClick = onRetrySync, enabled = !isSyncing) {
                if (isSyncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.error)
                else Icon(Icons.Rounded.Sync, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSummarySheet(summary: String?, isSummarizing: Boolean, onDismiss: () -> Unit, onRefresh: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AiDesign.surfaceColor()) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("CHAT SUMMARY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                IconButton(onClick = onRefresh, enabled = !isSummarizing) {
                    if (isSummarizing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp), tint = AiDesign.textColor(0.5f))
                }
            }
            Spacer(Modifier.height(16.dp))
            if (isSummarizing && summary == null) {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Text("Analyzing conversation...", Modifier.padding(top = 16.dp), color = AiDesign.textColor(0.6f))
                }
            } else if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyLarge, color = AiDesign.textColor(), lineHeight = 26.sp)
            } else {
                Text("No summary available.", color = AiDesign.textColor(0.4f))
            }
        }
    }
}

@Composable
fun AiHistoryDrawer(chats: List<AiChat>, currentChatId: Int?, onChatSelect: (Int) -> Unit, onNewChat: () -> Unit, onDeleteChat: (AiChat) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("HISTORY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 16.dp))
        Button(onClick = onNewChat, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("New Conversation", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val groups = listOf(ChatGroup.TODAY, ChatGroup.YESTERDAY, ChatGroup.THIS_WEEK, ChatGroup.OLDER)
            groups.forEach { group ->
                val groupChats = chats.filter { it.chatGroup() == group }
                if (groupChats.isNotEmpty()) {
                    item { Text(group.name.replace("_", " "), Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = AiDesign.textColor(0.4f)) }
                    items(groupChats, key = { it.id }) { chat ->
                        val isSelected = chat.id == currentChatId
                        Surface(
                            onClick = { onChatSelect(chat.id) },
                            shape = RoundedCornerShape(AiDesign.CornerSmall),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(0.2f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(if (isSelected) Icons.Rounded.ChatBubble else Icons.Rounded.ChatBubbleOutline, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else AiDesign.textColor(0.5f), modifier = Modifier.size(18.dp))
                                Text(chat.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) AiDesign.textColor() else AiDesign.textColor(0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { onDeleteChat(chat) }, Modifier.size(32.dp)) { Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp), tint = AiDesign.textColor(.3f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernAiDialog(title: String, icon: ImageVector, iconColor: Color, description: String, supportingText: String, primaryButtonText: String, onPrimaryClick: () -> Unit, secondaryButtonText: String? = null, onSecondaryClick: (() -> Unit)? = null, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(28.dp).fillMaxWidth(), 
            shape = RoundedCornerShape(AiDesign.CornerLarge), 
            color = AiDesign.surfaceColor(),
            border = BorderStroke(1.dp, AiDesign.glassBorder()),
            shadowElevation = 24.dp
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(86.dp).background(iconColor.copy(.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(42.dp))
                }
                Spacer(Modifier.height(26.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = iconColor, letterSpacing = 2.sp)
                Spacer(Modifier.height(14.dp))
                Text(description, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = AiDesign.textColor())
                Spacer(Modifier.height(10.dp))
                Text(supportingText, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = AiDesign.textColor(.6f), lineHeight = 22.sp)
                Spacer(Modifier.height(34.dp))
                Button(onClick = onPrimaryClick, Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = iconColor)) { Text(primaryButtonText, fontWeight = FontWeight.Black, color = Color.White) }
                if (secondaryButtonText != null && onSecondaryClick != null) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = onSecondaryClick, Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, AiDesign.glassBorder())) { Text(secondaryButtonText, fontWeight = FontWeight.Bold, color = AiDesign.textColor(0.8f)) }
                }
                TextButton(onClick = onDismiss, Modifier.padding(top = 10.dp)) { Text("Dismiss", style = MaterialTheme.typography.labelLarge, color = AiDesign.textColor(0.4f)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsDialog(
    state: AiSettingsUiState, savedConfigs: List<AiConfig>, onDismiss: () -> Unit,
    onProviderChange: (String) -> Unit, onApiKeyChange: (String) -> Unit, onModelChange: (String) -> Unit, onIconChange: (String) -> Unit,
    onCustomIconClick: () -> Unit,
    onSave: () -> Unit, onSaveConfig: (String) -> Unit, onDeleteConfig: (AiConfig) -> Unit, onEditConfig: (AiConfig) -> Unit,
    @Suppress("UNUSED_PARAMETER") onMoveConfig: (Int, Int) -> Unit, onTest: () -> Unit, onRefresh: () -> Unit,
    performanceMode: Boolean,
    onToggleDynamicPrompts: (Boolean) -> Unit,
    onPromptFormatChange: (String) -> Unit,
    aiSearchIconVisible: Boolean,
    onSetAiSearchIconVisible: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var configName by remember(state.editingConfig) { mutableStateOf(state.editingConfig?.name ?: "") }
    var showConfigSave by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var activeTab by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        containerColor = AiDesign.surfaceColor(), shape = RoundedCornerShape(AiDesign.CornerLarge),
        title = {
            Column {
                Text("AI Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = AiDesign.textColor())
                Spacer(Modifier.height(16.dp))
                PrimaryTabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = {
                        if (activeTab < 2) {
                            TabRowDefaults.PrimaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(activeTab, matchContentSize = true),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) {
                    val tabs = listOf("Setup" to Icons.Rounded.Settings, "Presets" to Icons.Rounded.Bookmarks)
                    tabs.forEachIndexed { i, (l, icon) -> 
                        Tab(
                            selected = activeTab == i, 
                            onClick = { activeTab = i }, 
                            unselectedContentColor = AiDesign.textColor(0.5f), 
                            selectedContentColor = AiDesign.textColor()
                        ) { 
                            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(icon, null, Modifier.size(16.dp))
                                Text(l, fontWeight = FontWeight.Bold) 
                            }
                        } 
                    }
                }
            }
        },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "settings_tab"
                ) { tab ->
                    if (tab == 0) {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            SettingsSection("Provider") {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AiSettingsHelper.providers.forEach { p ->
                                        FilterChip(
                                            selected = state.provider == p, 
                                            onClick = { onProviderChange(p) }, 
                                            label = { Text(p) }, 
                                            shape = RoundedCornerShape(12.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = Color.White,
                                                labelColor = AiDesign.textColor(0.6f),
                                                containerColor = AiDesign.glassColor()
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = state.provider == p, borderColor = AiDesign.glassBorder(), selectedBorderColor = Color.Transparent)
                                        )
                                    }
                                }
                            }
                            SettingsSection("Model") {
                                Surface(onClick = { showModelMenu = true }, shape = RoundedCornerShape(AiDesign.CornerSmall), color = AiDesign.glassColor(), border = BorderStroke(1.dp, AiDesign.glassBorder())) {
                                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(state.selectedModel, fontWeight = FontWeight.Bold, color = AiDesign.textColor())
                                        Icon(Icons.Rounded.UnfoldMore, null, tint = AiDesign.textColor(0.7f))
                                    }
                                }
                                DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }, containerColor = AiDesign.cardColor()) {
                                    AiSettingsHelper.getModels(state.provider).forEach { m -> DropdownMenuItem(text = { Text(m, color = AiDesign.textColor()) }, onClick = { onModelChange(m); showModelMenu = false }) }
                                }
                            }
                            SettingsSection("API Key") {
                                OutlinedTextField(
                                    value = state.apiKey, onValueChange = onApiKeyChange, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(AiDesign.CornerSmall),
                                    placeholder = { Text(AiSettingsHelper.getApiKeyPlaceholder(state.provider), color = AiDesign.textColor(0.3f)) },
                                    trailingIcon = { if (state.apiKey.isNotEmpty()) IconButton(onClick = { onApiKeyChange("") }) { Icon(Icons.Rounded.Close, null, tint = AiDesign.textColor(0.6f)) } },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = AiDesign.textColor(),
                                        unfocusedTextColor = AiDesign.textColor(),
                                        unfocusedBorderColor = AiDesign.glassBorder(),
                                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(0.5f),
                                        unfocusedContainerColor = AiDesign.glassColor(),
                                        focusedContainerColor = AiDesign.glassColor()
                                    ),
                                    supportingText = {
                                        if (state.apiKey.isEmpty() && state.isRemoteKeyAvailable) Text("Using shared key (limited quota)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { showTutorial = true }) { Text("Guide", color = MaterialTheme.colorScheme.primary) }
                                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AiSettingsHelper.getApiKeyUrl(state.provider)))) }) { Text("Get Key →", color = MaterialTheme.colorScheme.primary) }
                                }
                            }

                            SettingsSection("Suggested Prompts") {
                                Row(
                                    Modifier.fillMaxWidth().background(AiDesign.glassColor(), RoundedCornerShape(AiDesign.CornerSmall)).border(1.dp, AiDesign.glassBorder(), RoundedCornerShape(AiDesign.CornerSmall)).padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Dynamic Prompts", fontWeight = FontWeight.Bold, color = AiDesign.textColor())
                                        Text("Generate suggestions based on chat history", style = MaterialTheme.typography.labelSmall, color = AiDesign.textColor(0.6f))
                                    }
                                    Switch(
                                        checked = state.dynamicPromptsEnabled,
                                        onCheckedChange = onToggleDynamicPrompts,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                                            uncheckedThumbColor = AiDesign.textColor(0.6f),
                                            uncheckedTrackColor = AiDesign.glassColor(),
                                            uncheckedBorderColor = AiDesign.glassBorder()
                                        )
                                    )
                                }

                                if (state.dynamicPromptsEnabled) {
                                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Prompt Format", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                        Row(
                                            Modifier.fillMaxWidth().background(AiDesign.glassColor(), RoundedCornerShape(12.dp)).padding(4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf("short", "medium", "long").forEach { format ->
                                                val isSelected = state.promptFormat == format
                                                Surface(
                                                    onClick = { onPromptFormatChange(format) },
                                                    modifier = Modifier.weight(1f).height(36.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            format.replaceFirstChar { it.uppercase() },
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else AiDesign.textColor(0.7f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            SettingsSection("Advanced") {
                                Row(
                                    Modifier.fillMaxWidth().background(AiDesign.glassColor(), RoundedCornerShape(AiDesign.CornerSmall)).border(1.dp, AiDesign.glassBorder(), RoundedCornerShape(AiDesign.CornerSmall)).padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Search Toggle Icon", fontWeight = FontWeight.Bold, color = AiDesign.textColor())
                                        Text("Show web search toggle in chat input", style = MaterialTheme.typography.labelSmall, color = AiDesign.textColor(0.6f))
                                    }
                                    Switch(
                                        checked = aiSearchIconVisible,
                                        onCheckedChange = onSetAiSearchIconVisible,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                                            uncheckedThumbColor = AiDesign.textColor(0.6f),
                                            uncheckedTrackColor = AiDesign.glassColor(),
                                            uncheckedBorderColor = AiDesign.glassBorder()
                                        )
                                    )
                                }
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = onTest, Modifier.weight(1f).height(48.dp), enabled = !state.isTesting, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                                    if (state.isTesting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test Connection", fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = onRefresh, Modifier.size(48.dp).background(AiDesign.glassColor(), RoundedCornerShape(14.dp))) { Icon(Icons.Rounded.Sync, null, tint = AiDesign.textColor()) }
                            }

                            if (state.testResult != null) {
                                Surface(color = if (state.testResult.startsWith("✓")) Color(0xFF4CAF50).copy(.15f) else Color(0xFFFF5252).copy(.15f), shape = RoundedCornerShape(12.dp)) {
                                    Text(state.testResult, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = AiDesign.textColor())
                                }
                            }

                            Button(onClick = { showConfigSave = true }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)) {
                                Text(if (state.editingConfig != null) "Update Preset" else "Save as Preset", fontWeight = FontWeight.Black)
                            }

                            AnimatedVisibility(visible = showConfigSave || state.editingConfig != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = configName, onValueChange = { configName = it }, 
                                        Modifier.fillMaxWidth(), label = { Text("Preset Name") }, 
                                        shape = RoundedCornerShape(14.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = AiDesign.textColor(),
                                            unfocusedTextColor = AiDesign.textColor(),
                                            unfocusedBorderColor = AiDesign.glassBorder(),
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedContainerColor = AiDesign.glassColor(),
                                            focusedContainerColor = AiDesign.glassColor(),
                                            unfocusedLabelColor = AiDesign.textColor(0.5f),
                                            focusedLabelColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Preset Icon", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            // Custom Icon Picker
                                            Surface(
                                                onClick = onCustomIconClick, 
                                                modifier = Modifier.size(48.dp), 
                                                shape = RoundedCornerShape(14.dp), 
                                                color = if (state.selectedIcon == "CUSTOM") MaterialTheme.colorScheme.primaryContainer else AiDesign.glassColor(),
                                                border = if (state.selectedIcon == "CUSTOM") BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, AiDesign.glassBorder())
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (state.customIconUri != null) {
                                                        AsyncImage(model = state.customIconUri, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                                                    } else {
                                                        Icon(Icons.Rounded.AddAPhoto, null, Modifier.size(20.dp), tint = AiDesign.textColor(0.7f))
                                                    }
                                                }
                                            }

                                            listOf("AUTO","GEMINI","CHATGPT","GROQ","CLAUDE","DEEPSEEK","BOT","SPARKLE").forEach { ik ->
                                                Surface(
                                                    onClick = { onIconChange(ik) }, 
                                                    modifier = Modifier.size(48.dp), 
                                                    shape = RoundedCornerShape(14.dp), 
                                                    color = if (state.selectedIcon == ik) MaterialTheme.colorScheme.primaryContainer else AiDesign.glassColor(), 
                                                    border = if (state.selectedIcon == ik) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, AiDesign.glassBorder())
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) { Icon(getIconForConfig(ik, state.provider), null, Modifier.size(24.dp), tint = if (state.selectedIcon == ik) MaterialTheme.colorScheme.onPrimaryContainer else AiDesign.textColor()) }
                                                }
                                            }
                                        }
                                    }
                                    Button(onClick = { onSaveConfig(configName); showConfigSave = false }, Modifier.fillMaxWidth().height(52.dp), enabled = configName.isNotBlank(), shape = RoundedCornerShape(16.dp)) { Text("Save Preset", fontWeight = FontWeight.Black) }
                                }
                            }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (savedConfigs.isEmpty()) {
                                item { 
                                    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Rounded.Bookmarks, null, Modifier.size(48.dp).alpha(0.2f), tint = AiDesign.textColor())
                                        Text("No presets saved", color = AiDesign.textColor(0.4f), modifier = Modifier.padding(top = 8.dp))
                                    }
                                }
                            }
                            items(savedConfigs, key = { it.name }) { config ->
                                Surface(Modifier.fillMaxWidth(), RoundedCornerShape(AiDesign.CornerSmall), AiDesign.glassColor(), border = BorderStroke(1.dp, AiDesign.glassBorder())) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        AiAvatar(config, 40.dp, performanceMode = true)
                                        Column(Modifier.weight(1f)) { Text(config.name, fontWeight = FontWeight.Bold, color = AiDesign.textColor()); Text("${config.provider} · ${config.model}", style = MaterialTheme.typography.labelSmall, color = AiDesign.textColor(0.6f)) }
                                        IconButton(onClick = { onEditConfig(config); activeTab = 0 }) { Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                                        IconButton(onClick = { onDeleteConfig(config) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFF5252)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onSave, shape = RoundedCornerShape(14.dp)) { Text("Apply", fontWeight = FontWeight.Black) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = AiDesign.textColor(0.6f)) } },
    )
    if (showTutorial) GuideDialog { showTutorial = false }
}

@Composable
private fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideDialog(onDismiss: () -> Unit) {
    val providers = AiSettingsHelper.providers
    val pagerState = rememberPagerState { providers.size }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(AiDesign.CornerLarge), color = AiDesign.surfaceColor()) {
            Column(Modifier.padding(24.dp)) {
                Text("Setup Guide", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp))
                HorizontalPager(state = pagerState, modifier = Modifier.height(300.dp)) { page ->
                    val provider = providers[page]
                    Column {
                        Text(provider, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(AiSettingsHelper.getApiKeyPlaceholder(provider), color = AiDesign.textColor(0.7f))
                    }
                }
                Button(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Got it") }
            }
        }
    }
}

@Composable
fun ErrorMessage(error: String, onRetry: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(vertical = 12.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer.copy(0.4f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.2f))) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            Text(error, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, null, tint = MaterialTheme.colorScheme.error) }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    message: AiMessage,
    onDismiss: () -> Unit,
    onRegenerate: (Int) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Message Actions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            ActionRow(
                icon = Icons.Rounded.ContentCopy,
                label = "Copy Text",
                color = MaterialTheme.colorScheme.onSurface,
                onClick = {
                    clipboardManager.setText(AnnotatedString(message.text))
                    onDismiss()
                }
            )
            
            if (!message.isUser) {
                ActionRow(
                    icon = Icons.Rounded.Refresh,
                    label = "Regenerate",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = {
                        onRegenerate(message.id)
                        onDismiss()
                    }
                )
            }

            ActionRow(
                icon = Icons.Rounded.Share,
                label = "Share",
                color = MaterialTheme.colorScheme.onSurface,
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message.text)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share via"))
                    onDismiss()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageSourcesSheet(
    message: AiMessage,
    onDismiss: () -> Unit,
    onLinkClick: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    
    val sources = remember(message.searchSources) {
        if (message.searchSources.isNullOrBlank()) {
            emptyList<SearchResult>()
        } else {
            try {
                val moshi = Moshi.Builder()
                    .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
                val listType = Types.newParameterizedType(List::class.java, SearchResult::class.java)
                val adapter = moshi.adapter<List<SearchResult>>(listType)
                adapter.fromJson(message.searchSources) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Sources",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(sources, key = { index, source -> "${source.url}_$index" }) { _, source ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SourceFavicon(url = source.url, size = 24.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = source.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = source.displayUrl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            if (source.snippet.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = source.snippet,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 18.sp
                                )
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onLinkClick(source.url) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Rounded.Public, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open", fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = { 
                                        clipboardManager.setText(AnnotatedString(source.url))
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Copy URL", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
