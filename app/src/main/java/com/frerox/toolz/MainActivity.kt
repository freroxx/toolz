package com.frerox.toolz

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.frerox.toolz.service.StepCounterService
import com.frerox.toolz.ui.MainViewModel
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.screens.LoadingScreen
import com.frerox.toolz.ui.screens.dashboard.DashboardScreen
import com.frerox.toolz.ui.screens.math.*
import com.frerox.toolz.ui.screens.time.*
import com.frerox.toolz.ui.screens.utils.*
import com.frerox.toolz.ui.screens.light.*
import com.frerox.toolz.ui.screens.sensors.*
import com.frerox.toolz.ui.screens.notepad.*
import com.frerox.toolz.ui.screens.settings.*
import com.frerox.toolz.ui.screens.media.*
import com.frerox.toolz.ui.theme.ToolzTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
                else -> isSystemInDarkTheme()
            }

            val customPrimary = customPrimaryInt?.let { Color(it) }
            val navController = rememberNavController()

            // Handle widget navigation
            LaunchedEffect(intent) {
                handleIntent(intent, navController)
            }

            // Handle service start after permission check
            val context = this
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions[Manifest.permission.ACTIVITY_RECOGNITION] == true) {
                    startStepService()
                }
            }

            LaunchedEffect(Unit) {
                val permissionsToRequest = mutableListOf<String>()
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
                    } else {
                        startStepService()
                    }
                } else {
                    startStepService()
                }

                if (permissionsToRequest.isNotEmpty()) {
                    permissionLauncher.launch(permissionsToRequest.toTypedArray())
                }
            }

            ToolzTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
                customPrimary = customPrimary
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ToolzNavHost(navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun handleIntent(intent: Intent?, navController: NavController) {
        val navigateTo = intent?.getStringExtra("navigate_to")
        if (navigateTo != null) {
            val route = when (navigateTo) {
                "notepad" -> Screen.Notepad.route
                "voice_recorder" -> Screen.VoiceRecorder.route
                "step_counter" -> Screen.StepCounter.route
                "compass" -> Screen.Compass.route
                "world_clock" -> Screen.WorldClock.route
                "music_player" -> Screen.MusicPlayer.route
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
}

@Composable
fun ToolzNavHost(navController: androidx.navigation.NavHostController) {
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
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Loading.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigate = { route ->
                    navController.navigate(route)
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() })
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
    }
}
