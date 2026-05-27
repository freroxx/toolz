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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.ai.AiSettingsHelper
import com.frerox.toolz.ui.components.BouncyShape
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveSwitch
import com.frerox.toolz.ui.components.ExtraLargeExpressiveShape
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton
import com.frerox.toolz.ui.components.ToolzWavyLinearProgressIndicator
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Root Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onFinish: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val vibrationManager = LocalVibrationManager.current
    val pageCount = 8
    val pagerState = rememberPagerState(pageCount = { pageCount })
    var showSkipDialog by remember { mutableStateOf(false) }

    val goNext: (Int) -> Unit = { next ->
        vibrationManager?.vibrateClick()
        scope.launch { pagerState.animateScrollToPage(next) }
    }
    val goPrev: () -> Unit = {
        vibrationManager?.vibrateClick()
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header: back / progress / skip ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button – visible on pages 1..7
                AnimatedVisibility(
                    visible = pagerState.currentPage > 0,
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut(spring(Spring.DampingRatioMediumBouncy)) + fadeOut()
                ) {
                    Surface(
                        onClick = goPrev,
                        shape = SmallExpressiveShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(18.dp)
                        )
                    }
                }

                // When on page 0, push progress to left edge
                if (pagerState.currentPage == 0) Spacer(Modifier.width(0.dp))

                // ── Wavy-progress pill ─────────────────────────────────────
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                    shape = BouncyShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Step dots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(pageCount) { index ->
                                val isActive = index == pagerState.currentPage
                                val isPassed = index < pagerState.currentPage
                                val dotWidth by animateDpAsState(
                                    targetValue = if (isActive) 24.dp else 6.dp,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "dot_w_$index"
                                )
                                val dotColor by animateColorAsState(
                                    targetValue = when {
                                        isActive -> MaterialTheme.colorScheme.primary
                                        isPassed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    },
                                    label = "dot_c_$index"
                                )
                                Box(
                                    modifier = Modifier
                                        .height(5.dp)
                                        .width(dotWidth)
                                        .background(dotColor, CircleShape)
                                )
                            }
                        }
                        // Wavy progress bar
                        ToolzWavyLinearProgressIndicator(
                            progress = {
                                pagerState.currentPage.toFloat() / (pageCount - 1).toFloat()
                            },
                            modifier = Modifier.width(132.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }

                // Skip button
                Surface(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        showSkipDialog = true
                    },
                    shape = SmallExpressiveShape,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.09f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                ) {
                    Icon(
                        Icons.Rounded.FastForward,
                        contentDescription = "Skip",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(18.dp)
                    )
                }
            }

            // ── HorizontalPager ─────────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false
            ) { page ->
                Box(modifier = Modifier.fillMaxSize()) {
                    when (page) {
                        0 -> WelcomeStep(onNext = { goNext(1) })
                        1 -> PersonalityStep(
                            name = uiState.name,
                            onNameChange = viewModel::updateName,
                            onNext = { goNext(2) }
                        )
                        2 -> VisualsStep(
                            themeMode = uiState.themeMode,
                            dynamicColor = uiState.dynamicColor,
                            gradient = uiState.backgroundGradient,
                            onThemeChange = viewModel::updateTheme,
                            onDynamicColorChange = viewModel::updateDynamicColor,
                            onGradientChange = viewModel::updateGradient,
                            onNext = { goNext(3) }
                        )
                        3 -> PerformanceStep(
                            specs = uiState.deviceSpecs,
                            performanceMode = uiState.performanceMode,
                            onPerformanceModeChange = viewModel::updatePerformanceMode,
                            onNext = { goNext(4) }
                        )
                        4 -> IntelligenceStep(
                            apiKey = uiState.groqApiKey,
                            onApiKeyChange = viewModel::updateGroqKey,
                            onNext = { goNext(5) }
                        )
                        5 -> ProtocolsStep(onNext = { goNext(6) })
                        6 -> AlertsStep(
                            enabled = uiState.notificationsEnabled,
                            vaultEnabled = uiState.vaultEnabled,
                            onEnabledChange = viewModel::updateNotifications,
                            onVaultChange = viewModel::updateVault,
                            onNext = { goNext(7) }
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

// ─────────────────────────────────────────────────────────────────────────────
// Page 0 · Welcome
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WelcomeStep(onNext: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "welcome_pulse")
    val outerPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f, targetValue = 1.10f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "outer_pulse"
    )
    val innerPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(2000, delayMillis = 400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "inner_pulse"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.06f, targetValue = 0.20f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring_alpha"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo with concentric pulse rings
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
            // Outer ring
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .graphicsLayer { scaleX = outerPulse; scaleY = outerPulse }
                    .drawBehind {
                        drawCircle(
                            color = primaryColor,
                            alpha = ringAlpha * 0.5f,
                            radius = size.minDimension / 2
                        )
                    }
            )
            // Middle ring
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .graphicsLayer { scaleX = innerPulse; scaleY = innerPulse }
                    .drawBehind {
                        drawCircle(
                            color = primaryColor,
                            alpha = ringAlpha,
                            radius = size.minDimension / 2
                        )
                    }
            )
            // Core icon container
            Surface(
                modifier = Modifier.size(130.dp),
                shape = SquircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        StaggeredEntrance(index = 0) {
            Text(
                text = "TOOLZ",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-4).sp,
                    fontSize = 80.sp
                ),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(10.dp))

        StaggeredEntrance(index = 1) {
            Text(
                text = "Orchestrate your mobile life.",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        StaggeredEntrance(index = 2) {
            Text(
                text = "30+ high-precision instruments built for speed,\nprivacy, and absolute digital sovereignty.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                fontWeight = FontWeight.SemiBold,
                lineHeight = 24.sp
            )
        }

        Spacer(Modifier.height(64.dp))

        StaggeredEntrance(index = 3) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                shape = LargeExpressiveShape
            ) {
                Text("INITIALIZE PROTOCOL", fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.width(14.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page 1 · Personality / Name
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PersonalityStep(name: String, onNameChange: (String) -> Unit, onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated avatar bubble – first letter of name
        val avatarScale by animateFloatAsState(
            targetValue = if (name.isNotBlank()) 1.08f else 1f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
            label = "avatar_scale"
        )
        Surface(
            modifier = Modifier
                .size(108.dp)
                .graphicsLayer { scaleX = avatarScale; scaleY = avatarScale },
            shape = BouncyShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = if (name.isNotBlank()) name.first().uppercaseChar().toString() else null,
                    transitionSpec = {
                        (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn())
                            .togetherWith(scaleOut() + fadeOut())
                    },
                    label = "avatar_char"
                ) { char ->
                    if (char != null) {
                        Text(
                            char,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(Icons.Rounded.Face, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Spacer(Modifier.height(36.dp))

        StaggeredEntrance(index = 0) {
            Text(
                "YOUR IDENTITY",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(8.dp))
        StaggeredEntrance(index = 1) {
            Text(
                "How should Toolz address you?",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(Modifier.height(48.dp))

        StaggeredEntrance(index = 2) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = { Text("Agent ID / Name", fontWeight = FontWeight.Medium) },
                modifier = Modifier.fillMaxWidth(),
                shape = SquircleShape,
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { if (name.isNotBlank()) onNext() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            )
        }

        Spacer(Modifier.height(44.dp))

        StaggeredEntrance(index = 3) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                enabled = name.isNotBlank(),
                shape = BouncyShape
            ) {
                Text("CONFIRM IDENTITY", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page 2 · Visuals / Theme
// ─────────────────────────────────────────────────────────────────────────────

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
    val themeOptions = listOf("LIGHT", "SYSTEM", "DARK")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        StaggeredEntrance(index = 0) {
            Column {
                Text(
                    "INTERFACE",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                )
                Text(
                    "Personalize the sensory experience.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(Modifier.height(44.dp))

        StaggeredEntrance(index = 1) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionLabel("THEME ENGINE")
                // ── M3 Expressive Connected Button Group for theme selection ──
                ToolzConnectedButtonGroup(
                    selectedIndex = themeOptions.indexOf(themeMode).coerceAtLeast(0),
                    options = themeOptions,
                    onOptionSelected = { onThemeChange(themeOptions[it]) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        StaggeredEntrance(index = 2) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionLabel("AESTHETICS")
                OnboardingToggleExpressive(
                    title = "DYNAMIC SPECTRUM",
                    desc = "Synchronize with system accent colors.",
                    icon = Icons.Rounded.Palette,
                    checked = dynamicColor,
                    onCheckedChange = onDynamicColorChange
                )
                OnboardingToggleExpressive(
                    title = "NEO-MORPHISM",
                    desc = "Premium organic background gradients.",
                    icon = Icons.Rounded.Gradient,
                    checked = gradient,
                    onCheckedChange = onGradientChange
                )
            }
        }

        Spacer(Modifier.height(56.dp))

        StaggeredEntrance(index = 3) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = SquircleShape
            ) {
                Text("SYNC VISUALS", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page 3 · Performance / Hardware Scan
// ─────────────────────────────────────────────────────────────────────────────

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
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        StaggeredEntrance(index = 0) {
            Column {
                Text(
                    "SYSTEM SCAN",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                )
                Text(
                    "Optimizing protocols for your hardware.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        StaggeredEntrance(index = 1) {
            // Hardware spec card
            ExpressiveCard(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = SquircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = SmallExpressiveShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Memory, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "HARDWARE SPECS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    InfoSpecRow("RAM CAPACITY", "${String.format("%.1f", specs?.totalRamGb ?: 0.0)} GB")
                    Spacer(Modifier.height(8.dp))
                    InfoSpecRow("ANDROID KERNEL", specs?.androidVersion?.toString() ?: "Unknown")
                    Spacer(Modifier.height(16.dp))

                    // RAM bar using wavy indicator
                    val ramFraction = ((specs?.totalRamGb ?: 0.0) / 16.0).toFloat().coerceIn(0f, 1f)
                    val ramColor by animateColorAsState(
                        targetValue = when {
                            ramFraction < 0.25f -> MaterialTheme.colorScheme.error
                            ramFraction < 0.5f -> Color(0xFFF59E0B)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        label = "ram_color"
                    )
                    ToolzWavyLinearProgressIndicator(
                        progress = { ramFraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = ramColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        StaggeredEntrance(index = 2) {
            OnboardingToggleExpressive(
                title = "PERFORMANCE MODE",
                desc = "Disables intensive FX to maximize speed.",
                icon = Icons.Rounded.Speed,
                checked = performanceMode,
                onCheckedChange = onPerformanceModeChange
            )
        }

        // Recommendation notice
        AnimatedVisibility(
            visible = specs?.recommendPerformanceMode == true && !performanceMode,
            enter = expandVertically(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = shrinkVertically(spring(Spring.DampingRatioMediumBouncy)) + fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.09f),
                shape = BouncyShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Info, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "RECOMMENDED FOR OPTIMAL STABILITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(56.dp))

        StaggeredEntrance(index = 3) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = BouncyShape
            ) {
                Text("SET PARAMETERS", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page 4 · Intelligence / Groq API
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligenceStep(apiKey: String, onApiKeyChange: (String) -> Unit, onNext: () -> Unit) {
    var showGuide by remember { mutableStateOf(false) }
    val guideSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        StaggeredEntrance(index = 0) {
            Column {
                Text(
                    "INTELLIGENCE",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                )
                Text(
                    "Powered by Groq for lightspeed reasoning.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(Modifier.height(44.dp))

        StaggeredEntrance(index = 1) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                placeholder = { Text("Groq API Key (Optional)", fontWeight = FontWeight.Medium) },
                modifier = Modifier.fillMaxWidth(),
                shape = SquircleShape,
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showGuide = true }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.HelpOutline, null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            )
        }

        Spacer(Modifier.height(20.dp))

        StaggeredEntrance(index = 2) {
            // "Why use Groq?" info card
            ExpressiveCard(
                onClick = { showGuide = true },
                modifier = Modifier.fillMaxWidth(),
                shape = BouncyShape,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)),
                elevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = SmallExpressiveShape,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.AutoAwesome, null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "WHY USE GROQ?",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            "100% Free, privacy-hardened AI response layers.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                        modifier = Modifier
                            .size(18.dp)
                            .alpha(0.35f)
                    )
                }
            }
        }

        Spacer(Modifier.height(56.dp))

        StaggeredEntrance(index = 3) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = LargeExpressiveShape
            ) {
                Text(
                    if (apiKey.isBlank()) "BYPASS FOR NOW" else "ESTABLISH LINK",
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }

    // ── Groq Guide – Modal Bottom Sheet (replaces Dialog) ───────────────────
    if (showGuide) {
        ModalBottomSheet(
            onDismissRequest = { showGuide = false },
            sheetState = guideSheetState,
            shape = ExtraLargeExpressiveShape,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    width = 56.dp,
                    height = 4.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = SmallExpressiveShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "GROQ SETUP GUIDE",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(Modifier.height(28.dp))

                val steps = AiSettingsHelper.tutorials["Groq"] ?: emptyList()
                steps.forEachIndexed { index, step ->
                    StaggeredEntrance(index = index) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                modifier = Modifier.size(26.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "${index + 1}",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(
                                step,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(36.dp))

                ToolzExpressiveButton(
                    onClick = {
                        scope.launch { guideSheetState.hide() }.invokeOnCompletion {
                            if (!guideSheetState.isVisible) showGuide = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = BouncyShape
                ) {
                    Text("GOT IT", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page 5 · Protocols / Permissions
// ─────────────────────────────────────────────────────────────────────────────

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

    data class ProtocolItem(
        val title: String,
        val desc: String,
        val icon: ImageVector,
        val granted: Boolean,
        val onClick: () -> Unit
    )

    val protocols = listOf(
        ProtocolItem(
            "HARDWARE SUITE",
            "Precision sensors and visual capture.",
            Icons.Rounded.DeveloperBoard,
            permissionsState.allPermissionsGranted
        ) { permissionsState.launchMultiplePermissionRequest() },
        ProtocolItem(
            "ANALYTICS ENGINE",
            "Quantified usage for focus protocols.",
            Icons.Rounded.Analytics,
            usageStatsGranted
        ) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        ProtocolItem(
            "SYSTEM VAULT",
            "Real-time alert indexing and storage.",
            Icons.Rounded.Security,
            notificationListenerGranted
        ) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        ProtocolItem(
            "CONTROL BRIDGE",
            "Advanced accessibility integration.",
            Icons.Rounded.Hub,
            accessibilityGranted
        ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        StaggeredEntrance(index = 0) {
            Column {
                Text(
                    "PROTOCOLS",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                )
                Text(
                    "Authorize access to localized hardware.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        // StaggeredEntrance per card in a LazyColumn
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(protocols, key = { _, item -> item.title }) { index, item ->
                StaggeredEntrance(index = index + 1) {
                    ProtocolCardExpressive(
                        title = item.title,
                        desc = item.desc,
                        icon = item.icon,
                        granted = item.granted,
                        onClick = item.onClick
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        ToolzExpressiveButton(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = SquircleShape
        ) {
            Text("ADVANCE PROTOCOL", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page 6 · Alerts / Notifications
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AlertsStep(
    enabled: Boolean,
    vaultEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onVaultChange: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        StaggeredEntrance(index = 0) {
            Column {
                Text(
                    "ALERTS",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                )
                Text(
                    "Manage incoming transmission streams.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(Modifier.height(44.dp))

        StaggeredEntrance(index = 1) {
            OnboardingToggleExpressive(
                title = "PUSH NOTIFICATIONS",
                desc = "Real-time activity and task updates.",
                icon = Icons.Rounded.NotificationsActive,
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }

        Spacer(Modifier.height(16.dp))

        StaggeredEntrance(index = 2) {
            OnboardingToggleExpressive(
                title = "NOTIFICATION VAULT",
                desc = "Secure local indexing for all alerts.",
                icon = Icons.Rounded.VpnKey,
                checked = vaultEnabled,
                onCheckedChange = onVaultChange
            )
        }

        Spacer(Modifier.height(64.dp))

        StaggeredEntrance(index = 3) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = BouncyShape
            ) {
                Text("INITIALIZE STREAMS", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page 7 · Ready
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReadyStep(name: String, onComplete: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "ready_rings")

    // Three phase-offset rings
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0.75f, targetValue = 1.30f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring1"
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0.82f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            tween(2200, delayMillis = 350, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "ring2"
    )
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 0.90f, targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            tween(1800, delayMillis = 700, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "ring3"
    )
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.08f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Multi-ring celebration orb
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            // Ring 3 (outermost)
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .graphicsLayer { scaleX = ring1; scaleY = ring1 }
                    .drawBehind { drawCircle(primaryColor, alpha = glow * 0.4f, radius = size.minDimension / 2) }
            )
            // Ring 2
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .graphicsLayer { scaleX = ring2; scaleY = ring2 }
                    .drawBehind { drawCircle(primaryColor, alpha = glow * 0.65f, radius = size.minDimension / 2) }
            )
            // Ring 1 (closest)
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .graphicsLayer { scaleX = ring3; scaleY = ring3 }
                    .drawBehind { drawCircle(primaryColor, alpha = glow, radius = size.minDimension / 2) }
            )
            // Core
            Surface(
                modifier = Modifier.size(120.dp),
                shape = SquircleShape,
                color = MaterialTheme.colorScheme.primary,
                border = BorderStroke(3.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Check, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(Modifier.height(44.dp))

        StaggeredEntrance(index = 0) {
            Text(
                "DEPLOYMENT READY",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(14.dp))

        StaggeredEntrance(index = 1) {
            Text(
                "Agent ${name.ifBlank { "Explorer" }}, all systems are nominal\nand ready for launch.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                fontWeight = FontWeight.SemiBold,
                lineHeight = 24.sp,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(Modifier.height(64.dp))

        StaggeredEntrance(index = 2) {
            ToolzExpressiveButton(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = LargeExpressiveShape
            ) {
                Text(
                    "ENTER DASHBOARD",
                    fontWeight = FontWeight.Black,
                    fontSize = 19.sp,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared Components
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Expressive toggle row card — used on Visuals, Performance, and Alerts steps.
 */
@Composable
fun OnboardingToggleExpressive(
    title: String,
    desc: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (checked)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f)
        else
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "toggle_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        else
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
        label = "toggle_border"
    )

    ExpressiveCard(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape,
        containerColor = containerColor,
        border = BorderStroke(1.5.dp, borderColor),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = SmallExpressiveShape,
                color = (if (checked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.10f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon, null,
                        tint = if (checked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 0.3.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(14.dp))
            ExpressiveSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/**
 * Protocol permission card with granted / pending state transitions.
 */
@Composable
fun ProtocolCardExpressive(
    title: String,
    desc: String,
    icon: ImageVector,
    granted: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (granted)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
        else
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "proto_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (granted)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        else
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
        label = "proto_border"
    )

    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = BouncyShape,
        containerColor = containerColor,
        border = BorderStroke(1.dp, borderColor),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated icon swap: granted → CheckCircle
            AnimatedContent(
                targetState = granted,
                transitionSpec = {
                    (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn())
                        .togetherWith(scaleOut() + fadeOut())
                },
                label = "proto_icon"
            ) { isGranted ->
                Icon(
                    if (isGranted) Icons.Rounded.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 0.3.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f)
                )
            }
            AnimatedVisibility(
                visible = !granted,
                enter = expandHorizontally(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = shrinkHorizontally(spring(Spring.DampingRatioMediumBouncy)) + fadeOut()
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    modifier = Modifier
                        .size(20.dp)
                        .alpha(0.30f)
                )
            }
        }
    }
}

/**
 * Skip confirmation – uses BasicAlertDialog for full M3 Expressive theming.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipConfirmationDialogExpressive(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SquircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Warning orb
                val infiniteTransition = rememberInfiniteTransition(label = "warn_pulse")
                val warnPulse by infiniteTransition.animateFloat(
                    initialValue = 0.92f, targetValue = 1.10f,
                    animationSpec = infiniteRepeatable(
                        tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse
                    ),
                    label = "wp"
                )
                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer { scaleX = warnPulse; scaleY = warnPulse },
                    shape = BouncyShape,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.20f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Warning, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    "SKIP SETUP?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    "Onboarding helps Toolz calibrate itself for your specific hardware. Bypassing this may lead to sub-optimal sensory precision.",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    lineHeight = 22.sp,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(36.dp))

                ToolzExpressiveButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = BouncyShape
                ) {
                    Text("REJOIN SETUP", fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                }

                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text(
                        "SKIP ANYWAY",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal UI helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Section label — uppercase tracking label above a group of controls. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 2.sp
    )
}

/** Key–value row used in the hardware spec card. */
@Composable
private fun InfoSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
            fontWeight = FontWeight.SemiBold
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Platform helpers (unchanged)
// ─────────────────────────────────────────────────────────────────────────────

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
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return manager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}