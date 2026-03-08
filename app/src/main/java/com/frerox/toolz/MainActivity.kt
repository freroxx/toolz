package com.frerox.toolz

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.frerox.toolz.service.StepCounterService
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.screens.dashboard.DashboardScreen
import com.frerox.toolz.ui.screens.math.*
import com.frerox.toolz.ui.screens.time.*
import com.frerox.toolz.ui.screens.utils.*
import com.frerox.toolz.ui.screens.light.*
import com.frerox.toolz.ui.screens.sensors.*
import com.frerox.toolz.ui.screens.notepad.*
import com.frerox.toolz.ui.screens.settings.*
import com.frerox.toolz.ui.theme.ToolzTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Start Step Counter Service
        val intent = Intent(this, StepCounterService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        setContent {
            ToolzTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ToolzNavHost()
                }
            }
        }
    }
}

@Composable
fun ToolzNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
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
        
        // Light & Optics
        composable(Screen.Flashlight.route) { 
            FlashlightScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() }) 
        }
        composable(Screen.ScreenLight.route) { 
            ScreenLightScreen(viewModel = hiltViewModel(), onBack = { navController.popBackStack() }) 
        }
        composable(Screen.Magnifier.route) { 
            MagnifierScreen(onBack = { navController.popBackStack() }) 
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
    }
}
