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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.parseMarkdownToSegments
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  Design Constants & Utilities
// ─────────────────────────────────────────────────────────────

object AiDesign {
    val CornerLarge = 32.dp
    val CornerMedium = 24.dp
    val CornerSmall = 16.dp
    val ChatBubblePadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
    
    // Glossy glass effect
    fun glassModifier(color: Color = Color.White.copy(0.08f)) = Modifier
        .background(color, RoundedCornerShape(CornerMedium))
        .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(CornerMedium))
}

// ─────────────────────────────────────────────────────────────
//  Expressive Background (Animated Gradient Mesh)
// ─────────────────────────────────────────────────────────────

@Composable
fun ExpressiveBackground(performanceMode: Boolean) {
    if (performanceMode) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0F0F0F)))
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "mesh")
        
        val color1 = MaterialTheme.colorScheme.primary
        val color2 = MaterialTheme.colorScheme.tertiary
        val color3 = MaterialTheme.colorScheme.secondary
        val darkBg = Color(0xFF0A0A0A)

        val animOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse),
            label = "offset"
        )

        Box(Modifier.fillMaxSize().background(darkBg)) {
            Canvas(modifier = Modifier.fillMaxSize().blur(80.dp)) {
                val width = size.width
                val height = size.height
                
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color1.copy(0.4f), Color.Transparent),
                        center = Offset(width * (0.2f + 0.6f * animOffset), height * (0.3f + 0.4f * (1f - animOffset))),
                        radius = width * 0.8f
                    )
                )
                
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color2.copy(0.3f), Color.Transparent),
                        center = Offset(width * (0.8f - 0.5f * animOffset), height * (0.7f - 0.3f * animOffset)),
                        radius = width * 0.7f
                    )
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color3.copy(0.25f), Color.Transparent),
                        center = Offset(width * (0.5f + 0.3f * Math.sin(animOffset.toDouble() * Math.PI).toFloat()), height * (0.1f + 0.8f * animOffset)),
                        radius = width * 0.9f
                    )
                )
            }
            
            // Subtle noise/grain overlay
            Box(Modifier.fillMaxSize().alpha(0.03f).background(Color.Black))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Main Screen
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: AiAssistantViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val settingsUiState by viewModel.settingsUiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val vibration = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showQuotaDialog by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }

    // Screen entrance animation state
    var isStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isStarted = true
    }

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

    val configIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.updateCustomIcon(it) }
    }

    LaunchedEffect(uiState.messages.size, uiState.isLoading) {
        if (uiState.messages.isNotEmpty()) scope.launch { listState.animateScrollToItem(uiState.messages.size - 1) }
    }
    LaunchedEffect(uiState.streamingText) {
        if (uiState.streamingText.isNotEmpty() && uiState.messages.isNotEmpty()) listState.scrollToItem(uiState.messages.size - 1)
    }
    LaunchedEffect(uiState.quotaExceeded) { if (uiState.quotaExceeded) showQuotaDialog = true }

    // Dialogs / Sheets
    if (showSettings) {
        AiSettingsDialog(state = settingsUiState, savedConfigs = uiState.savedConfigs, onDismiss = { showSettings = false },
            onProviderChange = viewModel::updateProvider, onApiKeyChange = viewModel::updateApiKey,
            onModelChange = viewModel::updateModel, onIconChange = viewModel::updateIcon,
            onCustomIconClick = { configIconPicker.launch("image/*") },
            onSave = { viewModel.onSettingsSaveRequest(); showSettings = false },
            onSaveConfig = viewModel::saveConfig, onDeleteConfig = viewModel::deleteConfig,
            onEditConfig = viewModel::editConfig, onMoveConfig = viewModel::moveConfig,
            onTest = viewModel::testConnection, onRefresh = viewModel::refreshRemoteKeys,
            performanceMode = performanceMode)
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
        ModernAiDialog(title = "NEW CHAT REQUIRED", icon = Icons.Rounded.AutoAwesomeMotion, iconColor = MaterialTheme.colorScheme.primary,
            description = "Switching to ${uiState.pendingConfig?.model}?",
            supportingText = "To ensure consistency, a new chat will be created with the new model. Your current chat is saved in history.",
            primaryButtonText = "START NEW CHAT", onPrimaryClick = viewModel::confirmConfigSwitch,
            secondaryButtonText = "KEEP CURRENT", onSecondaryClick = viewModel::cancelConfigSwitch, onDismiss = viewModel::cancelConfigSwitch)
    }

    if (showSummary) {
        ChatSummarySheet(
            summary = uiState.chatSummary,
            isSummarizing = uiState.isSummarizing,
            onDismiss = { showSummary = false; viewModel.clearChatSummary() },
            onRefresh = { viewModel.summarizeChat() },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF1A1A1A),
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
            topBar = {
                AnimatedVisibility(
                    visible = isStarted,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut()
                ) {
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
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = isStarted,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
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
                    )
                }
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
                        targetState = (uiState.messages.isEmpty() && !uiState.isLoading) to isStarted,
                        transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(400)) },
                        label = "chat_content",
                    ) { (showEmpty, started) ->
                        if (started) {
                            if (showEmpty) {
                                EmptyChatStateRedesign(performanceMode = performanceMode, onSuggestionClick = { inputText = it })
                            } else {
                                ChatMessageList(
                                    messages = uiState.messages,
                                    streamingText = uiState.streamingText,
                                    isLoading = uiState.isLoading,
                                    error = uiState.error,
                                    listState = listState,
                                    currentConfig = uiState.savedConfigs.find { it.provider == settingsUiState.provider && it.model == settingsUiState.selectedModel },
                                    performanceMode = performanceMode,
                                    onScrollBottom = { scope.launch { listState.animateScrollToItem(uiState.messages.size.coerceAtLeast(1) - 1) } },
                                    onRetrySync = { vibration?.vibrateTick(); viewModel.retrySyncKeys() },
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

    Column(
        Modifier
            .fillMaxWidth()
            .then(if (performanceMode) Modifier.background(Color(0xFF121212)) else Modifier.statusBarsPadding())
    ) {
        CenterAlignedTopAppBar(
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.animateContentSize()) {
                    val currentChat = uiState.chats.find { it.id == uiState.currentChatId }
                    val hasMessages = uiState.messages.isNotEmpty()

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnimatedContent(
                            targetState = currentChat?.title ?: "AI Assistant",
                            transitionSpec = { (slideInVertically { it / 2 } + fadeIn()).togetherWith(slideOutVertically { -it / 2 } + fadeOut()) },
                            label = "title",
                        ) { title ->
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp),
                            )
                        }
                        if (hasMessages) {
                            IconButton(onClick = onRefreshTitle, modifier = Modifier.size(28.dp)) {
                                if (uiState.isGeneratingTitle && !performanceMode) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                } else {
                                    Icon(Icons.Rounded.AutoAwesome, "Refresh title", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(.9f))
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val statusColor by animateColorAsState(
                            when {
                                uiState.keysUnavailable -> Color(0xFFFF5252)
                                uiState.isLoading -> MaterialTheme.colorScheme.primary
                                else -> Color(0xFF4CAF50)
                            }, label = "status"
                        )
                        Box(Modifier.size(6.dp).background(statusColor, CircleShape))

                        Text(
                            "${settingsUiState.provider} · ${settingsUiState.selectedModel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
                }
            },
            actions = {
                AnimatedVisibility(visible = uiState.messages.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    IconButton(onClick = onSummarize) { Icon(Icons.Rounded.Summarize, "Summary", tint = Color.White) }
                }
                IconButton(onClick = onHistory) { Icon(Icons.Rounded.History, "History", tint = Color.White) }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings", tint = Color.White) }
            },
        )

        // Expressive Config Chips
        AnimatedVisibility(visible = uiState.savedConfigs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.savedConfigs) { config ->
                    val isSelected = settingsUiState.provider == config.provider && settingsUiState.selectedModel == config.model && settingsUiState.apiKey == config.apiKey
                    val bgColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(0.05f), label = "chipBg")
                    val contentColor by animateColorAsState(if (isSelected) Color.White else Color.White.copy(0.7f), label = "chipContent")

                    Surface(
                        onClick = { onConfigSelect(config) },
                        shape = RoundedCornerShape(AiDesign.CornerSmall),
                        color = bgColor,
                        border = if (!isSelected) BorderStroke(1.dp, Color.White.copy(0.1f)) else null,
                        modifier = Modifier.animateContentSize()
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AiAvatar(config, 20.dp, performanceMode = true)
                            Text(config.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = contentColor)
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(0.1f))
    }
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
) {
    val hasContent = inputText.isNotBlank() || selectedImage != null
    val supportsMedia = supportsVision || supportsFiles
    var showMediaMenu by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(visible = selectedImage != null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Box(Modifier.padding(start = 12.dp)) {
                Surface(
                    shape = RoundedCornerShape(AiDesign.CornerMedium),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    shadowElevation = 8.dp,
                    color = Color(0xFF1A1A1A)
                ) {
                    AsyncImage(
                        model = selectedImage,
                        contentDescription = null,
                        modifier = Modifier.size(90.dp).clip(RoundedCornerShape(AiDesign.CornerMedium)),
                        contentScale = ContentScale.Crop
                    )
                }
                IconButton(
                    onClick = onRemoveImage,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp).size(28.dp)
                ) {
                    Surface(shape = CircleShape, color = Color(0xFFFF5252), shadowElevation = 4.dp) {
                        Icon(Icons.Rounded.Close, null, Modifier.padding(6.dp).size(14.dp), tint = Color.White)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 54.dp),
                shape = RoundedCornerShape(26.dp),
                color = Color.White.copy(if (performanceMode) 0.12f else 0.08f),
                border = BorderStroke(1.dp, if (hasContent) MaterialTheme.colorScheme.primary.copy(0.4f) else Color.White.copy(0.1f)),
            ) {
                Row(
                    Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (supportsMedia) {
                        Box {
                            IconButton(onClick = { showMediaMenu = true }, modifier = Modifier.size(46.dp)) {
                                Icon(Icons.Rounded.Add, "Attach", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                            DropdownMenu(
                                expanded = showMediaMenu,
                                onDismissRequest = { showMediaMenu = false },
                                offset = androidx.compose.ui.unit.DpOffset(0.dp, (-8).dp),
                                shape = RoundedCornerShape(20.dp),
                                containerColor = Color(0xFF222222)
                            ) {
                                if (supportsVision) {
                                    DropdownMenuItem(
                                        text = { Text("Photo", fontWeight = FontWeight.Medium, color = Color.White) },
                                        onClick = { showMediaMenu = false; onAttach() },
                                        leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(20.dp), tint = Color.White.copy(0.7f)) }
                                    )
                                }
                                if (supportsFiles) {
                                    DropdownMenuItem(
                                        text = { Text("Document", fontWeight = FontWeight.Medium, color = Color.White) },
                                        onClick = { showMediaMenu = false },
                                        leadingIcon = { Icon(Icons.Rounded.Description, null, Modifier.size(20.dp), tint = Color.White.copy(0.7f)) }
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }

                    TextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        placeholder = { Text("Ask anything...", color = Color.White.copy(.4f)) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        maxLines = 6,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (!isLoading && hasContent) onSend() }),
                    )
                }
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.height(54.dp)) {
                AnimatedContent(targetState = isLoading, label = "send_btn") { loading ->
                    if (loading) {
                        FilledIconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(54.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFFF5252).copy(0.2f))
                        ) {
                            Icon(Icons.Rounded.Stop, "Cancel", tint = Color(0xFFFF5252))
                        }
                    } else {
                        val scale by animateFloatAsState(if (hasContent) 1f else 0.9f, label = "scale")
                        val containerColor by animateColorAsState(if (hasContent) MaterialTheme.colorScheme.primary else Color.White.copy(0.1f), label = "btnColor")
                        val contentColor by animateColorAsState(if (hasContent) Color.White else Color.White.copy(0.3f), label = "btnContent")

                        FilledIconButton(
                            onClick = { if (hasContent) onSend() },
                            enabled = hasContent,
                            modifier = Modifier.size(54.dp).scale(scale),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = containerColor,
                                contentColor = contentColor
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, "Send", modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Chat Bubble Redesign
// ─────────────────────────────────────────────────────────────

@Composable
fun ChatBubble(
    message: AiMessage,
    currentConfig: AiConfig?,
    performanceMode: Boolean,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val vibration = LocalVibrationManager.current
    var isCopied by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    val segments = remember(message.text) { parseMarkdownToSegments(message.text) }

    val entranceScale = remember { Animatable(if (performanceMode) 1f else 0.85f) }
    val entranceAlpha = remember { Animatable(if (performanceMode) 1f else 0f) }

    LaunchedEffect(message.id) {
        if (!performanceMode) {
            launch { entranceScale.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) }
            launch { entranceAlpha.animateTo(1f, tween(300)) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = entranceScale.value
                scaleY = entranceScale.value
                alpha = entranceAlpha.value
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
    ) {
        if (message.isUser) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(
                    topStart = AiDesign.CornerMedium,
                    topEnd = AiDesign.CornerMedium,
                    bottomStart = AiDesign.CornerMedium,
                    bottomEnd = 6.dp
                ),
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { vibration?.vibrateLongClick(); showActions = true }) },
                shadowElevation = 4.dp
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(AiDesign.ChatBubblePadding),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    lineHeight = 24.sp
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(0.95f)
            ) {
                AiAvatar(config = currentConfig, size = 34.dp, modifier = Modifier.offset(y = 4.dp), performanceMode = performanceMode)

                Surface(
                    color = Color.White.copy(if (performanceMode) 0.12f else 0.08f),
                    shape = RoundedCornerShape(
                        topStart = 6.dp,
                        topEnd = AiDesign.CornerMedium,
                        bottomStart = AiDesign.CornerMedium,
                        bottomEnd = AiDesign.CornerMedium
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(0.1f)),
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { vibration?.vibrateLongClick(); showActions = true }) },
                ) {
                    Column(Modifier.padding(AiDesign.ChatBubblePadding)) {
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                segments.forEach { seg -> 
                                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                                        MarkdownSegment(seg)
                                    }
                                }
                            }
                        }

                        Row(
                            Modifier.padding(top = 10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(visible = isCopied) {
                                Text("Copied", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), modifier = Modifier.padding(end = 8.dp))
                            }
                            IconButton(
                                onClick = {
                                    vibration?.vibrateClick();
                                    clipboard.setText(AnnotatedString(message.text));
                                    isCopied = true;
                                    scope.launch { delay(2000); isCopied = false }
                                },
                                modifier = Modifier.size(28.dp).background(Color.White.copy(0.1f), CircleShape),
                            ) {
                                Icon(
                                    imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (isCopied) Color(0xFF4CAF50) else Color.White.copy(0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showActions) {
        MessageActionsSheet(message.text, message.isUser, { showActions = false },
            { clipboard.setText(AnnotatedString(message.text)); isCopied = true; scope.launch { delay(2000); isCopied = false }; showActions = false })
    }
}

// ─────────────────────────────────────────────────────────────
//  Empty State Redesign
// ─────────────────────────────────────────────────────────────

@Composable
fun EmptyChatStateRedesign(performanceMode: Boolean, onSuggestionClick: (String) -> Unit) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            if (!performanceMode) {
                val inf = rememberInfiniteTransition(label = "hero")
                val pulse by inf.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), "pulse")
                val rotate by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(40000, easing = LinearEasing)), "rot")

                Box(Modifier.size(200.dp).scale(pulse).alpha(0.1f).background(Brush.radialGradient(listOf(primaryColor, Color.Transparent)), CircleShape))
                Box(Modifier.size(160.dp).rotate(rotate).alpha(0.15f).drawBehind {
                    drawCircle(
                        Brush.sweepGradient(listOf(primaryColor, tertiaryColor, secondaryColor, primaryColor)),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                })
            }

            Surface(
                Modifier.size(90.dp),
                shape = CircleShape,
                color = primaryColor,
                shadowElevation = 12.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(42.dp), tint = Color.White)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("AI Assistant", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text("Ready to help you with anything.", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(.6f))

        Spacer(Modifier.height(48.dp))

        // Updated Suggestions: Only 2 and better prompts
        val suggestions = listOf(
            Triple(Icons.Rounded.TipsAndUpdates, "Creative writing: Tell me a story about a futuristic city.", primaryColor),
            Triple(Icons.Rounded.Code, "Coding help: How do I implement a recursive function in Kotlin?", secondaryColor),
        )

        Column(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            suggestions.forEach { (icon, text, color) ->
                Surface(
                    onClick = { onSuggestionClick(text) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AiDesign.CornerMedium),
                    color = Color.White.copy(0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(0.1f))
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(40.dp).background(color.copy(.15f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(icon, null, Modifier.size(20.dp), tint = color)
                        }
                        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = Color.White.copy(.3f))
                    }
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

// ─────────────────────────────────────────────────────────────
//  ChatMessageList & Streaming Components
// ─────────────────────────────────────────────────────────────

@Composable
private fun ChatMessageList(
    messages: List<AiMessage>,
    streamingText: String,
    isLoading: Boolean,
    error: String?,
    listState: LazyListState,
    currentConfig: AiConfig?,
    performanceMode: Boolean,
    onScrollBottom: () -> Unit,
    onRetrySync: () -> Unit,
) {
    val isAtBottom by remember {
        derivedStateOf {
            val v = listState.layoutInfo.visibleItemsInfo; val l = v.lastOrNull() ?: return@derivedStateOf true
            l.index >= listState.layoutInfo.totalItemsCount - 2
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 24.dp, bottom = 24.dp)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(message = msg, currentConfig = currentConfig, performanceMode = performanceMode)
            }
            if (isLoading && streamingText.isNotEmpty()) {
                item(key = "streaming") { StreamingBubble(text = streamingText, config = currentConfig, performanceMode = performanceMode) }
            }
            if (isLoading && streamingText.isEmpty()) {
                item(key = "typing") { TypingIndicator(config = currentConfig, performanceMode = performanceMode) }
            }
            if (error != null) {
                item(key = "error") { ErrorMessage(error = error, onRetrySync = onRetrySync) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        AnimatedVisibility(
            visible = !isAtBottom && messages.isNotEmpty(),
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
        ) {
            FilledIconButton(
                onClick = onScrollBottom,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(0.15f), contentColor = Color.White)
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String, config: AiConfig?, performanceMode: Boolean) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth(.95f)) {
        AiAvatar(config, 34.dp, modifier = Modifier.offset(y = 4.dp), performanceMode = performanceMode)
        Surface(
            color = Color.White.copy(if (performanceMode) 0.12f else 0.08f),
            shape = RoundedCornerShape(6.dp, AiDesign.CornerMedium, AiDesign.CornerMedium, AiDesign.CornerMedium),
            border = BorderStroke(1.dp, Color.White.copy(0.1f)),
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(AiDesign.ChatBubblePadding)) {
                val segs = remember(text) { parseMarkdownToSegments(text) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { 
                    segs.forEach { seg -> 
                        CompositionLocalProvider(LocalContentColor provides Color.White) {
                            MarkdownSegment(seg) 
                        }
                    } 
                }

                if (!performanceMode) {
                    val inf = rememberInfiniteTransition(label = "cursor")
                    val alpha by inf.animateFloat(0.2f, 1f, infiniteRepeatable(tween(400), RepeatMode.Reverse), "a")
                    Box(Modifier.padding(top = 6.dp).size(2.dp, 16.dp).alpha(alpha).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp)))
                }
            }
        }
    }
}

@Composable
fun AiAvatar(config: AiConfig?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, performanceMode: Boolean = false) {
    val gradient = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (!performanceMode) {
            val inf = rememberInfiniteTransition(label = "avatar_glow")
            val glowScale by inf.animateFloat(1f, 1.3f, infiniteRepeatable(tween(2500), RepeatMode.Reverse), label = "s")
            Box(Modifier.size(size).scale(glowScale).alpha(0.1f).background(MaterialTheme.colorScheme.primary, CircleShape))
        }

        Surface(modifier = Modifier.size(size), shape = CircleShape, shadowElevation = 4.dp, color = Color(0xFF1A1A1A)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.background(gradient)) {
                if (config?.customIconUri != null) {
                    AsyncImage(
                        model = config.customIconUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val icon = if (config != null) getIconForConfig(config.iconRes, config.provider) else Icons.Rounded.AutoAwesome
                    Icon(icon, null, Modifier.size(size * .55f), tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun TypingIndicator(config: AiConfig?, performanceMode: Boolean = false) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AiAvatar(config, 34.dp, modifier = Modifier.offset(y = 4.dp), performanceMode = performanceMode)
        Surface(
            color = Color.White.copy(if (performanceMode) 0.12f else 0.08f),
            shape = RoundedCornerShape(6.dp, AiDesign.CornerMedium, AiDesign.CornerMedium, AiDesign.CornerMedium),
            border = BorderStroke(1.dp, Color.White.copy(0.1f))
        ) {
            Box(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                TypingIndicatorDots(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun TypingIndicatorDots(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val inf = rememberInfiniteTransition(label = "dots")
        for (i in 0..2) {
            val animAlpha by inf.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$i"
            )
            Box(Modifier.size(8.dp).alpha(animAlpha).background(color, CircleShape))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Helpers & Bottom Sheets
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

@Composable
private fun KeysUnavailableBanner(isSyncing: Boolean, onRetrySync: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color(0xFFFF5252).copy(0.2f),
        shape = RoundedCornerShape(AiDesign.CornerMedium),
        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(.3f)),
        shadowElevation = 4.dp
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(36.dp).background(Color(0xFFFF5252).copy(.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.KeyOff, null, Modifier.size(18.dp), tint = Color(0xFFFF5252))
            }
            Text("API keys required", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Button(
                onClick = { if (!isSyncing) onRetrySync() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (isSyncing) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.Rounded.Sync, null, Modifier.size(14.dp), tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text(if (isSyncing) "Syncing" else "Retry", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSummarySheet(
    summary: String?,
    isSummarizing: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(topStart = AiDesign.CornerLarge, topEnd = AiDesign.CornerLarge),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp, 4.dp).background(Color.White.copy(.2f), CircleShape))
            }
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val inf = rememberInfiniteTransition(label = "spark")
                    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "r")
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(20.dp).rotate(if (isSummarizing) rot else 0f), tint = MaterialTheme.colorScheme.primary)
                    Text("Chat Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isSummarizing) {
                        IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp).background(Color.White.copy(0.05f), CircleShape)) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp), tint = Color.White)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(18.dp), tint = Color.White.copy(.6f))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            AnimatedContent(targetState = isSummarizing, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }, label = "sumContent") { loading ->
                if (loading) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        TypingIndicatorDots(color = MaterialTheme.colorScheme.primary)
                        Text("Distilling conversation...", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(.7f))
                    }
                } else {
                    if (summary != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(.1f),
                                shape = RoundedCornerShape(AiDesign.CornerMedium),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(.2f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(summary, modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge, lineHeight = 28.sp, color = Color.White)
                            }
                            Text("Powered by Llama 3.3", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(.4f), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiHistoryDrawer(chats: List<AiChat>, currentChatId: Int?, onChatSelect: (Int) -> Unit, onNewChat: () -> Unit, onDeleteChat: (AiChat) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(chats, query) { if (query.isBlank()) chats else chats.filter { it.title.contains(query, true) } }
    val grouped = remember(filtered) { filtered.groupBy { it.chatGroup() }.toSortedMap(compareBy { it.ordinal }) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp)) {
            Text("History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onNewChat,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(AiDesign.CornerMedium),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.NoteAdd, null); Spacer(Modifier.width(10.dp))
                Text("New Chat", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AiDesign.CornerSmall),
                placeholder = { Text("Search chats...", color = Color.White.copy(0.4f)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Color.White.copy(0.6f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White.copy(0.05f),
                    focusedContainerColor = Color.White.copy(0.08f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(0.1f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(0.5f)
                )
            )
        }

        HorizontalDivider(color = Color.White.copy(0.1f))

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (grouped.isEmpty()) {
                item { Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(48.dp).alpha(.2f), tint = Color.White); Spacer(Modifier.height(16.dp)); Text("Empty history", color = Color.White.copy(0.3f)) } }
            } else {
                grouped.forEach { (group, groupChats) ->
                    item { Text(group.label().uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary.copy(.8f), modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 8.dp)) }
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
                                Icon(if (isSelected) Icons.Rounded.ChatBubble else Icons.Rounded.ChatBubbleOutline, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(0.5f), modifier = Modifier.size(18.dp))
                                Text(chat.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color.White else Color.White.copy(0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { onDeleteChat(chat) }, Modifier.size(32.dp)) { Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp), tint = Color.White.copy(.3f)) }
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
            color = Color(0xFF1E1E1E),
            border = BorderStroke(1.dp, Color.White.copy(0.1f)),
            shadowElevation = 24.dp
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(86.dp).background(iconColor.copy(.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(42.dp))
                }
                Spacer(Modifier.height(26.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = iconColor, letterSpacing = 2.sp)
                Spacer(Modifier.height(14.dp))
                Text(description, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = Color.White)
                Spacer(Modifier.height(10.dp))
                Text(supportingText, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = Color.White.copy(.6f), lineHeight = 22.sp)
                Spacer(Modifier.height(34.dp))
                Button(onClick = onPrimaryClick, Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = iconColor)) { Text(primaryButtonText, fontWeight = FontWeight.Black, color = Color.White) }
                if (secondaryButtonText != null && onSecondaryClick != null) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = onSecondaryClick, Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color.White.copy(0.2f))) { Text(secondaryButtonText, fontWeight = FontWeight.Bold, color = Color.White.copy(0.8f)) }
                }
                TextButton(onClick = onDismiss, Modifier.padding(top = 10.dp)) { Text("Dismiss", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(0.4f)) }
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
    performanceMode: Boolean
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
        containerColor = Color(0xFF1A1A1A), shape = RoundedCornerShape(AiDesign.CornerLarge),
        title = {
            Column {
                Text("AI Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
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
                            unselectedContentColor = Color.White.copy(0.5f), 
                            selectedContentColor = Color.White
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
                                                labelColor = Color.White.copy(0.6f),
                                                containerColor = Color.White.copy(0.05f)
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = state.provider == p, borderColor = Color.White.copy(0.1f), selectedBorderColor = Color.Transparent)
                                        )
                                    }
                                }
                            }
                            SettingsSection("Model") {
                                Surface(onClick = { showModelMenu = true }, shape = RoundedCornerShape(AiDesign.CornerSmall), color = Color.White.copy(0.05f), border = BorderStroke(1.dp, Color.White.copy(0.1f))) {
                                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(state.selectedModel, fontWeight = FontWeight.Bold, color = Color.White)
                                        Icon(Icons.Rounded.UnfoldMore, null, tint = Color.White.copy(0.7f))
                                    }
                                }
                                DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }, containerColor = Color(0xFF222222)) {
                                    AiSettingsHelper.getModels(state.provider).forEach { m -> DropdownMenuItem(text = { Text(m, color = Color.White) }, onClick = { onModelChange(m); showModelMenu = false }) }
                                }
                            }
                            SettingsSection("API Key") {
                                OutlinedTextField(
                                    value = state.apiKey, onValueChange = onApiKeyChange, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(AiDesign.CornerSmall),
                                    placeholder = { Text(AiSettingsHelper.getApiKeyPlaceholder(state.provider), color = Color.White.copy(0.3f)) },
                                    trailingIcon = { if (state.apiKey.isNotEmpty()) IconButton(onClick = { onApiKeyChange("") }) { Icon(Icons.Rounded.Close, null, tint = Color.White.copy(0.6f)) } },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        unfocusedBorderColor = Color.White.copy(0.1f),
                                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(0.5f),
                                        unfocusedContainerColor = Color.White.copy(0.02f),
                                        focusedContainerColor = Color.White.copy(0.05f)
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

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = onTest, Modifier.weight(1f).height(48.dp), enabled = !state.isTesting, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                                    if (state.isTesting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test Connection", fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = onRefresh, Modifier.size(48.dp).background(Color.White.copy(0.05f), RoundedCornerShape(14.dp))) { Icon(Icons.Rounded.Sync, null, tint = Color.White) }
                            }

                            if (state.testResult != null) {
                                Surface(color = if (state.testResult.startsWith("✓")) Color(0xFF4CAF50).copy(.15f) else Color(0xFFFF5252).copy(.15f), shape = RoundedCornerShape(12.dp)) {
                                    Text(state.testResult, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = Color.White)
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
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, unfocusedBorderColor = Color.White.copy(0.1f))
                                    )
                                    
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Preset Icon", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            // Custom Icon Picker
                                            Surface(
                                                onClick = onCustomIconClick, 
                                                modifier = Modifier.size(48.dp), 
                                                shape = RoundedCornerShape(14.dp), 
                                                color = if (state.selectedIcon == "CUSTOM") MaterialTheme.colorScheme.primaryContainer else Color.White.copy(0.05f),
                                                border = if (state.selectedIcon == "CUSTOM") BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (state.customIconUri != null) {
                                                        AsyncImage(model = state.customIconUri, contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                                                    } else {
                                                        Icon(Icons.Rounded.AddAPhoto, null, Modifier.size(20.dp), tint = Color.White)
                                                    }
                                                }
                                            }

                                            listOf("AUTO","GEMINI","CHATGPT","GROQ","CLAUDE","DEEPSEEK","BOT","SPARKLE").forEach { ik ->
                                                Surface(onClick = { onIconChange(ik) }, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(14.dp), color = if (state.selectedIcon == ik) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(0.05f), border = if (state.selectedIcon == ik) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                                                    Box(contentAlignment = Alignment.Center) { Icon(getIconForConfig(ik, state.provider), null, Modifier.size(24.dp), tint = Color.White) }
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
                                        Icon(Icons.Rounded.Bookmarks, null, Modifier.size(48.dp).alpha(0.2f), tint = Color.White)
                                        Text("No presets saved", color = Color.White.copy(0.4f), modifier = Modifier.padding(top = 8.dp))
                                    }
                                }
                            }
                            items(savedConfigs) { config ->
                                Surface(Modifier.fillMaxWidth(), RoundedCornerShape(AiDesign.CornerSmall), Color.White.copy(0.05f), border = BorderStroke(1.dp, Color.White.copy(0.1f))) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        AiAvatar(config, 40.dp, performanceMode = true)
                                        Column(Modifier.weight(1f)) { Text(config.name, fontWeight = FontWeight.Bold, color = Color.White); Text("${config.provider} · ${config.model}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(.6f)) }
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = Color.White.copy(0.6f)) } },
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
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF1E1E1E), shape = RoundedCornerShape(AiDesign.CornerLarge),
        title = { Text("Setup Guide", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, color = Color.White) },
        text = {
            Column(Modifier.fillMaxWidth().height(360.dp)) {
                HorizontalPager(state = pagerState, Modifier.fillMaxWidth().weight(1f)) { page ->
                    val p = providers[page]
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        AiAvatar(null, 64.dp, performanceMode = true)
                        Spacer(Modifier.height(16.dp))
                        Text(p, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Text(AiSettingsHelper.detailedInfo[p] ?: "", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.7f), modifier = Modifier.padding(16.dp))
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color.White.copy(0.1f))
                        (AiSettingsHelper.tutorials[p] ?: emptyList()).forEachIndexed { i, step ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                                Text("${i + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                                Text(step, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.8f), modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(providers.size) { i -> Box(Modifier.padding(4.dp).size(if (pagerState.currentPage == i) 10.dp else 6.dp).background(if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary else Color.White.copy(0.2f), CircleShape)) }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Got it") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionsSheet(messageText: String, isUser: Boolean, onDismiss: () -> Unit, onCopy: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(topStart = AiDesign.CornerLarge, topEnd = AiDesign.CornerLarge)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp).navigationBarsPadding()) {
            Text("Options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.padding(bottom = 24.dp))
            Surface(onClick = onCopy, shape = RoundedCornerShape(AiDesign.CornerMedium), color = Color.White.copy(0.05f), border = BorderStroke(1.dp, Color.White.copy(0.1f))) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Rounded.ContentCopy, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Copy Message", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            if (!isUser) {
                Spacer(Modifier.height(12.dp))
                Surface(onClick = { /* share */ onDismiss() }, shape = RoundedCornerShape(AiDesign.CornerMedium), color = Color.White.copy(0.05f), border = BorderStroke(1.dp, Color.White.copy(0.1f))) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Rounded.Share, null, tint = MaterialTheme.colorScheme.secondary)
                        Text("Share Response", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorMessage(error: String, onRetrySync: () -> Unit) {
    Surface(
        color = Color(0xFFFF5252).copy(0.15f),
        shape = RoundedCornerShape(AiDesign.CornerMedium),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(.2f)),
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = Color(0xFFFF5252))
                Text(error, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            if (error.contains("key", true) || error.contains("sync", true)) {
                Button(onClick = onRetrySync, modifier = Modifier.align(Alignment.End), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.Sync, null, Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Retry Sync")
                }
            }
        }
    }
}
