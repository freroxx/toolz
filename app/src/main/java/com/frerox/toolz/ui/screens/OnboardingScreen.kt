package com.frerox.toolz.ui.screens

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.ai.AiSettingsHelper
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onFinish: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val vibrationManager = LocalVibrationManager.current
    val pagerState = rememberPagerState(pageCount = { 8 })
    var showSkipDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with Skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Progress Indicator
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(8) { index ->
                        val active = index <= pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .width(if (index == pagerState.currentPage) 24.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                                .animateContentSize()
                        )
                    }
                }

                TextButton(
                    onClick = { 
                        vibrationManager?.vibrateClick()
                        showSkipDialog = true 
                    }
                ) {
                    Text(
                        "SKIP", 
                        style = MaterialTheme.typography.labelLarge, 
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false // Forced progression
            ) { page ->
                when (page) {
                    0 -> WelcomeStep(onNext = { 
                        vibrationManager?.vibrateClick()
                        scope.launch { pagerState.animateScrollToPage(1) }
                    })
                    1 -> PersonalityStep(
                        name = uiState.name,
                        onNameChange = viewModel::updateName,
                        onNext = {
                            vibrationManager?.vibrateClick()
                            scope.launch { pagerState.animateScrollToPage(2) }
                        }
                    )
                    2 -> VisualsStep(
                        themeMode = uiState.themeMode,
                        dynamicColor = uiState.dynamicColor,
                        gradient = uiState.backgroundGradient,
                        onThemeChange = viewModel::updateTheme,
                        onDynamicColorChange = viewModel::updateDynamicColor,
                        onGradientChange = viewModel::updateGradient,
                        onNext = {
                            vibrationManager?.vibrateClick()
                            scope.launch { pagerState.animateScrollToPage(3) }
                        }
                    )
                    3 -> PerformanceStep(
                        specs = uiState.deviceSpecs,
                        performanceMode = uiState.performanceMode,
                        onPerformanceModeChange = viewModel::updatePerformanceMode,
                        onNext = {
                            vibrationManager?.vibrateClick()
                            scope.launch { pagerState.animateScrollToPage(4) }
                        }
                    )
                    4 -> IntelligenceStep(
                        apiKey = uiState.groqApiKey,
                        onApiKeyChange = viewModel::updateGroqKey,
                        onNext = {
                            vibrationManager?.vibrateClick()
                            scope.launch { pagerState.animateScrollToPage(5) }
                        }
                    )
                    5 -> ProtocolsStep(
                        onNext = {
                            vibrationManager?.vibrateClick()
                            scope.launch { pagerState.animateScrollToPage(6) }
                        }
                    )
                    6 -> AlertsStep(
                        enabled = uiState.notificationsEnabled,
                        vaultEnabled = uiState.vaultEnabled,
                        onEnabledChange = viewModel::updateNotifications,
                        onVaultChange = viewModel::updateVault,
                        onNext = {
                            vibrationManager?.vibrateClick()
                            scope.launch { pagerState.animateScrollToPage(7) }
                        }
                    )
                    7 -> ReadyStep(
                        name = uiState.name,
                        onComplete = {
                            vibrationManager?.vibrateSuccess()
                            viewModel.finishOnboarding(onFinish)
                        }
                    )
                }
            }
        }
    }

    if (showSkipDialog) {
        SkipConfirmationDialog(
            onConfirm = {
                showSkipDialog = false
                viewModel.skipOnboarding(onFinish)
            },
            onDismiss = { showSkipDialog = false }
        )
    }
}

@Composable
fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "icon")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "scale"
        )

        Surface(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = RoundedCornerShape(48.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(48.dp))
        
        Text(
            text = "TOOLZ",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            letterSpacing = (-2).sp
        )
        
        Text(
            text = "Orchestrate your mobile life.",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(24.dp))
        
        Text(
            text = "30+ precision instruments designed for speed, privacy, and absolute control.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(64.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .bouncyClick {},
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("INITIALIZE SETUP", fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.sp)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
        }
    }
}

@Composable
fun PersonalityStep(name: String, onNameChange: (String) -> Unit, onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.Face, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(32.dp))
        Text("Your Identity", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text("How should Toolz address you?", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        
        Spacer(Modifier.height(48.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text("Agent Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { if (name.isNotBlank()) onNext() })
        )
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp),
            enabled = name.isNotBlank()
        ) {
            Text("CONTINUE", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun VisualsStep(
    themeMode: String,
    dynamicColor: Boolean,
    gradient: Boolean,
    onThemeChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onGradientChange: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Visual Interface", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text("Personalize the look and feel.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        
        Spacer(Modifier.height(40.dp))
        
        Text("THEME MODE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("LIGHT", "SYSTEM", "DARK").forEach { mode ->
                Surface(
                    modifier = Modifier.weight(1f).bouncyClick { onThemeChange(mode) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (themeMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    Text(
                        mode, 
                        modifier = Modifier.padding(16.dp), 
                        textAlign = TextAlign.Center, 
                        fontWeight = FontWeight.Black,
                        color = if (themeMode == mode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        OnboardingToggle(
            title = "Dynamic Color",
            desc = "Synchronize with system wallpaper.",
            icon = Icons.Rounded.Palette,
            checked = dynamicColor,
            onCheckedChange = onDynamicColorChange
        )
        
        Spacer(Modifier.height(16.dp))
        
        OnboardingToggle(
            title = "Glass Morphism",
            desc = "Premium animated background gradients.",
            icon = Icons.Rounded.Gradient,
            checked = gradient,
            onCheckedChange = onGradientChange
        )
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("SAVE VISUALS", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun PerformanceStep(
    specs: com.frerox.toolz.util.DeviceSpecHelper.DeviceSpecs?,
    performanceMode: Boolean,
    onPerformanceModeChange: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Hardware Scan", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text("Optimizing for your specific device.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        
        Spacer(Modifier.height(40.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("SYSTEM RESOURCES", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Detected RAM: ${String.format("%.1f", specs?.totalRamGb ?: 0.0)} GB", 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Android Version: ${specs?.androidVersion ?: "Unknown"}", 
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        OnboardingToggle(
            title = "Performance Mode",
            desc = "Disables heavy animations and blurs to save resources.",
            icon = Icons.Rounded.Speed,
            checked = performanceMode,
            onCheckedChange = onPerformanceModeChange
        )
        
        if (specs?.recommendPerformanceMode == true && !performanceMode) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Recommended for your device for a smoother experience.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("SET PERFORMANCE", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun IntelligenceStep(apiKey: String, onApiKeyChange: (String) -> Unit, onNext: () -> Unit) {
    var showGuide by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Intelligence", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text("Powered by Groq for lightspeed results.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        
        Spacer(Modifier.height(40.dp))
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            placeholder = { Text("Groq API Key (Optional)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            trailingIcon = {
                IconButton(onClick = { showGuide = true }) {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        )
        
        Spacer(Modifier.height(16.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth().bouncyClick { showGuide = true },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Why use Groq?", fontWeight = FontWeight.Black)
                    Text("Free, fast, and privacy-focused AI responses.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(if (apiKey.isBlank()) "MAYBE LATER" else "CONNECT ENGINE", fontWeight = FontWeight.Black)
        }
    }
    
    if (showGuide) {
        Dialog(onDismissRequest = { showGuide = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Groq Setup Guide", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(16.dp))
                    val steps = AiSettingsHelper.tutorials["Groq"] ?: emptyList()
                    steps.forEachIndexed { index, step ->
                        Text("${index + 1}. $step", modifier = Modifier.padding(vertical = 4.dp), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { showGuide = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("GOT IT", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ProtocolsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var usageStatsGranted by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var notificationListenerGranted by remember { mutableStateOf(hasNotificationListenerPermission(context)) }
    var accessibilityGranted by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageStatsGranted = hasUsageStatsPermission(context)
                notificationListenerGranted = hasNotificationListenerPermission(context)
                accessibilityGranted = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val systemPermissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACTIVITY_RECOGNITION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            @Suppress("DEPRECATION")
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            @Suppress("DEPRECATION")
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    val permissionsState = rememberMultiplePermissionsState(systemPermissions)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Text("Protocols", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text("Authorize access to system hardware.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        
        Spacer(Modifier.height(32.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            item {
                PermissionCard(
                    title = "Hardware Suite",
                    desc = "Camera, Flash, and Sensors.",
                    icon = Icons.Rounded.DeveloperBoard,
                    granted = permissionsState.allPermissionsGranted,
                    onClick = { permissionsState.launchMultiplePermissionRequest() }
                )
            }
            item {
                PermissionCard(
                    title = "Analytics Engine",
                    desc = "Usage stats for focus mode.",
                    icon = Icons.Rounded.Analytics,
                    granted = usageStatsGranted,
                    onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                )
            }
            item {
                PermissionCard(
                    title = "System Vault",
                    desc = "Listen for notifications.",
                    icon = Icons.Rounded.Security,
                    granted = notificationListenerGranted,
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                )
            }
            item {
                PermissionCard(
                    title = "Control Bridge",
                    desc = "Accessibility for advanced tools.",
                    icon = Icons.Rounded.Hub,
                    granted = accessibilityGranted,
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("NEXT PROTOCOL", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun AlertsStep(
    enabled: Boolean,
    vaultEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onVaultChange: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Alerts", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text("Manage how Toolz communicates.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        
        Spacer(Modifier.height(40.dp))
        
        OnboardingToggle(
            title = "Push Notifications",
            desc = "Stay informed about task updates.",
            icon = Icons.Rounded.NotificationsActive,
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
        
        Spacer(Modifier.height(16.dp))
        
        OnboardingToggle(
            title = "Notification Vault",
            desc = "Securely index and search alerts.",
            icon = Icons.Rounded.VpnKey,
            checked = vaultEnabled,
            onCheckedChange = onVaultChange
        )
        
        Spacer(Modifier.height(48.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("SET ALERTS", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun ReadyStep(name: String, onComplete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        
        Spacer(Modifier.height(40.dp))
        
        Text(
            "Ready, ${name.ifBlank { "Agent" }}!", 
            style = MaterialTheme.typography.displaySmall, 
            fontWeight = FontWeight.Black
        )
        Text(
            "All systems are initialized and ready for deployment.", 
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, 
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(64.dp))
        
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("ENTER DASHBOARD", fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
    }
}

@Composable
fun OnboardingToggle(
    title: String,
    desc: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().bouncyClick { onCheckedChange(!checked) },
        shape = RoundedCornerShape(24.dp),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) 
                                   else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    desc: String,
    icon: ImageVector,
    granted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (granted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) 
                                   else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (granted) Icons.Rounded.CheckCircle else icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(desc, style = MaterialTheme.typography.labelSmall)
            }
            if (!granted) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, modifier = Modifier.alpha(0.3f))
            }
        }
    }
}

@Composable
fun SkipConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Are you sure?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Onboarding helps Toolz optimize itself for your hardware and preferences. Skipping may result in a sub-optimal experience.",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("BACK TO SETUP", fontWeight = FontWeight.Black)
                }
                TextButton(onClick = onConfirm, modifier = Modifier.padding(top = 8.dp)) {
                    Text("SKIP ANYWAY", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun hasNotificationListenerPermission(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(context.packageName)
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
    return enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}
