package com.frerox.toolz.ui.screens.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.util.CryptoAlgorithm
import com.frerox.toolz.util.CryptoFormat
import com.frerox.toolz.util.CryptoManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartEncrypterScreen(
    onBack: () -> Unit,
    viewModel: SmartEncrypterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Clipboard detection on start
    LaunchedEffect(Unit) {
        val systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = systemClipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString() ?: ""
            if (text.isNotBlank()) {
                val format = CryptoManager.detectFormat(text)
                if (format != CryptoFormat.PLAINTEXT) {
                    val result = snackbarHostState.showSnackbar(
                        message = "Encoded text found in clipboard.",
                        actionLabel = "LOAD",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onInputChanged(text)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Smart Encrypter", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleSecureMode) {
                        Icon(
                            if (uiState.isSecureMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Secure Mode",
                            tint = if (uiState.isSecureMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Smart Input Panel
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = viewModel::onInputChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Input Text") },
                        placeholder = { Text("Enter text to encrypt or decode...") },
                        visualTransformation = if (uiState.isSecureMode) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(keyboardType = if (uiState.isSecureMode) KeyboardType.Password else KeyboardType.Text),
                        shape = RoundedCornerShape(24.dp),
                        minLines = 3,
                        maxLines = 8,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val text = clipboardManager.getText()?.text ?: ""
                                    viewModel.onInputChanged(text)
                                },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Icon(Icons.Rounded.ContentPasteGo, "Paste & Analyze", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }

                AnimatedVisibility(
                    visible = uiState.inputText.isNotBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Detected: ${uiState.detectedFormat}") },
                        leadingIcon = {
                            Icon(
                                when (uiState.detectedFormat) {
                                    CryptoFormat.BASE64 -> Icons.Rounded.Code
                                    CryptoFormat.HEX -> Icons.Rounded.Hexagon
                                    CryptoFormat.BINARY -> Icons.Rounded.Memory
                                    else -> Icons.Rounded.TextFields
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = null,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Algorithm Selector
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Select Algorithm",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    items(CryptoAlgorithm.entries) { algo ->
                        val selected = uiState.selectedAlgorithm == algo
                        val scale by animateFloatAsState(
                            targetValue = if (selected) 1.05f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "scale"
                        )
                        
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onAlgorithmSelected(algo) },
                            label = { Text(algo.name) },
                            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // AES Configuration
            AnimatedVisibility(
                visible = uiState.selectedAlgorithm == CryptoAlgorithm.AES,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AES Security Key") },
                    placeholder = { Text("Enter secret password...") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary) }
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = viewModel::encrypt,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.EnhancedEncryption, null)
                        Spacer(Modifier.width(8.dp))
                        Text("ENCRYPT", fontWeight = FontWeight.Black)
                    }
                }
                FilledTonalButton(
                    onClick = viewModel::decrypt,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    enabled = !uiState.isLoading
                ) {
                    Icon(Icons.Rounded.NoEncryption, null)
                    Spacer(Modifier.width(8.dp))
                    Text("DECODE", fontWeight = FontWeight.Black)
                }
            }

            // Error Message
            uiState.error?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Error, null, tint = MaterialTheme.colorScheme.error)
                        Text(err, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // Result Panel
            AnimatedVisibility(
                visible = uiState.resultText.isNotBlank(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Result Output", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            IconButton(onClick = viewModel::clearResult, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                uiState.resultText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            var copied by remember { mutableStateOf(false) }
                            
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(uiState.resultText))
                                    copied = true
                                },
                                modifier = Modifier.bouncyClick { }
                            ) {
                                AnimatedContent(targetState = copied) { isCopied ->
                                    if (isCopied) {
                                        Icon(Icons.Rounded.Check, "Copied", tint = MaterialTheme.colorScheme.primary)
                                        LaunchedEffect(Unit) { 
                                            kotlinx.coroutines.delay(2000)
                                            copied = false 
                                        }
                                    } else {
                                        Icon(Icons.Rounded.ContentCopy, "Copy")
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, uiState.resultText)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Encrypted Text"))
                                }
                            ) {
                                Icon(Icons.Rounded.Share, "Share")
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(40.dp))
        }
    }
}
