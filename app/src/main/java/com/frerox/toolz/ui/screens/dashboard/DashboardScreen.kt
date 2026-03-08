package com.frerox.toolz.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.navigation.Screen

data class ToolCategory(
    val title: String,
    val items: List<ToolItem>
)

data class ToolItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val description: String
)

val categories = listOf(
    ToolCategory(
        "Time & Productivity",
        listOf(
            ToolItem("Timer", Icons.Rounded.Timer, Screen.Timer.route, "Count down time"),
            ToolItem("Stopwatch", Icons.Rounded.History, Screen.Stopwatch.route, "Track elapsed time"),
            ToolItem("World Clock", Icons.Rounded.Public, Screen.WorldClock.route, "Global time zones"),
            ToolItem("Pomodoro", Icons.Rounded.AvTimer, Screen.Pomodoro.route, "Focus timer")
        )
    ),
    ToolCategory(
        "Light & Optics",
        listOf(
            ToolItem("Flashlight", Icons.Rounded.FlashlightOn, Screen.Flashlight.route, "Steady, SOS, Strobe"),
            ToolItem("Screen Light", Icons.Rounded.LightMode, Screen.ScreenLight.route, "Customizable light"),
            ToolItem("Magnifier", Icons.Rounded.ZoomIn, Screen.Magnifier.route, "Camera zoom"),
            ToolItem("Scanner", Icons.Rounded.QrCodeScanner, Screen.Scanner.route, "QR & Barcode")
        )
    ),
    ToolCategory(
        "Sensors & Navigation",
        listOf(
            ToolItem("Compass", Icons.Rounded.Explore, Screen.Compass.route, "Digital navigation"),
            ToolItem("Bubble Level", Icons.Rounded.Architecture, Screen.BubbleLevel.route, "Surface leveling"),
            ToolItem("Speedometer", Icons.Rounded.Speed, Screen.Speedometer.route, "GPS based speed"),
            ToolItem("Altimeter", Icons.Rounded.FilterHdr, Screen.Altimeter.route, "Altitude tracking"),
            ToolItem("Step Counter", Icons.Rounded.DirectionsRun, Screen.StepCounter.route, "Track your steps")
        )
    ),
    ToolCategory(
        "Math & Conversion",
        listOf(
            ToolItem("Calculator", Icons.Rounded.Calculate, Screen.Calculator.route, "Scientific math"),
            ToolItem("Unit Converter", Icons.Rounded.SyncAlt, Screen.UnitConverter.route, "Length, weight, etc"),
            ToolItem("Tip Calc", Icons.Rounded.ReceiptLong, Screen.TipCalculator.route, "Split bills"),
            ToolItem("BMI Calc", Icons.Rounded.MonitorWeight, Screen.BmiCalculator.route, "Health index")
        )
    ),
    ToolCategory(
        "Utilities",
        listOf(
            ToolItem("Ruler", Icons.Rounded.Straighten, Screen.Ruler.route, "On-screen measure"),
            ToolItem("Sound Meter", Icons.Rounded.GraphicEq, Screen.SoundMeter.route, "Decibel levels"),
            ToolItem("Color Picker", Icons.Rounded.Colorize, Screen.ColorPicker.route, "Camera hex picker"),
            ToolItem("Password Gen", Icons.Rounded.Password, Screen.PasswordGenerator.route, "Secure passwords"),
            ToolItem("Notepad", Icons.Rounded.NoteAlt, Screen.Notepad.route, "Simple notes")
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        "Toolz",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold
                    ) 
                },
                actions = {
                    IconButton(onClick = { onNavigate(Screen.Settings.route) }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            categories.forEach { category ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                items(category.items) { tool ->
                    ToolCard(tool = tool, onClick = { onNavigate(tool.route) })
                }
            }
        }
    }
}

@Composable
fun ToolCard(
    tool: ToolItem,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Column {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}
