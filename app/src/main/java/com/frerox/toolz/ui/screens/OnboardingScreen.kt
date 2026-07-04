package com.frerox.toolz.ui.screens

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.ai.AiSettingsHelper
import com.frerox.toolz.data.update.UpdateCheckResult
import com.frerox.toolz.ui.components.BouncyShape
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveContainedLoadingIndicator
import com.frerox.toolz.ui.components.ExpressiveSwitch
import com.frerox.toolz.ui.components.ExtraLargeExpressiveShape
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton
import com.frerox.toolz.ui.components.ToolzWavyLinearProgressIndicator
import com.frerox.toolz.ui.screens.time.components.PomodoroSuccessConfetti
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onFinish: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val vibrationManager = LocalVibrationManager.current
    val pageCount = 7
    val pagerState = rememberPagerState(pageCount = { pageCount })
    var showSkipDialog by remember { mutableStateOf(false) }
    var isFinishing by remember { mutableStateOf(false) }

    val goNext: () -> Unit = {
        vibrationManager?.vibrateClick()
        scope.launch { 
            if (pagerState.currentPage < pageCount - 1) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1) 
            }
        }
    }
    val goPrev: () -> Unit = {
        vibrationManager?.vibrateClick()
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    val isDark = when (uiState.themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }

    ToolzTheme(
        darkTheme = isDark,
        dynamicColor = uiState.dynamicColor,
        backgroundGradientEnabled = uiState.backgroundGradient,
        performanceMode = uiState.performanceMode
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(
                        visible = pagerState.currentPage > 0,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        IconButton(onClick = goPrev) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                    if (pagerState.currentPage == 0) Spacer(modifier = Modifier.width(48.dp))

                    // Progress
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(pageCount) { index ->
                            val isActive = index == pagerState.currentPage
                            val dotWidth by animateDpAsState(if (isActive) 24.dp else 8.dp, label = "dot")
                            val color = if (isActive) MaterialTheme.colorScheme.primary 
                                       else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(dotWidth)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    }

                    IconButton(onClick = { showSkipDialog = true }) {
                        Icon(Icons.Rounded.FastForward, contentDescription = "Skip", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    userScrollEnabled = false
                ) { page ->
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            (fadeIn(tween(400)) + slideInHorizontally { it / 2 })
                                .togetherWith(fadeOut(tween(400)) + slideOutHorizontally { -it / 2 })
                        },
                        label = "page"
                    ) { targetPage ->
                        when (targetPage) {
                            0 -> WelcomeStep(onNext = goNext)
                            1 -> UpdateStep(
                                state = uiState.updateState,
                                onCheck = viewModel::checkForUpdates,
                                onNext = goNext
                            )
                            2 -> PersonaStep(
                                name = uiState.name,
                                themeMode = uiState.themeMode,
                                dynamicColor = uiState.dynamicColor,
                                gradient = uiState.backgroundGradient,
                                onNameChange = viewModel::updateName,
                                onThemeChange = viewModel::updateTheme,
                                onDynamicColorChange = viewModel::updateDynamicColor,
                                onGradientChange = viewModel::updateGradient,
                                onNext = goNext
                            )
                            3 -> SystemStep(
                                specs = uiState.deviceSpecs,
                                performanceMode = uiState.performanceMode,
                                onPerformanceModeChange = viewModel::updatePerformanceMode,
                                onNext = goNext
                            )
                            4 -> IntelligenceStep(
                                apiKey = uiState.groqApiKey,
                                onApiKeyChange = viewModel::updateGroqKey,
                                onNext = goNext
                            )
                            5 -> AccessStep(
                                notificationsEnabled = uiState.notificationsEnabled,
                                vaultEnabled = uiState.vaultEnabled,
                                shizukuAuthorized = uiState.shizukuAuthorized,
                                onNotificationsChange = viewModel::updateNotifications,
                                onVaultChange = viewModel::updateVault,
                                refreshShizuku = viewModel::refreshShizukuStatus,
                                onNext = goNext
                            )
                            6 -> ReadyStep(
                                name = uiState.name,
                                onComplete = {
                                    vibrationManager?.vibrateSuccess()
                                    isFinishing = true
                                    viewModel.finishOnboarding(onFinish)
                                }
                            )
                        }
                    }
                }
            }

            // Confetti layer on top of background
            if (pagerState.currentPage == 6 && !isFinishing) {
                Box(modifier = Modifier.fillMaxSize().zIndex(5f)) {
                    PomodoroSuccessConfetti(onFinished = {})
                }
            }

            // Smooth finish transition overlay
            AnimatedVisibility(
                visible = isFinishing,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(400)),
                modifier = Modifier.fillMaxSize().zIndex(100f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background) // Ensure solid base
                        .toolzBackground(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ExpressiveContainedLoadingIndicator(
                            modifier = Modifier.size(140.dp, 80.dp),
                            color = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        Spacer(Modifier.height(32.dp))
                        Text(
                            "DEPLOYING WORKSPACE", // Fixed typo in "DEPLOYING"
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }

    if (showSkipDialog) {
        SkipConfirmationDialogExpressive(
            onConfirm = {
                showSkipDialog = false
                viewModel.skipOnboarding(onFinish)
            },
            onDismiss = { showSkipDialog = false }
        )
    }
}

// ── Step 0: Welcome ──────────────────────────────────────────────────────────

@Composable
fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StaggeredEntrance(index = 0) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
                // Smooth static glow
                Surface(
                    modifier = Modifier.size(200.dp).graphicsLayer { alpha = 0.35f },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {}
                
                Surface(
                    shape = SquircleShape,
                    modifier = Modifier.size(130.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp
                ) {
                    Image(
                        painter = painterResource(R.drawable.app_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(SquircleShape)
                    )
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        StaggeredEntrance(index = 1) {
            Text(
                "TOOLZ",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp
            )
        }

        StaggeredEntrance(index = 2) {
            Text(
                "Absolute digital sovereignty.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(16.dp))

        StaggeredEntrance(index = 3) {
            Text(
                "Made by frerox",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }

        Spacer(Modifier.height(32.dp))

        StaggeredEntrance(index = 4) {
            Text(
                "A minimalist suite of high-precision instruments designed for speed and privacy.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(64.dp))

        StaggeredEntrance(index = 5) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                shape = ExtraLargeExpressiveShape
            ) {
                Text("GET STARTED", fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontSize = 18.sp)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
            }
        }
    }
}

// ── Step 1: Update ───────────────────────────────────────────────────────────

@Composable
fun UpdateStep(
    state: UpdateCheckResult?,
    onCheck: () -> Unit,
    onNext: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (state == null) onCheck()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StaggeredEntrance(index = 0) {
            Text(
                "SYSTEM UPDATE",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
        }

        Spacer(Modifier.height(48.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
                label = "update_anim"
            ) { updateState ->
                when (updateState) {
                    null -> {
                        ExpressiveContainedLoadingIndicator(
                            modifier = Modifier.size(220.dp),
                            color = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                        )
                    }
                    is UpdateCheckResult.NewUpdate -> {
                        Surface(
                            shape = SquircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier.size(160.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
                            }
                        }
                    }
                    else -> {
                        Surface(
                            shape = SquircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier.size(160.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        StaggeredEntrance(index = 1) {
            AnimatedContent(targetState = state, label = "text") { updateState ->
                val title = when (updateState) {
                    null -> "Synchronizing..."
                    is UpdateCheckResult.NewUpdate -> "Protocol Update Found"
                    is UpdateCheckResult.UpToDate -> "Systems Optimal"
                    else -> "Connection Offline"
                }
                val desc = when (updateState) {
                    null -> "Checking for available system updates."
                    is UpdateCheckResult.NewUpdate -> "Version ${updateState.version} is ready for deployment."
                    is UpdateCheckResult.UpToDate -> "You are running the latest version of Toolz."
                    else -> "Could not reach update server. Skipping for now."
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(64.dp))

        StaggeredEntrance(index = 2) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = ExtraLargeExpressiveShape,
                enabled = state != null
            ) {
                Text(if (state is UpdateCheckResult.NewUpdate) "UPDATE & CONTINUE" else "CONTINUE", fontWeight = FontWeight.Black)
            }
        }
    }
}

// ── Step 2: Persona ──────────────────────────────────────────────────────────

@Composable
fun PersonaStep(
    name: String,
    themeMode: String,
    dynamicColor: Boolean,
    gradient: Boolean,
    onNameChange: (String) -> Unit,
    onThemeChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onGradientChange: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    val themeOptions = listOf("LIGHT", "SYSTEM", "DARK")
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Center
    ) {
        StaggeredEntrance(index = 0) {
            Text(
                "PERSONA",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
        }

        Spacer(Modifier.height(32.dp))

        StaggeredEntrance(index = 1) {
            ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("IDENTITY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        placeholder = { Text("Your Name / Agent ID") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = SquircleShape,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Spacer(Modifier.height(32.dp))

                    Text("INTERFACE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(16.dp))
                    ToolzConnectedButtonGroup(
                        selectedIndex = themeOptions.indexOf(themeMode).coerceAtLeast(0),
                        options = themeOptions,
                        onOptionSelected = { onThemeChange(themeOptions[it]) },
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    OnboardingToggleExpressive(
                        title = "Dynamic Spectrum",
                        desc = "Sync with system colors",
                        icon = Icons.Rounded.Palette,
                        checked = dynamicColor,
                        onCheckedChange = onDynamicColorChange
                    )
                    Spacer(Modifier.height(12.dp))
                    OnboardingToggleExpressive(
                        title = "Organic Gradients",
                        desc = "Premium visual depth",
                        icon = Icons.Rounded.Gradient,
                        checked = gradient,
                        onCheckedChange = onGradientChange
                    )
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        StaggeredEntrance(index = 2) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                enabled = name.isNotBlank(),
                shape = ExtraLargeExpressiveShape
            ) {
                Text("CONTINUE", fontWeight = FontWeight.Black)
            }
        }
    }
}

// ── Step 3: System ───────────────────────────────────────────────────────────

@Composable
fun SystemStep(
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
        StaggeredEntrance(index = 0) {
            Text(
                "SYSTEM",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
        }

        Spacer(Modifier.height(32.dp))

        StaggeredEntrance(index = 1) {
            ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("HARDWARE SCAN", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    InfoSpecRow("RAM CAPACITY", "${String.format("%.1f", specs?.totalRamGb ?: 0.0)} GB")
                    Spacer(Modifier.height(8.dp))
                    InfoSpecRow("ANDROID VERSION", specs?.androidVersion?.toString() ?: "Unknown")
                    
                    Spacer(Modifier.height(24.dp))
                    
                    val ramFraction = ((specs?.totalRamGb ?: 0.0) / 16.0).toFloat().coerceIn(0f, 1f)
                    ToolzWavyLinearProgressIndicator(
                        progress = { ramFraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )

                    Spacer(Modifier.height(32.dp))

                    OnboardingToggleExpressive(
                        title = "Performance Mode",
                        desc = "Maximize speed by limiting FX",
                        icon = Icons.Rounded.Speed,
                        checked = performanceMode,
                        onCheckedChange = onPerformanceModeChange
                    )
                    
                    if (specs?.recommendPerformanceMode == true && !performanceMode) {
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = MediumExpressiveShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Recommended for your device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        StaggeredEntrance(index = 2) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = ExtraLargeExpressiveShape
            ) {
                Text("OPTIMIZE", fontWeight = FontWeight.Black)
            }
        }
    }
}

// ── Step 4: Intelligence ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligenceStep(apiKey: String, onApiKeyChange: (String) -> Unit, onNext: () -> Unit) {
    var showGuide by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        StaggeredEntrance(index = 0) {
            Text(
                "INTELLIGENCE",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
        }

        Spacer(Modifier.height(32.dp))

        StaggeredEntrance(index = 1) {
            ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("AI ENGINE (GROQ)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChange,
                        placeholder = { Text("Groq API Key (Important)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = SquircleShape,
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showGuide = true }) {
                                Icon(Icons.AutoMirrored.Rounded.HelpOutline, null)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Spacer(Modifier.height(24.dp))

                    ToolzOutlinedExpressiveButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = MediumExpressiveShape
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("GET API KEY", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(24.dp))

                    Surface(
                        onClick = { showGuide = true },
                        shape = MediumExpressiveShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Why Groq?", fontWeight = FontWeight.Bold)
                                Text("Ultra-fast, private, and free AI layers.", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        StaggeredEntrance(index = 2) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = ExtraLargeExpressiveShape
            ) {
                Text(if (apiKey.isBlank()) "BYPASS" else "CONNECT", fontWeight = FontWeight.Black)
            }
        }
    }

    if (showGuide) {
        ModalBottomSheet(
            onDismissRequest = { showGuide = false },
            sheetState = sheetState,
            shape = ExtraLargeExpressiveShape
        ) {
            Column(Modifier.padding(28.dp).navigationBarsPadding()) {
                Text("GROQ SETUP", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(24.dp))
                val steps = AiSettingsHelper.tutorials["Groq"] ?: emptyList()
                steps.forEachIndexed { i, step ->
                    Row(Modifier.padding(vertical = 8.dp)) {
                        Text("${i + 1}.", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(step)
                    }
                }
                Spacer(Modifier.height(32.dp))
                ToolzExpressiveButton(
                    onClick = { 
                        scope.launch { sheetState.hide() }.invokeOnCompletion { showGuide = false }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = BouncyShape
                ) {
                    Text("DONE")
                }
            }
        }
    }
}

// ── Step 5: Access ───────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AccessStep(
    notificationsEnabled: Boolean,
    vaultEnabled: Boolean,
    shizukuAuthorized: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    onVaultChange: (Boolean) -> Unit,
    refreshShizuku: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var usageStatsGranted by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var notificationListenerGranted by remember { mutableStateOf(hasNotificationListenerPermission(context)) }
    var accessibilityGranted by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var showShizukuSetup by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageStatsGranted = hasUsageStatsPermission(context)
                notificationListenerGranted = hasNotificationListenerPermission(context)
                accessibilityGranted = isAccessibilityServiceEnabled(context)
                refreshShizuku()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACTIVITY_RECOGNITION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            @Suppress("DEPRECATION")
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            @Suppress("DEPRECATION")
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    val permissionsState = rememberMultiplePermissionsState(permissions)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        StaggeredEntrance(index = 0) {
            Text(
                "ACCESS",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
        }

        Spacer(Modifier.height(32.dp))

        StaggeredEntrance(index = 1) {
            ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                LazyColumn(modifier = Modifier.padding(24.dp).height(400.dp)) {
                    item {
                        Text("PROTOCOLS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(16.dp))
                    }

                    item {
                        ProtocolRow("Hardware Suite", "Camera, Mic & Location", Icons.Rounded.DeveloperBoard, permissionsState.allPermissionsGranted) {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                    }
                    item {
                        ProtocolRow("Storage Vault", "File processing & indexing", Icons.Rounded.Storage, false) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                               context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            } else {
                               permissionsState.launchMultiplePermissionRequest()
                            }
                        }
                    }
                    item {
                        ProtocolRow("Analytics", "Usage tracking protocols", Icons.Rounded.Analytics, usageStatsGranted) {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    }
                    item {
                        ProtocolRow("System Vault", "Notification local indexing", Icons.Rounded.Security, notificationListenerGranted) {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    }
                    item {
                        ProtocolRow("Control Bridge", "Advanced control integration", Icons.Rounded.Hub, accessibilityGranted) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }
                    item {
                        ProtocolRow("Shizuku Protocol", "Root-less system access", Icons.Rounded.Memory, shizukuAuthorized) {
                            if (com.frerox.toolz.util.shizuku.ShizukuHelper.isAuthorized()) {
                                // Already authorized
                            } else if (com.frerox.toolz.util.shizuku.ShizukuHelper.isAvailable()) {
                                com.frerox.toolz.util.shizuku.ShizukuHelper.requestPermission(1001)
                            } else {
                                showShizukuSetup = true
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(32.dp))
                        Text("STREAMS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(16.dp))
                    }
                    
                    item {
                        OnboardingToggleExpressive("Push Notifications", "Real-time updates", Icons.Rounded.NotificationsActive, notificationsEnabled, onNotificationsChange)
                        Spacer(Modifier.height(12.dp))
                    }
                    item {
                        OnboardingToggleExpressive("Notification Vault", "Secure local history", Icons.Rounded.VpnKey, vaultEnabled, onVaultChange)
                    }
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        StaggeredEntrance(index = 2) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = ExtraLargeExpressiveShape
            ) {
                Text("AUTHORIZE & CONTINUE", fontWeight = FontWeight.Black)
            }
        }
    }

    if (showShizukuSetup) {
        com.frerox.toolz.ui.components.ShizukuSetupBottomSheet(
            onDismiss = { showShizukuSetup = false }
        )
    }
}

// ── Step 6: Ready ────────────────────────────────────────────────────────────

@Composable
fun ReadyStep(name: String, onComplete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .zIndex(10f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StaggeredEntrance(index = 0) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
                Surface(
                    modifier = Modifier.size(200.dp).graphicsLayer { alpha = 0.35f },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {}
                Surface(
                    shape = SquircleShape,
                    modifier = Modifier.size(130.dp),
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(72.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        StaggeredEntrance(index = 1) {
            Text(
                "READY",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp
            )
        }

        StaggeredEntrance(index = 2) {
            Text(
                "Welcome ${name.ifBlank { "Explorer" }}, Toolz is now fully operational!",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(64.dp))

        StaggeredEntrance(index = 3) {
            ToolzExpressiveButton(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                shape = ExtraLargeExpressiveShape
            ) {
                Text("LAUNCH DASHBOARD", fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.sp)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
            }
        }
    }
}

// ── Shared ───────────────────────────────────────────────────────────────────

@Composable
fun OnboardingToggleExpressive(
    title: String,
    desc: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = MediumExpressiveShape,
        color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (checked) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            ExpressiveSwitch(checked, onCheckedChange)
        }
    }
}

@Composable
private fun ProtocolRow(title: String, desc: String, icon: ImageVector, granted: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = SquircleShape,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (granted) Icons.Rounded.CheckCircle else icon, null, tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
            if (!granted) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun InfoSpecRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Text(value, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipConfirmationDialogExpressive(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = ExtraLargeExpressiveShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(24.dp))
                Text("SKIP SETUP?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp))
                Text("Skipping setup might result in sub-optimal system calibration.", textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                ToolzExpressiveButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("BACK") }
                TextButton(onClick = onConfirm, modifier = Modifier.padding(top = 8.dp)) {
                    Text("SKIP ANYWAY", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WelcomeStepPreview() {
    ToolzTheme(performanceMode = true) {
        WelcomeStep(onNext = {})
    }
}

@Preview(showBackground = true)
@Composable
fun UpdateStepPreview() {
    ToolzTheme(performanceMode = true) {
        UpdateStep(state = null, onCheck = {}, onNext = {})
    }
}

@Preview(showBackground = true)
@Composable
fun PersonaStepPreview() {
    ToolzTheme(performanceMode = true) {
        PersonaStep(
            name = "Explorer",
            themeMode = "SYSTEM",
            dynamicColor = true,
            gradient = true,
            onNameChange = {},
            onThemeChange = {},
            onDynamicColorChange = {},
            onGradientChange = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SystemStepPreview() {
    ToolzTheme(performanceMode = true) {
        SystemStep(
            specs = null,
            performanceMode = false,
            onPerformanceModeChange = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun IntelligenceStepPreview() {
    ToolzTheme(performanceMode = true) {
        IntelligenceStep(
            apiKey = "",
            onApiKeyChange = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AccessStepPreview() {
    ToolzTheme(performanceMode = true) {
        AccessStep(
            notificationsEnabled = true,
            vaultEnabled = true,
            shizukuAuthorized = false,
            onNotificationsChange = {},
            onVaultChange = {},
            refreshShizuku = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ReadyStepPreview() {
    ToolzTheme(performanceMode = true) {
        ReadyStep(name = "Explorer", onComplete = {})
    }
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val mode = appOps?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }
}

private fun hasNotificationListenerPermission(context: Context): Boolean {
    return try {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        flat != null && flat.contains(context.packageName)
    } catch (e: Exception) {
        false
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    return try {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        manager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)?.any { 
            it.resolveInfo.serviceInfo.packageName == context.packageName 
        } == true
    } catch (e: Exception) {
        false
    }
}
