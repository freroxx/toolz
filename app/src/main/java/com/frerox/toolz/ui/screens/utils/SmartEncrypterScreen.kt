package com.frerox.toolz.ui.screens.utils

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.crypto.CryptoHistoryEntry
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.horizontalFadingEdges
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.frerox.toolz.util.CryptoManager
import com.frerox.toolz.util.CryptoManager.CryptoAlgorithm
import com.frerox.toolz.util.CryptoManager.CryptoFormat
import com.frerox.toolz.util.CryptoManager.CryptoOperation
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartEncrypterScreen(
    onBack: () -> Unit,
    viewModel: SmartEncrypterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showHistory by remember { mutableStateOf(false) }
    var showFullQr by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "LivePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Encrypter", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        AnimatedVisibility(visible = uiState.isLiveEnabled) {
                            Text("LIVE MODE ACTIVE", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD600), fontWeight = FontWeight.Bold)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleLiveMode) {
                        Box(contentAlignment = Alignment.Center) {
                            if (uiState.isLiveEnabled) {
                                Surface(
                                    modifier = Modifier.size(32.dp).scale(pulseScale),
                                    shape = CircleShape,
                                    color = Color(0xFFFFD600).copy(alpha = 0.2f)
                                ) {}
                            }
                            Icon(
                                if (uiState.isLiveEnabled) Icons.Rounded.Bolt else Icons.Rounded.FlashOff,
                                contentDescription = "Live Mode",
                                tint = if (uiState.isLiveEnabled) Color(0xFFFFD600) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = viewModel::toggleSecureMode) {
                        Icon(
                            if (uiState.isSecureMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Secure Mode",
                            tint = if (uiState.isSecureMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Rounded.History, contentDescription = "History")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Smart Input Panel
            item {
                CryptoPanel(
                    title = "Input Payload",
                    icon = Icons.AutoMirrored.Rounded.TextSnippet
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = uiState.inputText,
                            onValueChange = viewModel::onInputChanged,
                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                            placeholder = { Text("Enter text to encrypt, hash or encode...") },
                            visualTransformation = if (uiState.isSecureMode) PasswordVisualTransformation() else VisualTransformation.None,
                            keyboardOptions = KeyboardOptions(keyboardType = if (uiState.isSecureMode) KeyboardType.Password else KeyboardType.Text),
                            shape = RoundedCornerShape(28.dp),
                            minLines = 3,
                            maxLines = 8,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val text = clipboardManager.getText()?.text ?: ""
                                        viewModel.onInputChanged(text)
                                    },
                                    modifier = Modifier.padding(end = 4.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).size(36.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentPaste, "Paste", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(
                                visible = uiState.inputText.isNotBlank(),
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(0.5f),
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            when (uiState.detectedFormat) {
                                                CryptoFormat.BASE64 -> Icons.Rounded.Code
                                                CryptoFormat.HEX -> Icons.Rounded.Hexagon
                                                CryptoFormat.BINARY -> Icons.Rounded.Memory
                                                else -> Icons.Rounded.TextFields
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            "Detected: ${uiState.detectedFormat}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                
                            if (uiState.isLiveEnabled) {
                                val smartAutoBg by animateColorAsState(
                                    if (uiState.isSmartAutoEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    label = "SmartAutoBg"
                                )
                                val smartAutoContent by animateColorAsState(
                                    if (uiState.isSmartAutoEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    label = "SmartAutoContent"
                                )

                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .bouncyClick { viewModel.toggleSmartAuto() }
                                        .background(smartAutoBg)
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        if (uiState.isSmartAutoEnabled) Icons.Rounded.AutoAwesome else Icons.Rounded.RadioButtonUnchecked,
                                        null,
                                        modifier = Modifier.size(18.dp),
                                        tint = smartAutoContent
                                    )
                                    Text(
                                        if (uiState.isSmartAutoEnabled) "Smart Auto" else "Manual",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = smartAutoContent
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Algorithm Selection
            item {
                CryptoPanel(
                    title = "Choose Algorithm",
                    icon = Icons.Rounded.Tune
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { viewModel.toggleAlgorithmSection() },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (uiState.isAlgorithmSectionExpanded) "Tap to collapse" else "Tap to expand",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    if (uiState.isAlgorithmSectionExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        AnimatedVisibility(
                            visible = uiState.isAlgorithmSectionExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            AlgorithmSelector(
                                selected = uiState.selectedAlgorithm,
                                onSelected = viewModel::onAlgorithmSelected
                            )
                        }
                    }
                }
            }

            // Security Configuration
            item {
                AnimatedVisibility(
                    visible = listOf(CryptoAlgorithm.AES, CryptoAlgorithm.CHACHA20).contains(uiState.selectedAlgorithm),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    CryptoPanel(
                        title = "Security Key",
                        icon = Icons.Rounded.Security
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = uiState.password,
                                onValueChange = viewModel::onPasswordChanged,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter secret password...") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = RoundedCornerShape(20.dp),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            
                            // Strength Indicator
                            PasswordStrengthIndicator(strength = uiState.passwordStrength)

                            // Auto Clear Toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { viewModel.toggleAutoClear() }
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text("Auto-clear result (30s)", style = MaterialTheme.typography.bodyMedium)
                                }
                                Switch(
                                    checked = uiState.isAutoClearEnabled,
                                    onCheckedChange = { viewModel.toggleAutoClear() },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Action Center
            item {
                AnimatedVisibility(visible = !uiState.isLiveEnabled || !uiState.isSmartAutoEnabled || uiState.isManualSelectionActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ActionButton(
                            text = if (uiState.suggestedOperation == CryptoOperation.DECRYPT || uiState.suggestedOperation == CryptoOperation.DECODE) "DECRYPT" else "ENCRYPT",
                            icon = if (uiState.suggestedOperation == CryptoOperation.DECRYPT || uiState.suggestedOperation == CryptoOperation.DECODE) Icons.Rounded.NoEncryption else Icons.Rounded.EnhancedEncryption,
                            onClick = {
                                if (uiState.suggestedOperation == CryptoOperation.DECRYPT || uiState.suggestedOperation == CryptoOperation.DECODE) viewModel.decrypt()
                                else viewModel.encrypt()
                            },
                            isLoading = uiState.isLoading,
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                        
                        val canToggle = !listOf(
                            CryptoAlgorithm.MD5, CryptoAlgorithm.SHA1, CryptoAlgorithm.SHA256, CryptoAlgorithm.SHA512
                        ).contains(uiState.selectedAlgorithm)

                        if (canToggle) {
                            ActionButton(
                                text = if (uiState.suggestedOperation == CryptoOperation.DECRYPT || uiState.suggestedOperation == CryptoOperation.DECODE) "ENCRYPT" else "DECRYPT",
                                icon = if (uiState.suggestedOperation == CryptoOperation.DECRYPT || uiState.suggestedOperation == CryptoOperation.DECODE) Icons.Rounded.EnhancedEncryption else Icons.Rounded.NoEncryption,
                                onClick = {
                                    if (uiState.suggestedOperation == CryptoOperation.DECRYPT || uiState.suggestedOperation == CryptoOperation.DECODE) viewModel.encrypt()
                                    else viewModel.decrypt()
                                },
                                isLoading = uiState.isLoading,
                                modifier = Modifier.weight(1f),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Result Center
            item {
                AnimatedVisibility(
                    visible = uiState.resultText.isNotBlank(),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    ResultPanel(
                        result = uiState.resultText,
                        qrCode = uiState.qrCode,
                        autoClearSeconds = uiState.autoClearSeconds,
                        onClear = viewModel::clearResult,
                        onGenerateQr = viewModel::generateQr,
                        onOpenQrFull = { showFullQr = true },
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(uiState.resultText))
                        },
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, uiState.resultText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Crypto Result"))
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showHistory) {
        HistoryBottomSheet(
            history = history,
            onDismiss = { showHistory = false },
            onDelete = viewModel::deleteHistoryEntry,
            onClear = viewModel::clearHistory,
            onSelect = { 
                viewModel.restoreHistoryEntry(it)
                showHistory = false
            }
        )
    }

    if (showFullQr) {
        FullScreenQrDialog(
            result = uiState.resultText,
            foreColor = uiState.qrForeColor,
            backColor = uiState.qrBackColor,
            dotStyle = uiState.qrStyle,
            noteText = uiState.qrNoteText,
            noteSize = uiState.qrNoteSize,
            notePosition = uiState.qrNotePosition,
            isNoteEnabled = uiState.isQrNoteEnabled,
            qrCode = uiState.qrCode,
            isLoading = uiState.isQrLoading,
            onDismiss = { showFullQr = false },
            onUpdateCustomization = viewModel::updateQrCustomization
        )
    }

    // Error Snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }
}

@Composable
fun CryptoPanel(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        icon, 
                        null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp).size(24.dp)
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
            content()
        }
    }
}

@Composable
fun AlgorithmSelector(
    selected: CryptoAlgorithm,
    onSelected: (CryptoAlgorithm) -> Unit
) {
    val groups = listOf(
        "Standard" to listOf(CryptoAlgorithm.AES, CryptoAlgorithm.CHACHA20),
        "Encoding" to listOf(CryptoAlgorithm.BASE64, CryptoAlgorithm.HEX, CryptoAlgorithm.BINARY, CryptoAlgorithm.BASE32, CryptoAlgorithm.URL),
        "Hashing" to listOf(CryptoAlgorithm.SHA256, CryptoAlgorithm.SHA512, CryptoAlgorithm.SHA1, CryptoAlgorithm.MD5),
        "Fun" to listOf(CryptoAlgorithm.ROT13, CryptoAlgorithm.MORSE)
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        groups.forEach { (name, algos) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalFadingEdges(left = 16.dp, right = 16.dp)
                ) {
                    items(algos) { algo ->
                        FilterChip(
                            selected = selected == algo,
                            onClick = { onSelected(algo) },
                            label = { Text(algo.name) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PasswordStrengthIndicator(strength: Float) {
    val color = when {
        strength < 0.4f -> MaterialTheme.colorScheme.error
        strength < 0.7f -> Color(0xFFFFA000) // Orange
        else -> Color(0xFF4CAF50) // Green
    }
    
    val text = when {
        strength < 0.4f -> "Weak"
        strength < 0.7f -> "Medium"
        else -> "Strong"
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Security Strength", style = MaterialTheme.typography.labelSmall)
            Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { strength },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(64.dp)
            .bouncyClick { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        enabled = !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = contentColor, strokeWidth = 2.dp)
        } else {
            Icon(icon, null)
            Spacer(Modifier.width(8.dp))
            Text(text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun ResultPanel(
    result: String,
    qrCode: Bitmap?,
    autoClearSeconds: Int,
    onClear: () -> Unit,
    onGenerateQr: () -> Unit,
    onOpenQrFull: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.SpaceBetween, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(6.dp).size(16.dp))
                    }
                    Text("Result", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (autoClearSeconds > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                "Clearing in ${autoClearSeconds}s",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    
                    IconButton(onClick = onClear, modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surface.copy(0.3f), CircleShape)) {
                        Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.1f))
            ) {
                AnimatedContent(
                    targetState = result,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
                            .togetherWith(fadeOut(animationSpec = tween(90)))
                    },
                    label = "ResultAnimation"
                ) { targetResult ->
                    Text(
                        targetResult,
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            AnimatedVisibility(visible = qrCode != null) {
                qrCode?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .clickable(onClick = onOpenQrFull)
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            shadowElevation = 4.dp
                        ) {
                            Icon(
                                Icons.Rounded.Fullscreen, 
                                null, 
                                modifier = Modifier.padding(8.dp).size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
                
                if (qrCode == null) {
                    FilledTonalButton(
                        onClick = onGenerateQr,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.QrCode, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("QR")
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            saveAndShareQr(context, qrCode)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBottomSheet(
    history: List<CryptoHistoryEntry>,
    onDismiss: () -> Unit,
    onDelete: (CryptoHistoryEntry) -> Unit,
    onClear: () -> Unit,
    onSelect: (CryptoHistoryEntry) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No history yet", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fadingEdges(top = 16.dp, bottom = 16.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(history) { entry ->
                        HistoryItem(
                            entry = entry,
                            onClick = { onSelect(entry) },
                            onDelete = { onDelete(entry) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryItem(
    entry: CryptoHistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val time = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
    var showMenu by remember { mutableStateOf(false) }
    
    Box {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            when (entry.type) {
                                "ENCRYPT", "DECRYPT" -> Icons.Rounded.Lock
                                "HASH" -> Icons.Rounded.Fingerprint
                                "ENCODE", "DECODE" -> Icons.Rounded.Code
                                else -> Icons.Rounded.Code
                            },
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            entry.algorithm,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.secondary.copy(0.1f),
                            shape = CircleShape
                        ) {
                            Text(
                                entry.type,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Text(
                        entry.result,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            offset = androidx.compose.ui.unit.DpOffset(x = 100.dp, y = 0.dp),
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            DropdownMenuItem(
                text = { Text("Copy Result") },
                onClick = {
                    clipboardManager.setText(AnnotatedString(entry.result))
                    showMenu = false
                },
                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Share Result") },
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, entry.result)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Result"))
                    showMenu = false
                },
                leadingIcon = { Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    onDelete()
                    showMenu = false
                },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenQrDialog(
    result: String,
    foreColor: Int,
    backColor: Int,
    dotStyle: String,
    noteText: String,
    noteSize: Float,
    notePosition: String,
    isNoteEnabled: Boolean,
    qrCode: Bitmap?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onUpdateCustomization: (Int, Int, String, String, Float, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    
    // Caffeinate: Keep screen on
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, null)
                    }
                    Text("QR Customizer", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = {
                        qrCode?.let { saveAndShareQr(context, it) }
                    }) {
                        Icon(Icons.Rounded.Share, null)
                    }
                }

                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(backColor))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    qrCode?.let {
                        androidx.compose.foundation.Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.fillMaxSize().alpha(if (isLoading) 0.5f else 1f),
                            contentScale = ContentScale.Fit
                        )
                    }
                    
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = Color(foreColor),
                            strokeWidth = 4.dp
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Customization Controls
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Style & Colors", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            
                            // Dot Style
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Dot Style", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FilterChip(
                                        selected = dotStyle == "SQUARE",
                                        onClick = { onUpdateCustomization(foreColor, backColor, "SQUARE", noteText, noteSize, notePosition, isNoteEnabled) },
                                        label = { Text("Square") },
                                        leadingIcon = { if (dotStyle == "SQUARE") Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp)) },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    FilterChip(
                                        selected = dotStyle == "ROUNDED",
                                        onClick = { onUpdateCustomization(foreColor, backColor, "ROUNDED", noteText, noteSize, notePosition, isNoteEnabled) },
                                        label = { Text("Rounded") },
                                        leadingIcon = { if (dotStyle == "ROUNDED") Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp)) },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }

                            // Color Presets
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Quick Colors (Foreground)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                val presets = listOf(
                                    Color.Black, Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFF44336),
                                    Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFF009688), Color.White
                                )
                                
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(end = 12.dp)
                                ) {
                                    items(presets) { color ->
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                                .clickable { onUpdateCustomization(color.toArgb(), backColor, dotStyle, noteText, noteSize, notePosition, isNoteEnabled) }
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Quick Colors (Background)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                val presets = listOf(
                                    Color.White, Color.Black, Color(0xFFF5F5F5), Color(0xFFE3F2FD),
                                    Color(0xFFE8F5E9), Color(0xFFFFF3E0), Color(0xFFF3E5F5)
                                )
                                
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(end = 12.dp)
                                ) {
                                    items(presets) { color ->
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                                .clickable { onUpdateCustomization(foreColor, color.toArgb(), dotStyle, noteText, noteSize, notePosition, isNoteEnabled) }
                                        )
                                    }
                                }
                            }

                            // Custom Hex
                            var hexInput by remember { mutableStateOf(String.format("#%06X", (0xFFFFFF and foreColor))) }
                            OutlinedTextField(
                                value = hexInput,
                                onValueChange = { 
                                    hexInput = it
                                    try {
                                        val color = Color(android.graphics.Color.parseColor(it))
                                        onUpdateCustomization(color.toArgb(), backColor, dotStyle, noteText, noteSize, notePosition, isNoteEnabled)
                                    } catch (e: Exception) {}
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Custom Foreground Hex") },
                                shape = RoundedCornerShape(16.dp),
                                leadingIcon = { Icon(Icons.Rounded.Palette, null, tint = Color(foreColor)) },
                                singleLine = true
                            )
                        }
                    }

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.AutoMirrored.Rounded.StickyNote2, null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Include Note", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Switch(
                                    checked = isNoteEnabled,
                                    onCheckedChange = { 
                                        onUpdateCustomization(foreColor, backColor, dotStyle, noteText.ifEmpty { "Your Note" }, noteSize, notePosition, it)
                                    }
                                )
                            }

                            AnimatedVisibility(visible = isNoteEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OutlinedTextField(
                                        value = noteText,
                                        onValueChange = { onUpdateCustomization(foreColor, backColor, dotStyle, it, noteSize, notePosition, isNoteEnabled) },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("Enter note/link/text...") },
                                        shape = RoundedCornerShape(16.dp)
                                    )

                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Font Size", style = MaterialTheme.typography.labelMedium)
                                            Text("${noteSize.toInt()}px", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = noteSize,
                                            onValueChange = { onUpdateCustomization(foreColor, backColor, dotStyle, noteText, it, notePosition, isNoteEnabled) },
                                            valueRange = 10f..40f
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        FilterChip(
                                            selected = notePosition == "TOP",
                                            onClick = { onUpdateCustomization(foreColor, backColor, dotStyle, noteText, noteSize, "TOP", isNoteEnabled) },
                                            label = { Text("Top") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        FilterChip(
                                            selected = notePosition == "BOTTOM",
                                            onClick = { onUpdateCustomization(foreColor, backColor, dotStyle, noteText, noteSize, "BOTTOM", isNoteEnabled) },
                                            label = { Text("Bottom") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    OutlinedButton(
                        onClick = { 
                            clipboardManager.setText(AnnotatedString(result))
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Copy QR Content")
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        qrCode?.let { saveToGallery(context, it) }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = qrCode != null && !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Rounded.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save to Gallery", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

fun saveToGallery(context: android.content.Context, bitmap: Bitmap) {
    try {
        val filename = "QR_${System.currentTimeMillis()}.png"
        val outputStream: FileOutputStream
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
            }
            val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            outputStream = resolver.openOutputStream(imageUri!!) as FileOutputStream
        } else {
            val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            outputStream = FileOutputStream(image)
        }
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.close()
        android.widget.Toast.makeText(context, "Saved to Gallery", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Failed to save: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun saveAndShareQr(context: android.content.Context, bitmap: Bitmap) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "qr_code_${System.currentTimeMillis()}.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        if (contentUri != null) {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_STREAM, contentUri)
                type = "image/png"
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share QR Code"))
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Sharing failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}
