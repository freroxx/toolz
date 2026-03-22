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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frerox.toolz.data.ai.AiSettingsHelper
import com.frerox.toolz.data.ai.AiSettingsManager
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    settingsRepository: SettingsRepository,
    aiSettingsManager: AiSettingsManager? = null,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }
    var grokApiKey by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val vibrationManager = LocalVibrationManager.current

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val color1 by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        targetValue = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "c1"
    )
    val color2 by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
        targetValue = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "c2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(color1, color2, MaterialTheme.colorScheme.background)))
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "steps"
        ) { currentStep ->
            when (currentStep) {
                1 -> WelcomeStep(onNext = { 
                    vibrationManager?.vibrateClick()
                    step = 2 
                })
                2 -> PermissionsStep(onNext = { 
                    vibrationManager?.vibrateClick()
                    step = 3 
                })
                3 -> NameStep(
                    name = name,
                    onNameChange = { name = it },
                    onComplete = {
                        vibrationManager?.vibrateClick()
                        step = 4
                    }
                )
                4 -> AiOnboardingStep(
                    apiKey = grokApiKey,
                    onApiKeyChange = { grokApiKey = it },
                    onComplete = {
                        vibrationManager?.vibrateSuccess()
                        scope.launch {
                            settingsRepository.setUserName(name)
                            if (grokApiKey.isNotBlank()) {
                                aiSettingsManager?.setApiKey(grokApiKey, "Groq")
                            }
                            settingsRepository.setOnboardingCompleted(true)
                            onFinish()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "icon")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "scale"
        )

        Surface(
            modifier = Modifier
                .size(140.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = RoundedCornerShape(48.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            shadowElevation = 12.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(48.dp))
        
        Text(
            text = "TOOLZ PRO",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            text = "Your device, fully orchestrated.\n30+ precision instruments optimized for performance.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp),
            lineHeight = 26.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(48.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WelcomeFeature(Icons.Rounded.Lock, "SECURE ARCHIVE", Modifier.weight(1f))
                WelcomeFeature(Icons.Rounded.WifiOff, "OFFLINE ENGINE", Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WelcomeFeature(Icons.Rounded.Speed, "RAW HARDWARE", Modifier.weight(1f))
                WelcomeFeature(Icons.Rounded.Memory, "AI OPTIMIZED", Modifier.weight(1f))
            }
        }
        
        Spacer(Modifier.height(64.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .bouncyClick {},
            shape = RoundedCornerShape(24.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Text("INITIALIZE ENGINE", fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun WelcomeFeature(icon: ImageVector, text: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vibrationManager = LocalVibrationManager.current
    
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
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp)
            .statusBarsPadding()
    ) {
        Text(
            text = "Core Protocols",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Enable the following modules to unlock the full potential of your device's hardware sensors and storage engines.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(36.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                PermissionCard(
                    title = "System I/O & Sensors",
                    desc = "Flashlight, GPS, Camera, and activity tracking engine.",
                    icon = Icons.Rounded.DeveloperBoard,
                    granted = permissionsState.allPermissionsGranted,
                    onClick = { 
                        vibrationManager?.vibrateTick()
                        permissionsState.launchMultiplePermissionRequest() 
                    }
                )
            }

            item {
                PermissionCard(
                    title = "Telemetry & Focus",
                    desc = "Required for screen-time analytics and Focus Flow.",
                    icon = Icons.Rounded.Timeline,
                    granted = usageStatsGranted,
                    onClick = { 
                        vibrationManager?.vibrateTick()
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) 
                    }
                )
            }

            item {
                PermissionCard(
                    title = "Archive Listener",
                    desc = "Securely indexes system alerts for Notification Vault.",
                    icon = Icons.Rounded.Security,
                    granted = notificationListenerGranted,
                    onClick = { 
                        vibrationManager?.vibrateTick()
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) 
                    }
                )
            }

            item {
                PermissionCard(
                    title = "System Bridge",
                    desc = "Accessibility layer for advanced hard-lock focus features.",
                    icon = Icons.Rounded.Hub,
                    granted = accessibilityGranted,
                    onClick = { 
                        vibrationManager?.vibrateTick()
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) 
                    }
                )
            }
        }
        
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .bouncyClick {},
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (permissionsState.allPermissionsGranted)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (permissionsState.allPermissionsGranted)
                        MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("NEXT STEP", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }

            TextButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    onNext()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text("SKIP TO DASHBOARD", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline, letterSpacing = 1.sp)
            }
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
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        color = if (granted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            1.5.dp,
            if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (granted) Icons.Rounded.CheckCircle else icon,
                        null,
                        tint = if (granted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, lineHeight = 16.sp, fontWeight = FontWeight.Black)
            }
            
            if (!granted) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
            }
        }
    }
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
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

@Composable
fun NameStep(
    name: String,
    onNameChange: (String) -> Unit,
    onComplete: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nameGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = glowAlpha),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha)),
            shadowElevation = 16.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AccountCircle, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(48.dp))

        Text(
            text = "Who are you?",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            letterSpacing = (-1).sp
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Personalize your Toolz workspace.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black
        )

        Spacer(Modifier.height(56.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text("Display Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Black),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (name.isNotBlank()) onComplete() }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                focusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            leadingIcon = {
                Icon(Icons.Rounded.Fingerprint, null, tint = MaterialTheme.colorScheme.primary)
            }
        )

        Spacer(Modifier.height(24.dp))

        AnimatedVisibility(visible = name.isBlank()) {
            TextButton(onClick = onComplete) {
                Text("BYPASS CONFIGURATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline, letterSpacing = 1.sp)
            }
        }

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .bouncyClick {},
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("NEXT", fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun AiOnboardingStep(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onComplete: () -> Unit
) {
    val vibrationManager = LocalVibrationManager.current
    var showTutorial by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "ai")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer { translationY = floatAnim },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(40.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = "AI POWERED WORKSPACE",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Enable AI tools for advanced OCR, smart summaries, and assistant features.",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            placeholder = { Text("Enter Groq API Key") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { 
                    vibrationManager?.vibrateClick()
                    showTutorial = true 
                }) {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, null, tint = MaterialTheme.colorScheme.primary)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            )
        )

        Spacer(Modifier.height(12.dp))

        TextButton(
            onClick = {
                vibrationManager?.vibrateClick()
                onApiKeyChange("")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Key, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("USE TOOLZ DEFAULT KEY", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .bouncyClick {},
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("LAUNCH DASHBOARD", fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Rounded.RocketLaunch, null, modifier = Modifier.size(24.dp))
        }
    }

    if (showTutorial) {
        TutorialDialog(onDismiss = { showTutorial = false })
    }
}

@Composable
fun TutorialDialog(onDismiss: () -> Unit) {
    val tutorial = AiSettingsHelper.tutorials["Groq"] ?: listOf("No tutorial available")
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("HOW TO GET GROQ KEY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.height(20.dp))
                tutorial.forEachIndexed { index, step ->
                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Surface(
                            modifier = Modifier.size(24.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(step, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("I UNDERSTAND", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
