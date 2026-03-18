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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.frerox.toolz.data.ai.AiChat
import com.frerox.toolz.data.ai.AiConfig
import com.frerox.toolz.data.ai.AiMessage
import com.frerox.toolz.data.ai.AiSettingsHelper
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
//  Markdown segment model
// ─────────────────────────────────────────────────────────────

private sealed class MdSegment {
    data class Paragraph(val content: AnnotatedString) : MdSegment()
    data class Header(val level: Int, val text: String) : MdSegment()
    data class Code(val language: String, val code: String) : MdSegment()
    data class BulletItem(val content: AnnotatedString, val depth: Int = 0) : MdSegment()
    data class NumberedItem(val index: Int, val content: AnnotatedString) : MdSegment()
    object Divider : MdSegment()
}

// ─────────────────────────────────────────────────────────────
//  Markdown parser
// ─────────────────────────────────────────────────────────────

private fun parseMarkdownToSegments(raw: String): List<MdSegment> {
    val segments = mutableListOf<MdSegment>()
    val lines    = raw.lines()
    var i        = 0

    while (i < lines.size) {
        val line = lines[i]

        // Fenced code block
        if (line.trimStart().startsWith("```")) {
            val lang  = line.trim().removePrefix("```").trim().ifBlank { "code" }
            val code  = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                code.appendLine(lines[i])
                i++
            }
            segments += MdSegment.Code(lang, code.toString().trimEnd())
            i++
            continue
        }

        // Horizontal rule
        if (line.trim().matches(Regex("^[-*_]{3,}$"))) {
            segments += MdSegment.Divider
            i++
            continue
        }

        // Headers
        if (line.startsWith("#")) {
            val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
            segments += MdSegment.Header(level, line.drop(level).trim())
            i++
            continue
        }

        // Bullet list
        if (line.matches(Regex("^(\\s*)[\\-\\*\\+] .+"))) {
            val depth   = line.indexOfFirst { !it.isWhitespace() } / 2
            val content = line.trim().drop(2)
            segments += MdSegment.BulletItem(inlineMarkdown(content), depth)
            i++
            continue
        }

        // Numbered list
        val numberedMatch = Regex("^(\\d+)[\\.\\)] (.+)").find(line.trim())
        if (numberedMatch != null) {
            val idx     = numberedMatch.groupValues[1].toIntOrNull() ?: 1
            val content = numberedMatch.groupValues[2]
            segments += MdSegment.NumberedItem(idx, inlineMarkdown(content))
            i++
            continue
        }

        // Blank line — skip, used as separator
        if (line.isBlank()) {
            i++
            continue
        }

        // Normal paragraph
        segments += MdSegment.Paragraph(inlineMarkdown(line))
        i++
    }

    return segments
}

/** Applies inline formatting: **bold**, *italic*, `code`, ~~strikethrough~~ */
private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    // Patterns ordered by precedence (longer patterns first)
    data class Token(val start: Int, val end: Int, val content: String, val type: String)

    val tokens = mutableListOf<Token>()

    fun scan(regex: String, type: String) {
        Regex(regex).findAll(text).forEach { m ->
            tokens += Token(m.range.first, m.range.last + 1, m.groupValues.getOrElse(1) { m.value }, type)
        }
    }

    scan("""\*\*(.+?)\*\*""", "bold")
    scan("""\*(.+?)\*""", "italic")
    scan("""`(.+?)`""", "code")
    scan("""~~(.+?)~~""", "strike")

    // Sort by position, remove overlapping tokens
    val sorted = tokens.sortedBy { it.start }
    val clean  = mutableListOf<Token>()
    var cursor = 0
    for (tok in sorted) {
        if (tok.start >= cursor) {
            clean += tok
            cursor = tok.end
        }
    }

    cursor = 0
    for (tok in clean) {
        if (tok.start > cursor) append(text.substring(cursor, tok.start))
        when (tok.type) {
            "bold"   -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(tok.content) }
            "italic" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(tok.content) }
            "code"   -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF1E1E1E).copy(0.08f))) { append(tok.content) }
            "strike" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(tok.content) }
            else     -> append(tok.content)
        }
        cursor = tok.end
    }
    if (cursor < text.length) append(text.substring(cursor))
}

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

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                viewModel.onImageSelected(bmp)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Scroll to bottom whenever messages grow
    val totalMessages = uiState.messages.size
    LaunchedEffect(totalMessages, uiState.isLoading) {
        if (totalMessages > 0) {
            scope.launch { listState.animateScrollToItem(totalMessages - 1) }
        }
    }

    // Scroll to bottom when streaming text updates (live typing effect)
    LaunchedEffect(uiState.streamingText) {
        if (uiState.streamingText.isNotEmpty() && totalMessages > 0) {
            listState.scrollToItem(totalMessages - 1)
        }
    }

    // Quota dialog
    LaunchedEffect(uiState.quotaExceeded) {
        if (uiState.quotaExceeded) showQuotaDialog = true
    }

    if (showSettings) {
        AiSettingsDialog(
            state          = settingsUiState,
            savedConfigs   = uiState.savedConfigs,
            onDismiss      = { showSettings = false },
            onProviderChange = viewModel::updateProvider,
            onApiKeyChange = viewModel::updateApiKey,
            onModelChange  = viewModel::updateModel,
            onIconChange   = viewModel::updateIcon,
            onSave         = { viewModel.saveSettings(); showSettings = false },
            onSaveConfig   = viewModel::saveConfig,
            onDeleteConfig = viewModel::deleteConfig,
            onEditConfig   = viewModel::editConfig,
            onMoveConfig   = viewModel::moveConfig,
            onTest         = viewModel::testConnection,
        )
    }

    if (showQuotaDialog) {
        ModernAiDialog(
            title             = "QUOTA EXCEEDED",
            icon              = Icons.Rounded.LockClock,
            iconColor         = MaterialTheme.colorScheme.error,
            description       = "${settingsUiState.provider} has reached its limit.",
            supportingText    = "Switch to ${uiState.suggestedProvider} or use your own API key.",
            primaryButtonText = "SWITCH TO ${uiState.suggestedProvider?.uppercase() ?: "OTHER"}",
            onPrimaryClick    = { uiState.suggestedProvider?.let { viewModel.switchProvider(it) }; showQuotaDialog = false },
            secondaryButtonText = "SETUP MY OWN KEY",
            onSecondaryClick  = { showQuotaDialog = false; showSettings = true },
            onDismiss         = { showQuotaDialog = false },
        )
    }

    if (uiState.pendingConfig != null) {
        ModernAiDialog(
            title             = "SWITCH CONFIG",
            icon              = Icons.Rounded.SwapHoriz,
            iconColor         = MaterialTheme.colorScheme.primary,
            description       = "Starting a new chat with '${uiState.pendingConfig?.name}'.",
            supportingText    = "Your current conversation will be saved in history.",
            primaryButtonText = "CONFIRM & NEW CHAT",
            onPrimaryClick    = viewModel::confirmConfigSwitch,
            secondaryButtonText = "CANCEL",
            onSecondaryClick  = viewModel::cancelConfigSwitch,
            onDismiss         = viewModel::cancelConfigSwitch,
        )
    }

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                drawerTonalElevation = 0.dp,
                modifier  = Modifier.width(316.dp),
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
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
                    settingsUiState = settingsUiState,
                    uiState         = uiState,
                    performanceMode = performanceMode,
                    onBack          = { vibration?.vibrateClick(); onBack() },
                    onNewChat       = { vibration?.vibrateClick(); viewModel.createNewChat() },
                    onSettings      = { vibration?.vibrateClick(); showSettings = true },
                    onHistory       = { vibration?.vibrateClick(); scope.launch { drawerState.open() } },
                    onConfigSelect  = { vibration?.vibrateClick(); viewModel.onConfigRequest(it) },
                )
            },
            bottomBar = {
                ChatInputBar(
                    inputText       = inputText,
                    isLoading       = uiState.isLoading,
                    selectedImage   = uiState.selectedImage,
                    supportsVision  = AiSettingsHelper.supportsVision(settingsUiState.provider, settingsUiState.selectedModel),
                    performanceMode = performanceMode,
                    onInputChange   = { inputText = it },
                    onSend          = {
                        if (inputText.isNotBlank() || uiState.selectedImage != null) {
                            vibration?.vibrateClick()
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    onCancel        = { viewModel.cancelRequest() },
                    onAttach        = { imagePicker.launch("image/*") },
                    onRemoveImage   = { viewModel.onImageSelected(null) },
                )
            },
            containerColor = Color.Transparent,
        ) { paddingValues ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AnimatedContent(
                    targetState = uiState.messages.isEmpty() && !uiState.isLoading,
                    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                    label = "chat_content"
                ) { showEmpty ->
                    if (showEmpty) {
                        EmptyChatState(
                            onSuggestionClick = { suggestion ->
                                inputText = suggestion
                            }
                        )
                    } else {
                        ChatMessageList(
                            messages       = uiState.messages,
                            streamingText  = uiState.streamingText,
                            isLoading      = uiState.isLoading,
                            error          = uiState.error,
                            listState      = listState,
                            currentConfig  = uiState.savedConfigs.find {
                                it.provider == settingsUiState.provider &&
                                        it.model == settingsUiState.selectedModel
                            },
                            performanceMode = performanceMode,
                            onScrollBottom = {
                                scope.launch { listState.animateScrollToItem(uiState.messages.size.coerceAtLeast(1) - 1) }
                            }
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
    uiState: AiAssistantUiState,
    performanceMode: Boolean,
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onConfigSelect: (AiConfig) -> Unit,
) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        CenterAlignedTopAppBar(
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "AI ASSISTANT",
                        style       = MaterialTheme.typography.labelSmall,
                        fontWeight  = FontWeight.Black,
                        color       = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        val dotColor by animateColorAsState(
                            when {
                                uiState.isLoading  -> MaterialTheme.colorScheme.primary
                                uiState.error != null -> MaterialTheme.colorScheme.error
                                else               -> Color(0xFF4CAF50)
                            }, label = "status"
                        )

                        if (uiState.isLoading && !performanceMode) {
                            val inf = rememberInfiniteTransition(label = "dot")
                            val dotScale by inf.animateFloat(0.6f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "ds")
                            Box(Modifier.size(6.dp).scale(dotScale).clip(CircleShape).background(dotColor))
                        } else {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                        }

                        AnimatedContent(
                            targetState = "${settingsUiState.provider} · ${settingsUiState.selectedModel}",
                            transitionSpec = {
                                (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                            },
                            label = "model"
                        ) { text ->
                            Text(
                                text,
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp)
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(onClick = onNewChat) {
                    Icon(Icons.AutoMirrored.Rounded.NoteAdd, "New Chat", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, "Settings")
                }
                IconButton(onClick = onHistory) {
                    Icon(Icons.Rounded.History, "History")
                }
            }
        )

        // Config chip row
        if (uiState.savedConfigs.isNotEmpty()) {
            LazyRow(
                modifier       = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.savedConfigs) { config ->
                    val isSelected = settingsUiState.provider == config.provider &&
                            settingsUiState.selectedModel == config.model &&
                            settingsUiState.apiKey == config.apiKey
                    val chipScale by animateFloatAsState(if (isSelected) 1.04f else 1f, label = "chip")

                    FilterChip(
                        selected     = isSelected,
                        onClick      = { onConfigSelect(config) },
                        label        = { Text(config.name, fontWeight = FontWeight.SemiBold) },
                        leadingIcon  = { Icon(getIconForConfig(config.iconRes, config.provider), null, Modifier.size(16.dp)) },
                        modifier     = Modifier.scale(chipScale),
                        shape        = RoundedCornerShape(12.dp),
                        colors       = FilterChipDefaults.filterChipColors(
                            selectedContainerColor    = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor        = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor  = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.outlineVariant.copy(0.4f)
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Input bar
// ─────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
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
) {
    val hasContent  = inputText.isNotBlank() || selectedImage != null
    val surfaceColor by animateColorAsState(
        if (hasContent) MaterialTheme.colorScheme.primaryContainer.copy(0.25f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "input_bg"
    )

    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Attached image preview
        AnimatedVisibility(
            visible = selectedImage != null,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut()
        ) {
            Box(Modifier.padding(start = 4.dp)) {
                Surface(
                    shape  = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.4f))
                ) {
                    AsyncImage(
                        model            = selectedImage,
                        contentDescription = null,
                        modifier         = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)),
                        contentScale     = ContentScale.Crop
                    )
                }
                IconButton(
                    onClick  = onRemoveImage,
                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp).offset(x = 6.dp, y = (-6).dp)
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.error) {
                        Icon(Icons.Rounded.Close, null, Modifier.padding(3.dp).size(12.dp), tint = Color.White)
                    }
                }
            }
        }

        // Input pill
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp, max = 180.dp),
            shape  = RoundedCornerShape(26.dp),
            color  = surfaceColor,
            border = BorderStroke(
                1.dp,
                if (hasContent) MaterialTheme.colorScheme.primary.copy(0.2f)
                else MaterialTheme.colorScheme.outlineVariant.copy(0.3f)
            )
        ) {
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Attach button (vision models only)
                if (supportsVision) {
                    IconButton(
                        onClick  = onAttach,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, "Attach image", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }

                TextField(
                    value         = inputText,
                    onValueChange = onInputChange,
                    placeholder   = {
                        Text(
                            "Ask anything…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.45f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors   = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor   = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor   = Color.Transparent,
                    ),
                    maxLines      = 6,
                    textStyle     = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!isLoading) onSend() })
                )

                // Send / Cancel / Loading button
                Crossfade(
                    targetState = isLoading,
                    modifier    = Modifier.padding(4.dp),
                    label       = "send_state"
                ) { loading ->
                    if (loading) {
                        // Cancel button
                        IconButton(
                            onClick  = onCancel,
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                        ) {
                            Icon(
                                Icons.Rounded.Stop, "Cancel",
                                tint     = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick  = onSend,
                            enabled  = hasContent,
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (hasContent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send, "Send",
                                tint     = if (hasContent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Character count — appears when text is long
        AnimatedVisibility(visible = inputText.length > 200) {
            Text(
                "${inputText.length} chars",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.outline.copy(0.5f),
                modifier = Modifier.align(Alignment.End).padding(end = 8.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Message list
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
) {
    val isAtBottom by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val lastVisible  = visibleItems.lastOrNull() ?: return@derivedStateOf true
            val lastIndex    = listState.layoutInfo.totalItemsCount - 1
            lastVisible.index >= lastIndex - 1
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state          = listState,
            modifier       = Modifier
                .fillMaxSize()
                .let {
                    if (!performanceMode) it.drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.04f to Color.Black,
                                0.96f to Color.Black,
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    } else it
                },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(
                    message       = message,
                    currentConfig = currentConfig,
                    performanceMode = performanceMode,
                )
            }

            // Live streaming preview bubble
            if (isLoading && streamingText.isNotEmpty()) {
                item(key = "streaming") {
                    StreamingBubble(text = streamingText, config = currentConfig)
                }
            }

            // Typing indicator (shown before streaming starts)
            if (isLoading && streamingText.isEmpty()) {
                item(key = "typing") {
                    TypingIndicator(config = currentConfig)
                }
            }

            // Error
            if (error != null) {
                item(key = "error") {
                    ErrorMessage(error)
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        // Scroll-to-bottom FAB
        AnimatedVisibility(
            visible  = !isAtBottom && messages.isNotEmpty(),
            enter    = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit     = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        ) {
            FloatingActionButton(
                onClick        = onScrollBottom,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor   = MaterialTheme.colorScheme.onSurface,
                shape          = CircleShape,
                elevation      = FloatingActionButtonDefaults.elevation(2.dp),
                modifier       = Modifier.size(38.dp)
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Chat bubble
// ─────────────────────────────────────────────────────────────

@Composable
fun ChatBubble(
    message: AiMessage,
    currentConfig: AiConfig?,
    performanceMode: Boolean,
) {
    val clipboardManager = LocalClipboardManager.current
    val scope            = rememberCoroutineScope()
    val vibration        = LocalVibrationManager.current
    var isCopied         by remember { mutableStateOf(false) }
    var showActions      by remember { mutableStateOf(false) }

    val segments = remember(message.text) { parseMarkdownToSegments(message.text) }

    val appearScale = remember { Animatable(if (performanceMode) 1f else 0.88f) }
    val appearAlpha = remember { Animatable(if (performanceMode) 1f else 0f) }
    LaunchedEffect(message.id) {
        if (!performanceMode) {
            launch { appearScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
            launch { appearAlpha.animateTo(1f, tween(220)) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .graphicsLayer(scaleX = appearScale.value, scaleY = appearScale.value, alpha = appearAlpha.value),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.88f).align(if (message.isUser) Alignment.End else Alignment.Start)
        ) {
            // AI avatar
            if (!message.isUser) {
                AiAvatar(config = currentConfig, size = 30.dp, modifier = Modifier.padding(top = 6.dp, end = 8.dp))
            }

            // Bubble
            if (message.isUser) {
                // User: solid primary pill
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = {
                            vibration?.vibrateLongClick()
                            showActions = true
                        })
                    }
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (message.text.isNotBlank()) {
                            Text(
                                message.text,
                                style  = MaterialTheme.typography.bodyLarge,
                                color  = MaterialTheme.colorScheme.onPrimary,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            } else {
                // AI: glass surface card with rendered markdown
                Surface(
                    color   = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape   = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp),
                    border  = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.25f)),
                    modifier = Modifier.weight(1f).pointerInput(Unit) {
                        detectTapGestures(onLongPress = {
                            vibration?.vibrateLongClick()
                            showActions = true
                        })
                    }
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                segments.forEach { seg -> MarkdownSegment(seg) }
                            }
                        }

                        // Copy button row
                        Row(
                            Modifier.padding(top = 10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(visible = isCopied) {
                                Text(
                                    "Copied",
                                    style  = MaterialTheme.typography.labelSmall,
                                    color  = Color(0xFF4CAF50),
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                            IconButton(
                                onClick  = {
                                    vibration?.vibrateClick()
                                    clipboardManager.setText(AnnotatedString(message.text))
                                    isCopied = true
                                    scope.launch { delay(2200); isCopied = false }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                    null,
                                    modifier = Modifier.size(14.dp),
                                    tint     = if (isCopied) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Message action bottom sheet on long-press
    if (showActions) {
        MessageActionsSheet(
            messageText  = message.text,
            isUser       = message.isUser,
            onDismiss    = { showActions = false },
            onCopy       = {
                clipboardManager.setText(AnnotatedString(message.text))
                isCopied = true
                scope.launch { delay(2200); isCopied = false }
                showActions = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Streaming preview bubble (live text as it arrives)
// ─────────────────────────────────────────────────────────────

@Composable
private fun StreamingBubble(text: String, config: AiConfig?) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .padding(vertical = 2.dp)
    ) {
        AiAvatar(config, 30.dp, Modifier.padding(top = 6.dp, end = 8.dp))
        Surface(
            color  = MaterialTheme.colorScheme.surfaceContainerLow,
            shape  = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.25f)),
            modifier = Modifier.weight(1f)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                val segments = remember(text) { parseMarkdownToSegments(text) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    segments.forEach { seg -> MarkdownSegment(seg) }
                }
                // Blinking cursor
                val inf = rememberInfiniteTransition(label = "cursor")
                val cursorAlpha by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "ca")
                Box(
                    Modifier
                        .size(width = 2.dp, height = 16.dp)
                        .alpha(cursorAlpha)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Markdown segment renderer
// ─────────────────────────────────────────────────────────────

@Composable
private fun MarkdownSegment(seg: MdSegment) {
    when (seg) {
        is MdSegment.Header -> {
            val (fontSize, weight) = when (seg.level) {
                1 -> 22.sp to FontWeight.Black
                2 -> 18.sp to FontWeight.ExtraBold
                else -> 15.sp to FontWeight.Bold
            }
            Text(seg.text, fontSize = fontSize, fontWeight = weight, lineHeight = (fontSize.value + 4).sp)
        }

        is MdSegment.Paragraph -> {
            Text(seg.content, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)
        }

        is MdSegment.BulletItem -> {
            Row(
                modifier = Modifier.padding(start = (seg.depth * 12).dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(5.dp)
                        .offset(y = 9.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Text(seg.content, style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp, modifier = Modifier.weight(1f))
            }
        }

        is MdSegment.NumberedItem -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${seg.index}.",
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary,
                    style      = MaterialTheme.typography.bodyLarge,
                    modifier   = Modifier.widthIn(min = 20.dp)
                )
                Text(seg.content, style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp, modifier = Modifier.weight(1f))
            }
        }

        is MdSegment.Code -> CodeBlock(language = seg.language, code = seg.code)

        MdSegment.Divider -> HorizontalDivider(
            Modifier.padding(vertical = 4.dp),
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.outlineVariant.copy(0.4f)
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  Code block — dark surface + copy button
// ─────────────────────────────────────────────────────────────

@Composable
private fun CodeBlock(language: String, code: String) {
    val clipboardManager = LocalClipboardManager.current
    val vibration        = LocalVibrationManager.current
    var copied by remember { mutableStateOf(false) }
    val scope  = rememberCoroutineScope()

    Surface(
        color    = Color(0xFF0D1117),
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Language header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    language.uppercase(),
                    style       = MaterialTheme.typography.labelSmall,
                    fontWeight  = FontWeight.Bold,
                    color       = Color(0xFF8B949E),
                    letterSpacing = 1.sp
                )
                IconButton(
                    onClick  = {
                        vibration?.vibrateClick()
                        clipboardManager.setText(AnnotatedString(code))
                        copied = true
                        scope.launch { delay(2000); copied = false }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint     = if (copied) Color(0xFF4CAF50) else Color(0xFF8B949E)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF21262D))

            // Code content
            Text(
                code,
                fontFamily = FontFamily.Monospace,
                fontSize   = 12.5.sp,
                lineHeight = 19.sp,
                color      = Color(0xFFE6EDF3),
                modifier   = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  AI Avatar
// ─────────────────────────────────────────────────────────────

@Composable
private fun AiAvatar(
    config: AiConfig?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(size),
        shape    = CircleShape,
        color    = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val icon = if (config != null) getIconForConfig(config.iconRes, config.provider) else Icons.Rounded.AutoAwesome
            Icon(icon, null, modifier = Modifier.size(size * 0.5f), tint = Color.White)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Message actions bottom sheet (long-press)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionsSheet(
    messageText: String,
    isUser: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        containerColor    = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp, 3.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.18f), CircleShape))
            }
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp).navigationBarsPadding()) {
            Text(
                "Message",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                modifier   = Modifier.padding(bottom = 16.dp)
            )

            ActionSheetItem(Icons.Rounded.ContentCopy, "Copy message", onCopy)

            if (!isUser) {
                ActionSheetItem(Icons.Rounded.Share, "Share") {
                    // share intent handled by caller if needed
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun ActionSheetItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color   = Color.Transparent,
        shape   = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Typing indicator — single transition, proper bubble shape
// ─────────────────────────────────────────────────────────────

@Composable
fun TypingIndicator(config: AiConfig?) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        AiAvatar(config, 30.dp, Modifier.padding(top = 4.dp, end = 8.dp))

        Surface(
            color  = MaterialTheme.colorScheme.surfaceContainerLow,
            shape  = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.25f))
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Single InfiniteTransition drives all three dots
                val inf = rememberInfiniteTransition(label = "typing")
                val dot1 by inf.animateFloat(0.2f, 1f, infiniteRepeatable(tween(500, delayMillis = 0), RepeatMode.Reverse), "d1")
                val dot2 by inf.animateFloat(0.2f, 1f, infiniteRepeatable(tween(500, delayMillis = 150), RepeatMode.Reverse), "d2")
                val dot3 by inf.animateFloat(0.2f, 1f, infiniteRepeatable(tween(500, delayMillis = 300), RepeatMode.Reverse), "d3")

                listOf(dot1, dot2, dot3).forEach { alpha ->
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Empty chat state — functional suggestion chips
// ─────────────────────────────────────────────────────────────

@Composable
fun EmptyChatState(onSuggestionClick: (String) -> Unit) {
    val inf   = rememberInfiniteTransition(label = "empty")
    val pulse by inf.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), "p")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(96.dp).scale(pulse),
            shape    = CircleShape,
            color    = MaterialTheme.colorScheme.primaryContainer.copy(0.4f),
            border   = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(0.15f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            "How can I help you?",
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            textAlign  = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Ask anything — code, math, writing, analysis.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(horizontal = 48.dp)
        )

        Spacer(Modifier.height(36.dp))

        val suggestions = listOf(
            Pair(Icons.Rounded.Code,       "Write a Kotlin function"),
            Pair(Icons.Rounded.Calculate,  "Explain a math concept"),
            Pair(Icons.Rounded.Lightbulb,  "Brainstorm ideas for me"),
            Pair(Icons.Rounded.Description, "Summarize a long text"),
        )

        Column(
            verticalArrangement   = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth()
        ) {
            suggestions.chunked(2).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { (icon, text) ->
                        SuggestionChip(
                            icon     = icon,
                            text     = text,
                            modifier = Modifier.weight(1f),
                            onClick  = { onSuggestionClick(text) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
fun SuggestionChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(16.dp),
        color   = MaterialTheme.colorScheme.surfaceContainerHigh,
        border  = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.3f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                text,
                style    = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Error message
// ─────────────────────────────────────────────────────────────

@Composable
fun ErrorMessage(error: String) {
    Surface(
        color    = MaterialTheme.colorScheme.errorContainer.copy(0.85f),
        shape    = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.18f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  History drawer — grouped by time
// ─────────────────────────────────────────────────────────────

@Composable
fun AiHistoryDrawer(
    chats: List<AiChat>,
    currentChatId: Int?,
    onChatSelect: (Int) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (AiChat) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredChats = remember(chats, searchQuery) {
        if (searchQuery.isBlank()) chats
        else chats.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    // Group chats by time bucket
    val grouped = remember(filteredChats) {
        filteredChats
            .groupBy { it.chatGroup() }
            .toSortedMap(compareBy { it.ordinal })
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text(
                "CONVERSATIONS",
                style       = MaterialTheme.typography.labelSmall,
                fontWeight  = FontWeight.Black,
                color       = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick  = onNewChat,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.NoteAdd, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("NEW CONVERSATION", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium, letterSpacing = 0.5.sp)
            }

            Spacer(Modifier.height(12.dp))

            // Search
            Surface(
                color  = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape  = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Search, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                    BasicTextField(
                        value       = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine  = true,
                        textStyle   = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Search history…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                            }
                            inner()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (grouped.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.ChatBubbleOutline, null, modifier = Modifier.size(48.dp).alpha(0.18f))
                        Spacer(Modifier.height(10.dp))
                        Text("No conversations yet", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                grouped.forEach { (group, groupChats) ->
                    item(key = "group_${group.name}") {
                        Text(
                            group.label().uppercase(),
                            style       = MaterialTheme.typography.labelSmall,
                            fontWeight  = FontWeight.Bold,
                            color       = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                            letterSpacing = 1.sp,
                            modifier    = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }

                    items(groupChats, key = { it.id }) { chat ->
                        val isSelected = chat.id == currentChatId
                        ChatHistoryItem(
                            chat       = chat,
                            isSelected = isSelected,
                            onSelect   = { onChatSelect(chat.id) },
                            onDelete   = { onDeleteChat(chat) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle,
    decorationBox: @Composable (inner: @Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.text.BasicTextField(
        value         = value,
        onValueChange = onValueChange,
        singleLine    = singleLine,
        textStyle     = textStyle,
        decorationBox = decorationBox,
        modifier      = modifier,
    )
}

@Composable
private fun ChatHistoryItem(
    chat: AiChat,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        shape   = RoundedCornerShape(14.dp),
        color   = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(0.6f) else Color.Transparent,
        border  = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.18f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (isSelected) Icons.Rounded.ChatBubble else Icons.Rounded.ChatBubbleOutline,
                null,
                tint     = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                modifier = Modifier.size(17.dp)
            )
            Text(
                chat.title,
                modifier   = Modifier.weight(1f),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                color      = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Modern AI dialog
// ─────────────────────────────────────────────────────────────

@Composable
fun ModernAiDialog(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    description: String,
    supportingText: String,
    primaryButtonText: String,
    onPrimaryClick: () -> Unit,
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            shape    = RoundedCornerShape(28.dp),
            color    = MaterialTheme.colorScheme.surfaceContainerHigh,
            border   = BorderStroke(1.dp, iconColor.copy(0.1f))
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated icon
                val inf = rememberInfiniteTransition(label = "dialogIcon")
                val iconScale by inf.animateFloat(1f, 1.12f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), "s")
                Surface(
                    shape = CircleShape,
                    color = iconColor.copy(0.1f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = iconColor, modifier = Modifier.size(34.dp).scale(iconScale))
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = iconColor, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(10.dp))
                Text(description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 24.sp)
                Spacer(Modifier.height(8.dp))
                Text(supportingText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                Spacer(Modifier.height(28.dp))

                Button(
                    onClick  = onPrimaryClick,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = iconColor)
                ) { Text(primaryButtonText, fontWeight = FontWeight.Bold) }

                if (secondaryButtonText != null && onSecondaryClick != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick  = onSecondaryClick,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape    = RoundedCornerShape(16.dp),
                        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) { Text(secondaryButtonText, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 4.dp)) {
                    Text("MAYBE LATER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline.copy(0.5f))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Settings dialog — fixed height, no blur(), tab layout
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsDialog(
    state: AiSettingsUiState,
    savedConfigs: List<AiConfig>,
    onDismiss: () -> Unit,
    onProviderChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onSave: () -> Unit,
    onSaveConfig: (String) -> Unit,
    onDeleteConfig: (AiConfig) -> Unit,
    onEditConfig: (AiConfig) -> Unit,
    @Suppress("UNUSED_PARAMETER") onMoveConfig: (Int, Int) -> Unit,
    onTest: () -> Unit,
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
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Text("AI Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(12.dp))
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor   = Color.Transparent,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    listOf("SETUP", "CONFIGS").forEachIndexed { i, label ->
                        Tab(selected = activeTab == i, onClick = { activeTab = i }) {
                            Text(label, Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                if (activeTab == 0) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(AiSettingsHelper.disclaimerText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                        SettingsSection("Provider") {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Gemini", "ChatGPT", "Groq", "Claude", "DeepSeek", "OpenRouter").forEach { p ->
                                    FilterChip(
                                        selected = state.provider == p,
                                        onClick  = { onProviderChange(p) },
                                        label    = { Text(p, fontWeight = FontWeight.Medium) },
                                        shape    = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }

                        SettingsSection("Model") {
                            Box {
                                Surface(
                                    onClick = { showModelMenu = true },
                                    shape   = RoundedCornerShape(14.dp),
                                    color   = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    border  = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        Text(state.selectedModel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Icon(Icons.Rounded.UnfoldMore, null, modifier = Modifier.size(18.dp))
                                    }
                                }
                                DropdownMenu(
                                    expanded = showModelMenu,
                                    onDismissRequest = { showModelMenu = false },
                                    modifier = Modifier.fillMaxWidth(0.72f)
                                ) {
                                    AiSettingsHelper.getModels(state.provider).forEach { model ->
                                        DropdownMenuItem(
                                            text    = { Text(model, style = MaterialTheme.typography.bodyMedium) },
                                            onClick = { onModelChange(model); showModelMenu = false }
                                        )
                                    }
                                }
                            }
                        }

                        SettingsSection("API Key") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = { showTutorial = true }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Text("How to get one?", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                                }
                                TextButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AiSettingsHelper.getApiKeyUrl(state.provider))))
                                }) {
                                    Text("GET KEY →", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                                }
                            }

                            OutlinedTextField(
                                value         = state.apiKey,
                                onValueChange = onApiKeyChange,
                                modifier      = Modifier.fillMaxWidth(),
                                shape         = RoundedCornerShape(14.dp),
                                placeholder   = { Text(AiSettingsHelper.getApiKeyPlaceholder(state.provider), style = MaterialTheme.typography.bodySmall) },
                                isError       = !state.isKeyValid,
                                supportingText = {
                                    if (!state.isKeyValid)
                                        Text("Invalid key format for ${state.provider}", color = MaterialTheme.colorScheme.error)
                                    else if (state.apiKey.isEmpty())
                                        Text("Using Toolz Default Key", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                },
                                trailingIcon  = {
                                    if (state.apiKey.isNotEmpty()) {
                                        IconButton(onClick = { onApiKeyChange("") }) {
                                            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            )
                        }

                        // Test result banner
                        AnimatedVisibility(visible = state.testResult != null) {
                            val isSuccess = state.testResult?.startsWith("✓") == true
                            Surface(
                                color    = if (isSuccess) Color(0xFF4CAF50).copy(0.1f) else MaterialTheme.colorScheme.errorContainer.copy(0.5f),
                                shape    = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        if (isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                        null,
                                        tint = if (isSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        state.testResult ?: "",
                                        style    = MaterialTheme.typography.bodySmall,
                                        color    = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        // Action buttons
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick  = onTest,
                                modifier = Modifier.weight(1f).height(46.dp),
                                enabled  = !state.isTesting && state.isKeyValid,
                                shape    = RoundedCornerShape(14.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor   = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                if (state.isTesting) {
                                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.BugReport, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("TEST", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick  = { showConfigSave = true },
                                modifier = Modifier.weight(1f).height(46.dp),
                                enabled  = state.isKeyValid,
                                shape    = RoundedCornerShape(14.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor   = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            ) {
                                Icon(Icons.Rounded.Save, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (state.editingConfig != null) "UPDATE" else "SAVE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Config name + icon picker
                        AnimatedVisibility(visible = showConfigSave || state.editingConfig != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value         = configName,
                                    onValueChange = { configName = it },
                                    modifier      = Modifier.fillMaxWidth(),
                                    label         = { Text("Configuration name") },
                                    shape         = RoundedCornerShape(14.dp),
                                    singleLine    = true
                                )

                                Text("Agent Avatar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    listOf("AUTO", "GEMINI", "CHATGPT", "GROQ", "CLAUDE", "DEEPSEEK", "BOT", "SPARKLE", "BRAIN").forEach { iconKey ->
                                        val isSelected = state.selectedIcon == iconKey
                                        Surface(
                                            onClick  = { onIconChange(iconKey) },
                                            modifier = Modifier.size(46.dp),
                                            shape    = RoundedCornerShape(14.dp),
                                            color    = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                            border   = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    getIconForConfig(iconKey, state.provider), null,
                                                    modifier = Modifier.size(22.dp),
                                                    tint     = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick  = { if (configName.isNotBlank()) { onSaveConfig(configName); showConfigSave = false; configName = "" } },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    enabled  = configName.isNotBlank(),
                                    shape    = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Apply & Save Configuration", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Configs tab
                    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(savedConfigs) { _, config ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(18.dp),
                                color    = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(Modifier.size(40.dp), CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(getIconForConfig(config.iconRes, config.provider), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(config.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("${config.provider} · ${config.model}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                                    }
                                    IconButton(onClick = { onEditConfig(config); activeTab = 0 }) {
                                        Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { onDeleteConfig(config) }) {
                                        Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.error.copy(0.7f))
                                    }
                                }
                            }
                        }
                        if (savedConfigs.isEmpty()) {
                            item {
                                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Rounded.FolderOff, null, modifier = Modifier.size(44.dp).alpha(0.18f))
                                    Spacer(Modifier.height(10.dp))
                                    Text("No saved configurations", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = onSave,
                enabled  = state.isKeyValid,
                shape    = RoundedCornerShape(14.dp),
                modifier = Modifier.height(46.dp)
            ) { Text("Save as Default", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showTutorial) GuideDialog { showTutorial = false }
}

@Composable
private fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.65f))
        content()
    }
}

// ─────────────────────────────────────────────────────────────
//  Guide dialog
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideDialog(onDismiss: () -> Unit) {
    val providers  = listOf("Gemini", "ChatGPT", "Groq", "Claude", "DeepSeek", "OpenRouter")
    val pagerState = rememberPagerState { providers.size }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape            = RoundedCornerShape(28.dp),
        title = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SETUP GUIDE", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, letterSpacing = 1.5.sp)
                Text("Swipe for each AI provider", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().height(340.dp)) {
                HorizontalPager(state = pagerState, Modifier.fillMaxWidth().weight(1f)) { page ->
                    val provider       = providers[page]
                    val tutorialSteps  = AiSettingsHelper.tutorials[provider] ?: emptyList()
                    val info           = AiSettingsHelper.detailedInfo[provider] ?: ""

                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(Modifier.size(48.dp), CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(getIconForConfig("AUTO", provider), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(provider, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                        Text(info, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))

                        tutorialSteps.forEachIndexed { index, step ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                                Text("${index + 1}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp))
                                Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Page indicator
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                    providers.indices.forEach { i ->
                        Box(
                            Modifier
                                .padding(3.dp)
                                .clip(CircleShape)
                                .size(if (pagerState.currentPage == i) 8.dp else 6.dp)
                                .background(
                                    if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(0.2f)
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) { Text("Got it", fontWeight = FontWeight.Bold) }
        }
    )
}