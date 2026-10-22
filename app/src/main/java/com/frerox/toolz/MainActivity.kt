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

package com.frerox.toolz

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.*
import com.frerox.toolz.data.ai.AiSettingsManager
import com.frerox.toolz.data.developer.CapturedCrash
import com.frerox.toolz.data.developer.FastReportExporter
import com.frerox.toolz.data.developer.FastReportStore
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.update.pendingUpdate
import com.frerox.toolz.data.repository.FlashlightRepository
import com.frerox.toolz.service.FlashlightService
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.screens.LoadingOverlay
import com.frerox.toolz.ui.screens.LoadingViewModel
import com.frerox.toolz.ui.screens.OnboardingScreen
import com.frerox.toolz.ui.screens.dashboard.DashboardScreen
import com.frerox.toolz.ui.screens.light.*
import com.frerox.toolz.ui.screens.math.*
import com.frerox.toolz.ui.screens.media.BackgroundRemoverScreen
import com.frerox.toolz.ui.screens.media.MusicPlayerScreen
import com.frerox.toolz.ui.screens.media.MusicPlayerViewModel
import com.frerox.toolz.ui.screens.media.isCurrentNoteTrack
import com.frerox.toolz.ui.screens.media.FileConverterScreen
import com.frerox.toolz.ui.screens.notepad.NotepadScreen
import com.frerox.toolz.ui.screens.pdf.PdfViewModel
import com.frerox.toolz.ui.screens.pdf.ToolzPdfScreen
import com.frerox.toolz.ui.screens.sensors.*
import com.frerox.toolz.ui.screens.settings.BackupRestoreScreen
import com.frerox.toolz.ui.screens.settings.BackupRestoreViewModel
import com.frerox.toolz.ui.screens.settings.SettingsScreen
import com.frerox.toolz.ui.screens.settings.UpdateScreen
import com.frerox.toolz.ui.screens.time.*
import com.frerox.toolz.ui.screens.todo.TodoScreen
import com.frerox.toolz.ui.screens.utils.SmartEncrypterScreen
import com.frerox.toolz.ui.screens.utils.*
import com.frerox.toolz.ui.screens.qr.*
import com.frerox.toolz.ui.screens.notifications.NotificationVaultScreen
import com.frerox.toolz.ui.screens.focus.FocusFlowScreen
import com.frerox.toolz.ui.screens.focus.CaffeinateScreen
import com.frerox.toolz.ui.screens.clipboard.ClipboardScreen
import com.frerox.toolz.ui.screens.ai.AiAssistantScreen
import com.frerox.toolz.ui.screens.ai.AiAssistantViewModel
import com.frerox.toolz.ui.screens.ai.AiSettingsScreen
import com.frerox.toolz.ui.screens.calendar.CalendarScreen
import com.frerox.toolz.ui.screens.cleaner.CleanerScreen
import com.frerox.toolz.ui.screens.browser.TabManagementScreen
import com.frerox.toolz.ui.screens.search.SearchScreen
import com.frerox.toolz.ui.screens.browser.WebViewScreen
import com.frerox.toolz.ui.screens.browser.WebViewViewModel
import com.frerox.toolz.ui.screens.password.PasswordVaultScreen
import com.frerox.toolz.ui.screens.network.NetworkSuiteScreen
import com.frerox.toolz.ui.screens.whisper.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.util.NotificationHelper
import com.frerox.toolz.ui.components.MarkdownContent
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground
import androidx.compose.ui.tooling.preview.Preview
import com.frerox.toolz.service.StepCounterService
import androidx.compose.ui.graphics.graphicsLayer
import com.frerox.toolz.ui.components.OfflineTransitionOverlay
import com.frerox.toolz.util.OfflineManager
import com.frerox.toolz.util.OfflineState
import kotlinx.coroutines.delay
import com.frerox.toolz.util.VibrationManager
import com.frerox.toolz.worker.NewsCheckWorker
import com.frerox.toolz.worker.NotificationCleanupWorker
import com.frerox.toolz.worker.UpdateCheckWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener {

    companion object {
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val EXTRA_SHOW_UPDATE = "show_update"
        const val EXTRA_SHOW_UPDATE_DIALOG = "show_update_dialog"
        const val EXTRA_FROM_SHORTCUT = "from_shortcut"
        const val EXTRA_SHORTCUT_ID = "shortcut_id"
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

        val LocalFlashlightRepository = staticCompositionLocalOf<FlashlightRepository?> { null }

        /**
         * Whether this intent originated from a tool shortcut (static or pinned).
         * Checks both boolean and string extras and existence (static shortcuts store as string "true").
         */
        fun isShortcutIntent(intent: Intent?): Boolean {
            if (intent == null) return false
            if (intent.getBooleanExtra(EXTRA_FROM_SHORTCUT, false)) return true
            if (intent.getStringExtra(EXTRA_FROM_SHORTCUT) == "true") return true
            return intent.hasExtra(EXTRA_FROM_SHORTCUT)
        }
    }
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var whisperPushTargetStore: com.frerox.toolz.push.WhisperPushTargetStore

    @Inject
    lateinit var whisperPushRegistrar: com.frerox.toolz.push.WhisperPushRegistrar

    @Inject
    lateinit var vibrationManager: VibrationManager

    @Inject
    lateinit var aiSettingsManager: AiSettingsManager

    @Inject
    lateinit var offlineManager: OfflineManager

    @Inject
    lateinit var flashlightRepository: FlashlightRepository

    @Inject
    lateinit var whisperAvatarLoader: com.frerox.toolz.data.whisper.WhisperAvatarLoader

    @Inject
    lateinit var updateManager: com.frerox.toolz.data.update.UpdateManager

    private val currentIntentState = mutableStateOf<Intent?>(null)
    private val currentIntentVersion = mutableStateOf(0L)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        ToolzApplication.setFocused(hasFocus)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FCM → UnifiedPush swap: re-register the push endpoint (self-heal, replaces
        // the old FCM auto-init token refresh) and upload any registration parked
        // before login. MUST run AFTER super.onCreate() — Hilt performs field
        // injection there, and lifecycleScope's Main.immediate dispatcher would
        // otherwise execute this coroutine body inline before the push fields are
        // assigned (crash).
        lifecycleScope.launch {
            whisperPushRegistrar.ensureRegistered()
            whisperPushTargetStore.retryPendingIfAny()
        }
        // Widget generated previews (API 35+, rate-limited, non-fatal).
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.frerox.toolz.widget.ui.WidgetPreviewsPublisher.publishAll(this@MainActivity)
            } catch (_: Exception) { }
        }
        enableEdgeToEdge()
        window.setBackgroundDrawableResource(android.R.color.background_dark)
        Shizuku.addRequestPermissionResultListener(this)
        currentIntentState.value = intent
        currentIntentVersion.value += 1

        NotificationHelper.createAllChannels(this)
        scheduleCleanup()
        scheduleUpdateCheck()
        scheduleNewsCheck()
        scheduleFocusUsageSnapshot()

        // Developer Mode version guard: auto-disable master + all spoofs on
        // version bump so stale local fakes can never leak into a new build.
        // Also honors crash-on-next-launch test flag from the crash lab.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val pi = packageManager.getPackageInfo(packageName, 0)
                @Suppress("DEPRECATION")
                val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    pi.longVersionCode
                } else {
                    pi.versionCode.toLong()
                }
                settingsRepository.ensureDeveloperModeVersion(code)
                val crashNext = try {
                    settingsRepository.devCrashOnNextLaunch.first()
                } catch (_: Exception) { false }
                if (crashNext) {
                    val cleared = try {
                        settingsRepository.setDevCrashOnNextLaunch(false)
                        true
                    } catch (_: Exception) {
                        false
                    }
                    if (!cleared) {
                        com.frerox.toolz.data.developer.DevLogHub.e(
                            com.frerox.toolz.data.developer.DevLogHub.TAG,
                            "Crash-on-next-launch skipped — could not clear the flag",
                        )
                    } else {
                        com.frerox.toolz.data.developer.DevLogHub.e(
                            com.frerox.toolz.data.developer.DevLogHub.TAG,
                            "Crash-on-next-launch flag consumed — crashing the app now",
                        )
                        // A main-thread throw is a real process crash (not a swallowed
                        // coroutine failure), which is what the crash lab promises.
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            throw RuntimeException("[DEV TEST] crash-on-next-launch flag was set")
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        // Permissions are now deferred to feature sheets (PurgeShot / Whisper) and onboarding.
        // No auto-request on launch — prevents system dialogs before onboarding.
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = "SYSTEM")
            val dynamicColor by settingsRepository.dynamicColor.collectAsState(initial = true)
            val customPrimary by settingsRepository.customPrimaryColor.collectAsState(initial = null)
            val customSecondary by settingsRepository.customSecondaryColor.collectAsState(initial = null)
            val iconPresetId by settingsRepository.appIconPresetId.collectAsState(initial = "grape-haze")
            val iconAdaptTheme by settingsRepository.appIconAdaptTheme.collectAsState(initial = true)
            val backgroundGradientEnabled by settingsRepository.backgroundGradientEnabled.collectAsState(initial = true)
            val performanceMode by settingsRepository.performanceMode.collectAsState(initial = false)
            val hapticEnabled by settingsRepository.hapticFeedback.collectAsState(initial = true)
            val hapticIntensity by settingsRepository.hapticIntensity.collectAsState(initial = 0.5f)
            val showTopAppBarDescriptions by settingsRepository.showTopAppBarDescriptions.collectAsState(initial = true)

            // Post-crash recovery: FastReportStore writes a snapshot from the
            // uncaught handler; on the next launch we offer to turn it into a
            // Markdown report. The snapshot is only cleared after the user
            // acts, so process death cannot silently lose the diagnostics.
            val crashContext = LocalContext.current
            val fastReportScope = rememberCoroutineScope()
            var pendingCrash by remember { mutableStateOf<CapturedCrash?>(null) }
            // Retained separately so the exit animation still has content once
            // pendingCrash is cleared.
            var shownCrash by remember { mutableStateOf<CapturedCrash?>(null) }
            var fastReportBusy by remember { mutableStateOf(false) }
            var fastReportError by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                val stored = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val parsed = FastReportStore.readPendingCrash(crashContext)
                    if (parsed == null && FastReportStore.hasPendingCrash(crashContext)) {
                        // A corrupt or half-written snapshot must not haunt
                        // every launch; drop it once, then carry on normally.
                        FastReportStore.clearPendingCrash(crashContext)
                    }
                    parsed
                }
                pendingCrash = stored
                shownCrash = stored
            }

            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            // Icon-tuned accents: when Dynamic colors are off and Adapt to theme is on,
            // the selected icon's polished light/dark theme fills in for any accent
            // the user didn't pick explicitly. Explicit custom colors always win.
            val iconSpec = com.frerox.toolz.data.skin.IconCatalog.find(iconPresetId)
            val iconPrimary = if (!dynamicColor && iconAdaptTheme && customPrimary == null) {
                Color(iconSpec.themePrimary(isDark).toInt())
            } else null
            val iconSecondary = if (!dynamicColor && iconAdaptTheme && customSecondary == null) {
                Color(iconSpec.themeSecondary(isDark).toInt())
            } else null

            val offlineState by offlineManager.offlineState.collectAsState(initial = OfflineState.ONLINE)

            val loadingViewModel: LoadingViewModel = hiltViewModel()
            val loadingVisible by loadingViewModel.isVisible.collectAsState()
            val loadingMessage by loadingViewModel.loadingMessage.collectAsState()
            val loadingProgress by loadingViewModel.loadingProgress.collectAsState()

            val onboardingCompleted by settingsRepository.onboardingCompleted.collectAsState(initial = true)

            var offlineOverlayVisible by remember { mutableStateOf(false) }
            var offlineOverlayReady by remember { mutableStateOf(false) }
            var lastOfflineState by remember { mutableStateOf<OfflineState?>(null) }
            var offlineFirstEmissionDone by remember { mutableStateOf(false) }
            var blurSupported by remember { mutableStateOf(true) }
            val offlinePopupEnabled by settingsRepository.offlinePopupEnabled.collectAsState(initial = true)

            LaunchedEffect(offlineState, offlinePopupEnabled) {
                // Never show the transition overlay for the initial emission on cold
                // start (e.g. persisted manual Offline Mode). Only real transitions
                // after the first stable value trigger the popup + blur. This breaks
                // the permanent crash loop where every launch immediately blurred.
                if (!offlineFirstEmissionDone) {
                    lastOfflineState = offlineState
                    offlineFirstEmissionDone = true
                    return@LaunchedEffect
                }
                if (offlineState != lastOfflineState) {
                    lastOfflineState = offlineState
                    // Offline/online popup toggle (Settings → Interaction & HUD, on by default).
                    if (!offlinePopupEnabled) {
                        offlineOverlayVisible = false
                        return@LaunchedEffect
                    }
                    offlineOverlayReady = false
                    offlineOverlayVisible = true
                    delay(1200)
                    offlineOverlayReady = true
                    delay(1000)
                    offlineOverlayVisible = false
                }
            }

            val incomingIntent = currentIntentState.value
            val incomingIntentVersion = currentIntentVersion.value

            LaunchedEffect(incomingIntentVersion) {
                val latestIntent = incomingIntent ?: return@LaunchedEffect
                if (isQuickOpenIntent(latestIntent)) {
                    loadingViewModel.skipLoading()
                }
            }

            val blurAnim by animateFloatAsState(
                targetValue = if (offlineOverlayVisible) 20f else 0f,
                animationSpec = tween(600),
                label = "blur"
            )
            // MIUI-safe blur: SafeBlur clamps radius and swallows native
            // `nativePtr is null` failures. On first failure blur is latched off
            // for the rest of the session (overlay still shows, just unblurred).
            val safeBlurEffect = remember(blurAnim, blurSupported, performanceMode) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null
                else if (performanceMode || !blurSupported) null
                else if (blurAnim < com.frerox.toolz.util.SafeBlur.MIN_RADIUS) null
                else com.frerox.toolz.util.SafeBlur.createBlurOrNull(blurAnim)
            }
            val shouldLatchBlurOff = safeBlurEffect == null && blurAnim >= com.frerox.toolz.util.SafeBlur.MIN_RADIUS && !performanceMode && blurSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && offlineOverlayVisible
            if (shouldLatchBlurOff) {
                SideEffect { blurSupported = false }
            }

            ToolzTheme(
                darkTheme = isDark,
                dynamicColor = dynamicColor,
                customPrimary = (customPrimary?.let { Color(it) } ?: iconPrimary),
                customSecondary = (customSecondary?.let { Color(it) } ?: iconSecondary),
                backgroundGradientEnabled = backgroundGradientEnabled,
                performanceMode = performanceMode,
                hapticEnabled = hapticEnabled,
                hapticIntensity = hapticIntensity,
                showTopAppBarDescriptions = showTopAppBarDescriptions,
                vibrationManager = vibrationManager
            ) {
                CompositionLocalProvider(
                    LocalFlashlightRepository provides flashlightRepository,
                    com.frerox.toolz.ui.LocalWhisperAvatarLoader provides whisperAvatarLoader
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .toolzBackground()
                            .then(
                                if (safeBlurEffect != null) {
                                    Modifier.graphicsLayer {
                                        renderEffect = safeBlurEffect
                                    }
                                } else Modifier
                            )
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ) {
                            val navController = rememberNavController()
                            val pdfViewModel: PdfViewModel = hiltViewModel()

                            // Dev console shake-to-open: zero sensor cost unless master + toggle are both ON.
                            val shakeDetector = remember {
                                com.frerox.toolz.data.developer.ShakeDetector(applicationContext)
                            }
                            val devModeOn by settingsRepository.developerModeEnabled.collectAsState(initial = false)
                            val shakeOn by settingsRepository.devShakeToOpen.collectAsState(initial = false)
                            DisposableEffect(devModeOn, shakeOn) {
                                if (devModeOn && shakeOn) {
                                    shakeDetector.start {
                                        if (!this@MainActivity.lifecycle.currentState
                                                .isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                                        ) {
                                            return@start
                                        }
                                        if (navController.currentDestination?.route ==
                                            com.frerox.toolz.ui.navigation.Screen.DeveloperMode.route
                                        ) {
                                            return@start
                                        }
                                        try {
                                            navController.navigate(
                                                com.frerox.toolz.ui.navigation.Screen.DeveloperMode.route,
                                            ) { launchSingleTop = true }
                                        } catch (_: Exception) { }
                                    }
                                } else {
                                    shakeDetector.stop()
                                }
                                onDispose { shakeDetector.stop() }
                            }

                            ToolzNavHost(
                                navController = navController,
                                settingsRepository = settingsRepository,
                                aiSettingsManager = aiSettingsManager,
                                pdfViewModel = pdfViewModel,
                                performanceMode = performanceMode,
                                backgroundGradientEnabled = backgroundGradientEnabled,
                                incomingIntent = incomingIntent,
                                incomingIntentVersion = incomingIntentVersion,
                                onboardingCompleted = onboardingCompleted
                            )

                            UpdateOverlay(
                                settingsRepository = settingsRepository,
                                updateManager = updateManager,
                                onOpenUpdater = {
                                    try {
                                        navController.navigate(com.frerox.toolz.ui.navigation.Screen.Update.route)
                                    } catch (_: Exception) {
                                    }
                                },
                            )
                        }
                    }

                    LoadingOverlay(
                        isVisible = loadingVisible,
                        loadingMessage = loadingMessage,
                        loadingProgress = loadingProgress
                    )

                    OfflineTransitionOverlay(
                        state = offlineState,
                        visible = offlineOverlayVisible,
                        isReady = offlineOverlayReady,
                        performanceMode = performanceMode
                    )

                    // Crash recovery is hosted at the app root so it appears
                    // no matter which screen the user landed on, and above the
                    // news popup, loading and offline overlays.
                    AnimatedVisibility(
                        visible = pendingCrash != null,
                        enter = fadeIn(tween(200)) + scaleIn(
                            initialScale = 0.92f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                        exit = fadeOut(tween(150)) + scaleOut(
                            targetScale = 0.96f,
                            animationSpec = tween(150)
                        )
                    ) {
                        val crash = shownCrash ?: return@AnimatedVisibility
                        com.frerox.toolz.ui.screens.diagnostics.FastReportCrashPopup(
                            crash = crash,
                            busy = fastReportBusy,
                            errorText = fastReportError,
                            onFastReport = {
                                if (!fastReportBusy) {
                                    fastReportBusy = true
                                    fastReportError = null
                                    fastReportScope.launch {
                                        val result = kotlinx.coroutines.withContext(
                                            kotlinx.coroutines.Dispatchers.IO
                                        ) {
                                            val latest = FastReportStore.readPendingCrash(crashContext)
                                                ?: crash
                                            FastReportExporter.exportAndShare(crashContext, latest)
                                        }
                                        fastReportBusy = false
                                        if (result.shared) {
                                            FastReportStore.clearPendingCrash(crashContext)
                                            pendingCrash = null
                                            fastReportError = null
                                        } else {
                                            fastReportError = crashContext.getString(
                                                R.string.st_FastReport_failed
                                            )
                                        }
                                    }
                                }
                            },
                            onDismiss = {
                                if (!fastReportBusy) {
                                    FastReportStore.clearPendingCrash(crashContext)
                                    pendingCrash = null
                                    fastReportError = null
                                }
                            }
                        )
                    }
                }
                }
            }
        }

        lifecycleScope.launch {
            settingsRepository.stepCounterEnabled.collect { enabled ->
                if (enabled) {
                    if (hasActivityRecognitionPermission()) {
                        startStepService()
                    }
                } else {
                    stopStepService()
                }
            }
        }

        // PurgeShot: keep ContentObserver alive and restore queue after every launch (survives update/clear-data via external mirror)
        lifecycleScope.launch {
            settingsRepository.purgeShotEnabled.collect { enabled ->
                val svc = android.content.Intent(this@MainActivity, com.frerox.toolz.service.PurgeShotService::class.java).apply {
                    action = if (enabled) com.frerox.toolz.service.PurgeShotService.ACTION_START else com.frerox.toolz.service.PurgeShotService.ACTION_STOP
                }
                try {
                    if (enabled) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
                    } else {
                        startService(svc)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        lifecycleScope.launch {
            combine(
                settingsRepository.flashlightNotificationsEnabled,
                flashlightRepository.isOn
            ) { notifEnabled, flashOn -> notifEnabled to flashOn }.collect { (notifEnabled, flashOn) ->
                val intent = Intent(this@MainActivity, FlashlightService::class.java)
                if (flashOn) {
                    startForegroundService(intent)
                } else if (!notifEnabled) {
                    stopService(intent)
                }
            }
        }

        lifecycleScope.launch {
            settingsRepository.pomodoroNotifications.collect { enabled ->
                val intent = Intent(this@MainActivity, com.frerox.toolz.service.ToolService::class.java)
                if (enabled) {
                    startForegroundService(intent)
                } else {
                    // We don't stop the service here because it might be running a timer
                    // ToolService should handle its own lifecycle based on settings and state
                }
            }
        }

        // Keep music service warm for earphone detection
        val musicIntent = Intent(this, com.frerox.toolz.service.MusicPlayerService::class.java)
        try {
            startService(musicIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState.value = intent
        currentIntentVersion.value += 1
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(this)
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            // Permission result handled
        }
    }

    private fun scheduleCleanup() {
        val cleanupRequest = PeriodicWorkRequestBuilder<NotificationCleanupWorker>(
            24, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(true)
                .build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "NotificationCleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
    }

    private fun scheduleUpdateCheck() {
        val updateCheckRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            24, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                // Checks run on any network; the worker only auto-downloads
                // when the network is unmetered (critical updates exempt).
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UpdateCheck",
            // UPDATE so constraint/policy changes reach existing installs.
            ExistingPeriodicWorkPolicy.UPDATE,
            updateCheckRequest
        )
    }

    private fun scheduleNewsCheck() {
        val newsCheckRequest = PeriodicWorkRequestBuilder<NewsCheckWorker>(
            6, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "NewsCheck",
            ExistingPeriodicWorkPolicy.UPDATE,
            newsCheckRequest
        )
    }

    private fun scheduleFocusUsageSnapshot() {
        val focusUsageRequest = PeriodicWorkRequestBuilder<com.frerox.toolz.worker.FocusUsageWorker>(
            8, TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "FocusUsageSnapshot",
            ExistingPeriodicWorkPolicy.KEEP,
            focusUsageRequest
        )
    }

    private fun startStepService() {
        val intent = Intent(this, StepCounterService::class.java)
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopStepService() {
        val intent = Intent(this, StepCounterService::class.java)
        stopService(intent)
    }

    private fun isQuickOpenIntent(intent: Intent): Boolean {
        val componentName = intent.component?.className ?: ""
        if (componentName.endsWith(".MusicPlayer") ||
            componentName.endsWith(".PdfViewer") ||
            componentName.endsWith(".FileConverter") ||
            componentName.endsWith(".BackgroundRemover") ||
            componentName.endsWith(".QrScanner") ||
            componentName.endsWith(".BackupRestore") ||
            componentName.endsWith(".Encrypter") ||
            componentName.endsWith(".Decrypter")) {
            return true
        }

        // PDF View/Send
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND) {
            val uri = if (intent.action == Intent.ACTION_SEND) {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.data
            }
            if (uri != null) {
                val mimeType = contentResolver.getType(uri)
                val isPdf = intent.type == "application/pdf" ||
                        mimeType == "application/pdf" ||
                        uri.toString().lowercase().endsWith(".pdf")
                if (isPdf) return true
            }
        }

        // Specific tool navigation
        if (intent.hasExtra(EXTRA_NAVIGATE_TO) || 
            intent.hasExtra(EXTRA_SHOW_UPDATE) || 
            intent.hasExtra(EXTRA_SHOW_UPDATE_DIALOG)) {
            return true
        }

        // Tool shortcuts – skip loading, go directly to tool
        if (isShortcutIntent(intent)) {
            return true
        }

        return false
    }
}

@Composable
fun UpdateOverlay(
    settingsRepository: SettingsRepository,
    updateManager: com.frerox.toolz.data.update.UpdateManager,
    onOpenUpdater: () -> Unit,
) {
    val availableVersion by settingsRepository.updateAvailableVersion.collectAsState(initial = null)
    val changelog by settingsRepository.updateChangelog.collectAsState(initial = null)
    val managerState by updateManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }

    // The persisted offer is the source of truth: Later/Skip clear it, so the
    // sheet stays gone after a restart even though the manager keeps state.
    LaunchedEffect(availableVersion) {
        showSheet = availableVersion != null
    }

    if (showSheet && availableVersion != null) {
        UpdateSheetContent(
            availableVersion = availableVersion ?: "",
            changelog = changelog ?: "",
            downloadState = managerState,
            onDismiss = { showSheet = false },
            onDownload = {
                val pending = updateManager.currentPendingOrNull()
                    ?: updateManager.pendingFromStore()
                if (pending != null) {
                    updateManager.download(pending, autoInstall = true)
                } else {
                    scope.launch {
                        updateManager.check(showNotification = false, force = true)
                            ?.let { updateManager.download(it, autoInstall = true) }
                    }
                }
            },
            onOpenUpdater = {
                showSheet = false
                onOpenUpdater()
            },
            onLater = {
                showSheet = false
                updateManager.dismissOffer()
            },
            onSkip = {
                showSheet = false
                updateManager.skipVersion(availableVersion ?: "")
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheetContent(
    availableVersion: String,
    changelog: String,
    downloadState: com.frerox.toolz.data.update.UpdateManagerState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpenUpdater: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        UpdateOverlayContent(
            availableVersion = availableVersion,
            changelog = changelog,
            downloadState = downloadState,
            onDownload = onDownload,
            onOpenUpdater = onOpenUpdater,
            onIgnore = onLater,
            onSkip = onSkip,
        )
    }
}

@Composable
fun UpdateOverlayContent(
    availableVersion: String,
    changelog: String,
    onDownload: () -> Unit,
    onOpenUpdater: () -> Unit,
    onIgnore: () -> Unit,
    downloadState: com.frerox.toolz.data.update.UpdateManagerState? = null,
    onSkip: (() -> Unit)? = null,
) {
    val vibrationManager = com.frerox.toolz.ui.theme.LocalVibrationManager.current
    val downloading = downloadState as? com.frerox.toolz.data.update.UpdateManagerState.Downloading
    val ready = downloadState is com.frerox.toolz.data.update.UpdateManagerState.ReadyToInstall ||
        downloadState is com.frerox.toolz.data.update.UpdateManagerState.Installing
    val pending = downloadState?.pendingUpdate()
    val available = (downloadState as? com.frerox.toolz.data.update.UpdateManagerState.Available)?.update
    val critical = pending?.isCritical == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                com.frerox.toolz.ui.components.CurrentToolzMark(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)),
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.st_Update_OverlayTitle),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (critical) {
                        stringResource(
                            R.string.st_Update_OverlayCriticalSubtitle,
                            availableVersion,
                        )
                    } else {
                        stringResource(R.string.st_Update_OverlaySubtitle, availableVersion)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (downloading != null) {
            val smoothProgress by animateFloatAsState(
                targetValue = (downloading.progress / 100f).coerceIn(0f, 1f),
                animationSpec = tween(450, easing = LinearOutSlowInEasing),
                label = "overlay_progress",
            )
            val pct by animateIntAsState(
                targetValue = downloading.progress.toInt().coerceIn(0, 100),
                animationSpec = tween(450, easing = LinearOutSlowInEasing),
                label = "overlay_percent",
            )
            Text(
                text = stringResource(R.string.st_Update_OverlayDownloading, pct),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            LinearProgressIndicator(
                progress = { smoothProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            val sizeText = if (downloading.totalBytes > 0) {
                stringResource(
                    R.string.st_Update_Of,
                    com.frerox.toolz.data.update.formatBytes(downloading.bytesDownloaded),
                    com.frerox.toolz.data.update.formatBytes(downloading.totalBytes),
                )
            } else {
                com.frerox.toolz.data.update.formatBytes(downloading.bytesDownloaded)
            }
            val speedText = if (downloading.speedBps > 0) {
                stringResource(
                    R.string.st_Update_PerSecond,
                    com.frerox.toolz.data.update.formatBytes(downloading.speedBps),
                )
            } else {
                null
            }
            Text(
                text = listOfNotNull(sizeText, speedText).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (ready) {
            Text(
                text = stringResource(R.string.st_Update_OverlayReadyBody),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (changelog.isNotBlank() && !critical) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 170.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Box(Modifier.verticalScroll(rememberScrollState())) {
                    MarkdownContent(
                        markdown = changelog,
                        modifier = Modifier.padding(16.dp),
                        baseFontSize = 13.sp,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (pending?.sizeBytes != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "${com.frerox.toolz.data.update.formatBytes(pending.sizeBytes)} · " +
                        stringResource(R.string.st_Update_CheckedBeforeInstall),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (downloading != null || ready) {
            ToolzExpressiveButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    onOpenUpdater()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.st_Update_OpenUpdater),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            ToolzExpressiveButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    onDownload()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (critical) {
                        stringResource(R.string.st_Update_DownloadCritical)
                    } else {
                        stringResource(R.string.st_Update_DownloadUpdate)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        TextButton(onClick = onOpenUpdater, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.st_Update_SeeDetails))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    onIgnore()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(R.string.st_Update_Later),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onSkip != null && available != null && !critical) {
                TextButton(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        onSkip()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(R.string.st_Update_SkipVersion),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(4.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun UpdateOverlayPreview() {
    ToolzTheme {
        UpdateOverlayContent(
            availableVersion = "1.1.8",
            changelog = "### Highlights\n- Faster downloads with resume\n- Clearer install flow",
            onDownload = {},
            onOpenUpdater = {},
            onIgnore = {},
            onSkip = {},
        )
    }
}

@Composable
fun ToolzNavHost(
    navController: androidx.navigation.NavHostController,
    settingsRepository: SettingsRepository,
    aiSettingsManager: AiSettingsManager,
    pdfViewModel: PdfViewModel,
    performanceMode: Boolean,
    backgroundGradientEnabled: Boolean,
    incomingIntent: Intent?,
    incomingIntentVersion: Long,
    onboardingCompleted: Boolean,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var pendingExternalRoute by remember { mutableStateOf<String?>(null) }
    var pendingIsShortcut by remember { mutableStateOf(false) }
    var pendingNewsId by remember { mutableStateOf<String?>(null) }
    var launchedFromShortcut by remember { mutableStateOf(MainActivity.isShortcutIntent(incomingIntent)) }
    var showShortcutExitDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Reset shortcut flag when returning to dashboard/onboarding
    LaunchedEffect(currentRoute) {
        if (currentRoute == Screen.Dashboard.route || currentRoute == "onboarding" || currentRoute == null) {
            launchedFromShortcut = false
            showShortcutExitDialog = false
        }
        // Clear a consumed news highlight once we leave the news screen.
        if (currentRoute != Screen.ToolzNews.route) {
            pendingNewsId = null
        }
    }

    LaunchedEffect(incomingIntentVersion) {
        val latestIntent = incomingIntent ?: return@LaunchedEffect
        val isShortcut = MainActivity.isShortcutIntent(latestIntent)
        pendingNewsId = latestIntent.getStringExtra("news_id")?.takeIf { it.isNotBlank() }
        
        // 1. Resolve route first (handles aliases specifically)
        val resolvedRoute = resolveExternalNavigationRoute(latestIntent)
        
        // 2. Handle Whisper Chat Deep Link — P1-18 FIX: validate non-empty, not self, UUID/valid id shape.
        if (latestIntent.action == "com.frerox.toolz.OPEN_WHISPER_CHAT") {
            val otherUserId = latestIntent.getStringExtra("otherUserId")?.trim()
            if (!otherUserId.isNullOrBlank() && otherUserId.length in 5..128 && otherUserId.matches(Regex("[a-zA-Z0-9_-]+"))) {
                // Self-chat prevented at Repository level too, but guard early to avoid navigation loop.
                navController.navigate(Screen.WhisperChat.createRoute(otherUserId))
                return@LaunchedEffect
            } else {
                android.util.Log.w("MainActivity", "Invalid OPEN_WHISPER_CHAT otherUserId: $otherUserId")
            }
        }

        // 2b. Handle Whisper Friend Requests Deep Link
        if (latestIntent.action == "com.frerox.toolz.OPEN_WHISPER_FRIEND_REQUESTS" ||
            latestIntent.getBooleanExtra("open_friend_requests", false)) {
            navController.navigate(Screen.Whisper.route)
            return@LaunchedEffect
        }

        // 2c. Handle Whisper Group Deep Links (Phase-2 groups).
        // Groups are hidden in this release. The group surfaces stay in the
        // codebase, but no deep link or push tap may reach them: both actions
        // fall back to the Whisper hub, which no longer renders the section.
        if (latestIntent.action == "com.frerox.toolz.OPEN_WHISPER_GROUP_INVITES") {
            navController.navigate(Screen.Whisper.route)
            return@LaunchedEffect
        }
        if (latestIntent.action == "com.frerox.toolz.OPEN_WHISPER_GROUP") {
            val groupId = latestIntent.getStringExtra("groupId")?.trim()
            if (!com.frerox.toolz.data.whisper.groupsEnabled()) {
                navController.navigate(Screen.Whisper.route) {
                    launchSingleTop = true
                }
                return@LaunchedEffect
            }
            if (!groupId.isNullOrBlank() && groupId.length in 5..128 && groupId.matches(Regex("[a-zA-Z0-9_-]+"))) {
                navController.navigate(Screen.WhisperGroupChat.createRoute(groupId))
                return@LaunchedEffect
            } else {
                android.util.Log.w("MainActivity", "Invalid OPEN_WHISPER_GROUP groupId: $groupId")
            }
        }
        // 2d. Daily Ups deep link. Hidden in this release: the routes stay
        // registered, but the link lands on the hub instead of a blank screen.
        if (latestIntent.action == "com.frerox.toolz.OPEN_WHISPER_DAILY_UPS") {
            if (com.frerox.toolz.data.whisper.WhisperDailyUpsConfig.ENABLED) {
                navController.navigate(Screen.WhisperDailyUps.createRoute())
            } else {
                navController.navigate(Screen.Whisper.route) {
                    launchSingleTop = true
                }
            }
            return@LaunchedEffect
        }

        // 3. Handle PDF if NOT handled by a specific alias already
        // (If resolvedRoute is PdfReader, we'll handle it here to call openPdf)
        if (resolvedRoute == Screen.PdfReader.route || 
            (resolvedRoute == null && (latestIntent.action == Intent.ACTION_VIEW || latestIntent.action == Intent.ACTION_SEND))) {
            
            val uri = getUriFromIntent(latestIntent)
            if (uri != null) {
                val mimeType = context.contentResolver.getType(uri)
                val isPdf = latestIntent.type == "application/pdf" ||
                            mimeType == "application/pdf" ||
                            uri.toString().lowercase().endsWith(".pdf")

                if (isPdf) {
                    var title = context.getString(R.string.st_MainActivity_2b8a)
                    try {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                title = cursor.getString(nameIndex)
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }

                    pdfViewModel.openPdf(uri, title)
                    pendingExternalRoute = Screen.PdfReader.route
                    pendingIsShortcut = isShortcut
                    return@LaunchedEffect
                }
            }
        }

        pendingExternalRoute = resolvedRoute
        pendingIsShortcut = isShortcut && resolvedRoute != null
    }

    LaunchedEffect(pendingExternalRoute, onboardingCompleted, currentRoute) {
        val route = pendingExternalRoute ?: return@LaunchedEffect
        if (!onboardingCompleted) return@LaunchedEffect
        if (currentRoute == null || currentRoute == "onboarding") return@LaunchedEffect

        if (currentRoute != route) {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
        // Mark as shortcut-launched if the intent was a shortcut and target is not dashboard
        launchedFromShortcut = pendingIsShortcut && route != Screen.Dashboard.route && route != "onboarding"
        pendingIsShortcut = false
        pendingExternalRoute = null
    }

    // ── Shortcut exit confirmation ────────────────────────────────────────
    // When launched from a pinned/static shortcut, intercept back to show confirmation.
    val isToolRoute = currentRoute != null && currentRoute != Screen.Dashboard.route && currentRoute != "onboarding"
    val shouldConfirmExit = launchedFromShortcut && isToolRoute
    val toolOnBack: () -> Unit = {
        if (shouldConfirmExit) {
            showShortcutExitDialog = true
        } else {
            navController.popBackStack()
        }
    }

    BackHandler(enabled = shouldConfirmExit) {
        showShortcutExitDialog = true
    }

    if (showShortcutExitDialog) {
        AlertDialog(
            onDismissRequest = { showShortcutExitDialog = false },
            title = { Text(stringResource(R.string.st_Shortcut_Exit_Title)) },
            text = { Text(stringResource(R.string.st_Shortcut_Exit_Message)) },
            confirmButton = {
                TextButton(onClick = {
                    showShortcutExitDialog = false
                    launchedFromShortcut = false
                    navController.popBackStack()
                }) { Text(stringResource(R.string.st_Shortcut_Exit_Confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showShortcutExitDialog = false }) {
                    Text(stringResource(R.string.st_Shortcut_Exit_Cancel))
                }
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = if (onboardingCompleted) Screen.Dashboard.route else "onboarding",
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(350),
                initialOffsetX = { it }
            )
        },
        exitTransition = {
            slideOutHorizontally(
                animationSpec = tween(350),
                targetOffsetX = { -it }
            )
        },
        popEnterTransition = {
            slideInHorizontally(
                animationSpec = tween(350),
                initialOffsetX = { -it }
            ) + scaleIn(initialScale = 1f)
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(350),
                targetOffsetX = { it }
            ) + scaleOut(targetScale = 1f)
        }
    ) {
        composable("onboarding") {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigate = { route ->
                    navController.navigate(route)
                },
                settingsRepository = settingsRepository,
                // Shared activity-level instance — the PdfReader destination
                // renders from this same VM, so dashboard attachment opens
                // must go through it (a back-stack-scoped default would
                // open the doc on a detached instance and show the library).
                pdfViewModel = pdfViewModel
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = hiltViewModel(),
                onBack = { toolOnBack() },
                onNavigateToUpdate = { navController.navigate(Screen.Update.route) },
                onNavigateToNews = { navController.navigate(Screen.ToolzNews.route) },
                onNavigateToBackupRestore = { navController.navigate(Screen.BackupRestore.route) },
                onNavigateToToolShortcuts = { navController.navigate(Screen.ToolShortcuts.route) },
                onNavigateToAppSkin = { navController.navigate(Screen.AppSkin.route) },
                onNavigateToDeveloperMode = { navController.navigate(Screen.DeveloperMode.route) },
                onResetOnboarding = {
                    navController.navigate("onboarding") {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.DeveloperMode.route) {
            com.frerox.toolz.ui.screens.developer.DeveloperModeScreen(
                onBack = { toolOnBack() },
                onOpenRoute = { route ->
                    try { navController.navigate(route) } catch (_: Exception) { }
                },
            )
        }

        composable(Screen.AppSkin.route) {
            com.frerox.toolz.ui.screens.settings.AppSkinScreen(
                viewModel = hiltViewModel(),
                onBack = { toolOnBack() },
                onOpenCatalog = { navController.navigate(Screen.IconCatalog.route) },
                onOpenSkins = { navController.navigate(Screen.SkinsCatalog.route) },
            )
        }

        composable(Screen.SkinsCatalog.route) {
            com.frerox.toolz.ui.screens.settings.SkinsCatalogScreen(
                viewModel = hiltViewModel(),
                onBack = { toolOnBack() },
            )
        }

        composable(Screen.IconCatalog.route) {
            com.frerox.toolz.ui.screens.settings.IconCatalogScreen(
                viewModel = hiltViewModel(),
                onBack = { toolOnBack() },
            )
        }

        composable(
            route = Screen.BackupRestore.route,
            arguments = listOf(navArgument("initialUri") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val initialUri = backStackEntry.arguments?.getString("initialUri")
            BackupRestoreScreen(
                viewModel = hiltViewModel<BackupRestoreViewModel>(),
                initialRestoreUri = initialUri,
                onBack = { toolOnBack() }
            )
        }

        composable(Screen.Update.route) {
            val context = LocalContext.current
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            UpdateScreen(
                onBack = { toolOnBack() },
                currentVersionName = packageInfo.versionName ?: "1.0.0",
                currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
            )
        }

        composable(Screen.ToolzNews.route) {
            com.frerox.toolz.ui.screens.news.ToolzNewsScreen(
                onBack = { toolOnBack() },
                highlightNewsId = pendingNewsId
            )
        }

        composable(
            route = Screen.AiAssistant.route,
            arguments = listOf(
                navArgument("chatId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("isCoachMode") { type = NavType.BoolType; defaultValue = false }
            )
        ) {
            AiAssistantScreen(
                onNavigateToBrowser = { url -> navController.navigate(Screen.Browser.createRoute(url)) },
                onNavigateToSettings = { navController.navigate(Screen.AiSettings.route) },
                onBack = { toolOnBack() }
            )
        }

        composable(Screen.AiSettings.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                runCatching { navController.getBackStackEntry(Screen.AiAssistant.route) }.getOrNull()
            }
            val aiViewModel: AiAssistantViewModel = if (parentEntry != null) {
                hiltViewModel(parentEntry)
            } else {
                hiltViewModel()
            }
            AiSettingsScreen(
                viewModel = aiViewModel,
                onNavigateToBrowser = { url -> navController.navigate(Screen.Browser.createRoute(url)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Timer.route) {
            TimerScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.Stopwatch.route) {
            StopwatchScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }

        composable(
            route = Screen.Search.route,
            arguments = listOf(navArgument("query") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val rawQuery = backStackEntry.arguments?.getString("query")
            val initialQuery = rawQuery?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            SearchScreen(
                initialQuery = initialQuery,
                onBackClick = { toolOnBack() },
                onResultClick = { url ->
                    if (url == Screen.AdBlockConfig.route) {
                        navController.navigate(Screen.AdBlockConfig.route)
                    } else if (url == Screen.SitePermissions.route) {
                        navController.navigate(Screen.SitePermissions.route)
                    } else {
                        navController.navigate(Screen.Browser.createRoute(url))
                    }
                },
                onManageTabs = { navController.navigate(Screen.TabManagement.route) }
            )
        }

        composable(Screen.AdBlockConfig.route) {
            com.frerox.toolz.ui.screens.search.AdBlockSettingsScreen(
                onBack = { toolOnBack() },
                onNavigateToNextDnsSetup = { url ->
                    navController.navigate(Screen.NextDnsSetup.createRoute(url))
                }
            )
        }

        composable(
            route = Screen.NextDnsSetup.route,
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val decodedUrl = java.net.URLDecoder.decode(url, "UTF-8")
            com.frerox.toolz.ui.screens.search.NextDnsSetupScreen(
                url = decodedUrl,
                onBack = { toolOnBack() }
            )
        }

        composable(Screen.TabManagement.route) {
            TabManagementScreen(
                onBack = { toolOnBack() },
                onTabClick = { _, url ->
                    // TabManagement can be reached from the browser renderer OR from the
                    // search tool. Popping back only works in the first case — from the
                    // search screen it would return the user to search with the tab
                    // switched invisibly underneath (the "sometimes can't open tabs" bug).
                    // Always route through the browser destination: navigating to an
                    // equivalent Browser entry pops the chooser off the stack instead.
                    val target = Screen.Browser.createRoute(url)
                    if (!navController.popBackStack(route = target, inclusive = false)) {
                        navController.navigate(target) {
                            popUpTo(Screen.TabManagement.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        composable(
            route = Screen.Browser.route,
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val decodedUrl = java.net.URLDecoder.decode(url, "UTF-8")
            val musicViewModel: MusicPlayerViewModel = hiltViewModel()
            WebViewScreen(
                url = decodedUrl,
                onBack = { toolOnBack() },
                onManageTabs = { navController.navigate(Screen.TabManagement.route) },
                onNavigateToSearch = { query ->
                    navController.navigate(Screen.Search.createRoute(query))
                },
                onNavigateToPdf = { uri, title ->
                    pdfViewModel.openPdf(Uri.parse(uri), title)
                    navController.navigate(Screen.PdfReader.route)
                },
                onNavigateToMusic = { tab ->
                    navController.navigate(Screen.MusicPlayer.createRoute(tab))
                }
            )
        }

        composable(Screen.SitePermissions.route) {
            com.frerox.toolz.ui.screens.browser.SitePermissionsScreen(
                onBack = { toolOnBack() }
            )
        }
        composable(Screen.WorldClock.route) {
            WorldClockScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.Pomodoro.route) {
            PomodoroScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.FocusFlow.route) {
            FocusFlowScreen(onNavigateBack = { toolOnBack() })
        }
        composable(Screen.Caffeinate.route) {
            CaffeinateScreen(onNavigateBack = { toolOnBack() })
        }
        composable(Screen.Todo.route) {
            TodoScreen(viewModel = hiltViewModel(), onNavigateBack = { toolOnBack() })
        }
        composable(Screen.Calendar.route) {
            CalendarScreen(viewModel = hiltViewModel())
        }

        composable(
            route = Screen.MusicPlayer.route,
            arguments = listOf(
                navArgument("tab") { defaultValue = 0; type = NavType.IntType },
                navArgument("initialUri") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
            val initialUri = backStackEntry.arguments?.getString("initialUri")
            MusicPlayerScreen(
                viewModel = hiltViewModel(),
                initialTab = initialTab,
                initialUri = initialUri,
                onBack = { toolOnBack() }
            )
        }
        composable(
            route = Screen.MediaDownloader.route,
            arguments = listOf(navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val rawUrl = backStackEntry.arguments?.getString("url")
            val initialUrl = rawUrl?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            com.frerox.toolz.ui.screens.media.downloader.MediaDownloaderScreen(
                onBack = { toolOnBack() },
                initialUrl = initialUrl,
                onNavigateToSettings = { navController.navigate(Screen.MediaDownloaderSettings.route) },
            )
        }
        composable(route = Screen.MediaDownloaderSettings.route) {
            com.frerox.toolz.ui.screens.media.downloader.MediaDownloaderSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Screen.FileConverter.route,
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("initialUris") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")
            val title = backStackEntry.arguments?.getString("title") ?: "Document"
            val initialUris = backStackEntry.arguments?.getString("initialUris")
            val uri = uriString?.let { Uri.parse(it) }

            FileConverterScreen(
                viewModel = hiltViewModel(),
                onBack = { toolOnBack() },
                initialUri = uri,
                initialTitle = title,
                initialUris = initialUris
            )
        }
        composable(
            route = Screen.BackgroundRemover.route,
            arguments = listOf(navArgument("initialUri") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val initialUri = backStackEntry.arguments?.getString("initialUri")
            BackgroundRemoverScreen(
                initialUri = initialUri,
                onNavigateBack = { toolOnBack() }
            )
        }
        composable(Screen.Flashlight.route) {
            FlashlightScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.ScreenLight.route) {
            ScreenLightScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.Magnifier.route) {
            val vm: MagnifierViewModel = hiltViewModel()
            MagnifierScreen(onBack = { toolOnBack() }, settingsRepository = vm.repository)
        }
        composable(
            route = Screen.Scanner.route,
            arguments = listOf(navArgument("initialImageUri") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val initialUri = backStackEntry.arguments?.getString("initialImageUri")
            ScannerScreen(
                initialImageUri = initialUri,
                onBack = { toolOnBack() },
                onNavigateToGenerator = {
                    navController.navigate(Screen.QrGenerator.route) {
                        popUpTo(Screen.Scanner.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.QrGenerator.route) {
            val qrVm: QRViewModel = hiltViewModel()
            QRGeneratorScreen(
                viewModel = qrVm,
                onBack = { toolOnBack() },
                onNavigateToScanner = {
                    navController.navigate(Screen.Scanner.route) {
                        popUpTo(Screen.QrGenerator.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.LightMeter.route) {
            LightMeterScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }

        composable(Screen.Compass.route) {
            CompassScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.BubbleLevel.route) {
            BubbleLevelScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.Speedometer.route) {
            SpeedometerScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.Altimeter.route) {
            AltimeterScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.StepCounter.route) {
            StepCounterScreen(
                viewModel = hiltViewModel(), 
                onBack = { toolOnBack() },
                onNavigateToTrends = { navController.navigate(Screen.StepTrends.route) },
                onNavigateToAiAssistant = { chatId ->
                    navController.navigate(Screen.AiAssistant.createRoute(chatId, isCoachMode = true))
                }
            )
        }
        composable(Screen.StepTrends.route) {
            StepTrendsScreen(
                viewModel = hiltViewModel(),
                onBack = { toolOnBack() }
            )
        }
        composable(Screen.VoiceRecorder.route) {
            VoiceRecorderScreen(onBack = { toolOnBack() })
        }

        composable(Screen.Calculator.route) {
            CalculatorScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.UnitConverter.route) {
            UnitConverterScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.TipCalculator.route) {
            TipCalculatorScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.BmiCalculator.route) {
            BmiScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.EquationSolver.route) {
            EquationSolverScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }

        composable(Screen.Ruler.route) {
            RulerScreen(onBack = { toolOnBack() })
        }
        composable(
            route = Screen.SmartEncrypter.route,
            arguments = listOf(
                navArgument("initialUri") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("mode") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val initialUri = backStackEntry.arguments?.getString("initialUri")
            val mode = backStackEntry.arguments?.getString("mode")
            SmartEncrypterScreen(
                initialUri = initialUri,
                mode = mode,
                onBack = { toolOnBack() }
            )
        }
        composable(Screen.SoundMeter.route) {
            SoundMeterScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.ColorPicker.route) {
            ColorPickerScreen(onBack = { toolOnBack() })
        }
        composable(Screen.PasswordGenerator.route) {
            RandomGeneratorScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.PasswordVault.route) {
            PasswordVaultScreen(onBackClick = { toolOnBack() })
        }
        composable(
            route = Screen.Notepad.route + "?initialNoteId={initialNoteId}",
            arguments = listOf(navArgument("initialNoteId") { type = NavType.IntType; defaultValue = -1 })
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getInt("initialNoteId") ?: -1
            val initialNoteId = rawId.takeIf { it > 0 }
            val musicViewModel: MusicPlayerViewModel = hiltViewModel()
            NotepadScreen(
                viewModel = hiltViewModel(),
                onBack = { toolOnBack() },
                onOpenAiAssistant = {
                    navController.navigate(Screen.AiAssistant.createRoute()) {
                        launchSingleTop = true
                    }
                },
                onPlayAudio = { uri ->
                    // Inline playback: toggle from here, never navigate away from Notes.
                    // Note attachments loop the single track (repeat-one), not the queue.
                    val state = musicViewModel.uiState.value
                    val current = state.currentTrack
                    if (current != null && isCurrentNoteTrack(current, uri)) {
                        musicViewModel.togglePlayPause()
                    } else {
                        val track = state.tracks.find { it.uri == uri }
                        if (track != null) {
                            musicViewModel.playTrack(track)
                        } else {
                            // Fallback for generic audio / voice memo URIs not in the library.
                            try {
                                musicViewModel.playUri(Uri.parse(uri))
                            } catch (_: Exception) { }
                        }
                        musicViewModel.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_ONE)
                    }
                },
                onViewPdf = { uri, page ->
                    val parsed = try { Uri.parse(uri) } catch (_: Exception) { null }
                    if (parsed != null) {
                        val title = try {
                            parsed.lastPathSegment?.substringAfterLast('/')?.let {
                                java.net.URLDecoder.decode(it, "UTF-8")
                            }?.take(80)
                        } catch (_: Exception) { null } ?: "Document"
                        pdfViewModel.openPdfAtPage(parsed, title, page.coerceAtLeast(0))
                        navController.navigate(Screen.PdfReader.route) {
                            launchSingleTop = true
                        }
                    }
                },
                initialNoteId = initialNoteId
            )
        }
        composable(Screen.BatteryInfo.route) {
            BatteryInfoScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        composable(Screen.DeviceInfo.route) {
            DeviceInfoScreen(onBack = { toolOnBack() })
        }
        composable(Screen.FlipCoin.route) {
            FlipCoinScreen(
                onBack = { toolOnBack() },
                settingsRepository = settingsRepository
            )
        }
        composable(Screen.LivingWidgets.route) {
            com.frerox.toolz.ui.screens.livingframes.LivingWidgetsScreen(
                onBack = { toolOnBack() }
            )
        }
        // Legacy route alias (1.1.7 pinned widgets carry "living_frames").
        composable(Screen.LivingFrames.route) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.navigate(Screen.LivingWidgets.route) {
                    popUpTo(Screen.LivingFrames.route) { inclusive = true }
                }
            }
        }
        composable(Screen.PeriodicTable.route) {
            PeriodicTableScreen(onBack = { toolOnBack() })
        }
        composable(Screen.PdfReader.route) {
            ToolzPdfScreen(
                viewModel = pdfViewModel,
                onNavigateBack = { toolOnBack() },
                onNavigateToNote = { noteId ->
                    if (noteId > 0) {
                        navController.navigate(Screen.Notepad.route + "?initialNoteId=$noteId") {
                            launchSingleTop = true
                        }
                    }
                },
                onNavigateToConverter = { uri, title ->
                    navController.navigate(Screen.FileConverter.createRoute(uri, title))
                }
            )
        }
        composable(Screen.NotificationVault.route) {
            NotificationVaultScreen(onNavigateBack = { toolOnBack() })
        }
        composable(Screen.Clipboard.route) {
            ClipboardScreen(viewModel = hiltViewModel(), onBack = { toolOnBack() })
        }
        // P3 single entry: both legacy routes map to NetworkSuiteScreen (PowerSuite retired)
        composable(Screen.NetworkPowerSuite.route) {
            NetworkSuiteScreen(onBack = { toolOnBack() })
        }
        composable(Screen.WifiTweaks.route) {
            NetworkSuiteScreen(onBack = { toolOnBack() })
        }
        composable(Screen.FileCleaner.route) {
            val musicViewModel: MusicPlayerViewModel = hiltViewModel()
            CleanerScreen(
                onBack = { toolOnBack() },
                onNavigateToPdf = { uri, title ->
                    pdfViewModel.openPdf(uri, title)
                    navController.navigate(Screen.PdfReader.route)
                },
                onNavigateToMusic = { uri ->
                    val track = musicViewModel.uiState.value.tracks.find { it.uri == uri.toString() }
                    track?.let { 
                        musicViewModel.playTrack(it)
                        navController.navigate(Screen.MusicPlayer.route)
                    } ?: run {
                        // Fallback: if track not found in indexed list, try to play directly
                        musicViewModel.playUri(uri)
                        navController.navigate(Screen.MusicPlayer.route)
                    }
                }
            )
        }
        composable(Screen.WhisperOnboarding.route) {
            WhisperOnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Whisper.route) {
                        popUpTo(Screen.WhisperOnboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.WhisperAuth.route) {
            val whisperViewModel: WhisperViewModel = hiltViewModel()
            val onboardingShown by whisperViewModel.onboardingShown.collectAsStateWithLifecycle()
            
            LaunchedEffect(onboardingShown) {
                if (onboardingShown == false) {
                    navController.navigate(Screen.WhisperOnboarding.route)
                }
            }

            if (onboardingShown == true) {
                WhisperAuthScreen(
                    onAuthenticated = {
                        navController.navigate(Screen.Whisper.route) {
                            popUpTo(Screen.WhisperAuth.route) { inclusive = true }
                        }
                    },
                    onNavigateBack = { toolOnBack() }
                )
            }
        }
        composable(Screen.Whisper.route) {
            val whisperViewModel: WhisperViewModel = hiltViewModel()
            val onboardingShown by whisperViewModel.onboardingShown.collectAsStateWithLifecycle()

            LaunchedEffect(onboardingShown) {
                if (onboardingShown == false) {
                    navController.navigate(Screen.WhisperOnboarding.route)
                }
            }

            if (onboardingShown == true) {
                WhisperMainScreen(
                    onNavigateToChat = { otherUserId ->
                        navController.navigate(Screen.WhisperChat.createRoute(otherUserId))
                    },
                    onNavigateToProfile = { userId ->
                        navController.navigate(Screen.WhisperUserProfile.createRoute(userId))
                    },
                    // Hidden in this release: the callbacks stay wired so the
                    // screens can be re-enabled from the compile flag alone,
                    // but they can never be reached from the visible UI.
                    onNavigateToGroup = { groupId ->
                        if (com.frerox.toolz.data.whisper.groupsEnabled()) {
                            navController.navigate(Screen.WhisperGroupChat.createRoute(groupId))
                        }
                    },
                    onNavigateToDailyUps = { ownerId ->
                        if (com.frerox.toolz.data.whisper.WhisperDailyUpsConfig.ENABLED) {
                            navController.navigate(Screen.WhisperDailyUps.createRoute(ownerId))
                        }
                    },
                    onNavigateToDailyUpsCompose = {
                        if (com.frerox.toolz.data.whisper.WhisperDailyUpsConfig.ENABLED) {
                            navController.navigate(Screen.WhisperDailyUpsCompose.route)
                        }
                    },
                    onLoggedOut = {
                        navController.navigate(Screen.WhisperAuth.route) {
                            popUpTo(Screen.Whisper.route) { inclusive = true }
                        }
                    },
                    viewModel = whisperViewModel
                )
            }
        }
        composable(Screen.WhisperChat.route) {
            WhisperChatScreen(
                onNavigateBack = { toolOnBack() },
                onNavigateToProfile = { userId ->
                    navController.navigate(Screen.WhisperUserProfile.createRoute(userId))
                }
            )
        }
        composable(Screen.WhisperGroupChat.route) {
            WhisperGroupChatScreen(
                onNavigateBack = { toolOnBack() },
                onNavigateToInfo = { groupId ->
                    navController.navigate(Screen.WhisperGroupInfo.createRoute(groupId))
                }
            )
        }
        composable(Screen.WhisperGroupInfo.route) {
            WhisperGroupInfoScreen(
                onNavigateBack = { toolOnBack() },
                onNavigateToPermissions = { groupId ->
                    navController.navigate(Screen.WhisperGroupPermissions.createRoute(groupId))
                },
            )
        }
        composable(Screen.WhisperGroupPermissions.route) {
            com.frerox.toolz.ui.screens.whisper.WhisperGroupPermissionsScreen(
                onNavigateBack = { toolOnBack() },
            )
        }
        composable(
            route = Screen.WhisperDailyUps.route,
            arguments = listOf(
                navArgument("ownerId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            com.frerox.toolz.ui.screens.whisper.WhisperDailyUpsScreen(
                onNavigateBack = { toolOnBack() },
                onNavigateToCompose = {
                    navController.navigate(Screen.WhisperDailyUpsCompose.route)
                },
                ownerArg = backStackEntry.arguments?.getString("ownerId"),
            )
        }
        composable(Screen.WhisperDailyUpsCompose.route) {
            com.frerox.toolz.ui.screens.whisper.WhisperDailyUpsComposeScreen(
                onNavigateBack = { toolOnBack() },
                onPublished = {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.st_Whisper_DailyUp_Posted),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    navController.navigate(Screen.WhisperDailyUps.createRoute()) {
                        popUpTo(Screen.WhisperDailyUpsCompose.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Screen.WhisperUserProfile.route) {
            WhisperUserProfileScreen(
                onNavigateBack = { toolOnBack() },
                onNavigateToChat = { otherUserId ->
                    navController.navigate(Screen.WhisperChat.createRoute(otherUserId))
                }
            )
        }
        composable(Screen.PurgeShot.route) {
            com.frerox.toolz.ui.screens.purgeshot.PurgeShotScreen(onBack = { toolOnBack() })
        }
        composable(Screen.ToolShortcuts.route) {
            com.frerox.toolz.ui.screens.shortcuts.ToolShortcutsScreen(onBack = { toolOnBack() })
        }
    }
}
private fun resolveExternalNavigationRoute(intent: Intent): String? {
    val componentName = intent.component?.className
    val action = intent.action
    val data = intent.data

    // Handle Activity Aliases
    when {
        componentName?.endsWith(".MusicPlayer") == true -> {
            val uri = getUriFromIntent(intent)
            return Screen.MusicPlayer.createRoute(0, uri?.toString())
        }
        componentName?.endsWith(".PdfViewer") == true -> {
            val uri = getUriFromIntent(intent)
            return if (uri != null) Screen.PdfReader.route else null
        }
        componentName?.endsWith(".FileConverter") == true -> {
            val uris = getUrisFromIntent(intent)
            return Screen.FileConverter.createRoute(initialUris = uris.map { it.toString() })
        }
        componentName?.endsWith(".BackgroundRemover") == true -> {
            val uri = getUriFromIntent(intent)
            return Screen.BackgroundRemover.createRoute(uri?.toString())
        }
        componentName?.endsWith(".QrScanner") == true -> {
            val uri = getUriFromIntent(intent)
            return Screen.Scanner.createRoute(uri?.toString())
        }
        componentName?.endsWith(".BackupRestore") == true -> {
            val uri = getUriFromIntent(intent)
            return Screen.BackupRestore.createRoute(uri?.toString())
        }
        componentName?.endsWith(".Encrypter") == true -> {
            val uri = getUriFromIntent(intent)
            return Screen.SmartEncrypter.createRoute(uri?.toString(), mode = "encrypt")
        }
        componentName?.endsWith(".Decrypter") == true -> {
            val uri = getUriFromIntent(intent)
            return Screen.SmartEncrypter.createRoute(uri?.toString(), mode = "decrypt")
        }
    }

    if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
        val uri = intent.data!!
        if (intent.type == "application/pdf" || uri.toString().lowercase().endsWith(".pdf")) {
            return Screen.PdfReader.route
        }
        if (uri.toString().lowercase().endsWith(".enc")) {
            return Screen.SmartEncrypter.createRoute(uri.toString(), mode = "decrypt")
        }
        if (uri.toString().lowercase().endsWith(".tzbk")) {
            return Screen.BackupRestore.createRoute(uri.toString())
        }
        if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            return Screen.Browser.createRoute(uri.toString())
        }
    }

    if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val rawShared = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).find(sharedText)?.value
        val sharedUrl = rawShared?.trim()?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')
        if (!sharedUrl.isNullOrBlank()) {
            val lower = sharedUrl.lowercase()
            // Shared social-media links open directly in the Media Downloader tool.
            // Instagram only when it's an actual reel/post/tv link — profiles go to Browser.
            val isYouTube = "youtube.com" in lower || "youtu.be" in lower
            val isTikTok = "tiktok.com" in lower
            val isInstagramMedia = "instagram.com" in lower && (
                "/reel" in lower || "/reels" in lower || "/p/" in lower || "/tv/" in lower || "/share/" in lower
                )
            if (isYouTube || isTikTok || isInstagramMedia) {
                return Screen.MediaDownloader.createRoute(sharedUrl)
            }
            return Screen.Browser.createRoute(sharedUrl)
        }
    }

    if (intent.getBooleanExtra(MainActivity.EXTRA_SHOW_UPDATE, false) ||
        intent.getBooleanExtra(MainActivity.EXTRA_SHOW_UPDATE_DIALOG, false)
    ) {
        return Screen.Update.route
    }

    val route = intent.getStringExtra(MainActivity.EXTRA_NAVIGATE_TO)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return null

    if (route == Screen.Notepad.route) {
        val noteId = intent.getIntExtra("note_id", -1)
        if (noteId != -1) {
            return "${Screen.Notepad.route}?initialNoteId=$noteId"
        }
    }

    return route
}

private fun getUriFromIntent(intent: Intent): Uri? {
    return if (intent.action == Intent.ACTION_SEND) {
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        intent.data
    }
}

private fun getUrisFromIntent(intent: Intent): List<Uri> {
    val uris = mutableListOf<Uri>()
    when (intent.action) {
        Intent.ACTION_SEND -> {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.add(it) }
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.addAll(it) }
        }
        Intent.ACTION_VIEW -> {
            intent.data?.let { uris.add(it) }
        }
    }
    return uris
}
