package com.frerox.toolz.ui.screens.settings

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.compose.ui.tooling.preview.Preview
import com.frerox.toolz.BuildConfig
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToUpdate: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onResetOnboarding: () -> Unit
) {
    val vibrationManager = LocalVibrationManager.current
    val context = LocalContext.current

    val stepGoal by viewModel.stepGoal.collectAsState(initial = 10000)
    val themeMode by viewModel.themeMode.collectAsState(initial = "SYSTEM")
    val dynamicColor by viewModel.dynamicColor.collectAsState(initial = true)
    val customPrimaryInt by viewModel.customPrimaryColor.collectAsState(initial = null)
    val customSecondaryInt by viewModel.customSecondaryColor.collectAsState(initial = null)
    val backgroundGradientEnabled by viewModel.backgroundGradientEnabled.collectAsState(initial = true)
    val pdfAiOcrEnhance by viewModel.pdfAiOcrEnhance.collectAsState(initial = false)

    val dashboardView by viewModel.dashboardView.collectAsState(initial = "DEFAULT")

    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState(initial = true)
    val notificationVaultEnabled by viewModel.notificationVaultEnabled.collectAsState(initial = true)
    val stepNotifications by viewModel.stepNotifications.collectAsState(initial = true)
    val timerNotifications by viewModel.timerNotifications.collectAsState(initial = true)
    val musicNotifications by viewModel.musicNotifications.collectAsState(initial = true)
    val fileConversionNotifications by viewModel.fileConversionNotifications.collectAsState(initial = true)
    val appUpdateNotifications by viewModel.appUpdateNotifications.collectAsState(initial = true)
    val taskReminderNotifications by viewModel.taskReminderNotifications.collectAsState(initial = true)
    val eventReminderNotifications by viewModel.eventReminderNotifications.collectAsState(initial = true)
    val pomodoroNotifications by viewModel.pomodoroNotifications.collectAsState(initial = true)
    val flashlightNotificationsEnabled by viewModel.flashlightNotificationsEnabled.collectAsState(initial = false)

    val widgetBgColor by viewModel.widgetBackgroundColor.collectAsState(initial = 0xFFFFFFFF.toInt())
    val widgetOpacity by viewModel.widgetOpacity.collectAsState(initial = 0.9f)

    val hapticFeedback by viewModel.hapticFeedback.collectAsState(initial = true)
    val hapticIntensity by viewModel.hapticIntensity.collectAsState(initial = 0.5f)
    val unitSystem by viewModel.unitSystem.collectAsState(initial = "METRIC")
    val stepCounterEnabled by viewModel.stepCounterEnabled.collectAsState(initial = true)
    val showToolzPill by viewModel.showToolzPill.collectAsState(initial = true)
    val fillThePillEnabled by viewModel.fillThePillEnabled.collectAsState(initial = true)
    val pillTodoEnabled by viewModel.pillTodoEnabled.collectAsState(initial = true)
    val pillFocusEnabled by viewModel.pillFocusEnabled.collectAsState(initial = true)
    val showDashboardStats by viewModel.showDashboardStats.collectAsState(initial = false)

    val userName by viewModel.userName.collectAsState(initial = "")
    var userNameInput by remember { mutableStateOf("") }
    
    LaunchedEffect(userName) {
        if (userNameInput != userName) {
            userNameInput = userName
        }
    }

    LaunchedEffect(userNameInput) {
        if (userNameInput != userName) {
            delay(500)
            viewModel.setUserName(userNameInput)
        }
    }

    var showGraySuggestion by remember { mutableStateOf(false) }
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme
    }

    if (showGraySuggestion) {
        AlertDialog(
            onDismissRequest = { showGraySuggestion = false },
            title = { Text("Perfect Gray Suggestion", fontWeight = FontWeight.Black) },
            text = { Text("You've selected a dark primary color while in Dark Mode. Would you like to switch to a 'Perfect Gray' for a better visual experience?") },
            confirmButton = {
                Button(
                    onClick = {
                        vibrationManager?.vibrateSuccess()
                        viewModel.setCustomPrimaryColor(0xFF1A1C1E.toInt())
                        showGraySuggestion = false
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    @Suppress("DEPRECATION")
                    Text("ACCEPT CHANGES", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vibrationManager?.vibrateClick()
                    showGraySuggestion = false
                }) {
                    @Suppress("DEPRECATION")
                    Text("DISMISS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsState(initial = false)
    val offlineModeEnabled by viewModel.offlineModeEnabled.collectAsState(initial = false)

    val musicShakeToSkip by viewModel.musicShakeToSkip.collectAsState(initial = false)
    val musicShakeSensitivity by viewModel.musicShakeSensitivity.collectAsState(initial = 0.3f)
    val musicAudioFocus by viewModel.musicAudioFocus.collectAsState(initial = true)
    val musicAiEnabled by viewModel.musicAiEnabled.collectAsState(initial = true)
    val musicKeepScreenOnLyrics by viewModel.musicKeepScreenOnLyrics.collectAsState(initial = true)
    val karaokeEnabled by viewModel.karaokeEnabled.collectAsState(initial = true)

    val performanceMode by viewModel.performanceMode.collectAsState(initial = false)

    val converterCustomPath by viewModel.converterCustomOutputPath.collectAsState(initial = null)

    var showResetDialog by remember { mutableStateOf(false) }
    var showAdvancedThemeDialog by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState(initial = "")

    var expandedSection by remember { mutableStateOf<String?>(null) }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // Take persistable permission
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setConverterCustomOutputPath(it.toString())
        }
    }


    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("RESET ALL DATA?", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = { Text("This will reset your profile and all app settings to default.") },
            confirmButton = {
                Button(
                    onClick = {
                        vibrationManager?.vibrateLongClick()
                        viewModel.resetOnboarding()
                        showResetDialog = false
                        onResetOnboarding()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("RESET NOW", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vibrationManager?.vibrateClick()
                    showResetDialog = false
                }) {
                    Text("CANCEL", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(40.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showAdvancedThemeDialog) {
        AdvancedThemeDialog(
            currentPrimary = customPrimaryInt ?: Color(0xFF2962FF).toArgb(),
            currentSecondary = customSecondaryInt ?: Color(0xFF00BFA5).toArgb(),
            onDismiss = { showAdvancedThemeDialog = false },
            onSave = { primary, secondary ->
                viewModel.setCustomPrimaryColor(primary)
                viewModel.setCustomSecondaryColor(secondary)
                showAdvancedThemeDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Settings",
                subtitle = "Personalize Toolz",
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            showResetDialog = true
                        },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                largeFlexible = true,
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                SearchField(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fadingEdges(top = 16.dp, bottom = 16.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(Modifier.height(8.dp))

                    // 1. Section: MY ACCOUNT
                    StaggeredEntrance(index = 0) {
                        SettingsExpandableSection(
                            title = "MY ACCOUNT",
                            icon = Icons.Rounded.AccountCircle,
                            isExpanded = expandedSection == "ACCOUNT" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "ACCOUNT") null else "ACCOUNT" }
                        ) {
                            if (matches(searchQuery, "profile", "name", "explorer", "identity")) {
                                SettingsItem(
                                    title = "User Identity",
                                    subtitle = "Name: ${userName.ifBlank { "Explorer" }}",
                                    icon = Icons.Rounded.Badge
                                ) {
                                    OutlinedTextField(
                                        value = userNameInput,
                                        onValueChange = { userNameInput = it },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        placeholder = { Text("Enter your identity") },
                                        shape = RoundedCornerShape(20.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 2. Section: VISUALS & THEME
                    StaggeredEntrance(index = 1) {
                        SettingsExpandableSection(
                            title = "VISUALS & THEME",
                            icon = Icons.Rounded.Palette,
                            isExpanded = expandedSection == "VISUALS" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "VISUALS") null else "VISUALS" }
                        ) {
                            if (matches(searchQuery, "performance", "lag", "animations", "blur", "speed", "optimization")) {
                                SettingsToggleItem(
                                    title = "Performance Mode",
                                    subtitle = "Disable blur and high-frequency animations",
                                    icon = Icons.Rounded.Speed,
                                    checked = performanceMode,
                                    onCheckedChange = { viewModel.setPerformanceMode(it) }
                                )
                            }

                            if (matches(searchQuery, "dark", "light", "mode", "appearance", "theme")) {
                                SettingsItem(
                                    title = "Appearance Mode",
                                    subtitle = "Current: ${themeMode.lowercase()}",
                                    icon = Icons.Rounded.DarkMode
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("SYSTEM", "LIGHT", "DARK").forEach { mode ->
                                            val isSelected = themeMode == mode
                                            Surface(
                                                onClick = {
                                                    vibrationManager?.vibrateClick()
                                                    viewModel.setThemeMode(mode)
                                                },
                                                modifier = Modifier.weight(1f).height(48.dp).bouncyClick {},
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    @Suppress("DEPRECATION")
                                                    Text(mode, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (matches(searchQuery, "dynamic", "color", "material", "wallpaper", "advanced", "palette", "accent")) {
                                SettingsToggleItem(
                                    title = "Dynamic Colors",
                                    subtitle = "Adapt to device wallpaper (Android 12+)",
                                    icon = Icons.Rounded.ColorLens,
                                    checked = dynamicColor,
                                    onCheckedChange = { viewModel.setDynamicColor(it) }
                                )

                                if (!dynamicColor) {
                                    SettingsItem(
                                        title = "Custom Palette",
                                        subtitle = "Define your unique color scheme",
                                        icon = Icons.Rounded.FormatColorFill
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 12.dp)) {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                    @Suppress("DEPRECATION")
                                                    Text("PRIMARY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
                                                    IconButton(onClick = {
                                                        vibrationManager?.vibrateClick()
                                                        viewModel.setCustomPrimaryColor(null)
                                                    }, modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))) {
                                                        Icon(Icons.Rounded.RestartAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                Spacer(Modifier.height(8.dp))
                                                ColorPickerRow(
                                                    selectedColor = customPrimaryInt ?: Color(0xFF2962FF).toArgb(),
                                                    onColorSelected = {
                                                        vibrationManager?.vibrateClick()
                                                        val color = Color(it)
                                                        if (isDarkTheme && color.luminance() < 0.2f) {
                                                            showGraySuggestion = true
                                                        }
                                                        viewModel.setCustomPrimaryColor(it)
                                                    }
                                                )
                                            }

                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                    @Suppress("DEPRECATION")
                                                    Text("SECONDARY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.5.sp)
                                                    IconButton(onClick = {
                                                        vibrationManager?.vibrateClick()
                                                        viewModel.setCustomSecondaryColor(null)
                                                    }, modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))) {
                                                        Icon(Icons.Rounded.RestartAlt, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                Spacer(Modifier.height(8.dp))
                                                ColorPickerRow(
                                                    selectedColor = customSecondaryInt ?: Color(0xFF00BFA5).toArgb(),
                                                    onColorSelected = {
                                                        vibrationManager?.vibrateClick()
                                                        viewModel.setCustomSecondaryColor(it)
                                                    }
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    vibrationManager?.vibrateClick()
                                                    showAdvancedThemeDialog = true
                                                },
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                shape = RoundedCornerShape(16.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                    contentColor = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                Icon(Icons.Rounded.AutoFixHigh, null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                @Suppress("DEPRECATION")
                                                Text("ADVANCED THEME", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }

                            if (matches(searchQuery, "gradient", "background", "effect", "visual")) {
                                SettingsToggleItem(
                                    title = "Background Gradient",
                                    subtitle = "Subtle depth effect on main screens",
                                    icon = Icons.Rounded.Gradient,
                                    checked = backgroundGradientEnabled,
                                    onCheckedChange = { viewModel.setBackgroundGradientEnabled(it) }
                                )
                            }

                            if (matches(searchQuery, "recent", "home", "layout", "view", "dashboard", "style")) {
                                SettingsItem(
                                    title = "Home Layout",
                                    subtitle = "Current style: ${dashboardView.lowercase()}",
                                    icon = Icons.Rounded.Dashboard
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("DEFAULT", "LIST").forEach { viewStyle ->
                                            val isSelected = dashboardView == viewStyle
                                            Surface(
                                                onClick = {
                                                    vibrationManager?.vibrateClick()
                                                    viewModel.setDashboardView(viewStyle)
                                                },
                                                modifier = Modifier.weight(1f).height(48.dp).bouncyClick {},
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    @Suppress("DEPRECATION")
                                                    Text(viewStyle, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (matches(searchQuery, "widget", "opacity", "background", "styling", "home screen")) {
                                SettingsItem(
                                    title = "Widget Styling",
                                    subtitle = "Customize home screen widgets",
                                    icon = Icons.Rounded.SettingsSuggest
                                ) {
                                    Column(modifier = Modifier.padding(top = 12.dp)) {
                                        ColorPickerRow(
                                            selectedColor = widgetBgColor,
                                            onColorSelected = {
                                                vibrationManager?.vibrateClick()
                                                viewModel.setWidgetBackgroundColor(it)
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Opacity, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(16.dp))
                                            @Suppress("DEPRECATION")
                                            Slider(
                                                value = widgetOpacity,
                                                onValueChange = { viewModel.setWidgetOpacity(it) },
                                                valueRange = 0.1f..1f,
                                                modifier = Modifier.weight(1f),
                                                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                                            )
                                            Spacer(Modifier.width(16.dp))
                                            @Suppress("DEPRECATION")
                                            Text("${(widgetOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, modifier = Modifier.width(40.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Section: INTELLIGENCE & AI
                    StaggeredEntrance(index = 2) {
                        SettingsExpandableSection(
                            title = "INTELLIGENCE & AI",
                            icon = Icons.Rounded.AutoAwesome,
                            isExpanded = expandedSection == "INTELLIGENCE" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "INTELLIGENCE") null else "INTELLIGENCE" }
                        ) {
                            if (matches(searchQuery, "offline", "local", "internet", "network", "privacy")) {
                                SettingsToggleItem(
                                    title = "Offline Mode",
                                    subtitle = "Force local operation and hide AI features",
                                    icon = Icons.Rounded.CloudOff,
                                    checked = offlineModeEnabled,
                                    onCheckedChange = { viewModel.setOfflineModeEnabled(it) }
                                )
                            }
                            if (!offlineModeEnabled) {
                                if (matches(searchQuery, "ai", "clipboard", "monitoring", "summarize", "smart")) {
                                    val aiMonitoring by viewModel.aiClipboardMonitoringEnabled.collectAsState(initial = true)
                                    SettingsToggleItem(
                                        title = "AI Clipboard Monitoring",
                                        subtitle = "Smart classification and summaries",
                                        icon = Icons.Rounded.AutoAwesome,
                                        checked = aiMonitoring,
                                        onCheckedChange = { viewModel.setAiClipboardMonitoringEnabled(it) },
                                        enabled = !offlineModeEnabled
                                    )
                                }
                                if (matches(searchQuery, "pdf", "ocr", "enhance", "ai", "text", "scan")) {
                                    SettingsToggleItem(
                                        title = "AI OCR Enhance",
                                        subtitle = "Improve PDF text recognition with AI",
                                        icon = Icons.Rounded.DocumentScanner,
                                        checked = pdfAiOcrEnhance,
                                        onCheckedChange = { viewModel.setPdfAiOcrEnhance(it) },
                                        enabled = !offlineModeEnabled
                                    )
                                }
                                if (matches(searchQuery, "now playing", "lyrics", "meaning", "smart", "ai")) {
                                    SettingsToggleItem(
                                        title = "Now Playing AI",
                                        subtitle = "Smart lyrics meanings and recommendations",
                                        icon = Icons.Rounded.MusicNote,
                                        checked = musicAiEnabled,
                                        onCheckedChange = { viewModel.setMusicAiEnabled(it) },
                                        enabled = !offlineModeEnabled
                                    )
                                }
                            }
                        }
                    }

                    // 4. Section: INTERACTION & HUD
                    StaggeredEntrance(index = 3) {
                        SettingsExpandableSection(
                            title = "INTERACTION & HUD",
                            icon = Icons.Rounded.SmartButton,
                            isExpanded = expandedSection == "INTERACTION" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "INTERACTION") null else "INTERACTION" }
                        ) {
                            if (matches(searchQuery, "vpn", "dns", "live", "notification", "network")) {
                                val liveVpn by viewModel.liveVpnNotifications.collectAsState(initial = true)
                                val liveDns by viewModel.liveDnsNotifications.collectAsState(initial = true)
                                
                                SettingsToggleItem(
                                    title = "Live VPN Notifications",
                                    subtitle = "Show active VPN status in system tray",
                                    icon = Icons.Rounded.VpnLock,
                                    checked = liveVpn,
                                    onCheckedChange = { viewModel.setLiveVpnNotifications(it) }
                                )
                                SettingsToggleItem(
                                    title = "Live DNS Notifications",
                                    subtitle = "Alert on DNS latency changes",
                                    icon = Icons.Rounded.Dns,
                                    checked = liveDns,
                                    onCheckedChange = { viewModel.setLiveDnsNotifications(it) }
                                )
                            }

                            if (matches(searchQuery, "pill", "smart", "overlay", "todo", "focus", "fill", "hud")) {
                                SettingsToggleItem(
                                    title = "Smart Overlay (Pill)",
                                    subtitle = "Floating tool for quick access",
                                    icon = Icons.Rounded.SmartButton,
                                    checked = showToolzPill,
                                    onCheckedChange = { viewModel.setShowToolzPill(it) }
                                )

                                if (showToolzPill) {
                                    SettingsToggleItem(
                                        title = "Fill the Pill",
                                        subtitle = "Show tips when inactive",
                                        icon = Icons.Rounded.AutoAwesome,
                                        checked = fillThePillEnabled,
                                        onCheckedChange = { viewModel.setFillThePillEnabled(it) }
                                    )
                                    SettingsToggleItem(
                                        title = "Task Progress",
                                        subtitle = "Track active tasks on overlay",
                                        icon = Icons.Rounded.TaskAlt,
                                        checked = pillTodoEnabled,
                                        onCheckedChange = { viewModel.setPillTodoEnabled(it) }
                                    )
                                    SettingsToggleItem(
                                        title = "Focus Score",
                                        subtitle = "View productivity on overlay",
                                        icon = Icons.Rounded.Toll,
                                        checked = pillFocusEnabled,
                                        onCheckedChange = { viewModel.setPillFocusEnabled(it) }
                                    )
                                }
                            }

                            if (matches(searchQuery, "dashboard", "stats", "battery", "storage", "info")) {
                                SettingsToggleItem(
                                    title = "Show Dashboard Stats",
                                    subtitle = "Battery and storage info cards",
                                    icon = Icons.Rounded.BarChart,
                                    checked = showDashboardStats,
                                    onCheckedChange = { viewModel.setShowDashboardStats(it) }
                                )
                            }

                            if (matches(searchQuery, "vibration", "haptic", "intensity", "feedback", "tuner")) {
                                SettingsToggleItem(
                                    title = "Haptic Feedback",
                                    subtitle = "Tactile response on interaction",
                                    icon = Icons.Rounded.Vibration,
                                    checked = hapticFeedback,
                                    onCheckedChange = { viewModel.setHapticFeedback(it) }
                                )

                                if (hapticFeedback) {
                                    SettingsItem(
                                        title = "Haptic Tuner",
                                        subtitle = "Intensity: ${(hapticIntensity * 100).toInt()}%",
                                        icon = Icons.Rounded.Tune
                                    ) {
                                        Column(modifier = Modifier.padding(top = 12.dp)) {
                                            HapticTuner(
                                                intensity = hapticIntensity,
                                                onIntensityChange = { 
                                                    viewModel.setHapticIntensity(it)
                                                    vibrationManager?.vibrateClick()
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (matches(searchQuery, "unit", "system", "metric", "imperial", "measure")) {
                                SettingsItem(
                                    title = "Measurement Units",
                                    subtitle = "System: ${unitSystem.lowercase()}",
                                    icon = Icons.Rounded.SquareFoot
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("METRIC", "IMPERIAL").forEach { unit ->
                                            val isSelected = unitSystem == unit
                                            Surface(
                                                onClick = {
                                                    vibrationManager?.vibrateClick()
                                                    viewModel.setUnitSystem(unit)
                                                },
                                                modifier = Modifier.weight(1f).height(48.dp).bouncyClick {},
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    @Suppress("DEPRECATION")
                                                    Text(unit, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. Section: MEDIA & AUDIO
                    StaggeredEntrance(index = 4) {
                        SettingsExpandableSection(
                            title = "MEDIA & AUDIO",
                            icon = Icons.Rounded.MusicNote,
                            isExpanded = expandedSection == "MEDIA" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "MEDIA") null else "MEDIA" }
                        ) {
                            if (matches(searchQuery, "shake", "skip", "audio", "sensitivity", "gesture")) {
                                SettingsToggleItem(
                                    title = "Shake to Skip",
                                    subtitle = "Shake phone for next track",
                                    icon = Icons.Rounded.PhonelinkRing,
                                    checked = musicShakeToSkip,
                                    onCheckedChange = { viewModel.setMusicShakeToSkip(it) }
                                )
                                
                                if (musicShakeToSkip) {
                                    SettingsItem(
                                        title = "Shake Sensitivity",
                                        subtitle = "Intensity: ${(musicShakeSensitivity * 100).toInt()}%",
                                        icon = Icons.Rounded.GraphicEq
                                    ) {
                                        Slider(
                                            value = musicShakeSensitivity,
                                            onValueChange = { viewModel.setMusicShakeSensitivity(it) },
                                            valueRange = 0.1f..1f,
                                            modifier = Modifier.padding(top = 8.dp),
                                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }

                            if (matches(searchQuery, "lyrics", "screen", "sleep", "awake", "visibility")) {
                                SettingsToggleItem(
                                    title = "Lyrics Keep Awake",
                                    subtitle = "Prevent sleep in lyrics mode",
                                    icon = Icons.Rounded.Visibility,
                                    checked = musicKeepScreenOnLyrics,
                                    onCheckedChange = { viewModel.setMusicKeepScreenOnLyrics(it) }
                                )
                            }

                            if (matches(searchQuery, "audio", "focus", "pause", "smart")) {
                                SettingsToggleItem(
                                    title = "Smart Audio Focus",
                                    subtitle = "Auto-pause for other apps",
                                    icon = Icons.Rounded.Hearing,
                                    checked = musicAudioFocus,
                                    onCheckedChange = { viewModel.setMusicAudioFocus(it) }
                                )
                            }

                            if (matches(searchQuery, "karaoke", "mic", "sing", "audio")) {
                                SettingsToggleItem(
                                    title = "Karaoke Mode",
                                    subtitle = "Enable karaoke features and mic buttons",
                                    icon = Icons.Rounded.Mic,
                                    checked = karaokeEnabled,
                                    onCheckedChange = { viewModel.setKaraokeEnabled(it) }
                                )
                            }
                        }
                    }

                    // 6. Section: HEALTH & ACTIVITY
                    StaggeredEntrance(index = 5) {
                        SettingsExpandableSection(
                            title = "HEALTH & ACTIVITY",
                            icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                            isExpanded = expandedSection == "HEALTH" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "HEALTH") null else "HEALTH" }
                        ) {
                            if (matches(searchQuery, "step", "goal", "health", "tracker", "walking")) {
                                SettingsToggleItem(
                                    title = "Step Tracker",
                                    subtitle = "Monitor activity in background",
                                    icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                                    checked = stepCounterEnabled,
                                    onCheckedChange = { viewModel.setStepCounterEnabled(it) }
                                )

                                if (stepCounterEnabled) {
                                    SettingsItem(
                                        title = "Daily Step Goal",
                                        subtitle = "Target: $stepGoal steps",
                                        icon = Icons.Rounded.EmojiEvents
                                    ) {
                                        Slider(
                                            value = stepGoal.divideToFloat(),
                                            onValueChange = { viewModel.setStepGoal(it.toInt()) },
                                            valueRange = 1000f..30000f,
                                            steps = 29,
                                            modifier = Modifier.padding(top = 8.dp),
                                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 7. Section: NOTIFICATIONS
                    StaggeredEntrance(index = 6) {
                        SettingsExpandableSection(
                            title = "NOTIFICATIONS",
                            icon = Icons.Rounded.Notifications,
                            isExpanded = expandedSection == "NOTIFICATIONS" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "NOTIFICATIONS") null else "NOTIFICATIONS" }
                        ) {
                            if (matches(searchQuery, "notification", "master", "switch", "alerts")) {
                                SettingsToggleItem(
                                    title = "Master Switch",
                                    subtitle = "Enable all app alerts",
                                    icon = Icons.Rounded.NotificationsActive,
                                    checked = notificationsEnabled,
                                    onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                                )

                                if (notificationsEnabled) {
                                    if (matches(searchQuery, "vault", "history", "save")) {
                                        SettingsToggleItem(
                                            title = "Notification History",
                                            subtitle = "Save and view previous alerts",
                                            icon = Icons.Rounded.History,
                                            checked = notificationVaultEnabled,
                                            onCheckedChange = { viewModel.setNotificationVaultEnabled(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "step", "goal", "alert")) {
                                        SettingsToggleItem(
                                            title = "Step Goal Alerts",
                                            subtitle = "Notify on daily progress",
                                            icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                                            checked = stepNotifications,
                                            onCheckedChange = { viewModel.setStepNotifications(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "timer", "alert", "task")) {
                                        SettingsToggleItem(
                                            title = "Timer Alerts",
                                            subtitle = "Alarms for timers and focus sessions",
                                            icon = Icons.Rounded.Timer,
                                            checked = timerNotifications,
                                            onCheckedChange = { viewModel.setTimerNotifications(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "music", "playback", "media")) {
                                        SettingsToggleItem(
                                            title = "Music Player",
                                            subtitle = "Playback controls and track info",
                                            icon = Icons.Rounded.MusicNote,
                                            checked = musicNotifications,
                                            onCheckedChange = { viewModel.setMusicNotifications(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "conversion", "file", "progress")) {
                                        SettingsToggleItem(
                                            title = "File Conversion",
                                            subtitle = "Progress of your file operations",
                                            icon = Icons.Rounded.Transform,
                                            checked = fileConversionNotifications,
                                            onCheckedChange = { viewModel.setFileConversionNotifications(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "task", "reminder", "deadline")) {
                                        SettingsToggleItem(
                                            title = "Task Reminders",
                                            subtitle = "Deadlines and scheduled tasks",
                                            icon = Icons.Rounded.TaskAlt,
                                            checked = taskReminderNotifications,
                                            onCheckedChange = { viewModel.setTaskReminderNotifications(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "event", "calendar", "reminder")) {
                                        SettingsToggleItem(
                                            title = "Event Reminders",
                                            subtitle = "Upcoming calendar events",
                                            icon = Icons.Rounded.Event,
                                            checked = eventReminderNotifications,
                                            onCheckedChange = { viewModel.setEventReminderNotifications(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "pomodoro", "focus", "timer")) {
                                        SettingsToggleItem(
                                            title = "Pomodoro Timer",
                                            subtitle = "Session status and quick controls",
                                            icon = Icons.Rounded.AvTimer,
                                            checked = pomodoroNotifications,
                                            onCheckedChange = { viewModel.setPomodoroNotifications(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "flashlight", "light", "notification")) {
                                        SettingsToggleItem(
                                            title = "Flashlight Notification",
                                            subtitle = "Toggle from notification bar",
                                            icon = Icons.Rounded.FlashlightOn,
                                            checked = flashlightNotificationsEnabled,
                                            onCheckedChange = { viewModel.setFlashlightNotificationsEnabled(it) }
                                        )
                                    }
                                    if (matches(searchQuery, "update", "app", "version")) {
                                        SettingsToggleItem(
                                            title = "App Updates",
                                            subtitle = "New versions and patches",
                                            icon = Icons.Rounded.SystemUpdate,
                                            checked = appUpdateNotifications,
                                            onCheckedChange = { viewModel.setAppUpdateNotifications(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 8. Section: SYSTEM & DATA
                    StaggeredEntrance(index = 7) {
                        SettingsExpandableSection(
                            title = "SYSTEM & DATA",
                            icon = Icons.Rounded.SettingsInputComponent,
                            isExpanded = expandedSection == "SYSTEM" || searchQuery.isNotEmpty(),
                            onExpandToggle = { expandedSection = if (expandedSection == "SYSTEM") null else "SYSTEM" }
                        ) {
                            if (matches(searchQuery, "backup", "restore", "data", "save", "export", "import")) {
                                SettingsItem(
                                    title = "Backup & Restore",
                                    subtitle = "Export, import and automate data backups",
                                    icon = Icons.Rounded.Backup,
                                    onClick = {
                                        vibrationManager?.vibrateClick()
                                        onNavigateToBackupRestore()
                                    }
                                )
                            }

                            if (matches(searchQuery, "converter", "output", "path", "folder", "save", "storage")) {
                                SettingsItem(
                                    title = "Output Folder",
                                    subtitle = if (converterCustomPath == null) "Default: Downloads/Toolz" else "Custom folder active",
                                    icon = Icons.Rounded.FolderSpecial,
                                    onClick = { folderLauncher.launch(null) }
                                ) {
                                    if (converterCustomPath != null) {
                                        Row(
                                            modifier = Modifier.padding(top = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            @Suppress("DEPRECATION")
                                            Text(
                                                "Custom path active",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            TextButton(
                                                onClick = { viewModel.setConverterCustomOutputPath(null) },
                                                modifier = Modifier.height(32.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                @Suppress("DEPRECATION")
                                                Text("RESET", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            if (matches(searchQuery, "device", "info", "cache", "reset", "specs", "market")) {
                                SettingsItem(
                                    title = "Reset Device Info Cache",
                                    subtitle = "Clear saved market specifications",
                                    icon = Icons.Rounded.RestartAlt,
                                    onClick = {
                                        vibrationManager?.vibrateSuccess()
                                        viewModel.clearDeviceInfoCache()
                                        android.widget.Toast.makeText(context, "Device info cache cleared", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }

                    // 9. Section: UPDATE
                    if (!offlineModeEnabled) {
                        StaggeredEntrance(index = 8) {
                            SettingsExpandableSection(
                                title = "UPDATE",
                                icon = Icons.Rounded.SystemUpdate,
                                isExpanded = expandedSection == "UPDATE" || searchQuery.isNotEmpty(),
                                onExpandToggle = {
                                    expandedSection = if (expandedSection == "UPDATE") null else "UPDATE"
                                }
                            ) {
                                if (matches(searchQuery, "update", "auto", "patch", "background")) {
                                    SettingsToggleItem(
                                        title = "Automatic Updates",
                                        subtitle = "Download patches in background",
                                        icon = Icons.Rounded.AutoFixHigh,
                                        checked = autoUpdateEnabled,
                                        onCheckedChange = { viewModel.setAutoUpdateEnabled(it) }
                                    )
                                }
                                if (matches(searchQuery, "update", "check", "version", "new")) {
                                    SettingsItem(
                                        title = "Check for Updates",
                                        subtitle = "See if a new version is available",
                                        icon = Icons.Rounded.Update,
                                        onClick = onNavigateToUpdate
                                    )
                                }
                            }
                        }
                    }

                    // 10. Section: ABOUT
                    StaggeredEntrance(index = 9) {
                        AboutSection(onCheckUpdate = onNavigateToUpdate)
                    }

                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

fun Int.divideToFloat(): Float = this.toFloat()

@Composable
fun AdvancedThemeDialog(
    currentPrimary: Int,
    currentSecondary: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    var primaryHex by remember { mutableStateOf(String.format("#%06X", 0xFFFFFF and currentPrimary)) }
    var secondaryHex by remember { mutableStateOf(String.format("#%06X", 0xFFFFFF and currentSecondary)) }

    val vibrationManager = LocalVibrationManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(48.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }

                Text(
                    "ADVANCED THEME",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ThemeHexInput(
                        label = "PRIMARY COLOR",
                        value = primaryHex,
                        onValueChange = { primaryHex = it },
                        accentColor = parseHexSafe(primaryHex, Color(currentPrimary))
                    )

                    ThemeHexInput(
                        label = "SECONDARY COLOR",
                        value = secondaryHex,
                        onValueChange = { secondaryHex = it },
                        accentColor = parseHexSafe(secondaryHex, Color(currentSecondary))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("CANCEL", fontWeight = FontWeight.Black)
                    }

                    Button(
                        onClick = {
                            vibrationManager?.vibrateSuccess()
                            val p = parseHexSafe(primaryHex, Color(currentPrimary)).toArgb()
                            val s = parseHexSafe(secondaryHex, Color(currentSecondary)).toArgb()
                            onSave(p, s)
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("APPLY", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeHexInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    accentColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        @Suppress("DEPRECATION")
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            letterSpacing = 2.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accentColor)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            )

            OutlinedTextField(
                value = value,
                onValueChange = {
                    if (it.length <= 7) onValueChange(it.uppercase())
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    focusedBorderColor = accentColor
                )
            )
        }
    }
}

private fun parseHexSafe(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
    } catch (e: Exception) {
        fallback
    }
}

private fun matches(query: String, vararg keywords: String): Boolean {
    if (query.isEmpty()) return true
    return keywords.any { it.contains(query, ignoreCase = true) }
}

@Composable
fun SettingsExpandableSection(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val vibrationManager = LocalVibrationManager.current
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "arrowRotation")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        vibrationManager?.vibrateTick()
                        onExpandToggle()
                    }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(16.dp))
                @Suppress("DEPRECATION")
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    null,
                    modifier = Modifier.rotate(rotation).size(24.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    ExpressiveSearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = {
            Text(
                "Search settings...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            ),
        leadingIcon = {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                }
            }
        }
    )
}

@Composable
fun AboutSection(onCheckUpdate: () -> Unit) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(40.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 12.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("TOOLZ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            @Suppress("DEPRECATION")
            Text("V${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 3.sp)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "A polished toolkit designed for daily use by frerox.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ToolzExpressiveButton(
                    onClick = onCheckUpdate,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    @Suppress("DEPRECATION")
                    Text("UPDATES", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        val intent = Intent(Intent.ACTION_VIEW, "https://discord.gg/aAswRUerwh".toUri())
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2).copy(alpha = 0.1f), contentColor = Color(0xFF5865F2))
                ) {
                    Icon(Icons.Rounded.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    @Suppress("DEPRECATION")
                    Text("DISCORD", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HAPTIC TUNER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HapticTuner(
    intensity: Float,
    onIntensityChange: (Float) -> Unit
) {
    val haptic = rememberToolzHapticFeedback()
    val performanceMode = com.frerox.toolz.ui.theme.LocalPerformanceMode.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(intensity.coerceIn(0.1f, 1f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Box(
                modifier = Modifier
                    .weight((1f - intensity).coerceIn(0.1f, 1f))
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        ExpressiveSlider(
            value = intensity,
            onValueChange = onIntensityChange,
            onValueChangeFinished = { haptic.click() },
            valueRange = 0.1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("SOFT", "CRISP", "STRONG").forEachIndexed { i, label ->
                val target = when(i) {
                    0 -> 0.1f
                    1 -> 0.5f
                    else -> 1.0f
                }
                val isSelected = (intensity - target).let { if (it < 0) -it else it } < 0.2f
                
                Surface(
                    onClick = { 
                        onIntensityChange(target)
                        haptic.tick()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                    modifier = Modifier.height(32.dp).bouncyClick {}
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    MaterialTheme {
        Surface(color = Color(0xFF121212)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                SettingsExpandableSection(
                    title = "VISUALS & THEME",
                    icon = Icons.Rounded.Palette,
                    isExpanded = true,
                    onExpandToggle = {}
                ) {
                    SettingsToggleItem(
                        title = "Performance Mode",
                        subtitle = "Disable blur and high-frequency animations",
                        icon = Icons.Rounded.Speed,
                        checked = true,
                        onCheckedChange = {}
                    )
                    SettingsItem(
                        title = "Appearance Mode",
                        subtitle = "Current: system",
                        icon = Icons.Rounded.DarkMode
                    )
                    SettingsItem(
                        title = "Widget Styling",
                        subtitle = "Customize home screen widgets",
                        icon = Icons.Rounded.SettingsSuggest
                    )
                }
            }
        }
    }
}

@Composable
fun ColorPickerRow(selectedColor: Int, onColorSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val colors = listOf(
            Color(0xFF2962FF), Color(0xFF00BFA5), Color(0xFFFF6D00),
            Color(0xFFD50000), Color(0xFFAA00FF), Color(0xFF00C853),
            Color(0xFFE91E63), Color(0xFF673AB7), Color(0xFF03A9F4),
            Color.White, Color.Black
        )
        colors.forEach { color ->
            val argb = color.toArgb()
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (selectedColor == argb) 3.dp else 1.dp,
                        color = if (selectedColor == argb) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(argb) }
            ) {
                if (selectedColor == argb) {
                    Icon(
                        Icons.Rounded.Check,
                        null,
                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.align(Alignment.Center).size(20.dp)
                    )
                }
            }
        }
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    extraContent: @Composable (ColumnScope.() -> Unit)? = null
) {
    ExpressiveCard(
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth(),
        enabled = onClick != null,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        elevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    @Suppress("DEPRECATION")
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    @Suppress("DEPRECATION")
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                }
                if (onClick != null) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                }
            }
            extraContent?.let {
                Spacer(Modifier.height(4.dp))
                it()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    ExpressiveCard(
        onClick = { if (enabled) onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f),
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        elevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                }
            }
            ExpressiveSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}
