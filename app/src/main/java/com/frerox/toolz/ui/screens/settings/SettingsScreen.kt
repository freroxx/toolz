package com.frerox.toolz.ui.screens.settings

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.theme.LocalVibrationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToUpdate: () -> Unit,
    onResetOnboarding: () -> Unit
) {
    val vibrationManager = LocalVibrationManager.current

    val stepGoal by viewModel.stepGoal.collectAsState(initial = 10000)
    val themeMode by viewModel.themeMode.collectAsState(initial = "SYSTEM")
    val dynamicColor by viewModel.dynamicColor.collectAsState(initial = true)
    val customPrimaryInt by viewModel.customPrimaryColor.collectAsState(initial = null)
    val customSecondaryInt by viewModel.customSecondaryColor.collectAsState(initial = null)

    val dashboardView by viewModel.dashboardView.collectAsState(initial = "DEFAULT")
    val showRecentTools by viewModel.showRecentTools.collectAsState(initial = true)
    val showQuickNotes by viewModel.showQuickNotes.collectAsState(initial = true)

    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState(initial = true)
    val notificationVaultEnabled by viewModel.notificationVaultEnabled.collectAsState(initial = true)
    val stepNotifications by viewModel.stepNotifications.collectAsState(initial = true)
    val timerNotifications by viewModel.timerNotifications.collectAsState(initial = true)

    val widgetBgColor by viewModel.widgetBackgroundColor.collectAsState(initial = 0xFFFFFFFF.toInt())
    val widgetOpacity by viewModel.widgetOpacity.collectAsState(initial = 0.9f)

    val hapticFeedback by viewModel.hapticFeedback.collectAsState(initial = true)
    val hapticIntensity by viewModel.hapticIntensity.collectAsState(initial = 0.5f)
    val unitSystem by viewModel.unitSystem.collectAsState(initial = "METRIC")
    val stepCounterEnabled by viewModel.stepCounterEnabled.collectAsState(initial = true)
    val showToolzPill by viewModel.showToolzPill.collectAsState(initial = true)
    val pillTodoEnabled by viewModel.pillTodoEnabled.collectAsState(initial = true)
    val pillFocusEnabled by viewModel.pillFocusEnabled.collectAsState(initial = true)

    val userName by viewModel.userName.collectAsState(initial = "")
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsState(initial = false)

    val musicShakeToSkip by viewModel.musicShakeToSkip.collectAsState(initial = false)
    val musicShakeSensitivity by viewModel.musicShakeSensitivity.collectAsState(initial = 0.3f)
    val musicAudioFocus by viewModel.musicAudioFocus.collectAsState(initial = true)

    val performanceMode by viewModel.performanceMode.collectAsState(initial = false)

    var showResetDialog by remember { mutableStateOf(false) }
    var showAdvancedThemeDialog by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState(initial = "")

    var expandedSection by remember { mutableStateOf<String?>(null) }

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
            CenterAlignedTopAppBar(
                title = { @Suppress("DEPRECATION") Text("SETTINGS", fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium) },
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
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
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // Section: SYSTEM
                SettingsExpandableSection(
                    title = "SYSTEM & ENGINE",
                    icon = Icons.Rounded.SettingsInputComponent,
                    isExpanded = expandedSection == "SYSTEM" || searchQuery.isNotEmpty(),
                    onExpandToggle = { expandedSection = if (expandedSection == "SYSTEM") null else "SYSTEM" }
                ) {
                    if (matches(searchQuery, "update", "auto", "version")) {
                        SettingsToggleItem(
                            title = "Automatic Updates",
                            subtitle = "Download patches in the background",
                            icon = Icons.Rounded.AutoFixHigh,
                            checked = autoUpdateEnabled,
                            onCheckedChange = { viewModel.setAutoUpdateEnabled(it) }
                        )
                        SettingsItem(
                            title = "Check for Updates",
                            subtitle = "See if a new version is available",
                            icon = Icons.Rounded.SystemUpdate,
                            onClick = onNavigateToUpdate
                        )
                    }
                    if (matches(searchQuery, "performance", "lag", "animations", "blur")) {
                        SettingsToggleItem(
                            title = "Performance Mode",
                            subtitle = "Disable blur and high-frequency animations",
                            icon = Icons.Rounded.Speed,
                            checked = performanceMode,
                            onCheckedChange = { viewModel.setPerformanceMode(it) }
                        )
                    }
                }

                // Section: EXPERIENCE
                SettingsExpandableSection(
                    title = "INTERFACE & TOOLS",
                    icon = Icons.Rounded.DashboardCustomize,
                    isExpanded = expandedSection == "EXPERIENCE" || searchQuery.isNotEmpty(),
                    onExpandToggle = { expandedSection = if (expandedSection == "EXPERIENCE") null else "EXPERIENCE" }
                ) {
                    if (matches(searchQuery, "pill", "smart", "overlay", "todo", "focus")) {
                        SettingsToggleItem(
                            title = "Smart Overlay",
                            subtitle = "Floating tool for quick access",
                            icon = Icons.Rounded.SmartButton,
                            checked = showToolzPill,
                            onCheckedChange = { viewModel.setShowToolzPill(it) }
                        )

                        if (showToolzPill) {
                            SettingsToggleItem(
                                title = "Show Task Progress",
                                subtitle = "Track active tasks on the overlay",
                                icon = Icons.Rounded.TaskAlt,
                                checked = pillTodoEnabled,
                                onCheckedChange = { viewModel.setPillTodoEnabled(it) }
                            )
                            SettingsToggleItem(
                                title = "Show Focus Score",
                                subtitle = "View productivity score on the overlay",
                                icon = Icons.Rounded.Toll,
                                checked = pillFocusEnabled,
                                onCheckedChange = { viewModel.setPillFocusEnabled(it) }
                            )
                        }
                    }

                    if (matches(searchQuery, "recent", "home", "layout", "view", "dashboard", "notes")) {
                        SettingsToggleItem(
                            title = "Show Recent Tools",
                            subtitle = "List frequently used tools on home",
                            icon = Icons.Rounded.History,
                            checked = showRecentTools,
                            onCheckedChange = { viewModel.setShowRecentTools(it) }
                        )

                        SettingsToggleItem(
                            title = "Show Quick Notes",
                            subtitle = "Show pinned notes on dashboard",
                            icon = Icons.AutoMirrored.Rounded.StickyNote2,
                            checked = showQuickNotes,
                            onCheckedChange = { viewModel.setShowQuickNotes(it) }
                        )

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
                }

                // Section: PERSONAL & HEALTH
                SettingsExpandableSection(
                    title = "USER & ENVIRONMENT",
                    icon = Icons.Rounded.PersonPin,
                    isExpanded = expandedSection == "USER" || searchQuery.isNotEmpty(),
                    onExpandToggle = { expandedSection = if (expandedSection == "USER") null else "USER" }
                ) {
                    if (matches(searchQuery, "profile", "name", "explorer")) {
                        SettingsItem(
                            title = "User Profile",
                            subtitle = "Name: ${userName.ifBlank { "Explorer" }}",
                            icon = Icons.Rounded.AccountCircle
                        ) {
                            OutlinedTextField(
                                value = userName,
                                onValueChange = { viewModel.setUserName(it) },
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

                    if (matches(searchQuery, "step", "goal", "health", "tracker")) {
                        SettingsToggleItem(
                            title = "Step Tracker",
                            subtitle = "Monitor physical activity in background",
                            icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                            checked = stepCounterEnabled,
                            onCheckedChange = { viewModel.setStepCounterEnabled(it) }
                        )

                        if (stepCounterEnabled) {
                            SettingsItem(
                                title = "Step Goal",
                                subtitle = "Target: $stepGoal steps",
                                icon = Icons.Rounded.EmojiEvents
                            ) {
                                Slider(
                                    value = stepGoal.toFloat(),
                                    onValueChange = { viewModel.setStepGoal(it.toInt()) },
                                    valueRange = 1000f..30000f,
                                    steps = 29,
                                    modifier = Modifier.padding(top = 8.dp),
                                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }

                    if (matches(searchQuery, "unit", "system", "metric", "imperial")) {
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

                    if (matches(searchQuery, "vibration", "haptic", "intensity")) {
                        SettingsToggleItem(
                            title = "Haptic Feedback",
                            subtitle = "Tactile response on interaction",
                            icon = Icons.Rounded.Vibration,
                            checked = hapticFeedback,
                            onCheckedChange = { viewModel.setHapticFeedback(it) }
                        )

                        if (hapticFeedback) {
                            SettingsItem(
                                title = "Vibration Strength",
                                subtitle = "Intensity: ${(hapticIntensity * 100).toInt()}%",
                                icon = Icons.Rounded.GraphicEq
                            ) {
                                Slider(
                                    value = hapticIntensity,
                                    onValueChange = { viewModel.setHapticIntensity(it) },
                                    onValueChangeFinished = { vibrationManager?.vibrateClick() },
                                    valueRange = 0.1f..1f,
                                    modifier = Modifier.padding(top = 8.dp),
                                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }

                // Section: MEDIA
                SettingsExpandableSection(
                    title = "MUSIC & AUDIO",
                    icon = Icons.Rounded.MusicNote,
                    isExpanded = expandedSection == "MEDIA" || searchQuery.isNotEmpty(),
                    onExpandToggle = { expandedSection = if (expandedSection == "MEDIA") null else "MEDIA" }
                ) {
                    if (matches(searchQuery, "shake", "skip", "audio", "sensitivity")) {
                        SettingsToggleItem(
                            title = "Shake to Skip",
                            subtitle = "Shake phone to play next song",
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
                                Text(
                                    text = if (musicShakeSensitivity < 0.4f) "High force required" else if (musicShakeSensitivity < 0.7f) "Medium force" else "Light shake",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                        SettingsToggleItem(
                            title = "Smart Audio Focus",
                            subtitle = "Automatically pause for other apps",
                            icon = Icons.Rounded.Hearing,
                            checked = musicAudioFocus,
                            onCheckedChange = { viewModel.setMusicAudioFocus(it) }
                        )
                    }
                }

                // Section: NOTIFICATIONS
                SettingsExpandableSection(
                    title = "ALERTS & VAULT",
                    icon = Icons.Rounded.Notifications,
                    isExpanded = expandedSection == "NOTIFICATIONS" || searchQuery.isNotEmpty(),
                    onExpandToggle = { expandedSection = if (expandedSection == "NOTIFICATIONS") null else "NOTIFICATIONS" }
                ) {
                    if (matches(searchQuery, "notification", "vault", "history")) {
                        SettingsToggleItem(
                            title = "Master Switch",
                            subtitle = "Enable or disable all app alerts",
                            icon = Icons.Rounded.NotificationsActive,
                            checked = notificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                        )

                        if (notificationsEnabled) {
                            SettingsToggleItem(
                                title = "Notification History",
                                subtitle = "Save and view previous alerts",
                                icon = Icons.Rounded.VerifiedUser,
                                checked = notificationVaultEnabled,
                                onCheckedChange = { viewModel.setNotificationVaultEnabled(it) }
                            )
                            SettingsToggleItem(
                                title = "Step Goal Alerts",
                                subtitle = "Notify on daily progress",
                                icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                                checked = stepNotifications,
                                onCheckedChange = { viewModel.setStepNotifications(it) }
                            )
                            SettingsToggleItem(
                                title = "Timer Alerts",
                                subtitle = "Alarms for timers and tasks",
                                icon = Icons.Rounded.Timer,
                                checked = timerNotifications,
                                onCheckedChange = { viewModel.setTimerNotifications(it) }
                            )
                        }
                    }
                }

                // Section: DESIGN
                SettingsExpandableSection(
                    title = "THEME & VISUALS",
                    icon = Icons.Rounded.Palette,
                    isExpanded = expandedSection == "DESIGN" || searchQuery.isNotEmpty(),
                    onExpandToggle = { expandedSection = if (expandedSection == "DESIGN") null else "DESIGN" }
                ) {
                    if (matches(searchQuery, "dynamic", "color", "material", "wallpaper", "advanced", "palette")) {
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
                                        Text("ADVANCED THEMING", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    if (matches(searchQuery, "dark", "light", "mode", "appearance")) {
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
                }

                // Section: WIDGETS
                SettingsExpandableSection(
                    title = "EXTERNAL COMPONENTS",
                    icon = Icons.Rounded.Widgets,
                    isExpanded = expandedSection == "WIDGETS" || searchQuery.isNotEmpty(),
                    onExpandToggle = { expandedSection = if (expandedSection == "WIDGETS") null else "WIDGETS" }
                ) {
                    if (matches(searchQuery, "widget", "opacity", "background")) {
                        SettingsItem(
                            title = "Widget Styling",
                            subtitle = "Transparency and color overrides",
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

                AboutSection(onCheckUpdate = onNavigateToUpdate)

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

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
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                "Search settings...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
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
                    Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(24.dp))
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    )
}

@Composable
fun AboutSection(onCheckUpdate: () -> Unit) {
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
            Text("V1.0.3 (BETA)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 3.sp)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "A modular toolset for productivity and analytics. Built for high performance by frerox.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onCheckUpdate,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                @Suppress("DEPRECATION")
                Text("CHECK FOR UPDATES", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.bouncyClick(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    @Suppress("DEPRECATION")
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Black)
                }
                if (onClick != null) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                }
            }
            extraContent?.let {
                it()
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick { onCheckedChange(!checked) },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    @Suppress("DEPRECATION")
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Black)
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.8f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}
