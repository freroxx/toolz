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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.ui.components.bouncyClick
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    settingsRepository: SettingsRepository,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val color1 by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        targetValue = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.05f),
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
        label = "c1"
    )
    val color2 by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f),
        targetValue = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
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
                1 -> WelcomeStep(onNext = { step = 2 })
                2 -> PermissionsStep(onNext = { step = 3 })
                3 -> NameStep(
                    name = name,
                    onNameChange = { name = it },
                    onComplete = {
                        scope.launch {
                            settingsRepository.setUserName(name)
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
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "icon")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "scale"
        )

        Surface(
            modifier = Modifier.size(120.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.BuildCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(Modifier.height(48.dp))
        
        Text(
            text = "Toolz Precision",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            letterSpacing = (-1.5).sp
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = "The ultimate hardware-focused private toolkit for Android. Everything you need, unified in one engine.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 16.dp),
            lineHeight = 24.sp
        )

        Spacer(Modifier.height(48.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WelcomeFeature(Icons.Rounded.Lock, "SAFE & PRIVATE", Modifier.weight(1f))
            WelcomeFeature(Icons.Rounded.WifiOff, "OFFLINE FIRST", Modifier.weight(1f))
        }
        
        Spacer(Modifier.height(56.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(64.dp).bouncyClick {},
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("INITIALIZE SETUP", fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun WelcomeFeature(icon: ImageVector, text: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsStep(onNext: () -> Unit) {
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
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    val permissionsState = rememberMultiplePermissionsState(systemPermissions)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .statusBarsPadding()
    ) {
        Text(
            text = "Hardware Keys",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Engage the required sensors and storage modules to power the Toolz engine. Data never leaves your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        
        Spacer(Modifier.height(32.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                PermissionCard(
                    title = "System Core & Media",
                    desc = "Essential for Flashlight, Camera, GPS, and Storage.",
                    icon = Icons.Rounded.DeveloperBoard,
                    granted = permissionsState.allPermissionsGranted,
                    onClick = { permissionsState.launchMultiplePermissionRequest() }
                )
            }
            
            item {
                PermissionCard(
                    title = "Focus Analytics",
                    desc = "Empowers Focus Flow with screen-time insights.",
                    icon = Icons.Rounded.Timeline,
                    granted = usageStatsGranted,
                    onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                )
            }

            item {
                PermissionCard(
                    title = "Anti-Recall Archive",
                    desc = "Allows Notification Vault to secure system alerts.",
                    icon = Icons.Rounded.Security,
                    granted = notificationListenerGranted,
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                )
            }

            item {
                PermissionCard(
                    title = "Accessibility Bridge",
                    desc = "Enables Hard-Lock security for focus sessions.",
                    icon = Icons.Rounded.Hub,
                    granted = accessibilityGranted,
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
            }
        }
        
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(64.dp).bouncyClick {},
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (permissionsState.allPermissionsGranted) 
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (permissionsState.allPermissionsGranted) 
                        MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("PROCEED TO FINALIZE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            
            TextButton(
                onClick = onNext, 
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("SKIP TO DASHBOARD", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
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
        modifier = Modifier.fillMaxWidth().bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (granted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            1.5.dp, 
            if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) 
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (granted) Icons.Rounded.CheckCircle else icon,
                        null,
                        tint = if (granted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), lineHeight = 16.sp, fontWeight = FontWeight.Medium)
            }
            
            if (!granted) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AccountCircle, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        Spacer(Modifier.height(40.dp))
        
        Text(
            text = "Welcome Aboard",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            text = "How should Toolz address you?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        )
        
        Spacer(Modifier.height(48.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text("Enter your name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
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
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        
        Spacer(Modifier.height(56.dp))
        
        Button(
            onClick = onComplete,
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(64.dp).bouncyClick {},
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("FINALIZE CONFIGURATION", fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp)
        }
    }
}
