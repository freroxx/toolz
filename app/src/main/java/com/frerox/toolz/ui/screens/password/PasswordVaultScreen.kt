package com.frerox.toolz.ui.screens.password

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.frerox.toolz.data.password.PasswordEntity
import com.frerox.toolz.util.security.BiometricPromptUtils
import com.frerox.toolz.ui.theme.toolzBackground
import com.frerox.toolz.util.password.PasswordUtils
import com.frerox.toolz.ui.components.fadingEdges

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordVaultScreen(
    viewModel: PasswordVaultViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val categorizedPasswords by viewModel.categorizedPasswords.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val vaultStats by viewModel.vaultStats.collectAsState()

    var isUnlocked by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPassword by remember { mutableStateOf<PasswordEntity?>(null) }
    var passwordToDelete by remember { mutableStateOf<PasswordEntity?>(null) }
    var showGenerator by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importCsv(context, it) }
    }

    Box(modifier = Modifier.fillMaxSize().toolzBackground()) {
        AnimatedContent(
            targetState = isUnlocked,
            transitionSpec = {
                (fadeIn(animationSpec = tween(700, delayMillis = 150)) + 
                 scaleIn(initialScale = 0.95f, animationSpec = tween(700, delayMillis = 150)))
                    .togetherWith(fadeOut(animationSpec = tween(450)))
            },
            label = "vault_content"
        ) { unlocked ->
            if (unlocked) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    containerColor = Color.Transparent,
                    topBar = {
                        val collapsedFraction = scrollBehavior.state.collapsedFraction
                        
                        Surface(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .shadow(
                                    elevation = if (collapsedFraction > 0.05f) 16.dp else 4.dp,
                                    shape = RoundedCornerShape(32.dp),
                                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    spotColor = MaterialTheme.colorScheme.primary
                                )
                                .clip(RoundedCornerShape(32.dp)),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        ) {
                            LargeTopAppBar(
                                title = {
                                    Column(modifier = Modifier.padding(start = 12.dp)) {
                                        Text(
                                            "Vault",
                                            style = if (collapsedFraction > 0.5f) 
                                                MaterialTheme.typography.titleLarge 
                                            else 
                                                MaterialTheme.typography.displayLarge,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = if (collapsedFraction > 0.5f) 0.sp else (-3.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                        AnimatedVisibility(
                                            visible = collapsedFraction < 0.15f,
                                            enter = fadeIn() + expandVertically(),
                                            exit = fadeOut() + shrinkVertically()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Rounded.VerifiedUser, 
                                                    contentDescription = null, 
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    "Encrypted & Secure",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                )
                                            }
                                        }
                                    }
                                },
                                navigationIcon = {
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onBackClick()
                                        },
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .size(48.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape)
                                    ) {
                                        Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Back", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                actions = {
                                    Row(modifier = Modifier.padding(end = 8.dp)) {
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val autofillManager = context.getSystemService(AutofillManager::class.java)
                                                if (autofillManager != null && !autofillManager.hasEnabledAutofillServices()) {
                                                    try {
                                                        val intent = Intent("android.settings.REQUEST_SET_AUTOFILL_SERVICE")
                                                        intent.data = "package:${context.packageName}".toUri()
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        try {
                                                            context.startActivity(Intent("android.settings.AUTOFILL_SETTINGS"))
                                                        } catch (e2: Exception) {
                                                            Toast.makeText(context, "Autofill settings not found", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Autofill is active", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(Icons.Rounded.SettingsSuggest, contentDescription = "Autofill", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                csvPicker.launch("text/*")
                                            },
                                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(Icons.Rounded.FileUpload, contentDescription = "Import", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                scrollBehavior = scrollBehavior,
                                colors = TopAppBarDefaults.largeTopAppBarColors(
                                    containerColor = Color.Transparent,
                                    scrolledContainerColor = Color.Transparent
                                )
                            )
                        }
                    },
                    floatingActionButton = {
                        Column(horizontalAlignment = Alignment.End) {
                            FloatingActionButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showGenerator = true
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = RoundedCornerShape(20.dp),
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = "Generator")
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            LargeFloatingActionButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showAddDialog = true
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = RoundedCornerShape(28.dp),
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add Password", modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                    ) {
                        // Stats Overview
                        AnimatedVisibility(
                            visible = scrollBehavior.state.collapsedFraction < 0.1f,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatCard(
                                    label = "Total",
                                    value = vaultStats.total.toString(),
                                    icon = Icons.Rounded.Inventory2,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    label = "Breached",
                                    value = vaultStats.breached.toString(),
                                    icon = Icons.Rounded.GppBad,
                                    color = if (vaultStats.breached > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    label = "Weak",
                                    value = vaultStats.weak.toString(),
                                    icon = Icons.Rounded.Password,
                                    color = if (vaultStats.weak > 0) Color(0xFFFF9800) else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Floating Search Bar & Scan Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.onSearchQueryChange(it)
                                    },
                                    placeholder = { Text("Search vault...", style = MaterialTheme.typography.bodyLarge) },
                                    leadingIcon = { 
                                        Icon(
                                            Icons.Rounded.Search, 
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        ) 
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                    singleLine = true
                                )
                            }

                            ScanningButton(isScanning = isScanning, onClick = { viewModel.scanVault() })
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .fadingEdges(top = 16.dp, bottom = 32.dp),
                                contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 140.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                categorizedPasswords.forEach { (category, list) ->
                                    item(key = "header_$category") {
                                        CategoryHeader(category)
                                    }
                                    items(list, key = { it.id }) { password ->
                                        CredentialCard(
                                            password = password,
                                            onDelete = { passwordToDelete = password },
                                            onCheckPwned = { viewModel.checkPwned(password) },
                                            onEdit = { editingPassword = it }
                                        )
                                    }
                                }
                                
                                if (categorizedPasswords.isEmpty() && searchQuery.isNotEmpty()) {
                                    item {
                                        EmptySearchResult()
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                BiometricGate(onSuccess = { isUnlocked = true })
            }
        }
    }

    if (showAddDialog) {
        AddPasswordDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url, user, pass ->
                viewModel.addPassword(name, url, user, pass)
                showAddDialog = false
            }
        )
    }

    editingPassword?.let { password ->
        AddPasswordDialog(
            initialEntity = password,
            onDismiss = { editingPassword = null },
            onConfirm = { name, url, user, pass ->
                viewModel.updatePassword(password.copy(name = name, url = url, username = user, password = pass))
                editingPassword = null
            }
        )
    }

    passwordToDelete?.let { password ->
        AlertDialog(
            onDismissRequest = { passwordToDelete = null },
            title = { Text("Delete Credential?") },
            text = { Text("Are you sure you want to permanently delete the credentials for ${password.name}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePassword(password)
                        passwordToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { passwordToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showGenerator) {
        GeneratorBottomSheet(
            viewModel = viewModel,
            onDismiss = { showGenerator = false }
        )
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ScanningButton(isScanning: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scanPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(contentAlignment = Alignment.Center) {
        if (isScanning) {
            // High-quality sonar-like radar animation
            Canvas(modifier = Modifier.size(72.dp)) {
                // Expanding ripple
                drawCircle(
                    color = primaryColor,
                    radius = (size.minDimension / 2) * scanPulse,
                    alpha = (1.3f - scanPulse).coerceIn(0f, 0.3f),
                    style = Stroke(width = 2.dp.toPx())
                )
                
                // Sweep line
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.5f to primaryColor.copy(alpha = 0.1f),
                        1f to primaryColor
                    ),
                    startAngle = rotation,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        
        Surface(
            onClick = {
                if (!isScanning) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            },
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (isScanning) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = if (isScanning) 12.dp else 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isScanning) Icons.Rounded.WifiTethering else Icons.Rounded.Security,
                    contentDescription = "Scan Vault",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
fun CategoryHeader(name: String) {
    val color = when (name) {
        "MUST CHANGE" -> MaterialTheme.colorScheme.error
        "WEAK" -> Color(0xFFFF9800)
        "INCOMPLETE" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    ) {
        Surface(
            color = color.copy(alpha = 0.1f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, color.copy(alpha = 0.15f))
        ) {
            @Suppress("DEPRECATION")
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = color,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        HorizontalDivider(modifier = Modifier.weight(1f).alpha(0.08f), color = color)
    }
}

@Composable
fun EmptySearchResult() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.SearchOff, 
            contentDescription = null, 
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        @Suppress("DEPRECATION")
        Text(
            "NO MATCHES FOUND",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp
        )
        Text(
            "Try adjusting your search query.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BiometricGate(onSuccess: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    val pulseColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) 
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .drawBehind {
                            drawCircle(
                                color = pulseColor,
                                radius = size.minDimension / 2 * scale,
                                alpha = pulseAlpha
                            )
                        }
                )
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        BiometricPromptUtils.showBiometricPrompt(
                            activity = context as FragmentActivity,
                            onSuccess = { onSuccess() }
                        )
                    },
                    modifier = Modifier.size(120.dp),
                    shape = RoundedCornerShape(40.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 20.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Fingerprint,
                            contentDescription = "Unlock",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                "Vault Encrypted",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Verify identity to reveal secrets",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.alpha(0.8f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialCard(
    password: PasswordEntity,
    onDelete: () -> Unit,
    onCheckPwned: () -> Unit,
    onEdit: (PasswordEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val smartName = remember(password.name, password.url) {
        PasswordUtils.getSmartName(password.url, password.name)
    }

    val isIncomplete = password.password.isEmpty()

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEdit(password)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                }, label = "swipe_color"
            )
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Rounded.Edit
                SwipeToDismissBoxValue.EndToStart -> Icons.Rounded.Delete
                else -> null
            }
            val scale by animateFloatAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) 0.75f else 1.25f, label = "swipe_scale"
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
                        tint = if (direction == SwipeToDismissBoxValue.StartToEnd) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        },
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        val backgroundColor by animateColorAsState(
            targetValue = if (expanded) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            label = "bgColor"
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            border = BorderStroke(1.dp, if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                expanded = !expanded
            }
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = if (isIncomplete) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, (if (isIncomplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary).copy(alpha = 0.1f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            var loadFailed by remember { mutableStateOf(false) }
                            
                            val isApp = password.url?.startsWith("android://") == true
                            val packageName = if (isApp) password.url?.removePrefix("android://") else null

                            if (isApp && packageName != null) {
                                val icon = remember(packageName) { PasswordUtils.getAppIcon(context, packageName) }
                                if (icon != null) {
                                    AsyncImage(
                                        model = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp)
                                    )
                                } else {
                                    loadFailed = true
                                }
                            } else if (!password.url.isNullOrBlank()) {
                                val domain = remember(password.url) {
                                    try {
                                        val uri = if (!password.url!!.startsWith("http")) java.net.URI("https://${password.url}") else java.net.URI(password.url!!)
                                        uri.host?.removePrefix("www.")
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                if (domain != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("https://www.google.com/s2/favicons?domain=$domain&sz=128")
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
                                        onError = { loadFailed = true }
                                    )
                                } else {
                                    loadFailed = true
                                }
                            } else {
                                loadFailed = true
                            }

                            if (loadFailed) {
                                Text(
                                    text = smartName.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black,
                                    color = if (isIncomplete) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(18.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            smartName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            password.username,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isIncomplete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alpha(if (isIncomplete) 1f else 0.8f)
                        )
                    }

                    if (!isIncomplete) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                clipboardManager.setText(AnnotatedString(password.password))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Icon(
                            Icons.Rounded.WarningAmber,
                            contentDescription = "No password",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp).padding(4.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(spring(Spring.DampingRatioLowBouncy)) + fadeIn(),
                    exit = shrinkVertically(spring(Spring.DampingRatioLowBouncy)) + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(top = 20.dp)) {
                        if (!isIncomplete) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (revealed) password.password else "••••••••••••",
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = if (revealed) 0.sp else 3.sp
                                        ),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            revealed = !revealed
                                        }
                                    ) {
                                        Icon(
                                            if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = if (revealed) "Hide password" else "Show password",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = { onEdit(password) },
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                            ) {
                                Icon(Icons.Rounded.LockOpen, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text("Set Password", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isIncomplete) {
                                StrengthIndicator(password.strength)
                            } else {
                                @Suppress("DEPRECATION")
                                Text("NO PASSWORD SET", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Black)
                            }
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (!isIncomplete) {
                                    val pCount = password.pwnedCount ?: 0
                                    FilledTonalIconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onCheckPwned()
                                        },
                                        modifier = Modifier.size(48.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = if (pCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    ) {
                                        Icon(
                                            Icons.Rounded.Security,
                                            contentDescription = "Check Leak",
                                            tint = if (pCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.alpha(0.6f)
                                ) {
                                    Icon(Icons.Rounded.Swipe, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Swipe to edit/delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                        
                        if (password.pwnedCount != null && !isIncomplete) {
                            val pCount = password.pwnedCount ?: 0
                            Surface(
                                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                                color = (if (pCount > 0) MaterialTheme.colorScheme.errorContainer else Color(0xFFE8F5E9)).copy(alpha = 0.6f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, (if (pCount > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)).copy(alpha = 0.1f))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    Icon(
                                        if (pCount > 0) Icons.Rounded.GppBad else Icons.Rounded.Verified,
                                        contentDescription = null,
                                        tint = if (pCount > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = if (pCount > 0) "Breached in $pCount leaks!" else "Identity safe from leaks",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (pCount > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StrengthIndicator(strength: Int) {
    val color = when (strength) {
        0 -> Color(0xFFE53935)
        1 -> Color(0xFFFB8C00)
        2 -> Color(0xFFFDD835)
        3 -> Color(0xFF7CB342)
        else -> Color(0xFF43A047)
    }
    
    val strengthText = when (strength) {
        0 -> "CRITICAL"
        1 -> "WEAK"
        2 -> "MEDIUM"
        3 -> "STRONG"
        else -> "ELITE"
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { index ->
                val active = index <= strength
                val barColor = if (active) color else MaterialTheme.colorScheme.surfaceContainerHighest
                
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(if (active) 36.dp else 28.dp)
                        .clip(CircleShape)
                        .background(barColor)
                        .animateContentSize()
                )
            }
        }
        @Suppress("DEPRECATION")
        Text(
            text = strengthText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = color,
            letterSpacing = 1.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorBottomSheet(
    viewModel: PasswordVaultViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    val settings by viewModel.generatorSettings.collectAsState()
    
    var generatedPassword by remember { 
        mutableStateOf(com.frerox.toolz.util.password.PasswordGenerator.generate(
            settings.length.toInt(), 
            includeSymbols = settings.includeSymbols, 
            includeNumbers = settings.includeNumbers,
            includeUppercase = settings.includeUppercase
        )) 
    }

    val isStrong = settings.length >= 14 && settings.includeSymbols && settings.includeNumbers
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    val glowColor = MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 60.dp, height = 4.dp, color = MaterialTheme.colorScheme.outlineVariant) },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Password Engine",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        if (isStrong) {
                            drawRoundRect(
                                color = glowColor,
                                alpha = glowAlpha,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(32.dp.toPx())
                            )
                        }
                    },
                shape = RoundedCornerShape(32.dp),
                color = if (isStrong) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(2.dp, if (isStrong) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = generatedPassword,
                        transitionSpec = {
                            (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut())
                        },
                        modifier = Modifier.weight(1.0f),
                        label = "pass_anim"
                    ) { text ->
                        Text(
                            text, 
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = if (isStrong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            generatedPassword = com.frerox.toolz.util.password.PasswordGenerator.generate(
                                settings.length.toInt(), 
                                includeSymbols = settings.includeSymbols, 
                                includeNumbers = settings.includeNumbers,
                                includeUppercase = settings.includeUppercase
                            )
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Regenerate", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Length", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    AnimatedContent(targetState = settings.length.toInt(), label = "len_anim") { len ->
                        Text(
                            len.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Slider(
                    value = settings.length,
                    onValueChange = { 
                        if (it.toInt() != settings.length.toInt()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        viewModel.updateGeneratorSettings(settings.copy(length = it))
                    },
                    valueRange = 8f..64f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OptionChip(
                    selected = settings.includeUppercase,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.updateGeneratorSettings(settings.copy(includeUppercase = !settings.includeUppercase))
                    },
                    label = "A-Z",
                    icon = Icons.Rounded.TextFields,
                    modifier = Modifier.weight(1f)
                )
                OptionChip(
                    selected = settings.includeSymbols,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.updateGeneratorSettings(settings.copy(includeSymbols = !settings.includeSymbols))
                    },
                    label = "Symbols",
                    icon = Icons.Rounded.AlternateEmail,
                    modifier = Modifier.weight(1f)
                )
                OptionChip(
                    selected = settings.includeNumbers,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.updateGeneratorSettings(settings.copy(includeNumbers = !settings.includeNumbers))
                    },
                    label = "123",
                    icon = Icons.Rounded.Numbers,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
            
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    clipboardManager.setText(AnnotatedString(generatedPassword))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Copy Password", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun OptionChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(64.dp)
            .clickable { onClick() }
            .clip(RoundedCornerShape(20.dp)),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                modifier = Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            @Suppress("DEPRECATION")
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AddPasswordDialog(
    initialEntity: PasswordEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialEntity?.name ?: "") }
    var url by remember { mutableStateOf(initialEntity?.url ?: "") }
    var username by remember { mutableStateOf(initialEntity?.username ?: "") }
    var password by remember { mutableStateOf(initialEntity?.password ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    
    val haptic = LocalHapticFeedback.current

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onAppSelected = { appName, packageName ->
                name = appName
                url = "android://$packageName"
                showAppPicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(36.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (initialEntity == null) "New Entry" else "Edit Entry", 
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                )
                IconButton(
                    onClick = { showAppPicker = true },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Apps, contentDescription = "Import from App", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("Service Name") },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
                OutlinedTextField(
                    value = url, 
                    onValueChange = { url = it }, 
                    label = { Text("URL / App Package") },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
                OutlinedTextField(
                    value = username, 
                    onValueChange = { username = it }, 
                    label = { Text("Username / Email") },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            passwordVisible = !passwordVisible 
                        }) {
                            Icon(
                                if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, 
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (name.isNotBlank() && username.isNotBlank()) {
                        onConfirm(name, url.ifBlank { null }, username, password)
                    }
                },
                modifier = Modifier.height(64.dp).fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Text(if (initialEntity == null) "Secure Credentials" else "Update Credentials", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(56.dp)) { 
                Text("Cancel", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) 
            }
        }
    )
}

@Composable
fun AppPickerDialog(
    onDismiss: () -> Unit,
    onAppSelected: (String, String) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val apps = remember {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .sortedBy { pm.getApplicationLabel(it).toString() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        title = { Text("Select Source App", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(modifier = Modifier.height(450.dp)) {
                items(apps) { app ->
                    val name = pm.getApplicationLabel(app).toString()
                    val packageName = app.packageName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onAppSelected(name, packageName) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            AsyncImage(
                                model = pm.getApplicationIcon(app),
                                contentDescription = null,
                                modifier = Modifier.size(44.dp).padding(6.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            Text(packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
