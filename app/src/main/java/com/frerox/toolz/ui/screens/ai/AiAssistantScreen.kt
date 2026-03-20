package com.frerox.toolz.ui.screens.ai

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.frerox.toolz.data.ai.AiChat
import com.frerox.toolz.data.ai.AiConfig
import com.frerox.toolz.data.ai.AiMessage
import com.frerox.toolz.data.ai.AiSettingsHelper
import com.frerox.toolz.ui.components.MarkdownSegment
import com.frerox.toolz.ui.components.parseMarkdownToSegments
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  Provider icon helper
// ─────────────────────────────────────────────────────────────

fun getIconForConfig(iconType: String, provider: String): ImageVector = when (iconType) {
    "GEMINI"   -> Icons.Rounded.AutoAwesome
    "CHATGPT"  -> Icons.Rounded.Psychology
    "GROQ"     -> Icons.Rounded.Bolt
    "CLAUDE"   -> Icons.AutoMirrored.Rounded.FactCheck
    "DEEPSEEK" -> Icons.Rounded.Cyclone
    "BOT"      -> Icons.Rounded.SmartToy
    "SPARKLE"  -> Icons.Rounded.AutoAwesome
    "BRAIN"    -> Icons.Rounded.PsychologyAlt
    else       -> when (provider) {
        "Gemini"     -> Icons.Rounded.AutoAwesome
        "ChatGPT"    -> Icons.Rounded.Psychology
        "Groq"       -> Icons.Rounded.Bolt
        "Claude"     -> Icons.AutoMirrored.Rounded.FactCheck
        "DeepSeek"   -> Icons.Rounded.Cyclone
        "OpenRouter" -> Icons.Rounded.Hub
        else         -> Icons.Rounded.SmartToy
    }
}

// ─────────────────────────────────────────────────────────────
//  Keys unavailable banner
// ─────────────────────────────────────────────────────────────

@Composable
private fun KeysUnavailableBanner(isSyncing: Boolean, onRetrySync: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        color    = MaterialTheme.colorScheme.errorContainer.copy(.6f),
        shape    = RoundedCornerShape(14.dp),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(.2f)),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.KeyOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            Text("No API keys available", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
            Surface(onClick = { if (!isSyncing) onRetrySync() }, color = MaterialTheme.colorScheme.error, shape = RoundedCornerShape(9.dp)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (isSyncing) CircularProgressIndicator(Modifier.size(11.dp), strokeWidth = 1.5.dp, color = Color.White)
                    else Icon(Icons.Rounded.Sync, null, Modifier.size(11.dp), tint = Color.White)
                    Text(if (isSyncing) "Syncing…" else "RETRY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Chat summary bottom sheet
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSummarySheet(
    summary      : String?,
    isSummarizing: Boolean,
    onDismiss    : () -> Unit,
    onRefresh    : () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(.2f), CircleShape))
            }
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 44.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val inf = rememberInfiniteTransition(label = "spark")
                    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "r")
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp).rotate(if (isSummarizing) rot else 0f), tint = MaterialTheme.colorScheme.primary)
                    Text("CHAT SUMMARY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!isSummarizing) {
                        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(.5f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            AnimatedContent(targetState = isSummarizing, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "sumContent") { loading ->
                if (loading) {
                    // Animated loading state
                    val inf2 = rememberInfiniteTransition(label = "sumLoad")
                    val d1 by inf2.animateFloat(.15f, 1f, infiniteRepeatable(tween(600, delayMillis = 0),   RepeatMode.Reverse), "d1")
                    val d2 by inf2.animateFloat(.15f, 1f, infiniteRepeatable(tween(600, delayMillis = 200), RepeatMode.Reverse), "d2")
                    val d3 by inf2.animateFloat(.15f, 1f, infiniteRepeatable(tween(600, delayMillis = 400), RepeatMode.Reverse), "d3")
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(d1, d2, d3).forEach { a -> Box(Modifier.size(9.dp).alpha(a).background(MaterialTheme.colorScheme.primary, CircleShape)) }
                        }
                        Text("Analyzing conversation…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.6f))
                    }
                } else {
                    if (summary != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                color    = MaterialTheme.colorScheme.primaryContainer.copy(.15f),
                                shape    = RoundedCornerShape(18.dp),
                                border   = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(.12f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(summary, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
                            }
                            Text("Groq · llama-3.3-70b-versatile", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.35f))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Main screen
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: AiAssistantViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState         by viewModel.uiState.collectAsState()
    val settingsUiState by viewModel.settingsUiState.collectAsState()
    var inputText       by remember { mutableStateOf("") }
    val listState       = rememberLazyListState()
    val vibration       = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current
    val drawerState     = rememberDrawerState(DrawerValue.Closed)
    val scope           = rememberCoroutineScope()
    val context         = LocalContext.current
    var showSettings    by remember { mutableStateOf(false) }
    var showQuotaDialog by remember { mutableStateOf(false) }
    var showSummary     by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                else @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                viewModel.onImageSelected(bmp)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        if (uiState.messages.isNotEmpty()) scope.launch { listState.animateScrollToItem(uiState.messages.size - 1) }
    }
    LaunchedEffect(uiState.streamingText) {
        if (uiState.streamingText.isNotEmpty() && uiState.messages.isNotEmpty()) listState.scrollToItem(uiState.messages.size - 1)
    }
    LaunchedEffect(uiState.quotaExceeded) { if (uiState.quotaExceeded) showQuotaDialog = true }

    // Dialogs / sheets
    if (showSettings) {
        AiSettingsDialog(state = settingsUiState, savedConfigs = uiState.savedConfigs, onDismiss = { showSettings = false },
            onProviderChange = viewModel::updateProvider, onApiKeyChange = viewModel::updateApiKey,
            onModelChange = viewModel::updateModel, onIconChange = viewModel::updateIcon,
            onSave = { viewModel.saveSettings(); showSettings = false },
            onSaveConfig = viewModel::saveConfig, onDeleteConfig = viewModel::deleteConfig,
            onEditConfig = viewModel::editConfig, onMoveConfig = viewModel::moveConfig,
            onTest = viewModel::testConnection, onRefresh = viewModel::refreshRemoteKeys)
    }
    if (showQuotaDialog) {
        ModernAiDialog(title = "QUOTA EXCEEDED", icon = Icons.Rounded.LockClock, iconColor = MaterialTheme.colorScheme.error,
            description = "${settingsUiState.provider} has reached its limit.",
            supportingText = "Switch to ${uiState.suggestedProvider} or use your own API key.",
            primaryButtonText = "SWITCH TO ${uiState.suggestedProvider?.uppercase() ?: "OTHER"}",
            onPrimaryClick = { uiState.suggestedProvider?.let { viewModel.switchProvider(it) }; showQuotaDialog = false },
            secondaryButtonText = "SETUP MY OWN KEY", onSecondaryClick = { showQuotaDialog = false; showSettings = true },
            onDismiss = { showQuotaDialog = false })
    }
    if (uiState.pendingConfig != null) {
        ModernAiDialog(title = "SWITCH CONFIG", icon = Icons.Rounded.SwapHoriz, iconColor = MaterialTheme.colorScheme.primary,
            description = "Starting a new chat with '${uiState.pendingConfig?.name}'.",
            supportingText = "Your current conversation will be saved in history.",
            primaryButtonText = "CONFIRM & NEW CHAT", onPrimaryClick = viewModel::confirmConfigSwitch,
            secondaryButtonText = "CANCEL", onSecondaryClick = viewModel::cancelConfigSwitch, onDismiss = viewModel::cancelConfigSwitch)
    }
    if (showSummary) {
        ChatSummarySheet(
            summary       = uiState.chatSummary,
            isSummarizing = uiState.isSummarizing,
            onDismiss     = { showSummary = false; viewModel.clearChatSummary() },
            onRefresh     = { viewModel.summarizeChat() },
        )
    }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                drawerTonalElevation = 0.dp,
                modifier  = Modifier.width(316.dp),
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
            ) {
                AiHistoryDrawer(
                    chats         = uiState.chats,
                    currentChatId = uiState.currentChatId,
                    onChatSelect  = { vibration?.vibrateClick(); viewModel.loadChat(it); scope.launch { drawerState.close() } },
                    onNewChat     = { vibration?.vibrateClick(); viewModel.createNewChat(); scope.launch { drawerState.close() } },
                    onDeleteChat  = { vibration?.vibrateLongClick(); viewModel.deleteChat(it) },
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    settingsUiState   = settingsUiState,
                    uiState           = uiState,
                    performanceMode   = performanceMode,
                    onBack            = { vibration?.vibrateClick(); onBack() },
                    onNewChat         = { vibration?.vibrateClick(); viewModel.createNewChat() },
                    onSettings        = { vibration?.vibrateClick(); showSettings = true },
                    onHistory         = { vibration?.vibrateClick(); scope.launch { drawerState.open() } },
                    onConfigSelect    = { vibration?.vibrateClick(); viewModel.onConfigRequest(it) },
                    onSummarize       = {
                        vibration?.vibrateTick()
                        showSummary = true
                        if (uiState.chatSummary == null && !uiState.isSummarizing) viewModel.summarizeChat()
                    },
                    onRefreshTitle    = { vibration?.vibrateTick(); viewModel.refreshChatTitle() },
                )
            },
            bottomBar = {
                ChatInputBar(
                    inputText = inputText, isLoading = uiState.isLoading, selectedImage = uiState.selectedImage,
                    supportsVision = AiSettingsHelper.supportsVision(settingsUiState.provider, settingsUiState.selectedModel),
                    performanceMode = performanceMode,
                    onInputChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank() || uiState.selectedImage != null) {
                            vibration?.vibrateClick(); viewModel.sendMessage(inputText); inputText = ""
                        }
                    },
                    onCancel    = { viewModel.cancelRequest() },
                    onAttach    = { imagePicker.launch("image/*") },
                    onRemoveImage = { viewModel.onImageSelected(null) },
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)) {
                // Keys banner
                AnimatedVisibility(
                    visible  = uiState.keysUnavailable,
                    enter    = expandVertically() + fadeIn(),
                    exit     = shrinkVertically() + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(2f),
                ) {
                    KeysUnavailableBanner(
                        isSyncing   = uiState.isSyncingKeys,
                        onRetrySync = { vibration?.vibrateTick(); viewModel.retrySyncKeys() },
                    )
                }

                AnimatedContent(
                    targetState = uiState.messages.isEmpty() && !uiState.isLoading,
                    transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
                    label = "chat_content",
                ) { showEmpty ->
                    if (showEmpty) {
                        EmptyChatState(onSuggestionClick = { inputText = it })
                    } else {
                        ChatMessageList(
                            messages        = uiState.messages,
                            streamingText   = uiState.streamingText,
                            isLoading       = uiState.isLoading,
                            error           = uiState.error,
                            listState       = listState,
                            currentConfig   = uiState.savedConfigs.find { it.provider == settingsUiState.provider && it.model == settingsUiState.selectedModel },
                            performanceMode = performanceMode,
                            onScrollBottom  = { scope.launch { listState.animateScrollToItem(uiState.messages.size.coerceAtLeast(1) - 1) } },
                            onRetrySync     = { vibration?.vibrateTick(); viewModel.retrySyncKeys() },
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Top bar
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    settingsUiState: AiSettingsUiState,
    uiState        : AiAssistantUiState,
    performanceMode: Boolean,
    onBack         : () -> Unit,
    onNewChat      : () -> Unit,
    onSettings     : () -> Unit,
    onHistory      : () -> Unit,
    onConfigSelect : (AiConfig) -> Unit,
    onSummarize    : () -> Unit,
    onRefreshTitle : () -> Unit,
) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        CenterAlignedTopAppBar(
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Chat title + refresh button
                    val currentChat = uiState.chats.find { it.id == uiState.currentChatId }
                    val hasMessages = uiState.messages.isNotEmpty()

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AnimatedContent(
                            targetState = currentChat?.title ?: "AI ASSISTANT",
                            transitionSpec = { (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut()) },
                            label = "title",
                        ) { title ->
                            Text(
                                title.uppercase(),
                                style         = MaterialTheme.typography.labelMedium,
                                fontWeight    = FontWeight.Black,
                                color         = MaterialTheme.colorScheme.primary,
                                letterSpacing = if (currentChat != null) 0.5.sp else 2.sp,
                                maxLines      = 1,
                                overflow      = TextOverflow.Ellipsis,
                                modifier      = Modifier.widthIn(max = 200.dp),
                            )
                        }
                        // Refresh title button (shows when viewing a chat)
                        AnimatedVisibility(visible = hasMessages && !uiState.isGeneratingTitle, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                            IconButton(onClick = onRefreshTitle, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Rounded.AutoAwesome, "Refresh title", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary.copy(.6f))
                            }
                        }
                        if (uiState.isGeneratingTitle && !performanceMode) {
                            CircularProgressIndicator(Modifier.size(11.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary.copy(.5f))
                        }
                    }

                    // Status row
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (uiState.isSyncingKeys && !performanceMode) {
                            CircularProgressIndicator(Modifier.size(6.dp), strokeWidth = 1.dp, color = MaterialTheme.colorScheme.primary.copy(.6f))
                        } else {
                            val dotColor by animateColorAsState(
                                when {
                                    uiState.keysUnavailable   -> MaterialTheme.colorScheme.error
                                    uiState.isLoading         -> MaterialTheme.colorScheme.primary
                                    uiState.error != null     -> MaterialTheme.colorScheme.error
                                    else                      -> Color(0xFF4CAF50)
                                }, label = "dot"
                            )
                            if (uiState.isLoading && !performanceMode) {
                                val inf = rememberInfiniteTransition(label = "ds")
                                val s by inf.animateFloat(.6f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), "ds")
                                Box(Modifier.size(6.dp).scale(s).clip(CircleShape).background(dotColor))
                            } else {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                            }
                        }
                        AnimatedContent(
                            targetState = "${settingsUiState.provider} · ${settingsUiState.selectedModel}",
                            transitionSpec = { (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut()) },
                            label = "model",
                        ) { text ->
                            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick  = onBack,
                    modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.size(20.dp)) }
            },
            actions = {
                // Summarize button (only shown when there are messages)
                AnimatedVisibility(
                    visible = uiState.messages.isNotEmpty() && !uiState.isLoading,
                    enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit    = scaleOut() + fadeOut(),
                ) {
                    IconButton(
                        onClick  = onSummarize,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer.copy(.6f)),
                    ) {
                        Icon(Icons.Rounded.Summarize, "Summarize", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                IconButton(onClick = onNewChat) { Icon(Icons.AutoMirrored.Rounded.NoteAdd, "New Chat", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                IconButton(onClick = onHistory, modifier = Modifier.padding(end = 4.dp)) { Icon(Icons.Rounded.History, "History") }
            },
        )

        // Config chips
        if (uiState.savedConfigs.isNotEmpty()) {
            LazyRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(uiState.savedConfigs) { config ->
                    val isSel = settingsUiState.provider == config.provider && settingsUiState.selectedModel == config.model && settingsUiState.apiKey == config.apiKey
                    val scale by animateFloatAsState(if (isSel) 1.05f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "cs")
                    FilterChip(
                        selected = isSel, onClick = { onConfigSelect(config) },
                        label    = { Text(config.name, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(getIconForConfig(config.iconRes, config.provider), null, Modifier.size(15.dp)) },
                        modifier = Modifier.scale(scale), shape = RoundedCornerShape(11.dp),
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor   = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor       = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        }

        HorizontalDivider(thickness = .5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(.35f))
    }
}

// ─────────────────────────────────────────────────────────────
//  Input bar
// ─────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    inputText      : String,
    isLoading      : Boolean,
    selectedImage  : Bitmap?,
    supportsVision : Boolean,
    performanceMode: Boolean,
    onInputChange  : (String) -> Unit,
    onSend         : () -> Unit,
    onCancel       : () -> Unit,
    onAttach       : () -> Unit,
    onRemoveImage  : () -> Unit,
) {
    val hasContent = inputText.isNotBlank() || selectedImage != null
    val surfBg by animateColorAsState(
        if (hasContent) MaterialTheme.colorScheme.primaryContainer.copy(.2f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "ib"
    )

    Column(
        Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        AnimatedVisibility(visible = selectedImage != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Box(Modifier.padding(start = 4.dp)) {
                Surface(shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(.4f))) {
                    AsyncImage(model = selectedImage, contentDescription = null, modifier = Modifier.size(70.dp).clip(RoundedCornerShape(13.dp)), contentScale = ContentScale.Crop)
                }
                IconButton(onClick = onRemoveImage, modifier = Modifier.align(Alignment.TopEnd).size(20.dp).offset(x = 5.dp, y = (-5).dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.error) { Icon(Icons.Rounded.Close, null, Modifier.padding(3.dp).size(11.dp), tint = Color.White) }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 180.dp),
            shape    = RoundedCornerShape(26.dp),
            color    = surfBg,
            border   = BorderStroke(1.dp, if (hasContent) MaterialTheme.colorScheme.primary.copy(.18f) else MaterialTheme.colorScheme.outlineVariant.copy(.25f)),
        ) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
                if (supportsVision) {
                    IconButton(onClick = onAttach, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Rounded.AddPhotoAlternate, "Attach", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                TextField(
                    value = inputText, onValueChange = onInputChange,
                    placeholder = { Text("Ask anything…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.4f), style = MaterialTheme.typography.bodyLarge) },
                    modifier = Modifier.weight(1f),
                    colors   = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent, focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor  = Color.Transparent,
                    ),
                    maxLines = 6, textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!isLoading) onSend() }),
                )
                Crossfade(targetState = isLoading, modifier = Modifier.padding(4.dp), label = "ss") { loading ->
                    if (loading) {
                        IconButton(onClick = onCancel, modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape)) {
                            Icon(Icons.Rounded.Stop, "Cancel", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        val btnColor by animateColorAsState(
                            if (hasContent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(.5f), label = "bc")
                        IconButton(
                            onClick  = onSend,
                            enabled  = hasContent,
                            modifier = Modifier.size(44.dp).background(btnColor, CircleShape),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, "Send",
                                tint     = if (hasContent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = inputText.length > 200) {
            Text("${inputText.length} chars", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline.copy(.45f), modifier = Modifier.align(Alignment.End).padding(end = 8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Message list
// ─────────────────────────────────────────────────────────────

@Composable
private fun ChatMessageList(
    messages       : List<AiMessage>,
    streamingText  : String,
    isLoading      : Boolean,
    error          : String?,
    listState      : LazyListState,
    currentConfig  : AiConfig?,
    performanceMode: Boolean,
    onScrollBottom : () -> Unit,
    onRetrySync    : () -> Unit,
) {
    val isAtBottom by remember {
        derivedStateOf {
            val v = listState.layoutInfo.visibleItemsInfo; val l = v.lastOrNull() ?: return@derivedStateOf true
            l.index >= listState.layoutInfo.totalItemsCount - 2
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state   = listState,
            modifier = Modifier.fillMaxSize().let {
                if (!performanceMode) it.drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, .04f to Color.Black, .96f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                } else it
            },
            contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(message = msg, currentConfig = currentConfig, performanceMode = performanceMode)
            }
            if (isLoading && streamingText.isNotEmpty()) {
                item(key = "streaming") { StreamingBubble(text = streamingText, config = currentConfig) }
            }
            if (isLoading && streamingText.isEmpty()) {
                item(key = "typing") { TypingIndicator(config = currentConfig) }
            }
            if (error != null) {
                item(key = "error") {
                    ErrorMessage(error = error, onRetrySync = if (error.contains("key", true) || error.contains("sync", true) || error.contains("auth", true) || error.contains("Retry", true)) onRetrySync else null)
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        AnimatedVisibility(
            visible  = !isAtBottom && messages.isNotEmpty(),
            enter    = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit     = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        ) {
            FilledTonalIconButton(
                onClick  = onScrollBottom,
                modifier = Modifier.size(36.dp),
                shape    = CircleShape,
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Chat bubble  — M3 Expressive design
// ─────────────────────────────────────────────────────────────

@Composable
fun ChatBubble(
    message       : AiMessage,
    currentConfig : AiConfig?,
    performanceMode: Boolean,
) {
    val clipboard = LocalClipboardManager.current
    val scope     = rememberCoroutineScope()
    val vibration = LocalVibrationManager.current
    var isCopied  by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    val segments = remember(message.text) { parseMarkdownToSegments(message.text) }

    val scale = remember { Animatable(if (performanceMode) 1f else .9f) }
    val alpha = remember { Animatable(if (performanceMode) 1f else 0f) }
    LaunchedEffect(message.id) {
        if (!performanceMode) {
            launch { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
            launch { alpha.animateTo(1f, tween(200)) }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
            .graphicsLayer(scaleX = scale.value, scaleY = scale.value, alpha = alpha.value),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
    ) {
        if (message.isUser) {
            // ── User bubble — pill shape, primary color ────────────────────
            Surface(
                color    = MaterialTheme.colorScheme.primary,
                shape    = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 5.dp),
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { vibration?.vibrateLongClick(); showActions = true }) },
            ) {
                Text(message.text, Modifier.padding(horizontal = 16.dp, vertical = 11.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary, lineHeight = 22.sp)
            }
        } else {
            // ── AI bubble — glass card ────────────────────────────────────
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth(0.92f)) {
                AiAvatar(config = currentConfig, size = 28.dp)
                Surface(
                    color    = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape    = RoundedCornerShape(topStart = 5.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                    border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(.2f)),
                    modifier = Modifier.weight(1f).pointerInput(Unit) { detectTapGestures(onLongPress = { vibration?.vibrateLongClick(); showActions = true }) },
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { segments.forEach { seg -> MarkdownSegment(seg) } }
                        }
                        // Copy row
                        Row(Modifier.padding(top = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            AnimatedVisibility(visible = isCopied) {
                                Text("Copied", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), modifier = Modifier.padding(end = 5.dp))
                            }
                            IconButton(
                                onClick  = { vibration?.vibrateClick(); clipboard.setText(AnnotatedString(message.text)); isCopied = true; scope.launch { delay(2200); isCopied = false } },
                                modifier = Modifier.size(26.dp),
                            ) {
                                Icon(if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, null, Modifier.size(13.dp),
                                    tint = if (isCopied) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(.35f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showActions) {
        MessageActionsSheet(message.text, message.isUser, { showActions = false },
            { clipboard.setText(AnnotatedString(message.text)); isCopied = true; scope.launch { delay(2200); isCopied = false }; showActions = false })
    }
}

// ─────────────────────────────────────────────────────────────
//  Streaming bubble
// ─────────────────────────────────────────────────────────────

@Composable
private fun StreamingBubble(text: String, config: AiConfig?) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth(.92f).padding(vertical = 1.dp)) {
        AiAvatar(config, 28.dp)
        Surface(
            color    = MaterialTheme.colorScheme.surfaceContainerLow,
            shape    = RoundedCornerShape(5.dp, 20.dp, 20.dp, 20.dp),
            border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(.2f)),
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                val segs = remember(text) { parseMarkdownToSegments(text) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { segs.forEach { seg -> MarkdownSegment(seg) } }
                val inf = rememberInfiniteTransition(label = "cur")
                val ca by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), "ca")
                Box(Modifier.size(2.dp, 15.dp).alpha(ca).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp)))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  AI Avatar
// ─────────────────────────────────────────────────────────────

@Composable
private fun AiAvatar(config: AiConfig?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.size(size), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
        Box(contentAlignment = Alignment.Center) {
            val icon = if (config != null) getIconForConfig(config.iconRes, config.provider) else Icons.Rounded.AutoAwesome
            Icon(icon, null, Modifier.size(size * .5f), tint = Color.White)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Actions sheet
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionsSheet(messageText: String, isUser: Boolean, onDismiss: () -> Unit, onCopy: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(32.dp, 3.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(.18f), CircleShape)) } },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp).navigationBarsPadding()) {
            Text("Message", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 14.dp))
            ActionSheetItem(Icons.Rounded.ContentCopy, "Copy message", onCopy)
            if (!isUser) ActionSheetItem(Icons.Rounded.Share, "Share") { onDismiss() }
        }
    }
}

@Composable
private fun ActionSheetItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Typing indicator
// ─────────────────────────────────────────────────────────────

@Composable
fun TypingIndicator(config: AiConfig?) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(vertical = 2.dp)) {
        AiAvatar(config, 28.dp)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(5.dp, 20.dp, 20.dp, 20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(.2f))) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                val inf = rememberInfiniteTransition(label = "t")
                val d1 by inf.animateFloat(.15f, 1f, infiniteRepeatable(tween(500, delayMillis = 0),   RepeatMode.Reverse), "d1")
                val d2 by inf.animateFloat(.15f, 1f, infiniteRepeatable(tween(500, delayMillis = 150), RepeatMode.Reverse), "d2")
                val d3 by inf.animateFloat(.15f, 1f, infiniteRepeatable(tween(500, delayMillis = 300), RepeatMode.Reverse), "d3")
                listOf(d1, d2, d3).forEach { a -> Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = a))) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Empty state
// ─────────────────────────────────────────────────────────────

@Composable
fun EmptyChatState(onSuggestionClick: (String) -> Unit) {
    val inf   = rememberInfiniteTransition(label = "e")
    val pulse by inf.animateFloat(.92f, 1.08f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "p")
    val rot   by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), "r")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // Hero orb
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(120.dp).scale(pulse).background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(.12f), Color.Transparent)), CircleShape))
            Box(Modifier.size(80.dp).rotate(rot).background(Brush.sweepGradient(listOf(MaterialTheme.colorScheme.primary.copy(.06f), MaterialTheme.colorScheme.secondary.copy(.06f), Color.Transparent)), CircleShape))
            Surface(Modifier.size(72.dp), CircleShape, MaterialTheme.colorScheme.primaryContainer.copy(.5f), border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(.2f))) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("How can I help you?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Powered by Groq · Gemini · Claude · OpenAI", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.5f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(36.dp))

        val suggestions = listOf(
            Triple(Icons.Rounded.Code,        "Write a Kotlin function",   MaterialTheme.colorScheme.primary),
            Triple(Icons.Rounded.Calculate,   "Explain a math concept",    MaterialTheme.colorScheme.secondary),
            Triple(Icons.Rounded.Lightbulb,   "Brainstorm ideas for me",   MaterialTheme.colorScheme.tertiary),
            Triple(Icons.Rounded.Description, "Summarize a long text",     MaterialTheme.colorScheme.primary),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
            suggestions.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (icon, text, color) ->
                        SuggestionChip(icon = icon, text = text, accentColor = color, modifier = Modifier.weight(1f), onClick = { onSuggestionClick(text) })
                    }
                }
            }
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
fun SuggestionChip(
    icon       : ImageVector,
    text       : String,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier   : Modifier = Modifier,
    onClick    : () -> Unit = {},
) {
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(16.dp),
        color    = accentColor.copy(.07f),
        border   = BorderStroke(1.dp, accentColor.copy(.14f)),
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(16.dp), tint = accentColor)
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Error message
// ─────────────────────────────────────────────────────────────

@Composable
fun ErrorMessage(error: String, onRetrySync: (() -> Unit)? = null) {
    Surface(
        color    = MaterialTheme.colorScheme.errorContainer.copy(.8f),
        shape    = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(.15f)),
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            if (onRetrySync != null) {
                Spacer(Modifier.height(8.dp))
                Surface(onClick = onRetrySync, color = MaterialTheme.colorScheme.error, shape = RoundedCornerShape(9.dp), modifier = Modifier.align(Alignment.End)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Rounded.Sync, null, Modifier.size(13.dp), tint = Color.White)
                        Text("RETRY SYNC", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = .5.sp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  History drawer
// ─────────────────────────────────────────────────────────────

@Composable
fun AiHistoryDrawer(chats: List<AiChat>, currentChatId: Int?, onChatSelect: (Int) -> Unit, onNewChat: () -> Unit, onDeleteChat: (AiChat) -> Unit) {
    var q by remember { mutableStateOf("") }
    val filtered = remember(chats, q) { if (q.isBlank()) chats else chats.filter { it.title.contains(q, true) } }
    val grouped  = remember(filtered) { filtered.groupBy { it.chatGroup() }.toSortedMap(compareBy { it.ordinal }) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text("CONVERSATIONS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onNewChat, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                Icon(Icons.AutoMirrored.Rounded.NoteAdd, null, Modifier.size(17.dp)); Spacer(Modifier.width(8.dp))
                Text("NEW CONVERSATION", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium, letterSpacing = .5.sp)
            }
            Spacer(Modifier.height(10.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(.45f))
                    BtField(q, { q = it }, Modifier.weight(1f))
                    if (q.isNotEmpty()) IconButton(onClick = { q = "" }, Modifier.size(18.dp)) { Icon(Icons.Rounded.Close, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(.45f)) }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(.25f))
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            if (grouped.isEmpty()) {
                item { Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(44.dp).alpha(.15f)); Spacer(Modifier.height(10.dp)); Text("No conversations yet", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall) } }
            } else {
                grouped.forEach { (group, groupChats) ->
                    item(key = "g_${group.name}") { Text(group.label().uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.45f), letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 3.dp)) }
                    items(groupChats, key = { it.id }) { chat ->
                        val sel = chat.id == currentChatId
                        Surface(onClick = { onChatSelect(chat.id) }, shape = RoundedCornerShape(13.dp), color = if (sel) MaterialTheme.colorScheme.primaryContainer.copy(.55f) else Color.Transparent, border = if (sel) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(.15f)) else null, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Icon(if (sel) Icons.Rounded.ChatBubble else Icons.Rounded.ChatBubbleOutline, null, tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(.4f), modifier = Modifier.size(16.dp))
                                Text(chat.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                IconButton(onClick = { onDeleteChat(chat) }, Modifier.size(26.dp)) { Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(.3f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun BtField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    androidx.compose.foundation.text.BasicTextField(value = value, onValueChange = onChange, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        decorationBox = { inner -> if (value.isEmpty()) Text("Search history…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.38f)); inner() }, modifier = modifier)
}

// ─────────────────────────────────────────────────────────────
//  Dialogs
// ─────────────────────────────────────────────────────────────

@Composable
fun ModernAiDialog(title: String, icon: ImageVector, iconColor: Color, description: String, supportingText: String, primaryButtonText: String, onPrimaryClick: () -> Unit, secondaryButtonText: String? = null, onSecondaryClick: (() -> Unit)? = null, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(24.dp).fillMaxWidth(), RoundedCornerShape(28.dp), MaterialTheme.colorScheme.surfaceContainerHigh, border = BorderStroke(1.dp, iconColor.copy(.1f))) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val inf = rememberInfiniteTransition(label = "di"); val s by inf.animateFloat(1f, 1.12f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), "s")
                Surface(shape = CircleShape, color = iconColor.copy(.1f), modifier = Modifier.size(72.dp)) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(34.dp).scale(s)) } }
                Spacer(Modifier.height(20.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = iconColor, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(10.dp))
                Text(description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 24.sp)
                Spacer(Modifier.height(8.dp))
                Text(supportingText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.65f))
                Spacer(Modifier.height(28.dp))
                Button(onClick = onPrimaryClick, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = iconColor)) { Text(primaryButtonText, fontWeight = FontWeight.Bold) }
                if (secondaryButtonText != null && onSecondaryClick != null) { Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = onSecondaryClick, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Text(secondaryButtonText, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) } }
                TextButton(onClick = onDismiss, Modifier.padding(top = 4.dp)) { Text("MAYBE LATER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline.copy(.45f)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsDialog(
    state: AiSettingsUiState, savedConfigs: List<AiConfig>, onDismiss: () -> Unit,
    onProviderChange: (String) -> Unit, onApiKeyChange: (String) -> Unit, onModelChange: (String) -> Unit, onIconChange: (String) -> Unit,
    onSave: () -> Unit, onSaveConfig: (String) -> Unit, onDeleteConfig: (AiConfig) -> Unit, onEditConfig: (AiConfig) -> Unit,
    @Suppress("UNUSED_PARAMETER") onMoveConfig: (Int, Int) -> Unit, onTest: () -> Unit, onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    var configName     by remember(state.editingConfig) { mutableStateOf(state.editingConfig?.name ?: "") }
    var showConfigSave by remember { mutableStateOf(false) }
    var showTutorial   by remember { mutableStateOf(false) }
    var showModelMenu  by remember { mutableStateOf(false) }
    var activeTab      by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Text("AI Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(12.dp))
                TabRow(selectedTabIndex = activeTab, containerColor = Color.Transparent, indicator = { tp -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tp[activeTab]), color = MaterialTheme.colorScheme.primary) }) {
                    listOf("SETUP", "CONFIGS").forEachIndexed { i, l -> Tab(selected = activeTab == i, onClick = { activeTab = i }) { Text(l, Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) } }
                }
            }
        },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                if (activeTab == 0) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(AiSettingsHelper.disclaimerText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        SettingsSection("Provider") {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf("Gemini", "ChatGPT", "Groq", "Claude", "DeepSeek", "OpenRouter").forEach { p ->
                                    FilterChip(selected = state.provider == p, onClick = { onProviderChange(p) }, label = { Text(p, fontWeight = FontWeight.Medium) }, shape = RoundedCornerShape(10.dp))
                                }
                            }
                        }
                        SettingsSection("Model") {
                            Box {
                                Surface(onClick = { showModelMenu = true }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(.5f)), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(state.selectedModel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Icon(Icons.Rounded.UnfoldMore, null, Modifier.size(18.dp))
                                    }
                                }
                                DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }, modifier = Modifier.fillMaxWidth(.72f)) {
                                    AiSettingsHelper.getModels(state.provider).forEach { model -> DropdownMenuItem(text = { Text(model) }, onClick = { onModelChange(model); showModelMenu = false }) }
                                }
                            }
                        }
                        SettingsSection("API Key") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = { showTutorial = true }, Modifier.size(20.dp)) { Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary) }
                                    Text("How to get one?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.55f))
                                }
                                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AiSettingsHelper.getApiKeyUrl(state.provider)))) }) { Text("GET KEY →", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black) }
                            }
                            OutlinedTextField(
                                value = state.apiKey, onValueChange = onApiKeyChange, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                                placeholder = { Text(AiSettingsHelper.getApiKeyPlaceholder(state.provider), style = MaterialTheme.typography.bodySmall) },
                                isError = !state.isKeyValid,
                                supportingText = {
                                    if (!state.isKeyValid) Text("Invalid key format", color = MaterialTheme.colorScheme.error)
                                    else if (state.apiKey.isEmpty()) {
                                        if (state.isRemoteKeyAvailable) Text("✓ Using Toolz Default Key", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        else Text("Add your own key for guaranteed availability.", color = MaterialTheme.colorScheme.outline)
                                    }
                                },
                                trailingIcon = { if (state.apiKey.isNotEmpty()) IconButton(onClick = { onApiKeyChange("") }) { Icon(Icons.Rounded.Close, null, Modifier.size(17.dp)) } }
                            )
                        }
                        AnimatedVisibility(visible = state.testResult != null) {
                            val ok = state.testResult?.startsWith("✓") == true
                            Surface(color = if (ok) Color(0xFF4CAF50).copy(.1f) else MaterialTheme.colorScheme.errorContainer.copy(.5f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null, tint = if (ok) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                    Text(state.testResult ?: "", style = MaterialTheme.typography.bodySmall, color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onTest, Modifier.weight(1f).height(46.dp), enabled = !state.isTesting && state.isKeyValid, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                                if (state.isTesting && state.testResult?.contains("Syncing") != true) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else { Icon(Icons.Rounded.BugReport, null, Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text("TEST", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                            }
                            Button(onClick = { showConfigSave = true }, Modifier.weight(1f).height(46.dp), enabled = state.isKeyValid, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)) {
                                Icon(Icons.Rounded.Save, null, Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(if (state.editingConfig != null) "UPDATE" else "SAVE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = onRefresh, Modifier.size(46.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)), enabled = !state.isTesting) {
                                if (state.isTesting && state.testResult?.contains("Syncing") == true) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.Sync, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        AnimatedVisibility(visible = showConfigSave || state.editingConfig != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(value = configName, onValueChange = { configName = it }, Modifier.fillMaxWidth(), label = { Text("Configuration name") }, shape = RoundedCornerShape(14.dp), singleLine = true)
                                Text("Agent Avatar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.6f))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    listOf("AUTO","GEMINI","CHATGPT","GROQ","CLAUDE","DEEPSEEK","BOT","SPARKLE","BRAIN").forEach { ik ->
                                        val sel = state.selectedIcon == ik
                                        Surface(onClick = { onIconChange(ik) }, modifier = Modifier.size(44.dp), shape = RoundedCornerShape(13.dp), color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest, border = if (sel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                                            Box(contentAlignment = Alignment.Center) { Icon(getIconForConfig(ik, state.provider), null, Modifier.size(21.dp), tint = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                    }
                                }
                                Button(onClick = { if (configName.isNotBlank()) { onSaveConfig(configName); showConfigSave = false; configName = "" } }, Modifier.fillMaxWidth().height(46.dp), enabled = configName.isNotBlank(), shape = RoundedCornerShape(14.dp)) { Text("Apply & Save Configuration", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(savedConfigs) { _, config ->
                            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), MaterialTheme.colorScheme.surfaceContainerHighest, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(.25f))) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(Modifier.size(38.dp), CircleShape, MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(getIconForConfig(config.iconRes, config.provider), null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } }
                                    Column(Modifier.weight(1f)) { Text(config.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium); Text("${config.provider} · ${config.model}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.55f)) }
                                    IconButton(onClick = { onEditConfig(config); activeTab = 0 }) { Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
                                    IconButton(onClick = { onDeleteConfig(config) }) { Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error.copy(.65f)) }
                                }
                            }
                        }
                        if (savedConfigs.isEmpty()) {
                            item { Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.FolderOff, null, Modifier.size(42.dp).alpha(.15f)); Spacer(Modifier.height(10.dp)); Text("No saved configurations", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall) } }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onSave, enabled = state.isKeyValid, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(46.dp)) { Text("Save as Default", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (showTutorial) GuideDialog { showTutorial = false }
}

@Composable
private fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.6f))
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideDialog(onDismiss: () -> Unit) {
    val providers  = listOf("Gemini", "ChatGPT", "Groq", "Claude", "DeepSeek", "OpenRouter")
    val pagerState = rememberPagerState { providers.size }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(28.dp),
        title = { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text("SETUP GUIDE", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, letterSpacing = 1.5.sp); Text("Swipe for each provider", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } },
        text = {
            Column(Modifier.fillMaxWidth().height(340.dp)) {
                HorizontalPager(state = pagerState, Modifier.fillMaxWidth().weight(1f)) { page ->
                    val p = providers[page]
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(Modifier.size(46.dp), CircleShape, MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Icon(getIconForConfig("AUTO", p), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } }
                        Spacer(Modifier.height(10.dp)); Text(p, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                        Text(AiSettingsHelper.detailedInfo[p] ?: "", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(.55f), modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(.3f))
                        (AiSettingsHelper.tutorials[p] ?: emptyList()).forEachIndexed { i, step ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                                Text("${i + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp))
                                Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                    providers.indices.forEach { i -> Box(Modifier.padding(3.dp).clip(CircleShape).size(if (pagerState.currentPage == i) 8.dp else 5.dp).background(if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(.2f))) }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) { Text("Got it", fontWeight = FontWeight.Bold) } }
    )
}