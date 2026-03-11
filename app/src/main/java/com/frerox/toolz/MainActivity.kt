package com.frerox.toolz

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.StepCounterService
import com.frerox.toolz.ui.MainViewModel
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.screens.LoadingScreen
import com.frerox.toolz.ui.screens.OnboardingScreen
import com.frerox.toolz.ui.screens.dashboard.DashboardScreen
import com.frerox.toolz.ui.screens.math.*
import com.frerox.toolz.ui.screens.time.*
import com.frerox.toolz.ui.screens.utils.*
import com.frerox.toolz.ui.screens.light.*
import com.frerox.toolz.ui.screens.sensors.*
import com.frerox.toolz.ui.screens.notepad.*
import com.frerox.toolz.ui.screens.settings.*
import com.frerox.toolz.ui.screens.media.*
import com.frerox.toolz.ui.screens.pdf.*
import com.frerox.toolz.ui.theme.ToolzTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val pdfViewModel: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val themeMode by mainViewModel.themeMode.collectAsState()
            val dynamicColor by mainViewModel.dynamicColor.collectAsState()
            val customPrimaryInt by mainViewModel.customPrimaryColor.collectAsState()

            val darkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            val customPrimary = customPrimaryInt?.let { Color(it) }
            val navController = rememberNavController()

            // Handle widget navigation
            LaunchedEffect(intent) {
                handleIntent(intent, navController)
            }

            // Handle service start after permission check and toggle
            val context = this
            val scope = rememberCoroutineScope()
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions[Manifest.permission.ACTIVITY_RECOGNITION] == true) {
                    scope.launch {
                        if (settingsRepository.stepCounterEnabled.first()) {
                            startStepService()
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                settingsRepository.stepCounterEnabled.collectLatest { enabled ->
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
                                startStepService()
                            } else {
                                permissionLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
                            }
                        } else {
                            startStepService()
                        }
                    } else {
                        stopStepService()
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                }
            }

            ToolzTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
                customPrimary = customPrimary
            ) {
                val isPipMode = remember { mutableStateOf(false) }

                // Track PiP mode changes
                DisposableEffect(Unit) {
                    val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
                        isPipMode.value = info.isInPictureInPictureMode
                    }
                    addOnPictureInPictureModeChangedListener(listener)
                    onDispose { removeOnPictureInPictureModeChangedListener(listener) }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isPipMode.value) {
                        PipPlayerLayout()
                    } else {
                        ToolzNavHost(navController, settingsRepository, pdfViewModel)
                    }
                }
            }
        }
    }

    @Composable
    fun PipPlayerLayout() {
        val musicViewModel: MusicPlayerViewModel = hiltViewModel(this)
        val state by musicViewModel.uiState.collectAsState()
        val track = state.currentTrack

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (track != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        AsyncImage(
                            model = track.thumbnailUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        track.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { musicViewModel.skipPrevious() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { musicViewModel.togglePlayPause() },
                            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { musicViewModel.skipNext() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            } else {
                Text("No Music", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runBlocking {
                val pipEnabled = settingsRepository.musicPipEnabled.first()
                if (pipEnabled) {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(1, 1))
                        .build()
                    enterPictureInPictureMode(params)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun handleIntent(intent: Intent?, navController: NavController) {
        // Deep Link from system
        intent?.data?.let { uri ->
            if (intent.type == "application/pdf" || uri.toString().endsWith(".pdf")) {
                pdfViewModel.openPdf(uri)
                navController.navigate(Screen.PdfReader.route) {
                    launchSingleTop = true
                }
                return
            }
        }

        val navigateTo = intent?.getStringExtra("navigate_to")
        if (navigateTo != null) {
            val route = when (navigateTo) {
                "notepad" -> Screen.Notepad.route
                "voice_recorder" -> Screen.VoiceRecorder.route
                "step_counter" -> Screen.StepCounter.route
                "compass" -> Screen.Compass.route
                "world_clock" -> Screen.WorldClock.route
                "music_player" -> Screen.MusicPlayer.route
                "pdf_reader" -> Screen.PdfReader.route
                else -> null
            }
            route?.let {
                navController.navigate(it) {
                    launchSingleTop = true
                }
            }
        }
    }

    private fun startStepService() {
        val intent = Intent(this, StepCounterService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
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
fun ToolzNavHost(
    navController: androidx.navigation.NavHostController,
    settingsRepository: SettingsRepository,
    pdfViewModel: PdfViewModel
) {
    val onboardingCompleted by settingsRepository.onboardingCompleted.collectAsState(initial = true)

    NavHost(
        navController = navController,
        startDestination = Screen.Loading.route,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(400)) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(400)) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(400)) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(400)) }
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

        // Media & Optics
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
        composable(Screen.Notepad.route) {
            NotepadScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
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
            ToolzPdfScreen(viewModel = pdfViewModel, onNavigateBack = { navController.popBackStack() })
        }
    }
}
