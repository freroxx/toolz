package com.frerox.toolz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.*
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.screens.LoadingScreen
import com.frerox.toolz.ui.screens.OnboardingScreen
import com.frerox.toolz.ui.screens.dashboard.DashboardScreen
import com.frerox.toolz.ui.screens.light.*
import com.frerox.toolz.ui.screens.math.*
import com.frerox.toolz.ui.screens.media.MusicPlayerScreen
import com.frerox.toolz.ui.screens.media.MusicPlayerViewModel
import com.frerox.toolz.ui.screens.notepad.NotepadScreen
import com.frerox.toolz.ui.screens.pdf.PdfViewModel
import com.frerox.toolz.ui.screens.pdf.ToolzPdfScreen
import com.frerox.toolz.ui.screens.sensors.*
import com.frerox.toolz.ui.screens.settings.SettingsScreen
import com.frerox.toolz.ui.screens.time.*
import com.frerox.toolz.ui.screens.utils.*
import com.frerox.toolz.ui.screens.notifications.NotificationVaultScreen
import com.frerox.toolz.ui.screens.focus.FocusFlowScreen
import com.frerox.toolz.ui.screens.clipboard.ClipboardScreen
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.service.StepCounterService
import com.frerox.toolz.worker.NotificationCleanupWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        scheduleCleanup()

        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = "SYSTEM")
            val dynamicColor by settingsRepository.dynamicColor.collectAsState(initial = true)
            val customPrimary by settingsRepository.customPrimaryColor.collectAsState(initial = null)
            val customSecondary by settingsRepository.customSecondaryColor.collectAsState(initial = null)

            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            ToolzTheme(
                darkTheme = isDark,
                dynamicColor = dynamicColor,
                customPrimary = customPrimary?.let { Color(it) },
                customSecondary = customSecondary?.let { Color(it) }
            ) {
                val secondary = MaterialTheme.colorScheme.secondary
                val surface = MaterialTheme.colorScheme.surface

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (!dynamicColor) {
                                Brush.verticalGradient(
                                    colors = if (isDark) {
                                        listOf(surface, MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), surface)
                                    } else {
                                        listOf(surface, MaterialTheme.colorScheme.primary.copy(alpha = 0.03f), surface)
                                    }
                                )
                            } else {
                                Brush.verticalGradient(listOf(surface, surface))
                            }
                        )
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        val navController = rememberNavController()
                        val pdfViewModel: PdfViewModel = hiltViewModel()

                        // Centralized Intent Handler
                        LaunchedEffect(intent) {
                            if (intent?.action == Intent.ACTION_VIEW && intent.type == "application/pdf") {
                                intent.data?.let { uri ->
                                    pdfViewModel.openPdf(uri)
                                    delay(150) // Ensure state is ready
                                    navController.navigate(Screen.PdfReader.route)
                                }
                            }
                        }

                        ToolzNavHost(navController, settingsRepository, pdfViewModel)
                    }
                }
            }
        }
        
        lifecycleScope.launch {
            settingsRepository.stepCounterEnabled.collect { enabled ->
                if (enabled) startStepService() else stopStepService()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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

    private fun startStepService() {
        val intent = Intent(this, StepCounterService::class.java)
        startForegroundService(intent)
    }

    private fun stopStepService() {
        val intent = Intent(this, StepCounterService::class.java)
        stopService(intent)
    }
}

@Composable
fun ToolzNavHost(
    navController: androidx.navigation.NavHostController,
    settingsRepository: SettingsRepository,
    pdfViewModel: PdfViewModel
) {
    val onboardingCompleted by settingsRepository.onboardingCompleted.collectAsState(initial = true)

    NavHost(
        navController = navController,
        startDestination = Screen.Loading.route,
        enterTransition = { fadeIn(animationSpec = tween(400)) + slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(400)) },
        exitTransition = { fadeOut(animationSpec = tween(400)) + slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(400)) },
        popEnterTransition = { fadeIn(animationSpec = tween(400)) + slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(400)) },
        popExitTransition = { fadeOut(animationSpec = tween(400)) + slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(400)) }
    ) {
        composable(Screen.Loading.route) {
            LoadingScreen(
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
                onResetOnboarding = {
                    navController.navigate("onboarding") {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        // Time & Productivity
        composable(Screen.Timer.route) {
            TimerScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
        composable(Screen.Stopwatch.route) {
            StopwatchScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
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

        // Media & Documents
        composable(Screen.MusicPlayer.route) {
            MusicPlayerScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
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

        // Sensors & Navigation
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

        // Math & Conversion
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

        // Utilities
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
                    pdfViewModel.openPdf(uri.toUri())
                    navController.navigate(Screen.PdfReader.route)
                },
                initialNoteId = initialNoteId
            )
        }
        composable(Screen.BatteryInfo.route) {
            BatteryInfoScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
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
                }
            )
        }
        composable(Screen.NotificationVault.route) {
            NotificationVaultScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.Clipboard.route) {
            ClipboardScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
        }
    }
}
