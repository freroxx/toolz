/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.BasicAlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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

/**
 * Onboarding, redesigned around Material 3 Expressive:
 *  - a warm, calm voice instead of jargon ("Notifications", not "PROTOCOLS")
 *  - spring-driven motion throughout, not just fades
 *  - a live "setup score" so people can see what skipping costs them
 *  - clearer value framing on every permission ask
 */

private const val ExpressiveDamping = Spring.DampingRatioMediumBouncy
private const val ExpressiveStiffnessLow = Spring.StiffnessLow
private const val ExpressiveStiffnessMedium = Spring.StiffnessMedium

private val ExpressiveSpring = spring<Float>(dampingRatio = ExpressiveDamping, stiffness = ExpressiveStiffnessLow)
private val ExpressiveSpringDp = spring<androidx.compose.ui.unit.Dp>(dampingRatio = ExpressiveDamping, stiffness = ExpressiveStiffnessMedium)
private val ExpressiveSpringIntOffset = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = ExpressiveDamping, stiffness = ExpressiveStiffnessLow)
private val ExpressiveSpringIntSize = spring<androidx.compose.ui.unit.IntSize>(dampingRatio = ExpressiveDamping, stiffness = ExpressiveStiffnessMedium)

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

    // Live personalization score — a small, honest signal of how "set up" the
    // experience will feel, without gating anything. Purely informative.
    val setupScore = remember(uiState) {
        var total = 0
        var done = 0
        total++; if (uiState.name.isNotBlank()) done++
        total++; if (uiState.groqApiKey.isNotBlank()) done++
        total++; if (uiState.notificationsEnabled) done++
        total++; if (uiState.shizukuAuthorized) done++
        done.toFloat() / total
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
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(
                        visible = pagerState.currentPage > 0,
                        enter = scaleIn(ExpressiveSpring) + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        IconButton(onClick = goPrev) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_OnboardingScreen_4f2d))
                        }
                    }
                    if (pagerState.currentPage == 0) Spacer(modifier = Modifier.width(48.dp))

                    // Step progress — expressive morphing dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(pageCount) { index ->
                            val isActive = index == pagerState.currentPage
                            val isPast = index < pagerState.currentPage
                            val dotWidth by animateDpAsState(
                                targetValue = if (isActive) 28.dp else 8.dp,
                                animationSpec = ExpressiveSpringDp,
                                label = "dot_width"
                            )
                            val color = when {
                                isActive -> MaterialTheme.colorScheme.primary
                                isPast -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            }
                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(dotWidth)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    }

                    // Setup score ring replaces the old blunt "skip" fast-forward icon.
                    // Tapping it still offers to skip, but now it visibly communicates
                    // "here's what you'd be leaving on the table."
                    SetupScoreBadge(
                        progress = setupScore,
                        onClick = { showSkipDialog = true }
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    userScrollEnabled = false
                ) { page ->
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            (fadeIn(tween(420)) + slideInHorizontally(ExpressiveSpringIntOffset) { it / 3 })
                                .togetherWith(fadeOut(tween(220)) + slideOutHorizontally(tween(220)) { -it / 4 })
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
                        .background(MaterialTheme.colorScheme.background)
                        .toolzBackground(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ExpressiveContainedLoadingIndicator(
                            modifier = Modifier.size(140.dp, 80.dp),
                            color = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        Spacer(Modifier.height(28.dp))
                        Text(
                            stringResource(R.string.st_OnboardingScreen_a1b2),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.st_OnboardingScreen_c3d4),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    if (showSkipDialog) {
        SkipConfirmationDialogExpressive(
            setupScore = setupScore,
            onConfirm = {
                showSkipDialog = false
                viewModel.skipOnboarding(onFinish)
            },
            onDismiss = { showSkipDialog = false }
        )
    }
}

// ── Setup score badge (new) ────────────────────────────────────────────────
// A quiet ring that fills in as the person completes optional steps (name,
// AI key, notifications, deep access). Replaces the old plain "skip" icon
// with something that actually communicates value.

@Composable
private fun SetupScoreBadge(progress: Float, onClick: () -> Unit) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ExpressiveSpring,
        label = "setup_score"
    )
    val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val progressColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(modifier = Modifier.size(36.dp)) {
                    val stroke = 3.dp.toPx()
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    "${(animatedProgress * 100).toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
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
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                Surface(
                    modifier = Modifier.size(188.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                ) {}
                Surface(
                    shape = SquircleShape,
                    modifier = Modifier.size(124.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 3.dp
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

        Spacer(Modifier.height(40.dp))

        StaggeredEntrance(index = 1) {
            Text(
                stringResource(R.string.st_OnboardingScreen_e5f6),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(12.dp))

        StaggeredEntrance(index = 2) {
            Text(
                stringResource(R.string.st_OnboardingScreen_g7h8),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(20.dp))

        StaggeredEntrance(index = 3) {
            Text(
                stringResource(R.string.st_OnboardingScreen_i9j0),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        Spacer(Modifier.height(56.dp))

        StaggeredEntrance(index = 4) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = ExtraLargeExpressiveShape
            ) {
                Text(stringResource(R.string.st_OnboardingScreen_k1l2), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(20.dp))
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
                stringResource(R.string.st_OnboardingScreen_m3n4),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(40.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(tween(450)) + scaleIn(initialScale = 0.85f, animationSpec = tween(450)))
                        .togetherWith(fadeOut(tween(300)))
                },
                label = "update_anim"
            ) { updateState ->
                when (updateState) {
                    null -> {
                        ExpressiveContainedLoadingIndicator(
                            modifier = Modifier.size(180.dp),
                            color = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                        )
                    }
                    is UpdateCheckResult.NewUpdate -> {
                        Surface(
                            shape = SquircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier.size(140.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(60.dp))
                            }
                        }
                    }
                    else -> {
                        Surface(
                            shape = SquircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier.size(140.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(60.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        StaggeredEntrance(index = 1) {
            AnimatedContent(targetState = state, label = "text") { updateState ->
                val title = when (updateState) {
                    null -> stringResource(R.string.st_OnboardingScreen_o5p6)
                    is UpdateCheckResult.NewUpdate -> stringResource(R.string.st_OnboardingScreen_q7r8)
                    is UpdateCheckResult.UpToDate -> stringResource(R.string.st_OnboardingScreen_s9t0)
                    else -> stringResource(R.string.st_OnboardingScreen_u1v2)
                }
                val desc = when (updateState) {
                    null -> stringResource(R.string.st_OnboardingScreen_w3x4)
                    is UpdateCheckResult.NewUpdate -> "Version ${updateState.version} is available whenever you're ready."
                    is UpdateCheckResult.UpToDate -> stringResource(R.string.st_OnboardingScreen_y5z6)
                    else -> stringResource(R.string.st_OnboardingScreen_a7b8)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(56.dp))

        StaggeredEntrance(index = 2) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = ExtraLargeExpressiveShape,
                enabled = state != null
            ) {
                Text(if (state is UpdateCheckResult.NewUpdate) "Update and continue" else "Continue", fontWeight = FontWeight.SemiBold)
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
    val themeLabels = listOf("Light", "Auto", "Dark")
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Center
    ) {
        StaggeredEntrance(index = 0) {
            SectionHeader(eyebrow = "Step 2 of 5", title = "Make it yours")
        }

        Spacer(Modifier.height(28.dp))

        StaggeredEntrance(index = 1) {
            ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    CardEyebrow("Your name")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        placeholder = { Text("What should we call you?") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = SquircleShape,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Spacer(Modifier.height(28.dp))

                    CardEyebrow("Appearance")
                    Spacer(Modifier.height(12.dp))
                    ToolzConnectedButtonGroup(
                        selectedIndex = themeOptions.indexOf(themeMode).coerceAtLeast(0),
                        options = themeLabels,
                        onOptionSelected = { onThemeChange(themeOptions[it]) },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    )

                    Spacer(Modifier.height(20.dp))

                    OnboardingToggleExpressive(
                        title = "Match system colors",
                        desc = "Pull accents from your wallpaper",
                        icon = Icons.Rounded.Palette,
                        checked = dynamicColor,
                        onCheckedChange = onDynamicColorChange
                    )
                    Spacer(Modifier.height(10.dp))
                    OnboardingToggleExpressive(
                        title = "Soft gradient background",
                        desc = "A subtle touch of depth",
                        icon = Icons.Rounded.Gradient,
                        checked = gradient,
                        onCheckedChange = onGradientChange
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        StaggeredEntrance(index = 2) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                enabled = name.isNotBlank(),
                shape = ExtraLargeExpressiveShape
            ) {
                Text("Continue", fontWeight = FontWeight.SemiBold)
            }
        }
        if (name.isBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Add a name to continue",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
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
            SectionHeader(eyebrow = "Step 3 of 5", title = "Tuned for your device")
        }

        Spacer(Modifier.height(28.dp))

        StaggeredEntrance(index = 1) {
            ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Quick hardware check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(24.dp))

                    InfoSpecRow("Memory", "${String.format("%.1f", specs?.totalRamGb ?: 0.0)} GB")
                    Spacer(Modifier.height(10.dp))
                    InfoSpecRow("Android version", specs?.androidVersion?.toString() ?: "Unknown")

                    Spacer(Modifier.height(20.dp))

                    val ramFraction = ((specs?.totalRamGb ?: 0.0) / 16.0).toFloat().coerceIn(0f, 1f)
                    ToolzWavyLinearProgressIndicator(
                        progress = { ramFraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )

                    Spacer(Modifier.height(28.dp))

                    OnboardingToggleExpressive(
                        title = "Performance mode",
                        desc = "Lighter animations, snappier feel",
                        icon = Icons.Rounded.Speed,
                        checked = performanceMode,
                        onCheckedChange = onPerformanceModeChange
                    )

                    AnimatedVisibility(
                        visible = specs?.recommendPerformanceMode == true && !performanceMode,
                        enter = expandVertically(ExpressiveSpringIntSize) + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(Modifier.height(14.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                shape = MediumExpressiveShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "We think this would run smoother on your device",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        StaggeredEntrance(index = 2) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = ExtraLargeExpressiveShape
            ) {
                Text("Continue", fontWeight = FontWeight.SemiBold)
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
            SectionHeader(eyebrow = "Step 4 of 5", title = "Turn on AI features")
        }

        Spacer(Modifier.height(28.dp))

        StaggeredEntrance(index = 1) {
            ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    CardEyebrow("Groq API key")
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChange,
                        placeholder = { Text("Paste your key here") },
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

                    Spacer(Modifier.height(20.dp))

                    ToolzOutlinedExpressiveButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MediumExpressiveShape
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Get a free key", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(20.dp))

                    Surface(
                        onClick = { showGuide = true },
                        shape = MediumExpressiveShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Why Groq?", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "It's fast, free to start, and your key stays on this device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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

        Spacer(Modifier.height(40.dp))

        StaggeredEntrance(index = 2) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = ExtraLargeExpressiveShape
            ) {
                Text(if (apiKey.isBlank()) "Skip for now" else "Continue", fontWeight = FontWeight.SemiBold)
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
                Text("Setting up Groq", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                val steps = AiSettingsHelper.tutorials["Groq"] ?: emptyList()
                steps.forEachIndexed { i, step ->
                    Row(Modifier.padding(vertical = 8.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${i + 1}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(step, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(28.dp))
                ToolzExpressiveButton(
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { showGuide = false }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = BouncyShape
                ) {
                    Text("Got it")
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

    val grantedCount = listOf(
        permissionsState.allPermissionsGranted,
        usageStatsGranted,
        notificationListenerGranted,
        accessibilityGranted,
        shizukuAuthorized
    ).count { it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        StaggeredEntrance(index = 0) {
            SectionHeader(
                eyebrow = "Step 5 of 5",
                title = "Choose what to share",
                trailing = "$grantedCount/5 on"
            )
        }

        Spacer(Modifier.height(12.dp))

        StaggeredEntrance(index = 1) {
            Text(
                "Everything here is optional and stays on your device. Turn on only what's useful to you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
        }

        Spacer(Modifier.height(20.dp))

        StaggeredEntrance(index = 2) {
            ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                LazyColumn(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).height(400.dp)) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        CardEyebrow("Device access")
                        Spacer(Modifier.height(4.dp))
                    }

                    item {
                        ProtocolRow("Camera, mic & location", "For scanning and location-based tools", Icons.Rounded.DeveloperBoard, permissionsState.allPermissionsGranted) {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                    }
                    item {
                        ProtocolRow("Files", "So Toolz can open and process files you pick", Icons.Rounded.Storage, false) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            } else {
                                permissionsState.launchMultiplePermissionRequest()
                            }
                        }
                    }
                    item {
                        ProtocolRow("Usage insights", "See time spent in apps, stays local", Icons.Rounded.Analytics, usageStatsGranted) {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    }
                    item {
                        ProtocolRow("Notification history", "Search and revisit past notifications", Icons.Rounded.Security, notificationListenerGranted) {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    }
                    item {
                        ProtocolRow("Automation", "Lets Toolz tap through routine tasks for you", Icons.Rounded.Hub, accessibilityGranted) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }
                    item {
                        ProtocolRow("Shizuku", "Unlocks advanced tools without root", Icons.Rounded.Shield, shizukuAuthorized) {
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
                        Spacer(Modifier.height(24.dp))
                        CardEyebrow("Notifications")
                        Spacer(Modifier.height(4.dp))
                    }

                    item {
                        OnboardingToggleExpressive("Push notifications", "Real-time updates from Toolz", Icons.Rounded.NotificationsActive, notificationsEnabled, onNotificationsChange)
                        Spacer(Modifier.height(10.dp))
                    }
                    item {
                        OnboardingToggleExpressive("Keep a local history", "Encrypted, on-device only", Icons.Rounded.VpnKey, vaultEnabled, onVaultChange)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(36.dp))

        StaggeredEntrance(index = 3) {
            ToolzExpressiveButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = ExtraLargeExpressiveShape
            ) {
                Text("Continue", fontWeight = FontWeight.SemiBold)
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
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                Surface(
                    modifier = Modifier.size(188.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ) {}
                Surface(
                    shape = SquircleShape,
                    modifier = Modifier.size(124.dp),
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 5.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(60.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        StaggeredEntrance(index = 1) {
            Text(
                "You're all set",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(10.dp))

        StaggeredEntrance(index = 2) {
            Text(
                "Welcome, ${name.ifBlank { "friend" }}. Toolz is ready whenever you are.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(56.dp))

        StaggeredEntrance(index = 3) {
            ToolzExpressiveButton(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = ExtraLargeExpressiveShape
            ) {
                Text("Start using Toolz", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ── Shared ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(eyebrow: String, title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (trailing != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun CardEyebrow(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp
    )
}

@Composable
fun OnboardingToggleExpressive(
    title: String,
    desc: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(300),
        label = "toggle_container"
    )
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = MediumExpressiveShape,
        color = containerColor,
        border = if (checked) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
            }
            Spacer(Modifier.width(8.dp))
            ExpressiveSwitch(checked, onCheckedChange)
        }
    }
}

@Composable
private fun ProtocolRow(title: String, desc: String, icon: ImageVector, granted: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = SmallExpressiveShape,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (granted) Icons.Rounded.CheckCircle else icon, null, tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
            }
            if (!granted) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
private fun InfoSpecRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        Text(value, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipConfirmationDialogExpressive(
    setupScore: Float = 0f,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = ExtraLargeExpressiveShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Insights, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Skip setup?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (setupScore > 0f)
                        "You're ${(setupScore * 100).toInt()}% through personalizing Toolz. You can always finish this later from Settings."
                    else
                        "No problem — you can personalize Toolz anytime from Settings.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(28.dp))
                ToolzExpressiveButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Keep going", fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onConfirm, modifier = Modifier.padding(top = 6.dp)) {
                    Text("Skip anyway", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
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
