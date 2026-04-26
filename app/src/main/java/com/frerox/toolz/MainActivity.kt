package com.frerox.toolz

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
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
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.screens.LoadingScreen
import com.frerox.toolz.ui.screens.LoadingViewModel
import com.frerox.toolz.ui.screens.OnboardingScreen
import com.frerox.toolz.ui.screens.dashboard.DashboardScreen
import com.frerox.toolz.ui.screens.light.*
import com.frerox.toolz.ui.screens.math.*
import com.frerox.toolz.ui.screens.media.MusicPlayerScreen
import com.frerox.toolz.ui.screens.media.MusicPlayerViewModel
import com.frerox.toolz.ui.screens.media.FileConverterScreen
import com.frerox.toolz.ui.screens.notepad.NotepadScreen
import com.frerox.toolz.ui.screens.pdf.PdfViewModel
import com.frerox.toolz.ui.screens.pdf.ToolzPdfScreen
import com.frerox.toolz.ui.screens.sensors.*
import com.frerox.toolz.ui.screens.settings.SettingsScreen
import com.frerox.toolz.ui.screens.settings.UpdateScreen
import com.frerox.toolz.ui.screens.time.*
import com.frerox.toolz.ui.screens.todo.TodoScreen
import com.frerox.toolz.ui.screens.utils.*
import com.frerox.toolz.ui.screens.notifications.NotificationVaultScreen
import com.frerox.toolz.ui.screens.focus.FocusFlowScreen
import com.frerox.toolz.ui.screens.focus.CaffeinateScreen
import com.frerox.toolz.ui.screens.clipboard.ClipboardScreen
import com.frerox.toolz.ui.screens.ai.AiAssistantScreen
import com.frerox.toolz.ui.screens.calendar.CalendarScreen
import com.frerox.toolz.ui.screens.cleaner.CleanerScreen
import com.frerox.toolz.ui.screens.search.SearchScreen
import com.frerox.toolz.ui.screens.browser.WebViewScreen
import com.frerox.toolz.ui.screens.password.PasswordVaultScreen
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground
import com.frerox.toolz.service.StepCounterService
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.frerox.toolz.ui.components.OfflineTransitionOverlay
import com.frerox.toolz.util.OfflineManager
import com.frerox.toolz.util.OfflineState
import kotlinx.coroutines.delay
import com.frerox.toolz.util.VibrationManager
import com.frerox.toolz.worker.NotificationCleanupWorker
import com.frerox.toolz.worker.UpdateCheckWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val EXTRA_SHOW_UPDATE = "show_update"
        const val EXTRA_SHOW_UPDATE_DIALOG = "show_update_dialog"
    }
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var vibrationManager: VibrationManager

    @Inject
    lateinit var aiSettingsManager: AiSettingsManager

    @Inject
    lateinit var offlineManager: OfflineManager

    private val currentIntentState = mutableStateOf<Intent?>(null)
    private val currentIntentVersion = mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        currentIntentState.value = intent
        currentIntentVersion.value += 1

        scheduleCleanup()
        scheduleUpdateCheck()

        lifecycleScope.launch {
            aiSettingsManager.syncRemoteKeys()
        }

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = "SYSTEM")
            val dynamicColor by settingsRepository.dynamicColor.collectAsState(initial = true)
            val customPrimary by settingsRepository.customPrimaryColor.collectAsState(initial = null)
            val customSecondary by settingsRepository.customSecondaryColor.collectAsState(initial = null)
            val backgroundGradientEnabled by settingsRepository.backgroundGradientEnabled.collectAsState(initial = true)
            val performanceMode by settingsRepository.performanceMode.collectAsState(initial = false)
            val hapticEnabled by settingsRepository.hapticFeedback.collectAsState(initial = true)
            val hapticIntensity by settingsRepository.hapticIntensity.collectAsState(initial = 0.5f)

            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            val offlineState by offlineManager.offlineState.collectAsState(initial = OfflineState.ONLINE)

            var offlineOverlayVisible by remember { mutableStateOf(false) }
            var offlineOverlayReady by remember { mutableStateOf(false) }
            var lastOfflineState by remember { mutableStateOf(offlineState) }

            LaunchedEffect(offlineState) {
                if (offlineState != lastOfflineState) {
                    offlineOverlayReady = false
                    offlineOverlayVisible = true
                    delay(1200)
                    offlineOverlayReady = true
                    delay(1000)
                    offlineOverlayVisible = false
                    lastOfflineState = offlineState
                }
            }

            val blurAnim by animateFloatAsState(
                targetValue = if (offlineOverlayVisible) 30f else 0f,
                animationSpec = tween(600),
                label = "blur"
            )

            ToolzTheme(
                darkTheme = isDark,
                dynamicColor = dynamicColor,
                customPrimary = customPrimary?.let { Color(it) },
                customSecondary = customSecondary?.let { Color(it) },
                backgroundGradientEnabled = backgroundGradientEnabled,
                performanceMode = performanceMode,
                hapticEnabled = hapticEnabled,
                hapticIntensity = hapticIntensity,
                vibrationManager = vibrationManager
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .toolzBackground()
                            .graphicsLayer {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !performanceMode && blurAnim > 0f) {
                                    renderEffect = RenderEffect
                                        .createBlurEffect(blurAnim, blurAnim, Shader.TileMode.CLAMP)
                                        .asComposeRenderEffect()
                                }
                            }
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Transparent
                        ) {
                            val navController = rememberNavController()
                            val pdfViewModel: PdfViewModel = hiltViewModel()
                            val incomingIntent = currentIntentState.value
                            val incomingIntentVersion = currentIntentVersion.value

                            ToolzNavHost(
                                navController = navController,
                                settingsRepository = settingsRepository,
                                aiSettingsManager = aiSettingsManager,
                                pdfViewModel = pdfViewModel,
                                performanceMode = performanceMode,
                                backgroundGradientEnabled = backgroundGradientEnabled,
                                incomingIntent = incomingIntent,
                                incomingIntentVersion = incomingIntentVersion,
                            )

                            UpdateOverlay(settingsRepository)
                        }
                    }

                    OfflineTransitionOverlay(
                        state = offlineState,
                        visible = offlineOverlayVisible,
                        isReady = offlineOverlayReady,
                        performanceMode = performanceMode
                    )
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
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UpdateCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            updateCheckRequest
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
}

@Composable
fun UpdateOverlay(settingsRepository: SettingsRepository) {
    val availableVersion by settingsRepository.updateAvailableVersion.collectAsState(initial = null)
    val changelog by settingsRepository.updateChangelog.collectAsState(initial = null)
    val apkUrl by settingsRepository.updateApkUrl.collectAsState(initial = null)

    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(availableVersion) {
        if (availableVersion != null) {
            showDialog = true
        }
    }

    if (showDialog && availableVersion != null) {
        Dialog(
            onDismissRequest = { showDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                val vibrationManager = com.frerox.toolz.ui.theme.LocalVibrationManager.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Update,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(32.dp))

                    Text(
                        "ENGINE UPGRADE READY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 3.sp
                    )

                    Text(
                        "New Version Available",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        "v$availableVersion",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.height(40.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.History, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("CHANGELOG", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                changelog ?: "Performance optimizations and stability improvements.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(48.dp))

                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()

                    Button(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            apkUrl?.let { url ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            }
                            showDialog = false
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Rounded.Download, null)
                        Spacer(Modifier.width(12.dp))
                        Text("UPDATE NOW", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }

                    Spacer(Modifier.height(12.dp))

                    TextButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            showDialog = false
                            scope.launch { settingsRepository.setAvailableUpdate(null, null, null) }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("REMIND ME LATER", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
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
) {
    val onboardingCompleted by settingsRepository.onboardingCompleted.collectAsState(initial = true)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var pendingExternalRoute by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(incomingIntentVersion) {
        val latestIntent = incomingIntent ?: return@LaunchedEffect
        
        // Handle PDF View/Send Intent explicitly
        if (latestIntent.action == Intent.ACTION_VIEW || latestIntent.action == Intent.ACTION_SEND) {
            val uri = if (latestIntent.action == Intent.ACTION_SEND) {
                IntentCompat.getParcelableExtra(latestIntent, Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                latestIntent.data
            }

            if (uri != null) {
                val mimeType = context.contentResolver.getType(uri)
                val isPdf = latestIntent.type == "application/pdf" || 
                            mimeType == "application/pdf" ||
                            uri.toString().lowercase().endsWith(".pdf")
                
                if (isPdf) {
                    var title = "Document"
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
                    return@LaunchedEffect
                }
            }
        }
        
        pendingExternalRoute = resolveExternalNavigationRoute(latestIntent)
    }

    LaunchedEffect(pendingExternalRoute, onboardingCompleted, currentRoute) {
        val route = pendingExternalRoute ?: return@LaunchedEffect
        if (!onboardingCompleted) return@LaunchedEffect
        if (currentRoute == null || currentRoute == Screen.Loading.route || currentRoute == "onboarding") return@LaunchedEffect

        if (currentRoute != route) {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
        pendingExternalRoute = null
    }
    NavHost(
        navController = navController,
        startDestination = Screen.Loading.route,
        enterTransition = {
            if (performanceMode) fadeIn(animationSpec = tween(100))
            else fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            if (performanceMode) fadeOut(animationSpec = tween(100))
            else fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            if (performanceMode) fadeIn(animationSpec = tween(100))
            else fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            if (performanceMode) fadeOut(animationSpec = tween(100))
            else fadeOut(animationSpec = tween(300))
        }
    ) {
        composable(Screen.Loading.route) {
            val loadingViewModel: LoadingViewModel = hiltViewModel()
            LoadingScreen(
                viewModel = loadingViewModel,
                onLoadingComplete = {
                    val nextRoute = if (onboardingCompleted) Screen.Dashboard.route else "onboarding"
                    navController.navigate(nextRoute) {
                        popUpTo(Screen.Loading.route) { inclusive = true }
                    }
                }
            )
        }

        composable("onboarding") {
            OnboardingScreen(
                settingsRepository = settingsRepository,
                aiSettingsManager = aiSettingsManager,
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
                settingsRepository = settingsRepository
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() },
                onNavigateToUpdate = { navController.navigate(Screen.Update.route) },
                onResetOnboarding = {
                    navController.navigate("onboarding") {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Update.route) {
            val context = LocalContext.current
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            UpdateScreen(
                onBack = { navController.popBackStack() },
                currentVersionName = packageInfo.versionName ?: "1.0.0",
                currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
            )
        }

        composable(Screen.AiAssistant.route) {
            AiAssistantScreen(
                onNavigateToBrowser = { url -> navController.navigate(Screen.Browser.createRoute(url)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Timer.route) {
            TimerScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.Stopwatch.route) {
            StopwatchScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onBackClick = { navController.popBackStack() },
                onResultClick = { url ->
                    navController.navigate(Screen.Browser.createRoute(url))
                }
            )
        }

        composable(
            route = Screen.Browser.route,
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val decodedUrl = java.net.URLDecoder.decode(url, "UTF-8")
            WebViewScreen(
                url = decodedUrl,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.WorldClock.route) {
            WorldClockScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.Pomodoro.route) {
            PomodoroScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.FocusFlow.route) {
            FocusFlowScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Caffeinate.route) {
            CaffeinateScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Todo.route) {
            TodoScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.Calendar.route) {
            CalendarScreen(viewModel = hiltViewModel())
        }

        composable(
            route = Screen.MusicPlayer.route,
            arguments = listOf(navArgument("tab") { defaultValue = 0; type = NavType.IntType })
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
            MusicPlayerScreen(
                viewModel = hiltViewModel(),
                initialTab = initialTab,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "file_converter?uri={uri}&title={title}",
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri")
            val title = backStackEntry.arguments?.getString("title") ?: "Document"
            val uri = uriString?.let { Uri.parse(it) }
            
            FileConverterScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() },
                initialUri = uri,
                initialTitle = title
            )
        }
        composable(Screen.Flashlight.route) {
            FlashlightScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.ScreenLight.route) {
            ScreenLightScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.Magnifier.route) {
            val vm: MagnifierViewModel = hiltViewModel()
            MagnifierScreen(onBack = { navController.popBackStack() }, settingsRepository = vm.repository)
        }
        composable(Screen.Scanner.route) {
            ScannerScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.LightMeter.route) {
            LightMeterScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }

        composable(Screen.Compass.route) {
            CompassScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.BubbleLevel.route) {
            BubbleLevelScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.Speedometer.route) {
            SpeedometerScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.Altimeter.route) {
            AltimeterScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.StepCounter.route) {
            StepCounterScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.VoiceRecorder.route) {
            VoiceRecorderScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Calculator.route) {
            CalculatorScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.UnitConverter.route) {
            UnitConverterScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.TipCalculator.route) {
            TipCalculatorScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.BmiCalculator.route) {
            BmiScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.EquationSolver.route) {
            EquationSolverScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }

        composable(Screen.Ruler.route) {
            RulerScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.SoundMeter.route) {
            SoundMeterScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.ColorPicker.route) {
            ColorPickerScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.PasswordGenerator.route) {
            RandomGeneratorScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.PasswordVault.route) {
            PasswordVaultScreen(onBackClick = { navController.popBackStack() })
        }
        composable(
            route = Screen.Notepad.route + "?initialNoteId={initialNoteId}",
            arguments = listOf(navArgument("initialNoteId") { type = NavType.IntType; defaultValue = -1 })
        ) { backStackEntry ->
            val initialNoteId = backStackEntry.arguments?.getInt("initialNoteId").takeIf { it != -1 }
            val musicViewModel: MusicPlayerViewModel = hiltViewModel()
            NotepadScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() },
                onPlayAudio = { uri ->
                    val track = musicViewModel.uiState.value.tracks.find { it.uri == uri }
                    track?.let { musicViewModel.playTrack(it) }
                },
                onViewPdf = { uri ->
                    pdfViewModel.openPdf(Uri.parse(uri))
                    navController.navigate(Screen.PdfReader.route)
                },
                initialNoteId = initialNoteId
            )
        }
        composable(Screen.BatteryInfo.route) {
            BatteryInfoScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.DeviceInfo.route) {
            DeviceInfoScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.FlipCoin.route) {
            FlipCoinScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.PeriodicTable.route) {
            PeriodicTableScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.PdfReader.route) {
            ToolzPdfScreen(
                viewModel = pdfViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNote = { noteId ->
                    navController.navigate(Screen.Notepad.route + "?initialNoteId=$noteId")
                },
                onNavigateToConverter = { uri, title ->
                    navController.navigate(Screen.FileConverter.createRoute(uri, title))
                }
            )
        }
        composable(Screen.NotificationVault.route) {
            NotificationVaultScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Clipboard.route) {
            ClipboardScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.FileCleaner.route) {
            val musicViewModel: MusicPlayerViewModel = hiltViewModel()
            CleanerScreen(
                onBack = { navController.popBackStack() },
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
    }
}
private fun resolveExternalNavigationRoute(intent: Intent): String? {
    if (intent.action == Intent.ACTION_VIEW && intent.type == "application/pdf" && intent.data != null) {
        return Screen.PdfReader.route
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
